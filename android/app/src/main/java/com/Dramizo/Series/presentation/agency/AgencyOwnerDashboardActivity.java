package com.Dramizo.Series.presentation.agency;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
    private final ProDashWithdrawAdapter withdrawAdapter = new ProDashWithdrawAdapter();

    private long agencyDiamonds;
    private double diamondUsdRate = 0.00005d;
    private String agencyId;
    private String agencyName;
    private long minWithdraw = 10000L;
    private MiscDtos.AgencyMineDto mineSnap;

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
        load();
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
                setT(tvPerf4Value, String.format(Locale.US, "%s/%s/%s",
                        (int) e.hostSharePercent,
                        (int) e.commissionPercent,
                        (int) e.platformCutPercent));

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
