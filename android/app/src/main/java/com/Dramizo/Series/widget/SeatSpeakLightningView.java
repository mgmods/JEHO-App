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
            setAlpha(1f);
            startAnim();
        } else {
            active = false;
            intensity = 0.55f;
            stopAnim();
            setVisibility(INVISIBLE);
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
        float maxR = Math.min(cx, cy);
        float density = getResources().getDisplayMetrics().density;

        // Avatar is ~52dp inside a ~100dp cell → ring starts just outside the face.
        float avatarR = maxR * 0.52f;
        float breath = 1f + 0.06f * (float) Math.sin(phase * Math.PI * 2.0);
        float amp = 0.85f + 0.35f * intensity;

        // Soft radial glow under rings (premium live apps look).
        float glowR = avatarR * (1.55f + 0.35f * intensity) * breath;
        int glowKey = (int) (glowR * 10) + (int) (intensity * 40);
        if (glowShader == null || glowKey != lastGlowKey) {
            int inner = withAlpha(0xFF4DE8FF, 0.22f + 0.18f * intensity);
            int mid = withAlpha(0xFFFFD54F, 0.10f + 0.08f * intensity);
            int outer = 0x00000000;
            glowShader = new RadialGradient(
                    cx, cy, Math.max(1f, glowR),
                    new int[]{inner, mid, outer},
                    new float[]{0.35f, 0.7f, 1f},
                    Shader.TileMode.CLAMP);
            lastGlowKey = glowKey;
        }
        glowPaint.setShader(glowShader);
        canvas.drawCircle(cx, cy, glowR, glowPaint);
        glowPaint.setShader(null);

        // Stable luminous core ring hugging the avatar edge.
        float coreStroke = Math.max(1.8f * density, maxR * 0.035f);
        coreRingPaint.setStrokeWidth(coreStroke);
        coreRingPaint.setColor(withAlpha(0xFFFFFFFF, 0.35f + 0.45f * intensity));
        canvas.drawCircle(cx, cy, avatarR * 1.02f * breath, coreRingPaint);
        coreRingPaint.setStrokeWidth(coreStroke * 0.55f);
        coreRingPaint.setColor(withAlpha(0xFF40E0FF, 0.55f + 0.35f * intensity));
        canvas.drawCircle(cx, cy, avatarR * 1.02f * breath, coreRingPaint);

        // 4 expanding sound-wave rings (phase staggered).
        int rings = 4;
        float travel = maxR * 0.48f * amp;
        for (int i = 0; i < rings; i++) {
            float t = (phase + i / (float) rings) % 1f;
            float ease = 1f - (1f - t) * (1f - t); // ease-out expand
            float r = avatarR * 1.06f + travel * ease;
            if (r > maxR * 0.98f) continue;
            float fade = (1f - t);
            fade = fade * fade;
            float a = fade * (0.55f + 0.4f * intensity);
            float sw = Math.max(1.4f * density, maxR * 0.028f) * (1.25f - t * 0.55f);

            // Outer soft halo stroke
            ringPaint.setStrokeWidth(sw * 2.1f);
            ringPaint.setColor(withAlpha(waveColor(i), a * 0.28f));
            canvas.drawCircle(cx, cy, r, ringPaint);

            // Sharp primary wave
            ringPaint.setStrokeWidth(sw);
            ringPaint.setColor(withAlpha(waveColor(i), a));
            canvas.drawCircle(cx, cy, r, ringPaint);
        }
    }

    private static int waveColor(int index) {
        switch (index % 4) {
            case 0: return 0xFF7CFFF0; // mint cyan
            case 1: return 0xFFFFFFFF; // white
            case 2: return 0xFFFFE082; // soft gold
            default: return 0xFFB388FF; // soft violet accent
        }
    }

    private static int withAlpha(int color, float a) {
        int aa = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (color & 0x00FFFFFF) | (aa << 24);
    }
}
