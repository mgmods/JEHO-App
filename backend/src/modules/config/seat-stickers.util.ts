/**
 * Seat mic reaction stickers — managed from dashboard (AppSetting `seat_stickers`).
 * Defaults point at `/assets/seat-stickers/eXX.gif|webp` on the API origin.
 */

export type SeatStickerItem = {
  id: string;
  key: string;
  url: string;
  name?: string;
  sortOrder: number;
  isActive: boolean;
};

export type SeatStickersConfig = {
  version: number;
  updatedAt?: string;
  items: SeatStickerItem[];
};

const KEY_RE = /^[a-z0-9_-]{1,32}$/i;
const MEDIA_RE = /\.(gif|webp|png|jpe?g)(\?|$)/i;

let cache: SeatStickersConfig | null = null;
let cacheAt = 0;
const CACHE_TTL_MS = 60_000;

function originBase(
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): string {
  return String(origin).replace(/\/$/, '');
}

function cleanUrl(raw: unknown): string {
  if (raw == null) return '';
  const s = String(raw).trim();
  if (!s) return '';
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('/')) return s;
  if (s.startsWith('uploads/') || s.startsWith('assets/')) return `/${s}`;
  return s;
}

function cleanKey(raw: unknown, fallback: string): string {
  const s = String(raw || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, '');
  if (KEY_RE.test(s)) return s;
  return fallback;
}

function defaultItems(
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): SeatStickerItem[] {
  const base = `${originBase(origin)}/assets/seat-stickers`;
  const items: SeatStickerItem[] = [];
  for (let n = 1; n <= 74; n++) {
    const key = `e${String(n).padStart(2, '0')}`;
    const ext = n <= 26 ? 'gif' : 'webp';
    items.push({
      id: key,
      key,
      url: `${base}/${key}.${ext}`,
      name: key.toUpperCase(),
      sortOrder: n,
      isActive: true,
    });
  }
  return items;
}

export function defaultSeatStickersConfig(
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): SeatStickersConfig {
  return {
    version: 1,
    items: defaultItems(origin),
  };
}

export function sanitizeSeatStickersConfig(
  raw: unknown,
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): SeatStickersConfig {
  let obj: any = raw;
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw);
    } catch {
      return defaultSeatStickersConfig(origin);
    }
  }
  if (!obj || typeof obj !== 'object') return defaultSeatStickersConfig(origin);

  const srcItems = Array.isArray(obj.items) ? obj.items : Array.isArray(obj) ? obj : [];
  const seen = new Set<string>();
  const items: SeatStickerItem[] = [];
  srcItems.forEach((row: any, idx: number) => {
    if (!row || typeof row !== 'object') return;
    const fallbackKey = `s${idx + 1}`;
    const key = cleanKey(row.key ?? row.id ?? row.code, fallbackKey);
    if (!KEY_RE.test(key) || seen.has(key)) return;
    const url = cleanUrl(row.url ?? row.imageUrl ?? row.src);
    if (!url) return;
    seen.add(key);
    items.push({
      id: String(row.id || key).slice(0, 64),
      key,
      url,
      name: row.name != null ? String(row.name).slice(0, 80) : key.toUpperCase(),
      sortOrder: Number.isFinite(Number(row.sortOrder))
        ? Math.floor(Number(row.sortOrder))
        : idx + 1,
      isActive: row.isActive !== false && row.active !== false,
    });
  });

  // Empty admin clear is allowed — clients get no stickers until re-seeded.
  items.sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key));
  return {
    version: Math.max(1, Math.floor(Number(obj.version) || 1)),
    updatedAt: obj.updatedAt ? String(obj.updatedAt) : undefined,
    items,
  };
}

export function seatStickersClientPayload(
  cfg: SeatStickersConfig,
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
) {
  const o = originBase(origin);
  const abs = (u: string) => {
    if (!u) return '';
    if (/^https?:\/\//i.test(u)) return u;
    return `${o}${u.startsWith('/') ? '' : '/'}${u}`;
  };
  const items = (cfg.items || [])
    .filter((i) => i && i.isActive !== false && i.key && i.url)
    .map((i) => ({
      id: i.id || i.key,
      key: i.key,
      url: abs(i.url),
      name: i.name || i.key,
      sortOrder: i.sortOrder,
    }));
  return {
    version: cfg.version,
    updatedAt: cfg.updatedAt || null,
    items,
    formats: ['gif', 'webp', 'png', 'jpg'],
  };
}

export function rememberSeatStickersCache(cfg: SeatStickersConfig) {
  cache = cfg;
  cacheAt = Date.now();
}

export function getCachedSeatStickers(): SeatStickersConfig | null {
  if (!cache) return null;
  if (Date.now() - cacheAt > CACHE_TTL_MS) return null;
  return cache;
}

export function isLikelySeatStickerUrl(url: string): boolean {
  if (!url) return false;
  if (MEDIA_RE.test(url)) return true;
  return url.includes('/uploads/') || url.includes('/assets/seat-stickers/');
}

/** Resolve active key → absolute URL (for realtime validation). */
export function resolveActiveStickerUrl(
  cfg: SeatStickersConfig | null | undefined,
  key: string,
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): string | null {
  if (!key) return null;
  const k = key.trim().toLowerCase();
  const items = cfg?.items || [];
  const hit = items.find((i) => i && i.isActive !== false && i.key === k && i.url);
  if (!hit) return null;
  const u = hit.url;
  if (/^https?:\/\//i.test(u)) return u;
  const o = originBase(origin);
  return `${o}${u.startsWith('/') ? '' : '/'}${u}`;
}
