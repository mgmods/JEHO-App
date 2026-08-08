import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  Notification,
  NotificationType,
} from '../../database/entities/notification.entity';

/** Types that appear in the pinned «أخبار رسمية» chat (read-only). */
export const OFFICIAL_NEWS_TYPES: NotificationType[] = [
  NotificationType.SYSTEM,
  NotificationType.AGENCY,
  NotificationType.WALLET,
  NotificationType.VIP,
];
import { Device } from '../../database/entities/device.entity';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Room } from '../../database/entities/room.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { IsString, IsOptional, IsEnum, IsObject, IsUUID, IsBoolean } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { FirebaseAdminService } from './firebase-admin.service';
import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { RealtimeGateway } from '../realtime/realtime.gateway';

export class CreateNotificationDto {
  @ApiProperty()
  @IsUUID()
  userId: string;

  @ApiProperty({ enum: NotificationType })
  @IsEnum(NotificationType)
  type: NotificationType;

  @ApiProperty()
  @IsString()
  title: string;

  @ApiProperty()
  @IsString()
  body: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsObject()
  data?: Record<string, unknown>;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  sendPush?: boolean;
}

export class AdminPushDto {
  @ApiProperty()
  @IsString()
  title: string;

  @ApiProperty({ description: 'Plain text for preview / FCM (HTML is stripped if only rich content)' })
  @IsString()
  body: string;

  @ApiPropertyOptional({ description: 'Rich HTML for Official News bubble (optional)' })
  @IsOptional()
  @IsString()
  html?: string;

  @ApiPropertyOptional({ description: 'Alias of html' })
  @IsOptional()
  @IsString()
  bodyHtml?: string;

  @ApiPropertyOptional({ description: 'Promo / banner image URL' })
  @IsOptional()
  @IsString()
  imageUrl?: string;

  @ApiPropertyOptional({ description: 'CTA deep-link or https URL' })
  @IsOptional()
  @IsString()
  link?: string;

  @ApiPropertyOptional({ description: 'Alias of link' })
  @IsOptional()
  @IsString()
  url?: string;

  @ApiPropertyOptional({ enum: ['all', 'hosts', 'vip', 'user'] })
  @IsOptional()
  @IsString()
  audience?: 'all' | 'hosts' | 'vip' | 'user';

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  userId?: string;

  @ApiPropertyOptional({ enum: ['push', 'in_app', 'both'] })
  @IsOptional()
  @IsString()
  channel?: 'push' | 'in_app' | 'both';

