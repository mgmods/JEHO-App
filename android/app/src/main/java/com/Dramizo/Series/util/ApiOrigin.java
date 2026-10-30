package com.Dramizo.Series.util;

/**
 * JEHO CHAT connects only to its dedicated JEHO-OWN Cumin backend.
 * The admin dashboard is served by the same origin under /admin/.
 *
 * A stale BuildConfig override must never redirect the installed app to a
 * different JEHO copy or an unrelated hosting provider.
 */
public final class ApiOrigin {
    private static final String CANONICAL_API = "https://jeho-own-web-910481ae.hosted.cumin.dev";

    private ApiOrigin() {}

    public static String origin() {
        String configured = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        if (!configured.isEmpty()) {
            while (configured.endsWith("/")) {
                configured = configured.substring(0, configured.length() - 1);
            }
            if (configured.endsWith("/api/v1")) {
                configured = configured.substring(0, configured.length() - "/api/v1".length());
            }
            // Accept only the canonical JEHO-OWN host. Ignore any old/foreign
            // URL left in local.properties or a stale CI build parameter.
            if (CANONICAL_API.equalsIgnoreCase(configured)) {
                return CANONICAL_API;
            }
        }
        return CANONICAL_API;
    }

    public static String apiV1() {
        return origin() + "/api/v1/";
    }
}
