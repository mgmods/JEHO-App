"""Debug + fix Sham Cash config in production DB."""
from __future__ import annotations

import json
import os
import sys
import urllib.request

import paramiko

from deploy_secrets import deploy_settings

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HOST, USER, PASSWORD, _REMOTE_DIR = deploy_settings()
ACCOUNT_ID = "2c5e893402a9cd76d27d28b4b92605aa"

REMOTE_JS = r"""
require('dotenv').config();
const { Client } = require('pg');
(async () => {
  const c = new Client({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USERNAME || process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD,
    database: process.env.DB_DATABASE || process.env.DB_NAME || 'auralive',
  });
  await c.connect();
  const meta = {
    db: process.env.DB_DATABASE || process.env.DB_NAME || null,
    host: process.env.DB_HOST || null,
  };
  const key = 'sham_cash_config';
  const before = await c.query('SELECT key, value FROM app_settings WHERE key=$1', [key]);
  let beforeObj = null;
  if (before.rows[0]) {
    const v = before.rows[0].value;
    try { beforeObj = typeof v === 'string' ? JSON.parse(v) : v; } catch (e) { beforeObj = { raw: String(v).slice(0, 200) }; }
  }
  const cfg = {
    enabled: true,
    whatsapp: (beforeObj && beforeObj.whatsapp) || '',
    displayName: (beforeObj && beforeObj.displayName) || 'Sham Cash',
    accountName: (beforeObj && beforeObj.accountName) || 'Sham Cash',
    accountId: process.env.SHAM_CASH_ACCOUNT_ID || '',
    instructions:
      (beforeObj && beforeObj.instructions) ||
      'Pay via Sham Cash QR/account, then send proof on WhatsApp with order code.',
    updatedAt: new Date().toISOString(),
  };
  const encoded = JSON.stringify(cfg);
  if (before.rows[0]) {
    await c.query('UPDATE app_settings SET value=$1, "updatedAt"=NOW() WHERE key=$2', [encoded, key]);
  } else {
    await c.query(
      'INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt") VALUES (gen_random_uuid(), $1, $2, $3, NOW(), NOW())',
      [key, encoded, 'Sham Cash config']
    );
  }
  const after = await c.query('SELECT value FROM app_settings WHERE key=$1', [key]);
  let afterObj = null;
  try {
    const v = after.rows[0].value;
    afterObj = typeof v === 'string' ? JSON.parse(v) : v;
  } catch (e) {}
  console.log(JSON.stringify({
    meta,
    beforeAccountId: beforeObj && beforeObj.accountId ? String(beforeObj.accountId) : null,
    afterAccountId: afterObj && afterObj.accountId ? String(afterObj.accountId) : null,
    afterEnabled: !!(afterObj && afterObj.enabled),
  }));
  await c.end();
})().catch((e) => { console.error(String(e)); process.exit(1); });
"""


def main() -> None:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=45)
    try:
        sftp = client.open_sftp()
        remote = "/www/wwwroot/api.adnova.bbs.tr/.tmp_fix_sham_cash.js"
        with sftp.file(remote, "w") as f:
            f.write(REMOTE_JS)
        sftp.close()
        cmd = (
            f"cd /www/wwwroot/api.adnova.bbs.tr && "
            f"SHAM_CASH_ACCOUNT_ID={ACCOUNT_ID} node .tmp_fix_sham_cash.js"
        )
        _, stdout, stderr = client.exec_command(cmd, timeout=60)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        print(out.strip())
        if err.strip():
            print("ERR", err.strip()[:500])
        code = stdout.channel.recv_exit_status()
        client.exec_command(f"rm -f {remote}", timeout=30)
        if code != 0:
            raise SystemExit(code)
    finally:
        client.close()

    req = urllib.request.Request(
        "https://api.adnova.bbs.tr/api/v1/config/sham-cash",
        headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    data = payload.get("data") or {}
    print(
        "API",
        {
            "enabled": data.get("enabled"),
            "accountId": data.get("accountId"),
            "accountName": data.get("accountName"),
            "whatsapp": data.get("whatsapp"),
        },
    )


if __name__ == "__main__":
    main()
