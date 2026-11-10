package com.Dramizo.Series.presentation.stories;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.bumptech.glide.Glide;

/** Opens the exact story referenced by a story-reply card in a conversation. */
public final class StoryPreviewActivity extends AppCompatActivity {
    public static final String EXTRA_STORY_ID = "jeho_story_id";
    public static final String EXTRA_MEDIA_TYPE = "jeho_story_media_type";
    public static final String EXTRA_MEDIA_URL = "jeho_story_media_url";
    public static final String EXTRA_CAPTION = "jeho_story_caption";
    public static final String EXTRA_OWNER = "jeho_story_owner";

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        configureSystemBars();

        String storyId = getIntent().getStringExtra(EXTRA_STORY_ID);
        String mediaType = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        String mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA_URL);
        String caption = getIntent().getStringExtra(EXTRA_CAPTION);
        String owner = getIntent().getStringExtra(EXTRA_OWNER);
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            Toast.makeText(this, "محتوى الستوري لم يعد متاحاً", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 7, 10));

        View media;
        if ("video".equalsIgnoreCase(mediaType)) {
            VideoView video = new VideoView(this);
            video.setKeepScreenOn(true);
            video.setBackgroundColor(Color.BLACK);
            video.setOnPreparedListener(player -> {
                player.setLooping(false);
                video.start();
            });
            video.setOnErrorListener((player, what, extra) -> {
                Toast.makeText(this, "تعذر تشغيل فيديو الستوري", Toast.LENGTH_SHORT).show();
                return true;
            });
            video.setVideoURI(Uri.parse(absoluteUrl(mediaUrl)));
            media = video;
        } else {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(this).load(absoluteUrl(mediaUrl)).into(image);
            media = image;
        }
        root.addView(media, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView header = new TextView(this);
        header.setText(owner == null || owner.trim().isEmpty() ? "الستوري" : owner);
        header.setTextColor(Color.WHITE);
        header.setTextSize(16);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackground(background(0xAA17131D, dp(18)));
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(48), Gravity.TOP | Gravity.START);
        headerLp.setMargins(dp(14), dp(12), dp(70), 0);
        root.addView(header, headerLp);

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(Color.WHITE);
        close.setTextSize(30);
        close.setGravity(Gravity.CENTER);
        close.setBackground(background(0xAA17131D, dp(28)));
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        closeLp.setMargins(0, dp(12), dp(12), 0);
        root.addView(close, closeLp);
        close.setOnClickListener(v -> finish());

        if (caption != null && !caption.trim().isEmpty()) {
            TextView footer = new TextView(this);
            footer.setText(caption.trim());
            footer.setTextColor(Color.WHITE);
            footer.setTextSize(14);
            footer.setPadding(dp(16), dp(14), dp(16), dp(14));
            footer.setMaxLines(4);
            footer.setBackground(background(0xCC17131D, dp(18)));
            FrameLayout.LayoutParams footerLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            footerLp.setMargins(dp(14), 0, dp(14), dp(14));
            root.addView(footer, footerLp);
        }

        setContentView(root);
        if (storyId != null && !storyId.trim().isEmpty()) {
            try {
                AppContainer container = ContainerProvider.from(this);
                container.getIoExecutor().execute(() -> ApiCall.execute(container.getStoriesApi().view(storyId)));
            } catch (Exception ignored) {
                // The preview remains readable if view tracking is temporarily unavailable.
            }
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.rgb(8, 7, 10));
        window.setNavigationBarColor(Color.rgb(8, 7, 10));
        WindowCompat.setDecorFitsSystemWindows(window, true);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars()
                | androidx.core.view.WindowInsetsCompat.Type.navigationBars());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private String absoluteUrl(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String url = value.trim();
        return url.startsWith("/") ? com.Dramizo.Series.util.ApiOrigin.origin() + url : url;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
