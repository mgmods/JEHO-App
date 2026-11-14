#!/usr/bin/env python3
import socket
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, _ = deploy_settings()
for name in ["cloud.adastra.bbs.tr", "voice.adastra.bbs.tr", "api.adnova.bbs.tr"]:
    try:
        ips = sorted({x[4][0] for x in socket.getaddrinfo(name, None)})
        print("DNS", name, ips)
    except Exception as e:
        print("DNS", name, "FAIL", e)

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)

cmds = r"""
set -e
echo '--- cert ---'
openssl x509 -in /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/fullchain.pem -noout -subject -dates 2>&1 | head -10
openssl x509 -in /www/server/panel/vhost/cert/cloud.adastra.bbs.tr/fullchain.pem -noout -text 2>&1 | grep -A1 'Subject Alternative Name' | head -5
echo '--- local ---'
curl -sS -o /dev/null -w 'direct3910=%{http_code}\n' http://127.0.0.1:3910/
curl -sS -o /dev/null -w 'host80=%{http_code}\n' -H 'Host: cloud.adastra.bbs.tr' http://127.0.0.1/
curl -skS -o /dev/null -w 'host443=%{http_code}\n' -H 'Host: cloud.adastra.bbs.tr' https://127.0.0.1/
echo '--- nginx server blocks ---'
grep -n 'server_name' /www/server/panel/vhost/nginx/cloud.adastra.bbs.tr.conf
echo '--- bootstrap email ---'
grep -E '^(BOOTSTRAP_EMAIL|PUBLIC_URL|PORT)=' /www/wwwroot/cloud.adastra.bbs.tr/.env || true
"""
_, o, e = c.exec_command(cmds, timeout=60)
print(o.read().decode("utf-8", "replace"))
print(e.read().decode("utf-8", "replace")[-500:])
c.close()
