package com.Dramizo.Series.util;

/**
 * JEHO CHAT connects only to its dedicated Ghaymah/Cumin Cloud backend.
 * The admin dashboard and Android API share the same service origin.
 * Keep this canonical origin aligned with the Ghaymah deployment.
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
