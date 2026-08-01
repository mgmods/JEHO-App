import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource } from 'typeorm';
import { randomUUID } from 'crypto';
import type { Server as HttpServer, IncomingMessage } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { packFrame, unpackFrame } from './mikoo-frame.util';
import {
  decodeBetReq,
  decodeCostBetReq,
  decodeFields,
  decodeLoginReq,
  encode7UpBetRsp,
  encodeBetRsp,
  encodeCashoutRsp,
  encodeCleopatraBetRes,
  encodeDoSlotsRsp,
  encodeEmpty,
  encodeFortuneGameCfgRes,
  encodeFortuneGemsRes,
  encodeGameOverBroadcast,
  encodeGameOverRsp,
  encodeGetUserDataFor,
  encodeGreedyBetRes,
  encodeGreedyResultBroadcast,
  encodeGreedyStartBetBroadcast,
  encodeGreedyTableInfoRes,
  encodeHeartBeatRsp,
  encodeLineSlotsGameCfgRsp,
  encodeLoginRes,
  encodeLuckCarBetRsp,
  encodeLuckCarGameOverRsp,
  encodeLuckCarGetRankDataRes,
  encodeLuckCarGetUserRecordRes,
  encodeCrashGetUserRecordRes,
  encodeLuckCarStartBetBroadcast,
  encodeLuckCarTableInfo,
  encodeLucky77BetRsp,
  encodeLucky77GetRankDataRes,
  encodeLucky77OtherPlayerBetBroadcast,
  encodeLucky77ResultBroadcast,
  encodeLucky77StartBetBroadcast,
  encodeLucky77TableInfoRes,
  LUCKY77_RATIOS,
  LUCKY77_ROUNDNO,
  encodeMatchRes,
  encodeMegawaysBetRes,
  encodeMessage,
  encodeOkCodeDesc,
  encodeOlympiansBetRes,
  encodePirateBetRes,
  encodeChipCfgRes,
  encodeCleopatraTableInfo,
  encodeOlympiansTableInfo,
  encodePirateTableInfo,
  encodeStartBetBroadcast,
  encodeStartFlyBroadcast,
  encodeSugarBetRes,
  encodeTableInfo7UpDown,
  encodeTableInfoCrash,
  encodeUpdateRatioBroadcast,
  pbInt32,
  pbString,
  pbUInt64,
} from './mikoo-proto.util';
import { MikooSessionService, MikooPlayerContext } from './mikoo-session.service';
import { Wallet } from '../../../database/entities/wallet.entity';
import {
  CurrencyType,
  TransactionType,
  WalletTransaction,
} from '../../../database/entities/wallet-transaction.entity';
import { findMikooGame, MikooGameDef } from '../mikoo-games.catalog';
import { BaishunWsHandler } from './baishun-ws.handler';
import { MikooEconomyNotifyService } from './mikoo-economy-notify.service';
import { biasedCrashAt, multiAreaMultipliers, spinPayout } from './mikoo-house-edge.util';

type GameKind = 'crash' | 'multi' | 'spin';

type RoomBet = {
  userId: string;
  playerId: number;
  sessionId: string;
  amount: number;
  areaId: number;
  cashedOut: boolean;
  payout: number;
  ws?: WebSocket;
  displayName?: string;
  avatarUrl?: string;
};

type RoomState = {
  kind: GameKind;
  phase: 'betting' | 'flying' | 'over';
  round: number;
  crashAt: number;
  ratio: number;
  flyStart: number;
  betEnds: number;
  bets: Map<string, RoomBet>; // key = playerId:areaId
  clients: Set<WebSocket>;
  timers: NodeJS.Timeout[];
  /** Recent winning areas for 7updown history strip (1=down,2=seven,3=up). */
  history: number[];
};

function gameKind(game: MikooGameDef): GameKind {
  if (game.id === 'crash') return 'crash';
  // multi-area / dice-style hash games (fortune-slot is a spin slot, not multi)
  if (['7updown', 'greedy-box', 'luck-car', 'lucky77'].includes(game.id)) return 'multi';
  return 'spin';
}

@Injectable()
export class MikooGatewayService implements OnModuleDestroy {
  private readonly logger = new Logger(MikooGatewayService.name);
  private wss?: WebSocketServer;
  private readonly rooms = new Map<string, RoomState>();

  constructor(
    private readonly sessions: MikooSessionService,
    private readonly dataSource: DataSource,
    private readonly baishun: BaishunWsHandler,
    private readonly economy: MikooEconomyNotifyService,
    @InjectRepository(Wallet)
    private readonly wallets: Repository<Wallet>,
  ) {}

  onModuleDestroy() {
    this.wss?.close();
    for (const room of this.rooms.values()) {
      for (const t of room.timers) clearTimeout(t);
    }
  }

  attach(httpServer: HttpServer) {
    this.wss = new WebSocketServer({ noServer: true });
    httpServer.on('upgrade', (req, socket, head) => {
      const url = req.url || '';
      if (!url.startsWith('/games/ws/')) return;
      this.wss?.handleUpgrade(req, socket, head, (ws) => {
        const pathPart = url.split('/games/ws/')[1] || '';
        const slug = pathPart.split('?')[0]?.replace(/\/$/, '') || '';
        this.handleConnection(ws, slug, req);
      });
    });
    this.logger.log('Mikoo game WebSocket gateway attached at /games/ws/:gameId');
  }

  private send(ws: WebSocket, name: string, body: Buffer) {
    if (ws.readyState === WebSocket.OPEN) ws.send(packFrame(name, body));
  }

  private broadcast(room: RoomState, name: string, body: Buffer) {
    for (const ws of room.clients) this.send(ws, name, body);
  }

  /** Aggregate Lucky77 area totals (icon 0/1/2). Optional filter by playerId. */
  private lucky77AreaBets(
    room: RoomState,
    onlyPlayerId?: number,
  ): Array<{ icon: number; money: number }> {
    const map = new Map<number, number>();
    for (const b of room.bets.values()) {
      if (onlyPlayerId != null && b.playerId !== onlyPlayerId) continue;
      map.set(b.areaId, (map.get(b.areaId) || 0) + b.amount);
    }
    return [...map.entries()].map(([icon, money]) => ({ icon, money }));
  }

