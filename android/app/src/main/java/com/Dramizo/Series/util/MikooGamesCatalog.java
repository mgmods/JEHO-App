package com.Dramizo.Series.util;

import android.content.Context;
import androidx.annotation.NonNull;
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
        g.playUrl = ApiOrigin.origin() + "/games/mikoo/" + id + "/index.html?v="
                + MediaAssetSync.FALLBACK_EPOCH;
        g.coverUrl = MediaAssetSync.mikooCoverUrl(id);
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
        out.add(slot("camel-racing", "سباق الأسود", "Camel Racing", 15));
        out.add(slot("cleopatra-slot", "كليوباترا", "Cleopatra Slot", 16));
        out.add(slot("cleopatra-slots", "فتحات كليوباترا", "Cleopatra Spins", 17));
        out.add(slot("crash", "كراش", "Crash", 18));
        out.add(slot("fishing", "صيد السمك", "Fishing", 19));
        out.add(slot("football-plinko", "بلينكو كرة القدم", "Football Plinko", 20));
        out.add(slot("fortune-slot", "جواهر الحظ", "Fortune Gems", 21));
        out.add(slot("greedy-box", "صندوق الطمع", "Greedy Box", 22));
        out.add(slot("greedy-lion", "الأسد الطماع", "Greedy Lion", 23));
        out.add(slot("hilo", "هاي لو", "Hilo", 24));
        out.add(slot("line-slots", "فتحات الخط", "Line Slots", 25));
        out.add(slot("luck-car", "سيارة الحظ", "Lucky Car", 26));
        out.add(slot("lucky77", "لاكي ٧٧", "Lucky 77", 27));
        out.add(slot("megaways-slots", "ميجاوايز", "Megaways Slots", 28));
        out.add(slot("olympians", "الأوليمبيون", "Olympians", 29));
        out.add(slot("pirate-king", "ملك القراصنة", "Pirate King", 30));
        out.add(slot("royal-battle", "المعركة الملكية", "Royal Battle", 31));
        out.add(slot("slot777", "سلوت ٧٧٧", "Slot 777", 32));
        out.add(slot("sugar-rush", "سكر راش", "Sugar Rush", 33));
        out.add(slot("swimsuit-party", "حفلة السباحة", "Swimsuit Party", 34));
        return out;
    }


    /**
     * Cocos design size from each packaged game (Canvas / scene assets).
     * The room dock letterboxes the stage to this aspect so the full UI is visible.
     */
    public static final class ViewportSpec {
        public final int designW;
        public final int designH;
        /** True when designW >= designH (e.g. fishing table). */
        public final boolean landscape;

        public ViewportSpec(int designW, int designH) {
            this.designW = Math.max(1, designW);
            this.designH = Math.max(1, designH);
            this.landscape = this.designW >= this.designH;
        }

        public float aspect() {
            return designW / (float) designH;
        }
    }

    private static final Map<String, ViewportSpec> VIEWPORTS = new LinkedHashMap<>();
    static {
        // Every default catalog game — sizes from package scene Canvas / splash assets.
        putVp("7updown", 750, 750);
        putVp("bounty-football", 750, 1334);
        putVp("camel-racing", 750, 1334);
        putVp("cleopatra-slot", 750, 1334);
        putVp("cleopatra-slots", 750, 1334);
        putVp("crash", 750, 1334);
        putVp("fishing", 1334, 750);           // landscape
        putVp("football-plinko", 750, 1334);
        putVp("fortune-slot", 750, 1200);
        putVp("greedy-box", 750, 1334);
        putVp("greedy-lion", 750, 1334);
        putVp("hilo", 750, 1334);
        putVp("line-slots", 750, 944);
        putVp("luck-car", 750, 1334);
        putVp("lucky77", 750, 1248);
        putVp("megaways-slots", 750, 1334);
        putVp("olympians", 750, 1334);
        putVp("pirate-king", 750, 898);
        putVp("royal-battle", 750, 1334);
        putVp("slot777", 750, 1334);
        putVp("sugar-rush", 750, 1334);
        putVp("swimsuit-party", 750, 1624);    // taller portrait
    }

    private static void putVp(String id, int w, int h) {
        VIEWPORTS.put(id.toLowerCase(Locale.US), new ViewportSpec(w, h));
    }

    /** Resolve from game id or play URL path (`/games/mikoo/<id>/`). */
    @NonNull
    public static ViewportSpec viewportFor(@Nullable String gameId) {
        return viewportFor(gameId, null);
    }

    @NonNull
    public static ViewportSpec viewportFor(@Nullable String gameId, @Nullable String playUrl) {
        String key = null;
        if (gameId != null && !gameId.trim().isEmpty()) {
            key = gameId.trim().toLowerCase(Locale.US);
        }
        if ((key == null || key.isEmpty()) && playUrl != null) {
            try {
                String path = playUrl.toLowerCase(Locale.US);
                int i = path.indexOf("/games/mikoo/");
                if (i >= 0) {
                    String rest = path.substring(i + "/games/mikoo/".length());
                    int slash = rest.indexOf('/');
                    key = slash > 0 ? rest.substring(0, slash) : rest;
                    int q = key.indexOf('?');
                    if (q > 0) key = key.substring(0, q);
                }
            } catch (Exception ignored) {
            }
        }
        if (key != null) {
            ViewportSpec s = VIEWPORTS.get(key);
            if (s != null) return s;
            if (key.contains("fish")) return new ViewportSpec(1334, 750);
            if (key.contains("cleopatra")) return new ViewportSpec(750, 1334);
            if (key.contains("swim")) return new ViewportSpec(750, 1624);
            if (key.contains("lucky77") || key.contains("lucky-77")) {
                return new ViewportSpec(750, 1248);
            }
            if (key.contains("plinko") || key.contains("football")) {
                return new ViewportSpec(750, 1334);
            }
            if (key.contains("pirate")) return new ViewportSpec(750, 898);
            if (key.contains("line")) return new ViewportSpec(750, 944);
            if (key.contains("fortune") || key.contains("gem")) {
                return new ViewportSpec(750, 1200);
            }
            if (key.contains("7up") || key.contains("updown")) {
                return new ViewportSpec(750, 750);
            }
        }
        return new ViewportSpec(750, 1334);
    }

    /** All known mikoo game ids (for completeness checks). */
    @NonNull
    public static Set<String> knownGameIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MiscDtos.GameDto g : defaultSlots()) {
            if (g != null && g.id != null) ids.add(g.id.toLowerCase(Locale.US));
        }
        ids.addAll(VIEWPORTS.keySet());
        return ids;
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
                    // Prefer live server cover; only package defaults when missing.
                    // Optimized CDNs ship .jpg thumbs — never treat JPG as “stale”.
                    String apiCover = override.coverUrl != null ? override.coverUrl.trim() : "";
                    if (apiCover.isEmpty()) {
                        override.coverUrl = def.coverUrl;
                    } else if (MediaAssetSync.isPackageDefaultCover(apiCover, id)) {
                        // Package art — rewrite with live epoch + jpg.
                        override.coverUrl = MediaAssetSync.mikooCoverUrl(id);
                    } else {
                        // Dashboard custom cover (/uploads/…, CDN, etc.)
                        override.coverUrl = MediaAssetSync.bust(apiCover);
                    }
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
                } else if (override.coverUrl != null) {
                    override.coverUrl = MediaAssetSync.bust(override.coverUrl);
                }
                out.add(override);
            } else if (def != null) {
                out.add(def);
            }
        }
        return out;
    }
}
