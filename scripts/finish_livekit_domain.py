#!/usr/bin/env python3
"""Finish LiveKit domain: DB provider/url + public WSS check."""
from __future__ import annotations

import json
import re
import socket
import ssl
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
DOMAIN = "voice.adastra.bbs.tr"
WSS_URL = f"wss://{DOMAIN}"


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

    # 1) DB patch via psql (credentials from .env)
    code, out, err = run(
        client,
        f"""
set -e
cd {REMOTE_DIR}
set -a
. ./.env
set +a
export PGPASSWORD="$DB_PASSWORD"
# env uses DB_USERNAME / DB_DATABASE
SQL=$(cat <<'EOS'
UPDATE app_settings
SET value = jsonb_set(
  COALESCE(value::jsonb, '{{}}'::jsonb),
  '{{url}}',
  '"wss://voice.adastra.bbs.tr"'::jsonb,
  true
)::text
WHERE key = 'livekit_server_config';

INSERT INTO app_settings (key, value, description)
SELECT 'livekit_server_config',
       '{{"url":"wss://voice.adastra.bbs.tr","updatedAt":"now"}}',
       'LiveKit self-hosted credentials'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE key = 'livekit_server_config');

UPDATE app_settings
SET value = jsonb_set(
  COALESCE(value::jsonb, '{{}}'::jsonb),
  '{{provider}}',
  '"livekit"'::jsonb,
  true
)::text
WHERE key = 'voice_rtc_config';

INSERT INTO app_settings (key, value, description)
SELECT 'voice_rtc_config',
       '{{"provider":"livekit"}}',
       'Voice RTC provider'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE key = 'voice_rtc_config');

SELECT key, left(value, 160) FROM app_settings
WHERE key IN ('livekit_server_config','voice_rtc_config');
EOS
)
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_DATABASE" -v ON_ERROR_STOP=1 -c "$SQL" 2>&1 || \\
psql -h 127.0.0.1 -p 5432 -U "$DB_USERNAME" -d "$DB_DATABASE" -v ON_ERROR_STOP=1 <<'EOS2'
UPDATE app_settings SET value = (
  CASE WHEN value IS NULL OR value = '' THEN '{{"url":"wss://voice.adastra.bbs.tr"}}'
  ELSE value END
) WHERE key='livekit_server_config';
EOS2
""",
        timeout=60,
    )
    print("psql attempt1:\n", out[-2000:], err[-500:])

    # Safer python without psycopg2: use psql single commands
    code, out, err = run(
        client,
        f"""
python3 - <<'PY'
import json, os, subprocess, shlex
from pathlib import Path
env = {{}}
for line in Path({REMOTE_DIR!r}).joinpath('.env').read_text(errors='replace').splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k, _, v = line.partition('=')
    env[k.strip()] = v.strip().strip('"').strip("'")
os.environ['PGPASSWORD'] = env.get('DB_PASSWORD','')
user = env.get('DB_USERNAME') or env.get('DB_USER') or 'postgres'
db = env.get('DB_DATABASE') or env.get('DB_NAME') or 'auralive'
host = env.get('DB_HOST') or '127.0.0.1'
port = env.get('DB_PORT') or '5432'

def psql(sql):
    cmd = ['psql', '-h', host, '-p', str(port), '-U', user, '-d', db, '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', sql]
    p = subprocess.run(cmd, capture_output=True, text=True)
    print('SQL', sql[:80], '->', p.returncode, p.stdout.strip()[:300], p.stderr.strip()[:300])
    return p.returncode, p.stdout, p.stderr

# discover table
code,out,err = psql("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename ILIKE '%setting%';")
print('tables', out)

table = 'app_settings'
code,_,_ = psql(f"SELECT to_regclass('public.{{table}}');")
# fetch current
code,out,err = psql(f"SELECT value FROM {{table}} WHERE key='livekit_server_config';")
raw = out.strip()
try:
    cfg = json.loads(raw) if raw else {{}}
except Exception:
    cfg = {{}}
cfg['url'] = 'wss://voice.adastra.bbs.tr'
cfg['updatedAt'] = __import__('datetime').datetime.utcnow().isoformat()+'Z'
# preserve keys without printing
payload = json.dumps(cfg).replace("'", "''")
if raw:
    psql(f"UPDATE {{table}} SET value='{{payload}}' WHERE key='livekit_server_config';")
else:
    psql(f"INSERT INTO {{table}} (key,value,description) VALUES ('livekit_server_config','{{payload}}','LiveKit');")

code,out,err = psql(f"SELECT value FROM {{table}} WHERE key='voice_rtc_config';")
raw = out.strip()
try:
    cfg = json.loads(raw) if raw else {{}}
except Exception:
    cfg = {{}}
cfg['provider'] = 'livekit'
cfg['updatedAt'] = __import__('datetime').datetime.utcnow().isoformat()+'Z'
payload = json.dumps(cfg).replace("'", "''")
if raw:
    psql(f"UPDATE {{table}} SET value='{{payload}}' WHERE key='voice_rtc_config';")
else:
    psql(f"INSERT INTO {{table}} (key,value,description) VALUES ('voice_rtc_config','{{payload}}','Voice RTC');")

code,out,err = psql(
    f"SELECT key, left(value,120) FROM {{table}} WHERE key IN ('livekit_server_config','voice_rtc_config');"
)
print('result\\n', out)

# login admin
ident = env.get('ADMIN_EMAIL') or 'admin@auralive.com'
pw = env.get('ADMIN_PASSWORD') or ''
import urllib.request
body=json.dumps({{'identifier': ident, 'password': pw}}).encode()
req=urllib.request.Request('http://127.0.0.1:3000/api/v1/auth/login', data=body,
    headers={{'Content-Type':'application/json'}}, method='POST')
try:
    data=json.loads(urllib.request.urlopen(req, timeout=20).read().decode())
except Exception as e:
    print('login fail', e)
    if hasattr(e,'read'): print(e.read().decode()[:400])
    data={{}}
tok = data.get('accessToken') or data.get('token') or (data.get('data') or {{}}).get('accessToken') or (data.get('data') or {{}}).get('token')
print('login', bool(tok), 'keys', list(data.keys())[:10])
if tok:
    body=json.dumps({{'url':'wss://voice.adastra.bbs.tr','provider':'livekit'}}).encode()
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings',
        data=body, headers={{'Authorization':'Bearer '+tok,'Content-Type':'application/json'}}, method='PATCH')
    try:
        print('patch', urllib.request.urlopen(req, timeout=20).read().decode()[:400])
    except Exception as e:
        print('patch fail', e)
        if hasattr(e,'read'): print(e.read().decode()[:400])
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings',
        headers={{'Authorization':'Bearer '+tok}})
    try:
        print('get', urllib.request.urlopen(req, timeout=20).read().decode()[:400])
    except Exception as e:
        print('get fail', e)
PY
""",
        timeout=90,
    )
    print(out)
    if err.strip():
        print("ERR", err[-800:])

    run(client, "pm2 restart auralive-api --update-env", timeout=60)
    time.sleep(4)
    code, out, _ = run(client, "curl -s http://127.0.0.1:3000/api/v1/config/voice-rtc; echo")
    print("public config:", out)

    # Cloudflare 403 detail
    code, out, _ = run(
        client,
        "curl -sI https://voice.adastra.bbs.tr/ | head -30; echo '---'; curl -s https://voice.adastra.bbs.tr/ | head -c 500; echo",
    )
    print("through CF:\n", out)

    # Hint: check if other adastra hosts are DNS-only
    code, out, _ = run(
        client,
        "getent hosts voice.adastra.bbs.tr api.adnova.bbs.tr chats.adastra.bbs.tr 2>/dev/null; "
        "dig +short voice.adastra.bbs.tr A 2>/dev/null; dig +short api.adnova.bbs.tr A 2>/dev/null",
    )
    print("dns from server:\n", out)

    client.close()

    # Local origin connect success summary
    try:
        ctx = ssl._create_unverified_context()
        with socket.create_connection((HOST, 443), timeout=10) as raw:
            with ctx.wrap_socket(raw, server_hostname=DOMAIN) as s:
                s.sendall(
                    f"GET / HTTP/1.1\r\nHost: {DOMAIN}\r\nConnection: close\r\n\r\n".encode()
                )
                data = s.recv(200).decode("latin1")
                print("origin from agent:", data.split("\\r\\n")[0])
    except Exception as e:
        print("origin from agent fail", e)


if __name__ == "__main__":
    main()
