package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AgencyApi {
    @GET("agencies")
    Call<ApiResponse<MiscDtos.ListResult<MiscDtos.AgencyDto>>> list(@Query("page") int page);

    @GET("agencies/{id}")
    Call<ApiResponse<MiscDtos.AgencyDto>> get(@Path("id") String id);

    @GET("agencies/pricing")
    Call<ApiResponse<MiscDtos.AgencyPricingDto>> pricing();

    @GET("agencies/mine")
    Call<ApiResponse<MiscDtos.AgencyMineDto>> mine();

    @POST("agencies/applications")
    Call<ApiResponse<MiscDtos.AgencyApplicationDto>> apply(
            @Body MiscDtos.AgencyApplicationRequest body);

    @GET("agencies/{id}/earnings")
    Call<ApiResponse<MiscDtos.AgencyEarningsDto>> earnings(@Path("id") String id);

    @POST("agencies/{id}/distribute")
    Call<ApiResponse<Map<String, Object>>> distribute(
            @Path("id") String id, @Body() Map<String, Object> body);

    @POST("agencies/join-by-code")
    Call<ApiResponse<Object>> joinByCode(@Body Map<String, String> body);

    @retrofit2.http.PATCH("agencies/{id}/settings")
    Call<ApiResponse<Object>> updateSettings(@Path("id") String id, @Body Map<String, String> body);

    @POST("agencies/{id}/leave")
    Call<ApiResponse<Object>> leave(@Path("id") String id);

    @DELETE("agencies/{id}")
    Call<ApiResponse<Object>> delete(@Path("id") String id);

    @GET("agencies/{id}/join-requests")
    Call<ApiResponse<MiscDtos.ListResult<MiscDtos.AgencyMemberDto>>> joinRequests(@Path("id") String id);

    @POST("agencies/{id}/join-requests/{userId}/approve")
    Call<ApiResponse<Object>> approveJoin(@Path("id") String id, @Path("userId") String userId);

    @POST("agencies/{id}/join-requests/{userId}/reject")
    Call<ApiResponse<Object>> rejectJoin(@Path("id") String id, @Path("userId") String userId);

    @POST("agencies/purchase")
    Call<ApiResponse<Object>> purchase(@Body Map<String, String> body);

    @POST("agencies/{id}/members")
    Call<ApiResponse<Object>> addMember(@Path("id") String id, @Body Map<String, String> body);

    @retrofit2.http.PATCH("agencies/{id}/members/{userId}")
    Call<ApiResponse<Object>> updateMemberRole(
            @Path("id") String id,
            @Path("userId") String userId,
            @Body Map<String, String> body);

    @POST("agencies/{id}/members/{userId}/suspend")
    Call<ApiResponse<Object>> suspendMember(
            @Path("id") String id, @Path("userId") String userId);

    @POST("agencies/{id}/members/{userId}/unsuspend")
    Call<ApiResponse<Object>> unsuspendMember(
            @Path("id") String id, @Path("userId") String userId);

    @DELETE("agencies/{id}/members/{userId}")
    Call<ApiResponse<Object>> removeMember(@Path("id") String id, @Path("userId") String userId);

    @retrofit2.http.PATCH("agencies/{id}/commission")
    Call<ApiResponse<Object>> updateCommission(
            @Path("id") String id, @Body Map<String, Object> body);

    @GET("agencies/{id}/room")
    Call<ApiResponse<RoomDtos.RoomDto>> room(@Path("id") String id);

    @POST("agencies/{id}/room/open")
    Call<ApiResponse<RoomDtos.JoinRoomResult>> openRoom(
            @Path("id") String id,
            @Body RoomDtos.CreateRoomRequest body);

    @POST("agencies/{id}/room/enter")
    Call<ApiResponse<RoomDtos.JoinRoomResult>> enterRoom(@Path("id") String id);
}
