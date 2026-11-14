package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.GameAdsDtos;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface GameAdsApi {
    @GET("games/ads/config")
    Call<ApiResponse<GameAdsDtos.AdsConfigDto>> config();

    @POST("games/ads/rewarded/claim")
    Call<ApiResponse<GameAdsDtos.ClaimResultDto>> claimRewarded();
}
