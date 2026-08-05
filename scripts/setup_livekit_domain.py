#!/usr/bin/env python3
"""Point LiveKit at voice.adastra.bbs.tr via nginx TLS + update Nest settings.

Does not print full secrets. Uses scripts/deploy_secrets.py.
"""
from __future__ import annotations

import json
import socket
import ssl
import sys
import time
from pathlib import Path

import paramiko
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
DOMAIN = "voice.adastra.bbs.tr"
WSS_URL = f"wss://{DOMAIN}"
API_BASE = "https://api.adnova.bbs.tr/api/v1"
NGINX_SITE = f"/www/server/panel/vhost/nginx/{DOMAIN}.conf"
CERT_DIR = f"/www/server/panel/vhost/cert/{DOMAIN}"


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 180) -> tuple[int, str, str]:
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def http_json(method: str, url: str, body: dict | None = None, token: str | None = None):
    data = None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            payload = json.loads(raw) if raw else {}
        except Exception:
            payload = {"raw": raw[:500]}
        return e.code, payload
    except Exception as e:
        return 0, {"error": str(e)}


def admin_login_local(client: paramiko.SSHClient) -> str | None:
    """Login via localhost:3000 on server (Cloudflare often blocks external admin)."""
    code, out, _ = run(
        client,
        "grep -E '^(ADMIN_EMAIL|ADMIN_PASSWORD|SEED_ADMIN)=' "
        f"{REMOTE_DIR}/.env 2>/dev/null | head -20",
    )
    email = "admin@auralive.com"
    password = ""
    for line in out.splitlines():
        if "=" not in line:
            continue
        k, _, v = line.partition("=")
        k = k.strip()
        v = v.strip().strip('"').strip("'")
        if k == "ADMIN_EMAIL" and v:
            email = v
        if k == "ADMIN_PASSWORD" and v:
            password = v
    if not password:
        print("no ADMIN_PASSWORD in .env — will patch via DB only")
        return None

    body = json.dumps({"email": email, "password": password, "username": email})
    for path in ("/api/v1/auth/login", "/api/v1/admin/login"):
        script = (
            "python3 - <<'PY'\n"
            "import json, urllib.request\n"
            f"body = {body!r}.encode()\n"
            f"url = 'http://127.0.0.1:3000{path}'\n"
            "req = urllib.request.Request(\n"
            "    url, data=body,\n"
            "    headers={'Content-Type': 'application/json'},\n"
            "    method='POST',\n"
            ")\n"
            "try:\n"
            "    r = urllib.request.urlopen(req, timeout=20)\n"
            "    print(r.read().decode())\n"
            "except Exception as e:\n"
            "    print('ERR', e)\n"
            "    if hasattr(e, 'read'):\n"
            "        print(e.read().decode()[:400])\n"
            "PY"
        )
        code, out, err = run(client, script, timeout=40)
        text = (out or "").strip()
        payload = {}
        for line in reversed(text.splitlines()):
            line = line.strip()
            if line.startswith("{"):
                try:
                    payload = json.loads(line)
                    break
                except Exception:
                    pass
        token = (
            payload.get("accessToken")
            or payload.get("token")
            or (payload.get("data") or {}).get("accessToken")
            or (payload.get("data") or {}).get("token")
        )
        if token:
            print(f"admin login via {path}: ok")
            return token
        print(f"admin login via {path}: fail -> {text[:220]}")
    return None


def check_dns() -> bool:
    try:
        ips = socket.getaddrinfo(DOMAIN, 443, type=socket.SOCK_STREAM)
        addrs = sorted({x[4][0] for x in ips})
        print(f"DNS {DOMAIN} -> {addrs}")
        return HOST in addrs or any(a == HOST for a in addrs)
    except Exception as e:
        print(f"DNS lookup failed: {e}")
        return False


def write_nginx_http_only(client: paramiko.SSHClient) -> None:
    conf = f"""
server {{
    listen 80;
    listen [::]:80;
    server_name {DOMAIN};

    location ^~ /.well-known/acme-challenge/ {{
        root /www/wwwroot/java_node_ssl;
        default_type text/plain;
    }}

    location / {{
        return 301 https://$host$request_uri;
    }}
}}
"""
    # Prefer baota path; also write under conf.d if present
    sftp = client.open_sftp()
    for path in (
        NGINX_SITE,
        f"/etc/nginx/conf.d/{DOMAIN}.conf",
        f"/www/server/nginx/conf/vhost/{DOMAIN}.conf",
    ):
        try:
            parent = str(Path(path).parent).replace("\\", "/")
            run(client, f"mkdir -p {parent}")
            with sftp.file(path, "w") as f:
                f.write(conf)
            print(f"wrote nginx http conf: {path}")
        except Exception as e:
            print(f"skip {path}: {e}")
    sftp.close()


