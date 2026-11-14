package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface CosmeticsApi {
    @GET("cosmetics")
    Call<ApiResponse<CosmeticDtos.CatalogList>> catalog(@Query("type") String type);

    @GET("cosmetics/inventory")
    Call<ApiResponse<CosmeticDtos.InventoryList>> inventory();

    @POST("cosmetics/purchase")
    Call<ApiResponse<CosmeticDtos.UserCosmeticDto>> purchase(@Body CosmeticDtos.PurchaseRequest body);

    @POST("cosmetics/equip")
    Call<ApiResponse<CosmeticDtos.EquipResult>> equip(@Body CosmeticDtos.EquipRequest body);

    @POST("cosmetics/unequip")
    Call<ApiResponse<CosmeticDtos.EquipResult>> unequip(@Body CosmeticDtos.EquipRequest body);
}
