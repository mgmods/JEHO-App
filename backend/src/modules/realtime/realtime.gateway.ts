import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayConnection,
  OnGatewayDisconnect,
  ConnectedSocket,
  MessageBody,
} from '@nestjs/websockets';
import {
  Logger,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { In, MoreThan, Repository } from 'typeorm';
import { Server, Socket } from 'socket.io';
import { ChatParticipant } from '../../database/entities/chat-participant.entity';
import { RoomSeat, SeatStatus } from '../../database/entities/room-seat.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import { Room, RoomStatus, RoomKind } from '../../database/entities/room.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import {
  RoomAccess,
  RoomAccessGrant,
} from '../../database/entities/room-access.entity';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import { User, UserStatus } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { levelFromScore, MAX_ECONOMY_LEVEL } from '../../common/pricing-catalog';
import { effectiveVipLevel } from '../../common/vip-progress';
import { ContentModerationService } from '../moderation/content-moderation.service';
import { ZegoTokenService } from '../zego/zego-token.service';

interface AuthSocket extends Socket {
  userId?: string;
  username?: string;
}

const ALLOWED_ROOM_EVENTS = new Set([
  'room:typing',
  'room:reaction',
  'lucky:opened',
  'room:like',
  'chat:message',
  'room:summon',
  'room:chat_cleared',
]);

@WebSocketGateway({
  cors: { origin: '*' },
  namespace: '/realtime',
})
export class RealtimeGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer()
  server!: Server;

  private readonly logger = new Logger(RealtimeGateway.name);
  private readonly onlineUsers = new Map<string, Set<string>>();

  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    @InjectRepository(ChatParticipant)
    private readonly chatParticipants: Repository<ChatParticipant>,
    @InjectRepository(RoomSeat)
    private readonly roomSeats: Repository<RoomSeat>,
    @InjectRepository(RoomModerator)
    private readonly roomModerators: Repository<RoomModerator>,
    @InjectRepository(Room)
    private readonly roomsRepo: Repository<Room>,
    @InjectRepository(RoomBan)
    private readonly roomBans: Repository<RoomBan>,
    @InjectRepository(RoomAccess)
    private readonly roomAccess: Repository<RoomAccess>,
    @InjectRepository(Agency)
    private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(UserProfile)
    private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(UserVip)
    private readonly userVipRepo: Repository<UserVip>,
    @InjectRepository(GiftSend)
    private readonly giftSendsRepo: Repository<GiftSend>,
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly moderation: ContentModerationService,
    private readonly zegoTokenService: ZegoTokenService,
  ) {}

  async handleConnection(client: AuthSocket) {
    try {
      const token =
        (client.handshake.auth?.token as string) ||
        (client.handshake.headers?.authorization as string)?.replace('Bearer ', '');
      if (!token) {
        client.disconnect();
        return;
      }
      const payload = await this.jwtService.verifyAsync(token, {
        secret: this.configService.get<string>('app.jwt.secret'),
      });
      const user = await this.usersRepo.findOne({ where: { id: payload.sub } });
      if (!user || user.status !== UserStatus.ACTIVE) {
        client.disconnect();
        return;
      }
      client.userId = user.id;
      client.username = user.username;
      client.data.userId = user.id;
      client.data.username = user.username;
      client.data.roomProfiles = {};
      if (!this.onlineUsers.has(user.id)) {
        this.onlineUsers.set(user.id, new Set());
      }
      this.onlineUsers.get(user.id)!.add(client.id);
      client.join(`user:${user.id}`);
      this.logger.log(`Connected ${user.username} (${client.id})`);
    } catch {
      client.disconnect();
    }
  }

  async handleDisconnect(client: AuthSocket) {
    if (!client.userId) return;
    const joinedRoomIds = Object.keys(client.data.roomProfiles || {});
    const sockets = this.onlineUsers.get(client.userId);
    if (sockets) {
      sockets.delete(client.id);
      if (sockets.size === 0) {
        this.onlineUsers.delete(client.userId);
      }
    }
    const remainingSockets = await this.server
      .in(`user:${client.userId}`)
      .fetchSockets();
    if (remainingSockets.length === 0) {
      for (const roomId of joinedRoomIds) {
        await this.autoLeaveSeatOnDisconnect(client.userId, roomId);
      }
    }
    for (const roomId of joinedRoomIds) {
      await this.emitRoomMembers(roomId);
    }
  }

  /** When the user's last socket drops, free any voice-room mic seat they occupied. */
  private async autoLeaveSeatOnDisconnect(userId: string, roomId: string) {
    const [seated, sessionAccess, room] = await Promise.all([
      this.roomSeats.findOne({ where: { roomId, userId } }),
      this.roomAccess.findOne({
        where: { roomId, userId, grantType: RoomAccessGrant.SESSION },
      }),
      this.roomsRepo.findOne({ where: { id: roomId } }),
    ]);
    if (seated) {
      await this.roomSeats.update(
        { roomId, userId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      this.server.to(`room:${roomId}`).emit('room:event', {
        roomId,
        event: 'room:seat_left',
        payload: { roomId, userId },
        at: new Date().toISOString(),
      });
    }
    if (sessionAccess) await this.roomAccess.remove(sessionAccess);
    // Never auto-close the room on socket drop (network blip / background / reconnect).
    // HTTP leave() already keeps live sessions open; only POST /rooms/:id/close ends the stream.
    if (room?.activeHostId === userId) {
      this.server.to(`room:${roomId}`).emit('room:event', {
        roomId,
        event: 'room:host_away',
        payload: { roomId, userId, activeHostId: room.activeHostId, reason: 'host_disconnected' },
        at: new Date().toISOString(),
      });
    }
    this.server.to(`room:${roomId}`).emit('room:event', {
      roomId,
      event: 'room:user_left',
      payload: { roomId, userId },
      at: new Date().toISOString(),
    });
    void this.emitRoomMembers(roomId);
  }

  @SubscribeMessage('room:join')
  async onRoomJoin(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    data: {
      roomId: string;
      displayName?: string;
      avatarUrl?: string;
      vipLevel?: number;
      userLevel?: number;
    },
  ) {
    if (!data?.roomId || !client.userId) return;

    const room = await this.roomsRepo.findOne({ where: { id: data.roomId } });
    if (!room) return { error: 'Room not found' };
    if (room.status === RoomStatus.CLOSED) return { error: 'Room is closed' };
    if (room.agencyId) {
      const agency = await this.agenciesRepo.findOne({ where: { id: room.agencyId } });
      if (!agency || agency.status !== AgencyStatus.ACTIVE) {
        return { error: 'Agency is suspended or inactive' };
      }
    }
    const ban = await this.roomBans.findOne({
      where: { roomId: data.roomId, userId: client.userId },
    });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      return { error: 'You are banned from this room' };
    }
    const [seat, access] = await Promise.all([
      this.roomSeats.findOne({ where: { roomId: data.roomId, userId: client.userId } }),
      this.roomAccess.findOne({ where: { roomId: data.roomId, userId: client.userId } }),
    ]);
    const privileged =
      room.hostId === client.userId ||
      room.activeHostId === client.userId ||
      room.cohostId === client.userId ||
      !!seat;
    if (!privileged && !this.hasActiveAccess(access)) {
      return { error: 'HTTP room authorization required' };
    }
    const profilePayload = await this.buildJoinPayload(client.userId, data.roomId, room.hostId);

    client.data.roomProfiles = client.data.roomProfiles || {};
    client.data.roomProfiles[data.roomId] = profilePayload;
    client.join(`room:${data.roomId}`);
    this.server.to(`room:${data.roomId}`).emit('room:user_joined', {
      roomId: data.roomId,
      ...profilePayload,
    });

    const membersPayload = await this.emitRoomMembers(data.roomId);
    this.server.to(`room:${data.roomId}`).emit('room:event', {
      roomId: data.roomId,
      event: 'room:viewer_count',
      payload: { viewerCount: membersPayload?.viewerCount ?? 0 },
      from: { userId: client.userId, username: client.username },
      at: new Date().toISOString(),
    });

    return { joined: data.roomId, profile: profilePayload };
  }

  private async buildJoinPayload(userId: string, roomId: string, hostId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    const profile = await this.profilesRepo.findOne({ where: { userId } });
    const activeCosmeticRows: Array<{
      type: string;
      previewUrl: string | null;
      animationUrl: string | null;
    }> = await this.profilesRepo.manager.query(
      `SELECT cosmetic.type AS type,
              cosmetic."previewUrl" AS "previewUrl",
              cosmetic."animationUrl" AS "animationUrl"
         FROM user_cosmetics owned
         JOIN cosmetics cosmetic ON cosmetic.id = owned."cosmeticId"
        WHERE owned."userId" = $1
          AND owned.equipped = true
          AND (owned."expiresAt" IS NULL OR owned."expiresAt" > NOW())
          AND cosmetic."isActive" = true`,
      [userId],
    );
    const normalizeUrl = (url: string | null | undefined) => {
      if (!url) return '';
      let u = String(url).trim();
      if (!u) return '';
      // Compare by path only — profile may store absolute CDN while catalog is relative.
      u = u.replace(/^https?:\/\/[^/]+/i, '');
      const q = u.indexOf('?');
      if (q >= 0) u = u.slice(0, q);
      return u;
    };
    const activeCosmeticUrls = new Set<string>();
    const activeNormalized = new Set<string>();
    for (const row of activeCosmeticRows) {
      for (const raw of [row.previewUrl, row.animationUrl]) {
        if (!raw) continue;
        activeCosmeticUrls.add(raw);
        const n = normalizeUrl(raw);
        if (n) activeNormalized.add(n);
      }
    }
    const activeUrl = (url: string | null | undefined) => {
      if (!url) return null;
      if (activeCosmeticUrls.has(url)) return url;
      const n = normalizeUrl(url);
      return n && activeNormalized.has(n) ? url : null;
    };
    const playableCosmeticUrl = (preview: string | null, animation: string | null) => {
      const pick = (u: string | null) => {
        if (!u) return null;
        const lower = u.toLowerCase();
        if (lower.includes('runtime.html') || lower.endsWith('.html') || lower.endsWith('.json')) {
          return null;
        }
        return u;
      };
      return pick(animation) || pick(preview) || null;
    };
    // Prefer currently equipped entry_effect / join_toast rows (authoritative).
    const entryRow =
      activeCosmeticRows.find((r) => r.type === 'entry_effect') ||
      activeCosmeticRows.find((r) => r.type === 'join_toast') ||
      null;
    const vip = await this.userVipRepo.findOne({
      where: { userId, isActive: true, expiresAt: MoreThan(new Date()) },
      order: { level: 'DESC' },
    });
    const vipLevel = effectiveVipLevel(
      Number(vip?.level || 0),
      Number(profile?.totalSentCoins || 0),
    );
    const spent = await this.roomSpendCoins(userId, roomId);
    const supporterTier = await this.resolveSupporterTier(vipLevel, spent);
    const displayName = user?.displayName || user?.username || 'User';
    const avatarUrl = user?.avatarUrl || null;
    const entryEffectUrl =
      (entryRow ? entryRow.previewUrl : null) ||
      activeUrl(profile?.entryEffectUrl) ||
      null;
    const entryAnimationUrl =
      (entryRow
        ? playableCosmeticUrl(entryRow.previewUrl, entryRow.animationUrl)
        : null) ||
      activeUrl(profile?.entryAnimationUrl) ||
      playableCosmeticUrl(entryEffectUrl, null) ||
      null;
    const roomCardUrl = activeUrl(profile?.roomCardUrl) ?? profile?.roomCardUrl ?? null;
    // Prefer active catalog match, but never strip a profile-equipped frame/badge.
    const vipBadgeUrl = activeUrl(profile?.vipBadgeUrl) ?? profile?.vipBadgeUrl ?? null;
    const levelBadgeUrl = activeUrl(profile?.levelBadgeUrl) ?? profile?.levelBadgeUrl ?? null;
    const hostBadgeUrl = activeUrl(profile?.hostBadgeUrl) ?? profile?.hostBadgeUrl ?? null;
    const totalSentCoins = Math.max(0, Number(profile?.totalSentCoins || 0));
    const totalReceived = Math.max(0, Number(profile?.totalReceivedDiamonds || 0));
    const wealthLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(totalSentCoins));
    const charmLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(totalReceived));
    const isHost = hostId === userId;
    const cosmeticUrls = [entryEffectUrl, entryAnimationUrl].filter(
      (url): url is string => !!url,
    );
    const cosmetics = cosmeticUrls.length
      ? await this.profilesRepo.manager.getRepository(Cosmetic).find({
          where: [
            { previewUrl: In(cosmeticUrls) },
            { animationUrl: In(cosmeticUrls) },
          ],
        })
      : [];
    const metaFor = (previewUrl: string | null, animationUrl: string | null) =>
      (cosmetics.find(
        (c) =>
          c.previewUrl === previewUrl ||
          c.animationUrl === animationUrl ||
          c.previewUrl === animationUrl ||
          c.animationUrl === previewUrl ||
          normalizeUrl(c.previewUrl) === normalizeUrl(previewUrl) ||
          normalizeUrl(c.animationUrl) === normalizeUrl(animationUrl),
      )?.meta as Record<string, unknown> | null) ?? null;
    const entryEffectMeta = metaFor(entryEffectUrl, entryAnimationUrl);
    const cosmeticMeta =
      entryEffectUrl || entryAnimationUrl ? entryEffectMeta : null;
    const metadataPriority = Number(cosmeticMeta?.effectPriority);
    const tierPriority =
      supporterTier === 'legendary'
        ? 100
        : supporterTier === 'supporter'
          ? 80
          : vipLevel > 0
            ? 60
            : 20;

    return {
      userId,
      username: user?.username || displayName,
      displayName,
      avatarUrl,
      vipLevel,
      userLevel: Math.max(1, Number(user?.level || 1)),
      entryEffectUrl,
      entryAnimationUrl,
      animationUrl: entryAnimationUrl,
      roomCardUrl,
      vipBadgeUrl,
      levelBadgeUrl,
      hostBadgeUrl,
      isHost,
      supporterTier,
      roomSpendCoins: spent,
      wealthScore: wealthLevel,
      wealthLevel,
      charmScore: charmLevel,
      charmLevel,
      totalSentCoins,
      totalReceivedDiamonds: totalReceived,
      effectPriority: Number.isFinite(metadataPriority) ? metadataPriority : tierPriority,
      entryEffectMeta,
      cosmeticMeta,
      renderMode: this.metaString(cosmeticMeta, 'renderMode'),
      aspectRatio: this.metaNumber(cosmeticMeta, 'aspectRatio'),
      textSafeArea: cosmeticMeta?.textSafeArea ?? null,
      durationMs: this.metaNumber(cosmeticMeta, 'durationMs'),
    };
  }

  private metaString(meta: Record<string, unknown> | null, key: string) {
    const value = meta?.[key];
    return typeof value === 'string' && value.trim() ? value.trim() : null;
  }

  private metaNumber(meta: Record<string, unknown> | null, key: string) {
    const raw = meta?.[key];
    let value = Number(raw);
    if (typeof raw === 'string' && raw.includes(':')) {
      const [width, height] = raw.split(':').map(Number);
      value = width > 0 && height > 0 ? width / height : Number.NaN;
    }
    return Number.isFinite(value) && value > 0 ? value : null;
  }

  private async roomSpendCoins(userId: string, roomId: string): Promise<number> {
    const raw = await this.giftSendsRepo
      .createQueryBuilder('g')
      .select('COALESCE(SUM(g.totalCoins), 0)', 'total')
      .where('g.senderId = :userId', { userId })
      .andWhere('g.roomId = :roomId', { roomId })
      .getRawOne<{ total: string }>();
    return Math.max(0, Number(raw?.total || 0));
  }

  private async resolveSupporterTier(vipLevel: number, spent: number) {
    const supporterMin = await this.settingNumber('room.supporter_min_coins', 10000);
    const legendaryMin = await this.settingNumber('room.legendary_min_coins', 50000);
    if (spent >= legendaryMin) return 'legendary';
    if (spent >= supporterMin) return 'supporter';
    if (vipLevel > 0) return 'vip';
    return 'normal';
  }

  private async settingNumber(key: string, fallback: number) {
    const row = await this.settingsRepo.findOne({ where: { key } });
    const n = Number(row?.value);
    return Number.isFinite(n) ? n : fallback;
  }

  @SubscribeMessage('room:leave')
  async onRoomLeave(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { roomId: string },
  ) {
    if (!data?.roomId) return;
    if (client.data.roomProfiles) delete client.data.roomProfiles[data.roomId];
    client.leave(`room:${data.roomId}`);

    const membersPayload = await this.emitRoomMembers(data.roomId);
    const viewerCount = membersPayload?.viewerCount ?? 0;

    client.to(`room:${data.roomId}`).emit('room:user_left', {
      roomId: data.roomId,
      userId: client.userId,
      viewerCount,
    });

    this.server.to(`room:${data.roomId}`).emit('room:event', {
      roomId: data.roomId,
      event: 'room:viewer_count',
      payload: { viewerCount },
      from: { userId: client.userId, username: client.username },
      at: new Date().toISOString(),
    });

    return { left: data.roomId, viewerCount };
  }

  private async emitRoomMembers(roomId: string) {
    const sockets = await this.server.in(`room:${roomId}`).fetchSockets();
    const unique = new Map<string, Record<string, unknown>>();
    for (const socket of sockets) {
      const member = socket.data?.roomProfiles?.[roomId];
      if (member?.userId) unique.set(member.userId, member);
    }
    const members = Array.from(unique.values());
    await this.roomsRepo.update(
      { id: roomId },
      { viewerCount: members.length, updatedAt: new Date() },
    );
    void this.syncPersonalEmptySince(roomId, members.length);
    this.server.to(`room:${roomId}`).emit('room:members', {
      roomId,
      members,
      viewerCount: members.length,
    });
    this.server.to(`room:${roomId}`).emit('room:event', {
      roomId,
      event: 'room:viewer_count',
      payload: { viewerCount: members.length, roomId },
      at: new Date().toISOString(),
    });
    return { roomId, members, viewerCount: members.length };
  }

  /** Public rebroadcast of live members / viewer count (HTTP leave, kick, ban). */
  async broadcastRoomPresence(roomId: string) {
    return this.emitRoomMembers(roomId);
  }

  /**
   * Personal rooms only: start/stop empty timer based on realtime presence.
   * Host alone (muted) still has a socket member → emptySince cleared.
   */
  private async syncPersonalEmptySince(roomId: string, viewerCount: number) {
    try {
      const room = await this.roomsRepo.findOne({ where: { id: roomId } });
      if (!room?.activeHostId) return;
      if (room.agencyId || room.roomKind === RoomKind.AGENCY) return;
      if (room.roomKind === RoomKind.SUPPORT) return;
      if (room.status === RoomStatus.CLOSED) return;
      const [seated, sessions] = await Promise.all([
        this.roomSeats
          .createQueryBuilder('s')
          .where('s.roomId = :roomId', { roomId })
          .andWhere('s.userId IS NOT NULL')
          .getCount(),
        this.roomAccess
          .createQueryBuilder('a')
          .where('a.roomId = :roomId', { roomId })
          .andWhere('a.grantType = :gt', { gt: RoomAccessGrant.SESSION })
          .andWhere('(a.expiresAt IS NULL OR a.expiresAt > NOW())')
          .getCount(),
      ]);
      const occupied = seated > 0 || sessions > 0 || viewerCount > 0;
      if (occupied) {
        if (room.emptySince) {
          await this.roomsRepo.update({ id: roomId }, { emptySince: null });
        }
      } else if (!room.emptySince) {
        await this.roomsRepo.update(
          { id: roomId },
          { emptySince: new Date() },
        );
      }
    } catch (err) {
      this.logger.warn(
        `syncPersonalEmptySince ${roomId}: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  @SubscribeMessage('room:event')
  async onRoomEvent(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { roomId: string; event: string; payload?: unknown },
  ) {
    if (!data?.roomId || !data?.event || !client.userId) return;
    if (!ALLOWED_ROOM_EVENTS.has(data.event)) return { error: 'Event not allowed' };
    if (!client.rooms.has(`room:${data.roomId}`)) {
      return { error: 'Join the realtime room first' };
    }

    const allowed = await this.canAccessRoom(data.roomId, client.userId);
    if (!allowed) return { error: 'Not a room member' };

    if (data.event === 'room:summon' || data.event === 'room:chat_cleared') {
      const room = await this.roomsRepo.findOne({ where: { id: data.roomId } });
      const isRoomLead =
        !!room &&
        (room.hostId === client.userId ||
          room.activeHostId === client.userId ||
          room.cohostId === client.userId);
      let isStaff = isRoomLead;
      if (!isStaff && room) {
        const mod = await this.roomModerators.findOne({
          where: { roomId: data.roomId, userId: client.userId },
        });
        isStaff =
          !!mod &&
          (mod.canManageRoom === true ||
            mod.canKick === true ||
            mod.canBan === true);
      }
      if (!isStaff) {
        return {
          error:
            data.event === 'room:chat_cleared'
              ? 'Only room staff can clear chat'
              : 'Only room host can summon',
        };
      }
    }

    const profile = client.data.roomProfiles?.[data.roomId] || {};
    const incoming =
      data.payload &&
      typeof data.payload === 'object' &&
      !Array.isArray(data.payload)
        ? (data.payload as Record<string, unknown>)
        : {};
    if (JSON.stringify(incoming).length > 16_000) {
      return { error: 'Event payload is too large' };
    }
    let payload: Record<string, unknown> = { ...incoming, userId: client.userId };
    if (data.event === 'room:summon') {
      payload = {
        userId: client.userId,
        hostId: client.userId,
        message: 'صاحب الغرفة يستدعيك — ارجع للغرفة',
      };
    } else if (data.event === 'room:chat_cleared') {
      const now = new Date();
      try {
        await this.roomsRepo.update(
          { id: data.roomId },
          { chatClearedAt: now },
        );
      } catch {
        /* ignore stamp failures — still broadcast */
      }
      payload = {
        userId: client.userId,
        displayName: profile.displayName || client.username || 'مشرف',
        chatClearedAt: now.toISOString(),
        auto: false,
        full: true,
      };
    }
    if (data.event === 'chat:message') {
      const text = String(incoming.text || '').trim().slice(0, 1000);
      if (!text) return { error: 'Message is empty' };
      const chatMute = await this.moderation.getActiveChatMute(
        data.roomId,
        client.userId!,
      );
      if (chatMute.muted) {
        const untilIso = chatMute.until ? chatMute.until.toISOString() : null;
        client.emit('moderation:blocked', {
          roomId: data.roomId,
          code: 'CHAT_MUTED',
          reason: 'أنت مكتوم من الدردشة مؤقتاً',
          action: 'mute',
          until: untilIso,
          strikes: chatMute.strikes,
        });
        return {
          error: 'أنت مكتوم من الدردشة مؤقتاً',
          code: 'CHAT_MUTED',
          action: 'mute',
          until: untilIso,
        };
      }
      const mod = await this.moderation.inspectText(text);
      if (!mod.ok) {
        const resolution = await this.moderation.resolveRoomChatStrike(
          data.roomId,
          client.userId!,
          mod.reason,
        );
        client.emit('moderation:blocked', {
          roomId: data.roomId,
          code: mod.code,
          reason: resolution.reason,
          action: resolution.action,
          strikes: resolution.strikes,
          until: resolution.muteUntil
            ? resolution.muteUntil.toISOString()
            : null,
          muteMinutes: resolution.muteMinutes,
        });
        void this.enforceChatViolation(
          data.roomId,
          client.userId!,
          resolution.reason,
          resolution.action,
          {
            muteUntil: resolution.muteUntil,
            muteMinutes: resolution.muteMinutes,
            strikes: resolution.strikes,
          },
        );
        return {
          error: resolution.reason,
          code: mod.code,
          action: resolution.action,
        };
      }
      const wealthScore = Math.max(
        0,
        Number(profile.wealthLevel || profile.wealthScore || profile.totalSentCoins || incoming.wealthScore || 0),
      );
      const charmScore = Math.max(
        0,
        Number(profile.charmLevel || profile.charmScore || incoming.charmScore || 0),
      );
      const wealthLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(
        Number(profile.totalSentCoins || incoming.totalSentCoins || wealthScore || 0),
      ));
      const charmLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(
        Number(profile.totalReceivedDiamonds || incoming.totalReceivedDiamonds || charmScore || 0),
      ));
      const roomMeta = await this.roomsRepo.findOne({
        where: { id: data.roomId },
        select: ['id', 'agencyId'],
      });
      const agencyRoom = !!roomMeta?.agencyId;
      const vipBadgeUrl =
        (typeof profile.vipBadgeUrl === 'string' && profile.vipBadgeUrl) ||
        (typeof incoming.vipBadgeUrl === 'string' && incoming.vipBadgeUrl) ||
        null;
      const hostBadgeUrl =
        (typeof profile.hostBadgeUrl === 'string' && profile.hostBadgeUrl) ||
        (typeof incoming.hostBadgeUrl === 'string' && incoming.hostBadgeUrl) ||
        null;
      // Agency rooms → host signal; personal rooms → VIP frame.
      const frameUrl =
        (agencyRoom ? hostBadgeUrl : vipBadgeUrl) ||
        (typeof incoming.frameUrl === 'string' && incoming.frameUrl) ||
        null;
      payload = {
        text,
        userId: client.userId,
        name: profile.displayName || client.username || 'User',
        displayName: profile.displayName || client.username || 'User',
        avatarUrl: profile.avatarUrl || null,
        vipLevel: Number(profile.vipLevel || 0),
        userLevel: Math.max(1, Number(profile.userLevel || 1)),
        wealthScore: wealthLevel,
        wealthLevel,
        totalSentCoins: Number(profile.totalSentCoins || incoming.totalSentCoins || 0),
        charmScore: charmLevel,
        charmLevel,
        frameUrl,
        vipBadgeUrl,
        hostBadgeUrl,
        isHost: !!profile.isHost,
      };
    } else if (data.event === 'room:like') {
      const finite = (value: unknown) => {
        const number = Number(value);
        return Number.isFinite(number)
          ? Math.max(0, Math.min(4000, number))
          : null;
      };
      payload = {
        userId: client.userId,
        x: finite(incoming.x),
        y: finite(incoming.y),
      };
    } else if (data.event === 'room:reaction') {
      const emojiKey = String(incoming.emojiKey || '').toLowerCase();
      if (!/^e(0[1-9]|[1-6]\d|7[0-4])$/.test(emojiKey)) {
        return { error: 'Invalid reaction' };
      }
      payload = { userId: client.userId, emojiKey };
    } else if (data.event === 'lucky:opened') {
      payload = {
        userId: client.userId,
        displayName: profile.displayName || client.username || 'User',
        rewardLabel: String(incoming.rewardLabel || 'جائزة').slice(0, 80),
      };
    } else if (data.event === 'room:typing') {
      payload = { userId: client.userId, isTyping: incoming.isTyping === true };
    }

    this.server.to(`room:${data.roomId}`).emit('room:event', {
      roomId: data.roomId,
      event: data.event,
      payload,
      from: { userId: client.userId, username: client.username },
      at: new Date().toISOString(),
    });

    return { ok: true };
  }

  @SubscribeMessage('chat:typing')
  async onTyping(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { conversationId: string; isTyping: boolean },
  ) {
    if (!data?.conversationId || !client.userId) return;
    if (!(await this.isConversationMember(client.userId, data.conversationId))) {
      return { error: 'Not a participant' };
    }
    client.to(`conv:${data.conversationId}`).emit('chat:typing', {
      conversationId: data.conversationId,
      userId: client.userId,
      username: client.username,
      isTyping: !!data.isTyping,
    });
  }

  @SubscribeMessage('chat:join')
  async onChatJoin(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { conversationId: string },
  ) {
    if (!data?.conversationId || !client.userId) return;
    if (!(await this.isConversationMember(client.userId, data.conversationId))) {
      return { error: 'Not a participant' };
    }
    for (const room of [...client.rooms]) {
      if (
        typeof room === 'string' &&
        room.startsWith('conv:') &&
        room !== `conv:${data.conversationId}`
      ) {
        client.leave(room);
      }
    }
    client.join(`conv:${data.conversationId}`);
    return { joined: data.conversationId };
  }

  @SubscribeMessage('chat:leave')
  onChatLeave(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { conversationId: string },
  ) {
    if (!data?.conversationId) return;
    client.leave(`conv:${data.conversationId}`);
    return { left: data.conversationId };
  }

  @SubscribeMessage('presence:check')
  async onPresenceCheck(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() data: { userId: string },
  ) {
    if (!data?.userId) return { online: false };
    if (data.userId === client.userId) {
      return { userId: data.userId, online: true };
    }
    const targetSockets = await this.server
      .in(`user:${data.userId}`)
      .fetchSockets();
    const requesterRooms = new Set(
      Object.keys(client.data.roomProfiles || {}).map((id) => `room:${id}`),
    );
    const sharesRoom = targetSockets.some((socket) =>
      [...socket.rooms].some((room) => requesterRooms.has(room)),
    );
    return {
      userId: data.userId,
      online: sharesRoom && targetSockets.length > 0,
    };
  }

  @SubscribeMessage('chat:message')
  onChatMessage() {
    return { error: 'Use REST API to send messages' };
  }

  @SubscribeMessage('presence:ping')
  onPing(@ConnectedSocket() client: AuthSocket) {
    return {
      online: true,
      userId: client.userId,
      onlineCount: this.onlineUsers.size,
    };
  }

  private async isConversationMember(userId: string, conversationId: string) {
    const row = await this.chatParticipants.findOne({
      where: { userId, conversationId },
    });
    return !!row;
  }

  private async canAccessRoom(roomId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room || room.status === RoomStatus.CLOSED) return false;
    const ban = await this.roomBans.findOne({ where: { roomId, userId } });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) return false;
    if (
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId
    ) {
      return true;
    }
    const [seated, access] = await Promise.all([
      this.roomSeats.findOne({ where: { roomId, userId } }),
      this.roomAccess.findOne({ where: { roomId, userId } }),
    ]);
    if (seated) return true;
    return this.hasActiveAccess(access);
  }

  private hasActiveAccess(access: RoomAccess | null | undefined) {
    if (!access) return false;
    if (access.grantType === RoomAccessGrant.PERMANENT) return true;
    return !access.expiresAt || access.expiresAt > new Date();
  }

  isOnline(userId: string): boolean {
    return this.onlineUsers.has(userId);
  }

  async isOnlineGlobal(userId: string): Promise<boolean> {
    if (!this.server) return this.isOnline(userId);
    const sockets = await this.server.in(`user:${userId}`).fetchSockets();
    return sockets.length > 0;
  }

  emitToUser(userId: string, event: string, payload: unknown) {
    this.server.to(`user:${userId}`).emit(event, payload);
  }

  private async enforceChatViolation(
    roomId: string,
    userId: string,
    reason: string,
    action: 'block' | 'mute' | 'kick' | 'ban',
    meta?: { muteUntil?: Date | null; muteMinutes?: number; strikes?: number },
  ) {
    if (!userId || !roomId) return;
    try {
      const room = await this.roomsRepo.findOne({ where: { id: roomId } });
      if (!room) return;
      const isRoomLead =
        room.hostId === userId ||
        room.activeHostId === userId ||
        room.cohostId === userId;

      // Always force off seat + mic mute for non-leads (even on chat mute).
      if (!isRoomLead) {
        const seat = await this.roomSeats.findOne({ where: { roomId, userId } });
        if (seat) {
          await this.roomSeats.update(
            { roomId, userId },
            {
              userId: null,
              status: SeatStatus.EMPTY,
              isMuted: false,
              isModeratorMuted: false,
            },
          );
          const user = await this.usersRepo.findOne({ where: { id: userId } });
          this.emitToRoom(roomId, 'room:event', {
            roomId,
            event: 'room:seat_left',
            payload: {
              roomId,
              userId,
              username: user?.username || null,
              displayName: user?.displayName || user?.username || null,
              forced: true,
              reason: reason || 'auto_moderation',
            },
            at: new Date().toISOString(),
          });
          this.emitToRoom(roomId, 'room:event', {
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
        }
      }

      if (action === 'mute' || action === 'block') {
        this.emitToUser(userId, 'moderation:action', {
          roomId,
          action: 'mute',
          reason: reason || 'محتوى مخالف في الدردشة',
          until: meta?.muteUntil ? meta.muteUntil.toISOString() : null,
          muteMinutes: meta?.muteMinutes || 0,
          strikes: meta?.strikes || 0,
        });
        this.emitToRoom(roomId, 'room:event', {
          roomId,
          event: 'room:chat_muted',
          payload: {
            roomId,
            userId,
            until: meta?.muteUntil ? meta.muteUntil.toISOString() : null,
            muteMinutes: meta?.muteMinutes || 0,
            strikes: meta?.strikes || 0,
            reason: reason || 'auto_moderation',
          },
          at: new Date().toISOString(),
        });
        return;
      }

      // Never auto-kick room owner / active host / cohost.
      if (isRoomLead) return;

      if (action === 'ban') {
        const banMinutes = Math.max(10, Number(meta?.muteMinutes || 30));
        const expiresAt = new Date(Date.now() + banMinutes * 60 * 1000);
        const existing = await this.roomBans.findOne({ where: { roomId, userId } });
        if (existing) {
          existing.reason = reason || existing.reason;
          existing.expiresAt = expiresAt;
          existing.bannedById = room.hostId;
          await this.roomBans.save(existing);
        } else {
          await this.roomBans.save(
            this.roomBans.create({
              roomId,
              userId,
              bannedById: room.hostId,
              reason: reason || 'auto_moderation',
              expiresAt,
            }),
          );
        }
      }

      await this.roomSeats.update(
        { roomId, userId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      await this.roomAccess.delete({ roomId, userId });

      const user = await this.usersRepo.findOne({ where: { id: userId } });
      const eventName = action === 'ban' ? 'room:banned' : 'room:kicked';
      this.emitToRoom(roomId, 'room:event', {
        roomId,
        event: eventName,
        payload: {
          roomId,
          userId,
          username: user?.username || null,
          displayName: user?.displayName || user?.username || null,
          reason: reason || 'auto_moderation',
          auto: true,
          strikes: meta?.strikes || 0,
        },
        at: new Date().toISOString(),
      });
      this.emitToUser(userId, 'moderation:action', {
        roomId,
        action,
        reason: reason || 'محتوى مخالف في الدردشة',
        strikes: meta?.strikes || 0,
      });
      await this.ejectUserFromRoom(roomId, userId);
      void this.zegoTokenService.kickUser(
        room.zegoRoomId || room.id,
        userId,
        reason || 'Auto-moderation',
      );
    } catch (err) {
      this.logger.warn(
        `Auto-moderation enforce failed: ${(err as Error).message}`,
      );
    }
  }

  async ejectUserEverywhere(userId: string, reason: string) {
    const sockets = await this.server.in(`user:${userId}`).fetchSockets();
    for (const socket of sockets) {
      socket.emit('account:restricted', { reason });
      socket.disconnect(true);
    }
  }

  emitToRoom(roomId: string, event: string, payload: unknown) {
    this.server.to(`room:${roomId}`).emit(event, payload);
  }

  /** Fan-out to every connected socket (Mikoo-style global celebration toasts). */
  emitToAll(event: string, payload: unknown) {
    this.server.emit(event, payload);
  }

  async ejectRoom(roomId: string, reason: string) {
    const channel = `room:${roomId}`;
    this.server.to(channel).emit('room:event', {
      roomId,
      event: 'room:suspended',
      payload: { roomId, reason },
      at: new Date().toISOString(),
    });
    const sockets = await this.server.in(channel).fetchSockets();
    for (const socket of sockets) {
      if (socket.data.roomProfiles) delete socket.data.roomProfiles[roomId];
      await socket.leave(channel);
    }
  }

  async ejectUserFromRoom(roomId: string, userId: string) {
    const channel = `room:${roomId}`;
    const sockets = await this.server.in(channel).fetchSockets();
    for (const socket of sockets) {
      if (socket.data.userId !== userId) continue;
      if (socket.data.roomProfiles) delete socket.data.roomProfiles[roomId];
      await socket.leave(channel);
    }
    await this.emitRoomMembers(roomId);
  }

  emitToConversation(conversationId: string, event: string, payload: unknown) {
    this.server.to(`conv:${conversationId}`).emit(event, payload);
  }

  /** True when the user has an open socket joined to this DM (chat:join). */
  async isUserInConversation(userId: string, conversationId: string): Promise<boolean> {
    if (!userId || !conversationId || !this.server) return false;
    try {
      const sockets = await this.server.in(`user:${userId}`).fetchSockets();
      const room = `conv:${conversationId}`;
      return sockets.some((s) => s.rooms.has(room));
    } catch {
      return false;
    }
  }
}
