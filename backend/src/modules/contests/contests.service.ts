import {
  BadRequestException,
  Injectable,
  NotFoundException,
  Optional,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';
import { Contest } from '../../database/entities/contest.entity';
import { ContestEntry } from '../../database/entities/contest-entry.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
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
import { cacheRoomPkEnabled, readCachedRoomPkEnabled } from './room-pk-feature';

const SAFE_RICH_PRIZE = 5000;
const SAFE_GIFTS_PRIZE = 8000;
const CONTEST_BADGE_CODE = 'contest_weekly';
const CONTEST_BADGE_DAYS = 7;
const FAT_PRIZE_CEILING = 10000;

@Injectable()
export class ContestsService implements OnModuleInit {
  constructor(
    @InjectRepository(Contest) private readonly contestsRepo: Repository<Contest>,
    @InjectRepository(ContestEntry) private readonly entriesRepo: Repository<ContestEntry>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    private readonly dataSource: DataSource,
    private readonly cosmetics: CosmeticsService,
    @Optional() private readonly realtime?: RealtimeGateway,
  ) {}

  /** Dashboard-controlled kill switch. Cached briefly to avoid a settings DB read per gift. */
  private async isRoomPkEnabled(forceRefresh = false): Promise<boolean> {
    const now = Date.now();
    const cached = forceRefresh ? null : readCachedRoomPkEnabled(now);
    if (cached !== null) return cached;
    try {
      const setting = await this.settingsRepo.findOne({ where: { key: 'features.room_pk_enabled' } });
      const raw = String(setting?.value ?? 'false').trim().toLowerCase();
      const value = ['1', 'true', 'yes', 'on'].includes(raw);
      cacheRoomPkEnabled(value, now);
      return value;
    } catch {
      // Fail closed during DB/config errors: PK must not add load during an incident.
      cacheRoomPkEnabled(false, now);
      return false;
    }
  }

  private isTeamChallenge(contest: Pick<Contest, 'challengeType'>): boolean {
    return contest.challengeType === 'room_vs_room' || contest.challengeType === 'agency_vs_agency';
  }

  private validateRoomChallengeSides(
    challengeType: string | null | undefined,
    sideA: Record<string, unknown> | null | undefined,
    sideB: Record<string, unknown> | null | undefined,
  ) {
    if (challengeType !== 'room_vs_room') return;
    const rawA = sideA?.roomIds;
    const rawB = sideB?.roomIds;
    const idsA = Array.isArray(rawA) ? rawA.map(String).map((id) => id.trim()).filter(Boolean) : [];
    const idsB = Array.isArray(rawB) ? rawB.map(String).map((id) => id.trim()).filter(Boolean) : [];
    if (idsA.length !== 1 || idsB.length !== 1) {
      throw new BadRequestException('تحدي PK يتطلب تحديد معرّف روم واحد لكل طرف داخل roomIds');
    }
    if (idsA[0] === idsB[0]) {
      throw new BadRequestException('لا يمكن أن يتنافس الروم نفسه ضد نفسه');
    }
  }

  private emitTeamChallengeEnded(contest: Contest) {
    if (!this.realtime || contest.challengeType !== 'room_vs_room') return;
    const sideA = (contest.sideA || {}) as Record<string, unknown>;
    const sideB = (contest.sideB || {}) as Record<string, unknown>;
    const scoreA = Math.max(0, Number(sideA.score || 0));
    const scoreB = Math.max(0, Number(sideB.score || 0));
    const winner = scoreA === scoreB ? 'draw' : scoreA > scoreB ? 'sideA' : 'sideB';
    const roomIds = [...new Set([
      ...(Array.isArray(sideA.roomIds) ? sideA.roomIds.map(String) : []),
      ...(Array.isArray(sideB.roomIds) ? sideB.roomIds.map(String) : []),
    ].filter(Boolean))];
    for (const roomId of roomIds) {
      this.realtime.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'contest:team-ended',
        payload: { contestId: contest.id, title: contest.title, sideA: contest.sideA, sideB: contest.sideB, winner, scoreA, scoreB },
        at: new Date().toISOString(),
      });
    }
  }

  async onModuleInit() {
    // Additive migration only: retain existing contests and all user data.
    await this.dataSource.query(`
      ALTER TABLE "jeho_own"."contests"
        ADD COLUMN IF NOT EXISTS "kind" varchar(24) NOT NULL DEFAULT 'challenge',
        ADD COLUMN IF NOT EXISTS "bannerUrl" varchar(1024),
        ADD COLUMN IF NOT EXISTS "goalDiamonds" integer NOT NULL DEFAULT 0,
        ADD COLUMN IF NOT EXISTS "scoringMode" varchar(24) NOT NULL DEFAULT 'coins',
        ADD COLUMN IF NOT EXISTS "challengeType" varchar(24) NOT NULL DEFAULT 'individual',
        ADD COLUMN IF NOT EXISTS "allowedGiftIds" jsonb,
        ADD COLUMN IF NOT EXISTS "milestones" jsonb,
        ADD COLUMN IF NOT EXISTS "prizes" jsonb,
        ADD COLUMN IF NOT EXISTS "sideA" jsonb,
        ADD COLUMN IF NOT EXISTS "sideB" jsonb,
        ADD COLUMN IF NOT EXISTS "rules" jsonb,
        ADD COLUMN IF NOT EXISTS "isInteractive" boolean NOT NULL DEFAULT false
    `);
  }

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
    let items = await qb.getMany();
    // Hide team-vs-team challenges from every client while the dashboard kill switch is off.
    if (!(await this.isRoomPkEnabled(true))) items = items.filter((c) => !this.isTeamChallenge(c));
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
    if (this.isTeamChallenge(contest) && !(await this.isRoomPkEnabled(true))) {
      throw new NotFoundException('تحديات PK متوقفة حالياً');
    }
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
      if (this.isTeamChallenge(contest) && !(await this.isRoomPkEnabled(true))) {
        throw new BadRequestException('تحديات PK متوقفة حالياً من الإدارة');
      }
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
    context: { roomId?: string; agencyId?: string; occurredAt?: Date | string; giftId?: string; quantity?: number; diamondsAwarded?: number } = {},
  ) {
    const amount = Math.max(0, Math.floor(Number(coinsSpent) || 0));
    if (amount <= 0) return;
    // Use the persisted server timestamp of the gift send, not the later async
    // scoring time. This prevents delayed processing from moving a gift across
    // the event's start/end boundary.
    const occurredAt = context.occurredAt ? new Date(context.occurredAt) : new Date();
    if (Number.isNaN(occurredAt.getTime())) return;
    const active = await this.contestsRepo
      .createQueryBuilder('contest')
      .where('contest.startAt <= :occurredAt', { occurredAt })
      .andWhere('contest.endAt >= :occurredAt', { occurredAt })
      .getMany();
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
      const allowed = Array.isArray(c.allowedGiftIds) ? c.allowedGiftIds.map(String) : [];
      if (allowed.length && (!context.giftId || !allowed.includes(String(context.giftId)))) continue;
      const scoreDelta = c.scoringMode === 'gifts'
        ? Math.max(0, Math.floor(Number(context.quantity || 1)))
        : c.scoringMode === 'diamonds' || c.scoringMode === 'points'
          ? Math.max(0, Math.floor(Number(context.diamondsAwarded || 0)))
          : amount;
      if (scoreDelta <= 0) continue;
      // Team-vs-team challenges keep side totals on the configured challenge
      // record; no user-provided room/agency IDs are trusted without matching
      // the dashboard's explicit side membership lists.
      if (this.isTeamChallenge(c)) {
        if (!(await this.isRoomPkEnabled())) continue;
        const isRoomBattle = c.challengeType === 'room_vs_room';
        const key = isRoomBattle ? 'roomIds' : 'agencyIds';
        const candidate = isRoomBattle ? context.roomId : context.agencyId;
        if (!candidate) continue;
        const sideAIds = Array.isArray((c.sideA as any)?.[key]) ? (c.sideA as any)[key].map(String) : [];
        const sideBIds = Array.isArray((c.sideB as any)?.[key]) ? (c.sideB as any)[key].map(String) : [];
        const sideKey = sideAIds.includes(String(candidate)) ? 'sideA' : sideBIds.includes(String(candidate)) ? 'sideB' : null;
        if (!sideKey) continue;

        // Serialize concurrent gifts for the same contest so simultaneous sends cannot overwrite scores.
        const updated = await this.dataSource.transaction(async (manager) => {
          const locked = await manager.findOne(Contest, {
            where: { id: c.id },
            lock: { mode: 'pessimistic_write' },
          });
          if (!locked) return null;
          const side = { ...(((locked as any)[sideKey] as Record<string, unknown> | null) || {}) };
          side.score = Math.max(0, Number(side.score || 0)) + scoreDelta;
          (locked as any)[sideKey] = side;
          return manager.save(locked);
        });
        if (!updated) continue;

        const payload = {
          contestId: updated.id,
          challengeType: updated.challengeType,
          sideA: updated.sideA,
          sideB: updated.sideB,
          delta: scoreDelta,
          roomId: context.roomId || null,
          agencyId: context.agencyId || null,
          at: occurredAt.toISOString(),
        };
        // A room-vs-room score must fan out to both competing rooms, not only the sender's room.
        const targetRoomIds = isRoomBattle
          ? [...new Set([...sideAIds, ...sideBIds])]
          : (context.roomId ? [context.roomId] : []);
        for (const targetRoomId of targetRoomIds) {
          this.realtime?.emitToRoom(targetRoomId, 'room:event', {
            roomId: targetRoomId,
            event: 'contest:team-score',
            payload: { ...payload, roomId: targetRoomId },
            at: payload.at,
          });
        }
        continue;
      }
      const entry = await this.entriesRepo.findOne({
        where: { contestId: c.id, userId },
      });
      if (!entry) continue;
      await this.entriesRepo
        .createQueryBuilder()
        .update()
        .set({
          score: () => `CAST(CAST(COALESCE(score, '0') AS BIGINT) + ${scoreDelta} AS VARCHAR)`,
        })
        .where('id = :id', { id: entry.id })
        .execute();
      const payload = {
        contestId: c.id,
        userId,
        delta: scoreDelta,
        roomId: c.roomId,
        agencyId: c.agencyId,
        at: occurredAt.toISOString(),
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
    kind?: 'celebration' | 'event' | 'challenge';
    bannerUrl?: string;
    goalDiamonds?: number;
    scoringMode?: 'coins' | 'diamonds' | 'gifts' | 'points';
    challengeType?: 'individual' | 'host' | 'room_vs_room' | 'agency_vs_agency';
    allowedGiftIds?: string[];
    milestones?: Record<string, unknown>[];
    prizes?: Record<string, unknown>[];
    sideA?: Record<string, unknown> | null;
    sideB?: Record<string, unknown> | null;
    rules?: Record<string, unknown> | null;
    isInteractive?: boolean;
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
    this.validateRoomChallengeSides(dto.challengeType || 'individual', dto.sideA, dto.sideB);
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
        kind: ['celebration', 'event', 'challenge'].includes(String(dto.kind)) ? String(dto.kind) : 'challenge',
        bannerUrl: dto.bannerUrl?.trim() || null,
        goalDiamonds: Math.max(0, Math.floor(Number(dto.goalDiamonds || 0))),
        scoringMode: ['coins', 'diamonds', 'gifts', 'points'].includes(String(dto.scoringMode)) ? String(dto.scoringMode) : 'coins',
        challengeType: ['individual', 'host', 'room_vs_room', 'agency_vs_agency'].includes(String(dto.challengeType)) ? String(dto.challengeType) : 'individual',
        allowedGiftIds: Array.isArray(dto.allowedGiftIds) ? dto.allowedGiftIds.map(String).filter(Boolean) : null,
        milestones: Array.isArray(dto.milestones) ? dto.milestones : null,
        prizes: Array.isArray(dto.prizes) ? dto.prizes : null,
        sideA: dto.sideA && typeof dto.sideA === 'object' ? dto.sideA : null,
        sideB: dto.sideB && typeof dto.sideB === 'object' ? dto.sideB : null,
        rules: dto.rules && typeof dto.rules === 'object' ? dto.rules : null,
        isInteractive: dto.isInteractive === true,
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
      kind: 'celebration' | 'event' | 'challenge';
      bannerUrl: string;
      goalDiamonds: number;
      scoringMode: 'coins' | 'diamonds' | 'gifts' | 'points';
      challengeType: 'individual' | 'host' | 'room_vs_room' | 'agency_vs_agency';
      allowedGiftIds: string[];
      milestones: Record<string, unknown>[];
      prizes: Record<string, unknown>[];
      sideA: Record<string, unknown> | null;
      sideB: Record<string, unknown> | null;
      rules: Record<string, unknown> | null;
      isInteractive: boolean;
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
    if (dto.kind !== undefined) {
      if (!['celebration', 'event', 'challenge'].includes(dto.kind)) throw new BadRequestException('نوع الفعالية غير صالح');
      contest.kind = dto.kind;
    }
    if (dto.bannerUrl !== undefined) contest.bannerUrl = dto.bannerUrl?.trim() || null;
    if (dto.goalDiamonds !== undefined) contest.goalDiamonds = Math.max(0, Math.floor(Number(dto.goalDiamonds) || 0));
    if (dto.scoringMode !== undefined) {
      if (!['coins', 'diamonds', 'gifts', 'points'].includes(dto.scoringMode)) throw new BadRequestException('طريقة الاحتساب غير صالحة');
      contest.scoringMode = dto.scoringMode;
    }
    if (dto.challengeType !== undefined) {
      if (!['individual', 'host', 'room_vs_room', 'agency_vs_agency'].includes(dto.challengeType)) throw new BadRequestException('نوع التحدي غير صالح');
      contest.challengeType = dto.challengeType;
    }
    if (dto.allowedGiftIds !== undefined) contest.allowedGiftIds = Array.isArray(dto.allowedGiftIds) ? dto.allowedGiftIds.map(String).filter(Boolean) : null;
    if (dto.milestones !== undefined) contest.milestones = Array.isArray(dto.milestones) ? dto.milestones : null;
    if (dto.prizes !== undefined) contest.prizes = Array.isArray(dto.prizes) ? dto.prizes : null;
    if (dto.sideA !== undefined) contest.sideA = dto.sideA && typeof dto.sideA === 'object' ? dto.sideA : null;
    if (dto.sideB !== undefined) contest.sideB = dto.sideB && typeof dto.sideB === 'object' ? dto.sideB : null;
    if (dto.rules !== undefined) contest.rules = dto.rules && typeof dto.rules === 'object' ? dto.rules : null;
    if (dto.isInteractive !== undefined) contest.isInteractive = dto.isInteractive === true;
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
    this.validateRoomChallengeSides(contest.challengeType, contest.sideA, contest.sideB);
    if (dto.status) {
      const prev = contest.status;
      contest.status = dto.status;
      await this.contestsRepo.save(contest);
      if (prev !== 'ended' && dto.status === 'ended') {
        if (this.isTeamChallenge(contest)) {
          if (await this.isRoomPkEnabled(true)) this.emitTeamChallengeEnded(contest);
        } else {
          await this.settlePrizes(contest);
        }
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
    if (this.isTeamChallenge(contest)) {
      if (await this.isRoomPkEnabled(true)) this.emitTeamChallengeEnded(contest);
    } else {
      await this.settlePrizes(contest);
    }
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
      if (this.isTeamChallenge(contest)) {
        if (await this.isRoomPkEnabled(true)) this.emitTeamChallengeEnded(contest);
      } else {
        await this.settlePrizes(contest);
      }
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
    // Team challenge rewards are not individual contest-entry prizes.
    if (this.isTeamChallenge(contest)) return;
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

      const configuredPrizes = Array.isArray(contest.prizes) ? contest.prizes : [];
      if (configuredPrizes.length) {
        const maxRank = Math.min(100, Math.max(1, ...configuredPrizes.map((p) =>
          Math.max(1, Math.floor(Number(p?.rank ?? p?.place ?? 1) || 1)),
        )));
        const ranked = await manager.find(ContestEntry, {
          where: { contestId: contest.id },
          order: { score: 'DESC', joinedAt: 'ASC' },
          take: maxRank,
        });
        if (!ranked.length) return;
        winnerId = ranked[0].userId;
        for (let index = 0; index < configuredPrizes.length; index++) {
          const prize = configuredPrizes[index] || {};
          const rank = Math.max(1, Math.floor(Number(prize.rank ?? prize.place ?? 1) || 1));
          const entrant = ranked[rank - 1];
          if (!entrant) continue;
          const minimumScore = Math.max(0, Number(prize.minimumScore || 0));
          if (Number(entrant.score || 0) < minimumScore) continue;
          const coins = Math.max(0, Math.floor(Number(prize.coins || 0)));
          const diamonds = Math.max(0, Math.floor(Number(prize.diamonds || 0)));
          if (coins <= 0 && diamonds <= 0) continue;
          let prizeWallet = await manager.findOne(Wallet, {
            where: { userId: entrant.userId },
            lock: { mode: 'pessimistic_write' },
          });
          if (!prizeWallet) {
            prizeWallet = await manager.save(manager.create(Wallet, {
              userId: entrant.userId, coins: 0, diamonds: 0,
            }));
            prizeWallet = await manager.findOne(Wallet, {
              where: { userId: entrant.userId },
              lock: { mode: 'pessimistic_write' },
            });
          }
          if (!prizeWallet) continue;
          if (coins > 0) prizeWallet.coins = Number(prizeWallet.coins || 0) + coins;
          if (diamonds > 0) prizeWallet.diamonds = Number(prizeWallet.diamonds || 0) + diamonds;
          await manager.save(prizeWallet);
          const label = String(prize.label || prize.title || ('المركز ' + rank));
          if (coins > 0) await manager.save(manager.create(WalletTransaction, {
            userId: entrant.userId, type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS, amount: coins,
            balanceAfter: Number(prizeWallet.coins || 0),
            referenceType: 'contest_prize_stage_coins',
            referenceId: contest.id + ':' + rank + ':' + index,
            description: 'جائزة ' + label + ' — ' + contest.title,
            metadata: { contestId: contest.id, rank, score: entrant.score, label },
          }));
          if (diamonds > 0) await manager.save(manager.create(WalletTransaction, {
            userId: entrant.userId, type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.DIAMONDS, amount: diamonds,
            balanceAfter: Number(prizeWallet.diamonds || 0),
            referenceType: 'contest_prize_stage_diamonds',
            referenceId: contest.id + ':' + rank + ':' + index,
            description: 'جائزة ' + label + ' — ' + contest.title,
            metadata: { contestId: contest.id, rank, score: entrant.score, label },
          }));
        }
        const markerWallet = await manager.findOne(Wallet, { where: { userId: ranked[0].userId } });
        if (markerWallet) await manager.save(manager.create(WalletTransaction, {
          userId: ranked[0].userId, type: TransactionType.LUCKY_REWARD,
          currency: CurrencyType.COINS, amount: 0,
          balanceAfter: Number(markerWallet.coins || 0),
          referenceType: 'contest_prize', referenceId: contest.id,
          description: 'تم تسوية جوائز: ' + contest.title,
          metadata: { contestId: contest.id, configuredPrizes: configuredPrizes.length },
        }));
        return;
      }

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
          if (this.isTeamChallenge(c)) {
            if (await this.isRoomPkEnabled(true)) this.emitTeamChallengeEnded(c);
          } else {
            await this.settlePrizes(c);
          }
        }
      }
    }
  }
}
