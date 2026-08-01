package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public final class CasualGameDtos {
    private CasualGameDtos() {}

    public static class QueueRequest {
        @SerializedName("kind") public String kind;
        @SerializedName("roomId") public String roomId;
        public QueueRequest(String kind) { this(kind, null); }
        public QueueRequest(String kind, String roomId) {
            this.kind = kind;
            this.roomId = roomId;
        }
    }

    public static class ActionRequest {
        @SerializedName("action") public String action;
        @SerializedName("payload") public Map<String, Object> payload;
        public ActionRequest(String action, Map<String, Object> payload) {
            this.action = action;
            this.payload = payload;
        }
    }

    public static class PlayerDto {
        @SerializedName("id") public String id;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
    }

    public static class MatchDto {
        @SerializedName("id") public String id;
        @SerializedName("kind") public String kind;
        @SerializedName("status") public String status;
        @SerializedName("turn") public int turn;
        @SerializedName("state") public Map<String, Object> state;
        @SerializedName("winner") public String winner;
        @SerializedName("roomId") public String roomId;
        @SerializedName("player1Id") public String player1Id;
        @SerializedName("player2Id") public String player2Id;
        @SerializedName("player1") public PlayerDto player1;
        @SerializedName("player2") public PlayerDto player2;
    }

    public static class BossStatus {
        @SerializedName("date") public String date;
        @SerializedName("hp") public int hp;
        @SerializedName("maxHp") public int maxHp;
        @SerializedName("defeated") public boolean defeated;
        @SerializedName("winnerId") public String winnerId;
        @SerializedName("attacksUsed") public int attacksUsed;
        @SerializedName("attacksRemaining") public int attacksRemaining;
        @SerializedName("maxAttacks") public int maxAttacks;
        @SerializedName("rewardCoins") public int rewardCoins;
        @SerializedName("earnedCoins") public int earnedCoins;
        @SerializedName("damage") public int damage;
    }
}
