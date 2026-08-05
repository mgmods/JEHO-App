package com.Dramizo.Series.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.AuraLiveApp;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * Encrypted on-disk feature cache (AES-GCM via EncryptedSharedPreferences).
 * Per-user JSON blobs so navigation feels instant (agency, packages, …) while network refreshes.
 * Tokens stay in {@link SessionManager}; this store never holds auth secrets by design.
 */
public final class EncryptedFeatureCache {
    private static final String TAG = "EncryptedFeatureCache";
    private static final String PREFS = "auralive_feature_cache_enc";
    private static final String PREFS_FALLBACK = "auralive_feature_cache_plain";
    private static final long DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(12);

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final boolean encrypted;

    public EncryptedFeatureCache(Context context) {
        Context storage = AuraLiveApp.storageContext(context);
        SharedPreferences secure = null;
        boolean enc = false;
        try {
            androidx.security.crypto.MasterKey key = new androidx.security.crypto.MasterKey.Builder(storage)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build();
            secure = androidx.security.crypto.EncryptedSharedPreferences.create(
                    storage,
                    PREFS,
                    key,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            enc = true;
        } catch (Exception e) {
            Log.w(TAG, "Encrypted prefs unavailable, using private prefs: " + e.getMessage());
        }
        prefs = secure != null
                ? secure
                : storage.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE);
        encrypted = enc;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void putJson(String userId, String namespace, @Nullable Object value) {
        if (userId == null || userId.isEmpty() || namespace == null || value == null) return;
        CacheEntry entry = new CacheEntry();
        entry.savedAt = System.currentTimeMillis();
        entry.json = gson.toJson(value);
        prefs.edit().putString(key(userId, namespace), gson.toJson(entry)).apply();
    }

    @Nullable
    public <T> T getJson(String userId, String namespace, Class<T> type) {
        return getJson(userId, namespace, type, DEFAULT_TTL_MS);
    }

    @Nullable
    public <T> T getJson(String userId, String namespace, Class<T> type, long maxAgeMs) {
        CacheEntry entry = readEntry(userId, namespace);
        if (entry == null || entry.json == null) return null;
        if (maxAgeMs > 0 && System.currentTimeMillis() - entry.savedAt > maxAgeMs) return null;
        try {
            return gson.fromJson(entry.json, type);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public <T> T getJson(String userId, String namespace, Type type, long maxAgeMs) {
        CacheEntry entry = readEntry(userId, namespace);
        if (entry == null || entry.json == null) return null;
        if (maxAgeMs > 0 && System.currentTimeMillis() - entry.savedAt > maxAgeMs) return null;
        try {
            return gson.fromJson(entry.json, type);
        } catch (Exception e) {
            return null;
        }
    }

    public void clearUser(String userId) {
        if (userId == null || userId.isEmpty()) return;
        String prefix = userId + "::";
        SharedPreferences.Editor ed = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (k != null && k.startsWith(prefix)) ed.remove(k);
        }
        ed.apply();
    }

    @Nullable
    private CacheEntry readEntry(String userId, String namespace) {
        if (userId == null || userId.isEmpty() || namespace == null) return null;
        String raw = prefs.getString(key(userId, namespace), null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return gson.fromJson(raw, CacheEntry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String key(String userId, String namespace) {
        return userId + "::" + namespace;
    }

    private static final class CacheEntry {
        long savedAt;
        String json;
    }

    public static final String NS_AGENCY_MINE = "agency_mine";
    public static final String NS_RECHARGE_PACKAGES = "recharge_packages";
    /** App-wide bottom nav icons JSON (userId = {@link #UID_APP}). */
    public static final String NS_NAV_ICONS = "nav_icons";
    public static final String UID_APP = "app";
}
