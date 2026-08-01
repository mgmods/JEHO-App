import { Injectable, Logger } from '@nestjs/common';
import { WebSocket } from 'ws';
import { randomUUID } from 'crypto';
import { DataSource } from 'typeorm';
import { MikooSessionService, MikooPlayerContext } from './mikoo-session.service';
import { MikooEconomyNotifyService } from './mikoo-economy-notify.service';
import { spinPayout } from './mikoo-house-edge.util';
import { Wallet } from '../../../database/entities/wallet.entity';
import {
  CurrencyType,
  TransactionType,
  WalletTransaction,
} from '../../../database/entities/wallet-transaction.entity';
import { packHeartbeat, packMessage, unpackMessage } from './baishun-packer.util';
import {
  CHIP_LIST,
  SLOT_CHIP_LIST,
  SwimsuitRoute,
  decodeChangeChipReq,
  decodeClientMsg,
  decodeEnterGameReq,
  decodeSpinReq,
  encodeChangeChipRes,
  encodeChannelCfg,
  encodeClientMsg,
  encodeCoinRecordRes,
  encodeEnterGameRes,
  encodeLastSpinBoard,
  encodeHiloBetRes,
  encodeHiloDetailRes,
  encodeHiloGetConfigRes,
  encodeHiloSelectRes,
  encodeHiloSwitchCardRes,
  encodeHiloUserInfoRes,
  encodePlayerInfo,
  encodePlayerRes,
  encodeRoyalBetRes,
  encodeRoyalBetStateNotify,
  encodeRoyalDetailRes,
  encodeRoyalGetConfigRes,
  encodeRoyalMineSettlement,
  encodeRoyalEndSettlement,
  encodeRoyalHistory,
  encodeRoyalPlayStateNotify,
  encodeRoyalUserInfoRes,
  encodeSpinRes,
} from './swimsuit-pb.util';
import { decodeFields, encodeMessage, pbInt32, pbString, pbUInt64 } from './mikoo-proto.util';

type BaishunMsg = { msgId?: string; data?: Record<string, unknown> };

type BaishunProtocol = 'json' | 'packer' | 'clientmsg';

const PACKER_GAMES = new Set(['swimsuit-party']);
const CLIENTMSG_GAMES = new Set(['hilo', 'royal-battle']);

const HILO_MSG = {
  ResMessageError: 401,
  ReqHeartBeat: 402,
  ResHeadHeartBeat: 502,
  ResConnectSuccess: 500,
  ReqUserInfo: 404,
  ResUserInfo: 504,
  ReqGameConfig: 405,
  ResGameConfig: 505,
  ReqGameDetail: 406,
  ResGameDetail: 506,
  ReqBet: 407,
  ResBet: 507,
  ReqPlayerRank: 409,
  ResPlayerRank: 509,
  ReqMyRecord: 410,
  ResMyRecord: 510,
  ReqSelectResult: 411,
  ResSelectResult: 511,
  ReqSwitchCard: 412,
  ResSwitchCard: 512,
  ReqHistory: 412,
  ResHistory: 512,
  ReqRecover: 413,
  ResRecover: 513,
  ReqRecord: 413,
  ResRecord: 513,
  ReqChangeSelectChip: 414,
  ResChangeSelectChip: 514,
  ReqUpdateCode: 415,
  ResUpdateCode: 515,
  ReqUserRank: 417,
  ResUserRank: 517,
  // Royal notifies
  ResBetStateNotify: 520,
  ResStartStateNotify: 521,
  ResOverStateNotify: 522,
  ResAllPlayerGameResultNotify: 525,
  ResMineGameResultNotify: 527,
} as const;

@Injectable()
export class BaishunWsHandler {
  private readonly logger = new Logger(BaishunWsHandler.name);

  constructor(
    private readonly sessions: MikooSessionService,
    private readonly dataSource: DataSource,
    private readonly economy: MikooEconomyNotifyService,
  ) {}

  handle(ws: WebSocket, gameSlug: string, rawUrl = '') {
    const protocol = this.protocolFor(gameSlug);
    if (protocol === 'packer') {
      this.handlePacker(ws, gameSlug, rawUrl);
      return;
    }
    if (protocol === 'clientmsg') {
      this.handleClientMsg(ws, gameSlug, rawUrl);
      return;
    }
    this.handleJson(ws, gameSlug, rawUrl);
  }

  private protocolFor(gameSlug: string): BaishunProtocol {
    if (PACKER_GAMES.has(gameSlug)) return 'packer';
    if (CLIENTMSG_GAMES.has(gameSlug)) return 'clientmsg';
    return 'json';
  }

