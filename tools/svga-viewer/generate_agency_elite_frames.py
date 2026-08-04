"""Agency exclusive frames v2 — only ornate sources, composite PNG previews, zip deploy.

Fixes v1 issues:
- football/thin rings produced empty "broken" previews
- previews took a sparse SVGA layer instead of full frame composite
- too similar/dark recolors

Previews are always built from recolored SOURCE .png (full composed art) + VIP label.
SVGAs still animated from recolored layers of rich sources only.
"""
from __future__ import annotations

import io
import json
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

from generate_jeho_ornate_frames import (  # noqa: E402
    first_png_preview,
    parse_movie,
    rebuild_movie,
    recolor_image_bytes,
    reverse_sprite_frames,
    shift_hue_rgba,
)

ROOT = Path(__file__).resolve().parents[2]
FRAMES = ROOT / "backend" / "public" / "assets" / "cosmetics" / "frames"
CATALOG = ROOT / "backend" / "public" / "assets" / "cosmetics" / "catalog.json"
CACHE_TAG = "agency-elite-v2"

# Only dense, readable artbases (crowns / dragons / ornate platinum / furnace).
# NO thin football/score rings — those appear empty after recolor.
S_LION = "frame_mikoo_276_golden_lion_crown.svga"
S_DRAGON_CLOUD = "frame_mikoo_277_dragon_in_the_clouds.svga"
S_FURNACE = "frame_mikoo_280_golden_furnace.svga"
S_PLATINUM = "frame_mikoo_342_world_cup_platinum.svga"
S_GOLD_DRAGON = "frame_mikoo_343_golden_dragon.svga"
S_STARLIGHT = "frame_mikoo_279_starlight.svga"

