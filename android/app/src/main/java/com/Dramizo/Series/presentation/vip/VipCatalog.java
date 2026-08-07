package com.Dramizo.Series.presentation.vip;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.util.VipStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Full aristocracy (VIP) product map from app/backend behavior + plan benefits.
 *
 * Categories:
 * 1) Visual package auto-granted with rent
 * 2) Inside voice rooms (chat, join, seat, gifts priority)
 * 3) Agency rooms / user cards
 * 4) Mall unlocks (SVGA frames / entries)
 * 5) Tier power ladder (anti-kick/mute, host badge, …)
 */
public final class VipCatalog {
    public static final int MAX_TIER = 7;

    private VipCatalog() {}

    public static int clamp(int level) {
        return Math.min(MAX_TIER, Math.max(1, level));
    }

    @NonNull
    public static String titleAr(int level) {
        switch (clamp(level)) {
            case 1: return "برونزي";
            case 2: return "فضي";
            case 3: return "زمردي";
            case 4: return "ياقوتي";
            case 5: return "عنقاء";
            case 6: return "ليلي ملكي";
            default: return "ذهبي ملوكي";
        }
    }

    @NonNull
    public static String headlineAr(int level) {
        return "VIP " + clamp(level) + " · " + titleAr(level);
    }

    @Nullable
    public static String frameUrl(int level) {
        return VipStyle.fixedFramePath(clamp(level));
    }

    @Nullable
    public static String headUrl(int level) {
        return VipStyle.fixedHeadPath(clamp(level));
    }

    @NonNull
    public static String medalUrl(int level) {
        int t = clamp(level);
        return "/assets/cosmetics/vip/vip_medal_mikoo_" + t + ".png?v=20260806vipfix1";
    }

    /** Paid mall SVGA head frame code unlocked to buy at this VIP. */
    @NonNull
    public static String mallBeastFrameName(int level) {
        switch (clamp(level)) {
            case 1: return "أسد برونزي (مول)";
            case 2: return "نمر فضي (مول)";
            case 3: return "تنين زمردي (مول)";
            case 4: return "ديناصور ياقوتي (مول)";
            case 5: return "عنقاء قرمزية (مول)";
            case 6: return "تنين أسود بنفسجي (مول)";
            default: return "تنين ذهبي ملوكي (مول)";
        }
    }

    @Nullable
    public static String mallBeastFramePreviewUrl(int level) {
        // Thumbnails live under frames path if present; ServerAssets soft-fails.
        int t = clamp(level);
        int code = 84 + t; // 85..91
        return "/assets/cosmetics/frames/frame_mikoo_" + code + "_vip" + t + ".png";
    }

    @DrawableRes
    public static int medalDrawable(int level) {
        switch (clamp(level)) {
            case 1: return R.drawable.vip_medal_mikoo_1;
            case 2: return R.drawable.vip_medal_mikoo_2;
            case 3: return R.drawable.vip_medal_mikoo_3;
            case 4: return R.drawable.vip_medal_mikoo_4;
            case 5: return R.drawable.vip_medal_mikoo_5;
            case 6: return R.drawable.vip_medal_mikoo_6;
            default: return R.drawable.vip_medal_mikoo_7;
        }
    }

    @DrawableRes
    public static int bubbleDrawable(int level) {
        switch (clamp(level)) {
            case 1: return R.drawable.vip_chat_bubble_1;
            case 2: return R.drawable.vip_chat_bubble_2;
            case 3: return R.drawable.vip_chat_bubble_3;
            case 4: return R.drawable.vip_chat_bubble_4;
            case 5: return R.drawable.vip_chat_bubble_5;
            case 6: return R.drawable.vip_chat_bubble_6;
            default: return R.drawable.vip_chat_bubble_7;
        }
    }

    @ColorInt
    public static int accent(int level) {
        return VipStyle.forLevel(clamp(level)).stroke;
    }

