import { Injectable, NotFoundException, BadRequestException, Optional, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository, MoreThan } from 'typeorm';
import { VipPlan } from '../../database/entities/vip-plan.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { IsInt, Min, Max, IsOptional, IsIn } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { vipMedalUrl } from '../../common/vip-assets';
import {
  VIP_ALLOWED_DAYS,
  VIP_DURATION_PACKS,
  vipPackForDays,
  vipPriceForDays,
} from '../../common/promo-catalog';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';

export class PurchaseVipDto {
  @ApiProperty({ minimum: 1, maximum: 100 })
  @IsInt()
  @Min(1)
  @Max(100)
  level: number;

  /** Rental days — VIP is never permanent. Allowed: 7 / 30 / 40. */
  @ApiProperty({ required: false, enum: [7, 30, 40], default: 30 })
  @IsOptional()
  @IsInt()
  @IsIn([7, 30, 40])
  durationDays?: number;
}

@Injectable()
export class VipService {
  private readonly logger = new Logger(VipService.name);
  private static readonly MAX_VIP_LEVEL = 100;

  /** Hard progression curve so VIP100 stays elite. */
  private static vipPriceForLevel(level: number): number {
    const l = Math.max(1, Math.min(VipService.MAX_VIP_LEVEL, Math.floor(Number(level) || 1)));
    if (l <= 10) return [0, 99, 199, 399, 699, 999, 1499, 2499, 3999, 5999, 9999][l];
    if (l <= 20) return Math.round(9_999 + Math.pow(l - 10, 1.18) * 2_100);
    if (l <= 40) return Math.round(38_000 + Math.pow(l - 20, 1.24) * 5_600);
    if (l <= 60) return Math.round(150_000 + Math.pow(l - 40, 1.28) * 11_500);
    if (l <= 80) return Math.round(430_000 + Math.pow(l - 60, 1.32) * 21_000);
    return Math.round(1_000_000 + Math.pow(l - 80, 1.36) * 48_000);
  }

  constructor(
    @InjectRepository(VipPlan) private readonly plansRepo: Repository<VipPlan>,
    @InjectRepository(UserVip) private readonly userVipsRepo: Repository<UserVip>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    private readonly dataSource: DataSource,
    @Optional() private readonly cosmetics?: CosmeticsService,
  ) {}

  listPlans() {
    return this.ensurePlans().then(async () => {
      const plans = await this.plansRepo.find({
        where: { isActive: true },
        order: { level: 'ASC' },
      });
      return plans.map((p) => ({
        ...p,
        durationPacks: VIP_DURATION_PACKS.map((pack) => ({
          ...pack,
          coinPrice: vipPriceForDays(p.coinPriceMonthly, pack.days),
        })),
        // Default display = monthly (never permanent).
        durationDays: 30,
      }));
    });
  }

  /** Use existing VIP plan rows as-is. Never invent/overwrite prices from code. */
  private async ensurePlans() {
    const count = await this.plansRepo.count();
    if (count > 0 && !bootCatalogSeedEnabled()) {
      return;
    }
    if (count > 0 && bootCatalogSeedEnabled()) {
      // Seed mode with data: fill any missing levels only, never override existing.
    }
    for (let level = 1; level <= VipService.MAX_VIP_LEVEL; level++) {
      const price = VipService.vipPriceForLevel(level);
      const medalUrl = vipMedalUrl(level);
      const benefits = {
        entryEffect: level >= 1,
        antiKick: level >= 3,
        antiMute: level >= 4,
        hostBadge: level >= 2,
        exclusiveGifts: level >= 5,
        flyingComment: level >= 6,
        roomPriority: level,
        dmLimit: 50 + level * 20,
        extra: [
          level >= 15 ? 'priority_join_queue' : null,
          level >= 25 ? 'faster_profile_highlight' : null,
          level >= 40 ? 'premium_toast_variant' : null,
          level >= 55 ? 'advanced_room_theme_unlock' : null,
          level >= 70 ? 'supporter_spotlight' : null,
          level >= 85 ? 'ultra_entry_presence' : null,
          level >= 100 ? 'vip_master_100' : null,
        ].filter((item): item is string => !!item),
      };
      const existing = await this.plansRepo.findOne({ where: { level } });
      if (existing) continue;
      if (!bootCatalogSeedEnabled() && count > 0) continue;
      await this.plansRepo.save(
        this.plansRepo.create({
          level,
          name: `VIP${level}`,
          coinPriceMonthly: price,
          badgeUrl: medalUrl,
          benefits,
          isActive: true,
        }),
      );
    }
  }

  async myVip(userId: string) {
    return this.userVipsRepo.findOne({
      where: { userId, isActive: true, expiresAt: MoreThan(new Date()) },
      relations: ['vipPlan'],
      order: { level: 'DESC' },
    });
  }

  async purchase(userId: string, dto: PurchaseVipDto) {
    await this.ensurePlans();
    const plan = await this.plansRepo.findOne({ where: { level: dto.level, isActive: true } });
    if (!plan) throw new NotFoundException('VIP plan not found');

    const durationDays = VIP_ALLOWED_DAYS.includes(Number(dto.durationDays) as 7 | 30 | 40)
      ? Number(dto.durationDays)
      : 30;
    const pack = vipPackForDays(durationDays);
    const price = vipPriceForDays(plan.coinPriceMonthly, durationDays);

    const result = await this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet || Number(wallet.coins) < price) {
        throw new BadRequestException('Insufficient coins');
      }
      wallet.coins = Number(wallet.coins) - price;
      await manager.save(wallet);

      await manager.update(UserVip, { userId, isActive: true }, { isActive: false });

      const startsAt = new Date();
      const expiresAt = new Date(startsAt.getTime() + durationDays * 24 * 60 * 60 * 1000);
      const userVip = await manager.save(
        manager.create(UserVip, {
          userId,
          vipPlanId: plan.id,
          level: plan.level,
          startsAt,
          expiresAt,
          isActive: true,
        }),
      );

      await manager.save(
        manager.create(WalletTransaction, {
          userId,
          type: TransactionType.ADMIN_ADJUST,
          currency: CurrencyType.COINS,
          amount: -price,
          balanceAfter: Number(wallet.coins),
          referenceType: 'vip_purchase',
          referenceId: userVip.id,
          description: `Purchased VIP${plan.level} (${pack.labelEn} · ${durationDays}d)`,
        }),
      );

      return {
        userVip,
        plan,
        balance: Number(wallet.coins),
        durationDays,
        pack,
        coinPrice: price,
      };
    });

    // Cosmetics expire with VIP — never permanent.
    let cosmetics: unknown = null;
    if (this.cosmetics) {
      try {
        cosmetics = await this.cosmetics.grantVipAristocracyBundle(
          userId,
          plan.level,
          durationDays,
        );
      } catch {
        cosmetics = { error: 'cosmetics_grant_failed' };
      }
    }

    return { ...result, cosmetics };
  }

  /**
   * Free VIP grants are disabled — VIP is purchase-only.
   * Callers (tasks / host-target) may still invoke; we no-op safely.
   */
  async grantTemporaryVip(userId: string, level: number, days: number) {
    this.logger.warn(
      `Blocked free VIP grant user=${userId} level=${level} days=${days} (VIP is purchase-only)`,
    );
    return {
      blocked: true,
      reason: 'vip_purchase_only',
      userId,
      level: Math.max(0, Math.floor(Number(level) || 0)),
      days: Math.max(0, Math.floor(Number(days) || 0)),
      cosmetics: null,
    };
  }
}
