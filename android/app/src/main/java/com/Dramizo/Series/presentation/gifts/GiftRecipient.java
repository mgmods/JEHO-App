package com.Dramizo.Series.presentation.gifts;

import androidx.annotation.Nullable;

/** Mic guest or host available as a gift receiver in a voice room. */
public final class GiftRecipient {
    /** Sentinel used by the leading «الكل» chip in the gift strip. */
    public static final String ALL_MIC_ID = "__ALL_MIC__";

    public final String userId;
    public final String displayName;
    @Nullable public final String avatarUrl;
    @Nullable public final String hostBadgeUrl;
    @Nullable public final String vipBadgeUrl;
    /** 0-based seat index, or -1 when host is not seated / unknown. */
    public final int seatIndex;
    public final boolean host;
    /** Room gift support total for this seat (coins received in room session / board). */
    public final long supportCoins;
    /** Optional account level for chip display. */
    public final int userLevel;

    public GiftRecipient(String userId, String displayName,
                         @Nullable String avatarUrl, @Nullable String hostBadgeUrl,
                         int seatIndex, boolean host) {
        this(userId, displayName, avatarUrl, hostBadgeUrl, null, seatIndex, host, 0L, 0);
    }

    public GiftRecipient(String userId, String displayName,
                         @Nullable String avatarUrl, @Nullable String hostBadgeUrl,
                         @Nullable String vipBadgeUrl,
                         int seatIndex, boolean host) {
        this(userId, displayName, avatarUrl, hostBadgeUrl, vipBadgeUrl, seatIndex, host, 0L, 0);
    }

    public GiftRecipient(String userId, String displayName,
                         @Nullable String avatarUrl, @Nullable String hostBadgeUrl,
                         @Nullable String vipBadgeUrl,
                         int seatIndex, boolean host,
                         long supportCoins, int userLevel) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.hostBadgeUrl = hostBadgeUrl;
        this.vipBadgeUrl = vipBadgeUrl;
        this.seatIndex = seatIndex;
        this.host = host;
        this.supportCoins = Math.max(0L, supportCoins);
        this.userLevel = Math.max(0, userLevel);
    }

    public String seatLabel() {
        if (host && seatIndex <= 0) return "مضيف";
        if (seatIndex <= 0) return host ? "مضيف" : "";
        // Guest seats are 1-based in the grid (seatIndex 1 → "1").
        return String.valueOf(seatIndex);
    }

    public String supportLabel() {
        if (supportCoins <= 0L) return "0";
        if (supportCoins >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.1fM", supportCoins / 1_000_000d);
        }
        if (supportCoins >= 10_000L) {
            return String.format(java.util.Locale.US, "%.1fK", supportCoins / 1000d);
        }
        if (supportCoins >= 1000L) {
            return String.format(java.util.Locale.US, "%.1fK", supportCoins / 1000d);
        }
        return String.valueOf(supportCoins);
    }
}
