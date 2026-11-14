package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.PromoDtos;

import retrofit2.Call;
import retrofit2.http.GET;

public interface PromotionsApi {
    @GET("promotions/catalog")
    Call<ApiResponse<PromoDtos.Catalog>> catalog();

    @GET("promotions/me")
    Call<ApiResponse<PromoDtos.MyProgress>> me();
}
