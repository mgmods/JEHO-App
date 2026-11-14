package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("auth/register")
    Call<ApiResponse<AuthDtos.AuthResult>> register(@Body AuthDtos.RegisterRequest body);

    @POST("auth/login")
    Call<ApiResponse<AuthDtos.AuthResult>> login(@Body AuthDtos.LoginRequest body);

    @POST("auth/otp/send")
    Call<ApiResponse<AuthDtos.OtpSentResult>> sendOtp(@Body AuthDtos.SendOtpRequest body);

    @POST("auth/otp/verify")
    Call<ApiResponse<AuthDtos.AuthResult>> verifyOtp(@Body AuthDtos.VerifyOtpRequest body);

    @POST("auth/guest")
    Call<ApiResponse<AuthDtos.AuthResult>> guestLogin(@Body AuthDtos.GuestLoginRequest body);

    @POST("auth/social")
    Call<ApiResponse<AuthDtos.AuthResult>> socialLogin(@Body AuthDtos.SocialLoginRequest body);

    @POST("auth/refresh")
    Call<ApiResponse<AuthDtos.AuthResult>> refresh(@Body AuthDtos.RefreshTokenRequest body);

    @POST("auth/change-password")
    Call<ApiResponse<Object>> changePassword(@Body AuthDtos.ChangePasswordRequest body);

    @POST("auth/logout")
    Call<ApiResponse<Object>> logout();
}
