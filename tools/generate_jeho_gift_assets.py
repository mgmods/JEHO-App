#!/usr/bin/env python3
"""
Generate JEHO designed gift assets:
  - Country flag gifts as animated circular frames (SVG + PNG icon + MP4 effect)
  - Premium video gifts styled like entry rides (PNG icon + MP4)

Output under backend/public/assets/gifts/jeho/
"""
from __future__ import annotations

import json
import math
import os
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend" / "public" / "assets" / "gifts" / "jeho"
FLAGS = OUT / "flags"
FLAGS_ANIM = OUT / "flags_anim"
PREMIUM = OUT / "premium"
SIZE = 512
VIDEO_FPS = 24
VIDEO_SECONDS = 2.4


# (iso, ar_name, en_name, coin_price, flag bands: list of (color, weight) or special id)
COUNTRIES = [
    ("sa", "السعودية", "Saudi Arabia", 99, "sa"),
    ("ae", "الإمارات", "UAE", 99, "ae"),
    ("kw", "الكويت", "Kuwait", 79, "kw"),
    ("qa", "قطر", "Qatar", 79, "qa"),
    ("bh", "البحرين", "Bahrain", 59, "bh"),
    ("om", "عُمان", "Oman", 59, "om"),
    ("ye", "اليمن", "Yemen", 49, "ye"),
    ("iq", "العراق", "Iraq", 69, "iq"),
    ("sy", "سوريا", "Syria", 69, "sy"),
    ("jo", "الأردن", "Jordan", 59, "jo"),
    ("lb", "لبنان", "Lebanon", 59, "lb"),
    ("ps", "فلسطين", "Palestine", 49, "ps"),
    ("eg", "مصر", "Egypt", 69, "eg"),
    ("sd", "السودان", "Sudan", 49, "sd"),
    ("ly", "ليبيا", "Libya", 49, "ly"),
    ("tn", "تونس", "Tunisia", 49, "tn"),
    ("dz", "الجزائر", "Algeria", 59, "dz"),
    ("ma", "المغرب", "Morocco", 59, "ma"),
    ("tr", "تركيا", "Turkey", 79, "tr"),
    ("us", "أمريكا", "USA", 89, "us"),
]


