package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Maps gift names / icon paths to real playable MP4/SVGA when the catalog still
 * points at {@code /visual-system/runtime.html} (non-playable on Android).
 * Prefers Mikoo entry videos already shipped on the CDN.
 */
public final class GiftMediaResolver {
    private GiftMediaResolver() {}

    /**
     * @return absolute or site-relative media URL, or null if caller should keep original / sprite.
     */
    @Nullable
    public static String resolvePlayable(
            @Nullable String giftName,
            @Nullable String iconUrl,
            @Nullable String animationUrl
    ) {
        String mapped = mapByName(giftName);
        if (mapped == null) mapped = mapByIcon(iconUrl);

        String existing = CosmeticMedia.playableUrl(animationUrl);
        if (existing != null) {
            CosmeticMedia.Kind k = CosmeticMedia.kind(existing);
            if (k == CosmeticMedia.Kind.VIDEO || k == CosmeticMedia.Kind.SVGA) {
                // Catalog often stores sibling .mp4 next to gift PNGs that 404 on CDN.
                // Prefer known Mikoo entry videos when the catalog URL looks fragile.
                if (mapped != null && looksFragileGiftUrl(existing)) {
                    return mapped;
                }
                return existing;
            }
        }

        if (mapped != null) return mapped;
        if (iconUrl != null && !iconUrl.isEmpty()) {
            String sibling = iconUrl.replaceAll("(?i)\\.(png|jpe?g|webp)(\\?.*)?$", ".mp4$2");
            if (!sibling.equals(iconUrl) && CosmeticMedia.kind(sibling) == CosmeticMedia.Kind.VIDEO
                    && !looksFragileGiftUrl(sibling)) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * CDN entry remap for a gift (name/icon) — used when the primary animation URL 404s.
     */
    @Nullable
    public static String resolveMappedFallback(
            @Nullable String giftName,
            @Nullable String iconUrl
    ) {
        String mapped = mapByName(giftName);
        if (mapped == null) mapped = mapByIcon(iconUrl);
        return mapped;
    }

    /** Gift-sprite / visual-system paths that frequently 404 when used as MP4. */
    private static boolean looksFragileGiftUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.toLowerCase(Locale.US);
        return u.contains("/gifts/")
                || u.contains("gift-")
                || u.contains("gift_")
                || u.contains("visual-system")
                || (u.contains("/anims/") && !u.contains("/cosmetics/entries/"));
    }

    @Nullable
    private static String mapByIcon(@Nullable String iconUrl) {
        if (iconUrl == null || iconUrl.isEmpty()) return null;
        String u = iconUrl.toLowerCase(Locale.US);
        if (u.contains("lion") || u.contains("اسد") || u.contains("أسد")) {
            return entry("entry_mikoo_247_golden_lion_roar.mp4");
        }
        if (u.contains("car") || u.contains("سيارة")) {
            return entry("entry_mikoo_265_luxury_car_team.mp4");
        }
        if (u.contains("plane") || u.contains("طائرة") || u.contains("airplane")) {
            return entry("entry_mikoo_179_dubai_golden_airplane.mp4");
        }
        if (u.contains("ball") || u.contains("كرة") || u.contains("football")) {
            return entry("entry_mikoo_190_goal.mp4");
        }
        if (u.contains("crown") || u.contains("تاج") || u.contains("royal")) {
            return entry("entry_mikoo_268_royal_family.mp4");
        }
        if (u.contains("dragon") || u.contains("تنين")) {
            return entry("entry_mikoo_264_majestic_lion_king.mp4"); // closest cinematic until dedicated dragon gift mp4
        }
        if (u.contains("yacht") || u.contains("يخت") || u.contains("train") || u.contains("قطار")) {
            return entry("entry_mikoo_265_luxury_car_team.mp4");
        }
        if (u.contains("rocket") || u.contains("صاروخ") || u.contains("meteor") || u.contains("نيزك")) {
            return entry("entry_mikoo_177_glory_kick.mp4");
        }
        if (u.contains("fireworks") || u.contains("ألعاب") || u.contains("galaxy")
                || u.contains("مجرة") || u.contains("champagne") || u.contains("gift-champagne")) {
            return entry("entry_mikoo_267_winning_the_championship.mp4");
        }
        if (u.contains("planet") || u.contains("saturn") || u.contains("earth") || u.contains("كوكب")) {
            return entry("entry_mikoo_267_winning_the_championship.mp4");
        }
        if (u.contains("mikoo_gift_cache") || u.contains("/anims/")) {
            return null; // already special-cased via animationUrl
        }
        return null;
    }

    @Nullable
    private static String mapByName(@Nullable String giftName) {
        if (giftName == null || giftName.isEmpty()) return null;
        String n = giftName.trim().toLowerCase(Locale.US);
        // Arabic + English aliases
        if (containsAny(n, "lion", "أسد", "اسد", "ليث")) {
            return entry("entry_mikoo_247_golden_lion_roar.mp4");
        }
        if (containsAny(n, "tiger", "نمر")) {
            return entry("entry_mikoo_256_lightning_lion.mp4");
        }
        if (containsAny(n, "wolf", "ذئب")) {
            return entry("entry_mikoo_264_majestic_lion_king.mp4");
        }
        if (containsAny(n, "car", "سيارة", "سياره")) {
            return entry("entry_mikoo_265_luxury_car_team.mp4");
        }
        if (containsAny(n, "plane", "طائرة", "طيارة", "طائره")) {
            return entry("entry_mikoo_179_dubai_golden_airplane.mp4");
        }
        if (containsAny(n, "ball", "كرة", "كره", "football")) {
            return entry("entry_mikoo_190_goal.mp4");
        }
        if (containsAny(n, "crown", "تاج", "عرش", "throne")) {
            return entry("entry_mikoo_268_royal_family.mp4");
        }
        if (containsAny(n, "dragon", "تنين", "phoenix", "عنقاء")) {
            return entry("entry_mikoo_264_majestic_lion_king.mp4");
        }
        if (containsAny(n, "yacht", "يخت", "train", "قطار", "bike", "دراجة")) {
            return entry("entry_mikoo_265_luxury_car_team.mp4");
        }
        if (containsAny(n, "rocket", "صاروخ", "meteor", "نيزك", "plane")) {
            return entry("entry_mikoo_177_glory_kick.mp4");
        }
        if (containsAny(n, "fireworks", "ألعاب نارية", "galaxy", "مجرة", "champagne", "شامبانيا",
                "احتفال", "فاخر")) {
            return entry("entry_mikoo_267_winning_the_championship.mp4");
        }
        if (containsAny(n, "planet", "كوكب", "saturn", "earth")) {
            return entry("entry_mikoo_267_winning_the_championship.mp4");
        }
        if (containsAny(n, "castle", "قلعة", "diamond", "ألماس", "ring", "خاتم")) {
            return entry("entry_mikoo_268_royal_family.mp4");
        }
        if (containsAny(n, "heart", "قلب", "rose", "وردة", "teddy", "دب")) {
            return entry("entry_mikoo_260_happy_football.mp4");
        }
        if (containsAny(n, "egg", "بيضة", "بيضه", "icecream", "آيس", "ايس")) {
            return entry("entry_mikoo_260_happy_football.mp4");
        }
        if (containsAny(n, "guitar", "غيتار", "microphone", "ميكروفون", "piano", "بيانو", "drums", "طبول")) {
            return entry("entry_mikoo_208_shoter_wealth_top3.mp4");
        }
        if (containsAny(n, "unicorn", "يونيكورن", "elephant", "فيل", "eagle", "نسر", "falcon", "صقر")) {
            return entry("entry_mikoo_264_majestic_lion_king.mp4");
        }
        // No dedicated video yet — keep sprite/icon path for donkey/cow/ass.
        if (containsAny(n, "donkey", "حمار", "جحش", "cow", "بقرة", "بقره", "ass")) {
            return null;
        }
        return null;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (n != null && !n.isEmpty() && hay.contains(n.toLowerCase(Locale.US))) return true;
            if (n != null && hay.contains(n)) return true;
        }
        return false;
    }

    private static String entry(String fileName) {
        return "/assets/cosmetics/entries/" + fileName;
    }
}
