#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate JEHO CHAT official economy policy PDF (Arabic RTL, professional design)."""

from __future__ import annotations

import os
from datetime import date
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.colors import Color, HexColor, white, black
from reportlab.lib.enums import TA_CENTER, TA_RIGHT, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm, cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    PageBreak,
    KeepTogether,
    Flowable,
    HRFlowable,
)

# ── palette ──────────────────────────────────────────────────────────────
NAVY = HexColor("#0B1F3A")
GOLD = HexColor("#C9A227")
GOLD_SOFT = HexColor("#E8D48B")
TEAL = HexColor("#0E7C7B")
CREAM = HexColor("#F7F3EA")
CARD = HexColor("#FFFFFF")
SOFT = HexColor("#E8EEF5")
TEXT = HexColor("#1A2433")
MUTED = HexColor("#5A6A7A")
GREEN = HexColor("#1B7F4E")
RED_SOFT = HexColor("#B23A48")
ORANGE = HexColor("#C96A17")

PAGE_W, PAGE_H = A4
MARGIN = 16 * mm

FONT_REG = "C:\\Windows\\Fonts\\tahoma.ttf"
FONT_BOLD = "C:\\Windows\\Fonts\\tahomabd.ttf"
pdfmetrics.registerFont(TTFont("JehoAr", FONT_REG))
pdfmetrics.registerFont(TTFont("JehoArBold", FONT_BOLD))


def ar(text: str) -> str:
    """Shape Arabic + BiDi for ReportLab (LTR engine)."""
    if not text:
        return ""
    try:
        reshaped = arabic_reshaper.reshape(str(text))
        return get_display(reshaped)
    except Exception:
        return str(text)


def p(text: str, style: ParagraphStyle) -> Paragraph:
    # escape minimal XML
    safe = (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )
    return Paragraph(ar(safe), style)


