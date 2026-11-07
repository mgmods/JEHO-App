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


/** Direct-message alerts belong to the conversation unread badge, never notification inboxes. */
function isDirectMessageNotification(notification: Notification): boolean {
  const data = (notification.data || {}) as Record<string, unknown>;
  return notification.type === NotificationType.CHAT && data.type === 'chat' && typeof data.conversationId === 'string';
}

/** Lucky-gift win celebrations stay in the live room/app; never send an external FCM push. */
function isLuckyWinPayload(data?: Record<string, unknown>, title?: string, body?: string): boolean {
  // Inspect all payload strings (including nested metadata) because older
  // producers used different keys such as eventType, giftType or rewardSource.
  const values: string[] = [];
  const collect = (value: unknown, depth = 0) => {
    if (depth > 4 || value == null) return;
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      values.push(String(value).toLowerCase());
      return;
    }
    if (Array.isArray(value)) {
      value.forEach(item => collect(item, depth + 1));
      return;
    }
    if (typeof value === 'object') {
      Object.values(value as Record<string, unknown>).forEach(item => collect(item, depth + 1));
    }
  };
  collect(data);
  const markers = values.join(' ');
  const copy = `${String(title || '')} ${String(body || '')}`.toLowerCase();
  const luckyKind = /(lucky|luckygift|lucky_gift|lucky-box|lucky_box|هدية الحظ|الهدية المحظوظة|محظوظ)/i.test(markers);
  const winKind = /(hit|win|reward|won|success|payout|فوز|فاز|ربح|ربحت)/i.test(markers);
  const arabicWin = /((فاز|فازت|ربح|ربحت|فوز|مكسب).{0,28}(هدية الحظ|الهدية المحظوظة|محظوظ)|(هدية الحظ|الهدية المحظوظة|محظوظ).{0,28}(فاز|فازت|ربح|ربحت|فوز|مكسب))/i.test(copy);
  const englishWin = /(lucky|luckygift|lucky_gift|lucky-box|lucky_box).{0,32}(hit|win|reward|won|success|payout)|(hit|win|reward|won|success|payout).{0,32}(lucky|luckygift|lucky_gift|lucky-box|lucky_box)/i.test(copy);
  return (luckyKind && winKind) || arabicWin || englishWin;
}

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
    // Persist first. Realtime and push delivery are best-effort and must never
    // turn a successfully stored in-app notification into a reported failure.
    try {
      this.realtime.emitToUser(dto.userId, 'notification:new', notif);
    } catch (err) {
      this.logger.warn(`Realtime notification emit failed: ${(err as Error).message}`);
    }
    if (dto.sendPush !== false && !isLuckyWinPayload(dto.data, dto.title, dto.body)) {
      try {
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
      } catch (err) {
        this.logger.warn(`Push notification delivery failed: ${(err as Error).message}`);
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
    // Legacy direct-message alerts are hidden here too; only the conversation thread owns them.
    const rows = await this.notifRepo.find({ where: { userId }, order: { createdAt: 'DESC' } });
    const items = rows.filter((notification) => !isDirectMessageNotification(notification));
    const page = Math.max(1, query.page || 1);
    const limit = Math.min(200, Math.max(1, query.limit || 20));
    const pageItems = items.slice((page - 1) * limit, page * limit);
    return paginate(pageItems, items.length, page, limit);
  }

  /** Super-admin messages are stored separately from dashboard Official News. */
  async listAdminMessages(userId: string, query: PaginationDto) {
    const rows = await this.notifRepo.find({
      where: { userId, type: NotificationType.CHAT },
      order: { createdAt: 'DESC' },
    });
    const messages = rows.filter((n) => {
      const data = (n.data || {}) as any;
      return data.adminMessage === true || data.source === 'admin';
    });
    const page = Math.max(1, query.page || 1);
    const limit = Math.min(200, Math.max(1, query.limit || 80));
    const chronological = messages.slice((page - 1) * limit, page * limit).reverse();
    return paginate(chronological, messages.length, page, limit);
  }

  async adminMessagesPreview(userId: string) {
    const rows = await this.notifRepo.find({
      where: { userId, type: NotificationType.CHAT },
      order: { createdAt: 'DESC' },
    });
    const messages = rows.filter((n) => {
      const data = (n.data || {}) as any;
      return data.adminMessage === true || data.source === 'admin';
    });
    const latest = messages[0];
    return {
      unread: messages.filter((n) => !n.isRead).length,
      lastTitle: latest?.title || null,
      lastBody: latest?.body || null,
      lastAt: latest?.createdAt || null,
    };
  }

  async markAllAdminMessagesRead(userId: string) {
    const rows = await this.notifRepo.find({
      where: { userId, type: NotificationType.CHAT, isRead: false },
    });
    const ids = rows.filter((n) => {
      const data = (n.data || {}) as any;
      return data.adminMessage === true || data.source === 'admin';
    }).map((n) => n.id);
    if (ids.length) await this.notifRepo.update({ id: In(ids), userId }, { isRead: true });
    return { success: true, updated: ids.length };
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
    const unread = await this.notifRepo.find({ where: { userId, isRead: false } });
    const ids = unread.filter((notification) => !isDirectMessageNotification(notification)).map((notification) => notification.id);
    if (ids.length) await this.notifRepo.update({ id: In(ids), userId }, { isRead: true });
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
    const unread = await this.notifRepo.find({ where: { userId, isRead: false } });
    const count = unread.filter((notification) => !isDirectMessageNotification(notification)).length;
    return { count };
  }

  async officialUnreadCount(userId: string) {
    const count = await this.notifRepo.count({
      where: { userId, type: In(OFFICIAL_NEWS_TYPES), isRead: false },
    });
    return { count };
  }

  /** Send a data-only direct-message push without adding it to Official News. */
  async sendDirectMessagePush(
    recipientUserId: string,
    payload: {
      conversationId: string;
      messageId: string;
      senderId: string;
      senderName: string;
      avatarUrl?: string | null;
      body: string;
    },
  ): Promise<boolean> {
    if (!recipientUserId || !payload.conversationId || !payload.messageId) return false;
    try {
      return await this.sendFcm(
        recipientUserId,
        payload.senderName || 'JEHO CHAT',
        String(payload.body || 'لديك رسالة جديدة').slice(0, 180),
        {
          type: 'chat',
          notificationKind: 'direct_message',
          conversationId: payload.conversationId,
          messageId: payload.messageId,
          senderId: payload.senderId,
          peer_id: payload.senderId,
          senderName: payload.senderName || 'JEHO CHAT',
          avatarUrl: payload.avatarUrl || '',
        },
      );
    } catch (err) {
      this.logger.warn(`Direct-message push failed: ${(err as Error).message}`);
      return false;
    }
  }

  /** Admin-only diagnostics: counts only, never returns device tokens or credentials. */
  async fcmDeliveryStatus() {
    const activeDevices = await this.devicesRepo.count({ where: { isActive: true } });
    const tokenDevices = await this.devicesRepo.createQueryBuilder('device')
      .where('device.isActive = :active', { active: true })
      .andWhere('device.fcmToken IS NOT NULL')
      .andWhere("device.fcmToken <> ''")
      .getCount();
    return {
      firebaseReady: this.firebase.isReady(),
      activeDevices,
      tokenDevices,
      activeDevicesMissingToken: Math.max(0, activeDevices - tokenDevices),
      checkedAt: new Date().toISOString(),
    };
  }

  /** Send a real push to the authenticated admin device for end-to-end FCM verification. */
  async sendFcmTest(userId: string) {
    const devices = await this.devicesRepo.find({ where: { userId, isActive: true } });
    const tokenDevices = devices.filter((device) => !!device.fcmToken && !!device.fcmToken.trim()).length;
    const firebaseReady = this.firebase.isReady();
    if (!firebaseReady || tokenDevices === 0) {
      return {
        sent: false,
        firebaseReady,
        activeDevices: devices.length,
        tokenDevices,
        reason: !firebaseReady ? 'firebase_not_ready' : 'no_registered_device_token',
      };
    }
    const title = 'JEHO CHAT';
    const body = 'هذا إشعار اختبار للتأكد من عمل إشعارات الرسائل';
    const sent = await this.sendFcm(userId, title, body, {
      type: 'system',
      notificationKind: 'fcm_test',
      title,
      body,
      test: 'true',
    });
    return {
      sent,
      firebaseReady,
      activeDevices: devices.length,
      tokenDevices,
      reason: sent ? null : 'fcm_rejected_or_delivery_failed',
    };
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
    const tokens = Array.from(new Set(
      devices.map((d) => d.fcmToken).filter((token): token is string => !!token && !!token.trim()),
    ));
    if (!tokens.length) {
      this.logger.warn(`FCM not sent: no active device token for user=${userId}`);
      return false;
    }
    if (!this.firebase.isReady()) {
      this.logger.error(`FCM not sent: Firebase Admin unavailable user=${userId} devices=${devices.length}`);
      return false;
    }
    const result = await this.firebase.sendToTokens(tokens, title, body, data);
    if (result.invalidTokens?.length) {
      await this.devicesRepo.update(
        { fcmToken: In(result.invalidTokens) },
        { isActive: false, fcmToken: null },
      );
      this.logger.warn(`Deactivated ${result.invalidTokens.length} expired FCM token(s)`);
    }
    this.logger.log(`FCM delivery user=${userId} devices=${devices.length} tokens=${tokens.length} success=${result.success} failure=${result.failure}`);
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
            // Mark admin-originated sends so tapping their push opens Admin Messages.
            adminMessage: true,
            source: 'admin',
            originalType: type,
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

        // Keep the normal notification/official-news entry and also place the same
        // admin broadcast in the dedicated Admin Messages inbox. Do not send a
        // second push: this mirror exists only for the in-app conversation list.
        if (type !== NotificationType.CHAT) {
          await this.create({
            userId,
            type: NotificationType.CHAT,
            title: dto.title,
            body: plainBody,
            data: {
              audience,
              channel,
              adminMessage: true,
              source: 'admin',
              originalType: type,
              mirroredAdminMessage: true,
              targetUserId: userId,
              ...(richHtml ? { html: richHtml, bodyHtml: richHtml } : {}),
              ...(imageUrl ? { imageUrl } : {}),
              ...(link ? { url: link, link } : {}),
            },
            sendPush: false,
          });
        }
      } else if (pushNow) {
        const ok = await this.sendFcm(userId, dto.title, plainBody, {
          type,
          audience,
          title: dto.title,
          body: plainBody,
          targetUserId: userId,
          // Route taps to Admin Messages even when the dashboard uses push-only delivery.
          adminMessage: true,
          source: 'admin',
          originalType: type,
          ...(imageUrl ? { imageUrl } : {}),
          ...(link ? { url: link } : {}),
        });
        if (ok) pushed += 1;

        // Push-only must still leave an in-app copy in the Admin Messages inbox.
        // No second push is sent by this mirror.
        await this.create({
          userId,
          type: NotificationType.CHAT,
          title: dto.title,
          body: plainBody,
          data: {
            audience,
            channel,
            adminMessage: true,
            source: 'admin',
            originalType: type,
            mirroredAdminMessage: true,
            targetUserId: userId,
            ...(richHtml ? { html: richHtml, bodyHtml: richHtml } : {}),
            ...(imageUrl ? { imageUrl } : {}),
            ...(link ? { url: link, link } : {}),
          },
          sendPush: false,
        });
        saved += 1;
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
 * Sanitizer for admin Official News HTML (rendered in app WebView).
 * Keeps full layout + CSS (<style>, classes, divs) so branded offer cards match dashboard preview.
 * Blocks scripts / remote forms / inline handlers / javascript: URLs.
 */
function sanitizeOfficialHtml(raw: string): string {
  let html = String(raw || '').trim();
  if (!html) return '';
  // Full offer pages (~5–15kb) need headroom; hard-capped for abuse.
  if (html.length > 120_000) html = html.slice(0, 120_000);
  html = html
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/<\/?(iframe|object|embed|form|input|meta|link|base)[^>]*>/gi, '')
    // Drop event handlers but keep <button> / layout structure for CTAs.
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/data:text\/html/gi, '')
    .replace(/expression\s*\(/gi, '');
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
