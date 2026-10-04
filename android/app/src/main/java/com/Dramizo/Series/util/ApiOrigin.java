package com.Dramizo.Series.util;

import com.Dramizo.Series.BuildConfig;

/**
 * Resolves the API URL for this build.
 *
 * Configure API_BASE_URL through the Android build configuration for the
 * independent JEHO-OWN backend. There is deliberately no fallback to the
 * previous production server.
 */
public final class ApiOrigin {
    private static final String UNCONFIGURED_API = "https://YOUR-JEHO-OWN-API-HOST";

    private ApiOrigin() {}

    public static String origin() {
        String configured = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        String value = configured.isEmpty() ? UNCONFIGURED_API : configured;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/api/v1")) {
            value = value.substring(0, value.length() - "/api/v1".length());
        }
        return value;
    }

    public static String apiV1() {
        String configured = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        if (!configured.isEmpty()) {
            while (configured.endsWith("/")) {
                configured = configured.substring(0, configured.length() - 1);
            }
            return configured.endsWith("/api/v1") ? configured + "/" : configured + "/api/v1/";
        }
        return UNCONFIGURED_API + "/api/v1/";
    }
}
