import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { createHmac } from 'crypto';
import { SlotGameSession } from '../../../database/entities/slot-game-session.entity';
import { User } from '../../../database/entities/user.entity';
import { Wallet } from '../../../database/entities/wallet.entity';
import { AppSetting } from '../../../database/entities/app-setting.entity';
import { findMikooGame } from '../mikoo-games.catalog';

export type MikooPlayerContext = {
  sessionId: string;
  userId: string;
  publicId: number;
  gameId: string;
  gameType: number;
  displayName: string;
  avatarUrl: string;
  balance: number;
  ticket: string;
  /** Voice-room UUID when opened from a room (for chat notifications). */
  roomId: string | null;
};

@Injectable()
export class MikooSessionService {
  constructor(
    @InjectRepository(SlotGameSession)
    private readonly sessions: Repository<SlotGameSession>,
    @InjectRepository(User)
    private readonly users: Repository<User>,
    @InjectRepository(Wallet)
    private readonly wallets: Repository<Wallet>,
    @InjectRepository(AppSetting)
    private readonly settings: Repository<AppSetting>,
  ) {}

  private async secret() {
    const row = await this.settings.findOne({ where: { key: 'games.bsgame.code_secret' } });
    return row?.value?.trim() || 'jeho-slot-local';
  }

  private cleanTicket(ticket: string) {
    let t = String(ticket || '').trim();
    try {
      t = decodeURIComponent(t);
    } catch {
      /* keep */
    }
    return t;
  }

  private async absorbGamePoints(wallet: Wallet | null): Promise<number> {
    if (!wallet) return 0;
    const leftover = Math.max(0, Math.floor(Number(wallet.gamePoints || 0)));
    if (leftover <= 0) return Number(wallet.coins || 0);
    wallet.coins = Number(wallet.coins || 0) + leftover;
    wallet.gamePoints = 0;
    await this.wallets.save(wallet);
    return Number(wallet.coins || 0);
  }

  async resolvePlayer(
    gameSlug: string,
    playerId: number,
    ticket: string,
  ): Promise<MikooPlayerContext | null> {
    const code = this.cleanTicket(ticket);
    if (!code) return null;
    const game = findMikooGame(gameSlug);
    if (!game) return null;

    let session = await this.sessions.findOne({
      where: { code, status: 'active', gameId: game.id },
    });
    // Fallback: ticket alone (gameId mismatch from slug/path)
    if (!session) {
      session = await this.sessions.findOne({ where: { code, status: 'active' } });
    }
    if (!session) return null;

    const user = await this.users.findOne({ where: { id: session.userId } });
    if (!user) return null;

    const publicId = Number(user.publicId || 0);
    // Prefer matching playerId; if client sends 0 / missing, still allow ticket auth
    if (playerId > 0 && publicId > 0 && playerId !== publicId) {
      return null;
    }

    let wallet = await this.wallets.findOne({ where: { userId: user.id } });
    if (!wallet) {
      wallet = await this.wallets.save(this.wallets.create({ userId: user.id }));
    }
    const balance = await this.absorbGamePoints(wallet);

    const rawName = (user.displayName || user.username || 'Player').trim();
    const displayName =
      !rawName || /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(rawName) || /^[0-9a-f-]{20,}$/i.test(rawName)
        ? (() => {
            const u = (user.username || '').trim();
            return u && !/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(u) && !/^[0-9a-f-]{20,}$/i.test(u)
              ? u
              : 'Player';
          })()
        : rawName;

    return {
      sessionId: session.id,
      userId: user.id,
      publicId: publicId || playerId || 0,
      gameId: game.id,
      gameType: game.gameType ?? 0,
      displayName,
      avatarUrl: user.avatarUrl || '',
      balance,
      ticket: code,
      roomId: session.roomId || null,
    };
  }

  async resolveByTicketOnly(gameSlug: string, ticket: string) {
    return this.resolvePlayer(gameSlug, 0, ticket);
  }

  async refreshBalance(ctx: MikooPlayerContext) {
    const wallet = await this.wallets.findOne({ where: { userId: ctx.userId } });
    ctx.balance = await this.absorbGamePoints(wallet);
    return ctx.balance;
  }

  async signLocalCode(publicId: number, gameId: string) {
    const secret = await this.secret();
    const nonce = Math.random().toString(16).slice(2, 10);
    const payload = `${publicId}:${gameId}:${Date.now()}:${nonce}`;
    const sig = createHmac('sha256', secret).update(payload).digest('hex').slice(0, 32);
    return `${sig}${nonce}`;
  }
}
