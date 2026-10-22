package com.Dramizo.Series.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.api.AdminApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.presentation.common.ContainerProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Small in-memory cache for public role identity assets; never blocks room rendering. */
public final class StaffIdentityConfigCache {
    public static final class RoleIdentity {
        public final String label;
        public final String accentColor;
        public final String badgeUrl;
        public final String frameUrl;
        public final String nameplateUrl;
        public final String entryEffectUrl;

        RoleIdentity(String label, String accentColor, String badgeUrl, String frameUrl,
                     String nameplateUrl, String entryEffectUrl) {
            this.label = label;
            this.accentColor = accentColor;
            this.badgeUrl = badgeUrl;
            this.frameUrl = frameUrl;
            this.nameplateUrl = nameplateUrl;
            this.entryEffectUrl = entryEffectUrl;
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<String, RoleIdentity> CACHE = new HashMap<>();
    private static final List<Runnable> WAITERS = new ArrayList<>();
    private static boolean loaded;
    private static boolean loading;
    private static long loadedAtMs;
    private static final long CACHE_TTL_MS = 60_000L;

    private StaffIdentityConfigCache() {}

    public static RoleIdentity get(String role) {
        synchronized (LOCK) {
            RoleIdentity value = CACHE.get(role);
            if (value != null) return value;
        }
        return defaults(role);
    }

    public static void load(Context context, @Nullable Runnable onReady) {
        synchronized (LOCK) {
            if (loaded && System.currentTimeMillis() - loadedAtMs < CACHE_TTL_MS) {
                if (onReady != null) onReady.run();
                return;
            }
            if (loaded && System.currentTimeMillis() - loadedAtMs >= CACHE_TTL_MS) loaded = false;
            if (onReady != null) WAITERS.add(onReady);
            if (loading) return;
            loading = true;
        }
        try {
            AdminApi api = ContainerProvider.from(context).getAdminApi();
            api.publicStaffIdentity().enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                    Map<String, RoleIdentity> parsed = new HashMap<>();
                    Object data = response.body() != null ? response.body().data : null;
                    if (response.isSuccessful() && response.body() != null && response.body().success && data instanceof Map) {
                        Map<?, ?> root = (Map<?, ?>) data;
                        for (String role : new String[]{"super", "admin", "manager"}) {
                            Object raw = root.get(role);
                            if (raw instanceof Map) parsed.put(role, fromMap((Map<?, ?>) raw, role));
                        }
                    }
                    finish(parsed);
                }
                @Override public void onFailure(Call<ApiResponse<Object>> call, Throwable error) {
                    finish(new HashMap<>());
                }
            });
        } catch (Exception ignored) {
            finish(new HashMap<>());
        }
    }

    /** Force a fresh role-identity read after an administrator saves role assets. */
    public static void refresh(Context context, @Nullable Runnable onReady) {
        synchronized (LOCK) { loaded = false; }
        load(context, onReady);
    }

    private static void finish(Map<String, RoleIdentity> parsed) {
        List<Runnable> callbacks;
        synchronized (LOCK) {
            for (String role : new String[]{"super", "admin", "manager"}) {
                CACHE.put(role, parsed.getOrDefault(role, defaults(role)));
            }
            loaded = true;
            loadedAtMs = System.currentTimeMillis();
            loading = false;
            callbacks = new ArrayList<>(WAITERS);
            WAITERS.clear();
        }
        for (Runnable callback : callbacks) {
            try { callback.run(); } catch (Exception ignored) {}
        }
    }

    private static RoleIdentity fromMap(Map<?, ?> value, String role) {
        RoleIdentity fallback = defaults(role);
        return new RoleIdentity(
                safe(value.get("label"), fallback.label, 32),
                safe(value.get("accentColor"), fallback.accentColor, 7),
                safe(value.get("badgeUrl"), "", 2048),
                safe(value.get("frameUrl"), "", 2048),
                safe(value.get("nameplateUrl"), "", 2048),
                safe(value.get("entryEffectUrl"), "", 2048));
    }

    private static String safe(Object value, String fallback, int max) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? fallback : text.substring(0, Math.min(max, text.length()));
    }

    private static RoleIdentity defaults(String role) {
        if (StaffRoleHelper.SUPER.equals(role)) return new RoleIdentity("Super Admin", "#D8B35B", "", "", "", "");
        if (StaffRoleHelper.ADMIN.equals(role)) return new RoleIdentity("Admin", "#9AA8C7", "", "", "", "");
        if (StaffRoleHelper.MANAGER.equals(role)) return new RoleIdentity("Manager", "#37A889", "", "", "", "");
        return new RoleIdentity("", "#6B4CC2", "", "", "", "");
    }
}
