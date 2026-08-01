"""Extract SVGA frames to PNGs so you can open them in Cursor / Explorer.

Usage:
  python tools/svga-viewer/extract_svga.py path/to/file.svga
  python tools/svga-viewer/extract_svga.py   # extracts all frames/*.svga
"""
from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FRAMES = ROOT / "backend" / "public" / "assets" / "cosmetics" / "frames"
OUT = ROOT / "tools" / "svga-viewer" / "extracted"


def read_varint(buf: bytes, i: int) -> tuple[int, int]:
    x = 0
    s = 0
    while True:
        c = buf[i]
        i += 1
        x |= (c & 0x7F) << s
        if not (c & 0x80):
            return x, i
        s += 7


def parse_params(data: bytes) -> dict:
    i = 0
    out: dict = {}
    while i < min(len(data), 50_000):
        key, i = read_varint(data, i)
        field, wt = key >> 3, key & 7
        if field == 0:
            break
        if wt == 2:
            ln, i = read_varint(data, i)
            chunk = data[i : i + ln]
            i += ln
            if field == 1:
                out["version"] = chunk.decode("utf-8", "ignore")
            elif field == 2:
                j = 0
                p: dict = {}
                while j < len(chunk):
                    k, j = read_varint(chunk, j)
                    f, w = k >> 3, k & 7
                    if w == 5:
                        val = struct.unpack("<f", chunk[j : j + 4])[0]
                        j += 4
                        if f == 1:
                            p["w"] = val
                        elif f == 2:
                            p["h"] = val
                    elif w == 0:
                        v, j = read_varint(chunk, j)
                        if f == 3:
                            p["fps"] = v
                        elif f == 4:
                            p["frames"] = v
                    elif w == 2:
                        ln2, j = read_varint(chunk, j)
                        j += ln2
                    else:
                        break
                out["params"] = p
        elif wt == 0:
            _, i = read_varint(data, i)
        elif wt == 5:
            i += 4
        elif wt == 1:
            i += 8
        else:
            break
        if "version" in out and "params" in out:
            break
    return out


def extract_pngs(data: bytes) -> list[bytes]:
    pngs: list[bytes] = []
    i = 0
    while True:
        j = data.find(b"\x89PNG\r\n\x1a\n", i)
        if j < 0:
            break
        end = data.find(b"IEND", j)
        if end < 0:
            break
        pngs.append(data[j : end + 8])
        i = end + 8
    return pngs


def extract_one(svga: Path, dest_root: Path) -> Path:
    raw = zlib.decompress(svga.read_bytes())
    meta = parse_params(raw)
    dest = dest_root / svga.stem
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "meta.txt").write_text(
        f"file: {svga.name}\n"
        f"version: {meta.get('version')}\n"
        f"params: {meta.get('params')}\n"
        f"decompressed_bytes: {len(raw)}\n",
        encoding="utf-8",
    )
    pngs = extract_pngs(raw)
    for idx, png in enumerate(pngs):
        w = h = 0
        if len(png) >= 24:
            w, h = struct.unpack(">II", png[16:24])
        (dest / f"frame_{idx:03d}_{w}x{h}.png").write_bytes(png)
    print(f"{svga.name}: {len(pngs)} PNGs -> {dest}")
    return dest


def main() -> int:
    args = [Path(a) for a in sys.argv[1:]]
    if not args:
        args = sorted(FRAMES.glob("*.svga"))
    if not args:
        print("No .svga files found")
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    for path in args:
        if not path.is_file():
            print(f"missing: {path}")
            continue
        extract_one(path, OUT)
    print(f"\nOpen folder: {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
