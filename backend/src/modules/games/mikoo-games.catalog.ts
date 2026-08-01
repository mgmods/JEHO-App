/** Mikoo / BaiShun Cocos HTML5 games extracted from EXTRACTED_GAMES. */

export type MikooBridge = 'hash' | 'baishun';

export type MikooGameDef = {
  id: string;
  title: string;
  titleEn: string;
  /** Relative path under backend/public/games/mikoo/{id}/ */
  entry: string;
  bridge: MikooBridge;
  /** BaiShun module / report id (baishun games). */
  bsModuleId?: number;
  /** Hash-game login gameType (from Mikoo conf). */
  gameType?: number;
  /** WebSocket path suffix for hash games (e.g. crash). */
  wsPath?: string;
  sortOrder: number;
};

const ORIGIN = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr';

export const MIKOO_GAMES: MikooGameDef[] = [
  { id: '7updown', title: '7 Up Down', titleEn: '7 Up Down', entry: 'index.html', bridge: 'hash', gameType: 1, sortOrder: 10 },
  { id: 'cleopatra-slot', title: 'Cleopatra Slot', titleEn: 'Cleopatra Slot', entry: 'index.html', bridge: 'baishun', bsModuleId: 1107, sortOrder: 11 },
  { id: 'cleopatra-slots', title: 'Cleopatra Slots', titleEn: 'Cleopatra Slots', entry: 'index.html', bridge: 'hash', gameType: 2, sortOrder: 12 },
  { id: 'crash', title: 'Crash', titleEn: 'Crash', entry: 'index.html', bridge: 'hash', gameType: 3, wsPath: 'crash', sortOrder: 13 },
  { id: 'fishing', title: 'Fishing', titleEn: 'Fishing', entry: 'index.html', bridge: 'baishun', bsModuleId: 1022, sortOrder: 14 },
  { id: 'football-plinko', title: 'Football Plinko', titleEn: 'Football Plinko', entry: 'index.html', bridge: 'baishun', bsModuleId: 1184, sortOrder: 15 },
  { id: 'fortune-slot', title: 'جواهر الحظ', titleEn: 'Fortune Gems', entry: 'index.html', bridge: 'hash', gameType: 4, sortOrder: 16 },
  { id: 'greedy-box', title: 'Greedy Box', titleEn: 'Greedy Box', entry: 'index.html', bridge: 'hash', gameType: 5, sortOrder: 17 },
  { id: 'hilo', title: 'Hilo', titleEn: 'Hilo', entry: 'index.html', bridge: 'baishun', bsModuleId: 1072, sortOrder: 18 },
  { id: 'line-slots', title: 'Line Slots', titleEn: 'Line Slots', entry: 'index.html', bridge: 'hash', gameType: 6, sortOrder: 19 },
  { id: 'luck-car', title: 'سيارة الحظ', titleEn: 'Luck Car', entry: 'index.html', bridge: 'hash', gameType: 7, sortOrder: 20 },
  { id: 'lucky77', title: 'لاكي 77', titleEn: 'Lucky 77', entry: 'index.html', bridge: 'hash', gameType: 12, sortOrder: 21 },
  { id: 'megaways-slots', title: 'Megaways Slots', titleEn: 'Megaways Slots', entry: 'index.html', bridge: 'hash', gameType: 8, sortOrder: 22 },
  { id: 'olympians', title: 'Olympians', titleEn: 'Olympians', entry: 'index.html', bridge: 'hash', gameType: 9, sortOrder: 23 },
  { id: 'pirate-king', title: 'Pirate King', titleEn: 'Pirate King', entry: 'index.html', bridge: 'hash', gameType: 10, sortOrder: 24 },
  { id: 'royal-battle', title: 'Royal Battle', titleEn: 'Royal Battle', entry: 'index.html', bridge: 'baishun', bsModuleId: 1174, sortOrder: 25 },
  { id: 'slot777', title: 'سلوت', titleEn: 'Slot', entry: 'index.html', bridge: 'baishun', bsModuleId: 1098, sortOrder: 26 },
  { id: 'sugar-rush', title: 'Sugar Rush', titleEn: 'Sugar Rush', entry: 'index.html', bridge: 'hash', gameType: 11, sortOrder: 27 },
  { id: 'swimsuit-party', title: 'Swimsuit Party', titleEn: 'Swimsuit Party', entry: 'index.html', bridge: 'baishun', bsModuleId: 1183, sortOrder: 28 },
];

