import { Controller, Get, Req } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { InjectRepository } from '@nestjs/typeorm';
import { Request } from 'express';
import { Repository } from 'typeorm';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Public } from '../../common/decorators';
import { ZegoSettingsService } from '../zego/zego-settings.service';
import { LiveKitSettingsService } from '../livekit/livekit-settings.service';
import { ContentModerationService } from '../moderation/content-moderation.service';

@ApiTags('Config')
@Controller('config')
export class ConfigController {
  constructor(
    @InjectRepository(AppSetting)
    private readonly settingsRepo: Repository<AppSetting>,
    private readonly zegoSettingsService: ZegoSettingsService,
    private readonly liveKitSettingsService: LiveKitSettingsService,
    private readonly moderation: ContentModerationService,
  ) {}

  @Public()
  @Get('zego')
  @ApiOperation({ summary: 'Public ZEGOCLOUD client config (AppID only)' })
  getZego() {
    return this.zegoSettingsService.getPublicClientConfig();
  }

  @Public()
  @Get('voice-rtc')
  @ApiOperation({
    summary: 'Active voice RTC provider (zego | livekit) + public LiveKit URL',
  })
  getVoiceRtc() {
    return this.liveKitSettingsService.getPublicClientConfig();
  }

  @Public()
  @Get('banners')
  @ApiOperation({ summary: 'Home promo banners for the app' })
  async banners(@Req() req: Request) {
    const defaults = [
      {
        title: 'حفلات JEHO CHAT',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_party_ar.png',
        link: 'party',
        lang: 'ar',
      },
      {
        title: 'اربح الكوينز',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_wallet_ar.png',
        link: 'wallet',
        lang: 'ar',
      },
      {
        title: 'دراما قصيرة',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_drama_ar.png',
        link: 'drama',
        lang: 'ar',
      },
      {
        title: 'JEHO CHAT Parties',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_party_en.png',
        link: 'party',
        lang: 'en',
      },
      {
        title: 'Earn Coins',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_wallet_en.png',
        link: 'wallet',
        lang: 'en',
      },
      {
        title: 'Short Drama',
        imageUrl: 'https://api.adnova.bbs.tr/banners/banner_jeho_drama_en.png',
        link: 'drama',
        lang: 'en',
      },
    ];
    const row = await this.settingsRepo.findOne({ where: { key: 'home_banners' } });
    let list = defaults;
    if (row?.value) {
      try {
        const parsed = JSON.parse(row.value);
        if (Array.isArray(parsed) && parsed.length) list = parsed;
      } catch {
        /* keep defaults */
      }
    }

    const q = ((req.query?.lang as string) || '').trim().toLowerCase();
    if (!q) {
      // Return all locale variants; the app filters by device language.
      return list.slice(0, 12);
    }
    const lang = q.startsWith('ar') ? 'ar' : 'en';
    const filtered = list.filter((b: { lang?: string }) => {
      const bl = (b.lang || 'all').toLowerCase();
      return bl === 'all' || bl === lang || !b.lang;
    });
    return (filtered.length ? filtered : list).slice(0, 8);
  }

