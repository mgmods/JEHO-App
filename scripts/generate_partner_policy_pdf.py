#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Partner-facing JEHO policy PDF — green 3D backgrounds, logo, host/agency/supporter."""

from __future__ import annotations

import math
import os
from datetime import date
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFilter
from reportlab.lib.colors import HexColor, white, Color
from reportlab.lib.enums import TA_CENTER, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    PageBreak,
    Image as RLImage,
    KeepTogether,
    Flowable,
)

# Green luxury palette
GREEN_DEEP = HexColor("#0A3D2E")
GREEN_MID = HexColor("#0F5C45")
GREEN_LIGHT = HexColor("#1A8A68")
GREEN_SOFT = HexColor("#E8F6F0")
GOLD = HexColor("#C9A227")
GOLD_SOFT = HexColor("#E8D48B")
TEXT = HexColor("#0D1F18")
MUTED = HexColor("#3D5C50")
WHITE = white
CARD = HexColor("#F4FBF7")

PAGE_W, PAGE_H = A4
MARGIN = 14 * mm

pdfmetrics.registerFont(TTFont("JehoAr", r"C:\Windows\Fonts\tahoma.ttf"))
pdfmetrics.registerFont(TTFont("JehoArBold", r"C:\Windows\Fonts\tahomabd.ttf"))

ROOT = Path(__file__).resolve().parents[1]
LOGO_CANDIDATES = [
    ROOT / "docs" / "brand" / "jeho_logo.png",
    ROOT / "backend" / "public" / "logo.png",
    ROOT / "backend" / "dashboard" / "src" / "assets" / "brand" / "jeho_logo.png",
]


def ar(text: str) -> str:
    try:
        return get_display(arabic_reshaper.reshape(str(text)))
    except Exception:
        return str(text)


def p(text: str, style: ParagraphStyle) -> Paragraph:
    safe = str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return Paragraph(ar(safe), style)


def make_green_3d_bg(path: Path, w=1240, h=1754, seed=0):
    """Soft green depth with orbs + light beams (3D-ish)."""
    img = Image.new("RGB", (w, h), (10, 61, 46))
    px = img.load()
    # Vertical depth gradient
    for y in range(h):
        t = y / h
        r = int(8 + 18 * (1 - t) + 10 * math.sin(t * 3 + seed))
        g = int(48 + 40 * (1 - t) + 20 * math.cos(t * 2))
        b = int(36 + 28 * (1 - t))
        for x in range(0, w, 2):
            px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))
            if x + 1 < w:
                px[x + 1, y] = px[x, y]
    draw = ImageDraw.Draw(img, "RGBA")
    # Orbs
    orbs = [
        (int(w * 0.2), int(h * 0.15), int(w * 0.45), (26, 138, 104, 70)),
        (int(w * 0.75), int(h * 0.35), int(w * 0.38), (201, 162, 39, 50)),
        (int(w * 0.5), int(h * 0.7), int(w * 0.55), (15, 92, 69, 90)),
        (int(w * 0.15), int(h * 0.8), int(w * 0.3), (232, 212, 139, 40)),
    ]
    for cx, cy, rad, col in orbs:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=col)
        layer = layer.filter(ImageFilter.GaussianBlur(radius=max(8, rad // 8)))
        img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    # Soft bottom card zone
    fade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    for i in range(400):
        a = int(40 * (i / 400))
        fd.rectangle([0, h - 400 + i, w, h - 399 + i], fill=(232, 246, 240, a))
    img = Image.alpha_composite(img.convert("RGBA"), fade).convert("RGB")
    img.save(path, "PNG", quality=92)
    return path


class GreenHeader(Flowable):
    def __init__(self, title: str, subtitle: str = ""):
        super().__init__()
        self.title = title
        self.subtitle = subtitle
        self.h = 18 * mm

    def wrap(self, aw, ah):
        self.w = aw
        return aw, self.h

    def draw(self):
        c = self.canv
        c.setFillColor(GREEN_DEEP)
        c.roundRect(0, 0, self.w, self.h, 6, fill=1, stroke=0)
        c.setFillColor(GOLD)
        c.rect(0, self.h - 3, self.w, 3, fill=1, stroke=0)
        c.setFillColor(WHITE)
        c.setFont("JehoArBold", 13)
        c.drawRightString(self.w - 10, self.h / 2 + (2 if self.subtitle else -3), ar(self.title))
        if self.subtitle:
            c.setFont("JehoAr", 8)
            c.setFillColor(GOLD_SOFT)
            c.drawRightString(self.w - 10, self.h / 2 - 10, ar(self.subtitle))


def styles():
    base = dict(fontName="JehoAr", fontSize=10, leading=15, textColor=TEXT, alignment=TA_RIGHT)
    return {
        "body": ParagraphStyle("body", **base),
        "body_sm": ParagraphStyle("body_sm", **{**base, "fontSize": 9, "leading": 13}),
        "muted": ParagraphStyle("muted", **{**base, "fontSize": 8.5, "textColor": MUTED}),
        "center": ParagraphStyle(
            "center", fontName="JehoArBold", fontSize=18, leading=24,
            textColor=WHITE, alignment=TA_CENTER,
        ),
        "sub_c": ParagraphStyle(
            "sub_c", fontName="JehoAr", fontSize=11, leading=15,
            textColor=GOLD_SOFT, alignment=TA_CENTER,
        ),
        "th": ParagraphStyle(
            "th", fontName="JehoArBold", fontSize=8.5, leading=11,
            textColor=WHITE, alignment=TA_CENTER,
        ),
        "td": ParagraphStyle(
            "td", fontName="JehoAr", fontSize=8.5, leading=11,
            textColor=TEXT, alignment=TA_CENTER,
        ),
        "title_dark": ParagraphStyle(
            "title_dark", fontName="JehoArBold", fontSize=14, leading=18,
            textColor=GREEN_DEEP, alignment=TA_RIGHT,
        ),
    }


def tbl(headers, rows, widths=None):
    st = styles()
    data = [[p(h, st["th"]) for h in headers]]
    for row in rows:
        data.append([p(str(c), st["td"]) for c in row])
    t = Table(data, colWidths=widths, hAlign="CENTER")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), GREEN_DEEP),
                ("BACKGROUND", (0, 1), (-1, -1), CARD),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [CARD, GREEN_SOFT]),
                ("GRID", (0, 0), (-1, -1), 0.4, GREEN_LIGHT),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    return t


