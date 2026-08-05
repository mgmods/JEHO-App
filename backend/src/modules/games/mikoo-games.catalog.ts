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
  { id: '7updown', title: '٧ فوق تحت', titleEn: '7 Up Down', entry: 'index.html', bridge: 'hash', gameType: 1, sortOrder: 10 },
  { id: 'bounty-football', title: 'كرة القدم الجوائز', titleEn: 'Bounty Football', entry: 'index.html', bridge: 'hash', gameType: 100, wsPath: 'bounty-football', sortOrder: 14 },
  { id: 'cleopatra-slot', title: 'كليوباترا', titleEn: 'Cleopatra Slot', entry: 'index.html', bridge: 'baishun', bsModuleId: 1107, sortOrder: 15 },
  { id: 'cleopatra-slots', title: 'فتحات كليوباترا', titleEn: 'Cleopatra Spins', entry: 'index.html', bridge: 'hash', gameType: 2, sortOrder: 16 },
  { id: 'crash', title: 'كراش', titleEn: 'Crash', entry: 'index.html', bridge: 'hash', gameType: 3, wsPath: 'crash', sortOrder: 17 },
  { id: 'fishing', title: 'صيد السمك', titleEn: 'Fishing', entry: 'index.html', bridge: 'baishun', bsModuleId: 1022, sortOrder: 18 },
  { id: 'football-plinko', title: 'بلينكو كرة القدم', titleEn: 'Football Plinko', entry: 'index.html', bridge: 'baishun', bsModuleId: 1184, sortOrder: 19 },
  { id: 'fortune-slot', title: 'جواهر الحظ', titleEn: 'Fortune Gems', entry: 'index.html', bridge: 'hash', gameType: 4, sortOrder: 20 },
  { id: 'greedy-box', title: 'صندوق الطمع', titleEn: 'Greedy Box', entry: 'index.html', bridge: 'hash', gameType: 5, sortOrder: 21 },
  { id: 'hilo', title: 'هاي لو', titleEn: 'Hilo', entry: 'index.html', bridge: 'baishun', bsModuleId: 1072, sortOrder: 22 },
  { id: 'line-slots', title: 'فتحات الخط', titleEn: 'Line Slots', entry: 'index.html', bridge: 'hash', gameType: 6, sortOrder: 23 },
  { id: 'luck-car', title: 'سيارة الحظ', titleEn: 'Lucky Car', entry: 'index.html', bridge: 'hash', gameType: 7, sortOrder: 24 },
  { id: 'lucky77', title: 'لاكي ٧٧', titleEn: 'Lucky 77', entry: 'index.html', bridge: 'hash', gameType: 12, sortOrder: 25 },
  { id: 'megaways-slots', title: 'ميجاوايز', titleEn: 'Megaways Slots', entry: 'index.html', bridge: 'hash', gameType: 8, sortOrder: 26 },
  { id: 'olympians', title: 'الأوليمبيون', titleEn: 'Olympians', entry: 'index.html', bridge: 'hash', gameType: 9, sortOrder: 27 },
  { id: 'pirate-king', title: 'ملك القراصنة', titleEn: 'Pirate King', entry: 'index.html', bridge: 'hash', gameType: 10, sortOrder: 28 },
  { id: 'royal-battle', title: 'المعركة الملكية', titleEn: 'Royal Battle', entry: 'index.html', bridge: 'baishun', bsModuleId: 1174, sortOrder: 29 },
  { id: 'slot777', title: 'سلوت ٧٧٧', titleEn: 'Slot 777', entry: 'index.html', bridge: 'baishun', bsModuleId: 1098, sortOrder: 30 },
  { id: 'sugar-rush', title: 'سكر راش', titleEn: 'Sugar Rush', entry: 'index.html', bridge: 'hash', gameType: 11, sortOrder: 31 },
  { id: 'swimsuit-party', title: 'حفلة السباحة', titleEn: 'Swimsuit Party', entry: 'index.html', bridge: 'baishun', bsModuleId: 1183, sortOrder: 32 },
];

export function mikooPlayUrl(gameId: string) {
  return `${ORIGIN}/games/mikoo/${gameId}/index.html`;
}

export function mikooCoverUrl(gameId: string) {
  return `${ORIGIN}/games/mikoo/covers/${gameId}.png?v=20260805bf`;
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

  const rawTitle = item.title != null ? String(item.title).trim() : '';
  const rawTitleEn = item.titleEn != null ? String(item.titleEn).trim() : '';
  const looksEnglishOnly =
    !!rawTitle && !/[\u0600-\u06FF]/.test(rawTitle) && !/[\u0600-\u06FF]/.test(rawTitleEn);
  // Prefer catalog Arabic when saved settings still store English-only titles.
  const title =
    (rawTitle && /[\u0600-\u06FF]/.test(rawTitle) && rawTitle) ||
    game?.title ||
    rawTitle ||
    rawTitleEn ||
    id;
  const titleEn =
    rawTitleEn ||
    (looksEnglishOnly ? rawTitle : '') ||
    game?.titleEn ||
    (rawTitle && !/[\u0600-\u06FF]/.test(rawTitle) ? rawTitle : '') ||
    id;

  let coverUrl = item.coverUrl != null ? String(item.coverUrl).trim() : '';
  // Always prefer current package cover path (stale .jpg / old cache-bust params break the hub).
  if (!coverUrl || coverUrl.includes('/games/mikoo/covers/') || coverUrl.endsWith('.jpg')) {
    coverUrl = mikooCoverUrl(id);
  }

  return {
    ...item,
    id,
    title,
    titleEn,
    coverUrl,
    playUrl: item.playUrl || (game ? mikooPlayUrl(id) : item.playUrl),
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

  // Force fresh cover cache-bust + localized titles on every client response
  // even when admin-saved JSON has English-only names or old .jpg paths.
  out = out.map((g) => classifyCatalogItem(g) || g).filter(Boolean) as Array<
    Record<string, unknown>
  >;

  return out.sort(
    (a, b) => Number(a.sortOrder ?? 0) - Number(b.sortOrder ?? 0),
  );
}
