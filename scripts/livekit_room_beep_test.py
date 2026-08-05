#!/usr/bin/env python3
"""Join a voice room on production as a test peer and (optionally) publish a LiveKit beep.

Uses localhost API on the deploy host to avoid Cloudflare bot blocks.
"""
from __future__ import annotations

import json
import math
import struct
import sys
import time
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
ROOM_HINTS = ("وكاله", "وكالة", "سوالف", "حفلات", "علوش")


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 180):
    _i, o, e = client.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    return code, out, err


def write(client: paramiko.SSHClient, path: str, content: str):
    sftp = client.open_sftp()
    with sftp.file(path, "w") as f:
        f.write(content)
    sftp.close()


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    try:
        # Credentials: prefer known test user, else register
        uname = "lk_bot_alosh"
        pw = "Test@alosh999"
        email = "lk_bot_alosh@test.jeho.local"
        write(
            client,
            "/tmp/lk_login.json",
            json.dumps({"identifier": email, "password": pw}),
        )
        _c, out, _e = run(
            client,
            "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login "
            "-H 'Content-Type: application/json' -d @/tmp/lk_login.json",
        )
        try:
            j = json.loads(out)
        except Exception:
            j = {}
        data = j.get("data") if isinstance(j.get("data"), dict) else j
        token = (data or {}).get("accessToken") or (data or {}).get("token")
        if not token:
            write(
                client,
                "/tmp/lk_reg.json",
                json.dumps(
                    {
                        "username": uname,
                        "email": email,
                        "password": pw,
                        "displayName": "علوش بوت",
                    }
                ),
            )
            _c, out, _e = run(
                client,
                "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/register "
                "-H 'Content-Type: application/json' -d @/tmp/lk_reg.json",
            )
            try:
                j = json.loads(out)
            except Exception:
                j = {}
            data = j.get("data") if isinstance(j.get("data"), dict) else j
            token = (data or {}).get("accessToken") or (data or {}).get("token")
            if not token:
                # login again after register
                _c, out, _e = run(
                    client,
                    "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login "
                    "-H 'Content-Type: application/json' -d @/tmp/lk_login.json",
                )
                j = json.loads(out) if out else {}
                data = j.get("data") if isinstance(j.get("data"), dict) else j
                token = (data or {}).get("accessToken") or (data or {}).get("token")
        if not token:
            print("FAILED auth", out[:400])
            raise SystemExit(1)
        print("bot user ok:", uname)

        _c, out, _e = run(
            client,
            f"curl -sS 'http://127.0.0.1:3000/api/v1/rooms?limit=50' "
            f"-H 'Authorization: Bearer {token}'",
        )
        rooms_j = json.loads(out) if out else {}
        data = rooms_j.get("data", rooms_j)
        items = []
        if isinstance(data, dict):
            items = data.get("items") or data.get("rooms") or []
        elif isinstance(data, list):
            items = data
        print("rooms listed:", len(items or []))
        target = None
        for r in items or []:
            title = str((r or {}).get("title") or "")
            print(" -", title, (r or {}).get("id"))
            if any(h in title for h in ROOM_HINTS):
                target = r
                # prefer strongest match containing وكاله + سوالف
                if "وكاله" in title or "وكالة" in title:
                    break
        if not target and items:
            target = items[0]
        if not target:
            print("NO ROOM FOUND")
            raise SystemExit(2)
        rid = target["id"]
        print("TARGET ROOM:", target.get("title"), rid)

        write(client, "/tmp/lk_join.json", "{}")
        _c, out, _e = run(
            client,
            f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{rid}/join "
            f"-H 'Authorization: Bearer {token}' "
            f"-H 'Content-Type: application/json' -d @/tmp/lk_join.json",
        )
        join = json.loads(out) if out else {}
        jd = join.get("data") if isinstance(join.get("data"), dict) else join
        print(
            "join voiceProvider=",
            jd.get("voiceProvider"),
            "url=",
            jd.get("livekitUrl"),
            "token_len=",
            len(str(jd.get("token") or "")),
            "canPublish=",
            jd.get("canPublish"),
        )

        # take seat index 1 (guest seat) for publish privilege
        write(client, "/tmp/lk_seat.json", json.dumps({"seatIndex": 1}))
        _c, out, _e = run(
            client,
            f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{rid}/seats/take "
            f"-H 'Authorization: Bearer {token}' "
            f"-H 'Content-Type: application/json' -d @/tmp/lk_seat.json",
        )
        print("take seat:", out[:250])
        # if seats/take path different, try alternatives
        if "404" in out or "Cannot POST" in out or "statusCode" in out:
            for path in (
                f"/rooms/{rid}/take-seat",
                f"/rooms/{rid}/seats",
                f"/rooms/{rid}/seat",
            ):
                _c, out2, _e = run(
                    client,
                    f"curl -sS -X POST http://127.0.0.1:3000/api/v1{path} "
                    f"-H 'Authorization: Bearer {token}' "
                    f"-H 'Content-Type: application/json' "
                    f"-d @/tmp/lk_seat.json",
                )
                print("try", path, out2[:200])

        # refresh join for canPublish token
        _c, out, _e = run(
            client,
            f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{rid}/join "
            f"-H 'Authorization: Bearer {token}' "
            f"-H 'Content-Type: application/json' -d @/tmp/lk_join.json",
        )
        join = json.loads(out) if out else {}
        jd = join.get("data") if isinstance(join.get("data"), dict) else join
        lk_token = jd.get("token") or ""
        lk_url = jd.get("livekitUrl") or f"wss://{HOST}:7880"
        room_name = jd.get("livekitRoomName") or jd.get("zegoRoomId") or rid
        print(
            "refresh canPublish=",
            jd.get("canPublish"),
            "room=",
            room_name,
            "provider=",
            jd.get("voiceProvider"),
        )

        # Install livekit rtc if needed and run beep publisher for ~25s
        bot_py = r'''
import asyncio, math, struct, os, time
from livekit import rtc

URL = os.environ["LK_URL"]
TOKEN = os.environ["LK_TOKEN"]

async def main():
    room = rtc.Room()
    @room.on("track_subscribed")
    def on_sub(track, publication, participant):
        print(f"SUBSCRIBED audio/video from {participant.identity} kind={track.kind}", flush=True)
    @room.on("participant_connected")
    def on_pc(p):
        print(f"PARTICIPANT joined: {p.identity}", flush=True)
    @room.on("participant_disconnected")
    def on_pd(p):
        print(f"PARTICIPANT left: {p.identity}", flush=True)

    print("connecting…", URL, flush=True)
    await room.connect(URL, TOKEN)
    print("CONNECTED local=", room.local_participant.identity, "remotes=", len(room.remote_participants), flush=True)
    for pid, p in room.remote_participants.items():
        print(f" already here: {p.identity}", flush=True)

    source = rtc.AudioSource(48000, 1)
    track = rtc.LocalAudioTrack.create_audio_track("bot-beep", source)
    opts = rtc.TrackPublishOptions()
    opts.source = rtc.TrackSource.SOURCE_MICROPHONE
    pub = await room.local_participant.publish_track(track, opts)
    print("PUBLISHED beep track", pub.sid, flush=True)

    # 25 seconds of 880Hz beeps (0.2s on / 0.4s off)
    sample_rate = 48000
    samples_20ms = 960
    freq = 880.0
    t0 = time.time()
    frame_i = 0
    while time.time() - t0 < 25:
        phase_on = (frame_i % 30) < 10  # ~200ms on of 600ms cycle
        amp = 0.25 if phase_on else 0.0
        frames = bytearray()
        for n in range(samples_20ms):
            s = amp * math.sin(2 * math.pi * freq * ((frame_i * samples_20ms + n) / sample_rate))
            frames += struct.pack("<h", int(max(-1, min(1, s)) * 32767))
        audio = rtc.AudioFrame(
            data=bytes(frames),
            sample_rate=sample_rate,
            num_channels=1,
            samples_per_channel=samples_20ms,
        )
        await source.capture_frame(audio)
        frame_i += 1
        await asyncio.sleep(0.02)
    print("done beeps; remotes now=", len(room.remote_participants), flush=True)
    await room.disconnect()
    print("disconnected", flush=True)

asyncio.run(main())
'''
        write(client, "/tmp/lk_beep_bot.py", bot_py)
        # ensure package
        run(
            client,
            "python3 -m pip install -q 'livekit>=0.17.0' 2>/dev/null || "
            "pip3 install -q 'livekit>=0.17.0' 2>/dev/null || true",
            timeout=180,
        )
        # export token carefully via env file
        env_file = f"LK_URL={lk_url}\nLK_TOKEN={lk_token}\n"
        write(client, "/tmp/lk_bot.env", env_file)
        print("Starting LiveKit beep bot for 25s …")
        _c, out, err = run(
            client,
            "set -a; . /tmp/lk_bot.env; set +a; "
            "timeout 40 python3 /tmp/lk_beep_bot.py 2>&1 || "
            "timeout 40 python /tmp/lk_beep_bot.py 2>&1 || true",
            timeout=60,
        )
        print("BOT OUT:")
        print(out[-2000:] if out else "(empty)")
        if err:
            print("BOT ERR:", err[-500:])

        run(
            client,
            "rm -f /tmp/lk_login.json /tmp/lk_reg.json /tmp/lk_join.json "
            "/tmp/lk_seat.json /tmp/lk_beep_bot.py /tmp/lk_bot.env",
        )
        print("DONE")
    finally:
        client.close()


if __name__ == "__main__":
    main()
