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
    @GET("users/me")
    Call<ApiResponse<AuthDtos.UserDto>> me();

    @GET("host-target/me")
    Call<ApiResponse<Map<String, Object>>> hostTargetMe();

    @GET("users/{id}")
    Call<ApiResponse<AuthDtos.UserDto>> getUser(@Path("id") String id);

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

    @GET("users/me/gender-verification")
    Call<ApiResponse<MiscDtos.GenderVerificationDto>> getGenderVerification();

    @POST("users/me/gender-verification")
    Call<ApiResponse<MiscDtos.GenderVerificationDto>> submitGenderVerification(
            @Body MiscDtos.SubmitGenderVerificationRequest body);
}
