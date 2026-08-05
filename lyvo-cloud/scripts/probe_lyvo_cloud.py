#!/usr/bin/env python3
import ssl
import sys
from pathlib import Path

import paramiko
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, _ = deploy_settings()
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)

cmds = [
    "pm2 jlist | python3 -c \"import sys,json; d=json.load(sys.stdin); print([(x['name'],x['pm2_env'].get('status'),x.get('pid')) for x in d if 'lyvo' in x['name']])\"",
    "curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:3910/",
    "curl -sS http://127.0.0.1:3910/api/docs-lite | head -8",
    "ls -la /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/ 2>&1 | head -8",
    "test -f /www/server/panel/vhost/nginx/cloud.adastra.bbs.tr.conf && echo NGINX_OK || echo NGINX_MISSING",
    "nginx -t 2>&1 | tail -8",
]
for cmd in cmds:
    print(">>", cmd[:90])
    _, o, e = c.exec_command(cmd, timeout=60)
    print(o.read().decode("utf-8", "replace")[-1000:])
    er = e.read().decode("utf-8", "replace")[-400:]
    if er.strip():
        print("E", er)

# If no SSL cert, try certbot for cloud domain
_, out, _ = c.exec_command(
    "test -f /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/fullchain.pem && echo HAS_CERT || echo NO_CERT",
    timeout=30,
)
cert_state = out.read().decode().strip()
print("CERT", cert_state)
if "NO_CERT" in cert_state:
    print("Issuing certbot for cloud.adastra.bbs.tr ...")
    # HTTP-01 via webroot used by panel
    cmd = (
        "which certbot || which bt; "
        "mkdir -p /www/wwwroot/java_node_ssl; "
        "certbot certonly --webroot -w /www/wwwroot/java_node_ssl "
        "-d cloud.adastra.bbs.tr --non-interactive --agree-tos "
        "--register-unsafely-without-email 2>&1 | tail -30"
    )
    _, o, e = c.exec_command(cmd, timeout=180)
    print(o.read().decode("utf-8", "replace")[-1500:])
    print(e.read().decode("utf-8", "replace")[-500:])
    # try copy from letsencrypt
    c.exec_command(
        "mkdir -p /www/server/panel/vhost/cert/cloud.adastra.bbs.tr; "
        "for d in /etc/letsencrypt/live/cloud.adastra.bbs.tr "
        "/www/server/panel/vhost/cert/cloud.adastra.bbs.tr; do true; done; "
        "if [ -f /etc/letsencrypt/live/cloud.adastra.bbs.tr/fullchain.pem ]; then "
        "  cp -L /etc/letsencrypt/live/cloud.adastra.bbs.tr/fullchain.pem "
        "    /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/; "
        "  cp -L /etc/letsencrypt/live/cloud.adastra.bbs.tr/privkey.pem "
        "    /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/; "
        "  nginx -t && nginx -s reload; "
        "  echo COPIED_LE; "
        "fi",
        timeout=60,
    )

c.close()

ctx = ssl._create_unverified_context()
for url in [
    "http://cloud.adastra.bbs.tr/",
    "https://cloud.adastra.bbs.tr/",
    f"http://{HOST}/",
]:
    try:
        r = urllib.request.urlopen(
            urllib.request.Request(url, headers={"Host": "cloud.adastra.bbs.tr"}),
            timeout=15,
            context=ctx if url.startswith("https") else None,
        )
        print(url, "->", r.status, r.headers.get("content-type"), "len", len(r.read(200)))
    except Exception as ex:
        print(url, "->", type(ex).__name__, str(ex)[:120])
