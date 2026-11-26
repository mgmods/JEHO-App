import { Injectable, Logger, BadRequestException, ConflictException, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository, QueryRunner } from 'typeorm';
import { Advertisement, AdvertisementStatus } from '../../database/entities/advertisement.entity';
import { User } from '../../database/entities/user.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';

const DEFAULT_CONFIG: any = {
  enabled: true, sendButtonEnabled: true, paused: false, dailyLimit: 1, maxQueueSize: 500, maxTextLength: 180, maxWords: 30, publishDelayMs: 0,
  resetTimezone: 'UTC', displayDurationMs: 9000, gapMs: 1800,
  entrySpeedPxPerSec: 120, exitSpeedPxPerSec: 120, reducedMotionStatic: true,
  textMotion: 'right-to-left', nameMotion: 'static', avatarMotion: 'static',
  template: 'premium-v1', position: 'safe-bottom', direction: 'rtl', transitionType: 'slide', fontFamily: 'default',
  background: '',
  backgroundImage: '', backgroundOpacity: 100, radiusDp: 0, heightDp: 48,
  avatarDp: 34, avatarShape: 'circle', nameSp: 12, textSp: 13, customOffsetDp: 0,
  nameColor: '#9A681F', textColor: '#111827',
  nameGradientEnabled: false, nameGradientStartColor: '#FFD98A', nameGradientEndColor: '#FF6B9D',
  textGradientEnabled: false, textGradientStartColor: '#FFFFFF', textGradientEndColor: '#9EDCFF',
  entryAnimation: 'slide-right', exitAnimation: 'slide-left', glowType: 'none', glowColor: '#FFD66B', glowIntensity: 0,
  vipDailyLimit: 5,
  vipPolicy: { durationDays:0, displayDurationSeconds:9, dailyLimit:5, backgroundImage:'' },
  userPolicies: {},
  customFontUrl: '', customFontName: '', spacingDp: 8,
  avatarOffsetXDp: 0, avatarOffsetYDp: 0, nameOffsetXDp: 0, nameOffsetYDp: 0, nameWidthDp: 90,
  textOffsetXDp: 0, textOffsetYDp: 0, textWidthDp: 0,
  positionXPercent: 50, positionYPercent: 52,
  composerBackgroundImage: '', composerBackgroundOpacity: 100, composerBackgroundColor: '#F7F9FC',
  composerIconUrl: '', composerTitleColor: '#172033', composerSubtitleColor: '#66758B',
  composerInputTextColor: '#111827', composerInputHintColor: '#9AA7B8', composerInputBackgroundColor: '#FFFFFF', composerInputBorderColor: '#D8E0EA',
  composerCancelTextColor: '#43536A', composerCancelBackgroundColor: '#F4F7FB', composerCancelBorderColor: '#DCE4EE',
  composerSendTextColor: '#241C14', composerSendGradientStartColor: '#EEC577', composerSendGradientEndColor: '#BC7E2F', composerSendBorderColor: '#EFC981',
  composerButtonRadiusDp: 24, composerButtonWidthDp: 0, composerButtonHorizontalPaddingDp: 20, composerIconSizeDp: 42,
  composerButtonText: '✦  إعلان عام', composerButtonTextColor: '#FFFFFF',
  composerButtonBackgroundStartColor: '#694117', composerButtonBackgroundEndColor: '#E5B85B',
  composerButtonBorderColor: '#EFC981', composerButtonBorderWidthDp: 1,
  composerButtonHeightDp: 46, composerButtonTextSizeSp: 13,
};

