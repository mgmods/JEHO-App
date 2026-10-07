/**
 * Dashboard multi-admin ACL.
 * Super = full access. Operator = only modules granted as read|write.
 */

export const DASHBOARD_MODULES = [
  'dashboard',
  'conversations',
  'users',
  'agencies',
  'rooms',
  'gifts',
  'coins',
  'wallet',
  'withdrawals',
  'rechargeAgents',
  'vip',
  'vanityIds',
  'cosmetics',
  'offers',
  'promos',
  'luckyBoxes',
  'tasks',
  'hostTarget',
  'contests',
  'ranking',
  'roomCup',
  'games',
  'gameAds',
  'drama',
  'banners',
  'navIcons',
  'seatStickers',
  'reports',
  'notifications',
  'settings',
  'logs',
  'policyBrochure',
  'genderVerifications',
] as const;

export type DashboardModule = (typeof DASHBOARD_MODULES)[number];
export type PermLevel = 'none' | 'read' | 'write';

export type PermissionsMap = Partial<Record<DashboardModule, PermLevel>>;

const MODULE_SET = new Set<string>(DASHBOARD_MODULES);

/** Map first URL segment under /admin/* → module key (or super-only). */
const SEGMENT_MODULE: Record<string, DashboardModule | 'super-only' | 'self'> = {
  auth: 'self',
  operators: 'super-only',
  dashboard: 'dashboard',
  conversations: 'conversations',
  'policy-brochure': 'policyBrochure',
  users: 'users',
  agencies: 'agencies',
  rooms: 'rooms',
  gifts: 'gifts',
  coins: 'coins',
  'coin-packages': 'coins',
  wallet: 'wallet',
  withdrawals: 'withdrawals',
  withdraws: 'withdrawals',
  'recharge-agents': 'rechargeAgents',
  'recharge-agent-pricing': 'rechargeAgents',
  'recharge-agent-contacts': 'rechargeAgents',
  vip: 'vip',
  'vanity-ids': 'vanityIds',
  cosmetics: 'cosmetics',
  offers: 'offers',
  promos: 'promos',
  'lucky-boxes': 'luckyBoxes',
  tasks: 'tasks',
  'host-target': 'hostTarget',
  'host-targets': 'hostTarget',
  contests: 'contests',
  ranking: 'ranking',
  'room-cup': 'roomCup',
  games: 'games',
  'game-ads': 'gameAds',
  drama: 'drama',
  banners: 'banners',
  'nav-icons': 'navIcons',
  'seat-stickers': 'seatStickers',
  reports: 'reports',
  notifications: 'notifications',
  settings: 'settings',
  'payment-settings': 'settings',
  payments: 'settings',
  'binance-pay': 'settings',
  fourthwall: 'settings',
  'sham-cash': 'settings',
  zego: 'settings',
  livekit: 'settings',
  logs: 'logs',
  system: 'dashboard',
  'gender-verifications': 'genderVerifications',
  bulk: 'super-only',
  'plaza-events': 'banners',
};

/** Route name (vue-router) → module */
export const ROUTE_MODULE: Record<string, DashboardModule | 'super-only'> = {
  dashboard: 'dashboard',
  users: 'users',
  'user-detail': 'users',
  agencies: 'agencies',
  rooms: 'rooms',
  gifts: 'gifts',
  coins: 'coins',
  wallet: 'wallet',
  withdrawals: 'withdrawals',
  'recharge-agents': 'rechargeAgents',
  vip: 'vip',
  'vanity-ids': 'vanityIds',
  cosmetics: 'cosmetics',
  offers: 'offers',
  promos: 'promos',
  'lucky-boxes': 'luckyBoxes',
  tasks: 'tasks',
  'host-target': 'hostTarget',
  contests: 'contests',
  ranking: 'ranking',
  'room-cup': 'roomCup',
  games: 'games',
  'game-ads': 'gameAds',
  drama: 'drama',
  banners: 'banners',
  'nav-icons': 'navIcons',
  'seat-stickers': 'seatStickers',
  reports: 'reports',
  notifications: 'notifications',
  settings: 'settings',
  logs: 'logs',
  'policy-brochure': 'policyBrochure',
  'gender-verifications': 'genderVerifications',
};

export function emptyPermissions(): PermissionsMap {
  const out: PermissionsMap = {};
  for (const m of DASHBOARD_MODULES) out[m] = 'none';
  return out;
}

export function fullPermissions(): PermissionsMap {
  const out: PermissionsMap = {};
  for (const m of DASHBOARD_MODULES) out[m] = 'write';
  return out;
}

export function normalizePermissions(raw: unknown): PermissionsMap {
  const out = emptyPermissions();
  if (!raw || typeof raw !== 'object') return out;
  const src = raw as Record<string, unknown>;
  for (const key of Object.keys(src)) {
    if (!MODULE_SET.has(key)) continue;
    const level = String(src[key] || 'none').toLowerCase();
    if (level === 'write' || level === 'edit' || level === 'full') {
      out[key as DashboardModule] = 'write';
    } else if (level === 'read' || level === 'view') {
      out[key as DashboardModule] = 'read';
    } else {
      out[key as DashboardModule] = 'none';
    }
  }
  return out;
}

export function hasAnyPermission(perms: PermissionsMap | null | undefined): boolean {
  if (!perms) return false;
  return DASHBOARD_MODULES.some((m) => {
    const l = perms[m];
    return l === 'read' || l === 'write';
  });
}

export function canAccessModule(
  user: {
    isSuperAdmin?: boolean;
    staffRole?: string | null;
    permissions?: PermissionsMap | null;
  } | null | undefined,
  module: string,
  need: 'read' | 'write' = 'read',
): boolean {
  if (!user) return false;
  const role = String(user.staffRole || '').toLowerCase();
  if (user.isSuperAdmin || role === 'super') return true;

  const perms = user.permissions || {};
  const level = (perms as PermissionsMap)[module as DashboardModule] || 'none';
  if (need === 'write') return level === 'write';
  return level === 'read' || level === 'write';
}

function stripPath(url: string): string {
  const bare = String(url || '').split('?')[0];
  return bare
    .replace(/^https?:\/\/[^/]+/i, '')
    .replace(/^\/api\/v\d+\//i, '')
    .replace(/^\//, '');
}

/**
 * Resolve required module for an admin API call.
 * Returns { kind: 'ok' } | { kind: 'deny', message } | { kind: 'super' } | { kind: 'self' }
 */
export function resolveAdminRequest(
  method: string,
  url: string,
):
  | { kind: 'self' }
  | { kind: 'super' }
  | { kind: 'module'; module: DashboardModule; need: 'read' | 'write' }
  | { kind: 'deny'; message: string } {
  const path = stripPath(url);
  // path: admin/users/xxx
  const parts = path.split('/').filter(Boolean);
  if (parts[0] !== 'admin') {
    return { kind: 'deny', message: 'Invalid admin route' };
  }
  const segment = (parts[1] || '').toLowerCase();
  if (!segment) return { kind: 'module', module: 'dashboard', need: 'read' };

  const mapped = SEGMENT_MODULE[segment];
  if (mapped === 'self') return { kind: 'self' };
  if (mapped === 'super-only' || !mapped) {
    return { kind: 'super' };
  }
  const m = String(method || 'GET').toUpperCase();
  const need: 'read' | 'write' =
    m === 'GET' || m === 'HEAD' || m === 'OPTIONS' ? 'read' : 'write';
  return { kind: 'module', module: mapped, need };
}