class Banner(Flowable):
    def __init__(self, title: str, subtitle: str = "", height=28 * mm):
        super().__init__()
        self.title = title
        self.subtitle = subtitle
        self.h = height
        self.w = PAGE_W - 2 * MARGIN

    def wrap(self, availWidth, availHeight):
        self.w = availWidth
        return self.w, self.h

    def draw(self):
        c = self.canv
        c.setFillColor(NAVY)
        c.roundRect(0, 0, self.w, self.h, 8, fill=1, stroke=0)
        c.setFillColor(GOLD)
        c.rect(0, self.h - 4, self.w, 4, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("JehoArBold", 16)
        t = ar(self.title)
        c.drawRightString(self.w - 12, self.h / 2 + (4 if self.subtitle else -4), t)
        if self.subtitle:
            c.setFont("JehoAr", 9)
            c.setFillColor(GOLD_SOFT)
            c.drawRightString(self.w - 12, self.h / 2 - 12, ar(self.subtitle))


class IconPill(Flowable):
    def __init__(self, icon: str, label: str, color: Color, w=52 * mm, h=14 * mm):
        super().__init__()
        self.icon = icon
        self.label = label
        self.color = color
        self._w = w
        self._h = h

    def wrap(self, aw, ah):
        return self._w, self._h

    def draw(self):
        c = self.canv
        c.setFillColor(self.color)
        c.roundRect(0, 0, self._w, self._h, 7, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("JehoArBold", 10)
        c.drawCentredString(self._w / 2, self._h / 2 - 3, ar(f"{self.icon}  {self.label}"))


class SectionTitle(Flowable):
    def __init__(self, number: str, title: str):
        super().__init__()
        self.number = number
        self.title = title
        self.h = 12 * mm

    def wrap(self, aw, ah):
        self.w = aw
        return aw, self.h

    def draw(self):
        c = self.canv
        c.setFillColor(TEAL)
        c.circle(self.w - 7, self.h / 2, 7, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("JehoArBold", 9)
        c.drawCentredString(self.w - 7, self.h / 2 - 3, ar(self.number))
        c.setFillColor(NAVY)
        c.setFont("JehoArBold", 13)
        c.drawRightString(self.w - 18, self.h / 2 - 4, ar(self.title))
        c.setStrokeColor(GOLD)
        c.setLineWidth(1.2)
        c.line(0, 0, self.w - 18, 0)


def styles():
    s = getSampleStyleSheet()
    base = dict(fontName="JehoAr", fontSize=10, leading=15, textColor=TEXT, alignment=TA_RIGHT)
    return {
        "body": ParagraphStyle("body", **base),
        "body_sm": ParagraphStyle("body_sm", **{**base, "fontSize": 9, "leading": 13}),
        "muted": ParagraphStyle("muted", **{**base, "fontSize": 9, "textColor": MUTED}),
        "h_center": ParagraphStyle(
            "h_center",
            fontName="JehoArBold",
            fontSize=22,
            leading=28,
            textColor=white,
            alignment=TA_CENTER,
        ),
        "sub_center": ParagraphStyle(
            "sub_center",
            fontName="JehoAr",
            fontSize=11,
            leading=16,
            textColor=GOLD_SOFT,
            alignment=TA_CENTER,
        ),
        "table_cell": ParagraphStyle(
            "table_cell",
            fontName="JehoAr",
            fontSize=8.5,
            leading=12,
            textColor=TEXT,
            alignment=TA_CENTER,
        ),
        "table_head": ParagraphStyle(
            "table_head",
            fontName="JehoArBold",
            fontSize=8.5,
            leading=12,
            textColor=white,
            alignment=TA_CENTER,
        ),
        "kpi": ParagraphStyle(
            "kpi",
            fontName="JehoArBold",
            fontSize=12,
            leading=16,
            textColor=NAVY,
            alignment=TA_CENTER,
        ),
        "footer": ParagraphStyle(
            "footer",
            fontName="JehoAr",
            fontSize=8,
            leading=10,
            textColor=MUTED,
            alignment=TA_CENTER,
        ),
        "warn": ParagraphStyle(
            "warn",
            fontName="JehoAr",
            fontSize=9.5,
            leading=14,
            textColor=RED_SOFT,
            alignment=TA_RIGHT,
        ),
        "ok": ParagraphStyle(
            "ok",
            fontName="JehoAr",
            fontSize=9.5,
            leading=14,
            textColor=GREEN,
            alignment=TA_RIGHT,
        ),
    }


def table_ar(headers, rows, col_widths=None):
    st = styles()
    data = [[p(h, st["table_head"]) for h in headers]]
    for row in rows:
        data.append([p(str(c), st["table_cell"]) for c in row])
    t = Table(data, colWidths=col_widths, hAlign="CENTER")
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), white),
                ("BACKGROUND", (0, 1), (-1, -1), CREAM),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [CREAM, SOFT]),
                ("GRID", (0, 0), (-1, -1), 0.4, HexColor("#C5D0DC")),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                ("LEFTPADDING", (0, 0), (-1, -1), 4),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    return t


def footer_header(canvas, doc):
    canvas.saveState()
    # top thin gold
    canvas.setFillColor(GOLD)
    canvas.rect(0, PAGE_H - 3, PAGE_W, 3, fill=1, stroke=0)
    # bottom bar
    canvas.setFillColor(NAVY)
    canvas.rect(0, 0, PAGE_W, 12 * mm, fill=1, stroke=0)
    canvas.setFillColor(GOLD)
    canvas.rect(0, 12 * mm, PAGE_W, 1.2, fill=1, stroke=0)
    canvas.setFillColor(white)
    canvas.setFont("JehoAr", 8)
    canvas.drawCentredString(
        PAGE_W / 2,
        5 * mm,
        ar(f"JEHO CHAT · سياسة الاقتصاد الرسمية · {date.today().isoformat()} · صفحة {doc.page}"),
    )
    canvas.restoreState()


