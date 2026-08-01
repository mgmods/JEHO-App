package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TasksApi {
    @GET("tasks/daily")
    Call<ApiResponse<List<MiscDtos.TaskDto>>> daily();

    @GET("tasks/daily")
    Call<ApiResponse<List<MiscDtos.TaskDto>>> dailyScoped(
            @Query("roomId") String roomId,
            @Query("agencyId") String agencyId);

    @POST("tasks/checkin")
    Call<ApiResponse<Map<String, Object>>> checkin();

    @POST("tasks/daily/{id}/claim")
    Call<ApiResponse<Map<String, Object>>> claim(@Path("id") String id);
}
