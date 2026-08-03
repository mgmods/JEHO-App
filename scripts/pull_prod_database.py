#!/usr/bin/env python3
"""Download latest PostgreSQL dump from production into database/."""
from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_secrets import deploy_settings  # noqa: E402

OUT_DIR = ROOT / "database"
STAMP = datetime.now(timezone.utc).strftime("%Y%m%d")
REMOTE_SQL = f"/tmp/jeho_auralive_{STAMP}.sql"
REMOTE_SCHEMA = f"/tmp/jeho_schema_{STAMP}.sql"
LOCAL_FULL = OUT_DIR / f"auralive_prod_{STAMP}.sql"
LOCAL_SCHEMA = OUT_DIR / "schema.sql"
LOCAL_LATEST = OUT_DIR / "auralive_latest.sql"


def run(client: paramiko.SSHClient, cmd: str, timeout: int = 600) -> str:
    print("$", cmd[:180], "..." if len(cmd) > 180 else "")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if code != 0:
        raise RuntimeError(f"exit {code}\nSTDOUT:\n{out}\nSTDERR:\n{err}")
    if err.strip():
        sys.stdout.buffer.write((err[:800] + "\n").encode("utf-8", "replace"))
    return out


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    host, user, password, remote_dir = deploy_settings()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(host, username=user, password=password, timeout=45)
    try:
        # Load .env without rewriting production file (strip BOM / CR if present).
        prep = (
            f"cd {remote_dir} && set -a && "
            "source <(sed '1s/^\\xEF\\xBB\\xBF//' .env | tr -d '\\r') && set +a && "
            'echo "db=${DB_DATABASE:-${DB_NAME:-auralive}}" && '
            'PGPASSWORD="${DB_PASSWORD:-postgres}" psql '
            '-h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" '
            '-U "${DB_USERNAME:-${DB_USER:-postgres}}" '
            '-d "${DB_DATABASE:-${DB_NAME:-auralive}}" '
            '-c "SELECT pg_size_pretty(pg_database_size(current_database())) AS size, '
            "current_database() AS name;\""
        )
        info = run(client, prep, timeout=60)
        print(info)

        dump_cmd = (
            f"cd {remote_dir} && set -a && "
            "source <(sed '1s/^\\xEF\\xBB\\xBF//' .env | tr -d '\\r') && set +a && "
            f'PGPASSWORD="${{DB_PASSWORD:-postgres}}" pg_dump '
            f'-h "${{DB_HOST:-localhost}}" -p "${{DB_PORT:-5432}}" '
            f'-U "${{DB_USERNAME:-${{DB_USER:-postgres}}}}" '
            f'-d "${{DB_DATABASE:-${{DB_NAME:-auralive}}}}" '
            f"--no-owner --no-acl --clean --if-exists "
            f"-f {REMOTE_SQL} && "
            f'PGPASSWORD="${{DB_PASSWORD:-postgres}}" pg_dump '
            f'-h "${{DB_HOST:-localhost}}" -p "${{DB_PORT:-5432}}" '
            f'-U "${{DB_USERNAME:-${{DB_USER:-postgres}}}}" '
            f'-d "${{DB_DATABASE:-${{DB_NAME:-auralive}}}}" '
            f"--schema-only --no-owner --no-acl --clean --if-exists "
            f"-f {REMOTE_SCHEMA} && "
            f"ls -lh {REMOTE_SQL} {REMOTE_SCHEMA}"
        )
        print(run(client, dump_cmd, timeout=900))

        sftp = client.open_sftp()
        try:
            print(f"Downloading {REMOTE_SQL} -> {LOCAL_FULL}")
            sftp.get(REMOTE_SQL, str(LOCAL_FULL))
            print(f"Downloading {REMOTE_SCHEMA} -> {LOCAL_SCHEMA}")
            sftp.get(REMOTE_SCHEMA, str(LOCAL_SCHEMA))
        finally:
            sftp.close()

        # Stable alias for "latest full dump"
        LOCAL_LATEST.write_bytes(LOCAL_FULL.read_bytes())

        run(client, f"rm -f {REMOTE_SQL} {REMOTE_SCHEMA}", timeout=30)
    finally:
        client.close()

    print("OK full:", LOCAL_FULL, LOCAL_FULL.stat().st_size)
    print("OK latest alias:", LOCAL_LATEST, LOCAL_LATEST.stat().st_size)
    print("OK schema:", LOCAL_SCHEMA, LOCAL_SCHEMA.stat().st_size)


if __name__ == "__main__":
    main()