export function mikooPlayUrl(gameId: string) {
  return `${ORIGIN}/games/mikoo/${gameId}/index.html`;
}

export function mikooCoverUrl(gameId: string) {
  return `${ORIGIN}/games/mikoo/covers/${gameId}.png?v=20260801a`;
}

export function mikooSplashUrl(gameId: string) {
  return `${ORIGIN}/games/mikoo/${gameId}/splash.85cfd.png`;
}

export function findMikooGame(gameId: string) {
  const id = gameId.trim().toLowerCase();
  return MIKOO_GAMES.find((g) => g.id === id) || null;
}

/** Old HTML casual games + XO removed from product. */
export function isRemovedLegacyHtmlGame(item: Record<string, unknown> | null | undefined): boolean {
  if (!item) return true;
  const id = String(item.id || '').toLowerCase();
  const playUrl = String(item.playUrl || '').toLowerCase();
  if (
    id === 'lucky-wheel' ||
    id === 'dice' ||
    id === 'wheel' ||
    id === 'xo' ||
    id === 'tic_tac_toe' ||
    id === 'tictactoe'
  ) {
    return true;
  }
  if (
    playUrl.includes('lucky-wheel') ||
    playUrl.includes('/games/dice.html') ||
    playUrl.endsWith('dice.html') ||
    playUrl.includes('tic_tac_toe') ||
    playUrl.includes('tictactoe')
  ) {
    return true;
  }
  return false;
}

export function buildDefaultGamesCatalog() {
  return MIKOO_GAMES.map((g) => ({
    id: g.id,
    title: g.title,
    titleEn: g.titleEn,
    coverUrl: mikooCoverUrl(g.id),
    playUrl: mikooPlayUrl(g.id),
    sortOrder: g.sortOrder,
    mode: 'mikoo_slot',
    bridge: g.bridge,
    bsModuleId: g.bsModuleId ?? null,
    gameType: g.gameType ?? null,
    enabled: true,
  }));
}

export function classifyCatalogItem(item: Record<string, unknown>) {
  if (isRemovedLegacyHtmlGame(item)) return null;

  const id = String(item.id || '').toLowerCase();
  const playUrl = String(item.playUrl || '').toLowerCase();
  if (id === 'fireforce' || playUrl.includes('fireforce')) return null;
  if (
    ['ono', 'domino', 'ludo'].includes(id) ||
    playUrl.includes('ono.html') ||
    playUrl.includes('domino.html') ||
    playUrl.includes('ludo.html')
  ) {
    return null;
  }

  let mode = String(item.mode || '');
  if (!mode) {
    if (playUrl.includes('/games/mikoo/') || id.includes('slot') || id.includes('crash')) {
      mode = 'mikoo_slot';
    } else {
      mode = 'local_solo';
    }
  }

  const game = findMikooGame(id);
  const enabled = item.enabled === false || item.visible === false ? false : true;

  return {
    ...item,
    mode,
    enabled,
    bridge: item.bridge ?? game?.bridge ?? null,
    gameType: item.gameType ?? game?.gameType ?? null,
    bsModuleId: item.bsModuleId ?? game?.bsModuleId ?? null,
  };
}

export function sanitizeGamesCatalog(
  raw: unknown,
  options: { forClient?: boolean } = {},
) {
  const defaults = buildDefaultGamesCatalog();
  const list = Array.isArray(raw) && raw.length ? [...raw] : [...defaults];
  // New games added in code (e.g. lucky77) must appear even if dashboard JSON is stale.
  const seen = new Set(
    list.map((item) => String((item as Record<string, unknown>).id || '').toLowerCase()),
  );
  for (const d of defaults) {
    const id = String(d.id || '').toLowerCase();
    if (id && !seen.has(id)) {
      list.push(d);
      seen.add(id);
    }
  }
  let out = list
    .map((item) => classifyCatalogItem(item as Record<string, unknown>))
    .filter(Boolean) as Array<Record<string, unknown>>;

  if (options.forClient) {
    out = out.filter((g) => g.enabled !== false);
  }

  return out.sort(
    (a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0),
  );
}
