import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { IS_PUBLIC_KEY } from '../decorators';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { User, UserStatus } from '../../database/entities/user.entity';
import { AdminRole, AdminUser } from '../../database/entities/admin-user.entity';
import {
  hasDashboardAccess,
  isDashboardSuper,
  normalizeStaffRole,
} from '../staff-role';
import {
  fullPermissions,
  normalizePermissions,
} from '../dashboard-permissions';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    private readonly dataSource: DataSource,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    const request = context.switchToHttp().getRequest();

    if (isPublic) {
      await this.tryAttachUser(request, false);
      return true;
    }

    const attached = await this.tryAttachUser(request, true);
    if (!attached) {
      throw new UnauthorizedException('Missing authentication token');
    }
    return true;
  }

  private async tryAttachUser(
    request: {
      headers: { authorization?: string };
      user?: Record<string, unknown>;
    },
    required: boolean,
  ): Promise<boolean> {
    const token = this.extractToken(request);
    if (!token) {
      if (required) throw new UnauthorizedException('Missing authentication token');
      return false;
    }
    try {
      const payload = await this.jwtService.verifyAsync(token, {
        secret: this.configService.get<string>('app.jwt.secret'),
      });
      // Dashboard sessions are backed by admin_users, not app users.
      // Resolve this repository from the root TypeORM DataSource so guarded
      // feature modules do not each need to register AdminUser in forFeature.
      // Accept older signed dashboard sessions that predate tokenType, but only when
      // the token explicitly identifies itself as an admin session. The admin must
      // still exist in admin_users and be active; ordinary app-user tokens are not promoted.
      if (
        payload.tokenType === 'dashboard' ||
        (payload.role === 'admin' && payload.isAdmin === true)
      ) {
        const admin = await this.dataSource.getRepository(AdminUser).findOne({
          where: { id: payload.sub },
        });
        if (!admin) {
          // Legacy dashboard tokens may share the same role claims as app staff.
          // If no admin_users row matches, continue resolving the token as an app user.
          // Explicit dashboard tokens must never fall through to app-user auth.
          if (payload.tokenType === 'dashboard') {
            if (required) throw new UnauthorizedException('Dashboard account is unavailable');
            return false;
          }
        } else {
          if (!admin.isActive) {
            if (required) throw new UnauthorizedException('Dashboard account is disabled or unavailable');
            return false;
          }
          // Normalize legacy and dashboard role spellings consistently. The
          // database role remains authoritative for legacy Super accounts.
          const staffRole = normalizeStaffRole({
            staffRole: admin.staffRole,
            isAdmin: admin.role === AdminRole.SUPER,
          });
          const superAdmin = staffRole === 'super' || admin.role === AdminRole.SUPER;
          const permissions = superAdmin
            ? fullPermissions()
            : normalizePermissions(admin.dashboardPermissions);
          request.user = {
            ...payload,
            sub: admin.id,
            username: admin.username,
            email: admin.email,
            isAdmin: true,
            isSuperAdmin: superAdmin,
            staffRole: superAdmin ? 'super' : 'operator',
            permissions,
            role: 'admin',
            isGuest: false,
            authSource: 'dashboard',
          };
          return true;
        }
      }

      const user = await this.usersRepo.findOne({ where: { id: payload.sub } });
      if (!user || user.status !== UserStatus.ACTIVE) {
        if (required) {
          throw new UnauthorizedException('Account is restricted or unavailable');
        }
        return false;
      }
      const staffRole = normalizeStaffRole(user);
      const superAdmin = isDashboardSuper(user);
      const dashboardAccess = hasDashboardAccess(user);
      const permissions = superAdmin
        ? fullPermissions()
        : staffRole === 'admin'
          ? fullPermissions()
          : staffRole === 'manager'
            ? normalizePermissions({ dashboard: 'read', rooms: 'write', streams: 'write' })
            : normalizePermissions(user.dashboardPermissions);
      request.user = {
        ...payload,
        sub: user.id,
        username: user.username,
        isAdmin: dashboardAccess,
        isSuperAdmin: superAdmin,
        staffRole,
        permissions,
        role: dashboardAccess ? 'admin' : 'user',
        isGuest: user.isGuest,
        authSource: 'app',
      };
      return true;
    } catch (error) {
      if (error instanceof UnauthorizedException) throw error;
      if (required) throw new UnauthorizedException('Invalid or expired token');
      return false;
    }
  }

  private extractToken(request: {
    headers: { authorization?: string };
  }): string | null {
    const auth = request.headers.authorization;
    if (!auth) return null;
    const [type, token] = auth.split(' ');
    return type === 'Bearer' ? token : null;
  }
}
