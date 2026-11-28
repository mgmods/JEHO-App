package com.Dramizo.Series.data.remote.interceptor;

import androidx.annotation.NonNull;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final SessionManager sessionManager;

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();
        // Public authentication endpoints must not inherit a stale token from
        // the previous account/session.
        if (path.contains("/auth/login") || path.contains("/auth/register")
                || path.contains("/auth/otp/") || path.contains("/auth/guest")
                || path.contains("/auth/social") || path.contains("/auth/refresh")) {
            return chain.proceed(original);
        }
        String token = sessionManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            return chain.proceed(original);
        }
        Request authed = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();
        return chain.proceed(authed);
    }
}
