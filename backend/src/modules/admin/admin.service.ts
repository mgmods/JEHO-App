import { Injectable, NotFoundException, UnauthorizedException, BadRequestException, ForbiddenException, ConflictException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, IsNull, LessThan, Not, Repository } from 'typeorm';
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
import { RechargeOrder, RechargeStatus } from '../../database/entities/recharge-order.entity';
import { WithdrawRequest, WithdrawStatus } from '../../database/entities/withdraw-request.entity';
import {
  Report,
  ReportStatus,
  ReportTargetType,
} from '../../database/entities/report.entity';
import { AdminUser } from '../../database/entities/admin-user.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { AbuseLog, AbuseSeverity } from '../../database/entities/abuse-log.entity';
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
import { GiftsService } from '../gifts/gifts.service';
import { AgenciesService } from '../agencies/agencies.service';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { purgeRoomReferencesBeforeDelete } from '../../common/room-delete-sql';
import {
  generateActivationCode,
} from '../agencies/agency-activation';
import {
  isValidAgencyPublicId,
  normalizeAgencyPublicId,
} from '../agencies/agency-perks';
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
import { salaryLadderToHostTargetStages } from '../../common/host-salary-ladder';
import {
  normalizeStaffRole,
  isDashboardSuper,
  staffRank,
  type PlatformStaffRole,
} from '../../common/staff-role';

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

  /** Gift branded for an agency (logo/name on send payload) — admin only */
  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  brandAgencyId?: string | null;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  category?: string;
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
  private staffRoleColumnReady = false;

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
    private readonly cosmeticsService: CosmeticsService,
    private readonly giftsService: GiftsService,
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
      revenueByProviderRaw,
      todayRechargeSum,
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
        .where('r.status = :s', { s: RechargeStatus.COMPLETED })
        .getRawOne(),
      this.rechargeRepo
        .createQueryBuilder('r')
        .select('r.provider', 'provider')
        .addSelect('COALESCE(SUM(r.amountFiat),0)', 'total')
        .addSelect('COUNT(*)', 'count')
        .where('r.status = :s', { s: RechargeStatus.COMPLETED })
        .groupBy('r.provider')
        .getRawMany(),
      this.rechargeRepo
        .createQueryBuilder('r')
        .select('COALESCE(SUM(r.amountFiat),0)', 'total')
        .where('r.status = :s', { s: RechargeStatus.COMPLETED })
        .andWhere('r.completedAt >= :day', {
          day: (() => {
            const d = new Date();
            d.setHours(0, 0, 0, 0);
            return d;
          })(),
        })
        .getRawOne(),
    ]);

    const revenueByProvider = (revenueByProviderRaw || []).map((row: any) => {
      const provider = String(row.provider || 'other');
      return {
        provider,
        label: this.providerRevenueLabel(provider),
        total: Number(row.total || 0),
        count: Number(row.count || 0),
      };
    }).sort((a, b) => b.total - a.total);

    const totalRev = Number(rechargeSum?.total || 0);
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
      totalRechargeFiat: totalRev,
      revenue: totalRev,
      totalRevenue: totalRev,
      todayRevenue: Number(todayRechargeSum?.total || 0),
      revenueByProvider,
    };
  }

  private providerRevenueLabel(provider: string): string {
    switch (String(provider || '').toLowerCase()) {
      case 'google_play':
        return 'جوجل بلاي';
      case 'fourthwall':
        return 'بطاقة';
      case 'binance_wallet':
      case 'binance_pay':
      case 'crypto':
        return 'USDT';
      case 'sham_cash':
        return 'شام كاش';
      case 'stripe':
        return 'Stripe';
      case 'paypal':
        return 'PayPal';
      case 'admin':
        return 'أدمن';
      case 'recharge_agent':
        return 'وكيل شحن';
      default:
        return provider || 'أخرى';
    }
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
    await this.ensureStaffRoleColumn();
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
    const frameByUser = await this.equippedFramesByUserIds(
      items.map((u) => u.id),
    );
    const mapped = items.map((u) => {
      const base = this.mapAdminUser(u) as Record<string, unknown>;
      const equipped = frameByUser.get(u.id) || null;
      const profileFrame =
        (u.profile as any)?.vipBadgeUrl || (u.profile as any)?.hostBadgeUrl || null;
      const frameUrl = equipped || profileFrame || null;
      return {
        ...base,
        frameUrl,
        frameAnimUrl: equipped || frameUrl,
      };
    });
    return paginate(mapped, total, query.page || 1, query.limit || 20);
  }

  private async ensureStaffRoleColumn() {
    if (this.staffRoleColumnReady) return;
    try {
      await this.dataSource.query(`
        ALTER TABLE users
        ADD COLUMN IF NOT EXISTS "staffRole" varchar(16) DEFAULT NULL
      `);
    } catch {
      // column may already exist or DB user lacks ALTER
    }
    this.staffRoleColumnReady = true;
  }

  /** Equipped headwear frames (vip_badge / host_badge) keyed by userId. */
  private async equippedFramesByUserIds(
    userIds: string[],
  ): Promise<Map<string, string>> {
    const frameByUser = new Map<string, string>();
    const ids = [...new Set(userIds.filter(Boolean))];
    if (!ids.length) return frameByUser;
    const equipped = await this.userCosmeticRepo
      .createQueryBuilder('uc')
      .innerJoinAndSelect('uc.cosmetic', 'c')
      .where('uc.userId IN (:...ids)', { ids })
      .andWhere('uc.equipped = true')
      .andWhere('(uc.expiresAt IS NULL OR uc.expiresAt > NOW())')
      .andWhere('c.isActive = true')
      .andWhere('c.type IN (:...types)', {
        types: ['vip_badge', 'host_badge'],
      })
      .getMany();
    for (const row of equipped) {
      const c: any = row.cosmetic;
      const url = c?.animationUrl || c?.previewUrl;
      if (url && !frameByUser.has(row.userId)) {
        frameByUser.set(row.userId, String(url));
      }
    }
    return frameByUser;
  }

  private mapAdminUser(user: User & { wallet?: Wallet; profile?: { country?: string | null; totalSentCoins?: number; totalReceivedDiamonds?: number; vipBadgeUrl?: string | null; hostBadgeUrl?: string | null } }) {
    const wallet = user.wallet;
    const staffRole = normalizeStaffRole(user);
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
      staffRole,
      isAdmin: !!user.isAdmin || staffRole === 'super',
      isManager: staffRole === 'manager',
      isSuperAdmin: staffRole === 'super',
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
    user.staffRole = null;
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
        title: 'مبروك!',
        body: `${displayName} لعب Solo 77 وفاز بـ 777`,
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
      .leftJoinAndSelect('host.profile', 'hostProfile')
      // CS rooms first — addSelect alias (TypeORM breaks on raw CASE in orderBy).
      .addSelect(
        `CASE WHEN r."roomKind" = :adminSupportPin THEN 1 ELSE 0 END`,
        'admin_support_pin',
      )
      .setParameter('adminSupportPin', RoomKind.SUPPORT)
      .orderBy('admin_support_pin', 'DESC')
      .addOrderBy('r.createdAt', 'DESC')
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
    } else if (status === 'live') {
      qb.andWhere('r.status = :open', { open: RoomStatus.OPEN });
      qb.andWhere('r.activeHostId IS NOT NULL');
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
    const hostIds = [
      ...new Set(
        items
          .map((r) => r.hostId || r.host?.id)
          .filter((id): id is string => !!id),
      ),
    ];

    /** Equipped headwear (vip_badge frame) for each host */
    const frameByHost = new Map<string, string>();
    if (hostIds.length) {
      const equipped = await this.userCosmeticRepo
        .createQueryBuilder('uc')
        .innerJoinAndSelect('uc.cosmetic', 'c')
        .where('uc.userId IN (:...ids)', { ids: hostIds })
        .andWhere('uc.equipped = true')
        .andWhere('(uc.expiresAt IS NULL OR uc.expiresAt > NOW())')
        .andWhere('c.isActive = true')
        .andWhere('c.type IN (:...types)', {
          types: ['vip_badge', 'host_badge'],
        })
        .getMany();
      for (const row of equipped) {
        const c: any = row.cosmetic;
        const url = c?.animationUrl || c?.previewUrl;
        if (url && !frameByHost.has(row.userId)) {
          frameByHost.set(row.userId, String(url));
        }
      }
    }

    const mapped = items.map((r) => {
      const hostId = r.hostId || r.host?.id || '';
      const profile = (r.host as any)?.profile;
      const equippedFrame = hostId ? frameByHost.get(hostId) : null;
      const profileFrame =
        profile?.vipBadgeUrl || profile?.hostBadgeUrl || null;
      const hostFrameUrl = equippedFrame || profileFrame || null;
      const isLive = r.status === RoomStatus.OPEN && !!r.activeHostId;
      return {
        ...r,
        name: r.title,
        ownerName: r.host?.displayName || r.host?.username || '—',
        hostName: r.host?.displayName || r.host?.username || '—',
        hostAvatarUrl: r.host?.avatarUrl || null,
        hostFrameUrl,
        hostFrameAnimUrl: equippedFrame || hostFrameUrl,
        membersCount: r.viewerCount ?? 0,
        isSupport: r.roomKind === RoomKind.SUPPORT,
        isLive,
        status: isLive ? 'live' : r.status,
      };
    });
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
    if (room.agencyId || room.roomKind === RoomKind.AGENCY) {
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
    const isAgencyRoom = room.roomKind === RoomKind.AGENCY || !!room.agencyId;
    if (isAgencyRoom && !opts.allowAgency) {
      throw new ForbiddenException(
        'Persistent agency rooms cannot be deleted; suspend the agency instead.',
      );
    }

    // Always force-end live + eject before hard delete so clients leave now
    try {
      await this.forceEndStream(roomId, 'admin_deleted');
    } catch {
      /* already closed / missing */
    }

    this.realtimeGateway.emitToRoom(roomId, 'room:event', {
      roomId,
      event: 'room:closed',
      payload: { roomId, reason: 'admin_deleted' },
      at: new Date().toISOString(),
    });
    await this.realtimeGateway.ejectRoom(roomId, 'admin_deleted').catch(() => undefined);

    await this.dataSource.transaction(async (manager) => {
      await purgeRoomReferencesBeforeDelete(
        manager,
        roomId,
        String(room.title || roomId),
      );
      await manager.getRepository(Room).delete(roomId);
    });
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
    dto: {
      accessMode?: string;
      entryFeeCoins?: number;
      title?: string;
      roomKind?: string;
      isSupport?: boolean;
    },
  ) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (dto.title !== undefined) room.title = dto.title;

    const kindHint = String(dto.roomKind || '').toLowerCase();
    const enableSupport = dto.isSupport === true || kindHint === RoomKind.SUPPORT;
    const disableSupport =
      dto.isSupport === false ||
      (kindHint === RoomKind.STANDARD && room.roomKind === RoomKind.SUPPORT);

    if (enableSupport) {
      // Only real agency rooms are blocked — personal rooms of hosts in an
      // agency still may keep a null agencyId; rely on roomKind.
      if (room.roomKind === RoomKind.AGENCY || !!room.agencyId) {
        throw new BadRequestException(
          'لا يمكن ترقية روم وكالة إلى خدمة عملاء — اختر روماً شخصياً',
        );
      }
      room.roomKind = RoomKind.SUPPORT;
      room.isPersistent = true;
      room.status = RoomStatus.OPEN;
      room.isPublic = true;
      room.hasPassword = false;
      room.passwordHash = null;
      room.accessMode = RoomAccessMode.FREE;
      room.entryFeeCoins = 0;
      // Keep visible at top of home even if the host is briefly offline.
      room.emptySince = null;
    } else if (disableSupport) {
      room.roomKind = RoomKind.STANDARD;
      room.isPersistent = false;
    }

    const lockedFree =
      !!room.agencyId ||
      room.roomKind === RoomKind.AGENCY ||
      room.roomKind === RoomKind.SUPPORT ||
      room.isPersistent;

    if (lockedFree) {
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

  async setRoomCustomerService(roomId: string, enabled: boolean) {
    return this.updateRoom(roomId, { isSupport: enabled });
  }

  reconcileFriendsCounts() {
    return this.usersService.reconcileFriendsCounts();
  }

  async listGifts() {
    return this.giftsRepo.find({ order: { sortOrder: 'ASC' } });
  }

  listGiftCategories() {
    return this.giftsService.listCategoriesAdmin();
  }

  upsertGiftCategory(
    id: string | null,
    dto: {
      key?: string;
      labelAr?: string;
      labelEn?: string | null;
      sortOrder?: number;
      isActive?: boolean;
      iconUrl?: string | null;
    },
  ) {
    return this.giftsService.upsertCategory(id, dto);
  }

  deleteGiftCategory(id: string) {
    return this.giftsService.deleteCategory(id);
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

  /** Merge still-image gifts only — never wipes video gifts. */
  rebuildStillGiftCatalog() {
    return this.giftsService.rebuildStillImageCatalog();
  }

  /** Upsert flag + premium video gifts. */
  importJehoDesignedGifts() {
    return this.giftsService.importJehoDesignedGifts();
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
      category: dto.category ?? (gift as any).category ?? 'normal',
      brandAgencyId:
        dto.brandAgencyId === undefined
          ? (gift as any).brandAgencyId ?? null
          : dto.brandAgencyId
            ? String(dto.brandAgencyId)
            : null,
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
      items.map((w) => {
        const details = (w.payoutDetails || {}) as Record<string, unknown>;
        const src = String(details.source || details.channel || details.stream || '').toLowerCase();
        const isAgency =
          src.includes('agency') || src === 'agency_commission' || src === 'agency_host';
        const stream = isAgency ? 'agency' : 'personal';
        const agencyKind =
          src.includes('host') && src.includes('agency')
            ? 'host'
            : src === 'agency_host'
              ? 'host'
              : isAgency
                ? 'commission'
                : null;
        return {
          ...w,
          userName: w.user?.displayName || w.user?.username || '—',
          amount: w.diamonds,
          stream,
          agencyKind,
          isAgencyCommission: isAgency && agencyKind !== 'host',
          isAgencyHost: isAgency && agencyKind === 'host',
          isPersonalRoom: stream === 'personal',
        };
      }),
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
            const details = (req.payoutDetails || {}) as Record<string, unknown>;
            const src = String(details.source || details.channel || '').toLowerCase();
            const agencyPool =
              src.includes('agency') ||
              src === 'agency_commission' ||
              src === 'agency_host' ||
              src === 'agency' ||
              src === 'agency_room' ||
              String(details.stream || '') === 'agency';
            if (agencyPool) {
              (wallet as any).agencyDiamonds =
                Number((wallet as any).agencyDiamonds || 0) + amount;
            } else {
              wallet.diamonds = Number(wallet.diamonds || 0) + amount;
            }
            await manager.save(wallet);
            await manager.save(
              manager.create(WalletTransaction, {
                userId: req.userId,
                type: TransactionType.ADMIN_ADJUST,
                currency: CurrencyType.DIAMONDS,
                amount,
                balanceAfter: agencyPool
                  ? Number((wallet as any).agencyDiamonds || 0)
                  : Number(wallet.diamonds),
                referenceType: 'withdraw_refund',
                referenceId: refundRef,
                description: agencyPool
                  ? src.includes('host')
                    ? `رفض سحب أرباح مضيفة — إرجاع`
                    : `رفض سحب عمولة وكالة — إرجاع`
                  : `رفض سحب روم شخصي — إرجاع`,
                metadata: { stream: agencyPool ? 'agency' : 'personal', source: src },
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

  /**
   * Public-facing policy brochure snapshot for hosts / agencies / supporters.
   * No secrets (payment keys, Zego, admin internals).
   */
  async policyBrochureSnapshot() {
    const settingsRows = await this.settingsRepo.find();
    const settings: Record<string, string> = {};
    for (const row of settingsRows) {
      if (row?.key != null) settings[row.key] = String(row.value ?? '');
    }

    const parseJson = <T>(key: string, fallback: T): T => {
      try {
        const raw = settings[key];
        if (!raw) return fallback;
        return JSON.parse(raw) as T;
      } catch {
        return fallback;
      }
    };

    const {
      GIFT_DIAMOND_RATIO,
      LUCKY_GIFT_DIAMOND_RATIO,
      AGENCY_CREATE,
      HOST_ROOM_INVITE_REWARD,
      STANDARD_RECHARGE_PACKAGES,
      MALL_COSMETIC_PRICES,
    } = await import('../../common/pricing-catalog');

    const gifts = await this.giftsRepo.find({
      where: { isActive: true },
      order: { sortOrder: 'ASC', coinPrice: 'ASC' },
      take: 120,
    });

    const packages = await this.walletService.listPackages().catch(() => ({
      items: [...STANDARD_RECHARGE_PACKAGES],
    }));

    const vipPlans = await this.vipRepo.find({
      where: { isActive: true },
      order: { level: 'ASC' },
      take: 40,
    });

    let vanityItems: Array<{ publicId: string; priceCoins: number; status?: string }> = [];
    try {
      const rows = await this.dataSource.query(
        `SELECT "publicId", "priceCoins", status FROM vanity_ids
         WHERE status = 'available'
         ORDER BY "priceCoins" ASC
         LIMIT 80`,
      );
      if (Array.isArray(rows)) {
        vanityItems = rows.map((r: any) => ({
          publicId: String(r.publicId ?? ''),
          priceCoins: Number(r.priceCoins ?? 0) || 0,
          status: String(r.status ?? ''),
        }));
      }
    } catch {
      vanityItems = [];
    }

    let cosmetics: Array<{ name: string; type: string; priceCoins: number }> = [];
    try {
      const rows = await this.dataSource.query(
        `SELECT name, type, "coinPrice" FROM cosmetics
         WHERE "isActive" = true
         ORDER BY type ASC, "coinPrice" ASC
         LIMIT 100`,
      );
      if (Array.isArray(rows)) {
        cosmetics = rows.map((r: any) => ({
          name: String(r.name ?? ''),
          type: String(r.type ?? ''),
          priceCoins: Number(r.coinPrice ?? 0) || 0,
        }));
      }
    } catch {
      cosmetics = [];
    }

    let luckyBoxes: Array<{ name: string; priceCoins: number; note?: string }> = [];
    try {
      const rows = await this.dataSource.query(
        `SELECT title, kind, "costCoins" FROM lucky_boxes
         WHERE "isActive" = true
         ORDER BY "costCoins" ASC
         LIMIT 40`,
      );
      if (Array.isArray(rows)) {
        luckyBoxes = rows.map((r: any) => ({
          name: String(r.title ?? 'صندوق حظ'),
          priceCoins: Number(r.costCoins ?? 0) || 0,
          note: String(r.kind || '') === 'free_daily' ? 'يومي مجاني' : undefined,
        }));
      }
    } catch {
      luckyBoxes = [];
    }

    const hostTargetRaw =
      parseJson<any>('host_monthly_target', null) ||
      parseJson<any>('hostMonthlyTarget', null);
    let hostTarget: any[] = [];
    if (Array.isArray(hostTargetRaw)) {
      hostTarget = hostTargetRaw;
    } else if (hostTargetRaw && Array.isArray(hostTargetRaw.stages)) {
      hostTarget = hostTargetRaw.stages;
    }
    const hasSalary = hostTarget.some(
      (s) => Number(s?.hostSalaryUsd) > 0 || Number(s?.agentSalaryUsd) > 0,
    );
    if (!hostTarget.length || !hasSalary) {
      hostTarget = salaryLadderToHostTargetStages();
    }

    const agencyCreateCoins = Number(
      settings[AGENCY_CREATE.settingKey] || AGENCY_CREATE.defaultCoins,
    );

    const economy = {
      giftDiamondRatio: GIFT_DIAMOND_RATIO,
      luckyGiftDiamondRatio: LUCKY_GIFT_DIAMOND_RATIO,
      diamondUsdRate: Number(settings['economy.diamondUsdRate'] || 0.00005),
      diamondCoinRate: Number(settings['economy.diamondCoinRate'] || 0.55),
      withdrawTargetDiamonds: Number(
        settings['economy.withdrawTargetDiamonds'] ||
          settings['withdraw_target_diamonds'] ||
          10000,
      ),
      agencyCreateCoins: Number.isFinite(agencyCreateCoins)
        ? agencyCreateCoins
        : AGENCY_CREATE.defaultCoins,
      hostInviteDiamonds: HOST_ROOM_INVITE_REWARD.diamonds,
      hostInviteDwellSeconds: HOST_ROOM_INVITE_REWARD.dwellSeconds,
      hostInviteMaxPerDay: HOST_ROOM_INVITE_REWARD.maxRewardsPerHostPerDay,
      platformShare: Number(settings['economy.platform_share'] || settings['gift_platform_share'] || 0.4),
      agencyShare: Number(settings['economy.agency_share'] || settings['gift_agency_share'] || 0.15),
      hostShareWithAgency: Number(settings['economy.host_share_agency'] || 0.45),
      hostShareSolo: Number(settings['economy.host_share_solo'] || 0.6),
    };

    const paymentFlags = {
      shamCash: /true|1|yes/i.test(String(settings['payments.sham_cash.enabled'] || settings['sham_cash_enabled'] || '')),
      binancePay: /true|1|yes/i.test(String(settings['payments.binance_pay.enabled'] || settings['binance_pay_enabled'] || '')),
      fourthwall: /true|1|yes/i.test(String(settings['payments.fourthwall.enabled'] || settings['fourthwall_enabled'] || '')),
      googlePlay: true,
      rechargeAgents: true,
    };

    const storeOffers = (() => {
      const raw = parseJson<any>('store_offers', []);
      const list = Array.isArray(raw) ? raw : raw?.items || [];
      return (list as any[])
        .filter((o) => o && (o.active !== false))
        .slice(0, 40)
        .map((o) => ({
          title: String(o.title || o.name || o.sku || 'عرض'),
          subtitle: String(o.subtitle || o.description || ''),
          coins: Number(o.coins || 0) || 0,
          bonusCoins: Number(o.bonusCoins || o.bonus || 0) || 0,
          priceUsd: Number(o.priceUsd || o.price || 0) || 0,
        }));
    })();

    const homeBanners = (() => {
      const raw = parseJson<any>('home_banners', []);
      const list = Array.isArray(raw) ? raw : raw?.items || [];
      return (list as any[]).slice(0, 20).map((b) => ({
        title: String(b.title || b.name || 'بنر'),
        imageUrl: String(b.imageUrl || b.url || ''),
        link: String(b.link || b.href || ''),
      }));
    })();

    let promoCatalog: any = null;
    try {
      const {
        PROMO_CATALOG_VERSION,
        MONTHLY_RECHARGE_OFFERS,
        AGENT_FLOAT_BONUS_TIERS,
        SUPPORTER_PACKS,
        VIP_DURATION_PACKS,
      } = await import('../../common/promo-catalog');
      const override = parseJson<any>('promo_catalog', null);
      promoCatalog = override || {
        version: PROMO_CATALOG_VERSION,
        monthlyOffers: MONTHLY_RECHARGE_OFFERS,
        agentTiers: AGENT_FLOAT_BONUS_TIERS,
        supporterPacks: SUPPORTER_PACKS,
        vipDurationPacks: VIP_DURATION_PACKS,
      };
    } catch {
      promoCatalog = null;
    }

    let withdrawPackages: any[] = [];
    try {
      const wp = await this.walletService.listWithdrawPackages?.();
      const items = (wp as any)?.items || wp || [];
      withdrawPackages = Array.isArray(items)
        ? items.slice(0, 20).map((p: any) => ({
            usd: Number(p.usd || p.priceUsd || 0) || 0,
            diamonds: Number(p.diamonds || 0) || 0,
          }))
        : [];
    } catch {
      withdrawPackages = [];
    }

    const games = (() => {
      const raw = parseJson<any>('app_games', []);
      const list = Array.isArray(raw) ? raw : raw?.games || raw?.items || [];
      return (list as any[])
        .filter((g) => g && g.enabled !== false)
        .slice(0, 40)
        .map((g) => ({
          name: String(g.name || g.title || g.id || 'لعبة'),
          id: String(g.id || g.code || ''),
        }));
    })();

    let agentPricing: any = null;
    try {
      agentPricing = parseJson<any>('recharge_agent_pricing', null);
    } catch {
      agentPricing = null;
    }

    const mediaBase = 'https://api.adnova.bbs.tr';
    const absUrl = (u?: string | null) => {
      if (!u) return null;
      const s = String(u).trim();
      if (!s) return null;
      if (s.startsWith('http')) return s;
      return `${mediaBase}${s.startsWith('/') ? '' : '/'}${s}`;
    };

    return {
      brand: 'JEHO CHAT',
      logoUrl: `${mediaBase}/logo.png`,
      audience: 'hosts_agencies_supporters',
      generatedAt: new Date().toISOString(),
      economy,
      hostTarget: Array.isArray(hostTarget) ? hostTarget : [],
      gifts: gifts.map((g) => ({
        id: g.id,
        name: g.name,
        coinPrice: g.coinPrice,
        diamondValue: g.diamondValue,
        type: g.type,
        iconUrl: absUrl(g.iconUrl),
      })),
      packages: (packages as any)?.items || packages || [],
      storeOffers,
      promoCatalog,
      homeBanners,
      withdrawPackages,
      games,
      agentPricing,
      vipPlans: vipPlans.map((p) => ({
        id: p.id,
        name: p.name,
        level: p.level,
        priceCoins: p.coinPriceMonthly ?? vipPriceForLevel(p.level),
        durationDays: 30,
      })),
      vanityIds: vanityItems,
      cosmetics,
      mallPriceHints: MALL_COSMETIC_PRICES,
      luckyBoxes,
      paymentFlags,
      luckyGiftTiers: [
        { name: 'حظ برونزي', coinPrice: 100 },
        { name: 'حظ فضي', coinPrice: 500 },
        { name: 'حظ ذهبي', coinPrice: 1000 },
      ],
      notes: {
        intro:
          'دليل سياسة JEHO CHAT المحدّث: اقتصاد العملات والألماس، تقسيم الهدايا، برنامج الوكالات (تحقق، ID، عمولة، إطارات حصرية، دعوات)، تارجت ورواتب، مهام يومية وإعلانات AdMob، هدايا الدول والفيديو، الشحن والسحب — بلا أسرار إدارية.',
      },
    };
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
    if (key === 'app_nav_icons') {
      try {
        const { sanitizeNavIconsConfig } = await import('../config/nav-icons.util');
        const parsed = typeof value === 'string' ? JSON.parse(value) : value;
        const clean = sanitizeNavIconsConfig(parsed);
        const prev = await this.settingsRepo.findOne({ where: { key } });
        let prevVer = 0;
        if (prev?.value) {
          try {
            prevVer = Math.max(0, Number(JSON.parse(prev.value)?.version) || 0);
          } catch {
            prevVer = 0;
          }
        }
        clean.version = prevVer + 1;
        clean.updatedAt = new Date().toISOString();
        nextValue = JSON.stringify(clean);
      } catch {
        // keep original
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
    if (!user || !user.passwordHash) {
      throw new UnauthorizedException('Invalid admin credentials');
    }
    // Browser dashboard is Super-only. Managers (in-app staff) cannot log in.
    if (!isDashboardSuper(user)) {
      throw new UnauthorizedException(
        'Super admin only — dashboard is not available for managers or regular accounts',
      );
    }
    const ok = await bcrypt.compare(password, user.passwordHash);
    if (!ok) throw new UnauthorizedException('Invalid admin credentials');

    // Keep isAdmin + staffRole in sync for Super accounts.
    let dirty = false;
    if (!user.isAdmin) {
      user.isAdmin = true;
      dirty = true;
    }
    if (normalizeStaffRole(user) === 'super' && String(user.staffRole || '').toLowerCase() !== 'super') {
      user.staffRole = 'super';
      dirty = true;
    }
    if (dirty) await this.usersRepo.save(user);

    const accessToken = await this.jwtService.signAsync({
      sub: user.id,
      username: user.username,
      isAdmin: true,
      isSuperAdmin: true,
      staffRole: 'super',
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
        isSuperAdmin: true,
        staffRole: 'super',
        role: 'admin',
      },
    };
  }

  async adminMe(userId: string) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user || !isDashboardSuper(user)) {
      throw new UnauthorizedException('Super admin access required');
    }
    return {
      id: user.id,
      email: user.email,
      username: user.username,
      displayName: user.displayName,
      name: user.displayName,
      isAdmin: true,
      isSuperAdmin: true,
      staffRole: 'super',
      role: 'admin',
    };
  }

  /**
   * Super-admin only: update dashboard login email and/or password.
   * Requires current password. Mirrors into admin_users when linked.
   */
  async updateAdminCredentials(
    userId: string,
    dto: {
      currentPassword: string;
      newEmail?: string;
      newPassword?: string;
    },
  ) {
    const user = await this.usersRepo
      .createQueryBuilder('u')
      .addSelect('u.passwordHash')
      .where('u.id = :id', { id: userId })
      .getOne();
    if (!user || !isDashboardSuper(user)) {
      throw new UnauthorizedException('Super admin access required');
    }
    if (!user.passwordHash) {
      throw new BadRequestException('This account has no password set');
    }
    const ok = await bcrypt.compare(String(dto.currentPassword || ''), user.passwordHash);
    if (!ok) {
      throw new UnauthorizedException('Current password is incorrect');
    }

    const nextEmailRaw = dto.newEmail != null ? String(dto.newEmail).trim().toLowerCase() : '';
    const nextPassword = dto.newPassword != null ? String(dto.newPassword) : '';
    if (!nextEmailRaw && !nextPassword) {
      throw new BadRequestException('Provide a new email and/or a new password');
    }

    if (nextEmailRaw) {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(nextEmailRaw)) {
        throw new BadRequestException('Invalid email address');
      }
      if (nextEmailRaw !== String(user.email || '').toLowerCase()) {
        const taken = await this.usersRepo
          .createQueryBuilder('u')
          .where('LOWER(u.email) = :email', { email: nextEmailRaw })
          .andWhere('u.id != :id', { id: user.id })
          .getOne();
        if (taken) {
          throw new ConflictException('Email is already in use');
        }
        user.email = nextEmailRaw;
      }
    }

    let passwordHash: string | null = null;
    if (nextPassword) {
      if (nextPassword.length < 8) {
        throw new BadRequestException('New password must be at least 8 characters');
      }
      passwordHash = await bcrypt.hash(nextPassword, 12);
      user.passwordHash = passwordHash;
    }

    await this.usersRepo.save(user);

    // Keep legacy admin_users table in sync when present.
    const linked = await this.adminUsersRepo.find({
      where: [{ linkedUserId: user.id }],
    });
    for (const row of linked) {
      if (nextEmailRaw) row.email = nextEmailRaw;
      if (passwordHash) row.passwordHash = passwordHash;
      await this.adminUsersRepo.save(row);
    }

    return {
      ok: true,
      email: user.email,
      username: user.username,
      message: 'Admin credentials updated',
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

    const providerRows = await this.rechargeRepo
      .createQueryBuilder('r')
      .select('r.provider', 'provider')
      .addSelect('COALESCE(SUM(r.amountFiat),0)', 'total')
      .addSelect('COUNT(*)', 'count')
      .where('r.status = :s', { s: RechargeStatus.COMPLETED })
      .groupBy('r.provider')
      .getRawMany();
    const byProvider = (providerRows || [])
      .map((row: any) => ({
        provider: String(row.provider || 'other'),
        label: this.providerRevenueLabel(String(row.provider || 'other')),
        total: Number(row.total || 0),
        count: Number(row.count || 0),
      }))
      .filter((r) => r.total > 0 || r.count > 0)
      .sort((a, b) => b.total - a.total);
    // Doughnut = real fiat revenue by payment channel (Play / card / USDT / …).
    const revLabels = byProvider.map((r) => r.label);
    const revValues = byProvider.map((r) => r.total);
    if (revLabels.length === 0) {
      revLabels.push('—');
      revValues.push(0);
    }

    return {
      labels,
      users: {
        labels,
        users: usersSeries,
        data: usersSeries,
      },
      revenue: {
        labels: revLabels,
        values: revValues,
        data: revValues,
        byProvider,
        // Keep coin totals for mini-stats (not revenue).
        coinVolume: {
          labels: ['Recharge', 'Gifts', 'VIP'],
          values: [rechargeVal, giftsVal, vipVal],
        },
      },
      rooms: {
        labels,
        data: roomsSeries,
      },
    };
  }

  async getUser(id: string) {
    await this.ensureStaffRoleColumn();
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
    const frameByUser = await this.equippedFramesByUserIds([id]);
    const equipped = frameByUser.get(id) || null;
    const profileFrame =
      (user.profile as any)?.vipBadgeUrl ||
      (user.profile as any)?.hostBadgeUrl ||
      null;
    const frameUrl = equipped || profileFrame || null;
    mapped.frameUrl = frameUrl;
    mapped.frameAnimUrl = equipped || frameUrl;
    return mapped;
  }

  async patchUser(id: string, body: Record<string, unknown>) {
    await this.ensureStaffRoleColumn();
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
    if (typeof body.genderVerified === 'boolean') user.genderVerified = body.genderVerified;

    // Platform staff role (manager / super / none).
    if (body.staffRole !== undefined && body.staffRole !== null) {
      const role = normalizeStaffRole({
        staffRole: String(body.staffRole),
        isAdmin: false,
      });
      if (role === 'none') {
        user.staffRole = null;
        // Clearing role also clears isAdmin unless explicitly kept below.
        if (body.isAdmin === undefined) user.isAdmin = false;
      } else {
        user.staffRole = role;
        // Super can open dashboard (isAdmin). Manager is in-app staff only.
        if (body.isAdmin === undefined) {
          user.isAdmin = role === 'super';
        }
      }
    } else if (typeof body.isAdmin === 'boolean') {
      user.isAdmin = body.isAdmin;
      if (body.isAdmin && normalizeStaffRole(user) === 'none') {
        user.staffRole = 'super';
      }
      if (!body.isAdmin && normalizeStaffRole(user) === 'super') {
        // Only drop staffRole when it came from isAdmin/super path.
        if (String(user.staffRole || '').toLowerCase() === 'super' || !user.staffRole) {
          user.staffRole = null;
        }
      }
    }

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
        providerLabel:
          r.provider === 'sham_cash'
            ? 'شام كاش'
            : r.provider === 'fourthwall'
              ? 'بطاقة'
              : r.provider === 'google_play'
                ? 'جوجل بلاي'
                : r.provider === 'binance_wallet' || r.provider === 'binance_pay'
                  ? 'USDT'
                  : r.provider === 'recharge_agent'
                    ? 'وكيل شحن'
                    : r.provider === 'crypto'
                      ? 'USDT'
                      : r.provider === 'admin'
                        ? 'أدمن'
                        : r.provider === 'stripe'
                          ? 'Stripe'
                          : r.provider === 'paypal'
                            ? 'PayPal'
                            : r.provider,
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async completeRechargeOrder(orderId: string, providerPaymentId?: string, note?: string) {
    const order = await this.rechargeRepo.findOne({ where: { id: orderId } });
    if (!order) throw new NotFoundException('طلب الشحن غير موجود');
    if (order.status !== RechargeStatus.PENDING) {
      throw new BadRequestException('الطلب ليس معلّقاً');
    }
    const completed = await this.walletService.completeRecharge(
      orderId,
      providerPaymentId || `admin_confirm_${Date.now()}`,
    );
    if (note) {
      completed.providerPayload = {
        ...(completed.providerPayload || {}),
        adminNote: note,
        confirmedAt: new Date().toISOString(),
      };
      await this.rechargeRepo.save(completed);
    }
    return completed;
  }

  async cancelRechargeOrder(orderId: string) {
    const order = await this.rechargeRepo.findOne({ where: { id: orderId } });
    if (!order) throw new NotFoundException('طلب الشحن غير موجود');
    if (order.status !== RechargeStatus.PENDING) {
      throw new BadRequestException('الطلب ليس معلّقاً');
    }
    order.status = RechargeStatus.CANCELLED;
    return this.rechargeRepo.save(order);
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
      publicId: string | null;
      isVerified: boolean;
      exclusiveFrameCode: string | null;
      exclusiveRoomCardCode: string | null;
      exclusiveFrameUrl: string | null;
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

    // Admin-only short public ID.
    if (dto.publicId !== undefined) {
      if (dto.publicId === null || String(dto.publicId).trim() === '') {
        agency.publicId = null;
      } else {
        if (!isValidAgencyPublicId(String(dto.publicId))) {
          throw new BadRequestException(
            'معرف الوكالة: حروف وأرقام إنجليزية فقط، 3–12 خانة',
          );
        }
        const next = normalizeAgencyPublicId(String(dto.publicId));
        if (next !== agency.publicId) {
          const clash = await this.agencyRepo.findOne({
            where: { publicId: next },
            select: ['id'],
          });
          if (clash && clash.id !== agency.id) {
            throw new ConflictException(`معرف الوكالة ${next} مستخدم مسبقاً`);
          }
          agency.publicId = next;
        }
      }
    }

    // Verification badge — admin only.
    if (dto.isVerified !== undefined) {
      const next = !!dto.isVerified;
      agency.isVerified = next;
      agency.verifiedAt = next ? agency.verifiedAt || new Date() : null;
      if (next && !agency.publicId) {
        agency.publicId = await this.agenciesService.allocateAgencyPublicId();
      }
    }

    if (dto.exclusiveFrameCode !== undefined) {
      agency.exclusiveFrameCode = dto.exclusiveFrameCode
        ? String(dto.exclusiveFrameCode).trim()
        : null;
    }
    if (dto.exclusiveRoomCardCode !== undefined) {
      agency.exclusiveRoomCardCode = dto.exclusiveRoomCardCode
        ? String(dto.exclusiveRoomCardCode).trim()
        : null;
    }
    if (dto.exclusiveFrameUrl !== undefined) {
      agency.exclusiveFrameUrl = dto.exclusiveFrameUrl
        ? String(dto.exclusiveFrameUrl).trim()
        : null;
    }

    return this.agencyRepo.save(agency);
  }

  /**
   * Grant agency-exclusive cosmetics to owner (+ managers/hosts optionally).
   * Mall purchase is blocked for these items.
   */
  async grantAgencyExclusives(
    id: string,
    body?: {
      frameCode?: string;
      roomCardCode?: string;
      days?: number;
      includeManagers?: boolean;
      includeHosts?: boolean;
      equip?: boolean;
    },
  ) {
    const agency = await this.agencyRepo.findOne({ where: { id } });
    if (!agency) throw new NotFoundException('الوكالة غير موجودة');
    if (agency.status !== AgencyStatus.ACTIVE) {
      throw new BadRequestException('الوكالة يجب أن تكون نشطة لمنح الحوافز الحصرية');
    }

    const frameCode =
      (body?.frameCode && String(body.frameCode).trim()) ||
      agency.exclusiveFrameCode ||
      '';
    const roomCardCode =
      (body?.roomCardCode && String(body.roomCardCode).trim()) ||
      agency.exclusiveRoomCardCode ||
      '';
    if (!frameCode && !roomCardCode) {
      throw new BadRequestException(
        'حدّدي رمز إطار حصري (frame) و/أو بطاقة روم (room card)',
      );
    }

    if (frameCode) agency.exclusiveFrameCode = frameCode;
    if (roomCardCode) agency.exclusiveRoomCardCode = roomCardCode;
    await this.agencyRepo.save(agency);

    const roles: AgencyRole[] = [AgencyRole.OWNER];
    if (body?.includeManagers !== false) roles.push(AgencyRole.MANAGER);
    if (body?.includeHosts) roles.push(AgencyRole.HOST);

    const members = await this.agencyMembersRepo.find({
      where: {
        agencyId: id,
        isActive: true,
        status: AgencyMemberStatus.ACTIVE,
        role: In(roles),
      },
    });
    // Always include owner even if membership row is odd.
    const userIds = new Set<string>([agency.ownerId, ...members.map((m) => m.userId)]);
    const days = Math.max(7, Math.min(365, Math.floor(Number(body?.days) || 90)));
    const granted: Array<{ userId: string; code: string; ok: boolean; error?: string }> = [];

    for (const userId of userIds) {
      for (const code of [frameCode, roomCardCode].filter(Boolean)) {
        try {
          await this.cosmeticsService.grantTemporary(userId, code, days);
          if (body?.equip !== false) {
            // grantTemporary already tries equip — best effort
          }
          granted.push({ userId, code, ok: true });
        } catch (e) {
          granted.push({
            userId,
            code,
            ok: false,
            error: e instanceof Error ? e.message : String(e),
          });
        }
      }
    }

    try {
      await this.notificationsService.create({
        userId: agency.ownerId,
        type: NotificationType.AGENCY,
        title: 'حوافز حصرية للوكالة',
        body: `تم منح وكالتك «${agency.name}» إطارات/بطاقات حصرية من إدارة التطبيق.`,
        data: {
          agencyId: agency.id,
          publicId: agency.publicId,
          exclusiveFrameCode: agency.exclusiveFrameCode,
          exclusiveRoomCardCode: agency.exclusiveRoomCardCode,
        },
        sendPush: true,
      });
    } catch {
      /* ignore */
    }

    return {
      agencyId: agency.id,
      publicId: agency.publicId,
      exclusiveFrameCode: agency.exclusiveFrameCode,
      exclusiveRoomCardCode: agency.exclusiveRoomCardCode,
      days,
      granted,
      okCount: granted.filter((g) => g.ok).length,
    };
  }

  async approveAgency(id: string) {
    const current = await this.agencyRepo.findOne({ where: { id } });
    if (!current) throw new NotFoundException('الوكالة غير موجودة');
    // Auto-assign shareable publicId if missing when activating.
    if (!current.publicId) {
      current.publicId = await this.agenciesService.allocateAgencyPublicId();
      await this.agencyRepo.save(current);
    }
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
    // Suspend + revoke verified badge until policy check again.
    const agency = await this.updateAgency(id, {
      status: AgencyStatus.SUSPENDED,
      isVerified: false,
    });
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

  /** Live host / process metrics for admin dashboard cards. */
  async systemHealth() {
    const os = await import('os');
    const fs = await import('fs/promises');
    const totalMem = os.totalmem();
    const freeMem = os.freemem();
    const usedMem = Math.max(0, totalMem - freeMem);
    const memPct = totalMem > 0 ? (usedMem / totalMem) * 100 : 0;

    const cpus = os.cpus() || [];
    const cores = cpus.length || 1;
    let cpuPct = 0;
    try {
      const start = process.cpuUsage();
      const t0 = process.hrtime.bigint();
      await new Promise((r) => setTimeout(r, 140));
      const diff = process.cpuUsage(start);
      const elapsedUs = Number(process.hrtime.bigint() - t0) / 1000;
      if (elapsedUs > 0) {
        const usedUs = diff.user + diff.system;
        // process share of one core → normalize by cores for “machine feel”
        cpuPct = Math.min(100, (usedUs / elapsedUs) * 100);
        // scale: process-only can look tiny — blend with 1-min load when available
        const load1 = os.loadavg()?.[0];
        if (Number.isFinite(load1) && load1 > 0) {
          const loadPct = Math.min(100, (load1 / cores) * 100);
          cpuPct = Math.max(cpuPct, loadPct * 0.85);
        }
      }
    } catch {
      const load1 = os.loadavg()?.[0] ?? 0;
      cpuPct = Math.min(100, (load1 / cores) * 100);
    }

    let disk: {
      totalBytes: number;
      freeBytes: number;
      usedBytes: number;
      usedPercent: number;
      path: string;
    } | null = null;
    const diskPath = process.cwd();
    try {
      const statfs = (fs as any).statfs;
      if (typeof statfs === 'function') {
        const s = await statfs(diskPath);
        const bsize = Number(s.bsize || s.blocksize || 4096);
        const totalBytes = bsize * Number(s.blocks || 0);
        const freeBytes = bsize * Number(s.bavail ?? s.bfree ?? 0);
        const usedBytes = Math.max(0, totalBytes - freeBytes);
        if (totalBytes > 0) {
          disk = {
            totalBytes,
            freeBytes,
            usedBytes,
            usedPercent: (usedBytes / totalBytes) * 100,
            path: diskPath,
          };
        }
      }
    } catch {
      disk = null;
    }

    const heap = process.memoryUsage();
    const [logsTotal, logsCritical, logsUnresolved] = await Promise.all([
      this.abuseRepo.count(),
      this.abuseRepo.count({ where: { severity: AbuseSeverity.CRITICAL } }),
      this.abuseRepo.count({ where: { resolved: false } }),
    ]);

    const host = os.hostname();
    const platform = `${os.type()} ${os.release()}`;
    const uptimeSec = Math.floor(os.uptime());
    const processUptimeSec = Math.floor(process.uptime());

    return {
      status: memPct >= 92 || cpuPct >= 95 ? 'critical' : memPct >= 80 || cpuPct >= 85 ? 'warn' : 'ok',
      host,
      platform,
      node: process.version,
      arch: os.arch(),
      cores,
      uptimeSec,
      processUptimeSec,
      cpu: {
        percent: Math.round(cpuPct * 10) / 10,
        model: cpus[0]?.model?.trim() || 'CPU',
        cores,
        loadAvg: os.loadavg(),
      },
      memory: {
        totalBytes: totalMem,
        freeBytes: freeMem,
        usedBytes: usedMem,
        usedPercent: Math.round(memPct * 10) / 10,
      },
      process: {
        rssBytes: heap.rss,
        heapUsedBytes: heap.heapUsed,
        heapTotalBytes: heap.heapTotal,
        externalBytes: heap.external,
      },
      disk,
      logs: {
        total: logsTotal,
        critical: logsCritical,
        unresolved: logsUnresolved,
      },
      checkedAt: new Date().toISOString(),
    };
  }

  /**
   * Purge abuse/system log rows.
   * - olderThanDays > 0 → delete older than N days
   * - olderThanDays omitted/0 → delete matching set (or all when no other filters)
   */
  async cleanupLogs(opts: {
    olderThanDays?: number;
    unresolvedOnly?: boolean;
    resolvedOnly?: boolean;
  } = {}) {
    const days = Math.max(0, Math.floor(Number(opts.olderThanDays ?? 0)));
    const where: Record<string, unknown> = {};

    if (days > 0) {
      const cutoff = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
      where.createdAt = LessThan(cutoff);
    }
    if (opts.resolvedOnly) {
      where.resolved = true;
    } else if (opts.unresolvedOnly) {
      where.resolved = false;
    }

    // Guard: wiping everything requires explicit empty filter + olderThanDays 0
    // (allowed — admin confirmed from UI)
    if (Object.keys(where).length === 0) {
      const result = await this.abuseRepo
        .createQueryBuilder()
        .delete()
        .from(AbuseLog)
        .execute();
      const deleted = Number(result.affected || 0);
      const remaining = await this.abuseRepo.count();
      return { deleted, remaining, olderThanDays: null };
    }

    const result = await this.abuseRepo.delete(where as any);
    const deleted = Number(result.affected || 0);
    const remaining = await this.abuseRepo.count();
    return { deleted, remaining, olderThanDays: days || null };
  }
}
