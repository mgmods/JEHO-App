package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import java.util.ArrayList;
import android.view.animation.LinearInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

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
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.io.File;
import com.Dramizo.Series.util.ApiOrigin;

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
    private AnimatorSet activeAnimator;
    private volatile boolean needsResume = false;
    private boolean movingForPlacement = false;
    private long currentDisplayBudgetMs = 9000L;
    private long animationGeneration = 0L;
    private volatile long serverClockOffsetMs = 0L;
    private volatile boolean serverClockKnown = false;
    private CustomTarget<File> customFontTarget;
    private volatile String loadedCustomFontUrl = "";
    private volatile Typeface customTypeface;

    public RoomAdvertisementTickerView(Context context) { this(context, null); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public RoomAdvertisementTickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setVisibility(GONE);

        backgroundImage = new ImageView(context);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backgroundImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        avatar = new ImageView(context);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        name = new TextView(context);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        name.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textViewport = new FrameLayout(context);
        textViewport.setClipChildren(true);
        textViewport.setClipToPadding(true);
        text = new TextView(context);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
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
            if (customFontTarget != null) { Glide.with(this).clear(customFontTarget); customFontTarget = null; }
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
                    post(this::hideAdvertisement);
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
        if (ev.serverNow != null) {
            long eventServerNow = parseIsoUtc(ev.serverNow, System.currentTimeMillis());
            serverClockOffsetMs = eventServerNow - System.currentTimeMillis();
            serverClockKnown = true;
        }
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
                    else post(RoomAdvertisementTickerView.this::hideAdvertisement);
                } else {
                    // No globally active advertisement: remove the entire row, not just its contents.
                    post(RoomAdvertisementTickerView.this::hideAdvertisement);
                }
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Current>> call, Throwable t) {}
        });
    }

    private void cancelActiveAnimator() {
        AnimatorSet animator = activeAnimator;
        activeAnimator = null;
        animationGeneration++;
        if (animator != null) animator.cancel();
        text.animate().cancel();
    }

    private void hideAdvertisement() {
        cancelActiveAnimator();
        currentAdId = "";
        needsResume = false;
        currentDisplayBudgetMs = 0L;
        text.setText("");
        text.setTranslationX(0f);
        text.setAlpha(1f);
        Glide.with(this).clear(backgroundImage);
        backgroundImage.setVisibility(GONE);
        setVisibility(GONE);
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
        applySafePlacement(cfg.position, cfg.customOffsetDp, cfg.positionXPercent, cfg.positionYPercent);

        avatar.getLayoutParams().width = dp(Math.max(20, cfg.avatarDp));
        avatar.getLayoutParams().height = dp(Math.max(20, cfg.avatarDp));
        avatar.setLayoutParams(avatar.getLayoutParams());
        name.setTextSize(Math.max(8, cfg.nameSp > 0 ? cfg.nameSp : 12));
        name.setTextColor(safeColor(cfg.nameColor, Color.WHITE));
        text.setTextSize(Math.max(9, cfg.textSp > 0 ? cfg.textSp : 13));
        text.setTextColor(safeColor(cfg.textColor, Color.WHITE));
        applyConfiguredTypeface(cfg);
        applyConfiguredGradients(cfg);
        int spacing = dp(Math.max(0, cfg.spacingDp));
        FrameLayout.LayoutParams av = (FrameLayout.LayoutParams) avatar.getLayoutParams();
        av.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        av.rightMargin = spacing + signedDp(cfg.avatarOffsetXDp);
        av.leftMargin = spacing;
        avatar.setLayoutParams(av);
        avatar.setTranslationY(signedDp(cfg.avatarOffsetYDp));

        int nameWidth = dp(cfg.nameWidthDp > 0 ? cfg.nameWidthDp : 90);
        FrameLayout.LayoutParams nm = (FrameLayout.LayoutParams) name.getLayoutParams();
        nm.width = nameWidth;
        nm.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        nm.rightMargin = spacing + dp(cfg.avatarDp) + spacing + signedDp(cfg.nameOffsetXDp);
        nm.leftMargin = spacing;
        name.setLayoutParams(nm);
        name.setTranslationY(signedDp(cfg.nameOffsetYDp));

        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) textViewport.getLayoutParams();
        vp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        vp.rightMargin = spacing + dp(cfg.avatarDp) + spacing + nameWidth + signedDp(cfg.textOffsetXDp);
        vp.leftMargin = spacing;
        vp.width = cfg.textWidthDp > 0 ? dp(cfg.textWidthDp) : ViewGroup.LayoutParams.MATCH_PARENT;
        textViewport.setLayoutParams(vp);
        textViewport.setTranslationY(signedDp(cfg.textOffsetYDp));
    }

    private void bindAd(AdvertisementDtos.Advertisement ad) {
        name.setText(ad.senderName == null ? "عضو" : ad.senderName);
        String fullMessage = ad.text == null ? "" : ad.text;
        text.setText(fullMessage);
        // Measure the complete message independently of the room's current width.
        // The viewport clips the moving ticker, never the TextView's actual content.
        text.setSingleLine(true);
        text.setHorizontallyScrolling(true);
        text.setEllipsize(null);
        int fullTextWidth = Math.max(dp(1), (int) Math.ceil(text.getPaint().measureText(fullMessage))
                + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight() + dp(8));
        text.setWidth(fullTextWidth);
        FrameLayout.LayoutParams fullTextParams = (FrameLayout.LayoutParams) text.getLayoutParams();
        fullTextParams.width = fullTextWidth;
        text.setLayoutParams(fullTextParams);
        if (config != null) applyConfiguredGradients(config);
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

    private String resolvedCustomFontUrl(AdvertisementDtos.Ticker cfg) {
        if (cfg == null || cfg.customFontUrl == null || cfg.customFontUrl.trim().isEmpty()) return "";
        String value = cfg.customFontUrl.trim();
        return value.startsWith("/") ? ApiOrigin.origin() + value : value;
    }

    private void applyConfiguredTypeface(AdvertisementDtos.Ticker cfg) {
        String family = cfg == null ? "default" : cfg.fontFamily;
        Typeface face;
        if ("custom".equalsIgnoreCase(family)) {
            face = customTypeface != null ? customTypeface : Typeface.DEFAULT;
            loadCustomTypeface(cfg);
        } else {
            if (customFontTarget != null) { Glide.with(this).clear(customFontTarget); customFontTarget = null; }
            loadedCustomFontUrl = "";
            customTypeface = null;
            face = "serif".equalsIgnoreCase(family) ? Typeface.SERIF
                    : "mono".equalsIgnoreCase(family) ? Typeface.MONOSPACE
                    : "sans".equalsIgnoreCase(family) ? Typeface.SANS_SERIF : Typeface.DEFAULT;
        }
        text.setTypeface(face);
        name.setTypeface(face, Typeface.BOLD);
    }

    private void loadCustomTypeface(AdvertisementDtos.Ticker cfg) {
        final String url = resolvedCustomFontUrl(cfg);
        if (url.isEmpty() || url.equals(loadedCustomFontUrl)) return;
        loadedCustomFontUrl = url;
        if (customFontTarget != null) Glide.with(this).clear(customFontTarget);
        customFontTarget = new CustomTarget<File>() {
            @Override public void onResourceReady(File resource, Transition<? super File> transition) {
                if (config == null || !url.equals(resolvedCustomFontUrl(config))) return;
                try {
                    Typeface loaded = Typeface.createFromFile(resource);
                    customTypeface = loaded;
                    text.setTypeface(loaded);
                    name.setTypeface(loaded, Typeface.BOLD);
                    applyConfiguredGradients(config);
                } catch (Exception ignored) {
                    customTypeface = null;
                }
            }
            @Override public void onLoadCleared(Drawable placeholder) {}
            @Override public void onLoadFailed(Drawable errorDrawable) {
                customTypeface = null;
            }
        };
        Glide.with(this).asFile().load(url).into(customFontTarget);
    }

    private void applyConfiguredGradients(AdvertisementDtos.Ticker cfg) {
        if (cfg == null) return;
        applyGradient(name, cfg.nameGradientEnabled, cfg.nameGradientStartColor,
                cfg.nameGradientEndColor, cfg.nameColor);
        // Keep advertisement text color controlled by the Dashboard.
        // Fall back to a readable dark color only when the dashboard value is missing/invalid.
        text.getPaint().setShader(null);
        text.setTextColor(safeColor(cfg.textColor, Color.rgb(17, 24, 39)));
        text.invalidate();
    }

    private void applyGradient(TextView target, boolean enabled, String startColor,
                               String endColor, String fallbackColor) {
        target.getPaint().setShader(null);
        if (!enabled) {
            target.setTextColor(safeColor(fallbackColor, Color.WHITE));
            target.invalidate();
            return;
        }
        target.post(() -> {
            int width = Math.max(1, target.getWidth());
            int start = safeColor(startColor, Color.WHITE);
            int end = safeColor(endColor, start);
            target.getPaint().setShader(new LinearGradient(0f, 0f, width, 0f,
                    start, end, Shader.TileMode.CLAMP));
            target.invalidate();
        });
    }

    private void animateTextIn(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, long generation) {
        text.clearAnimation();
        text.setAlpha(1f);
        name.setAlpha(1f);
        avatar.setAlpha(1f);
        text.setTranslationX(0f);
        name.setTranslationX(0f);
        avatar.setTranslationX(0f);
        final long budget = Math.max(1000L, currentDisplayBudgetMs > 0 ? currentDisplayBudgetMs : 9000L);
        if (cfg != null && cfg.reducedMotionStatic && isReduceMotionEnabled()) {
            postDelayed(() -> {
                if (generation == animationGeneration && ad.id.equals(currentAdId)) hideAdvertisement();
            }, budget);
            return;
        }
        text.post(() -> {
            if (generation != animationGeneration || !ad.id.equals(currentAdId)) return;
            ArrayList<Animator> animations = new ArrayList<>();
            String legacyTextMode = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction)
                    ? "right-to-left" : "left-to-right";
            String configuredTextMode = cfg == null || cfg.textMotion == null ? legacyTextMode : cfg.textMotion;
            int viewportWidth = Math.max(1, textViewport.getWidth());
            int completeTextWidth = Math.max(text.getWidth(),
                    (int) Math.ceil(text.getPaint().measureText(text.getText().toString())));
            // A long message must still travel through the viewport even when the
            // dashboard's selected mode is static/fade; otherwise some words stay clipped.
            if (completeTextWidth > viewportWidth
                    && ("static".equalsIgnoreCase(configuredTextMode) || "fade".equalsIgnoreCase(configuredTextMode))) {
                configuredTextMode = legacyTextMode;
            }
            addElementMotion(text, configuredTextMode, viewportWidth, budget, animations);
            addElementMotion(name, cfg == null ? "static" : cfg.nameMotion,
                    Math.max(1, getWidth()), budget, animations);
            addElementMotion(avatar, cfg == null ? "static" : cfg.avatarMotion,
                    Math.max(1, getWidth()), budget, animations);
            if (animations.isEmpty()) {
                postDelayed(() -> {
                    if (generation == animationGeneration && ad.id.equals(currentAdId)) hideAdvertisement();
                }, budget);
                return;
            }
            AnimatorSet set = new AnimatorSet();
            set.playTogether(animations);
            activeAnimator = set;
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (activeAnimator == set) activeAnimator = null;
                    if (generation == animationGeneration && ad.id.equals(currentAdId)) hideAdvertisement();
                }
            });
            set.start();
        });
    }

    private void addElementMotion(View target, String rawMode, int viewport, long duration,
                                  ArrayList<Animator> animations) {
        String mode = rawMode == null ? "static" : rawMode.toLowerCase(Locale.ROOT);
        target.setTranslationX(0f);
        target.setAlpha(1f);
        if ("static".equals(mode) || mode.isEmpty()) return;
        if ("fade".equals(mode)) {
            ObjectAnimator fade = ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f);
            fade.setDuration(Math.min(700L, duration));
            fade.setInterpolator(new LinearInterpolator());
            animations.add(fade);
            return;
        }
        int width = Math.max(1, target.getWidth());
        float distance = viewport + width;
        boolean fromRight = "right-to-left".equals(mode) || "rtl".equals(mode);
        boolean fromLeft = "left-to-right".equals(mode) || "ltr".equals(mode);
        if (!fromRight && !fromLeft) return;
        float start = fromRight ? distance : -distance;
        float finish = fromRight ? -distance : distance;
        ObjectAnimator movement = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, start, finish);
        movement.setDuration(duration);
        movement.setInterpolator(new LinearInterpolator());
        animations.add(movement);
    }

    private void applySafePlacement(String position, int customOffsetDp, int xPercent, int yPercent) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) getLayoutParams();
            if (lp == null) return;
            lp.startToStart = ConstraintLayout.LayoutParams.UNSET;
            lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
            lp.leftToLeft = ConstraintLayout.LayoutParams.UNSET;
            lp.rightToRight = ConstraintLayout.LayoutParams.UNSET;
            lp.topToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
            lp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.horizontalBias = Math.max(0, Math.min(100, xPercent)) / 100f;
            lp.verticalBias = Math.max(0, Math.min(100, yPercent)) / 100f;
            if ("free".equalsIgnoreCase(position)) {
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topMargin = 0;
                lp.bottomMargin = 0;
            } else if ("safe-top".equalsIgnoreCase(position)) {
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topMargin = dp(68);
                lp.horizontalBias = 0.5f;
                lp.verticalBias = 0f;
            } else {
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToTop = R.id.bottomBar;
                lp.bottomMargin = "custom-safe".equalsIgnoreCase(position)
                        ? dp(Math.max(0, 76 - customOffsetDp)) : dp(6);
                lp.horizontalBias = 0.5f;
                lp.verticalBias = 1f;
            }
            setTranslationX(0f);
            setTranslationY(0f);
            setLayoutParams(lp);
            return;
        }
        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp == null) return;
            boolean bottom = "safe-bottom".equalsIgnoreCase(position) || "custom-safe".equalsIgnoreCase(position);
            lp.gravity = bottom ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                    : (Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = bottom ? 0 : dp(68);
            lp.bottomMargin = bottom ? dp(76) : 0;
            setLayoutParams(lp);
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
    private int signedDp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int safeColor(String value, int fallback) { try { return Color.parseColor(value); } catch (Exception e) { return fallback; } }
    private static int parseColorWithOpacity(int opacity, String value) {
        try {
            int color = Color.parseColor(value);
            int a = Math.max(0, Math.min(255, Math.round(255f * opacity / 100f)));
            return (color & 0x00FFFFFF) | (a << 24);
        } catch (Exception e) { return 0xEE21142D; }
    }
}