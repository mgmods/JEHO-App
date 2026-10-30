package com.Dramizo.Series.util;

/**
 * JEHO CHAT connects only to its dedicated JEHO-OWN Cumin backend.
 * The admin dashboard is served by the same origin under /admin/.
 *
 * The API host is intentionally fixed so stale BuildConfig/local.properties
 * values cannot redirect the installed app to another JEHO copy or host.
 */
public final class ApiOrigin {
    private static final String CANONICAL_API = "https://jeho-own-web-910481ae.hosted.cumin.dev";

    private ApiOrigin() {}

    public static String origin() {
        return CANONICAL_API;
    }

    public static String apiV1() {
        return origin() + "/api/v1/";
    }
}