  /** Cleopatra / Slot777 / Football Plinko / Fishing — JSON {msgId,data}. */
  private handleJson(ws: WebSocket, gameSlug: string, rawUrl: string) {
    let player: MikooPlayerContext | null = null;
    let authReady: Promise<void> = Promise.resolve();
    let betAmount = SLOT_CHIP_LIST[0];
    const query = this.parseQuery(rawUrl);

    const ensurePlayer = async (
      data?: Record<string, unknown>,
    ): Promise<MikooPlayerContext | null> => {
      if (player) return player;
      const rawUid = data?.user_id ?? data?.userId ?? query.user_id ?? query.userId ?? 0;
      const uid = Number(
        rawUid === 'undefined' || rawUid === 'null' || rawUid === '' ? 0 : rawUid,
      );
      const code = String(
        data?.code ?? data?.token ?? query.code ?? query.token ?? '',
      );
      if ((Number.isFinite(uid) && uid > 0) || code) {
        player = await this.sessions.resolvePlayer(
          gameSlug,
          Number.isFinite(uid) && uid > 0 ? uid : 0,
          code,
        );
      }
      return player;
    };

    const balanceFields = (bal: number, p?: MikooPlayerContext | null) => ({
      user_id: Number(p?.publicId ?? 0),
      userId: Number(p?.publicId ?? 0),
      uid: Number(p?.publicId ?? 0),
      open_id: Number(p?.publicId ?? 0),
      openId: String(p?.publicId ?? ''),
      nickname: p?.displayName || 'Player',
      nickName: p?.displayName || 'Player',
      name: p?.displayName || 'Player',
      userName: p?.displayName || 'Player',
      avatar: p?.avatarUrl || '',
      headImg: p?.avatarUrl || '',
      head: p?.avatarUrl || '',
      balance: bal,
      coin: bal,
      gold: bal,
      money: bal,
      userMoney: bal,
    });

    authReady = (async () => {
      const connected = await ensurePlayer();
      this.replyJson(ws, 'Connect', {
        Code: connected ? 0 : 1,
        Data: { ok: !!connected, game: gameSlug },
      });
      if (connected) {
        const bal = await this.sessions.refreshBalance(connected);
        this.logger.log(
          `baishun connect ${gameSlug} user=${connected.userId} bal=${bal} room=${connected.roomId || '-'}`,
        );
        this.logger.log(
          `GAME_PROBE WS_CONNECT game=${gameSlug} user=${connected.userId} bal=${bal} room=${connected.roomId || '-'}`,
        );
        this.replyJson(ws, 'SelfInfo', {
          Code: 0,
          Data: balanceFields(bal, connected),
        });
        this.replyJson(ws, 'SelfBalance', {
          Code: 0,
          Data: { balance: bal, coin: bal, gold: bal, money: bal, userMoney: bal },
        });
        if (bal <= 0) {
          this.replyJson(ws, 'NeedRecharge', {
            Code: 2,
            ErrorTips: 'Insufficient balance',
            Data: { balance: 0, coin: 0, need_recharge: true },
          });
        }
      }
    })();

    ws.on('message', (raw) => {
      void (async () => {
        try {
          await authReady;
          const text = Buffer.isBuffer(raw)
            ? raw.toString('utf8')
            : Buffer.from(raw as ArrayBuffer).toString('utf8');
          if (!text.trim().startsWith('{')) return;
          const msg = JSON.parse(text) as BaishunMsg & { msgId?: string | number; errCode?: number };
          const msgId = String(msg.msgId ?? '');
          const data = (msg.data || {}) as Record<string, unknown>;

          if (msgId === 'Connect' || msgId === 'SelfInfo' || msgId === 'UpdateCode') {
            await ensurePlayer(data);
            const bal = player ? await this.sessions.refreshBalance(player) : 0;
            this.replyJson(ws, msgId === 'UpdateCode' ? 'UpdateCode' : 'SelfInfo', {
              Code: player ? 0 : 1,
              ErrorTips: player ? '' : 'Invalid session',
              Data: balanceFields(bal, player),
            });
            if (msgId === 'Connect') {
              this.replyJson(ws, 'Connect', { Code: player ? 0 : 1, Data: { ok: !!player } });
              if (player) {
                this.replyJson(ws, 'SelfBalance', {
                  Code: 0,
                  Data: { balance: bal, coin: bal, gold: bal, money: bal, userMoney: bal },
                });
              }
            }
            return;
          }

          if (msgId === 'SelfBalance' || msgId === 'ReqBalance' || msgId === 'GetBalance') {
            await ensurePlayer(data);
            if (!player) {
              // Never Code:0 with coin:0 — client treats that as real empty wallet.
              this.replyJson(ws, 'SelfBalance', {
                Code: 1,
                ErrorTips: 'Not logged in',
                Data: { balance: 0, coin: 0 },
              });
              return;
            }
            const bal = await this.sessions.refreshBalance(player);
            this.replyJson(ws, 'SelfBalance', {
              Code: 0,
              Data: { balance: bal, coin: bal, gold: bal, money: bal, userMoney: bal },
            });
            return;
          }

          if (msgId === 'GameConfig' || msgId === 'GameDetail') {
            const bal = player ? await this.sessions.refreshBalance(player) : 0;
            const coinIcon =
              'https://api.adnova.bbs.tr/assets/pack/ic_pass_card_coin.png';
            const isPlinko = gameSlug === 'football-plinko';
            const isCleopatra =
              gameSlug === 'cleopatra-slot' || gameSlug === 'cleopatra-slots';
            const puzzle = isCleopatra ? this.cleopatraPuzzle() : undefined;
            this.replyJson(ws, msgId, {
              Code: 0,
              Data: {
                min_bet: SLOT_CHIP_LIST[0],
                max_bet: 100000,
                bet_list: SLOT_CHIP_LIST,
                betList: SLOT_CHIP_LIST,
                betConfig: SLOT_CHIP_LIST,
                chips: SLOT_CHIP_LIST,
                defaultBet: SLOT_CHIP_LIST[0],
                default_bet: SLOT_CHIP_LIST[0],
                lines: 20,
                currency: 'coin',
                currencyIcon: coinIcon,
                currency_icon: coinIcon,
                balance: bal,
                coin: bal,
                gold: bal,
                money: bal,
                userMoney: bal,
                ...(puzzle
                  ? {
                      puzzle,
                      puzzleInfo: puzzle,
                      NormalResult: puzzle,
                    }
                  : {}),
                // Football plinko: routes keyed by `${defaultRows}_${rewardMultipleIndex}`
                ...(isPlinko
                  ? {
                      defaultRows: 8,
                      defaultRisk: 1,
                      rows: 8,
                      risk: 1,
                      riskList: [0, 1, 2],
                      rowList: [8, 9, 10, 11, 12, 13, 14, 15, 16],
                    }
                  : {}),
              },
            });
            return;
          }

          if (msgId === 'Spin' || msgId === 'FreeSpin' || msgId === 'NormalSpin' || msgId === 'Bet') {
            await ensurePlayer(data);
            if (!player) {
              this.replyJson(ws, msgId === 'Bet' ? 'Bet' : 'Spin', {
                Code: 1,
                ErrorTips: 'Not logged in',
              });
              return;
            }
            betAmount = Math.max(
              SLOT_CHIP_LIST[0],
              Math.floor(
                Number(
                  data.bet ??
                    data.bet_amount ??
                    data.betAmount ??
                    data.defaultBet ??
                    data.chip ??
                    betAmount,
                ) || SLOT_CHIP_LIST[0],
              ),
            );
            if (!Number.isFinite(betAmount) || betAmount <= 0) {
              betAmount = SLOT_CHIP_LIST[0];
            }
            if (betAmount > 10_000) {
              this.replyJson(ws, msgId === 'Bet' ? 'Bet' : 'Spin', {
                Code: 1,
                ErrorTips: 'Bet too high',
              });
              return;
            }
            // Echo client's msgId (Spin / FreeSpin / NormalSpin / Bet) — clients listen on the same id.
            const replyId =
              msgId === 'FreeSpin' || msgId === 'NormalSpin' || msgId === 'Bet' || msgId === 'Spin'
                ? msgId
                : 'Spin';
            // Cleopatra/Slot777 silently drop Spin if timestamp_ms !== SpinData.timeStamp (strict !=).
            // Echo the exact client value when present; otherwise omit the field.
            const clientTs = data.timestamp_ms ?? data.timeStamp ?? data.timestamp;
            const hasClientTs = clientTs !== undefined && clientTs !== null && clientTs !== '';
            try {
              this.logger.log(
                `GAME_PROBE BAISHUN_SPIN game=${gameSlug} msgId=${msgId} bet=${betAmount} user=${player.userId} bal=${player.balance}`,
              );
              const afterBet = await this.debit(player.userId, betAmount, gameSlug);
              const { win, mult } = spinPayout(betAmount);
              let balance = afterBet;
              if (win > 0) balance = await this.credit(player.userId, win, gameSlug);
              player.balance = balance;
              const rewardMultipleIndex = Math.max(0, Math.min(10, mult > 0 ? Math.min(10, mult) : 0));
              const rewardLevel = win >= betAmount * 3 ? 2 : win > 0 ? 1 : 0;
              const roundId = randomUUID();
              const puzzle = this.cleopatraPuzzle();
              const puzzleInfo = this.slot777PuzzleInfo();
              const isPlinko = gameSlug === 'football-plinko';
              const plinkoIdx = isPlinko
                ? Math.max(0, Math.min(15, win > 0 ? Math.min(15, Math.floor(mult) || 1) : 0))
                : rewardMultipleIndex;
              const payload: Record<string, unknown> =
                replyId === 'Bet'
                  ? {
                      reward: win,
                      win,
                      win_amount: win,
                      total_win: win,
                      rewardMultipleIndex: plinkoIdx,
                      rewardLevel,
                      balance,
                      coin: balance,
                      gold: balance,
                      money: balance,
                      userMoney: balance,
                      bet: betAmount,
                      roundId,
                      round_id: roundId,
                      defaultRows: 8,
                      defaultRisk: 1,
                    }
                  : {
                      bet: betAmount,
                      win,
                      reward: win,
                      win_amount: win,
                      total_win: win,
                      balance,
                      coin: balance,
                      gold: balance,
                      money: balance,
                      userMoney: balance,
                      roundId,
                      round_id: roundId,
                      // Cleopatra uses `puzzle` (cols×rows); Slot777 uses `puzzleInfo` (3 middles).
                      puzzle,
                      puzzleInfo,
                      symbols: this.fakeSymbols(),
                      rewardIcon: win > 0 ? [0, 1, 2] : [],
                      rewardLevel,
                      rewardMultipleIndex: plinkoIdx,
                      rewardFree: 0,
                      totalFreeReward: 0,
                      freeCount: 0,
                      last_free_num: 0,
                      showPiggyBank: false,
                      todayWin: win,
                      lines: win > 0 ? [{ line: 1, win }] : [],
                      free_spin: 0,
                      freeSpin: 0,
                      supplyInfo: [],
                      defaultRows: isPlinko ? 8 : undefined,
                      defaultRisk: isPlinko ? 1 : undefined,
                    };
              if (hasClientTs) {
                // Cleopatra compares with loose != against string Date.now().toString().
                payload.timestamp_ms = clientTs;
                payload.timeStamp = clientTs;
                payload.timestamp = clientTs;
              }
              this.replyJson(ws, replyId, { Code: 0, Data: payload });
              this.replyJson(ws, 'SelfBalance', {
                Code: 0,
                Data: { balance, coin: balance, gold: balance, money: balance, userMoney: balance },
              });
              if (win > 0) {
                this.replyJson(ws, 'UserCoinChange', {
                  Code: 0,
                  Data: { balance, change: win, coin: balance, gold: balance },
                });
              }
              this.logger.log(
                `baishun ${replyId} ${gameSlug} user=${player.userId} bet=${betAmount} win=${win} bal=${balance}`,
              );
              void this.economy.onBetWin({
                sessionId: player.sessionId,
                userId: player.userId,
                gameId: gameSlug,
                betCoins: betAmount,
                winCoins: win,
                balanceAfter: balance,
              });
            } catch {
              this.logger.warn(
                `baishun insufficient ${gameSlug} user=${player.userId} bet=${betAmount}`,
              );
              this.replyJson(ws, replyId, {
                Code: 2,
                ErrorTips: 'Insufficient balance',
                Data: {
                  need_recharge: true,
                  balance: player.balance,
                  coin: player.balance,
                },
              });
              this.replyJson(ws, 'NeedRecharge', {
                Code: 2,
                ErrorTips: 'Insufficient balance',
                Data: {
                  need_recharge: true,
                  balance: player.balance,
                  coin: player.balance,
                },
              });
            }
            return;
          }

          if (
            msgId === 'Heartbeat' ||
            msgId === 'HeartBeat' ||
            msgId === 'heartBeat' ||
            msgId === 'Ping' ||
            msgId === 'ping' ||
            msgId === '1000' ||
            Number(msgId) === 1000
          ) {
            if (/^\d+$/.test(msgId) || msgId === '1000') {
              this.replyFish(ws, 1000, { ts: Date.now() });
            } else {
              this.replyJson(ws, msgId || 'Heartbeat', { Code: 0, Data: { ts: Date.now() } });
            }
            return;
          }

          if (msgId === 'ChangeBet') {
            betAmount = Math.max(
              SLOT_CHIP_LIST[0],
              Math.floor(Number(data.bet ?? betAmount)),
            );
            this.replyJson(ws, 'ChangeBet', { Code: 0, Data: { bet: betAmount } });
            return;
          }

          if (msgId === 'GameRecord' || msgId === 'GameRecordDetail') {
            // Bet history rows (bet/reward/time) — not the same as RankList.
            const now = Math.floor(Date.now() / 1000);
            const history = Array.from({ length: 8 }, (_, i) => {
              const bet = SLOT_CHIP_LIST[i % SLOT_CHIP_LIST.length];
              const reward = i % 3 === 0 ? bet * (2 + (i % 4)) : 0;
              return {
                bet,
                bet_amount: bet,
                reward,
                bonus: reward,
                win: reward,
                win_amount: reward,
                time: now - i * 3600,
                timestamp: now - i * 3600,
                multiple: reward > 0 ? reward / Math.max(1, bet) : 0,
                roundId: `r${now - i}`,
                round_id: `r${now - i}`,
              };
            });
            this.replyJson(ws, msgId, {
              Code: 0,
              Data: {
                list: history,
                records: history,
                total: history.length,
              },
            });
            return;
          }

          if (msgId === 'RankList') {
            let ranks = await this.economy.topWinners(gameSlug, 20);
            if (ranks.length < 5) {
              const global = await this.economy.topWinners(undefined, 20);
              const seen = new Set(ranks.map((r) => String(r.userId)));
              for (const g of global) {
                if (ranks.length >= 20) break;
                if (seen.has(String(g.userId))) continue;
                ranks.push(g);
                seen.add(String(g.userId));
              }
            }
            const list = ranks.map((r, idx) => {
              const amount = Math.max(0, Number(r.score) || Number(r.win) || 0);
              return {
                user_id: r.userId,
                userId: r.userId,
                open_id: r.userId,
                nickname: r.nickname,
                nickName: r.nickname,
                name: r.nickname,
                avatar: r.avatar,
                headImg: r.avatar,
                head: r.avatar,
                score: amount,
                win: amount,
                win_amount: amount,
                reward: amount, // football / cleopatra RankItem
                winMoney: amount,
                gold: amount,
                coin: amount,
                balance: amount,
                rank: r.rank || idx + 1,
                level: 1,
                spins: r.spins,
              };
            });
            const selfAmount = Math.max(0, Number(player?.balance) || 0);
            const self =
              list.find((r) => String(r.userId) === String(player?.publicId || player?.userId)) ||
              (player
                ? {
                    user_id: player.publicId,
                    userId: player.publicId,
                    open_id: player.publicId,
                    nickname: player.displayName || 'Player',
                    nickName: player.displayName || 'Player',
                    name: player.displayName || 'Player',
                    avatar: player.avatarUrl || '',
                    headImg: player.avatarUrl || '',
                    head: player.avatarUrl || '',
                    score: selfAmount,
                    win: selfAmount,
                    win_amount: selfAmount,
                    reward: selfAmount,
                    winMoney: selfAmount,
                    gold: selfAmount,
                    coin: selfAmount,
                    balance: selfAmount,
                    rank: list.length + 1,
                    level: 1,
                    spins: 0,
                  }
                : {
                    user_id: 0,
                    userId: 0,
                    nickname: 'Player',
                    nickName: 'Player',
                    avatar: '',
                    score: 0,
                    reward: 0,
                    win: 0,
                    coin: 0,
                    rank: 0,
                    level: 1,
                  });
            const records =
              list.length > 0
                ? [
                    ...list.filter((r) => String(r.userId) !== String(self.userId)),
                  ]
                : [];
            // Keep self in selfRank; list is top winners by amount.
            this.replyJson(ws, msgId, {
              Code: 0,
              Data: {
                list: list.length ? list : [self],
                records: list.length ? list : [self],
                rankList: list.length ? list : [self],
                selfRank: self,
                self: self,
                rankSize: Math.max(list.length || 1, 1),
                total: Math.max(list.length || 1, 1),
              },
            });
            return;
          }

          // Fishing numeric protocol (msgId as number)
          const fishCode = Number(msgId);
          if (Number.isFinite(fishCode) && fishCode >= 1000) {
            await this.handleFishingMsg(ws, gameSlug, fishCode, data, query, () => player, (p) => {
              player = p;
            });
            return;
          }

          if (msgId === 'QueryPiggyBank' || msgId === 'CollectPiggyBank') {
            this.replyJson(ws, msgId, { Code: 0, Data: { piggyBank: 0, status: 0 } });
            return;
          }

          this.logger.warn(`baishun unhandled msgId=${msgId || '?'} game=${gameSlug}`);
          this.replyJson(ws, msgId || 'MessageError', { Code: 0, Data: {} });
        } catch (err) {
          this.logger.warn(`BaiShun WS parse: ${(err as Error).message}`);
        }
      })();
    });
  }

