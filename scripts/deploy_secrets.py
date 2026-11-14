"""Load JEHO deploy SSH settings from env or scripts/deploy.local.env.

Priority:
  1) Existing process environment (JEHO_DEPLOY_*)
  2) scripts/deploy.local.env (local only — gitignored)
"""
from __future__ import annotations

import os
from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent
_LOCAL_ENV = _SCRIPTS / "deploy.local.env"
_KEYS = (
    "JEHO_DEPLOY_HOST",
    "JEHO_DEPLOY_USER",
    "JEHO_DEPLOY_PASSWORD",
    "JEHO_DEPLOY_DIR",
)


def _parse_env_file(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip().strip('"').strip("'")
        if key:
            out[key] = val
    return out


def load_deploy_env() -> None:
    """Populate os.environ for missing JEHO_DEPLOY_* keys from deploy.local.env."""
    file_vals = _parse_env_file(_LOCAL_ENV)
    for key in _KEYS:
        if os.environ.get(key):
            continue
        if key in file_vals and file_vals[key]:
            os.environ[key] = file_vals[key]


def deploy_settings() -> tuple[str, str, str, str]:
    """Return (host, user, password, remote_dir)."""
    load_deploy_env()
    host = os.environ.get("JEHO_DEPLOY_HOST", "79.143.180.50")
    user = os.environ.get("JEHO_DEPLOY_USER", "root")
    password = os.environ.get("JEHO_DEPLOY_PASSWORD", "")
    remote_dir = os.environ.get(
        "JEHO_DEPLOY_DIR",
        "/www/wwwroot/api.adnova.bbs.tr",
    )
    if not password:
        raise SystemExit(
            "Missing JEHO_DEPLOY_PASSWORD. Set it in the environment or create "
            f"{_LOCAL_ENV.name} (see deploy.local.env.example)."
        )
    return host, user, password, remote_dir
