package com.Dramizo.Series.util;

import android.content.Context;
import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.MiscDtos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local fallback when config API is unavailable or outdated. */
public final class MikooGamesCatalog {
    private MikooGamesCatalog() {}

    private static MiscDtos.GameDto slot(String id, String titleAr, String titleEn, int sortOrder) {
        MiscDtos.GameDto g = new MiscDtos.GameDto();
        g.id = id;
        g.title = titleAr;
        g.titleEn = titleEn;
        g.mode = "mikoo_slot";
        g.sortOrder = sortOrder;
        g.enabled = Boolean.TRUE;
        g.playUrl = ApiOrigin.origin() + "/games/mikoo/" + id + "/index.html?v=20260802g";
        g.coverUrl = ApiOrigin.origin() + "/games/mikoo/covers/" + id + ".png?v=20260805bf";
        return g;
    }

    /** Prefer Arabic title when app locale is AR; otherwise English. */
    public static String displayTitle(@Nullable Context context, @Nullable MiscDtos.GameDto g) {
        if (g == null) return "لعبة";
        boolean arabic = false;
        if (context != null) {
            Locale loc = context.getResources().getConfiguration().getLocales().get(0);
            arabic = loc != null && "ar".equalsIgnoreCase(loc.getLanguage());
        }
        MiscDtos.GameDto def = null;
        if (g.id != null) {
            for (MiscDtos.GameDto d : defaultSlots()) {
                if (g.id.equalsIgnoreCase(d.id)) {
                    def = d;
                    break;
                }
            }
        }
        String ar = firstNonEmpty(g.title, def != null ? def.title : null);
        String en = firstNonEmpty(g.titleEn, def != null ? def.titleEn : null, g.title);
        // Server/dashboard sometimes store English in `title` only — use local AR when needed.
        if (arabic && !hasArabic(ar) && def != null && hasArabic(def.title)) {
            ar = def.title;
        }
        if (arabic) {
            if (hasArabic(ar)) return ar;
            if (ar != null && !ar.isEmpty()) return ar;
            if (en != null && !en.isEmpty()) return en;
        } else {
            if (en != null && !en.isEmpty()) return en;
            if (ar != null && !ar.isEmpty()) return ar;
        }
        return "لعبة";
    }

    private static boolean hasArabic(@Nullable String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    public static List<MiscDtos.GameDto> defaultSlots() {
        List<MiscDtos.GameDto> out = new ArrayList<>();
        out.add(slot("7updown", "٧ فوق تحت", "7 Up Down", 10));
        out.add(slot("bounty-football", "كرة القدم الجوائز", "Bounty Football", 14));
        out.add(slot("cleopatra-slot", "كليوباترا", "Cleopatra Slot", 15));
        out.add(slot("cleopatra-slots", "فتحات كليوباترا", "Cleopatra Spins", 16));
        out.add(slot("crash", "كراش", "Crash", 17));
        out.add(slot("fishing", "صيد السمك", "Fishing", 18));
        out.add(slot("football-plinko", "بلينكو كرة القدم", "Football Plinko", 19));
        out.add(slot("fortune-slot", "جواهر الحظ", "Fortune Gems", 20));
        out.add(slot("greedy-box", "صندوق الطمع", "Greedy Box", 21));
        out.add(slot("hilo", "هاي لو", "Hilo", 22));
        out.add(slot("line-slots", "فتحات الخط", "Line Slots", 23));
        out.add(slot("luck-car", "سيارة الحظ", "Lucky Car", 24));
        out.add(slot("lucky77", "لاكي ٧٧", "Lucky 77", 25));
        out.add(slot("megaways-slots", "ميجاوايز", "Megaways Slots", 26));
        out.add(slot("olympians", "الأوليمبيون", "Olympians", 27));
        out.add(slot("pirate-king", "ملك القراصنة", "Pirate King", 28));
        out.add(slot("royal-battle", "المعركة الملكية", "Royal Battle", 29));
        out.add(slot("slot777", "سلوت ٧٧٧", "Slot 777", 30));
        out.add(slot("sugar-rush", "سكر راش", "Sugar Rush", 31));
        out.add(slot("swimsuit-party", "حفلة السباحة", "Swimsuit Party", 32));
        return out;
    }

    public static boolean isBlocked(MiscDtos.GameDto g) {
        if (g == null) return true;
        if (Boolean.FALSE.equals(g.enabled)) return true;
        String id = g.id != null ? g.id.toLowerCase(Locale.US) : "";
        String playUrl = g.playUrl != null ? g.playUrl.toLowerCase(Locale.US) : "";
        if (id.contains("fireforce") || playUrl.contains("fireforce")) return true;
        if (id.contains("wheel") || id.equals("dice") || id.equals("xo")
                || id.contains("tic_tac") || id.contains("tictactoe")
                || playUrl.contains("lucky-wheel")
                || playUrl.contains("dice.html")
                || playUrl.contains("tic_tac")) {
            return true;
        }
        return id.contains("ono") || id.contains("domino") || id.contains("ludo")
                || playUrl.contains("ono.html") || playUrl.contains("domino.html")
                || playUrl.contains("ludo.html");
    }

    public static boolean isMikooEntry(MiscDtos.GameDto g) {
        if (g == null) return false;
        return "mikoo_slot".equalsIgnoreCase(g.mode)
                || MikooGameBridge.isMikooSlot(g.mode, g.playUrl);
    }

    /**
     * When API returns a mikoo list, only those games are shown (hide/delete from dashboard).
     * Offline / empty API falls back to the full default catalog.
     */
    public static List<MiscDtos.GameDto> mergeSlots(@Nullable List<MiscDtos.GameDto> api) {
        Map<String, MiscDtos.GameDto> defaults = new LinkedHashMap<>();
        for (MiscDtos.GameDto def : defaultSlots()) {
            defaults.put(def.id.toLowerCase(Locale.US), def);
        }
        if (api == null) {
            return new ArrayList<>(defaults.values());
        }

        Map<String, MiscDtos.GameDto> apiById = new LinkedHashMap<>();
        Set<String> apiOrder = new LinkedHashSet<>();
        boolean anyMikoo = false;
        for (MiscDtos.GameDto g : api) {
            if (g == null || isBlocked(g) || !isMikooEntry(g) || g.id == null) continue;
            anyMikoo = true;
            String id = g.id.toLowerCase(Locale.US);
            apiOrder.add(id);
            apiById.put(id, g);
        }
        if (!anyMikoo) {
            return new ArrayList<>(defaults.values());
        }

        List<MiscDtos.GameDto> out = new ArrayList<>();
        for (String id : apiOrder) {
            MiscDtos.GameDto override = apiById.get(id);
            MiscDtos.GameDto def = defaults.get(id);
            if (override != null) {
                if (def != null) {
                    // Always serve package cover + AR/EN from local defaults when API is English-only.
                    override.coverUrl = def.coverUrl;
                    if (override.playUrl == null || override.playUrl.isEmpty()) {
                        override.playUrl = def.playUrl;
                    }
                    if (override.titleEn == null || override.titleEn.trim().isEmpty()) {
                        override.titleEn = def.titleEn;
                    }
                    if (override.title == null || override.title.trim().isEmpty()
                            || !hasArabic(override.title)) {
                        override.title = def.title;
                    }
                }
                out.add(override);
            } else if (def != null) {
                out.add(def);
            }
        }
        return out;
    }
}
