package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Body;
import java.util.Map;

public interface VipApi {
    @GET("vip/plans")
    Call<ApiResponse<MiscDtos.VipPlanList>> plans();

    @POST("vip/purchase")
    Call<ApiResponse<Object>> purchase(@Body MiscDtos.PurchaseVipRequest body);

    @GET("vip/mysterious-mode")
    Call<ApiResponse<Map<String, Object>>> mysteriousModeStatus();

    @POST("vip/mysterious-mode")
    Call<ApiResponse<Map<String, Object>>> setMysteriousMode(@Body Map<String, Boolean> body);
}
