package com.Dramizo.Series.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;

import retrofit2.Response;

/** Sends a direct-message reply from the Android notification shade. */
public final class NotificationQuickReplyReceiver extends BroadcastReceiver {
    public static final String EXTRA_REPLY = "jeho_inline_reply";
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String ACTION_MARK_READ = "com.mgwa.chat.action.MARK_DM_READ";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        CharSequence reply = RemoteInput.getResultsFromIntent(intent) != null
                ? RemoteInput.getResultsFromIntent(intent).getCharSequence(EXTRA_REPLY) : null;
        String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        String text = reply != null ? reply.toString().trim() : "";
        boolean markRead = ACTION_MARK_READ.equals(intent.getAction());
        if (conversationId == null || conversationId.trim().isEmpty()
                || (!markRead && text.isEmpty())) return;

        BroadcastReceiver.PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        try {
            AppContainer container = ((AuraLiveApp) app).getContainer();
            container.getIoExecutor().execute(() -> {
                boolean sent = false;
                try {
                    if (markRead) {
                        // The messages endpoint records lastReadAt, clears unreadCount,
                        // and emits the standard read receipt without launching an Activity.
                        Response<ApiResponse<ChatDtos.MessageList>> response =
                                container.getChatApi().messages(conversationId, 1, 1).execute();
                        sent = response.isSuccessful() && response.body() != null
                                && response.body().success;
                    } else {
                        ChatDtos.SendMessageRequest request =
                                new ChatDtos.SendMessageRequest(text, "text", null);
                        Response<ApiResponse<ChatDtos.MessageDto>> response =
                                container.getChatApi().send(conversationId, request).execute();
                        sent = response.isSuccessful() && response.body() != null
                                && response.body().success && response.body().data != null;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (sent) {
                        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(app).cancel(notificationId);
                            AuraNotificationHelper.clearChatMessageHistory(app, notificationId);
                            AuraNotificationHelper.refreshMessageGroupSummary(app);
                        }
                    } else if (AuraLiveApp.getForegroundActivity() != null) {
                        android.app.Activity activity = AuraLiveApp.getForegroundActivity();
                        activity.runOnUiThread(() -> Toast.makeText(
                                activity, "تعذر إرسال الرد، حاول مرة أخرى", Toast.LENGTH_SHORT).show());
                    }
                    pending.finish();
                }
            });
        } catch (Exception e) {
            pending.finish();
        }
    }
}
