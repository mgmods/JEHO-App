#!/usr/bin/env python3
"""Install self-hosted LiveKit on the deploy host and configure Nest JWT/API settings.

Does not print secrets. Uses scripts/deploy_secrets.py.
"""
from __future__ import annotations

import json
import secrets
import string
import sys
import time
from pathlib import Path

import paramiko
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
API_BASE = "https://api.adnova.bbs.tr/api/v1"

# Stable-ish key id + random secret (32+ chars). Stored on server + Nest app_settings.
API_KEY = "APIJEHOVOICE"
API_SECRET = secrets.token_urlsafe(36)


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 180) -> tuple[int, str, str]:
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def ensure_livekit(client: paramiko.SSHClient) -> str:
    """Start docker LiveKit; return public wss url."""
    # Prefer docker compose under /opt/jeho-livekit
    remote = "/opt/jeho-livekit"
    run(client, f"mkdir -p {remote}")

    # keys inline in livekit.yaml (safe YAML)
    yaml = f"""port: 7880
rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  use_external_ip: true
keys:
  {API_KEY}: {API_SECRET}
logging:
  level: info
"""
    sftp = client.open_sftp()
    with sftp.file(f"{remote}/livekit.yaml", "w") as f:
        f.write(yaml)
    compose = """services:
  livekit:
    image: livekit/livekit-server:latest
    container_name: jeho-livekit
    restart: unless-stopped
    network_mode: host
    command: ["--config", "/etc/livekit.yaml"]
    volumes:
      - ./livekit.yaml:/etc/livekit.yaml:ro
"""
    with sftp.file(f"{remote}/docker-compose.yml", "w") as f:
        f.write(compose)
    sftp.close()

    code, out, err = run(client, "command -v docker && docker --version")
    if code != 0:
        print("ERROR: docker not installed on server")
        print(err or out)
        raise SystemExit(2)

    # Ensure docker compose plugin
    run(client, f"cd {remote} && docker compose pull livekit", timeout=300)
    code, out, err = run(
        client,
        f"cd {remote} && docker compose down 2>/dev/null; docker compose up -d",
        timeout=180,
    )
    print("docker compose:", "ok" if code == 0 else f"fail {code}")
    if code != 0:
        print(out)
        print(err)
        raise SystemExit(3)

    # Open firewall if ufw present (best-effort)
    run(
        client,
        "if command -v ufw >/dev/null; then "
        "ufw allow 7880/tcp; ufw allow 7881/tcp; "
        "ufw allow 50000:50100/udp; ufw status | head -20; fi",
        timeout=60,
    )

    # Wait healthy
    for _ in range(15):
        code, out, _ = run(client, "docker ps --filter name=jeho-livekit --format '{{.Status}}'")
        if "Up" in out:
            break
        time.sleep(2)
    else:
        print("WARN: container not clearly Up")
        run(client, "docker logs jeho-livekit --tail 40")

    wss = f"wss://{HOST}:7880"
    # Prefer domain if nginx will proxy later; raw IP works for tests with use_external_ip
    return wss


def http_json(method: str, url: str, body: dict | None = None, token: str | None = None):
    data = None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            j = json.loads(raw)
        except Exception:
            j = {"raw": raw}
        return e.code, j


def admin_login() -> str:
    email = "admin@auralive.com"
    password = "Admin@AuraLive2024"
    prod = ROOT / "deploy" / ".env.production"
    if prod.is_file():
        for line in prod.read_text(encoding="utf-8").splitlines():
            if line.startswith("ADMIN_EMAIL="):
                email = line.split("=", 1)[1].strip()
            if line.startswith("ADMIN_PASSWORD="):
                password = line.split("=", 1)[1].strip()
    status, j = http_json(
        "POST",
        f"{API_BASE}/admin/auth/login",
        {"email": email, "password": password},
    )
    data = j.get("data") if isinstance(j.get("data"), dict) else j
    token = (
        (data or {}).get("accessToken")
        or (data or {}).get("access_token")
        or (data or {}).get("token")
        or j.get("accessToken")
    )
    if not token:
        print("admin login failed", status, str(j)[:300])
        raise SystemExit("Could not login as admin — check ADMIN credentials")
    print("admin login ok")
    return token


def configure_voice_rtc(token: str, wss_url: str):
    body = {
        "url": wss_url,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "provider": "livekit",
    }
    status, j = http_json("PATCH", f"{API_BASE}/admin/voice-rtc-settings", body, token)
    print("patch voice-rtc-settings status", status)
    if status >= 400:
        print(json.dumps(j)[:500])
        body2 = {"url": wss_url, "apiKey": API_KEY, "apiSecret": API_SECRET}
        status2, j2 = http_json(
            "PATCH", f"{API_BASE}/admin/voice-rtc-settings", body2, token
        )
        print("patch keys only", status2)
        status3, j3 = http_json(
            "PATCH",
            f"{API_BASE}/admin/voice-rtc-settings",
            {"provider": "livekit"},
            token,
        )
        print("switch provider", status3, json.dumps(j3)[:300])
        if status3 >= 400 and status2 >= 400:
            raise SystemExit("Failed to configure LiveKit settings")
    else:
        data = j.get("data") if isinstance(j.get("data"), dict) else j
        print(
            "provider=",
            data.get("provider"),
            "ready=",
            data.get("ready"),
            "url_configured=",
            data.get("urlConfigured"),
        )


