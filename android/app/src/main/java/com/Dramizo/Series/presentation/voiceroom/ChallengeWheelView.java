package com.Dramizo.Series.presentation.voiceroom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/** Native RTL-friendly ten-sector room challenge wheel. */
public final class ChallengeWheelView extends View {
    private static final String[] LABELS = {
            "فقرة تحدي الالغاز",
            "فقرة رسالة من مجهول",
            "فقرة الاسئلة",
            "فقرة تحدي الاجابة السريعة بين شخصين",
            "فقرة كرسي الإعتراف",
            "فقرة احزر الاغنية او اكمل المثل التالي",
            "فقرة المايك المفخخ",
            "فقرة صراحة ام جراءة",
            "فقرة الحزازير",
            "فقرة حقيقتان وكذبة"
    };
    private static final int[] COLORS = {
            0xFF4B286F, 0xFF263F70, 0xFF176B70, 0xFF80512B, 0xFF7C3158,
            0xFF344B82, 0xFF39704C, 0xFF82433A, 0xFF55418A, 0xFF23627B
    };

    private final Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private ValueAnimator spinAnimator;

    public ChallengeWheelView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2.2f));
        borderPaint.setColor(0xFFFFD77A);
        centerPaint.setColor(0xFFFFD77A);
        centerInnerPaint.setColor(0xFF21172F);
        logoPaint.setColor(0xFFFFE6AE);
        logoPaint.setTextAlign(Paint.Align.CENTER);
        logoPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        labelPaint.setTextSize(dp(10.5f));
        labelPaint.setShadowLayer(dp(2), 0, dp(1), 0xCC000000);
        setContentDescription("عجلة تحديات جيهو");
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
        float target = current + 6f * 360f + remainder;
        spinAnimator = ValueAnimator.ofFloat(current, target);
        spinAnimator.setDuration(Math.max(4800L, durationMs));
        spinAnimator.setInterpolator(new DecelerateInterpolator(2.4f));
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
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = side * 0.485f;
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        for (int i = 0; i < LABELS.length; i++) {
            float start = -90f + i * 36f;
            sectorPaint.setColor(COLORS[i]);
            sectorPaint.setStyle(Paint.Style.FILL);
            canvas.drawArc(bounds, start, 36f, true, sectorPaint);

            sectorPaint.setStyle(Paint.Style.STROKE);
            sectorPaint.setStrokeWidth(dp(1.15f));
            sectorPaint.setColor(0xFFFFD77A);
            canvas.drawArc(bounds, start, 36f, true, sectorPaint);

            canvas.save();
            canvas.rotate(start + 18f, cx, cy);
            float labelRadius = radius * 0.47f;
            int textWidth = Math.max(1, (int) dp(78));
            StaticLayout layout = new StaticLayout(
                    LABELS[i], labelPaint, textWidth, Layout.Alignment.ALIGN_CENTER,
                    0.98f, 0f, false);
            canvas.translate(cx + labelRadius, cy - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
        }

        canvas.drawCircle(cx, cy, radius, borderPaint);
        float hub = radius * 0.235f;
        canvas.drawCircle(cx, cy, hub + dp(3), centerPaint);
        canvas.drawCircle(cx, cy, hub, centerInnerPaint);
        logoPaint.setTextSize(dp(18));
        canvas.drawText("JEHO", cx, cy + dp(6), logoPaint);
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(dp(1.2f));
        canvas.drawCircle(cx, cy, hub - dp(5), centerPaint);
        centerPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float normalize(float angle) {
        float result = angle % 360f;
        return result < 0 ? result + 360f : result;
    }
}