  /** Safe BetRsp for Lucky77 — curBet required by client chip-fly path. */
  private sendLucky77BetRsp(
    ws: WebSocket,
    data: {
      code: number;
      desc: string;
      tipType?: number;
      userMoney: number;
      betTotal?: Array<{ icon: number; money: number }>;
      curBet?: { icon: number; money: number };
    },
  ) {
    this.send(
      ws,
      '.game.BetRsp',
      encodeLucky77BetRsp({
        ...data,
        curBet: data.curBet ?? { icon: 0, money: 0 },
      }),
    );
  }

  private ensureRoom(gameId: string, kind: GameKind): RoomState {
    let room = this.rooms.get(gameId);
    if (!room) {
      room = {
        kind,
        phase: 'betting',
        round: 1,
        crashAt: this.randomCrash(),
        ratio: 1,
        flyStart: 0,
        betEnds: Date.now() + 10000,
        bets: new Map(),
        clients: new Set(),
        timers: [],
        history: [],
      };
      this.rooms.set(gameId, room);
      if (kind === 'crash' || kind === 'multi') this.scheduleRound(gameId);
    }
    return room;
  }

  private randomCrash() {
    return biasedCrashAt();
  }

  private scheduleRound(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    for (const t of room.timers) clearTimeout(t);
    room.timers = [];
    if (room.phase !== 'betting') return;
    // Empty table: idle until someone connects (still on OUR server, not Mikoo).
    if (room.clients.size === 0) return;
    const delay = Math.max(500, room.betEnds - Date.now());
    room.timers.push(setTimeout(() => this.finishBetting(gameId), delay));
    const secs = Math.ceil(delay / 1000);
    if (gameId === 'greedy-box') {
      this.broadcast(room, '.game.StartBetBroadcast', encodeGreedyStartBetBroadcast(1, secs, room.round));
    } else if (gameId === 'luck-car') {
      this.broadcast(
        room,
        '.game.StartBetBroadcast',
        encodeLuckCarStartBetBroadcast(secs, room.round),
      );
    } else if (gameId === 'lucky77') {
      this.broadcast(room, '.game.StartBetBroadcast', encodeLucky77StartBetBroadcast(1, secs));
    } else {
      this.broadcast(room, '.game.StartBetBroadcast', encodeStartBetBroadcast(secs));
    }
  }

  private finishBetting(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    if (room.kind === 'crash') {
      this.startFly(gameId);
      return;
    }
    // multi-area settle
    void this.settleMulti(gameId);
  }

  private startFly(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    room.phase = 'flying';
    room.ratio = 1;
    room.flyStart = Date.now();
    this.broadcast(room, '.game.StartFlyBroadcast', encodeStartFlyBroadcast(1, 3));
    this.tickFly(gameId);
  }

