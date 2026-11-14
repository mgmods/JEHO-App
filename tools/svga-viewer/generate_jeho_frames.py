"""Generate JEHO branded animated avatar frames as SVGA 2.1.0 (+ PNG preview)."""
from __future__ import annotations

import json
import math
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "backend" / "public" / "assets" / "cosmetics" / "frames"
CATALOG = ROOT / "backend" / "public" / "assets" / "cosmetics" / "catalog.json"
SIZE = 300
FPS = 12
N_FRAMES = 24
HOLE = 108  # avatar hole radius


def enc_varint(n: int) -> bytes:
    out = bytearray()
    n = int(n)
    while n > 0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n & 0x7F)
    return bytes(out)


def enc_key(field: int, wt: int) -> bytes:
    return enc_varint((field << 3) | wt)


def enc_bytes(field: int, data: bytes) -> bytes:
    return enc_key(field, 2) + enc_varint(len(data)) + data


def enc_string(field: int, s: str) -> bytes:
    return enc_bytes(field, s.encode("utf-8"))


def enc_float(field: int, value: float) -> bytes:
    return enc_key(field, 5) + struct.pack("<f", float(value))


def enc_int(field: int, value: int) -> bytes:
    return enc_key(field, 0) + enc_varint(int(value))


def build_svga(png_frames: list[bytes], fps: int = FPS) -> bytes:
    n = len(png_frames)
    params = (
        enc_float(1, float(SIZE))
        + enc_float(2, float(SIZE))
        + enc_int(3, fps)
        + enc_int(4, n)
    )
    parts = [enc_string(1, "2.1.0"), enc_bytes(2, params)]

    for i, png in enumerate(png_frames):
        key = f"mov_{i:03d}"
        entry = enc_string(1, key) + enc_bytes(2, png)
        parts.append(enc_bytes(3, entry))

    # Each image visible on exactly one timeline frame (Mikoo-style sequence).
    for i in range(n):
        key = f"mov_{i:03d}"
        frames_blob = bytearray()
        for t in range(n):
            if t == i:
                layout = enc_float(3, float(SIZE)) + enc_float(4, float(SIZE))
                frame = enc_float(1, 1.0) + enc_bytes(2, layout)
            else:
                frame = b""
            frames_blob += enc_bytes(2, frame)
        sprite = enc_string(1, key) + bytes(frames_blob)
        parts.append(enc_bytes(4, sprite))

    return zlib.compress(b"".join(parts), level=9)


def cut_avatar_hole(img: Image.Image, radius: int = HOLE) -> Image.Image:
    img = img.convert("RGBA")
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    cx = cy = SIZE // 2
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=255)
    # soft edge
    mask = mask.filter(ImageFilter.GaussianBlur(1.2))
    r, g, b, a = img.split()
    # punch hole: where mask is white, alpha -> 0
    inv = Image.eval(mask, lambda p: 255 - p)
    a = Image.composite(a, Image.new("L", img.size, 0), inv)
    # keep a thin soft ring so hole edge looks clean
    return Image.merge("RGBA", (r, g, b, a))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def draw_glow_ring(draw, cx, cy, r, color, width=10, glow=True):
    for i in range(4 if glow else 1):
        rr = r + i * 2
        alpha = max(40, 180 - i * 40) if glow else 220
        draw.ellipse(
            (cx - rr, cy - rr, cx + rr, cy + rr),
            outline=rgba(color, alpha),
            width=max(2, width - i),
        )


