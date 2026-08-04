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
import { DiceRollDto } from './dto/dice.dto';
import { RoomGameAccessService } from './room-game-access.service';

/** Display multipliers — capped so wins stay reasonable. */
export const DICE_ODDS: Record<number, number> = {
  2: 8,
  3: 6,
  4: 5,
  5: 4,
  6: 3,
  7: 2,
  8: 3,
  9: 4,
  10: 5,
  11: 6,
  12: 8,
};

@Injectable()
export class DiceService {
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
      'games.dice.enabled',
      'games.dice.min_bet',
      'games.dice.max_bet',
      'games.dice.win_weight',
      'games.dice.lose_weight',
    ];
    const rows = await this.settingsRepo.find({ where: keys.map((key) => ({ key })) });
    const map = new Map(rows.map((r) => [r.key, r.value]));
    const num = (k: string, fallback: number) => {
      const n = Number(map.get(k));
      return Number.isFinite(n) ? n : fallback;
    };
    return {
      enabled: (map.get('games.dice.enabled') ?? 'true') !== 'false',
      minBet: Math.max(1, num('games.dice.min_bet', 100)),
      maxBet: Math.max(100, num('games.dice.max_bet', 20_000)),
      /** Very low hit rate (~8%). */
      winWeight: Math.max(0, num('games.dice.win_weight', 8)),
      loseWeight: Math.max(1, num('games.dice.lose_weight', 92)),
    };
  }

  async getState(userId: string) {
    const cfg = await this.cfg();
    if (!cfg.enabled) throw new BadRequestException('Dice game disabled');
    let wallet = await this.walletRepo.findOne({ where: { userId } });
    if (!wallet) wallet = await this.walletRepo.save(this.walletRepo.create({ userId }));
    return {
      enabled: true,
      balance: Number(wallet.coins || 0),
      currency: 'coins',
      todayWins: await this.todayWins(userId),
      config: {
        minBet: cfg.minBet,
        maxBet: cfg.maxBet,
        chips: [100, 1000, 10000, 50000].filter((c) => c <= cfg.maxBet),
        odds: DICE_ODDS,
      },
    };
  }

  async roll(userId: string, dto: DiceRollDto, roomId?: string) {
    await this.roomAccess.assertParticipant(roomId, userId);
    const cfg = await this.cfg();
    if (!cfg.enabled) throw new BadRequestException('Dice game disabled');
    const amount = Math.floor(Number(dto.amount));
    const pick = Math.floor(Number(dto.number));
    if (amount < cfg.minBet || amount > cfg.maxBet) {
      throw new BadRequestException(`Bet must be between ${cfg.minBet} and ${cfg.maxBet}`);
    }
    if (pick < 2 || pick > 12) throw new BadRequestException('Number must be 2-12');

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
      const rollId = randomUUID();
      // Unique per ledger row — uq_wallet_tx_user_reference is (userId, referenceType, referenceId).
      // Never reuse the same referenceId across bet/win rows.
      const betRef = `${rollId}:bet`;
      const winRef = `${rollId}:win`;
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: -amount,
          balanceAfter: Number(wallet.coins),
          referenceType: 'dice_bet',
          referenceId: betRef,
          description: 'Dice bet (coins)',
          metadata: { amount, number: pick, roomId: roomId || null, rollId, currency: 'coins' },
        }),
      );

      const hit = this.shouldWin(cfg);
      const [d1, d2] = hit ? this.comboFor(pick) : this.comboNot(pick);
      const total = d1 + d2;
      const multiplier = DICE_ODDS[pick] || 2;
      const won = total === pick;
      const payout = won ? amount * multiplier : 0;

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
            referenceType: 'dice_win',
            referenceId: winRef,
            description: `Dice win x${multiplier} (coins)`,
            metadata: {
              amount,
              payout,
              multiplier,
              pick,
              d1,
              d2,
              roomId: roomId || null,
              rollId,
              currency: 'coins',
            },
          }),
        );
        await this.addTodayWin(manager, userId, payout);
      }

      return {
        balance: Number(wallet.coins || 0),
        currency: 'coins',
        bet: amount,
        pick,
        d1,
        d2,
        total,
        multiplier,
        payout,
        won,
        net: payout - amount,
      };
    });

    if (roomId) {
      const player = await this.roomAccess.playerIdentity(userId);
      this.realtime?.emitToRoom(roomId, 'dice:roll', {
        roomId,
        ...player,
        total: result.total,
        won: result.won,
        payout: result.payout,
      });
    }

    return { ...result, todayWins: await this.todayWins(userId) };
  }

  private shouldWin(cfg: { winWeight: number; loseWeight: number }) {
    const total = cfg.winWeight + cfg.loseWeight;
    return Math.random() * total < cfg.winWeight;
  }

  private comboFor(sum: number): [number, number] {
    const combos: [number, number][] = [];
    for (let i = 1; i <= 6; i++) {
      for (let j = 1; j <= 6; j++) {
        if (i + j === sum) combos.push([i, j]);
      }
    }
    return combos[Math.floor(Math.random() * combos.length)] || [1, 1];
  }

  private comboNot(sum: number): [number, number] {
    let d1 = 1;
    let d2 = 1;
    let guard = 0;
    do {
      d1 = 1 + Math.floor(Math.random() * 6);
      d2 = 1 + Math.floor(Math.random() * 6);
      guard++;
    } while (d1 + d2 === sum && guard < 40);
    return [d1, d2];
  }

  private todayKey() {
    return new Date().toISOString().slice(0, 10);
  }

  private async todayWins(userId: string) {
    const key = `dice.today.${userId}.${this.todayKey()}`;
    const row = await this.settingsRepo.findOne({ where: { key } });
    return Number(row?.value || 0) || 0;
  }

  private async addTodayWin(
    manager: import('typeorm').EntityManager,
    userId: string,
    payout: number,
  ) {
    const key = `dice.today.${userId}.${this.todayKey()}`;
    const repo = manager.getRepository(AppSetting);
    let row = await repo.findOne({ where: { key } });
    if (!row) {
      row = repo.create({ key, value: String(payout), description: 'Daily dice wins' });
    } else {
      row.value = String(Number(row.value || 0) + payout);
    }
    await repo.save(row);
  }
}
