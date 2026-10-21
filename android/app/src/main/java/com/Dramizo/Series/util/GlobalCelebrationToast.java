package com.Dramizo.Series.util;

import android.app.Activity;
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
        Activity activity = request.activity.get();
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

        boolean showTimes = multiplier >= 1 && (gameOrLucky || multiplier >= 2);
        if (wrapTimes != null && tvTimes != null && showTimes) {
            wrapTimes.setVisibility(View.VISIBLE);
            tvTimes.setText(String.format(Locale.US, "×%d", multiplier));
            if (badge != null) badge.setVisibility(View.GONE);
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

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        lp.topMargin = dp(activity, topMarginDp);
        banner.setLayoutParams(lp);
        banner.setAlpha(0f);
        banner.setTranslationX(dp(activity, 140));
        root.addView(banner);
        current = new WeakReference<>(banner);

        banner.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        hideRunnable = () -> removeBanner(banner);
        MAIN.postDelayed(hideRunnable, canOpenRoom ? 6200L : 5200L);
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