def frame_to_png_bytes(img: Image.Image) -> bytes:
    import io

    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def gen_aurora_cyan(t: int, n: int) -> Image.Image:
    """Cyan aurora ring with rotating spark arcs."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx = cy = SIZE // 2
    phase = (t / n) * math.tau
    colors = [(0, 220, 255), (80, 255, 210), (120, 180, 255)]
    for k, col in enumerate(colors):
        r = 128 + k * 8
        draw_glow_ring(d, cx, cy, r, col, width=8 - k)
    # rotating arcs
    for i in range(8):
        ang = phase + i * (math.tau / 8)
        x0 = cx + math.cos(ang) * 118
        y0 = cy + math.sin(ang) * 118
        x1 = cx + math.cos(ang + 0.55) * 138
        y1 = cy + math.sin(ang + 0.55) * 138
        d.line((x0, y0, x1, y1), fill=rgba((180, 255, 255), 220), width=3)
        d.ellipse((x1 - 3, y1 - 3, x1 + 3, y1 + 3), fill=rgba((255, 255, 255), 230))
    # outer diamond ticks
    for i in range(12):
        ang = -phase * 0.7 + i * (math.tau / 12)
        x = cx + math.cos(ang) * 145
        y = cy + math.sin(ang) * 145
        s = 4 + (i % 3)
        d.polygon(
            [(x, y - s), (x + s, y), (x, y + s), (x - s, y)],
            fill=rgba((0, 255, 220), 200),
        )
    layer = layer.filter(ImageFilter.GaussianBlur(0.6))
    img = Image.alpha_composite(img, layer)
    return cut_avatar_hole(img)


def gen_ruby_pulse(t: int, n: int) -> Image.Image:
    """Magenta/ruby pulse — ring thickness + sparkle breath."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = SIZE // 2
    pulse = 0.5 + 0.5 * math.sin((t / n) * math.tau)
    r = 126 + int(8 * pulse)
    w = 6 + int(6 * pulse)
    draw_glow_ring(d, cx, cy, r, (255, 40, 120), width=w)
    draw_glow_ring(d, cx, cy, r + 14, (255, 120, 60), width=4)
    # crown tip top
    tip_y = cy - r - 18 - int(6 * pulse)
    d.polygon(
        [(cx, tip_y), (cx - 16, cy - r + 6), (cx + 16, cy - r + 6)],
        fill=rgba((255, 80, 140), 240),
    )
    d.ellipse((cx - 5, tip_y - 5, cx + 5, tip_y + 5), fill=rgba((255, 220, 240), 255))
    # orbiting gems
    for i in range(6):
        ang = (t / n) * math.tau + i * (math.tau / 6)
        x = cx + math.cos(ang) * (r + 6)
        y = cy + math.sin(ang) * (r + 6)
        d.ellipse((x - 5, y - 5, x + 5, y + 5), fill=rgba((255, 210, 230), 230))
    img = img.filter(ImageFilter.GaussianBlur(0.4))
    return cut_avatar_hole(img)


def gen_emerald_orbit(t: int, n: int) -> Image.Image:
    """Emerald ring with three orbiting orbs."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = SIZE // 2
    phase = (t / n) * math.tau
    draw_glow_ring(d, cx, cy, 130, (40, 220, 120), width=9)
    draw_glow_ring(d, cx, cy, 118, (20, 120, 80), width=3, glow=False)
    # leaf accents
    for i in range(10):
        ang = phase * 0.4 + i * (math.tau / 10)
        x = cx + math.cos(ang) * 140
        y = cy + math.sin(ang) * 140
        lx = x + math.cos(ang) * 12
        ly = y + math.sin(ang) * 12
        d.line((x, y, lx, ly), fill=rgba((120, 255, 180), 200), width=3)
    for i in range(3):
        ang = phase + i * (math.tau / 3)
        x = cx + math.cos(ang) * 148
        y = cy + math.sin(ang) * 148
        d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=rgba((180, 255, 210), 240))
        d.ellipse((x - 4, y - 4, x + 4, y + 4), fill=rgba((40, 180, 100), 255))
    return cut_avatar_hole(img.filter(ImageFilter.GaussianBlur(0.35)))


def gen_gold_spin(t: int, n: int) -> Image.Image:
    """Gold royal ring with spinning highlights + bottom banner spark."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = SIZE // 2
    phase = (t / n) * math.tau
    gold = (255, 200, 60)
    draw_glow_ring(d, cx, cy, 128, gold, width=10)
    draw_glow_ring(d, cx, cy, 140, (255, 240, 150), width=3)
    # spinning dashes
    for i in range(16):
        ang = phase + i * (math.tau / 16)
        if i % 2 == 0:
            x0 = cx + math.cos(ang) * 118
            y0 = cy + math.sin(ang) * 118
            x1 = cx + math.cos(ang) * 148
            y1 = cy + math.sin(ang) * 148
            d.line((x0, y0, x1, y1), fill=rgba((255, 255, 200), 210), width=2)
    # top crown
    d.polygon(
        [
            (cx - 28, cy - 138),
            (cx - 18, cy - 158),
            (cx, cy - 145),
            (cx + 18, cy - 158),
            (cx + 28, cy - 138),
            (cx + 20, cy - 128),
            (cx - 20, cy - 128),
        ],
        fill=rgba(gold, 245),
    )
    # bottom jewel
    by = cy + 138
    pulse = 4 * math.sin(phase * 2)
    d.ellipse((cx - 10, by - 10 + pulse, cx + 10, by + 10 + pulse), fill=rgba((255, 240, 180), 240))
    return cut_avatar_hole(img.filter(ImageFilter.GaussianBlur(0.4)))


