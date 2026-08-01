package com.Dramizo.Series.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Mikoo XProgressBar — thin rounded progress track. */
public class XProgressBar extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();
    private float progress; // 0..1
    private int max = 100;

    public XProgressBar(Context context) {
        super(context);
        init();
    }

    public XProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public XProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setColor(0x33FFFFFF);
        trackPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0xFF3AEBAB);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public void setProgress(int value) {
        progress = Math.max(0f, Math.min(1f, value / (float) max));
        invalidate();
    }

    public void setProgressFraction(float fraction) {
        progress = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public int getProgress() {
        return Math.round(progress * max);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float radius = h / 2f;
        trackRect.set(0, 0, getWidth(), h);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        float w = getWidth() * progress;
        if (w > 0f) {
            fillRect.set(0, 0, Math.max(w, radius * 2f), h);
            if (fillRect.right > getWidth()) fillRect.right = getWidth();
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint);
        }
    }
}