  /** Swimsuit Party — Packer + protobuf routes. */
  private handlePacker(ws: WebSocket, gameSlug: string, rawUrl: string) {
    let player: MikooPlayerContext | null = null;
    let chipIdx = 0;
    let chipMultiple = CHIP_LIST[0];
    const query = this.parseQuery(rawUrl);

    this.logger.log(`baishun packer attach ${gameSlug}`);

    ws.on('message', (raw) => {
      void (async () => {
        try {
          const buf = Buffer.isBuffer(raw) ? raw : Buffer.from(raw as ArrayBuffer);
          const msg = unpackMessage(buf);
          if (!msg) return;

          if (msg.isHeartbeat) {
            this.sendBin(ws, packHeartbeat(msg.seq, Date.now()));
            return;
          }

          const { route, seq, buffer } = msg;

          if (route === SwimsuitRoute.PLAYER_ENTER_GAME) {
            const req = decodeEnterGameReq(buffer);
            const uid = Number(req.userName || query.user_id || query.userId || 0);
            const code = String(req.code || query.code || '');
            player = await this.sessions.resolvePlayer(gameSlug, uid, code);
            if (!player) {
              this.sendBin(
                ws,
                packMessage(
                  route,
                  seq,
                  encodeEnterGameRes({
                    code: 15, // AuthFailed
                    playerInfo: encodePlayerInfo({
                      playerId: 0,
                      userName: '',
                      nickName: '',
                      headImg: '',
                      coin: 0,
                      chip: chipMultiple,
                      chipIdx,
                    }),
                    channelCfg: encodeChannelCfg(),
                  }),
                ),
              );
              return;
            }
            const bal = await this.sessions.refreshBalance(player);
            this.logger.log(
              `baishun connect ${gameSlug} user=${player.userId} bal=${bal} room=${player.roomId || '-'}`,
            );
            chipIdx = 0;
            chipMultiple = CHIP_LIST[0];
            const playerInfo = encodePlayerInfo({
              playerId: Number(player.publicId) || player.userId.length,
              userName: player.displayName || String(player.publicId),
              nickName: player.displayName || 'Player',
              headImg: player.avatarUrl || '',
              coin: bal,
              chip: chipMultiple,
              chipIdx,
              appChannel: query.app_channel || '',
              appId: query.app_id || '',
              language: req.language || '2',
            });
            this.sendBin(
              ws,
              packMessage(
                route,
                seq,
                encodeEnterGameRes({
                  code: 0,
                  playerInfo,
                  channelCfg: encodeChannelCfg(),
                  lastSpin: encodeLastSpinBoard(),
                }),
              ),
            );
            return;
          }

          if (!player) {
            // late auth from query
            const uid = Number(query.user_id || query.userId || 0);
            const code = String(query.code || '');
            if (uid || code) player = await this.sessions.resolvePlayer(gameSlug, uid, code);
          }

          if (route === SwimsuitRoute.PLAYER_INFO) {
            if (!player) {
              this.sendBin(ws, packMessage(route, seq, encodePlayerRes(8, Buffer.alloc(0))));
              return;
            }
            const bal = await this.sessions.refreshBalance(player);
            const playerInfo = encodePlayerInfo({
              playerId: Number(player.publicId) || 1,
              userName: player.displayName || String(player.publicId),
              nickName: player.displayName || 'Player',
              headImg: player.avatarUrl || '',
              coin: bal,
              chip: chipMultiple,
              chipIdx,
            });
            this.sendBin(ws, packMessage(route, seq, encodePlayerRes(0, playerInfo)));
            return;
          }

          if (route === SwimsuitRoute.PLAYER_CHANGE_CHIP) {
            const req = decodeChangeChipReq(buffer);
            chipIdx = Math.max(0, Math.min(CHIP_LIST.length - 1, req.chipIdx | 0));
            chipMultiple = req.chipMultiple > 0 ? req.chipMultiple : CHIP_LIST[chipIdx];
            this.sendBin(
              ws,
              packMessage(route, seq, encodeChangeChipRes(0, chipIdx, chipMultiple)),
            );
            return;
          }

          if (route === SwimsuitRoute.PLAYER_SPIN) {
            if (!player) {
              this.sendBin(
                ws,
                packMessage(
                  route,
                  seq,
                  encodeSpinRes({
                    code: 8,
                    boardCols: this.swimsuitBoard(),
                    winCoin: 0,
                    coin: 0,
                    roundId: '',
                  }),
                ),
              );
              return;
            }
            const req = decodeSpinReq(buffer);
            if (req.chipMultiple > 0) chipMultiple = req.chipMultiple;
            if (req.chipIdx >= 0) chipIdx = req.chipIdx;
            const betAmount = Math.max(100, Math.floor(chipMultiple || CHIP_LIST[0]));
            if (betAmount > 10_000) {
              this.sendBin(
                ws,
                packMessage(
                  route,
                  seq,
                  encodeSpinRes({
                    code: 3,
                    boardCols: this.swimsuitBoard(),
                    winCoin: 0,
                    coin: player.balance,
                    roundId: '',
                  }),
                ),
              );
              return;
            }
            try {
              const isFree = !!req.free;
              let balance = await this.sessions.refreshBalance(player);
              let win = 0;
              if (!isFree) {
                balance = await this.debit(player.userId, betAmount, gameSlug);
                win = spinPayout(betAmount).win;
                if (win > 0) balance = await this.credit(player.userId, win, gameSlug);
              }
              player.balance = balance;
              const roundId = randomUUID();
              const boardCols = this.swimsuitBoard(win > 0);
              const winSym = boardCols[0]?.[1] ?? 1;
              this.sendBin(
                ws,
                packMessage(
                  route,
                  seq,
                  encodeSpinRes({
                    code: 0,
                    boardCols,
                    winCoin: win,
                    coin: balance,
                    roundId,
                    symbolMultiple: win > 0 ? Math.max(1, win / Math.max(1, betAmount)) : 0,
                    winLineList: win > 0 ? [1] : [],
                    winSymbolList: win > 0 ? [winSym] : [],
                  }),
                ),
              );
              if (!isFree) {
                void this.economy.onBetWin({
                  sessionId: player.sessionId,
                  userId: player.userId,
                  gameId: gameSlug,
                  betCoins: betAmount,
                  winCoins: win,
                  balanceAfter: balance,
                });
              }
            } catch {
              this.sendBin(
                ws,
                packMessage(
                  route,
                  seq,
                  encodeSpinRes({
                    code: 13, // CoinInsufficient
                    boardCols: this.swimsuitBoard(),
                    winCoin: 0,
                    coin: player.balance,
                    roundId: '',
                  }),
                ),
              );
            }
            return;
          }

          if (route === SwimsuitRoute.PLAYER_GAME_RECORD) {
            this.sendBin(ws, packMessage(route, seq, encodeCoinRecordRes()));
            return;
          }

          if (
            route === SwimsuitRoute.PLAYER_EXIT_GAME ||
            route === SwimsuitRoute.NOTIFY_PLAYER_EXIT_GAME
          ) {
            this.sendBin(ws, packMessage(route, seq, encodeMessage([pbInt32(1, 0)])));
            return;
          }

          if (route === SwimsuitRoute.PLAYER_PIGGY_BANK_RECEIVED) {
            this.sendBin(
              ws,
              packMessage(route, seq, encodeMessage([pbInt32(1, 0), pbUInt64(2, 0)])),
            );
            return;
          }

          // sync property / unknown — OK empty
          this.sendBin(ws, packMessage(route, seq, encodeMessage([pbInt32(1, 0)])));
        } catch (err) {
          this.logger.debug(`packer WS ${gameSlug}: ${(err as Error).message}`);
        }
      })();
    });
  }

