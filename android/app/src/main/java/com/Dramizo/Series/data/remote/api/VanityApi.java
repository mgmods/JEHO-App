package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.VanityDtos;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface VanityApi {
    @GET("vanity-ids")
    Call<ApiResponse<VanityDtos.Catalog>> list(@Query("status") String status);

    @GET("vanity-ids/mine")
    Call<ApiResponse<VanityDtos.MineResult>> mine();

    @POST("vanity-ids/id/{publicId}/reserve")
    Call<ApiResponse<VanityDtos.VanityItem>> reserve(@Path("publicId") String publicId);

    @POST("vanity-ids/id/{publicId}/purchase")
    Call<ApiResponse<VanityDtos.PurchaseResult>> purchase(@Path("publicId") String publicId);
}
