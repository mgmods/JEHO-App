package com.Dramizo.Series.util;

import android.app.Activity;
import android.animation.ObjectAnimator;
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
    private static volatile boolean remoteShineEnabled = true;
    private static volatile boolean remoteFrameGlowEnabled = true;
    private static volatile int remoteDurationSeconds = 3;
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
                    Object bg = cfg.get("backgroundUrl");
                    remoteBackgroundUrl = bg instanceof String ? ((String) bg).trim() : "";
                    remoteShineEnabled = !Boolean.FALSE.equals(cfg.get("shineEnabled"));
                    remoteFrameGlowEnabled = !Boolean.FALSE.equals(cfg.get("frameGlowEnabled"));
                    try {
                        remoteDurationSeconds = Math.max(2, Math.min(8,
                                (int) Math.round(Double.parseDouble(String.valueOf(cfg.get("durationSeconds"))))));
                    } catch (Exception ignored) { remoteDurationSeconds = 3; }
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
        refreshToastStyle(activity);
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
        if (gameOrLucky && coinsWon > 0L) {
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
                    Glide.with(activity.getApplicationContext())
                            .load(absLeft)
                            .override(128, 128)
                            .centerCrop()
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
                removeBanner(banner);
                RoomJoinGateActivity.open(activity, targetRoom);
            });
        } else {
            banner.setOnClickListener(null);
            banner.setClickable(false);
        }

        boolean giftSend = kind != null && "gift_send".equalsIgnoreCase(kind);
        boolean showTimes = multiplier >= 1 && (gameOrLucky || giftSend || multiplier >= 2);
        if (wrapTimes != null && tvTimes != null && showTimes) {
            wrapTimes.setVisibility(View.VISIBLE);
            wrapTimes.setScaleX(0.82f);
            wrapTimes.setScaleY(0.82f);
            tvTimes.setText(giftSend ? String.format(Locale.US, "X%d", multiplier) : String.format(Locale.US, "×%d", multiplier));
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
        int toastWidth = Math.min(dp(activity, 360), Math.max(dp(activity, 260), screenWidth - dp(activity, 24)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(toastWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(activity, topMarginDp);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setScaleX(0.985f);
        banner.setScaleY(0.985f);
        banner.setTranslationX(dp(activity, 140));
        if (toastBackground != null) {
            String imageUrl = AssetCatalog.absoluteUrl(remoteBackgroundUrl);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                toastBackground.setVisibility(View.VISIBLE);
                try { Glide.with(activity.getApplicationContext()).load(imageUrl).centerCrop().into(toastBackground); }
                catch (Exception ignored) { toastBackground.setVisibility(View.GONE); }
            } else toastBackground.setVisibility(View.GONE);
        }
        banner.setElevation(dp(activity, remoteFrameGlowEnabled ? 20 : 14));
        if (sheen != null && remoteShineEnabled) {
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
        hideRunnable = () -> removeBanner(banner);
        MAIN.postDelayed(hideRunnable, Math.max(2, Math.min(8, remoteDurationSeconds)) * 1000L);
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