VARIANTS: list[dict] = [
    # —— VIP 1–10 —— vivid distinct palettes ——
    {
        "code": "frame_agency_vip1_bronze_crown",
        "name": "وكالة VIP1 · تاج برونزي",
        "label": "VIP 1",
        "source": S_LION,
        "hue": 22, "sat": 1.2, "bright": 0.95, "contrast": 1.12, "color": 1.15,
        "reverse": False, "sortOrder": 9201,
    },
    {
        "code": "frame_agency_vip2_silver_eagle",
        "name": "وكالة VIP2 · نسر فضي",
        "label": "VIP 2",
        "source": S_PLATINUM,
        "hue": 200, "sat": 0.55, "bright": 1.2, "contrast": 1.18, "color": 0.9,
        "reverse": True, "sortOrder": 9202,
    },
    {
        "code": "frame_agency_vip3_emerald_dragon",
        "name": "وكالة VIP3 · تنين زمردي",
        "label": "VIP 3",
        "source": S_DRAGON_CLOUD,
        "hue": 135, "sat": 1.35, "bright": 1.05, "contrast": 1.15, "color": 1.25,
        "reverse": False, "sortOrder": 9203,
    },
    {
        "code": "frame_agency_vip4_royal_crown",
        "name": "وكالة VIP4 · تاج ملكي",
        "label": "VIP 4",
        "source": S_STARLIGHT,
        "hue": 275, "sat": 1.25, "bright": 1.08, "contrast": 1.2, "color": 1.2,
        "reverse": True, "sortOrder": 9204,
    },
    {
        "code": "frame_agency_vip5_golden_eagle",
        "name": "وكالة VIP5 · نسر ذهبي",
        "label": "VIP 5",
        "source": S_PLATINUM,
        "hue": 42, "sat": 1.4, "bright": 1.12, "contrast": 1.15, "color": 1.3,
        "reverse": False, "sortOrder": 9205,
    },
    {
        "code": "frame_agency_vip6_violet_dragon",
        "name": "وكالة VIP6 · تنين بنفسجي",
        "label": "VIP 6",
        "source": S_GOLD_DRAGON,
        "hue": 290, "sat": 1.4, "bright": 1.0, "contrast": 1.18, "color": 1.28,
        "reverse": True, "sortOrder": 9206,
    },
    {
        "code": "frame_agency_vip7_ruby_crown",
        "name": "وكالة VIP7 · تاج ياقوتي",
        "label": "VIP 7",
        "source": S_FURNACE,
        "hue": 348, "sat": 1.45, "bright": 1.08, "contrast": 1.2, "color": 1.3,
        "reverse": False, "sortOrder": 9207,
    },
    {
        "code": "frame_agency_vip8_fire_dragon",
        "name": "وكالة VIP8 · تنين ناري",
        "label": "VIP 8",
        "source": S_GOLD_DRAGON,
        "hue": 8, "sat": 1.5, "bright": 1.1, "contrast": 1.22, "color": 1.35,
        "reverse": False, "sortOrder": 9208,
    },
    {
        "code": "frame_agency_vip9_obsidian_eagle",
        "name": "وكالة VIP9 · نسر أسود",
        "label": "VIP 9",
        "source": S_PLATINUM,
        "hue": 235, "sat": 0.85, "bright": 0.88, "contrast": 1.35, "color": 1.1,
        "reverse": True, "sortOrder": 9209,
    },
    {
        "code": "frame_agency_vip10_platinum_crown",
        "name": "وكالة VIP10 · تاج بلاتيني",
        "label": "VIP 10",
        "source": S_LION,
        "hue": 190, "sat": 0.5, "bright": 1.22, "contrast": 1.2, "color": 0.95,
        "reverse": True, "sortOrder": 9210,
    },
    # —— Levels ——
    {
        "code": "frame_agency_lv20_ice_dragon",
        "name": "وكالة Level 20 · تنين جليدي",
        "label": "LV 20",
        "source": S_DRAGON_CLOUD,
        "hue": 188, "sat": 1.15, "bright": 1.15, "contrast": 1.18, "color": 1.2,
        "reverse": True, "sortOrder": 9220,
    },
    {
        "code": "frame_agency_lv40_crimson_eagle",
        "name": "وكالة Level 40 · نسر قرمزي",
        "label": "LV 40",
        "source": S_FURNACE,
        "hue": 0, "sat": 1.45, "bright": 1.0, "contrast": 1.22, "color": 1.3,
        "reverse": True, "sortOrder": 9221,
    },
    {
        "code": "frame_agency_lv60_emerald_crown",
        "name": "وكالة Level 60 · تاج زمردي",
        "label": "LV 60",
        "source": S_LION,
        "hue": 128, "sat": 1.35, "bright": 1.08, "contrast": 1.15, "color": 1.28,
        "reverse": False, "sortOrder": 9222,
    },
    {
        "code": "frame_agency_lv80_shadow_dragon",
        "name": "وكالة Level 80 · تنين ظل ذهبي",
        "label": "LV 80",
        "source": S_GOLD_DRAGON,
        "hue": 38, "sat": 1.25, "bright": 0.92, "contrast": 1.3, "color": 1.2,
        "reverse": True, "sortOrder": 9223,
    },
    {
        "code": "frame_agency_lv100_king_crown",
        "name": "وكالة Level 100 · ملك الملوك",
        "label": "LV 100",
        "source": S_LION,
        "hue": 48, "sat": 1.4, "bright": 1.15, "contrast": 1.22, "color": 1.3,
        "reverse": False, "sortOrder": 9224,
    },
    # —— Ranks ——
    {
        "code": "frame_agency_rank_a_neon_phoenix",
        "name": "رتبة وكالة A · عنقاء نيون",
        "label": "RANK A",
        "source": S_FURNACE,
        "hue": 310, "sat": 1.4, "bright": 1.12, "contrast": 1.2, "color": 1.3,
        "reverse": True, "sortOrder": 9230,
    },
    {
        "code": "frame_agency_rank_s_royal_dragon",
        "name": "رتبة وكالة S · تنين ملكي",
        "label": "RANK S",
        "source": S_GOLD_DRAGON,
        "hue": 32, "sat": 1.35, "bright": 1.12, "contrast": 1.18, "color": 1.28,
        "reverse": False, "sortOrder": 9231,
    },
    {
        "code": "frame_agency_rank_ss_sky_eagle",
        "name": "رتبة وكالة SS · نسر السماوات",
        "label": "RANK SS",
        "source": S_PLATINUM,
        "hue": 248, "sat": 1.15, "bright": 1.15, "contrast": 1.2, "color": 1.2,
        "reverse": True, "sortOrder": 9232,
    },
]


def _font(size: int) -> ImageFont.ImageFont:
    for p in (
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ):
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            pass
    return ImageFont.load_default()


def accent_from_hue(hue: float) -> tuple[int, int, int]:
    import colorsys

    r, g, b = colorsys.hsv_to_rgb((hue % 360) / 360.0, 0.9, 1.0)
    return int(r * 255), int(g * 255), int(b * 255)


