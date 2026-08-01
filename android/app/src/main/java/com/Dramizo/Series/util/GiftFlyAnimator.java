package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

/** Animates a gift icon from center toward a seated recipient. */
public final class GiftFlyAnimator {
    private GiftFlyAnimator() {}

    public static void fly(FrameLayout overlay, @Nullable String iconUrl,
                           PointF from, PointF to, Runnable onEnd) {
        if (overlay == null || from == null || to == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int size = (int) (72 * overlay.getResources().getDisplayMetrics().density);
        ImageView icon = new ImageView(overlay.getContext());
        icon.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        icon.setX(from.x - size / 2f);
        icon.setY(from.y - size / 2f);
        icon.setScaleX(0.2f);
        icon.setScaleY(0.2f);
        icon.setAlpha(0f);
        if (iconUrl != null && !iconUrl.isEmpty()) {
            Glide.with(icon).load(AssetCatalog.absoluteUrl(iconUrl))
                    .placeholder(ImagePlaceholder.gift())
                    .error(ImagePlaceholder.gift())
                    .into(icon);
        } else {
            icon.setImageResource(ImagePlaceholder.gift());
        }
        overlay.addView(icon);
        overlay.bringToFront();

        ObjectAnimator tx = ObjectAnimator.ofFloat(icon, View.X, from.x - size / 2f, to.x - size / 2f);
        ObjectAnimator ty = ObjectAnimator.ofFloat(icon, View.Y, from.y - size / 2f, to.y - size / 2f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.2f, 1.3f, 0.85f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.2f, 1.3f, 0.85f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(icon, View.ALPHA, 0f, 1f, 1f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(tx, ty, sx, sy, alpha);
        set.setDuration(1100);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (icon.getParent() instanceof ViewGroup parent) {
                    parent.removeView(icon);
                }
                if (onEnd != null) onEnd.run();
            }
        });
        icon.animate().rotation(18f).setDuration(500).withEndAction(() ->
                icon.animate().rotation(-8f).setDuration(400).start()).start();
        set.start();
    }

    public static PointF centerOf(View view) {
        if (view == null) return new PointF(0f, 0f);
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return new PointF(loc[0] + view.getWidth() / 2f, loc[1] + view.getHeight() / 2f);
    }

    public static PointF centerInOverlay(FrameLayout overlay, View target) {
        PointF screen = centerOf(target);
        int[] overlayLoc = new int[2];
        overlay.getLocationOnScreen(overlayLoc);
        return new PointF(screen.x - overlayLoc[0], screen.y - overlayLoc[1]);
    }

    public static void pulseTarget(@Nullable View target) {
        if (target == null) return;
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.animate()
                .scaleX(1.12f).scaleY(1.12f)
                .setDuration(180)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                .start();
    }
}
