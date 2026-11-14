"""Build ornate JEHO frames by recoloring / remotioning existing Mikoo SVGA art.

Keeps wings, crowns, jewels, particle layers — only changes palette + timeline.
"""
from __future__ import annotations

import io
import json
import struct
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
FRAMES = ROOT / "backend" / "public" / "assets" / "cosmetics" / "frames"
CATALOG = ROOT / "backend" / "public" / "assets" / "cosmetics" / "catalog.json"


def enc_varint(n: int) -> bytes:
    out = bytearray()
    n = int(n)
    while n > 0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n & 0x7F)
    return bytes(out)


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


def enc_key(field: int, wt: int) -> bytes:
    return enc_varint((field << 3) | wt)


def enc_bytes(field: int, data: bytes) -> bytes:
    return enc_key(field, 2) + enc_varint(len(data)) + data


def enc_string(field: int, s: str) -> bytes:
    return enc_bytes(field, s.encode("utf-8"))


def parse_movie(data: bytes):
    version = b"2.1.0"
    params = b""
    images: list[tuple[str, bytes]] = []
    sprites: list[bytes] = []
    i = 0
    while i < len(data):
        key, i = read_varint(data, i)
        field, wt = key >> 3, key & 7
        if wt == 2:
            ln, i = read_varint(data, i)
            chunk = data[i : i + ln]
            i += ln
            if field == 1:
                version = chunk
            elif field == 2:
                params = chunk
            elif field == 3:
                j = 0
                ikey = None
                ival = None
                while j < len(chunk):
                    k, j = read_varint(chunk, j)
                    f, w = k >> 3, k & 7
                    if w == 2:
                        l2, j = read_varint(chunk, j)
                        val = chunk[j : j + l2]
                        j += l2
                        if f == 1:
                            ikey = val.decode("utf-8", "ignore")
                        elif f == 2:
                            ival = val
                    elif w == 0:
                        _, j = read_varint(chunk, j)
                    elif w == 5:
                        j += 4
                    elif w == 1:
                        j += 8
                    else:
                        break
                if ikey is not None and ival is not None:
                    images.append((ikey, ival))
            elif field == 4:
                sprites.append(chunk)
            else:
                pass
        elif wt == 0:
            _, i = read_varint(data, i)
        elif wt == 5:
            i += 4
        elif wt == 1:
            i += 8
        else:
            break
    return version, params, images, sprites


def rebuild_movie(version: bytes, params: bytes, images, sprites) -> bytes:
    parts = [enc_bytes(1, version), enc_bytes(2, params)]
    for key, blob in images:
        entry = enc_string(1, key) + enc_bytes(2, blob)
        parts.append(enc_bytes(3, entry))
    for sprite in sprites:
        parts.append(enc_bytes(4, sprite))
    return zlib.compress(b"".join(parts), level=9)


def shift_hue_rgba(img: Image.Image, hue_deg: float, sat: float = 1.0, bright: float = 1.0) -> Image.Image:
    """Fast vectorized hue rotate — keeps ornate art, changes palette only."""
    rgba = np.asarray(img.convert("RGBA"), dtype=np.float32) / 255.0
    rgb = rgba[..., :3]
    a = rgba[..., 3]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    maxc = np.maximum(np.maximum(r, g), b)
    minc = np.minimum(np.minimum(r, g), b)
    v = maxc
    s = np.where(maxc == 0, 0.0, (maxc - minc) / np.maximum(maxc, 1e-8))
    rc = (maxc - r) / np.maximum(maxc - minc, 1e-8)
    gc = (maxc - g) / np.maximum(maxc - minc, 1e-8)
    bc = (maxc - b) / np.maximum(maxc - minc, 1e-8)
    h = np.zeros_like(maxc)
    h = np.where((maxc == r) & (maxc != minc), (bc - gc) % 6.0, h)
    h = np.where((maxc == g) & (maxc != minc), 2.0 + rc - bc, h)
    h = np.where((maxc == b) & (maxc != minc), 4.0 + gc - rc, h)
    h = h / 6.0
    h = (h + ((hue_deg % 360.0) / 360.0)) % 1.0
    s = np.clip(s * sat, 0.0, 1.0)
    v = np.clip(v * bright, 0.0, 1.0)
    i = np.floor(h * 6.0).astype(np.int32)
    f = h * 6.0 - i
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))
    i_mod = i % 6
    out = np.zeros_like(rgb)
    for idx, (rr, gg, bb) in enumerate(
        [
            (v, t, p),
            (q, v, p),
            (p, v, t),
            (p, q, v),
            (t, p, v),
            (v, p, q),
        ]
    ):
        m = i_mod == idx
        out[..., 0] = np.where(m, rr, out[..., 0])
        out[..., 1] = np.where(m, gg, out[..., 1])
        out[..., 2] = np.where(m, bb, out[..., 2])
    # keep near-grays mostly untouched so metal highlights stay crisp
    gray = s < 0.08
    out[gray] = rgb[gray]
    result = np.dstack([out, a])
    result = (np.clip(result, 0.0, 1.0) * 255.0).astype(np.uint8)
    return Image.fromarray(result, "RGBA")


