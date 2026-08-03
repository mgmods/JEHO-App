# -*- coding: utf-8 -*-
"""
JEHO CHAT policy decks — landscape 960×540, detailed Economy v2 explanations.
Numbers from pricing-catalog / gifts / host-target / tasks / wallet / agencies.
No families. No invented features.
"""
from __future__ import annotations

from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw
from reportlab.lib.colors import Color, HexColor, white
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

W, H = 960, 540
ASSETS = Path(r"d:\JEHO-CHAT\docs\policy_assets")
OUT = Path(r"c:\Users\momf\Desktop")
FONT = r"C:\Windows\Fonts\arial.ttf"
FONT_B = r"C:\Windows\Fonts\arialbd.ttf"

pdfmetrics.registerFont(TTFont("Ar", FONT))
pdfmetrics.registerFont(TTFont("ArB", FONT_B))

GOLD = HexColor("#F2D45C")
GREEN_D = HexColor("#145A32")
GREEN = HexColor("#1E8449")
GREEN_M = HexColor("#27AE60")
ROW_A = HexColor("#C8F0D8")
ROW_B = HexColor("#EAF9F0")
INK = HexColor("#1A1A1A")
TEAL = HexColor("#0D7377")
PURPLE = HexColor("#6C3483")
SOFT = HexColor("#F4FCF7")


def ar(t: str) -> str:
    return get_display(arabic_reshaper.reshape(t))


def gradient(c: canvas.Canvas, left: Color, right: Color, steps: int = 72):
    for i in range(steps):
        t = i / (steps - 1)
        col = Color(
            left.red + (right.red - left.red) * t,
            left.green + (right.green - left.green) * t,
            left.blue + (right.blue - left.blue) * t,
        )
        c.setFillColor(col)
        c.rect(W * i / steps, 0, W / steps + 1.5, H, fill=1, stroke=0)


def radial_glow(c, cx, cy, r, color: Color, strength=0.35):
    for i in range(28, 0, -1):
        t = i / 28
        c.setFillColor(Color(color.red, color.green, color.blue, alpha=strength * (1 - t)))
        c.circle(cx, cy, r * t, fill=1, stroke=0)


_LOGO = None


def logo_reader():
    global _LOGO
    if _LOGO is not None:
        return _LOGO
    icon_path = ASSETS / "jeho_icon.png"
    if not icon_path.exists():
        icon_path = ASSETS / "jeho_logo.png"
    size = 256
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=28, fill=(13, 115, 119, 255))
    d.rounded_rectangle((4, 4, size - 5, size - 5), radius=24, outline=(242, 212, 92, 255), width=5)
    if icon_path.exists():
        icon = Image.open(icon_path).convert("RGBA")
        pad = 28
        icon = icon.resize((size - 2 * pad, size - 2 * pad), Image.Resampling.LANCZOS)
        tile.paste(icon, (pad, pad), icon)
    _LOGO = ImageReader(tile)
    return _LOGO


def draw_logo(c, x, y, size=88, label=True):
    c.drawImage(logo_reader(), x, y, width=size, height=size, mask="auto")
    if label:
        c.setFillColor(GOLD)
        c.setFont("ArB", 9)
        c.drawCentredString(x + size / 2, y - 14, "JEHO CHAT")


def gold_line(c, x1, y, x2, w=2):
    c.setStrokeColor(GOLD)
    c.setLineWidth(w)
    c.line(x1, y, x2, y)


def title_gold(c, text, x, y, size=24, align="right"):
    c.setFillColor(GOLD)
    c.setFont("ArB", size)
    t = ar(text)
    if align == "center":
        c.drawCentredString(x, y, t)
    else:
        c.drawRightString(x, y, t)


def title_ink(c, text, x, y, size=20, align="right"):
    c.setFillColor(INK)
    c.setFont("ArB", size)
    t = ar(text)
    if align == "center":
        c.drawCentredString(x, y, t)
    else:
        c.drawRightString(x, y, t)


