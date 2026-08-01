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

  @ApiProperty()
  @IsString()
  body: string;

  @ApiPropertyOptional({ enum: ['all', 'hosts', 'vip', 'user'] })
  @IsOptional()
  @IsString()
  audience?: 'all' | 'hosts' | 'vip' | 'user';

  @ApiPropertyOptional()
  @IsOptional()
  @IsUUID()
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
      lastBody: latest?.body || null,
      lastAt: latest?.createdAt || null,
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
          body: dto.body,
          data: {
            audience,
            channel,
            officialNews: isOfficial,
            source: 'admin',
            // Helps clients/debug: this bubble belongs only to this userId.
            targetUserId: userId,
          },
          // create() already pushes when sendPush is true — avoid double FCM.
          sendPush: pushNow,
        });
        saved += 1;
        if (notif.fcmSent) pushed += 1;
      } else if (pushNow) {
        const ok = await this.sendFcm(userId, dto.title, dto.body, {
          type,
          audience,
          title: dto.title,
          body: dto.body,
          targetUserId: userId,
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
      body: dto.body,
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
