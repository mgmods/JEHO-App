package com.Dramizo.Series.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

/** Dismisses a notification from a RemoteViews action button. */
public final class NotificationDismissReceiver extends BroadcastReceiver {
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (id < 0) return;
        try {
            NotificationManagerCompat.from(context.getApplicationContext()).cancel(id);
        } catch (Exception ignored) {
        }
    }
}
