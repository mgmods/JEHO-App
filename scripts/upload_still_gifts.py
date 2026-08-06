#!/usr/bin/env python3
"""Upload JEHO still gift PNGs to production public folder."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings
import paramiko
from scp import SCPClient

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / "backend" / "public" / "assets" / "gifts" / "jeho" / "still"


def main() -> int:
    host, user, password, remote = deploy_settings()
    files = sorted(LOCAL.glob("*.png"))
    if not files:
        print("no pngs in", LOCAL)
        return 1
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(host, username=user, password=password, timeout=25)
    dest = f"{remote}/public/assets/gifts/jeho/still"
    c.exec_command(f"mkdir -p {dest}")
    with SCPClient(c.get_transport()) as scp:
        for p in files:
            scp.put(str(p), f"{dest}/{p.name}")
    print(f"uploaded {len(files)} pngs → {dest}")
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