def cover_page(story, st):
    # full-width navy block via table
    logo_block = Table(
        [
            [p("◆  JEHO CHAT  ◆", st["h_center"])],
            [p("سياسة اقتصاد المنصة · النسخة الرسمية", st["sub_center"])],
            [p("Economy Policy · v3 · August 2026", st["sub_center"])],
        ],
        colWidths=[PAGE_W - 2 * MARGIN],
    )
    logo_block.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), NAVY),
                ("TOPPADDING", (0, 0), (-1, 0), 28),
                ("BOTTOMPADDING", (0, -1), (-1, -1), 22),
                ("TOPPADDING", (0, 1), (-1, -1), 6),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
            ]
        )
    )
    story.append(logo_block)
    story.append(Spacer(1, 8 * mm))
    story.append(
        p(
            "موجّه إلى: صاحب المنصة · المضيفات · أصحاب الوكالات · وكلاء الشحن · الداعمين",
            st["body"],
        )
    )
    story.append(Spacer(1, 4 * mm))
    story.append(
        p(
            "هذه الوثيقة هي السياسة الاقتصادية الرسمية لتطبيق JEHO CHAT. "
            "تضمن ربحاً مستداماً لصاحب المنصة، وعدلاً للمضيف والوكالة، ومرونة لوكيل الشحن، "
            "وقيمة ممتعة للداعم دون أن يحوّل التطبيق إلى «خاسر مالي».",
            st["body"],
        )
    )
    story.append(Spacer(1, 6 * mm))

    kpis = Table(
        [
            [
                p("حصة المنصة\n35%", st["kpi"]),
                p("حصة المضيف (وكالة)\n50%", st["kpi"]),
                p("حصة صاحب الوكالة\n15%", st["kpi"]),
            ],
            [
                p("سك الهدايا\n42% ألماس", st["kpi"]),
                p("السحب\n$0.00005 / ألماسة", st["kpi"]),
                p("التسوية\nشهرية", st["kpi"]),
            ],
        ],
        colWidths=[(PAGE_W - 2 * MARGIN) / 3] * 3,
    )
    kpis.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), CREAM),
                ("BOX", (0, 0), (-1, -1), 1.5, GOLD),
                ("INNERGRID", (0, 0), (-1, -1), 0.6, SOFT),
                ("TOPPADDING", (0, 0), (-1, -1), 10),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    story.append(kpis)
    story.append(Spacer(1, 8 * mm))
    story.append(
        p(
            "✔ المنصة رابحة حتى في أسوأ حالة (كل العملات تُنفق هدايا عادية).",
            st["ok"],
        )
    )
    story.append(
        p(
            "✔ وكيل الشحن يشتري رصيداً ويبيع بسعره الحر — عند نفاد الرصيد يطلب شحن جديد.",
            st["ok"],
        )
    )
    story.append(
        p(
            "✔ رواتب التارجت الشهري سياسة تسوية يدوية بالدولار للقمم (لا تُدفع تلقائياً كنقد).",
            st["muted"],
        )
    )
    story.append(Spacer(1, 10 * mm))
    story.append(HRFlowable(width="100%", thickness=1, color=GOLD, spaceBefore=2, spaceAfter=6))
    story.append(
        p(
            f"تاريخ الإصدار: {date.today().strftime('%Y-%m-%d')}  ·  Pricing catalog: 20260806economy-v3",
            st["muted"],
        )
    )


