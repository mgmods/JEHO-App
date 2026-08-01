package com.Dramizo.Series.presentation.drama;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.DramaDtos;
import com.Dramizo.Series.databinding.ActivityDramaPlayerBinding;
import com.Dramizo.Series.databinding.BottomSheetDramaEpisodesBinding;
import com.Dramizo.Series.databinding.ItemDramaEpisodeBinding;
import com.Dramizo.Series.databinding.ItemDramaEpisodeNumberBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.bumptech.glide.Glide;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DramaPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_SERIES_ID = "series_id";
    public static final String EXTRA_SERIES_TITLE = "series_title";
    public static final String EXTRA_SERIES_COVER = "series_cover";
    public static final String EXTRA_SERIES_DESC = "series_desc";
    public static final String EXTRA_EPISODE_COUNT = "episode_count";

    private ActivityDramaPlayerBinding binding;
    private AppContainer c;
    private final List<DramaDtos.EpisodeDto> episodes = new ArrayList<>();
    private EpisodeAdapter episodeAdapter;
    private String seriesId;
    private String seriesTitle = "";
    private String seriesCover = "";
    private String seriesDesc = "";
    private long seriesTotalViews = 0;
    private DramaDtos.AdsConfigDto adsConfig;
    private DramaDtos.RewardsConfigDto rewardsConfig;
    private DramaAdsHelper adsHelper;
    private SharedPreferences unlockedPrefs;
    private SharedPreferences claimedPrefs;
    private final Set<String> claimedEpisodes = new HashSet<>();
    private int currentPage = -1;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BottomSheetDialog episodesSheet;
    private boolean initialPlayRequested;
    private boolean rewardCelebrationRunning;
    private AnimatorSet activeRewardAnim;
    private int autoAdAttemptPage = -1;
    private boolean unlockInProgress;
    private int statusBarInsetTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        binding = ActivityDramaPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            statusBarInsetTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            applyStatusBarInsetToVisibleEpisodes();
            return insets;
        });

        c = ContainerProvider.from(this);
        seriesId = getIntent().getStringExtra(EXTRA_SERIES_ID);
        seriesTitle = safe(getIntent().getStringExtra(EXTRA_SERIES_TITLE));
        seriesCover = safe(getIntent().getStringExtra(EXTRA_SERIES_COVER));
        seriesDesc = safe(getIntent().getStringExtra(EXTRA_SERIES_DESC));
        unlockedPrefs = getSharedPreferences("drama_unlocked", Context.MODE_PRIVATE);
        claimedPrefs = getSharedPreferences("drama_claimed_rewards", Context.MODE_PRIVATE);
        claimedEpisodes.addAll(claimedPrefs.getStringSet("claimed", new HashSet<>()));

        MobileAds.initialize(this, initStatus -> {});

        episodeAdapter = new EpisodeAdapter();
        binding.viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.viewPager.setAdapter(episodeAdapter);
        binding.viewPager.setOffscreenPageLimit(1);
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                schedulePlayAt(position, 0);
            }
        });

        AppLoadingOverlay.showUntilReady(this);
        loadConfig();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void hidePlayerLoading() {
        AppLoadingOverlay.hide(this);
    }

    private void loadConfig() {
        c.getIoExecutor().execute(() -> {
            Result<DramaDtos.DramaConfigDto> r = ApiCall.execute(c.getDramaApi().config());
            if (isFinishing()) return;
            runOnUiThread(() -> {
                if (r.success && r.data != null) {
                    if (r.data.ads != null) {
                        adsConfig = r.data.ads;
                        if (adsConfig.rewardedInterval <= 0) adsConfig.rewardedInterval = 1;
                        if (adsConfig.freeEpisodesCount < 0) adsConfig.freeEpisodesCount = 1;
                    }
                    if (r.data.rewards != null) {
                        rewardsConfig = r.data.rewards;
                        if (rewardsConfig.watchThreshold <= 0 || rewardsConfig.watchThreshold > 1) {
                            rewardsConfig.watchThreshold = 0.8;
                        }
                        if (rewardsConfig.coinsPerEpisode < 0) rewardsConfig.coinsPerEpisode = 5;
                    }
                }
                if (adsConfig == null) adsConfig = DramaDtos.AdsConfigDto.defaults();
                if (rewardsConfig == null) rewardsConfig = DramaDtos.RewardsConfigDto.defaults();
                adsHelper = new DramaAdsHelper(this, adsConfig);
                adsHelper.preload();
                loadSeriesDetail();
            });
        });
    }

    private void loadSeriesDetail() {
        if (seriesId == null) {
            finish();
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<DramaDtos.SeriesDto> r = ApiCall.execute(c.getDramaApi().seriesDetail(seriesId));
            if (isFinishing()) return;
            runOnUiThread(() -> {
                hidePlayerLoading();
                if (r.success && r.data != null && r.data.episodes != null && !r.data.episodes.isEmpty()) {
                    if (!TextUtils.isEmpty(r.data.title)) seriesTitle = r.data.title;
                    if (!TextUtils.isEmpty(r.data.coverUrl)) seriesCover = r.data.coverUrl;
                    if (!TextUtils.isEmpty(r.data.description)) seriesDesc = r.data.description;
                    seriesTotalViews = Math.max(0, r.data.totalViews);
                    episodes.clear();
                    episodes.addAll(r.data.episodes);
                    episodeAdapter.notifyDataSetChanged();
                    binding.viewPager.post(() -> schedulePlayAt(0, 0));
                } else {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    private void schedulePlayAt(int position, int attempt) {
        if (unlockInProgress) return;
        if (position < 0 || position >= episodes.size()) return;
        if (position != currentPage) {
            pauseAllExcept(position);
            currentPage = position;
        }
        mainHandler.postDelayed(() -> {
            if (isFinishing()) return;
            RecyclerView rv = (RecyclerView) binding.viewPager.getChildAt(0);
            if (rv == null) {
                if (attempt < 20) schedulePlayAt(position, attempt + 1);
                return;
            }
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
            if (!(vh instanceof EpisodeVH)) {
                if (attempt < 20) schedulePlayAt(position, attempt + 1);
                return;
            }
            EpisodeVH evh = (EpisodeVH) vh;
            DramaDtos.EpisodeDto ep = episodes.get(position);
            boolean locked = isEpisodeLocked(position, ep.id);
            evh.bindMeta(ep, position);
            evh.setLocked(locked);
            if (locked) {
                evh.pause();
                showUnlockForCurrentPage(evh, ep);
                // Warm next ads while gated.
                if (adsHelper != null) adsHelper.preload();
            } else {
                evh.playImmediate();
                recordView(ep.id);
                // Preload while free episode plays so next locked ep is instant.
                if (adsHelper != null) adsHelper.preload();
            }
            initialPlayRequested = true;
        }, attempt == 0 ? 40 : 80);
    }

    private void showUnlockForCurrentPage(EpisodeVH evh, DramaDtos.EpisodeDto ep) {
        evh.setLocked(true);
        evh.setAdPreparing(false, null);
        evh.b.btnWatchAd.setEnabled(true);
        evh.b.btnWatchAd.setOnClickListener(v -> triggerUnlockAd(evh, ep, false));

        // Auto-show ad when landing on locked episode (professional flow).
        if (autoAdAttemptPage != currentPage) {
            autoAdAttemptPage = currentPage;
            triggerUnlockAd(evh, ep, true);
        }
    }

    private void triggerUnlockAd(EpisodeVH evh, DramaDtos.EpisodeDto ep, boolean auto) {
        if (adsHelper == null || unlockInProgress) return;
        unlockInProgress = true;
        evh.b.btnWatchAd.setEnabled(false);

        adsHelper.showForUnlock(new DramaAdsHelper.UnlockCallback() {
            @Override
            public void onPreparing() {
                if (isFinishing()) return;
                evh.setAdPreparing(true, getString(R.string.unlock_ad_preparing));
            }

            @Override
            public void onAdShowing() {
                if (isFinishing()) return;
                // Hide prep UI — fullscreen ad takes over.
                evh.setAdPreparing(false, null);
            }

            @Override
            public void onUnlocked() {
                if (isFinishing()) return;
                unlockInProgress = false;
                unlockEpisode(ep.id);
                evh.setAdPreparing(false, null);
                evh.setLocked(false);
                evh.b.btnWatchAd.setEnabled(true);
                evh.playImmediate();
                recordView(ep.id);
                if (adsHelper != null) adsHelper.preload();
            }

            @Override
            public void onFailed() {
                if (isFinishing()) return;
                unlockInProgress = false;
                evh.setAdPreparing(false, getString(R.string.unlock_ad_retry_hint));
                evh.setLocked(true);
                evh.b.btnWatchAd.setEnabled(true);
                if (adsHelper != null) adsHelper.preload();
                if (!auto) {
                    Toast.makeText(DramaPlayerActivity.this,
                            R.string.unlock_ad_incomplete, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isEpisodeLocked(int index, String episodeId) {
        if (adsHelper == null || !adsHelper.requiresAd(index)) return false;
        return !getUnlockedSet().contains(episodeId);
    }

    private Set<String> getUnlockedSet() {
        return new HashSet<>(unlockedPrefs.getStringSet("unlocked_" + seriesId, new HashSet<>()));
    }

    private void unlockEpisode(String episodeId) {
        Set<String> unlocked = getUnlockedSet();
        unlocked.add(episodeId);
        unlockedPrefs.edit().putStringSet("unlocked_" + seriesId, unlocked).apply();
    }

    private void pauseAllExcept(int keepPosition) {
        RecyclerView rv = (RecyclerView) binding.viewPager.getChildAt(0);
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
            if (vh instanceof EpisodeVH && vh.getBindingAdapterPosition() != keepPosition) {
                ((EpisodeVH) vh).pause();
            }
        }
    }

    private void recordView(String episodeId) {
        c.getIoExecutor().execute(() -> ApiCall.execute(c.getDramaApi().viewEpisode(episodeId)));
    }

    private void claimReward(String episodeId) {
        if (episodeId == null || episodeId.isEmpty()) return;
        if (hasClaimedReward(episodeId)) return;
        // Mark locally first so rewind / re-watch cannot double-claim UI or API spam.
        markClaimedReward(episodeId);

        c.getIoExecutor().execute(() -> {
            Result<DramaDtos.ClaimRewardResult> r = ApiCall.execute(c.getDramaApi().claimReward(episodeId));
            if (isFinishing()) return;
            runOnUiThread(() -> {
                if (!r.success) {
                    // Network failure — allow a later retry.
                    unmarkClaimedReward(episodeId);
                    return;
                }
                if (r.data != null && r.data.credited && r.data.coins > 0) {
                    showRewardCelebration(r.data.coins);
                }
                // alreadyClaimed / disabled → stay marked, no celebration
            });
        });
    }

    private boolean hasClaimedReward(String episodeId) {
        return claimedEpisodes.contains(episodeId);
    }

    private void markClaimedReward(String episodeId) {
        claimedEpisodes.add(episodeId);
        Set<String> saved = new HashSet<>(claimedPrefs.getStringSet("claimed", new HashSet<>()));
        saved.add(episodeId);
        claimedPrefs.edit().putStringSet("claimed", saved).apply();
    }

    private void unmarkClaimedReward(String episodeId) {
        claimedEpisodes.remove(episodeId);
        Set<String> saved = new HashSet<>(claimedPrefs.getStringSet("claimed", new HashSet<>()));
        saved.remove(episodeId);
        claimedPrefs.edit().putStringSet("claimed", saved).apply();
    }

    private void applyStatusBarInsetToVisibleEpisodes() {
        RecyclerView rv = (RecyclerView) binding.viewPager.getChildAt(0);
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
            if (vh instanceof EpisodeVH) ((EpisodeVH) vh).applyStatusBarInset();
        }
    }

    private void advanceToNextEpisode(int fromPosition) {
        int next = fromPosition + 1;
        if (next >= episodes.size()) return;
        binding.viewPager.setCurrentItem(next, true);
    }

    private void showEpisodesSheet() {
        if (episodesSheet != null && episodesSheet.isShowing()) {
            episodesSheet.dismiss();
            return;
        }
        BottomSheetDramaEpisodesBinding sheet = BottomSheetDramaEpisodesBinding.inflate(getLayoutInflater());
        episodesSheet = AuraDialogHelper.bottomSheet(this);
        AuraDialogHelper.applyContent(sheet.getRoot());
        episodesSheet.setContentView(sheet.getRoot());

        sheet.tvSeriesSheetTitle.setText(seriesTitle);
        sheet.tvSeriesSheetDesc.setText(seriesDesc);
        sheet.tvSeriesSheetCount.setText(getString(R.string.episodes_count_format, episodes.size()));
        int coinsEp = rewardsConfig != null ? Math.max(0, rewardsConfig.coinsPerEpisode) : 0;
        if (coinsEp > 0) {
            long seriesTotal = (long) coinsEp * Math.max(0, episodes.size());
            sheet.tvSeriesSheetReward.setVisibility(View.VISIBLE);
            sheet.tvSeriesSheetReward.setText(
                    "+" + coinsEp + " عملة / حلقة  •  حتى +" + seriesTotal + " للمسلسل");
        } else {
            sheet.tvSeriesSheetReward.setVisibility(View.GONE);
        }
        sheet.tvSeriesSheetViews.setVisibility(View.VISIBLE);
        sheet.tvSeriesSheetViews.setText("👁 مشاهدات " + formatCount(Math.max(0, seriesTotalViews)));
        if (!TextUtils.isEmpty(seriesCover)) {
            Glide.with(this).load(seriesCover).centerCrop().into(sheet.imgSeriesSheet);
        }
        sheet.btnCloseEpisodes.setOnClickListener(v -> episodesSheet.dismiss());

        sheet.recyclerEpisodesSheet.setLayoutManager(new GridLayoutManager(this, 5));
        sheet.recyclerEpisodesSheet.setAdapter(new EpisodeNumberAdapter(index -> {
            episodesSheet.dismiss();
            binding.viewPager.setCurrentItem(index, false);
            schedulePlayAt(index, 0);
        }));
        episodesSheet.show();
    }

    private void showRewardCelebration(int coins) {
        if (coins <= 0 || binding == null) return;
        if (rewardCelebrationRunning) {
            // One celebration at a time — refresh text only.
            binding.tvRewardCenter.setText("ربحت +" + coins + " كوينز!");
            return;
        }
        rewardCelebrationRunning = true;

        if (activeRewardAnim != null) {
            activeRewardAnim.cancel();
            activeRewardAnim = null;
        }
        binding.rewardBurst.removeAllViews();
        if (binding.lottieReward != null) {
            binding.lottieReward.cancelAnimation();
            binding.lottieReward.setVisibility(View.GONE);
        }

        binding.tvRewardCenter.setBackgroundResource(R.drawable.bg_drama_reward_chip);
        binding.tvRewardCenter.setTextColor(getColor(R.color.aurora_mint));
        binding.tvRewardCenter.setText("ربحت +" + coins + " كوينز!");
        binding.tvRewardCenter.setVisibility(View.VISIBLE);
        binding.tvRewardCenter.bringToFront();
        binding.tvRewardCenter.setScaleX(0.4f);
        binding.tvRewardCenter.setScaleY(0.4f);
        binding.tvRewardCenter.setAlpha(0f);

        if (binding.lottieReward != null) {
            binding.lottieReward.setVisibility(View.VISIBLE);
            binding.lottieReward.bringToFront();
            binding.lottieReward.setProgress(0f);
            binding.lottieReward.playAnimation();
        }

        AnimatorSet scaleIn = new AnimatorSet();
        scaleIn.playTogether(
                ObjectAnimator.ofFloat(binding.tvRewardCenter, "scaleX", 0.4f, 1.15f, 1f),
                ObjectAnimator.ofFloat(binding.tvRewardCenter, "scaleY", 0.4f, 1.15f, 1f),
                ObjectAnimator.ofFloat(binding.tvRewardCenter, "alpha", 0f, 1f)
        );
        scaleIn.setDuration(520);
        scaleIn.setInterpolator(new OvershootInterpolator(1.6f));

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(binding.tvRewardCenter, "alpha", 1f, 0f);
        fadeOut.setStartDelay(1600);
        fadeOut.setDuration(380);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                binding.tvRewardCenter.setVisibility(View.GONE);
                if (binding.lottieReward != null) {
                    binding.lottieReward.cancelAnimation();
                    binding.lottieReward.setVisibility(View.GONE);
                }
                rewardCelebrationRunning = false;
                activeRewardAnim = null;
            }
        });

        activeRewardAnim = new AnimatorSet();
        activeRewardAnim.playTogether(scaleIn);
        activeRewardAnim.start();
        fadeOut.start();
        binding.rewardBurst.post(this::spawnCoinBurst);
    }

    private void spawnCoinBurst() {
        if (binding == null) return;
        binding.rewardBurst.removeAllViews();
        int w = binding.rewardBurst.getWidth();
        int h = binding.rewardBurst.getHeight();
        if (w <= 0 || h <= 0) {
            w = getResources().getDisplayMetrics().widthPixels;
            h = getResources().getDisplayMetrics().heightPixels;
        }
        float centerX = w / 2f;
        float centerY = h / 2f;
        int size = (int) (28 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < 8; i++) {
            ImageView coin = new ImageView(this);
            coin.setImageResource(R.drawable.icon_coin_brand);
            binding.rewardBurst.addView(coin, new ViewGroup.LayoutParams(size, size));
            coin.setX(centerX - size / 2f);
            coin.setY(centerY - size / 2f);
            float endX = (float) (Math.random() * w * 0.8 - w * 0.4);
            float endY = (float) (-120 - Math.random() * 280);
            AnimatorSet anim = new AnimatorSet();
            anim.playTogether(
                    ObjectAnimator.ofFloat(coin, "translationX", 0f, endX),
                    ObjectAnimator.ofFloat(coin, "translationY", 0f, endY),
                    ObjectAnimator.ofFloat(coin, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(coin, "rotation", 0f, 180f + (float) (Math.random() * 180))
            );
            anim.setDuration(900 + (long) (Math.random() * 450));
            anim.setStartDelay(i * 55L);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (binding != null) binding.rewardBurst.removeView(coin);
                }
            });
            anim.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseAllExcept(-1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlockInProgress) return;
        if (initialPlayRequested && currentPage >= 0) {
            schedulePlayAt(currentPage, 0);
        }
    }

    @Override
    protected void onDestroy() {
        if (adsHelper != null) adsHelper.destroy();
        AppLoadingOverlay.hide(this);
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (episodesSheet != null) episodesSheet.dismiss();
        RecyclerView rv = (RecyclerView) binding.viewPager.getChildAt(0);
        if (rv != null) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
                if (vh instanceof EpisodeVH) ((EpisodeVH) vh).release();
            }
        }
    }

    private class EpisodeAdapter extends RecyclerView.Adapter<EpisodeVH> {
        @NonNull
        @Override
        public EpisodeVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new EpisodeVH(ItemDramaEpisodeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        @OptIn(markerClass = UnstableApi.class)
        public void onBindViewHolder(@NonNull EpisodeVH h, int position) {
            h.bind(episodes.get(position), position);
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }

        @Override
        public void onViewRecycled(@NonNull EpisodeVH holder) {
            holder.release();
        }
    }

    private class EpisodeNumberAdapter extends RecyclerView.Adapter<EpisodeNumberAdapter.VH> {
        interface Listener {
            void onPick(int index);
        }

        private final Listener listener;

        EpisodeNumberAdapter(Listener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemDramaEpisodeNumberBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DramaDtos.EpisodeDto ep = episodes.get(position);
            h.b.tvEpisodeNumber.setText(String.valueOf(ep.episodeNumber));
            boolean current = position == currentPage;
            h.b.episodeCard.setCardBackgroundColor(current ? 0xFFE50914 : 0xFF2A2A35);
            h.itemView.setOnClickListener(v -> listener.onPick(position));
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemDramaEpisodeNumberBinding b;
            VH(ItemDramaEpisodeNumberBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private class EpisodeVH extends RecyclerView.ViewHolder {
        final ItemDramaEpisodeBinding b;
        ExoPlayer player;
        DramaDtos.EpisodeDto episode;
        int position;
        boolean rewardClaimed;
        boolean wantPlay;
        private final Runnable progressChecker = this::checkWatchProgress;

        EpisodeVH(ItemDramaEpisodeBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(DramaDtos.EpisodeDto ep, int pos) {
            this.episode = ep;
            this.position = pos;
            this.rewardClaimed = hasClaimedReward(ep.id);
            bindMeta(ep, pos);
            applyStatusBarInset();

            if (ep.thumbnailUrl != null && !ep.thumbnailUrl.isEmpty()) {
                b.imgThumbnail.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(ep.thumbnailUrl).centerCrop().into(b.imgThumbnail);
            } else if (!TextUtils.isEmpty(seriesCover)) {
                b.imgThumbnail.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(seriesCover).centerCrop().into(b.imgThumbnail);
            } else {
                b.imgThumbnail.setVisibility(View.GONE);
            }

            boolean locked = isEpisodeLocked(pos, ep.id);
            setLocked(locked);

            b.btnBack.setOnClickListener(v -> {
                if (isTaskRoot()) {
                    Intent home = new Intent(DramaPlayerActivity.this,
                            com.Dramizo.Series.presentation.main.MainActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    home.putExtra(com.Dramizo.Series.presentation.main.MainActivity.EXTRA_OPEN_DRAMA, true);
                    startActivity(home);
                }
                finish();
            });
            b.btnMore.setOnClickListener(v -> showEpisodesSheet());
            b.btnEpisodes.setOnClickListener(v -> showEpisodesSheet());
            b.seriesInfoBar.setOnClickListener(v -> showEpisodesSheet());
            b.btnLike.setOnClickListener(v -> toggleLike(ep));
            b.btnWatchAd.setOnClickListener(v -> triggerUnlockAd(this, ep, false));
            b.tapArea.setOnClickListener(v -> {
                if (isEpisodeLocked(position, episode.id)) {
                    triggerUnlockAd(this, ep, false);
                    return;
                }
                if (player == null) {
                    playImmediate();
                    return;
                }
                if (player.isPlaying()) {
                    pause();
                    b.icPlay.setVisibility(View.VISIBLE);
                    b.icPlay.setImageResource(R.drawable.ic_player_play);
                    b.icPlay.animate().alpha(0.85f).setDuration(150).start();
                } else {
                    playImmediate();
                    b.icPlay.animate().alpha(0f).setDuration(150)
                            .withEndAction(() -> b.icPlay.setVisibility(View.GONE)).start();
                }
            });
        }

        void bindMeta(DramaDtos.EpisodeDto ep, int pos) {
            b.tvEpTitleTop.setText(getString(R.string.episode_number_format, ep.episodeNumber));
            b.tvLikeCount.setText(formatCount(Math.max(0, ep.likeCount)));
            b.tvViewCount.setText(formatCount(Math.max(0, ep.viewCount)));
            b.tvEpCount.setText(String.valueOf(episodes.size()));
            b.btnLike.setImageResource(ep.isLiked
                    ? R.drawable.ic_heart_like : R.drawable.ic_heart_outline);
            b.tvSeriesTitle.setText(seriesTitle);
            b.tvSeriesEpisodes.setText(getString(R.string.episodes_count_format, episodes.size()));
            b.tvSeriesDesc.setText(TextUtils.isEmpty(seriesDesc) ? ep.title : seriesDesc);
            if (!TextUtils.isEmpty(seriesCover)) {
                Glide.with(itemView.getContext()).load(seriesCover).centerCrop().into(b.imgSeriesCover);
            }
        }

        void setLocked(boolean locked) {
            b.lockOverlay.setVisibility(locked ? View.VISIBLE : View.GONE);
            if (!locked) setAdPreparing(false, null);
        }

        void applyStatusBarInset() {
            int top = Math.max(statusBarInsetTop, dp(8));
            b.topBar.setPadding(
                    b.topBar.getPaddingLeft(),
                    top,
                    b.topBar.getPaddingRight(),
                    b.topBar.getPaddingBottom());
        }

        private int dp(int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }

        void setAdPreparing(boolean preparing, @Nullable String status) {
            if (b.progressAdPrepare != null) {
                b.progressAdPrepare.setVisibility(preparing ? View.VISIBLE : View.GONE);
            }
            if (b.tvAdStatus != null) {
                if (status != null && !status.isEmpty()) {
                    b.tvAdStatus.setText(status);
                    b.tvAdStatus.setVisibility(View.VISIBLE);
                } else {
                    b.tvAdStatus.setVisibility(View.GONE);
                }
            }
            if (b.btnWatchAd != null) {
                b.btnWatchAd.setVisibility(preparing ? View.INVISIBLE : View.VISIBLE);
            }
        }

        void initPlayer() {
            if (player != null || episode == null || TextUtils.isEmpty(episode.videoUrl)) return;
            b.progressPlayer.setVisibility(View.VISIBLE);
            player = new ExoPlayer.Builder(DramaPlayerActivity.this).build();
            b.playerView.setPlayer(player);
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            player.setMediaItem(MediaItem.fromUri(episode.videoUrl));
            player.prepare();
            final int episodePosition = position;
            player.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width <= 0 || videoSize.height <= 0) return;
                    // Landscape / YouTube-like → FIT; vertical short drama → ZOOM fill
                    boolean landscape = videoSize.width > videoSize.height;
                    b.playerView.setResizeMode(landscape
                            ? AspectRatioFrameLayout.RESIZE_MODE_FIT
                            : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        b.progressPlayer.setVisibility(View.GONE);
                        if (wantPlay) {
                            player.setPlayWhenReady(true);
                            player.play();
                            b.imgThumbnail.setVisibility(View.GONE);
                            b.icPlay.setVisibility(View.GONE);
                        }
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        b.progressPlayer.setVisibility(View.VISIBLE);
                    } else if (playbackState == Player.STATE_ENDED) {
                        b.progressPlayer.setVisibility(View.GONE);
                        if (episode != null
                                && !hasClaimedReward(episode.id)
                                && rewardsConfig != null
                                && rewardsConfig.enabled
                                && rewardsConfig.coinsPerEpisode > 0) {
                            rewardClaimed = true;
                            claimReward(episode.id);
                        }
                        mainHandler.postDelayed(
                                () -> advanceToNextEpisode(episodePosition), 350);
                    } else if (playbackState == Player.STATE_IDLE) {
                        b.progressPlayer.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        b.progressPlayer.setVisibility(View.GONE);
                        b.imgThumbnail.setVisibility(View.GONE);
                        startProgressCheck();
                    } else {
                        stopProgressCheck();
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    b.progressPlayer.setVisibility(View.GONE);
                    Toast.makeText(DramaPlayerActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        }

        void playImmediate() {
            wantPlay = true;
            if (player == null) initPlayer();
            if (player != null) {
                player.setPlayWhenReady(true);
                player.play();
                b.icPlay.setVisibility(View.GONE);
            }
        }

        void pause() {
            wantPlay = false;
            if (player != null) {
                player.setPlayWhenReady(false);
                player.pause();
            }
        }

        void release() {
            wantPlay = false;
            stopProgressCheck();
            if (player != null) {
                player.release();
                player = null;
            }
            b.playerView.setPlayer(null);
            b.progressPlayer.setVisibility(View.GONE);
        }

        private void startProgressCheck() {
            mainHandler.removeCallbacks(progressChecker);
            mainHandler.postDelayed(progressChecker, 1000);
        }

        private void stopProgressCheck() {
            mainHandler.removeCallbacks(progressChecker);
        }

        private void checkWatchProgress() {
            if (player == null || episode == null) return;
            if (rewardClaimed || hasClaimedReward(episode.id)) {
                rewardClaimed = true;
                return;
            }
            if (rewardsConfig == null || !rewardsConfig.enabled || rewardsConfig.coinsPerEpisode <= 0) {
                return;
            }
            long duration = player.getDuration();
            long positionMs = player.getCurrentPosition();
            if (duration > 0) {
                double threshold = rewardsConfig.watchThreshold > 0 && rewardsConfig.watchThreshold <= 1
                        ? rewardsConfig.watchThreshold : 0.8;
                if ((double) positionMs / duration >= threshold) {
                    rewardClaimed = true;
                    claimReward(episode.id);
                    return;
                }
            }
            if (player.isPlaying()) {
                mainHandler.postDelayed(progressChecker, 800);
            }
        }

        private void toggleLike(DramaDtos.EpisodeDto ep) {
            ep.isLiked = !ep.isLiked;
            ep.likeCount += ep.isLiked ? 1 : -1;
            b.btnLike.setImageResource(ep.isLiked
                    ? R.drawable.ic_heart_like : R.drawable.ic_heart_outline);
            b.tvLikeCount.setText(formatCount(ep.likeCount));
            c.getIoExecutor().execute(() -> ApiCall.execute(c.getDramaApi().likeEpisode(ep.id)));
        }
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }
}
