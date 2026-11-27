import { Controller, Get, Post, Body, UseGuards, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Room } from '../../database/entities/room.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { UserCosmetic } from '../../database/entities/user-cosmetic.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Public } from '../../common/decorators';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { AdminGuard } from '../../common/guards/admin.guard';

@ApiTags('Room Cup')
@Controller('room-cup')
export class RoomCupController implements OnModuleInit, OnModuleDestroy {
  private seasonTimer: ReturnType<typeof setInterval> | null = null;
  private readonly logger = new Logger(RoomCupController.name);

  constructor(
    @InjectRepository(GiftSend) private readonly giftsRepo: Repository<GiftSend>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(VipPlan) private readonly vipPlansRepo: Repository<VipPlan>,
    @InjectRepository(UserVip) private readonly userVipRepo: Repository<UserVip>,
    @InjectRepository(Cosmetic) private readonly cosmeticsRepo: Repository<Cosmetic>,
    @InjectRepository(UserCosmetic) private readonly userCosmeticsRepo: Repository<UserCosmetic>,
  ) {}

  async onModuleInit() {
    try {
      await this.roomsRepo.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "cupBadgeSeason" VARCHAR(32)`,
      );
    } catch (err) {
      this.logger.warn(`cupBadgeSeason ensure failed: ${(err as Error).message}`);
    }
    this.seasonTimer = setInterval(() => {
      void this.settleExpiredSeason().catch((err) =>
        this.logger.warn(`room cup automatic settlement failed: ${(err as Error).message}`),
      );
    }, 30_000);
    this.seasonTimer.unref?.();
    void this.settleExpiredSeason().catch(() => undefined);
  }

  onModuleDestroy() {
    if (this.seasonTimer) clearInterval(this.seasonTimer);
    this.seasonTimer = null;
  }

  private async setting(key: string, fallback = '') {
    const row = await this.settingsRepo.findOne({ where: { key } });
    return row?.value ?? fallback;
  }

  private async seasonBounds(period: string) {
    const configuredStartRaw = await this.setting('room_cup.start_at', '');
    const configuredEndRaw = await this.setting('room_cup.end_at', '');
    const configuredStart = new Date(String(configuredStartRaw || ''));
    const configuredEnd = new Date(String(configuredEndRaw || ''));
    if (Number.isFinite(configuredStart.getTime()) && Number.isFinite(configuredEnd.getTime())
        && configuredEnd.getTime() > configuredStart.getTime()) {
      const key = configuredStart.toISOString().slice(0, 10) + '_' + configuredEnd.toISOString().slice(0, 10);
      return { start: configuredStart, end: configuredEnd, key };
    }
    const now = new Date();
    if (period === 'monthly') {
      const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
      const end = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
      const key = now.getUTCFullYear() + '-' + String(now.getUTCMonth() + 1).padStart(2, '0');
      return { start, end, key };
    }
    const day = now.getUTCDay() || 7;
    const start = new Date(Date.UTC(
      now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - (day - 1),
    ));
    const end = new Date(start.getTime() + 7 * 86400000);
    const oneJan = new Date(Date.UTC(now.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((start.getTime() - oneJan.getTime()) / 86400000 + oneJan.getUTCDay() + 1) / 7);
    const key = now.getUTCFullYear() + '-W' + String(week).padStart(2, '0');
    return { start, end, key };
  }

  private async settleExpiredSeason() {
    const enabled = (await this.setting('room_cup.enabled', 'true')) !== 'false';
    if (!enabled) return;
    const period = (await this.setting('room_cup.period', 'weekly')) || 'weekly';
    const bounds = await this.seasonBounds(period);
    if (bounds.end && bounds.end.getTime() <= Date.now()) {
      await this.endSeason();
    }
  }

  @Public()
  @Get('leaderboard')
  @ApiOperation({ summary: 'Current room cup leaderboard' })
  async leaderboard() {
    await this.settleExpiredSeason();
    return this.buildLeaderboard();
  }

  private async buildLeaderboard() {
    const enabled = (await this.setting('room_cup.enabled', 'true')) !== 'false';
    const period = (await this.setting('room_cup.period', 'weekly')) || 'weekly';
    const { start, end, key } = await this.seasonBounds(period);
    if (!enabled) {
      return { enabled: false, period, seasonKey: key, items: [] };
    }
    const rows = await this.giftsRepo
      .createQueryBuilder('g')
      .select('g.roomId', 'roomId')
      .addSelect('COALESCE(SUM(g.diamondsAwarded), 0)', 'score')
      .where('g.roomId IS NOT NULL')
      .andWhere('g.createdAt >= :start', { start })
      .groupBy('g.roomId')
      .orderBy('score', 'DESC')
      .limit(50)
      .getRawMany();
    const ids = rows.map((r) => r.roomId).filter(Boolean);
    const rooms = ids.length
      ? await this.roomsRepo.find({
          where: { id: In(ids) },
          relations: ['host'],
        })
      : [];
    const byId = new Map(rooms.map((r) => [r.id, r]));
    const items = rows.map((r, i) => {
      const room = byId.get(r.roomId);
      return {
        rank: i + 1,
        roomId: r.roomId,
        score: Math.floor(Number(r.score) || 0),
        title: room?.title || 'غرفة',
        coverUrl: room?.coverUrl || null,
        publicId: (room as any)?.publicId || null,
        isOfficial: !!(room as any)?.isOfficial,
        cupBadgeSeason: (room as any)?.cupBadgeSeason || null,
        hostName: room?.host?.displayName || room?.host?.username || null,
      };
    });
    return { enabled: true, period, seasonKey: key, periodStart: start.toISOString(), periodEnd: end?.toISOString() || null, items };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/end-season')
  @ApiOperation({ summary: 'Award prizes, set winner badges, reset for next season' })
  async endSeason() {
    await this.settingsRepo.query('SELECT pg_advisory_lock(hashtext($1))', ['jeho:room-cup-settlement-v1']);
    try {
      const period = (await this.setting('room_cup.period', 'weekly')) || 'weekly';
      const bounds = await this.seasonBounds(period);
      const lastClosed = await this.setting('room_cup.last_closed_season', '');
      if (lastClosed === bounds.key) {
        return { closed: true, seasonKey: bounds.key, alreadyClosed: true, winners: [] };
      }
      const board = await this.buildLeaderboard();
    let prizes: any[] = [];
    try {
      prizes = JSON.parse(await this.setting('room_cup.prizes', '[]'));
    } catch {
      prizes = [];
    }
    const seasonKey = board.seasonKey;
    // Clear previous badges
    await this.roomsRepo
      .createQueryBuilder()
      .update(Room)
      .set({ cupBadgeSeason: null })
      .where('"cupBadgeSeason" IS NOT NULL')
      .execute();

    for (const prize of prizes) {
      const rank = Number(prize.rank) || 0;
      const item = board.items.find((x: any) => x.rank === rank);
      if (!item?.roomId) continue;
      const room = await this.roomsRepo.findOne({ where: { id: item.roomId } });
      if (!room) continue;
      room.cupBadgeSeason = seasonKey;
      await this.roomsRepo.save(room);
      const hostId = room.hostId;
      if (!hostId) continue;
      const coins = Math.max(0, Math.floor(Number(prize.coins) || 0));
      const diamonds = Math.max(0, Math.floor(Number(prize.diamonds) || 0));
      if (coins > 0 || diamonds > 0) {
        let wallet = await this.walletsRepo.findOne({ where: { userId: hostId } });
        if (!wallet) {
          wallet = await this.walletsRepo.save(this.walletsRepo.create({ userId: hostId }));
        }
        if (coins > 0) {
          wallet.coins = Number(wallet.coins) + coins;
          await this.walletsRepo.save(wallet);
          await this.txRepo.save(
            this.txRepo.create({
              userId: hostId,
              type: TransactionType.LUCKY_REWARD,
              currency: CurrencyType.COINS,
              amount: coins,
              balanceAfter: Number(wallet.coins),
              referenceType: 'room_cup',
              referenceId: `${seasonKey}:r${rank}:coins`,
              description: `جائزة كأس الروم #${rank}`,
            }),
          );
        }
        if (diamonds > 0) {
          wallet.diamonds = Number(wallet.diamonds) + diamonds;
          await this.walletsRepo.save(wallet);
          await this.txRepo.save(
            this.txRepo.create({
              userId: hostId,
              type: TransactionType.LUCKY_REWARD,
              currency: CurrencyType.DIAMONDS,
              amount: diamonds,
              balanceAfter: Number(wallet.diamonds),
              referenceType: 'room_cup',
              referenceId: `${seasonKey}:r${rank}:diamonds`,
              description: `جائزة كأس الروم #${rank}`,
            }),
          );
        }
      }
      // VIP is purchase-only — ignore vipLevel / vipPlanId prizes from room cup.
      const cosmeticId = String(prize.cosmeticId || '').trim();
      if (cosmeticId) {
        const cosmetic = await this.cosmeticsRepo.findOne({ where: { id: cosmeticId, isActive: true } });
        if (cosmetic) {
          const existing = await this.userCosmeticsRepo.findOne({ where: { userId: hostId, cosmeticId } });
          const days = Math.max(0, Math.floor(Number(prize.cosmeticDays) || 0));
          const expiresAt = days > 0 ? new Date(Date.now() + days * 86400000) : null;
          if (existing) {
            if (!existing.expiresAt || !expiresAt || expiresAt > existing.expiresAt) existing.expiresAt = expiresAt;
            await this.userCosmeticsRepo.save(existing);
          } else {
            await this.userCosmeticsRepo.save(this.userCosmeticsRepo.create({
              userId: hostId, cosmeticId, equipped: false, expiresAt,
            }));
          }
        } else {
          this.logger.warn('Room cup reward cosmetic not found: ' + cosmeticId);
        }
      }
    }

    await this.settingsRepo.save(
      (await this.settingsRepo.findOne({ where: { key: 'room_cup.season_key' } })) ||
        this.settingsRepo.create({ key: 'room_cup.season_key' }),
    ).catch(() => undefined);
    const seasonRow = await this.settingsRepo.findOne({ where: { key: 'room_cup.season_key' } });
    if (seasonRow) {
      seasonRow.value = seasonKey;
      await this.settingsRepo.save(seasonRow);
    } else {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: 'room_cup.season_key',
          value: seasonKey,
          description: 'Last closed room cup season',
        }),
      );
    }
    let closed = await this.settingsRepo.findOne({ where: { key: 'room_cup.last_closed_season' } });
    if (!closed) closed = this.settingsRepo.create({ key: 'room_cup.last_closed_season', value: seasonKey });
    closed.value = seasonKey;
    await this.settingsRepo.save(closed);

    const durationMs = Math.max(86400000, bounds.end.getTime() - bounds.start.getTime());
    const nextStart = bounds.end;
    const nextEnd = new Date(nextStart.getTime() + durationMs);
    const startRow = await this.settingsRepo.findOne({ where: { key: 'room_cup.start_at' } });
    const endRow = await this.settingsRepo.findOne({ where: { key: 'room_cup.end_at' } });
    if (startRow && endRow) {
      startRow.value = nextStart.toISOString();
      endRow.value = nextEnd.toISOString();
      await this.settingsRepo.save(startRow);
      await this.settingsRepo.save(endRow);
    }
    return {
      closed: true,
      seasonKey,
      nextStartAt: nextStart.toISOString(),
      nextEndAt: nextEnd.toISOString(),
      winners: board.items.slice(0, 3),
    };
    } finally {
      await this.settingsRepo.query('SELECT pg_advisory_unlock(hashtext($1))', ['jeho:room-cup-settlement-v1']).catch(() => undefined);
    }
  }
}
