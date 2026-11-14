#!/usr/bin/env python3
"""Deploy LYVO Cloud as a SEPARATE site on cloud.adastra.bbs.tr (not JEHO API).

Does not touch Nest backend or /admin dashboard.
Requires: scripts/deploy.local.env (same SSH host is OK; remote path is different).
"""
from __future__ import annotations

import os
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

LYVO = ROOT / "lyvo-cloud"
REMOTE_LYVO = "/www/wwwroot/cloud.adastra.bbs.tr"
DOMAIN = "cloud.adastra.bbs.tr"
PM2_NAME = "lyvo-cloud"
NGINX_SITE = f"/www/server/panel/vhost/nginx/{DOMAIN}.conf"
CERT_DIR = f"/www/server/panel/vhost/cert/{DOMAIN}"


def run_local(cmd: list[str], cwd: Path | None = None) -> None:
    print("+", " ".join(cmd))
    # Windows: npm is npm.cmd — shell=True so PATH resolves it.
    subprocess.check_call(
        cmd,
        cwd=str(cwd or LYVO),
        shell=(os.name == "nt"),
    )


def main() -> int:
    host, user, password, _jeho_remote = deploy_settings()

    # Build SPA
    if not (LYVO / "node_modules").exists():
        run_local(["npm", "install"], LYVO)
    if not (LYVO / "web" / "node_modules").exists():
        run_local(["npm", "install"], LYVO / "web")
    run_local(["npm", "run", "build"], LYVO)

    buf_path = Path(tempfile.gettempdir()) / "lyvo-cloud-deploy.tgz"
    if buf_path.exists():
        buf_path.unlink()

    exclude = {"node_modules", "data", ".git", "web/node_modules"}
    with tarfile.open(buf_path, "w:gz") as tar:
        for p in LYVO.rglob("*"):
            if not p.is_file():
                continue
            rel = p.relative_to(LYVO).as_posix()
            if any(part in rel.split("/") for part in ("node_modules", "data", ".git")):
                continue
            if rel.startswith("web/") and "/node_modules/" in rel:
                continue
            # Skip src of web after build? keep package.json + dist + server
            if rel.startswith("web/src/") or rel == "web/index.html":
                continue
            tar.add(p, arcname=f"lyvo-cloud/{rel}")

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(host, username=user, password=password, timeout=30)

    def sh(cmd: str, timeout: int = 300) -> str:
        print("remote:", cmd[:120])
        _, o, e = client.exec_command(cmd, timeout=timeout)
        out = o.read().decode("utf-8", "replace")
        err = e.read().decode("utf-8", "replace")
        code = o.channel.recv_exit_status()
        if code != 0:
            print(err[-2000:])
            raise RuntimeError(f"remote exit {code}: {cmd}")
        return out

    sh(f"mkdir -p {REMOTE_LYVO}")
    sftp = client.open_sftp()
    remote_tgz = "/tmp/lyvo-cloud-deploy.tgz"
    sftp.put(str(buf_path), remote_tgz)
    sftp.close()

    sh(
        f"tar -xzf {remote_tgz} -C /tmp && "
        f"rsync -a --delete "
        f"--exclude .user.ini --exclude data/ "
        f"/tmp/lyvo-cloud/ {REMOTE_LYVO}/ || true; "
        f"test -f {REMOTE_LYVO}/package.json"
    )

    # env — seed LYVO .env; copy Fourthwall keys from JEHO API .env if present
    env_path = f"{REMOTE_LYVO}/.env"
    jeho_env = f"{_jeho_remote}/.env"
    sh(
        f"test -f {env_path} || cp {REMOTE_LYVO}/.env.example {env_path}; "
        f"grep -q PUBLIC_URL {env_path} || echo PUBLIC_URL=https://{DOMAIN} >> {env_path}; "
        # pull card payment credentials from JEHO (same Fourthwall shop as the Android app)
        f"if [ -f {jeho_env} ]; then "
        f"  for k in FOURTHWALL_API_USER FOURTHWALL_API_PASSWORD FOURTHWALL_STOREFRONT_TOKEN "
        f"    FOURTHWALL_SHOP_DOMAIN FOURTHWALL_WEBHOOK_SECRET; do "
        f"    v=$(grep -E \"^$k=\" {jeho_env} | head -1 | cut -d= -f2- | tr -d '\\r' | sed \"s/^[\\\"']//;s/[\\\"']$//\"); "
        f"    if [ -n \"$v\" ]; then "
        f"      if grep -qE \"^$k=\" {env_path}; then "
        f"        sed -i \"s|^$k=.*|$k=$v|\" {env_path}; "
        f"      else echo \"$k=$v\" >> {env_path}; fi; "
        f"    fi; "
        f"  done; "
        f"  echo COPIED_FOURTHWALL_FROM_JEHO; "
        f"fi"
    )

    sh(f"cd {REMOTE_LYVO} && npm install --omit=dev")

    # nginx
    conf_local = (LYVO / "deploy" / "cloud.adastra.bbs.tr.conf").read_text(encoding="utf-8")
    sftp = client.open_sftp()
    with sftp.file("/tmp/cloud.adastra.bbs.tr.conf", "w") as f:
        f.write(conf_local)
    sftp.close()

    sh(
        f"mkdir -p {CERT_DIR}; "
        # Prefer reuse voice cert SAN if panel cert missing — user should issue cert for cloud.*
        f"if [ ! -f {CERT_DIR}/fullchain.pem ]; then "
        f"  if [ -f /www/server/panel/vhost/cert/voice.adastra.bbs.tr/fullchain.pem ]; then "
        f"    echo 'NOTE: issue LE cert for cloud.adastra.bbs.tr in panel; temp copy voice cert fails SAN'; "
        f"  fi; "
        f"fi; "
        f"cp /tmp/cloud.adastra.bbs.tr.conf {NGINX_SITE}; "
        f"nginx -t && (nginx -s reload || systemctl reload nginx)"
    )

    # PM2
    sh(
        f"cd {REMOTE_LYVO} && "
        f"(pm2 describe {PM2_NAME} >/dev/null 2>&1 && pm2 restart {PM2_NAME} --update-env || "
        f"pm2 start server/index.mjs --name {PM2_NAME} --cwd {REMOTE_LYVO}) && "
        f"pm2 save"
    )

    print(f"\nOK — LYVO Cloud: https://{DOMAIN}")
    print("Separate from JEHO API /admin and from LiveKit voice.adastra.bbs.tr")
    client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
