package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.GameDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface GameApi {
    @POST("rooms/{roomId}/games")
    Call<ApiResponse<GameDtos.RoomGameDto>> start(
            @Path("roomId") String roomId,
            @Body GameDtos.StartGameRequest body);

    @POST("rooms/{roomId}/games/{gameId}/join")
    Call<ApiResponse<GameDtos.RoomGameDto>> join(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId);

    @POST("rooms/{roomId}/games/{gameId}/reject")
    Call<ApiResponse<GameDtos.RoomGameDto>> reject(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId);

    @POST("rooms/{roomId}/games/{gameId}/move")
    Call<ApiResponse<GameDtos.RoomGameDto>> move(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId,
            @Body GameDtos.MoveRequest body);

    @POST("rooms/{roomId}/games/{gameId}/state")
    Call<ApiResponse<GameDtos.RoomGameDto>> syncState(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId,
            @Body GameDtos.StateRequest body);

    @GET("rooms/{roomId}/games/{gameId}")
    Call<ApiResponse<GameDtos.RoomGameDto>> get(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId);

    @POST("rooms/{roomId}/games/{gameId}/cancel")
    Call<ApiResponse<GameDtos.RoomGameDto>> cancel(
            @Path("roomId") String roomId,
            @Path("gameId") String gameId);
}
