package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class ContestDtos {
    private ContestDtos() {}

    public static class ContestDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("category") public String category;
        @SerializedName("status") public String status;
        @SerializedName("entryFeeCoins") public int entryFeeCoins;
        @SerializedName("prizeCoins") public int prizeCoins;
        @SerializedName("prizeLabel") public String prizeLabel;
        @SerializedName("startAt") public String startAt;
        @SerializedName("endAt") public String endAt;
        @SerializedName("entrantsCount") public int entrantsCount;
        @SerializedName("joined") public boolean joined;
        @SerializedName("scope") public String scope;
        @SerializedName("roomId") public String roomId;
        @SerializedName("agencyId") public String agencyId;
    }

    public static class ContestList {
        @SerializedName("items") public List<ContestDto> items;
    }

    public static class LeaderRow {
        @SerializedName("rank") public int rank;
        @SerializedName("userId") public String userId;
        @SerializedName("score") public long score;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("hostBadgeUrl") public String hostBadgeUrl;
    }

    public static class ContestDetail {
        @SerializedName("contest") public ContestDto contest;
        @SerializedName("leaderboard") public List<LeaderRow> leaderboard;
    }
}
