package com.Dramizo.Series.util;

import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.Map;

/** VIP avatar frame and host badge shared across room surfaces. */
public final class AvatarCosmetics {
    private AvatarCosmetics() {}

    public static void applyFrame(@Nullable ImageView frameView, @Nullable String frameUrl) {
        applyFrame(frameView, frameUrl, null);
    }

    public static void applyFrame(
            @Nullable ImageView frameView,
            @Nullable String frameUrl,
            @Nullable Map<String, ?> meta) {
        if (frameView == null) return;
        stopWearMotion(frameView, null);
        resetWearTransform(frameView);
        if (frameUrl == null || frameUrl.isEmpty()) {
            frameView.setVisibility(View.GONE);
            resetWearTransform(frameView);
            return;
        }
        frameView.setVisibility(View.VISIBLE);
        frameView.setAlpha(1f);
        loadWearImage(frameView, frameUrl);
        applyWearMeta(frameView, meta, 1f);
        // ImageView frames stay static. Live-style motion lives only in HostSignalView.
    }

    /** Decode frames at view size — huge PNGs otherwise freeze lists/profile. */
    private static void loadWearImage(@Nullable ImageView view, @Nullable String url) {
        if (view == null || url == null || url.isEmpty()) return;
        Runnable load = () -> {
            if (view.getContext() == null) return;
            int px = Math.max(view.getWidth(), view.getHeight());
            if (px <= 0) {
                px = Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 120f, view.getResources().getDisplayMetrics()));
            }
            // Cap decode size — frames never need 2K+ pixels on list rows.
            px = Math.min(Math.max(px, 64), 512);
            Glide.with(view)
                    .load(AssetCatalog.absoluteUrl(url))
                    .override(px, px)
                    .fitCenter()
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(view);
        };
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            load.run();
        } else {
            view.post(load);
        }
    }

    /**
     * Same host rendering as the live room stage: {@link HostSignalView} owns the
     * avatar + animated frame together (no separate ImageView sway).
     */
    public static boolean bindHostLikeLive(
            @Nullable HostSignalView hostSignal,
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String hostBadgeUrl,
            @Nullable Map<String, ?> hostBadgeMeta,
            int fallbackLevel) {
        // Prefetch face into imgAvatar first so fallback is never blank if HostSignalView fails.
        if (avatarView != null) {
            avatarView.setVisibility(View.VISIBLE);
            bindAvatar(avatarView, avatarUrl);
        }
        if (frameView != null) {
            stopWearMotion(frameView, avatarView);
            frameView.setVisibility(View.GONE);
            frameView.setImageDrawable(null);
        }
        if (hostSignal == null) {
            return false;
        }
        if (hostBadgeUrl == null || hostBadgeUrl.isEmpty()) {
            hostSignal.clearSignal();
            hostSignal.setVisibility(View.GONE);
            return false;
        }
        // Mikoo: hide bare avatar only after HostSignalView owns the face.
        hostSignal.setVisibility(View.VISIBLE);
        boolean ok = hostSignal.bind(
                hostBadgeUrl, avatarUrl, hostBadgeMeta, Math.max(1, fallbackLevel));
        if (ok) {
            if (avatarView != null) avatarView.setVisibility(View.GONE);
            hostSignal.resumeMotion();
        } else {
            hostSignal.setVisibility(View.GONE);
            if (avatarView != null) {
                avatarView.setVisibility(View.VISIBLE);
                bindAvatar(avatarView, avatarUrl);
            }
        }
        return ok;
    }

    /**
     * Stacks avatar inside a larger frame so the face fills the frame opening
     * (same proportions used on profile / seats / chat).
     */
    public static void bindStacked(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String frameUrl) {
        bindStacked(avatarView, frameView, null, avatarUrl, frameUrl, null);
    }

    public static void bindStacked(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable HostSignalView hostSignalView,
            @Nullable String avatarUrl,
            @Nullable String frameUrl,
            @Nullable Map<String, ?> hostMeta) {
        // HostSignalView: wing-flap motion for host badges AND VIP PNG frames (Mikoo-style).
        if (hostSignalView != null && frameUrl != null && !frameUrl.isEmpty()
                && (isHostSignalUrl(frameUrl) || hostMeta != null || isWearFrameUrl(frameUrl))) {
            bindHostLikeLive(hostSignalView, avatarView, frameView, avatarUrl, frameUrl, hostMeta, 1);
            return;
        }
        if (avatarView != null) bindAvatar(avatarView, avatarUrl);
        if (hostSignalView != null) {
            hostSignalView.clearSignal();
            hostSignalView.setVisibility(View.GONE);
        }
        if (avatarView != null) avatarView.setVisibility(View.VISIBLE);
        if (frameUrl != null && !frameUrl.isEmpty()) {
            applyFrame(frameView, frameUrl, hostMeta);
            fitAvatarInsideWear(avatarView, hostMeta);
        } else if (frameView != null) {
            stopWearMotion(frameView, avatarView);
            frameView.setVisibility(View.GONE);
            frameView.setImageDrawable(null);
            if (avatarView != null) resetWearTransform(avatarView);
        }
    }

    public static boolean isHostSignalUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("host-frames")
                || lower.contains("host_signal")
                || lower.startsWith("native://host-signal")
                || (lower.contains("frame-") && lower.contains("transparent"));
    }

    /** VIP / mall avatar frames that should use HostSignalView wing motion. */
    public static boolean isWearFrameUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("/cosmetics/frames/")
                || lower.contains("frame_mikoo_")
                || lower.contains("/assets/vip/")
                || (lower.contains("vip_badge") && !lower.contains("runtime.html"));
    }

    /**
     * Host badge replaces the avatar frame when equipped.
     * Avatar stays centered; badge wraps it. Optional meta JSON:
     * {"offsetX":0,"offsetY":-4,"scale":1.15,"avatarScale":0.7}
     */
    public static void applyHostWear(
            @Nullable ImageView frameView,
            @Nullable ImageView badgeView,
            @Nullable String frameUrl,
            @Nullable String hostBadgeUrl) {
        applyHostWear(frameView, badgeView, null, frameUrl, hostBadgeUrl, null, null);
    }

    public static void applyHostWear(
            @Nullable ImageView frameView,
            @Nullable ImageView badgeView,
            @Nullable ImageView avatarView,
            @Nullable String frameUrl,
            @Nullable String hostBadgeUrl,
            @Nullable Map<String, ?> frameMeta,
            @Nullable Map<String, ?> hostBadgeMeta) {
        boolean hasBadge = hostBadgeUrl != null && !hostBadgeUrl.isEmpty();
        boolean hasFrame = frameUrl != null && !frameUrl.isEmpty();
        if (avatarView != null) resetWearTransform(avatarView);
        if (hasBadge) {
            ImageView target = badgeView != null ? badgeView : frameView;
            if (badgeView != null && frameView != null) {
                resetWearTransform(frameView);
                frameView.setVisibility(View.GONE);
                frameView.setImageDrawable(null);
            }
            if (target != null) {
                target.setVisibility(View.VISIBLE);
                target.setAlpha(1f);
                // Frame/badge fills the stack; do not inflate past the box (clips lists).
                applyWearMeta(target, hostBadgeMeta, 1f);
                loadWearImage(target, hostBadgeUrl);
                stopWearMotion(target, avatarView);
            }
            // Avatar must match stack size first, then shrink into the frame opening.
            fitAvatarInsideWear(avatarView, hostBadgeMeta);
            return;
        }
        if (badgeView != null) {
            stopWearMotion(badgeView, avatarView);
            resetWearTransform(badgeView);
            badgeView.setVisibility(View.GONE);
            badgeView.setImageDrawable(null);
        }
        applyFrame(frameView, frameUrl, frameMeta);
        stopWearMotion(frameView, avatarView);
        if (hasFrame) {
            fitAvatarInsideWear(avatarView, frameMeta);
        } else if (avatarView != null) {
            resetWearTransform(avatarView);
        }
    }

    /**
     * Make avatar fill the frame stack, then apply one scale into the opening.
     * Default 0.72 matches HostSignalView / Host CSS baseline (not a second shrink).
     */
    private static void fitAvatarInsideWear(
            @Nullable ImageView avatarView, @Nullable Map<String, ?> meta) {
        if (avatarView == null) return;
        applyFitOnce(avatarView, meta);
        // If parent wasn't measured yet, retry after layout.
        if (avatarView.getParent() instanceof android.view.View) {
            android.view.View parent = (android.view.View) avatarView.getParent();
            if (parent.getWidth() <= 0 || parent.getHeight() <= 0) {
                avatarView.post(() -> applyFitOnce(avatarView, meta));
            }
        }
    }

    private static void applyFitOnce(
            @Nullable ImageView avatarView, @Nullable Map<String, ?> meta) {
        if (avatarView == null) return;
        syncAvatarSizeToStack(avatarView);
        avatarView.setPadding(0, 0, 0, 0);
        avatarView.setBackground(null);
        float avatarScale = metaFloat(meta, "avatarScale", 0.72f);
        if (avatarScale <= 0f) avatarScale = 0.72f;
        // 0.70 from older server meta is the same design baseline as 0.72.
        if (Math.abs(avatarScale - 0.70f) < 0.02f) avatarScale = 0.72f;
        avatarView.setScaleX(avatarScale);
        avatarView.setScaleY(avatarScale);
    }

    private static void syncAvatarSizeToStack(@Nullable ImageView avatarView) {
        if (avatarView == null) return;
        if (!(avatarView.getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup stack = (android.view.ViewGroup) avatarView.getParent();
        android.view.ViewGroup.LayoutParams ap = avatarView.getLayoutParams();
        if (ap == null) return;
        int measuredW = stack.getWidth();
        int measuredH = stack.getHeight();
        int targetW = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        int targetH = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        if (measuredW > 0 && measuredH > 0) {
            targetW = measuredW;
            targetH = measuredH;
        } else {
            android.view.ViewGroup.LayoutParams sp = stack.getLayoutParams();
            if (sp != null && sp.width > 0 && sp.height > 0) {
                targetW = sp.width;
                targetH = sp.height;
            }
        }
        if (ap.width != targetW || ap.height != targetH) {
            ap.width = targetW;
            ap.height = targetH;
            avatarView.setLayoutParams(ap);
        }
        if (avatarView.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams fp =
                    (android.widget.FrameLayout.LayoutParams) avatarView.getLayoutParams();
            fp.gravity = android.view.Gravity.CENTER;
            avatarView.setLayoutParams(fp);
        }
    }

    /** Prefer the FrameLayout that stacks avatar+frame. */
    @Nullable
    private static View stackRoot(@Nullable View a, @Nullable View b) {
        View parentA = a != null && a.getParent() instanceof View ? (View) a.getParent() : null;
        View parentB = b != null && b.getParent() instanceof View ? (View) b.getParent() : null;
        if (parentA != null && parentA == parentB) return parentA;
        if (parentA != null) return parentA;
        return parentB;
    }

    private static void playWearMotion(
            @Nullable View frameOrBadge, @Nullable View avatar, @Nullable String key) {
        // Intentionally no ImageView motion — live host motion is HostSignalView only.
        stopWearMotion(frameOrBadge, avatar);
    }

    private static void stopWearMotion(@Nullable View frameOrBadge, @Nullable View avatar) {
        if (frameOrBadge != null) RoomCardAnimator.stop(frameOrBadge);
        if (avatar != null) RoomCardAnimator.stop(avatar);
        View stack = stackRoot(frameOrBadge, avatar);
        if (stack != null) RoomCardAnimator.stop(stack);
    }

    /** Apply cosmetic.meta layout: offsetX/offsetY (dp), scale. */
    public static void applyWearMeta(
            @Nullable View target,
            @Nullable Map<String, ?> meta,
            float defaultScale) {
        if (target == null) return;
        float scale = metaFloat(meta, "scale", defaultScale);
        float ox = metaFloat(meta, "offsetX", 0f);
        float oy = metaFloat(meta, "offsetY", 0f);
        float density = target.getResources().getDisplayMetrics().density;
        target.setScaleX(scale);
        target.setScaleY(scale);
        target.setTranslationX(ox * density);
        target.setTranslationY(oy * density);
    }

    private static void resetWearTransform(@Nullable View v) {
        if (v == null) return;
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
    }

    private static float metaFloat(@Nullable Map<String, ?> meta, String key, float fallback) {
        if (meta == null || key == null) return fallback;
        Object raw = meta.get(key);
        if (raw == null) return fallback;
        if (raw instanceof Number) return ((Number) raw).floatValue();
        try {
            return Float.parseFloat(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static void bindAvatar(@Nullable ImageView avatarView, @Nullable String avatarUrl) {
        AvatarImageLoader.load(avatarView, avatarUrl);
    }

    /** Avatar + worn frame/host badge in one call (use wherever a profile photo is shown). */
    public static void bindWear(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String frameUrl) {
        bindWear(avatarView, frameView, avatarUrl, frameUrl, null, null, null);
    }

    public static void bindWear(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String frameUrl,
            @Nullable String hostBadgeUrl) {
        bindWear(avatarView, frameView, avatarUrl, frameUrl, hostBadgeUrl, null, null);
    }

    public static void bindWear(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String frameUrl,
            @Nullable String hostBadgeUrl,
            @Nullable Map<String, ?> frameMeta,
            @Nullable Map<String, ?> hostBadgeMeta) {
        bindAvatar(avatarView, avatarUrl);
        applyHostWear(frameView, null, avatarView, frameUrl, hostBadgeUrl, frameMeta, hostBadgeMeta);
    }

    /** Prefer this for lists / profile — VIP frame only (host signal is agency-room wear). */
    public static void bindWear(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (user == null) {
            bindWear(avatarView, frameView, null, null, null, null, null);
            return;
        }
        bindWear(
                avatarView,
                frameView,
                user.avatarUrl,
                user.vipBadgeUrl,
                null,
                null,
                null);
    }

    /**
     * Profile / Me: personal VIP frame only.
     * Host signal belongs to agency rooms — never on the personal profile.
     */
    public static void bindProfileWear(
            @Nullable HostSignalView hostSignal,
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable String avatarUrl,
            @Nullable String vipFrameUrl,
            @Nullable String hostBadgeUrl,
            @Nullable Map<String, ?> hostBadgeMeta,
            int fallbackLevel) {
        // Personal profile: animate VIP frame via HostSignalView when present.
        if (hostSignal != null && vipFrameUrl != null && !vipFrameUrl.isEmpty()) {
            boolean live = bindHostLikeLive(
                    hostSignal, avatarView, frameView, avatarUrl, vipFrameUrl, null,
                    Math.max(1, fallbackLevel));
            if (live) return;
        }
        if (hostSignal != null) {
            hostSignal.clearSignal();
            hostSignal.setVisibility(View.GONE);
        }
        if (avatarView != null) avatarView.setVisibility(View.VISIBLE);
        bindWear(avatarView, frameView, avatarUrl, vipFrameUrl, null, null, null);
    }

    /**
     * In-room wear: agency rooms → host signal; personal rooms → VIP frame.
     */
    public static void bindRoomWear(
            @Nullable HostSignalView hostSignal,
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable ImageView hostBadgeView,
            @Nullable String avatarUrl,
            @Nullable String vipFrameUrl,
            @Nullable String hostBadgeUrl,
            @Nullable Map<String, ?> hostBadgeMeta,
            boolean agencyRoom,
            int fallbackLevel) {
        if (agencyRoom) {
            boolean live = bindHostLikeLive(
                    hostSignal, avatarView, frameView, avatarUrl, hostBadgeUrl, hostBadgeMeta,
                    fallbackLevel);
            if (live) {
                if (hostBadgeView != null) {
                    hostBadgeView.setVisibility(View.GONE);
                    hostBadgeView.setImageDrawable(null);
                }
                return;
            }
            if (avatarView != null) {
                avatarView.setVisibility(View.VISIBLE);
                bindAvatar(avatarView, avatarUrl);
            }
            applyHostWear(frameView, hostBadgeView, avatarView, null, hostBadgeUrl, null, hostBadgeMeta);
            return;
        }
        // Personal room: VIP frame with wing motion when HostSignalView is available.
        if (hostSignal != null && vipFrameUrl != null && !vipFrameUrl.isEmpty()) {
            boolean live = bindHostLikeLive(
                    hostSignal, avatarView, frameView, avatarUrl, vipFrameUrl, null, fallbackLevel);
            if (live) {
                if (hostBadgeView != null) {
                    hostBadgeView.setVisibility(View.GONE);
                    hostBadgeView.setImageDrawable(null);
                }
                return;
            }
        }
        if (hostSignal != null) {
            hostSignal.clearSignal();
            hostSignal.setVisibility(View.GONE);
        }
        if (avatarView != null) {
            avatarView.setVisibility(View.VISIBLE);
            bindAvatar(avatarView, avatarUrl);
        }
        applyHostWear(frameView, hostBadgeView, avatarView, vipFrameUrl, null, null, null);
    }

    /** Frame without reloading avatar — VIP frame only outside agency rooms. */
    public static void bindWearOnAvatar(
            @Nullable ImageView avatarView,
            @Nullable ImageView frameView,
            @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (user == null) {
            applyHostWear(frameView, null, avatarView, null, null, null, null);
            return;
        }
        applyHostWear(
                frameView,
                null,
                avatarView,
                user.vipBadgeUrl,
                null,
                null,
                null);
    }

    public static void styleBadges(TextView levelChip, TextView charmChip, TextView wealthChip,
                                   int vipLevel, int userLevel) {
        styleBadges(levelChip, charmChip, wealthChip, vipLevel, userLevel, 0L, 0L);
    }

    /**
     * Room user-card chips: always show VIP (when &gt;0), Lv, شعبية, ثروة.
     */
    public static void styleUserCardBadges(
            @Nullable TextView vipChip,
            @Nullable TextView levelChip,
            @Nullable TextView charmChip,
            @Nullable TextView wealthChip,
            int vipLevel,
            int userLevel,
            long popularityScore,
            long wealthScore) {
        if (vipChip != null) {
            int vip = Math.max(0, vipLevel);
            if (vip > 0) {
                vipChip.setVisibility(View.VISIBLE);
                vipChip.setText("VIP" + vip);
                vipChip.setBackgroundResource(R.drawable.bg_chip_vip);
                vipChip.setTextColor(0xFFFFFFFF);
            } else {
                vipChip.setVisibility(View.GONE);
            }
        }
        if (levelChip != null) {
            int lv = Math.max(1, userLevel);
            levelChip.setVisibility(View.VISIBLE);
            levelChip.setText("Lv." + lv);
            levelChip.setBackgroundResource(R.drawable.bg_chip_level);
            levelChip.setTextColor(0xFFFFFFFF);
        }
        if (charmChip != null) {
            long pop = Math.max(0, popularityScore);
            charmChip.setVisibility(View.VISIBLE);
            charmChip.setText("شعبية " + formatScore(Math.max(1, pop)));
            charmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            charmChip.setTextColor(0xFFFFFFFF);
        }
        if (wealthChip != null) {
            long wealth = Math.max(0, wealthScore);
            wealthChip.setVisibility(View.VISIBLE);
            wealthChip.setText("ثروة " + formatScore(Math.max(0, wealth)));
            wealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
            wealthChip.setTextColor(0xFFFFFFFF);
        }
    }

    /**
     * Shows only real signals: Lv when meaningful, charm (🌙) / wealth (❤) when &gt; 0.
     * Colors match ProfileFragment chips (level amber / wealth green / charm pink).
     */
    public static void styleBadges(TextView levelChip, TextView charmChip, TextView wealthChip,
                                   int vipLevel, int userLevel, long charmScore, long wealthScore) {
        if (levelChip != null) {
            int lv = Math.max(0, userLevel);
            if (lv > 1) {
                levelChip.setVisibility(View.VISIBLE);
                levelChip.setText("Lv." + lv);
                levelChip.setBackgroundResource(R.drawable.bg_chip_level);
                levelChip.setTextColor(0xFFFFFFFF);
            } else {
                levelChip.setVisibility(View.GONE);
            }
        }

        if (charmChip != null) {
            if (charmScore > 0) {
                charmChip.setVisibility(View.VISIBLE);
                charmChip.setText("🌙" + formatScore(charmScore));
                charmChip.setBackgroundResource(R.drawable.bg_chip_charm);
                charmChip.setTextColor(0xFFFFFFFF);
            } else {
                charmChip.setVisibility(View.GONE);
            }
        }

        if (wealthChip != null) {
            if (wealthScore > 0) {
                wealthChip.setVisibility(View.VISIBLE);
                wealthChip.setText("❤" + formatScore(wealthScore));
                wealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
                wealthChip.setTextColor(0xFFFFFFFF);
            } else {
                wealthChip.setVisibility(View.GONE);
            }
        }
    }

    /** Chat list / profile row — always show Lv + wealth + charm display levels. */
    public static void styleProfileChips(TextView levelChip, TextView charmChip, TextView wealthChip,
                                         int userLevel, long charmLevel, long wealthLevel) {
        if (levelChip != null) {
            int lv = Math.max(1, userLevel);
            levelChip.setVisibility(View.VISIBLE);
            levelChip.setText("Lv." + lv);
            levelChip.setBackgroundResource(R.drawable.bg_chip_level);
            levelChip.setTextColor(0xFFFFFFFF);
        }
        if (charmChip != null) {
            charmChip.setVisibility(View.VISIBLE);
            charmChip.setText("🌙" + formatScore(Math.max(1, charmLevel)));
            charmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            charmChip.setTextColor(0xFFFFFFFF);
        }
        if (wealthChip != null) {
            wealthChip.setVisibility(View.VISIBLE);
            wealthChip.setText("❤" + formatScore(Math.max(1, wealthLevel)));
            wealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
            wealthChip.setTextColor(0xFFFFFFFF);
        }
    }

    private static String formatScore(long value) {
        if (value <= 0) return "0";
        // Economy levels are capped at 600 — show as plain level in room badges.
        if (value <= 600) return String.valueOf(value);
        if (value >= 1_000_000L) return String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f);
        if (value >= 1_000L) return String.format(java.util.Locale.US, "%.1fK", value / 1_000f);
        return String.valueOf(value);
    }
}
