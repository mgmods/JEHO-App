package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UserApi {
    @GET("moments/feed")
    Call<ApiResponse<List<Map<String, Object>>>> momentsFeed(@Query("page") int page, @Query("limit") int limit);

    @GET("moments/users/{userId}")
    Call<ApiResponse<List<Map<String, Object>>>> userMoments(@Path("userId") String userId);

    @POST("moments")
    Call<ApiResponse<Map<String, Object>>> createMoment(@Body Map<String, Object> body);

    @POST("moments/{id}/like")
    Call<ApiResponse<Map<String, Object>>> toggleMomentLike(@Path("id") String id);

    @GET("moments/{id}/comments")
    Call<ApiResponse<List<Map<String, Object>>>> momentComments(@Path("id") String id);

    @POST("moments/{id}/comments")
    Call<ApiResponse<Map<String, Object>>> commentOnMoment(@Path("id") String id, @Body Map<String, String> body);

    @DELETE("moments/{id}")
    Call<ApiResponse<Map<String, Object>>> deleteMoment(@Path("id") String id);

    @GET("users/me")
    Call<ApiResponse<AuthDtos.UserDto>> me();

    @GET("host-target/me")
    Call<ApiResponse<Map<String, Object>>> hostTargetMe();

    /** Ladder stage withdraw amounts (host salary / agency agent share). No wallet packages. */
    @GET("host-target/withdraw-options")
    Call<ApiResponse<Map<String, Object>>> hostTargetWithdrawOptions(
            @Query("role") String role,
            @Query("agencyId") String agencyId);

    @GET("users/{id}")
    Call<ApiResponse<AuthDtos.UserDto>> getUser(@Path("id") String id);

    @GET("users/{id}/badges")
    Call<ApiResponse<List<com.Dramizo.Series.data.remote.dto.BadgeDto>>> badges(@Path("id") String id);

    @PATCH("users/me")
    Call<ApiResponse<AuthDtos.UserDto>> updateProfile(@Body MiscDtos.UpdateProfileRequest body);

    @PUT("users/me")
    Call<ApiResponse<AuthDtos.UserDto>> updateProfilePut(@Body MiscDtos.UpdateProfileRequest body);

    @POST("users/{id}/follow")
    Call<ApiResponse<Object>> follow(@Path("id") String id);

    @POST("users/{id}/unfollow")
    Call<ApiResponse<Object>> unfollow(@Path("id") String id);

    @GET("users/{id}/followers")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> followers(@Path("id") String id, @Query("page") int page);

    @GET("users/{id}/following")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> following(@Path("id") String id, @Query("page") int page);

    @GET("users/me/friends")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> friends(@Query("page") int page);

    @GET("users/{id}/friends")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> userFriends(@Path("id") String id, @Query("page") int page);

    @GET("users/search")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> search(@Query("q") String q, @Query("page") int page);

    @GET("users/me/blocked")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> blocked();

    @POST("users/{id}/block")
    Call<ApiResponse<Object>> block(@Path("id") String id, @Body Map<String, String> body);

    @DELETE("users/{id}/block")
    Call<ApiResponse<Object>> unblock(@Path("id") String id);

    @GET("users/me/level")
    Call<ApiResponse<MiscDtos.LevelInfoDto>> level();

    @GET("users/me/visitors")
    Call<ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> visitors(@Query("page") int page);

    @POST("users/{id}/visit")
    Call<ApiResponse<Object>> visit(@Path("id") String id);

    @GET("users/me/requests")
    Call<ApiResponse<List<MiscDtos.SocialRequestDto>>> requests(@Query("type") String type);

    @GET("users/me/relations")
    Call<ApiResponse<List<MiscDtos.SocialRequestDto>>> relations(@Query("type") String type);

    @GET("users/me/cp")
    Call<ApiResponse<MiscDtos.CpStatusDto>> myCp();

    @POST("users/me/requests/{id}/accept")
    Call<ApiResponse<Object>> acceptRequest(@Path("id") String id);

    @POST("users/me/requests/{id}/reject")
    Call<ApiResponse<Object>> rejectRequest(@Path("id") String id);

    @POST("users/{id}/request")
    Call<ApiResponse<Object>> sendRequest(@Path("id") String id, @Body Map<String, String> body);

    @DELETE("users/{id}/friend")
    Call<ApiResponse<Object>> unfriend(@Path("id") String id);

    @GET("users/{id}/bonds")
    Call<ApiResponse<java.util.List<MiscDtos.BondDto>>> bonds(@Path("id") String id);

    @DELETE("users/{id}/bond")
    Call<ApiResponse<Object>> endBond(@Path("id") String id, @Query("type") String type);

    @GET("cp/catalog")
    Call<ApiResponse<List<Map<String, Object>>>> cpCatalog();

    /** Public active relationships for room seats; returns only relationship ids/types and visual config. */
    @GET("cp/room-effects")
    Call<ApiResponse<Map<String, Object>>> cpRoomSeatEffects(@Query("userIds") String userIds);

    @GET("cp/discover")
    Call<ApiResponse<List<Map<String, Object>>>> discoverCp(@Query("type") String type);

    @GET("cp/users/{id}")
    Call<ApiResponse<List<Map<String, Object>>>> cpProfileRelationships(@Path("id") String id);

    @POST("cp/{id}/end")
    Call<ApiResponse<Map<String, Object>>> endCpRelationship(@Path("id") String id);

    @GET("cp/me")
    Call<ApiResponse<List<Map<String, Object>>>> myCpRelationships();

    @POST("cp/profile-requests")
    Call<ApiResponse<Map<String, Object>>> createProfileCpRequest(@Body Map<String, String> body);

    @GET("cp/requests")
    Call<ApiResponse<List<Map<String, Object>>>> cpRequests();

    @GET("cp/sent-requests")
    Call<ApiResponse<List<Map<String, Object>>>> cpSentRequests();

    @POST("cp/requests/{id}/cancel")
    Call<ApiResponse<Map<String, Object>>> cancelCpRequest(@Path("id") String id);

    @POST("cp/requests/{id}/respond")
    Call<ApiResponse<Map<String, Object>>> respondCpRequest(@Path("id") String id, @Body Map<String, Boolean> body);

    @GET("users/me/gender-verification")
    Call<ApiResponse<MiscDtos.GenderVerificationDto>> getGenderVerification();

    @POST("users/me/gender-verification")
    Call<ApiResponse<MiscDtos.GenderVerificationDto>> submitGenderVerification(
            @Body MiscDtos.SubmitGenderVerificationRequest body);
}
