/** Minimal protobuf encode/decode for Mikoo hash-game messages. */

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

  // sugar-rush / lucky77: money@6, erbanNo@7, tipType@8
  if (slug === 'sugar-rush' || slug === 'lucky77') {
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

/** greedy-box TableInfoRes — unlocks table UI after login. */
export function encodeGreedyTableInfoRes(data: {
  state: number;
  timeLeft: number;
  curTurn: number;
  userMoney: number;
  chips?: number[];
}): Buffer {
  const chips = data.chips ?? [100, 500, 1000, 5000, 10000];
  const boxes: Buffer[] = [];
  const ratios = [2, 3, 5, 8, 10, 15, 20, 50];
  for (let i = 0; i < 8; i++) {
    const box = encodeMessage([
      pbUInt32(1, i + 1),
      pbString(2, `box${i + 1}`),
      pbUInt32(3, ratios[i] || 2),
    ]);
    boxes.push(Buffer.concat([tag(5, 2), writeVarint(box.length), box]));
  }
  return encodeMessage([
    pbUInt32(1, data.state),
    pbUInt32(2, Math.max(0, data.timeLeft | 0)),
    pbUInt32(3, data.curTurn),
    pbPackedUInt64(4, chips),
    ...boxes,
    pbPackedUInt32(6, [1, 2, 3]),
    pbUInt64(11, Math.max(0, Math.floor(data.userMoney))),
    pbUInt64(12, chips[0] || 100),
    pbUInt32(17, 1),
  ]);
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
}): Buffer {
  return encodeMessage([
    pbUInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.tipType ?? 0),
    pbUInt64(4, Math.max(0, Math.floor(data.userMoney))),
    pbUInt32(6, data.iconId ?? 0),
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

/** 7updown TableInfo — different from crash. */
export function encodeTableInfo7UpDown(data: {
  state: number;
  betTime: number;
  waitTime?: number;
  playerNum: number;
  timeLeft: number;
  totalBet?: number;
  history?: number[];
}): Buffer {
  const history = data.history ?? [1, 2, 3, 1, 3, 2, 1, 3];
  const parts: Buffer[] = [
    pbInt32(1, data.state),
    pbUInt32(2, data.betTime),
    pbUInt32(3, data.waitTime ?? 3),
    pbUInt32(4, data.playerNum),
  ];
  for (const h of history) parts.push(pbUInt32(5, h));
  parts.push(pbUInt32(6, Math.max(0, data.timeLeft | 0)));
  parts.push(pbUInt32(7, data.totalBet ?? 0));
  // betConf chips: id, betMin, conf csv
  const chips = [
    { id: 1, min: 10, conf: '10,50,100,500,1000' },
    { id: 2, min: 100, conf: '100,500,1000,5000' },
  ];
  for (const c of chips) {
    const row = encodeMessage([
      pbUInt32(1, c.id),
      pbUInt32(2, c.min),
      pbString(3, c.conf),
    ]);
    parts.push(Buffer.concat([tag(9, 2), writeVarint(row.length), row]));
  }
  // area ratios for down/seven/up
  for (const [id, ratio] of [[1, 2], [2, 5], [3, 2]] as const) {
    const area = encodeMessage([
      pbUInt32(1, id),
      pbUInt32(2, 0),
      pbUInt32(3, 0),
      pbUInt32(4, ratio),
    ]);
    parts.push(Buffer.concat([tag(8, 2), writeVarint(area.length), area]));
  }
  // AvailableChips@14 — unlocks chip tray on some clients
  for (const chip of [10, 50, 100, 500, 1000, 5000, 10000]) {
    parts.push(pbUInt32(14, chip));
  }
  return encodeMessage(parts);
}

/** 7updown BetRsp field order. */
export function encode7UpBetRsp(data: {
  code: number;
  desc: string;
  selfMoney: number;
  areaId?: number;
  betMoney?: number;
  tipType?: number;
}): Buffer {
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbDouble(3, data.selfMoney),
    pbInt32(4, data.areaId ?? 0),
    pbInt32(5, Math.max(0, Math.floor(data.betMoney ?? 0))),
    pbInt32(7, data.tipType ?? 0),
  ]);
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
  return encodeMessage([pbFloat(1, ratio), pbUInt64(2, flyTime)]);
}

