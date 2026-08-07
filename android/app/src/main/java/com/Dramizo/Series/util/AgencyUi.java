package com.Dramizo.Series.util;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Mikoo user-card guild strip: {@code icon_info_guild_bg_base_lv1..6}
 * colours / shield numeral change by agency (guild) display level.
 */
public final class AgencyUi {
    private AgencyUi() {}

    /** Visual tiers with distinct Mikoo banner art (Roman I–VI shield). */
    public static final int MAX_BANNER_TIER = 6;

    @DrawableRes
    private static final int[] BANNER = {
            0,
            R.drawable.icon_info_guild_bg_base_lv1,
            R.drawable.icon_info_guild_bg_base_lv2,
            R.drawable.icon_info_guild_bg_base_lv3,
            R.drawable.icon_info_guild_bg_base_lv4,
            R.drawable.icon_info_guild_bg_base_lv5,
            R.drawable.icon_info_guild_bg_base_lv6,
    };

    /**
     * Map lifetime agency diamonds (gifts → agency pool) onto visual tier 1–6.
     * Soft ladder similar to room/economy progression — not mall catalogue levels.
     */
    public static int bannerTierFromDiamonds(long totalDiamonds) {
        long d = Math.max(0L, totalDiamonds);
        if (d >= 5_000_000L) return 6;
        if (d >= 1_000_000L) return 5;
        if (d >= 200_000L) return 4;
        if (d >= 50_000L) return 3;
        if (d >= 10_000L) return 2;
        return 1;
    }

    public static int clampTier(int level) {
        if (level <= 0) return 1;
        return Math.min(MAX_BANNER_TIER, level);
    }

    @DrawableRes
    public static int bannerRes(int levelOrTier) {
        int t = clampTier(levelOrTier);
        return BANNER[t];
    }

    public static void bindBanner(
            @Nullable ImageView bannerView,
            @Nullable TextView levelView,
            int levelOrTier) {
        int tier = clampTier(levelOrTier);
        if (bannerView != null) {
            bannerView.setImageResource(bannerRes(tier));
        }
        if (levelView != null) {
            levelView.setVisibility(View.VISIBLE);
            levelView.setText("Lv." + tier);
            levelView.bringToFront();
        }
    }

    /** Load agency profile face (logo) — never owner avatar. */
    public static void bindLogo(
            @Nullable ImageView logoView,
            @Nullable String logoUrl,
            @Nullable String coverFallback) {
        if (logoView == null) return;
        // Same circular clip path as user avatars so room header / family sheet match.
        AvatarImageLoader.applyCircularClip(logoView);
        String url = firstUrl(logoUrl, coverFallback);
        if (url == null) {
            logoView.setTag(R.id.tag_image_url, null);
            logoView.setImageResource(R.drawable.icon_agency);
            return;
        }
        String abs = AssetCatalog.absoluteUrl(url);
        if (abs == null || abs.isEmpty()) {
            logoView.setImageResource(R.drawable.icon_agency);
            return;
        }
        // Bust media when admin replaces files under same path.
        abs = MediaAssetSync.bust(abs);
        Object prev = logoView.getTag(R.id.tag_image_url);
        if (prev instanceof String && abs.equals(prev) && logoView.getDrawable() != null) {
            return;
        }
        logoView.setTag(R.id.tag_image_url, abs);
        try {
            Glide.with(logoView)
                    .load(abs)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(logoView);
        } catch (Exception ignored) {
            logoView.setImageResource(R.drawable.icon_agency);
        }
    }

    @Nullable
    private static String firstUrl(@Nullable String a, @Nullable String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    /**
     * Mikoo-style card body: base dark sheet, VIP users get a soft top→bottom
     * gradient tint from {@link VipStyle} palettes (gradual colours by VIP).
     */
    public static void applyUserCardSheet(
            @Nullable View sheetBg,
            @Nullable View moreBlock,
            int vipLevel) {
        if (sheetBg == null && moreBlock == null) return;
        float density = sheetBg != null
                ? sheetBg.getResources().getDisplayMetrics().density
                : (moreBlock != null
                ? moreBlock.getResources().getDisplayMetrics().density
                : 2f);
        float topR = 12f * density;
        if (vipLevel <= 0) {
            if (sheetBg != null) sheetBg.setBackgroundResource(R.drawable.bg_user_card_sheet);
            if (moreBlock != null) moreBlock.setBackgroundResource(R.drawable.bg_user_card_sheet);
            return;
        }
        VipStyle.Palette p = VipStyle.forLevel(vipLevel);
        // Deep card #121616 with VIP gradient wash (not full solid pastel).
        int top = blend(0xFF121616, p.start | 0xFF000000, 0.55f);
        int mid = blend(0xFF121616, p.end | 0xFF000000, 0.35f);
        int bottom = 0xFF121616;
        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, mid, bottom});
        body.setCornerRadii(new float[]{
                topR, topR, topR, topR, 0, 0, 0, 0
        });
        if (sheetBg != null) sheetBg.setBackground(body);
        if (moreBlock != null) {
            GradientDrawable more = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mid, bottom});
            moreBlock.setBackground(more);
        }
    }

    private static int blend(int base, int tint, float tintStrength) {
        float s = Math.max(0f, Math.min(1f, tintStrength));
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        int tr = (tint >> 16) & 0xFF;
        int tg = (tint >> 8) & 0xFF;
        int tb = tint & 0xFF;
        int r = Math.round(br * (1f - s) + tr * s);
        int g = Math.round(bg * (1f - s) + tg * s);
        int b = Math.round(bb * (1f - s) + tb * s);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
