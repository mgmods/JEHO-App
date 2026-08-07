/** Minimal protobuf encode/decode for Mikoo hash-game messages. */

import {
  BOUNTY_AREA_RATIOS,
  BOUNTY_BET_CHIPS,
  CAMEL_RACING_RATIOS,
  GREEDY_BOX_RATIOS,
  LUCK_CAR_RATIOS_MILLI,
  LUCKY77_AREA_RATIOS,
  LUCKY77_BET_CHIPS,
  MIKOO_BET_CHIPS,
  MIKOO_CRASH_CHIPS,
  MIKOO_WIN_MULTS,
  chipsList,
  lucky77ChipsList,
  randomWinMult,
} from './mikoo-game-economy';

export { CAMEL_RACING_RATIOS };

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

function readVarint(buf: Buffer, offset: number): { value: number; next: number } {
  let value = 0;
  let shift = 0;
  let i = offset;
  while (i < buf.length) {
    const b = buf[i++];
    value |= (b & 0x7f) << shift;
    if ((b & 0x80) === 0) break;
    shift += 7;
  }
  return { value, next: i };
}

function tag(field: number, wire: number): Buffer {
  return writeVarint((field << 3) | wire);
}

export function pbInt32(field: number, value: number): Buffer {
  return Buffer.concat([tag(field, 0), writeVarint(value | 0)]);
}

export function pbString(field: number, value: string): Buffer {
  const s = Buffer.from(value ?? '', 'utf8');
  return Buffer.concat([tag(field, 2), writeVarint(s.length), s]);
}

export function pbDouble(field: number, value: number): Buffer {
  const b = Buffer.alloc(8);
  b.writeDoubleLE(Number(value) || 0, 0);
  return Buffer.concat([tag(field, 1), b]);
}

export function pbUInt32(field: number, value: number): Buffer {
  return Buffer.concat([tag(field, 0), writeVarint((value >>> 0) || 0)]);
}

export function pbUInt64(field: number, value: number): Buffer {
  return Buffer.concat([tag(field, 0), writeVarint(Math.max(0, Math.floor(Number(value) || 0)))]);
}

export function pbFloat(field: number, value: number): Buffer {
  const b = Buffer.alloc(4);
  b.writeFloatLE(Number(value) || 0, 0);
  return Buffer.concat([tag(field, 5), b]);
}

export function encodeMessage(parts: Buffer[]): Buffer {
  return Buffer.concat(parts.filter(Boolean));
}

export function decodeFields(buf: Buffer): Record<number, unknown> {
  const out: Record<number, unknown> = {};
  let i = 0;
  while (i < buf.length) {
    const tagRead = readVarint(buf, i);
    i = tagRead.next;
    const field = tagRead.value >>> 3;
    const wire = tagRead.value & 7;
    if (wire === 0) {
      const v = readVarint(buf, i);
      i = v.next;
      out[field] = v.value;
    } else if (wire === 1) {
      out[field] = buf.readDoubleLE(i);
      i += 8;
    } else if (wire === 2) {
      const lenRead = readVarint(buf, i);
      i = lenRead.next;
      const slice = buf.slice(i, i + lenRead.value);
      i += lenRead.value;
      // keep both string and raw for nested messages
      out[field] = slice.toString('utf8');
      out[field + 1000] = slice;
    } else if (wire === 5) {
      out[field] = buf.readFloatLE(i);
      i += 4;
    } else {
      break;
    }
  }
  return out;
}

export function decodeLoginReq(buf: Buffer) {
  const f = decodeFields(buf);
  return {
    playerId: Number(f[1] ?? 0),
    ticket: String(f[2] ?? ''),
    entrance: Number(f[3] ?? 0),
    gameType: Number(f[9] ?? 0),
    roomId: String(f[6] ?? ''),
  };
}

/**
 * BetReq layouts:
 * - pirate-king: money@1, roomId@2(int), ticket@3(string), gameType@4
 * - olympians / cleopatra / sugar: money@1, ticket@2(string), …
 * - 7updown / multi: area@1, money@2, ticket@3
 */
export function decodeBetReq(buf: Buffer, style?: 'pirate' | 'multi' | 'default') {
  const f = decodeFields(buf);
  const f1 = Number(f[1] ?? 0);
  const f2 = f[2];
  const f3 = f[3];
  const ticketStr = typeof f2 === 'string' ? f2 : String(f3 ?? '');

  if (style === 'pirate') {
    return {
      areaId: 0,
      money: f1,
      ticket: typeof f3 === 'string' ? String(f3) : ticketStr,
      roomId: Number(f2 ?? 0),
      gameType: Number(f[4] ?? 0),
    };
  }

  if (style === 'multi') {
    return {
      areaId: f1,
      money: Number(f2 ?? 0) || Number(f3 ?? 0),
      ticket: typeof f3 === 'string' ? String(f3) : ticketStr,
      roomId: Number(f[4] ?? 0),
      gameType: Number(f[5] ?? 0),
    };
  }

  // Default spin: money@1, ticket@2(string)
  if (typeof f2 === 'string' && f2.length > 0) {
    return {
      areaId: 0,
      money: f1,
      ticket: f2,
      roomId: Number(f3 ?? 0),
      gameType: Number(f[4] ?? 0),
    };
  }

  // Pirate-like without explicit style (roomId@2 + ticket@3)
  if (typeof f2 !== 'string' && typeof f3 === 'string' && f3.length > 0) {
    return {
      areaId: 0,
      money: f1,
      ticket: String(f3),
      roomId: Number(f2 ?? 0),
      gameType: Number(f[4] ?? 0),
    };
  }

  return {
    areaId: f1,
    money: Number(f2 ?? 0) || Number(f3 ?? 0),
    ticket: typeof f3 === 'string' ? String(f3) : ticketStr,
    roomId: Number(f[4] ?? 0),
    gameType: Number(f[5] ?? 0),
  };
}

/** DoSlotsReq / DoFortuneGemsReq: cost, roomId, ticket */
export function decodeCostBetReq(buf: Buffer) {
  const f = decodeFields(buf);
  return {
    money: Math.max(0, Math.floor(Number(f[1] ?? 0))),
    roomId: Number(f[2] ?? 0),
    ticket: typeof f[3] === 'string' ? String(f[3]) : '',
  };
}

export function encodeLoginRes(data: {
  code: number;
  desc: string;
  playerId?: number;
  tableId?: string;
  name?: string;
  head?: string;
  userMoney: number;
  tipType?: number;
}): Buffer {
  const raw = (data.name ?? '').trim();
  const name =
    !raw || /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(raw) ? 'Player' : raw;
  const parts = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
  ];
  if (data.playerId != null) parts.push(pbInt32(3, data.playerId));
  if (data.tableId) parts.push(pbString(4, data.tableId));
  // Always send name — omitting it makes some clients fall back to UUID/erbanNo.
  parts.push(pbString(5, name));
  parts.push(pbString(6, data.head ?? ''));
  parts.push(pbDouble(7, Math.max(0, Number(data.userMoney) || 0)));
  // erbanNo = short public show-id (never UUID)
  parts.push(pbString(8, data.playerId != null ? String(data.playerId) : ''));
  parts.push(pbInt32(9, data.tipType ?? 0));
  return encodeMessage(parts);
}

export function encodeMatchRes(data: { code: number; desc: string; tableId?: string }): Buffer {
  const parts = [pbInt32(1, data.code), pbString(2, data.desc)];
  if (data.tableId) parts.push(pbString(3, data.tableId));
  return encodeMessage(parts);
}

export function encodeGetUserDataRes(data: {
  code: number;
  desc: string;
  userMoney: number;
  name?: string;
  head?: string;
  id?: number;
  tipType?: number;
}): Buffer {
  // Layout B — crash / luck-car (legacy default)
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbInt32(4, data.id ?? 0),
    pbString(5, data.name ?? 'Player'),
    pbString(6, data.head ?? ''),
    pbDouble(7, data.userMoney),
  ]);
}

/** Per-game GetUserDataRes — wrong layout zeroes/misplaces coin HUD after login. */
export function encodeGetUserDataFor(
  gameSlug: string,
  data: {
    code: number;
    desc: string;
    userMoney: number;
    name?: string;
    head?: string;
    id?: number;
    tipType?: number;
  },
): Buffer {
  const slug = (gameSlug || '').toLowerCase();
  const rawName = (data.name ?? '').trim();
  const name =
    !rawName || /^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(rawName) ? 'Player' : rawName;
  const head = data.head ?? '';
  const id = data.id ?? 0;
  const tip = data.tipType ?? 0;

  // Layout C — fortune-slot / Fortune Gems
  if (slug === 'fortune-slot') {
    return encodeMessage([
      pbUInt32(1, data.code),
      pbString(2, name),
      pbString(3, head),
      pbDouble(4, data.userMoney),
      pbInt32(5, 0), // isNewUser
      pbUInt32(6, 0), // chargeLv
    ]);
  }

  // Layout D — greedy-box (money is uint64, not double)
  if (slug === 'greedy-box') {
    return encodeMessage([
      pbUInt32(1, data.code),
      pbString(2, data.desc),
      pbString(3, name),
      pbString(4, head),
      pbUInt64(5, Math.max(0, Math.floor(data.userMoney))),
    ]);
  }

  // bounty-football: code@1 desc@2 id@3 name@4 head@5 erbanNo@7 userMoney@9(double)
  if (slug === 'bounty-football') {
    return encodeMessage([
      pbInt32(1, data.code),
      pbString(2, data.desc),
      pbInt32(3, id),
      pbString(4, name),
      pbString(5, head),
      pbInt32(7, id),
      pbDouble(9, data.userMoney),
    ]);
  }

  // Layout B — crash / luck-car / megaways (tipType before id)
  if (slug === 'crash' || slug === 'luck-car' || slug === 'megaways-slots') {
    return encodeMessage([
      pbInt32(1, data.code),
      pbString(2, data.desc),
      pbInt32(3, tip),
      pbInt32(4, id),
      pbString(5, name),
      pbString(6, head),
      pbDouble(7, data.userMoney),
      ...(slug === 'megaways-slots' ? [pbInt32(8, id)] : []),
    ]);
  }

  // sugar-rush / lucky77 / camel-racing: money@6, erbanNo@7, tipType@8
  if (slug === 'sugar-rush' || slug === 'lucky77' || slug === 'camel-racing') {
    return encodeMessage([
      pbInt32(1, data.code),
      pbString(2, data.desc),
      pbInt32(3, id),
      pbString(4, name),
      pbString(5, head),
      pbDouble(6, data.userMoney),
      pbInt32(7, id),
      pbInt32(8, tip),
    ]);
  }

  // Layout A — olympians / line-slots / pirate / cleopatra-slots / 7updown
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, id),
    pbString(4, name),
    pbString(5, head),
    pbDouble(6, data.userMoney),
    pbInt32(7, tip),
  ]);
}

