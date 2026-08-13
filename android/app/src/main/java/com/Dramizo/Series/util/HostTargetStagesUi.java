package com.Dramizo.Series.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Binds host target ladder (progress + horizontal stage chips) — wallet / earnings / withdraw. */
public final class HostTargetStagesUi {
    private HostTargetStagesUi() {}

    public static boolean isEnabled(@Nullable Map<String, Object> data) {
        if (data == null) return false;
        Object enabled = data.get("enabled");
        if (Boolean.TRUE.equals(enabled)) return true;
        if (enabled instanceof Number) return ((Number) enabled).intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static void bindSection(
            Context ctx,
            @Nullable View section,
            @Nullable TextView tvMonth,
            @Nullable TextView tvProgress,
            @Nullable ProgressBar progressBar,
            @Nullable LinearLayout stagesRow,
            @Nullable Map<String, Object> data) {
        bindSection(ctx, section, tvMonth, tvProgress, progressBar, stagesRow, null, data);
    }

    public static void bindSection(
            Context ctx,
            @Nullable View section,
            @Nullable TextView tvMonth,
            @Nullable TextView tvProgress,
            @Nullable ProgressBar progressBar,
            @Nullable LinearLayout stagesRow,
            @Nullable HorizontalScrollView stagesScroll,
            @Nullable Map<String, Object> data) {
        if (section == null) return;
        if (data == null || !isEnabled(data)) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        long progress = toLong(data.get("progress"));
        long next = toLong(data.get("nextThreshold"));
        long remaining = toLong(data.get("remaining"));
        int cycle = Math.max(1, (int) toLong(data.get("cycle")));
        String month = String.valueOf(data.get("yearMonth"));
        Object periodLabel = data.get("periodLabel");
        if (periodLabel != null && !String.valueOf(periodLabel).isEmpty()
                && !"null".equals(String.valueOf(periodLabel))) {
            month = String.valueOf(periodLabel);
        }
        String periodTag = "";
        Object period = data.get("period");
        if (period != null) {
            String pk = String.valueOf(period);
            if ("weekly".equals(pk)) periodTag = " · " + ctx.getString(R.string.host_target_period_weekly);
            else if ("monthly".equals(pk)) periodTag = " · " + ctx.getString(R.string.host_target_period_monthly);
        }
        if (tvMonth != null) {
            String ml = month != null && !month.isEmpty() && !"null".equals(month)
                    ? month : ctx.getString(R.string.host_target_current_period);
            tvMonth.setText(ml + periodTag + " · " + ctx.getString(R.string.host_target_cycle, cycle));
        }
        if (tvProgress != null) {
            if (next > 0) {
                tvProgress.setText(ctx.getString(
                        R.string.host_target_progress_line, progress, next, remaining));
            } else {
                tvProgress.setText(ctx.getString(R.string.host_target_cycle_complete, progress));
            }
        }
        if (progressBar != null) {
            int pct = next <= 0 ? 100 : (int) Math.min(100, (progress * 100L) / Math.max(1, next));
            progressBar.setProgress(pct);
        }
        if (stagesRow != null) {
            stagesRow.removeAllViews();
            Object stagesObj = data.get("stages");
            if (stagesObj instanceof List) {
                LayoutInflater inflater = LayoutInflater.from(ctx);
                int index = 0;
                int currentIndex = -1;
                for (Object row : (List<?>) stagesObj) {
                    if (!(row instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> s = (Map<String, Object>) row;
                    index++;
                    if ("current".equals(String.valueOf(s.get("status")))) {
                        currentIndex = index - 1;
                    }
                    View item = inflater.inflate(R.layout.item_host_target_stage, stagesRow, false);
                    bindStageChip(ctx, item, s, index);
                    stagesRow.addView(item);
                }
                scrollToCurrentStage(stagesScroll, stagesRow, currentIndex);
            }
        }
    }

    private static void scrollToCurrentStage(
            @Nullable HorizontalScrollView stagesScroll,
            @Nullable LinearLayout stagesRow,
            int currentIndex) {
        if (stagesScroll == null || stagesRow == null || currentIndex < 0) return;
        final int scrollTo = currentIndex;
        stagesScroll.post(() -> {
            if (stagesRow.getChildCount() <= scrollTo) return;
            View target = stagesRow.getChildAt(scrollTo);
            if (target != null) {
                stagesScroll.smoothScrollTo(Math.max(0, target.getLeft() - 24), 0);
            }
        });
    }

    private static void bindStageChip(Context ctx, View item, Map<String, Object> s, int fallbackIndex) {
        TextView tvIndex = item.findViewById(R.id.tvStageIndex);
        TextView tvTitle = item.findViewById(R.id.tvStageTitle);
        TextView tvTh = item.findViewById(R.id.tvStageThreshold);
        TextView tvSalary = item.findViewById(R.id.tvStageSalary);
        TextView tvReward = item.findViewById(R.id.tvStageReward);
        TextView tvState = item.findViewById(R.id.tvStageState);
        ImageView imgStatus = item.findViewById(R.id.imgStageStatus);

        String status = String.valueOf(s.get("status"));
        boolean claimed = Boolean.TRUE.equals(s.get("claimed"));
        boolean reached = Boolean.TRUE.equals(s.get("reached"));
        if ("null".equals(status) || status.isEmpty()) {
            status = claimed || reached ? "done" : (fallbackIndex == 1 ? "current" : "locked");
        }
        String title = String.valueOf(s.get("title"));
        if (title == null || "null".equals(title) || title.isEmpty()) {
            title = ctx.getString(R.string.agency_withdraw_stage_title, fallbackIndex);
        }
        long th = toLong(s.get("threshold"));
        double hostSalary = toDouble(s.get("hostSalaryUsd"));
        double agentSalary = toDouble(s.get("agentSalaryUsd"));

        if (tvIndex != null) {
            int shown = (int) toLong(s.get("index"));
            tvIndex.setText(String.valueOf(shown > 0 ? shown : fallbackIndex));
        }
        if (tvTitle != null) tvTitle.setText(title);
        if (tvTh != null) {
            if (hostSalary > 0) {
                tvTh.setText(ctx.getString(R.string.host_target_stage_threshold_salary, th, hostSalary));
            } else {
                tvTh.setText(String.format(Locale.US, "%,d ◆", th));
            }
        }
        if (tvSalary != null) {
            if (hostSalary > 0 || agentSalary > 0) {
                tvSalary.setVisibility(View.VISIBLE);
                tvSalary.setText(ctx.getString(
                        R.string.host_target_stage_split, hostSalary, agentSalary));
            } else {
                tvSalary.setVisibility(View.GONE);
            }
        }
        bindRewardLine(ctx, tvReward, s);

        if ("done".equals(status)) {
            item.setBackgroundResource(R.drawable.bg_host_target_stage_done);
            if (tvIndex != null) {
                tvIndex.setBackgroundResource(R.drawable.bg_host_target_stage_badge_done);
            }
            if (imgStatus != null) {
                imgStatus.setVisibility(View.VISIBLE);
                imgStatus.setImageResource(R.drawable.ic_host_target_check);
            }
            if (tvState != null) {
                tvState.setText(R.string.host_target_stage_done);
                tvState.setTextColor(0xFF0F766E);
            }
        } else if ("current".equals(status)) {
            item.setBackgroundResource(R.drawable.bg_host_target_stage_current);
            if (tvIndex != null) {
                tvIndex.setBackgroundResource(R.drawable.bg_host_target_stage_badge);
            }
            if (imgStatus != null) imgStatus.setVisibility(View.GONE);
            if (tvState != null) {
                tvState.setText(reached
                        ? R.string.host_target_stage_ready_withdraw
                        : R.string.host_target_stage_current);
                tvState.setTextColor(reached ? 0xFF047857 : 0xFFB45309);
            }
        } else {
            item.setBackgroundResource(R.drawable.bg_host_target_stage);
            if (tvIndex != null) {
                tvIndex.setBackgroundResource(R.drawable.bg_host_target_stage_badge_locked);
            }
            if (imgStatus != null) {
                imgStatus.setVisibility(View.VISIBLE);
                imgStatus.setImageResource(R.drawable.ic_host_target_lock);
            }
            if (tvState != null) {
                tvState.setText(R.string.host_target_stage_locked);
                tvState.setTextColor(0xFF64748B);
            }
        }
    }

    private static void bindRewardLine(Context ctx, @Nullable TextView tvReward, Map<String, Object> s) {
        if (tvReward == null) return;
        long rewardCoins = toLong(s.get("rewardCoins"));
        long rewardDiamonds = toLong(s.get("rewardDiamonds"));
        StringBuilder reward = new StringBuilder();
        boolean any = false;
        if (rewardCoins > 0) {
            reward.append("+").append(String.format(Locale.US, "%,d", rewardCoins)).append(" coins");
            any = true;
        }
        if (rewardDiamonds > 0) {
            if (any) reward.append(" · ");
            reward.append("+").append(String.format(Locale.US, "%,d", rewardDiamonds))
                    .append(" ◆");
            any = true;
        }
        Object cosCode = s.get("rewardCosmeticCode");
        if (cosCode != null && !"null".equals(String.valueOf(cosCode))
                && !String.valueOf(cosCode).trim().isEmpty()) {
            if (any) reward.append(" · ");
            long days = Math.max(1, toLong(s.get("rewardCosmeticDays")));
            reward.append(ctx.getString(R.string.host_target_reward_frame, days));
            any = true;
        }
        Object vipLv = s.get("rewardVipLevel");
        if (vipLv instanceof Number && ((Number) vipLv).intValue() > 0) {
            if (any) reward.append(" · ");
            int vl = ((Number) vipLv).intValue();
            long vd = Math.max(1, toLong(s.get("rewardVipDays")));
            reward.append(ctx.getString(R.string.host_target_reward_vip, vl, vd));
            any = true;
        }
        if (!any) {
            tvReward.setVisibility(View.GONE);
        } else {
            tvReward.setVisibility(View.VISIBLE);
            tvReward.setText(reward.toString());
        }
    }

    private static long toLong(@Nullable Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(o).replace(",", "").trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double toDouble(@Nullable Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o == null) return 0d;
        try {
            return Double.parseDouble(String.valueOf(o).replace(",", "").trim());
        } catch (Exception e) {
            return 0d;
        }
    }
}
