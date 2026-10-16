/**
 * Bottom tab icons for JEHO CHAT (party / drama / games / chat / me).
 * Stored as AppSetting key `app_nav_icons`. PNG and JPG URLs supported.
 */

export type NavTabKey = 'party' | 'drama' | 'games' | 'chat' | 'me';
export const PROFILE_ACTION_KEYS = ['events', 'agency', 'recharge', 'tasks', 'settings', 'support'] as const;
export type ProfileActionKey = typeof PROFILE_ACTION_KEYS[number];

export type NavIconPair = {
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
  /** Single uploaded icon for each shortcut in the profile (أنا) grid. */
  profileActions?: Partial<Record<ProfileActionKey, string>>;
};

export const NAV_TAB_KEYS: NavTabKey[] = ['party', 'drama', 'games', 'chat', 'me'];

const IMG_RE = /\.(png|jpe?g|webp|svg)(\?|$)/i;

/** Public media is served at host root, not below Nest's /api/v1 prefix. */
function publicOrigin(raw?: string): string {
  const value = String(raw || '').trim() || 'https://jeho-own-backend.mgs.blitz.cloud';
  return value.replace(/\/+$/, '').replace(/\/api\/v\d+$/i, '');
}

function cleanUrl(raw: unknown): string {
  if (raw == null) return '';
  const s = String(raw).trim();
  if (!s) return '';
  // Uploads belong to the active JEHO-OWN deployment. If older dashboard
  // versions saved an absolute URL from the legacy host, retain only its
  // /uploads path so clients resolve it against the current API origin.
  if (s.startsWith('http://') || s.startsWith('https://')) {
    try {
      const parsed = new URL(s);
      if (parsed.pathname.startsWith('/uploads/')) return parsed.pathname + parsed.search;
    } catch {
      // Preserve an unparseable absolute URL; the client can fall back safely.
    }
    return s;
  }
  // Allow site-relative uploads paths and other absolute URLs.
  if (s.startsWith('/')) return s;
  if (s.startsWith('uploads/') || s.startsWith('banners/')) return `/${s}`;
  return s;
}

function cleanPair(raw: any): NavIconPair | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const normal = cleanUrl(raw.normal ?? raw.unselected ?? raw.off);
  const selected = cleanUrl(raw.selected ?? raw.on ?? raw.active);
  const icon = cleanUrl(raw.icon ?? raw.url ?? raw.imageUrl);
  const pair: NavIconPair = {};
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
  origin = process.env.PUBLIC_API_URL || process.env.API_PUBLIC_URL || process.env.PUBLIC_API_ORIGIN || 'https://jeho-own-backend.mgs.blitz.cloud',
): NavIconsConfig {
  const base = `${publicOrigin(origin)}/assets/nav-icons`;
  const pair = (name: string): NavIconPair => ({
    normal: `${base}/ic_home_tab_${name}_normal.svg`,
    selected: `${base}/ic_home_tab_${name}_selected.svg`,
  });
  return {
    version: 1,
    party: pair('party'),
    drama: pair('drama'),
    games: pair('game'),
    chat: pair('chat'),
    me: pair('me'),
    profileActions: {},
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
      (out as any)[key] = pair;
    } else {
      (out as any)[key] = (defaults as any)[key] || {};
    }
  }
  const rawActions = src.profileActions && typeof src.profileActions === 'object' ? src.profileActions : {};
  const profileActions: Partial<Record<ProfileActionKey, string>> = {};
  for (const key of PROFILE_ACTION_KEYS) {
    const url = cleanUrl(rawActions[key]);
    if (url) profileActions[key] = url;
  }
  out.profileActions = profileActions;
  return out;
}

/** Absolute image URL for clients. */
export function resolveNavIconUrl(
  pair: NavIconPair | undefined | null,
  selected: boolean,
  origin = process.env.PUBLIC_API_URL || process.env.API_PUBLIC_URL || process.env.PUBLIC_API_ORIGIN || 'https://jeho-own-backend.mgs.blitz.cloud',
): string {
  if (!pair) return '';
  const base = selected
    ? pair.selected || pair.normal || pair.icon || ''
    : pair.normal || pair.icon || pair.selected || '';
  if (!base) return '';
  if (/^https?:\/\//i.test(base)) return base;
  const o = publicOrigin(origin);
  return `${o}${base.startsWith('/') ? '' : '/'}${base}`;
}

export function navIconsClientPayload(
  cfg: NavIconsConfig,
  origin = process.env.PUBLIC_API_URL || process.env.API_PUBLIC_URL || process.env.PUBLIC_API_ORIGIN || 'https://jeho-own-backend.mgs.blitz.cloud',
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
    profileActions: Object.fromEntries(
      PROFILE_ACTION_KEYS.map((key) => [key, resolveNavIconUrl({ icon: cfg.profileActions?.[key] }, false, origin) || '']),
    ),
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