def recolor_image_bytes(blob: bytes, hue_deg: float, sat: float, bright: float) -> bytes:
    # PNG or JPEG
    try:
        img = Image.open(io.BytesIO(blob))
    except Exception:
        return blob
    img = shift_hue_rgba(img, hue_deg, sat, bright)
    out = io.BytesIO()
    # Keep PNG for alpha
    img.save(out, format="PNG", optimize=True)
    return out.getvalue()


def reverse_sprite_frames(sprite_chunk: bytes) -> bytes:
    """Reverse FrameEntity order inside a SpriteEntity to change motion direction."""
    j = 0
    image_key = None
    frames: list[bytes] = []
    extras: list[bytes] = []
    while j < len(sprite_chunk):
        start = j
        k, j = read_varint(sprite_chunk, j)
        f, w = k >> 3, k & 7
        if w == 2:
            l2, j = read_varint(sprite_chunk, j)
            val = sprite_chunk[j : j + l2]
            j += l2
            if f == 1:
                image_key = val
            elif f == 2:
                frames.append(val)
            else:
                extras.append(sprite_chunk[start:j])
        elif w == 0:
            _, j = read_varint(sprite_chunk, j)
            extras.append(sprite_chunk[start:j])
        elif w == 5:
            j += 4
            extras.append(sprite_chunk[start:j])
        elif w == 1:
            j += 8
            extras.append(sprite_chunk[start:j])
        else:
            extras.append(sprite_chunk[start:])
            break
    out = bytearray()
    if image_key is not None:
        out += enc_bytes(1, image_key)
    for fr in reversed(frames):
        out += enc_bytes(2, fr)
    out += b"".join(extras)
    return bytes(out)


def first_png_preview(images) -> Image.Image | None:
    # Prefer largest near-square image as preview
    best = None
    best_area = 0
    for _, blob in images:
        try:
            im = Image.open(io.BytesIO(blob)).convert("RGBA")
        except Exception:
            continue
        area = im.size[0] * im.size[1]
        if area > best_area:
            best = im
            best_area = area
    return best


