package com.Dramizo.Series.util;

import android.app.Activity;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.animation.LinearInterpolator;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.voiceroom.RoomJoinGateActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.Locale;
import java.util.Map;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Global celebration toast on Home / outside rooms.
 * Avatar or gift/game icon + مبروك line + optional times badge (no Go chip).
 */
public final class GlobalCelebrationToast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> current = new WeakReference<>(null);
    private static WeakReference<View> currentCenter = new WeakReference<>(null);
    /** Deduplicate each event + toast kind + placement independently for a short window. */
    private static final LinkedHashMap<String, DedupeState> RECENT_DEDUPE = new LinkedHashMap<>();
    private static final Map<String, ArrayDeque<ToastRequest>> PENDING_BY_LANE = new java.util.HashMap<>();
    private static final java.util.Set<String> ACTIVE_LANES = new java.util.HashSet<>();
    /** Each visible banner owns its hide timer; simultaneous wins never cancel one another. */
    private static final Map<View, Runnable> ACTIVE_HIDE_TASKS = new WeakHashMap<>();
    private static final Map<View, Integer> STACK_STEPS_DP = new WeakHashMap<>();
    private static volatile String remoteBackgroundUrl = "";
    private static volatile String remoteLuckyNormalBackgroundUrl = "";
    private static volatile String remoteLuckyReturnBackgroundUrl = "";
    private static volatile String remoteLuckyRoomHighBackgroundUrl = "";
    private static volatile String remoteLuckyHighBackgroundUrl = "";
    // Dashboard-controlled public announcement thresholds. Zero disables that criterion.
    private static volatile double remoteHighWinMinMultiplier = 10d;
    private static volatile long remoteHighWinMinCoins = 1000L;
    private static volatile boolean remoteGiftSendEnabled = true;
    private static volatile boolean remoteLuckyNormalEnabled = true;
    private static volatile boolean remoteLuckyReturnEnabled = true;
    private static volatile boolean remoteLuckyHighEnabled = true;
    private static volatile boolean remoteLuckyRoomHighEnabled = true;
    private static volatile boolean remoteShineEnabled = true;
    private static volatile boolean remoteFrameGlowEnabled = true;
    private static volatile boolean remoteLuckyNormalShineEnabled = true;
    private static volatile boolean remoteLuckyReturnShineEnabled = true;
    private static volatile boolean remoteLuckyHighShineEnabled = true;
    private static volatile boolean remoteLuckyNormalGlowEnabled = true;
    private static volatile boolean remoteLuckyReturnGlowEnabled = true;
    private static volatile boolean remoteLuckyRoomHighShineEnabled = true, remoteLuckyRoomHighGlowEnabled = true;
    private static volatile boolean remoteLuckyHighGlowEnabled = true;
    private static volatile int remoteDurationSeconds = 3;
    private static volatile int remoteLuckyNormalDurationSeconds = 4;
    private static volatile int remoteLuckyReturnDurationSeconds = 3;
    private static volatile int remoteLuckyRoomHighDurationSeconds = 5;
    private static volatile int remoteLuckyHighDurationSeconds = 5;
    private static volatile float remoteGiftAvatarX = 8f, remoteGiftAvatarY = 50f, remoteGiftTextX = 25f, remoteGiftTextY = 50f;
    private static volatile float remoteGiftBodyX = 25f, remoteGiftBodyY = 67f, remoteGiftCountX = 88f, remoteGiftCountY = 50f;
    private static volatile float remoteGiftPlacementX = 50f, remoteGiftPlacementY = 16f;
    private static volatile float remoteLuckyNormalPlacementX = 50f, remoteLuckyNormalPlacementY = 16f;
    private static volatile float remoteLuckyReturnPlacementX = 50f, remoteLuckyReturnPlacementY = 32f;
    private static volatile float remoteLuckyHighPlacementX = 50f, remoteLuckyHighPlacementY = 16f;
    private static volatile float remoteLuckyNormalAvatarX = 8f, remoteLuckyNormalAvatarY = 42f;
    private static volatile float remoteLuckyNormalTextX = 25f, remoteLuckyNormalTextY = 42f, remoteLuckyNormalBodyX = 25f, remoteLuckyNormalBodyY = 62f;
    private static volatile float remoteLuckyNormalCountX = 88f, remoteLuckyNormalCountY = 50f;
    private static volatile float remoteLuckyReturnAvatarX = 8f, remoteLuckyReturnAvatarY = 42f;
    private static volatile float remoteLuckyReturnTextX = 25f, remoteLuckyReturnTextY = 42f, remoteLuckyReturnBodyX = 25f, remoteLuckyReturnBodyY = 62f;
    private static volatile float remoteLuckyReturnCountX = 88f, remoteLuckyReturnCountY = 50f;
    private static volatile float remoteLuckyRoomHighAvatarX = 8f, remoteLuckyRoomHighAvatarY = 50f, remoteLuckyRoomHighPlacementX = 50f, remoteLuckyRoomHighPlacementY = 50f;
    private static volatile float remoteLuckyHighAvatarX = 8f, remoteLuckyHighAvatarY = 42f;
    private static volatile float remoteLuckyRoomHighTextX = 25f, remoteLuckyRoomHighTextY = 42f, remoteLuckyRoomHighBodyX = 25f, remoteLuckyRoomHighBodyY = 62f;
    private static volatile float remoteLuckyHighTextX = 25f, remoteLuckyHighTextY = 42f, remoteLuckyHighBodyX = 25f, remoteLuckyHighBodyY = 62f;
    private static volatile float remoteLuckyRoomHighCountX = 88f, remoteLuckyRoomHighCountY = 50f;
    private static volatile float remoteLuckyHighCountX = 88f, remoteLuckyHighCountY = 50f;
    private static volatile int remoteGiftWidthDp = 340, remoteGiftHeightDp = 100, remoteGiftAvatarSizeDp = 44;
    private static volatile float remoteGiftTitleSizeSp = 13f, remoteGiftBodySizeSp = 12f, remoteGiftCountSizeSp = 12f;
    private static volatile int remoteLuckyNormalWidthDp = 340, remoteLuckyNormalHeightDp = 100, remoteLuckyNormalAvatarSizeDp = 44;
    private static volatile float remoteLuckyNormalTitleSizeSp = 13f, remoteLuckyNormalBodySizeSp = 12f, remoteLuckyNormalCountSizeSp = 12f;
    private static volatile int remoteLuckyReturnWidthDp = 340, remoteLuckyReturnHeightDp = 100, remoteLuckyReturnAvatarSizeDp = 44;
    private static volatile float remoteLuckyReturnTitleSizeSp = 13f, remoteLuckyReturnBodySizeSp = 12f, remoteLuckyReturnCountSizeSp = 12f;
    private static volatile int remoteLuckyRoomHighWidthDp = 340, remoteLuckyRoomHighHeightDp = 100, remoteLuckyRoomHighAvatarSizeDp = 44;
    private static volatile int remoteLuckyHighWidthDp = 340, remoteLuckyHighHeightDp = 100, remoteLuckyHighAvatarSizeDp = 44;
    private static volatile float remoteLuckyRoomHighTitleSizeSp = 13f, remoteLuckyRoomHighBodySizeSp = 12f, remoteLuckyRoomHighCountSizeSp = 12f;
    private static volatile float remoteLuckyHighTitleSizeSp = 13f, remoteLuckyHighBodySizeSp = 12f, remoteLuckyHighCountSizeSp = 12f;
    private static long lastStyleFetchAt;
    private static volatile Map<String, Object> supportChestToastConfig;

    public static void setSupportChestToastConfig(@Nullable Map<?, ?> config) {
        if (config == null) { supportChestToastConfig = null; return; }
        java.util.HashMap<String, Object> copy = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : config.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        supportChestToastConfig = copy;
    }

    private static final class DedupeState {
        long shownAt;
        int placementMask;
    }

    private static final class ToastRequest {
        final WeakReference<Activity> activity;
        final String title;
        final String body;
        final String avatarUrl;
        final String badgeUrl;
        final int topMarginDp;
        final double multiplier;
        final long coinsWon;
        final String displayName;
        final String kind;
        final String roomId;
        final boolean centerPlacement;

        ToastRequest(Activity activity, String title, String body, String avatarUrl,
                     String badgeUrl, int topMarginDp, double multiplier, long coinsWon,
                     String displayName, String kind, String roomId, boolean centerPlacement) {
            this.activity = new WeakReference<>(activity);
            this.title = title;
            this.body = body;
            this.avatarUrl = avatarUrl;
            this.badgeUrl = badgeUrl;
            this.topMarginDp = topMarginDp;
            this.multiplier = multiplier;
            this.coinsWon = coinsWon;
            this.displayName = displayName;
            this.kind = kind;
            this.roomId = roomId;
            this.centerPlacement = centerPlacement;
        }
    }

    private static float numberConfig(@Nullable Map<String, Object> config, String key, float fallback) {
        if (config == null) return fallback;
        try {
            Object value = config.get(key);
            float parsed = Float.parseFloat(String.valueOf(value));
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private GlobalCelebrationToast() {}

    public static void showGiftCount(@Nullable Activity activity,
                                     @Nullable String senderName,
                                     @Nullable String giftName,
                                     @Nullable String avatarUrl,
                                     @Nullable String giftIconUrl,
                                     int quantity) {
        // Gift-send top toasts are retired globally. The gift animation remains in-room.
    }

    private static void applyRemoteStyleToActiveToast() {
        View active = current.get();
        if (active == null || active.getTag() == null) return;
        String activeKind = String.valueOf(active.getTag());
        boolean giftSend = "gift_send".equals(activeKind);
        boolean luckyRoomHigh = "lucky_hit_high_room".equals(activeKind);
        boolean luckyHigh = "lucky_hit_high".equals(activeKind) || luckyRoomHigh;
        boolean luckyReturn = "lucky_hit_return".equals(activeKind);
        boolean luckyNormal = "lucky_hit_normal".equals(activeKind);
        if (!giftSend && !luckyHigh && !luckyNormal && !luckyReturn) return;
        Activity activity = com.Dramizo.Series.AuraLiveApp.getForegroundActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        ImageView background = active.findViewById(R.id.imgToastBackground);
        String chosenBackground = giftSend ? remoteBackgroundUrl
                : luckyRoomHigh ? remoteLuckyRoomHighBackgroundUrl : luckyHigh ? remoteLuckyHighBackgroundUrl : luckyReturn ? remoteLuckyReturnBackgroundUrl : remoteLuckyNormalBackgroundUrl;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        final int requestedWidthDp = giftSend ? remoteGiftWidthDp
                : luckyRoomHigh ? remoteLuckyRoomHighWidthDp : luckyHigh ? remoteLuckyHighWidthDp
                : luckyReturn ? remoteLuckyReturnWidthDp : remoteLuckyNormalWidthDp;
        final int requestedHeightDp = giftSend ? remoteGiftHeightDp
                : luckyRoomHigh ? remoteLuckyRoomHighHeightDp : luckyHigh ? remoteLuckyHighHeightDp
                : luckyReturn ? remoteLuckyReturnHeightDp : remoteLuckyNormalHeightDp;
        ViewGroup.LayoutParams activeParams = active.getLayoutParams();
        if (activeParams instanceof FrameLayout.LayoutParams) {
            int maxWidth = Math.max(dp(activity, 220), screenWidth - dp(activity, 24));
            activeParams.width = Math.min(dp(activity, Math.max(220, Math.min(360, requestedWidthDp))), maxWidth);
            activeParams.height = dp(activity, Math.max(60, Math.min(180, requestedHeightDp)));
            active.setLayoutParams(activeParams);
            if ((luckyHigh || luckyReturn || luckyNormal || giftSend) && active.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) active.getParent();
                final float positionX = giftSend ? remoteGiftPlacementX : luckyRoomHigh ? remoteLuckyRoomHighPlacementX
                        : luckyHigh ? remoteLuckyHighPlacementX
                        : luckyReturn ? remoteLuckyReturnPlacementX : remoteLuckyNormalPlacementX;
                final float positionY = giftSend ? remoteGiftPlacementY : luckyRoomHigh ? remoteLuckyRoomHighPlacementY
                        : luckyHigh ? remoteLuckyHighPlacementY
                        : luckyReturn ? remoteLuckyReturnPlacementY : remoteLuckyNormalPlacementY;
                active.post(() -> {
                    if (active.getParent() != parent || parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
                    ViewGroup.LayoutParams currentParams = active.getLayoutParams();
                    if (!(currentParams instanceof FrameLayout.LayoutParams)) return;
                    FrameLayout.LayoutParams roomParams = (FrameLayout.LayoutParams) currentParams;
                    int maxLeft = Math.max(0, parent.getWidth() - active.getWidth());
                    int maxTop = Math.max(0, parent.getHeight() - active.getHeight());
                    int left = Math.round(parent.getWidth() * Math.max(0f, Math.min(100f, positionX)) / 100f - active.getWidth() / 2f);
                    int top = Math.round(parent.getHeight() * Math.max(0f, Math.min(100f, positionY)) / 100f - active.getHeight() / 2f);
                    roomParams.gravity = Gravity.TOP | Gravity.LEFT;
                    roomParams.leftMargin = Math.max(0, Math.min(maxLeft, left));
                    roomParams.topMargin = Math.max(0, Math.min(maxTop, top));
                    active.setLayoutParams(roomParams);
                });
            }
        }
        String imageUrl = toastBackgroundUrl(chosenBackground);
        if (background != null) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                active.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                background.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                background.setScaleType(ImageView.ScaleType.FIT_XY);
                background.setVisibility(View.VISIBLE);
                try {
                    Glide.with(activity).load(imageUrl).dontTransform().into(background);
                } catch (Exception ignored) {
                    background.setVisibility(View.GONE);
                }
            } else background.setVisibility(View.GONE);
        }
        boolean glow = giftSend ? remoteFrameGlowEnabled : luckyRoomHigh ? remoteLuckyRoomHighGlowEnabled : luckyHigh ? remoteLuckyHighGlowEnabled : luckyReturn ? remoteLuckyReturnGlowEnabled : remoteLuckyNormalGlowEnabled;
        boolean shine = giftSend ? remoteShineEnabled : luckyRoomHigh ? remoteLuckyRoomHighShineEnabled : luckyHigh ? remoteLuckyHighShineEnabled : luckyReturn ? remoteLuckyReturnShineEnabled : remoteLuckyNormalShineEnabled;
        active.setElevation(dp(activity, glow ? 20 : 14));
        View sheen = active.findViewById(R.id.viewCelebrationSheen);
        if (sheen != null && shine) {
            sheen.setVisibility(View.VISIBLE);
            GradientDrawable shineDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] { 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF });
            sheen.setBackground(shineDrawable);
            sheen.setRotation(12f);
            sheen.setAlpha(0.78f);
            active.post(() -> {
                if (sheen.getParent() != active || active.getParent() == null) return;
                ObjectAnimator sweep = ObjectAnimator.ofFloat(
                        sheen, View.TRANSLATION_X, -dp(activity, 52), active.getWidth() + dp(activity, 52));
                sweep.setDuration(1250L);
                sweep.setRepeatCount(1);
                sweep.setInterpolator(new LinearInterpolator());
                sweep.start();
            });
        } else if (sheen != null) sheen.setVisibility(View.GONE);
        if (giftSend || luckyHigh || luckyNormal || luckyReturn) {
            ImageView avatar = active.findViewById(R.id.imgCelebrationAvatar);
            TextView title = active.findViewById(R.id.tvCelebrationTitle);
            TextView body = active.findViewById(R.id.tvCelebrationBody);
            final float avatarX = giftSend ? remoteGiftAvatarX : luckyRoomHigh ? remoteLuckyRoomHighAvatarX : luckyHigh ? remoteLuckyHighAvatarX : luckyReturn ? remoteLuckyReturnAvatarX : remoteLuckyNormalAvatarX;
            final float avatarY = giftSend ? remoteGiftAvatarY : luckyRoomHigh ? remoteLuckyRoomHighAvatarY : luckyHigh ? remoteLuckyHighAvatarY : luckyReturn ? remoteLuckyReturnAvatarY : remoteLuckyNormalAvatarY;
            final float textX = giftSend ? remoteGiftTextX : luckyRoomHigh ? remoteLuckyRoomHighTextX : luckyHigh ? remoteLuckyHighTextX : luckyReturn ? remoteLuckyReturnTextX : remoteLuckyNormalTextX;
            final float textY = giftSend ? remoteGiftTextY : luckyRoomHigh ? remoteLuckyRoomHighTextY : luckyHigh ? remoteLuckyHighTextY : luckyReturn ? remoteLuckyReturnTextY : remoteLuckyNormalTextY;
            final float bodyX = giftSend ? remoteGiftBodyX : luckyRoomHigh ? remoteLuckyRoomHighBodyX : luckyHigh ? remoteLuckyHighBodyX : luckyReturn ? remoteLuckyReturnBodyX : remoteLuckyNormalBodyX;
            final float bodyY = giftSend ? remoteGiftBodyY : luckyRoomHigh ? remoteLuckyRoomHighBodyY : luckyHigh ? remoteLuckyHighBodyY : luckyReturn ? remoteLuckyReturnBodyY : remoteLuckyNormalBodyY;
            final float countX = giftSend ? remoteGiftCountX : luckyRoomHigh ? remoteLuckyRoomHighCountX : luckyHigh ? remoteLuckyHighCountX : luckyReturn ? remoteLuckyReturnCountX : remoteLuckyNormalCountX;
            final float countY = giftSend ? remoteGiftCountY : luckyRoomHigh ? remoteLuckyRoomHighCountY : luckyHigh ? remoteLuckyHighCountY : luckyReturn ? remoteLuckyReturnCountY : remoteLuckyNormalCountY;
            final int avatarSize = giftSend ? remoteGiftAvatarSizeDp : luckyRoomHigh ? remoteLuckyRoomHighAvatarSizeDp : luckyHigh ? remoteLuckyHighAvatarSizeDp : luckyReturn ? remoteLuckyReturnAvatarSizeDp : remoteLuckyNormalAvatarSizeDp;
            final float titleSize = giftSend ? remoteGiftTitleSizeSp : luckyRoomHigh ? remoteLuckyRoomHighTitleSizeSp : luckyHigh ? remoteLuckyHighTitleSizeSp : luckyReturn ? remoteLuckyReturnTitleSizeSp : remoteLuckyNormalTitleSizeSp;
            final float bodySize = giftSend ? remoteGiftBodySizeSp : luckyRoomHigh ? remoteLuckyRoomHighBodySizeSp : luckyHigh ? remoteLuckyHighBodySizeSp : luckyReturn ? remoteLuckyReturnBodySizeSp : remoteLuckyNormalBodySizeSp;
            final float countSize = giftSend ? remoteGiftCountSizeSp : luckyRoomHigh ? remoteLuckyRoomHighCountSizeSp : luckyHigh ? remoteLuckyHighCountSizeSp : luckyReturn ? remoteLuckyReturnCountSizeSp : remoteLuckyNormalCountSizeSp;
            applyTextSizes(title, body, active.findViewById(R.id.tvCelebrationTimes), titleSize, bodySize, countSize);
            resizeAvatar(activity, avatar, avatarSize);
            active.post(() -> {
                if (active.getParent() == null) return;
                positionViewPercent(avatar, active, avatarX, avatarY, true);
                positionViewPercent(title, active, textX, textY, false);
                positionViewPercent(body, active, bodyX, bodyY, false);
                positionViewPercent(active.findViewById(R.id.wrapCelebrationTimes), active, countX, countY, true);
            });
        }
        Runnable previousHide = ACTIVE_HIDE_TASKS.remove(active);
        if (previousHide != null) MAIN.removeCallbacks(previousHide);
        boolean activeCenter = active == currentCenter.get();
        Runnable styleHide = () -> removeBanner(active, activeCenter);
        ACTIVE_HIDE_TASKS.put(active, styleHide);
        int duration = giftSend ? remoteDurationSeconds : luckyRoomHigh ? remoteLuckyRoomHighDurationSeconds : luckyHigh ? remoteLuckyHighDurationSeconds : luckyReturn ? remoteLuckyReturnDurationSeconds : remoteLuckyNormalDurationSeconds;
        MAIN.postDelayed(styleHide, Math.max(1, Math.min(12, duration)) * 1000L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedConfig(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map ? (Map<String, Object>) value : java.util.Collections.emptyMap();
    }
    private static String stringValue(Object value, String fallback) {
        return value instanceof String ? ((String) value).trim() : fallback;
    }
    private static boolean isLuckyToastKind(@Nullable String kind) {
        if (kind == null) return false;
        String normalized = kind.trim().toLowerCase(java.util.Locale.ROOT);
        return "lucky_hit".equals(normalized)
                || "lucky_hit_normal".equals(normalized)
                || "lucky_hit_high".equals(normalized)
                || "lucky_hit_high_room".equals(normalized)
                || "lucky_hit_return".equals(normalized)
                || "lucky_return".equals(normalized)
                || "lucky_high".equals(normalized);
    }

    /** Dashboard switches are independent for normal, returned, general-high and room-high lanes. */
    private static boolean isToastEnabled(@Nullable String kind, boolean centerPlacement,
                                          double multiplier, long coinsWon, @Nullable String body) {
        if (kind == null) return true;
        String normalized = kind.trim().toLowerCase(java.util.Locale.ROOT);
        if ("gift_send".equals(normalized)) return remoteGiftSendEnabled;
        if (!isLuckyToastKind(normalized)) return true;
        boolean returned = "lucky_hit_return".equals(normalized) || "lucky_return".equals(normalized)
                || ( !"lucky_hit_high".equals(normalized) && !"lucky_high".equals(normalized)
                && !"lucky_hit_normal".equals(normalized)
                && (multiplier <= 0d || (body != null && (body.contains("مردود جزئي")
                || body.contains("مردود +") || body.contains("مردود الهدية")))));
        if (returned) return remoteLuckyReturnEnabled;
        boolean high = "lucky_hit_high".equals(normalized) || "lucky_high".equals(normalized)
                || multiplier >= remoteHighWinMinMultiplier && remoteHighWinMinMultiplier > 0d
                || coinsWon >= remoteHighWinMinCoins && remoteHighWinMinCoins > 0L;
        if (high) return centerPlacement ? remoteLuckyRoomHighEnabled : remoteLuckyHighEnabled;
        return remoteLuckyNormalEnabled;
    }

    private static int intValue(Object value, int fallback) {
        try { return Math.max(1, Math.min(12, (int) Math.round(Double.parseDouble(String.valueOf(value))))); }
        catch (Exception ignored) { return fallback; }
    }
    private static float floatValue(Object value, float fallback) {
        try { return Math.max(0f, Math.min(100f, Float.parseFloat(String.valueOf(value)))); }
        catch (Exception ignored) { return fallback; }
    }

    /** Refresh dashboard style when the shared fetch throttle allows it. */
    public static void refreshStyleNow(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        // Respect the shared one-minute throttle; screen resumes are not config-change events.
        refreshToastStyle(activity);
    }

    private static void refreshToastStyle(Activity activity) {
        long now = System.currentTimeMillis();
        if (now - lastStyleFetchAt < 60_000L) return;
        lastStyleFetchAt = now;
        try {
            com.Dramizo.Series.AuraLiveApp app =
                    (com.Dramizo.Series.AuraLiveApp) activity.getApplicationContext();
            app.getContainer().getConfigApi().toastStyle(now).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                                  Response<ApiResponse<Map<String, Object>>> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().data == null) return;
                    Map<String, Object> cfg = response.body().data;
                    Map<String, Object> gift = nestedConfig(cfg, "giftSend");
                    Map<String, Object> normal = nestedConfig(cfg, "luckyWinNormal");
                    Map<String, Object> returned = nestedConfig(cfg, "luckyReturn");
                    Map<String, Object> high = nestedConfig(cfg, "luckyWinHigh");
                    Map<String, Object> roomHigh = nestedConfig(cfg, "luckyWinHighRoom");
                    try { remoteHighWinMinMultiplier = Math.max(0d, Double.parseDouble(String.valueOf(cfg.get("highWinMinMultiplier")))); }
                    catch (Exception ignored) { remoteHighWinMinMultiplier = 10d; }
                    try { remoteHighWinMinCoins = Math.max(0L, Long.parseLong(String.valueOf(cfg.get("highWinMinCoins")))); }
                    catch (Exception ignored) { remoteHighWinMinCoins = 1000L; }
                    remoteGiftSendEnabled = !Boolean.FALSE.equals(gift.get("enabled"));
                    remoteLuckyNormalEnabled = !Boolean.FALSE.equals(normal.get("enabled"));
                    remoteLuckyReturnEnabled = !Boolean.FALSE.equals(returned.get("enabled"));
                    remoteLuckyHighEnabled = !Boolean.FALSE.equals(high.get("enabled"));
                    remoteLuckyRoomHighEnabled = !Boolean.FALSE.equals(roomHigh.get("enabled"));
                    remoteBackgroundUrl = stringValue(gift.get("backgroundUrl"), stringValue(cfg.get("backgroundUrl"), ""));
                    remoteShineEnabled = !Boolean.FALSE.equals(gift.getOrDefault("shineEnabled", cfg.get("shineEnabled")));
                    remoteFrameGlowEnabled = !Boolean.FALSE.equals(gift.getOrDefault("frameGlowEnabled", cfg.get("frameGlowEnabled")));
                    remoteDurationSeconds = intValue(gift.get("durationSeconds"), intValue(cfg.get("durationSeconds"), 3));
                    remoteGiftAvatarX = floatValue(gift.get("avatarX"), 8f);
                    remoteGiftAvatarY = floatValue(gift.get("avatarY"), 50f);
                    remoteGiftTextX = floatValue(gift.get("textX"), 25f);
                    remoteGiftTextY = floatValue(gift.get("textY"), 50f);
                    remoteGiftBodyX = floatValue(gift.get("bodyX"), 25f);
                    remoteGiftBodyY = floatValue(gift.get("bodyY"), 67f);
                    remoteGiftCountX = floatValue(gift.get("countX"), 88f);
                    remoteGiftCountY = floatValue(gift.get("countY"), 50f);
                    remoteGiftWidthDp = dimensionValue(gift.get("widthDp"), 340, 220, 360);
                    remoteGiftHeightDp = dimensionValue(gift.get("heightDp"), 100, 60, 180);
                    remoteGiftAvatarSizeDp = dimensionValue(gift.get("avatarSizeDp"), 44, 20, 76);
                    remoteGiftTitleSizeSp = dimensionValue(gift.get("titleSizeSp"), 13, 8, 30);
                    remoteGiftBodySizeSp = dimensionValue(gift.get("bodySizeSp"), 12, 8, 30);
                    remoteGiftCountSizeSp = dimensionValue(gift.get("countSizeSp"), 12, 8, 30);
                    remoteGiftPlacementX = floatValue(gift.get("placementX"), 50f);
                    remoteGiftPlacementY = floatValue(gift.get("placementY"), 16f);
                    remoteLuckyNormalBackgroundUrl = stringValue(normal.get("backgroundUrl"), "");
                    remoteLuckyReturnBackgroundUrl = stringValue(returned.get("backgroundUrl"), "");
                    remoteLuckyNormalPlacementX = floatValue(normal.get("placementX"), 50f);
                    remoteLuckyNormalPlacementY = floatValue(normal.get("placementY"), 16f);
                    remoteLuckyReturnPlacementX = floatValue(returned.get("placementX"), 50f);
                    remoteLuckyReturnPlacementY = floatValue(returned.get("placementY"), 32f);
                    remoteLuckyHighBackgroundUrl = stringValue(high.get("backgroundUrl"), "");
                    remoteLuckyRoomHighBackgroundUrl = stringValue(roomHigh.get("backgroundUrl"), "");
                    remoteLuckyNormalShineEnabled = !Boolean.FALSE.equals(normal.get("shineEnabled"));
                    remoteLuckyReturnShineEnabled = !Boolean.FALSE.equals(returned.get("shineEnabled"));
                    remoteLuckyHighShineEnabled = !Boolean.FALSE.equals(high.get("shineEnabled"));
                    remoteLuckyRoomHighShineEnabled = !Boolean.FALSE.equals(roomHigh.get("shineEnabled"));
                    remoteLuckyNormalGlowEnabled = !Boolean.FALSE.equals(normal.get("frameGlowEnabled"));
                    remoteLuckyReturnGlowEnabled = !Boolean.FALSE.equals(returned.get("frameGlowEnabled"));
                    remoteLuckyHighGlowEnabled = !Boolean.FALSE.equals(high.get("frameGlowEnabled"));
                    remoteLuckyRoomHighGlowEnabled = !Boolean.FALSE.equals(roomHigh.get("frameGlowEnabled"));
                    remoteLuckyNormalDurationSeconds = intValue(normal.get("durationSeconds"), 4);
                    remoteLuckyReturnDurationSeconds = intValue(returned.get("durationSeconds"), 3);
                    remoteLuckyHighDurationSeconds = intValue(high.get("durationSeconds"), 5);
                    remoteLuckyRoomHighDurationSeconds = intValue(roomHigh.get("durationSeconds"), 5);
                    remoteLuckyNormalAvatarX = floatValue(normal.get("avatarX"), 8f);
                    remoteLuckyNormalAvatarY = floatValue(normal.get("avatarY"), 50f);
                    remoteLuckyNormalTextX = floatValue(normal.get("textX"), 25f);
                    remoteLuckyNormalTextY = floatValue(normal.get("textY"), 42f);
                    remoteLuckyNormalBodyX = floatValue(normal.get("bodyX"), 25f);
                    remoteLuckyNormalBodyY = floatValue(normal.get("bodyY"), 62f);
                    remoteLuckyNormalCountX = floatValue(normal.get("countX"), 88f);
                    remoteLuckyNormalCountY = floatValue(normal.get("countY"), 50f);
                    remoteLuckyNormalWidthDp = dimensionValue(normal.get("widthDp"), 340, 220, 360);
                    remoteLuckyNormalHeightDp = dimensionValue(normal.get("heightDp"), 100, 60, 180);
                    remoteLuckyNormalAvatarSizeDp = dimensionValue(normal.get("avatarSizeDp"), 44, 20, 76);
                    remoteLuckyNormalTitleSizeSp = dimensionValue(normal.get("titleSizeSp"), 13, 8, 30);
                    remoteLuckyNormalBodySizeSp = dimensionValue(normal.get("bodySizeSp"), 12, 8, 30);
                    remoteLuckyNormalCountSizeSp = dimensionValue(normal.get("countSizeSp"), 12, 8, 30);
                    remoteLuckyReturnAvatarX = floatValue(returned.get("avatarX"), 8f);
                    remoteLuckyReturnAvatarY = floatValue(returned.get("avatarY"), 42f);
                    remoteLuckyReturnTextX = floatValue(returned.get("textX"), 25f);
                    remoteLuckyReturnTextY = floatValue(returned.get("textY"), 42f);
                    remoteLuckyReturnBodyX = floatValue(returned.get("bodyX"), 25f);
                    remoteLuckyReturnBodyY = floatValue(returned.get("bodyY"), 62f);
                    remoteLuckyReturnCountX = floatValue(returned.get("countX"), 88f);
                    remoteLuckyReturnCountY = floatValue(returned.get("countY"), 50f);
                    remoteLuckyReturnWidthDp = dimensionValue(returned.get("widthDp"), 340, 220, 360);
                    remoteLuckyReturnHeightDp = dimensionValue(returned.get("heightDp"), 100, 60, 180);
                    remoteLuckyReturnAvatarSizeDp = dimensionValue(returned.get("avatarSizeDp"), 44, 20, 76);
                    remoteLuckyReturnTitleSizeSp = dimensionValue(returned.get("titleSizeSp"), 13, 8, 30);
                    remoteLuckyReturnBodySizeSp = dimensionValue(returned.get("bodySizeSp"), 12, 8, 30);
                    remoteLuckyReturnCountSizeSp = dimensionValue(returned.get("countSizeSp"), 12, 8, 30);
                    remoteLuckyHighAvatarX = floatValue(high.get("avatarX"), 8f);
                    remoteLuckyRoomHighAvatarX = floatValue(roomHigh.get("avatarX"), 8f);
                    remoteLuckyHighAvatarY = floatValue(high.get("avatarY"), 50f);
                    remoteLuckyRoomHighAvatarY = floatValue(roomHigh.get("avatarY"), 50f);
                    remoteLuckyRoomHighPlacementX = floatValue(roomHigh.get("placementX"), 50f);
                    remoteLuckyRoomHighPlacementY = floatValue(roomHigh.get("placementY"), 50f);
                    remoteLuckyHighPlacementX = floatValue(high.get("placementX"), 50f);
                    remoteLuckyHighPlacementY = floatValue(high.get("placementY"), 16f);
                    remoteLuckyHighTextX = floatValue(high.get("textX"), 25f);
                    remoteLuckyRoomHighTextX = floatValue(roomHigh.get("textX"), 25f);
                    remoteLuckyHighTextY = floatValue(high.get("textY"), 42f);
                    remoteLuckyRoomHighTextY = floatValue(roomHigh.get("textY"), 42f);
                    remoteLuckyHighBodyX = floatValue(high.get("bodyX"), 25f);
                    remoteLuckyRoomHighBodyX = floatValue(roomHigh.get("bodyX"), 25f);
                    remoteLuckyHighBodyY = floatValue(high.get("bodyY"), 62f);
                    remoteLuckyRoomHighBodyY = floatValue(roomHigh.get("bodyY"), 62f);
                    remoteLuckyHighCountX = floatValue(high.get("countX"), 88f);
                    remoteLuckyRoomHighCountX = floatValue(roomHigh.get("countX"), 88f);
                    remoteLuckyHighCountY = floatValue(high.get("countY"), 50f);
                    remoteLuckyRoomHighCountY = floatValue(roomHigh.get("countY"), 50f);
                    remoteLuckyHighWidthDp = dimensionValue(high.get("widthDp"), 340, 220, 360);
                    remoteLuckyRoomHighWidthDp = dimensionValue(roomHigh.get("widthDp"), 340, 220, 360);
                    remoteLuckyHighHeightDp = dimensionValue(high.get("heightDp"), 100, 60, 180);
                    remoteLuckyRoomHighHeightDp = dimensionValue(roomHigh.get("heightDp"), 100, 60, 180);
                    remoteLuckyHighAvatarSizeDp = dimensionValue(high.get("avatarSizeDp"), 44, 20, 76);
                    remoteLuckyRoomHighAvatarSizeDp = dimensionValue(roomHigh.get("avatarSizeDp"), 44, 20, 76);
                    remoteLuckyHighTitleSizeSp = dimensionValue(high.get("titleSizeSp"), 13, 8, 30);
                    remoteLuckyRoomHighTitleSizeSp = dimensionValue(roomHigh.get("titleSizeSp"), 13, 8, 30);
                    remoteLuckyHighBodySizeSp = dimensionValue(high.get("bodySizeSp"), 12, 8, 30);
                    remoteLuckyRoomHighBodySizeSp = dimensionValue(roomHigh.get("bodySizeSp"), 12, 8, 30);
                    remoteLuckyHighCountSizeSp = dimensionValue(high.get("countSizeSp"), 12, 8, 30);
                    remoteLuckyRoomHighCountSizeSp = dimensionValue(roomHigh.get("countSizeSp"), 12, 8, 30);
                    MAIN.post(GlobalCelebrationToast::applyRemoteStyleToActiveToast);
                }
                @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable error) {
                    // Keep the last known style and native fallback while the free host sleeps.
                }
            });
        } catch (Exception ignored) {
            // Network settings are optional; toast display must never depend on them.
        }
    }

    /** Uses the same OR thresholds saved in the dashboard and enforced by the backend. */
    public static boolean isHighWin(double multiplier, long coinsWon) {
        boolean multiplierReached = remoteHighWinMinMultiplier > 0d && multiplier >= remoteHighWinMinMultiplier;
        boolean coinsReached = remoteHighWinMinCoins > 0L && coinsWon >= remoteHighWinMinCoins;
        return multiplierReached || coinsReached;
    }

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey
    ) {
        show(activity, title, body, avatarUrl, badgeUrl, dedupeKey, 28, 0, 0L, null, null, null);
    }

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            int topMarginDp
    ) {
        show(activity, title, body, avatarUrl, badgeUrl, dedupeKey, topMarginDp, 0, 0L, null, null, null);
    }

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            int topMarginDp,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind
    ) {
        show(activity, title, body, avatarUrl, badgeUrl, dedupeKey,
                topMarginDp, multiplier, coinsWon, displayName, kind, null);
    }

    public static void show(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            int topMarginDp,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId
    ) {
        // Lucky result toasts are dashboard-configured and must render on both home and room paths.
        // Never render the retired gift-send confirmation toast, regardless of caller.
        if ("gift_send".equalsIgnoreCase(kind)) return;
        if (activity == null || activity.isFinishing()) return;
        try {
            android.content.Context app = activity.getApplicationContext();
            if (app instanceof com.Dramizo.Series.AuraLiveApp aura
                    && aura.getContainer().getSessionManager().isMuteCelebrationPopups()) {
                return;
            }
        } catch (Exception ignored) {
        }
        enqueueToast(activity, title, body, avatarUrl, badgeUrl, dedupeKey, topMarginDp,
                multiplier, coinsWon, displayName, kind, roomId, false);
    }

    /** Shows the chest burst independently of optional celebration banners/settings. */
    public static void showSupportChestBurst(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        MAIN.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root instanceof FrameLayout) showChestBurst(activity, (FrameLayout) root);
        });
    }

    /** Room-only presentation for large lucky wins: same configurable artwork, centered on screen. */
    public static void showRoomCenter(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId
    ) {
        if ("gift_send".equalsIgnoreCase(kind)) return;
        enqueueToast(activity, title, body, avatarUrl, badgeUrl, dedupeKey, 28,
                multiplier, coinsWon, displayName, kind, roomId, true);
    }

    /** Room-wide top banner for chest openings; matches the normal high-return toast lane. */
    public static void showRoomTop(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId
    ) {
        if ("gift_send".equalsIgnoreCase(kind)) return;
        enqueueToast(activity, title, body, avatarUrl, badgeUrl, dedupeKey, 72,
                multiplier, coinsWon, displayName, kind, roomId, false);
    }

    private static void enqueueToast(
            @Nullable Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            int topMarginDp,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId,
            boolean centerPlacement
    ) {
        if ("gift_send".equalsIgnoreCase(kind)) return;
        if (activity == null || activity.isFinishing()) return;
        try {
            android.content.Context app = activity.getApplicationContext();
            if (app instanceof com.Dramizo.Series.AuraLiveApp aura
                    && aura.getContainer().getSessionManager().isMuteCelebrationPopups()) {
                return;
            }
        } catch (Exception ignored) {
        }
        if (isLuckyToastKind(kind)) refreshToastStyle(activity);
        if (!isToastEnabled(kind, centerPlacement, multiplier, coinsWon, body)) return;
        long now = System.currentTimeMillis();
        // Keep a separate dedupe lane per toast kind. A normal room win, a public
        // high-win toast and the sender-only personal toast may all describe the same
        // reward event and must still be allowed to appear together.
        if (dedupeKey != null && !dedupeKey.trim().isEmpty()) {
            RECENT_DEDUPE.entrySet().removeIf(entry -> now - entry.getValue().shownAt >= 1_200L);
            String kindKey = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
            String scopedKey = dedupeKey + "|" + kindKey;
            int placementBit = centerPlacement ? 2 : 1;
            DedupeState state = RECENT_DEDUPE.get(scopedKey);
            if (state != null && (state.placementMask & placementBit) != 0) return;
            if (state == null) {
                state = new DedupeState();
                RECENT_DEDUPE.put(scopedKey, state);
            }
            state.shownAt = now;
            state.placementMask |= placementBit;
            while (RECENT_DEDUPE.size() > 128) {
                String oldest = RECENT_DEDUPE.keySet().iterator().next();
                RECENT_DEDUPE.remove(oldest);
            }
        }
        final int margin = Math.max(8, topMarginDp);
        ToastRequest request = new ToastRequest(activity, title, body, avatarUrl, badgeUrl,
                margin, multiplier, coinsWon, displayName, kind, roomId, centerPlacement);
        MAIN.post(() -> {
            // Each toast type owns a fixed screen slot and its own FIFO. Different
            // types can coexist; repeated events of one type wait for that slot to clear.
            String laneKey = toastLaneKey(request.kind, request.centerPlacement);
            ArrayDeque<ToastRequest> lane = PENDING_BY_LANE.computeIfAbsent(
                    laneKey, ignored -> new ArrayDeque<>());
            lane.addLast(request);
            showNext(laneKey);
        });
    }

    private static String toastLaneKey(@Nullable String kind, boolean personal) {
        String normalized = kind == null ? "default" : kind.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) normalized = "default";
        return normalized + (personal ? "|personal" : "|room");
    }

    private static void showNext(String laneKey) {
        if (laneKey == null || ACTIVE_LANES.contains(laneKey)) return;
        ArrayDeque<ToastRequest> lane = PENDING_BY_LANE.get(laneKey);
        ToastRequest request = lane != null ? lane.pollFirst() : null;
        if (request == null) return;
        Activity activity = com.Dramizo.Series.AuraLiveApp.getForegroundActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            activity = request.activity.get();
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            lane.addFirst(request);
            MAIN.postDelayed(() -> showNext(laneKey), 160L);
            return;
        }
        ACTIVE_LANES.add(laneKey);
        try {
            showInternal(activity, request.title, request.body, request.avatarUrl,
                    request.badgeUrl, request.topMarginDp, request.multiplier, request.coinsWon,
                    request.displayName, request.kind, request.roomId, request.centerPlacement, laneKey);
        } catch (Throwable error) {
            ACTIVE_LANES.remove(laneKey);
            android.util.Log.w("GlobalCelebrationToast", "Toast rendering failed", error);
            MAIN.post(() -> showNext(laneKey));
        }
    }

    private static void showInternal(
            Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            int topMarginDp,
            double multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId,
            boolean centerPlacement
            , @NonNull String laneKey
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;

        // Do not replace an already visible toast. Every event receives its own view.
        View banner = LayoutInflater.from(activity)
                .inflate(R.layout.layout_celebration_toast, root, false);
        ImageView avatar = banner.findViewById(R.id.imgCelebrationAvatar);
        ImageView badge = banner.findViewById(R.id.imgCelebrationBadge);
        TextView tvTitle = banner.findViewById(R.id.tvCelebrationTitle);
        TextView tvBody = banner.findViewById(R.id.tvCelebrationBody);
        if (centerPlacement && avatar != null) {
            // The center reward toast is a personal text result, not a profile card.
            View avatarSlot = (View) avatar.getParent();
            if (avatarSlot != null) avatarSlot.setVisibility(View.GONE);
            avatar.setVisibility(View.GONE);
            if (tvTitle != null && tvTitle.getParent() instanceof View) {
                View titleColumn = (View) tvTitle.getParent();
                ViewGroup.LayoutParams raw = titleColumn.getLayoutParams();
                if (raw instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                    lp.setMarginStart(0);
                    titleColumn.setLayoutParams(lp);
                }
            }
        }
        View wrapTimes = banner.findViewById(R.id.wrapCelebrationTimes);
        TextView tvTimes = banner.findViewById(R.id.tvCelebrationTimes);
        TextView btnGo = banner.findViewById(R.id.btnCelebrationGo);
        ImageView toastBackground = banner.findViewById(R.id.imgToastBackground);
        View sheen = banner.findViewById(R.id.viewCelebrationSheen);

        boolean planet = kind != null && "planet_summon".equalsIgnoreCase(kind);
        boolean supportChest = kind != null && "support_chest".equalsIgnoreCase(kind);
        Map<String, Object> chestToast = supportChest ? supportChestToastConfig : null;
        if (supportChest && avatar != null) {
            int giftSize = dp(activity, Math.max(24, Math.min(120,
                    (int) numberConfig(supportChestToastConfig, "giftSizeDp", 58))));
            ViewGroup.LayoutParams imageParams = avatar.getLayoutParams();
            if (imageParams != null) {
                imageParams.width = giftSize;
                imageParams.height = giftSize;
                avatar.setLayoutParams(imageParams);
            }
        }
        boolean gameOrLucky = kind != null && (
                "game_win".equalsIgnoreCase(kind) || isLuckyToastKind(kind));
        boolean giftSend = kind != null && "gift_send".equalsIgnoreCase(kind);
        boolean luckyWin = isLuckyToastKind(kind);
        // Different send paths phrase partial returns differently ("مردود +..." vs
        // "مردود جزئي ..."). Classify by the outcome, not one exact UI string, so the
        // dashboard's dedicated return-toast background/style is used on every client.
        boolean explicitReturn = kind != null && ("lucky_hit_return".equalsIgnoreCase(kind)
                || "lucky_return".equalsIgnoreCase(kind));
        boolean explicitHigh = kind != null && ("lucky_hit_high".equalsIgnoreCase(kind)
                || "lucky_high".equalsIgnoreCase(kind));
        boolean explicitNormal = kind != null && "lucky_hit_normal".equalsIgnoreCase(kind);
        boolean luckyReturn = luckyWin && (explicitReturn || (!explicitHigh && !explicitNormal
                && (multiplier <= 0d || (body != null
                && (body.contains("مردود جزئي") || body.contains("مردود +")
                || body.contains("مردود الهدية"))))));
        boolean luckyHigh = luckyWin && !luckyReturn
                && (explicitHigh || multiplier >= 10d || coinsWon >= 1000L);
        boolean luckyRoomHigh = luckyHigh && centerPlacement;
        String toastKindTag = supportChest ? "support_chest" : giftSend ? "gift_send" : luckyWin ? (luckyRoomHigh ? "lucky_hit_high_room" : luckyReturn ? "lucky_hit_return" : luckyHigh ? "lucky_hit_high" : "lucky_hit_normal") : null;
        banner.setTag(toastKindTag);
        boolean customToastStyle = giftSend || luckyWin || supportChest;
        boolean remoteShine = !supportChest && (giftSend ? remoteShineEnabled : luckyRoomHigh ? remoteLuckyRoomHighShineEnabled : luckyHigh ? remoteLuckyHighShineEnabled : luckyReturn ? remoteLuckyReturnShineEnabled : remoteLuckyNormalShineEnabled);
        boolean remoteGlow = !supportChest && (giftSend ? remoteFrameGlowEnabled : luckyRoomHigh ? remoteLuckyRoomHighGlowEnabled : luckyHigh ? remoteLuckyHighGlowEnabled : luckyReturn ? remoteLuckyReturnGlowEnabled : remoteLuckyNormalGlowEnabled);
        int customDuration = supportChest ? 6 : giftSend ? remoteDurationSeconds : luckyRoomHigh ? remoteLuckyRoomHighDurationSeconds : luckyHigh ? remoteLuckyHighDurationSeconds : luckyReturn ? remoteLuckyReturnDurationSeconds : remoteLuckyNormalDurationSeconds;
        if (planet) {
            banner.setBackgroundResource(R.drawable.bg_planet_go_banner);
        }
        if (supportChest) {
            GradientDrawable supportChestBackground = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[] { 0xF52A1B12, 0xF04A321B, 0xF51C1B2B });
            supportChestBackground.setCornerRadius(dp(activity, 18));
            supportChestBackground.setStroke(dp(activity, 2), 0xFFFFD36A);
            banner.setBackground(supportChestBackground);
            banner.setElevation(dp(activity, 24));
        }

        String winnerName = firstNonEmpty(displayName, title);
        if (tvTitle != null) {
            boolean showChestWinner = !supportChest || chestToast == null
                    || !Boolean.FALSE.equals(chestToast.get("showWinnerName"));
            tvTitle.setVisibility(showChestWinner ? View.VISIBLE : View.GONE);
            tvTitle.setText(winnerName != null ? winnerName : "مبروك الفوز!");
            tvTitle.setSelected(true);
        }
        String line;
        if (gameOrLucky && coinsWon > 0L) {
            if (luckyWin && body != null && body.contains("مردود جزئي")) {
                line = "↩ مردود جزئي · " + String.format(Locale.US, "%,d", coinsWon) + " كوينز";
            } else {
                line = "🪙 ربح " + String.format(Locale.US, "%,d", coinsWon) + " كوينز";
            }
        } else {
            line = buildMikooLine(kind, displayName, body, coinsWon, multiplier);
        }
        if (tvBody != null) {
            tvBody.setText(line);
            tvBody.setSelected(true);
        }
        if (customToastStyle) {
            float titleSize = supportChest ? numberConfig(chestToast, "titleSizeSp", 17f) : giftSend ? remoteGiftTitleSizeSp : luckyRoomHigh ? remoteLuckyRoomHighTitleSizeSp : luckyHigh ? remoteLuckyHighTitleSizeSp : luckyReturn ? remoteLuckyReturnTitleSizeSp : remoteLuckyNormalTitleSizeSp;
            float bodySize = supportChest ? numberConfig(chestToast, "bodySizeSp", 14f) : giftSend ? remoteGiftBodySizeSp : luckyRoomHigh ? remoteLuckyRoomHighBodySizeSp : luckyHigh ? remoteLuckyHighBodySizeSp : luckyReturn ? remoteLuckyReturnBodySizeSp : remoteLuckyNormalBodySizeSp;
            float countSize = supportChest ? 12f : giftSend ? remoteGiftCountSizeSp : luckyRoomHigh ? remoteLuckyRoomHighCountSizeSp : luckyHigh ? remoteLuckyHighCountSizeSp : luckyReturn ? remoteLuckyReturnCountSizeSp : remoteLuckyNormalCountSizeSp;
            if (tvTitle != null) tvTitle.setTextSize(titleSize);
            if (tvBody != null) tvBody.setTextSize(bodySize);
            if (tvTimes != null) tvTimes.setTextSize(countSize);
        }

        // Lucky/game wins prioritize the actual winner's portrait; use the gift/game icon only as fallback.
        String leftUrl = firstNonEmpty(avatarUrl, badgeUrl);
        String absLeft = AssetCatalog.absoluteUrl(leftUrl);
        // If the profile URL is malformed, try the secondary image rather than skipping both.
        if ((absLeft == null || absLeft.isEmpty()) && badgeUrl != null && !badgeUrl.trim().isEmpty()) {
            absLeft = AssetCatalog.absoluteUrl(badgeUrl);
        }
        if (absLeft != null) {
            com.Dramizo.Series.util.CosmeticMedia.Kind badgeKind =
                    com.Dramizo.Series.util.CosmeticMedia.kind(absLeft);
            if (badgeKind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                    || badgeKind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA) {
                absLeft = null;
            }
        }
        // Winner-only center result is text-only; do not request or load any profile image.
        if (avatar != null && !centerPlacement) {
            if (absLeft != null && !absLeft.isEmpty()) {
                try {
                    Glide.with(activity)
                            .load(absLeft)
                            .override(128, 128)
                            .transform(new CircleCrop())
                            .placeholder(gameOrLucky || planet
                                    ? R.drawable.ic_screen_chat_lottery : R.drawable.jeho_logo)
                            .error(gameOrLucky || planet
                                    ? R.drawable.ic_screen_chat_lottery : R.drawable.jeho_logo)
                            .dontAnimate()
                            .into(avatar);
                } catch (Exception ignored) {
                    avatar.setImageResource(R.drawable.ic_screen_chat_lottery);
                }
            } else {
                avatar.setImageResource(gameOrLucky || planet
                        ? R.drawable.ic_screen_chat_lottery
                        : R.drawable.jeho_logo);
            }
        }

        // Never show a "Go" chip on home luck/game toast — optional room open via banner tap.
        if (btnGo != null) btnGo.setVisibility(View.GONE);
        boolean canOpenRoom = roomId != null && !roomId.trim().isEmpty();
        if (canOpenRoom) {
            final String targetRoom = roomId.trim();
            banner.setOnClickListener(v -> {
                removeBanner(banner, centerPlacement);
                RoomJoinGateActivity.open(activity, targetRoom);
            });
        } else {
            banner.setOnClickListener(null);
            banner.setClickable(false);
        }

        int displayMultiplier = giftSend ? Math.max(1, (int) Math.round(multiplier)) : (int) Math.max(0, Math.round(multiplier));
        boolean showTimes = giftSend ? displayMultiplier >= 1 : gameOrLucky ? multiplier > 0d : displayMultiplier >= 2;
        if (wrapTimes != null && tvTimes != null && showTimes) {
            wrapTimes.setVisibility(View.VISIBLE);
            wrapTimes.setScaleX(0.82f);
            wrapTimes.setScaleY(0.82f);
            if (giftSend && displayMultiplier > 1) {
                tvTimes.setText("X1");
                ValueAnimator countUp = ValueAnimator.ofInt(1, displayMultiplier);
                countUp.setDuration(Math.min(1200L, Math.max(350L, displayMultiplier * 8L)));
                countUp.addUpdateListener(animation ->
                        tvTimes.setText(String.format(Locale.US, "X%d", (Integer) animation.getAnimatedValue())));
                countUp.start();
            } else {
                tvTimes.setText(giftSend
                        ? String.format(Locale.US, "X%d", displayMultiplier)
                        : "×" + formatMultiplier(multiplier));
            }
            if (badge != null) {
                if (giftSend) {
                    String giftIcon = AssetCatalog.absoluteUrl(badgeUrl);
                    if (giftIcon != null && !giftIcon.isEmpty()) {
                        badge.setVisibility(View.VISIBLE);
                        try { Glide.with(activity.getApplicationContext()).load(giftIcon).centerCrop().into(badge); }
                        catch (Exception ignored) { badge.setVisibility(View.GONE); }
                    } else badge.setVisibility(View.GONE);
                } else badge.setVisibility(View.GONE);
            }
        } else {
            if (wrapTimes != null) wrapTimes.setVisibility(View.GONE);
            // Game/luck already put badge on the left icon — hide trailing personal badge.
            String absBadge = gameOrLucky ? null : AssetCatalog.absoluteUrl(badgeUrl);
            if (badge != null) {
                if (absBadge != null && !absBadge.isEmpty()) {
                    badge.setVisibility(View.VISIBLE);
                    try {
                        Glide.with(activity.getApplicationContext())
                                .load(absBadge)
                                .centerCrop()
                                .into(badge);
                    } catch (Exception ignored) {
                        badge.setVisibility(View.GONE);
                    }
                } else {
                    badge.setVisibility(View.GONE);
                }
            }
        }

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        final int requestedWidthDp = giftSend ? remoteGiftWidthDp
                : luckyRoomHigh ? remoteLuckyRoomHighWidthDp : luckyHigh ? remoteLuckyHighWidthDp
                : luckyReturn ? remoteLuckyReturnWidthDp : remoteLuckyNormalWidthDp;
        final int requestedHeightDp = giftSend ? remoteGiftHeightDp
                : luckyRoomHigh ? remoteLuckyRoomHighHeightDp : luckyHigh ? remoteLuckyHighHeightDp
                : luckyReturn ? remoteLuckyReturnHeightDp : remoteLuckyNormalHeightDp;
        int maxToastWidth = Math.max(dp(activity, 220), screenWidth - dp(activity, 24));
        int toastWidth = Math.min(dp(activity, supportChest ? 360 : Math.max(220, Math.min(360, requestedWidthDp))), maxToastWidth);
        int toastHeight = customToastStyle
                ? dp(activity, supportChest
                    ? Math.max(60, Math.min(240, (int) numberConfig(chestToast, "height", 104)))
                    : Math.max(60, Math.min(180, requestedHeightDp)))
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        // Keep the text elements content-sized like the dashboard's draggable preview.
        // Full-width TextViews cannot be positioned by their left-edge percentage.
        int textMaxWidth = Math.max(dp(activity, 84), Math.round(toastWidth * 0.66f));
        if (tvTitle != null) tvTitle.setMaxWidth(textMaxWidth);
        if (tvBody != null) tvBody.setMaxWidth(textMaxWidth);
        if (avatar != null) {
            avatar.setBackgroundResource(R.drawable.bg_notification_avatar);
            avatar.setClipToOutline(true);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(toastWidth, toastHeight);
        if (centerPlacement || luckyWin || giftSend) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            final float rawX = supportChest ? 50f : giftSend ? remoteGiftPlacementX : luckyRoomHigh ? remoteLuckyRoomHighPlacementX
                    : luckyHigh ? remoteLuckyHighPlacementX
                    : luckyReturn ? remoteLuckyReturnPlacementX : remoteLuckyNormalPlacementX;
            final float rawY = supportChest ? 50f : giftSend ? remoteGiftPlacementY : luckyRoomHigh ? remoteLuckyRoomHighPlacementY
                    : luckyHigh ? remoteLuckyHighPlacementY
                    : luckyReturn ? remoteLuckyReturnPlacementY : remoteLuckyNormalPlacementY;
            final float placementX = Math.max(0f, Math.min(100f, rawX)) / 100f;
            final float placementY = Math.max(0f, Math.min(100f, rawY)) / 100f;
            banner.post(() -> {
                if (banner.getParent() == null) return;
                int maxLeft = Math.max(0, root.getWidth() - banner.getWidth());
                int maxTop = Math.max(0, root.getHeight() - banner.getHeight());
                int left = Math.round(root.getWidth() * placementX - banner.getWidth() / 2f);
                int top = Math.round(root.getHeight() * placementY - banner.getHeight() / 2f);
                FrameLayout.LayoutParams positioned = (FrameLayout.LayoutParams) banner.getLayoutParams();
                positioned.leftMargin = Math.max(0, Math.min(maxLeft, left));
                positioned.topMargin = Math.max(0, Math.min(maxTop, top));
                banner.setLayoutParams(positioned);
            });
        } else {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(activity, supportChest
                    ? Math.max(0, Math.min(300, (int) numberConfig(chestToast, "yOffset", topMarginDp)))
                    : topMarginDp);
        }
        // A lane has exactly one visible toast. Different toast types use their
        // own dashboard X/Y coordinates and never push one another up or down.
        banner.setTag("jeho-toast-lane|" + laneKey);
        banner.setLayoutParams(lp);
        banner.setTranslationY(0f);
        banner.setAlpha(0f);
        banner.setScaleX(0.985f);
        banner.setScaleY(0.985f);
        banner.setTranslationX(dp(activity, 140));
        if (toastBackground != null) {
            String selectedBackground = supportChest
                    ? (chestToast == null ? "" : String.valueOf(chestToast.getOrDefault("backgroundUrl", "")))
                    : giftSend ? remoteBackgroundUrl : luckyRoomHigh ? remoteLuckyRoomHighBackgroundUrl : luckyHigh ? remoteLuckyHighBackgroundUrl : luckyReturn ? remoteLuckyReturnBackgroundUrl : remoteLuckyNormalBackgroundUrl;
            String imageUrl = customToastStyle ? toastBackgroundUrl(selectedBackground) : null;
            if (imageUrl != null && !imageUrl.isEmpty()) {
                // The uploaded artwork owns its pixels, edges and alpha channel. Do not add
                // the built-in rounded frame behind a transparent PNG/WebP design.
                banner.setBackground(null);
                toastBackground.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                toastBackground.setScaleType(ImageView.ScaleType.FIT_XY);
                toastBackground.setVisibility(View.VISIBLE);
                try { Glide.with(activity.getApplicationContext()).load(imageUrl).dontTransform().into(toastBackground); }
                catch (Exception ignored) { toastBackground.setVisibility(View.GONE); }
            } else {
                toastBackground.setVisibility(View.GONE);
            }
        }
        banner.setElevation(dp(activity, supportChest ? 28 : customToastStyle && remoteGlow ? 20 : 14));
        if (sheen != null && customToastStyle && remoteShine) {
            sheen.setVisibility(View.VISIBLE);
            GradientDrawable shine = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] { 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF });
            sheen.setBackground(shine);
            sheen.setRotation(12f);
            sheen.setAlpha(0.78f);
        } else if (sheen != null) sheen.setVisibility(View.GONE);
        root.addView(banner);
        if (centerPlacement) currentCenter = new WeakReference<>(banner);
        else current = new WeakReference<>(banner);
        // Keep this lane occupied until this exact banner is removed.

        banner.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        if (sheen != null && sheen.getVisibility() == View.VISIBLE) {
            banner.post(() -> {
                if (sheen.getParent() != banner || banner.getParent() == null) return;
                ObjectAnimator sweep = ObjectAnimator.ofFloat(
                        sheen, View.TRANSLATION_X, -dp(activity, 52), banner.getWidth() + dp(activity, 52));
                sweep.setDuration(1250L);
                sweep.setRepeatCount(1);
                sweep.setInterpolator(new LinearInterpolator());
                sweep.start();
            });
        }
        if (wrapTimes != null && wrapTimes.getVisibility() == View.VISIBLE) {
            wrapTimes.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        }
        if (customToastStyle) {
            final float avatarX = supportChest ? 10f : giftSend ? remoteGiftAvatarX : luckyRoomHigh ? remoteLuckyRoomHighAvatarX : luckyHigh ? remoteLuckyHighAvatarX : luckyReturn ? remoteLuckyReturnAvatarX : remoteLuckyNormalAvatarX;
            final float avatarY = supportChest ? 50f : giftSend ? remoteGiftAvatarY : luckyRoomHigh ? remoteLuckyRoomHighAvatarY : luckyHigh ? remoteLuckyHighAvatarY : luckyReturn ? remoteLuckyReturnAvatarY : remoteLuckyNormalAvatarY;
            final float textX = supportChest ? 28f : giftSend ? remoteGiftTextX : luckyRoomHigh ? remoteLuckyRoomHighTextX : luckyHigh ? remoteLuckyHighTextX : luckyReturn ? remoteLuckyReturnTextX : remoteLuckyNormalTextX;
            final float textY = supportChest ? 38f : giftSend ? remoteGiftTextY : luckyRoomHigh ? remoteLuckyRoomHighTextY : luckyHigh ? remoteLuckyHighTextY : luckyReturn ? remoteLuckyReturnTextY : remoteLuckyNormalTextY;
            final float bodyX = supportChest ? 28f : giftSend ? remoteGiftBodyX : luckyRoomHigh ? remoteLuckyRoomHighBodyX : luckyHigh ? remoteLuckyHighBodyX : luckyReturn ? remoteLuckyReturnBodyX : remoteLuckyNormalBodyX;
            final float bodyY = supportChest ? 65f : giftSend ? remoteGiftBodyY : luckyRoomHigh ? remoteLuckyRoomHighBodyY : luckyHigh ? remoteLuckyHighBodyY : luckyReturn ? remoteLuckyReturnBodyY : remoteLuckyNormalBodyY;
            final float countX = giftSend ? remoteGiftCountX : luckyRoomHigh ? remoteLuckyRoomHighCountX : luckyHigh ? remoteLuckyHighCountX : luckyReturn ? remoteLuckyReturnCountX : remoteLuckyNormalCountX;
            final float countY = giftSend ? remoteGiftCountY : luckyRoomHigh ? remoteLuckyRoomHighCountY : luckyHigh ? remoteLuckyHighCountY : luckyReturn ? remoteLuckyReturnCountY : remoteLuckyNormalCountY;
            final int avatarSize = supportChest ? 56 : giftSend ? remoteGiftAvatarSizeDp : luckyRoomHigh ? remoteLuckyRoomHighAvatarSizeDp : luckyHigh ? remoteLuckyHighAvatarSizeDp : luckyReturn ? remoteLuckyReturnAvatarSizeDp : remoteLuckyNormalAvatarSizeDp;
            resizeAvatar(activity, avatar, avatarSize);
            banner.post(() -> {
                if (banner.getParent() == null) return;
                positionViewPercent(avatar, banner, avatarX, avatarY, true);
                positionViewPercent(tvTitle, banner, textX, textY, false);
                positionViewPercent(tvBody, banner, bodyX, bodyY, false);
                positionViewPercent(wrapTimes, banner, countX, countY, true);
            });
        }
        Runnable laneHideRunnable = () -> removeBanner(banner, centerPlacement);
        ACTIVE_HIDE_TASKS.put(banner, laneHideRunnable);
        MAIN.postDelayed(laneHideRunnable, customToastStyle
                ? Math.max(1, Math.min(12, customDuration)) * 1000L
                : (canOpenRoom ? 6200L : 5200L));
    }

    /** Room-wide reward burst for a completed support-chest level. */
    private static void showChestBurst(Activity activity, FrameLayout root) {
        if (activity == null || root == null || activity.isFinishing() || activity.isDestroyed()) return;

        final FrameLayout celebration = new FrameLayout(activity);
        celebration.setClipChildren(false);
        celebration.setClipToPadding(false);
        celebration.setClickable(false);
        celebration.setFocusable(false);
        root.addView(celebration, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final int width = Math.max(root.getWidth(), activity.getResources().getDisplayMetrics().widthPixels);
        final int height = Math.max(root.getHeight(), activity.getResources().getDisplayMetrics().heightPixels);
        final int dp8 = dp(activity, 8);

        // A bright impact flash and expanding gold shockwaves.
        View flash = new View(activity);
        GradientDrawable flashShape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { 0x00FFF4C5, 0x88FFE28A, 0x00FF9D36 });
        flashShape.setShape(GradientDrawable.OVAL);
        flash.setBackground(flashShape);
        FrameLayout.LayoutParams flashParams = new FrameLayout.LayoutParams(
                dp(activity, 190), dp(activity, 190), Gravity.CENTER);
        celebration.addView(flash, flashParams);
        AnimatorSet flashAnim = new AnimatorSet();
        flashAnim.playTogether(
                ObjectAnimator.ofFloat(flash, View.ALPHA, 0f, 0.95f, 0f),
                ObjectAnimator.ofFloat(flash, View.SCALE_X, 0.12f, 2.35f),
                ObjectAnimator.ofFloat(flash, View.SCALE_Y, 0.12f, 2.35f));
        flashAnim.setDuration(1450L);
        flashAnim.start();

        for (int ringIndex = 0; ringIndex < 4; ringIndex++) {
            View ring = new View(activity);
            GradientDrawable ringShape = new GradientDrawable();
            ringShape.setShape(GradientDrawable.OVAL);
            ringShape.setColor(0x00FFFFFF);
            ringShape.setStroke(dp(activity, ringIndex == 0 ? 4 : 2),
                    ringIndex == 1 ? 0xFFFFA94B : 0xFFFFE18A);
            ring.setBackground(ringShape);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(
                    dp(activity, 112), dp(activity, 112), Gravity.CENTER);
            celebration.addView(ring, ringParams);
            AnimatorSet ringAnim = new AnimatorSet();
            ringAnim.playTogether(
                    ObjectAnimator.ofFloat(ring, View.ALPHA, 0.95f, 0f),
                    ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.15f, 2.9f),
                    ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.15f, 2.9f));
            ringAnim.setStartDelay(ringIndex * 220L);
            ringAnim.setDuration(1450L + ringIndex * 180L);
            ringAnim.start();
        }

        // Golden coin and diamond rain falls from the top of every viewer's screen.
        final String[] rainSymbols = { "🪙", "🪙", "💎", "🪙", "✨", "🪙", "🎁", "🪙" };
        for (int i = 0; i < 42; i++) {
            TextView coin = new TextView(activity);
            coin.setText(rainSymbols[i % rainSymbols.length]);
            coin.setTextSize(i % 5 == 0 ? 27f : 22f);
            coin.setGravity(Gravity.CENTER);
            coin.setClickable(false);
            coin.setFocusable(false);
            FrameLayout.LayoutParams coinParams = new FrameLayout.LayoutParams(
                    dp(activity, 38), dp(activity, 42), Gravity.TOP | Gravity.LEFT);
            coinParams.leftMargin = Math.max(0, Math.min(width - dp(activity, 38),
                    (int) ((i * (width / 32f)) + ((i % 3) - 1) * dp8)));
            celebration.addView(coin, coinParams);
            coin.setTranslationY(-dp(activity, 35 + (i % 5) * 34));
            coin.setRotation((i % 2 == 0 ? -1f : 1f) * (i % 4) * 18f);
            AnimatorSet rain = new AnimatorSet();
            rain.playTogether(
                    ObjectAnimator.ofFloat(coin, View.TRANSLATION_Y, coin.getTranslationY(),
                            height + dp(activity, 70 + (i % 4) * 30)),
                    ObjectAnimator.ofFloat(coin, View.TRANSLATION_X, 0f,
                            (i % 2 == 0 ? 1f : -1f) * dp(activity, 22 + (i % 5) * 12)),
                    ObjectAnimator.ofFloat(coin, View.ROTATION, coin.getRotation(),
                            coin.getRotation() + (i % 2 == 0 ? 420f : -420f)),
                    ObjectAnimator.ofFloat(coin, View.ALPHA, 0f, 1f, 1f, 0f));
            rain.setStartDelay((i % 8) * 110L);
            rain.setDuration(2500L + (i % 4) * 240L);
            rain.start();
        }

        // Radial reward burst: gift boxes, coins, diamonds and light particles.
        final String[] symbols = { "🎁", "🪙", "💎", "✨", "🌟", "🪙", "💎", "🎁",
                "✨", "🪙", "💎", "🌟", "🎁", "🪙", "💎", "✨", "🪙", "🎁", "💎", "🌟",
                "🪙", "💎", "🎁", "✨" };
        for (int i = 0; i < symbols.length; i++) {
            TextView particle = new TextView(activity);
            particle.setText(symbols[i]);
            particle.setTextSize(23f + (i % 3) * 4f);
            particle.setGravity(Gravity.CENTER);
            particle.setClickable(false);
            particle.setFocusable(false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            celebration.addView(particle, params);
            double angle = (Math.PI * 2d * i) / symbols.length;
            float distance = dp(activity, 115 + (i % 4) * 34);
            float dx = (float) Math.cos(angle) * distance;
            float dy = (float) Math.sin(angle) * distance;
            AnimatorSet burst = new AnimatorSet();
            burst.playTogether(
                    ObjectAnimator.ofFloat(particle, View.TRANSLATION_X, 0f, dx),
                    ObjectAnimator.ofFloat(particle, View.TRANSLATION_Y, 0f, dy),
                    ObjectAnimator.ofFloat(particle, View.ROTATION, 0f,
                            (i % 2 == 0 ? 1f : -1f) * 330f),
                    ObjectAnimator.ofFloat(particle, View.ALPHA, 0f, 1f, 0f),
                    ObjectAnimator.ofFloat(particle, View.SCALE_X, 0.2f, 1.25f, 0.7f),
                    ObjectAnimator.ofFloat(particle, View.SCALE_Y, 0.2f, 1.25f, 0.7f));
            burst.setStartDelay(180L + (i % 5) * 65L);
            burst.setDuration(1950L + (i % 3) * 210L);
            burst.start();
        }

        // High-contrast in-room celebration toast, centered over the room content.
        TextView banner = new TextView(activity);
        banner.setText("💥  انفجار صندوق الدعم  💎");
        banner.setTextColor(0xFFFFE9B0);
        banner.setTextSize(16f);
        banner.setTypeface(null, android.graphics.Typeface.BOLD);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(activity, 20), dp(activity, 13), dp(activity, 20), dp(activity, 13));
        GradientDrawable bannerBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { 0xF52C1D16, 0xF55A3B18, 0xF52C1D16 });
        bannerBg.setCornerRadius(dp(activity, 28));
        bannerBg.setStroke(dp(activity, 1), 0xFFFFD36A);
        banner.setBackground(bannerBg);
        banner.setElevation(dp(activity, 18));
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        bannerParams.topMargin = dp(activity, 155);
        celebration.addView(banner, bannerParams);
        AnimatorSet bannerAnim = new AnimatorSet();
        bannerAnim.playTogether(
                ObjectAnimator.ofFloat(banner, View.ALPHA, 0f, 1f, 1f, 0f),
                ObjectAnimator.ofFloat(banner, View.TRANSLATION_Y, dp(activity, 24), 0f, 0f, -dp(activity, 12)),
                ObjectAnimator.ofFloat(banner, View.SCALE_X, 0.82f, 1.04f, 1f, 0.98f),
                ObjectAnimator.ofFloat(banner, View.SCALE_Y, 0.82f, 1.04f, 1f, 0.98f));
        bannerAnim.setStartDelay(320L);
        bannerAnim.setDuration(3400L);
        bannerAnim.start();

        TextView blast = new TextView(activity);
        blast.setText("💥");
        blast.setTextSize(76f);
        blast.setGravity(Gravity.CENTER);
        blast.setClickable(false);
        blast.setFocusable(false);
        blast.setShadowLayer(dp(activity, 20), 0f, 0f, 0xFFFFD36A);
        FrameLayout.LayoutParams blastParams = new FrameLayout.LayoutParams(
                dp(activity, 132), dp(activity, 132), Gravity.CENTER);
        celebration.addView(blast, blastParams);
        AnimatorSet blastAnimation = new AnimatorSet();
        blastAnimation.playTogether(
                ObjectAnimator.ofFloat(blast, View.ALPHA, 0f, 1f, 1f, 0f),
                ObjectAnimator.ofFloat(blast, View.SCALE_X, 0.12f, 1.3f, 1.7f, 1.95f),
                ObjectAnimator.ofFloat(blast, View.SCALE_Y, 0.12f, 1.3f, 1.7f, 1.95f),
                ObjectAnimator.ofFloat(blast, View.ROTATION, -22f, 8f, -4f, 0f));
        blastAnimation.setStartDelay(180L);
        blastAnimation.setDuration(2150L);
        blastAnimation.start();

        MAIN.postDelayed(() -> {
            if (celebration.getParent() == root) root.removeView(celebration);
        }, 4550L);
    }

    private static String formatMultiplier(double multiplier) {
        if (Math.abs(multiplier - Math.rint(multiplier)) < 0.0001d) {
            return String.format(Locale.US, "%.0f", multiplier);
        }
        return String.format(Locale.US, "%.2f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static int dimensionValue(Object value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, (int) Math.round(Double.parseDouble(String.valueOf(value))))); }
        catch (Exception ignored) { return fallback; }
    }

    private static void applyTextSizes(@Nullable TextView title, @Nullable TextView body, @Nullable TextView count,
                                       float titleSizeSp, float bodySizeSp, float countSizeSp) {
        if (title != null) title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8f, Math.min(30f, titleSizeSp)));
        if (body != null) body.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8f, Math.min(30f, bodySizeSp)));
        if (count != null) count.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8f, Math.min(30f, countSizeSp)));
    }

    private static void resizeAvatar(Activity activity, ImageView avatar, int sizeDp) {
        if (avatar == null) return;
        int size = dp(activity, Math.max(20, Math.min(76, sizeDp)));
        ViewGroup.LayoutParams params = avatar.getLayoutParams();
        if (params != null) {
            params.width = size;
            params.height = size;
            avatar.setLayoutParams(params);
        }
        View parent = (View) avatar.getParent();
        if (parent != null) {
            ViewGroup.LayoutParams parentParams = parent.getLayoutParams();
            if (parentParams != null) {
                parentParams.width = size;
                parentParams.height = size;
                parent.setLayoutParams(parentParams);
            }
        }
    }

    private static void positionViewPercent(View target, View container, float xPercent, float yPercent, boolean centerX) {
        if (target == null || container == null || container.getWidth() <= 0 || container.getHeight() <= 0) return;
        float desiredX = container.getWidth() * Math.max(0f, Math.min(100f, xPercent)) / 100f;
        float desiredY = container.getHeight() * Math.max(0f, Math.min(100f, yPercent)) / 100f;
        float targetWidth = Math.max(1, target.getWidth());
        float targetHeight = Math.max(1, target.getHeight());
        // Keep all configured elements inside the artwork even when a saved slider
        // value is 0% or 100%; otherwise the avatar/name/reward/X can disappear off-edge.
        if (centerX) {
            desiredX = Math.max(targetWidth / 2f,
                    Math.min(container.getWidth() - targetWidth / 2f, desiredX));
        } else {
            desiredX = Math.max(0f,
                    Math.min(Math.max(0f, container.getWidth() - targetWidth), desiredX));
        }
        desiredY = Math.max(targetHeight / 2f,
                Math.min(container.getHeight() - targetHeight / 2f, desiredY));
        int[] targetLocation = new int[2];
        int[] containerLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        container.getLocationOnScreen(containerLocation);
        // Dashboard preview stores the avatar/count center but the text's left edge.
        // Remove existing translations before deriving the layout baseline so repeated
        // live-style refreshes are idempotent.
        float baseX = targetLocation[0] - containerLocation[0] - target.getTranslationX();
        float baseY = targetLocation[1] - containerLocation[1] - target.getTranslationY();
        target.setTranslationX(desiredX - baseX - (centerX ? targetWidth / 2f : 0f));
        target.setTranslationY(desiredY - baseY - targetHeight / 2f);
    }

    /** Home strip wording: مبروك … حصل على … (luck + games). */
    private static String buildMikooLine(
            @Nullable String kind,
            @Nullable String displayName,
            @Nullable String body,
            long coinsWon,
            double multiplier
    ) {
        String name = displayName != null ? displayName.trim() : "";
        if ("planet_summon".equalsIgnoreCase(kind)) {
            if (body != null && !body.isEmpty()) return body;
            if (!name.isEmpty()) return "رائع ~ " + name + " استدعى كوكبًا رائعًا";
            return "رائع ~ استدعاء كوكب رائع";
        }
        if ("lucky_hit".equalsIgnoreCase(kind)) {
            if (!name.isEmpty() && coinsWon > 0) {
                if (multiplier >= 2d) {
                    return "مبروك " + name + " حصل على " + coinsWon + " · ×" + formatMultiplier(multiplier);
                }
                return "مبروك " + name + " حصل على " + coinsWon;
            }
            if (body != null && !body.isEmpty()) return body;
            if (!name.isEmpty()) return "مبروك " + name + " فاز بهدية حظ";
            return "مبروك! فاز بهدية حظ";
        }
        if ("game_win".equalsIgnoreCase(kind)) {
            if (!name.isEmpty() && coinsWon > 0) {
                return "مبروك " + name + " حصل على " + coinsWon;
            }
            if (body != null && !body.isEmpty()) return body;
            if (!name.isEmpty()) return "مبروك " + name + " فاز باللعبة";
            return "مبروك! فاز باللعبة";
        }
        if (body != null && !body.isEmpty()) return body;
        if (!name.isEmpty() && coinsWon > 0) return "مبروك " + name + " حصل على " + coinsWon;
        if (!name.isEmpty()) return "مبروك " + name;
        return "مبروك!";
    }

    private static void removeBanner(View banner) { removeBanner(banner, false); }

    private static void removeBanner(View banner, boolean centerPlacement) {
        if (banner == null) return;
        Runnable laneHide = ACTIVE_HIDE_TASKS.remove(banner);
        if (laneHide != null) MAIN.removeCallbacks(laneHide);
        float out = -Math.max(banner.getWidth(), dp(banner, 280));
        banner.animate()
                .alpha(0f)
                .translationX(out)
                .setDuration(480)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (banner.getParent() instanceof ViewGroup parent) {
                        parent.removeView(banner);
                        // Other toast lanes are independent; removal never repositions them.
                        STACK_STEPS_DP.remove(banner);
                    }
                    if (centerPlacement) {
                        if (currentCenter.get() == banner) currentCenter = new WeakReference<>(null);
                    } else {
                        if (current.get() == banner) current = new WeakReference<>(null);
                    }
                    Object laneTag = banner.getTag();
                    if (laneTag instanceof String && ((String) laneTag).startsWith("jeho-toast-lane|")) {
                        String laneKey = ((String) laneTag).substring("jeho-toast-lane|".length());
                        ACTIVE_LANES.remove(laneKey);
                        MAIN.post(() -> showNext(laneKey));
                    }
                })
                .start();
    }

    private static void reflowToastStack(ViewGroup root) {
        int index = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof String) || !((String) tag).startsWith("jeho-toast-")) continue;
            int step = STACK_STEPS_DP.getOrDefault(child, 96);
            float target = dp(child, index * step);
            child.animate().translationY(target).setDuration(180L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            index++;
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    /**
     * Dashboard uploads can be returned as /api/v1/uploads/... by older API
     * versions. Static media is served from /uploads/...; normalize that path
     * before Glide requests it so a saved background works in the Android app.
     */
    private static String toastBackgroundUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return "";
        String raw = pathOrUrl.trim();
        boolean suppliedAbsoluteUrl = raw.matches("(?i)^https?://.*");
        String absolute = suppliedAbsoluteUrl ? raw : AssetCatalog.absoluteUrl(raw);
        if (absolute == null || absolute.trim().isEmpty()) return "";
        try {
            android.net.Uri uri = android.net.Uri.parse(absolute.trim());
            String path = uri.getEncodedPath();
            if (path != null) {
                // Normalize legacy API-prefixed upload paths, but do not replace
                // the host of an absolute URL: uploads may be served from a
                // separate storage/CDN host and rewriting it to the API host
                // makes the image silently disappear in the Android toast.
                path = path.replaceFirst("(?i)^/api/v\\d+(?=/uploads/)", "");
                if (path.matches("(?i)^/uploads/.+")) {
                    android.net.Uri.Builder builder = uri.buildUpon().encodedPath(path);
                    if (!suppliedAbsoluteUrl) {
                        return ApiOrigin.origin() + path
                                + (uri.getEncodedQuery() == null ? "" : "?" + uri.getEncodedQuery())
                                + (uri.getEncodedFragment() == null ? "" : "#" + uri.getEncodedFragment());
                    }
                    return builder.build().toString();
                }
            }
            return uri.toString();
        } catch (Exception ignored) {
            // Preserve the saved URL if a legacy value cannot be parsed.
            return absolute;
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }
}
