package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Large lucky-gift reveal locked to the exact center of the room screen, followed by flights to seats. */
public final class LuckyGiftStageAnimator {
    private LuckyGiftStageAnimator() {}

    public static void play(@Nullable FrameLayout overlay, @Nullable String giftIconUrl,
            @Nullable List<PointF> micTargets, long coinsSpent, int quantity, int personCount,
            @Nullable Runnable onScatterStart, @Nullable Runnable onEnd) {
        play(overlay, giftIconUrl, micTargets, coinsSpent, quantity, personCount,
                null, null, false, onScatterStart, onEnd);
    }

    public static void play(@Nullable FrameLayout overlay, @Nullable String giftIconUrl,
            @Nullable List<PointF> micTargets, long coinsSpent, int quantity, int personCount,
            @Nullable PointF revealCenter, @Nullable Runnable onScatterStart,
            @Nullable Runnable onEnd) {
        play(overlay, giftIconUrl, micTargets, coinsSpent, quantity, personCount,
                null, revealCenter, false, onScatterStart, onEnd);
    }

    public static void play(@Nullable FrameLayout overlay, @Nullable String giftIconUrl,
            @Nullable List<PointF> micTargets, long coinsSpent, int quantity, int personCount,
            @Nullable PointF launchPoint, @Nullable PointF revealCenter, boolean cannonBurst,
            @Nullable Runnable onScatterStart, @Nullable Runnable onEnd) {
        if (overlay == null || overlay.getWidth() <= 0 || overlay.getHeight() <= 0
                || giftIconUrl == null || giftIconUrl.trim().isEmpty()) {
            finish(onEnd);
            return;
        }

        final float density = overlay.getResources().getDisplayMetrics().density;

        // Center the lucky-gift hero against the actual app window, not against
        // giftOverlay's local bounds. The overlay can be inset/translated by room UI
        // containers; using width/2 here makes the gift visibly drift toward an edge.
        // Convert the root window's center from screen coordinates into overlay space.
        final View root = overlay.getRootView();
        final int[] rootLocation = new int[2];
        final int[] overlayLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        overlay.getLocationOnScreen(overlayLocation);
        final float centerX = revealCenter != null ? revealCenter.x
                : rootLocation[0] + root.getWidth() * 0.5f - overlayLocation[0];
        final float centerY = revealCenter != null ? revealCenter.y
                : rootLocation[1] + root.getHeight() * 0.5f - overlayLocation[1];
        final float startX = launchPoint != null ? launchPoint.x : centerX;
        final float startY = launchPoint != null ? launchPoint.y : overlay.getHeight() - 42f * density;

        final FrameLayout stage = new FrameLayout(overlay.getContext());
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setClickable(false);
        // Room UI is Arabic/RTL. Keep the animation stage LTR so absolute X
        // coordinates and left margins are never mirrored toward the right edge.
        stage.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        stage.setElevation(overlay.getElevation() + 40f * density);
        stage.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.addView(stage);

        View glow = new View(overlay.getContext());
        GradientDrawable glowBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x00FFE8A3, 0xAAFFD66B, 0x00A66CFF});
        glowBg.setShape(GradientDrawable.OVAL);
        glow.setBackground(glowBg);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(
                Math.round(164f * density), Math.round(164f * density),
                Gravity.TOP | Gravity.LEFT);
        glowLp.leftMargin = Math.round(centerX - 82f * density);
        glowLp.topMargin = Math.round(centerY - 82f * density);
        stage.addView(glow, glowLp);
        glow.setAlpha(0f);
        glow.setScaleX(0.45f);
        glow.setScaleY(0.45f);

        ImageView hero = new ImageView(overlay.getContext());
        hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadGift(hero, giftIconUrl);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
                Math.round(112f * density), Math.round(112f * density),
                Gravity.TOP | Gravity.LEFT);
        heroLp.leftMargin = Math.round(startX - 56f * density);
        heroLp.topMargin = Math.round(startY - 56f * density);
        stage.addView(hero, heroLp);
        hero.setAlpha(0f);
        hero.setScaleX(0.42f);
        hero.setScaleY(0.42f);

        TextView count = new TextView(overlay.getContext());
        int copies = Math.max(1, quantity);
        count.setText("×" + copies);
        count.setTextColor(0xFFFFFFFF);
        count.setTextSize(17f);
        count.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        count.setGravity(Gravity.CENTER);
        count.setShadowLayer(5f * density, 0f, 2f * density, 0xCC000000);
        FrameLayout.LayoutParams countLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        countLp.topMargin = Math.round(centerY + 54f * density);
        stage.addView(count, countLp);
        count.setAlpha(0f);
        count.setTranslationY(10f * density);

        AnimatorSet reveal = new AnimatorSet();
        reveal.playTogether(
                ObjectAnimator.ofFloat(hero, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(hero, View.X, startX - 56f * density, centerX - 56f * density),
                ObjectAnimator.ofFloat(hero, View.Y, startY - 56f * density, centerY - 56f * density),
                ObjectAnimator.ofFloat(hero, View.SCALE_X, 0.42f, 1.22f, 1f),
                ObjectAnimator.ofFloat(hero, View.SCALE_Y, 0.42f, 1.22f, 1f),
                ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 0.85f, 0.5f),
                ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.45f, 1.1f),
                ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.45f, 1.1f),
                ObjectAnimator.ofFloat(count, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(count, View.TRANSLATION_Y, 10f * density, 0f));
        reveal.setDuration(520L);
        reveal.setInterpolator(new OvershootInterpolator(1.15f));
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (onScatterStart != null) {
                    try { onScatterStart.run(); } catch (Exception ignored) {}
                }
                // Keep the original hero at the verified screen center, then
                // launch one clone along a real path to each requested seat.
                // The center hero remains visible throughout the seat flights.
                scatter(overlay, stage, hero, giftIconUrl, centerX, centerY,
                        micTargets, quantity, personCount, density, cannonBurst, onEnd);
            }
        });
        reveal.start();
    }

    private static void scatter(FrameLayout overlay, FrameLayout stage, ImageView hero,
            String iconUrl, float fromX, float fromY, @Nullable List<PointF> targets,
            int quantity, int personCount, float density, boolean cannonBurst,
            @Nullable Runnable onEnd) {
        List<PointF> seats = new ArrayList<>();
        if (targets != null) for (PointF p : targets) if (p != null) seats.add(p);
        if (seats.isEmpty()) {
            fadeAndRemove(stage, onEnd);
            return;
        }

        // Every flight maps to a real occupied seat supplied by the room.
        // Never create decorative targets over empty seats or arbitrary screen positions.
        hero.animate().cancel();
        hero.setAlpha(1f);
        hero.setScaleX(1f);
        hero.setScaleY(1f);

        int flightCount = seats.size();
        final int[] remaining = {flightCount};
        int size = Math.round(44f * density);

        for (int i = 0; i < flightCount; i++) {
            PointF target = seats.get(i);

            ImageView copy = new ImageView(overlay.getContext());
            copy.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadGift(copy, iconUrl);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            copy.setLayoutParams(lp);
            copy.setX(fromX - size / 2f);
            copy.setY(fromY - size / 2f);
            copy.setAlpha(0f);
            copy.setElevation(60f * density);
            stage.addView(copy);

            final float endX = Math.max(0f, Math.min(overlay.getWidth() - size, target.x - size / 2f));
            final float endY = Math.max(0f, Math.min(overlay.getHeight() - size, target.y - size / 2f));
            final float startX = fromX - size / 2f;
            final float startY = fromY - size / 2f;
            AnimatorSet flight = new AnimatorSet();
            flight.playTogether(
                    ObjectAnimator.ofFloat(copy, View.X, startX, endX),
                    ObjectAnimator.ofFloat(copy, View.Y, startY, endY),
                    ObjectAnimator.ofFloat(copy, View.ALPHA, 0f, 1f, 1f, 0.2f),
                    ObjectAnimator.ofFloat(copy, View.SCALE_X, 0.68f, 1.14f, 0.82f),
                    ObjectAnimator.ofFloat(copy, View.SCALE_Y, 0.68f, 1.14f, 0.82f),
                    ObjectAnimator.ofFloat(copy, View.ROTATION, -14f, 14f, 0f));
            // Cannon volleys are synchronized: every selected mic receives
            // its matching gift in the same burst, with no stagger between seats.
            flight.setStartDelay(cannonBurst ? 0L : (i / 4) * 65L);
            flight.setDuration(660L);
            flight.setInterpolator(new DecelerateInterpolator(1.35f));
            flight.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (copy.getParent() instanceof ViewGroup) {
                        ((ViewGroup) copy.getParent()).removeView(copy);
                    }
                    remaining[0]--;
                    if (remaining[0] == 0) fadeAndRemove(stage, onEnd);
                }
            });
            flight.start();
        }
    }

    private static void loadGift(ImageView view, String url) {
        try {
            Glide.with(view.getContext().getApplicationContext())
                    .load(AssetCatalog.absoluteUrl(url))
                    .placeholder(ImagePlaceholder.gift())
                    .error(ImagePlaceholder.gift())
                    .into(view);
        } catch (Exception ignored) {
            view.setImageResource(ImagePlaceholder.gift());
        }
    }

    /** Winning currency is accounted for by the server; deliberately show no coin flying from screen center. */
    public static void showWinBurst(@Nullable FrameLayout overlay, int multiplier,
            long coinsWon, @Nullable Runnable onEnd) {
        finish(onEnd);
    }

    public static void showWinBurst(@Nullable FrameLayout overlay, int multiplier,
            long coinsWon, @Nullable List<PointF> micTargets, @Nullable Runnable onEnd) {
        finish(onEnd);
    }

    private static void fadeAndRemove(View view, @Nullable Runnable onEnd) {
        view.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            finish(onEnd);
        }).start();
    }

    private static void finish(@Nullable Runnable callback) {
        if (callback != null) try { callback.run(); } catch (Exception ignored) {}
    }
}