package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.DramaDtos;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import java.util.List;

public interface DramaApi {
    @GET("drama/config")
    Call<ApiResponse<DramaDtos.DramaConfigDto>> config();

    @GET("drama/series")
    Call<ApiResponse<List<DramaDtos.SeriesDto>>> series();

    @GET("drama/series/{id}")
    Call<ApiResponse<DramaDtos.SeriesDto>> seriesDetail(@Path("id") String id);

    @POST("drama/series/{id}/view")
    Call<ApiResponse<Void>> viewSeries(@Path("id") String id);

    @POST("drama/series/{id}/like")
    Call<ApiResponse<Void>> likeSeries(@Path("id") String id);

    @POST("drama/episodes/{id}/view")
    Call<ApiResponse<Void>> viewEpisode(@Path("id") String id);

    @POST("drama/episodes/{id}/like")
    Call<ApiResponse<Void>> likeEpisode(@Path("id") String id);

    @POST("drama/episodes/{id}/claim-reward")
    Call<ApiResponse<DramaDtos.ClaimRewardResult>> claimReward(@Path("id") String id);
}
