package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ConfigApi {
    @GET("config/banners")
    Call<ApiResponse<List<MiscDtos.BannerDto>>> banners();

    @GET("config/offers")
    Call<ApiResponse<List<MiscDtos.OfferDto>>> offers();

    @GET("config/games")
    Call<ApiResponse<List<MiscDtos.GameDto>>> games();

    // Dashboard-managed icons must never be served from a stale HTTP/CDN cache.
    // The timestamp query also gives every refresh a unique request URL.
    @Headers({"Cache-Control: no-cache, no-store, max-age=0", "Pragma: no-cache"})
    @GET("config/nav-icons")
    Call<ApiResponse<MiscDtos.NavIconsDto>> navIcons(@Query("_") long cacheBust);

    @GET("config/seat-stickers")
    Call<ApiResponse<MiscDtos.SeatStickersDto>> seatStickers();

    @GET("config/features")
    Call<ApiResponse<MiscDtos.FeaturesDto>> features();

    @GET("config/app-update")
    Call<ApiResponse<MiscDtos.AppUpdateDto>> appUpdate();

    @GET("config/zego")
    Call<ApiResponse<MiscDtos.ZegoConfigDto>> zego();

    @Headers({"Cache-Control: no-cache", "Pragma: no-cache"})
    @GET("config/theme")
    Call<ApiResponse<MiscDtos.ThemeDto>> theme();

    @GET("config/support")
    Call<ApiResponse<MiscDtos.SupportConfigDto>> support();

    @GET("config/sham-cash")
    Call<ApiResponse<MiscDtos.ShamCashConfigDto>> shamCash();

    @POST("translate")
    Call<ApiResponse<Map<String, Object>>> translate(@Body Map<String, String> body);
}
