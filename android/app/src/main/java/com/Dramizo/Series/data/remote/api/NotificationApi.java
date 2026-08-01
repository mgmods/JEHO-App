package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.Map;

public interface NotificationApi {
    @GET("notifications")
    Call<ApiResponse<MiscDtos.ListResult<MiscDtos.NotificationDto>>> list(@Query("page") int page);

    @GET("notifications/official")
    Call<ApiResponse<MiscDtos.ListResult<MiscDtos.NotificationDto>>> officialList(@Query("page") int page);

    @GET("notifications/official/preview")
    Call<ApiResponse<MiscDtos.OfficialNewsPreviewDto>> officialPreview();

    @POST("notifications/official/read-all")
    Call<ApiResponse<Object>> officialMarkAllRead();

    @PATCH("notifications/{id}/read")
    Call<ApiResponse<Object>> markRead(@Path("id") String id);

    @POST("notifications/devices")
    Call<ApiResponse<Object>> registerDevice(@Body Map<String, String> body);

    @DELETE("notifications/devices/{deviceId}")
    Call<ApiResponse<Object>> unregisterDevice(@Path("deviceId") String deviceId);
}
