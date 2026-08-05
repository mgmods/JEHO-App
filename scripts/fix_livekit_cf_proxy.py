#!/usr/bin/env python3
"""Fix CF Flexible redirect loop + set provider/url correctly."""
from __future__ import annotations

import json
import ssl
import socket
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
DOMAIN = "voice.adastra.bbs.tr"
NGINX_SITE = f"/www/server/panel/vhost/nginx/{DOMAIN}.conf"
CERT = f"/www/server/panel/vhost/cert/{DOMAIN}/fullchain.pem"
KEY = f"/www/server/panel/vhost/cert/{DOMAIN}/privkey.pem"
WSS_URL = f"wss://{DOMAIN}"


def run(client, cmd, timeout=120):
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    conf = f"""
# LiveKit — HTTP (CF Flexible) + HTTPS (CF Full / direct). No forced redirect loop.

server {{
    listen 80;
    listen [::]:80;
    server_name {DOMAIN};

    location ^~ /.well-known/acme-challenge/ {{
        root /www/wwwroot/java_node_ssl;
        default_type text/plain;
    }}

    location / {{
        proxy_pass http://127.0.0.1:7880;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_connect_timeout 60s;
        proxy_buffering off;
    }}
}}

server {{
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name {DOMAIN};

    ssl_certificate     {CERT};
    ssl_certificate_key {KEY};
    ssl_protocols TLSv1.2 TLSv1.3;

    proxy_buffering off;
    client_max_body_size 0;

    location / {{
        proxy_pass http://127.0.0.1:7880;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_connect_timeout 60s;
    }}
}}
"""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    sftp = client.open_sftp()
    for path in (NGINX_SITE, f"/www/server/nginx/conf/vhost/{DOMAIN}.conf"):
        with sftp.file(path, "w") as f:
            f.write(conf)
        print("wrote", path)
    sftp.close()

    code, out, err = run(
        client,
        "nginx -t 2>&1 && (/etc/init.d/nginx reload 2>&1 || nginx -s reload 2>&1)",
    )
    print(out[-800:], err[-200:])

    # API settings via localhost login (nested data)
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

def psql(sql):
    p = subprocess.run(
        ['psql','-h',host,'-p',str(port),'-U',user,'-d',db,'-v','ON_ERROR_STOP=1','-t','-A','-c',sql],
        capture_output=True, text=True)
    print(p.returncode, p.stdout.strip()[:200], p.stderr.strip()[:200])
    return p.stdout.strip()

raw = psql("SELECT value FROM app_settings WHERE key='livekit_server_config';")
try:
    cfg = json.loads(raw) if raw else {{}}
except Exception:
    cfg = {{}}
cfg['url'] = {WSS_URL!r}
# keep apiKey if present; secret stays encrypted field as-is
psql("UPDATE app_settings SET value='" + json.dumps(cfg).replace("'", "''") + "' WHERE key='livekit_server_config';")
if not raw:
    psql("INSERT INTO app_settings (key,value,description) VALUES ('livekit_server_config','" + json.dumps(cfg).replace("'", "''") + "','LiveKit');")

vr_raw = psql("SELECT value FROM app_settings WHERE key='voice_rtc_config';")
try:
    vr = json.loads(vr_raw) if vr_raw else {{}}
except Exception:
    vr = {{}}
vr['provider'] = 'livekit'
psql("UPDATE app_settings SET value='" + json.dumps(vr).replace("'", "''") + "' WHERE key='voice_rtc_config';")

print('db_url', cfg.get('url'), 'provider', vr.get('provider'), 'has_apiKey', bool(cfg.get('apiKey') or cfg.get('apiSecretEnc')))

# admin login
body = json.dumps({{'identifier': env.get('ADMIN_EMAIL','admin@auralive.com'), 'password': env.get('ADMIN_PASSWORD','')}}).encode()
req = urllib.request.Request('http://127.0.0.1:3000/api/v1/auth/login', data=body, headers={{'Content-Type':'application/json'}}, method='POST')
data = json.loads(urllib.request.urlopen(req, timeout=20).read().decode())
inner = data.get('data') if isinstance(data.get('data'), dict) else data
tok = (
    (inner or {{}}).get('accessToken')
    or (inner or {{}}).get('token')
    or data.get('accessToken')
    or data.get('token')
)
print('login_ok', bool(tok))
if not tok:
    print('login_keys', list(data.keys()), list((inner or {{}}).keys())[:15])
    raise SystemExit(2)

for method, path, payload in [
    ('PATCH', '/api/v1/admin/voice-rtc-settings', {{'url': {WSS_URL!r}, 'provider': 'livekit'}}),
    ('GET', '/api/v1/admin/voice-rtc-settings', None),
    ('GET', '/api/v1/config/voice-rtc', None),
]:
    headers = {{'Authorization': 'Bearer ' + tok}}
    data_b = None
    if payload is not None:
        data_b = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request('http://127.0.0.1:3000' + path, data=data_b, headers=headers, method=method)
    try:
        raw = urllib.request.urlopen(req, timeout=20).read().decode()
        print(method, path, raw[:280])
    except Exception as e:
        msg = str(e)
        if hasattr(e, 'read'):
            msg += ' ' + e.read().decode()[:200]
        print(method, path, 'FAIL', msg)
PY
""",
        timeout=60,
    )
    print(out)
    if err.strip():
        print(err[-400:])

    run(client, "pm2 restart auralive-api --update-env", timeout=60)
    time.sleep(5)
    code, out, _ = run(
        client,
        "curl -s http://127.0.0.1:3000/api/v1/config/voice-rtc; echo; "
        "curl -sI https://voice.adastra.bbs.tr/ | head -15; echo ---; "
        "curl -s https://voice.adastra.bbs.tr/ | head -c 80; echo; "
        "curl -sI http://voice.adastra.bbs.tr/ | head -15",
    )
    print(out)

    client.close()

    # Agent → public CF
    import urllib.request

    for url in (f"https://{DOMAIN}/", f"http://{DOMAIN}/"):
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=15) as r:
                print("agent", url, r.status, r.read()[:40])
        except Exception as e:
            print("agent", url, type(e).__name__, e)

    try:
        ctx = ssl.create_default_context()
        with socket.create_connection((DOMAIN, 443), timeout=10) as raw:
            with ctx.wrap_socket(raw, server_hostname=DOMAIN) as s:
                print("TLS peer CN check ok", s.version())
    except Exception as e:
        print("public TLS", e)


if __name__ == "__main__":
    main()