def hex_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def draw_flag_rect(draw: ImageDraw.ImageDraw, box, flag_id: str) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    def band_h(colors):
        n = len(colors)
        for i, c in enumerate(colors):
            ya = y0 + int(h * i / n)
            yb = y0 + int(h * (i + 1) / n)
            draw.rectangle([x0, ya, x1, yb], fill=c)

    def band_v(colors):
        n = len(colors)
        for i, c in enumerate(colors):
            xa = x0 + int(w * i / n)
            xb = x0 + int(w * (i + 1) / n)
            draw.rectangle([xa, y0, xb, y1], fill=c)

    if flag_id == "sa":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#006C35"))
        # simple sword + text bar
        draw.rectangle([x0 + w * 0.2, y0 + h * 0.55, x1 - w * 0.2, y0 + h * 0.62], fill=(255, 255, 255))
        draw.ellipse([x0 + w * 0.42, y0 + h * 0.28, x0 + w * 0.58, y0 + h * 0.48], outline=(255, 255, 255), width=4)
    elif flag_id == "ae":
        band_h([hex_rgb("#00732F"), hex_rgb("#FFFFFF"), hex_rgb("#000000")])
        draw.rectangle([x0, y0, x0 + w * 0.28, y1], fill=hex_rgb("#FF0000"))
    elif flag_id == "kw":
        band_h([hex_rgb("#007A3D"), hex_rgb("#FFFFFF"), hex_rgb("#CE1126")])
        draw.polygon([(x0, y0), (x0 + w * 0.32, y0 + h * 0.5), (x0, y1)], fill=(0, 0, 0))
    elif flag_id == "qa":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#8A1538"))
        draw.rectangle([x0, y0, x0 + w * 0.3, y1], fill=(255, 255, 255))
        for i in range(9):
            cy = y0 + int(h * (i + 0.5) / 9)
            draw.polygon(
                [(x0 + w * 0.3, cy - h * 0.06), (x0 + w * 0.42, cy), (x0 + w * 0.3, cy + h * 0.06)],
                fill=hex_rgb("#8A1538"),
            )
    elif flag_id == "bh":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#CE1126"))
        draw.rectangle([x0, y0, x0 + w * 0.3, y1], fill=(255, 255, 255))
        for i in range(5):
            cy = y0 + int(h * (i + 0.5) / 5)
            draw.polygon(
                [(x0 + w * 0.3, cy - h * 0.1), (x0 + w * 0.42, cy), (x0 + w * 0.3, cy + h * 0.1)],
                fill=hex_rgb("#CE1126"),
            )
    elif flag_id == "om":
        band_h([hex_rgb("#FFFFFF"), hex_rgb("#DB161B"), hex_rgb("#008000")])
        draw.rectangle([x0, y0, x0 + w * 0.28, y1], fill=hex_rgb("#DB161B"))
    elif flag_id == "ye":
        band_h([hex_rgb("#CE1126"), hex_rgb("#FFFFFF"), hex_rgb("#000000")])
    elif flag_id == "iq":
        band_h([hex_rgb("#CE1126"), hex_rgb("#FFFFFF"), hex_rgb("#007A3D")])
        draw.rectangle([x0 + w * 0.25, y0 + h * 0.42, x1 - w * 0.25, y0 + h * 0.58], fill=hex_rgb("#000000"))
    elif flag_id == "sy":
        band_h([hex_rgb("#CE1126"), hex_rgb("#FFFFFF"), hex_rgb("#000000")])
        for sx in (0.35, 0.5, 0.65):
            cx, cy = x0 + w * sx, y0 + h * 0.5
            r = min(w, h) * 0.06
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hex_rgb("#007A3D"))
    elif flag_id == "jo":
        band_h([hex_rgb("#000000"), hex_rgb("#FFFFFF"), hex_rgb("#007A3D")])
        draw.polygon([(x0, y0), (x0 + w * 0.35, y0 + h * 0.5), (x0, y1)], fill=hex_rgb("#CE1126"))
        cx, cy = x0 + w * 0.12, y0 + h * 0.5
        r = min(w, h) * 0.04
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255))
    elif flag_id == "lb":
        band_h([hex_rgb("#EE161F"), hex_rgb("#FFFFFF"), hex_rgb("#EE161F")])
        # cedar-ish triangle
        cx = x0 + w * 0.5
        draw.polygon(
            [(cx, y0 + h * 0.28), (cx - w * 0.12, y0 + h * 0.55), (cx + w * 0.12, y0 + h * 0.55)],
            fill=hex_rgb("#00A651"),
        )
        draw.rectangle([cx - w * 0.02, y0 + h * 0.52, cx + w * 0.02, y0 + h * 0.68], fill=hex_rgb("#00A651"))
    elif flag_id == "ps":
        band_h([hex_rgb("#000000"), hex_rgb("#FFFFFF"), hex_rgb("#007A3D")])
        draw.polygon([(x0, y0), (x0 + w * 0.35, y0 + h * 0.5), (x0, y1)], fill=hex_rgb("#CE1126"))
    elif flag_id == "eg":
        band_h([hex_rgb("#CE1126"), hex_rgb("#FFFFFF"), hex_rgb("#000000")])
        draw.ellipse([x0 + w * 0.42, y0 + h * 0.38, x0 + w * 0.58, y0 + h * 0.62], fill=hex_rgb("#C09300"))
    elif flag_id == "sd":
        band_h([hex_rgb("#D21034"), hex_rgb("#FFFFFF"), hex_rgb("#000000")])
        draw.polygon([(x0, y0), (x0 + w * 0.32, y0 + h * 0.5), (x0, y1)], fill=hex_rgb("#007229"))
    elif flag_id == "ly":
        band_h([hex_rgb("#E70013"), hex_rgb("#000000"), hex_rgb("#239E46")])
        cx, cy = x0 + w * 0.5, y0 + h * 0.5
        r = min(w, h) * 0.08
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255), width=3)
    elif flag_id == "tn":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#E70013"))
        cx, cy = x0 + w * 0.5, y0 + h * 0.5
        r = min(w, h) * 0.22
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255))
        r2 = r * 0.7
        draw.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=hex_rgb("#E70013"))
        r3 = r * 0.35
        draw.ellipse([cx - r3 * 0.2, cy - r3, cx + r3 * 1.6, cy + r3], fill=(255, 255, 255))
    elif flag_id == "dz":
        band_v([hex_rgb("#006233"), hex_rgb("#FFFFFF")])
        cx, cy = x0 + w * 0.5, y0 + h * 0.5
        r = min(w, h) * 0.18
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hex_rgb("#D21034"))
        r2 = r * 0.7
        draw.ellipse([cx - r2 * 0.3, cy - r2, cx + r2 * 1.4, cy + r2], fill=hex_rgb("#FFFFFF") if cx > x0 + w * 0.4 else hex_rgb("#006233"))
    elif flag_id == "ma":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#C1272D"))
        cx, cy, r = x0 + w * 0.5, y0 + h * 0.5, min(w, h) * 0.16
        pts = []
        for i in range(5):
            a = math.radians(-90 + i * 72)
            b = math.radians(-90 + i * 72 + 36)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
            pts.append((cx + r * 0.45 * math.cos(b), cy + r * 0.45 * math.sin(b)))
        draw.polygon(pts, outline=hex_rgb("#006233"))
        draw.line(pts + [pts[0]], fill=hex_rgb("#006233"), width=4)
    elif flag_id == "tr":
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#E30A17"))
        cx, cy = x0 + w * 0.4, y0 + h * 0.5
        r = min(w, h) * 0.2
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255))
        r2 = r * 0.78
        draw.ellipse([cx - r2 + r * 0.25, cy - r2, cx + r2 + r * 0.25, cy + r2], fill=hex_rgb("#E30A17"))
        sx, sy, sr = cx + r * 0.85, cy, r * 0.35
        pts = []
        for i in range(5):
            a = math.radians(-90 + i * 72)
            b = math.radians(-90 + i * 72 + 36)
            pts.append((sx + sr * math.cos(a), sy + sr * math.sin(a)))
            pts.append((sx + sr * 0.4 * math.cos(b), sy + sr * 0.4 * math.sin(b)))
        draw.polygon(pts, fill=(255, 255, 255))
    else:  # us-ish
        draw.rectangle([x0, y0, x1, y1], fill=hex_rgb("#B22234"))
        for i in range(7):
            ya = y0 + int(h * i / 13 * 2)
            yb = y0 + int(h * (i * 2 + 1) / 13)
            draw.rectangle([x0, ya, x1, yb], fill=(255, 255, 255))
        draw.rectangle([x0, y0, x0 + w * 0.42, y0 + h * 0.54], fill=hex_rgb("#3C3B6E"))