  @Public()
  @Get('features')
  @ApiOperation({ summary: 'App feature flags for clients' })
  async features() {
    const keys = [
      'features.female_only_voice_hosts',
      'gender_verification.auto_accept',
      'rooms.mic_without_host_approval',
      'gifts.sound_enabled',
      'tasks.enabled',
    ];
    const rows = await this.settingsRepo
      .createQueryBuilder('s')
      .where('s.key IN (:...keys)', { keys })
      .getMany();
    const map = new Map(rows.map((r) => [r.key, r.value]));
    const bool = (key: string, fallback: boolean) => {
      const raw = map.get(key);
      if (raw === undefined || raw === null || raw === '') return fallback;
      const s = String(raw).trim().toLowerCase();
      return s === '1' || s === 'true' || s === 'yes' || s === 'on';
    };
    return {
      // Default OFF: no female identity gate unless admin enables it.
      femaleOnlyVoiceHosts: bool('features.female_only_voice_hosts', false),
      genderVerificationAutoAccept: bool('gender_verification.auto_accept', false),
      // Default ON: guests can take a mic seat without host approval.
      micWithoutHostApproval: bool('rooms.mic_without_host_approval', true),
      roomRulesText: await this.readSetting(
        'rooms.rules_text',
        'احترموا القوانين واستمتعوا بالجلسة',
      ),
      // Default ON: gift WAV / SFX play unless admin mutes globally.
      giftSoundsEnabled: bool('gifts.sound_enabled', true),
      // Default ON: hide full tasks product when admin turns it off.
      tasksEnabled: bool('tasks.enabled', true),
      // Bump via app settings (or redeploy MIKOO_COVERS_CACHE_TAG) when static art is replaced.
      mediaAssetEpoch: await this.readSetting(
        'app.media_asset_epoch',
        (await import('../games/mikoo-games.catalog')).MIKOO_COVERS_CACHE_TAG,
      ),
      ...(await this.moderation.clientPolicy()),
    };
  }

  private async readSetting(key: string, fallback: string) {
    const row = await this.settingsRepo.findOne({ where: { key } });
    const v = row?.value?.trim();
    return v || fallback;
  }

  @Public()
  @Get('app-update')
  @ApiOperation({ summary: 'Store update prompt config for Android clients' })
  async appUpdate() {
    const keys = [
      'app.latest_version_code',
      'app.latest_version_name',
      'app.min_version_code',
      'app.update_title_ar',
      'app.update_message_ar',
      'app.update_title_en',
      'app.update_message_en',
      'app.update_store_url',
      'app.update_force',
    ];
    const rows = await this.settingsRepo
      .createQueryBuilder('s')
      .where('s.key IN (:...keys)', { keys })
      .getMany();
    const map = new Map(rows.map((r) => [r.key, r.value]));
    const num = (k: string, fallback: number) => {
      const n = Number(map.get(k));
      return Number.isFinite(n) ? Math.floor(n) : fallback;
    };
    const latestCode = num('app.latest_version_code', 0);
    const minCode = num('app.min_version_code', 0);
    return {
      latestVersionCode: latestCode,
      latestVersionName: map.get('app.latest_version_name') || '',
      minVersionCode: minCode,
      forceUpdate: map.get('app.update_force') === 'true',
      titleAr: map.get('app.update_title_ar') || 'تحديث جديد متوفر',
      messageAr:
        map.get('app.update_message_ar') ||
        'يتوفر إصدار أحدث من JEHO CHAT على المتجر. حدّث الآن للاستمتاع بالتحسينات والإصلاحات.',
      titleEn: map.get('app.update_title_en') || 'Update available',
      messageEn:
        map.get('app.update_message_en') ||
        'A newer version of JEHO CHAT is available on the store. Update now for improvements and fixes.',
      storeUrl:
        map.get('app.update_store_url') ||
        'https://play.google.com/store/apps/details?id=com.Dramizo.Series',
    };
  }

  @Public()
  @Get('offers')
  @ApiOperation({ summary: 'Limited-time coin offers shown in the gift-box FAB' })
  async offers() {
    const fabRow = await this.settingsRepo.findOne({ where: { key: 'offers_fab_visible' } });
    // Hidden when explicitly set to "false"; missing key = visible.
    if (fabRow?.value === 'false') return [];

    const defaults = [
      {
        id: 'offer_start',
        title: 'عرض البداية',
        subtitle: '+3,000 بونص',
        sku: 'coins_70000',
        coins: 70000,
        bonusCoins: 3000,
        priceUsd: 4.99,
        lottieUrl: 'https://api.adnova.bbs.tr/assets/lottie/gift_burst.json',
        imageUrl: '',
        popular: true,
        active: true,
      },
      {
        id: 'offer_star',
        title: 'عرض النجم',
        subtitle: '+8,000 بونص',
        sku: 'coins_141700',
        coins: 141700,
        bonusCoins: 8000,
        priceUsd: 9.99,
        lottieUrl: 'https://api.adnova.bbs.tr/assets/lottie/gift_crown.json',
        imageUrl: '',
        popular: false,
        active: true,
      },
    ];
    const row = await this.settingsRepo.findOne({ where: { key: 'store_offers' } });
    if (!row?.value) return defaults.filter((o) => o.active !== false);
    try {
      const parsed = JSON.parse(row.value);
      if (!Array.isArray(parsed)) return defaults.filter((o) => o.active !== false);
      // Empty array means admin cleared offers — do not fall back to defaults.
      return parsed.filter((o) => o && o.active !== false);
    } catch {
      return defaults.filter((o) => o.active !== false);
    }
  }

