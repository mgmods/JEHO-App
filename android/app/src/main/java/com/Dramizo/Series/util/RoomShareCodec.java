package com.Dramizo.Series.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

/** Encodes / parses in-app room share messages between chats. */
public final class RoomShareCodec {
    public static final String PREFIX = "[[room_share|";
    public static final String SUFFIX = "]]";

    private RoomShareCodec() {}

    public static String encode(String roomId, @Nullable String title) {
        return encode(roomId, title, null);
    }

    public static String encode(String roomId, @Nullable String title, @Nullable String coverUrl) {
        String id = roomId != null ? roomId.trim() : "";
        String t = sanitize(title);
        if (t.isEmpty()) t = "غرفة صوتية";
        String cover = sanitize(coverUrl);
        if (cover.isEmpty()) {
            return PREFIX + id + "|" + t + SUFFIX;
        }
        return PREFIX + id + "|" + t + "|" + cover + SUFFIX;
    }

    private static String sanitize(@Nullable String value) {
        if (value == null) return "";
        return value.trim()
                .replace('|', ' ')
                .replace('[', ' ')
                .replace(']', ' ');
    }

    public static boolean isRoomShare(@Nullable String content) {
        if (content == null) return false;
        String c = content.trim();
        return c.startsWith(PREFIX) && c.endsWith(SUFFIX);
    }

    @Nullable
    public static String roomIdOf(@Nullable String content) {
        Parsed p = parse(content);
        return p != null ? p.roomId : null;
    }

    @Nullable
    public static String titleOf(@Nullable String content) {
        Parsed p = parse(content);
        return p != null ? p.title : null;
    }

    @Nullable
    public static Parsed parse(@Nullable String content) {
        if (!isRoomShare(content)) return null;
        String trimmed = content.trim();
        String body = trimmed.substring(PREFIX.length(), trimmed.length() - SUFFIX.length());
        int first = body.indexOf('|');
        if (first <= 0) return null;
        String id = body.substring(0, first).trim();
        if (TextUtils.isEmpty(id)) return null;
        String rest = body.substring(first + 1);
        int second = rest.indexOf('|');
        String title;
        String coverUrl = null;
        if (second < 0) {
            title = rest.trim();
        } else {
            title = rest.substring(0, second).trim();
            coverUrl = rest.substring(second + 1).trim();
            if (coverUrl.isEmpty()) coverUrl = null;
        }
        if (TextUtils.isEmpty(title)) title = "غرفة صوتية";
        return new Parsed(id, title, coverUrl);
    }

    /** Friendly preview for conversation lists. */
    public static String previewLabel(@Nullable String content) {
        Parsed p = parse(content);
        if (p == null) return content != null ? content : "";
        return "🎙️ " + p.title;
    }

    public static final class Parsed {
        public final String roomId;
        public final String title;
        @Nullable public final String coverUrl;

        public Parsed(String roomId, String title) {
            this(roomId, title, null);
        }

        public Parsed(String roomId, String title, @Nullable String coverUrl) {
            this.roomId = roomId;
            this.title = title;
            this.coverUrl = coverUrl;
        }
    }
}