    /**
     * Full card content: sections for visual, rooms, agencies, mall, powers.
     */
    @NonNull
    public static List<Section> sectionsForCard(int vipLevel) {
        int L = clamp(vipLevel);
        List<Section> out = new ArrayList<>();

        // ── 1. Package that ships with rent ──
        Section visual = new Section("الهوية البصرية الكاملة", "تُفعَّل تلقائياً مع إيجار هذا المستوى");
        visual.add(perk("ميدالية الأرستقراطية VIP " + L, true, true,
                "تُمنح وتُلبس على البروفايل والبطاقة"));
        visual.add(perk("إطار الأرستقراطية الثابت", true, true,
                "إطار ud_vip_tou على بطاقتك وغرفة الصوت"));
        visual.add(perk("شريط هوية الرأس", true, true,
                "لوحة ic_head_vip على بطاقة المستخدم"));
        visual.add(perk("فقاعة شات القاعة VIP " + L, true, true,
                "سكن فقاعة محادثة مباشرة في الغرفة"));
        visual.add(perk("فقاعة الرسائل الخاصة", true, true,
                "تمييز فقاعة الشات 1:1 بنفس المستوى"));
        visual.add(perk("شارة VIP ملونة + لون الاسم", true, true,
                "شريحة VIP وألوان الاسم في القوائم والغرف"));
        out.add(visual);

        // ── 2. Inside rooms (personal + shared) ──
        Section room = new Section("مزايا داخل الغرف الصوتية", "تظهر فور دخولك أي غرفة / قاعة");
        room.add(perk("دخول VIP بأولوية التأثير", true, false,
                "صفّ تأثير الدخول (VIP_ENTRY) أعلى من الأعضاء العاديين"));
        room.add(perk("Toast دخول مميز", true, false,
                "نمط VIP على إشعار الانضمام داخل الغرفة"));
        room.add(perk("هوية على مقعد المايك", true, false,
                "مستوى VIP يظهر معك على المقعد والقوائم"));
        room.add(perk("شارة داعم VIP عند الإهداء", true, false,
                "إن لم تكن داعم إنفاق أعلى — يُصنَّف حضورك VIP في الهدايا"));
        room.add(perk("أولوية غرفة · درجة " + L, true, false,
                "مستوى أولوية الحضور = VIP " + L + " (roomPriority)"));
        room.add(perk("تأثير دخول / رايد (إن مُجهَّز)", L >= 1, L >= 1,
                L >= 5
                        ? "يمكنك تجهيز وتفعيل تأثيرات الدخول؛ VIP 5+ يفتح سكنات دخول VIP في المول"
                        : "فعّل تأثير دخول من المعرض؛ VIP يرفع أولوية عرضه عند الانضمام"));
        if (L >= 2) {
            room.add(perk("شارة المضيف المتحركة", true, false,
                    "امتياز hostBadge من خطة VIP 2 فأعلى"));
        }
        if (L >= 3) {
            room.add(perk("حماية من الطرد", true, false,
                    "antiKick — امتياز حماية داخل الغرفة من VIP 3"));
        }
        if (L >= 4) {
            room.add(perk("حماية من الكتم", true, false,
                    "antiMute — امتياز حماية المايك/الكتم من VIP 4"));
        }
        if (L >= 5) {
            room.add(perk("هدايا نخبة / تمييز إهداء", true, false,
                    "exclusiveGifts — امتياز هدايا مميزة من VIP 5"));
        }
        if (L >= 6) {
            room.add(perk("تعليق طائر في الغرفة", true, false,
                    "flyingComment — امتياز تعليقات مميزة من VIP 6"));
        }
        if (L >= 7) {
            room.add(perk("الحضور الملكي الكامل", true, false,
                    "أرقى مظهر ومزايا الغرفة لـ VIP 7"));
        }
        out.add(room);

        // ── 3. Agencies ──
        Section agency = new Section("الوكالات وغرف العائلة", "كيف يظهر VIP داخل الوكالات");
        agency.add(perk("بطاقة مستخدم بلمسة أرستقراطية", true, true,
                "تدرّج VIP وإطار النبلاء على بطاقتك داخل غرف الوكالة"));
        agency.add(perk("هوية VIP على شاشات الوكالة", true, true,
                "الميدالية والإطار يظهران في بطاقة الغرفة والعائلة"));
        agency.add(perk("تميّز عن الأعضاء العاديين في الوكالة", true, false,
                "شارة VIP ولون الاسم واضحان بجانب مضيف الوكالة"));
        if (L >= 2) {
            agency.add(perk("حضور مضيف أقوى", true, false,
                    "مع VIP 2+ تبرز هويتك أكثر على مسرح الغرفة/الوكالة"));
        }
        agency.add(perk("فتح إطارات الوكالة المؤهلة في المول", true, false,
                "أي إطار SVGA بقفل VIP " + L + " يصبح قابلاً للشراء واللبس في غرفة الوكالة أيضاً"));
        out.add(agency);

        // ── 4. Mall unlocks ──
        Section mall = new Section("فتح معرض المول (شراء)", "VIP يفتح — الشراء بالعملات منفصلاً");
        mall.add(perk(mallBeastFrameName(L), true, false,
                "شراء إطار رأس متحرك VIP " + L + " من المول (لا يُمنح مجاناً)"));
        for (int i = 1; i < L; i++) {
            mall.add(perk("متاح أيضاً: " + mallBeastFrameName(i), true, false,
                    "لأن مستواك أعلى — تفتح كل إطارات VIP 1…" + L));
        }
        if (L >= 5) {
            mall.add(perk("تأثيرات دخول VIP في المول", true, false,
                    "سكنات دخول مسمّاة VIP 5 / 6 / 7 (شراء من المول)"));
        } else {
            mall.add(perk("تأثيرات دخول VIP (من VIP 5)", false, false,
                    "افتح VIP 5 فأعلى لشراء سكنات الدخول الخاصة"));
        }
        mall.add(perk("أي مادة مول بقفل minVip ≤ " + L, true, false,
                "بوابة المول تتحقق من VIP قبل الشراء واللبس"));
        out.add(mall);

        // ── 5. Ladder teaser ──
        if (L < MAX_TIER) {
            Section next = new Section("الترقية التالية", "ماذا يضاف لو رفعت المستوى");
            int n = L + 1;
            String unlock;
            switch (n) {
                case 2: unlock = "شارة المضيف + مظهر فضي كامل"; break;
                case 3: unlock = "حماية من الطرد + إطار/فقاعة زمردي"; break;
                case 4: unlock = "حماية من الكتم + هوية ياقوتية"; break;
                case 5: unlock = "هدايا نخبة + دخول VIP في المول + عنقاء"; break;
                case 6: unlock = "تعليق طائر + ليلي ملكي"; break;
                default: unlock = "المظهر الذهبي الملوكي وكل الإطارات"; break;
            }
            next.add(perk("VIP " + n + " · " + titleAr(n), false, false, unlock));
            out.add(next);
        }

        return out;
    }

    private static Perk perk(String title, boolean unlocked, boolean included, String hint) {
        return new Perk(title, unlocked, included, hint);
    }

    public static final class Section {
        public final String title;
        public final String subtitle;
        public final List<Perk> perks = new ArrayList<>();

        public Section(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        void add(Perk p) {
            perks.add(p);
        }
    }

    public static final class Perk {
        public final String title;
        public final boolean unlocked;
        public final boolean includedWithRent;
        public final String hint;

        public Perk(String title, boolean unlocked, boolean includedWithRent, String hint) {
            this.title = title;
            this.unlocked = unlocked;
            this.includedWithRent = includedWithRent;
            this.hint = hint;
        }
    }
}