@Injectable()
export class AdvertisementsService implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(AdvertisementsService.name);
  private timer: ReturnType<typeof setInterval> | null = null;
  private busy = false;
  private leaderRunner: QueryRunner | null = null;
  private nextLeadershipCheckAt = 0;
  private nextLeaderHealthAt = 0;
  private leaderJustAcquired = false;

  constructor(
    @InjectRepository(Advertisement) private readonly ads: Repository<Advertisement>,
    @InjectRepository(User) private readonly users: Repository<User>,
    private readonly db: DataSource,
    private readonly realtime: RealtimeGateway,
  ) {}

  async onModuleInit() {
    // Production deployments may already have a populated JEHO schema, so the
    // first-install seed is intentionally skipped. Provision this feature's
    // table idempotently at runtime; otherwise POST /advertisements fails with
    // PostgreSQL 42P01 on existing installations that predate the ads module.
    await this.ensureSchema();
    await this.ensureConfig();
    this.timer = setInterval(() => void this.tick(), 500);
    void this.tick();
  }

  private async ensureSchema() {
    await this.db.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
    await this.db.query(`
      CREATE TABLE IF NOT EXISTS "jeho_own"."room_advertisements" (
        "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        "senderId" uuid NOT NULL REFERENCES "jeho_own"."users"("id") ON DELETE CASCADE,
        "senderName" varchar(128) NOT NULL,
        "senderImageUrl" varchar(512),
        "text" varchar(500) NOT NULL,
        "status" varchar(16) NOT NULL DEFAULT 'queued',
        "queueOrder" bigint NOT NULL DEFAULT 0,
        "startedAt" timestamptz,
        "completedAt" timestamptz,
        "nextEligibleAt" timestamptz,
        "rejectionCode" varchar(64),
        "rejectionReason" varchar(255),
        "idempotencyKey" varchar(128),
        "displayTemplateId" varchar(64),
        "displayDurationMs" integer NOT NULL DEFAULT 9000,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now()
      )
    `);
    await this.db.query(`
      ALTER TABLE "jeho_own"."room_advertisements"
        ADD COLUMN IF NOT EXISTS "senderImageUrl" varchar(512),
        ADD COLUMN IF NOT EXISTS "nextEligibleAt" timestamptz,
        ADD COLUMN IF NOT EXISTS "rejectionCode" varchar(64),
        ADD COLUMN IF NOT EXISTS "rejectionReason" varchar(255),
        ADD COLUMN IF NOT EXISTS "idempotencyKey" varchar(128),
        ADD COLUMN IF NOT EXISTS "displayTemplateId" varchar(64),
        ADD COLUMN IF NOT EXISTS "displayConfig" jsonb,
        ADD COLUMN IF NOT EXISTS "displayDurationMs" integer NOT NULL DEFAULT 9000,
        ADD COLUMN IF NOT EXISTS "createdAt" timestamptz NOT NULL DEFAULT now(),
        ADD COLUMN IF NOT EXISTS "updatedAt" timestamptz NOT NULL DEFAULT now()
    `);
    await this.db.query('CREATE INDEX IF NOT EXISTS "idx_room_ads_queue" ON "jeho_own"."room_advertisements" ("status","queueOrder","createdAt")');
    await this.db.query('CREATE INDEX IF NOT EXISTS "idx_room_ads_sender_day" ON "jeho_own"."room_advertisements" ("senderId","createdAt")');
    await this.db.query('CREATE UNIQUE INDEX IF NOT EXISTS "uq_room_ads_idempotency" ON "jeho_own"."room_advertisements" ("idempotencyKey") WHERE "idempotencyKey" IS NOT NULL');
  }

  async onModuleDestroy() {
    if (this.timer) clearInterval(this.timer);
    if (this.leaderRunner) {
      try { await this.leaderRunner.query('SELECT pg_advisory_unlock(hashtextextended($1,0))', ['voice-room-ad-worker']); } catch {}
      try { await this.leaderRunner.release(); } catch {}
      this.leaderRunner = null;
    }
  }

  private async ensureWorkerLeadership(): Promise<boolean> {
    if (this.leaderRunner && !this.leaderRunner.isReleased) {
      if (Date.now() < this.nextLeaderHealthAt) return true;
      this.nextLeaderHealthAt = Date.now() + 5000;
      try { await this.leaderRunner.query('SELECT 1'); return true; }
      catch { try { await this.leaderRunner.release(); } catch {} this.leaderRunner = null; }
    }
    if (Date.now() < this.nextLeadershipCheckAt) return false;
    this.nextLeadershipCheckAt = Date.now() + 500;
    const runner = this.db.createQueryRunner();
    try {
      await runner.connect();
      const rows = await runner.query('SELECT pg_try_advisory_lock(hashtextextended($1,0)) AS acquired', ['voice-room-ad-worker']);
      if (rows?.[0]?.acquired === true || rows?.[0]?.acquired === 't') {
        this.leaderRunner = runner;
        this.leaderJustAcquired = true;
        this.log.log('This API instance is the active global room-advertisement worker.');
        return true;
      }
      await runner.release();
      return false;
    } catch (error) {
      try { await runner.release(); } catch {}
      this.log.warn('Advertisement worker election failed: ' + String((error as any)?.message || error));
      return false;
    }
  }

  private async config() {
    const rows = await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1', ['voice_room_ads_config']);
    let value: any = {};
    try { value = rows?.[0]?.value ? JSON.parse(rows[0].value) : {}; } catch {}
    const merged = { ...DEFAULT_CONFIG, ...(value || {}) };
    // Migrate the old built-in 10-second delay to immediate publishing, while
    // preserving any non-default delay explicitly chosen by the administrator.
    if (Number(value?.publishDelayMs) === 10000) merged.publishDelayMs = 0;
    // Upgrade only the original built-in dark/white preset. Preserve any
    // administrator-customized ticker background and name styling.
    if (String(value?.background || '') === 'linear-gradient(90deg,rgba(20,12,42,.94),rgba(50,24,76,.86))') {
      merged.background = DEFAULT_CONFIG.background;
    }
    if (String(value?.textColor || '').toUpperCase() === '#FFFFFF' && value?.textGradientEnabled !== true) {
      merged.textColor = '#111827';
    }
    // Legacy glow and decorative bar fills are disabled: only the uploaded transparent frame is rendered.
    merged.background = '';
    merged.backgroundOpacity = 100;
    merged.radiusDp = 0;
    merged.glowType = 'none'; merged.glowColor = ''; merged.glowIntensity = 0;
    if (!['slide-top','slide-bottom','slide-left','slide-right','fade','zoom'].includes(merged.entryAnimation)) merged.entryAnimation = 'slide-right';
    if (!['slide-top','slide-bottom','slide-left','slide-right','fade','zoom'].includes(merged.exitAnimation)) merged.exitAnimation = 'slide-left';
    return merged;
  }

  private async ensureConfig() {
    // Unique-key upserts make first boot safe when multiple API replicas start together.
    await this.db.query(
      'INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt") VALUES (gen_random_uuid(),$1,$2,$3,NOW(),NOW()) ON CONFLICT ("key") DO NOTHING',
      ['voice_room_ads_config', JSON.stringify(DEFAULT_CONFIG), 'Voice room advertising live configuration'],
    );
    await this.db.query(
      'INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt") VALUES (gen_random_uuid(),$1,$2,$3,NOW(),NOW()) ON CONFLICT ("key") DO NOTHING',
      ['voice_room_ads_banned_words', '', 'Voice room advertising banned words, comma-separated'],
    );
    await this.db.query(
      'INSERT INTO "jeho_own"."app_settings" ("id","key","value","description","createdAt","updatedAt") VALUES (gen_random_uuid(),$1,$2,$3,NOW(),NOW()) ON CONFLICT ("key") DO NOTHING',
      ['voice_room_ads_user_policies', JSON.stringify({users:{}}), 'Per-user and VIP advertising policies'],
    );
  }

  async current() {
    const config = await this.config();
    // Do not resurrect a stale active row while publishing is disabled or paused.
    const ad = config.enabled === true && config.paused !== true
      ? await this.ads.findOne({ where: { status: AdvertisementStatus.PLAYING }, order: { startedAt: 'ASC' } })
      : null;
    return { advertisement: ad ? this.publicAd(ad) : null, config, serverNow: new Date().toISOString() };
  }

  async getPublicConfig() {
    const c = await this.config();
    return { enabled: c.enabled === true, sendButtonEnabled: c.sendButtonEnabled === true, paused: c.paused === true, maxTextLength: this.bound(c.maxTextLength, 1, 500, 180), maxWords: this.bound(c.maxWords, 1, 200, 30), ticker: c };
  }

  async remainingForUser(userId: string) {
    const base = await this.config();
    const c = await this.effectiveConfigForUser(userId, base);
    const limit = Math.max(0, Math.floor(Number(c.dailyLimit) || 0));
    const start = await this.resetStartFromDb(c.resetTimezone);
    // Use the explicitly-owned JEHO schema here instead of TypeORM's entity
    // query builder. The live table is provisioned in jeho_own by ensureSchema;
    // a metadata/search_path mismatch must never make the quota endpoint fail.
    const rows = await this.db.query(
      'SELECT COUNT(*)::int AS "used" FROM "jeho_own"."room_advertisements" WHERE "senderId"=$1 AND "createdAt">=$2 AND "status" = ANY($3::varchar[])',
      [userId, start, [
        AdvertisementStatus.QUEUED,
        AdvertisementStatus.PLAYING,
        AdvertisementStatus.COMPLETED,
        AdvertisementStatus.REMOVED,
      ]],
    );
    const used = Math.max(0, Number(rows?.[0]?.used || 0));
    return { limit, used, remaining: Math.max(0, limit-used) };
  }

  async create(userId: string, rawText: string, key: string) {
    const base = await this.config();
    const c = await this.effectiveConfigForUser(userId, base);
    if (c.enabled !== true || c.sendButtonEnabled !== true) throw new ConflictException('إرسال الإعلانات غير متاح حالياً');
    if (c.paused === true) throw new ConflictException('الإعلانات متوقفة مؤقتاً من لوحة التحكم؛ أعد تفعيلها ثم أرسل الإعلان');
    const text = String(rawText || '').replace(/(?:\r\n|\r|\n)+/g, ', ').replace(/[ \t]+/g, ' ').replace(/\s*,\s*/g, ', ').trim();
    const max = this.bound(c.maxTextLength, 1, 500, 180);
    if (!text) throw new BadRequestException('اكتب نص الإعلان أولاً');
    if (text.length > max) throw new BadRequestException('تجاوزت الحد الأقصى لعدد الأحرف (' + max + ')');
    const maxWords = this.bound(c.maxWords, 1, 200, 30);
    const wordCount = text.split(/\s+/u).filter(Boolean).length;
    if (wordCount > maxWords) throw new BadRequestException('الإعلان يحتوي على ' + wordCount + ' كلمة، والحد المسموح ' + maxWords + ' كلمة');
    if (!key || key.length < 8 || key.length > 128) throw new BadRequestException('معرّف الطلب غير صالح');

    const existing = await this.ads.findOne({where:{idempotencyKey:key}});
    if (existing) {
      await this.recordRejected(userId, text, 'duplicate', 'محاولة إرسال مكررة بنفس معرّف الطلب');
      if (existing.senderId !== userId || existing.text !== text) throw new ConflictException('معرّف الطلب مستخدم مسبقاً مع بيانات مختلفة');
      let remaining: any = null;
      try { remaining = await this.remainingForUser(userId); }
      catch (quotaError) { this.log.warn('Duplicate advertisement confirmed but quota refresh failed: ' + String((quotaError as any)?.message || quotaError)); }
      return {duplicate:true, advertisement:this.publicAd(existing), remaining};
    }

    // The transaction serializes all queue mutations so maxQueueSize and queueOrder
    // remain correct even when many devices submit at the same moment.

    const banned = await this.getBannedWords();
    const hit = banned.find(w => this.containsBannedWord(text, w));
    if (hit) {
      await this.recordRejected(userId,text,'blocked_word','الإعلان يحتوي على كلمات غير مسموح بها');
      throw new BadRequestException('الإعلان يحتوي على كلمات غير مسموح بها');
    }

    const qr = this.db.createQueryRunner();
    await qr.connect(); await qr.startTransaction();
    try {
      await qr.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['voice-room-ad-queue']);
      await qr.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['voice-ad-limit:'+userId+':'+this.resetKey(c.resetTimezone)]);
      const duplicate = await qr.manager.findOne(Advertisement,{where:{idempotencyKey:key}});
      if (duplicate) {
        await qr.commitTransaction();
        await this.recordRejected(userId, text, 'duplicate', 'محاولة إرسال مكررة بنفس معرّف الطلب');
        if (duplicate.senderId !== userId || duplicate.text !== text) throw new ConflictException('معرّف الطلب مستخدم مسبقاً مع بيانات مختلفة');
        let remaining: any = null;
        try { remaining = await this.remainingForUser(userId); }
        catch (quotaError) { this.log.warn('Duplicate advertisement confirmed but quota refresh failed: ' + String((quotaError as any)?.message || quotaError)); }
        return {duplicate:true, advertisement:this.publicAd(duplicate), remaining};
      }
      const start = await this.resetStartFromDb(c.resetTimezone);
      const count = Number((await qr.query('SELECT COUNT(*)::int AS count FROM "jeho_own"."room_advertisements" WHERE "senderId"=$1 AND "createdAt">=$2 AND "status" IN ($3,$4,$5,$6)',[userId,start,AdvertisementStatus.QUEUED,AdvertisementStatus.PLAYING,AdvertisementStatus.COMPLETED,AdvertisementStatus.REMOVED]))?.[0]?.count || 0);
      const limit = Math.max(0,Math.floor(Number(c.dailyLimit)||0));
      if (count >= limit) throw new ConflictException('لقد وصلت إلى الحد اليومي للإعلانات');
      const queueCount = Number((await qr.query('SELECT COUNT(*)::int AS count FROM "jeho_own"."room_advertisements" WHERE "status"=$1',[AdvertisementStatus.QUEUED]))?.[0]?.count || 0);
      if (queueCount >= Math.max(1, Math.floor(Number(c.maxQueueSize) || 500))) throw new ConflictException('طابور الإعلانات ممتلئ حالياً');

      const user = await qr.manager.getRepository(User).findOne({where:{id:userId}});
      if (!user) throw new BadRequestException('الحساب غير موجود');
      const maxOrder = Number((await qr.query('SELECT COALESCE(MAX("queueOrder"),0)::bigint AS max FROM "jeho_own"."room_advertisements" WHERE "status"=$1',[AdvertisementStatus.QUEUED]))?.[0]?.max || 0);
      const avatarUrl = String((user as any).avatarUrl || '').trim();
      const safeAvatarUrl = avatarUrl.length > 0 && avatarUrl.length <= 512 ? avatarUrl : null;
      const ad = qr.manager.create(Advertisement,{
        senderId:userId,
        senderName:String((user as any).displayName || (user as any).username || 'عضو').slice(0,128),
        // The DB column is varchar(512). Never let an unusually long CDN/signed
        // avatar URL reject the entire advertisement insert.
        senderImageUrl:safeAvatarUrl,
        text,status:AdvertisementStatus.QUEUED,queueOrder:maxOrder+1,idempotencyKey:key,
        displayTemplateId:String(c.template||'premium-v1'),
        displayConfig: this.pickDisplayConfig(c),
        displayDurationMs: this.resolveDisplayDuration(text, c),
      });
      await qr.manager.save(ad);
      // Commit is the authoritative success point. Never turn a committed
      // advertisement into an HTTP error because the post-commit quota lookup
      // or worker wake-up fails.
      await qr.commitTransaction();
      let remaining: any = null;
      try {
        remaining = await this.remainingForUser(userId);
      } catch (quotaError) {
        this.log.warn('Advertisement committed but quota refresh failed: ' + String((quotaError as any)?.message || quotaError));
      }
      void this.tick();
      return {duplicate:false,advertisement:this.publicAd(ad),remaining};
    } catch(e) {
      const failure = e as any;
      const message = String(failure?.message || e || 'Unknown advertisement error');
      this.log.error('Advertisement create failed: ' + JSON.stringify({
        message,
        code: failure?.code || failure?.driverError?.code || null,
        constraint: failure?.constraint || failure?.driverError?.constraint || null,
        detail: failure?.detail || failure?.driverError?.detail || null,
        table: failure?.table || failure?.driverError?.table || null,
      }));
      await qr.rollbackTransaction();
      if (e instanceof ConflictException && /الحد اليومي|الطابور ممتلئ/.test(e.message)) {
        await this.recordRejected(userId, text, e.message.includes('الحد اليومي') ? 'daily_limit' : 'queue_full', e.message);
      } else if (!(e instanceof BadRequestException) && !(e instanceof ConflictException)) {
        // Keep an admin-visible failure record for unexpected DB/runtime errors,
        // so "send failed" can be traced to the real backend cause after deploy.
        try {
          const reason = (message + (failure?.code ? ' [' + failure.code + ']' : '')).slice(0, 255);
          await this.recordRejected(userId, text, 'server_error', reason);
        } catch (recordError) {
          this.log.error('Could not persist advertisement failure audit: ' + String((recordError as any)?.message || recordError));
        }
      }
      throw e;
    } finally { await qr.release(); }
  }

  private async recordRejected(userId:string,text:string,code:string,reason:string) {
    const user=await this.users.findOne({where:{id:userId}});
    await this.ads.save(this.ads.create({senderId:userId,senderName:String((user as any)?.displayName || (user as any)?.username || 'عضو').slice(0,128),senderImageUrl:String((user as any)?.avatarUrl||'').trim()||null,text:text.slice(0,500),status:AdvertisementStatus.REJECTED,rejectionCode:code,rejectionReason:reason,queueOrder:0,displayDurationMs:0}));
  }

  private async tick() {
    if(this.busy)return;
    // Claim the in-process worker slot before awaiting database leadership;
    // otherwise overlapping 500ms ticks can both pass the initial busy check.
    this.busy=true;
    try {
      if (!(await this.ensureWorkerLeadership())) return;
      const c=await this.config();
      const now=Date.now();
      if(this.leaderJustAcquired){
        this.leaderJustAcquired=false;
        if(c.enabled===true && c.paused!==true){
          const recovered=await this.ads.findOne({where:{status:AdvertisementStatus.PLAYING},order:{startedAt:'ASC'}});
          if(recovered){
            const serverNow=new Date();
            const remaining=this.bound(recovered.displayDurationMs,1000,120000,9000)-(serverNow.getTime()-(recovered.startedAt?.getTime()||serverNow.getTime()));
            if(remaining>0){ const recoveredConfig = recovered.displayConfig ? {...c,...recovered.displayConfig} : c; this.realtime.server.to('rooms:all').emit('room:advertisement',{type:'start',advertisement:this.publicAd(recovered),config:recoveredConfig,at:(recovered.startedAt||serverNow).toISOString(),serverNow:serverNow.toISOString(),recovered:true}); }
          }
        }
      }
      const current=await this.ads.findOne({where:{status:AdvertisementStatus.PLAYING},order:{startedAt:'ASC'}});
      if(current){
        const done=(current.startedAt?.getTime()||now)+this.bound(current.displayDurationMs,1000,120000,9000);
        // A pause/disable must also close the active DB row; otherwise it remains
        // PLAYING and permanently blocks the global queue after re-enabling.
        const forceClose=c.enabled!==true || c.paused===true;
        if(!forceClose && now < done) return;
        const completedAt=forceClose ? new Date(now) : new Date(done);
        current.status=AdvertisementStatus.COMPLETED;
        current.completedAt=completedAt;
        current.nextEligibleAt=new Date(completedAt.getTime()+(forceClose?0:Math.max(0,Number(c.gapMs)||0)));
        await this.ads.save(current);
        this.realtime.server.to('rooms:all').emit('room:advertisement', {
          type: 'end', advertisement: this.publicAd(current), config: c,
          at: current.completedAt.toISOString(), serverNow: new Date().toISOString(),
        });
      }
      // Persisted global gate: survives API restarts and the tiny window between
      // completing one ad and promoting the next queued ad. This guarantees a
      // single global ad followed by the configured quiet interval in every room.
      const gateRows=await this.db.query(
        'SELECT MAX("nextEligibleAt") AS "nextAt" FROM "jeho_own"."room_advertisements" WHERE "status"=$1 AND "nextEligibleAt" IS NOT NULL',
        [AdvertisementStatus.COMPLETED],
      );
      const nextEligibleAt=gateRows?.[0]?.nextAt ? new Date(gateRows[0].nextAt).getTime() : 0;
      if(nextEligibleAt > now || c.enabled !== true || c.paused === true) return;
      const qr=this.db.createQueryRunner(); await qr.connect(); await qr.startTransaction();
      try{
        await qr.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['voice-room-ad-queue']);
        const playing=await qr.manager.findOne(Advertisement,{where:{status:AdvertisementStatus.PLAYING}});
        if(playing){await qr.commitTransaction();return;}
        const next=await qr.manager.createQueryBuilder(Advertisement,'a')
          .where('a.status=:s',{s:AdvertisementStatus.QUEUED})
          // Wait the dashboard-configured preparation window before global broadcast.
          .andWhere('a.createdAt <= :eligibleAt',{eligibleAt:new Date(Date.now()-this.bound(c.publishDelayMs,0,60000,0))})
          .orderBy('a.queueOrder','ASC').addOrderBy('a.createdAt','ASC')
          .setLock('pessimistic_write').setOnLocked('skip_locked').getOne();
        if(!next){await qr.commitTransaction();return;}
        next.status=AdvertisementStatus.PLAYING;
        next.startedAt=new Date();
        const senderConfig = await this.effectiveConfigForUser(next.senderId, c);
        next.displayConfig = this.pickDisplayConfig(senderConfig);
        next.displayDurationMs=this.resolveDisplayDuration(next.text, senderConfig);
        await qr.manager.save(next); await qr.commitTransaction();
        this.realtime.server.to('rooms:all').emit('room:advertisement',{type:'start',advertisement:this.publicAd(next),config:next.displayConfig ? {...c,...next.displayConfig} : c,at:next.startedAt.toISOString(),serverNow:new Date().toISOString()});
      }catch(e){await qr.rollbackTransaction();this.log.error('Advertisement queue publish failed: '+String((e as any)?.message||e));}finally{await qr.release();}
    }catch(e){this.log.warn('Advertisement tick failed: '+String((e as any)?.message||e));}
    finally{this.busy=false;}
  }

  private resolveDisplayDuration(text: string, c: any): number {
    const mode = String(c.textMotion || 'right-to-left').toLowerCase();
    const moving = ['right-to-left', 'rtl', 'left-to-right', 'ltr'].includes(mode);
    // Keep this canonical 360dp metric identical to the Android ticker timeline,
    // so all devices share the same entry/exit phase boundaries.
    const textWidthDp = Math.max(1, Math.ceil(String(text || '').length
      * Math.max(8, this.bound(c.textSp, 9, 36, 13) * 0.62)));
    const viewportWidthDp = 360;
    const fromRight = mode === 'right-to-left' || mode === 'rtl';
    const entryDistanceDp = fromRight ? viewportWidthDp : textWidthDp;
    const exitDistanceDp = fromRight ? textWidthDp : viewportWidthDp;
    const entrySpeedDp = this.bound(c.entrySpeedPxPerSec, 20, 800, 120);
    const exitSpeedDp = this.bound(c.exitSpeedPxPerSec, 20, 800, 120);
    const motionBudget = moving
      ? Math.ceil(entryDistanceDp * 1000 / entrySpeedDp)
        + Math.ceil(exitDistanceDp * 1000 / exitSpeedDp) + 400
      : 0;
    const requestedMs = c.displayDurationSeconds !== undefined
      ? this.bound(Number(c.displayDurationSeconds) * 1000, 1000, 120000, 9000)
      : this.bound(c.displayDurationMs, 1000, 120000, 9000);
    if (moving) return Math.min(120000, Math.max(requestedMs, motionBudget + 300));
    return Math.min(120000, Math.max(1000, requestedMs));
  }

  private pickDisplayConfig(c:any){
    const keys=['displayDurationSeconds','displayDurationMs','backgroundImage','heightDp','avatarDp','avatarShape','nameSp','textSp','nameColor','textColor','nameGradientEnabled','nameGradientStartColor','nameGradientEndColor','textGradientEnabled','textGradientStartColor','textGradientEndColor','customFontUrl','customFontName','fontFamily','spacingDp','avatarOffsetXDp','avatarOffsetYDp','nameWidthDp','textOffsetXDp','textOffsetYDp','textWidthDp','textMotion','nameMotion','avatarMotion' ,'entryAnimation','exitAnimation','transitionType','direction','position','positionXPercent','positionYPercent','customOffsetDp','entrySpeedPxPerSec','exitSpeedPxPerSec'];
    const out:any={}; for(const k of keys) if(c[k]!==undefined) out[k]=c[k]; return out;
  }

  private async effectiveConfigForUser(userId:string, base:any){
    let overrides:any={};
    try{const rows=await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1',['voice_room_ads_user_policies']); overrides=rows?.[0]?.value?JSON.parse(rows[0].value):{};}catch{}
    const u=await this.users.findOne({where:{id:userId}});
    const publicId=String((u as any)?.publicId||'').trim(); let vip=false;
    try{const vipRows=await this.db.query('SELECT 1 FROM "jeho_own"."user_vips" WHERE "userId"=$1 AND "isActive"=true AND "startsAt"<=NOW() AND "expiresAt">NOW() LIMIT 1',[userId]); vip=vipRows?.length>0;}catch{}
    const vipPolicy=(base.vipPolicy&&typeof base.vipPolicy==='object')?base.vipPolicy:{};
    const configuredVipDays=this.bound(vipPolicy.durationDays,0,3650,0);
    try {
      const vipRows=await this.db.query(
        'SELECT 1 FROM "jeho_own"."user_vips" WHERE "userId"=$1 AND "isActive"=true AND "startsAt"<=NOW() AND "expiresAt">NOW() AND ($2::int=0 OR NOW()<LEAST("expiresAt","startsAt"+($2::int*INTERVAL \'1 day\'))) LIMIT 1',
        [userId,configuredVipDays]
      );
      vip=vipRows?.length>0;
    } catch { vip=false; }
    const storedDirect=overrides?.users?.[userId]||(publicId?overrides?.users?.[publicId]:null)||{};
    const directExpiry=storedDirect?.expiresAt ? new Date(storedDirect.expiresAt).getTime() : Number.POSITIVE_INFINITY;
    const direct=directExpiry>Date.now()?storedDirect:{};
    // VIP/direct-user policies intentionally inherit the normal ticker visuals.\n    // VIP / direct-user visual policy is intentionally limited to the background.
    // Position, font, text/name/avatar motion, entry/exit, glow, sizing and all other
    // ticker behavior always inherit the normal dashboard ticker. This keeps one
    // source of truth for the ticker layout and prevents VIP/ID ads from jumping to
    // a different place or using a second set of motion settings.
    const merged={...base};
    const applyBackgroundOnly=(source:any)=>{
      for(const k of ['backgroundImage']) {
        const v=source?.[k];
        if(v!==undefined && v!==null && !(typeof v==='string' && v.trim()==='')) merged[k]=v;
      }
    };
    if(vip) applyBackgroundOnly(vipPolicy);
    if(directExpiry>Date.now()) applyBackgroundOnly(direct);
    // Quotas/duration remain policy controls, but never become ticker visual settings.
    if(vip&&direct?.dailyLimit===undefined) merged.dailyLimit=this.bound(vipPolicy.dailyLimit??base.vipDailyLimit??base.dailyLimit,0,1000,base.dailyLimit);
    if(direct?.dailyLimit!==undefined) merged.dailyLimit=this.bound(direct.dailyLimit,0,1000,base.dailyLimit);
    if(vip&&vipPolicy.displayDurationSeconds!==undefined&&direct?.displayDurationSeconds===undefined) merged.displayDurationSeconds=this.bound(vipPolicy.displayDurationSeconds,1,120,9);
    if(direct?.displayDurationSeconds!==undefined) merged.displayDurationSeconds=this.bound(direct.displayDurationSeconds,1,120,9);
    return merged;
  }

  async adminUserPolicy(userRef:string){
    const base=await this.config(); let all:any={users:{}}; try{const rows=await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1',['voice_room_ads_user_policies']); all=rows?.[0]?.value?JSON.parse(rows[0].value):all;}catch{}
    let user:any=null;
    if(/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(userRef)) {
      user=await this.users.findOne({where:{id:userRef}});
    }
    if(!user) user=await this.users.findOne({where:{publicId:userRef}});
    if(!user) throw new BadRequestException('المستخدم غير موجود');
    const key=String((user as any).publicId||user.id); const saved=all?.users?.[key]||{};
    const active=saved.expiresAt ? new Date(saved.expiresAt).getTime()>Date.now() : true;
    return {user:{id:user.id,publicId:(user as any).publicId,displayName:(user as any).displayName},policy:{...saved,active,expiresAt:saved.expiresAt||null},vipPolicy:base.vipPolicy||{},vipDailyLimit:base.vipDailyLimit||base.dailyLimit};
  }

  async patchUserPolicy(body:any){
    const userRef=String(body?.userId||body?.publicId||'').trim(); if(!userRef) throw new BadRequestException('أدخل ID المستخدم');
    let user:any=null;
    if(/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(userRef)) {
      user=await this.users.findOne({where:{id:userRef}});
    }
    if(!user) user=await this.users.findOne({where:{publicId:userRef}});
    if(!user) throw new BadRequestException('المستخدم غير موجود');
    let all:any={users:{}}; try{const rows=await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1',['voice_room_ads_user_policies']); all=rows?.[0]?.value?JSON.parse(rows[0].value):all;}catch{} all.users=all.users||{};
    const key=String((user as any).publicId||user.id); const previous=all.users[key]||{}; const p:any={...previous}; for(const k of ['durationDays','displayDurationSeconds','dailyLimit','backgroundImage']) if(body[k]!==undefined)p[k]=body[k];
    if(p.dailyLimit!==undefined)p.dailyLimit=this.bound(p.dailyLimit,0,1000,1);
    if(body.durationDays!==undefined){
      p.durationDays=this.bound(body.durationDays,1,3650,1);
      p.activatedAt=new Date().toISOString();
      p.expiresAt=new Date(Date.now()+p.durationDays*86400000).toISOString();
    } else if(!p.expiresAt && p.durationDays>0){
      p.activatedAt=new Date().toISOString();
      p.expiresAt=new Date(Date.now()+p.durationDays*86400000).toISOString();
    }
    if(p.displayDurationSeconds!==undefined)p.displayDurationSeconds=this.bound(p.displayDurationSeconds,1,120,9);
   
    all.users[key]=p; await this.db.query('UPDATE "jeho_own"."app_settings" SET "value"=$1,"updatedAt"=NOW() WHERE "key"=$2',[JSON.stringify(all),'voice_room_ads_user_policies']); return {ok:true,user:{id:user.id,publicId:(user as any).publicId,displayName:(user as any).displayName},policy:p};
  }

  async patchVipPolicy(body:any){
    const base=await this.config(); const p:any={...(base.vipPolicy||{})}; for(const k of ['durationDays','displayDurationSeconds','dailyLimit','backgroundImage']) if(body[k]!==undefined)p[k]=body[k]; if(p.dailyLimit!==undefined)p.dailyLimit=this.bound(p.dailyLimit,0,1000,5); if(p.durationDays!==undefined)p.durationDays=this.bound(p.durationDays,0,3650,0); if(p.displayDurationSeconds!==undefined)p.displayDurationSeconds=this.bound(p.displayDurationSeconds,1,120,9);
    const merged={...base,vipPolicy:p,vipDailyLimit:p.dailyLimit}; await this.db.query('UPDATE "jeho_own"."app_settings" SET "value"=$1,"updatedAt"=NOW() WHERE "key"=$2',[JSON.stringify(merged),'voice_room_ads_config']); this.realtime.server.to('rooms:all').emit('room:advertisement:config',merged); return {ok:true,vipPolicy:p};
  }

  async patchConfig(input:Record<string,unknown>) {
    const c=await this.config();
    const merged:any={...c};
    const num=(k:string,min:number,max:number)=>{if(input[k]!==undefined)merged[k]=this.bound(input[k],min,max,min);};
    [['dailyLimit',0,1000],['vipDailyLimit',0,1000],['maxQueueSize',1,5000],['maxTextLength',1,500],['maxWords',1,200],['publishDelayMs',0,60000],['displayDurationMs',1000,120000],['gapMs',0,86400000],['entrySpeedPxPerSec',20,800],['exitSpeedPxPerSec',20,800],['composerBackgroundOpacity',0,100],['radiusDp',0,40],['composerButtonRadiusDp',0,40],['composerButtonWidthDp',0,600],['composerButtonHorizontalPaddingDp',0,80],['composerIconSizeDp',20,80],['composerButtonBorderWidthDp',0,8],['composerButtonHeightDp',32,80],['composerButtonTextSizeSp',9,24],['heightDp',32,140],['avatarDp',20,80],['nameSp',8,32],['textSp',9,36],['spacingDp',0,40],['customOffsetDp',-150,150],['avatarOffsetXDp',-300,300],['avatarOffsetYDp',-100,100],['nameOffsetXDp',-300,300],['nameOffsetYDp',-100,100],['nameWidthDp',24,260],['textOffsetXDp',-300,300],['textOffsetYDp',-100,100],['textWidthDp',0,1000],['positionXPercent',0,100],['positionYPercent',0,100]].forEach(x=>num(x[0] as string,x[1] as number,x[2] as number));
    ['enabled','sendButtonEnabled','paused','reducedMotionStatic','nameGradientEnabled','textGradientEnabled'].forEach(k=>{if(input[k]!==undefined)merged[k]=input[k]===true;});
    ['resetTimezone','template','position','direction','textMotion','nameMotion','avatarMotion','entryAnimation','exitAnimation','backgroundImage','avatarShape','nameColor','textColor','nameGradientStartColor','nameGradientEndColor','textGradientStartColor','textGradientEndColor','customFontUrl','customFontName','transitionType','fontFamily','composerBackgroundImage','composerBackgroundColor','composerIconUrl','composerTitleColor','composerSubtitleColor','composerInputTextColor','composerInputHintColor','composerInputBackgroundColor','composerInputBorderColor','composerCancelTextColor','composerCancelBackgroundColor','composerCancelBorderColor','composerSendTextColor','composerSendGradientStartColor','composerSendGradientEndColor','composerSendBorderColor','composerButtonText','composerButtonTextColor','composerButtonBackgroundStartColor','composerButtonBackgroundEndColor','composerButtonBorderColor'].forEach(k=>{if(input[k]!==undefined)merged[k]=String(input[k]??'');});
    if (!['slide','fade'].includes(merged.transitionType)) merged.transitionType='slide';
    const animationModes=['slide-top','slide-bottom','slide-left','slide-right','fade','zoom'];
    if (!animationModes.includes(merged.entryAnimation)) merged.entryAnimation='slide-right';
    if (!animationModes.includes(merged.exitAnimation)) merged.exitAnimation='slide-left';
    merged.glowType='none'; merged.glowColor=''; merged.glowIntensity=0;
    merged.background=''; merged.backgroundOpacity=100; merged.radiusDp=0;
    if (!['default','sans','serif','mono','custom'].includes(merged.fontFamily)) merged.fontFamily='default';
    merged.customFontUrl=String(merged.customFontUrl||'').slice(0,2048);
    merged.customFontName=String(merged.customFontName||'').slice(0,128);
    if (merged.fontFamily==='custom' && !/^\/uploads\/[a-z0-9-]+\.(ttf|otf)$/i.test(merged.customFontUrl)) {
      merged.fontFamily='default'; merged.customFontUrl=''; merged.customFontName='';
    }
    if (!['premium-v1','compact'].includes(merged.template)) merged.template='premium-v1';
    if (!['circle','square'].includes(merged.avatarShape)) merged.avatarShape='circle';
    if (!['safe-top','safe-bottom','custom-safe','free'].includes(merged.position)) merged.position='safe-bottom';
    if (!['rtl','ltr'].includes(merged.direction)) merged.direction='rtl';
    const motions=['right-to-left','left-to-right','static','fade'];
    if (!motions.includes(merged.textMotion)) merged.textMotion=merged.direction==='ltr'?'left-to-right':'right-to-left';
    if (!motions.includes(merged.nameMotion)) merged.nameMotion='static';
    if (!motions.includes(merged.avatarMotion)) merged.avatarMotion='static';
    try { new Intl.DateTimeFormat('en-US',{timeZone:merged.resetTimezone || 'UTC'}).format(new Date()); }
    catch { throw new BadRequestException('منطقة التوقيت غير صالحة؛ استخدم اسم IANA مثل Asia/Riyadh'); }
    if (merged.backgroundImage && !/^https?:\/\//i.test(merged.backgroundImage)) merged.backgroundImage='';
    merged.backgroundImage=String(merged.backgroundImage||'').slice(0,2048);
    const composerUrls=['composerBackgroundImage','composerIconUrl'];
    for (const k of composerUrls) { if (merged[k] && !/^https?:\/\//i.test(merged[k])) merged[k]=''; merged[k]=String(merged[k]||'').slice(0,2048); }
    const composerColors=['composerButtonTextColor','composerButtonBackgroundStartColor','composerButtonBackgroundEndColor','composerButtonBorderColor','composerBackgroundColor','composerTitleColor','composerSubtitleColor','composerInputTextColor','composerInputHintColor','composerInputBackgroundColor','composerInputBorderColor','composerCancelTextColor','composerCancelBackgroundColor','composerCancelBorderColor','composerSendTextColor','composerSendGradientStartColor','composerSendGradientEndColor','composerSendBorderColor'];
    for (const k of composerColors) { const v=String(merged[k]||''); if (!/^#(?:[0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(v) && !/^rgba?\(/i.test(v)) merged[k]=DEFAULT_CONFIG[k]; }
    await this.db.transaction(async manager => {
      await manager.query(
        'UPDATE "jeho_own"."app_settings" SET "value"=$1,"updatedAt"=NOW() WHERE "key"=$2',
        [JSON.stringify(merged), 'voice_room_ads_config'],
      );
      // If an ad is already on air, keep its server expiry aligned with the new
      // dashboard speed/duration settings before notifying every connected room.
      const activeRows = await manager.query(
        'SELECT "id","text" FROM "jeho_own"."room_advertisements" WHERE "status"=$1 ORDER BY "startedAt" ASC LIMIT 1 FOR UPDATE',
        [AdvertisementStatus.PLAYING],
      );
      if (activeRows?.[0]) {
        const duration = this.resolveDisplayDuration(String(activeRows[0].text || ''), merged);
        await manager.query(
          'UPDATE "jeho_own"."room_advertisements" SET "displayDurationMs"=$1,"updatedAt"=NOW() WHERE "id"=$2',
          [duration, activeRows[0].id],
        );
      }
    });
    this.realtime.server.to('rooms:all').emit('room:advertisement:config',merged);
    return merged;
  }

  async adminBannedWords(){ return {words:(await this.getBannedWords()).join(', ')}; }

  async patchBannedWords(words:string){
    const cleaned=String(words||'').split(',').map(x=>x.trim()).filter(Boolean).slice(0,5000).join(', ');
    await this.db.query('UPDATE "jeho_own"."app_settings" SET "value"=$1,"updatedAt"=NOW() WHERE "key"=$2',[cleaned,'voice_room_ads_banned_words']);
    return {words:cleaned};
  }

  async patchQueue(id:string,body:Record<string,unknown>){
    const qr=this.db.createQueryRunner(); await qr.connect(); await qr.startTransaction();
    try {
      await qr.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['voice-room-ad-queue']);
      const ad=await qr.manager.findOne(Advertisement,{where:{id},lock:{mode:'pessimistic_write'}});
      if(!ad)throw new BadRequestException('الإعلان غير موجود');
      if(body.action==='remove'){
        if(ad.status===AdvertisementStatus.PLAYING)throw new ConflictException('لا يمكن حذف الإعلان الجاري');
        ad.status=AdvertisementStatus.REMOVED; await qr.manager.save(ad);
      } else if(body.action==='priority') {
        if(ad.status!==AdvertisementStatus.QUEUED)throw new ConflictException('يمكن تغيير أولوية الإعلانات المنتظرة فقط');
        ad.queueOrder=Math.max(0,Number(body.queueOrder)||0); await qr.manager.save(ad);
      } else throw new BadRequestException('إجراء غير صالح');
      await qr.commitTransaction();
      return{ok:true};
    } catch(e) { await qr.rollbackTransaction(); throw e; }
    finally { await qr.release(); }
  }

  async adminQueue(limit=50, search='', history=false){
    const qb=this.ads.createQueryBuilder('a');
    if(history) qb.where('a.status IN (:...statuses)',{statuses:[AdvertisementStatus.COMPLETED,AdvertisementStatus.REJECTED,AdvertisementStatus.REMOVED]});
    else qb.where('a.status IN (:...statuses)',{statuses:[AdvertisementStatus.PLAYING,AdvertisementStatus.QUEUED]});
    const term=String(search||'').trim();
    if(term) qb.andWhere('(CAST(a.id AS text)=:id OR a.senderName ILIKE :term OR a.text ILIKE :term OR CAST(a.senderId AS text)=:id)',{id:term,term:'%'+term+'%'});
    const [items,total]=await qb.orderBy('a.status','ASC').addOrderBy('a.queueOrder','ASC').addOrderBy('a.createdAt','DESC').take(limit).getManyAndCount();
    return{total,items:items.map(a=>({...this.publicAd(a),rejectionCode:a.rejectionCode,rejectionReason:a.rejectionReason}))};
  }

  async adminStats(days=7){
    const sql = `SELECT
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day'))::int AS total,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status='completed')::int AS displayed,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status='rejected')::int AS rejected,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status='queued')::int AS waiting,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status IN ('queued','playing','completed','removed'))::int AS accepted,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND rejectionCode='blocked_word')::int AS "blockedWordAttempts",
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND rejectionCode='duplicate')::int AS "duplicateAttempts"
      FROM "jeho_own"."room_advertisements"`;
    return (await this.db.query(sql,[days]))?.[0]||{};
  }

  private publicAd(a:Advertisement){return{id:a.id,senderId:a.senderId,senderName:a.senderName,senderImageUrl:a.senderImageUrl,text:a.text,status:a.status,queueOrder:Number(a.queueOrder||0),createdAt:a.createdAt,startedAt:a.startedAt,completedAt:a.completedAt,displayDurationMs:a.displayDurationMs,displayConfig:a.displayConfig||null};}
  private async getBannedWords(){const r=await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1',['voice_room_ads_banned_words']);return String(r?.[0]?.value||'').split(',').map(x=>x.trim()).filter(Boolean);}
  private normalize(input:string){return String(input||'').normalize('NFKC').toLocaleLowerCase('ar').replace(/[إأآٱ]/g,'ا').replace(/ى/g,'ي').replace(/ؤ/g,'و').replace(/ئ/g,'ي').replace(/ة/g,'ه').replace(/[\u064B-\u065F\u0670]/g,'');}
  private containsBannedWord(text:string, banned:string){
    const term=Array.from(this.normalize(banned)).filter(ch=>/[\p{L}\p{N}]/u.test(ch));
    if(!term.length)return false;
    const escaped=term.join('[^\\p{L}\\p{N}]*');
    const pattern=new RegExp('(?<![\\p{L}\\p{N}])'+escaped+'(?![\\p{L}\\p{N}])','u');
    return pattern.test(this.normalize(text));
  }
  private resetKey(timezone:string){try{return new Intl.DateTimeFormat('en-CA',{timeZone:timezone||'UTC',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());}catch{return new Date().toISOString().slice(0,10);}}
  private async resetStartFromDb(timezone:string){
    const safe = String(timezone || 'UTC').trim() || 'UTC';
    try {
      // Build the UTC instant corresponding to midnight in the configured IANA timezone.
      const rows = await this.db.query(
        "SELECT ((date_trunc('day', NOW() AT TIME ZONE $1) AT TIME ZONE $1)) AS start",
        [safe],
      );
      return rows?.[0]?.start ? new Date(rows[0].start) : new Date();
    } catch {
      return new Date(this.resetKey(safe)+'T00:00:00.000Z');
    }
  }
  private bound(v:any,min:number,max:number,fallback:number){const n=Number(v);return Math.max(min,Math.min(max,Number.isFinite(n)?n:fallback));}
}