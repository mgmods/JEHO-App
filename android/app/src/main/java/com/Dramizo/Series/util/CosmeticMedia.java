package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Detects playable cosmetic media (GIF / WebP / MP4 / SVGA) and rejects HTML/Lottie JSON. */
public final class CosmeticMedia {
    private CosmeticMedia() {}

    public enum Kind { NONE, IMAGE, GIF, VIDEO, SVGA }

    @Nullable
    public static String playableUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String u = url.trim();
        String lower = stripQuery(u).toLowerCase(Locale.US);
        if (lower.contains("runtime.html") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            return null;
        }
        if (lower.endsWith(".json")) return null;
        if (lower.startsWith("native://")) return null;
        Kind k = kind(u);
        return k == Kind.NONE ? null : u;
    }

    public static Kind kind(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return Kind.NONE;
        String lower = stripQuery(url.trim()).toLowerCase(Locale.US);
        if (lower.contains("runtime.html") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            return Kind.NONE;
        }
        if (lower.endsWith(".json")) return Kind.NONE;
        if (lower.endsWith(".svga")) return Kind.SVGA;
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) {
            return Kind.VIDEO;
        }
        if (lower.endsWith(".gif")) return Kind.GIF;
        if (lower.endsWith(".webp") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")) {
            return Kind.IMAGE;
        }
        // Unknown extension from CDN — allow Glide image attempt.
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/")) {
            return Kind.IMAGE;
        }
        return Kind.NONE;
    }

    public static boolean isAnimatedWear(@Nullable String url) {
        Kind k = kind(url);
        return k == Kind.GIF || k == Kind.VIDEO || k == Kind.SVGA
                || (url != null && stripQuery(url).toLowerCase(Locale.US).endsWith(".webp"));
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }
}
