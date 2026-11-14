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
    ) {
      v = v.slice(1, -1);
    }
    keys[k] = v;
  }
  return keys;
}

function dbCfg(keys) {
  return {
    host: keys.DB_HOST || 'localhost',
    port: Number(keys.DB_PORT || 5432),
    database: keys.DB_DATABASE || keys.DB_NAME,
    user: keys.DB_USERNAME || keys.DB_USER,
    password: keys.DB_PASSWORD,
  };
}

(async () => {
  const publicId = process.env.PUBLIC_ID || '48067367';
  const keys = loadEnv('/www/wwwroot/api.adnova.bbs.tr/.env');
  const client = new Client({ ...dbCfg(keys), connectionTimeoutMillis: 15000 });
  await client.connect();
  const out = { publicId };

  const cols = await client.query(
    `SELECT column_name, data_type
     FROM information_schema.columns
     WHERE table_name = 'host_monthly_progress'
     ORDER BY ordinal_position`,
  );
  out.progress_columns = cols.rows;

  const users = await client.query(
    `SELECT id, "publicId", username, "displayName"
     FROM users WHERE "publicId" = $1 LIMIT 1`,
    [publicId],
  );
  out.user = users.rows[0] || null;
  if (!out.user) {
    console.log(JSON.stringify(out));
    await client.end();
    return;
  }
  const uid = out.user.id;

  const allProg = await client.query(
    `SELECT * FROM host_monthly_progress WHERE "userId" = $1`,
    [uid],
  );
  out.progress_all = allProg.rows;

  // also any recent progress near this week keys
  const recent = await client.query(
    `SELECT "userId", "yearMonth", progress, "claimedStageIds", "cyclesCompleted", "updatedAt"
     FROM host_monthly_progress
     WHERE "yearMonth" LIKE '2026%'
     ORDER BY "updatedAt" DESC NULLS LAST
     LIMIT 5`,
  );
  out.sample_recent_any_user = recent.rows;

  // wallet slim
  const w = await client.query(
    `SELECT coins, diamonds, "agencyDiamonds", "updatedAt"
     FROM wallets WHERE "userId" = $1`,
    [uid],
  );
  out.wallet = w.rows[0] || null;

  // how progress is recorded - gift receives last days
  try {
    const giftCols = await client.query(
      `SELECT column_name FROM information_schema.columns
       WHERE table_name = 'gift_transactions' ORDER BY ordinal_position`,
    );
    out.gift_tx_cols = giftCols.rows.map((r) => r.column_name);
    if (giftCols.rows.length) {
      const g = await client.query(
        `SELECT * FROM gift_transactions
         WHERE "receiverId" = $1 OR "toUserId" = $1 OR "userId" = $1
         ORDER BY 1 DESC LIMIT 5`,
        [uid],
      ).catch(async () => {
        // try receiverId only
        return client.query(
          `SELECT id, "createdAt" FROM gift_transactions WHERE "receiverId" = $1
           ORDER BY "createdAt" DESC LIMIT 5`,
          [uid],
        );
      });
      out.gift_sample = g.rows;
    }
  } catch (e) {
    out.gift_error = String(e.message || e);
  }

  // stage salary withdraw marks - check withdraw_requests columns + all for user
  const wrCols = await client.query(
    `SELECT column_name FROM information_schema.columns
     WHERE table_name = 'withdraw_requests' ORDER BY ordinal_position`,
  );
  out.withdraw_request_cols = wrCols.rows.map((r) => r.column_name);
  try {
    const wr = await client.query(
      `SELECT * FROM withdraw_requests WHERE "userId" = $1 ORDER BY "createdAt" DESC LIMIT 20`,
      [uid],
    );
    out.withdraw_requests = wr.rows;
  } catch (e) {
    out.wr_err = String(e.message || e);
  }

  // Host receive gift diamonds progress path - wallet txs recent
  const txs = await client.query(
    `SELECT "createdAt", type::text, currency::text, amount, "referenceType", "referenceId",
            left(coalesce(description,''), 100) AS description
     FROM wallet_transactions
     WHERE "userId" = $1
     ORDER BY "createdAt" DESC
     LIMIT 30`,
    [uid],
  );
  out.recent_wallet_txs = txs.rows;

  console.log(JSON.stringify(out));
  await client.end();
})().catch((e) => {
  console.log(JSON.stringify({ error: String(e.message || e) }));
});
