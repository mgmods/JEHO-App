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
 * Super: full access. Operator: modules granted in dashboardPermissions.
 * Managers (in-app only) never pass.
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
    } | null;

    if (!user || !hasDashboardAccess(user)) {
      throw new ForbiddenException(
        'Dashboard access required — Super or limited admin only',
      );
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

    const method = String(request.method || 'GET');
    const url = String(request.originalUrl || request.url || '');
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
