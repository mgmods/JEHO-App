"""Generate animated VIP1–7 + Monthly S2 frames from Mikoo animal/ornate SVGAs.

Overwrites static VIP PNG previews with recolored animated art and writes matching .svga.
Updates catalog.json animationUrl / hasSvga for those codes only.
"""
from __future__ import annotations

import json
import zlib
from pathlib import Path

from PIL import Image

# Reuse protobuf helpers from the ornate generator.
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
CACHE_TAG = "vip-beast-svga1"

# Wild-beast themed recolors of existing ornate SVGA (lion / dragon / wings / furnace).
VARIANTS = [
    {
        "code": "frame_mikoo_85_vip1",
        "name": "VIP1 · أسد برونزي",
        "source": "frame_mikoo_276_golden_lion_crown.svga",
        "hue": 18,
        "sat": 1.05,
        "bright": 0.92,
        "reverse": False,
        "minVipLevel": 1,
        "coinPrice": 499,
        "sortOrder": 201,
    },
    {
        "code": "frame_mikoo_86_vip2",
        "name": "VIP2 · نمر فضي",
        "source": "frame_mikoo_342_world_cup_platinum.svga",
        "hue": 200,
        "sat": 0.55,
        "bright": 1.12,
        "reverse": True,
        "minVipLevel": 2,
        "coinPrice": 799,
        "sortOrder": 202,
    },
    {
        "code": "frame_mikoo_87_vip3",
        "name": "VIP3 · تنين زمردي",
        "source": "frame_mikoo_277_dragon_in_the_clouds.svga",
        "hue": 125,
        "sat": 1.25,
        "bright": 1.0,
        "reverse": False,
        "minVipLevel": 3,
        "coinPrice": 1299,
        "sortOrder": 203,
    },
    {
        "code": "frame_mikoo_88_vip4",
        "name": "VIP4 · ديناصور ياقوتي",
        "source": "frame_mikoo_343_golden_dragon.svga",
        "hue": 215,
        "sat": 1.2,
        "bright": 1.05,
        "reverse": True,
        "minVipLevel": 4,
        "coinPrice": 1999,
        "sortOrder": 204,
    },
    {
        "code": "frame_mikoo_89_vip5",
        "name": "VIP5 · عنقاء قرمزية",
        "source": "frame_mikoo_280_golden_furnace.svga",
        "hue": 350,
        "sat": 1.35,
        "bright": 1.08,
        "reverse": False,
        "minVipLevel": 5,
        "coinPrice": 2999,
        "sortOrder": 205,
    },
    {
        "code": "frame_mikoo_90_vip6",
        "name": "VIP6 · تنين أسود بنفسجي",
        "source": "frame_mikoo_277_dragon_in_the_clouds.svga",
        "hue": 275,
        "sat": 1.3,
        "bright": 0.88,
        "reverse": True,
        "minVipLevel": 6,
        "coinPrice": 4499,
        "sortOrder": 206,
    },
    {
        "code": "frame_mikoo_91_vip7",
        "name": "VIP7 · تنين ذهبي ملوكي",
        "source": "frame_mikoo_343_golden_dragon.svga",
        "hue": 32,
        "sat": 1.15,
        "bright": 1.12,
        "reverse": False,
        "minVipLevel": 7,
        "coinPrice": 6999,
        "sortOrder": 207,
    },
    {
        "code": "frame_mikoo_237_monthly_s2_agency",
        "name": "رتبة S2 · عنقاء سماوية",
        "source": "frame_mikoo_276_golden_lion_crown.svga",
        "hue": 175,
        "sat": 1.22,
        "bright": 1.1,
        "reverse": True,
        "minVipLevel": 0,
        "coinPrice": 3999,
        "sortOrder": 2027,
    },
]


def process_one(variant: dict) -> dict:
    src = FRAMES / variant["source"]
    if not src.exists():
        raise FileNotFoundError(src)
    raw = zlib.decompress(src.read_bytes())
    version, params, images, sprites = parse_movie(raw)
    print(
        f"{variant['code']}: source={variant['source']} "
        f"images={len(images)} sprites={len(sprites)} hue={variant['hue']}"
    )
    new_images = [
        (
            key,
            recolor_image_bytes(blob, variant["hue"], variant["sat"], variant["bright"]),
        )
        for key, blob in images
    ]
    new_sprites = sprites
    if variant.get("reverse"):
        new_sprites = [reverse_sprite_frames(s) for s in sprites]

    svga = rebuild_movie(version, params, new_images, new_sprites)
    stem = variant["code"]
    svga_path = FRAMES / f"{stem}.svga"
    png_path = FRAMES / f"{stem}.png"
    # Remove prior static-only preview if present; rewrite both.
    svga_path.write_bytes(svga)

    preview = first_png_preview(new_images)
    if preview is None:
        base_png = FRAMES / variant["source"].replace(".svga", ".png")
        preview = shift_hue_rgba(
            Image.open(base_png), variant["hue"], variant["sat"], variant["bright"]
        )
    canvas = Image.new("RGBA", (300, 300), (0, 0, 0, 0))
    preview.thumbnail((300, 300), Image.Resampling.LANCZOS)
    ox = (300 - preview.size[0]) // 2
    oy = (300 - preview.size[1]) // 2
    canvas.alpha_composite(preview, (ox, oy))
    canvas.save(png_path, format="PNG", optimize=True)
    print(f"  wrote {svga_path.name} ({svga_path.stat().st_size}) {png_path.name}")

    return {
        "code": variant["code"],
        "name": variant["name"],
        "previewUrl": f"/assets/cosmetics/frames/{stem}.png?v={CACHE_TAG}",
        "animationUrl": f"/assets/cosmetics/frames/{stem}.svga?v={CACHE_TAG}",
        "coinPrice": variant["coinPrice"],
        "minVipLevel": variant["minVipLevel"],
        "sortOrder": variant["sortOrder"],
        "meta": {
            "source": "jeho_vip_beast_svga",
            "basedOn": variant["source"],
            "hasSvga": True,
            "cdnSvga": None,
            "aristocracy": variant["minVipLevel"] > 0,
            "pricedMall": True,
            "vipLevel": variant["minVipLevel"] if variant["minVipLevel"] > 0 else None,
            "hue": variant["hue"],
            "reverseMotion": bool(variant.get("reverse")),
        },
    }


def main() -> None:
    updates = {it["code"]: process_one(it) for it in VARIANTS}
    raw = json.loads(CATALOG.read_text(encoding="utf-8"))
    existing = list(raw.get("items") or [])
    for i, it in enumerate(existing):
        code = str(it.get("code") or "")
        if code not in updates:
            continue
        u = updates[code]
        # Preserve type; merge fields we own.
        it["name"] = u["name"]
        it["previewUrl"] = u["previewUrl"]
        it["animationUrl"] = u["animationUrl"]
        it["coinPrice"] = u["coinPrice"]
        it["minVipLevel"] = u["minVipLevel"]
        it["sortOrder"] = u["sortOrder"]
        meta = dict(it.get("meta") or {})
        meta.update({k: v for k, v in u["meta"].items() if v is not None})
        it["meta"] = meta
        existing[i] = it
    raw["items"] = existing
    raw["count"] = len(existing)
    ver = str(raw.get("version") or "")
    if "vip-beast" not in ver:
        raw["version"] = f"{ver}+vip-beast".strip("+")
    CATALOG.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"catalog updated ({len(updates)} VIP/S2 frames)")


if __name__ == "__main__":
    main()
