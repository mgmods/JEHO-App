package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;

public interface AuthRepository {
    Result<AuthDtos.AuthResult> login(String identifier, String password);
    Result<AuthDtos.AuthResult> register(String email, String password, String username, String displayName);
    Result<AuthDtos.OtpSentResult> sendOtp(String phone);
    Result<AuthDtos.AuthResult> verifyOtp(String phone, String code, String username);
    Result<AuthDtos.AuthResult> guestLogin(String deviceId, String displayName);
    Result<AuthDtos.AuthResult> socialLogin(String provider, String token);

    Result<AuthDtos.AuthResult> socialLogin(
            String provider,
            String token,
            String email,
            String displayName,
            String avatarUrl,
            String providerUserId);
    Result<Object> logout();
    Result<Object> changePassword(String currentPassword, String newPassword);
}
