package com.Dramizo.Series.util;

import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;

/**
 * Professional TikTok-style join / entry banner binder.
 * Adapts name & chip text contrast to the toast/effect background luminance.
 */
public final class JoinToastBinder {
    public static final long DISPLAY_MS = 2800L;

    public static final class Views {
        public View container;
        public ImageView banner;
        public View scrim;
        public LottieAnimationView lottie;
        public ImageView avatar;
        public ImageView frame;
        public TextView action;
        public TextView name;
        public ImageView vipBadge;
        public ImageView levelBadge;
        public ImageView hostBadge;
        public TextView chipHost;
        public TextView chipVip;
        public TextView chipLevel;
        public TextView chipWealth;
        public TextView chipRoomSpend;

        @NonNull
        public static Views from(@NonNull View container) {
            Views v = new Views();
            v.container = container;
            v.banner = container.findViewById(com.Dramizo.Series.R.id.imgJoinBanner);
            v.scrim = container.findViewById(com.Dramizo.Series.R.id.joinToastScrim);
            v.lottie = container.findViewById(com.Dramizo.Series.R.id.lottieJoinEffect);
            v.avatar = container.findViewById(com.Dramizo.Series.R.id.imgJoinAvatar);
            v.frame = container.findViewById(com.Dramizo.Series.R.id.imgJoinFrame);
            v.action = container.findViewById(com.Dramizo.Series.R.id.tvJoinAction);
            v.name = container.findViewById(com.Dramizo.Series.R.id.tvJoinUser);
            v.vipBadge = container.findViewById(com.Dramizo.Series.R.id.imgJoinVipBadge);
            v.levelBadge = container.findViewById(com.Dramizo.Series.R.id.imgJoinLevelBadge);
            v.hostBadge = container.findViewById(com.Dramizo.Series.R.id.imgJoinHostBadge);
            v.chipHost = container.findViewById(com.Dramizo.Series.R.id.chipJoinHost);
            v.chipVip = container.findViewById(com.Dramizo.Series.R.id.chipJoinVip);
            v.chipLevel = container.findViewById(com.Dramizo.Series.R.id.chipJoinLevel);
            v.chipWealth = container.findViewById(com.Dramizo.Series.R.id.chipJoinWealth);
            v.chipRoomSpend = container.findViewById(com.Dramizo.Series.R.id.chipJoinRoomSpend);
            return v;
        }
    }

    public static final class Data {
        public String displayName;
        public String avatarUrl;
        public String frameUrl;
        public String entryEffectUrl;
        public String entryAnimationUrl;
        public String toastUrl;
        public String toastAnimationUrl;
        public int vipLevel;
        public int userLevel = 1;
        public String vipBadgeUrl;
        public String levelBadgeUrl;
        public String hostBadgeUrl;
        public boolean isHost;
        public String supporterTier = "normal";
        public long wealthScore;
        public long roomSpendCoins;
        public String renderMode;
        public float aspectRatio;
        public float safeLeft;
        public float safeTop;
        public float safeRight;
        public float safeBottom;
        public long durationMs;
        public String actionLabel;
    }

    private JoinToastBinder() {}

    public static boolean hasVisual(@Nullable Data d) {
        if (d == null) return false;
        return firstNonEmpty(d.entryEffectUrl, d.toastUrl, d.entryAnimationUrl, d.toastAnimationUrl) != null;
    }

