package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Resolves what media to play for a gift in the room.
 * <p>
 * Admin uploads win by type:
 * <ul>
 *   <li>static image (png/jpg/webp still) → show image</li>
 *   <li>animated gif / animated webp → play as gif</li>
 *   <li>video (mp4/webm/mov) / SVGA → play video</li>
 * </ul>
 * Mikoo entry remaps exist only as last-resort fallback when catalog still has
 * {@code runtime.html} and no uploaded media.
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
        // 1) Explicit animation from dashboard / API — never overwrite real uploads.
        String anim = firstRealMedia(animationUrl);
        if (anim != null) return anim;

        // 2) Icon itself can be the show media (gif / webp / video uploaded as icon).
        String icon = firstRealMedia(iconUrl);
        if (icon != null) {
            CosmeticMedia.Kind ik = CosmeticMedia.kind(icon);
            if (ik == CosmeticMedia.Kind.VIDEO
                    || ik == CosmeticMedia.Kind.SVGA
                    || ik == CosmeticMedia.Kind.GIF
                    || isTrustedUpload(icon)) {
                return icon;
            }
            // Static catalog PNG icon with no animation: keep still (no forced Mikoo MP4).
            if (ik == CosmeticMedia.Kind.IMAGE) return icon;
        }

        // 3) Legacy HTML placeholder only → optional Mikoo name/icon entry remap.
        if (isHtmlPlaceholder(animationUrl)) {
            String mapped = mapByName(giftName);
            if (mapped == null) mapped = mapByIcon(iconUrl);
            if (mapped != null) return mapped;
        }

        // 4) Sibling .mp4 next to icon only when not a fragile guess.
        if (iconUrl != null && !iconUrl.isEmpty()) {
            String sibling = iconUrl.replaceAll("(?i)\\.(png|jpe?g|webp)(\\?.*)?$", ".mp4$2");
            if (!sibling.equals(iconUrl)
                    && CosmeticMedia.kind(sibling) == CosmeticMedia.Kind.VIDEO
                    && isTrustedUpload(sibling)) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * CDN entry remap for a gift (name/icon) — used when the primary animation URL 404s.
     * Never use this to replace a successful admin upload.
     */
    @Nullable
    public static String resolveMappedFallback(
            @Nullable String giftName,
            @Nullable String iconUrl
    ) {
        // Only for true legacy blanks — if icon/anim already real media, no silent swap.
        if (firstRealMedia(iconUrl) != null) return null;
        String mapped = mapByName(giftName);
        if (mapped == null) mapped = mapByIcon(iconUrl);
        return mapped;
    }

    /** True uploaded /cdn media worth playing as-is (not HTML engine stubs). */
    @Nullable
    public static String firstRealMedia(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return null;
        if (isHtmlPlaceholder(url)) return null;
        String playable = CosmeticMedia.playableUrl(url.trim());
        if (playable == null) return null;
        CosmeticMedia.Kind k = CosmeticMedia.kind(playable);
        if (k == CosmeticMedia.Kind.NONE) return null;
        return playable;
    }

    public static boolean isHtmlPlaceholder(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String u = url.toLowerCase(Locale.US);
        return u.contains("runtime.html") || u.endsWith(".html") || u.endsWith(".htm");
    }

    /** Admin uploads and packaged JEHO gift assets. */
    public static boolean isTrustedUpload(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String u = url.toLowerCase(Locale.US);
        return u.contains("/uploads/")
                || u.contains("/assets/gifts/")
                || u.contains("/assets/pack/")
                || u.startsWith("http://")
                || u.startsWith("https://");
    }

    @Nullable
    private static String mapByIcon(@Nullable String iconUrl) {
        if (iconUrl == null || iconUrl.isEmpty()) return null;
        // Never remap admin uploads by path keywords.
        if (isTrustedUpload(iconUrl) && firstRealMedia(iconUrl) != null
                && !isHtmlPlaceholder(iconUrl)) {
            return null;
        }
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
            return entry("entry_mikoo_264_majestic_lion_king.mp4");
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
        return null;
    }

    @Nullable
    private static String mapByName(@Nullable String giftName) {
        if (giftName == null || giftName.isEmpty()) return null;
        String n = giftName.trim().toLowerCase(Locale.US);
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
        if (containsAny(n, "rocket", "صاروخ", "meteor", "نيزك")) {
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
