package com.Dramizo.Series.data.remote.dto;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public final class GameDtos {
    private GameDtos() {}

    public static class RoomGameDto {
        @SerializedName("id") public String id;
        @SerializedName("roomId") public String roomId;
        @SerializedName("type") public String type;
        @SerializedName("status") public String status;
        @SerializedName("playerXId") public String playerXId;
        @SerializedName("playerOId") public String playerOId;
        @SerializedName("playerXName") public String playerXName;
        @SerializedName("playerOName") public String playerOName;
        @SerializedName("board") public List<Integer> board;
        @SerializedName("turn") public String turn;
        @SerializedName("winner") public String winner;
        @SerializedName("action") public String action;
        @SerializedName("htmlGameKey") public String htmlGameKey;
        @SerializedName("stateJson") public JsonElement stateJson;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("updatedAt") public String updatedAt;
    }

    public static class StartGameRequest {
        @SerializedName("type") public String type;
        @SerializedName("opponentId") public String opponentId;
        @SerializedName("htmlGameKey") public String htmlGameKey;

        public StartGameRequest(String type) {
            this.type = type;
        }

        public StartGameRequest(String type, String opponentId) {
            this.type = type;
            this.opponentId = opponentId;
        }

        public StartGameRequest(String type, String opponentId, String htmlGameKey) {
            this.type = type;
            this.opponentId = opponentId;
            this.htmlGameKey = htmlGameKey;
        }
    }

    public static class MoveRequest {
        @SerializedName("cell") public int cell;

        public MoveRequest(int cell) {
            this.cell = cell;
        }
    }

    public static class StateRequest {
        @SerializedName("state") public Map<String, Object> state;

        public StateRequest(Map<String, Object> state) {
            this.state = state;
        }
    }
}
