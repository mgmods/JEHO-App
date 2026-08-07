package com.Dramizo.Series.util;

import android.widget.TextView;

import com.Dramizo.Series.R;

/**
 * Server URL helper + emergency placeholders only.
 * Cosmetics/gifts/frames/room cards/join effects must come from API
 * (previewUrl / animationUrl / iconUrl / roomCardUrl) served by the dashboard.
 * Do not add new local themed catalogs here.
 */
public final class AssetCatalog {
    private AssetCatalog() {}

    /** Neutral placeholder when a remote URL is missing — user avatar icon. */
    public static int placeholderDrawable() {
        return R.drawable.ic_tab_me;
    }

    public static int giftDrawable(String name) {
        return R.drawable.ic_screen_chat_lottery;
    }

    public static String giftLottie(String nameOrUrl) {
        if (nameOrUrl != null && nameOrUrl.endsWith(".json") && !nameOrUrl.startsWith("http")) {
            return nameOrUrl.startsWith("lottie/") ? nameOrUrl : ("lottie/" + nameOrUrl);
        }
        return null;
    }

    public static int vipMedal(int level) {
        switch (Math.min(7, Math.max(1, level))) {
            case 1: return R.drawable.vip_medal_mikoo_1;
            case 2: return R.drawable.vip_medal_mikoo_2;
            case 3: return R.drawable.vip_medal_mikoo_3;
            case 4: return R.drawable.vip_medal_mikoo_4;
            case 5: return R.drawable.vip_medal_mikoo_5;
            case 6: return R.drawable.vip_medal_mikoo_6;
            default: return R.drawable.vip_medal_mikoo_7;
        }
    }

    public static int profileVipBadge(int level) {
        return vipMedal(level);
    }

    /** Canonical remote Mikoo medal URL (VIP1–7). */
    public static String vipMedalUrl(int level) {
        int n = Math.min(7, Math.max(1, level));
        return MediaAssetSync.bust(
                absoluteUrl("/assets/cosmetics/vip/vip_medal_mikoo_" + n + ".png"));
    }

    /** Join-toast VIP avatar corner (Mikoo noble head 1–7 sequential). */
    public static int vipJoinKuang(int vipLevel) {
        switch (Math.min(7, Math.max(1, vipLevel))) {
            case 1: return R.drawable.bg_enter_noble_head_1;
            case 2: return R.drawable.bg_enter_noble_head_2;
            case 3: return R.drawable.bg_enter_noble_head_3;
            case 4: return R.drawable.bg_enter_noble_head_4;
            case 5: return R.drawable.bg_enter_noble_head_5;
            case 6: return R.drawable.bg_enter_noble_head_6;
            default: return R.drawable.bg_enter_noble_head_7;
        }
    }

    public static void applyRoomRoleBadge(TextView view, boolean agencyRoom) {
        if (view == null) return;
        view.setCompoundDrawablesRelative(null, null, null, null);
    }

    public static void applyLevelBadge(TextView view, int level) {
        if (view == null) return;
        view.setCompoundDrawablesRelative(null, null, null, null);
    }

    /**
     * Local coin-stack art for recharge packages when the server has no iconUrl.
     * Tier follows the coins amount returned by {@code GET wallet/packages}.
     */
    public static int rechargeBagForCoins(long coins) {
        if (coins <= 100) return R.drawable.cz_ic1;
        if (coins <= 500) return R.drawable.cz_ic2;
        if (coins <= 1200) return R.drawable.cz_ic3;
        if (coins <= 3000) return R.drawable.cz_ic4;
        return R.drawable.cz_ic5;
    }

    /** Mikoo-style pack icon by grid index (cz_ic1…cz_ic5). */
    public static int rechargeBagForIndex(int index) {
        switch (Math.floorMod(index, 5)) {
            case 0: return R.drawable.cz_ic1;
            case 1: return R.drawable.cz_ic2;
            case 2: return R.drawable.cz_ic3;
            case 3: return R.drawable.cz_ic4;
            default: return R.drawable.cz_ic5;
        }
    }

    /** Coin-stack art for limited-time store offers. */
    public static int offerArtForCoins(long coins) {
        if (coins <= 30000) return R.drawable.cz_ic3;
        if (coins <= 80000) return R.drawable.cz_ic4;
        if (coins <= 160000) return R.drawable.cz_ic5;
        return R.drawable.cz_ic5;
    }

    public static int crownForRank(int rank) {
        return placeholderDrawable();
    }

    public static int frameDrawable(String codeOrUrl) {
        return placeholderDrawable();
    }

    public static int roomCardDrawable(int index) {
        return placeholderDrawable();
    }

    public static int joinToastDrawable(int index) {
        int[] bgs = {
                R.drawable.ic_enter_room_anima_bg10,
                R.drawable.ic_enter_room_anima_bg20,
                R.drawable.ic_enter_room_anima_bg30,
                R.drawable.ic_enter_room_anima_bg40,
                R.drawable.ic_enter_room_anima_bg50,
                R.drawable.ic_enter_room_anima_bg60,
                R.drawable.ic_enter_room_anima_bg70,
                R.drawable.ic_enter_room_anima_bg80,
                R.drawable.ic_enter_room_anima_bg90,
                R.drawable.ic_enter_room_anima_bg100
        };
        if (bgs.length == 0) return placeholderDrawable();
        int i = Math.floorMod(index, bgs.length);
        return bgs[i];
    }

    public static int joinToastForVip(int vipLevel, String name) {
        // Mikoo VIP1–7 map onto enter banner assets in order (not scrambled).
        switch (Math.min(7, Math.max(1, vipLevel))) {
            case 1: return R.drawable.ic_enter_room_anima_bg10;
            case 2: return R.drawable.ic_enter_room_anima_bg20;
            case 3: return R.drawable.ic_enter_room_anima_bg30;
            case 4: return R.drawable.ic_enter_room_anima_bg40;
            case 5: return R.drawable.ic_enter_room_anima_bg50;
            case 6: return R.drawable.ic_enter_room_anima_bg60;
            default: return R.drawable.ic_enter_room_anima_bg70;
        }
    }

    /** Prefix relative dashboard/API asset paths with the production host. */
    public static String absoluteUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) return null;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        String origin = ApiOrigin.origin();
        if (pathOrUrl.startsWith("/")) return origin + pathOrUrl;
        return origin + "/" + pathOrUrl;
    }



    public static final String[] EMOJI_CHARS = {
            "smile", "fire", "love", "clap", "laugh", "cool"
    };
}
