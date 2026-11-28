package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class RoomCupDtos {
    private RoomCupDtos() {}

    public static class Leaderboard {
        @SerializedName("enabled") public boolean enabled;
        @SerializedName("period") public String period;
        @SerializedName("seasonKey") public String seasonKey;
        @SerializedName("periodStart") public String periodStart;
        @SerializedName("periodEnd") public String periodEnd;
        @SerializedName("prizes") public List<Prize> prizes;
        @SerializedName("items") public List<Item> items;
    }

    public static class Prize {
        @SerializedName("rank") public int rank;
        @SerializedName("coins") public long coins;
        @SerializedName("diamonds") public long diamonds;
        @SerializedName("vipLevel") public int vipLevel;
        @SerializedName("vipDays") public int vipDays;
        @SerializedName("cosmeticId") public String cosmeticId;
        @SerializedName("cosmeticDays") public int cosmeticDays;
        @SerializedName("cosmeticName") public String cosmeticName;
        @SerializedName("cosmeticPreviewUrl") public String cosmeticPreviewUrl;
    }

    public static class Item {
        @SerializedName("rank") public int rank;
        @SerializedName("roomId") public String roomId;
        @SerializedName("score") public long score;
        @SerializedName("title") public String title;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("publicId") public String publicId;
        @SerializedName("isOfficial") public boolean isOfficial;
        @SerializedName("cupBadgeSeason") public String cupBadgeSeason;
        @SerializedName("hostName") public String hostName;
    }
}
