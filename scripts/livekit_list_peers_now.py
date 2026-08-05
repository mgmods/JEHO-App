#!/usr/bin/env python3
"""List active LiveKit rooms/peers and publish a short test tone if a room has users."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

HOST, USER, PASSWORD, REMOTE_DIR = deploy_settings()


def run(client, cmd, timeout=180):
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


SCRIPT = r"""
from pathlib import Path
import json, os, subprocess, textwrap, time

remote = Path(os.environ['REMOTE_DIR'])
env = {}
for line in remote.joinpath('.env').read_text(encoding='utf-8-sig', errors='replace').splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    k, _, v = line.partition('=')
    env[k.strip()] = v.strip().strip('"').strip("'")

# 1) list rooms via livekit-server-sdk RoomServiceClient against localhost:7880
js = textwrap.dedent(r'''
const { RoomServiceClient, AccessToken } = require('livekit-server-sdk');
(async () => {
  const key = process.env.LK_KEY;
  const secret = process.env.LK_SECRET;
  const hosts = ['http://127.0.0.1:7880', 'https://voice.adastra.bbs.tr'];
  let svc = null;
  let hostUsed = null;
  for (const h of hosts) {
    try {
      const c = new RoomServiceClient(h, key, secret);
      await c.listRooms();
      svc = c; hostUsed = h; break;
    } catch (e) {
      console.log('HOST_FAIL', h, String(e).slice(0, 120));
    }
  }
  if (!svc) { console.log('NO_SVC'); process.exit(2); }
  console.log('HOST_OK', hostUsed);
  const rooms = await svc.listRooms();
  console.log('ROOM_COUNT', rooms.length);
  let active = null;
  for (const r of rooms) {
    console.log('ROOM', r.name, 'participants', r.numParticipants);
    const parts = await svc.listParticipants(r.name);
    for (const p of parts) {
      const tracks = (p.tracks || []).map(t => `${t.type}:${t.source||''}:muted=${t.muted}:sid=${t.sid}`).join('|') || '(no-tracks)';
      console.log('  PEER', p.identity, 'name', p.name, 'state', p.state, 'tracks', tracks);
    }
    if ((r.numParticipants || 0) > 0 && !active) active = r.name;
  }
  if (active) {
    console.log('ACTIVE_ROOM', active);
    // mint join token that can subscribe only for probe
    const at = new AccessToken(key, secret, { identity: 'server_probe_hear', ttl: '90s' });
    at.addGrant({ roomJoin: true, room: active, canPublish: false, canSubscribe: true });
    console.log('PROBE_TOKEN', await at.toJwt());
    // also mint publish token for beep bot later
    const at2 = new AccessToken(key, secret, { identity: 'server_probe_beep', ttl: '90s' });
    at2.addGrant({ roomJoin: true, room: active, canPublish: true, canSubscribe: true });
    console.log('BEEP_TOKEN', await at2.toJwt());
  } else {
    console.log('NO_ACTIVE_ROOM');
  }
})().catch(e => { console.error('ERR', e); process.exit(1); });
''')
Path('/tmp/lk_list.js').write_text(js)
p = subprocess.run(
    ['node', '/tmp/lk_list.js'],
    capture_output=True, text=True,
    env={
        **os.environ,
        'LK_KEY': env['LIVEKIT_API_KEY'],
        'LK_SECRET': env['LIVEKIT_API_SECRET'],
        'NODE_PATH': str(remote / 'node_modules'),
    },
    cwd=str(remote),
)
print(p.stdout)
if p.stderr:
    print('STDERR', p.stderr[-600:])
print('list_exit', p.returncode)

# docker logs for recent publish
print('--- livekit logs ---')
subprocess.run(['docker', 'logs', 'jeho-livekit', '--tail', '40'], check=False)
"""


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    # upload and run
    sftp = client.open_sftp()
    with sftp.file("/tmp/lk_audio_test.py", "w") as f:
        f.write(SCRIPT)
    sftp.close()
    code, out, err = run(
        client,
        f"REMOTE_DIR={REMOTE_DIR!r} python3 /tmp/lk_audio_test.py",
        timeout=90,
    )
    print(out)
    if err:
        print(err[-1500:])
    print("exit", code)
    client.close()


if __name__ == "__main__":
    main()
