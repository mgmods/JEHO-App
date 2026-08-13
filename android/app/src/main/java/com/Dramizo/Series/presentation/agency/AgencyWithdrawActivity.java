package com.Dramizo.Series.presentation.agency;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.BalanceRedirect;
import com.Dramizo.Series.util.HostTargetStagesUi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Host: single current target-stage salary card only.
 * Agency: balance packages — no host target / no call stats.
 */
public class AgencyWithdrawActivity extends ThemedActivity {
    public static final String EXTRA_FOR_HOST = "for_host";
    public static final String EXTRA_BALANCE = "balance";
    public static final String EXTRA_RATE = "rate";
    public static final String EXTRA_MIN = "min";
    public static final String EXTRA_AGENCY_ID = "agency_id";
    public static final String EXTRA_AGENCY_NAME = "agency_name";

    private boolean forHost;
    private long balance;
    private double rate = 0.00005d;
    private long minDiamonds = 10000L;
    private String agencyId;
    private String agencyName;
    private String method = "bank";
    private long selectedDiamonds = 0L;
    private String selectedStageId = null;
    private boolean canSubmit = false;
    private boolean hostTargetOn;
    @Nullable private Map<String, Object> hostTargetSnapshot;

    private TextView tvMethodLabel;
    private TextView tvSelectedAmount;
    private TextView tvMessage;
    private TextView tvEmptyPackages;
    private TextInputLayout tilAccount;
    private TextInputEditText etAccount;
    private MaterialButton btnSubmit;
    private PackageAdapter packageAdapter;

    public static Intent intent(
            Context ctx,
            boolean forHost,
            long balance,
            double rate,
            long minDiamonds,
            @Nullable String agencyId,
            @Nullable String agencyName) {
        Intent i = new Intent(ctx, AgencyWithdrawActivity.class);
        i.putExtra(EXTRA_FOR_HOST, forHost);
        i.putExtra(EXTRA_BALANCE, balance);
        i.putExtra(EXTRA_RATE, rate);
        i.putExtra(EXTRA_MIN, minDiamonds);
        i.putExtra(EXTRA_AGENCY_ID, agencyId);
        i.putExtra(EXTRA_AGENCY_NAME, agencyName);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agency_withdraw);

        forHost = getIntent().getBooleanExtra(EXTRA_FOR_HOST, false);
        balance = Math.max(0L, getIntent().getLongExtra(EXTRA_BALANCE, 0L));
        rate = getIntent().getDoubleExtra(EXTRA_RATE, 0.00005d);
        if (rate <= 0) rate = 0.00005d;
        minDiamonds = Math.max(1000L, getIntent().getLongExtra(EXTRA_MIN, 10000L));
        agencyId = getIntent().getStringExtra(EXTRA_AGENCY_ID);
        agencyName = getIntent().getStringExtra(EXTRA_AGENCY_NAME);

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvMessage = findViewById(R.id.tvMessage);
        tvEmptyPackages = findViewById(R.id.tvEmptyPackages);
        TextView tvBalance = findViewById(R.id.tvBalance);
        TextView tvMin = findViewById(R.id.tvMin);
        LinearLayout rowMethodPicker = findViewById(R.id.rowMethodPicker);
        tvMethodLabel = findViewById(R.id.tvMethodLabel);
        tvSelectedAmount = findViewById(R.id.tvSelectedAmount);
        tilAccount = findViewById(R.id.tilAccount);
        etAccount = findViewById(R.id.etAccount);
        btnSubmit = findViewById(R.id.btnSubmit);
        RecyclerView recycler = findViewById(R.id.recyclerPackages);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (tvTitle != null) {
            tvTitle.setText(forHost
                    ? R.string.agency_host_platform_withdraw_title
                    : R.string.agency_platform_withdraw_title);
        }
        if (tvMessage != null) {
            tvMessage.setText(forHost
                    ? R.string.agency_host_platform_withdraw_message
                    : R.string.agency_owner_withdraw_no_target);
        }
        paintBalance(tvBalance, tvMin);
        applyMethodUi();

