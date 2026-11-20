package com.Dramizo.Series.presentation.profile;

import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.AvatarImageLoader;

/** Secure full-screen viewer for profile avatars and room/profile covers. */
public class SecureProfileImageActivity extends ThemedActivity {
    public static final String EXTRA_IMAGE_URL = "secure_profile_image_url";
    public static final String EXTRA_IMAGE_IS_COVER = "secure_profile_image_is_cover";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        String url = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (url == null || url.trim().isEmpty()) {
            finish();
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int margin = Math.round(16f * metrics.density);
        int availableWidth = Math.max(1, metrics.widthPixels - margin * 2);
        int availableHeight = Math.max(1, metrics.heightPixels - Math.round(112f * metrics.density));
        boolean cover = getIntent().getBooleanExtra(EXTRA_IMAGE_IS_COVER, false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageParams;
        if (cover) {
            int coverHeight = Math.min(availableHeight, Math.round(availableWidth * 9f / 16f));
            imageParams = new FrameLayout.LayoutParams(availableWidth, coverHeight, Gravity.CENTER);
        } else {
            int squareSize = Math.min(availableWidth, availableHeight);
            imageParams = new FrameLayout.LayoutParams(squareSize, squareSize, Gravity.CENTER);
        }
        image.setBackgroundColor(Color.BLACK);
        root.addView(image, imageParams);
        root.setOnClickListener(v -> finish());
        image.setOnClickListener(v -> finish());
        setContentView(root);
        AvatarImageLoader.loadMedia(image, url.trim(), 0);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}