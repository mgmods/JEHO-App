package com.Dramizo.Series.presentation.voiceroom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/** Flat, clean Fortune Wheel. Long result names are shown below the wheel, not squeezed into slices. */
public final class ChallengeWheelView extends View {
    private static final String[] LABELS = {
            "تحدي الألغاز", "رسالة من مجهول", "فقرة الأسئلة",
            "تحدي الإجابة السريعة بين شخصين", "كرسي الاعتراف",
            "احزر الأغنية أو أكمل المثل", "المايك المفخخ",
            "صراحة أم جرأة", "الحزازير", "حقيقتان وكذبة"
    };
    private static final String[] SHORT_LABELS = {
            "الألغاز", "مجهول", "أسئلة", "سرعة", "اعتراف",
            "أغنية", "مايك", "جرأة", "حزازير", "حقيقتان"
    };
    private static final int[] COLORS = {
            0xFF6E43A1, 0xFF287F9A, 0xFF287B69, 0xFFB77A36, 0xFFB34D78,
            0xFF496CB4, 0xFF4C925C, 0xFFC15B48, 0xFF7860B2, 0xFF277E9A
    };

    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint logoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final StaticLayout[] layouts = new StaticLayout[SHORT_LABELS.length];
    private final RectF bounds = new RectF();
    private final Path wedge = new Path();
    private ValueAnimator spinAnimator;

    public ChallengeWheelView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("عجلة تحديات جيهو");

        sectorPaint.setStyle(Paint.Style.FILL);
        separatorPaint.setStyle(Paint.Style.STROKE);
        separatorPaint.setStrokeWidth(dp(1.35f));
        separatorPaint.setColor(0xDFFFFFFF);
        separatorPaint.setStrokeCap(Paint.Cap.ROUND);

        outerLinePaint.setStyle(Paint.Style.STROKE);
        outerLinePaint.setStrokeWidth(dp(1.4f));
        outerLinePaint.setColor(0x99FFFFFF);

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setShadowLayer(dp(5), 0, dp(2), 0x66000000);
        hubEdgePaint.setStyle(Paint.Style.STROKE);
        hubEdgePaint.setStrokeWidth(dp(2));

        pointerPaint.setStyle(Paint.Style.FILL);
        pointerPaint.setColor(0xFFFFD36C);
        pointerPaint.setShadowLayer(dp(3), 0, dp(1), 0x77000000);

        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        labelPaint.setTextSize(dp(10.5f));
        labelPaint.setShadowLayer(dp(1.5f), 0, dp(1), 0x77000000);
        for (int i = 0; i < SHORT_LABELS.length; i++) {
            layouts[i] = new StaticLayout(SHORT_LABELS[i], labelPaint, (int) dp(64),
                    Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        }

        logoPaint.setColor(0xFFFFE9B5);
        logoPaint.setTextAlign(Paint.Align.CENTER);
        logoPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        logoPaint.setTextSize(dp(14));
    }

    public static String labelAt(int index) {
        return LABELS[Math.max(0, Math.min(LABELS.length - 1, index))];
    }

    public void spinTo(int index, long durationMs, @NonNull Runnable onFinished) {
        int selected = Math.max(0, Math.min(LABELS.length - 1, index));
        if (spinAnimator != null) spinAnimator.cancel();
        float current = getRotation();
        float desired = normalize(-selected * 36f - 18f);
        float target = current + 6f * 360f + normalize(desired - normalize(current));
        spinAnimator = ValueAnimator.ofFloat(current, target);
        spinAnimator.setDuration(Math.max(5200L, durationMs));
        spinAnimator.setInterpolator(new DecelerateInterpolator(2.2f));
        spinAnimator.addUpdateListener(v -> setRotation((Float) v.getAnimatedValue()));
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
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = side * .485f;
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // The wheel is the only graphic surface: no enclosing plate, box, or heavy rim.
        for (int i = 0; i < LABELS.length; i++) {
            float start = -90f + i * 36f;
            sectorPaint.setShader(new LinearGradient(cx - radius * .45f, cy - radius,
                    cx + radius * .55f, cy + radius,
                    new int[]{lighten(COLORS[i]), COLORS[i], darken(COLORS[i])},
                    new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawArc(bounds, start, 36f, true, sectorPaint);
            sectorPaint.setShader(null);

            double angle = Math.toRadians(start);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius, separatorPaint);

            // Each short label is centered in its own wedge and clipped to the wheel disc.
            canvas.save();
            wedge.reset();
            wedge.moveTo(cx, cy);
            wedge.arcTo(bounds, start, 36f);
            wedge.close();
            canvas.clipPath(wedge);
            canvas.rotate(start + 18f, cx, cy);
            StaticLayout text = layouts[i];
            float textRadius = radius * .655f;
            canvas.translate(cx + textRadius - dp(32), cy - text.getHeight() / 2f);
            text.draw(canvas);
            canvas.restore();
        }

        canvas.drawCircle(cx, cy, radius - dp(1), outerLinePaint);

        // Small central button, integrated into the wheel face.
        float hub = radius * .205f;
        hubPaint.setShader(new LinearGradient(cx - hub, cy - hub, cx + hub, cy + hub,
                new int[]{0xFF39264F, 0xFF171222, 0xFF342342},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hub, hubPaint);
        hubPaint.setShader(null);
        hubEdgePaint.setColor(0xFFFFD477);
        canvas.drawCircle(cx, cy, hub - dp(2), hubEdgePaint);
        canvas.drawText("JEHO", cx, cy + dp(5), logoPaint);

        // Compact fixed pointer at 12 o'clock.
        Path pointer = new Path();
        pointer.moveTo(cx, cy - radius + dp(2));
        pointer.lineTo(cx - dp(9), cy - radius - dp(13));
        pointer.lineTo(cx + dp(9), cy - radius - dp(13));
        pointer.close();
        canvas.drawPath(pointer, pointerPaint);
    }

    private int lighten(int color) {
        return blend(color, 0xFFFFFFFF, .16f);
    }

    private int darken(int color) {
        return blend(color, 0xFF000000, .22f);
    }

    private int blend(int a, int b, float amount) {
        int alpha = (int) (((a >>> 24) & 255) * (1f - amount) + ((b >>> 24) & 255) * amount);
        int red = (int) (((a >> 16) & 255) * (1f - amount) + ((b >> 16) & 255) * amount);
        int green = (int) (((a >> 8) & 255) * (1f - amount) + ((b >> 8) & 255) * amount);
        int blue = (int) ((a & 255) * (1f - amount) + (b & 255) * amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float normalize(float angle) {
        float result = angle % 360f;
        return result < 0 ? result + 360f : result;
    }
}
