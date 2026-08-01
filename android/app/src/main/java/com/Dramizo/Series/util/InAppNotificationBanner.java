package com.Dramizo.Series.util;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

import java.lang.ref.WeakReference;

/** Lightweight in-app notification banner for foreground FCM messages. */
public final class InAppNotificationBanner {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> currentBanner = new WeakReference<>(null);
    private static Runnable hideRunnable;
    private static String lastDedupeKey;
    private static long lastShownAt;

    private InAppNotificationBanner() {}

    public static void show(
            Activity activity,
            String title,
            String body,
            String type,
            @Nullable Intent tapIntent
    ) {
        show(activity, title, body, type, tapIntent, null, null);
    }

    public static void show(
            Activity activity,
            String title,
            String body,
            String type,
            @Nullable Intent tapIntent,
            @Nullable String dedupeKey
    ) {
        show(activity, title, body, type, tapIntent, dedupeKey, null);
    }

    public static void show(
            Activity activity,
            String title,
            String body,
            String type,
            @Nullable Intent tapIntent,
            @Nullable String dedupeKey,
            @Nullable String avatarUrl
    ) {
        if (activity == null || activity.isFinishing()) return;
        long now = System.currentTimeMillis();
        if (dedupeKey != null && dedupeKey.equals(lastDedupeKey) && now - lastShownAt < 10_000L) {
            return;
        }
        lastDedupeKey = dedupeKey;
        lastShownAt = now;
        MAIN.post(() -> showInternal(activity, title, body, type, tapIntent, avatarUrl));
    }

    private static void showInternal(
            Activity activity,
            String title,
            String body,
            String type,
            @Nullable Intent tapIntent,
            @Nullable String avatarUrl
    ) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;

        View existing = currentBanner.get();
        if (existing != null && existing.getParent() instanceof ViewGroup) {
            ((ViewGroup) existing.getParent()).removeView(existing);
        }
        if (hideRunnable != null) MAIN.removeCallbacks(hideRunnable);

        View banner = LayoutInflater.from(activity).inflate(R.layout.in_app_notification_banner, root, false);
        ImageView icon = banner.findViewById(R.id.imgBannerIcon);
        TextView tvTitle = banner.findViewById(R.id.tvBannerTitle);
        TextView tvBody = banner.findViewById(R.id.tvBannerBody);
        tvTitle.setText(title != null ? title : activity.getString(R.string.app_name));
        tvBody.setText(body != null ? body : "");

        String url = AssetCatalog.absoluteUrl(avatarUrl);
        if (url != null && !url.isEmpty()) {
            icon.setPadding(0, 0, 0, 0);
            icon.setBackgroundResource(R.drawable.bg_notification_avatar);
            AvatarImageLoader.load(icon, url);
        } else {
            icon.setImageResource(iconForType(type));
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = dp(activity, 12);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setTranslationY(-dp(activity, 24));
        root.addView(banner);
        currentBanner = new WeakReference<>(banner);

        banner.animate().alpha(1f).translationY(0f).setDuration(220).start();
        if (tapIntent != null) {
            banner.setOnClickListener(v -> {
                tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(tapIntent);
                removeBanner(banner);
            });
        }
        hideRunnable = () -> removeBanner(banner);
        MAIN.postDelayed(hideRunnable, 4200L);
    }

    private static void removeBanner(View banner) {
        if (banner == null) return;
        banner.animate().alpha(0f).translationY(-dp(banner, 16)).setDuration(180)
                .withEndAction(() -> {
                    if (banner.getParent() instanceof ViewGroup) {
                        ((ViewGroup) banner.getParent()).removeView(banner);
                    }
                }).start();
    }

    private static int iconForType(String type) {
        if (type == null) return ImagePlaceholder.brandLogo();
        switch (type.toLowerCase()) {
            case "gift":
                return ImagePlaceholder.gift();
            case "chat":
            case "message":
            case "follow":
            case "friend":
            case "relation":
                return ImagePlaceholder.brandLogo();
            default:
                return ImagePlaceholder.brandLogo();
        }
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}
