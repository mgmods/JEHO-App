/**
 * Bottom tab icons for JEHO CHAT (party / drama / games / chat / me).
 * Stored as AppSetting key `app_nav_icons`. PNG and JPG URLs supported.
 */

export type NavTabKey = 'party' | 'drama' | 'games' | 'chat' | 'me';

export type NavIconPair = {
  /** Label shown below the tab icon. */
  label?: string;
  /** Client route: home | messages | profile | drama | games. */
  route?: string;
  /** Whether the tab is visible. */
  enabled?: boolean;
  /** Lower values appear first. */
  sortOrder?: number;
  /** Default / unselected icon URL (png/jpg). */
  normal?: string;
  /** Selected / active icon URL (png/jpg). Falls back to normal. */
  selected?: string;
  /** Single icon used for both states when normal/selected omitted. */
  icon?: string;
};

export type NavIconsConfig = {
  version: number;
  updatedAt?: string;
  party?: NavIconPair;
  drama?: NavIconPair;
  games?: NavIconPair;
  chat?: NavIconPair;
  me?: NavIconPair;
};

export const NAV_TAB_KEYS: NavTabKey[] = ['party', 'drama', 'games', 'chat', 'me'];

const IMG_RE = /\.(png|jpe?g|webp|svg)(\?|$)/i;

function cleanUrl(raw: unknown): string {
  if (raw == null) return '';
  const s = String(raw).trim();
  if (!s) return '';
  // Allow absolute or site-relative uploads paths.
  if (s.startsWith('http://') || s.startsWith('https://') || s.startsWith('/')) return s;
  if (s.startsWith('uploads/') || s.startsWith('banners/')) return `/${s}`;
  return s;
}

function cleanPair(raw: any): NavIconPair | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const normal = cleanUrl(raw.normal ?? raw.unselected ?? raw.off);
  const selected = cleanUrl(raw.selected ?? raw.on ?? raw.active);
  const icon = cleanUrl(raw.icon ?? raw.url ?? raw.imageUrl);
  const pair: NavIconPair = {};
  const label = String(raw.label ?? '').trim();
  const route = String(raw.route ?? '').trim().toLowerCase();
  if (label) pair.label = label;
  if (route) pair.route = route;
  if (raw.enabled !== undefined) pair.enabled = raw.enabled !== false;
  if (Number.isFinite(Number(raw.sortOrder))) pair.sortOrder = Math.floor(Number(raw.sortOrder));
  if (normal) pair.normal = normal;
  if (selected) pair.selected = selected;
  if (icon) pair.icon = icon;
  if (!pair.normal && !pair.selected && !pair.icon) return undefined;
  return pair;
}

export function emptyNavIconsConfig(): NavIconsConfig {
  return defaultNavIconsConfig();
}

/** Shipped JEHO tab SVGs under /assets/nav-icons (same art as APK assets/icons). */
export function defaultNavIconsConfig(
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): NavIconsConfig {
  const base = `${String(origin).replace(/\/$/, '')}/assets/nav-icons`;
  const pair = (name: string): NavIconPair => ({
    normal: `${base}/ic_home_tab_${name}_normal.svg`,
    selected: `${base}/ic_home_tab_${name}_selected.svg`,
  });
  return {
    version: 1,
    party: { ...pair('party'), label: 'الرئيسية', route: 'home', enabled: true, sortOrder: 1 },
    drama: { ...pair('drama'), label: 'دراما', route: 'drama', enabled: false, sortOrder: 4 },
    games: { ...pair('game'), label: 'ألعاب', route: 'games', enabled: false, sortOrder: 5 },
    chat: { ...pair('chat'), label: 'الرسائل', route: 'messages', enabled: true, sortOrder: 2 },
    me: { ...pair('me'), label: 'أنا', route: 'profile', enabled: true, sortOrder: 3 },
  };
}

export function sanitizeNavIconsConfig(raw: unknown): NavIconsConfig {
  let obj: any = raw;
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw);
    } catch {
      return defaultNavIconsConfig();
    }
  }
  if (!obj || typeof obj !== 'object') return defaultNavIconsConfig();

  // Support { tabs: { party: {...} } } or flat { party: {...} }
  const src = obj.tabs && typeof obj.tabs === 'object' ? obj.tabs : obj;
  const defaults = defaultNavIconsConfig();
  const out: NavIconsConfig = {
    version: Math.max(1, Math.floor(Number(obj.version) || 1)),
    updatedAt: obj.updatedAt ? String(obj.updatedAt) : undefined,
  };
  for (const key of NAV_TAB_KEYS) {
    const pair = cleanPair(src[key]);
    if (pair && (pair.normal || pair.selected || pair.icon)) {
      const fallback = (defaults as any)[key] || {};
      (pair as any).label = pair.label || fallback.label || '';
      (pair as any).route = pair.route || fallback.route || '';
      if (pair.enabled === undefined) (pair as any).enabled = fallback.enabled !== false;
      if (pair.sortOrder === undefined) (pair as any).sortOrder = fallback.sortOrder || 99;
      (out as any)[key] = pair;
    } else {
      (out as any)[key] = (defaults as any)[key] || {};
    }
  }
  return out;
}

/** Absolute image URL for clients. */
export function resolveNavIconUrl(
  pair: NavIconPair | undefined | null,
  selected: boolean,
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
): string {
  if (!pair) return '';
  const base = selected
    ? pair.selected || pair.normal || pair.icon || ''
    : pair.normal || pair.icon || pair.selected || '';
  if (!base) return '';
  if (/^https?:\/\//i.test(base)) return base;
  const o = origin.replace(/\/$/, '');
  return `${o}${base.startsWith('/') ? '' : '/'}${base}`;
}

export function navIconsClientPayload(
  cfg: NavIconsConfig,
  origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr',
) {
  const map = (pair?: NavIconPair) => {
    const normal = resolveNavIconUrl(pair, false, origin);
    const selected = resolveNavIconUrl(pair, true, origin);
    return {
      normal: normal || '',
      selected: selected || normal || '',
    };
  };
  return {
    version: cfg.version,
    updatedAt: cfg.updatedAt || null,
    party: map(cfg.party),
    drama: map(cfg.drama),
    games: map(cfg.games),
    chat: map(cfg.chat),
    me: map(cfg.me),
    /** Hint for clients / docs. */
    formats: ['png', 'jpg', 'jpeg', 'webp', 'svg'],
  };
}

export function isLikelyImageUrl(url: string): boolean {
  if (!url) return false;
  if (IMG_RE.test(url)) return true;
  // uploads without extension still OK (served with content-type)
  return url.includes('/uploads/');
}
