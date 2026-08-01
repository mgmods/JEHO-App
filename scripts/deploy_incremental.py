"""Incremental production deploy: upload only changed files.

Examples:
  python deploy_incremental.py backend/src/modules/agencies/agencies.service.ts
  python deploy_incremental.py backend/dashboard/src/views/AgenciesView.vue
  python deploy_incremental.py --apk

Backend source changes are uploaded individually, then compiled remotely because
NestJS serves JavaScript from dist. Dashboard builds are compared file-by-file
against /admin and only changed build artifacts are uploaded. Public visual
assets are synchronized only with --visual-system. npm install runs only when
package.json or package-lock.json is explicitly supplied. An explicitly
supplied migration is uploaded and executed before the backend restart.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import time
import tempfile
import uuid
import stat
import zipfile
from pathlib import Path, PurePosixPath

import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
HOST = os.environ.get("JEHO_DEPLOY_HOST", "79.143.180.50")
USER = os.environ.get("JEHO_DEPLOY_USER", "root")
PASSWORD = os.environ.get("JEHO_DEPLOY_PASSWORD", "")
if not PASSWORD:
    raise SystemExit(
        "Set JEHO_DEPLOY_PASSWORD in the environment before running deploy_incremental.py"
    )
REMOTE_DIR = os.environ.get(
    "JEHO_DEPLOY_DIR",
    "/www/wwwroot/api.adnova.bbs.tr",
)
BACKEND = ROOT / "backend"
DASHBOARD = BACKEND / "dashboard"
DASH_DIST = DASHBOARD / "dist"
LOCAL_ADMIN = BACKEND / "admin"
OBSOLETE_PUBLIC_ADMIN = BACKEND / "public" / "admin"
OBSOLETE_DASHBOARD_PUBLIC = DASHBOARD / "public"
VISUAL_SOURCE = ROOT / "Host signals"
SERVER_VISUAL_SYSTEM = BACKEND / "public" / "visual-system"
ANDROID_VISUAL_SYSTEM = (
    ROOT / "android" / "app" / "src" / "main" / "assets" / "visual-system"
)
RETIRED_ASSET_DIRS = ("frames", "gifts", "host", "toasts")
RETIRED_LOTTIE_FILES = (
    "frame_glow.json",
    "gift_car.json",
    "gift_castle.json",
    "gift_champagne.json",
    "gift_dragon.json",
    "gift_fireworks.json",
    "gift_heart.json",
    "gift_icecream.json",
    "gift_lion.json",
    "gift_lucky.json",
    "gift_meteor.json",
    "gift_plane.json",
    "gift_ring.json",
    "gift_rocket.json",
    "gift_rose.json",
    "gift_teddy.json",
    "gift_unicorn.json",
    "gift_yacht.json",
)
APK = ROOT / "android" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"


def digest_stream(stream) -> str:
    value = hashlib.sha256()
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        value.update(chunk)
    return value.hexdigest()


def same_file(sftp: paramiko.SFTPClient, local: Path, remote: str) -> bool:
    try:
        if sftp.stat(remote).st_size != local.stat().st_size:
            return False
        with local.open("rb") as source:
            local_hash = digest_stream(source)
        with sftp.open(remote, "rb") as source:
            return local_hash == digest_stream(source)
    except OSError:
        return False


def ensure_remote_dir(sftp: paramiko.SFTPClient, remote_dir: str) -> None:
    current = PurePosixPath("/")
    for part in PurePosixPath(remote_dir).parts[1:]:
        current /= part
        try:
            sftp.stat(str(current))
        except OSError:
            sftp.mkdir(str(current))


def upload_if_changed(
    sftp: paramiko.SFTPClient,
    local: Path,
    remote: str,
) -> bool:
    if same_file(sftp, local, remote):
        return False
    ensure_remote_dir(sftp, str(PurePosixPath(remote).parent))
    temporary = f"{remote}.cursor-upload-{uuid.uuid4().hex}"
    try:
        # Paramiko's pipelined put can overwhelm this host during large visual
        # archives. Synchronous chunks are slower but survive long transfers.
        with local.open("rb") as source, sftp.file(temporary, "wb") as target:
            target.set_pipelined(False)
            while True:
                chunk = source.read(256 * 1024)
                if not chunk:
                    break
                target.write(chunk)
            target.flush()
        try:
            sftp.posix_rename(temporary, remote)
        except OSError:
            try:
                sftp.remove(remote)
            except OSError:
                pass
            sftp.rename(temporary, remote)
    except Exception:
        try:
            sftp.remove(temporary)
        except OSError:
            pass
        raise
    try:
        label = local.relative_to(ROOT)
    except ValueError:
        label = local.name
    print(f"uploaded {label}", flush=True)
    return True


def remove_stale_dashboard_files(
    sftp: paramiko.SFTPClient,
    remote_root: str,
    local_relative_files: set[str],
) -> int:
    removed = 0

    def walk(remote_dir: str, relative_dir: PurePosixPath) -> None:
        nonlocal removed
        try:
            entries = sftp.listdir_attr(remote_dir)
        except OSError:
            return
        for entry in entries:
            remote_path = f"{remote_dir.rstrip('/')}/{entry.filename}"
            relative = relative_dir / entry.filename
            if stat.S_ISDIR(entry.st_mode):
                walk(remote_path, relative)
                try:
                    if not sftp.listdir(remote_path):
                        sftp.rmdir(remote_path)
                except OSError:
                    pass
                continue
            if relative.as_posix() not in local_relative_files:
                sftp.remove(remote_path)
                removed += 1
                print(f"removed stale admin/{relative.as_posix()}", flush=True)

    walk(remote_root, PurePosixPath())
    return removed


def run_remote(client: paramiko.SSHClient, command: str, timeout: int = 300) -> None:
    print(f"$ {command}", flush=True)
    _, stdout, _ = client.exec_command(command, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    while not stdout.channel.exit_status_ready():
        if stdout.channel.recv_ready():
            print(stdout.channel.recv(4096).decode("utf-8", "replace"), end="", flush=True)
        else:
            time.sleep(0.15)
    while stdout.channel.recv_ready():
        print(stdout.channel.recv(4096).decode("utf-8", "replace"), end="", flush=True)
    code = stdout.channel.recv_exit_status()
    if code:
        raise RuntimeError(f"Remote command failed with exit code {code}")


def remote_sha256(client: paramiko.SSHClient, remote: str) -> str | None:
    command = f"test -f {remote} && sha256sum {remote} | cut -d' ' -f1 || true"
    _, stdout, _ = client.exec_command(command, timeout=120)
    value = stdout.read().decode("utf-8", "replace").strip()
    return value or None


def local_sha256(path: Path) -> str:
    with path.open("rb") as source:
        return digest_stream(source)


def upload_atomic(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    ensure_remote_dir(sftp, str(PurePosixPath(remote).parent))
    temporary = f"{remote}.cursor-upload-{uuid.uuid4().hex}"
    try:
        with local.open("rb") as source, sftp.file(temporary, "wb") as target:
            target.set_pipelined(False)
            while True:
                chunk = source.read(256 * 1024)
                if not chunk:
                    break
                target.write(chunk)
            target.flush()
        try:
            sftp.posix_rename(temporary, remote)
        except OSError:
            try:
                sftp.remove(remote)
            except OSError:
                pass
            sftp.rename(temporary, remote)
    except Exception:
        try:
            sftp.remove(temporary)
        except OSError:
            pass
        raise
    print(f"uploaded {local.name}", flush=True)


def normalized_path(raw: str) -> Path:
    candidate = Path(raw)
    if not candidate.is_absolute():
        candidate = ROOT / candidate
    resolved = candidate.resolve()
    if not resolved.is_file():
        raise FileNotFoundError(raw)
    return resolved


def build_dashboard() -> None:
    npm = "npm.cmd" if sys.platform == "win32" else "npm"
    subprocess.run([npm, "run", "build"], cwd=DASHBOARD, check=True)
    for obsolete in (LOCAL_ADMIN, OBSOLETE_PUBLIC_ADMIN, OBSOLETE_DASHBOARD_PUBLIC):
        if obsolete.is_dir():
            shutil.rmtree(obsolete)
        elif obsolete.exists():
            obsolete.unlink()
    shutil.copytree(DASH_DIST, LOCAL_ADMIN)


def build_visual_system() -> None:
    """Wipe and rebuild local visual-system copies from Host signals."""
    required_files = {
        # Keep the existing HTML runtime bridge used by Android/WebView.
        "runtime.html": None,  # copied from previous build if missing in source
        "gifts/gifts.js": "gifts/gifts.js",
        "gifts/gifts.css": "gifts/gifts.css",
        "gifts/catalog.json": "gifts/catalog.json",
        "join-toasts/join-toasts.js": "join-toasts/join-toasts.js",
        "join-toasts/join-toasts.css": "join-toasts/join-toasts.css",
        "host-frames/signal.js": "host-frames/signal.js",
        "host-frames/styles.css": "host-frames/styles.css",
    }
    directories = (
        "gifts/assets",
        "gifts/sprites",
        "gifts/audio",
    )
    previous_runtime = None
    for existing in (SERVER_VISUAL_SYSTEM / "runtime.html", ANDROID_VISUAL_SYSTEM / "runtime.html"):
        if existing.is_file():
            previous_runtime = existing.read_bytes()
            break
    source_runtime = VISUAL_SOURCE / "runtime.html"
    if not source_runtime.is_file() and previous_runtime is None:
        raise FileNotFoundError("runtime.html missing in Host signals and previous visual-system")

    for target in (SERVER_VISUAL_SYSTEM, ANDROID_VISUAL_SYSTEM):
        if target.exists():
            shutil.rmtree(target)
        target.mkdir(parents=True)

        if source_runtime.is_file():
            shutil.copy2(source_runtime, target / "runtime.html")
        else:
            (target / "runtime.html").write_bytes(previous_runtime)

        for source_name, target_name in required_files.items():
            if target_name is None:
                continue
            source = VISUAL_SOURCE / source_name
            if not source.is_file():
                raise FileNotFoundError(source)
            destination = target / target_name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)

        for directory in directories:
            source = VISUAL_SOURCE / directory
            if not source.is_dir():
                raise FileNotFoundError(source)
            shutil.copytree(source, target / directory)

        # Join toasts (banner PNGs) — required by mall + room join FX.
        join_assets_src = VISUAL_SOURCE / "join-toasts" / "assets"
        if not join_assets_src.is_dir() or not any(join_assets_src.glob("*.png")):
            join_assets_src = VISUAL_SOURCE / "join-toasts" / "assets-original-backup"
        if join_assets_src.is_dir():
            join_dest = target / "join-toasts" / "assets"
            join_dest.mkdir(parents=True, exist_ok=True)
            for source in sorted(join_assets_src.glob("*.png")):
                shutil.copy2(source, join_dest / source.name)

        # Entry-effect previews (Mikoo rides mirrored as entry-mikoo-*.png).
        entry_assets_src = VISUAL_SOURCE / "entry-effects" / "assets"
        if entry_assets_src.is_dir():
            entry_dest = target / "entry-effects" / "assets"
            entry_dest.mkdir(parents=True, exist_ok=True)
            for source in sorted(entry_assets_src.glob("*.png")):
                shutil.copy2(source, entry_dest / source.name)
        for entry_name in ("entry-effects.js", "entry-effects.css"):
            entry_file = VISUAL_SOURCE / "entry-effects" / entry_name
            if entry_file.is_file():
                dest_dir = target / "entry-effects"
                dest_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy2(entry_file, dest_dir / entry_name)

        # Host frames: transparent PNGs only (wing system).
        host_assets = target / "host-frames" / "assets"
        host_assets.mkdir(parents=True, exist_ok=True)
        transparent = sorted((VISUAL_SOURCE / "host-frames" / "assets").glob("*-transparent.png"))
        if len(transparent) < 30:
            raise FileNotFoundError(
                f"Expected 30 host transparent frames, found {len(transparent)}"
            )
        for source in transparent:
            shutil.copy2(source, host_assets / source.name)

        # Room card frames when present locally.
        room_assets_src = VISUAL_SOURCE / "room-frames" / "assets"
        if room_assets_src.is_dir() and any(room_assets_src.glob("*.png")):
            room_dest = target / "room-frames" / "assets"
            room_dest.mkdir(parents=True, exist_ok=True)
            for source in sorted(room_assets_src.glob("*.png")):
                shutil.copy2(source, room_dest / source.name)

        # Convenience root index for ops/debug.
        index = VISUAL_SOURCE / "index.html"
        if index.is_file():
            shutil.copy2(index, target / "index.html")
        integration = VISUAL_SOURCE / "INTEGRATION.md"
        if integration.is_file():
            shutil.copy2(integration, target / "INTEGRATION.md")



def cleanup_retired_assets() -> int:
    removed = 0
    for name in RETIRED_ASSET_DIRS:
        target = BACKEND / "public" / "assets" / name
        if target.is_dir():
            removed += sum(1 for path in target.rglob("*") if path.is_file())
            shutil.rmtree(target)
    lottie = BACKEND / "public" / "assets" / "lottie"
    for name in RETIRED_LOTTIE_FILES:
        target = lottie / name
        if target.is_file():
            target.unlink()
            removed += 1
    for pattern in ("host_badge_lv*.json", "join_effect*.json"):
        for target in lottie.glob(pattern):
            if target.is_file():
                target.unlink()
                removed += 1
    return removed


def visual_system_archive() -> Path:
    archive = Path(tempfile.gettempdir()) / "jeho-visual-system.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for path in sorted(SERVER_VISUAL_SYSTEM.rglob("*")):
            if path.is_file():
                output.write(path, path.relative_to(SERVER_VISUAL_SYSTEM).as_posix())
    return archive


def install_apk() -> None:
    if not APK.exists():
        raise FileNotFoundError(f"APK missing: {APK}")
    subprocess.run(["adb", "install", "-r", str(APK)], check=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="*", help="Changed workspace-relative files")
    parser.add_argument("--apk", action="store_true", help="Install existing debug APK")
    parser.add_argument(
        "--visual-system",
        action="store_true",
        help="Package and deploy Host signals runtime to server and Android",
    )
    parser.add_argument(
        "--cleanup-retired-assets",
        action="store_true",
        help="Delete replaced frame, gift, host, and toast asset directories",
    )
    args = parser.parse_args()

    files = [normalized_path(value) for value in args.files]
    allowed_backend_root_files = {
        (BACKEND / "package.json").resolve(),
        (BACKEND / "package-lock.json").resolve(),
        (BACKEND / "tsconfig.json").resolve(),
        (BACKEND / "tsconfig.build.json").resolve(),
        (BACKEND / "nest-cli.json").resolve(),
    }
    allowed_migration_files = {
        (BACKEND / "scripts" / "20260721-professional-room-features.sql").resolve(),
        (BACKEND / "scripts" / "migrate-professional-room-features.js").resolve(),
        (BACKEND / "scripts" / "migrate-visual-system.js").resolve(),
        (BACKEND / "scripts" / "migrate-payments-agents-theme.js").resolve(),
        (BACKEND / "scripts" / "20260723-agency-activation-code.sql").resolve(),
        (BACKEND / "scripts" / "migrate-agency-activation-code.js").resolve(),
        (BACKEND / "scripts" / "20260725-fix-agency-live-rooms.sql").resolve(),
        (BACKEND / "scripts" / "migrate-fix-agency-live-rooms.js").resolve(),
        (BACKEND / "scripts" / "20260725-limit-agency-room-mod-powers.sql").resolve(),
        (BACKEND / "scripts" / "migrate-limit-agency-room-mod-powers.js").resolve(),
        (BACKEND / "scripts" / "20260728-room-gift-sounds.sql").resolve(),
        (BACKEND / "scripts" / "migrate-room-gift-sounds.js").resolve(),
        (BACKEND / "scripts" / "20260728-slot-game-sessions.sql").resolve(),
        (BACKEND / "scripts" / "migrate-slot-games.js").resolve(),
        (BACKEND / "scripts" / "migrate-remove-casual-games.js").resolve(),
        (BACKEND / "scripts" / "20260801-game-ad-reward-enum.sql").resolve(),
        (BACKEND / "scripts" / "migrate-game-ad-reward-enum.js").resolve(),
    }
    backend_files = [
        path for path in files
        if path.is_relative_to(BACKEND / "src")
        or path in allowed_backend_root_files
        or path in allowed_migration_files
    ]
    mikoo_games_root = (BACKEND / "public" / "games" / "mikoo").resolve()
    backend_static_files = [
        path for path in files
        if (
            (
                path.is_relative_to(BACKEND / "public" / "games")
                and (
                    path.is_relative_to(mikoo_games_root)
                    or path.suffix.lower() in {".html", ".js", ".css"}
                )
            )
            or path.is_relative_to(SERVER_VISUAL_SYSTEM)
            or (
                path.is_relative_to(BACKEND / "public")
                and path.suffix.lower() in {
                    ".html", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico",
                    ".gif", ".mp4", ".json", ".svga",
                }
                and not path.is_relative_to(BACKEND / "public" / "admin")
            )
        )
    ]
    dashboard_requested = any(path.is_relative_to(DASHBOARD) for path in files)
    visual_requested = args.visual_system
    cleanup_requested = args.cleanup_retired_assets
    unsupported = [
        path for path in files
        if path not in backend_files
        and path not in backend_static_files
        and not path.is_relative_to(DASHBOARD)
    ]
    if unsupported:
        raise ValueError(
            "Unsupported incremental paths: "
            + ", ".join(str(path.relative_to(ROOT)) for path in unsupported)
        )

    if dashboard_requested:
        build_dashboard()
    if visual_requested:
        build_visual_system()
    local_retired_removed = cleanup_retired_assets() if cleanup_requested else 0
    visual_archive = visual_system_archive() if visual_requested else None
    visual_digest = local_sha256(visual_archive) if visual_archive else None

    if (
        backend_files
        or backend_static_files
        or dashboard_requested
        or visual_requested
        or cleanup_requested
    ):
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        client.connect(HOST, username=USER, password=PASSWORD, timeout=45)
        try:
            if backend_files:
                run_remote(
                    client,
                    f"cd {REMOTE_DIR} && node -e "
                    "'require(\"dotenv\").config(); "
                    "const names=[\"JWT_SECRET\",\"JWT_REFRESH_SECRET\","
                    "\"ADMIN_PASSWORD\",\"DB_PASSWORD\"]; "
                    "const missing=names.filter(n=>!process.env[n]||process.env[n].length<16); "
                    "if(missing.length){console.error(\"Missing/short production secrets: \"+"
                    "missing.join(\",\"));process.exit(1)}; "
                    "if(process.env.JWT_SECRET===process.env.JWT_REFRESH_SECRET){"
                    "console.error(\"JWT secrets must differ\");process.exit(1)}; "
                    "console.log(\"production secret preflight passed\")'",
                    timeout=60,
                )
            sftp = client.open_sftp()
            backend_changed = False
            for path in [*backend_files, *backend_static_files]:
                relative = path.relative_to(BACKEND).as_posix()
                backend_changed |= upload_if_changed(
                    sftp, path, f"{REMOTE_DIR}/{relative}"
                )

            dashboard_changes = 0
            dashboard_removed = 0
            if dashboard_requested:
                dashboard_files = [path for path in DASH_DIST.rglob("*") if path.is_file()]
                dashboard_files.sort(
                    key=lambda path: (
                        path.name == "index.html",
                        path.relative_to(DASH_DIST).as_posix(),
                    )
                )
                for path in dashboard_files:
                    if not path.is_file():
                        continue
                    # Dashboard deploys only executable build artifacts. Static images,
                    # fonts and media are skipped unless Vite emitted a content-hashed
                    # dependency required by the new JS/CSS bundle.
                    executable = path.suffix.lower() in {".html", ".js", ".css", ".map"}
                    hashed_dependency = (
                        path.is_relative_to(DASH_DIST / "assets")
                        and re.search(r"-[A-Za-z0-9_-]{8,}$", path.stem) is not None
                    )
                    if not executable and not hashed_dependency:
                        continue
                    relative = path.relative_to(DASH_DIST).as_posix()
                    if upload_if_changed(
                        sftp, path, f"{REMOTE_DIR}/admin/{relative}"
                    ):
                        dashboard_changes += 1
                local_dashboard_files = {
                    path.relative_to(DASH_DIST).as_posix()
                    for path in dashboard_files
                }
                dashboard_removed = remove_stale_dashboard_files(
                    sftp,
                    f"{REMOTE_DIR}/admin",
                    local_dashboard_files,
                )

            visual_changes = 0
            visual_removed = 0
            if visual_requested:
                remote_archive = f"{REMOTE_DIR}/public/.visual-system.zip"
                if remote_sha256(client, remote_archive) != visual_digest:
                    upload_atomic(sftp, visual_archive, remote_archive)
                    visual_changes = 1
            sftp.close()

            if dashboard_requested:
                # Nginx serves REMOTE_DIR/admin. Remove the obsolete duplicate
                # created by older deploy scripts under backend/public/admin.
                run_remote(client, f"rm -rf {REMOTE_DIR}/public/admin", timeout=60)

            if visual_requested:
                run_remote(
                    client,
                    f"cd {REMOTE_DIR} && rm -rf public/visual-system-staging "
                    "&& mkdir -p public/visual-system-staging "
                    "&& unzip -q public/.visual-system.zip "
                    "-d public/visual-system-staging "
                    "&& test -f public/visual-system-staging/runtime.html "
                    "&& rm -rf public/visual-system "
                    "&& mv public/visual-system-staging public/visual-system",
                    timeout=300,
                )

            if cleanup_requested:
                retired = " ".join(
                    f"{REMOTE_DIR}/public/assets/{name}" for name in RETIRED_ASSET_DIRS
                )
                run_remote(client, f"rm -rf {retired}", timeout=120)
                lottie_root = f"{REMOTE_DIR}/public/assets/lottie"
                retired_lottie = " ".join(
                    f"{lottie_root}/{name}" for name in RETIRED_LOTTIE_FILES
                )
                run_remote(
                    client,
                    f"rm -f {retired_lottie} "
                    f"{lottie_root}/host_badge_lv*.json "
                    f"{lottie_root}/join_effect*.json",
                    timeout=120,
                )

            if backend_files:
                if any(
                    path.name in {"package.json", "package-lock.json"}
                    for path in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && npm install",
                        timeout=600,
                    )
                room_migration = (
                    BACKEND / "scripts" / "migrate-professional-room-features.js"
                ).resolve()
                room_migration_sql = (
                    BACKEND / "scripts" / "20260721-professional-room-features.sql"
                ).resolve()
                visual_migration = (
                    BACKEND / "scripts" / "migrate-visual-system.js"
                ).resolve()
                agency_code_migration = (
                    BACKEND / "scripts" / "migrate-agency-activation-code.js"
                ).resolve()
                agency_code_sql = (
                    BACKEND / "scripts" / "20260723-agency-activation-code.sql"
                ).resolve()
                agency_live_migration = (
                    BACKEND / "scripts" / "migrate-fix-agency-live-rooms.js"
                ).resolve()
                agency_live_sql = (
                    BACKEND / "scripts" / "20260725-fix-agency-live-rooms.sql"
                ).resolve()
                agency_mod_migration = (
                    BACKEND / "scripts" / "migrate-limit-agency-room-mod-powers.js"
                ).resolve()
                agency_mod_sql = (
                    BACKEND / "scripts" / "20260725-limit-agency-room-mod-powers.sql"
                ).resolve()
                gift_sounds_migration = (
                    BACKEND / "scripts" / "migrate-room-gift-sounds.js"
                ).resolve()
                gift_sounds_sql = (
                    BACKEND / "scripts" / "20260728-room-gift-sounds.sql"
                ).resolve()
                slot_games_migration = (
                    BACKEND / "scripts" / "migrate-slot-games.js"
                ).resolve()
                slot_games_sql = (
                    BACKEND / "scripts" / "20260728-slot-game-sessions.sql"
                ).resolve()
                casual_games_migration = (
                    BACKEND / "scripts" / "migrate-remove-casual-games.js"
                ).resolve()
                game_ad_migration = (
                    BACKEND / "scripts" / "migrate-game-ad-reward-enum.js"
                ).resolve()
                game_ad_sql = (
                    BACKEND / "scripts" / "20260801-game-ad-reward-enum.sql"
                ).resolve()
                if room_migration in backend_files or room_migration_sql in backend_files:
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-professional-room-features.js",
                        timeout=300,
                    )
                if (
                    agency_code_migration in backend_files
                    or agency_code_sql in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-agency-activation-code.js",
                        timeout=300,
                    )
                if (
                    agency_live_migration in backend_files
                    or agency_live_sql in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-fix-agency-live-rooms.js",
                        timeout=300,
                    )
                if (
                    agency_mod_migration in backend_files
                    or agency_mod_sql in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-limit-agency-room-mod-powers.js",
                        timeout=300,
                    )
                if (
                    gift_sounds_migration in backend_files
                    or gift_sounds_sql in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-room-gift-sounds.js",
                        timeout=300,
                    )
                if (
                    slot_games_migration in backend_files
                    or slot_games_sql in backend_files
                ):
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-slot-games.js",
                        timeout=300,
                    )
                if casual_games_migration in backend_files:
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-remove-casual-games.js",
                        timeout=300,
                    )
                if game_ad_migration in backend_files or game_ad_sql in backend_files:
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-game-ad-reward-enum.js",
                        timeout=300,
                    )
                if visual_migration in backend_files:
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && mkdir -p backups && set -a "
                        "&& . ./.env && set +a "
                        "&& PGPASSWORD=\"${DB_PASSWORD:-postgres}\" pg_dump "
                        "-h \"${DB_HOST:-localhost}\" -p \"${DB_PORT:-5432}\" "
                        "-U \"${DB_USER:-${DB_USERNAME:-postgres}}\" "
                        "-d \"${DB_NAME:-${DB_DATABASE:-auralive}}\" "
                        "-Fc -f \"backups/pre-visual-system-$(date +%Y%m%d-%H%M%S).dump\"",
                        timeout=600,
                    )
                    run_remote(
                        client,
                        f"cd {REMOTE_DIR} && node scripts/migrate-visual-system.js",
                        timeout=600,
                    )
                run_remote(
                    client,
                    f"cd {REMOTE_DIR} && rm -rf dist.cursor-backup "
                    "&& if [ -d dist ]; then cp -a dist dist.cursor-backup; fi "
                    "&& rm -f tsconfig*.tsbuildinfo "
                    "&& (npm run build || { rm -rf dist; "
                    "if [ -d dist.cursor-backup ]; then mv dist.cursor-backup dist; fi; "
                    "exit 1; }) "
                    "&& rm -rf dist.cursor-backup "
                    "&& pm2 restart auralive-api --update-env",
                    timeout=600,
                )
            print(
                f"incremental deploy complete: backend={len(backend_files)}, "
                f"static={len(backend_static_files)}, "
                f"dashboard_artifacts={dashboard_changes}, "
                f"dashboard_removed={dashboard_removed}, "
                f"visual_artifacts={visual_changes}, "
                f"visual_removed={visual_removed}, "
                f"retired_local_removed={local_retired_removed}",
                flush=True,
            )
        finally:
            client.close()

    if args.apk:
        install_apk()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
