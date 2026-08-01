package com.Dramizo.Series.presentation.games;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.data.remote.dto.GameAdsDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.ApiCall;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * AdMob for games: interstitial on open/close cadence + rewarded coin claim.
 * Config is driven from dashboard ({@code games.admob}).
 */
public final class GameAdsHelper {
    private static final String TAG = "GameAdsHelper";
    private static final String PREFS = "game_ads_prefs";
    private static final String KEY_OPEN_COUNT = "open_count";
    private static final long RETRY_MS = 3_000L;

    private static volatile GameAdsHelper instance;
    private static volatile boolean sdkInit;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameAdsDtos.AdsConfigDto config = GameAdsDtos.AdsConfigDto.disabled();
    private boolean configLoaded;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean loadingInterstitial;
    private boolean loadingRewarded;
    private boolean showing;
    private Activity boundActivity;

    public interface ContinueCallback {
        void onContinue();
    }

    public interface RewardUiCallback {
        void onPreparing();
        void onResult(boolean credited, int coins, long balance, @Nullable String message);
    }

    public static GameAdsHelper get(@NonNull Context context) {
        if (instance == null) {
            synchronized (GameAdsHelper.class) {
                if (instance == null) instance = new GameAdsHelper();
            }
        }
        return instance;
    }

    private GameAdsHelper() {}

    public void ensureSdk(@NonNull Context context) {
        if (sdkInit) return;
        try {
            MobileAds.initialize(context.getApplicationContext(), status -> sdkInit = true);
            sdkInit = true;
        } catch (Exception e) {
            Log.w(TAG, "MobileAds init failed: " + e.getMessage());
        }
    }

    public void refreshConfig(@NonNull Context context) {
        ensureSdk(context);
        AppContainer c = containerOf(context);
        if (c == null) return;
        c.getIoExecutor().execute(() -> {
            Result<GameAdsDtos.AdsConfigDto> r = ApiCall.execute(c.getGameAdsApi().config());
            handler.post(() -> {
                if (r.success && r.data != null) {
                    config = r.data;
                    configLoaded = true;
                    preload(boundActivity != null ? boundActivity : asActivity(context));
                } else {
                    config = GameAdsDtos.AdsConfigDto.disabled();
                    configLoaded = true;
                }
            });
        });
    }

    public boolean isRewardedEnabled() {
        return config != null && config.adsEnabled && config.rewardedEnabled
                && resolveUnit(config.rewardedId) != null;
    }

    public int rewardedCoins() {
        return config != null ? Math.max(0, config.rewardedCoins) : 0;
    }

    /** Call before opening a game. Continues immediately if no ad due. */
    public void maybeShowOnOpen(@NonNull Activity activity, @NonNull ContinueCallback next) {
        boundActivity = activity;
        ensureSdk(activity);
        if (!configLoaded) {
            refreshConfig(activity);
            next.onContinue();
            return;
        }
        if (!config.adsEnabled || !config.interstitialEnabled) {
            next.onContinue();
            return;
        }
        int every = Math.max(1, config.interstitialEveryNOpens);
        int count = bumpOpenCount(activity);
        if (count % every != 0) {
            next.onContinue();
            return;
        }
        showInterstitial(activity, next);
    }

    /** Call when closing a game if dashboard enables close ads. */
    public void maybeShowOnClose(@NonNull Activity activity, @NonNull ContinueCallback next) {
        boundActivity = activity;
        if (!config.adsEnabled || !config.interstitialOnClose || !config.interstitialEnabled) {
            next.onContinue();
            return;
        }
        showInterstitial(activity, next);
    }

