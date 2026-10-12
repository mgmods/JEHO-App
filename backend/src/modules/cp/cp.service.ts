import { BadRequestException, ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { DataSource } from 'typeorm';
import { CpType, CpRelationshipStatus } from '../../database/entities/cp-relationship.entity';
import { CpRequestStatus } from '../../database/entities/cp-request.entity';

const TYPES = new Set<string>(Object.values(CpType));
const EXPIRY_MINUTES = 30;

@Injectable()
export class CpService {
  constructor(private readonly dataSource: DataSource) {}

  async discover(type?: CpType) {
    if (type && !TYPES.has(type)) throw new BadRequestException('نوع CP غير صالح');
    const where = type ? 'AND r.type = $1' : '';
    const args = type ? [type] : [];
    return this.dataSource.query(
      `SELECT r.id, r.type, r.points::text AS points, r.level, r."startedAt",
        json_build_object('id', a.id, 'username', a.username, 'displayName', a."displayName", 'avatarUrl', a."avatarUrl") AS "userA",
        json_build_object('id', b.id, 'username', b.username, 'displayName', b."displayName", 'avatarUrl', b."avatarUrl") AS "userB"
       FROM cp_relationships r
       JOIN users a ON a.id = r."userAId" JOIN users b ON b.id = r."userBId"
       WHERE r.status = 'active' ${where}
       ORDER BY r.points DESC, r."startedAt" ASC LIMIT 100`,
      args,
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
      const existing = await manager.query('SELECT * FROM cp_requests WHERE "idempotencyKey" = $1 FOR UPDATE', [key]);
      if (existing.length) {
        if (existing[0].fromUserId !== userId || existing[0].toUserId !== recipientId) {
          throw new ConflictException('مفتاح العملية مستخدم لطلب آخر');
        }
        return existing[0];
      }
      const [room] = await manager.query('SELECT id, type, status, "hostId" FROM rooms WHERE id = $1 FOR SHARE', [roomId]);
      if (!room || room.type !== 'voice' || room.status === 'closed') {
        throw new BadRequestException('يجب اختيار غرفة صوتية فعالة');
      }
      const presence = await manager.query(
        `SELECT id FROM (SELECT id FROM room_seats WHERE "roomId" = $1 AND "userId" = ANY($2::uuid[]) AND status IN ('occupied','muted')
         UNION SELECT id FROM rooms WHERE id = $1 AND "hostId" = ANY($2::uuid[])) p`,
        [roomId, [userId, recipientId]],
      );
      const present = await manager.query(
        `SELECT count(DISTINCT member_id)::int AS n FROM (
          SELECT "userId" AS member_id FROM room_seats WHERE "roomId" = $1 AND "userId" = ANY($2::uuid[]) AND status IN ('occupied','muted')
          UNION SELECT "hostId" AS member_id FROM rooms WHERE id = $1 AND "hostId" = ANY($2::uuid[])
        ) s`,
        [roomId, [userId, recipientId]],
      );
      if (Number(present[0]?.n || 0) !== 2) throw new BadRequestException('يجب أن يكون الطرفان موجودين في الغرفة نفسها');
      const [priceRow] = await manager.query('SELECT value FROM app_settings WHERE key = $1', [`cp.price.${type}`]);
      const price = Number(priceRow?.value);
      if (!Number.isSafeInteger(price) || price <= 0) {
        throw new BadRequestException('سعر هذا النوع غير محدد من لوحة التحكم بعد');
      }
      const [wallet] = await manager.query('SELECT id, coins FROM wallets WHERE "userId" = $1 FOR UPDATE', [userId]);
      if (!wallet) throw new NotFoundException('المحفظة غير موجودة');
      const balance = Number(wallet.coins);
      if (balance < price) throw new BadRequestException('رصيد العملات غير كافٍ');
      const [user] = await manager.query('SELECT id FROM users WHERE id = $1 AND status = $2', [recipientId, 'active']);
      if (!user) throw new NotFoundException('المستخدم المستهدف غير موجود أو غير نشط');
      const [request] = await manager.query(
        `INSERT INTO cp_requests ("fromUserId","toUserId",type,status,"roomId","priceCoins","idempotencyKey","expiresAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'pending',$4,$5,$6,now() + ($7 || ' minutes')::interval,now(),now()) RETURNING *`,
        [userId, recipientId, type, roomId, String(price), key, String(EXPIRY_MINUTES)],
      );
      await manager.query('UPDATE wallets SET coins = coins - $1, "updatedAt" = now() WHERE "userId" = $2', [price, userId]);
      await manager.query(
        `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
         VALUES ($1,'gift_send','coins',$2,$3,'cp_request',$4,$5,$6::json,now())`,
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
        throw new ConflictException('انتهت مهلة الطلب وتم رد المبلغ');
      }
      if (!accept) {
        await this.refund(manager, request);
        await manager.query(`UPDATE cp_requests SET status='rejected',"respondedAt"=now(),"updatedAt"=now() WHERE id=$1`, [requestId]);
        return { id: requestId, status: 'rejected', refunded: true };
      }
      const [relationship] = await manager.query(
        `INSERT INTO cp_relationships ("userAId","userBId",type,status,points,level,"startedAt","createdAt","updatedAt")
         VALUES ($1,$2,$3,'active',0,1,now(),now(),now()) RETURNING *`,
        [request.fromUserId, request.toUserId, request.type],
      );
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
    await manager.query('UPDATE wallets SET coins=coins+$1,"updatedAt"=now() WHERE "userId"=$2', [amount, request.fromUserId]);
    await manager.query(
      `INSERT INTO wallet_transactions ("userId",type,currency,amount,"balanceAfter","referenceType","referenceId",description,metadata,"createdAt")
       VALUES ($1,'refund','coins',$2,$3,'cp_request_refund',$4,'استرداد هدية CP',$5::json,now())
       ON CONFLICT ("userId","referenceType","referenceId") WHERE "referenceType" IS NOT NULL AND "referenceId" IS NOT NULL DO NOTHING`,
      [request.fromUserId, amount, balance + amount, request.id, JSON.stringify({ cpRequestId: request.id, reason: request.status })],
    );
    await manager.query('UPDATE cp_requests SET "refundedAt"=now(),"updatedAt"=now() WHERE id=$1 AND "refundedAt" IS NULL', [request.id]);
  }
}