export function encodeGameOverBroadcast(ratio: number, settleTime: number): Buffer {
  return encodeMessage([pbFloat(1, ratio), pbUInt32(2, settleTime)]);
}

export function encodeCashoutRsp(data: {
  code: number;
  desc: string;
  ratio: number;
  winMoney: number;
  selfMoney: number;
}): Buffer {
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, 0),
    pbFloat(4, data.ratio),
    pbUInt64(5, data.winMoney),
    pbDouble(6, data.selfMoney),
  ]);
}

export function encodeTableInfoCrash(data: {
  state: number;
  playerNum: number;
  timeLeft: number;
  ratio?: number;
}): Buffer {
  // Crash client: 1 state, 2 playerNum, 3 history, 4 timeLeft, 5 argAlpha, 6 argBeta,
  // 7 cashOutMoney, 8 cashRatio, 9 totalBet, 10 selfBet, 11 ratio, 12 flyTime, 15 availableChips
  const parts = [
    pbInt32(1, data.state),
    pbInt32(2, data.playerNum),
    pbInt32(4, Math.max(0, data.timeLeft | 0)),
    pbFloat(5, 0.08),
    pbFloat(6, 0.5),
    pbDouble(7, 0),
    pbFloat(8, 1),
    pbUInt64(9, 0),
    pbUInt64(10, 0),
    pbFloat(11, data.ratio ?? 1),
    pbUInt64(12, 0),
  ];
  for (const chip of [100, 500, 1000, 5000, 10000, 50000]) {
    parts.push(pbInt32(15, chip));
  }
  return encodeMessage(parts);
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

/** luck-car BetRsp: code,desc,tipType,selfMoney,id,betMoney,myBetAll */
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
    pbUInt64(5, data.areaId ?? 0),
    pbUInt64(6, data.betMoney ?? 0),
    pbUInt64(7, data.myBetAll ?? data.betMoney ?? 0),
  ]);
}

