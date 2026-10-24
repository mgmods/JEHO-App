import { Controller, Get, Post, Body, UseGuards, OnModuleInit, Logger } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Room } from '../../database/entities/room.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Wallet } from '../../database/entities/wallet.entity';
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
export class RoomCupController implements OnModuleInit {
  private readonly logger = new Logger(RoomCupController.name);
  private roomCupTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    @InjectRepository(GiftSend) private readonly giftsRepo: Repository<GiftSend>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(VipPlan) private readonly vipPlansRepo: Repository<VipPlan>,
    @InjectRepository(UserVip) private readonly userVipRepo: Repository<UserVip>,
  ) {}

  async onModuleInit() {
    try {
      await this.roomsRepo.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "cupBadgeSeason" VARCHAR(32)`,
      );
    } catch (err) {
      this.logger.warn(`cupBadgeSeason ensure failed: ${(err as Error).message}`);
    }
    // A lightweight UTC boundary checker: no extra dependency and no request needed.
    await this.checkSeasonRollover().catch((err) =>
      this.logger.error(`Initial room-cup rollover check failed: ${(err as Error).message}`),
    );
    this.roomCupTimer = setInterval(() => {
      void this.checkSeasonRollover().catch((err) =>
        this.logger.error(`Room-cup automatic rewards failed: ${(err as Error).message}`),
      );
    }, 60_000);
    this.roomCupTimer.unref?.();
  }

  onModuleDestroy() {
    if (this.roomCupTimer) clearInterval(this.roomCupTimer);
  }

  private async setting(key: string, fallback = '') {
    const row = await this.settingsRepo.findOne({ where: { key } });
    return row?.value ?? fallback;
  }

  private seasonBounds(period: string, at = new Date()) {
    let start: Date;
    if (period === 'monthly') {
      start = new Date(Date.UTC(at.getUTCFullYear(), at.getUTCMonth(), 1));
    } else {
      const day = at.getUTCDay() || 7;
      start = new Date(Date.UTC(at.getUTCFullYear(), at.getUTCMonth(), at.getUTCDate() - (day - 1)));
    }
    return this.boundsForStart(period, start);
  }

  private boundsForStart(period: string, start: Date) {
    const end = period === 'monthly'
      ? new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 1))
      : new Date(start.getTime() + 7 * 86400000);
    let key: string;
    if (period === 'monthly') {
      key = `${start.getUTCFullYear()}-${String(start.getUTCMonth() + 1).padStart(2, '0')}`;
    } else {
      const oneJan = new Date(Date.UTC(start.getUTCFullYear(), 0, 1));
      const week = Math.ceil(((start.getTime() - oneJan.getTime()) / 86400000 + oneJan.getUTCDay() + 1) / 7);
      key = `${start.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
    }
    return { start, end, key };
  }

  private async checkSeasonRollover() {
    if ((await this.setting('room_cup.enabled', 'true')) === 'false') return;
    const period = (await this.setting('room_cup.period', 'weekly')) || 'weekly';
    const current = this.seasonBounds(period);
    const checkpointKey = 'room_cup.auto_next_start';
    const checkpoint = await this.settingsRepo.findOne({ where: { key: checkpointKey } });
    // First boot establishes a baseline; never pay out historical seasons by surprise.
    if (!checkpoint?.value) {
      await this.settingsRepo.save(this.settingsRepo.create({
        key: checkpointKey, value: current.start.toISOString(),
        description: 'Next room-cup season start to process automatically',
      }));
      return;
    }
    let cursor = new Date(checkpoint.value);
    if (Number.isNaN(cursor.getTime())) {
      cursor = current.start;
      checkpoint.value = cursor.toISOString();
      await this.settingsRepo.save(checkpoint);
      return;
    }
    // Catch up every missed season after a sleep/restart, one at a time.
    let guard = 0;
    while (cursor.getTime() < current.start.getTime() && guard++ < 24) {
      const season = this.boundsForStart(period, cursor);
      const board = await this.leaderboardForRange(period, season.start, season.end, season.key);
      await this.endSeason(board, season.key);
      cursor = season.end;
      const latest = await this.settingsRepo.findOne({ where: { key: checkpointKey } });
      if (latest) {
        latest.value = cursor.toISOString();
        await this.settingsRepo.save(latest);
      } else {
        await this.settingsRepo.save(this.settingsRepo.create({
          key: checkpointKey, value: cursor.toISOString(),
          description: 'Next room-cup season start to process automatically',
        }));
      }
      this.logger.log(`Automatically awarded room cup season ${season.key}`);
    }
  }

  @Public()
  @Get('leaderboard')
  @ApiOperation({ summary: 'Current room cup leaderboard' })
  async leaderboard() {
    const enabled = (await this.setting('room_cup.enabled', 'true')) !== 'false';
    const period = (await this.setting('room_cup.period', 'weekly')) || 'weekly';
    const { start, key } = this.seasonBounds(period);
    if (!enabled) {
      return { enabled: false, period, seasonKey: key, items: [] };
    }
    const bounds = this.seasonBounds(period);
    return this.leaderboardForRange(period, bounds.start, bounds.end, key);
  }

  private async leaderboardForRange(period: string, start: Date, end: Date, key: string) {
    const rows = await this.giftsRepo
      .createQueryBuilder('g')
      .select('g.roomId', 'roomId')
      .addSelect('COALESCE(SUM(g.diamondsAwarded), 0)', 'score')
      .where('g.roomId IS NOT NULL')
      .andWhere('g.createdAt >= :start AND g.createdAt < :end', { start, end })
      .groupBy('g.roomId')
      .orderBy('score', 'DESC')
      .limit(50)
      .getRawMany();
    const ids = rows.map((r) => r.roomId).filter(Boolean);
    const rooms = ids.length
      ? await this.roomsRepo.find({ where: { id: In(ids) }, relations: ['host'] })
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
    return { enabled: true, period, seasonKey: key, periodStart: start.toISOString(), items };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/end-season')
  @ApiOperation({ summary: 'Award prizes, set winner badges, reset for next season' })
  async endSeason(boardOverride?: any, seasonKeyOverride?: string) {
    const board = boardOverride || await this.leaderboard();
    let prizes: any[] = [];
    try {
      prizes = JSON.parse(await this.setting('room_cup.prizes', '[]'));
    } catch {
      prizes = [];
    }
    const seasonKey = seasonKeyOverride || board.seasonKey;
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
          const referenceId = `${seasonKey}:r${rank}:coins`;
          const alreadyPaid = await this.txRepo.findOne({ where: { userId: hostId, referenceType: 'room_cup', referenceId } });
          if (!alreadyPaid) {
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
              referenceId,
              description: `جائزة كأس الروم #${rank}`,
            }),
          );
          }
        }
        if (diamonds > 0) {
          const referenceId = `${seasonKey}:r${rank}:diamonds`;
          const alreadyPaid = await this.txRepo.findOne({ where: { userId: hostId, referenceType: 'room_cup', referenceId } });
          if (!alreadyPaid) {
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
              referenceId,
              description: `جائزة كأس الروم #${rank}`,
            }),
          );
          }
        }
      }
      // VIP is purchase-only — ignore vipLevel / vipPlanId prizes from room cup.
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
    return { closed: true, seasonKey, winners: board.items.slice(0, 3) };
  }
}