def oval_mask(size: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    pad = int(size * 0.08)
    d.ellipse([pad, pad, size - pad, size - pad], fill=255)
    return m


def draw_gold_ring(img: Image.Image, t: float) -> None:
    d = ImageDraw.Draw(img, "RGBA")
    s = img.size[0]
    cx = cy = s / 2
    for i, (r, col, w) in enumerate(
        [
            (0.46, (255, 215, 80, 255), 10),
            (0.43, (255, 170, 40, 220), 6),
            (0.40, (255, 240, 180, 180), 3),
        ]
    ):
        rr = s * r
        phase = t * math.pi * 2 + i
        # spark arcs
        for a0 in range(0, 360, 40):
            a = math.radians(a0 + phase * 40)
            x0 = cx + rr * math.cos(a)
            y0 = cy + rr * math.sin(a)
            x1 = cx + rr * math.cos(a + 0.35)
            y1 = cy + rr * math.sin(a + 0.35)
            d.line([(x0, y0), (x1, y1)], fill=col, width=w)
    # outer glow dots
    for k in range(12):
        a = math.radians(k * 30 + t * 360)
        rr = s * (0.47 + 0.02 * math.sin(t * 8 + k))
        x = cx + rr * math.cos(a)
        y = cy + rr * math.sin(a)
        rad = 4 + 2 * math.sin(t * 10 + k)
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(255, 230, 120, 220))


