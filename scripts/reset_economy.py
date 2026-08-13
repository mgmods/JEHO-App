#!/usr/bin/env python3
"""CLEAN ECONOMY — reset production economy data to a blank, coherent state.

Wipes only economy ledgers/balances and stale economy settings. KEEPS users,
agencies, agency members, the gift catalog and recharge packages.

Usage (from repo root):
    python scripts/reset_economy.py            # DRY RUN — shows what would change
    python scripts/reset_economy.py --apply    # actually run the reset (transaction)

Safe by design: the SSH/DB credentials load via scripts/deploy_secrets.py, and
nothing is written unless you pass --apply.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
import paramiko

APPLY = "--apply" in sys.argv[1:]

NODE_SCRIPT = r"""
const fs = require('fs');
const path = require('path');
const envPath = path.join(process.cwd(), '.env');
const envText = fs.readFileSync(envPath, 'utf8');
for (const line of envText.split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
  if (!m || m[1].startsWith('#')) continue;
  let v = m[2].trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
  if (process.env[m[1]] === undefined) process.env[m[1]] = v;
}
const { Client } = require(path.join(process.cwd(), 'node_modules', 'pg'));
const APPLY = process.env.RESET_APPLY === '1';

// Stale economy setting keys from the old multi-pool / salary-ladder system.
// Deleting economy.config.v1 + host_monthly_target lets them reseed cleanly.
const STALE_KEYS = [
  'agency_platform_cut_percent',
  'agency_host_share_percent',
  'platform_gift_revenue_diamonds',
  'economy.diamondUsdRate',
  'economy.minWithdrawDiamonds',
  'economy.diamondCoinRate',
  'economy.withdrawTargetDiamonds',
  'withdraw_target_diamonds',
  'economy.platform_share',
  'economy.agency_share',
  'economy.host_share_agency',
  'economy.host_share_solo',
  'gift_platform_share',
  'gift_agency_share',
  'host_monthly_target',
  'economy.config.v1',
];

// Ledger tables to empty (only if they exist).
const TRUNCATE_TABLES = [
  'wallet_transactions',
  'gift_sends',
  'host_monthly_progress',
  'host_target_claims',
  'agency_payout_requests',
  'withdraw_requests',
  'lucky_reward_grants',
  'lucky_box_opens',
];

(async () => {
  const c = new Client({
    host: process.env.DB_HOST || process.env.POSTGRES_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || process.env.POSTGRES_PORT || 5432),
    user: process.env.DB_USER || process.env.DB_USERNAME || process.env.POSTGRES_USER,
    password: process.env.DB_PASSWORD || process.env.DB_PASS || process.env.POSTGRES_PASSWORD,
    database: process.env.DB_NAME || process.env.DB_DATABASE || process.env.POSTGRES_DB,
  });
  await c.connect();

  const exists = async (t) => {
    const r = await c.query(`SELECT to_regclass($1) AS reg`, ['public.' + t]);
    return !!r.rows[0].reg;
  };

  // ---- BEFORE snapshot ----
  const before = {};
  const wb = await c.query(
    `SELECT COUNT(*)::int AS wallets,
            COALESCE(SUM(coins),0)::text AS coins,
            COALESCE(SUM(diamonds),0)::text AS diamonds,
            COALESCE(SUM("agencyDiamonds"),0)::text AS "agencyDiamonds",
            COALESCE(SUM("traderDiamonds"),0)::text AS "traderDiamonds"
     FROM wallets`,
  );
  before.wallets = wb.rows[0];
  for (const t of TRUNCATE_TABLES) {
    if (await exists(t)) {
      const r = await c.query(`SELECT COUNT(*)::int AS n FROM ${t}`);
      before[t] = r.rows[0].n;
    } else {
      before[t] = 'MISSING';
    }
  }
  const sk = await c.query(
    `SELECT COUNT(*)::int AS n FROM app_settings WHERE key = ANY($1)`,
    [STALE_KEYS],
  );
  before.staleSettings = sk.rows[0].n;
  console.log('BEFORE', JSON.stringify(before, null, 2));

  if (!APPLY) {
    console.log('DRY RUN — nothing changed. Re-run with --apply to execute.');
    await c.end();
    return;
  }

  // ---- APPLY (single transaction) ----
    await c.query('BEGIN');
  try {
    await c.query(
      `UPDATE wallets SET coins = 0, diamonds = 0,
        "agencyDiamonds" = 0, "traderDiamonds" = 0,
        "silverCoins" = 0, "gamePoints" = 0,
        "totalRecharged" = 0, "totalWithdrawn" = 0`,
    );
    // Truncate every existing ledger table in ONE statement so mutual FKs
    // (e.g. lucky_reward_grants -> lucky_box_opens) don't block the wipe.
    const present = [];
    for (const t of TRUNCATE_TABLES) {
      if (await exists(t)) present.push(t);
    }
    if (present.length) {
      await c.query(`TRUNCATE TABLE ${present.join(', ')} RESTART IDENTITY CASCADE`);
    }
    if (await exists('agencies')) {
      await c.query(`UPDATE agencies SET "totalDiamonds" = 0`);
    }
    await c.query(`DELETE FROM app_settings WHERE key = ANY($1)`, [STALE_KEYS]);
    await c.query('COMMIT');
    console.log('APPLIED: economy reset committed.');
  } catch (e) {
    await c.query('ROLLBACK');
    console.error('ROLLED BACK:', e.message);
    process.exit(1);
  }

  // ---- AFTER snapshot ----
  const after = {};
  const wa = await c.query(
    `SELECT COUNT(*)::int AS wallets,
            COALESCE(SUM(coins),0)::text AS coins,
            COALESCE(SUM(diamonds),0)::text AS diamonds
     FROM wallets`,
  );
  after.wallets = wa.rows[0];
  console.log('AFTER', JSON.stringify(after, null, 2));

  await c.end();
})().catch((e) => {
  console.error('ERR', e.message);
  process.exit(1);
});
"""


def main() -> None:
    host, user, password, remote = deploy_settings()
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(host, username=user, password=password, timeout=30)
    try:
        sftp = client.open_sftp()
        with sftp.file("/tmp/jeho_reset_economy.js", "w") as f:
            f.write(NODE_SCRIPT)
        sftp.close()
        env = "RESET_APPLY=1 " if APPLY else ""
        cmd = f"cd {remote} && {env}node /tmp/jeho_reset_economy.js"
        _stdin, stdout, stderr = client.exec_command(cmd, timeout=120)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        print(out)
        if err.strip():
            print("STDERR:", err[-2000:])
    finally:
        client.close()


if __name__ == "__main__":
    mode = "APPLY" if APPLY else "DRY RUN"
    print(f"JEHO economy reset — mode: {mode}")
    main()
