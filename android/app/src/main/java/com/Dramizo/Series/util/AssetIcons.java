package com.Dramizo.Series.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads icons from {@code assets/icons/} by file name.
 * <pre>
 * AssetIcons.load(imageView, "ic_home_tab_party_selected");
 * // → assets/icons/ic_home_tab_party_selected.svg
 * </pre>
 * Keep the original .svg files — do not convert/rasterize them into res/drawable.
 */
public final class AssetIcons {
    public static final String DIR = "icons/";

    /** Bottom tab: home / party */
    public static final String TAB_PARTY_NORMAL = "ic_home_tab_party_normal";
    public static final String TAB_PARTY_SELECTED = "ic_home_tab_party_selected";
    /** Bottom tab: games */
    public static final String TAB_GAME_NORMAL = "ic_home_tab_game_normal";
    public static final String TAB_GAME_SELECTED = "ic_home_tab_game_selected";
    /** Bottom tab: chat */
    public static final String TAB_CHAT_NORMAL = "ic_home_tab_chat_normal";
    public static final String TAB_CHAT_SELECTED = "ic_home_tab_chat_selected";
    /** Bottom tab: me / profile */
    public static final String TAB_ME_NORMAL = "ic_home_tab_me_normal";
    public static final String TAB_ME_SELECTED = "ic_home_tab_me_selected";
    /** Optional explore (unused by default if drama keeps its own art) */
    public static final String TAB_EXPLORE_NORMAL = "ic_home_tab_explore_normal";
    public static final String TAB_EXPLORE_SELECTED = "ic_home_tab_explore_selected";
    /** Home header */
    public static final String HOME_SEARCH = "ic_search";
    public static final String HOME_FILTER = "nearby_filter";

    private static final ConcurrentHashMap<String, Drawable> CACHE = new ConcurrentHashMap<>();

    private AssetIcons() {}

    /** Asset path for a logical icon name (with or without {@code .svg}). */
    @NonNull
    public static String assetPath(@Nullable String name) {
        if (name == null || name.isEmpty()) return DIR;
        String key = name.trim();
        if (key.startsWith(DIR)) key = key.substring(DIR.length());
        if (key.startsWith("icons/")) key = key.substring("icons/".length());
        if (!key.contains(".")) key = key + ".svg";
        return DIR + key;
    }

    /**
     * Load SVG under {@code assets/icons/} into the ImageView.
     * Name example: {@code "ic_home_tab_party_selected"} or {@code "ic_search.svg"}.
     */
    public static void load(@Nullable ImageView view, @Nullable String name) {
        if (view == null || name == null || name.isEmpty()) return;
        final String path = assetPath(name);
        Drawable cached = CACHE.get(path);
        if (cached != null) {
            apply(view, cached);
            return;
        }
        Context app = view.getContext().getApplicationContext();
        Drawable d = decode(app, path);
        if (d != null) {
            CACHE.put(path, d);
            apply(view, d);
        }
    }

    public static void loadTab(@Nullable ImageView view, boolean selected,
                               @NonNull String normalName, @NonNull String selectedName) {
        load(view, selected ? selectedName : normalName);
    }

    @Nullable
    private static Drawable decode(@NonNull Context ctx, @NonNull String path) {
        try (InputStream is = ctx.getAssets().open(path)) {
            SVG svg = SVG.getFromInputStream(is);
            if (svg.getDocumentWidth() <= 0) {
                svg.setDocumentWidth("64px");
            }
            if (svg.getDocumentHeight() <= 0) {
                svg.setDocumentHeight("64px");
            }
            return new PictureDrawable(svg.renderToPicture());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void apply(@NonNull ImageView view, @NonNull Drawable d) {
        // PictureDrawable needs software layer for correct antialiasing on many devices.
        view.setLayerType(ImageView.LAYER_TYPE_SOFTWARE, null);
        view.clearColorFilter();
        view.setImageTintList(null);
        view.setImageDrawable(d);
    }

    /** Drop cache (rare — e.g. hot-replace icons in debug). */
    public static void clearCache() {
        CACHE.clear();
    }
}
