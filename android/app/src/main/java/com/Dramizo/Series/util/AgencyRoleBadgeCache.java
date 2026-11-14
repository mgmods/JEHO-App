package com.Dramizo.Series.util;

import android.content.Context;
import androidx.annotation.Nullable;
import com.Dramizo.Series.data.remote.api.AdminApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Public, short-lived cache for dashboard-controlled agency role badge images. */
public final class AgencyRoleBadgeCache {
    public static final class Badges {
        public final String leader;
        public final String host;
        Badges(String leader, String host) { this.leader = leader; this.host = host; }
    }
    private static final Object LOCK = new Object();
    private static volatile Badges value = new Badges("", "");
    private static long loadedAt;
    private static boolean loading;
    private static Runnable pending;
    private static final long TTL_MS = 30_000L;
    private AgencyRoleBadgeCache() {}
    public static Badges get() { return value; }
    public static void load(Context context, @Nullable Runnable callback) {
        synchronized (LOCK) {
            if (System.currentTimeMillis() - loadedAt < TTL_MS) {
                if (callback != null) callback.run();
                return;
            }
            if (callback != null) pending = callback;
            if (loading) return;
            loading = true;
        }
        try {
            AdminApi api = ContainerProvider.from(context).getAdminApi();
            api.publicAgencyRoleBadges().enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                    String leader = "", host = "";
                    Object data = response.body() == null ? null : response.body().data;
                    if (response.isSuccessful() && data instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) data;
                        leader = safe(map.get("leaderBadgeUrl"));
                        host = safe(map.get("hostBadgeUrl"));
                    }
                    finish(new Badges(leader, host));
                }
                @Override public void onFailure(Call<ApiResponse<Object>> call, Throwable error) {
                    finish(value);
                }
            });
        } catch (Exception ignored) { finish(value); }
    }
    public static void refresh(Context context, @Nullable Runnable callback) {
        synchronized (LOCK) { loadedAt = 0; }
        load(context, callback);
    }
    private static String safe(Object raw) {
        if (raw == null) return "";
        String s = String.valueOf(raw).trim();
        return s.length() <= 2048 && (s.startsWith("https://") || s.startsWith("/uploads/")) ? s : "";
    }
    private static void finish(Badges next) {
        Runnable cb;
        synchronized (LOCK) {
            value = next;
            loadedAt = System.currentTimeMillis();
            loading = false;
            cb = pending;
            pending = null;
        }
        if (cb != null) cb.run();
    }
}
