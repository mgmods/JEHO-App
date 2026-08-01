package com.Dramizo.Series.util;

import com.Dramizo.Series.R;

import java.util.Locale;

/**
 * Seat reaction stickers:
 * - e01–e26: existing animated GIFs under assets/emoji/
 * - e27–e74: Mikoo animated WebP pack under assets/emoji/
 */
public final class SeatReactionEmojis {
    private SeatReactionEmojis() {}

    /** Shared fallback drawable (assets load the real GIF/WebP). */
    public static final int FALLBACK_DRAWABLE = R.drawable.emoji_reaction_fallback;

    /** Inclusive range — keep in sync with assets/emoji and realtime.gateway. */
    public static final int MIN_INDEX = 1;
    public static final int MAX_INDEX = 74;

    public static final String[] KEYS = buildKeys(MIN_INDEX, MAX_INDEX);

    /** Same length as KEYS — every slot uses the shared fallback (asset is source of truth). */
    public static final int[] DRAWABLES = buildDrawables(KEYS.length);

    private static String[] buildKeys(int min, int max) {
        String[] keys = new String[Math.max(0, max - min + 1)];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = String.format(Locale.US, "e%02d", min + i);
        }
        return keys;
    }

    private static int[] buildDrawables(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = FALLBACK_DRAWABLE;
        return out;
    }

    public static boolean isAllowed(String key) {
        return indexOf(key) >= 0;
    }

    public static int indexOf(String key) {
        if (key == null || key.isEmpty()) return -1;
        String k = key.trim().toLowerCase(Locale.US);
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(k)) return i;
        }
        return -1;
    }

    public static int drawableForKey(String key) {
        return isAllowed(key) ? FALLBACK_DRAWABLE : 0;
    }

    /** Relative path inside assets/, e.g. emoji/e01.gif or emoji/e27.webp */
    public static String assetPathForKey(String key) {
        int i = indexOf(key);
        if (i < 0) return null;
        int num = MIN_INDEX + i;
        // Original pack is GIF; Mikoo additions are animated WebP.
        String ext = num <= 26 ? "gif" : "webp";
        return "emoji/" + KEYS[i] + "." + ext;
    }

    public static String assetUriForKey(String key) {
        String path = assetPathForKey(key);
        return path == null ? null : "file:///android_asset/" + path;
    }

    /** True when the asset is animated WebP (Mikoo pack). */
    public static boolean isWebpKey(String key) {
        int i = indexOf(key);
        return i >= 0 && (MIN_INDEX + i) > 26;
    }
}