/** Packed repeated uint64 (length-delimited) — greedy availableChips. */
export function pbPackedUInt64(field: number, values: number[]): Buffer {
  const payload = Buffer.concat(
    values.map((v) => writeVarint(Math.max(0, Math.floor(Number(v) || 0)))),
  );
  return Buffer.concat([tag(field, 2), writeVarint(payload.length), payload]);
}

/** Packed repeated uint32 — greedy bingoIcon / resultIcons. */
export function pbPackedUInt32(field: number, values: number[]): Buffer {
  const payload = Buffer.concat(values.map((v) => writeVarint((v >>> 0) || 0)));
  return Buffer.concat([tag(field, 2), writeVarint(payload.length), payload]);
}

/** greedy-box TableInfoRes — unlocks table UI after login.
 * Client fields: state@1 timeLeft@2 curTurn@3 chips@4 boxList@5 bingoIcon@6
 * betTotal@7 totalGain@9 todayWin@10 betRank@11 userMoney@12 lastChips@13
 * totalBet@17 curBingoIcon@18. Field 11 is RankInfo[], never a scalar. */
export function encodeGreedyTableInfoRes(data: {
  state: number;
  timeLeft: number;
  curTurn: number;
  userMoney: number;
  chips?: number[];
}): Buffer {
  const chips = data.chips ?? chipsList();
  const boxes: Buffer[] = [];
  const ratios = [...GREEDY_BOX_RATIOS];
  for (let i = 0; i < 8; i++) {
    const box = encodeMessage([
      pbUInt32(1, i + 1),
      pbString(2, `box${i + 1}`),
      pbUInt32(3, ratios[i] || 2),
    ]);
    boxes.push(Buffer.concat([tag(5, 2), writeVarint(box.length), box]));
  }
  const money = Math.max(0, Math.floor(data.userMoney));
  return encodeMessage([
    pbUInt32(1, data.state),
    pbUInt32(2, Math.max(0, data.timeLeft | 0)),
    pbUInt32(3, data.curTurn),
    pbPackedUInt64(4, chips),
    ...boxes,
    pbPackedUInt32(6, [1, 2, 3, 4, 5, 6, 7, 8]),
    pbUInt64(9, 0), // totalGain
    pbUInt64(10, 0), // todayWin
    // betRank@11 omitted (empty)
    pbUInt64(12, money),
    pbUInt64(13, chips[0] || 100), // lastChips = last selected chip
    pbUInt64(17, 0), // totalBet
    pbUInt32(18, 0), // curBingoIcon
  ]);
}

/** greedy-box GetRankDataRes: code@1 desc@2 rankList@3 RankInfo{uid,name,head,score,rank}. */
export function encodeGreedyGetRankDataRes(
  ranks: Array<{ playerId?: number; name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [pbUInt32(1, 0), pbString(2, 'OK')];
  ranks.forEach((r, i) => {
    const row = encodeMessage([
      pbUInt64(1, Math.max(0, Math.floor(r.playerId ?? 0))),
      pbString(2, r.name || 'Player'),
      pbString(3, r.head || ''),
      pbUInt64(4, Math.max(0, Math.floor(r.winMoney))),
      pbUInt32(5, i + 1),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  });
  return encodeMessage(parts);
}

/** greedy-box GetUserRecordRes: code@1 desc@2 recordData@3[]. Empty list clears loading. */
export function encodeGreedyGetUserRecordRes(): Buffer {
  return encodeMessage([pbUInt32(1, 0), pbString(2, 'OK')]);
}

/** greedy-box StartBetBroadcast: state, betTime, curTurn. */
export function encodeGreedyStartBetBroadcast(state: number, betTime: number, curTurn: number): Buffer {
  return encodeMessage([
    pbUInt32(1, state),
    pbUInt32(2, Math.max(0, betTime | 0)),
    pbUInt32(3, curTurn),
  ]);
}

/** greedy-box BetRes. */
export function encodeGreedyBetRes(data: {
  code: number;
  desc: string;
  userMoney: number;
  tipType?: number;
  iconId?: number;
  betMoney?: number;
}): Buffer {
  const parts: Buffer[] = [
    pbUInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbUInt64(4, Math.max(0, Math.floor(data.userMoney))),
  ];
  if (data.iconId != null && data.iconId > 0) {
    const area = encodeMessage([
      pbUInt32(1, data.iconId | 0),
      pbUInt64(2, Math.max(0, Math.floor(data.betMoney ?? 0))),
    ]);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(area.length), area]));
  }
  parts.push(pbUInt32(6, data.iconId ?? 0));
  return encodeMessage(parts);
}

/** greedy-box OtherPlayerBetBroadcast: curBet@1 BetArea, uid@2. */
export function encodeGreedyOtherPlayerBetBroadcast(data: {
  icon: number;
  money: number;
  uid: number;
}): Buffer {
  const area = encodeMessage([
    pbUInt32(1, data.icon | 0),
    pbUInt64(2, Math.max(0, Math.floor(data.money))),
  ]);
  return encodeMessage([
    Buffer.concat([tag(1, 2), writeVarint(area.length), area]),
    pbInt32(2, data.uid | 0),
  ]);
}

/** greedy-box ResultBroadcast after round settle. */
export function encodeGreedyResultBroadcast(data: {
  settleTime: number;
  curTurn: number;
  userMoney: number;
  winMoney: number;
  bingoIcon: number;
}): Buffer {
  return encodeMessage([
    pbUInt32(1, 3), // settle state
    pbUInt32(2, data.settleTime),
    pbUInt32(3, data.curTurn),
    pbUInt64(4, 0),
    pbUInt64(5, Math.max(0, Math.floor(data.winMoney))),
    pbUInt64(6, Math.max(0, Math.floor(data.userMoney))),
    pbUInt64(7, Math.max(0, Math.floor(data.winMoney))),
    pbUInt32(8, data.bingoIcon),
    pbPackedUInt32(9, [data.bingoIcon]),
  ]);
}

/** BountyFootball (gameType 100) — multi-icon bet table. 10 areas. */
export const BOUNTY_FOOTBALL_RATIOS = [...BOUNTY_AREA_RATIOS];
export const BOUNTY_FOOTBALL_CHIPS = [...BOUNTY_BET_CHIPS];

function pbBool(field: number, value: boolean): Buffer {
  return Buffer.concat([tag(field, 0), writeVarint(value ? 1 : 0)]);
}

function pbPackedInt32(field: number, values: number[]): Buffer {
  const payload = Buffer.concat(values.map((v) => writeVarint(v | 0)));
  return Buffer.concat([tag(field, 2), writeVarint(payload.length), payload]);
}

function encodeBountyBetStu(iconId: number, money: number): Buffer {
  return encodeMessage([
    pbInt32(1, iconId | 0),
    pbDouble(2, Math.max(0, Number(money) || 0)),
  ]);
}

function encodeBountyOpenAward(data: {
  name: string;
  head: string;
  totalBet: number;
  totalGain: number;
}): Buffer {
  return encodeMessage([
    pbString(1, data.name || 'Player'),
    pbString(2, data.head || ''),
    pbInt32(3, Math.max(0, Math.floor(data.totalBet))),
    pbInt32(4, Math.max(0, Math.floor(data.totalGain))),
  ]);
}

function encodeBountyAnimateTime(turnTime = 4, rankTime = 2): Buffer {
  return encodeMessage([pbDouble(1, turnTime), pbDouble(2, rankTime)]);
}

function encodeBountyBetStuArr(bets: Array<{ iconId: number; money: number }>): Buffer {
  const parts: Buffer[] = [];
  for (const b of bets) {
    const row = encodeBountyBetStu(b.iconId, b.money);
    parts.push(Buffer.concat([tag(1, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** bounty-football TableInfoRes — unlocks board after login. */
export function encodeBountyFootballTableInfoRes(data: {
  state: number;
  timeLeft: number;
  curTurn: number;
  history?: number[];
  allAreaBets?: Array<{ iconId: number; money: number }>;
  myBets?: Array<{ iconId: number; money: number }>;
  todayWin?: number;
  totalGain?: number;
  myTotalBet?: number;
  totalBet?: number;
  curTurnRewardIcon?: number;
  /** Wheel light stop 1..16 (never 0). */
  turnPos?: number;
}): Buffer {
  const chips = BOUNTY_FOOTBALL_CHIPS;
  const ratios = BOUNTY_FOOTBALL_RATIOS;
  const history = (data.history ?? []).slice(-12);
  const turnPos = Math.max(1, Math.min(16, data.turnPos || 1));
  const parts: Buffer[] = [
    pbInt32(1, data.state | 0),
    pbUInt32(2, Math.max(0, data.timeLeft | 0)),
    Buffer.concat([
      tag(3, 2),
      writeVarint(encodeBountyAnimateTime().length),
      encodeBountyAnimateTime(),
    ]),
  ];
  for (const b of data.allAreaBets ?? []) {
    const row = encodeBountyBetStu(b.iconId, b.money);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(row.length), row]));
  }
  parts.push(pbPackedInt32(5, chips));
  parts.push(pbInt32(6, turnPos));
  if (history.length) parts.push(pbPackedInt32(7, history));
  parts.push(pbDouble(8, Math.max(0, data.todayWin ?? 0)));
  for (const b of data.myBets ?? []) {
    const row = encodeBountyBetStu(b.iconId, b.money);
    parts.push(Buffer.concat([tag(9, 2), writeVarint(row.length), row]));
  }
  parts.push(pbDouble(10, Math.max(0, data.totalGain ?? 0)));
  parts.push(pbInt32(12, data.curTurnRewardIcon ?? 0));
  parts.push(pbInt32(13, data.curTurn | 0));
  parts.push(pbPackedUInt32(15, ratios));
  parts.push(pbDouble(18, Math.max(0, data.myTotalBet ?? 0)));
  parts.push(pbDouble(19, Math.max(0, data.totalBet ?? 0)));
  parts.push(pbInt32(20, 0)); // lastSpeed
  parts.push(pbBool(21, false)); // hasRepeat
  return encodeMessage(parts);
}

export function encodeBountyFootballStartBetBroadcast(
  state: number,
  betTime: number,
  curTurn: number,
): Buffer {
  return encodeMessage([
    pbInt32(1, state),
    pbInt32(2, Math.max(0, betTime | 0)),
    pbInt32(3, curTurn | 0),
    pbBool(4, false),
  ]);
}

export function encodeBountyFootballBetRes(data: {
  code: number;
  desc: string;
  userMoney: number;
  tipType?: number;
  allAreaBets?: Array<{ iconId: number; money: number }>;
  curBet?: { iconId: number; money: number };
  myBets?: Array<{ iconId: number; money: number }>;
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, Math.max(0, Number(data.userMoney) || 0)),
  ];
  if (data.allAreaBets?.length) {
    const arr = encodeBountyBetStuArr(data.allAreaBets);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(arr.length), arr]));
  }
  if (data.curBet) {
    const row = encodeBountyBetStu(data.curBet.iconId, data.curBet.money);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  if (data.myBets?.length) {
    const arr = encodeBountyBetStuArr(data.myBets);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(arr.length), arr]));
  }
  parts.push(pbInt32(7, data.tipType ?? 0));
  return encodeMessage(parts);
}

