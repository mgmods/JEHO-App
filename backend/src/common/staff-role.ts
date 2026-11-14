/** Platform staff roles for in-app moderation + dashboard multi-admin. */
export type PlatformStaffRole = 'none' | 'manager' | 'operator' | 'admin' | 'super';

/** Resolve platform staff role from DB fields (legacy isAdmin ⇒ super, not operators). */
export function normalizeStaffRole(user: {
  staffRole?: string | null;
  isAdmin?: boolean;
} | null | undefined): PlatformStaffRole {
  if (!user) return 'none';
  const raw = String(user.staffRole || '')
    .trim()
    .toLowerCase();
  if (
    raw === 'super' ||
    raw === 'super_admin' ||
    raw === 'superadmin'
  ) {
    return 'super';
  }
  if (raw === 'admin') return 'admin';
  if (
    raw === 'operator' ||
    raw === 'dashboard_operator' ||
    raw === 'admin_op' ||
    raw === 'staff'
  ) {
    return 'operator';
  }
  if (raw === 'manager' || raw === 'moderator' || raw === 'mod') return 'manager';
  // Only use the legacy isAdmin flag when staffRole is genuinely absent.
  // An explicit non-staff role (for example "user" after demotion) must win,
  // otherwise stale isAdmin=true would resurrect Super Admin after downgrade.
  if (raw.length > 0) return 'none';
  if (user.isAdmin) return 'super';
  return 'none';
}

/** Full owner of the browser dashboard (can manage other admins). */
export function isDashboardSuper(user: {
  staffRole?: string | null;
  isAdmin?: boolean;
} | null | undefined): boolean {
  return normalizeStaffRole(user) === 'super';
}

/** Limited dashboard staff with per-module permissions. */
export function isDashboardOperator(user: {
  staffRole?: string | null;
  isAdmin?: boolean;
} | null | undefined): boolean {
  return normalizeStaffRole(user) === 'operator';
}

/** Can log into the browser admin panel. */
export function hasDashboardAccess(user: {
  staffRole?: string | null;
  isAdmin?: boolean;
} | null | undefined): boolean {
  const role = normalizeStaffRole(user);
  return role === 'super' || role === 'admin' || role === 'operator' || role === 'manager';
}

export function staffRank(role: PlatformStaffRole): number {
  if (role === 'super') return 4;
  if (role === 'admin') return 3;
  if (role === 'operator') return 2;
  if (role === 'manager') return 1;
  return 0;
}
