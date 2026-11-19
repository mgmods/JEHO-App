import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  BadRequestException,
  NotFoundException,
  Injectable,
  Logger,
  SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Request } from 'express';
import { RedisService } from '../redis/redis.module';
import { AbuseLog, AbuseSeverity } from '../../database/entities/abuse-log.entity';
import { User, UserStatus } from '../../database/entities/user.entity';

export const SKIP_SECURITY_SHIELD = 'skipSecurityShield';
export const SkipSecurityShield = () => SetMetadata(SKIP_SECURITY_SHIELD, true);

const BAN_IP_PREFIX = 'shield:ban:ip:';
const BAN_USER_PREFIX = 'shield:ban:user:';
const BURST_PREFIX = 'shield:burst:';

@Injectable()
export class SecurityShieldService {
  private readonly logger = new Logger(SecurityShieldService.name);
  // The global guard runs on every authenticated API request. Cache the durable
  // users.status lookup briefly and coalesce concurrent misses so bursts of room
  // and dashboard requests do not issue one identical SELECT per request.
  private readonly userBanStatusCache = new Map<string, { banned: boolean; expiresAt: number }>();
  private readonly userBanStatusInFlight = new Map<string, Promise<boolean>>();
  private readonly userBanCacheEpoch = new Map<string, number>();
  private static readonly USER_BAN_CACHE_TTL_MS = 1_000;
  private static readonly USER_BAN_CACHE_MAX_ENTRIES = 10_000;

  constructor(
    private readonly redis: RedisService,
    @InjectRepository(AbuseLog)
    private readonly abuseLogs: Repository<AbuseLog>,
    @InjectRepository(User)
    private readonly users: Repository<User>,
  ) {}

  clientIp(req: Request): string {
    const xf = String(req.headers['x-forwarded-for'] || '')
      .split(',')[0]
      .trim();
    const real = String(req.headers['x-real-ip'] || '').trim();
    const raw = xf || real || req.ip || req.socket?.remoteAddress || '';
    return raw.replace(/^::ffff:/, '').slice(0, 64) || 'unknown';
  }

  async isIpBanned(ip: string): Promise<boolean> {
    if (!ip || ip === 'unknown' || ip === '127.0.0.1' || ip === '::1') return false;
    return !!(await this.redis.get(`${BAN_IP_PREFIX}${ip}`));
  }

  async isUserBanned(userId: string): Promise<boolean> {
    if (!userId) return false;

    // Redis-backed temporary bans remain authoritative and are checked on every
    // request; this cache only covers the durable PostgreSQL users.status lookup.
    if (await this.redis.get(BAN_USER_PREFIX + userId)) return true;

    const now = Date.now();
    const cached = this.userBanStatusCache.get(userId);
    if (cached && cached.expiresAt > now) return cached.banned;
    if (cached) this.userBanStatusCache.delete(userId);

    const pending = this.userBanStatusInFlight.get(userId);
    if (pending) return pending;

    const epoch = this.userBanCacheEpoch.get(userId) || 0;
    const lookup = (async () => {
      const user = await this.users.findOne({
        where: { id: userId },
        select: ['id', 'status'],
      });
      const banned = !!user && (
        user.status === UserStatus.BANNED || user.status === UserStatus.SUSPENDED
      );

      // Do not repopulate a cache entry after an admin status update invalidated it.
      if ((this.userBanCacheEpoch.get(userId) || 0) === epoch) {
        if (this.userBanStatusCache.size >= SecurityShieldService.USER_BAN_CACHE_MAX_ENTRIES) {
          for (const [key, value] of this.userBanStatusCache) {
            if (value.expiresAt <= now) this.userBanStatusCache.delete(key);
          }
          if (this.userBanStatusCache.size >= SecurityShieldService.USER_BAN_CACHE_MAX_ENTRIES) {
            const oldestKey = this.userBanStatusCache.keys().next().value;
            if (oldestKey) this.userBanStatusCache.delete(oldestKey);
          }
        }
        this.userBanStatusCache.set(userId, {
          banned,
          expiresAt: Date.now() + SecurityShieldService.USER_BAN_CACHE_TTL_MS,
        });
      }
      return banned;
    })();

    this.userBanStatusInFlight.set(userId, lookup);
    try {
      return await lookup;
    } finally {
      if (this.userBanStatusInFlight.get(userId) === lookup) {
        this.userBanStatusInFlight.delete(userId);
      }
    }
  }