export function encodeBountyFootballOtherPlayerBetBroadcast(data: {
  allAreaBets: Array<{ iconId: number; money: number }>;
  playerId: number;
  bet: { iconId: number; money: number };
}): Buffer {
  const parts: Buffer[] = [];
  for (const b of data.allAreaBets) {
    const row = encodeBountyBetStu(b.iconId, b.money);
    parts.push(Buffer.concat([tag(1, 2), writeVarint(row.length), row]));
  }
  const userBet = encodeMessage([
    pbInt32(1, data.playerId | 0),
    Buffer.concat([
      tag(2, 2),
      writeVarint(encodeBountyBetStu(data.bet.iconId, data.bet.money).length),
      encodeBountyBetStu(data.bet.iconId, data.bet.money),
    ]),
  ]);
  parts.push(Buffer.concat([tag(2, 2), writeVarint(userBet.length), userBet]));
  return encodeMessage(parts);
}

export function encodeBountyFootballResultBroadcast(data: {
  state?: number;
  betTime: number;
  betRank?: Array<{ name: string; head: string; totalBet: number; totalGain: number }>;
  myReward?: { name: string; head: string; totalBet: number; totalGain: number };
  curTurnRewardIcon: number;
  turnPos?: number;
  curTurn: number;
  userMoney: number;
  todayWin?: number;
  rewardHistory?: number[];
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.state ?? 0), // SETTLEMENT
    pbInt32(2, Math.max(0, data.betTime | 0)),
  ];
  for (const r of data.betRank ?? []) {
    const row = encodeBountyOpenAward(r);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  if (data.myReward) {
    const row = encodeBountyOpenAward(data.myReward);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(row.length), row]));
  }
  // Field 5 unused; curTurnRewardIcon@6 turnPos@7 curTurn@8 userMoney@9 todayWin@10 history@11
  parts.push(pbInt32(6, data.curTurnRewardIcon | 0));
  parts.push(pbInt32(7, data.turnPos ?? 0));
  parts.push(pbInt32(8, data.curTurn | 0));
  parts.push(pbDouble(9, Math.max(0, Number(data.userMoney) || 0)));
  parts.push(pbInt32(10, Math.max(0, Math.floor(data.todayWin ?? 0))));
  if (data.rewardHistory?.length) {
    parts.push(pbPackedInt32(11, data.rewardHistory));
  }
  return encodeMessage(parts);
}

