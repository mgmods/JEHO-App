package com.Dramizo.Series.fcm;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.util.ActiveChatTracker;
import com.Dramizo.Series.util.AuraNotificationHelper;
import com.Dramizo.Series.util.NotificationRouter;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class AuraMessagingService extends FirebaseMessagingService {
    private static final String TAG = "AuraMessagingService";
    private static final String CHANNEL_ID = "auralive_default";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "FCM token refreshed");
        AuraLiveApp app = (AuraLiveApp) getApplication();
        AppContainer container = app.getContainer();
        container.getSessionManager().saveFcmToken(token);
        container.getIoExecutor().execute(() ->
                container.getNotificationRepository().registerDevice(token, "android")
        );
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();
        String title = getString(R.string.app_name);
        String body = "";

        // Prefer data payload (custom layouts). notification payload is legacy fallback.
        if (data.containsKey("title") && data.get("title") != null && !data.get("title").isEmpty()) {
            title = data.get("title");
        } else if (message.getNotification() != null && message.getNotification().getTitle() != null) {
            title = message.getNotification().getTitle();
        }
        if (data.containsKey("body") && data.get("body") != null && !data.get("body").isEmpty()) {
            body = data.get("body");
        } else if (message.getNotification() != null && message.getNotification().getBody() != null) {
            body = message.getNotification().getBody();
        }

        String type = data.containsKey("type") && data.get("type") != null ? data.get("type") : "system";
        showNotification(title, body, type, data);
    }

    private void showNotification(String title, String body, String type, Map<String, String> data) {
        String conversationId = firstNonEmpty(data, "conversationId", "conversation_id");
        if (ActiveChatTracker.shouldMute(type, conversationId)) {
            return;
        }
        if (ActiveChatTracker.isChatType(type)) {
            try {
                AppContainer container = ((AuraLiveApp) getApplication()).getContainer();
                if (container.getSessionManager().isMuteMessageNotifications()) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        Intent intent = NotificationRouter.build(this, type, data);
        // Guarantee chat extras even if router keys differ from FCM payload.
        if (conversationId != null && !conversationId.isEmpty()) {
            intent.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversationId);
        }
        String peerName = firstNonEmpty(data, "senderName", "peer_name", "peerName", "name", "title");
        if (peerName != null) {
            intent.putExtra(ChatConversationActivity.EXTRA_TITLE, peerName);
        }
        String avatar = firstNonEmpty(data,
                "avatarUrl", "senderAvatarUrl", "imageUrl", "senderAvatar", "avatar");
        if (avatar != null) {
            intent.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatar);
        }
        String peerId = firstNonEmpty(data, "peer_id", "peerId", "senderId", "user_id", "userId");
        if (peerId != null) {
            intent.putExtra(ChatConversationActivity.EXTRA_PEER_ID, peerId);
        }

        int notificationId = stableNotificationId(type, conversationId, data);
        // Always use custom system notification when outside that chat —
        // chat list, party room, background, and other destinations.
        AuraNotificationHelper.show(
                this, CHANNEL_ID, title, body, type, intent, notificationId, avatar);
        // Refresh Official News badge on Messages tab when a general notice arrives.
        try {
            android.content.Intent refresh =
                    new android.content.Intent(
                            com.Dramizo.Series.presentation.messages.MessagesFragment
                                    .ACTION_OFFICIAL_NEWS_UPDATED);
            refresh.setPackage(getPackageName());
            sendBroadcast(refresh);
        } catch (Exception ignored) {
        }
    }

    private static int stableNotificationId(String type, String conversationId, Map<String, String> data) {
        if (conversationId != null && !conversationId.isEmpty()) {
            return ("chat:" + conversationId).hashCode();
        }
        if (data != null) {
            String nid = data.get("notificationId");
            if (nid != null && !nid.isEmpty()) return nid.hashCode();
        }
        String key = (type != null ? type : "system") + ":" + System.currentTimeMillis();
        return key.hashCode();
    }

    private static String firstNonEmpty(Map<String, String> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            String v = data.get(key);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}
