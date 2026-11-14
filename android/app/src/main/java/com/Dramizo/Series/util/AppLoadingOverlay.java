package com.Dramizo.Series.util;

import android.app.Activity;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.opensource.svgaplayer.SVGAImageView;

import java.util.WeakHashMap;

/** Branded Mikoo loading (svga_room_loading) for activities and main app tabs. */
public final class AppLoadingOverlay {
    private static final long MIN_VISIBLE_MS = 280L;
    private static final long ENTRY_SAFETY_TIMEOUT_MS = 4_000L;
    private static final WeakHashMap<Activity, State> STATES = new WeakHashMap<>();

    private AppLoadingOverlay() {}

    public static void showOnEntry(@NonNull Activity activity) {
        show(activity, true, true);
    }

    /** Show overlay until the caller explicitly hides it (e.g. after data load). */
    public static void showUntilReady(@NonNull Activity activity) {
        show(activity, false, false);
    }

    public static void show(@NonNull Activity activity) {
        show(activity, false, false);
    }

    private static void show(@NonNull Activity activity, boolean hideAfterFirstDraw, boolean safetyTimeout) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> show(activity, hideAfterFirstDraw, safetyTimeout));
            return;
        }
        State existing = STATES.get(activity);
        if (existing != null && existing.overlay.getParent() != null) {
            if (!hideAfterFirstDraw && !safetyTimeout) {
                existing.awaitingHide = true;
            }
            existing.hiding = false;
            MikooLoadingAnim.bind(existing.animation);
            existing.overlay.setVisibility(View.VISIBLE);
            existing.overlay.setAlpha(1f);
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) content;

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(0x4D000000);
        overlay.setAlpha(0f);
        overlay.setElevation(dp(activity, 40));

        SVGAImageView animation = new SVGAImageView(activity);
        FrameLayout.LayoutParams animationParams = new FrameLayout.LayoutParams(
                dp(activity, 120),
                dp(activity, 120),
                Gravity.CENTER);
        overlay.addView(animation, animationParams);
        MikooLoadingAnim.bind(animation);
        parent.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        State state = new State(overlay, animation, System.currentTimeMillis());
        state.awaitingHide = !hideAfterFirstDraw && !safetyTimeout;
        STATES.put(activity, state);
        overlay.animate().alpha(1f).setDuration(120L).start();
        if (hideAfterFirstDraw) {
            parent.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            if (parent.getViewTreeObserver().isAlive()) {
                                parent.getViewTreeObserver().removeOnPreDrawListener(this);
                            }
                            parent.post(() -> hide(activity));
                            return true;
                        }
                    });
        }
        if (safetyTimeout) {
            overlay.postDelayed(() -> {
                State s = STATES.get(activity);
                if (s != null && !s.awaitingHide) hide(activity);
            }, ENTRY_SAFETY_TIMEOUT_MS);
        }
    }

    public static void hide(@NonNull Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> hide(activity));
            return;
        }
        State state = STATES.get(activity);
        if (state == null || state.hiding) return;
        long remaining = MIN_VISIBLE_MS - (System.currentTimeMillis() - state.shownAt);
        if (remaining > 0) {
            state.overlay.postDelayed(() -> hide(activity), remaining);
            return;
        }
        state.hiding = true;
        state.awaitingHide = false;
        state.overlay.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    MikooLoadingAnim.stop(state.animation);
                    if (state.overlay.getParent() instanceof ViewGroup) {
                        ((ViewGroup) state.overlay.getParent()).removeView(state.overlay);
                    }
                    STATES.remove(activity);
                })
                .start();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class State {
        final FrameLayout overlay;
        final SVGAImageView animation;
        final long shownAt;
        boolean hiding;
        boolean awaitingHide;

        State(FrameLayout overlay, SVGAImageView animation, long shownAt) {
            this.overlay = overlay;
            this.animation = animation;
            this.shownAt = shownAt;
        }
    }
}
