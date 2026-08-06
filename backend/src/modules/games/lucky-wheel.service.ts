import {
  BadRequestException,
  Injectable,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { DataSource, Repository } from 'typeorm';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  CurrencyType,
  TransactionType,
  WalletTransaction,
} from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { LuckyWheelSpinDto } from './dto/lucky-wheel.dto';
import { RoomGameAccessService } from './room-game-access.service';
import { clampGamePayout, GAME_PAYOUT } from './game-payout-guard';

export type LuckyWheelSector = 'red' | 'orange' | 'seven' | 'lose';

/** Visual wheel sectors (8) — must match lucky-wheel.html slice order. */
export const WHEEL_SECTORS: { id: LuckyWheelSector; multiplier: number }[] = [
  { id: 'seven', multiplier: 6 },
  { id: 'orange', multiplier: 2 },
  { id: 'lose', multiplier: 0 },
  { id: 'orange', multiplier: 2 },
  { id: 'red', multiplier: 2 },
  { id: 'lose', multiplier: 0 },
  { id: 'red', multiplier: 2 },
  { id: 'orange', multiplier: 2 },
];

@Injectable()
export class LuckyWheelService {
  constructor(
    @InjectRepository(Wallet)
    private readonly walletRepo: Repository<Wallet>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly dataSource: DataSource,
    private readonly roomAccess: RoomGameAccessService,
    @Optional() private readonly realtime?: RealtimeGateway,
  ) {}

  private async cfg() {
    const keys = [
      'games.lucky_wheel.enabled',
      'games.lucky_wheel.min_bet',
      'games.lucky_wheel.max_bet',
      'games.lucky_wheel.lose_weight',
      'games.lucky_wheel.x2_weight',
      'games.lucky_wheel.x8_weight',
    ];
    const rows = await this.settingsRepo.find({ where: keys.map((key) => ({ key })) });
    const map = new Map(rows.map((r) => [r.key, r.value]));
    const num = (k: string, fallback: number) => {
      const n = Number(map.get(k));
      return Number.isFinite(n) ? n : fallback;
    };
    return {
      enabled: (map.get('games.lucky_wheel.enabled') ?? 'true') !== 'false',
      minBet: Math.max(1, num('games.lucky_wheel.min_bet', 100)),
      maxBet: Math.min(Math.max(100, num('games.lucky_wheel.max_bet', 10_000)), GAME_PAYOUT.maxBet),
      /** Very low win rate by default. */
      loseWeight: Math.max(1, num('games.lucky_wheel.lose_weight', 72)),
      x2Weight: Math.max(0, num('games.lucky_wheel.x2_weight', 24)),
      x8Weight: Math.max(0, num('games.lucky_wheel.x8_weight', 4)),
    };
  }

  async getState(userId: string) {
    const cfg = await this.cfg();
    if (!cfg.enabled) throw new BadRequestException('Lucky wheel disabled');
    let wallet = await this.walletRepo.findOne({ where: { userId } });
    if (!wallet) wallet = await this.walletRepo.save(this.walletRepo.create({ userId }));
    const today = await this.todayWins(userId);
    return {
      enabled: true,
      balance: Number(wallet.coins || 0),
      currency: 'coins',
      todayWins: today,
      config: {
        minBet: cfg.minBet,
        maxBet: cfg.maxBet,
        chips: [100, 500, 1000, 5000, 10_000].filter((c) => c <= cfg.maxBet),
        multipliers: { red: 2, orange: 2, seven: 6, lose: 0 },
        sectors: WHEEL_SECTORS,
      },
    };
  }

