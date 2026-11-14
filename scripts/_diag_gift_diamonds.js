/**
 * Trace recent gift diamond credits for names like اسماعيل / علاء / ماري
 */
const fs = require('fs');
const { Client } = require('pg');

function loadEnv(file) {
  const keys = {};
  if (!fs.existsSync(file)) return keys;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#') || !t.includes('=')) continue;
    const i = t.indexOf('=');
    let k = t.slice(0, i).trim();
    let v = t.slice(i + 1).trim();
    if (
      (v.startsWith('"') && v.endsWith('"')) ||
      (v.startsWith("'") && v.endsWith("'"))
    )
      v = v.slice(1, -1);
    keys[k] = v;
  }
  return keys;
}

(async () => {
  const keys = loadEnv('/www/wwwroot/api.adnova.bbs.tr/.env');
  const client = new Client({
    host: keys.DB_HOST,
    port: Number(keys.DB_PORT || 5432),
    database: keys.DB_DATABASE,
    user: keys.DB_USERNAME,
    password: keys.DB_PASSWORD,
    connectionTimeoutMillis: 20000,
  });
  await client.connect();
  const out = {};

  // Find candidate users by name
  const users = await client.query(
    `SELECT id, "publicId", username, "displayName"
     FROM users
     WHERE "displayName" ILIKE '%اسماعيل%'
        OR "displayName" ILIKE '%علاء%'
        OR "displayName" ILIKE '%ماري%'
        OR "displayName" ILIKE '%Ismail%'
        OR "displayName" ILIKE '%Ala%'
        OR "displayName" ILIKE '%Mary%'
        OR username ILIKE '%ismail%'
        OR username ILIKE '%ala%'
        OR username ILIKE '%mary%'
        OR "publicId" IN ('48067367')
     ORDER BY "createdAt" DESC
     LIMIT 40`,
  );
  out.users = users.rows;

  // Also load gift_sends recent 2 hours
  const sends = await client.query(
    `SELECT gs.id, gs."senderId", gs."receiverId", gs."giftId", gs.quantity,
            gs."coinsSpent", gs."diamondsAwarded", gs."createdAt",
            s."displayName" AS sender_name, s."publicId" AS sender_public,
            r."displayName" AS receiver_name, r."publicId" AS receiver_public,
            g.name AS gift_name, g."coinPrice", g."diamondValue", g.type::text AS gift_type
     FROM gift_sends gs
     LEFT JOIN users s ON s.id = gs."senderId"
     LEFT JOIN users r ON r.id = gs."receiverId"
     LEFT JOIN gifts g ON g.id = gs."giftId"
     WHERE gs."createdAt" > NOW() - INTERVAL '6 hours'
     ORDER BY gs."createdAt" DESC
     LIMIT 40`,
  ).catch(async (e) => {
    out.gift_sends_error = String(e.message || e);
    // try column variants
    const cols = await client.query(
      `SELECT column_name FROM information_schema.columns
       WHERE table_name='gift_sends' ORDER BY ordinal_position`,
    );
    out.gift_sends_cols = cols.rows.map((r) => r.column_name);
    return { rows: [] };
  });
  out.recent_sends = sends.rows || [];

  // wallet txs gift related last 6h
  const txs = await client.query(
    `SELECT t."userId", u."publicId", u."displayName",
            t.type::text, t.currency::text, t.amount, t."balanceAfter",
            t."referenceType", t.description, t."createdAt", t.metadata
     FROM wallet_transactions t
     LEFT JOIN users u ON u.id = t."userId"
     WHERE t."createdAt" > NOW() - INTERVAL '6 hours'
       AND (
         t."referenceType" ILIKE '%gift%'
         OR t.description ILIKE '%gift%'
         OR t.description ILIKE '%هدي%'
         OR t.type::text ILIKE '%GIFT%'
       )
     ORDER BY t."createdAt" DESC
     LIMIT 50`,
  );
  out.recent_gift_txs = txs.rows;

  // Wallets for name matches
  if (users.rows.length) {
    const ids = users.rows.map((u) => u.id);
    const wallets = await client.query(
      `SELECT w."userId", u."publicId", u."displayName",
              w.coins, w.diamonds, w."agencyDiamonds", w."traderDiamonds", w."updatedAt"
       FROM wallets w
       JOIN users u ON u.id = w."userId"
       WHERE w."userId" = ANY($1::uuid[])`,
      [ids],
    );
    out.wallets = wallets.rows;
  }

  // Top recent gift receives specifically diamonds positive
  const diaRecv = await client.query(
    `SELECT t."createdAt", u."publicId", u."displayName", t.amount, t."balanceAfter",
            t."referenceType", left(t.description, 120) AS description
     FROM wallet_transactions t
     JOIN users u ON u.id = t."userId"
     WHERE t."createdAt" > NOW() - INTERVAL '6 hours'
       AND t.currency::text ILIKE '%DIAMOND%'
       AND t.amount > 0
       AND t."referenceType" ILIKE '%gift%'
     ORDER BY t."createdAt" DESC
     LIMIT 30`,
  );
  out.diamond_credits = diaRecv.rows;

  console.log(JSON.stringify(out));
  await client.end();
})().catch((e) => {
  console.log(JSON.stringify({ error: String(e.message || e) }));
});
