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
import { User } from '../../database/entities/user.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { RoomGameAccessService } from './room-game-access.service';
import { clampGamePayout, GAME_PAYOUT } from './game-payout-guard';

type CasualGameId =
  | 'slots'
  | 'roulette'
  | 'plinko'
  | 'crash'
  | 'rps'
  | 'hilo'
  | 'witch'
  | 'blackjack'
  | 'poker'
  | 'tarot'
  | 'penalty'
  | 'reaction';

const ALL_CASUAL: CasualGameId[] = [
  'slots',
  'roulette',
  'plinko',
  'crash',
  'rps',
  'hilo',
  'witch',
  'blackjack',
  'poker',
  'tarot',
  'penalty',
  'reaction',
];

@Injectable()
export class CasualBetService {
  constructor(
    @InjectRepository(Wallet)
    private readonly walletRepo: Repository<Wallet>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    private readonly dataSource: DataSource,
    private readonly roomAccess: RoomGameAccessService,
    @Optional() private readonly realtime?: RealtimeGateway,
  ) {}

  private async displayName(userId: string) {
    const u = await this.usersRepo.findOne({ where: { id: userId } });
    return u?.displayName || u?.username || 'لاعب';
  }

  async state(userId: string) {
    let wallet = await this.walletRepo.findOne({ where: { userId } });
    if (!wallet) wallet = await this.walletRepo.save(this.walletRepo.create({ userId }));
    return {
      balance: Number(wallet.coins || 0),
      currency: 'coins',
      minBet: 50,
      maxBet: Math.min(20_000, GAME_PAYOUT.maxBet),
      chips: [50, 100, 500, 1000, 5000, 10_000],
      games: ALL_CASUAL,
    };
  }

