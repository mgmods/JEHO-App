package com.Dramizo.Series.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Professional seating mic waves — concentric soft rings + glow that expand
 * outside the avatar while the user is speaking (RTC sound-level driven).
 */
public class SeatSpeakLightningView extends View {
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable private ValueAnimator animator;
    private float phase;
    private float intensity = 0.72f;
    private boolean active;
    @Nullable private RadialGradient glowShader;
    private int lastGlowKey;

    public SeatSpeakLightningView(Context context) {
        super(context);
        init();
    }

    public SeatSpeakLightningView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SeatSpeakLightningView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        // Software layer: soft glow blends better on many devices.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        coreRingPaint.setStyle(Paint.Style.STROKE);
        coreRingPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.FILL);
        setVisibility(INVISIBLE);
        setClickable(false);
        setFocusable(false);
    }

    public void setSpeaking(boolean speaking) {
        if (speaking) {
            if (active && animator != null && animator.isRunning()) {
                invalidate();
                return;
            }
            active = true;
            setVisibility(VISIBLE);
            animate().cancel();
            setAlpha(1f);
            startAnim();
        } else {
            if (!active) return;
            active = false;
            intensity = 0.55f;
            stopAnim();
            animate().cancel();
            animate().alpha(0f).setDuration(140L).withEndAction(() -> {
                if (!active) {
                    setVisibility(INVISIBLE);
                    setAlpha(1f);
                }
            }).start();
            invalidate();
        }
    }

    /**
     * 0–100 sound level from Zego/LiveKit; drives ring amplitude & opacity.
     */
    public void setIntensity(float level0to100) {
        float n = Math.max(0f, Math.min(100f, level0to100)) / 100f;
        // Keep a lively floor while speaking so quiet speech still pulses.
        intensity = 0.42f + 0.58f * n;
        if (active) invalidate();
    }

    public boolean isSpeakingActive() {
        return active;
    }

    private void startAnim() {
        stopAnim();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1100);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            if (!active) return;
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnim() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        glowShader = null;
        lastGlowKey = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active || getWidth() <= 0 || getHeight() <= 0) return;
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float density = getResources().getDisplayMetrics().density;
        float radius = Math.min(cx, cy) * 0.88f;
        float pulse = 1f + 0.045f * (float) Math.sin(phase * Math.PI * 2d);
        float audio = 0.55f + 0.45f * intensity;
        float ringRadius = radius * pulse;
        float stroke = Math.max(2f * density, radius * 0.055f);
        // One luminous ring hugs the mic/avatar boundary; audio level controls its energy.
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(stroke * 2.4f);
        ringPaint.setColor(withAlpha(0xFF40DFFF, 0.16f + 0.18f * audio));
        canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setColor(withAlpha(0xFFFFD76A, 0.45f + 0.45f * audio));
        canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        coreRingPaint.setStrokeWidth(Math.max(1f * density, stroke * 0.38f));
        coreRingPaint.setColor(withAlpha(0xFFFFFFFF, 0.28f + 0.48f * audio));
        canvas.drawCircle(cx, cy, ringRadius - stroke * 0.7f, coreRingPaint);
    }

    private static int withAlpha(int color, float a) {
        int aa = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (color & 0x00FFFFFF) | (aa << 24);
    }
}
