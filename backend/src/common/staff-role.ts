/** Platform staff roles for in-app moderation (+ super dashboard flag). */
export type PlatformStaffRole = 'none' | 'manager' | 'super';

/** Resolve platform staff role from DB fields (legacy isAdmin ⇒ super). */
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
  if (raw === 'manager' || raw === 'moderator' || raw === 'mod') return 'manager';
  // Legacy dashboard flag without staffRole column filled in.
  if (user.isAdmin) return 'super';
  return 'none';
}

/** Browser dashboard + every /admin API: Super only (full powers). */
export function isDashboardSuper(user: {
  staffRole?: string | null;
  isAdmin?: boolean;
} | null | undefined): boolean {
  return normalizeStaffRole(user) === 'super';
}

export function staffRank(role: PlatformStaffRole): number {
  if (role === 'super') return 2;
  if (role === 'manager') return 1;
  return 0;
}
