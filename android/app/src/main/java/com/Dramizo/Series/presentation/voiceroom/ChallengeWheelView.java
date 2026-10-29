package com.Dramizo.Series.presentation.voiceroom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/** Premium native JEHO challenge wheel. The server remains authoritative for the selected sector. */
public final class ChallengeWheelView extends View {
    private static final String[] LABELS = {
            "تحدي الألغاز",
            "رسالة من مجهول",
            "فقرة الأسئلة",
            "تحدي الإجابة السريعة بين شخصين",
            "كرسي الاعتراف",
            "احزر الأغنية أو أكمل المثل",
            "المايك المفخخ",
            "صراحة أم جرأة",
            "الحزازير",
            "حقيقتان وكذبة"
    };
    private static final int[][] PALETTES = {
            {0xFF6E43A1, 0xFF38205F},
            {0xFF3C75B8, 0xFF1B355E},
            {0xFF279A9B, 0xFF14545C},
            {0xFFC18442, 0xFF70401F},
            {0xFFB34D78, 0xFF682744},
            {0xFF526FC0, 0xFF293B78},
            {0xFF4F9A67, 0xFF24583C},
            {0xFFC65D4D, 0xFF78352F},
            {0xFF8066C1, 0xFF493777},
            {0xFF3487A1, 0xFF1B4E68}
    };

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final StaticLayout[] labelLayouts = new StaticLayout[LABELS.length];
    private final RectF wheelBounds = new RectF();
    private final RectF rimBounds = new RectF();
    private ValueAnimator spinAnimator;

    public ChallengeWheelView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("عجلة تحديات جيهو");

        rimPaint.setStyle(Paint.Style.FILL);
        rimPaint.setShadowLayer(dp(8), 0, dp(4), 0x99000000);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        detailPaint.setStyle(Paint.Style.FILL);

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setShadowLayer(dp(5), 0, dp(2), 0x99000000);

        hubInnerPaint.setStyle(Paint.Style.FILL);