  @Public()
  @Get('nav-icons')
  @ApiOperation({
    summary: 'Bottom navigation tab icons (PNG/JPG) managed from dashboard',
  })
  async navIcons() {
    const {
      emptyNavIconsConfig,
      sanitizeNavIconsConfig,
      navIconsClientPayload,
    } = await import('./nav-icons.util');
    const row = await this.settingsRepo.findOne({ where: { key: 'app_nav_icons' } });
    let cfg = emptyNavIconsConfig();
    if (row?.value) {
      try {
        cfg = sanitizeNavIconsConfig(JSON.parse(row.value));
      } catch {
        cfg = sanitizeNavIconsConfig(row.value);
      }
    }
    return navIconsClientPayload(cfg);
  }

  @Public()
  @Get('seat-stickers')
  @ApiOperation({
    summary: 'Seat mic reaction stickers (GIF/WebP) managed from dashboard',
  })
  async seatStickers() {
    const {
      defaultSeatStickersConfig,
      sanitizeSeatStickersConfig,
      seatStickersClientPayload,
      rememberSeatStickersCache,
    } = await import('./seat-stickers.util');
    const row = await this.settingsRepo.findOne({ where: { key: 'seat_stickers' } });
    let cfg = defaultSeatStickersConfig();
    if (row?.value) {
      try {
        cfg = sanitizeSeatStickersConfig(JSON.parse(row.value));
      } catch {
        cfg = sanitizeSeatStickersConfig(row.value);
      }
    } else {
      // First boot: persist defaults so dashboard can edit immediately.
      try {
        const created = this.settingsRepo.create({
          key: 'seat_stickers',
          value: JSON.stringify(cfg),
          description: 'Seat mic reaction stickers catalog',
        });
        await this.settingsRepo.save(created);
      } catch {
        // non-fatal
      }
    }
    rememberSeatStickersCache(cfg);
    return seatStickersClientPayload(cfg);
  }

  @Public()
  @Get('games')
  @ApiOperation({ summary: 'Playable games catalog for the app (managed from dashboard)' })
  async games() {
    const {
      buildDefaultGamesCatalog,
      sanitizeGamesCatalog,
      isRemovedLegacyHtmlGame,
    } = await import('../games/mikoo-games.catalog');
    const defaults = buildDefaultGamesCatalog();
    const row = await this.settingsRepo.findOne({ where: { key: 'app_games' } });
    let raw: unknown = defaults;
    if (row?.value) {
      try {
        const parsed = JSON.parse(row.value);
        raw = Array.isArray(parsed) && parsed.length ? parsed : defaults;
      } catch {
        raw = defaults;
      }
    }

    // Optional: re-persist merged catalog (new games + int sortOrder) without blocking clients.
    if (row) {
      try {
        const cleaned = sanitizeGamesCatalog(raw);
        const next = JSON.stringify(cleaned);
        if (row.value !== next) {
          row.value = next;
          await this.settingsRepo.save(row);
        }
        raw = cleaned;
      } catch {
        // Redis / DB write issues must not break the public catalog response.
        raw = sanitizeGamesCatalog(raw);
      }
    }

    return sanitizeGamesCatalog(raw, { forClient: true });
  }