def stamp_label(img: Image.Image, label: str, accent: tuple[int, int, int]) -> Image.Image:
    im = img.convert("RGBA")
    w, h = im.size
    pad = max(6, min(w, h) // 26)
    font_size = max(16, min(w, h) // 8)
    font = _font(font_size)
    draw = ImageDraw.Draw(im)
    bbox = draw.textbbox((0, 0), label, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    box_w = tw + pad * 3
    box_h = th + pad * 2
    x0 = (w - box_w) // 2
    y0 = h - box_h - max(10, h // 16)
    x1, y1 = x0 + box_w, y0 + box_h

    glow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.rounded_rectangle([x0 - 5, y0 - 5, x1 + 5, y1 + 5], radius=box_h // 2 + 5, fill=(*accent, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(3, pad // 2)))
    im = Image.alpha_composite(im, glow)
    draw = ImageDraw.Draw(im)
    draw.rounded_rectangle(
        [x0, y0, x1, y1],
        radius=box_h // 2,
        fill=(8, 10, 16, 230),
        outline=(*accent, 255),
        width=max(2, pad // 3),
    )
    tx = x0 + (box_w - tw) // 2
    ty = y0 + (box_h - th) // 2 - 1
    draw.text((tx + 1, ty + 1), label, font=font, fill=(0, 0, 0, 200))
    draw.text((tx, ty), label, font=font, fill=(255, 248, 230, 255))
    return im


def opaque_score(img: Image.Image) -> float:
    """0..1 — fraction of non-transparent pixels (frame visibility)."""
    a = np.asarray(img.convert("RGBA"))[..., 3]
    return float((a > 24).mean())


def build_preview(variant: dict) -> Image.Image:
    """Always from full source PNG (composite), never a lone SVGA layer."""
    src_png = FRAMES / variant["source"].replace(".svga", ".png")
    if not src_png.exists():
        raise FileNotFoundError(f"missing composite preview {src_png.name}")
    img = shift_hue_rgba(
        Image.open(src_png).convert("RGBA"),
        variant["hue"],
        variant["sat"],
        variant["bright"],
    )
    img = ImageEnhance.Contrast(img).enhance(float(variant.get("contrast", 1.1)))
    img = ImageEnhance.Color(img).enhance(float(variant.get("color", 1.1)))
    # Fit on square transparent canvas
    canvas = Image.new("RGBA", (340, 340), (0, 0, 0, 0))
    img.thumbnail((320, 320), Image.Resampling.LANCZOS)
    ox = (340 - img.size[0]) // 2
    oy = (340 - img.size[1]) // 2
    canvas.alpha_composite(img, (ox, oy))
    score = opaque_score(canvas)
    if score < 0.04:
        raise RuntimeError(f"{variant['code']}: preview too empty score={score:.4f}")
    canvas = stamp_label(canvas, variant["label"], accent_from_hue(variant["hue"]))
    return canvas


def process_one(variant: dict) -> dict:
    src = FRAMES / variant["source"]
    if not src.exists():
        raise FileNotFoundError(src)

    # Full composed preview first (visibility guarantee)
    preview = build_preview(variant)
    png_path = FRAMES / f"{variant['code']}.png"
    preview.save(png_path, format="PNG", optimize=True)

    # Animated SVGA recolor (ornate motion)
    raw = zlib.decompress(src.read_bytes())
    version, params, images, sprites = parse_movie(raw)
    print(
        f"{variant['code']}: src={variant['source']} "
        f"images={len(images)} sprites={len(sprites)} hue={variant['hue']} "
        f"preview_ok={opaque_score(preview):.3f}"
    )
    new_images = [
        (key, recolor_image_bytes(blob, variant["hue"], variant["sat"], variant["bright"]))
        for key, blob in images
    ]
    new_sprites = sprites
    if variant.get("reverse"):
        new_sprites = [reverse_sprite_frames(s) for s in sprites]
    svga = rebuild_movie(version, params, new_images, new_sprites)
    svga_path = FRAMES / f"{variant['code']}.svga"
    svga_path.write_bytes(svga)
    print(f"  wrote {svga_path.name} ({svga_path.stat().st_size // 1024}KB) {png_path.name}")

    stem = variant["code"]
    return {
        "type": "vip_badge",
        "code": stem,
        "name": variant["name"],
        "description": "إطار حصري للوكالات — إدارة فقط، غير متاح بالمول",
        "previewUrl": f"/assets/cosmetics/frames/{stem}.png?v={CACHE_TAG}",
        "animationUrl": f"/assets/cosmetics/frames/{stem}.svga?v={CACHE_TAG}",
        "coinPrice": 0,
        "minVipLevel": 0,
        "minUserLevel": 0,
        "sortOrder": variant["sortOrder"],
        "meta": {
            "source": "jeho_agency_elite_v2",
            "basedOn": variant["source"],
            "hasSvga": True,
            "cdnSvga": None,
            "agencyExclusive": True,
            "pricedMall": False,
            "agencyGrant": "agency_admin",
            "label": variant["label"],
            "hue": variant["hue"],
            "reverseMotion": bool(variant.get("reverse")),
            "theme": "agency_ornate_v2",
        },
    }


def main() -> None:
    FRAMES.mkdir(parents=True, exist_ok=True)
    items = []
    for v in VARIANTS:
        items.append(process_one(v))

    raw = json.loads(CATALOG.read_text(encoding="utf-8"))
    existing = list(raw.get("items") or [])
    by_code = {str(it.get("code")): i for i, it in enumerate(existing)}
    for it in items:
        if it["code"] in by_code:
            row = existing[by_code[it["code"]]]
            row.update({k: it[k] for k in it if k != "meta"})
            meta = dict(row.get("meta") or {})
            meta.update(it["meta"])
            row["meta"] = meta
            existing[by_code[it["code"]]] = row
        else:
            existing.append(it)
    raw["items"] = existing
    raw["count"] = len(existing)
    ver = str(raw.get("version") or "")
    if "agency-elite-v2" not in ver:
        raw["version"] = f"{ver}+agency-elite-v2".strip("+")
    CATALOG.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\ncatalog: {len(items)} frames @ {CACHE_TAG}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("FATAL:", e, file=sys.stderr)
        raise
