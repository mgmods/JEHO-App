"""Generate mikoo room border tops 4–7 matching ranks 1–3 style."""
from __future__ import annotations

import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC_DIR = Path(r"d:\JEHO-CHAT\backend\public\assets\rooms\mikoo")

# Base templates cycle for structure variety
BASE = {
    4: SRC_DIR / "bg_room_border_top1.webp",  # ornate gold structure
    5: SRC_DIR / "bg_room_border_top2.webp",  # silver structure
    6: SRC_DIR / "bg_room_border_top3.webp",  # rose structure
    7: SRC_DIR / "bg_room_border_top1.webp",
}

# target metal hue shifts (degrees) + number disk RGB
THEMES = {
    # emerald green metal + deep green disk
    4: {"hue_shift": 95, "disk": (12, 110, 70), "digit": (255, 250, 210)},
    # royal purple
    5: {"hue_shift": 260, "disk": (90, 30, 150), "digit": (255, 245, 255)},
    # cyan / ice
    6: {"hue_shift": 175, "disk": (10, 110, 160), "digit": (240, 255, 255)},
    # copper / orange fire
    7: {"hue_shift": 25, "disk": (160, 45, 15), "digit": (255, 245, 200)},
}


def shift_hue_rgba(im: Image.Image, degrees: float) -> Image.Image:
    """Hue-rotate non-transparent pixels; keep alpha."""
    arr = np.asarray(im).astype(np.float32)
    rgb = arr[:, :, :3] / 255.0
    a = arr[:, :, 3:4] / 255.0
    # RGB -> HSV
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
    conditions = [
        (i == 0, (v, t, p)),
        (i == 1, (q, v, p)),
        (i == 2, (p, v, t)),
        (i == 3, (p, q, v)),
        (i == 4, (t, p, v)),
        (i == 5, (v, p, q)),
    ]
    for cond, ch in conditions:
        for c in range(3):
            out[:, :, c] = np.where(cond, ch[c], out[:, :, c])

    rgba = np.dstack([np.clip(out * 255, 0, 255), arr[:, :, 3]])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def find_disk_center(im: Image.Image) -> tuple[int, int, int]:
    """Rough center of the colored number disk (top-right badge)."""
    a = np.asarray(im).astype(np.float32)
    r, g, b, al = a[:, :, 0], a[:, :, 1], a[:, :, 2], a[:, :, 3]
    h, w = r.shape
    # chroma-ish score
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    chroma = (mx - mn) * (al / 255.0)
    # prefer top-right
    score = chroma.copy()
    score[:, : int(w * 0.45)] = 0
    score[int(h * 0.42) :, :] = 0
    score[score < 35] = 0
    ys, xs = np.where(score > 0)
    if len(xs) < 50:
        return int(w * 0.72), int(h * 0.14), 42
    # weighted mean of high-chroma pixels
    wts = score[ys, xs]
    cx = int(np.average(xs, weights=wts))
    cy = int(np.average(ys, weights=wts))
    # radius estimate from spread
    dist = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
    rad = int(np.percentile(dist, 70))
    rad = max(34, min(52, rad))
    return cx, cy, rad


def draw_number(
    im: Image.Image,
    number: int,
    disk_rgb: tuple[int, int, int],
    digit_rgb: tuple[int, int, int],
) -> Image.Image:
    out = im.copy()
    cx, cy, rad = find_disk_center(out)
    draw = ImageDraw.Draw(out)

    # soft disk under number (covers old glyph)
    for k in range(rad, rad - 10, -1):
        alpha = int(230 * (k / rad))
        color = (*disk_rgb, alpha)
        # draw ellipse via temp layer for alpha
        layer = Image.new("RGBA", out.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse((cx - k, cy - k, cx + k, cy + k), fill=color)
        out = Image.alpha_composite(out, layer)

    draw = ImageDraw.Draw(out)
    # fill solid disk for clean glyph base
    layer = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    r_in = int(rad * 0.78)
    # radial-ish fill
    for k in range(r_in, 0, -1):
        t = k / r_in
        col = tuple(int(disk_rgb[i] * (0.55 + 0.45 * t)) for i in range(3)) + (255,)
        ld.ellipse((cx - k, cy - k, cx + k, cy + k), fill=col)
    # highlight ring
    ld.ellipse(
        (cx - r_in, cy - r_in, cx + r_in, cy + r_in),
        outline=(255, 255, 255, 80),
        width=2,
    )
    out = Image.alpha_composite(out, layer)
    draw = ImageDraw.Draw(out)

    font = None
    for path in (
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ):
        if os.path.isfile(path):
            try:
                font = ImageFont.truetype(path, size=int(rad * 1.35))
                break
            except OSError:
                pass
    if font is None:
        font = ImageFont.load_default()

    text = str(number)
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = cx - tw // 2 - bbox[0]
    ty = cy - th // 2 - bbox[1] - 2
    # shadow
    draw.text((tx + 2, ty + 2), text, font=font, fill=(0, 0, 0, 140))
    draw.text((tx, ty), text, font=font, fill=(*digit_rgb, 255))
    # subtle gold rim on digit via second pass thinner? skip
    return out


def main():
    for n in (4, 5, 6, 7):
        base_path = BASE[n]
        theme = THEMES[n]
        im = Image.open(base_path).convert("RGBA")
        # mild hue so metal matches new rank while keeping shape/wings
        shifted = shift_hue_rgba(im, theme["hue_shift"])
        # slight boost saturation via blend with original keeps wing whites
        arr_s = np.asarray(shifted).astype(np.float32)
        arr_o = np.asarray(im).astype(np.float32)
        # keep high-luma near-white wings from original
        luma = (arr_o[:, :, 0] + arr_o[:, :, 1] + arr_o[:, :, 2]) / 3.0
        white_mask = (luma > 200) & (arr_o[:, :, 3] > 40)
        for c in range(3):
            arr_s[:, :, c] = np.where(white_mask, arr_o[:, :, c], arr_s[:, :, c])
        result = Image.fromarray(arr_s.astype(np.uint8), "RGBA")
        result = draw_number(result, n, theme["disk"], theme["digit"])
        out_path = SRC_DIR / f"bg_room_border_top{n}.webp"
        result.save(out_path, "WEBP", quality=92, method=6)
        print("wrote", out_path, result.size)


if __name__ == "__main__":
    main()
