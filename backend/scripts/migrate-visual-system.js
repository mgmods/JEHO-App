require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT || 5432),
  user: process.env.DB_USER || process.env.DB_USERNAME || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  database: process.env.DB_NAME || process.env.DB_DATABASE || 'auralive',
  ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
});

const hostNames = [
  'الطليعة', 'الحارس', 'الصقر', 'الزمرد', 'العاصفة', 'الشبح', 'الشمس',
  'التنين', 'السماوي', 'الخالد', 'الكوبرا', 'الأسد', 'النسر', 'الذئب',
  'العنقاء', 'العقرب', 'الكراكن', 'الكبش', 'الغراب', 'الإمبراطور',
  'التنين الإمبراطوري', 'النمر الأبيض', 'ملك الجليد', 'الميغالودون',
  'المينوتور', 'الأرملة السوداء', 'سيد الرعد', 'الشوغن', 'ملك الظلام',
  'الحاكم الكوني',
];

const entryCatalog = [
  { id: 'lion', name: 'الأسد الملكي' },
  { id: 'dragon', name: 'التنين' },
  { id: 'falcon', name: 'الصقر' },
  { id: 'tiger', name: 'النمر' },
  { id: 'wolf', name: 'الذئب' },
  { id: 'phoenix', name: 'العنقاء' },
  { id: 'crown', name: 'التاج' },
  { id: 'galaxy', name: 'المجرة' },
  { id: 'neon', name: 'نيون' },
  { id: 'fire', name: 'اللهب' },
  { id: 'ice', name: 'الجليد' },
  { id: 'legend', name: 'أسطوري' },
];

const joinCatalog = [
  { id: 'vip-gold', name: 'ذهبي VIP' },
  { id: 'diamond', name: 'الماس' },
  { id: 'fire-dragon', name: 'تنين النار' },
  { id: 'ice-wolf', name: 'ذئب الجليد' },
  { id: 'neon-cyber', name: 'سايبر نيون' },
  { id: 'royal-crown', name: 'التاج الملكي' },
  { id: 'angel', name: 'الملاك' },
  { id: 'phoenix', name: 'العنقاء' },
];

function giftPrice(item) {
  const base = { basic: 10, premium: 100, legendary: 1000, mythic: 5000 }[item.tier] || 10;
  const step = { basic: 5, premium: 20, legendary: 100, mythic: 250 }[item.tier] || 5;
  return base + item.order * step;
}

async function insertCosmetic(type, code, name, previewUrl, order, price, meta) {
  await client.query(
    `INSERT INTO cosmetics
      (id, type, code, name, description, "previewUrl", "animationUrl",
       "coinPrice", "minVipLevel", "minUserLevel", "isActive", "sortOrder",
       meta, "createdAt", "updatedAt")
     VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7, 0, 0, true, $8, $9, NOW(), NOW())
     ON CONFLICT (code) DO UPDATE SET
       type = EXCLUDED.type,
       name = EXCLUDED.name, "previewUrl" = EXCLUDED."previewUrl",
       "animationUrl" = EXCLUDED."animationUrl", "coinPrice" = EXCLUDED."coinPrice",
       "sortOrder" = EXCLUDED."sortOrder", meta = EXCLUDED.meta,
       "isActive" = true, "updatedAt" = NOW()`,
    [
      type, code, name, 'مؤثر Host signals متطابق محلياً وعلى السيرفر',
      previewUrl, '/visual-system/runtime.html', price, order,
      JSON.stringify({ engine: 'host-signals', ...meta }),
    ],
  );
}

