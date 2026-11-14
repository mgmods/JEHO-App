/**
 * One-shot: deactivate every VIP membership and clear VIP cosmetics from profiles.
 * VIP is purchase-only going forward.
 *
 * Usage (on server): node scripts/wipe-all-vip.js
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
    const key = m[1];
    let val = m[2].trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    if (process.env[key] === undefined) process.env[key] = val;
  }
}

async function main() {
  loadEnv();
  const client = new Client({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    database: process.env.DB_DATABASE || process.env.DB_NAME || 'auralive',
  });
  await client.connect();
  try {
    const vip = await client.query(
      `UPDATE user_vips SET "isActive" = false WHERE "isActive" = true RETURNING id`,
    );
    const profiles = await client.query(
      `UPDATE user_profiles
       SET "vipBadgeUrl" = NULL,
           "hostBadgeUrl" = NULL,
           "entryEffectUrl" = NULL
       WHERE "vipBadgeUrl" IS NOT NULL
          OR "hostBadgeUrl" IS NOT NULL
          OR "entryEffectUrl" IS NOT NULL
       RETURNING "userId"`,
    );
    const cosmetics = await client.query(
      `UPDATE user_cosmetics uc
       SET equipped = false
       FROM cosmetics c
       WHERE uc."cosmeticId" = c.id
         AND uc.equipped = true
         AND c.type IN ('vip_badge', 'host_badge', 'entry_effect', 'join_toast')
       RETURNING uc.id`,
    );
    console.log(
      JSON.stringify(
        {
          ok: true,
          deactivatedVipRows: vip.rowCount,
          clearedProfiles: profiles.rowCount,
          unequippedCosmetics: cosmetics.rowCount,
        },
        null,
        2,
      ),
    );
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
