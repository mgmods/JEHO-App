package com.Dramizo.Series.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Encodes / parses in-app agency (family) join invites in private chat.
 * Format: {@code [[agency_invite|agencyId|name|logoUrl|code]]}
 * Code is always the last segment (survives missing middle fields).
 */
public final class AgencyInviteCodec {
    public static final String PREFIX = "[[agency_invite|";
    public static final String SUFFIX = "]]";

    private AgencyInviteCodec() {}

    public static String encode(
            String agencyId,
            @Nullable String name,
            @Nullable String logoUrl,
            @Nullable String code) {
        String id = sanitize(agencyId);
        String n = sanitize(name);
        if (n.isEmpty()) n = "وكالة";
        String logo = sanitize(logoUrl);
        String c = sanitizeCode(code);
        return PREFIX + id + "|" + n + "|" + logo + "|" + c + SUFFIX;
    }

    private static String sanitize(@Nullable String value) {
        if (value == null) return "";
        return value.trim()
                .replace('|', ' ')
                .replace('[', ' ')
                .replace(']', ' ');
    }

    private static String sanitizeCode(@Nullable String value) {
        if (value == null) return "";
        return value.trim()
                .toUpperCase(java.util.Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isAgencyInvite(@Nullable String content) {
        if (content == null) return false;
        String c = content.trim();
        // Tolerate trailing whitespace or minor noise after ]] for server-normalized bodies.
        int start = c.indexOf(PREFIX);
        if (start < 0) return false;
        return c.indexOf(SUFFIX, start + PREFIX.length()) > start;
    }

    @Nullable
    public static Parsed parse(@Nullable String content) {
        if (content == null) return null;
        String raw = content.trim();
        int start = raw.indexOf(PREFIX);
        if (start < 0) return null;
        int end = raw.indexOf(SUFFIX, start + PREFIX.length());
        if (end <= start) return null;
        String body = raw.substring(start + PREFIX.length(), end);
        String[] parts = body.split("\\|", -1);
        if (parts.length < 1) return null;
        String id = parts[0] != null ? parts[0].trim() : "";
        if (TextUtils.isEmpty(id)) return null;
        String name = parts.length > 1 ? parts[1].trim() : "";
        if (TextUtils.isEmpty(name)) name = "وكالة";
        String logo = parts.length > 2 ? parts[2].trim() : "";
        if (logo.isEmpty()) logo = null;
        // Prefer last segment as activation code (length-safe 8-char codes).
        String code = "";
        if (parts.length > 3) {
            code = parts[parts.length - 1] != null ? parts[parts.length - 1].trim() : "";
        }
        code = sanitizeCode(code);
        if (code.isEmpty()) code = null;
        return new Parsed(id, name, logo, code);
    }

    public static String previewLabel(@Nullable String content) {
        Parsed p = parse(content);
        if (p == null) return content != null ? content : "";
        return "🏠 " + p.name;
    }

    public static final class Parsed {
        public final String agencyId;
        public final String name;
        @Nullable public final String logoUrl;
        @Nullable public final String code;

        public Parsed(String agencyId, String name, @Nullable String logoUrl, @Nullable String code) {
            this.agencyId = agencyId;
            this.name = name;
            this.logoUrl = logoUrl;
            this.code = code;
        }
    }
}
