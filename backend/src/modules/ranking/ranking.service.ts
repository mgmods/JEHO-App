import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  RankingSnapshot,
  RankingPeriod,
  RankingCategory,
} from '../../database/entities/ranking-snapshot.entity';
import { Agency } from '../../database/entities/agency.entity';
import { Room } from '../../database/entities/room.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';

type RankingBoardItem = {
  targetId: string;
  targetName: string | null;
  score: number;
  rank?: number;
  meta?: Record<string, unknown> | null;
  user?: {
    id: string;
    username: string;
    displayName: string;
    avatarUrl: string | null;
    level: number;
    hostBadgeUrl: string | null;
    vipBadgeUrl: string | null;
    frameUrl: string | null;
    followersCount: number;
    totalReceivedDiamonds: number;
    nameStyle: Record<string, unknown> | null;
  } | null;
};

@Injectable()
export class RankingService {
  constructor(
    @InjectRepository(RankingSnapshot)
    private readonly snapshotsRepo: Repository<RankingSnapshot>,
    @InjectRepository(Agency)
    private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(Room)
    private readonly roomsRepo: Repository<Room>,
    @InjectRepository(GiftSend)
    private readonly giftSendsRepo: Repository<GiftSend>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(Follow)
    private readonly followsRepo: Repository<Follow>,
  ) {}

  private weekStart(date: Date): Date {
    const start = new Date(Date.UTC(
      date.getUTCFullYear(),
      date.getUTCMonth(),
      date.getUTCDate(),
    ));
    const day = start.getUTCDay() || 7;
    start.setUTCDate(start.getUTCDate() - day + 1);
    return start;
  }

  periodKey(period: RankingPeriod, date = new Date()): string {
    const y = date.getUTCFullYear();
    const m = String(date.getUTCMonth() + 1).padStart(2, '0');
    const d = String(date.getUTCDate()).padStart(2, '0');
    if (period === RankingPeriod.DAILY) return `${y}-${m}-${d}`;
    if (period === RankingPeriod.WEEKLY) {
      const start = this.weekStart(date);
      const thursday = new Date(start);
      thursday.setUTCDate(thursday.getUTCDate() + 3);
      const isoYear = thursday.getUTCFullYear();
      const firstThursday = new Date(Date.UTC(isoYear, 0, 4));
      const firstWeekStart = this.weekStart(firstThursday);
      const week = Math.floor((start.getTime() - firstWeekStart.getTime()) / 604800000) + 1;
      return `${isoYear}-W${String(week).padStart(2, '0')}`;
    }
    return `${y}-${m}`;
  }

  async getBoard(period: RankingPeriod, category: RankingCategory, limit = 50) {
    const key = this.periodKey(period);
    const cached = await this.snapshotsRepo.find({
      where: { period, category, periodKey: key },
      order: { rank: 'ASC' },
      take: limit,
    });
    const cacheIsFresh =
      cached.length > 0 &&
      Date.now() - new Date(cached[0].createdAt).getTime() < 60_000;
    if (cacheIsFresh) {
      const items = this.isUserCategory(category)
        ? await this.withUserNames(
            cached.map((item) => ({
              targetId: item.targetId,
              targetName: item.targetName,
              score: Number(item.score),
              rank: item.rank,
              meta: item.meta,
            })),
          )
        : cached.map((item) => ({ ...item, score: Number(item.score) }));
      return { period, category, periodKey: key, items };
    }

    const items = await this.compute(period, category, limit);
    if (cached.length) {
      await this.snapshotsRepo.delete({ period, category, periodKey: key });
    }
    if (items.length) {
      await this.snapshotsRepo.save(
        items.map((item, idx) =>
          this.snapshotsRepo.create({
            period,
            category,
            periodKey: key,
            targetId: item.targetId,
            targetName: item.targetName,
            rank: idx + 1,
            score: item.score,
            meta: item.meta || null,
          }),
        ),
      );
    }
    return {
      period,
      category,
      periodKey: key,
      items: items.map((item, idx) => ({ ...item, rank: idx + 1 })),
    };
  }

  private since(period: RankingPeriod): Date {
    const now = new Date();
    if (period === RankingPeriod.DAILY) {
      return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    }
    if (period === RankingPeriod.WEEKLY) {
      return this.weekStart(now);
    }
    return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
  }