def find_logo() -> Path | None:
    for c in LOGO_CANDIDATES:
        if c.exists():
            return c
    return None


def on_page(bg_path: Path, logo_path: Path | None):
    def _draw(canv: canvas.Canvas, doc):
        canv.saveState()
        if bg_path.exists():
            canv.drawImage(str(bg_path), 0, 0, width=PAGE_W, height=PAGE_H, preserveAspectRatio=False, mask="auto")
        # Gold edge rails
        canv.setStrokeColor(GOLD)
        canv.setLineWidth(1.2)
        canv.rect(8 * mm, 10 * mm, PAGE_W - 16 * mm, PAGE_H - 20 * mm)
        # footer
        canv.setFillColor(GREEN_DEEP)
        canv.rect(0, 0, PAGE_W, 11 * mm, fill=1, stroke=0)
        canv.setFillColor(GOLD)
        canv.rect(0, 11 * mm, PAGE_W, 1.2, fill=1, stroke=0)
        canv.setFillColor(WHITE)
        canv.setFont("JehoAr", 7.5)
        canv.drawCentredString(
            PAGE_W / 2,
            4.2 * mm,
            ar(f"سياسة الشراكة · صفحة {doc.page} · {date.today().isoformat()}"),
        )
        if logo_path and logo_path.exists() and doc.page > 1:
            try:
                canv.drawImage(
                    str(logo_path),
                    12 * mm,
                    PAGE_H - 18 * mm,
                    width=12 * mm,
                    height=12 * mm,
                    mask="auto",
                    preserveAspectRatio=True,
                )
            except Exception:
                pass
        canv.restoreState()

    return _draw