  /** Hilo / Royal Battle — raw ClientMsg protobuf (no Packer). */
  private handleClientMsg(ws: WebSocket, gameSlug: string, rawUrl: string) {
    let player: MikooPlayerContext | null = null;
    const query = this.parseQuery(rawUrl);
    const isRoyal = gameSlug === 'royal-battle';
    // Per-connection Hilo round state
    let hiloCard = { id: 7, flower: 0 };
    let hiloBet = 0;
    let hiloRounds = 0;
    let royalRoundId = `r${Date.now()}`;
    let royalMyBet = 0;
    let chain: Promise<void> = Promise.resolve();

    const ensurePlayer = async () => {
      if (player) return player;
      const uid = Number(query.user_id || query.userId || 0);
      const code = String(query.code || query.token || '');
      if (uid || code) player = await this.sessions.resolvePlayer(gameSlug, uid, code);
      return player;
    };

    const randCard = () => ({
      id: 1 + Math.floor(Math.random() * 13),
      flower: Math.floor(Math.random() * 4),
    });

    void (async () => {
      const p0 = await ensurePlayer();
      this.sendBin(ws, encodeClientMsg(HILO_MSG.ResConnectSuccess, Buffer.alloc(0)));
      if (p0) {
        const bal = await this.sessions.refreshBalance(p0);
        this.logger.log(
          `baishun connect ${gameSlug} user=${p0.userId} bal=${bal} room=${p0.roomId || '-'}`,
        );
      }
      if (isRoyal) {
        royalRoundId = `r${Date.now()}`;
        this.sendBin(
          ws,
          encodeClientMsg(HILO_MSG.ResBetStateNotify, encodeRoyalBetStateNotify(royalRoundId)),
        );
      }
    })();

    ws.on('message', (raw) => {
      chain = chain.then(async () => {
        try {
          const buf = Buffer.isBuffer(raw) ? raw : Buffer.from(raw as ArrayBuffer);
          const { msgId, body } = decodeClientMsg(buf);

          if (msgId === HILO_MSG.ReqHeartBeat) {
            this.sendBin(
              ws,
              encodeClientMsg(HILO_MSG.ResHeadHeartBeat, encodeMessage([pbInt32(1, 30)])),
            );
            return;
          }

          if (msgId === HILO_MSG.ReqUpdateCode || msgId === HILO_MSG.ReqUserInfo) {
            const p = await ensurePlayer();
            const bal = p ? await this.sessions.refreshBalance(p) : 0;
            const info = isRoyal
              ? encodeRoyalUserInfoRes({
                  userId: String(p?.publicId || '0'),
                  nickname: p?.displayName || 'Player',
                  avatar: p?.avatarUrl || '',
                  balance: bal,
                })
              : encodeHiloUserInfoRes({
                  userId: String(p?.publicId || '0'),
                  nickname: p?.displayName || 'Player',
                  avatar: p?.avatarUrl || '',
                  balance: bal,
                });
            this.sendBin(ws, encodeClientMsg(HILO_MSG.ResUserInfo, info));
            if (msgId === HILO_MSG.ReqUpdateCode) {
              this.sendBin(
                ws,
                encodeClientMsg(HILO_MSG.ResUpdateCode, encodeMessage([pbInt32(1, 0)])),
              );
            }
            return;
          }

          if (msgId === HILO_MSG.ReqGameConfig) {
            this.sendBin(
              ws,
              encodeClientMsg(
                HILO_MSG.ResGameConfig,
                isRoyal ? encodeRoyalGetConfigRes() : encodeHiloGetConfigRes(),
              ),
            );
            if (isRoyal) {
              this.sendBin(
                ws,
                encodeClientMsg(HILO_MSG.ResBetStateNotify, encodeRoyalBetStateNotify(royalRoundId)),
              );
            }
            return;
          }

          if (msgId === HILO_MSG.ReqGameDetail || msgId === HILO_MSG.ReqRecover) {
            if (isRoyal) {
              this.sendBin(
                ws,
                encodeClientMsg(
                  msgId === HILO_MSG.ReqRecover ? HILO_MSG.ResRecover : HILO_MSG.ResGameDetail,
                  encodeRoyalDetailRes({
                    betFinishSec: 12,
                    selectedChipIndex: 0,
                    roundId: royalRoundId,
                  }),
                ),
              );
              this.sendBin(
                ws,
                encodeClientMsg(HILO_MSG.ResBetStateNotify, encodeRoyalBetStateNotify(royalRoundId)),
              );
            } else {
              hiloCard = randCard();
              this.sendBin(
                ws,
                encodeClientMsg(
                  msgId === HILO_MSG.ReqRecover ? HILO_MSG.ResRecover : HILO_MSG.ResGameDetail,
                  encodeHiloDetailRes({ cardId: hiloCard.id, flower: hiloCard.flower }),
                ),
              );
            }
            return;
          }

          if (msgId === HILO_MSG.ReqBet) {
            const p = await ensurePlayer();
            if (!p) {
              this.sendBin(
                ws,
                encodeClientMsg(HILO_MSG.ResBet, encodeMessage([pbUInt64(1, 0)])),
              );
              return;
            }
            const fields = decodeFields(body);
            if (isRoyal) {
              const chip = Math.max(
                10,
                Math.min(10_000, Math.floor(Number(fields[2] ?? fields[1] ?? 100))),
              );
              try {
                const bal = await this.debit(p.userId, chip, gameSlug);
                p.balance = bal;
                royalMyBet += chip;
                this.sendBin(
                  ws,
                  encodeClientMsg(HILO_MSG.ResBet, encodeRoyalBetRes(bal, royalMyBet)),
                );
                this.logger.log(
                  `baishun Bet ${gameSlug} user=${p.userId} bet=${chip} bal=${bal}`,
                );
                void this.economy.onBetWin({
                  sessionId: p.sessionId,
                  userId: p.userId,
                  gameId: gameSlug,
                  betCoins: chip,
                  winCoins: 0,
                  balanceAfter: bal,
                });
                // Auto settle after short delay so UI progresses.
                setTimeout(() => {
                  if (ws.readyState !== WebSocket.OPEN) return;
                  const winner = 1 + Math.floor(Math.random() * 2);
                  const blue = [1, 5, 9];
                  const red = [2, 6, 10];
                  const rewardAreaIds = [winner];
                  this.sendBin(
                    ws,
                    encodeClientMsg(
                      HILO_MSG.ResStartStateNotify,
                      encodeRoyalPlayStateNotify({
                        waitDuration: 5,
                        winner,
                        blueCards: blue,
                        redCards: red,
                        rewardAreaIds,
                      }),
                    ),
                  );
                  void (async () => {
                    const areaOdds = [0, 1.95, 1.95, 8, 4.5, 4.5];
                    const odds = areaOdds[winner] || 1.95;
                    const stake = royalMyBet || chip;
                    const win = Math.floor(stake * odds * (Math.random() > 0.45 ? 1 : 0));
                    let nb = await this.sessions.refreshBalance(p);
                    if (win > 0) nb = await this.credit(p.userId, win, gameSlug);
                    p.balance = nb;
                    const uid = String(p.publicId || p.userId);
                    setTimeout(() => {
                      if (ws.readyState !== WebSocket.OPEN) return;
                      this.sendBin(
                        ws,
                        encodeClientMsg(
                          HILO_MSG.ResAllPlayerGameResultNotify,
                          encodeRoyalEndSettlement({
                            winner,
                            blueCards: blue,
                            redCards: red,
                            rewardAreaIds,
                            otherTotalWin: win,
                          }),
                        ),
                      );
                      this.sendBin(
                        ws,
                        encodeClientMsg(
                          HILO_MSG.ResMineGameResultNotify,
                          encodeRoyalMineSettlement({
                            userId: uid,
                            balance: nb,
                            rewardAmount: win,
                            todayWin: win,
                          }),
                        ),
                      );
                      this.sendBin(
                        ws,
                        encodeClientMsg(HILO_MSG.ResOverStateNotify, Buffer.alloc(0)),
                      );
                      royalMyBet = 0;
                      royalRoundId = `r${Date.now()}`;
                      setTimeout(() => {
                        if (ws.readyState !== WebSocket.OPEN) return;
                        this.sendBin(
                          ws,
                          encodeClientMsg(
                            HILO_MSG.ResBetStateNotify,
                            encodeRoyalBetStateNotify(royalRoundId),
                          ),
                        );
                      }, 1500);
                    }, 4500);
                    if (win > 0) {
                      void this.economy.onBetWin({
                        sessionId: p.sessionId,
                        userId: p.userId,
                        gameId: gameSlug,
                        betCoins: 0,
                        winCoins: win,
                        balanceAfter: nb,
                      });
                    }
                  })();
                }, 3000);
              } catch {
                this.sendBin(
                  ws,
                  encodeClientMsg(HILO_MSG.ResBet, encodeRoyalBetRes(p.balance || 0, royalMyBet)),
                );
              }
              return;
            }

            // Hilo: field1 = amount
            const betAmount = Math.max(
              10,
              Math.min(10_000, Math.floor(Number(fields[1] ?? 100))),
            );
            try {
              const afterBet = await this.debit(p.userId, betAmount, gameSlug);
              p.balance = afterBet;
              hiloBet = betAmount;
              hiloRounds = 1;
              hiloCard = randCard();
              this.sendBin(
                ws,
                encodeClientMsg(
                  HILO_MSG.ResBet,
                  encodeHiloBetRes({
                    newBalance: afterBet,
                    cardId: hiloCard.id,
                    flower: hiloCard.flower,
                    showBet: betAmount,
                    roundState: 1,
                  }),
                ),
              );
              this.logger.log(
                `baishun Bet ${gameSlug} user=${p.userId} bet=${betAmount} bal=${afterBet}`,
              );
              void this.economy.onBetWin({
                sessionId: p.sessionId,
                userId: p.userId,
                gameId: gameSlug,
                betCoins: betAmount,
                winCoins: 0,
                balanceAfter: afterBet,
              });
            } catch {
              this.sendBin(
                ws,
                encodeClientMsg(
                  HILO_MSG.ResBet,
                  encodeHiloBetRes({
                    newBalance: p.balance || 0,
                    cardId: hiloCard.id,
                    flower: hiloCard.flower,
                    showBet: 0,
                    roundState: 0,
                  }),
                ),
              );
            }
            return;
          }

          if (msgId === HILO_MSG.ReqSelectResult) {
            const p = await ensurePlayer();
            const fields = decodeFields(body);
            const selectType = Number(fields[1] ?? 0); // 1 lower, 2 higher
            const prev = hiloCard.id;
            const next = randCard();
            const won =
              selectType === 2
                ? next.id > prev
                : selectType === 1
                  ? next.id < prev
                  : next.id !== prev;
            hiloCard = next;
            let showBet = hiloBet;
            let bal = p ? await this.sessions.refreshBalance(p) : 0;
            if (won && p && hiloBet > 0) {
              const payout = Math.floor(hiloBet * 1.5);
              bal = await this.credit(p.userId, payout, gameSlug);
              p.balance = bal;
              showBet = payout;
              hiloBet = payout;
              hiloRounds += 1;
              void this.economy.onBetWin({
                sessionId: p.sessionId,
                userId: p.userId,
                gameId: gameSlug,
                betCoins: 0,
                winCoins: payout,
                balanceAfter: bal,
              });
            } else {
              hiloBet = 0;
            }
            this.sendBin(
              ws,
              encodeClientMsg(
                HILO_MSG.ResSelectResult,
                encodeHiloSelectRes({
                  result: won ? 1 : 0,
                  cardId: next.id,
                  flower: next.flower,
                  showBet,
                  roundState: won ? 1 : 0,
                  numberRounds: hiloRounds,
                }),
              ),
            );
            this.logger.log(
              `baishun Select ${gameSlug} user=${p?.userId || '?'} won=${won} bal=${bal}`,
            );
            return;
          }

          if (msgId === HILO_MSG.ReqSwitchCard || msgId === HILO_MSG.ReqHistory) {
            if (isRoyal) {
              // Royal: 412 = ReqHistory → ResHistory (512)
              this.sendBin(
                ws,
                encodeClientMsg(HILO_MSG.ResHistory, encodeRoyalHistory()),
              );
              return;
            }
            hiloCard = randCard();
            this.sendBin(
              ws,
              encodeClientMsg(
                HILO_MSG.ResSwitchCard,
                encodeHiloSwitchCardRes(hiloCard.id, hiloCard.flower),
              ),
            );
            return;
          }

          if (msgId === HILO_MSG.ReqChangeSelectChip) {
            const fields = decodeFields(body);
            const chip = Math.max(0, Math.floor(Number(fields[1] ?? 0)));
            this.sendBin(
              ws,
              encodeClientMsg(
                HILO_MSG.ResChangeSelectChip,
                encodeMessage([pbInt32(1, chip)]),
              ),
            );
            return;
          }

          if (
            msgId === HILO_MSG.ReqPlayerRank ||
            msgId === HILO_MSG.ReqMyRecord ||
            msgId === HILO_MSG.ReqUserRank ||
            msgId === 418
          ) {
            const ranks = await this.economy.topWinners(gameSlug, 20);
            const resId =
              msgId === HILO_MSG.ReqPlayerRank
                ? HILO_MSG.ResPlayerRank
                : msgId === HILO_MSG.ReqMyRecord
                  ? HILO_MSG.ResMyRecord
                  : msgId === HILO_MSG.ReqUserRank
                    ? HILO_MSG.ResUserRank
                    : 518;
            this.sendBin(
              ws,
              encodeClientMsg(
                resId,
                encodeMessage([
                  pbInt32(1, 0),
                  ...ranks.slice(0, 1).flatMap((r) => [
                    pbString(2, r.userId),
                    pbString(3, r.nickname),
                    pbString(4, r.avatar),
                    pbUInt64(5, r.score),
                  ]),
                ]),
              ),
            );
            return;
          }

          if (msgId >= 400 && msgId < 500) {
            this.logger.warn(`clientmsg unhandled ${gameSlug} msgId=${msgId}`);
            this.sendBin(ws, encodeClientMsg(msgId + 100, encodeMessage([pbInt32(1, 0)])));
            return;
          }
          this.logger.warn(`clientmsg unknown ${gameSlug} msgId=${msgId}`);
        } catch (err) {
          this.logger.warn(`clientmsg WS ${gameSlug}: ${(err as Error).message}`);
        }
      });
    });
  }

