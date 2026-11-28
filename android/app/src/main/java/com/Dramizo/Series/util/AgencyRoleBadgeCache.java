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

/** Public cache for dashboard-controlled agency role badge images used only by the public profile. */
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

    // Profile screens must observe dashboard image replacements promptly.
    private static final long TTL_MS = 0L;

    private AgencyRoleBadgeCache() {}

    public static Badges get() {
        return value;
    }

    public static void load(Context context, @Nullable Runnable callback) {
        synchronized (LOCK) {
            if (TTL_MS > 0 && System.currentTimeMillis() - loadedAt < TTL_MS) {
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
                @Override public void onResponse(Call<ApiResponse<Object>> call,
                        Response<ApiResponse<Object>> response) {
                    String leader = "", host = "";
                    Object data = response.body() == null ? null : response.body().data;
                    if (response.isSuccessful() && response.body() != null
                            && response.body().success && data instanceof Map) {
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
        } catch (Exception ignored) {
            finish(value);
        }
    }

    /** Force a fresh dashboard read after role assets are changed. */
    public static void refresh(Context context, @Nullable Runnable callback) {
        synchronized (LOCK) {
            loadedAt = 0L;
        }
        load(context, callback);
    }

    private static String safe(Object raw) {
        if (raw == null) return "";
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || s.length() > 2048) return "";

        // Accept absolute URLs and server-served asset paths. AssetCatalog
        // normalizes relative paths before Glide loads them.
        if (s.startsWith("https://") || s.startsWith("http://")
                || s.startsWith("/uploads/") || s.startsWith("/assets/")
                || s.startsWith("/api/") || s.startsWith("uploads/")
                || s.startsWith("assets/")) {
            return s;
        }
        return "";
    }

    private static void finish(Badges next) {
        Runnable cb;
        synchronized (LOCK) {
            value = next == null ? new Badges("", "") : next;
            loadedAt = System.currentTimeMillis();
            loading = false;
            cb = pending;
            pending = null;
        }
        if (cb != null) cb.run();
    }
}
