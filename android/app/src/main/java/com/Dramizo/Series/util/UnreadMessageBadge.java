package com.Dramizo.Series.util;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;

import retrofit2.Response;

/** Keeps the inbox unread total shared by the main tabs and the voice-room message shortcut. */
public final class UnreadMessageBadge {
    public static final String ACTION_CHANGED = "com.mgwa.chat.action.UNREAD_MESSAGES_CHANGED";
    public static final String EXTRA_COUNT = "unread_count";
    private static final String PREFS = "jeho_unread_badge";
    private static final String KEY_COUNT = "count";
    private UnreadMessageBadge() {}

    public static int current(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_COUNT, 0);
    }

    public static void refresh(@NonNull Context context) {
        Context app = context.getApplicationContext();
        try {
            AppContainer c = ((AuraLiveApp) app).getContainer();
            c.getIoExecutor().execute(() -> {
                int total = 0;
                try {
                    Response<ApiResponse<ChatDtos.ConversationList>> response =
                            c.getChatApi().conversations().execute();
                    if (response.isSuccessful() && response.body() != null
                            && response.body().success && response.body().data != null
                            && response.body().data.items != null) {
                        for (ChatDtos.ConversationDto item : response.body().data.items) {
                            if (item != null) total += Math.max(0, item.unreadCount);
                        }
                    } else {
                        return;
                    }
                } catch (Exception ignored) { return; }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, total).apply();
                Intent changed = new Intent(ACTION_CHANGED).setPackage(app.getPackageName());
                changed.putExtra(EXTRA_COUNT, total);
                app.sendBroadcast(changed);
            });
        } catch (Exception ignored) {}
    }
}
