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

    // Native Android staff policy. The role is reloaded from the database by JwtAuthGuard.
    // Keep this as an explicit allow-list: a newly added dashboard endpoint must not
    // silently become available to app staff.
    if (user.authSource === 'app') {
      const role = String(user.staffRole || '').toLowerCase();
      if (role === 'super' || user.isSuperAdmin) return true;

      const resolved = resolveAdminRequest(method, url);
      if (resolved.kind === 'deny' || resolved.kind === 'self' || resolved.kind === 'super') {
        throw new ForbiddenException('This action is reserved for Super Admin');
      }
      const path = url.split('?')[0].toLowerCase().replace(/^\/api\/v\d+/, '');
      const verb = method.toUpperCase();
      const readOnly = ['GET', 'HEAD', 'OPTIONS'].includes(verb);
      const isTemporaryBan = /^\/admin\/users\/[^/]+\/temporary-(ban|unban)$/.test(path);
      const isCosmeticGrant = verb === 'POST' && path === '/admin/cosmetics/grant';
      const isVanityAssign = verb === 'POST' && path === '/vanity-ids/admin/assign';
      const isVipAssign = verb === 'POST' && path === '/admin/vip/assign';

      // Shared restrictions for every non-super role.
      const alwaysSuperOnly =
        /\/admin\/operators(?:\/|$)/.test(path) ||
        /\/admin\/staff-identity(?:\/|$)/.test(path) ||
        /\/admin\/toast-manager(?:\/|$)/.test(path) ||
        /\/admin\/mysterious-man(?:\/|$)/.test(path) ||
        /\/admin\/lucky-gift-cannon(?:\/|$)/.test(path) ||
        /\/admin\/support-chest(?:\/|$)/.test(path) ||
        /\/admin\/notifications(?:\/|$)/.test(path) ||
        /\/admin\/room-pk-settings(?:\/|$)/.test(path) ||
        /\/admin\/security\/ban-ip(?:\/|$)/.test(path) ||
        /\/admin\/security\/ban-user(?:\/|$)/.test(path) ||
        /\/admin\/users\/maintenance\//.test(path) ||
        /\/admin\/users\/[^/]+\/reset-baseline(?:\/|$)/.test(path) ||
        /\/admin\/economy\/(settings\/reset|renormalize-gifts)(?:\/|$)/.test(path) ||
        /\/admin\/(settings|payment-settings|payments|binance-pay|fourthwall|sham-cash|zego-settings|voice-rtc-settings|cp-settings)(?:\/|$)/.test(path) ||
        /\/admin\/coin-packages(?:\/|$)/.test(path) ||
        /\/admin\/bulk(?:\/|$)/.test(path) ||
        /\/admin\/gifts\/(import-[^/]+|activate-all|rebuild-still-catalog)(?:\/|$)/.test(path) ||
        /\/admin\/rooms\/platform(?:\/|$)/.test(path) ||
        /\/admin\/rooms\/[^/]+\/platform-moderators(?:\/|$)/.test(path) ||
        (verb === 'DELETE' && /^\/admin\/rooms\/[^/]+$/.test(path)) ||
        // Permanent/global account actions and generic account edits are Super-only.
        ((verb === 'PATCH' || verb === 'DELETE') && /^\/admin\/users\/[^/]+$/.test(path)) ||
        (verb === 'PATCH' && /^\/admin\/users\/[^/]+\/status(?:\/|$)/.test(path)) ||
        /^\/admin\/users\/[^/]+\/(ban|unban)(?:\/|$)/.test(path) ||
        (verb === 'POST' && /^\/admin\/streams\/[^/]+\/force-end(?:\/|$)/.test(path));

      if (alwaysSuperOnly) {
        throw new ForbiddenException('This action is reserved for Super Admin');
      }

      if (role === 'admin') {
        // Admin may supervise users, rooms, streams, reports, agencies, and grant
        // approved account benefits. Catalog/configuration and financial controls
        // remain Super Admin-only.
        const allowedModules = new Set([
          'dashboard', 'users', 'rooms', 'streams', 'reports', 'agencies',
          'cosmetics', 'gifts', 'vip', 'vanityIds',
        ]);
        if (resolved.kind !== 'module' || !allowedModules.has(resolved.module)) {
          throw new ForbiddenException('This module is not available to Admin');
        }
        if (isTemporaryBan) return true; // service enforces 1–24h and own-ban unban.
        if (path.startsWith('/admin/users') && !readOnly) {
          throw new ForbiddenException('Admin may change user access only through owned temporary-ban endpoints');
        }
        if (path.startsWith('/admin/streams') && !readOnly &&
            !/^\/admin\/streams\/[^/]+\/end$/.test(path)) {
          throw new ForbiddenException('Admin may end a stream normally; forced or bulk stream actions are Super Admin-only');
        }
        if (path.startsWith('/admin/cosmetics')) {
          if (readOnly || isCosmeticGrant) return true;
          throw new ForbiddenException('Admin may grant existing cosmetics only');
        }
        if (path.startsWith('/admin/gifts')) {
          if (readOnly) return true;
          throw new ForbiddenException('Gift catalog edits are reserved for Super Admin');
        }
        if (path.startsWith('/admin/vip')) {
          if (readOnly || isVipAssign || (verb === 'POST' && /^\/admin\/vip\/[^/]+\/revoke$/.test(path))) return true;
          throw new ForbiddenException('Admin may grant or revoke an existing VIP membership only');
        }
        if (path.startsWith('/vanity-ids/admin/')) {
          if ((verb === 'GET' && path === '/vanity-ids/admin/all') || isVanityAssign) return true;
          throw new ForbiddenException('Admin may assign an existing vanity ID only');
        }
        // Admin may manage room settings and close a room, but cannot delete it.
        return true;
      }

      if (role === 'manager') {
        const allowedModules = new Set(['dashboard', 'rooms', 'streams', 'reports', 'cosmetics', 'gifts']);
        if (resolved.kind !== 'module' || !allowedModules.has(resolved.module)) {
          throw new ForbiddenException('Manager access is limited to room moderation, streams, reports, and frames/entrances');
        }
        if (path.startsWith('/admin/dashboard') && !readOnly) {
          throw new ForbiddenException('Manager dashboard access is read-only');
        }
        if (path.startsWith('/admin/rooms') && !readOnly) {
          throw new ForbiddenException('Manager cannot change room configuration or close/delete rooms');
        }
        if (path.startsWith('/admin/streams') && !readOnly &&
            !/^\/admin\/streams\/[^/]+\/end$/.test(path)) {
          throw new ForbiddenException('Manager may end a live stream normally; forced end is Super Admin-only');
        }
        if (path.startsWith('/admin/reports') && !readOnly &&
            !(/^\/admin\/reports\/[^/]+$/.test(path) && verb === 'PATCH') &&
            !(/^\/admin\/reports\/[^/]+\/(resolve|dismiss)$/.test(path) && verb === 'POST')) {
          throw new ForbiddenException('Manager may only resolve, dismiss, or update an existing report');
        }
        if (path.startsWith('/admin/cosmetics')) {
          if (readOnly || isCosmeticGrant) return true;
          throw new ForbiddenException('Manager may grant existing frames/entrances only');
        }
        if (path.startsWith('/admin/gifts')) {
          if (readOnly) return true;
          throw new ForbiddenException('Manager cannot edit the gift catalog');
        }
        // Managers may review and resolve reports, but not access account,
        // agency, wallet, staff, global settings, or identity controls.
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