        logoPaint.setTextAlign(Paint.Align.CENTER);
        logoPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));

        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        labelPaint.setTextSize(dp(9.2f));
        labelPaint.setShadowLayer(dp(2.4f), 0, dp(1.2f), 0xDD10101A);

        int textWidth = Math.max(1, (int) dp(78));
        for (int i = 0; i < LABELS.length; i++) {
            labelLayouts[i] = new StaticLayout(
                    LABELS[i], labelPaint, textWidth, Layout.Alignment.ALIGN_CENTER,
                    1.02f, 0f, false);
        }
    }

    public static String labelAt(int index) {
        return LABELS[Math.max(0, Math.min(LABELS.length - 1, index))];
    }

    public void spinTo(int index, long durationMs, @NonNull Runnable onFinished) {
        int selected = Math.max(0, Math.min(LABELS.length - 1, index));
        if (spinAnimator != null) spinAnimator.cancel();

        float current = getRotation();
        float desired = normalize(-selected * 36f - 18f);
        float remainder = normalize(desired - normalize(current));
        float target = current + 7f * 360f + remainder;

        spinAnimator = ValueAnimator.ofFloat(current, target);
        spinAnimator.setDuration(Math.max(5200L, durationMs));
        spinAnimator.setInterpolator(new DecelerateInterpolator(2.15f));
        spinAnimator.addUpdateListener(a -> setRotation((Float) a.getAnimatedValue()));
        spinAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }
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
        float outer = side * 0.492f;
        float radius = side * 0.452f;

        // Deep outer housing and polished multi-layer metallic rim.
        canvas.drawCircle(cx, cy + dp(2), outer, rimPaint);
        rimBounds.set(cx - outer, cy - outer, cx + outer, cy + outer);
        rimPaint.clearShadowLayer();
        rimPaint.setShader(new LinearGradient(cx, cy - outer, cx, cy + outer,
                new int[]{0xFFFFF0BE, 0xFFB77A2E, 0xFFFFD979, 0xFF80501F},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, outer - dp(1), rimPaint);
        rimPaint.setShader(null);

        float face = radius;
        wheelBounds.set(cx - face, cy - face, cx + face, cy + face);
        fillPaint.setShader(new RadialGradient(cx - face * .28f, cy - face * .34f,
                face * 1.55f, new int[]{0xFF4A405C, 0xFF171321, 0xFF090811},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, face + dp(1), fillPaint);
        fillPaint.setShader(null);

        // Ten equal sectors, each with its own subtle dimensional gradient.
        for (int i = 0; i < LABELS.length; i++) {
            float start = -90f + i * 36f;
            fillPaint.setShader(new LinearGradient(
                    cx - face * .55f, cy - face * .72f,
                    cx + face * .58f, cy + face * .72f,
                    new int[]{PALETTES[i][0], PALETTES[i][1]},
                    null, Shader.TileMode.CLAMP));
            canvas.drawArc(wheelBounds, start, 36f, true, fillPaint);
            fillPaint.setShader(null);

            // Fine separators and a restrained inner highlight keep every slice crisp.
            linePaint.setStrokeWidth(dp(1.45f));
            linePaint.setColor(0xFFFFE7A2);
            double angle = Math.toRadians(start);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(angle) * face,
                    cy + (float) Math.sin(angle) * face, linePaint);

            linePaint.setStrokeWidth(dp(.65f));
            linePaint.setColor(0x66FFFFFF);
            canvas.drawArc(wheelBounds, start + 1.2f, 33.6f, false, linePaint);

            // Radial text orientation gives long Arabic labels room without crowding the hub.
            canvas.save();
            canvas.rotate(start + 18f, cx, cy);
            StaticLayout layout = labelLayouts[i];
            float labelRadius = face * .535f;
            canvas.translate(labelRadius - dp(39), -layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
        }

        // Fine outer track and ten jewel-like index studs.
        linePaint.setColor(0xFFFFE8A6);
        linePaint.setStrokeWidth(dp(1.25f));
        canvas.drawCircle(cx, cy, face - dp(1), linePaint);
        linePaint.setColor(0xFF8A5B29);
        linePaint.setStrokeWidth(dp(2.2f));
        canvas.drawCircle(cx, cy, outer - dp(5), linePaint);
        for (int i = 0; i < 40; i++) {
            double a = Math.toRadians(i * 9f - 90f);
            float r1 = outer - dp(i % 4 == 0 ? 10 : 8);
            float r2 = outer - dp(i % 4 == 0 ? 15 : 11);
            detailPaint.setColor(i % 4 == 0 ? 0xFFFFF1C1 : 0xFF9E6D35);
            canvas.drawCircle(cx + (float) Math.cos(a) * r1,
                    cy + (float) Math.sin(a) * r1,
                    dp(i % 4 == 0 ? 1.8f : 1.05f), detailPaint);
            if (i % 4 == 0) {
                linePaint.setColor(0xFFFFE6A0);
                linePaint.setStrokeWidth(dp(1.1f));
                canvas.drawLine(cx + (float) Math.cos(a) * r1,
                        cy + (float) Math.sin(a) * r1,
                        cx + (float) Math.cos(a) * r2,
                        cy + (float) Math.sin(a) * r2, linePaint);
            }
        }

        // Raised JEHO center medallion.
        float hub = face * .205f;
        hubPaint.setShader(new LinearGradient(cx - hub, cy - hub, cx + hub, cy + hub,
                new int[]{0xFFFFF0B8, 0xFFC18A3C, 0xFFFFD978, 0xFF80501F},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hub + dp(4), hubPaint);
        hubPaint.setShader(null);

        hubInnerPaint.setShader(new RadialGradient(cx - hub * .28f, cy - hub * .38f,
                hub * 1.4f, new int[]{0xFF40334F, 0xFF171323, 0xFF0B0911},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hub, hubInnerPaint);
        hubInnerPaint.setShader(null);

        linePaint.setColor(0xFFFFE6A0);
        linePaint.setStrokeWidth(dp(1.1f));
        canvas.drawCircle(cx, cy, hub - dp(5), linePaint);

        logoPaint.setShader(new LinearGradient(cx, cy - dp(12), cx, cy + dp(12),
                new int[]{0xFFFFF4CF, 0xFFFFD36D, 0xFFCA9141},
                null, Shader.TileMode.CLAMP));
        logoPaint.setTextSize(dp(18));
        canvas.drawText("JEHO", cx, cy + dp(6), logoPaint);
        logoPaint.setShader(null);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float normalize(float angle) {
        float result = angle % 360f;
        return result < 0 ? result + 360f : result;
    }
}
