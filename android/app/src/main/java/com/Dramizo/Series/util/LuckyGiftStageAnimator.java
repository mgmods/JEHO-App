package com.Dramizo.Series.util;

import android.graphics.PointF;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Lucky gift stage FX — intentionally disabled.
 * Room UX uses ComingMsgView-sized toast + light seat float only.
 */
public final class LuckyGiftStageAnimator {
    private LuckyGiftStageAnimator() {}

    public static void play(
            @Nullable FrameLayout overlay,
            @Nullable String giftIconUrl,
            @Nullable List<PointF> micTargets,
            long coinsSpent,
            int quantity,
            int personCount,
            @Nullable Runnable onScatterStart,
            @Nullable Runnable onEnd
    ) {
        if (onScatterStart != null) {
            try { onScatterStart.run(); } catch (Exception ignored) {}
        }
        if (onEnd != null) {
            try { onEnd.run(); } catch (Exception ignored) {}
        }
    }

    public static void showWinBurst(
            @Nullable FrameLayout overlay,
            int multiplier,
            long coinsWon,
            @Nullable Runnable onEnd
    ) {
        showWinBurst(overlay, multiplier, coinsWon, null, onEnd);
    }

    public static void showWinBurst(
            @Nullable FrameLayout overlay,
            int multiplier,
            long coinsWon,
            @Nullable List<PointF> micTargets,
            @Nullable Runnable onEnd
    ) {
        if (onEnd != null) {
            try { onEnd.run(); } catch (Exception ignored) {}
        }
    }
}
