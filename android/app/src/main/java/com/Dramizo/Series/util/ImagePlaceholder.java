package com.Dramizo.Series.util;

import com.Dramizo.Series.R;

/** Local bundled placeholders while remote images load. */
public final class ImagePlaceholder {
    private ImagePlaceholder() {}

    /** App brand mark (launcher logo) — splash, official news, system notices. */
    public static int brandLogo() {
        return R.drawable.jeho_logo;
    }

    /** Default user avatar — JEHO CHAT branded watermark. */
    public static int avatar() {
        return R.drawable.ic_default_avatar;
    }

    /** Room cover fallback — light placeholder. */
    public static int cover() {
        return R.color.ios_grouped_bg;
    }

    /** Generic games cover while remote artwork loads. */
    public static int game() {
        return R.drawable.ic_game_placeholder;
    }

    /** @deprecated Use {@link #avatar()} or {@link #cover()}. */
    public static int light() {
        return avatar();
    }

    /** Gift icon placeholder — used anywhere a gift image is expected. */
    public static int gift() {
        return R.drawable.ic_screen_chat_lottery;
    }
}
