package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
    private final View glowOverlay;
    private final ImageView avatar;
    private final TextView name;
    private final TextView text;
    private final FrameLayout textViewport;
    private final AtomicBoolean attached = new AtomicBoolean(false);
    private volatile AdvertisementDtos.Ticker config;
    private volatile String currentAdId = "";
    private volatile AdvertisementDtos.Advertisement currentAdvertisement;
    private AnimatorSet activeAnimator;
    // True once the current ad's motion has actually been initialized; a refresh must retry if layout prevented startup.
    private volatile boolean needsResume = false;
    private boolean movingForPlacement = false;
    private boolean animationInitialized = false;
    private long currentDisplayBudgetMs = 9000L;
    private long animationGeneration = 0L;
    private volatile long serverClockAtSyncMs = 0L;
    private volatile long serverClockSyncElapsedMs = 0L;
    private volatile boolean serverClockKnown = false;
    private Runnable expiryRunnable;
    private volatile long refreshGeneration = 0L;
    private CustomTarget<File> customFontTarget;
    private volatile String loadedCustomFontUrl = "";
    private volatile String loadedBackgroundImageUrl = "";
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
        glowOverlay = new View(context);
        glowOverlay.setClickable(false);
        glowOverlay.setAlpha(0f);
        addView(glowOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
            if (customFontTarget != null) {
                // The View can be detached while its Activity is already being destroyed.
                // Never ask Glide.with(View) for a RequestManager from this lifecycle callback.
                try {
                    Glide.with(getContext().getApplicationContext()).clear(customFontTarget);
                } catch (Exception ignored) {
                    // Glide owns the request lifecycle; dropping our reference is sufficient
                    // when the Activity/View hierarchy is already being torn down.
                }
                customFontTarget = null;
            }
            if (!movingForPlacement) needsResume = true;
        }
        super.onDetachedFromWindow();
    }

    @Override public void onUserEvent(String event, JsonObject payload) {
        if ("room:advertisement:config".equals(event)) {
            // Invalidate older current-ad responses and rebind a running ticker to
            // the new dashboard motion settings without changing the saved settings.
            refreshGeneration++;
            try {
                AdvertisementDtos.Ticker next = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.Ticker.class);
                config = next;
                if (next != null) SEND_BUTTON_ENABLED = next.enabled && next.sendButtonEnabled && !next.paused;
                if (next != null && (!next.enabled || next.paused)) {
                    post(() -> hideAdvertisement());
                } else if (next != null) {
                    if (!currentAdId.isEmpty()) {
                        needsResume = true;
                        animationInitialized = false;
                    }
                    refreshCurrent();
                }
            } catch (Exception ignored) {}
            return;
        }
        if (!"room:advertisement".equals(event) || payload == null) return;
        if (!payload.has("advertisement") || !payload.get("advertisement").isJsonObject()) return;
        AdvertisementDtos.RealtimeEvent ev = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.RealtimeEvent.class);
        if (ev == null || ev.advertisement == null) return;
        if ("end".equalsIgnoreCase(ev.type) || "completed".equalsIgnoreCase(ev.type)
                || "stop".equalsIgnoreCase(ev.type) || "expired".equalsIgnoreCase(ev.type)) {
            refreshGeneration++;
            if (!serverClockKnown && ev.serverNow != null) {
                long eventServerNow = parseIsoUtc(ev.serverNow, System.currentTimeMillis());
                anchorServerClock(eventServerNow, SystemClock.elapsedRealtime());
            }
            post(() -> {
                if (!attached.get()) return;
                boolean endingVisibleAd = ev.advertisement.id != null
                        && ev.advertisement.id.equals(currentAdId);
                if (endingVisibleAd) {
                    // Socket delivery latency differs per device. Use the server's
                    // completedAt timestamp, not event arrival, to hide in sync.
                    long endedAt = parseIsoUtc(ev.at, System.currentTimeMillis());
                    long remaining = endedAt - estimatedServerNowMs();
                    if (remaining <= 0L) {
                        hideAdvertisement();
                        refreshCurrent();
                    } else {
                        // scheduleExpiry performs the next-current lookup at the
                        // shared deadline; don't let an early HTTP "no active ad"
                        // response hide this ticker ahead of the other rooms.
                        scheduleExpiry(ev.advertisement.id, remaining, animationGeneration);
                    }
                } else {
                    refreshCurrent();
                }
            });
            return;
        }
        if (!"start".equalsIgnoreCase(ev.type)) return;
        // A realtime start event is newer than any current-ad request already in flight.
        refreshGeneration++;
        if (!serverClockKnown && ev.serverNow != null) {
            long eventServerNow = parseIsoUtc(ev.serverNow, System.currentTimeMillis());
            // Fallback only until the first round-trip clock sync completes.
            anchorServerClock(eventServerNow, SystemClock.elapsedRealtime());
        }
        long remaining = Math.max(0L, ev.advertisement.displayDurationMs);
        long serverNow = estimatedServerNowMs();
        if (ev.at != null) {
            long startedAt = parseIsoUtc(ev.at, serverNow);
            remaining = ev.advertisement.displayDurationMs - Math.max(0L, serverNow - startedAt);
        }
        final long remainingMs = remaining;
        final AdvertisementDtos.Ticker eventConfig = ev.config != null ? ev.config : config;
        if (eventConfig != null && (!eventConfig.enabled || eventConfig.paused)) {
            post(() -> RoomAdvertisementTickerView.this.hideAdvertisement());
            return;
        }
        post(() -> {
            if (!attached.get()) return;
            if (remainingMs <= 0L) {
                refreshCurrent();
                return;
            }
            showAdvertisement(ev.advertisement, eventConfig, remainingMs);
        });
    }

    @Override public void onConnected() { refreshCurrent(); }
    @Override public void onDisconnected() {}

    private void refreshCurrent() {
        if (!attached.get()) return;
        final long requestGeneration = ++refreshGeneration;
        final long requestStartedElapsed = SystemClock.elapsedRealtime();
        ContainerProvider.from(getContext()).getRoomApi().advertisementCurrent().enqueue(new Callback<ApiResponse<AdvertisementDtos.Current>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Current>> call, Response<ApiResponse<AdvertisementDtos.Current>> response) {
                final long requestFinishedElapsed = SystemClock.elapsedRealtime();
                AdvertisementDtos.Current current = response.body() != null ? response.body().data : null;
                if (current != null && current.serverNow != null) {
                    long serverNow = parseIsoUtc(current.serverNow, System.currentTimeMillis());
                    long midpointElapsed = requestStartedElapsed
                            + Math.max(0L, requestFinishedElapsed - requestStartedElapsed) / 2L;
                    anchorServerClock(serverNow, midpointElapsed);
                }
                if (!attached.get()) return;
                if (requestGeneration != refreshGeneration) {
                    // A realtime start may beat this HTTP response. Keep its clock
                    // sample and re-phase the currently visible ad locally, without
                    // spawning another overlapping HTTP refresh request.
                    AdvertisementDtos.Advertisement active = currentAdvertisement;
                    AdvertisementDtos.Ticker activeConfig = config;
                    if (current != null && current.serverNow != null && active != null) {
                        needsResume = true;
                        animationInitialized = false;
                        post(() -> {
                            if (!attached.get() || !active.id.equals(currentAdId)) return;
                            long serverNow = estimatedServerNowMs();
                            long startedAt = parseIsoUtc(active.startedAt, serverNow);
                            long remaining = active.displayDurationMs - Math.max(0L, serverNow - startedAt);
                            if (remaining > 0L) showAdvertisement(active, activeConfig, remaining);
                            else hideAdvertisement();
                        });
                    }
                    return;
                }
                if (current != null && current.config != null) {
                    SEND_BUTTON_ENABLED = current.config.enabled && current.config.sendButtonEnabled && !current.config.paused;
                    config = current.config;
                }
                if (current != null && current.advertisement != null && current.config != null && current.config.enabled && !current.config.paused) {
                    long remaining = Math.max(0L, current.advertisement.displayDurationMs);
                    try {
                        long serverNow = estimatedServerNowMs();
                        long startedAt = parseIsoUtc(current.advertisement.startedAt, serverNow);
                        remaining = current.advertisement.displayDurationMs - Math.max(0L, serverNow - startedAt);
                    } catch (Exception ignored) {}
                    final long remainingMs = remaining;
                    if (remainingMs > 0L) post(() -> {
                        if (!attached.get()) return;
                        if (current.advertisement.id != null
                                && current.advertisement.id.equals(currentAdId)) {
                            needsResume = true;
                            animationInitialized = false;
                        }
                        showAdvertisement(current.advertisement, current.config, remainingMs);
                    });
                    else post(() -> {
                        if (!attached.get()) return;
                        hideAdvertisement();
                    });
                } else {
                    // No globally active advertisement: remove the entire row, not just its contents.
                    post(() -> {
                        if (!attached.get()) return;
                        hideAdvertisement();
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Current>> call, Throwable t) {
                // Keep the current display governed by its already scheduled server-time expiry.
            }
        });
    }

    private void anchorServerClock(long serverNowMs, long elapsedRealtimeMs) {
        serverClockAtSyncMs = serverNowMs;
        serverClockSyncElapsedMs = elapsedRealtimeMs;
        serverClockKnown = true;
    }

    private long estimatedServerNowMs() {
        if (serverClockKnown) {
            return serverClockAtSyncMs
                    + Math.max(0L, SystemClock.elapsedRealtime() - serverClockSyncElapsedMs);
        }
        return System.currentTimeMillis();
    }

    private void scheduleExpiry(String adId, long remainingMs, long generation) {
        if (expiryRunnable != null) removeCallbacks(expiryRunnable);
        if (remainingMs <= 0L) {
            if (adId != null && adId.equals(currentAdId)) hideAdvertisement();
            return;
        }
        expiryRunnable = () -> {
            if (!attached.get() || generation != animationGeneration || !adId.equals(currentAdId)) return;
            hideAdvertisement();
            // The server may already have promoted the next queued advertisement.
            refreshCurrent();
        };
        postDelayed(expiryRunnable, remainingMs);
    }

    private void cancelActiveAnimator() {
        if (expiryRunnable != null) {
            removeCallbacks(expiryRunnable);
            expiryRunnable = null;
        }
        AnimatorSet animator = activeAnimator;
        activeAnimator = null;
        animationGeneration++;
        animationInitialized = false;
        if (animator != null) animator.cancel();
        text.animate().cancel();
    }

    private void clearGlide(ImageView target) {
        if (target == null) return;
        try {
            Glide.with(getContext().getApplicationContext()).clear(target);
        } catch (Exception ignored) {
            // Activity may already be destroyed.
        }
    }

    private void clearGlide(CustomTarget<File> target) {
        if (target == null) return;
        try {
            Glide.with(getContext().getApplicationContext()).clear(target);
        } catch (Exception ignored) {
            // Activity may already be destroyed.
        }
    }

    private void hideAdvertisement() {
        if (!attached.get()) return;
        cancelActiveAnimator();
        currentAdId = "";
        currentAdvertisement = null;
        needsResume = false;
        currentDisplayBudgetMs = 0L;
        text.setText("");
        name.setText("");
        text.setTranslationX(0f);
        name.setTranslationX(0f);
        glowOverlay.setAlpha(0f);
        glowOverlay.setScaleX(1f);
        glowOverlay.setScaleY(1f);
        avatar.setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        text.setAlpha(1f);
        name.setAlpha(1f);
        avatar.setAlpha(1f);
        clearGlide(backgroundImage);
        loadedBackgroundImageUrl = "";
        clearGlide(avatar);
        backgroundImage.setImageDrawable(null);
        avatar.setImageDrawable(null);
        backgroundImage.setVisibility(GONE);
        setBackground(null);
        setVisibility(GONE);
    }

    private void applyGlowConfig(AdvertisementDtos.Ticker cfg) {
        if (cfg == null || cfg.glowType == null || "none".equalsIgnoreCase(cfg.glowType) || cfg.glowIntensity <= 0) {
            glowOverlay.setAlpha(0f); return;
        }
        int base = safeColor(cfg.glowColor, Color.parseColor("#FFD66B"));
        int alpha = Math.max(0, Math.min(100, cfg.glowIntensity)) * 180 / 100;
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(Math.max(12, alpha / 4), Color.red(base), Color.green(base), Color.blue(base)));
        g.setCornerRadius(dp(Math.max(0, cfg.radiusDp)));
        g.setStroke(Math.max(1, dp(1)), Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)));
        glowOverlay.setBackground(g);
        glowOverlay.setElevation(dp(6));
    }

    private void showAdvertisement(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg) {
        showAdvertisement(ad, cfg, ad == null ? 0L : ad.displayDurationMs);
    }

    private void showAdvertisement(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, long remainingMs) {
        if (ad == null || ad.id == null) return;
        if (remainingMs <= 0L) {
            if (ad.id.equals(currentAdId)) hideAdvertisement();
            return;
        }
        boolean sameAd = ad.id.equals(currentAdId);
        if (ad.displayConfig != null && cfg != null) cfg = mergeDisplayConfig(cfg, ad.displayConfig);
        currentAdvertisement = ad;
        if (sameAd && !needsResume && animationInitialized) {
            config = cfg;
            currentDisplayBudgetMs = remainingMs;
            bindConfig(cfg);
            scheduleExpiry(ad.id, remainingMs, animationGeneration);
            return;
        }
        cancelActiveAnimator();
        currentAdId = ad.id;
        needsResume = false;
        currentDisplayBudgetMs = remainingMs;
        config = cfg;
        bindConfig(cfg);
        bindAd(ad);
        // Do not flash the completed-position bar for a frame before its
        // server-synchronized entrance phase is initialized.
        setVisibility(INVISIBLE);
        scheduleExpiry(ad.id, remainingMs, animationGeneration);
        animateTextIn(ad, cfg, animationGeneration, Math.max(0L, ad.displayDurationMs - remainingMs));
    }

    private AdvertisementDtos.Ticker mergeDisplayConfig(AdvertisementDtos.Ticker base, AdvertisementDtos.Ticker override) {
        try {
            JsonObject merged = new com.google.gson.Gson().toJsonTree(base).getAsJsonObject();
            JsonObject extra = new com.google.gson.Gson().toJsonTree(override).getAsJsonObject();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : extra.entrySet()) if (e.getValue() != null && !e.getValue().isJsonNull()) merged.add(e.getKey(), e.getValue());
            return new com.google.gson.Gson().fromJson(merged, AdvertisementDtos.Ticker.class);
        } catch (Exception ignored) { return base; }
    }

    private void bindConfig(AdvertisementDtos.Ticker cfg) {
        if (cfg == null) return;
        int h = dp(Math.max(36, cfg.heightDp > 0 ? cfg.heightDp : 48));
        ViewGroup.LayoutParams own = getLayoutParams();
        if (own != null && own.height != h) { own.height = h; setLayoutParams(own); }
        setMinimumHeight(h);
        setBackground(buildBackground(cfg.background, cfg.backgroundOpacity, cfg.radiusDp));
        backgroundImage.setAlpha(Math.max(0, Math.min(100, cfg.backgroundOpacity)) / 100f);
        String backgroundUrl = cfg.backgroundImage == null ? "" : cfg.backgroundImage.trim();
        if ((backgroundUrl.startsWith("https://") || backgroundUrl.startsWith("http://"))) {
            // bindConfig may run again for the same active ad after a realtime refresh.
            // Re-submitting the same GIF request restarts its first frame and makes the
            // animated ticker background look frozen; keep the running drawable alive.
            if (!backgroundUrl.equals(loadedBackgroundImageUrl)) {
                loadedBackgroundImageUrl = backgroundUrl;
                Glide.with(getContext().getApplicationContext())
                        .load(backgroundUrl)
                        .centerCrop()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .into(backgroundImage);
            }
            backgroundImage.setVisibility(VISIBLE);
        } else {
            if (!loadedBackgroundImageUrl.isEmpty()) {
                Glide.with(getContext().getApplicationContext()).clear(backgroundImage);
                backgroundImage.setImageDrawable(null);
                loadedBackgroundImageUrl = "";
            }
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
        applyGlowConfig(cfg);
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
        updateTickerTextWidth();
        if (config != null) applyConfiguredGradients(config);
        if (ad.senderImageUrl != null && !ad.senderImageUrl.trim().isEmpty()) {
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(getContext().getApplicationContext()).load(ad.senderImageUrl).centerCrop()
                    .placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar);
            if (config != null && "circle".equalsIgnoreCase(config.avatarShape)) request = request.circleCrop();
            request.into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_default_avatar);
        }
        setVisibility(VISIBLE);
    }

    private void updateTickerTextWidth() {
        String fullMessage = text.getText() == null ? "" : text.getText().toString();
        int fullTextWidth = Math.max(dp(1), (int) Math.ceil(text.getPaint().measureText(fullMessage))
                + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight() + dp(8));
        text.setWidth(fullTextWidth);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) text.getLayoutParams();
        if (params != null && params.width != fullTextWidth) {
            params.width = fullTextWidth;
            text.setLayoutParams(params);
        }
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
            if (customFontTarget != null) {
                clearGlide(customFontTarget);
                customFontTarget = null;
            }
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
        if (customFontTarget != null) clearGlide(customFontTarget);
        customFontTarget = new CustomTarget<File>() {
            @Override public void onResourceReady(File resource, Transition<? super File> transition) {
                if (config == null || !url.equals(resolvedCustomFontUrl(config))) return;
                try {
                    Typeface loaded = Typeface.createFromFile(resource);
                    customTypeface = loaded;
                    text.setTypeface(loaded);
                    name.setTypeface(loaded, Typeface.BOLD);
                    updateTickerTextWidth();
                    applyConfiguredGradients(config);
                    AdvertisementDtos.Advertisement active = currentAdvertisement;
                    AdvertisementDtos.Ticker activeConfig = config;
                    if (attached.get() && active != null && active.id.equals(currentAdId)) {
                        long serverNow = estimatedServerNowMs();
                        long startedAt = parseIsoUtc(active.startedAt, serverNow);
                        long remaining = active.displayDurationMs - Math.max(0L, serverNow - startedAt);
                        needsResume = true;
                        animationInitialized = false;
                        if (remaining > 0L) {
                            post(() -> {
                                if (attached.get() && active.id.equals(currentAdId)) {
                                    showAdvertisement(active, activeConfig, remaining);
                                }
                            });
                        } else {
                            post(() -> RoomAdvertisementTickerView.this.hideAdvertisement());
                        }
                    }
                } catch (Exception ignored) {
                    customTypeface = null;
                }
            }
            @Override public void onLoadCleared(Drawable placeholder) {}
            @Override public void onLoadFailed(Drawable errorDrawable) {
                customTypeface = null;
            }
        };
        Glide.with(getContext().getApplicationContext()).asFile().load(url).into(customFontTarget);
    }

    private void applyConfiguredGradients(AdvertisementDtos.Ticker cfg) {
        if (cfg == null) return;
        applyGradient(name, cfg.nameGradientEnabled, cfg.nameGradientStartColor,
                cfg.nameGradientEndColor, cfg.nameColor);
        applyGradient(text, cfg.textGradientEnabled, cfg.textGradientStartColor,
                cfg.textGradientEndColor, cfg.textColor);
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

    private void animateTextIn(AdvertisementDtos.Advertisement ad, AdvertisementDtos.Ticker cfg, long generation, long elapsedSinceServerStartMs) {
        text.clearAnimation();
        text.setAlpha(1f);
        name.setAlpha(1f);
        avatar.setAlpha(1f);
        text.setTranslationX(0f);
        name.setTranslationX(0f);
        avatar.setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        final long budget = Math.max(1000L, currentDisplayBudgetMs > 0 ? currentDisplayBudgetMs : 9000L);
        if (isReduceMotionEnabled()) {
            // Respect the device's reduced-motion setting while keeping the ad readable.
            setVisibility(VISIBLE);
            animationInitialized = true;
            return;
        }
        // Begin the ticker motion on the very next frame after its content is laid out.
        postOnAnimation(() -> {
            if (generation != animationGeneration || !ad.id.equals(currentAdId) || !attached.get()) return;
            // Room overlays can receive the realtime event before their parent has measured them.
            // Retry after layout instead of constructing a 1px ticker that appears frozen.
            if (textViewport.getWidth() <= dp(8) || text.getWidth() <= dp(1)) {
                postDelayed(() -> animateTextIn(ad, cfg, generation, elapsedSinceServerStartMs), 32L);
                return;
            }
            ArrayList<Animator> animations = new ArrayList<>();
            // Professional dashboard-controlled bar entrance. The text ticker remains
            // independently synchronized, while the whole pill gets a polished entrance.
            String entry = cfg == null || cfg.entryAnimation == null ? "slide-right" : cfg.entryAnimation.toLowerCase(Locale.ROOT);
            Animator entryAnimator = buildBarTransition(entry, true);
            if (entryAnimator != null) animations.add(entryAnimator);
            String legacyTextMode = cfg == null || !"ltr".equalsIgnoreCase(cfg.direction)
                    ? "right-to-left" : "left-to-right";
            String configuredTextMode = cfg == null || cfg.textMotion == null
                    ? legacyTextMode : cfg.textMotion;
            int viewportWidth = Math.max(1, textViewport.getWidth());
            int completeTextWidth = Math.max(text.getWidth(),
                    (int) Math.ceil(text.getPaint().measureText(text.getText().toString())));
            // The dashboard controls the motion mode. Never replace its setting with
            // another mode: static means static and fade means fade.
            // For a moving ticker, use the configured pixel speed so the complete
            // message crosses the viewport before the ad is removed.
            long textMotionDuration = calculateTextMotionDuration(
                    configuredTextMode, viewportWidth, completeTextWidth, cfg, budget);
            addElementMotion(text, configuredTextMode, viewportWidth, textMotionDuration,
                    animations, elapsedSinceServerStartMs);
            // Keep the whole ticker lifecycle tied to the text crossing the bar.
            // No avatar/name animation may keep an already-finished message visible.
            addElementMotion(name, cfg == null ? "static" : cfg.nameMotion,
                    Math.max(1, getWidth()), textMotionDuration, animations, elapsedSinceServerStartMs);
            addElementMotion(avatar, cfg == null ? "static" : cfg.avatarMotion,
                    Math.max(1, getWidth()), textMotionDuration, animations, elapsedSinceServerStartMs);
            if (animations.isEmpty()) {
                // Static mode remains visible until the authoritative expiry callback fires.
                setVisibility(VISIBLE);
                return;
            }
            setVisibility(VISIBLE);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(animations);
            activeAnimator = set;
            animationInitialized = true;
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (activeAnimator == set) activeAnimator = null;
                    if (generation == animationGeneration && ad.id.equals(currentAdId)) {
                        // Start the selected professional exit immediately after the
                        // final word has crossed the edge. Never leave the finished bar visible.
                        Animator exit = buildBarTransition(cfg == null || cfg.exitAnimation == null ? "slide-left" : cfg.exitAnimation.toLowerCase(Locale.ROOT), false);
                        if (exit != null) {
                            exit.addListener(new AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(Animator a) {
                                    if (generation == animationGeneration && ad.id.equals(currentAdId)) hideAdvertisement();
                                }
                            });
                            activeAnimator = new AnimatorSet();
                            ((AnimatorSet)activeAnimator).playTogether(exit);
                            activeAnimator.start();
                        } else {
                            hideAdvertisement();
                        }
                    }
                }
            });
            set.start();
            // Reconstruct each child animator's phase from the same server timeline.
            // AnimatorSet.setCurrentPlayTime is API 26+, but ValueAnimator supports
            // setCurrentPlayTime on older Android versions too.
            if (elapsedSinceServerStartMs > 0L) {
                final long phaseSyncElapsed = SystemClock.elapsedRealtime();
                postOnAnimation(() -> {
                    if (!attached.get() || generation != animationGeneration || activeAnimator != set) return;
                    long synchronizedPlayTime = elapsedSinceServerStartMs
                            + Math.max(0L, SystemClock.elapsedRealtime() - phaseSyncElapsed);
                    for (Animator child : animations) {
                        if (child instanceof ValueAnimator) {
                            try {
                                ((ValueAnimator) child).setCurrentPlayTime(synchronizedPlayTime);
                            } catch (Exception ignored) {}
                        }
                    }
                });
            }
        });
    }

    private Animator buildBarTransition(String mode, boolean entering) {
        String m = mode == null ? "slide-right" : mode.toLowerCase(Locale.ROOT);
        long d = m.contains("fire") ? 360L : (m.contains("gold") ? 320L : 240L);
        d = Math.min(420L, Math.max(160L, d));
        ArrayList<Animator> a = new ArrayList<>();
        float distance = dp(22);
        if (m.contains("top")) setTranslationY(entering ? -distance : 0f);
        else if (m.contains("bottom")) setTranslationY(entering ? distance : 0f);
        else if (m.contains("right")) setTranslationX(entering ? distance : 0f);
        else if (m.contains("left")) setTranslationX(entering ? -distance : 0f);
        else { setTranslationX(0f); setTranslationY(0f); }
        if (m.contains("zoom") || m.contains("fire") || m.contains("gold")) { setScaleX(entering ? 0.94f : 1f); setScaleY(entering ? 0.94f : 1f); }
        if (m.contains("fade") || m.contains("fire") || m.contains("gold")) setAlpha(entering ? 0f : 1f);
        if (entering) {
            a.add(ObjectAnimator.ofFloat(this, View.TRANSLATION_X, getTranslationX(), 0f));
            a.add(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, getTranslationY(), 0f));
            a.add(ObjectAnimator.ofFloat(this, View.SCALE_X, getScaleX(), 1f));
            a.add(ObjectAnimator.ofFloat(this, View.SCALE_Y, getScaleY(), 1f));
            a.add(ObjectAnimator.ofFloat(this, View.ALPHA, getAlpha(), 1f));
        } else {
            float tx=getTranslationX(), ty=getTranslationY();
            if (m.contains("top")) ty=-distance; else if (m.contains("bottom")) ty=distance; else if (m.contains("right")) tx=distance; else if (m.contains("left")) tx=-distance;
            a.add(ObjectAnimator.ofFloat(this, View.TRANSLATION_X, getTranslationX(), tx));
            a.add(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, getTranslationY(), ty));
            if (m.contains("zoom") || m.contains("fire") || m.contains("gold")) { a.add(ObjectAnimator.ofFloat(this, View.SCALE_X, getScaleX(), 0.94f)); a.add(ObjectAnimator.ofFloat(this, View.SCALE_Y, getScaleY(), 0.94f)); }
            if (m.contains("fade") || m.contains("fire") || m.contains("gold")) a.add(ObjectAnimator.ofFloat(this, View.ALPHA, getAlpha(), 0f));
        }
        AnimatorSet set = new AnimatorSet(); set.playTogether(a); set.setDuration(d); set.setInterpolator(new LinearInterpolator());
        if (m.contains("fire") || m.contains("gold")) {
            glowOverlay.setAlpha(entering ? Math.max(0.15f, (cfgGlowIntensity()) / 100f) : glowOverlay.getAlpha());
            glowOverlay.animate().alpha(entering ? 0.8f : 0f).scaleX(entering ? 1.02f : 1.08f).scaleY(entering ? 1.02f : 1.08f).setDuration(d).start();
        }
        return set;
    }

    private float cfgGlowIntensity() { return config == null ? 55f : Math.max(0, Math.min(100, config.glowIntensity)); }

    private long calculateTextMotionDuration(String rawMode, int viewportWidth, int textWidth,
                                             AdvertisementDtos.Ticker cfg, long displayBudget) {
        String mode = rawMode == null ? "static" : rawMode.toLowerCase(Locale.ROOT);
        if ("static".equals(mode) || mode.isEmpty() || "fade".equals(mode)) return displayBudget;
        boolean moving = "right-to-left".equals(mode) || "rtl".equals(mode)
                || "left-to-right".equals(mode) || "ltr".equals(mode);
        if (!moving) return displayBudget;
        int entrySpeed = cfg == null ? 120 : cfg.entrySpeedPxPerSec;
        int exitSpeed = cfg == null ? 120 : cfg.exitSpeedPxPerSec;
        entrySpeed = dp(Math.max(20, Math.min(800, entrySpeed > 0 ? entrySpeed : 120)));
        exitSpeed = dp(Math.max(20, Math.min(800, exitSpeed > 0 ? exitSpeed : 120)));
        boolean fromRight = "right-to-left".equals(mode) || "rtl".equals(mode);
        long entryDistance = fromRight ? Math.max(1, viewportWidth) : Math.max(1, textWidth);
        long exitDistance = fromRight ? Math.max(1, textWidth) : Math.max(1, viewportWidth);
        long entryMs = (entryDistance * 1000L + entrySpeed - 1L) / entrySpeed;
        long exitMs = (exitDistance * 1000L + exitSpeed - 1L) / exitSpeed;
        // Enter and leave at their separately configured dashboard speeds.
        return Math.max(1000L, Math.min(120000L, entryMs + exitMs + 140L));
    }

    private void addElementMotion(View target, String rawMode, int viewport, long duration,
                                  ArrayList<Animator> animations, long elapsedSinceServerStartMs) {
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
        int visibleWidth = Math.max(1, viewport);
        boolean fromRight = "right-to-left".equals(mode) || "rtl".equals(mode);
        boolean fromLeft = "left-to-right".equals(mode) || "ltr".equals(mode);
        if (!fromRight && !fromLeft) return;

        // Start just outside the viewport and finish immediately after the complete
        // text leaves the opposite edge. This prevents the first/last words from
        // being clipped and avoids wasting time on an unnecessary second viewport.
        float start = fromRight ? visibleWidth : -width;
        float finish = fromRight ? -width : visibleWidth;
        float travel = Math.abs(finish - start);
        if (target == text && config != null) {
            int entrySpeedDp = Math.max(20, Math.min(800,
                    config.entrySpeedPxPerSec > 0 ? config.entrySpeedPxPerSec : 120));
            int exitSpeedDp = Math.max(20, Math.min(800,
                    config.exitSpeedPxPerSec > 0 ? config.exitSpeedPxPerSec : 120));
            float entryPoint = 0f;
            target.setTranslationX(start);
            // Use one canonical dp timeline for every device/room. The actual
            // viewport/text dimensions only affect the pixel positions, never the
            // shared entry/exit phase durations.
            float textSizeSp = Math.min(36f, Math.max(9f, config.textSp > 0 ? config.textSp : 13));
            long canonicalTextWidthDp = Math.max(1L, (long) Math.ceil(
                    text.getText().length() * Math.max(8f, textSizeSp * 0.62f)));
            long canonicalViewportWidthDp = 360L;
            boolean fromRightForTimeline = "right-to-left".equals(mode) || "rtl".equals(mode);
            long entryDistanceDp = fromRightForTimeline ? canonicalViewportWidthDp : canonicalTextWidthDp;
            long exitDistanceDp = fromRightForTimeline ? canonicalTextWidthDp : canonicalViewportWidthDp;
            long entryMs = Math.max(1L, (long) Math.ceil(entryDistanceDp * 1000d / entrySpeedDp));
            long exitMs = Math.max(1L, (long) Math.ceil(exitDistanceDp * 1000d / exitSpeedDp));
            long cycleMs = entryMs + exitMs;

            // Keep the ticker moving for the entire server-controlled ad lifetime.
            // The reset happens only after the complete message has left the clipped
            // viewport, so users never see a stationary message between passes.
            ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);
            ticker.setDuration(Math.max(1L, cycleMs));
            // One complete pass only. The backend expiry is motionBudget + 300ms,
            // so the row disappears shortly after the final word clears the viewport.
            ticker.setRepeatCount(0);
            ticker.setInterpolator(new LinearInterpolator());
            // Drive the ticker from monotonic elapsed time anchored to the server
            // start timestamp. A delayed frame catches up immediately instead of
            // freezing or accumulating drift after a busy UI frame.
            final long tickerAnchorElapsed = SystemClock.elapsedRealtime();
            final long tickerAnchorServerElapsed = Math.max(0L, elapsedSinceServerStartMs);
            ticker.addUpdateListener(animation -> {
                long timelineElapsed = tickerAnchorServerElapsed
                        + Math.max(0L, SystemClock.elapsedRealtime() - tickerAnchorElapsed);
                long cyclePosition = timelineElapsed % cycleMs;
                float phase = cyclePosition / (float) cycleMs;
                float split = entryMs / (float) cycleMs;
                float x;
                if (phase <= split) {
                    float leg = split <= 0f ? 1f : phase / split;
                    x = start + (entryPoint - start) * leg;
                } else {
                    float leg = (phase - split) / Math.max(0.0001f, 1f - split);
                    x = entryPoint + (finish - entryPoint) * leg;
                }
                target.setTranslationX(x);
            });
            animations.add(ticker);
            return;
        }
        long computedDuration = Math.max(1L, Math.round((travel / 120f) * 1000f));
        long safeDuration = Math.max(1000L, duration > 0L ? Math.min(duration, computedDuration + 140L) : computedDuration + 140L);
        ObjectAnimator movement = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, start, finish);
        movement.setDuration(safeDuration);
        movement.setInterpolator(new LinearInterpolator());
        animations.add(movement);
    }

    private void applySafePlacement(String position, int customOffsetDp, int xPercent, int yPercent) {
        ViewGroup parent = (ViewGroup) getParent();
        boolean free = "free".equalsIgnoreCase(position);
        final float x = Math.max(0, Math.min(100, xPercent)) / 100f;
        final float y = Math.max(0, Math.min(100, yPercent)) / 100f;

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
            lp.horizontalBias = x;
            lp.verticalBias = y;

            if (free) {
                // Dashboard preview uses the centre of the draggable ticker as the anchor:
                // 50% means the ticker centre is exactly at 50% of the room width/height.
                // Keep the same anchor on Android, but compensate when the Activity draws
                // behind system bars so the saved Y coordinate refers to the visible room,
                // not the status/navigation areas.
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topMargin = 0;
                lp.bottomMargin = 0;
                setTranslationX(freePlacementInsetOffsetX(parent, x));
                setTranslationY(freePlacementInsetOffsetY(parent, y));
            } else if ("safe-top".equalsIgnoreCase(position)) {
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.topMargin = dp(68) + statusBarInsetPx();
                lp.horizontalBias = 0.5f;
                lp.verticalBias = 0f;
                setTranslationX(0f);
                setTranslationY(0f);
            } else {
                lp.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                lp.bottomToTop = R.id.bottomBar;
                lp.bottomMargin = "custom-safe".equalsIgnoreCase(position)
                        ? dp(Math.max(0, 76 - customOffsetDp)) : dp(6);
                lp.horizontalBias = 0.5f;
                lp.verticalBias = 1f;
                setTranslationX(0f);
                setTranslationY(0f);
            }
            setLayoutParams(lp);
            return;
        }

        if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp == null) return;
            if (free) {
                // FrameLayout has no bias; place the centre at the exact dashboard
                // percentage inside the room's usable (non-system-bar) rectangle.
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.leftMargin = 0;
                lp.topMargin = 0;
                setLayoutParams(lp);
                post(() -> {
                    if (!attached.get() || getParent() != parent) return;
                    float targetX = parent.getWidth() * x;
                    float targetY = parent.getHeight() * y;
                    setTranslationX(targetX - getWidth() / 2f + freePlacementInsetOffsetX(parent, x));
                    setTranslationY(targetY - getHeight() / 2f + freePlacementInsetOffsetY(parent, y));
                });
            } else {
                boolean bottom = "safe-bottom".equalsIgnoreCase(position) || "custom-safe".equalsIgnoreCase(position);
                lp.gravity = bottom ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                        : (Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lp.topMargin = bottom ? 0 : dp(68) + statusBarInsetPx();
                lp.bottomMargin = bottom ? dp(76) : 0;
                setTranslationX(0f);
                setTranslationY(0f);
                setLayoutParams(lp);
            }
        }
    }

    /**
     * Converts a dashboard percentage (whose room starts below the system bars)
     * to the corresponding Android window coordinate. This is intentionally only
     * used for the draggable/free placement; safe-top/safe-bottom retain their
     * existing behaviour.
     */
    private float freePlacementInsetOffsetX(ViewGroup parent, float percent) {
        if (Build.VERSION.SDK_INT < 23 || parent == null) return 0f;
        try {
            WindowInsets wi = getRootWindowInsets();
            if (wi == null) return 0f;
            int left = 0, right = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = wi.getInsets(WindowInsets.Type.systemBars());
                left = in.left;
                right = in.right;
            } else {
                left = wi.getSystemWindowInsetLeft();
                right = wi.getSystemWindowInsetRight();
            }
            return left - (left + right) * percent;
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private float freePlacementInsetOffsetY(ViewGroup parent, float percent) {
        if (Build.VERSION.SDK_INT < 23 || parent == null) return 0f;
        try {
            WindowInsets wi = getRootWindowInsets();
            if (wi == null) return 0f;
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = wi.getInsets(WindowInsets.Type.systemBars());
                top = in.top;
                bottom = in.bottom;
            } else {
                top = wi.getSystemWindowInsetTop();
                bottom = wi.getSystemWindowInsetBottom();
            }
            return top - (top + bottom) * percent;
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private int statusBarInsetPx() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsets wi = getRootWindowInsets();
                return wi == null ? 0 : wi.getInsets(WindowInsets.Type.statusBars()).top;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                WindowInsets wi = getRootWindowInsets();
                return wi == null ? 0 : wi.getSystemWindowInsetTop();
            }
        } catch (Exception ignored) {}
        return 0;
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