package com.Dramizo.Series.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Lightweight, locally rendered premium lucky-gift launcher.
 * Drawn with Canvas only; no textures, network calls, or per-frame server updates.
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
        setContentDescription("مدفع هدايا المحظوظ — اسحب لتحريكه واضغط للإطلاق");
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void fire() {
        if (fireAnimator != null) fireAnimator.cancel();
        fireAnimator = ValueAnimator.ofFloat(0f, 1f);
        fireAnimator.setDuration(420L);
        fireAnimator.setInterpolator(new DecelerateInterpolator());
        fireAnimator.addUpdateListener(a -> {
            fireProgress = (float) a.getAnimatedValue();
            recoil = (float) Math.sin(fireProgress * Math.PI) * (1f - fireProgress) * 0.16f;
            invalidate();
        });
        fireAnimator.start();
    }

    private void fillGradient(Canvas canvas, Path path, int top, int middle, int bottom) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(20, 20, 80, 82,
                new int[]{top, middle, bottom}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.scale(w / 100f, h / 100f);

        // Jewel-like dark violet medallion with a restrained gold rim.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(42, 38, 49,
                new int[]{0xEE382D62, 0xE8171738, 0xD9081020},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 50, 47, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setColor(0xD9F7D78A);
        canvas.drawCircle(50, 50, 45.5f, paint);
        paint.setStrokeWidth(0.8f);
        paint.setColor(0x887CEBFF);
        canvas.drawCircle(50, 50, 41.5f, paint);
        paint.setStyle(Paint.Style.FILL);

        // Soft cyan/pink flare, only while firing.
        if (fireProgress > 0f && fireProgress < 0.72f) {
            paint.setShader(new RadialGradient(79, 31, 29,
                    new int[]{0x99FFF1B8, 0x449F73FF, 0x00FF5FAD},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(70, 39, 30, paint);
            paint.setShader(null);
        }

        // Compact jeweled carriage and curved support.
        Path carriage = new Path();
        carriage.moveTo(21, 63); carriage.quadTo(50, 56, 77, 62);
        carriage.lineTo(82, 72); carriage.quadTo(51, 84, 19, 73); carriage.close();
        fillGradient(canvas, carriage, 0xFFFFE8A0, 0xFFB97A27, 0xFF57345E);

        Path support = new Path();
        support.moveTo(39, 53); support.lineTo(62, 49); support.lineTo(66, 66);
        support.lineTo(36, 69); support.close();
        fillGradient(canvas, support, 0xFFFCE9B4, 0xFFB9863E, 0xFF543A70);

        // Barrel and receiver, angled upward like a compact fantasy launcher.
        canvas.save();
        canvas.rotate(-17f, 50f, 48f);
        canvas.translate(-recoil * 100f, 0f);

        Path barrel = new Path();
        barrel.moveTo(24, 39); barrel.quadTo(43, 32, 71, 36);
        barrel.lineTo(77, 44); barrel.lineTo(72, 52);
        barrel.lineTo(29, 53); barrel.close();
        fillGradient(canvas, barrel, 0xFFFFF2C7, 0xFFD1A34D, 0xFF65427C);

        // Deep enamel channel on the barrel.
        Path enamel = new Path();
        enamel.moveTo(31, 41); enamel.lineTo(68, 38);
        enamel.lineTo(71, 44); enamel.lineTo(32, 48); enamel.close();
        paint.setShader(new LinearGradient(30, 40, 68, 47,
                new int[]{0xFF202A58, 0xFF6D4BA1, 0xFF253A68}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(enamel, paint);
        paint.setShader(null);

        // Gold rails and a bright muzzle ring.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFFFE7A1);
        canvas.drawLine(31, 39, 66, 36.5f, paint);
        canvas.drawLine(33, 51, 68, 49, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(72, 34, 84, 50,
                new int[]{0xFFFFF1B7, 0xFFDEA64A, 0xFF7540A0}, null, Shader.TileMode.CLAMP));
        canvas.drawOval(70, 33, 84, 51, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFF251A48);
        canvas.drawOval(74, 36, 82, 48, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF8AF4FF);
        canvas.drawCircle(78, 42, 2.3f, paint);
        canvas.restore();

        // Side chamber with a glowing cyan jewel.
        paint.setShader(new LinearGradient(34, 45, 48, 62,
                new int[]{0xFFFFF1B9, 0xFFDA9D3C, 0xFF6E438A}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(35, 48, 51, 62, 5, 5, paint);
        paint.setShader(null);
        paint.setColor(0xFF29365F);
        canvas.drawRoundRect(38, 50, 48, 60, 3, 3, paint);
        paint.setShader(new RadialGradient(42, 54, 7,
                new int[]{0xFFFFFFFF, 0xFF8CF7FF, 0xFF4C72D9},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(43, 55, 4.2f, paint);
        paint.setShader(null);

        // Two small polished wheels and gold hubs.
        for (float x : new float[]{32f, 66f}) {
            paint.setColor(0xFF17182D);
            canvas.drawCircle(x, 70, 8.5f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(0xFFFFD982);
            canvas.drawCircle(x, 70, 7f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF6B4A93);
            canvas.drawCircle(x, 70, 3.2f, paint);
            paint.setColor(0xFFFFF0B5);
            canvas.drawCircle(x, 70, 1.4f, paint);
        }

        // Gold sparks and a small four-point glint on firing.
        if (fireProgress > 0f && fireProgress < 0.52f) {
            float fade = 1f - fireProgress / 0.52f;
            paint.setColor(((int) (255 * fade) << 24) | 0x00FFF0A0);
            paint.setStrokeWidth(1.8f);
            for (int i = 0; i < 6; i++) {
                double a = (i * Math.PI / 3.0) - 0.8;
                float radius = 7f + fireProgress * 17f;
                float sx = 81f + (float) Math.cos(a) * radius;
                float sy = 39f + (float) Math.sin(a) * radius;
                canvas.drawLine(sx, sy, sx + (float) Math.cos(a) * 3.5f,
                        sy + (float) Math.sin(a) * 3.5f, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFF0B8);
        canvas.drawCircle(20, 29, 1.5f, paint);
        canvas.drawCircle(77, 72, 1.2f, paint);
        canvas.restore();
    }

    public void release() {
        if (fireAnimator != null) { fireAnimator.cancel(); fireAnimator = null; }
    }
}
