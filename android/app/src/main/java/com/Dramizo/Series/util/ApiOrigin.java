package com.Dramizo.Series.util;

import com.Dramizo.Series.BuildConfig;

/**
 * JEHO CHAT's canonical API is the isolated JEHO-OWN backend deployed on the isolated JEHO-OWN Cumin service.
 * The admin dashboard is served by the same backend host under /admin/.
 */
public final class ApiOrigin {
    private static final String CANONICAL_API = "https://jeho-own-web-910481ae.hosted.cumin.dev";

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
