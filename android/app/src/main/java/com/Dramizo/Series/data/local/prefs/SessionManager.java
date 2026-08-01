package com.Dramizo.Series.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.google.gson.Gson;

/**
 * Plain SharedPreferences session store on the unwrapped app context.
 * Locale-wrapped Application contexts can fail to reload saved tokens on cold start.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS = "auralive_session";
    private static final String PREFS_LEGACY_SECURE = "auralive_secure_session";
    private static final String PREFS_LEGACY_PLAIN = "auralive_session_plain";

    public static final java.util.concurrent.locks.ReentrantLock AUTH_REFRESH_LOCK =
            new java.util.concurrent.locks.ReentrantLock();

    private final Context storageContext;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public SessionManager(Context context) {
        storageContext = AuraLiveApp.storageContext(context);
        prefs = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyIfNeeded();
    }

    private void migrateLegacyIfNeeded() {
        if (hasToken(prefs)) return;
        SharedPreferences plain = storageContext.getSharedPreferences(PREFS_LEGACY_PLAIN, Context.MODE_PRIVATE);
        if (hasToken(plain)) {
            copyAuth(plain, prefs);
            return;
        }
        try {
            androidx.security.crypto.MasterKey key = new androidx.security.crypto.MasterKey.Builder(storageContext)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences secure = androidx.security.crypto.EncryptedSharedPreferences.create(
                    storageContext, PREFS_LEGACY_SECURE, key,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            if (hasToken(secure)) {
                copyAuth(secure, prefs);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            SharedPreferences legacy = storageContext.getSharedPreferences(PREFS_LEGACY_SECURE, Context.MODE_PRIVATE);
            if (hasToken(legacy)) copyAuth(legacy, prefs);
        } catch (Exception ignored) {
        }
    }

    private static boolean hasToken(SharedPreferences p) {
        String t = p.getString("access_token", null);
        return t != null && !t.isEmpty();
    }

    private static void copyAuth(SharedPreferences from, SharedPreferences to) {
        to.edit()
                .putString("access_token", from.getString("access_token", null))
                .putString("refresh_token", from.getString("refresh_token", null))
                .putString("user_json", from.getString("user_json", null))
                .putString("user_id", from.getString("user_id", null))
                .commit();
    }

    public void saveSession(AuthDtos.AuthResult result) {
        if (result == null || result.accessToken == null || result.accessToken.isEmpty()) {
            Log.w(TAG, "saveSession skipped — missing access token");
            return;
        }
        if (!prefs.edit()
                .putString("access_token", result.accessToken)
                .putString("refresh_token", result.refreshToken)
                .putString("user_json", result.user != null ? gson.toJson(result.user) : prefs.getString("user_json", null))
                .putString("user_id", result.user != null ? result.user.id : prefs.getString("user_id", null))
                .commit()) {
            Log.e(TAG, "saveSession commit failed");
        }
    }

    public void updateCachedUser(AuthDtos.UserDto user) {
        if (user == null) return;
        prefs.edit()
                .putString("user_json", gson.toJson(user))
                .putString("user_id", user.id != null ? user.id : prefs.getString("user_id", null))
                .commit();
    }

    public String getAccessToken() { return prefs.getString("access_token", null); }
    public String getRefreshToken() { return prefs.getString("refresh_token", null); }
    public String getUserId() { return prefs.getString("user_id", null); }

    public AuthDtos.UserDto getUser() {
        String json = prefs.getString("user_json", null);
        if (json == null || json.isEmpty() || "null".equals(json)) return null;
        try {
            return gson.fromJson(json, AuthDtos.UserDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getDisplayName() {
        AuthDtos.UserDto user = getUser();
        if (user == null) return null;
        if (user.displayName != null && !user.displayName.isEmpty()) return user.displayName;
        return user.username;
    }

    public int getVipLevel() {
        AuthDtos.UserDto user = getUser();
        if (user == null) return 0;
        if (user.vipLevel > 0) return user.vipLevel;
        try {
            String json = prefs.getString("user_json", null);
            if (json == null) return 0;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (o.has("activeVip") && o.get("activeVip").isJsonObject()) {
                com.google.gson.JsonObject vip = o.getAsJsonObject("activeVip");
                if (vip.has("level") && !vip.get("level").isJsonNull()) {
                    return Math.max(0, vip.get("level").getAsInt());
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public int getUserLevel() {
        AuthDtos.UserDto user = getUser();
        return user != null ? Math.max(1, user.level) : 1;
    }

    public String getEntryEffectUrl() {
        AuthDtos.UserDto user = getUser();
        if (user == null) return null;
        if (user.entryEffectUrl != null && !user.entryEffectUrl.isEmpty()) return user.entryEffectUrl;
        return null;
    }

    public String getEntryAnimationUrl() {
        AuthDtos.UserDto user = getUser();
        return user != null ? user.entryAnimationUrl : null;
    }

    public String getRoomCardUrl() {
        AuthDtos.UserDto user = getUser();
        return user != null ? user.roomCardUrl : null;
    }

    public String getAvatarUrl() {
        AuthDtos.UserDto user = getUser();
        return user != null ? user.avatarUrl : null;
    }

    public String getHostBadgeUrl() {
        AuthDtos.UserDto user = getUser();
        return user != null ? user.hostBadgeUrl : null;
    }

    public boolean isLoggedIn() {
        String t = getAccessToken();
        return t != null && !t.isEmpty();
    }

    public void clearAuth() {
        prefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .remove("user_json")
                .remove("user_id")
                .commit();
    }

    public void clear() { prefs.edit().clear().apply(); }
    public void saveFcmToken(String token) { prefs.edit().putString("fcm_token", token).apply(); }
    public String getFcmToken() { return prefs.getString("fcm_token", null); }
    public void setDarkMode(boolean enabled) { prefs.edit().putBoolean("dark_mode", enabled).apply(); }
    public boolean isDarkMode() { return prefs.getBoolean("dark_mode", false); }

    public void setMuteMessageNotifications(boolean muted) {
        prefs.edit().putBoolean("mute_message_notifications", muted).apply();
    }

    public boolean isMuteMessageNotifications() {
        return prefs.getBoolean("mute_message_notifications", false);
    }

    public void setFriendsOnlyMessages(boolean enabled) {
        prefs.edit().putBoolean("friends_only_messages", enabled).apply();
    }

    public boolean isFriendsOnlyMessages() {
        return prefs.getBoolean("friends_only_messages", false);
    }

    public void setFemaleOnlyVoiceHostsFromServer(boolean enabled) {
        prefs.edit().putBoolean("server_female_only_hosts", enabled).apply();
    }

    public boolean isFemaleOnlyVoiceHostsFromServer() {
        return prefs.getBoolean("server_female_only_hosts", false);
    }

    public void setMicWithoutHostApprovalFromServer(boolean enabled) {
        prefs.edit().putBoolean("server_mic_without_approval", enabled).apply();
    }

    public boolean isMicWithoutHostApprovalFromServer() {
        return prefs.getBoolean("server_mic_without_approval", true);
    }

    public void setGiftSoundsEnabledFromServer(boolean enabled) {
        prefs.edit().putBoolean("server_gift_sounds_enabled", enabled).apply();
    }

    public boolean isGiftSoundsEnabledFromServer() {
        return prefs.getBoolean("server_gift_sounds_enabled", true);
    }

    public void setLanguage(String lang) {
        prefs.edit().putString("language", lang).commit();
        try {
            storageContext.getSharedPreferences("auralive_lang", Context.MODE_PRIVATE)
                    .edit().putString("language", lang).commit();
        } catch (Exception ignored) {
        }
    }

    public String getLanguage() { return prefs.getString("language", "ar"); }

    private String createRoomKey(String field) {
        String uid = getUserId();
        if (uid == null || uid.isEmpty()) uid = "_guest";
        return "create_room_" + field + "_" + uid;
    }

    public void saveCreateRoomDefaults(String title, String coverUrl) {
        android.content.SharedPreferences.Editor e = prefs.edit();
        if (title != null) e.putString(createRoomKey("title"), title);
        if (coverUrl != null) e.putString(createRoomKey("cover"), coverUrl);
        e.apply();
    }

    public void saveCreateRoomTitle(String title) {
        if (title == null) return;
        prefs.edit().putString(createRoomKey("title"), title).apply();
    }

    public void saveCreateRoomCover(String coverUrl) {
        if (coverUrl == null) return;
        prefs.edit().putString(createRoomKey("cover"), coverUrl).apply();
    }

    public String getCreateRoomTitle() {
        return prefs.getString(createRoomKey("title"), null);
    }

    public String getCreateRoomCoverUrl() {
        return prefs.getString(createRoomKey("cover"), null);
    }

    public void saveAccountSnapshot() {
        AuthDtos.UserDto u = getUser();
        if (u == null || u.username == null) return;
        String key = "acc_" + u.username;
        String blob = gson.toJson(new SavedAccount(getAccessToken(), getRefreshToken(), u));
        prefs.edit().putString(key, blob).apply();
        java.util.HashSet<String> set = new java.util.HashSet<>(prefs.getStringSet("saved_accounts", new java.util.HashSet<>()));
        set.add(u.username);
        prefs.edit().putStringSet("saved_accounts", set).apply();
    }

    public java.util.List<String> listSavedAccounts() {
        return new java.util.ArrayList<>(prefs.getStringSet("saved_accounts", new java.util.HashSet<>()));
    }

    public boolean switchToAccount(String username) {
        String blob = prefs.getString("acc_" + username, null);
        if (blob == null) return false;
        SavedAccount a = gson.fromJson(blob, SavedAccount.class);
        if (a == null || a.accessToken == null) return false;
        prefs.edit()
                .putString("access_token", a.accessToken)
                .putString("refresh_token", a.refreshToken)
                .putString("user_json", gson.toJson(a.user))
                .putString("user_id", a.user != null ? a.user.id : null)
                .commit();
        return true;
    }

    public void removeSavedAccount(String username) {
        if (username == null) return;
        java.util.HashSet<String> set = new java.util.HashSet<>(prefs.getStringSet("saved_accounts", new java.util.HashSet<>()));
        set.remove(username);
        prefs.edit().remove("acc_" + username).putStringSet("saved_accounts", set).apply();
    }

    private static class SavedAccount {
        String accessToken;
        String refreshToken;
        AuthDtos.UserDto user;
        SavedAccount(String accessToken, String refreshToken, AuthDtos.UserDto user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
        }
    }
}
