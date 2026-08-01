package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.GameStoreDtos;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GameStoreApi {
    @GET("games-store/catalog")
    Call<ApiResponse<GameStoreDtos.CatalogResult>> catalog(@Query("section") String section);

    @GET("games-store/inventory")
    Call<ApiResponse<java.util.List<GameStoreDtos.OwnedItem>>> inventory();

    @POST("games-store/purchase")
    Call<ApiResponse<GameStoreDtos.PurchaseResult>> purchase(@Body Map<String, String> body);

    @GET("games-store/loadout")
    Call<ApiResponse<Map<String, String>>> loadout();

    @POST("games-store/equip")
    Call<ApiResponse<GameStoreDtos.EquipResult>> equip(@Body Map<String, String> body);
}
