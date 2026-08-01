package com.Dramizo.Series;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.friends.RequestsActivity;
import com.Dramizo.Series.presentation.createroom.CreateRoomActivity;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ActiveChatTracker;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraNotificationHelper;
import com.Dramizo.Series.util.NotificationRouter;
import com.Dramizo.Series.util.RoomSoundFx;
import com.Dramizo.Series.zego.ZegoEngineManager;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

public class AuraLiveApp extends Application {
    /** Unwrapped context — SharedPreferences must not use locale ConfigurationContext. */
    private static volatile Context prefsStorageBase;

    private AppContainer container;
    private static volatile Activity foregroundActivity;
    private static volatile int startedActivities;

    /** Always use for auth/session prefs (survives attachBaseContext locale wrap). */
    public static Context storageContext(Context any) {
        if (prefsStorageBase != null) return prefsStorageBase;
        if (any == null) return null;
        Context app = any.getApplicationContext();
        if (app instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) app).getBaseContext();
            if (base != null) return base;
        }
        return app;
    }

    public static Activity getForegroundActivity() {
        return foregroundActivity;
    }

    public static boolean isAppInForeground() {
        return startedActivities > 0;
    }

    @Override
    protected void attachBaseContext(Context base) {
        prefsStorageBase = base;
        // Language applied after container exists; use plain prefs fallback for early boot.
        String lang = "ar";
        try {
            lang = base.getSharedPreferences("auralive_lang", MODE_PRIVATE).getString("language", "ar");
        } catch (Exception ignored) {
        }
        super.attachBaseContext(com.Dramizo.Series.util.LocaleHelper.wrap(base, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        // Fetch runtime Zego client settings early (non-blocking).
        try {
            container.getIoExecutor().execute(() -> {
                ZegoEngineManager.getInstance().fetchAndApplyRemote(this, container.getConfigApi());
                AppFeatures.refresh(container);
            });
        } catch (Exception ignored) {
        }
        // Sync language helper prefs with secure session
        try {
            String lang = container.getSessionManager().getLanguage();
            getSharedPreferences("auralive_lang", MODE_PRIVATE).edit().putString("language", lang).apply();
            com.Dramizo.Series.util.LocaleHelper.wrap(this, lang);
            String tag = com.Dramizo.Series.util.LocaleHelper.normalizeTag(lang);
            AppCompatDelegate.setApplicationLocales(
                    "system".equals(tag)
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(tag));
        } catch (Exception ignored) {
        }
        createNotificationChannel();
        registerRealtimeNotifications();
        try {
            com.Dramizo.Series.util.InviteReferralHelper.captureInstallReferrer(this);
        } catch (Exception ignored) {
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                EdgeToEdgeHelper.apply(activity);
                if (activity.getClass().getSimpleName().equals("SplashActivity")) return;
                if (activity instanceof MainActivity
                        || activity instanceof VoiceRoomActivity
                        || activity instanceof CreateRoomActivity) {
                    return;
                }
                activity.getWindow().getDecorView().post(() ->
                        AppLoadingOverlay.showOnEntry(activity));
            }
            @Override public void onActivityStarted(Activity activity) {
                startedActivities++;
                // ThemedActivity pads contentRoot once in onPostCreate — skip global double-pad.
                if (activity instanceof ThemedActivity) return;
                // Skip shells that manage their own insets
                if (activity instanceof MainActivity
                        || activity instanceof VoiceRoomActivity
                        || activity instanceof ProfileActivity
                        || activity instanceof CreateRoomActivity
                        || activity instanceof RequestsActivity
                        || activity instanceof ChatConversationActivity
                        || activity instanceof LoginActivity) {
                    return;
                }
                View content = activity.findViewById(android.R.id.content);
                if (!(content instanceof ViewGroup)) return;
                View root = ((ViewGroup) content).getChildAt(0);
                if (root == null) return;
                Object tagged = root.getTag(R.id.tag_edge_padded);
                if (tagged != null) return;
                root.setTag(R.id.tag_edge_padded, Boolean.TRUE);
                EdgeToEdgeHelper.padSystemBars(root);
            }
            @Override public void onActivityResumed(Activity activity) {
                foregroundActivity = activity;
                View decor = activity.getWindow() != null
                        ? activity.getWindow().getDecorView()
                        : null;
                if (decor != null) {
                    decor.post(() ->
                            com.Dramizo.Series.util.RemoteTheme.applyMappedAssets(decor));
                }
            }
            @Override public void onActivityPaused(Activity activity) {
                if (foregroundActivity == activity) foregroundActivity = null;
            }
            @Override public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (foregroundActivity == activity) foregroundActivity = null;
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        try {
            container.getIoExecutor().execute(() -> {
                try {
                    ZegoEngineManager.getInstance().init(AuraLiveApp.this);
                } catch (Throwable t) {
                    android.util.Log.w("AuraLiveApp", "ZEGO init deferred: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "ZEGO init schedule failed: " + t.getMessage());
        }
        try {
            RoomSoundFx.init(this);
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "RoomSoundFx init skipped: " + t.getMessage());
        }
        try {
            FirebaseMessaging.getInstance().setAutoInitEnabled(true);
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> container.getSessionManager().saveFcmToken(token))
                    .addOnFailureListener(e -> android.util.Log.w("AuraLiveApp", "FCM token skipped: " + e.getMessage()));
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "Firebase not configured yet: " + t.getMessage());
        }
    }

    public AppContainer getContainer() {
        return container;
    }

    private void registerRealtimeNotifications() {
        RealtimeClient.getInstance().addUserListener(new RealtimeClient.UserListener() {
            @Override
            public void onUserEvent(String event, JsonObject payload) {
                if ("account:restricted".equals(event)) {
                    if (container != null) container.getSessionManager().clearAuth();
                    RealtimeClient.getInstance().disconnect();
                    Activity activity = foregroundActivity;
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() -> {
                            Intent login = new Intent(
                                    activity,
                                    com.Dramizo.Series.presentation.auth.LoginActivity.class);
                            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            activity.startActivity(login);
                            activity.finishAffinity();
                        });
                    }
                    return;
                }
                if ("account:reset".equals(event)) {
                    // Admin baseline wipe — refresh /me so wallet/VIP/frames clear immediately.
                    if (container == null) return;
                    container.getIoExecutor().execute(() -> {
                        try {
                            container.getUserRepository().getMe();
                        } catch (Exception ignored) {
                        }
                    });
                    return;
                }
                if ("celebration:toast".equals(event) && payload != null) {
                    Activity activity = foregroundActivity;
                    if (activity == null || activity.isFinishing()) return;
                    // Inside voice room: use room chat bubbles + official-news-style
                    // lucky strip — skip the global top toast over the room card.
                    if (activity instanceof VoiceRoomActivity) return;
                    String title = jsonString(payload, "title", "مبروك!");
                    String body = jsonString(payload, "body", "");
                    String avatar = jsonString(payload, "avatarUrl", null);
                    String badgeCandidate = jsonString(payload, "giftIconUrl", null);
                    if (badgeCandidate == null || badgeCandidate.isEmpty()) {
                        badgeCandidate = jsonString(payload, "gameIconUrl", null);
                    }
                    if (badgeCandidate == null || badgeCandidate.isEmpty()) {
                        badgeCandidate = jsonString(payload, "gameCoverUrl", null);
                    }
                    final String badge = badgeCandidate;
                    String dedupe = jsonString(payload, "id", body);
                    activity.runOnUiThread(() ->
                            com.Dramizo.Series.util.GlobalCelebrationToast.show(
                                    activity, title, body, avatar, badge, dedupe));
                    return;
                }
                if (!"notification:new".equals(event) || payload == null) return;
                Activity activity = foregroundActivity;
                if (activity == null || activity.isFinishing()) return;

                String title = jsonString(payload, "title", getString(R.string.app_name));
                String body = jsonString(payload, "body", "");
                String type = jsonString(payload, "type", "system");
                String notifRowId = jsonString(payload, "id", null);
                Map<String, Object> data = new HashMap<>();
                JsonElement rawData = payload.get("data");
                if (rawData != null && rawData.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry
                            : rawData.getAsJsonObject().entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value != null && value.isJsonPrimitive()) {
                            data.put(entry.getKey(), value.getAsString());
                        }
                    }
                }
                Object convObj = data.get("conversationId");
                if (convObj == null) convObj = data.get("conversation_id");
                String conversationId = convObj != null ? String.valueOf(convObj) : null;
                if (ActiveChatTracker.shouldMute(type, conversationId)) {
                    return;
                }
                if (ActiveChatTracker.isChatType(type)
                        && container != null
                        && container.getSessionManager().isMuteMessageNotifications()) {
                    return;
                }
                Intent intent = NotificationRouter.build(activity, type, data);
                if (conversationId != null && !conversationId.isEmpty()) {
                    intent.putExtra(
                            ChatConversationActivity.EXTRA_CONVERSATION_ID,
                            conversationId);
                }
                Object nameObj = data.get("senderName");
                if (nameObj == null) nameObj = data.get("peer_name");
                if (nameObj != null) {
                    intent.putExtra(ChatConversationActivity.EXTRA_TITLE, String.valueOf(nameObj));
                }
                Object avatarObj = data.get("avatarUrl");
                if (avatarObj == null) avatarObj = data.get("senderAvatarUrl");
                String avatarUrl = avatarObj != null ? String.valueOf(avatarObj) : null;
                if (avatarUrl != null) {
                    intent.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatarUrl);
                }
                int notifyId = conversationId != null && !conversationId.isEmpty()
                        ? ("chat:" + conversationId).hashCode()
                        : notificationIdKey(notifRowId);
                // Custom system notification while in chat list / party / any other screen.
                AuraNotificationHelper.show(
                        activity,
                        "auralive_default",
                        title,
                        body,
                        type,
                        intent,
                        notifyId,
                        avatarUrl);
                try {
                    Intent refresh = new Intent(
                            com.Dramizo.Series.presentation.messages.MessagesFragment
                                    .ACTION_OFFICIAL_NEWS_UPDATED);
                    refresh.setPackage(getPackageName());
                    sendBroadcast(refresh);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static int notificationIdKey(@Nullable String id) {
        if (id != null && !id.isEmpty()) return id.hashCode();
        return ("rt:" + System.currentTimeMillis()).hashCode();
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        try {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value.getAsString();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "auralive_default",
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.channel_description));
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
                NotificationChannel roomChannel = new NotificationChannel(
                        com.Dramizo.Series.service.VoiceRoomForegroundService.CHANNEL_ID,
                        "الغرفة الصوتية النشطة",
                        NotificationManager.IMPORTANCE_LOW);
                roomChannel.setDescription("إبقاء صوت الغرفة والرجوع إليها أو الخروج منها");
                roomChannel.setShowBadge(false);
                roomChannel.enableVibration(false);
                roomChannel.setSound(null, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    roomChannel.setAllowBubbles(true);
                }
                nm.createNotificationChannel(roomChannel);
                NotificationChannel bubbleChannel = new NotificationChannel(
                        com.Dramizo.Series.service.VoiceRoomForegroundService.BUBBLE_CHANNEL_ID,
                        "فقاعة الغرفة الصوتية",
                        NotificationManager.IMPORTANCE_DEFAULT);
                bubbleChannel.setDescription("فقاعة اختيارية للرجوع السريع إلى الغرفة");
                bubbleChannel.setShowBadge(false);
                bubbleChannel.enableVibration(false);
                bubbleChannel.setSound(null, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bubbleChannel.setAllowBubbles(true);
                }
                nm.createNotificationChannel(bubbleChannel);
            }
        }
    }
}
