package com.Dramizo.Series.util;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.Locale;

/**
 * Host signals room-frame overlay from the server, stretched to the list/header item size.
 */
public final class RoomKenarHelper {
    private static final int TAG_SYNC = 0x52C4FD01;

    private RoomKenarHelper() {}

    /** Keep server Host-signals room-frame URLs as-is. */
    @Nullable
    public static String sanitize(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        return url;
    }

    public static boolean isRoomFrameUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("/room-frames/")
                || lower.contains("room-0")
                || lower.contains("room-1")
                || lower.contains("room-2")
                || lower.contains("room-3")
                || lower.contains("room-4");
    }

    @Nullable
    public static String preferAnimated(@Nullable String animationUrl, @Nullable String previewUrl) {
        if (animationUrl != null && !animationUrl.isEmpty()
                && !animationUrl.toLowerCase(Locale.US).contains("runtime.html")) {
            return sanitize(animationUrl);
        }
        return sanitize(previewUrl);
    }

    public static void bind(@Nullable ImageView target, @Nullable String roomCardUrl) {
        bind(target, null, roomCardUrl, roomCardUrl, false, false);
    }

    public static void bind(@Nullable ImageView target, @Nullable View cardBody,
                            @Nullable String roomCardUrl) {
        bind(target, cardBody, roomCardUrl, roomCardUrl, false, false);
    }

    public static void bind(@Nullable ImageView target, @Nullable View cardBody,
                            @Nullable String roomCardUrl, @Nullable String stableKey) {
        bind(target, cardBody, roomCardUrl, stableKey, false, false);
    }

    public static void bind(@Nullable ImageView target, @Nullable View cardBody,
                            @Nullable String roomCardUrl, @Nullable String stableKey,
                            boolean animated) {
        bind(target, cardBody, roomCardUrl, stableKey, animated, false);
    }

    /**
     * @param fillItem true = stretch frame to cardBody pixel size (party list / header chip)
     */
    public static void bind(@Nullable ImageView target, @Nullable View cardBody,
                            @Nullable String roomCardUrl, @Nullable String stableKey,
                            boolean animated, boolean fillItem) {
        if (target == null) return;
        String frameUrl = sanitize(roomCardUrl);
        String absWanted = frameUrl == null || frameUrl.isEmpty()
                ? null : AssetCatalog.absoluteUrl(frameUrl);
        // Skip reload when same frame already showing (stops list flicker).
        Object loaded = target.getTag(R.id.tag_room_kenar_url);
        if (absWanted != null && absWanted.equals(loaded)
                && target.getVisibility() == View.VISIBLE
                && target.getDrawable() != null) {
            if (!animated) RoomCardAnimator.stop(target);
            return;
        }
        clearSync(target);
        if (frameUrl == null || frameUrl.isEmpty()) {
            RoomCardAnimator.stop(target);
            try {
                Glide.with(target).clear(target);
            } catch (Exception ignored) {
            }
            target.setImageDrawable(null);
            target.setVisibility(View.GONE);
            target.setTag(R.id.tag_room_kenar_url, null);
            return;
        }

        target.setVisibility(View.VISIBLE);
        target.setScaleType(ImageView.ScaleType.FIT_XY);
        target.setAdjustViewBounds(false);
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setRotation(0f);
        target.setTranslationX(0f);
        target.setTranslationY(0f);
        if (cardBody != null) {
            cardBody.setBackgroundResource(R.drawable.bg_host_card_under_kenar);
        }

        final String abs = absWanted;
        target.setTag(R.id.tag_room_kenar_url, abs);
        final Runnable apply = () -> {
            if (!abs.equals(target.getTag(R.id.tag_room_kenar_url))) return;
            int w = 1;
            int h = 1;
            if (fillItem && cardBody != null && cardBody.getWidth() > 0 && cardBody.getHeight() > 0) {
                sizeToCard(target, cardBody);
                w = cardBody.getWidth();
                h = cardBody.getHeight();
            } else if (target.getWidth() > 0 && target.getHeight() > 0) {
                w = target.getWidth();
                h = target.getHeight();
            } else if (cardBody != null) {
                w = Math.max(1, cardBody.getWidth());
                h = Math.max(1, cardBody.getHeight());
            }
            String lower = abs.toLowerCase(Locale.US);
            RequestOptions opts = new RequestOptions()
                    .override(Math.max(1, w), Math.max(1, h))
                    .dontTransform()
                    .dontAnimate();
            if (lower.contains(".gif") && !lower.contains(".webp")) {
                Glide.with(target).asGif().load(abs).apply(opts).into(target);
            } else {
                Glide.with(target).load(abs).apply(opts).into(target);
            }
            if (animated) {
                RoomCardAnimator.play(target, stableKey);
            } else {
                RoomCardAnimator.stop(target);
            }
        };

        if (fillItem && cardBody != null) {
            View.OnLayoutChangeListener sync =
                    (v, l, t, r, b, ol, ot, or, ob) -> {
                        if ((r - l) > 0 && (b - t) > 0) apply.run();
                    };
            target.setTag(TAG_SYNC, new SyncState(cardBody, sync));
            cardBody.addOnLayoutChangeListener(sync);
            if (cardBody.getWidth() > 0 && cardBody.getHeight() > 0) {
                apply.run();
            } else {
                cardBody.post(apply);
            }
        } else {
            target.post(apply);
        }
    }

    private static void sizeToCard(ImageView target, View cardBody) {
        int w = cardBody.getWidth();
        int h = cardBody.getHeight();
        if (w <= 0 || h <= 0) return;
        // Exact card size — one frame only (no second border from overhang).
        FrameLayout.LayoutParams flp;
        if (target.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            flp = (FrameLayout.LayoutParams) target.getLayoutParams();
        } else {
            flp = new FrameLayout.LayoutParams(w, h);
        }
        flp.width = w;
        flp.height = h;
        flp.gravity = Gravity.TOP | Gravity.START;
        flp.leftMargin = cardBody.getLeft();
        flp.topMargin = cardBody.getTop();
        flp.rightMargin = 0;
        flp.bottomMargin = 0;
        target.setLayoutParams(flp);
    }

    private static void clearSync(ImageView target) {
        Object tag = target.getTag(TAG_SYNC);
        if (tag instanceof SyncState) {
            SyncState state = (SyncState) tag;
            try {
                state.cardBody.removeOnLayoutChangeListener(state.listener);
            } catch (Exception ignored) {
            }
        }
        target.setTag(TAG_SYNC, null);
        RoomCardAnimator.stop(target);
    }

    private static final class SyncState {
        final View cardBody;
        final View.OnLayoutChangeListener listener;

        SyncState(View cardBody, View.OnLayoutChangeListener listener) {
            this.cardBody = cardBody;
            this.listener = listener;
        }
    }
}