def build_flag_frame(flag_id: str, t: float = 0.0) -> Image.Image:
    s = SIZE
    base = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    # soft dark disc
    disc = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    dd = ImageDraw.Draw(disc)
    pad = int(s * 0.06)
    dd.ellipse([pad, pad, s - pad, s - pad], fill=(12, 14, 28, 240))
    base = Image.alpha_composite(base, disc)

    # flag fill
    flag_layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    fd = ImageDraw.Draw(flag_layer)
    inset = int(s * 0.14)
    draw_flag_rect(fd, (inset, inset, s - inset, s - inset), flag_id)
    # slight wave warp via offset paste strips
    waved = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    strip = 4
    for x in range(inset, s - inset, strip):
        dy = int(6 * math.sin(t * math.pi * 2 + x / 28.0))
        part = flag_layer.crop((x, 0, min(x + strip, s), s))
        waved.paste(part, (x, dy), part)
    mask = oval_mask(s)
    base.paste(waved, (0, 0), mask)
    draw_gold_ring(base, t)
    return base


def write_svg_flag(path: Path, flag_id: str, colors_hint: str) -> None:
    # stylized animated SVG frame (CSS animation) — client/web friendly
    svg = f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200">
  <defs>
    <radialGradient id="g" cx="50%" cy="40%" r="60%">
      <stop offset="0%" stop-color="#2a2040"/>
      <stop offset="100%" stop-color="#0a0a14"/>
    </radialGradient>
    <linearGradient id="gold" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#ffe9a0"/>
      <stop offset="50%" stop-color="#d4a017"/>
      <stop offset="100%" stop-color="#fff0c0"/>
    </linearGradient>
    <clipPath id="circ"><circle cx="100" cy="100" r="72"/></clipPath>
  </defs>
  <circle cx="100" cy="100" r="92" fill="url(#g)"/>
  <g clip-path="url(#circ)">
    <rect x="28" y="28" width="144" height="144" fill="{colors_hint}">
      <animateTransform attributeName="transform" type="translate"
        values="0 0; 0 -3; 0 0; 0 3; 0 0" dur="2.2s" repeatCount="indefinite"/>
    </rect>
  </g>
  <circle cx="100" cy="100" r="76" fill="none" stroke="url(#gold)" stroke-width="6">
    <animate attributeName="stroke-dasharray" values="20 10; 8 22; 20 10" dur="2s" repeatCount="indefinite"/>
    <animateTransform attributeName="transform" type="rotate" from="0 100 100" to="360 100 100" dur="6s" repeatCount="indefinite"/>
  </circle>
  <circle cx="100" cy="100" r="84" fill="none" stroke="#ffd76a" stroke-opacity="0.35" stroke-width="2">
    <animate attributeName="r" values="82;88;82" dur="1.8s" repeatCount="indefinite"/>
  </circle>
  <!-- flag:{flag_id} -->
