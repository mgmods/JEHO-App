import { Injectable, Logger, BadRequestException, ConflictException, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { Advertisement, AdvertisementStatus } from '../../database/entities/advertisement.entity';
import { User } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';

const DEFAULT_CONFIG: any = {
  enabled: true, sendButtonEnabled: true, paused: false, dailyLimit: 1, maxQueueSize: 500, maxTextLength: 180,
  resetTimezone: 'UTC', displayDurationMs: 9000, gapMs: 1800,
  entrySpeedPxPerSec: 120, exitSpeedPxPerSec: 120, reducedMotionStatic: true,
  template: 'premium-v1', position: 'safe-bottom', direction: 'rtl',
  background: 'linear-gradient(90deg,rgba(20,12,42,.94),rgba(50,24,76,.86))',
  backgroundImage: '', backgroundOpacity: 92, radiusDp: 18, heightDp: 48,
  avatarDp: 34, avatarShape: 'circle', nameSp: 12, textSp: 13,
  nameColor: '#FFD98A', textColor: '#FFFFFF', spacingDp: 8,
};

@Injectable()
export class AdvertisementsService implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(AdvertisementsService.name);
  private timer: ReturnType<typeof setInterval> | null = null;
  private busy = false;

  constructor(
    @InjectRepository(Advertisement) private readonly ads: Repository<Advertisement>,
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(UserProfile) private readonly profiles: Repository<UserProfile>,
    private readonly db: DataSource,
    private readonly realtime: RealtimeGateway,
  ) {}

  async onModuleInit() {
    await this.ensureConfig();
    this.timer = setInterval(() => void this.tick(), 1000);
    void this.tick();
  }

  onModuleDestroy() { if (this.timer) clearInterval(this.timer); }

  private async config() {
    const rows = await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1', ['voice_room_ads_config']);
    let value: any = {};
    try { value = rows?.[0]?.value ? JSON.parse(rows[0].value) : {}; } catch {}
    return { ...DEFAULT_CONFIG, ...(value || {}) };
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
  }

  async current() {
    const config = await this.config();
    const ad = await this.ads.findOne({ where: { status: AdvertisementStatus.PLAYING }, order: { startedAt: 'ASC' } });
    return { advertisement: ad ? this.publicAd(ad) : null, config };
  }

  async getPublicConfig() {
    const c = await this.config();
    return { enabled: c.enabled === true, sendButtonEnabled: c.sendButtonEnabled === true, maxTextLength: this.bound(c.maxTextLength, 1, 500, 180), ticker: c };
  }

  async remainingForUser(userId: string) {
    const c = await this.config();
    const limit = Math.max(0, Math.floor(Number(c.dailyLimit) || 0));
    const start = await this.resetStartFromDb(c.resetTimezone);
    const used = await this.ads.createQueryBuilder('a')
      .where('a.senderId=:userId',{userId})
      .andWhere('a.createdAt>=:start',{start})
      .andWhere('a.status IN (:...statuses)',{statuses:[AdvertisementStatus.QUEUED,AdvertisementStatus.PLAYING,AdvertisementStatus.COMPLETED,AdvertisementStatus.REMOVED]})
      .getCount();
    return { limit, used, remaining: Math.max(0, limit-used) };
  }

  async create(userId: string, rawText: string, key: string) {
    const c = await this.config();
    if (c.enabled !== true || c.sendButtonEnabled !== true) throw new ConflictException('إرسال الإعلانات غير متاح حالياً');
    const text = String(rawText || '').trim();
    const max = this.bound(c.maxTextLength, 1, 500, 180);
    if (!text) throw new BadRequestException('اكتب نص الإعلان أولاً');
    if (text.length > max) throw new BadRequestException('تجاوزت الحد الأقصى لعدد الأحرف');
    if (!key || key.length < 8 || key.length > 128) throw new BadRequestException('معرّف الطلب غير صالح');

    const existing = await this.ads.findOne({where:{idempotencyKey:key}});
    if (existing) {
      if (existing.senderId !== userId || existing.text !== text) throw new ConflictException('معرّف الطلب مستخدم مسبقاً مع بيانات مختلفة');
      return {duplicate:true, advertisement:this.publicAd(existing), remaining:await this.remainingForUser(userId)};
    }

    // The transaction serializes all queue mutations so maxQueueSize and queueOrder
    // remain correct even when many devices submit at the same moment.

    const banned = await this.getBannedWords();
    const normalized = this.normalize(text);
    const hit = banned.find(w => normalized.includes(this.normalize(w)));
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
        if (duplicate.senderId !== userId || duplicate.text !== text) throw new ConflictException('معرّف الطلب مستخدم مسبقاً مع بيانات مختلفة');
        await qr.commitTransaction();
        return {duplicate:true, advertisement:this.publicAd(duplicate), remaining:await this.remainingForUser(userId)};
      }
      const start = await this.resetStartFromDb(c.resetTimezone);
      const count = Number((await qr.query('SELECT COUNT(*)::int AS count FROM "jeho_own"."room_advertisements" WHERE "senderId"=$1 AND "createdAt">=$2 AND "status" IN ($3,$4,$5,$6)',[userId,start,AdvertisementStatus.QUEUED,AdvertisementStatus.PLAYING,AdvertisementStatus.COMPLETED,AdvertisementStatus.REMOVED]))?.[0]?.count || 0);
      const limit = Math.max(0,Math.floor(Number(c.dailyLimit)||0));
      if (count >= limit) throw new ConflictException('لقد وصلت إلى الحد اليومي للإعلانات');
      const queueCount = Number((await qr.query('SELECT COUNT(*)::int AS count FROM "jeho_own"."room_advertisements" WHERE "status"=$1',[AdvertisementStatus.QUEUED]))?.[0]?.count || 0);
      if (queueCount >= Math.max(1, Math.floor(Number(c.maxQueueSize) || 500))) throw new ConflictException('طابور الإعلانات ممتلئ حالياً');

      const user = await qr.manager.getRepository(User).findOne({where:{id:userId}});
      if (!user) throw new BadRequestException('الحساب غير موجود');
      const profile = await qr.manager.getRepository(UserProfile).findOne({where:{userId}});
      const maxOrder = Number((await qr.query('SELECT COALESCE(MAX("queueOrder"),0)::bigint AS max FROM "jeho_own"."room_advertisements" WHERE "status"=$1',[AdvertisementStatus.QUEUED]))?.[0]?.max || 0);
      const ad = qr.manager.create(Advertisement,{
        senderId:userId,
        senderName:String((profile as any)?.displayName || (user as any).username || 'عضو').slice(0,128),
        senderImageUrl:String((profile as any)?.avatarUrl || '').trim() || null,
        text,status:AdvertisementStatus.QUEUED,queueOrder:maxOrder+1,idempotencyKey:key,
        displayTemplateId:String(c.template||'premium-v1'),
        displayDurationMs:this.bound(c.displayDurationMs,1000,120000,9000),
      });
      await qr.manager.save(ad); await qr.commitTransaction(); void this.tick();
      return {duplicate:false,advertisement:this.publicAd(ad),remaining:await this.remainingForUser(userId)};
    } catch(e) { await qr.rollbackTransaction(); throw e; }
    finally { await qr.release(); }
  }

  private async recordRejected(userId:string,text:string,code:string,reason:string) {
    const user=await this.users.findOne({where:{id:userId}});
    const profile=user?await this.profiles.findOne({where:{userId}}):null;
    await this.ads.save(this.ads.create({senderId:userId,senderName:String((profile as any)?.displayName || (user as any)?.username || 'عضو').slice(0,128),senderImageUrl:String((profile as any)?.avatarUrl||'').trim()||null,text:text.slice(0,500),status:AdvertisementStatus.REJECTED,rejectionCode:code,rejectionReason:reason,queueOrder:0,displayDurationMs:0}));
  }

  private async tick() {
    if(this.busy)return; this.busy=true;
    try {
      const c=await this.config();
      if(c.enabled!==true || c.paused===true)return;
      const now=Date.now();
      const current=await this.ads.findOne({where:{status:AdvertisementStatus.PLAYING},order:{startedAt:'ASC'}});
      if(current){
        const done=(current.startedAt?.getTime()||now)+this.bound(current.displayDurationMs,1000,120000,9000);
        if(now < done + Math.max(0,Number(c.gapMs)||0)) return;
        current.status=AdvertisementStatus.COMPLETED; current.completedAt=new Date(); current.nextEligibleAt=new Date(done+Math.max(0,Number(c.gapMs)||0));
        await this.ads.save(current);
      }
      const qr=this.db.createQueryRunner(); await qr.connect(); await qr.startTransaction();
      try{
        await qr.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['voice-room-ad-queue']);
        const playing=await qr.manager.findOne(Advertisement,{where:{status:AdvertisementStatus.PLAYING}});
        if(playing){await qr.commitTransaction();return;}
        const next=await qr.manager.createQueryBuilder(Advertisement,'a')
          .where('a.status=:s',{s:AdvertisementStatus.QUEUED})
          .orderBy('a.queueOrder','ASC').addOrderBy('a.createdAt','ASC')
          .setLock('pessimistic_write').setOnLocked('skip_locked').getOne();
        if(!next){await qr.commitTransaction();return;}
        next.status=AdvertisementStatus.PLAYING; next.startedAt=new Date(); next.displayDurationMs=this.bound(next.displayDurationMs,1000,120000,9000);
        await qr.manager.save(next); await qr.commitTransaction();
        this.realtime.server.to('rooms:all').emit('room:advertisement',{type:'start',advertisement:this.publicAd(next),config:c,at:next.startedAt.toISOString()});
      }catch(e){await qr.rollbackTransaction();}finally{await qr.release();}
    }catch(e){this.log.warn('Advertisement tick failed: '+String((e as any)?.message||e));}
    finally{this.busy=false;}
  }

  async patchConfig(input:Record<string,unknown>) {
    const c=await this.config();
    const merged:any={...c};
    const num=(k:string,min:number,max:number)=>{if(input[k]!==undefined)merged[k]=this.bound(input[k],min,max,min);};
    [['dailyLimit',0,1000],['maxQueueSize',1,5000],['maxTextLength',1,500],['displayDurationMs',1000,120000],['gapMs',0,120000],['entrySpeedPxPerSec',20,800],['exitSpeedPxPerSec',20,800],['backgroundOpacity',0,100],['radiusDp',0,40],['heightDp',32,100],['avatarDp',20,56],['nameSp',8,24],['textSp',9,30],['spacingDp',0,30]].forEach(x=>num(x[0] as string,x[1] as number,x[2] as number));
    ['enabled','sendButtonEnabled','paused','reducedMotionStatic'].forEach(k=>{if(input[k]!==undefined)merged[k]=input[k]===true;});
    ['resetTimezone','template','position','direction','background','backgroundImage','avatarShape','nameColor','textColor'].forEach(k=>{if(input[k]!==undefined)merged[k]=String(input[k]??'');});
    await this.db.query('UPDATE "jeho_own"."app_settings" SET "value"=$1,"updatedAt"=NOW() WHERE "key"=$2',[JSON.stringify(merged),'voice_room_ads_config']);
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
    const ad=await this.ads.findOne({where:{id}}); if(!ad)throw new BadRequestException('الإعلان غير موجود');
    if(body.action==='remove'){if(ad.status===AdvertisementStatus.PLAYING)throw new ConflictException('لا يمكن حذف الإعلان الجاري');ad.status=AdvertisementStatus.REMOVED;await this.ads.save(ad);return{ok:true};}
    if(body.action==='priority'){ad.queueOrder=Math.max(0,Number(body.queueOrder)||0);await this.ads.save(ad);return{ok:true};}
    return{ok:false};
  }

  async adminQueue(limit=50){
    const [items,total]=await this.ads.findAndCount({where:[{status:AdvertisementStatus.PLAYING},{status:AdvertisementStatus.QUEUED}],order:{queueOrder:'ASC',createdAt:'ASC'},take:limit});
    return{total,items:items.map(a=>this.publicAd(a))};
  }

  async adminStats(days=7){
    const sql = `SELECT
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day'))::int AS total,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status='completed')::int AS displayed,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND status='rejected')::int AS rejected,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND rejectionCode='blocked_word')::int AS blockedWordAttempts,
      COUNT(*) FILTER (WHERE "createdAt" >= NOW() - ($1::int * INTERVAL '1 day') AND rejectionCode='duplicate')::int AS duplicateAttempts
      FROM "jeho_own"."room_advertisements"`;
    return (await this.db.query(sql,[days]))?.[0]||{};
  }

  private publicAd(a:Advertisement){return{id:a.id,senderId:a.senderId,senderName:a.senderName,senderImageUrl:a.senderImageUrl,text:a.text,status:a.status,queueOrder:Number(a.queueOrder||0),createdAt:a.createdAt,startedAt:a.startedAt,completedAt:a.completedAt,displayDurationMs:a.displayDurationMs};}
  private async getBannedWords(){const r=await this.db.query('SELECT "value" FROM "jeho_own"."app_settings" WHERE "key"=$1 LIMIT 1',['voice_room_ads_banned_words']);return String(r?.[0]?.value||'').split(',').map(x=>x.trim()).filter(Boolean);}
  private normalize(input:string){return String(input||'').normalize('NFKC').toLocaleLowerCase('ar').replace(/[^\p{L}\p{N}]/gu,'').replace(/[إأآٱ]/g,'ا').replace(/ى/g,'ي').replace(/ؤ/g,'و').replace(/ئ/g,'ي').replace(/ة/g,'ه');}
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