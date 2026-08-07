package com.Dramizo.Series.util;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.bumptech.glide.Glide;
import com.caverock.androidsvg.SVG;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom-nav icons from dashboard ({@code GET /config/nav-icons}).
 * Config is stored in {@link EncryptedFeatureCache}.
 * Supports remote PNG / JPG / WEBP (Glide) and SVG (androidsvg — same engine as
 * {@code assets/icons/ic_home_tab_*.svg}). Empty URL → APK AssetIcons fallback.
 */
public final class RemoteNavIcons {
    public static final String TAB_PARTY = "party";
    public static final String TAB_DRAMA = "drama";
    public static final String TAB_GAMES = "games";
    public static final String TAB_CHAT = "chat";
    public static final String TAB_ME = "me";

    private static volatile MiscDtos.NavIconsDto cached;
    private static final ConcurrentHashMap<String, Drawable> SVG_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService SVG_IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RemoteNavIcons() {}

    /** Drop memory caches so new mediaepoch/icons re-download. */
    public static void invalidateCaches() {
        SVG_CACHE.clear();
        // Keep encrypted config; fresh paint uses MediaAssetSync.bust on load.
    }

    @Nullable
    public static MiscDtos.NavIconsDto get() {
        return cached;
    }

    /** Instant paint from encrypted disk (never blocks on network). */
    public static void hydrateFromCache(AppContainer c) {
        if (c == null) return;
        try {
            MiscDtos.NavIconsDto disk = c.getFeatureCache().getJson(
                    EncryptedFeatureCache.UID_APP,
                    EncryptedFeatureCache.NS_NAV_ICONS,
                    MiscDtos.NavIconsDto.class,
                    0L);
            if (disk != null) cached = disk;
        } catch (Exception ignored) {
        }
    }

    /** Network refresh + encrypted write. Call from a background thread. */
    public static void refreshBlocking(AppContainer c) {
        if (c == null) return;
        try {
            Result<MiscDtos.NavIconsDto> r = ApiCall.execute(c.getConfigApi().navIcons());
            if (!r.success || r.data == null) return;
            cached = r.data;
            c.getFeatureCache().putJson(
                    EncryptedFeatureCache.UID_APP,
                    EncryptedFeatureCache.NS_NAV_ICONS,
                    r.data);
        } catch (Exception ignored) {
        }
    }

    public static void bind(
            @Nullable ImageView view,
            @Nullable String tabKey,
            boolean selected,
            @Nullable String fallbackNormal,
            @Nullable String fallbackSelected) {
        if (view == null) return;
        String url = resolveUrl(tabKey, selected);
        if (url != null && !url.isEmpty()) {
            view.clearColorFilter();
            view.setImageTintList(null);
            if (isSvgUrl(url)) {
                loadRemoteSvg(view, url, fallbackNormal, fallbackSelected, selected);
                return;
            }
            // Raster (png/jpg/webp)
            Object prev = view.getTag(R.id.tag_image_url);
            if (prev instanceof String && url.equals(prev) && view.getDrawable() != null) {
                return;
            }
            view.setTag(R.id.tag_image_url, url);
            view.setLayerType(ImageView.LAYER_TYPE_HARDWARE, null);
            try {
                Glide.with(view)
                        .load(url)
                        .apply(MediaAssetSync.requestOptions())
                        .dontAnimate()
                        .fitCenter()
                        .into(view);
                return;
            } catch (Exception ignored) {
                // fall through to assets
            }
        }
        AssetIcons.loadTab(view, selected,
                fallbackNormal != null ? fallbackNormal : "",
                fallbackSelected != null ? fallbackSelected : fallbackNormal);
    }

    private static void loadRemoteSvg(
            ImageView view,
            String url,
            @Nullable String fallbackNormal,
            @Nullable String fallbackSelected,
            boolean selected) {
        Object prev = view.getTag(R.id.tag_image_url);
        if (prev instanceof String && url.equals(prev) && view.getDrawable() != null) {
            return;
        }
        view.setTag(R.id.tag_image_url, url);

        Drawable hit = SVG_CACHE.get(url);
        if (hit != null) {
            applySvg(view, hit);
            return;
        }

        // Keep current art while loading (or fall back if empty).
        if (view.getDrawable() == null) {
            AssetIcons.loadTab(view, selected,
                    fallbackNormal != null ? fallbackNormal : "",
                    fallbackSelected != null ? fallbackSelected : fallbackNormal);
        }

        final int token = url.hashCode();
        view.setTag(R.id.tag_frame_url, token);
        SVG_IO.execute(() -> {
            Drawable d = decodeRemoteSvg(url);
            if (d != null) SVG_CACHE.put(url, d);
            MAIN.post(() -> {
                if (view.getTag(R.id.tag_frame_url) == null
                        || !Integer.valueOf(token).equals(view.getTag(R.id.tag_frame_url))) {
                    return;
                }
                if (d != null) {
                    applySvg(view, d);
                } else {
                    view.setTag(R.id.tag_image_url, null);
                    AssetIcons.loadTab(view, selected,
                            fallbackNormal != null ? fallbackNormal : "",
                            fallbackSelected != null ? fallbackSelected : fallbackNormal);
                }
            });
        });
    }

    @Nullable
    private static Drawable decodeRemoteSvg(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "image/svg+xml,image/*,*/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream is = conn.getInputStream()) {
                SVG svg = SVG.getFromInputStream(is);
                if (svg.getDocumentWidth() <= 0) svg.setDocumentWidth("64px");
                if (svg.getDocumentHeight() <= 0) svg.setDocumentHeight("64px");
                return new PictureDrawable(svg.renderToPicture());
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void applySvg(ImageView view, Drawable d) {
        // PictureDrawable needs software layer (same as AssetIcons).
        view.setLayerType(ImageView.LAYER_TYPE_SOFTWARE, null);
        view.clearColorFilter();
        view.setImageTintList(null);
        view.setImageDrawable(d);
    }

    public static boolean isSvgUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#');
        if (h >= 0) path = path.substring(0, h);
        return path.toLowerCase(Locale.US).endsWith(".svg");
    }

    @Nullable
    private static String resolveUrl(@Nullable String tabKey, boolean selected) {
        MiscDtos.NavIconPairDto pair = pairFor(tabKey);
        if (pair == null) return null;
        String primary = selected
                ? first(pair.selected, pair.normal, pair.icon)
                : first(pair.normal, pair.icon, pair.selected);
        if (primary == null || primary.isEmpty()) return null;
        return MediaAssetSync.bust(AssetCatalog.absoluteUrl(primary));
    }

    @Nullable
    private static MiscDtos.NavIconPairDto pairFor(@Nullable String tabKey) {
        MiscDtos.NavIconsDto cfg = cached;
        if (cfg == null || tabKey == null) return null;
        switch (tabKey) {
            case TAB_PARTY: return cfg.party;
            case TAB_DRAMA: return cfg.drama;
            case TAB_GAMES: return cfg.games;
            case TAB_CHAT: return cfg.chat;
            case TAB_ME: return cfg.me;
            default: return null;
        }
    }

    @Nullable
    private static String first(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}
