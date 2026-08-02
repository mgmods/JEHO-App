package com.Dramizo.Series.util;

import android.app.Activity;
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
import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;

/**
 * Mikoo-style global celebration toast (lucky hit / game win).
 * Shows on whatever Activity is in the foreground — home, chats, any room.
 */
public final class GlobalCelebrationToast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> current = new WeakReference<>(null);
    private static Runnable hideRunnable;
    private static String lastDedupeKey;
    private static long lastShownAt;

    private GlobalCelebrationToast() {}

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey
    ) {
        show(activity, title, body, avatarUrl, badgeUrl, dedupeKey, 28);
    }

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            int topMarginDp
    ) {
        if (activity == null || activity.isFinishing()) return;
        long now = System.currentTimeMillis();
        if (dedupeKey != null && dedupeKey.equals(lastDedupeKey) && now - lastShownAt < 8_000L) {
            return;
        }
        lastDedupeKey = dedupeKey;
        lastShownAt = now;
        final int margin = Math.max(8, topMarginDp);
        MAIN.post(() -> showInternal(activity, title, body, avatarUrl, badgeUrl, margin));
    }

    private static void showInternal(
            Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            int topMarginDp
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;

        View existing = current.get();
        if (existing != null && existing.getParent() instanceof ViewGroup) {
            ((ViewGroup) existing.getParent()).removeView(existing);
        }
        if (hideRunnable != null) MAIN.removeCallbacks(hideRunnable);

        View banner = LayoutInflater.from(activity)
                .inflate(R.layout.layout_celebration_toast, root, false);
        ImageView avatar = banner.findViewById(R.id.imgCelebrationAvatar);
        ImageView badge = banner.findViewById(R.id.imgCelebrationBadge);
        TextView tvTitle = banner.findViewById(R.id.tvCelebrationTitle);
        TextView tvBody = banner.findViewById(R.id.tvCelebrationBody);

        if (tvTitle != null) {
            tvTitle.setText(title != null && !title.isEmpty() ? title : "مبروك!");
        }
        if (tvBody != null) {
            String line = body != null ? body : "";
            tvBody.setText(line);
            tvBody.setSelected(true); // enable marquee
        }

        String absAvatar = AssetCatalog.absoluteUrl(avatarUrl);
        if (avatar != null) {
            if (absAvatar != null && !absAvatar.isEmpty()) {
                try {
                    Glide.with(activity.getApplicationContext())
                            .load(absAvatar)
                            .circleCrop()
                            .into(avatar);
                } catch (Exception ignored) {
                    avatar.setImageResource(R.drawable.jeho_logo);
                }
            } else {
                avatar.setImageResource(R.drawable.jeho_logo);
            }
        }

        String absBadge = AssetCatalog.absoluteUrl(badgeUrl);
        if (badge != null) {
            if (absBadge != null && !absBadge.isEmpty()) {
                badge.setVisibility(View.VISIBLE);
                try {
                    Glide.with(activity.getApplicationContext())
                            .load(absBadge)
                            .centerCrop()
                            .into(badge);
                } catch (Exception ignored) {
                    badge.setVisibility(View.GONE);
                }
            } else {
                badge.setVisibility(View.GONE);
            }
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = dp(activity, topMarginDp);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setTranslationX(dp(activity, 80));
        root.addView(banner);
        current = new WeakReference<>(banner);

        // Slide in fast from the right, hold, then slow crawl off to the left (news-style).
        banner.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .start();
        hideRunnable = () -> removeBanner(banner);
        MAIN.postDelayed(hideRunnable, 4800L);
    }

    private static void removeBanner(View banner) {
        if (banner == null) return;
        float out = -Math.max(banner.getWidth(), dp(banner, 280));
        banner.animate()
                .alpha(0.85f)
                .translationX(out)
                .setDuration(1600)
                .withEndAction(() -> {
                    if (banner.getParent() instanceof ViewGroup parent) {
                        parent.removeView(banner);
                    }
                })
                .start();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
