package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityUserLevelBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;

public class UserLevelActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityUserLevelBinding binding = ActivityUserLevelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        if (binding.btnTasks != null) {
            com.Dramizo.Series.util.TasksFeature.applyVisibility(binding.btnTasks, this);
            binding.btnTasks.setOnClickListener(v -> {
                if (!com.Dramizo.Series.util.TasksFeature.isEnabled(this)) return;
                startActivity(new Intent(this, TaskCenterActivity.class));
            });
        }

        binding.tabLevel.addTab(binding.tabLevel.newTab().setText(R.string.growth_tab));
        binding.tabLevel.addTab(binding.tabLevel.newTab().setText(R.string.wealth_tab));
        binding.tabLevel.addTab(binding.tabLevel.newTab().setText(R.string.charm_tab));
        binding.tabLevel.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int i = tab.getPosition();
                binding.panelGrowth.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
                binding.panelWealth.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
                binding.panelCharm.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        AppContainer c = ContainerProvider.from(this);
        AuthDtos.UserDto cached = c.getSessionManager().getUser();
        if (cached != null) {
            bindEconomy(binding, cached);
        }

        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.LevelInfoDto> r = ApiCall.execute(c.getUserApi().level());
            Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
            runOnUiThread(() -> {
                if (me.success && me.data != null) {
                    c.getSessionManager().updateCachedUser(me.data);
                    bindEconomy(binding, me.data);
                }
                if (!r.success || r.data == null) {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                MiscDtos.LevelInfoDto info = r.data;
                int level = Math.max(1, info.level);
                long current = Math.max(0, info.currentThreshold);
                long next = Math.max(current + 1, info.nextThreshold);
                long xp = Math.max(0, info.experience);
                long inLevel = Math.max(0, xp - current);
                long need = Math.max(1, next - current);
                int pct = info.progress > 0
                        ? (int) Math.min(100, Math.round(info.progress * 100))
                        : (int) Math.min(100, inLevel * 100 / need);

                binding.tvLevel.setText(getString(R.string.level_number, level));
                binding.tvLevelTitle.setText(rankTitleFor(level));
                binding.tvXp.setText(getString(R.string.level_xp_progress, inLevel, need));
                binding.tvXpHint.setText(getString(R.string.level_xp_remaining, Math.max(0, need - inLevel)));
                binding.progressXp.setProgress(pct);

                if (info.levelBadgeUrl != null && !info.levelBadgeUrl.isEmpty()) {
                    binding.imgLevelBadge.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(AssetCatalog.absoluteUrl(info.levelBadgeUrl))
                            .placeholder(binding.imgLevelBadge.getDrawable())
                            .error(R.drawable.ic_pro_level)
                            .into(binding.imgLevelBadge);
                }
            });
        });
    }

    private void bindEconomy(ActivityUserLevelBinding binding, AuthDtos.UserDto user) {
        int wealthLv = Math.max(1, user.wealthLevel > 0 ? user.wealthLevel : (int) Math.max(1, user.wealthScore));
        int charmLv = Math.max(1, user.popularityLevel > 0
                ? user.popularityLevel
                : (int) Math.max(1, user.charmScore));
        long sent = Math.max(0, user.totalSentCoins);
        long received = Math.max(0, user.totalReceivedDiamonds);

        binding.tvWealthLevel.setText(getString(R.string.level_number, wealthLv));
        binding.tvCharmLevel.setText(getString(R.string.level_number, charmLv));

        long wealthNext = scoreForLevel(wealthLv + 1);
        long charmNext = scoreForLevel(charmLv + 1);
        long wealthNeed = Math.max(0, wealthNext - sent);
        long charmNeed = Math.max(0, charmNext - received);

        binding.tvWealthScore.setText(String.format(Locale.US,
                "مرسل: %,d · %s",
                sent,
                getString(R.string.level_next_need, formatNum(wealthNeed), wealthLv + 1)));
        binding.tvCharmScore.setText(String.format(Locale.US,
                "مستلم: %,d · %s",
                received,
                getString(R.string.level_next_need, formatNum(charmNeed), charmLv + 1)));

        binding.progressWealth.setProgress(progressToward(sent, wealthLv));
        binding.progressCharm.setProgress(progressToward(received, charmLv));
        binding.tvWealthHint.setText(getString(R.string.wealth_coin_tip));
        binding.tvCharmHint.setText(getString(R.string.charm_coin_tip));
    }

    /** Inverse of backend levelFromScore: level ≈ cbrt(score/10). */
    private static long scoreForLevel(int level) {
        long l = Math.max(1, level);
        return 10L * l * l * l;
    }

    private static int progressToward(long score, int level) {
        long cur = scoreForLevel(level);
        long next = scoreForLevel(level + 1);
        if (next <= cur) return 100;
        long span = next - cur;
        long done = Math.max(0, score - cur);
        return (int) Math.min(100, done * 100 / Math.max(1, span));
    }

    private static String formatNum(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000d);
        if (n >= 1_000) return String.format(Locale.US, "%.1fK", n / 1_000d);
        return String.valueOf(n);
    }

    private String rankTitleFor(int level) {
        if (level >= 50) return getString(R.string.level_rank_legend);
        if (level >= 30) return getString(R.string.level_rank_star);
        if (level >= 15) return getString(R.string.level_rank_rising);
        if (level >= 5) return getString(R.string.level_rank_active);
        return getString(R.string.level_rank_newcomer);
    }
}
