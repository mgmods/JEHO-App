import 'reflect-metadata';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { DataSource } from 'typeorm';
import * as bcrypt from 'bcrypt';
import * as entities from './entities';
import { AdminRole } from './entities/admin-user.entity';
import { CosmeticType } from './entities/cosmetic.entity';
import { STANDARD_RECHARGE_PACKAGES } from '../common/pricing-catalog';

function loadEnvFile(filePath: string) {
  if (!existsSync(filePath)) return;
  const lines = readFileSync(filePath, 'utf8').split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (!(key in process.env)) process.env[key] = value;
  }
}

loadEnvFile(resolve(process.cwd(), '.env'));

const entityList = Object.values(entities).filter(
  (e) => typeof e === 'function',
) as Function[];

async function seed() {
  const adminEmail = process.env.ADMIN_EMAIL?.trim();
  const adminPassword = process.env.ADMIN_PASSWORD;
  const adminUsername = process.env.ADMIN_USERNAME?.trim();
  if (!adminEmail || !adminPassword || adminPassword.length < 16 || !adminUsername) {
    throw new Error('Set ADMIN_EMAIL, ADMIN_USERNAME, and a unique ADMIN_PASSWORD of at least 16 characters in backend/.env before seeding.');
  }
  const databaseUrl = process.env.DATABASE_URL?.trim();
  let parsedDatabaseUrl: URL | undefined;
  if (databaseUrl) {
    try {
      parsedDatabaseUrl = new URL(databaseUrl);
    } catch {
      throw new Error('DATABASE_URL must be a valid PostgreSQL connection URL.');
    }
  }
  const dbHost = parsedDatabaseUrl?.hostname || process.env.DB_HOST?.trim();
  const dbUsername =
    (parsedDatabaseUrl ? decodeURIComponent(parsedDatabaseUrl.username) : '') ||
    process.env.DB_USERNAME?.trim();
  const dbPassword =
    (parsedDatabaseUrl ? decodeURIComponent(parsedDatabaseUrl.password) : '') ||
    process.env.DB_PASSWORD;
  const dbDatabase =
    (parsedDatabaseUrl ? decodeURIComponent(parsedDatabaseUrl.pathname.replace(/^\//, '')) : '') ||
    process.env.DB_DATABASE?.trim();
  const dbPort = Number(parsedDatabaseUrl?.port || process.env.DB_PORT || 5432);
  if (!dbHost || !dbUsername || !dbPassword || !dbDatabase) {
    throw new Error('Set DATABASE_URL or DB_HOST, DB_USERNAME, DB_PASSWORD, and DB_DATABASE before seeding.');
  }

  const connectionOptions = {
    type: 'postgres' as const,
    ...(databaseUrl
      ? { url: databaseUrl }
      : {
          host: dbHost,
          port: dbPort,
          username: dbUsername,
          password: dbPassword,
          database: dbDatabase,
        }),
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
  };

  // Prepare only the dedicated JEHO-OWN schema; never create or sync tables in public.
  const bootstrap = new DataSource({
    ...connectionOptions,
    entities: [],
    synchronize: false,
  });
  await bootstrap.initialize();
  try {
    // TypeORM's PostgreSQL UUID columns use uuid_generate_v4() by default.
    // Supabase projects do not always have uuid-ossp enabled, so enable it
    // before schema synchronization. This is scoped to the current database
    // and does not touch any application tables outside JEHO-OWN.
    await bootstrap.query('CREATE EXTENSION IF NOT EXISTS "uuid-ossp"');
    await bootstrap.query('CREATE SCHEMA IF NOT EXISTS "jeho_own"');
  } finally {
    await bootstrap.destroy();
  }

  const ds = new DataSource({
    ...connectionOptions,
    schema: 'jeho_own',
    extra: { options: '-c search_path=jeho_own,extensions,public' },
    entities: entityList,
    synchronize: true,
  });

  await ds.initialize();
  console.log('Connected — seeding JEHO-OWN…');

  const vipRepo = ds.getRepository(entities.VipPlan);
  const cosmeticRepo = ds.getRepository(entities.Cosmetic);
  const adminRepo = ds.getRepository(entities.AdminUser);
  const userRepo = ds.getRepository(entities.User);
  const profileRepo = ds.getRepository(entities.UserProfile);
  const walletRepo = ds.getRepository(entities.Wallet);
  const settingsRepo = ds.getRepository(entities.AppSetting);
  const vipPriceForLevel = (level: number): number => {
    const l = Math.max(1, Math.min(100, Math.floor(Number(level) || 1)));
    if (l <= 10) return [0, 299, 499, 799, 1199, 1799, 2499, 3499, 4999, 6999, 9999][l];
    if (l <= 20) return Math.round(9_999 + Math.pow(l - 10, 1.18) * 2_100);
    if (l <= 40) return Math.round(38_000 + Math.pow(l - 20, 1.24) * 5_600);
    if (l <= 60) return Math.round(150_000 + Math.pow(l - 40, 1.28) * 11_500);
    if (l <= 80) return Math.round(430_000 + Math.pow(l - 60, 1.32) * 21_000);
    return Math.round(1_000_000 + Math.pow(l - 80, 1.36) * 48_000);
  };

  const vipBenefits = (level: number) => ({
    entryEffect: level >= 1,
    antiKick: level >= 3,
    antiMute: level >= 4,
    hostBadge: level >= 2,
    exclusiveGifts: level >= 5,
    flyingComment: level >= 6,
    roomPriority: level,
    dmLimit: 50 + level * 20,
    extra: [
      level >= 7 ? 'animated_avatar' : null,
      level >= 8 ? 'exclusive_emotes' : null,
      level >= 9 ? 'vip_lounge' : null,
      level >= 10 ? 'name_color_custom' : null,
      level >= 25 ? 'priority_join_queue' : null,
      level >= 50 ? 'advanced_room_theme_unlock' : null,
      level >= 75 ? 'supporter_spotlight' : null,
      level >= 100 ? 'vip_master_100' : null,
    ].filter(Boolean) as string[],
  });

  for (let level = 1; level <= 100; level++) {
    const price = vipPriceForLevel(level);
    const badgeTier = Math.min(10, Math.max(1, Math.ceil(level / 10)));
    const existing = await vipRepo.findOne({ where: { level } });
    if (!existing) {
      await vipRepo.save(
        vipRepo.create({
          level,
          name: `VIP${level}`,
          coinPriceMonthly: price,
          badgeUrl: `/assets/pack/vip_medal_${badgeTier}.png?v=20260717c`,
          benefits: vipBenefits(level),
          isActive: true,
        }),
      );
      console.log(`  VIP${level} created`);
    } else {
      existing.coinPriceMonthly = price;
      existing.badgeUrl = existing.badgeUrl || `/assets/pack/vip_medal_${badgeTier}.png?v=20260717c`;
      existing.isActive = true;
      await vipRepo.save(existing);
      console.log(`  VIP${level} price updated → ${price}`);
    }
  }

  // Gifts, join toasts, entry effects, and host signals are provisioned by
  // migrate-visual-system.js from the canonical /public/visual-system runtime.
  // Avatar and mic frames were intentionally retired.
  // Room cards: only Mikoo rank borders (mall catalog). Legacy kenar/cover cards removed.
  let sort = 1;
  const mikooBorders = [
    { code: 'room_mikoo_border_top1', name: 'إطار الروم · المركز 1', file: 'bg_room_border_top1.webp', price: 299 },
    { code: 'room_mikoo_border_top2', name: 'إطار الروم · المركز 2', file: 'bg_room_border_top2.webp', price: 249 },
    { code: 'room_mikoo_border_top3', name: 'إطار الروم · المركز 3', file: 'bg_room_border_top3.webp', price: 199 },
    { code: 'room_mikoo_border_top4', name: 'إطار الروم · المركز 4', file: 'bg_room_border_top4.webp', price: 179 },
    { code: 'room_mikoo_border_top5', name: 'إطار الروم · المركز 5', file: 'bg_room_border_top5.webp', price: 159 },
    { code: 'room_mikoo_border_top6', name: 'إطار الروم · المركز 6', file: 'bg_room_border_top6.webp', price: 139 },
    { code: 'room_mikoo_border_top7', name: 'إطار الروم · المركز 7', file: 'bg_room_border_top7.webp', price: 119 },
  ];
  for (const b of mikooBorders) {
    const existing = await cosmeticRepo.findOne({ where: { code: b.code } });
    const payload = {
      type: CosmeticType.ROOM_CARD,
      code: b.code,
      name: b.name,
      description: 'إطار يظهر حول بطاقة الغرفة في القائمة وداخل الروم',
      previewUrl: `/assets/rooms/mikoo/${b.file}?v=20260730r`,
      animationUrl: null,
      coinPrice: b.price,
      minVipLevel: 0,
      isActive: true,
      sortOrder: sort++,
      meta: { forRooms: true, source: 'mikoo_border', kind: 'room_card' },
    };
    if (!existing) {
      await cosmeticRepo.save(cosmeticRepo.create(payload as any));
      console.log(`  Room card ${b.code} created`);
    } else {
      Object.assign(existing, payload);
      await cosmeticRepo.save(existing);
    }
  }

  // Full-screen in-room wallpapers (NOT list cover / room_card).
  // All paid in coins (mall ladder syncs prices on boot).
  const roomBackgrounds: Array<{ code: string; name: string; previewUrl: string; coinPrice: number }> = [
    { code: 'bg_voice_default', name: 'خلفية صوتية', previewUrl: '/assets/backgrounds/voice-room-bg.png?v=20260728', coinPrice: 99 },
    { code: 'bg_aurora_night', name: 'أورورا ليلية', previewUrl: '/assets/backgrounds/bg_aurora_night.png?v=20260728', coinPrice: 149 },
    { code: 'bg_ocean_deep', name: 'أعماق المحيط', previewUrl: '/assets/backgrounds/bg_ocean_deep.png?v=20260728', coinPrice: 199 },
    { code: 'bg_mint_dream', name: 'حلم النعناع', previewUrl: '/assets/backgrounds/bg_mint_dream.png?v=20260728', coinPrice: 249 },
    { code: 'bg_sunset_lounge', name: 'غروب الصالة', previewUrl: '/assets/backgrounds/bg_sunset_lounge.png?v=20260728', coinPrice: 299 },
    { code: 'bg_violet_stage', name: 'منصة بنفسجية', previewUrl: '/assets/backgrounds/bg_violet_stage.png?v=20260728', coinPrice: 399 },
    { code: 'bg_golden_party', name: 'حفلة ذهبية', previewUrl: '/assets/backgrounds/bg_golden_party.png?v=20260728', coinPrice: 499 },
    { code: 'bg_neon_city', name: 'مدينة نيون', previewUrl: '/assets/backgrounds/bg_neon_city.png?v=20260728', coinPrice: 599 },
    { code: 'bg_rose_velvet', name: 'مخمل وردي', previewUrl: '/assets/backgrounds/bg_rose_velvet.png?v=20260728', coinPrice: 799 },
    { code: 'bg_ice_crystal', name: 'بلورة ثلج', previewUrl: '/assets/backgrounds/bg_ice_crystal.png?v=20260728', coinPrice: 999 },
    { code: 'bg_emerald_club', name: 'نادي الزمرد', previewUrl: '/assets/backgrounds/bg_emerald_club.png?v=20260728', coinPrice: 1299 },
    { code: 'bg_cosmic_dust', name: 'غبار كوني', previewUrl: '/assets/backgrounds/bg_cosmic_dust.png?v=20260728', coinPrice: 1499 },
    { code: 'bg_amber_glow', name: 'توهج كهرماني', previewUrl: '/assets/backgrounds/bg_amber_glow.png?v=20260728', coinPrice: 1799 },
    { code: 'bg_sapphire_hall', name: 'قاعة ياقوت', previewUrl: '/assets/backgrounds/bg_sapphire_hall.png?v=20260728', coinPrice: 1999 },
    { code: 'bg_cherry_night', name: 'ليلة الكرز', previewUrl: '/assets/backgrounds/bg_cherry_night.png?v=20260728', coinPrice: 1799 },
    { code: 'bg_royal_indigo', name: 'نيلي ملكي', previewUrl: '/assets/backgrounds/bg_royal_indigo.png?v=20260728', coinPrice: 1999 },
  ];
  for (const bg of roomBackgrounds) {
    const existing = await cosmeticRepo.findOne({ where: { code: bg.code } });
    const payload = {
      type: CosmeticType.ROOM_BACKGROUND,
      code: bg.code,
      name: bg.name,
      description: 'خلفية غرفة صوت تظهر للجميع داخل الروم',
      previewUrl: bg.previewUrl,
      animationUrl: null,
      coinPrice: bg.coinPrice,
      minVipLevel: 0,
      isActive: true,
      sortOrder: sort++,
      meta: { roomWallpaper: true },
    };
    if (!existing) {
      await cosmeticRepo.save(cosmeticRepo.create(payload as any));
      console.log(`  Room background ${bg.code} created`);
    } else {
      Object.assign(existing, payload);
      await cosmeticRepo.save(existing);
    }
  }

  for (let lvl = 1; lvl <= 10; lvl++) {
    const code = `vip${lvl}`;
    const existing = await cosmeticRepo.findOne({ where: { code } });
    if (!existing) {
      await cosmeticRepo.save(
        cosmeticRepo.create({
          type: CosmeticType.VIP_BADGE,
          code,
          name: `VIP${lvl} Badge`,
          previewUrl: `/assets/pack/vip_medal_${lvl}.png?v=20260717c`,
          coinPrice: 0,
          minVipLevel: lvl,
          isActive: true,
          sortOrder: sort++,
        } as any),
      );
    } else {
      existing.previewUrl = `/assets/pack/vip_medal_${lvl}.png?v=20260717c`;
      existing.name = `VIP${lvl} Badge`;
      existing.isActive = true;
      await cosmeticRepo.save(existing);
    }
  }

  const LEVEL_PREVIEWS = [
    'pack/ic_glamour_up_180_199.png',
    'pack/ic_glamour_up_200_219.png',
    'pack/ic_glamour_up_220_239.png',
    'pack/ic_glamour_up_240_259.png',
    'pack/ic_glamour_up_260_279.png',
    'pack/ic_glamour_up_280_299.png',
    'pack/ic_glamour_up_300_319.png',
    'pack/ic_glamour_up_320_339.png',
    'pack/ic_glamour_up_340_359.png',
    'pack/ic_glamour_up_360_379.png',
    'pack/ic_glamour_up_380_399.png',
    'pack/ic_badge_lv1.png',
    'pack/ic_badge_lv2.png',
    'pack/ic_badge_lv3.png',
    'pack/ic_badge_lv4.png',
    'pack/ic_profile_svip_1.png',
    'pack/ic_profile_svip_2.png',
    'pack/ic_profile_svip_3.png',
    'pack/ic_profile_svip_4.png',
    'pack/ic_profile_svip_5.png',
  ];
  for (let lvl = 1; lvl <= 20; lvl++) {
    const code = `level_${lvl}`;
    const preview = `/assets/${LEVEL_PREVIEWS[lvl - 1]}?v=20260717c`;
    const existing = await cosmeticRepo.findOne({ where: { code } });
    if (!existing) {
      await cosmeticRepo.save(
        cosmeticRepo.create({
          type: CosmeticType.LEVEL_BADGE,
          code,
          name: `Level ${lvl}`,
          previewUrl: preview,
          coinPrice: 0,
          minUserLevel: lvl,
          isActive: true,
          sortOrder: sort++,
        } as any),
      );
    } else {
      existing.previewUrl = preview;
      existing.name = `Level ${lvl}`;
      existing.isActive = true;
      await cosmeticRepo.save(existing);
    }
  }

  for (const [code, name, url, price] of [
    ['badge_host', 'شارة المضيف', '/assets/pack/guardian_relation_cp_8_10.png', 800],
    ['badge_agency', 'شارة الوكالة', '/assets/pack/ff_challenge_frame_top1.png', 1200],
    ['contest_weekly', 'شارة المسابقة', '/assets/pack/bg_main_activity_center.png', 1500],
  ] as const) {
    const existing = await cosmeticRepo.findOne({ where: { code } });
    const previewUrl = `${url}?v=20260717c`;
    if (!existing) {
      await cosmeticRepo.save(
        cosmeticRepo.create({
          type: CosmeticType.HOST_BADGE,
          code,
          name,
          previewUrl,
          coinPrice: price,
          isActive: true,
          sortOrder: sort++,
        } as any),
      );
    } else {
      // Keep dashboard coinPrice; seed only refreshes assets/name for existing rows.
      existing.previewUrl = previewUrl;
      existing.name = name;
      existing.isActive = true;
      await cosmeticRepo.save(existing);
    }
  }

  // mark end of cosmetics — continue admin seed
  const _cosmeticsDone = true;
  void _cosmeticsDone;

  let adminUser = await userRepo.findOne({ where: { email: adminEmail } });
  if (!adminUser) {
    const passwordHash = await bcrypt.hash(adminPassword, 12);
    adminUser = await userRepo.save(
      userRepo.create({
        email: adminEmail,
        username: adminUsername,
        passwordHash,
        displayName: 'JEHO-OWN Admin',
        isAdmin: true,
        emailVerified: true,
        level: 50,
      }),
    );
    await profileRepo.save(profileRepo.create({ userId: adminUser.id }));
    await walletRepo.save(
      walletRepo.create({ userId: adminUser.id, coins: 100000, diamonds: 100000 }),
    );
    console.log(`  Admin user ${adminEmail} created`);
  } else if (!adminUser.isAdmin) {
    adminUser.isAdmin = true;
    await userRepo.save(adminUser);
  }

  const adminRecord = await adminRepo.findOne({ where: { email: adminEmail } });
  if (!adminRecord) {
    await adminRepo.save(
      adminRepo.create({
        email: adminEmail,
        username: adminUsername,
        passwordHash: await bcrypt.hash(adminPassword, 12),
        role: AdminRole.SUPER,
        linkedUserId: adminUser.id,
        isActive: true,
      }),
    );
    console.log('  AdminUser record created');
  }

  const defaults: Array<[string, string, string]> = [
    ['brand_name', 'JEHO-OWN', 'Product brand name'],
    ['coin_to_diamond_rate', '0.35', 'Legacy note: gift diamond capped at coin×0.35; actual mint is gift.diamondValue'],
    ['diamond_to_fiat', '0.00005', 'Withdraw conversion USD per diamond'],
    ['min_withdraw_diamonds', '10000', 'Minimum withdraw amount'],
    ['economy.minWithdrawDiamonds', '10000', 'Minimum withdraw amount (runtime)'],
    ['economy.diamondUsdRate', '0.00005', 'Withdraw USD per diamond (runtime)'],
    ['maintenance_mode', 'false', 'Global maintenance flag'],
    [
      'recharge_packages',
      JSON.stringify(STANDARD_RECHARGE_PACKAGES),
      'In-app coin recharge packages (Play productId = sku)',
    ],
    ['pricing.version', '20260723economy-safe-v4', 'Canonical pricing catalog version'],
  ];
  for (const [key, value, description] of defaults) {
    const s = await settingsRepo.findOne({ where: { key } });
    if (!s) {
      await settingsRepo.save(settingsRepo.create({ key, value, description }));
    }
  }

  await ds.destroy();
  console.log('JEHO-OWN seed complete.');
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});
