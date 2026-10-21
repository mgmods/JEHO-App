package com.Dramizo.Series.util;

import android.app.Activity;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.voiceroom.RoomJoinGateActivity;
import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
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
    private static Runnable hideRunnable;
    private static String lastDedupeKey;
    private static long lastShownAt;
    private static final ArrayDeque<ToastRequest> PENDING = new ArrayDeque<>();
    private static boolean showing;
    private static volatile String remoteBackgroundUrl = "";
    private static volatile String remoteLuckyNormalBackgroundUrl = "";
    private static volatile String remoteLuckyHighBackgroundUrl = "";
    private static volatile boolean remoteShineEnabled = true;
    private static volatile boolean remoteFrameGlowEnabled = true;
    private static volatile boolean remoteLuckyNormalShineEnabled = true;
    private static volatile boolean remoteLuckyHighShineEnabled = true;
    private static volatile boolean remoteLuckyNormalGlowEnabled = true;
    private static volatile boolean remoteLuckyHighGlowEnabled = true;
    private static volatile int remoteDurationSeconds = 3;
    private static volatile int remoteLuckyNormalDurationSeconds = 4;
    private static volatile int remoteLuckyHighDurationSeconds = 5;
    private static volatile float remoteGiftAvatarX = 8f, remoteGiftAvatarY = 50f, remoteGiftTextX = 25f, remoteGiftTextY = 50f;
    private static volatile float remoteGiftBodyX = 25f, remoteGiftBodyY = 67f, remoteGiftCountX = 88f, remoteGiftCountY = 50f;
    private static volatile float remoteLuckyNormalAvatarX = 8f, remoteLuckyNormalAvatarY = 42f;
    private static volatile float remoteLuckyNormalTextX = 25f, remoteLuckyNormalTextY = 42f, remoteLuckyNormalBodyX = 25f, remoteLuckyNormalBodyY = 62f;
    private static volatile float remoteLuckyNormalCountX = 88f, remoteLuckyNormalCountY = 50f;
    private static volatile float remoteLuckyHighAvatarX = 8f, remoteLuckyHighAvatarY = 42f;
    private static volatile float remoteLuckyHighTextX = 25f, remoteLuckyHighTextY = 42f, remoteLuckyHighBodyX = 25f, remoteLuckyHighBodyY = 62f;
    private static volatile float remoteLuckyHighCountX = 88f, remoteLuckyHighCountY = 50f;
    private static volatile int remoteGiftWidthDp = 340, remoteGiftHeightDp = 100, remoteGiftAvatarSizeDp = 44;
    private static volatile int remoteLuckyNormalWidthDp = 340, remoteLuckyNormalHeightDp = 100, remoteLuckyNormalAvatarSizeDp = 44;
    private static volatile int remoteLuckyHighWidthDp = 340, remoteLuckyHighHeightDp = 100, remoteLuckyHighAvatarSizeDp = 44;
    private static long lastStyleFetchAt;

    private static final class ToastRequest {
        final WeakReference<Activity> activity;
        final String title;
        final String body;
        final String avatarUrl;
        final String badgeUrl;
        final int topMarginDp;
        final int multiplier;
        final long coinsWon;
        final String displayName;
        final String kind;
        final String roomId;

        ToastRequest(Activity activity, String title, String body, String avatarUrl,
                     String badgeUrl, int topMarginDp, int multiplier, long coinsWon,
                     String displayName, String kind, String roomId) {
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
        }
    }

    private GlobalCelebrationToast() {}

    public static void showGiftCount(@Nullable Activity activity,
                                     @Nullable String senderName,
                                     @Nullable String giftName,
                                     @Nullable String avatarUrl,
                                     @Nullable String giftIconUrl,
                                     int quantity) {
        int count = Math.max(1, quantity);
        String body = (giftName == null || giftName.trim().isEmpty() ? "هدية" : giftName.trim()) + " · تم الإرسال";
        show(activity, senderName, body, avatarUrl, giftIconUrl,
                "gift-send-" + System.currentTimeMillis(), 28, count, 0L,
                senderName, "gift_send", null);
    }

    private static void applyRemoteStyleToActiveToast() {
        View active = current.get();
        if (active == null || active.getTag() == null) return;
        String activeKind = String.valueOf(active.getTag());
        boolean giftSend = "gift_send".equals(activeKind);
        boolean luckyHigh = "lucky_hit_high".equals(activeKind);
        boolean luckyNormal = "lucky_hit_normal".equals(activeKind);
        if (!giftSend && !luckyHigh && !luckyNormal) return;
        Activity activity = com.Dramizo.Series.AuraLiveApp.getForegroundActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        ImageView background = active.findViewById(R.id.imgToastBackground);
        String chosenBackground = giftSend ? remoteBackgroundUrl
                : luckyHigh ? remoteLuckyHighBackgroundUrl : remoteLuckyNormalBackgroundUrl;
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int requestedWidthDp = giftSend ? remoteGiftWidthDp : luckyHigh ? remoteLuckyHighWidthDp : remoteLuckyNormalWidthDp;
        int requestedHeightDp = giftSend ? remoteGiftHeightDp : luckyHigh ? remoteLuckyHighHeightDp : remoteLuckyNormalHeightDp;
        ViewGroup.LayoutParams activeParams = active.getLayoutParams();
        if (activeParams instanceof FrameLayout.LayoutParams) {
            activeParams.width = Math.min(dp(activity, requestedWidthDp), Math.max(dp(activity, 220), screenWidth - dp(activity, 24)));
            activeParams.height = dp(activity, requestedHeightDp);
            active.setLayoutParams(activeParams);
        }
        String imageUrl = AssetCatalog.absoluteUrl(chosenBackground);
        if (background != null) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                active.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                background.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                background.setVisibility(View.VISIBLE);
                try {
                    Glide.with(activity).load(imageUrl).dontTransform().into(background);
                } catch (Exception ignored) {
                    background.setVisibility(View.GONE);
                }
            } else background.setVisibility(View.GONE);
        }
        boolean glow = giftSend ? remoteFrameGlowEnabled : luckyHigh ? remoteLuckyHighGlowEnabled : remoteLuckyNormalGlowEnabled;
        boolean shine = giftSend ? remoteShineEnabled : luckyHigh ? remoteLuckyHighShineEnabled : remoteLuckyNormalShineEnabled;
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
        if (giftSend || luckyHigh || luckyNormal) {
            ImageView avatar = active.findViewById(R.id.imgCelebrationAvatar);
            TextView title = active.findViewById(R.id.tvCelebrationTitle);
            TextView body = active.findViewById(R.id.tvCelebrationBody);
            final float avatarX = giftSend ? remoteGiftAvatarX : luckyHigh ? remoteLuckyHighAvatarX : remoteLuckyNormalAvatarX;
            final float avatarY = giftSend ? remoteGiftAvatarY : luckyHigh ? remoteLuckyHighAvatarY : remoteLuckyNormalAvatarY;
            final float textX = giftSend ? remoteGiftTextX : luckyHigh ? remoteLuckyHighTextX : remoteLuckyNormalTextX;
            final float textY = giftSend ? remoteGiftTextY : luckyHigh ? remoteLuckyHighTextY : remoteLuckyNormalTextY;
            final float bodyX = giftSend ? remoteGiftBodyX : luckyHigh ? remoteLuckyHighBodyX : remoteLuckyNormalBodyX;
            final float bodyY = giftSend ? remoteGiftBodyY : luckyHigh ? remoteLuckyHighBodyY : remoteLuckyNormalBodyY;
            final float countX = giftSend ? remoteGiftCountX : luckyHigh ? remoteLuckyHighCountX : remoteLuckyNormalCountX;
            final float countY = giftSend ? remoteGiftCountY : luckyHigh ? remoteLuckyHighCountY : remoteLuckyNormalCountY;
            final int avatarSize = giftSend ? remoteGiftAvatarSizeDp : luckyHigh ? remoteLuckyHighAvatarSizeDp : remoteLuckyNormalAvatarSizeDp;
            resizeAvatar(activity, avatar, avatarSize);
            active.post(() -> {
                if (active.getParent() == null) return;
                positionViewPercent(avatar, active, avatarX, avatarY, true);
                positionViewPercent(title, active, textX, textY, false);
                positionViewPercent(body, active, bodyX, bodyY, false);
                positionViewPercent(active.findViewById(R.id.wrapCelebrationTimes), active, countX, countY, true);
            });
        }
        if (hideRunnable != null) MAIN.removeCallbacks(hideRunnable);
        hideRunnable = () -> removeBanner(active);
        int duration = giftSend ? remoteDurationSeconds : luckyHigh ? remoteLuckyHighDurationSeconds : remoteLuckyNormalDurationSeconds;
        MAIN.postDelayed(hideRunnable, Math.max(1, Math.min(12, duration)) * 1000L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedConfig(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map ? (Map<String, Object>) value : java.util.Collections.emptyMap();
    }
    private static String stringValue(Object value, String fallback) {
        return value instanceof String ? ((String) value).trim() : fallback;
    }
    private static int intValue(Object value, int fallback) {
        try { return Math.max(1, Math.min(12, (int) Math.round(Double.parseDouble(String.valueOf(value))))); }
        catch (Exception ignored) { return fallback; }
    }
    private static float floatValue(Object value, float fallback) {
        try { return Math.max(0f, Math.min(100f, Float.parseFloat(String.valueOf(value)))); }
        catch (Exception ignored) { return fallback; }
    }

    private static void refreshToastStyle(Activity activity) {
        long now = System.currentTimeMillis();
        if (now - lastStyleFetchAt < 15_000L) return;
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
                    Map<String, Object> high = nestedConfig(cfg, "luckyWinHigh");
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
                    remoteLuckyNormalBackgroundUrl = stringValue(normal.get("backgroundUrl"), "");
                    remoteLuckyHighBackgroundUrl = stringValue(high.get("backgroundUrl"), "");
                    remoteLuckyNormalShineEnabled = !Boolean.FALSE.equals(normal.get("shineEnabled"));
                    remoteLuckyHighShineEnabled = !Boolean.FALSE.equals(high.get("shineEnabled"));
                    remoteLuckyNormalGlowEnabled = !Boolean.FALSE.equals(normal.get("frameGlowEnabled"));
                    remoteLuckyHighGlowEnabled = !Boolean.FALSE.equals(high.get("frameGlowEnabled"));
                    remoteLuckyNormalDurationSeconds = intValue(normal.get("durationSeconds"), 4);
                    remoteLuckyHighDurationSeconds = intValue(high.get("durationSeconds"), 5);
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
                    remoteLuckyHighAvatarX = floatValue(high.get("avatarX"), 8f);
                    remoteLuckyHighAvatarY = floatValue(high.get("avatarY"), 50f);
                    remoteLuckyHighTextX = floatValue(high.get("textX"), 25f);
                    remoteLuckyHighTextY = floatValue(high.get("textY"), 42f);
                    remoteLuckyHighBodyX = floatValue(high.get("bodyX"), 25f);
                    remoteLuckyHighBodyY = floatValue(high.get("bodyY"), 62f);
                    remoteLuckyHighCountX = floatValue(high.get("countX"), 88f);
                    remoteLuckyHighCountY = floatValue(high.get("countY"), 50f);
                    remoteLuckyHighWidthDp = dimensionValue(high.get("widthDp"), 340, 220, 360);
                    remoteLuckyHighHeightDp = dimensionValue(high.get("heightDp"), 100, 60, 180);
                    remoteLuckyHighAvatarSizeDp = dimensionValue(high.get("avatarSizeDp"), 44, 20, 76);
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
            int multiplier,
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
            int multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId
    ) {
        if (activity == null || activity.isFinishing()) return;
        try {
            android.content.Context app = activity.getApplicationContext();
            if (app instanceof com.Dramizo.Series.AuraLiveApp aura
                    && aura.getContainer().getSessionManager().isMuteCelebrationPopups()) {
                return;
            }
        } catch (Exception ignored) {
        }
        if ("gift_send".equalsIgnoreCase(kind) || "lucky_hit".equalsIgnoreCase(kind)) refreshToastStyle(activity);
        long now = System.currentTimeMillis();
        if (dedupeKey != null && dedupeKey.equals(lastDedupeKey) && now - lastShownAt < 8_000L) {
            return;
        }
        lastDedupeKey = dedupeKey;
        lastShownAt = now;
        final int margin = Math.max(8, topMarginDp);
        ToastRequest request = new ToastRequest(activity, title, body, avatarUrl, badgeUrl,
                margin, multiplier, coinsWon, displayName, kind, roomId);
        MAIN.post(() -> {
            PENDING.addLast(request);
            showNext();
        });
    }

    private static void showNext() {
        if (showing) return;
        ToastRequest request = PENDING.pollFirst();
        if (request == null) return;
        Activity activity = com.Dramizo.Series.AuraLiveApp.getForegroundActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            activity = request.activity.get();
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            showNext();
            return;
        }
        showing = true;
        showInternal(activity, request.title, request.body, request.avatarUrl,
                request.badgeUrl, request.topMarginDp, request.multiplier, request.coinsWon,
                request.displayName, request.kind, request.roomId);
    }

    private static void showInternal(
            Activity activity,
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            int topMarginDp,
            int multiplier,
            long coinsWon,
            @Nullable String displayName,
            @Nullable String kind,
            @Nullable String roomId
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            showing = false;
            showNext();
            return;
        }
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) {
            showing = false;
            showNext();
            return;
        }

        View existing = current.get();
        if (existing != null && existing.getParent() instanceof ViewGroup) {
            ((ViewGroup) existing.getParent()).removeView(existing);
        }
        if (hideRunnable != null) MAIN.removeCallbacks(hideRunnable);

        View banner = LayoutInflater.from(activity)
                .inflate(R.layout.layout_celebration_toast, root, false);
        ImageView avatar = banner.findViewById(R.id.imgCelebrationAvatar);
        ImageView badge = banner.findViewById(R.id.imgCelebrationBadge);
        TextView tvTitle = banner.findViewById(R.id.tvCelebrationTitle);
        TextView tvBody = banner.findViewById(R.id.tvCelebrationBody);
        View wrapTimes = banner.findViewById(R.id.wrapCelebrationTimes);
        TextView tvTimes = banner.findViewById(R.id.tvCelebrationTimes);
        TextView btnGo = banner.findViewById(R.id.btnCelebrationGo);
        ImageView toastBackground = banner.findViewById(R.id.imgToastBackground);
        View sheen = banner.findViewById(R.id.viewCelebrationSheen);

        boolean planet = kind != null && "planet_summon".equalsIgnoreCase(kind);
        boolean gameOrLucky = kind != null && (
                "game_win".equalsIgnoreCase(kind) || "lucky_hit".equalsIgnoreCase(kind));
        boolean giftSend = kind != null && "gift_send".equalsIgnoreCase(kind);
        boolean luckyWin = kind != null && "lucky_hit".equalsIgnoreCase(kind);
        boolean luckyHigh = luckyWin && (multiplier >= 10 || coinsWon >= 1000L);
        String toastKindTag = giftSend ? "gift_send" : luckyWin ? (luckyHigh ? "lucky_hit_high" : "lucky_hit_normal") : null;
        banner.setTag(toastKindTag);
        boolean customToastStyle = giftSend || luckyWin;
        boolean remoteShine = giftSend ? remoteShineEnabled : luckyHigh ? remoteLuckyHighShineEnabled : remoteLuckyNormalShineEnabled;
        boolean remoteGlow = giftSend ? remoteFrameGlowEnabled : luckyHigh ? remoteLuckyHighGlowEnabled : remoteLuckyNormalGlowEnabled;
        int customDuration = giftSend ? remoteDurationSeconds : luckyHigh ? remoteLuckyHighDurationSeconds : remoteLuckyNormalDurationSeconds;
        if (planet) {
            banner.setBackgroundResource(R.drawable.bg_planet_go_banner);
        }

        String winnerName = firstNonEmpty(displayName, title);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(winnerName != null ? winnerName : "مبروك الفوز!");
            tvTitle.setSelected(true);
        }
        String line;
        if ("lucky_hit".equalsIgnoreCase(kind) && body != null && !body.trim().isEmpty()) {
            // Preserve server wording such as partial refund/return and the actual X result.
            line = body.trim();
        } else if (gameOrLucky && coinsWon > 0L) {
            line = "🪙 ربح " + String.format(Locale.US, "%,d", coinsWon) + " كوينز";
        } else {
            line = buildMikooLine(kind, displayName, body, coinsWon, multiplier);
        }
        if (tvBody != null) {
            tvBody.setText(line);
            tvBody.setSelected(true);
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
        if (avatar != null) {
            if (absLeft != null && !absLeft.isEmpty()) {
                try {
                    // Profile pictures must remain circular even when the uploaded toast
                    // background is a custom image and the dashboard changes avatar size/position.
                    avatar.setBackgroundResource(R.drawable.bg_notification_avatar);
                    avatar.setClipToOutline(true);
                    Glide.with(activity)
                            .load(absLeft)
                            .override(128, 128)
                            .circleCrop()
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
                avatar.setBackgroundResource(R.drawable.bg_notification_avatar);
                avatar.setClipToOutline(true);
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
                removeBanner(banner);
                RoomJoinGateActivity.open(activity, targetRoom);
            });
        } else {
            banner.setOnClickListener(null);
            banner.setClickable(false);
        }

        // A winning/refund event should never lose its X badge because a decimal
        // multiplier (for example 0.5) was truncated while parsing the socket payload.
        int displayMultiplier = giftSend ? Math.max(1, multiplier)
                : luckyWin && coinsWon > 0L ? Math.max(1, multiplier) : multiplier;
        boolean showTimes = displayMultiplier >= 1 && (gameOrLucky || giftSend || displayMultiplier >= 2);
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
                        : String.format(Locale.US, "×%d", displayMultiplier));
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
        int requestedWidthDp = giftSend ? remoteGiftWidthDp : luckyHigh ? remoteLuckyHighWidthDp : remoteLuckyNormalWidthDp;
        int requestedHeightDp = giftSend ? remoteGiftHeightDp : luckyHigh ? remoteLuckyHighHeightDp : remoteLuckyNormalHeightDp;
        int toastWidth = Math.min(dp(activity, requestedWidthDp), Math.max(dp(activity, 220), screenWidth - dp(activity, 24)));
        int toastHeight = customToastStyle ? dp(activity, requestedHeightDp) : ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(toastWidth, toastHeight);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(activity, topMarginDp);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setScaleX(0.985f);
        banner.setScaleY(0.985f);
        banner.setTranslationX(dp(activity, 140));
        if (toastBackground != null) {
            String selectedBackground = giftSend ? remoteBackgroundUrl : luckyHigh ? remoteLuckyHighBackgroundUrl : remoteLuckyNormalBackgroundUrl;
            String imageUrl = customToastStyle ? AssetCatalog.absoluteUrl(selectedBackground) : null;
            if (imageUrl != null && !imageUrl.isEmpty()) {
                // The uploaded artwork owns its pixels, edges and alpha channel. Do not add
                // the built-in rounded frame behind a transparent PNG/WebP design.
                banner.setBackground(null);
                toastBackground.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                toastBackground.setVisibility(View.VISIBLE);
                try { Glide.with(activity.getApplicationContext()).load(imageUrl).dontTransform().into(toastBackground); }
                catch (Exception ignored) { toastBackground.setVisibility(View.GONE); }
            } else {
                toastBackground.setVisibility(View.GONE);
            }
        }
        banner.setElevation(dp(activity, customToastStyle && remoteGlow ? 20 : 14));
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
        current = new WeakReference<>(banner);

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
            final float avatarX = giftSend ? remoteGiftAvatarX : luckyHigh ? remoteLuckyHighAvatarX : remoteLuckyNormalAvatarX;
            final float avatarY = giftSend ? remoteGiftAvatarY : luckyHigh ? remoteLuckyHighAvatarY : remoteLuckyNormalAvatarY;
            final float textX = giftSend ? remoteGiftTextX : luckyHigh ? remoteLuckyHighTextX : remoteLuckyNormalTextX;
            final float textY = giftSend ? remoteGiftTextY : luckyHigh ? remoteLuckyHighTextY : remoteLuckyNormalTextY;
            final float bodyX = giftSend ? remoteGiftBodyX : luckyHigh ? remoteLuckyHighBodyX : remoteLuckyNormalBodyX;
            final float bodyY = giftSend ? remoteGiftBodyY : luckyHigh ? remoteLuckyHighBodyY : remoteLuckyNormalBodyY;
            final float countX = giftSend ? remoteGiftCountX : luckyHigh ? remoteLuckyHighCountX : remoteLuckyNormalCountX;
            final float countY = giftSend ? remoteGiftCountY : luckyHigh ? remoteLuckyHighCountY : remoteLuckyNormalCountY;
            final int avatarSize = giftSend ? remoteGiftAvatarSizeDp : luckyHigh ? remoteLuckyHighAvatarSizeDp : remoteLuckyNormalAvatarSizeDp;
            resizeAvatar(activity, avatar, avatarSize);
            banner.post(() -> {
                if (banner.getParent() == null) return;
                positionViewPercent(avatar, banner, avatarX, avatarY, true);
                positionViewPercent(tvTitle, banner, textX, textY, false);
                positionViewPercent(tvBody, banner, bodyX, bodyY, false);
                positionViewPercent(wrapTimes, banner, countX, countY, true);
            });
        }
        hideRunnable = () -> removeBanner(banner);
        MAIN.postDelayed(hideRunnable, customToastStyle
                ? Math.max(1, Math.min(12, customDuration)) * 1000L
                : (canOpenRoom ? 6200L : 5200L));
    }

    private static int dimensionValue(Object value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, (int) Math.round(Double.parseDouble(String.valueOf(value))))); }
        catch (Exception ignored) { return fallback; }
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
        int[] targetLocation = new int[2];
        int[] containerLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        container.getLocationOnScreen(containerLocation);
        float currentX = targetLocation[0] - containerLocation[0] + (centerX ? target.getWidth() / 2f : 0f);
        float currentY = targetLocation[1] - containerLocation[1] + target.getHeight() / 2f;
        target.setTranslationX(target.getTranslationX() + desiredX - currentX);
        target.setTranslationY(target.getTranslationY() + desiredY - currentY);
    }

    /** Home strip wording: مبروك … حصل على … (luck + games). */
    private static String buildMikooLine(
            @Nullable String kind,
            @Nullable String displayName,
            @Nullable String body,
            long coinsWon,
            int multiplier
    ) {
        String name = displayName != null ? displayName.trim() : "";
        if ("planet_summon".equalsIgnoreCase(kind)) {
            if (body != null && !body.isEmpty()) return body;
            if (!name.isEmpty()) return "رائع ~ " + name + " استدعى كوكبًا رائعًا";
            return "رائع ~ استدعاء كوكب رائع";
        }
        if ("lucky_hit".equalsIgnoreCase(kind)) {
            if (!name.isEmpty() && coinsWon > 0) {
                if (multiplier >= 2) {
                    return "مبروك " + name + " حصل على " + coinsWon + " · ×" + multiplier;
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

    private static void removeBanner(View banner) {
        if (banner == null) return;
        if (hideRunnable != null) {
            MAIN.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        float out = -Math.max(banner.getWidth(), dp(banner, 280));
        banner.animate()
                .alpha(0f)
                .translationX(out)
                .setDuration(480)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (banner.getParent() instanceof ViewGroup parent) {
                        parent.removeView(banner);
                    }
                    if (current.get() == banner) current = new WeakReference<>(null);
                    showing = false;
                    showNext();
                })
                .start();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
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
