#!/usr/bin/env python3
"""FULL production reset to a blank slate for testing.

Zeros: all wallet balances, all agency earnings, every owned/equipped cosmetic
(frames, entry effects, room cards, badges, VIP), profile earning counters, and
all economy/gift ledgers.

KEEPS: users, agencies + membership, the gift catalog, recharge packages, and
the live economy config (economy.config.v1) + host target config.

Usage:
    python scripts/full_reset.py            # DRY RUN
    python scripts/full_reset.py --apply    # execute (single transaction)
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
const envText = fs.readFileSync(path.join(process.cwd(), '.env'), 'utf8');
for (const line of envText.split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
  if (!m || m[1].startsWith('#')) continue;
  let v = m[2].trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
  if (process.env[m[1]] === undefined) process.env[m[1]] = v;
}
const { Client } = require(path.join(process.cwd(), 'node_modules', 'pg'));
const APPLY = process.env.RESET_APPLY === '1';

// Ledgers + ownership tables to empty (only if present).
const TRUNCATE_TABLES = [
  'wallet_transactions', 'gift_sends', 'host_monthly_progress',
  'host_target_claims', 'withdraw_requests', 'agency_payout_requests',
  'lucky_reward_grants', 'lucky_box_opens',
  'user_cosmetics', 'user_vips',
];

(async () => {
  const c = new Client({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USER || process.env.DB_USERNAME || process.env.POSTGRES_USER,
    password: process.env.DB_PASSWORD || process.env.POSTGRES_PASSWORD,
    database: process.env.DB_NAME || process.env.DB_DATABASE || process.env.POSTGRES_DB,
  });
  await c.connect();
  const exists = async (t) => !!(await c.query(`SELECT to_regclass($1) r`, ['public.' + t])).rows[0].r;

  const snap = async () => {
    const w = (await c.query(`SELECT COUNT(*)::int n, COALESCE(SUM(coins),0)::text coins, COALESCE(SUM(diamonds),0)::text diamonds, COALESCE(SUM("agencyDiamonds"),0)::text ad FROM wallets`)).rows[0];
    const uc = await exists('user_cosmetics') ? (await c.query(`SELECT COUNT(*)::int n FROM user_cosmetics`)).rows[0].n : 'MISSING';
    const uv = await exists('user_vips') ? (await c.query(`SELECT COUNT(*)::int n FROM user_vips`)).rows[0].n : 'MISSING';
    const ag = (await c.query(`SELECT COUNT(*)::int n, COALESCE(SUM("totalDiamonds"),0)::text t FROM agencies`)).rows[0];
    const gs = await exists('gift_sends') ? (await c.query(`SELECT COUNT(*)::int n FROM gift_sends`)).rows[0].n : 0;
    const users = (await c.query(`SELECT COUNT(*)::int n FROM users`)).rows[0].n;
    return { wallets: w, ownedCosmetics: uc, vips: uv, agencies: ag, giftSends: gs, users };
  };

  console.log('BEFORE', JSON.stringify(await snap(), null, 2));
  if (!APPLY) { console.log('DRY RUN — nothing changed. Re-run with --apply.'); await c.end(); return; }

  await c.query('BEGIN');
  try {
    // 1) wallets → all balances zero
    await c.query(`UPDATE wallets SET coins=0, diamonds=0, "agencyDiamonds"=0, "traderDiamonds"=0,
      "silverCoins"=0, "gamePoints"=0, "totalRecharged"=0, "totalWithdrawn"=0`);
    // 2) agency earnings → zero
    await c.query(`UPDATE agencies SET "totalDiamonds"=0`);
    // 3) strip equipped cosmetics mirrored on profiles + zero earning counters
    await c.query(`UPDATE user_profiles SET
      "entryEffectUrl"=NULL, "entryAnimationUrl"=NULL, "roomCardUrl"=NULL,
      "vipBadgeUrl"=NULL, "levelBadgeUrl"=NULL, "hostBadgeUrl"=NULL, "nameColor"=NULL,
      "totalReceivedDiamonds"=0, "totalSentCoins"=0`);
    // 4) strip per-room equipped cosmetics
    await c.query(`UPDATE rooms SET "backgroundUrl"=NULL, "backgroundEquippedById"=NULL,
      "roomCardUrl"=NULL, "roomCardEquippedById"=NULL`);
    // 5) empty ledgers + ownership tables (one statement handles mutual FKs)
    const present = [];
    for (const t of TRUNCATE_TABLES) if (await exists(t)) present.push(t);
    if (present.length) await c.query(`TRUNCATE TABLE ${present.join(', ')} RESTART IDENTITY CASCADE`);
    await c.query('COMMIT');
    console.log('APPLIED: full reset committed. Truncated:', present.join(', '));
  } catch (e) {
    await c.query('ROLLBACK');
    console.error('ROLLED BACK:', e.message);
    process.exit(1);
  }
  console.log('AFTER', JSON.stringify(await snap(), null, 2));
  await c.end();
})().catch((e) => { console.error('ERR', e.message); process.exit(1); });
"""


def main() -> None:
    host, user, password, remote = deploy_settings()
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(host, username=user, password=password, timeout=30)
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sftp = client.open_sftp()
        with sftp.file("/tmp/jeho_full_reset.js", "w") as f:
            f.write(NODE_SCRIPT)
        sftp.close()
        env = "RESET_APPLY=1 " if APPLY else ""
        _in, out, err = client.exec_command(f"cd {remote} && {env}node /tmp/jeho_full_reset.js", timeout=120)
        print(out.read().decode("utf-8", errors="replace"))
        e = err.read().decode("utf-8", errors="replace")
        if e.strip():
            print("STDERR:", e[-2000:])
    finally:
        client.close()


if __name__ == "__main__":
    print(f"JEHO FULL reset — mode: {'APPLY' if APPLY else 'DRY RUN'}")
    main()
