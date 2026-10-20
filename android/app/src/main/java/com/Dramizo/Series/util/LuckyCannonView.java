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
 * Lightweight, locally rendered golden cannon control for the voice room.
 * No textures, network calls, or per-frame server updates are used.
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("مدفع هدايا المحظوظ");
        setClickable(true);
        setFocusable(true);
    }

    public void fire() {
        if (fireAnimator != null) fireAnimator.cancel();
        fireAnimator = ValueAnimator.ofFloat(0f, 1f);
        fireAnimator.setDuration(360L);
        fireAnimator.setInterpolator(new DecelerateInterpolator());
        fireAnimator.addUpdateListener(a -> {
            fireProgress = (float) a.getAnimatedValue();
            recoil = (float) Math.sin(fireProgress * Math.PI) * (1f - fireProgress) * 0.13f;
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
        // Soft gold halo, painted only during the short firing animation.
        if (fireProgress > 0f && fireProgress < 0.7f) {
            paint.setShader(new LinearGradient(18, 18, 82, 82,
                    new int[]{0x00FFD76A, 0x44FFC83D, 0x00FFD76A},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(50, 48, 44, paint);
            paint.setShader(null);
        }
        // Compact dark-gold base.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(22, 69, 78, 89,
                new int[]{0xFF6D4A12, 0xFFE8B84A, 0xFF5D3C0D}, null, Shader.TileMode.CLAMP));
        Path base = new Path();
        base.moveTo(18, 70); base.lineTo(80, 70); base.lineTo(72, 84);
        base.quadTo(50, 91, 28, 84); base.close();
        canvas.drawPath(base, paint);
        paint.setShader(null);
        // Barrel with metallic highlight and a short recoil shift.
        canvas.save();
        canvas.translate(-recoil * 100f, 0);
        paint.setShader(new LinearGradient(18, 35, 77, 60,
                new int[]{0xFF5A5A5A, 0xFFD8D8D8, 0xFF6A6A6A, 0xFFB98725},
                new float[]{0f, .38f, .72f, 1f}, Shader.TileMode.CLAMP));
        Path barrel = new Path();
        barrel.moveTo(24, 40); barrel.lineTo(73, 29); barrel.lineTo(82, 35);
        barrel.lineTo(80, 48); barrel.lineTo(29, 56); barrel.close();
        canvas.drawPath(barrel, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f);
        paint.setColor(0xFFFFD56A);
        canvas.drawLine(31, 42, 74, 33, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(72, 30, 85, 47,
                new int[]{0xFFFFE7A1, 0xFFB77A16, 0xFF51300A}, null, Shader.TileMode.CLAMP));
        canvas.drawOval(74, 31, 86, 47, paint);
        paint.setShader(null);
        canvas.restore();
        // Two dark wheels with polished gold rims.
        for (float x : new float[]{36f, 63f}) {
            paint.setColor(0xFF252525); canvas.drawCircle(x, 67, 12, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            paint.setColor(0xFFFFD36B); canvas.drawCircle(x, 67, 9.5f, paint);
            paint.setStrokeWidth(1.4f); paint.setColor(0xFF9B6A18);
            canvas.drawLine(x - 6, 67, x + 6, 67, paint);
            canvas.drawLine(x, 61, x, 73, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFEAC05C); canvas.drawCircle(x, 67, 2.4f, paint);
        }
        // Small golden sparks around the muzzle during firing.
        if (fireProgress > 0f && fireProgress < 0.48f) {
            float fade = 1f - fireProgress / 0.48f;
            paint.setColor(((int) (255 * fade) << 24) | 0x00F6C64D);
            paint.setStrokeWidth(2f);
            for (int i = 0; i < 5; i++) {
                double a = (i * 0.72) - 0.9;
                float sx = 86f + (float) Math.cos(a) * (5f + fireProgress * 18f);
                float sy = 39f + (float) Math.sin(a) * (5f + fireProgress * 18f);
                canvas.drawLine(sx, sy, sx + (float) Math.cos(a) * 4f, sy + (float) Math.sin(a) * 4f, paint);
            }
        }
        // Tiny central gold jewel.
        paint.setColor(0xFFFFE7A1); canvas.drawCircle(50, 53, 2.3f, paint);
        canvas.restore();
    }

    public void release() {
        if (fireAnimator != null) { fireAnimator.cancel(); fireAnimator = null; }
    }
}
