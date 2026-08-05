"""Upload default nav SVG icons path config into production app_settings."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
ORIGIN = "https://api.adnova.bbs.tr"
BASE = f"{ORIGIN}/assets/nav-icons"


def pair(name: str) -> dict:
    return {
        "normal": f"{BASE}/ic_home_tab_{name}_normal.svg",
        "selected": f"{BASE}/ic_home_tab_{name}_selected.svg",
    }


def main() -> int:
    cfg = {
        "version": 1,
        "updatedAt": "2026-08-05T00:00:00.000Z",
        "party": pair("party"),
        "drama": pair("drama"),
        "games": pair("game"),
        "chat": pair("chat"),
        "me": pair("me"),
    }

    remote_js = r"""
const path = require('path');
const root = process.env.SEED_ROOT || process.cwd();
require(path.join(root, 'node_modules/dotenv')).config({ path: path.join(root, '.env') });
const { Client } = require(path.join(root, 'node_modules/pg'));
const fs = require('fs');
const cfg = JSON.parse(fs.readFileSync('/tmp/app_nav_icons.json', 'utf8'));

(async () => {
  const c = new Client({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 5432),
    user: process.env.DB_USER || process.env.DB_USERNAME,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME || process.env.DB_DATABASE,
  });
  await c.connect();
  const existing = await c.query(
    "select id, value from app_settings where key = 'app_nav_icons' limit 1",
  );
  if (existing.rows.length) {
    let prevVer = 0;
    try {
      prevVer = Math.max(0, Number(JSON.parse(existing.rows[0].value || '{}').version) || 0);
    } catch (e) {
      prevVer = 0;
    }
    cfg.version = prevVer + 1;
    cfg.updatedAt = new Date().toISOString();
    const next = JSON.stringify(cfg);
    await c.query(
      'update app_settings set value = $1, description = $2, "updatedAt" = NOW() where key = $3',
      [next, 'Default bottom tab icons (SVG)', 'app_nav_icons'],
    );
    console.log('updated app_nav_icons version=' + cfg.version);
  } else {
    cfg.version = 1;
    cfg.updatedAt = new Date().toISOString();
    await c.query(
      'insert into app_settings (id, key, value, description, "createdAt", "updatedAt") values (gen_random_uuid(), $1, $2, $3, NOW(), NOW())',
      ['app_nav_icons', JSON.stringify(cfg), 'Default bottom tab icons (SVG)'],
    );
    console.log('inserted app_nav_icons');
  }
  await c.end();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
"""

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=45)
    try:
        sftp = client.open_sftp()
        with sftp.file("/tmp/app_nav_icons.json", "w") as f:
            f.write(json.dumps(cfg, ensure_ascii=False))
        with sftp.file("/tmp/seed_nav_icons.js", "w") as f:
            f.write(remote_js)
        sftp.close()

        _, stdout, stderr = client.exec_command(
            f"cd {REMOTE_DIR} && SEED_ROOT={REMOTE_DIR} node /tmp/seed_nav_icons.js",
            timeout=60,
        )
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        code = stdout.channel.recv_exit_status()
        print(out)
        if err.strip():
            print(err)
        if code != 0:
            return code

        _, stdout, _ = client.exec_command(
            "curl -sS https://api.adnova.bbs.tr/api/v1/config/nav-icons | head -c 1500",
            timeout=30,
        )
        print(stdout.read().decode("utf-8", "replace"))
        _, stdout, _ = client.exec_command(
            "curl -sS -o /dev/null -w '%{http_code}' "
            "https://api.adnova.bbs.tr/assets/nav-icons/ic_home_tab_chat_normal.svg",
            timeout=30,
        )
        print("chat_normal_svg_http", stdout.read().decode("utf-8", "replace"))
    finally:
        client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
