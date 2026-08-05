package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;

import java.util.Locale;

/** Platform staff helpers (not agency manager). */
public final class StaffRoleHelper {
    private StaffRoleHelper() {}

    public static final String SUPER = "super";
    public static final String MANAGER = "manager";
    public static final String NONE = "none";

    public static String normalize(@Nullable AuthDtos.UserDto user) {
        if (user == null) return NONE;
        if (user.isAdmin) return SUPER;
        return normalize(user.staffRole);
    }

    public static String normalize(@Nullable String raw) {
        if (raw == null) return NONE;
        String r = raw.trim().toLowerCase(Locale.US);
        if (r.isEmpty() || "none".equals(r) || "null".equals(r)) return NONE;
        if ("super".equals(r) || "super_admin".equals(r) || "superadmin".equals(r)
                || "admin".equals(r)) {
            return SUPER;
        }
        if ("manager".equals(r) || "moderator".equals(r) || "mod".equals(r)) {
            return MANAGER;
        }
        return NONE;
    }

    public static boolean isStaff(@Nullable AuthDtos.UserDto user) {
        String r = normalize(user);
        return SUPER.equals(r) || MANAGER.equals(r);
    }

    public static boolean isSuper(@Nullable AuthDtos.UserDto user) {
        return SUPER.equals(normalize(user));
    }

    public static boolean isManager(@Nullable AuthDtos.UserDto user) {
        return MANAGER.equals(normalize(user));
    }

    /** Short label under the display name. */
    public static String badgeAr(@Nullable AuthDtos.UserDto user) {
        String r = normalize(user);
        if (SUPER.equals(r)) return "سوبر أدمن";
        if (MANAGER.equals(r)) return "مانجر";
        return "";
    }

    /** One-line powers summary for profile. */
    public static String powersAr(@Nullable AuthDtos.UserDto user) {
        String r = normalize(user);
        if (SUPER.equals(r)) {
            return "صلاحيات سوبر أدمن كاملة: إدارة الغرف كلها (كتم · طرد · حظر · إعدادات · موسيقى · ألعاب) + لوحة الإدارة على المتصفح (لك وحدك — لا تُمنح لأي شخص عادي أو مانجر).";
        }
        if (MANAGER.equals(r)) {
            return "صلاحيات مانجر داخل الغرف فقط: كتم · طرد · حظر · مقاعد · دعوات. بدون لوحة الإدارة على المتصفح وبدون إعدادات الغرفة الكاملة.";
        }
        return "";
    }
}
