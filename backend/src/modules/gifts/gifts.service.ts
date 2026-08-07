import {
  Injectable,
  NotFoundException,
  BadRequestException,
  Optional,
  Inject,
  forwardRef,
  OnModuleInit,
  Logger,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { DataSource, MoreThan, Repository } from 'typeorm';
import { existsSync, readFileSync } from 'fs';
import { join } from 'path';
import { randomInt } from 'crypto';
import { Gift, GiftType } from '../../database/entities/gift.entity';
import { GiftCategory } from '../../database/entities/gift-category.entity';
import { GiftSend } from '../../database/entities/gift-send.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  WalletTransaction,
  TransactionType,
  CurrencyType,
} from '../../database/entities/wallet-transaction.entity';
import { UserProfile } from '../../database/entities/user-profile.entity';
import { Agency, AgencyStatus } from '../../database/entities/agency.entity';
import {
  AgencyMember,
  AgencyMemberStatus,
} from '../../database/entities/agency-member.entity';
import { independentAgencyGiftSplit } from '../agencies/agency-gift-split';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { paginate, PaginationDto } from '../../common/dto/pagination.dto';
import { SendGiftDto, SendAllMicGiftDto } from './dto/gifts.dto';
import { TasksService } from '../tasks/tasks.service';
import { ContestsService } from '../contests/contests.service';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { HostTargetService } from '../host-target/host-target.service';
import { User } from '../../database/entities/user.entity';
import { UserVip } from '../../database/entities/user-vip.entity';
import { Room, RoomStatus, RoomKind } from '../../database/entities/room.entity';
import { RoomSeat } from '../../database/entities/room-seat.entity';
import { bootCatalogSeedEnabled } from '../../common/db-authoritative';
import { resolvePlayableGiftAnimation } from './gift-media.resolve';
import { effectiveVipLevel } from '../../common/vip-progress';
import {
  GIFT_DIAMOND_RATIO,
  LUCKY_GIFT_DIAMOND_RATIO,
  LUCKY_GIFT_MAX_MULTIPLIER,
  mintDiamondsPerUnit,
} from '../../common/pricing-catalog';

type LuckyRoll = {
  luckyMultiplier: number | null;
  luckyCoinsWon: number;
};

@Injectable()
export class GiftsService implements OnModuleInit {
  private readonly log = new Logger(GiftsService.name);

  constructor(
    @InjectRepository(Gift) private readonly giftsRepo: Repository<Gift>,
    @InjectRepository(GiftCategory)
    private readonly categoriesRepo: Repository<GiftCategory>,
    @InjectRepository(GiftSend) private readonly sendsRepo: Repository<GiftSend>,
    @InjectRepository(Wallet) private readonly walletsRepo: Repository<Wallet>,
    @InjectRepository(WalletTransaction)
    private readonly txRepo: Repository<WalletTransaction>,
    @InjectRepository(UserProfile) private readonly profilesRepo: Repository<UserProfile>,
    @InjectRepository(Agency) private readonly agenciesRepo: Repository<Agency>,
    @InjectRepository(AgencyMember) private readonly membersRepo: Repository<AgencyMember>,
    @InjectRepository(AppSetting) private readonly settingsRepo: Repository<AppSetting>,
    @InjectRepository(Room) private readonly roomsRepo: Repository<Room>,
    @InjectRepository(RoomSeat) private readonly seatsRepo: Repository<RoomSeat>,
    @InjectRepository(User) private readonly usersRepo: Repository<User>,
    @InjectRepository(UserVip) private readonly userVipRepo: Repository<UserVip>,
    private readonly dataSource: DataSource,
    private readonly tasksService: TasksService,
    @Optional() private readonly realtime?: RealtimeGateway,
    @Optional()
    @Inject(forwardRef(() => ContestsService))
    private readonly contestsService?: ContestsService,
    @Optional() private readonly hostTarget?: HostTargetService,
  ) {}

  async onModuleInit() {
    // Schema-only fix is always safe. Catalog seeds never run against live DB.
    await this.ensureGiftCategoryColumn();
    await this.ensureGiftCategoriesTable();
    await this.ensureDefaultGiftCategories();
    // Critical: zero diamondValue + Math.min() previously awarded 0 diamonds on paid gifts.
    await this.healZeroDiamondCatalog();
    // Always rewrite competitor brand text in gift titles/descriptions for app UI.
    await this.ensureJehoPublicBranding();
    if (!bootCatalogSeedEnabled()) {
      this.log.log('Gifts catalog: DB authoritative (no boot seed)');
      return;
    }
    await this.ensureDefaultLuckyGift();
    await this.ensureMikooGiftTabs();
  }

  /** Replace Mikoo/Mego branding in gift names (and descriptions) visible in the app. */
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