  private tickFly(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room || room.phase !== 'flying') return;
    const elapsed = Date.now() - room.flyStart;
    room.ratio = Math.round((1 + elapsed / 3200) * 100) / 100;
    this.broadcast(room, '.game.UpdateRatioBroadcast', encodeUpdateRatioBroadcast(room.ratio, elapsed));
    if (room.ratio >= room.crashAt) {
      room.phase = 'over';
      this.broadcast(room, '.game.GameOverBroadcast', encodeGameOverBroadcast(room.crashAt, 3));
      // Notify room for players who did not cash out (lost their bet).
      for (const bet of room.bets.values()) {
        if (bet.cashedOut || !bet.sessionId || bet.amount <= 0) continue;
        void this.economy.onBetWin({
          sessionId: bet.sessionId,
          userId: bet.userId,
          gameId,
          betCoins: 0,
          winCoins: 0,
          lostCoins: bet.amount,
          balanceAfter: 0,
        });
      }
      room.timers.push(setTimeout(() => this.resetRound(gameId), 3000));
      return;
    }
    room.timers.push(setTimeout(() => this.tickFly(gameId), 180));
  }

  private async settleMulti(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    room.phase = 'over';
    // Dice 1-6 twice for 7updown; greedy/luck-car pick icon; lucky77 wheel 1..9.
    const d1 = 1 + Math.floor(Math.random() * 6);
    const d2 = 1 + Math.floor(Math.random() * 6);
    const total = d1 + d2;
    const lucky77WinPos = 1 + Math.floor(Math.random() * 9);
    const lucky77AreaType = LUCKY77_ROUNDNO[lucky77WinPos - 1] || 1;
    const lucky77WinIcon = lucky77AreaType - 1; // client bets 0,1,2
    const winArea =
      gameId === 'greedy-box' || gameId === 'luck-car'
        ? 1 + Math.floor(Math.random() * 8)
        : gameId === 'lucky77'
          ? lucky77WinIcon
          : total < 7
            ? 1
            : total === 7
              ? 2
              : 3;
    if (gameId === '7updown') {
      room.history = [...room.history, winArea].slice(-12);
    } else if (gameId === 'lucky77') {
      room.history = [...room.history, lucky77WinPos].slice(-12);
    }
    const multipliers = multiAreaMultipliers();
    const greedyRatio: Record<number, number> = {
      1: 2, 2: 3, 3: 5, 4: 8, 5: 10, 6: 15, 7: 20, 8: 50,
    };
    const luckCarRatio: Record<number, number> = {
      1: 2, 2: 3, 3: 4, 4: 5, 5: 8, 6: 10, 7: 15, 8: 25,
    };

    // Aggregate 7updown / lucky77 wins per player (one settle msg).
    const sevenByUser = new Map<
      string,
      {
        userId: string;
        sessionId: string;
        ws?: WebSocket;
        win: number;
        betTotal: number;
        playerId: number;
        displayName: string;
        avatarUrl: string;
      }
    >();

    for (const bet of room.bets.values()) {
      const mult =
        gameId === 'greedy-box'
          ? bet.areaId === winArea
            ? greedyRatio[winArea] || 2
            : 0
          : gameId === 'luck-car'
            ? bet.areaId === winArea
              ? luckCarRatio[winArea] || 2
              : 0
            : gameId === 'lucky77'
              ? bet.areaId === winArea
                ? LUCKY77_RATIOS[winArea] || 2
                : 0
              : bet.areaId === winArea
                ? multipliers[winArea] || 2
                : 0;
      const win = Math.floor(bet.amount * mult);

      if (gameId === '7updown' || gameId === 'lucky77') {
        const key = bet.userId;
        const row = sevenByUser.get(key) || {
          userId: bet.userId,
          sessionId: bet.sessionId,
          ws: bet.ws,
          win: 0,
          betTotal: 0,
          playerId: bet.playerId,
          displayName: bet.displayName || 'Player',
          avatarUrl: bet.avatarUrl || '',
        };
        row.win += win;
        row.betTotal += bet.amount;
        if (bet.ws) row.ws = bet.ws;
        if (bet.sessionId) row.sessionId = bet.sessionId;
        if (bet.displayName) row.displayName = bet.displayName;
        if (bet.avatarUrl) row.avatarUrl = bet.avatarUrl;
        if (bet.playerId) row.playerId = bet.playerId;
        sevenByUser.set(key, row);
        continue;
      }

      let bal = 0;
      try {
        if (win > 0) bal = await this.credit(bet.userId, win, gameId, mult, bet.amount);
        else {
          const w = await this.wallets.findOne({ where: { userId: bet.userId } });
          bal = Number(w?.coins || 0);
        }
      } catch {
        bal = 0;
      }
      if (bet.ws && bet.ws.readyState === WebSocket.OPEN) {
        if (gameId === 'greedy-box') {
          this.send(
            bet.ws,
            '.game.ResultBroadcast',
            encodeGreedyResultBroadcast({
              settleTime: 4,
              curTurn: room.round,
              userMoney: bal,
              winMoney: win,
              bingoIcon: winArea,
            }),
          );
        } else if (gameId === 'luck-car') {
          const myBets = new Array(8).fill(0);
          myBets[Math.max(0, (bet.areaId || 1) - 1)] = bet.amount;
          this.send(
            bet.ws,
            '.game.GameOverRsp',
            encodeLuckCarGameOverRsp({
              winCar: winArea,
              winMoney: win,
              selfMoney: bal,
              myBets,
            }),
          );
        }
      }
      if (win > 0 && bet.sessionId) {
        void this.economy.onBetWin({
          sessionId: bet.sessionId,
          userId: bet.userId,
          gameId: gameId,
          betCoins: 0,
          winCoins: win,
          balanceAfter: bal,
        });
      } else if (win === 0 && bet.sessionId && bet.amount > 0) {
        void this.economy.onBetWin({
          sessionId: bet.sessionId,
          userId: bet.userId,
          gameId: gameId,
          betCoins: 0,
          winCoins: 0,
          lostCoins: bet.amount,
          balanceAfter: bal,
        });
      }
    }

    if (gameId === '7updown') {
      const notifiedWs = new Set<WebSocket>();
      for (const row of sevenByUser.values()) {
        let bal = 0;
        try {
          if (row.win > 0) {
            const ratio = row.betTotal > 0 ? row.win / row.betTotal : 0;
            bal = await this.credit(row.userId, row.win, gameId, ratio, row.betTotal);
          } else {
            const w = await this.wallets.findOne({ where: { userId: row.userId } });
            bal = Number(w?.coins || 0);
          }
        } catch {
          bal = 0;
        }
        if (row.ws && row.ws.readyState === WebSocket.OPEN) {
          this.send(
            row.ws,
            '.game.GameOverRsp',
            encodeGameOverRsp({ nums: [d1, d2], winMoney: row.win, selfMoney: bal }),
          );
          notifiedWs.add(row.ws);
        }
        if (row.win > 0 && row.sessionId) {
          void this.economy.onBetWin({
            sessionId: row.sessionId,
            userId: row.userId,
            gameId,
            betCoins: 0,
            winCoins: row.win,
            balanceAfter: bal,
          });
        } else if (row.win === 0 && row.sessionId && row.betTotal > 0) {
          void this.economy.onBetWin({
            sessionId: row.sessionId,
            userId: row.userId,
            gameId,
            betCoins: 0,
            winCoins: 0,
            lostCoins: row.betTotal,
            balanceAfter: bal,
          });
        }
      }
      for (const ws of room.clients) {
        if (notifiedWs.has(ws) || ws.readyState !== WebSocket.OPEN) continue;
        this.send(
          ws,
          '.game.GameOverRsp',
          encodeGameOverRsp({ nums: [d1, d2], winMoney: 0, selfMoney: 0 }),
        );
      }
    } else if (gameId === 'lucky77') {
      const notifiedWs = new Set<WebSocket>();
      const goodLuck = [...sevenByUser.values()]
        .filter((r) => r.win > 0)
        .sort((a, b) => b.win - a.win)
        .slice(0, 3)
        .map((r) => ({
          name: r.displayName || 'Player',
          head: r.avatarUrl || '',
          totalBet: r.betTotal,
          totalGain: r.win,
        }));
      const betRank = [...sevenByUser.values()]
        .filter((r) => r.win > 0 || r.betTotal > 0)
        .sort((a, b) => b.win - a.win || b.betTotal - a.betTotal)
        .slice(0, 10)
        .map((r) => ({
          playerId: r.playerId,
          name: r.displayName || 'Player',
          head: r.avatarUrl || '',
          awardMoney: r.win,
        }));
      for (const row of sevenByUser.values()) {
        let bal = 0;
        try {
          if (row.win > 0) {
            const ratio = row.betTotal > 0 ? row.win / row.betTotal : 0;
            bal = await this.credit(row.userId, row.win, gameId, ratio, row.betTotal);
          } else {
            const w = await this.wallets.findOne({ where: { userId: row.userId } });
            bal = Number(w?.coins || 0);
          }
        } catch {
          bal = 0;
        }
        if (row.ws && row.ws.readyState === WebSocket.OPEN) {
          this.send(
            row.ws,
            '.game.ResultBroadcast',
            encodeLucky77ResultBroadcast({
              state: 3,
              betTime: 4,
              name: row.displayName || 'Player',
              head: row.avatarUrl || '',
              totalBet: row.betTotal,
              totalGain: row.win,
              curBingoIcon: [lucky77WinPos],
              curTurn: room.round,
              userMoney: bal,
              todayWin: row.win,
              goodLuck,
              betRank,
            }),
          );
          notifiedWs.add(row.ws);
        }
        if (row.win > 0 && row.sessionId) {
          void this.economy.onBetWin({
            sessionId: row.sessionId,
            userId: row.userId,
            gameId,
            betCoins: 0,
            winCoins: row.win,
            balanceAfter: bal,
          });
        } else if (row.win === 0 && row.sessionId && row.betTotal > 0) {
          void this.economy.onBetWin({
            sessionId: row.sessionId,
            userId: row.userId,
            gameId,
            betCoins: 0,
            winCoins: 0,
            lostCoins: row.betTotal,
            balanceAfter: bal,
          });
        }
      }
      for (const ws of room.clients) {
        if (notifiedWs.has(ws) || ws.readyState !== WebSocket.OPEN) continue;
        this.send(
          ws,
          '.game.ResultBroadcast',
          encodeLucky77ResultBroadcast({
            state: 3,
            betTime: 4,
            totalBet: 0,
            totalGain: 0,
            curBingoIcon: [lucky77WinPos],
            curTurn: room.round,
            userMoney: 0,
            todayWin: 0,
            goodLuck,
            betRank,
          }),
        );
      }
    } else if (gameId === 'greedy-box' && room.bets.size === 0) {
      this.broadcast(
        room,
        '.game.ResultBroadcast',
        encodeGreedyResultBroadcast({
          settleTime: 4,
          curTurn: room.round,
          userMoney: 0,
          winMoney: 0,
          bingoIcon: winArea,
        }),
      );
    } else if (gameId === 'luck-car') {
      for (const ws of room.clients) {
        let already = false;
        for (const bet of room.bets.values()) {
          if (bet.ws === ws) {
            already = true;
            break;
          }
        }
        if (already || ws.readyState !== WebSocket.OPEN) continue;
        this.send(
          ws,
          '.game.GameOverRsp',
          encodeLuckCarGameOverRsp({
            winCar: winArea,
            winMoney: 0,
            selfMoney: 0,
            myBets: new Array(8).fill(0),
          }),
        );
      }
    }

    room.timers.push(
      setTimeout(() => this.resetRound(gameId), gameId === 'lucky77' ? 8500 : 4000),
    );
  }

  private resetRound(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    room.phase = 'betting';
    room.round += 1;
    room.crashAt = this.randomCrash();
    room.ratio = 1;
    // Lucky77: longer bet window so chip-fly + area taps are usable.
    room.betEnds = Date.now() + (gameId === 'lucky77' ? 18000 : 10000);
    room.bets.clear();
    this.scheduleRound(gameId);
  }

  private handleConnection(ws: WebSocket, gameSlug: string, req: IncomingMessage) {
    const game = findMikooGame(gameSlug);
    if (!game) {
      this.logger.warn(`Unknown game WS slug: ${gameSlug}`);
      ws.close();
      return;
    }
    if (game.bridge === 'baishun') {
      this.baishun.handle(ws, gameSlug, req.url || '');
      return;
    }

    const kind = gameKind(game);
    const room = this.ensureRoom(gameSlug, kind);
    const wasEmpty = room.clients.size === 0;
    room.clients.add(ws);
    // Resume multi rounds when first player joins an idle table.
    if (wasEmpty && (kind === 'crash' || kind === 'multi') && room.phase === 'betting' && room.timers.length === 0) {
      room.betEnds = Date.now() + (gameSlug === 'lucky77' ? 18000 : 10000);
      this.scheduleRound(gameSlug);
    }
    let player: MikooPlayerContext | null = null;
    // Serialize handlers — parallel LoginReq + GetUserDataReq drops post-login msgs.
    let chain: Promise<void> = Promise.resolve();

    ws.on('message', (data) => {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data as ArrayBuffer);
      const frame = unpackFrame(buf);
      if (!frame) return;
      chain = chain
        .then(async () => {
          player = await this.dispatch(ws, gameSlug, room, kind, frame.name, frame.body, player);
        })
        .catch((err) => {
          this.logger.warn(`WS chain ${gameSlug} ${frame.name}: ${(err as Error).message}`);
        });
    });

    ws.on('close', () => room.clients.delete(ws));
    ws.on('error', () => room.clients.delete(ws));
  }

  /** Push TableInfo / StartBet for crash, multi, and spin slots that need chip lists. */
  private pushTableState(
    ws: WebSocket,
    gameSlug: string,
    room: RoomState,
    kind: GameKind,
    player: MikooPlayerContext | null,
  ) {
    if (kind === 'spin') {
      this.pushSpinTableInfo(ws, gameSlug);
      return;
    }
    if (kind !== 'crash' && kind !== 'multi') return;
    const timeLeft =
      room.phase === 'betting' ? Math.max(1, Math.ceil((room.betEnds - Date.now()) / 1000)) : 0;
    const state = room.phase === 'betting' ? 1 : room.phase === 'flying' ? 2 : 3;
    if (gameSlug === 'greedy-box') {
      const body = encodeGreedyTableInfoRes({
        state,
        timeLeft,
        curTurn: room.round,
        userMoney: player?.balance ?? 0,
      });
      this.send(ws, '.game.TableInfoRes', body);
      this.send(ws, '.game.TableInfo', body);
      if (room.phase === 'betting' && timeLeft > 0) {
        this.send(ws, '.game.StartBetBroadcast', encodeGreedyStartBetBroadcast(1, timeLeft, room.round));
      }
      return;
    }
    if (gameSlug === 'lucky77') {
      const myAreaBet = player ? this.lucky77AreaBets(room, player.publicId) : [];
      const body = encodeLucky77TableInfoRes({
        state,
        timeLeft,
        curTurn: room.round,
        history: room.history.length ? room.history : [1, 3, 5, 7, 9, 2, 4, 6],
        betTotal: this.lucky77AreaBets(room),
        myAreaBet,
      });
      this.send(ws, '.game.TableInfoRes', body);
      this.send(ws, '.game.TableInfo', body);
      if (room.phase === 'betting' && timeLeft > 0) {
        this.send(ws, '.game.StartBetBroadcast', encodeLucky77StartBetBroadcast(1, timeLeft));
      }
      return;
    }
    if (gameSlug === '7updown') {
      this.send(
        ws,
        '.game.TableInfo',
        encodeTableInfo7UpDown({
          state,
          betTime: 10,
          playerNum: room.clients.size,
          timeLeft,
          history: room.history.length
            ? room.history
            : [1, 3, 2, 1, 3, 1, 2, 3],
        }),
      );
    } else if (gameSlug === 'luck-car') {
      this.send(
        ws,
        '.game.TableInfo',
        encodeLuckCarTableInfo({
          state,
          playerNum: room.clients.size,
          timeLeft,
          curTurn: room.round,
        }),
      );
    } else {
      this.send(
        ws,
        '.game.TableInfo',
        encodeTableInfoCrash({
          state,
          playerNum: room.clients.size,
          timeLeft,
          ratio: room.ratio,
        }),
      );
    }
    if (room.phase === 'betting' && timeLeft > 0) {
      if (gameSlug === 'luck-car') {
        this.send(
          ws,
          '.game.StartBetBroadcast',
          encodeLuckCarStartBetBroadcast(timeLeft, room.round),
        );
      } else {
        this.send(ws, '.game.StartBetBroadcast', encodeStartBetBroadcast(timeLeft));
      }
    }
  }

  /** Spin slots wait on TableInfo / chipCfg before bet UI unlocks (NaN / freeze without it). */
  private pushSpinTableInfo(ws: WebSocket, gameSlug: string) {
    if (gameSlug === 'olympians') {
      const body = encodeOlympiansTableInfo();
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      return;
    }
    if (gameSlug === 'pirate-king') {
      const body = encodePirateTableInfo();
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      return;
    }
    if (gameSlug === 'cleopatra-slots') {
      const body = encodeCleopatraTableInfo();
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      return;
    }
    if (gameSlug === 'megaways-slots' || gameSlug === 'sugar-rush') {
      this.send(ws, '.game.s_c_getChipCfgRes', encodeChipCfgRes());
      this.send(ws, '.game.GetChipCfgRes', encodeChipCfgRes());
      return;
    }
    if (gameSlug === 'fortune-slot') {
      this.send(ws, '.game.GameCfgRes', encodeFortuneGameCfgRes());
      this.send(ws, '.game.GameCfgRsp', encodeFortuneGameCfgRes());
      return;
    }
    if (gameSlug === 'line-slots') {
      this.send(ws, '.game.GameCfgRsp', encodeLineSlotsGameCfgRsp());
      this.send(ws, '.game.GameCfgRes', encodeLineSlotsGameCfgRsp());
    }
  }

  private async dispatch(
    ws: WebSocket,
    gameSlug: string,
    room: RoomState,
    kind: GameKind,
    name: string,
    body: Buffer,
    player: MikooPlayerContext | null,
  ): Promise<MikooPlayerContext | null> {
    try {
      if (name === '.login.LoginReq') {
        const req = decodeLoginReq(body);
        const ctx = await this.sessions.resolvePlayer(gameSlug, req.playerId, req.ticket);
        if (!ctx) {
          this.logger.warn(`login FAIL ${gameSlug} playerId=${req.playerId} ticket=${String(req.ticket || '').slice(0, 12)}…`);
          this.send(ws, '.login.LoginRes', encodeLoginRes({
            code: 1, desc: 'Invalid ticket', userMoney: 0, tipType: 1,
          }));
          return null;
        }
        this.logger.log(
          `login OK ${gameSlug} user=${ctx.userId} publicId=${ctx.publicId} bal=${ctx.balance} room=${ctx.roomId || '-'}`,
        );
        this.send(ws, '.login.LoginRes', encodeLoginRes({
          code: 0,
          desc: 'OK',
          playerId: ctx.publicId,
          tableId: `jeho-${gameSlug}`,
          name: ctx.displayName,
          head: ctx.avatarUrl,
          userMoney: ctx.balance,
          tipType: ctx.balance <= 0 ? 2 : 0,
        }));
        // Lucky77 HUD (name/avatar/coins) reads SelfData from GetUserData + Login.
        if (gameSlug === 'lucky77') {
          const ud = encodeGetUserDataFor(gameSlug, {
            code: 0,
            desc: 'OK',
            userMoney: ctx.balance,
            name: ctx.displayName,
            head: ctx.avatarUrl,
            id: ctx.publicId,
            tipType: ctx.balance <= 0 ? 2 : 0,
          });
          this.send(ws, '.game.GetUserDataRes', ud);
          this.send(ws, '.game.s_c_getUserData', ud);
        }
        // Crash / multi: seed select-time immediately after login (some builds skip Match).
        this.pushTableState(ws, gameSlug, room, kind, ctx);
        return ctx;
      }

      if (!player) {
        this.logger.debug(`hash drop (no player) ${gameSlug} ${name}`);
        return null;
      }

      if (name === '.hall.MatchReq') {
        this.send(ws, '.hall.MatchRes', encodeMatchRes({
          code: 0, desc: 'OK', tableId: `jeho-${gameSlug}`,
        }));
        if (gameSlug === 'lucky77') {
          const bal = await this.sessions.refreshBalance(player);
          player.balance = bal;
          const ud = encodeGetUserDataFor(gameSlug, {
            code: 0,
            desc: 'OK',
            userMoney: bal,
            name: player.displayName,
            head: player.avatarUrl,
            id: player.publicId,
            tipType: bal <= 0 ? 2 : 0,
          });
          this.send(ws, '.game.GetUserDataRes', ud);
          this.send(ws, '.game.s_c_getUserData', ud);
        }
        this.pushTableState(ws, gameSlug, room, kind, player);
        return player;
      }

      if (name === '.game.GetUserDataReq' || name === '.game.c_s_getUserData') {
        const bal = await this.sessions.refreshBalance(player);
        const payload = encodeGetUserDataFor(gameSlug, {
          code: 0, desc: 'OK', userMoney: bal,
          name: player.displayName, head: player.avatarUrl, id: player.publicId,
        });
        this.send(ws, '.game.GetUserDataRes', payload);
        this.send(ws, '.game.s_c_getUserData', payload);
        // Push chip/TableInfo/GameCfg so spin UIs leave splash (Mega already had chipCfg).
        this.pushSpinTableInfo(ws, gameSlug);
        return player;
      }

      if (name === '.game.TableInfoReq') {
        this.pushTableState(ws, gameSlug, room, kind, player);
        return player;
      }

      if (name === '.game.HeartBeatReq') {
        const f = decodeFields(body);
        const hb = encodeHeartBeatRsp(Number(f[1] ?? Date.now()));
        this.send(ws, '.game.HeartBeatRsp', hb);
        this.send(ws, '.game.HeartBeatRes', hb);
        return player;
      }

      if (name === '.game.GetRankDataReq') {
        try {
          const ranks = await this.economy.topWinners(gameSlug, 20);
          const filled =
            ranks.length >= 3 ? ranks : await this.economy.topWinners(undefined, 20);
          const mapped = filled.slice(0, 20).map((r) => ({
            playerId: Number(r.userId) || 0,
            name: r.nickname || String(r.userId) || 'Player',
            head: r.avatar || '',
            winMoney: Math.max(0, Number(r.score) || Number(r.win) || 0),
          }));
          if (gameSlug === 'lucky77') {
            this.send(ws, '.game.GetRankDataRes', encodeLucky77GetRankDataRes(mapped));
          } else {
            this.send(ws, '.game.GetRankDataRes', encodeLuckCarGetRankDataRes(mapped));
          }
        } catch (err) {
          this.logger.warn(`GetRankDataReq ${gameSlug}: ${(err as Error).message}`);
          this.send(
            ws,
            '.game.GetRankDataRes',
            gameSlug === 'lucky77'
              ? encodeLucky77GetRankDataRes([])
              : encodeLuckCarGetRankDataRes([]),
          );
        }
        return player;
      }

      if (name === '.game.GetUserRecordReq') {
        try {
          if (gameSlug === 'crash') {
            this.send(ws, '.game.GetUserRecordRes', encodeCrashGetUserRecordRes([]));
          } else if (gameSlug === 'luck-car') {
            this.send(ws, '.game.GetUserRecordRes', encodeLuckCarGetUserRecordRes([]));
          } else {
            // 7updown / others — empty crash-shaped is safest no-op for unknown
            this.send(ws, '.game.GetUserRecordRes', encodeCrashGetUserRecordRes([]));
          }
        } catch (err) {
          this.logger.warn(`GetUserRecordReq ${gameSlug}: ${(err as Error).message}`);
          this.send(ws, '.game.GetUserRecordRes', encodeCrashGetUserRecordRes([]));
        }
        return player;
      }

      if (name === '.game.c_s_bet') {
        const style = gameSlug === 'megaways-slots' ? 'megaways' : 'sugar';
        return this.handleSpinBet(ws, gameSlug, body, player, style);
      }

      if (name === '.game.DoSlotsReq') {
        return this.handleCostSpin(ws, gameSlug, body, player, 'doslots');
      }

      if (name === '.game.DoFortuneGemsReq') {
        return this.handleCostSpin(ws, gameSlug, body, player, 'fortune');
      }

      if (name === '.game.GameCfgReq') {
        if (gameSlug === 'line-slots') {
          this.send(ws, '.game.GameCfgRsp', encodeLineSlotsGameCfgRsp());
          this.send(ws, '.game.GameCfgRes', encodeLineSlotsGameCfgRsp());
        } else {
          this.send(ws, '.game.GameCfgRes', encodeFortuneGameCfgRes());
          this.send(ws, '.game.GameCfgRsp', encodeFortuneGameCfgRes());
        }
        return player;
      }

      if (name === '.game.c_s_getChipCfgReq' || name === '.game.GetChipCfgReq') {
        this.send(ws, '.game.s_c_getChipCfgRes', encodeChipCfgRes());
        this.send(ws, '.game.GetChipCfgRes', encodeChipCfgRes());
        return player;
      }

      if (name === '.game.BetReq') {
        this.logger.log(
          `GAME_PROBE BET_REQ game=${gameSlug} kind=${kind} user=${player.userId} bal=${player.balance} bytes=${body?.length || 0}`,
        );
        if (kind === 'spin') {
          const style =
            gameSlug === 'pirate-king'
              ? 'pirate'
              : gameSlug === 'megaways-slots'
                ? 'megaways'
                : gameSlug === 'cleopatra-slots'
                  ? 'cleopatra'
                  : 'olympians';
          return this.handleSpinBet(ws, gameSlug, body, player, style);
        }
        return this.handleBet(ws, gameSlug, room, kind, body, player);
      }

      if (name === '.game.CashoutReq' && kind === 'crash') {
        return this.handleCashout(ws, gameSlug, room, player);
      }

      if (name.endsWith('Req')) {
        const res = name.replace(/Req$/, 'Res');
        const rsp = name.replace(/Req$/, 'Rsp');
        const ok = encodeOkCodeDesc(0, 'OK');
        this.send(ws, res, ok);
        if (res !== rsp) this.send(ws, rsp, ok);
        this.logger.warn(`hash unhandled-ack ${gameSlug} ${name}`);
      } else {
        this.logger.warn(`hash unhandled ${gameSlug} ${name}`);
      }
    } catch (err) {
      this.logger.warn(`WS ${gameSlug} ${name}: ${(err as Error).message}`);
    }
    return player;
  }

  private async handleCostSpin(
    ws: WebSocket,
    gameSlug: string,
    body: Buffer,
    player: MikooPlayerContext,
    style: 'doslots' | 'fortune',
  ): Promise<MikooPlayerContext> {
    const req = decodeCostBetReq(body);
    let amount = Math.max(1, Math.floor(Number(req.money) || 0));
    // Cap single bet so max-chip spam can't break economy display.
    const MAX_BET = 10_000;
    if (amount > MAX_BET) amount = MAX_BET;
    const fail = (code: number, desc: string, tip: number) => {
      if (style === 'fortune') {
        this.send(ws, '.game.DoFortuneGemsRes', encodeFortuneGemsRes({
          code, desc, cost: amount, userMoney: player.balance, winMoney: 0, tipType: tip,
        }));
      } else {
        this.send(ws, '.game.DoSlotsRsp', encodeDoSlotsRsp({
          code, desc, cost: amount, userMoney: player.balance, winMoney: 0, tipType: tip,
        }));
      }
    };
    if (amount <= 0) {
      fail(1, 'Invalid bet', 1);
      return player;
    }
    try {
      let bal = await this.debit(player.userId, amount, gameSlug, player.publicId);
      const { win, mult } = spinPayout(amount);
      if (win > 0) bal = await this.credit(player.userId, win, gameSlug, mult, amount);
      player.balance = bal;
      if (style === 'fortune') {
        this.send(ws, '.game.DoFortuneGemsRes', encodeFortuneGemsRes({
          code: 0, desc: 'OK', cost: amount, userMoney: bal, winMoney: win, tipType: 0,
        }));
      } else {
        this.send(ws, '.game.DoSlotsRsp', encodeDoSlotsRsp({
          code: 0, desc: 'OK', cost: amount, userMoney: bal, winMoney: win, tipType: 0,
        }));
      }
      this.logger.log(
        `hash ${style} ${gameSlug} user=${player.userId} bet=${amount} win=${win} bal=${bal}`,
      );
      void this.economy.onBetWin({
        sessionId: player.sessionId,
        userId: player.userId,
        gameId: gameSlug,
        betCoins: amount,
        winCoins: win,
        balanceAfter: bal,
      });
    } catch {
      fail(1, 'Insufficient balance', 2);
    }
    return player;
  }

  private async handleSpinBet(
    ws: WebSocket,
    gameSlug: string,
    body: Buffer,
    player: MikooPlayerContext,
    style: 'olympians' | 'sugar' | 'megaways' | 'pirate' | 'cleopatra',
  ): Promise<MikooPlayerContext> {
    const req = decodeBetReq(body, style === 'pirate' ? 'pirate' : 'default');
    let amount = Math.max(1, Math.floor(Number(req.money) || 0));
    const MAX_BET = 10_000;
    if (amount > MAX_BET) amount = MAX_BET;
    this.logger.log(
      `GAME_PROBE SPIN_DECODE game=${gameSlug} style=${style} amount=${amount} roomId=${req.roomId} user=${player.userId} bal=${player.balance}`,
    );
    const fail = (code: number, desc: string, tip: number) => {
      if (style === 'sugar') {
        this.send(ws, '.game.s_c_bet', encodeSugarBetRes({
          code, desc, money: player.balance, tipType: tip, winMoney: 0,
        }));
      } else if (style === 'megaways') {
        this.send(ws, '.game.s_c_bet', encodeMegawaysBetRes({
          code, desc, money: player.balance, tipType: tip, winMoney: 0,
        }));
      } else if (style === 'pirate') {
        this.send(ws, '.game.BetRes', encodePirateBetRes({
          code, desc, selfMoney: player.balance, winMoney: 0, tipType: tip,
        }));
      } else if (style === 'cleopatra') {
        this.send(ws, '.game.BetRes', encodeCleopatraBetRes({
          code, desc, selfMoney: player.balance, winMoney: 0, tipType: tip,
        }));
      } else {
        this.send(ws, '.game.BetRes', encodeOlympiansBetRes({
          code, desc, selfMoney: player.balance, winMoney: 0, tipType: tip,
        }));
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code, desc, selfMoney: player.balance, tipType: tip,
        }));
      }
    };
    if (amount <= 0) {
      fail(1, 'Invalid bet', 1);
      return player;
    }
    try {
      let bal = await this.debit(player.userId, amount, gameSlug, player.publicId);
      const { win, mult } = spinPayout(amount);
      if (win > 0) bal = await this.credit(player.userId, win, gameSlug, mult, amount);
      player.balance = bal;
      if (style === 'sugar') {
        this.send(ws, '.game.s_c_bet', encodeSugarBetRes({
          code: 0, desc: 'OK', money: bal, tipType: 0, winMoney: win,
        }));
      } else if (style === 'megaways') {
        this.send(ws, '.game.s_c_bet', encodeMegawaysBetRes({
          code: 0, desc: 'OK', money: bal, tipType: 0, winMoney: win,
        }));
      } else if (style === 'pirate') {
        this.send(ws, '.game.BetRes', encodePirateBetRes({
          code: 0, desc: 'OK', selfMoney: bal, winMoney: win, tipType: 0,
        }));
      } else if (style === 'cleopatra') {
        this.send(ws, '.game.BetRes', encodeCleopatraBetRes({
          code: 0, desc: 'OK', selfMoney: bal, winMoney: win, tipType: 0,
        }));
      } else {
        this.send(ws, '.game.BetRes', encodeOlympiansBetRes({
          code: 0, desc: 'OK', selfMoney: bal, winMoney: win, tipType: 0,
        }));
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, betMoney: amount, tipType: 0,
        }));
      }
      this.logger.log(`hash spin ${gameSlug} user=${player.userId} bet=${amount} win=${win} bal=${bal}`);
      void this.economy.onBetWin({
        sessionId: player.sessionId,
        userId: player.userId,
        gameId: gameSlug,
        betCoins: amount,
        winCoins: win,
        balanceAfter: bal,
      });
    } catch {
      fail(1, 'Insufficient balance', 2);
    }
    return player;
  }

  private async handleBet(
    ws: WebSocket,
    gameSlug: string,
    room: RoomState,
    kind: GameKind,
    body: Buffer,
    player: MikooPlayerContext,
  ) {
    const req = decodeBetReq(body, kind === 'multi' || kind === 'crash' ? 'multi' : 'default');
    const amount = Math.max(1, Math.floor(Number(req.money) || 0));
    this.logger.log(
      `GAME_PROBE BET_DECODE game=${gameSlug} phase=${room.phase} amount=${amount} area=${req.areaId} user=${player.userId} bal=${player.balance}`,
    );
    const MAX_BET = 10_000;
    if (amount > MAX_BET) {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1, iconId: req.areaId,
        }));
      } else if (gameSlug === 'lucky77') {
        this.sendLucky77BetRsp(ws, {
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: amount },
        });
      } else if (gameSlug === '7updown') {
        this.send(ws, '.game.BetRsp', encode7UpBetRsp({
          code: 1, desc: 'Bet too high', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else if (gameSlug === 'luck-car') {
        this.send(ws, '.game.BetRsp', encodeLuckCarBetRsp({
          code: 1, desc: 'Bet too high', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 1, desc: 'Bet too high', selfMoney: player.balance, tipType: 1,
        }));
      }
      return player;
    }
    if (amount <= 0) {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Invalid bet', userMoney: player.balance, tipType: 1, iconId: req.areaId,
        }));
      } else if (gameSlug === 'lucky77') {
        this.sendLucky77BetRsp(ws, {
          code: 1, desc: 'Invalid bet', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: 0 },
        });
      } else if (gameSlug === '7updown') {
        this.send(ws, '.game.BetRsp', encode7UpBetRsp({
          code: 1, desc: 'Invalid bet', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else if (gameSlug === 'luck-car') {
        this.send(ws, '.game.BetRsp', encodeLuckCarBetRsp({
          code: 1, desc: 'Invalid bet', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 1, desc: 'Invalid bet', selfMoney: player.balance, tipType: 1,
        }));
      }
      return player;
    }

    if (kind === 'spin') {
      return this.handleSpinBet(ws, gameSlug, body, player, 'olympians');
    }

    if (room.phase !== 'betting') {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Not betting phase', userMoney: player.balance, tipType: 1, iconId: req.areaId,
        }));
      } else if (gameSlug === 'lucky77') {
        this.sendLucky77BetRsp(ws, {
          code: 1, desc: 'Not betting phase', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: amount },
        });
      } else if (gameSlug === '7updown') {
        this.send(ws, '.game.BetRsp', encode7UpBetRsp({
          code: 1, desc: 'Not betting phase', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else if (gameSlug === 'luck-car') {
        this.send(ws, '.game.BetRsp', encodeLuckCarBetRsp({
          code: 1, desc: 'Not betting phase', selfMoney: player.balance, tipType: 1, areaId: req.areaId,
        }));
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 1, desc: 'Not betting phase', selfMoney: player.balance, tipType: 1,
        }));
      }
      return player;
    }

    try {
      const bal = await this.debit(player.userId, amount, gameSlug, player.publicId);
      player.balance = bal;
      const key = `${player.publicId}:${req.areaId || 0}`;
      const existing = room.bets.get(key);
      if (existing) {
        existing.amount += amount;
        existing.ws = ws;
        existing.sessionId = player.sessionId;
        existing.displayName = player.displayName || existing.displayName;
        existing.avatarUrl = player.avatarUrl || existing.avatarUrl;
      } else {
        room.bets.set(key, {
          userId: player.userId,
          playerId: player.publicId,
          sessionId: player.sessionId,
          amount,
          areaId: req.areaId || 0,
          cashedOut: false,
          payout: 0,
          ws,
          displayName: player.displayName,
          avatarUrl: player.avatarUrl,
        });
      }
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 0, desc: 'OK', userMoney: bal, tipType: 0, iconId: req.areaId,
        }));
      } else if (gameSlug === 'lucky77') {
        const betTotal = this.lucky77AreaBets(room, player.publicId);
        const roomBetTotal = this.lucky77AreaBets(room);
        const curBet = { icon: req.areaId || 0, money: amount };
        this.sendLucky77BetRsp(ws, {
          code: 0,
          desc: 'OK',
          userMoney: bal,
          tipType: 0,
          betTotal,
          curBet,
        });
        // Chip-fly FX for everyone (client skips animation when uid === self).
        this.broadcast(
          room,
          '.game.OtherPlayerBetBroadcast',
          encodeLucky77OtherPlayerBetBroadcast({
            betTotal: roomBetTotal,
            curBet,
            uid: player.publicId,
          }),
        );
      } else if (gameSlug === '7updown') {
        this.send(ws, '.game.BetRsp', encode7UpBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, betMoney: amount, areaId: req.areaId, tipType: 0,
        }));
        this.send(ws, '.game.BetRes', encode7UpBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, betMoney: amount, areaId: req.areaId, tipType: 0,
        }));
      } else if (gameSlug === 'luck-car') {
        let myBetAll = 0;
        for (const b of room.bets.values()) {
          if (b.playerId === player.publicId) myBetAll += b.amount;
        }
        const rsp = encodeLuckCarBetRsp({
          code: 0,
          desc: 'OK',
          selfMoney: bal,
          tipType: 0,
          areaId: req.areaId,
          betMoney: amount,
          myBetAll,
        });
        this.send(ws, '.game.BetRsp', rsp);
        this.send(ws, '.game.BetRes', rsp);
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, tipType: 0,
        }));
      }
      this.logger.log(
        `GAME_PROBE BET_OK game=${gameSlug} user=${player.userId} area=${req.areaId} bet=${amount} bal=${bal}`,
      );
      void this.economy.onBetWin({
        sessionId: player.sessionId,
        userId: player.userId,
        gameId: gameSlug,
        betCoins: amount,
        winCoins: 0,
        balanceAfter: bal,
      });
    } catch {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Insufficient balance', userMoney: player.balance, tipType: 2, iconId: req.areaId,
        }));
      } else if (gameSlug === 'lucky77') {
        this.sendLucky77BetRsp(ws, {
          code: 1, desc: 'Insufficient balance', userMoney: player.balance, tipType: 2,
          curBet: { icon: req.areaId || 0, money: amount },
        });
      } else if (gameSlug === '7updown') {
        this.send(ws, '.game.BetRsp', encode7UpBetRsp({
          code: 1, desc: 'Insufficient balance', selfMoney: player.balance, tipType: 2, areaId: req.areaId,
        }));
      } else if (gameSlug === 'luck-car') {
        this.send(ws, '.game.BetRsp', encodeLuckCarBetRsp({
          code: 1, desc: 'Insufficient balance', selfMoney: player.balance, tipType: 2, areaId: req.areaId,
        }));
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 1, desc: 'Insufficient balance', selfMoney: player.balance, tipType: 2,
        }));
      }
    }
    return player;
  }

  private async handleCashout(
    ws: WebSocket,
    gameSlug: string,
    room: RoomState,
    player: MikooPlayerContext,
  ) {
    const bet = [...room.bets.values()].find((b) => b.playerId === player.publicId && !b.cashedOut);
    if (!bet || room.phase !== 'flying') {
      this.send(ws, '.game.CashoutRsp', encodeCashoutRsp({
        code: 1, desc: 'Cannot cashout', ratio: room.ratio, winMoney: 0, selfMoney: player.balance,
      }));
      return player;
    }
    bet.cashedOut = true;
    const win = Math.floor(bet.amount * room.ratio);
    bet.payout = win;
    const bal = await this.credit(player.userId, win, gameSlug, room.ratio, bet.amount);
    player.balance = bal;
    this.send(ws, '.game.CashoutRsp', encodeCashoutRsp({
      code: 0, desc: 'OK', ratio: room.ratio, winMoney: win, selfMoney: bal,
    }));
    void this.economy.onBetWin({
      sessionId: player.sessionId,
      userId: player.userId,
      gameId: gameSlug,
      betCoins: 0,
      winCoins: win,
      balanceAfter: bal,
    });
    return player;
  }

  private async debit(userId: string, amount: number, gameId: string, playerId: number) {
    return this.dataSource.transaction(async (manager) => {
      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) wallet = await manager.save(manager.create(Wallet, { userId }));
      const balance = Number(wallet.coins || 0);
      if (balance < amount) throw new Error('INSUFFICIENT');
      wallet.coins = balance - amount;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.EXCHANGE,
          currency: CurrencyType.COINS,
          amount: -amount,
          balanceAfter: Number(wallet.coins),
          referenceType: 'mikoo_game_bet',
          referenceId: randomUUID(),
          description: `Mikoo ${gameId} bet`,
          metadata: { gameId, playerId, amount },
        }),
      );
      return Number(wallet.coins);
    });
  }

  private async credit(
    userId: string,
    amount: number,
    gameId: string,
    ratio: number,
    bet: number,
  ) {
    if (amount <= 0) {
      const w = await this.wallets.findOne({ where: { userId } });
      return Number(w?.coins || 0);
    }
    return this.dataSource.transaction(async (manager) => {
      let wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) wallet = await manager.save(manager.create(Wallet, { userId }));
      wallet.coins = Number(wallet.coins || 0) + amount;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.LUCKY_REWARD,
          currency: CurrencyType.COINS,
          amount,
          balanceAfter: Number(wallet.coins),
          referenceType: 'mikoo_game_win',
          referenceId: randomUUID(),
          description: `Mikoo ${gameId} win`,
          metadata: { gameId, ratio, bet, amount },
        }),
      );
      return Number(wallet.coins);
    });
  }
}