export function encodeBountyFootballGetRankDataRes(
  ranks: Array<{ playerId?: number; name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const r of ranks) {
    const row = encodeMessage([
      pbInt32(1, Math.max(0, Math.floor(r.playerId ?? 0))),
      pbString(2, r.name || 'Player'),
      pbString(3, r.head || ''),
      pbDouble(4, Math.max(0, Number(r.winMoney) || 0)),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** 7updown TableInfo — different from crash. */
const SEVEN_UP_AREA_RATIOS: Record<number, number> = { 1: 2, 2: 5, 3: 2 };
const SEVEN_UP_CHIPS = chipsList();
const SEVEN_UP_CHIP_CSV = MIKOO_BET_CHIPS.join(',');

function encode7UpBetArea(data: {
  id: number;
  totalBet?: number;
  myBet?: number;
  ratio?: number;
}): Buffer {
  const id = data.id | 0;
  return encodeMessage([
    pbUInt32(1, id),
    pbUInt32(2, Math.max(0, Math.floor(data.totalBet ?? 0))),
    pbUInt32(3, Math.max(0, Math.floor(data.myBet ?? 0))),
    pbUInt32(4, Math.max(0, Math.floor(data.ratio ?? SEVEN_UP_AREA_RATIOS[id] ?? 2))),
  ]);
}

function encode7UpBetAreaAll(id: number, totalBet: number): Buffer {
  return encodeMessage([
    pbUInt32(1, id | 0),
    pbUInt32(2, Math.max(0, Math.floor(totalBet))),
  ]);
}

/** 7updown UpdateBetPoolBroadcast — realtime pooled totals on each area. */
export function encode7UpUpdateBetPoolBroadcast(data: {
  totalBet: number;
  betInfo: Array<{ id: number; totalBet: number }>;
}): Buffer {
  const parts: Buffer[] = [pbUInt64(1, Math.max(0, Math.floor(data.totalBet)))];
  for (const a of data.betInfo ?? []) {
    const row = encode7UpBetAreaAll(a.id, a.totalBet);
    parts.push(Buffer.concat([tag(2, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

export function encode7UpUpdatePlayerNumBroadcast(num: number): Buffer {
  return encodeMessage([pbUInt64(1, Math.max(0, Math.floor(num)))]);
}

/**
 * 7updown GetRankDataRes: code@1, desc@2, rankData@3[], tipType@4
 * (luck-car uses tipType@3 / rankData@4 — do NOT reuse that encoder here.)
 */
export function encode7UpGetRankDataRes(
  ranks: Array<{ name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const r of ranks) {
    const row = encodeMessage([
      pbString(1, r.name || 'Player'),
      pbString(2, r.head || ''),
      pbDouble(3, Math.max(0, Number(r.winMoney) || 0)),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(4, 0)); // tipType required
  return encodeMessage(parts);
}

/** 7updown GetUserRecordRes: code, desc, recordData[], tipType. */
export function encode7UpGetUserRecordRes(
  records: Array<{
    time?: number;
    betInfo?: string;
    returnMoney?: number;
    result?: number;
    round?: number;
  }> = [],
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const rec of records) {
    const row = encodeMessage([
      pbInt32(1, Math.max(0, Math.floor(rec.time ?? Date.now() / 1000))),
      pbString(2, rec.betInfo ?? ''),
      pbDouble(3, Math.max(0, Number(rec.returnMoney) || 0)),
      pbInt32(4, Math.max(0, Math.floor(rec.result ?? 0))),
      pbInt32(5, Math.max(0, Math.floor(rec.round ?? 0))),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(4, 0));
  return encodeMessage(parts);
}

export function encodeTableInfo7UpDown(data: {
  state: number;
  betTime: number;
  waitTime?: number;
  playerNum: number;
  timeLeft: number;
  totalBet?: number;
  history?: number[];
  /** Area pools — down=1, seven=2, up=3. */
  betInfo?: Array<{ id: number; totalBet?: number; myBet?: number; ratio?: number }>;
}): Buffer {
  const history = data.history ?? [1, 2, 3, 1, 3, 2, 1, 3];
  // Client enum: betting=1, over=2 only — never send 3.
  const state = data.state === 1 ? 1 : 2;
  const parts: Buffer[] = [
    pbInt32(1, state),
    pbUInt32(2, data.betTime),
    pbUInt32(3, data.waitTime ?? 3),
    pbUInt32(4, data.playerNum),
  ];
  for (const h of history) parts.push(pbUInt32(5, h));
  parts.push(pbUInt32(6, Math.max(0, data.timeLeft | 0)));
  parts.push(pbUInt32(7, Math.max(0, Math.floor(data.totalBet ?? 0))));
  // betInfo@8 — area cards + ratios (required for cup/area taps)
  const areas =
    data.betInfo?.length
      ? data.betInfo
      : ([1, 2, 3] as const).map((id) => ({
          id,
          totalBet: 0,
          myBet: 0,
          ratio: SEVEN_UP_AREA_RATIOS[id],
        }));
  for (const a of areas) {
    const area = encode7UpBetArea(a);
    parts.push(Buffer.concat([tag(8, 2), writeVarint(area.length), area]));
  }
  // betConf@9 chips: id, betMin, conf csv
  const chips = [
    { id: 1, min: 50, conf: SEVEN_UP_CHIP_CSV },
    { id: 2, min: 100, conf: SEVEN_UP_CHIP_CSV },
  ];
  for (const c of chips) {
    const row = encodeMessage([
      pbUInt32(1, c.id),
      pbUInt32(2, c.min),
      pbString(3, c.conf),
    ]);
    parts.push(Buffer.concat([tag(9, 2), writeVarint(row.length), row]));
  }
  // AvailableChips@14 int32 — unlocks expanded bet tray
  for (const chip of SEVEN_UP_CHIPS) {
    parts.push(pbInt32(14, chip));
  }
  return encodeMessage(parts);
}

/** 7updown BetRsp — includes betInfo so chips land on areas. */
export function encode7UpBetRsp(data: {
  code: number;
  desc: string;
  selfMoney: number;
  areaId?: number;
  betMoney?: number;
  tipType?: number;
  betInfo?: Array<{ id: number; totalBet?: number; myBet?: number; ratio?: number }>;
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.selfMoney),
    pbInt32(4, data.areaId ?? 0),
    pbInt32(5, Math.max(0, Math.floor(data.betMoney ?? 0))),
  ];
  for (const a of data.betInfo ?? []) {
    const row = encode7UpBetArea(a);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(7, data.tipType ?? 0));
  return encodeMessage(parts);
}

export function encodeBetRsp(data: {
  code: number;
  desc: string;
  selfMoney: number;
  betMoney?: number;
  tipType?: number;
  areaId?: number;
}): Buffer {
  const parts = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbDouble(4, data.selfMoney),
    pbUInt64(5, data.betMoney ?? 0),
  ];
  if (data.areaId) parts.push(pbInt32(6, data.areaId));
  return encodeMessage(parts);
}

export function encodeHeartBeatRsp(time: number): Buffer {
  return encodeMessage([pbUInt64(1, time)]);
}

export function encodeStartBetBroadcast(betTime: number): Buffer {
  return encodeMessage([pbInt32(1, betTime)]);
}

export function encodeUpdateRatioBroadcast(ratio: number, flyTime: number): Buffer {
  // Crash client: curMult = ratio/100 — send centi (150 = 1.50x).
  return encodeMessage([pbFloat(1, ratio), pbUInt64(2, flyTime)]);
}

export function encodeGameOverBroadcast(ratio: number, settleTime: number): Buffer {
  // Crash client: saveResult(ratio/100) — send centi.
  return encodeMessage([pbFloat(1, ratio), pbUInt32(2, settleTime)]);
}

export function encodeCashoutRsp(data: {
  code: number;
  desc: string;
  ratio: number;
  winMoney: number;
  selfMoney: number;
  tipType?: number;
}): Buffer {
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbFloat(4, data.ratio),
    pbUInt64(5, data.winMoney),
    pbDouble(6, data.selfMoney),
  ]);
}

/** Crash auto-cashout toggle ack — type is required by client. */
export function encodeCashoutConfRsp(data: {
  code?: number;
  desc?: string;
  type: number;
  ratio?: number;
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.code ?? 0),
    pbString(2, data.desc ?? 'OK'),
    pbInt32(3, data.type | 0),
  ];
  if (data.ratio != null) parts.push(pbInt32(4, Math.max(0, Math.floor(data.ratio))));
  return encodeMessage(parts);
}

/** Crash UpdateBetPoolBroadcast — totalBet, playerId, bet amount. */
export function encodeCrashUpdateBetPoolBroadcast(data: {
  totalBet: number;
  playerId: number;
  bet: number;
}): Buffer {
  return encodeMessage([
    pbUInt64(1, Math.max(0, Math.floor(data.totalBet))),
    pbUInt64(2, Math.max(0, Math.floor(data.playerId))),
    pbUInt64(3, Math.max(0, Math.floor(data.bet))),
  ]);
}

/** Crash SomeoneCashoutBroadcast — ratio in centi, playerId. */
export function encodeSomeoneCashoutBroadcast(ratioCenti: number, playerId: number): Buffer {
  return encodeMessage([
    pbFloat(1, ratioCenti),
    pbInt32(2, playerId | 0),
  ]);
}

export function encodeTableInfoCrash(data: {
  state: number;
  playerNum: number;
  timeLeft: number;
  ratio?: number;
  history?: number[];
  totalBet?: number;
  selfBet?: number;
  flyTime?: number;
  cashOutMoney?: number;
  cashRatio?: number;
  cashOutConfType?: number;
  cashOutConfRatio?: number;
  availableChips?: number[];
}): Buffer {
  // Crash TableInfo (strict):
  // 1 state, 2 playerNum, 3 history[] float, 4 timeLeft,
  // 5 argAlpha, 6 argBeta, 7 cashOutMoney uint64, 8 cashRatio int32,
  // 9 totalBet, 10 selfBet, 11 ratio (raw x), 12 flyTime,
  // 13 cashOutConfType, 14 cashOutConfRatio (centi), 15 availableChips[]
  const parts: Buffer[] = [
    pbInt32(1, data.state | 0),
    pbUInt32(2, Math.max(0, data.playerNum | 0)),
  ];
  for (const h of data.history ?? []) {
    parts.push(pbFloat(3, Number(h) || 0));
  }
  parts.push(pbUInt32(4, Math.max(0, data.timeLeft | 0)));
  // Match client defaults BASENUM=10, INDEXNUM=1.4
  parts.push(pbFloat(5, 10));
  parts.push(pbFloat(6, 1.4));
  parts.push(pbUInt64(7, Math.max(0, Math.floor(data.cashOutMoney ?? 0))));
  parts.push(pbInt32(8, Math.max(0, Math.floor(data.cashRatio ?? 0))));
  parts.push(pbUInt64(9, Math.max(0, Math.floor(data.totalBet ?? 0))));
  parts.push(pbUInt64(10, Math.max(0, Math.floor(data.selfBet ?? 0))));
  parts.push(pbFloat(11, data.ratio ?? 1));
  parts.push(pbUInt64(12, Math.max(0, Math.floor(data.flyTime ?? 0))));
  parts.push(pbInt32(13, data.cashOutConfType ?? 0));
  parts.push(pbInt32(14, data.cashOutConfRatio ?? 101)); // 1.01x default
  // Crash UI only has chip0..chip3 (AMOUNDNUM=4). Extra chips → null.getChildByName freeze.
  const chips = (data.availableChips ?? [...MIKOO_CRASH_CHIPS]).slice(0, 4);
  for (const chip of chips) {
    parts.push(pbInt32(15, Math.max(1, Math.floor(Number(chip) || 0))));
  }
  return encodeMessage(parts);
}

/** Crash StartFlyBroadcast is an empty message — presence alone starts the rocket. */
export function encodeStartFlyBroadcast(_ratio = 1, _settleTime = 3): Buffer {
  return Buffer.alloc(0);
}

/** cleopatra-slots BetRes — client changeIcon needs 33 cells: cols heights [6,7,7,7,6]. */
export function encodeCleopatraBetRes(data: {
  code: number;
  desc: string;
  selfMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const win = Math.max(0, Math.floor(data.winMoney || 0));
  const rowCounts = [6, 7, 7, 7, 6];
  const winSym = 1 + Math.floor(Math.random() * 8);
  const cells: Buffer[] = [];
  for (let col = 0; col < rowCounts.length; col++) {
    const rows = rowCounts[col];
    for (let row = 0; row < rows; row++) {
      const number =
        win > 0 && row === 2 ? winSym : 1 + Math.floor(Math.random() * 9);
      const pos = encodeMessage([
        pbInt32(1, row),
        pbInt32(2, col),
        pbInt32(3, number),
        pbInt32(4, 0), // gold
      ]);
      cells.push(Buffer.concat([tag(3, 2), writeVarint(pos.length), pos]));
    }
  }
  const symbolInfo = encodeMessage([
    pbInt32(1, win),
    pbInt32(2, 0),
    ...cells,
  ]);
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.selfMoney),
    Buffer.concat([tag(4, 2), writeVarint(symbolInfo.length), symbolInfo]),
    pbInt32(5, 0),
    pbInt32(6, 0),
    pbInt32(7, data.tipType ?? 0),
  ]);
}

/** luck-car BetRsp: code,desc,tipType,selfMoney,id(int32),betMoney(int32),myBetAll(uint32). */
export function encodeLuckCarBetRsp(data: {
  code: number;
  desc: string;
  selfMoney: number;
  tipType?: number;
  areaId?: number;
  betMoney?: number;
  myBetAll?: number;
}): Buffer {
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbDouble(4, data.selfMoney),
    pbInt32(5, data.areaId ?? 0),
    pbInt32(6, Math.max(0, Math.floor(data.betMoney ?? 0))),
    pbUInt32(7, Math.max(0, Math.floor(data.myBetAll ?? data.betMoney ?? 0))),
  ]);
}

/** Olympians BetRes — 6×5 grid. Do NOT send updatePosList: bad missPos coords freeze UI (null getComponent). Win shown via client patch on empty-update path. */
export function encodeOlympiansBetRes(data: {
  code: number;
  desc: string;
  selfMoney: number;
  winMoney: number;
  tipType?: number;
  /** Actual payout mult (x2..x50) — painted on win line cells. */
  mult?: number;
}): Buffer {
  const cols = 6;
  const rows = 5;
  const win = Math.max(0, Math.floor(data.winMoney || 0));
  const winSymbol = 1 + Math.floor(Math.random() * 8);
  const paidMult =
    data.mult && data.mult > 0
      ? data.mult
      : win > 0
        ? randomWinMult()
        : 0;

  type Cell = { row: number; col: number; number: number; mul: number };
  const board: Cell[] = [];
  for (let col = 0; col < cols; col++) {
    for (let row = 0; row < rows; row++) {
      // Scatter decorative mults so players see x2 / x10 / x50 values on symbols.
      const decor =
        Math.random() < 0.18 ? randomWinMult() : 1;
      board.push({
        row,
        col,
        number: 1 + Math.floor(Math.random() * 9),
        mul: decor,
      });
    }
  }
  // Visual match line when winning — paint paid mult so UI shows the true x.
  if (win > 0) {
    for (let col = 0; col < Math.min(5, cols); col++) {
      const cell = board.find((c) => c.col === col && c.row === 2);
      if (cell) {
        cell.number = winSymbol;
        cell.mul = Math.max(1, paidMult || randomWinMult());
      }
    }
  }

  const encodePos = (c: Cell) =>
    encodeMessage([
      pbInt32(1, c.row),
      pbInt32(2, c.col),
      pbInt32(3, c.number),
      pbInt32(4, c.mul),
    ]);

  const matrixParts = board.map((c) => {
    const p = encodePos(c);
    return Buffer.concat([tag(3, 2), writeVarint(p.length), p]);
  });

  const symbolInfo = encodeMessage([
    pbInt32(1, win),
    pbInt32(2, 0),
    ...matrixParts,
  ]);

  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.selfMoney),
    pbInt32(4, win),
    Buffer.concat([tag(5, 2), writeVarint(symbolInfo.length), symbolInfo]),
    pbInt32(6, 0),
    pbInt32(7, data.tipType ?? 0),
  ]);
}

/** sugar-rush s_c_bet: code,desc,tipType,money,symbols[] */
export function encodeSugarBetRes(data: {
  code: number;
  desc: string;
  money: number;
  tipType?: number;
  winMoney?: number;
  mult?: number;
}): Buffer {
  const win = Math.max(0, Math.floor(data.winMoney ?? 0));
  const paidMult = data.mult && data.mult > 0 ? data.mult : win > 0 ? randomWinMult() : 1;
  const matrix: Buffer[] = [];
  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 6; col++) {
      const mul =
        win > 0 && row === 2 && col < 5
          ? Math.max(1, paidMult)
          : Math.random() < 0.15
            ? randomWinMult()
            : 1;
      matrix.push(
        encodeMessage([
          pbInt32(1, row),
          pbInt32(2, col),
          pbInt32(3, 1 + Math.floor(Math.random() * 8)),
          pbInt32(4, mul),
        ]),
      );
    }
  }
  const symbolInfo = encodeMessage([
    pbInt32(1, win),
    pbInt32(2, 0),
    ...matrix.map((m) => Buffer.concat([tag(3, 2), writeVarint(m.length), m])),
  ]);
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbDouble(4, data.money),
    Buffer.concat([tag(5, 2), writeVarint(symbolInfo.length), symbolInfo]),
  ]);
}

