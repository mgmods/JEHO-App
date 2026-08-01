"""Upsert Sham Cash accountId in production app_settings."""
from __future__ import annotations

import json
import os
import sys

import paramiko

HOST = os.environ.get("JEHO_DEPLOY_HOST", "79.143.180.50")
USER = os.environ.get("JEHO_DEPLOY_USER", "root")
PASSWORD = os.environ.get("JEHO_DEPLOY_PASSWORD", "")
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
  const key = 'sham_cash_config';
  const r = await c.query('SELECT value FROM app_settings WHERE key=$1', [key]);
  let cfg = {
    enabled: true,
    whatsapp: '',
    displayName: 'شام كاش',
    accountName: '',
    accountId: '',
    instructions: '',
  };
  if (r.rows[0] && r.rows[0].value) {
    try { cfg = { ...cfg, ...JSON.parse(r.rows[0].value) }; } catch (e) {}
  }
  cfg.accountId = process.env.SHAM_CASH_ACCOUNT_ID || '';
  cfg.enabled = true;
  cfg.updatedAt = new Date().toISOString();
  const encoded = JSON.stringify(cfg);
  if (r.rows[0]) {
    await c.query('UPDATE app_settings SET value=$1 WHERE key=$2', [encoded, key]);
  } else {
    await c.query(
      'INSERT INTO app_settings (id, key, value, description, "createdAt", "updatedAt") VALUES (gen_random_uuid(), $1, $2, $3, NOW(), NOW())',
      [key, encoded, 'Sham Cash (Syria) account + WhatsApp manual recharge']
    );
  }
  console.log(JSON.stringify({
    ok: true,
    accountId: cfg.accountId,
    accountName: cfg.accountName || null,
    whatsapp: cfg.whatsapp || null,
  }));
  await c.end();
})().catch((e) => { console.error(e); process.exit(1); });
"""


def main() -> None:
    if not PASSWORD:
        raise SystemExit("JEHO_DEPLOY_PASSWORD required")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=45)
    try:
        sftp = client.open_sftp()
        remote = "/www/wwwroot/api.adnova.bbs.tr/.tmp_set_sham_cash_account.js"
        with sftp.file(remote, "w") as f:
            f.write(REMOTE_JS)
        sftp.close()
        cmd = (
            f"cd /www/wwwroot/api.adnova.bbs.tr && "
            f"SHAM_CASH_ACCOUNT_ID={ACCOUNT_ID} node .tmp_set_sham_cash_account.js"
        )
        _, stdout, stderr = client.exec_command(cmd, timeout=60)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        sys.stdout.write(out)
        if err:
            sys.stderr.write(err)
        code = stdout.channel.recv_exit_status()
        client.exec_command(f"rm -f {remote}", timeout=30)
        raise SystemExit(code)
    finally:
        client.close()


if __name__ == "__main__":
    main()
