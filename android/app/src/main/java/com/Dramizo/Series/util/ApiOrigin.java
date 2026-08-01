package com.Dramizo.Series.util;

/**
 * Runtime host resolution — avoids a single plain "https://api..." string in BuildConfig.
 * This is obfuscation of the constant only; real security is JWT + server checks.
 */
public final class ApiOrigin {
    private static final int K = 0x5A;

    // XOR-encoded "https://api.adnova.bbs.tr"
    private static final byte[] ORIGIN = new byte[] {
            0x32, 0x2e, 0x2e, 0x2a, 0x29, 0x60, 0x75, 0x75,
            0x3b, 0x2a, 0x33, 0x74, 0x3b, 0x3e, 0x34, 0x35,
            0x2c, 0x3b, 0x74, 0x38, 0x38, 0x29, 0x74, 0x2e,
            0x28
    };

    private ApiOrigin() {}

    private static String decode(byte[] enc) {
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            out[i] = (char) ((enc[i] & 0xff) ^ K);
        }
        return new String(out);
    }

    /** https://api.adnova.bbs.tr */
    public static String origin() {
        return decode(ORIGIN);
    }

    /** https://api.adnova.bbs.tr/api/v1/ */
    public static String apiV1() {
        return origin() + "/api/v1/";
    }
}
