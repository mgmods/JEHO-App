import { BadRequestException, Injectable, Logger, NotFoundException, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, IsNull, MoreThan, Repository } from 'typeorm';
import { existsSync, readFileSync } from 'fs';
import { join } from 'path';

/** Resolve Nest public/ whether cwd is backend root or dist/. */
function resolvePublicDir(): string {
  const candidates = [
    join(process.cwd(), 'public'),
    join(__dirname, '..', '..', '..', 'public'),
    join(__dirname, '..', '..', 'public'),
  ];
  for (const c of candidates) {
    if (existsSync(c)) return c;
  }
  return candidates[0];
}
import { Cosmetic, CosmeticType } from '../../database/entities/cosmetic.entity';
import { UserCosmetic } from '../../database/entities/user-cosmetic.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { MediaCleanupService } from '../uploads/media-cleanup.service';
import { MALL_COSMETIC_PRICES, PRICING_VERSION } from '../../common/pricing-catalog';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';
import { isStaticVipTouUrl } from '../../common/vip-visual';

type MikooCatalogItem = {
  type: CosmeticType | string;
  code: string;
  name: string;
  previewUrl: string;
  animationUrl?: string | null;
  coinPrice?: number;
  minVipLevel?: number;
  minUserLevel?: number;
  sortOrder?: number;
  meta?: Record<string, unknown>;
};

@Injectable()
export class CosmeticsService implements OnModuleInit {
  private aristocracyEnsured = false;
  private mallPricingEnsured = false;
  private roomBgEnsured = false;
  private mikooCatalogEnsured = false;
  private hostBadgeEnsured = false;
  private readonly logger = new Logger(CosmeticsService.name);

  constructor(
    @InjectRepository(Cosmetic) private readonly cosmeticsRepo: Repository<Cosmetic>,
    @InjectRepository(UserCosmetic) private readonly userCosmeticsRepo: Repository<UserCosmetic>,
    @InjectRepository(UserProfile) private readonly profileRepo: Repository<UserProfile>,
    @InjectRepository(Wallet) private readonly walletRepo: Repository<Wallet>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(UserVip) private readonly userVipRepo: Repository<UserVip>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    private readonly mediaCleanup: MediaCleanupService,
    private readonly dataSource: DataSource,
  ) {}

  async onModuleInit() {
    // Always install fixed VIP head frames + re-bind VIP gates (safe on production).
    try {
      await this.ensureFixedVipTouFrames();
      await this.rebindVipFrameGates();
      await this.healStaticVipTouWear();
    } catch (err) {
      this.logger.warn(`VIP frame gate rebind: ${(err as Error).message}`);
    }
    // Live DB is the catalog. Boot mutators only when BOOT_SEED_CATALOGS=1.
    if (!bootCatalogSeedEnabled()) {
      this.logger.log('Cosmetics catalog: DB authoritative (no boot seed)');
      return;
    }
    try {
      await this.ensureRoomBackgroundCatalog();
      await this.ensureAristocracyCatalog();
      await this.ensureRoomCardCatalog();
      await this.ensureHostBadgeCatalog();
      await this.ensureMikooCosmeticsCatalog();
      await this.ensurePromoCosmetics();
      await this.purgeBrokenEntryEffects();
      await this.ensureMallPricing();
      await this.ensurePublicBranding();
    } catch (err) {
      this.logger.warn(`cosmetics pricing init: ${(err as Error).message}`);
    }
  }

  async catalog(type?: CosmeticType) {
    this.assertSupportedType(type);
    // Read-only from DB — never re-sync prices on every API hit.
    const where: any = { isActive: true };
    if (type) where.type = type;
    return this.cosmeticsRepo.find({ where, order: { sortOrder: 'ASC', coinPrice: 'ASC' } });
  }

  async adminList(type?: CosmeticType) {
    this.assertSupportedType(type);
    const where: any = {};
    if (type) where.type = type;
    return this.cosmeticsRepo.find({ where, order: { sortOrder: 'ASC', createdAt: 'DESC' } });
  }

  async adminCreate(dto: Partial<Cosmetic> & { type: CosmeticType; code: string; name: string; previewUrl: string }) {
    this.assertSupportedType(dto.type);
    const code = String(dto.code || '').trim();
    const name = String(dto.name || '').trim();
    if (!code || !name || !dto.previewUrl) {
      throw new BadRequestException('type, code, name, previewUrl مطلوبة');
    }
    const row = this.cosmeticsRepo.create({
      type: dto.type,
      code,
      name,
      description: dto.description || null,
      previewUrl: dto.previewUrl,
      animationUrl: dto.animationUrl || null,
      coinPrice: Number(dto.coinPrice || 0),
      minVipLevel: Number(dto.minVipLevel || 0),
      // Head frames / host wear: VIP only — never account level.
      minUserLevel:
        dto.type === CosmeticType.VIP_BADGE || dto.type === CosmeticType.HOST_BADGE
          ? 0
          : Number(dto.minUserLevel || 0),
      isActive: dto.isActive !== false,
      sortOrder: Number(dto.sortOrder || 0),
      meta: dto.meta || null,
    });
    return this.cosmeticsRepo.save(row);
  }

  private assertSupportedType(type?: CosmeticType) {
    if (type && !Object.values(CosmeticType).includes(type)) {
      throw new BadRequestException('Unsupported cosmetic type');
    }
  }

  async adminUpdate(id: string, dto: Partial<Cosmetic>) {
    const row = await this.cosmeticsRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('العنصر غير موجود');
    if (dto.type != null) {
      this.assertSupportedType(dto.type);
      row.type = dto.type;
    }
    if (dto.code != null) row.code = String(dto.code).trim();
    if (dto.name != null) row.name = String(dto.name).trim();
    if (dto.description !== undefined) row.description = dto.description || null;
    if (dto.previewUrl != null) {
      this.mediaCleanup.replaceUpload(row.previewUrl, dto.previewUrl);
      if (
        String(row.previewUrl || '').startsWith('/assets/') &&
        String(dto.previewUrl).startsWith('/assets/') &&
        row.previewUrl !== dto.previewUrl
      ) {
        this.mediaCleanup.deletePublicAssetPath(row.previewUrl);
      }
      row.previewUrl = dto.previewUrl;
    }
    if (dto.animationUrl !== undefined) {
      this.mediaCleanup.replaceUpload(row.animationUrl, dto.animationUrl || null);
      row.animationUrl = dto.animationUrl || null;
    }
    if (dto.coinPrice != null) row.coinPrice = Number(dto.coinPrice);
    if (dto.minVipLevel != null) row.minVipLevel = Number(dto.minVipLevel);
    if (dto.minUserLevel != null) row.minUserLevel = Number(dto.minUserLevel);
    // Frames / headwear are VIP-bound only (dashboard accident: "level 20" was account level).
    if (row.type === CosmeticType.VIP_BADGE || row.type === CosmeticType.HOST_BADGE) {
      row.minUserLevel = 0;
    }
    if (dto.isActive != null) row.isActive = !!dto.isActive;
    if (dto.sortOrder != null) row.sortOrder = Number(dto.sortOrder);
    if (dto.meta !== undefined) row.meta = dto.meta;
    return this.cosmeticsRepo.save(row);
  }