</svg>
'''
    path.write_text(svg, encoding="utf-8")


def frames_to_mp4(frames: list[Image.Image], path: Path, fps: int = VIDEO_FPS) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    h, w = frames[0].size[1], frames[0].size[0]
    # Try mp4v then avc1
    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(str(path), fourcc, fps, (w, h))
    if not writer.isOpened():
        raise RuntimeError(f"VideoWriter failed: {path}")
    for fr in frames:
        rgb = np.array(fr.convert("RGB"))
        bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
        writer.write(bgr)
    writer.release()


def make_flag_video(flag_id: str, path: Path) -> None:
    n = int(VIDEO_FPS * VIDEO_SECONDS)
    frames = []
    for i in range(n):
        t = i / max(1, n - 1)
        canvas = Image.new("RGBA", (SIZE, SIZE), (8, 6, 18, 255))
        # radial glow pulse
        glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        r = int(SIZE * (0.25 + 0.12 * math.sin(t * math.pi * 2)))
        c = (255, 200, 80, 50)
        gd.ellipse([SIZE // 2 - r, SIZE // 2 - r, SIZE // 2 + r, SIZE // 2 + r], fill=c)
        canvas = Image.alpha_composite(canvas, glow)
        # scale in like entry
        scale = 0.55 + 0.45 * min(1.0, t * 2.2)
        if t > 0.75:
            scale = 1.0 + 0.08 * math.sin((t - 0.75) * 20)
        frame = build_flag_frame(flag_id, t)
        nw = max(8, int(SIZE * scale))
        resized = frame.resize((nw, nw), Image.Resampling.LANCZOS)
        paste = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        paste.paste(resized, ((SIZE - nw) // 2, (SIZE - nw) // 2), resized)
        # particles
        pd = ImageDraw.Draw(paste)
        for k in range(18):
            a = t * 6 + k * 0.7
            rr = SIZE * (0.15 + 0.35 * ((k * 0.13 + t) % 1))
            x = SIZE / 2 + rr * math.cos(a)
            y = SIZE / 2 + rr * math.sin(a * 1.3)
            rad = 2 + (k % 3)
            pd.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(255, 230, 140, 200))
        canvas = Image.alpha_composite(canvas, paste)
        frames.append(canvas.convert("RGB"))
    frames_to_mp4(frames, path)


# -------- premium video gifts (entry-style) --------
PREMIUM_GIFTS = [
    {
        "id": "golden_lion",
        "nameAr": "أسد ذهبي",
        "nameEn": "Golden Lion",
        "price": 1999,
        "sort": 10,
        "style": "lion",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_247_golden_lion_roar.mp4",
    },
    {
        "id": "lightning_lion",
        "nameAr": "أسد البرق",
        "nameEn": "Lightning Lion",
        "price": 2499,
        "sort": 20,
        "style": "lion_blue",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_256_lightning_lion.mp4",
    },
    {
        "id": "dubai_plane",
        "nameAr": "طائرة ذهبية",
        "nameEn": "Golden Airplane",
        "price": 1599,
        "sort": 30,
        "style": "plane",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_179_dubai_golden_airplane.mp4",
    },
    {
        "id": "luxury_car",
        "nameAr": "سيارة فاخرة",
        "nameEn": "Luxury Car",
        "price": 1299,
        "sort": 40,
        "style": "car",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_265_luxury_car_team.mp4",
    },
    {
        "id": "royal_crown",
        "nameAr": "تاج ملكي",
        "nameEn": "Royal Crown",
        "price": 999,
        "sort": 50,
        "style": "crown",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_268_royal_family.mp4",
    },
    {
        "id": "world_trophy",
        "nameAr": "كأس البطولة",
        "nameEn": "Championship Trophy",
        "price": 899,
        "sort": 60,
        "style": "trophy",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_196_football_trophy.mp4",
    },
    {
        "id": "vip_galaxy",
        "nameAr": "مجرة VIP",
        "nameEn": "VIP Galaxy",
        "price": 2999,
        "sort": 5,
        "style": "galaxy",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_73_vip7.mp4",
    },
    {
        "id": "goal_fire",
        "nameAr": "هدف ناري",
        "nameEn": "Fire Goal",
        "price": 699,
        "sort": 70,
        "style": "goal",
        "entryUrl": "/assets/cosmetics/entries/entry_mikoo_190_goal.mp4",
    },
]


def draw_premium_icon(style: str) -> Image.Image:
    s = 384
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # bg gem
    d.ellipse([16, 16, s - 16, s - 16], fill=(18, 12, 40, 255))
    if style in ("lion", "lion_blue"):
        col = (255, 190, 60) if style == "lion" else (100, 180, 255)
        # mane
        for i in range(16):
            a = i / 16 * math.pi * 2
            x = s / 2 + 110 * math.cos(a)
            y = s / 2 + 110 * math.sin(a)
            d.ellipse([x - 28, y - 28, x + 28, y + 28], fill=(*col, 200))
        d.ellipse([s * 0.28, s * 0.28, s * 0.72, s * 0.72], fill=col)
        d.ellipse([s * 0.38, s * 0.4, s * 0.48, s * 0.5], fill=(20, 10, 10))
        d.ellipse([s * 0.52, s * 0.4, s * 0.62, s * 0.5], fill=(20, 10, 10))
        d.polygon([(s * 0.5, s * 0.52), (s * 0.45, s * 0.62), (s * 0.55, s * 0.62)], fill=(20, 10, 10))
    elif style == "plane":
        d.polygon(
            [(s * 0.2, s * 0.55), (s * 0.75, s * 0.4), (s * 0.78, s * 0.48), (s * 0.25, s * 0.62)],
            fill=(255, 210, 80),
        )
        d.polygon([(s * 0.45, s * 0.45), (s * 0.55, s * 0.25), (s * 0.6, s * 0.48)], fill=(255, 230, 120))
        d.polygon([(s * 0.45, s * 0.58), (s * 0.55, s * 0.78), (s * 0.6, s * 0.55)], fill=(255, 230, 120))
    elif style == "car":
        d.rounded_rectangle([s * 0.15, s * 0.4, s * 0.85, s * 0.65], radius=20, fill=(220, 40, 60))
        d.polygon([(s * 0.3, s * 0.4), (s * 0.4, s * 0.28), (s * 0.7, s * 0.28), (s * 0.8, s * 0.4)], fill=(180, 20, 40))
        d.ellipse([s * 0.25, s * 0.58, s * 0.4, s * 0.73], fill=(30, 30, 30))
        d.ellipse([s * 0.6, s * 0.58, s * 0.75, s * 0.73], fill=(30, 30, 30))
    elif style == "crown":
        pts = [(s * 0.2, s * 0.65), (s * 0.25, s * 0.35), (s * 0.4, s * 0.5), (s * 0.5, s * 0.28), (s * 0.6, s * 0.5), (s * 0.75, s * 0.35), (s * 0.8, s * 0.65)]
        d.polygon(pts, fill=(255, 205, 60))
        d.ellipse([s * 0.45, s * 0.22, s * 0.55, s * 0.32], fill=(255, 80, 100))
    elif style == "trophy":
        d.ellipse([s * 0.3, s * 0.2, s * 0.7, s * 0.55], fill=(255, 200, 50))
        d.rectangle([s * 0.45, s * 0.5, s * 0.55, s * 0.7], fill=(200, 160, 40))
        d.rectangle([s * 0.35, s * 0.7, s * 0.65, s * 0.78], fill=(180, 140, 30))
    elif style == "goal":
        d.ellipse([s * 0.25, s * 0.25, s * 0.75, s * 0.75], fill=(240, 240, 240))
        for i in range(5):
            a = i / 5 * math.pi * 2
            x = s / 2 + 50 * math.cos(a)
            y = s / 2 + 50 * math.sin(a)
            d.ellipse([x - 18, y - 18, x + 18, y + 18], outline=(30, 30, 30), width=3)
    else:  # galaxy
        for i in range(40):
            x = (i * 47) % s
            y = (i * 91) % s
            r = 1 + i % 3
            d.ellipse([x, y, x + r, y + r], fill=(200 + i % 55, 180, 255, 200))
        d.ellipse([s * 0.3, s * 0.3, s * 0.7, s * 0.7], fill=(120, 80, 220, 180))
        d.ellipse([s * 0.4, s * 0.4, s * 0.6, s * 0.6], fill=(255, 200, 255, 120))
    # gold ring
    d.ellipse([10, 10, s - 10, s - 10], outline=(255, 210, 80, 255), width=8)
    return img


def make_premium_preview_video(style: str, path: Path) -> None:
    """Short showcase animation (entry-flavored); real gift play uses entry mp4 when present."""
    n = int(VIDEO_FPS * 2.0)
    frames = []
    icon = draw_premium_icon(style)
    for i in range(n):
        t = i / max(1, n - 1)
        canvas = Image.new("RGB", (SIZE, SIZE), (6, 4, 16))
        # streak background
        arr = np.array(canvas)
        for y in range(SIZE):
            v = int(20 + 40 * math.sin(y / 40.0 + t * 8))
            arr[y, :, 0] = max(0, min(255, v // 2))
            arr[y, :, 1] = max(0, min(255, v // 3))
            arr[y, :, 2] = max(0, min(255, v + 30))
        canvas = Image.fromarray(arr.astype(np.uint8)).convert("RGBA")
        scale = 0.4 + 0.6 * min(1.0, t * 1.8)
        if t > 0.7:
            scale = 1.0 + 0.1 * math.sin((t - 0.7) * 25)
        nw = max(8, int(icon.size[0] * scale * SIZE / icon.size[0] * 0.85))
        resized = icon.resize((nw, nw), Image.Resampling.LANCZOS)
        paste = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        # fly from left like entry ride
        xoff = int((1 - min(1.0, t * 1.5)) * (-SIZE * 0.6) + (SIZE - nw) / 2)
        paste.paste(resized, (xoff, (SIZE - nw) // 2), resized)
        pd = ImageDraw.Draw(paste)
        for k in range(24):
            px = (k * 37 + t * 200) % SIZE
            py = (k * 53 + math.sin(t * 5 + k) * 40) % SIZE
            pd.ellipse([px, py, px + 3, py + 3], fill=(255, 220, 120, 180))
        canvas = Image.alpha_composite(canvas, paste)
        frames.append(canvas.convert("RGB"))
    frames_to_mp4(frames, path)


def main() -> None:
    for p in (FLAGS, FLAGS_ANIM, PREMIUM):
        p.mkdir(parents=True, exist_ok=True)

    flag_catalog = []
    palette_hint = {
        "sa": "#006C35",
        "ae": "#FF0000",
        "kw": "#007A3D",
        "qa": "#8A1538",
        "bh": "#CE1126",
        "om": "#DB161B",
        "ye": "#CE1126",
        "iq": "#CE1126",
        "sy": "#CE1126",
        "jo": "#007A3D",
        "lb": "#EE161F",
        "ps": "#CE1126",
        "eg": "#CE1126",
        "sd": "#D21034",
        "ly": "#239E46",
        "tn": "#E70013",
        "dz": "#006233",
        "ma": "#C1272D",
        "tr": "#E30A17",
        "us": "#3C3B6E",
    }

    print("Generating flag frames...")
    for iso, ar, en, price, fid in COUNTRIES:
        icon = build_flag_frame(fid, 0.15)
        icon_path = FLAGS / f"{iso}.png"
        icon.convert("RGBA").save(icon_path)
        write_svg_flag(FLAGS / f"{iso}.svg", fid, palette_hint.get(fid, "#888888"))
        mp4_path = FLAGS_ANIM / f"{iso}.mp4"
        make_flag_video(fid, mp4_path)
        flag_catalog.append(
            {
                "code": iso.upper(),
                "nameAr": f"علم {ar}",
                "nameEn": f"Flag {en}",
                "coinPrice": price,
                "iconUrl": f"/assets/gifts/jeho/flags/{iso}.png",
                "animationUrl": f"/assets/gifts/jeho/flags_anim/{iso}.mp4",
                "svgUrl": f"/assets/gifts/jeho/flags/{iso}.svg",
                "category": "country",
                "sortOrder": 100 + COUNTRIES.index((iso, ar, en, price, fid)),
            }
        )
        print(f"  flag {iso} ok")

    premium_catalog = []
    print("Generating premium video gifts...")
    for g in PREMIUM_GIFTS:
        icon = draw_premium_icon(g["style"])
        icon_path = PREMIUM / f"{g['id']}.png"
        icon.save(icon_path)
        preview = PREMIUM / f"{g['id']}_preview.mp4"
        make_premium_preview_video(g["style"], preview)
        # Prefer real entry ride mp4 for playback quality; fallback to designed preview
        anim = g["entryUrl"]
        premium_catalog.append(
            {
                "nameAr": g["nameAr"],
                "nameEn": g["nameEn"],
                "coinPrice": g["price"],
                "iconUrl": f"/assets/gifts/jeho/premium/{g['id']}.png",
                "animationUrl": anim,
                "previewUrl": f"/assets/gifts/jeho/premium/{g['id']}_preview.mp4",
                "category": "premium",
                "sortOrder": g["sort"],
            }
        )
        print(f"  premium {g['id']} ok")

    catalog = {
        "version": 1,
        "source": "jeho-designed",
        "flags": flag_catalog,
        "premium": premium_catalog,
    }
    (OUT / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT / 'catalog.json'}")
    print("Done.")


if __name__ == "__main__":
    main()
