#!/usr/bin/env python3
"""Install livekit + run 35s beep while on seat 6 with canPublish."""
from __future__ import annotations
import json, sys
from pathlib import Path
import paramiko
sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
HOST, USER, PASSWORD, _ = deploy_settings()
ROOM_ID = "567ca7e1-665b-4dde-909d-51fbce270792"
EMAIL = "lk_bot_alosh@test.jeho.local"
PW = "Test@alosh999"

def run(c, cmd, timeout=300):
    _i, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    return o.channel.recv_exit_status(), out, err

def write(c, path, content):
    sftp = c.open_sftp()
    with sftp.file(path, "w") as f:
        f.write(content)
    sftp.close()

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)

# bootstrap pip
code, out, err = run(c, r"""
set -e
if ! python3 -m pip --version >/dev/null 2>&1; then
  curl -sS https://bootstrap.pypa.io/get-pip.py -o /tmp/get-pip.py
  python3 /tmp/get-pip.py --break-system-packages 2>&1 | tail -15
fi
python3 -m pip install --break-system-packages -U pip 2>&1 | tail -5
python3 -m pip install --break-system-packages 'livekit' 'livekit-api' 2>&1 | tail -25
python3 -c "from livekit import rtc; print('livekit-rtc-ok', rtc.__name__)"
""", timeout=360)
print(out)
print(err[-400:] if err else "")

write(c, "/tmp/lk_login.json", json.dumps({"identifier": EMAIL, "password": PW}))
_code, out, _ = run(c, "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login -H 'Content-Type: application/json' -d @/tmp/lk_login.json")
j = json.loads(out)
data = j.get("data") if isinstance(j.get("data"), dict) else j
token = (data or {}).get("accessToken") or (data or {}).get("token")
write(c, "/tmp/lk_join.json", "{}")
# ensure seat + join
write(c, "/tmp/lk_seat.json", json.dumps({"seatIndex": 6}))
run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/join -H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' -d @/tmp/lk_join.json")
run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/seats/take -H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' -d @/tmp/lk_seat.json")
_code, out, _ = run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM_ID}/join -H 'Authorization: Bearer {token}' -H 'Content-Type: application/json' -d @/tmp/lk_join.json")
jd = json.loads(out).get("data") or {}
lk_token = jd.get("token") or ""
lk_url = jd.get("livekitUrl") or f"wss://{HOST}:7880"
print("canPublish", jd.get("canPublish"), "token_len", len(lk_token))

bot = open(Path(__file__).resolve().parent / "livekit_beep_body.py", "w") if False else None
bot_src = r'''
import asyncio, math, struct, os, time
from livekit import rtc
URL = os.environ["LK_URL"]; TOKEN = os.environ["LK_TOKEN"]
async def main():
    room = rtc.Room()
    @room.on("track_subscribed")
    def on_sub(track, publication, participant):
        print(f"HEAR from {participant.identity} kind={track.kind}", flush=True)
    @room.on("participant_connected")
    def on_pc(p): print(f"PEER IN {p.identity}", flush=True)
    @room.on("participant_disconnected")
    def on_pd(p): print(f"PEER OUT {p.identity}", flush=True)
    print("connect", URL, flush=True)
    await room.connect(URL, TOKEN)
    print("CONNECTED", room.local_participant.identity, "peers", len(room.remote_participants), flush=True)
    for p in room.remote_participants.values():
        print(" peer:", p.identity, flush=True)
    source = rtc.AudioSource(48000, 1)
    track = rtc.LocalAudioTrack.create_audio_track("bot-beep", source)
    opts = rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE)
    try:
        pub = await room.local_participant.publish_track(track, opts)
        print("PUBLISHED", getattr(pub, "sid", pub), flush=True)
    except Exception as e:
        print("PUBLISH FAIL", repr(e), flush=True)
    sample_rate=48000; samples=960; freq=880.0; t0=time.time(); n=0
    while time.time()-t0 < 35:
        on = (n % 30) < 12
        amp = 0.3 if on else 0.0
        buf=bytearray()
        for i in range(samples):
            s = amp * math.sin(2*math.pi*freq*((n*samples+i)/sample_rate))
            buf += struct.pack("<h", int(max(-1,min(1,s))*32767))
        frame = rtc.AudioFrame(data=bytes(buf), sample_rate=sample_rate, num_channels=1, samples_per_channel=samples)
        await source.capture_frame(frame)
        n += 1
        await asyncio.sleep(0.02)
    print("DONE peers", len(room.remote_participants), flush=True)
    await room.disconnect()
asyncio.run(main())
'''
write(c, "/tmp/lk_beep_bot.py", bot_src)
write(c, "/tmp/lk_bot.env", f"LK_URL={lk_url}\nLK_TOKEN={lk_token}\n")
print("BEEP 35 seconds — open the room and take mic if you want two-way")
_code, out, err = run(c, "set -a; . /tmp/lk_bot.env; set +a; timeout 50 python3 /tmp/lk_beep_bot.py 2>&1", timeout=70)
print(out)
print(err[-300:] if err else "")
c.close()
