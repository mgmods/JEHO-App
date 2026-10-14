import {
  Injectable,
  BadRequestException,
  NotFoundException,
  ConflictException,
} from '@nestjs/common';
import { DataSource } from 'typeorm';

type BadgeInput = {
  code?: string;
  name?: string;
  description?: string | null;
  imageUrl?: string | null;
  isActive?: boolean;
  sortOrder?: number;
};

@Injectable()
export class BadgesService {
  constructor(private readonly dataSource: DataSource) {}

  async listCatalog(userId?: string) {
    const rows = await this.dataSource.query(
      `SELECT b.id, b.code, b.name, b.description, b."imageUrl",
              b."isActive", b."sortOrder", b."createdAt",
              (ub.id IS NOT NULL) AS earned,
              ub."awardedAt" AS "awardedAt"
       FROM badge_definitions b
       LEFT JOIN user_badges ub
         ON ub."badgeId" = b.id AND ub."userId" = $1 AND ub."isRevoked" = false
       WHERE b."isActive" = true
       ORDER BY b."sortOrder" ASC, b."createdAt" ASC`,
      [userId || null],
    );
    return rows;
  }

  async listUserBadges(userId: string) {
    return this.dataSource.query(
      `SELECT b.id, b.code, b.name, b.description, b."imageUrl",
              ub."awardedAt", ub.source
       FROM user_badges ub
       INNER JOIN badge_definitions b ON b.id = ub."badgeId"
       WHERE ub."userId" = $1 AND ub."isRevoked" = false AND b."isActive" = true
       ORDER BY ub."awardedAt" ASC`,
      [userId],
    );
  }

  async adminList() {
    return this.dataSource.query(
      `SELECT b.*,
              (SELECT COUNT(*)::int FROM user_badges ub
               WHERE ub."badgeId" = b.id AND ub."isRevoked" = false) AS "earnedCount"
       FROM badge_definitions b
       ORDER BY b."sortOrder" ASC, b."createdAt" DESC`,
    );
  }

  async saveDefinition(input: BadgeInput, id?: string) {
    const code = String(input.code || '').trim().toLowerCase();
    const name = String(input.name || '').trim();
    if (!id && !/^[a-z0-9][a-z0-9_-]{1,63}$/.test(code)) {
      throw new BadRequestException('Badge code must be 2-64 lowercase letters, numbers, _ or -');
    }
    if (!name || name.length > 100) {
      throw new BadRequestException('Badge name is required (max 100 characters)');
    }
    const description = input.description == null ? null : String(input.description).trim().slice(0, 1000);
    const imageUrl = input.imageUrl == null ? null : String(input.imageUrl).trim().slice(0, 2048);
    if (id) {
      const exists = await this.dataSource.query('SELECT id FROM badge_definitions WHERE id = $1 LIMIT 1', [id]);
      if (!exists.length) throw new NotFoundException('Badge not found');
      const rows = await this.dataSource.query(
        `UPDATE badge_definitions SET name=$2, description=$3, "imageUrl"=$4,
          "isActive"=$5, "sortOrder"=$6, "updatedAt"=NOW()
         WHERE id=$1 RETURNING *`,
        [id, name, description, imageUrl, input.isActive !== false, Number.isFinite(Number(input.sortOrder)) ? Number(input.sortOrder) : 0],
      );
      return rows[0];
    }
    try {
      const rows = await this.dataSource.query(
        `INSERT INTO badge_definitions (code, name, description, "imageUrl", "isActive", "sortOrder")
         VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
        [code, name, description, imageUrl, input.isActive !== false, Number.isFinite(Number(input.sortOrder)) ? Number(input.sortOrder) : 0],
      );
      return rows[0];
    } catch (error: any) {
      if (error?.code === '23505') throw new ConflictException('Badge code already exists');
      throw error;
    }
  }

  async grant(userId: string, badgeId: string, awardedBy: string, note?: string) {
    const [user] = await this.dataSource.query('SELECT id FROM users WHERE id=$1 LIMIT 1', [userId]);
    const [badge] = await this.dataSource.query('SELECT id FROM badge_definitions WHERE id=$1 AND "isActive"=true LIMIT 1', [badgeId]);
    if (!user) throw new NotFoundException('User not found');
    if (!badge) throw new NotFoundException('Active badge not found');
    const [existing] = await this.dataSource.query(
      'SELECT id FROM user_badges WHERE "userId"=$1 AND "badgeId"=$2 AND "isRevoked"=false LIMIT 1',
      [userId, badgeId],
    );
    if (existing) return { granted: false, alreadyEarned: true };
    const rows = await this.dataSource.query(
      `INSERT INTO user_badges ("userId","badgeId",source,"awardedBy",note)
       VALUES ($1,$2,'manual',$3,$4) RETURNING *`,
      [userId, badgeId, awardedBy, note ? String(note).slice(0, 500) : null],
    );
    return { granted: true, badge: rows[0] };
  }

  async revoke(userId: string, badgeId: string) {
    const rows = await this.dataSource.query(
      `UPDATE user_badges SET "isRevoked"=true, "revokedAt"=NOW()
       WHERE "userId"=$1 AND "badgeId"=$2 AND "isRevoked"=false RETURNING id`,
      [userId, badgeId],
    );
    if (!rows.length) throw new NotFoundException('Active user badge not found');
    return { revoked: true };
  }
}
