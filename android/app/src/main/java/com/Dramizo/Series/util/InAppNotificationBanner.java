package com.Dramizo.Series.util;

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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Lightweight, serialized in-app notification queue for foreground events. */
public final class InAppNotificationBanner {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static final Map<String, Long> RECENT_KEYS = new HashMap<>();
    private static WeakReference<View> currentBanner = new WeakReference<>(null);
    private static Runnable hideRunnable;
    private static boolean showing;
    private static long durationMs = 4200L;

    private static final class Request {
        final WeakReference<Activity> activity;
        final String title;
        final String body;
        final String type;
        final Intent tapIntent;
        final String dedupeKey;
        final String avatarUrl;

        Request(Activity activity, String title, String body, String type,
                Intent tapIntent, String dedupeKey, String avatarUrl) {
            this.activity = new WeakReference<>(activity);
            this.title = title;
            this.body = body;
            this.type = type;
            this.tapIntent = tapIntent;
            this.dedupeKey = dedupeKey;
            this.avatarUrl = avatarUrl;
        }
    }

    private InAppNotificationBanner() {}

    public static void setDurationMs(long value) {
        durationMs = Math.max(1500L, Math.min(12000L, value));
    }

    public static void show(Activity activity, String title, String body, String type,
                            @Nullable Intent tapIntent) {
        show(activity, title, body, type, tapIntent, null, null);
    }

    public static void show(Activity activity, String title, String body, String type,
                            @Nullable Intent tapIntent, @Nullable String dedupeKey) {
        show(activity, title, body, type, tapIntent, dedupeKey, null);
    }

    public static void show(Activity activity, String title, String body, String type,
                            @Nullable Intent tapIntent, @Nullable String dedupeKey,
                            @Nullable String avatarUrl) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Request request = new Request(activity, title, body, type, tapIntent, dedupeKey, avatarUrl);
        MAIN.post(() -> {
            if (dedupeKey != null) {
                long now = System.currentTimeMillis();
                Long last = RECENT_KEYS.get(dedupeKey);
                if (last != null && now - last < 10_000L) return;
                RECENT_KEYS.put(dedupeKey, now);
                if (RECENT_KEYS.size() > 200) {
                    String oldestKey = null;
                    long oldestAt = Long.MAX_VALUE;
                    for (Map.Entry<String, Long> entry : RECENT_KEYS.entrySet()) {
                        if (entry.getValue() < oldestAt) {
                            oldestAt = entry.getValue();
                            oldestKey = entry.getKey();
                        }
                    }
                    if (oldestKey != null) RECENT_KEYS.remove(oldestKey);
                }
            }
            QUEUE.addLast(request);
            showNext();
        });
    }

    private static void showNext() {
        if (showing) return;
        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.removeFirst();
            Activity activity = request.activity.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                releaseKey(request);
                continue;
            }
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (!(root instanceof FrameLayout)) {
                releaseKey(request);
                continue;
            }
            showInternal(activity, root, request);
            return;
        }
    }

    private static void showInternal(Activity activity, ViewGroup root, Request request) {
        View banner = LayoutInflater.from(activity).inflate(
                R.layout.in_app_notification_banner, root, false);
        ImageView icon = banner.findViewById(R.id.imgBannerIcon);
        TextView tvTitle = banner.findViewById(R.id.tvBannerTitle);
        TextView tvBody = banner.findViewById(R.id.tvBannerBody);
        tvTitle.setText(request.title != null && !request.title.isEmpty()
                ? request.title : activity.getString(R.string.app_name));
        tvBody.setText(request.body != null ? request.body : "");

        String url = AssetCatalog.absoluteUrl(request.avatarUrl);
        if (url != null && !url.isEmpty()) {
            icon.setPadding(0, 0, 0, 0);
            icon.setBackgroundResource(R.drawable.bg_notification_avatar);
            AvatarImageLoader.load(icon, url);
        } else {
            icon.setImageResource(iconForType(request.type));
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = dp(activity, 12);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setTranslationY(-dp(activity, 24));
        root.addView(banner);
        currentBanner = new WeakReference<>(banner);
        showing = true;

        banner.animate().alpha(1f).translationY(0f).setDuration(220).start();
        if (request.tapIntent != null) {
            banner.setOnClickListener(v -> {
                Intent intent = new Intent(request.tapIntent);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try {
                    activity.startActivity(intent);
                } catch (Exception ignored) {
                }
                dismiss(banner, request);
            });
        }
        hideRunnable = () -> dismiss(banner, request);
        MAIN.postDelayed(hideRunnable, durationMs);
    }

    private static void dismiss(View banner, Request request) {
        if (hideRunnable != null) MAIN.removeCallbacks(hideRunnable);
        if (banner == null) {
            finishRequest(request);
            return;
        }
        banner.animate().alpha(0f).translationY(-dp(banner, 16)).setDuration(180)
                .withEndAction(() -> {
                    if (banner.getParent() instanceof ViewGroup) {
                        ((ViewGroup) banner.getParent()).removeView(banner);
                    }
                    finishRequest(request);
                }).start();
    }

    private static void finishRequest(Request request) {
        currentBanner = new WeakReference<>(null);
        hideRunnable = null;
        showing = false;
        releaseKey(request);
        MAIN.post(InAppNotificationBanner::showNext);
    }

    private static void releaseKey(Request request) {
        // Recent keys are retained for a short dedupe window after display,
        // preventing duplicate FCM/socket deliveries from reappearing.
    }

    private static int iconForType(String type) {
        if (type == null) return ImagePlaceholder.brandLogo();
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
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

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density);
    }
}