  private swimsuitBoard(forceWinLine = false): number[][] {
    const cols: number[][] = [];
    const winSym = 1 + Math.floor(Math.random() * 8);
    for (let c = 0; c < 5; c++) {
      const col: number[] = [];
      for (let r = 0; r < 4; r++) {
        if (forceWinLine && r === 1) col.push(winSym);
        else col.push(1 + Math.floor(Math.random() * 8));
      }
      cols.push(col);
    }
    return cols;
  }

  /** Fishing uses numeric msgId + {errCode,data} envelope. */
  private replyFish(ws: WebSocket, msgId: number, data: Record<string, unknown>, errCode = 0) {
    if (ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ msgId, errCode, data }));
  }

  private async handleFishingMsg(
    ws: WebSocket,
    gameSlug: string,
    code: number,
    data: Record<string, unknown>,
    query: Record<string, string>,
    getPlayer: () => MikooPlayerContext | null,
    setPlayer: (p: MikooPlayerContext | null) => void,
  ) {
    let player = getPlayer();
    if (!player) {
      const uid = Number(data.user_id ?? data.userId ?? query.user_id ?? query.userId ?? 0);
      const auth = String(data.code ?? data.token ?? query.code ?? '');
      if (uid || auth) {
        player = await this.sessions.resolvePlayer(gameSlug, uid, auth);
        setPlayer(player);
      }
    }

    if (code === 1000) {
      this.replyFish(ws, 1000, { ts: Date.now() });
      return;
    }

    if (code === 1001) {
      const bal = player ? await this.sessions.refreshBalance(player) : 0;
      this.replyFish(ws, 1001, {
        coin: bal,
        balance: bal,
        avatar: player?.avatarUrl || '',
        nickname: player?.displayName || 'Player',
        user_id: player?.publicId || 0,
        currency_icon: '',
      });
      return;
    }

    if (code === 1002) {
      if (!player) {
        // Last-chance auth from query so splash can leave.
        const uid = Number(query.user_id ?? query.userId ?? 0);
        const auth = String(query.code ?? query.token ?? '');
        if (uid || auth) {
          player = await this.sessions.resolvePlayer(gameSlug, uid, auth);
          setPlayer(player);
        }
      }
      if (!player) {
        this.replyFish(ws, 1002, { fishs: [] }, 5);
        return;
      }
      const bal = await this.sessions.refreshBalance(player);
      const uid = player.publicId || player.userId;
      const makeFish = (id: number, i: number) => ({
        id,
        ft: 1 + (i % 8),
        line: 1 + (i % 12),
        buffer: 0,
        ageTime: 0,
        delayed: 0,
      });
      const fishs = Array.from({ length: 8 }, (_, i) => makeFish(1000 + i, i));
      const self = {
        pos: 0,
        userId: uid,
        user_id: uid,
        open_id: uid,
        coin: bal,
        nickname: player.displayName || 'Player',
        nickName: player.displayName || 'Player',
        avatar: player.avatarUrl || '',
        angle: 90,
        weapon: 1,
        level: 1,
        betIndex: 0,
        fireCount: 0,
        exp: 0,
        maxExp: 0,
      };
      this.replyFish(ws, 1002, {
        user_id: uid,
        players: [self],
        paoBei: CHIP_LIST,
        allProp: {},
        itemsCfg: {},
        curBank: 0,
        piggyBank: 0,
        piggyBankStatus: 0,
        fishType: [1, 2, 3, 4, 5, 6, 7, 8],
        coinType: 0,
        fishs,
      });
      this.logger.log(`baishun connect ${gameSlug} user=${player.userId} bal=${bal} (fishing enter)`);
      // Keep spawning periodically while socket open — payload must be { fishs: [...] }.
      const spawnTimer = setInterval(() => {
        if (ws.readyState !== WebSocket.OPEN) {
          clearInterval(spawnTimer);
          return;
        }
        const fid = 2000 + Math.floor(Math.random() * 9000);
        this.replyFish(ws, 1004, {
          fishs: [makeFish(fid, fid % 8)],
        });
      }, 2800);
      ws.once('close', () => clearInterval(spawnTimer));
      return;
    }

    if (code === 1005) {
      // Weapon fire — small debit per shot
      const cost = Math.max(10, Math.floor(Number(data.coin ?? data.bet ?? data.mul ?? 10)));
      if (!player) {
        this.replyFish(ws, 1005, {}, 5);
        return;
      }
      try {
        const bal = await this.debit(player.userId, Math.min(cost, 5000), gameSlug);
        player.balance = bal;
        this.replyFish(ws, 1005, {
          userID: player.publicId || player.userId,
          userId: player.publicId || player.userId,
          newCoin: bal,
          coin: bal,
          curCoin: bal,
          ok: true,
        });
        this.replyFish(ws, 1019, { coin: bal, balance: bal });
        void this.economy.onBetWin({
          sessionId: player.sessionId,
          userId: player.userId,
          gameId: gameSlug,
          betCoins: Math.min(cost, 5000),
          winCoins: 0,
          balanceAfter: bal,
        });
      } catch {
        this.replyFish(ws, 1005, { need_recharge: true }, 5);
      }
      return;
    }

    if (code === 1006) {
      // Hit — occasional fish kill payout
      if (!player) {
        this.replyFish(ws, 1006, {}, 5);
        return;
      }
      const fishId = Number(data.fishId ?? data.id ?? 0);
      const { win } = spinPayout(Math.max(50, Math.floor(Number(data.coin ?? 100))));
      let bal = await this.sessions.refreshBalance(player);
      if (win > 0) {
        bal = await this.credit(player.userId, win, gameSlug);
        player.balance = bal;
        this.replyFish(ws, 1003, {
          ids: [fishId],
          fishId,
          fish_id: fishId,
          id: fishId,
          userId: player.publicId,
          user_id: player.publicId,
          coin: win,
          bonus: win,
          die: true,
          kill: true,
        });
        this.replyFish(ws, 1019, { coin: bal, balance: bal, change: win });
        void this.economy.onBetWin({
          sessionId: player.sessionId,
          userId: player.userId,
          gameId: gameSlug,
          betCoins: 0,
          winCoins: win,
          balanceAfter: bal,
        });
      }
      this.replyFish(ws, 1006, {
        ok: true,
        coin: bal,
        win,
        fishId,
        fish_id: fishId,
        bonus: win,
        userId: player.publicId,
      });
      return;
    }

    if (code === 1101) {
      let ranks = await this.economy.topWinners(gameSlug, 20);
      if (ranks.length < 5) {
        ranks = await this.economy.topWinners(undefined, 20);
      }
      this.replyFish(ws, 1101, {
        RankList: ranks.map((r) => {
          const amount = Math.max(0, Number(r.score) || Number(r.win) || 0);
          return {
            rank: r.rank,
            level: 1,
            nickName: r.nickname,
            nickname: r.nickname,
            avatar: r.avatar,
            win: amount,
            score: amount,
            coin: amount,
            reward: amount,
            userId: r.userId,
          };
        }),
        list: ranks.map((r) => ({
          rank: r.rank,
          nickName: r.nickname,
          win: Math.max(0, Number(r.score) || 0),
          avatar: r.avatar,
        })),
      });
      return;
    }

    if (code === 1011 || code === 1010 || code === 1030 || code === 1031) {
      this.replyFish(ws, code, { list: [], piggyBank: 0, status: 0, curBank: 0 });
      return;
    }

    // Default OK so client UI does not freeze on unknown codes.
    this.replyFish(ws, code, {});
  }

  private parseQuery(url: string) {
    const out: Record<string, string> = {};
    const q = url.includes('?') ? url.split('?')[1] : '';
    for (const part of q.split('&')) {
      if (!part) continue;
      const [k, ...rest] = part.split('=');
      try {
        out[decodeURIComponent(k)] = decodeURIComponent(rest.join('=') || '');
      } catch {
        out[k] = rest.join('=') || '';
      }
    }
    return out;
  }

  private fakeSymbols() {
    return Array.from({ length: 15 }, () => 1 + Math.floor(Math.random() * 8));
  }

  /** Cleopatra: NormalResult[col][row] — heights [6,7,7,7,6] = 33 cells. */
  private cleopatraPuzzle(): number[][] {
    const heights = [6, 7, 7, 7, 6];
    const cols: number[][] = [];
    for (let c = 0; c < heights.length; c++) {
      const col: number[] = [];
      for (let r = 0; r < heights[c]; r++) col.push(1 + Math.floor(Math.random() * 8));
      cols.push(col);
    }
    return cols;
  }

  /** Slot777: puzzleInfo is 3 middle-row symbol ids; client expands to 3×3. */
  private slot777PuzzleInfo(): number[] {
    return [
      1 + Math.floor(Math.random() * 8),
      1 + Math.floor(Math.random() * 8),
      1 + Math.floor(Math.random() * 8),
    ];
  }

  private replyJson(ws: WebSocket, msgId: string, data: Record<string, unknown>) {
    if (ws.readyState !== WebSocket.OPEN) return;
    // Cleopatra/Slot777/Plinko wrap: { msgId, errCode, data } where callbacks use
    // Code=errCode and Data=data (flat payload). Nested {Code,Data} inside `data` freezes UI.
    const hasEnvelope = data.Code !== undefined || data.Data !== undefined;
    const errCode = hasEnvelope ? Number(data.Code ?? 0) : 0;
    const errMsg = hasEnvelope ? String(data.ErrorTips ?? data.errMsg ?? '') : '';
    const body = hasEnvelope
      ? ((data.Data as Record<string, unknown>) ?? {})
      : data;
    ws.send(
      JSON.stringify({
        msgId,
        errCode,
        errMsg,
        data: body,
      }),
    );
  }

  private sendBin(ws: WebSocket, buf: Buffer) {
    if (ws.readyState !== WebSocket.OPEN) return;
    ws.send(buf);
  }

  private async debit(userId: string, amount: number, gameId: string) {
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
          referenceType: 'mikoo_baishun_bet',
          referenceId: randomUUID(),
          description: `BaiShun ${gameId} spin`,
          metadata: { gameId, amount },
        }),
      );
      return Number(wallet.coins);
    });
  }

  private async credit(userId: string, amount: number, gameId: string) {
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
          referenceType: 'mikoo_baishun_win',
          referenceId: randomUUID(),
          description: `BaiShun ${gameId} win`,
          metadata: { gameId, amount },
        }),
      );
      return Number(wallet.coins);
    });
  }
}
