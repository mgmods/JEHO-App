#!/usr/bin/env python3
"""Join active LiveKit room on server and publish short beep pattern."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE = deploy_settings()


def main() -> int:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)

    # Write remote script without broken f-string nesting
    remote_py = r'''
import asyncio, math, os, struct, subprocess, sys, wave
from pathlib import Path

remote = Path(os.environ["REMOTE_DIR"])
env = {}
for line in remote.joinpath(".env").read_text(encoding="utf-8-sig", errors="replace").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, _, v = line.partition("=")
    env[k.strip()] = v.strip().strip('"').strip("'")

KEY, SECRET = env["LIVEKIT_API_KEY"], env["LIVEKIT_API_SECRET"]

venv = Path("/tmp/lkbeepvenv")
py = venv / "bin" / "python"
if not py.exists():
    subprocess.check_call([sys.executable, "-m", "venv", str(venv)])
    subprocess.check_call([str(venv / "bin" / "pip"), "install", "-q", "livekit", "livekit-api"])

js = """
const { RoomServiceClient, AccessToken } = require('livekit-server-sdk');
(async () => {
  const svc = new RoomServiceClient('http://127.0.0.1:7880', process.env.LK_KEY, process.env.LK_SECRET);
  const rooms = await svc.listRooms();
  let room = null;
  for (const r of rooms) {
    if ((r.numParticipants||0) > 0) { room = r.name; break; }
  }
  if (!room) { console.log('NO_ROOM'); process.exit(3); }
  console.log('ROOM ' + room);
  const parts = await svc.listParticipants(room);
  for (const p of parts) {
    const n = (p.tracks||[]).length;
    console.log('PEER ' + p.identity + ' tracks=' + n);
  }
  const at = new AccessToken(process.env.LK_KEY, process.env.LK_SECRET, { identity: 'server_beep_test', ttl: '120s' });
  at.addGrant({ roomJoin: true, room, canPublish: true, canSubscribe: true });
  console.log('TOKEN ' + await at.toJwt());
})().catch(e => { console.error(e); process.exit(1); });
"""
Path("/tmp/lk_ready.js").write_text(js)
p = subprocess.run(
    ["node", "/tmp/lk_ready.js"],
    capture_output=True, text=True,
    env={**os.environ, "LK_KEY": KEY, "LK_SECRET": SECRET, "NODE_PATH": str(remote / "node_modules")},
    cwd=str(remote),
)
print(p.stdout)
if p.returncode != 0:
    print(p.stderr)
    raise SystemExit(p.returncode)

room_name = token = None
for line in p.stdout.splitlines():
    if line.startswith("ROOM "):
        room_name = line[5:].strip()
    if line.startswith("TOKEN "):
        token = line[6:].strip()
if not room_name or not token:
    raise SystemExit("missing room/token")

wav_path = "/tmp/lk_beep.wav"
rate = 48000
secs = 5
with wave.open(wav_path, "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(rate)
    frames = bytearray()
    for i in range(rate * secs):
        t = i / rate
        on = (int(t * 2) % 2) == 0
        amp = 0.4 if on else 0.0
        val = int(amp * 32767 * math.sin(2 * math.pi * 880 * t))
        frames += struct.pack("<h", val)
    w.writeframes(frames)

runner = """
import asyncio, wave
from livekit import rtc

URL = "ws://127.0.0.1:7880"
TOKEN = %r
WAV = %r

async def main():
    room = rtc.Room()
    await room.connect(URL, TOKEN)
    print("JOINED", room.name, "remotes", len(room.remote_participants))
    for pid, p in room.remote_participants.items():
        pubs = list(p.track_publications.keys()) if hasattr(p, "track_publications") else []
        print("REMOTE", p.identity, "pubs", len(pubs))
    source = rtc.AudioSource(48000, 1)
    track = rtc.LocalAudioTrack.create_audio_track("beep", source)
    opts = rtc.TrackPublishOptions()
    opts.source = rtc.TrackSource.SOURCE_MICROPHONE
    pub = await room.local_participant.publish_track(track, opts)
    print("PUBLISHED", getattr(pub, "sid", pub))
    with wave.open(WAV, "rb") as w:
        samples_per_frame = 480
        while True:
            data = w.readframes(samples_per_frame)
            if not data:
                break
            frame = rtc.AudioFrame(
                data=data,
                sample_rate=48000,
                num_channels=1,
                samples_per_channel=len(data)//2,
            )
            await source.capture_frame(frame)
            await asyncio.sleep(0.01)
    print("BEEP_DONE")
    await asyncio.sleep(1.0)
    await room.disconnect()
    print("LEFT")

asyncio.run(main())
""" % (token, wav_path)
Path("/tmp/lk_beep_run.py").write_text(runner)
p2 = subprocess.run([str(py), "/tmp/lk_beep_run.py"], capture_output=True, text=True, timeout=90)
print(p2.stdout)
if p2.stderr:
    print("STDERR", p2.stderr[-2000:])
print("beep_exit", p2.returncode)
'''

    sftp = client.open_sftp()
    with sftp.file("/tmp/lk_ready_beep.py", "w") as f:
        f.write(remote_py)
    sftp.close()

    cmd = (
        "command -v python3 >/dev/null; "
        "(dpkg -l python3-venv >/dev/null 2>&1 || apt-get install -y -qq python3-venv); "
        f"REMOTE_DIR={REMOTE!r} python3 /tmp/lk_ready_beep.py"
    )
    _, stdout, stderr = client.exec_command(cmd, timeout=180)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    print(out)
    if err.strip():
        print(err[-1500:])
    client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
