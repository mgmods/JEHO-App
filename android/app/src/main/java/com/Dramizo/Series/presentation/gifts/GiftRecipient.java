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
    /** 0-based seat index, or -1 when host is not seated. */
    public final int seatIndex;
    public final boolean host;

    public GiftRecipient(String userId, String displayName,
                         @Nullable String avatarUrl, @Nullable String hostBadgeUrl,
                         int seatIndex, boolean host) {
        this(userId, displayName, avatarUrl, hostBadgeUrl, null, seatIndex, host);
    }

    public GiftRecipient(String userId, String displayName,
                         @Nullable String avatarUrl, @Nullable String hostBadgeUrl,
                         @Nullable String vipBadgeUrl,
                         int seatIndex, boolean host) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.hostBadgeUrl = hostBadgeUrl;
        this.vipBadgeUrl = vipBadgeUrl;
        this.seatIndex = seatIndex;
        this.host = host;
    }

    public String seatLabel() {
        if (host || seatIndex <= 0) return host ? "المضيف" : "";
        // Match seat grid numbers (guest seatIndex 1 → "مقعد 1").
        return "مقعد " + seatIndex;
    }
}
