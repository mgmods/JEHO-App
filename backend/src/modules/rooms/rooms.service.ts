import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  BadRequestException,
  ConflictException,
  Optional,
  OnModuleInit,
  OnModuleDestroy,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, IsNull, Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { ConfigService } from '@nestjs/config';
import { existsSync } from 'fs';
import { basename, join } from 'path';
import { Room, RoomAccessMode, RoomKind, RoomStatus, RoomType } from '../../database/entities/room.entity';
import { RoomSeat, SeatStatus } from '../../database/entities/room-seat.entity';
import {
  RoomSeatSignal,
  RoomSeatSignalKind,
} from '../../database/entities/room-seat-signal.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { RoomModerator, ModeratorRole } from '../../database/entities/room-moderator.entity';
import { RoomAccess, RoomAccessGrant } from '../../database/entities/room-access.entity';
import { RoomHostFollow } from '../../database/entities/room-host-follow.entity';
import { Follow } from '../../database/entities/follow.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { User, UserStatus } from '../../database/entities/user.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import {
  CreateRoomDto,
  JoinRoomDto,
  TakeSeatDto,
  KickBanDto,
  SetCohostDto,
  RoomMusicDto,
  ModeratorPermissionsDto,
  RoomUserReportDto,
} from './dto/rooms.dto';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { ZegoTokenService } from '../zego/zego-token.service';
import { LiveKitTokenService } from '../livekit/livekit-token.service';
import { LiveKitSettingsService } from '../livekit/livekit-settings.service';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { TasksService } from '../tasks/tasks.service';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';
import { MediaCleanupService } from '../uploads/media-cleanup.service';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import {
  Report,
  ReportStatus,
  ReportTargetType,
} from '../../database/entities/report.entity';
import { RoomMusicTrack } from '../../database/entities/room-music-track.entity';
import { effectiveVipLevel } from '../../common/vip-progress';
import { fixedVipFrameUrl, fixedVipHeadUrl, isStaticVipTouUrl } from '../../common/vip-visual';
import { ContentModerationService } from '../moderation/content-moderation.service';
import {
  normalizeStaffRole,
  staffRank,
  type PlatformStaffRole,
} from '../../common/staff-role';

const DEFAULT_ROOM_SEAT_COUNT = 11; // host stage + numbered seats 1..10

/** Visual guild banner tier 1–6 from lifetime agency diamonds (matches users/agencies). */
function agencyVisualBannerTier(totalDiamonds: number): number {
  const d = Math.max(0, Number(totalDiamonds) || 0);
  if (d >= 5_000_000) return 6;
  if (d >= 1_000_000) return 5;
  if (d >= 200_000) return 4;
  if (d >= 50_000) return 3;
  if (d >= 10_000) return 2;
  return 1;
}
/** Personal empty live rooms auto-end after this many minutes (owner-away / ghost rooms). */
const DEFAULT_PERSONAL_EMPTY_CLOSE_MINUTES = 30;

@Injectable()
export class RoomsService implements OnModuleInit, OnModuleDestroy {
  private readonly log = new Logger(RoomsService.name);
  private personalEmptySweepTimer: ReturnType<typeof setInterval> | null = null;
  private chatAutoClearTimer: ReturnType<typeof setInterval> | null = null;
  /** Default list/cover art when a room has no custom cover (backgrounds pack on disk). */
  private readonly roomCoverCards = [
    'backgrounds/bg_aurora_night',
    'backgrounds/bg_ocean_deep',
    'backgrounds/bg_mint_dream',
    'backgrounds/bg_sunset_lounge',
    'backgrounds/bg_violet_stage',
    'backgrounds/bg_golden_party',
    'backgrounds/bg_neon_city',
  ];

