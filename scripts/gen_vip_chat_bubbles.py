"""Build VIP chat bubble skins 1–7 from Mikoo noble/lottery 9-patch sources.

VIP1 earl · VIP2 marquis · VIP3 duke · VIP4 lottery33440 · VIP5 lottery66666
VIP6 king · VIP7 king hue-shifted (7th set clone)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CURSOR_ASSETS = Path(r"C:\Users\momf\.cursor\projects\d-JEHO-CHAT\assets")
OUT_SERVER = ROOT / "backend" / "public" / "assets" / "chat" / "vip_bubbles"
OUT_ANDROID = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable-xxhdpi"

SCALE = 3  # tiny templates → readable xxhdpi 9-patches


def find_one(pattern: str) -> Path:
    hits = sorted(CURSOR_ASSETS.glob(pattern))
    if not hits:
        raise FileNotFoundError(pattern)
    return hits[0]


SOURCES = {
    1: find_one("*noble_bubble_earl*"),
    2: find_one("*noble_bubble_marquis*"),
    3: find_one("*noble_bubble_duke*"),
    4: find_one("*lottery_box_33440*"),
    5: find_one("*lottery_box_66666*"),
    6: find_one("*noble_bubble_king1*"),
}


def strip_nine_border(im: Image.Image) -> Image.Image:
    """Drop 1px 9-patch marker ring if present."""
    im = im.convert("RGBA")
    w, h = im.size
    if w < 4 or h < 4:
        return im
    px = im.load()

    def is_marker(c):
        return c[3] > 180 and c[0] < 40 and c[1] < 40 and c[2] < 40

    top_m = sum(1 for x in range(w) if is_marker(px[x, 0]))
    left_m = sum(1 for y in range(h) if is_marker(px[0, y]))
    if top_m >= 1 or left_m >= 1:
        return im.crop((1, 1, w - 1, h - 1))
    return im


def shift_hue_rgba(im: Image.Image, degrees: float) -> Image.Image:
    arr = np.asarray(im).astype(np.float32)
    rgb = arr[:, :, :3] / 255.0
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    diff = mx - mn + 1e-8
    h = np.zeros_like(mx)
    mask = mx == r
    h[mask] = ((g - b) / diff)[mask] % 6
    mask = (mx == g) & ~(mx == r)
    h[mask] = ((b - r) / diff)[mask] + 2
    mask = (mx == b) & ~(mx == r) & ~(mx == g)
    h[mask] = ((r - g) / diff)[mask] + 4
    h = h / 6.0
    s = np.where(mx == 0, 0, diff / (mx + 1e-8))
    v = mx
    h = (h + (degrees / 360.0)) % 1.0
    i = np.floor(h * 6).astype(np.int32)
    f = h * 6 - i
    p = v * (1 - s)
    q = v * (1 - f * s)
    t = v * (1 - (1 - f) * s)
    i = i % 6
    out = np.zeros_like(rgb)
    for cond, ch in (
        (i == 0, (v, t, p)),
        (i == 1, (q, v, p)),
        (i == 2, (p, v, t)),
        (i == 3, (p, q, v)),
        (i == 4, (t, p, v)),
        (i == 5, (v, p, q)),
    ):
        for c in range(3):
            out[:, :, c] = np.where(cond, ch[c], out[:, :, c])
    rgba = np.dstack([np.clip(out * 255, 0, 255), arr[:, :, 3]])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def to_nine_patch(content: Image.Image) -> Image.Image:
    """Center stretch + inset padding (content area ≈ dark glass)."""
    content = content.convert("RGBA")
    w, h = content.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(content, (1, 1))
    px = out.load()
    # Stretch band: middle third (keeps corner ornaments crisp)
    x0 = 1 + max(1, w // 3)
    x1 = 1 + min(w - 2, (2 * w) // 3)
    y0 = 1 + max(1, h // 3)
    y1 = 1 + min(h - 2, (2 * h) // 3)
    for x in range(x0, x1 + 1):
        px[x, 0] = (0, 0, 0, 255)
    for y in range(y0, y1 + 1):
        px[0, y] = (0, 0, 0, 255)
    # Content padding ~18% inset
    px0 = 1 + max(2, int(w * 0.18))
    px1 = 1 + min(w - 3, int(w * 0.82))
    py0 = 1 + max(2, int(h * 0.22))
    py1 = 1 + min(h - 3, int(h * 0.78))
    for x in range(px0, px1 + 1):
        px[x, h + 1] = (0, 0, 0, 255)
    for y in range(py0, py1 + 1):
        px[w + 1, y] = (0, 0, 0, 255)
    return out


def prepare_content(path: Path, hue: float | None = None) -> Image.Image:
    im = strip_nine_border(Image.open(path))
    if hue is not None:
        im = shift_hue_rgba(im, hue)
    w, h = im.size
    im = im.resize((w * SCALE, h * SCALE), Image.Resampling.LANCZOS)
    return im


def main() -> None:
    OUT_SERVER.mkdir(parents=True, exist_ok=True)
    OUT_ANDROID.mkdir(parents=True, exist_ok=True)
    contents: dict[int, Image.Image] = {
        n: prepare_content(p) for n, p in SOURCES.items()
    }
    # VIP7: clone king with royal pink/magenta shift
    contents[7] = prepare_content(SOURCES[6], hue=42.0)

    for level in range(1, 8):
        nine = to_nine_patch(contents[level])
        # Server: plain webp preview (no 9 markers) + android-ready 9.png
        plain = contents[level]
        webp = OUT_SERVER / f"vip_chat_bubble_{level}.webp"
        plain.save(webp, "WEBP", quality=92, method=6)
        png9 = OUT_SERVER / f"vip_chat_bubble_{level}.9.png"
        nine.save(png9, "PNG")
        # Android drawable name: vip_chat_bubble_N.9.png
        and_path = OUT_ANDROID / f"vip_chat_bubble_{level}.9.png"
        nine.save(and_path, "PNG")
        print(f"VIP{level}: {plain.size} → {webp.name} + {and_path.name}")
    print("done")


if __name__ == "__main__":
    main()