def write_nginx_ssl(client: paramiko.SSHClient, cert: str, key: str) -> None:
    conf = f"""
# LiveKit signaling (WebSocket) — media UDP stays on host 50000-50100 / TCP 7881

server {{
    listen 80;
    listen [::]:80;
    server_name {DOMAIN};

    location ^~ /.well-known/acme-challenge/ {{
        root /www/wwwroot/java_node_ssl;
        default_type text/plain;
    }}

    location / {{
        return 301 https://$host$request_uri;
    }}
}}

server {{
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name {DOMAIN};

    ssl_certificate     {cert};
    ssl_certificate_key {key};
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    proxy_buffering off;
    proxy_request_buffering off;
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
    sftp = client.open_sftp()
    written = 0
    for path in (
        NGINX_SITE,
        f"/www/server/nginx/conf/vhost/{DOMAIN}.conf",
    ):
        try:
            parent = str(Path(path).parent).replace("\\", "/")
            run(client, f"mkdir -p {parent}")
            with sftp.file(path, "w") as f:
                f.write(conf)
            print(f"wrote nginx ssl conf: {path}")
            written += 1
        except Exception as e:
            print(f"skip {path}: {e}")
    sftp.close()
    if written == 0:
        raise RuntimeError("could not write any nginx vhost file")


def issue_cert(client: paramiko.SSHClient) -> tuple[str, str] | None:
    run(client, "mkdir -p /www/wwwroot/java_node_ssl")
    # Try existing cert first
    for base in (
        f"/www/server/panel/vhost/cert/{DOMAIN}",
        f"/etc/letsencrypt/live/{DOMAIN}",
        f"/www/server/panel/vhost/ssl/{DOMAIN}",
    ):
        code, out, _ = run(client, f"ls -la {base} 2>/dev/null")
        if code == 0 and out.strip():
            print(f"existing cert dir: {base}\n{out}")
            full = f"{base}/fullchain.pem"
            priv = f"{base}/privkey.pem"
            code2, _, _ = run(client, f"test -f {full} && test -f {priv}")
            if code2 == 0:
                return full, priv
            # baota layout
            for a, b in (
                (f"{base}/fullchain.pem", f"{base}/privkey.pem"),
                (f"{base}/fullchain.cer", f"{base}/privkey.key"),
                (f"{base}/{DOMAIN}.pem", f"{base}/{DOMAIN}.key"),
            ):
                c3, _, _ = run(client, f"test -f {a} && test -f {b}")
                if c3 == 0:
                    return a, b

    # Prefer certbot if available
    code, which, _ = run(client, "command -v certbot || command -v /usr/bin/certbot")
    if code == 0 and which.strip():
        write_nginx_http_only(client)
        reload_nginx(client)
        email = "admin@auralive.com"
        cmd = (
            f"certbot certonly --webroot -w /www/wwwroot/java_node_ssl "
            f"-d {DOMAIN} --non-interactive --agree-tos -m {email} --force-renewal 2>&1 "
            f"|| certbot certonly --nginx -d {DOMAIN} --non-interactive --agree-tos -m {email} 2>&1 "
            f"|| certbot certonly --standalone -d {DOMAIN} --non-interactive --agree-tos -m {email} "
            f"--preferred-challenges http --http-01-port 80 2>&1"
        )
        code, out, err = run(client, cmd, timeout=180)
        print("certbot:\n", out[-1500:], err[-500:])
        full = f"/etc/letsencrypt/live/{DOMAIN}/fullchain.pem"
        priv = f"/etc/letsencrypt/live/{DOMAIN}/privkey.pem"
        c2, _, _ = run(client, f"test -f {full} && test -f {priv}")
        if c2 == 0:
            return full, priv

    # baota acme / bt python
    code, out, err = run(
        client,
        f"mkdir -p {CERT_DIR}; "
        f"if command -v btpython >/dev/null; then "
        f"  btpython /www/server/panel/class/acme_v2.py --domain {DOMAIN} 2>&1 | tail -40; "
        f"fi; "
        f"ls -la {CERT_DIR} 2>/dev/null; "
        f"ls -la /www/server/panel/vhost/cert/ 2>/dev/null | head -30",
        timeout=180,
    )
    print("bt cert attempt:\n", out[-2000:], err[-400:])

    # Self-signed fallback (browsers / Android may reject unless trust user)
    run(client, f"mkdir -p {CERT_DIR}")
    code, out, err = run(
        client,
        f"openssl req -x509 -nodes -newkey rsa:2048 -days 825 "
        f"-keyout {CERT_DIR}/privkey.pem -out {CERT_DIR}/fullchain.pem "
        f"-subj '/CN={DOMAIN}' 2>&1",
    )
    print("self-signed:", "ok" if code == 0 else out + err)
    c2, _, _ = run(
        client, f"test -f {CERT_DIR}/fullchain.pem && test -f {CERT_DIR}/privkey.pem"
    )
    if c2 == 0:
        print("WARN: using self-signed cert until Let's Encrypt succeeds")
        return f"{CERT_DIR}/fullchain.pem", f"{CERT_DIR}/privkey.pem"
    return None


def reload_nginx(client: paramiko.SSHClient) -> None:
    code, out, err = run(
        client,
        "nginx -t 2>&1; "
        "(/etc/init.d/nginx reload 2>&1 || systemctl reload nginx 2>&1 || nginx -s reload 2>&1); "
        "echo EXIT:$?",
    )
    print("nginx reload:\n", out[-1500:], err[-400:])


def update_livekit_yaml_domain(client: paramiko.SSHClient) -> None:
    """Keep keys; ensure use_external_ip for media via public IP."""
    code, yaml, _ = run(client, "cat /opt/jeho-livekit/livekit.yaml 2>/dev/null")
    if code != 0 or not yaml.strip():
        print("WARN: livekit.yaml missing")
        return
    # ensure use_external_ip true already from setup
    if "use_external_ip" not in yaml:
        yaml = yaml.replace(
            "rtc:",
            "rtc:\n  use_external_ip: true",
            1,
        )
        sftp = client.open_sftp()
        with sftp.file("/opt/jeho-livekit/livekit.yaml", "w") as f:
            f.write(yaml)
        sftp.close()
        run(client, "cd /opt/jeho-livekit && docker compose up -d", timeout=120)
        print("restarted livekit with yaml touch")
    else:
        print("livekit.yaml ok")
    run(client, "docker ps --filter name=jeho-livekit --format '{{.Names}} {{.Status}}'")


def update_api_env_and_settings(client: paramiko.SSHClient, token: str | None) -> None:
    run(
        client,
        f"cd {REMOTE_DIR} && "
        f"if grep -q '^LIVEKIT_URL=' .env; then "
        f"  sed -i 's|^LIVEKIT_URL=.*|LIVEKIT_URL={WSS_URL}|' .env; "
        f"else echo 'LIVEKIT_URL={WSS_URL}' >> .env; fi && "
        f"if grep -q '^VOICE_RTC_PROVIDER=' .env; then "
        f"  sed -i 's|^VOICE_RTC_PROVIDER=.*|VOICE_RTC_PROVIDER=livekit|' .env; "
        f"else echo 'VOICE_RTC_PROVIDER=livekit' >> .env; fi && "
        f"grep -E 'LIVEKIT_URL|VOICE_RTC' .env",
    )
    if token:
        patch_local(client, token)


def patch_local(client: paramiko.SSHClient, token: str) -> None:
    body = json.dumps({"url": WSS_URL, "provider": "livekit"})
    code, out, err = run(
        client,
        "python3 - <<'PY'\n"
        "import json, urllib.request\n"
        f"body = {body!r}.encode()\n"
        f"tok = {token!r}\n"
        "req = urllib.request.Request(\n"
        "    'http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings',\n"
        "    data=body,\n"
        "    headers={\n"
        "        'Content-Type': 'application/json',\n"
        "        'Authorization': 'Bearer ' + tok,\n"
        "    },\n"
        "    method='PATCH',\n"
        ")\n"
        "try:\n"
        "    r = urllib.request.urlopen(req, timeout=20)\n"
        "    print(r.status, r.read().decode()[:500])\n"
        "except Exception as e:\n"
        "    print('ERR', e)\n"
        "    if hasattr(e, 'read'):\n"
        "        print(e.read().decode()[:500])\n"
        "PY",
        timeout=40,
    )
    print("local patch:", out, err)


def patch_url_in_db(client: paramiko.SSHClient) -> None:
    """Direct DB update of livekit_server_config JSON url field."""
    script = r"""
