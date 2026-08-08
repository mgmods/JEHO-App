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

(async () => {
  const publicId = process.env.PUBLIC_ID || '48067367';
  const keys = loadEnv('/www/wwwroot/api.adnova.bbs.tr/.env');
  const url = keys.DATABASE_URL || keys.DB_URL || keys.TYPEORM_URL || '';
  const cfg = url
    ? { connectionString: url }
    : {
        host: keys.DB_HOST || keys.DATABASE_HOST || 'localhost',
        port: Number(keys.DB_PORT || keys.DATABASE_PORT || 5432),
        database:
          keys.DB_DATABASE ||
          keys.DB_NAME ||
          keys.DATABASE_NAME ||
          keys.POSTGRES_DB,
        user:
          keys.DB_USERNAME ||
          keys.DB_USER ||
          keys.DATABASE_USER ||
          keys.POSTGRES_USER,
        password:
          keys.DB_PASSWORD ||
          keys.DATABASE_PASSWORD ||
          keys.POSTGRES_PASSWORD,
      };

  const client = new Client({ ...cfg, connectionTimeoutMillis: 15000 });
  await client.connect();
  const out = { publicId };

  const users = await client.query(
    `SELECT id, "publicId", username, "displayName", "createdAt"
     FROM users
     WHERE "publicId" = $1 OR username = $1
     LIMIT 5`,
    [publicId],
  );
  out.users = users.rows;
  if (!users.rows.length) {
    console.log(JSON.stringify(out));
    await client.end();
    return;
  }
  const uid = users.rows[0].id;
  out.userId = uid;
  out.displayName = users.rows[0].displayName;
  out.username = users.rows[0].username;

  try {
    const w = await client.query(
      `SELECT * FROM wallets WHERE "userId" = $1 LIMIT 1`,
      [uid],
    );
    out.wallet = w.rows[0] || null;
  } catch (e) {
    out.wallet_error = String(e.message || e);
  }

  try {
    const p = await client.query(
      `SELECT id, "yearMonth", progress, "claimedStageIds",
              "withdrawnStageIds", "cyclesCompleted", "createdAt", "updatedAt"
       FROM host_monthly_progress
       WHERE "userId" = $1
       ORDER BY "updatedAt" DESC NULLS LAST
       LIMIT 20`,
      [uid],
    );
    out.progress_rows = p.rows;
  } catch (e) {
    out.progress_error = String(e.message || e);
  }

  try {
    const c = await client.query(
      `SELECT id, type::text, currency::text, amount, "balanceAfter",
              "referenceType", "referenceId", description, "createdAt"
       FROM wallet_transactions
       WHERE "userId" = $1
         AND (
           "referenceType" = 'host_monthly_target'
           OR description ILIKE '%host target%'
         )
       ORDER BY "createdAt" DESC
       LIMIT 40`,
      [uid],
    );
    out.host_target_txs = c.rows;
  } catch (e) {
    out.tx_error = String(e.message || e);
  }

  try {
    const s = await client.query(
      `SELECT value FROM app_settings WHERE key = 'host_monthly_target' LIMIT 1`,
    );
    if (s.rows[0]) {
      const parsed = JSON.parse(s.rows[0].value);
      const stages = parsed.stages || [];
      out.config_meta = {
        enabled: parsed.enabled,
        period: parsed.period,
        currency: parsed.currency,
        ladderVersion: parsed.ladderVersion,
        stage_count: stages.length,
        sample_stages: stages.slice(0, 5).map((x) => ({
          id: x.id,
          title: x.title,
          threshold: x.threshold,
          rewardDiamonds: x.rewardDiamonds,
          rewardCoins: x.rewardCoins,
          hostSalaryUsd: x.hostSalaryUsd,
        })),
        last_stage: stages.length
          ? {
              id: stages[stages.length - 1].id,
              threshold: stages[stages.length - 1].threshold,
              hostSalaryUsd: stages[stages.length - 1].hostSalaryUsd,
            }
          : null,
      };
    }
  } catch (e) {
    out.config_error = String(e.message || e);
  }

  try {
    const a = await client.query(
      `SELECT m."agencyId", m.role::text, m.status::text, a.name AS agency_name
       FROM agency_members m
       LEFT JOIN agencies a ON a.id = m."agencyId"
       WHERE m."userId" = $1
       LIMIT 10`,
      [uid],
    );
    out.agencies = a.rows;
  } catch (e) {
    out.agency_error = String(e.message || e);
  }

  try {
    const wr = await client.query(
      `SELECT table_name FROM information_schema.tables
       WHERE table_schema='public' AND table_name ILIKE '%withdraw%'`,
    );
    out.withdraw_tables = wr.rows.map((r) => r.table_name);
    for (const t of out.withdraw_tables) {
      try {
        const q = await client.query(
          `SELECT * FROM "${t}" WHERE "userId" = $1 ORDER BY 1 DESC LIMIT 12`,
          [uid],
        );
        out['table_' + t] = q.rows;
      } catch (e) {
        out['table_' + t + '_err'] = String(e.message || e);
      }
    }
  } catch (e) {
    out.withdraw_error = String(e.message || e);
  }

  console.log(JSON.stringify(out));
  await client.end();
})().catch((e) => {
  console.log(JSON.stringify({ error: String(e && e.message ? e.message : e) }));
  process.exit(0);
});
