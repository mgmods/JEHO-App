import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { isDashboardSuper } from '../staff-role';

/**
 * Browser admin dashboard + /admin/* APIs.
 * Only platform Super Admin (staffRole super / legacy isAdmin). Managers never pass.
 */
@Injectable()
export class AdminGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest();
    const user = request.user;
    if (!user || !isDashboardSuper(user)) {
      throw new ForbiddenException(
        'Super admin access required — dashboard is locked to Super only',
      );
    }
    return true;
  }
}
