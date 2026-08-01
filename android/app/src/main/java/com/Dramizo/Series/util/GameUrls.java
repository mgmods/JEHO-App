package com.Dramizo.Series.util;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Locale;

/** Resolves casual HTML games to bundled assets (offline-safe) with HTTPS fallback. */
public final class GameUrls {
    public static final String ASSET_BASE = "file:///android_asset/games/";

    private GameUrls() {}

    public static String remoteBase() {
        return ApiOrigin.origin() + "/games/";
    }

    public static String asset(String fileName) {
        return ASSET_BASE + fileName;
    }

    public static String remote(String fileName) {
        return remoteBase() + fileName;
    }

    /** Prefer bundled asset when present; otherwise keep remote URL. */
    public static String resolve(Context context, @Nullable String urlOrFile) {
        if (urlOrFile == null || urlOrFile.trim().isEmpty()) {
            return "";
        }
        String raw = urlOrFile.trim();
        String file = extractFileName(raw);
        if (file != null && assetExists(context, file)) {
            return asset(file);
        }
        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("file://")) {
            return raw;
        }
        return preferAsset(context, raw.contains(".") ? raw : raw + ".html");
    }

    public static String preferAsset(Context context, String fileName) {
        if (assetExists(context, fileName)) return asset(fileName);
        return remote(fileName);
    }

    @Nullable
    private static String extractFileName(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isEmpty()) return null;
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return name;
        return null;
    }

    private static boolean assetExists(Context context, String fileName) {
        AssetManager am = context.getAssets();
        try {
            am.open("games/" + fileName).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