def build():
    desktop = Path(os.path.expanduser("~")) / "Desktop"
    if not desktop.exists():
        desktop = Path(os.environ.get("USERPROFILE", str(Path.home()))) / "Desktop"
    out = desktop / "JEHO-CHAT-Economy-Policy-2026.pdf"

    st = styles()
    doc = SimpleDocTemplate(
        str(out),
        pagesize=A4,
        leftMargin=MARGIN,
        rightMargin=MARGIN,
        topMargin=14 * mm,
        bottomMargin=18 * mm,
        title="JEHO CHAT Economy Policy",
        author="JEHO CHAT",
    )
    story = []

    # ── COVER ──
    cover_page(story, st)
    story.append(PageBreak())

    # ── 1 MONEY FLOW ──
    story.append(SectionTitle("١", "تدفق الأموال — من الدولار إلى العملة والربح"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "المسار الأساسي: المستخدم يدفع دولار (Play / USDT / وكيل شحن) → يحصل على عملات (Coins) "
            "→ ينفق على هدايا / مول / VIP / ألعاب. الهدايا تسك ألماساً (Diamonds) للمستلم وفق النسبة "
            "ثم يُقسَّم بين المنصة والمضيف والوكالة. السحب النقدي من الألماس فقط وفق السعر الرسمي.",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        table_ar(
            ["المنفذ", "ماذا يدخل المنصة؟", "ماذا يخرج؟", "هامش المالك"],
            [
                ["Google Play", "سعر الباقة", "عمولة 15–30% + عملات", "مرتفع بعد المصارف"],
                ["USDT / مباشر", "كامل المبلغ", "عملات فقط", "الأعلى"],
                ["وكيل شحن", "سعر الجملة + عضوية", "رصيد كوينز للوكيل", "ممتاز (~%80+ صافي)"],
                ["مول / VIP / ألعاب", "عملات مستهلكة", "لا ألماس (جزء كبير)", "ربح صافٍ 100%"],
                ["هدايا عادية", "عملات", "ألماس قابل للسحب (جزء)", "حصة المنصة 35%"],
            ],
            col_widths=[32 * mm, 40 * mm, 48 * mm, 40 * mm],
        )
    )
    story.append(PageBreak())

    # ── 2 PACKAGES ──
    story.append(SectionTitle("٢", "باقات الشحن (التارجت الاستهلاكي)"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "الباقات مصمّمة لإغراء الدخول (باقة $0.99) مع سقف آمن لكثافة العملات في الباقات الكبيرة "
            "حتى لا يصبح السحب خسارة. الكثافة القصوى ≈ 22,500 عملة لكل $1.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        table_ar(
            ["الباقة", "السعر $", "عملات", "بونص", "الإجمالي", "عملة لكل $"],
            [
                ["دخول", "0.99", "12,000", "0", "12,000", "≈12,121"],
                ["صغيرة", "2.99", "38,000", "2,000", "40,000", "≈13,378"],
                ["شائعة ★", "4.99", "80,000", "8,000", "88,000", "≈17,635"],
                ["متوسطة", "9.99", "170,000", "18,000", "188,000", "≈18,819"],
                ["كبيرة", "19.99", "360,000", "40,000", "400,000", "≈20,010"],
                ["بلاتين", "49.99", "900,000", "100,000", "1,000,000", "≈20,004"],
                ["ماسية", "99.99", "2,000,000", "250,000", "2,250,000", "≈22,502"],
            ],
            col_widths=[28 * mm, 22 * mm, 28 * mm, 22 * mm, 30 * mm, 30 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "تارجت الإدمن: راجع شهرياً نسبة الباقات الكبيرة عبر لوحة Wallet. إن زاد اعتماد اللاعبين "
            "على باقة $99 فقط وتجاوزت نسبة السحوبات 35% من الإيرادات الشهرية — خفّض البونص من الداشبورد.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ── 3 GIFTS SPLIT ──
    story.append(SectionTitle("٣", "الهدايا — سك الألماس وتقسيم الأرباح"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "عند إرسال هدية عادية: يُحسب عدد الألماس = أقل قيمة (diamondValue المعرّف، أو floor(السعر × 0.42)). "
            "ثم يُقسَّم مستقلاً: منصة 35% · مضيف 50% · صاحب وكالة 15% (في روم الوكالة). "
            "روم شخصي: منصة 35% · مضيف 65%. هدايا الحظ: سك 18% فقط (+ مردود ألعاب متوقع 48% للمرسل).",
            st["body"],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        table_ar(
            ["مثال: هدية 1,000 عملة", "ألماس مسكوك", "منصة", "مضيف", "وكالة"],
            [
                ["روم وكالة", "420", "147", "210", "63"],
                ["روم شخصي", "420", "147", "273", "—"],
            ],
            col_widths=[40 * mm, 32 * mm, 28 * mm, 28 * mm, 28 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "قيمة السحب الرسمية: 1 ألماسة = $0.00005  →  10,000 ألماسة = $0.50 (الحد الأدنى للسحب).",
            st["body"],
        )
    )
    story.append(
        p(
            "مثال ربح المنصة: داعم اشترى بـ $100 عبر وكيل شحن → أنفق كل العملات هدايا عادية في روم وكالة. "
            "الالتزام بالسحب للمضيف+الوكالة ≈ 16–22% من الـ $100. المنصة تحتفظ بـ 78–84% قبل المصاريف.",
            st["ok"],
        )
    )
    story.append(PageBreak())

    # ── 4 RECHARGE AGENT ──
    story.append(SectionTitle("٤", "وكيل الشحن — الإيداع والبيع الحر"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "وكيل الشحن ليس موظفاً: هو تاجر. يدفع للمنصة عضوية + ثمن رصيد جملة (USDT)، "
            "فيُصدَّر له رصيد كوينز في حساب الوكيل. يبيع للمستخدمين بسعر يحدّده هو (مرن). "
            "عند نفاد الرصيد يطلب تعبئة جديدة. المنصة لا تحدد سعر بيعه الإلزامي — فقط سعر الجملة والاقتراح.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        table_ar(
            ["البند", "القيمة الرسمية"],
            [
                ["رسوم العضوية", "$25 USDT (مرة) — إيراد صافٍ للمنصة"],
                ["سعر الجملة", "$0.010 لكل 100 عملة = $1.00 لكل 10,000 عملة"],
                ["سعر مقترح للبيع", "$0.014 لكل 100 = $1.40 لكل 10,000 (+40% هامش للوكيل)"],
                ["حرية التسعير", "يبيع الوكيل بأي سعر يريده (أعلى/أدنى على مسؤوليته)"],
                ["بونص تعبئة ≥$200", "+8% كوينز إضافية"],
                ["بونص تعبئة ≥$500", "+12% كوينز"],
                ["بونص تعبئة ≥$1,000", "+15% كوينز"],
                ["حد الرصيد", "من 10,000 إلى 20,000,000 عملة"],
            ],
            col_widths=[55 * mm, 105 * mm],
        )
    )
    story.append(Spacer(1, 4 * mm))
    story.append(p("مثال عملي — إيداع $1,000 كـ رصيد جملة:", st["body"]))
    story.append(
        p(
            "• ثمن الجملة: $1,000 → 10,000,000 عملة أساسية.\n"
            "• بونص 15% → +1,500,000 = إجمالي 11,500,000 عملة في رصيد الوكيل.\n"
            "• إن باع بسعر مقترح $1.40 / 10k يجمع ≈ $1,610 من الزبائن → ربح وكيل ≈ $610.\n"
            "• المنصة حصلت على $1,000 نقداً فوراً + لاحقاً حصة الهدايا عند الإنفاق.\n"
            "• عند نفاد الرصيد: رسالة «اشحن رصيد الوكيل» ويعيد الإيداع.",
            st["body_sm"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        p(
            "ملاحظة: السحب الشخصي للمضيف يمكن توجيهه عبر وكيل شحن (قناة صرف محلي)، "
            "بينما رصيد وكالة ألماس لا يُصرف عبر وكيل الشحن — يبقى تسوية المنصة.",
            st["muted"],
        )
    )
    story.append(PageBreak())

    # ── 5 HOST AGENCY TARGET ──
    story.append(SectionTitle("٥", "المضيف · الوكالة · التارجت الشهري"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "المضيف يربح من حصة الألماس (50% وكالة / 65% شخصي) ويمكنه السحب عند بلوغ 10,000 ألماسة. "
            "صاحب الوكالة يربح 15% من ألماس هدايا أعضاءه في رومات الوكالة + رسوم فتح وكالة 50,000 عملة. "
            "التارجت الشهري: سلم 34 مرحلة يقيس تقدّم المضيفة بالألماس المستلم خلال الشهر الميلادي.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        table_ar(
            ["مرحلة", "عتبة ألماس", "راتب مضيف $", "وكيل $", "الإجمالي $"],
            [
                ["1", "15,000", "1", "0", "1"],
                ["6", "100,000", "7", "0", "7"],
                ["10", "700,000", "30", "10", "40"],
                ["15", "1,800,000", "90", "30", "120"],
                ["20", "2,800,000", "170", "130", "300"],
                ["25", "5,000,000", "450", "550", "1,000"],
                ["30", "8,000,000", "1,200", "1,800", "3,000"],
                ["34", "15,000,000", "3,400", "2,000", "5,400"],
            ],
            col_widths=[28 * mm, 36 * mm, 32 * mm, 28 * mm, 32 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "سياسة التسليم: أجور التارجت دولار تُسدَّد يدوياً نهاية كل شهر ميلادي بعد مراجعة الإدارة "
            "(لوحة Host Target). لا تُخصم آلياً من خزنة السحوبات. الأرباح الأساسية للمضيف تبقى سحب الألماس. "
            "يمكنك كصاحب تطبيق تجميد سلم الرواتب الإضافي في الأشهر الضعيفة.",
            st["warn"],
        )
    )
    story.append(PageBreak())

    # ── 6 SINKS & GAMES ──
    story.append(SectionTitle("٦", "مصارف العملات (Sinks) — ربح صافٍ"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        table_ar(
            ["المصدر", "الوصف", "أثره على المنصة"],
            [
                ["مول التجميل", "إطارات / دخوليات / خلفيات / كروت روم", "عملات تُحرق → ربح 100%"],
                ["VIP 1–10+", "اشتراك زمني (أسبوعي/شهري/40 يوم)", "عملات تُحرق"],
                ["الألعاب Mikoo", "RTP≈80% (بيت 20%)", "صافي إيجابي طويل الأجل"],
                ["هدايا الحظ", "EV مردود ≈48% + سك 18%", "ربح مزدوج"],
                ["فتح وكالة", "50,000 عملة", "حرق عملات + نمو شبكة"],
                ["ألعاب السلوتس", "خسارة قسرية ~72%", "sink قوي"],
            ],
            col_widths=[36 * mm, 70 * mm, 54 * mm],
        )
    )
    story.append(Spacer(1, 4 * mm))
    story.append(SectionTitle("٧", "سياسة السحب والتسوية الشهرية"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "• الحد الأدنى: 10,000 ألماسة ($0.50).\n"
            "• القنوات: PayPal / بنك / USDT / وكيل شحن (للشخصية فقط).\n"
            "• دورة المراجعة: طلبات السحب تُراجع خلال 24–72 ساعة عمل.\n"
            "• التسوية الشهرية لصاحب المنصة: اجمع (إيرادات الشحن − سحوبات معتمدة − رواتب تارجت − "
            "عمليات) = صافي الربح. الهدف التشغيلي: صافي ≥ 45% من إجمالي الشحن الشهري.\n"
            "• ممنوع: الدعم الذاتي / تعدد حسابات وهمية / تلاعب بالحظ / خصم ألماس الوكالة كأنه شخصي.",
            st["body"],
        )
    )
    story.append(PageBreak())

    # ── 8 OWNER PROJECTIONS ──
    story.append(SectionTitle("٨", "توقعات أرباح صاحب التطبيق (سيناريوهات)"))
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "الأرقام إرشادية بعد ضبط economy-v3. افترض أن 70% من العملات تذهب للهدايا و 30% للمول/VIP/الألعاب "
            "(المصارف الصافية). عمولة المتجر 20% وسطياً على Play فقط. مزيج قنوات: 40% Play / 40% وكلاء / 20% USDT.",
            st["body"],
        )
    )
    story.append(Spacer(1, 2 * mm))
    story.append(
        table_ar(
            ["شحن شهري إجمالي", "تقدير صافي المنصة", "هامش ≈", "ملاحظة"],
            [
                ["$5,000", "$2,200 – $2,800", "44–56%", "انطلاق مبكر"],
                ["$20,000", "$9,500 – $12,000", "48–60%", "شبكة وكالات نشطة"],
                ["$50,000", "$25,000 – $32,000", "50–64%", "وكلاء شحن + USDT"],
                ["$100,000", "$52,000 – $68,000", "52–68%", "نضج + sinks قوية"],
                ["$250,000", "$135,000 – $175,000", "54–70%", "أرباح هائلة مع ضبط السحب"],
            ],
            col_widths=[38 * mm, 42 * mm, 28 * mm, 52 * mm],
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(
        p(
            "كيف تضمن «أرباحاً هائلة» دون الاعتماد على أحد؟ ركّز على: (1) توسيع وكلاء الشحن المحليين "
            "لأن ربح المنصة أعلى منهم من Play؛ (2) عروض مول/VIP شهرية؛ (3) عدم رفع سعر سحب الألماس؛ "
            "(4) عدم زيادة نسبة سك الهدايا فوق 42%؛ (5) عدم خفض حصة المنصة تحت 35% إلا بحملة مؤقتة.",
            st["ok"],
        )
    )
    story.append(PageBreak())

    # ── 9 RULES ──
    story.append(SectionTitle("٩", "قواعد ذهبية — لا تُكسر"))
    story.append(Spacer(1, 3 * mm))
    rules = [
        "لا ترفع سعر سحب الألماس فوق $0.00005 بدون إعادة حساب كامل الهامش.",
        "لا تزد كثافة العملات في باقة $99 فوق ≈23,000 عملة/$ دون خفض السك أو السحب.",
        "لا تعطِ وكيل الشحن بونص تعبئة فوق 15% إلا مؤقتاً (أزمة تفعيل).",
        "راتب التارجت دولار يدوي شهري — لا تفعّل صرف تلقائي مليوني.",
        "ألماس المنصة (عداد الإيرادات) لا يُسحَب للمستخدمين — ملك المنصة.",
        "وكيل الشحن يبيع بحرية؛ المنصة لا تضمن ربحه إن باع بخسارة.",
        "المضيف والوكالة يربحان من النشاط الحقيقي فقط — ممنوع تهريب دعم ذاتي.",
        "راجع لوحة الاقتصاد أسبوعياً: شحن / هدايا / سحب / ألعاب / وكلاء.",
    ]
    for i, r in enumerate(rules, 1):
        story.append(p(f"{i}. {r}", st["body"]))
    story.append(Spacer(1, 6 * mm))
    story.append(Banner("جاهز للنشر بثقة", "Economy-v3 · المضيف يربح · الوكالة تربح · الوكيل يتجر · المنصة تربح أكثر"))
    story.append(Spacer(1, 6 * mm))
    story.append(
        p(
            "بتوقيع هذه السياسة يعتمد صاحب التطبيق أن نموذج JEHO CHAT الاقتصادي مُصمم ليكون "
            "مربحاً للمنصة بشكل هيكلي (house edge)، لا لحظياً. أي تعديل جوهري يتم من لوحة الإدارة "
            "مع تسجيل التاريخ. نسخة هذا الملف على سطح المكتب هي المرجع التشغيلي المعتمد.",
            st["body"],
        )
    )
    story.append(Spacer(1, 8 * mm))
    story.append(p("JEHO CHAT — Official Platform Economy Policy", st["muted"]))
    story.append(p("Confidential · Internal + Partner distribution", st["muted"]))

    doc.build(story, onFirstPage=footer_header, onLaterPages=footer_header)
    return out


if __name__ == "__main__":
    path = build()
    print(str(path))
