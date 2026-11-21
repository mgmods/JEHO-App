package com.Dramizo.Series.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Animated radial equalizer driven by the live speaking level; the avatar itself never scales. */
public final class SeatVoiceWaveBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean speaking;
    private float level = 48f;
    private float phase;
    private int barCount = 40;
    private final float density;

    public SeatVoiceWaveBarsView(Context context) { this(context, null); }

    public SeatVoiceWaveBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFD76A);
        paint.setStrokeWidth(Math.max(1.5f, 2.0f * density));
        setWillNotDraw(false);
    }

    public void setBarCount(int count) {
        barCount = Math.max(20, Math.min(48, count));
        invalidate();
    }

    public void setSpeaking(boolean active, float audioLevel) {
        speaking = active;
        level = Math.max(0f, Math.min(100f, audioLevel));
        if (active) {
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            postInvalidateOnAnimation();
        } else {
            setVisibility(GONE);
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!speaking || getWidth() <= 0 || getHeight() <= 0) return;
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.405f;
        float intensity = 0.18f + (level / 100f) * 0.82f;
        for (int i = 0; i < barCount; i++) {
            double angle = (Math.PI * 2d * i / barCount) + phase;
            float envelope = (float) (0.5d + 0.5d * Math.sin(i * 0.69d + phase * 3.0d));
            float detail = (float) (0.5d + 0.5d * Math.sin(i * 0.27d - phase * 2.2d));
            float length = (2.5f + (envelope * 6.5f + detail * 4.5f) * intensity) * density;
            float x1 = cx + (float) Math.cos(angle) * radius;
            float y1 = cy + (float) Math.sin(angle) * radius;
            float x2 = cx + (float) Math.cos(angle) * (radius + length);
            float y2 = cy + (float) Math.sin(angle) * (radius + length);
            paint.setAlpha(Math.min(255, 145 + Math.round(110f * intensity)));
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        phase += 0.07f + (level / 100f) * 0.13f;
        postInvalidateDelayed(34L);
    }
}
