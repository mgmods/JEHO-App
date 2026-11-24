package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AdvertisementDtos;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.google.gson.JsonObject;
import com.squareup.picasso.Picasso;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RoomAdvertisementTickerView extends FrameLayout implements RealtimeClient.UserListener {
    private final ImageView avatar;
    private final TextView name;
    private final TextView text;
    private final FrameLayout textViewport;
    private final AtomicBoolean attached = new AtomicBoolean(false);
    private volatile AdvertisementDtos.Ticker config;
    private volatile String currentAdId = "";

    public RoomAdvertisementTickerView(Context context) { this(context, null); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
        setVisibility(GONE);

        avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        name = new TextView(context);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textViewport = new FrameLayout(context);
        textViewport.setClipChildren(true);
        textViewport.setClipToPadding(true);
        text = new TextView(context);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        textViewport.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(avatar, new FrameLayout.LayoutParams(34, 34));
        addView(name, new FrameLayout.LayoutParams(90, 34));
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34);
        addView(textViewport, vp);
        setContentDescription("شريط الإعلانات");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (attached.compareAndSet(false, true)) {
            RealtimeClient.getInstance().addUserListener(this);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (attached.compareAndSet(true, false)) {
            RealtimeClient.getInstance().removeUserListener(this);
        }
        super.onDetachedFromWindow();
    }

    @Override public void onUserEvent(String event, JsonObject payload) {
        if ("room:advertisement:config".equals(event)) {
            try { AdvertisementDtos.Ticker next = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.Ticker.class); config = next; if (next != null && !next.enabled) post(() -> setVisibility(GONE)); } catch (Exception ignored) {}
            return;
        }
        if (!"room:advertisement".equals(event) || payload == null) return;
        if (!payload.has("advertisement") || !payload.get("advertisement").isJsonObject()) return;
        AdvertisementDtos.RealtimeEvent ev = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.RealtimeEvent.class);
        if (ev == null || ev.advertisement == null || !"start".equals(ev.type)) return;
        post(() -> showAdvertisement(ev.advertisement, ev.config));
    }

    @Override public void onConnected() {}
    @Override public void onDisconnected() {}

    private void showAdvertisement(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg) {
        if (ad.id == null || ad.id.equals(currentAdId)) return;
        currentAdId = ad.id;
        config = cfg;
        bindConfig(cfg);
        bindAd(ad);
        animateTextIn(ad, cfg);
    }

    private void bindConfig(AdvertisementDtos.Ticker cfg) {
        if (cfg == null) return;
        int h = dp(Math.max(36, cfg.heightDp > 0 ? cfg.heightDp : 48));
        getLayoutParams();
        setMinimumHeight(h);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parseColorWithOpacity(cfg.backgroundOpacity, cfg.background));
        bg.setCornerRadius(dp(Math.max(0, cfg.radiusDp)));
        setBackground(bg);

        avatar.getLayoutParams().width = dp(Math.max(20, cfg.avatarDp));
        avatar.getLayoutParams().height = dp(Math.max(20, cfg.avatarDp));
        avatar.setLayoutParams(avatar.getLayoutParams());
        name.setTextSize(Math.max(8, cfg.nameSp > 0 ? cfg.nameSp : 12));
        name.setTextColor(safeColor(cfg.nameColor, Color.WHITE));
        text.setTextSize(Math.max(9, cfg.textSp > 0 ? cfg.textSp : 13));
        text.setTextColor(safeColor(cfg.textColor, Color.WHITE));
        int spacing = dp(Math.max(4, cfg.spacingDp > 0 ? cfg.spacingDp : 8));
        FrameLayout.LayoutParams av = (FrameLayout.LayoutParams) avatar.getLayoutParams();
        av.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        av.leftMargin = spacing; av.rightMargin = 0; avatar.setLayoutParams(av);
        FrameLayout.LayoutParams nm = (FrameLayout.LayoutParams) name.getLayoutParams();
        nm.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        nm.leftMargin = spacing + dp(cfg.avatarDp) + spacing; name.setLayoutParams(nm);
        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) textViewport.getLayoutParams();
        vp.gravity = Gravity.CENTER_VERTICAL;
        vp.leftMargin = spacing + dp(cfg.avatarDp) + spacing + dp(90);
        vp.rightMargin = spacing; textViewport.setLayoutParams(vp);
    }

    private void bindAd(AdvertisementDtos.Advertisement ad) {
        name.setText(ad.senderName == null ? "عضو" : ad.senderName);
        text.setText(ad.text == null ? "" : ad.text);
        if (ad.senderImageUrl != null && !ad.senderImageUrl.trim().isEmpty()) {
            Picasso.get().load(ad.senderImageUrl).fit().centerCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_asset_profile);
        }
        if (config != null && "square".equalsIgnoreCase(config.avatarShape)) {
            avatar.setBackground(null);
        }
        setVisibility(VISIBLE);
    }

    private void animateTextIn(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg) {
        text.animate().cancel();
        text.clearAnimation();
        text.setTranslationX(0f);
        if (cfg != null && cfg.reducedMotionStatic && isReduceMotionEnabled()) {
            text.setText(text.getText());
            return;
        }
        text.post(() -> {
            int viewport = Math.max(1, textViewport.getWidth());
            int width = Math.max(1, text.getWidth());
            int distance = viewport + width;
            boolean rtl = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction);
            float start = rtl ? distance : -distance;
            float end = rtl ? -distance : distance;
            text.setTranslationX(start);
            float speed = rtl ? safe(cfg != null ? cfg.entrySpeedPxPerSec : 120, 120) : safe(cfg != null ? cfg.exitSpeedPxPerSec : 120, 120);
            long duration = Math.max(500L, Math.min(60000L, (long) ((distance / dp(speed)) * 1000L)));
            ObjectAnimator in = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, start, 0f);
            in.setDuration(duration);
            in.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    animateTextAcross(ad, cfg, viewport);
                }
            });
            in.start();
        });
    }

    private void animateTextAcross(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, int viewport) {
        if (!ad.id.equals(currentAdId)) return;
        int width = Math.max(1, text.getWidth());
        boolean rtl = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction);
        float end = rtl ? -(viewport + width) : (viewport + width);
        float distance = Math.abs(end);
        float speed = safe(cfg != null ? cfg.exitSpeedPxPerSec : 120, 120);
        long duration = Math.max(500L, Math.min(60000L, (long) ((distance / dp(speed)) * 1000L)));
        ObjectAnimator out = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, 0f, end);
        out.setDuration(duration);
        out.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (ad.id.equals(currentAdId)) {
                    postDelayed(() -> { if (ad.id.equals(currentAdId)) setVisibility(INVISIBLE); }, Math.max(0, cfg != null ? cfg.gapMs : 1800));
                }
            }
        });
        out.start();
    }

    private boolean isReduceMotionEnabled() {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && Build.VERSION.SDK_INT >= 19;
    }

    private static float safe(int value, int fallback) { return value > 0 ? value : fallback; }
    private static int dp(float px) { return Math.max(1, Math.round(px * 1f)); }
    private int dp(int value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }
    private static int safeColor(String value, int fallback) { try { return Color.parseColor(value); } catch (Exception e) { return fallback; } }
    private static int parseColorWithOpacity(int opacity, String value) {
        try {
            int color = Color.parseColor(value);
            int a = Math.max(0, Math.min(255, Math.round(255f * opacity / 100f)));
            return (color & 0x00FFFFFF) | (a << 24);
        } catch (Exception e) { return 0xEE21142D; }
    }
}