python3 - <<'PY'
import json, os, re
env_path = '/www/wwwroot/api.adnova.bbs.tr/.env'
db_url = None
if os.path.isfile(env_path):
    for line in open(env_path, encoding='utf-8', errors='replace'):
        if line.startswith('DATABASE_URL='):
            db_url = line.split('=',1)[1].strip().strip('"').strip("'")
if not db_url:
    print('no DATABASE_URL')
    raise SystemExit(1)
# prefer psycopg2
try:
    import psycopg2
except ImportError:
    import subprocess
    subprocess.check_call(['pip3', 'install', '-q', 'psycopg2-binary'])
    import psycopg2
# parse postgres://user:pass@host:port/db
m = re.match(r'postgres(?:ql)?://([^:]+):([^@]+)@([^:/]+)(?::(\d+))?/([^?]+)', db_url)
if not m:
    print('cannot parse DATABASE_URL')
    raise SystemExit(2)
user, password, host, port, db = m.groups()
port = int(port or 5432)
conn = psycopg2.connect(host=host, port=port, user=user, password=password, dbname=db)
cur = conn.cursor()
cur.execute("SELECT value FROM app_settings WHERE key=%s", ('livekit_server_config',))
row = cur.fetchone()
if not row:
    print('no livekit_server_config row')
