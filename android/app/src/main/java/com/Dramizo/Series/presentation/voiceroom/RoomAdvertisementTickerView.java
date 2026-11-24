package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
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
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.JsonObject;
import com.bumptech.glide.Glide;

import java.util.concurrent.atomic.AtomicBoolean;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class RoomAdvertisementTickerView extends FrameLayout implements RealtimeClient.UserListener {
    public static volatile boolean SEND_BUTTON_ENABLED = true;
    private final ImageView backgroundImage;
    private final ImageView avatar;
    private final TextView name;
    private final TextView text;
    private final FrameLayout textViewport;
    private final AtomicBoolean attached = new AtomicBoolean(false);
    private volatile AdvertisementDtos.Ticker config;
    private volatile String currentAdId = "";
    private ObjectAnimator activeAnimator;
    private volatile boolean needsResume = false;
    private boolean movingForPlacement = false;
    private long currentDisplayBudgetMs = 9000L;
    private long animationGeneration = 0L;
    private volatile long serverClockOffsetMs = 0L;
    private volatile boolean serverClockKnown = false;

    public RoomAdvertisementTickerView(Context context) { this(context, null); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
        setVisibility(GONE);

        backgroundImage = new ImageView(context);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backgroundImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        addView(avatar, new FrameLayout.LayoutParams(dp(34), dp(34)));
        addView(name, new FrameLayout.LayoutParams(dp(90), dp(34)));
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        addView(textViewport, vp);
        setContentDescription("شريط الإعلانات");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (attached.compareAndSet(false, true)) {
            RealtimeClient.getInstance().addUserListener(this);
            refreshCurrent();
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (attached.compareAndSet(true, false)) {
            RealtimeClient.getInstance().removeUserListener(this);
            cancelActiveAnimator();
            if (!movingForPlacement) needsResume = true;
        }
        super.onDetachedFromWindow();
    }

    @Override public void onUserEvent(String event, JsonObject payload) {
        if ("room:advertisement:config".equals(event)) {
            try {
                AdvertisementDtos.Ticker next = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.Ticker.class);
                config = next;
                if (next != null) SEND_BUTTON_ENABLED = next.enabled && next.sendButtonEnabled;
                if (next != null && !next.enabled) {
                    currentAdId = "";
                    cancelActiveAnimator();
                    post(() -> setVisibility(GONE));
                } else if (next != null) {
                    refreshCurrent();
                }
            } catch (Exception ignored) {}
            return;
        }
        if (!"room:advertisement".equals(event) || payload == null) return;
        if (!payload.has("advertisement") || !payload.get("advertisement").isJsonObject()) return;
        AdvertisementDtos.RealtimeEvent ev = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.RealtimeEvent.class);
        if (ev == null || ev.advertisement == null || !"start".equals(ev.type)) return;
        long remaining = ev.advertisement.displayDurationMs;
        if (serverClockKnown && ev.at != null) {
            long serverNow = System.currentTimeMillis() + serverClockOffsetMs;
            long startedAt = parseIsoUtc(ev.at, serverNow);
            remaining = ev.advertisement.displayDurationMs - Math.max(0L, serverNow - startedAt);
        }
        final long remainingMs = remaining;
        if (remainingMs > 0L) post(() -> showAdvertisement(ev.advertisement, ev.config, remainingMs));
    }

    @Override public void onConnected() { refreshCurrent(); }
    @Override public void onDisconnected() {}

    private void refreshCurrent() {
        if (!attached.get()) return;
        ContainerProvider.from(getContext()).getRoomApi().advertisementCurrent().enqueue(new Callback<ApiResponse<AdvertisementDtos.Current>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Current>> call, Response<ApiResponse<AdvertisementDtos.Current>> response) {
                AdvertisementDtos.Current current = response.body() != null ? response.body().data : null;
                if (current != null && current.config != null) {
                    SEND_BUTTON_ENABLED = current.config.enabled && current.config.sendButtonEnabled;
                    config = current.config;
                }
                if (current != null && current.advertisement != null && current.config != null && current.config.enabled) {
                    long remaining = Math.max(0L, current.advertisement.displayDurationMs);
                    try {
                        long serverNow = parseIsoUtc(current.serverNow, System.currentTimeMillis());
                        serverClockOffsetMs = serverNow - System.currentTimeMillis();
                        serverClockKnown = true;
                        long startedAt = parseIsoUtc(current.advertisement.startedAt, serverNow);
                        remaining = current.advertisement.displayDurationMs - Math.max(0L, serverNow - startedAt);
                    } catch (Exception ignored) {}
                    final long remainingMs = remaining;
                    if (remainingMs > 0L) post(() -> showAdvertisement(current.advertisement, current.config, remainingMs));
                    else post(() -> { currentAdId = ""; setVisibility(GONE); });
                } else if (current != null && current.config != null && !current.config.enabled) {
                    post(() -> setVisibility(GONE));
                }
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Current>> call, Throwable t) {}
        });
    }

    private void cancelActiveAnimator() {
        ObjectAnimator animator = activeAnimator;
        activeAnimator = null;
        animationGeneration++;
        if (animator != null) animator.cancel();
        text.animate().cancel();
    }

    private void showAdvertisement(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg) {
        showAdvertisement(ad, cfg, ad == null ? 0L : ad.displayDurationMs);
    }

    private void showAdvertisement(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, long remainingMs) {
        if (ad == null || ad.id == null) return;
        boolean sameAd = ad.id.equals(currentAdId);
        if (sameAd && !needsResume) {
            config = cfg;
            bindConfig(cfg);
            return;
        }
        cancelActiveAnimator();
        currentAdId = ad.id;
        needsResume = false;
        currentDisplayBudgetMs = Math.max(250L, remainingMs);
        config = cfg;
        bindConfig(cfg);
        bindAd(ad);
        animateTextIn(ad, cfg, animationGeneration);
    }

    private void bindConfig(AdvertisementDtos.Ticker cfg) {
        if (cfg == null) return;
        int h = dp(Math.max(36, cfg.heightDp > 0 ? cfg.heightDp : 48));
        ViewGroup.LayoutParams own = getLayoutParams();
        if (own != null && own.height != h) { own.height = h; setLayoutParams(own); }
        setMinimumHeight(h);
        setBackground(buildBackground(cfg.background, cfg.backgroundOpacity, cfg.radiusDp));
        backgroundImage.setAlpha(Math.max(0, Math.min(100, cfg.backgroundOpacity)) / 100f);
        if (cfg.backgroundImage != null && (cfg.backgroundImage.startsWith("https://") || cfg.backgroundImage.startsWith("http://"))) {
            Glide.with(this).load(cfg.backgroundImage).centerCrop().into(backgroundImage);
            backgroundImage.setVisibility(VISIBLE);
        } else {
            Glide.with(this).clear(backgroundImage);
            backgroundImage.setVisibility(GONE);
        }
        applySafePlacement(cfg.position, cfg.customOffsetDp);

        avatar.getLayoutParams().width = dp(Math.max(20, cfg.avatarDp));
        avatar.getLayoutParams().height = dp(Math.max(20, cfg.avatarDp));
        avatar.setLayoutParams(avatar.getLayoutParams());
        name.setTextSize(Math.max(8, cfg.nameSp > 0 ? cfg.nameSp : 12));
        name.setTextColor(safeColor(cfg.nameColor, Color.WHITE));
        text.setTextSize(Math.max(9, cfg.textSp > 0 ? cfg.textSp : 13));
        text.setTextColor(safeColor(cfg.textColor, Color.WHITE));
        Typeface face = "serif".equalsIgnoreCase(cfg.fontFamily) ? Typeface.SERIF
                : "mono".equalsIgnoreCase(cfg.fontFamily) ? Typeface.MONOSPACE
                : "sans".equalsIgnoreCase(cfg.fontFamily) ? Typeface.SANS_SERIF : Typeface.DEFAULT;
        text.setTypeface(face);
        int spacing = dp(Math.max(0, cfg.spacingDp));
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
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(this).load(ad.senderImageUrl).centerCrop()
                    .placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar);
            if (config != null && "circle".equalsIgnoreCase(config.avatarShape)) request = request.circleCrop();
            request.into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_default_avatar);
        }
        setVisibility(VISIBLE);
    }

    private void animateTextIn(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, long generation) {
        text.clearAnimation();
        text.setAlpha(1f);
        text.setTranslationX(0f);
        if (cfg != null && cfg.reducedMotionStatic && isReduceMotionEnabled()) {
            text.setText(text.getText());
            long visibleFor = Math.max(1000L, currentDisplayBudgetMs) + Math.max(0, cfg != null ? cfg.gapMs : 1800);
            postDelayed(() -> { if (generation == animationGeneration && ad.id.equals(currentAdId)) setVisibility(INVISIBLE); }, visibleFor);
            return;
        }
        text.post(() -> {
            if (generation != animationGeneration || !ad.id.equals(currentAdId)) return;
            int viewport = Math.max(1, textViewport.getWidth());
            int width = Math.max(1, text.getWidth());
            int distance = viewport + width;
            boolean rtl = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction);
            float start = rtl ? distance : -distance;
            float end = rtl ? -distance : distance;
            text.setTranslationX(start);
            float density = getResources().getDisplayMetrics().density;
            float entrySpeed = safe(cfg != null ? cfg.entrySpeedPxPerSec : 120, 120) * density;
            float exitSpeed = safe(cfg != null ? cfg.exitSpeedPxPerSec : 120, 120) * density;
            long requestedEntry = Math.max(250L, (long) ((distance / Math.max(1f, entrySpeed)) * 1000L));
            long requestedExit = Math.max(250L, (long) ((distance / Math.max(1f, exitSpeed)) * 1000L));
            long budget = Math.max(1000L, currentDisplayBudgetMs > 0 ? currentDisplayBudgetMs : (cfg != null ? cfg.displayDurationMs : 9000));
            long requestedTotal = requestedEntry + requestedExit;
            long entryDuration = requestedTotal > budget ? Math.max(250L, budget * requestedEntry / requestedTotal) : requestedEntry;
            long exitDuration = Math.max(250L, budget - entryDuration);
            if (entryDuration + exitDuration > budget) entryDuration = Math.max(250L, budget - exitDuration);
            boolean fade = cfg != null && "fade".equalsIgnoreCase(cfg.transitionType);
            text.setAlpha(fade ? 0f : 1f);
            ObjectAnimator in = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, start, 0f);
            activeAnimator = in;
            in.setDuration(entryDuration);
            if (fade) text.animate().alpha(1f).setDuration(Math.min(450L, entryDuration)).start();
            in.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (activeAnimator == in) activeAnimator = null;
                    if (generation == animationGeneration && ad.id.equals(currentAdId)) animateTextAcross(ad, cfg, viewport, exitDuration, generation);
                }
            });
            in.start();
        });
    }

    private void animateTextAcross(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, int viewport, long exitDuration, long generation) {
        if (generation != animationGeneration || !ad.id.equals(currentAdId)) return;
        int width = Math.max(1, text.getWidth());
        boolean rtl = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction);
        float end = rtl ? -(viewport + width) : (viewport + width);
        ObjectAnimator out = ObjectAnimator.ofFloat(text, View.TRANSLATION_X, 0f, end);
        activeAnimator = out;
        out.setDuration(Math.max(250L, exitDuration));
        if (cfg != null && "fade".equalsIgnoreCase(cfg.transitionType)) text.animate().alpha(0f).setDuration(Math.min(450L, exitDuration)).start();
        out.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (activeAnimator == out) activeAnimator = null;
                if (generation == animationGeneration && ad.id.equals(currentAdId)) {
                    setVisibility(INVISIBLE);
                }
            }
        });
        out.start();
    }

    private void applySafePlacement(String position, int customOffsetDp) {
        setTranslationY("custom-safe".equalsIgnoreCase(position) ? dp(Math.max(-100, Math.min(100, customOffsetDp))) : 0f);
        if (!(getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) getParent();
        int current = parent.indexOfChild(this);
        if (current < 0) return;
        int target = 0;
        if ("safe-bottom".equalsIgnoreCase(position) || "custom-safe".equalsIgnoreCase(position)) {
            target = parent.getChildCount() - 1;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child.getId() == R.id.rowOfficialNews) { target = i + 1; break; }
            }
        }
        if (current != target) {
            movingForPlacement = true;
            parent.removeView(this);
            parent.addView(this, Math.max(0, Math.min(target, parent.getChildCount())));
            movingForPlacement = false;
        }
    }

    private GradientDrawable buildBackground(String value, int opacity, int radiusDp) {
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?").matcher(value == null ? "" : value);
        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
        while (matcher.find()) {
            try {
                int color = Color.parseColor(matcher.group());
                colors.add(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            } catch (Exception ignored) {}
        }
        java.util.regex.Matcher rgba = java.util.regex.Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})").matcher(value == null ? "" : value);
        while (rgba.find()) {
            try {
                int red = Math.max(0, Math.min(255, Integer.parseInt(rgba.group(1))));
                int green = Math.max(0, Math.min(255, Integer.parseInt(rgba.group(2))));
                int blue = Math.max(0, Math.min(255, Integer.parseInt(rgba.group(3))));
                colors.add(Color.argb(alpha, red, green, blue));
            } catch (Exception ignored) {}
        }
        GradientDrawable bg;
        if (value != null && value.toLowerCase().contains("linear-gradient") && colors.size() >= 2) {
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{colors.get(0), colors.get(colors.size() - 1)});
        } else {
            int color = safeColor(value, 0xFF21142D);
            bg = new GradientDrawable();
            bg.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        }
        bg.setCornerRadius(dp(Math.max(0, radiusDp)));
        return bg;
    }

    private static long parseIsoUtc(String value, long fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            java.util.Date parsed = format.parse(value);
            return parsed == null ? fallback : parsed.getTime();
        } catch (Exception ignored) { return fallback; }
    }

    private boolean isReduceMotionEnabled() {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (Build.VERSION.SDK_INT < 17) return false;
        try { return Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f; }
        catch (Exception ignored) { return false; }
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