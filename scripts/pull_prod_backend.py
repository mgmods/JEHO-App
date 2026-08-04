#!/usr/bin/env python3
"""Download production backend source (+ public assets needed for DB parity).

Pulls from remote JEHO_DEPLOY_DIR into backend/, excluding node_modules,
.env secrets, dist build artifacts, and heavy caches.
Does NOT overwrite local scripts/deploy.local.env or git history.
"""
from __future__ import annotations

import os
import stat
import sys
import tarfile
import tempfile
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

BACKEND = ROOT / "backend"
REMOTE_ARCHIVE = "/tmp/jeho_backend_mirror.tgz"

# Paths relative to remote deploy dir packaged into the archive.
INCLUDE_PATHS = (
    "src",
    "public",
    "admin",
    "package.json",
    "package-lock.json",
    "tsconfig.json",
    "tsconfig.build.json",
    "nest-cli.json",
    "dashboard/package.json",
    "dashboard/package-lock.json",
    "dashboard/src",
    "dashboard/index.html",
    "dashboard/vite.config.ts",
    "dashboard/tsconfig.json",
    "dashboard/tsconfig.app.json",
    "dashboard/tsconfig.node.json",
)

SKIP_DIR_NAMES = {
    "node_modules",
    ".git",
    "dist",
    "__pycache__",
    ".cache",
}


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 600) -> str:
    print("$", cmd[:200], "..." if len(cmd) > 200 else "")
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if code != 0:
        raise RuntimeError(f"exit {code}\nSTDOUT:\n{out}\nSTDERR:\n{err}")
    if err.strip():
        sys.stdout.buffer.write((err[:1200] + "\n").encode("utf-8", "replace"))
    return out


def extract_safe(archive: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, "r:gz") as tf:
        for member in tf.getmembers():
            name = member.name.replace("\\", "/")
            if name.startswith("/") or ".." in name.split("/"):
                continue
            base = name.split("/", 1)[0]
            if base in SKIP_DIR_NAMES:
                continue
            # Never clobber local secrets
            if name in (".env", "backend/.env") or name.endswith("/.env"):
                continue
            tf.extract(member, path=dest)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    host, user, password, remote_dir = deploy_settings()

    include = " ".join(INCLUDE_PATHS)
    pack_cmd = (
        f"cd {remote_dir} && "
        f"tar czf {REMOTE_ARCHIVE} "
        "--exclude=node_modules --exclude=dist --exclude=.git "
        "--exclude='*.map' --exclude=dashboard/node_modules "
        f"{include} 2>/dev/null; "
        f"ls -lh {REMOTE_ARCHIVE}"
    )

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(host, username=user, password=password, timeout=45)
    try:
        print(run(client, f"ls -la {remote_dir} | head -50", timeout=60))
        print(run(client, pack_cmd, timeout=900))

        with tempfile.TemporaryDirectory() as tmp:
            local_tgz = Path(tmp) / "backend_mirror.tgz"
            sftp = client.open_sftp()
            try:
                size = sftp.stat(REMOTE_ARCHIVE).st_size
                print(f"Downloading {REMOTE_ARCHIVE} ({size} bytes) -> {local_tgz}")
                sftp.get(REMOTE_ARCHIVE, str(local_tgz))
            finally:
                sftp.close()

            print(f"Extracting into {BACKEND}")
            extract_safe(local_tgz, BACKEND)

        run(client, f"rm -f {REMOTE_ARCHIVE}", timeout=30)
    finally:
        client.close()

    print("OK mirrored production backend into", BACKEND)


if __name__ == "__main__":
    main()
