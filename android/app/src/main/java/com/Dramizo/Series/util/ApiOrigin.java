package com.Dramizo.Series.util;

import com.Dramizo.Series.BuildConfig;

/**
 * JEHO-OWN uses the same production API/server as the canonical JEHO-CHAT source.
 * A build-time override is still supported for development, but production falls
 * back to the canonical server instead of the isolated Render/Supabase instance.
 */
public final class ApiOrigin {
    private static final String CANONICAL_API = "https://api.adnova.bbs.tr";

    private ApiOrigin() {}

    public static String origin() {
        String configured = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        String value = configured.isEmpty() || configured.contains("YOUR-JEHO-OWN-API-HOST")
                ? CANONICAL_API
                : configured;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/api/v1")) {
            value = value.substring(0, value.length() - "/api/v1".length());
        }
        return value;
    }

    public static String apiV1() {
        return origin() + "/api/v1/";
    }
}
