package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Mikoo-style gift motion: gift holds mid-screen, clones, then flies to each mic seat.
 */
public final class GiftFlyAnimator {
    private GiftFlyAnimator() {}

    public static void fly(FrameLayout overlay, @Nullable String iconUrl,
                           PointF from, PointF to, Runnable onEnd) {
        if (overlay == null || from == null || to == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int size = (int) (72 * overlay.getResources().getDisplayMetrics().density);
        ImageView icon = newImage(overlay, iconUrl, size);
        icon.setX(from.x - size / 2f);
        icon.setY(from.y - size / 2f);
        icon.setScaleX(0.2f);
        icon.setScaleY(0.2f);
        icon.setAlpha(0f);
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
                remove(icon);
                if (onEnd != null) onEnd.run();
            }
        });
        icon.animate().rotation(18f).setDuration(500).withEndAction(() ->
                icon.animate().rotation(-8f).setDuration(400).start()).start();
        set.start();
    }

    /**
     * Hold a large gift in the center, then clone one icon per seat and fly out.
     * Matches Mikoo “all mics / gift scatter” motion from room screenshots.
     */
    public static void holdAndScatter(
            @Nullable FrameLayout overlay,
            @Nullable String iconUrl,
            @Nullable PointF center,
            @Nullable List<PointF> seats,
            long holdMs,
            @Nullable Runnable onScatterStart,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        float density = overlay.getResources().getDisplayMetrics().density;
        int w = Math.max(overlay.getWidth(),
                overlay.getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(overlay.getHeight(),
                overlay.getResources().getDisplayMetrics().heightPixels);
        PointF mid = center != null ? center : new PointF(w / 2f, h * 0.42f);
        List<PointF> targets = seats != null ? new ArrayList<>(seats) : new ArrayList<>();

        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();

        int big = Math.round(96f * density);
        ImageView hero = newImage(overlay, iconUrl, big);
        hero.setX(mid.x - big / 2f);
        hero.setY(mid.y - big / 2f);
        hero.setScaleX(0.15f);
        hero.setScaleY(0.15f);
        hero.setAlpha(0f);
        overlay.addView(hero);

        // Pop + hold in center.
        AnimatorSet pop = new AnimatorSet();
        pop.playTogether(
                ObjectAnimator.ofFloat(hero, View.SCALE_X, 0.15f, 1.18f, 1f),
                ObjectAnimator.ofFloat(hero, View.SCALE_Y, 0.15f, 1.18f, 1f),
                ObjectAnimator.ofFloat(hero, View.ALPHA, 0f, 1f, 1f),
                ObjectAnimator.ofFloat(hero, View.ROTATION, -12f, 8f, 0f));
        pop.setDuration(620);
        pop.setInterpolator(new OvershootInterpolator(1.2f));
        pop.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                long hold = Math.max(350L, holdMs);
                hero.postDelayed(() -> {
                    if (onScatterStart != null) {
                        try {
                            onScatterStart.run();
                        } catch (Exception ignored) {
                        }
                    }
                    if (targets.isEmpty()) {
                        // Pulse fade only.
                        hero.animate()
                                .alpha(0f).scaleX(1.4f).scaleY(1.4f)
                                .setDuration(420)
                                .withEndAction(() -> {
                                    remove(hero);
                                    if (onEnd != null) onEnd.run();
                                })
                                .start();
                        return;
                    }
                    scatterClones(overlay, iconUrl, mid, targets, density, onEnd);
                    // Hero dissolves while clones fly.
                    hero.animate()
                            .alpha(0f).scaleX(0.4f).scaleY(0.4f)
                            .setDuration(380)
                            .withEndAction(() -> remove(hero))
                            .start();
                }, hold);
            }
        });
        pop.start();
    }

    private static void scatterClones(
            FrameLayout overlay,
            @Nullable String iconUrl,
            PointF from,
            List<PointF> seats,
            float density,
            @Nullable Runnable onEnd
    ) {
        int size = Math.round(42f * density);
        final int[] left = {seats.size()};
        if (left[0] <= 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        for (int i = 0; i < seats.size(); i++) {
            PointF to = seats.get(i);
            if (to == null) {
                left[0]--;
                continue;
            }
            ImageView clone = newImage(overlay, iconUrl, size);
            clone.setX(from.x - size / 2f);
            clone.setY(from.y - size / 2f);
            clone.setScaleX(0.55f);
            clone.setScaleY(0.55f);
            clone.setAlpha(0.95f);
            overlay.addView(clone);

            long delay = 40L + i * 55L;
            long duration = 780L + (i % 4) * 60L;

            ObjectAnimator tx = ObjectAnimator.ofFloat(
                    clone, View.X, from.x - size / 2f, to.x - size / 2f);
            ObjectAnimator ty = ObjectAnimator.ofFloat(
                    clone, View.Y, from.y - size / 2f, to.y - size / 2f);
            ObjectAnimator sx = ObjectAnimator.ofFloat(clone, View.SCALE_X, 0.55f, 1.05f, 0.7f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(clone, View.SCALE_Y, 0.55f, 1.05f, 0.7f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(clone, View.ALPHA, 0.95f, 1f, 0f);
            ObjectAnimator rot = ObjectAnimator.ofFloat(clone, View.ROTATION, 0f,
                    (i % 2 == 0 ? 1f : -1f) * (25f + (i % 5) * 8f));

            AnimatorSet fly = new AnimatorSet();
            fly.playTogether(tx, ty, sx, sy, alpha, rot);
            fly.setStartDelay(delay);
            fly.setDuration(duration);
            fly.setInterpolator(new AccelerateDecelerateInterpolator());
            fly.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    remove(clone);
                    left[0]--;
                    if (left[0] <= 0 && onEnd != null) onEnd.run();
                }
            });
            fly.start();
        }
    }

    /** Floating gold "37x" near combos (matches Mikoo left multiplier). */
    public static void showComboBurst(
            @Nullable FrameLayout overlay,
            int combo,
            @Nullable PointF at,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null || combo < 1) {
            if (onEnd != null) onEnd.run();
            return;
        }
        float density = overlay.getResources().getDisplayMetrics().density;
        int w = Math.max(overlay.getWidth(),
                overlay.getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(overlay.getHeight(),
                overlay.getResources().getDisplayMetrics().heightPixels);
        PointF p = at != null ? at : new PointF(w * 0.14f, h * 0.62f);

        TextView tv = new TextView(overlay.getContext());
        tv.setText(combo + "x");
        tv.setTextColor(0xFFFFE082);
        tv.setTextSize(28f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setShadowLayer(6f, 0f, 2f, 0xE0000000);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        tv.setX(Math.max(8f * density, p.x - tv.getMeasuredWidth() / 2f));
        tv.setY(p.y - tv.getMeasuredHeight() / 2f);
        tv.setAlpha(0f);
        tv.setScaleX(0.4f);
        tv.setScaleY(0.4f);
        overlay.addView(tv);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();

        tv.animate()
                .alpha(1f)
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> tv.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .withEndAction(() -> tv.postDelayed(() -> tv.animate()
                                .alpha(0f)
                                .translationYBy(-28f * density)
                                .setDuration(480)
                                .setInterpolator(new DecelerateInterpolator())
                                .withEndAction(() -> {
                                    remove(tv);
                                    if (onEnd != null) onEnd.run();
                                })
                                .start(), 900))
                        .start())
                .start();
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

    private static ImageView newImage(FrameLayout overlay, @Nullable String iconUrl, int size) {
        ImageView icon = new ImageView(overlay.getContext());
        icon.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (iconUrl != null && !iconUrl.isEmpty()) {
            try {
                Glide.with(icon).load(AssetCatalog.absoluteUrl(iconUrl))
                        .placeholder(ImagePlaceholder.gift())
                        .error(ImagePlaceholder.gift())
                        .into(icon);
            } catch (Exception e) {
                icon.setImageResource(ImagePlaceholder.gift());
            }
        } else {
            icon.setImageResource(ImagePlaceholder.gift());
        }
        return icon;
    }

    private static void remove(@Nullable View v) {
        if (v == null) return;
        if (v.getParent() instanceof ViewGroup parent) {
            parent.removeView(v);
        }
    }
}
