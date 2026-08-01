#!/usr/bin/env python3
"""Compress local Android assets to shrink APK size (GIFs + large PNGs)."""
from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image, ImageSequence

ROOT = Path(r"d:\JEHO-CHAT\android\app\src\main\assets")
EMOJI_DIR = ROOT / "emoji"
VISUAL = ROOT / "visual-system"
SEATS = ROOT / "seats"

GIF_MAX = 112  # seat reaction display ~30dp
PNG_MAX = 640  # host frames / gift sprites on screen are much smaller
PNG_MIN_BYTES = 180_000  # only touch large PNGs


def human(n: int) -> str:
    return f"{n / (1024 * 1024):.2f}MB"


def compress_gif(path: Path) -> tuple[int, int]:
    before = path.stat().st_size
    im = Image.open(path)
    frames: list[Image.Image] = []
    durations: list[int] = []
    for frame in ImageSequence.Iterator(im):
        duration = int(frame.info.get("duration", 50) or 50)
        # Drop ultra-short frames slightly to cut size (keep motion).
        if duration < 20:
            duration = 20
        rgba = frame.convert("RGBA")
        rgba.thumbnail((GIF_MAX, GIF_MAX), Image.Resampling.LANCZOS)
        # Adaptive palette — enough for stickers, much smaller files.
        pal = rgba.convert("P", palette=Image.ADAPTIVE, colors=96)
        frames.append(pal)
        durations.append(duration)
    if not frames:
        return before, before
    # Keep every frame but optimized; disposal=2 reduces ghosting artifacts.
    tmp = path.with_suffix(".tmp.gif")
    frames[0].save(
        tmp,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        optimize=True,
        disposal=2,
    )
    after = tmp.stat().st_size
    if after < before:
        try:
            path.unlink(missing_ok=True)
            tmp.rename(path)
        except OSError:
            # Windows file lock fallback
            import shutil
            shutil.copyfile(tmp, path)
            tmp.unlink(missing_ok=True)
        return before, after
    tmp.unlink(missing_ok=True)
    return before, before


def compress_png(path: Path) -> tuple[int, int]:
    before = path.stat().st_size
    if before < PNG_MIN_BYTES:
        return before, before
    im = Image.open(path)
    if im.mode not in ("RGB", "RGBA", "P"):
        im = im.convert("RGBA")
    elif im.mode == "P":
        im = im.convert("RGBA")
    w, h = im.size
    if max(w, h) > PNG_MAX:
        im = im.copy()
        im.thumbnail((PNG_MAX, PNG_MAX), Image.Resampling.LANCZOS)
    tmp = path.with_suffix(".tmp.png")
    # quantize large opaque-ish art when huge; keep alpha for frames
    if im.mode == "RGBA" and before > 900_000:
        # Always keep alpha; palette conversion destroyed transparent frames.
        im.save(tmp, optimize=True, compress_level=9)
    else:
        im.save(tmp, optimize=True, compress_level=9)
    after = tmp.stat().st_size
    # Reject tiny/corrupt outputs (bad quantize of transparent art).
    if after < before * 0.98 and after > 20_000:
        try:
            path.unlink(missing_ok=True)
            tmp.rename(path)
        except OSError:
            import shutil
            shutil.copyfile(tmp, path)
            tmp.unlink(missing_ok=True)
        return before, after
    tmp.unlink(missing_ok=True)
    return before, before


def walk_pngs(folder: Path):
    if not folder.exists():
        return
    for path in folder.rglob("*.png"):
        yield path


def main() -> int:
    saved = 0
    total_before = 0
    total_after = 0

    print("== emoji GIFs ==")
    print("  SKIPPED — keep original transparent animated GIFs (use restore_seat_gifs.py)")

    print("== visual-system PNGs ==")
    for path in walk_pngs(VISUAL):
        b, a = compress_png(path)
        if b == a and b < PNG_MIN_BYTES:
            continue
        total_before += b
        total_after += a
        saved += b - a
        if b != a:
            print(f"  {path.relative_to(ROOT)}: {human(b)} -> {human(a)}")

    print("== seats PNGs ==")
    for path in walk_pngs(SEATS):
        b, a = compress_png(path)
        total_before += b
        total_after += a
        saved += b - a
        if b != a:
            print(f"  {path.relative_to(ROOT)}: {human(b)} -> {human(a)}")

    print("----")
    print(f"saved ~{human(saved)} (from touched files {human(total_before)} -> {human(total_after)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
