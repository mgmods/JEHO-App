package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.ContestDtos;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.Map;

public interface ContestsApi {
    @GET("contests")
    Call<ApiResponse<ContestDtos.ContestList>> list();

    @GET("contests")
    Call<ApiResponse<ContestDtos.ContestList>> listScoped(
            @Query("roomId") String roomId,
            @Query("agencyId") String agencyId);

    @GET("contests/room-pk/invitations")
    Call<ApiResponse<ContestDtos.ContestList>> roomPkInvitations(@Query("roomId") String roomId);

    @POST("contests/room-pk")
    Call<ApiResponse<Object>> createRoomPk(@Body Map<String, Object> body);

    @POST("contests/{id}/room-pk-response")
    Call<ApiResponse<Object>> respondRoomPk(@Path("id") String id, @Body Map<String, Object> body);

    @GET("contests/{id}/room-pk/rosters")
    Call<ApiResponse<com.google.gson.JsonObject>> roomPkRosters(@Path("id") String id);

    @GET("contests/{id}")
    Call<ApiResponse<ContestDtos.ContestDetail>> get(@Path("id") String id);

    @POST("contests/{id}/join")
    Call<ApiResponse<Object>> join(@Path("id") String id);
}
