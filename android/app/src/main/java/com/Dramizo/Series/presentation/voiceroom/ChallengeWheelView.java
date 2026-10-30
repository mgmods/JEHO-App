package com.Dramizo.Series.presentation.voiceroom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/** Native Fortune-style challenge wheel with a fixed frame, pointer and complete Arabic labels. */
public final class ChallengeWheelView extends View {
    private static final String[] LABELS = {
            "تحدي الألغاز", "رسالة من مجهول", "فقرة الأسئلة",
            "تحدي الإجابة السريعة بين شخصين", "كرسي الاعتراف",
            "احزر الأغنية أو أكمل المثل", "المايك المفخخ",
            "صراحة أم جرأة", "فقرة الحزازير", "حقيقتان وكذبة"
    };
    private static final int[] COLORS = {
            0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFD95B, 0xFF72CDB4, 0xFFF38181,
            0xFFAA96DA, 0xFFEF91B9, 0xFF8ED9B4, 0xFFFFB98D, 0xFFFF928D
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hubText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final StaticLayout[] labels = new StaticLayout[LABELS.length];
    private final RectF wheelBounds = new RectF();
    private final Path wedge = new Path();
    private ValueAnimator spinAnimator;
    private float wheelRotation;

    public ChallengeWheelView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("عجلة التحديات");
        paint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.2f));
        linePaint.setColor(0xEFFFFFFF);
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        labelPaint.setTextSize(dp(8.2f));
        labelPaint.setShadowLayer(dp(1.8f), 0, dp(1), 0xCC000000);
        for (int i = 0; i < LABELS.length; i++) {
            labels[i] = new StaticLayout(LABELS[i], labelPaint, (int) dp(58),
                    Layout.Alignment.ALIGN_CENTER, 0.94f, 0f, false);
        }
        hubText.setColor(0xFFFFE8AD);
        hubText.setTextAlign(Paint.Align.CENTER);
        hubText.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        hubText.setTextSize(dp(11));
        textBackground.setStyle(Paint.Style.FILL);
    }

    public static String labelAt(int index) {
        return LABELS[Math.max(0, Math.min(LABELS.length - 1, index))];
    }

    public void spinTo(int index, long durationMs, @NonNull Runnable onFinished) {
        int selected = Math.max(0, Math.min(LABELS.length - 1, index));
        if (spinAnimator != null) spinAnimator.cancel();
        float desired = normalize(-selected * 36f - 18f);
        float target = wheelRotation + 6f * 360f + normalize(desired - normalize(wheelRotation));
        spinAnimator = ValueAnimator.ofFloat(wheelRotation, target);
        spinAnimator.setDuration(Math.max(6200L, durationMs));
        spinAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        spinAnimator.addUpdateListener(a -> {
            wheelRotation = (Float) a.getAnimatedValue();
            invalidate();
        });
        spinAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!cancelled) onFinished.run();
            }
        });
        spinAnimator.start();
    }

    public void stop() {
        if (spinAnimator != null) {
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0f) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float frameRadius = side * .485f;
        float wheelRadius = side * .425f;

        // Stationary metallic gold frame and illuminated bulbs.
        paint.setShader(new SweepGradient(cx, cy,
                new int[]{0xFFFFF1A8, 0xFFB77A18, 0xFFFFE36B, 0xFF9B6415, 0xFFFFF1A8}, null));
        canvas.drawCircle(cx, cy, frameRadius, paint);
        paint.setShader(null);
        paint.setColor(0xFF26172C);
        canvas.drawCircle(cx, cy, frameRadius - dp(5), paint);
        paint.setColor(0xFFFFD45F);
        canvas.drawCircle(cx, cy, frameRadius - dp(8), paint);
        paint.setColor(0xFF24172E);
        canvas.drawCircle(cx, cy, frameRadius - dp(11), paint);

        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2d * i / 24d - Math.PI / 2d;
            float x = cx + (float) Math.cos(a) * (frameRadius - dp(4));
            float y = cy + (float) Math.sin(a) * (frameRadius - dp(4));
            paint.setShader(new LinearGradient(x - dp(3), y - dp(3), x + dp(3), y + dp(3),
                    new int[]{0xFFFFFFFF, 0xFFFFE77B, 0xFFFF8A24}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, dp(3.1f), paint);
            paint.setShader(null);
        }

        wheelBounds.set(cx - wheelRadius, cy - wheelRadius, cx + wheelRadius, cy + wheelRadius);
        canvas.save();
        canvas.rotate(wheelRotation, cx, cy);
        for (int i = 0; i < LABELS.length; i++) {
            float start = -90f + i * 36f;
            paint.setShader(new LinearGradient(cx - wheelRadius * .55f, cy - wheelRadius,
                    cx + wheelRadius * .55f, cy + wheelRadius,
                    new int[]{lighten(COLORS[i]), COLORS[i], darken(COLORS[i])},
                    new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawArc(wheelBounds, start, 36f, true, paint);
            paint.setShader(null);

            wedge.reset();
            wedge.moveTo(cx, cy);
            wedge.arcTo(wheelBounds, start, 36f);
            wedge.close();
            canvas.save();
            canvas.clipPath(wedge);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(Math.toRadians(start)) * wheelRadius,
                    cy + (float) Math.sin(Math.toRadians(start)) * wheelRadius, linePaint);
            canvas.rotate(start + 18f, cx, cy);
            StaticLayout text = labels[i];
            float textRadius = wheelRadius * .56f;
            canvas.translate(cx + textRadius - dp(29), cy - text.getHeight() / 2f);
            text.draw(canvas);
            canvas.restore();
        }
        canvas.drawCircle(cx, cy, wheelRadius - dp(1), linePaint);
        canvas.restore();

        // Central gold hub.
        float hub = wheelRadius * .19f;
        paint.setShader(new LinearGradient(cx - hub, cy - hub, cx + hub, cy + hub,
                new int[]{0xFFFFF2B8, 0xFFD79A32, 0xFF8E5315}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hub, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        canvas.drawCircle(cx, cy, hub - dp(2), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText("SPIN", cx, cy + dp(4), hubText);

        // Fixed red pointer at twelve o'clock; it never rotates with the wheel.
        Path pointer = new Path();
        pointer.moveTo(cx - dp(12), cy - frameRadius + dp(1));
        pointer.lineTo(cx + dp(12), cy - frameRadius + dp(1));
        pointer.lineTo(cx, cy - frameRadius + dp(25));
        pointer.close();
        paint.setColor(0xFFFF1744);
        paint.setShadowLayer(dp(4), 0, dp(2), 0x99000000);
        canvas.drawPath(pointer, paint);
        paint.clearShadowLayer();
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy - frameRadius + dp(2), dp(3), paint);
    }

    private int lighten(int color) { return blend(color, 0xFFFFFFFF, .17f); }
    private int darken(int color) { return blend(color, 0xFF000000, .20f); }
    private int blend(int a, int b, float t) {
        int aa = (int)(((a >>> 24) & 255) * (1f-t) + ((b >>> 24) & 255) * t);
        int r = (int)(((a >> 16) & 255) * (1f-t) + ((b >> 16) & 255) * t);
        int g = (int)(((a >> 8) & 255) * (1f-t) + ((b >> 8) & 255) * t);
        int bl = (int)((a & 255) * (1f-t) + (b & 255) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private static float normalize(float angle) {
        float value = angle % 360f;
        return value < 0 ? value + 360f : value;
    }
}
