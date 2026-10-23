import { Injectable, NotFoundException, ConflictException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Badge } from '../../database/entities/badge.entity';
import { UserBadge } from '../../database/entities/user-badge.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { NotificationType } from '../../database/entities/notification.entity';

export interface UpsertBadgeDto {
  name: string;
  description?: string;
  imageUrl: string;
  category?: string;
  awardType?: string;
  achievementKey?: string | null;
  achievementThreshold?: number;
  isActive?: boolean;
  isAnimated?: boolean;
  sortOrder?: number;
}

@Injectable()
export class BadgesService {
  constructor(
    @InjectRepository(Badge) private readonly badges: Repository<Badge>,
    @InjectRepository(UserBadge) private readonly userBadges: Repository<UserBadge>,
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(Follow) private readonly follows: Repository<Follow>,
    @InjectRepository(Wallet) private readonly wallets: Repository<Wallet>,
    @InjectRepository(UserProfile) private readonly profiles: Repository<UserProfile>,
    private readonly notifications: NotificationsService,
  ) {}

  async catalogForIdentifier(identifier: string) {
    const value = (identifier || '').trim();
    if (!value) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({
      where: [{ id: value }, { publicId: value }],
      select: { id: true, publicId: true },
    });
    if (!user) throw new NotFoundException('User not found');
    return this.catalog(user.id);
  }

  /** Read the user's persisted ownership records directly, independent of catalogue visibility. */
  async earnedForIdentifier(identifier: string) {
    const value = (identifier || '').trim();
    if (!value) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({
      where: [{ id: value }, { publicId: value }],
      select: { id: true, publicId: true },
    });
    if (!user) throw new NotFoundException('User not found');
    const grants = await this.userBadges.find({
      where: { userId: user.id },
      relations: { badge: true },
      order: { grantedAt: 'DESC' },
    });
    return grants
      .filter((grant) => !!grant.badge)
      .filter((grant) => !grant.expiresAt || new Date(grant.expiresAt).getTime() > Date.now())
      .map((grant) => ({
        ...grant.badge,
        earned: true,
        grantedAt: grant.grantedAt,
        expiresAt: grant.expiresAt ?? null,
        source: grant.source,
      }));
  }

  async catalog(userId: string) {
    // Reconcile configured achievement badges against the user's real, persisted metrics.
    // The unique user/badge index plus INSERT .. ON CONFLICT DO NOTHING makes this idempotent.
    await this.awardEligibleAchievementBadges(userId);
    const [catalog, owned] = await Promise.all([
      // Always load the full catalogue so a badge already granted to a user does not
      // disappear from their profile if an admin later hides it from the public catalogue.
      this.badges.find({ order: { sortOrder: 'ASC', name: 'ASC' } }),
      this.userBadges.createQueryBuilder('ub')
        .leftJoinAndSelect('ub.badge', 'badge')
        .where('ub.userId = :userId', { userId })
        .andWhere('(ub.expiresAt IS NULL OR ub.expiresAt > NOW())')
        .getMany(),
    ]);
    const ownedById = new Map(owned.map((row) => [row.badgeId, row]));
    // Active badges are discoverable by everyone; previously granted badges remain
    // visible to their owner even when no longer offered to new users.
    return catalog
      .filter((badge) => badge.isActive || ownedById.has(badge.id))
      .map((badge) => ({
        ...badge,
        earned: ownedById.has(badge.id),
        grantedAt: ownedById.get(badge.id)?.grantedAt ?? null,
        source: ownedById.get(badge.id)?.source ?? null,
      }));
  }

  private async awardEligibleAchievementBadges(userId: string): Promise<void> {
    const user = await this.users.findOne({
      where: { id: userId },
      select: { id: true, level: true, experience: true },
    });
    if (!user) return;

    const [followerCount, wallet, profile] = await Promise.all([
      this.follows.count({ where: { followingId: userId } }),
      this.wallets.findOne({
        where: { userId },
        select: { userId: true, totalRecharged: true },
      }),
      this.profiles.findOne({
        where: { userId },
        select: { userId: true, totalSentCoins: true, totalReceivedDiamonds: true },
      }),
    ]);
    const metrics: Record<string, number> = {
      level: Number(user.level) || 0,
      experience: Number(user.experience) || 0,
      followers: followerCount,
      // These values are persisted totals, not current wallet balances.
      recharge: Number(wallet?.totalRecharged) || 0,
      support: Number(profile?.totalSentCoins) || 0,
      received: Number(profile?.totalReceivedDiamonds) || 0,
    };

    const eligible = await this.badges.find({
      where: {
        isActive: true,
        awardType: 'achievement',
        achievementKey: In(Object.keys(metrics)),
      },
    });
    const earned = eligible.filter((badge) =>
      Number.isFinite(Number(badge.achievementThreshold))
      && Number(badge.achievementThreshold) > 0
      && metrics[badge.achievementKey || ''] !== undefined
      && metrics[badge.achievementKey || ''] >= Number(badge.achievementThreshold),
    );
    if (!earned.length) return;

    const inserted = await this.userBadges.createQueryBuilder()
      .insert()
      .values(earned.map((badge) => ({
        userId,
        badgeId: badge.id,
        grantedByAdminId: null,
        source: 'achievement',
      })))
      .orIgnore()
      .returning(['id', 'badgeId'])
      .execute();

    // Only rows actually inserted by this request trigger a notification.
    const insertedBadgeIds = new Set<string>(
      (inserted.raw || []).map((row: { badgeId?: string; badge_id?: string }) =>
        String(row.badgeId || row.badge_id || ''),
      ).filter(Boolean),
    );
    const newlyEarned = earned.filter((badge) => insertedBadgeIds.has(badge.id));
    await Promise.all(newlyEarned.map((badge) => this.notifications.notifyUser(
      userId,
      NotificationType.SYSTEM,
      'ميدالية جديدة! 🏅',
      `حصلت على ميدالية ${badge.name}`,
      { kind: 'badge_earned', badgeId: badge.id, badgeName: badge.name, imageUrl: badge.imageUrl },
    )));
  }

