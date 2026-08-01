package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface LuckyBoxesApi {
    @GET("lucky-boxes")
    Call<ApiResponse<List<MiscDtos.LuckyBoxDto>>> list();

    @POST("lucky-boxes/{id}/open")
    Call<ApiResponse<MiscDtos.LuckyBoxRewardDto>> open(
            @Path("id") String id,
            @Body Map<String, String> body);

    @POST("lucky-boxes/fund/room/{roomId}")
    Call<ApiResponse<Map<String, Object>>> fundRoom(
            @Path("roomId") String roomId,
            @Body Map<String, Long> body);

    @POST("lucky-boxes/fund/agency/{agencyId}")
    Call<ApiResponse<Map<String, Object>>> fundAgency(
            @Path("agencyId") String agencyId,
            @Body Map<String, Long> body);
}
