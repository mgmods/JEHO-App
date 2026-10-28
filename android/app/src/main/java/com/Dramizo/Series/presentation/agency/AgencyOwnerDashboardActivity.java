package com.Dramizo.Series.presentation.agency;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Professional agency-owner dashboard — commission balance, hosts, month stats, withdraw log.
 */
public class AgencyOwnerDashboardActivity extends ThemedActivity {
    private ProgressBar progress;
    private TextView tvBalanceUsd;
    private TextView tvBalanceDiamonds;
    private TextView tvKpi1Value;
    private TextView tvKpi1Delta;
    private TextView tvKpi2Value;
    private TextView tvKpi2Delta;
    private TextView tvKpi3Value;
    private TextView tvKpi3Delta;
    private TextView tvPerf1Value;
    private TextView tvPerf2Value;
    private TextView tvPerf3Value;
    private TextView tvPerf4Value;
    private TextView tvWithdrawEmpty;
    private TextView tvAgencyStatsEmpty;
    private TextView tvAgencyStatsTotal;
    private TextView tvAgencyPublicId;
    private TextView tvAgencyActivationCode;
    private LinearLayout layoutAgencyMemberStats;
    private final ProDashWithdrawAdapter withdrawAdapter = new ProDashWithdrawAdapter();

    private long agencyDiamonds;
    private double diamondUsdRate = 0.00005d;
    private String agencyId;
    private String agencyName;
    private long minWithdraw = 10000L;
    private MiscDtos.AgencyMineDto mineSnap;
    private MiscDtos.AgencyStatsDto latestAgencyStats;
    private int agencyStatsVisibleCount = 40;
    // onCreate performs the first load; skip the immediate first onResume to avoid
    // duplicate network requests and overlapping dashboard renders.
    private boolean firstResumeHandled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pro_dash_board);

        progress = findViewById(R.id.progress);
        tvBalanceUsd = findViewById(R.id.tvBalanceUsd);
        tvBalanceDiamonds = findViewById(R.id.tvBalanceDiamonds);
        TextView title = findViewById(R.id.tvTitle);
        if (title != null) title.setText(R.string.agency_owner_dashboard_title);
        TextView balLab = findViewById(R.id.tvBalanceLabel);
        if (balLab != null) balLab.setText(R.string.dash_agency_current_balance);

        setLabel(R.id.tvKpi1Label, R.string.dash_total_commission);
        setLabel(R.id.tvKpi2Label, R.string.dash_active_hosts);
        setLabel(R.id.tvKpi3Label, R.string.dash_month_commission);
        setLabel(R.id.tvPerf1Label, R.string.dash_perf_month_commission);
        setLabel(R.id.tvPerf2Label, R.string.dash_perf_week_commission);
        setLabel(R.id.tvPerf3Label, R.string.dash_perf_gifts_volume);
        setLabel(R.id.tvPerf4Label, R.string.dash_perf_split);

        tvKpi1Value = findViewById(R.id.tvKpi1Value);
        tvKpi1Delta = findViewById(R.id.tvKpi1Delta);
        tvKpi2Value = findViewById(R.id.tvKpi2Value);
        tvKpi2Delta = findViewById(R.id.tvKpi2Delta);
        tvKpi3Value = findViewById(R.id.tvKpi3Value);
        tvKpi3Delta = findViewById(R.id.tvKpi3Delta);
        tvPerf1Value = findViewById(R.id.tvPerf1Value);
        tvPerf2Value = findViewById(R.id.tvPerf2Value);
        tvPerf3Value = findViewById(R.id.tvPerf3Value);
        tvPerf4Value = findViewById(R.id.tvPerf4Value);
        tvWithdrawEmpty = findViewById(R.id.tvWithdrawEmpty);
        tvAgencyStatsEmpty = findViewById(R.id.tvAgencyStatsEmpty);
        tvAgencyStatsTotal = findViewById(R.id.tvAgencyStatsTotal);
        layoutAgencyMemberStats = findViewById(R.id.layoutAgencyMemberStats);
        tvAgencyPublicId = findViewById(R.id.tvAgencyPublicId);
        tvAgencyActivationCode = findViewById(R.id.tvAgencyActivationCode);
        MaterialButton btnCopyAgencyId = findViewById(R.id.btnCopyAgencyId);
        if (btnCopyAgencyId != null) {
            btnCopyAgencyId.setOnClickListener(v -> copyToClipboard(
                    "رقم الوكالة", tvAgencyPublicId == null ? null : tvAgencyPublicId.getText().toString()));
        }
        MaterialButton btnCopyAgencyCode = findViewById(R.id.btnCopyAgencyCode);
        if (btnCopyAgencyCode != null) {
            btnCopyAgencyCode.setOnClickListener(v -> copyToClipboard(
                    "كود تفعيل الوكالة", tvAgencyActivationCode == null ? null : tvAgencyActivationCode.getText().toString()));
        }
        MaterialButton btnAgencyStatsMore = findViewById(R.id.btnAgencyStatsMore);
        if (btnAgencyStatsMore != null) {
            btnAgencyStatsMore.setOnClickListener(v -> {
                if (latestAgencyStats == null || latestAgencyStats.members == null) return;
                agencyStatsVisibleCount = Math.min(latestAgencyStats.members.size(), agencyStatsVisibleCount + 40);
                renderAgencyStats(latestAgencyStats);
            });
        }
        MaterialButton btnAgencyAudit = findViewById(R.id.btnAgencyModerationAudit);
        if (btnAgencyAudit != null) {
            btnAgencyAudit.setVisibility(View.VISIBLE);
            btnAgencyAudit.setOnClickListener(v -> loadAgencyModerationAudit());
        }

        RecyclerView recycler = findViewById(R.id.recyclerWithdraws);
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setAdapter(withdrawAdapter);
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        ImageView btnRefresh = findViewById(R.id.btnRefresh);
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> load());
        ImageView btnSecondary = findViewById(R.id.btnSecondary);
        if (btnSecondary != null) {
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setOnClickListener(v -> openManage());
        }
        MaterialButton btnWithdraw = findViewById(R.id.btnWithdraw);
        if (btnWithdraw != null) btnWithdraw.setOnClickListener(v -> openWithdraw());
        TextView seeAll = findViewById(R.id.tvSeeAllWithdraws);
        if (seeAll != null) {
            seeAll.setOnClickListener(v ->
                    startActivity(AgencyWithdrawHistoryActivity.intent(this, false)));
        }
        TextView foot = findViewById(R.id.tvFootnote);
        if (foot != null) foot.setText(R.string.dash_agency_footnote);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResumeHandled) {
            load();
        } else {
            firstResumeHandled = true;
        }
    }

    private void setLabel(int id, int res) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(res);
    }

    private void load() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyMineDto> mine =
                    ApiCall.execute(ContainerProvider.from(this).getAgencyApi().mine());
            Result<MiscDtos.AgencyStatsDto> statsResult = null;
            Result<MiscDtos.AgencyStatsDto> loadedStats = statsResult;
            if (mine.success && mine.data != null && mine.data.agency != null
                    && mine.data.agency.id != null) {
                loadedStats = ApiCall.execute(
                        ContainerProvider.from(this).getAgencyApi().stats(mine.data.agency.id));
            }
            final Result<MiscDtos.AgencyStatsDto> agencyStats = loadedStats;
            Result<WalletDtos.EconomyConfig> econ =
                    ApiCall.execute(ContainerProvider.from(this).getWalletApi().economyConfig());
            Result<WalletDtos.WalletDto> wallet =
                    ApiCall.execute(ContainerProvider.from(this).getWalletApi().getWallet());
            Result<WalletDtos.WithdrawList> withdraws =
                    ApiCall.execute(ContainerProvider.from(this).getWalletApi().withdraws());
            runOnUiThread(() -> {
                if (progress != null) progress.setVisibility(View.GONE);
                if (isFinishing() || isDestroyed()) return;
                if (!mine.success || mine.data == null || mine.data.agency == null) {
                    Toast.makeText(this,
                            mine.error != null ? mine.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                mineSnap = mine.data;
                agencyId = mine.data.agency.id;
                agencyName = mine.data.agency.name;
                setT(tvAgencyPublicId, mine.data.agency.publicId == null
                        ? "—" : mine.data.agency.publicId);
                String activationCode = mine.data.agency.activationCode;
                setT(tvAgencyActivationCode, activationCode == null || activationCode.trim().isEmpty()
                        ? "غير متاح" : activationCode);
                TextView dashboardTitle = findViewById(R.id.tvTitle);
                if (dashboardTitle != null && agencyName != null && !agencyName.trim().isEmpty()) {
                    dashboardTitle.setText(agencyName + " · " + getString(R.string.agency_owner_dashboard_title));
                }
                renderAgencyStats(agencyStats != null && agencyStats.success ? agencyStats.data : null);
                MiscDtos.AgencyEarningsDto e = mine.data.earnings;
                if (e == null) {
                    Toast.makeText(this, R.string.agency_earn_owner_only, Toast.LENGTH_LONG).show();
                    return;
                }
                if (e.diamondUsdRate > 0) diamondUsdRate = e.diamondUsdRate;
                // CLEAN ECONOMY: one diamond pool. Owner commission now lives in
                // the single wallet balance (available.personalDiamonds / wallet.diamonds).
                if (e.available != null) {
                    agencyDiamonds = Math.max(0L, e.available.personalDiamonds);
                }
                if (wallet.success && wallet.data != null) {
                    agencyDiamonds = Math.max(0L, wallet.data.diamonds);
                    if (wallet.data.diamondUsdRate > 0) diamondUsdRate = wallet.data.diamondUsdRate;
                }
                if (econ.success && econ.data != null) {
                    if (econ.data.diamondUsdRate > 0) diamondUsdRate = econ.data.diamondUsdRate;
                    if (econ.data.minWithdrawDiamonds > 0) {
                        minWithdraw = Math.max(1000L, econ.data.minWithdrawDiamonds);
                    }
                }

                double availUsd = e.available != null && e.available.personalUsd > 0
                        ? e.available.personalUsd
                        : agencyDiamonds * diamondUsdRate;
                setT(tvBalanceUsd, formatUsd(availUsd));
                setT(tvBalanceDiamonds, formatLong(agencyDiamonds) + " ◆");

                long allComm = e.totals != null ? e.totals.ownerCommissionEarned : 0L;
                double allCommUsd = e.totals != null && e.totals.ownerCommissionUsd > 0
                        ? e.totals.ownerCommissionUsd
                        : allComm * diamondUsdRate;
                setT(tvKpi1Value, formatUsd(allCommUsd));
                setT(tvKpi1Delta, deltaText(e.monthCommissionDeltaPct));

                int hosts = e.activeHosts > 0 ? e.activeHosts : e.memberCount;
                setT(tvKpi2Value, String.valueOf(Math.max(0, hosts)));
                setT(tvKpi2Delta, getString(R.string.dash_members_total, e.memberCount));

                long monthComm = 0L;
                double monthCommUsd = 0d;
                long weekComm = 0L;
                double weekCommUsd = 0d;
                long monthGross = 0L;
                if (e.periods != null && e.periods.month != null) {
                    monthComm = e.periods.month.ownerCommissionEarned;
                    monthCommUsd = e.periods.month.ownerCommissionUsd > 0
                            ? e.periods.month.ownerCommissionUsd
                            : monthComm * diamondUsdRate;
                    monthGross = e.periods.month.grossGiftsDiamonds;
                }
                if (e.periods != null && e.periods.week != null) {
                    weekComm = e.periods.week.ownerCommissionEarned;
                    weekCommUsd = e.periods.week.ownerCommissionUsd > 0
                            ? e.periods.week.ownerCommissionUsd
                            : weekComm * diamondUsdRate;
                }
                setT(tvKpi3Value, formatUsd(monthCommUsd));
                setT(tvKpi3Delta, deltaText(e.monthCommissionDeltaPct));

                setT(tvPerf1Value, formatUsd(monthCommUsd));
                setT(tvPerf2Value, formatUsd(weekCommUsd));
                setT(tvPerf3Value, formatLong(monthGross));
                // Platform economics are private: show only the host/agency split.
                setT(tvPerf4Value, String.format(Locale.US, "%s%% / %s%%",
                        (int) e.hostSharePercent,
                        (int) e.commissionPercent));

                List<WalletDtos.WithdrawDto> filtered = new ArrayList<>();
                if (withdraws.success && withdraws.data != null && withdraws.data.items != null) {
                    for (WalletDtos.WithdrawDto w : withdraws.data.items) {
                        if (ProDashWithdrawAdapter.isOwnerSource(w)) filtered.add(w);
                        if (filtered.size() >= 8) break;
                    }
                }
                withdrawAdapter.submit(filtered);
                if (tvWithdrawEmpty != null) {
                    tvWithdrawEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void loadAgencyModerationAudit() {
        if (agencyId == null || agencyId.trim().isEmpty()) return;
        Toast.makeText(this, "جاري تحميل سجل الإشراف…", Toast.LENGTH_SHORT).show();
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(
                    ContainerProvider.from(this).getAgencyApi().moderationAudit(agencyId, 100));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!result.success || result.data == null) {
                    Toast.makeText(this, result.error != null ? result.error : "تعذر تحميل سجل الإشراف",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Object rawItems = result.data.get("items");
                StringBuilder body = new StringBuilder();
                if (rawItems instanceof List) {
                    List<?> items = (List<?>) rawItems;
                    for (Object raw : items) {
                        if (!(raw instanceof Map)) continue;
                        Map<String, Object> item = (Map<String, Object>) raw;
                        String action = String.valueOf(item.get("action"));
                        String actionLabel = "mute".equals(action) ? "كتم مايك"
                                : ("remove_mic".equals(action) ? "تنزيل من المايك"
                                : ("kick".equals(action) ? "طرد من الغرفة"
                                : ("ban".equals(action) ? "حظر" : action)));
                        String createdAt = String.valueOf(item.get("createdAt"));
                        if (createdAt.length() >= 16) createdAt = createdAt.substring(0, 16).replace('T', ' ');
                        body.append("الإجراء: ").append(actionLabel).append("\n")
                                .append("المشرف: ").append(String.valueOf(item.get("actorName")))
                                .append(" (ID ").append(String.valueOf(item.get("actorPublicId"))).append(")\n")
                                .append("المستخدم: ").append(String.valueOf(item.get("targetName")))
                                .append(" (ID ").append(String.valueOf(item.get("targetPublicId"))).append(")\n")
                                .append("الغرفة: ").append(String.valueOf(item.get("roomTitle"))).append("\n")
                                .append("السبب: ").append(item.get("reason") == null ? "—" : String.valueOf(item.get("reason"))).append("\n")
                                .append("الوقت: ").append(createdAt).append(" UTC\n")
                                .append("────────────────────\n");
                    }
                }
                if (body.length() == 0) body.append("لا توجد إجراءات إشراف مسجلة حتى الآن.");
                android.widget.TextView content = new android.widget.TextView(this);
                content.setText(body.toString());
                content.setTextColor(getColor(R.color.text_primary));
                content.setTextSize(13);
                content.setPadding(dp(16), dp(12), dp(16), dp(12));
                android.widget.ScrollView scroll = new android.widget.ScrollView(this);
                scroll.addView(content);
                new AlertDialog.Builder(this)
                        .setTitle("سجل إجراءات مشرفي الوكالة")
                        .setView(scroll)
                        .setPositiveButton("إغلاق", null)
                        .show();
            });
        });
    }

    private void renderAgencyStats(@Nullable MiscDtos.AgencyStatsDto stats) {
        latestAgencyStats = stats;
        if (layoutAgencyMemberStats == null) return;
        layoutAgencyMemberStats.removeAllViews();
        MaterialButton btnMore = findViewById(R.id.btnAgencyStatsMore);
        if (stats == null) {
            if (btnMore != null) btnMore.setVisibility(View.GONE);
            if (tvAgencyStatsEmpty != null) tvAgencyStatsEmpty.setVisibility(View.VISIBLE);
            if (tvAgencyStatsTotal != null) tvAgencyStatsTotal.setText("إجمالي الهدايا: —");
            return;
        }
        if (tvAgencyStatsTotal != null) {
            String targetLabel = stats.targetEnabled
                    ? (" • التارجت " + ("weekly".equalsIgnoreCase(stats.targetPeriod) ? "الأسبوعي" : "الشهري"))
                    : " • التارجت غير مفعّل";
            String lastActivityLabel = "";
            if (stats.lastGiftActivityAt != null && stats.lastGiftActivityAt.length() >= 16) {
                lastActivityLabel = " • آخر دعم (UTC): "
                        + stats.lastGiftActivityAt.substring(0, 16).replace('T', ' ');
            }
            tvAgencyStatsTotal.setText("ملخص اليوم: دعم "
                    + formatLong(Math.max(0L, stats.dailyDiamonds)) + " ◆"
                    + " • مضيفون نشطون: " + stats.activeHostsToday
                    + " • أعضاء جدد: " + stats.newMembersToday
                    + "\nملخص الأسبوع: دعم "
                    + formatLong(Math.max(0L, stats.weeklyDiamonds)) + " ◆"
                    + " • مضيفون نشطون: " + stats.activeHostsThisWeek
                    + " • أعضاء جدد: " + stats.newMembersThisWeek
                    + "\nملخص الشهر: دعم "
                    + formatLong(Math.max(0L, stats.monthlyDiamonds)) + " ◆"
                    + " • مضيفون نشطون: " + stats.activeHostsThisMonth
                    + " • أعضاء جدد: " + stats.newMembersThisMonth
                    + lastActivityLabel
                    + "\nإجمالي الهدايا خلال العضوية الحالية: "
                    + formatLong(Math.max(0L, stats.giftsReceivedDiamonds)) + " ◆"
                    + " • الأعضاء الحاليون: " + stats.memberCount + targetLabel);
        }
        if (stats.members == null || stats.members.isEmpty()) {
            if (tvAgencyStatsEmpty != null) tvAgencyStatsEmpty.setVisibility(View.VISIBLE);
            if (btnMore != null) btnMore.setVisibility(View.GONE);
            return;
        }
        if (tvAgencyStatsEmpty != null) tvAgencyStatsEmpty.setVisibility(View.GONE);
        int count = Math.min(stats.members.size(), Math.max(40, agencyStatsVisibleCount));
        for (int i = 0; i < count; i++) {
            MiscDtos.AgencyMemberPeriodStatDto member = stats.members.get(i);
            if (member == null) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundResource(R.drawable.bg_dash_perf_cell);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = dp(7);
            card.setLayoutParams(cardParams);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(header, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView rank = new TextView(this);
            rank.setText(String.valueOf(i + 1));
            rank.setTextColor(getColor(R.color.text_secondary));
            rank.setTextSize(12);
            rank.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams rankParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            header.addView(rank, rankParams);

            LinearLayout identity = new LinearLayout(this);
            identity.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            identityParams.setMargins(dp(8), 0, dp(8), 0);
            header.addView(identity, identityParams);

            TextView name = new TextView(this);
            String displayName = member.displayName == null || member.displayName.trim().isEmpty()
                    ? "مضيف" : member.displayName;
            name.setText(displayName);
            name.setTextColor(getColor(R.color.text_primary));
            name.setTextSize(13);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            identity.addView(name);

            String role = member.role == null ? "host" : member.role.toLowerCase(Locale.ROOT);
            String roleLabel = "owner".equals(role) ? "مالك الوكالة"
                    : ("manager".equals(role) ? "مشرف" : ("host".equals(role) ? "مضيف" : "عضو"));
            TextView identityMeta = new TextView(this);
            identityMeta.setText(roleLabel + (member.publicId == null || member.publicId.isEmpty()
                    ? "" : " • ID " + member.publicId));
            identityMeta.setTextColor(getColor(R.color.text_secondary));
            identityMeta.setTextSize(11);
            identity.addView(identityMeta);

            TextView diamonds = new TextView(this);
            diamonds.setText(formatLong(Math.max(0L, member.giftsReceivedDiamonds)) + " ◆");
            diamonds.setTextColor(getColor(R.color.hiyoo_dark_green));
            diamonds.setTextSize(12);
            diamonds.setTypeface(null, android.graphics.Typeface.BOLD);
            header.addView(diamonds);

            TextView dailySupport = new TextView(this);
            dailySupport.setText("دعم اليوم: " + formatLong(Math.max(0L, member.dailyDiamonds))
                    + " ◆  • الأسبوع: " + formatLong(Math.max(0L, member.weeklyDiamonds))
                    + " ◆  • الشهر: " + formatLong(Math.max(0L, member.monthlyDiamonds)) + " ◆");
            dailySupport.setTextColor(getColor(R.color.text_secondary));
            dailySupport.setTextSize(11);
            dailySupport.setPadding(dp(36), dp(5), 0, 0);
            card.addView(dailySupport);

            if (member.targetEnabled) {
                String targetName = member.currentTargetName == null || member.currentTargetName.trim().isEmpty()
                        ? "التارجت الحالي" : member.currentTargetName;
                TextView targetLine = new TextView(this);
                targetLine.setText(targetName + " • المنجز " + formatLong(member.targetProgress)
                        + " / " + formatLong(member.currentTargetThreshold)
                        + " • المتبقي " + formatLong(member.targetRemaining));
                targetLine.setTextColor(getColor(R.color.text_secondary));
                targetLine.setTextSize(11);
                targetLine.setPadding(dp(36), dp(8), 0, dp(4));
                card.addView(targetLine);

                ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setProgress(Math.max(0, Math.min(100, member.targetProgressPercent)));
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
                progressParams.setMargins(dp(36), 0, 0, 0);
                card.addView(progressBar, progressParams);
            } else {
                TextView targetLine = new TextView(this);
                targetLine.setText("التارجت غير مفعّل حالياً");
                targetLine.setTextColor(getColor(R.color.text_secondary));
                targetLine.setTextSize(11);
                targetLine.setPadding(dp(36), dp(6), 0, 0);
                card.addView(targetLine);
            }
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(androidx.core.content.ContextCompat.getDrawable(
                    this, android.R.drawable.list_selector_background));
            card.setOnClickListener(v -> showAgencyMemberDetails(member, roleLabel));
            layoutAgencyMemberStats.addView(card);
        }
        if (btnMore != null) {
            boolean hasMore = count < stats.members.size();
            btnMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
            if (hasMore) {
                btnMore.setText("عرض بقية الأعضاء (" + (stats.members.size() - count) + ")");
            }
        }
    }

    private void showAgencyMemberDetails(MiscDtos.AgencyMemberPeriodStatDto member, String roleLabel) {
        String id = member.publicId == null || member.publicId.trim().isEmpty()
                ? member.userId : member.publicId;
        StringBuilder details = new StringBuilder();
        details.append("الرتبة: ").append(roleLabel)
                .append("\nID العضو: ").append(id)
                .append("\nهدايا فترة العضوية الحالية: ")
                .append(formatLong(Math.max(0L, member.giftsReceivedDiamonds))).append(" ◆");
        if (member.targetEnabled) {
            details.append("\n\nالتارجت: ")
                    .append(member.currentTargetName == null ? "المستوى الحالي" : member.currentTargetName)
                    .append("\nالمنجز: ").append(formatLong(member.targetProgress))
                    .append(" / ").append(formatLong(member.currentTargetThreshold))
                    .append("\nالمتبقي: ").append(formatLong(member.targetRemaining))
                    .append("\nنسبة الإنجاز: ")
                    .append(Math.max(0, Math.min(100, member.targetProgressPercent))).append("%")
                    .append("\nالفترة: ")
                    .append("weekly".equalsIgnoreCase(member.targetPeriod) ? "أسبوعية" : "شهرية");
        } else {
            details.append("\n\nالتارجت غير مفعّل حالياً.");
        }
        new AlertDialog.Builder(this)
                .setTitle(member.displayName == null || member.displayName.trim().isEmpty()
                        ? "تفاصيل العضو" : member.displayName)
                .setMessage(details.toString())
                .setNegativeButton("إغلاق", null)
                .setPositiveButton("نسخ ID", (dialog, which) -> copyToClipboard("ID العضو", id))
                .show();
    }

    private void copyToClipboard(String label, @Nullable String value) {
        if (value == null || value.trim().isEmpty() || "—".equals(value) || "غير متاح".equals(value)) {
            Toast.makeText(this, "لا توجد قيمة لنسخها حالياً", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "تعذر فتح الحافظة", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value.trim()));
        Toast.makeText(this, "تم نسخ " + label, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openWithdraw() {
        startActivity(AgencyWithdrawActivity.intent(
                this, false, agencyDiamonds, diamondUsdRate, minWithdraw, agencyId, agencyName));
    }

    private void openManage() {
        if (agencyId == null) return;
        startActivity(new Intent(this, AgencyManageActivity.class)
                .putExtra(AgencyManageActivity.EXTRA_AGENCY_ID, agencyId));
    }

    private static void setT(@Nullable TextView tv, String v) {
        if (tv != null) tv.setText(v);
    }

    private static String formatLong(long n) {
        return String.format(Locale.US, "%,d", Math.max(0L, n));
    }

    private static String formatUsd(double usd) {
        return String.format(Locale.US, "$%.2f", Math.max(0d, usd));
    }

    private String deltaText(double pct) {
        if (Double.isNaN(pct) || Double.isInfinite(pct)) return "—";
        String sign = pct > 0 ? "+" : "";
        return sign + String.format(Locale.US, "%.1f%%", pct) + " " + getString(R.string.dash_vs_prev_month);
    }
}
