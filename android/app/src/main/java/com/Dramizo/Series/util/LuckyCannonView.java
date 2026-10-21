package com.Dramizo.Series.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Lightweight locally drawn 3D lucky-gem button. No textures, network calls,
 * or per-frame server updates; the room Activity owns drag and send behavior.
 */
public final class LuckyCannonView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float recoil;
    private float fireProgress;
    private ValueAnimator fireAnimator;

    public LuckyCannonView(Context context) { super(context); init(); }
    public LuckyCannonView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public LuckyCannonView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setContentDescription("زر هدايا المحظوظ");
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    /** Short tactile-looking pulse used when the existing send flow launches. */
    public void fire() {
        if (fireAnimator != null) fireAnimator.cancel();
        fireAnimator = ValueAnimator.ofFloat(0f, 1f);
        fireAnimator.setDuration(360L);
        fireAnimator.setInterpolator(new DecelerateInterpolator());
        fireAnimator.addUpdateListener(a -> {
            fireProgress = (float) a.getAnimatedValue();
            recoil = (float) Math.sin(fireProgress * Math.PI) * (1f - fireProgress) * 0.07f;
            invalidate();
        });
        fireAnimator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.scale(w / 100f, h / 100f);
        canvas.translate(0, recoil * 100f);

        // Soft outer glow, limited to the short launch animation.
        if (fireProgress > 0f && fireProgress < 0.7f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(12, 10, 88, 92,
                    new int[]{0x00FF3D72, 0x66FFB62E, 0x003EDBFF},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(50, 49, 49, paint);
            paint.setShader(null);
        }

        // Deep dimensional shadow and polished gold outer bezel.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        canvas.drawCircle(50, 52, 47, paint);
        paint.setShader(new LinearGradient(16, 8, 82, 94,
                new int[]{0xFFFFF0A8, 0xFFB87916, 0xFFFFD86B, 0xFF70400D},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 48, 46, paint);
        paint.setShader(null);

        // Inner metallic highlight ring.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        paint.setColor(0xFFFFF0B3);
        canvas.drawCircle(50, 47, 41.5f, paint);
        paint.setStyle(Paint.Style.FILL);

        // Ruby / crimson enamel face.
        paint.setShader(new LinearGradient(23, 13, 76, 83,
                new int[]{0xFFFF6C83, 0xFFCE123D, 0xFF760B2B, 0xFF36051D},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 47, 39.5f, paint);
        paint.setShader(null);

        // Gloss highlight along the upper edge.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x99FFFFFF);
        canvas.drawArc(20, 16, 80, 76, 205, 125, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // Diamond silhouette with icy cyan facets.
        Path diamond = new Path();
        diamond.moveTo(50, 21);
        diamond.lineTo(70, 39);
        diamond.lineTo(50, 72);
        diamond.lineTo(30, 39);
        diamond.close();
        paint.setShader(new LinearGradient(31, 22, 68, 70,
                new int[]{0xFFFFFFFF, 0xFF6BF4FF, 0xFF168BE8, 0xFFB7FFFF},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(diamond, paint);
        paint.setShader(null);

        // Crisp facets give the gem a cut-glass, three-dimensional look.
        paint.setColor(0xFFDAFFFF);
        Path leftFacet = new Path();
        leftFacet.moveTo(30, 39); leftFacet.lineTo(43, 39);
        leftFacet.lineTo(50, 21); leftFacet.close();
        canvas.drawPath(leftFacet, paint);
        paint.setColor(0xFF5DD8FF);
        Path rightFacet = new Path();
        rightFacet.moveTo(50, 21); rightFacet.lineTo(57, 39);
        rightFacet.lineTo(70, 39); rightFacet.close();
        canvas.drawPath(rightFacet, paint);
        paint.setColor(0xFF0875D1);
        Path lowerLeft = new Path();
        lowerLeft.moveTo(30, 39); lowerLeft.lineTo(50, 72);
        lowerLeft.lineTo(43, 39); lowerLeft.close();
        canvas.drawPath(lowerLeft, paint);
        paint.setColor(0xFF8CFBFF);
        Path lowerRight = new Path();
        lowerRight.moveTo(43, 39); lowerRight.lineTo(57, 39);
        lowerRight.lineTo(50, 72); lowerRight.close();
        canvas.drawPath(lowerRight, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0xCCFFFFFF);
        canvas.drawPath(diamond, paint);
        paint.setStyle(Paint.Style.FILL);

        // Tiny gold/cyan particles fly out from the button during the local press pulse.
        if (fireProgress > 0f && fireProgress < 0.72f) {
            float progress = fireProgress / 0.72f;
            float alpha = 1f - progress;
            int[] particleColors = {0xFFFFE8A0, 0xFF7CF7FF, 0xFFFF8BB2, 0xFFFFFFFF,
                    0xFFFFD15C, 0xFF92DFFF, 0xFFFFA6CB, 0xFFFFF2C1};
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(-90 + i * 45);
                float distance = 17f + progress * 29f;
                float x = 50f + (float) Math.cos(angle) * distance;
                float y = 47f + (float) Math.sin(angle) * distance;
                paint.setColor(((int) (255 * alpha) << 24) | (particleColors[i] & 0x00FFFFFF));
                canvas.drawCircle(x, y, 1.2f + alpha * 1.5f, paint);
                paint.setStrokeWidth(1.1f);
                canvas.drawLine(x, y, x - (float) Math.cos(angle) * (3f + progress * 3f),
                        y - (float) Math.sin(angle) * (3f + progress * 3f), paint);
            }
        }
        canvas.restore();
    }

    public void release() {
        if (fireAnimator != null) { fireAnimator.cancel(); fireAnimator = null; }
    }
}
