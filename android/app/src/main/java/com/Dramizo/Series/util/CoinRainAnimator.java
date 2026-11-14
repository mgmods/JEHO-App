package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lucky / مردود gold FX: coins pulse from screen center then fly to every occupied seat.
 */
public final class CoinRainAnimator {
    private CoinRainAnimator() {}

    /**
     * Prefer center→seats when targets exist; otherwise gentle free-fall rain.
     */
    public static void rain(
            @Nullable FrameLayout overlay,
            @Nullable List<PointF> targets,
            int coinCount,
            @Nullable Runnable onEnd
    ) {
        if (targets != null && !targets.isEmpty()) {
            burstCenterToSeats(overlay, targets, coinCount, onEnd);
        } else {
            freeFallRain(overlay, coinCount, onEnd);
        }
    }

    /**
     * Gold pulses at mid-screen then flies to each seat (longer, readable).
     */
    public static void burstCenterToSeats(
            @Nullable FrameLayout overlay,
            @Nullable List<PointF> seats,
            int coinCount,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int w = Math.max(overlay.getWidth(), overlay.getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(overlay.getHeight(), overlay.getResources().getDisplayMetrics().heightPixels);
        if (w <= 0 || h <= 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        List<PointF> targets = seats != null ? new ArrayList<>(seats) : new ArrayList<>();
        if (targets.isEmpty()) {
            freeFallRain(overlay, coinCount, onEnd);
            return;
        }

        float density = overlay.getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        // Hard cap — budget devices (Hot 30 etc.) OOM when 100+ ImageViews+animators spawn.
        int n = Math.max(6, Math.min(18, coinCount));
        Random rnd = new Random();
        PointF center = new PointF(w / 2f, h * 0.48f);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();

        // Center pulse cluster — gold heartbeats before scatter.
        int pulseN = Math.min(6, Math.max(3, targets.size()));
        final int[] remaining = {n + pulseN};
        Runnable doneOne = () -> {
            remaining[0]--;
            if (remaining[0] <= 0 && onEnd != null) onEnd.run();
        };

        for (int i = 0; i < pulseN; i++) {
            ImageView coin = new ImageView(overlay.getContext());
            coin.setImageResource(R.drawable.ic_asset_coin_gold);
            coin.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            float ox = (rnd.nextFloat() - 0.5f) * 36f * density;
            float oy = (rnd.nextFloat() - 0.5f) * 36f * density;
            coin.setX(center.x - size / 2f + ox);
            coin.setY(center.y - size / 2f + oy);
            coin.setAlpha(0f);
            coin.setScaleX(0.35f);
            coin.setScaleY(0.35f);
            overlay.addView(coin);

            long delay = 40L + rnd.nextInt(220);
            ObjectAnimator sx = ObjectAnimator.ofFloat(coin, View.SCALE_X, 0.35f, 1.35f, 1f, 1.2f, 0.9f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(coin, View.SCALE_Y, 0.35f, 1.35f, 1f, 1.2f, 0.9f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(coin, View.ALPHA, 0f, 1f, 1f, 0.85f, 0f);
            ObjectAnimator rot = ObjectAnimator.ofFloat(coin, View.ROTATION, 0f,
                    (rnd.nextBoolean() ? 1f : -1f) * (90f + rnd.nextInt(160)));
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(sx, sy, alpha, rot);
            pulse.setStartDelay(delay);
            pulse.setDuration(1500 + rnd.nextInt(500));
            pulse.setInterpolator(new OvershootInterpolator(1.15f));
            pulse.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    remove(coin);
                    doneOne.run();
                }
            });
            pulse.start();
        }

        // Scatter: each coin flies from center to a seat (round-robin + jitter).
        for (int i = 0; i < n; i++) {
            final int idx = i;
            PointF to = targets.get(idx % targets.size());
            ImageView coin = new ImageView(overlay.getContext());
            coin.setImageResource(R.drawable.ic_asset_coin_gold);
            coin.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            float startX = center.x - size / 2f + (rnd.nextFloat() - 0.5f) * 28f * density;
            float startY = center.y - size / 2f + (rnd.nextFloat() - 0.5f) * 28f * density;
            coin.setX(startX);
            coin.setY(startY);
            coin.setAlpha(0f);
            coin.setScaleX(0.4f);
            coin.setScaleY(0.4f);
            overlay.addView(coin);

            float endX = to.x - size / 2f + (rnd.nextFloat() - 0.5f) * 42f * density;
            float endY = to.y - size / 2f + (rnd.nextFloat() - 0.5f) * 32f * density;
            // Stagger so seats fill over ~3s; flight long enough to read mid-screen burst.
            long delay = 320L + (idx * 36L) + rnd.nextInt(200);
            long fly = 2200 + rnd.nextInt(1100); // 2.2–3.3s flight
            long linger = 700 + rnd.nextInt(500);

            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setStartDelay(delay);
            anim.setDuration(fly + linger);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            final float sx0 = startX;
            final float sy0 = startY;
            final long flyMs = fly;
            final long lingerMs = linger;
            anim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float flyT = Math.min(1f, t * ((flyMs + lingerMs) / (float) flyMs));
                if (flyT > 1f) flyT = 1f;
                // Ease-out toward seat after a tiny hold at center.
                float hold = 0.12f;
                float u = flyT < hold ? 0f : (flyT - hold) / (1f - hold);
                u = u * u * (3f - 2f * u); // smoothstep
                coin.setX(sx0 + (endX - sx0) * u);
                coin.setY(sy0 + (endY - sy0) * u);
                float scale = flyT < 0.2f
                        ? 0.4f + flyT * 3.5f
                        : (flyT < 0.85f ? 1.15f : 1.15f - (flyT - 0.85f) * 1.2f);
                coin.setScaleX(Math.max(0.35f, scale));
                coin.setScaleY(Math.max(0.35f, scale));
                coin.setRotation(u * (220f + (idx % 7) * 18f));
                float aVal = flyT < 0.08f ? flyT / 0.08f
                        : (flyT > 0.88f ? Math.max(0f, (1f - flyT) / 0.12f) : 1f);
                coin.setAlpha(aVal);
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    remove(coin);
                    doneOne.run();
                }
            });
            anim.start();
        }
    }

    /** Burst coins upward from a point then fall (soft مردود). */
    public static void burstFrom(
            @Nullable FrameLayout overlay,
            @Nullable PointF from,
            int coinCount,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null || from == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int w = Math.max(overlay.getWidth(), 1);
        int h = Math.max(overlay.getHeight(), 1);
        float density = overlay.getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        int n = Math.max(8, Math.min(16, coinCount));
        Random rnd = new Random();
        final int[] remaining = {n};
        for (int i = 0; i < n; i++) {
            ImageView coin = new ImageView(overlay.getContext());
            coin.setImageResource(R.drawable.ic_asset_coin_gold);
            coin.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            coin.setX(from.x - size / 2f);
            coin.setY(from.y - size / 2f);
            overlay.addView(coin);
            float peakY = from.y - (100 + rnd.nextInt(180)) * density;
            float endX = Math.max(0, Math.min(w - size,
                    from.x + (rnd.nextFloat() - 0.5f) * 260f * density));
            float endY = Math.min(h - size, from.y + (50 + rnd.nextInt(140)) * density);
            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1400 + rnd.nextInt(700));
            anim.setStartDelay(rnd.nextInt(280));
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float y = t < 0.4f
                        ? from.y + (peakY - from.y) * (t / 0.4f)
                        : peakY + (endY - peakY) * ((t - 0.4f) / 0.6f);
                float x = from.x + (endX - (from.x - size / 2f)) * t;
                coin.setX(x);
                coin.setY(y - size / 2f);
                coin.setRotation(t * 420f);
                coin.setAlpha(t < 0.82f ? 1f : Math.max(0f, (1f - t) / 0.18f));
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    remove(coin);
                    remaining[0]--;
                    if (remaining[0] <= 0 && onEnd != null) onEnd.run();
                }
            });
            anim.start();
        }
    }

    private static void freeFallRain(
            @Nullable FrameLayout overlay,
            int coinCount,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int w = Math.max(overlay.getWidth(), overlay.getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(overlay.getHeight(), overlay.getResources().getDisplayMetrics().heightPixels);
        if (w <= 0 || h <= 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        float density = overlay.getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        int n = Math.max(8, Math.min(16, coinCount));
        Random rnd = new Random();
        final int[] remaining = {n};
        for (int i = 0; i < n; i++) {
            ImageView coin = new ImageView(overlay.getContext());
            coin.setImageResource(R.drawable.ic_asset_coin_gold);
            coin.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            float startX = rnd.nextFloat() * Math.max(1, w - size);
            float startY = -size - rnd.nextInt(Math.max(1, h / 3));
            coin.setX(startX);
            coin.setY(startY);
            coin.setAlpha(0f);
            overlay.addView(coin);
            float endX = startX + (rnd.nextFloat() - 0.5f) * 80f * density;
            float endY = h * (0.55f + rnd.nextFloat() * 0.35f);
            long delay = rnd.nextInt(900);
            long dur = 1600 + rnd.nextInt(1100);
            ObjectAnimator tx = ObjectAnimator.ofFloat(coin, View.X, startX, endX);
            ObjectAnimator ty = ObjectAnimator.ofFloat(coin, View.Y, startY, endY);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(coin, View.ALPHA, 0f, 1f, 1f, 0f);
            ObjectAnimator rot = ObjectAnimator.ofFloat(coin, View.ROTATION, 0f, 200f + rnd.nextInt(200));
            AnimatorSet set = new AnimatorSet();
            set.playTogether(tx, ty, alpha, rot);
            set.setStartDelay(delay);
            set.setDuration(dur);
            set.setInterpolator(new DecelerateInterpolator(0.9f));
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    remove(coin);
                    remaining[0]--;
                    if (remaining[0] <= 0 && onEnd != null) onEnd.run();
                }
            });
            set.start();
        }
    }

    private static void remove(View v) {
        if (v != null && v.getParent() instanceof ViewGroup parent) {
            parent.removeView(v);
        }
    }
}
