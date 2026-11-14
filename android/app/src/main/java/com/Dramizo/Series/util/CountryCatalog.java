package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Unified country codes for profile + home filter (ISO + Arabic/English + flag emoji). */
public final class CountryCatalog {
    public static final class Entry {
        public final String code;
        public final String nameAr;
        public final String nameEn;
        public final String flag;
        public Entry(String code, String nameAr, String nameEn, String flag) {
            this.code = code;
            this.nameAr = nameAr;
            this.nameEn = nameEn;
            this.flag = flag;
        }
        public String label() { return flag + "  " + nameAr; }
        public String chip() { return flag + " " + code; }
        /** Localized display name for tab chrome (AR vs EN based on app locale). */
        public String displayName() {
            String lang = Locale.getDefault().getLanguage();
            if (lang != null && lang.toLowerCase(Locale.US).startsWith("ar")) {
                return nameAr;
            }
            return nameEn != null && !nameEn.isEmpty() ? nameEn : nameAr;
        }
    }

    private static final Map<String, Entry> BY_CODE = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();

    static {
        add("SY", "سوريا", "Syria", "🇸🇾");
        add("TR", "تركيا", "Turkey", "🇹🇷");
        add("SA", "السعودية", "KSA", "🇸🇦");
        add("AE", "الإمارات", "UAE", "🇦🇪");
        add("EG", "مصر", "Egypt", "🇪🇬");
        add("JO", "الأردن", "Jordan", "🇯🇴");
        add("LB", "لبنان", "Lebanon", "🇱🇧");
        add("IQ", "العراق", "Iraq", "🇮🇶");
        add("KW", "الكويت", "Kuwait", "🇰🇼");
        add("QA", "قطر", "Qatar", "🇶🇦");
        add("BH", "البحرين", "Bahrain", "🇧🇭");
        add("OM", "عمان", "Oman", "🇴🇲");
        add("YE", "اليمن", "Yemen", "🇾🇪");
        add("PS", "فلسطين", "Palestine", "🇵🇸");
        add("LY", "ليبيا", "Libya", "🇱🇾");
        add("TN", "تونس", "Tunisia", "🇹🇳");
        add("DZ", "الجزائر", "Algeria", "🇩🇿");
        add("MA", "المغرب", "Morocco", "🇲🇦");
        add("SD", "السودان", "Sudan", "🇸🇩");
        add("MR", "موريتانيا", "Mauritania", "🇲🇷");
        add("DE", "ألمانيا", "Germany", "🇩🇪");
        add("FR", "فرنسا", "France", "🇫🇷");
        add("NL", "هولندا", "Netherlands", "🇳🇱");
        add("SE", "السويد", "Sweden", "🇸🇪");
        add("GB", "بريطانيا", "UK", "🇬🇧");
        add("US", "الولايات المتحدة", "USA", "🇺🇸");
        add("CA", "كندا", "Canada", "🇨🇦");
        add("OTHER", "أخرى", "Other", "🌍");
    }

    private static void add(String code, String ar, String en, String flag) {
        Entry e = new Entry(code, ar, en, flag);
        BY_CODE.put(code, e);
        BY_NAME.put(ar, e);
        if (en != null && !en.isEmpty()) BY_NAME.put(en, e);
    }

    private CountryCatalog() {}

    public static List<Entry> all() {
        return new ArrayList<>(BY_CODE.values());
    }

    public static String[] spinnerLabels() {
        List<Entry> all = all();
        String[] out = new String[all.size() + 1];
        out[0] = "اختر الدولة";
        for (int i = 0; i < all.size(); i++) out[i + 1] = all.get(i).label();
        return out;
    }

    public static String codeAtSpinnerIndex(int index) {
        if (index <= 0) return "";
        List<Entry> all = all();
        if (index > all.size()) return "";
        return all.get(index - 1).code;
    }

    public static int spinnerIndexFor(@Nullable String stored) {
        if (stored == null || stored.trim().isEmpty()) return 0;
        Entry e = resolve(stored.trim());
        if (e == null) return 0;
        int i = 1;
        for (Entry x : all()) {
            if (x.code.equals(e.code)) return i;
            i++;
        }
        return 0;
    }

    @Nullable
    public static Entry resolve(@Nullable String value) {
        if (value == null || value.isEmpty()) return null;
        String v = value.trim();
        Entry byCode = BY_CODE.get(v.toUpperCase(Locale.US));
        if (byCode != null) return byCode;
        Entry byName = BY_NAME.get(v);
        if (byName != null) return byName;
        // tolerate "SY · سوريا" style / common aliases
        String compact = v.replace("ة", "ه").replace("ى", "ي");
        for (Entry e : BY_CODE.values()) {
            if (v.contains(e.code) || v.contains(e.nameAr)) return e;
            String nameCompact = e.nameAr.replace("ة", "ه").replace("ى", "ي");
            if (compact.contains(nameCompact) || nameCompact.contains(compact)) return e;
        }
        return null;
    }

    /** Chip / list label with flag, e.g. "🇸🇾 سوريا". Falls back to raw text. */
    public static String labelWithFlag(@Nullable String country) {
        if (country == null || country.trim().isEmpty()) return "🌍 —";
        Entry e = resolve(country);
        if (e != null) return e.label();
        return "🌍  " + country.trim();
    }

    public static String flagOnly(@Nullable String country) {
        Entry e = resolve(country);
        return e != null ? e.flag : "🌍";
    }

    /** Official flag image URL (flagcdn), or null for OTHER / unknown. */
    @Nullable
    public static String flagImageUrl(@Nullable String country) {
        Entry e = resolve(country);
        if (e == null || "OTHER".equalsIgnoreCase(e.code)) return null;
        return "https://flagcdn.com/w40/" + e.code.toLowerCase(Locale.US) + ".png";
    }

    public static boolean matchesFilter(@Nullable String roomCountry, @Nullable String filterCode) {
        if (filterCode == null || filterCode.isEmpty() || "ALL".equalsIgnoreCase(filterCode)) return true;
        Entry room = resolve(roomCountry);
        if (room == null) return false;
        return room.code.equalsIgnoreCase(filterCode);
    }
}