  listAdmin() {
    return this.badges.find({ order: { sortOrder: 'ASC', name: 'ASC' } });
  }

  async create(dto: UpsertBadgeDto) {
    this.validate(dto);
    return this.badges.save(this.badges.create({
      ...dto,
      description: dto.description ?? '',
      category: dto.category ?? 'general',
      awardType: dto.awardType ?? 'manual',
      achievementKey: dto.achievementKey ?? null,
      achievementThreshold: dto.achievementThreshold ?? 0,
      isActive: dto.isActive ?? true,
      isAnimated: dto.isAnimated ?? false,
      sortOrder: dto.sortOrder ?? 0,
    }));
  }

  async update(id: string, dto: Partial<UpsertBadgeDto>) {
    const badge = await this.badges.findOne({ where: { id } });
    if (!badge) throw new NotFoundException('Badge not found');
    const merged = { ...badge, ...dto };
    this.validate(merged);
    Object.assign(badge, dto);
    return this.badges.save(badge);
  }

  async remove(id: string) {
    const badge = await this.badges.findOne({ where: { id } });
    if (!badge) throw new NotFoundException('Badge not found');
    await this.badges.remove(badge);
    return { success: true };
  }

  async grant(badgeId: string, userIdentifier: string, adminId: string | null, durationDaysRaw?: number) {
    const badge = await this.badges.findOne({ where: { id: badgeId } });
    if (!badge) throw new NotFoundException('Badge not found');
    const identifier = (userIdentifier || '').trim();
    if (!identifier) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({
      where: [{ id: identifier }, { publicId: identifier }],
      select: { id: true, publicId: true, username: true, displayName: true },
    });
    if (!user) throw new NotFoundException('User not found by UUID or public ID');
    const durationDays = durationDaysRaw == null || durationDaysRaw === 0
      ? 0
      : Math.min(3650, Math.max(1, Math.floor(Number(durationDaysRaw))));
    if (durationDaysRaw != null && (!Number.isFinite(Number(durationDaysRaw)) || Number(durationDaysRaw) < 0)) {
      throw new BadRequestException('Badge duration must be 0 (permanent) or a positive number of days');
    }
    const expiresAt = durationDays > 0 ? new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000) : null;
    const existing = await this.userBadges.findOne({ where: { badgeId, userId: user.id } });
    let grant: UserBadge;
    if (existing) {
      if (!existing.expiresAt || new Date(existing.expiresAt).getTime() > Date.now()) {
        throw new ConflictException('User already owns this badge');
      }
      existing.grantedAt = new Date();
      existing.grantedByAdminId = adminId;
      existing.source = 'manual';
      existing.expiresAt = expiresAt;
      grant = await this.userBadges.save(existing);
    } else {
      grant = await this.userBadges.save(this.userBadges.create({
        badgeId, userId: user.id, grantedByAdminId: adminId, source: 'manual', expiresAt,
      }));
    }
    const notification = await this.notifications.notifyUser(
      user.id,
      NotificationType.SYSTEM,
      'تم إهداؤك ميدالية جديدة! 🏅',
      `مبروك! تمت إضافة ميدالية ${badge.name} إلى حسابك. افتح ملفك الشخصي لعرضها.`,
      { kind: 'badge_earned', badgeId: badge.id, badgeName: badge.name, imageUrl: badge.imageUrl },
    );
    return {
      ...grant,
      user: { id: user.id, publicId: user.publicId, username: user.username, displayName: user.displayName },
      badge,
      expiresAt: grant.expiresAt ?? null,
      notificationCreated: Boolean(notification),
      notificationId: notification?.id ?? null,
    };
  }

  async revoke(badgeId: string, userIdentifier: string) {
    const identifier = (userIdentifier || '').trim();
    if (!identifier) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({ where: [{ id: identifier }, { publicId: identifier }] });
    if (!user) throw new NotFoundException('User not found by UUID or public ID');
    const grant = await this.userBadges.findOne({ where: { badgeId, userId: user.id } });
    if (!grant) throw new NotFoundException('Badge ownership not found');
    await this.userBadges.remove(grant);
    return { success: true };
  }

  private validate(dto: UpsertBadgeDto) {
    if (!dto.name?.trim()) throw new BadRequestException('Badge name is required');
    if (!dto.imageUrl?.trim()) throw new BadRequestException('Badge image URL is required');
    if (dto.name.length > 80 || dto.imageUrl.length > 512) throw new BadRequestException('Badge field is too long');
    if (dto.awardType === 'achievement') {
      if (!dto.achievementKey || !['level', 'experience', 'followers', 'recharge', 'support', 'received'].includes(dto.achievementKey)) {
        throw new BadRequestException('Achievement key must be level, experience, followers, recharge, support, or received');
      }
      if (!Number.isFinite(Number(dto.achievementThreshold)) || Number(dto.achievementThreshold) <= 0) {
        throw new BadRequestException('Achievement threshold must be a positive number');
      }
    }
  }
}