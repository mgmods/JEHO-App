package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.ContestDtos;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ContestsApi {
    @GET("contests")
    Call<ApiResponse<ContestDtos.ContestList>> list();

    @GET("contests")
    Call<ApiResponse<ContestDtos.ContestList>> listScoped(
            @Query("roomId") String roomId,
            @Query("agencyId") String agencyId);

    @GET("contests/{id}")
    Call<ApiResponse<ContestDtos.ContestDetail>> get(@Path("id") String id);

    @POST("contests/{id}/join")
    Call<ApiResponse<Object>> join(@Path("id") String id);
}