  @ApiPropertyOptional({ enum: NotificationType })
  @IsOptional()
  @IsEnum(NotificationType)
  type?: NotificationType;
}

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    @InjectRepository(Notification)
    private readonly notifRepo: Repository<Notification>,
    @InjectRepository(Device)
    private readonly devicesRepo: Repository<Device>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(UserVip)
    private readonly vipRepo: Repository<UserVip>,
    @InjectRepository(Room)
    private readonly roomsRepo: Repository<Room>,
    private readonly firebase: FirebaseAdminService,
    private readonly realtime: RealtimeGateway,
  ) {}

  async create(dto: CreateNotificationDto) {
    const notif = await this.notifRepo.save(
      this.notifRepo.create({
        userId: dto.userId,
        type: dto.type,
        title: dto.title,
        body: dto.body,
        data: dto.data || null,
      }),
    );
    this.realtime.emitToUser(dto.userId, 'notification:new', notif);
    if (dto.sendPush !== false) {
      const sent = await this.sendFcm(dto.userId, dto.title, dto.body, {
        ...(dto.data || {}),
        type: dto.type,
        notificationId: notif.id,
        title: dto.title,
        body: dto.body,
      });
      if (sent) {
        notif.fcmSent = true;
        await this.notifRepo.save(notif);
      }
    }
    return notif;
  }

  async notifyUser(
    userId: string,
    type: NotificationType,
    title: string,
    body: string,
    data?: Record<string, unknown>,
  ) {
    try {
      return await this.create({
        userId,
        type,
        title,
        body,
        data,
        sendPush: true,
      });
    } catch (err) {
      this.logger.warn(`notifyUser failed: ${(err as Error).message}`);
      return null;
    }
  }

  async list(userId: string, query: PaginationDto) {
    const [items, total] = await this.notifRepo.findAndCount({
      where: { userId },
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  /** Official News feed (system / agency / wallet / vip) — chat-style inbox. */
  async listOfficial(userId: string, query: PaginationDto) {
    const take = Math.min(query.limit || 80, 200);
    const [items, total] = await this.notifRepo.findAndCount({
      where: { userId, type: In(OFFICIAL_NEWS_TYPES) },
      skip: query.skip,
      take,
      order: { createdAt: 'DESC' },
    });
    // Chronological for chat bubbles (oldest → newest).
    const chronological = [...items].reverse();
    return paginate(chronological, total, query.page || 1, take);
  }

  async officialPreview(userId: string) {
    const latest = await this.notifRepo.findOne({
      where: { userId, type: In(OFFICIAL_NEWS_TYPES) },
      order: { createdAt: 'DESC' },
    });
    const unread = await this.notifRepo.count({
      where: { userId, type: In(OFFICIAL_NEWS_TYPES), isRead: false },
    });
    return {
      unread,
      lastTitle: latest?.title || null,
      lastBody: latest
        ? plainTextPreview(latest.body, (latest.data as any)?.html || (latest.data as any)?.bodyHtml)
        : null,
      lastAt: latest?.createdAt || null,
      lastImageUrl:
        latest && latest.data && typeof (latest.data as any).imageUrl === 'string'
          ? (latest.data as any).imageUrl
          : null,
    };
  }

  async markRead(userId: string, id: string) {
    const notif = await this.notifRepo.findOne({ where: { id, userId } });
    if (!notif) throw new NotFoundException('Notification not found');
    notif.isRead = true;
    return this.notifRepo.save(notif);
  }

  async markAllRead(userId: string) {
    await this.notifRepo.update({ userId, isRead: false }, { isRead: true });
    return { success: true };
  }

  async markAllOfficialRead(userId: string) {
    await this.notifRepo.update(
      { userId, isRead: false, type: In(OFFICIAL_NEWS_TYPES) },
      { isRead: true },
    );
    return { success: true };
  }

  async unreadCount(userId: string) {
    const count = await this.notifRepo.count({ where: { userId, isRead: false } });
    return { count };
  }

  async officialUnreadCount(userId: string) {
    const count = await this.notifRepo.count({
      where: { userId, type: In(OFFICIAL_NEWS_TYPES), isRead: false },
    });
    return { count };
  }

  async sendFcm(
    userId: string,
    title: string,
    body: string,
    data?: Record<string, unknown>,
  ): Promise<boolean> {
    const devices = await this.devicesRepo.find({
      where: { userId, isActive: true },
    });
    const tokens = devices.map((d) => d.fcmToken).filter(Boolean) as string[];
    if (!tokens.length) return false;
    const result = await this.firebase.sendToTokens(tokens, title, body, data);
    return result.success > 0;
  }

  async adminSend(dto: AdminPushDto) {
    const type = dto.type || NotificationType.SYSTEM;
    const channel = dto.channel || 'both';
    const audience = dto.audience || (dto.userId ? 'user' : 'all');
    const userIds = await this.resolveAudience(audience, dto.userId);
    if (audience === 'user' && userIds.length === 0) {
      throw new BadRequestException(
        'لم يتم العثور على المستخدم — أدخل رقم المعرّف الظاهر (publicId) أو UUID',
      );
    }

    const richHtml = sanitizeOfficialHtml(dto.html || dto.bodyHtml || '');
    const imageUrl = sanitizeHttpUrl(dto.imageUrl || '');
    const link = sanitizeHttpUrl(dto.link || dto.url || '');
    // FCM + preview must stay plain text.
    const plainBody =
      String(dto.body || '')
        .replace(/<[^>]+>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim() || stripTags(richHtml) || dto.title;

    let saved = 0;
    let pushed = 0;

    const isOfficial = OFFICIAL_NEWS_TYPES.includes(type);
    for (const userId of userIds) {
      let notif: Notification | null = null;
      // Official news must always land in the in-app inbox (أخبار رسمية),
      // even when the dashboard channel is set to "push" only.
      const persistInbox =
        channel === 'in_app' || channel === 'both' || (channel === 'push' && isOfficial);
      const pushNow = channel === 'both' || channel === 'push';

      if (persistInbox) {
        notif = await this.create({
          userId,
          type,
          title: dto.title,
          body: plainBody,
          data: {
            audience,
            channel,
            officialNews: isOfficial,
            source: 'admin',
            targetUserId: userId,
            ...(richHtml ? { html: richHtml, bodyHtml: richHtml } : {}),
            ...(imageUrl ? { imageUrl } : {}),
            ...(link ? { url: link, link } : {}),
          },
          // create() already pushes when sendPush is true — avoid double FCM.
          sendPush: pushNow,
        });
        saved += 1;
        if (notif.fcmSent) pushed += 1;
      } else if (pushNow) {
        const ok = await this.sendFcm(userId, dto.title, plainBody, {
          type,
          audience,
          title: dto.title,
          body: plainBody,
          targetUserId: userId,
          ...(imageUrl ? { imageUrl } : {}),
          ...(link ? { url: link } : {}),
        });
        if (ok) pushed += 1;
      }
    }

    return {
      queued: true,
      audience,
      channel,
      targets: userIds.length,
      saved,
      pushed,
      title: dto.title,
      body: plainBody,
      hasHtml: !!richHtml,
      hasImage: !!imageUrl,
    };
  }

  /** Resolve dashboard target: UUID, numeric publicId, or username. */
  async resolveUserIdToken(raw?: string | null): Promise<string | null> {
    const token = String(raw || '').trim();
    if (!token) return null;
    const isUuid =
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        token,
      );
    if (isUuid) {
      const byId = await this.usersRepo.findOne({ where: { id: token } });
      return byId?.id || null;
    }
    if (/^\d+$/.test(token)) {
      const byPublic = await this.usersRepo.findOne({
        where: { publicId: token },
      });
      if (byPublic) return byPublic.id;
    }
    const byUsername = await this.usersRepo.findOne({
      where: { username: token.toLowerCase() },
    });
    return byUsername?.id || null;
  }

  private async resolveAudience(
    audience: string,
    userId?: string,
  ): Promise<string[]> {
    if (audience === 'user') {
      const resolved = await this.resolveUserIdToken(userId);
      return resolved ? [resolved] : [];
    }
    if (audience === 'hosts') {
      const rows = await this.roomsRepo
        .createQueryBuilder('r')
        .select('DISTINCT r.hostId', 'hostId')
        .where('r.hostId IS NOT NULL')
        .getRawMany<{ hostId: string }>();
      return rows.map((r) => r.hostId).filter(Boolean);
    }
    if (audience === 'vip') {
      const now = new Date();
      const rows = await this.vipRepo
        .createQueryBuilder('v')
        .select('DISTINCT v.userId', 'userId')
        .where('v.expiresAt > :now', { now })
        .getRawMany<{ userId: string }>();
      return rows.map((r) => r.userId).filter(Boolean);
    }
    // Full audience (no hard 5000 cap) — admin broadcast must reach everyone.
    const rows = await this.usersRepo
      .createQueryBuilder('u')
      .select('u.id', 'id')
      .getRawMany<{ id: string }>();
    return rows.map((r) => r.id).filter(Boolean);
  }

  async registerDevice(
    userId: string,
    payload: {
      deviceId: string;
      platform: string;
      fcmToken?: string;
      token?: string;
      model?: string;
      appVersion?: string;
    },
  ) {
    const fcmToken = payload.fcmToken || payload.token;
    let device = await this.devicesRepo.findOne({
      where: { userId, deviceId: payload.deviceId },
    });
    if (!device && fcmToken) {
      device = await this.devicesRepo.findOne({ where: { fcmToken } });
    }
    if (!device) {
      device = this.devicesRepo.create({
        userId,
        deviceId: payload.deviceId,
        platform: payload.platform,
      });
    }
    device.userId = userId;
    device.deviceId = payload.deviceId || device.deviceId;
    device.platform = payload.platform;
    device.fcmToken = fcmToken || device.fcmToken;
    device.model = payload.model || device.model;
    device.appVersion = payload.appVersion || device.appVersion;
    device.isActive = true;
    device.lastSeenAt = new Date();
    return this.devicesRepo.save(device);
  }

  async unregisterDevice(userId: string, deviceId: string) {
    await this.devicesRepo.update(
      { userId, deviceId },
      { isActive: false, fcmToken: null },
    );
    return { unregistered: true };
  }
}

/** Strip tags for inbox previews / FCM. */
function stripTags(value: string): string {
  return String(value || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

function plainTextPreview(body?: string | null, html?: string | null): string | null {
  const fromBody = stripTags(body || '');
  if (fromBody) return fromBody.slice(0, 240);
  const fromHtml = stripTags(html || '');
  return fromHtml ? fromHtml.slice(0, 240) : null;
}

/**
 * Lightweight allowlist sanitizer for admin Official News HTML.
 * Blocks script/style and inline handlers — keeps common formatting + links + images.
 */
function sanitizeOfficialHtml(raw: string): string {
  let html = String(raw || '').trim();
  if (!html) return '';
  if (html.length > 50_000) html = html.slice(0, 50_000);
  html = html
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?>[\s\S]*?<\/style>/gi, '')
    .replace(/<\/?(iframe|object|embed|form|input|button|meta|link|base)[^>]*>/gi, '')
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/data:text\/html/gi, '');
  return html.trim();
}

function sanitizeHttpUrl(raw: string): string {
  const s = String(raw || '').trim();
  if (!s) return '';
  if (s.length > 2_000) return '';
  const lower = s.toLowerCase();
  if (
    lower.startsWith('https://') ||
    lower.startsWith('http://') ||
    lower.startsWith('/') ||
    lower.startsWith('jeho://') ||
    lower.startsWith('auralive://')
  ) {
    return s;
  }
  // relative path without scheme — treat as app/CDN path
  if (s.startsWith('uploads/') || s.startsWith('assets/')) return '/' + s;
  return '';
}
