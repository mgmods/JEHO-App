#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JEHO CHAT — دليل سياسة الأعضاء للمستخدمين (عام).
موجّه للوكالة · المضيف · الداعم · وكيل الشحن · الجميع.
بدون أرقام أرباح المنصة الداخلية — يركّز على ما يستفيد المستخدم.
يُحفظ على سطح المكتب + docs/policies.
"""

from __future__ import annotations

import math
import os
import webbrowser
from datetime import date
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFilter
from reportlab.lib.colors import HexColor, white
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
    Flowable,
)

# Brand palette (JEHO: deep teal + rose accent — professional public doc)
DEEP = HexColor("#0B1A16")
MID = HexColor("#124438")
LIGHT = HexColor("#1F8A6A")
SOFT = HexColor("#EEF8F3")
ROSE = HexColor("#FE2C55")
GOLD = HexColor("#C9A227")
GOLD_SOFT = HexColor("#E8D48B")
TEXT = HexColor("#0D1F18")
MUTED = HexColor("#3D5C50")
WHITE = white
CARD = HexColor("#F7FCF9")
PLAY_GREEN = HexColor("#01875F")

PAGE_W, PAGE_H = A4
MARGIN = 13 * mm
PLAY_URL = "https://play.google.com/store/apps/details?id=com.Dramizo.Series"
APP_NAME = "JEHO CHAT"
PACKAGE = "com.Dramizo.Series"
YEAR = date.today().year

pdfmetrics.registerFont(TTFont("JehoAr", r"C:\Windows\Fonts\tahoma.ttf"))
pdfmetrics.registerFont(TTFont("JehoArBold", r"C:\Windows\Fonts\tahomabd.ttf"))

ROOT = Path(__file__).resolve().parents[1]
LOGO_CANDIDATES = [
    ROOT / "docs" / "brand" / "jeho_logo.png",
    ROOT / "docs" / "brand" / "logo.png",
    ROOT / "backend" / "public" / "logo.png",
    ROOT / "android" / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png",
]

# Official host salary ladder (host-salary-ladder.ts 20260807-min10usd-v2)
LADDER = [
    (1, 150_000, 10, 2),
    (2, 200_000, 15, 5),
    (3, 400_000, 22, 8),
    (4, 700_000, 30, 10),
    (5, 1_000_000, 38, 12),
    (6, 1_200_000, 50, 18),
    (7, 1_400_000, 65, 22),
    (8, 1_600_000, 75, 25),
    (9, 1_800_000, 90, 30),
    (10, 2_000_000, 105, 45),
    (11, 2_200_000, 120, 60),
    (12, 2_400_000, 135, 80),
    (13, 2_600_000, 150, 100),
    (14, 2_800_000, 170, 130),
    (15, 3_000_000, 200, 170),
    (16, 3_500_000, 240, 220),
    (17, 4_000_000, 300, 300),
    (18, 4_500_000, 370, 400),
    (19, 5_000_000, 450, 550),
    (20, 5_500_000, 550, 700),
    (21, 6_000_000, 700, 900),
    (22, 6_500_000, 850, 1150),
    (23, 7_000_000, 1000, 1450),
    (24, 8_000_000, 1200, 1800),
    (25, 9_000_000, 2200, 1400),
    (26, 10_000_000, 2600, 1600),
    (27, 12_000_000, 3000, 1800),
    (28, 15_000_000, 3400, 2000),
]


def ar(text: str) -> str:
    try:
        return get_display(arabic_reshaper.reshape(str(text)))
    except Exception:
        return str(text)


def p(text: str, style: ParagraphStyle) -> Paragraph:
    safe = (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )
    return Paragraph(ar(safe), style)


def make_bg(path: Path, w=1240, h=1754, seed=2):
    img = Image.new("RGB", (w, h), (11, 26, 22))
    px = img.load()
    for y in range(h):
        t = y / h
        r = int(8 + 12 * (1 - t) + 6 * math.sin(t * 3 + seed))
        g = int(40 + 48 * (1 - t))
        b = int(30 + 22 * (1 - t))
        for x in range(0, w, 2):
            px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))
            if x + 1 < w:
                px[x + 1, y] = px[x, y]
    for cx, cy, rad, col in [
        (int(w * 0.15), int(h * 0.1), int(w * 0.38), (254, 44, 85, 35)),
        (int(w * 0.85), int(h * 0.25), int(w * 0.32), (201, 162, 39, 40)),
        (int(w * 0.5), int(h * 0.75), int(w * 0.48), (31, 138, 106, 70)),
    ]:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=col)
        layer = layer.filter(ImageFilter.GaussianBlur(radius=max(12, rad // 7)))
        img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    fade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    for i in range(420):
        a = int(55 * (i / 420))
        fd.rectangle([0, h - 420 + i, w, h - 419 + i], fill=(238, 248, 243, a))
    img = Image.alpha_composite(img.convert("RGBA"), fade).convert("RGB")
    img.save(path, "PNG")
    return path


class SectionHead(Flowable):
    def __init__(self, title: str, subtitle: str = ""):
        super().__init__()
        self.title = title
        self.subtitle = subtitle
        self.h = 15 * mm

    def wrap(self, aw, ah):
        self.w = aw
        return aw, self.h

    def draw(self):
        c = self.canv
        c.setFillColor(DEEP)
        c.roundRect(0, 0, self.w, self.h, 5, fill=1, stroke=0)
        c.setFillColor(ROSE)
        c.rect(0, self.h - 2.3, self.w, 2.3, fill=1, stroke=0)
        c.setFillColor(WHITE)
        c.setFont("JehoArBold", 11.5)
        c.drawRightString(self.w - 7, self.h / 2 + (1 if self.subtitle else -2), ar(self.title))
        if self.subtitle:
            c.setFont("JehoAr", 7.8)
            c.setFillColor(GOLD_SOFT)
            c.drawRightString(self.w - 7, self.h / 2 - 8.5, ar(self.subtitle))


class PlayDownloadButton(Flowable):
    def __init__(self, width):
        super().__init__()
        self.w = width
        self.h = 15 * mm

    def wrap(self, aw, ah):
        return self.w, self.h

    def draw(self):
        c = self.canv
        c.setFillColor(PLAY_GREEN)
        c.roundRect(0, 0, self.w, self.h, 8, fill=1, stroke=0)
        c.setStrokeColor(GOLD)
        c.setLineWidth(1.1)
        c.roundRect(1.5, 1.5, self.w - 3, self.h - 3, 7, fill=0, stroke=1)
        c.setFillColor(WHITE)
        c.setFont("JehoArBold", 12)
        c.drawCentredString(self.w / 2, self.h / 2 + 1.5, ar("لتحميل التطبيق اضغط هنا"))
        c.setFont("JehoAr", 7)
        c.setFillColor(GOLD_SOFT)
        c.drawCentredString(self.w / 2, self.h / 2 - 9, ar("Google Play · JEHO CHAT"))
        c.linkURL(PLAY_URL, (0, 0, self.w, self.h), relative=1)


def styles():
    base = dict(fontName="JehoAr", fontSize=9.8, leading=14.5, textColor=TEXT, alignment=TA_RIGHT)
    return {
        "body": ParagraphStyle("body", **base),
        "body_sm": ParagraphStyle("body_sm", **{**base, "fontSize": 8.8, "leading": 12.8}),
        "muted": ParagraphStyle("muted", **{**base, "fontSize": 8.2, "textColor": MUTED}),
        "center": ParagraphStyle(
            "center", fontName="JehoArBold", fontSize=22, leading=28,
            textColor=WHITE, alignment=TA_CENTER,
        ),
        "sub_c": ParagraphStyle(
            "sub_c", fontName="JehoAr", fontSize=10.5, leading=14.5,
            textColor=GOLD_SOFT, alignment=TA_CENTER,
        ),
        "th": ParagraphStyle(
            "th", fontName="JehoArBold", fontSize=7.6, leading=10,
            textColor=WHITE, alignment=TA_CENTER,
        ),
        "td": ParagraphStyle(
            "td", fontName="JehoAr", fontSize=7.4, leading=10,
            textColor=TEXT, alignment=TA_CENTER,
        ),
        "td_r": ParagraphStyle(
            "td_r", fontName="JehoAr", fontSize=7.8, leading=11,
            textColor=TEXT, alignment=TA_RIGHT,
        ),
        "note": ParagraphStyle(
            "note", fontName="JehoAr", fontSize=8, leading=11.5,
            textColor=MUTED, alignment=TA_RIGHT,
        ),
        "bullet": ParagraphStyle(
            "bullet", fontName="JehoAr", fontSize=9.2, leading=13.5,
            textColor=TEXT, alignment=TA_RIGHT, rightIndent=2,
        ),
    }


def tbl(headers, rows, widths=None, right_cols=None):
    st = styles()
    right_cols = right_cols or set()
    data = [[p(h, st["th"]) for h in headers]]
    for row in rows:
        line = []
        for i, c in enumerate(row):
            style = st["td_r"] if i in right_cols else st["td"]
            line.append(p(str(c), style))
        data.append(line)
    t = Table(data, colWidths=widths, hAlign="CENTER")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), DEEP),
                ("BACKGROUND", (0, 1), (-1, -1), CARD),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [CARD, SOFT]),
                ("GRID", (0, 0), (-1, -1), 0.3, LIGHT),
                ("TOPPADDING", (0, 0), (-1, -1), 3.5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 3.5),
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
            canv.drawImage(
                str(bg_path), 0, 0, width=PAGE_W, height=PAGE_H,
                preserveAspectRatio=False, mask="auto",
            )
        canv.setStrokeColor(GOLD)
        canv.setLineWidth(1)
        canv.rect(7 * mm, 9 * mm, PAGE_W - 14 * mm, PAGE_H - 18 * mm)
        canv.setFillColor(DEEP)
        canv.rect(0, 0, PAGE_W, 10 * mm, fill=1, stroke=0)
        canv.setFillColor(ROSE)
        canv.rect(0, 10 * mm, PAGE_W, 1.1, fill=1, stroke=0)
        canv.setFillColor(WHITE)
        canv.setFont("JehoAr", 7)
        canv.drawCentredString(
            PAGE_W / 2,
            3.6 * mm,
            ar(f"{APP_NAME} · دليل سياسة الأعضاء · صفحة {doc.page} · {date.today().isoformat()}"),
        )
        if logo_path and logo_path.exists() and doc.page > 1:
            try:
                canv.drawImage(
                    str(logo_path), 11 * mm, PAGE_H - 17 * mm,
                    width=10 * mm, height=10 * mm, mask="auto", preserveAspectRatio=True,
                )
            except Exception:
                pass
        canv.restoreState()

    return _draw


def bullets(story, items, st):
    for it in items:
        story.append(p(f"•  {it}", st["bullet"]))
        story.append(Spacer(1, 1.2 * mm))


def build() -> Path:
    desktop = Path(os.environ.get("USERPROFILE", str(Path.home()))) / "Desktop"
    desktop.mkdir(parents=True, exist_ok=True)
    docs_out = ROOT / "docs" / "policies"
    docs_out.mkdir(parents=True, exist_ok=True)

    out_desktop = desktop / f"JEHO-CHAT-Member-Policy-{YEAR}.pdf"
    out_desktop_ar = desktop / f"JEHO-CHAT-Member-Guide-{YEAR}-AR.pdf"
    out_docs = docs_out / f"JEHO-CHAT-Member-Policy-{YEAR}.pdf"

    tmp = Path(os.environ.get("TEMP", ".")) / "jeho_member_policy_pdf_v2"
    tmp.mkdir(exist_ok=True)
    bg = make_bg(tmp / "bg.png")
    logo = find_logo()
    st = styles()
    story = []
    usable = PAGE_W - 2 * MARGIN

    # ════════════════════════════════════════ COVER
    story.append(Spacer(1, 18 * mm))
    if logo and logo.exists():
        story.append(RLImage(str(logo), width=34 * mm, height=34 * mm, hAlign="CENTER"))
    story.append(Spacer(1, 4 * mm))
    story.append(p(APP_NAME, st["center"]))
    story.append(p("دليل سياسة التطبيق للأعضاء", st["sub_c"]))
    story.append(p("الوكالة · المضيف · الداعم · وكيل الشحن · التميّز · القواعد", st["sub_c"]))
    story.append(Spacer(1, 5 * mm))
    story.append(
        p(
            "دليل رسمي مبسّط يوضح ماذا تستفيد عند فتح وكالة أو الانضمام مضيفاً "
            "أو الشحن أو السحب — بلغة للمستخدمين والشركاء. الأرقام قابلة للتحديث "
            "من إدارة التطبيق وفق تاريخ التفعيل على الخادم.",
            st["sub_c"],
        )
    )
    story.append(Spacer(1, 7 * mm))
    kpis = Table(
        [[
            p("28 مرحلة\nتارجت", st["center"]),
            p("سحب\nبنك · USDT", st["center"]),
            p("وكالة\n+ مضيفات", st["center"]),
            p("VIP\n& مول", st["center"]),
        ]],
        colWidths=[usable / 4] * 4,
    )
    kpis.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), DEEP),
                ("BOX", (0, 0), (-1, -1), 1.2, GOLD),
                ("INNERGRID", (0, 0), (-1, -1), 0.4, LIGHT),
                ("TOPPADDING", (0, 0), (-1, -1), 9),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
            ]
        )
    )
    story.append(kpis)
    story.append(Spacer(1, 7 * mm))
    story.append(PlayDownloadButton(usable * 0.7))
    story.append(Spacer(1, 3 * mm))
    story.append(p(f"Google Play: {PLAY_URL}", st["note"]))
    story.append(p(f"معرّف التطبيق: {PACKAGE}", st["note"]))
    story.append(p(f"الإصدار: {date.today().isoformat()} · للجمهور والأعضاء", st["note"]))
    story.append(PageBreak())

    # ════════════════════════════════════════ 1 GENERAL
    story.append(SectionHead("١ · ما هو JEHO CHAT؟", "تطبيق تواصل صوتي واجتماعي"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "JEHO CHAT تطبيق غرف صوتية ودردشة وهدايا ودراما. تقدر تقابل ناس، تفتح روم، "
            "تدعم بنجوم وهدايا، تصير مضيفاً أو تملك وكالة، وتشحن عبر القنوات الرسمية.",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        tbl(
            ["البند", "التفصيل"],
            [
                ["اسم التطبيق", "JEHO CHAT"],
                ["الفئة العمرية", "18 سنة فما فوق"],
                ["العملة الرئيسية", "عملات Coins — للشراء والإنفاق (هدايا، VIP، مول، وكالة…)"],
                ["عملة الأرباح", "ألماس Diamonds — من الهدايا/المهام/التارجت — قابلة للسحب"],
                ["الروم الشخصي", "لكل مستخدم — أرباح ألماس منفصلة عن روم الوكالة"],
                ["روم الوكالة", "روم دائم للوكالة — هدايا تُحسب للأعضاء والوكالة"],
                ["الحسابات", "حساب واحد حقيقي لكل شخص — ممنوع التلاعب والوهمية"],
                ["التحميل", "من Google Play بالرابط الرسمي فقط"],
            ],
            widths=[38 * mm, usable - 38 * mm],
            right_cols={0, 1},
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 2 AGENCY
    story.append(SectionHead("٢ · فتح وكالة — ماذا تستفيد؟", "مالك الوكالة والمزايا"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "الوكالة منظومة رسمية لتبنّي مضيفات وإدارة روم حي. تُفتح بطلب داخل التطبيق "
            "(مراجعة الإدارة) وليس بضغطة عشوائية.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["المرحلة", "ماذا يحدث"],
            [
                ["1. الطلب", "تعبئة نموذج: الاسم، الخطة، التواصل، الخبرة، عدد المضيفات المتوقع + قبول الشروط"],
                ["2. الرسوم", "حوالي 50,000 عملة (أو مجاني إذا فعّلت الإدارة ذلك)"],
                ["3. الاعتماد", "مراجعة الإدارة → وكالة نشطة + رقم عام GID"],
                ["4. التشغيل", "كود تفعيل/دعوة · روم وكالة دائم · أعضاء · متابعة عائلة"],
            ],
            widths=[32 * mm, usable - 32 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(p("ماذا يحصل مالك الوكالة؟", st["body_sm"]))
    story.append(Spacer(1, 1.5 * mm))
    bullets(
        story,
        [
            "روم وكالة دائم وعلني (هوية الوكالة — ليس اسم المضيف الشخصي).",
            "لوحة أعضاء ودعوات وإدارة مضيفات (مالك / مدير / مضيف).",
            "حصة من هدايا روم الوكالة تُجمَع كعمولة وكالة قابلة للسحب بعد قفل مراحل التارجت.",
            "بطاقة «عائلة» للوكالة: متابعة، إحصائيات، شارة موثّقة عند الاعتماد.",
            "إمكانية منح إطارات/هوية بصرية حصرية عند تفعيلها من الإدارة.",
        ],
        st,
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        p(
            "مهم: كلمة «وكالة» محجوزة للهوية الرسمية. لا تستخدمها في اسم/روم شخصي. "
            "ممنوع الوكالات الوهمية أو تعدد الحسابات لأغراض السحب.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 3 HOST
    story.append(SectionHead("٣ · المضيف — أرباح وتجربة", "روم شخصي vs روم وكالة"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["النوع", "ماذا تستفيد"],
            [
                ["مضيف في وكالة", "روم دائم، هدايا تُضاف للتارجت والأرباح، عمولة دعوة ضيوف جدد، مهام مضيفة"],
                ["روم شخصي", "غرفة خاصة بك — أرباح ألماس شخصي منفصلة، عنوان بلا كلمة وكالة"],
                ["مدير وكالة", "بث وإدارة الغرفة والأعضاء (حسب الصلاحيات)"],
            ],
            widths=[40 * mm, usable - 40 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(p("كيف تتكوّن أرباحك من الهدايا؟", st["body_sm"]))
    story.append(Spacer(1, 1.2 * mm))
    story.append(
        p(
            "عند وصول هدية مدفوعة بالعملات، يُسكّ ألماس للطرف المستفيد. "
            "في روم الوكالة (عضو نشط): المضيف يحصل حصّة واضحة من الماس المسكوك "
            "(حوالي 45٪)، والوكالة حصّة صاحب الوكالة (حوالي 15٪). "
            "في الروم الشخصي تكون حصة المضيف أعلى. الأرصدة لا تُخلط — روم شخصي منفصل عن روم وكالة.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        p(
            "مثال مبسّط: هدية بـ 1,000 عملة في روم وكالة تُولّد ماساً للسك، "
            "ثم يذهب جزء للمضيف وجزء لرصيد الوكالة — حسب السياسة الحية.",
            st["muted"],
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["إضافة", "التفصيل"],
            [
                ["دعوة ضيف جديد", "مضيفة وكالة: ضيف يمكث ~دقيقتين → مكافأة ألماس (حد يومي)"],
                ["مهام المضيف", "مهام داخلية تمنح تقدّماً ومكافآت عند الإكمال"],
                ["التحقّق من الجنس", "عند تفعيل سياسة الاستضافة قد يُطلب فحص وجه حي للسماح بالاستضافة"],
            ],
            widths=[38 * mm, usable - 38 * mm],
            right_cols={0, 1},
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 4 TARGET
    story.append(SectionHead("٤ · التارجت — مراحل الراتب", "28 مرحلة · حد أدنى مضيف $10"))
    story.append(Spacer(1, 2 * mm))
    story.append(
        p(
            "نظام التارجت يقيس نشاط الهدايا خلال الفترة (أسبوعي أو شهري حسب إعداد الإدارة). "
            "عندما تصل لمرحلة وتُقفل (تتحقق)، تصبح جاهزة للسحب كراتب مضيف / راتب وكيل. "
            "لا يوجد سحب عشوائي بباقات ألماس — المراحل المقفلة أو كامل الرصيد عند السماح فقط.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        tbl(
            ["قاعدة", "المعنى"],
            [
                ["حد أدنى راتب مضيف", "$10 — لا مراحل أقل"],
                ["وحدة الهدف", "1 وحدة هدف = 1 ألماسة في العرض"],
                ["راتب الوكيل", "يظهر للوكالة عندما المضيفون يقفّلون المرحلة"],
                ["سحب المضيف", "طلب مباشرة لإدارة المنصة (أرباح روم الوكالة)"],
                ["سحب المالك", "عمولة الوكالة بعد قفل التارجت من المضيفين"],
            ],
            widths=[40 * mm, usable - 40 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(p("جدول مراحل التارجت الرسمي (راتب مضيف + راتب وكيل بالدولار):", st["body_sm"]))
    story.append(Spacer(1, 1.5 * mm))

    # Split ladder into two tables for readability
    def ladder_table(rows):
        data_rows = [
            [
                str(s),
                f"{t:,}",
                f"${h}",
                f"${a}",
                f"${h + a}",
            ]
            for s, t, h, a in rows
        ]
        return tbl(
            ["مرحلة", "الهدف (ماسة)", "مضيف $", "وكيل $", "الإجمالي $"],
            data_rows,
            widths=[usable * 0.14, usable * 0.28, usable * 0.18, usable * 0.18, usable * 0.22],
        )

    story.append(ladder_table(LADDER[:14]))
    story.append(PageBreak())
    story.append(SectionHead("٤ب · بقية مراحل التارجت", "مراحل 15–28"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(ladder_table(LADDER[14:]))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "بعد إكمال كل المراحل في الفترة، يمكن إعادة الدورة مع احتفاظ الفائض حسب الإعداد. "
            "القيم قابلة للتعديل من لوحة الإدارة وتصبح سارية من تاريخ السيرفر.",
            st["note"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 5 WITHDRAW
    story.append(SectionHead("٥ · السحب للمستخدمين", "طرق الاستلام وشروط السماح"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["البند", "التفصيل"],
            [
                ["رصيد شخصي", "ألماس الروم الشخصي / الهدايا الشخصية"],
                ["رصيد وكالة", "أرباح روم الوكالة (مضيف) أو عمولة الوكالة (مالك) — منفصل تماماً"],
                ["شرط التارجت", "سحب عمولة/أرباح الوكالة بعد قفل مرحلة واحدة على الأقل"],
                ["طرق الاستلام", "تحويل بنكي USD · USDT (TRC20/ERC20) · PayPal · شام كاش · أخرى"],
                ["حد أدنى تقريبي", "حوالي 10,000 ألماسة (أو حسب إعداد الإدارة)"],
                ["قيمة الألماسة", "حوالي $0.00005 لكل ماسة (قابلة للتحديث)"],
                ["المراجعة", "الطلب يمر لإدارة المنصة → مدفوع أو مرفوض مع توضيح"],
            ],
            widths=[36 * mm, usable - 36 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "تنبيه: أرباح روم الوكالة لا تُصرف عبر مول وكلاء الشحن. "
            "لا تشارك بيانات حسابك البنكي أو محفظتك إلا في نموذج السحب الرسمي داخل التطبيق.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 6 RECHARGE AGENT
    story.append(SectionHead("٦ · وكيل الشحن", "للمشترين ولمن يريد البيع"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "وكيل الشحن ليس وكالة استضافة. هو بائع عملات معتمد داخل التطبيق — "
            "المنصة توفر دليلاً لوكلاء موثوقين، والعميل يشحن بأمان.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        tbl(
            ["الجانب", "ماذا تستفيد"],
            [
                ["المشتري", "شحن عملات سريع عبر وكيل محلي/موثوق من الدليل الرسمي"],
                ["الوكيل", "مخزون بيع، ظهور في الدليل، عمولات/بونص مستويات حسب الإدارة"],
                ["العضوية", "طلب داخل التطبيق + رسوم عضوية (مثال ~$25 USDT) + كمية ابتدائية"],
                ["القواعد", "أسعار عادلة، عدم الاحتيال، عدم انتحال الإدارة"],
            ],
            widths=[32 * mm, usable - 32 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        p(
            "اشترِ فقط من وكلاء ظاهرين في التطبيق. لا تحوّل أموالاً لجهات مجهولة خارج الدليل.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 7 SUPPORTER / VIP
    story.append(SectionHead("٧ · الداعم · VIP · التميّز", "ما يظهر لعشاق التطبيق"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["الميزة", "لمن؟", "الفائدة"],
            [
                ["داعم (باقات)", "من يشحن بمبالغ أعلى", "إطار تميّز لمدة معينة (مثال 7/15/30 يوم)"],
                ["VIP", "بالعملات · إيجار 7/30/40 يوم", "إطار، اسم، أولوية دخول، امتيازات حسب المستوى"],
                ["آي دي مميز", "شراء إيجار ~30 يوم", "رقم أو اسم عام مميز في البروفايل"],
                ["المول", "الجميع (بعضها VIP)", "إطارات، دخول، خلفيات روم، بطاقات…"],
                ["ثروة / سحر", "تلقائي بالهدايا", "مستويات ترتيب وتألق في الروم والبروفايل"],
            ],
            widths=[28 * mm, 40 * mm, usable - 68 * mm],
            right_cols={0, 1, 2},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "VIP والإطارات والآي دي المميز غالباً إيجار لمدة محددة — ليست ملكية دائمة إلا ما تعلنه الإدارة.",
            st["note"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 8 ROOMS GAMES SOCIAL
    story.append(SectionHead("٨ · الغرف · الألعاب · الأنشطة", "تجربة الجميع داخل التطبيق"))
    story.append(Spacer(1, 2.5 * mm))
    bullets(
        story,
        [
            "غرف صوت: مايكات، هدايا، متابعة روم، كتم/طرد حسب الصلاحيات.",
            "أنشطة وبلازا: فعاليات وحفلات (منها افتتاح وكالة) — انضم من تبويب الأنشطة.",
            "متابعة: مستخدمين · رومات · عائلة الوكالة.",
            "دردشات خاصة ورسائل رسمية من JEHO داخل التبويب.",
            "ألعاب داخلية/ويب (مثل السلوتس) حسب التفعيل في التطبيق.",
            "دراما وترفيه ومحتوى إضافي عند توفره.",
        ],
        st,
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 9 RULES
    story.append(SectionHead("٩ · سياسة الاستخدام والمجتمع", "واجبة على الجميع"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["ممنوع", "لماذا"],
            [
                ["قاصرين / محتوى 18+", "حماية المستخدمين والالتزام القانوني"],
                ["احتيال · حسابات وهمية · غسل أرصدة", "يحمي الاقتصاد والمستخدمين"],
                ["إهداء ذاتي أو تلاعب بالتارجت", "يُوقف الأرباح أو الحساب"],
                ["روابط خارجية / ترويج تطبيقات / إباحية في الشات", "فلتر تلقائي + عقوبات"],
                ["انتحال وكالة أو وكيل شحن", "الجهات الرسمية فقط داخل التطبيق"],
                ["مضايقة / تنمّر / سب", "كتم، طرد، حظر روم أو حساب"],
            ],
            widths=[55 * mm, usable - 55 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        p(
            "عقوبات الروم تدريجية (مثال): كتم → طرد → حظر روم. "
            "المخالفات الجسيمة تصل لحظر الحساب. للإدارة حق تعليق أي وكالة أو وكيل أو مضيف عند المخالفة.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ════════════════════════════════════════ 10 PRIVACY
    story.append(SectionHead("١٠ · الخصوصية والحساب", "حقوقك كمستخدم"))
    story.append(Spacer(1, 2.5 * mm))
    bullets(
        story,
        [
            "لا تبيع المنصة بياناتك الشخصية لأطراف خارجية لأغراض تسويق عشوائي.",
            "بيانات الحساب والبث والرسائل تُعالَج لتشغيل الخدمة والأمان.",
            "يمكنك طلب حذف الحساب من الإعدادات أو القنوات الرسمية (معالجة خلال مدة معلنة).",
            "لا تشارك كلمات المرور أو رموز Google أو بيانات دفع في الدردشة.",
            "سياسة الخصوصية وشروط الاستخدام متاحة من داخل التطبيق / الموقع الرسمي.",
        ],
        st,
    )
    story.append(Spacer(1, 3 * mm))
    story.append(SectionHead("١١ · أدوار باختصار", "من يستفيد بماذا؟"))
    story.append(Spacer(1, 2.5 * mm))
    story.append(
        tbl(
            ["الدور", "يستفيد"],
            [
                ["زائر / داعم", "غرف، هدايا، ألعاب، VIP، تميّز، صداقات"],
                ["مضيف شخصي", "روم خاص + ألماس شخصي + سحب"],
                ["مضيف وكالة", "روم دائم + تارجت + أرباح وكالة + مهام"],
                ["مالك / مدير وكالة", "فريق + عمولة + بطاقة عائلة + إدارة"],
                ["وكيل شحن", "بيع عملات + دليل + حوافز"],
            ],
            widths=[40 * mm, usable - 40 * mm],
            right_cols={0, 1},
        )
    )
    story.append(Spacer(1, 6 * mm))
    story.append(PlayDownloadButton(usable * 0.7))
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "هذا الدليل موجّه للأعضاء والجمهور. الأرقام الاقتصادية والتارجت مبنية على السياسة "
            "الرسمية الحالية للتطبيق وقد تُحدَّث. لا يُستبدل هذا المستند باستشارة قانونية كاملة "
            "عند الحاجة — الشروط القانونية التفصيلية ضمن التطبيق.",
            st["note"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(p(f"© {YEAR} {APP_NAME} · للتوزيع على الأعضاء والشركاء", st["note"]))
    story.append(p("صوت · تواصل · تألّق", st["sub_c"]))

    def write_pdf(path: Path):
        doc = SimpleDocTemplate(
            str(path),
            pagesize=A4,
            leftMargin=MARGIN,
            rightMargin=MARGIN,
            topMargin=15 * mm,
            bottomMargin=15 * mm,
            title=f"{APP_NAME} Member Policy {YEAR}",
            author=APP_NAME,
            subject="دليل سياسة الأعضاء — وكالة مضيف داعم وكيل شحن",
        )
        doc.build(story, onFirstPage=on_page(bg, logo), onLaterPages=on_page(bg, logo))

    write_pdf(out_desktop)
    try:
        write_pdf(out_desktop_ar)
    except Exception as e:
        print("AR copy skip:", e)
    try:
        write_pdf(out_docs)
    except Exception as e:
        print("docs copy skip:", e)

    print("Wrote:", str(out_desktop))
    if out_desktop_ar.exists() and out_desktop_ar.stat().st_size > 10000:
        print("Wrote AR:", str(out_desktop_ar))
    if out_docs.exists() and out_docs.stat().st_size > 10000:
        print("Also:", str(out_docs))
    return out_desktop


if __name__ == "__main__":
    path = build()
    try:
        os.startfile(str(path))  # noqa: PTH
    except Exception:
        try:
            webbrowser.open(path.as_uri())
        except Exception:
            pass
