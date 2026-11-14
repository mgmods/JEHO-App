package com.Dramizo.Series.widget;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** Mikoo CircleImageView — circular clipped avatar. */
public class CircleImageView extends AppCompatImageView {
    public CircleImageView(Context context) {
        super(context);
        init();
    }

    public CircleImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = Math.max(1, view.getWidth());
                int h = Math.max(1, view.getHeight());
                int size = Math.min(w, h);
                int left = (w - size) / 2;
                int top = (h - size) / 2;
                outline.setOval(left, top, left + size, top + size);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }
}
