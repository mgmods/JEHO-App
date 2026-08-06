import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ConflictException,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { User, Gender } from '../../database/entities/user.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Block } from '../../database/entities/block.entity';
import {
  Report,
  ReportStatus,
  ReportTargetType,
} from '../../database/entities/report.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import {
  SocialRequest,
  SocialRequestStatus,
  SocialRequestType,
} from '../../database/entities/social-request.entity';
import { ProfileVisit } from '../../database/entities/profile-visit.entity';
import { Cosmetic } from '../../database/entities/cosmetic.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { UpdateProfileDto, ReportUserDto } from './dto/users.dto';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { NotificationsService } from '../notifications/notifications.service';
import { normalizeStaffRole } from '../../common/staff-role';
import { NotificationType } from '../../database/entities/notification.entity';
import { TasksService } from '../tasks/tasks.service';
import { MediaCleanupService } from '../uploads/media-cleanup.service';
import { IdentityVerificationService } from './identity-verification.service';
import { ContentModerationService } from '../moderation/content-moderation.service';
import { HOST_NEW_USER_CHAT, levelFromScore, MAX_ECONOMY_LEVEL } from '../../common/pricing-catalog';
import { effectiveVipLevel } from '../../common/vip-progress';

const LEVEL_THRESHOLDS = [
  0, 100, 500, 1500, 4000, 10000, 25000, 60000, 150000, 400000, 1000000,
];

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(UserProfile) private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(Follow) private readonly followsRepo: Repository<Follow>,
    @InjectRepository(Block) private readonly blocksRepo: Repository<Block>,
    @InjectRepository(Report) private readonly reportsRepo: Repository<Report>,
    @InjectRepository(UserVip) private readonly userVipsRepo: Repository<UserVip>,
    @InjectRepository(SocialRequest) private readonly socialRepo: Repository<SocialRequest>,
    @InjectRepository(ProfileVisit) private readonly visitsRepo: Repository<ProfileVisit>,
    @InjectRepository(Cosmetic) private readonly cosmeticsRepo: Repository<Cosmetic>,
    private readonly dataSource: DataSource,
    private readonly realtimeGateway: RealtimeGateway,
    private readonly mediaCleanup: MediaCleanupService,
    private readonly identityVerification: IdentityVerificationService,
    private readonly moderation: ContentModerationService,
    @Optional() private readonly notifications?: NotificationsService,
    @Optional() private readonly tasksService?: TasksService,
  ) {}

  private economyStats(profile?: UserProfile | null) {
    const received = Math.max(0, Number(profile?.totalReceivedDiamonds ?? 0));
    const sent = Math.max(0, Number(profile?.totalSentCoins ?? 0));
    const popularityLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(received));
    const wealthLevel = Math.min(MAX_ECONOMY_LEVEL, levelFromScore(sent));
    return {
      charmScore: popularityLevel,
      /** شعبية: مستوى عرض 1–600 (ليس الرقم الخام). */
      popularityScore: popularityLevel,
      popularityLevel,
      wealthScore: wealthLevel,
      /** ثروة: مستوى عرض 1–600 (ليس الرقم الخام). */
      wealthLevel,
      totalReceivedDiamonds: received,
      totalSentCoins: sent,
    };
  }

  /** First-day Hi badge + new-user window for hostess engagement tasks. */
  private newUserFlags(user: User) {
    const createdAt = user.createdAt ? new Date(user.createdAt) : null;
    const ageMs = createdAt ? Date.now() - createdAt.getTime() : Number.POSITIVE_INFINITY;
    const ageHours = ageMs / (60 * 60 * 1000);
    const isFirstDay = Number.isFinite(ageHours) && ageHours <= HOST_NEW_USER_CHAT.firstDayHours;
    const isNewUser =
      Number.isFinite(ageHours) && ageHours <= HOST_NEW_USER_CHAT.newUserDays * 24;
    const gender = String(user.gender || '').toLowerCase();
    return {
      createdAt: createdAt?.toISOString() ?? null,
      isFirstDay,
      showHiBadge: isFirstDay,
      isNewUser,
      isNewMale: isNewUser && gender === 'male',
    };
  }

  private async wearMetaForUrl(url?: string | null) {
    if (!url) return null;
    const row = await this.cosmeticsRepo.findOne({ where: { previewUrl: url } });
    return (row?.meta as Record<string, unknown> | null) ?? null;
  }

  private async withWearMeta<T extends Record<string, unknown>>(flat: T): Promise<T & {
    hostBadgeMeta: Record<string, unknown> | null;
  }> {
    const hostBadgeMeta = await this.wearMetaForUrl(
      flat.hostBadgeUrl as string | null | undefined,
    );
    return { ...flat, hostBadgeMeta };
  }

  private flatten(user: User) {
    const p = user.profile;
    const economy = this.economyStats(p);
    const newbie = this.newUserFlags(user);
    const staffRole = normalizeStaffRole(user);
    return {
      ...user,
      staffRole,
      isAdmin: staffRole === 'super',
      isSuperAdmin: staffRole === 'super',
      bio: p?.bio ?? user['bio'] ?? null,
      coverUrl: p?.coverUrl ?? null,
      entryEffectUrl: p?.entryEffectUrl ?? null,
      entryAnimationUrl: p?.entryAnimationUrl ?? null,
      roomCardUrl: p?.roomCardUrl ?? null,
      vipBadgeUrl: p?.vipBadgeUrl ?? null,
      levelBadgeUrl: p?.levelBadgeUrl ?? null,
      hostBadgeUrl: p?.hostBadgeUrl ?? null,
      country: p?.country ?? null,
      countryChangedAt: p?.countryChangedAt
        ? new Date(p.countryChangedAt).toISOString()
        : null,
      countryChangeAvailableAt: (() => {
        const changedAt = p?.countryChangedAt
          ? new Date(p.countryChangedAt).getTime()
          : p?.country && user.createdAt
            ? new Date(user.createdAt).getTime()
            : null;
        if (!changedAt) return null;
        const next = changedAt + 30 * 24 * 60 * 60 * 1000;
        return new Date(next).toISOString();
      })(),
      city: p?.city ?? null,
      albumUrls: p?.albumUrls ?? [],
      followersCount: p?.followersCount ?? 0,
      followingCount: p?.followingCount ?? 0,
      friendsCount: p?.friendsCount ?? 0,
      ...economy,
      ...newbie,
      showOnlineStatus: p?.showOnlineStatus ?? true,
      allowDmFromStrangers: p?.allowDmFromStrangers ?? true,
      dmGiftGateEnabled: p?.dmGiftGateEnabled ?? false,
      dmRequiredGiftId: p?.dmRequiredGiftId ?? null,
      lastSeenAt: user.updatedAt,
    };
  }

  async getMe(userId: string) {
    const user = await this.usersRepo.findOne({
      where: { id: userId },
      relations: ['profile', 'wallet'],
    });
    if (!user) throw new NotFoundException('User not found');
    const now = new Date();
    const vip = await this.userVipsRepo.findOne({
      where: { userId, isActive: true },
      relations: ['vipPlan'],
      order: { level: 'DESC' },
    });
    const vipValid = vip && (!vip.expiresAt || vip.expiresAt > now) ? vip : null;
    const verification = await this.identityVerification.getStatus(userId);
    return this.withWearMeta({
      ...this.flatten(user),
      activeVip: vipValid,
      vipLevel: effectiveVipLevel(vipValid?.level ?? 0, Number(user.profile?.totalSentCoins || 0)),
      genderVerified: verification.genderVerified,
      genderVerificationStatus: verification.genderVerificationStatus,
    });
  }

  async getById(id: string, viewerId?: string) {
    const user = await this.usersRepo.findOne({
      where: { id },
      relations: ['profile'],
    });
    if (!user) throw new NotFoundException('User not found');
    const now = new Date();
    const vip = await this.userVipsRepo.findOne({
      where: { userId: id, isActive: true },
      order: { level: 'DESC' },
    });
    const vipLevel =
      vip && (!vip.expiresAt || vip.expiresAt > now)
        ? Number(vip.level || 0)
        : 0;
    let isFollowing = false;
    if (viewerId && viewerId !== id) {
      const follow = await this.followsRepo.findOne({
        where: { followerId: viewerId, followingId: id },
      });
      isFollowing = !!follow;
    }
    return this.withWearMeta({
      ...this.flatten(user),
      vipLevel: effectiveVipLevel(vipLevel, Number(user.profile?.totalSentCoins || 0)),
      isFollowing,
    });
  }

  async getByUsername(username: string) {
    const key = username.trim();
    const user = await this.usersRepo.findOne({
      where: [{ username: key.toLowerCase() }, { publicId: key }],
      relations: ['profile'],
    });
    if (!user) throw new NotFoundException('User not found');
    return this.withWearMeta(this.flatten(user));
  }

  async updateProfile(userId: string, dto: UpdateProfileDto) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    if (dto.displayName !== undefined) {
      this.moderation.assertNoAgencyImpersonation(dto.displayName, 'الاسم الظاهر');
      user.displayName = dto.displayName;
    }
    if (dto.avatarUrl !== undefined) {
      this.mediaCleanup.replaceUpload(user.avatarUrl, dto.avatarUrl);
      user.avatarUrl = dto.avatarUrl;
    }
    if (dto.gender !== undefined) {
      const prev = (user.gender || Gender.UNSPECIFIED).toLowerCase();
      const next = (dto.gender || Gender.UNSPECIFIED).toLowerCase();
      const hadGender =
        prev && prev !== Gender.UNSPECIFIED && prev !== Gender.OTHER;
      if (hadGender && next !== prev) {
        throw new BadRequestException('لا يمكن تغيير الجنس بعد التسجيل');
      }
      if (!hadGender && next && next !== Gender.UNSPECIFIED) {
        user.gender = dto.gender as Gender;
        if (next === Gender.FEMALE) {
          user.genderVerified = false;
        }
      }
    }
    if (dto.birthday !== undefined) user.birthday = new Date(dto.birthday);
    await this.usersRepo.save(user);

    let profile = await this.profilesRepo.findOne({ where: { userId } });
    if (!profile) {
      profile = this.profilesRepo.create({ userId });
    }
    if (dto.bio !== undefined) {
      this.moderation.assertNoAgencyImpersonation(dto.bio, 'النبذة');
      profile.bio = dto.bio;
    }
    if (dto.country !== undefined) {
      const nextCountry = (dto.country || '').trim();
      const prevCountry = (profile.country || '').trim();
      if (nextCountry && nextCountry !== prevCountry) {
        if (prevCountry) {
          const changedAt = profile.countryChangedAt
            ? new Date(profile.countryChangedAt).getTime()
            : user.createdAt
              ? new Date(user.createdAt).getTime()
              : 0;
          if (changedAt > 0) {
            const elapsed = Date.now() - changedAt;
            const cooldownMs = 30 * 24 * 60 * 60 * 1000;
            if (elapsed < cooldownMs) {
              const daysLeft = Math.ceil((cooldownMs - elapsed) / (24 * 60 * 60 * 1000));
              throw new BadRequestException(
                `لا يمكنك تبديل البلدان إلا مرة واحدة كل 30 يوماً. متبقي ${daysLeft} يوم`,
              );
            }
          }
        }
        profile.country = nextCountry;
        profile.countryChangedAt = new Date();
        // Mikoo: changing country clears personal ranking display totals.
        profile.totalSentCoins = 0;
        profile.totalReceivedDiamonds = 0;
      } else if (!prevCountry && nextCountry) {
        profile.country = nextCountry;
        profile.countryChangedAt = new Date();
      }
    }
    if (dto.city !== undefined) profile.city = dto.city;
    if (dto.language !== undefined) profile.language = dto.language;
    if (dto.interests !== undefined) profile.interests = dto.interests;
    if (dto.albumUrls !== undefined) {
      const oldAlbum = Array.isArray(profile.albumUrls) ? profile.albumUrls : [];
      const nextAlbum = Array.isArray(dto.albumUrls) ? dto.albumUrls : [];
      for (const url of oldAlbum) {
        if (!nextAlbum.includes(url)) this.mediaCleanup.deleteUploadUrl(url);
      }
      profile.albumUrls = nextAlbum;
    }
    if (dto.coverUrl !== undefined) {
      this.mediaCleanup.replaceUpload(profile.coverUrl, dto.coverUrl);
      profile.coverUrl = dto.coverUrl;
    }
    if (dto.showOnlineStatus !== undefined) profile.showOnlineStatus = dto.showOnlineStatus;
    if (dto.allowDmFromStrangers !== undefined) {
      profile.allowDmFromStrangers = dto.allowDmFromStrangers;
    }
    if (dto.dmGiftGateEnabled !== undefined) {
      profile.dmGiftGateEnabled = !!dto.dmGiftGateEnabled;
    }
    if (dto.dmRequiredGiftId !== undefined) {
      profile.dmRequiredGiftId = dto.dmRequiredGiftId || null;
    }
    await this.profilesRepo.save(profile);

    return this.getMe(userId);
  }

  async follow(followerId: string, followingId: string) {
    if (followerId === followingId) {
      throw new BadRequestException('Cannot follow yourself');
    }
    const target = await this.usersRepo.findOne({ where: { id: followingId } });
    if (!target) throw new NotFoundException('User not found');

    const blocked = await this.blocksRepo.findOne({
      where: [
        { blockerId: followingId, blockedId: followerId },
        { blockerId: followerId, blockedId: followingId },
      ],
    });
    if (blocked) throw new BadRequestException('Cannot follow this user');

    const existing = await this.followsRepo.findOne({
      where: { followerId, followingId },
    });
    if (existing) throw new ConflictException('Already following');

    await this.followsRepo.save(this.followsRepo.create({ followerId, followingId }));
    await this.profilesRepo.increment({ userId: followingId }, 'followersCount', 1);
    await this.profilesRepo.increment({ userId: followerId }, 'followingCount', 1);
    const follower = await this.usersRepo.findOne({ where: { id: followerId } });
    const name = follower?.displayName || follower?.username || 'مستخدم';
    void this.tasksService?.recordProgress(followerId, 'follow', 1).catch(() => undefined);
    void this.notifications?.notifyUser(
      followingId,
      NotificationType.FOLLOW,
      'متابع جديد',
      `${name} بدأ بمتابعتك`,
      { type: 'follow', userId: followerId },
    );
    return { following: true };
  }

  async unfollow(followerId: string, followingId: string) {
    const result = await this.followsRepo.delete({ followerId, followingId });
    if (result.affected) {
      await this.profilesRepo.decrement({ userId: followingId }, 'followersCount', 1);
      await this.profilesRepo.decrement({ userId: followerId }, 'followingCount', 1);
    }
    return { following: false };
  }

  async listFollowers(userId: string, query: PaginationDto) {
    const [items, total] = await this.followsRepo.findAndCount({
      where: { followingId: userId },
      relations: ['follower', 'follower.profile'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    const users = items.map((f) => this.publicUser(f.follower));
    return paginate(users, total, query.page || 1, query.limit || 20);
  }

  async listFollowing(userId: string, query: PaginationDto) {
    const [items, total] = await this.followsRepo.findAndCount({
      where: { followerId: userId },
      relations: ['following', 'following.profile'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    const users = items.map((f) => this.publicUser(f.following));
    return paginate(users, total, query.page || 1, query.limit || 20);
  }

  private publicUser(user: User | null | undefined) {
    if (!user) return null;
    const economy = this.economyStats(user.profile);
    const newbie = this.newUserFlags(user);
    const activeVip = Number((user as any).vipLevel ?? 0);
    const vipLevel = effectiveVipLevel(activeVip, economy.totalSentCoins);
    return {
      id: user.id,
      username: user.username,
      publicId: user.publicId,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      gender: user.gender,
      genderVerified: !!user.genderVerified,
      level: user.level,
      vipLevel,
      bio: user.profile?.bio ?? null,
      coverUrl: user.profile?.coverUrl ?? null,
      entryEffectUrl: user.profile?.entryEffectUrl ?? null,
      entryAnimationUrl: user.profile?.entryAnimationUrl ?? null,
      roomCardUrl: user.profile?.roomCardUrl ?? null,
      vipBadgeUrl: user.profile?.vipBadgeUrl ?? null,
      levelBadgeUrl: user.profile?.levelBadgeUrl ?? null,
      hostBadgeUrl: user.profile?.hostBadgeUrl ?? null,
      country: user.profile?.country ?? null,
      followersCount: user.profile?.followersCount ?? 0,
      followingCount: user.profile?.followingCount ?? 0,
      friendsCount: user.profile?.friendsCount ?? 0,
      ...economy,
      ...newbie,
    };
  }

  private async removeFriendship(userA: string, userB: string) {
    const rows = await this.socialRepo.find({
      where: [
        {
          fromUserId: userA,
          toUserId: userB,
          type: SocialRequestType.FRIEND,
          status: SocialRequestStatus.ACCEPTED,
        },
        {
          fromUserId: userB,
          toUserId: userA,
          type: SocialRequestType.FRIEND,
          status: SocialRequestStatus.ACCEPTED,
        },
      ],
    });
    if (!rows.length) return false;
    for (const row of rows) {
      row.status = SocialRequestStatus.REJECTED;
      await this.socialRepo.save(row);
    }
    await this.profilesRepo
      .createQueryBuilder()
      .update(UserProfile)
      .set({ friendsCount: () => 'GREATEST("friendsCount" - 1, 0)' })
      .where('"userId" IN (:...ids)', { ids: [userA, userB] })
      .execute();
    return true;
  }

  async unfriend(userId: string, friendId: string) {
    if (userId === friendId) throw new BadRequestException('Invalid friend');
    const removed = await this.removeFriendship(userId, friendId);
    if (!removed) throw new NotFoundException('Friendship not found');
    return { unfriended: true };
  }

  /** End an accepted social bond (relation / guardian / friend) with a peer. */
  async endBond(userId: string, peerId: string, type?: SocialRequestType) {
    if (!peerId || userId === peerId) throw new BadRequestException('Invalid peer');
    const bondType = type || SocialRequestType.RELATION;
    if (bondType === SocialRequestType.FRIEND) {
      return this.unfriend(userId, peerId);
    }
    const rows = await this.socialRepo.find({
      where: [
        {
          fromUserId: userId,
          toUserId: peerId,
          type: bondType,
          status: SocialRequestStatus.ACCEPTED,
        },
        {
          fromUserId: peerId,
          toUserId: userId,
          type: bondType,
          status: SocialRequestStatus.ACCEPTED,
        },
      ],
    });
    if (!rows.length) throw new NotFoundException('Bond not found');
    for (const row of rows) {
      row.status = SocialRequestStatus.REJECTED;
      await this.socialRepo.save(row);
    }
    this.realtimeGateway.emitToUser(peerId, 'social:bond_ended', {
      peerId: userId,
      type: bondType,
      at: new Date().toISOString(),
    });
    return { ended: true, type: bondType };
  }

  async block(blockerId: string, blockedId: string, reason?: string) {
    if (blockerId === blockedId) throw new BadRequestException('Cannot block yourself');
    await this.followsRepo.delete({ followerId: blockerId, followingId: blockedId });
    await this.followsRepo.delete({ followerId: blockedId, followingId: blockerId });
    await this.removeFriendship(blockerId, blockedId);
    const existing = await this.blocksRepo.findOne({ where: { blockerId, blockedId } });
    if (existing) return existing;
    return this.blocksRepo.save(
      this.blocksRepo.create({ blockerId, blockedId, reason: reason || null }),
    );
  }

  async unblock(blockerId: string, blockedId: string) {
    await this.blocksRepo.delete({ blockerId, blockedId });
    return { blocked: false };
  }

  async listBlocks(blockerId: string, query: PaginationDto) {
    const [items, total] = await this.blocksRepo.findAndCount({
      where: { blockerId },
      relations: ['blocked', 'blocked.profile'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    const users = items.map((b) => this.publicUser(b.blocked));
    return paginate(users, total, query.page || 1, query.limit || 20);
  }

  async report(reporterId: string, dto: ReportUserDto) {
    if (dto.targetType !== ReportTargetType.USER) {
      throw new BadRequestException('Use the target-specific report endpoint');
    }
    if (reporterId === dto.targetId) {
      throw new BadRequestException('Cannot report yourself');
    }
    const target = await this.usersRepo.findOne({ where: { id: dto.targetId } });
    if (!target) throw new NotFoundException('Reported user not found');
    const existing = await this.reportsRepo.findOne({
      where: {
        reporterId,
        targetType: ReportTargetType.USER,
        targetId: dto.targetId,
        status: ReportStatus.PENDING,
      },
      order: { createdAt: 'DESC' },
    });
    if (existing) return existing;
    return this.reportsRepo.save(
      this.reportsRepo.create({
        reporterId,
        targetType: ReportTargetType.USER,
        targetId: dto.targetId,
        reason: dto.reason.trim().slice(0, 64),
        description: dto.description?.trim().slice(0, 2000) || null,
      }),
    );
  }

  async addExperience(userId: string, amount: number) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) return null;
    user.experience = Number(user.experience) + amount;
    let level = 1;
    for (let i = 0; i < LEVEL_THRESHOLDS.length; i++) {
      if (Number(user.experience) >= LEVEL_THRESHOLDS[i]) level = i + 1;
    }
    user.level = Math.min(level, 50);
    await this.usersRepo.save(user);
    return { level: user.level, experience: user.experience };
  }

  async search(q: string, query: PaginationDto) {
    const term = (q || '').trim();
    const qb = this.usersRepo
      .createQueryBuilder('u')
      .leftJoinAndSelect('u.profile', 'profile')
      .orderBy('u.createdAt', 'DESC')
      .skip(query.skip)
      .take(query.limit || 20);

    if (term) {
      // Exact publicId first so numeric app IDs always resolve.
      qb.andWhere(
        '(u.publicId = :exact OR CAST(u.publicId AS text) ILIKE :s OR u.username ILIKE :s OR u.displayName ILIKE :s)',
        { exact: term, s: `%${term}%` },
      );
    }

    const [items, total] = await qb.getManyAndCount();
    return paginate(
      items.map((u) => this.publicUser(u)),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  async getLevelInfo(userId: string) {
    const user = await this.usersRepo.findOne({
      where: { id: userId },
      relations: ['profile'],
    });
    if (!user) throw new NotFoundException('User not found');
    const current = LEVEL_THRESHOLDS[user.level - 1] ?? 0;
    const next = LEVEL_THRESHOLDS[user.level] ?? current + 1000000;
    return {
      level: user.level,
      levelBadgeUrl: user.profile?.levelBadgeUrl ?? null,
      experience: Number(user.experience),
      currentThreshold: current,
      nextThreshold: next,
      progress: Math.min(1, (Number(user.experience) - current) / (next - current || 1)),
    };
  }

  async listFriends(userId: string, query: PaginationDto) {
    const [items, total] = await this.socialRepo.findAndCount({
      where: [
        {
          fromUserId: userId,
          type: SocialRequestType.FRIEND,
          status: SocialRequestStatus.ACCEPTED,
        },
        {
          toUserId: userId,
          type: SocialRequestType.FRIEND,
          status: SocialRequestStatus.ACCEPTED,
        },
      ],
      relations: ['fromUser', 'fromUser.profile', 'toUser', 'toUser.profile'],
      skip: query.skip,
      take: query.limit || 20,
      order: { updatedAt: 'DESC' },
    });
    return paginate(
      items.map((row) =>
        this.publicUser(row.fromUserId === userId ? row.toUser : row.fromUser),
      ),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }

  /** Accepted CP pair between two users? */
  async isCpPair(userA: string, userB: string): Promise<boolean> {
    if (!userA || !userB || userA === userB) return false;
    const row = await this.socialRepo.findOne({
      where: [
        {
          fromUserId: userA,
          toUserId: userB,
          type: SocialRequestType.RELATION,
          status: SocialRequestStatus.ACCEPTED,
        },
        {
          fromUserId: userB,
          toUserId: userA,
          type: SocialRequestType.RELATION,
          status: SocialRequestStatus.ACCEPTED,
        },
      ],
    });
    return !!row;
  }

  private async hasAcceptedCp(userId: string): Promise<boolean> {
    const row = await this.socialRepo.findOne({
      where: [
        {
          fromUserId: userId,
          type: SocialRequestType.RELATION,
          status: SocialRequestStatus.ACCEPTED,
        },
        {
          toUserId: userId,
          type: SocialRequestType.RELATION,
          status: SocialRequestStatus.ACCEPTED,
        },
      ],
    });
    return !!row;
  }

  /** Lifetime gift coins exchanged between a CP pair → intimacy level. */
  private async cpBondScore(userA: string, userB: string): Promise<number> {
    try {
      const rows = await this.dataSource.query(
        `SELECT COALESCE(SUM(gs."totalCoins"), 0)::text AS s
           FROM gift_sends gs
          WHERE (gs."senderId" = $1 AND gs."receiverId" = $2)
             OR (gs."senderId" = $2 AND gs."receiverId" = $1)`,
        [userA, userB],
      );
      return Math.max(0, Math.floor(Number(rows?.[0]?.s || 0)));
    } catch {
      return 0;
    }
  }

  /** CP hub status for me (partner + intimacy). */
  async myCpStatus(userId: string) {
    const items = await this.listAcceptedRelations(userId, SocialRequestType.RELATION);
    const bond = items[0] || null;
    if (!bond?.user?.id) {
      return {
        hasCp: false,
        partner: null,
        bondScore: 0,
        level: 0,
        nextLevelAt: 500,
      };
    }
    const bondScore = await this.cpBondScore(userId, bond.user.id);
    // 500 coins exchanged between pair = 1 level (soft Mikoo-style ladder).
    const level = Math.min(99, 1 + Math.floor(bondScore / 500));
    const nextLevelAt = level * 500;
    return {
      hasCp: true,
      partner: bond.user,
      bondScore,
      level,
      nextLevelAt,
      relationId: bond.id,
    };
  }

  async createSocialRequest(fromUserId: string, toUserId: string, type: SocialRequestType) {
    if (fromUserId === toUserId) throw new BadRequestException('Cannot request yourself');
    const target = await this.usersRepo.findOne({ where: { id: toUserId } });
    if (!target) throw new NotFoundException('User not found');
    if (type === SocialRequestType.RELATION) {
      if (await this.hasAcceptedCp(fromUserId)) {
        throw new BadRequestException('لديك CP بالفعل — أنهِ الارتباط الحالي أولاً');
      }
      if (await this.hasAcceptedCp(toUserId)) {
        throw new BadRequestException('لدى هذا المستخدم CP بالفعل');
      }
    }
    let row = await this.socialRepo.findOne({ where: { fromUserId, toUserId, type } });
    if (row) {
      if (row.status === SocialRequestStatus.PENDING) return row;
      row.status = SocialRequestStatus.PENDING;
      const saved = await this.socialRepo.save(row);
      this.realtimeGateway.emitToUser(toUserId, 'social:request', {
        id: saved.id,
        type: saved.type,
        status: saved.status,
        fromUserId,
        toUserId,
        at: new Date().toISOString(),
      });
      return saved;
    }
    const saved = await this.socialRepo.save(
      this.socialRepo.create({
        fromUserId,
        toUserId,
        type,
        status: SocialRequestStatus.PENDING,
      }),
    );
    this.realtimeGateway.emitToUser(toUserId, 'social:request', {
      id: saved.id,
      type: saved.type,
      status: saved.status,
      fromUserId,
      toUserId,
      at: new Date().toISOString(),
    });
    const from = await this.usersRepo.findOne({ where: { id: fromUserId } });
    const name = from?.displayName || from?.username || 'مستخدم';
    const label =
      type === SocialRequestType.FRIEND
        ? 'طلب صداقة'
        : type === SocialRequestType.RELATION
          ? 'طلب CP'
          : type === SocialRequestType.GUARDIAN
            ? 'طلب وصاية'
            : 'طلب متابعة';
    void this.notifications?.notifyUser(
      toUserId,
      NotificationType.FOLLOW,
      label,
      `${name} أرسل لك ${label}`,
      { type: 'social', requestId: saved.id, requestType: type },
    );
    return saved;
  }

  async listAcceptedRelations(userId: string, type?: SocialRequestType) {
    const where: any[] = [
      { fromUserId: userId, status: SocialRequestStatus.ACCEPTED },
      { toUserId: userId, status: SocialRequestStatus.ACCEPTED },
    ];
    if (type) {
      where[0].type = type;
      where[1].type = type;
    }
    const items = await this.socialRepo.find({
      where,
      relations: ['fromUser', 'fromUser.profile', 'toUser', 'toUser.profile'],
      order: { updatedAt: 'DESC' },
      take: 100,
    });
    const allowed = type
      ? [type]
      : [SocialRequestType.RELATION, SocialRequestType.GUARDIAN, SocialRequestType.FRIEND];
    return items
      .filter((r) => allowed.includes(r.type))
      .map((r) => {
        const peer = r.fromUserId === userId ? r.toUser : r.fromUser;
        return {
          id: r.id,
          type: r.type,
          status: r.status,
          createdAt: r.createdAt,
          user: this.publicUser(peer),
        };
      });
  }

  async listIncomingRequests(userId: string, type?: SocialRequestType) {
    const where: any = { toUserId: userId, status: SocialRequestStatus.PENDING };
    if (type) where.type = type;
    const items = await this.socialRepo.find({
      where,
      relations: ['fromUser', 'fromUser.profile'],
      order: { createdAt: 'DESC' },
      take: 50,
    });
    return items.map((r) => ({
      id: r.id,
      type: r.type,
      status: r.status,
      createdAt: r.createdAt,
      user: this.publicUser(r.fromUser),
    }));
  }

  async respondSocialRequest(userId: string, requestId: string, accept: boolean) {
    const row = await this.socialRepo.findOne({ where: { id: requestId, toUserId: userId } });
    if (!row) throw new NotFoundException('Request not found');
    if (accept && row.status === SocialRequestStatus.ACCEPTED) {
      return { id: row.id, status: row.status };
    }
    row.status = accept ? SocialRequestStatus.ACCEPTED : SocialRequestStatus.REJECTED;
    await this.socialRepo.save(row);
    if (accept) {
      if (row.type === SocialRequestType.FRIEND) {
        try {
          await this.follow(userId, row.fromUserId);
        } catch {
          /* already following */
        }
        try {
          await this.follow(row.fromUserId, userId);
        } catch {
          /* already */
        }
        await this.profilesRepo.increment({ userId }, 'friendsCount', 1);
        await this.profilesRepo.increment({ userId: row.fromUserId }, 'friendsCount', 1);
      } else if (row.type === SocialRequestType.FOLLOW) {
        // Legacy follow request: acceptance means requester follows recipient only.
        try {
          await this.follow(row.fromUserId, userId);
        } catch {
          /* already following */
        }
      } else if (row.type === SocialRequestType.RELATION) {
        if (await this.hasAcceptedCp(userId)) {
          // Accepting when I somehow already have another CP (race) — supersede below.
        }
        if (await this.hasAcceptedCp(row.fromUserId)) {
          // Sender already paired — only keep this one; supersede others.
        }
        // One CP pair only: supersede other accepted relation links for both users.
        const others = await this.socialRepo.find({
          where: [
            { fromUserId: userId, type: SocialRequestType.RELATION, status: SocialRequestStatus.ACCEPTED },
            { toUserId: userId, type: SocialRequestType.RELATION, status: SocialRequestStatus.ACCEPTED },
            {
              fromUserId: row.fromUserId,
              type: SocialRequestType.RELATION,
              status: SocialRequestStatus.ACCEPTED,
            },
            {
              toUserId: row.fromUserId,
              type: SocialRequestType.RELATION,
              status: SocialRequestStatus.ACCEPTED,
            },
          ],
        });
        for (const other of others) {
          if (other.id === row.id) continue;
          other.status = SocialRequestStatus.REJECTED;
          await this.socialRepo.save(other);
        }
      }
      // guardian: accepted status is the link (listed via me/relations)
    }
    this.realtimeGateway.emitToUser(row.fromUserId, 'social:request_responded', {
      id: row.id,
      status: row.status,
      type: row.type,
      fromUserId: row.fromUserId,
      toUserId: row.toUserId,
      at: new Date().toISOString(),
    });
    this.realtimeGateway.emitToUser(row.toUserId, 'social:request_responded', {
      id: row.id,
      status: row.status,
      type: row.type,
      fromUserId: row.fromUserId,
      toUserId: row.toUserId,
      at: new Date().toISOString(),
    });
    return { id: row.id, status: row.status };
  }

  async recordVisit(visitorId: string, profileUserId: string) {
    if (!visitorId || visitorId === profileUserId) return { recorded: false };
    let row = await this.visitsRepo.findOne({ where: { visitorId, profileUserId } });
    if (!row) {
      row = this.visitsRepo.create({ visitorId, profileUserId, visitCount: 1, lastVisitedAt: new Date() });
    } else {
      row.visitCount = Number(row.visitCount || 0) + 1;
      row.lastVisitedAt = new Date();
    }
    await this.visitsRepo.save(row);
    return { recorded: true };
  }

  async reconcileFriendsCounts() {
    const rows = await this.socialRepo.find({
      where: { type: SocialRequestType.FRIEND, status: SocialRequestStatus.ACCEPTED },
    });
    const counts = new Map<string, number>();
    for (const row of rows) {
      counts.set(row.fromUserId, (counts.get(row.fromUserId) || 0) + 1);
      counts.set(row.toUserId, (counts.get(row.toUserId) || 0) + 1);
    }
    for (const [userId, count] of counts.entries()) {
      await this.profilesRepo.update({ userId }, { friendsCount: count });
    }
    return { reconciled: counts.size };
  }

  async listVisitors(profileUserId: string, query: PaginationDto) {
    const [items, total] = await this.visitsRepo.findAndCount({
      where: { profileUserId },
      relations: ['visitor', 'visitor.profile'],
      skip: query.skip,
      take: query.limit || 20,
      order: { lastVisitedAt: 'DESC' },
    });
    return paginate(
      items.map((v) => ({
        ...this.publicUser(v.visitor),
        visitCount: v.visitCount,
        lastVisitedAt: v.lastVisitedAt,
      })),
      total,
      query.page || 1,
      query.limit || 20,
    );
  }
}
