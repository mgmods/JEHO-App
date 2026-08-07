/** Platform staff roles for in-app moderation + dashboard multi-admin. */
export type PlatformStaffRole = 'none' | 'manager' | 'operator' | 'super';

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
    raw === 'superadmin' ||
    raw === 'admin'
  ) {
    return 'super';
  }
  if (
    raw === 'operator' ||
    raw === 'dashboard_operator' ||
    raw === 'admin_op' ||
    raw === 'staff'
  ) {
    return 'operator';
  }
  if (raw === 'manager' || raw === 'moderator' || raw === 'mod') return 'manager';
  // Legacy dashboard flag without staffRole column filled in.
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
  return role === 'super' || role === 'operator';
}

export function staffRank(role: PlatformStaffRole): number {
  if (role === 'super') return 3;
  if (role === 'operator') return 2;
  if (role === 'manager') return 1;
  return 0;
}
