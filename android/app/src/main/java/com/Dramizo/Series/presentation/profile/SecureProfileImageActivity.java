package com.Dramizo.Series.presentation.profile;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.AvatarImageLoader;

/**
 * Full-screen private avatar preview. FLAG_SECURE is intentionally scoped to this
 * Activity so Android screenshots remain available on the rest of the profile page.
 */
public class SecureProfileImageActivity extends ThemedActivity {
    public static final String EXTRA_IMAGE_URL = "secure_profile_image_url";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        root.addView(image, params);
        root.setOnClickListener(v -> finish());
        image.setOnClickListener(v -> finish());
        setContentView(root);

        String url = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (url == null || url.trim().isEmpty()) {
            finish();
            return;
        }
        AvatarImageLoader.load(image, url.trim());
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