        packageAdapter = new PackageAdapter(forHost, pkg -> {
            if (pkg == null || !pkg.cashable) return;
            selectedDiamonds = pkg.diamonds;
            selectedStageId = pkg.id;
            paintSelected();
        });
        if (recycler != null) {
            // Single column — host shows one stage only.
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setNestedScrollingEnabled(false);
            recycler.setAdapter(packageAdapter);
        }

        if (rowMethodPicker != null) {
            rowMethodPicker.setOnClickListener(v -> showMethodDialog());
        }
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> submit());
        }

        loadPackages();
    }

    private void paintBalance(@Nullable TextView tvBalance, @Nullable TextView tvMin) {
        double balUsd = balance * rate;
        if (tvBalance != null) {
            tvBalance.setText(getString(
                    forHost
                            ? R.string.agency_host_platform_withdraw_balance_line
                            : R.string.agency_platform_withdraw_balance_line,
                    balance, formatUsd(balUsd)));
        }
        if (tvMin != null) {
            if (forHost && hostTargetOn) {
                tvMin.setText(R.string.agency_withdraw_target_min_hint);
            } else if (forHost) {
                tvMin.setText(getString(R.string.agency_platform_withdraw_min_line, minDiamonds));
            } else {
                tvMin.setText(getString(R.string.agency_platform_withdraw_min_line, minDiamonds));
            }
        }
    }

    private void bindHostTargetSection(@Nullable Map<String, Object> data) {
        View section = findViewById(R.id.sectionHostTargetWithdraw);
        if (!forHost || section == null) {
            if (section != null) section.setVisibility(View.GONE);
            return;
        }
        if (data == null || !Boolean.TRUE.equals(data.get("enabled"))) {
            section.setVisibility(View.GONE);
            hostTargetOn = false;
            hostTargetSnapshot = null;
            return;
        }
        hostTargetOn = true;
        hostTargetSnapshot = data;
        HostTargetStagesUi.bindSection(
                this,
                section,
                findViewById(R.id.tvHostTargetMonthWithdraw),
                findViewById(R.id.tvHostTargetProgressWithdraw),
                findViewById(R.id.progressHostTargetWithdraw),
                findViewById(R.id.hostTargetStagesRowWithdraw),
                data);
    }

    private void loadPackages() {
        if (forHost) {
            loadHostCurrentStage();
        } else {
            loadAgencyBalancePackages();
        }
    }

    /** Host: current target-stage card when ladder is on; otherwise min balance packages. */
    private void loadHostCurrentStage() {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            String aid = agencyId != null ? agencyId : "";
            Result<Map<String, Object>> opts = ApiCall.execute(
                    ContainerProvider.from(this).getUserApi()
                            .hostTargetWithdrawOptions("host", aid.isEmpty() ? null : aid));

            List<WalletDtos.WithdrawPackageDto> items = new ArrayList<>();
            String serverMessage = null;
            boolean allowed = false;
            boolean useBalancePackages = false;

            if (opts != null && opts.success && opts.data != null) {
                Map<String, Object> data = opts.data;
                double r = toDouble(data.get("diamondUsdRate"));
                if (r > 0) rate = r;
                long minFrom10 = Math.max(1L, Math.round(10.0 / Math.max(1e-9, rate)));
                minDiamonds = Math.max(minDiamonds, minFrom10);
                allowed = Boolean.TRUE.equals(data.get("fullBalanceAllowed"));
                boolean targetOn = Boolean.TRUE.equals(data.get("enabled"))
                        || Boolean.TRUE.equals(data.get("targetRequired"));
                Object msg = data.get("message");
                if (msg != null) serverMessage = String.valueOf(msg);

                Object itemsObj = data.get("items");
                if (itemsObj instanceof List) {
                    for (Object row : (List<?>) itemsObj) {
                        if (!(row instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) row;
                        WalletDtos.WithdrawPackageDto p = stageFromMap(m);
                        if (p == null || p.diamonds <= 0) continue;
                        if (p.diamonds > balance) p.cashable = false;
                        items.add(p);
                        break; // one stage only
                    }
                }
                // Target ladder OFF (or no stage) → half/full packages by min amount.
                if (items.isEmpty() && allowed && !targetOn) {
                    useBalancePackages = true;
                }
            } else {
                serverMessage = opts != null && opts.error != null
                        ? String.valueOf(opts.error)
                        : getString(R.string.agency_withdraw_target_load_fail);
            }

            if (useBalancePackages) {
                items = buildBalancePackages();
                allowed = !items.isEmpty();
                if (serverMessage == null || serverMessage.isEmpty()) {
                    serverMessage = getString(R.string.agency_host_platform_withdraw_message_nontarget);
                }
            }

            final List<WalletDtos.WithdrawPackageDto> finalItems = items;
            final String finalMsg = serverMessage;
            final boolean finalAllowed = allowed;
            final boolean balanceMode = useBalancePackages;
            final Map<String, Object> targetSnap = opts != null && opts.success && opts.data != null
                    ? opts.data : null;
            runOnUiThread(() -> {
                hostTargetOn = targetSnap != null && (Boolean.TRUE.equals(targetSnap.get("enabled"))
                        || Boolean.TRUE.equals(targetSnap.get("targetRequired")));
                bindHostTargetSection(targetSnap);
                canSubmit = finalAllowed;
                paintBalance(findViewById(R.id.tvBalance), findViewById(R.id.tvMin));
                if (tvMessage != null && finalMsg != null && !finalMsg.isEmpty()) {
                    tvMessage.setText(finalMsg);
                }
                paintEmptyPackages(finalItems, balanceMode, targetSnap);
                if (packageAdapter != null) packageAdapter.submit(finalItems);
                selectedDiamonds = 0L;
                selectedStageId = null;
                WalletDtos.WithdrawPackageDto pick = null;
                for (WalletDtos.WithdrawPackageDto p : finalItems) {
                    if (p != null && p.cashable && p.diamonds <= balance) {
                        pick = p;
                        break;
                    }
                }
                // Still select non-cashable current stage so UI shows it.
                if (pick == null && !finalItems.isEmpty()) pick = finalItems.get(0);
                if (pick != null && packageAdapter != null) {
                    packageAdapter.selectById(pick.id);
                    if (pick.cashable) {
                        selectedDiamonds = pick.diamonds;
                        selectedStageId = pick.id;
                        canSubmit = true;
                    }
                }
                paintSelected();
            });
        });
    }

    /** Builds half/full packages when balance ≥ min (agency + host-without-target). */
    private List<WalletDtos.WithdrawPackageDto> buildBalancePackages() {
        List<WalletDtos.WithdrawPackageDto> items = new ArrayList<>();
        if (balance < minDiamonds) return items;
        long half = balance / 2;
        if (half >= minDiamonds) {
            WalletDtos.WithdrawPackageDto p = makePkg("half", half, half * rate,
                    getString(R.string.agency_withdraw_pkg_half));
            p.cashable = true;
            p.stageStatus = "balance";
            items.add(p);
        }
        WalletDtos.WithdrawPackageDto full = makePkg("full", balance, balance * rate,
                getString(R.string.agency_withdraw_pkg_full_balance));
        full.cashable = true;
        full.isFullBalance = true;
        full.stageStatus = "full";
        items.add(full);
        return items;
    }

    private void paintEmptyPackages(
            List<WalletDtos.WithdrawPackageDto> items,
            boolean showMinGate,
            @Nullable Map<String, Object> targetSnap) {
        if (tvEmptyPackages == null) return;
        boolean empty = items == null || items.isEmpty();
        if (!empty) {
            // Show stage-specific hint on the single non-cashable card.
            if (forHost && hostTargetOn && items.size() == 1 && !items.get(0).cashable) {
                WalletDtos.WithdrawPackageDto p = items.get(0);
                tvEmptyPackages.setVisibility(View.VISIBLE);
                tvEmptyPackages.setText(getString(
                        R.string.agency_withdraw_stage_progress_blocked,
                        p.stageIndex > 0 ? p.stageIndex : 1,
                        p.thresholdDiamonds,
                        p.progressDiamonds,
                        Math.max(0L, p.remainingDiamonds)));
                return;
            }
            if (forHost && hostTargetOn && items.size() == 1 && items.get(0).cashable
                    && items.get(0).diamonds > balance) {
                WalletDtos.WithdrawPackageDto p = items.get(0);
                tvEmptyPackages.setVisibility(View.VISIBLE);
                tvEmptyPackages.setText(getString(
                        R.string.agency_withdraw_stage_balance_blocked,
                        p.stageIndex > 0 ? p.stageIndex : 1,
                        formatUsd(p.hostSalaryUsd > 0 ? p.hostSalaryUsd : p.usd),
                        p.diamonds,
                        balance));
                return;
            }
            tvEmptyPackages.setVisibility(View.GONE);
            return;
        }
        tvEmptyPackages.setVisibility(View.VISIBLE);
        if (forHost && hostTargetOn && targetSnap != null) {
            long progress = toLong(targetSnap.get("progress"));
            Object stagesObj = targetSnap.get("stages");
            if (stagesObj instanceof List) {
                for (Object row : (List<?>) stagesObj) {
                    if (!(row instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> s = (Map<String, Object>) row;
                    if (!"current".equals(String.valueOf(s.get("status")))) continue;
                    long th = toLong(s.get("threshold"));
                    int idx = (int) toLong(s.get("index"));
                    tvEmptyPackages.setText(getString(
                            R.string.agency_withdraw_stage_progress_blocked,
                            idx > 0 ? idx : 1,
                            th,
                            progress,
                            Math.max(0L, th - progress)));
                    return;
                }
            }
            tvEmptyPackages.setText(R.string.agency_withdraw_stage_not_ready);
            return;
        }
        long need = Math.max(0L, minDiamonds - balance);
        if (showMinGate && balance < minDiamonds) {
            tvEmptyPackages.setText(getString(
                    R.string.agency_withdraw_below_min,
                    minDiamonds,
                    balance,
                    need,
                    formatUsd(minDiamonds * rate)));
        } else if (forHost) {
            tvEmptyPackages.setText(R.string.agency_withdraw_stage_not_ready);
        } else {
            tvEmptyPackages.setText(R.string.agency_platform_withdraw_insufficient);
        }
    }

    /** Agency: balance slices + full — no target stages. */
    private void loadAgencyBalancePackages() {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            String aid = agencyId != null ? agencyId : "";
            Result<Map<String, Object>> opts = ApiCall.execute(
                    ContainerProvider.from(this).getUserApi()
                            .hostTargetWithdrawOptions("agency", aid.isEmpty() ? null : aid));
            String serverMessage = null;
            if (opts != null && opts.success && opts.data != null) {
                double r = toDouble(opts.data.get("diamondUsdRate"));
                if (r > 0) rate = r;
                Object msg = opts.data.get("message");
                if (msg != null) serverMessage = String.valueOf(msg);
            } else if (opts != null && opts.error != null) {
                serverMessage = String.valueOf(opts.error);
            }
            long minFrom10 = Math.max(1L, Math.round(10.0 / Math.max(1e-9, rate)));
            minDiamonds = Math.max(minDiamonds, minFrom10);

            List<WalletDtos.WithdrawPackageDto> items = buildBalancePackages();

            final List<WalletDtos.WithdrawPackageDto> finalItems = items;
            final String finalMsg = serverMessage != null
                    ? serverMessage
                    : getString(R.string.agency_owner_withdraw_no_target);
            runOnUiThread(() -> {
                hostTargetOn = false;
                bindHostTargetSection(null);
                canSubmit = !finalItems.isEmpty();
                paintBalance(findViewById(R.id.tvBalance), findViewById(R.id.tvMin));
                if (tvMessage != null) tvMessage.setText(finalMsg);
                paintEmptyPackages(finalItems, true, null);
                if (packageAdapter != null) packageAdapter.submit(finalItems);
                selectedDiamonds = 0L;
                selectedStageId = null;
                if (!finalItems.isEmpty()) {
                    WalletDtos.WithdrawPackageDto pick = finalItems.get(finalItems.size() - 1);
                    packageAdapter.selectById(pick.id);
                    selectedDiamonds = pick.diamonds;
                    selectedStageId = pick.id;
                }
                paintSelected();
            });
        });
    }

    @Nullable
    private WalletDtos.WithdrawPackageDto stageFromMap(Map<String, Object> m) {
        String id = m.get("id") != null ? String.valueOf(m.get("id")) : null;
        if (id == null || id.isEmpty()) return null;
        long diamonds = toLong(m.get("diamonds"));
        double usd = toDouble(m.get("usd"));
        if (usd <= 0 && diamonds > 0) usd = diamonds * rate;
        boolean cashable = Boolean.TRUE.equals(m.get("cashable"));
        int stageIndex = (int) toLong(m.get("stageIndex"));
        String title = m.get("title") != null ? String.valueOf(m.get("title")) : null;
        String status = m.get("status") != null ? String.valueOf(m.get("status")) : "current";
        double hostUsd = toDouble(m.get("hostSalaryUsd"));
        long remaining = toLong(m.get("remaining"));
        long progress = toLong(m.get("progress"));
        long threshold = toLong(m.get("threshold"));
        if (title == null || title.isEmpty()) {
            title = stageIndex > 0
                    ? getString(R.string.agency_withdraw_stage_title, stageIndex)
                    : id;
        }
        WalletDtos.WithdrawPackageDto p = makePkg(id, diamonds, usd, title);
        p.cashable = cashable;
        p.stageIndex = stageIndex;
        p.stageStatus = status;
        p.hostSalaryUsd = hostUsd;
        p.progressDiamonds = progress;
        p.remainingDiamonds = remaining;
        p.thresholdDiamonds = threshold;
        return p;
    }

    private static WalletDtos.WithdrawPackageDto makePkg(
            String id, long diamonds, double usd, @Nullable String label) {
        WalletDtos.WithdrawPackageDto p = new WalletDtos.WithdrawPackageDto();
        p.id = id != null ? id : String.valueOf(diamonds);
        p.diamonds = diamonds;
        p.usd = Math.max(0d, usd);
        p.label = label != null && !label.trim().isEmpty()
                ? label.trim()
                : formatUsdStatic(p.usd);
        p.currency = "USD";
        p.cashable = true;
        return p;
    }

    private void paintSelected() {
        if (tvSelectedAmount == null) return;
        if (selectedDiamonds <= 0) {
            tvSelectedAmount.setText(R.string.agency_withdraw_pick_amount_first);
            return;
        }
        double usd = selectedDiamonds * rate;
        if (packageAdapter != null) {
            WalletDtos.WithdrawPackageDto p = packageAdapter.selectedPackage();
            if (p != null && p.usd > 0) usd = p.usd;
        }
        tvSelectedAmount.setText(getString(
                R.string.agency_withdraw_selected_amount,
                selectedDiamonds,
                formatUsd(usd)));
    }

    private void showMethodDialog() {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_agency_payout_method, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            AuraDialogHelper.configureShown(dialog);
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) bs.setBackgroundResource(android.R.color.transparent);
        });

        View.OnClickListener pick = v -> {
            if (v.getId() == R.id.optBank) method = "bank";
            else if (v.getId() == R.id.optUsdt) method = "usdt";
            else if (v.getId() == R.id.optPaypal) method = "paypal";
            else if (v.getId() == R.id.optShamCash) method = "sham_cash";
            applyMethodUi();
            dialog.dismiss();
        };
        TextView optBank = sheet.findViewById(R.id.optBank);
        TextView optUsdt = sheet.findViewById(R.id.optUsdt);
        TextView optPaypal = sheet.findViewById(R.id.optPaypal);
        TextView optSham = sheet.findViewById(R.id.optShamCash);
        if (optBank != null) optBank.setOnClickListener(pick);
        if (optUsdt != null) optUsdt.setOnClickListener(pick);
        if (optPaypal != null) optPaypal.setOnClickListener(pick);
        if (optSham != null) optSham.setOnClickListener(pick);
        dialog.show();
    }

    private void applyMethodUi() {
        if (tvMethodLabel != null) {
            tvMethodLabel.setText(methodLabelRes());
        }
        if (tilAccount != null) {
            if ("usdt".equals(method)) {
                tilAccount.setHint(getString(R.string.agency_withdraw_account_usdt_hint));
            } else if ("paypal".equals(method)) {
                tilAccount.setHint(getString(R.string.agency_withdraw_account_paypal_hint));
            } else if ("sham_cash".equals(method)) {
                tilAccount.setHint(getString(R.string.agency_withdraw_account_sham_hint));
            } else {
                tilAccount.setHint(getString(R.string.agency_withdraw_account_bank_hint));
            }
        }
    }

    private int methodLabelRes() {
        if ("usdt".equals(method)) return R.string.agency_withdraw_method_usdt;
        if ("paypal".equals(method)) return R.string.agency_withdraw_method_paypal;
        if ("sham_cash".equals(method)) return R.string.agency_withdraw_method_sham_cash;
        return R.string.agency_withdraw_method_bank;
    }

    private void submit() {
        long diamonds = selectedDiamonds;
        String account = etAccount != null && etAccount.getText() != null
                ? etAccount.getText().toString().trim() : "";
        if (diamonds <= 0 || selectedStageId == null || selectedStageId.isEmpty()) {
            if (balance < minDiamonds && !forHost) {
                Toast.makeText(this, getString(
                        R.string.agency_withdraw_below_min,
                        minDiamonds,
                        balance,
                        Math.max(0L, minDiamonds - balance),
                        formatUsd(minDiamonds * rate)), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.agency_withdraw_pick_amount_first, Toast.LENGTH_SHORT)
                        .show();
            }
            return;
        }
        if (forHost) {
            WalletDtos.WithdrawPackageDto sel =
                    packageAdapter != null ? packageAdapter.selectedPackage() : null;
            if (sel == null || !sel.cashable || !canSubmit) {
                if (sel != null && !sel.cashable && hostTargetOn) {
                    Toast.makeText(this, getString(
                            R.string.agency_withdraw_stage_progress_blocked,
                            sel.stageIndex > 0 ? sel.stageIndex : 1,
                            sel.thresholdDiamonds,
                            sel.progressDiamonds,
                            Math.max(0L, sel.remainingDiamonds)), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.agency_withdraw_stage_not_ready, Toast.LENGTH_LONG)
                            .show();
                }
                return;
            }
        } else if (!canSubmit) {
            Toast.makeText(this, R.string.agency_platform_withdraw_insufficient, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (diamonds > balance) {
            Toast.makeText(this, R.string.agency_platform_withdraw_insufficient,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!forHost && diamonds < minDiamonds) {
            Toast.makeText(this, R.string.agency_platform_withdraw_insufficient,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (account.isEmpty()) {
            Toast.makeText(this, R.string.agency_distribute_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnSubmit != null) btnSubmit.setEnabled(false);
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            WalletDtos.WithdrawRequest body =
                    new WalletDtos.WithdrawRequest((int) Math.min(Integer.MAX_VALUE, diamonds),
                            method, account);
            if (body.payoutDetails == null) {
                body.payoutDetails = new HashMap<>();
            }
            body.payoutDetails.put("channel", "platform");
            body.payoutDetails.put("source", forHost ? "agency_host" : "agency_commission");
            body.payoutDetails.put("stream", "agency");
            body.payoutDetails.put("currency", "USD");
            body.payoutDetails.put("usdAmount", selectedUsd(diamonds));
            body.payoutDetails.put("account", account);
            body.payoutDetails.put("method", method);
            if (forHost) {
                body.payoutDetails.put("stageId", selectedStageId);
                body.payoutDetails.put("targetStageId", selectedStageId);
                body.payoutDetails.put("role", "host");
            } else {
                body.payoutDetails.put("stageId", selectedStageId);
                body.payoutDetails.put("role", "agency");
            }
            if (agencyId != null && !agencyId.isEmpty()) {
                body.payoutDetails.put("agencyId", agencyId);
            }
            if (agencyName != null && !agencyName.isEmpty()) {
                body.payoutDetails.put("agencyName", agencyName);
            }
            Result<Object> r = ApiCall.execute(
                    ContainerProvider.from(this).getWalletApi().withdraw(body));
            runOnUiThread(() -> {
                if (btnSubmit != null) btnSubmit.setEnabled(true);
                if (r.success) {
                    Toast.makeText(this, R.string.agency_platform_withdraw_ok, Toast.LENGTH_LONG)
                            .show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    BalanceRedirect.handle(this, r.error);
                }
            });
        });
    }

    private double selectedUsd(long diamonds) {
        if (packageAdapter != null) {
            WalletDtos.WithdrawPackageDto p = packageAdapter.selectedPackage();
            if (p != null && p.diamonds == diamonds && p.usd > 0) return p.usd;
        }
        return diamonds * rate;
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

    private static String formatUsd(double usd) {
        return formatUsdStatic(usd);
    }

    private static String formatUsdStatic(double usd) {
        double v = Math.max(0d, usd);
        if (Math.abs(v - Math.rint(v)) < 0.001d) {
            return String.format(Locale.US, "$%.0f", Math.rint(v));
        }
        return String.format(Locale.US, "$%.2f", v);
    }

    private static final class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.VH> {
        interface Listener {
            void onSelect(WalletDtos.WithdrawPackageDto pkg);
        }

        private final List<WalletDtos.WithdrawPackageDto> items = new ArrayList<>();
        private final Listener listener;
        private final boolean forHost;
        private int selected = -1;

        PackageAdapter(boolean forHost, Listener listener) {
            this.forHost = forHost;
            this.listener = listener;
        }

        void submit(List<WalletDtos.WithdrawPackageDto> data) {
            items.clear();
            selected = -1;
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        void selectById(@Nullable String id) {
            if (id == null) return;
            for (int i = 0; i < items.size(); i++) {
                if (id.equals(items.get(i).id)) {
                    int old = selected;
                    selected = i;
                    if (old >= 0) notifyItemChanged(old);
                    notifyItemChanged(selected);
                    return;
                }
            }
        }

        @Nullable
        WalletDtos.WithdrawPackageDto selectedPackage() {
            if (selected < 0 || selected >= items.size()) return null;
            return items.get(selected);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_withdraw_package, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.WithdrawPackageDto p = items.get(position);
            if (p.usd > 0) {
                h.tvUsd.setText(formatUsdStatic(p.usd));
            } else {
                h.tvUsd.setText(p.label != null ? p.label : "—");
            }
            h.tvDiamonds.setText(String.format(Locale.US, "%,d ألماس", p.diamonds));
            if (h.tvHint != null) {
                boolean balancePkg = p.isFullBalance
                        || "full".equals(p.id)
                        || "half".equals(p.id)
                        || "balance".equals(p.stageStatus)
                        || "full".equals(p.stageStatus);
                if (!forHost || balancePkg) {
                    if (p.isFullBalance || "full".equals(p.id)) {
                        h.tvHint.setText(R.string.agency_withdraw_pkg_full_balance);
                    } else {
                        h.tvHint.setText(p.label != null ? p.label : "—");
                    }
                } else if (p.cashable) {
                    h.tvHint.setText(String.format(Locale.US,
                            "مرحلتك %d · جاهزة للسحب\n%s",
                            p.stageIndex > 0 ? p.stageIndex : 1,
                            formatUsdStatic(p.hostSalaryUsd > 0 ? p.hostSalaryUsd : p.usd)));
                } else {
                    long rem = p.remainingDiamonds;
                    h.tvHint.setText(String.format(Locale.US,
                            "مرحلتك %d · جارية\nمتبقي %,d ألماس",
                            p.stageIndex > 0 ? p.stageIndex : 1,
                            Math.max(0L, rem)));
                }
            }
            boolean canPick = p.cashable;
            h.card.setBackgroundResource(position == selected
                    ? R.drawable.bg_withdraw_package_selected
                    : R.drawable.bg_withdraw_package);
            h.itemView.setAlpha(canPick || forHost ? 1f : 0.42f);
            h.itemView.setOnClickListener(v -> {
                if (!p.cashable) {
                    Toast.makeText(v.getContext(),
                            R.string.agency_withdraw_stage_not_ready,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                int old = selected;
                selected = h.getBindingAdapterPosition();
                if (old >= 0) notifyItemChanged(old);
                if (selected >= 0) notifyItemChanged(selected);
                if (listener != null && selected >= 0 && selected < items.size()) {
                    listener.onSelect(items.get(selected));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final View card;
            final TextView tvUsd;
            final TextView tvDiamonds;
            final TextView tvHint;

            VH(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardPackage);
                tvUsd = itemView.findViewById(R.id.tvUsd);
                tvDiamonds = itemView.findViewById(R.id.tvDiamonds);
                tvHint = itemView.findViewById(R.id.tvPkgHint);
            }
        }
    }
}