/** Olympians BetRes — 6×5 grid. Do NOT send updatePosList: bad missPos coords freeze UI (null getComponent). Win shown via client patch on empty-update path. */
export function encodeOlympiansBetRes(data: {
  code: number;
  desc: string;
  selfMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const cols = 6;
  const rows = 5;
  const win = Math.max(0, Math.floor(data.winMoney || 0));
  const winSymbol = 1 + Math.floor(Math.random() * 8);

  type Cell = { row: number; col: number; number: number; mul: number };
  const board: Cell[] = [];
  for (let col = 0; col < cols; col++) {
    for (let row = 0; row < rows; row++) {
      board.push({
        row,
        col,
        number: 1 + Math.floor(Math.random() * 9),
        mul: 1,
      });
    }
  }
  // Visual match line when winning (display only — cascade list omitted to avoid freeze).
  if (win > 0) {
    for (let col = 0; col < Math.min(5, cols); col++) {
      const cell = board.find((c) => c.col === col && c.row === 2);
      if (cell) cell.number = winSymbol;
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
}): Buffer {
  const matrix: Buffer[] = [];
  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 6; col++) {
      matrix.push(
        encodeMessage([
          pbInt32(1, row),
          pbInt32(2, col),
          pbInt32(3, 1 + Math.floor(Math.random() * 8)),
          pbInt32(4, 1),
        ]),
      );
    }
  }
  const symbolInfo = encodeMessage([
    pbInt32(1, Math.max(0, Math.floor(data.winMoney ?? 0))),
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

/** line-slots DoSlotsRsp */
export function encodeDoSlotsRsp(data: {
  code: number;
  desc: string;
  cost: number;
  userMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const icons: Buffer[] = [];
  for (let i = 0; i < 15; i++) {
    icons.push(pbInt32(7, 1 + Math.floor(Math.random() * 9)));
  }
  const isBingo = data.winMoney > 0 ? 1 : 0;
  return encodeMessage([
    pbInt32(1, data.code),
    pbString(2, data.desc),
    pbInt32(3, data.cost),
    pbInt32(4, isBingo),
    ...icons,
    pbDouble(8, data.userMoney),
    pbInt32(9, data.tipType ?? 0),
  ]);
}

/** fortune-slot / Fortune Gems DoFortuneGemsRes */
export function encodeFortuneGemsRes(data: {
  code: number;
  desc: string;
  cost: number;
  userMoney: number;
  winMoney: number;
  tipType?: number;
}): Buffer {
  const icons: Buffer[] = [];
  // 5x3 grid typical for fortune gems
  for (let i = 0; i < 15; i++) {
    icons.push(pbUInt32(6, 1 + Math.floor(Math.random() * 8)));
  }
  const isWin = data.winMoney > 0 ? 1 : 0;
  return encodeMessage([
    pbUInt32(1, data.code),
    pbString(2, data.desc),
    pbUInt32(3, data.cost),
    pbUInt32(4, isWin),
    pbDouble(5, data.userMoney),
    ...icons,
    pbUInt32(8, 0), // lineReturn
    pbUInt32(10, isWin ? 1 : 0), // winMultiplier
    pbUInt32(11, 0), // WheelWinMoney
    pbUInt32(12, Math.max(0, Math.floor(data.winMoney))),
    pbInt32(13, data.tipType ?? 0),
  ]);
}

/** Minimal GameCfgRes for fortune gems (unlocks spin UI). */
export function encodeFortuneGameCfgRes(): Buffer {
  const betMult = [1, 2, 5, 10, 20, 50, 100].map((n) => pbUInt32(4, n));
  const cfg = encodeMessage([
    pbUInt32(1, 1), // gameSwitch on
    pbUInt32(2, 10000), // maxBet
    pbUInt32(3, 1), // minBet
    ...betMult,
  ]);
  return encodeMessage([
    pbUInt32(1, 0),
    pbString(2, 'OK'),
    Buffer.concat([tag(3, 2), writeVarint(cfg.length), cfg]),
  ]);
}

/** line-slots GameCfgRsp — client waits on splash until this arrives. */
export function encodeLineSlotsGameCfgRsp(): Buffer {
  const defaultCfg = encodeMessage([
    pbInt32(1, 10), // BasicBetLimit
    pbInt32(2, 1), // MinBet
    pbInt32(3, 3), // ScrollGrid
    pbInt32(4, 0), // BonusTrunCount
    pbString(5, '1,2,5,10,20,50,100'), // BetMult
    pbString(6, '10,20,50,100'), // AutoSpinCount
  ]);
  const symbols: Buffer[] = [];
  for (let i = 1; i <= 9; i++) {
    const row = encodeMessage([
      pbInt32(1, i), // SymbolID
      pbString(2, `s${i}`), // SymbolName
      pbInt32(3, 1), // SymbolType
      pbInt32(4, 5),
      pbInt32(5, 10),
      pbInt32(6, 20),
    ]);
    symbols.push(Buffer.concat([tag(2, 2), writeVarint(row.length), row]));
  }
  const lines: Buffer[] = [];
  for (let i = 1; i <= 9; i++) {
    const row = encodeMessage([
      pbInt32(1, i), // lineID
      pbString(2, '0,1,2,3,4'), // lineInclude
    ]);
    lines.push(Buffer.concat([tag(3, 2), writeVarint(row.length), row]));
  }
  return encodeMessage([
    Buffer.concat([tag(1, 2), writeVarint(defaultCfg.length), defaultCfg]),
    ...symbols,
    ...lines,
  ]);
}

/** megaways / sugar chip list — unlocks bet UI after splash. */
export function encodeChipCfgRes(chips: number[] = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000]): Buffer {
  return encodeMessage([
    pbInt32(1, 0),
    pbString(2, 'OK'),
    pbInt32(3, 0), // addPer
    ...chips.map((c) => pbInt32(4, c)),
  ]);
}

const DEFAULT_SPIN_CHIPS = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000];

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

export function encodeStartFlyBroadcast(ratio = 1, settleTime = 3): Buffer {
  return encodeMessage([pbFloat(1, ratio), pbUInt32(2, settleTime)]);
}

/** UI ratios are milli (ratio/1000 → x2). Settlement still uses real 2..25. */
const LUCK_CAR_RATIOS_MILLI = [2000, 3000, 4000, 5000, 8000, 10000, 15000, 25000];
const LUCK_CAR_CHIPS = [100, 500, 1000, 5000, 10000, 50000];

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

/** luck-car TableInfo — NOT crash TableInfo. */
export function encodeLuckCarTableInfo(data: {
  state: number;
  playerNum: number;
  timeLeft: number;
  curTurn?: number;
  totalBet?: number;
}): Buffer {
  const cars = LUCK_CAR_RATIOS_MILLI.map((_, i) => i + 1);
  const parts: Buffer[] = [
    pbInt32(1, data.state),
    pbUInt32(2, data.playerNum),
    pbUInt32(3, Math.max(0, data.timeLeft | 0)),
    pbUInt32(4, data.totalBet ?? 0),
  ];
  for (let i = 0; i < LUCK_CAR_RATIOS_MILLI.length; i++) {
    const area = encodeLuckCarBetArea(i + 1, LUCK_CAR_RATIOS_MILLI[i]);
    parts.push(Buffer.concat([tag(5, 2), writeVarint(area.length), area]));
  }
  for (let i = 0; i < LUCK_CAR_RATIOS_MILLI.length; i++) {
    const conf = encodeLuckCarConf(i + 1);
    parts.push(Buffer.concat([tag(6, 2), writeVarint(conf.length), conf]));
  }
  parts.push(pbInt32(14, 10)); // betTime
  for (const c of cars) parts.push(pbInt32(15, c)); // betCars
  for (const r of LUCK_CAR_RATIOS_MILLI) parts.push(pbInt32(16, r)); // betRatio milli
  parts.push(pbInt32(17, 1)); // trackType
  parts.push(pbInt32(18, 0)); // trackPos
  parts.push(pbString(23, JSON.stringify(LUCK_CAR_CHIPS))); // chipsConf
  return encodeMessage(parts);
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

/** luck-car GetUserRecordRes: code,desc,recordData[]. Empty list clears loading. */
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
      pbUInt32(1, Math.max(0, Math.floor(rec.time ?? Date.now() / 1000))),
      pbString(2, rec.betInfo ?? ''),
      pbDouble(3, Math.max(0, Number(rec.returnMoney) || 0)),
      pbUInt32(4, Math.max(0, rec.winCar ?? 0)),
      pbUInt32(5, Math.max(0, rec.winRatio ?? 0)),
      pbUInt32(6, Math.max(0, rec.winCarBet ?? 0)),
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

/** 7UpDown / multi-area settle. */
export function encodeGameOverRsp(data: {
  time?: number;
  nums: number[];
  winMoney: number;
  selfMoney: number;
}): Buffer {
  const parts: Buffer[] = [pbInt32(1, data.time ?? 3)];
  for (const n of data.nums) parts.push(pbInt32(2, n));
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
export const LUCKY77_RATIOS = [2, 2, 8] as const;

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
  const chips = data.chips ?? [100, 500, 1000, 5000];
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
