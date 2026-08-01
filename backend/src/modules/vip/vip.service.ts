import { Injectable, NotFoundException, BadRequestException, Optional } from '@nestjs/common';
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
import { IsInt, Min, Max } from 'class-validator';
import { ApiProperty } from '@nestjs/swagger';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import { vipMedalUrl } from '../../common/vip-assets';

export class PurchaseVipDto {
  @ApiProperty({ minimum: 1, maximum: 100 })
  @IsInt()
  @Min(1)
  @Max(100)
  level: number;
}

@Injectable()
export class VipService {
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
    return this.ensurePlans().then(() =>
      this.plansRepo.find({ where: { isActive: true }, order: { level: 'ASC' } }),
    );
  }

  /** Ensure VIP1–100 plans exist and stay in sync. Medals are Mikoo VIP1–7 art. */
  private async ensurePlans() {
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
      if (!existing) {
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
        continue;
      }
      existing.name = `VIP${level}`;
      existing.coinPriceMonthly = price;
      // Always keep Mikoo medal sequence VIP1→medal1 … VIP7→medal7 (higher → medal7).
      existing.badgeUrl = medalUrl;
      existing.benefits = { ...(existing.benefits || {}), ...benefits };
      existing.isActive = true;
      await this.plansRepo.save(existing);
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

    const result = await this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet || Number(wallet.coins) < plan.coinPriceMonthly) {
        throw new BadRequestException('Insufficient coins');
      }
      wallet.coins = Number(wallet.coins) - plan.coinPriceMonthly;
      await manager.save(wallet);

      await manager.update(UserVip, { userId, isActive: true }, { isActive: false });

      const startsAt = new Date();
      const expiresAt = new Date(startsAt.getTime() + 30 * 24 * 60 * 60 * 1000);
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
          amount: -plan.coinPriceMonthly,
          balanceAfter: Number(wallet.coins),
          referenceType: 'vip_purchase',
          referenceId: userVip.id,
          description: `Purchased VIP${plan.level}`,
        }),
      );

      return { userVip, plan, balance: Number(wallet.coins) };
    });

    // After VIP is active: grant + equip matching aristocracy cosmetics.
    let cosmetics: unknown = null;
    if (this.cosmetics) {
      try {
        cosmetics = await this.cosmetics.grantVipAristocracyBundle(userId, plan.level);
      } catch {
        cosmetics = { error: 'cosmetics_grant_failed' };
      }
    }

    return { ...result, cosmetics };
  }

  /** Contest / host-target / task prize — grant VIP for N days without charging coins. */
  async grantTemporaryVip(userId: string, level: number, days: number) {
    await this.ensurePlans();
    const lvl = Math.max(1, Math.min(VipService.MAX_VIP_LEVEL, Math.floor(Number(level) || 1)));
    const daysSafe = Math.max(1, Math.min(365, Math.floor(Number(days) || 7)));
    const plan = await this.plansRepo.findOne({ where: { level: lvl, isActive: true } });
    if (!plan) throw new NotFoundException(`VIP plan ${lvl} not found`);

    await this.userVipsRepo.update({ userId, isActive: true }, { isActive: false });
    const startsAt = new Date();
    const expiresAt = new Date(startsAt.getTime() + daysSafe * 24 * 60 * 60 * 1000);
    const userVip = await this.userVipsRepo.save(
      this.userVipsRepo.create({
        userId,
        vipPlanId: plan.id,
        level: plan.level,
        startsAt,
        expiresAt,
        isActive: true,
      }),
    );

    let cosmetics: unknown = null;
    if (this.cosmetics) {
      try {
        cosmetics = await this.cosmetics.grantVipAristocracyBundle(userId, plan.level);
      } catch {
        cosmetics = { error: 'cosmetics_grant_failed' };
      }
    }
    return { userVip, plan, days: daysSafe, cosmetics };
  }
}
