package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.RoomCupDtos;
import retrofit2.Call;
import retrofit2.http.GET;

public interface RoomCupApi {
    @GET("room-cup/leaderboard")
    Call<ApiResponse<RoomCupDtos.Leaderboard>> leaderboard();
}
