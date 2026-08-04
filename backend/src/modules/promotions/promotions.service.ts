import { Injectable, Logger, OnModuleDestroy, OnModuleInit, Optional } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, DataSource, LessThanOrEqual } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { UserPromoProgress } from '../../database/entities/user-promo-progress.entity';
import { User } from '../../database/entities/user.entity';
import {
  VanityId,
  VanityIdStatus,
} from '../../database/entities/vanity-id.entity';
import { CosmeticsService } from '../cosmetics/cosmetics.service';
import {
  PROMO_CATALOG_VERSION,
  MONTHLY_RECHARGE_OFFERS,
  SUPPORTER_PACKS,
  AGENT_FLOAT_BONUS_TIERS,
  VIP_DURATION_PACKS,
  currentMonthKey,
  agentFloatBonusPercent,
} from '../../common/promo-catalog';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';

@Injectable()
export class PromotionsService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(PromotionsService.name);
  private vanityExpireTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(UserPromoProgress)
    private readonly progressRepo: Repository<UserPromoProgress>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(VanityId)
    private readonly vanityRepo: Repository<VanityId>,
    private readonly dataSource: DataSource,
    @Optional() private readonly cosmetics?: CosmeticsService,
  ) {}

  async onModuleInit() {
    try {
      await this.ensureCatalogSetting();
      if (bootCatalogSeedEnabled()) {
        await this.ensurePromoCosmetics();
        await this.ensurePromoVanityPool();
      } else {
        this.logger.log('Promotions: DB authoritative (no boot promo seed)');
      }
    } catch (err) {
      this.logger.warn(`promo init: ${(err as Error).message}`);
    }
    // Hourly expiry for timed special IDs (no @nestjs/schedule in this project).
    this.vanityExpireTimer = setInterval(() => {
      void this.expireVanityLeases().catch((err) =>
        this.logger.warn(`vanity expire cron: ${(err as Error).message}`),
      );
    }, 60 * 60 * 1000);
    if (typeof this.vanityExpireTimer.unref === 'function') {
      this.vanityExpireTimer.unref();
    }
    void this.expireVanityLeases().catch(() => undefined);
  }

  onModuleDestroy() {
    if (this.vanityExpireTimer) {
      clearInterval(this.vanityExpireTimer);
      this.vanityExpireTimer = null;
    }
  }

  /** Public catalog for app + dashboard. */
  getCatalog() {
    return {
      version: PROMO_CATALOG_VERSION,
      vipDurations: VIP_DURATION_PACKS,
      monthlyOffers: MONTHLY_RECHARGE_OFFERS,
      agentTiers: AGENT_FLOAT_BONUS_TIERS,
      supporterPacks: SUPPORTER_PACKS,
    };
  }

  async myProgress(userId: string) {
    const monthKey = currentMonthKey();
    let row = await this.progressRepo.findOne({ where: { userId, monthKey } });
    if (!row) {
      row = this.progressRepo.create({
        userId,
        monthKey,
        usdSpent: 0,
        claimedOfferIds: [],
      });
      await this.progressRepo.save(row);
    }
    const spent = Number(row.usdSpent) || 0;
    return {
      monthKey,
      usdSpent: spent,
      claimedOfferIds: row.claimedOfferIds || [],
      monthlyOffers: MONTHLY_RECHARGE_OFFERS.map((o) => ({
        ...o,
        progressUsd: Math.min(spent, o.thresholdUsd),
        claimed: (row!.claimedOfferIds || []).includes(o.id),
        unlocked: spent >= o.thresholdUsd,
      })),
      supporterPacks: SUPPORTER_PACKS.map((o) => ({
        ...o,
        progressUsd: Math.min(spent, o.thresholdUsd),
        claimed: (row!.claimedOfferIds || []).includes(o.id),
        unlocked: spent >= o.thresholdUsd,
      })),
      agentTiers: AGENT_FLOAT_BONUS_TIERS,
      vipDurations: VIP_DURATION_PACKS,
    };
  }

  /**
   * After a successful user recharge: accumulate monthly USD and grant unlocked rewards.
   * Marks claimed only after successful delivery (avoids lost rewards).
   */
  async onUserRechargeCompleted(userId: string, priceUsd: number) {
    const usd = Math.max(0, Number(priceUsd) || 0);
    if (!userId || usd <= 0) return { skipped: true };

    const monthKey = currentMonthKey();
    const unlocked: string[] = [];

    await this.dataSource.transaction(async (manager) => {
      let row = await manager.findOne(UserPromoProgress, {
        where: { userId, monthKey },
        lock: { mode: 'pessimistic_write' },
      });
      if (!row) {
        row = manager.create(UserPromoProgress, {
          userId,
          monthKey,
          usdSpent: 0,
          claimedOfferIds: [],
        });
      }
      row.usdSpent = Number((Number(row.usdSpent) + usd).toFixed(2));
      const claimed = new Set(row.claimedOfferIds || []);
      for (const offer of MONTHLY_RECHARGE_OFFERS) {
        if (claimed.has(offer.id)) continue;
        if (Number(row.usdSpent) < offer.thresholdUsd) continue;
        unlocked.push(offer.id);
      }
      for (const pack of SUPPORTER_PACKS) {
        if (claimed.has(pack.id)) continue;
        if (Number(row.usdSpent) < pack.thresholdUsd) continue;
        unlocked.push(pack.id);
      }
      await manager.save(row);
    });

    const awarded: string[] = [];
    for (const id of unlocked) {
      try {
        await this.deliverReward(userId, id);
        awarded.push(id);
      } catch (err) {
        this.logger.warn(`deliver ${id} → ${userId}: ${(err as Error).message}`);
      }
    }

    if (awarded.length) {
      await this.dataSource.transaction(async (manager) => {
        const row = await manager.findOne(UserPromoProgress, {
          where: { userId, monthKey },
          lock: { mode: 'pessimistic_write' },
        });
        if (!row) return;
        const claimed = new Set(row.claimedOfferIds || []);
        for (const id of awarded) claimed.add(id);
        row.claimedOfferIds = [...claimed];
        await manager.save(row);
      });
    }

    return { awarded, monthKey, unlocked };
  }

  /**
   * Agent float top-up bonus: return extra coins to add on top of `baseCoins`
   * when the USDT value hits a tier.
   */
  computeAgentFloatBonusCoins(baseCoins: number, usdAmount: number): {
    bonusPercent: number;
    bonusCoins: number;
  } {
    const pct = agentFloatBonusPercent(usdAmount);
    const bonusCoins = pct > 0 ? Math.floor((Math.max(0, baseCoins) * pct) / 100) : 0;
    return { bonusPercent: pct, bonusCoins };
  }

  /** Expire timed vanity leases and restore previous publicId. */
  async expireVanityLeases() {
    const now = new Date();
    const expired = await this.vanityRepo.find({
      where: {
        status: VanityIdStatus.OWNED,
        expiresAt: LessThanOrEqual(now),
      },
      take: 200,
    });
    // Skip permanent purchases (expiresAt null) — LessThanOrEqual already excludes null in SQL.
    let count = 0;
    for (const row of expired) {
      if (!row.expiresAt) continue;
      try {
        if (row.ownerUserId) {
          const user = await this.usersRepo.findOne({ where: { id: row.ownerUserId } });
          if (user && user.publicId === row.publicId) {
            user.publicId = row.previousPublicId || user.id.replace(/-/g, '').slice(0, 8);
            await this.usersRepo.save(user);
          }
        }
        row.status = VanityIdStatus.AVAILABLE;
        row.ownerUserId = null;
        row.expiresAt = null;
        row.previousPublicId = null;
        row.purchasedAt = null;
        await this.vanityRepo.save(row);
        count += 1;
      } catch (err) {
        this.logger.warn(`expire vanity ${row.publicId}: ${(err as Error).message}`);
      }
    }
    if (count > 0) this.logger.log(`Expired ${count} timed vanity ID(s)`);
    return { expired: count };
  }

  private async deliverReward(userId: string, offerId: string) {
    const monthly = MONTHLY_RECHARGE_OFFERS.find((o) => o.id === offerId);
    if (monthly) {
      await this.grantPromoFrame(userId, monthly.cosmeticCode, monthly.rewardDays);
      return;
    }
    const supporter = SUPPORTER_PACKS.find((o) => o.id === offerId);
    if (supporter) {
      await this.grantPromoFrame(
        userId,
        supporter.frameCosmeticCode,
        supporter.rewardDays,
      );
      await this.grantTimedSpecialId(userId, supporter.rewardDays);
    }
  }

  private async grantPromoFrame(userId: string, code: string, days: number) {
    if (!this.cosmetics) return;
    await this.cosmetics.ensurePromoCosmetics();
    await this.cosmetics.grantTemporary(userId, code, days, { forceTimed: true });
  }

  private async grantTimedSpecialId(userId: string, days: number) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) return;

    let row = await this.vanityRepo.findOne({
      where: { status: VanityIdStatus.AVAILABLE },
      order: { priceCoins: 'ASC', publicId: 'ASC' },
    });
    if (!row) {
      const candidate = `8${String(Date.now()).slice(-7)}`;
      row = this.vanityRepo.create({
        publicId: candidate,
        status: VanityIdStatus.AVAILABLE,
        priceCoins: 0,
      });
      await this.vanityRepo.save(row);
    }

    const previous = user.publicId;
    user.publicId = row.publicId;
    await this.usersRepo.save(user);

    row.status = VanityIdStatus.OWNED;
    row.ownerUserId = userId;
    row.purchasedAt = new Date();
    row.expiresAt = new Date(Date.now() + Math.max(1, days) * 24 * 60 * 60 * 1000);
    row.previousPublicId = previous;
    row.reservedUntil = null;
    await this.vanityRepo.save(row);
  }

  private async ensureCatalogSetting() {
    const key = 'promo_catalog';
    const existing = await this.settingsRepo.findOne({ where: { key } });
    // Never overwrite dashboard/production promo catalog with code defaults.
    if (existing?.value) return;
    if (!bootCatalogSeedEnabled()) {
      this.logger.log('Promo catalog: DB authoritative (empty, seed disabled)');
      return;
    }
    const value = JSON.stringify(this.getCatalog());
    await this.settingsRepo.save(
      this.settingsRepo.create({
        key,
        value,
        description: 'Monthly / agent / supporter / VIP duration promos',
      }),
    );
  }

  private async ensurePromoCosmetics() {
    if (!this.cosmetics) return;
    await this.cosmetics.ensurePromoCosmetics();
  }

  /** Seed a small pool of low-price vanity IDs for supporter promo leases. */
  private async ensurePromoVanityPool() {
    const seeds = ['888888', '666666', '999999', '777777', '555555', '168168', '520520', '131452'];
    for (const publicId of seeds) {
      const existing = await this.vanityRepo.findOne({ where: { publicId } });
      if (existing) continue;
      await this.vanityRepo.save(
        this.vanityRepo.create({
          publicId,
          status: VanityIdStatus.AVAILABLE,
          priceCoins: 0,
        }),
      );
    }
  }
}
