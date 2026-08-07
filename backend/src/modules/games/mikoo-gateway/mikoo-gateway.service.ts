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
  encode7UpGetRankDataRes,
  encode7UpGetUserRecordRes,
  encode7UpUpdateBetPoolBroadcast,
  encode7UpUpdatePlayerNumBroadcast,
  encodeBetRsp,
  encodeCashoutRsp,
  encodeCashoutConfRsp,
  encodeCleopatraBetRes,
  encodeCrashUpdateBetPoolBroadcast,
  encodeSomeoneCashoutBroadcast,
  encodeDoSlotsRsp,
  encodeEmpty,
  encodeFortuneGameCfgRes,
  encodeFortuneGemsRes,
  encodeGameOverBroadcast,
  encodeGameOverRsp,
  encodeGetUserDataFor,
  encodeGreedyBetRes,
  encodeGreedyGetRankDataRes,
  encodeGreedyGetUserRecordRes,
  encodeGreedyOtherPlayerBetBroadcast,
  encodeGreedyResultBroadcast,
  encodeGreedyStartBetBroadcast,
  encodeGreedyTableInfoRes,
  encodeBountyFootballBetRes,
  encodeBountyFootballGetRankDataRes,
  encodeBountyFootballOtherPlayerBetBroadcast,
  encodeBountyFootballResultBroadcast,
  encodeBountyFootballStartBetBroadcast,
  encodeBountyFootballTableInfoRes,
  BOUNTY_FOOTBALL_RATIOS,
  CAMEL_RACING_RATIOS,
  encodeCamelBetRsp,
  encodeCamelGetRankDataRes,
  encodeCamelGetUserRecordRes,
  encodeCamelPlayerBetBroadcast,
  encodeCamelPlayerNumsBroadcast,
  encodeCamelResultBroadcast,
  encodeCamelStartBetBroadcast,
  encodeCamelTableInfoRes,
  encodeHeartBeatRsp,
  encodeLineSlotsGameCfgRsp,
  encodeLineSlotsGetRankDataRes,
  encodeLineSlotsGetUserRecordRes,
  encodeLoginRes,
  encodeLuckCarBetRsp,
  encodeLuckCarGameOverRsp,
  encodeLuckCarGetRankDataRes,
  encodeLuckCarGetUserRecordRes,
  encodeCrashGetUserRecordRes,
  encodeLuckCarStartBetBroadcast,
  encodeLuckCarTableInfo,
  encodeLuckCarUpdateBetPoolBroadcast,
  encodeLuckCarUpdatePlayerNumBroadcast,
  encodeLucky77BetRsp,
  encodeLucky77GetPrizeDrawRecordRes,
  encodeLucky77GetRankDataRes,
  encodeLucky77GetUserRecordRes,
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
import { biasedCrashAt, multiAreaMultipliers, pickWeightedAreaId, pickWeightedAreaIndex, spinPayout, CRASH_CASHOUT_RAKE } from './mikoo-house-edge.util';
import { GREEDY_BOX_RATIOS, LUCK_CAR_RATIOS } from './mikoo-game-economy';
import { clampBetAmount, clampGamePayout, GAME_PAYOUT } from '../game-payout-guard';

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
  /** When over phase ends (crash settle hold). */
  overEnds: number;
  bets: Map<string, RoomBet>; // key = playerId:areaId
  clients: Set<WebSocket>;
  /** Last known wallet balance per WS (keeps spectator GameOver selfMoney honest). */
  balances: Map<WebSocket, number>;
  timers: NodeJS.Timeout[];
  /** Recent winning areas for 7updown history strip (1=down,2=seven,3=up). */
  history: number[];
  /** BountyFootball wheel stop (1..16). Never 0 — client throws and spin dies. */
  lastTurnPos?: number;
};

function gameKind(game: MikooGameDef): GameKind {
  if (game.id === 'crash') return 'crash';
  // multi-area / dice-style hash games (fortune-slot is a spin slot, not multi)
  if (['7updown', 'greedy-box', 'luck-car', 'lucky77', 'bounty-football', 'camel-racing'].includes(game.id)) {
    return 'multi';
  }
  return 'spin';
}

type Lucky77CostRecord = {
  time: number;
  curTurn: number;
  icons: number[];
  betTotal: Array<{ icon: number; cost: number; money: number }>;
};

