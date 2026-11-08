package com.Dramizo.Series.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

/** Dismisses a notification from a RemoteViews action button. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String ACTION_DISMISS = "com.mgwa.chat.action.DISMISS_MESSAGE_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (id == -1) return;
        try {
            Context app = context.getApplicationContext();
            NotificationManagerCompat.from(app).cancel(id);
            AuraNotificationHelper.refreshMessageGroupSummary(app);
        } catch (Exception ignored) {
        }
    }
}