  invalidateUserBanCache(userId: string): void {
    if (!userId) return;
    this.userBanCacheEpoch.set(userId, (this.userBanCacheEpoch.get(userId) || 0) + 1);
    this.userBanStatusCache.delete(userId);
  }

  async banIp(ip: string, ttlSeconds = 86_400, reason = 'abuse') {
    if (!ip || ip === 'unknown') return;
    await this.redis.set(`${BAN_IP_PREFIX}${ip}`, reason, ttlSeconds);
    this.logger.warn(`IP banned ${ip} (${ttlSeconds}s): ${reason}`);
  }

  async banUser(userId: string, ttlSeconds = 86_400, reason = 'abuse') {
    if (!userId) return;
    await this.redis.set(`${BAN_USER_PREFIX}${userId}`, reason, ttlSeconds);
    await this.users.update({ id: userId }, { status: UserStatus.SUSPENDED });
    this.invalidateUserBanCache(userId);
    this.logger.warn(`User banned ${userId} (${ttlSeconds}s): ${reason}`);
  }

  /**
   * Temporary staff ban: Redis TTL is the enforcement source. Do not mutate
   * users.status, because BANNED/SUSPENDED is a durable account restriction.
   */
  async createStaffTemporaryBan(
    userId: string,
    hours: number,
    reason: string,
    actorId: string,
  ) {
    const user = await this.users.findOne({
      where: { id: userId },
      select: ['id', 'status'],
    });
    if (!user) throw new NotFoundException('User not found');
    if (user.status === UserStatus.BANNED || user.status === UserStatus.SUSPENDED) {
      throw new BadRequestException('This account has a durable restriction; only Super Admin can review it');
    }
    const safeHours = Math.max(1, Math.min(24, Math.floor(Number(hours) || 1)));
    const expiresAt = new Date(Date.now() + safeHours * 60 * 60 * 1000).toISOString();
    const value = JSON.stringify({
      kind: 'staff-temporary-ban',
      actorId,
      reason: String(reason || 'admin temporary ban').slice(0, 500),
      expiresAt,
    });
    const key = `${BAN_USER_PREFIX}${userId}`;
    const created = await this.redis.setIfAbsent(key, value, safeHours * 3600);
    if (!created) {
      const existing = await this.redis.get(key);
      if (existing) {
        throw new BadRequestException('An active ban already exists for this account; Super Admin review is required');
      }
      throw new BadRequestException('Temporary-ban storage is unavailable; no ban was applied');
    }
    return { userId, hours: safeHours, expiresAt, actorId };
  }

  async removeStaffTemporaryBan(userId: string, actorId: string, isSuper: boolean) {
    const key = `${BAN_USER_PREFIX}${userId}`;
    const raw = await this.redis.get(key);
    if (!raw) throw new NotFoundException('No active temporary ban was found');
    let ban: { kind?: string; actorId?: string; expiresAt?: string };
    try { ban = JSON.parse(raw); } catch {
      throw new ForbiddenException('This is not a staff temporary ban; Super Admin review is required');
    }
    if (ban.kind !== 'staff-temporary-ban') {
      throw new ForbiddenException('This is not a staff temporary ban; Super Admin review is required');
    }
    if (!isSuper && ban.actorId !== actorId) {
      throw new ForbiddenException('You can only remove a temporary ban you issued');
    }
    const removed = await this.redis.deleteIfValueMatches(key, raw);
    if (!removed) {
      throw new BadRequestException('The temporary ban changed or expired; refresh the user and retry');
    }
    return { ok: true, userId, removedBy: actorId };
  }