  async spin(userId: string, dto: LuckyWheelSpinDto, roomId?: string) {
    await this.roomAccess.assertParticipant(roomId, userId);
    const cfg = await this.cfg();
    if (!cfg.enabled) throw new BadRequestException('Lucky wheel disabled');
    const amount = Math.floor(Number(dto.amount));
    if (amount < cfg.minBet || amount > cfg.maxBet) {
      throw new BadRequestException(`Bet must be between ${cfg.minBet} and ${cfg.maxBet}`);
    }

    const result = await this.dataSource.transaction(async (manager) => {
      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) wallet = await manager.save(manager.create(Wallet, { userId }));
      if (Number(wallet.coins || 0) < amount) {
        throw new BadRequestException('Insufficient coins');
      }

      wallet.coins = Number(wallet.coins || 0) - amount;
      await manager.save(wallet);
      const spinId = randomUUID();
      const betRef = `${spinId}:bet`;
      const winRef = `${spinId}:win`;
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: -amount,
          balanceAfter: Number(wallet.coins),
          referenceType: 'lucky_wheel_bet',
          referenceId: betRef,
          description: 'Lucky wheel bet (coins)',
          metadata: { amount, roomId: roomId || null, spinId, currency: 'coins' },
        }),
      );

      const outcome = this.pickOutcome(cfg);
      const sectorIndex = this.pickSectorIndex(outcome);
      const sector = WHEEL_SECTORS[sectorIndex];
      const multiplier = sector.multiplier;
      const rawPayout = amount * multiplier;
      const { win: payout } = clampGamePayout({ bet: amount, win: rawPayout });

      if (payout > 0) {
        wallet.coins = Number(wallet.coins || 0) + payout;
        await manager.save(wallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS,
            amount: payout,
            balanceAfter: Number(wallet.coins),
            referenceType: 'lucky_wheel_win',
            referenceId: winRef,
            description: `Lucky wheel win x${multiplier} (coins)`,
            metadata: {
              amount,
              payout,
              rawPayout,
              multiplier,
              sector: sector.id,
              roomId: roomId || null,
              spinId,
              currency: 'coins',
            },
          }),
        );
      }

      // Track today wins in a lightweight setting key per user-day (optional aggregate).
      if (payout > 0) {
        await this.addTodayWin(manager, userId, payout);
      }

      return {
        balance: Number(wallet.coins || 0),
        currency: 'coins',
        bet: amount,
        sectorIndex,
        sector: sector.id,
        multiplier,
        payout,
        won: payout > 0,
        net: payout - amount,
      };
    });

    if (roomId) {
      const player = await this.roomAccess.playerIdentity(userId);
      this.realtime?.emitToRoom(roomId, 'lucky-wheel:spin', {
        roomId,
        ...player,
        sector: result.sector,
        payout: result.payout,
        won: result.won,
      });
    }

    return {
      ...result,
      todayWins: await this.todayWins(userId),
    };
  }

  private pickOutcome(cfg: {
    loseWeight: number;
    x2Weight: number;
    x8Weight: number;
  }): 'lose' | 'x2' | 'x8' {
    const total = cfg.loseWeight + cfg.x2Weight + cfg.x8Weight;
    let r = Math.random() * total;
    if (r < cfg.loseWeight) return 'lose';
    r -= cfg.loseWeight;
    if (r < cfg.x2Weight) return 'x2';
    return 'x8';
  }

  private pickSectorIndex(outcome: 'lose' | 'x2' | 'x8'): number {
    const idxs = WHEEL_SECTORS.map((s, i) => ({ s, i })).filter(({ s }) => {
      if (outcome === 'lose') return s.id === 'lose';
      if (outcome === 'x8') return s.id === 'seven';
      return s.multiplier === 2;
    });
    return idxs[Math.floor(Math.random() * idxs.length)]?.i ?? 2;
  }

  private todayKey() {
    return new Date().toISOString().slice(0, 10);
  }

  private async todayWins(userId: string) {
    const key = `lucky_wheel.today.${userId}.${this.todayKey()}`;
    const row = await this.settingsRepo.findOne({ where: { key } });
    return Number(row?.value || 0) || 0;
  }

  private async addTodayWin(
    manager: import('typeorm').EntityManager,
    userId: string,
    payout: number,
  ) {
    const key = `lucky_wheel.today.${userId}.${this.todayKey()}`;
    const repo = manager.getRepository(AppSetting);
    let row = await repo.findOne({ where: { key } });
    if (!row) {
      row = repo.create({
        key,
        value: String(payout),
        description: 'Daily lucky-wheel wins',
      });
    } else {
      row.value = String(Number(row.value || 0) + payout);
    }
    await repo.save(row);
  }
}
