import { Injectable, NotFoundException, UnauthorizedException, BadRequestException, ForbiddenException, ConflictException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, IsNull, Not, Repository } from 'typeorm';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import { User, UserStatus } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import {
  Room,
  RoomAccessMode,
  RoomKind,
  RoomStatus,
  RoomType,
} from '../../database/entities/room.entity';
import { RoomSeat, SeatStatus } from '../../database/entities/room-seat.entity';
import { RoomAccess } from '../../database/entities/room-access.entity';
import { Gift } from '../../database/entities/gift.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { RechargeOrder } from '../../database/entities/recharge-order.entity';
import { WithdrawRequest, WithdrawStatus } from '../../database/entities/withdraw-request.entity';
import {
  Report,
  ReportStatus,
  ReportTargetType,
} from '../../database/entities/report.entity';
import { AdminUser } from '../../database/entities/admin-user.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { AbuseLog } from '../../database/entities/abuse-log.entity';
import { WalletTransaction, TransactionType, CurrencyType } from '../../database/entities/wallet-transaction.entity';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
  AgencyRole,
} from '../../database/entities/agency-member.entity';
import {
  AgencyApplication,
  AgencyApplicationStatus,
} from '../../database/entities/agency-application.entity';
import { Notification, NotificationType } from '../../database/entities/notification.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { UserCosmetic } from '../../database/entities/user-cosmetic.entity';
import { UserGameItem } from '../../database/entities/user-game-item.entity';
import { WalletService } from '../wallet/wallet.service';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import { AgenciesService } from '../agencies/agencies.service';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { purgeRoomReferencesBeforeDelete } from '../../common/room-delete-sql';
import {
  generateActivationCode,
} from '../agencies/agency-activation';
import {
  IsString,
  IsOptional,
  IsBoolean,
  IsInt,
  IsEnum,
  IsObject,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { GiftType } from '../../database/entities/gift.entity';
import { v4 as uuidv4 } from 'uuid';

/** Reasonable monthly VIP coin prices (not explosion formula). */
export function vipPriceForLevel(level: number): number {
  const l = Math.max(1, Math.min(100, Math.floor(Number(level) || 1)));
  if (l <= 10) return [0, 299, 499, 799, 1199, 1799, 2499, 3499, 4999, 6999, 9999][l];
  if (l <= 20) return Math.round(9_999 + Math.pow(l - 10, 1.18) * 2_100);
  if (l <= 40) return Math.round(38_000 + Math.pow(l - 20, 1.24) * 5_600);
  if (l <= 60) return Math.round(150_000 + Math.pow(l - 40, 1.28) * 11_500);
  if (l <= 80) return Math.round(430_000 + Math.pow(l - 60, 1.32) * 21_000);
  return Math.round(1_000_000 + Math.pow(l - 80, 1.36) * 48_000);
}

export class AdminAdjustWalletDto {
  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  coinsDelta?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  diamondsDelta?: number;

  @ApiPropertyOptional({ description: 'Silver coins (domino / in-game silver)' })
  @IsOptional()
  @IsInt()
  silverCoinsDelta?: number;

  @ApiPropertyOptional({ description: 'Game points (activity / domino points)' })
  @IsOptional()
  @IsInt()
  gamePointsDelta?: number;

  /** Alias used by dashboard form */
  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  amount?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  note?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  reason?: string;
}

export class UpsertGiftDto {
  @ApiProperty()
  @IsString()
  name!: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  description?: string;

  @ApiProperty()
  @IsString()
  iconUrl!: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  animationUrl?: string;

  @ApiProperty()
  @IsInt()
  coinPrice!: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  diamondValue?: number;

  @ApiPropertyOptional({ enum: GiftType })
  @IsOptional()
  @IsEnum(GiftType)
  type?: GiftType;

  @ApiPropertyOptional()
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;

  @ApiPropertyOptional()
  @IsOptional()
  @IsInt()
  sortOrder?: number;

  @ApiPropertyOptional()
  @IsOptional()
  @IsObject()
  luckyConfig?: { minMultiplier: number; maxMultiplier: number; winChance: number };
}

export class UpdateUserStatusDto {
  @ApiProperty({ enum: UserStatus })
  @IsEnum(UserStatus)
  status!: UserStatus;
}

export class ReviewWithdrawDto {
  @ApiProperty({ enum: WithdrawStatus })
  @IsEnum(WithdrawStatus)
  status!: WithdrawStatus;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  adminNote?: string;
}

@Injectable()
export class AdminService {
  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(UserProfile) private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(Gift) private readonly giftsRepo: Repository<Gift>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(RechargeOrder) private readonly rechargeRepo: Repository<RechargeOrder>,
    @InjectRepository(WithdrawRequest) private readonly withdrawRepo: Repository<WithdrawRequest>,
    @InjectRepository(Report) private readonly reportsRepo: Repository<Report>,
    @InjectRepository(AdminUser) private readonly adminUsersRepo: Repository<AdminUser>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(AbuseLog) private readonly abuseRepo: Repository<AbuseLog>,
    @InjectRepository(WalletTransaction) private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(VipPlan) private readonly vipRepo: Repository<VipPlan>,
    @InjectRepository(Agency) private readonly agencyRepo: Repository<Agency>,
    @InjectRepository(AgencyMember) private readonly agencyMembersRepo: Repository<AgencyMember>,
    @InjectRepository(AgencyApplication)
    private readonly agencyApplicationsRepo: Repository<AgencyApplication>,
    @InjectRepository(RoomSeat) private readonly roomSeatsRepo: Repository<RoomSeat>,
    @InjectRepository(Notification) private readonly notificationRepo: Repository<Notification>,
    @InjectRepository(UserVip) private readonly userVipRepo: Repository<UserVip>,
    @InjectRepository(UserCosmetic) private readonly userCosmeticRepo: Repository<UserCosmetic>,
    @InjectRepository(UserGameItem) private readonly userGameItemRepo: Repository<UserGameItem>,
    private readonly walletService: WalletService,
    private readonly jwtService: JwtService,
    private readonly realtimeGateway: RealtimeGateway,
    private readonly notificationsService: NotificationsService,
    private readonly usersService: UsersService,
    private readonly agenciesService: AgenciesService,
    private readonly dataSource: DataSource,
  ) {}

  async dashboard() {
    const [
      users,
      deletedUsers,
      rooms,
      liveRooms,
      gifts,
      pendingWithdraws,
      pendingReports,
      rechargeSum,
    ] = await Promise.all([
      this.usersRepo.count({ where: { status: Not(UserStatus.DELETED) } }),
      this.usersRepo.count({ where: { status: UserStatus.DELETED } }),
      this.roomsRepo.count({ where: { status: RoomStatus.OPEN } }),
      this.roomsRepo.count({
        where: { status: RoomStatus.OPEN, activeHostId: Not(IsNull()) },
      }),
      this.giftsRepo.count({ where: { isActive: true } }),
      this.withdrawRepo.count({ where: { status: WithdrawStatus.PENDING } }),
      this.reportsRepo.count({ where: { status: ReportStatus.PENDING } }),
      this.rechargeRepo
        .createQueryBuilder('r')
        .select('COALESCE(SUM(r.amountFiat),0)', 'total')
        .where('r.status = :s', { s: 'completed' })
        .getRawOne(),
    ]);

    return {
      brand: 'JEHO CHAT',
      users,
      totalUsers: users,
      deletedUsers,
      rooms,
      openRooms: rooms,
      liveStreams: liveRooms,
      streams: liveRooms,
      activeStreams: liveRooms,
      activeGifts: gifts,
      pendingWithdraws,
      pendingReports,
      totalRechargeFiat: Number(rechargeSum?.total || 0),
      revenue: Number(rechargeSum?.total || 0),
      totalRevenue: Number(rechargeSum?.total || 0),
    };
  }

  /** Currently live voice rooms for the admin overview panel. */
  async dashboardLive() {
    const rooms = await this.roomsRepo
      .createQueryBuilder('r')
      .leftJoinAndSelect('r.host', 'host')
      .where('r.status = :open', { open: RoomStatus.OPEN })
      .andWhere('r.activeHostId IS NOT NULL')
      .orderBy('r.viewerCount', 'DESC')
      .addOrderBy('r.updatedAt', 'DESC')
      .take(20)
      .getMany();

    const needFetch = [
      ...new Set(
        rooms
          .filter((r) => r.activeHostId && r.host?.id !== r.activeHostId)
          .map((r) => r.activeHostId as string),
      ),
    ];
    const extraHosts =
      needFetch.length > 0
        ? await this.usersRepo.find({
            where: { id: In(needFetch) },
            select: ['id', 'displayName', 'username', 'avatarUrl'],
          })
        : [];
    const hostById = new Map(extraHosts.map((u) => [u.id, u]));

    return rooms.map((r) => {
      const liveHost =
        (r.activeHostId && r.host?.id === r.activeHostId ? r.host : null) ||
        (r.activeHostId ? hostById.get(r.activeHostId) : null) ||
        r.host;
      return {
        id: r.id,
        title: r.title,
        roomName: r.title,
        hostName: liveHost?.displayName || liveHost?.username || '—',
        userName: liveHost?.displayName || liveHost?.username || '—',
        viewers: Number(r.viewerCount || 0),
        viewerCount: Number(r.viewerCount || 0),
        agencyId: r.agencyId,
        roomKind: r.roomKind,
        coverUrl: r.coverUrl,
        status: 'live',
      };
    });
  }

  async listUsers(query: PaginationDto) {
    const qb = this.usersRepo
      .createQueryBuilder('u')
      .leftJoinAndSelect('u.wallet', 'wallet')
      .leftJoinAndSelect('u.profile', 'profile')
      .orderBy('u.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    if (query.search?.trim()) {
      const raw = query.search.trim();
      const s = `%${raw}%`;
      // publicId is VARCHAR — never bind as Number (PG: varchar = int fails).
      qb.andWhere(
        '(u.displayName ILIKE :s OR u.username ILIKE :s OR u.email ILIKE :s OR CAST(u.id AS text) ILIKE :s OR u.publicId = :publicIdExact OR CAST(u.publicId AS text) ILIKE :s)',
        { s, publicIdExact: raw },
      );
    }
    const status = (query.status || '').toLowerCase();
    if (status === 'banned') {
      qb.andWhere('u.status = :st', { st: UserStatus.BANNED });
    } else if (status === 'active') {
      qb.andWhere('u.status = :st', { st: UserStatus.ACTIVE });
    } else if (status === 'deleted') {
      qb.andWhere('u.status = :st', { st: UserStatus.DELETED });
    } else {
      // Default list hides soft-deleted accounts.
      qb.andWhere('u.status != :deleted', { deleted: UserStatus.DELETED });
    }

    const [items, total] = await qb.getManyAndCount();
    const mapped = items.map((u) => this.mapAdminUser(u));
    return paginate(mapped, total, query.page || 1, query.limit || 20);
  }

  private mapAdminUser(user: User & { wallet?: Wallet; profile?: { country?: string | null; totalSentCoins?: number; totalReceivedDiamonds?: number } }) {
    const wallet = user.wallet;
    return {
      ...user,
      name: user.displayName,
      country: user.profile?.country ?? null,
      coins: Number(wallet?.coins ?? 0),
      diamonds: Number(wallet?.diamonds ?? 0),
      silverCoins: 0,
      gamePoints: 0,
      level: Number(user.level ?? 1),
      experience: Number(user.experience ?? 0),
      totalSentCoins: Number(user.profile?.totalSentCoins ?? 0),
      totalReceivedDiamonds: Number(user.profile?.totalReceivedDiamonds ?? 0),
      genderVerified: !!user.genderVerified,
      isBanned: user.status === UserStatus.BANNED,
      isDeleted: user.status === UserStatus.DELETED,
    };
  }

  async updateUserStatus(userId: string, dto: UpdateUserStatusDto) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    user.status = dto.status;
    await this.usersRepo.save(user);
    await this.abuseRepo.save(
      this.abuseRepo.create({
        userId,
        action: `status_${dto.status}`,
        severity: dto.status === UserStatus.BANNED ? ('high' as any) : ('medium' as any),
        details: `Admin set status to ${dto.status}`,
      }),
    );
    if (dto.status !== UserStatus.ACTIVE) {
      await this.realtimeGateway.ejectUserEverywhere(
        userId,
        `account_${dto.status}`,
      );
    }
    return user;
  }

  async deleteUser(userId: string, actorId?: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    if (actorId && actorId === userId) {
      throw new BadRequestException('Cannot delete your own account');
    }

    // Preserve agency history, but suspend owned agencies and eject room occupants.
    const ownedAgencies = await this.agencyRepo.find({ where: { ownerId: userId } });
    for (const agency of ownedAgencies) {
      await this.suspendAgency(agency.id, 'owner_deleted');
    }

    user.status = UserStatus.DELETED;
    user.isAdmin = false;
    user.refreshTokenHash = null;
    await this.usersRepo.save(user);
    await this.realtimeGateway.ejectUserEverywhere(userId, 'account_deleted');
    await this.abuseRepo.save(
      this.abuseRepo.create({
        userId,
        action: 'status_deleted',
        severity: 'high' as any,
        details: 'Admin soft-deleted user account',
      }),
    );
    return { deleted: true, id: userId, agenciesDeleted: ownedAgencies.length };
  }

  /**
   * Hard wipe to brand-new baseline: wallet, VIP, kenars/frames/entries, level.
   * Keeps identity (name/avatar/genderVerified). Uses SQL updates so bigint
   * wallet columns and stale ORM entities cannot leave residual balances.
   */
  async resetUserToBaseline(userId: string, actorId?: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    if (user.isAdmin) {
      throw new BadRequestException('Cannot reset an admin account');
    }

    const result = await this.dataSource.transaction(async (manager) => {
      await manager.update(User, { id: userId }, { level: 1, experience: 0 });

      const wallet = await manager.findOne(Wallet, { where: { userId } });
      if (wallet) {
        await manager
          .createQueryBuilder()
          .update(Wallet)
          .set({
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            silverCoins: 0,
            gamePoints: 0,
            totalRecharged: 0,
            totalWithdrawn: 0,
          })
          .where('userId = :userId', { userId })
          .execute();
      } else {
        await manager.save(
          manager.create(Wallet, {
            userId,
            coins: 0,
            diamonds: 0,
            traderDiamonds: 0,
            silverCoins: 0,
            gamePoints: 0,
            totalRecharged: 0,
            totalWithdrawn: 0,
          }),
        );
      }

      // Wipe VIP rows completely (not only isActive=false).
      const vipDeleted = await manager.delete(UserVip, { userId });

      let profile = await manager.findOne(UserProfile, { where: { userId } });
      if (!profile) {
        profile = manager.create(UserProfile, { userId });
      }
      profile.entryEffectUrl = null;
      profile.entryAnimationUrl = null;
      profile.roomCardUrl = null;
      profile.vipBadgeUrl = null;
      profile.levelBadgeUrl = null;
      profile.hostBadgeUrl = null;
      profile.nameColor = null;
      profile.totalSentCoins = 0;
      profile.totalReceivedDiamonds = 0;
      await manager.save(profile);

      const cosmeticsDeleted = await manager.delete(UserCosmetic, { userId });
      const gameItemsDeleted = await manager.delete(UserGameItem, { userId });

      await manager.save(
        manager.create(AbuseLog, {
          userId,
          action: 'admin_reset_baseline',
          severity: 'high' as any,
          details: `Admin ${actorId || 'unknown'} hard-reset user to baseline`,
        }),
      );

      return {
        cosmeticsRemoved: cosmeticsDeleted?.affected ?? 0,
        gameItemsRemoved: gameItemsDeleted?.affected ?? 0,
        vipRemoved: vipDeleted?.affected ?? 0,
      };
    });

    try {
      this.realtimeGateway.emitToUser(userId, 'account:reset', {
        reason: 'admin_reset_baseline',
        at: new Date().toISOString(),
      });
    } catch {
      // ignore realtime failures
    }

    return {
      reset: true,
      id: userId,
      ...result,
      user: await this.getUser(userId),
    };
  }

  /**
   * QA helper: fan-out Mikoo-style global celebration toasts (lucky + game).
   * Looks up displayName/username (default علوش) for avatar when possible.
   */
  async emitTestCelebrations(opts?: { name?: string; kinds?: Array<'lucky' | 'game'> }) {
    const nameHint = (opts?.name || 'علوش').trim();
    const kinds = (opts?.kinds?.length ? opts.kinds : ['lucky', 'game']) as Array<'lucky' | 'game'>;
    const user = await this.usersRepo
      .createQueryBuilder('u')
      .where('u.displayName ILIKE :n OR u.username ILIKE :n', { n: `%${nameHint}%` })
      .andWhere('u.status != :deleted', { deleted: UserStatus.DELETED })
      .orderBy('u.updatedAt', 'DESC')
      .getOne();
    const displayName = user?.displayName || user?.username || nameHint || 'علوش';
    const avatarUrl = user?.avatarUrl || '';
    const userId = user?.id || 'test-user';
    const at = new Date().toISOString();
    const emitted: string[] = [];

    if (kinds.includes('lucky')) {
      this.realtimeGateway.emitToAll('celebration:toast', {
        kind: 'lucky_hit',
        id: `lucky:test:${userId}:${Date.now()}`,
        userId,
        displayName,
        avatarUrl,
        giftName: 'Lucky Box',
        giftIconUrl: '',
        multiplier: 10,
        coinsWon: 5000,
        stake: 500,
        roomId: null,
        title: 'حظ سعيد! 🎉',
        body: `مبروك ${displayName} · محظوظ ×10 · +5000`,
        at,
      });
      emitted.push('lucky');
    }

    if (kinds.includes('game')) {
      // slight delay so client shows both banners in sequence
      await new Promise((r) => setTimeout(r, 2500));
      this.realtimeGateway.emitToAll('celebration:toast', {
        kind: 'game_win',
        id: `game:test:${userId}:${Date.now()}`,
        userId,
        displayName,
        avatarUrl,
        gameId: 'solo',
        gameTitle: 'Solo 77',
        gameIconUrl: '',
        gameCoverUrl: '',
        coinsWon: 777,
        roomId: null,
        title: 'مبروك! 🎊',
        body: `مبروك ${displayName} حصل على 777 · Solo 77`,
        at: new Date().toISOString(),
      });
      emitted.push('game');
    }

    return {
      ok: true,
      displayName,
      userId: user?.id || null,
      found: !!user,
      emitted,
      socketClients: (this.realtimeGateway as any)?.server?.engine?.clientsCount ?? null,
    };
  }

  /** Reset every non-admin, non-deleted account to baseline (test wipe). */
  async resetAllUsersToBaseline(actorId?: string) {
    const users = await this.usersRepo.find({
      where: { isAdmin: false },
      select: ['id', 'status'],
    });
    let resetCount = 0;
    let skipped = 0;
    for (const row of users) {
      if (row.status === UserStatus.DELETED) {
        skipped += 1;
        continue;
      }
      await this.resetUserToBaseline(row.id, actorId);
      resetCount += 1;
    }
    return { reset: true, resetCount, skipped };
  }

  async listRooms(query: PaginationDto) {
    const qb = this.roomsRepo
      .createQueryBuilder('r')
      .leftJoinAndSelect('r.host', 'host')
      .orderBy('r.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    const status = (query.status || '').toLowerCase();
    if (status === 'active' || status === 'open') {
      qb.andWhere('r.status = :st', { st: RoomStatus.OPEN });
    } else if (status === 'locked') {
      qb.andWhere('r.status = :st', { st: RoomStatus.LOCKED });
    } else if (status === 'closed') {
      qb.andWhere('r.status = :st', { st: RoomStatus.CLOSED });
    } else if (status === 'all') {
      // no status filter
    } else {
      // Default: hide closed rooms so dashboard stays clean
      qb.andWhere('r.status != :closed', { closed: RoomStatus.CLOSED });
    }

    if (query.search?.trim()) {
      const raw = query.search.trim();
      const s = `%${raw}%`;
      qb.andWhere(
        '(r.title ILIKE :s OR CAST(r.id AS text) ILIKE :s OR host.displayName ILIKE :s OR host.username ILIKE :s OR host.publicId = :publicIdExact OR CAST(host.publicId AS text) ILIKE :s)',
        { s, publicIdExact: raw },
      );
    }

    const [items, total] = await qb.getManyAndCount();
    const mapped = items.map((r) => ({
      ...r,
      name: r.title,
      ownerName: r.host?.displayName || r.host?.username || '—',
      hostName: r.host?.displayName || r.host?.username || '—',
      hostAvatarUrl: r.host?.avatarUrl || null,
      membersCount: r.viewerCount ?? 0,
    }));
    return paginate(mapped, total, query.page || 1, query.limit || 20);
  }

  /** Live / ended voice rooms for the Streams dashboard page. */
  async listStreams(query: PaginationDto) {
    const qb = this.roomsRepo
      .createQueryBuilder('r')
      .leftJoinAndSelect('r.host', 'host')
      .orderBy('r.viewerCount', 'DESC')
      .addOrderBy('r.updatedAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    const status = (query.status || '').toLowerCase();
    if (status === 'ended' || status === 'closed') {
      qb.andWhere('r.status = :st', { st: RoomStatus.CLOSED });
    } else if (status === 'all') {
      // no filter
    } else {
      // Default + "live": only rooms with an active host session
      qb.andWhere('r.status = :open', { open: RoomStatus.OPEN });
      qb.andWhere('r.activeHostId IS NOT NULL');
    }

    if (query.search?.trim()) {
      const s = `%${query.search.trim()}%`;
      qb.andWhere(
        '(r.title ILIKE :s OR CAST(r.id AS text) ILIKE :s OR host.displayName ILIKE :s OR host.username ILIKE :s)',
        { s },
      );
    }

    const [items, total] = await qb.getManyAndCount();
    const mapped = items.map((r) => this.mapStream(r));
    return paginate(mapped, total, query.page || 1, query.limit || 20);
  }

  async getStream(id: string) {
    const room = await this.roomsRepo.findOne({
      where: { id },
      relations: ['host'],
    });
    if (!room) throw new NotFoundException('Stream not found');
    return this.mapStream(room);
  }

  /** End a live session (works for personal + agency rooms). */
  async forceEndStream(roomId: string, reason = 'admin_force_end') {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Stream not found');

    await this.txRepo.manager.transaction(async (manager) => {
      await manager.update(
        RoomSeat,
        { roomId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      room.status = RoomStatus.CLOSED;
      room.activeHostId = null;
      room.viewerCount = 0;
      await manager.save(Room, room);
    });
    await this.realtimeGateway.ejectRoom(roomId, reason);
    return { ended: true, id: roomId, reason };
  }

  private mapStream(r: Room & { host?: User }) {
    const isLive = r.status === RoomStatus.OPEN && !!r.activeHostId;
    return {
      id: r.id,
      title: r.title,
      roomName: r.title,
      hostName: r.host?.displayName || r.host?.username || '—',
      hostAvatarUrl: r.host?.avatarUrl || null,
      host: r.host
        ? {
            id: r.host.id,
            displayName: r.host.displayName,
            username: r.host.username,
            avatarUrl: r.host.avatarUrl,
          }
        : null,
      viewers: Number(r.viewerCount || 0),
      viewerCount: Number(r.viewerCount || 0),
      status: isLive ? 'live' : 'ended',
      isLive,
      coverUrl: r.coverUrl,
      agencyId: r.agencyId,
      roomKind: r.roomKind,
      startedAt: r.updatedAt || r.createdAt,
      createdAt: r.createdAt,
    };
  }

  async closeRoom(roomId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (room.agencyId || room.roomKind === RoomKind.AGENCY || room.isPersistent) {
      throw new ForbiddenException('Suspend the agency instead of closing its persistent room');
    }

    await this.txRepo.manager.transaction(async (manager) => {
      await manager.update(
        RoomSeat,
        { roomId },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      await manager.delete(RoomAccess, { roomId });
      room.status = RoomStatus.CLOSED;
      room.activeHostId = null;
      room.viewerCount = 0;
      await manager.save(Room, room);
    });
    await this.realtimeGateway.ejectRoom(roomId, 'admin_closed');
    return { closed: true, deleted: false, id: roomId };
  }

  /**
   * Fully remove room after close. Gift/wallet settlements remain in transactions;
   * gift_sends keep history but roomId is nulled.
   */
  async deleteRoom(roomId: string, opts: { allowAgency?: boolean } = {}) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const isAgencyRoom = room.isPersistent || room.roomKind === 'agency' || !!room.agencyId;
    if (isAgencyRoom) {
      throw new ForbiddenException(
        'Persistent agency rooms cannot be deleted; suspend the agency instead.',
      );
    }
    await this.txRepo.manager.query(
      `UPDATE gift_sends SET "roomId" = NULL WHERE "roomId" = $1`,
      [roomId],
    );
    await this.txRepo.manager.query(
      `DELETE FROM room_access WHERE "roomId" = $1`,
      [roomId],
    ).catch(() => undefined);
    await this.txRepo.manager.query(
      `UPDATE reports
          SET description = ('deletedRoomTitle=' || $2::text || E'\n' || COALESCE(description, '')),
              "roomId" = NULL
        WHERE "roomId" = $1::uuid`,
      [roomId, String(room.title || roomId)],
    );
    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:closed',
      payload: { roomId, reason: 'admin_deleted' },
      at: new Date().toISOString(),
    });
    await this.roomsRepo.delete(roomId);
    return { deleted: true, id: roomId };
  }

  async deleteAgency(id: string) {
    const agency = await this.agencyRepo.findOne({ where: { id } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    const rooms = await this.roomsRepo.find({ where: { agencyId: id } });
    const roomIds = rooms.map((room) => room.id);

    // End live sessions first so seats/viewers are cleared before hard delete.
    for (const room of rooms) {
      try {
        await this.forceEndStream(room.id, 'agency_deleted');
      } catch {
        /* already closed */
      }
    }

    await this.txRepo.manager.transaction(async (manager) => {
      for (const roomId of roomIds) {
        const deletedRoom = rooms.find((room) => room.id === roomId);
        const roomTitle = String(deletedRoom?.title || roomId);
        await purgeRoomReferencesBeforeDelete(manager, roomId, roomTitle);
      }

      await manager.delete(AgencyApplication, { agencyId: id });
      await manager.query(`UPDATE contests SET "agencyId" = NULL WHERE "agencyId" = $1`, [id]);
      await manager.query(
        `UPDATE lucky_box_opens SET "agencyId" = NULL WHERE "agencyId" = $1`,
        [id],
      );
      if (roomIds.length) {
        await manager.delete(Room, roomIds);
      }
      await manager.delete(AgencyMember, { agencyId: id });
      await manager.delete(Agency, id);
    });

    for (const roomId of roomIds) {
      await this.realtimeGateway.ejectRoom(roomId, 'agency_deleted');
    }
    return { deleted: true, id, deletedRoomIds: roomIds };
  }

  async updateRoom(
    roomId: string,
    dto: { accessMode?: string; entryFeeCoins?: number; title?: string },
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (dto.title !== undefined) room.title = dto.title;
    if (room.agencyId || room.roomKind === RoomKind.AGENCY || room.isPersistent) {
      room.status = RoomStatus.OPEN;
      room.isPublic = true;
      room.hasPassword = false;
      room.passwordHash = null;
      room.accessMode = RoomAccessMode.FREE;
      room.entryFeeCoins = 0;
    } else {
      if (dto.accessMode !== undefined) room.accessMode = dto.accessMode as any;
      if (dto.entryFeeCoins !== undefined) {
        room.entryFeeCoins = Math.max(0, Number(dto.entryFeeCoins || 0));
      }
    }
    await this.roomsRepo.save(room);
    return room;
  }

  reconcileFriendsCounts() {
    return this.usersService.reconcileFriendsCounts();
  }

  async listGifts() {
    return this.giftsRepo.find({ order: { sortOrder: 'ASC' } });
  }

  async activateAllGifts() {
    const result = await this.giftsRepo
      .createQueryBuilder()
      .update(Gift)
      .set({ isActive: true })
      .where('"isActive" = false')
      .execute();
    const total = await this.giftsRepo.count();
    const active = await this.giftsRepo.count({ where: { isActive: true } });
    return {
      ok: true,
      activated: result.affected || 0,
      total,
      active,
    };
  }

  async upsertGift(id: string | null, dto: UpsertGiftDto) {
    let gift = id ? await this.giftsRepo.findOne({ where: { id } }) : null;
    if (id && !gift) throw new NotFoundException('Gift not found');
    if (!gift) {
      gift = this.giftsRepo.create({
        name: dto.name,
        iconUrl: dto.iconUrl,
        coinPrice: dto.coinPrice,
      });
    }
    Object.assign(gift, {
      name: dto.name,
      description: dto.description ?? gift.description,
      iconUrl: dto.iconUrl,
      animationUrl: dto.animationUrl ?? gift.animationUrl,
      coinPrice: dto.coinPrice,
      diamondValue: dto.diamondValue ?? gift.diamondValue ?? Math.floor(dto.coinPrice * 0.5),
      type: dto.type ?? gift.type,
      isActive: dto.isActive ?? gift.isActive ?? true,
      sortOrder: dto.sortOrder ?? gift.sortOrder ?? 0,
      luckyConfig:
        (dto.type ?? gift.type) === GiftType.LUCKY
          ? (dto.luckyConfig ??
            gift.luckyConfig ?? {
              minMultiplier: 1,
              maxMultiplier: 5,
              winChance: 0.1,
            })
          : null,
    });
    return this.giftsRepo.save(gift);
  }

  async adjustWallet(userId: string, dto: AdminAdjustWalletDto) {
    const coins =
      dto.coinsDelta != null
        ? Number(dto.coinsDelta)
        : dto.amount != null
          ? Number(dto.amount)
          : 0;
    const diamonds = dto.diamondsDelta != null ? Number(dto.diamondsDelta) : 0;
    const silverCoins = dto.silverCoinsDelta != null ? Number(dto.silverCoinsDelta) : 0;
    const gamePoints = dto.gamePointsDelta != null ? Number(dto.gamePointsDelta) : 0;
    return this.walletService.adminAdjust(
      userId,
      coins,
      diamonds,
      dto.note || dto.reason || 'Admin adjustment',
      silverCoins,
      gamePoints,
    );
  }

  async listWithdraws(query: PaginationDto) {
    const [items, total] = await this.withdrawRepo.findAndCount({
      relations: ['user'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(
      items.map((w) => ({
        ...w,
        userName: w.user?.displayName || w.user?.username || '—',
        amount: w.diamonds,
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async reviewWithdraw(id: string, adminId: string, dto: ReviewWithdrawDto) {
    return this.dataSource.transaction(async (manager) => {
      const req = await manager.findOne(WithdrawRequest, {
        where: { id },
        lock: { mode: 'pessimistic_write' },
      });
      if (!req) throw new NotFoundException('Withdraw not found');
      if (
        req.status === WithdrawStatus.PAID ||
        req.status === WithdrawStatus.REJECTED
      ) {
        throw new BadRequestException('طلب السحب تمت معالجته مسبقاً');
      }

      const next =
        dto.status === WithdrawStatus.APPROVED ? WithdrawStatus.PAID : dto.status;
      if (next !== WithdrawStatus.PAID && next !== WithdrawStatus.REJECTED) {
        throw new BadRequestException('حالة السحب غير صالحة');
      }

      req.status = next;
      req.reviewedById = adminId;
      req.adminNote = dto.adminNote || null;
      const saved = await manager.save(req);

      if (next === WithdrawStatus.PAID) {
        await manager.increment(
          Wallet,
          { userId: req.userId },
          'totalWithdrawn',
          Number(req.diamonds),
        );
      }

      if (next === WithdrawStatus.REJECTED) {
        const refundRef = `withdraw_refund:${req.id}`;
        const alreadyRefunded = await manager.findOne(WalletTransaction, {
          where: {
            userId: req.userId,
            referenceType: 'withdraw_refund',
            referenceId: refundRef,
          },
          lock: { mode: 'pessimistic_write' },
        });
        if (!alreadyRefunded) {
          const wallet = await manager.findOne(Wallet, {
            where: { userId: req.userId },
            lock: { mode: 'pessimistic_write' },
          });
          if (wallet) {
            const amount = Number(req.diamonds);
            wallet.diamonds = Number(wallet.diamonds || 0) + amount;
            await manager.save(wallet);
            await manager.save(
              manager.create(WalletTransaction, {
                userId: req.userId,
                type: TransactionType.ADMIN_ADJUST,
                currency: CurrencyType.DIAMONDS,
                amount,
                balanceAfter: Number(wallet.diamonds),
                referenceType: 'withdraw_refund',
                referenceId: refundRef,
                description: `Withdraw rejected refund ${req.id}`,
              }),
            );
          }
        }
      }

      return saved;
    });
  }

  async listReports(query: PaginationDto) {
    const qb = this.reportsRepo
      .createQueryBuilder('r')
      .leftJoinAndSelect('r.reporter', 'reporter')
      .orderBy('r.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    let status = (query.status || '').toLowerCase();
    if (status === 'dismissed') status = ReportStatus.REJECTED;
    if (status && Object.values(ReportStatus).includes(status as ReportStatus)) {
      qb.andWhere('r.status = :status', { status });
    }

    const type = (query.type || '').toLowerCase();
    if (type) qb.andWhere('r.targetType = :type', { type });

    const [items, total] = await qb.getManyAndCount();
    const targetUserIds = items
      .filter((report) => report.targetType === 'user')
      .map((report) => report.targetId);
    const roomIds = items.map((report) => report.roomId).filter(Boolean) as string[];
    const [targetUsers, reportRooms] = await Promise.all([
      targetUserIds.length
        ? this.usersRepo.find({ where: { id: In(targetUserIds) } })
        : [],
      roomIds.length ? this.roomsRepo.find({ where: { id: In(roomIds) } }) : [],
    ]);
    const targetUsersById = new Map<string, User>(
      targetUsers.map((user): [string, User] => [user.id, user]),
    );
    const roomsById = new Map<string, Room>(
      reportRooms.map((room): [string, Room] => [room.id, room]),
    );
    return paginate(
      items.map((r) => {
        const targetUser = targetUsersById.get(r.targetId);
        const reportRoom = r.roomId ? roomsById.get(r.roomId) : null;
        return {
          ...r,
          type: r.targetType,
          reporterName: r.reporter?.displayName || r.reporter?.username || '—',
          targetName: targetUser?.displayName || targetUser?.username || r.targetId,
          targetAvatarUrl: targetUser?.avatarUrl || null,
          roomTitle: reportRoom?.title || null,
          status: r.status === ReportStatus.REJECTED ? 'dismissed' : r.status,
        };
      }),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async getReport(id: string) {
    const report = await this.reportsRepo.findOne({
      where: { id },
      relations: { reporter: true },
    });
    if (!report) throw new NotFoundException('Report not found');
    const [targetUser, room, handler] = await Promise.all([
      report.targetType === ReportTargetType.USER
        ? this.usersRepo.findOne({ where: { id: report.targetId } })
        : null,
      report.roomId ? this.roomsRepo.findOne({ where: { id: report.roomId } }) : null,
      report.handledById
        ? this.usersRepo.findOne({ where: { id: report.handledById } })
        : null,
    ]);
    return {
      ...report,
      type: report.targetType,
      reporterName:
        report.reporter?.displayName || report.reporter?.username || report.reporterId,
      targetName: targetUser?.displayName || targetUser?.username || report.targetId,
      targetAvatarUrl: targetUser?.avatarUrl || null,
      roomTitle: room?.title || null,
      handledByName: handler?.displayName || handler?.username || null,
      status: report.status === ReportStatus.REJECTED ? 'dismissed' : report.status,
    };
  }

  async resolveReport(
    id: string,
    status: ReportStatus,
    adminNote?: string,
    adminId?: string,
  ) {
    const report = await this.reportsRepo.findOne({ where: { id } });
    if (!report) throw new NotFoundException('Report not found');
    report.status = status;
    report.adminNote = adminNote || null;
    report.handledById = adminId || null;
    return this.reportsRepo.save(report);
  }

  async getSettings() {
    return this.settingsRepo.find({ order: { key: 'ASC' } });
  }

  async setSetting(key: string, value: string, description?: string) {
    let nextValue = value;
    if (key === 'app_games') {
      try {
        const { sanitizeGamesCatalog } = await import('../games/mikoo-games.catalog');
        const parsed = JSON.parse(value);
        nextValue = JSON.stringify(sanitizeGamesCatalog(parsed));
      } catch {
        // keep original value if JSON is invalid
      }
    }
    let setting = await this.settingsRepo.findOne({ where: { key } });
    if (!setting) {
      setting = this.settingsRepo.create({ key, value: nextValue, description: description || null });
    } else {
      setting.value = nextValue;
      if (description) setting.description = description;
    }
    return this.settingsRepo.save(setting);
  }

  async adminLogin(email: string, password: string) {
    const user = await this.usersRepo
      .createQueryBuilder('u')
      .addSelect('u.passwordHash')
      .where('LOWER(u.email) = :email', { email: email.toLowerCase() })
      .getOne();
    if (!user || !user.isAdmin || !user.passwordHash) {
      throw new UnauthorizedException('Invalid admin credentials');
    }
    const ok = await bcrypt.compare(password, user.passwordHash);
    if (!ok) throw new UnauthorizedException('Invalid admin credentials');

    const accessToken = await this.jwtService.signAsync({
      sub: user.id,
      username: user.username,
      isAdmin: true,
      role: 'admin',
    });
    return {
      accessToken,
      token: accessToken,
      tokenType: 'Bearer',
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
        displayName: user.displayName,
        name: user.displayName,
        isAdmin: true,
      },
    };
  }

  async adminMe(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user || !user.isAdmin) throw new UnauthorizedException('Not an admin');
    return {
      id: user.id,
      email: user.email,
      username: user.username,
      displayName: user.displayName,
      name: user.displayName,
      isAdmin: true,
    };
  }

  async charts() {
    const days = 7;
    const labels: string[] = [];
    const usersSeries: number[] = [];
    const roomsSeries: number[] = [];
    for (let i = days - 1; i >= 0; i--) {
      const day = new Date();
      day.setHours(0, 0, 0, 0);
      day.setDate(day.getDate() - i);
      const next = new Date(day);
      next.setDate(next.getDate() + 1);
      labels.push(day.toISOString().slice(5, 10));
      usersSeries.push(
        await this.usersRepo
          .createQueryBuilder('u')
          .where('u.createdAt >= :day AND u.createdAt < :next', { day, next })
          .getCount(),
      );
      roomsSeries.push(
        await this.roomsRepo
          .createQueryBuilder('r')
          .where('r.createdAt >= :day AND r.createdAt < :next', { day, next })
          .getCount(),
      );
    }
    const rechargeCoins = await this.txRepo
      .createQueryBuilder('t')
      .select('COALESCE(SUM(ABS(t.amount)), 0)', 'total')
      .where('t.type = :type', { type: TransactionType.RECHARGE })
      .andWhere('t.currency = :cur', { cur: CurrencyType.COINS })
      .getRawOne();
    const giftCoins = await this.txRepo
      .createQueryBuilder('t')
      .select('COALESCE(SUM(ABS(t.amount)), 0)', 'total')
      .where('t.type = :type', { type: TransactionType.GIFT_SEND })
      .andWhere('t.currency = :cur', { cur: CurrencyType.COINS })
      .getRawOne();
    const vipCoins = await this.txRepo
      .createQueryBuilder('t')
      .select('COALESCE(SUM(ABS(t.amount)), 0)', 'total')
      .where('t.referenceType = :ref', { ref: 'vip_purchase' })
      .getRawOne();
    const rechargeVal = Math.max(0, Number(rechargeCoins?.total || 0));
    const giftsVal = Math.max(0, Number(giftCoins?.total || 0));
    const vipVal = Math.max(0, Number(vipCoins?.total || 0));

    return {
      labels,
      users: {
        labels,
        users: usersSeries,
        data: usersSeries,
      },
      revenue: {
        labels: ['Recharge', 'Gifts', 'VIP'],
        values: [rechargeVal, giftsVal, vipVal],
        data: [rechargeVal, giftsVal, vipVal],
      },
      rooms: {
        labels,
        data: roomsSeries,
      },
    };
  }

  async getUser(id: string) {
    const user = await this.usersRepo.findOne({
      where: { id },
      relations: ['wallet', 'profile'],
    });
    if (!user) throw new NotFoundException('User not found');
    const mapped = this.mapAdminUser(user) as Record<string, unknown>;
    const vip = await this.userVipRepo.findOne({
      where: { userId: id, isActive: true },
      order: { expiresAt: 'DESC' },
    });
    mapped.vipLevel =
      vip && vip.expiresAt && vip.expiresAt > new Date() ? Number(vip.level || 0) : 0;
    return mapped;
  }

  async patchUser(id: string, body: Record<string, unknown>) {
    const user = await this.usersRepo.findOne({
      where: { id },
      relations: ['profile', 'wallet'],
    });
    if (!user) throw new NotFoundException('User not found');

    const displayName =
      typeof body.displayName === 'string'
        ? body.displayName
        : typeof body.name === 'string'
          ? body.name
          : null;
    if (displayName != null) user.displayName = displayName.trim();

    if (typeof body.email === 'string') user.email = body.email.trim() || null;
    if (typeof body.phone === 'string') user.phone = body.phone.trim() || null;
    if (typeof body.username === 'string' && body.username.trim()) {
      user.username = body.username.trim();
    }
    if (typeof body.gender === 'string' && body.gender.trim()) {
      user.gender = body.gender.trim().toLowerCase() as any;
    }
    if (typeof body.status === 'string') user.status = body.status as UserStatus;
    if (typeof body.isAdmin === 'boolean') user.isAdmin = body.isAdmin;
    if (typeof body.genderVerified === 'boolean') user.genderVerified = body.genderVerified;

    if (body.level != null && Number.isFinite(Number(body.level))) {
      user.level = Math.max(1, Math.min(9999, Math.floor(Number(body.level))));
    }
    if (body.experience != null && Number.isFinite(Number(body.experience))) {
      user.experience = Math.max(0, Math.floor(Number(body.experience)));
    }

    let profile = user.profile;
    if (!profile) {
      profile = this.profilesRepo.create({ userId: id });
    }
    const country = typeof body.country === 'string' ? body.country.trim() : null;
    if (country != null) profile.country = country || null;
    if (body.totalSentCoins != null && Number.isFinite(Number(body.totalSentCoins))) {
      profile.totalSentCoins = Math.max(0, Math.floor(Number(body.totalSentCoins)));
    }
    if (body.totalReceivedDiamonds != null && Number.isFinite(Number(body.totalReceivedDiamonds))) {
      profile.totalReceivedDiamonds = Math.max(0, Math.floor(Number(body.totalReceivedDiamonds)));
    }
    if (
      country != null ||
      body.totalSentCoins != null ||
      body.totalReceivedDiamonds != null
    ) {
      await this.profilesRepo.save(profile);
      user.profile = profile;
    }

    // Absolute wallet set (optional) — use deltas for adjustments; these overwrite balances.
    if (user.wallet) {
      let walletTouched = false;
      if (body.coins != null && Number.isFinite(Number(body.coins))) {
        user.wallet.coins = Math.max(0, Math.floor(Number(body.coins)));
        walletTouched = true;
      }
      if (body.diamonds != null && Number.isFinite(Number(body.diamonds))) {
        user.wallet.diamonds = Math.max(0, Math.floor(Number(body.diamonds)));
        walletTouched = true;
      }
      if (body.gamePoints != null && Number.isFinite(Number(body.gamePoints))) {
        const target = Math.max(0, Math.floor(Number(body.gamePoints)));
        if (target > 0) {
          user.wallet.coins = Number(user.wallet.coins || 0) + target;
        }
        user.wallet.gamePoints = 0;
        walletTouched = true;
      }
      if (walletTouched) await this.walletsRepo.save(user.wallet);
    }

    // VIP level shortcut: assign by numeric level if vipLevel provided.
    if (body.vipLevel != null && Number.isFinite(Number(body.vipLevel))) {
      const vipLevel = Math.max(0, Math.floor(Number(body.vipLevel)));
      if (vipLevel <= 0) {
        await this.revokeActiveVip(id);
      } else {
        await this.assignVipByLevel(id, vipLevel);
      }
    }

    await this.usersRepo.save(user);
    if (user.status !== UserStatus.ACTIVE) {
      await this.realtimeGateway.ejectUserEverywhere(
        id,
        `account_${user.status}`,
      );
    }
    const refreshed = await this.usersRepo.findOne({
      where: { id },
      relations: ['profile', 'wallet'],
    });
    return this.mapAdminUser(refreshed!);
  }

  async deleteGift(id: string) {
    const gift = await this.giftsRepo.findOne({ where: { id } });
    if (!gift) throw new NotFoundException('Gift not found');
    // CASCADE on gift_sends removes history rows; hard-delete so admin UI clears.
    await this.giftsRepo.delete(id);
    return { deleted: true, id };
  }

  async listTransactions(query: PaginationDto) {
    const [items, total] = await this.txRepo.findAndCount({
      relations: ['user'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(
      items.map((t) => ({
        ...t,
        userName: t.user?.displayName || t.user?.username || '—',
        coins: Number(t.amount),
        status: 'completed',
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async listRecharges(query: PaginationDto) {
    const [items, total] = await this.rechargeRepo.findAndCount({
      relations: ['user'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(
      items.map((r: any) => ({
        ...r,
        userName: r.user?.displayName || r.user?.username || '—',
        coins: r.coins ?? r.coinAmount ?? 0,
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  listRechargePackages() {
    return this.walletService.listPackages();
  }

  saveRechargePackages(items: Array<Record<string, unknown>>) {
    return this.walletService.savePackages(items);
  }

  listWithdrawPackages() {
    return this.walletService.listWithdrawPackages();
  }

  saveWithdrawPackages(items: Array<Record<string, unknown>>) {
    return this.walletService.saveWithdrawPackages(items);
  }

  async listVipPlans() {
    const plans = await this.vipRepo.find({
      where: { isActive: true },
      order: { level: 'ASC' },
    });
    return plans.map((p) => ({
      // Catalog currently has 10 medal arts; map VIP 1..100 into 10 visual tiers.
      badgeTier: Math.min(10, Math.max(1, Math.ceil((p.level || 1) / 10))),
      ...p,
      price: p.coinPriceMonthly,
      coinPrice: p.coinPriceMonthly,
      durationDays: 30,
      status: p.isActive ? 'active' : 'inactive',
      badgeUrl:
        p.badgeUrl ||
        `/assets/cosmetics/vip/vip_medal_mikoo_${Math.min(7, Math.max(1, p.level || 1))}.png?v=20260801vip7`,
      benefits: p.benefits?.extra?.length
        ? p.benefits.extra
        : [
            p.benefits?.entryEffect ? 'تأثير دخول' : null,
            p.benefits?.hostBadge ? 'إشارة مضيف متحركة' : null,
            p.benefits?.antiKick ? 'حماية من الطرد' : null,
            p.benefits?.exclusiveGifts ? 'هدايا حصرية' : null,
            `أولوية غرف ${p.benefits?.roomPriority ?? p.level}`,
          ].filter(Boolean),
    }));
  }

  async listVipMembers(query: PaginationDto) {
    const [items, total] = await this.userVipRepo.findAndCount({
      relations: ['user', 'vipPlan'],
      skip: query.skip,
      take: query.limit || 50,
      order: { createdAt: 'DESC' },
    });
    return paginate(
      items.map((m) => ({
        id: m.id,
        userId: m.userId,
        userName: m.user?.displayName || m.user?.username,
        planId: m.vipPlanId,
        planName: m.vipPlan?.name || `VIP${m.level}`,
        level: m.level,
        expiresAt: m.expiresAt,
        status: m.isActive && m.expiresAt > new Date() ? 'active' : 'expired',
      })),
      total,
      query.page || 1,
      query.limit || 50,
    );
  }

  async upsertVipPlan(id: string | null, body: Record<string, any>) {
    let plan = id ? await this.vipRepo.findOne({ where: { id } }) : null;
    const level = Number(body.level || plan?.level || 1);
    const benefits = plan?.benefits || {
      entryEffect: level >= 1,
      antiKick: level >= 3,
      antiMute: level >= 4,
      hostBadge: level >= 2,
      exclusiveGifts: level >= 5,
      flyingComment: level >= 6,
      roomPriority: level,
      dmLimit: 50 + level * 20,
      extra: Array.isArray(body.benefits) ? body.benefits : [],
    };
    if (Array.isArray(body.benefits)) benefits.extra = body.benefits;
    if (!plan) {
      plan = this.vipRepo.create({
        level,
        name: body.name || `VIP${level}`,
        coinPriceMonthly: Number(body.price ?? body.coinPriceMonthly ?? vipPriceForLevel(level)),
        badgeUrl: body.badgeUrl || `/assets/cosmetics/vip/vip_medal_mikoo_${Math.min(7, Math.max(1, level))}.png?v=20260801vip7`,
        benefits,
        isActive: body.isActive !== false,
      });
    } else {
      if (body.name) plan.name = body.name;
      if (body.price != null || body.coinPriceMonthly != null) {
        plan.coinPriceMonthly = Number(body.price ?? body.coinPriceMonthly);
      }
      if (body.level != null) plan.level = level;
      plan.benefits = benefits;
      if (typeof body.isActive === 'boolean') plan.isActive = body.isActive;
    }
    return this.vipRepo.save(plan);
  }

  async deleteVipPlan(id: string) {
    const plan = await this.vipRepo.findOne({ where: { id } });
    if (!plan) throw new NotFoundException('VIP plan not found');
    await this.userVipRepo.update({ vipPlanId: id }, { isActive: false });
    await this.vipRepo.delete(id);
    return { deleted: true, id };
  }

  async assignVip(body: { userId: string; planId: string }) {
    const plan = await this.vipRepo.findOne({ where: { id: body.planId } });
    if (!plan) throw new NotFoundException('VIP plan not found');
    const user = await this.usersRepo.findOne({ where: { id: body.userId } });
    if (!user) throw new NotFoundException('User not found');
    await this.userVipRepo.update({ userId: body.userId, isActive: true }, { isActive: false });
    const startsAt = new Date();
    const expiresAt = new Date(startsAt.getTime() + 30 * 24 * 60 * 60 * 1000);
    return this.userVipRepo.save(
      this.userVipRepo.create({
        userId: body.userId,
        vipPlanId: plan.id,
        level: plan.level,
        startsAt,
        expiresAt,
        isActive: true,
      }),
    );
  }

  async revokeVip(id: string) {
    const row = await this.userVipRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('VIP membership not found');
    row.isActive = false;
    return this.userVipRepo.save(row);
  }

  private async revokeActiveVip(userId: string) {
    await this.userVipRepo.update({ userId, isActive: true }, { isActive: false });
  }

  private async assignVipByLevel(userId: string, level: number) {
    const plan = await this.vipRepo.findOne({
      where: { level, isActive: true },
    });
    if (!plan) {
      throw new BadRequestException(`لا توجد خطة VIP بالمستوى ${level}`);
    }
    return this.assignVip({ userId, planId: plan.id });
  }

  async listAgencyApplications(query: PaginationDto) {
    const qb = this.agencyApplicationsRepo
      .createQueryBuilder('application')
      .leftJoinAndSelect('application.applicant', 'applicant')
      .leftJoinAndSelect('application.reviewedBy', 'reviewedBy')
      .orderBy('application.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);
    const status = String(query.status || '').trim().toLowerCase();
    if (status && Object.values(AgencyApplicationStatus).includes(status as AgencyApplicationStatus)) {
      qb.andWhere('application.status = :status', { status });
    }
    if (query.search?.trim()) {
      const search = `%${query.search.trim()}%`;
      qb.andWhere(
        '(application.proposedName ILIKE :search OR application.contactEmail ILIKE :search OR applicant.displayName ILIKE :search OR applicant.username ILIKE :search)',
        { search },
      );
    }
    const [items, total] = await qb.getManyAndCount();
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  async getAgencyApplication(id: string) {
    const application = await this.agencyApplicationsRepo.findOne({
      where: { id },
      relations: ['applicant', 'reviewedBy'],
    });
    if (!application) throw new NotFoundException('Agency application not found');
    return application;
  }

  async reviewAgencyApplication(
    id: string,
    adminId: string,
    action: 'approve' | 'reject' | 'request_changes',
    reviewNote?: string,
  ) {
    const note = String(reviewNote || '').trim();
    if (action !== 'approve' && !note) {
      throw new BadRequestException('A review note is required');
    }
    const result = await this.dataSource.transaction(async (manager) => {
      const application = await manager.findOne(AgencyApplication, {
        where: { id },
        lock: { mode: 'pessimistic_write' },
      });
      if (!application) throw new NotFoundException('Agency application not found');
      if (application.status === AgencyApplicationStatus.APPROVED) {
        if (action !== 'approve') {
          throw new ConflictException('Approved applications cannot be changed');
        }
        const agency = application.agencyId
          ? await manager.findOne(Agency, { where: { id: application.agencyId } })
          : null;
        return { application, agency, room: agency ? await manager.findOne(Room, {
          where: { agencyId: agency.id, hostId: application.applicantId },
        }) : null, shouldRefund: false };
      }
      if (application.status !== AgencyApplicationStatus.PENDING) {
        throw new ConflictException('Only pending applications can be reviewed');
      }

      application.reviewNote = note || null;
      application.reviewedById = adminId;
      application.reviewedAt = new Date();
      if (action === 'reject' || action === 'request_changes') {
        application.status =
          action === 'reject'
            ? AgencyApplicationStatus.REJECTED
            : AgencyApplicationStatus.CHANGES_REQUESTED;
        return {
          application: await manager.save(application),
          agency: null,
          room: null,
          shouldRefund: action === 'reject',
        };
      }

      const existingAgency = await manager
        .createQueryBuilder(Agency, 'agency')
        .where(
          'agency."ownerId" = :applicantId OR LOWER(agency.name) = LOWER(:name)',
          {
            applicantId: application.applicantId,
            name: application.proposedName,
          },
        )
        .getOne();
      const existingMembership = await manager.findOne(AgencyMember, {
        where: {
          userId: application.applicantId,
          isActive: true,
          status: AgencyMemberStatus.ACTIVE,
        },
      });
      if (existingAgency || existingMembership) {
        throw new ConflictException('Applicant already owns or belongs to an agency, or name is taken');
      }

      const commissionRow = await manager.findOne(AppSetting, {
        where: { key: 'agency_default_commission_percent' },
      });
      let activationCode = generateActivationCode();
      for (let attempt = 0; attempt < 12; attempt++) {
        const clash = await manager.findOne(Agency, {
          where: { activationCode },
          select: ['id'],
        });
        if (!clash) break;
        activationCode = generateActivationCode();
      }
      const agency = await manager.save(
        manager.create(Agency, {
          name: application.proposedName,
          description: application.description,
          ownerId: application.applicantId,
          status: AgencyStatus.ACTIVE,
          memberCount: 1,
          totalDiamonds: 0,
          commissionPercent: Number(commissionRow?.value || 20),
          activationCode,
          notificationStyle: 'welcome',
        }),
      );
      await manager.save(
        manager.create(AgencyMember, {
          agencyId: agency.id,
          userId: application.applicantId,
          role: AgencyRole.OWNER,
          status: AgencyMemberStatus.ACTIVE,
          isActive: true,
        }),
      );
      const room = await manager.save(
        manager.create(Room, {
          title: agency.name,
          description: `${agency.name} — persistent agency voice room`,
          type: RoomType.VOICE,
          status: RoomStatus.CLOSED,
          hostId: agency.ownerId,
          activeHostId: null,
          seatCount: 11,
          viewerCount: 0,
          isPublic: true,
          roomKind: RoomKind.AGENCY,
          isPersistent: true,
          agencyId: agency.id,
          accessMode: RoomAccessMode.FREE,
          entryFeeCoins: 0,
          hasPassword: false,
          passwordHash: null,
          zegoRoomId: `agency_${uuidv4().replace(/-/g, '').slice(0, 16)}`,
        }),
      );
      const seats: RoomSeat[] = [];
      for (let index = 0; index < 11; index++) {
        seats.push(
          manager.create(RoomSeat, {
            roomId: room.id,
            seatIndex: index,
            isHostSeat: index === 0,
            status: SeatStatus.EMPTY,
            userId: null,
          }),
        );
      }
      await manager.save(seats);
      application.status = AgencyApplicationStatus.APPROVED;
      application.agencyId = agency.id;
      await manager.save(application);
      return { application, agency, room, shouldRefund: false };
    });

    if ((result as { shouldRefund?: boolean }).shouldRefund) {
      await this.agenciesService.refundApplicationFee(id).catch(() => undefined);
    }

    if (action === 'approve' && result.agency?.activationCode) {
      try {
        await this.notificationsService.create({
          userId: result.agency.ownerId,
          type: NotificationType.AGENCY,
          title: 'تم تفعيل وكالتك',
          body: `وكالتك «${result.agency.name}» نشطة. كود التفعيل: ${result.agency.activationCode} — شاركه مع مضيفاتك فقط.`,
          data: {
            agencyId: result.agency.id,
            activationCode: result.agency.activationCode,
            officialNews: true,
          },
          sendPush: true,
        });
      } catch {
        // ignore
      }
    } else if (action === 'reject') {
      try {
        const app = result.application;
        await this.notificationsService.create({
          userId: app.applicantId,
          type: NotificationType.AGENCY,
          title: 'تم رفض طلب الوكالة',
          body: app.reviewNote
            ? `لم تتم الموافقة على طلب وكالتك. السبب: ${app.reviewNote}`
            : 'لم تتم الموافقة على طلب وكالتك.',
          data: { applicationId: app.id, officialNews: true, action: 'reject' },
          sendPush: true,
        });
      } catch {
        // ignore
      }
    } else if (action === 'request_changes') {
      try {
        const app = result.application;
        await this.notificationsService.create({
          userId: app.applicantId,
          type: NotificationType.AGENCY,
          title: 'مطلوب تعديلات على طلب الوكالة',
          body: app.reviewNote
            ? `يرجى تعديل طلب الوكالة: ${app.reviewNote}`
            : 'يرجى تعديل طلب الوكالة وإعادة الإرسال.',
          data: {
            applicationId: app.id,
            officialNews: true,
            action: 'request_changes',
          },
          sendPush: true,
        });
      } catch {
        // ignore
      }
    }
    return result;
  }

  /** Issue or rotate the private activation code for an agency. */
  async regenerateAgencyActivationCode(id: string) {
    const agency = await this.agencyRepo.findOne({ where: { id } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    let activationCode = generateActivationCode();
    for (let attempt = 0; attempt < 12; attempt++) {
      const clash = await this.agencyRepo.findOne({
        where: { activationCode },
        select: ['id'],
      });
      if (!clash || clash.id === id) break;
      activationCode = generateActivationCode();
    }
    agency.activationCode = activationCode;
    await this.agencyRepo.save(agency);
    try {
      await this.notificationsService.create({
        userId: agency.ownerId,
        type: NotificationType.AGENCY,
        title: 'كود تفعيل الوكالة',
        body: `كود تفعيل وكالتك «${agency.name}»: ${activationCode} — شاركه مع مضيفاتك فقط.`,
        data: { agencyId: agency.id, activationCode },
        sendPush: true,
      });
    } catch {
      // ignore push failures
    }
    return {
      id: agency.id,
      name: agency.name,
      activationCode: agency.activationCode,
      notificationStyle: agency.notificationStyle,
    };
  }

  async listAgencies(query: PaginationDto) {
    const qb = this.agencyRepo
      .createQueryBuilder('a')
      .leftJoinAndSelect('a.owner', 'owner')
      .orderBy('a.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    if (query.search?.trim()) {
      const raw = query.search.trim();
      const s = `%${raw}%`;
      // Explicit text casts avoid Postgres "could not determine data type of parameter".
      qb.andWhere(
        '(a.name ILIKE :s OR CAST(a.id AS text) ILIKE :s OR owner.displayName ILIKE :s OR owner.username ILIKE :s OR CAST(owner.publicId AS text) ILIKE :s)',
        { s },
      );
    }
    const status = (query.status || '').toLowerCase();
    if (status === 'pending') {
      qb.andWhere('a.status = :st', { st: AgencyStatus.PENDING });
    } else if (status === 'active') {
      qb.andWhere('a.status = :st', { st: AgencyStatus.ACTIVE });
    } else if (status === 'suspended') {
      qb.andWhere('a.status = :st', { st: AgencyStatus.SUSPENDED });
    }

    const [items, total] = await qb.getManyAndCount();
    const mapped = items.map((a) => ({
      ...a,
      ownerName: a.owner?.displayName || a.owner?.username || a.ownerId,
      hostsCount: Number(a.memberCount || 0),
      membersCount: Number(a.memberCount || 0),
      commission: Number(a.commissionPercent || 0),
      commissionRate: Number(a.commissionPercent || 0),
      activationCode: a.activationCode || null,
      notificationStyle: a.notificationStyle || 'welcome',
    }));
    return paginate(mapped, total, query.page || 1, query.limit || 20);
  }

  async createAgency(dto: {
    name: string;
    description?: string;
    ownerId: string;
    commissionPercent?: number;
    logoUrl?: string;
    status?: AgencyStatus;
  }) {
    throw new BadRequestException(
      'Direct agency creation is disabled; approve a professional application',
    );
  }

  async updateAgency(
    id: string,
    dto: Partial<{
      name: string;
      description: string;
      commissionPercent: number;
      commission: number;
      logoUrl: string;
      ownerId: string;
      status: AgencyStatus;
    }>,
  ) {
    const agency = await this.agencyRepo.findOne({ where: { id } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    if (dto.name != null) agency.name = String(dto.name).trim();
    if (dto.description !== undefined) agency.description = dto.description || null;
    if (dto.logoUrl !== undefined) agency.logoUrl = dto.logoUrl || null;
    if (dto.ownerId && dto.ownerId !== agency.ownerId) {
      throw new BadRequestException(
        'Direct owner transfer is disabled because memberships and permanent rooms must be migrated transactionally',
      );
    }
    const commission = dto.commissionPercent ?? dto.commission;
    if (commission != null) agency.commissionPercent = Number(commission);
    if (dto.status) agency.status = dto.status;
    return this.agencyRepo.save(agency);
  }

  async approveAgency(id: string) {
    const agency = await this.updateAgency(id, { status: AgencyStatus.ACTIVE });
    const rooms = await this.roomsRepo.find({ where: { agencyId: id } });
    for (const room of rooms) {
      room.status = RoomStatus.OPEN;
      room.activeHostId = null;
      room.viewerCount = 0;
      room.isPublic = true;
      await this.roomsRepo.save(room);
      await this.roomSeatsRepo.update(
        { roomId: room.id },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      this.realtimeGateway.emitToRoom(room.id, 'room:event', {
        roomId: room.id,
        event: 'room:reactivated',
        payload: { roomId: room.id, agencyId: id },
        at: new Date().toISOString(),
      });
    }
    try {
      await this.notificationsService.create({
        userId: agency.ownerId,
        type: NotificationType.AGENCY,
        title: 'تم إعادة تفعيل الوكالة',
        body: `وكالتك «${agency.name}» أصبحت نشطة مجدداً.`,
        data: { agencyId: agency.id, officialNews: true, action: 'approve' },
        sendPush: true,
      });
    } catch {
      // ignore
    }
    return agency;
  }

  async suspendAgency(id: string, _reason?: string) {
    const agency = await this.updateAgency(id, { status: AgencyStatus.SUSPENDED });
    const rooms = await this.roomsRepo.find({ where: { agencyId: id } });
    for (const room of rooms) {
      await this.roomSeatsRepo.update(
        { roomId: room.id },
        {
          userId: null,
          status: SeatStatus.EMPTY,
          isMuted: false,
          isModeratorMuted: false,
        },
      );
      await this.txRepo.manager.delete(RoomAccess, { roomId: room.id });
      room.status = RoomStatus.CLOSED;
      room.activeHostId = null;
      room.viewerCount = 0;
      await this.roomsRepo.save(room);
      await this.realtimeGateway.ejectRoom(room.id, _reason || 'agency_suspended');
    }
    try {
      const reason = String(_reason || '').trim();
      await this.notificationsService.create({
        userId: agency.ownerId,
        type: NotificationType.AGENCY,
        title: 'تم تعليق الوكالة',
        body: reason
          ? `تم تعليق وكالتك «${agency.name}». السبب: ${reason}`
          : `تم تعليق وكالتك «${agency.name}». تواصل مع الدعم للمزيد.`,
        data: {
          agencyId: agency.id,
          officialNews: true,
          action: 'suspend',
          reason: reason || null,
        },
        sendPush: true,
      });
    } catch {
      // ignore
    }
    return agency;
  }

  async getAgency(id: string) {
    const agency = await this.agencyRepo.findOne({
      where: { id },
      relations: ['owner'],
    });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    const members = await this.agencyMembersRepo.find({
      where: { agencyId: id },
      relations: ['user'],
      order: { joinedAt: 'ASC' },
    });
    return {
      ...agency,
      ownerName: agency.owner?.displayName || agency.owner?.username || agency.ownerId,
      hostsCount: Number(agency.memberCount || 0),
      membersCount: Number(agency.memberCount || 0),
      commission: Number(agency.commissionPercent || 0),
      members: members.map((m) => ({
        id: m.id,
        userId: m.userId,
        role: m.role,
        status: m.status,
        isActive: m.isActive,
        publicId: m.user?.publicId || null,
        displayName: m.user?.displayName || m.user?.username || m.userId,
        username: m.user?.username || null,
        avatarUrl: m.user?.avatarUrl || null,
      })),
    };
  }

  async removeAgencyMember(agencyId: string, userId: string) {
    const agency = await this.agencyRepo.findOne({ where: { id: agencyId } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    if (agency.ownerId === userId) {
      throw new BadRequestException('لا يمكن طرد مالك الوكالة');
    }
    const member = await this.agencyMembersRepo.findOne({
      where: { agencyId, userId },
    });
    if (!member) throw new NotFoundException('العضو غير موجود');
    if (member.role === AgencyRole.OWNER) {
      throw new BadRequestException('لا يمكن طرد المالك');
    }
    const wasActive =
      member.status === AgencyMemberStatus.ACTIVE && member.isActive;
    await this.agencyMembersRepo.delete(member.id);
    if (wasActive && Number(agency.memberCount || 0) > 0) {
      await this.agencyRepo.decrement({ id: agencyId }, 'memberCount', 1);
    }
    return { ok: true, agencyId, userId };
  }

  async listNotifications(query: PaginationDto) {
    const [items, total] = await this.notificationRepo.findAndCount({
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(items, total, query.page || 1, query.limit || 20);
  }

  async sendNotification(body: {
    title: string;
    body: string;
    userId?: string;
    audience?: 'all' | 'hosts' | 'vip' | 'user';
    channel?: 'push' | 'in_app' | 'both';
    type?: NotificationType;
    message?: string;
  }) {
    return this.notificationsService.adminSend({
      title: body.title,
      body: body.body || body.message || '',
      userId: body.userId,
      audience: body.audience || (body.userId ? 'user' : 'all'),
      channel: body.channel || 'both',
      type: body.type || NotificationType.SYSTEM,
    });
  }

  async patchSettings(body: Record<string, string>) {
    const results: AppSetting[] = [];
    for (const [key, value] of Object.entries(body || {})) {
      results.push(await this.setSetting(key, String(value)));
    }
    return results;
  }

  async listLogs(query: PaginationDto) {
    const [items, total] = await this.abuseRepo.findAndCount({
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    return paginate(items, total, query.page || 1, query.limit || 20);
  }
}
