#!/usr/bin/env python3
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE = deploy_settings()
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
script = f"""
set +e
LY=/www/wwwroot/cloud.adastra.bbs.tr/.env
JE={REMOTE}/.env
echo 'lyvo keys:'
for k in FOURTHWALL_API_USER FOURTHWALL_API_PASSWORD FOURTHWALL_STOREFRONT_TOKEN FOURTHWALL_SHOP_DOMAIN FOURTHWALL_WEBHOOK_SECRET; do
  if grep -qE "^$k=.+" "$LY" 2>/dev/null; then echo "  $k=SET"; else echo "  $k=EMPTY"; fi
done
echo 'jeho keys:'
for k in FOURTHWALL_API_USER FOURTHWALL_API_PASSWORD FOURTHWALL_STOREFRONT_TOKEN FOURTHWALL_SHOP_DOMAIN FOURTHWALL_WEBHOOK_SECRET; do
  if grep -qE "^$k=.+" "$JE" 2>/dev/null; then echo "  $k=SET"; else echo "  $k=EMPTY"; fi
done
# show shop domain only (not secret)
grep -E '^FOURTHWALL_SHOP_DOMAIN=' "$LY" 2>/dev/null | sed 's/\\r//' || true
curl -sS -o /dev/null -w 'lyvo_http=%{{http_code}}\\n' http://127.0.0.1:3910/
"""
_, o, e = c.exec_command(script, timeout=40)
print(o.read().decode())
err = e.read().decode()
if err.strip():
    print(err[-400:])
c.close()
