import { BadRequestException, ConflictException, Injectable, NotFoundException, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { CpType } from '../../database/entities/cp-relationship.entity';
import { CpRequestStatus } from '../../database/entities/cp-request.entity';

const TYPES = new Set<string>(Object.values(CpType));
const EXPIRY_MINUTES = 30;

@Injectable()
export class CpService implements OnModuleInit, OnModuleDestroy {
  private expiryTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private readonly dataSource: DataSource) {}

  onModuleInit() {
    // Expiry processing only touches CP requests and their wallet ledger rows.
    this.expiryTimer = setInterval(() => {
      void this.expireDueRequests().catch(() => undefined);
    }, 60_000);
    this.expiryTimer.unref?.();
    void this.expireDueRequests().catch(() => undefined);
  }

  onModuleDestroy() {
    if (this.expiryTimer) clearInterval(this.expiryTimer);
    this.expiryTimer = null;
  }


  async catalog() {
    const rows = await this.dataSource.query(
      `SELECT key,value FROM app_settings WHERE key LIKE 'cp.%' ORDER BY key`,
    );
    const settings: Record<string, string> = Object.fromEntries(rows.map((row: any) => [row.key, String(row.value)]));
    const catalog: Array<Record<string, unknown>> = [];
    for (const type of Object.values(CpType)) {
      const price = Number(settings[`cp.price.${type}`]);
      const giftId = settings[`cp.gift.${type}`] || null;
      const [gift] = giftId ? await this.dataSource.query(
        'SELECT id,name,"iconUrl","animationUrl" FROM gifts WHERE id=$1',
        [giftId],
      ) : [];
      const enabled = settings[`cp.enabled.${type}`] === 'true'
        && Number.isSafeInteger(price) && price > 0 && !!gift;
      catalog.push({
        type, enabled, priceCoins: enabled ? price : null,
        gift: gift ? { id: gift.id, name: gift.name, iconUrl: gift.iconUrl, animationUrl: gift.animationUrl } : null,
        giftPoints: Number(settings[`cp.gift_points.${type}`] || 0) || 0,
      });
    }
    return catalog;
  }

  async discover(type?: CpType) {
    if (type && !TYPES.has(type)) throw new BadRequestException('نوع CP غير صالح');
    const where = type ? 'AND r.type = $1' : '';
    const args = type ? [type] : [];
    const rows = await this.dataSource.query(
      `SELECT r.id, r.type, r.points::text AS points, r.level, r."startedAt",
        json_build_object('id', a.id, 'username', a.username, 'displayName', a."displayName", 'avatarUrl', a."avatarUrl") AS "userA",
        json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl") AS "userB"
       FROM cp_relationships r
       JOIN users a ON a.id = r."userAId" JOIN users b ON b.id = r."userBId"
       WHERE r.status = 'active' ${where}
       ORDER BY r.points DESC, r."startedAt" ASC LIMIT 100`,
      args,
    );
    const types: string[] = Array.from(new Set<string>(rows.map((row: any) => String(row.type))));
    const configs: Record<string, Array<{ level: number; pointsRequired: number; reward?: string }>> = {};
    for (const itemType of types) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.levels.${itemType}`],
      );
      try { configs[itemType] = JSON.parse(setting?.value || '[]'); }
      catch { configs[itemType] = []; }
    }
    return rows.map((row: any) => {
      const points = BigInt(String(row.points || 0));
      const levels = (configs[row.type] || []).slice().sort((a, b) => Number(a.pointsRequired) - Number(b.pointsRequired));
      const currentThreshold = levels.filter(item => Number(item.level) <= Number(row.level)).reduce((max, item) => Math.max(max, Number(item.pointsRequired) || 0), 0);
      const next = levels.find(item => Number(item.level) > Number(row.level));
      const nextThreshold = next ? BigInt(Math.max(0, Number(next.pointsRequired) || 0)) : null;
      const span = nextThreshold === null ? 0n : (nextThreshold - BigInt(currentThreshold));
      const progressed = points > BigInt(currentThreshold) ? points - BigInt(currentThreshold) : 0n;
      const progressPercent = span > 0n ? Math.max(0, Math.min(100, Number((progressed * 10000n) / span) / 100)) : 100;
      const startedAt = new Date(row.startedAt);
      const durationDays = Number.isNaN(startedAt.getTime()) ? 0 : Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 86400000));
      const rewardsUnlocked = levels.filter(item => Number(item.level) <= Number(row.level) && item.reward).map(item => ({ level: item.level, reward: item.reward }));
      return {
        ...row,
        durationDays,
        nextLevel: next ? Number(next.level) : null,
        nextLevelPoints: nextThreshold === null ? null : nextThreshold.toString(),
        progressPercent,
        rewardsUnlocked,
      };
    });
  }

  async profileRelationships(userId: string) {
    return this.dataSource.query(
      `SELECT r.id,r.type,r.points::text AS points,r.level,r."startedAt",
       json_build_object('id',p.id,'username',p.username,'displayName',p."displayName",'avatarUrl',p."avatarUrl") AS partner
       FROM cp_relationships r
       JOIN users p ON p.id=CASE WHEN r."userAId"=$1 THEN r."userBId" ELSE r."userAId" END
       WHERE (r."userAId"=$1 OR r."userBId"=$1) AND r.status='active'
       ORDER BY r.points DESC,r."startedAt" ASC`,
      [userId],
    );
  }

  async forUser(userId: string) {
    return this.dataSource.query(
      `SELECT r.*, json_build_object('id', p.id, 'username', p.username, 'displayName', p."displayName", 'avatarUrl', p."avatarUrl") AS partner
       FROM cp_relationships r
       JOIN users p ON p.id = CASE WHEN r."userAId" = $1 THEN r."userBId" ELSE r."userAId" END
       WHERE (r."userAId" = $1 OR r."userBId" = $1)
       ORDER BY (r.status = 'active') DESC, r."startedAt" DESC`,
      [userId],
    );
  }


  async sentRequests(userId: string) {
    await this.expireDueRequests();
    return this.dataSource.query(
      `SELECT q.*, json_build_object('id',u.id,'username',u.username,'displayName',u."displayName",'avatarUrl',u."avatarUrl") AS recipient
       FROM cp_requests q JOIN users u ON u.id=q."toUserId"
       WHERE q."fromUserId"=$1 AND q.status IN ('pending','accepted','rejected','expired','cancelled')
       ORDER BY q."createdAt" DESC LIMIT 100`,
      [userId],
    );
  }

  async cancelRequest(userId: string, requestId: string) {
    return this.dataSource.transaction(async manager => {
      const [request] = await manager.query('SELECT * FROM cp_requests WHERE id=$1 FOR UPDATE', [requestId]);
      if (!request) throw new NotFoundException('طلب CP غير موجود');
      if (request.fromUserId !== userId) throw new BadRequestException('لا يمكنك إلغاء طلب ليس لك');
      if (request.status !== CpRequestStatus.PENDING) throw new ConflictException('لا يمكن إلغاء هذا الطلب بعد معالجته');
      await this.refund(manager, request);
      await manager.query(`UPDATE cp_requests SET status='cancelled',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
      return { id: requestId, status: 'cancelled', refunded: true };
    });
  }

  async requests(userId: string) {
    await this.expireDueRequests();
    return this.dataSource.query(
      `SELECT q.*, json_build_object('id', u.id, 'username', u.username, 'displayName', u."displayName", 'avatarUrl', u."avatarUrl") AS sender
       FROM cp_requests q JOIN users u ON u.id = q."fromUserId"
       WHERE q."toUserId" = $1 AND q.status = 'pending' ORDER BY q."createdAt" DESC`,
      [userId],
    );
  }

  async createRequest(userId: string, input: { recipientId: string; type: CpType; roomId: string; idempotencyKey: string }) {
    const recipientId = String(input?.recipientId || '');
    const roomId = String(input?.roomId || '');
    const key = String(input?.idempotencyKey || '').trim();
    const type = input?.type;
    if (!recipientId || !roomId || !key || key.length > 128 || !TYPES.has(type)) {
      throw new BadRequestException('بيانات طلب CP غير مكتملة');
    }
    if (recipientId === userId) throw new BadRequestException('لا يمكنك إنشاء CP مع حسابك');
    return this.dataSource.transaction(async manager => {
      const pairKey = [userId, recipientId].sort().join(':') + ':' + type;
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1), 0)', [pairKey]);
      const existing = await manager.query('SELECT * FROM cp_requests WHERE "idempotencyKey" = $1 FOR UPDATE', [key]);
      if (existing.length) {
        if (existing[0].fromUserId !== userId || existing[0].toUserId !== recipientId || existing[0].type !== type || existing[0].roomId !== roomId) {
          throw new ConflictException('مفتاح العملية مستخدم لطلب آخر');
        }
        return existing[0];
      }
      const [room] = await manager.query('SELECT id, type, status, "hostId" FROM rooms WHERE id = $1 FOR SHARE', [roomId]);
      if (!room || room.type !== 'voice' || room.status === 'closed') {
        throw new BadRequestException('يجب اختيار غرفة صوتية فعالة');
      }
      const present = await manager.query(
        `SELECT count(DISTINCT member_id)::int AS n FROM (
          SELECT "userId" AS member_id FROM room_seats WHERE "roomId" = $1 AND "userId" = ANY($2::uuid[]) AND status IN ('occupied','muted')
          UNION SELECT "hostId" AS member_id FROM rooms WHERE id = $1 AND "hostId" = ANY($2::uuid[])
        ) s`,
        [roomId, [userId, recipientId]],
      );
      if (Number(present[0]?.n || 0) !== 2) throw new BadRequestException('يجب أن يكون الطرفان موجودين في الغرفة نفسها');
      const duplicate = await manager.query(
        `SELECT id FROM cp_requests WHERE status='pending' AND type=$1
         AND (("fromUserId"=$2 AND "toUserId"=$3) OR ("fromUserId"=$3 AND "toUserId"=$2)) LIMIT 1 FOR UPDATE`,
        [type, userId, recipientId],
      );
      if (duplicate.length) throw new ConflictException('يوجد طلب CP معلّق بين الحسابين بالفعل');
      const [enabledRow] = await manager.query('SELECT value FROM app_settings WHERE key = $1', [`cp.enabled.${type}`]);
      if (String(enabledRow?.value || '').toLowerCase() !== 'true') {
        throw new BadRequestException('هذا النوع غير مفعّل من لوحة التحكم بعد');
      }
      const [expiryRow] = await manager.query('SELECT value FROM app_settings WHERE key = $1', ['cp.policy.request_expiry_minutes']);
      const configuredExpiry = Number(expiryRow?.value);
      const expiryMinutes = Number.isInteger(configuredExpiry) && configuredExpiry >= 5 && configuredExpiry <= 1440
        ? configuredExpiry : EXPIRY_MINUTES;
      const [priceRow] = await manager.query('SELECT value FROM app_settings WHERE key = $1', [`cp.price.${type}`]);
      const price = Number(priceRow?.value);
      if (!Number.isSafeInteger(price) || price <= 0) {
        throw new BadRequestException('سعر هذا النوع غير محدد من لوحة التحكم بعد');
      }
      const [giftSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift.${type}`]);
      const [gift] = giftSetting?.value
        ? await manager.query('SELECT id,name,"iconUrl","animationUrl" FROM gifts WHERE id=$1', [giftSetting.value])
        : [];
      if (!gift) throw new BadRequestException('يجب تحديد هدية فعلية لهذا النوع من لوحة التحكم');
      const [wallet] = await manager.query('SELECT id, coins FROM wallets WHERE "userId" = $1 FOR UPDATE', [userId]);
      if (!wallet) throw new NotFoundException('المحفظة غير موجودة');
      const balance = Number(wallet.coins);
      if (balance < price) throw new BadRequestException('رصيد العملات غير كافٍ');
      const [user] = await manager.query('SELECT id FROM users WHERE id = $1 AND status = $2', [recipientId, 'active']);
      if (!user) throw new NotFoundException('المستخدم المستهدف غير موجود أو غير نشط');
      const [request] = await manager.query(
        `INSERT INTO cp_requests ("fromUserId","toUserId",type,status,"roomId","priceCoins","giftId","giftName","giftIconUrl","giftAnimationUrl","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',$4,$5,$6,$7,$8,$9,$10,now() + ($11 || ' minutes')::interval,now(),now()) RETURNING *`,
        [userId, recipientId, type, roomId, String(price), gift.id, gift.name, gift.iconUrl, gift.animationUrl, key, String(expiryMinutes)],
      );
      await manager.query('UPDATE wallets SET coins = coins - $1, "updatedAt" = now() WHERE "userId" = $2', [price, userId]);
      await manager.query(
        `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
         VALUES ($1,'gift_send','coins',$2,$3,'cp_request',$4,$5,$6,now())`,
        [userId, price, balance - price, request.id, `هدية بدء CP - ${type}`, JSON.stringify({ type, recipientId, roomId })],
      );
      return request;
    });
  }

  async respond(userId: string, requestId: string, accept: boolean) {
    return this.dataSource.transaction(async manager => {
      const [request] = await manager.query('SELECT * FROM cp_requests WHERE id = $1 FOR UPDATE', [requestId]);
      if (!request) throw new NotFoundException('طلب CP غير موجود');
      if (request.toUserId !== userId) throw new BadRequestException('لا يمكنك الرد على هذا الطلب');
      if (request.status !== CpRequestStatus.PENDING) throw new ConflictException('تمت معالجة هذا الطلب مسبقاً');
      if (new Date(request.expiresAt).getTime() <= Date.now()) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='expired',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'expired', refunded: true };
      }
      if (!accept) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'rejected', refunded: true };
      }
      const active = await manager.query(
        `SELECT id FROM cp_relationships WHERE status='active' AND type=$1
         AND (("userAId"=$2 AND "userBId"=$3) OR ("userAId"=$3 AND "userBId"=$2)) LIMIT 1 FOR UPDATE`,
        [request.type, request.fromUserId, request.toUserId],
      );
      if (active.length) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'rejected', refunded: true, reason: 'relationship_already_active' };
      }
      const [relationship] = await manager.query(
        `INSERT INTO cp_relationships ("userAId","userBId",type,status,points,level,"startedAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'active',0,1,now(),now(),now()) RETURNING *`,
        [request.fromUserId, request.toUserId, request.type],
      );
      const [giftPointsSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.gift_points.${request.type}`]);
      const initialPoints = Number(giftPointsSetting?.value);
      if (Number.isSafeInteger(initialPoints) && initialPoints > 0) {
        await manager.query(
          `INSERT INTO cp_point_events ("relationshipId","eventType","referenceType","referenceId",points,metadata,"createdAt")
           VALUES ($1,'initiation_gift','cp_request',$2,$3,$4::jsonb,now())
           ON CONFLICT ("referenceType","referenceId") DO NOTHING`,
          [relationship.id, request.id, String(initialPoints), JSON.stringify({ giftId: request.giftId, giftName: request.giftName })],
        );
        const [levelsSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${request.type}`]);
        let computedLevel = 1;
        try {
          const levels = JSON.parse(levelsSetting?.value || '[]') as Array<{ level: number; pointsRequired: number }>;
          for (const item of levels) if (Number(item.pointsRequired) <= initialPoints) computedLevel = Math.max(computedLevel, Number(item.level) || 1);
        } catch { /* keep safe baseline level */ }
        const [maxSetting] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.max_level.${request.type}`]);
        const configuredMax = Number(maxSetting?.value);
        if (Number.isInteger(configuredMax) && configuredMax > 0) computedLevel = Math.min(computedLevel, configuredMax);
        await manager.query('UPDATE cp_relationships SET points=$1,level=$2,"updatedAt"=now() WHERE id=$3', [String(initialPoints), computedLevel, relationship.id]);
        relationship.points = String(initialPoints);
        relationship.level = computedLevel;
      }
      await manager.query(`UPDATE cp_requests SET status='accepted',"respondedAt"=now(),"relationshipId"=$2,"updatedAt"=now() WHERE id=$1`, [requestId, relationship.id]);
      return { requestId, status: 'accepted', relationship };
    });
  }

  async end(userId: string, relationshipId: string) {
    const result = await this.dataSource.query(
      `UPDATE cp_relationships SET status='ended',"endedAt"=now(),"endedByUserId"=$1,"updatedAt"=now()
       WHERE id=$2 AND status='active' AND ($1 = "userAId" OR $1 = "userBId") RETURNING id,type,status,"endedAt"`,
      [userId, relationshipId],
    );
    if (!result.length) throw new NotFoundException('العلاقة غير موجودة أو منتهية');
    return result[0];
  }



  /** Award configured points when a committed normal gift is exchanged by active CP partners. */
  async recordGiftInteraction(senderId: string, receiverId: string, giftSendId: string, totalCoins: number) {
    if (!senderId || !receiverId || !giftSendId || senderId === receiverId) return;
    const relationships = await this.dataSource.query(
      `SELECT id,type FROM cp_relationships WHERE status='active'
       AND (("userAId"=$1 AND "userBId"=$2) OR ("userAId"=$2 AND "userBId"=$1))`,
      [senderId, receiverId],
    );
    for (const relationship of relationships) {
      const [setting] = await this.dataSource.query(
        'SELECT value FROM app_settings WHERE key=$1',
        [`cp.points.gift_send.${relationship.type}`],
      );
      const points = Number(setting?.value);
      if (!Number.isSafeInteger(points) || points <= 0) continue;
      await this.recordPoints(
        relationship.id, 'gift_send', 'gift_send', `${giftSendId}:${relationship.id}`, points,
        { senderId, receiverId, giftSendId, totalCoins: Math.max(0, Number(totalCoins) || 0) },
      );
    }
  }

  /** Record a server-verified, idempotent points event; never exposed as a user-award endpoint. */
  async recordPoints(
    relationshipId: string,
    eventType: string,
    referenceType: string,
    referenceId: string,
    points: number,
    metadata: Record<string, unknown> = {},
  ) {
    if (!Number.isSafeInteger(points) || points < 0 || !eventType || !referenceType || !referenceId) {
      throw new BadRequestException('حدث النقاط غير صالح');
    }
    return this.dataSource.transaction(async manager => {
      const [relationship] = await manager.query(
        'SELECT * FROM cp_relationships WHERE id=$1 AND status=\'active\' FOR UPDATE',
        [relationshipId],
      );
      if (!relationship) throw new NotFoundException('العلاقة غير نشطة');
      const inserted = await manager.query(
        `INSERT INTO cp_point_events ("relationshipId","eventType","referenceType","referenceId",points,metadata,"createdAt")
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,now())
         ON CONFLICT ("referenceType","referenceId") DO NOTHING RETURNING id`,
        [relationshipId, eventType, referenceType, referenceId, String(points), JSON.stringify(metadata)],
      );
      if (!inserted.length) return { relationshipId, duplicate: true, points: relationship.points, level: relationship.level };
      const total = BigInt(String(relationship.points)) + BigInt(points);
      const [levelConfig] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.levels.${relationship.type}`]);
      let level = 1;
      try {
        const levels = JSON.parse(levelConfig?.value || '[]') as Array<{ level: number; pointsRequired: number }>;
        for (const item of levels) if (Number(item.pointsRequired) <= Number(total)) level = Math.max(level, Number(item.level) || 1);
      } catch { /* malformed admin config never blocks point ledger writes */ }
      const [maxRow] = await manager.query('SELECT value FROM app_settings WHERE key=$1', [`cp.max_level.${relationship.type}`]);
      const maxLevel = Number(maxRow?.value);
      if (Number.isInteger(maxLevel) && maxLevel > 0) level = Math.min(level, maxLevel);
      await manager.query(
        'UPDATE cp_relationships SET points=$1,level=$2,"updatedAt"=now() WHERE id=$3',
        [total.toString(), level, relationshipId],
      );
      return { relationshipId, duplicate: false, points: total.toString(), level };
    });
  }

  private async expireDueRequests() {
    const due = await this.dataSource.query(
      `SELECT id FROM cp_requests WHERE status='pending' AND "expiresAt" <= now() ORDER BY "expiresAt" LIMIT 100`,
    );
    for (const row of due) {
      await this.dataSource.transaction(async manager => {
        const [request] = await manager.query('SELECT * FROM cp_requests WHERE id=$1 FOR UPDATE', [row.id]);
        if (!request || request.status !== 'pending' || new Date(request.expiresAt).getTime() > Date.now()) return;
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='expired',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1 AND status='pending'`, [row.id]);
      });
    }
  }

  private async refund(manager: any, request: any) {
    if (request.refundedAt) return;
    const [wallet] = await manager.query('SELECT coins FROM wallets WHERE "userId"=$1 FOR UPDATE', [request.fromUserId]);
    if (!wallet) throw new NotFoundException('محفظة صاحب الطلب غير موجودة');
    const balance = Number(wallet.coins);
    const amount = Number(request.priceCoins);
    const inserted = await manager.query(
      `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
       VALUES ($1,'refund','coins',$2,$3,'cp_request_refund',$4,'استرداد هدية CP',$5,now())
       ON CONFLICT ("userId","referenceType","referenceId") WHERE "referenceType" IS NOT NULL AND "referenceId" IS NOT NULL DO NOTHING
       RETURNING id`,
      [request.fromUserId, amount, balance + amount, request.id, JSON.stringify({ cpRequestId: request.id, reason: request.status })],
    );
    if (inserted.length) {
      await manager.query('UPDATE wallets SET coins=coins+$1,"updatedAt"=now() WHERE "userId"=$2', [amount, request.fromUserId]);
    }
    await manager.query('UPDATE cp_requests SET "refundedAt"=now(),"updatedAt"=now() WHERE id=$1 AND "refundedAt" IS NULL', [request.id]);
  }
}
