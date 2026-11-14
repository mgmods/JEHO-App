package com.Dramizo.Series.presentation.common;

import android.os.Handler;
import android.os.Looper;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Queues room visual effects (entry banners, gift overlays) and plays them one
 * at a time on the main thread in priority-then-FIFO order.
 */
public final class RoomEffectQueue {

    public enum EffectType {
        NORMAL_ENTRY(0),
        GIFT(1),
        VIP_ENTRY(2),
        SUPPORTER_ENTRY(3),
        LEGENDARY_GIFT(4);

        final int priority;
        EffectType(int priority) { this.priority = priority; }
    }

    public static final class Effect {
        public final EffectType type;
        public final String displayName;
        public final String avatarUrl;
        public final String entryEffectUrl;
        public final String entryAnimationUrl;
        public final int vipLevel;
        /** normal | vip | supporter | legendary */
        public final String supporterTier;
        public final int userLevel;
        public final String vipBadgeUrl;
        public final String levelBadgeUrl;
        public final String hostBadgeUrl;
        public final boolean isHost;
        public final boolean showHiBadge;
        public final long wealthScore;
        public final long roomSpendCoins;
        public final int effectPriority;
        public final String renderMode;
        public final float aspectRatio;
        public final float safeLeft;
        public final float safeTop;
        public final float safeRight;
        public final float safeBottom;
        public final long durationMs;
        public final long totalCoins;
        public final String giftName;
        public final String giftIconUrl;
        public final String giftAnimationUrl;
        public final int comboCount;
        /** Real gift sender user id (not the local viewer). */
        public final String senderUserId;
        public final int senderVipLevel;
        public final String receiverId;
        public final long receiverGiftCoins;

        final long seq;

        Effect(Builder b, long seq) {
            this.type = b.type;
            this.displayName = b.displayName;
            this.avatarUrl = b.avatarUrl;
            this.entryEffectUrl = b.entryEffectUrl;
            this.entryAnimationUrl = b.entryAnimationUrl;
            this.vipLevel = b.vipLevel;
            this.supporterTier = b.supporterTier;
            this.userLevel = b.userLevel;
            this.vipBadgeUrl = b.vipBadgeUrl;
            this.levelBadgeUrl = b.levelBadgeUrl;
            this.hostBadgeUrl = b.hostBadgeUrl;
            this.isHost = b.isHost;
            this.showHiBadge = b.showHiBadge;
            this.wealthScore = b.wealthScore;
            this.roomSpendCoins = b.roomSpendCoins;
            this.effectPriority = b.effectPriority;
            this.renderMode = b.renderMode;
            this.aspectRatio = b.aspectRatio;
            this.safeLeft = b.safeLeft;
            this.safeTop = b.safeTop;
            this.safeRight = b.safeRight;
            this.safeBottom = b.safeBottom;
            this.durationMs = b.durationMs;
            this.totalCoins = b.totalCoins;
            this.giftName = b.giftName;
            this.giftIconUrl = b.giftIconUrl;
            this.giftAnimationUrl = b.giftAnimationUrl;
            this.comboCount = b.comboCount;
            this.senderUserId = b.senderUserId;
            this.senderVipLevel = b.senderVipLevel;
            this.receiverId = b.receiverId;
            this.receiverGiftCoins = b.receiverGiftCoins;
            this.seq = seq;
        }

        public boolean isEntry() {
            return type == EffectType.NORMAL_ENTRY
                    || type == EffectType.VIP_ENTRY
                    || type == EffectType.SUPPORTER_ENTRY;
        }

        public boolean isGift() {
            return type == EffectType.GIFT || type == EffectType.LEGENDARY_GIFT;
        }
    }

    public static final class Builder {
        EffectType type = EffectType.NORMAL_ENTRY;
        String displayName;
        String avatarUrl;
        String entryEffectUrl;
        String entryAnimationUrl;
        int vipLevel;
        String supporterTier = "normal";
        int userLevel = 1;
        String vipBadgeUrl;
        String levelBadgeUrl;
        String hostBadgeUrl;
        boolean isHost;
        boolean showHiBadge;
        long wealthScore;
        long roomSpendCoins;
        int effectPriority;
        String renderMode;
        float aspectRatio;
        float safeLeft;
        float safeTop;
        float safeRight;
        float safeBottom;
        long durationMs;
        long totalCoins;
        String giftName;
        String giftIconUrl;
        String giftAnimationUrl;
        int comboCount = 1;
        String senderUserId;
        int senderVipLevel;
        String receiverId;
        long receiverGiftCoins;

