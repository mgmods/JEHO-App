#!/usr/bin/env python3
from __future__ import annotations
import sys
from pathlib import Path
import paramiko
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings
HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
ROOM = "567ca7e1-665b-4dde-909d-51fbce270792"

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
cmd = f"""
python3 - <<'PY'
import os, subprocess
from pathlib import Path
env={{}}
for line in Path({REMOTE_DIR!r}).joinpath('.env').read_text(encoding='utf-8-sig',errors='replace').splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,_,v=line.partition('=')
    env[k.strip()]=v.strip().strip('"').strip("'")
os.environ['PGPASSWORD']=env['DB_PASSWORD']
def psql(sql):
    p=subprocess.run(['psql','-h',env['DB_HOST'],'-p',env['DB_PORT'],'-U',env['DB_USERNAME'],'-d',env['DB_DATABASE'],'-c',sql],capture_output=True,text=True)
    print(sql[:80]); print(p.stdout or p.stderr)
psql("SELECT column_name FROM information_schema.columns WHERE table_name='seats' ORDER BY ordinal_position;")
psql("SELECT * FROM seats WHERE \\"roomId\\"='{ROOM}' LIMIT 20;")
psql("SELECT id, \\"hostId\\", \\"activeHostId\\", \\"viewerCount\\", status FROM rooms WHERE id='{ROOM}';")
# who is activeHost user name
psql("SELECT id, \\"displayName\\", username FROM users WHERE id=(SELECT \\"activeHostId\\" FROM rooms WHERE id='{ROOM}');")
PY
"""
_, stdout, stderr = client.exec_command(cmd, timeout=40)
print(stdout.read().decode())
print(stderr.read().decode()[:500])
client.close()
