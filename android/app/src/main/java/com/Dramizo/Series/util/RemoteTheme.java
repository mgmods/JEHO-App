package com.Dramizo.Series.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * Applies APK-bundled visuals only. Dashboard remote theme is not used.
 */
public final class RemoteTheme {
    private static volatile MiscDtos.ThemeDto cached;

    private RemoteTheme() {}

    public static MiscDtos.ThemeDto getCached(Context context) {
        MiscDtos.ThemeDto mem = cached;
        if (mem != null) return mem;
        // App visuals ship only in the APK — never hydrate from dashboard/remote theme.
        MiscDtos.ThemeDto defaults = MiscDtos.ThemeDto.defaults();
        cached = defaults;
        return defaults;
    }

    /** Remote theme download disabled — UI assets live in res/. */
    public static void refreshFromApi(Context context, com.Dramizo.Series.data.remote.api.ConfigApi api) {
        // no-op
    }

    private static void writeDisk(Context context, MiscDtos.ThemeDto theme) {
        // no-op — do not persist remote theme
    }

    @Nullable
    private static MiscDtos.ThemeDto readDisk(Context context) {
        return null;
    }

    public static int getCachedVersion(Context context) {
        MiscDtos.ThemeDto theme = getCached(context);
        return theme != null ? Math.max(0, theme.version) : 1;
    }

