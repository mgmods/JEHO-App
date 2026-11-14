package com.Dramizo.Series.presentation.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Horizontal ViewPager2 tabs vs vertical list / AppBar nested scroll.
 */
public class NestedScrollableHost extends FrameLayout {
    private float touchSlop;
    private float initialX;
    private float initialY;
    private boolean decided;

    public NestedScrollableHost(@NonNull Context context) {
        super(context);
        init(context);
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private ViewPager2 findViewPager() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ViewPager2) return (ViewPager2) child;
        }
        return null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleTouch(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        handleTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void handleTouch(MotionEvent e) {
        ViewPager2 vp = findViewPager();
        if (vp == null || !vp.isUserInputEnabled()) return;

        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
            decided = false;
            return;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            decided = false;
            return;
        }
        if (action != MotionEvent.ACTION_MOVE || decided) return;

        float dx = Math.abs(e.getX() - initialX);
        float dy = Math.abs(e.getY() - initialY);
        if (dx <= touchSlop && dy <= touchSlop) return;

        decided = true;
        if (dx > dy) {
            // Horizontal tab swipe — block parents (SRL / AppBar)
            requestDisallowParents(true);
        } else {
            // Vertical — let nested scroll collapse AppBar + recycle list
            requestDisallowParents(false);
        }
    }

    private void requestDisallowParents(boolean disallow) {
        ViewParent p = getParent();
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
            p = p.getParent();
        }
    }
}
