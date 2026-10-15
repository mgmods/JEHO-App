import { BadRequestException, Injectable, Logger, NotFoundException, OnModuleInit } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, IsNull, MoreThan, Repository } from 'typeorm';
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
    // The database remains authoritative: dashboard changes appear without reseeding.
    // The app's "frames" tab represents both VIP_BADGE and HOST_BADGE records.
    const where: any = { isActive: true };
    if (type === CosmeticType.VIP_BADGE) {
      where.type = In([CosmeticType.VIP_BADGE, CosmeticType.HOST_BADGE]);
    } else if (type) {
      where.type = type;
    }
    const rows = await this.cosmeticsRepo.find({
      where,
      order: { sortOrder: 'ASC', coinPrice: 'ASC' },
    });
    // Some backend-only rewards are deliberately marked hidden from the mall.
    return rows.filter((row) => (row.meta as any)?.mallHidden !== true);
  }

  async adminList(type?: CosmeticType) {
    this.assertSupportedType(type);
    const where: any = {};
    if (type) where.type = type;
    return this.cosmeticsRepo.find({ where, order: { sortOrder: 'ASC', createdAt: 'DESC' } });
  }

  /** Resolve mall/catalog preview URL from cosmetic code (agency exclusives, admin). */
  async resolvePreviewUrl(code: string | null | undefined): Promise<string | null> {