    public static int parseColor(@Nullable String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) return fallback;
        try {
            String v = hex.trim();
            if (!v.startsWith("#")) v = "#" + v;
            return Color.parseColor(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Nullable
    public static String assetUrl(Context context, String key) {
        MiscDtos.ThemeDto theme = getCached(context);
        if (theme == null || theme.assets == null || key == null) return null;
        String value = theme.assets.get(key);
        return notEmpty(value) ? value : null;
    }

    /** Brand artwork ships in the APK; remote brand URLs are ignored. */
    @Nullable
    public static String brandLogoUrl(Context context) {
        return null;
    }

    /** Splash artwork ships in the APK; remote splash URLs are ignored. */
    @Nullable
    public static String brandSplashUrl(Context context) {
        return null;
    }

  /** Wallpapers ship in the APK; remote theme URLs are ignored. */
  @Nullable
  public static String backgroundUrl(Context context, String screenKey) {
        return null;
  }

    @Nullable
    private static String backgroundField(MiscDtos.ThemeBackgrounds bg, String screenKey) {
        if (bg == null || screenKey == null) return null;
        switch (screenKey) {
            case "app": return bg.app;
            case "auth": return bg.auth;
            case "splash": return bg.splash;
            case "home": return bg.home;
            case "profile": return bg.profile;
            case "chat": return bg.chat;
            case "games": return bg.games;
            case "createRoom": return bg.createRoom;
            case "liveRoom": return bg.liveRoom;
            case "voiceRoom": return bg.voiceRoom;
            case "appNight": return bg.appNight;
            case "homeHeader": return bg.homeHeader;
            case "roomDefault": return bg.roomDefault;
            default: return null;
        }
    }

    @Nullable
    public static String roomDefaultBackgroundUrl(Context context) {
        return backgroundUrl(context, "voiceRoom");
    }

    @Nullable
    public static String homeBackgroundUrl(Context context) {
        return backgroundUrl(context, "home");
    }

    public static void applyActivityBackground(@Nullable Activity activity, String screenKey) {
        if (activity == null) return;
        View root = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        int fallback = fallbackForScreen(screenKey);
        String url = backgroundUrl(activity, screenKey);
        applyBackgroundTo(activity, root, url, fallback);
        applySystemBarsFromBackground(activity, url, fallback, screenKey);
        applyMappedAssets(root);
    }

    public static void applyActivityBackground(@Nullable View root, String screenKey) {
        if (root == null) return;
        Context ctx = root.getContext();
        String url = backgroundUrl(ctx, screenKey);
        applyBackgroundTo(null, root, url, fallbackForScreen(screenKey));
        applyMappedAssets(root);
    }

    /** One clean white background for the whole app. */
    private static int fallbackForScreen(String screenKey) {
        return R.color.white;
    }

    /**
     * Light theme: white status/nav bars with dark system icons.
     */
    private static void applySystemBarsFromBackground(
            @NonNull Activity activity,
            @Nullable String url,
            int fallbackRes,
            @Nullable String screenKey
    ) {
        Window window = activity.getWindow();
        if (window == null) return;
        final int white = activity.getResources().getColor(R.color.white, activity.getTheme());
        window.setStatusBarColor(Color.TRANSPARENT);
        if (wantsSolidBottomChrome(screenKey)) {
            applyBottomChromeColor(activity, white);
        } else {
            window.setNavigationBarColor(white);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(true);
            }
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(window, window.getDecorView());
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(true);
        if ("voiceRoom".equals(screenKey)) {
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            c.setAppearanceLightStatusBars(false);
            c.setAppearanceLightNavigationBars(false);
        }
    }

    /** Paints Android nav + main tab bar the same wallpaper-matched solid color. */
    private static void applyBottomChromeColor(@NonNull Activity activity, int color) {
        Window window = activity.getWindow();
        if (window == null) return;
        // Fully opaque — never let wallpaper bleed under system nav / tabs.
        int solid = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
        window.setNavigationBarColor(solid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        View bar = activity.findViewById(R.id.customBottomBar);
        if (bar != null) {
            bar.setBackgroundColor(solid);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(window, window.getDecorView());
        c.setAppearanceLightNavigationBars(isLight(solid));
    }

    /** Main shell tabs share one solid chrome with the Android navigation bar. */
    private static boolean wantsSolidBottomChrome(@Nullable String screenKey) {
        if (screenKey == null) return false;
        switch (screenKey) {
            case "home":
            case "drama":
            case "games":
            case "chat":
            case "profile":
            case "createRoom":
                return true;
            default:
                return false;
        }
    }

    private static int sampleStripColor(@NonNull Bitmap bitmap, boolean top) {
        int avg = sampleStripColorRaw(bitmap, top);
        // A subtle darkening keeps white system icons readable while preserving the image hue.
        return Color.rgb(
                (int) Math.min(255, Color.red(avg) * 0.82),
                (int) Math.min(255, Color.green(avg) * 0.82),
                (int) Math.min(255, Color.blue(avg) * 0.82)
        );
    }

    /** Exact average of the top/bottom image strip — used to match bottom chrome to wallpaper. */
    private static int sampleStripColorRaw(@NonNull Bitmap bitmap, boolean top) {
        int width = Math.max(1, bitmap.getWidth());
        int height = Math.max(1, bitmap.getHeight());
        int strip = Math.max(1, height / 9);
        int startY = top ? 0 : Math.max(0, height - strip);
        int stepX = Math.max(1, width / 24);
        int stepY = Math.max(1, strip / 8);
        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;
        for (int y = startY; y < Math.min(height, startY + strip); y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.alpha(pixel) < 128) continue;
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                count++;
            }
        }
        if (count == 0) return 0xFF0B1220; // deep navy brand fallback (not purple)
        return Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    private static void applySystemBarIconContrast(
            @NonNull Window window,
            int statusColor,
            int navigationColor
    ) {
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isLight(statusColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isLight(navigationColor)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static boolean isLight(int color) {
        double luminance = (
                0.2126 * Color.red(color)
                        + 0.7152 * Color.green(color)
                        + 0.0722 * Color.blue(color)
        ) / 255.0;
        return luminance > 0.62;
    }

    private static void applyBackgroundTo(
            @Nullable Activity activity,
            @Nullable View root,
            @Nullable String url,
            int fallbackRes
    ) {
        if (root == null) return;
        ImageView target = findBackgroundImageView(root);
        if (target != null) {
            // Preserve XML artwork when no remote wallpaper URL is configured.
            if (notEmpty(url)) {
                loadInto(target, url, fallbackRes);
            }
            return;
        }

        View bgTarget = root;
        if (activity != null) {
            View content = activity.findViewById(android.R.id.content);
            if (content instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) content;
                if (group.getChildCount() > 0) {
                    bgTarget = group.getChildAt(0);
                    ImageView nested = findBackgroundImageView(bgTarget);
                    if (nested != null) {
                        if (notEmpty(url)) loadInto(nested, url, fallbackRes);
                        return;
                    }
                }
            }
        }

        String resolved = AssetCatalog.absoluteUrl(url);
        if (resolved == null || resolved.isEmpty()) {
            try {
                bgTarget.setBackgroundResource(fallbackRes);
            } catch (Exception ignored) {
            }
            return;
        }

        final View finalTarget = bgTarget;
        try {
            Glide.with(finalTarget.getContext())
                    .load(resolved)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(
                                @NonNull Drawable resource,
                                @Nullable Transition<? super Drawable> transition
                        ) {
                            finalTarget.setBackground(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Exception e) {
            try {
                finalTarget.setBackgroundResource(fallbackRes);
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private static ImageView findBackgroundImageView(View root) {
        if (root == null) return null;
        Context ctx = root.getContext();
        String[] ids = {"imgThemeBg", "imgHomeBg", "imgRoomBg", "imgCoverFallback"};
        for (String idName : ids) {
            int id = ctx.getResources().getIdentifier(idName, "id", ctx.getPackageName());
            if (id != 0) {
                View found = root.findViewById(id);
                if (found instanceof ImageView) return (ImageView) found;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ImageView
                        && child.getLayoutParams() != null
                        && child.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT
                        && child.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    return (ImageView) child;
                }
                ImageView nested = findBackgroundImageView(child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    /**
     * UI icons and wallpapers ship in the APK. Remote theme URLs are ignored.
     */
    public static void applyMappedAssets(@Nullable View root) {
        // no-op: keep layout/local drawables
    }

    private static void loadMappedUrl(View root, String idName, @Nullable String url) {
        if (!notEmpty(url)) return;
        Context context = root.getContext();
        int id = context.getResources().getIdentifier(idName, "id", context.getPackageName());
        if (id == 0) return;
        View found = root.findViewById(id);
        if (found instanceof ImageView) {
            loadMappedUrl((ImageView) found, url);
        }
    }

    private static void loadMappedUrl(ImageView view, String url) {
        String resolved = AssetCatalog.absoluteUrl(url);
        if (!notEmpty(resolved)) return;
        Drawable fallback = view.getDrawable();
        try {
            Glide.with(view)
                    .load(resolved)
                    .placeholder(fallback)
                    .error(fallback)
                    .into(view);
        } catch (Exception ignored) {
            if (fallback != null) view.setImageDrawable(fallback);
        }
    }

    private static void loadMappedAsset(ImageView view, String key) {
        String url = assetUrl(view.getContext(), key);
        String resolved = AssetCatalog.absoluteUrl(url);
        if (!notEmpty(resolved)) return;
        Drawable fallback = view.getDrawable();
        try {
            Glide.with(view)
                    .load(resolved)
                    .placeholder(fallback)
                    .error(fallback)
                    .into(view);
        } catch (Exception ignored) {
            if (fallback != null) view.setImageDrawable(fallback);
        }
    }

    public static void applyChrome(Activity activity) {
        // Wallpaper + system bars come from applyActivityBackground.
        // Do not paint flat theme colors over brand backgrounds.
    }

    public static int primaryColor(Context context) {
        MiscDtos.ThemeDto theme = getCached(context);
        int fallback = 0xFFFE2C55;
        try {
            fallback = context.getColor(R.color.gift_accent);
        } catch (Exception ignored) {
            try {
                fallback = context.getColor(R.color.aurora_teal);
            } catch (Exception ignored2) {
            }
        }
        if (theme == null || theme.colors == null) return fallback;
        int parsed = parseColor(theme.colors.primary, fallback);
        // Dashboard sometimes stores surface/white as "primary" for light themes.
        if (isWeakBrandFill(parsed)) return fallback;
        return parsed;
    }

    private static boolean isWeakBrandFill(int color) {
        int a = (color >>> 24) & 0xFF;
        if (a < 180) return true;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        if (lum >= 0.78) return true;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return (max - min) < 18 && lum > 0.55;
    }

    public static void loadInto(ImageView view, @Nullable String pathOrUrl, int fallbackRes) {
        if (view == null) return;
        // Never un-hide stubs that layouts mark GONE.
        if (view.getVisibility() == View.GONE) return;
        String resolved = AssetCatalog.absoluteUrl(pathOrUrl);
        if (notEmpty(resolved)) {
            Drawable keep = view.getDrawable();
            try {
                Glide.with(view.getContext())
                        .load(resolved)
                        .placeholder(keep)
                        .error(fallbackRes != 0 ? fallbackRes : keep)
                        .into(view);
            } catch (Exception ignored) {
                if (fallbackRes != 0) view.setImageResource(fallbackRes);
            }
            return;
        }
        // No remote URL — keep android:src from XML. Never wipe artwork with white.
        if (view.getDrawable() == null && fallbackRes != 0) {
            view.setImageResource(fallbackRes);
        }
    }

    /**
     * Keep {@code android:src} from XML layouts. Do not override icons in code —
     * change drawables under res/drawable* to update UI art.
     */
    public static void loadAsset(ImageView view, String key, int fallbackRes) {
        if (view == null) return;
        view.setVisibility(View.VISIBLE);
        // XML src is the source of truth.
    }

    /** Keep XML icon visible — remote theme icons are disabled. */
    public static void loadAssetOrGone(ImageView view, String key) {
        if (view != null) view.setVisibility(View.VISIBLE);
    }

    private static boolean notEmpty(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
    }
}