def gen_violet_wave(t: int, n: int) -> Image.Image:
    """Violet energy waves rippling around the ring."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = SIZE // 2
    phase = (t / n) * math.tau
    draw_glow_ring(d, cx, cy, 126, (170, 80, 255), width=8)
    # wavy outer path
    pts = []
    for i in range(48):
        ang = i / 48 * math.tau
        wobble = 8 * math.sin(ang * 4 + phase * 2)
        rr = 142 + wobble
        pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
    d.line(pts + [pts[0]], fill=rgba((220, 160, 255), 220), width=3)
    # lightning ticks
    for i in range(5):
        ang = phase + i * (math.tau / 5)
        x0 = cx + math.cos(ang) * 120
        y0 = cy + math.sin(ang) * 120
        x1 = cx + math.cos(ang + 0.12) * 150
        y1 = cy + math.sin(ang + 0.12) * 150
        x2 = cx + math.cos(ang + 0.22) * 132
        y2 = cy + math.sin(ang + 0.22) * 132
        d.line([(x0, y0), (x1, y1), (x2, y2)], fill=rgba((255, 220, 255), 230), width=2)
    return cut_avatar_hole(img.filter(ImageFilter.GaussianBlur(0.45)))


DESIGNS = [
    {
        "code": "frame_jeho_aurora_cyan",
        "name": "إطار شفق سماوي",
        "slug": "aurora_cyan",
        "fn": gen_aurora_cyan,
        "coinPrice": 299,
        "sortOrder": 9101,
    },
    {
        "code": "frame_jeho_ruby_pulse",
        "name": "إطار ياقوت نابض",
        "slug": "ruby_pulse",
        "fn": gen_ruby_pulse,
        "coinPrice": 349,
        "sortOrder": 9102,
    },
    {
        "code": "frame_jeho_emerald_orbit",
        "name": "إطار زمرد مداري",
        "slug": "emerald_orbit",
        "fn": gen_emerald_orbit,
        "coinPrice": 329,
        "sortOrder": 9103,
    },
    {
        "code": "frame_jeho_gold_spin",
        "name": "إطار ذهب دوار",
        "slug": "gold_spin",
        "fn": gen_gold_spin,
        "coinPrice": 399,
        "sortOrder": 9104,
    },
    {
        "code": "frame_jeho_violet_wave",
        "name": "إطار موج بنفسجي",
        "slug": "violet_wave",
        "fn": gen_violet_wave,
        "coinPrice": 379,
        "sortOrder": 9105,
    },
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    catalog_items = []
    for design in DESIGNS:
        print(f"Generating {design['code']}…")
        pngs: list[bytes] = []
        preview = None
        for t in range(N_FRAMES):
            frame = design["fn"](t, N_FRAMES)
            if t == 0:
                preview = frame
            pngs.append(frame_to_png_bytes(frame))
        svga = build_svga(pngs, FPS)
        stem = design["code"]
        png_path = OUT / f"{stem}.png"
        svga_path = OUT / f"{stem}.svga"
        assert preview is not None
        preview.save(png_path, format="PNG", optimize=True)
        svga_path.write_bytes(svga)
        print(f"  -> {png_path.name} ({png_path.stat().st_size} B), {svga_path.name} ({svga_path.stat().st_size} B)")
        catalog_items.append(
            {
                "type": "vip_badge",
                "code": design["code"],
                "name": design["name"],
                "previewUrl": f"/assets/cosmetics/frames/{stem}.png?v=jeho1",
                "animationUrl": f"/assets/cosmetics/frames/{stem}.svga?v=jeho1",
                "coinPrice": design["coinPrice"],
                "minVipLevel": 0,
                "minUserLevel": 0,
                "sortOrder": design["sortOrder"],
                "meta": {
                    "source": "jeho_original",
                    "hasSvga": True,
                    "fps": FPS,
                    "frames": N_FRAMES,
                },
            }
        )

    # Merge into catalog.json
    raw = json.loads(CATALOG.read_text(encoding="utf-8"))
    items = list(raw.get("items") or [])
    by_code = {str(it.get("code")): i for i, it in enumerate(items)}
    for it in catalog_items:
        code = it["code"]
        if code in by_code:
            items[by_code[code]] = it
        else:
            items.append(it)
    raw["items"] = items
    raw["count"] = len(items)
    raw["version"] = "mikoo-import-3-svga+jeho1"
    CATALOG.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Catalog updated: {len(catalog_items)} JEHO frames, total items={len(items)}")


if __name__ == "__main__":
    main()
