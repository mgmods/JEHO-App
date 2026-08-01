package com.Dramizo.Series.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;

/**
 * Shared Mikoo {@code svga_room_loading.svga} binder — room join, overlays, list footers.
 */
public final class MikooLoadingAnim {
    public static final String ASSET = "svga/svga_room_loading.svga";

    private MikooLoadingAnim() {}

    public static void bind(@Nullable SVGAImageView svga) {
        if (svga == null) return;
        Context ctx = svga.getContext();
        if (ctx == null) return;
        svga.setClearsAfterStop(false);
        svga.setLoops(-1);
        svga.setVisibility(View.VISIBLE);
        Object tag = svga.getTag();
        if (ASSET.equals(tag) && svga.getDrawable() != null) {
            if (!svga.isAnimating()) svga.startAnimation();
            return;
        }
        try {
            SVGAParser parser = new SVGAParser(ctx);
            parser.decodeFromAssets(ASSET, new SVGAParser.ParseCompletion() {
                @Override
                public void onComplete(@NonNull SVGAVideoEntity videoItem) {
                    if (svga.getWindowToken() == null) return;
                    svga.setTag(ASSET);
                    svga.setVideoItem(videoItem);
                    svga.startAnimation();
                }

                @Override
                public void onError() {
                    // Optional if asset missing.
                }
            }, null);
        } catch (Exception ignored) {
        }
    }

    public static void stop(@Nullable SVGAImageView svga) {
        if (svga == null) return;
        try {
            svga.stopAnimation(true);
        } catch (Exception ignored) {
        }
    }
}
