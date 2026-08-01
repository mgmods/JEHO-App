package com.Dramizo.Series.domain.usecase.auth;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.api.AuthApi;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.ApiCall;

public class RefreshSessionUseCase {
    private final AuthApi authApi;
    private final SessionManager sessionManager;

    public RefreshSessionUseCase(AuthApi authApi, SessionManager sessionManager) {
        this.authApi = authApi;
        this.sessionManager = sessionManager;
    }

    public Result<AuthDtos.AuthResult> execute() {
        SessionManager.AUTH_REFRESH_LOCK.lock();
        try {
            String refresh = sessionManager.getRefreshToken();
            if (refresh == null || refresh.isEmpty()) {
                return Result.err("no_refresh");
            }
            Result<AuthDtos.AuthResult> r = ApiCall.execute(
                    authApi.refresh(new AuthDtos.RefreshTokenRequest(refresh)));
            if (r.success && r.data != null) {
                sessionManager.saveSession(r.data);
                return r;
            }
            // Never wipe login here — keep the user signed in offline / on transient errors.
            // TokenAuthenticator clears only on a definitive HTTP 401/403 from /auth/refresh.
            return r;
        } finally {
            SessionManager.AUTH_REFRESH_LOCK.unlock();
        }
    }
}
