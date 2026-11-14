package com.Dramizo.Series.presentation.drama;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.DramaDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.FragmentDramaBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.RemoteTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DramaFragment extends Fragment {
    private FragmentDramaBinding binding;
    private AppContainer c;
    private DramaSeriesAdapter adapter;
    private DramaFeaturedAdapter featuredAdapter;
    private DramaDtos.DramaConfigDto config;
    private boolean loadingInFlight;
    private int featuredPage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable featuredAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (binding == null || featuredAdapter == null || featuredAdapter.count() <= 1) return;
            featuredPage = (featuredPage + 1) % featuredAdapter.count();
            binding.pagerFeatured.setCurrentItem(featuredPage, true);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDramaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        c = ContainerProvider.from(requireActivity());

        RemoteTheme.applyActivityBackground(binding.getRoot(), "home");

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        DramaSeriesAdapter.Listener openSeries = this::openSeries;

        adapter = new DramaSeriesAdapter(openSeries);
        featuredAdapter = new DramaFeaturedAdapter(this::openSeries);

        binding.recyclerSeries.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.recyclerSeries.setAdapter(adapter);
        binding.pagerFeatured.setAdapter(featuredAdapter);
        binding.pagerFeatured.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                featuredPage = position;
                updateFeaturedDots(position);
                handler.removeCallbacks(featuredAutoScroll);
                handler.postDelayed(featuredAutoScroll, 4500);
            }
        });

        binding.swipeRefresh.setColorSchemeResources(R.color.aurora_cyan, R.color.aurora_mint);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            loadSeries();
            loadCoinsBalance();
        });

        setLoading(true);
        loadConfig();
        loadCoinsBalance();
    }

    private void openSeries(DramaDtos.SeriesDto series) {
        if (series == null || !isAdded()) return;
        Intent i = new Intent(requireContext(), DramaPlayerActivity.class);
        i.putExtra(DramaPlayerActivity.EXTRA_SERIES_ID, series.id);
        i.putExtra(DramaPlayerActivity.EXTRA_SERIES_TITLE, series.title);
        i.putExtra(DramaPlayerActivity.EXTRA_SERIES_COVER, series.coverUrl);
        i.putExtra(DramaPlayerActivity.EXTRA_SERIES_DESC, series.description);
        i.putExtra(DramaPlayerActivity.EXTRA_EPISODE_COUNT, series.episodeCount);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCoinsBalance();
        if (featuredAdapter != null && featuredAdapter.count() > 1) {
            handler.removeCallbacks(featuredAutoScroll);
            handler.postDelayed(featuredAutoScroll, 3500);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(featuredAutoScroll);
        super.onPause();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadCoinsBalance() {
        if (c == null || !isAdded()) return;
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                long coins = (w.success && w.data != null) ? w.data.coins : 0;
                binding.tvCoinsBalance.setText(String.format(Locale.US, "%,d", coins));
            });
        });
    }

    private void setLoading(boolean loading) {
        if (binding == null || !isAdded()) return;
        if (loading) {
            if (loadingInFlight) return;
            loadingInFlight = true;
            binding.tvEmpty.setVisibility(View.GONE);
            AppLoadingOverlay.showUntilReady(requireActivity());
        } else {
            loadingInFlight = false;
            binding.swipeRefresh.setRefreshing(false);
            AppLoadingOverlay.hide(requireActivity());
        }
    }

    private void loadConfig() {
        setLoading(true);
        c.getIoExecutor().execute(() -> {
            Result<DramaDtos.DramaConfigDto> r = ApiCall.execute(c.getDramaApi().config());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (r.success && r.data != null) {
                    config = r.data;
                    if (!config.enabled) {
                        setLoading(false);
                        binding.recyclerSeries.setVisibility(View.GONE);
                        binding.featuredRow.setVisibility(View.GONE);
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        binding.tvEmpty.setText(R.string.no_data);
                        binding.swipeRefresh.setEnabled(false);
                        return;
                    }
                    if (config.rewards != null) {
                        adapter.setCoinsPerEpisode(Math.max(0, config.rewards.coinsPerEpisode));
                    } else {
                        adapter.setCoinsPerEpisode(0);
                    }
                }
                loadSeries();
            });
        });
    }

    private void loadSeries() {
        if (!loadingInFlight) setLoading(true);
        c.getIoExecutor().execute(() -> {
            Result<List<DramaDtos.SeriesDto>> r = ApiCall.execute(c.getDramaApi().series());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                setLoading(false);
                if (r.success && r.data != null) {
                    adapter.submit(r.data);
                    bindFeatured(r.data);
                    boolean empty = r.data.isEmpty();
                    binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.recyclerSeries.setVisibility(empty ? View.GONE : View.VISIBLE);
                } else {
                    binding.recyclerSeries.setVisibility(View.GONE);
                    binding.featuredRow.setVisibility(View.GONE);
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void bindFeatured(List<DramaDtos.SeriesDto> all) {
        if (binding == null || featuredAdapter == null) return;
        List<DramaDtos.SeriesDto> ranked = new ArrayList<>();
        if (all != null) ranked.addAll(all);
        Collections.sort(ranked, Comparator.comparingLong(
                (DramaDtos.SeriesDto s) -> s != null ? s.totalViews : 0L).reversed());
        List<DramaDtos.SeriesDto> top = new ArrayList<>();
        for (DramaDtos.SeriesDto s : ranked) {
            if (s == null) continue;
            top.add(s);
            if (top.size() >= 5) break;
        }
        featuredAdapter.submit(top);
        if (top.isEmpty()) {
            binding.featuredRow.setVisibility(View.GONE);
            handler.removeCallbacks(featuredAutoScroll);
            return;
        }
        binding.featuredRow.setVisibility(View.VISIBLE);
        buildFeaturedDots(top.size());
        featuredPage = 0;
        binding.pagerFeatured.setCurrentItem(0, false);
        handler.removeCallbacks(featuredAutoScroll);
        if (top.size() > 1) {
            handler.postDelayed(featuredAutoScroll, 3500);
        }
    }

    private void buildFeaturedDots(int count) {
        if (binding == null || binding.featuredDots == null) return;
        binding.featuredDots.removeAllViews();
        if (count <= 1) return;
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            int size = dp(6);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(dp(3));
            lp.setMarginEnd(dp(3));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_banner_dot_inactive);
            binding.featuredDots.addView(dot);
        }
        updateFeaturedDots(0);
    }

    private void updateFeaturedDots(int active) {
        if (binding == null || binding.featuredDots == null) return;
        for (int i = 0; i < binding.featuredDots.getChildCount(); i++) {
            View dot = binding.featuredDots.getChildAt(i);
            boolean on = i == active;
            dot.setBackgroundResource(on
                    ? R.drawable.bg_banner_dot_active
                    : R.drawable.bg_banner_dot_inactive);
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(featuredAutoScroll);
        if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        super.onDestroyView();
        binding = null;
    }
}
