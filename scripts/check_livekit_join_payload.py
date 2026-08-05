#!/usr/bin/env python3
"""Verify voice provider + force a room join payload for active agency room."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
TARGET_ROOM = "567ca7e1-665b-4dde-909d-51fbce270792"  # سوالفنا حفلات


def run(client, cmd, timeout=90):
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    return stdout.channel.recv_exit_status(), out, err


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
remote = Path({REMOTE_DIR!r})
env = {{}}
for line in remote.joinpath('.env').read_text(encoding='utf-8-sig', errors='replace').splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,_,v=line.partition('=')
    env[k.strip()]=v.strip().strip('"').strip("'")

print('ENV LIVEKIT_URL', env.get('LIVEKIT_URL'))
print('ENV VOICE_RTC', env.get('VOICE_RTC_PROVIDER'))
print('cfg', urllib.request.urlopen('http://127.0.0.1:3000/api/v1/config/voice-rtc', timeout=10).read().decode())

# public rooms presence - find open rooms with viewers via sql
os.environ['PGPASSWORD'] = env['DB_PASSWORD']
def psql(sql):
    p = subprocess.run(['psql','-h',env['DB_HOST'],'-p',env['DB_PORT'],'-U',env['DB_USERNAME'],'-d',env['DB_DATABASE'],'-t','-A','-c',sql], capture_output=True, text=True)
    return p.stdout.strip()

print('open_rooms')
print(psql("SELECT id, title, status, \\"viewerCount\\", \\"activeHostId\\" IS NOT NULL as has_host FROM rooms WHERE status='open' ORDER BY \\"viewerCount\\" DESC NULLS LAST LIMIT 8;"))
# column names may differ
print('open_alt')
print(psql("SELECT column_name FROM information_schema.columns WHERE table_name='rooms' AND column_name ILIKE '%viewer%' OR (table_name='rooms' AND column_name ILIKE '%host%') LIMIT 20;"))
print(psql("SELECT id, title, status FROM rooms WHERE title ILIKE '%سوالف%' OR id='{TARGET_ROOM}' LIMIT 5;".replace('{TARGET_ROOM}', {TARGET_ROOM!r})))

# Try login bot alosh-like and join
# Prefer existing bot from prior tests
for ident, pw in [
    (env.get('ADMIN_EMAIL'), env.get('ADMIN_PASSWORD')),
]:
    body=json.dumps({{'identifier': ident, 'password': pw}}).encode()
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/auth/login', data=body, headers={{'Content-Type':'application/json'}}, method='POST')
    try:
        data=json.loads(urllib.request.urlopen(req,timeout=20).read().decode())
    except Exception as e:
        print('login fail', e); continue
    inner=data.get('data') if isinstance(data.get('data'), dict) else data
    tok=inner.get('accessToken') or inner.get('token')
    print('admin_login', bool(tok))
    if not tok: continue
    # admin voice rtc
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings', headers={{'Authorization':'Bearer '+tok}})
    print('admin_voice', urllib.request.urlopen(req,timeout=15).read().decode()[:350])

# try token for room with seed test user if exists
# mint via direct service by logging in lk bot
for bot_ident, bot_pw in [('lk_bot_alosh','Test@cbcdd9e7'),('lk_test_8690','Test@cbcdd9e7')]:
  try:
    body=json.dumps({{'identifier': bot_ident, 'password': bot_pw}}).encode()
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/auth/login', data=body, headers={{'Content-Type':'application/json'}}, method='POST')
    data=json.loads(urllib.request.urlopen(req,timeout=15).read().decode())
    inner=data.get('data') if isinstance(data.get('data'), dict) else data
    tok=inner.get('accessToken') or inner.get('token')
    if not tok:
      print('bot login fail', bot_ident, list((inner or {{}}).keys())[:8]); continue
    print('bot', bot_ident, 'ok')
    # join room
    body=json.dumps({{}}).encode()
    req=urllib.request.Request('http://127.0.0.1:3000/api/v1/rooms/'+{TARGET_ROOM!r}+'/join', data=body, headers={{'Authorization':'Bearer '+tok,'Content-Type':'application/json'}}, method='POST')
    try:
        raw=urllib.request.urlopen(req,timeout=20).read().decode()
    except Exception as e:
        print('join fail', e)
        if hasattr(e,'read'): print(e.read().decode()[:300])
        continue
    j=json.loads(raw)
    d=j.get('data') or j
    print('JOIN voiceProvider=', d.get('voiceProvider'), 'livekitUrl=', d.get('livekitUrl'), 'livekitRoom=', d.get('livekitRoomName'), 'canPublish=', d.get('canPublish'), 'token_len=', len(d.get('token') or ''))
    # decode jwt payload without verify
    import base64
    tok=d.get('token') or ''
    if tok.count('.')==2:
      payload=tok.split('.')[1]
      payload += '=' * (-len(payload)%4)
      print('JWT', base64.urlsafe_b64decode(payload.encode()).decode()[:400])
  except Exception as e:
    print('bot err', bot_ident, e)

# list tables seats for who is seated
print('seats', psql("SELECT seat_index, user_id, status FROM seats WHERE room_id='"+{TARGET_ROOM!r}+"' AND user_id IS NOT NULL LIMIT 12;"))
PY
""",
        timeout=60,
    )
    print(out)
    if err:
        print(err[-800:])
    client.close()


if __name__ == "__main__":
    main()