/**
 * megaways s_c_bet: code,desc,money,tipType,symbols[] where each symbolInfo.matrixProtoList
 * is numList { list: int32[] } (one reel per list) — NOT olympians posInfo.
 */
export function encodeMegawaysBetRes(data: {
  code: number;
  desc: string;
  money: number;
  tipType?: number;
  winMoney?: number;
}): Buffer {
  const win = Math.max(0, Math.floor(data.winMoney ?? 0));
  const reelCount = 6;
  const heights = [5, 6, 5, 7, 6, 5];
  const winSym = 1 + Math.floor(Math.random() * 8);
  const reels: Buffer[] = [];
  for (let col = 0; col < reelCount; col++) {
    const h = heights[col] ?? 5;
    const nums: number[] = [];
    for (let row = 0; row < h; row++) {
      nums.push(
        win > 0 && row === Math.floor(h / 2)
          ? winSym
          : 1 + Math.floor(Math.random() * 10),
      );
    }
    // numList: repeated int32 list@1
    const numList = encodeMessage(nums.map((n) => pbInt32(1, n)));
    reels.push(Buffer.concat([tag(3, 2), writeVarint(numList.length), numList]));
  }
  const symbolInfo = encodeMessage([
    pbInt32(1, win),
    pbInt32(2, 0),
    ...reels,
  ]);
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.money),
    pbInt32(4, data.tipType ?? 0),
    Buffer.concat([tag(5, 2), writeVarint(symbolInfo.length), symbolInfo]),
  ]);
}

/** pirate-king BetRes: iconGroup@6 + bingoLine@7 (BingoLine: lineID,icon,num) */
export function encodePirateBetRes(data: {
  code: number;
  desc: string;
  selfMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const win = Math.max(0, Math.floor(data.winMoney));
  const winIcon = 1 + Math.floor(Math.random() * 8);
  const icons: number[] = [];
  for (let i = 0; i < 15; i++) {
    icons.push(1 + Math.floor(Math.random() * 8));
  }
  // Paint a simple middle-row match when winning so bingoLine matches board.
  if (win > 0) {
    for (let col = 0; col < 5; col++) {
      icons[5 + col] = winIcon; // row1 of 3×5
    }
  }
  const parts: Buffer[] = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.selfMoney),
    pbInt32(4, win),
    ...icons.map((ic) => pbInt32(6, ic)),
  ];
  if (win > 0) {
    const bingo = encodeMessage([
      pbInt32(1, 1), // lineID
      pbInt32(2, winIcon),
      pbInt32(3, 5), // num icons on line
    ]);
    parts.push(Buffer.concat([tag(7, 2), writeVarint(bingo.length), bingo]));
  }
  parts.push(pbInt32(8, 0)); // jackPotIndex
  parts.push(pbInt32(9, data.tipType ?? 0));
  return encodeMessage(parts);
}

/** line-slots DoSlotsRsp — iconGroup@7, bingoLine@6, userMoney@8 double, tipType@9 required. */
export function encodeDoSlotsRsp(data: {
  code: number;
  desc: string;
  cost: number;
  userMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const icons: number[] = [];
  for (let i = 0; i < 15; i++) {
    icons.push(1 + Math.floor(Math.random() * 9));
  }
  const win = Math.max(0, Math.floor(data.winMoney));
  const isBingo = win > 0 ? 1 : 0;
  // Paint middle row (indices 5..9) as a 5-of-a-kind when winning.
  const winIcon = 1 + Math.floor(Math.random() * 8);
  if (isBingo) {
    for (let col = 0; col < 5; col++) icons[5 + col] = winIcon;
  }
  const parts: Buffer[] = [
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, Math.max(0, Math.floor(data.cost))),
    pbInt32(4, isBingo),
  ];
  if (isBingo) {
    // BingoLine required fields: lineID, icon, num, award, ratio
    const bingo = encodeMessage([
      pbInt32(1, 1),
      pbInt32(2, winIcon),
      pbInt32(3, 5),
      pbInt32(4, win),
      pbInt32(5, Math.max(1, Math.floor(win / Math.max(1, data.cost)))),
    ]);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(bingo.length), bingo]));
  }
  for (const ic of icons) parts.push(pbInt32(7, ic));
  parts.push(pbDouble(8, Math.max(0, Number(data.userMoney) || 0)));
  parts.push(pbInt32(9, data.tipType ?? 0));
  return encodeMessage(parts);
}

/** fortune-slot / Fortune Gems DoFortuneGemsRes */
export function encodeFortuneGemsRes(data: {
  code: number;
  desc: string;
  cost: number;
  userMoney: number;
  winMoney: number;
  tipType?: number;
  mult?: number;
}): Buffer {
  const icons: Buffer[] = [];
  // 5x3 grid typical for fortune gems
  for (let i = 0; i < 15; i++) {
    icons.push(pbUInt32(6, 1 + Math.floor(Math.random() * 8)));
  }
  const isWin = data.winMoney > 0 ? 1 : 0;
  const winMult = Math.max(0, Math.floor(data.mult ?? (isWin ? randomWinMult() : 0)));
  return encodeMessage([
    pbUInt32(1, data.code),
    pbString(2, data.desc),
    pbUInt32(3, data.cost),
    pbUInt32(4, isWin),
    pbDouble(5, data.userMoney),
    ...icons,
    pbUInt32(8, 0), // lineReturn
    pbUInt32(10, winMult), // winMultiplier — show x2..x50
    pbUInt32(11, 0), // WheelWinMoney
    pbUInt32(12, Math.max(0, Math.floor(data.winMoney))),
    pbInt32(13, data.tipType ?? 0),
  ]);
}

/** Minimal GameCfgRes for fortune gems (unlocks spin UI). */
export function encodeFortuneGameCfgRes(): Buffer {
  const betMult = MIKOO_BET_CHIPS.map((n) => pbUInt32(4, n));
  const cfg = encodeMessage([
    pbUInt32(1, 1), // gameSwitch on
    pbUInt32(2, 10000), // maxBet
    pbUInt32(3, 50), // minBet
    ...betMult,
  ]);
  return encodeMessage([
    pbUInt32(1, 0),
    pbString(2, 'OK'),
    Buffer.concat([tag(3, 2), writeVarint(cfg.length), cfg]),
  ]);
}

/**
 * line-slots GameCfgRsp — client does JSON.parse on BetMult / AutoSpinCount
 * (after stripping quotes), so they MUST be JSON array strings like "[1,2,5]".
 * Paylines are cell indices on a 3×5 board (row-major 0..14).
 */