        public Builder type(EffectType t)           { type = t; return this; }
        public Builder displayName(String v)         { displayName = v; return this; }
        public Builder avatarUrl(String v)           { avatarUrl = v; return this; }
        public Builder entryEffectUrl(String v)      { entryEffectUrl = v; return this; }
        public Builder entryAnimationUrl(String v)   { entryAnimationUrl = v; return this; }
        public Builder vipLevel(int v)               { vipLevel = v; return this; }
        public Builder supporterTier(String v)       { supporterTier = v; return this; }
        public Builder userLevel(int v)              { userLevel = Math.max(1, v); return this; }
        public Builder vipBadgeUrl(String v)         { vipBadgeUrl = v; return this; }
        public Builder levelBadgeUrl(String v)       { levelBadgeUrl = v; return this; }
        public Builder hostBadgeUrl(String v)        { hostBadgeUrl = v; return this; }
        public Builder isHost(boolean v)              { isHost = v; return this; }
        public Builder showHiBadge(boolean v)         { showHiBadge = v; return this; }
        public Builder wealthScore(long v)            { wealthScore = Math.max(0L, v); return this; }
        public Builder roomSpendCoins(long v)        { roomSpendCoins = Math.max(0L, v); return this; }
        public Builder effectPriority(int v)          { effectPriority = v; return this; }
        public Builder renderMode(String v)           { renderMode = v; return this; }
        public Builder aspectRatio(float v)           { aspectRatio = Math.max(0f, v); return this; }
        public Builder textSafeArea(float l, float t, float r, float b) {
            safeLeft = l; safeTop = t; safeRight = r; safeBottom = b; return this;
        }
        public Builder durationMs(long v)             { durationMs = Math.max(0L, v); return this; }
        public Builder totalCoins(long v)            { totalCoins = v; return this; }
        public Builder giftName(String v)            { giftName = v; return this; }
        public Builder giftIconUrl(String v)         { giftIconUrl = v; return this; }
        public Builder giftAnimationUrl(String v)    { giftAnimationUrl = v; return this; }
        public Builder comboCount(int v)             { comboCount = Math.max(1, v); return this; }
        public Builder senderUserId(String v)        { senderUserId = v; return this; }
        public Builder senderVipLevel(int v)         { senderVipLevel = Math.max(0, v); return this; }
        public Builder receiverId(String v)          { receiverId = v; return this; }
        public Builder receiverGiftCoins(long v)     { receiverGiftCoins = Math.max(0, v); return this; }

        public Effect build(RoomEffectQueue queue) {
            return new Effect(this, queue.seqGen.getAndIncrement());
        }
    }

    public interface Listener {
        void onPlay(Effect effect);
        default void onIdle() {}
    }

    private static final long ENTRY_DURATION_MS = 5000;
    private static final long GIFT_DURATION_MS = 5200;
    private static final long COMBO_GIFT_DURATION_MS = 5800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong seqGen = new AtomicLong();
    private final PriorityQueue<Effect> queue = new PriorityQueue<>((a, b) -> {
        // Consecutive joins must never overtake each other as profile data arrives.
        if (a.isEntry() && b.isEntry()) return Long.compare(a.seq, b.seq);
        // Preserve the established effect-type hierarchy; server priority orders peers within it.
        int aPriority = a.type.priority * 1000 + Math.max(-999, Math.min(999, a.effectPriority));
        int bPriority = b.type.priority * 1000 + Math.max(-999, Math.min(999, b.effectPriority));
        int p = Integer.compare(bPriority, aPriority);
        if (p != 0) return p;
        return Long.compare(a.seq, b.seq);
    });
    private static final int MAX_PENDING_ENTRIES = 8;

    private final Listener listener;
    private boolean playing;
    private final Runnable safetyAdvance = () -> {
        if (!playing) return;
        playing = false;
        pump();
    };

    public RoomEffectQueue(Listener listener) {
        this.listener = listener;
    }

    public void enqueue(Effect effect) {
        if (effect == null) return;
        handler.post(() -> {
            if (effect.isEntry()) trimPendingEntries();
            queue.offer(effect);
            pump();
        });
    }

    /** Call when the active native/Web effect finishes (or is replaced intentionally). */
    public void notifyFinished() {
        handler.post(() -> {
            if (!playing) return;
            handler.removeCallbacks(safetyAdvance);
            playing = false;
            pump();
        });
    }

    public void clear() {
        handler.post(() -> {
            queue.clear();
            playing = false;
            handler.removeCallbacksAndMessages(null);
        });
    }

    private void trimPendingEntries() {
        int pending = 0;
        for (Effect e : queue) {
            if (e.isEntry()) pending++;
        }
        while (pending >= MAX_PENDING_ENTRIES) {
            Effect drop = null;
            for (Effect e : queue) {
                if (e.isEntry()) {
                    drop = e;
                    break;
                }
            }
            if (drop == null) break;
            queue.remove(drop);
            pending--;
        }
    }

    private void pump() {
        if (playing) return;
        Effect next = queue.poll();
        if (next == null) {
            if (listener != null) listener.onIdle();
            return;
        }
        playing = true;
        if (listener != null) listener.onPlay(next);
        long duration;
        if (next.durationMs > 0) {
            duration = next.durationMs;
        } else if (next.isGift()) {
            duration = next.comboCount > 1 ? COMBO_GIFT_DURATION_MS : GIFT_DURATION_MS;
        } else {
            duration = ENTRY_DURATION_MS;
        }
        // Safety net if the effect never reports completion (entry VAP / gift MP4 download).
        handler.removeCallbacks(safetyAdvance);
        long pad = next.isEntry() ? 4000L : 2500L;
        if (next.isGift() && next.durationMs >= 30_000L) {
            pad = 5_000L; // video gifts already carry a long hold
        }
        handler.postDelayed(safetyAdvance, duration + pad);
    }
}