async function main() {
  const root = path.join(__dirname, '..', 'public', 'visual-system');
  const catalogPath = path.join(root, 'gifts', 'catalog.json');
  const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
  await client.connect();
  await client.query('BEGIN');
  try {
    await client.query(`
      ALTER TABLE cosmetics
        ALTER COLUMN type TYPE varchar(32) USING type::text;

      -- Wipe ONLY legacy Host-signals rows. Preserve Mikoo / mall cosmetics.
      DELETE FROM cosmetics
       WHERE code LIKE 'host_signal_%'
          OR code LIKE 'entry_signal_%'
          OR code LIKE 'join_signal_%'
          OR code LIKE 'room_frame_%'
          OR code LIKE 'room_kenar_%'
          OR code LIKE 'room_pro_%'
          OR code LIKE 'card_vip_%'
          OR (
               "previewUrl" LIKE '/visual-system/%'
           AND code NOT LIKE '%mikoo%'
           AND type::text IN ('entry_effect', 'join_toast', 'host_badge', 'room_card')
          );

      DROP TYPE IF EXISTS cosmetics_type_enum;
      CREATE TYPE cosmetics_type_enum AS ENUM (
        'entry_effect',
        'join_toast',
        'room_card',
        'room_background',
        'level_badge',
        'vip_badge',
        'host_badge'
      );
      ALTER TABLE cosmetics
        ALTER COLUMN type TYPE cosmetics_type_enum
        USING type::cosmetics_type_enum;

      UPDATE user_profiles
         SET "entryEffectUrl" = CASE
               WHEN "entryEffectUrl" LIKE '/visual-system/%' THEN "entryEffectUrl" ELSE NULL END,
             "entryAnimationUrl" = CASE
               WHEN "entryEffectUrl" LIKE '/visual-system/%' THEN '/visual-system/runtime.html' ELSE NULL END,
             "hostBadgeUrl" = CASE
               WHEN "hostBadgeUrl" LIKE '/visual-system/%' THEN "hostBadgeUrl" ELSE NULL END,
             "roomCardUrl" = CASE
               WHEN "roomCardUrl" LIKE '/visual-system/%' THEN "roomCardUrl" ELSE NULL END;
    `);

    for (const item of catalog.items) {
      const price = giftPrice(item);
      await client.query(
        `INSERT INTO gifts
          (id, name, description, "iconUrl", "animationUrl", "coinPrice",
           "diamondValue", type, "isActive", "sortOrder", "comboWindowMs",
           "luckyConfig", "createdAt", "updatedAt")
         SELECT gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7, true, $8, $9, NULL, NOW(), NOW()
          WHERE NOT EXISTS (SELECT 1 FROM gifts WHERE "iconUrl" = $3::varchar)`,
        [
          item.name,
          `هدية ${item.tier} متحركة مع مؤثر صوتي ${item.sound}`,
          `/visual-system/gifts/assets/${item.file}`,
          '/visual-system/runtime.html',
          price,
          Math.max(1, Math.floor(price * 0.8)),
          item.id === 'lucky-box' ? 'lucky' : (item.tier === 'basic' ? 'normal' : 'premium'),
          item.order,
          item.animation === 'box' ? 8000 : 5000,
        ],
      );
    }

    // Legacy Host-signals entry pack (lion→legend) removed — files never shipped.
    // Mikoo entry rides are seeded via ensureMikooCosmeticsCatalog instead.

    for (let index = 0; index < joinCatalog.length; index += 1) {
      const item = joinCatalog[index];
      const file = `join-${item.id}.png`;
      const disk = path.join(root, 'join-toasts', 'assets', file);
      if (!fs.existsSync(disk)) throw new Error(`Missing join toast asset: ${file}`);
      await insertCosmetic(
        'join_toast',
        `join_signal_${item.id}`,
        `توست ${item.name}`,
        `/visual-system/join-toasts/assets/${file}`,
        index + 1,
        900 + index * 250,
        { variant: item.id, kind: 'join_toast' },
      );
    }

    let hostFiles = [];
    const hostFramesDir = path.join(root, 'host-frames', 'assets');
    if (fs.existsSync(hostFramesDir)) {
      hostFiles = fs.readdirSync(hostFramesDir)
        .filter((name) => name.endsWith('-transparent.png'))
        .sort();
      for (let index = 0; index < hostFiles.length; index += 1) {
        const level = index + 1;
        await insertCosmetic(
          'host_badge',
          `host_signal_${String(level).padStart(2, '0')}`,
          `إشارة المضيف: ${hostNames[index] || `مستوى ${level}`}`,
          `/visual-system/host-frames/assets/${hostFiles[index]}`,
          level,
          2500 + index * 750,
          { level },
        );
      }
    } else {
      console.log('skip host-frames seed (assets missing)');
    }

    const roomFramesDir = path.join(root, 'room-frames', 'assets');
    let roomFiles = [];
    if (fs.existsSync(roomFramesDir)) {
      roomFiles = fs.readdirSync(roomFramesDir)
        .filter((name) => /^room-\d{2}-.+\.png$/i.test(name))
        .sort();
      for (let index = 0; index < roomFiles.length; index += 1) {
        const file = roomFiles[index];
        const match = file.match(/^room-(\d{2})-(.+)\.png$/i);
        const level = match ? Number(match[1]) : index + 1;
        const slug = match ? match[2] : `frame-${index + 1}`;
        await insertCosmetic(
          'room_card',
          `room_frame_${String(level).padStart(2, '0')}_${slug}`,
          `إطار روم: ${slug.replace(/-/g, ' ')}`,
          `/visual-system/room-frames/assets/${file}`,
          level,
          400 + index * 120,
          { level, slug, kind: 'room_frame' },
        );
      }
    } else {
      console.log('skip room-frames seed (assets missing)');
    }

    // Retire legacy Host-signals entry effects (lion→legend) if any remain.
    await client.query(`
      DELETE FROM cosmetics
       WHERE code LIKE 'entry_signal_%'
          OR (type::text = 'entry_effect'
              AND "previewUrl" LIKE '/visual-system/entry-effects/assets/entry-%'
              AND "previewUrl" NOT LIKE '%entry-mikoo-%')
    `);

    // Keep only Mikoo room borders active; retire kenar/pro/vip/room_frame cards.
    await client.query(`
      UPDATE cosmetics
         SET "isActive" = false, "updatedAt" = NOW()
       WHERE type::text = 'room_card'
         AND code NOT IN (
           'room_mikoo_border_top1',
           'room_mikoo_border_top2',
           'room_mikoo_border_top3'
         )
    `);

    await client.query(`
      UPDATE cosmetics
         SET "animationUrl" = '/visual-system/runtime.html',
             "updatedAt" = NOW()
       WHERE "previewUrl" LIKE '/visual-system/%'
    `);

    await client.query('COMMIT');
    console.log(
      `Visual system migrated: gifts=${catalog.items.length}, `
      + `entries=0 (legacy lion→legend removed), joins=${joinCatalog.length}, `
      + `hosts=${hostFiles.length}, rooms=${roomFiles.length}`,
    );
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    await client.end();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
