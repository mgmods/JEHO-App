"""Zip agency exclusive frames + catalog and deploy to production as one archive.

Uploads one zip, extracts on the server (unzip -o), then restarts auralive-api
so cosmetics catalog re-seeds into the DB.
"""
from __future__ import annotations

import io
import sys
import zipfile
from pathlib import Path

import paramiko

sys.path.insert(0, str(Path(__file__).resolve().parent))
from deploy_secrets import deploy_settings  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
FRAMES = ROOT / "backend" / "public" / "assets" / "cosmetics" / "frames"
CATALOG = ROOT / "backend" / "public" / "assets" / "cosmetics" / "catalog.json"
PREFIX = "frame_agency_"


def build_zip() -> bytes:
    buf = io.BytesIO()
    count = 0
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        # catalog at cosmetics/catalog.json when extracted under public/assets/cosmetics
        zf.write(CATALOG, "catalog.json")
        count += 1
        for path in sorted(FRAMES.glob(f"{PREFIX}*")):
            if path.suffix.lower() not in {".png", ".svga"}:
                continue
            zf.write(path, f"frames/{path.name}")
            count += 1
    data = buf.getvalue()
    print(f"zip built: {count} files, {len(data) / 1024 / 1024:.1f} MB")
    return data


def main() -> None:
    host, user, password, remote_dir = deploy_settings()
    archive = build_zip()
    remote_zip = f"{remote_dir}/public/assets/cosmetics/.agency-frames-pack.zip"
    dest_dir = f"{remote_dir}/public/assets/cosmetics"

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"connecting {user}@{host} …")
    client.connect(host, username=user, password=password, timeout=60)
    sftp = client.open_sftp()
    print(f"uploading pack → {remote_zip}")
    with sftp.file(remote_zip, "wb") as remote:
        remote.set_pipelined(False)
        remote.write(archive)
        remote.flush()
    sftp.close()

    # Extract with unzip into cosmetics dir (frames/ + catalog.json)
    cmd = (
        f"cd {dest_dir} && "
        f"unzip -o -q {remote_zip} && "
        f"test -f frames/frame_agency_vip1_bronze_crown.png && "
        f"test -f catalog.json && "
        f"ls frames/frame_agency_*.png | wc -l && "
        f"rm -f {remote_zip} && "
        f"cd {remote_dir} && pm2 restart auralive-api --update-env"
    )
    print("remote unzip + pm2 restart …")
    _stdin, stdout, stderr = client.exec_command(cmd, timeout=300)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    client.close()
    if out.strip():
        print(out.strip())
    if err.strip():
        print(err.strip())
    if code != 0:
        raise SystemExit(f"remote failed exit={code}")
    print("deploy complete")


if __name__ == "__main__":
    main()