VARIANTS = [
    {
        "code": "frame_jeho_aurora_cyan",
        "name": "إطار أجنحة بلاتين سماوي",
        "source": "frame_mikoo_342_world_cup_platinum.svga",
        "hue": 185,
        "sat": 1.15,
        "bright": 1.05,
        "reverse": False,
        "coinPrice": 499,
        "sortOrder": 9101,
    },
    {
        "code": "frame_jeho_ruby_pulse",
        "name": "إطار تاج أسد ياقوتي",
        "source": "frame_mikoo_276_golden_lion_crown.svga",
        "hue": 320,
        "sat": 1.25,
        "bright": 1.0,
        "reverse": True,
        "coinPrice": 549,
        "sortOrder": 9102,
    },
    {
        "code": "frame_jeho_emerald_orbit",
        "name": "إطار تنين الغيوم زمردي",
        "source": "frame_mikoo_277_dragon_in_the_clouds.svga",
        "hue": 110,
        "sat": 1.2,
        "bright": 1.02,
        "reverse": False,
        "coinPrice": 529,
        "sortOrder": 9103,
    },
    {
        "code": "frame_jeho_gold_spin",
        "name": "إطار تنين ذهبي ملوكي",
        "source": "frame_mikoo_343_golden_dragon.svga",
        "hue": 25,
        "sat": 1.1,
        "bright": 1.08,
        "reverse": True,
        "coinPrice": 599,
        "sortOrder": 9104,
    },
    {
        "code": "frame_jeho_violet_wave",
        "name": "إطار فرن ذهبي بنفسجي",
        "source": "frame_mikoo_280_golden_furnace.svga",
        "hue": 265,
        "sat": 1.18,
        "bright": 1.0,
        "reverse": True,
        "coinPrice": 579,
        "sortOrder": 9105,
    },
]


def process_one(variant: dict) -> dict:
    src = FRAMES / variant["source"]
    raw = zlib.decompress(src.read_bytes())
    version, params, images, sprites = parse_movie(raw)
    print(
        f"{variant['code']}: source={variant['source']} "
        f"images={len(images)} sprites={len(sprites)} hue={variant['hue']}"
    )
    new_images = []
    for key, blob in images:
        new_images.append(
            (
                key,
                recolor_image_bytes(
                    blob, variant["hue"], variant["sat"], variant["bright"]
                ),
            )
        )
    new_sprites = sprites
    if variant.get("reverse"):
        new_sprites = [reverse_sprite_frames(s) for s in sprites]

    svga = rebuild_movie(version, params, new_images, new_sprites)
    stem = variant["code"]
    svga_path = FRAMES / f"{stem}.svga"
    png_path = FRAMES / f"{stem}.png"
    svga_path.write_bytes(svga)

    preview = first_png_preview(new_images)
    if preview is None:
        # fallback: recolor source preview png
        base_png = FRAMES / variant["source"].replace(".svga", ".png")
        preview = shift_hue_rgba(
            Image.open(base_png), variant["hue"], variant["sat"], variant["bright"]
        )
    # Fit preview on dark square for mall thumb
    canvas = Image.new("RGBA", (300, 300), (0, 0, 0, 0))
    preview.thumbnail((300, 300), Image.Resampling.LANCZOS)
    ox = (300 - preview.size[0]) // 2
    oy = (300 - preview.size[1]) // 2
    canvas.alpha_composite(preview, (ox, oy))
    canvas.save(png_path, format="PNG", optimize=True)
    print(f"  wrote {svga_path.name} ({svga_path.stat().st_size}) {png_path.name}")
    return {
        "type": "vip_badge",
        "code": variant["code"],
        "name": variant["name"],
        "previewUrl": f"/assets/cosmetics/frames/{stem}.png?v=jeho2",
        "animationUrl": f"/assets/cosmetics/frames/{stem}.svga?v=jeho2",
        "coinPrice": variant["coinPrice"],
        "minVipLevel": 0,
        "minUserLevel": 0,
        "sortOrder": variant["sortOrder"],
        "meta": {
            "source": "jeho_ornate_recolor",
            "basedOn": variant["source"],
            "hasSvga": True,
            "hue": variant["hue"],
            "reverseMotion": bool(variant.get("reverse")),
        },
    }


def main() -> None:
    items = [process_one(v) for v in VARIANTS]
    raw = json.loads(CATALOG.read_text(encoding="utf-8"))
    existing = list(raw.get("items") or [])
    by_code = {str(it.get("code")): i for i, it in enumerate(existing)}
    for it in items:
        if it["code"] in by_code:
            existing[by_code[it["code"]]] = it
        else:
            existing.append(it)
    raw["items"] = existing
    raw["count"] = len(existing)
    raw["version"] = "mikoo-import-3-svga+jeho2-ornate"
    CATALOG.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("catalog updated")


if __name__ == "__main__":
    main()
