package com.Dramizo.Series.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Pulse / float animation for gift burst without dimming the room. */
public final class GiftBurstAnimator {
    private static final int TAG = 0x70F11A20;

    private GiftBurstAnimator() {}

    public static void start(View target) {
        if (target == null) return;
        stop(target);
        ObjectAnimator sx = ObjectAnimator.ofFloat(target, View.SCALE_X, 0.85f, 1.15f, 0.85f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(target, View.SCALE_Y, 0.85f, 1.15f, 0.85f);
        ObjectAnimator ty = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, 0f, -12f, 0f);
        ObjectAnimator rot = ObjectAnimator.ofFloat(target, View.ROTATION, -4f, 4f, -4f);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        ty.setRepeatCount(ValueAnimator.INFINITE);
        rot.setRepeatCount(ValueAnimator.INFINITE);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, ty, rot);
        set.setDuration(900);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        target.setTag(TAG, set);
    }

    public static void stop(View target) {
        if (target == null) return;
        Object tag = target.getTag(TAG);
        if (tag instanceof AnimatorSet) {
            ((AnimatorSet) tag).cancel();
            target.setTag(TAG, null);
        }
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        target.setRotation(0f);
    }
}
