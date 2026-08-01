package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.UserDao;
import com.Dramizo.Series.data.local.entity.UserEntity;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.api.AuthApi;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.concurrent.ExecutorService;

public class AuthRepositoryImpl implements AuthRepository {
    private final AuthApi api;
    private final SessionManager sessionManager;
    private final UserDao userDao;
    private final ExecutorService io;

    public AuthRepositoryImpl(AuthApi api, SessionManager sessionManager, UserDao userDao, ExecutorService io) {
        this.api = api;
        this.sessionManager = sessionManager;
        this.userDao = userDao;
        this.io = io;
    }

    private Result<AuthDtos.AuthResult> persist(Result<AuthDtos.AuthResult> result) {
        if (result.success && result.data != null) {
            sessionManager.saveSession(result.data);
            if (result.data.user != null) {
                UserEntity e = new UserEntity();
                e.id = result.data.user.id;
                e.username = result.data.user.username;
                e.displayName = result.data.user.displayName;
                e.avatarUrl = result.data.user.avatarUrl;
                e.level = result.data.user.level;
                e.vipLevel = result.data.user.vipLevel;
                e.followersCount = result.data.user.followersCount;
                e.followingCount = result.data.user.followingCount;
                e.cachedAt = System.currentTimeMillis();
                io.execute(() -> userDao.upsert(e));
            }
        }
        return result;
    }

    @Override
    public Result<AuthDtos.AuthResult> login(String identifier, String password) {
        return persist(ApiCall.execute(api.login(new AuthDtos.LoginRequest(identifier, password))));
    }

    @Override
    public Result<AuthDtos.AuthResult> register(String email, String password, String username, String displayName) {
        return persist(ApiCall.execute(api.register(new AuthDtos.RegisterRequest(email, password, username, displayName))));
    }

    @Override
    public Result<AuthDtos.OtpSentResult> sendOtp(String phone) {
        return ApiCall.execute(api.sendOtp(new AuthDtos.SendOtpRequest(phone)));
    }

    @Override
    public Result<AuthDtos.AuthResult> verifyOtp(String phone, String code, String username) {
        return persist(ApiCall.execute(api.verifyOtp(new AuthDtos.VerifyOtpRequest(phone, code, username))));
    }

    @Override
    public Result<AuthDtos.AuthResult> guestLogin(String deviceId, String displayName) {
        return persist(ApiCall.execute(api.guestLogin(new AuthDtos.GuestLoginRequest(deviceId, displayName))));
    }

    @Override
    public Result<AuthDtos.AuthResult> socialLogin(String provider, String token) {
        return socialLogin(provider, token, null, null, null, null);
    }

    @Override
    public Result<AuthDtos.AuthResult> socialLogin(
            String provider,
            String token,
            String email,
            String displayName,
            String avatarUrl,
            String providerUserId) {
        AuthDtos.SocialLoginRequest req = new AuthDtos.SocialLoginRequest(provider, token);
        req.email = email;
        req.displayName = displayName;
        req.avatarUrl = avatarUrl;
        req.providerUserId = providerUserId;
        return persist(ApiCall.execute(api.socialLogin(req)));
    }

    @Override
    public Result<Object> logout() {
        Result<Object> result = ApiCall.execute(api.logout());
        sessionManager.clearAuth();
        io.execute(userDao::clear);
        return result.success ? result : Result.ok(new Object());
    }

    @Override
    public Result<Object> changePassword(String currentPassword, String newPassword) {
        return ApiCall.execute(api.changePassword(new AuthDtos.ChangePasswordRequest(currentPassword, newPassword)));
    }
}
