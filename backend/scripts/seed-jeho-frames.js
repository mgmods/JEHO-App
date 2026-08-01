/**
 * Upsert JEHO original animated frames into cosmetics table.
 * Usage: node scripts/seed-jeho-frames.js
 */
const { Client } = require('pg');
const fs = require('fs');
const path = require('path');

function loadEnv() {
  const envPath = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
    if (!m) continue;
    let val = m[2].trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    if (process.env[m[1]] === undefined) process.env[m[1]] = val;
  }
}

const CODES = [
  'frame_jeho_aurora_cyan',
  'frame_jeho_ruby_pulse',
  'frame_jeho_emerald_orbit',
  'frame_jeho_gold_spin',
  'frame_jeho_violet_wave',
];

async function main() {
  loadEnv();
  const catalogPath = path.join(
    __dirname,
    '..',
    'public',
    'assets',
    'cosmetics',
    'catalog.json',
  );
  const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
  const items = (catalog.items || []).filter((i) => CODES.includes(i.code));
  if (items.length !== CODES.length) {
    throw new Error(
      `Expected ${CODES.length} catalog items, found ${items.length}`,
    );
  }

  const client = new Client({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    database: process.env.DB_DATABASE || process.env.DB_NAME || 'auralive',
  });
  await client.connect();
  const out = [];
  try {
    for (const it of items) {
      const meta = JSON.stringify(it.meta || {});
      const r = await client.query(
        `INSERT INTO cosmetics (
           id, type, code, name, description,
           "previewUrl", "animationUrl", "coinPrice",
           "minVipLevel", "minUserLevel", "isActive", "sortOrder",
           meta, "createdAt", "updatedAt"
         ) VALUES (
           gen_random_uuid(), $1, $2, $3, $4,
           $5, $6, $7,
           $8, $9, true, $10,
           $11::jsonb, NOW(), NOW()
         )
         ON CONFLICT (code) DO UPDATE SET
           type = EXCLUDED.type,
           name = EXCLUDED.name,
           description = EXCLUDED.description,
           "previewUrl" = EXCLUDED."previewUrl",
           "animationUrl" = EXCLUDED."animationUrl",
           "coinPrice" = EXCLUDED."coinPrice",
           "minVipLevel" = EXCLUDED."minVipLevel",
           "minUserLevel" = EXCLUDED."minUserLevel",
           "isActive" = true,
           "sortOrder" = EXCLUDED."sortOrder",
           meta = EXCLUDED.meta,
           "updatedAt" = NOW()
         RETURNING code, "previewUrl", "animationUrl", "coinPrice"`,
        [
          it.type,
          it.code,
          it.name,
          'من متجر المظهر',
          it.previewUrl,
          it.animationUrl,
          Number(it.coinPrice) || 0,
          Number(it.minVipLevel) || 0,
          Number(it.minUserLevel) || 0,
          Number(it.sortOrder) || 0,
          meta,
        ],
      );
      out.push(r.rows[0]);
    }
  } finally {
    await client.end();
  }
  console.log(JSON.stringify({ ok: true, upserted: out }, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
