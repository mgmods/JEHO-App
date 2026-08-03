package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compatibility facade over the Android-native room visual-effects view. */
public final class RoomVisualEffects {
    private static final Pattern GIFT_ID =
            Pattern.compile("(?:^|/)(?:gift-)?([a-z0-9-]+)\\.(?:png|webp|jpe?g|gif|mp4|webm|mov|json)(?:\\?.*)?$",
                    Pattern.CASE_INSENSITIVE);

    private final NativeRoomEffectsView effectsView;

    public RoomVisualEffects(NativeRoomEffectsView effectsView) {
        this.effectsView = effectsView;
    }

    public boolean showGift(@Nullable String giftName, @Nullable String iconUrl,
                            @Nullable String animationUrl, @Nullable String senderName, int quantity) {
        return showGift(giftName, iconUrl, animationUrl, senderName, quantity, null);
    }

    public boolean showGift(@Nullable String giftName, @Nullable String iconUrl,
                            @Nullable String animationUrl, @Nullable String senderName, int quantity,
                            @Nullable Runnable onComplete) {
        String giftId = resolveGiftId(giftName, iconUrl, animationUrl);
        if (giftId == null) return false;
        boolean media = CosmeticMedia.playableUrl(animationUrl) != null;
        long hold = media
                ? (quantity > 1 ? 8200 : 7500)
                : (quantity > 1 ? 5200 : 4800);
        return effectsView.showGift(
                giftId.toLowerCase(Locale.US),
                giftName,
                iconUrl,
                animationUrl,
                senderName,
                Math.max(1, quantity),
                hold,
                onComplete);
    }

    /** Backward-compatible overload. */
    public boolean showGift(@Nullable String giftName, @Nullable String iconUrl,
                            @Nullable String senderName, int quantity) {
        return showGift(giftName, iconUrl, null, senderName, quantity, null);
    }

    /** Prefer human gift name / catalog slug — never use cache filenames as title ids. */
    @Nullable
    private static String resolveGiftId(@Nullable String giftName, @Nullable String iconUrl,
                                        @Nullable String animationUrl) {
        String fromName = slugGiftName(giftName);
        if (isCleanAssetId(fromName)) return fromName;

        String fromIcon = match(GIFT_ID, iconUrl);
        if (isCleanAssetId(fromIcon)) return fromIcon;

        String fromAnim = match(GIFT_ID, animationUrl);
        if (isCleanAssetId(fromAnim)) return fromAnim;

        if (fromName != null && !fromName.isEmpty() && !looksLikeGarbageId(fromName)) {
            return fromName;
        }
        // Keep playback even for custom uploads — never surface cache ids as the gift title.
        return "custom";
    }

    private static boolean looksLikeGarbageId(@Nullable String id) {
        if (id == null || id.isEmpty()) return true;
        String s = id.toLowerCase(Locale.US);
        if (s.length() > 32) return true;
        if (s.contains("cache") || s.contains("mikoo_gift") || s.contains("gift_cache")) return true;
        return s.matches(".*\\d{10,}.*");
    }

    private static boolean isCleanAssetId(@Nullable String id) {
        if (id == null || id.isEmpty()) return false;
        if (looksLikeGarbageId(id)) return false;
        return id.matches("[a-z0-9-]{2,40}");
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String entryEffectUrl, long durationMs) {
        return showEntry(displayName, avatarUrl, entryEffectUrl, durationMs,
                0, 1, 0L, null, null, null, false, false, null);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String entryEffectUrl, long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost) {
        return showEntry(displayName, avatarUrl, entryEffectUrl, durationMs,
                vipLevel, userLevel, wealthScore, vipBadgeUrl, levelBadgeUrl,
                hostBadgeUrl, isHost, false, null);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String entryEffectUrl, long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost,
                             @Nullable Runnable onComplete) {
        return showEntry(displayName, avatarUrl, entryEffectUrl, durationMs,
                vipLevel, userLevel, wealthScore, vipBadgeUrl, levelBadgeUrl,
                hostBadgeUrl, isHost, false, onComplete);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String entryEffectUrl, long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost, boolean showHiBadge,
                             @Nullable Runnable onComplete) {
        // Mikoo-like: VIP/level picks toast accent; equipped ride media plays full-screen from server.
        String entry = defaultVariantForVip(vipLevel);
        String media = CosmeticMedia.playableUrl(entryEffectUrl);
        boolean hasRide = media != null;
        long playDuration = hasRide
                ? (durationMs > 0 ? Math.max(3_500L, Math.min(8_000L, durationMs)) : 5_500L)
                : (durationMs > 0
                ? Math.max(2_200L, Math.min(3_200L, durationMs))
                : 2_800L);
        return effectsView.showEntry(
                displayName,
                avatarUrl,
                entry,
                hostBadgeUrl,
                media,
                playDuration,
                Math.max(0, vipLevel),
                Math.max(1, userLevel),
                Math.max(0L, wealthScore),
                vipBadgeUrl,
                levelBadgeUrl,
                hostBadgeUrl,
                isHost,
                showHiBadge,
                onComplete);
    }

