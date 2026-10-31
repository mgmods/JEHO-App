import { Injectable, NotFoundException, ConflictException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';
import { Badge } from '../../database/entities/badge.entity';
import { UserBadge } from '../../database/entities/user-badge.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
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
  hiddenTaskEnabled?: boolean;
  hiddenTaskEvent?: string | null;
  hiddenTaskTarget?: number;
  hiddenTaskDays?: number;
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
    private readonly dataSource: DataSource,
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
    await this.cleanupExpiredDisplayedMedals(user.id);
    const grants = await this.userBadges.find({
      where: { userId: user.id },
      relations: { badge: true },
      order: { grantedAt: 'DESC' },
    });
    return grants
      .filter((grant) => !!grant.badge)
      .filter((grant) => !grant.expiresAt || new Date(grant.expiresAt).getTime() > Date.now())
      .map((grant) => {
        const {
          hiddenTaskEnabled: _hiddenTaskEnabled,
          hiddenTaskEvent: _hiddenTaskEvent,
          hiddenTaskTarget: _hiddenTaskTarget,
          hiddenTaskDays: _hiddenTaskDays,
          ...publicBadge
        } = grant.badge;
        return {
          ...publicBadge,
          earned: true,
          grantedAt: grant.grantedAt,
          expiresAt: grant.expiresAt ?? null,
          source: grant.source,
        };
      });
  }

  async catalog(userId: string) {
    await this.cleanupExpiredDisplayedMedals(userId);
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
      .map((badge) => {
        const {
          hiddenTaskEnabled: _hiddenTaskEnabled,
          hiddenTaskEvent: _hiddenTaskEvent,
          hiddenTaskTarget: _hiddenTaskTarget,
          hiddenTaskDays: _hiddenTaskDays,
          ...publicBadge
        } = badge;
        return {
          ...publicBadge,
          earned: ownedById.has(badge.id),
          grantedAt: ownedById.get(badge.id)?.grantedAt ?? null,
          source: ownedById.get(badge.id)?.source ?? null,
        };
      });
  }

  /** Remove expired medal IDs from the profile showcase so timed medals disappear everywhere. */
  private async cleanupExpiredDisplayedMedals(userId: string): Promise<void> {
    const expired = await this.userBadges.createQueryBuilder('ub')
      .select('ub.badgeId', 'badgeId')
      .where('ub.userId = :userId', { userId })
      .andWhere('ub.expiresAt IS NOT NULL AND ub.expiresAt <= NOW()')
      .getRawMany<{ badgeId: string }>();
    if (!expired.length) return;
    const expiredIds = new Set(expired.map((row) => String(row.badgeId)));
    const profile = await this.profiles.findOne({ where: { userId } });
    if (!profile || !Array.isArray(profile.displayedMedalIds)) return;
    const next = profile.displayedMedalIds.filter((id) => !expiredIds.has(String(id)));
    if (next.length !== profile.displayedMedalIds.length) {
      profile.displayedMedalIds = next;
      await this.profiles.save(profile);
    }
  }

  /** Track secret medal conditions from real server activity; no task record/event/API is created. */
  async recordHiddenTaskProgress(userId: string, event: string, amount = 1): Promise<void> {
    if (!userId || !event || amount <= 0) return;
    const raw = String(event).toLowerCase();
    const family = (raw.startsWith('rooms') || raw.startsWith('host_room') || raw.startsWith('host_visit')) ? 'rooms'
      : (raw.startsWith('gift') || raw.startsWith('host_gift')) ? 'gift'
      : raw.startsWith('follow') ? 'follow'
      : (raw.startsWith('mic') || raw.startsWith('host_mic')) ? 'mic'
      : (raw.startsWith('checkin') || raw.startsWith('host_checkin')) ? 'checkin'
      : (raw.startsWith('recharge') || raw.startsWith('exchange')) ? 'recharge'
      : raw.startsWith('game') || raw.startsWith('host_game') ? 'game'
      : raw.startsWith('win') ? 'win'
      : (raw.startsWith('ad') || raw.includes('rewarded')) ? 'ad'
      : (raw.startsWith('chat') || raw.startsWith('host_chat') || raw.startsWith('message') || raw.startsWith('dm')) ? 'chat'
      : '';
    if (!family) return;

    const configured = await this.badges.find({
      where: { isActive: true, hiddenTaskEnabled: true, hiddenTaskEvent: family },
      select: { id: true, hiddenTaskTarget: true, hiddenTaskDays: true },
    });
    for (const medal of configured) {
      const target = Math.max(1, Math.min(1_000_000_000, Math.floor(Number(medal.hiddenTaskTarget) || 1)));
      const key = `hidden_medal_progress:${userId}:${medal.id}`;
      let shouldGrant = false;
      await this.dataSource.transaction(async (manager) => {
        await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [key]);
        let row = await manager.findOne(AppSetting, { where: { key }, lock: { mode: 'pessimistic_write' } });
        let state: { current: number; awarded: boolean } = { current: 0, awarded: false };
        try { if (row?.value) state = { ...state, ...JSON.parse(row.value) }; } catch {}
        if (!state.awarded) {
          state.current = Math.min(target, Math.max(0, Number(state.current) || 0) + amount);
          shouldGrant = state.current >= target;
          if (!row) row = manager.create(AppSetting, { key, value: '', description: 'Private medal condition progress' });
          row.value = JSON.stringify(state);
          await manager.save(row);
        }
      });
      if (!shouldGrant) continue;
      try {
        const granted = await this.grantForTask(userId, medal.id, medal.hiddenTaskDays, 'hidden_task', false);
        const alreadyOwned = !granted && !!(await this.userBadges.createQueryBuilder('ub')
          .where('ub.userId = :userId AND ub.badgeId = :badgeId', { userId, badgeId: medal.id })
          .andWhere('(ub.expiresAt IS NULL OR ub.expiresAt > NOW())')
          .getOne());
        if (granted || alreadyOwned) {
          const row = await this.dataSource.getRepository(AppSetting).findOne({ where: { key } });
          if (row?.value) {
            const state = JSON.parse(row.value);
            state.awarded = true;
            row.value = JSON.stringify(state);
            await this.dataSource.getRepository(AppSetting).save(row);
          }
        }
      } catch (error) {
        console.error('[Badges] Private medal grant failed', error instanceof Error ? error.message : String(error));
      }
    }
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

  async isActiveForTask(badgeId: string): Promise<boolean> {
    if (!badgeId) return false;
    return !!(await this.badges.findOne({
      where: { id: badgeId, isActive: true },
      select: { id: true },
    }));
  }

  /**
   * Automatic task reward. A per-user/per-medal advisory lock prevents duplicate
   * grants. The medal is pinned into the public profile showcase even if it was
   * already owned, so completing the task always equips it automatically.
   */
  async grantForTask(userId: string, badgeId: string, durationDaysRaw = 0, source = 'task', notify = true): Promise<boolean> {
    const badge = await this.badges.findOne({ where: { id: badgeId, isActive: true } });
    if (!badge || !userId) return false;

    const durationDays = Math.max(0, Math.min(3650, Math.floor(Number(durationDaysRaw) || 0)));
    const expiresAt = durationDays > 0
      ? new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000)
      : null;

    const granted = await this.dataSource.transaction(async (manager) => {
      await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
        `task-medal:${userId}:${badgeId}`,
      ]);
      const existing = await manager.findOne(UserBadge, {
        where: { userId, badgeId },
        lock: { mode: 'pessimistic_write' },
      });
      let newlyGranted = false;
      if (!existing || (existing.expiresAt && new Date(existing.expiresAt).getTime() <= Date.now())) {
        if (existing) {
          existing.grantedAt = new Date();
          existing.grantedByAdminId = null;
          existing.source = source;
          existing.expiresAt = expiresAt;
          await manager.save(existing);
        } else {
          await manager.save(manager.create(UserBadge, {
            userId,
            badgeId,
            grantedByAdminId: null,
            source,
            expiresAt,
          }));
        }
        newlyGranted = true;
      }

      let profile = await manager.findOne(UserProfile, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!profile) profile = manager.create(UserProfile, { userId });
      const displayed = Array.isArray(profile.displayedMedalIds) ? profile.displayedMedalIds : [];
      profile.displayedMedalIds = [
        badgeId,
        ...displayed.filter((id) => id !== badgeId),
      ].slice(0, 5);
      await manager.save(profile);
      return newlyGranted;
    });

    if (granted && notify) {
      try {
        await this.notifications.notifyUser(
          userId,
          NotificationType.SYSTEM,
          'ميدالية جديدة! 🏅',
          source === 'hidden_task' ? 'حصلت على ميدالية جديدة.' : `أكملت المهمة وحصلت تلقائياً على ميدالية ${badge.name}`,
          { kind: 'badge_earned', badgeId: badge.id, badgeName: badge.name, imageUrl: badge.imageUrl, source },
        );
      } catch (error) {
        // Notification delivery must never roll back a successfully granted medal.
        console.error('[Badges] Task medal notification failed', error instanceof Error ? error.message : String(error));
      }
    }
    return granted;
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
      hiddenTaskEnabled: dto.hiddenTaskEnabled ?? false,
      hiddenTaskEvent: dto.hiddenTaskEvent ?? null,
      hiddenTaskTarget: Math.max(1, Math.floor(Number(dto.hiddenTaskTarget) || 1)),
      hiddenTaskDays: Math.max(0, Math.min(3650, Math.floor(Number(dto.hiddenTaskDays) || 0))),
      isActive: dto.isActive ?? true,
      isAnimated: dto.isAnimated ?? false,
      sortOrder: dto.sortOrder ?? 0,
    }));
  }

  async update(id: string, dto: Partial<UpsertBadgeDto>) {
    const badge = await this.badges.findOne({ where: { id } });
    if (!badge) throw new NotFoundException('Medal not found');
    const merged = { ...badge, ...dto };
    this.validate(merged);
    Object.assign(badge, dto);
    if (dto.hiddenTaskTarget !== undefined) badge.hiddenTaskTarget = Math.max(1, Math.floor(Number(dto.hiddenTaskTarget) || 1));
    if (dto.hiddenTaskDays !== undefined) badge.hiddenTaskDays = Math.max(0, Math.min(3650, Math.floor(Number(dto.hiddenTaskDays) || 0)));
    return this.badges.save(badge);
  }

  async remove(id: string) {
    const badge = await this.badges.findOne({ where: { id } });
    if (!badge) throw new NotFoundException('Medal not found');
    await this.badges.remove(badge);
    return { success: true };
  }

  async grant(badgeId: string, userIdentifier: string, adminId: string | null, durationDaysRaw?: number) {
    const badge = await this.badges.findOne({ where: { id: badgeId } });
    if (!badge) throw new NotFoundException('Medal not found');
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
      throw new BadRequestException('Medal duration must be 0 (permanent) or a positive number of days');
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

  /** Grant one medal to every existing account without duplicating active ownership. */
  async grantAll(badgeId: string, adminId: string | null, durationDaysRaw?: number) {
    const badge = await this.badges.findOne({ where: { id: badgeId } });
    if (!badge) throw new NotFoundException('Medal not found');
    const durationDays = durationDaysRaw == null || durationDaysRaw === 0
      ? 0
      : Math.min(3650, Math.max(1, Math.floor(Number(durationDaysRaw))));
    if (durationDaysRaw != null && (!Number.isFinite(Number(durationDaysRaw)) || Number(durationDaysRaw) < 0)) {
      throw new BadRequestException('Medal duration must be 0 (permanent) or a positive number of days');
    }
    const expiresAt = durationDays > 0 ? new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000) : null;
    const users = await this.users.find({ select: { id: true } });
    let granted = 0;
    let skipped = 0;
    const notifications: Array<{ userId: string; renewed: boolean }> = [];

    // Process in bounded chunks so a large community does not create one enormous SQL query.
    for (let offset = 0; offset < users.length; offset += 250) {
      const chunk = users.slice(offset, offset + 250);
      const ids = chunk.map((user) => user.id);
      const existing = await this.userBadges.find({ where: { badgeId, userId: In(ids) } });
      const byUser = new Map(existing.map((row) => [row.userId, row]));
      const inserts: Array<{ badgeId: string; userId: string; grantedByAdminId: string | null; source: string; expiresAt: Date | null }> = [];
      const renewals: UserBadge[] = [];
      for (const userId of ids) {
        const row = byUser.get(userId);
        if (row && (!row.expiresAt || new Date(row.expiresAt).getTime() > Date.now())) {
          skipped += 1;
          continue;
        }
        if (row) {
          row.grantedAt = new Date();
          row.grantedByAdminId = adminId;
          row.source = 'manual';
          row.expiresAt = expiresAt;
          renewals.push(row);
          notifications.push({ userId, renewed: true });
        } else {
          inserts.push({ badgeId, userId, grantedByAdminId: adminId, source: 'manual', expiresAt });
          notifications.push({ userId, renewed: false });
        }
      }
      if (renewals.length) {
        await this.userBadges.save(renewals);
        granted += renewals.length;
      }
      if (inserts.length) {
        const result = await this.userBadges.createQueryBuilder()
          .insert()
          .values(inserts)
          .orIgnore()
          .returning(['userId'])
          .execute();
        const insertedIds = new Set<string>((result.raw || []).map((row: { userId?: string; user_id?: string }) =>
          String(row.userId || row.user_id || ''),
        ).filter(Boolean));
        granted += insertedIds.size;
        // Drop notifications for rows that lost a uniqueness race.
        for (let i = notifications.length - 1; i >= 0; i--) {
          if (!notifications[i].renewed && ids.includes(notifications[i].userId) && !insertedIds.has(notifications[i].userId)) {
            notifications.splice(i, 1);
            skipped += 1;
          }
        }
      }
    }

    // Limit notification concurrency to keep bulk gifting gentle on the API/database.
    for (let offset = 0; offset < notifications.length; offset += 25) {
      await Promise.all(notifications.slice(offset, offset + 25).map(({ userId }) => this.notifications.notifyUser(
        userId,
        NotificationType.SYSTEM,
        'تم إهداؤك ميدالية جديدة! 🏅',
        `مبروك! تمت إضافة ميدالية ${badge.name} إلى حسابك. افتح ملفك الشخصي لعرضها.`,
        { kind: 'badge_earned', badgeId: badge.id, badgeName: badge.name, imageUrl: badge.imageUrl },
      )));
    }
    return { success: true, badge, totalUsers: users.length, granted, skipped, durationDays };
  }

  async revoke(badgeId: string, userIdentifier: string) {
    const identifier = (userIdentifier || '').trim();
    if (!identifier) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({ where: [{ id: identifier }, { publicId: identifier }] });
    if (!user) throw new NotFoundException('User not found by UUID or public ID');
    const grant = await this.userBadges.findOne({ where: { badgeId, userId: user.id } });
    if (!grant) throw new NotFoundException('Medal ownership not found');
    await this.userBadges.remove(grant);
    return { success: true };
  }

  private validate(dto: UpsertBadgeDto) {
    if (!dto.name?.trim()) throw new BadRequestException('Medal name is required');
    if (!dto.imageUrl?.trim()) throw new BadRequestException('Medal image URL is required');
    if (dto.name.length > 80 || dto.imageUrl.length > 512) throw new BadRequestException('Medal field is too long');
    if (dto.awardType === 'achievement') {
      if (!dto.achievementKey || !['level', 'experience', 'followers', 'recharge', 'support', 'received'].includes(dto.achievementKey)) {
        throw new BadRequestException('Achievement key must be level, experience, followers, recharge, support, or received');
      }
      if (!Number.isFinite(Number(dto.achievementThreshold)) || Number(dto.achievementThreshold) <= 0) {
        throw new BadRequestException('Achievement threshold must be a positive number');
      }
    }
    if (dto.hiddenTaskEnabled) {
      const allowedEvents = ['rooms', 'gift', 'follow', 'mic', 'checkin', 'recharge', 'game', 'win', 'chat', 'ad'];
      if (!dto.hiddenTaskEvent || !allowedEvents.includes(dto.hiddenTaskEvent)) {
        throw new BadRequestException('Private medal activity type is invalid');
      }
      if (!Number.isFinite(Number(dto.hiddenTaskTarget)) || Number(dto.hiddenTaskTarget) < 1) {
        throw new BadRequestException('Private medal target must be at least 1');
      }
      if (!Number.isFinite(Number(dto.hiddenTaskDays)) || Number(dto.hiddenTaskDays) < 0 || Number(dto.hiddenTaskDays) > 3650) {
        throw new BadRequestException('Private medal duration must be between 0 and 3650 days');
      }
    }
  }
}