  async recordAbuse(opts: {
    userId?: string | null;
    ip?: string | null;
    action: string;
    severity?: AbuseSeverity;
    details?: string;
    targetType?: string;
    targetId?: string;
  }) {
    try {
      await this.abuseLogs.save(
        this.abuseLogs.create({
          userId: opts.userId || null,
          ipAddress: opts.ip || null,
          action: opts.action.slice(0, 64),
          severity: opts.severity || AbuseSeverity.MEDIUM,
          details: opts.details || null,
          targetType: opts.targetType || null,
          targetId: opts.targetId || null,
        }),
      );
    } catch (err) {
      this.logger.debug(`abuse log failed: ${(err as Error).message}`);
    }
  }

  /**
   * Sliding burst counter. Returns true if over limit (should block).
   * Auto-bans IP on critical burst.
   */
  async checkBurst(
    key: string,
    limit: number,
    windowSec: number,
    opts?: { ip?: string; userId?: string; autoBan?: boolean },
  ): Promise<boolean> {
    const redisKey = `${BURST_PREFIX}${key}`;
    const n = await this.redis.incr(redisKey);
    if (n === 1) await this.redis.expire(redisKey, windowSec);
    if (n <= limit) return false;

    await this.recordAbuse({
      userId: opts?.userId,
      ip: opts?.ip,
      action: 'burst_limit',
      severity: AbuseSeverity.HIGH,
      details: `${key} count=${n} limit=${limit}/${windowSec}s`,
    });
    if (opts?.autoBan && opts.ip) {
      await this.banIp(opts.ip, 3600, `burst:${key}`);
    }
    return true;
  }
}

@Injectable()
export class SecurityShieldGuard implements CanActivate {
  private readonly logger = new Logger(SecurityShieldGuard.name);

  constructor(
    private readonly shield: SecurityShieldService,
    private readonly reflector: Reflector,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const skip = this.reflector.getAllAndOverride<boolean>(SKIP_SECURITY_SHIELD, [
      ctx.getHandler(),
      ctx.getClass(),
    ]);
    if (skip) return true;

    const req = ctx.switchToHttp().getRequest<Request & { user?: { sub?: string } }>();
    if (!req) return true;

    const ip = this.shield.clientIp(req);
    (req as any).clientIp = ip;

    if (await this.shield.isIpBanned(ip)) {
      this.logger.warn(`blocked banned IP ${ip} ${req.method} ${req.url}`);
      throw new ForbiddenException('Access denied');
    }

    const userId = req.user?.sub;
    if (userId && (await this.shield.isUserBanned(userId))) {
      throw new ForbiddenException('Account suspended');
    }

    // UUID spoofing: reject body.userId that differs from JWT (IDOR bait).
    // Skip admin APIs and room moderation routes that intentionally target another user
    // (mute/kick/ban/seat invite/approve, cohost, etc.).
    const path = String(req.url || '').split('?')[0];
    const isAdminPath = path.includes('/admin/');
    const allowsForeignUserId =
      isAdminPath ||
      /\/rooms\/[^/]+\/(mic|kick|ban|cohost|seat-invites|task-invites|seat-requests\/(?:approve|reject))$/.test(
        path,
      ) ||
      /\/agencies\/[^/]+\/(distribute|members)$/.test(path);
    const body = req.body as Record<string, unknown> | undefined;
    if (!allowsForeignUserId && userId && body && typeof body === 'object') {
      for (const k of ['userId', 'user_id', 'uid']) {
        const v = body[k];
        if (typeof v === 'string' && v.length > 8 && v !== userId) {
          await this.shield.recordAbuse({
            userId,
            ip,
            action: 'idor_attempt',
            severity: AbuseSeverity.HIGH,
            details: `${k}=${v} path=${req.url}`,
          });
          const over = await this.shield.checkBurst(
            `idor:${ip}`,
            5,
            600,
            { ip, userId, autoBan: true },
          );
          if (over) throw new ForbiddenException('Access denied');
          throw new ForbiddenException('Invalid identity claim');
        }
      }
    }

    return true;
  }
}
