#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko
sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
HOST, USER, PASSWORD, _ = deploy_settings()
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
_i, o, e = c.exec_command(
    "ufw allow 50000:50100/udp; ufw status | grep -E '7880|7881|50000' || true",
    timeout=60,
)
print(o.read().decode())
print(e.read().decode()[:200])
c.close()
