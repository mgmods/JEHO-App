package com.Dramizo.Series.presentation.agency;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityAgencyBinding;
import com.Dramizo.Series.databinding.DialogAgencyConfirmBinding;
import com.Dramizo.Series.databinding.DialogCreateAgencyBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.RoomOpenChooser;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Collections;
import java.util.Locale;

public class AgencyActivity extends ThemedActivity {
    private static final int PERIOD_WEEK = 0;
    private static final int PERIOD_MONTH = 1;
    private static final int PERIOD_ALL = 2;

    private AgencyViewModel vm;
    private AgencyAdapter adapter;
    private String myAgencyId;
    private MiscDtos.AgencyMineDto myAgency;
    private MiscDtos.AgencyEarningsDto lastEarnings;
    private MiscDtos.AgencyHostDashboardDto lastHostDash;
    private int selectedPeriod = PERIOD_MONTH;
    private boolean canLeave;
    private boolean canManage;
    private boolean canDeleteAgency;
    private ActivityAgencyBinding binding;
    private BottomSheetDialog applicationSheet;
    private com.google.android.material.button.MaterialButton applicationSubmit;
    private boolean awaitingFirstMine = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgencyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Avoid guest/flash until mine cache or network paints the real role.
        if (binding.tvPricing != null) binding.tvPricing.setVisibility(View.GONE);
        if (binding.cardJoinByCode != null) binding.cardJoinByCode.setVisibility(View.GONE);
        if (binding.btnBecomeAgent != null) binding.btnBecomeAgent.setVisibility(View.GONE);
        if (binding.cardMyAgency != null) binding.cardMyAgency.setVisibility(View.GONE);
        AppLoadingOverlay.showUntilReady(this);
        binding.btnBack.setOnClickListener(v -> navigateUp());
        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(AgencyViewModel.class);
        binding.btnRefresh.setOnClickListener(v -> vm.load());
        if (binding.btnAgencyMenu != null) {
            binding.btnAgencyMenu.setOnClickListener(v -> showAgencyToolbarMenu());
        }
        // Safety: never leave Mikoo spinner forever if mine fails silently.
        binding.getRoot().postDelayed(() -> {
            if (awaitingFirstMine) {
                awaitingFirstMine = false;
                AppLoadingOverlay.hide(this);
                if (binding.tvPricing != null) binding.tvPricing.setVisibility(View.VISIBLE);
                if (binding.cardJoinByCode != null) binding.cardJoinByCode.setVisibility(View.VISIBLE);
                if (binding.btnBecomeAgent != null) binding.btnBecomeAgent.setVisibility(View.VISIBLE);
            }
        }, 8_000L);
        adapter = new AgencyAdapter(new AgencyAdapter.Listener() {
            @Override public void onJoin(String agencyId) {
                // Join is by private activation code only — focus the code field.
                if (binding.cardJoinByCode.getVisibility() == View.VISIBLE) {
                    binding.etActivationCode.requestFocus();
                    Toast.makeText(AgencyActivity.this,
                            R.string.agency_join_by_code_hint, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onLeave(String agencyId) {
                boolean pending = isPendingMembership(myAgency);
                showConfirmSheet(
                        pending
                                ? getString(R.string.agency_cancel_join_title)
                                : getString(R.string.agency_leave_title),
                        pending
                                ? getString(R.string.agency_cancel_join_message)
                                : getString(R.string.agency_leave_message),
                        pending
                                ? getString(R.string.agency_cancel_join)
                                : getString(R.string.agency_leave),
                        () -> vm.leave(agencyId));
            }
            @Override public void onManage(String agencyId) { openManage(agencyId); }
            @Override public void onEnterLive(String agencyId, String openRoomId) {
                enterLiveAgency(agencyId, openRoomId);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setNestedScrollingEnabled(false);
        binding.btnJoinByCode.setOnClickListener(v -> {
            String code = binding.etActivationCode.getText() != null
                    ? binding.etActivationCode.getText().toString().trim() : "";
            if (code.length() < 4) {
                Toast.makeText(this, R.string.agency_join_code_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            vm.joinByCode(code);
        });
        if (binding.btnSearchAgency != null) {
            binding.btnSearchAgency.setOnClickListener(v -> {
                String q = binding.etAgencySearch != null && binding.etAgencySearch.getText() != null
                        ? binding.etAgencySearch.getText().toString().trim() : "";
                if (q.length() < 3) {
                    Toast.makeText(this, R.string.agency_search_code_min, Toast.LENGTH_SHORT).show();
                    return;
                }
                vm.searchAgencies(q);
            });
        }
        binding.btnCopyCode.setOnClickListener(v -> {
            CharSequence code = binding.tvActivationCode.getText();
            if (code == null || code.length() == 0) return;
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("agency_code", code));
                Toast.makeText(this, R.string.agency_copied, Toast.LENGTH_SHORT).show();
            }
        });
        binding.btnManageAgency.setOnClickListener(v -> {
            if (myAgencyId != null) openManage(myAgencyId);
        });
        if (binding.btnWithdrawAgencyEarnings != null) {
            binding.btnWithdrawAgencyEarnings.setOnClickListener(v -> openDiamondWithdraw());
        }
        if (binding.btnDistributeAgencyEarnings != null) {
            binding.btnDistributeAgencyEarnings.setOnClickListener(v -> showHostPayoutQueue());
        }
        if (binding.btnHostWithdraw != null) {
            binding.btnHostWithdraw.setOnClickListener(v -> { /* disabled */ });
        }
        if (binding.btnHostViewEarnings != null) {
            binding.btnHostViewEarnings.setOnClickListener(v -> {
                Intent i = new Intent(this,
                        com.Dramizo.Series.presentation.wallet.BagActivity.class);
                i.putExtra(com.Dramizo.Series.presentation.wallet.BagActivity.EXTRA_TAB, 1);
                startActivity(i);
            });
        }
        View btnHostRequest = binding.getRoot().findViewById(R.id.btnHostRequestPayout);
        if (btnHostRequest != null) {
            btnHostRequest.setOnClickListener(v -> showHostRequestPayoutDialog());
        }
        if (binding.chipPeriodWeek != null) {
            binding.chipPeriodWeek.setOnClickListener(v -> setPeriod(PERIOD_WEEK));
            binding.chipPeriodMonth.setOnClickListener(v -> setPeriod(PERIOD_MONTH));
            binding.chipPeriodAll.setOnClickListener(v -> setPeriod(PERIOD_ALL));
            applyPeriodChips();
        }
        vm.getAgencies().observe(this, list -> rebindAgencyDirectory());
        vm.getSearchAttempted().observe(this, attempted -> rebindAgencyDirectory());
        vm.getMine().observe(this, m -> {
            if (awaitingFirstMine) {
                awaitingFirstMine = false;
                AppLoadingOverlay.hide(this);
            }
            if (m != null && m.application != null && m.agency == null
                    && "approved".equalsIgnoreCase(m.application.status)) {
                // An approved application is only valid while its created agency still exists.
                m.application = null;
            }
            myAgency = m;
            if (m != null && m.agency != null) {
                myAgencyId = m.agency.id;
                String role = m.role != null ? m.role.toLowerCase(Locale.US) : "";
                boolean pendingJoin = isPendingMembership(m);
                canLeave = pendingJoin || !"owner".equals(role);
                canManage = !pendingJoin && ("owner".equals(role) || "manager".equals(role));
                canDeleteAgency = !pendingJoin && "owner".equals(role);
                binding.tvPricing.setVisibility(View.GONE);
                binding.cardJoinByCode.setVisibility(View.GONE);
                if (binding.btnBecomeAgent != null) binding.btnBecomeAgent.setVisibility(View.GONE);
                binding.cardMyAgency.setVisibility(View.VISIBLE);
                boolean isHost = !pendingJoin
                        && ("host".equals(role) || "member".equals(role));
                boolean isAgent = !pendingJoin && ("owner".equals(role) || "manager".equals(role));
                if (binding.sectionEarnings != null) {
                    binding.sectionEarnings.setVisibility(isAgent ? View.VISIBLE : View.GONE);
                }
                if (binding.rowOwnerEarningsActions != null) {
                    // Withdraw / distribute: owner only (not manager, not host/member).
                    binding.rowOwnerEarningsActions.setVisibility(
                            !pendingJoin && "owner".equals(role) ? View.VISIBLE : View.GONE);
                }
                if (binding.rowPeriodChips != null) {
                    binding.rowPeriodChips.setVisibility(pendingJoin ? View.GONE : View.VISIBLE);
                }
                bindHostDashboard(isHost ? m.hostDashboard : null);
                if (isHost) loadHostMonthlyTarget();
                else hideHostTarget();
                binding.tvMyAgencyName.setText(
                        m.agency.name != null ? m.agency.name : getString(R.string.agency));
                com.Dramizo.Series.util.AgencyVerifiedBadge.bind(
                        binding.tvMyAgencyName, null, m.agency);
                binding.tvMyAgencyMeta.setText(formatAgencyMeta(m));
                updateHeroEyebrow(role, pendingJoin);
                if (canManage && m.agency.activationCode != null
                        && !m.agency.activationCode.isEmpty()) {
                    // Code lives in toolbar menu — keep text ready, hide inline block.
                    if (binding.tvActivationCode != null) {
                        binding.tvActivationCode.setText(m.agency.activationCode);
                    }
                }
                binding.rowActivationCode.setVisibility(View.GONE);
                hideInlineHubActions();
                rebindAgencyDirectory();
                if (pendingJoin) {
                    hideAgentStats();
                    hideHostDashboard();
                    if (binding.rowPeriodChips != null) {
                        binding.rowPeriodChips.setVisibility(View.GONE);
                    }
                    showEarningsMessage(getString(R.string.agency_join_pending_hint));
                } else if (isHost) {
                    showHostEarningsOnly();
                    if (m.hostDashboard != null) bindHostDashboard(m.hostDashboard);
                    rebindPeriodUi();
                } else if (m.earnings == null) {
                    hideHostDashboard();
                    showEarningsMessage(getString(R.string.agency_earn_owner_only));
                }
            } else {
                myAgencyId = null;
                canLeave = false;
                canManage = false;
                canDeleteAgency = false;
                binding.tvPricing.setVisibility(View.VISIBLE);
                binding.cardJoinByCode.setVisibility(View.VISIBLE);
                if (binding.btnBecomeAgent != null) binding.btnBecomeAgent.setVisibility(View.VISIBLE);
                binding.cardMyAgency.setVisibility(View.GONE);
                if (binding.sectionEarnings != null) {
                    binding.sectionEarnings.setVisibility(View.GONE);
                }
                clearEarningsCards();
                binding.rowActivationCode.setVisibility(View.GONE);
                if (binding.rowOwnerEarningsActions != null) {
                    binding.rowOwnerEarningsActions.setVisibility(View.GONE);
                }
                hideHostDashboard();
                hideHostTarget();
                lastEarnings = null;
                lastHostDash = null;
                rebindAgencyDirectory();
            }
            bindApplicationStatus(m);
            syncAgencyRoomButton();
        });
        vm.getEarnings().observe(this, e -> {
            if (e == null || e.totals == null) {
                return;
            }
            // Hosts/members use hostDashboard — ignore agent earnings payload.
            boolean isHost = myAgency != null && myAgency.role != null
                    && ("host".equalsIgnoreCase(myAgency.role)
                    || "member".equalsIgnoreCase(myAgency.role))
                    && !isPendingMembership(myAgency);
            if (isHost) return;
            binding.cardMyAgency.setVisibility(View.VISIBLE);
            if (binding.sectionEarnings != null) {
                binding.sectionEarnings.setVisibility(View.VISIBLE);
            }
            bindEarningsCards(e);
            // Actions moved to toolbar menu
            hideInlineHubActions();
            // Never show host withdraw on agency hub.
            if (binding.btnHostWithdraw != null) {
                binding.btnHostWithdraw.setVisibility(View.GONE);
            }
            syncHostEarningsHelp(false);
        });
        vm.getMessage().observe(this, m -> {
            if ("application_submitted".equals(m)) {
                if (applicationSheet != null) applicationSheet.dismiss();
                Toast.makeText(this, R.string.agency_application_submitted, Toast.LENGTH_SHORT).show();
            } else if ("agency_deleted".equals(m)) {
                Toast.makeText(this, R.string.agency_deleted, Toast.LENGTH_LONG).show();
            } else if (m != null) {
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getSubmitting().observe(this, submitting -> {
            if (applicationSubmit == null) return;
            boolean busy = Boolean.TRUE.equals(submitting);
            applicationSubmit.setEnabled(!busy);
            applicationSubmit.setText(busy
                    ? R.string.loading
                    : R.string.agency_submit_application);
        });
        vm.getError().observe(this, e -> {
            if (e == null) return;
            com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });
        binding.btnBecomeAgent.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AgencyCreateActivity.class)));
        binding.btnAgencyRoom.setOnClickListener(v ->
                RoomOpenChooser.openWithMine(this, myAgency));
        binding.btnDeleteAgency.setOnClickListener(v -> confirmDeleteAgency());
        vm.load();
    }

    private void openDiamondWithdraw() {
        // Agency owner: request cash from platform admin (not agent mall / packages UI).
        showAgencyPlatformWithdrawDialog();
    }

    /**
     * Owner / host submit platform withdraw from agencyDiamonds pool.
     * Hosts never wait for agency owner approval — lands in admin dashboard.
     */
    private void showAgencyPlatformWithdrawDialog() {
        showAgencyPlatformWithdrawDialog(false);
    }

    private void showHostRequestPayoutDialog() {
        // Host settles agency-room earnings with platform admin (not agency queue).
        showAgencyPlatformWithdrawDialog(true);
    }

    private void showAgencyPlatformWithdrawDialog(boolean forHost) {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> wr = com.Dramizo.Series.util.ApiCall.execute(
                    ContainerProvider.from(this).getWalletApi().getWallet());
            Result<WalletDtos.EconomyConfig> er = com.Dramizo.Series.util.ApiCall.execute(
                    ContainerProvider.from(this).getWalletApi().economyConfig());
            runOnUiThread(() -> {
                long balance = 0L;
                double rate = 0.00005d;
                long minW = 10000L;
                if (wr.success && wr.data != null) {
                    balance = Math.max(0L, wr.data.agencyDiamonds);
                    if (wr.data.diamondUsdRate > 0) rate = wr.data.diamondUsdRate;
                }
                if (er.success && er.data != null) {
                    if (er.data.diamondUsdRate > 0) rate = er.data.diamondUsdRate;
                    if (er.data.minWithdrawDiamonds > 0) {
                        minW = Math.max(1000L, er.data.minWithdrawDiamonds);
                    }
                }
                if (forHost && lastHostDash != null) {
                    if (lastHostDash.diamondUsdRate > 0) rate = lastHostDash.diamondUsdRate;
                    if (lastHostDash.agencyDiamonds >= 0) {
                        balance = Math.max(0L, lastHostDash.agencyDiamonds);
                    }
                } else if (lastEarnings != null) {
                    if (lastEarnings.diamondUsdRate > 0) rate = lastEarnings.diamondUsdRate;
                    if (lastEarnings.available != null
                            && lastEarnings.available.agencyDiamonds >= 0) {
                        balance = Math.max(0L, lastEarnings.available.agencyDiamonds);
                    }
                }
                openAgencyWithdrawForm(balance, rate, minW, forHost);
            });
        });
    }

    private void openAgencyWithdrawForm(
            long balanceDiamonds, double diamondUsdRate, long minDiamonds, boolean forHost) {
        double balUsd = usdOf(balanceDiamonds, diamondUsdRate);
        final android.widget.EditText etAmount = new android.widget.EditText(this);
        etAmount.setHint(R.string.agency_platform_withdraw_amount_hint);
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final android.widget.EditText etAccount = new android.widget.EditText(this);
        etAccount.setHint(R.string.agency_platform_withdraw_account_hint);
        etAccount.setMinLines(2);
        etAccount.setSingleLine(false);
        final String[] methods = new String[]{"bank", "usdt", "paypal", "other"};
        final String[] methodLabels = new String[]{
                getString(R.string.agency_withdraw_method_bank),
                getString(R.string.agency_withdraw_method_usdt),
                getString(R.string.agency_withdraw_method_paypal),
                getString(R.string.agency_withdraw_method_other),
        };
        final android.widget.Spinner spMethod = new android.widget.Spinner(this);
        spMethod.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, methodLabels));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad);
        TextView bal = new TextView(this);
        bal.setText(getString(
                forHost
                        ? R.string.agency_host_platform_withdraw_balance_line
                        : R.string.agency_platform_withdraw_balance_line,
                balanceDiamonds, formatUsdMoney(balUsd)));
        bal.setTextColor(getColor(R.color.text_primary));
        bal.setPadding(0, 0, 0, pad / 2);
        box.addView(bal);
        TextView minHint = new TextView(this);
        minHint.setText(getString(R.string.agency_platform_withdraw_min_line, minDiamonds));
        minHint.setTextColor(getColor(R.color.text_secondary));
        minHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        minHint.setPadding(0, 0, 0, pad / 2);
        box.addView(minHint);
        TextView methodLab = new TextView(this);
        methodLab.setText(R.string.agency_platform_withdraw_method);
        methodLab.setTextColor(getColor(R.color.text_secondary));
        methodLab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(methodLab);
        box.addView(spMethod);
        box.addView(etAmount);
        box.addView(etAccount);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(forHost
                        ? R.string.agency_host_platform_withdraw_title
                        : R.string.agency_platform_withdraw_title)
                .setMessage(forHost
                        ? R.string.agency_host_platform_withdraw_message
                        : R.string.agency_platform_withdraw_message)
                .setView(box)
                .setPositiveButton(R.string.agency_payout_send, (d, w) -> {
                    long diamonds = 0;
                    try {
                        diamonds = Long.parseLong(etAmount.getText() != null
                                ? etAmount.getText().toString().trim() : "0");
                    } catch (NumberFormatException ignored) {}
                    String account = etAccount.getText() != null
                            ? etAccount.getText().toString().trim() : "";
                    int mIdx = Math.max(0, Math.min(methods.length - 1, spMethod.getSelectedItemPosition()));
                    String method = methods[mIdx];
                    if (diamonds < minDiamonds) {
                        Toast.makeText(this,
                                getString(R.string.agency_platform_withdraw_min_line, minDiamonds),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (diamonds > balanceDiamonds) {
                        Toast.makeText(this, R.string.agency_platform_withdraw_insufficient,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (account.isEmpty()) {
                        Toast.makeText(this, R.string.agency_distribute_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitAgencyPlatformWithdraw(diamonds, method, account, forHost);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void submitAgencyPlatformWithdraw(
            long diamonds, String method, String account, boolean forHost) {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            WalletDtos.WithdrawRequest body =
                    new WalletDtos.WithdrawRequest((int) Math.min(Integer.MAX_VALUE, diamonds),
                            method, account);
            if (body.payoutDetails == null) {
                body.payoutDetails = new java.util.HashMap<>();
            }
            body.payoutDetails.put("channel", "platform");
            // Host share vs owner commission — both deduct agencyDiamonds on backend.
            body.payoutDetails.put("source", forHost ? "agency_host" : "agency_commission");
            body.payoutDetails.put("stream", "agency");
            if (myAgencyId != null) {
                body.payoutDetails.put("agencyId", myAgencyId);
            }
            if (myAgency != null && myAgency.agency != null && myAgency.agency.name != null) {
                body.payoutDetails.put("agencyName", myAgency.agency.name);
            }
            if (forHost) {
                body.payoutDetails.put("role", "host");
            }
            Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                    ContainerProvider.from(this).getWalletApi().withdraw(body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, R.string.agency_platform_withdraw_ok, Toast.LENGTH_LONG).show();
                    vm.load();
                } else {
                    com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                }
            });
        });
    }

    private void syncHostEarningsHelp(boolean show) {
        if (binding.rowHostEarningsHelp == null) return;
        binding.rowHostEarningsHelp.setVisibility(show ? View.VISIBLE : View.GONE);
        if (binding.sectionHostDashboard != null && show) {
            binding.sectionHostDashboard.setVisibility(View.VISIBLE);
        }
    }

    private void showHostEarningsOnly() {
        hideAgentStats();
        binding.tvEarnings.setVisibility(View.GONE);
        if (binding.sectionEarnings != null) {
            binding.sectionEarnings.setVisibility(View.GONE);
        }
        syncHostEarningsHelp(true);
    }

    private void showEarningsMessage(String message) {
        clearEarningsCards();
        if (binding.sectionEarnings != null) {
            binding.sectionEarnings.setVisibility(View.VISIBLE);
        }
        binding.tvEarnings.setVisibility(View.VISIBLE);
        binding.tvEarnings.setText(message);
        if (binding.tvHeroPrimaryValue != null) {
            binding.tvHeroPrimaryValue.setText("—");
        }
        if (binding.tvHeroPrimaryLabel != null) {
            binding.tvHeroPrimaryLabel.setText(R.string.agency_dashboard_eyebrow);
        }
    }

    private void hideAgentStats() {
        clearEarningsCards();
        if (binding.sectionEarnings != null) {
            binding.sectionEarnings.setVisibility(View.GONE);
        }
        if (binding.rowOwnerEarningsActions != null) {
            binding.rowOwnerEarningsActions.setVisibility(View.GONE);
        }
    }

    private void hideHostDashboard() {
        lastHostDash = null;
        if (binding.sectionHostDashboard != null) {
            binding.sectionHostDashboard.setVisibility(View.GONE);
        }
        syncHostEarningsHelp(false);
    }

    private void hideHostTarget() {
        if (binding.cardHostTarget != null) {
            binding.cardHostTarget.setVisibility(View.GONE);
        }
    }

    private void setPeriod(int period) {
        selectedPeriod = period;
        applyPeriodChips();
        rebindPeriodUi();
    }

    private void applyPeriodChips() {
        if (binding.chipPeriodWeek == null) return;
        styleChip(binding.chipPeriodWeek, selectedPeriod == PERIOD_WEEK);
        styleChip(binding.chipPeriodMonth, selectedPeriod == PERIOD_MONTH);
        styleChip(binding.chipPeriodAll, selectedPeriod == PERIOD_ALL);
    }

    private void styleChip(TextView chip, boolean on) {
        chip.setBackgroundResource(on ? R.drawable.bg_agency_chip_on : R.drawable.bg_agency_chip_off);
        chip.setTextColor(getColor(on ? android.R.color.white : R.color.text_secondary));
    }

    private void clearEarningsCards() {
        binding.tvEarnings.setVisibility(View.GONE);
        if (binding.rowEarnCards != null) binding.rowEarnCards.setVisibility(View.GONE);
        if (binding.rowEarnEstimates != null) binding.rowEarnEstimates.setVisibility(View.GONE);
        if (binding.tvEarnPercents != null) binding.tvEarnPercents.setVisibility(View.GONE);
    }

    private void bindEarningsCards(MiscDtos.AgencyEarningsDto e) {
        lastEarnings = e;
        hideHostDashboard();
        if (binding.sectionEarnings != null) {
            binding.sectionEarnings.setVisibility(View.VISIBLE);
        }
        rebindPeriodUi();
    }

    private void bindHostDashboard(MiscDtos.AgencyHostDashboardDto d) {
        lastHostDash = d;
        if (d == null || binding.sectionHostDashboard == null) {
            hideHostDashboard();
            return;
        }
        binding.sectionHostDashboard.setVisibility(View.VISIBLE);
        syncHostEarningsHelp(true);
        binding.tvHostWeek.setText(diamondUsdLine(
                d.diamondsEarnedWeek,
                d.usdEarnedWeek > 0
                        ? d.usdEarnedWeek : usdOf(d.diamondsEarnedWeek, d.diamondUsdRate)));
        binding.tvHostMonth.setText(diamondUsdLine(
                d.diamondsEarnedMonth,
                d.usdEarnedMonth > 0
                        ? d.usdEarnedMonth : usdOf(d.diamondsEarnedMonth, d.diamondUsdRate)));
        binding.tvHostAll.setText(diamondUsdLine(
                d.diamondsEarnedAllTime,
                d.usdEarnedAllTime > 0
                        ? d.usdEarnedAllTime : usdOf(d.diamondsEarnedAllTime, d.diamondUsdRate)));
        if (binding.tvHostWalletHint != null) {
            double walletUsd = d.walletUsd > 0 ? d.walletUsd : usdOf(d.walletDiamonds, d.diamondUsdRate);
            binding.tvHostWalletHint.setText(diamondUsdLine(d.walletDiamonds, walletUsd));
        }
        rebindPeriodUi();
    }

    private static double usdOf(long diamonds, double rate) {
        double r = rate > 0 ? rate : 0.00005d;
        return Math.round(diamonds * r * 10000d) / 10000d;
    }

    private String formatUsd(double usd) {
        return String.format(Locale.US, "$%.2f", Math.max(0d, usd));
    }

    /** "10,000 [diamond icon] · 71¢" or "… · $1.20" */
    private CharSequence diamondUsdLine(long diamonds, double usd) {
        String left = String.format(Locale.US, "%,d ", Math.max(0L, diamonds));
        String money;
        double u = Math.max(0d, usd);
        if (u > 0 && u < 1d) {
            money = String.format(Locale.US, " · %d¢", Math.max(1, (int) Math.round(u * 100)));
        } else {
            money = " · " + formatUsd(u);
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(left + "\uFFFC" + money);
        Drawable icon = ContextCompat.getDrawable(this, R.drawable.icon_diamond);
        if (icon != null) {
            int size = dp(14);
            icon = icon.mutate();
            icon.setBounds(0, 0, size, size);
            int start = left.length();
            sb.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE),
                    start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            sb.replace(left.length(), left.length() + 1, "◆");
        }
        return sb;
    }

    private void rebindPeriodUi() {
        applyPeriodChips();
        boolean isHost = myAgency != null
                && myAgency.role != null
                && ("host".equalsIgnoreCase(myAgency.role)
                || "member".equalsIgnoreCase(myAgency.role))
                && !isPendingMembership(myAgency);

        if (isHost && lastHostDash != null) {
            long diamonds;
            double usd;
            int labelRes;
            switch (selectedPeriod) {
                case PERIOD_WEEK:
                    diamonds = lastHostDash.diamondsEarnedWeek;
                    usd = lastHostDash.usdEarnedWeek > 0
                            ? lastHostDash.usdEarnedWeek
                            : usdOf(diamonds, lastHostDash.diamondUsdRate);
                    labelRes = R.string.agency_period_week;
                    break;
                case PERIOD_ALL:
                    diamonds = lastHostDash.diamondsEarnedAllTime;
                    usd = lastHostDash.usdEarnedAllTime > 0
                            ? lastHostDash.usdEarnedAllTime
                            : usdOf(diamonds, lastHostDash.diamondUsdRate);
                    labelRes = R.string.agency_period_all;
                    break;
                case PERIOD_MONTH:
                default:
                    diamonds = lastHostDash.diamondsEarnedMonth;
                    usd = lastHostDash.usdEarnedMonth > 0
                            ? lastHostDash.usdEarnedMonth
                            : usdOf(diamonds, lastHostDash.diamondUsdRate);
                    labelRes = R.string.agency_period_month;
                    break;
            }
            if (binding.tvHeroPrimaryLabel != null) {
                binding.tvHeroPrimaryLabel.setText(
                        getString(R.string.agency_host_kpi_earned) + " · " + getString(labelRes));
            }
            if (binding.tvHeroPrimaryValue != null) {
                binding.tvHeroPrimaryValue.setText(formatUsd(usd));
            }
            if (binding.tvHeroPrimarySub != null) {
                binding.tvHeroPrimarySub.setVisibility(View.VISIBLE);
                binding.tvHeroPrimarySub.setText(diamondUsdLine(diamonds, usd));
            }
            return;
        }

        if (lastEarnings == null) return;
        MiscDtos.AgencyPeriodSlice slice = periodSliceOf(lastEarnings, selectedPeriod);
        long commissionDiamonds;
        double commissionUsd;
        if (slice != null) {
            commissionDiamonds = slice.ownerCommissionEarned;
            commissionUsd = slice.ownerCommissionUsd > 0
                    ? slice.ownerCommissionUsd
                    : usdOf(commissionDiamonds, lastEarnings.diamondUsdRate);
        } else if (lastEarnings.totals != null) {
            commissionDiamonds = lastEarnings.totals.ownerCommissionEarned;
            commissionUsd = lastEarnings.totals.ownerCommissionUsd > 0
                    ? lastEarnings.totals.ownerCommissionUsd
                    : usdOf(commissionDiamonds, lastEarnings.diamondUsdRate);
        } else {
            return;
        }

        int labelRes;
        switch (selectedPeriod) {
            case PERIOD_WEEK:
                labelRes = R.string.agency_period_week;
                break;
            case PERIOD_ALL:
                labelRes = R.string.agency_period_all;
                break;
            case PERIOD_MONTH:
            default:
                labelRes = R.string.agency_period_month;
                break;
        }

        binding.tvEarnings.setVisibility(View.GONE);
        // Agency profit (USD) only — hide gift volume / host share diamond tiles
        if (binding.rowEarnCards != null) binding.rowEarnCards.setVisibility(View.GONE);
        if (binding.rowEarnEstimates != null) binding.rowEarnEstimates.setVisibility(View.GONE);
        if (binding.tvEarnPercents != null) {
            binding.tvEarnPercents.setVisibility(View.VISIBLE);
            binding.tvEarnPercents.setText(getString(R.string.agency_earn_percents_format,
                    lastEarnings.commissionPercent, lastEarnings.hostSharePercent));
        }

        if (binding.tvHeroPrimaryLabel != null) {
            binding.tvHeroPrimaryLabel.setText(
                    getString(R.string.agency_kpi_commission) + " · " + getString(labelRes));
        }
        if (binding.tvHeroPrimaryValue != null) {
            binding.tvHeroPrimaryValue.setText(formatUsd(commissionUsd));
        }
        if (binding.tvHeroPrimarySub != null) {
            binding.tvHeroPrimarySub.setVisibility(View.VISIBLE);
            binding.tvHeroPrimarySub.setText(diamondUsdLine(commissionDiamonds, commissionUsd));
        }
        if (binding.tvEarnCommission != null) {
            binding.tvEarnCommission.setText(formatUsd(commissionUsd));
        }
    }

    @androidx.annotation.Nullable
    private static MiscDtos.AgencyPeriodSlice periodSliceOf(
            MiscDtos.AgencyEarningsDto e, int period) {
        if (e == null || e.periods == null) return null;
        switch (period) {
            case PERIOD_WEEK:
                return e.periods.week;
            case PERIOD_ALL:
                return e.periods.allTime;
            case PERIOD_MONTH:
            default:
                return e.periods.month;
        }
    }

    private void updateHeroEyebrow(String role, boolean pendingJoin) {
        if (binding.tvDashEyebrow == null) return;
        if (pendingJoin) {
            binding.tvDashEyebrow.setText(R.string.agency_join_pending_meta);
            return;
        }
        if ("host".equals(role) || "member".equals(role)) {
            binding.tvDashEyebrow.setText(R.string.agency_host_period_breakdown);
        } else if ("owner".equals(role) || "manager".equals(role)) {
            binding.tvDashEyebrow.setText(R.string.agency_earnings_section);
        } else {
            binding.tvDashEyebrow.setText(R.string.agency_dashboard_eyebrow);
        }
    }

    private void loadHostMonthlyTarget() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r =
                    com.Dramizo.Series.util.ApiCall.execute(c.getUserApi().hostTargetMe());
            runOnUiThread(() -> bindHostMonthlyTarget(r));
        });
    }

    private void bindHostMonthlyTarget(Result<java.util.Map<String, Object>> hostTarget) {
        if (binding == null || binding.cardHostTarget == null) return;
        if (hostTarget == null || !hostTarget.success || hostTarget.data == null
                || !Boolean.TRUE.equals(hostTarget.data.get("enabled"))) {
            hideHostTarget();
            return;
        }
        long progress = toLong(hostTarget.data.get("progress"));
        long next = toLong(hostTarget.data.get("nextThreshold"));
        if (next <= 0) next = Math.max(progress, 1);
        String month = String.valueOf(hostTarget.data.get("yearMonth"));
        if (month == null || "null".equals(month)) month = "";
        binding.cardHostTarget.setVisibility(View.VISIBLE);
        if (binding.tvHostTargetMeta != null) {
            binding.tvHostTargetMeta.setText(getString(
                    R.string.agency_host_target_format, month, progress, next));
        }
        if (binding.progressHostTarget != null) {
            int pct = (int) Math.min(100, Math.round((progress * 100.0) / Math.max(1, next)));
            binding.progressHostTarget.setProgress(pct);
        }
    }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o == null) return 0L;
        try {
            return (long) Double.parseDouble(String.valueOf(o));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void confirmDeleteAgency() {
        if (myAgencyId == null || !canDeleteAgency) return;
        String name = myAgency != null && myAgency.agency != null && myAgency.agency.name != null
                ? myAgency.agency.name : "";
        showConfirmSheet(
                getString(R.string.agency_delete_confirm_title),
                getString(R.string.agency_delete_confirm_message)
                        + (name.isEmpty() ? "" : "\n\n«" + name + "»"),
                getString(R.string.agency_delete),
                () -> vm.deleteAgency(myAgencyId));
    }

    private void syncAgencyRoomButton() {
        // Go-live / team / payouts live in the toolbar menu — not stacked on the hub.
        hideInlineHubActions();
        if (binding.btnDeleteAgency != null) {
            binding.btnDeleteAgency.setVisibility(View.GONE);
        }
    }

    /** Remove body action buttons; features open from toolbar ⋮ menu. Hosts keep payout CTAs. */
    private void hideInlineHubActions() {
        if (binding.rowOwnerEarningsActions != null) {
            binding.rowOwnerEarningsActions.setVisibility(View.GONE);
        }
        if (binding.rowActivationCode != null) {
            binding.rowActivationCode.setVisibility(View.GONE);
        }
        if (binding.btnAgencyRoom != null) {
            binding.btnAgencyRoom.setVisibility(View.GONE);
        }
        if (binding.btnManageAgency != null) {
            binding.btnManageAgency.setVisibility(View.GONE);
        }
        if (binding.sectionAdminGrid != null) {
            binding.sectionAdminGrid.setVisibility(View.GONE);
        }
        boolean isHost = myAgency != null && myAgency.role != null
                && ("host".equalsIgnoreCase(myAgency.role)
                || "member".equalsIgnoreCase(myAgency.role))
                && !isPendingMembership(myAgency);
        if (binding.rowHostEarningsHelp != null) {
            View request = binding.getRoot().findViewById(R.id.btnHostRequestPayout);
            if (isHost) {
                binding.rowHostEarningsHelp.setVisibility(View.VISIBLE);
                if (request != null) request.setVisibility(View.VISIBLE);
                if (binding.btnHostViewEarnings != null) {
                    binding.btnHostViewEarnings.setVisibility(View.VISIBLE);
                }
                if (binding.tvHostEarningsHelp != null) {
                    binding.tvHostEarningsHelp.setVisibility(View.VISIBLE);
                }
            } else {
                if (request != null) request.setVisibility(View.GONE);
                if (binding.btnHostViewEarnings != null) {
                    binding.btnHostViewEarnings.setVisibility(View.GONE);
                }
                if (binding.tvHostEarningsHelp != null) {
                    binding.tvHostEarningsHelp.setVisibility(View.GONE);
                }
                binding.rowHostEarningsHelp.setVisibility(View.GONE);
            }
        }
        if (binding.btnAgencyMenu != null) {
            boolean show = myAgency != null && myAgency.agency != null
                    && !isPendingMembership(myAgency);
            binding.btnAgencyMenu.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showAgencyToolbarMenu() {
        if (myAgency == null || myAgency.agency == null || isPendingMembership(myAgency)) {
            Toast.makeText(this, R.string.agency_join_pending_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        String role = myAgency.role != null ? myAgency.role.toLowerCase(Locale.US) : "";
        boolean isOwner = "owner".equals(role);
        boolean isManager = "manager".equals(role);
        boolean isHost = "host".equals(role) || "member".equals(role);
        boolean eligibleLive = myAgency.isEligibleHost();

        BottomSheetDialog sheet = newSheet();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, dp(8), pad, dp(20));
        box.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText(R.string.menu);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(10));
        box.addView(title);

        if (isOwner || isManager) {
            // Hosts withdraw on-platform; no agency settlement queue.
        }
        if (isOwner) {
            addMenuRow(box, R.string.agency_withdraw_earnings, () -> {
                sheet.dismiss();
                openDiamondWithdraw();
            });
        }
        if (canManage) {
            addMenuRow(box, R.string.agency_activation_code, () -> {
                sheet.dismiss();
                showActivationCodeSheet();
            });
            addMenuRow(box, R.string.agency_team_tools, () -> {
                sheet.dismiss();
                if (myAgencyId != null) openManage(myAgencyId);
            });
        }
        if (isHost) {
            addMenuRow(box, R.string.agency_host_request_payout, () -> {
                sheet.dismiss();
                showHostRequestPayoutDialog();
            });
            addMenuRow(box, R.string.agency_host_view_earnings, () -> {
                sheet.dismiss();
                Intent i = new Intent(this,
                        com.Dramizo.Series.presentation.wallet.BagActivity.class);
                i.putExtra(com.Dramizo.Series.presentation.wallet.BagActivity.EXTRA_TAB, 1);
                startActivity(i);
            });
        }
        if (eligibleLive) {
            addMenuRow(box, R.string.agency_go_live, () -> {
                sheet.dismiss();
                RoomOpenChooser.openWithMine(this, myAgency);
            });
        }

        if (box.getChildCount() <= 1) {
            Toast.makeText(this, R.string.agency_earn_owner_only, Toast.LENGTH_SHORT).show();
            return;
        }

        AuraDialogHelper.applyContent(box);
        sheet.setContentView(box);
        sheet.show();
    }

    private void addMenuRow(LinearLayout box, int titleRes, Runnable onClick) {
        TextView row = new TextView(this);
        row.setText(titleRes);
        row.setTextColor(getColor(R.color.text_primary));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.setTypeface(row.getTypeface(), android.graphics.Typeface.BOLD);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackgroundResource(R.drawable.bg_agency_menu_row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> onClick.run());
        box.addView(row);
    }

    private void showActivationCodeSheet() {
        String code = myAgency != null && myAgency.agency != null
                && myAgency.agency.activationCode != null
                ? myAgency.agency.activationCode : "";
        if (code.isEmpty() && binding.tvActivationCode != null
                && binding.tvActivationCode.getText() != null) {
            code = binding.tvActivationCode.getText().toString();
        }
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, R.string.agency_earn_owner_only, Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalCode = code;
        showConfirmSheet(
                getString(R.string.agency_activation_code),
                finalCode,
                getString(R.string.copy),
                () -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "agency_code", finalCode));
                        Toast.makeText(this, R.string.agency_copied, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void bindApplicationStatus(MiscDtos.AgencyMineDto mine) {
        // Once the agency exists, the dashboard card is enough — hide stale "approved" banner.
        if (mine != null && mine.agency != null) {
            binding.cardApplicationStatus.setVisibility(View.GONE);
            binding.btnBecomeAgent.setVisibility(View.GONE);
            return;
        }
        MiscDtos.AgencyApplicationDto application = mine != null ? mine.application : null;
        if (application == null) {
            binding.cardApplicationStatus.setVisibility(View.GONE);
            binding.btnBecomeAgent.setVisibility(View.VISIBLE);
            binding.btnBecomeAgent.setText(R.string.open_agency);
            return;
        }
        String status = application.status != null
                ? application.status.toLowerCase(Locale.US) : "pending";
        binding.cardApplicationStatus.setVisibility(View.VISIBLE);
        binding.tvReviewNote.setVisibility(
                application.reviewNote != null && !application.reviewNote.trim().isEmpty()
                        ? View.VISIBLE : View.GONE);
        if (binding.tvReviewNote.getVisibility() == View.VISIBLE) {
            binding.tvReviewNote.setText(getString(
                    R.string.agency_review_note_format, application.reviewNote));
        }
        switch (status) {
            case "changes_requested":
            case "changes-requested":
                binding.tvApplicationBadge.setText(R.string.agency_status_changes);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_pending);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_changes);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_changes_body);
                binding.btnBecomeAgent.setVisibility(View.VISIBLE);
                binding.btnBecomeAgent.setText(R.string.agency_resubmit);
                break;
            case "rejected":
                binding.tvApplicationBadge.setText(R.string.agency_status_rejected);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_bad);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_rejected);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_rejected_body);
                binding.btnBecomeAgent.setVisibility(View.VISIBLE);
                binding.btnBecomeAgent.setText(R.string.agency_apply_again);
                break;
            case "approved":
                binding.tvApplicationBadge.setText(R.string.agency_status_approved);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_ok);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_approved);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_approved_body);
                binding.btnBecomeAgent.setVisibility(View.GONE);
                break;
            default:
                binding.tvApplicationBadge.setText(R.string.agency_status_pending);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_pending);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_pending);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_pending_body);
                binding.btnBecomeAgent.setVisibility(View.GONE);
                break;
        }
    }

    private String formatAgencyMeta(MiscDtos.AgencyMineDto m) {
        if (m == null || m.agency == null) return "";
        if (isPendingMembership(m)) {
            return getString(R.string.agency_join_pending_meta);
        }
        String status = m.agencyStatus != null && !m.agencyStatus.isEmpty()
                ? m.agencyStatus
                : (m.agency.status != null ? m.agency.status : "");
        String statusLabel = "";
        if ("suspended".equalsIgnoreCase(status)) {
            statusLabel = getString(R.string.agency_status_suspended);
        } else if ("pending".equalsIgnoreCase(status)) {
            statusLabel = getString(R.string.agency_status_pending_review);
        }
        String idPart = (m.agency.publicId != null && !m.agency.publicId.isEmpty())
                ? ("ID " + m.agency.publicId + " · ")
                : "";
        String verified = m.agency.isVerified ? (getString(R.string.agency_verified_label) + " · ") : "";
        return verified + idPart + getString(R.string.agency_meta_format,
                roleAr(m.role),
                m.agency.memberCount,
                m.agency.commissionPercent,
                statusLabel);
    }

    private static boolean isPendingMembership(MiscDtos.AgencyMineDto m) {
        if (m == null || m.membershipStatus == null) return false;
        return "pending".equalsIgnoreCase(m.membershipStatus.trim());
    }

    private static String roleAr(String role) {
        if (role == null) return "عضو";
        switch (role.toLowerCase(Locale.US)) {
            case "owner": return "مالك";
            case "manager": return "مدير";
            case "host": return "مضيف";
            default: return "عضو";
        }
    }

    private BottomSheetDialog newSheet() {
        return AuraDialogHelper.bottomSheet(this);
    }

    private void showConfirmSheet(String title, String message, String yesLabel, Runnable onYes) {
        BottomSheetDialog sheet = newSheet();
        DialogAgencyConfirmBinding form = DialogAgencyConfirmBinding.inflate(getLayoutInflater());
        form.tvConfirmTitle.setText(title);
        form.tvConfirmMessage.setText(message);
        form.btnConfirmYes.setText(yesLabel);
        form.btnConfirmYes.setOnClickListener(v -> {
            sheet.dismiss();
            onYes.run();
        });
        form.btnConfirmNo.setOnClickListener(v -> sheet.dismiss());
        AuraDialogHelper.applyContent(form.getRoot());
        sheet.setContentView(form.getRoot());
        sheet.show();
    }

    private void showCreateDialog() {
        if (myAgency != null && myAgency.application != null) {
            String current = myAgency.application.status;
            if (current == null || "pending".equalsIgnoreCase(current)
                    || "approved".equalsIgnoreCase(current)) {
                Toast.makeText(this, R.string.agency_duplicate_application, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        vm.loadPricing();
        BottomSheetDialog sheet = newSheet();
        applicationSheet = sheet;
        DialogCreateAgencyBinding form = DialogCreateAgencyBinding.inflate(LayoutInflater.from(this));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundResource(R.drawable.bg_aura_dialog_surface);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad, pad, pad);
        int panelHeight = (int) (getResources().getDisplayMetrics().heightPixels
                * (AuraDialogHelper.MAX_HEIGHT_RATIO - 0.02f));
        wrap.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, panelHeight));
        TextView title = new TextView(this);
        title.setText(R.string.open_agency);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setGravity(android.view.Gravity.END);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView hint = new TextView(this);
        hint.setText(R.string.agency_application_paid_hint);
        hint.setTextColor(getColor(R.color.aurora_mint));
        hint.setTextSize(12);
        hint.setGravity(android.view.Gravity.END);
        hint.setPadding(0, pad / 3, 0, pad / 2);
        TextView priceView = new TextView(this);
        priceView.setTextColor(0xFFFFD54F);
        priceView.setTextSize(14);
        priceView.setGravity(android.view.Gravity.END);
        priceView.setTypeface(priceView.getTypeface(), android.graphics.Typeface.BOLD);
        priceView.setPadding(0, 0, 0, pad / 2);
        MiscDtos.AgencyPricingDto cached = vm.getPricing().getValue();
        int priceCoins = cached != null ? Math.max(0, cached.createPriceCoins) : 0;
        if (priceCoins > 0) {
            priceView.setText(getString(R.string.agency_create_price_format, priceCoins));
        } else {
            priceView.setText(R.string.agency_application_loading_price);
        }
        androidx.lifecycle.Observer<MiscDtos.AgencyPricingDto> priceObserver = p -> {
            if (p == null) return;
            int coins = Math.max(0, p.createPriceCoins);
            if (coins > 0) {
                priceView.setText(getString(R.string.agency_create_price_format, coins));
            } else {
                priceView.setText(R.string.agency_application_free_hint);
            }
        };
        vm.getPricing().observe(this, priceObserver);
        wrap.addView(title);
        wrap.addView(hint);
        wrap.addView(priceView);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        form.getRoot().setLayoutParams(formLp);
        wrap.addView(form.getRoot());
        prefillApplication(form);
        applyAccountCountry(form);
        com.google.android.material.button.MaterialButton submit = form.btnSubmitAgency;
        applicationSubmit = submit;
        submit.setOnClickListener(v -> {
            MiscDtos.AgencyApplicationRequest request = applicationFrom(form);
            String validationError = validateApplication(request);
            if (validationError != null) {
                Toast.makeText(this, validationError, Toast.LENGTH_LONG).show();
                return;
            }
            if (!request.termsAccepted) {
                Toast.makeText(this, R.string.agency_accept_terms, Toast.LENGTH_SHORT).show();
                return;
            }
            vm.submitApplication(request);
        });
        form.btnCancelAgency.setOnClickListener(v -> sheet.dismiss());
        AuraDialogHelper.applyContent(wrap);
        sheet.setContentView(wrap);
        sheet.setOnDismissListener(dialog -> {
            vm.getPricing().removeObserver(priceObserver);
            if (applicationSheet == sheet) applicationSheet = null;
            if (applicationSubmit == submit) applicationSubmit = null;
        });
        sheet.show();
        View bottomSheet = sheet.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private String validateApplication(MiscDtos.AgencyApplicationRequest request) {
        if (request.proposedName.length() < 3) {
            return "اسم الوكالة يجب أن يكون 3 أحرف على الأقل";
        }
        if (request.businessPlan.length() < 50) {
            return "خطة العمل يجب أن تكون واضحة ولا تقل عن 50 حرفاً";
        }
        if (request.country.length() < 2) {
            return "أدخل اسم الدولة بشكل صحيح";
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(request.contactEmail).matches()) {
            return "أدخل بريداً إلكترونياً صحيحاً";
        }
        if (request.contactPhone.length() < 6) {
            return "رقم التواصل يجب أن يتكون من 6 خانات على الأقل";
        }
        if (request.experience.length() < 10) {
            return "اكتب خبرتك في 10 أحرف على الأقل";
        }
        if (request.expectedHostCount < 1) {
            return "أدخل عدد المضيفين المتوقع";
        }
        return null;
    }

    private void prefillApplication(DialogCreateAgencyBinding form) {
        MiscDtos.AgencyApplicationDto a =
                myAgency != null ? myAgency.application : null;
        if (a == null) return;
        form.etAgencyName.setText(a.proposedName);
        form.etAgencyDesc.setText(a.businessPlan != null ? a.businessPlan : a.description);
        form.etCountry.setText(a.country);
        form.etContactEmail.setText(a.contactEmail);
        form.etContactPhone.setText(a.contactPhone);
        form.etExperience.setText(a.experience);
        if (a.expectedHostCount > 0) {
            form.etExpectedHostCount.setText(String.valueOf(a.expectedHostCount));
        }
    }

    private void applyAccountCountry(DialogCreateAgencyBinding form) {
        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user =
                ContainerProvider.from(this).getSessionManager().getUser();
        String country = user != null && user.country != null ? user.country.trim() : "";
        if (country.isEmpty()) {
            country = java.util.Locale.getDefault().getCountry();
        }
        if (country == null || country.trim().isEmpty()) country = "JO";
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        String canonicalCountry = entry != null ? entry.code : country;
        form.etCountry.setTag(canonicalCountry);
        form.etCountry.setText(CountryCatalog.labelWithFlag(country));
        form.etCountry.setFocusable(false);
        form.etCountry.setClickable(false);
        form.etCountry.setLongClickable(false);
        form.etCountry.setAlpha(0.92f);
    }

    private MiscDtos.AgencyApplicationRequest applicationFrom(DialogCreateAgencyBinding form) {
        MiscDtos.AgencyApplicationRequest r = new MiscDtos.AgencyApplicationRequest();
        r.proposedName = text(form.etAgencyName);
        r.description = text(form.etAgencyDesc);
        r.businessPlan = r.description;
        Object accountCountry = form.etCountry.getTag();
        r.country = accountCountry instanceof String
                ? ((String) accountCountry).trim()
                : text(form.etCountry);
        r.contactEmail = text(form.etContactEmail);
        r.contactPhone = text(form.etContactPhone);
        r.socialLink = null;
        r.experience = text(form.etExperience);
        try {
            r.expectedHostCount = Integer.parseInt(text(form.etExpectedHostCount));
        } catch (NumberFormatException ignored) {
            r.expectedHostCount = 0;
        }
        r.documentUrls = java.util.Collections.emptyList();
        r.termsAccepted = form.checkTerms.isChecked();
        return r;
    }

    private static String text(android.widget.EditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private void showHostPayoutQueue() {
        // Hosts cash out via platform admin; agency no longer settles host diamonds.
        Toast.makeText(this, R.string.agency_host_payouts_via_platform, Toast.LENGTH_LONG).show();
    }

    private String formatUsdMoney(double usd) {
        double u = Math.max(0d, usd);
        if (u > 0 && u < 1d) {
            return String.format(Locale.US, "%d¢", Math.max(1, (int) Math.round(u * 100)));
        }
        return formatUsd(u);
    }

    private String plainDiamondsUsd(long diamonds, double usd) {
        return String.format(Locale.US, "%,d · %s", Math.max(0L, diamonds), formatUsdMoney(usd));
    }

    private void showDistributeDialog() {
        showHostPayoutQueue();
    }

    private void rebindAgencyDirectory() {
        if (adapter == null || binding == null) return;
        // Members already in an agency don't need the public lookup results.
        boolean hideLookup = myAgencyId != null && !isPendingMembership(myAgency);
        java.util.List<MiscDtos.AgencyDto> rows = hideLookup
                ? Collections.emptyList()
                : vm.getAgencies().getValue();
        if (rows == null) rows = Collections.emptyList();
        adapter.submit(rows, myAgencyId, canLeave, canManage);
        boolean hasResults = !rows.isEmpty();
        boolean tried = Boolean.TRUE.equals(vm.getSearchAttempted().getValue());
        boolean showEmpty = !hideLookup && !hasResults && tried;
        if (binding.tvActiveAgenciesTitle != null) {
            binding.tvActiveAgenciesTitle.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        }
        if (binding.tvEmpty != null) {
            binding.tvEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }
        if (binding.recycler != null) {
            binding.recycler.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        }
    }

    private void enterLiveAgency(String agencyId, String openRoomId) {
        if (openRoomId != null && !openRoomId.trim().isEmpty()) {
            Intent i = new Intent(this,
                    com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.class);
            i.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_ROOM_ID,
                    openRoomId.trim());
            i.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_IS_HOST, false);
            startActivity(i);
            return;
        }
        if (agencyId == null || agencyId.trim().isEmpty()) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.RoomDtos.JoinRoomResult> entered =
                    ApiCall.execute(c.getAgencyApi().enterRoom(agencyId.trim()));
            runOnUiThread(() -> {
                if (!entered.success || entered.data == null || entered.data.room == null
                        || entered.data.room.id == null) {
                    Toast.makeText(this,
                            entered.error != null ? entered.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(this,
                        com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.class);
                i.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_ROOM_ID,
                        entered.data.room.id);
                i.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_IS_HOST, false);
                startActivity(i);
            });
        });
    }

    private void openManage(String agencyId) {
        if (agencyId == null || agencyId.isEmpty()) return;
        android.content.Intent i = new android.content.Intent(this, AgencyManageActivity.class);
        i.putExtra(AgencyManageActivity.EXTRA_AGENCY_ID, agencyId);
        startActivity(i);
    }
}
