/**
 * Seat mic reaction stickers — managed from dashboard (AppSetting `seat_stickers`).
 * Admin-created catalog only; legacy bundled e01-e74 stickers are retired.
 */

export type SeatStickerItem = {
  id: string;
  key: string;
  url: string;
  name?: string;
  sortOrder: number;
  isActive: boolean;
  accessType: 'free' | 'paid' | 'vip' | 'cp';
  priceCoins: number;
  audience: 'all' | 'male' | 'female';
  requiredVipLevel: number;
  requiresCp: boolean;
  categoryId: string;
};

export type SeatStickerCategory = {
  id: string; name: string; iconUrl?: string; sortOrder: number; isActive: boolean;
  accessType: 'free' | 'paid' | 'vip' | 'cp'; priceCoins: number; durationDays: number;
  audience: 'all' | 'male' | 'female'; requiredVipLevel: number; requiresCp: boolean;
};

export type SeatStickersConfig = {
  categories: SeatStickerCategory[];
  version: number;
  updatedAt?: string;
  enabled: boolean;
  items: SeatStickerItem[];
};

const DEFAULT_CATEGORIES: SeatStickerCategory[] = [];

const KEY_RE = /^[a-z0-9_-]{1,32}$/i;
const MEDIA_RE = /\.(gif|webp|png|jpe?g)(\?|$)/i;

let cache: SeatStickersConfig | null = null;
let cacheAt = 0;
const CACHE_TTL_MS = 5_000;

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

function defaultItems(): SeatStickerItem[] {
  // No bundled/legacy stickers: admins create fresh categories and upload assets.
  return [];
}

export function defaultSeatStickersConfig(
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
 ): SeatStickersConfig {
  void origin;
  return {
    version: 4,
    enabled: true,
    categories: DEFAULT_CATEGORIES.map((x) => ({ ...x })),
    items: defaultItems(),
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
      accessType: ['free', 'paid', 'vip', 'cp'].includes(String(row.accessType)) ? row.accessType : 'free',
      priceCoins: Math.max(0, Math.floor(Number(row.priceCoins) || 0)),
      audience: ['all', 'male', 'female'].includes(String(row.audience)) ? row.audience : 'all',
      requiredVipLevel: Math.max(1, Math.min(7, Math.floor(Number(row.requiredVipLevel) || 1))),
      requiresCp: row.requiresCp === true,
      categoryId: cleanKey(row.categoryId, ''),
    });
  });

  const rawCategories = Array.isArray(obj.categories) ? obj.categories : DEFAULT_CATEGORIES;
  const categorySeen = new Set<string>();
  const categories: SeatStickerCategory[] = rawCategories.flatMap((row: any, idx: number) => {
    if (!row || typeof row !== 'object') return [];
    const id = cleanKey(row.id ?? row.key, 'category' + (idx + 1));
    if (id === 'classic' || categorySeen.has(id)) return [];
    categorySeen.add(id);
    const accessType = ['free', 'paid', 'vip', 'cp'].includes(String(row.accessType)) ? row.accessType : 'free';
    return [{
      id, name: String(row.name || id).slice(0, 80),
      iconUrl: cleanUrl(row.iconUrl || row.imageUrl) || undefined,
      sortOrder: Number.isFinite(Number(row.sortOrder)) ? Math.floor(Number(row.sortOrder)) : idx + 1,
      isActive: row.isActive !== false, accessType,
      priceCoins: Math.max(0, Math.floor(Number(row.priceCoins) || 0)),
      durationDays: Math.max(0, Math.min(3650, Math.floor(Number(row.durationDays) || 0))),
      audience: ['all', 'male', 'female'].includes(String(row.audience)) ? row.audience : 'all',
      requiredVipLevel: Math.max(1, Math.min(7, Math.floor(Number(row.requiredVipLevel) || 1))),
      requiresCp: row.requiresCp === true,
    }];
  });

  // Never silently erase a sticker because its category was omitted, renamed,
  // or supplied by an older dashboard. Keep every valid media URL visible by
  // attaching orphaned items to a safe general category.
  const legacyCatalog = Math.floor(Number(obj.version) || 0) < 4;
  if (!legacyCatalog && items.length) {
    if (!categories.length) {
      const id = 'general';
      categories.push({
        id, name: 'عام', sortOrder: 1, isActive: true, accessType: 'free',
        priceCoins: 0, durationDays: 0, audience: 'all',
        requiredVipLevel: 1, requiresCp: false,
      });
      categorySeen.add(id);
    }
    const fallbackCategoryId = categories[0].id;
    for (const item of items) {
      if (!item.categoryId || !categorySeen.has(item.categoryId)) {
        item.categoryId = fallbackCategoryId;
      }
    }
  }

  // Pre-v4 data is intentionally migrated only when it is the old bundled
  // catalog. A v4 dashboard save is always retained, including URL-only items.
  if (legacyCatalog) {
    categories.splice(0, categories.length);
    items.splice(0, items.length);
  }
  items.sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key));
  return {
    version: Math.max(4, Math.floor(Number(obj.version) || 4)),
    categories: categories.sort((a, b) => a.sortOrder - b.sortOrder),
    updatedAt: obj.updatedAt ? String(obj.updatedAt) : undefined,
    enabled: obj.enabled !== false,
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
  const activeCategories = (cfg.categories || []).filter((c) => c && c.isActive !== false);
  const items = (cfg.items || [])
    .filter((i) => cfg.enabled !== false && i && i.isActive !== false && i.key && i.url && activeCategories.some((c) => c.id === i.categoryId))
    .map((i) => {
      const category = activeCategories.find((c) => c.id === i.categoryId);
      const accessRank: Record<string, number> = { free: 0, paid: 1, vip: 2, cp: 3 };
      const itemAccess = i.accessType || 'free';
      const categoryAccess = category?.accessType || 'free';
      const accessType = (accessRank[categoryAccess] || 0) > (accessRank[itemAccess] || 0) ? categoryAccess : itemAccess;
      return {
        id: i.id || i.key, key: i.key, url: abs(i.url), name: i.name || i.key,
        sortOrder: i.sortOrder, accessType,
        priceCoins: Math.max(Number(i.priceCoins) || 0, Number(category?.priceCoins) || 0),
        audience: i.audience && i.audience !== 'all' ? i.audience : (category?.audience || 'all'),
        requiredVipLevel: Math.max(Number(i.requiredVipLevel) || 1, Number(category?.requiredVipLevel) || 1),
        requiresCp: i.requiresCp === true || category?.requiresCp === true || categoryAccess === 'cp',
        categoryId: i.categoryId, durationDays: Math.max(0, Number(category?.durationDays) || 0),
      };
    });
  return {
    version: cfg.version,
    updatedAt: cfg.updatedAt || null,
    enabled: cfg.enabled !== false,
    categories: activeCategories,
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
  if (cfg?.enabled === false) return null;
  const activeCategoryIds = new Set((cfg?.categories || []).filter((c) => c && c.isActive !== false).map((c) => c.id));
  const items = cfg?.items || [];
  const hit = items.find((i) => i && i.isActive !== false && i.key === k && i.url && activeCategoryIds.has(i.categoryId));
  if (!hit) return null;
  const u = hit.url;
  if (/^https?:\/\//i.test(u)) return u;
  const o = originBase(origin);
  return `${o}${u.startsWith('/') ? '' : '/'}${u}`;
}