  async adminDelete(id: string) {
    const row = await this.cosmeticsRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('العنصر غير موجود');

    const urls = [...new Set([row.previewUrl, row.animationUrl].filter(Boolean))] as string[];

    // Clear worn profile / room pointers that still reference these media URLs.
    for (const url of urls) {
      const clean = String(url).split('?')[0];
      await this.dataSource.query(
        `UPDATE user_profiles
            SET "hostBadgeUrl" = CASE WHEN "hostBadgeUrl" LIKE $1 THEN NULL ELSE "hostBadgeUrl" END,
                "vipBadgeUrl" = CASE WHEN "vipBadgeUrl" LIKE $1 THEN NULL ELSE "vipBadgeUrl" END,
                "levelBadgeUrl" = CASE WHEN "levelBadgeUrl" LIKE $1 THEN NULL ELSE "levelBadgeUrl" END,
                "entryEffectUrl" = CASE WHEN "entryEffectUrl" LIKE $1 THEN NULL ELSE "entryEffectUrl" END,
                "entryAnimationUrl" = CASE WHEN "entryAnimationUrl" LIKE $1 THEN NULL ELSE "entryAnimationUrl" END,
                "roomCardUrl" = CASE WHEN "roomCardUrl" LIKE $1 THEN NULL ELSE "roomCardUrl" END`,
        [`${clean}%`],
      ).catch(() => undefined);
      await this.dataSource.query(
        `UPDATE rooms
            SET "roomCardUrl" = NULL, "roomCardEquippedById" = NULL
          WHERE "roomCardUrl" LIKE $1`,
        [`${clean}%`],
      ).catch(() => undefined);
    }

    // Delete media files from disk when no other cosmetic still references them.
    for (const url of urls) {
      const references = await this.cosmeticsRepo
        .createQueryBuilder('cosmetic')
        .where('cosmetic.id != :id', { id })
        .andWhere(
          '(cosmetic."previewUrl" = :url OR cosmetic."animationUrl" = :url)',
          { url },
        )
        .getCount();
      if (references === 0) {
        this.mediaCleanup.deleteUploadUrl(url);
        this.mediaCleanup.deletePublicAssetPath(url);
      }
    }

    await this.userCosmeticsRepo.delete({ cosmeticId: id });
    await this.cosmeticsRepo.delete(id);
    this.logger.log(`Hard-deleted cosmetic ${row.code} (${id}) + media`);
    return { deleted: true, id, code: row.code };
  }

  async inventory(userId: string) {
    // Include expired rows so the mall can offer full-price repurchase.
    return this.userCosmeticsRepo.find({
      where: { userId },
      relations: ['cosmetic'],
      order: { createdAt: 'DESC' },
    });
  }

  private mallLeaseDays() {
    return 30;
  }

  private chargePrice(fullPrice: number, renew: boolean) {
    const full = Math.max(0, Math.floor(Number(fullPrice) || 0));
    return renew ? Math.ceil(full / 2) : full;
  }

  /**
   * Grant a cosmetic for a limited number of days (contest prizes, promos).
   * Extends from max(now, existing expiresAt) when already owned temporarily.
   * Permanent ownership (expiresAt null) is left permanent unless forceTimed.
   */
  async grantTemporary(
    userId: string,
    cosmeticCode: string,
    days: number,
    opts?: { forceTimed?: boolean },
  ) {
    const daysSafe = Math.max(1, Math.min(365, Math.floor(Number(days) || 7)));
    let cosmetic =
      (await this.cosmeticsRepo.findOne({
        where: { code: String(cosmeticCode || '').trim(), isActive: true },
      })) || null;

    if (!cosmetic) {
      cosmetic = await this.cosmeticsRepo
        .createQueryBuilder('c')
        .where('c.isActive = true')
        .andWhere('c.type IN (:...types)', {
          types: [
            CosmeticType.HOST_BADGE,
            CosmeticType.VIP_BADGE,
            CosmeticType.LEVEL_BADGE,
            CosmeticType.ROOM_CARD,
          ],
        })
        .andWhere(`COALESCE(c.meta->>'aristocracy', 'false') != 'true'`)
        .andWhere(`c.code NOT ILIKE '%_vip_%'`)
        .andWhere(`c.code NOT ILIKE 'vip%'`)
        .orderBy('c.sortOrder', 'ASC')
        .getOne();
    }
    if (!cosmetic) {
      throw new NotFoundException(`Cosmetic ${cosmeticCode} not found`);
    }

    const now = new Date();
    let owned = await this.userCosmeticsRepo.findOne({
      where: { userId, cosmeticId: cosmetic.id },
    });
    if (!owned) {
      owned = this.userCosmeticsRepo.create({
        userId,
        cosmeticId: cosmetic.id,
        equipped: false,
        expiresAt: new Date(now.getTime() + daysSafe * 24 * 60 * 60 * 1000),
      });
    } else if (owned.expiresAt == null && !opts?.forceTimed) {
      // Already permanent — keep permanent, still try equip.
    } else {
      const base =
        owned.expiresAt && owned.expiresAt.getTime() > now.getTime()
          ? owned.expiresAt.getTime()
          : now.getTime();
      owned.expiresAt = new Date(base + daysSafe * 24 * 60 * 60 * 1000);
    }
    owned = await this.userCosmeticsRepo.save(owned);

    try {
      await this.equip(userId, cosmetic.id, { skipRequirements: true });
    } catch {
      /* equip is best-effort */
    }

    return {
      granted: true,
      cosmetic,
      expiresAt: owned.expiresAt,
      days: daysSafe,
    };
  }

  /**
   * Upsert promo reward frames (monthly gifts + supporter packs).
   * Uses existing pack art so grants never fall back to a random mall badge.
   */
  async ensurePromoCosmetics() {
    const v = '20260802promo';
    const items: Array<{
      code: string;
      name: string;
      previewUrl: string;
      sortOrder: number;
      daysHint: number;
    }> = [
      {
        code: 'promo_monthly_gift_45',
        name: 'هدية الشحن الشهرية (45 يوم)',
        previewUrl: `/assets/pack/ff_challenge_frame_top1.png?v=${v}`,
        sortOrder: 9100,
        daysHint: 45,
      },
      {
        code: 'promo_monthly_gift_90',
        name: 'هدية الشحن الشهرية (90 يوم)',
        previewUrl: `/assets/pack/guardian_relation_cp_8_10.png?v=${v}`,
        sortOrder: 9101,
        daysHint: 90,
      },
      {
        code: 'promo_supporter_frame_7',
        name: 'إطار الداعم (7 أيام)',
        previewUrl: `/assets/pack/bg_main_activity_center.png?v=${v}`,
        sortOrder: 9110,
        daysHint: 7,
      },
      {
        code: 'promo_supporter_frame_15',
        name: 'إطار الداعم (15 يوم)',
        previewUrl: `/assets/pack/ff_challenge_frame_top1.png?v=${v}`,
        sortOrder: 9111,
        daysHint: 15,
      },
      {
        code: 'promo_supporter_frame_30',
        name: 'إطار الداعم (30 يوم)',
        previewUrl: `/assets/pack/guardian_relation_cp_8_10.png?v=${v}`,
        sortOrder: 9112,
        daysHint: 30,
      },
    ];

    for (const item of items) {
      let row = await this.cosmeticsRepo.findOne({ where: { code: item.code } });
      if (!row) {
        await this.cosmeticsRepo.save(
          this.cosmeticsRepo.create({
            type: CosmeticType.HOST_BADGE,
            code: item.code,
            name: item.name,
            description: `عرض ترويجي · ${item.daysHint} يوم`,
            previewUrl: item.previewUrl,
            animationUrl: null,
            coinPrice: 0,
            minVipLevel: 0,
            minUserLevel: 0,
            isActive: true,
            sortOrder: item.sortOrder,
            meta: { promo: true, rewardDays: item.daysHint, mallHidden: true },
          }),
        );
        continue;
      }
      row.type = CosmeticType.HOST_BADGE;
      row.name = item.name;
      row.description = `عرض ترويجي · ${item.daysHint} يوم`;
      row.previewUrl = item.previewUrl;
      row.coinPrice = 0;
      row.isActive = true;
      row.sortOrder = item.sortOrder;
      row.meta = { ...(row.meta || {}), promo: true, rewardDays: item.daysHint, mallHidden: true };
      await this.cosmeticsRepo.save(row);
    }
  }

  private async activeVipLevel(userId: string): Promise<number> {
    const vip = await this.userVipRepo.findOne({
      where: { userId, isActive: true, expiresAt: MoreThan(new Date()) },
      order: { level: 'DESC' },
    });
    return Number(vip?.level || 0);
  }

  /**
   * VIP plan (1–100) → visual frame/medal tier (1–7).
   * Used for grants; equip still checks cosmetic.minVipLevel (usually 1–7).
   */
  private vipVisualTier(vipLevel: number): number {
    return Math.min(7, Math.max(1, Math.floor(Number(vipLevel) || 1)));
  }

