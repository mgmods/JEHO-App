package com.Dramizo.Series.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Formats timestamps using the device locale / 12–24h preference. */
public final class DeviceTimeFormat {
    private DeviceTimeFormat() {}

    private static final String[] ISO = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
    };

    @NonNull
    public static String messageTime(@NonNull Context context, @Nullable String isoOrRaw) {
        Date date = parse(isoOrRaw);
        if (date == null) return isoOrRaw != null ? isoOrRaw : "";
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(context);
        return tf.format(date);
    }

    @NonNull
    public static String lastSeen(@NonNull Context context, @Nullable String isoOrRaw) {
        Date date = parse(isoOrRaw);
        if (date == null) return context.getString(com.Dramizo.Series.R.string.last_seen_recently);
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 2 * 60_000L) {
            return context.getString(com.Dramizo.Series.R.string.online_now);
        }
        DateFormat df;
        if (diff < 24 * 60 * 60_000L) {
            df = android.text.format.DateFormat.getTimeFormat(context);
            return context.getString(com.Dramizo.Series.R.string.last_seen_at, df.format(date));
        }
        df = android.text.format.DateFormat.getMediumDateFormat(context);
        DateFormat tf = android.text.format.DateFormat.getTimeFormat(context);
        return context.getString(com.Dramizo.Series.R.string.last_seen_at, df.format(date) + " · " + tf.format(date));
    }

    @Nullable
    public static Date parse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim();
        for (String pattern : ISO) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s);
            } catch (ParseException ignored) {
            }
        }
        try {
            return new Date(Long.parseLong(s));
        } catch (Exception ignored) {
            return null;
        }
    }
}