else:
    try:
        cfg = json.loads(row[0])
    except Exception:
        cfg = {}
    cfg['url'] = 'wss://voice.adastra.bbs.tr'
    cfg['updatedAt'] = __import__('datetime').datetime.utcnow().isoformat() + 'Z'
    cur.execute("UPDATE app_settings SET value=%s WHERE key=%s", (json.dumps(cfg), 'livekit_server_config'))
    print('updated livekit url in db')
cur.execute("SELECT value FROM app_settings WHERE key=%s", ('voice_rtc_config',))
row = cur.fetchone()
if row:
    try:
        cfg = json.loads(row[0])
    except Exception:
        cfg = {}
else:
    cfg = {}
cfg['provider'] = 'livekit'
cfg['updatedAt'] = __import__('datetime').datetime.utcnow().isoformat() + 'Z'
if row:
    cur.execute("UPDATE app_settings SET value=%s WHERE key=%s", (json.dumps(cfg), 'voice_rtc_config'))
else:
    cur.execute(
        "INSERT INTO app_settings (key, value, description) VALUES (%s,%s,%s)",
        ('voice_rtc_config', json.dumps(cfg), 'Voice RTC provider'),
    )
print('updated voice provider livekit')
conn.commit()
cur.close(); conn.close()
print('db ok')
PY
"""
    code, out, err = run(client, script, timeout=90)
    print("db patch:", out, err)


def test_wss() -> None:
    # TLS endpoint + LiveKit HTTP
    print("--- connectivity tests ---")
    try:
        ctx = ssl.create_default_context()
        with socket.create_connection((DOMAIN, 443), timeout=10) as sock:
            with ctx.wrap_socket(sock, server_hostname=DOMAIN) as ssock:
                print("TLS handshake:", ssock.version(), ssock.getpeercert().get("subject"))
    except Exception as e:
        print("TLS fail:", e)
        try:
            ctx = ssl._create_unverified_context()
            with socket.create_connection((DOMAIN, 443), timeout=10) as sock:
                with ctx.wrap_socket(sock, server_hostname=DOMAIN) as ssock:
                    print("TLS (insecure) ok:", ssock.version())
        except Exception as e2:
            print("TLS insecure fail:", e2)

    # HTTP GET to root /rtc
    try:
        req = urllib.request.Request(f"https://{DOMAIN}/", method="GET")
        with urllib.request.urlopen(req, timeout=15, context=ssl.create_default_context()) as r:
            print("HTTPS / status", r.status, r.read()[:120])
    except Exception as e:
        print("HTTPS /:", e)

    try:
        import websocket  # type: ignore

        ws = websocket.create_connection(
            f"wss://{DOMAIN}/rtc?access_token=test&auto_subscribe=1",
            timeout=8,
            sslopt={"cert_reqs": ssl.CERT_NONE},
        )
        print("raw wss open (expect close on bad token)")
        ws.close()
    except Exception as e:
        print("wss probe:", type(e).__name__, e)


def main() -> int:
    print(f"target domain: {DOMAIN}  host: {HOST}")
    dns_ok = check_dns()
    if not dns_ok:
        print(
            f"WARN: DNS for {DOMAIN} does not resolve to {HOST}. "
            "Create A record voice → server IP, then re-run."
        )

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    try:
        code, out, _ = run(
            client,
            "hostname; docker ps --filter name=jeho-livekit --format '{{.Names}} {{.Status}}'; "
            "ss -lntp | grep 7880 | head -5; nginx -v 2>&1; "
            "ls /www/server/panel/vhost/nginx 2>/dev/null | head -20",
        )
        print(out)

        update_livekit_yaml_domain(client)
        certs = issue_cert(client)
        if not certs:
            print("ERROR: could not obtain any cert")
            return 2
        cert, key = certs
        print("using cert", cert)
        write_nginx_ssl(client, cert, key)
        reload_nginx(client)

        token = admin_login_local(client)
        update_api_env_and_settings(client, token)
        patch_url_in_db(client)
        run(client, "pm2 restart auralive-api --update-env", timeout=60)
        # final status
        code, out, _ = run(
            client,
            f"curl -skI https://{DOMAIN}/ | head -15; "
            f"curl -sk https://{DOMAIN}/ | head -c 200; echo; "
            "grep LIVEKIT_URL /www/wwwroot/api.adnova.bbs.tr/.env",
        )
        print(out)
    finally:
        client.close()

    test_wss()
    print(f"DONE. Configure clients with: {WSS_URL}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
