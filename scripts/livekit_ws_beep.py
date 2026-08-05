#!/usr/bin/env python3
"""Fix URL to ws:// (no TLS on :7880) and run 40s beep bot from localhost."""
from __future__ import annotations
import json, sys
from pathlib import Path
import paramiko
sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()
ROOM = "567ca7e1-665b-4dde-909d-51fbce270792"
EMAIL = "lk_bot_alosh@test.jeho.local"
PW = "Test@alosh999"

def run(c, cmd, timeout=120):
    _i, o, e = c.exec_command(cmd, timeout=timeout)
    return o.channel.recv_exit_status(), o.read().decode("utf-8", "replace"), e.read().decode("utf-8", "replace")

def write(c, path, content):
    s = c.open_sftp()
    with s.file(path, "w") as f:
        f.write(content)
    s.close()

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASSWORD, timeout=30)

# Show what 7880 returns
code, out, err = run(c, "curl -sS -i http://127.0.0.1:7880/ | head -20; echo '---'; curl -sk -i https://127.0.0.1:7880/ | head -5 || true")
print("HTTP probe:\n", out)

# Update URL in nest env + admin settings to ws://
code, env_out, _ = run(c, f"grep -E '^(LIVEKIT_|VOICE_RTC|ADMIN_)' {REMOTE_DIR}/.env")
env = {}
for line in env_out.splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        env[k] = v.strip()

# Prefer local plain WS for public URL until TLS proxy exists
public_ws = f"ws://{HOST}:7880"
# rewrite .env LIVEKIT_URL
lines = []
code, raw, _ = run(c, f"cat {REMOTE_DIR}/.env")
for line in raw.splitlines():
    if line.startswith("LIVEKIT_URL="):
        lines.append(f"LIVEKIT_URL={public_ws}")
    else:
        lines.append(line)
if not any(l.startswith("LIVEKIT_URL=") for l in lines):
    lines.append(f"LIVEKIT_URL={public_ws}")
write(c, f"{REMOTE_DIR}/.env", "\n".join(lines) + "\n")
print("updated LIVEKIT_URL to", public_ws)

# Admin login + patch
write(c, "/tmp/adm.json", json.dumps({"email": env.get("ADMIN_EMAIL"), "password": env.get("ADMIN_PASSWORD")}))
code, out, _ = run(c, "curl -sS -X POST http://127.0.0.1:3000/api/v1/admin/auth/login -H 'Content-Type: application/json' -d @/tmp/adm.json")
j = json.loads(out)
d = j.get("data") if isinstance(j.get("data"), dict) else j
at = (d or {}).get("accessToken") or (d or {}).get("token")
patch = {
    "url": public_ws,
    "apiKey": env.get("LIVEKIT_API_KEY") or "APIJEHOVOICE",
    "apiSecret": env.get("LIVEKIT_API_SECRET") or "",
    "provider": "livekit",
}
write(c, "/tmp/patch.json", json.dumps(patch))
code, out, _ = run(c, f"curl -sS -X PATCH http://127.0.0.1:3000/api/v1/admin/voice-rtc-settings -H 'Authorization: Bearer {at}' -H 'Content-Type: application/json' -d @/tmp/patch.json")
print("patch:", out[:250])
run(c, "pm2 restart auralive-api --update-env", timeout=60)
import time; time.sleep(4)

# Login bot + seat + token
write(c, "/tmp/login.json", json.dumps({"identifier": EMAIL, "password": PW}))
code, out, _ = run(c, "curl -sS -X POST http://127.0.0.1:3000/api/v1/auth/login -H 'Content-Type: application/json' -d @/tmp/login.json")
tok = (json.loads(out).get("data") or {}).get("accessToken")
write(c, "/tmp/join.json", "{}")
write(c, "/tmp/seat.json", json.dumps({"seatIndex": 6}))
run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM}/join -H 'Authorization: Bearer {tok}' -H 'Content-Type: application/json' -d @/tmp/join.json")
run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM}/seats/take -H 'Authorization: Bearer {tok}' -H 'Content-Type: application/json' -d @/tmp/seat.json")
code, out, _ = run(c, f"curl -sS -X POST http://127.0.0.1:3000/api/v1/rooms/{ROOM}/join -H 'Authorization: Bearer {tok}' -H 'Content-Type: application/json' -d @/tmp/join.json")
jd = json.loads(out).get("data") or {}
lk_token = jd.get("token") or ""
# Use localhost plain WS from bot
lk_url = "ws://127.0.0.1:7880"
print("token canPublish", jd.get("canPublish"), "client_url_payload", jd.get("livekitUrl"))

bot = open.__doc__  # no-op keep style
bot_src = r'''
import asyncio, math, struct, os, time
from livekit import rtc
URL=os.environ["LK_URL"]; TOKEN=os.environ["LK_TOKEN"]
async def main():
    room=rtc.Room()
    @room.on("track_subscribed")
    def on_sub(track, publication, participant):
        print(f"HEAR from {participant.identity} kind={track.kind}", flush=True)
    @room.on("participant_connected")
    def on_pc(p): print(f"PEER IN {p.identity}", flush=True)
    print("connect", URL, flush=True)
    await room.connect(URL, TOKEN)
    print("CONNECTED", room.local_participant.identity, "peers", len(room.remote_participants), flush=True)
    for p in room.remote_participants.values():
        print(" peer:", p.identity, flush=True)
    source=rtc.AudioSource(48000,1)
    track=rtc.LocalAudioTrack.create_audio_track("bot-beep", source)
    opts=rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE)
    pub=await room.local_participant.publish_track(track, opts)
    print("PUBLISHED", pub.sid, flush=True)
    sample_rate=48000; samples=960; freq=880.0; t0=time.time(); n=0
    while time.time()-t0<40:
        on=(n%30)<12
        amp=0.32 if on else 0.0
        buf=bytearray()
        for i in range(samples):
            s=amp*math.sin(2*math.pi*freq*((n*samples+i)/sample_rate))
            buf+=struct.pack("<h", int(max(-1,min(1,s))*32767))
        await source.capture_frame(rtc.AudioFrame(data=bytes(buf), sample_rate=sample_rate, num_channels=1, samples_per_channel=samples))
        n+=1
        await asyncio.sleep(0.02)
    print("DONE peers", len(room.remote_participants), flush=True)
    await room.disconnect()
asyncio.run(main())
'''
write(c, "/tmp/lk_beep_bot.py", bot_src)
write(c, "/tmp/lk_bot.env", f"LK_URL={lk_url}\nLK_TOKEN={lk_token}\n")
print("BEEPING 40s on seat 6 as lk_bot_alosh — listen now in سوالفنا حفلات")
code, out, err = run(c, "set -a; . /tmp/lk_bot.env; set +a; timeout 55 python3 /tmp/lk_beep_bot.py 2>&1", timeout=70)
print(out)
print(err[-300:] if err else "")
c.close()
