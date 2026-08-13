package com.Dramizo.Series.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.bumptech.glide.Glide;
import com.Dramizo.Series.R;
import com.tencent.qgame.animplayer.AnimView;
import com.tencent.qgame.animplayer.util.ScaleType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transparent, touch-pass-through native renderer for room entries and gifts.
 * It intentionally owns only one effect tree at a time to keep room memory bounded.
 */
@OptIn(markerClass = UnstableApi.class)
public final class NativeRoomEffectsView extends FrameLayout {
    private static final String[] ENTRY_VARIANTS = {
            "normal", "vip", "gold", "diamond", "legend", "supporter"
    };
    private static final int[] ENTRY_ACCENTS = {
            0xff5ec8ff, 0xffffd56a, 0xffffb21d, 0xffb7e0ff, 0xffff6a3a, 0xffd39bff
    };
    private static final Map<String, GiftSpec> GIFTS = buildGiftCatalog();
    /** Horizontal sprite strips (9 frames) — same as Host signals / gifts.js. */
    private static final Map<String, SpriteSpec> SPRITES = buildSpriteCatalog();
    private static final LruCache<String, Bitmap> BITMAPS =
            new LruCache<String, Bitmap>(12 * 1024) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };

    private final Runnable scheduledFinish = this::finishActive;
    private final List<Animator> looseAnimators = new ArrayList<>();
    @Nullable private AnimatorSet activeAnimators;
    @Nullable private Runnable activeCompletion;
    @Nullable private ExoPlayer giftPlayer;
    @Nullable private AnimView entryAnimView;
    @Nullable private AnimView giftAnimView;
    @Nullable private com.opensource.svgaplayer.SVGAImageView giftSvgaView;
    @Nullable private SpriteSheetView activeSprite;
    private int generation;

    public NativeRoomEffectsView(Context context) {
        this(context, null);
    }

    public NativeRoomEffectsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NativeRoomEffectsView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayoutDirection(TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()));
        setVisibility(GONE);
    }

    public boolean showGift(@Nullable String giftId, @Nullable String remoteIconUrl,
                            @Nullable String senderName, int quantity,
                            long durationMs, @Nullable Runnable onComplete) {
        return showGift(giftId, null, remoteIconUrl, null, senderName, quantity, durationMs, onComplete);
    }

    public boolean showGift(@Nullable String giftId, @Nullable String remoteIconUrl,
                            @Nullable String animationUrl, @Nullable String senderName, int quantity,
                            long durationMs, @Nullable Runnable onComplete) {
        return showGift(giftId, null, remoteIconUrl, animationUrl, senderName, quantity, durationMs, onComplete);
    }

    public boolean showGift(@Nullable String giftId, @Nullable String displayName,
                            @Nullable String remoteIconUrl, @Nullable String animationUrl,
                            @Nullable String senderName, int quantity,
                            long durationMs, @Nullable Runnable onComplete) {
        GiftSpec spec = giftId == null ? null : GIFTS.get(giftId.toLowerCase(Locale.US));
        if (spec == null && giftId != null && !giftId.isEmpty()) {
            String title = firstNonEmpty(displayName, humanizeGiftId(giftId));
            spec = new GiftSpec(giftId.toLowerCase(Locale.US), title, "run", "premium");
        }
        if (spec == null) return false;
        if (displayName != null && !displayName.trim().isEmpty()
                && (GIFTS.get(spec.id) == null || !displayName.trim().equals(spec.name))) {
            // Prefer the server/catalog gift title over a local fallback or raw id.
            String title = displayName.trim();
            if (!looksLikeRawMediaId(title)) {
                spec = new GiftSpec(spec.id, title, spec.family, spec.tier);
            }
        }
        interruptActive(false);
        int token = ++generation;
        activeCompletion = onComplete;
        setAlpha(1f);
        setVisibility(VISIBLE);
        final GiftSpec finalSpec = spec;
        post(() -> {
            if (token != generation) return;
            renderGift(finalSpec, remoteIconUrl, animationUrl, senderName, Math.max(1, quantity));
            try {
                // Gift SFX disabled.
            } catch (Exception ignored) {
            }
            String playable = CosmeticMedia.playableUrl(animationUrl);
            // Video gifts: ExoPlayer owns the timeline (STATE_ENDED). Fixed 8s cut
            // caused black shutter + mid-screen toast after premature finish.
            if (playable != null
                    && CosmeticMedia.kind(playable) == CosmeticMedia.Kind.VIDEO) {
                scheduleFinish(token, 120_000L); // safety only
                return;
            }
            long hold = durationMs > 0 ? durationMs : (quantity > 1 ? 5200 : 4800);
            if (isCrossingFamily(finalSpec.family)) {
                hold = Math.max(hold, 4500L);
            }
            scheduleFinish(token, hold);
        });
        return true;
    }

    private static boolean looksLikeRawMediaId(@Nullable String value) {
        if (value == null || value.isEmpty()) return true;
        String s = value.toLowerCase(Locale.US);
        if (s.contains("cache") || s.contains("mikoo_gift")) return true;
        if (s.matches(".*\\d{10,}.*")) return true;
        return s.length() > 48 && !s.contains(" ");
    }

    private static String humanizeGiftId(@Nullable String giftId) {
        if (giftId == null || giftId.isEmpty() || "custom".equalsIgnoreCase(giftId)) {
            return "هدية";
        }
        if (looksLikeRawMediaId(giftId)) return "هدية";
        return giftId.replace('-', ' ');
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String variant, @Nullable String remoteFrameUrl,
                             long durationMs,
                             @Nullable Runnable onComplete) {
        return showEntry(displayName, avatarUrl, variant, remoteFrameUrl, null, durationMs,
                0, 1, 0L, null, null, null, false, false, onComplete);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String variant, @Nullable String remoteFrameUrl,
                             long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost,
                             @Nullable Runnable onComplete) {
        return showEntry(displayName, avatarUrl, variant, remoteFrameUrl, null, durationMs,
                vipLevel, userLevel, wealthScore, vipBadgeUrl, levelBadgeUrl,
                hostBadgeUrl, isHost, false, onComplete);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String variant, @Nullable String remoteFrameUrl,
                             long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost, boolean showHiBadge,
                             @Nullable Runnable onComplete) {
        return showEntry(displayName, avatarUrl, variant, remoteFrameUrl, null, durationMs,
                vipLevel, userLevel, wealthScore, vipBadgeUrl, levelBadgeUrl,
                hostBadgeUrl, isHost, showHiBadge, onComplete);
    }

    public boolean showEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String variant, @Nullable String remoteFrameUrl,
                             @Nullable String entryMediaUrl,
                             long durationMs,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost, boolean showHiBadge,
                             @Nullable Runnable onComplete) {
        int variantIndex = indexOfVariant(variant);
        if (variantIndex < 0) variantIndex = 0;
        // Interrupt previous without firing its completion — queue owns sequencing.
        interruptActive(false);
        int token = ++generation;
        activeCompletion = onComplete;
        setAlpha(1f);
        setVisibility(VISIBLE);
        final int finalVariant = variantIndex;
        final String media = CosmeticMedia.playableUrl(entryMediaUrl);
        post(() -> {
            if (token != generation) return;
            renderEntry(displayName, avatarUrl, remoteFrameUrl, media, finalVariant,
                    vipLevel, userLevel, wealthScore, vipBadgeUrl, levelBadgeUrl,
                    hostBadgeUrl, isHost, showHiBadge);
            scheduleFinish(token, durationMs > 0 ? durationMs : (media != null ? 6000 : 2800));
        });
        return true;
    }

    public void showSlotWinBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                   long winCoins, @Nullable String gameTitle) {
        if (getContext() == null) return;
        post(() -> showResultBubble(displayName, avatarUrl, winCoins, gameTitle, true));
    }

    public void showSlotLoseBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                    long betCoins, @Nullable String gameTitle) {
        if (getContext() == null) return;
        post(() -> showResultBubble(displayName, avatarUrl, betCoins, gameTitle, false));
    }

    private void showResultBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                  long coins, @Nullable String gameTitle, boolean won) {
        showResultBubble(displayName, avatarUrl, coins, gameTitle, null, won);
    }

    private void showResultBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                  long coins, @Nullable String gameTitle,
                                  @Nullable String gameIconUrl, boolean won) {
        // Game/luck bubbles are NOT user join toasts — no personal avatar/name chrome.
        // One line of text (winner name lives inside the sentence) + optional game cover only.
        setVisibility(VISIBLE);
        bringToFront();

        // One crawl at a time (Mikoo mid-screen banner) — clear older win bubbles.
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View old = getChildAt(i);
            if (old != null && "slot_result_bubble".equals(old.getTag())) {
                old.animate().cancel();
                removeViewAt(i);
            }
        }

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(10), padV = dp(6);
        card.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14));
        bg.setColor(won ? 0xD9121820 : 0xD9181418);
        bg.setStroke(dp(1), won ? 0x66FFD76A : 0x44FFFFFF);
        card.setBackground(bg);
        card.setElevation(dp(6));

        // Prefer game cover; never show the player's profile photo on this strip.
        String iconUrl = gameIconUrl != null && !gameIconUrl.isEmpty() ? gameIconUrl : null;
        if (iconUrl != null) {
            ImageView img = new ImageView(getContext());
            int iconSize = dp(28);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            imgLp.setMarginEnd(dp(8));
            img.setLayoutParams(imgLp);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable rounded = new GradientDrawable();
            rounded.setShape(GradientDrawable.RECTANGLE);
            rounded.setCornerRadius(dp(7));
            rounded.setColor(0xFF243038);
            rounded.setStroke(dp(1), won ? 0xFFFFD76A : 0x88FFFFFF);
            img.setBackground(rounded);
            img.setClipToOutline(true);
            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(7));
                }
            });
            try {
                Glide.with(getContext()).load(AssetCatalog.absoluteUrl(iconUrl))
                        .centerCrop().into(img);
            } catch (Exception ignored) {
                img.setImageResource(R.drawable.ic_screen_chat_lottery);
            }
            card.addView(img);
        }

        int maxText = Math.max(dp(160), Math.round(getResources().getDisplayMetrics().widthPixels * 0.48f));
        String who = displayName != null && !displayName.isEmpty() ? displayName : "لاعب";
        TextView tvLine = new TextView(getContext());
        tvLine.setTextColor(won ? 0xFFFFF3C4 : 0xFFFFFFFF);
        tvLine.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tvLine.setMaxLines(2);
        tvLine.setEllipsize(TextUtils.TruncateAt.END);
        tvLine.setMaxWidth(maxText);
        String line;
        if (won) {
            line = "مبروك " + who + " حصل على " + coins;
            if (gameTitle != null && !gameTitle.isEmpty()) {
                line += " · " + gameTitle;
            }
        } else {
            line = who + " خسر " + coins;
            if (gameTitle != null && !gameTitle.isEmpty()) {
                line += " · " + gameTitle;
            }
        }
        tvLine.setText(line);
        card.addView(tvLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setTag("slot_result_bubble");

        // Mid-screen horizontal crawl lane (slightly below true center).
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START);
        lp.topMargin = dp(28);
        addView(card, lp);
        setVisibility(VISIBLE);
        bringToFront();

        playBubbleCrawl(card, won ? 2200L : 1600L);
    }

    /**
     * Enter from start side → pause mid → crawl out to the opposite side.
     */
    private void playBubbleCrawl(View card, long holdMs) {
        card.setAlpha(0f);
        card.post(() -> {
            int w = Math.max(card.getWidth(), dp(180));
            int parentW = getWidth() > 0
                    ? getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            final float enterFrom = -w - dp(24);
            final float park = Math.max(dp(8), (parentW - w) / 2f);
            final float exitTo = parentW + dp(28);

            card.setTranslationX(enterFrom);
            card.setAlpha(1f);
            card.animate()
                    .translationX(park)
                    .setDuration(700)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> card.postDelayed(() -> {
                        if (card.getParent() == null) return;
                        float distance = Math.abs(exitTo - park);
                        long duration = Math.max(1400L, Math.min(2800L,
                                (long) (distance / Math.max(1f,
                                        getResources().getDisplayMetrics().density) * 8f)));
                        card.animate()
                                .translationX(exitTo)
                                .setDuration(duration)
                                .setInterpolator(new LinearInterpolator())
                                .withEndAction(() -> {
                                    removeView(card);
                                    if (!hasSlotBubbles() && getChildCount() == 0) {
                                        setVisibility(GONE);
                                    }
                                })
                                .start();
                    }, holdMs))
                    .start();
        });
    }

    /** @deprecated stacking unused — crawl bubbles are single-lane. */
    private void restackSlotBubbles() {
        // no-op kept for event-bubble callers during transition
    }

    private boolean hasSlotBubbles() {
        for (int i = 0; i < getChildCount(); i++) {
            if ("slot_result_bubble".equals(getChildAt(i).getTag())) return true;
        }
        return false;
    }

    /** Remove FX layers but keep Mikoo win/lose identity bubbles. */
    private void removeEffectViewsOnly() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child != null && "slot_result_bubble".equals(child.getTag())) continue;
            child.animate().cancel();
            if (child instanceof ImageView) {
                try {
                    Glide.with(getContext().getApplicationContext()).clear(child);
                } catch (Exception ignored) {
                }
            }
            removeViewAt(i);
        }
    }

    public void showSlotWinBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                   long winCoins, @Nullable String gameTitle,
                                   @Nullable String gameIconUrl) {
        if (getContext() == null) return;
        post(() -> showResultBubble(displayName, avatarUrl, winCoins, gameTitle, gameIconUrl, true));
    }

    public void showSlotLoseBubble(@Nullable String displayName, @Nullable String avatarUrl,
                                    long betCoins, @Nullable String gameTitle,
                                    @Nullable String gameIconUrl) {
        if (getContext() == null) return;
        post(() -> showResultBubble(displayName, avatarUrl, betCoins, gameTitle, gameIconUrl, false));
    }

    /**
     * Mikoo dark event bubble for magic-ball / planet / custom room events
     * (same lane as win bubbles — not Android Toast).
     */
    public void showRoomEventBubble(
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String message,
            @Nullable String badgeUrl
    ) {
        if (getContext() == null) return;
        String who = displayName != null && !displayName.isEmpty() ? displayName : "";
        String body = message != null && !message.isEmpty() ? message : "حدث في الغرفة";
        // Event bubbles without a personal displayName prefer body-only (no white “name · body”).
        post(() -> showEventBubbleCard(who, avatarUrl, body, badgeUrl));
    }

    private void showEventBubbleCard(
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String message,
            @Nullable String badgeUrl
    ) {
        setVisibility(VISIBLE);
        bringToFront();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View old = getChildAt(i);
            if (old != null && "slot_result_bubble".equals(old.getTag())) {
                old.animate().cancel();
                removeViewAt(i);
            }
        }

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(10), padV = dp(6);
        card.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14));
        bg.setColor(0xD9121820);
        bg.setStroke(dp(1), 0x66FFD76A);
        card.setBackground(bg);
        card.setElevation(dp(6));

        // Prefer game/gift badge over face; hide left icon when neither is set.
        String url = badgeUrl != null && !badgeUrl.isEmpty() ? badgeUrl
                : (avatarUrl != null && !avatarUrl.isEmpty() ? avatarUrl : null);
        if (url != null) {
            ImageView img = new ImageView(getContext());
            int avatarSize = dp(28);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
            imgLp.setMarginEnd(dp(8));
            img.setLayoutParams(imgLp);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable circle = new GradientDrawable();
            boolean face = avatarUrl != null && avatarUrl.equals(url);
            if (face) {
                circle.setShape(GradientDrawable.OVAL);
            } else {
                circle.setShape(GradientDrawable.RECTANGLE);
                circle.setCornerRadius(dp(7));
            }
            circle.setColor(0xFF243038);
            img.setBackground(circle);
            img.setClipToOutline(true);
            try {
                if (face) {
                    Glide.with(getContext()).load(AssetCatalog.absoluteUrl(url)).circleCrop().into(img);
                } else {
                    Glide.with(getContext()).load(AssetCatalog.absoluteUrl(url)).centerCrop().into(img);
                }
            } catch (Exception ignored) {
            }
            card.addView(img);
        }

        TextView tv = new TextView(getContext());
        tv.setTextColor(0xFFFFF3C4);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        int maxText = Math.max(dp(140), Math.round(getResources().getDisplayMetrics().widthPixels * 0.42f));
        tv.setMaxWidth(maxText);
        String who = displayName != null ? displayName : "";
        String body = message != null ? message : "";
        // Avoid "name · name body" double labels when body already contains the winner.
        if (who.isEmpty()) {
            tv.setText(body);
        } else if (body.isEmpty()) {
            tv.setText(who);
        } else if (body.contains(who)) {
            tv.setText(body);
        } else {
            tv.setText(who + " · " + body);
        }
        card.addView(tv);
        card.setTag("slot_result_bubble");

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START);
        lp.topMargin = dp(28);
        addView(card, lp);
        playBubbleCrawl(card, 2000L);
    }

    public void stopAll() {
        interruptActive(true);
    }

    /** Clears the active effect. When {@code fireCompletion} is true, notifies the queue. */
    private void interruptActive(boolean fireCompletion) {
        generation++;
        removeCallbacks(scheduledFinish);
        if (activeAnimators != null) {
            activeAnimators.cancel();
            activeAnimators = null;
        }
        Runnable completion = activeCompletion;
        activeCompletion = null;
        clearContent();
        if (fireCompletion && completion != null) completion.run();
    }

    public void destroy() {
        stopAll();
        removeAllViews();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override protected void onDetachedFromWindow() {
        stopAll();
        super.onDetachedFromWindow();
    }

    private void renderGift(GiftSpec spec, @Nullable String remoteIconUrl,
                            @Nullable String animationUrl, @Nullable String senderName, int quantity) {
        removeEffectViewsOnly();
        releaseGiftPlayer();
        int width = Math.max(getWidth(), dp(120));
        int height = Math.max(getHeight(), dp(120));
        String anim = animationUrl != null ? animationUrl.trim() : "";
        if (anim.toLowerCase(Locale.US).contains("runtime.html")
                || anim.toLowerCase(Locale.US).endsWith(".html")) {
            anim = "";
        }
        CosmeticMedia.Kind animKind = CosmeticMedia.kind(anim);
        // Center stage for image / gif / video / SVGA — same presentation as video gifts.
        boolean mediaFx = animKind == CosmeticMedia.Kind.SVGA
                || animKind == CosmeticMedia.Kind.VIDEO
                || animKind == CosmeticMedia.Kind.GIF
                || animKind == CosmeticMedia.Kind.IMAGE
                || (anim != null && !anim.isEmpty());
        // Prefer icon when animation empty so still gifts still fullscreen.
        String effectiveAnim = mediaFx ? anim : animationUrl;
        if ((effectiveAnim == null || effectiveAnim.isEmpty())
                && remoteIconUrl != null && !remoteIconUrl.isEmpty()) {
            effectiveAnim = remoteIconUrl;
            animKind = CosmeticMedia.kind(effectiveAnim);
        }
        boolean fullscreenMedia = animKind == CosmeticMedia.Kind.VIDEO
                || animKind == CosmeticMedia.Kind.SVGA
                || animKind == CosmeticMedia.Kind.GIF
                || animKind == CosmeticMedia.Kind.IMAGE
                || (effectiveAnim != null && !effectiveAnim.isEmpty());

        View visual = createGiftVisual(spec, remoteIconUrl,
                effectiveAnim, Math.min(width, height));
        if (visual != null) {
            if (visual instanceof ImageView) {
                ((ImageView) visual).setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            // Full-bleed stage for video/SVGA/image — never the chat recycle bounds.
            LayoutParams lp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
            addView(visual, lp);
            // VAP AnimView must stay 1.0 scale/alpha (same as entry ride). Pop hurts VAP decode.
            boolean vapAnim = visual instanceof AnimView;
            if (!vapAnim) {
                try {
                    visual.setScaleX(0.28f);
                    visual.setScaleY(0.28f);
                    visual.setAlpha(0f);
                    ObjectAnimator pop = ObjectAnimator.ofPropertyValuesHolder(visual,
                            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.28f, 1f),
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.28f, 1f),
                            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f));
                    pop.setDuration(420);
                    pop.setInterpolator(new DecelerateInterpolator(1.4f));
                    pop.start();
                } catch (Exception ignored) {
                }
            } else {
                visual.setScaleX(1f);
                visual.setScaleY(1f);
                visual.setAlpha(1f);
            }
            if (fullscreenMedia) {
                try {
                    setElevation(Math.max(getElevation(), 52f));
                    bringToFront();
                } catch (Exception ignored) {
                }
            }
        }
        // ComboGiftView already shows sender + gift name. Skip the old
        // "هدية من <long id>" overlay — especially on video gifts.
        if (!fullscreenMedia) {
            String title = spec.name != null ? spec.name.trim() : "";
            if (!title.isEmpty() && !looksLikeRawMediaId(title)) {
                TextView label = label(title + (quantity > 1 ? " ×" + quantity : ""), "");
                LayoutParams labelParams = new LayoutParams(
                        Math.min(dp(430), Math.round(width * .92f)), LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                labelParams.bottomMargin = Math.max(dp(8), Math.round(height * .04f));
                addView(label, labelParams);
                ObjectAnimator labelIn = ObjectAnimator.ofPropertyValuesHolder(label,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f));
                labelIn.setDuration(280);
                activeAnimators = new AnimatorSet();
                activeAnimators.play(labelIn);
                activeAnimators.start();
            }
        }
    }

    /**
     * Entry rides: Tencent AnimView (VAP RGB|alpha + sizes from embedded vapc JSON).
     */
    @Nullable
    private View createEntryRideVisual(String url, int width, int height) {
        return createVapAnimVisual(url, /* forGift */ false);
    }

    /**
     * Same VAP pipeline as room entry — entry-effect MP4s are dual-plate (RGB | alpha).
     * Playing them with ExoPlayer shows "half video + half black".
     */
    @Nullable
    private View createVapAnimVisual(@Nullable String url, boolean forGift) {
        String abs = AssetCatalog.absoluteUrl(url);
        if (abs == null || abs.isEmpty()) return null;
        CosmeticMedia.Kind kind = CosmeticMedia.kind(abs);
        if (kind == CosmeticMedia.Kind.IMAGE) {
            String mp4 = abs.replaceAll("(?i)\\.(png|jpe?g|webp)(\\?.*)?$", ".mp4$2");
            if (!mp4.equals(abs) && CosmeticMedia.kind(mp4) == CosmeticMedia.Kind.VIDEO) {
                abs = mp4;
                kind = CosmeticMedia.Kind.VIDEO;
            }
        }
        if (kind == CosmeticMedia.Kind.GIF) {
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.TRANSPARENT);
            try {
                Glide.with(this).asGif().load(abs).into(image);
            } catch (Exception ignored) {
                return null;
            }
            image.setLayoutParams(new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            return image;
        }
        if (kind != CosmeticMedia.Kind.VIDEO) {
            android.util.Log.w("NativeRoomEffects", "skip static vap media: " + abs);
            return null;
        }

        releaseGiftPlayer();
        final AnimView anim = new AnimView(getContext());
        anim.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        anim.setBackgroundColor(Color.TRANSPARENT);
        // Same as Mikoo entry: center-crop full stage; vapc inside MP4 controls RGB size.
        anim.setScaleType(ScaleType.CENTER_CROP);
        anim.setLoop(1);
        // Entry gifts: play gift/MP4 audio. Room-join entry rides stay muted
        // so a wave of joins does not drown mic chat.
        anim.setMute(!forGift);
        if (forGift) {
            giftAnimView = anim;
        } else {
            entryAnimView = anim;
        }
        bindAnimFinish(anim);

        final String mediaUrl = abs;
        final boolean giftMode = forGift;
        new Thread(() -> {
            File file = cacheEntryMp4(mediaUrl);
            if (file == null || !file.exists() || file.length() <= 8_192) {
                android.util.Log.w("NativeRoomEffects", "vap cache miss: " + mediaUrl);
                post(() -> {
                    if (giftMode) {
                        if (giftAnimView == anim) finishActive();
                    } else if (entryAnimView == anim) {
                        finishActive();
                    }
                });
                return;
            }
            // Gift path: plain MP4 (no vapc) → Exo fullscreen. Entry rides stay on AnimView.
            if (giftMode && !localFileHasVapc(file)) {
                android.util.Log.i("NativeRoomEffects",
                        "gift mp4 has no vapc — Exo fullscreen url=" + mediaUrl);
                post(() -> {
                    if (giftAnimView != anim) return;
                    try {
                        removeView(anim);
                    } catch (Exception ignored) {
                    }
                    giftAnimView = null;
                    View streamed = createGiftVideoStream(mediaUrl, null, null);
                    if (streamed != null) {
                        addView(streamed, new LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER));
                    } else {
                        finishActive();
                    }
                });
                return;
            }
            post(() -> {
                AnimView current = giftMode ? giftAnimView : entryAnimView;
                if (current != anim) return;
                Runnable start = () -> {
                    AnimView still = giftMode ? giftAnimView : entryAnimView;
                    if (still != anim || anim.getParent() == null) return;
                    try {
                        anim.startPlay(file);
                    } catch (Exception e) {
                        android.util.Log.w("NativeRoomEffects", "AnimView start failed", e);
                        finishActive();
                    }
                };
                if (anim.isAttachedToWindow()) {
                    start.run();
                } else {
                    anim.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View v) {
                            anim.removeOnAttachStateChangeListener(this);
                            start.run();
                        }
                        @Override public void onViewDetachedFromWindow(View v) {}
                    });
                }
            });
        }, giftMode ? "gift-vap" : "entry-vap").start();
        return anim;
    }

    /** Entry-effect / Mikoo car packs — dual-plate VAP (sizes from vapc JSON in the MP4). */
    private static boolean looksLikeEntryVapMedia(@Nullable String absUrl) {
        if (absUrl == null || absUrl.isEmpty()) return false;
        String u = absUrl.toLowerCase(Locale.US);
        if (u.contains("/cosmetics/entries/")
                || u.contains("/entries/entry_")
                || u.contains("entry_mikoo")
                || u.contains("mikoo_gift_car")
                || u.contains("/entry_effect")
                || u.contains("visual-system/entry")
                || u.contains("دخولية")
                || u.contains("entry_gift")
                || u.contains("gift_entry")
                || u.contains("/gifts/")
                || u.contains("/uploads/")
                || u.contains("vap")
                || u.contains("_vap")) {
            return true;
        }
        // Common CDN for Mikoo entry cars reused as gifts.
        if (u.contains("echoliveapp.com/video/") || u.contains("res.echoliveapp.com/video/")) {
            return true;
        }
        return false;
    }

    /** Detect embedded {@code vapc} metadata in a cached MP4 without full decode. */
    private static boolean localFileHasVapc(@Nullable File file) {
        if (file == null || !file.exists() || file.length() < 64) return false;
        try {
            byte[] head = new byte[(int) Math.min(file.length(), 262_144L)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int off = 0;
                while (off < head.length) {
                    int n = in.read(head, off, head.length - off);
                    if (n <= 0) break;
                    off += n;
                }
                if (off < head.length) {
                    byte[] trim = new byte[off];
                    System.arraycopy(head, 0, trim, 0, off);
                    head = trim;
                }
            }
            return VapLayout.parse(head) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gift video: download (or use warm cache) then play local file — never leave the user
     * staring at a static icon because TextureView was INVISIBLE / stream never painted.
     * Never re-stream a URL that already failed download (404/403) — try mapped fallback instead.
     * Entry/VAP packs re-route to {@link #createVapAnimVisual} (same as room entry).
     */
    @Nullable
    private View createGiftVideoStream(String absUrl, @Nullable String iconUrl,
                                       @Nullable String fallbackUrl) {
        if (absUrl == null || absUrl.isEmpty()) return null;
        releaseGiftPlayer();

        String playUrl = absUrl;
        try {
            String abs = AssetCatalog.absoluteUrl(absUrl);
            if (abs != null && !abs.isEmpty()) playUrl = abs;
        } catch (Exception ignored) {
        }
        String fallbackAbs = null;
        if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
            try {
                String f = AssetCatalog.absoluteUrl(fallbackUrl.trim());
                if (f != null && !f.isEmpty() && !f.equalsIgnoreCase(playUrl)) {
                    fallbackAbs = f;
                }
            } catch (Exception ignored) {
            }
        }

        FrameLayout stage = new FrameLayout(getContext());
        stage.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        stage.setBackgroundColor(Color.TRANSPARENT);
        stage.setClickable(false);
        stage.setFocusable(false);

        ImageView placeholder = new ImageView(getContext());
        placeholder.setScaleType(ImageView.ScaleType.FIT_CENTER);
        placeholder.setAlpha(0.95f);
        int ph = Math.max(dp(180), Math.round(Math.min(
                Math.max(getWidth(), dp(220)),
                Math.max(getHeight(), dp(220))) * 0.48f));
        stage.addView(placeholder, new FrameLayout.LayoutParams(ph, ph, Gravity.CENTER));
        String iconAbs = AssetCatalog.absoluteUrl(iconUrl);
        try {
            if (iconAbs != null && !iconAbs.isEmpty()) {
                Glide.with(getContext().getApplicationContext())
                        .load(iconAbs)
                        .fitCenter()
                        .into(placeholder);
            } else {
                placeholder.setImageResource(R.drawable.ic_asset_gift);
            }
        } catch (Exception e) {
            placeholder.setImageResource(R.drawable.ic_asset_gift);
        }

        // MUST stay VISIBLE (alpha 0): INVISIBLE TextureView never renders frames on many devices.
        PlayerView playerView = (PlayerView) android.view.LayoutInflater
                .from(getContext())
                .inflate(R.layout.view_gift_exo_player, stage, false);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.TRANSPARENT);
        playerView.setShutterBackgroundColor(Color.TRANSPARENT);
        playerView.setVisibility(VISIBLE);
        playerView.setAlpha(0f);
        try {
            // Fill the entire chat-stage bounds (cover), not a small letterboxed fit.
            playerView.setResizeMode(
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        } catch (Exception ignored) {
        }
        stage.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        final int token = generation;
        final String finalUrl = playUrl;
        final String altUrl = fallbackAbs;
        File warmPrimary = peekGiftMp4Cache(getContext(), finalUrl);
        File warmAlt = altUrl != null ? peekGiftMp4Cache(getContext(), altUrl) : null;
        File warm = warmPrimary != null ? warmPrimary : warmAlt;
        String warmUrl = warmPrimary != null ? finalUrl : (warmAlt != null ? altUrl : null);
        // Cached file is a VAP entry pack → AnimView (same as room join), not Exo.
        if (warm != null && warmUrl != null && localFileHasVapc(warm)) {
            View vap = createVapAnimVisual(warmUrl, true);
            if (vap != null) return vap;
        }
        if (warm != null) {
            bindGiftExoPlayer(stage, playerView, placeholder, warm, warmUrl != null ? warmUrl : finalUrl,
                    iconUrl, token, true,
                    warmPrimary != null ? altUrl : null);
        } else {
            android.util.Log.i("NativeRoomEffects", "gift video downloading url=" + finalUrl
                    + (altUrl != null ? (" alt=" + altUrl) : ""));
            final Context app = getContext().getApplicationContext();
            new Thread(() -> {
                File file = cacheGiftMp4(app, finalUrl);
                String chosenUrl = finalUrl;
                File chosen = file;
                if ((chosen == null || !chosen.exists() || chosen.length() <= 8_192)
                        && altUrl != null) {
                    File altFile = cacheGiftMp4(app, altUrl);
                    if (altFile != null && altFile.exists() && altFile.length() > 8_192) {
                        chosen = altFile;
                        chosenUrl = altUrl;
                    }
                }
                final File playFile = chosen;
                final String playChosen = chosenUrl;
                post(() -> {
                    if (token != generation) return;
                    if (playFile != null && playFile.exists() && playFile.length() > 8_192) {
                        if (localFileHasVapc(playFile)) {
                            try {
                                removeView(stage);
                            } catch (Exception ignored) {
                            }
                            View vap = createVapAnimVisual(playChosen, true);
                            if (vap != null) {
                                addView(vap, new LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        Gravity.CENTER));
                                return;
                            }
                        }
                        bindGiftExoPlayer(stage, playerView, placeholder, playFile,
                                playChosen, iconUrl, token, true,
                                playChosen.equals(altUrl) ? null : altUrl);
                    } else if (altUrl != null) {
                        bindGiftExoPlayer(stage, playerView, placeholder, null,
                                altUrl, iconUrl, token, false, null);
                    } else {
                        android.util.Log.w("NativeRoomEffects",
                                "gift video missing after download, skip stream url=" + finalUrl);
                        releaseGiftPlayer();
                        placeholder.setAlpha(1f);
                        placeholder.setVisibility(VISIBLE);
                        postDelayed(() -> {
                            if (token == generation) finishActive();
                        }, 1600);
                    }
                });
            }, "gift-mp4-fetch").start();
        }
        return stage;
    }

    private void bindGiftExoPlayer(FrameLayout stage,
                                   PlayerView playerView,
                                   ImageView placeholder,
                                   @Nullable File localFile,
                                   String playUrl,
                                   @Nullable String iconUrl,
                                   int token,
                                   boolean fromFile,
                                   @Nullable String fallbackUrl) {
        if (token != generation) return;
        releaseGiftPlayer();

        androidx.media3.exoplayer.DefaultLoadControl loadControl =
                new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(250, 10_000, 100, 250)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();

        androidx.media3.datasource.DataSource.Factory dataSourceFactory;
        android.net.Uri mediaUri;
        if (fromFile && localFile != null) {
            mediaUri = android.net.Uri.fromFile(localFile);
            dataSourceFactory = new androidx.media3.datasource.DefaultDataSource.Factory(
                    getContext());
        } else {
            mediaUri = android.net.Uri.parse(playUrl);
            dataSourceFactory = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("JEHO-Android/2.0.44 (ExoPlayer)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(10_000)
                    .setReadTimeoutMs(30_000);
        }

        ExoPlayer player = new ExoPlayer.Builder(getContext())
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                        new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                dataSourceFactory))
                .build();
        giftPlayer = player;
        playerView.setPlayer(player);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        try {
            // Gift MP4 audio (including entry-effect gifts). handleAudioFocus=false so
            // Zego/LiveKit room voice is not paused while the effect plays.
            player.setVolume(1f);
            player.setAudioAttributes(
                    new androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                    /* handleAudioFocus= */ false);
        } catch (Exception ignored) {
            try { player.setVolume(1f); } catch (Exception ignored2) {}
        }

        final Runnable reveal = () -> {
            if (giftPlayer != player || token != generation) return;
            playerView.setAlpha(1f);
            placeholder.animate().alpha(0f).setDuration(120)
                    .withEndAction(() -> placeholder.setVisibility(GONE))
                    .start();
        };

        player.addListener(new Player.Listener() {
            private boolean revealed;

            private void revealOnce() {
                if (revealed) return;
                revealed = true;
                post(reveal);
            }

            @Override
            public void onRenderedFirstFrame() {
                if (giftPlayer != player || token != generation) return;
                revealOnce();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (giftPlayer != player || token != generation) return;
                if (playbackState == Player.STATE_READY) {
                    revealOnce();
                    try { player.play(); } catch (Exception ignored) {}
                } else if (playbackState == Player.STATE_ENDED) {
                    post(() -> finishActive());
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (giftPlayer != player || token != generation) return;
                android.util.Log.w("NativeRoomEffects",
                        "gift exo failed file=" + fromFile + " url=" + playUrl, error);
                post(() -> {
                    if (fromFile && localFile != null) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            localFile.delete();
                        } catch (Exception ignored) {
                        }
                        String next = (fallbackUrl != null && !fallbackUrl.equalsIgnoreCase(playUrl))
                                ? fallbackUrl : null;
                        if (next != null) {
                            bindGiftExoPlayer(stage, playerView, placeholder, null,
                                    next, iconUrl, token, false, null);
                        } else {
                            // Corrupt cache — do not re-stream same dead URL (404 loop).
                            releaseGiftPlayer();
                            placeholder.setAlpha(1f);
                            placeholder.setVisibility(VISIBLE);
                            postDelayed(() -> {
                                if (token == generation) finishActive();
                            }, 1600);
                        }
                        return;
                    }
                    if (fallbackUrl != null && !fallbackUrl.equalsIgnoreCase(playUrl)) {
                        bindGiftExoPlayer(stage, playerView, placeholder, null,
                                fallbackUrl, iconUrl, token, false, null);
                        return;
                    }
                    releaseGiftPlayer();
                    placeholder.setAlpha(1f);
                    placeholder.setVisibility(VISIBLE);
                    // Keep icon briefly then finish — never silent black.
                    postDelayed(() -> {
                        if (token == generation) finishActive();
                    }, 1600);
                });
            }
        });

        player.setMediaItem(MediaItem.fromUri(mediaUri));
        player.prepare();
        player.play();
        android.util.Log.i("NativeRoomEffects",
                "gift video play file=" + fromFile + " url=" + playUrl
                        + (localFile != null ? (" bytes=" + localFile.length()) : ""));
    }

    private void bindAnimFinish(AnimView anim) {
        if (anim == null) return;
        try {
            anim.setAnimListener(new com.tencent.qgame.animplayer.inter.IAnimListener() {
                @Override
                public void onFailed(int errorType, @Nullable String errorMsg) {
                    post(() -> {
                        if (entryAnimView == anim || giftAnimView == anim) finishActive();
                    });
                }

                @Override
                public void onVideoComplete() {
                    post(() -> {
                        if (entryAnimView == anim || giftAnimView == anim) finishActive();
                    });
                }

                @Override
                public void onVideoDestroy() {}

                @Override
                public void onVideoStart() {}

                @Override
                public void onVideoRender(int frameIndex,
                                          @Nullable com.tencent.qgame.animplayer.AnimConfig config) {}

                @Override
                public boolean onVideoConfigReady(
                        @Nullable com.tencent.qgame.animplayer.AnimConfig config) {
                    return true;
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Download gift MP4 once into app cache for instant local playback. */
    @Nullable
    public static File cacheGiftMp4(Context context, String absUrl) {
        if (context == null || absUrl == null || absUrl.isEmpty()) return null;
        try {
            File dir = new File(context.getCacheDir(), "entry_vap");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String name = Integer.toHexString(absUrl.split("\\?")[0].hashCode()) + ".mp4";
            File out = new File(dir, name);
            if (out.exists() && out.length() > 8_192) return out;
            File tmp = new File(dir, name + ".tmp");
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            java.net.URL url = new java.net.URL(absUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(45_000);
            conn.setRequestProperty("User-Agent", "JEHO-Android/2.0.43 (GiftCache)");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                conn.disconnect();
                return null;
            }
            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) fos.write(buf, 0, n);
                fos.getFD().sync();
            } finally {
                conn.disconnect();
            }
            if (tmp.length() <= 8_192) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return null;
            }
            if (out.exists()) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
            if (!tmp.renameTo(out)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return null;
            }
            return out;
        } catch (Exception e) {
            android.util.Log.w("NativeRoomEffects", "cacheGiftMp4 failed", e);
            return null;
        }
    }

    @Nullable
    private static File peekGiftMp4Cache(Context context, String absUrl) {
        if (context == null || absUrl == null || absUrl.isEmpty()) return null;
        try {
            File dir = new File(context.getCacheDir(), "entry_vap");
            String name = Integer.toHexString(absUrl.split("\\?")[0].hashCode()) + ".mp4";
            File out = new File(dir, name);
            if (out.exists() && out.length() > 8_192) return out;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Warm gift media so send/play is instant for everyone in the room. */
    public static void preloadGiftUrls(Context context, @Nullable Iterable<String> urls) {
        if (context == null || urls == null) return;
        Context app = context.getApplicationContext();
        java.util.ArrayList<String> videos = new java.util.ArrayList<>();
        java.util.ArrayList<String> images = new java.util.ArrayList<>();
        for (String raw : urls) {
            if (raw == null || raw.isEmpty()) continue;
            String abs = AssetCatalog.absoluteUrl(raw);
            if (abs == null || abs.isEmpty()) continue;
            CosmeticMedia.Kind kind = CosmeticMedia.kind(abs);
            if (kind == CosmeticMedia.Kind.VIDEO) {
                if (!videos.contains(abs)) videos.add(abs);
            } else if (kind == CosmeticMedia.Kind.GIF
                    || kind == CosmeticMedia.Kind.IMAGE
                    || kind == CosmeticMedia.Kind.SVGA) {
                if (!images.contains(abs)) images.add(abs);
            }
        }
        // Videos first — limited concurrency so weak networks still finish the important ones.
        final java.util.concurrent.atomic.AtomicInteger inFlight =
                new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.ConcurrentLinkedQueue<String> q =
                new java.util.concurrent.ConcurrentLinkedQueue<>(videos);
        Runnable pump = new Runnable() {
            @Override public void run() {
                while (inFlight.get() < 2) {
                    String next = q.poll();
                    if (next == null) return;
                    if (peekGiftMp4Cache(app, next) != null) continue;
                    inFlight.incrementAndGet();
                    final String url = next;
                    new Thread(() -> {
                        try {
                            cacheGiftMp4(app, url);
                        } finally {
                            inFlight.decrementAndGet();
                            run();
                        }
                    }, "gift-preload").start();
                }
            }
        };
        pump.run();
        for (String abs : images) {
            try {
                Glide.with(app).load(abs).preload();
            } catch (Exception ignored) {
            }
        }
    }

    /** Download entry MP4 once into app cache (AnimView needs a local file). */
    @Nullable
    private File cacheEntryMp4(String absUrl) {
        return cacheGiftMp4(getContext(), absUrl);
    }

    private View createGiftVisual(GiftSpec spec, @Nullable String remoteIconUrl,
                                  @Nullable String animationUrl, int visualSize) {
        String anim = animationUrl != null ? animationUrl.trim() : "";
        // Backend stores HTML engine URL for all gifts — native uses catalog assets/sprites.
        if (GiftMediaResolver.isHtmlPlaceholder(anim)) {
            anim = "";
        }

        // Prefer resolved media by type: admin image / gif / video wins over name remaps.
        String resolved = null;
        try {
            resolved = GiftMediaResolver.resolvePlayable(
                    spec != null ? spec.name : null, remoteIconUrl, anim.isEmpty() ? null : anim);
        } catch (Exception ignored) {
        }
        String pick = resolved != null && !resolved.isEmpty()
                ? resolved
                : (anim.isEmpty() ? firstNonEmpty(remoteIconUrl, null) : anim);
        String abs = pick == null || pick.isEmpty() ? "" : AssetCatalog.absoluteUrl(pick);
        CosmeticMedia.Kind kind = CosmeticMedia.kind(abs);

    
        if (kind == CosmeticMedia.Kind.VIDEO) {
            String fallback = GiftMediaResolver.isTrustedUpload(abs)
                    ? null
                    : GiftMediaResolver.resolveMappedFallback(
                            spec != null ? spec.name : null, remoteIconUrl);
            if (fallback != null) {
                try {
                    String fAbs = AssetCatalog.absoluteUrl(fallback);
                    if (fAbs != null && fAbs.equalsIgnoreCase(abs)) fallback = null;
                } catch (Exception ignored) {
                }
            }
            View streamed = createGiftVideoStream(abs, remoteIconUrl, fallback);
            if (streamed != null) return streamed;
        }

        // Same as Mikoo: SVGA full-screen gift effect.
        if (kind == CosmeticMedia.Kind.SVGA) {
            final String absSvga = abs;
            try {
                com.opensource.svgaplayer.SVGAImageView svga =
                        new com.opensource.svgaplayer.SVGAImageView(getContext());
                svga.setScaleType(ImageView.ScaleType.FIT_CENTER);
                svga.setClearsAfterStop(true);
                svga.setLoops(1);
                giftSvgaView = svga;
                com.opensource.svgaplayer.SVGAParser parser =
                        new com.opensource.svgaplayer.SVGAParser(getContext());
                parser.decodeFromURL(new URL(absSvga), new com.opensource.svgaplayer.SVGAParser.ParseCompletion() {
                    @Override
                    public void onComplete(
                            @androidx.annotation.NonNull
                            com.opensource.svgaplayer.SVGAVideoEntity videoItem) {
                        post(() -> {
                            if (giftSvgaView != svga) return;
                            svga.setVideoItem(videoItem);
                            svga.startAnimation();
                            try {
                                svga.setCallback(new com.opensource.svgaplayer.SVGACallback() {
                                    @Override public void onPause() {}
                                    @Override public void onFinished() {
                                        post(() -> {
                                            if (giftSvgaView == svga) finishActive();
                                        });
                                    }
                                    @Override public void onRepeat() {}
                                    @Override public void onStep(int i, double v) {}
                                });
                            } catch (Exception ignored) {
                            }
                        });
                    }

                    @Override
                    public void onError() {
                        android.util.Log.w("NativeRoomEffects", "SVGA gift parse failed: " + absSvga);
                        post(() -> finishActive());
                    }
                }, null);
                return svga;
            } catch (Exception ignored) {
            }
        }

        String lower = abs != null ? abs.toLowerCase(Locale.US) : "";
        if (lower.contains(".json")) {
            LottieAnimationView lottie = new LottieAnimationView(getContext());
            lottie.setRepeatCount(0);
            lottie.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try {
                lottie.setAnimationFromUrl(abs);
                lottie.playAnimation();
            } catch (Exception ignored) {
            }
            return lottie;
        }

        SpriteSpec sprite = SPRITES.get(spec.id);
        if (sprite != null) {
            Bitmap sheet = decodeAssetFull("visual-system/gifts/sprites/" + sprite.file);
            if (sheet != null && sheet.getWidth() >= sprite.frames) {
                SpriteSheetView spriteView = new SpriteSheetView(getContext());
                spriteView.play(sheet, sprite.frames, sprite.cycleMs);
                activeSprite = spriteView;
                return spriteView;
            }
        }

        // Static image / GIF / (animated) WebP — show exactly what was uploaded.
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String iconAbs = remoteIconUrl != null && !remoteIconUrl.isEmpty()
                ? AssetCatalog.absoluteUrl(remoteIconUrl) : null;
        String imageUrl = !abs.isEmpty() ? abs : iconAbs;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            String il = imageUrl.toLowerCase(Locale.US);
            boolean animated = kind == CosmeticMedia.Kind.GIF
                    || il.contains(".gif")
                    || (il.contains(".webp") && !il.contains("static"));
            try {
                if (animated) {
                    Glide.with(this).asGif().load(imageUrl)
                            .fitCenter()
                            .into(image);
                } else {
                    Glide.with(this).load(imageUrl)
                            .fitCenter()
                            .into(image);
                }
            } catch (Exception e) {
                try {
                    Glide.with(this).load(imageUrl).fitCenter().into(image);
                } catch (Exception ignored) {
                    loadAssetOrRemote(image, "visual-system/gifts/assets/gift-" + spec.id + ".png",
                            remoteIconUrl, visualSize, visualSize);
                }
            }
        } else {
            loadAssetOrRemote(image, "visual-system/gifts/assets/gift-" + spec.id + ".png",
                    remoteIconUrl, visualSize, visualSize);
        }
        return image;
    }

    private void renderEntry(@Nullable String displayName, @Nullable String avatarUrl,
                             @Nullable String remoteFrameUrl, @Nullable String entryMediaUrl,
                             int variantIndex,
                             int vipLevel, int userLevel, long wealthScore,
                             @Nullable String vipBadgeUrl, @Nullable String levelBadgeUrl,
                             @Nullable String hostBadgeUrl, boolean isHost, boolean showHiBadge) {
        removeEffectViewsOnly();
        releaseGiftPlayer();
        int width = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(getHeight(), getResources().getDisplayMetrics().heightPixels);

        // Mikoo: full-screen entry ride only. Join name toast is ComingMsgView (single toast).
        String ride = CosmeticMedia.playableUrl(entryMediaUrl);
        if (ride == null) return;
        View rideView = createEntryRideVisual(ride, width, height);
        if (rideView == null) return;
        LayoutParams rideLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        rideView.setLayoutParams(rideLp);
        rideView.setAlpha(1f);
        rideView.setTranslationY(0f);
        rideView.setScaleX(1f);
        rideView.setScaleY(1f);
        addView(rideView, rideLp);
    }

    /** Entry effect: fade + rise from bottom into the room stage.
     * Animate the wrapper only — never set TextureView alpha (breaks video on many devices). */
    private AnimatorSet riseEntryEntrance(View image, int height) {
        float fromY = Math.max(dp(160), height * 0.28f);
        image.setAlpha(1f);
        image.setTranslationY(fromY);
        image.setScaleX(0.92f);
        image.setScaleY(0.92f);
        ObjectAnimator enter = ObjectAnimator.ofPropertyValuesHolder(image,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, fromY, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1f));
        enter.setDuration(720);
        enter.setInterpolator(new DecelerateInterpolator(1.45f));
        AnimatorSet set = new AnimatorSet();
        set.play(enter);
        return set;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    private static int mixColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = ((from >>> 24) & 0xff) + Math.round((((to >>> 24) & 0xff) - ((from >>> 24) & 0xff)) * t);
        int r = ((from >>> 16) & 0xff) + Math.round((((to >>> 16) & 0xff) - ((from >>> 16) & 0xff)) * t);
        int g = ((from >>> 8) & 0xff) + Math.round((((to >>> 8) & 0xff) - ((from >>> 8) & 0xff)) * t);
        int b = (from & 0xff) + Math.round(((to & 0xff) - (from & 0xff)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private TextView chip(String text, int accent) {
        TextView chip = new TextView(getContext());
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(8);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setPadding(dp(5), dp(1), dp(5), dp(1));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor((accent & 0x00FFFFFF) | 0x99000000);
        bg.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(3));
        chip.setLayoutParams(lp);
        return chip;
    }

    private static String formatCompact(long value) {
        if (value >= 1_000_000L) return String.format(Locale.US, "%.1fM", value / 1_000_000f);
        if (value >= 1_000L) return String.format(Locale.US, "%.1fK", value / 1_000f);
        return String.valueOf(value);
    }

    private AnimatorSet giftEntrance(String family, View image, int width, int height) {
        if (isCrossingFamily(family)) {
            return crossingEntrance(family, image, width, height);
        }
        float startX = 0f, startY = 0f, startScale = .15f, startRotation = 0f;
        long duration = 1100;
        switch (family) {
            case "bounce": startY = -height * .7f; startScale = .5f; startRotation = -12f; break;
            case "rise": case "crown": case "stomp":
                startY = height * .65f; startScale = .55f; break;
            case "fly": case "rocket": case "meteor":
                startX = -width * .8f; startY = height * .5f; startScale = .25f;
                startRotation = -28f; break;
            case "sail": startX = width * 1.1f; startScale = .55f; startRotation = 6f; break;
            case "swing": startY = -height * .65f; startScale = .6f; startRotation = -28f; break;
            case "crystal": startY = -height * .35f; startScale = .25f; startRotation = 180f; break;
            case "dragon": case "phoenix":
                startScale = .15f; startRotation = -18f; duration = 1250; break;
            case "orbit": startScale = .15f; startRotation = -190f; break;
            case "heart": startScale = .1f; break;
            case "burst": case "box": case "music": startScale = .05f; startRotation = -35f; break;
            default: startScale = .15f; startRotation = -18f; break;
        }
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator transform = ObjectAnimator.ofPropertyValuesHolder(image,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, startX, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startY, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, startScale, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, startScale, 1f),
                PropertyValuesHolder.ofFloat(View.ROTATION, startRotation, 0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f));
        transform.setDuration(duration);
        transform.setInterpolator(new DecelerateInterpolator(2f));
        set.play(transform);
        return set;
    }

    /** Full-screen walk/drive matching Host-signals gifts.css cross keyframes. */
    private AnimatorSet crossingEntrance(String family, View image, int width, int height) {
        float dir = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1f : 1f;
        Keyframe[] xKeys;
        Keyframe[] yKeys;
        Keyframe[] sxKeys;
        Keyframe[] rotKeys;
        Keyframe[] aKeys;
        long duration = 4050;
        switch (family) {
            case "train":
                duration = 4100;
                xKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, dir * -width * 1.05f),
                        Keyframe.ofFloat(.5f, dir * -width * .05f),
                        Keyframe.ofFloat(1f, dir * width * 1.1f)};
                yKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, height * .08f),
                        Keyframe.ofFloat(.5f, 0f),
                        Keyframe.ofFloat(1f, height * .04f)};
                sxKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, .5f), Keyframe.ofFloat(.5f, 1.05f), Keyframe.ofFloat(1f, .58f)};
                rotKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(1f, 0f)};
                aKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.22f, 1f),
                        Keyframe.ofFloat(.85f, 1f), Keyframe.ofFloat(1f, 0f)};
                break;
            case "tractor":
                duration = 4100;
                xKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, dir * -width * .9f),
                        Keyframe.ofFloat(.36f, dir * -width * .35f),
                        Keyframe.ofFloat(.52f, dir * -width * .04f),
                        Keyframe.ofFloat(.7f, dir * width * .34f),
                        Keyframe.ofFloat(1f, dir * width * .92f)};
                yKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, height * .07f), Keyframe.ofFloat(.52f, -height * .02f),
                        Keyframe.ofFloat(1f, height * .06f)};
                sxKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, .52f), Keyframe.ofFloat(.52f, 1.04f), Keyframe.ofFloat(1f, .55f)};
                rotKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, -2f), Keyframe.ofFloat(.52f, -1f), Keyframe.ofFloat(1f, 0f)};
                aKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.18f, 1f),
                        Keyframe.ofFloat(.88f, 1f), Keyframe.ofFloat(1f, 0f)};
                break;
            case "bird":
                duration = 4100;
                xKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, dir * -width * .75f),
                        Keyframe.ofFloat(.45f, dir * -width * .12f),
                        Keyframe.ofFloat(.58f, dir * width * .06f),
                        Keyframe.ofFloat(.76f, dir * width * .36f),
                        Keyframe.ofFloat(1f, dir * width * .8f)};
                yKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, height * .45f), Keyframe.ofFloat(.45f, -height * .08f),
                        Keyframe.ofFloat(.58f, -height * .16f), Keyframe.ofFloat(1f, -height * .48f)};
                sxKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, .35f), Keyframe.ofFloat(.58f, 1.08f), Keyframe.ofFloat(1f, .42f)};
                rotKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, -22f), Keyframe.ofFloat(.58f, -3f), Keyframe.ofFloat(1f, 20f)};
                aKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.2f, 1f),
                        Keyframe.ofFloat(.85f, 1f), Keyframe.ofFloat(1f, 0f)};
                break;
            case "drive":
                xKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, dir * -width * .95f),
                        Keyframe.ofFloat(.48f, dir * -width * .04f),
                        Keyframe.ofFloat(.64f, dir * width * .12f),
                        Keyframe.ofFloat(1f, dir * width * .95f)};
                yKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, height * .14f), Keyframe.ofFloat(.48f, 0f),
                        Keyframe.ofFloat(1f, height * .08f)};
                sxKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, .45f), Keyframe.ofFloat(.48f, 1.05f), Keyframe.ofFloat(1f, .52f)};
                rotKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, -5f), Keyframe.ofFloat(.48f, 0f), Keyframe.ofFloat(1f, 4f)};
                aKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.18f, 1f),
                        Keyframe.ofFloat(.85f, 1f), Keyframe.ofFloat(1f, 0f)};
                break;
            case "run":
            default:
                xKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, dir * -width * .9f),
                        Keyframe.ofFloat(.24f, dir * -width * .42f),
                        Keyframe.ofFloat(.4f, dir * -width * .14f),
                        Keyframe.ofFloat(.49f, 0f),
                        Keyframe.ofFloat(.58f, dir * width * .14f),
                        Keyframe.ofFloat(.78f, dir * width * .5f),
                        Keyframe.ofFloat(1f, dir * width * .92f)};
                yKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, height * .16f), Keyframe.ofFloat(.24f, height * .02f),
                        Keyframe.ofFloat(.49f, -height * .03f), Keyframe.ofFloat(.58f, height * .02f),
                        Keyframe.ofFloat(1f, -height * .05f)};
                sxKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, .42f), Keyframe.ofFloat(.49f, 1.06f), Keyframe.ofFloat(1f, .48f)};
                rotKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, -7f), Keyframe.ofFloat(.4f, 2f),
                        Keyframe.ofFloat(.58f, 2f), Keyframe.ofFloat(1f, 5f)};
                aKeys = new Keyframe[]{
                        Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.12f, 1f),
                        Keyframe.ofFloat(.85f, 1f), Keyframe.ofFloat(1f, 0f)};
                break;
        }
        ObjectAnimator transform = ObjectAnimator.ofPropertyValuesHolder(image,
                PropertyValuesHolder.ofKeyframe(View.TRANSLATION_X, xKeys),
                PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, yKeys),
                PropertyValuesHolder.ofKeyframe(View.SCALE_X, sxKeys),
                PropertyValuesHolder.ofKeyframe(View.SCALE_Y, sxKeys),
                PropertyValuesHolder.ofKeyframe(View.ROTATION, rotKeys),
                PropertyValuesHolder.ofKeyframe(View.ALPHA, aKeys));
        transform.setDuration(duration);
        transform.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.play(transform);
        return set;
    }

    private static boolean isCrossingFamily(String family) {
        return "run".equals(family) || "drive".equals(family) || "train".equals(family)
                || "tractor".equals(family) || "bird".equals(family);
    }

    private AnimatorSet giftAmbient(String family, View image) {
        AnimatorSet set = new AnimatorSet();
        if (isCrossingFamily(family)) {
            return set; // crossing path is the full motion
        }
        ObjectAnimator first;
        ObjectAnimator second;
        switch (family) {
            case "orbit":
                first = repeat(image, View.ROTATION, 0f, 360f, 8000);
                second = repeat(image, View.SCALE_X, .98f, 1.04f, 1800);
                break;
            case "heart":
                first = repeat(image, View.SCALE_X, 1f, 1.06f, 750);
                second = repeat(image, View.SCALE_Y, 1f, 1.06f, 750);
                break;
            case "swing": case "music":
                first = repeat(image, View.ROTATION, -3f, 3f, 1000);
                second = repeat(image, View.TRANSLATION_Y, 0f, -dp(7), 1100);
                break;
            case "stomp":
                first = repeat(image, View.TRANSLATION_Y, 0f, -dp(9), 520);
                second = repeat(image, View.SCALE_Y, .985f, 1.015f, 520);
                break;
            case "dragon": case "phoenix":
                first = repeat(image, View.TRANSLATION_Y, 0f, -dp(12), 1000);
                second = repeat(image, View.ROTATION, -2f, 2f, 1200);
                break;
            default:
                first = repeat(image, View.TRANSLATION_Y, 0f, -dp(12), 1200);
                second = repeat(image, View.ROTATION, -.5f, .5f, 1200);
                break;
        }
        first.setStartDelay(1050);
        second.setStartDelay(1050);
        set.playTogether(first, second);
        return set;
    }

    private ObjectAnimator repeat(View target, android.util.Property<View, Float> property,
                                  float from, float to, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, property, from, to);
        animator.setDuration(duration);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }

    private void addParticles(int accent, int count, int width, int height) {
        for (int index = 0; index < count; index++) {
            View particle = new View(getContext());
            int size = dp(index % 3 == 0 ? 6 : 3);
            particle.setBackground(circle(withAlpha(accent, 230), Color.TRANSPARENT, 0));
            LayoutParams params = new LayoutParams(size, size);
            params.gravity = Gravity.BOTTOM | Gravity.START;
            params.leftMargin = Math.round(width * (.08f + ((index * 37) % 84) / 100f));
            params.bottomMargin = Math.round(height * .18f);
            addView(particle, params);
            ObjectAnimator rise = ObjectAnimator.ofPropertyValuesHolder(particle,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -height * .42f),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, .2f, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, .2f, 1f, 0f));
            rise.setDuration(1700 + (index % 5) * 120L);
            rise.setStartDelay((index % 7) * 90L);
            rise.setRepeatCount(ObjectAnimator.INFINITE);
            looseAnimators.add(rise);
            rise.start();
        }
    }

    private void loadAssetOrRemote(ImageView target, String assetPath,
                                   @Nullable String remoteUrl, int width, int height) {
        Bitmap bitmap = decodeAsset(assetPath, width, height);
        if (bitmap != null) {
            target.setImageBitmap(bitmap);
        } else if (remoteUrl != null && !remoteUrl.isEmpty()) {
            Glide.with(this).load(AssetCatalog.absoluteUrl(remoteUrl)).into(target);
        } else {
            target.setImageResource(ImagePlaceholder.cover());
        }
    }

    @Nullable
    private Bitmap decodeAsset(String path, int width, int height) {
        String key = path + ":" + width + "x" + height;
        Bitmap cached = BITMAPS.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContext().getAssets().open(path)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= width
                    && bounds.outHeight / (sample * 2) >= height) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded;
            try (InputStream input = getContext().getAssets().open(path)) {
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
            if (decoded != null) BITMAPS.put(key, decoded);
            return decoded;
        } catch (IOException ignored) {
            return null;
        }
    }

    private View glow(int color) {
        View view = new View(getContext());
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(250));
        drawable.setColors(new int[]{withAlpha(color, 105), withAlpha(color, 35), Color.TRANSPARENT});
        view.setBackground(drawable);
        view.setAlpha(0f);
        return view;
    }

    private TextView label(String title, String detail) {
        TextView view = new TextView(getContext());
        view.setText(detail.isEmpty() ? title : title + "\n" + detail);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(dp(24), dp(10), dp(24), dp(10));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, 0xcc080b16, Color.TRANSPARENT});
        background.setCornerRadius(dp(40));
        view.setBackground(background);
        return view;
    }

    private void scheduleFinish(int token, long duration) {
        removeCallbacks(scheduledFinish);
        postDelayed(scheduledFinish, Math.max(1800, duration));
    }

    private void finishActive() {
        removeCallbacks(scheduledFinish);
        final int token = generation;
        animate().cancel();
        animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(280)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (token != generation) return;
                        Runnable completion = activeCompletion;
                        activeCompletion = null;
                        generation++;
                        clearContent();
                        if (completion != null) completion.run();
                    }
                }).start();
    }

    private void clearContent() {
        animate().setListener(null);
        animate().cancel();
        if (activeAnimators != null) {
            activeAnimators.cancel();
            activeAnimators = null;
        }
        for (Animator animator : looseAnimators) animator.cancel();
        looseAnimators.clear();
        if (activeSprite != null) {
            activeSprite.stop();
            activeSprite = null;
        }
        releaseGiftPlayer();
        removeEffectViewsOnly();
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        if (!hasSlotBubbles()) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    private void releaseGiftPlayer() {
        if (giftSvgaView != null) {
            try {
                giftSvgaView.stopAnimation(true);
            } catch (Exception ignored) {
            }
            giftSvgaView = null;
        }
        if (giftAnimView != null && giftAnimView != entryAnimView) {
            try {
                giftAnimView.stopPlay();
            } catch (Exception ignored) {
            }
            giftAnimView = null;
        } else {
            giftAnimView = null;
        }
        if (entryAnimView != null) {
            try {
                entryAnimView.stopPlay();
            } catch (Exception ignored) {
            }
            entryAnimView = null;
        }
        if (giftPlayer == null) return;
        try {
            giftPlayer.setPlayWhenReady(false);
            giftPlayer.release();
        } catch (Exception ignored) {
        }
        giftPlayer = null;
    }

    @Nullable
    private Bitmap decodeAssetFull(String path) {
        String key = path + ":full";
        Bitmap cached = BITMAPS.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded;
            try (InputStream input = getContext().getAssets().open(path)) {
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
            if (decoded != null) BITMAPS.put(key, decoded);
            return decoded;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int indexOfVariant(@Nullable String variant) {
        if (variant == null) return -1;
        for (int i = 0; i < ENTRY_VARIANTS.length; i++) {
            if (ENTRY_VARIANTS[i].equalsIgnoreCase(variant)) return i;
        }
        return -1;
    }

    private static int tierAccent(String tier) {
        switch (tier) {
            case "premium": return 0xffbb7cff;
            case "legendary": return 0xffffb82e;
            case "mythic": return 0xffff5578;
            default: return 0xff72d8ff;
        }
    }

    private GradientDrawable circle(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Map<String, GiftSpec> buildGiftCatalog() {
        Map<String, GiftSpec> result = new HashMap<>();
        add(result, "ball", "كرة أسطورية", "bounce", "basic");
        add(result, "car", "سيارة خارقة", "drive", "legendary");
        add(result, "castle", "قلعة الجليد", "rise", "mythic");
        add(result, "champagne", "احتفال فاخر", "burst", "premium");
        add(result, "crown", "التاج الملكي", "crown", "legendary");
        add(result, "diamond", "ألماسة", "crystal", "premium");
        add(result, "dragon", "التنين", "dragon", "mythic");
        add(result, "fireworks", "ألعاب نارية", "burst", "premium");
        add(result, "galaxy", "المجرة", "orbit", "mythic");
        add(result, "guitar", "غيتار النجوم", "swing", "premium");
        add(result, "heart", "قلب كريستالي", "heart", "premium");
        add(result, "icecream", "آيس كريم", "bounce", "basic");
        add(result, "lion", "الأسد الملكي", "run", "legendary");
        add(result, "lucky-box", "صندوق الحظ", "box", "premium");
        add(result, "meteor", "النيزك", "meteor", "legendary");
        add(result, "plane", "طائرة خاصة", "fly", "legendary");
        add(result, "ring", "خاتم الحب", "crystal", "premium");
        add(result, "rocket", "الصاروخ", "rocket", "legendary");
        add(result, "rose", "وردة الحب", "heart", "basic");
        add(result, "teddy", "الدب اللطيف", "bounce", "basic");
        add(result, "unicorn", "اليونيكورن", "swing", "legendary");
        add(result, "yacht", "اليخت الملكي", "sail", "mythic");
        add(result, "microphone", "ميكروفون النجوم", "swing", "premium");
        add(result, "phoenix", "العنقاء", "phoenix", "mythic");
        add(result, "royal-tiger", "النمر الملكي", "run", "mythic");
        add(result, "luxury-watch", "ساعة فاخرة", "crystal", "legendary");
        add(result, "treasure-chest", "كنز الجواهر", "box", "mythic");
        add(result, "golden-throne", "العرش الذهبي", "crown", "mythic");
        add(result, "dire-wolf", "الذئب الجليدي", "run", "mythic");
        add(result, "royal-train", "القطار الملكي", "train", "legendary");
        add(result, "golden-tractor", "التراكتور الذهبي", "tractor", "legendary");
        add(result, "crystal-piano", "البيانو الكريستالي", "music", "mythic");
        add(result, "superbike", "الدراجة الخارقة", "drive", "legendary");
        add(result, "royal-drums", "طبول الملوك", "music", "legendary");
        add(result, "planet-earth", "كوكب الأرض", "orbit", "mythic");
        add(result, "planet-saturn", "كوكب زحل", "orbit", "mythic");
        add(result, "royal-eagle", "النسر الملكي", "bird", "mythic");
        add(result, "royal-elephant", "الفيل الملكي", "stomp", "mythic");
        add(result, "golden-falcon", "الصقر الذهبي", "bird", "legendary");
        add(result, "magic-lamp", "المصباح السحري", "orbit", "mythic");
        add(result, "lucky-coin-100", "حظ برونزي", "burst", "basic");
        add(result, "lucky-coin-500", "حظ فضي", "burst", "premium");
        add(result, "lucky-coin-1000", "حظ ذهبي", "burst", "legendary");
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, SpriteSpec> buildSpriteCatalog() {
        Map<String, SpriteSpec> map = new HashMap<>();
        map.put("lion", new SpriteSpec("gift-lion.png", 9, 950));
        map.put("royal-tiger", new SpriteSpec("gift-royal-tiger.png", 9, 900));
        map.put("dire-wolf", new SpriteSpec("gift-dire-wolf.png", 9, 820));
        map.put("superbike", new SpriteSpec("gift-superbike.png", 9, 660));
        return Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, GiftSpec> map, String id, String name,
                            String family, String tier) {
        map.put(id, new GiftSpec(id, name, family, tier));
    }

    private static final class GiftSpec {
        final String id;
        final String name;
        final String family;
        final String tier;

        GiftSpec(String id, String name, String family, String tier) {
            this.id = id;
            this.name = name;
            this.family = family;
            this.tier = tier;
        }
    }

    private static final class SpriteSpec {
        final String file;
        final int frames;
        final long cycleMs;

        SpriteSpec(String file, int frames, long cycleMs) {
            this.file = file;
            this.frames = frames;
            this.cycleMs = cycleMs;
        }
    }

    /** Horizontal strip sprite player (9 frames × square cells). */
    private static final class SpriteSheetView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect();
        private final Rect dst = new Rect();
        @Nullable private Bitmap sheet;
        private int frames = 1;
        private int frameW;
        private int frameH;
        private int frameIndex;
        @Nullable private ValueAnimator animator;

        SpriteSheetView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void play(Bitmap sheet, int frames, long cycleMs) {
            stop();
            this.sheet = sheet;
            this.frames = Math.max(1, frames);
            this.frameW = Math.max(1, sheet.getWidth() / this.frames);
            this.frameH = sheet.getHeight();
            this.frameIndex = 0;
            animator = ValueAnimator.ofInt(0, this.frames - 1);
            animator.setDuration(Math.max(200L, cycleMs));
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                int next = (int) a.getAnimatedValue();
                if (next != frameIndex) {
                    frameIndex = next;
                    invalidate();
                }
            });
            animator.start();
            invalidate();
        }

        void stop() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (sheet == null || sheet.isRecycled()) return;
            int left = Math.min(frameIndex * frameW, sheet.getWidth() - frameW);
            src.set(left, 0, left + frameW, frameH);
            dst.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(sheet, src, dst, paint);
        }

        @Override protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }
    }
}
