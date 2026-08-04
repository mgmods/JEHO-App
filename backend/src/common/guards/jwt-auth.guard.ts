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
import { Repository } from 'typeorm';
import { User, UserStatus } from '../../database/entities/user.entity';
import { normalizeStaffRole } from '../staff-role';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    const request = context.switchToHttp().getRequest();

    if (isPublic) {
      // Optional auth on public routes (e.g. profile isFollowing for logged-in viewers).
      await this.tryAttachUser(request, false);
      return true;
    }

    const attached = await this.tryAttachUser(request, true);
    if (!attached) {
      throw new UnauthorizedException('Missing authentication token');
    }
    return true;
  }

  /** Returns true when request.user was set. When required, invalid tokens throw. */
  private async tryAttachUser(
    request: { headers: { authorization?: string }; user?: Record<string, unknown> },
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
      const user = await this.usersRepo.findOne({ where: { id: payload.sub } });
      if (!user || user.status !== UserStatus.ACTIVE) {
        if (required) {
          throw new UnauthorizedException('Account is restricted or unavailable');
        }
        return false;
      }
      const staffRole = normalizeStaffRole(user);
      request.user = {
        ...payload,
        sub: user.id,
        username: user.username,
        isAdmin: user.isAdmin || staffRole === 'super',
        staffRole,
        role: user.isAdmin || staffRole === 'super' ? 'admin' : 'user',
        isGuest: user.isGuest,
      };
      return true;
    } catch (error) {
      if (error instanceof UnauthorizedException) throw error;
      if (required) throw new UnauthorizedException('Invalid or expired token');
      return false;
    }
  }

  private extractToken(request: { headers: { authorization?: string } }): string | null {
    const auth = request.headers.authorization;
    if (!auth) return null;
    const [type, token] = auth.split(' ');
    return type === 'Bearer' ? token : null;
  }
}