    private static String defaultVariantForVip(int vipLevel) {
        if (vipLevel >= 9) return "legend";
        if (vipLevel >= 6) return "diamond";
        if (vipLevel >= 3) return "gold";
        if (vipLevel >= 1) return "vip";
        return "normal";
    }

    public void showSlotWinBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                   long winCoins, @Nullable String gameTitle) {
        showSlotWinBubble(displayName, avatarUrl, winCoins, gameTitle, null);
    }

    public void showSlotWinBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                   long winCoins, @Nullable String gameTitle,
                                   @Nullable String gameIconUrl) {
        effectsView.showSlotWinBubble(displayName, avatarUrl, winCoins, gameTitle, gameIconUrl);
    }

    public void showSlotLoseBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                    long betCoins, @Nullable String gameTitle) {
        showSlotLoseBubble(displayName, avatarUrl, betCoins, gameTitle, null);
    }

    public void showSlotLoseBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                    long betCoins, @Nullable String gameTitle,
                                    @Nullable String gameIconUrl) {
        effectsView.showSlotLoseBubble(displayName, avatarUrl, betCoins, gameTitle, gameIconUrl);
    }

    public void showRoomEventBubble(
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String message,
            @Nullable String badgeUrl
    ) {
        effectsView.showRoomEventBubble(displayName, avatarUrl, message, badgeUrl);
    }

    public void stopAll() {
        effectsView.stopAll();
    }

    public void destroy() {
        effectsView.destroy();
    }

    @Nullable
    private static String match(Pattern pattern, @Nullable String value) {
        if (value == null || value.isEmpty()) return null;
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Nullable
    private static String slugGiftName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        String n = name.trim().toLowerCase(Locale.US)
                .replace(' ', '-')
                .replace('_', '-');
        if (n.contains("lion") || n.contains("أسد") || n.contains("اسد")) return "lion";
        if (n.contains("tiger") || n.contains("نمر")) return "royal-tiger";
        if (n.contains("wolf") || n.contains("ذئب")) return "dire-wolf";
        if (n.contains("donkey") || n.contains("حمار") || n.contains("جحش")) return "donkey";
        if (n.contains("cow") || n.contains("بقرة") || n.contains("بقره")) return "cow";
        if (n.contains("ass") || n.contains("حمير")) return "donkey";
        if (n.contains("car") || n.contains("سيارة")) return "car";
        if (n.contains("dragon") || n.contains("تنين")) return "dragon";
        if (n.contains("rocket") || n.contains("صاروخ")) return "rocket";
        if (n.contains("plane") || n.contains("طائرة")) return "plane";
        if (n.contains("yacht") || n.contains("يخت")) return "yacht";
        if (n.contains("train") || n.contains("قطار")) return "royal-train";
        if (n.contains("elephant") || n.contains("فيل")) return "royal-elephant";
        if (n.contains("eagle") || n.contains("نسر")) return "royal-eagle";
        if (n.contains("falcon") || n.contains("صقر")) return "golden-falcon";
        if (n.contains("phoenix") || n.contains("عنقاء")) return "phoenix";
        if (n.contains("unicorn") || n.contains("يونيكورن")) return "unicorn";
        return n.replaceAll("[^a-z0-9-]", "");
    }
}
