package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public final class SlotGameDtos {
    private SlotGameDtos() {}

    public static class StartRequest {
        @SerializedName("gameId") public String gameId;
        @SerializedName("roomId") public String roomId;
    }

    public static class SessionDto {
        @SerializedName("sessionId") public String sessionId;
        @SerializedName("gameId") public String gameId;
        @SerializedName("bridge") public String bridge;
        @SerializedName("code") public String code;
        @SerializedName("userId") public String userId;
        @SerializedName("roomId") public String roomId;
        @SerializedName("appId") public long appId;
        @SerializedName("gsp") public int gsp;
        @SerializedName("appChannel") public String appChannel;
        @SerializedName("language") public String language;
        @SerializedName("gameMode") public String gameMode;
        @SerializedName("currencyIcon") public String currencyIcon;
        @SerializedName("gameType") public int gameType;
        @SerializedName("containerUrl") public String containerUrl;
        /** HTTP game_route base for BaiShun DOMAIN (not the WSS URL). */
        @SerializedName("routeUrl") public String routeUrl;
        @SerializedName("hashGameConfig") public String hashGameConfig;
        @SerializedName("balance") public long balance;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
    }

    public static class ActivePlayerDto {
        @SerializedName("sessionId") public String sessionId;
        @SerializedName("userId") public String userId;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("gameId") public String gameId;
        @SerializedName("totalWinCoins") public int totalWinCoins;
        @SerializedName("spinCount") public int spinCount;
    }

    public static class LeaderboardEntryDto {
        @SerializedName("userId") public String userId;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("totalWinCoins") public int totalWinCoins;
        @SerializedName("spinCount") public int spinCount;
    }
}
