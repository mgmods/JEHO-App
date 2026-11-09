package com.Dramizo.Series.util;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.widget.TextView;

import com.Dramizo.Series.R;

/** Shared animated room-presence chip used in profiles and private conversations. */
public final class RoomTrackingIndicator {
    private RoomTrackingIndicator() {}

    public static void setActive(TextView indicator, boolean active) {
        if (indicator == null) return;
        Object tag = indicator.getTag();
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel();
            indicator.setTag(null);
        }
        indicator.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!active) {
            indicator.animate().cancel();
            indicator.setAlpha(1f);
            indicator.setScaleX(1f);
            indicator.setScaleY(1f);
            return;
        }

        indicator.setText("في الغرفة");
        indicator.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_room_tracking_radar, 0, 0, 0);
        indicator.setCompoundDrawablePadding(dp(indicator, 5));
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(
                indicator,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.78f));
        pulse.setDuration(900L);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        indicator.setTag(pulse);
        pulse.start();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}