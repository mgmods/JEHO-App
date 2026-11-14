"""Rebuild game hub covers from each Mikoo package's real splash art (not placeholder)."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MIKOO = ROOT / "backend" / "public" / "games" / "mikoo"
COVERS = MIKOO / "covers"
OUT_SIZE = 640
CACHE_TAG = "20260804g"

# Prefer real package splash; fallback hand-tuned theme if only stub splash exists.
THEMES: dict[str, tuple[str, str, tuple[int, int, int], tuple[int, int, int]]] = {
    "7updown": ("7↑↓", "Up Down", (20, 90, 40), (40, 180, 90)),
    "cleopatra-slot": ("كليوباترا", "Cleopatra", (90, 50, 10), (210, 160, 40)),
    "cleopatra-slots": ("كليوباترا", "Cleopatra", (70, 20, 90), (190, 90, 200)),
    "crash": ("كراش", "Crash", (10, 30, 70), (220, 50, 50)),
    "fishing": ("صيد", "Fishing", (0, 60, 110), (30, 180, 220)),
    "football-plinko": ("بلينكو", "Plinko", (10, 80, 20), (50, 200, 70)),
    "fortune-slot": ("جواهر", "Gems", (80, 0, 100), (255, 120, 40)),
    "greedy-box": ("صندوق", "Greedy", (90, 40, 0), (255, 180, 0)),
    "hilo": ("هاي لو", "HiLo", (20, 20, 80), (80, 140, 255)),
    "line-slots": ("خطوط", "Lines", (50, 10, 60), (180, 60, 220)),
    "luck-car": ("سيارة", "Luck Car", (20, 20, 20), (255, 50, 50)),
    "lucky77": ("لاكي 77", "Lucky 77", (40, 0, 0), (220, 30, 30)),
    "megaways-slots": ("ميجا", "Megaways", (0, 40, 80), (0, 180, 255)),
    "olympians": ("أولمبي", "Olympians", (40, 20, 90), (255, 200, 40)),
    "pirate-king": ("قراصنة", "Pirate", (10, 40, 50), (200, 160, 40)),
    "royal-battle": ("ملكي", "Royal", (50, 10, 10), (220, 170, 40)),
    "slot777": ("سلوت", "Slot 777", (20, 0, 40), (255, 50, 120)),
    "sugar-rush": ("سكر", "Sugar", (100, 20, 80), (255, 100, 180)),
    "swimsuit-party": ("سباحة", "Party", (0, 70, 120), (40, 200, 230)),
}


def has_arabic(s: str) -> bool:
    return any("\u0600" <= c <= "\u06FF" for c in s or "")


def pick_source(game_id: str) -> Path | None:
    game_dir = MIKOO / game_id
    if not game_dir.is_dir():
        return None
    candidates: list[Path] = []
    for pat in ("splash*.webp", "splash*.png", "splash*.jpg", "splash.png"):
        candidates.extend(game_dir.glob(pat))
    # Real package splash is usually 40KB+
    ranked = sorted(
        (p for p in candidates if p.is_file()),
        key=lambda p: p.stat().st_size,
        reverse=True,
    )
    for p in ranked:
        if p.stat().st_size >= 25_000:
            return p
    # Existing cover if decent size
    for ext in (".png", ".jpg", ".webp"):
        c = COVERS / f"{game_id}{ext}"
        if c.is_file() and c.stat().st_size >= 40_000:
            return c
    # Largest image in package under ~2MB that is near-square decorative art
    pack_imgs = []
    for pat in ("**/*.png", "**/*.jpg", "**/*.webp", "**/*.jpeg"):
        pack_imgs.extend(game_dir.glob(pat))
    pack_imgs = [
        p
        for p in pack_imgs
        if p.is_file() and 40_000 <= p.stat().st_size <= 1_800_000
        and "splash" not in p.name.lower()
    ]
    pack_imgs.sort(key=lambda p: p.stat().st_size, reverse=True)
    for p in pack_imgs[:25]:
        try:
            with Image.open(p) as im:
                w, h = im.size
            if w < 200 or h < 200:
                continue
            ratio = w / h
            if 0.55 <= ratio <= 1.85:
                return p
        except Exception:
            continue
    return ranked[0] if ranked else None


def to_square_cover(src: Path, dest: Path) -> None:
    im = Image.open(src).convert("RGBA")
    # Some splash is portrait loading art — center-crop to square.
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    # Bias slightly upward for character logos.
    top = max(0, top - side // 12)
    if top + side > h:
        top = h - side
    im = im.crop((left, top, left + side, top + side))
    im = im.resize((OUT_SIZE, OUT_SIZE), Image.Resampling.LANCZOS)
    # Soft vignette edge
    vignette = Image.new("L", (OUT_SIZE, OUT_SIZE), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse((20, 20, OUT_SIZE - 20, OUT_SIZE - 20), fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(18))
    base = Image.new("RGBA", (OUT_SIZE, OUT_SIZE), (12, 10, 24, 255))
    base.paste(im, (0, 0))
    base.putalpha(255)
    rgb = Image.composite(base, Image.new("RGBA", base.size, (12, 10, 24, 255)), vignette)
    rgb.convert("RGB").save(dest, "PNG", optimize=True)


def make_theme_cover(game_id: str, dest: Path) -> None:
    ar, en, c1, c2 = THEMES.get(
        game_id, (game_id, game_id, (30, 30, 50), (120, 80, 200))
    )
    im = Image.new("RGB", (OUT_SIZE, OUT_SIZE), c1)
    d = ImageDraw.Draw(im)
    for i in range(OUT_SIZE):
        t = i / OUT_SIZE
        r = int(c1[0] * (1 - t) + c2[0] * t)
        g = int(c1[1] * (1 - t) + c2[1] * t)
        b = int(c1[2] * (1 - t) + c2[2] * t)
        d.line([(0, i), (OUT_SIZE, i)], fill=(r, g, b))
    # Decorative chip
    d.rounded_rectangle(
        (70, 90, OUT_SIZE - 70, OUT_SIZE - 90),
        radius=48,
        outline=(255, 255, 255, 180),
        width=6,
    )
    d.ellipse((OUT_SIZE // 2 - 90, 140, OUT_SIZE // 2 + 90, 320), fill=(255, 255, 255, 40), outline=(255, 215, 80), width=4)
    try:
        font_big = ImageFont.truetype("arial.ttf", 54)
        font_sm = ImageFont.truetype("arial.ttf", 36)
    except Exception:
        font_big = ImageFont.load_default()
        font_sm = font_big
    # center texts
    for text, y, font in ((ar, 360, font_big), (en, 440, font_sm)):
        bbox = d.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        d.text(((OUT_SIZE - tw) // 2, y), text, fill=(255, 255, 255), font=font)
    im.save(dest, "PNG", optimize=True)


def main() -> None:
    COVERS.mkdir(parents=True, exist_ok=True)
    ids = sorted({p.name for p in MIKOO.iterdir() if p.is_dir() and p.name not in ("covers",)})
    report = []
    for game_id in ids:
        if game_id not in THEMES and not (MIKOO / game_id).is_dir():
            continue
        dest = COVERS / f"{game_id}.png"
        src = pick_source(game_id)
        try:
            if src is not None and src.stat().st_size >= 20_000:
                to_square_cover(src, dest)
                # Remove stale jpg alias that clients may miss
                for alt in COVERS.glob(f"{game_id}.*"):
                    if alt.suffix.lower() != ".png" and alt.name != dest.name:
                        try:
                            alt.unlink()
                        except OSError:
                            pass
                report.append(f"OK  {game_id}: {src.name} -> {dest.name} ({dest.stat().st_size})")
            else:
                make_theme_cover(game_id, dest)
                report.append(f"THEME {game_id}: generated -> {dest.name}")
        except Exception as e:
            make_theme_cover(game_id, dest)
            report.append(f"FALLBACK {game_id}: {e}")
    print(f"cache_tag={CACHE_TAG}")
    for line in report:
        print(line)


if __name__ == "__main__":
    main()
