package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ConfigApi {
    @GET("config/banners")
    Call<ApiResponse<List<MiscDtos.BannerDto>>> banners();

    @GET("config/offers")
    Call<ApiResponse<List<MiscDtos.OfferDto>>> offers();

    @GET("config/games")
    Call<ApiResponse<List<MiscDtos.GameDto>>> games();

    @GET("config/features")
    Call<ApiResponse<MiscDtos.FeaturesDto>> features();

    @GET("config/app-update")
    Call<ApiResponse<MiscDtos.AppUpdateDto>> appUpdate();

    @GET("config/zego")
    Call<ApiResponse<MiscDtos.ZegoConfigDto>> zego();

    @GET("config/support")
    Call<ApiResponse<MiscDtos.SupportConfigDto>> support();

    @POST("translate")
    Call<ApiResponse<Map<String, Object>>> translate(@Body Map<String, String> body);
}
