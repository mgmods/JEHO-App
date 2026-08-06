package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Room gift flight + professional all-mic clone scatter (Mikoo-style).
 * Center hero holds → simultaneous clones arc to every selected mic.
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
     * @deprecated Prefer {@link #allMicClone} for full banner + simultaneous scatter.
     */
    @Deprecated
    public static void holdAndScatter(
            @Nullable FrameLayout overlay,
            @Nullable String iconUrl,
            @Nullable PointF center,
            @Nullable List<PointF> seats,
            long holdMs,
            @Nullable Runnable onScatterStart,
            @Nullable Runnable onEnd
    ) {
        allMicClone(overlay, iconUrl, center, seats, 1, 0, null, holdMs, onScatterStart, onEnd);
    }

    /**
     * Mikoo all-mic send:
     * large gift in center + "Nx" label + banner "X إرسال للكل على المايك"
     * then simultaneous clone arcs to every mic.
     *
     * @param qtyShown  value painted next to the hero (e.g. 10 → "10x")
     * @param totalScore optional big score strip (coins × people); 0 hides it
     */
    public static void allMicClone(
            @Nullable FrameLayout overlay,
            @Nullable String iconUrl,
            @Nullable PointF center,
            @Nullable List<PointF> seats,
            int qtyShown,
            long totalScore,
            @Nullable String senderName,
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
        PointF mid = center != null ? center : new PointF(w / 2f, h * 0.40f);
        List<PointF> targets = seats != null ? new ArrayList<>(seats) : new ArrayList<>();

        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        try {
            overlay.setElevation(40f);
        } catch (Exception ignored) {
        }

        // ——— Stage root for hero + labels (easier fade) ———
        FrameLayout stage = new FrameLayout(overlay.getContext());
        FrameLayout.LayoutParams stageLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        stage.setLayoutParams(stageLp);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        overlay.addView(stage);

        int heroSize = Math.round(118f * density);
        ImageView hero = newImage(overlay, iconUrl, heroSize);
        hero.setX(mid.x - heroSize / 2f);
        hero.setY(mid.y - heroSize / 2f - 10f * density);
        hero.setScaleX(0.12f);
        hero.setScaleY(0.12f);
        hero.setAlpha(0f);
        stage.addView(hero);

        // Soft glow behind gift
        View glow = new View(overlay.getContext());
        int glowSize = Math.round(160f * density);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(glowSize, glowSize);
        glow.setLayoutParams(glowLp);
        GradientDrawable glowBg = new GradientDrawable();
        glowBg.setShape(GradientDrawable.OVAL);
        glowBg.setColors(new int[]{0x55FFC107, 0x00FF9800});
        glow.setBackground(glowBg);
        glow.setX(mid.x - glowSize / 2f);
        glow.setY(mid.y - glowSize / 2f - 10f * density);
        glow.setAlpha(0f);
        stage.addView(glow, 0);

        // Multiplier row: "10×" + mini gift (white, huge — as in screenshots)
        LinearLayout multRow = new LinearLayout(overlay.getContext());
        multRow.setOrientation(LinearLayout.HORIZONTAL);
        multRow.setGravity(Gravity.CENTER_VERTICAL);
        multRow.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int showQty = Math.max(1, qtyShown);
        TextView multTv = new TextView(overlay.getContext());
        multTv.setText(String.format(Locale.US, "%dx", showQty));
        multTv.setTextColor(0xFFFFFFFF);
        multTv.setTextSize(34f);
        multTv.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        multTv.setShadowLayer(10f * density, 0f, 3f * density, 0xEE000000);
        multRow.addView(multTv);
        ImageView mini = newImage(overlay, iconUrl, Math.round(36f * density));
        LinearLayout.LayoutParams miniLp = new LinearLayout.LayoutParams(
                Math.round(36f * density), Math.round(36f * density));
        miniLp.setMarginStart(Math.round(6f * density));
        mini.setLayoutParams(miniLp);
        multRow.addView(mini);
        multRow.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        multRow.setX(mid.x - multRow.getMeasuredWidth() / 2f);
        multRow.setY(mid.y - heroSize * 0.55f - 28f * density);
        multRow.setAlpha(0f);
        multRow.setScaleX(0.5f);
        multRow.setScaleY(0.5f);
        stage.addView(multRow);

        // Banner: «Name إرسال للكل على المايك»
        TextView banner = new TextView(overlay.getContext());
        String who = senderName != null && !senderName.trim().isEmpty()
                ? senderName.trim() : "مستخدم";
        banner.setText(who + "  إرسال للكل على المايك");
        banner.setTextColor(0xFFFFFFFF);
        banner.setTextSize(13.5f);
        banner.setTypeface(Typeface.DEFAULT_BOLD);
        banner.setMaxLines(1);
        banner.setGravity(Gravity.CENTER);
        int padH = Math.round(14f * density);
        int padV = Math.round(7f * density);
        banner.setPadding(padH, padV, padH, padV);
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setCornerRadius(20f * density);
        bannerBg.setColor(0xBB1A0A18);
        bannerBg.setStroke(Math.round(1.2f * density), 0x66FFD54F);
        banner.setBackground(bannerBg);
        banner.setShadowLayer(4f * density, 0f, 1f * density, 0x88000000);
        banner.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        banner.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float banW = banner.getMeasuredWidth();
        banner.setX(Math.max(10f * density, Math.min(mid.x - banW / 2f, w - banW - 10f * density)));
        banner.setY(mid.y + heroSize * 0.42f + 8f * density);
        banner.setAlpha(0f);
        banner.setTranslationY(12f * density);
        stage.addView(banner);

        // Optional total score chip (like “225000x”)
        TextView scoreChip = null;
        if (totalScore > showQty) {
            scoreChip = new TextView(overlay.getContext());
            scoreChip.setText(formatScore(totalScore) + "x");
            scoreChip.setTextColor(0xFFFFF59D);
            scoreChip.setTextSize(15f);
            scoreChip.setTypeface(Typeface.DEFAULT_BOLD);
            scoreChip.setPadding(padH, padV, padH, padV);
            GradientDrawable scBg = new GradientDrawable();
            scBg.setCornerRadius(16f * density);
            scBg.setColor(0xCC5D3A00);
            scBg.setStroke(Math.round(1f * density), 0xFFFFC107);
            scoreChip.setBackground(scBg);
            scoreChip.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scoreChip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            scoreChip.setX(Math.max(8f * density, mid.x - scoreChip.getMeasuredWidth() / 2f));
            scoreChip.setY(Math.max(48f * density, mid.y - heroSize - 52f * density));
            scoreChip.setAlpha(0f);
            stage.addView(scoreChip);
        }
        final TextView scoreChipFinal = scoreChip;

        // Deco spark icons around hero (coins / mini gifts)
        List<ImageView> sparks = spawnSparks(stage, iconUrl, mid, density);

        // Pop-in
        AnimatorSet pop = new AnimatorSet();
        List<Animator> parts = new ArrayList<>();
        parts.add(ObjectAnimator.ofFloat(hero, View.SCALE_X, 0.12f, 1.2f, 1f));
        parts.add(ObjectAnimator.ofFloat(hero, View.SCALE_Y, 0.12f, 1.2f, 1f));
        parts.add(ObjectAnimator.ofFloat(hero, View.ALPHA, 0f, 1f));
        parts.add(ObjectAnimator.ofFloat(hero, View.ROTATION, -14f, 10f, 0f));
        parts.add(ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 0.9f, 0.55f));
        parts.add(ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.4f, 1.1f));
        parts.add(ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.4f, 1.1f));
        parts.add(ObjectAnimator.ofFloat(multRow, View.ALPHA, 0f, 1f));
        parts.add(ObjectAnimator.ofFloat(multRow, View.SCALE_X, 0.5f, 1.08f, 1f));
        parts.add(ObjectAnimator.ofFloat(multRow, View.SCALE_Y, 0.5f, 1.08f, 1f));
        parts.add(ObjectAnimator.ofFloat(banner, View.ALPHA, 0f, 1f));
        parts.add(ObjectAnimator.ofFloat(banner, View.TRANSLATION_Y, 12f * density, 0f));
        if (scoreChipFinal != null) {
            parts.add(ObjectAnimator.ofFloat(scoreChipFinal, View.ALPHA, 0f, 1f));
            parts.add(ObjectAnimator.ofFloat(scoreChipFinal, View.SCALE_X, 0.6f, 1f));
            parts.add(ObjectAnimator.ofFloat(scoreChipFinal, View.SCALE_Y, 0.6f, 1f));
        }
        pop.playTogether(parts);
        pop.setDuration(680);
        pop.setInterpolator(new OvershootInterpolator(1.35f));

        // Gentle breathing on hero while holding
        ObjectAnimator breatheX = ObjectAnimator.ofFloat(hero, View.SCALE_X, 1f, 1.06f, 1f);
        ObjectAnimator breatheY = ObjectAnimator.ofFloat(hero, View.SCALE_Y, 1f, 1.06f, 1f);
        breatheX.setDuration(900);
        breatheY.setDuration(900);
        breatheX.setRepeatCount(ValueAnimator.INFINITE);
        breatheY.setRepeatCount(ValueAnimator.INFINITE);
        breatheX.setInterpolator(new LinearInterpolator());
        breatheY.setInterpolator(new LinearInterpolator());

        pop.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                try {
                    breatheX.start();
                    breatheY.start();
                } catch (Exception ignored) {
                }
                long hold = Math.max(480L, holdMs);
                stage.postDelayed(() -> {
                    try {
                        breatheX.cancel();
                        breatheY.cancel();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (onScatterStart != null) onScatterStart.run();
                    } catch (Exception ignored) {
                    }
                    if (targets.isEmpty()) {
                        fadeStage(stage, onEnd);
                        return;
                    }
                    // Keep hero visible during launch, then fade as clones fly.
                    scatterClonesRadial(
                            overlay, iconUrl, mid, targets, density, showQty, () -> {
                                fadeStage(stage, onEnd);
                            });
                    // Dim hero/banner while clones leave (not remove immediately).
                    stage.animate()
                            .alpha(0.15f)
                            .setDuration(420)
                            .setStartDelay(180)
                            .start();
                }, hold);
            }
        });

        try {
            pop.start();
            animateSparks(sparks, density);
        } catch (OutOfMemoryError | Exception e) {
            remove(stage);
            if (onEnd != null) onEnd.run();
        }
    }

    private static String formatScore(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000d);
        if (n >= 10_000) return String.format(Locale.US, "%.1fk", n / 1000d);
        return String.format(Locale.US, "%,d", n);
    }

    private static List<ImageView> spawnSparks(
            FrameLayout stage, @Nullable String iconUrl, PointF mid, float density) {
        List<ImageView> list = new ArrayList<>();
        int n = 8;
        int size = Math.round(18f * density);
        for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2 * i) / n;
            float r = 54f * density + (i % 3) * 8f * density;
            ImageView sp = newImage(stage, iconUrl, size);
            sp.setX(mid.x + (float) Math.cos(ang) * r - size / 2f);
            sp.setY(mid.y + (float) Math.sin(ang) * r - size / 2f);
            sp.setAlpha(0f);
            sp.setScaleX(0.3f);
            sp.setScaleY(0.3f);
            stage.addView(sp);
            list.add(sp);
        }
        return list;
    }

    private static void animateSparks(List<ImageView> sparks, float density) {
        if (sparks == null) return;
        for (int i = 0; i < sparks.size(); i++) {
            ImageView sp = sparks.get(i);
            long delay = 40L + i * 35L;
            sp.animate()
                    .alpha(0.85f)
                    .scaleX(1f).scaleY(1f)
                    .rotationBy((i % 2 == 0 ? 1 : -1) * 40f)
                    .setStartDelay(delay)
                    .setDuration(500)
                    .withEndAction(() -> sp.animate()
                            .translationYBy(-10f * density)
                            .alpha(0.35f)
                            .setDuration(900)
                            .setInterpolator(new LinearInterpolator())
                            .start())
                    .start();
        }
    }

    private static void fadeStage(View stage, @Nullable Runnable onEnd) {
        if (stage == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        stage.animate().cancel();
        stage.animate()
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> {
                    remove(stage);
                    if (onEnd != null) {
                        try { onEnd.run(); } catch (Exception ignored) {}
                    }
                })
                .start();
    }

    /**
     * Simultaneous radial scatter — all clones launch almost together (not max 8 sequential).
     */
    private static void scatterClonesRadial(
            FrameLayout overlay,
            @Nullable String iconUrl,
            PointF from,
            List<PointF> seats,
            float density,
            int qtyLabel,
            @Nullable Runnable onEnd
    ) {
        int maxSeats = Math.min(seats != null ? seats.size() : 0, 24);
        if (maxSeats <= 0) {
            if (onEnd != null) onEnd.run();
            return;
        }
        final int[] left = {maxSeats};
        int size = Math.round(48f * density);
        PathInterpolator flight = new PathInterpolator(0.22f, 0.9f, 0.35f, 1f);

        for (int i = 0; i < maxSeats; i++) {
            PointF to = seats.get(i);
            if (to == null) {
                left[0]--;
                if (left[0] <= 0 && onEnd != null) onEnd.run();
                continue;
            }
            final int idx = i;
            ImageView clone = newImage(overlay, iconUrl, size);
            float fromX = from.x - size / 2f;
            float fromY = from.y - size / 2f;
            clone.setX(fromX);
            clone.setY(fromY);
            clone.setScaleX(0.85f);
            clone.setScaleY(0.85f);
            clone.setAlpha(0.15f);
            clone.setElevation(20f + idx);
            overlay.addView(clone);

            // Tiny qty badge on clone head for multi-qty feel
            if (qtyLabel > 1 && idx < 6) {
                // keep visual clean — skip per-clone text for density
            }

            // Arc mid control point (outward bulge)
            float dx = to.x - from.x;
            float dy = to.y - from.y;
            float dist = (float) Math.hypot(dx, dy);
            float nx = dist > 1 ? -dy / dist : 0f;
            float ny = dist > 1 ? dx / dist : 0f;
            float bulge = Math.min(90f * density, dist * 0.28f) * ((idx % 2 == 0) ? 1f : -0.75f);
            final float midX = (from.x + to.x) / 2f + nx * bulge - size / 2f;
            final float midY = (from.y + to.y) / 2f + ny * bulge - Math.min(40f * density, dist * 0.12f) - size / 2f;
            final float endX = to.x - size / 2f;
            final float endY = to.y - size / 2f;

            long delay = (idx % 3) * 18L; // near-simultaneous
            long duration = 720L + (idx % 5) * 28L;

            ValueAnimator pathAnim = ValueAnimator.ofFloat(0f, 1f);
            pathAnim.setStartDelay(delay);
            pathAnim.setDuration(duration);
            pathAnim.setInterpolator(flight);
            pathAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                // Quadratic bezier
                float u = 1f - t;
                float x = u * u * fromX + 2 * u * t * midX + t * t * endX;
                float y = u * u * fromY + 2 * u * t * midY + t * t * endY;
                clone.setX(x);
                clone.setY(y);
                float scale = 0.9f + 0.35f * (float) Math.sin(t * Math.PI);
                if (t > 0.75f) {
                    scale *= (1f - (t - 0.75f) / 0.25f) * 0.55f + 0.45f;
                }
                clone.setScaleX(scale);
                clone.setScaleY(scale);
                float alpha = t < 0.12f ? t / 0.12f : (t > 0.82f ? (1f - t) / 0.18f : 1f);
                clone.setAlpha(Math.max(0f, Math.min(1f, alpha)));
                clone.setRotation((idx % 2 == 0 ? 1f : -1f) * t * (28f + (idx % 4) * 6f));
            });
            pathAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Landing pop
                    clone.animate()
                            .scaleX(1.15f).scaleY(1.15f)
                            .setDuration(90)
                            .withEndAction(() -> clone.animate()
                                    .scaleX(0.2f).scaleY(0.2f).alpha(0f)
                                    .setDuration(160)
                                    .withEndAction(() -> {
                                        remove(clone);
                                        left[0]--;
                                        if (left[0] <= 0 && onEnd != null) {
                                            try { onEnd.run(); } catch (Exception ignored) {}
                                        }
                                    })
                                    .start())
                            .start();
                }
            });
            pathAnim.start();
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
        tv.setTypeface(Typeface.DEFAULT_BOLD);
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
                .scaleX(1.14f).scaleY(1.14f)
                .setDuration(160)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                .start();
    }

    private static ImageView newImage(ViewGroup overlay, @Nullable String iconUrl, int size) {
        ImageView icon = new ImageView(overlay.getContext());
        icon.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (iconUrl != null && !iconUrl.isEmpty()) {
            try {
                Glide.with(overlay.getContext().getApplicationContext())
                        .load(AssetCatalog.absoluteUrl(iconUrl))
                        .placeholder(ImagePlaceholder.gift())
                        .error(ImagePlaceholder.gift())
                        .into(icon);
            } catch (Throwable e) {
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