  private async assertCosmeticRequirements(userId: string, cosmetic: Cosmetic) {
    const user = await this.usersRepo.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');

    // Headwear/VIP frames are VIP-gated only — ignore accidental minUserLevel in DB.
    const isVipWear =
      cosmetic.type === CosmeticType.VIP_BADGE ||
      cosmetic.type === CosmeticType.HOST_BADGE ||
      (cosmetic.meta as any)?.aristocracy === true;

    const userLevel = Math.max(1, Number(user.level || 1));
    if (!isVipWear && cosmetic.minUserLevel > 0 && userLevel < cosmetic.minUserLevel) {
      throw new BadRequestException(
        `يتطلب مستوى الحساب ${cosmetic.minUserLevel}`,
      );
    }

    if (cosmetic.minVipLevel > 0) {
      const vipLevel = await this.activeVipLevel(userId);
      // Plan can be 1–100; compare against catalog min (visual tiers usually 1–7).
      if (vipLevel < cosmetic.minVipLevel) {
        throw new BadRequestException(
          `يتطلب VIP ${cosmetic.minVipLevel} (مستواك VIP ${vipLevel || 0})`,
        );
      }
    }
  }

  async purchase(userId: string, cosmeticId: string) {
    const cosmetic = await this.cosmeticsRepo.findOne({ where: { id: cosmeticId, isActive: true } });
    if (!cosmetic) throw new NotFoundException('Cosmetic not found');

    // Agency exclusive frames/cards: admin grant only — never mall purchase.
    const { isAgencyExclusiveCosmetic } = await import('../agencies/agency-perks');
    if (isAgencyExclusiveCosmetic(cosmetic.meta, cosmetic.code)) {
      throw new BadRequestException(
        'هذا الإطار/البطاقة حصرية للوكالات المعتمدة — تُمنح من إدارة التطبيق فقط',
      );
    }

    await this.assertCosmeticRequirements(userId, cosmetic);

    const fullPrice = Math.max(0, Math.floor(Number(cosmetic.coinPrice) || 0));
    const leaseDays = this.mallLeaseDays();
    const now = new Date();

    const result = await this.cosmeticsRepo.manager.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      let owned = await manager.findOne(UserCosmetic, {
        where: { userId, cosmeticId },
      });

      const active =
        !!owned &&
        (owned.expiresAt == null || owned.expiresAt.getTime() > now.getTime());
      // Renew at half price only while the lease is still active.
      const renew = active;
      const price = this.chargePrice(fullPrice, renew);

      if (price > 0) {
        if (!wallet || wallet.coins < price) {
          throw new BadRequestException('Insufficient coins');
        }
        wallet.coins -= price;
        await manager.save(Wallet, wallet);
        await manager.save(
          WalletTransaction,
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.GIFT_SEND,
            currency: CurrencyType.COINS,
            amount: -price,
            balanceAfter: wallet.coins,
            referenceType: 'cosmetic_purchase',
            referenceId: cosmeticId,
            description: renew
              ? `تجديد ${cosmetic.name || cosmetic.code} (${leaseDays} يوم · نصف السعر)`
              : `شراء ${cosmetic.name || cosmetic.code} (${leaseDays} يوم)`,
          }),
        );
      }

      if (!owned) {
        owned = manager.create(UserCosmetic, {
          userId,
          cosmeticId,
          equipped: false,
          expiresAt: new Date(now.getTime() + leaseDays * 24 * 60 * 60 * 1000),
        });
      } else {
        const base =
          renew && owned.expiresAt && owned.expiresAt.getTime() > now.getTime()
            ? owned.expiresAt.getTime()
            : now.getTime();
        // Always timed mall lease (max 30 days per purchase/renew).
        owned.expiresAt = new Date(base + leaseDays * 24 * 60 * 60 * 1000);
      }
      owned = await manager.save(UserCosmetic, owned);
      return { owned, renew, price, fullPrice, leaseDays, expiresAt: owned.expiresAt };
    });

    // Auto-equip after purchase/claim so the user wears it immediately.
    try {
      await this.equip(userId, cosmeticId, { skipRequirements: true });
    } catch {
      /* ownership is enough; equip may fail on rare races */
    }
    return result;
  }

  async equip(userId: string, cosmeticId: string, opts?: { skipRequirements?: boolean }) {
    const owned = await this.userCosmeticsRepo.findOne({
      where: { userId, cosmeticId },
      relations: ['cosmetic'],
    });
    if (!owned) throw new NotFoundException('You do not own this cosmetic');
    if (owned.expiresAt && owned.expiresAt <= new Date()) {
      throw new BadRequestException('This cosmetic has expired');
    }

    if (!opts?.skipRequirements) {
      await this.assertCosmeticRequirements(userId, owned.cosmetic);
    }

    const type = owned.cosmetic.type;
    const sameType = await this.userCosmeticsRepo.find({
      where: { userId },
      relations: ['cosmetic'],
    });
    for (const row of sameType) {
      if (row.cosmetic?.type === type) {
        row.equipped = row.id === owned.id;
        await this.userCosmeticsRepo.save(row);
      }
    }

    const profile = await this.profileRepo.findOne({ where: { userId } });
    if (profile) {
      if (type === CosmeticType.ENTRY_EFFECT || type === CosmeticType.JOIN_TOAST) {
        // Prefer playable ride (GIF/WebP/MP4); keep preview as static fallback.
        const anim = owned.cosmetic.animationUrl || '';
        const lower = String(anim).toLowerCase();
        const playable =
          !!anim &&
          !lower.includes('runtime.html') &&
          !lower.endsWith('.html') &&
          !lower.endsWith('.json');
        profile.entryEffectUrl = owned.cosmetic.previewUrl || (playable ? anim : null);
        profile.entryAnimationUrl = playable
          ? anim
          : owned.cosmetic.previewUrl || null;
      }
      if (type === CosmeticType.ROOM_CARD) {
        const anim = owned.cosmetic.animationUrl || '';
        const lower = String(anim).toLowerCase();
        const playable =
          !!anim &&
          !lower.includes('runtime.html') &&
          !lower.endsWith('.html') &&
          !lower.endsWith('.json');
        const cardUrl = playable
          ? anim
          : owned.cosmetic.previewUrl || null;
        profile.roomCardUrl = cardUrl;
        // Sync outer list frame onto every room this user hosts.
        await this.dataSource.query(
          `UPDATE rooms
              SET "roomCardUrl" = $1, "roomCardEquippedById" = $2
            WHERE "hostId" = $2`,
          [cardUrl, userId],
        ).catch(() => undefined);
      }
      // room_background is applied per-room via PATCH /rooms/:id/background (not profile).
      if (type === CosmeticType.HOST_BADGE) {
        // Prefer GIF/WebP/MP4 animation when provided; skip HTML/JSON engines.
        const anim = owned.cosmetic.animationUrl || '';
        const lower = String(anim).toLowerCase();
        const playable =
          !!anim &&
          !lower.includes('runtime.html') &&
          !lower.endsWith('.html') &&
          !lower.endsWith('.json');
        profile.hostBadgeUrl = playable
          ? anim
          : owned.cosmetic.previewUrl || null;
      }
      if (type === CosmeticType.VIP_BADGE) {
        const anim = owned.cosmetic.animationUrl || '';
        const lower = String(anim).toLowerCase();
        const playable =
          !!anim &&
          !lower.includes('runtime.html') &&
          !lower.endsWith('.html') &&
          !lower.endsWith('.json');
        const preview = owned.cosmetic.previewUrl || null;
        const next = playable ? anim : preview;
        // Mikoo: static vip_tou is not avatar wear — do not overwrite mall/SVGA frames.
        if (
          (owned.cosmetic.meta as any)?.fixedVipFrame === true ||
          isStaticVipTouUrl(preview) ||
          isStaticVipTouUrl(anim)
        ) {
          // Grant inventory only; keep existing profile.vipBadgeUrl wear.
        } else {
          profile.vipBadgeUrl = next;
        }
      }
      if (type === CosmeticType.LEVEL_BADGE) {
        profile.levelBadgeUrl = owned.cosmetic.previewUrl;
      }
      await this.profileRepo.save(profile);
    }

    return { equipped: true, cosmetic: owned.cosmetic, profile };
  }

  /** Clear wear for this cosmetic type (frame / entry / badge / room card). */
  async unequip(userId: string, cosmeticId: string) {
    const owned = await this.userCosmeticsRepo.findOne({
      where: { userId, cosmeticId },
      relations: ['cosmetic'],
    });
    if (!owned) throw new NotFoundException('You do not own this cosmetic');

    const type = owned.cosmetic.type;
    const sameType = await this.userCosmeticsRepo.find({
      where: { userId },
      relations: ['cosmetic'],
    });
    for (const row of sameType) {
      if (row.cosmetic?.type === type && row.equipped) {
        row.equipped = false;
        await this.userCosmeticsRepo.save(row);
      }
    }

    const profile = await this.profileRepo.findOne({ where: { userId } });
    if (profile) {
      if (type === CosmeticType.ENTRY_EFFECT || type === CosmeticType.JOIN_TOAST) {
        profile.entryEffectUrl = null;
        profile.entryAnimationUrl = null;
      }
      if (type === CosmeticType.ROOM_CARD) {
        profile.roomCardUrl = null;
        await this.dataSource.query(
          `UPDATE rooms
              SET "roomCardUrl" = NULL, "roomCardEquippedById" = NULL
            WHERE "hostId" = $1
              AND "roomCardEquippedById" = $1`,
          [userId],
        ).catch(() => undefined);
      }
      if (type === CosmeticType.HOST_BADGE) {
        profile.hostBadgeUrl = null;
      }
      if (type === CosmeticType.VIP_BADGE) {
        profile.vipBadgeUrl = null;
      }
      if (type === CosmeticType.LEVEL_BADGE) {
        profile.levelBadgeUrl = null;
      }
      await this.profileRepo.save(profile);
    }

    return { equipped: false, cosmetic: owned.cosmetic, profile };
  }

  /**
   * VIP plans are 1..100; Mikoo visual frames/medals are VIP1–7 only.
   * Map plan level → visual tier 1..7 (same as vipMedalTier), then grant + equip.
   */
  async grantVipAristocracyBundle(userId: string, vipLevel: number, days = 30) {
    const vip = Math.min(100, Math.max(1, Math.floor(Number(vipLevel) || 1)));
    const level = this.vipVisualTier(vip);
    const daysSafe = Math.max(1, Math.min(365, Math.floor(Number(days) || 30)));
    await this.ensureAristocracyCatalog();

    // Only auto-equip medals. Fixed VIP head frames (ud_vip_tou) are display-only
    // (Mikoo: vip_tou is separate from mall/SVGA wear) — never overwrite wear.
    const types: CosmeticType[] = [
      CosmeticType.LEVEL_BADGE,
      CosmeticType.VIP_BADGE, // owned in inventory, not force-equipped
      CosmeticType.HOST_BADGE,
    ];

    const granted: Array<{ type: CosmeticType; code: string; expiresAt?: Date | null }> = [];

    for (const type of types) {
      const item = await this.pickAristocracyItem(type, level);
      if (!item) continue;
      // VIP rentals are time-limited — never grant permanent cosmetics with VIP.
      const temp = await this.grantTemporary(userId, item.code, daysSafe, {
        forceTimed: true,
      });
      granted.push({ type, code: item.code, expiresAt: temp?.expiresAt ?? null });
      // Equip medals only (level_badge). Never auto-equip fixed VIP frames over SVGA wear.
      if (type === CosmeticType.LEVEL_BADGE) {
        try {
          await this.equip(userId, item.id, { skipRequirements: true });
        } catch {
          // ignore rare races
        }
      }
    }

    return { vipLevel: vip, visualTier: level, days: daysSafe, granted };
  }

  private async ensureOwned(userId: string, cosmeticId: string) {
    const existing = await this.userCosmeticsRepo.findOne({ where: { userId, cosmeticId } });
    if (existing) return existing;
    return this.userCosmeticsRepo.save(
      this.userCosmeticsRepo.create({ userId, cosmeticId, equipped: false }),
    );
  }

  private async pickAristocracyItem(type: CosmeticType, level: number): Promise<Cosmetic | null> {
    // Fixed VIP head frames (ud_vip_tou) — never pick paid mall "frames" for VIP grant.
    if (type === CosmeticType.VIP_BADGE) {
      const fixed = await this.cosmeticsRepo.findOne({
        where: { code: `vip_tou_fixed_${level}`, isActive: true },
      });
      if (fixed) return fixed;
      const anyFixed = await this.cosmeticsRepo.find({
        where: { type: CosmeticType.VIP_BADGE, minVipLevel: level, isActive: true },
        order: { sortOrder: 'ASC' },
      });
      const match = anyFixed.find((c) => (c.meta as any)?.fixedVipFrame);
      if (match) return match;
      return null;
    }

    const exact = await this.cosmeticsRepo.findOne({
      where: { type, minVipLevel: level, isActive: true },
      order: { sortOrder: 'ASC' },
    });
    if (exact) return exact;

    const codeHints = [
      `aristocracy_${type}_${level}`,
      `host_vip_${level}`,
      `host_lv${level}`,
      `vip${level}`,
      `level_vip_${level}`,
      `level_${level}`,
      `frame_vip_${level}`,
      `toast_vip_${level}`,
      `entry_vip_${level}`,
      `card_vip_${level}`,
    ];
    for (const code of codeHints) {
      const byCode = await this.cosmeticsRepo.findOne({ where: { code, isActive: true } });
      if (byCode && byCode.type === type) return byCode;
    }

    if (type === CosmeticType.LEVEL_BADGE) {
      const byUserLevel = await this.cosmeticsRepo.findOne({
        where: { type, minUserLevel: level, isActive: true },
        order: { sortOrder: 'ASC' },
      });
      if (byUserLevel) return byUserLevel;
    }

    const all = await this.cosmeticsRepo.find({
      where: { type, isActive: true },
      order: { sortOrder: 'ASC', minVipLevel: 'ASC' },
    });
    if (!all.length) return null;
    const leq = all.filter((c) => Number(c.minVipLevel || 0) <= level && Number(c.minVipLevel || 0) > 0);
    if (leq.length) return leq[leq.length - 1];
    return all[Math.min(level - 1, all.length - 1)] || all[0];
  }

  /** Keep only persistent VIP badges, level badges, and room-card grants. */
  /**
   * Reasonable mall prices like major social apps.
   * Only true VIP-plan aristocracy rows stay coinPrice=0 (unlocked by VIP).
   * Mall frames whose code merely contains "_vip_" stay paid.
   */
  async ensureMallPricing() {
    if (this.mallPricingEnsured) return;
    if (!bootCatalogSeedEnabled()) {
      this.mallPricingEnsured = true;
      return;
    }
    const verKey = 'pricing.cosmetics_version';
    const row = await this.settingsRepo.findOne({ where: { key: verKey } });
    if (row?.value === PRICING_VERSION) {
      this.mallPricingEnsured = true;
      return;
    }

    const all = await this.cosmeticsRepo.find({ where: { isActive: true } });
    const byType: Record<string, Cosmetic[]> = {};
    for (const c of all) {
      const key = String(c.type);
      if (!byType[key]) byType[key] = [];
      byType[key].push(c);
    }

    let updated = 0;
    for (const [type, list] of Object.entries(byType)) {
      list.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
      const ladder = MALL_COSMETIC_PRICES[type];
      if (!ladder?.length) continue;
      for (let i = 0; i < list.length; i++) {
        const c = list[i];
        if (this.isAristocracyFree(c)) {
          if (Number(c.coinPrice) !== 0) {
            c.coinPrice = 0;
            await this.cosmeticsRepo.save(c);
            updated += 1;
          }
          continue;
        }
        // Keep explicit catalog prices for VIP beast / S2 frames.
        if (c.meta && (c.meta as any).pricedMall) {
          continue;
        }
        const tier = Math.min(ladder.length - 1, i % ladder.length);
        // Spread longer catalogs across the ladder by index bucket
        const bucket = list.length <= ladder.length
          ? i
          : Math.min(ladder.length - 1, Math.floor((i / list.length) * ladder.length));
        let price = ladder[bucket] ?? ladder[tier] ?? 999;
        // Never leave mall wearables at 0 after this sync.
        if (price <= 0) price = ladder.find((p) => p > 0) ?? 299;
        if (Number(c.coinPrice) !== price) {
          c.coinPrice = price;
          await this.cosmeticsRepo.save(c);
          updated += 1;
        }
      }
    }

    if (!row) {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: verKey,
          value: PRICING_VERSION,
          description: 'Mall cosmetics pricing version',
        }),
      );
    } else {
      row.value = PRICING_VERSION;
      await this.settingsRepo.save(row);
    }
    this.mallPricingEnsured = true;
    this.logger.log(`Synced mall cosmetic prices (${updated} rows) → ${PRICING_VERSION}`);
  }

  /** VIP plan unlocks only — not mall SKUs that happen to contain "_vip_". */
  private isAristocracyFree(c: Cosmetic): boolean {
    // Explicit mall-priced VIP/S2 frames stay paid even if also VIP-grantable.
    if (c.meta && (c.meta as any).pricedMall) return false;
    if (c.meta && (c.meta as any).aristocracy) return true;
    const code = String(c.code || '');
    return /^(vip\d+|level_vip_\d+|host_vip_\d+|frame_vip_\d+|toast_vip_\d+|entry_vip_\d+|card_vip_\d+)$/i.test(
      code,
    );
  }

  /** Strip competitor brand from user-visible cosmetic copy. */
  private stripCompetitorBrand(text: string | null | undefined): string {
    return String(text || '')
      .replace(/(ال)?(ميكو|ميجو)/g, 'JEHO')
      .replace(/mikoo/gi, 'JEHO')
      .replace(/mego/gi, 'JEHO')
      .replace(/\s{2,}/g, ' ')
      .replace(/\s*·\s*·\s*/g, ' · ')
      .replace(/^\s*·\s*|\s*·\s*$/g, '')
      .trim();
  }

  /** One-shot rename of any leftover brand text in the live catalog. */
  async ensurePublicBranding() {
    const verKey = 'cosmetics.public_branding_version';
    // Bump when rewrite rules change so production re-applies.
    const ver = '20260806-jeho-v2';
    const row = await this.settingsRepo.findOne({ where: { key: verKey } });
    if (row?.value === ver) return;

    const all = await this.cosmeticsRepo.find();
    let updated = 0;
    for (const c of all) {
      const nextName = this.stripCompetitorBrand(c.name);
      const nextDesc = this.stripCompetitorBrand(c.description);
      let dirty = false;
      if (nextName && nextName !== c.name) {
        c.name = nextName;
        dirty = true;
      }
      if ((c.description || '') !== nextDesc) {
        c.description = nextDesc || null;
        dirty = true;
      }
      if (dirty) {
        await this.cosmeticsRepo.save(c);
        updated += 1;
      }
    }

    if (!row) {
      await this.settingsRepo.save(
        this.settingsRepo.create({
          key: verKey,
          value: ver,
          description: 'Public cosmetic display names branding version',
        }),
      );
    } else {
      row.value = ver;
      await this.settingsRepo.save(row);
    }
    if (updated > 0) this.logger.log(`Stripped competitor brand from ${updated} cosmetics`);
  }

  /** 16 professional room wallpapers — all paid in coins (mall ladder). */
  async ensureRoomBackgroundCatalog() {
    if (this.roomBgEnsured) return;
    const v = '20260801';
    const rows: Array<{
      code: string;
      name: string;
      file: string;
      coinPrice: number;
      sortOrder: number;
    }> = [
      { code: 'bg_voice_default', name: 'خلفية صوتية', file: 'voice-room-bg.png', coinPrice: 99, sortOrder: 10 },
      { code: 'bg_aurora_night', name: 'أورورا ليلية', file: 'bg_aurora_night.png', coinPrice: 149, sortOrder: 20 },
      { code: 'bg_ocean_deep', name: 'أعماق المحيط', file: 'bg_ocean_deep.png', coinPrice: 199, sortOrder: 30 },
      { code: 'bg_mint_dream', name: 'حلم النعناع', file: 'bg_mint_dream.png', coinPrice: 249, sortOrder: 40 },
      { code: 'bg_sunset_lounge', name: 'غروب الصالة', file: 'bg_sunset_lounge.png', coinPrice: 299, sortOrder: 50 },
      { code: 'bg_violet_stage', name: 'منصة بنفسجية', file: 'bg_violet_stage.png', coinPrice: 399, sortOrder: 60 },
      { code: 'bg_golden_party', name: 'حفلة ذهبية', file: 'bg_golden_party.png', coinPrice: 499, sortOrder: 70 },
      { code: 'bg_neon_city', name: 'مدينة نيون', file: 'bg_neon_city.png', coinPrice: 599, sortOrder: 80 },
      { code: 'bg_rose_velvet', name: 'مخمل وردي', file: 'bg_rose_velvet.png', coinPrice: 799, sortOrder: 90 },
      { code: 'bg_ice_crystal', name: 'بلورة ثلج', file: 'bg_ice_crystal.png', coinPrice: 999, sortOrder: 100 },
      { code: 'bg_emerald_club', name: 'نادي الزمرد', file: 'bg_emerald_club.png', coinPrice: 1299, sortOrder: 110 },
      { code: 'bg_cosmic_dust', name: 'غبار كوني', file: 'bg_cosmic_dust.png', coinPrice: 1499, sortOrder: 120 },
      { code: 'bg_amber_glow', name: 'توهج كهرماني', file: 'bg_amber_glow.png', coinPrice: 1799, sortOrder: 130 },
      { code: 'bg_sapphire_hall', name: 'قاعة ياقوت', file: 'bg_sapphire_hall.png', coinPrice: 1999, sortOrder: 140 },
      { code: 'bg_cherry_night', name: 'ليلة الكرز', file: 'bg_cherry_night.png', coinPrice: 1799, sortOrder: 150 },
      { code: 'bg_royal_indigo', name: 'نيلي ملكي', file: 'bg_royal_indigo.png', coinPrice: 1999, sortOrder: 160 },
      { code: 'bg_teal_lounge', name: 'صالة فيروزية', file: 'bg_teal_lounge.png', coinPrice: 1299, sortOrder: 170 },
      { code: 'bg_amber_lounge', name: 'صالة كهرمانية', file: 'bg_amber_lounge.png', coinPrice: 1399, sortOrder: 180 },
      { code: 'bg_indigo_stage', name: 'منصة نيلي كوني', file: 'bg_indigo_stage.png', coinPrice: 1599, sortOrder: 190 },
      { code: 'bg_emerald_hall', name: 'قاعة زمرد', file: 'bg_emerald_hall.png', coinPrice: 1699, sortOrder: 200 },
    ];

    let created = 0;
    for (const row of rows) {
      const previewUrl = `/assets/backgrounds/${row.file}?v=${v}`;
      let existing = await this.cosmeticsRepo.findOne({ where: { code: row.code } });
      if (!existing) {
        await this.cosmeticsRepo.save(
          this.cosmeticsRepo.create({
            type: CosmeticType.ROOM_BACKGROUND,
            code: row.code,
            name: row.name,
            description: 'خلفية غرفة صوت تظهر للجميع داخل الروم',
            previewUrl,
            animationUrl: null,
            coinPrice: row.coinPrice,
            minVipLevel: 0,
            minUserLevel: 0,
            isActive: true,
            sortOrder: row.sortOrder,
            meta: { roomWallpaper: true },
          } as any),
        );
        created += 1;
        continue;
      }
      let dirty = false;
      if (existing.type !== CosmeticType.ROOM_BACKGROUND) {
        existing.type = CosmeticType.ROOM_BACKGROUND;
        dirty = true;
      }
      if (existing.previewUrl !== previewUrl) {
        existing.previewUrl = previewUrl;
        dirty = true;
      }
      if (existing.name !== row.name) {
        existing.name = row.name;
        dirty = true;
      }
      // coinPrice stays from DB (dashboard). Seeds only create missing rows.
      if (existing.sortOrder == null) {
        existing.sortOrder = row.sortOrder;
        dirty = true;
      }
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      if (dirty) await this.cosmeticsRepo.save(existing);
    }

    // Deactivate legacy placeholders that are not in the new pack.
    for (const legacy of ['bg_night', 'bg_party']) {
      const row = await this.cosmeticsRepo.findOne({ where: { code: legacy } });
      if (row && row.isActive) {
        row.isActive = false;
        await this.cosmeticsRepo.save(row);
      }
    }

    this.roomBgEnsured = true;
    if (created > 0) this.logger.log(`Seeded ${created} room backgrounds`);
  }

  /**
   * Undo accidental auto-wear of static VIP frames (ud_vip_tou) over mall/SVGA frames.
   * Safe to run every boot.
   */
  private async healStaticVipTouWear() {
    // 1) Clear profiles that point wear at static VIP art.
    const cleared = await this.dataSource.query(
      `UPDATE user_profiles
          SET "vipBadgeUrl" = NULL
        WHERE "vipBadgeUrl" LIKE '%ud_vip_tou_%'
           OR "vipBadgeUrl" LIKE '%vip_tou_fixed_%'`,
    );
    // 2) Unequip fixed-VIP inventory rows so they stop refreshing wear URLs.
    await this.dataSource.query(
      `UPDATE user_cosmetics uc
          SET equipped = false
         FROM cosmetics c
        WHERE c.id = uc."cosmeticId"
          AND uc.equipped = true
          AND (
            c.code LIKE 'vip_tou_fixed_%'
            OR COALESCE(c."previewUrl", '') LIKE '%ud_vip_tou_%'
            OR COALESCE(c.meta->>'fixedVipFrame', '') = 'true'
          )`,
    );
    // 3) Re-attach wear from any other equipped vip_badge (mall/SVGA).
    await this.dataSource.query(
      `UPDATE user_profiles p
          SET "vipBadgeUrl" = sub.wear
         FROM (
           SELECT DISTINCT ON (uc."userId")
                  uc."userId" AS uid,
                  COALESCE(NULLIF(c."animationUrl", ''), c."previewUrl") AS wear
             FROM user_cosmetics uc
             JOIN cosmetics c ON c.id = uc."cosmeticId"
            WHERE uc.equipped = true
              AND c.type = 'vip_badge'
              AND c."isActive" = true
              AND (uc."expiresAt" IS NULL OR uc."expiresAt" > NOW())
              AND COALESCE(c."previewUrl", '') NOT LIKE '%ud_vip_tou_%'
              AND COALESCE(c.meta->>'fixedVipFrame', '') <> 'true'
              AND c.code NOT LIKE 'vip_tou_fixed_%'
            ORDER BY uc."userId", uc."updatedAt" DESC NULLS LAST
         ) sub
        WHERE p."userId" = sub.uid
          AND (p."vipBadgeUrl" IS NULL OR p."vipBadgeUrl" = '')`,
    );
    const n = Array.isArray(cleared) ? cleared.length : (cleared as any)?.rowCount;
    if (n) this.logger.log(`Healed static VIP wear on profiles (cleared ~${n})`);
  }

  /**
   * Fixed Mikoo VIP headframes (ud_vip_tou_1..7) + head plaques.
   * Always safe on production — unique codes, coinPrice=0, not mall mix.
   */
  async ensureFixedVipTouFrames() {
    for (let lvl = 1; lvl <= 7; lvl++) {
      const framePath = `/assets/cosmetics/vip/ud_vip_tou_${lvl}.webp?v=20260806vipfix1`;
      const headPath = `/assets/cosmetics/vip/ic_head_vip_${lvl}.webp?v=20260806vipfix1`;
      await this.upsertAristocracyRow({
        type: CosmeticType.VIP_BADGE,
        code: `vip_tou_fixed_${lvl}`,
        name: `إطار VIP${lvl}`,
        previewUrl: framePath,
        animationUrl: null,
        minVipLevel: lvl,
        minUserLevel: 0,
        sortOrder: 200 + lvl,
        meta: {
          aristocracy: true,
          vipLevel: lvl,
          fixedVipFrame: true,
          source: 'ud_vip_tou',
          headUrl: headPath,
        },
      });
    }
  }

  async ensureAristocracyCatalog() {
    if (this.aristocracyEnsured) return;

    // Legacy mall rows vip1..vip10 cluttered the Frames tab and were not Mikoo
    // headwear — deactivate them. VIP medals stay available as level badges.
    for (let lvl = 1; lvl <= 10; lvl++) {
      const legacy = await this.cosmeticsRepo.findOne({ where: { code: `vip${lvl}` } });
      if (legacy && legacy.isActive) {
        legacy.isActive = false;
        await this.cosmeticsRepo.save(legacy);
      }
    }

    // Mikoo only ships VIP1–7 medals — keep badges sequential and force URLs.
    for (let lvl = 1; lvl <= 7; lvl++) {
      const medalPath = `/assets/cosmetics/vip/vip_medal_mikoo_${lvl}.png?v=20260806vipfix1`;
      await this.upsertAristocracyRow({
        type: CosmeticType.LEVEL_BADGE,
        code: `level_vip_${lvl}`,
        name: `شارة VIP${lvl}`,
        previewUrl: medalPath,
        animationUrl: null,
        minVipLevel: lvl,
        minUserLevel: 0,
        sortOrder: 100 + lvl,
        meta: { aristocracy: true, vipLevel: lvl, source: 'vip_level_badge' },
      });
      // Also keep catalog code used by Mikoo import in sync.
      await this.upsertAristocracyRow({
        type: CosmeticType.LEVEL_BADGE,
        code: `vip_medal_mikoo_${lvl}`,
        name: `وسام VIP${lvl}`,
        previewUrl: medalPath,
        animationUrl: null,
        minVipLevel: lvl,
        minUserLevel: 0,
        sortOrder: 110 + lvl,
        meta: { aristocracy: true, vipLevel: lvl, source: 'mikoo_xunzhang' },
      });
    }

    await this.ensureFixedVipTouFrames();

    // Bind every active head frame (vip_badge) to VIP only — never account level.
    await this.rebindVipFrameGates();

    // Deactivate bogus level_vip_8..10 that pointed at missing/non-Mikoo art.
    for (let lvl = 8; lvl <= 10; lvl++) {
      const row = await this.cosmeticsRepo.findOne({ where: { code: `level_vip_${lvl}` } });
      if (row?.isActive) {
        row.isActive = false;
        await this.cosmeticsRepo.save(row);
      }
    }

    this.aristocracyEnsured = true;
  }

  /**
   * Frames (vip_badge): always VIP-gated, never account-level-gated.
   * Safe on production (BOOT_SEED off) — only updates gate columns + meta.vipLevel.
   */
  private async rebindVipFrameGates() {
    const frames = await this.cosmeticsRepo.find({
      where: { type: CosmeticType.VIP_BADGE, isActive: true },
    });
    let updated = 0;
    for (const frame of frames) {
      const code = String(frame.code || '');
      const name = String(frame.name || '');
      const metaVip = Number((frame.meta as any)?.vipLevel || 0);
      const m =
        code.match(/(?:^|_)vip(\d+)$/i) ||
        code.match(/vip[_-]?(\d+)/i) ||
        name.match(/VIP\s*(\d+)/i) ||
        name.match(/في\s*اي\s*بي\s*(\d+)/i);
      let vip = 0;
      if (m) vip = Math.max(0, Number(m[1]) || 0);
      else if (metaVip > 0) vip = metaVip;
      else if (Number(frame.minVipLevel || 0) > 0) vip = Number(frame.minVipLevel);
      // Wear art is VIP1–7; plans 8–100 still wear tier 7.
      if (vip > 0) vip = Math.min(7, Math.max(1, vip));

      let dirty = false;
      if (Number(frame.minUserLevel || 0) !== 0) {
        frame.minUserLevel = 0;
        dirty = true;
      }
      if (vip > 0) {
        const nextSort = Math.max(Number(frame.sortOrder) || 0, 200 + vip);
        if (Number(frame.minVipLevel || 0) !== vip) {
          frame.minVipLevel = vip;
          dirty = true;
        }
        if ((frame.meta as any)?.vipLevel !== vip || !(frame.meta as any)?.aristocracy) {
          frame.meta = {
            ...(frame.meta || {}),
            vipLevel: vip,
            aristocracy: true,
          };
          dirty = true;
        }
        // Keep VIP frames ordered but avoid thrashing custom sortOrder unless zeroish.
        if ((Number(frame.sortOrder) || 0) < 200 && frame.sortOrder !== nextSort) {
          frame.sortOrder = nextSort;
          dirty = true;
        }
      }
      if (dirty) {
        await this.cosmeticsRepo.save(frame);
        updated += 1;
      }
    }
    if (updated > 0) {
      this.logger.log(`Re-bound ${updated} VIP frames (minVipLevel, cleared minUserLevel)`);
    }
  }

  /**
   * Room list cards: Mikoo rank borders top1–top7.
   */
  async ensureRoomCardCatalog() {
    const v = '20260806r7';
    const mikooBorders = [
      { code: 'room_mikoo_border_top1', name: 'إطار الروم · المركز 1', file: 'bg_room_border_top1.webp', price: 299, sortOrder: 50 },
      { code: 'room_mikoo_border_top2', name: 'إطار الروم · المركز 2', file: 'bg_room_border_top2.webp', price: 249, sortOrder: 51 },
      { code: 'room_mikoo_border_top3', name: 'إطار الروم · المركز 3', file: 'bg_room_border_top3.webp', price: 199, sortOrder: 52 },
      { code: 'room_mikoo_border_top4', name: 'إطار الروم · المركز 4', file: 'bg_room_border_top4.webp', price: 179, sortOrder: 53 },
      { code: 'room_mikoo_border_top5', name: 'إطار الروم · المركز 5', file: 'bg_room_border_top5.webp', price: 159, sortOrder: 54 },
      { code: 'room_mikoo_border_top6', name: 'إطار الروم · المركز 6', file: 'bg_room_border_top6.webp', price: 139, sortOrder: 55 },
      { code: 'room_mikoo_border_top7', name: 'إطار الروم · المركز 7', file: 'bg_room_border_top7.webp', price: 119, sortOrder: 56 },
    ];
    const keepCodes = new Set(mikooBorders.map((b) => b.code));

    let created = 0;
    for (const b of mikooBorders) {
      const previewUrl = `/assets/rooms/mikoo/${b.file}?v=${v}`;
      const diskRel = previewUrl.split('?')[0].replace(/^\//, '');
      if (!existsSync(join(resolvePublicDir(), diskRel))) continue;

      let existing = await this.cosmeticsRepo.findOne({ where: { code: b.code } });
      if (!existing) {
        await this.cosmeticsRepo.save(
          this.cosmeticsRepo.create({
            type: CosmeticType.ROOM_CARD,
            code: b.code,
            name: b.name,
            description: 'إطار يظهر حول بطاقة الغرفة في القائمة وداخل الروم',
            previewUrl,
            animationUrl: null,
            coinPrice: b.price,
            minVipLevel: 0,
            minUserLevel: 0,
            isActive: true,
            sortOrder: b.sortOrder,
            meta: { source: 'mikoo_border', kind: 'room_card', rank: Number(b.code.slice(-1)) },
          } as any),
        );
        created += 1;
        continue;
      }
      let dirty = false;
      if (existing.type !== CosmeticType.ROOM_CARD) {
        existing.type = CosmeticType.ROOM_CARD;
        dirty = true;
      }
      if (existing.previewUrl !== previewUrl) {
        existing.previewUrl = previewUrl;
        dirty = true;
      }
      if (existing.name !== b.name) {
        existing.name = b.name;
        dirty = true;
      }
      const nextDesc = 'إطار يظهر حول بطاقة الغرفة في القائمة وداخل الروم';
      if (existing.description !== nextDesc) {
        existing.description = nextDesc;
        dirty = true;
      }
      // coinPrice stays from DB (dashboard) unless zero/null new row.
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      if ((existing as any).sortOrder !== b.sortOrder) {
        (existing as any).sortOrder = b.sortOrder;
        dirty = true;
      }
      if (dirty) await this.cosmeticsRepo.save(existing);
    }

    // Deactivate every other room_card (kenar / legacy clones).
    const allCards = await this.cosmeticsRepo.find({ where: { type: CosmeticType.ROOM_CARD } });
    let retired = 0;
    for (const row of allCards) {
      if (keepCodes.has(String(row.code || ''))) continue;
      if (!row.isActive) continue;
      row.isActive = false;
      await this.cosmeticsRepo.save(row);
      retired += 1;
    }

    if (created > 0 || retired > 0) {
      this.logger.log(`Room cards: seeded=${created}, retired=${retired} (kept Mikoo borders top1–7)`);
    }
  }

  /**
   * Hard-remove Host-signals entry_signal_* (الأسد الملكي → أسطوري) and any
   * non-Mikoo entry art whose files were never shipped / are missing.
   */
  async purgeBrokenEntryEffects() {
    const pub = resolvePublicDir();
    const rows = await this.cosmeticsRepo.find({
      where: { type: CosmeticType.ENTRY_EFFECT },
    });
    let killed = 0;
    for (const row of rows) {
      const code = String(row.code || '');
      const preview = String(row.previewUrl || '').split('?')[0];
      // Only remove known-broken legacy host-signal entries — never wipe Mikoo CDN rows
      // just because a local file check failed (cwd / deploy lag).
      const isLegacyHostSignals =
        code.startsWith('entry_signal_') ||
        code.startsWith('entry_vip_') ||
        (preview.includes('/visual-system/entry-effects/assets/entry-') &&
          !preview.includes('entry-mikoo-'));
      if (isLegacyHostSignals) {
        await this.cosmeticsRepo.remove(row);
        killed += 1;
      }
    }
    // Clear equipped profile URLs that still point at missing legacy entry art.
    await this.dataSource.query(
      `UPDATE user_profiles
          SET "entryEffectUrl" = NULL,
              "entryAnimationUrl" = NULL
        WHERE ("entryEffectUrl" LIKE '/visual-system/entry-effects/assets/entry-%'
          AND "entryEffectUrl" NOT LIKE '%entry-mikoo-%')
           OR "entryEffectUrl" LIKE '%entry-lion%'
           OR "entryEffectUrl" LIKE '%entry-legend%'
           OR "entryEffectUrl" LIKE '%entry_signal_%'`,
    ).catch(() => undefined);
    if (killed > 0) this.logger.log(`Purged ${killed} broken/legacy entry effects`);
  }

  /**
   * Seed host signals (إشارات المضيف) from visual-system PNG frames.
   */
  async ensureHostBadgeCatalog() {
    // Host badges removed — قطاع الراس (vip_badge) is the only headwear channel.
    try {
      await this.cosmeticsRepo
        .createQueryBuilder()
        .update()
        .set({ isActive: false })
        .where('type = :t', { t: CosmeticType.HOST_BADGE })
        .execute();
    } catch (e) {
      this.logger.warn(`host_badge deactivate: ${(e as Error).message}`);
    }
    this.hostBadgeEnsured = true;
  }

  /**
   * Seed / refresh cosmetics imported from Mikoo CDN
   * (`public/assets/cosmetics/catalog.json` produced by scripts/import_mikoo_cosmetics.py).
   */
  async ensureMikooCosmeticsCatalog() {
    if (this.mikooCatalogEnsured) return;
    const catalogPath = join(resolvePublicDir(), 'assets', 'cosmetics', 'catalog.json');
    if (!existsSync(catalogPath)) {
      this.logger.warn(`Mikoo cosmetics catalog missing: ${catalogPath}`);
      return;
    }

    let items: MikooCatalogItem[] = [];
    try {
      const raw = JSON.parse(readFileSync(catalogPath, 'utf8'));
      items = Array.isArray(raw?.items) ? raw.items : [];
    } catch (err) {
      this.logger.warn(`Mikoo cosmetics catalog unreadable: ${(err as Error).message}`);
      this.mikooCatalogEnsured = true;
      return;
    }

    const allowed = new Set(Object.values(CosmeticType));
    let upserted = 0;
    for (const item of items) {
      const type = String(item.type || '') as CosmeticType;
      const code = String(item.code || '').trim();
      const name = this.stripCompetitorBrand(String(item.name || '').trim());
      const previewUrl = String(item.previewUrl || '').trim();
      if (!code || !name || !previewUrl || !allowed.has(type)) continue;

      const existing = await this.cosmeticsRepo.findOne({ where: { code } });
      const animationUrl =
        item.animationUrl === undefined || item.animationUrl === null
          ? null
          : String(item.animationUrl);
      const coinPrice = Math.max(0, Number(item.coinPrice) || 0);
      const sortOrder = Number(item.sortOrder) || 0;
      const minVipLevel = Math.max(0, Number(item.minVipLevel) || 0);
      const minUserLevel = Math.max(0, Number(item.minUserLevel) || 0);
      const meta = {
        ...(existing?.meta || {}),
        ...(item.meta || {}),
        mikooImport: true,
      };
      const publicDesc = 'من متجر المظهر';

      if (!existing) {
        await this.cosmeticsRepo.save(
          this.cosmeticsRepo.create({
            type,
            code,
            name,
            description: publicDesc,
            previewUrl,
            animationUrl,
            coinPrice,
            minVipLevel,
            minUserLevel,
            isActive: true,
            sortOrder,
            meta,
          }),
        );
        upserted += 1;
        continue;
      }

      let dirty = false;
      if (existing.type !== type) {
        existing.type = type;
        dirty = true;
      }
      if (existing.name !== name) {
        existing.name = name;
        dirty = true;
      }
      if (
        existing.description !== publicDesc ||
        /ميكو|mikoo/i.test(String(existing.description || ''))
      ) {
        existing.description = publicDesc;
        dirty = true;
      }
      if (existing.previewUrl !== previewUrl) {
        existing.previewUrl = previewUrl;
        dirty = true;
      }
      if ((existing.animationUrl || null) !== animationUrl) {
        existing.animationUrl = animationUrl;
        dirty = true;
      }
      // coinPrice stays from DB — catalog never overwrites dashboard prices.
      // Only fill price when the row is still free/zero and catalog has a paid price.
      if (Number(existing.coinPrice) <= 0 && coinPrice > 0) {
        existing.coinPrice = coinPrice;
        dirty = true;
      }
      if (existing.sortOrder !== sortOrder) {
        existing.sortOrder = sortOrder;
        dirty = true;
      }
      if (Number(existing.minVipLevel || 0) !== minVipLevel) {
        existing.minVipLevel = minVipLevel;
        dirty = true;
      }
      if (Number(existing.minUserLevel || 0) !== minUserLevel) {
        existing.minUserLevel = minUserLevel;
        dirty = true;
      }
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      existing.meta = meta;
      dirty = true;
      if (dirty) {
        await this.cosmeticsRepo.save(existing);
        upserted += 1;
      }
    }

    // Only deactivate legacy /visual-system/ rows with missing files — keep Mikoo assets active.
    const pub = resolvePublicDir();
    const legacy = await this.cosmeticsRepo.find({
      where: { isActive: true },
    });
    for (const row of legacy) {
      const preview = String(row.previewUrl || '').split('?')[0];
      if (!preview.startsWith('/visual-system/')) continue;
      if (preview.includes('host-frames')) continue; // host signals seeded separately
      const rel = preview.replace(/^\//, '');
      const disk = join(pub, rel);
      if (!existsSync(disk)) {
        row.isActive = false;
        await this.cosmeticsRepo.save(row);
      }
    }

    // Join toasts removed — duplicated الدخولية (entry_effect). Deactivate leftovers.
    try {
      await this.cosmeticsRepo
        .createQueryBuilder()
        .update()
        .set({ isActive: false })
        .where('type = :t', { t: CosmeticType.JOIN_TOAST })
        .execute();
    } catch (e) {
      this.logger.warn(`join_toast deactivate: ${(e as Error).message}`);
    }

    this.mikooCatalogEnsured = true;
    if (upserted > 0) {
      this.logger.log(`Mikoo cosmetics catalog synced (${upserted} rows)`);
      await this.refreshEquippedMikooWearUrls().catch((err) =>
        this.logger.warn(`Equipped wear URL refresh failed: ${(err as Error).message}`),
      );
    }
  }

  /** After catalog SVGA/MP4 URLs change, push new animationUrl onto equipped profiles. */
  private async refreshEquippedMikooWearUrls() {
    const equipped = await this.userCosmeticsRepo.find({
      where: { equipped: true },
      relations: ['cosmetic'],
    });
    let updated = 0;
    for (const row of equipped) {
      const c = row.cosmetic;
      if (!c || !row.userId) continue;
      if (c.type !== CosmeticType.VIP_BADGE && c.type !== CosmeticType.HOST_BADGE) continue;
      const anim = c.animationUrl || '';
      const lower = String(anim).toLowerCase();
      const playable =
        !!anim &&
        !lower.includes('runtime.html') &&
        !lower.endsWith('.html') &&
        !lower.endsWith('.json');
      const next = playable ? anim : c.previewUrl || null;
      if (!next) continue;
      const profile = await this.profileRepo.findOne({ where: { userId: row.userId } });
      if (!profile) continue;
      if (c.type === CosmeticType.VIP_BADGE && profile.vipBadgeUrl !== next) {
        profile.vipBadgeUrl = next;
        await this.profileRepo.save(profile);
        updated += 1;
      } else if (c.type === CosmeticType.HOST_BADGE && profile.hostBadgeUrl !== next) {
        profile.hostBadgeUrl = next;
        await this.profileRepo.save(profile);
        updated += 1;
      }
    }
    if (updated > 0) {
      this.logger.log(`Refreshed equipped wear URLs (${updated} profiles)`);
    }
  }

  private async upsertAristocracyRow(payload: {
    type: CosmeticType;
    code: string;
    name: string;
    previewUrl: string;
    animationUrl: string | null;
    minVipLevel: number;
    minUserLevel?: number;
    sortOrder: number;
    meta?: Record<string, unknown>;
  }) {
    const existing = await this.cosmeticsRepo.findOne({ where: { code: payload.code } });
    if (!existing) {
      await this.cosmeticsRepo.save(
        this.cosmeticsRepo.create({
          type: payload.type,
          code: payload.code,
          name: payload.name,
          previewUrl: payload.previewUrl,
          animationUrl: payload.animationUrl,
          coinPrice: 0,
          minVipLevel: payload.minVipLevel,
          minUserLevel: payload.minUserLevel || 0,
          isActive: true,
          sortOrder: payload.sortOrder,
          meta: payload.meta || null,
        }),
      );
      return;
    }
    existing.type = payload.type;
    existing.name = payload.name;
    // Force medal/frame URLs so VIP1–7 stay sequential across redeploys.
    existing.previewUrl = payload.previewUrl;
    if (payload.animationUrl) existing.animationUrl = payload.animationUrl;
    existing.minVipLevel = payload.minVipLevel;
    if (payload.minUserLevel != null) existing.minUserLevel = payload.minUserLevel;
    existing.isActive = true;
    existing.coinPrice = 0;
    existing.sortOrder = payload.sortOrder;
    existing.meta = { ...(existing.meta || {}), ...(payload.meta || {}) };
    await this.cosmeticsRepo.save(existing);
  }

  entryPayload(
    displayName: string,
    avatarUrl: string | null,
    toastUrl: string | null,
    toastAnimationUrl: string | null,
    entryEffectUrl: string | null,
    entryAnimationUrl: string | null,
    vipLevel = 0,
  ) {
    return {
      type: 'room_join',
      displayName,
      avatarUrl,
      toastUrl: toastUrl ?? null,
      toastAnimationUrl: toastAnimationUrl ?? null,
      entryEffectUrl: entryEffectUrl ?? null,
      entryAnimationUrl: entryAnimationUrl ?? null,
      animationUrl: entryAnimationUrl ?? null,
      vipLevel,
    };
  }
}
