#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko
sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings

HOST, USER, PASSWORD, _ = deploy_settings()
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
cmd = r"""
if command -v ufw >/dev/null; then
  ufw allow 7880/tcp || true
  ufw allow 7881/tcp || true
  ufw allow 50000:50100/udp || true
  ufw status | head -25
else
  echo 'ufw not found'
  iptables -I INPUT -p tcp --dport 7880 -j ACCEPT 2>/dev/null || true
  iptables -I INPUT -p tcp --dport 7881 -j ACCEPT 2>/dev/null || true
  iptables -I INPUT -p udp --dport 50000:50100 -j ACCEPT 2>/dev/null || true
fi
curl -sS -o /dev/null -w 'local_7880_http=%{http_code}\n' http://127.0.0.1:7880/ || true
"""
_i, o, e = client.exec_command(cmd, timeout=60)
print(o.read().decode("utf-8", "replace"))
print(e.read().decode("utf-8", "replace")[:200])
client.close()
