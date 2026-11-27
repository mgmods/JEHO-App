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
  }

  private async setting(key: string, fallback = '') {
    const row = await this.settingsRepo.findOne({ where: { key } });
    return row?.value ?? fallback;
  }

  private seasonBounds(period: string) {
    const now = new Date();
    if (period === 'monthly') {
      const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
      const key = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`;
      return { start, key };
    }
    // weekly ISO-ish: Monday UTC
    const day = now.getUTCDay() || 7;
    const start = new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - (day - 1)),
    );
    const oneJan = new Date(Date.UTC(now.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((start.getTime() - oneJan.getTime()) / 86400000 + oneJan.getUTCDay() + 1) / 7);
    const key = `${now.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
    return { start, key };
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
    return { enabled: true, period, seasonKey: key, periodStart: start.toISOString(), items };
  }

  @UseGuards(JwtAuthGuard, AdminGuard)
  @ApiBearerAuth()
  @Post('admin/end-season')
  @ApiOperation({ summary: 'Atomically award prizes once, set winner badges, and close the room cup season' })
  async endSeason() {
    // The advisory transaction lock serializes concurrent admin clicks and retries.
    // The last-closed key is checked while holding the same lock, so no reward can
    // be paid twice for a season even if two requests arrive simultaneously.
    return this.roomsRepo.manager.transaction(async (manager) => {
      await manager.query(
        'SELECT pg_advisory_xact_lock(hashtext($1))',
        ['jeho:room-cup:end-season'],
      );

      const settings = manager.getRepository(AppSetting);
      const rooms = manager.getRepository(Room);
      const wallets = manager.getRepository(Wallet);
      const transactions = manager.getRepository(WalletTransaction);
      const board = await this.leaderboard();
      const seasonKey = String(board.seasonKey || '');
      if (!seasonKey) {
        throw new Error('Room cup season key is missing; no prizes were issued.');
      }

      const lastClosed = await settings.findOne({ where: { key: 'room_cup.season_key' } });
      if (lastClosed?.value === seasonKey) {
        return { closed: true, alreadyClosed: true, seasonKey, winners: board.items.slice(0, 3) };
      }

      let prizes: any[] = [];
      try {
        const raw = await this.setting('room_cup.prizes', '[]');
        const parsed = JSON.parse(raw);
        prizes = Array.isArray(parsed) ? parsed : [];
      } catch {
        prizes = [];
      }

      await rooms.createQueryBuilder()
        .update(Room)
        .set({ cupBadgeSeason: null })
        .where('"cupBadgeSeason" IS NOT NULL')
        .execute();

      for (const prize of prizes) {
        const rank = Number(prize.rank) || 0;
        const item = board.items.find((entry: any) => entry.rank === rank);
        if (!item?.roomId) continue;

        const room = await rooms.findOne({ where: { id: item.roomId } });
        if (!room) continue;
        room.cupBadgeSeason = seasonKey;
        await rooms.save(room);

        const hostId = room.hostId;
        if (!hostId) continue;
        const coins = Math.max(0, Math.floor(Number(prize.coins) || 0));
        const diamonds = Math.max(0, Math.floor(Number(prize.diamonds) || 0));
        if (coins <= 0 && diamonds <= 0) continue;

        let wallet = await wallets.findOne({ where: { userId: hostId }, lock: { mode: 'pessimistic_write' } });
        if (!wallet) {
          wallet = wallets.create({ userId: hostId });
        }

        if (coins > 0) {
          wallet.coins = Number(wallet.coins || 0) + coins;
          await wallets.save(wallet);
          await transactions.save(transactions.create({
            userId: hostId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS,
            amount: coins,
            balanceAfter: Number(wallet.coins),
            referenceType: 'room_cup',
            referenceId: `${seasonKey}:r${rank}:coins`,
            description: `جائزة كأس الروم #${rank}`,
          }));
        }
        if (diamonds > 0) {
          wallet.diamonds = Number(wallet.diamonds || 0) + diamonds;
          await wallets.save(wallet);
          await transactions.save(transactions.create({
            userId: hostId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.DIAMONDS,
            amount: diamonds,
            balanceAfter: Number(wallet.diamonds),
            referenceType: 'room_cup',
            referenceId: `${seasonKey}:r${rank}:diamonds`,
            description: `جائزة كأس الروم #${rank}`,
          }));
        }
      }

      // VIP remains purchase-only. Persist the closed season in the same
      // transaction as wallet credits and badges, so a failure rolls everything back.
      if (lastClosed) {
        lastClosed.value = seasonKey;
        lastClosed.description = 'Last closed room cup season';
        await settings.save(lastClosed);
      } else {
        await settings.save(settings.create({
          key: 'room_cup.season_key',
          value: seasonKey,
          description: 'Last closed room cup season',
        }));
      }

      return { closed: true, alreadyClosed: false, seasonKey, winners: board.items.slice(0, 3) };
    });
  }
}
}