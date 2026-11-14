#!/usr/bin/env python3
"""Copy original seat GIFs from Downloads — no re-encode (keeps transparency)."""
from __future__ import annotations

import shutil
from pathlib import Path

SRC = Path(r"c:\Users\momf\Downloads\animated")
DST = Path(r"d:\JEHO-CHAT\android\app\src\main\assets\emoji")


def main() -> None:
    files = sorted(SRC.glob("*.gif"))
    if len(files) != 26:
        raise SystemExit(f"Expected 26 GIFs, found {len(files)}")
    DST.mkdir(parents=True, exist_ok=True)
    total = 0
    for i, src in enumerate(files, 1):
        out = DST / f"e{i:02d}.gif"
        shutil.copyfile(src, out)
        total += out.stat().st_size
        print(f"{out.name} <- {src.name}")
    print(f"Done. {total / (1024 * 1024):.1f} MB (originals, transparent)")


if __name__ == "__main__":
    main()
