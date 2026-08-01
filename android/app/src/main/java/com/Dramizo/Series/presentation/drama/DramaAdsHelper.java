package com.Dramizo.Series.presentation.drama;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.DramaDtos;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Rewarded-ad gate for drama episodes.
 * Ads are preloaded so unlock can show immediately; no toast spam.
 */
public class DramaAdsHelper {
    private static final String TAG = "DramaAdsHelper";
    private static final long RETRY_DELAY_MS = 2_500L;
    private static final long WAIT_TIMEOUT_MS = 15_000L;
    private static final long WAIT_POLL_MS = 300L;

    private final Activity activity;
    private final DramaDtos.AdsConfigDto adsConfig;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RewardedAd rewardedAd;
    private boolean loading;
    private boolean showing;
    private int failStreak;
    private Runnable pendingRetry;

    public interface UnlockCallback {
        /** Called while waiting for a preloaded ad (optional UI). */
        void onPreparing();
        /** Ad fullscreen is about to / did show. */
        void onAdShowing();
        /** User completed reward — unlock and play. */
        void onUnlocked();
        /** Failed or closed without reward — keep lock, allow retry. */
        void onFailed();
    }

    public DramaAdsHelper(Activity activity, DramaDtos.AdsConfigDto adsConfig) {
        this.activity = activity;
        this.adsConfig = adsConfig != null ? adsConfig : DramaDtos.AdsConfigDto.defaults();
    }

    public boolean isAdsEnabled() {
        return adsConfig != null && adsConfig.adsEnabled;
    }

    public boolean isReady() {
        return rewardedAd != null;
    }

    /** Episode index 0-based. */
    public boolean requiresAd(int episodeIndex) {
        if (!isAdsEnabled()) return false;
        int free = Math.max(0, adsConfig.freeEpisodesCount);
        if (episodeIndex < free) return false;
        int interval = Math.max(1, adsConfig.rewardedInterval);
        return ((episodeIndex - free) % interval) == 0;
    }

    private String resolveAdUnit() {
        // Production only — never fall back to Google sample / test units.
        if (adsConfig.rewardedId == null) return "";
        String id = adsConfig.rewardedId.trim();
        if (id.isEmpty()) return "";
        if (id.contains("ca-app-pub-3940256099942544")) {
            Log.w(TAG, "Ignoring Google sample ad unit — set a real rewardedId in dashboard");
            return "";
        }
        return id;
    }

    /** Keep an ad warm in memory. Safe to call often. */
    public void preload() {
        if (!isAdsEnabled()) return;
        String unit = resolveAdUnit();
        if (unit.isEmpty()) {
            Log.w(TAG, "No production rewarded ad unit configured");
            return;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (loading || rewardedAd != null || showing) return;

        loading = true;
        Log.i(TAG, "Preloading rewarded ad…");
        RewardedAd.load(activity, unit, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        loading = false;
                        failStreak = 0;
                        Log.i(TAG, "Rewarded ad ready");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        loading = false;
                        failStreak++;
                        Log.w(TAG, "Preload failed (" + error.getCode() + "): " + error.getMessage());
                        scheduleRetryPreload();
                    }
                });
    }

    private void scheduleRetryPreload() {
        if (pendingRetry != null) handler.removeCallbacks(pendingRetry);
        long delay = Math.min(RETRY_DELAY_MS * Math.max(1, failStreak), 12_000L);
        pendingRetry = () -> {
            pendingRetry = null;
            preload();
        };
        handler.postDelayed(pendingRetry, delay);
    }

    /**
     * Show the unlock ad. If already preloaded → show immediately.
     * Otherwise wait (with onPreparing) until ready / timeout, then show or fail once.
     */
    public void showForUnlock(@NonNull UnlockCallback callback) {
        if (!isAdsEnabled()) {
            callback.onUnlocked();
            return;
        }
        if (showing) return;

        if (rewardedAd != null) {
            showAdInternal(callback);
            return;
        }

        callback.onPreparing();
        preload();

        final long started = System.currentTimeMillis();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    callback.onFailed();
                    return;
                }
                if (rewardedAd != null) {
                    showAdInternal(callback);
                    return;
                }
                if (System.currentTimeMillis() - started >= WAIT_TIMEOUT_MS) {
                    callback.onFailed();
                    preload();
                    return;
                }
                if (!loading) preload();
                handler.postDelayed(this, WAIT_POLL_MS);
            }
        });
    }

    private void showAdInternal(@NonNull UnlockCallback callback) {
        if (rewardedAd == null || activity == null || activity.isFinishing()) {
            callback.onFailed();
            return;
        }
        if (showing) return;

        showing = true;
        final boolean[] earned = {false};
        final RewardedAd ad = rewardedAd;
        rewardedAd = null; // consume

        callback.onAdShowing();

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                Log.i(TAG, "Ad showing");
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                showing = false;
                preload(); // warm next
                if (earned[0]) {
                    callback.onUnlocked();
                } else {
                    callback.onFailed();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                showing = false;
                Log.w(TAG, "Ad show failed: " + error.getMessage());
                preload();
                callback.onFailed();
            }
        });

        ad.show(activity, (OnUserEarnedRewardListener) rewardItem -> earned[0] = true);
    }

    public void destroy() {
        if (pendingRetry != null) {
            handler.removeCallbacks(pendingRetry);
            pendingRetry = null;
        }
        handler.removeCallbacksAndMessages(null);
        rewardedAd = null;
        loading = false;
        showing = false;
    }
}
