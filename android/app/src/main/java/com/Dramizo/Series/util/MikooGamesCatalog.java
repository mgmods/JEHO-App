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
        g.playUrl = ApiOrigin.origin() + "/games/mikoo/" + id + "/index.html";
        g.coverUrl = ApiOrigin.origin() + "/games/mikoo/covers/" + id + ".png?v=20260801a";
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
        if (arabic) {
            if (g.title != null && !g.title.trim().isEmpty()) return g.title.trim();
            if (g.titleEn != null && !g.titleEn.trim().isEmpty()) return g.titleEn.trim();
        } else {
            if (g.titleEn != null && !g.titleEn.trim().isEmpty()) return g.titleEn.trim();
            if (g.title != null && !g.title.trim().isEmpty()) return g.title.trim();
        }
        return "لعبة";
    }

    public static List<MiscDtos.GameDto> defaultSlots() {
        List<MiscDtos.GameDto> out = new ArrayList<>();
        out.add(slot("7updown", "7 فوق تحت", "7 Up Down", 10));
        out.add(slot("cleopatra-slot", "كليوباترا", "Cleopatra Slot", 11));
        out.add(slot("cleopatra-slots", "فتحات كليوباترا", "Cleopatra Slots", 12));
        out.add(slot("crash", "كراش", "Crash", 13));
        out.add(slot("fishing", "صيد السمك", "Fishing", 14));
        out.add(slot("football-plinko", "بلينكو كرة", "Football Plinko", 15));
        out.add(slot("fortune-slot", "جواهر الحظ", "Fortune Gems", 16));
        out.add(slot("greedy-box", "صندوق الجشع", "Greedy Box", 17));
        out.add(slot("hilo", "هاي لو", "Hilo", 18));
        out.add(slot("line-slots", "فتحات الخط", "Line Slots", 19));
        out.add(slot("luck-car", "سيارة الحظ", "Luck Car", 20));
        out.add(slot("lucky77", "لاكي 77", "Lucky 77", 21));
        out.add(slot("megaways-slots", "ميجا وايز", "Megaways Slots", 22));
        out.add(slot("olympians", "الأوليمبيون", "Olympians", 23));
        out.add(slot("pirate-king", "ملك القراصنة", "Pirate King", 24));
        out.add(slot("royal-battle", "المعركة الملكية", "Royal Battle", 25));
        out.add(slot("slot777", "سلوت", "Slot", 26));
        out.add(slot("sugar-rush", "سكر راش", "Sugar Rush", 27));
        out.add(slot("swimsuit-party", "حفلة السباحة", "Swimsuit Party", 28));
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
                if ((override.coverUrl == null || override.coverUrl.isEmpty()) && def != null) {
                    override.coverUrl = def.coverUrl;
                }
                if ((override.playUrl == null || override.playUrl.isEmpty()) && def != null) {
                    override.playUrl = def.playUrl;
                }
                if ((override.title == null || override.title.isEmpty()) && def != null) {
                    override.title = def.title;
                }
                if ((override.titleEn == null || override.titleEn.isEmpty()) && def != null) {
                    override.titleEn = def.titleEn;
                }
                out.add(override);
            } else if (def != null) {
                out.add(def);
            }
        }
        return out;
    }
}
