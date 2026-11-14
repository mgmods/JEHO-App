#!/usr/bin/env python3
"""Audit one production user by publicId (SSH + node/pg on API host)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
import paramiko

PUBLIC_ID = sys.argv[1] if len(sys.argv) > 1 else "48067367"

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
const publicId = process.env.AUDIT_PUBLIC_ID || '48067367';

(async () => {
  const c = new Client({
    host: process.env.DB_HOST || process.env.POSTGRES_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || process.env.POSTGRES_PORT || 5432),
    user: process.env.DB_USER || process.env.DB_USERNAME || process.env.POSTGRES_USER,
    password: process.env.DB_PASSWORD || process.env.DB_PASS || process.env.POSTGRES_PASSWORD,
    database: process.env.DB_NAME || process.env.DB_DATABASE || process.env.POSTGRES_DB,
  });
  await c.connect();

  let u = await c.query(
    `SELECT id, "publicId", username, "displayName", status, "createdAt"
     FROM users WHERE "publicId" = $1 OR username = $1 LIMIT 5`,
    [publicId],
  );
  if (!u.rows.length) {
    u = await c.query(
      `SELECT id, "publicId", username, "displayName", status, "createdAt"
       FROM users WHERE id::text = $1 LIMIT 5`,
      [publicId],
    );
  }
  console.log('USERS', JSON.stringify(u.rows, null, 2));
  if (!u.rows.length) {
    await c.end();
    return;
  }
  const uid = u.rows[0].id;

  const w = await c.query(
    `SELECT coins, diamonds, "agencyDiamonds", "traderDiamonds", "gamePoints",
            "totalRecharged", "silverCoins"
     FROM wallets WHERE "userId" = $1`,
    [uid],
  );
  console.log('WALLET', JSON.stringify(w.rows, null, 2));

  const sums = await c.query(
    `SELECT type::text as type, currency::text as currency,
            COALESCE("referenceType",'') as ref,
            COUNT(*)::int as n,
            COALESCE(SUM(amount),0)::text as sum_amount
     FROM wallet_transactions WHERE "userId" = $1
     GROUP BY type, currency, COALESCE("referenceType",'')
     ORDER BY ABS(SUM(amount)) DESC
     LIMIT 40`,
    [uid],
  );
  console.log('SUMS', JSON.stringify(sums.rows, null, 2));

  const pos = await c.query(
    `SELECT type::text, currency::text, amount::text, COALESCE("referenceType",'') as ref,
            LEFT(COALESCE(description,''), 80) as description, "createdAt"
     FROM wallet_transactions
     WHERE "userId" = $1 AND amount > 0 AND currency = 'coins'
     ORDER BY amount DESC LIMIT 30`,
    [uid],
  );
  console.log('TOP_COIN_CREDITS', JSON.stringify(pos.rows, null, 2));

  const recent = await c.query(
    `SELECT type::text, currency::text, amount::text, "balanceAfter"::text,
            COALESCE("referenceType",'') as ref,
            LEFT(COALESCE(description,''), 80) as description, "createdAt"
     FROM wallet_transactions WHERE "userId" = $1
     ORDER BY "createdAt" DESC LIMIT 25`,
    [uid],
  );
  console.log('RECENT_TX', JSON.stringify(recent.rows, null, 2));

  const giftsIn = await c.query(
    `SELECT COUNT(*)::int as n,
            COALESCE(SUM("totalCoins"),0)::text as coins,
            COALESCE(SUM("diamondsAwarded"),0)::text as diamonds
     FROM gift_sends WHERE "receiverId" = $1`,
    [uid],
  );
  const giftsOut = await c.query(
    `SELECT COUNT(*)::int as n,
            COALESCE(SUM("totalCoins"),0)::text as coins,
            COALESCE(SUM("diamondsAwarded"),0)::text as diamonds
     FROM gift_sends WHERE "senderId" = $1`,
    [uid],
  );
  console.log('GIFTS_RECV', JSON.stringify(giftsIn.rows[0]));
  console.log('GIFTS_SENT', JSON.stringify(giftsOut.rows[0]));

  const recCols = await c.query(
    `SELECT column_name FROM information_schema.columns
     WHERE table_name = 'recharge_orders' ORDER BY ordinal_position`,
  );
  console.log('RECHARGE_COLS', recCols.rows.map((r) => r.column_name).join(','));

  const rec = await c.query(
    `SELECT * FROM recharge_orders WHERE "userId" = $1
     ORDER BY "createdAt" DESC LIMIT 20`,
    [uid],
  );
  console.log('RECHARGE', JSON.stringify(rec.rows, null, 2));
  console.log('RECHARGE_COUNT', rec.rowCount);

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
        with sftp.file("/tmp/jeho_user_audit.js", "w") as f:
            f.write(NODE_SCRIPT)
        sftp.close()
        cmd = (
            f"cd {remote} && AUDIT_PUBLIC_ID={PUBLIC_ID} "
            f"node /tmp/jeho_user_audit.js"
        )
        _stdin, stdout, stderr = client.exec_command(cmd, timeout=90)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        print(out)
        if err.strip():
            print("STDERR:", err[-2000:])
    finally:
        client.close()


if __name__ == "__main__":
    main()
