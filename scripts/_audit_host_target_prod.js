/**
 * Production schema/data health audit for host-target + related risk areas.
 * Prints ASCII-only JSON summary (safe for Windows consoles).
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
    host: keys.DB_HOST || 'localhost',
    port: Number(keys.DB_PORT || 5432),
    database: keys.DB_DATABASE,
    user: keys.DB_USERNAME,
    password: keys.DB_PASSWORD,
    connectionTimeoutMillis: 20000,
  });
  await client.connect();
  const out = { ok: true, findings: [] };

  function finding(level, id, detail) {
    out.findings.push({ level, id, detail });
  }

  // --- host_monthly_progress schema ---
  const progCols = await client.query(
    `SELECT column_name, data_type, is_nullable, column_default
     FROM information_schema.columns
     WHERE table_schema='public' AND table_name='host_monthly_progress'
     ORDER BY ordinal_position`,
  );
  out.progress_columns = progCols.rows.map((r) => r.column_name);
  const colSet = new Set(out.progress_columns);
  for (const need of [
    'userId',
    'yearMonth',
    'progress',
    'claimedStageIds',
    'withdrawnStageIds',
    'cyclesCompleted',
  ]) {
    if (!colSet.has(need)) finding('critical', 'missing_column', need);
  }

  // --- config ---
  const cfgRow = await client.query(
    `SELECT value FROM app_settings WHERE key='host_monthly_target' LIMIT 1`,
  );
  let cfg = null;
  if (cfgRow.rows[0]) {
    try {
      cfg = JSON.parse(cfgRow.rows[0].value);
    } catch (e) {
      finding('critical', 'config_parse', String(e.message || e));
    }
  } else {
    finding('critical', 'config_missing', 'host_monthly_target');
  }
  if (cfg) {
    out.config = {
      enabled: cfg.enabled,
      period: cfg.period,
      currency: cfg.currency,
      ladderVersion: cfg.ladderVersion,
      stage_count: (cfg.stages || []).length,
    };
    const stages = cfg.stages || [];
    const zeroReward = stages.filter(
      (s) =>
        Number(s.rewardDiamonds || 0) === 0 && Number(s.rewardCoins || 0) === 0,
    ).length;
    out.config.stages_zero_wallet_reward = zeroReward;
    if (zeroReward === stages.length && stages.length) {
      finding(
        'info',
        'wallet_rewards_zero',
        'All stages have rewardDiamonds/Coins=0 (salary USD via withdraw only)',
      );
    }
    if (String(cfg.period || '') === 'weekly') {
      finding(
        'warn',
        'period_weekly',
        'Progress keyed as ISO week (YYYYWww); monthly rows (YYYY-MM) are orphaned',
      );
    }
  }

  // --- period distribution ---
  const periodDist = await client.query(
    `SELECT "yearMonth", COUNT(*)::int AS n,
            SUM(CASE WHEN progress::bigint > 0 THEN 1 ELSE 0 END)::int AS with_progress,
            SUM(CASE WHEN COALESCE("claimedStageIds",'[]') NOT IN ('[]','null','') THEN 1 ELSE 0 END)::int AS with_claims
     FROM host_monthly_progress
     GROUP BY "yearMonth"
     ORDER BY "yearMonth" DESC
     LIMIT 20`,
  );
  out.period_distribution = periodDist.rows;

  // Orphans: monthly keys when config weekly (or vice versa)
  const mixed = await client.query(
    `SELECT
       SUM(CASE WHEN "yearMonth" ~ '^[0-9]{4}-[0-9]{2}$' THEN 1 ELSE 0 END)::int AS monthly_keys,
       SUM(CASE WHEN "yearMonth" ~ '^[0-9]{4}W[0-9]{2}$' THEN 1 ELSE 0 END)::int AS weekly_keys,
       COUNT(*)::int AS total
     FROM host_monthly_progress`,
  );
  out.period_key_mix = mixed.rows[0];
  if (
    mixed.rows[0] &&
    Number(mixed.rows[0].monthly_keys) > 0 &&
    Number(mixed.rows[0].weekly_keys) > 0
  ) {
    finding(
      'warn',
      'mixed_period_keys',
      `monthly=${mixed.rows[0].monthly_keys} weekly=${mixed.rows[0].weekly_keys}`,
    );
  }

  // Hosts with many claimed stages this active period (likely confused)
  let activeKey = null;
  if (cfg && cfg.period === 'weekly') {
    // compute current ISO week-ish key
    const d = new Date();
    const tmp = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
    tmp.setUTCDate(tmp.getUTCDate() + 4 - (tmp.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil(
      ((tmp.getTime() - yearStart.getTime()) / 86400000 + 1) / 7,
    );
    activeKey = `${tmp.getUTCFullYear()}W${String(weekNo).padStart(2, '0')}`;
  } else {
    const d = new Date();
    activeKey = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
  }
  out.active_period_key_guess = activeKey;

  const topProgress = await client.query(
    `SELECT u."publicId", u.username, p.progress::bigint AS progress,
            p."claimedStageIds", p."withdrawnStageIds", p."cyclesCompleted", p."updatedAt"
     FROM host_monthly_progress p
     JOIN users u ON u.id = p."userId"
     WHERE p."yearMonth" = $1 AND p.progress::bigint > 0
     ORDER BY p.progress::bigint DESC
     LIMIT 25`,
    [activeKey],
  );
  out.top_progress_active = topProgress.rows.map((r) => ({
    publicId: r.publicId,
    progress: String(r.progress),
    claimed_len: (() => {
      try {
        const a = typeof r.claimedStageIds === 'string'
          ? JSON.parse(r.claimedStageIds || '[]')
          : r.claimedStageIds || [];
        return Array.isArray(a) ? a.length : 0;
      } catch {
        return -1;
      }
    })(),
    withdrawn_len: (() => {
      try {
        const a = typeof r.withdrawnStageIds === 'string'
          ? JSON.parse(r.withdrawnStageIds || '[]')
          : r.withdrawnStageIds || [];
        return Array.isArray(a) ? a.length : 0;
      } catch {
        return -1;
      }
    })(),
    cycles: r.cyclesCompleted,
  }));

  // Hosts claimed many stages but zero withdrawn (normal until salary cashout)
  const claimedNoWithdraw = await client.query(
    `SELECT COUNT(*)::int AS n FROM host_monthly_progress
     WHERE "yearMonth" = $1
       AND COALESCE("claimedStageIds",'[]') NOT IN ('[]','null','')
       AND (COALESCE("withdrawnStageIds",'[]') IN ('[]','null','') OR "withdrawnStageIds" IS NULL)`,
    [activeKey],
  );
  out.claimed_but_no_salary_withdraw = claimedNoWithdraw.rows[0];

  // Wallet: diamonds=0 hosts with high progress - not a bug if rewards zero
  const highProgZeroDiamonds = await client.query(
    `SELECT COUNT(*)::int AS n
     FROM host_monthly_progress p
     JOIN wallets w ON w."userId" = p."userId"
     WHERE p."yearMonth" = $1 AND p.progress::bigint >= 150000
       AND COALESCE(w.diamonds,0)::bigint = 0`,
    [activeKey],
  );
  out.high_progress_zero_personal_diamonds = highProgZeroDiamonds.rows[0];
  if (Number(highProgZeroDiamonds.rows[0]?.n || 0) > 0) {
    finding(
      'info',
      'high_progress_zero_diamonds',
      `${highProgZeroDiamonds.rows[0].n} hosts with progress>=150k and personal diamonds=0 (expected if salary-only ladder)`,
    );
  }

  // Stage id mix: old salary_N vs salary_v2_N
  const claimShapes = await client.query(
    `SELECT
       SUM(CASE WHEN "claimedStageIds" LIKE '%salary_v2_%' THEN 1 ELSE 0 END)::int AS v2_claims,
       SUM(CASE WHEN "claimedStageIds" LIKE '%"salary_1"%' OR "claimedStageIds" LIKE '%salary_1,%' OR "claimedStageIds" LIKE '%["salary_1"%' THEN 1 ELSE 0 END)::int AS v1_claims,
       COUNT(*)::int AS total
     FROM host_monthly_progress
     WHERE COALESCE("claimedStageIds",'[]') NOT IN ('[]','null','')`,
  );
  out.claim_id_shapes = claimShapes.rows[0];
  if (Number(claimShapes.rows[0]?.v1_claims || 0) > 0) {
    finding(
      'warn',
      'legacy_stage_ids',
      `${claimShapes.rows[0].v1_claims} rows still have old salary_N claim ids`,
    );
  }

  // Related tables that often drift
  const tables = [
    'host_monthly_progress',
    'host_target_claims',
    'withdraw_requests',
    'wallets',
    'wallet_transactions',
    'app_settings',
  ];
  out.table_exists = {};
  for (const t of tables) {
    const r = await client.query(
      `SELECT EXISTS (
         SELECT 1 FROM information_schema.tables
         WHERE table_schema='public' AND table_name=$1
       ) AS e`,
      [t],
    );
    out.table_exists[t] = !!r.rows[0]?.e;
    if (!r.rows[0]?.e && t !== 'host_target_claims') {
      finding('warn', 'table_missing', t);
    }
  }

  // withdraw_requests columns for stageId linkage
  if (out.table_exists.withdraw_requests) {
    const wc = await client.query(
      `SELECT column_name FROM information_schema.columns
       WHERE table_name='withdraw_requests' ORDER BY ordinal_position`,
    );
    out.withdraw_columns = wc.rows.map((r) => r.column_name);
    const need = ['userId', 'diamonds', 'status'];
    for (const n of need) {
      if (!out.withdraw_columns.includes(n))
        finding('warn', 'withdraw_col_missing', n);
    }
    const hasStage =
      out.withdraw_columns.includes('stageId') ||
      out.withdraw_columns.includes('targetStageId') ||
      out.withdraw_columns.includes('metadata');
    if (!hasStage) {
      finding(
        'warn',
        'withdraw_no_stage_link',
        'No obvious stageId column — hard to audit salary stage advances',
      );
    }
  }

  // counts
  const counts = await client.query(
    `SELECT
       (SELECT COUNT(*)::int FROM host_monthly_progress) AS progress_rows,
       (SELECT COUNT(*)::int FROM host_monthly_progress WHERE "yearMonth"=$1) AS active_rows,
       (SELECT COUNT(*)::int FROM host_monthly_progress WHERE "yearMonth"=$1 AND progress::bigint>0) AS active_with_progress,
       (SELECT COUNT(*)::int FROM withdraw_requests) AS withdrawals_total`,
    [activeKey],
  );
  out.counts = counts.rows[0];

  // diamond usd rate
  const rate = await client.query(
    `SELECT value FROM app_settings WHERE key='economy.diamondUsdRate' LIMIT 1`,
  );
  out.diamondUsdRate = rate.rows[0]?.value || null;

  // recent host target reward txs (should be rare if rewards 0)
  try {
    const txs = await client.query(
      `SELECT COUNT(*)::int AS n
       FROM wallet_transactions
       WHERE "referenceType" = 'host_monthly_target'`,
    );
    out.host_target_wallet_tx_count = txs.rows[0]?.n ?? 0;
  } catch (e) {
    out.host_target_wallet_tx_error = String(e.message || e);
  }

  console.log(JSON.stringify(out));
  await client.end();
})().catch((e) => {
  console.log(JSON.stringify({ ok: false, error: String(e.message || e) }));
});
