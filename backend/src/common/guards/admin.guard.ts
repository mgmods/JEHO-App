import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import {
  hasDashboardAccess,
  isDashboardSuper,
} from '../staff-role';
import {
  canAccessModule,
  hasAnyPermission,
  normalizePermissions,
  resolveAdminRequest,
  type PermissionsMap,
} from '../dashboard-permissions';

/**
 * Browser admin dashboard + /admin/* APIs.
 * Dashboard sessions retain their operator ACL. Authenticated app staff use a
 * separate role policy: Super = full, Admin = all non-super-only modules,
 * Manager = room and live-stream operations only.
 */
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest();
    const user = request.user as {
      staffRole?: string | null;
      isAdmin?: boolean;
      isSuperAdmin?: boolean;
      permissions?: PermissionsMap | null;
      authSource?: string;
    } | null;

    if (!user) {
      throw new ForbiddenException('Staff access required');
    }

    const method = String(request.method || 'GET');
    const url = String(request.originalUrl || request.url || '');

    // Native Android admin console uses the regular app session. Never treat
    // ordinary users as staff; JwtAuthGuard reloads the current DB role first.
    if (user.authSource === 'app') {
      const role = String(user.staffRole || '').toLowerCase();
      if (role === 'super' || user.isSuperAdmin) return true;
      const resolved = resolveAdminRequest(method, url);
      if (resolved.kind === 'deny' || resolved.kind === 'self' || resolved.kind === 'super') {
        throw new ForbiddenException('This action is reserved for Super Admin');
      }
      const path = url.split('?')[0].toLowerCase();
      const superOnlyPath =
        /\/admin\/users\/maintenance\/reset-all-baseline(?:\/|$)/.test(path) ||
        /\/admin\/users\/[^/]+\/reset-baseline(?:\/|$)/.test(path) ||
        /\/admin\/security\/ban-ip(?:\/|$)/.test(path) ||
        /\/admin\/economy\/settings\/reset(?:\/|$)/.test(path) ||
        /\/admin\/economy\/renormalize-gifts(?:\/|$)/.test(path) ||
        /\/admin\/gifts\/(?:import-[^/]+|activate-all|rebuild-still-catalog)(?:\/|$)/.test(path) ||
        /\/admin\/rooms\/platform(?:\/|$)/.test(path) ||
        /\/admin\/rooms\/[^/]+\/platform-moderators(?:\/|$)/.test(path) ||
        // Granting account-bound benefits is Super Admin only; staff may
        // review catalogs but cannot mint VIP, cosmetics, or vanity IDs.
        /\\/admin\\/vip\\/assign(?:\\/|$)/.test(path) ||
        /\\/admin\\/cosmetics\\/grant(?:\\/|$)/.test(path) ||
        /\\/vanity-ids\\/admin\\/assign(?:\\/|$)/.test(path);
      if (role === 'admin' && superOnlyPath) {
        throw new ForbiddenException('This action is reserved for Super Admin');
      }
      if (role === 'admin') return true;
      if (role === 'manager') {
        if (resolved.kind !== 'module' || !['dashboard', 'rooms', 'streams'].includes(resolved.module)) {
          throw new ForbiddenException('Manager access is limited to rooms and live streams');
        }
        if (/\/admin\/rooms\/platform(?:\/|$)/.test(path) || /platform-moderators/.test(path) || /\/force-end(?:\/|$)/.test(path)) {
          throw new ForbiddenException('This room operation requires Admin or Super Admin');
        }
        return true;
      }
      throw new ForbiddenException('Staff access required');
    }

    if (user.authSource !== 'dashboard' || !hasDashboardAccess(user)) {
      throw new ForbiddenException('Dashboard access required — Super or limited admin only');
    }

    if (isDashboardSuper(user) || user.isSuperAdmin) {
      return true;
    }

    // Operators need at least one module granted.
    const perms = normalizePermissions(user.permissions);
    if (!hasAnyPermission(perms)) {
      throw new ForbiddenException(
        'No dashboard modules granted — ask Super admin for permissions',
      );
    }

    const resolved = resolveAdminRequest(method, url);

    if (resolved.kind === 'self') {
      return true;
    }
    if (resolved.kind === 'super' || resolved.kind === 'deny') {
      throw new ForbiddenException(
        resolved.kind === 'deny'
          ? resolved.message
          : 'This action is reserved for Super admin only',
      );
    }

    const ok = canAccessModule(
      { staffRole: 'operator', permissions: perms },
      resolved.module,
      resolved.need,
    );
    if (!ok) {
      throw new ForbiddenException(
        resolved.need === 'write'
          ? `No write permission for module: ${resolved.module}`
          : `No read permission for module: ${resolved.module}`,
      );
    }
    return true;
  }
}
