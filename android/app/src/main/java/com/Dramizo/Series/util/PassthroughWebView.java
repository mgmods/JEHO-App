package com.Dramizo.Series.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * Visual-only WebView. It can cover the room for full-screen effects but can
 * never become a touch target, so controls underneath remain interactive.
 */
public final class PassthroughWebView extends WebView {
    public PassthroughWebView(Context context) {
        super(context);
    }

    public PassthroughWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PassthroughWebView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }
}
