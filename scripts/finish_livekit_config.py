#!/usr/bin/env python3
"""Finish LiveKit configuration on server via localhost (bypasses Cloudflare bot block)."""
from __future__ import annotations

import json
import secrets
import string
import sys
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 120):
    _i, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def write_remote(client: paramiko.SSHClient, path: str, content: str):
    sftp = client.open_sftp()
    with sftp.file(path, "w") as f:
        f.write(content)
    sftp.close()


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    try:
        _c, out, _e = run(
            client,
            "docker ps --filter name=jeho-livekit --format '{{.Names}} {{.Status}}'; "
            "ss -lntp 2>/dev/null | grep -E '7880|7881' || netstat -lntp 2>/dev/null | grep -E '7880|7881' || true",
        )
        print("LIVEKIT CONTAINER:")
        print(out.strip() or "(no output)")

        _c, env_out, _e = run(
            client,
            f"grep -E '^(LIVEKIT_URL|LIVEKIT_API_KEY|LIVEKIT_API_SECRET|VOICE_RTC_PROVIDER|ADMIN_EMAIL|ADMIN_PASSWORD)=' "
            f"{REMOTE_DIR}/.env || true",
        )
        env: dict[str, str] = {}
        for line in env_out.splitlines():
            if "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
        for k in ("LIVEKIT_URL", "VOICE_RTC_PROVIDER", "LIVEKIT_API_KEY"):
            print(f"{k}={env.get(k, '')}")
        print(f"LIVEKIT_API_SECRET=***({len(env.get('LIVEKIT_API_SECRET',''))} chars)")

        admin_email = env.get("ADMIN_EMAIL") or "admin@auralive.com"
        admin_pw = env.get("ADMIN_PASSWORD") or ""
        if not admin_pw:
            raise SystemExit("ADMIN_PASSWORD missing on server .env")

        write_remote(
            client,
            "/tmp/jeho_admin_login.json",
            json.dumps({"email": admin_email, "password": admin_pw}),
        )
        _c, out, err = run(
            client,
            "curl -sS -X POST http://127.0.0.1:3000/api/v1/admin/auth/login "
            "-H 'Content-Type: application/json' "
            "-d @/tmp/jeho_admin_login.json",
        )
        try:
            j = json.loads(out)
        except Exception:
            print("admin login raw", out[:400], err[:200])
            raise SystemExit(1)
        data = j.get("data") if isinstance(j.get("data"), dict) else j
        token = (
            (data or {}).get("accessToken")
            or (data or {}).get("token")
            or j.get("accessToken")
        )
        if not token:
            print("admin login fail", str(j)[:400])
            raise SystemExit(1)
        print("admin login OK")

        patch = {
            "url": env.get("LIVEKIT_URL") or f"wss://{HOST}:7880",
            "apiKey": env.get("LIVEKIT_API_KEY") or "APIJEHOVOICE",
            "apiSecret": env.get("LIVEKIT_API_SECRET") or "",
            "provider": "livekit",
        }
        if not patch["apiSecret"]:
            raise SystemExit("LIVEKIT_API_SECRET empty in .env")
        write_remote(client, "/tmp/jeho_lk_patch.json", json.dumps(patch))
        _c, out, err = run(
            client,
            "curl -sS -X PATCH http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings "
            f"-H 'Authorization: Bearer {token}' "
            "-H 'Content-Type: application/json' "
            "-d @/tmp/jeho_lk_patch.json",
        )
        print("PATCH settings:", out[:500])

        _c, out, _e = run(client, "curl -sS http://127.0.0.1:3000/api/v1/config/voice-rtc")
        print("PUBLIC config:", out[:300])

        uname = "lk_test_" + "".join(secrets.choice(string.digits) for _ in range(4))
        pw = "Test@" + secrets.token_hex(4)
        email = f"{uname}@test.jeho.local"
        write_remote(
            client,
            "/tmp/jeho_reg.json",
            json.dumps(
                {
                    "username": uname,
                    "email": email,
                    "password": pw,
                    "displayName": "LiveKit Tester",
                }
            ),
        )
        _c, out, _e = run(
            client,
            "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/register "
            "-H 'Content-Type: application/json' -d @/tmp/jeho_reg.json",
        )
        try:
            rj = json.loads(out)
        except Exception:
            rj = {}
        rdata = rj.get("data") if isinstance(rj.get("data"), dict) else rj
        utoken = (rdata or {}).get("accessToken") or (rdata or {}).get("token")
        if not utoken:
            write_remote(
                client,
                "/tmp/jeho_ulogin.json",
                json.dumps({"identifier": email, "password": pw}),
            )
            _c, out, _e = run(
                client,
                "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login "
                "-H 'Content-Type: application/json' -d @/tmp/jeho_ulogin.json",
            )
            try:
                lj = json.loads(out)
            except Exception:
                lj = {}
            ldata = lj.get("data") if isinstance(lj.get("data"), dict) else lj
            utoken = (ldata or {}).get("accessToken") or (ldata or {}).get("token")

        print("----- TEST USER -----")
        print("username:", uname)
        print("password:", pw)
        print("email:", email)
        print("token:", "yes" if utoken else "no")

        if utoken:
            _c, out, _e = run(
                client,
                f"curl -sS 'http://127.0.0.1:3000/api/v1/rooms?limit=40' "
                f"-H 'Authorization: Bearer {utoken}'",
            )
            try:
                rooms_j = json.loads(out)
            except Exception:
                rooms_j = {}
            data = rooms_j.get("data", rooms_j)
            items = []
            if isinstance(data, dict):
                items = data.get("items") or data.get("rooms") or []
            elif isinstance(data, list):
                items = data
            print("rooms:", len(items or []))
            target = None
            for r in items or []:
                title = str((r or {}).get("title") or "")
                if "سوالف" in title or "حفلات" in title:
                    target = r
                    break
            if not target:
                for r in (items or [])[:10]:
                    print(" -", (r or {}).get("title"))
                if items:
                    target = items[0]
            if target:
                rid = target.get("id")
                print("JOIN", rid, target.get("title"))
                write_remote(client, "/tmp/jeho_join.json", "{}")
                _c, out, _e = run(
                    client,
                    f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{rid}/join "
                    f"-H 'Authorization: Bearer {utoken}' "
                    f"-H 'Content-Type: application/json' -d @/tmp/jeho_join.json",
                )
                try:
                    jj = json.loads(out)
                except Exception:
                    print("join raw", out[:400])
                    jj = {}
                d = jj.get("data") if isinstance(jj.get("data"), dict) else jj
                print("voiceProvider=", d.get("voiceProvider"))
                print("livekitUrl=", d.get("livekitUrl"))
                print("token_len=", len(str(d.get("token") or "")))
                print("canPublish=", d.get("canPublish"))
                if d.get("voiceProvider") == "livekit" and d.get("token"):
                    print("SERVER JOIN PATH OK — LiveKit token issued")
                else:
                    print("WARN join payload incomplete", str(d)[:300])

        run(
            client,
            "rm -f /tmp/jeho_admin_login.json /tmp/jeho_lk_patch.json "
            "/tmp/jeho_reg.json /tmp/jeho_ulogin.json /tmp/jeho_join.json",
        )
        print("DONE")
    finally:
        client.close()


if __name__ == "__main__":
    main()
