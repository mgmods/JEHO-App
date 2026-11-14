package com.Dramizo.Series.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Silent media cache sync for static CDN assets (game covers, packages, banners…).
 *
 * <p>Problem: Glide + OkHttp cache image URLs forever — replacing a file at the same
 * path on the server never reaches the device. Solution:</p>
 * <ul>
 *   <li>Append / refresh {@code ?v=} with a server epoch ({@code mediaAssetEpoch}).</li>
 *   <li>When the epoch changes, wipe Glide disk + OkHttp image caches in the background.</li>
 * </ul>
 */
public final class MediaAssetSync {
    /** Fallback when server has not sent an epoch yet (bump when shipping new covers). */
    public static final String FALLBACK_EPOCH = "20260807coversJpg1";

    private static final String PREFS = "media_asset_sync";
    private static final String KEY_EPOCH = "epoch";
    private static final AtomicBoolean wiping = new AtomicBoolean(false);
    private static volatile String liveEpoch = FALLBACK_EPOCH;

    private MediaAssetSync() {}

    public static String epoch() {
        return liveEpoch != null && !liveEpoch.isEmpty() ? liveEpoch : FALLBACK_EPOCH;
    }

    /** Call on process start (before first image load). */
    public static void hydrate(@Nullable Context any) {
        try {
            Context c = storage(any);
            if (c == null) return;
            String saved = prefs(c).getString(KEY_EPOCH, FALLBACK_EPOCH);
            if (saved != null && !saved.isEmpty()) liveEpoch = saved.trim();
        } catch (Exception ignored) {
        }
    }

    /**
     * Apply epoch from {@code GET /config/features} (silent).
     * If changed → wipe image disks so next loads hit the network with the new bust.
     */
    public static void applyServerEpoch(@Nullable Context any, @Nullable String serverEpoch) {
        if (serverEpoch == null) return;
        String ep = serverEpoch.trim();
        if (ep.isEmpty()) return;
        Context app = storage(any);
        if (app == null) return;

        String prev = prefs(app).getString(KEY_EPOCH, "");
        liveEpoch = ep;
        if (ep.equals(prev)) return;

        prefs(app).edit().putString(KEY_EPOCH, ep).apply();
        // New bust is enough for most URLs; also wipe to beat older keys without v=.
        wipeImageCachesAsync(app);
        RemoteNavIcons.invalidateCaches();
    }