    public void showRewardedForCoins(@NonNull Activity activity, @NonNull RewardUiCallback cb) {
        boundActivity = activity;
        ensureSdk(activity);
        if (!isRewardedEnabled()) {
            cb.onResult(false, 0, 0, "إعلانات المكافأة غير مفعّلة");
            return;
        }
        if (showing) return;

        Runnable show = () -> {
            if (rewardedAd == null) {
                cb.onResult(false, 0, 0, "الإعلان غير جاهز، حاول لاحقاً");
                preloadRewarded(activity);
                return;
            }
            showing = true;
            final boolean[] earned = {false};
            final RewardedAd ad = rewardedAd;
            rewardedAd = null;
            cb.onPreparing();
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    showing = false;
                    preloadRewarded(activity);
                    if (earned[0]) {
                        claimReward(activity, cb);
                    } else {
                        cb.onResult(false, 0, 0, "أكمل مشاهدة الإعلان للحصول على المكافأة");
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    showing = false;
                    preloadRewarded(activity);
                    cb.onResult(false, 0, 0, "تعذر عرض الإعلان");
                }
            });
            ad.show(activity, (OnUserEarnedRewardListener) item -> earned[0] = true);
        };

        if (rewardedAd != null) {
            show.run();
            return;
        }
        cb.onPreparing();
        preloadRewarded(activity);
        handler.postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            show.run();
        }, 2_500L);
    }

    private void claimReward(@NonNull Activity activity, @NonNull RewardUiCallback cb) {
        AppContainer c = containerOf(activity);
        if (c == null) {
            cb.onResult(false, 0, 0, "تعذر الاتصال");
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<GameAdsDtos.ClaimResultDto> r =
                    ApiCall.execute(c.getGameAdsApi().claimRewarded());
            handler.post(() -> {
                if (!r.success || r.data == null) {
                    cb.onResult(false, 0, 0, r.error != null ? r.error : "فشل صرف المكافأة");
                    return;
                }
                GameAdsDtos.ClaimResultDto d = r.data;
                if (d.dailyCapReached) {
                    cb.onResult(false, 0, d.balance, "وصلت الحد اليومي للمكافآت");
                    return;
                }
                if (d.credited) {
                    cb.onResult(true, d.coins, d.balance, null);
                } else {
                    cb.onResult(false, 0, d.balance, "لم تُضف عملات");
                }
            });
        });
    }

    private void showInterstitial(@NonNull Activity activity, @NonNull ContinueCallback next) {
        if (showing) {
            next.onContinue();
            return;
        }
        if (interstitialAd == null) {
            preloadInterstitial(activity);
            next.onContinue();
            return;
        }
        showing = true;
        InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                showing = false;
                preloadInterstitial(activity);
                next.onContinue();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                showing = false;
                preloadInterstitial(activity);
                next.onContinue();
            }
        });
        ad.show(activity);
    }

    public void preload(@Nullable Activity activity) {
        if (activity == null) return;
        boundActivity = activity;
        preloadInterstitial(activity);
        preloadRewarded(activity);
    }

    private void preloadInterstitial(@NonNull Activity activity) {
        if (!config.adsEnabled || !config.interstitialEnabled) return;
        String unit = resolveUnit(config.interstitialId);
        if (unit == null) return;
        if (loadingInterstitial || interstitialAd != null || showing) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        loadingInterstitial = true;
        InterstitialAd.load(activity, unit, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        loadingInterstitial = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        loadingInterstitial = false;
                        Log.w(TAG, "Interstitial load fail: " + loadAdError.getMessage());
                        handler.postDelayed(() -> preloadInterstitial(activity), RETRY_MS);
                    }
                });
    }

    private void preloadRewarded(@NonNull Activity activity) {
        if (!config.adsEnabled || !config.rewardedEnabled) return;
        String unit = resolveUnit(config.rewardedId);
        if (unit == null) return;
        if (loadingRewarded || rewardedAd != null || showing) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        loadingRewarded = true;
        RewardedAd.load(activity, unit, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        loadingRewarded = false;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        loadingRewarded = false;
                        Log.w(TAG, "Rewarded load fail: " + loadAdError.getMessage());
                        handler.postDelayed(() -> preloadRewarded(activity), RETRY_MS);
                    }
                });
    }

    @Nullable
    private static String resolveUnit(@Nullable String raw) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty()) return null;
        if (id.contains("ca-app-pub-3940256099942544")) {
            Log.w(TAG, "Ignoring Google sample ad unit");
            return null;
        }
        return id;
    }

    private static int bumpOpenCount(@NonNull Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int n = p.getInt(KEY_OPEN_COUNT, 0) + 1;
        p.edit().putInt(KEY_OPEN_COUNT, n).apply();
        return n;
    }

    @Nullable
    private static AppContainer containerOf(@NonNull Context context) {
        try {
            Context app = context.getApplicationContext();
            if (app instanceof AuraLiveApp) {
                return ((AuraLiveApp) app).getContainer();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Activity asActivity(@NonNull Context context) {
        return context instanceof Activity ? (Activity) context : null;
    }

    public static void toastReward(@NonNull Activity activity, boolean ok, int coins, @Nullable String msg) {
        String text = msg != null ? msg : (ok ? ("+" + coins + " عملة") : "لم تُضف مكافأة");
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }
}
