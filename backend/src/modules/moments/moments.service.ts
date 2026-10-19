import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { DataSource } from 'typeorm';

export type MomentVisibility = 'public' | 'followers' | 'friends' | 'private';
export interface CreateMomentDto {
  body?: string;
  imageUrl?: string | null;
  visibility?: MomentVisibility;
}

@Injectable()
export class MomentsService {
  constructor(private readonly dataSource: DataSource) {}

  private async canView(moment: any, viewerId: string): Promise<boolean> {
    if (moment.userId === viewerId || moment.visibility === 'public') return true;
    if (moment.visibility === 'private') return false;
    const [follow] = await this.dataSource.query(
      'SELECT 1 FROM "jeho_own"."follows" WHERE "followerId" = $1 AND "followingId" = $2 LIMIT 1',
      [viewerId, moment.userId],
    );
    if (moment.visibility === 'followers') return !!follow;
    const [friend] = await this.dataSource.query(
      `SELECT 1 FROM "jeho_own"."social_requests"
       WHERE status = 'accepted' AND type = 'friend'
         AND (("fromUserId" = $1 AND "toUserId" = $2) OR ("fromUserId" = $2 AND "toUserId" = $1))
       LIMIT 1`,
      [viewerId, moment.userId],
    ).catch(() => [null]);
    return !!friend;
  }

  private async decorate(rows: any[], viewerId: string) {
    if (!rows.length) return [];
    const ids = rows.map((row) => row.id);
    const [likes, comments] = await Promise.all([
      this.dataSource.query(
        'SELECT "momentId", count(*)::int AS count, bool_or("userId" = $1) AS "likedByMe" FROM "jeho_own"."moment_likes" WHERE "momentId" = ANY($2::uuid[]) GROUP BY "momentId"',
        [viewerId, ids],
      ),
      this.dataSource.query(
        'SELECT "momentId", count(*)::int AS count FROM "jeho_own"."moment_comments" WHERE "momentId" = ANY($1::uuid[]) GROUP BY "momentId"',
        [ids],
      ),
    ]);
    const likeMap = new Map<string, any>(likes.map((x: any) => [String(x.momentId), x] as [string, any]));
    const commentMap = new Map<string, number>(comments.map((x: any) => [String(x.momentId), Number(x.count)] as [string, number]));
    return rows.map((row) => ({
      ...row,
      likeCount: Number(likeMap.get(row.id)?.count ?? 0),
      likedByMe: Boolean(likeMap.get(row.id)?.likedByMe),
      commentCount: commentMap.get(row.id) ?? 0,
    }));
  }

  async feed(viewerId: string, page = 1, limit = 20) {
    const safePage = Math.max(1, Math.trunc(Number(page) || 1));
    const safeLimit = Math.min(50, Math.max(1, Math.trunc(Number(limit) || 20)));
    const rows = await this.dataSource.query(
      `SELECT m.id, m."userId", m.body, m."imageUrl", m.visibility, m."createdAt",
              u.username, u."displayName", u."avatarUrl"
       FROM "jeho_own"."moments" m
       JOIN "jeho_own"."users" u ON u.id = m."userId"
       WHERE u.status = 'active'
         AND (m.visibility = 'public' OR m."userId" = $1
           OR (m.visibility = 'followers' AND EXISTS (
             SELECT 1 FROM "jeho_own"."follows" f WHERE f."followerId" = $1 AND f."followingId" = m."userId"))
           OR (m.visibility = 'friends' AND EXISTS (
             SELECT 1 FROM "jeho_own"."social_requests" s WHERE s.status = 'accepted' AND s.type = 'friend'
             AND ((s."fromUserId" = $1 AND s."toUserId" = m."userId") OR (s."fromUserId" = m."userId" AND s."toUserId" = $1)))))
       ORDER BY m."createdAt" DESC, m.id DESC LIMIT $2 OFFSET $3`,
      [viewerId, safeLimit, (safePage - 1) * safeLimit],
    );
    return this.decorate(rows, viewerId);
  }

  async forUser(userId: string, viewerId: string) {
    const rows = await this.dataSource.query(
      `SELECT m.id, m."userId", m.body, m."imageUrl", m.visibility, m."createdAt",
              u.username, u."displayName", u."avatarUrl"
       FROM "jeho_own"."moments" m JOIN "jeho_own"."users" u ON u.id = m."userId"
       WHERE m."userId" = $1 AND u.status = 'active'
       ORDER BY m."createdAt" DESC, m.id DESC LIMIT 100`,
      [userId],
    );
    const visible: any[] = [];
    for (const row of rows) if (await this.canView(row, viewerId)) visible.push(row);
    return this.decorate(visible, viewerId);
  }

