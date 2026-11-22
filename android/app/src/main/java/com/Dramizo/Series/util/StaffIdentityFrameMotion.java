package com.Dramizo.Series.util;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

/** Gentle motion for staff identity frames, including static uploaded images. */
public final class StaffIdentityFrameMotion {
    private StaffIdentityFrameMotion() {}

    public static void start(@Nullable View frame) {
        if (frame == null) return;
        stop(frame);
        frame.setScaleX(1f);
        frame.setScaleY(1f);
        frame.setAlpha(1f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                frame,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f, 1f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.92f, 1f));
        animator.setDuration(2200L);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        frame.setTag(R.id.imgFrame, animator);
        animator.start();
    }

    public static void stop(@Nullable View frame) {
        if (frame == null) return;
        Object tag = frame.getTag(R.id.imgFrame);
        if (tag instanceof ObjectAnimator) ((ObjectAnimator) tag).cancel();
        frame.setTag(R.id.imgFrame, null);
        frame.setScaleX(1f);
        frame.setScaleY(1f);
        frame.setAlpha(1f);
    }
}
