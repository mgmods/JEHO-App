package com.Dramizo.Series.util;

import com.Dramizo.Series.BuildConfig;

/**
 * JEHOO test build uses the Ghaymah-hosted JEHO service.
 * Uploaded media storage remains configured by the backend's existing primary-storage settings.
 */
public final class ApiOrigin {
    private static final String TEST_API_ORIGIN = "https://jeho-own-web-910481ae.hosted.cumin.dev";

    private ApiOrigin() {}

    public static String origin() {
        String configured = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        String value = configured.isEmpty() || configured.contains("YOUR-JEHO-OWN-API-HOST")
                ? TEST_API_ORIGIN
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
