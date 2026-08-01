package com.Dramizo.Series.presentation.common;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.Dramizo.Series.R;

/** Light theme edge-to-edge with white system bars. */
public final class EdgeToEdgeHelper {
    private EdgeToEdgeHelper() {}

    public static void apply(@NonNull Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        int white = ContextCompat.getColor(activity, R.color.white);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(white);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(true);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(true);
    }

    /** Dark immersive chrome for voice room (light icons on dark wallpaper). */
    public static void applyImmersiveDark(@NonNull Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
    }

    /**
     * Solid Android navigation bar matching the main bottom tab chrome.
     * Status bar stays transparent so wallpaper can still show under it.
     */
    public static void applySolidBottomChrome(@NonNull Activity activity) {
        int color = ContextCompat.getColor(activity, R.color.bottom_chrome);
        applySolidBottomChrome(activity, color);
    }

    public static void applySolidBottomChrome(@NonNull Activity activity, @ColorInt int color) {
        activity.getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        // Dark navy chrome → light (white) system nav buttons.
        double luminance = (
                0.2126 * ((color >> 16) & 0xFF)
                        + 0.7152 * ((color >> 8) & 0xFF)
                        + 0.0722 * (color & 0xFF)
        ) / 255.0;
        c.setAppearanceLightNavigationBars(luminance > 0.62);
    }

    public static void padBottom(@NonNull View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(left, top, right, bottom + Math.max(bars.bottom, 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void padStatusOnly(@NonNull View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(left, top + bars.top, right, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public static void padSystemBars(@NonNull View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left, top + bars.top, right, bottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds status-bar inset to an existing top margin (for chrome over full-bleed BG). */
    public static void addStatusBarTopMargin(@NonNull View view) {
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof android.view.ViewGroup.MarginLayoutParams)) return;
        final android.view.ViewGroup.MarginLayoutParams mlp =
                (android.view.ViewGroup.MarginLayoutParams) lp;
        final int baseTop = mlp.topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            mlp.topMargin = baseTop + Math.max(bars.top, 0);
            v.setLayoutParams(mlp);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Adds nav-bar inset as bottom margin (keeps fixed-height bars usable —
     * unlike padding which shrinks children inside a fixed 72dp bar).
     */
    public static void addNavBarBottomMargin(@NonNull View view) {
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof android.view.ViewGroup.MarginLayoutParams)) return;
        final android.view.ViewGroup.MarginLayoutParams mlp =
                (android.view.ViewGroup.MarginLayoutParams) lp;
        final int baseBottom = mlp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            mlp.bottomMargin = baseBottom + Math.max(bars.bottom, 0);
            v.setLayoutParams(mlp);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
