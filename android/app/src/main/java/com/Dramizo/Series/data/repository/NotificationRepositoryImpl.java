package com.Dramizo.Series.data.repository;

import android.os.Build;
import android.provider.Settings;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.api.NotificationApi;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.NotificationRepository;
import com.Dramizo.Series.util.ApiCall;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class NotificationRepositoryImpl implements NotificationRepository {
    private final NotificationApi api;
    private final SessionManager sessionManager;
    private final android.content.Context appContext;

    public NotificationRepositoryImpl(
            NotificationApi api,
            ExecutorService io,
            SessionManager sessionManager,
            android.content.Context appContext) {
        this.api = api;
        this.sessionManager = sessionManager;
        this.appContext = appContext.getApplicationContext();
    }

    @Override
    public Result<MiscDtos.ListResult<MiscDtos.NotificationDto>> list(int page) {
        return ApiCall.execute(api.list(page));
    }

    @Override
    public Result<Object> markRead(String id) {
        return ApiCall.execute(api.markRead(id));
    }

    @Override
    public Result<Object> registerDevice(String token, String platform) {
        if (token == null || token.isEmpty()) return Result.err("empty token");
        sessionManager.saveFcmToken(token);
        String deviceId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) deviceId = "android-" + System.currentTimeMillis();
        Map<String, String> body = new HashMap<>();
        body.put("deviceId", deviceId);
        body.put("platform", platform != null ? platform : "android");
        body.put("fcmToken", token);
        body.put("token", token);
        body.put("model", Build.MODEL);
        body.put("appVersion", "1.0.0");
        return ApiCall.execute(api.registerDevice(body));
    }

    @Override
    public Result<Object> unregisterDevice() {
        String deviceId = Settings.Secure.getString(
                appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) return Result.ok(new Object());
        return ApiCall.execute(api.unregisterDevice(deviceId));
    }
}
