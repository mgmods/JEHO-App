package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Native super-admin API. Requests use the app's authenticated Retrofit client;
 * server-side JwtAuthGuard/AdminGuard remain authoritative. */
public interface AdminApi {
    @GET("admin/dashboard/overview")
    Call<ApiResponse<Object>> overview();

    @GET("admin/users")
    Call<ApiResponse<Object>> users(@Query("page") int page, @Query("limit") int limit,
                                    @Query("search") String search);

    @POST("admin/users/{id}/ban")
    Call<ApiResponse<Object>> banUser(@Path("id") String id);

    @POST("admin/users/{id}/unban")
    Call<ApiResponse<Object>> unbanUser(@Path("id") String id);

    @GET("admin/rooms")
    Call<ApiResponse<Object>> rooms(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/rooms/{id}/close")
    Call<ApiResponse<Object>> closeRoom(@Path("id") String id);

    @GET("admin/streams")
    Call<ApiResponse<Object>> streams(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/streams/{id}/end")
    Call<ApiResponse<Object>> endStream(@Path("id") String id);

    @GET("admin/agencies")
    Call<ApiResponse<Object>> agencies(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/agencies/{id}/suspend")
    Call<ApiResponse<Object>> suspendAgency(@Path("id") String id, @Body Map<String, String> body);

    @GET("admin/gifts")
    Call<ApiResponse<Object>> gifts();

    @GET("admin/vip")
    Call<ApiResponse<Object>> vipMembers(@Query("page") int page, @Query("limit") int limit);

    @GET("admin/vip/plans")
    Call<ApiResponse<Object>> vipPlans();

    @POST("admin/vip/assign")
    Call<ApiResponse<Object>> assignVip(@Body Map<String, String> body);

    @GET("admin/notifications")
    Call<ApiResponse<Object>> notifications(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/notifications/send")
    Call<ApiResponse<Object>> sendNotification(@Body Map<String, String> body);

    @GET("admin/logs")
    Call<ApiResponse<Object>> logs(@Query("page") int page, @Query("limit") int limit);
}