  constructor(
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(RoomSeat) private readonly seatsRepo: Repository<RoomSeat>,
    @InjectRepository(RoomSeatSignal)
    private readonly seatSignalsRepo: Repository<RoomSeatSignal>,
    @InjectRepository(RoomBan) private readonly bansRepo: Repository<RoomBan>,
    @InjectRepository(RoomModerator) private readonly modsRepo: Repository<RoomModerator>,
    @InjectRepository(UserVip) private readonly userVipsRepo: Repository<UserVip>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(RoomAccess) private readonly accessRepo: Repository<RoomAccess>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction) private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(Cosmetic) private readonly cosmeticsRepo: Repository<Cosmetic>,
    @InjectRepository(Follow) private readonly followsRepo: Repository<Follow>,
    @InjectRepository(RoomHostFollow) private readonly roomFollowsRepo: Repository<RoomHostFollow>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(AgencyMember)
    private readonly agencyMembersRepo: Repository<AgencyMember>,
    @InjectRepository(Report) private readonly reportsRepo: Repository<Report>,
    @InjectRepository(RoomMusicTrack)
    private readonly musicTracksRepo: Repository<RoomMusicTrack>,
    private readonly dataSource: DataSource,
    private readonly zegoTokenService: ZegoTokenService,
    private readonly liveKitTokenService: LiveKitTokenService,
    private readonly liveKitSettings: LiveKitSettingsService,
    private readonly realtimeGateway: RealtimeGateway,
    private readonly tasksService: TasksService,
    private readonly mediaCleanup: MediaCleanupService,
    private readonly configService: ConfigService,
    private readonly moderation: ContentModerationService,
    @Optional() private readonly notifications?: NotificationsService,
  ) {}

  async onModuleInit() {
    try {
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "liveSessionStartedAt" TIMESTAMPTZ`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "emptySince" TIMESTAMPTZ`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "chatZoneEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "charmEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "bannerEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "micInteractEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "entryEffectsEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "lowGiftEffectsEnabled" boolean NOT NULL DEFAULT true`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "chatClearedAt" TIMESTAMPTZ`,
      );
      await this.dataSource.query(
        `ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "chatAutoClearMinutes" integer NOT NULL DEFAULT 0`,
      );
      await this.dataSource.query(
        `ALTER TABLE users ADD COLUMN IF NOT EXISTS "staffRole" varchar(16) DEFAULT NULL`,
      );
    } catch (err) {
      this.log.warn(
        `rooms column ensure skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
    // Personal rooms: end live if nobody is in (host away + empty) for ~30 minutes.
    this.personalEmptySweepTimer = setInterval(() => {
      void this.sweepEmptyPersonalRooms().catch((err) =>
        this.log.warn(
          `personal empty sweep: ${err instanceof Error ? err.message : String(err)}`,
        ),
      );
    }, 60_000);
    // Auto-clear public chat for rooms with chatAutoClearMinutes > 0.
    this.chatAutoClearTimer = setInterval(() => {
      void this.sweepAutoChatClear().catch((err) =>
        this.log.warn(
          `chat auto-clear: ${err instanceof Error ? err.message : String(err)}`,
        ),
      );
    }, 20_000);
  }

  onModuleDestroy() {
    if (this.personalEmptySweepTimer) {
      clearInterval(this.personalEmptySweepTimer);
      this.personalEmptySweepTimer = null;
    }
    if (this.chatAutoClearTimer) {
      clearInterval(this.chatAutoClearTimer);
      this.chatAutoClearTimer = null;
    }
  }

  private isPersonalLiveRoom(room: Pick<Room, 'roomKind' | 'agencyId' | 'activeHostId' | 'status'>) {
    if (room.agencyId || room.roomKind === RoomKind.AGENCY) return false;
    if (room.roomKind === RoomKind.SUPPORT) return false;
    if (!room.activeHostId) return false;
    if (room.status === RoomStatus.CLOSED) return false;
    return true;
  }

  /** Anyone still "in" the room? Host muted on stage counts; away with empty room does not. */
  private async countLivePresence(roomId: string): Promise<number> {
    const [seated, sessions, room] = await Promise.all([
      this.seatsRepo
        .createQueryBuilder('s')
        .where('s.roomId = :roomId', { roomId })
        .andWhere('s.userId IS NOT NULL')
        .getCount(),
      this.accessRepo
        .createQueryBuilder('a')
        .where('a.roomId = :roomId', { roomId })
        .andWhere('a.grantType = :gt', { gt: RoomAccessGrant.SESSION })
        .andWhere('(a.expiresAt IS NULL OR a.expiresAt > NOW())')
        .getCount(),
      this.roomsRepo.findOne({
        where: { id: roomId },
        select: ['id', 'viewerCount'],
      }),
    ]);
    const viewers = Math.max(0, Number(room?.viewerCount || 0));
    return seated + sessions + viewers;
  }

  /**
   * Track emptySince for personal live rooms.
   * Presence = mic seats OR session joins OR realtime viewers.
   * Owner alone + muted mic still has seat/viewer → never auto-closed.
   */
  async syncPersonalRoomEmptyState(roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room || !this.isPersonalLiveRoom(room)) return;
    const presence = await this.countLivePresence(roomId);
    if (presence > 0) {
      if (room.emptySince) {
        room.emptySince = null;
        await this.roomsRepo.save(room);
      }
      return;
    }
    if (!room.emptySince) {
      room.emptySince = new Date();
      await this.roomsRepo.save(room);
    }
  }

  /** End personal lives that have been fully empty for the configured window. */
  async sweepEmptyPersonalRooms() {
    const minutes = await this.numberSetting(
      'rooms.personal_empty_close_minutes',
      DEFAULT_PERSONAL_EMPTY_CLOSE_MINUTES,
    );
    const cutoff = new Date(Date.now() - Math.max(5, minutes) * 60_000);
    const candidates = await this.roomsRepo
      .createQueryBuilder('room')
      .where('room.activeHostId IS NOT NULL')
      .andWhere('room.status != :closed', { closed: RoomStatus.CLOSED })
      .andWhere('room.agencyId IS NULL')
      .andWhere("(room.roomKind IS NULL OR room.roomKind = :std)", {
        std: RoomKind.STANDARD,
      })
      .getMany();

    let closed = 0;
    for (const room of candidates) {
      if (!this.isPersonalLiveRoom(room)) continue;
      const presence = await this.countLivePresence(room.id);
      if (presence > 0) {
        if (room.emptySince) {
          room.emptySince = null;
          await this.roomsRepo.save(room);
        }
        continue;
      }
      if (!room.emptySince) {
        room.emptySince = new Date();
        await this.roomsRepo.save(room);
        continue;
      }
      if (room.emptySince > cutoff) continue;
      await this.endLiveSession(room);
      this.realtimeGateway.emitToRoom(room.id, 'room:event', {
        roomId: room.id,
        event: 'room:auto_closed',
        payload: {
          roomId: room.id,
          reason: 'personal_empty_timeout',
          emptyMinutes: minutes,
        },
        at: new Date().toISOString(),
      });
      closed += 1;
      this.log.log(
        `Auto-closed empty personal room ${room.id} after ${minutes}m idle`,
      );
    }
    return { scanned: candidates.length, closed };
  }

  private async numberSetting(key: string, fallback: number): Promise<number> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    const n = Number(row?.value);
    return Number.isFinite(n) && n > 0 ? n : fallback;
  }

  private async boolSetting(key: string, fallback: boolean): Promise<boolean> {
    const row = await this.settingsRepo.findOne({ where: { key } });
    if (!row?.value) return fallback;
    const s = String(row.value).trim().toLowerCase();
    return s === '1' || s === 'true' || s === 'yes' || s === 'on';
  }

  /** When true, guests may take a mic seat without raise-hand / host approval. */
  private async micWithoutHostApproval(): Promise<boolean> {
    return this.boolSetting('rooms.mic_without_host_approval', true);
  }

  private notifyRoomUpdated(roomId: string) {
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:updated',
      payload: { roomId },
      at: new Date().toISOString(),
    });
  }

  private emitRoomEvent(
    roomId: string,
    event: string,
    payload: Record<string, unknown> = {},
  ) {
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event,
      payload: { roomId, ...payload },
      at: new Date().toISOString(),
    });
  }

  /** Notify a single user (even if not fully joined channel yet) + room fans. */
  private emitRoomEventToUser(
    roomId: string,
    userId: string,
    event: string,
    payload: Record<string, unknown> = {},
  ) {
    const body = {
      roomId,
      event,
      payload: { roomId, ...payload },
      at: new Date().toISOString(),
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', body);
    this.realtimeGateway.emitToUser(userId, 'room:event', body);
  }

  private async notifyStaffUpdated(roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) {
      this.notifyRoomUpdated(roomId);
      return;
    }
    const mods = await this.modsRepo.find({ where: { roomId } });
    this.emitRoomEvent(roomId, 'room:staff_updated', {
      cohostId: room.cohostId || null,
      hostId: room.hostId,
      activeHostId: room.activeHostId || null,
      moderatorIds: mods.map((m) => m.userId),
    });
    this.notifyRoomUpdated(roomId);
  }

  private notifyRoomClosed(roomId: string) {
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:closed',
      payload: { roomId, reason: 'host_closed' },
      at: new Date().toISOString(),
    });
  }

  async musicLibrary() {
    const items = await this.musicTracksRepo.find({
      where: { isActive: true },
      relations: { uploadedBy: true },
      order: { updatedAt: 'DESC' },
      take: 100,
    });
    return {
      items: items.map((track) => ({
        id: track.id,
        url: track.url,
        title: track.title,
        artist: track.artist,
        uploadedBy: track.uploadedBy?.displayName || track.uploadedBy?.username || null,
        updatedAt: track.updatedAt,
      })),
    };
  }

  async removeMusicTrack(trackId: string, actorId: string) {
    const [track, actor] = await Promise.all([
      this.musicTracksRepo.findOne({ where: { id: trackId } }),
      this.usersRepo.findOne({ where: { id: actorId } }),
    ]);
    if (!track) throw new NotFoundException('Music track not found');
    if (track.uploadedById !== actorId && !actor?.isAdmin) {
      throw new ForbiddenException('Only the uploader can remove this track');
    }
    track.isActive = false;
    await this.musicTracksRepo.save(track);
    return { removed: true, id: track.id };
  }

  async supporters(roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const selectTop = async (whereSql: string, params: unknown[]) => {
      const rows = await this.dataSource.query(
        `SELECT g."senderId" AS "userId",
                COALESCE(u."displayName", u.username, 'مستخدم') AS "displayName",
                u."avatarUrl" AS "avatarUrl",
                CASE WHEN EXISTS (
                  SELECT 1
                    FROM user_cosmetics owned
                    JOIN cosmetics cosmetic ON cosmetic.id = owned."cosmeticId"
                   WHERE owned."userId" = g."senderId"
                     AND owned.equipped = true
                     AND (owned."expiresAt" IS NULL OR owned."expiresAt" > NOW())
                     AND cosmetic."isActive" = true
                     AND (
                       cosmetic."previewUrl" = p."hostBadgeUrl"
                       OR cosmetic."animationUrl" = p."hostBadgeUrl"
                     )
                ) THEN p."hostBadgeUrl" ELSE NULL END AS "hostBadgeUrl",
                SUM(g."totalCoins")::bigint AS score
           FROM gift_sends g
           JOIN users u ON u.id = g."senderId"
      LEFT JOIN user_profiles p ON p."userId" = u.id
          WHERE ${whereSql}
       GROUP BY g."senderId", u."displayName", u.username, u."avatarUrl", p."hostBadgeUrl"
       ORDER BY score DESC
          LIMIT 3`,
        params,
      );
      return rows.map((row: Record<string, unknown>, index: number) => ({
        rank: index + 1,
        userId: row.userId,
        displayName: row.displayName,
        avatarUrl: row.avatarUrl,
        hostBadgeUrl: row.hostBadgeUrl,
        score: Number(row.score || 0),
      }));
    };
    // Room header tops are session-scoped: cleared when live ends.
    const roomTop =
      room.liveSessionStartedAt != null
        ? await selectTop(`g."roomId" = $1 AND g."createdAt" >= $2`, [
            roomId,
            room.liveSessionStartedAt,
          ])
        : [];
    const agencyTop = room.agencyId
      ? await selectTop(
          `g."roomId" IN (SELECT id FROM rooms WHERE "agencyId" = $1)`,
          [room.agencyId],
        )
      : [];
    return {
      roomId,
      agencyId: room.agencyId,
      room: roomTop,
      agency: agencyTop,
      dayGold: await this.roomDayGold(roomId),
    };
  }

  /**
   * Mikoo contribute board inside a room.
   * type = wealth|charm (or legacy day|week|month|wealth|charm)
   * period = day|week|month (used with wealth/charm)
   */
  async contribute(
    roomId: string,
    type = 'wealth',
    limit = 50,
    period?: string,
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const n = Math.min(100, Math.max(1, Number(limit) || 50));
    const raw = String(type || 'wealth').toLowerCase();
    const periodRaw = String(period || '').toLowerCase();

    let category: 'wealth' | 'charm' = 'wealth';
    let periodKind: 'day' | 'week' | 'month' = 'day';

    if (raw === 'charm' || raw === 'wealth') {
      category = raw;
      periodKind =
        periodRaw === 'week' || periodRaw === 'month' || periodRaw === 'day'
          ? (periodRaw as 'day' | 'week' | 'month')
          : 'day';
    } else if (raw === 'day' || raw === 'week' || raw === 'month') {
      category = 'wealth';
      periodKind = raw;
    } else if (raw.includes('_')) {
      const [c, p] = raw.split('_');
      category = c === 'charm' ? 'charm' : 'wealth';
      periodKind =
        p === 'week' || p === 'month' || p === 'day'
          ? (p as 'day' | 'week' | 'month')
          : 'day';
    }

    const userCol = category === 'charm' ? 'g."receiverId"' : 'g."senderId"';
    const params: unknown[] = [roomId];
    let timeSql = '';
    if (periodKind === 'day') {
      timeSql = ` AND g."createdAt" >= date_trunc('day', NOW())`;
    } else if (periodKind === 'week') {
      timeSql = ` AND g."createdAt" >= NOW() - INTERVAL '7 days'`;
    } else if (periodKind === 'month') {
      timeSql = ` AND g."createdAt" >= NOW() - INTERVAL '30 days'`;
    }
    params.push(n);
    const rows = await this.dataSource.query(
      `SELECT ${userCol} AS "userId",
              COALESCE(u."displayName", u.username, 'مستخدم') AS "displayName",
              u."avatarUrl" AS "avatarUrl",
              SUM(g."totalCoins")::bigint AS score
         FROM gift_sends g
         JOIN users u ON u.id = ${userCol}
        WHERE g."roomId" = $1${timeSql}
     GROUP BY ${userCol}, u."displayName", u.username, u."avatarUrl"
     ORDER BY score DESC
        LIMIT $${params.length}`,
      params,
    );
    const items = rows.map((row: Record<string, unknown>, index: number) => ({
      rank: index + 1,
      userId: row.userId,
      displayName: row.displayName,
      avatarUrl: row.avatarUrl,
      score: Number(row.score || 0),
    }));
    const dayGold = await this.roomDayGold(roomId);
    return {
      roomId,
      type: category,
      period: periodKind,
      dayGold,
      items,
    };
  }

  /** Mikoo RoomAdditional.dayGold — gift coins in this room since local midnight. */
  async roomDayGold(roomId: string): Promise<number> {
    const rows = await this.dataSource.query(
      `SELECT COALESCE(SUM(g."totalCoins"), 0)::bigint AS gold
         FROM gift_sends g
        WHERE g."roomId" = $1
          AND g."createdAt" >= date_trunc('day', NOW())`,
      [roomId],
    );
    return Number(rows?.[0]?.gold || 0);
  }

  async reportUser(roomId: string, reporterId: string, dto: RoomUserReportDto) {
    if (reporterId === dto.targetUserId) {
      throw new ForbiddenException('Cannot report yourself');
    }
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const [reporterAccess, reporterSeat, target, targetAccess, targetSeat] =
      await Promise.all([
      this.accessRepo.findOne({ where: { roomId, userId: reporterId } }),
      this.seatsRepo.findOne({ where: { roomId, userId: reporterId } }),
      this.usersRepo.findOne({ where: { id: dto.targetUserId } }),
      this.accessRepo.findOne({ where: { roomId, userId: dto.targetUserId } }),
      this.seatsRepo.findOne({ where: { roomId, userId: dto.targetUserId } }),
    ]);
    if (
      room.hostId !== reporterId &&
      !this.hasActiveAccess(reporterAccess) &&
      !reporterSeat
    ) {
      throw new ForbiddenException('Join the room before reporting a room user');
    }
    if (!target) throw new NotFoundException('Reported user not found');
    if (
      room.hostId !== dto.targetUserId &&
      !this.hasActiveAccess(targetAccess) &&
      !targetSeat
    ) {
      throw new BadRequestException('Reported user is not in this room');
    }
    const existing = await this.reportsRepo.findOne({
      where: {
        reporterId,
        targetType: ReportTargetType.USER,
        targetId: dto.targetUserId,
        roomId,
      },
      order: { createdAt: 'DESC' },
    });
    if (existing && existing.status === ReportStatus.PENDING) {
      return { submitted: true, reportId: existing.id, duplicate: true };
    }
    const report = await this.reportsRepo.save(
      this.reportsRepo.create({
        reporterId,
        targetType: ReportTargetType.USER,
        targetId: dto.targetUserId,
        roomId,
        reason: dto.reason,
        description: [`roomId=${roomId}`, dto.description?.trim()]
          .filter(Boolean)
          .join('\n'),
        evidenceUrls: null,
      }),
    );
    return { submitted: true, reportId: report.id };
  }

  private defaultRoomCover(room: {
    id?: string;
    hostId?: string;
    roomKind?: RoomKind;
    agencyId?: string | null;
  }) {
    if (room.roomKind === RoomKind.AGENCY || room.agencyId) {
      return '/assets/backgrounds/bg_royal_indigo.png?v=20260730c';
    }
    const key = String(room.id || room.hostId || 'room');
    let hash = 0;
    for (let i = 0; i < key.length; i++) {
      hash = (hash * 31 + key.charCodeAt(i)) | 0;
    }
    const card = this.roomCoverCards[Math.abs(hash) % this.roomCoverCards.length];
    return `/assets/${card}.png?v=20260730c`;
  }

  async create(hostId: string, dto: CreateRoomDto) {
    const host = await this.usersRepo.findOne({
      where: { id: hostId },
      relations: ['profile'],
    });
    if (!host) throw new NotFoundException('Host not found');
    const displayName = (host.displayName || host.username || '').trim();
    if (!displayName) {
      throw new BadRequestException('Profile name is required before opening a room');
    }

    const membership = await this.agencyMembersRepo.findOne({
      where: {
        userId: hostId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
      relations: ['agency'],
      order: { joinedAt: 'DESC' },
    });
    const isAgencyHost =
      !!membership &&
      membership.agency?.status === AgencyStatus.ACTIVE &&
      [AgencyRole.OWNER, AgencyRole.MANAGER].includes(membership.role);

    // Agency hosts keep a separate personal room when the client asks for it
    // ("My room") — agency live uses POST /agencies/:id/room/open instead.
    if (!isAgencyHost || dto.preferPersonal === true) {
      return this.createPersonalRoom(hostId, host, displayName, dto);
    }

    // Agency room title is ALWAYS the agency name — never the host's personal display name.
    const agencyName = String(membership!.agency.name || '').trim() || 'وكالة';
    const title = agencyName;
    const coverUrl = (
      dto.coverUrl ||
      membership!.agency?.logoUrl ||
      this.defaultRoomCover({
        hostId,
        roomKind: RoomKind.AGENCY,
        agencyId: membership!.agencyId,
      })
    ).trim();

    const existing = await this.roomsRepo.findOne({
      where: { agencyId: membership!.agencyId, hostId },
    });
    if (existing) {
      const hostCard =
        (host.profile as { roomCardUrl?: string | null } | undefined)?.roomCardUrl || null;
      Object.assign(existing, {
        // Force agency branding every open so lobby never shows a personal name.
        title: agencyName,
        coverUrl: existing.coverUrl || coverUrl,
        backgroundUrl:
          dto.backgroundUrl !== undefined
            ? dto.backgroundUrl || null
            : existing.backgroundUrl,
        roomCardUrl: existing.roomCardUrl || hostCard || null,
        roomCardEquippedById:
          existing.roomCardUrl || hostCard
            ? existing.roomCardEquippedById || hostId
            : null,
        status: RoomStatus.OPEN,
        activeHostId: hostId,
        liveSessionStartedAt: existing.liveSessionStartedAt || new Date(),
        roomKind: RoomKind.AGENCY,
        isPersistent: true,
        isPublic: true,
        accessMode: RoomAccessMode.FREE,
        entryFeeCoins: 0,
        hasPassword: false,
        passwordHash: null,
      });
      await this.roomsRepo.save(existing);
      await this.occupyHostSeat(
        existing.id,
        hostId,
        Math.max(existing.seatCount || 0, DEFAULT_ROOM_SEAT_COUNT),
      );
      return this.buildHostJoinPayload(hostId, existing.id);
    }

    const seatCount = dto.seatCount || DEFAULT_ROOM_SEAT_COUNT;
    const hostCard =
      (host.profile as { roomCardUrl?: string | null } | undefined)?.roomCardUrl || null;
    const room = this.roomsRepo.create({
      title,
      description: dto.description || null,
      coverUrl,
      backgroundUrl: dto.backgroundUrl || null,
      roomCardUrl: hostCard,
      roomCardEquippedById: hostCard ? hostId : null,
      type: dto.type || RoomType.VOICE,
      hostId,
      agencyId: membership!.agencyId,
      activeHostId: hostId,
      status: RoomStatus.OPEN,
      roomKind: RoomKind.AGENCY,
      isPersistent: true,
      seatCount,
      topic: dto.topic || null,
      isPublic: true,
      accessMode: RoomAccessMode.FREE,
      entryFeeCoins: 0,
      liveSessionStartedAt: new Date(),
      zegoRoomId: `room_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
      hasPassword: false,
      passwordHash: null,
    });
    try {
      await this.dataSource.transaction(async (manager) => {
        await manager.save(room);
        const seats: RoomSeat[] = [];
        for (let i = 0; i < seatCount; i++) {
          seats.push(
            manager.create(RoomSeat, {
              roomId: room.id,
              seatIndex: i,
              isHostSeat: i === 0,
              status: i === 0 ? SeatStatus.OCCUPIED : SeatStatus.EMPTY,
              userId: i === 0 ? hostId : null,
            }),
          );
        }
        await manager.save(seats);
        room.viewerCount = 0;
        await manager.save(room);
      });
    } catch (error) {
      const raced = await this.roomsRepo.findOne({
        where: { agencyId: membership!.agencyId, hostId },
      });
      if (raced) return this.create(hostId, dto);
      throw error;
    }
    void this.notifyRoomOpened(hostId, room.id, title, displayName);
    void this.tasksService
      .recordProgress(hostId, 'host_room', 1, { agencyId: membership!.agencyId })
      .catch(() => undefined);
    return this.buildHostJoinPayload(hostId, room.id);
  }

  /** Personal voice room — no agency membership required. */
  private async createPersonalRoom(
    hostId: string,
    host: User,
    displayName: string,
    dto: CreateRoomDto,
  ) {
    const title = dto.title?.trim() || `${displayName}`;
    this.moderation.assertNoAgencyImpersonation(title, 'اسم الروم');
    if (dto.description !== undefined) {
      this.moderation.assertNoAgencyImpersonation(dto.description, 'وصف الروم');
    }
    const coverUrl = (
      dto.coverUrl ||
      this.defaultRoomCover({
        hostId,
        roomKind: RoomKind.STANDARD,
      })
    ).trim();

    const existing = await this.roomsRepo.findOne({
      where: { hostId, agencyId: IsNull(), roomKind: RoomKind.STANDARD },
      order: { createdAt: 'DESC' },
    });
    if (existing) {
      const hostCard =
        (host.profile as { roomCardUrl?: string | null } | undefined)?.roomCardUrl || null;
      Object.assign(existing, {
        // Keep permanent room name/cover once set — don't overwrite with host profile each open.
        title: (existing.title && String(existing.title).trim()) || title,
        coverUrl: existing.coverUrl || coverUrl,
        backgroundUrl:
          dto.backgroundUrl !== undefined
            ? dto.backgroundUrl || null
            : existing.backgroundUrl,
        roomCardUrl: existing.roomCardUrl || hostCard || null,
        roomCardEquippedById:
          existing.roomCardUrl || hostCard
            ? existing.roomCardEquippedById || hostId
            : null,
        status: RoomStatus.OPEN,
        activeHostId: hostId,
        liveSessionStartedAt: existing.liveSessionStartedAt || new Date(),
        emptySince: null,
        isPersistent: false,
        isPublic: true,
        accessMode: RoomAccessMode.FREE,
        entryFeeCoins: 0,
        hasPassword: false,
        passwordHash: null,
      });
      await this.roomsRepo.save(existing);
      await this.occupyHostSeat(
        existing.id,
        hostId,
        Math.max(existing.seatCount || 0, DEFAULT_ROOM_SEAT_COUNT),
      );
      return this.buildHostJoinPayload(hostId, existing.id);
    }

    const seatCount = dto.seatCount || DEFAULT_ROOM_SEAT_COUNT;
    const hostCard =
      (host.profile as { roomCardUrl?: string | null } | undefined)?.roomCardUrl || null;
    const room = this.roomsRepo.create({
      title,
      description: dto.description || null,
      coverUrl,
      backgroundUrl: dto.backgroundUrl || null,
      roomCardUrl: hostCard,
      roomCardEquippedById: hostCard ? hostId : null,
      type: dto.type || RoomType.VOICE,
      hostId,
      agencyId: null,
      activeHostId: hostId,
      status: RoomStatus.OPEN,
      roomKind: RoomKind.STANDARD,
      isPersistent: false,
      seatCount,
      topic: dto.topic || null,
      isPublic: true,
      accessMode: RoomAccessMode.FREE,
      entryFeeCoins: 0,
      liveSessionStartedAt: new Date(),
      zegoRoomId: `room_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
      hasPassword: false,
      passwordHash: null,
    });
    await this.dataSource.transaction(async (manager) => {
      await manager.save(room);
      const seats: RoomSeat[] = [];
      for (let i = 0; i < seatCount; i++) {
        seats.push(
          manager.create(RoomSeat, {
            roomId: room.id,
            seatIndex: i,
            isHostSeat: i === 0,
            status: i === 0 ? SeatStatus.OCCUPIED : SeatStatus.EMPTY,
            userId: i === 0 ? hostId : null,
          }),
        );
      }
      await manager.save(seats);
      room.viewerCount = 0;
      await manager.save(room);
    });
    void this.notifyRoomOpened(hostId, room.id, title, displayName);
    void this.tasksService.recordProgress(hostId, 'host_room', 1).catch(() => undefined);
    return this.buildHostJoinPayload(hostId, room.id);
  }

  private async buildHostJoinPayload(hostId: string, roomId: string) {
    const full = await this.getRoom(roomId);
    return this.buildRtcJoinPayload(hostId, full, true);
  }

  /**
   * Unified Zego / LiveKit join payload. Clients switch engines via voiceProvider.
   * LiveKit = free self-hosted open source; Zego = existing cloud path.
   */
  private async buildRtcJoinPayload(
    userId: string,
    full: Awaited<ReturnType<RoomsService['getRoom']>>,
    canPublish: boolean,
  ) {
    const roomKey = full.zegoRoomId || full.id;
    const provider = await this.liveKitSettings.getProvider();

    if (provider === 'livekit') {
      const lk = await this.liveKitTokenService.generateToken(
        userId,
        roomKey,
        canPublish ? 3600 : 3600,
        canPublish,
      );
      return {
        room: full,
        voiceProvider: 'livekit' as const,
        token: lk.token,
        appId: 0,
        zegoRoomId: full.zegoRoomId,
        livekitUrl: lk.url,
        livekitRoomName: lk.roomName,
        userId,
        expireAt: lk.expireAt,
        canPublish,
      };
    }

    // Publish tokens must NOT be 60s: short TTL silently kills mic mid-room when
    // refresh races fail, so only a few seated users hear each other.
    // Default server TTL (~3600s) + client renew keeps Zego continuous.
    const zego = await this.zegoTokenService.generateToken(
      userId,
      full.zegoRoomId || undefined,
      canPublish ? 3600 : 3600,
      canPublish,
    );
    return {
      room: full,
      voiceProvider: 'zego' as const,
      token: zego.token,
      appId: zego.appId,
      zegoRoomId: full.zegoRoomId,
      livekitUrl: '',
      livekitRoomName: '',
      userId,
      expireAt: zego.expireAt,
      canPublish,
    };
  }

  /** End live session: clear host and mark closed (persistent rows stay). */
  async endLiveSession(room: Room) {
    room.activeHostId = null;
    room.liveSessionStartedAt = null;
    room.emptySince = null;
    room.status = RoomStatus.CLOSED;
    room.viewerCount = 0;
    await this.roomsRepo.save(room);
    await this.seatsRepo.update(
      { roomId: room.id },
      {
        userId: null,
        status: SeatStatus.EMPTY,
        isMuted: false,
        isModeratorMuted: false,
      },
    );
    await this.seatSignalsRepo.delete({ roomId: room.id });
    this.realtimeGateway.emitToRoom(room.id, 'room:event', {
      roomId: room.id,
      event: 'room:seat_requests_cleared',
      payload: { roomId: room.id, reason: 'session_ended' },
      at: new Date().toISOString(),
    });
    this.realtimeGateway.emitToRoom(room.id, 'room:event', {
      roomId: room.id,
      event: 'room:supporters_cleared',
      payload: { roomId: room.id },
      at: new Date().toISOString(),
    });
    this.notifyRoomClosed(room.id);
    this.notifyRoomUpdated(room.id);
  }

  /** Close a host's agency room after leave / kick / demote. */
  async closeAgencyHostRoom(agencyId: string, hostUserId: string) {
    const room = await this.roomsRepo.findOne({
      where: { agencyId, hostId: hostUserId },
    });
    if (!room) return;
    await this.endLiveSession(room);
  }

  private async assertEligibleAgencyHost(agencyId: string, userId: string) {
    if ((await this.platformStaffRole(userId)) === 'super') return;
    const membership = await this.agencyMembersRepo.findOne({
      where: {
        agencyId,
        userId,
        status: AgencyMemberStatus.ACTIVE,
        isActive: true,
      },
    });
    if (
      !membership ||
      ![AgencyRole.OWNER, AgencyRole.MANAGER].includes(membership.role)
    ) {
      throw new ForbiddenException(
        'فتح بث الوكالة لمالك الوكالة أو الأدمن فقط',
      );
    }
  }

  /**
   * Super staff force-opens an agency permanent room as live host (any agency).
   */
  async forceOpenAgencyRoom(roomId: string, actorId: string) {
    if ((await this.platformStaffRole(actorId)) !== 'super') {
      throw new ForbiddenException('Super privileges required');
    }
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (!room.agencyId && room.roomKind !== RoomKind.AGENCY) {
      throw new BadRequestException('Not an agency room');
    }
    room.status = RoomStatus.OPEN;
    room.activeHostId = actorId;
    room.emptySince = null;
    if (!room.liveSessionStartedAt) room.liveSessionStartedAt = new Date();
    room.isPublic = true;
    await this.roomsRepo.save(room);
    await this.occupyHostSeat(
      room.id,
      actorId,
      Math.max(room.seatCount || 0, DEFAULT_ROOM_SEAT_COUNT),
    );
    this.notifyRoomUpdated(roomId);
    return this.buildHostJoinPayload(actorId, roomId);
  }

  private async occupyHostSeat(roomId: string, hostId: string, seatCount: number) {
    let hostSeat = await this.seatsRepo.findOne({ where: { roomId, seatIndex: 0 } });
    if (!hostSeat) {
      const seats: RoomSeat[] = [];
      for (let i = 0; i < seatCount; i++) {
        seats.push(
          this.seatsRepo.create({
            roomId,
            seatIndex: i,
            isHostSeat: i === 0,
            status: i === 0 ? SeatStatus.OCCUPIED : SeatStatus.EMPTY,
            userId: i === 0 ? hostId : null,
          }),
        );
      }
      await this.seatsRepo.save(seats);
      return;
    }
    await this.seatsRepo.update(
      { roomId, userId: hostId },
      { userId: null, status: SeatStatus.EMPTY },
    );
    hostSeat.userId = hostId;
    hostSeat.status = SeatStatus.OCCUPIED;
    hostSeat.isHostSeat = true;
    await this.seatsRepo.save(hostSeat);
  }

  private async notifyRoomOpened(
    hostId: string,
    roomId: string,
    title: string,
    hostName: string,
  ) {
    if (!this.notifications) return;
    const recipientIds = new Set<string>();
    const social = await this.followsRepo.find({
      where: { followingId: hostId },
      take: 500,
    });
    for (const f of social) recipientIds.add(f.followerId);
    const roomFans = await this.roomFollowsRepo.find({
      where: { hostId },
      take: 500,
    });
    for (const f of roomFans) recipientIds.add(f.followerId);
    recipientIds.delete(hostId);
    for (const uid of recipientIds) {
      void this.notifications.notifyUser(
        uid,
        NotificationType.ROOM,
        `${hostName} فتح غرفة صوتية 🎙️`,
        title || 'انضم الآن',
        { type: 'room', roomId, hostId },
      );
    }
  }

  async followHostRoom(followerId: string, roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (room.hostId === followerId) throw new BadRequestException('Cannot follow your own room');
    const existing = await this.roomFollowsRepo.findOne({
      where: { followerId, hostId: room.hostId },
    });
    if (existing) return { following: true, hostId: room.hostId };
    await this.roomFollowsRepo.save(
      this.roomFollowsRepo.create({ followerId, hostId: room.hostId }),
    );
    return { following: true, hostId: room.hostId };
  }

  async unfollowHostRoom(followerId: string, roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.roomFollowsRepo.delete({ followerId, hostId: room.hostId });
    return { following: false, hostId: room.hostId };
  }

  async isFollowingHostRoom(followerId: string, roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const row = await this.roomFollowsRepo.findOne({
      where: { followerId, hostId: room.hostId },
    });
    return { following: !!row, hostId: room.hostId };
  }

  private async wearMetaForUrl(url?: string | null) {
    if (!url) return null;
    const row = await this.cosmeticsRepo.findOne({ where: { previewUrl: url } });
    return (row?.meta as Record<string, unknown> | null) ?? null;
  }

  private async attachFrame(user: any, vipLevel = 0) {
    if (!user) return null;
    const profile = user.profile || {};
    const rawHost = profile.hostBadgeUrl ?? user.hostBadgeUrl ?? null;
    const rawVip = profile.vipBadgeUrl ?? user.vipBadgeUrl ?? null;
    const rawLevel = profile.levelBadgeUrl ?? user.levelBadgeUrl ?? null;
    // Prefer owned+equipped URL, but never strip a profile-equipped host frame
    // (room stage/seats were losing the badge while profile/chat still showed it).
    const [verifiedHost, verifiedVip, verifiedLevel] = await Promise.all([
      this.activeOwnedWearUrl(user.id, rawHost),
      this.activeOwnedWearUrl(user.id, rawVip),
      this.activeOwnedWearUrl(user.id, rawLevel),
    ]);
    const hostBadgeUrl = verifiedHost ?? rawHost;
    const level = Number(vipLevel || user.vipLevel || 0);
    // Equipped wear first (mall / SVGA). Static VIP art is vipTouUrl/vipHeadUrl only.
    let vipBadgeUrl = verifiedVip ?? rawVip;
    if (isStaticVipTouUrl(vipBadgeUrl)) {
      vipBadgeUrl = await this.findEquippedMallHeadFrame(user.id);
    }
    const vipHeadUrl = fixedVipHeadUrl(level);
    const vipTouUrl = fixedVipFrameUrl(level);
    const levelBadgeUrl = verifiedLevel ?? rawLevel;
    const hostBadgeMeta = await this.wearMetaForUrl(hostBadgeUrl);
    return {
      ...user,
      country: profile.country ?? user.country ?? null,
      hostBadgeUrl,
      hostBadgeMeta,
      vipBadgeUrl,
      vipHeadUrl,
      vipTouUrl,
      levelBadgeUrl,
      roomCardUrl: profile.roomCardUrl ?? user.roomCardUrl ?? null,
      vipLevel: level,
      profile: undefined,
    };
  }

  /** Equipped non-static VIP/head frame (SVGA/web mall art). */
  private async findEquippedMallHeadFrame(userId: string): Promise<string | null> {
    if (!userId) return null;
    const rows = await this.dataSource.query(
      `SELECT cosmetic."previewUrl" AS url, cosmetic."animationUrl" AS anim
         FROM user_cosmetics owned
         JOIN cosmetics cosmetic ON cosmetic.id = owned."cosmeticId"
        WHERE owned."userId" = $1
          AND owned.equipped = true
          AND cosmetic."isActive" = true
          AND cosmetic.type = 'vip_badge'
          AND (owned."expiresAt" IS NULL OR owned."expiresAt" > NOW())
          AND COALESCE(cosmetic."previewUrl", '') NOT LIKE '%ud_vip_tou_%'
          AND COALESCE(cosmetic."previewUrl", '') NOT LIKE '%vip_tou_fixed_%'
          AND COALESCE(cosmetic.meta->>'fixedVipFrame', '') <> 'true'
        ORDER BY owned."updatedAt" DESC NULLS LAST
        LIMIT 1`,
      [userId],
    );
    if (!rows?.length) return null;
    const anim = rows[0].anim != null ? String(rows[0].anim) : '';
    const url = rows[0].url != null ? String(rows[0].url) : '';
    return anim || url || null;
  }

  private async activeOwnedWearUrl(
    userId: string,
    url: string | null,
    requireEquipped = true,
  ) {
    if (!userId || !url) return null;
    const rows = await this.dataSource.query(
      `SELECT 1
         FROM cosmetics cosmetic
    LEFT JOIN user_cosmetics owned
           ON owned."cosmeticId" = cosmetic.id
          AND owned."userId" = $1
          AND (owned."expiresAt" IS NULL OR owned."expiresAt" > NOW())
        WHERE cosmetic."isActive" = true
          AND (
            cosmetic."previewUrl" = $2
            OR cosmetic."animationUrl" = $2
          )
          AND (
            (
              owned.id IS NOT NULL
              AND ($3 = false OR owned.equipped = true)
            )
            OR (
              COALESCE(cosmetic."coinPrice", 0) = 0
              AND COALESCE(cosmetic."minVipLevel", 0) = 0
              AND COALESCE(cosmetic."minUserLevel", 0) = 0
            )
          )
        LIMIT 1`,
      [userId, url, requireEquipped],
    );
    return rows.length ? url : null;
  }

  private async vipMapForUsers(userIds: string[]) {
    const ids = [...new Set(userIds.filter(Boolean))];
    const map = new Map<string, number>();
    if (!ids.length) return map;
    const rows = await this.userVipsRepo
      .createQueryBuilder('v')
      .select('v.userId', 'userId')
      .addSelect('MAX(v.level)', 'level')
      .where('v.userId IN (:...ids)', { ids })
      .andWhere('v.isActive = true')
      .andWhere('v.expiresAt > NOW()')
      .groupBy('v.userId')
      .getRawMany<{ userId: string; level: string }>();
    for (const row of rows) {
      map.set(row.userId, Number(row.level) || 0);
    }
    const users = await this.usersRepo.find({
      where: ids.map((id) => ({ id })),
      relations: ['profile'],
    });
    for (const user of users) {
      const base = map.get(user.id) || 0;
      map.set(user.id, effectiveVipLevel(base, Number((user as any).profile?.totalSentCoins || 0)));
    }
    return map;
  }

  private collectViewerAvatars(room: any): string[] {
    const urls: string[] = [];
    const seen = new Set<string>();
    const push = (user: any) => {
      if (!user) return;
      const url = user.avatarUrl;
      const id = user.id || url;
      if (!url || seen.has(id)) return;
      seen.add(id);
      urls.push(url);
    };
    // Host first so home cards always show a face (Mikoo supporter row).
    if (room.host) push(room.host);
    for (const seat of room.seats || []) {
      if (urls.length >= 6) break;
      if (seat?.user) push(seat.user);
    }
    return urls;
  }

  private computeRoomLevel(totalCoins: number, viewerCount = 0): number {
    let level = 1;
    if (totalCoins >= 1_000_000) {
      level = 6 + Math.floor(Math.log10(Math.max(1, totalCoins / 1_000_000)));
    } else if (totalCoins >= 200_000) level = 5;
    else if (totalCoins >= 50_000) level = 4;
    else if (totalCoins >= 10_000) level = 3;
    else if (totalCoins >= 1_000) level = 2;
    level = Math.max(level, 1 + Math.floor(Math.max(0, viewerCount) / 25));
    return Math.min(99, level);
  }

  private async roomGiftCoinTotals(
    roomIds: string[],
  ): Promise<Map<string, number>> {
    const map = new Map<string, number>();
    if (!roomIds.length) return map;
    const rows: Array<{ roomId: string; coins: string }> =
      await this.dataSource.query(
        `SELECT "roomId" AS "roomId", COALESCE(SUM("totalCoins"), 0)::bigint AS coins
         FROM gift_sends
         WHERE "roomId" = ANY($1::uuid[])
         GROUP BY "roomId"`,
        [roomIds],
      );
    for (const row of rows) {
      map.set(row.roomId, Number(row.coins) || 0);
    }
    return map;
  }

  private async decorateRoom(room: any) {
    const userIds: string[] = [];
    if (room.hostId) userIds.push(room.hostId);
    for (const s of room.seats || []) {
      if (s.userId) userIds.push(s.userId);
      else if (s.user?.id) userIds.push(s.user.id);
    }
    const vipMap = await this.vipMapForUsers(userIds);
    const [equippedBackground] =
      await Promise.all([
        this.activeOwnedWearUrl(
          room.backgroundEquippedById || room.hostId,
          room.backgroundUrl,
          false,
        ),
      ]);
    // Prefer room-level frame; fall back to host profile / host DTO (mall equip).
    const fromRoom =
      typeof room.roomCardUrl === 'string' && room.roomCardUrl.trim()
        ? room.roomCardUrl.trim()
        : null;
    const hostProfile = room.host?.profile as { roomCardUrl?: string | null } | undefined;
    const fromHostProfile =
      typeof hostProfile?.roomCardUrl === 'string' && hostProfile.roomCardUrl.trim()
        ? hostProfile.roomCardUrl.trim()
        : null;
    const decoratedSeats = await Promise.all(
      (room.seats || []).map(async (s: any) => {
        const uid = s.userId || s.user?.id;
        return {
          ...s,
          user: await this.attachFrame(s.user, vipMap.get(uid) || 0),
        };
      }),
    );
    const hostDto = await this.attachFrame(room.host, vipMap.get(room.hostId) || 0);
    const fromHostDto =
      hostDto && typeof (hostDto as any).roomCardUrl === 'string'
        ? String((hostDto as any).roomCardUrl).trim() || null
        : null;
    const roomCardUrl = fromRoom || fromHostProfile || fromHostDto || null;
    let finalRoomCardUrl = roomCardUrl;
    const decorated = {
      ...room,
      roomCardUrl: finalRoomCardUrl,
      host: hostDto,
      seats: decoratedSeats,
    };
    const coverUrl = decorated.coverUrl?.trim()
      ? decorated.coverUrl.trim()
      : this.defaultRoomCover(decorated);
    const isAgency =
      decorated.roomKind === RoomKind.AGENCY || !!decorated.agencyId;
    const isSupport = decorated.roomKind === RoomKind.SUPPORT;
    // Room identity is permanent (title + coverUrl), separate from host profile.
    // Agency rooms always surface the agency name in the lobby (never host displayName).
    let listTitle =
      String(decorated.title || '').trim() ||
      String(decorated.host?.displayName || decorated.host?.username || '').trim() ||
      'غرفة';
    /** Agency room face uses agency brand (GID/logo/level) — never host publicId or host heart badge. */
    let agencyPublicId: string | null = null;
    let agencyLogoUrl: string | null = null;
    let agencyLevel = 1;
    let agencyTotalDiamonds = 0;
    let agencyIsVerified = false;
    if (isAgency && decorated.agencyId) {
      try {
        const agency = await this.agenciesRepo.findOne({
          where: { id: decorated.agencyId },
        });
        if (agency) {
          const agencyName = String(agency.name || '').trim();
          if (agencyName) listTitle = agencyName;
          agencyIsVerified = !!agency.isVerified;
          agencyLogoUrl = agency.logoUrl ? String(agency.logoUrl).trim() : null;
          // Room cover is often the actual brand photo when logoUrl was never set.
          if (!agencyLogoUrl && coverUrl) {
            const c = coverUrl.trim();
            if (
              c &&
              !c.toLowerCase().includes('backgrounds/bg_') &&
              !c.toLowerCase().includes('/assets/backgrounds')
            ) {
              agencyLogoUrl = c;
            }
          }
          agencyTotalDiamonds = Math.max(0, Number(agency.totalDiamonds) || 0);
          agencyLevel = agencyVisualBannerTier(agencyTotalDiamonds);
          agencyPublicId = agency.publicId ? String(agency.publicId).trim() : null;
          if (!agencyPublicId) {
            // Lazy-assign GID so room UI never falls back to owner user id.
            for (let i = 0; i < 12; i++) {
              const candidate = String(10000 + Math.floor(Math.random() * 90000));
              const clash = await this.agenciesRepo.findOne({
                where: { publicId: candidate },
              });
              if (!clash) {
                agency.publicId = candidate;
                await this.agenciesRepo.update(
                  { id: agency.id },
                  { publicId: candidate },
                );
                agencyPublicId = candidate;
                break;
              }
            }
            if (!agencyPublicId) {
              agencyPublicId = String(10000 + (Date.now() % 90000));
              await this.agenciesRepo.update(
                { id: agency.id },
                { publicId: agencyPublicId },
              );
            }
          }
          if (!finalRoomCardUrl && agency.exclusiveRoomCardCode) {
            const code = String(agency.exclusiveRoomCardCode).trim();
            if (code) {
              const cosmetic = await this.cosmeticsRepo.findOne({
                where: { code, isActive: true },
              });
              const kenar =
                cosmetic?.previewUrl?.trim() || cosmetic?.animationUrl?.trim();
              if (kenar) finalRoomCardUrl = kenar;
            }
          }
          decorated.roomCardUrl = finalRoomCardUrl;
        }
      } catch {
        /* keep listTitle */
      }
    }
    return {
      ...decorated,
      title: listTitle,
      coverUrl,
      backgroundUrl: equippedBackground,
      roomLabel: isSupport ? 'support' : isAgency ? 'agency' : 'personal',
      isSupport,
      // Agency: show agency GID. Personal/support: host publicId.
      displayRoomId: isAgency
        ? agencyPublicId || decorated.host?.publicId || null
        : decorated.host?.publicId || null,
      agencyPublicId,
      agencyLogoUrl,
      agencyLevel,
      agencyTotalDiamonds,
      agencyIsVerified,
      viewerAvatars: this.collectViewerAvatars(decorated),
      moderatorIds: (room.moderators || []).map((m: any) => m.userId).filter(Boolean),
      moderatorPermissions: (room.moderators || []).map((m: RoomModerator) => ({
        userId: m.userId,
        // Strict true only — matches assertModeratorPermission (null/false = no power).
        canManageMusic: m.canManageMusic === true,
        canChangeFrames: m.canChangeFrames === true,
        canControlGames: m.canControlGames === true,
        canMute: m.canMute === true,
        canKick: m.canKick === true,
        canBan: m.canBan === true,
        canManageSeats: m.canManageSeats === true,
        canInvite: m.canInvite === true,
        canManageRoom: m.canManageRoom === true,
      })),
    };
  }

  private async memberIdentity(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    return {
      userId,
      displayName: user?.displayName || user?.username || 'مستخدم',
      publicId: user?.publicId || null,
    };
  }

  private dedupeRoomsById(rooms: Room[]): Room[] {
    const map = new Map<string, Room>();
    for (const room of rooms) {
      if (!room?.id) continue;
      const prev = map.get(room.id);
      if (!prev) {
        map.set(room.id, room);
        continue;
      }
      const byIndex = new Map<number, RoomSeat>();
      for (const seat of [...(prev.seats || []), ...(room.seats || [])]) {
        if (seat?.seatIndex == null) continue;
        byIndex.set(seat.seatIndex, seat);
      }
      prev.seats = [...byIndex.values()];
    }
    return [...map.values()];
  }

  private applyPublicRoomListFilters(
    qb: ReturnType<Repository<Room>['createQueryBuilder']>,
    query: PaginationDto,
  ) {
    qb.leftJoin('room.agency', 'agency')
      .where('room.isPublic = true')
      // Live rooms, OR official customer-service rooms (always listable when open).
      .andWhere(
        `(room.activeHostId IS NOT NULL OR room.roomKind = :supportKind)`,
        { supportKind: RoomKind.SUPPORT },
      )
      .andWhere(
        '(room.status = :open OR (room.status = :locked AND room.hasPassword = true) OR room.roomKind = :supportKind)',
        { open: RoomStatus.OPEN, locked: RoomStatus.LOCKED, supportKind: RoomKind.SUPPORT },
      )
      .andWhere('(room.agencyId IS NULL OR agency.status = :agencyActive)', {
        agencyActive: AgencyStatus.ACTIVE,
      });
    const tag = (query.tag || '').trim().toLowerCase();
    if (tag) {
      qb.andWhere(
        'LOWER(room.tags) LIKE :tagLike',
        { tagLike: `%${tag}%` },
      );
    }
    const country = (query.country || '').trim();
    if (country) {
      qb.leftJoin('room.host', 'filterHost')
        .leftJoin('filterHost.profile', 'filterHostProfile')
        .andWhere('LOWER(filterHostProfile.country) = LOWER(:country)', { country });
    }

    // Live presence is already gated by activeHostId (except support rooms pinned by admin).
    const q = (query.search || '').trim();
    if (q) {
      // displayRoomId shown in-app is the host publicId — include it in search.
      qb.leftJoin('room.host', 'searchHost');
      qb.andWhere(
        '(LOWER(room.title) LIKE :q OR CAST(room.id AS text) LIKE :q OR searchHost.publicId = :exact OR CAST(searchHost.publicId AS text) LIKE :q OR LOWER(searchHost.displayName) LIKE :q OR LOWER(searchHost.username) LIKE :q)',
        { q: `%${q.toLowerCase()}%`, exact: q },
      );
    }
    return q;
  }

  async list(query: PaginationDto) {
    const countQb = this.roomsRepo.createQueryBuilder('room');
    this.applyPublicRoomListFilters(countQb, query);
    const total = await countQb.getCount();

    const qb = this.roomsRepo
      .createQueryBuilder('room')
      .leftJoinAndSelect('room.host', 'host')
      .leftJoinAndSelect('host.profile', 'hostProfile')
      .leftJoinAndSelect('room.seats', 'seats')
      .leftJoinAndSelect('seats.user', 'seatUser')
      .leftJoinAndSelect('seatUser.profile', 'seatProfile');
    this.applyPublicRoomListFilters(qb, query);
    // Hot / Explore: official CS rooms always first (pin), then heat.
    // TypeORM mis-parses raw CASE in orderBy() as an alias ("CASE WHEN r… not found").
    // Select as named columns, then orderBy those aliases.
    qb.addSelect(
      `(COALESCE(room."viewerCount", 0) * 500
        + COALESCE((
            SELECT COUNT(*)::int FROM room_seats rs
             WHERE rs."roomId" = room.id AND rs."userId" IS NOT NULL
          ), 0) * 250
        + COALESCE((
            SELECT SUM(gs."totalCoins") FROM gift_sends gs
             WHERE gs."roomId" = room.id
               AND gs."createdAt" > NOW() - INTERVAL '24 hours'
          ), 0) / 15.0
        + COALESCE((
            SELECT SUM(gs."totalCoins") FROM gift_sends gs
             WHERE gs."roomId" = room.id
          ), 0) / 80.0)`,
      'explore_heat',
    )
      .addSelect(
        `CASE WHEN room."roomKind" = :supportPinKind THEN 1 ELSE 0 END`,
        'support_pin',
      )
      .setParameter('supportPinKind', RoomKind.SUPPORT)
      .orderBy('support_pin', 'DESC')
      .addOrderBy('explore_heat', 'DESC')
      .addOrderBy('room.viewerCount', 'DESC')
      .addOrderBy('room.updatedAt', 'DESC')
      .addOrderBy('room.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    const rawItems = await qb.getMany();
    const items = this.dedupeRoomsById(rawItems);
    const giftCoins = await this.roomGiftCoinTotals(items.map((room) => room.id));
    const enriched = await Promise.all(
      items.map(async (room, index) => ({
        ...(await this.decorateRoom(room)),
        roomLevel: this.computeRoomLevel(
          giftCoins.get(room.id) || 0,
          room.viewerCount || 0,
        ),
        exploreRank: (query.skip || 0) + index + 1,
        challengeBadge: room.viewerCount >= 10 ? 'Hot 🔥' : null,
      })),
    );
    return paginate(enriched, total, query.page || 1, query.limit || 20);
  }

  async getRoom(id: string) {
    const room = await this.roomsRepo.findOne({
      where: { id },
      relations: [
        'host',
        'host.profile',
        'seats',
        'seats.user',
        'seats.user.profile',
        'moderators',
      ],
    });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    // Respect host-configured size (guest mics + host seat). Never force back to default 11.
    const desiredSeatCount = Math.max(
      2,
      Math.min(31, Number(room.seatCount) || DEFAULT_ROOM_SEAT_COUNT),
    );
    if (room.seatCount !== desiredSeatCount) {
      room.seatCount = desiredSeatCount;
      await this.roomsRepo.save(room);
    }
    const existingIndices = new Set((room.seats || []).map((seat) => seat.seatIndex));
    const missingSeats: Partial<RoomSeat>[] = [];
    for (let index = 0; index < desiredSeatCount; index++) {
      if (!existingIndices.has(index)) {
        missingSeats.push({
          roomId: id,
          seatIndex: index,
          isHostSeat: index === 0,
          status: SeatStatus.EMPTY,
          userId: null,
        });
      }
    }
    if (missingSeats.length) {
      await this.seatsRepo
        .createQueryBuilder()
        .insert()
        .into(RoomSeat)
        .values(missingSeats)
        .orIgnore()
        .execute();
      room.seats = await this.seatsRepo.find({
        where: { roomId: id },
        relations: ['user', 'user.profile'],
        order: { seatIndex: 'ASC' },
      });
    }
    // Hide leftover seats outside the configured capacity (e.g. after shrink).
    if (Array.isArray(room.seats)) {
      room.seats = room.seats.filter(
        (seat: RoomSeat) => seat != null && seat.seatIndex < desiredSeatCount,
      );
    }
    const giftCoins = await this.roomGiftCoinTotals([id]);
    const decorated = await this.decorateRoom(room);
    return {
      ...decorated,
      seatCount: desiredSeatCount,
      roomLevel: this.computeRoomLevel(
        giftCoins.get(id) || 0,
        room.viewerCount || 0,
      ),
    };
  }

  async getRoomForViewer(id: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if (room.isPublic && !room.hasPassword) return this.getRoom(id);
    if (
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId
    ) {
      return this.getRoom(id);
    }
    const [access, seat, moderator] = await Promise.all([
      this.accessRepo.findOne({ where: { roomId: id, userId } }),
      this.seatsRepo.findOne({ where: { roomId: id, userId } }),
      this.modsRepo.findOne({ where: { roomId: id, userId } }),
    ]);
    if (!this.hasActiveAccess(access) && !seat && !moderator) {
      throw new ForbiddenException('Join this room before viewing its participants');
    }
    return this.getRoom(id);
  }

  async join(roomId: string, userId: string, dto: JoinRoomDto) {
    const room = await this.roomsRepo
      .createQueryBuilder('r')
      .addSelect('r.passwordHash')
      .where('r.id = :id', { id: roomId })
      .getOne();
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if (room.status === RoomStatus.CLOSED) {
      // Only the room host may reopen a closed session (go live again).
      if (room.hostId !== userId) {
        throw new BadRequestException('Room is closed');
      }
    }

    if (room.status === RoomStatus.LOCKED && room.hostId !== userId) {
      if (room.hasPassword && room.passwordHash) {
        if (!dto.password) throw new ForbiddenException('Password required');
        const ok = await bcrypt.compare(dto.password, room.passwordHash);
        if (!ok) throw new ForbiddenException('Invalid room password');
      } else {
        throw new ForbiddenException('Room is locked by host');
      }
    }

    const ban = await this.bansRepo.findOne({ where: { roomId, userId } });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('You are banned from this room');
    }

    if (
      room.hasPassword &&
      room.hostId !== userId &&
      room.status !== RoomStatus.LOCKED
    ) {
      if (!dto.password || !room.passwordHash) {
        throw new ForbiddenException('Password required');
      }
      const ok = await bcrypt.compare(dto.password, room.passwordHash);
      if (!ok) throw new ForbiddenException('Invalid room password');
    }

    let priorAccess = await this.accessRepo.findOne({ where: { roomId, userId } });
    if (
      priorAccess?.grantType === RoomAccessGrant.SESSION &&
      priorAccess.expiresAt &&
      priorAccess.expiresAt <= new Date()
    ) {
      await this.accessRepo.remove(priorAccess);
      priorAccess = null;
    }
    await this.chargeEntryFee(room, userId);
    const currentAccess =
      priorAccess ||
      (room.hostId !== userId
        ? await this.accessRepo.findOne({ where: { roomId, userId } })
        : null);
    if (currentAccess?.grantType === RoomAccessGrant.SESSION) {
      currentAccess.expiresAt = new Date(Date.now() + 12 * 60 * 60_000);
      await this.accessRepo.save(currentAccess);
    }
    if (!currentAccess && room.hostId !== userId) {
      await this.accessRepo.upsert(
        {
          roomId,
          userId,
          grantType: RoomAccessGrant.SESSION,
          coinsPaid: 0,
          expiresAt: new Date(Date.now() + 12 * 60 * 60_000),
        },
        ['roomId', 'userId'],
      );
    }

    // Socket.IO membership is the single source of truth for viewer counts.
    const seated = await this.seatsRepo.findOne({ where: { roomId, userId } });
    const isHost = room.hostId === userId;
    const canTakeHostSeat = isHost;

    // Host / active agency host owns seat 0 when joining/rejoining.
    if (canTakeHostSeat) {
      if (room.agencyId || room.roomKind === RoomKind.AGENCY) {
        if (!room.agencyId) {
          throw new ForbiddenException('Agency room is unavailable');
        }
        await this.assertEligibleAgencyHost(room.agencyId, userId);
      }
      const hostSeat = await this.seatsRepo.findOne({ where: { roomId, seatIndex: 0 } });
      if (hostSeat) {
        if (hostSeat.userId && hostSeat.userId !== userId) {
          hostSeat.userId = null;
          hostSeat.status = SeatStatus.EMPTY;
          await this.seatsRepo.save(hostSeat);
        }
        const other = await this.seatsRepo.findOne({ where: { roomId, userId } });
        if (other && other.seatIndex !== 0) {
          other.userId = null;
          other.status = SeatStatus.EMPTY;
          await this.seatsRepo.save(other);
        }
        hostSeat.userId = userId;
        hostSeat.status = SeatStatus.OCCUPIED;
        hostSeat.isHostSeat = true;
        await this.seatsRepo.save(hostSeat);
      }
      room.activeHostId = userId;
      room.status = RoomStatus.OPEN;
      room.emptySince = null;
      if (!room.liveSessionStartedAt) {
        room.liveSessionStartedAt = new Date();
      }
      await this.roomsRepo.save(room);
      this.notifyRoomUpdated(roomId);
    }

    const full = await this.getRoom(roomId);
    const canPublish =
      full.hostId === userId ||
      full.activeHostId === userId ||
      full.cohostId === userId ||
      (full.seats || []).some(
        (seat: RoomSeat) =>
          seat.userId === userId && !seat.isModeratorMuted,
      );
    // Real daily task: entered a voice room
    void this.tasksService
      .recordProgress(userId, 'rooms', 1, {
        roomId: room.id,
        agencyId: room.agencyId || undefined,
      })
      .catch(() => undefined);
    void this.tasksService
      .recordProgress(userId, 'host_visit', 1, {
        roomId: room.id,
        agencyId: room.agencyId || undefined,
      })
      .catch(() => undefined);
    // Agency host invite dwell: start timer if guest was invited today.
    if (!isHost) {
      void this.tasksService
        .maybeTrackRoomJoinForInvite({
          roomId: room.id,
          guestId: userId,
          roomHostId: room.hostId,
          activeHostId: room.activeHostId,
        })
        .catch(() => undefined);
      // Schedule completion check after dwell window (2 min).
      setTimeout(() => {
        void this.tasksService
          .tryCompleteRoomInviteReward({
            roomId: room.id,
            hostId: room.activeHostId || room.hostId,
            guestId: userId,
          })
          .catch(() => undefined);
      }, (120 + 2) * 1000);
    }
    void this.syncPersonalRoomEmptyState(roomId).catch(() => undefined);
    return this.buildRtcJoinPayload(userId, full, canPublish);
  }

  async issueZegoToken(roomId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (room.status === RoomStatus.CLOSED) {
      throw new ForbiddenException('Room is closed');
    }
    await this.assertAgencyRoomActive(room);
    const [seat, access, ban] = await Promise.all([
      this.seatsRepo.findOne({ where: { roomId, userId } }),
      this.accessRepo.findOne({ where: { roomId, userId } }),
      this.bansRepo.findOne({ where: { roomId, userId } }),
    ]);
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('You are banned from this room');
    }
    const canEnter =
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId ||
      this.hasActiveAccess(access) ||
      !!seat;
    if (!canEnter) throw new ForbiddenException('Join the room before requesting an RTC token');
    const canPublish =
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId ||
      (!!seat && !seat.isModeratorMuted);
    const full = await this.getRoom(roomId);
    return this.buildRtcJoinPayload(userId, full, canPublish);
  }

  async setGiftSounds(roomId: string, actorId: string, enabled: boolean) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    room.giftSoundsEnabled = enabled !== false;
    await this.roomsRepo.save(room);
    const payload = { roomId, giftSoundsEnabled: room.giftSoundsEnabled };
    this.realtimeGateway.emitToRoom(roomId, 'room:gift_sounds', payload);
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  /** Mikoo RoomMore toggles that must sync for everyone in the room. */
  async setDisplaySettings(
    roomId: string,
    actorId: string,
    patch: {
      chatZoneEnabled?: boolean;
      charmEnabled?: boolean;
      bannerEnabled?: boolean;
      micInteractEnabled?: boolean;
      entryEffectsEnabled?: boolean;
      lowGiftEffectsEnabled?: boolean;
    },
  ) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (typeof patch.chatZoneEnabled === 'boolean') {
      room.chatZoneEnabled = patch.chatZoneEnabled;
    }
    if (typeof patch.charmEnabled === 'boolean') {
      room.charmEnabled = patch.charmEnabled;
    }
    if (typeof patch.bannerEnabled === 'boolean') {
      room.bannerEnabled = patch.bannerEnabled;
    }
    if (typeof patch.micInteractEnabled === 'boolean') {
      room.micInteractEnabled = patch.micInteractEnabled;
    }
    if (typeof patch.entryEffectsEnabled === 'boolean') {
      room.entryEffectsEnabled = patch.entryEffectsEnabled;
    }
    if (typeof patch.lowGiftEffectsEnabled === 'boolean') {
      room.lowGiftEffectsEnabled = patch.lowGiftEffectsEnabled;
    }
    await this.roomsRepo.save(room);
    const payload = {
      roomId,
      chatZoneEnabled: room.chatZoneEnabled !== false,
      charmEnabled: room.charmEnabled !== false,
      bannerEnabled: room.bannerEnabled !== false,
      micInteractEnabled: room.micInteractEnabled !== false,
      entryEffectsEnabled: room.entryEffectsEnabled !== false,
      lowGiftEffectsEnabled: room.lowGiftEffectsEnabled !== false,
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:display_settings', payload);
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  private normalizeChatAutoClearMinutes(raw: unknown): number {
    const n = Math.floor(Number(raw));
    if (n === 1 || n === 5 || n === 10) return n;
    return 0;
  }

  /**
   * Staff wipe of the public chat screen for every device in the room.
   * Stamps DB chatClearedAt so late-joiners discard stale local caches.
   */
  async clearPublicChat(
    roomId: string,
    actorId: string,
    opts?: { auto?: boolean; displayName?: string | null },
  ) {
    if (!opts?.auto) {
      await this.assertModeratorPermission(
        roomId,
        actorId,
        'canManageRoom',
        'Room management permission is required',
      );
    }
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const now = new Date();
    room.chatClearedAt = now;
    await this.roomsRepo.save(room);

    let displayName = opts?.displayName || null;
    if (!displayName && actorId) {
      const actor = await this.usersRepo.findOne({ where: { id: actorId } });
      displayName =
        actor?.displayName || actor?.username || (opts?.auto ? 'النظام' : 'مشرف');
    }
    const payload = {
      userId: actorId || null,
      displayName: displayName || (opts?.auto ? 'النظام' : 'مشرف'),
      chatClearedAt: now.toISOString(),
      auto: !!opts?.auto,
      full: true,
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:chat_cleared',
      payload,
      from: { userId: actorId || null, username: displayName || 'مشرف' },
      at: now.toISOString(),
    });
    this.notifyRoomUpdated(roomId);
    return {
      ok: true,
      roomId,
      chatClearedAt: now.toISOString(),
      chatAutoClearMinutes: room.chatAutoClearMinutes || 0,
    };
  }

  /** Cycle/set auto public-chat wipe interval (0 / 1 / 5 / 10 minutes). */
  async setChatAutoClearMinutes(
    roomId: string,
    actorId: string,
    minutes: number,
  ) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    room.chatAutoClearMinutes = this.normalizeChatAutoClearMinutes(minutes);
    // Reset timer baseline so first auto wipe is after a full interval.
    if (room.chatAutoClearMinutes > 0) {
      room.chatClearedAt = new Date();
    }
    await this.roomsRepo.save(room);
    const payload = {
      roomId,
      chatAutoClearMinutes: room.chatAutoClearMinutes,
      chatClearedAt: room.chatClearedAt
        ? room.chatClearedAt.toISOString()
        : null,
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:chat_auto_clear',
      payload,
      at: new Date().toISOString(),
    });
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  /** Server-driven periodic wipe for rooms with chatAutoClearMinutes > 0. */
  private async sweepAutoChatClear() {
    const rooms = await this.roomsRepo
      .createQueryBuilder('room')
      .select([
        'room.id',
        'room.chatAutoClearMinutes',
        'room.chatClearedAt',
        'room.activeHostId',
        'room.liveSessionStartedAt',
        'room.status',
      ])
      .where('room."chatAutoClearMinutes" > 0')
      .andWhere('room.status = :open', { open: RoomStatus.OPEN })
      .andWhere('room.activeHostId IS NOT NULL')
      .getMany();
    if (!rooms.length) return;
    const now = Date.now();
    for (const room of rooms) {
      const minutes = this.normalizeChatAutoClearMinutes(room.chatAutoClearMinutes);
      if (minutes <= 0) continue;
      const base = room.chatClearedAt
        ? room.chatClearedAt.getTime()
        : room.liveSessionStartedAt
          ? room.liveSessionStartedAt.getTime()
          : 0;
      if (base <= 0) {
        // First enable without baseline: stamp now so next tick waits full interval.
        await this.roomsRepo.update(room.id, { chatClearedAt: new Date(now) });
        continue;
      }
      if (now - base < minutes * 60_000) continue;
      try {
        await this.clearPublicChat(room.id, room.activeHostId || '', {
          auto: true,
          displayName: 'تنظيف تلقائي',
        });
      } catch (err) {
        this.log.warn(
          `auto clear ${room.id}: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    }
  }

  async setPassword(roomId: string, actorId: string, locked: boolean, password?: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (room.roomKind === RoomKind.AGENCY || room.agencyId) {
      throw new ForbiddenException('Agency rooms are always public and password-free');
    }
    if (locked) {
      if (!password || !String(password).trim()) {
        throw new BadRequestException('أدخل كلمة سر لقفل الغرفة');
      }
      room.status = RoomStatus.LOCKED;
      room.hasPassword = true;
      room.passwordHash = await bcrypt.hash(String(password).trim(), 10);
    } else {
      room.status = RoomStatus.OPEN;
      room.hasPassword = false;
      room.passwordHash = null;
    }
    await this.roomsRepo.save(room);
    this.emitRoomEvent(roomId, 'room:lock_changed', {
      locked: !!locked,
      hasPassword: !!room.hasPassword,
      status: room.status,
    });
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  async lockSeat(roomId: string, actorId: string, seatIndex: number, locked: boolean) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageSeats',
      'Seat management permission is required',
    );
    const seat = await this.seatsRepo.findOne({ where: { roomId, seatIndex } });
    if (!seat) throw new NotFoundException('Seat not found');
    if (seat.isHostSeat || seat.seatIndex === 0) {
      throw new ForbiddenException('Host seat cannot be locked or cleared');
    }
    if (locked) {
      if (seat.userId && seat.userId !== actorId) {
        throw new ConflictException('Cannot lock occupied seat');
      }
      seat.userId = null;
      seat.status = SeatStatus.LOCKED;
    } else {
      seat.status = SeatStatus.EMPTY;
    }
    await this.seatsRepo.save(seat);
    this.emitRoomEvent(roomId, 'room:seat_locked', {
      seatIndex,
      locked,
      status: seat.status,
      userId: seat.userId,
    });
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  async resizeSeats(roomId: string, actorId: string, seatCount: number) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    // Client sends total seats including host (seat 0). Clamp 2..31.
    const target = Math.min(31, Math.max(2, Math.floor(Number(seatCount) || DEFAULT_ROOM_SEAT_COUNT)));
    const existing = await this.seatsRepo.find({ where: { roomId }, order: { seatIndex: 'ASC' } });
    if (target > existing.length) {
      const extra: RoomSeat[] = [];
      for (let i = existing.length; i < target; i++) {
        extra.push(
          this.seatsRepo.create({
            roomId,
            seatIndex: i,
            isHostSeat: false,
            status: SeatStatus.EMPTY,
            userId: null,
          }),
        );
      }
      await this.seatsRepo.save(extra);
    } else if (target < existing.length) {
      const occupiedOutOfRange = existing.some(
        (seat) => seat.seatIndex >= target && !!seat.userId,
      );
      if (occupiedOutOfRange) {
        throw new ConflictException(
          'Move users from seats outside the new size before shrinking',
        );
      }
      const removable = existing.filter((s) => s.seatIndex >= target && !s.userId);
      if (removable.length) await this.seatsRepo.remove(removable);
    }
    room.seatCount = target;
    await this.roomsRepo.save(room);
    this.emitRoomEvent(roomId, 'room:seats_resized', {
      seatCount: target,
    });
    this.notifyRoomUpdated(roomId);
    return this.getRoom(roomId);
  }

  private async seatRequestItems(roomId: string) {
    await this.seatSignalsRepo
      .createQueryBuilder()
      .delete()
      .where('"roomId" = :roomId', { roomId })
      .andWhere('"expiresAt" <= NOW()')
      .execute();
    const rows = await this.seatSignalsRepo.find({
      where: { roomId, kind: RoomSeatSignalKind.REQUEST },
      order: { createdAt: 'ASC' },
    });
    return rows.map((row) => ({
      userId: row.userId,
      seatIndex: row.seatIndex,
      displayName: row.displayName || 'Guest',
      at: row.createdAt.toISOString(),
    }));
  }

  async listSeatRequests(roomId: string, actorId: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canInvite',
      'Mic invitation permission is required',
    );
    return { items: await this.seatRequestItems(roomId) };
  }

  async inviteToSeat(
    roomId: string,
    actorId: string,
    targetUserId: string,
    requestedSeatIndex?: number | null,
  ) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canInvite',
      'Mic invitation permission is required',
    );
    await this.assertCanTargetUser(roomId, actorId, targetUserId);
    const [occupied, access, room, ban] = await Promise.all([
      this.seatsRepo.findOne({ where: { roomId, userId: targetUserId } }),
      this.accessRepo.findOne({ where: { roomId, userId: targetUserId } }),
      this.roomsRepo.findOne({ where: { id: roomId } }),
      this.bansRepo.findOne({ where: { roomId, userId: targetUserId } }),
    ]);
    if (!room) throw new NotFoundException('Room not found');
    if (!this.hasActiveAccess(access) && !occupied && room.hostId !== targetUserId) {
      throw new ForbiddenException('User is not currently joined to this room');
    }
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('User is banned from this room');
    }
    if (occupied) throw new ConflictException('User is already on a seat');
    let seat = requestedSeatIndex != null
      ? await this.seatsRepo.findOne({ where: { roomId, seatIndex: requestedSeatIndex } })
      : null;
    if (!seat) {
      seat = await this.seatsRepo
        .createQueryBuilder('seat')
        .where('seat.roomId = :roomId', { roomId })
        .andWhere('seat.userId IS NULL')
        .andWhere('seat.status != :locked', { locked: SeatStatus.LOCKED })
        .andWhere('seat.isHostSeat = false')
        .orderBy('seat.seatIndex', 'ASC')
        .getOne();
    }
    if (
      !seat ||
      seat.isHostSeat ||
      seat.seatIndex === 0 ||
      seat.userId ||
      seat.status === SeatStatus.LOCKED
    ) {
      throw new BadRequestException('No free seat available');
    }
    let invite = await this.seatSignalsRepo.findOne({
      where: { roomId, userId: targetUserId, kind: RoomSeatSignalKind.INVITE },
    });
    if (!invite) {
      invite = this.seatSignalsRepo.create({
        roomId,
        userId: targetUserId,
        kind: RoomSeatSignalKind.INVITE,
      });
    }
    invite.seatIndex = seat.seatIndex;
    invite.createdById = actorId;
    invite.displayName = null;
    invite.expiresAt = new Date(Date.now() + 60_000);
    invite = await this.seatSignalsRepo.save(invite);
    this.realtimeGateway.emitToUser(targetUserId, 'room:event', {
      roomId,
      event: 'room:seat_invited',
      payload: { roomId, userId: targetUserId, seatIndex: seat.seatIndex },
      at: new Date().toISOString(),
    });
    // Also fan-out on the room channel so in-room clients never miss the invite.
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_invited',
      payload: { roomId, userId: targetUserId, seatIndex: seat.seatIndex },
      at: new Date().toISOString(),
    });
    return {
      invited: true,
      userId: invite.userId,
      seatIndex: invite.seatIndex,
      invitedBy: invite.createdById,
      expiresAt: invite.expiresAt,
    };
  }

  async respondToSeatInvite(roomId: string, userId: string, accept: boolean) {
    const invite = await this.seatSignalsRepo.findOne({
      where: { roomId, userId, kind: RoomSeatSignalKind.INVITE },
    });
    if (!invite || invite.expiresAt.getTime() < Date.now()) {
      if (invite) await this.seatSignalsRepo.remove(invite);
      throw new BadRequestException('Seat invitation expired');
    }
    await this.seatSignalsRepo.remove(invite);
    if (!accept) return this.getRoom(roomId);
    if (!invite.createdById) throw new BadRequestException('Invalid seat invitation');
    return this.approveSeat(roomId, invite.createdById, userId, invite.seatIndex, true);
  }

  async raiseHand(
    roomId: string,
    userId: string,
    raised: boolean,
    seatIndex?: number | null,
  ) {
    const room = await this.getRoom(roomId);
    const [access, occupied, ban] = await Promise.all([
      this.accessRepo.findOne({ where: { roomId, userId } }),
      this.seatsRepo.findOne({ where: { roomId, userId } }),
      this.bansRepo.findOne({ where: { roomId, userId } }),
    ]);
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('You are banned from this room');
    }
    if (!this.hasActiveAccess(access) && !occupied && room.hostId !== userId) {
      throw new ForbiddenException('Join the room before requesting a mic seat');
    }
    if (!raised) {
      await this.seatSignalsRepo.delete({
        roomId,
        userId,
        kind: RoomSeatSignalKind.REQUEST,
      });
      this.realtimeGateway.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'room:hand_lowered',
        payload: { roomId, userId },
        at: new Date().toISOString(),
      });
      return {
        roomId,
        userId,
        raised: false,
        requests: await this.seatRequestItems(roomId),
      };
    }

    if (room.hostId === userId) {
      return { roomId, userId, raised: false, message: 'Host does not need to request a seat' };
    }
    if (occupied) {
      throw new ConflictException('User is already on a seat');
    }

    const user = await this.usersRepo.findOne({ where: { id: userId } });
    const displayName = user?.displayName || user?.username || 'Guest';
    let saved = await this.seatSignalsRepo.findOne({
      where: { roomId, userId, kind: RoomSeatSignalKind.REQUEST },
    });
    if (!saved) {
      saved = this.seatSignalsRepo.create({
        roomId,
        userId,
        kind: RoomSeatSignalKind.REQUEST,
      });
    }
    saved.seatIndex =
      seatIndex != null && Number.isFinite(Number(seatIndex)) ? Number(seatIndex) : null;
    saved.displayName = displayName;
    saved.createdById = userId;
    saved.expiresAt = new Date(Date.now() + 10 * 60_000);
    saved = await this.seatSignalsRepo.save(saved);
    const req = {
      userId,
      seatIndex: saved.seatIndex,
      displayName,
      at: saved.createdAt.toISOString(),
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:hand_raised',
      payload: { roomId, ...req },
      at: req.at,
    });
    return {
      roomId,
      userId,
      raised: true,
      request: req,
      requests: await this.seatRequestItems(roomId),
    };
  }

  async approveSeat(
    roomId: string,
    actorId: string,
    targetUserId: string,
    seatIndex?: number | null,
    invited = false,
  ) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canInvite',
      'Mic request approval permission is required',
    );
    const pending = await this.seatSignalsRepo.findOne({
      where: { roomId, userId: targetUserId, kind: RoomSeatSignalKind.REQUEST },
    });
    if (!pending && !invited) {
      throw new BadRequestException('No pending mic request for this user');
    }
    const [targetAccess, existingSeat, room] = await Promise.all([
      this.accessRepo.findOne({ where: { roomId, userId: targetUserId } }),
      this.seatsRepo.findOne({ where: { roomId, userId: targetUserId } }),
      this.roomsRepo.findOne({ where: { id: roomId } }),
    ]);
    if (!room) throw new NotFoundException('Room not found');
    if (
      !this.hasActiveAccess(targetAccess) &&
      !existingSeat &&
      room.hostId !== targetUserId
    ) {
      throw new ForbiddenException('User is no longer in this room');
    }
    const preferred =
      seatIndex != null && Number.isFinite(Number(seatIndex))
        ? Number(seatIndex)
        : pending?.seatIndex ?? null;

    const target = await this.dataSource.transaction(async (manager) => {
      const seats = await manager.find(RoomSeat, {
        where: { roomId },
        order: { seatIndex: 'ASC' },
        lock: { mode: 'pessimistic_write' },
      });
      let selected =
        preferred != null ? seats.find((seat) => seat.seatIndex === preferred) : null;
      if (
        !selected ||
        selected.isHostSeat ||
        selected.seatIndex === 0 ||
        selected.status === SeatStatus.LOCKED ||
        (selected.userId && selected.userId !== targetUserId)
      ) {
        selected =
          seats.find(
            (seat) =>
              seat.seatIndex !== 0 &&
              !seat.isHostSeat &&
              seat.status !== SeatStatus.LOCKED &&
              (!seat.userId || seat.userId === targetUserId),
          ) || null;
      }
      if (!selected) throw new BadRequestException('No free seat available');
      await manager.update(
        RoomSeat,
        { roomId, userId: targetUserId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      selected.userId = targetUserId;
      selected.status = SeatStatus.OCCUPIED;
      selected.isMuted = false;
      selected.isModeratorMuted = false;
      return manager.save(RoomSeat, selected);
    });
    await this.seatSignalsRepo.delete({ roomId, userId: targetUserId });

    this.notifyRoomUpdated(roomId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_approved',
      payload: {
        roomId,
        userId: targetUserId,
        seatIndex: target.seatIndex,
        displayName: pending?.displayName || null,
      },
      at: new Date().toISOString(),
    });
    return this.getRoom(roomId);
  }

  async rejectSeat(roomId: string, actorId: string, targetUserId: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canInvite',
      'Mic request approval permission is required',
    );
    await this.seatSignalsRepo.delete({
      roomId,
      userId: targetUserId,
      kind: RoomSeatSignalKind.REQUEST,
    });
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_rejected',
      payload: { roomId, userId: targetUserId },
      at: new Date().toISOString(),
    });
    return {
      rejected: true,
      userId: targetUserId,
      requests: await this.seatRequestItems(roomId),
    };
  }

  async setMic(roomId: string, actorId: string, muted: boolean, targetUserId?: string) {
    const effectiveUserId =
      targetUserId && String(targetUserId).trim() ? String(targetUserId).trim() : actorId;
    const moderatingOther = effectiveUserId !== actorId;
    if (moderatingOther) {
      await this.assertModeratorPermission(
        roomId,
        actorId,
        'canMute',
        'لا تملك صلاحية كتم المايك',
      );
      await this.assertCanTargetUser(roomId, actorId, effectiveUserId);
    }
    const seat = await this.seatsRepo.findOne({ where: { roomId, userId: effectiveUserId } });
    if (!seat) {
      if (moderatingOther) {
        throw new BadRequestException('المستخدم ليس على المايك حالياً');
      }
      return { roomId, userId: effectiveUserId, muted: !!muted };
    }
    if (
      !moderatingOther &&
      !muted &&
      seat.isModeratorMuted
    ) {
      throw new ForbiddenException('يحتاج المضيف أو المشرف لفك كتم المايك أولاً');
    }
    seat.isMuted = !!muted;
    if (moderatingOther) {
      seat.isModeratorMuted = !!muted;
      // Unmute by host/mod clears both flags so the guest can speak again.
      if (!muted) {
        seat.isMuted = false;
        seat.isModeratorMuted = false;
      }
    }
    await this.seatsRepo.save(seat);
    // Admin mute is flag-only — do not eject from Zego (kick/ban still eject).
    this.notifyRoomUpdated(roomId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:mic_changed',
      payload: {
        roomId,
        userId: effectiveUserId,
        muted: !!seat.isMuted,
        moderatorMuted: !!seat.isModeratorMuted,
      },
      at: new Date().toISOString(),
    });
    return {
      roomId,
      userId: effectiveUserId,
      muted: !!seat.isMuted,
      moderatorMuted: !!seat.isModeratorMuted,
    };
  }

  async leave(roomId: string, userId: string) {
    const wasSeated = await this.seatsRepo.findOne({ where: { roomId, userId } });
    const roomBefore = await this.roomsRepo.findOne({ where: { id: roomId } });
    await this.seatsRepo.update(
      { roomId, userId },
      {
        userId: null,
        status: SeatStatus.EMPTY,
        isMuted: false,
        isModeratorMuted: false,
      },
    );
    await this.seatSignalsRepo.delete({ roomId, userId });
    await this.accessRepo.delete({
      roomId,
      userId,
      grantType: RoomAccessGrant.SESSION,
    });
    const room = roomBefore || (await this.roomsRepo.findOne({ where: { id: roomId } }));
    if (room) {
      const priorActiveHostId = room.activeHostId;
      if (room.activeHostId === userId) {
        // Host left the app UI / mic — keep the live session alive.
        // Only POST /rooms/:id/close (إنهاء البث) clears activeHostId.
        // Client already logs out of Zego so minutes are not billed.
        this.realtimeGateway.emitToRoom(roomId, 'room:event', {
          roomId,
          event: 'room:host_away',
          payload: { roomId, userId, activeHostId: room.activeHostId },
          at: new Date().toISOString(),
        });
        this.notifyRoomUpdated(roomId);
      }
      // Complete invite reward if guest already fulfilled dwell before leaving.
      if (room.hostId !== userId) {
        void this.tasksService
          .maybeCompleteInviteOnLeave({
            roomId,
            guestId: userId,
            roomHostId: room.hostId,
            activeHostId: priorActiveHostId,
          })
          .catch(() => undefined);
      }
    }
    this.notifyRoomUpdated(roomId);
    if (wasSeated) {
      this.realtimeGateway.emitToRoom(roomId, 'room:event', {
        roomId,
        event: 'room:seat_left',
        payload: { roomId, userId },
        at: new Date().toISOString(),
      });
    }
    // Top-level + nested leave so Android viewer strip updates without waiting for poll.
    this.realtimeGateway.emitToRoom(roomId, 'room:user_left', {
      roomId,
      userId,
    });
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:user_left',
      payload: { roomId, userId },
      at: new Date().toISOString(),
    });
    void this.realtimeGateway.broadcastRoomPresence(roomId).catch(() => undefined);
    void this.syncPersonalRoomEmptyState(roomId).catch(() => undefined);
    return { left: true };
  }

  /**
   * Hostess invites a new male into her room for the 40◆ / 2-minute dwell reward.
   */
  async inviteGuestForTaskReward(roomId: string, hostId: string, guestId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    const isRoomHost =
      room.hostId === hostId ||
      room.activeHostId === hostId ||
      room.cohostId === hostId;
    if (!isRoomHost) {
      throw new ForbiddenException('فقط مضيفة الغرفة يمكنها الدعوة للمهمة');
    }
    const access = await this.accessRepo.findOne({ where: { roomId, userId: guestId } });
    const seated = await this.seatsRepo.findOne({ where: { roomId, userId: guestId } });
    const alreadyIn = !!seated || this.hasActiveAccess(access);
    return this.tasksService.inviteGuestForRoomReward({
      roomId,
      hostId,
      guestId,
      guestAlreadyInRoom: alreadyIn,
    });
  }

  async takeSeat(roomId: string, userId: string, dto: TakeSeatDto) {
    // Already joined via /join — do not re-require password (that blocked seat switches).
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if (room.status === RoomStatus.CLOSED) throw new BadRequestException('Room is closed');
    const ban = await this.bansRepo.findOne({ where: { roomId, userId } });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('You are banned from this room');
    }

    const isHost = room.hostId === userId;
    const alreadySeated = await this.seatsRepo.findOne({ where: { roomId, userId } });
    // Guests may only switch seats if already on mic; first seat requires host approval
    // unless the dashboard enables free mic (rooms.mic_without_host_approval).
    if (!isHost && !alreadySeated) {
      const freeMic = await this.micWithoutHostApproval();
      if (!freeMic) {
        throw new ForbiddenException('Request mic access and wait for host approval');
      }
    }

    await this.dataSource.transaction(async (manager) => {
      const seat = await manager.findOne(RoomSeat, {
        where: { roomId, seatIndex: dto.seatIndex },
        lock: { mode: 'pessimistic_write' },
      });
      if (!seat) throw new NotFoundException('Seat not found');
      if (!isHost && (seat.isHostSeat || seat.seatIndex === 0)) {
        throw new ForbiddenException('Host seat is reserved');
      }
      // Room owner may take any seat (including guest seats).
      if (seat.status === SeatStatus.LOCKED) {
        throw new ForbiddenException('Seat is locked');
      }
      if (seat.userId && seat.userId !== userId) {
        throw new ConflictException('Seat occupied');
      }
      await manager.update(
        RoomSeat,
        { roomId, userId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      seat.userId = userId;
      seat.status = SeatStatus.OCCUPIED;
      seat.isMuted = false;
      seat.isModeratorMuted = false;
      await manager.save(RoomSeat, seat);
    });
    // Owner remains the live host for permissions; seat change only moves stage presence.
    if (isHost && room.activeHostId) {
      // Keep activeHostId on the room owner so privileges are never stripped mid-hop.
      if (room.activeHostId !== room.hostId) {
        room.activeHostId = room.hostId;
        await this.roomsRepo.save(room);
      }
    }
    await this.seatSignalsRepo.delete({ roomId, userId });
    const member = await this.memberIdentity(userId);
    // Instant seat map for everyone (free-mic sit without host queue).
    this.emitRoomEvent(roomId, 'room:seat_taken', {
      ...member,
      seatIndex: dto.seatIndex,
    });
    this.notifyRoomUpdated(roomId);
    void this.syncPersonalRoomEmptyState(roomId).catch(() => undefined);
    // Mic time task: each seat take counts toward mic_N
    void this.tasksService
      .recordProgress(userId, 'mic', 1, {
        roomId: room.id,
        agencyId: room.agencyId || undefined,
      })
      .catch(() => undefined);
    void this.tasksService
      .recordProgress(userId, 'host_mic', 1, {
        roomId: room.id,
        agencyId: room.agencyId || undefined,
      })
      .catch(() => undefined);
    return this.getRoom(roomId);
  }

  private async ensureAccess(roomId: string, userId: string, dto: JoinRoomDto) {
    const room = await this.roomsRepo
      .createQueryBuilder('r')
      .addSelect('r.passwordHash')
      .where('r.id = :id', { id: roomId })
      .getOne();
    if (!room) throw new NotFoundException('Room not found');
    if (room.status === RoomStatus.CLOSED) throw new BadRequestException('Room is closed');
    if (room.status === RoomStatus.LOCKED && room.hostId !== userId) {
      throw new ForbiddenException('Room is locked by host');
    }
    const ban = await this.bansRepo.findOne({ where: { roomId, userId } });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('You are banned from this room');
    }
    if (room.hasPassword && room.hostId !== userId) {
      if (!dto.password || !room.passwordHash) throw new ForbiddenException('Password required');
      const ok = await bcrypt.compare(dto.password, room.passwordHash);
      if (!ok) throw new ForbiddenException('Invalid room password');
    }
    return room;
  }

  private async chargeEntryFee(room: Room, userId: string) {
    if (room.hostId === userId) return;
    const mode = room.accessMode || RoomAccessMode.FREE;
    const fee = Math.max(0, Number(room.entryFeeCoins || 0));
    if (mode === RoomAccessMode.FREE || fee <= 0) return;

    await this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      const existing = await manager.findOne(RoomAccess, {
        where: { roomId: room.id, userId },
      });
      if (mode === RoomAccessMode.PERMANENT && existing) return;
      if (
        mode === RoomAccessMode.PAID &&
        existing?.grantType === RoomAccessGrant.SESSION
      ) {
        return;
      }
      if (!wallet || Number(wallet.coins) < fee) {
        throw new BadRequestException('Insufficient coins for room entry');
      }
      wallet.coins = Number(wallet.coins) - fee;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.ROOM_ENTRY,
          currency: CurrencyType.COINS,
          amount: -fee,
          balanceAfter: Number(wallet.coins),
          referenceType: 'room_entry',
          referenceId: room.id,
          description: `Room entry: ${room.title}`,
        }),
      );
      const hostWallet = await manager.findOne(Wallet, {
        where: { userId: room.hostId },
        lock: { mode: 'pessimistic_write' },
      });
      if (hostWallet) {
        hostWallet.coins = Number(hostWallet.coins) + fee;
        await manager.save(hostWallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId: room.hostId,
            type: TransactionType.ROOM_ENTRY,
            currency: CurrencyType.COINS,
            amount: fee,
            balanceAfter: Number(hostWallet.coins),
            referenceType: 'room_entry',
            referenceId: room.id,
            description: `Room entry revenue`,
          }),
        );
      }
      if (!existing) {
        await manager.save(
          manager.create(RoomAccess, {
            roomId: room.id,
            userId,
            grantType:
              mode === RoomAccessMode.PERMANENT
                ? RoomAccessGrant.PERMANENT
                : RoomAccessGrant.SESSION,
            coinsPaid: fee,
            expiresAt:
              mode === RoomAccessMode.PERMANENT
                ? null
                : new Date(Date.now() + 12 * 60 * 60_000),
          }),
        );
      } else if (mode === RoomAccessMode.PAID) {
        existing.coinsPaid = Number(existing.coinsPaid || 0) + fee;
        await manager.save(existing);
      }
    });
  }

  async leaveSeat(roomId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    // Owner may leave a seat when switching; closing the room is a separate path.
    await this.clearUserSeat(roomId, userId);
    this.notifyRoomUpdated(roomId);
    const member = await this.memberIdentity(userId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_left',
      payload: { roomId, ...member },
      at: new Date().toISOString(),
    });
    void this.syncPersonalRoomEmptyState(roomId).catch(() => undefined);
    return this.getRoom(roomId);
  }

  /** Force a seated user into audience (keeps room access). Used by auto-mod + host/mod. */
  async forceLeaveSeat(roomId: string, userId: string, reason?: string) {
    const seat = await this.seatsRepo.findOne({ where: { roomId, userId } });
    if (!seat) return { left: false, userId };
    await this.clearUserSeat(roomId, userId);
    this.notifyRoomUpdated(roomId);
    const member = await this.memberIdentity(userId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_left',
      payload: {
        roomId,
        ...member,
        forced: true,
        reason: reason || 'auto_moderation',
      },
      at: new Date().toISOString(),
    });
    void this.syncPersonalRoomEmptyState(roomId).catch(() => undefined);
    return { left: true, userId };
  }

  /** Host/moderator: remove someone from mic without kicking them from the room. */
  async forceLeaveSeatByModerator(
    roomId: string,
    actorId: string,
    targetUserId: string,
    reason?: string,
  ) {
    const uid = String(targetUserId || '').trim();
    if (!uid) throw new BadRequestException('معرّف المستخدم مطلوب');
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canManageSeats',
      'لا تملك صلاحية إنزال المستخدمين من المايك',
    );
    await this.assertCanTargetUser(roomId, actorId, uid);
    const seat = await this.seatsRepo.findOne({ where: { roomId, userId: uid } });
    if (!seat) {
      throw new BadRequestException('المستخدم ليس على المايك حالياً');
    }
    return this.forceLeaveSeat(roomId, uid, reason || 'moderator');
  }

  private async clearUserSeat(roomId: string, userId: string) {
    await this.seatsRepo.update(
      { roomId, userId },
      {
        userId: null,
        status: SeatStatus.EMPTY,
        isMuted: false,
        isModeratorMuted: false,
      },
    );
    await this.seatSignalsRepo.delete({ roomId, userId });
  }

  async setCohost(roomId: string, hostId: string, dto: SetCohostDto) {
    await this.assertRoomOwner(roomId, hostId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (dto.userId === hostId) {
      throw new BadRequestException('Room owner cannot be assigned as cohost');
    }
    const [user, access, seat] = await Promise.all([
      this.usersRepo.findOne({ where: { id: dto.userId } }),
      this.accessRepo.findOne({ where: { roomId, userId: dto.userId } }),
      this.seatsRepo.findOne({ where: { roomId, userId: dto.userId } }),
    ]);
    if (!user || user.status !== UserStatus.ACTIVE) {
      throw new NotFoundException('Active user not found');
    }
    if (!this.hasActiveAccess(access) && !seat && room.activeHostId !== dto.userId) {
      throw new ForbiddenException('User must join the room before becoming cohost');
    }
    if (room.agencyId) {
      const membership = await this.agencyMembersRepo.findOne({
        where: {
          agencyId: room.agencyId,
          userId: dto.userId,
          isActive: true,
          status: AgencyMemberStatus.ACTIVE,
        },
      });
      if (
        !membership ||
        ![AgencyRole.OWNER, AgencyRole.MANAGER].includes(membership.role)
      ) {
        throw new ForbiddenException('Cohost must be an agency owner or manager');
      }
    }
    room.cohostId = dto.userId || null;
    await this.roomsRepo.save(room);
    await this.notifyStaffUpdated(roomId);
    return this.getRoom(roomId);
  }

  async kick(roomId: string, actorId: string, dto: KickBanDto) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canKick',
      'لا تملك صلاحية الإخراج من الغرفة',
    );
    await this.assertCanTargetUser(roomId, actorId, dto.userId);
    await this.leave(roomId, dto.userId);
    const member = await this.memberIdentity(dto.userId);
    this.emitRoomEventToUser(roomId, dto.userId, 'room:kicked', {
      ...member,
      reason: dto.reason || null,
    });
    await this.realtimeGateway.ejectUserFromRoom(roomId, dto.userId);
    void this.ejectRtcUser(roomId, dto.userId, 'Kicked from voice room');
    void this.realtimeGateway.broadcastRoomPresence(roomId).catch(() => undefined);
    return { kicked: true, userId: dto.userId };
  }

  /** Auto-moderation: mute mic without requiring a human moderator. */
  async systemMuteMic(roomId: string, userId: string, reason?: string) {
    const seat = await this.seatsRepo.findOne({ where: { roomId, userId } });
    if (seat) {
      seat.isMuted = true;
      seat.isModeratorMuted = true;
      await this.seatsRepo.save(seat);
    }
    this.notifyRoomUpdated(roomId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:mic_changed',
      payload: {
        roomId,
        userId,
        muted: true,
        moderatorMuted: true,
        reason: reason || 'auto_moderation',
      },
      at: new Date().toISOString(),
    });
    return { muted: true, userId };
  }

  /** Auto-moderation: kick (and optionally short ban) without mod permission. */
  async systemKick(
    roomId: string,
    userId: string,
    reason?: string,
    options?: { banMinutes?: number },
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) return { kicked: false, userId };
    // Never auto-kick room owner / active host / cohost.
    if (
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId
    ) {
      return { kicked: false, userId, skipped: 'host' };
    }
    const banMinutes = Math.max(0, Number(options?.banMinutes || 0));
    if (banMinutes > 0) {
      const expiresAt = new Date(Date.now() + banMinutes * 60 * 1000);
      const existing = await this.bansRepo.findOne({ where: { roomId, userId } });
      if (existing) {
        existing.reason = reason || existing.reason;
        existing.expiresAt = expiresAt;
        existing.bannedById = room.hostId;
        await this.bansRepo.save(existing);
      } else {
        await this.bansRepo.save(
          this.bansRepo.create({
            roomId,
            userId,
            bannedById: room.hostId,
            reason: reason || 'auto_moderation',
            expiresAt,
          }),
        );
      }
    }
    await this.leave(roomId, userId);
    const member = await this.memberIdentity(userId);
    const eventName = banMinutes > 0 ? 'room:banned' : 'room:kicked';
    this.emitRoomEventToUser(roomId, userId, eventName, {
      ...member,
      reason: reason || 'auto_moderation',
      auto: true,
    });
    this.realtimeGateway.emitToUser(userId, 'moderation:action', {
      roomId,
      action: banMinutes > 0 ? 'ban' : 'kick',
      reason: reason || 'محتوى مخالف في الدردشة',
    });
    await this.realtimeGateway.ejectUserFromRoom(roomId, userId);
    void this.ejectRtcUser(roomId, userId, reason || 'Auto-moderation kick');
    void this.realtimeGateway.broadcastRoomPresence(roomId).catch(() => undefined);
    return { kicked: true, userId, banMinutes };
  }

  async ban(roomId: string, actorId: string, dto: KickBanDto) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canBan',
      'لا تملك صلاحية الطرد من الغرفة',
    );
    await this.assertCanTargetUser(roomId, actorId, dto.userId);
    const allowedDurations = [10, 30, 150 * 24 * 60];
    if (!dto.durationMinutes || !allowedDurations.includes(dto.durationMinutes)) {
      throw new BadRequestException('Allowed ban durations are 10, 30, or 216000 minutes');
    }
    const expiresAt = new Date(Date.now() + dto.durationMinutes * 60 * 1000);
    const existing = await this.bansRepo.findOne({
      where: { roomId, userId: dto.userId },
    });
    let saved: RoomBan;
    if (existing) {
      existing.reason = dto.reason || existing.reason;
      existing.expiresAt = expiresAt;
      existing.bannedById = actorId;
      saved = await this.bansRepo.save(existing);
    } else {
      saved = await this.bansRepo.save(
        this.bansRepo.create({
          roomId,
          userId: dto.userId,
          bannedById: actorId,
          reason: dto.reason || null,
          expiresAt,
        }),
      );
    }
    await this.leave(roomId, dto.userId);
    const member = await this.memberIdentity(dto.userId);
    this.emitRoomEventToUser(roomId, dto.userId, 'room:banned', {
      ...member,
      reason: saved.reason || null,
      expiresAt: saved.expiresAt,
    });
    await this.realtimeGateway.ejectUserFromRoom(roomId, dto.userId);
    void this.ejectRtcUser(roomId, dto.userId, 'Banned from voice room');
    void this.realtimeGateway.broadcastRoomPresence(roomId).catch(() => undefined);
    return saved;
  }

  async banStatus(roomId: string, actorId: string, targetUserId: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canBan',
      'لا تملك صلاحية الطرد من الغرفة',
    );
    const ban = await this.bansRepo.findOne({ where: { roomId, userId: targetUserId } });
    const active = !!(ban && (!ban.expiresAt || ban.expiresAt > new Date()));
    return { banned: active, expiresAt: active ? ban?.expiresAt ?? null : null };
  }

  async listBans(roomId: string, actorId: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canBan',
      'لا تملك صلاحية الطرد من الغرفة',
    );
    const rows = await this.bansRepo.find({
      where: { roomId },
      relations: ['user'],
      order: { createdAt: 'DESC' },
      take: 100,
    });
    const now = new Date();
    const banItems = rows
      .filter((b) => !b.expiresAt || b.expiresAt > now)
      .map((b) => ({
        id: b.id,
        kind: 'ban' as const,
        userId: b.userId,
        reason: b.reason,
        expiresAt: b.expiresAt,
        createdAt: b.createdAt,
        user: b.user
          ? {
              id: b.user.id,
              publicId: b.user.publicId,
              username: b.user.username,
              displayName: b.user.displayName,
              avatarUrl: b.user.avatarUrl,
            }
          : null,
      }));

    const muteRows = await this.moderation.listActiveChatMutes(roomId);
    const muteUserIds = muteRows.map((m) => m.userId);
    const muteUsers =
      muteUserIds.length > 0
        ? await this.usersRepo.find({ where: { id: In(muteUserIds) } })
        : [];
    const muteById = new Map(muteUsers.map((u) => [u.id, u]));
    const bannedIds = new Set(banItems.map((b) => b.userId));
    const muteItems = muteRows
      .filter((m) => !bannedIds.has(m.userId))
      .map((m) => {
        const u = muteById.get(m.userId);
        return {
          id: m.id,
          kind: 'chat_mute' as const,
          userId: m.userId,
          reason: `كتم الدردشة · مخالفة ${m.strikeCount || 0}`,
          expiresAt: m.chatMutedUntil,
          createdAt: m.updatedAt || m.createdAt,
          user: u
            ? {
                id: u.id,
                publicId: u.publicId,
                username: u.username,
                displayName: u.displayName,
                avatarUrl: u.avatarUrl,
              }
            : null,
        };
      });

    return { items: [...banItems, ...muteItems] };
  }

  async unban(roomId: string, actorId: string, targetUserId: string) {
    await this.assertModeratorPermission(
      roomId,
      actorId,
      'canBan',
      'لا تملك صلاحية الطرد من الغرفة',
    );
    await this.bansRepo.delete({ roomId, userId: targetUserId });
    await this.moderation.clearRoomChatPenalty(roomId, targetUserId);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:unbanned',
      payload: { roomId, userId: targetUserId, clearedChatMute: true },
      at: new Date().toISOString(),
    });
    this.realtimeGateway.emitToUser(targetUserId, 'moderation:action', {
      roomId,
      action: 'unmute',
      reason: 'تم رفع العقوبة من القائمة السوداء',
    });
    return { banned: false, userId: targetUserId, chatMuted: false };
  }

  async addModerator(roomId: string, hostId: string, userId: string) {
    await this.assertRoomOwner(roomId, hostId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (userId === hostId) {
      throw new BadRequestException('Room owner already has all permissions');
    }
    const [user, access, seat] = await Promise.all([
      this.usersRepo.findOne({ where: { id: userId } }),
      this.accessRepo.findOne({ where: { roomId, userId } }),
      this.seatsRepo.findOne({ where: { roomId, userId } }),
    ]);
    if (!user || user.status !== UserStatus.ACTIVE) {
      throw new NotFoundException('Active user not found');
    }
    if (
      !this.hasActiveAccess(access) &&
      !seat &&
      room.cohostId !== userId &&
      room.activeHostId !== userId
    ) {
      throw new ForbiddenException('User must join the room before becoming a moderator');
    }
    const existing = await this.modsRepo.findOne({ where: { roomId, userId } });
    if (existing) {
      await this.notifyStaffUpdated(roomId);
      return existing;
    }
    const saved = await this.modsRepo.save(
      this.modsRepo.create({
        roomId,
        userId,
        role: ModeratorRole.MOD,
        appointedById: hostId,
        // Mikoo-style: appointed مشرف gets full host operational control.
        // Owner can still revoke individual flags via PATCH permissions.
        canMute: true,
        canKick: true,
        canBan: true,
        canManageSeats: true,
        canInvite: true,
        canControlGames: true,
        canManageMusic: true,
        canChangeFrames: true,
        canManageRoom: true,
      }),
    );
    await this.notifyStaffUpdated(roomId);
    return saved;
  }

  async removeModerator(roomId: string, hostId: string, userId: string) {
    await this.assertRoomOwner(roomId, hostId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const existing = await this.modsRepo.findOne({ where: { roomId, userId } });
    if (existing) await this.modsRepo.remove(existing);
    if (room.cohostId === userId) {
      room.cohostId = null;
      await this.roomsRepo.save(room);
    }
    await this.notifyStaffUpdated(roomId);
    return { removed: true, userId };
  }

  async updateModeratorPermissions(
    roomId: string,
    ownerId: string,
    userId: string,
    dto: ModeratorPermissionsDto,
  ) {
    await this.assertRoomOwner(roomId, ownerId);
    const moderator = await this.modsRepo.findOne({ where: { roomId, userId } });
    if (!moderator) throw new NotFoundException('Moderator not found');
    if (dto.canManageMusic !== undefined) moderator.canManageMusic = dto.canManageMusic;
    if (dto.canChangeFrames !== undefined) moderator.canChangeFrames = dto.canChangeFrames;
    if (dto.canControlGames !== undefined) moderator.canControlGames = dto.canControlGames;
    if (dto.canMute !== undefined) moderator.canMute = dto.canMute;
    if (dto.canKick !== undefined) moderator.canKick = dto.canKick;
    if (dto.canBan !== undefined) moderator.canBan = dto.canBan;
    if (dto.canManageSeats !== undefined) moderator.canManageSeats = dto.canManageSeats;
    if (dto.canInvite !== undefined) moderator.canInvite = dto.canInvite;
    if (dto.canManageRoom !== undefined) moderator.canManageRoom = dto.canManageRoom;
    const saved = await this.modsRepo.save(moderator);
    await this.notifyStaffUpdated(roomId);
    return saved;
  }

    async close(roomId: string, hostId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const staff = await this.platformStaffRole(hostId);
    // Super may force-end any live room. Host/owner still may close their own.
    if (room.hostId !== hostId && staff !== 'super') {
      throw new ForbiddenException('Only host can close room');
    }

    // End the live session for everyone (agency rooms stay in DB; reopen via create/open).
    await this.seatsRepo.update(
      { roomId },
      {
        userId: null,
        status: SeatStatus.EMPTY,
        isMuted: false,
        isModeratorMuted: false,
      },
    );
    // Cancel every pending mic request / invite when the room closes.
    await this.seatSignalsRepo.delete({ roomId });
    room.activeHostId = null;
    room.liveSessionStartedAt = null;
    room.emptySince = null;
    room.viewerCount = 0;
    room.status = RoomStatus.CLOSED;
    await this.roomsRepo.save(room);
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:seat_requests_cleared',
      payload: { roomId, reason: 'room_closed' },
      at: new Date().toISOString(),
    });
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:supporters_cleared',
      payload: { roomId },
      at: new Date().toISOString(),
    });
    this.notifyRoomClosed(roomId);
    this.notifyRoomUpdated(roomId);
    return { closed: true, deleted: false, id: roomId };
  }

  async update(
    roomId: string,
    hostId: string,
    dto: {
      title?: string;
      description?: string;
      coverUrl?: string;
      backgroundUrl?: string;
      accessMode?: RoomAccessMode;
      entryFeeCoins?: number;
    },
  ) {
    await this.assertModeratorPermission(
      roomId,
      hostId,
      'canManageRoom',
      'Room management permission is required',
    );
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const isAgencyRoom = room.roomKind === RoomKind.AGENCY || !!room.agencyId;
    if (!isAgencyRoom) {
      if (dto.title !== undefined) {
        this.moderation.assertNoAgencyImpersonation(dto.title, 'اسم الروم');
      }
      if (dto.description !== undefined && dto.description) {
        this.moderation.assertNoAgencyImpersonation(dto.description, 'وصف الروم');
      }
    }
    if (dto.title !== undefined) room.title = dto.title;
    if (dto.description !== undefined) room.description = dto.description || null;
    if (dto.coverUrl !== undefined) {
      this.mediaCleanup.replaceUpload(room.coverUrl, dto.coverUrl || null);
      room.coverUrl = dto.coverUrl || null;
      // Keep agency profile face in sync — cover often IS the brand image.
      if (room.agencyId && room.coverUrl && String(room.coverUrl).trim()) {
        const next = String(room.coverUrl).trim();
        const generic =
          next.toLowerCase().includes('backgrounds/bg_') ||
          next.toLowerCase().includes('/assets/backgrounds');
        if (!generic) {
          try {
            const ag = await this.agenciesRepo.findOne({
              where: { id: room.agencyId },
              select: ['id', 'logoUrl'],
            });
            if (ag && (!ag.logoUrl || !String(ag.logoUrl).trim())) {
              await this.agenciesRepo.update(
                { id: room.agencyId },
                { logoUrl: next.slice(0, 512) },
              );
            }
          } catch {
            /* non-fatal */
          }
        }
      }
    }
    if (dto.backgroundUrl !== undefined) {
      // Room wallpapers are shared catalog URLs (/assets or /uploads cosmetics).
      // Never delete the previous file when switching — that wiped admin uploads.
      const nextBackground = dto.backgroundUrl?.trim() || null;
      if (nextBackground) {
        await this.assertOwnedRoomCosmetic(
          hostId,
          'room_background',
          nextBackground,
        );
      }
      room.backgroundUrl = nextBackground;
      room.backgroundEquippedById = nextBackground ? hostId : null;
    }
    if (room.roomKind === RoomKind.AGENCY || room.agencyId) {
      room.status = RoomStatus.OPEN;
      room.isPublic = true;
      room.hasPassword = false;
      room.passwordHash = null;
      room.accessMode = RoomAccessMode.FREE;
      room.entryFeeCoins = 0;
    }
    await this.roomsRepo.save(room);
    return this.getRoom(roomId);
  }

  async setBackground(roomId: string, actorId: string, backgroundUrl: string | null) {
    await this.assertCanChangeFrames(roomId, actorId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const next = backgroundUrl?.trim() || null;
    if (next) {
      await this.assertOwnedRoomCosmetic(actorId, 'room_background', next);
    }
    // Do NOT mediaCleanup.replaceUpload here: backgroundUrl points at shared
    // cosmetic/catalog media. Deleting it broke dashboard-uploaded wallpapers.
    room.backgroundUrl = next;
    room.backgroundEquippedById = next ? actorId : null;
    await this.roomsRepo.save(room);
    const payload = { roomId, backgroundUrl: room.backgroundUrl };
    this.realtimeGateway.emitToRoom(roomId, 'room:background', payload);
    return payload;
  }

  async setFrame(roomId: string, actorId: string, roomCardUrl: string | null) {
    await this.assertCanChangeFrames(roomId, actorId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const next = roomCardUrl?.trim() || null;
    if (next) {
      await this.assertOwnedRoomCosmetic(actorId, 'room_card', next);
    }
    room.roomCardUrl = next;
    room.roomCardEquippedById = next ? actorId : null;
    await this.roomsRepo.save(room);
    const payload = { roomId, roomCardUrl: room.roomCardUrl };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:frame',
      payload,
      at: new Date().toISOString(),
    });
    this.notifyRoomUpdated(roomId);
    return payload;
  }

  private async assertOwnedRoomCosmetic(
    userId: string,
    type: 'room_background' | 'room_card',
    previewUrl: string,
  ) {
    const wanted = String(previewUrl || '').trim();
    const wantedBase = wanted.split('?')[0];
    const rows = await this.dataSource.query(
      `SELECT c.id
         FROM cosmetics c
    LEFT JOIN user_cosmetics owned
           ON owned."cosmeticId" = c.id
          AND owned."userId" = $1
          AND (owned."expiresAt" IS NULL OR owned."expiresAt" > NOW())
        WHERE c.type::text = $2
          AND (
            c."previewUrl" = $3
            OR c."animationUrl" = $3
            OR split_part(COALESCE(c."previewUrl", ''), '?', 1) = $4
            OR split_part(COALESCE(c."animationUrl", ''), '?', 1) = $4
          )
          AND c."isActive" = true
          AND (
            owned.id IS NOT NULL
            OR (
              COALESCE(c."coinPrice", 0) = 0
              AND COALESCE(c."minVipLevel", 0) = 0
              AND COALESCE(c."minUserLevel", 0) = 0
            )
          )
        LIMIT 1`,
      [userId, type, wanted, wantedBase],
    );
    if (!rows.length) {
      throw new ForbiddenException('Own this room cosmetic before applying it');
    }
  }

  async updateMusic(roomId: string, actorId: string, dto: RoomMusicDto) {
    await this.assertCanManageMusic(roomId, actorId);
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const currentPosition =
      room.musicStatus === 'playing' && room.musicStartedAt
        ? Number(room.musicPositionMs || 0) + Date.now() - room.musicStartedAt.getTime()
        : Number(room.musicPositionMs || 0);
    const safeCurrentPosition = Math.min(
      86_400_000,
      Math.max(0, currentPosition),
    );
    if (dto.action === 'load') {
      if (!dto.url?.trim()) throw new BadRequestException('Music URL is required');
      const musicUrl = dto.url.trim();
      const isLocalOnly = /^local:\/\//i.test(musicUrl);
      const isYoutubeRef = /^yt:\/\/[A-Za-z0-9_-]{6,20}$/i.test(musicUrl);
      const isAppUpload =
        /^\/uploads\/[^/?#]+\.(mp3|m4a|aac|wav)$/i.test(musicUrl);
      // Direct HTTP(S) audio files — not YouTube watch pages.
      const isRemoteAudio =
        /^https?:\/\/.+\.(mp3|m4a|aac|wav)(\?|#|$)/i.test(musicUrl);
      if (!isLocalOnly) {
        if (/youtu\.be\/|youtube\.com\//i.test(musicUrl)) {
          throw new BadRequestException(
            'Use in-app internet search, not a raw YouTube page link',
          );
        }
        if (!isYoutubeRef && !isAppUpload && !isRemoteAudio) {
          throw new ForbiddenException(
            'Music must be local, uploaded, yt://id, or a direct audio URL',
          );
        }
        if (isAppUpload) {
          const uploadDir =
            this.configService.get<string>('app.uploadDir') || './uploads';
          const musicFile = join(uploadDir, basename(musicUrl));
          if (!existsSync(musicFile)) {
            throw new NotFoundException('Uploaded music file was not found');
          }
        }
      }
      room.musicUrl = musicUrl;
      room.musicTitle = (dto.title?.trim() || 'موسيقى').slice(0, 180);
      room.musicArtist = dto.artist?.trim().slice(0, 180) || null;
      room.musicPositionMs = 0;
      room.musicStatus = 'playing';
      room.musicStartedAt = new Date();
    } else if (dto.action === 'play') {
      if (!room.musicUrl) throw new BadRequestException('No music selected');
      room.musicPositionMs = dto.positionMs ?? safeCurrentPosition;
      room.musicStatus = 'playing';
      room.musicStartedAt = new Date();
    } else if (dto.action === 'pause') {
      room.musicPositionMs = dto.positionMs ?? safeCurrentPosition;
      room.musicStatus = 'paused';
      room.musicStartedAt = null;
    } else if (dto.action === 'seek') {
      room.musicPositionMs = dto.positionMs ?? 0;
      room.musicStartedAt = room.musicStatus === 'playing' ? new Date() : null;
    } else {
      room.musicPositionMs = 0;
      room.musicStatus = 'stopped';
      room.musicStartedAt = null;
    }
    await this.roomsRepo.save(room);
    if (
      dto.action === 'load' &&
      room.musicUrl &&
      !/^local:\/\//i.test(room.musicUrl) &&
      !/^yt:\/\//i.test(room.musicUrl)
    ) {
      await this.musicTracksRepo.upsert(
        {
          uploadedById: actorId,
          url: room.musicUrl,
          title: room.musicTitle || 'موسيقى',
          artist: room.musicArtist,
          isActive: true,
        },
        ['url'],
      );
    }
    const payload = {
      roomId,
      url: room.musicUrl,
      title: room.musicTitle,
      artist: room.musicArtist,
      status: room.musicStatus,
      positionMs: Number(room.musicPositionMs || 0),
      startedAt: room.musicStartedAt?.toISOString() || null,
      serverTime: new Date().toISOString(),
      updatedBy: actorId,
    };
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:music',
      payload,
      at: new Date().toISOString(),
    });
    return payload;
  }

  private async assertModeratorPermission(
    roomId: string,
    actorId: string,
    permission:
      | 'canMute'
      | 'canKick'
      | 'canBan'
      | 'canManageSeats'
      | 'canInvite'
      | 'canManageRoom',
    message: string,
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    // Host / cohost / active host keep moderation even if agency status is odd.
    if (
      room.hostId === actorId ||
      room.cohostId === actorId ||
      room.activeHostId === actorId
    ) {
      return;
    }
    // Platform staff: super = full room powers; manager = mute/kick/ban/seats/invite.
    const staff = await this.platformStaffRole(actorId);
    if (staff === 'super') return;
    if (staff === 'manager' && permission !== 'canManageRoom') {
      return;
    }
    await this.assertAgencyRoomActive(room);
    // Agency owner may manage agency rooms for settings/moderation APIs.
    if (room.agencyId) {
      const agency = await this.agenciesRepo.findOne({ where: { id: room.agencyId } });
      if (agency && agency.ownerId === actorId) return;
    }
    const moderator = await this.modsRepo.findOne({
      where: { roomId, userId: actorId },
    });
    // Display / room management: any appointed moderator (or one with the flag).
    if (permission === 'canManageRoom' && moderator) {
      return;
    }
    if (!moderator || moderator[permission] !== true) {
      throw new ForbiddenException(message);
    }
  }

  private async assertCanManageMusic(roomId: string, actorId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if (
      room.hostId === actorId ||
      room.cohostId === actorId ||
      room.activeHostId === actorId
    ) {
      return;
    }
    if ((await this.platformStaffRole(actorId)) === 'super') return;
    const mod = await this.modsRepo.findOne({ where: { roomId, userId: actorId } });
    if (!mod?.canManageMusic) {
      throw new ForbiddenException('Music permission is required');
    }
  }

  private async assertCanChangeFrames(roomId: string, actorId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if (
      room.hostId === actorId ||
      room.cohostId === actorId ||
      room.activeHostId === actorId
    ) {
      return;
    }
    if ((await this.platformStaffRole(actorId)) === 'super') return;
    const mod = await this.modsRepo.findOne({ where: { roomId, userId: actorId } });
    if (!mod?.canChangeFrames) {
      throw new ForbiddenException('Frame permission is required');
    }
  }

  private async assertRoomOwner(roomId: string, actorId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertAgencyRoomActive(room);
    if ((await this.platformStaffRole(actorId)) === 'super') return;
    if (room.hostId !== actorId) {
      throw new ForbiddenException('Only the room owner can manage moderators');
    }
  }

  private async platformStaffRole(userId: string): Promise<PlatformStaffRole> {
    if (!userId) return 'none';
    try {
      const user = await this.usersRepo.findOne({
        where: { id: userId },
        select: ['id', 'isAdmin', 'staffRole'] as any,
      });
      return normalizeStaffRole(user as any);
    } catch {
      return 'none';
    }
  }

  private async assertAgencyRoomActive(room: Pick<Room, 'agencyId' | 'roomKind'>) {
    if (!room.agencyId && room.roomKind !== RoomKind.AGENCY) return;
    if (!room.agencyId) throw new ForbiddenException('Agency room is unavailable');
    const agency = await this.agenciesRepo.findOne({ where: { id: room.agencyId } });
    if (!agency || agency.status !== AgencyStatus.ACTIVE) {
      throw new ForbiddenException('Agency is suspended or inactive');
    }
  }

  private hasActiveAccess(access: RoomAccess | null | undefined) {
    if (!access) return false;
    if (access.grantType === RoomAccessGrant.PERMANENT) return true;
    return !access.expiresAt || access.expiresAt > new Date();
  }

  private async ejectRtcUser(
    roomId: string,
    userId: string,
    reason: string,
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) return false;
    return this.zegoTokenService.kickUser(
      room.zegoRoomId || room.id,
      userId,
      reason,
    );
  }

  /**
   * Mods/cohosts cannot kick/ban the room owner or themselves.
   * Super admin may target anyone except equal/higher platform staff.
   */
  private async assertCanTargetUser(roomId: string, actorId: string, targetUserId: string) {
    if (!targetUserId) throw new BadRequestException('معرّف المستخدم مطلوب');
    if (actorId === targetUserId) {
      throw new ForbiddenException('لا يمكنك طرد نفسك');
    }
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');

    const actorStaff = await this.platformStaffRole(actorId);
    const targetStaff = await this.platformStaffRole(targetUserId);
    if (staffRank(targetStaff) > 0 && staffRank(actorStaff) < staffRank(targetStaff)) {
      throw new ForbiddenException('لا يمكن استهداف طاقم المنصة الأعلى رتبة');
    }
    if (
      staffRank(targetStaff) > 0 &&
      staffRank(actorStaff) === staffRank(targetStaff) &&
      actorStaff !== 'super'
    ) {
      throw new ForbiddenException('لا يمكن لمشرف المنصة استهداف زميل بنفس الرتبة');
    }

    // Super admin may moderate even the room owner.
    if (actorStaff === 'super') return;

    if (room.hostId === targetUserId) {
      throw new ForbiddenException('لا يمكن طرد صاحب الغرفة');
    }
    if (room.hostId !== actorId) {
      if (room.cohostId === targetUserId) {
        throw new ForbiddenException('لا يمكن للمشرف استهداف المضيف المساعد');
      }
      const targetModerator = await this.modsRepo.findOne({
        where: { roomId, userId: targetUserId },
      });
      if (targetModerator) {
        throw new ForbiddenException('لا يمكن لمشرف طرد مشرف آخر');
      }
    }
  }
}