    public static void bind(@NonNull Views v, @NonNull Data d, @NonNull Handler handler) {
        if (v.container == null) return;
        clearVisuals(v);
        String bannerUrl = firstNonEmpty(d.entryEffectUrl, d.toastUrl);
        String lottieUrl = firstNonEmpty(d.entryAnimationUrl, d.toastAnimationUrl);
        if (bannerUrl == null && lottieUrl == null) return;
        String mode = d.renderMode == null ? "" : d.renderMode.toLowerCase(java.util.Locale.US);
        boolean staticOnly = mode.contains("static") || mode.contains("image");
        boolean animationOnly = mode.contains("lottie") || mode.contains("animation");
        if (staticOnly) lottieUrl = null;
        if (animationOnly && lottieUrl != null) bannerUrl = null;

        v.container.setVisibility(View.VISIBLE);
        v.container.bringToFront();
        v.container.setElevation(24f);
        v.container.setTranslationY(56f);
        v.container.setAlpha(0f);

        if (v.scrim != null) v.scrim.setVisibility(View.VISIBLE);
        applyTextSafeArea(v, d);

        if (bannerUrl != null && v.banner != null) {
            v.banner.setVisibility(View.VISIBLE);
            v.banner.setScaleType(mode.contains("inside") || mode.contains("contain")
                    || (d.aspectRatio > 0f && d.aspectRatio > 3.5f)
                    ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.FIT_CENTER);
            Glide.with(v.banner.getContext().getApplicationContext())
                    .load(AssetCatalog.absoluteUrl(bannerUrl))
                    .placeholder(new ColorDrawable(android.graphics.Color.TRANSPARENT))
                    .error(new ColorDrawable(android.graphics.Color.TRANSPARENT))
                    .fitCenter()
                    .into(v.banner);
        } else if (v.banner != null) {
            v.banner.setVisibility(View.GONE);
        }

        if (lottieUrl != null && v.lottie != null) {
            v.lottie.setVisibility(View.VISIBLE);
            try {
                final LottieAnimationView target = v.lottie;
                target.setFailureListener(error -> {
                    target.cancelAnimation();
                    target.setVisibility(View.GONE);
                });
                v.lottie.setAnimationFromUrl(AssetCatalog.absoluteUrl(lottieUrl));
                v.lottie.playAnimation();
            } catch (Exception ignored) {
                v.lottie.setVisibility(View.GONE);
            }
        } else if (v.lottie != null) {
            try { v.lottie.cancelAnimation(); } catch (Exception ignored) {}
            v.lottie.setVisibility(View.GONE);
        }

        String name = d.displayName != null && !d.displayName.isEmpty() ? d.displayName : "مستخدم";
        if (v.name != null) v.name.setText(name);
        if (v.action != null) {
            String action = d.actionLabel != null && !d.actionLabel.isEmpty()
                    ? d.actionLabel
                    : actionForTier(d.supporterTier, d.vipLevel);
            v.action.setText(action);
        }

        if (v.avatar != null) AvatarCosmetics.bindAvatar(v.avatar, d.avatarUrl);
        if (v.frame != null) AvatarCosmetics.applyFrame(v.frame, d.frameUrl);

        bindBadgeImage(v.vipBadge, d.vipBadgeUrl);
        bindBadgeImage(v.levelBadge, d.levelBadgeUrl);
        bindBadgeImage(v.hostBadge, d.isHost ? d.hostBadgeUrl : null);

        int vip = Math.max(0, d.vipLevel);
        int lv = Math.max(1, d.userLevel);
        if (v.chipHost != null) {
            boolean fallback = d.isHost && isEmpty(d.hostBadgeUrl);
            v.chipHost.setVisibility(fallback ? View.VISIBLE : View.GONE);
            if (fallback) {
                VipStyle.applyChip(v.chipHost, Math.max(vip, 3));
                v.chipHost.setTextColor(VipStyle.chipTextColor(Math.max(vip, 3)));
            }
        }
        if (v.chipVip != null) {
            if (vip > 0 && isEmpty(d.vipBadgeUrl)) {
                v.chipVip.setVisibility(View.VISIBLE);
                v.chipVip.setText("VIP" + vip);
                VipStyle.applyChip(v.chipVip, vip);
                v.chipVip.setTextColor(VipStyle.chipTextColor(vip));
            } else {
                v.chipVip.setVisibility(View.GONE);
            }
        }
        if (v.chipLevel != null) {
            if (lv > 1 && isEmpty(d.levelBadgeUrl)) {
                v.chipLevel.setVisibility(View.VISIBLE);
                v.chipLevel.setText("Lv." + lv);
                VipStyle.applyChip(v.chipLevel, vip);
                v.chipLevel.setTextColor(VipStyle.chipTextColor(vip));
            } else {
                v.chipLevel.setVisibility(View.GONE);
            }
        }
        if (v.chipWealth != null) {
            long wealth = Math.max(0L, d.wealthScore);
            if (wealth > 0) {
                v.chipWealth.setVisibility(View.VISIBLE);
                v.chipWealth.setText("ثروة " + formatScore(wealth));
                VipStyle.applyChip(v.chipWealth, vip);
                v.chipWealth.setTextColor(VipStyle.chipTextColor(vip));
            } else {
                v.chipWealth.setVisibility(View.GONE);
            }
        }
        if (v.chipRoomSpend != null) {
            long roomSpend = Math.max(0L, d.roomSpendCoins);
            if (roomSpend > 0) {
                v.chipRoomSpend.setVisibility(View.VISIBLE);
                v.chipRoomSpend.setText("الغرفة " + formatScore(roomSpend));
                VipStyle.applyChip(v.chipRoomSpend, Math.max(vip, 2));
                v.chipRoomSpend.setTextColor(VipStyle.chipTextColor(Math.max(vip, 2)));
            } else {
                v.chipRoomSpend.setVisibility(View.GONE);
            }
        }

        v.container.animate().cancel();
        v.container.animate().translationY(0f).alpha(1f).setDuration(240).start();

        final int token = v.container.hashCode() ^ (int) System.nanoTime();
        v.container.setTag(token);
        handler.postDelayed(() -> {
            if (v.container == null) return;
            Object tag = v.container.getTag();
            if (!(tag instanceof Integer) || (Integer) tag != token) return;
            if (v.lottie != null) {
                try { v.lottie.cancelAnimation(); } catch (Exception ignored) {}
                v.lottie.setVisibility(View.GONE);
            }
            v.container.animate().translationY(48f).alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        if (v.container != null) v.container.setVisibility(View.GONE);
                    }).start();
        }, d.durationMs > 0 ? Math.max(1200L, Math.min(12000L, d.durationMs)) : DISPLAY_MS);
    }

    private static void bindBadgeImage(@Nullable ImageView img, @Nullable String url) {
        if (img == null) return;
        if (url == null || url.isEmpty()) {
            Glide.with(img.getContext().getApplicationContext()).clear(img);
            img.setImageDrawable(null);
            img.setVisibility(View.GONE);
            return;
        }
        img.setVisibility(View.VISIBLE);
        Glide.with(img.getContext().getApplicationContext())
                .load(AssetCatalog.absoluteUrl(url))
                .placeholder(new ColorDrawable(android.graphics.Color.TRANSPARENT))
                .error(new ColorDrawable(android.graphics.Color.TRANSPARENT))
                .fitCenter()
                .into(img);
    }

    private static void clearVisuals(@NonNull Views v) {
        if (v.banner != null) {
            Glide.with(v.banner.getContext().getApplicationContext()).clear(v.banner);
            v.banner.setImageDrawable(null);
            v.banner.setVisibility(View.GONE);
        }
        if (v.lottie != null) {
            try { v.lottie.cancelAnimation(); } catch (Exception ignored) {}
            v.lottie.clearAnimation();
            v.lottie.setImageDrawable(null);
            v.lottie.setVisibility(View.GONE);
        }
    }

    private static void applyTextSafeArea(@NonNull Views v, @NonNull Data d) {
        if (v.scrim == null) return;
        v.scrim.post(() -> {
            int width = v.container.getWidth();
            int height = v.container.getHeight();
            v.scrim.setPadding(safeInset(d.safeLeft, width), safeInset(d.safeTop, height),
                    safeInset(d.safeRight, width), safeInset(d.safeBottom, height));
        });
    }

    private static int safeInset(float value, int size) {
        if (value <= 0f || size <= 0) return 0;
        float normalized = value > 1f && value <= 100f ? value / 100f : value;
        return normalized <= 1f ? Math.round(size * Math.min(0.45f, normalized))
                : Math.round(Math.min(size * 0.45f, normalized));
    }

    private static boolean isEmpty(@Nullable String value) {
        return value == null || value.isEmpty();
    }

    private static String actionForTier(@Nullable String tier, int vip) {
        if ("legendary".equals(tier)) return "دخول أسطوري";
        if ("supporter".equals(tier)) return "انضم كداعم";
        if (vip >= 1) return "انضم · VIP" + vip;
        return "انضم للغرفة";
    }

    private static String formatScore(long n) {
        if (n >= 1_000_000) return String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(java.util.Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }
}
