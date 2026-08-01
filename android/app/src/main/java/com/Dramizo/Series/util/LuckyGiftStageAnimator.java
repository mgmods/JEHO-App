package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Lucky gift send FX (Mikoo mega-style):
 * Center spend label, then gold coins sprinkle onto every mic seat.
 */
public final class LuckyGiftStageAnimator {
    private LuckyGiftStageAnimator() {}

    public static void play(
            @Nullable FrameLayout overlay,
            @Nullable String giftIconUrl,
            @Nullable List<PointF> micTargets,
            long coinsSpent,
            int quantity,
            int personCount,
            @Nullable Runnable onScatterStart,
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
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        float density = overlay.getResources().getDisplayMetrics().density;
        Random rnd = new Random();

        ImageView giftIcon = null;
        if (giftIconUrl != null && !giftIconUrl.trim().isEmpty()) {
            int iconSize = Math.round(88 * density);
            giftIcon = new ImageView(overlay.getContext());
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(iconSize, iconSize);
            giftIcon.setLayoutParams(ilp);
            giftIcon.setX((w - iconSize) / 2f);
            giftIcon.setY(h * 0.28f);
            giftIcon.setAlpha(0f);
            giftIcon.setScaleX(0.5f);
            giftIcon.setScaleY(0.5f);
            overlay.addView(giftIcon);
            try {
                com.bumptech.glide.Glide.with(overlay.getContext().getApplicationContext())
                        .load(giftIconUrl.trim())
                        .into(giftIcon);
            } catch (Exception ignored) {
                giftIcon.setImageResource(R.drawable.ic_asset_coin_gold);
            }
            giftIcon.animate()
                    .alpha(1f)
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(360)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }

        TextView centerLabel = new TextView(overlay.getContext());
        centerLabel.setTextColor(Color.parseColor("#FFFFCF4C"));
        centerLabel.setTextSize(26f);
        centerLabel.setTypeface(Typeface.DEFAULT_BOLD);
        centerLabel.setShadowLayer(6f, 0f, 2f, Color.parseColor("#E6000000"));
        centerLabel.setGravity(Gravity.CENTER);
        int qty = Math.max(1, quantity);
        long showCoins = Math.max(1L, coinsSpent);
        centerLabel.setText(String.format(Locale.US, "×%d · %s", qty, formatCoins(showCoins)));
        centerLabel.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        centerLabel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        centerLabel.setX((w - centerLabel.getMeasuredWidth()) / 2f);
        centerLabel.setY(h * 0.42f);
        centerLabel.setAlpha(0f);
        centerLabel.setScaleX(0.7f);
        centerLabel.setScaleY(0.7f);
        overlay.addView(centerLabel);

        final ImageView iconRef = giftIcon;
        ObjectAnimator labelIn = ObjectAnimator.ofFloat(centerLabel, View.ALPHA, 0f, 1f);
        ObjectAnimator labelSx = ObjectAnimator.ofFloat(centerLabel, View.SCALE_X, 0.7f, 1.1f, 1f);
        ObjectAnimator labelSy = ObjectAnimator.ofFloat(centerLabel, View.SCALE_Y, 0.7f, 1.1f, 1f);
        AnimatorSet labelPop = new AnimatorSet();
        labelPop.playTogether(labelIn, labelSx, labelSy);
        labelPop.setDuration(420);
        labelPop.setInterpolator(new OvershootInterpolator(1.05f));
        labelPop.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onScatterStart != null) onScatterStart.run();
                Runnable finish = () -> {
                    fadeOutAndRemove(centerLabel);
                    fadeOutAndRemove(iconRef);
                    if (onEnd != null) onEnd.run();
                };
                // Prefer sprinkle onto all mics (mega-style); fallback free-fall.
                if (micTargets != null && !micTargets.isEmpty()) {
                    int n = Math.min(140, Math.max(48, 36 + Math.max(1, personCount) * 10));
                    CoinRainAnimator.burstCenterToSeats(overlay, micTargets, n, finish);
                } else {
                    slowCoinRain(overlay, w, h, density, rnd, personCount, finish);
                }
            }
        });
        labelPop.start();
    }

    /** Slow vertical coin rain — falls gently, not toward seats. */
    private static void slowCoinRain(
            FrameLayout overlay,
            int w,
            int h,
            float density,
            Random rnd,
            int personCount,
            @Nullable Runnable onEnd
    ) {
        int coinN = Math.min(64, Math.max(28, 24 + Math.max(1, personCount) * 4));
        int size = Math.round(22 * density);
        final int[] left = {coinN};
        Runnable doneOne = () -> {
            left[0]--;
            if (left[0] <= 0 && onEnd != null) onEnd.run();
        };

        for (int i = 0; i < coinN; i++) {
            ImageView coin = new ImageView(overlay.getContext());
            coin.setImageResource(R.drawable.ic_asset_coin_gold);
            coin.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            float startX = rnd.nextFloat() * Math.max(1, w - size);
            float startY = -size - rnd.nextInt(Math.max(1, (int) (h * 0.35f)));
            coin.setX(startX);
            coin.setY(startY);
            coin.setAlpha(0f);
            coin.setScaleX(0.45f);
            coin.setScaleY(0.45f);
            overlay.addView(coin);

            float endX = startX + (rnd.nextFloat() - 0.5f) * 70f * density;
            float endY = h * (0.55f + rnd.nextFloat() * 0.35f);
            long delay = 40L + rnd.nextInt(900);
            // Slow fall — 1.4s … 2.4s
            long dur = 1400 + rnd.nextInt(1000);

            ObjectAnimator tx = ObjectAnimator.ofFloat(coin, View.X, startX, endX);
            ObjectAnimator ty = ObjectAnimator.ofFloat(coin, View.Y, startY, endY);
            ObjectAnimator sx = ObjectAnimator.ofFloat(coin, View.SCALE_X, 0.45f, 1.05f, 0.9f);
            ObjectAnimator sy = ObjectAnimator.ofFloat(coin, View.SCALE_Y, 0.45f, 1.05f, 0.9f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(coin, View.ALPHA, 0f, 1f, 1f, 0f);
            ObjectAnimator rot = ObjectAnimator.ofFloat(coin, View.ROTATION, 0f,
                    (rnd.nextBoolean() ? 1f : -1f) * (120f + rnd.nextInt(200)));
            AnimatorSet set = new AnimatorSet();
            set.playTogether(tx, ty, sx, sy, alpha, rot);
            set.setStartDelay(delay);
            set.setDuration(dur);
            set.setInterpolator(new DecelerateInterpolator(0.85f));
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeView(coin);
                    doneOne.run();
                }
            });
            set.start();
        }
    }

    /** Center win burst: Mikoo LuckyGiftView — big ×N + coins text over tier BG. */
    public static void showWinBurst(
            @Nullable FrameLayout overlay,
            int multiplier,
            long coinsWon,
            @Nullable Runnable onEnd
    ) {
        showWinBurst(overlay, multiplier, coinsWon, null, onEnd);
    }

    public static void showWinBurst(
            @Nullable FrameLayout overlay,
            int multiplier,
            long coinsWon,
            @Nullable List<PointF> micTargets,
            @Nullable Runnable onEnd
    ) {
        if (overlay == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        int w = Math.max(overlay.getWidth(), overlay.getResources().getDisplayMetrics().widthPixels);
        int h = Math.max(overlay.getHeight(), overlay.getResources().getDisplayMetrics().heightPixels);
        int box = Math.round(Math.min(350f, Math.min(w, h) * 0.72f));
        int mul = Math.max(1, multiplier);

        FrameLayout card = new FrameLayout(overlay.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(box, box);
        card.setLayoutParams(lp);
        card.setX((w - box) / 2f);
        card.setY(h * 0.28f - box / 2f);
        card.setAlpha(0f);
        card.setScaleX(0.7f);
        card.setScaleY(0.7f);

        ImageView bg = new ImageView(overlay.getContext());
        bg.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bg.setImageResource(tierBg(mul));
        card.addView(bg);

        TextView tvMul = new TextView(overlay.getContext());
        tvMul.setText(String.valueOf(mul));
        tvMul.setTextColor(Color.parseColor("#FFFFCF4C"));
        tvMul.setTextSize(46f);
        tvMul.setTypeface(Typeface.DEFAULT_BOLD);
        tvMul.setShadowLayer(3f, 0f, 0f, Color.parseColor("#E6000000"));
        tvMul.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Math.round(box * 0.28f);
        tvMul.setLayoutParams(mlp);
        card.addView(tvMul);

        TextView tvTimes = new TextView(overlay.getContext());
        tvTimes.setText("مرات");
        tvTimes.setTextColor(Color.WHITE);
        tvTimes.setTextSize(22f);
        tvTimes.setTypeface(Typeface.DEFAULT_BOLD);
        tvTimes.setGravity(Gravity.CENTER);
        tvTimes.setShadowLayer(3f, 0f, 0f, Color.parseColor("#E6000000"));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(box * 0.46f);
        tvTimes.setLayoutParams(tlp);
        card.addView(tvTimes);

        TextView tvCoins = new TextView(overlay.getContext());
        tvCoins.setText("+" + formatCoins(Math.max(0L, coinsWon)) + " عملة");
        tvCoins.setTextColor(Color.WHITE);
        tvCoins.setTextSize(15f);
        tvCoins.setTypeface(Typeface.DEFAULT_BOLD);
        tvCoins.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Math.round(box * 0.72f);
        tvCoins.setLayoutParams(clp);
        card.addView(tvCoins);

        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        overlay.addView(card);

        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .withEndAction(() -> card.postDelayed(() -> {
                    card.animate()
                            .alpha(0f)
                            .scaleX(0.85f)
                            .scaleY(0.85f)
                            .setDuration(420)
                            .withEndAction(() -> {
                                removeView(card);
                                if (onEnd != null) onEnd.run();
                            })
                            .start();
                }, 3200))
                .start();

        // Mega return: coins pulse from center then fly to every mic.
        int rainN = Math.min(140, Math.max(48, 28 + mul * 8));
        CoinRainAnimator.burstCenterToSeats(overlay,
                (micTargets != null && !micTargets.isEmpty()) ? micTargets : null,
                rainN, null);
    }

    private static int tierBg(int multiplier) {
        if (multiplier > 20) return R.drawable.bg_lucky_gift_low_plus;
        if (multiplier > 10) return R.drawable.bg_lucky_gift_low_1000;
        if (multiplier > 5) return R.drawable.bg_lucky_gift_low_500;
        return R.drawable.bg_lucky_gift_low_100;
    }

    private static String formatCoins(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.US, "%.1fK", n / 1000.0);
        return String.valueOf(n);
    }

    private static void fadeOutAndRemove(View v) {
        if (v == null) return;
        v.animate().alpha(0f).setDuration(220).withEndAction(() -> removeView(v)).start();
    }

    private static void removeView(View v) {
        if (v != null && v.getParent() instanceof ViewGroup parent) {
            parent.removeView(v);
        }
    }
}
