package com.Dramizo.Series.util;

import android.util.Log;

import androidx.annotation.Nullable;

/**
 * High-signal game probe logs for live 15-min play sessions.
 * Filter: adb logcat -s GAME_PROBE
 */
public final class GameProbeLog {
    public static final String TAG = "GAME_PROBE";

    private GameProbeLog() {}

    public static void i(String event, @Nullable String detail) {
        Log.i(TAG, event + (detail == null || detail.isEmpty() ? "" : " | " + truncate(detail)));
    }

    public static void w(String event, @Nullable String detail) {
        Log.w(TAG, event + (detail == null || detail.isEmpty() ? "" : " | " + truncate(detail)));
    }

    public static void e(String event, @Nullable String detail) {
        Log.e(TAG, event + (detail == null || detail.isEmpty() ? "" : " | " + truncate(detail)));
    }

    public static void bridge(String iface, String method, @Nullable String args) {
        i("BRIDGE." + iface + "." + method, args);
    }

    public static void net(String direction, @Nullable String url) {
        i("NET." + direction, url);
    }

    public static void js(String level, @Nullable String msg) {
        i("JS." + level, msg);
    }

    public static void ws(String phase, @Nullable String detail) {
        i("WS." + phase, detail);
    }

    private static String truncate(@Nullable String s) {
        if (s == null) return "";
        return s.length() <= 1800 ? s : s.substring(0, 1800) + "…";
    }
}