  async play(
    userId: string,
    body: {
      gameId: CasualGameId;
      amount: number;
      roomId?: string;
      choice?: string | number;
    },
  ) {
    const gameId = String(body?.gameId || '').toLowerCase() as CasualGameId;
    if (!ALL_CASUAL.includes(gameId)) {
      throw new BadRequestException('Unknown game');
    }
    await this.roomAccess.assertParticipant(body?.roomId, userId);
    const amount = Math.floor(Number(body?.amount));
    if (!Number.isFinite(amount) || amount < 50 || amount > Math.min(20_000, GAME_PAYOUT.maxBet)) {
      throw new BadRequestException(`Bet must be between 50 and ${Math.min(20_000, GAME_PAYOUT.maxBet)} coins`);
    }

    const outcome = this.resolveOutcome(gameId, body?.choice);
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
      const playId = randomUUID();
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: -amount,
          balanceAfter: Number(wallet.coins),
          referenceType: `casual_${gameId}_bet`,
          referenceId: `${playId}:bet`,
          description: `${gameId} bet (coins)`,
          metadata: { gameId, amount, roomId: body?.roomId || null, currency: 'coins' },
        }),
      );

      const rawPayout = Math.floor(amount * outcome.multiplier);
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
            referenceType: `casual_${gameId}_win`,
            referenceId: `${playId}:win`,
            description: `${gameId} win x${outcome.multiplier} (coins)`,
            metadata: {
              gameId,
              amount,
              payout,
              rawPayout,
              multiplier: outcome.multiplier,
              detail: outcome.detail,
              roomId: body?.roomId || null,
              currency: 'coins',
            },
          }),
        );
      }

      return {
        balance: Number(wallet.coins || 0),
        currency: 'coins',
        gameId,
        bet: amount,
        payout,
        won: payout > 0,
        net: payout - amount,
        multiplier: outcome.multiplier,
        detail: outcome.detail,
      };
    });

    const name = await this.displayName(userId);
    if (body?.roomId) {
      this.realtime?.emitToRoom(body.roomId, 'casual-game:play', {
        roomId: body.roomId,
        userId,
        displayName: name,
        gameId,
        won: result.won,
        payout: result.payout,
        bet: amount,
        detail: result.detail,
      });
    }

    return { ...result, displayName: name };
  }

  private resolveOutcome(
    gameId: CasualGameId,
    choice?: string | number,
  ): { multiplier: number; detail: Record<string, unknown> } {
    if (gameId === 'slots') {
      const sym = ['7️⃣', '💎', '🍒', '⭐', '🍋', '🔔'];
      const a = sym[Math.floor(Math.random() * sym.length)];
      const b = sym[Math.floor(Math.random() * sym.length)];
      const c = sym[Math.floor(Math.random() * sym.length)];
      let multiplier = 0;
      if (a === b && b === c) multiplier = a === '7️⃣' ? 12 : 6;
      else if (a === b || b === c || a === c) multiplier = 1.5;
      // house edge: force lose often
      if (Math.random() < 0.72) {
        return {
          multiplier: 0,
          detail: { reels: [a, b === a ? sym[(sym.indexOf(a) + 1) % sym.length] : b, c] },
        };
      }
      return { multiplier, detail: { reels: [a, b, c] } };
    }
    if (gameId === 'roulette') {
      const n = Math.floor(Math.random() * 37);
      const red = new Set([
        1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36,
      ]);
      const color = n === 0 ? 'green' : red.has(n) ? 'red' : 'black';
      const pick = String(choice || 'red').toLowerCase();
      const hit = pick === color;
      // Color odds 1.85 + slight void chance for house (~78–82% RTP red/black)
      let multiplier = 0;
      if (hit) {
        if (color === 'green') multiplier = 12;
        else if (Math.random() < 0.88) multiplier = 1.85;
      }
      return { multiplier, detail: { number: n, color, pick } };
    }
    if (gameId === 'plinko') {
      // RTP ≈ 72–78% (was ~300%). Soft outer mults, rare center.
      const bins = [0.2, 0.5, 0.8, 1.5, 0.8, 0.5, 0.2];
      const weights = [8, 14, 20, 16, 20, 14, 8];
      const total = weights.reduce((a, b) => a + b, 0);
      let r = Math.random() * total;
      let idx = 0;
      for (let i = 0; i < weights.length; i++) {
        r -= weights[i];
        if (r <= 0) {
          idx = i;
          break;
        }
      }
      return { multiplier: bins[idx], detail: { bin: idx, label: `x${bins[idx]}` } };
    }
    if (gameId === 'crash') {
      // Stronger early bust than 0.92 param.
      const crashAt = Math.max(1.0, Math.floor((1 / (1 - Math.random() * 0.78)) * 100) / 100);
      const cashout = Math.max(1.1, Number(choice) || 1.5);
      const won = cashout < crashAt;
      return {
        multiplier: won ? Math.round(cashout * 0.97 * 100) / 100 : 0,
        detail: { crashAt, cashout, won },
      };
    }
    if (gameId === 'rps') {
      const opts = ['rock', 'paper', 'scissors'] as const;
      const you = String(choice || 'rock').toLowerCase();
      const bot = opts[Math.floor(Math.random() * 3)];
      let multiplier = 0;
      if (you === bot) multiplier = 1; // push
      else if (
        (you === 'rock' && bot === 'scissors') ||
        (you === 'paper' && bot === 'rock') ||
        (you === 'scissors' && bot === 'paper')
      ) {
        multiplier = 1.55; // RTP ≈ 85% raw → feel fair, house on edges elsewhere
      }
      return { multiplier, detail: { you, bot } };
    }
    if (gameId === 'hilo') {
      const card = 2 + Math.floor(Math.random() * 13);
      const next = 2 + Math.floor(Math.random() * 13);
      const pick = String(choice || 'high').toLowerCase();
      const won =
        (pick === 'high' && next > card) ||
        (pick === 'low' && next < card) ||
        (pick === 'same' && next === card);
      return {
        multiplier: won ? (pick === 'same' ? 5 : 1.6) : 0,
        detail: { card, next, pick },
      };
    }
    if (gameId === 'witch') {
      const prophecies = [
        'نعم قريباً',
        'احذر الطريق',
        'كنز بانتظارك',
        'الحظ معك الليلة',
        'انتظر إشارة',
        'قوة خفية تعمل',
        'ابتسم للقدر',
      ];
      const prophecy = prophecies[Math.floor(Math.random() * prophecies.length)];
      const multiplier = Math.random() < 0.28 ? (Math.random() < 0.15 ? 5 : 2) : 0;
      return { multiplier, detail: { prophecy } };
    }
    if (gameId === 'blackjack') {
      const draw = () => 2 + Math.floor(Math.random() * 10);
      let you = draw() + draw();
      let dealer = draw() + draw();
      while (you < 17) you += draw();
      while (dealer < 17) dealer += draw();
      you = Math.min(you, 26);
      dealer = Math.min(dealer, 26);
      let multiplier = 0;
      if (you <= 21 && (dealer > 21 || you > dealer)) multiplier = you === 21 ? 2.0 : 1.7;
      else if (you <= 21 && you === dealer) multiplier = 1;
      return { multiplier, detail: { you, dealer } };
    }
    if (gameId === 'poker') {
      const dice = Array.from({ length: 5 }, () => 1 + Math.floor(Math.random() * 6));
      const counts = new Map<number, number>();
      dice.forEach((d) => counts.set(d, (counts.get(d) || 0) + 1));
      const vals = [...counts.values()].sort((a, b) => b - a);
      let multiplier = 0;
      if (vals[0] === 5) multiplier = 20;
      else if (vals[0] === 4) multiplier = 8;
      else if (vals[0] === 3 && vals[1] === 2) multiplier = 6;
      else if (vals[0] === 3) multiplier = 3;
      else if (vals[0] === 2 && vals[1] === 2) multiplier = 2;
      else if (vals[0] === 2) multiplier = 1.2;
      if (Math.random() < 0.55 && multiplier > 1.2) multiplier = 0;
      return { multiplier, detail: { dice, hand: vals[0] } };
    }
    if (gameId === 'tarot') {
      const cards = ['🌟', '🌙', '🔥', '💧', '⚔️', '👑', '🌹', '💀'];
      const card = cards[Math.floor(Math.random() * cards.length)];
      const multiplier = Math.random() < 0.3 ? (card === '👑' ? 6 : 2) : 0;
      return { multiplier, detail: { card } };
    }
    if (gameId === 'penalty') {
      const dirs = ['L', 'C', 'R'];
      const shot = String(choice || 'C').toUpperCase();
      // Keeper saves more often than pure 1/3 (house).
      const keeperBias = Math.random();
      const keeper =
        keeperBias < 0.42
          ? shot
          : dirs[Math.floor(Math.random() * 3)];
      const won = shot !== keeper;
      return { multiplier: won ? 1.55 : 0, detail: { shot, keeper } };
    }
    if (gameId === 'reaction') {
      // Client ms is untrusted — fixed low-edge server roll (no free ×4).
      const roll = Math.random();
      let multiplier = 0;
      if (roll < 0.12) multiplier = 1.5;
      else if (roll < 0.28) multiplier = 1.1;
      else if (roll < 0.4) multiplier = 1.0;
      const ms = Math.max(1, Number(choice) || 999);
      return { multiplier, detail: { ms, serverRoll: true } };
    }
    return { multiplier: 0, detail: {} };
  }
}
