import { Injectable, NotFoundException, ConflictException, BadRequestException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Badge } from '../../database/entities/badge.entity';
import { UserBadge } from '../../database/entities/user-badge.entity';
import { User } from '../../database/entities/user.entity';
import { Follow } from '../../database/entities/follow.entity';

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

  async catalog(userId: string) {
    // Reconcile configured achievement badges against the user's real, persisted metrics.
    // The unique user/badge index plus INSERT .. ON CONFLICT DO NOTHING makes this idempotent.
    await this.awardEligibleAchievementBadges(userId);
    const [badges, owned] = await Promise.all([
      this.badges.find({ where: { isActive: true }, order: { sortOrder: 'ASC', name: 'ASC' } }),
      this.userBadges.find({ where: { userId }, relations: { badge: true } }),
    ]);
    const ownedById = new Map(owned.map((row) => [row.badgeId, row]));
    return badges.map((badge) => ({
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

    const followerCount = await this.follows.count({ where: { followingId: userId } });
    const metrics: Record<string, number> = {
      level: Number(user.level) || 0,
      experience: Number(user.experience) || 0,
      followers: followerCount,
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

    await this.userBadges.createQueryBuilder()
      .insert()
      .values(earned.map((badge) => ({
        userId,
        badgeId: badge.id,
        grantedByAdminId: null,
        source: 'achievement',
      })))
      .orIgnore()
      .execute();
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

  async grant(badgeId: string, userIdentifier: string, adminId: string | null) {
    const badge = await this.badges.findOne({ where: { id: badgeId } });
    if (!badge) throw new NotFoundException('Badge not found');
    const identifier = (userIdentifier || '').trim();
    if (!identifier) throw new BadRequestException('User ID is required');
    const user = await this.users.findOne({
      where: [{ id: identifier }, { publicId: identifier }],
      select: { id: true, publicId: true, username: true, displayName: true },
    });
    if (!user) throw new NotFoundException('User not found by UUID or public ID');
    const existing = await this.userBadges.findOne({ where: { badgeId, userId: user.id } });
    if (existing) throw new ConflictException('User already owns this badge');
    const grant = await this.userBadges.save(this.userBadges.create({
      badgeId, userId: user.id, grantedByAdminId: adminId, source: 'manual',
    }));
    return { ...grant, user: { id: user.id, publicId: user.publicId, username: user.username, displayName: user.displayName }, badge };
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
      if (!dto.achievementKey || !['level', 'experience', 'followers'].includes(dto.achievementKey)) {
        throw new BadRequestException('Achievement key must be level, experience, or followers');
      }
      if (!Number.isFinite(Number(dto.achievementThreshold)) || Number(dto.achievementThreshold) <= 0) {
        throw new BadRequestException('Achievement threshold must be a positive number');
      }
    }
  }
}