def build():
    desktop = Path(os.environ.get("USERPROFILE", str(Path.home()))) / "Desktop"
    desktop.mkdir(parents=True, exist_ok=True)
    out = desktop / "JEHO-Partner-Policy-Green-2026.pdf"
    tmp = Path(os.environ.get("TEMP", ".")) / "jeho_pdf_bg"
    tmp.mkdir(exist_ok=True)
    bg = make_green_3d_bg(tmp / "green3d.png")
    logo = find_logo()

    st = styles()
    story = []
    usable = PAGE_W - 2 * MARGIN

    # COVER
    story.append(Spacer(1, 28 * mm))
    if logo and logo.exists():
        story.append(RLImage(str(logo), width=38 * mm, height=38 * mm, hAlign="CENTER"))
    story.append(Spacer(1, 6 * mm))
    story.append(p("سياسة الشراكة والربح", st["center"]))
    story.append(p("للمضيف · صاحب الوكالة · الداعم", st["sub_c"]))
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "دليل واضح ومختصر: كيف تربح من الهدايا، كيف تُحسب حصة الوكالة، وكيف يدعم الداعم. "
            "بدون تفاصيل تقنية، وبكلمات بسيطة.",
            st["sub_c"],
        )
    )
    story.append(Spacer(1, 10 * mm))
    kpis = Table(
        [[
            p("حصة المضيف\n47%", st["center"]),
            p("حصة الوكالة\n15%", st["center"]),
            p("ألماس الهدايا\n40%", st["center"]),
        ]],
        colWidths=[usable / 3] * 3,
    )
    kpis.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), GREEN_DEEP),
                ("BOX", (0, 0), (-1, -1), 1.5, GOLD),
                ("INNERGRID", (0, 0), (-1, -1), 0.5, GREEN_LIGHT),
                ("TOPPADDING", (0, 0), (-1, -1), 12),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 12),
            ]
        )
    )
    story.append(kpis)
    story.append(Spacer(1, 8 * mm))
    story.append(p("التسوية: شهرياً  ·  السحب: حسب الحد الأدنى المعتمد", st["sub_c"]))
    story.append(PageBreak())

    # ── HOST ──
    story.append(GreenHeader("١ · سياسة المضيف", "كيف تربح من الهدايا والتارجت"))
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "عندما يرسل الداعم هدية عادية في رومك (وأنت عضو وكالة نشط)، تُسكّ ألماس من قيمة الهدية "
            "ثم تحصل على حصتك منها. الهدايا المجانية من الحقيبة لا تنتج ألماساً.",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["البند", "التفصيل"],
            [
                ["رومات الوكالة", "حصتك 47% من الألماس المسكوك"],
                ["رومك الشخصي", "حصتك 62% من الألماس المسكوك"],
                ["سكّ الهدية العادية", "40% من سعر الهدية بالعملات → ألماس"],
                ["هدية الحظ", "ألماس أقل (15%) + مردود عملات للداعم أحياناً"],
                ["السحب", "عند بلوغ الحد الأدنى للألماس (شخصي أو وكالة حسب نوع الروم)"],
                ["التارجت الشهري", "مراحل إضافية — مكافآت تُسلَّم بعد المراجعة نهاية الشهر"],
            ],
            widths=[50 * mm, usable - 50 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["مثال (هدية 1,000 عملة · روم وكالة)", "ألماس"],
            [
                ["يُسكّ للمجموع", "400"],
                ["حصتك (مضيف)", "188"],
                ["صاحب الوكالة", "60"],
                ["المنصة", "152"],
            ],
            widths=[usable * 0.55, usable * 0.45],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "نصيحة: ابقَ نشطاً، رحّب بالداعمين، ولا تعتمد على هدايا الحظ وحدها — الهدايا العادية هي مصدر ألماسك الثابت.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ── AGENCY ──
    story.append(GreenHeader("٢ · سياسة صاحب الوكالة", "عمولتك من نشاط المضيفات"))
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "الوكالة تجمع المضيفات تحت إدارة واحدة. عندما يستقبل عضو من وكالتك هدية في روم وكالة، "
            "تحصل على 15% من الألماس المسكوك كعمولة مالك.",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["البند", "التفصيل"],
            [
                ["عمولتك الثابتة", "15% من ألماس هدايا أعضاءك (روم الوكالة)"],
                ["فتح وكالة", "يُدفع بالعملات حسب السعر المعتمد + موافقة الإدارة"],
                ["متى لا تُحتسب؟", "هدية لروم شخصي، أو هدية لنفسك، أو عضو غير نشط"],
                ["الألماس", "يُضاف لرصيد ألماس الوكالة — السحب عبر القنوات المعتمدة"],
                ["مسؤولية المالك", "تنشيط المضيفات، عدم الحسابات الوهمية، الالتزام بالسياسة"],
            ],
            widths=[48 * mm, usable - 48 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["نشاط شهري تقريبي (هدايا أعضاء)", "عمولتك ≈"],
            [
                ["400,000 ألماس مسكوك", "60,000 ألماس"],
                ["1,000,000 ألماس مسكوك", "150,000 ألماس"],
                ["5,000,000 ألماس مسكوك", "750,000 ألماس"],
            ],
            widths=[usable * 0.55, usable * 0.45],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "راتب التارجت للمضيفات/الوكلاء المرتبطين يُراجع ويُسلَّم شهرياً حسب السلم المعلن — ليس بدلاً عن عمولة الهدايا.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ── SUPPORTER ──
    story.append(GreenHeader("٣ · سياسة الداعم", "كيف تدعم وكيف تعمل العملات"))
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "الداعم يشتري العملات (باقات الشحن أو وكيل شحن محلي) ثم ينفقها على الهدايا، المول، VIP والألعاب. "
            "الهدايا تظهر للمضيف/المايك وتحوَّل جزئياً لألماس حسب الجداول أعلاه.",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["النوع", "ماذا يحدث؟"],
            [
                ["هدية عادية / كومبو / مميزة", "عملات تُخصم · ألماس للمستلم (40%)"],
                ["هدية حظ", "عملات تُخصم · مردود محتمل للداعم · ألماس أقل للمستلم"],
                ["مول / VIP / تجميل", "عملات تُستهلك بالكامل (لا ألماس للمستلم)"],
                ["الألعاب", "عملات رهان · فوز/خسارة حسب الحظ (المنصة تدير التوازن)"],
                ["الدعم الذاتي", "غير مسموح — لا هدية لنفسك"],
            ],
            widths=[55 * mm, usable - 55 * mm],
        )
    )
    story.append(Spacer(1, 4 * mm))
    story.append(GreenHeader("٤ · وكيل الشحن (للداعمين المحليين)", "شراء جملة وبيع حر"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "وكيل الشحن يدفع للمنصة ويحصل على رصيد عملات، ثم يبيع للداعمين بسعره. "
            "عند نفاد الرصيد يعيد الشحن. السعر النهائي بين الوكيل والداعم حر.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        tbl(
            ["البند", "الملخص"],
            [
                ["العضوية", "رسوم انضمام مرة واحدة (USDT)"],
                ["الجملة", "سعر رسمي لكل كمية عملات"],
                ["البيع", "حر — اقتراح سعر تجزئة فقط"],
                ["انتهاء الرصيد", "إيقاف البيع حتى تعبئة جديدة"],
            ],
            widths=[40 * mm, usable - 40 * mm],
        )
    )
    story.append(PageBreak())

    # ── RULES ──
    story.append(GreenHeader("٥ · قواعد ذهبية للجميع", "باختصار"))
    story.append(Spacer(1, 4 * mm))
    rules = [
        "الهدايا المدفوعة تُنتج ألماساً للمستلم وفق النسبة — الحقيبة المجانية لا.",
        "روم الوكالة: 47% مضيف · 15% مالك وكالة · الباقي للمنصة.",
        "روم شخصي: 62% مضيف · الباقي للمنصة.",
        "السحب شهري/عند الطلب بعد الحد الأدنى — بدون تلاعب أو حسابات مكررة.",
        "الألعاب للتسلية؛ لا توجد ضمانات ربح فردي.",
        "أي مخالفة (دعم ذاتي، وهمي، غش) قد تؤدي لإيقاف الرصيد/العضوية.",
    ]
    for i, r in enumerate(rules, 1):
        story.append(p(f"{i}. {r}", st["body"]))
    story.append(Spacer(1, 8 * mm))
    story.append(p("بالتوفيق — ربح عادل للجميع.", st["title_dark"]))
    story.append(Spacer(1, 4 * mm))
    if logo and logo.exists():
        story.append(RLImage(str(logo), width=22 * mm, height=22 * mm, hAlign="CENTER"))
    story.append(p("Partner Policy · Green Edition · 2026", st["muted"]))

    doc = SimpleDocTemplate(
        str(out),
        pagesize=A4,
        leftMargin=MARGIN,
        rightMargin=MARGIN,
        topMargin=16 * mm,
        bottomMargin=16 * mm,
        title="JEHO Partner Policy",
        author="JEHO",
    )
    doc.build(story, onFirstPage=on_page(bg, logo), onLaterPages=on_page(bg, logo))
    return out


if __name__ == "__main__":
    print(build())
