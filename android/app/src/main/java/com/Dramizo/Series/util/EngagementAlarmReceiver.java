package com.Dramizo.Series.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires local engagement notifications when Samsung/OEM kills WorkManager delays.
 */
public class EngagementAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        LocalEngagementScheduler.onAlarmFired(context.getApplicationContext());
    }
}
