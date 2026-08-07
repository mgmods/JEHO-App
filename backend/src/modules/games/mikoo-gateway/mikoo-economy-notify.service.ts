import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { SlotGameSession } from '../../../database/entities/slot-game-session.entity';
import { User } from '../../../database/entities/user.entity';
import { RealtimeGateway } from '../../realtime/realtime.gateway';
import { findMikooGame, mikooCoverUrl } from '../mikoo-games.catalog';

/**
 * Room chat + session stats for live Mikoo play (wallet debit/credit stays in WS handlers).
 */
@Injectable()
export class MikooEconomyNotifyService {
  private readonly logger = new Logger(MikooEconomyNotifyService.name);

  constructor(
    @InjectRepository(SlotGameSession)
    private readonly sessions: Repository<SlotGameSession>,
    @InjectRepository(User)
    private readonly users: Repository<User>,
    private readonly realtime: RealtimeGateway,
  ) {}

  async onBetWin(opts: {
    sessionId: string;
    userId: string;
    gameId: string;
    betCoins: number;
    winCoins: number;
    balanceAfter: number;
    /** Explicit settle loss (multi/crash wipe). Avoids treating a multi-area bet as a lose. */
    lostCoins?: number;
  }) {
    const bet = Math.max(0, Math.floor(opts.betCoins || 0));
    const win = Math.max(0, Math.floor(opts.winCoins || 0));
    const lost = Math.max(0, Math.floor(opts.lostCoins || 0));
    this.logger.log(
      `play ${opts.gameId} user=${opts.userId} bet=${bet} win=${win} lost=${lost} bal=${opts.balanceAfter}`,
    );

    const session = await this.sessions.findOne({ where: { id: opts.sessionId } });
    if (!session) return;

    if (bet > 0) session.totalBetCoins = Number(session.totalBetCoins || 0) + bet;
    if (win > 0) session.totalWinCoins = Number(session.totalWinCoins || 0) + win;
    // Count once per wager (not again on win-settlement with betCoins=0).
    if (bet > 0) session.spinCount = Number(session.spinCount || 0) + 1;
    session.lastHeartbeatAt = new Date();
    await this.sessions.save(session);

    const roomId = session.roomId?.trim();
    if (!roomId) return;

    const user = await this.users.findOne({ where: { id: opts.userId } });
    const game = findMikooGame(opts.gameId);
    let displayName = (user?.displayName || user?.username || 'Player').trim();
    if (!displayName || /^[0-9a-f-]{20,}$/i.test(displayName)) {
      displayName = user?.username?.trim() || 'Player';
    }
    const avatarUrl = user?.avatarUrl || '';
    const won = win > 0;
    const gameTitle = game?.title || opts.gameId;
    const gameCoverUrl = game ? mikooCoverUrl(game.id) : '';

    this.realtime.emitToRoom(roomId, 'room:slot_play', {
      roomId,
      userId: opts.userId,
      displayName,
      avatarUrl,
      gameId: opts.gameId,
      gameTitle,
      gameCoverUrl,
      gameIconUrl: gameCoverUrl,
      betCoins: bet,
      winCoins: win,
      won,
      balanceAfter: opts.balanceAfter,
    });

    if (won) {
      this.realtime.emitToRoom(roomId, 'room:slot_win', {
        roomId,
        userId: opts.userId,
        displayName,
        avatarUrl,
        gameId: opts.gameId,
        gameTitle,
        gameCoverUrl,
        gameIconUrl: gameCoverUrl,
        winCoins: win,
        totalWinCoins: session.totalWinCoins,
        balanceAfter: opts.balanceAfter,
      });
      // Global «مبروك» toast — encourages others (Mikoo-style, even outside rooms).
      if (win >= 50) {
        this.realtime.emitToAll('celebration:toast', {
          kind: 'game_win',
          id: `game:${opts.userId}:${opts.gameId}:${Date.now()}`,
          userId: opts.userId,
          displayName,
          avatarUrl,
          gameId: opts.gameId,
          gameTitle,
          gameIconUrl: gameCoverUrl,
          gameCoverUrl,
          coinsWon: win,
          roomId,
          title: 'مبروك!',
          body: `${displayName} لعب ${gameTitle} وفاز بـ ${win}`,
          at: new Date().toISOString(),
        });
      }
      return;
    }

    // Lose FX: explicit multi/crash settle OR completed spin with bet>0 (not fishing spam / not multi bet).
    const multiBetPhase = [
      '7updown',
      'greedy-box',
      'luck-car',
      'lucky77',
      'bounty-football',
      'camel-racing',
      'crash',
    ].includes(opts.gameId);
    const spinLose = bet > 0 && win === 0 && !multiBetPhase && opts.gameId !== 'fishing';
    const loseAmount = lost > 0 ? lost : spinLose ? bet : 0;
    if (loseAmount > 0) {
      this.realtime.emitToRoom(roomId, 'room:slot_lose', {
        roomId,
        userId: opts.userId,
        displayName,
        avatarUrl,
        gameId: opts.gameId,
        gameTitle,
        gameCoverUrl,
        gameIconUrl: gameCoverUrl,
        betCoins: loseAmount,
        winCoins: 0,
        balanceAfter: opts.balanceAfter,
      });
    }
  }

  /** Top winners for in-game Rank / «كأس» lists. */
  async topWinners(gameId?: string, limit = 20) {
    const take = Math.min(50, Math.max(1, limit));
    const qb = this.sessions
      .createQueryBuilder('s')
      .select('s.userId', 'userId')
      .addSelect('SUM(s.totalWinCoins)', 'wins')
      .addSelect('SUM(s.spinCount)', 'spins')
      .groupBy('s.userId')
      .orderBy('wins', 'DESC')
      .limit(take);
    if (gameId) qb.where('s.gameId = :gameId', { gameId });
    const rows = await qb.getRawMany<{ userId: string; wins: string; spins: string }>();
    const out: Array<{
      userId: string;
      nickname: string;
      avatar: string;
      score: number;
      win: number;
      spins: number;
      rank: number;
    }> = [];
    let rank = 1;
    for (const row of rows) {
      const user = await this.users.findOne({ where: { id: row.userId } });
      const score = Number(row.wins) || 0;
      let nickname = (user?.displayName || user?.username || 'Player').trim();
      if (!nickname || /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(nickname) || /^[0-9a-f-]{20,}$/i.test(nickname)) {
        const u = (user?.username || '').trim();
        nickname = u && !/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(u) && !/^[0-9a-f-]{20,}$/i.test(u) ? u : 'Player';
      }
      const publicId = (user?.publicId || '').trim();
      out.push({
        userId: /^\d+$/.test(publicId) ? publicId : '0',
        nickname,
        avatar: user?.avatarUrl || '',
        score,
        win: score,
        spins: Number(row.spins) || 0,
        rank: rank++,
      });
    }
    return out;
  }
}
