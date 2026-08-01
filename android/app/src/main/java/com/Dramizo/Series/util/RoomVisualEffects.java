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
        String giftId = match(GIFT_ID, iconUrl);
        if (giftId == null) giftId = match(GIFT_ID, animationUrl);
        if (giftId == null) giftId = match(GIFT_ID, giftName);
        if (giftId == null) giftId = slugGiftName(giftName);
        if (giftId == null) return false;
        return effectsView.showGift(
                giftId.toLowerCase(Locale.US),
                iconUrl,
                animationUrl,
                senderName,
                Math.max(1, quantity),
                quantity > 1 ? 5200 : 4800,
                null);
    }

    /** Backward-compatible overload. */
    public boolean showGift(@Nullable String giftName, @Nullable String iconUrl,
                            @Nullable String senderName, int quantity) {
        return showGift(giftName, iconUrl, null, senderName, quantity);
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
        if (n.contains("lion") || n.contains("أسد")) return "lion";
        if (n.contains("tiger") || n.contains("نمر")) return "royal-tiger";
        if (n.contains("wolf") || n.contains("ذئب")) return "dire-wolf";
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
