package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class AdvertisementDtos {
    private AdvertisementDtos() {}

    public static class Config {
        public boolean enabled;
        public boolean sendButtonEnabled;
        public int maxTextLength;
        public Ticker ticker;
    }

    public static class Ticker {
        public boolean enabled;
        public boolean sendButtonEnabled;
        public int dailyLimit;
        public int maxTextLength;
        public int displayDurationMs;
        public int gapMs;
        public int entrySpeedPxPerSec;
        public int exitSpeedPxPerSec;
        public boolean reducedMotionStatic;
        public String template;
        public String position;
        public String direction;
        public String transitionType;
        public String fontFamily;
        public String background;
        public String backgroundImage;
        public int backgroundOpacity;
        public int radiusDp;
        public int heightDp;
        public int avatarDp;
        public String avatarShape;
        public int nameSp;
        public int textSp;
        public String nameColor;
        public String textColor;
        public boolean nameGradientEnabled;
        public String nameGradientStartColor;
        public String nameGradientEndColor;
        public boolean textGradientEnabled;
        public String textGradientStartColor;
        public String textGradientEndColor;
        public String customFontUrl;
        public String customFontName;
        public int spacingDp;
        public int customOffsetDp;
        public int avatarOffsetXDp;
        public int avatarOffsetYDp;
        public int nameOffsetXDp;
        public int nameOffsetYDp;
        public int nameWidthDp;
        public int textOffsetXDp;
        public int textOffsetYDp;
        public int textWidthDp;
        public int positionXPercent;
        public int positionYPercent;
    }

    public static class Remaining {
        public int limit;
        public int used;
        public int remaining;
    }

    public static class Advertisement {
        public String id;
        public String senderId;
        public String senderName;
        public String senderImageUrl;
        public String text;
        public String status;
        public long queueOrder;
        public String createdAt;
        public String startedAt;
        public String completedAt;
        public int displayDurationMs;
    }

    public static class CreateResponse {
        public boolean duplicate;
        public Advertisement advertisement;
        public Remaining remaining;
    }

    public static class Current {
        public Advertisement advertisement;
        public Ticker config;
        public String serverNow;
    }

    public static class RealtimeEvent {
        public String type;
        public Advertisement advertisement;
        public Ticker config;
        public String at;
        public String serverNow;
        public boolean recovered;
    }
}