    /** Force-wipe without changing epoch (rare admin/debug). */
    public static void wipeImageCachesAsync(@Nullable Context any) {
        final Context app = storage(any);
        if (app == null) return;
        if (!wiping.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                try {
                    Glide.get(app).clearDiskCache();
                } catch (Exception ignored) {
                }
                deleteRecursive(new File(app.getCacheDir(), "glide_http"));
                deleteRecursive(new File(app.getCacheDir(), "glide_images"));
            } finally {
                wiping.set(false);
            }
        }, "media-asset-wipe").start();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Glide.get(app).clearMemory();
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Ensure static asset URLs carry the live epoch so content swaps load without APK rebuild.
     * Does not touch user uploads ({@code /uploads/}) or arbitrary http CDN user avatars.
     */
    @Nullable
    public static String bust(@Nullable String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return pathOrUrl;
        String abs = AssetCatalog.absoluteUrl(pathOrUrl);
        if (abs == null || abs.isEmpty()) return pathOrUrl;
        if (!shouldBust(abs)) return abs;
        return replaceOrAppendV(abs, epoch());
    }

    /** Optimized JPEG thumb (~50–70KB vs 600KB+ PNG). Always current epoch. */
    @NonNull
    public static String mikooCoverUrl(@NonNull String gameId) {
        String id = gameId.trim();
        return ApiOrigin.origin() + "/games/mikoo/covers/" + id + ".jpg?v=" + epoch();
    }

    /** Heavy PNG original if JPEG missing on CDN. */
    @NonNull
    public static String mikooCoverUrlPng(@NonNull String gameId) {
        String id = gameId.trim();
        return ApiOrigin.origin() + "/games/mikoo/covers/" + id + ".png?v=" + epoch();
    }

    /**
     * True when the URL is the built-in package cover for this game id
     * ({@code /games/mikoo/covers/{id}.jpg|png…}), not a custom dashboard upload.
     */
    public static boolean isPackageDefaultCover(@Nullable String pathOrUrl, @Nullable String gameId) {
        if (pathOrUrl == null || pathOrUrl.isEmpty() || gameId == null || gameId.isEmpty()) {
            return false;
        }
        String path = pathOrUrl;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        path = path.toLowerCase(Locale.US);
        String id = gameId.trim().toLowerCase(Locale.US);
        if (!path.contains("/games/mikoo/covers/")) return false;
        return path.endsWith("/games/mikoo/covers/" + id + ".jpg")
                || path.endsWith("/games/mikoo/covers/" + id + ".jpeg")
                || path.endsWith("/games/mikoo/covers/" + id + ".png")
                || path.endsWith("/games/mikoo/covers/" + id + ".webp");
    }

    public static RequestOptions requestOptions() {
        return new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .signature(new ObjectKey("media-epoch:" + epoch()));
    }

    /** Catalog / room dock: decode only tile size so Glide does not store huge bitmaps. */
    public static RequestOptions tileOptions(int sizePx) {
        int s = Math.max(64, sizePx);
        return requestOptions().override(s, s).centerCrop();
    }

    /** Load remote static art with epoch signature (game covers / package icons). */
    public static void loadInto(
            @NonNull ImageView view,
            @Nullable String pathOrUrl,
            @DrawableRes int placeholder) {
        loadInto(view, pathOrUrl, placeholder, 0);
    }

    public static void loadInto(
            @NonNull ImageView view,
            @Nullable String pathOrUrl,
            @DrawableRes int placeholder,
            int overridePx) {
        String url = bust(pathOrUrl);
        // Prefer optimized jpg for mikoo covers if a stale .png URL is still in path.
        if (url != null && url.contains("/games/mikoo/covers/") && url.contains(".png")) {
            url = url.replace(".png", ".jpg");
        }
        Object prev = view.getTag(R.id.tag_image_url);
        String tag = url != null ? (url + "#" + epoch() + "#" + overridePx) : null;
        if (tag != null && tag.equals(prev) && view.getDrawable() != null) {
            return;
        }
        if (tag != null) view.setTag(R.id.tag_image_url, tag);
        try {
            if (url == null || url.isEmpty()) {
                view.setImageResource(placeholder);
                return;
            }
            RequestOptions opts = overridePx > 0 ? tileOptions(overridePx) : requestOptions();
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> req =
                    Glide.with(view)
                            .load(url)
                            .apply(opts)
                            .placeholder(placeholder)
                            .dontAnimate();
            if (url.contains("/games/mikoo/covers/") && url.contains(".jpg")) {
                String png = url.replace(".jpg", ".png");
                req = req.error(Glide.with(view)
                        .load(png)
                        .apply(opts)
                        .placeholder(placeholder)
                        .error(placeholder));
            } else {
                req = req.error(placeholder);
            }
            req.into(view);
        } catch (Exception e) {
            view.setImageResource(placeholder);
        }
    }

    private static boolean shouldBust(@NonNull String url) {
        String u = url.toLowerCase(Locale.US);
        if (u.contains("/uploads/")) return false;
        if (u.contains("/avatar")) return false;
        return u.contains("/games/mikoo/covers/")
                || u.contains("/games/mikoo/") && u.contains("splash")
                || u.contains("/assets/")
                || u.contains("/banners/")
                || u.contains("/mall/")
                || u.contains("/icons/")
                || u.contains("/cosmetics/")
                || u.contains("/nav")
                || u.contains("nav-icon");
    }

    @NonNull
    private static String replaceOrAppendV(@NonNull String url, @NonNull String v) {
        // Strip existing v= query (keep other params).
        try {
            int q = url.indexOf('?');
            if (q < 0) return url + "?v=" + v;
            String base = url.substring(0, q);
            String query = url.substring(q + 1);
            StringBuilder kept = new StringBuilder();
            for (String part : query.split("&")) {
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                String key = eq >= 0 ? part.substring(0, eq) : part;
                if ("v".equalsIgnoreCase(key) || "ver".equalsIgnoreCase(key)
                        || "version".equalsIgnoreCase(key) || "_".equals(key)
                        || "t".equalsIgnoreCase(key)) {
                    continue;
                }
                if (kept.length() > 0) kept.append('&');
                kept.append(part);
            }
            if (kept.length() > 0) {
                return base + "?" + kept + "&v=" + v;
            }
            return base + "?v=" + v;
        } catch (Exception e) {
            return url;
        }
    }

    private static Context storage(@Nullable Context any) {
        if (any == null) return null;
        try {
            return AuraLiveApp.storageContext(any.getApplicationContext());
        } catch (Exception e) {
            return any.getApplicationContext();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void deleteRecursive(@Nullable File f) {
        if (f == null || !f.exists()) return;
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File k : kids) deleteRecursive(k);
                }
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignored) {
        }
    }
}