  private async compute(
    period: RankingPeriod,
    category: RankingCategory,
    limit: number,
  ): Promise<RankingBoardItem[]> {
    const since = this.since(period);

    if (category === RankingCategory.RICH) {
      const rows = await this.giftSendsRepo
        .createQueryBuilder('g')
        .select('g.senderId', 'targetId')
        .addSelect('SUM(g.totalCoins)', 'score')
        .where('g.createdAt >= :since', { since })
        .groupBy('g.senderId')
        .orderBy('score', 'DESC')
        .limit(limit)
        .getRawMany();
      return this.withUserNames(rows);
    }

    if (category === RankingCategory.POPULAR) {
      // Popularity = support received (diamonds from gifts), not follow count.
      const rows = await this.giftSendsRepo
        .createQueryBuilder('g')
        .select('g.receiverId', 'targetId')
        .addSelect('SUM(g.diamondsAwarded)', 'score')
        .where('g.createdAt >= :since', { since })
        .groupBy('g.receiverId')
        .orderBy('score', 'DESC')
        .limit(limit)
        .getRawMany();
      return this.withUserNames(rows);
    }

    if (category === RankingCategory.GIFTS) {
      const rows = await this.giftSendsRepo
        .createQueryBuilder('g')
        .select('g.receiverId', 'targetId')
        .addSelect('SUM(g.diamondsAwarded)', 'score')
        .where('g.createdAt >= :since', { since })
        .groupBy('g.receiverId')
        .orderBy('score', 'DESC')
        .limit(limit)
        .getRawMany();
      return this.withUserNames(rows);
    }

    if (category === RankingCategory.HOST) {
      const rows = await this.giftSendsRepo
        .createQueryBuilder('g')
        .select('g.receiverId', 'targetId')
        .addSelect('SUM(g.totalCoins)', 'score')
        .where('g.createdAt >= :since', { since })
        .andWhere('g.roomId IS NOT NULL')
        .groupBy('g.receiverId')
        .orderBy('score', 'DESC')
        .limit(limit)
        .getRawMany();
      return this.withUserNames(rows);
    }

    if (category === RankingCategory.AGENCY) {
      const rows = await this.agenciesRepo.find({
        order: { totalDiamonds: 'DESC' },
        take: limit,
      });
      return rows.map((a) => ({
        targetId: a.id,
        targetName: a.name,
        score: Number(a.totalDiamonds),
      }));
    }

    if (category === RankingCategory.ROOM) {
      const rows = await this.roomsRepo.find({
        order: { viewerCount: 'DESC' },
        take: limit,
      });
      return rows.map((r) => ({
        targetId: r.id,
        targetName: r.title,
        score: r.viewerCount,
      }));
    }

    return [];
  }

  private async withUserNames(
    rows: Array<{
      targetId: string;
      targetName?: string | null;
      score: string | number;
      rank?: number;
      meta?: Record<string, unknown> | null;
    }>,
  ): Promise<RankingBoardItem[]> {
    const ids = rows.map((r) => r.targetId).filter(Boolean);
    if (!ids.length) return [];
    const users = await this.usersRepo.find({
      where: { id: In(ids) },
      relations: { profile: true, vips: { vipPlan: true } },
    });
    // A user may keep earning and spending normally while using the anonymous identity,
    // but must not appear as a ranking entry while that mode is enabled.
    const map = new Map(users.filter((u) => !u.mysteriousMode).map((u) => [u.id, u]));
    return rows.filter((r) => map.has(r.targetId)).map((r) => {
      const user = map.get(r.targetId)!;
      const activeVip = (user.vips || [])
        .filter((vip) => vip.isActive && (!vip.expiresAt || new Date(vip.expiresAt).getTime() > Date.now()))
        .sort((a, b) => Number(b.level || 0) - Number(a.level || 0))[0];
      const benefits = activeVip?.vipPlan?.benefits as any;
      const nameStyle = benefits?.visuals?.nameStyle && typeof benefits.visuals.nameStyle === 'object'
        ? benefits.visuals.nameStyle as Record<string, unknown>
        : null;
      return {
        ...r,
        targetId: r.targetId,
        targetName: user.displayName || user.username || r.targetName || null,
        score: Number(r.score),
        user: {
          id: user.id,
          username: user.username,
          displayName: user.displayName,
          avatarUrl: user.avatarUrl,
          level: user.level,
          hostBadgeUrl: user.profile?.hostBadgeUrl || null,
          vipBadgeUrl: user.profile?.vipBadgeUrl || null,
          frameUrl: user.profile?.hostBadgeUrl || user.profile?.vipBadgeUrl || null,
          followersCount: user.profile?.followersCount || 0,
          totalReceivedDiamonds: Number(user.profile?.totalReceivedDiamonds) || 0,
          totalSentCoins: Number(user.profile?.totalSentCoins) || 0,
          nameStyle,
        },
      };
    });
  }

  private isUserCategory(category: RankingCategory): boolean {
    return (
      category === RankingCategory.RICH ||
      category === RankingCategory.POPULAR ||
      category === RankingCategory.GIFTS ||
      category === RankingCategory.HOST
    );
  }
}
