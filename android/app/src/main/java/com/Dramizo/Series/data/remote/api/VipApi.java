package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface VipApi {
    @GET("vip/plans")
    Call<ApiResponse<MiscDtos.VipPlanList>> plans();

    @POST("vip/purchase")
    Call<ApiResponse<Object>> purchase(@Body MiscDtos.PurchaseVipRequest body);
}
