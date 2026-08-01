package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.SlotGameDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SlotGameApi {
    @POST("games/slot/session")
    Call<ApiResponse<SlotGameDtos.SessionDto>> startSession(@Body SlotGameDtos.StartRequest body);

    @POST("games/slot/session/{sessionId}/heartbeat")
    Call<ApiResponse<Map<String, Object>>> heartbeat(@Path("sessionId") String sessionId);

    @POST("games/slot/session/{sessionId}/end")
    Call<ApiResponse<Map<String, Object>>> endSession(@Path("sessionId") String sessionId);

    @GET("games/slot/rooms/{roomId}/active")
    Call<ApiResponse<List<SlotGameDtos.ActivePlayerDto>>> activeInRoom(@Path("roomId") String roomId);

    @GET("games/slot/rooms/{roomId}/leaderboard")
    Call<ApiResponse<List<SlotGameDtos.LeaderboardEntryDto>>> roomLeaderboard(@Path("roomId") String roomId);
}
