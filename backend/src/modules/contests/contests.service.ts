import {
  BadRequestException,
  Injectable,
  NotFoundException,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';
import { Contest } from '../../database/entities/contest.entity';
import { ContestEntry } from '../../database/entities/contest-entry.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { User } from '../../database/entities/user.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';

const SAFE_RICH_PRIZE = 5000;
const SAFE_GIFTS_PRIZE = 8000;
const CONTEST_BADGE_CODE = 'contest_weekly';
const CONTEST_BADGE_DAYS = 7;
const FAT_PRIZE_CEILING = 10000;

@Injectable()
export class ContestsService {
  constructor(
    @InjectRepository(Contest) private readonly contestsRepo: Repository<Contest>,
    @InjectRepository(ContestEntry) private readonly entriesRepo: Repository<ContestEntry>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    private readonly dataSource: DataSource,
    private readonly cosmetics: CosmeticsService,
    @Optional() private readonly realtime?: RealtimeGateway,
  ) {}

  async ensureDefaults() {
    // Never clamp live contest prizes after dashboard edits.
    if (!bootCatalogSeedEnabled()) return;
    await this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        'contests:rolling-defaults',
      ]);
      const now = new Date();
      const repo = manager.getRepository(Contest);

      // Clamp overly generous live contests left from older defaults.
      const fat = await repo
        .createQueryBuilder('contest')
        .where('contest.status IN (:...statuses)', {
          statuses: ['active', 'upcoming'],
        })
        .andWhere('contest.endAt > :now', { now })
        .andWhere('contest.prizeCoins > :ceil', { ceil: FAT_PRIZE_CEILING })
        .getMany();
      for (const c of fat) {
        const gifts = String(c.category || '') === 'gifts';
        c.prizeCoins = gifts ? SAFE_GIFTS_PRIZE : SAFE_RICH_PRIZE;
        c.prizeLabel = gifts
          ? `${SAFE_GIFTS_PRIZE.toLocaleString('en-US')} كوينز + شارة ${CONTEST_BADGE_DAYS} أيام`
          : `${SAFE_RICH_PRIZE.toLocaleString('en-US')} كوينز + شارة ${CONTEST_BADGE_DAYS} أيام`;
        await repo.save(c);
      }

      // Do NOT auto-recreate contests here — that made admin deletes look broken
      // (list/adminList kept spawning weekly defaults after every delete).
    });
  }

  async list(userId?: string, roomId?: string, agencyId?: string) {
    await this.ensureDefaults();
    await this.refreshStatuses();
    const qb = this.contestsRepo.createQueryBuilder('c').orderBy('c.status', 'ASC').addOrderBy('c.endAt', 'ASC');
    if (roomId && agencyId) {
      qb.andWhere(
        '(c.scope = :global OR (c.scope = :room AND c.roomId = :roomId) OR (c.scope = :agency AND c.agencyId = :agencyId))',
        { global: 'global', room: 'room', agency: 'agency', roomId, agencyId },
      );
    } else if (roomId) {
      qb.andWhere('(c.scope = :global OR (c.scope = :room AND c.roomId = :roomId))', {
        global: 'global',
        room: 'room',
        roomId,
      });
    } else if (agencyId) {
      qb.andWhere('(c.scope = :global OR (c.scope = :agency AND c.agencyId = :agencyId))', {
        global: 'global',
        agency: 'agency',
        agencyId,
      });
    } else {
      qb.andWhere('(c.scope IS NULL OR c.scope = :global OR c.scope = :empty)', {
        global: 'global',
        empty: '',
      });
    }
    const items = await qb.getMany();
    let joined = new Set<string>();
    if (userId) {
      const mine = await this.entriesRepo.find({ where: { userId } });
      joined = new Set(mine.map((e) => e.contestId));
    }
    return {
      items: items.map((c) => ({
        ...c,
        joined: joined.has(c.id),
      })),
    };
  }

  async get(id: string, userId?: string) {
    const contest = await this.contestsRepo.findOne({ where: { id } });
    if (!contest) throw new NotFoundException('المسابقة غير موجودة');
    const entries = await this.entriesRepo.find({
      where: { contestId: id },
      order: { score: 'DESC', joinedAt: 'ASC' },
      take: 50,
    });
    const userIds = entries.map((e) => e.userId);
    const users = userIds.length
      ? await this.usersRepo.find({
          where: { id: In(userIds) },
          relations: ['profile'],
        })
      : [];
    const byId = new Map(users.map((u) => [u.id, u]));
    let joined = false;
    if (userId) {
      joined = !!(await this.entriesRepo.findOne({ where: { contestId: id, userId } }));
    }
    return {
      contest: { ...contest, joined },
      leaderboard: entries.map((e, i) => {
        const u = byId.get(e.userId);
        return {
          rank: i + 1,
          userId: e.userId,
          score: Number(e.score || 0),
          displayName: u?.displayName || u?.username || 'مستخدم',
          avatarUrl: u?.avatarUrl || null,
          hostBadgeUrl: u?.profile?.hostBadgeUrl || null,
        };
      }),
    };
  }

  async join(userId: string, contestId: string) {
    await this.refreshStatuses();
    return this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `contest-entry:${contestId}:${userId}`,
      ]);
      const contest = await manager.findOne(Contest, {
        where: { id: contestId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!contest) throw new NotFoundException('المسابقة غير موجودة');
      if (contest.status !== 'active') {
        throw new BadRequestException('المسابقة غير مفتوحة للانضمام');
      }
      const existing = await manager.findOne(ContestEntry, {
        where: { contestId, userId },
      });
      if (existing) throw new BadRequestException('منضم مسبقاً');

      if (contest.entryFeeCoins > 0) {
        const wallet = await manager.findOne(Wallet, {
          where: { userId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!wallet || Number(wallet.coins) < contest.entryFeeCoins) {
          throw new BadRequestException('رصيد كوينز غير كافٍ لرسوم الدخول');
        }
        wallet.coins = Number(wallet.coins) - contest.entryFeeCoins;
        await manager.save(wallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.EXCHANGE,
            currency: CurrencyType.COINS,
            amount: -contest.entryFeeCoins,
            balanceAfter: Number(wallet.coins),
            referenceType: 'contest_entry',
            referenceId: contest.id,
            description: `Contest entry fee: ${contest.title}`,
          }),
        );
      }

      let score = 0;
      const category = String(contest.category || '').toLowerCase();
      if (!['gifts', 'rich', 'gift', 'popular'].includes(category)) {
        const user = await manager.findOne(User, { where: { id: userId } });
        score = user ? Number(user.experience || 0) : 0;
      }
      await manager.save(
        manager.create(ContestEntry, {
          contestId,
          userId,
          score: String(score),
        }),
      );
      contest.entrantsCount = Number(contest.entrantsCount || 0) + 1;
      await manager.save(contest);
      return { ok: true, contestId };
    });
  }

  /** Add score for joined active contests (gift spend / popular receive). */
  async addScore(
    userId: string,
    coinsSpent: number,
    kind: 'gifts' | 'rich' | 'popular' = 'gifts',
    context: { roomId?: string; agencyId?: string } = {},
  ) {
    const amount = Math.max(0, Math.floor(Number(coinsSpent) || 0));
    if (amount <= 0) return;
    await this.refreshStatuses();
    const active = await this.contestsRepo.find({ where: { status: 'active' } });
    for (const c of active) {
      if (c.scope === 'room' && (!context.roomId || c.roomId !== context.roomId)) {
        continue;
      }
      if (
        c.scope === 'agency' &&
        (!context.agencyId || c.agencyId !== context.agencyId)
      ) {
        continue;
      }
      const cat = String(c.category || '').toLowerCase();
      const match =
        kind === 'popular'
          ? cat === 'popular'
          : kind === 'rich'
            ? cat === 'rich'
            : cat === 'gifts' || cat === 'gift' || cat === 'rich';
      if (!match) continue;
      const entry = await this.entriesRepo.findOne({
        where: { contestId: c.id, userId },
      });
      if (!entry) continue;
      await this.entriesRepo
        .createQueryBuilder()
        .update()
        .set({
          score: () => `CAST(CAST(COALESCE(score, '0') AS BIGINT) + ${amount} AS VARCHAR)`,
        })
        .where('id = :id', { id: entry.id })
        .execute();
      const payload = {
        contestId: c.id,
        userId,
        delta: amount,
        roomId: c.roomId,
        agencyId: c.agencyId,
        at: new Date().toISOString(),
      };
      this.realtime?.emitToUser(userId, 'contest:score', payload);
      if (c.scope === 'room' && c.roomId) {
        this.realtime?.emitToRoom(c.roomId, 'room:event', {
          roomId: c.roomId,
          event: 'contest:score',
          payload,
          at: payload.at,
        });
      }
    }
  }

  async adminList() {
    await this.ensureDefaults();
    await this.refreshStatuses();
    const items = await this.contestsRepo.find({
      order: { status: 'ASC', endAt: 'ASC' },
    });
    return { items, total: items.length };
  }

  async adminCreate(dto: {
    title: string;
    description?: string;
    category?: string;
    entryFeeCoins?: number;
    prizeCoins?: number;
    prizeLabel?: string;
    startAt?: string;
    endAt?: string;
    days?: number;
    /** Duration in hours (preferred for 24h contests). Overrides days when set. */
    hours?: number;
    scope?: 'global' | 'room' | 'agency';
    roomId?: string;
    agencyId?: string;
  }) {
    const now = new Date();
    const hoursRaw = dto.hours != null ? Number(dto.hours) : NaN;
    const useHours = Number.isFinite(hoursRaw) && hoursRaw > 0;
    const days = Math.max(1, Number(dto.days || 7));
    const startAt = dto.startAt ? new Date(dto.startAt) : now;
    const endAt = dto.endAt
      ? new Date(dto.endAt)
      : useHours
        ? new Date(startAt.getTime() + Math.max(1, Math.floor(hoursRaw)) * 60 * 60 * 1000)
        : new Date(startAt.getTime() + days * 24 * 60 * 60 * 1000);
    if (!(startAt instanceof Date) || Number.isNaN(startAt.getTime())) {
      throw new BadRequestException('تاريخ البداية غير صالح');
    }
    if (!(endAt instanceof Date) || Number.isNaN(endAt.getTime()) || endAt <= startAt) {
      throw new BadRequestException('تاريخ النهاية غير صالح');
    }
    const title = String(dto.title || '').trim();
    if (!title) throw new BadRequestException('عنوان المسابقة مطلوب');
    const scope = dto.scope || 'global';
    if (scope === 'room' && !dto.roomId) {
      throw new BadRequestException('roomId مطلوب لمسابقات الروم');
    }
    if (scope === 'agency' && !dto.agencyId) {
      throw new BadRequestException('agencyId مطلوب لمسابقات الوكالة');
    }
    const row = await this.contestsRepo.save(
      this.contestsRepo.create({
        title,
        description: dto.description?.trim() || null,
        category: dto.category || 'rich',
        status: now < startAt ? 'upcoming' : now > endAt ? 'ended' : 'active',
        entryFeeCoins: Math.max(0, Number(dto.entryFeeCoins || 0)),
        prizeCoins: Math.min(
          FAT_PRIZE_CEILING,
          Math.max(0, Number(dto.prizeCoins || 0)),
        ),
        prizeLabel: dto.prizeLabel?.trim() || null,
        startAt,
        endAt,
        entrantsCount: 0,
        scope,
        roomId: scope === 'room' ? dto.roomId : null,
        agencyId: scope === 'agency' ? dto.agencyId : null,
      }),
    );
    return row;
  }

  async adminUpdate(
    id: string,
    dto: Partial<{
      title: string;
      description: string;
      category: string;
      entryFeeCoins: number;
      prizeCoins: number;
      prizeLabel: string;
      startAt: string;
      endAt: string;
      status: 'upcoming' | 'active' | 'ended';
      scope: 'global' | 'room' | 'agency';
      roomId: string;
      agencyId: string;
    }>,
  ) {
    const contest = await this.contestsRepo.findOne({ where: { id } });
    if (!contest) throw new NotFoundException('المسابقة غير موجودة');
    if (dto.title != null) contest.title = String(dto.title).trim();
    if (dto.description !== undefined) contest.description = dto.description?.trim() || null;
    if (dto.category != null) contest.category = dto.category;
    if (dto.entryFeeCoins != null) contest.entryFeeCoins = Math.max(0, Number(dto.entryFeeCoins));
    if (dto.prizeCoins != null) {
      contest.prizeCoins = Math.min(
        FAT_PRIZE_CEILING,
        Math.max(0, Number(dto.prizeCoins)),
      );
    }
    if (dto.prizeLabel !== undefined) contest.prizeLabel = dto.prizeLabel?.trim() || null;
    if (dto.startAt) contest.startAt = new Date(dto.startAt);
    if (dto.endAt) contest.endAt = new Date(dto.endAt);
    if (dto.scope !== undefined) contest.scope = dto.scope;
    if (dto.roomId !== undefined) contest.roomId = dto.roomId || null;
    if (dto.agencyId !== undefined) contest.agencyId = dto.agencyId || null;
    if (contest.scope === 'room' && !contest.roomId) {
      throw new BadRequestException('roomId مطلوب لمسابقات الروم');
    }
    if (contest.scope === 'agency' && !contest.agencyId) {
      throw new BadRequestException('agencyId مطلوب لمسابقات الوكالة');
    }
    if (contest.scope === 'global') {
      contest.roomId = null;
      contest.agencyId = null;
    }
    if (dto.status) {
      const prev = contest.status;
      contest.status = dto.status;
      await this.contestsRepo.save(contest);
      if (prev !== 'ended' && dto.status === 'ended') {
        await this.settlePrizes(contest);
      }
      return contest;
    }
    return this.contestsRepo.save(contest);
  }

  async adminEnd(id: string) {
    const contest = await this.contestsRepo.findOne({ where: { id } });
    if (!contest) throw new NotFoundException('المسابقة غير موجودة');
    if (contest.status === 'ended') return contest;
    contest.status = 'ended';
    await this.contestsRepo.save(contest);
    await this.settlePrizes(contest);
    return contest;
  }

  /** End every non-ended contest (settle prizes where applicable). */
  async adminEndAll() {
    await this.refreshStatuses();
    const open = await this.contestsRepo.find({
      where: [{ status: 'active' }, { status: 'upcoming' }],
    });
    const ended: Contest[] = [];
    for (const contest of open) {
      contest.status = 'ended';
      await this.contestsRepo.save(contest);
      await this.settlePrizes(contest);
      ended.push(contest);
    }
    return { ended: ended.length, items: ended };
  }

  /** Hard-delete contest + its entries. Prefer ending first if prizes matter. */
  async adminDelete(id: string) {
    const contest = await this.contestsRepo.findOne({ where: { id } });
    if (!contest) throw new NotFoundException('المسابقة غير موجودة');
    await this.dataSource.transaction(async (manager) => {
      await manager.delete(ContestEntry, { contestId: id });
      await manager.delete(Contest, { id });
    });
    return { deleted: true, id };
  }

  /** Pay top entrant prizeCoins + temporary contest badge (idempotent). */
  private async settlePrizes(contest: Contest) {
    const prize = Math.max(0, Math.floor(Number(contest.prizeCoins) || 0));
    let winnerId: string | null = null;

    await this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `contest-prize:${contest.id}`,
      ]);
      const already = await manager.findOne(WalletTransaction, {
        where: { referenceType: 'contest_prize', referenceId: contest.id },
        lock: { mode: 'pessimistic_write' },
      });
      if (already) return;

      const top = await manager.find(ContestEntry, {
        where: { contestId: contest.id },
        order: { score: 'DESC', joinedAt: 'ASC' },
        take: 1,
      });
      if (!top.length) return;

      winnerId = top[0].userId;
      let wallet = await manager.findOne(Wallet, {
        where: { userId: winnerId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(
          manager.create(Wallet, { userId: winnerId, coins: 0, diamonds: 0 }),
        );
        wallet = await manager.findOne(Wallet, {
          where: { userId: winnerId },
          lock: { mode: 'pessimistic_write' },
        });
      }
      if (!wallet) {
        winnerId = null;
        return;
      }

      if (prize > 0) {
        wallet.coins = Number(wallet.coins || 0) + prize;
        await manager.save(wallet);
      }

      const label = contest.prizeLabel ? ` · ${contest.prizeLabel}` : '';
      await manager.save(
        manager.create(WalletTransaction, {
          userId: winnerId,
          type: TransactionType.LUCKY_REWARD,
          currency: CurrencyType.COINS,
          amount: prize,
          balanceAfter: Number(wallet.coins),
          referenceType: 'contest_prize',
          referenceId: contest.id,
          description: `جائزة مسابقة: ${contest.title}${label}`,
          metadata: {
            contestId: contest.id,
            category: contest.category,
            score: top[0].score,
            prizeLabel: contest.prizeLabel,
            badgeCode: CONTEST_BADGE_CODE,
            badgeDays: CONTEST_BADGE_DAYS,
          },
        }),
      );
    });

    if (!winnerId) return;
    try {
      await this.cosmetics.grantTemporary(winnerId, CONTEST_BADGE_CODE, CONTEST_BADGE_DAYS);
    } catch {
      /* badge grant is best-effort after coin settle */
    }
  }

  private async refreshStatuses() {
    const now = new Date();
    const all = await this.contestsRepo.find();
    for (const c of all) {
      if (c.status === 'ended') continue;
      let next: Contest['status'] = c.status;
      if (now < c.startAt) next = 'upcoming';
      else if (now > c.endAt) next = 'ended';
      else next = 'active';
      if (next !== c.status) {
        const wasActive = c.status === 'active';
        c.status = next;
        await this.contestsRepo.save(c);
        if (wasActive && next === 'ended') {
          await this.settlePrizes(c);
        }
      }
    }
  }
}
