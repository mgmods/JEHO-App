#!/usr/bin/env python3
"""Mint LiveKit JWT via backend node_modules + open WSS to voice domain."""
from __future__ import annotations

import sys
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
from pathlib import Path
import json, subprocess, os, time, textwrap

remote = Path({REMOTE_DIR!r})
env = {{}}
for line in remote.joinpath('.env').read_text(encoding='utf-8-sig', errors='replace').splitlines():
    line=line.strip()
    if not line or line.startswith('#') or '=' not in line: continue
    k,_,v=line.partition('=')
    env[k.strip()]=v.strip().strip('"').strip("'")

# find livekit-server-sdk
candidates = [
    remote / 'node_modules' / 'livekit-server-sdk',
    remote / 'dist' / '..' / 'node_modules' / 'livekit-server-sdk',
]
nm = None
for c in candidates:
    if c.resolve().exists():
        nm = str(c.resolve().parent)
        break
if not nm:
    # search
    p = subprocess.run(['find', str(remote), '-maxdepth', '3', '-type', 'd', '-name', 'livekit-server-sdk'],
                       capture_output=True, text=True)
    if p.stdout.strip():
        nm = str(Path(p.stdout.strip().splitlines()[0]).parent)
print('node_modules_parent', nm)

node = textwrap.dedent('''
const {{ AccessToken }} = require('livekit-server-sdk');
(async () => {{
  const at = new AccessToken(process.env.LK_KEY, process.env.LK_SECRET, {{ identity: 'probe-cli', ttl: '2m' }});
  at.addGrant({{ roomJoin: true, room: 'domain_probe_room', canPublish: false, canSubscribe: true }});
  const jwt = await at.toJwt();
  process.stdout.write(String(jwt));
}})().catch(e => {{ console.error('TOKEN_FAIL', e); process.exit(1); }});
''')
Path('/tmp/mk_lk_token.js').write_text(node)
p = subprocess.run(
    ['node', '/tmp/mk_lk_token.js'],
    capture_output=True, text=True,
    env={{**os.environ, 'LK_KEY': env['LIVEKIT_API_KEY'], 'LK_SECRET': env['LIVEKIT_API_SECRET'],
          'NODE_PATH': nm or ''}},
    cwd=str(remote),
)
token = (p.stdout or '').strip()
print('token_ok', bool(token and token.count('.')==2), 'stderr', (p.stderr or '')[:160])
if not (token and token.count('.')==2):
    raise SystemExit(2)

try:
    import websocket
except Exception:
    subprocess.check_call(['pip3', 'install', '--break-system-packages', '-q', 'websocket-client'])
    import websocket

# LiveKit WebSocket URL patterns used by SDK
urls = [
    'wss://voice.adastra.bbs.tr/rtc?access_token=' + token + '&auto_subscribe=1&sdk=js&version=2.0.0&protocol=12',
    'wss://voice.adastra.bbs.tr?access_token=' + token,
]
for url in urls:
    try:
        ws = websocket.create_connection(url, timeout=12, header=['Origin: https://voice.adastra.bbs.tr'])
        print('OPEN', url.split('?')[0], 'ok')
        try:
            ws.settimeout(3)
            msg = ws.recv()
            print('  recv', type(msg).__name__, len(msg) if msg is not None else 0)
        except Exception as e:
            print('  recv', type(e).__name__, str(e)[:80])
        ws.close()
    except Exception as e:
        print('FAIL', url.split('?')[0], type(e).__name__, str(e)[:120])
print('public_cfg')
import urllib.request
print(urllib.request.urlopen('http://127.0.0.1:3000/api/v1/config/voice-rtc', timeout=10).read().decode())
PY
""",
        timeout=90,
    )
    print(out)
    if err.strip():
        print(err[-500:])
    client.close()


if __name__ == "__main__":
    main()
