package com.Dramizo.Series.util;

import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Nullable;

/** Deduped toasts — rate-limit (429) must not flood the UI or look like a hard crash. */
public final class ErrorToasts {
    private static long lastRateLimitToastAt;

    private ErrorToasts() {}

    public static void show(@Nullable Context context, @Nullable String error) {
        if (context == null || error == null || error.trim().isEmpty()) return;
        if (ApiCall.isRateLimited(error)) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastRateLimitToastAt < 45_000L) return;
            lastRateLimitToastAt = now;
            Toast.makeText(context,
                    "الخادم مشغول قليلاً، انتظر لحظات",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
    }
}