export function encodeLineSlotsGameCfgRsp(): Buffer {
  const defaultCfg = encodeMessage([
    pbInt32(1, 50), // BasicBetLimit
    pbInt32(2, 50), // MinBet
    pbInt32(3, 3), // ScrollGrid (rows)
    pbInt32(4, 0), // BonusTrunCount
    pbString(5, JSON.stringify([...MIKOO_BET_CHIPS])), // BetMult — stake tray
    pbString(6, '[10,20,50,100]'), // AutoSpinCount — JSON array
  ]);
  const symbols: Buffer[] = [];
  // Score mults lean into product x2..x50 range (3/4/5-of-a-kind).
  const mults = [
    [2, 3, 4],
    [3, 4, 10],
    [4, 10, 20],
    [10, 20, 25],
    [20, 25, 50],
    [3, 10, 25],
    [4, 20, 50],
    [10, 25, 50],
    [2, 20, 50],
  ];
  for (let i = 1; i <= 9; i++) {
    const m = mults[i - 1] || [5, 10, 20];
    const row = encodeMessage([
      pbInt32(1, i), // SymbolID
      pbString(2, `s${i}`), // SymbolName
      pbInt32(3, 1), // SymbolType
      pbInt32(4, m[0]), // ScoreMultLine3
      pbInt32(5, m[1]), // ScoreMultLine4
      pbInt32(6, m[2]), // ScoreMultLine5
    ]);
    symbols.push(Buffer.concat([tag(2, 2), writeVarint(row.length), row]));
  }
  // Classic 9 paylines on 3×5 (cols left→right).
  const paylines = [
    '5,6,7,8,9', // mid row
    '0,1,2,3,4', // top row
    '10,11,12,13,14', // bottom row
    '0,6,12,8,4',
    '10,6,2,8,14',
    '0,1,7,3,4',
    '10,11,7,13,14',
    '5,1,2,3,9',
    '5,11,12,13,9',
  ];
  const lines: Buffer[] = [];
  for (let i = 0; i < paylines.length; i++) {
    const row = encodeMessage([
      pbInt32(1, i + 1), // lineID
      pbString(2, paylines[i]), // lineInclude
    ]);
    lines.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage([
    Buffer.concat([tag(1, 2), writeVarint(defaultCfg.length), defaultCfg]),
    ...symbols,
    ...lines,
  ]);
}

/** line-slots GetRankDataRes: code@1 desc@2 rankData@3{name,head,winMoney int64} tipType@4. */
export function encodeLineSlotsGetRankDataRes(
  ranks: Array<{ name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const r of ranks) {
    const row = encodeMessage([
      pbString(1, r.name || 'Player'),
      pbString(2, r.head || ''),
      pbInt32(3, Math.max(0, Math.floor(Number(r.winMoney) || 0))), // int64 as varint
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(4, 0)); // tipType required
  return encodeMessage(parts);
}

/** line-slots GetUserRecordRes: empty list + tipType clears history loading. */
export function encodeLineSlotsGetUserRecordRes(): Buffer {
  return encodeMessage([pbInt32(1, 0), pbString(2, 'OK'), pbInt32(4, 0)]);
}

/** megaways / sugar chip list — unlocks bet UI after splash. */
export function encodeChipCfgRes(chips: number[] = chipsList()): Buffer {
  return encodeMessage([
    pbInt32(1, 0),
    pbString(2, 'OK'),
    pbInt32(3, 0), // addPer
    ...chips.map((c) => pbInt32(4, c)),
  ]);
}

const DEFAULT_SPIN_CHIPS = chipsList();

/** olympians TableInfo: availableChips@1*, free@2, extra@3 — unlocks +/- bet (avoids NaN). */
export function encodeOlympiansTableInfo(
  chips: number[] = DEFAULT_SPIN_CHIPS,
  free = 1,
  extra = 1,
): Buffer {
  return encodeMessage([
    ...chips.map((c) => pbInt32(1, c)),
    pbInt32(2, free),
    pbInt32(3, extra),
  ]);
}

/** pirate-king TableInfo: chips@1*, jackpots@2*, lastChips@4 — closes loading / enables spin. */
export function encodePirateTableInfo(
  chips: number[] = DEFAULT_SPIN_CHIPS,
  lastChips?: number,
): Buffer {
  const last = lastChips ?? chips[0] ?? 1;
  return encodeMessage([
    ...chips.map((c) => pbInt32(1, c)),
    pbDouble(2, 0),
    pbInt32(4, last),
  ]);
}

/** cleopatra-slots TableInfo: packed chips@1, freeTimes@2, totalWin@3, lastChips@6. */
export function encodeCleopatraTableInfo(
  chips: number[] = DEFAULT_SPIN_CHIPS,
  lastChips?: number,
): Buffer {
  const last = lastChips ?? chips[0] ?? 1;
  return encodeMessage([
    pbPackedUInt64(1, chips),
    pbUInt64(2, 0),
    pbUInt64(3, 0),
    pbUInt64(6, last),
  ]);
}

/** UI ratios are milli (ratio/1000 → x2). Settlement uses real ratios from economy module. */
const LUCK_CAR_CHIPS = chipsList();

function encodeLuckCarBetArea(id: number, ratio: number, totalBet = 0, myBet = 0): Buffer {
  return encodeMessage([
    pbUInt32(1, id),
    pbUInt32(2, totalBet),
    pbUInt32(3, myBet),
    pbUInt32(4, ratio),
  ]);
}

function encodeLuckCarConf(id: number): Buffer {
  return encodeMessage([
    pbUInt32(1, id),
    pbUInt32(2, 80 + id * 3), // desertSpeed
    pbUInt32(3, 100 + id * 2), // ExpresswaySpeed
    pbUInt32(4, 90 + id), // Highway
    pbUInt32(5, 70 + id), // Dirt
    pbUInt32(6, 60 + id), // Bumpy
    pbUInt32(7, 50 + id), // Potholes
  ]);
}

function encodeLuckCarSpeed(trackID: number, carID: number, speed: number): Buffer {
  return encodeMessage([
    pbUInt32(1, trackID),
    pbUInt32(2, carID),
    pbUInt32(3, speed),
  ]);
}

/** luck-car TableInfo — GameState is ONLY betting=1, over=2 (never 3). */
export function encodeLuckCarTableInfo(data: {
  state: number;
  playerNum: number;
  timeLeft: number;
  curTurn?: number;
  totalBet?: number;
  myBets?: number[];
}): Buffer {
  // Client enum: betting=1, over=2 only.
  const state = data.state === 1 ? 1 : 2;
  const betTime = Math.max(1, data.timeLeft | 0) || 10;
  const cars = LUCK_CAR_RATIOS_MILLI.map((_, i) => i + 1);
  const myBets = data.myBets ?? new Array(8).fill(0);
  const parts: Buffer[] = [
    pbInt32(1, state),
    pbUInt32(2, Math.max(0, data.playerNum | 0)),
    pbUInt32(3, Math.max(0, data.timeLeft | 0)),
    pbUInt32(4, Math.max(0, data.totalBet ?? 0)),
  ];
  for (let i = 0; i < LUCK_CAR_RATIOS_MILLI.length; i++) {
    const area = encodeLuckCarBetArea(
      i + 1,
      LUCK_CAR_RATIOS_MILLI[i],
      0,
      myBets[i] || 0,
    );
    parts.push(Buffer.concat([tag(5, 2), writeVarint(area.length), area]));
  }
  for (let i = 0; i < LUCK_CAR_RATIOS_MILLI.length; i++) {
    const conf = encodeLuckCarConf(i + 1);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(conf.length), conf]));
  }
  for (const b of myBets) parts.push(pbUInt32(8, Math.max(0, b | 0)));
  parts.push(pbInt32(14, betTime));
  for (const c of cars) parts.push(pbInt32(15, c));
  for (const r of LUCK_CAR_RATIOS_MILLI) parts.push(pbInt32(16, r));
  parts.push(pbInt32(17, 1)); // trackType
  parts.push(pbInt32(18, 0)); // trackPos
  // Client does JSON.parse(chipsConf) then pushes into Instance.chipsConf[].
  parts.push(pbString(23, JSON.stringify(LUCK_CAR_CHIPS)));
  return encodeMessage(parts);
}

/** luck-car UpdateBetPoolBroadcast: totalBet@1 id@2 bet@3 betInfo@4[]. */
export function encodeLuckCarUpdateBetPoolBroadcast(data: {
  totalBet: number;
  playerId: number;
  bet: number;
  betInfo?: Array<{ id: number; totalBet: number; myBet?: number; ratio?: number }>;
}): Buffer {
  const parts: Buffer[] = [
    pbUInt64(1, Math.max(0, Math.floor(data.totalBet))),
    pbUInt64(2, Math.max(0, Math.floor(data.playerId))),
    pbUInt64(3, Math.max(0, Math.floor(data.bet))),
  ];
  for (const a of data.betInfo ?? []) {
    const id = Math.max(1, Math.min(8, a.id | 0));
    const area = encodeLuckCarBetArea(
      id,
      a.ratio ?? LUCK_CAR_RATIOS_MILLI[id - 1] ?? 2000,
      a.totalBet ?? 0,
      a.myBet ?? 0,
    );
    parts.push(Buffer.concat([tag(4, 2), writeVarint(area.length), area]));
  }
  return encodeMessage(parts);
}

/** luck-car UpdatePlayerNumBroadcast. */
export function encodeLuckCarUpdatePlayerNumBroadcast(num: number): Buffer {
  return encodeMessage([pbUInt64(1, Math.max(0, Math.floor(num)))]);
}

/** luck-car StartBetBroadcast — required: betTime, trackType, trackPos, curTurn. */
export function encodeLuckCarStartBetBroadcast(
  betTime: number,
  curTurn = 1,
): Buffer {
  const parts: Buffer[] = [pbInt32(1, Math.max(1, betTime | 0))];
  for (let i = 1; i <= 8; i++) parts.push(pbInt32(2, i));
  for (const r of LUCK_CAR_RATIOS_MILLI) parts.push(pbInt32(3, r));
  parts.push(pbInt32(4, 1)); // trackType
  parts.push(pbInt32(5, 0)); // trackPos
  parts.push(pbInt32(6, curTurn));
  return encodeMessage(parts);
}

/** luck-car GameOverRsp — car race settle (not 7up dice). */
export function encodeLuckCarGameOverRsp(data: {
  winCar: number;
  winMoney: number;
  selfMoney: number;
  myBets?: number[];
  time?: number;
}): Buffer {
  const winCar = Math.max(1, Math.min(8, data.winCar | 0));
  const parts: Buffer[] = [
    pbUInt32(1, data.time ?? 4),
    pbUInt32(2, winCar),
  ];
  const myBets = data.myBets ?? new Array(8).fill(0);
  for (const b of myBets) parts.push(pbUInt32(3, Math.max(0, b | 0)));
  // tracks 1..8
  for (let t = 1; t <= 8; t++) parts.push(pbUInt32(5, t));
  for (let car = 1; car <= 8; car++) {
    const spd = encodeLuckCarSpeed(car, car, 60 + car * 5 + (car === winCar ? 40 : 0));
    parts.push(Buffer.concat([tag(6, 2), writeVarint(spd.length), spd]));
  }
  parts.push(pbUInt32(7, Math.max(0, Math.floor(data.winMoney))));
  parts.push(pbDouble(8, data.selfMoney));
  for (let i = 1; i <= 8; i++) parts.push(pbInt32(9, i)); // betCars
  for (let i = 1; i <= 8; i++) {
    parts.push(pbUInt32(10, i === winCar ? 8 : 10 + i)); // runTime
  }
  return encodeMessage(parts);
}

