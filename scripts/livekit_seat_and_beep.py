#!/usr/bin/env python3
"""Try free seats + install livekit rtc + beep in room سوالفنا حفلات."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
ROOM_ID = "567ca7e1-665b-4dde-909d-51fbce270792"
EMAIL = "lk_bot_alosh@test.jeho.local"
PW = "Test@alosh999"


def run(c, cmd, timeout=180):
    _i, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    return o.channel.recv_exit_status(), out, err


def write(c, path, content):
    sftp = c.open_sftp()
    with sftp.file(path, "w") as f:
        f.write(content)
    sftp.close()


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    try:
        write(c, "/tmp/lk_login.json", json.dumps({"identifier": EMAIL, "password": PW}))
        _code, out, _err = run(
            c,
            "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login "
            "-H 'Content-Type: application/json' -d @/tmp/lk_login.json",
        )
        j = json.loads(out)
        data = j.get("data") if isinstance(j.get("data"), dict) else j
        token = (data or {}).get("accessToken") or (data or {}).get("token")
        if not token:
            print("auth fail", out[:300])
            return 1
        print("auth ok")

        # get room seats
        _code, out, _err = run(
            c,
            f"curl -sS http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID} "
            f"-H 'Authorization: Bearer {token}'",
        )
        roomj = json.loads(out)
        rd = roomj.get("data") if isinstance(roomj.get("data"), dict) else roomj
        seats = rd.get("seats") or []
        print("seats:")
        free = []
        for s in seats:
            idx = s.get("seatIndex")
            uid = s.get("userId")
            st = s.get("status")
            print(f"  #{idx} status={st} user={uid}")
            if not uid and idx and idx != 0:
                free.append(idx)
        if not free:
            free = list(range(1, 11))

        # join
        write(c, "/tmp/lk_join.json", "{}")
        run(
            c,
            f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/join "
            f"-H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' "
            f"-d @/tmp/lk_join.json",
        )

        seated = False
        for idx in free:
            write(c, "/tmp/lk_seat.json", json.dumps({"seatIndex": idx}))
            _code, out, _err = run(
                c,
                f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/seats/take "
                f"-H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' "
                f"-d @/tmp/lk_seat.json",
            )
            print(f"seat {idx}:", out[:180])
            if '"success":true' in out or (out.startswith("{") and '"error"' not in out and "409" not in out and "403" not in out):
                try:
                    oj = json.loads(out)
                    if oj.get("success") is False:
                        continue
                    seated = True
                    print("seated on", idx)
                    break
                except Exception:
                    pass

        # rejoin for publish token
        _code, out, _err = run(
            c,
            f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/join "
            f"-H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' "
            f"-d @/tmp/lk_join.json",
        )
        join = json.loads(out)
        jd = join.get("data") if isinstance(join.get("data"), dict) else join
        lk_token = jd.get("token") or ""
        lk_url = jd.get("livekitUrl") or f"wss://{HOST}:7880"
        print("canPublish=", jd.get("canPublish"), "provider=", jd.get("voiceProvider"), "tok=", len(lk_token))

        # if still can't publish, mint admin-forced publish by generating token with livekit on server
        if not jd.get("canPublish"):
            # Use access token service via small node on server? Or inject seat occupied by us via SQL
            print("WARN: without canPublish token may reject publish; still try")

        # Install livekit python (livekit-rtc wheels)
        code, out, err = run(
            c,
            "python3 -m pip install --break-system-packages -q livekit livekit-api 2>&1 | tail -20; "
            "python3 -c 'import livekit; print(livekit.__file__)' 2>&1",
            timeout=240,
        )
        print("pip:", out[-500:])
        print("pip.err:", err[-200:])

        bot = r'''
import asyncio, math, struct, os, time
from livekit import rtc

URL = os.environ["LK_URL"]
TOKEN = os.environ["LK_TOKEN"]

async def main():
    room = rtc.Room()
    @room.on("track_subscribed")
    def on_sub(track, publication, participant):
        print(f"HEAR track from {participant.identity} kind={track.kind}", flush=True)
    @room.on("participant_connected")
    def on_pc(p):
        print(f"PEER IN: {p.identity}", flush=True)
    @room.on("participant_disconnected")
    def on_pd(p):
        print(f"PEER OUT: {p.identity}", flush=True)

    print("connect", URL, flush=True)
    await room.connect(URL, TOKEN)
    print("CONNECTED as", room.local_participant.identity,
          "peers", len(room.remote_participants), flush=True)
    for p in room.remote_participants.values():
        print(" peer already:", p.identity, flush=True)

    source = rtc.AudioSource(48000, 1)
    track = rtc.LocalAudioTrack.create_audio_track("bot-beep", source)
    opts = rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE)
    try:
        pub = await room.local_participant.publish_track(track, opts)
        print("PUBLISHED", pub.sid, flush=True)
    except Exception as e:
        print("PUBLISH FAIL", e, flush=True)

    sample_rate = 48000
    samples = 960
    freq = 880.0
    t0 = time.time()
    nframe = 0
    while time.time() - t0 < 30:
        on = (nframe % 30) < 10
        amp = 0.28 if on else 0.0
        buf = bytearray()
        for i in range(samples):
            s = amp * math.sin(2 * math.pi * freq * ((nframe * samples + i) / sample_rate))
            buf += struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767))
        frame = rtc.AudioFrame(data=bytes(buf), sample_rate=sample_rate,
                               num_channels=1, samples_per_channel=samples)
        try:
            await source.capture_frame(frame)
        except Exception as e:
            print("frame err", e, flush=True)
            break
        nframe += 1
        await asyncio.sleep(0.02)
    print("STOP peers=", len(room.remote_participants), flush=True)
    await room.disconnect()

asyncio.run(main())
'''
        write(c, "/tmp/lk_beep_bot.py", bot)
        write(c, "/tmp/lk_bot.env", f"LK_URL={lk_url}\nLK_TOKEN={lk_token}\n")
        print("beep 30s… listen on seat as إسماعيل / علوش")
        _code, out, err = run(
            c,
            "set -a; . /tmp/lk_bot.env; set +a; "
            "timeout 45 python3 /tmp/lk_beep_bot.py 2>&1",
            timeout=60,
        )
        print(out[-2500:] if out else "(no out)")
        if err:
            print("err", err[-400:])
    finally:
        c.close()


if __name__ == "__main__":
    raise SystemExit(main() or 0)
