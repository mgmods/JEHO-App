package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.CasualGameDtos;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface CasualGameApi {
    @POST("casual-games/queue")
    Call<ApiResponse<CasualGameDtos.MatchDto>> queue(@Body CasualGameDtos.QueueRequest body);

    @DELETE("casual-games/queue")
    Call<ApiResponse<Map<String, Object>>> cancelQueue(@Query("kind") String kind);

    @GET("casual-games/{matchId}")
    Call<ApiResponse<CasualGameDtos.MatchDto>> get(@Path("matchId") String matchId);

    @GET("casual-games/boss/status")
    Call<ApiResponse<CasualGameDtos.BossStatus>> bossStatus();

    @POST("casual-games/boss/attack")
    Call<ApiResponse<CasualGameDtos.BossStatus>> attackBoss();

    @POST("casual-games/{matchId}/action")
    Call<ApiResponse<CasualGameDtos.MatchDto>> action(
            @Path("matchId") String matchId,
            @Body CasualGameDtos.ActionRequest body);

    @POST("casual-games/{matchId}/heartbeat")
    Call<ApiResponse<Map<String, Object>>> heartbeat(@Path("matchId") String matchId);

    @POST("casual-games/{matchId}/leave")
    Call<ApiResponse<CasualGameDtos.MatchDto>> leave(@Path("matchId") String matchId);

    @GET("casual-games/rooms/{roomId}/leaderboard")
    Call<ApiResponse<Map<String, Object>>> roomLeaderboard(@Path("roomId") String roomId);
}