def create_test_user() -> dict:
    uname = "livekit_test_" + "".join(secrets.choice(string.digits) for _ in range(4))
    password = "Test@" + secrets.token_hex(4)
    email = f"{uname}@test.jeho.local"
    status, j = http_json(
        "POST",
        f"{API_BASE}/auth/register",
        {
            "username": uname,
            "email": email,
            "password": password,
            "displayName": "LiveKit Tester",
        },
    )
    if status >= 400:
        print("register status", status, str(j)[:200])
    data = j.get("data") if isinstance(j.get("data"), dict) else j
    access = (data or {}).get("accessToken") or (data or {}).get("token")
    if not access:
        status, j = http_json(
            "POST",
            f"{API_BASE}/auth/login",
            {"identifier": email, "password": password},
        )
        data = j.get("data") if isinstance(j.get("data"), dict) else j
        access = (data or {}).get("accessToken") or (data or {}).get("token")
        if status >= 400:
            print("login status", status, str(j)[:200])
    return {
        "username": uname,
        "email": email,
        "password": password,
        "token": access,
        "user": (data or {}).get("user") or data,
    }


def public_voice_check():
    status, j = http_json("GET", f"{API_BASE}/config/voice-rtc")
    print("public voice-rtc", status, json.dumps(j)[:400])


def list_rooms_and_join(user_token: str):
    status, j = http_json("GET", f"{API_BASE}/rooms?limit=30", token=user_token)
    print("rooms list", status)
    items = []
    if isinstance(j, dict):
        data = j.get("data", j)
        if isinstance(data, dict):
            items = data.get("items") or data.get("rooms") or data.get("data") or []
        elif isinstance(data, list):
            items = data
    target = None
    for r in items or []:
        if not isinstance(r, dict):
            continue
        title = str(r.get("title") or "")
        if "سوالف" in title or "حفلات" in title or "party" in title.lower():
            target = r
            break
    if not target and items:
        target = items[0]
    if not target:
        print("No public room found to join")
        return
    rid = target.get("id")
    print("joining room", rid, target.get("title"))
    status, j = http_json(
        "POST", f"{API_BASE}/rooms/{rid}/join", {"password": ""}, user_token
    )
    print("join status", status)
    data = j.get("data") if isinstance(j.get("data"), dict) else j
    print(
        "voiceProvider=",
        data.get("voiceProvider"),
        "livekitUrl=",
        data.get("livekitUrl"),
        "token_len=",
        len(str(data.get("token") or "")),
        "canPublish=",
        data.get("canPublish"),
    )


def main():
    print("=== LiveKit setup on", HOST, "===")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    try:
        wss = ensure_livekit(client)
        print("LiveKit URL:", wss)
        # Also write env hints into Nest .env (optional dual path)
        # Update Nest env for LiveKit (idempotent)
        sftp = client.open_sftp()
        env_path = f"{REMOTE_DIR}/.env"
        try:
            with sftp.open(env_path, "r") as rf:
                existing = rf.read().decode("utf-8", "replace")
        except OSError:
            existing = ""
        lines = []
        keys_drop = {
            "VOICE_RTC_PROVIDER",
            "LIVEKIT_URL",
            "LIVEKIT_API_KEY",
            "LIVEKIT_API_SECRET",
        }
        for line in existing.splitlines():
            key = line.split("=", 1)[0].strip() if "=" in line else ""
            if key in keys_drop:
                continue
            lines.append(line)
        lines.extend(
            [
                "VOICE_RTC_PROVIDER=livekit",
                f"LIVEKIT_URL={wss}",
                f"LIVEKIT_API_KEY={API_KEY}",
                f"LIVEKIT_API_SECRET={API_SECRET}",
                "",
            ]
        )
        with sftp.open(env_path, "w") as wf:
            wf.write("\n".join(lines) + "\n")
        sftp.close()
        run(client, "pm2 restart auralive-api --update-env || true", timeout=90)
        time.sleep(5)
        print("Nest .env LiveKit keys updated + api restarted")
    finally:
        client.close()

    token = admin_login()
    configure_voice_rtc(token, f"wss://{HOST}:7880")
    public_voice_check()
    user = create_test_user()
    print("TEST USER (login in app):")
    print("  username:", user["username"])
    print("  password:", user["password"])
    if user.get("token"):
        list_rooms_and_join(user["token"])
    print("=== done ===")
    print("Open admin → Voice RTC: should show LiveKit ready.")
    print("Both of you need an install of the APK that includes LiveKit (new build).")


if __name__ == "__main__":
    main()