@Injectable()
export class MikooGatewayService implements OnModuleDestroy {
  private readonly logger = new Logger(MikooGatewayService.name);
  private wss?: WebSocketServer;
  private readonly rooms = new Map<string, RoomState>();
  /** Per-user Lucky77 «My Cost» rows (newest last), kept in-memory. */
  private readonly lucky77UserCost = new Map<string, Lucky77CostRecord[]>();

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
        // Normalize PORT aliases (e.g. camelracing → camel-racing).
        const game = findMikooGame(slug);
        this.handleConnection(ws, game?.id || slug, req);
      });
    });
    this.logger.log('Mikoo game WebSocket gateway attached at /games/ws/:gameId');
  }

  private send(ws: WebSocket, name: string, body: Buffer) {
    if (ws.readyState === WebSocket.OPEN) ws.send(packFrame(name, body));
  }

  private broadcast(room: RoomState, name: string, body: Buffer, except?: WebSocket) {
    for (const ws of room.clients) {
      if (except && ws === except) continue;
      this.send(ws, name, body);
    }
  }

  /** Aggregate Lucky77 area totals (icon 0/1/2). Optional filter by playerId. */
  /** Aggregate BountyFootball icon bets for TableInfo / BetRes. */
  private bountyFootballBets(room: RoomState, playerId?: number) {
    const areaMap = new Map<number, number>();
    const myMap = new Map<number, number>();
    let totalBet = 0;
    let myTotalBet = 0;
    for (const b of room.bets.values()) {
      const icon = Math.max(1, Math.min(10, b.areaId || 1));
      areaMap.set(icon, (areaMap.get(icon) || 0) + b.amount);
      totalBet += b.amount;
      if (playerId != null && b.playerId === playerId) {
        myMap.set(icon, (myMap.get(icon) || 0) + b.amount);
        myTotalBet += b.amount;
      }
    }
    const allAreaBets = [...areaMap.entries()].map(([iconId, money]) => ({ iconId, money }));
    const myBets = [...myMap.entries()].map(([iconId, money]) => ({ iconId, money }));
    return { allAreaBets, myBets, totalBet, myTotalBet };
  }

  private shuffleInts(from: number, to: number): number[] {
    const a: number[] = [];
    for (let i = from; i <= to; i++) a.push(i);
    for (let i = a.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      const t = a[i]!;
      a[i] = a[j]!;
      a[j] = t;
    }
    return a;
  }

  private camelMyBets(room: RoomState, playerId?: number): Array<{ icon: number; money: number }> {
    const map = new Map<number, number>();
    for (const b of room.bets.values()) {
      if (playerId != null && b.playerId !== playerId) continue;
      const icon = Math.max(1, Math.min(8, b.areaId || 1));
      map.set(icon, (map.get(icon) || 0) + b.amount);
    }
    return [...map.entries()].map(([icon, money]) => ({ icon, money }));
  }

  private camelPool(room: RoomState): { arena: number[]; money: number[]; totalBet: number } {
    const map = new Map<number, number>();
    let totalBet = 0;
    for (const b of room.bets.values()) {
      const icon = Math.max(1, Math.min(8, b.areaId || 1));
      map.set(icon, (map.get(icon) || 0) + b.amount);
      totalBet += b.amount;
    }
    const arena = [...map.keys()];
    const money = arena.map((i) => map.get(i) || 0);
    return { arena, money, totalBet };
  }

  /**
   * Wheel has 16 lights (ALLUNITNUM); bet areas are 1–10.
   * Positions 1–10 map to areas 1–10; 11–16 repeat low areas 1–6 (lower odds).
   * Client getTurnTimes requires both start/stop in [1,16] or the spin crashes.
   */
  private pickBountyTurnPos(winArea: number, lastPos?: number): number {
    const area = Math.max(1, Math.min(10, winArea | 0));
    const candidates =
      area <= 6 ? [area, area + 10] : [area];
    let best = candidates[0];
    let bestDist = -1;
    const from = Math.max(1, Math.min(16, lastPos || 1));
    for (const pos of candidates) {
      let d = 0;
      let cur = from;
      while (cur !== pos) {
        d++;
        cur = cur >= 16 ? 1 : cur + 1;
      }
      // Prefer a full wrap that plays longer: + 5*16 is forced by getTurnTimes
      if (d > bestDist) {
        bestDist = d;
        best = pos;
      }
    }
    return best;
  }

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

  /** 7updown areas 1=down, 2=seven, 3=up. */
  private sevenUpAreaBets(
    room: RoomState,
    onlyPlayerId?: number,
  ): Array<{ id: number; totalBet: number; myBet: number; ratio: number }> {
    const ratios: Record<number, number> = { 1: 2, 2: 5, 3: 2 };
    const totals = new Map<number, number>();
    const mine = new Map<number, number>();
    for (const b of room.bets.values()) {
      totals.set(b.areaId, (totals.get(b.areaId) || 0) + b.amount);
      if (onlyPlayerId != null && b.playerId === onlyPlayerId) {
        mine.set(b.areaId, (mine.get(b.areaId) || 0) + b.amount);
      }
    }
    return [1, 2, 3].map((id) => ({
      id,
      totalBet: totals.get(id) || 0,
      myBet: mine.get(id) || 0,
      ratio: ratios[id] || 2,
    }));
  }

  /** Pool + chip-fly for everyone except the better (they already animated from BetRsp). */
  private sevenUpPoolBroadcast(room: RoomState, exceptWs?: WebSocket) {
    const areas = this.sevenUpAreaBets(room);
    const totalBet = areas.reduce((s, a) => s + a.totalBet, 0);
    this.broadcast(
      room,
      '.game.UpdateBetPoolBroadcast',
      encode7UpUpdateBetPoolBroadcast({
        totalBet,
        betInfo: areas.map((a) => ({ id: a.id, totalBet: a.totalBet })),
      }),
      exceptWs,
    );
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
        betEnds:
          Date.now() +
          (gameId === 'lucky77' ||
          gameId === '7updown' ||
          gameId === 'crash' ||
          gameId === 'greedy-box' ||
          gameId === 'luck-car' ||
          gameId === 'bounty-football' ||
          gameId === 'camel-racing'
            ? 18000
            : 10000),
        overEnds: 0,
        bets: new Map(),
        clients: new Set(),
        balances: new Map(),
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
    } else if (gameId === 'bounty-football') {
      this.broadcast(
        room,
        '.game.StartBetBroadcast',
        encodeBountyFootballStartBetBroadcast(1, secs, room.round),
      );
    } else if (gameId === 'camel-racing') {
      this.broadcast(room, '.game.StartBetBroadcast', encodeCamelStartBetBroadcast(1, secs));
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
    room.overEnds = 0;
    this.broadcast(room, '.game.StartFlyBroadcast', encodeStartFlyBroadcast());
    this.tickFly(gameId);
  }

  private tickFly(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room || room.phase !== 'flying') return;
    const elapsed = Math.max(0, Date.now() - room.flyStart);
    // Match Crash client curve: 1 + (tSec / 10)^1.4 (BASENUM=10, INDEXNUM=1.4).
    const tSec = elapsed / 1000;
    room.ratio = Math.max(1, Math.round((1 + Math.pow(tSec / 10, 1.4)) * 100) / 100);
    // Client UpdateRatioBroadcast expects centi (150 = 1.50x).
    this.broadcast(
      room,
      '.game.UpdateRatioBroadcast',
      encodeUpdateRatioBroadcast(room.ratio * 100, elapsed),
    );
    if (room.ratio >= room.crashAt) {
      room.phase = 'over';
      room.overEnds = Date.now() + 6000;
      room.history = [...room.history, room.crashAt].slice(-12);
      this.broadcast(
        room,
        '.game.GameOverBroadcast',
        encodeGameOverBroadcast(room.crashAt * 100, 6),
      );
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
          balanceAfter: room.balances.get(bet.ws!) ?? 0,
        });
      }
      room.timers.push(setTimeout(() => this.resetRound(gameId), 6000));
      return;
    }
    room.timers.push(setTimeout(() => this.tickFly(gameId), 180));
  }

  private async settleMulti(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    room.phase = 'over';
    // House-safe land: inverse-weight multi-area (RTP≈75%). Fair dice only for pure 7updown after.
    const d1 = 1 + Math.floor(Math.random() * 6);
    const d2 = 1 + Math.floor(Math.random() * 6);
    const total = d1 + d2;
    // Lucky77: pick icon 0/1/2 by inverse ratio weight, then a matching board position.
    const lucky77Icon = pickWeightedAreaIndex(LUCKY77_RATIOS, 0.75);
    const lucky77Positions = LUCKY77_ROUNDNO.map((t, i) => ({ pos: i + 1, type: t }))
      .filter((x) => x.type === lucky77Icon + 1);
    const lucky77WinPos =
      lucky77Positions.length > 0
        ? lucky77Positions[Math.floor(Math.random() * lucky77Positions.length)]!.pos
        : 1 + Math.floor(Math.random() * 9);
    const lucky77WinIcon = lucky77Icon;
    const winArea =
      gameId === 'greedy-box'
        ? pickWeightedAreaId(GREEDY_BOX_RATIOS, 0.75)
        : gameId === 'luck-car'
          ? pickWeightedAreaId(LUCK_CAR_RATIOS, 0.75)
          : gameId === 'bounty-football'
            ? pickWeightedAreaId(BOUNTY_FOOTBALL_RATIOS, 0.75)
            : gameId === 'camel-racing'
              ? pickWeightedAreaId(CAMEL_RACING_RATIOS, 0.75)
            : gameId === 'lucky77'
              ? lucky77WinIcon
              : // 7updown: use actual dice total (classic feel) + slightly reduced mults.
                total < 7
                ? 1
                : total === 7
                  ? 2
                  : 3;
    if (gameId === '7updown') {
      room.history = [...room.history, winArea].slice(-12);
    } else if (gameId === 'lucky77') {
      room.history = [...room.history, lucky77WinPos].slice(-12);
    } else if (gameId === 'bounty-football') {
      room.history = [...room.history, winArea].slice(-12);
    } else if (gameId === 'camel-racing') {
      room.history = [...room.history, winArea].slice(-12);
    }
    const multipliers = multiAreaMultipliers();
    const greedyRatio: Record<number, number> = {};
    GREEDY_BOX_RATIOS.forEach((r, i) => {
      greedyRatio[i + 1] = r;
    });
    const luckCarRatio: Record<number, number> = {};
    // Real multipliers (not milli) for settle — must match TableInfo car odds.
    LUCK_CAR_RATIOS.forEach((r, i) => {
      luckCarRatio[i + 1] = r;
    });
    const bountyRatio: Record<number, number> = {};
    BOUNTY_FOOTBALL_RATIOS.forEach((r, i) => {
      bountyRatio[i + 1] = r;
    });
    const camelRatio: Record<number, number> = {};
    CAMEL_RACING_RATIOS.forEach((r, i) => {
      camelRatio[i + 1] = r;
    });

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
            : gameId === 'bounty-football'
              ? bet.areaId === winArea
                ? bountyRatio[winArea] || 2
                : 0
            : gameId === 'camel-racing'
              ? bet.areaId === winArea
                ? camelRatio[winArea] || 2
                : 0
            : gameId === 'lucky77'
              ? bet.areaId === winArea
                ? LUCKY77_RATIOS[winArea] || 2
                : 0
              : bet.areaId === winArea
                ? multipliers[winArea] || 2
                : 0;
      const win = clampGamePayout({
        bet: bet.amount,
        win: Math.floor(bet.amount * mult),
      }).win;

      if (gameId === '7updown' || gameId === 'lucky77' || gameId === 'bounty-football' || gameId === 'camel-racing') {
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

      // Per-user round cap when settle is per-bet (greedy / luck-car lines).
      const settledWin = clampGamePayout({ bet: bet.amount, win }).win;

      let bal = 0;
      try {
        if (settledWin > 0) bal = await this.credit(bet.userId, settledWin, gameId, mult, bet.amount);
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
              winMoney: settledWin,
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
              winMoney: settledWin,
              selfMoney: bal,
              myBets,
            }),
          );
        }
      }
      if (settledWin > 0 && bet.sessionId) {
        void this.economy.onBetWin({
          sessionId: bet.sessionId,
          userId: bet.userId,
          gameId: gameId,
          betCoins: 0,
          winCoins: settledWin,
          balanceAfter: bal,
        });
      } else if (settledWin === 0 && bet.sessionId && bet.amount > 0) {
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

    // Round total cap (bounty/7up/lucky77 aggregate) so many chip stacks cannot stack past abs max.
    for (const row of sevenByUser.values()) {
      const capped = clampGamePayout({ bet: row.betTotal, win: row.win });
      if (capped.capped) {
        this.logger.warn(
          `PAYOUT_CAP multi-agg game=${gameId} user=${row.userId} raw=${capped.rawWin} -> ${capped.win}`,
        );
      }
      row.win = capped.win;
    }

    if (gameId === '7updown') {
      const areasForRoom = this.sevenUpAreaBets(room);
      const rank = [...sevenByUser.values()]
        .filter((r) => r.win > 0)
        .sort((a, b) => b.win - a.win)
        .slice(0, 3)
        .map((r) => ({
          name: r.displayName || 'Player',
          head: r.avatarUrl || '',
          winMoney: r.win,
        }));
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
          const myAreas = this.sevenUpAreaBets(room, row.playerId);
          this.send(
            row.ws,
            '.game.GameOverRsp',
            encodeGameOverRsp({
              time: 6,
              nums: [d1, d2],
              winMoney: row.win,
              selfMoney: bal,
              betInfo: myAreas,
              rank,
            }),
          );
          notifiedWs.add(row.ws);
          room.balances.set(row.ws, bal);
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
          encodeGameOverRsp({
            time: 6,
            nums: [d1, d2],
            winMoney: 0,
            selfMoney: room.balances.get(ws) ?? 0,
            betInfo: areasForRoom,
            rank,
          }),
        );
      }
      this.broadcast(
        room,
        '.game.UpdatePlayerNumBroadcast',
        encode7UpUpdatePlayerNumBroadcast(room.clients.size),
      );
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
        // «My Cost» panel rows — stakes by icon for this round.
        const costByIcon = new Map<number, { cost: number; money: number }>();
        for (const bet of room.bets.values()) {
          if (bet.userId !== row.userId) continue;
          const icon = bet.areaId | 0;
          const cur = costByIcon.get(icon) || { cost: 0, money: 0 };
          cur.cost += bet.amount;
          if (icon === winArea) cur.money += Math.floor(bet.amount * (LUCKY77_RATIOS[winArea] || 2));
          costByIcon.set(icon, cur);
        }
        if (costByIcon.size > 0) {
          const list = this.lucky77UserCost.get(row.userId) ?? [];
          list.push({
            time: Math.floor(Date.now() / 1000),
            curTurn: room.round,
            icons: [lucky77WinPos],
            betTotal: [...costByIcon.entries()].map(([icon, v]) => ({
              icon,
              cost: v.cost,
              money: v.money,
            })),
          });
          while (list.length > 50) list.shift();
          this.lucky77UserCost.set(row.userId, list);
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
    } else if (gameId === 'bounty-football') {
      // Must land on icon 1–16. Client getTurnTimes throws if 0 → no spin.
      const turnPos = this.pickBountyTurnPos(winArea, room.lastTurnPos);
      room.lastTurnPos = turnPos;
      const notifiedWs = new Set<WebSocket>();
      const betRank = [...sevenByUser.values()]
        .filter((r) => r.win > 0 || r.betTotal > 0)
        .sort((a, b) => b.win - a.win || b.betTotal - a.betTotal)
        .slice(0, 3)
        .map((r) => ({
          name: r.displayName || 'Player',
          head: r.avatarUrl || '',
          totalBet: r.betTotal,
          totalGain: r.win,
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
            encodeBountyFootballResultBroadcast({
              state: 0,
              // Client schedules: stopBet 1.2 + ready 2 + spin 7 + boom 0.5 + flicker 1.5 ≈ 12s
              betTime: 12,
              betRank,
              myReward: {
                name: row.displayName || 'Player',
                head: row.avatarUrl || '',
                totalBet: row.betTotal,
                totalGain: row.win,
              },
              curTurnRewardIcon: winArea,
              turnPos,
              curTurn: room.round,
              userMoney: bal,
              todayWin: row.win,
              rewardHistory: room.history.length ? room.history : [winArea],
            }),
          );
          notifiedWs.add(row.ws);
          room.balances.set(row.ws, bal);
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
          encodeBountyFootballResultBroadcast({
            state: 0,
            betTime: 12,
            betRank,
            curTurnRewardIcon: winArea,
            turnPos,
            curTurn: room.round,
            userMoney: room.balances.get(ws) ?? 0,
            todayWin: 0,
            rewardHistory: room.history.length ? room.history : [winArea],
          }),
        );
      }
    } else if (gameId === 'camel-racing') {
      const animalRandom = this.shuffleInts(1, 8);
      const posRandom = this.shuffleInts(1, 8);
      const notifiedWs = new Set<WebSocket>();
      const betRank = [...sevenByUser.values()]
        .filter((r) => r.win > 0 || r.betTotal > 0)
        .sort((a, b) => b.win - a.win || b.betTotal - a.betTotal)
        .slice(0, 3)
        .map((r) => ({
          name: r.displayName || 'Player',
          head: r.avatarUrl || '',
          winMoney: r.win,
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
            encodeCamelResultBroadcast({
              state: 0,
              betTime: 8,
              totalBet: row.betTotal,
              totalGain: row.win,
              curBingoIcon: winArea,
              currGroupId: 0,
              curTurn: room.round,
              userMoney: bal,
              todayWin: row.win,
              playerStatus: 0,
              betRank,
              animalRandom,
              posRandom,
            }),
          );
          notifiedWs.add(row.ws);
          room.balances.set(row.ws, bal);
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
          encodeCamelResultBroadcast({
            state: 0,
            betTime: 8,
            totalBet: 0,
            totalGain: 0,
            curBingoIcon: winArea,
            currGroupId: 0,
            curTurn: room.round,
            userMoney: room.balances.get(ws) ?? 0,
            todayWin: 0,
            playerStatus: 0,
            betRank,
            animalRandom,
            posRandom,
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
            selfMoney: room.balances.get(ws) ?? 0,
            myBets: new Array(8).fill(0),
          }),
        );
      }
    }

    room.timers.push(
      setTimeout(
        () => this.resetRound(gameId),
        // Bounty spin timeline is ~12s before result dlg + dlg hold; StartBet
        // calls client reset() and would kill mid-spin if we reopen too early.
        gameId === 'bounty-football'
          ? 18000
          : gameId === 'camel-racing'
            ? 10000
          : gameId === 'lucky77' || gameId === '7updown'
            ? 8500
            : gameId === 'luck-car'
              ? 9000
              : 4000,
      ),
    );
  }

  private resetRound(gameId: string) {
    const room = this.rooms.get(gameId);
    if (!room) return;
    room.phase = 'betting';
    room.round += 1;
    room.crashAt = this.randomCrash();
    room.ratio = 1;
    room.overEnds = 0;
    // Lucky77 / 7updown / crash: longer bet window so chip select is usable.
    room.betEnds =
      Date.now() +
      (gameId === 'lucky77' ||
      gameId === '7updown' ||
      gameId === 'crash' ||
      gameId === 'greedy-box' ||
      gameId === 'luck-car' ||
      gameId === 'bounty-football' ||
      gameId === 'camel-racing'
        ? 18000
        : 10000);
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
      room.betEnds =
        Date.now() +
        (gameSlug === 'lucky77' ||
        gameSlug === '7updown' ||
        gameSlug === 'crash' ||
        gameSlug === 'greedy-box' ||
        gameSlug === 'luck-car' ||
        gameSlug === 'bounty-football' ||
        gameSlug === 'camel-racing'
          ? 18000
          : 10000);
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

    const onGone = () => {
      room.clients.delete(ws);
      room.balances.delete(ws);
      if (gameSlug === '7updown' && room.clients.size > 0) {
        this.broadcast(
          room,
          '.game.UpdatePlayerNumBroadcast',
          encode7UpUpdatePlayerNumBroadcast(room.clients.size),
        );
      }
    };
    ws.on('close', onGone);
    ws.on('error', onGone);
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
      room.phase === 'betting'
        ? Math.max(1, Math.ceil((room.betEnds - Date.now()) / 1000))
        : room.phase === 'over' && gameSlug === 'crash'
          ? Math.max(1, Math.ceil((room.overEnds - Date.now()) / 1000))
          : 0;
    const state =
      gameSlug === 'luck-car'
        ? room.phase === 'betting'
          ? 1
          : 2 // luck-car GameState: betting=1, over=2 only
        : room.phase === 'betting'
          ? 1
          : room.phase === 'flying'
            ? 2
            : 3;
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
    if (gameSlug === 'bounty-football') {
      const { allAreaBets, myBets, totalBet, myTotalBet } = this.bountyFootballBets(
        room,
        player?.publicId,
      );
      // Client GAMESTATUS: BETTING=1, SETTLEMENT=0.
      // Never show SETTLEMENT table state during active play — that would open
      // gameResult dlg immediately without the wheel spin (see _onSetTableStatu).
      const bountyState = room.phase === 'betting' ? 1 : 0;
      // turnPos must be 1..16; turnPos=0 makes getTurnTimes throw and kills spin.
      const turnPos = Math.max(1, Math.min(16, room.lastTurnPos || 1));
      const body = encodeBountyFootballTableInfoRes({
        state: bountyState,
        timeLeft: bountyState === 1 ? timeLeft : 8,
        curTurn: room.round,
        history: room.history,
        allAreaBets,
        myBets,
        totalBet,
        myTotalBet,
        turnPos,
        curTurnRewardIcon:
          bountyState === 0 && room.history.length
            ? room.history[room.history.length - 1]
            : 0,
      });
      this.send(ws, '.game.TableInfoRes', body);
      this.send(ws, '.game.TableInfo', body);
      if (room.phase === 'betting' && timeLeft > 0) {
        this.send(
          ws,
          '.game.StartBetBroadcast',
          encodeBountyFootballStartBetBroadcast(1, timeLeft, room.round),
        );
      }
      return;
    }
    if (gameSlug === 'camel-racing') {
      const camelState = room.phase === 'betting' ? 1 : 0;
      let totalBet = 0;
      for (const b of room.bets.values()) totalBet += b.amount;
      const body = encodeCamelTableInfoRes({
        state: camelState,
        timeLeft: camelState === 1 ? timeLeft : 8,
        totalPlayerNum: room.clients.size,
        playerStatus: 0,
        camelSpeed: 80,
        curTurn: room.round,
        currGroupId: 0,
        curBingoIcon:
          camelState === 0 && room.history.length
            ? room.history[room.history.length - 1]
            : 0,
        totalBet,
        totalGain: 0,
        history: room.history.length ? room.history : [1, 3, 5, 2, 8, 4],
        myBets: player ? this.camelMyBets(room, player.publicId) : [],
        ratios: [...CAMEL_RACING_RATIOS],
      });
      this.send(ws, '.game.TableInfoRes', body);
      this.send(ws, '.game.TableInfo', body);
      this.send(
        ws,
        '.game.PlayerNumsBroadcast',
        encodeCamelPlayerNumsBroadcast(player?.publicId ?? 0, player?.balance ?? 0, room.clients.size),
      );
      if (room.phase === 'betting' && timeLeft > 0) {
        this.send(ws, '.game.StartBetBroadcast', encodeCamelStartBetBroadcast(1, timeLeft));
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
      // Client only: betting=1, over=2
      const sevenState = room.phase === 'betting' ? 1 : 2;
      const areas = this.sevenUpAreaBets(room, player?.publicId);
      const totalBet = areas.reduce((s, a) => s + a.totalBet, 0);
      const body = encodeTableInfo7UpDown({
        state: sevenState,
        betTime: 18,
        playerNum: room.clients.size,
        timeLeft,
        totalBet,
        history: room.history.length ? room.history : [1, 3, 2, 1, 3, 1, 2, 3],
        betInfo: areas,
      });
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      this.send(
        ws,
        '.game.UpdatePlayerNumBroadcast',
        encode7UpUpdatePlayerNumBroadcast(room.clients.size),
      );
    } else if (gameSlug === 'luck-car') {
      let totalBet = 0;
      const myBets = new Array(8).fill(0);
      for (const b of room.bets.values()) {
        totalBet += b.amount;
        const idx = Math.max(0, Math.min(7, (b.areaId || 1) - 1));
        if (player && b.playerId === player.publicId) myBets[idx] += b.amount;
      }
      const body = encodeLuckCarTableInfo({
        state,
        playerNum: room.clients.size,
        timeLeft,
        curTurn: room.round,
        totalBet,
        myBets,
      });
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      this.send(
        ws,
        '.game.UpdatePlayerNumBroadcast',
        encodeLuckCarUpdatePlayerNumBroadcast(room.clients.size),
      );
    } else if (gameSlug === 'crash') {
      let totalBet = 0;
      let selfBet = 0;
      for (const b of room.bets.values()) {
        totalBet += b.amount;
        if (player && b.playerId === player.publicId) selfBet += b.amount;
      }
      const flyTime =
        room.phase === 'flying' ? Math.max(0, Date.now() - room.flyStart) : 0;
      // Crash states: 0 wait, 1 bet, 2 fly, 3 over
      const crashState =
        room.phase === 'betting' ? 1 : room.phase === 'flying' ? 2 : 3;
      const body = encodeTableInfoCrash({
        state: crashState,
        playerNum: room.clients.size,
        timeLeft,
        ratio: room.ratio,
        history: room.history.length ? room.history : [1.2, 2.1, 1.5, 3.4, 1.8],
        totalBet,
        selfBet,
        flyTime,
      });
      this.send(ws, '.game.TableInfo', body);
      this.send(ws, '.game.TableInfoRes', body);
      // Mid-join during flight: seed rocket line immediately (avoids null _animNode freeze).
      if (room.phase === 'flying') {
        this.send(
          ws,
          '.game.UpdateRatioBroadcast',
          encodeUpdateRatioBroadcast(room.ratio * 100, flyTime),
        );
      }
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
        // Always re-read wallet on login — HUD userMoney comes from this reply only.
        const loginBal = await this.sessions.refreshBalance(ctx);
        ctx.balance = loginBal;
        this.logger.log(
          `login OK ${gameSlug} user=${ctx.userId} publicId=${ctx.publicId} bal=${loginBal} room=${ctx.roomId || '-'}`,
        );
        room.balances.set(ws, loginBal);
        this.send(ws, '.login.LoginRes', encodeLoginRes({
          code: 0,
          desc: 'OK',
          playerId: ctx.publicId,
          tableId: `jeho-${gameSlug}`,
          name: ctx.displayName,
          head: ctx.avatarUrl,
          userMoney: loginBal,
          tipType: loginBal <= 0 ? 2 : 0,
        }));
        // Lucky77 / 7updown / crash / greedy / line-slots / luck-car HUD from GetUserData + Login.
        if (
          gameSlug === 'lucky77' ||
          gameSlug === '7updown' ||
          gameSlug === 'crash' ||
          gameSlug === 'greedy-box' ||
          gameSlug === 'line-slots' ||
          gameSlug === 'luck-car' ||
          gameSlug === 'bounty-football' ||
          gameSlug === 'camel-racing'
        ) {
          const ud = encodeGetUserDataFor(gameSlug, {
            code: 0,
            desc: 'OK',
            userMoney: loginBal,
            name: ctx.displayName,
            head: ctx.avatarUrl,
            id: ctx.publicId,
            tipType: loginBal <= 0 ? 2 : 0,
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
          } else if (gameSlug === '7updown') {
            this.send(ws, '.game.GetRankDataRes', encode7UpGetRankDataRes(mapped));
          } else if (gameSlug === 'greedy-box') {
            this.send(ws, '.game.GetRankDataRes', encodeGreedyGetRankDataRes(mapped));
          } else if (gameSlug === 'bounty-football') {
            this.send(ws, '.game.GetRankDataRes', encodeBountyFootballGetRankDataRes(mapped));
          } else if (gameSlug === 'camel-racing') {
            this.send(ws, '.game.GetRankDataRes', encodeCamelGetRankDataRes(mapped));
          } else if (gameSlug === 'line-slots') {
            this.send(ws, '.game.GetRankDataRes', encodeLineSlotsGetRankDataRes(mapped));
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
              : gameSlug === '7updown'
                ? encode7UpGetRankDataRes([])
                : gameSlug === 'greedy-box'
                  ? encodeGreedyGetRankDataRes([])
                  : gameSlug === 'bounty-football'
                    ? encodeBountyFootballGetRankDataRes([])
                  : gameSlug === 'camel-racing'
                    ? encodeCamelGetRankDataRes([])
                  : gameSlug === 'line-slots'
                    ? encodeLineSlotsGetRankDataRes([])
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
          } else if (gameSlug === 'camel-racing') {
            this.send(ws, '.game.GetUserRecordRes', encodeCamelGetUserRecordRes([]));
          } else if (gameSlug === '7updown') {
            this.send(ws, '.game.GetUserRecordRes', encode7UpGetUserRecordRes([]));
          } else if (gameSlug === 'greedy-box') {
            this.send(ws, '.game.GetUserRecordRes', encodeGreedyGetUserRecordRes());
          } else if (gameSlug === 'line-slots') {
            this.send(ws, '.game.GetUserRecordRes', encodeLineSlotsGetUserRecordRes());
          } else if (gameSlug === 'lucky77') {
            const rows = this.lucky77UserCost.get(player.userId) ?? [];
            // Newest first for the My Cost list.
            this.send(
              ws,
              '.game.GetUserRecordRes',
              encodeLucky77GetUserRecordRes([...rows].reverse().slice(0, 40)),
            );
          } else {
            this.send(ws, '.game.GetUserRecordRes', encodeCrashGetUserRecordRes([]));
          }
        } catch (err) {
          this.logger.warn(`GetUserRecordReq ${gameSlug}: ${(err as Error).message}`);
          this.send(
            ws,
            '.game.GetUserRecordRes',
            gameSlug === 'lucky77'
              ? encodeLucky77GetUserRecordRes([])
              : encodeCrashGetUserRecordRes([]),
          );
        }
        return player;
      }

      if (name === '.game.GetPrizeDrawRecordReq') {
        try {
          if (gameSlug === 'lucky77') {
            const room = this.rooms.get(gameSlug) || this.ensureRoom(gameSlug, 'multi');
            const hist =
              room.history.length > 0
                ? room.history
                : [1, 3, 5, 7, 9, 2, 4, 6];
            this.send(
              ws,
              '.game.GetPrizeDrawRecordRes',
              encodeLucky77GetPrizeDrawRecordRes(hist.slice(-40)),
            );
          } else {
            this.send(
              ws,
              '.game.GetPrizeDrawRecordRes',
              encodeLucky77GetPrizeDrawRecordRes([]),
            );
          }
        } catch (err) {
          this.logger.warn(`GetPrizeDrawRecordReq ${gameSlug}: ${(err as Error).message}`);
          this.send(
            ws,
            '.game.GetPrizeDrawRecordRes',
            encodeLucky77GetPrizeDrawRecordRes([]),
          );
        }
        return player;
      }

      if (name === '.game.refreshStatReq') {
        this.send(ws, '.game.refreshStatRes', encodeOkCodeDesc(0, 'OK'));
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

      if (name === '.game.CashoutConfReq' && gameSlug === 'crash') {
        const f = decodeFields(body);
        const type = Number(f[1] ?? 0);
        const ratio = Number(f[2] ?? 101);
        const rsp = encodeCashoutConfRsp({ code: 0, desc: 'OK', type, ratio });
        this.send(ws, '.game.CashoutConfRsp', rsp);
        this.send(ws, '.game.CashoutConfRes', rsp);
        return player;
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
    let amount = clampBetAmount(Number(req.money) || 0, GAME_PAYOUT.maxBet);
    if (amount < 1) amount = 0;
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
          code: 0, desc: 'OK', cost: amount, userMoney: bal, winMoney: win, tipType: 0, mult,
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
    let amount = clampBetAmount(Number(req.money) || 0, GAME_PAYOUT.maxBet);
    if (amount < 1) amount = 0;
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
          code: 0, desc: 'OK', money: bal, tipType: 0, winMoney: win, mult,
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
          code: 0, desc: 'OK', selfMoney: bal, winMoney: win, tipType: 0, mult,
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
    const req = decodeBetReq(body, kind === 'multi' ? 'multi' : 'default');
    const rawAmount = Math.floor(Number(req.money) || 0);
    this.logger.log(
      `GAME_PROBE BET_DECODE game=${gameSlug} phase=${room.phase} amount=${rawAmount} area=${req.areaId} user=${player.userId} bal=${player.balance}`,
    );
    // All multi boards share the same stake rail (bounty used to allow 1_000_000).
    if (rawAmount > GAME_PAYOUT.maxBet) {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1, iconId: req.areaId,
        }));
      } else if (gameSlug === 'bounty-football') {
        this.send(ws, '.game.BetRes', encodeBountyFootballBetRes({
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1,
          curBet: { iconId: req.areaId || 0, money: rawAmount },
        }));
      } else if (gameSlug === 'camel-racing') {
        this.send(ws, '.game.BetRsp', encodeCamelBetRsp({
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: rawAmount },
        }));
      } else if (gameSlug === 'lucky77') {
        this.sendLucky77BetRsp(ws, {
          code: 1, desc: 'Bet too high', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: rawAmount },
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
    const amount = clampBetAmount(rawAmount, GAME_PAYOUT.maxBet);
    if (amount <= 0) {
      if (gameSlug === 'greedy-box') {
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 1, desc: 'Invalid bet', userMoney: player.balance, tipType: 1, iconId: req.areaId,
        }));
      } else if (gameSlug === 'bounty-football') {
        this.send(ws, '.game.BetRes', encodeBountyFootballBetRes({
          code: 1, desc: 'Invalid bet', userMoney: player.balance, tipType: 1,
          curBet: { iconId: req.areaId || 0, money: 0 },
        }));
      } else if (gameSlug === 'camel-racing') {
        this.send(ws, '.game.BetRsp', encodeCamelBetRsp({
          code: 1, desc: 'Invalid bet', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: 0 },
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
      } else if (gameSlug === 'bounty-football') {
        this.send(ws, '.game.BetRes', encodeBountyFootballBetRes({
          code: 1, desc: 'Not betting phase', userMoney: player.balance, tipType: 1,
          curBet: { iconId: req.areaId || 0, money: amount },
        }));
      } else if (gameSlug === 'camel-racing') {
        this.send(ws, '.game.BetRsp', encodeCamelBetRsp({
          code: 1, desc: 'Not betting phase', userMoney: player.balance, tipType: 1,
          curBet: { icon: req.areaId || 0, money: amount },
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
      room.balances.set(ws, bal);
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
        const myAreaAmt = room.bets.get(`${player.publicId}:${req.areaId || 0}`)?.amount ?? amount;
        this.send(ws, '.game.BetRes', encodeGreedyBetRes({
          code: 0,
          desc: 'OK',
          userMoney: bal,
          tipType: 0,
          iconId: req.areaId,
          betMoney: myAreaAmt,
        }));
        this.broadcast(
          room,
          '.game.OtherPlayerBetBroadcast',
          encodeGreedyOtherPlayerBetBroadcast({
            icon: req.areaId || 0,
            money: amount,
            uid: player.publicId,
          }),
          ws,
        );
      } else if (gameSlug === 'bounty-football') {
        const { allAreaBets, myBets } = this.bountyFootballBets(room, player.publicId);
        this.send(ws, '.game.BetRes', encodeBountyFootballBetRes({
          code: 0,
          desc: 'OK',
          userMoney: bal,
          tipType: 0,
          allAreaBets,
          myBets,
          curBet: { iconId: req.areaId || 0, money: amount },
        }));
        this.broadcast(
          room,
          '.game.OtherPlayerBetBroadcast',
          encodeBountyFootballOtherPlayerBetBroadcast({
            allAreaBets,
            playerId: player.publicId,
            bet: { iconId: req.areaId || 0, money: amount },
          }),
        );
      } else if (gameSlug === 'camel-racing') {
        const myBets = this.camelMyBets(room, player.publicId);
        const pool = this.camelPool(room);
        const icon = Math.max(1, Math.min(8, req.areaId || 1));
        this.send(
          ws,
          '.game.BetRsp',
          encodeCamelBetRsp({
            code: 0,
            desc: 'OK',
            userMoney: bal,
            tipType: 0,
            betTotal: myBets,
            curBet: { icon, money: amount },
          }),
        );
        this.broadcast(
          room,
          '.game.PlayerBetBroadcast',
          encodeCamelPlayerBetBroadcast({
            arena: pool.arena,
            money: pool.money,
            myArena: myBets.map((b) => b.icon),
            myMoney: myBets.map((b) => b.money),
          }),
        );
        this.broadcast(
          room,
          '.game.PlayerNumsBroadcast',
          encodeCamelPlayerNumsBroadcast(player.publicId, bal, room.clients.size),
        );
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
        const areas = this.sevenUpAreaBets(room, player.publicId);
        const rsp = encode7UpBetRsp({
          code: 0,
          desc: 'OK',
          selfMoney: bal,
          betMoney: amount,
          areaId: req.areaId,
          tipType: 0,
          betInfo: areas,
        });
        this.send(ws, '.game.BetRsp', rsp);
        this.send(ws, '.game.BetRes', rsp);
        // Others see chip-fly via UpdateBetPool; better already flew from BetRsp.
        this.sevenUpPoolBroadcast(room, ws);
      } else if (gameSlug === 'luck-car') {
        let myBetAll = 0;
        let totalBet = 0;
        const areaTotals = new Array(8).fill(0);
        for (const b of room.bets.values()) {
          totalBet += b.amount;
          const idx = Math.max(0, Math.min(7, (b.areaId || 1) - 1));
          areaTotals[idx] += b.amount;
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
        this.broadcast(
          room,
          '.game.UpdateBetPoolBroadcast',
          encodeLuckCarUpdateBetPoolBroadcast({
            totalBet,
            playerId: player.publicId,
            bet: amount,
            betInfo: areaTotals.map((t, i) => ({
              id: i + 1,
              totalBet: t,
            })),
          }),
        );
      } else if (gameSlug === 'crash') {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, tipType: 0, betMoney: amount,
        }));
        let totalBet = 0;
        for (const b of room.bets.values()) totalBet += b.amount;
        this.broadcast(
          room,
          '.game.UpdateBetPoolBroadcast',
          encodeCrashUpdateBetPoolBroadcast({
            totalBet,
            playerId: player.publicId,
            bet: amount,
          }),
        );
      } else {
        this.send(ws, '.game.BetRsp', encodeBetRsp({
          code: 0, desc: 'OK', selfMoney: bal, tipType: 0, betMoney: amount,
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
      } else if (gameSlug === 'bounty-football') {
        this.send(ws, '.game.BetRes', encodeBountyFootballBetRes({
          code: 1, desc: 'Insufficient balance', userMoney: player.balance, tipType: 2,
          curBet: { iconId: req.areaId || 0, money: amount },
        }));
      } else if (gameSlug === 'camel-racing') {
        this.send(ws, '.game.BetRsp', encodeCamelBetRsp({
          code: 1, desc: 'Insufficient balance', userMoney: player.balance, tipType: 2,
          curBet: { icon: req.areaId || 0, money: amount },
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
        code: 1,
        desc: 'Cannot cashout',
        ratio: room.ratio * 100,
        winMoney: 0,
        selfMoney: player.balance,
      }));
      return player;
    }
    bet.cashedOut = true;
    // Bust dist already houses; rake + hard ratio/abs caps.
    const ratio = Math.min(room.ratio, GAME_PAYOUT.maxCrashRatio);
    const { win, capped } = clampGamePayout({
      bet: bet.amount,
      win: Math.floor(bet.amount * ratio * CRASH_CASHOUT_RAKE),
    });
    if (capped) {
      this.logger.warn(
        `PAYOUT_CAP crash user=${player.userId} ratio=${room.ratio} bet=${bet.amount} win=${win}`,
      );
    }
    bet.payout = win;
    const bal = await this.credit(player.userId, win, gameSlug, ratio, bet.amount);
    player.balance = bal;
    room.balances.set(ws, bal);
    // CashoutRsp.ratio is centi (client shows ratio/100).
    this.send(ws, '.game.CashoutRsp', encodeCashoutRsp({
      code: 0,
      desc: 'OK',
      ratio: room.ratio * 100,
      winMoney: win,
      selfMoney: bal,
    }));
    this.broadcast(
      room,
      '.game.SomeoneCashoutBroadcast',
      encodeSomeoneCashoutBroadcast(room.ratio * 100, player.publicId),
    );
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
    const clamped = clampGamePayout({ bet, win: amount });
    if (clamped.capped) {
      this.logger.warn(
        `PAYOUT_CAP credit game=${gameId} user=${userId} bet=${bet} raw=${clamped.rawWin} -> ${clamped.win}`,
      );
    }
    amount = clamped.win;
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
          metadata: {
            gameId,
            ratio,
            bet,
            amount,
            capped: clamped.capped,
            rawWin: clamped.rawWin,
          },
        }),
      );
      return Number(wallet.coins);
    });
  }
}
