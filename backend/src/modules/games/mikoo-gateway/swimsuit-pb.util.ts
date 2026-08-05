/**
 * Minimal protobuf encode/decode for swimsuit-party BaiShun Packer routes.
 * Field numbers taken from the game's embedded protobufjs definitions.
 */
import {
  decodeFields,
  encodeMessage,
  pbDouble,
  pbInt32,
  pbString,
  pbUInt64,
} from './mikoo-proto.util';
import { chipsList } from './mikoo-game-economy';

function writeVarint(n: number): Buffer {
  const out: number[] = [];
  let v = n >>> 0;
  while (v >= 0x80) {
    out.push((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  out.push(v);
  return Buffer.from(out);
}

function tag(field: number, wire: number): Buffer {
  return writeVarint((field << 3) | wire);
}

function pbBool(field: number, value: boolean): Buffer {
  return Buffer.concat([tag(field, 0), writeVarint(value ? 1 : 0)]);
}

function pbBytes(field: number, value: Buffer): Buffer {
  return Buffer.concat([tag(field, 2), writeVarint(value.length), value]);
}

function pbPackedInt32(field: number, values: number[]): Buffer {
  const parts = values.map((v) => writeVarint(v | 0));
  const packed = Buffer.concat(parts);
  return Buffer.concat([tag(field, 2), writeVarint(packed.length), packed]);
}

function pbPackedDouble(field: number, values: number[]): Buffer {
  const packed = Buffer.alloc(values.length * 8);
  values.forEach((v, i) => packed.writeDoubleLE(Number(v) || 0, i * 8));
  return Buffer.concat([tag(field, 2), writeVarint(packed.length), packed]);
}

function encodeCow(rows: number[]): Buffer {
  return encodeMessage([pbPackedInt32(1, rows)]);
}

export const SwimsuitRoute = {
  PLAYER_ENTER_GAME: 100,
  PLAYER_INFO: 101,
  PLAYER_EXIT_GAME: 102,
  NOTIFY_PLAYER_EXIT_GAME: 103,
  PLAYER_GAME_RECORD: 104,
  PLAYER_CHANGE_CHIP: 105,
  PLAYER_SPIN: 107,
  PLAYER_SYNC_PROPERTY: 108,
  PLAYER_PIGGY_BANK_RECEIVED: 109,
} as const;

/** Swimsuit Party / packer games — expanded stakes. */
export const CHIP_LIST = chipsList();

/** Cleopatra / Slot777 / JSON slots — same tray for all games. */
export const SLOT_CHIP_LIST = chipsList();

export function decodeEnterGameReq(buf: Buffer) {
  const f = decodeFields(buf);
  return {
    env: String(f[1] ?? ''),
    language: String(f[2] ?? ''),
    gameId: Number(f[3] ?? 0),
    userName: String(f[4] ?? ''),
    appChannel: String(f[5] ?? ''),
    appId: String(f[6] ?? ''),
    gameMode: Number(f[7] ?? 0),
    code: String(f[8] ?? ''),
    roomId: String(f[9] ?? ''),
    role: String(f[10] ?? ''),
  };
}

export function decodeSpinReq(buf: Buffer) {
  const f = decodeFields(buf);
  return {
    chipIdx: Number(f[1] ?? 0),
    chipMultiple: Number(f[2] ?? 0),
    free: Boolean(f[3]),
  };
}

export function decodeChangeChipReq(buf: Buffer) {
  const f = decodeFields(buf);
  return {
    chipIdx: Number(f[1] ?? 0),
    chipMultiple: Number(f[2] ?? 0),
  };
}

export function encodePlayerInfo(data: {
  playerId: number;
  userName: string;
  nickName: string;
  headImg: string;
  coin: number;
  chip: number;
  chipIdx: number;
  appChannel?: string;
  appId?: string;
  language?: string;
}): Buffer {
  return encodeMessage([
    pbUInt64(1, data.playerId),
    pbString(2, data.userName),
    pbString(3, data.nickName),
    pbString(4, data.headImg),
    pbInt32(5, 0),
    pbInt32(6, 1),
    pbInt32(7, 0),
    pbUInt64(8, Math.max(0, Math.floor(data.coin))),
    pbInt32(9, data.chip),
    pbString(12, data.appChannel || ''),
    pbString(13, data.appId || ''),
    pbString(14, data.language || '2'),
    pbBool(15, false),
    pbBool(16, false),
    pbUInt64(17, 0),
    pbUInt64(18, 0),
    pbUInt64(19, 0),
    pbBool(20, false),
    pbInt32(21, data.chipIdx),
    pbUInt64(22, 0),
    pbUInt64(23, 0),
    pbUInt64(24, 0),
  ]);
}

export function encodeChannelCfg(chipList: number[] = CHIP_LIST): Buffer {
  // Minimal cfg: ChipList is enough for bet buttons; symbols optional.
  return encodeMessage([pbPackedInt32(3, chipList), pbBool(6, false)]);
}

export function encodeEmptyBoard(cols = 5, rows = 4): Buffer[] {
  const board: Buffer[] = [];
  for (let c = 0; c < cols; c++) {
    const col: number[] = [];
    for (let r = 0; r < rows; r++) {
      // Prefer low face symbols (1..8) — avoid 0 UNKNOWN.
      col.push(1 + Math.floor(Math.random() * 8));
    }
    board.push(pbBytes(2, encodeCow(col))); // PlayerSpinRes.Board field 2
  }
  return board;
}

export function encodeLastSpinBoard(cols = 5, rows = 4): Buffer {
  const parts: Buffer[] = [];
  for (let c = 0; c < cols; c++) {
    const col: number[] = [];
    for (let r = 0; r < rows; r++) col.push(1 + Math.floor(Math.random() * 8));
    parts.push(pbBytes(1, encodeCow(col))); // LastSpinResultInfo.Board field 1
  }
  parts.push(pbDouble(3, 0));
  parts.push(pbUInt64(4, 0));
  parts.push(pbString(5, ''));
  return encodeMessage(parts);
}

export function encodeEnterGameRes(data: {
  code?: number;
  playerInfo: Buffer;
  channelCfg: Buffer;
  lastSpin?: Buffer;
}): Buffer {
  const parts = [
    pbInt32(1, data.code ?? 0),
    pbString(2, 'OK'),
    pbBytes(3, data.playerInfo),
    pbBytes(4, data.channelCfg),
    pbString(5, 'UTC'),
  ];
  if (data.lastSpin) parts.push(pbBytes(6, data.lastSpin));
  return encodeMessage(parts);
}

export function encodePlayerRes(code: number, playerInfo: Buffer): Buffer {
  return encodeMessage([pbInt32(1, code), pbBytes(2, playerInfo)]);
}

export function encodeChangeChipRes(code: number, chipIdx: number, chipMultiple: number): Buffer {
  return encodeMessage([pbInt32(1, code), pbInt32(2, chipIdx), pbInt32(3, chipMultiple)]);
}

export function encodeSpinRes(data: {
  code?: number;
  boardCols: number[][];
  winCoin: number;
  coin: number;
  roundId: string;
  freeCount?: number;
  symbolMultiple?: number;
  winLineList?: number[];
  winSymbolList?: number[];
}): Buffer {
  const parts: Buffer[] = [pbInt32(1, data.code ?? 0)];
  for (const col of data.boardCols) {
    parts.push(pbBytes(2, encodeCow(col)));
  }
  const lines = data.winLineList ?? [];
  const syms = data.winSymbolList ?? [];
  if (lines.length) parts.push(pbPackedInt32(3, lines));
  parts.push(pbInt32(4, data.freeCount ?? 0));
  parts.push(pbDouble(6, data.symbolMultiple ?? 0));
  parts.push(pbUInt64(9, Math.max(0, Math.floor(data.winCoin))));
  parts.push(pbUInt64(10, Math.max(0, Math.floor(data.coin))));
  parts.push(pbString(12, data.roundId));
  if (syms.length) parts.push(pbPackedInt32(13, syms));
  return encodeMessage(parts);
}

export function encodeCoinRecordRes(): Buffer {
  return encodeMessage([pbInt32(1, 0), pbInt32(2, 1), pbInt32(3, 20)]);
}

/** Hilo GetConfigRes: betAmountLimit@3, chips@4 packed int32. */
export function encodeHiloGetConfigRes(
  betAmountLimit = 50_000,
  chips: number[] = CHIP_LIST,
): Buffer {
  return encodeMessage([pbInt32(3, betAmountLimit), pbPackedInt32(4, chips)]);
}

/** Hilo UserInfoRes. */
export function encodeHiloUserInfoRes(data: {
  userId: string;
  nickname: string;
  avatar: string;
  balance: number;
}): Buffer {
  return encodeMessage([
    pbString(1, data.userId),
    pbString(2, data.nickname),
    pbString(3, data.avatar),
    pbUInt64(4, Math.max(0, Math.floor(data.balance))),
  ]);
}

function encodeHiloCard(id: number, flower: number): Buffer {
  return encodeMessage([pbInt32(1, id), pbInt32(2, flower)]);
}

function encodeHiloSelectButton(amount: number, comparison: number): Buffer {
  return encodeMessage([pbUInt64(1, Math.max(0, Math.floor(amount))), pbInt32(2, comparison)]);
}

/** Hilo BetRes — must include buttons + current card or UI stays dead. */
export function encodeHiloBetRes(data: {
  newBalance: number;
  cardId: number;
  flower: number;
  showBet: number;
  roundState?: number;
}): Buffer {
  const left = encodeHiloSelectButton(Math.floor(data.showBet * 1.5), 1); // lower
  const right = encodeHiloSelectButton(Math.floor(data.showBet * 1.5), 2); // higher
  const card = encodeHiloCard(data.cardId, data.flower);
  return encodeMessage([
    pbUInt64(1, Math.max(0, Math.floor(data.newBalance))),
    pbBytes(2, left),
    pbBytes(3, right),
    pbBytes(4, card),
    pbInt32(5, data.roundState ?? 1),
    pbUInt64(6, Math.max(0, Math.floor(data.showBet))),
  ]);
}

/** Hilo SelectRes after HI/LO choice. */
export function encodeHiloSelectRes(data: {
  result: number; // 1 win, 0 lose
  cardId: number;
  flower: number;
  showBet: number;
  roundState: number;
  numberRounds?: number;
}): Buffer {
  const left = encodeHiloSelectButton(Math.floor(data.showBet * 1.5), 1);
  const right = encodeHiloSelectButton(Math.floor(data.showBet * 1.5), 2);
  const card = encodeHiloCard(data.cardId, data.flower);
  return encodeMessage([
    pbInt32(1, data.result),
    pbBytes(2, left),
    pbBytes(3, right),
    pbBytes(4, card),
    pbInt32(5, data.roundState),
    pbUInt64(6, Math.max(0, Math.floor(data.showBet))),
    pbInt32(7, data.numberRounds ?? 1),
  ]);
}

/** Hilo DetailRes / GameDetail — idle table. */
export function encodeHiloDetailRes(data?: {
  cardId?: number;
  flower?: number;
  selectedChip?: number;
}): Buffer {
  const card = encodeHiloCard(data?.cardId ?? 7, data?.flower ?? 0);
  return encodeMessage([
    pbBytes(1, card),
    pbInt32(2, 0), // roundState idle
    pbUInt64(3, 0),
    pbUInt64(4, 0),
    pbUInt64(7, 0),
    pbInt32(10, data?.selectedChip ?? (CHIP_LIST[0] || 100)),
    pbString(11, ''),
    pbInt32(12, 0),
  ]);
}

export function encodeHiloSwitchCardRes(cardId: number, flower: number): Buffer {
  return encodeMessage([pbBytes(1, encodeHiloCard(cardId, flower))]);
}

/** Royal Battle GetConfigRes. */
export function encodeRoyalGetConfigRes(chips: number[] = CHIP_LIST): Buffer {
  const areas = ['1.95', '1.95', '8', '4.5', '4.5'].map((odds) => {
    const row = encodeMessage([pbString(1, odds)]);
    return pbBytes(5, row);
  });
  return encodeMessage([
    pbInt32(1, 15), // betTotalDuration
    pbInt32(2, 5), // playWaitDuration
    pbInt32(3, 5), // settlementWaitDuration
    pbPackedInt32(4, chips),
    ...areas,
    pbBool(6, false),
    pbBool(7, false),
    pbBool(8, false),
    pbInt32(9, 0),
    pbBool(10, false),
    pbBool(11, false),
    pbInt32(13, 3000), // showCardDuration ms (client divides by 1000)
  ]);
}

/** Royal UserInfoRes with balanceGame. */
export function encodeRoyalUserInfoRes(data: {
  userId: string;
  nickname: string;
  avatar: string;
  balance: number;
}): Buffer {
  const bal = Math.max(0, Math.floor(data.balance));
  return encodeMessage([
    pbString(1, data.userId),
    pbString(2, data.nickname),
    pbString(3, data.avatar),
    pbUInt64(4, bal),
    pbUInt64(5, bal),
  ]);
}

/** Royal BetRes. */
export function encodeRoyalBetRes(newBalance: number, totalBetMy: number): Buffer {
  return encodeMessage([
    pbUInt64(1, Math.max(0, Math.floor(newBalance))),
    pbUInt64(2, Math.max(0, Math.floor(totalBetMy))),
    pbInt32(3, 0),
  ]);
}

/** Royal DetailRes — betting phase. Field numbers must match client DetailRes. */
export function encodeRoyalDetailRes(data: {
  betFinishSec: number;
  selectedChipIndex?: number;
  selectedChip?: number;
  roundId?: string;
}): Buffer {
  const roundId = data.roundId || `r${Date.now()}`;
  const chipIdx =
    typeof data.selectedChipIndex === 'number'
      ? data.selectedChipIndex
      : 0;
  return encodeMessage([
    pbInt32(1, 1), // roomState = Bet
    pbUInt64(2, Math.max(0, Math.floor(data.betFinishSec))),
    pbUInt64(3, 0), // playFinishDuration
    pbUInt64(4, 0), // startSettlementDuration
    pbUInt64(8, 0), // totalBet
    pbUInt64(9, 0), // totalBetMy
    pbInt32(10, chipIdx), // selectedChip INDEX
    pbUInt64(11, 0), // todayWin
    pbBool(14, false), // isRepeat
    pbString(17, roundId),
  ]);
}

export function encodeRoyalBetStateNotify(roundId: string): Buffer {
  return encodeMessage([
    pbString(1, roundId),
    pbBool(2, false),
    pbString(3, roundId),
  ]);
}

export function encodeRoyalPlayStateNotify(data: {
  waitDuration: number;
  winner: number;
  blueCards?: number[];
  redCards?: number[];
  rewardAreaIds?: number[];
}): Buffer {
  const blue = data.blueCards ?? [1, 2, 3];
  const red = data.redCards ?? [4, 5, 6];
  const rewards = data.rewardAreaIds ?? [data.winner];
  return encodeMessage([
    pbUInt64(1, data.waitDuration),
    pbPackedInt32(2, blue),
    pbInt32(3, 1),
    pbPackedInt32(4, red),
    pbInt32(5, 1),
    pbInt32(6, data.winner),
    pbPackedInt32(7, rewards),
  ]);
}

/** Royal ResMineGameResultNotify (527) — UserSettlementFinish. */
export function encodeRoyalMineSettlement(data: {
  userId: string;
  balance: number;
  rewardAmount: number;
  todayWin?: number;
}): Buffer {
  const bal = Math.max(0, Math.floor(data.balance));
  const reward = Math.max(0, Math.floor(data.rewardAmount));
  return encodeMessage([
    pbString(1, data.userId),
    pbUInt64(2, bal),
    pbUInt64(3, Math.max(0, Math.floor(data.todayWin ?? reward))),
    pbUInt64(4, reward),
    pbUInt64(5, reward),
    pbUInt64(6, Math.max(0, Math.floor(data.todayWin ?? reward))),
    pbUInt64(7, bal),
  ]);
}

/** Royal ResAllPlayerGameResultNotify (525) — EndSettlementData (minimal). */
export function encodeRoyalEndSettlement(data: {
  winner: number;
  blueCards?: number[];
  redCards?: number[];
  rewardAreaIds?: number[];
  otherTotalWin?: number;
}): Buffer {
  const blue = data.blueCards ?? [1, 2, 3];
  const red = data.redCards ?? [4, 5, 6];
  const rewards = data.rewardAreaIds ?? [data.winner];
  return encodeMessage([
    pbUInt64(3, Math.max(0, Math.floor(data.otherTotalWin ?? 0))),
    pbBool(4, true),
    pbPackedInt32(5, blue),
    pbInt32(6, 1),
    pbPackedInt32(7, red),
    pbInt32(8, 1),
    pbInt32(9, data.winner),
    pbPackedInt32(10, rewards),
  ]);
}

/** Royal History { winners@1 packed, handTypes@2 packed }. */
export function encodeRoyalHistory(
  winners: number[] = [1, 2, 1, 2, 1, 1, 2, 1],
  handTypes: number[] = [1, 1, 1, 1, 1, 1, 1, 1],
): Buffer {
  return encodeMessage([
    pbPackedInt32(1, winners),
    pbPackedInt32(2, handTypes),
  ]);
}

/** google.protobuf.Any { value: bytes } — field 2 only (type_url omitted). */
export function encodeAnyValue(value: Buffer): Buffer {
  return encodeMessage([pbBytes(2, value)]);
}

/** Hilo / Royal-battle outer ClientMsg envelope. */
export function encodeClientMsg(msgId: number, body: Buffer = Buffer.alloc(0)): Buffer {
  return encodeMessage([pbInt32(1, msgId), pbBytes(2, encodeAnyValue(body))]);
}

export function decodeClientMsg(buf: Buffer): { msgId: number; body: Buffer } {
  const f = decodeFields(buf);
  const msgId = Number(f[1] ?? 0);
  const anyRaw = (f[1002] as Buffer) || Buffer.alloc(0);
  if (anyRaw.length) {
    const anyFields = decodeFields(anyRaw);
    const value = (anyFields[1002] as Buffer) || Buffer.alloc(0);
    return { msgId, body: value };
  }
  return { msgId, body: Buffer.alloc(0) };
}