def body_right(c, lines, x, y, size=12, leading=20, color=INK):
    c.setFillColor(color)
    c.setFont("Ar", size)
    yy = y
    for line in lines:
        c.drawRightString(x, yy, ar(line))
        yy -= leading
    return yy


def panel(c, x, y, w, h, fill=SOFT, stroke=GREEN_M, radius=10):
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(1.5)
    c.roundRect(x, y, w, h, radius, fill=1, stroke=1)


def draw_table(c, x, y_top, col_w, headers, rows, title=None, row_h=24):
    total_w = sum(col_w)
    y = y_top
    if title:
        c.setFillColor(GREEN_D)
        c.rect(x, y - row_h, total_w, row_h, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("ArB", 10)
        c.drawCentredString(x + total_w / 2, y - row_h + 7, ar(title))
        c.setStrokeColor(HexColor("#0B3B1E"))
        c.rect(x, y - row_h, total_w, row_h, fill=0, stroke=1)
        y -= row_h
    c.setFillColor(GREEN)
    c.rect(x, y - row_h, total_w, row_h, fill=1, stroke=0)
    cx = x
    c.setFillColor(white)
    c.setFont("ArB", 9)
    for i, h in enumerate(headers):
        c.setStrokeColor(HexColor("#0B3B1E"))
        c.rect(cx, y - row_h, col_w[i], row_h, fill=0, stroke=1)
        c.drawCentredString(cx + col_w[i] / 2, y - row_h + 7, ar(h))
        cx += col_w[i]
    y -= row_h
    for r_i, row in enumerate(rows):
        c.setFillColor(ROW_A if r_i % 2 == 0 else ROW_B)
        c.rect(x, y - row_h, total_w, row_h, fill=1, stroke=0)
        cx = x
        c.setFont("Ar", 9)
        c.setFillColor(INK)
        for i, cell in enumerate(row):
            c.setStrokeColor(HexColor("#5D6D7E"))
            c.rect(cx, y - row_h, col_w[i], row_h, fill=0, stroke=1)
            c.drawCentredString(cx + col_w[i] / 2, y - row_h + 7, ar(cell))
            cx += col_w[i]
        y -= row_h
    return y


def header_bar(c, title: str, left: Color, right: Color):
    gradient(c, left, right)
    draw_logo(c, 22, H - 100, 70)
    title_gold(c, title, W - 40, H - 52, 22)
    gold_line(c, 110, H - 72, W - 40)


def cover(c, subtitle_lines, title="مقدمة سياسة عن JEHO CHAT"):
    gradient(c, HexColor("#7DCEA0"), HexColor("#145A32"))
    radial_glow(c, 200, 280, 220, HexColor("#F7DC6F"), 0.22)
    c.setFillColor(HexColor("#58D68D"))
    c.roundRect(70, 130, 260, 260, 18, fill=1, stroke=0)
    c.setStrokeColor(GOLD)
    c.setLineWidth(3)
    c.roundRect(70, 130, 260, 260, 18, fill=0, stroke=1)
    draw_logo(c, 105, 165, 190, label=False)
    c.setFillColor(white)
    c.setFont("ArB", 13)
    c.drawCentredString(200, 105, "JEHO CHAT")
    c.setFont("Ar", 11)
    c.drawCentredString(200, 85, "Voice Chat & Play Rooms")
    gold_line(c, 95, 72, 305, 1.5)
    title_gold(c, title, W - 55, H - 110, 24)
    gold_line(c, 480, H - 125, W - 55)
    c.setStrokeColor(GOLD)
    c.setLineWidth(4)
    c.line(470, H - 160, 470, H - 340)
    body_right(c, subtitle_lines, W - 60, H - 170, size=13, leading=32, color=white)
    c.showPage()


# ───────────────────── ADMIN ─────────────────────


def admin_slides(c: canvas.Canvas):
    cover(
        c,
        [
            "نوع التطبيق : غرف صوتية + ألعاب + اقتصاد عملات/ألماس",
            "هذا الملف للإدارة — شرح كامل للأرقام والأنظمة",
            "Economy v2 · أغسطس 2026 · بلا نظام عائلات",
        ],
    )

    # TOC
    header_bar(c, "فهرس سياسة الإدارة (مفصل)", HexColor("#58D68D"), HexColor("#117A65"))
    panel(c, 40, 60, 880, 380, fill=white, stroke=TEAL)
    body_right(
        c,
        [
            "١. العملات والألماس والسحب    ٢. تقسيم الهدايا العادية والوكالة",
            "٣. هدايا الحظ (المحظوظ) بالتفصيل    ٤. تارجت المضيفة الشهري (قابل للزيادة)",
            "٥. المهام والمكافآت والدعوات    ٦. الوكالات وعمولتها",
            "٧. باقات الشحن وباقات الداعم    ٨. VIP / الأرستقراطية والمول",
            "٩. صندوق الحظ وتمويله    ١٠. قواعد الإدارة والمراجعة",
            "",
            "ملاحظة: كل رقم هنا من كود السيرفر (pricing-catalog + gifts + host-target).",
            "لا يوجد «تسكير تارجت» كهدية منفصلة — التسكير = إكمال مرحلة التارجت تلقائياً.",
        ],
        890,
        400,
        size=12,
        leading=28,
    )
    c.showPage()

    # 1 currencies
    header_bar(c, "١) العملات · الألماس · السحب", HexColor("#AED6F1"), HexColor("#58D68D"))
    panel(c, 40, 70, 880, 370, fill=HexColor("#EBF5FB"), stroke=TEAL)
    body_right(
        c,
        [
            "• العملات (Coins): تُشترى بالشحن وتُنفق على الهدايا والألعاب والمول وVIP.",
            "• الألماس (Diamonds): يُسكّ من الهدايا للمستلم — قابل للسحب النقدي (ما عدا traderDiamonds).",
            "• تبديل ألماس → عملات داخل التطبيق: ×0.55 (من المحفظة).",
            "• سعر السحب الرسمي: 1 ألماسة = $0.00005",
            "• الحد الأدنى للسحب (تارجت السحب في المحفظة): 10,000 ألماسة",
            "• باقات سحب افتراضية بالدولار: 0.5 · 1 · 2.5 · 5 · 10 · 20 · 50 · 100",
            "• تبادل ألماس بين مضيفات → traderDiamonds (غير قابل للسحب النقدي).",
            "• الفضة/نقاط المهام: عرض/مهام فقط — ليست ألماس سحب.",
        ],
        890,
        400,
        size=12,
        leading=30,
    )
    c.showPage()

    # 2 gift split
    header_bar(c, "٢) تقسيم الهدايا العادية", HexColor("#F7DC6F"), HexColor("#1ABC9C"))
    body_right(
        c,
        [
            "عند إرسال هدية عادية: تُخصم العملات من المرسل ويُسكّ حوض ألماس = 45٪ من قيمة العملات.",
            "ثم يُوزَّع الحوض:",
        ],
        W - 40,
        H - 100,
        size=12,
        leading=20,
    )
    draw_table(
        c,
        120,
        360,
        [240, 110, 110, 130],
        ["الحالة", "المنصة", "الوكالة", "المضيف"],
        [
            ["روم وكالة نشطة", "30٪", "15٪", "55٪"],
            ["روم شخصي / بلا وكالة", "30٪", "—", "70٪"],
            ["المستلم = مالك الوكالة", "30٪", "0٪", "70٪"],
        ],
        title="توزيع حوض الألماس بعد سكّ 45٪ (Economy v2)",
        row_h=30,
    )
    body_right(
        c,
        [
            "النسب قابلة للتعديل من لوحة الإدارة (منصة ≤40٪ · عمولة وكالة ≤50٪).",
            "الدعم الذاتي ممنوع تماماً — حتى صاحب الروم لا يرسل هدية لنفسه.",
        ],
        W - 40,
        120,
        size=12,
        leading=22,
    )
    c.showPage()

    # 3 lucky gifts DETAILED
    header_bar(c, "٣) هدايا الحظ (المحظوظ) — شرح كامل", PURPLE, HexColor("#F5B041"))
    panel(c, 30, 250, 900, 170, fill=HexColor("#F5EEF8"), stroke=PURPLE)
    body_right(
        c,
        [
            "طبقات الهدايا الرسمية: حظ برونزي 100 عملة · فضي 500 · ذهبي 1000.",
            "سكّ ألماس للمستلم من هدية الحظ: 20٪ فقط (أقل من العادي 45٪) لأن المرسل قد يسترد عملات.",
            "المردود (إن فاز) يذهب عملات للمرسل فقط — ألماس المستلم لا يتضاعف.",
            "الكمية القصوى لهدية الحظ: 177 · للعادية: 99.",
            "في التطبيق يظهر توست: «مردود +N» أو «ضرب حظه ×M · +N» + سطر في الشات.",
        ],
        900,
        390,
        size=11,
        leading=22,
    )
    draw_table(
        c,
        80,
        230,
        [200, 160, 280],
        ["احتمال تقريبي", "المضاعف", "المعنى"],
        [
            ["~40٪", "0.30× – 0.80×", "مردود ناعم (عملات للمرسل)"],
            ["~10٪", "1.20× – 2.50×", "فوز متوسط"],
            ["~2.5٪", "3× – 8×", "فوز كبير (سقف ×8)"],
            ["~47.5٪", "بدون مردود", "خصم العملات بدون استرجاع"],
        ],
        title="جدول رول الحظ في السيرفر (هدف EV ≈ 0.52)",
        row_h=26,
    )
    c.showPage()

    # 4 host monthly target DETAILED
    header_bar(c, "٤) تارجت المضيفة الشهري (قابل للزيادة)", HexColor("#F5B7B1"), HexColor("#82E0AA"))
    panel(c, 30, 300, 900, 120, fill=HexColor("#FDEDEC"), stroke=GREEN)
    body_right(
        c,
        [
            "تارجت المضيفة ≠ تارجت السحب. التارجت الشهري يتقدم بألماس الهدايا المستلمة بعد التقسيم.",
            "المراحل قابلة للزيادة/التعديل من الإعداد host_monthly_target في لوحة الإدارة.",
            "عند إكمال مرحلة: تُسكَّر تلقائياً وتُصرف المكافأة (عملات ± ألماس). بعد آخر مرحلة تبدأ دورة جديدة.",
            "لا توجد «هدية تسكير تارجت» منفصلة في التطبيق — التسكير = إكمال العتبة.",
        ],
        900,
        390,
        size=11,
        leading=22,
    )
    draw_table(
        c,
        100,
        280,
        [100, 140, 140, 140],
        ["مرحلة", "العتبة (◆)", "مكافأة عملات", "مكافأة ألماس"],
        [
            ["1", "1,000", "50", "0"],
            ["2", "5,000", "200", "20"],
            ["3", "15,000", "500", "80"],
            ["4", "35,000", "1,000", "200"],
            ["5", "75,000", "2,500", "500"],
        ],
        title="المراحل الافتراضية (الإدارة تقدر ترفع العتبات والمكافآت)",
        row_h=24,
    )
    c.showPage()

    # 5 tasks
    header_bar(c, "٥) المهام · الحضور · دعوة الغرفة", HexColor("#D7BDE2"), HexColor("#ABEBC6"))
    panel(c, 30, 70, 900, 370, fill=white, stroke=PURPLE)
    body_right(
        c,
        [
            "• مهام المستخدم ومهام المضيفة في مركز المهام — استلامها يعطي نقاط/فضة/عملات حسب المهمة.",
            "• مهام المضيفة (h1…h20) لا تعطي ألماس سحب مجاني من زر «استلام المهمة».",
            "• مكافأة دعوة غرفة (مضيفة وكالة فقط):",
            "    – ضيف ذكر جديد (حساب ≤30 يوم) يُدعى للروم ويبقى ≈ 120 ثانية",
            "    – المكافأة: 15 ألماسة (من الكتالوج HOST_ROOM_INVITE_REWARD)",
            "    – حد أقصى: 5 مكافآت لكل مضيفة في اليوم (UTC)",
            "• جولة دردشة ذكور جدد: 5–10 رسائل خلال 180 ثانية (للمهام — ليست ألماس).",
            "• الحضور (check-in) مرة يومياً لكل حساب.",
            "• التحايل/الحسابات المتعددة = إيقاف المكافآت.",
        ],
        900,
        400,
        size=12,
        leading=28,
    )
    c.showPage()

    # 6 agencies
    header_bar(c, "٦) الوكالات (بديل العائلات)", HexColor("#AED6F1"), HexColor("#58D68D"))
    panel(c, 40, 70, 880, 370, fill=HexColor("#EBF5FB"), stroke=TEAL)
    body_right(
        c,
        [
            "• لا يوجد نظام عائلات. البديل = الوكالة (مالك · مدير · مضيف · عضو).",
            "• فتح الوكالة ≈ 50,000 عملة ثم مراجعة الإدارة (قابل للتعديل).",
            "• عمولة المالك الافتراضية 15٪ من حوض ألماس هدايا الأعضاء.",
            "• المالك يوزّع ألماس قابل للسحب على الأعضاء عبر النظام.",
            "• روم الوكالة دائم وعام ويظهر في Hot عند وجود مضيف نشط.",
            "• «هدية وكالة» في الكتالوج = هدية عادية بسعر ≈499 عملة (تبويب وكالة) —",
            "  ليست اقتصاداً منفصلاً؛ التقسيم العام 30/15/55 يبقى هو الحكم.",
            "• يُمنع الوكالات الوهمية والحسابات المتعددة للاحتيال على العمولة.",
        ],
        890,
        400,
        size=12,
        leading=30,
    )
    c.showPage()

    # 7 recharge + supporter packs
    header_bar(c, "٧) باقات الشحن وباقات الداعم", HexColor("#F5B7B1"), HexColor("#F5B041"))
    draw_table(
        c,
        80,
        420,
        [110, 160, 120, 160],
        ["السعر $", "العملات", "بونص", "الإجمالي"],
        [
            ["0.99", "12,000", "0", "12,000"],
            ["2.99", "40,000", "2,000", "42,000"],
            ["4.99", "85,000", "8,000", "93,000"],
            ["9.99", "180,000", "20,000", "200,000"],
            ["19.99", "380,000", "45,000", "425,000"],
            ["49.99", "950,000", "120,000", "1,070,000"],
            ["99.99", "2,200,000", "350,000", "2,550,000"],
        ],
        title="كتالوج الشحن الرسمي Economy v2",
        row_h=22,
    )
    body_right(
        c,
        [
            "باقات الداعم (شحن تراكمي): ≈$200/7 أيام · $500/15 يوم · $1000/30 يوم → إطار + مزايا.",
            "وكيل الشحن منفصل عن عمولة الوكالة — أسعار ضمن الكتالوج أو عرض معتمد فقط.",
        ],
        W - 40,
        90,
        size=11,
        leading=18,
    )
    c.showPage()

    # 8 VIP + mall
    header_bar(c, "٨) VIP / الأرستقراطية · الإطارات · الدخولية", HexColor("#F9E79F"), HexColor("#76D7C4"))
    panel(c, 30, 70, 900, 370, fill=SOFT, stroke=GOLD)
    body_right(
        c,
        [
            "• VIP = الأرستقراطية في الواجهة (نفس النظام، مستويات 1–100).",
            "• الإيجار فقط: 7 / 30 / 40 يوماً — لا VIP مجاني دائم من المهام أو التارجت.",
            "• أمثلة أسعار شهرية: VIP1≈99 عملة · VIP2≈199 … VIP10≈9999 (ثم منحنى تصاعدي).",
            "• مزايا تدريجية: دخولية، شارة، حماية من الطرد/الكتم، هدايا مشاهير، تعليق طائر…",
            "• المول بالعملات: إطارات (vip_badge) · دخولية (entry_effect) · بطاقة روم · خلفية روم.",
            "  سلم أسعار تقريباً: دخولية 399→3999 · إطار 499→3999 · خلفية من 99 صعوداً.",
            "• توست الانضمام (join_toast) وشارة المضيف كمنتج مول منفصل: معطّلة في السيرفر حالياً",
            "  (الدخولية تغطي التأثير البصري).",
            "• الثروة/السحر = مستويات عرض حسب الإرسال/الاستلام — ليست عملة.",
        ],
        900,
        400,
        size=11,
        leading=28,
    )
    c.showPage()

    # 9 lucky boxes
    header_bar(c, "٩) صندوق الحظ العائم · dayGold · الكأس", HexColor("#85C1E9"), HexColor("#F5B041"))
    panel(c, 40, 70, 880, 370, fill=HexColor("#EBF5FB"), stroke=TEAL)
    body_right(
        c,
        [
            "• صندوق الحظ في الروم منفصل عن «هدية الحظ» في الكتالوج.",
            "• يومي مجاني: مرة/يوم — جوائز عملات مرجّحة (مثلاً 20/40/100) + نقاط.",
            "• صندوق مدفوع: ≈800 عملة/يوم — جوائز عملات أعلى (بدون ألماس سحب من الصندوق).",
            "• يمكن تمويل الصندوق من مضيف/وكالة (رصيد تمويل مرتبط بالروم).",
            "• dayGold: مجموع عملات الهدايا في الروم منذ منتصف الليل — يظهر على كأس الروم.",
            "• لوحات المساهمة (ثروة/سحر) لفترات يوم/أسبوع/شهر.",
            "• كأس الروم الأسبوعي/الشهري يمكن تفعيله بجوائز من إعدادات الأدمن.",
        ],
        890,
        400,
        size=12,
        leading=30,
    )
    c.showPage()

    # 10 admin rules
    header_bar(c, "١٠) قواعد الإدارة والمراجعة", HexColor("#5DADE2"), HexColor("#1E8449"))
    panel(c, 40, 70, 880, 370, fill=white, stroke=GOLD)
    body_right(
        c,
        [
            "• أي تغيير نسب/عتبات/باقات يُوثَّق في app_settings أو لوحة التحكم.",
            "• تارجت المضيفة قابل للزيادة: عدّل مراحل host_monthly_target (عتبة + مكافآت).",
            "• الاحتيال والحسابات المتعددة = حظر/مصادرة.",
            "• ملف المضيفات والداعمين نسخة مبسّطة للجمهور — هذا الملف للإدارة فقط.",
            "• التفسير النهائي لبنود السياسة لإدارة JEHO CHAT.",
            "• النسخة: Economy v2 · أغسطس 2026",
        ],
        890,
        400,
        size=13,
        leading=34,
    )
    c.showPage()


# ───────────────────── HOSTS / SUPPORTERS ─────────────────────


def hosts_slides(c: canvas.Canvas):
    cover(
        c,
        [
            "دليل مبسّط للمضيفات والداعمين",
            "هدايا · حظ · تارجت · مهام · وكالة · سحب",
            "اقرئي كل صفحة بتمعن — الأرقام من النظام الرسمي",
        ],
        title="دليل المضيفات والداعمين",
    )

    header_bar(c, "كيف تكسبين ألماساً؟", PURPLE, HexColor("#82E0AA"))
    panel(c, 40, 70, 880, 370, fill=HexColor("#F5EEF8"), stroke=PURPLE)
    body_right(
        c,
        [
            "١) الهدايا العادية: يُسكّ 45٪ ألماس ثم تأخذين 55٪ مع وكالة (أو 70٪ بلا وكالة).",
            "٢) هدايا الحظ: يُسكّ 20٪ ألماس لكِ — والمرسل قد يسترد عملات إذا فاز.",
            "٣) تارجت المضيفة الشهري: كل ألماس تستلمينه بعد التقسيم يرفع التارجت.",
            "٤) دعوة ضيف جديد للروم (مضيفة وكالة): 15 ألماسة بعد بقاء ≈ دقيقتين (حد 5/يوم).",
            "٥) السحب: 1 ألماسة = $0.00005 · الحد الأدنى 10,000 ألماسة.",
            "٦) ممنوع دعم نفسكِ — النظام يرفض الهدايا الذاتية.",
        ],
        890,
        400,
        size=13,
        leading=36,
    )
    c.showPage()

    header_bar(c, "هدايا الحظ (للداعم والمضيفة)", HexColor("#F7DC6F"), HexColor("#9B59B6"))
    panel(c, 30, 200, 900, 220, fill=HexColor("#FEF9E7"), stroke=GOLD)
    body_right(
        c,
        [
            "للداعم: تختارين حظ برونزي/فضي/ذهبي (100 / 500 / 1000 عملة).",
            "قد يرجع لكِ مردود عملات (توست «مردود» أو «ضرب حظه»).",
            "للمضيفة: تستلمين ألماساً بنسبة 20٪ من قيمة الهدية ثم التقسيم المعتاد.",
            "الحظ ليس قماراً مضموناً — كثير من الجولات بدون مردود.",
        ],
        900,
        390,
        size=13,
        leading=28,
    )
    draw_table(
        c,
        160,
        180,
        [160, 160, 200],
        ["الهدية", "السعر", "سكّ ألماس للمستلم"],
        [
            ["حظ برونزي", "100 عملة", "20٪"],
            ["حظ فضي", "500 عملة", "20٪"],
            ["حظ ذهبي", "1000 عملة", "20٪"],
        ],
        title="طبقات الحظ الرسمية",
        row_h=26,
    )
    c.showPage()

    header_bar(c, "تارجت المضيفة — كيف يزيد ويُسكَّر؟", HexColor("#F5B7B1"), HexColor("#58D68D"))
    panel(c, 30, 300, 900, 120, fill=HexColor("#FDEDEC"), stroke=GREEN)
    body_right(
        c,
        [
            "كل هدية تستلمينها تزيد تقدّم التارجت (بالألماس بعد التقسيم).",
            "عند وصول عتبة مرحلة: المرحلة تُسكَّر تلقائياً وتدخل المكافأة لمحفظتكِ.",
            "بعد إكمال كل المراحل تبدأ دورة جديدة في نفس الشهر (التارجت قابل للتكرار).",
            "الإدارة تقدر ترفع العتبات — راقبي التارجت من المحفظة/شاشة التارجت.",
        ],
        900,
        390,
        size=12,
        leading=22,
    )
    draw_table(
        c,
        120,
        280,
        [100, 130, 130, 130],
        ["مرحلة", "تحتاج ◆", "عملات", "ألماس"],
        [
            ["1", "1,000", "50", "—"],
            ["2", "5,000", "200", "20"],
            ["3", "15,000", "500", "80"],
            ["4", "35,000", "1,000", "200"],
            ["5", "75,000", "2,500", "500"],
        ],
        title="المراحل الافتراضية",
        row_h=24,
    )
    c.showPage()

    header_bar(c, "المهام والدعوات", HexColor("#D7BDE2"), HexColor("#ABEBC6"))
    panel(c, 40, 70, 880, 370, fill=white, stroke=PURPLE)
    body_right(
        c,
        [
            "• افتحي مركز المهام يومياً — استلمي المكافآت (نقاط/فضة/عملات حسب المهمة).",
            "• دعوة روم مكتملة = 15 ألماسة (مضيفة وكالة + ضيف جديد يبقى دقيقتين).",
            "• حد 5 دعوات مكافأة باليوم — بعدها تُحتسب للمهام فقط إن وُجدت.",
            "• لا تنتظري ألماس من زر استلام المهمة العادية — الألماس من الهدايا/الدعوة/التارجت.",
            "• للداعم: المهام اليومية والحضور تزيد مكافآتكِ داخل التطبيق.",
        ],
        890,
        400,
        size=13,
        leading=36,
    )
    c.showPage()

    header_bar(c, "الوكالة وتقسيم الهدايا", HexColor("#AED6F1"), HexColor("#58D68D"))
    draw_table(
        c,
        140,
        380,
        [220, 110, 110, 120],
        ["الحالة", "منصة", "وكالة", "مضيفة"],
        [
            ["مع وكالة", "30٪", "15٪", "55٪"],
            ["بلا وكالة", "30٪", "—", "70٪"],
        ],
        title="بعد سكّ الألماس (عادي 45٪ / حظ 20٪)",
        row_h=36,
    )
    body_right(
        c,
        [
            "انضمي لوكالة موثوقة. فتح وكالة ≈ 50,000 عملة + موافقة الإدارة.",
            "JEHO بلا عائلات — الوكالة هي النظام الرسمي.",
        ],
        W - 50,
        160,
        size=13,
        leading=24,
    )
    c.showPage()

    header_bar(c, "للداعمين: شحن · إطارات · VIP", HexColor("#F9E79F"), HexColor("#76D7C4"))
    panel(c, 40, 70, 880, 370, fill=SOFT, stroke=GOLD)
    body_right(
        c,
        [
            "• اشحن من التطبيق أو وكيل معتمد فقط (باقات من $0.99 إلى $99.99).",
            "• أهدِ من المتجر: عادي أو حظ — لا تدعم المضيفة لنفسها.",
            "• VIP/الأرستقراطية: اشتراك مؤقت بالعملات (7/30/40 يوم).",
            "• المول: اشترِ إطاراً أو دخولية بالعملات لتزيين ملفكِ ورومكِ.",
            "• صندوق الحظ في الروم: مجاني مرة يومياً أو مدفوع ≈800 عملة — جوائز عملات.",
            "• باقات الداعم عند الشحن التراكمي تعطي إطاراً ومزايا إضافية.",
        ],
        890,
        400,
        size=13,
        leading=34,
    )
    c.showPage()

    header_bar(c, "مرجع السحب السريع", PURPLE, HexColor("#82E0AA"))
    draw_table(
        c,
        200,
        400,
        [110, 180, 160],
        ["مستوى", "ألماس", "قيمة $"],
        [
            ["1", "400,000", "20$"],
            ["2", "1,200,000", "60$"],
            ["3", "2,000,000", "100$"],
            ["4", "4,000,000", "200$"],
            ["5", "8,000,000", "400$"],
            ["6", "16,000,000", "800$"],
            ["7", "32,000,000", "1,600$"],
            ["8", "40,000,000", "2,000$"],
        ],
        title="1 ألماسة = $0.00005 · حد أدنى 10,000",
        row_h=26,
    )
    c.showPage()

    header_bar(c, "قواعد مهمة", HexColor("#F5B7B1"), HexColor("#1E8449"))
    panel(c, 40, 80, 880, 350, fill=HexColor("#FDEDEC"), stroke=GREEN)
    body_right(
        c,
        [
            "• لا أرصدة خارج التطبيق · لا حسابات متعددة.",
            "• التحايل على المهام/التارجت/الحظ = إيقاف.",
            "• هذا الدليل ملخص — ملف الإدارة أوضح للأرقام الدقيقة.",
            "• Economy v2 · أغسطس 2026 · JEHO CHAT",
        ],
        890,
        380,
        size=15,
        leading=42,
    )
    c.showPage()


def build(path: Path, slides_fn):
    path.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(path), pagesize=(W, H))
    slides_fn(c)
    c.save()
    print("OK", path.name.encode("ascii", "replace").decode("ascii"), path.stat().st_size)


def main():
    build(OUT / "JEHO_CHAT_سياسة_الإدارة.pdf", admin_slides)
    build(OUT / "JEHO_CHAT_دليل_المضيفات_والداعمين.pdf", hosts_slides)


if __name__ == "__main__":
    main()