  private async ensureJehoPublicBranding() {
    const verKey = 'gifts.public_branding_version';
    // Bump when rewrite rules change so production re-applies.
    const ver = '20260806-jeho-v2';
    try {
      const row = await this.settingsRepo.findOne({ where: { key: verKey } });
      if (row?.value === ver) return;
      const all = await this.giftsRepo.find();
      let updated = 0;
      for (const g of all) {
        const nextName = this.stripCompetitorBrand(g.name);
        const nextDesc = this.stripCompetitorBrand(g.description);
        if (nextName !== (g.name || '') || nextDesc !== (g.description || '')) {
          g.name = nextName || g.name;
          g.description = nextDesc || g.description;
          await this.giftsRepo.save(g);
          updated += 1;
        }
      }
      if (row) {
        row.value = ver;
        await this.settingsRepo.save(row);
      } else {
        await this.settingsRepo.save(this.settingsRepo.create({ key: verKey, value: ver }));
      }
      if (updated > 0) this.log.log(`Gifts public branding rewritten (${updated} rows)`);
    } catch (err) {
      this.log.warn(
        `ensureJehoPublicBranding skipped: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  }

  /**
   * Repair paid gifts that have catalog diamondValue=0 (legacy default).
   * Does not change free bag gifts (coinPrice=0) or admin-set positive ceilings.
   */
  private async healZeroDiamondCatalog() {
    try {
      const rows: Array<{ id: string; coinPrice: number; type: string }> =
        await this.dataSource.query(
          `SELECT id, "coinPrice", type FROM gifts
           WHERE COALESCE("diamondValue", 0) = 0
             AND COALESCE("coinPrice", 0) > 0
             AND "isActive" = true`,
        );
      if (!rows?.length) return;
      let fixed = 0;
      for (const row of rows) {
        const isLucky = String(row.type || '') === GiftType.LUCKY;
        const next = mintDiamondsPerUnit(
          Number(row.coinPrice) || 0,
          0,
          isLucky ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO,
        );
        if (next <= 0) continue;
        await this.giftsRepo.update({ id: row.id }, { diamondValue: next });
        fixed += 1;
      }
      if (fixed > 0) {
        this.log.warn(
          `Healed ${fixed} gift(s) with diamondValue=0 → minted from coin × ratio`,
        );
      }
    } catch (err) {
      this.log.warn(
        `healZeroDiamondCatalog: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  }

  /** Ensure optional Mikoo-style category column exists (prod may have synchronize=false). */
  private async ensureGiftCategoryColumn() {
    try {
      await this.dataSource.query(
        `ALTER TABLE gifts ADD COLUMN IF NOT EXISTS category varchar(32) DEFAULT 'normal'`,
      );
    } catch (err) {
      this.log.warn(
        `ensureGiftCategoryColumn: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  }

  private async ensureGiftCategoriesTable() {
    try {
      await this.dataSource.query(`
        CREATE TABLE IF NOT EXISTS gift_categories (
          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
          key varchar(32) NOT NULL UNIQUE,
          "labelAr" varchar(64) NOT NULL,
          "labelEn" varchar(64) NULL,
          "sortOrder" int NOT NULL DEFAULT 0,
          "isActive" boolean NOT NULL DEFAULT true,
          "iconUrl" varchar(512) NULL,
          "createdAt" timestamptz NOT NULL DEFAULT now(),
          "updatedAt" timestamptz NOT NULL DEFAULT now()
        )
      `);
    } catch (err) {
      this.log.warn(
        `ensureGiftCategoriesTable: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  }

  /** Seed defaults only when the table is empty — never re-create admin deletions. */
  private async ensureDefaultGiftCategories() {
    try {
      const count = await this.categoriesRepo.count();
      if (count > 0) return;
    } catch (err) {
      this.log.warn(
        `ensureDefaultGiftCategories count: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
    const defaults: Array<{
      key: string;
      labelAr: string;
      labelEn: string;
      sortOrder: number;
    }> = [
      { key: 'normal', labelAr: 'عادي', labelEn: 'Normal', sortOrder: 0 },
      { key: 'lucky', labelAr: 'حظ', labelEn: 'Lucky', sortOrder: 1 },
      { key: 'combo', labelAr: 'كومبو', labelEn: 'Combo', sortOrder: 2 },
      { key: 'premium', labelAr: 'مميز', labelEn: 'Premium', sortOrder: 3 },
      { key: 'country', labelAr: 'دول', labelEn: 'Country', sortOrder: 4 },
    ];
    for (const d of defaults) {
      try {
        await this.categoriesRepo.save(
          this.categoriesRepo.create({
            key: d.key,
            labelAr: d.labelAr,
            labelEn: d.labelEn,
            sortOrder: d.sortOrder,
            isActive: true,
          }),
        );
      } catch (err) {
        this.log.warn(
          `ensureDefaultGiftCategories ${d.key}: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }
  }

  /** صندوق الحظ العائم في الغرفة — ليس هدية محظوظ. */
  private isLuckyBoxCatalogGift(g: { name?: string | null; iconUrl?: string | null }) {
    const icon = String(g.iconUrl || '').toLowerCase();
    const name = String(g.name || '').toLowerCase();
    return icon.includes('lucky-box') || name.includes('صندوق');
  }

  /** House-positive lucky defaults (soft + medium + rare big). */
  private async ensureDefaultLuckyGift() {
    try {
      const luckyConfig = {
        winChance: 0.405,
        minMultiplier: 0.3,
        maxMultiplier: 8,
      };

      const luckySeeds = [
        {
          name: 'حظ برونزي',
          description: 'هدية محظوظة — 100 عملة',
          iconUrl: '/assets/gifts/mikoo/bg_lucky_gift_low_100.webp',
          coinPrice: 100,
          sortOrder: 1,
          category: 'lucky',
        },
        {
          name: 'حظ فضي',
          description: 'هدية محظوظة — 500 عملة',
          iconUrl: '/assets/gifts/mikoo/bg_lucky_gift_low_500.webp',
          coinPrice: 500,
          sortOrder: 2,
          category: 'lucky',
        },
        {
          name: 'حظ ذهبي',
          description: 'هدية محظوظة — 1000 عملة',
          iconUrl: '/assets/gifts/mikoo/bg_lucky_gift_low_1000.webp',
          coinPrice: 1000,
          sortOrder: 3,
          category: 'lucky',
        },
      ];

      // Hide lucky-box from the gifts catalog (room float stays separate).
      const allActive = await this.giftsRepo.find({ where: { isActive: true } });
      for (const g of allActive) {
        if (!this.isLuckyBoxCatalogGift(g)) continue;
        g.isActive = false;
        await this.giftsRepo.save(g);
        this.log.log(`Deactivated lucky-box gift from catalog: ${g.name}`);
      }

      // Sync live lucky configs to house-safe table (soft returns allowed).
      const bareLucky = await this.giftsRepo.find({
        where: { type: GiftType.LUCKY, isActive: true },
      });
      for (const g of bareLucky) {
        const win = Number(g.luckyConfig?.winChance) || 0;
        const maxMul = Number(g.luckyConfig?.maxMultiplier) || 0;
        const minMul = Number(g.luckyConfig?.minMultiplier) || 0;
        const needsSync =
          !g.luckyConfig ||
          maxMul > LUCKY_GIFT_MAX_MULTIPLIER ||
          maxMul < 3 ||
          minMul > 1 ||
          win < 0.3 ||
          win > 0.5;
        if (needsSync) {
          g.luckyConfig = { ...luckyConfig };
          await this.giftsRepo.save(g);
        }
      }

      for (const seed of luckySeeds) {
        let existing = await this.giftsRepo.findOne({ where: { name: seed.name } });
        if (!existing) {
          await this.giftsRepo.save(
            this.giftsRepo.create({
              ...seed,
              animationUrl: null,
              diamondValue: Math.floor(seed.coinPrice * LUCKY_GIFT_DIAMOND_RATIO),
              type: GiftType.LUCKY,
              isActive: true,
              luckyConfig,
            }),
          );
          this.log.log(`Seeded lucky gift: ${seed.name}`);
          continue;
        }
        let dirty = false;
        if (existing.type !== GiftType.LUCKY) {
          existing.type = GiftType.LUCKY;
          dirty = true;
        }
        if (!existing.luckyConfig
            || Number(existing.luckyConfig.winChance) < 0.3
            || Number(existing.luckyConfig.maxMultiplier) < 3) {
          existing.luckyConfig = luckyConfig;
          dirty = true;
        }
        if (existing.iconUrl !== seed.iconUrl) {
          existing.iconUrl = seed.iconUrl;
          dirty = true;
        }
        if ((existing as any).category !== 'lucky') {
          (existing as any).category = 'lucky';
          dirty = true;
        }
        // No looping JSON pulse — lucky FX is native (center → scatter).
        if (existing.animationUrl) {
          existing.animationUrl = null as any;
          dirty = true;
        }
        // Price/sort stay from DB (dashboard edits). Seeds only create missing rows.
        if (existing.sortOrder == null) {
          existing.sortOrder = seed.sortOrder;
          dirty = true;
        }
        if (!existing.isActive) {
          existing.isActive = true;
          dirty = true;
        }
        if (dirty) await this.giftsRepo.save(existing);
      }

      // Keep only the 3 official lucky tiers — hide legacy duplicates (الإعجاب / هدية الحظ…).
      const keep = new Set(luckySeeds.map((s) => s.name));
      const extras = await this.giftsRepo.find({
        where: { type: GiftType.LUCKY, isActive: true },
      });
      for (const g of extras) {
        if (keep.has(g.name)) continue;
        g.isActive = false;
        await this.giftsRepo.save(g);
        this.log.log(`Deactivated legacy lucky gift: ${g.name}`);
      }
    } catch (err) {
      this.log.warn(
        `ensureDefaultLuckyGift skipped: ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }
  }

  /**
   * Mikoo gift panel tabs: bag / CP / friendships / country / debris / agency / celebrity.
   * Seeds chrome icons extracted from Mikoo APK until full listV3 dump is imported.
   */
  private async ensureMikooGiftTabs() {
    try {
      // Backfill category from type for existing rows.
      await this.dataSource.query(
        `UPDATE gifts SET category = 'lucky' WHERE type::text = 'lucky' AND (category IS NULL OR category = '' OR category = 'normal')`,
      );
      await this.dataSource.query(
        `UPDATE gifts SET category = 'premium' WHERE type::text = 'premium' AND (category IS NULL OR category = '' OR category = 'normal')`,
      );
      await this.dataSource.query(
        `UPDATE gifts SET category = COALESCE(NULLIF(category, ''), 'normal') WHERE category IS NULL OR category = ''`,
      );

      const tabSeeds: Array<{
        name: string;
        category: string;
        type: GiftType;
        iconUrl: string;
        coinPrice: number;
        sortOrder: number;
        description: string;
      }> = [
        {
          name: 'حقيبة ترحيب',
          category: 'bag',
          type: GiftType.NORMAL,
          iconUrl: '/assets/gifts/mikoo/gift_back_lv1.webp',
          coinPrice: 0,
          sortOrder: 10,
          description: 'هدية حقيبة — تظهر في تبويب الحقيبة',
        },
        {
          name: 'حقيبة فضية',
          category: 'bag',
          type: GiftType.NORMAL,
          iconUrl: '/assets/gifts/mikoo/gift_back_lv2.webp',
          coinPrice: 0,
          sortOrder: 11,
          description: 'هدية حقيبة',
        },
        {
          name: 'قلب CP',
          category: 'cp',
          type: GiftType.PREMIUM,
          iconUrl: '/assets/gifts/mikoo/bg_cp_card.webp',
          coinPrice: 299,
          sortOrder: 20,
          description: 'هدية علاقة CP',
        },
        {
          name: 'صداقة مقربة',
          category: 'friend',
          type: GiftType.PREMIUM,
          iconUrl: '/assets/gifts/mikoo/bg_friendship.webp',
          coinPrice: 199,
          sortOrder: 30,
          description: 'هدية صداقات / best friend',
        },
        {
          name: 'هدية دولة',
          category: 'country',
          type: GiftType.NORMAL,
          iconUrl: '/assets/gifts/mikoo/ic_weekly_celebrity_gift.webp',
          coinPrice: 99,
          sortOrder: 40,
          description: 'هدية تبويب الدولة',
        },
        {
          name: 'فتات VIP',
          category: 'debris',
          type: GiftType.NORMAL,
          iconUrl: '/assets/gifts/mikoo/egg_gift_d.webp',
          coinPrice: 50,
          sortOrder: 50,
          description: 'هدية فتات (debris) — عملة خاصة',
        },
        {
          name: 'هدية وكالة',
          category: 'agency',
          type: GiftType.PREMIUM,
          iconUrl: '/assets/gifts/mikoo/gift_back_lv3.webp',
          coinPrice: 499,
          sortOrder: 60,
          description: 'هدية تبويب الوكالة',
        },
        {
          name: 'مشاهير',
          category: 'celebrity',
          type: GiftType.PREMIUM,
          iconUrl: '/assets/gifts/mikoo/bg_celebrity_gift.webp',
          coinPrice: 799,
          sortOrder: 70,
          description: 'هدية مشاهير / celebrity',
        },
        {
          name: 'حظ بلس',
          category: 'lucky',
          type: GiftType.LUCKY,
          iconUrl: '/assets/gifts/mikoo/bg_lucky_gift_low_plus.webp',
          coinPrice: 2000,
          sortOrder: 4,
          description: 'طبقة حظ إضافية',
        },
      ];

      const luckyConfig = {
        winChance: 0.1,
        minMultiplier: 1,
        maxMultiplier: 5,
      };

      for (const seed of tabSeeds) {
        let existing = await this.giftsRepo.findOne({ where: { name: seed.name } });
        const diamondValue = Math.floor(
          seed.coinPrice *
            (seed.type === GiftType.LUCKY ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO),
        );
        if (!existing) {
          await this.giftsRepo.save(
            this.giftsRepo.create({
              name: seed.name,
              description: seed.description,
              iconUrl: seed.iconUrl,
              animationUrl:
                seed.type === GiftType.LUCKY
                  ? null
                  : '/visual-system/runtime.html',
              coinPrice: seed.coinPrice,
              diamondValue,
              type: seed.type,
              category: seed.category,
              isActive: true,
              sortOrder: seed.sortOrder,
              luckyConfig: seed.type === GiftType.LUCKY ? luckyConfig : null,
            } as any),
          );
          continue;
        }
        let dirty = false;
        if (existing.iconUrl !== seed.iconUrl) {
          existing.iconUrl = seed.iconUrl;
          dirty = true;
        }
        if ((existing as any).category !== seed.category) {
          (existing as any).category = seed.category;
          dirty = true;
        }
        if (existing.type !== seed.type) {
          existing.type = seed.type;
          dirty = true;
        }
        if (!existing.isActive) {
          existing.isActive = true;
          dirty = true;
        }
        if (dirty) await this.giftsRepo.save(existing);
      }

      // Country-flag gifts + any Mikoo catalog.json rows (partial until listV3 ticket).
      await this.importMikooCountryGiftsFile();
      await this.importMikooGiftCatalogFile();
      // JEHO designed flag frames + entry-style video gifts (self-hosted assets).
      await this.importJehoDesignedGifts();
    } catch (err) {
      this.log.warn(
        `ensureMikooGiftTabs skipped: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  }

  /**
   * Self-hosted flag-frame gifts + premium entry-style video gifts
   * (backend/public/assets/gifts/jeho/catalog.json).
   * Safe to call from admin even when DB is authoritative (inserts missing only;
   * updates icon/animation for matching names without touching coinPrice).
   */
  /**
   * Upsert professional still-image gifts. Never deletes video/flag/existing gifts.
   * Media type stays as uploaded: PNG/JPEG=image, GIF/WebP=anim, MP4=video on play.
   */
  async rebuildStillImageCatalog(): Promise<{
    deleted: number;
    created: number;
    updated: number;
    categories: number;
  }> {
    const catalogPath = join(
      process.cwd(),
      'public',
      'assets',
      'gifts',
      'jeho',
      'still-catalog.json',
    );
    if (!existsSync(catalogPath)) {
      throw new BadRequestException(
        'still-catalog.json missing under public/assets/gifts/jeho/',
      );
    }
    let raw: any;
    try {
      raw = JSON.parse(readFileSync(catalogPath, 'utf8'));
    } catch {
      throw new BadRequestException('still-catalog.json invalid JSON');
    }
    const gifts = Array.isArray(raw?.gifts) ? raw.gifts : [];
    if (!gifts.length) {
      throw new BadRequestException('still-catalog has no gifts');
    }

    // Ensure core category tabs exist (do NOT deactivate country / custom tabs).
    const coreCats: Array<{
      key: string;
      labelAr: string;
      labelEn: string;
      sortOrder: number;
    }> = Array.isArray(raw?.categories)
      ? raw.categories
      : [
          { key: 'normal', labelAr: 'عادي', labelEn: 'Normal', sortOrder: 0 },
          { key: 'lucky', labelAr: 'حظ', labelEn: 'Lucky', sortOrder: 1 },
          { key: 'combo', labelAr: 'كومبو', labelEn: 'Combo', sortOrder: 2 },
          { key: 'premium', labelAr: 'مميز', labelEn: 'Premium', sortOrder: 3 },
        ];
    let catN = 0;
    for (const d of coreCats) {
      let row = await this.categoriesRepo.findOne({ where: { key: d.key } });
      if (!row) {
        row = this.categoriesRepo.create({
          key: d.key,
          labelAr: d.labelAr,
          labelEn: d.labelEn,
          sortOrder: d.sortOrder,
          isActive: true,
        });
      } else {
        row.labelAr = d.labelAr;
        row.labelEn = d.labelEn;
        row.sortOrder = d.sortOrder;
        row.isActive = true;
      }
      await this.categoriesRepo.save(row);
      catN += 1;
    }

    const stillBase = '/assets/gifts/jeho/still';
    const versionTag = 'v=still2';
    let created = 0;
    let updated = 0;
    for (const g of gifts) {
      const code = String(g?.code || '').trim();
      const name = String(g?.nameAr || g?.name || '').trim().slice(0, 64);
      const file = String(g?.file || '').trim();
      if (!name || !file) continue;
      const iconUrl = `${stillBase}/${file}?${versionTag}`;
      // Image gift: icon + anim both still → app plays as IMAGE (can change anim to mp4 later).
      const animationUrl = iconUrl;
      const coinPrice = Math.max(1, Number(g?.coinPrice) || 10);
      const typeRaw = String(g?.type || 'normal').toLowerCase();
      const type =
        typeRaw === 'lucky'
          ? GiftType.LUCKY
          : typeRaw === 'combo'
            ? GiftType.COMBO
            : typeRaw === 'premium'
              ? GiftType.PREMIUM
              : GiftType.NORMAL;
      const category = String(g?.category || type || 'normal')
        .trim()
        .toLowerCase()
        .slice(0, 32);
      const lucky =
        type === GiftType.LUCKY || g?.lucky === true
          ? {
              mode: 'multiplier' as const,
              minMultiplier: 0.2,
              maxMultiplier: 20,
              winChance: 0.35,
            }
          : null;

      // Match by name — never overwrite gifts that already have real video animation.
      let existing = await this.giftsRepo.findOne({ where: { name } });
      if (existing) {
        const existingAnim = String(existing.animationUrl || '').toLowerCase();
        const isVideoGift =
          existingAnim.endsWith('.mp4') ||
          existingAnim.endsWith('.webm') ||
          existingAnim.endsWith('.mov') ||
          existingAnim.includes('.mp4?') ||
          existingAnim.includes('.webm?');
        if (isVideoGift) {
          // Keep video gifts untouched.
          continue;
        }
        let dirty = false;
        if (existing.iconUrl !== iconUrl) {
          existing.iconUrl = iconUrl;
          dirty = true;
        }
        if (!existing.animationUrl || !String(existing.animationUrl).includes('/still/')) {
          // Only set anim to still if empty / html placeholder — never clobber custom media.
          const a = String(existing.animationUrl || '').toLowerCase();
          if (!a || a.includes('runtime.html') || a.endsWith('.html')) {
            existing.animationUrl = animationUrl;
            dirty = true;
          }
        }
        if (!existing.isActive) {
          existing.isActive = true;
          dirty = true;
        }
        // Paid gifts with catalog diamondValue=0 awarded 0 diamonds (Math.min bug).
        if (
          Number(existing.diamondValue || 0) <= 0 &&
          Number(existing.coinPrice || coinPrice || 0) > 0
        ) {
          const price = Math.max(1, Number(existing.coinPrice) || coinPrice);
          existing.diamondValue = mintDiamondsPerUnit(
            price,
            0,
            type === GiftType.LUCKY ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO,
          );
          dirty = true;
        }
        if (dirty) {
          await this.giftsRepo.save(existing);
          updated += 1;
        }
        continue;
      }

      await this.giftsRepo.save(
        this.giftsRepo.create({
          name,
          description: `هدية صورة · ${code || name}`.slice(0, 255),
          iconUrl,
          animationUrl,
          coinPrice,
          diamondValue: Math.floor(
            coinPrice * (type === GiftType.LUCKY ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO),
          ),
          type,
          category,
          isActive: true,
          sortOrder: Number(g?.sortOrder) || created,
          luckyConfig: lucky,
        } as any),
      );
      created += 1;
    }

    this.log.log(
      `Still gifts merged (no wipe): created=${created}, updated=${updated}, cats=${catN}`,
    );
    return { deleted: 0, created, updated, categories: catN };
  }

  /**
   * JEHO designed packs: flag frames + premium video gifts (upsert only, no full wipe).
   * App plays media by file type: mp4 → video, gif/webp → anim, png → image.
   */
  async importJehoDesignedGifts(): Promise<{ flags: number; premium: number }> {
    const catalogPath = join(
      process.cwd(),
      'public',
      'assets',
      'gifts',
      'jeho',
      'catalog.json',
    );
    if (!existsSync(catalogPath)) {
      this.log.warn('JEHO gift catalog missing — run tools/generate_jeho_gift_assets.py');
      return { flags: 0, premium: 0 };
    }
    let raw: any;
    try {
      raw = JSON.parse(readFileSync(catalogPath, 'utf8'));
    } catch (err) {
      this.log.warn(
        `importJehoDesignedGifts parse: ${err instanceof Error ? err.message : String(err)}`,
      );
      return { flags: 0, premium: 0 };
    }
    const flags = Array.isArray(raw?.flags) ? raw.flags : [];
    const premium = Array.isArray(raw?.premium) ? raw.premium : [];
    let flagsN = 0;
    let premiumN = 0;

    for (const [i, c] of flags.entries()) {
      const name = String(c?.nameAr || c?.nameEn || '').trim().slice(0, 64);
      const iconUrl = String(c?.iconUrl || '').trim();
      const animationUrl = String(c?.animationUrl || '').trim();
      if (!name || !iconUrl) continue;
      const coinPrice = Math.max(1, Number(c?.coinPrice) || 99);
      let existing = await this.giftsRepo.findOne({ where: { name } });
      if (!existing) {
        await this.giftsRepo.save(
          this.giftsRepo.create({
            name,
            description: `هدية علم متحركة · ${c?.nameEn || ''}`.trim().slice(0, 255),
            iconUrl,
            animationUrl: animationUrl || iconUrl,
            coinPrice,
            diamondValue: Math.floor(coinPrice * GIFT_DIAMOND_RATIO),
            type: GiftType.NORMAL,
            category: 'country',
            isActive: true,
            sortOrder: Number(c?.sortOrder) || 400 + i,
          } as any),
        );
        flagsN += 1;
        continue;
      }
      let dirty = false;
      if (existing.iconUrl !== iconUrl) {
        existing.iconUrl = iconUrl;
        dirty = true;
      }
      if (animationUrl && existing.animationUrl !== animationUrl) {
        existing.animationUrl = animationUrl;
        dirty = true;
      }
      if ((existing as any).category !== 'country') {
        (existing as any).category = 'country';
        dirty = true;
      }
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      if (dirty) await this.giftsRepo.save(existing);
      flagsN += 1;
    }

    for (const [i, g] of premium.entries()) {
      const name = String(g?.nameAr || g?.nameEn || '').trim().slice(0, 64);
      const iconUrl = String(g?.iconUrl || '').trim();
      const animationUrl = String(g?.animationUrl || g?.previewUrl || '').trim();
      if (!name || !iconUrl) continue;
      const coinPrice = Math.max(1, Number(g?.coinPrice) || 999);
      let existing = await this.giftsRepo.findOne({ where: { name } });
      if (!existing) {
        await this.giftsRepo.save(
          this.giftsRepo.create({
            name,
            description: `هدية فيديو · ${g?.nameEn || ''}`.trim().slice(0, 255),
            iconUrl,
            animationUrl: animationUrl || iconUrl,
            coinPrice,
            diamondValue: Math.floor(coinPrice * GIFT_DIAMOND_RATIO),
            type: GiftType.PREMIUM,
            category: 'premium',
            isActive: true,
            sortOrder: Number(g?.sortOrder) || 50 + i,
          } as any),
        );
        premiumN += 1;
        continue;
      }
      let dirty = false;
      if (existing.iconUrl !== iconUrl) {
        existing.iconUrl = iconUrl;
        dirty = true;
      }
      if (animationUrl && existing.animationUrl !== animationUrl) {
        existing.animationUrl = animationUrl;
        dirty = true;
      }
      if ((existing as any).category !== 'premium') {
        (existing as any).category = 'premium';
        dirty = true;
      }
      if (existing.type !== GiftType.PREMIUM) {
        existing.type = GiftType.PREMIUM;
        dirty = true;
      }
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      if (dirty) await this.giftsRepo.save(existing);
      premiumN += 1;
    }

    this.log.log(
      `JEHO designed gifts synced (flags=${flagsN}, premium video=${premiumN})`,
    );
    return { flags: flagsN, premium: premiumN };
  }

  /** Public Mikoo country flags → gift tab "دولة" (does not need listV3 ticket). */
  private async importMikooCountryGiftsFile() {
    const countriesPath = join(
      process.cwd(),
      'public',
      'assets',
      'gifts',
      'mikoo',
      'countries.json',
    );
    if (!existsSync(countriesPath)) return;
    let items: any[] = [];
    try {
      const raw = JSON.parse(readFileSync(countriesPath, 'utf8'));
      items = Array.isArray(raw?.items) ? raw.items : [];
    } catch {
      return;
    }
    if (!items.length) return;

    let upserted = 0;
    for (const [i, c] of items.entries()) {
      const nameAr = String(c?.nameAr || c?.name || '').trim();
      const iconUrl = String(c?.icon || '').trim();
      if (!nameAr || !iconUrl) continue;
      const name = `علم ${nameAr}`.slice(0, 120);
      let existing = await this.giftsRepo.findOne({ where: { name } });
      const coinPrice = 99;
      if (!existing) {
        await this.giftsRepo.save(
          this.giftsRepo.create({
            name,
            description: `هدية علم دولة · ${c?.short || ''}`.trim(),
            iconUrl,
            animationUrl: '/visual-system/runtime.html',
            coinPrice,
            diamondValue: Math.floor(coinPrice * GIFT_DIAMOND_RATIO),
            type: GiftType.NORMAL,
            category: 'country',
            isActive: true,
            sortOrder: 400 + i,
          } as any),
        );
        upserted += 1;
        continue;
      }
      let dirty = false;
      if (existing.iconUrl !== iconUrl) {
        existing.iconUrl = iconUrl;
        dirty = true;
      }
      if ((existing as any).category !== 'country') {
        (existing as any).category = 'country';
        dirty = true;
      }
      if (!existing.isActive) {
        existing.isActive = true;
        dirty = true;
      }
      if (dirty) await this.giftsRepo.save(existing);
      upserted += 1;
    }
    if (upserted > 0) this.log.log(`Mikoo country gifts synced (${upserted} rows)`);
  }

  private async importMikooGiftCatalogFile() {
    const catalogPath = join(process.cwd(), 'public', 'assets', 'gifts', 'mikoo', 'catalog.json');
    if (!existsSync(catalogPath)) return;
    let items: any[] = [];
    try {
      const raw = JSON.parse(readFileSync(catalogPath, 'utf8'));
      items = Array.isArray(raw?.items) ? raw.items : [];
    } catch {
      return;
    }
    if (!items.length) return;

    let upserted = 0;
    for (const item of items) {
      const name = String(item.name || '').trim();
      const iconUrl = String(item.iconUrl || '').trim();
      if (!name || !iconUrl) continue;
      const category = String(item.category || 'normal');
      const typeRaw = String(item.type || 'normal').toLowerCase();
      const type =
        typeRaw === 'lucky'
          ? GiftType.LUCKY
          : typeRaw === 'premium'
            ? GiftType.PREMIUM
            : typeRaw === 'combo'
              ? GiftType.COMBO
              : GiftType.NORMAL;
      const coinPrice = Math.max(0, Number(item.coinPrice) || 0);
      let existing = await this.giftsRepo.findOne({ where: { name } });
      if (!existing) {
        await this.giftsRepo.save(
          this.giftsRepo.create({
            name,
            description: `مستورد (${category})`,
            iconUrl,
            animationUrl: item.animationUrl || '/visual-system/runtime.html',
            coinPrice,
            diamondValue: Math.floor(
              coinPrice * (type === GiftType.LUCKY ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO),
            ),
            type,
            category,
            isActive: true,
            sortOrder: 1000 + upserted,
            luckyConfig:
              type === GiftType.LUCKY
                ? { winChance: 0.1, minMultiplier: 1, maxMultiplier: 5 }
                : null,
          } as any),
        );
        upserted += 1;
        continue;
      }
      // Keep coinPrice from DB — catalog file must not overwrite dashboard prices.
      existing.iconUrl = iconUrl;
      existing.animationUrl = item.animationUrl || existing.animationUrl;
      (existing as any).category = category;
      existing.type = type;
      existing.isActive = true;
      await this.giftsRepo.save(existing);
      upserted += 1;
    }
    if (upserted > 0) this.log.log(`Mikoo gift catalog synced (${upserted} rows)`);
  }

  private async assertGiftTarget(dto: SendGiftDto) {
    // Chat / DM gifts: no room — only verify the receiver exists.
    if (!dto.roomId) {
      const receiver = await this.usersRepo.findOne({ where: { id: dto.receiverId } });
      if (!receiver) throw new NotFoundException('Receiver not found');
      return;
    }
    const room = await this.roomsRepo.findOne({ where: { id: dto.roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const rid = String(dto.receiverId || '');
    // Live host on stage (seat 0 / activeHost / owner) is always giftable.
    const isLiveHost =
      (!!room.hostId && String(room.hostId) === rid) ||
      (!!room.cohostId && String(room.cohostId) === rid) ||
      (!!room.activeHostId && String(room.activeHostId) === rid);
    if (isLiveHost) return;

    const seated = await this.seatsRepo.findOne({
      where: [
        { roomId: dto.roomId, userId: dto.receiverId },
        { roomId: dto.roomId, isHostSeat: true, userId: dto.receiverId },
      ],
    });
    if (seated) return;

    throw new BadRequestException('Receiver is not in this room');
  }

  /**
   * House-positive lucky roll with frequent soft rebates (Mikoo-style feel).
   * Approx EV ≈ 0.52–0.55 of stake — platform keeps the rest + diamond cut every send.
   * Soft returns are common so users rarely see an empty round (no harsh lose UX).
   * Tiers:
   *  - 40% soft 0.30–0.80×
   *  - 10% medium 1.20–2.50×
   *  - 2.5% big 3–max× (hard-capped)
   */
  private rollLuckyPayout(
    totalCoins: number,
    cfg?: {
      winChance?: number;
      minMultiplier?: number;
      maxMultiplier?: number;
    } | null,
  ): LuckyRoll {
    const hardMax = Math.max(1, Number(LUCKY_GIFT_MAX_MULTIPLIER) || 5);
    const maxMul = Math.min(
      hardMax,
      Math.max(3, Number(cfg?.maxMultiplier) || hardMax),
    );
    // ≈ EV 0.40–0.42 coin rebate to sender (house keeps majority + diamond cut).
    const roll = randomInt(0, 1_000_000) / 1_000_000;
    let luckyMultiplier: number | null = null;
    if (roll < 0.38) {
      const frac = randomInt(0, 1_000_000) / 1_000_000;
      luckyMultiplier = Math.round((0.25 + frac * 0.45) * 100) / 100; // 0.25–0.70
    } else if (roll < 0.47) {
      const frac = randomInt(0, 1_000_000) / 1_000_000;
      luckyMultiplier = Math.round((1.1 + frac * 0.9) * 100) / 100; // 1.10–2.00
    } else if (roll < 0.485) {
      const frac = randomInt(0, 1_000_000) / 1_000_000;
      luckyMultiplier = Math.round((2.5 + frac * (maxMul - 2.5)) * 100) / 100;
    }
    const luckyCoinsWon =
      luckyMultiplier != null
        ? Math.max(1, Math.floor(Math.max(0, totalCoins) * luckyMultiplier))
        : 0;
    return { luckyMultiplier, luckyCoinsWon };
  }

  async catalog() {
    const gifts = await this.giftsRepo.find({
      where: { isActive: true },
      order: { sortOrder: 'ASC', coinPrice: 'ASC' },
    });
    return gifts.filter((g) => !this.isLuckyBoxCatalogGift(g));
  }

  /** Active gift sheet tabs for the client (keys match gift.category). */
  async listCategories() {
    await this.ensureDefaultGiftCategories();
    const rows = await this.categoriesRepo.find({
      where: { isActive: true },
      order: { sortOrder: 'ASC', createdAt: 'ASC' },
    });
    if (rows.length > 0) return rows;
    // Hard fallback if DB table empty / migrations lag.
    return [
      { key: 'normal', labelAr: 'عادي', labelEn: 'Normal', sortOrder: 0, isActive: true },
      { key: 'lucky', labelAr: 'حظ', labelEn: 'Lucky', sortOrder: 1, isActive: true },
      { key: 'combo', labelAr: 'كومبو', labelEn: 'Combo', sortOrder: 2, isActive: true },
      { key: 'premium', labelAr: 'مميز', labelEn: 'Premium', sortOrder: 3, isActive: true },
    ];
  }

  async listCategoriesAdmin() {
    await this.ensureDefaultGiftCategories();
    return this.categoriesRepo.find({
      order: { sortOrder: 'ASC', createdAt: 'ASC' },
    });
  }

  async upsertCategory(
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
    let row = id ? await this.categoriesRepo.findOne({ where: { id } }) : null;
    if (id && !row) throw new NotFoundException('Gift category not found');
    const keyRaw = (dto.key ?? row?.key ?? '').trim().toLowerCase();
    const key = keyRaw.replace(/[^a-z0-9_\-]/g, '').slice(0, 32);
    if (!key) throw new BadRequestException('category key required');
    if (!row) {
      const clash = await this.categoriesRepo.findOne({ where: { key } });
      if (clash) throw new BadRequestException('category key already exists');
      row = this.categoriesRepo.create({
        key,
        labelAr: (dto.labelAr || key).trim(),
        labelEn: dto.labelEn?.trim() || null,
        sortOrder: dto.sortOrder ?? 0,
        isActive: dto.isActive ?? true,
        iconUrl: dto.iconUrl ?? null,
      });
    } else {
      if (dto.key && key !== row.key) {
        const clash = await this.categoriesRepo.findOne({ where: { key } });
        if (clash && clash.id !== row.id) {
          throw new BadRequestException('category key already exists');
        }
        const oldKey = row.key;
        row.key = key;
        // Keep gifts pointing to the renamed tab.
        try {
          await this.giftsRepo
            .createQueryBuilder()
            .update()
            .set({ category: key })
            .where('category = :oldKey', { oldKey })
            .execute();
        } catch {
          /* column may lag */
        }
      }
      if (dto.labelAr != null) row.labelAr = dto.labelAr.trim() || row.labelAr;
      if (dto.labelEn !== undefined) row.labelEn = dto.labelEn?.trim() || null;
      if (dto.sortOrder != null) row.sortOrder = Number(dto.sortOrder) || 0;
      if (dto.isActive != null) row.isActive = !!dto.isActive;
      if (dto.iconUrl !== undefined) row.iconUrl = dto.iconUrl || null;
    }
    return this.categoriesRepo.save(row);
  }

  async deleteCategory(id: string) {
    const row = await this.categoriesRepo.findOne({ where: { id } });
    if (!row) throw new NotFoundException('Gift category not found');
    const total = await this.categoriesRepo.count();
    if (total <= 1) {
      throw new BadRequestException('يجب الإبقاء على فئة واحدة على الأقل');
    }
    // Move gifts off this tab so the sheet stays consistent.
    const others = await this.categoriesRepo.find({
      order: { sortOrder: 'ASC', createdAt: 'ASC' },
    });
    const fallback =
      others.find((c) => c.id !== row.id)?.key ||
      others.find((c) => c.key !== row.key)?.key ||
      'normal';
    try {
      await this.giftsRepo
        .createQueryBuilder()
        .update()
        .set({ category: fallback })
        .where('category = :key', { key: row.key })
        .execute();
    } catch (err) {
      this.log.warn(
        `deleteCategory reassign gifts: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
    await this.categoriesRepo.remove(row);
    return { ok: true, reassignedTo: fallback };
  }

  async send(senderId: string, dto: SendGiftDto) {
    const selfGift = senderId === dto.receiverId;
    // Room owner / host / anyone: never gift yourself (no self-support).
    if (selfGift) {
      throw new BadRequestException(
        'الدعم الذاتي غير مسموح — لا يمكن إرسال هدية لنفسك',
      );
    }
    const gift = await this.giftsRepo.findOne({ where: { id: dto.giftId, isActive: true } });
    if (!gift) throw new NotFoundException('Gift not found');
    await this.assertGiftTarget(dto);

    const giftCategory = String((gift as any).category || '')
      .trim()
      .toLowerCase();
    if (giftCategory === 'cp') {
      const rows = await this.dataSource.query(
        `SELECT 1 FROM social_requests
          WHERE type = 'relation' AND status = 'accepted'
            AND (("fromUserId" = $1 AND "toUserId" = $2)
              OR ("fromUserId" = $2 AND "toUserId" = $1))
          LIMIT 1`,
        [senderId, dto.receiverId],
      );
      if (!rows?.length) {
        throw new BadRequestException(
          'هدايا CP للشريك فقط — أكملا ربط CP من مساحة CP أولاً',
        );
      }
    }

    const isLucky = gift.type === GiftType.LUCKY;
    const qtyCap = isLucky ? 177 : 99;
    const qty = Math.max(1, Math.min(qtyCap, Math.floor(Number(dto.quantity) || 1)));
    // Lucky gifts never ride a client combo streak (combo ≠ مردود).
    const comboCount = isLucky
      ? 1
      : Math.max(1, Math.min(99, Math.floor(Number(dto.comboCount) || 1)));
    const coinPrice = Math.max(0, Number(gift.coinPrice) || 0);
    const totalCoins = coinPrice * qty;
    const ratioCap = isLucky ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO;
    // Never use Math.min(catalog=0, …) — that wiped diamonds on unpaid catalog rows.
    const cappedPerUnit = mintDiamondsPerUnit(coinPrice, gift.diamondValue, ratioCap);
    // Receiver diamonds stay base (no multiplier). Lucky jackpot returns coins to sender.
    const diamondsAwarded = cappedPerUnit * qty;
    let luckyMultiplier: number | null = null;
    let luckyCoinsWon = 0;

    if (isLucky) {
      const rolled = this.rollLuckyPayout(totalCoins, gift.luckyConfig);
      luckyMultiplier = rolled.luckyMultiplier;
      luckyCoinsWon = rolled.luckyCoinsWon;
    }

    return this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId: senderId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet || Number(wallet.coins) < totalCoins) {
        throw new BadRequestException('Insufficient coins');
      }

      wallet.coins = Number(wallet.coins) - totalCoins;
      if (luckyCoinsWon > 0) {
        wallet.coins = Number(wallet.coins) + luckyCoinsWon;
      }
      await manager.save(wallet);

      // Persist the gift row first so every wallet ledger line can reference a unique send.id
      // (uq_wallet_tx_user_reference forbids reusing agency.id for every commission).
      const send = await manager.save(
        manager.create(GiftSend, {
          giftId: gift.id,
          giftName: gift.name,
          giftIconUrl: gift.iconUrl,
          senderId,
          receiverId: dto.receiverId,
          roomId: dto.roomId || null,
          quantity: qty,
          comboCount,
          totalCoins,
          diamondsAwarded,
          luckyMultiplier,
        }),
      );

      // Gift diamond split — TWO SEPARATE WALLET POOLS:
      // - Personal room (no agency room / no matching agency membership):
      //     platform / host → wallet.diamonds  (personal withdraw to platform)
      // - Agency room (room.agencyId + active agency member of THAT agency):
      //     platform / agency owner / host → wallet.agencyDiamonds for both owner commission + host share
      // Agency commission never mixes with personal-room balances.
      let hostDiamonds = diamondsAwarded;
      let agentShare = 0;
      let platformCut = 0;
      let agencyId: string | null = null;
      let earningsStream: 'personal' | 'agency' = 'personal';

      let giftRoom: Room | null = null;
      if (dto.roomId) {
        giftRoom = await manager.findOne(Room, { where: { id: dto.roomId } });
      }
      const roomAgencyId =
        giftRoom?.agencyId ||
        (giftRoom?.roomKind === RoomKind.AGENCY ? giftRoom.agencyId : null) ||
        null;

      let membership: AgencyMember | null = null;
      if (roomAgencyId) {
        membership = await manager.findOne(AgencyMember, {
          where: {
            userId: dto.receiverId,
            agencyId: roomAgencyId,
            isActive: true,
            status: AgencyMemberStatus.ACTIVE,
          },
        });
      }

      let platformPct = 40;
      const cutRow = await manager.findOne(AppSetting, {
        where: { key: 'agency_platform_cut_percent' },
      });
      if (cutRow?.value && Number.isFinite(Number(cutRow.value))) {
        platformPct = Math.min(100, Math.max(0, Number(cutRow.value)));
      }
      let hostSharePct = 45;
      const hostShareRow = await manager.findOne(AppSetting, {
        where: { key: 'agency_host_share_percent' },
      });
      if (hostShareRow?.value && Number.isFinite(Number(hostShareRow.value))) {
        hostSharePct = Math.min(100, Math.max(0, Number(hostShareRow.value)));
      }

      if (membership && roomAgencyId) {
        const agency = await manager.findOne(Agency, { where: { id: membership.agencyId } });
        if (agency?.status === AgencyStatus.ACTIVE) {
          agencyId = agency.id;
          earningsStream = 'agency';
          const agencyPct = Math.min(100, Math.max(0, Number(agency.commissionPercent) || 15));
          const receiverIsOwner = String(agency.ownerId) === String(dto.receiverId);
          const split = independentAgencyGiftSplit(
            diamondsAwarded,
            hostSharePct,
            agencyPct,
            platformPct,
            { ownerIsReceiver: receiverIsOwner },
          );
          agentShare = split.agentShare;
          platformCut = split.platformCut;
          hostDiamonds = split.hostDiamonds;
          if (agentShare > 0 && agency.ownerId) {
              let agentWallet = await manager.findOne(Wallet, {
                where: { userId: agency.ownerId },
                lock: { mode: 'pessimistic_write' },
              });
              if (!agentWallet) {
                agentWallet = manager.create(Wallet, { userId: agency.ownerId });
              }
              (agentWallet as any).agencyDiamonds =
                Number((agentWallet as any).agencyDiamonds || 0) + agentShare;
              await manager.save(agentWallet);
              await manager.save(
                manager.create(WalletTransaction, {
                  userId: agency.ownerId,
                  type: TransactionType.GIFT_RECEIVE,
                  currency: CurrencyType.DIAMONDS,
                  amount: agentShare,
                  balanceAfter: Number((agentWallet as any).agencyDiamonds || 0),
                  referenceType: 'agency_commission',
                  referenceId: send.id,
                  description: `عمولة وكالة (روم وكالة) — ${gift.name}`,
                  metadata: {
                    agencyId: agency.id,
                    giftSendId: send.id,
                    stream: 'agency',
                    roomId: dto.roomId || null,
                  },
                }),
              );
          }
          await manager.increment(Agency, { id: agency.id }, 'totalDiamonds', diamondsAwarded);
        }
      }

      if (earningsStream === 'personal') {
        platformCut = Math.floor((diamondsAwarded * platformPct) / 100);
        hostDiamonds = Math.max(0, diamondsAwarded - platformCut);
        agentShare = 0;
        agencyId = null;
      }

      if (platformCut > 0) {
        let rev = await manager.findOne(AppSetting, {
          where: { key: 'platform_gift_revenue_diamonds' },
        });
        if (!rev) {
          rev = manager.create(AppSetting, {
            key: 'platform_gift_revenue_diamonds',
            value: '0',
            description: 'Cumulative platform cut from gifts (diamonds)',
          });
        }
        rev.value = String(Number(rev.value || 0) + platformCut);
        await manager.save(rev);
      }

      let recvWallet: Wallet = wallet;
      if (!selfGift) {
        const found = await manager.findOne(Wallet, {
          where: { userId: dto.receiverId },
          lock: { mode: 'pessimistic_write' },
        });
        recvWallet = found ?? manager.create(Wallet, { userId: dto.receiverId });
      }
      if (earningsStream === 'agency') {
        (recvWallet as any).agencyDiamonds =
          Number((recvWallet as any).agencyDiamonds || 0) + hostDiamonds;
      } else {
        recvWallet.diamonds = Number(recvWallet.diamonds) + hostDiamonds;
      }
      await manager.save(recvWallet);

      await manager.save(
        manager.create(WalletTransaction, {
          userId: senderId,
          type: TransactionType.GIFT_SEND,
          currency: CurrencyType.COINS,
          amount: -totalCoins,
          balanceAfter: Number(wallet.coins) - luckyCoinsWon,
          referenceType: 'gift_send',
          referenceId: send.id,
          description: `Sent ${qty}x ${gift.name}`,
        }),
      );
      if (luckyCoinsWon > 0 && luckyMultiplier) {
        await manager.save(
          manager.create(WalletTransaction, {
            userId: senderId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS,
            amount: luckyCoinsWon,
            balanceAfter: Number(wallet.coins),
            referenceType: 'lucky_gift',
            referenceId: send.id,
            description: `Lucky ×${luckyMultiplier} on ${gift.name}`,
            metadata: {
              luckyMultiplier,
              giftId: gift.id,
              giftName: gift.name,
              coinPrice,
              quantity: qty,
              totalCoins,
            },
          }),
        );
      }
      await manager.save(
        manager.create(WalletTransaction, {
          userId: dto.receiverId,
          type: TransactionType.GIFT_RECEIVE,
          currency: CurrencyType.DIAMONDS,
          amount: hostDiamonds,
          balanceAfter:
            earningsStream === 'agency'
              ? Number((recvWallet as any).agencyDiamonds || 0)
              : Number(recvWallet.diamonds),
          referenceType:
            earningsStream === 'agency' ? 'gift_receive_agency' : 'gift_receive',
          referenceId: send.id,
          description:
            earningsStream === 'agency'
              ? `هدية روم وكالة ${qty}x ${gift.name}`
              : `هدية روم شخصي ${qty}x ${gift.name}`,
          metadata: {
            ...(luckyMultiplier ? { luckyMultiplier } : {}),
            platformCut,
            hostDiamonds,
            stream: earningsStream,
            ...(agencyId ? { agencyId, agentShare } : {}),
            roomId: dto.roomId || null,
          },
        }),
      );

      await manager.increment(UserProfile, { userId: senderId }, 'totalSentCoins', totalCoins);
      await manager.increment(
        UserProfile,
        { userId: dto.receiverId },
        'totalReceivedDiamonds',
        diamondsAwarded,
      );

      return {
        send,
        gift,
        comboCount,
        luckyMultiplier,
        luckyCoinsWon,
        coinPrice,
        quantity: qty,
        totalCoins,
        hostDiamonds,
        platformCut,
        agentShare,
        stream: earningsStream,
        senderBalance: Number(wallet.coins),
        receiverDiamonds:
          earningsStream === 'agency'
            ? Number((recvWallet as any).agencyDiamonds || 0)
            : Number(recvWallet.diamonds),
        success: true,
        coinsSpent: totalCoins,
        // Transparent Mikoo-style breakdown for client UI.
        breakdown: {
          coinsSpent: totalCoins,
          luckyReturn: luckyCoinsWon,
          luckyMultiplier: luckyMultiplier || 0,
          diamondsToHost: hostDiamonds,
          diamondsPlatform: platformCut,
          diamondsAgency: agentShare,
          diamondPool: diamondsAwarded,
          stream: earningsStream,
        },
        wallet: {
          coins: Number(wallet.coins),
          diamonds: Number(recvWallet.diamonds || 0),
          agencyDiamonds: Number((recvWallet as any).agencyDiamonds || 0),
          silverCoins: Number(wallet.silverCoins || 0),
          gamePoints: Number(wallet.gamePoints || 0),
        },
      };
    }).then(async (result) => {
      const taskRoom = dto.roomId
        ? await this.roomsRepo.findOne({ where: { id: dto.roomId } })
        : null;
      void this.tasksService
        .recordProgress(senderId, 'gift', 1, {
          roomId: dto.roomId,
          agencyId: taskRoom?.agencyId || undefined,
        })
        .catch(() => undefined);
      if (result.send?.receiverId) {
        void this.tasksService
          .recordProgress(result.send.receiverId, 'host_gift', 1, {
            roomId: dto.roomId,
            agencyId: taskRoom?.agencyId || undefined,
          })
          .catch(() => undefined);
        const spentCoins = gift.coinPrice * qty;
        const diamondProgress = Number(result.hostDiamonds ?? result.send?.diamondsAwarded ?? 0);
        const hostTarget = this.hostTarget;
        if (hostTarget) {
          void hostTarget
            .getConfig()
            .then((cfg) => {
              if (!cfg?.enabled) return null;
              const amount =
                cfg.currency === 'gift_coins' ? spentCoins : diamondProgress;
              if (amount <= 0) return null;
              return hostTarget.recordHostProgress(
                result.send.receiverId,
                amount,
              );
            })
            .catch(() => undefined);
        }
      }
      const spent = gift.coinPrice * qty;
      const contestContext = {
        roomId: dto.roomId,
        agencyId: taskRoom?.agencyId || undefined,
      };
      void this.contestsService
        ?.addScore(senderId, spent, 'gifts', contestContext)
        .catch(() => undefined);
      const receiverId = result.send?.receiverId;
      if (receiverId) {
        void this.contestsService
          ?.addScore(receiverId, spent, 'popular', contestContext)
          .catch(() => undefined);
      }
      const sender = await this.usersRepo.findOne({ where: { id: senderId } });
      const senderProfile = await this.profilesRepo.findOne({ where: { userId: senderId } });
      const vip = await this.userVipRepo.findOne({
        where: {
          userId: senderId,
          isActive: true,
          expiresAt: MoreThan(new Date()),
        },
        order: { level: 'DESC' },
      });
      const vipLevel = effectiveVipLevel(
        Number(vip?.level || 0),
        Number(senderProfile?.totalSentCoins || 0),
      );
      let roomSpend = gift.coinPrice * qty;
      if (dto.roomId) {
        const qb = this.sendsRepo
          .createQueryBuilder('g')
          .select('COALESCE(SUM(g.totalCoins), 0)', 'total')
          .where('g.senderId = :senderId', { senderId })
          .andWhere('g.roomId = :roomId', { roomId: dto.roomId });
        const raw = await qb.getRawOne<{ total: string }>();
        roomSpend = Math.max(roomSpend, Number(raw?.total || 0));
      }
      const supporterMin = Number(
        (await this.settingsRepo.findOne({ where: { key: 'room.supporter_min_coins' } }))?.value,
      );
      const legendaryMin = Number(
        (await this.settingsRepo.findOne({ where: { key: 'room.legendary_min_coins' } }))?.value,
      );
      const sMin = Number.isFinite(supporterMin) ? supporterMin : 10000;
      const lMin = Number.isFinite(legendaryMin) ? legendaryMin : 50000;
      const supporterTier =
        roomSpend >= lMin
          ? 'legendary'
          : roomSpend >= sMin
            ? 'supporter'
            : vipLevel > 0
              ? 'vip'
              : 'normal';

      const receiverUser = await this.usersRepo.findOne({ where: { id: dto.receiverId } });
      const receiverProfile = await this.profilesRepo.findOne({
        where: { userId: dto.receiverId },
      });
      let receiverSeatIndex: number | null = null;
      if (dto.roomId) {
        const receiverSeat = await this.seatsRepo.findOne({
          where: { roomId: dto.roomId, userId: dto.receiverId },
        });
        if (receiverSeat) receiverSeatIndex = receiverSeat.seatIndex;
      }
      let receiverRoomGiftTotal = 0;
      if (dto.roomId) {
        const totalRow = await this.sendsRepo
          .createQueryBuilder('g')
          .select('COALESCE(SUM(g.totalCoins), 0)', 'total')
          .where('g.roomId = :roomId', { roomId: dto.roomId })
          .andWhere('g.receiverId = :receiverId', { receiverId: dto.receiverId })
          .getRawOne<{ total: string }>();
        receiverRoomGiftTotal = Number(totalRow?.total || 0);
      }

      const animationPayload: Record<string, unknown> = {
        giftId: gift.id,
        giftName: gift.name,
        giftType: gift.type,
        giftIconUrl: gift.iconUrl,
        iconUrl: gift.iconUrl,
        animationUrl:
          resolvePlayableGiftAnimation({
            giftName: gift.name,
            iconUrl: gift.iconUrl,
            animationUrl: gift.animationUrl,
          }) || gift.animationUrl,
        senderId,
        senderName: sender?.displayName || sender?.username || 'User',
        senderAvatarUrl: sender?.avatarUrl || null,
        senderHostBadgeUrl: senderProfile?.hostBadgeUrl || null,
        senderVipBadgeUrl: senderProfile?.vipBadgeUrl || null,
        senderVipLevel: vipLevel,
        senderUserLevel: Math.max(1, Number(sender?.level || 1)),
        senderWealthScore: Math.max(0, Number(senderProfile?.totalSentCoins || 0)),
        senderCharmScore: Math.max(0, Number(senderProfile?.totalReceivedDiamonds || 0)),
        supporterTier,
        roomSpendCoins: roomSpend,
        effectPriority:
          supporterTier === 'legendary' || gift.coinPrice * qty >= 5000
            ? 100
            : supporterTier === 'supporter'
              ? 80
              : 40,
        receiverId: dto.receiverId,
        receiverName: receiverUser?.displayName || receiverUser?.username || 'User',
        receiverAvatarUrl: receiverUser?.avatarUrl || null,
        receiverHostBadgeUrl: receiverProfile?.hostBadgeUrl || null,
        receiverVipBadgeUrl: receiverProfile?.vipBadgeUrl || null,
        receiverSeatIndex,
        receiverRoomGiftTotal,
        brandAgencyId: null as string | null,
        brandAgencyName: null as string | null,
        brandAgencyLogoUrl: null as string | null,
        brandAgencyPublicId: null as string | null,
        roomId: dto.roomId || null,
        quantity: qty,
        comboCount,
        totalCoins: gift.coinPrice * qty,
        diamondsAwarded: result.send?.diamondsAwarded ?? 0,
        luckyMultiplier: result.luckyMultiplier || null,
        luckyCoinsWon: result.luckyCoinsWon || 0,
        at: new Date().toISOString(),
      };

      // Optional branded gift (admin-linked agency on gift catalog).
      const brandId = (gift as any).brandAgencyId || null;
      if (brandId) {
        const brandAgency = await this.dataSource.getRepository(Agency).findOne({
          where: { id: brandId },
        });
        if (brandAgency) {
          animationPayload.brandAgencyId = brandAgency.id;
          animationPayload.brandAgencyName = brandAgency.name;
          animationPayload.brandAgencyLogoUrl = brandAgency.logoUrl;
          animationPayload.brandAgencyPublicId = brandAgency.publicId || null;
        }
      } else if (taskRoom?.agencyId) {
        const brandAg = await this.dataSource.getRepository(Agency).findOne({
          where: { id: taskRoom.agencyId },
        });
        if (brandAg && brandAg.isVerified && brandAg.status === AgencyStatus.ACTIVE) {
          animationPayload.brandAgencyId = brandAg.id;
          animationPayload.brandAgencyName = brandAg.name;
          animationPayload.brandAgencyLogoUrl = brandAg.logoUrl;
          animationPayload.brandAgencyPublicId = brandAg.publicId || null;
        }
      }
      if (dto.roomId) {
        this.realtime?.emitToRoom(dto.roomId, 'room:event', {
          roomId: dto.roomId,
          event: 'gift:animation',
          payload: animationPayload,
          from: { userId: senderId },
          at: animationPayload.at,
        });
        this.realtime?.emitToRoom(dto.roomId, 'room:event', {
          roomId: dto.roomId,
          event: 'gift:send',
          payload: animationPayload,
          from: { userId: senderId },
          at: animationPayload.at,
        });
        if (result.luckyMultiplier && result.luckyCoinsWon > 0) {
          this.realtime?.emitToRoom(dto.roomId, 'room:event', {
            roomId: dto.roomId,
            event: 'lucky:hit',
            payload: {
              senderId,
              senderName: animationPayload.senderName,
              senderAvatarUrl: animationPayload.senderAvatarUrl,
              giftName: gift.name,
              giftIconUrl: gift.iconUrl,
              coinPrice: gift.coinPrice,
              quantity: qty,
              luckyMultiplier: result.luckyMultiplier,
              luckyCoinsWon: result.luckyCoinsWon,
              formula: `${gift.coinPrice * qty} × ${result.luckyMultiplier}`,
            },
            from: { userId: senderId },
            at: animationPayload.at,
          });
          this.broadcastLuckyCelebration({
            roomId: dto.roomId,
            senderId,
            displayName:
              typeof animationPayload.senderName === 'string'
                ? animationPayload.senderName
                : undefined,
            avatarUrl:
              typeof animationPayload.senderAvatarUrl === 'string'
                ? animationPayload.senderAvatarUrl
                : undefined,
            giftName: gift.name,
            giftIconUrl: gift.iconUrl,
            coinPrice: gift.coinPrice,
            quantity: qty,
            luckyMultiplier: result.luckyMultiplier,
            luckyCoinsWon: result.luckyCoinsWon,
          });
        }
        if (taskRoom?.agencyId) {
          const agencyRooms = await this.roomsRepo.find({
            where: { agencyId: taskRoom.agencyId, status: RoomStatus.OPEN },
          });
          for (const agencyRoom of agencyRooms) {
            if (agencyRoom.id === dto.roomId) continue;
            this.realtime?.emitToRoom(agencyRoom.id, 'room:event', {
              roomId: agencyRoom.id,
              event: 'agency:supporters_updated',
              payload: {
                agencyId: taskRoom.agencyId,
                sourceRoomId: dto.roomId,
              },
              from: { userId: senderId },
              at: animationPayload.at,
            });
          }
        }
      }
      this.realtime?.emitToUser(dto.receiverId, 'gift:animation', animationPayload);
      this.realtime?.emitToUser(senderId, 'gift:animation', animationPayload);
      return result;
    });
  }

  /**
   * All-mic send (Mikoo-style bill × personCount):
   * - Charge coinPrice × qty × N once
   * - One lucky roll on the full bill (coins rebate to sender only)
   * - Diamond pool from ratio; platform cut first
   * - Remainder: 50% → room host, 50% → seated mics (equal split)
   */
  async sendAllMic(senderId: string, dto: SendAllMicGiftDto) {
    const rawIds = Array.isArray(dto.receiverIds) ? dto.receiverIds : [];
    const receiverIds = [
      ...new Set(
        rawIds
          .map((id) => String(id || '').trim())
          .filter((id) => id.length > 0 && id !== senderId),
      ),
    ].slice(0, 20);
    if (receiverIds.length === 0) {
      throw new BadRequestException(
        'الدعم الذاتي غير مسموح — اختر مستلمين آخرين على المايك',
      );
    }
    if (receiverIds.length === 1) {
      return this.send(senderId, {
        giftId: dto.giftId,
        receiverId: receiverIds[0],
        quantity: dto.quantity,
        roomId: dto.roomId,
        comboCount: dto.comboCount,
      });
    }

    const room = await this.roomsRepo.findOne({ where: { id: dto.roomId } });
    if (!room) throw new NotFoundException('Room not found');
    const roomHostId = String(room.activeHostId || room.hostId || '');
    if (!roomHostId) throw new BadRequestException('Room has no host');

    for (const rid of receiverIds) {
      await this.assertGiftTarget({
        giftId: dto.giftId,
        receiverId: rid,
        roomId: dto.roomId,
        quantity: dto.quantity,
      });
    }

    const gift = await this.giftsRepo.findOne({
      where: { id: dto.giftId, isActive: true },
    });
    if (!gift) throw new NotFoundException('Gift not found');

    const isLucky = gift.type === GiftType.LUCKY;
    const qtyCap = isLucky ? 177 : 99;
    const qty = Math.max(1, Math.min(qtyCap, Math.floor(Number(dto.quantity) || 1)));
    const comboCount = isLucky
      ? 1
      : Math.max(1, Math.min(99, Math.floor(Number(dto.comboCount) || 1)));
    const personCount = receiverIds.length;
    const coinPrice = Math.max(0, Number(gift.coinPrice) || 0);
    const totalCoins = coinPrice * qty * personCount;
    const ratioCap = isLucky ? LUCKY_GIFT_DIAMOND_RATIO : GIFT_DIAMOND_RATIO;
    const cappedPerUnit = mintDiamondsPerUnit(coinPrice, gift.diamondValue, ratioCap);
    const diamondPool = cappedPerUnit * qty * personCount;

    let luckyMultiplier: number | null = null;
    let luckyCoinsWon = 0;
    if (isLucky) {
      const rolled = this.rollLuckyPayout(totalCoins, gift.luckyConfig);
      luckyMultiplier = rolled.luckyMultiplier;
      luckyCoinsWon = rolled.luckyCoinsWon;
    }

    let platformPct = 40;
    const cutRow = await this.settingsRepo.findOne({
      where: { key: 'agency_platform_cut_percent' },
    });
    if (cutRow?.value && Number.isFinite(Number(cutRow.value))) {
      platformPct = Math.min(50, Math.max(0, Number(cutRow.value)));
    }
    const platformCut = Math.floor((diamondPool * platformPct) / 100);
    const rem = Math.max(0, diamondPool - platformCut);
    const hostDiamonds = Math.floor(rem / 2);
    const micsPool = rem - hostDiamonds;
    const baseMic = Math.floor(micsPool / personCount);
    let leftover = micsPool - baseMic * personCount;
    const micShares = new Map<string, number>();
    for (const rid of receiverIds) {
      const extra = leftover > 0 ? 1 : 0;
      if (leftover > 0) leftover -= 1;
      micShares.set(rid, baseMic + extra);
    }

    return this.dataSource.transaction(async (manager) => {
      const wallet = await manager.findOne(Wallet, {
        where: { userId: senderId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet || Number(wallet.coins) < totalCoins) {
        throw new BadRequestException('Insufficient coins');
      }
      wallet.coins = Number(wallet.coins) - totalCoins;
      if (luckyCoinsWon > 0) {
        wallet.coins = Number(wallet.coins) + luckyCoinsWon;
      }
      await manager.save(wallet);

      if (platformCut > 0) {
        let rev = await manager.findOne(AppSetting, {
          where: { key: 'platform_gift_revenue_diamonds' },
        });
        if (!rev) {
          rev = manager.create(AppSetting, {
            key: 'platform_gift_revenue_diamonds',
            value: '0',
            description: 'Cumulative platform cut from gifts (diamonds)',
          });
        }
        rev.value = String(Number(rev.value || 0) + platformCut);
        await manager.save(rev);
      }

      const unitCoins = coinPrice * qty;
      const sends: GiftSend[] = [];
      for (const rid of receiverIds) {
        const d = micShares.get(rid) || 0;
        const send = await manager.save(
          manager.create(GiftSend, {
            giftId: gift.id,
            giftName: gift.name,
            giftIconUrl: gift.iconUrl,
            senderId,
            receiverId: rid,
            roomId: dto.roomId,
            quantity: qty,
            comboCount,
            totalCoins: unitCoins,
            diamondsAwarded: d,
            luckyMultiplier: rid === receiverIds[0] ? luckyMultiplier : null,
          }),
        );
        sends.push(send);
      }
      const primary = sends[0];

      await manager.save(
        manager.create(WalletTransaction, {
          userId: senderId,
          type: TransactionType.GIFT_SEND,
          currency: CurrencyType.COINS,
          amount: -totalCoins,
          balanceAfter: Number(wallet.coins) - luckyCoinsWon,
          referenceType: 'gift_send_all_mic',
          referenceId: primary.id,
          description: `All-mic ${qty}x ${gift.name} ×${personCount}`,
          metadata: {
            personCount,
            receiverIds,
            hostDiamonds,
            platformCut,
            diamondPool,
          },
        }),
      );
      if (luckyCoinsWon > 0 && luckyMultiplier) {
        await manager.save(
          manager.create(WalletTransaction, {
            userId: senderId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS,
            amount: luckyCoinsWon,
            balanceAfter: Number(wallet.coins),
            referenceType: 'lucky_gift',
            referenceId: primary.id,
            description: `Lucky ×${luckyMultiplier} on all-mic ${gift.name}`,
            metadata: {
              luckyMultiplier,
              giftId: gift.id,
              totalCoins,
              personCount,
            },
          }),
        );
      }

      // Credit each mic their share (self-target share stays with platform).
      // Agency rooms → agencyDiamonds; personal rooms → diamonds (never mixed).
      const roomAgencyId = room.agencyId || null;
      let selfMicReturned = 0;
      for (let i = 0; i < receiverIds.length; i++) {
        const rid = receiverIds[i];
        const share = micShares.get(rid) || 0;
        if (share <= 0) continue;
        if (rid === senderId) {
          selfMicReturned += share;
          continue;
        }
        let rw = await manager.findOne(Wallet, {
          where: { userId: rid },
          lock: { mode: 'pessimistic_write' },
        });
        if (!rw) rw = manager.create(Wallet, { userId: rid });
        let toAgencyPool = false;
        if (roomAgencyId) {
          const mem = await manager.findOne(AgencyMember, {
            where: {
              userId: rid,
              agencyId: roomAgencyId,
              isActive: true,
              status: AgencyMemberStatus.ACTIVE,
            },
          });
          toAgencyPool = !!mem;
        }
        if (toAgencyPool) {
          (rw as any).agencyDiamonds =
            Number((rw as any).agencyDiamonds || 0) + share;
        } else {
          rw.diamonds = Number(rw.diamonds) + share;
        }
        await manager.save(rw);
        await manager.save(
          manager.create(WalletTransaction, {
            userId: rid,
            type: TransactionType.GIFT_RECEIVE,
            currency: CurrencyType.DIAMONDS,
            amount: share,
            balanceAfter: toAgencyPool
              ? Number((rw as any).agencyDiamonds || 0)
              : Number(rw.diamonds),
            referenceType: toAgencyPool
              ? 'gift_receive_mic_agency'
              : 'gift_receive_mic',
            referenceId: sends[i].id,
            description: toAgencyPool
              ? `حصة مايك (روم وكالة) ${qty}x ${gift.name}`
              : `All-mic share ${qty}x ${gift.name}`,
            metadata: {
              allMic: true,
              personCount,
              platformCut,
              stream: toAgencyPool ? 'agency' : 'personal',
              ...(roomAgencyId ? { agencyId: roomAgencyId } : {}),
            },
          }),
        );
        await manager.increment(
          UserProfile,
          { userId: rid },
          'totalReceivedDiamonds',
          share,
        );
      }
      if (selfMicReturned > 0) {
        let rev = await manager.findOne(AppSetting, {
          where: { key: 'platform_gift_revenue_diamonds' },
        });
        if (!rev) {
          rev = manager.create(AppSetting, {
            key: 'platform_gift_revenue_diamonds',
            value: '0',
            description: 'Cumulative platform cut from gifts (diamonds)',
          });
        }
        rev.value = String(Number(rev.value || 0) + selfMicReturned);
        await manager.save(rev);
      }

      // Host half (on top of mic share if host is seated).
      if (hostDiamonds > 0) {
        let hw = await manager.findOne(Wallet, {
          where: { userId: roomHostId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!hw) hw = manager.create(Wallet, { userId: roomHostId });
        let hostToAgency = false;
        if (roomAgencyId) {
          const hostMem = await manager.findOne(AgencyMember, {
            where: {
              userId: roomHostId,
              agencyId: roomAgencyId,
              isActive: true,
              status: AgencyMemberStatus.ACTIVE,
            },
          });
          hostToAgency = !!hostMem;
        }
        if (hostToAgency) {
          (hw as any).agencyDiamonds =
            Number((hw as any).agencyDiamonds || 0) + hostDiamonds;
        } else {
          hw.diamonds = Number(hw.diamonds) + hostDiamonds;
        }
        await manager.save(hw);
        await manager.save(
          manager.create(WalletTransaction, {
            userId: roomHostId,
            type: TransactionType.GIFT_RECEIVE,
            currency: CurrencyType.DIAMONDS,
            amount: hostDiamonds,
            balanceAfter: hostToAgency
              ? Number((hw as any).agencyDiamonds || 0)
              : Number(hw.diamonds),
            referenceType: hostToAgency
              ? 'gift_receive_host_agency'
              : 'gift_receive_host',
            referenceId: primary.id,
            description: hostToAgency
              ? `حصة المضيف 50% (روم وكالة) ${gift.name}`
              : `All-mic host 50% from ${gift.name}`,
            metadata: {
              allMic: true,
              personCount,
              platformCut,
              micsPool,
              stream: hostToAgency ? 'agency' : 'personal',
              ...(roomAgencyId ? { agencyId: roomAgencyId } : {}),
            },
          }),
        );
        await manager.increment(
          UserProfile,
          { userId: roomHostId },
          'totalReceivedDiamonds',
          hostDiamonds,
        );
      }

      await manager.increment(
        UserProfile,
        { userId: senderId },
        'totalSentCoins',
        totalCoins,
      );

      const senderWalletOut = await manager.findOne(Wallet, {
        where: { userId: senderId },
      });

      return {
        send: primary,
        gift,
        comboCount,
        luckyMultiplier,
        luckyCoinsWon,
        coinPrice,
        quantity: qty,
        personCount,
        totalCoins,
        hostDiamonds,
        micsDiamonds: micsPool,
        platformCut,
        agentShare: 0,
        stream: roomAgencyId ? 'agency' : 'personal',
        senderBalance: Number(wallet.coins),
        success: true,
        coinsSpent: totalCoins,
        receiverIds,
        micShares: Object.fromEntries(micShares),
        breakdown: {
          coinsSpent: totalCoins,
          luckyReturn: luckyCoinsWon,
          luckyMultiplier: luckyMultiplier || 0,
          diamondsToHost: hostDiamonds,
          diamondsToMics: micsPool,
          diamondsPlatform: platformCut,
          diamondsAgency: 0,
          diamondPool,
          stream: roomAgencyId ? 'agency' : 'personal',
        },
        wallet: {
          coins: Number(wallet.coins),
          diamonds: Number(senderWalletOut?.diamonds || wallet.diamonds || 0),
          agencyDiamonds: Number(
            (senderWalletOut as any)?.agencyDiamonds ||
              (wallet as any).agencyDiamonds ||
              0,
          ),
          silverCoins: Number(wallet.silverCoins || 0),
          gamePoints: Number(wallet.gamePoints || 0),
        },
      };
    }).then(async (result) => {
      void this.tasksService
        .recordProgress(senderId, 'gift', 1, {
          roomId: dto.roomId,
          agencyId: room.agencyId || undefined,
        })
        .catch(() => undefined);
      void this.contestsService
        ?.addScore(senderId, totalCoins, 'gifts', {
          roomId: dto.roomId,
          agencyId: room.agencyId || undefined,
        })
        .catch(() => undefined);
      const perTargetCoins = coinPrice * qty;
      for (const rid of receiverIds) {
        void this.contestsService
          ?.addScore(rid, perTargetCoins, 'popular', {
            roomId: dto.roomId,
            agencyId: room.agencyId || undefined,
          })
          .catch(() => undefined);
      }

      const sender = await this.usersRepo.findOne({ where: { id: senderId } });
      const senderProfile = await this.profilesRepo.findOne({
        where: { userId: senderId },
      });
      const vip = await this.userVipRepo.findOne({
        where: {
          userId: senderId,
          isActive: true,
          expiresAt: MoreThan(new Date()),
        },
        order: { level: 'DESC' },
      });
      const vipLevel = effectiveVipLevel(
        Number(vip?.level || 0),
        Number(senderProfile?.totalSentCoins || 0),
      );
      const at = new Date().toISOString();
      const animationPayload = {
        giftId: gift.id,
        giftName: gift.name,
        giftType: gift.type,
        giftIconUrl: gift.iconUrl,
        iconUrl: gift.iconUrl,
        animationUrl:
          resolvePlayableGiftAnimation({
            giftName: gift.name,
            iconUrl: gift.iconUrl,
            animationUrl: gift.animationUrl,
          }) || gift.animationUrl,
        senderId,
        senderName: sender?.displayName || sender?.username || 'User',
        senderAvatarUrl: sender?.avatarUrl || null,
        senderHostBadgeUrl: senderProfile?.hostBadgeUrl || null,
        senderVipBadgeUrl: senderProfile?.vipBadgeUrl || null,
        senderVipLevel: vipLevel,
        senderUserLevel: Math.max(1, Number(sender?.level || 1)),
        allMic: true,
        receiverIds,
        receiverId: receiverIds[0],
        roomId: dto.roomId,
        quantity: qty,
        comboCount,
        personCount,
        totalCoins,
        diamondsAwarded: diamondPool,
        hostDiamonds: result.hostDiamonds,
        micsDiamonds: result.micsDiamonds,
        luckyMultiplier: result.luckyMultiplier || null,
        luckyCoinsWon: result.luckyCoinsWon || 0,
        at,
      };
      this.realtime?.emitToRoom(dto.roomId, 'room:event', {
        roomId: dto.roomId,
        event: 'gift:animation',
        payload: animationPayload,
        from: { userId: senderId },
        at,
      });
      this.realtime?.emitToRoom(dto.roomId, 'room:event', {
        roomId: dto.roomId,
        event: 'gift:send',
        payload: animationPayload,
        from: { userId: senderId },
        at,
      });
      if (result.luckyMultiplier && result.luckyCoinsWon > 0) {
        this.realtime?.emitToRoom(dto.roomId, 'room:event', {
          roomId: dto.roomId,
          event: 'lucky:hit',
          payload: {
            senderId,
            senderName: animationPayload.senderName,
            senderAvatarUrl: animationPayload.senderAvatarUrl,
            giftName: gift.name,
            giftIconUrl: gift.iconUrl,
            coinPrice: gift.coinPrice,
            quantity: qty,
            personCount,
            luckyMultiplier: result.luckyMultiplier,
            luckyCoinsWon: result.luckyCoinsWon,
            formula: `${totalCoins} × ${result.luckyMultiplier}`,
            allMic: true,
          },
          from: { userId: senderId },
          at,
        });
        this.broadcastLuckyCelebration({
          roomId: dto.roomId,
          senderId,
          displayName: animationPayload.senderName ?? undefined,
          avatarUrl: animationPayload.senderAvatarUrl ?? undefined,
          giftName: gift.name,
          giftIconUrl: gift.iconUrl,
          coinPrice: gift.coinPrice,
          quantity: qty * Math.max(1, personCount),
          luckyMultiplier: result.luckyMultiplier,
          luckyCoinsWon: result.luckyCoinsWon,
        });
      }
      this.realtime?.emitToUser(senderId, 'gift:animation', animationPayload);
      return result;
    });
  }

  /**
   * Global toast for OUR lucky gifts only (catalog lucky_* rolls) — not invented Mikoo packs.
   */
  private broadcastLuckyCelebration(opts: {
    roomId?: string;
    senderId: string;
    displayName?: string;
    avatarUrl?: string;
    giftName?: string;
    giftIconUrl?: string;
    coinPrice: number;
    quantity: number;
    luckyMultiplier: number;
    luckyCoinsWon: number;
  }) {
    const stake = Math.max(0, Math.floor(Number(opts.coinPrice) || 0))
      * Math.max(1, Math.floor(Number(opts.quantity) || 1));
    const mul = Number(opts.luckyMultiplier) || 0;
    const won = Math.max(0, Math.floor(Number(opts.luckyCoinsWon) || 0));
    // Broadcast wins that feel notable — 100+ stake with return, ×3+, or won ≥ 200.
    const notable = won > 0 && (stake >= 100 || mul >= 3 || won >= 200);
    if (!notable || !this.realtime) return;
    const name = (opts.displayName || 'لاعب').trim() || 'لاعب';
    const soft = mul > 0 && mul < 1;
    const times = Math.max(1, Math.round(mul));
    const giftLabel = (opts.giftName || '').trim();
    const title = soft ? 'مردود جزئي' : 'حظ سعيد!';
    const body = soft
      ? `${name} أرسل ${giftLabel || 'هدية حظ'} · مردود +${won}`
      : `${name} أرسل ${giftLabel || 'هدية حظ'} للفوز بـ ${won} عملة. (${times} مرة)`;
    this.realtime.emitToAll('celebration:toast', {
      kind: 'lucky_hit',
      id: `lucky:${opts.senderId}:${Date.now()}`,
      userId: opts.senderId,
      displayName: name,
      avatarUrl: opts.avatarUrl || '',
      giftName: giftLabel,
      giftIconUrl: opts.giftIconUrl || '',
      multiplier: mul,
      coinsWon: won,
      stake,
      roomId: opts.roomId || null,
      title,
      body,
      at: new Date().toISOString(),
    });
  }

  async history(userId: string, query: PaginationDto) {
    const [items, total] = await this.sendsRepo.findAndCount({
      where: [{ senderId: userId }, { receiverId: userId }],
      relations: ['gift', 'sender', 'receiver'],
      skip: query.skip,
      take: query.limit || 20,
      order: { createdAt: 'DESC' },
    });
    const normalized = items.map((send) => ({
      ...send,
      gift:
        send.gift ??
        (send.giftName
          ? {
              id: send.giftId,
              name: send.giftName,
              description: null,
              iconUrl: send.giftIconUrl,
              animationUrl: null,
              coinPrice: Math.max(0, Math.floor(send.totalCoins / Math.max(1, send.quantity))),
              diamondValue: 0,
              type: GiftType.NORMAL,
              isActive: false,
              sortOrder: 0,
            }
          : null),
    }));
    return paginate(normalized, total, query.page || 1, query.limit || 20);
  }
}
