package com.Dramizo.Series.presentation.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Modern chat voice waveform — played portion lights up and bars pulse while playing.
 */
public class AudioWaveformView extends View {
    private static final int BAR_COUNT = 28;

    private final Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private final float[] heights = new float[BAR_COUNT];

    private float progress; // 0..1
    private boolean animating;
    private float pulsePhase;
    private ValueAnimator animator;
    private int playedColor = 0xFFFFFFFF;
    private int idleColor = 0x66FFFFFF;

    public AudioWaveformView(Context context) {
        super(context);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        playedPaint.setStyle(Paint.Style.FILL);
        idlePaint.setStyle(Paint.Style.FILL);
        playedPaint.setColor(playedColor);
        idlePaint.setColor(idleColor);
        // Deterministic modern pattern (not flat WA/Telegram bars).
        float[] pattern = {
                0.28f, 0.48f, 0.72f, 0.40f, 0.90f, 0.55f, 0.35f, 0.78f,
                0.62f, 0.30f, 0.85f, 0.50f, 0.68f, 0.42f, 0.95f, 0.58f,
                0.33f, 0.76f, 0.46f, 0.88f, 0.38f, 0.70f, 0.52f, 0.82f,
                0.36f, 0.64f, 0.44f, 0.74f
        };
        System.arraycopy(pattern, 0, heights, 0, BAR_COUNT);
    }

    public void setWaveColors(int played, int idle) {
        playedColor = played;
        idleColor = idle;
        playedPaint.setColor(played);
        idlePaint.setColor(idle);
        invalidate();
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setAnimating(boolean on) {
        if (animating == on) return;
        animating = on;
        if (on) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
                animator.setDuration(1100L);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(a -> {
                    pulsePhase = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!animator.isStarted()) animator.start();
        } else if (animator != null) {
            animator.cancel();
            pulsePhase = 0f;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        setAnimating(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;

        float gap = Math.max(2f, w / (BAR_COUNT * 3.2f));
        float barW = (w - gap * (BAR_COUNT - 1)) / BAR_COUNT;
        float radius = barW / 2f;
        float midY = getPaddingTop() + h / 2f;
        float playedUntilX = getPaddingLeft() + w * progress;

        for (int i = 0; i < BAR_COUNT; i++) {
            float base = heights[i];
            float amp = base;
            if (animating) {
                // Traveling pulse — bars "bloom" while audio plays.
                float wave = (float) (0.55 + 0.45 * Math.sin(pulsePhase + i * 0.42));
                amp = Math.min(1f, base * (0.55f + 0.75f * wave));
            }
            float barH = Math.max(barW * 1.4f, h * amp);
            float left = getPaddingLeft() + i * (barW + gap);
            float top = midY - barH / 2f;
            float bottom = midY + barH / 2f;
            barRect.set(left, top, left + barW, bottom);
            boolean played = (left + barW / 2f) <= playedUntilX;
            canvas.drawRoundRect(barRect, radius, radius, played ? playedPaint : idlePaint);
        }
    }
}
