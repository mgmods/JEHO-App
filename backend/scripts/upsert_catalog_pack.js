/**
 * Upsert generated cosmetics + gifts pack into DB.
 * Usage: NODE_PATH=./node_modules node scripts/upsert_catalog_pack.js
 * Or from backend root with ts-node env loaded.
 */
const fs = require('fs');
const path = require('path');
const { Client } = require('pg');

require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const pack = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'generated_catalog_pack.json'), 'utf8'),
);

async function main() {
  const client = new Client({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    database: process.env.DB_NAME || 'auralive',
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
  });
  await client.connect();
  let cos = 0;
  let gifts = 0;
  for (const item of pack.cosmetics || []) {
    const existing = await client.query('SELECT id FROM cosmetics WHERE code = $1 LIMIT 1', [item.code]);
    const meta = JSON.stringify(item.meta || {});
    if (existing.rows[0]) {
      await client.query(
        `UPDATE cosmetics SET type=$2, name=$3, description=$4, "previewUrl"=$5, "animationUrl"=$6,
         "coinPrice"=$7, "minVipLevel"=$8, "minUserLevel"=$9, "isActive"=true, "sortOrder"=$10, meta=$11, "updatedAt"=NOW()
         WHERE id=$1`,
        [
          existing.rows[0].id,
          item.type,
          item.name,
          item.description || null,
          item.previewUrl,
          item.animationUrl || null,
          item.coinPrice || 0,
          item.minVipLevel || 0,
          item.minUserLevel || 0,
          item.sortOrder || 0,
          meta,
        ],
      );
    } else {
      await client.query(
        `INSERT INTO cosmetics (id, type, code, name, description, "previewUrl", "animationUrl", "coinPrice",
         "minVipLevel", "minUserLevel", "isActive", "sortOrder", meta, "createdAt", "updatedAt")
         VALUES (gen_random_uuid(), $1,$2,$3,$4,$5,$6,$7,$8,$9,true,$10,$11,NOW(),NOW())`,
        [
          item.type,
          item.code,
          item.name,
          item.description || null,
          item.previewUrl,
          item.animationUrl || null,
          item.coinPrice || 0,
          item.minVipLevel || 0,
          item.minUserLevel || 0,
          item.sortOrder || 0,
          meta,
        ],
      );
    }
    cos += 1;
  }
  for (const g of pack.gifts || []) {
    const existing = await client.query('SELECT id FROM gifts WHERE name = $1 LIMIT 1', [g.name]);
    const lucky = g.luckyConfig ? JSON.stringify(g.luckyConfig) : null;
    if (existing.rows[0]) {
      await client.query(
        `UPDATE gifts SET "iconUrl"=$2, "animationUrl"=$3, "coinPrice"=$4, "diamondValue"=$5, type=$6,
         "sortOrder"=$7, "isActive"=true, "luckyConfig"=$8 WHERE id=$1`,
        [
          existing.rows[0].id,
          g.iconUrl,
          g.animationUrl,
          g.coinPrice,
          g.diamondValue,
          g.type,
          g.sortOrder,
          lucky,
        ],
      );
    } else {
      await client.query(
        `INSERT INTO gifts (id, name, "iconUrl", "animationUrl", "coinPrice", "diamondValue", type,
         "isActive", "sortOrder", "luckyConfig", "createdAt", "updatedAt")
         VALUES (gen_random_uuid(), $1,$2,$3,$4,$5,$6,true,$7,$8,NOW(),NOW())`,
        [g.name, g.iconUrl, g.animationUrl, g.coinPrice, g.diamondValue, g.type, g.sortOrder, lucky],
      );
    }
    gifts += 1;
  }
  // profile/room columns if missing
  await client.query(`ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "roomCardUrl" varchar(512)`);
  await client.query(`ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS "entryAnimationUrl" varchar(512)`);
  await client.query(`ALTER TABLE rooms ADD COLUMN IF NOT EXISTS "roomCardUrl" varchar(512)`);
  console.log(`Upserted cosmetics=${cos} gifts=${gifts}`);
  await client.end();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
