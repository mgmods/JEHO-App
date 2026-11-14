package com.Dramizo.Series.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Host signals {@code roomFrameWave} — soft scale/tilt on room-frame art. */
public final class RoomCardAnimator {
    private static final int TAG_ANIMATOR = 0x70F11A2F;
    public static final int TAG_BASE_SCALE = 0x70F11A30;

    private RoomCardAnimator() {
    }

    public static void play(View view) {
        play(view, null);
    }

    public static void play(View view, String stableKey) {
        if (view == null) return;
        stop(view);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.012f, 1.006f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.012f, 1.006f, 1f);
        ObjectAnimator rotate = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 0.15f, -0.12f, 0f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, rotate);
        set.setDuration(3200L);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        view.setTag(TAG_ANIMATOR, set);
    }

    public static void stop(View view) {
        if (view == null) return;
        Object tagged = view.getTag(TAG_ANIMATOR);
        if (tagged instanceof AnimatorSet) {
            ((AnimatorSet) tagged).cancel();
            view.setTag(TAG_ANIMATOR, null);
        }
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setRotation(0f);
        view.setLayerType(View.LAYER_TYPE_NONE, null);
    }
}
