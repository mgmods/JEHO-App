#!/usr/bin/env python3
"""Fix LiveKit API key in DB (strip wrong email), verify WSS connect."""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()


def run(client, cmd, timeout=120):
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)

    code, out, err = run(
        client,
        f"""
python3 - <<'PY'
import json, os, subprocess, urllib.request
from pathlib import Path

def load_env(path):
    env = {{}}
    text = Path(path).read_text(encoding='utf-8-sig', errors='replace')
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, _, v = line.partition('=')
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env

env = load_env({REMOTE_DIR!r} + '/.env')
os.environ['PGPASSWORD'] = env['DB_PASSWORD']
user, db, host, port = env['DB_USERNAME'], env['DB_DATABASE'], env['DB_HOST'], env['DB_PORT']
real_key = env.get('LIVEKIT_API_KEY') or 'APIJEHOVOICE'
real_secret = env.get('LIVEKIT_API_SECRET') or ''
assert real_key and real_secret and '@' not in real_key

def psql(sql):
    p = subprocess.run(
        ['psql','-h',host,'-p',str(port),'-U',user,'-d',db,'-v','ON_ERROR_STOP=1','-t','-A','-c',sql],
        capture_output=True, text=True)
    if p.returncode != 0:
        print('psql_fail', p.stderr[:200])
    return p.stdout.strip()

body_login = json.dumps({{'identifier': env['ADMIN_EMAIL'], 'password': env['ADMIN_PASSWORD']}}).encode()
req = urllib.request.Request('http://127.0.0.1:3000/api/v1/auth/login', data=body_login,
    headers={{'Content-Type':'application/json'}}, method='POST')
data = json.loads(urllib.request.urlopen(req, timeout=20).read().decode())
inner = data.get('data') if isinstance(data.get('data'), dict) else data
tok = (inner or {{}}).get('accessToken') or (inner or {{}}).get('token')
print('login', bool(tok))

# Prefer API patch with real key+secret + url + provider
payload = {{
    'url': 'wss://voice.adastra.bbs.tr',
    'apiKey': real_key,
    'apiSecret': real_secret,
    'provider': 'livekit',
}}
req = urllib.request.Request(
    'http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings',
    data=json.dumps(payload).encode(),
    headers={{'Authorization':'Bearer '+tok,'Content-Type':'application/json'}},
    method='PATCH',
)
resp = json.loads(urllib.request.urlopen(req, timeout=20).read().decode())
d = resp.get('data') or resp
print('ready', d.get('ready'), 'provider', d.get('provider'), 'url', d.get('url'),
      'apiKeyConfigured', d.get('apiKeyConfigured'), 'apiKey_masked', d.get('apiKey'),
      'source', d.get('configSource'))

# public config
print('public', urllib.request.urlopen('http://127.0.0.1:3000/api/v1/config/voice-rtc', timeout=10).read().decode())

# Token mint test via room join is heavy; mint via livekit-server-sdk if node available
PY
""",
        timeout=40,
    )
    print(out)
    if err.strip() and "Deprecation" not in err:
        print(err[-300:])

    # Connect probe with real token using Python livekit if installed in container/host
    code, out, err = run(
        client,
        f"""
python3 - <<'PY'
import json, os, urllib.request, subprocess, time
from pathlib import Path

def load_env(path):
    env = {{}}
    for line in Path(path).read_text(encoding='utf-8-sig', errors='replace').splitlines():
        line=line.strip()
        if not line or line.startswith('#') or '=' not in line: continue
        k,_,v=line.partition('=')
        env[k.strip()]=v.strip().strip('"').strip("'")
    return env
env = load_env({REMOTE_DIR!r}+'/.env')

# Install livekit client quietly in venv
venv='/tmp/lkvenv'
subprocess.run(['python3','-m','venv',venv], check=False)
pip=venv+'/bin/pip'
py=venv+'/bin/python'
subprocess.run([pip,'install','-q','livekit','livekit-api'], check=False)

# Create token with livekit api
code = r'''
import asyncio, os
from livekit import api, rtc

URL = "wss://voice.adastra.bbs.tr"
KEY = os.environ["LK_KEY"]
SECRET = os.environ["LK_SECRET"]

async def main():
    token = api.AccessToken(KEY, SECRET) \\
        .with_identity("domain_probe") \\
        .with_name("domain_probe") \\
        .with_grants(api.VideoGrants(room_join=True, room="domain_probe_room", can_publish=False, can_subscribe=True)) \\
        .to_jwt()
    room = rtc.Room()
    try:
        await room.connect(URL, token)
        print("CONNECT_OK peers", len(room.remote_participants), "name", room.name)
        await asyncio.sleep(2)
        await room.disconnect()
        print("DISCONNECT_OK")
    except Exception as e:
        print("CONNECT_FAIL", type(e).__name__, e)

asyncio.run(main())
'''
open('/tmp/lk_connect_test.py','w').write(code)
import os as _os
_os.environ['LK_KEY']=env['LIVEKIT_API_KEY']
_os.environ['LK_SECRET']=env['LIVEKIT_API_SECRET']
p=subprocess.run([py,'/tmp/lk_connect_test.py'], capture_output=True, text=True, env={{**_os.environ}}, timeout=40)
print(p.stdout)
print(p.stderr[-500:] if p.stderr else '')
print('exit', p.returncode)
PY
""",
        timeout=120,
    )
    print(out)
    if err.strip():
        print(err[-500:])

    code, out, _ = run(
        client,
        "pm2 restart auralive-api --update-env >/dev/null; sleep 4; "
        "curl -s http://127.0.0.1:3000/api/v1/config/voice-rtc; echo",
    )
    print("after restart:", out)

    client.close()


if __name__ == "__main__":
    main()
