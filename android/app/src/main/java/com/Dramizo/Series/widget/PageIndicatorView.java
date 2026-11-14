package com.Dramizo.Series.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

import java.util.ArrayList;
import java.util.List;

/** Mikoo PageIndicatorView — oval page dots under gift grid. */
public class PageIndicatorView extends LinearLayout {
    private final List<View> dots = new ArrayList<>();
    private int dotSizePx;
    private int gapPx;

    public PageIndicatorView(Context context) {
        this(context, null);
    }

    public PageIndicatorView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.CENTER);
        setOrientation(HORIZONTAL);
        float d = context.getResources().getDisplayMetrics().density;
        dotSizePx = Math.round(5f * d);
        gapPx = Math.round(3f * d);
    }

    public void initIndicator(int count) {
        removeAllViews();
        dots.clear();
        if (count <= 1) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        for (int i = 0; i < count; i++) {
            View dot = new View(getContext());
            LayoutParams lp = new LayoutParams(dotSizePx, dotSizePx);
            lp.setMargins(gapPx, 0, gapPx, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.shape_indicator_present_invisible);
            addView(dot);
            dots.add(dot);
        }
        setSelectedPage(0);
    }

    public void setSelectedPage(int page) {
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setBackgroundResource(i == page
                    ? R.drawable.shape_indicator_present_visible
                    : R.drawable.shape_indicator_present_invisible);
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
