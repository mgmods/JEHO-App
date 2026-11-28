package com.Dramizo.Series.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;

import java.util.WeakHashMap;

/**
 * Subtle pulse for staff identity frames. Safe for RecyclerView reuse:
 * callers must stop the animation when a view is rebound or recycled.
 */
public final class StaffIdentityFrameMotion {
    private static final WeakHashMap<View, AnimatorSet> RUNNING = new WeakHashMap<>();

    private StaffIdentityFrameMotion() { }

    public static void start(View view) {
        if (view == null) return;
        stop(view);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.82f, 1.0f);
        alpha.setRepeatMode(ValueAnimator.REVERSE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.985f, 1.025f);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.985f, 1.025f);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(2200L);
        RUNNING.put(view, set);
        set.start();
    }

    public static void stop(View view) {
        if (view == null) return;
        AnimatorSet previous = RUNNING.remove(view);
        if (previous != null) previous.cancel();
        view.setAlpha(1.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
    }
}