  @Public()
  @Get('theme')
  @ApiOperation({ summary: 'Remote app theme/assets managed from server settings' })
  async theme() {
    const row = await this.settingsRepo.findOne({ where: { key: 'app_theme' } });
    const defaults = {
      version: 1,
      updatedAt: new Date().toISOString(),
      brand: {
        appName: 'JEHO CHAT',
        logoUrl: '',
        splashUrl: '',
        faviconUrl: '',
      },
      colors: {
        primary: '#FE2C55',
        secondary: '#22D3EE',
        background: '#FFFFFF',
        surface: '#FFFFFF',
        onPrimary: '#FFFFFF',
        danger: '#F0435B',
        gold: '#F5C542',
      },
      backgrounds: {},
      assets: {},
      splash: { enabled: true, skipEnabled: true, items: [] },
    };
    if (!row?.value) return defaults;
    try {
      const parsed = JSON.parse(row.value);
      if (!parsed || typeof parsed !== 'object') return defaults;
      const version = Number(parsed.version) || defaults.version;
      const updatedAt = parsed.updatedAt || new Date().toISOString();
      const cacheBust = encodeURIComponent(String(version) + '-' + String(updatedAt));
      const bust = (value: unknown) => {
        const url = String(value || '').trim();
        const isHttpUrl = /^https?:\/\//i.test(url);\n        const isRelativeUrl = url.startsWith('/');\n        if (!url || (!isHttpUrl && !isRelativeUrl)) return value;
        return url + (url.includes('?') ? '&' : '?') + 'v=' + cacheBust;
      };
      const next = {
        ...defaults,
        ...parsed,
        version,
        updatedAt,
      };
      if (next.brand?.splashUrl) {
        next.brand = { ...next.brand, splashUrl: bust(next.brand.splashUrl) };
      }
      if (next.splash?.items && Array.isArray(next.splash.items)) {
        next.splash = {
          ...next.splash,
          items: next.splash.items.map((item: any) => ({
            ...item,
            url: bust(item?.url),
          })),
        };
      }
      return next;
    } catch {
      return defaults;
    }
  }

  @Public()
  @Get('support')
  @ApiOperation({ summary: 'Customer support channels for the app (managed from dashboard)' })
  async support() {
    const pick = async (key: string, fallback = '') => {
      const row = await this.settingsRepo.findOne({ where: { key } });
      const v = row?.value != null ? String(row.value).trim() : '';
      return v || fallback;
    };
    return {
      whatsapp: await pick('support_whatsapp'),
      telegram: await pick('support_telegram'),
      instagram: await pick('support_instagram'),
      email: await pick('supportEmail', await pick('support_email', 'support@adnova.bbs.tr')),
      phone: await pick('support_phone'),
    };
  }

  @Public()
  @Get('sham-cash')
  @ApiOperation({
    summary: 'Sham Cash config — account QR + WhatsApp proof (Coming soon if no account id)',
  })
  async shamCash() {
    const row = await this.settingsRepo.findOne({ where: { key: 'sham_cash_config' } });
    let cur: Record<string, any> = {};
    try {
      const raw = row?.value as any;
      cur = typeof raw === 'string' ? JSON.parse(raw || '{}') : { ...(raw || {}) };
    } catch {
      cur = {};
    }
    const accountId = String(cur.accountId || cur.iban || cur.accountNumber || '')
      .trim()
      .toLowerCase();
    const whatsapp = String(cur.whatsapp || '').trim();
    const digits = whatsapp.replace(/\D/g, '');
    const hasAccount = accountId.length >= 8;
    const displayName = String(cur.displayName || 'شام كاش').trim() || 'شام كاش';
    const accountName = String(cur.accountName || '').trim() || displayName;
    return {
      enabled: hasAccount,
      whatsapp: digits.length >= 8 ? whatsapp : '',
      displayName,
      accountName,
      accountId: hasAccount ? accountId : '',
      instructions: hasAccount
        ? String(cur.instructions || '').trim() ||
          'حوّل عبر شام كاش بالمسح أو الرقم، ثم أرسل صورة الإثبات على واتساب.'
        : '',
    };
  }
}