  async create(userId: string, dto: CreateMomentDto) {
    const body = String(dto?.body ?? '').trim();
    const imageUrl = dto?.imageUrl == null ? null : String(dto.imageUrl).trim();
    const visibility = dto?.visibility ?? 'public';
    if (!['public', 'followers', 'friends', 'private'].includes(visibility)) throw new BadRequestException('Invalid visibility');
    if (body.length > 2000) throw new BadRequestException('Moment text is too long');
    if (imageUrl && (imageUrl.length > 1024 || !/^https?:\/\//i.test(imageUrl))) throw new BadRequestException('Image URL must be an uploaded HTTP(S) URL');
    if (!body && !imageUrl) throw new BadRequestException('Add text or an image');
    const [row] = await this.dataSource.query(
      `INSERT INTO "jeho_own"."moments" ("userId", body, "imageUrl", visibility)
       VALUES ($1, $2, $3, $4)
       RETURNING id, "userId", body, "imageUrl", visibility, "createdAt"`,
      [userId, body, imageUrl, visibility],
    );
    const [author] = await this.dataSource.query(
      'SELECT username, "displayName", "avatarUrl" FROM "jeho_own"."users" WHERE id = $1',
      [userId],
    );
    return { ...row, ...author, likeCount: 0, commentCount: 0, likedByMe: false };
  }

  private async visibleMoment(momentId: string, viewerId: string) {
    const [moment] = await this.dataSource.query(
      'SELECT id, "userId", visibility FROM "jeho_own"."moments" WHERE id = $1',
      [momentId],
    );
    if (!moment) throw new NotFoundException('Moment not found');
    if (!(await this.canView(moment, viewerId))) throw new ForbiddenException('You cannot view this moment');
    return moment;
  }

  async toggleLike(momentId: string, userId: string) {
    await this.visibleMoment(momentId, userId);
    const [existing] = await this.dataSource.query(
      'SELECT id FROM "jeho_own"."moment_likes" WHERE "momentId" = $1 AND "userId" = $2',
      [momentId, userId],
    );
    if (existing) {
      await this.dataSource.query('DELETE FROM "jeho_own"."moment_likes" WHERE id = $1', [existing.id]);
      return { liked: false };
    }
    await this.dataSource.query(
      'INSERT INTO "jeho_own"."moment_likes" ("momentId", "userId") VALUES ($1, $2) ON CONFLICT ("momentId", "userId") DO NOTHING',
      [momentId, userId],
    );
    return { liked: true };
  }

  async listComments(momentId: string, viewerId: string) {
    await this.visibleMoment(momentId, viewerId);
    return this.dataSource.query(
      `SELECT c.id, c."momentId", c."userId", c.body, c."createdAt",
              u.username, u."displayName", u."avatarUrl"
       FROM "jeho_own"."moment_comments" c JOIN "jeho_own"."users" u ON u.id = c."userId"
       WHERE c."momentId" = $1 AND u.status = 'active'
       ORDER BY c."createdAt" ASC LIMIT 200`,
      [momentId],
    );
  }

  async addComment(momentId: string, userId: string, bodyValue: unknown) {
    await this.visibleMoment(momentId, userId);
    const body = String(bodyValue ?? '').trim();
    if (!body || body.length > 1000) throw new BadRequestException('Comment must be between 1 and 1000 characters');
    const [row] = await this.dataSource.query(
      `INSERT INTO "jeho_own"."moment_comments" ("momentId", "userId", body)
       VALUES ($1, $2, $3) RETURNING id, "momentId", "userId", body, "createdAt"`,
      [momentId, userId, body],
    );
    const [author] = await this.dataSource.query(
      'SELECT username, "displayName", "avatarUrl" FROM "jeho_own"."users" WHERE id = $1',
      [userId],
    );
    return { ...row, ...author };
  }

  async report(momentId: string, reporterId: string, input: { reason?: unknown; description?: unknown }) {
    const moment = await this.visibleMoment(momentId, reporterId);
    if (moment.userId === reporterId) throw new BadRequestException('You cannot report your own moment');
    const allowedReasons = new Set(['spam', 'harassment', 'inappropriate', 'violence', 'scam', 'other']);
    const reason = String(input?.reason ?? '').trim().toLowerCase();
    if (!allowedReasons.has(reason)) throw new BadRequestException('Choose a valid report reason');
    const note = String(input?.description ?? '').trim().slice(0, 1000);
    const [existing] = await this.dataSource.query(
      `SELECT id FROM "jeho_own"."reports"
       WHERE "reporterId" = $1 AND "targetType"::text = 'moment' AND "targetId" = $2
         AND status::text IN ('pending', 'reviewing')
       LIMIT 1`,
      [reporterId, momentId],
    );
    if (existing) throw new BadRequestException('You have already reported this moment');
    const [snapshot] = await this.dataSource.query(
      'SELECT body, "imageUrl" FROM "jeho_own"."moments" WHERE id = $1',
      [momentId],
    );
    const excerpt = String(snapshot?.body ?? '').trim().slice(0, 1200);
    const image = snapshot?.imageUrl ? String(snapshot.imageUrl).slice(0, 1024) : '';
    const context = [
      'بلاغ عن منشور في اللحظات.',
      excerpt ? `محتوى المنشور: ${excerpt}` : '',
      image ? `صورة المنشور: ${image}` : '',
      note ? `ملاحظات المُبلّغ: ${note}` : '',
    ].filter(Boolean).join('\\n');
    const [created] = await this.dataSource.query(
      `INSERT INTO "jeho_own"."reports"
       ("reporterId", "targetType", "targetId", "roomId", reason, description, status, "createdAt", "updatedAt")
       VALUES ($1, 'moment', $2, NULL, $3, $4, 'pending', now(), now())
       RETURNING id, "targetType", "targetId", reason, status, "createdAt"`,
      [reporterId, momentId, reason, context],
    );
    return { submitted: true, reportId: created.id, status: created.status };
  }

  async remove(momentId: string, userId: string) {
    const [row] = await this.dataSource.query(
      'SELECT "userId" FROM "jeho_own"."moments" WHERE id = $1',
      [momentId],
    );
    if (!row) throw new NotFoundException('Moment not found');
    if (row.userId !== userId) throw new ForbiddenException('Only the author can delete this moment');
    await this.dataSource.query('DELETE FROM "jeho_own"."moments" WHERE id = $1', [momentId]);
    return { deleted: true };
  }
}