/** luck-car GetRankDataRes: code,desc,tipType(required),rankData[]{name,head,winMoney}. */
export function encodeLuckCarGetRankDataRes(
  ranks: Array<{ name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, 0),
    pbString(2, 'OK'),
    pbInt32(3, 0), // tipType required
  ];
  for (const r of ranks) {
    const row = encodeMessage([
      pbString(1, r.name || 'Player'),
      pbString(2, r.head || ''),
      pbDouble(3, Math.max(0, Number(r.winMoney) || 0)),
    ]);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** luck-car GetUserRecordRes: code,desc,recordData[]. returnMoney is int32 not double. */
export function encodeLuckCarGetUserRecordRes(
  records: Array<{
    time?: number;
    betInfo?: string;
    returnMoney?: number;
    winCar?: number;
    winRatio?: number;
    winCarBet?: number;
  }> = [],
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const rec of records) {
    const row = encodeMessage([
      pbInt32(1, Math.max(0, Math.floor(rec.time ?? Date.now() / 1000))),
      pbString(2, rec.betInfo ?? ''),
      pbInt32(3, Math.max(0, Math.floor(Number(rec.returnMoney) || 0))),
      pbInt32(4, Math.max(0, rec.winCar ?? 0)),
      pbInt32(5, Math.max(0, rec.winRatio ?? 0)),
      pbInt32(6, Math.max(0, rec.winCarBet ?? 0)),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/**
 * crash GetUserRecordRes — recordStruct:
 * time@1, betMoney@2, isCashout@3, cashoutRatio@4 float, cashoutMoney@5
 */
export function encodeCrashGetUserRecordRes(
  records: Array<{
    time?: number;
    betMoney?: number;
    isCashout?: boolean;
    cashoutRatio?: number;
    cashoutMoney?: number;
  }> = [],
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const rec of records) {
    const row = encodeMessage([
      pbInt32(1, Math.max(0, Math.floor(rec.time ?? Date.now() / 1000))),
      pbDouble(2, Math.max(0, Number(rec.betMoney) || 0)),
      pbInt32(3, rec.isCashout ? 1 : 0),
      pbFloat(4, Math.max(0, Number(rec.cashoutRatio) || 0)),
      pbDouble(5, Math.max(0, Number(rec.cashoutMoney) || 0)),
    ]);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** 7UpDown / multi-area settle — nums are dice faces; rank fills top cup winners. */
export function encodeGameOverRsp(data: {
  time?: number;
  nums: number[];
  winMoney: number;
  selfMoney: number;
  betInfo?: Array<{ id: number; totalBet?: number; myBet?: number; ratio?: number }>;
  rank?: Array<{ name: string; head: string; winMoney: number }>;
}): Buffer {
  const parts: Buffer[] = [pbUInt32(1, data.time ?? 3)];
  for (const n of data.nums) parts.push(pbUInt32(2, Math.max(0, Math.floor(n))));
  for (const a of data.betInfo ?? []) {
    const row = encode7UpBetArea(a);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  if (data.rank?.length) {
    const players = data.rank.map((r) =>
      encodeMessage([
        pbString(1, r.name || 'Player'),
        pbString(2, r.head || ''),
        pbDouble(3, Math.max(0, Number(r.winMoney) || 0)),
      ]),
    );
    const rankMsg = encodeMessage(
      players.map((p) => Buffer.concat([tag(1, 2), writeVarint(p.length), p])),
    );
    parts.push(Buffer.concat([tag(4, 2), writeVarint(rankMsg.length), rankMsg]));
  }
  parts.push(pbDouble(5, data.winMoney));
  parts.push(pbDouble(6, data.selfMoney));
  return encodeMessage(parts);
}

export function encodeEmpty(): Buffer {
  return Buffer.alloc(0);
}

export function encodeOkCodeDesc(code = 0, desc = 'OK'): Buffer {
  return encodeMessage([pbInt32(1, code), pbString(2, desc)]);
}

/** Lucky77 wheel positions 1..9 → area types 1/2/3 (client bets iconId 0/1/2). */
export const LUCKY77_ROUNDNO = [1, 2, 1, 2, 1, 2, 1, 2, 3] as const;
/** Payout ratios for bet icons 0,1,2 — matches TableInfoRes.ratios. */
export const LUCKY77_RATIOS = [...LUCKY77_AREA_RATIOS] as [number, number, number];

function encodeLucky77BetStruct(icon: number, money: number): Buffer {
  return encodeMessage([
    pbInt32(1, icon | 0),
    pbInt32(2, Math.max(0, Math.floor(Number(money) || 0))),
  ]);
}

function encodeLucky77OpenAward(data: {
  name: string;
  head: string;
  totalBet: number;
  totalGain: number;
}): Buffer {
  return encodeMessage([
    pbString(1, data.name ?? ''),
    pbString(2, data.head ?? ''),
    pbInt32(3, Math.max(0, Math.floor(data.totalBet || 0))),
    pbInt32(4, Math.max(0, Math.floor(data.totalGain || 0))),
  ]);
}

/** Lucky77 TableInfoRes — 3 bet areas, 4 chips, wheel history. */
export function encodeLucky77TableInfoRes(data: {
  state: number;
  timeLeft: number;
  curTurn: number;
  chips?: number[];
  ratios?: number[];
  history?: number[];
  myAreaBet?: Array<{ icon: number; money: number }>;
  betTotal?: Array<{ icon: number; money: number }>;
}): Buffer {
  // Must be exactly 4 values — UI has CHIPCOUNTS=4 fixed nodes.
  const raw = data.chips ?? lucky77ChipsList();
  const chips = raw.slice(0, 4);
  while (chips.length < 4) chips.push(LUCKY77_BET_CHIPS[chips.length] ?? 100);
  const ratios = data.ratios ?? [...LUCKY77_RATIOS];
  const history = data.history ?? [1, 2, 3, 4, 5, 9, 1, 2];
  const parts: Buffer[] = [
    pbInt32(1, data.state | 0),
    pbUInt32(2, Math.max(0, data.timeLeft | 0)),
  ];
  for (const b of data.betTotal ?? []) {
    const row = encodeLucky77BetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  for (const c of chips) parts.push(pbInt32(4, Math.max(0, Math.floor(c))));
  for (const b of data.myAreaBet ?? []) {
    const row = encodeLucky77BetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(7, data.curTurn | 0));
  for (const r of ratios) parts.push(pbUInt32(8, Math.max(0, Math.floor(r))));
  for (const h of history) parts.push(pbUInt32(9, Math.max(0, Math.floor(h))));
  parts.push(pbInt32(10, 0)); // rankSwitch
  parts.push(pbInt32(11, 0)); // rankSwitchDelayTime
  return encodeMessage(parts);
}

export function encodeLucky77StartBetBroadcast(state: number, betTime: number): Buffer {
  return encodeMessage([
    pbInt32(1, state | 0),
    pbInt32(2, Math.max(0, betTime | 0)),
  ]);
}

export function encodeLucky77BetRsp(data: {
  code: number;
  desc: string;
  tipType?: number;
  userMoney: number;
  betTotal?: Array<{ icon: number; money: number }>;
  curBet?: { icon: number; money: number };
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.code | 0),
    pbString(2, data.desc ?? ''),
    pbInt32(3, data.tipType ?? 0),
    pbDouble(4, Math.max(0, Number(data.userMoney) || 0)),
  ];
  for (const b of data.betTotal ?? []) {
    const row = encodeLucky77BetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  if (data.curBet) {
    const row = encodeLucky77BetStruct(data.curBet.icon, data.curBet.money);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** Lucky77 OtherPlayerBetBroadcast — drives chip-fly FX + pooled area totals. */
export function encodeLucky77OtherPlayerBetBroadcast(data: {
  betTotal: Array<{ icon: number; money: number }>;
  curBet: { icon: number; money: number };
  uid: number;
}): Buffer {
  const parts: Buffer[] = [];
  for (const b of data.betTotal ?? []) {
    const row = encodeLucky77BetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(1, 2), writeVarint(row.length), row]));
  }
  const cur = encodeLucky77BetStruct(data.curBet.icon, data.curBet.money);
  parts.push(Buffer.concat([tag(2, 2), writeVarint(cur.length), cur]));
  parts.push(pbInt32(3, data.uid | 0));
  return encodeMessage(parts);
}

function encodeLucky77RankStruct(data: {
  playerId?: number;
  name: string;
  head: string;
  awardMoney: number;
  hasAward?: number;
  points?: number;
}): Buffer {
  return encodeMessage([
    pbInt32(1, data.playerId ?? 0),
    pbString(2, data.name ?? 'Player'),
    pbString(3, data.head ?? ''),
    pbInt32(4, Math.max(0, Math.floor(data.awardMoney || 0))),
    pbInt32(5, data.hasAward ?? (data.awardMoney > 0 ? 1 : 0)),
    pbInt32(6, Math.max(0, Math.floor(data.points ?? data.awardMoney ?? 0))),
  ]);
}

/** Lucky77 GetRankDataRes — weekRank[] of { dayRank[], weekNo }. */
export function encodeLucky77GetRankDataRes(
  ranks: Array<{ playerId?: number; name: string; head: string; winMoney: number }>,
): Buffer {
  const dayRows = ranks.map((r) =>
    encodeLucky77RankStruct({
      playerId: r.playerId,
      name: r.name,
      head: r.head,
      awardMoney: r.winMoney,
      points: r.winMoney,
    }),
  );
  const weekNo = 1;
  const weekParts: Buffer[] = [];
  for (const row of dayRows) {
    weekParts.push(Buffer.concat([tag(1, 2), writeVarint(row.length), row]));
  }
  weekParts.push(pbInt32(2, weekNo));
  const week = encodeMessage(weekParts);
  return encodeMessage([
    pbInt32(1, 0),
    pbString(2, 'OK'),
    Buffer.concat([tag(3, 2), writeVarint(week.length), week]),
    pbInt32(4, weekNo),
    pbInt32(5, 0), // tipType
  ]);
}

/**
 * Lucky77 GetUserRecordRes (My Cost panel):
 * code@1, desc@2, recordData@3[] of recordStruct
 * recordStruct: time@1 int64, betTotal@2 detailBetStruct[], icon@3 int32[], curTurn@4
 * detailBetStruct: icon@1, cost@2 (stake), money@3 (return)
 */
export function encodeLucky77GetUserRecordRes(
  records: Array<{
    time?: number;
    curTurn?: number;
    icons?: number[];
    betTotal?: Array<{ icon: number; cost: number; money: number }>;
  }> = [],
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const rec of records) {
    const rowParts: Buffer[] = [
      pbUInt64(1, Math.max(0, Math.floor(rec.time ?? Date.now() / 1000))),
    ];
    for (const b of rec.betTotal ?? []) {
      const detail = encodeMessage([
        pbInt32(1, b.icon | 0),
        pbInt32(2, Math.max(0, Math.floor(b.cost || 0))),
        pbInt32(3, Math.max(0, Math.floor(b.money || 0))),
      ]);
      rowParts.push(Buffer.concat([tag(2, 2), writeVarint(detail.length), detail]));
    }
    for (const icon of rec.icons ?? []) {
      rowParts.push(pbInt32(3, Math.max(0, Math.floor(icon))));
    }
    rowParts.push(pbInt32(4, Math.max(0, Math.floor(rec.curTurn ?? 0))));
    const row = encodeMessage(rowParts);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

/** Lucky77 Game History (prize strip) — code/desc + recordData int32[] of wheel positions 1..9. */
export function encodeLucky77GetPrizeDrawRecordRes(positions: number[] = []): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const p of positions) {
    parts.push(pbInt32(3, Math.max(0, Math.floor(p))));
  }
  return encodeMessage(parts);
}

/** Lucky77 ResultBroadcast — myGoodLuck required. */
export function encodeLucky77ResultBroadcast(data: {
  state?: number;
  betTime?: number;
  name?: string;
  head?: string;
  totalBet: number;
  totalGain: number;
  curBingoIcon: number[];
  curTurn: number;
  userMoney: number;
  todayWin?: number;
  goodLuck?: Array<{ name: string; head: string; totalBet: number; totalGain: number }>;
  betRank?: Array<{ playerId?: number; name: string; head: string; awardMoney: number }>;
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.state ?? 3),
    pbInt32(2, data.betTime ?? 4),
  ];
  for (const g of data.goodLuck ?? []) {
    const row = encodeLucky77OpenAward(g);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  const mine = encodeLucky77OpenAward({
    name: data.name ?? '',
    head: data.head ?? '',
    totalBet: data.totalBet,
    totalGain: data.totalGain,
  });
  parts.push(Buffer.concat([tag(4, 2), writeVarint(mine.length), mine]));
  for (const r of data.betRank ?? []) {
    const row = encodeLucky77RankStruct({
      playerId: r.playerId,
      name: r.name,
      head: r.head,
      awardMoney: r.awardMoney,
    });
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  for (const icon of data.curBingoIcon ?? []) {
    parts.push(pbInt32(6, Math.max(0, Math.floor(icon))));
  }
  parts.push(pbInt32(7, data.curTurn | 0));
  parts.push(pbDouble(8, Math.max(0, Number(data.userMoney) || 0)));
  parts.push(pbInt32(9, Math.max(0, Math.floor(data.todayWin ?? data.totalGain))));
  return encodeMessage(parts);
}

// ── Camel Racing (PORT=camelracing) — 8-lane multi-bet race ─────────────────

function encodeCamelBetStruct(icon: number, money: number): Buffer {
  return encodeMessage([
    pbInt32(1, Math.max(0, Math.floor(icon))),
    pbDouble(2, Math.max(0, Number(money) || 0)),
  ]);
}

function encodeCamelRankStruct(name: string, head: string, winMoney: number): Buffer {
  return encodeMessage([
    pbString(1, name || 'Player'),
    pbString(2, head || ''),
    pbDouble(3, Math.max(0, Number(winMoney) || 0)),
  ]);
}

function encodeCamelRecordStruct(data: {
  cost: number;
  reward: number;
  time: number;
  betTotal?: Array<{ icon: number; money: number }>;
  result?: Array<{ icon: number; money: number }>;
  curTurn?: number;
  currGroupId?: number;
  retIcon?: number;
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, Math.max(0, Math.floor(data.cost))),
    pbInt32(2, Math.max(0, Math.floor(data.reward))),
    pbInt32(3, Math.max(0, Math.floor(data.time))),
  ];
  for (const b of data.betTotal ?? []) {
    const row = encodeCamelBetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(row.length), row]));
  }
  for (const b of data.result ?? []) {
    const row = encodeCamelBetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(6, data.curTurn ?? 1));
  parts.push(pbInt32(7, data.currGroupId ?? 0));
  parts.push(pbInt32(8, data.retIcon ?? 0));
  return encodeMessage(parts);
}

/** Camel TableInfoRes — GameState: waitting=0, betting=1. */
export function encodeCamelTableInfoRes(data: {
  state: number;
  timeLeft: number;
  totalPlayerNum: number;
  playerStatus?: number;
  camelSpeed?: number;
  curTurn?: number;
  currGroupId?: number;
  curBingoIcon?: number;
  totalBet?: number;
  totalGain?: number;
  history?: number[];
  myBets?: Array<{ icon: number; money: number }>;
  ratios?: number[];
  chips?: number[];
}): Buffer {
  const ratios = data.ratios?.length ? data.ratios : [...CAMEL_RACING_RATIOS];
  const chips = data.chips?.length ? data.chips : chipsList();
  const state = data.state === 1 ? 1 : 0;
  const parts: Buffer[] = [
    pbInt32(1, state),
    pbUInt32(2, Math.max(0, data.timeLeft | 0)),
  ];
  for (const b of data.myBets ?? []) {
    if (b.money <= 0) continue;
    const row = encodeCamelBetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  for (const c of chips) parts.push(pbInt32(4, Math.max(0, Math.floor(c))));
  if (data.totalBet != null) parts.push(pbInt32(5, Math.max(0, Math.floor(data.totalBet))));
  if (data.totalGain != null) parts.push(pbInt32(6, Math.max(0, Math.floor(data.totalGain))));
  if (data.curBingoIcon != null && data.curBingoIcon > 0) {
    parts.push(pbInt32(8, data.curBingoIcon | 0));
  }
  parts.push(pbInt32(9, data.currGroupId ?? 0));
  parts.push(pbInt32(10, data.curTurn ?? 1));
  for (const r of ratios) parts.push(pbUInt32(11, Math.max(1, Math.floor(r))));
  parts.push(pbUInt32(12, Math.max(0, data.totalPlayerNum | 0)));
  for (const h of data.history ?? []) parts.push(pbUInt32(13, Math.max(0, h | 0)));
  parts.push(pbInt32(14, data.playerStatus ?? 0));
  parts.push(pbInt32(19, data.camelSpeed ?? 80));
  return encodeMessage(parts);
}

/** StartBetBroadcast: state@1 betTime@2 — state must be betting=1. */
export function encodeCamelStartBetBroadcast(state: number, betTime: number): Buffer {
  return encodeMessage([
    pbInt32(1, state === 1 ? 1 : 0),
    pbInt32(2, Math.max(1, betTime | 0)),
  ]);
}

export function encodeCamelBetRsp(data: {
  code: number;
  desc: string;
  userMoney: number;
  tipType?: number;
  betTotal?: Array<{ icon: number; money: number }>;
  curBet?: { icon: number; money: number };
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.code | 0),
    pbString(2, data.desc || ''),
    pbDouble(3, Math.max(0, Number(data.userMoney) || 0)),
  ];
  for (const b of data.betTotal ?? []) {
    const row = encodeCamelBetStruct(b.icon, b.money);
    parts.push(Buffer.concat([tag(4, 2), writeVarint(row.length), row]));
  }
  if (data.curBet) {
    const row = encodeCamelBetStruct(data.curBet.icon, data.curBet.money);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(6, data.tipType ?? 0));
  return encodeMessage(parts);
}

export function encodeCamelPlayerBetBroadcast(data: {
  arena: number[];
  money: number[];
  myArena?: number[];
  myMoney?: number[];
}): Buffer {
  const parts: Buffer[] = [];
  for (const a of data.arena) parts.push(pbUInt32(1, Math.max(0, a | 0)));
  for (const m of data.money) parts.push(pbDouble(2, Math.max(0, Number(m) || 0)));
  for (const a of data.myArena ?? []) parts.push(pbUInt32(3, Math.max(0, a | 0)));
  for (const m of data.myMoney ?? []) parts.push(pbDouble(4, Math.max(0, Number(m) || 0)));
  return encodeMessage(parts);
}

export function encodeCamelPlayerNumsBroadcast(
  uid: number,
  totalMoney: number,
  totalPlayerNum: number,
): Buffer {
  return encodeMessage([
    pbUInt32(1, Math.max(0, uid | 0)),
    pbDouble(2, Math.max(0, Number(totalMoney) || 0)),
    pbUInt32(3, Math.max(0, totalPlayerNum | 0)),
  ]);
}

/** ResultBroadcast — race settle + animation seed. */
export function encodeCamelResultBroadcast(data: {
  state?: number;
  betTime?: number;
  totalBet: number;
  totalGain: number;
  curBingoIcon: number;
  currGroupId?: number;
  curTurn: number;
  userMoney: number;
  todayWin?: number;
  playerStatus?: number;
  betRank?: Array<{ name: string; head: string; winMoney: number }>;
  animalRandom?: number[];
  posRandom?: number[];
}): Buffer {
  const parts: Buffer[] = [
    pbInt32(1, data.state ?? 0),
    pbInt32(2, data.betTime ?? 8),
    pbInt32(3, Math.max(0, Math.floor(data.totalBet))),
    pbInt32(4, Math.max(0, Math.floor(data.totalGain))),
  ];
  for (const r of data.betRank ?? []) {
    const row = encodeCamelRankStruct(r.name, r.head, r.winMoney);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(6, Math.max(1, Math.min(8, data.curBingoIcon | 0))));
  parts.push(pbInt32(7, data.currGroupId ?? 0));
  parts.push(pbInt32(8, data.curTurn | 0));
  parts.push(pbDouble(9, Math.max(0, Number(data.userMoney) || 0)));
  parts.push(pbInt32(10, Math.max(0, Math.floor(data.todayWin ?? data.totalGain))));
  parts.push(pbInt32(11, data.playerStatus ?? 0));
  for (const p of data.posRandom ?? []) parts.push(pbUInt32(12, Math.max(0, p | 0)));
  for (const a of data.animalRandom ?? []) parts.push(pbUInt32(13, Math.max(0, a | 0)));
  return encodeMessage(parts);
}

export function encodeCamelGetRankDataRes(
  ranks: Array<{ name: string; head: string; winMoney: number }>,
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const r of ranks) {
    const row = encodeCamelRankStruct(r.name, r.head, r.winMoney);
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  parts.push(pbInt32(4, 0)); // tipType required
  return encodeMessage(parts);
}

export function encodeCamelGetUserRecordRes(
  records: Array<{
    cost: number;
    reward: number;
    time?: number;
    betTotal?: Array<{ icon: number; money: number }>;
    retIcon?: number;
    curTurn?: number;
  }> = [],
): Buffer {
  const parts: Buffer[] = [pbInt32(1, 0), pbString(2, 'OK')];
  for (const rec of records) {
    const row = encodeCamelRecordStruct({
      cost: rec.cost,
      reward: rec.reward,
      time: rec.time ?? Math.floor(Date.now() / 1000),
      betTotal: rec.betTotal,
      result: rec.retIcon
        ? [{ icon: rec.retIcon, money: rec.reward }]
        : [],
      curTurn: rec.curTurn ?? 1,
      currGroupId: 0,
      retIcon: rec.retIcon ?? 0,
    });
    parts.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage(parts);
}

