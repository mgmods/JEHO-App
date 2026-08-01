package com.Dramizo.Series.data.remote.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * On HTTP 401, refresh access token once and retry the failed call.
 */
public class TokenAuthenticator implements Authenticator {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final SessionManager sessionManager;
    private final String baseUrl;
    private final Gson gson = new Gson();
    private final OkHttpClient refreshClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public TokenAuthenticator(SessionManager sessionManager, String baseUrl) {
        this.sessionManager = sessionManager;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        if (responseCount(response) >= 2) return null;
        String path = response.request().url().encodedPath();
        if (path.contains("/auth/login") || path.contains("/auth/register")
                || path.contains("/auth/refresh") || path.contains("/auth/guest")) {
            return null;
        }

        SessionManager.AUTH_REFRESH_LOCK.lock();
        try {
            String currentAccess = sessionManager.getAccessToken();
            String failedAuth = response.request().header("Authorization");
            if (currentAccess != null && failedAuth != null
                    && !failedAuth.equals("Bearer " + currentAccess)
                    && !currentAccess.isEmpty()) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentAccess)
                        .build();
            }

            String refresh = sessionManager.getRefreshToken();
            if (refresh == null || refresh.isEmpty()) {
                // Keep local session — user can still open the app offline.
                return null;
            }

            String bodyJson = gson.toJson(new AuthDtos.RefreshTokenRequest(refresh));
            Request refreshReq = new Request.Builder()
                    .url(baseUrl + "auth/refresh")
                    .post(RequestBody.create(bodyJson, JSON))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build();

            try (Response refreshResp = refreshClient.newCall(refreshReq).execute()) {
                if (!refreshResp.isSuccessful() || refreshResp.body() == null) {
                    // Never wipe saved login here — keep the user signed in locally.
                    return null;
                }
                String raw = refreshResp.body().string();
                Type type = new TypeToken<ApiResponse<AuthDtos.AuthResult>>() {}.getType();
                ApiResponse<AuthDtos.AuthResult> parsed = gson.fromJson(raw, type);
                if (parsed == null || !parsed.success || parsed.data == null
                        || parsed.data.accessToken == null || parsed.data.accessToken.isEmpty()) {
                    // Do not wipe on malformed body — retry later.
                    return null;
                }
                sessionManager.saveSession(parsed.data);
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + parsed.data.accessToken)
                        .build();
            } catch (IOException e) {
                return null;
            }
        } finally {
            SessionManager.AUTH_REFRESH_LOCK.unlock();
        }
    }

    private static int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }
}
