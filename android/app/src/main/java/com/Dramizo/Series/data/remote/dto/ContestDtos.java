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
        @SerializedName("kind") public String kind;
        @SerializedName("bannerUrl") public String bannerUrl;
        @SerializedName("goalDiamonds") public long goalDiamonds;
        @SerializedName("scoringMode") public String scoringMode;
        @SerializedName("challengeType") public String challengeType;
        @SerializedName("isInteractive") public boolean isInteractive;
        @SerializedName("sideA") public SideDto sideA;
        @SerializedName("sideB") public SideDto sideB;
        @SerializedName("milestones") public List<MilestoneDto> milestones;
        @SerializedName("prizes") public List<PrizeDto> prizes;
    }

    public static class SideDto {
        @SerializedName("name") public String name;
        @SerializedName("logoUrl") public String logoUrl;
        @SerializedName("score") public long score;
        @SerializedName("roomIds") public List<String> roomIds;
        @SerializedName("agencyIds") public List<String> agencyIds;
    }

    public static class MilestoneDto {
        @SerializedName("goal") public long goal;
        @SerializedName("title") public String title;
        @SerializedName("prize") public String prize;
    }

    public static class PrizeDto {
        @SerializedName("rank") public int rank;
        @SerializedName("coins") public long coins;
        @SerializedName("diamonds") public long diamonds;
        @SerializedName("label") public String label;
        @SerializedName("minimumScore") public long minimumScore;
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
