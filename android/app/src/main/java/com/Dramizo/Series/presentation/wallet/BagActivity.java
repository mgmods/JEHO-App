package com.Dramizo.Series.presentation.wallet;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.PromoDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityBagBinding;
import com.Dramizo.Series.databinding.ItemBagPackageBinding;
import com.Dramizo.Series.databinding.ItemWithdrawRequestBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.QrBitmap;
import com.Dramizo.Series.util.RewardBurstOverlay;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class BagActivity extends ThemedActivity {
    public static final String EXTRA_TAB = "extra_tab";
    /** convert | transfer | withdraw | target — opens a focused diamonds screen. */
    public static final String EXTRA_DIAMOND_ACTION = "diamond_action";
    private static final String[] PAY_VALUES = {"paypal", "bank", "usdt"};

    private ActivityBagBinding binding;
    private AppContainer c;
    private PkgAdapter adapter;
    private WithdrawAdapter withdrawAdapter;
    private WithdrawPackageAdapter withdrawPackageAdapter;
    private String diamondActionMode;
    /** self = paypal/bank/usdt ; agent = via recharge agent */
    private boolean withdrawViaAgent = false;
    private String selectedWithdrawMethod = "paypal";
    private String selectedAgentId;
    private String selectedAgentName;
    private long selectedWithdrawDiamonds = 0L;
    private final List<WalletDtos.AgentDirectoryEntry> withdrawAgents = new ArrayList<>();
    private double diamondUsdRate = 0.00005d;
    private double diamondCoinRate = 0.6d;
    private long minWithdrawDiamonds = 10000L;
    private WalletDtos.WalletDto currentWallet;
    private final Handler binancePollHandler = new Handler(Looper.getMainLooper());
    private Runnable binancePollRunnable;
    private Runnable binanceCountdownRunnable;
    private final AtomicBoolean binancePolling = new AtomicBoolean(false);
    private BottomSheetDialog binanceDepositSheet;
    private TextView tvDepositCountdown;
    private TextView tvDepositStatus;
    private ProgressBar progressDepositRing;
    private TextView tvHourglass;
    private ObjectAnimator hourglassAnimator;
    private long depositExpiresAtMs;
    private long depositCreatedAtMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBagBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(this, "app");
        c = ContainerProvider.from(this);
        binding.btnBack.setOnClickListener(v -> navigateUp());
        if (binding.btnWalletMenu != null) {
            binding.btnWalletMenu.setOnClickListener(v ->
                    startActivity(new Intent(this, WalletBillsActivity.class)));
        }
        if (binding.ivSignFloat != null) {
            binding.ivSignFloat.setVisibility(View.VISIBLE);
            binding.ivSignFloat.setOnClickListener(v ->
                    SignInDialog.show(getSupportFragmentManager()));
        }

        binding.tabs.addTab(binding.tabs.newTab().setText("كوينزات"));
        binding.tabs.addTab(binding.tabs.newTab().setText("ماسة"));
        binding.tabs.addTab(binding.tabs.newTab().setText("وكيل"));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPanel(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        adapter = new PkgAdapter(this::buyPackage);
        binding.recyclerPackages.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerPackages.setNestedScrollingEnabled(false);
        binding.recyclerPackages.setHasFixedSize(false);
        binding.recyclerPackages.setAdapter(adapter);
        setupAgentsStrip();

        if (binding.btnOpenAgentPortal != null) {
            binding.btnOpenAgentPortal.setOnClickListener(v ->
                    startActivity(new Intent(this, RechargeAgentActivity.class)));
        }
        if (binding.btnOpenAgentDirectory != null) {
            binding.btnOpenAgentDirectory.setOnClickListener(v ->
                    openAgentDirectoryWithPackageHint());
        }

        withdrawAdapter = new WithdrawAdapter();
        binding.recyclerWithdrawHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWithdrawHistory.setAdapter(withdrawAdapter);
        withdrawPackageAdapter = new WithdrawPackageAdapter(pkg -> {
            selectedWithdrawDiamonds = pkg != null ? pkg.diamonds : 0L;
            if (binding.etWithdrawDiamonds != null && selectedWithdrawDiamonds > 0) {
                binding.etWithdrawDiamonds.setText(String.valueOf(selectedWithdrawDiamonds));
            }
        });
        if (binding.recyclerWithdrawPackages != null) {
            binding.recyclerWithdrawPackages.setLayoutManager(new GridLayoutManager(this, 3));
            binding.recyclerWithdrawPackages.setNestedScrollingEnabled(false);
            binding.recyclerWithdrawPackages.setAdapter(withdrawPackageAdapter);
        }
        if (binding.rowWithdrawChannel != null) {
            binding.rowWithdrawChannel.setOnClickListener(v -> showWithdrawChannelChooser());
        }
        binding.btnOpenEarnings.setOnClickListener(v ->
                startActivity(new Intent(this, EarningsActivity.class)));
        View.OnClickListener openDiamondScreen = v -> {
            String action;
            int id = v.getId();
            if (id == R.id.btnFocusConvert) action = "convert";
            else if (id == R.id.btnFocusTransfer) action = "transfer";
            else if (id == R.id.btnFocusTarget) action = "target";
            else action = "withdraw";
            Intent i = new Intent(this, BagActivity.class);
            i.putExtra(EXTRA_TAB, 1);
            i.putExtra(EXTRA_DIAMOND_ACTION, action);
            startActivity(i);
        };
        if (binding.btnFocusWithdraw != null) {
            binding.btnFocusWithdraw.setOnClickListener(openDiamondScreen);
        }
        if (binding.btnOpenDebrisStore != null) {
            binding.btnOpenDebrisStore.setOnClickListener(v ->
                    startActivity(new Intent(this,
                            com.Dramizo.Series.presentation.games.GameStoreActivity.class)));
        }
        if (binding.btnOpenDebrisTasks != null) {
            binding.btnOpenDebrisTasks.setOnClickListener(v ->
                    startActivity(new Intent(this,
                            com.Dramizo.Series.presentation.profile.TaskCenterActivity.class)));
        }
        if (binding.btnFocusConvert != null) {
            binding.btnFocusConvert.setOnClickListener(openDiamondScreen);
        }
        if (binding.btnFocusTransfer != null) {
            binding.btnFocusTransfer.setOnClickListener(openDiamondScreen);
        }
        if (binding.btnFocusTarget != null) {
            binding.btnFocusTarget.setOnClickListener(openDiamondScreen);
        }
        if (binding.btnRechargeAgent != null) {
            binding.btnRechargeAgent.setOnClickListener(v ->
                    startActivity(new Intent(this, RechargeAgentActivity.class)));
        }

        binding.btnExchangeDiamonds.setOnClickListener(v -> {
            String raw = binding.etDiamonds.getText() != null ? binding.etDiamonds.getText().toString().trim() : "";
            int amount;
            try { amount = Integer.parseInt(raw); } catch (Exception e) { amount = 0; }
            if (amount <= 0) {
                Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                return;
            }
            final int diamonds = amount;
            c.getIoExecutor().execute(() -> {
                Result<WalletDtos.WalletDto> r = ApiCall.execute(c.getWalletApi().exchange(
                        new WalletDtos.ExchangeRequest(diamonds)));
                runOnUiThread(() -> {
                    if (r.success) {
                        RewardBurstOverlay.show(
                                this,
                                RewardBurstOverlay.Kind.DIAMONDS,
                                "تم التحويل!",
                                diamonds + " ماس → عملات");
                        applyWallet(r.data);
                    } else Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                });
            });
        });

        if (binding.btnHostTrade != null) {
            binding.btnHostTrade.setOnClickListener(v -> {
                String toId = binding.etTradeHostId.getText() != null
                        ? binding.etTradeHostId.getText().toString().trim() : "";
                String raw = binding.etTradeDiamonds.getText() != null
                        ? binding.etTradeDiamonds.getText().toString().trim() : "";
                int amount;
                try { amount = Integer.parseInt(raw); } catch (Exception e) { amount = 0; }
                if (toId.isEmpty() || amount <= 0) {
                    Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                    return;
                }
                final String peerId = toId;
                final int diamonds = amount;
                c.getIoExecutor().execute(() -> {
                    Result<java.util.Map<String, Object>> r = ApiCall.execute(
                            c.getWalletApi().hostTrade(new WalletDtos.HostTradeRequest(peerId, diamonds)));
                    runOnUiThread(() -> {
                        if (r.success) {
                            RewardBurstOverlay.show(
                                    this,
                                    RewardBurstOverlay.Kind.DIAMONDS,
                                    "تم التبديل!",
                                    diamonds + " ماس أُضيفت للتاجر");
                            c.getIoExecutor().execute(() -> {
                                Result<WalletDtos.WalletDto> w = ApiCall.execute(c.getWalletApi().getWallet());
                                runOnUiThread(() -> {
                                    if (w.success) applyWallet(w.data);
                                });
                            });
                        } else {
                            Toast.makeText(this,
                                    r.error != null ? r.error : getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
        }

        binding.etWithdrawMethod.setFocusable(false);
        binding.etWithdrawMethod.setClickable(true);
        binding.etWithdrawMethod.setCursorVisible(false);
        binding.etWithdrawMethod.setText(paymentLabels()[0]);
        binding.etWithdrawMethod.setOnClickListener(v -> showPaymentMethodDialog());
        binding.etWithdrawAgent.setFocusable(false);
        binding.etWithdrawAgent.setClickable(true);
        binding.etWithdrawAgent.setCursorVisible(false);
        binding.etWithdrawAgent.setOnClickListener(v -> showAgentPickerDialog());
        binding.btnChannelSelf.setOnClickListener(v -> setWithdrawChannel(false));
        binding.btnChannelAgent.setOnClickListener(v -> setWithdrawChannel(true));
        setWithdrawChannel(false);

        binding.btnWithdrawDiamonds.setOnClickListener(v -> {
            long packageAmount = selectedWithdrawDiamonds;
            String raw = binding.etWithdrawDiamonds.getText() != null
                    ? binding.etWithdrawDiamonds.getText().toString().trim() : "";
            String details = binding.etWithdrawDetails.getText() != null
                    ? binding.etWithdrawDetails.getText().toString().trim() : "";
            int amount;
            if (packageAmount > 0) {
                amount = (int) Math.min(Integer.MAX_VALUE, packageAmount);
            } else {
                try { amount = Integer.parseInt(raw); } catch (Exception e) { amount = 0; }
            }
            if (amount < minWithdrawDiamonds) {
                Toast.makeText(this, getString(R.string.minimum_withdrawal, minWithdrawDiamonds),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            final int diamonds = amount;
            if (withdrawViaAgent) {
                if (selectedAgentId == null || selectedAgentId.isEmpty()) {
                    Toast.makeText(this, "اختر وكيل شحن للسحب", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String agentId = selectedAgentId;
                final String note = details;
                c.getIoExecutor().execute(() -> {
                    Result<Object> r = ApiCall.execute(c.getWalletApi().withdraw(
                            WalletDtos.WithdrawRequest.viaAgent(diamonds, agentId, note)));
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(this, R.string.withdrawal_submitted, Toast.LENGTH_LONG).show();
                            load();
                        } else {
                            Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
                return;
            }
            if (selectedWithdrawMethod == null || selectedWithdrawMethod.isEmpty()) {
                Toast.makeText(this, R.string.choose_payment_method, Toast.LENGTH_SHORT).show();
                return;
            }
            if (details.isEmpty()) {
                Toast.makeText(this, R.string.enter_account_details, Toast.LENGTH_SHORT).show();
                return;
            }
            final String m = selectedWithdrawMethod;
            final String acc = details;
            c.getIoExecutor().execute(() -> {
                Result<Object> r = ApiCall.execute(c.getWalletApi().withdraw(
                        new WalletDtos.WithdrawRequest(diamonds, m, acc)));
                runOnUiThread(() -> {
                    if (r.success) {
                        Toast.makeText(this, R.string.withdrawal_submitted, Toast.LENGTH_LONG).show();
                        load();
                    } else {
                        Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        load();
        applyWalletIcons();
        preloadWithdrawAgents();
        diamondActionMode = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_DIAMOND_ACTION) : null;
        int startTab = getIntent() != null ? getIntent().getIntExtra(EXTRA_TAB, 0) : 0;
        if (diamondActionMode != null && !diamondActionMode.isEmpty()) {
            startTab = 1;
        }
        if (startTab < 0 || startTab > 2) startTab = 0;
        TabLayout.Tab tab = binding.tabs.getTabAt(startTab);
        if (tab != null) binding.tabs.selectTab(tab);
        showPanel(startTab);
        setWithdrawChannel(false);
        applyDiamondActionMode(diamondActionMode);
        handleRechargeReturn(getIntent());
    }

    /** After card (Fourthwall) checkout — toast + refresh balance. */
    private void handleRechargeReturn(@Nullable Intent intent) {
        if (intent == null) return;
        if (!intent.getBooleanExtra(CardCheckoutActivity.EXTRA_RECHARGE_SUCCESS, false)) {
            return;
        }
        int coins = intent.getIntExtra(CardCheckoutActivity.EXTRA_RECHARGE_COINS, 0);
        boolean pending = intent.getBooleanExtra("extra_recharge_pending", false);
        // Consume so rotation / resume doesn't re-toast.
        intent.removeExtra(CardCheckoutActivity.EXTRA_RECHARGE_SUCCESS);
        intent.removeExtra(CardCheckoutActivity.EXTRA_RECHARGE_COINS);
        intent.removeExtra("extra_recharge_pending");

        if (pending) {
            Toast.makeText(this, R.string.card_checkout_success_pending_hint, Toast.LENGTH_LONG).show();
        } else if (coins > 0) {
            RewardBurstOverlay.showCoins(
                    this,
                    getString(R.string.card_checkout_success),
                    String.format(Locale.US, "+%,d", coins));
        } else {
            Toast.makeText(this, R.string.card_checkout_success, Toast.LENGTH_LONG).show();
        }
        // Force wallet refresh now (onResume also reloads).
        if (c != null) {
            c.getIoExecutor().execute(() -> {
                Result<WalletDtos.WalletDto> w = ApiCall.execute(c.getWalletApi().getWallet());
                runOnUiThread(() -> {
                    if (!isFinishing() && w.success) applyWallet(w.data);
                });
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (c == null) return;
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = ApiCall.execute(c.getWalletApi().getWallet());
            Result<PromoDtos.MyProgress> promo = ApiCall.execute(c.getPromotionsApi().me());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (w.success) applyWallet(w.data);
                applyPromoProgress(promo.success ? promo.data : null);
            });
        });
    }

    private void applyPromoProgress(@Nullable PromoDtos.MyProgress progress) {
        if (binding == null || binding.sectionPromoOffers == null) return;
        if (progress == null) {
            binding.sectionPromoOffers.setVisibility(View.GONE);
            return;
        }
        binding.sectionPromoOffers.setVisibility(View.VISIBLE);
        if (binding.tvPromoSpent != null) {
            binding.tvPromoSpent.setText(String.format(Locale.US,
                    "مصروف هذا الشهر: $%.0f", progress.usdSpent));
        }
        if (binding.tvPromoLines != null) {
            StringBuilder sb = new StringBuilder();
            for (PromoDtos.OfferProgress o : progress.safeMonthly()) {
                appendPromoLine(sb, o, "هدية");
            }
            for (PromoDtos.OfferProgress o : progress.safeSupporter()) {
                appendPromoLine(sb, o, "داعم");
            }
            if (sb.length() == 0) {
                sb.append("اشحن $200 للحصول على إطار + ID مميز");
            }
            binding.tvPromoLines.setText(sb.toString().trim());
        }
    }

    private static void appendPromoLine(StringBuilder sb, PromoDtos.OfferProgress o, String kind) {
        if (o == null) return;
        if (sb.length() > 0) sb.append('\n');
        String title = o.titleAr != null && !o.titleAr.isEmpty() ? o.titleAr : kind;
        String status = o.claimed ? "✓ مستلم" : (o.unlocked ? "جاهز" : String.format(Locale.US,
                "$%.0f / $%.0f", o.progressUsd, o.thresholdUsd));
        sb.append("• ").append(title).append(" — ").append(status);
    }

    /** Hub shows balance + menu; each action opens this activity focused on one form. */
    private void applyDiamondActionMode(@Nullable String mode) {
        if (binding == null) return;
        boolean focused = mode != null && !mode.isEmpty();
        if (!focused) {
            // Hub: keep forms hidden — only menu rows.
            if (binding.sectionWithdrawForm != null) binding.sectionWithdrawForm.setVisibility(View.GONE);
            if (binding.sectionConvert != null) binding.sectionConvert.setVisibility(View.GONE);
            if (binding.sectionHostTrade != null) binding.sectionHostTrade.setVisibility(View.GONE);
            if (binding.sectionHostMonthlyTarget != null) {
                binding.sectionHostMonthlyTarget.setVisibility(View.GONE);
            }
            return;
        }
        if (binding.tabs != null) binding.tabs.setVisibility(View.GONE);
        if (binding.panelCoins != null) binding.panelCoins.setVisibility(View.GONE);
        if (binding.panelDebris != null) binding.panelDebris.setVisibility(View.GONE);
        if (binding.panelDiamonds != null) binding.panelDiamonds.setVisibility(View.VISIBLE);
        if (binding.btnFocusConvert != null) binding.btnFocusConvert.setVisibility(View.GONE);
        if (binding.btnFocusTransfer != null) binding.btnFocusTransfer.setVisibility(View.GONE);
        if (binding.btnFocusWithdraw != null) binding.btnFocusWithdraw.setVisibility(View.GONE);
        if (binding.btnFocusTarget != null) binding.btnFocusTarget.setVisibility(View.GONE);
        if (binding.btnOpenEarnings != null) binding.btnOpenEarnings.setVisibility(View.GONE);
        if (binding.sectionWithdrawForm != null) binding.sectionWithdrawForm.setVisibility(View.GONE);
        if (binding.sectionConvert != null) binding.sectionConvert.setVisibility(View.GONE);
        if (binding.sectionHostTrade != null) binding.sectionHostTrade.setVisibility(View.GONE);
        if (binding.sectionHostMonthlyTarget != null) {
            binding.sectionHostMonthlyTarget.setVisibility(View.GONE);
        }
        String title = "الماس";
        if ("convert".equals(mode)) {
            title = "فك الألماس";
            if (binding.sectionConvert != null) binding.sectionConvert.setVisibility(View.VISIBLE);
        } else if ("transfer".equals(mode)) {
            title = "تحويل لفتاة";
            if (binding.sectionHostTrade != null) binding.sectionHostTrade.setVisibility(View.VISIBLE);
        } else if ("target".equals(mode)) {
            title = "تارجت المضيف";
            if (binding.sectionHostMonthlyTarget != null) {
                binding.sectionHostMonthlyTarget.setVisibility(View.VISIBLE);
            }
        } else {
            title = "سحب الدخل";
            if (binding.sectionWithdrawForm != null) {
                binding.sectionWithdrawForm.setVisibility(View.VISIBLE);
            }
        }
        if (binding.tvWalletTitle != null) binding.tvWalletTitle.setText(title);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (binding == null || intent == null) return;
        diamondActionMode = intent.getStringExtra(EXTRA_DIAMOND_ACTION);
        int startTab = intent.getIntExtra(EXTRA_TAB, -1);
        if (diamondActionMode != null && !diamondActionMode.isEmpty()) startTab = 1;
        if (startTab >= 0 && startTab <= 2) {
            TabLayout.Tab tab = binding.tabs.getTabAt(startTab);
            if (tab != null) binding.tabs.selectTab(tab);
            showPanel(startTab);
            applyDiamondActionMode(diamondActionMode);
        }
        handleRechargeReturn(intent);
    }

    private void setWithdrawChannel(boolean viaAgent) {
        withdrawViaAgent = viaAgent;
        binding.tilWithdrawMethod.setVisibility(viaAgent ? View.GONE : View.VISIBLE);
        binding.tilWithdrawAgent.setVisibility(viaAgent ? View.VISIBLE : View.GONE);
        binding.tilWithdrawDetails.setHint(viaAgent ? "ملاحظة لوكيل الشحن (اختياري)" : "تفاصيل الحساب / الإيميل");
        binding.btnChannelSelf.setBackgroundResource(
                viaAgent ? R.drawable.bg_wallet_channel_off : R.drawable.bg_wallet_channel_on);
        binding.btnChannelAgent.setBackgroundResource(
                viaAgent ? R.drawable.bg_wallet_channel_on : R.drawable.bg_wallet_channel_off);
        binding.btnChannelSelf.setTextColor(viaAgent
                ? getColor(R.color.text_secondary) : 0xFF1A1200);
        binding.btnChannelAgent.setTextColor(viaAgent
                ? 0xFF1A1200 : getColor(R.color.text_secondary));
        if (binding.tvWithdrawChannelLabel != null) {
            binding.tvWithdrawChannelLabel.setText(viaAgent
                    ? "تحويل إلى وكيل الشحن"
                    : "تحويل إلى حسابي الشخصي");
        }
        if (binding.imgWithdrawChannelBg != null) {
            binding.imgWithdrawChannelBg.setImageResource(viaAgent
                    ? R.drawable.icon_withdraw_top_collect_bg
                    : R.drawable.icon_withdraw_top_bg);
        }
        if (viaAgent && (selectedAgentName == null || selectedAgentName.isEmpty())) {
            binding.etWithdrawAgent.setText("اضغط لاختيار وكيل شحن");
        }
    }

    private void showWithdrawChannelChooser() {
        String[] labels = {
                "تحويل إلى حسابي الشخصي",
                "تحويل إلى وكيل الشحن"
        };
        AuraDialogHelper.list(this, "اختاري قناة السحب", labels, which -> {
            setWithdrawChannel(which == 1);
            if (which == 1) showAgentPickerDialog();
        });
    }

    private void preloadWithdrawAgents() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.AgentDirectoryResult> r =
                    ApiCall.execute(c.getWalletApi().withdrawAgents());
            runOnUiThread(() -> {
                withdrawAgents.clear();
                if (r.success && r.data != null && r.data.items != null) {
                    withdrawAgents.addAll(r.data.items);
                }
            });
        });
    }

    private void showAgentPickerDialog() {
        if (withdrawAgents.isEmpty()) {
            Toast.makeText(this, "جاري تحميل الوكلاء…", Toast.LENGTH_SHORT).show();
            preloadWithdrawAgents();
            c.getIoExecutor().execute(() -> {
                Result<WalletDtos.AgentDirectoryResult> r =
                        ApiCall.execute(c.getWalletApi().withdrawAgents());
                runOnUiThread(() -> {
                    withdrawAgents.clear();
                    if (r.success && r.data != null && r.data.items != null) {
                        withdrawAgents.addAll(r.data.items);
                    }
                    if (withdrawAgents.isEmpty()) {
                        Toast.makeText(this, "لا يوجد وكلاء نشطون حالياً", Toast.LENGTH_LONG).show();
                    } else {
                        showAgentPickerDialog();
                    }
                });
            });
            return;
        }
        String[] labels = new String[withdrawAgents.size()];
        for (int i = 0; i < withdrawAgents.size(); i++) {
            WalletDtos.AgentDirectoryEntry e = withdrawAgents.get(i);
            String country = e.country != null && !e.country.isEmpty() ? (" · " + e.country) : "";
            labels[i] = (e.displayName != null ? e.displayName : "وكيل شحن") + country;
        }
        AuraDialogHelper.list(this, getString(R.string.withdraw_pick_recharge_agent), labels, which -> {
            WalletDtos.AgentDirectoryEntry e = withdrawAgents.get(which);
            selectedAgentId = e.id;
            selectedAgentName = e.displayName;
            binding.etWithdrawAgent.setText(labels[which]);
        });
    }

    private void applyWalletIcons() {
        // Coin/diamond icons come from activity_bag.xml android:src.
    }

    private static void bindPackageIcon(ImageView view, WalletDtos.RechargePackageDto pkg, int position) {
        if (view == null) return;
        int tierRes = com.Dramizo.Series.util.AssetCatalog.rechargeBagForIndex(position);
        long coins = pkg != null ? pkg.coins : 0;
        if (coins > 0) {
            tierRes = com.Dramizo.Series.util.AssetCatalog.rechargeBagForCoins(coins);
        }
        // Prefer Mikoo-style local pile icons over remote URLs (same look as Mikoo grid).
        view.setImageResource(tierRes);
    }

    private void showPaymentMethodDialog() {
        String[] paymentLabels = paymentLabels();
        AuraDialogHelper.list(this, getString(R.string.payment_method), paymentLabels, which -> {
            selectedWithdrawMethod = PAY_VALUES[which];
            binding.etWithdrawMethod.setText(paymentLabels[which]);
            if (which == 0) {
                binding.etWithdrawDetails.setHint(R.string.paypal_email);
            } else if (which == 1) {
                binding.etWithdrawDetails.setHint(R.string.bank_account_details);
            } else {
                binding.etWithdrawDetails.setHint(R.string.usdt_wallet_address);
            }
        });
    }

    private String[] paymentLabels() {
        return new String[]{"PayPal", getString(R.string.bank_transfer), "USDT"};
    }

    private void buyPackage(WalletDtos.RechargePackageDto pkg) {
        if (pkg == null || pkg.sku == null || pkg.sku.isEmpty()) {
            Toast.makeText(this, "SKU غير صالح — حدّثه من لوحة التحكم", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(RechargePaymentActivity.intent(this, pkg));
    }

    private void buyWithGooglePlay(WalletDtos.RechargePackageDto pkg) {
        int totalCoins = pkg.coins + Math.max(0, pkg.bonusCoins);
        c.getBillingHelper().queryProducts(Collections.singletonList(pkg.sku), () ->
                c.getBillingHelper().launchPurchase(BagActivity.this, pkg.sku,
                        new com.Dramizo.Series.billing.BillingHelper.PurchaseCallback() {
                            @Override
                            public void onPurchaseSuccess(String sku, String purchaseToken, String orderId) {
                                c.getIoExecutor().execute(() -> {
                                    Result<WalletDtos.WalletDto> r = c.getWalletRepository().verifyPurchase(
                                            sku, purchaseToken, orderId, totalCoins, pkg.amountForVerify());
                                    runOnUiThread(() -> {
                                        if (r.success) {
                                            c.getBillingHelper().consumePendingPurchase();
                                            RewardBurstOverlay.showCoins(
                                                    BagActivity.this,
                                                    "تم الشحن بنجاح!",
                                                    String.format(java.util.Locale.US, "+%,d عملة", totalCoins));
                                            applyWallet(r.data);
                                        } else {
                                            Toast.makeText(BagActivity.this,
                                                    r.error != null ? r.error : getString(R.string.error_generic),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                });
                            }

                            @Override
                            public void onPurchaseError(String message) {
                                if (message != null && (message.contains("تم إلغاء")
                                        || message.toLowerCase().contains("cancel"))) {
                                    return;
                                }
                                Toast.makeText(BagActivity.this,
                                        "تعذر إتمام الشراء. تأكد أن المنتج مفعّل في Google Play.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }));
    }

    private void showBinanceNetworkDialog(WalletDtos.RechargePackageDto pkg) {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_binance_network, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        sheet.findViewById(R.id.btnNetworkTrx).setOnClickListener(v -> {
            dialog.dismiss();
            buyWithBinanceWallet(pkg, "TRX");
        });
        sheet.findViewById(R.id.btnNetworkBsc).setOnClickListener(v -> {
            dialog.dismiss();
            buyWithBinanceWallet(pkg, "BSC");
        });
        dialog.show();
    }

    private void buyWithBinanceWallet(WalletDtos.RechargePackageDto pkg, String network) {
        Toast.makeText(this, "جاري إنشاء طلب إيداع USDT…", Toast.LENGTH_SHORT).show();
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.BinanceWalletOrderResult> created = ApiCall.execute(
                    c.getWalletApi().createBinanceWalletOrder(
                            new WalletDtos.BinanceWalletOrderRequest(pkg.sku, network)));
            runOnUiThread(() -> {
                if (!created.success || created.data == null || created.data.order == null
                        || created.data.order.id == null) {
                    Toast.makeText(this,
                            created.error != null ? created.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                WalletDtos.BinanceWalletOrderResult result = created.data;
                if (result.address == null || result.address.isEmpty()) {
                    Toast.makeText(this,
                            result.message != null ? result.message
                                    : "Binance Wallet غير مُفعّل على السيرفر — الطلب معلّق",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                showBinanceDepositDialog(result);
                startBinanceStatusPolling(result.order.id);
            });
        });
    }

    private void showBinanceDepositDialog(WalletDtos.BinanceWalletOrderResult result) {
        dismissBinanceDepositSheet();
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_binance_deposit, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        binanceDepositSheet = dialog;

        String networkLabel = result.network != null ? result.network : "USDT";
        if ("TRX".equalsIgnoreCase(networkLabel)) networkLabel = "TRX · TRC20";
        else if ("BSC".equalsIgnoreCase(networkLabel)) networkLabel = "BSC · BEP20";

        TextView tvNetwork = sheet.findViewById(R.id.tvDepositNetwork);
        TextView tvAmount = sheet.findViewById(R.id.tvDepositAmount);
        TextView tvCoins = sheet.findViewById(R.id.tvDepositCoins);
        TextView tvAddress = sheet.findViewById(R.id.tvDepositAddress);
        TextView tvTag = sheet.findViewById(R.id.tvDepositTag);
        View tagRow = sheet.findViewById(R.id.tagRow);
        View btnCopyTag = sheet.findViewById(R.id.btnCopyTag);
        ImageView imgQr = sheet.findViewById(R.id.imgQr);
        ProgressBar progressQr = sheet.findViewById(R.id.progressQr);
        tvDepositCountdown = sheet.findViewById(R.id.tvCountdown);
        tvDepositStatus = sheet.findViewById(R.id.tvDepositStatus);
        progressDepositRing = sheet.findViewById(R.id.progressRing);
        tvHourglass = sheet.findViewById(R.id.tvHourglass);

        tvNetwork.setText(networkLabel);
        double payAmount = result.amount > 0
                ? result.amount
                : (result.order != null ? result.order.amountFiat : 0);
        String amountPlain = formatUsdtAmount(payAmount);
        tvAmount.setText(amountPlain + " USDT");
        int coins = result.order.coins + Math.max(0, result.order.bonusCoins);
        tvCoins.setText(String.format(Locale.US, "%,d عملة", coins));
        tvAddress.setText(result.address);

        boolean hasTag = result.tag != null && !result.tag.isEmpty();
        tagRow.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        btnCopyTag.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        if (hasTag) tvTag.setText(result.tag);

        sheet.findViewById(R.id.btnCopyAddress).setOnClickListener(v ->
                copyToClipboard("deposit_address", result.address));
        sheet.findViewById(R.id.btnCopyAmount).setOnClickListener(v ->
                copyToClipboard("deposit_amount", amountPlain));
        if (hasTag) {
            btnCopyTag.setOnClickListener(v -> copyToClipboard("deposit_tag", result.tag));
        }

        String expiresRaw = result.expiresAt != null ? result.expiresAt
                : (result.order != null ? result.order.expiresAt : null);
        depositExpiresAtMs = parseIsoMillis(expiresRaw, System.currentTimeMillis() + 30 * 60_000L);
        depositCreatedAtMs = System.currentTimeMillis();
        startDepositCountdown();
        startHourglassSpin();

        c.getIoExecutor().execute(() -> {
            Bitmap qr = QrBitmap.encode(result.address, 512);
            runOnUiThread(() -> {
                if (isFinishing() || imgQr == null) return;
                progressQr.setVisibility(View.GONE);
                if (qr != null) imgQr.setImageBitmap(qr);
            });
        });

        dialog.setOnDismissListener(d -> {
            if (binanceDepositSheet == dialog) binanceDepositSheet = null;
            stopDepositCountdown();
            stopHourglassSpin();
        });
        dialog.show();
    }

    private void updateDepositStatusUi(String statusText, boolean success, boolean failed) {
        if (tvDepositStatus != null) {
            tvDepositStatus.setText(statusText);
            tvDepositStatus.setTextColor(getColor(success ? R.color.aurora_success
                    : failed ? R.color.aurora_coral : R.color.aurora_mint));
        }
    }

    private void startDepositCountdown() {
        stopDepositCountdown();
        binanceCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                long left = Math.max(0L, depositExpiresAtMs - System.currentTimeMillis());
                int total = (int) Math.max(1L, depositExpiresAtMs - depositCreatedAtMs);
                int pct = (int) Math.min(100, Math.max(0, (left * 100L) / total));
                int sec = (int) (left / 1000L);
                int m = sec / 60;
                int s = sec % 60;
                if (tvDepositCountdown != null) {
                    tvDepositCountdown.setText(String.format(Locale.US, "%02d:%02d", m, s));
                }
                if (progressDepositRing != null) progressDepositRing.setProgress(pct);
                if (left <= 0) {
                    updateDepositStatusUi("انتهت مهلة الإيداع", false, true);
                    stopHourglassSpin();
                    return;
                }
                binancePollHandler.postDelayed(this, 1000L);
            }
        };
        binancePollHandler.post(binanceCountdownRunnable);
    }

    private void stopDepositCountdown() {
        if (binanceCountdownRunnable != null) {
            binancePollHandler.removeCallbacks(binanceCountdownRunnable);
            binanceCountdownRunnable = null;
        }
    }

    private void startHourglassSpin() {
        stopHourglassSpin();
        if (tvHourglass == null) return;
        hourglassAnimator = ObjectAnimator.ofFloat(tvHourglass, View.ROTATION, 0f, 180f);
        hourglassAnimator.setDuration(1600L);
        hourglassAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        hourglassAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        hourglassAnimator.setInterpolator(new LinearInterpolator());
        hourglassAnimator.start();
    }

    private void stopHourglassSpin() {
        if (hourglassAnimator != null) {
            hourglassAnimator.cancel();
            hourglassAnimator = null;
        }
        if (tvHourglass != null) tvHourglass.setRotation(0f);
    }

    private void dismissBinanceDepositSheet() {
        stopDepositCountdown();
        stopHourglassSpin();
        if (binanceDepositSheet != null) {
            try { binanceDepositSheet.dismiss(); } catch (Exception ignored) {}
            binanceDepositSheet = null;
        }
    }

    /** Store prices like 4.99 — never show trailing zeros (4.99000000). */
    private static String formatUsdtAmount(double amount) {
        if (!(amount > 0) || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0";
        }
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        String plain = bd.toPlainString();
        // Keep at least one decimal for money feel when needed: 5 -> 5, 4.90 -> 4.9, 4.99 -> 4.99
        if (plain.indexOf('.') < 0 && amount != Math.rint(amount)) {
            return String.format(Locale.US, "%.2f", amount);
        }
        return plain;
    }

    private static long parseIsoMillis(String raw, long fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(raw);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
            }
        }
        return fallback;
    }

    private void copyToClipboard(String label, String value) {
        if (value == null || value.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBinanceStatusPolling(String orderId) {
        stopBinanceStatusPolling();
        binancePolling.set(true);
        final int[] attempts = {0};
        binancePollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!binancePolling.get() || isFinishing()) return;
                attempts[0]++;
                c.getIoExecutor().execute(() -> {
                    Result<WalletDtos.BinanceWalletStatusResult> status = ApiCall.execute(
                            c.getWalletApi().binanceWalletOrderStatus(orderId));
                    runOnUiThread(() -> {
                        if (!binancePolling.get() || isFinishing()) return;
                        String orderStatus = status.success && status.data != null
                                && status.data.order != null ? status.data.order.status : null;
                        if ("paid".equalsIgnoreCase(orderStatus)
                                || "completed".equalsIgnoreCase(orderStatus)
                                || "success".equalsIgnoreCase(orderStatus)) {
                            stopBinanceStatusPolling();
                            updateDepositStatusUi("تم الشحن بنجاح ✓", true, false);
                            stopHourglassSpin();
                            Toast.makeText(BagActivity.this, "تم شحن USDT بنجاح", Toast.LENGTH_SHORT).show();
                            load();
                            binancePollHandler.postDelayed(() -> dismissBinanceDepositSheet(), 1800L);
                            return;
                        }
                        if ("failed".equalsIgnoreCase(orderStatus)
                                || "refunded".equalsIgnoreCase(orderStatus)
                                || "expired".equalsIgnoreCase(orderStatus)
                                || "cancelled".equalsIgnoreCase(orderStatus)) {
                            stopBinanceStatusPolling();
                            updateDepositStatusUi("انتهى الطلب · " + orderStatus, false, true);
                            stopHourglassSpin();
                            Toast.makeText(BagActivity.this,
                                    "انتهى طلب الإيداع بحالة: " + orderStatus,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        updateDepositStatusUi("بانتظار تأكيد الشبكة…", false, false);
                        if (attempts[0] >= 90) {
                            stopBinanceStatusPolling();
                            updateDepositStatusUi("ما زال معلّقاً — يتحدّث تلقائياً لاحقاً", false, false);
                            Toast.makeText(BagActivity.this,
                                    "ما زال الطلب معلّقاً — سيتحدّث الرصيد تلقائياً بعد تأكيد الدفع",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        binancePollHandler.postDelayed(this, 4000L);
                    });
                });
            }
        };
        binancePollHandler.postDelayed(binancePollRunnable, 2500L);
    }

    private void stopBinanceStatusPolling() {
        binancePolling.set(false);
        if (binancePollRunnable != null) {
            binancePollHandler.removeCallbacks(binancePollRunnable);
            binancePollRunnable = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopBinanceStatusPolling();
        stopDepositCountdown();
        stopHourglassSpin();
        dismissBinanceDepositSheet();
        binding = null;
        super.onDestroy();
    }

    private void showPanel(int index) {
        binding.panelCoins.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        binding.panelDiamonds.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (binding.panelDebris != null) {
            binding.panelDebris.setVisibility(View.GONE);
        }
        if (binding.panelAgent != null) {
            binding.panelAgent.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        }
        // Mikoo: sign float only on coins tab.
        if (binding.ivSignFloat != null) {
            binding.ivSignFloat.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        }
    }

    /** Y offset of a descendant inside the diamonds ScrollView content. */
    private static int offsetInScrollView(View target) {
        int y = 0;
        View v = target;
        while (v != null && !(v.getParent() instanceof android.widget.ScrollView)) {
            y += v.getTop();
            Object parent = v.getParent();
            if (!(parent instanceof View)) break;
            v = (View) parent;
        }
        return Math.max(0, y);
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            Result<WalletDtos.PackagesResult> p = c.getWalletRepository().getPackages();
            Result<WalletDtos.WithdrawList> withdraws = ApiCall.execute(c.getWalletApi().withdraws());
            Result<WalletDtos.EconomyConfig> economy =
                    ApiCall.execute(c.getWalletApi().economyConfig());
            Result<WalletDtos.WithdrawPackagesResult> withdrawPkgs =
                    ApiCall.execute(c.getWalletApi().withdrawPackages());
            Result<Map<String, Object>> hostTarget =
                    ApiCall.execute(c.getUserApi().hostTargetMe());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || binding == null) return;
                if (economy.success && economy.data != null) {
                    diamondUsdRate = economy.data.diamondUsdRate > 0
                            ? economy.data.diamondUsdRate : diamondUsdRate;
                    diamondCoinRate = economy.data.diamondCoinRate > 0
                            ? economy.data.diamondCoinRate : diamondCoinRate;
                    minWithdrawDiamonds = economy.data.minWithdrawDiamonds > 0
                            ? economy.data.minWithdrawDiamonds : minWithdrawDiamonds;
                    if (economy.data.withdrawTargetDiamonds > 0) {
                        minWithdrawDiamonds = economy.data.withdrawTargetDiamonds;
                    }
                }
                if (withdrawPkgs.success && withdrawPkgs.data != null) {
                    if (withdrawPkgs.data.diamondUsdRate > 0) {
                        diamondUsdRate = withdrawPkgs.data.diamondUsdRate;
                    }
                    if (withdrawPkgs.data.minWithdrawDiamonds > 0) {
                        minWithdrawDiamonds = withdrawPkgs.data.minWithdrawDiamonds;
                    }
                    if (withdrawPackageAdapter != null) {
                        withdrawPackageAdapter.submit(withdrawPkgs.data.items);
                    }
                }
                if (w.success) applyWallet(w.data);
                bindHostMonthlyTarget(hostTarget);
                renderWithdrawHistory(withdraws);
                List<WalletDtos.RechargePackageDto> pkgs = new ArrayList<>();
                if (p.success && p.data != null && p.data.items != null && !p.data.items.isEmpty()) {
                    pkgs.addAll(p.data.items);
                    adapter.submit(pkgs);
                    List<String> skus = new ArrayList<>();
                    for (WalletDtos.RechargePackageDto pkg : pkgs) {
                        if (pkg != null && pkg.sku != null && !pkg.sku.isEmpty()) skus.add(pkg.sku);
                    }
                    if (!skus.isEmpty()) {
                        c.getBillingHelper().queryProducts(skus, () -> {
                            c.getBillingHelper().applyPlayPrices(pkgs);
                            adapter.submit(pkgs);
                        });
                    }
                } else {
                    adapter.submit(Collections.emptyList());
                    Toast.makeText(BagActivity.this,
                            "تعذر تحميل باقات الشحن — حاول لاحقاً",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void renderWithdrawHistory(Result<WalletDtos.WithdrawList> result) {
        if (binding == null) return;
        if (!result.success || result.data == null || result.data.items == null || result.data.items.isEmpty()) {
            if (binding.tvWithdrawHistory != null) {
                binding.tvWithdrawHistory.setVisibility(View.VISIBLE);
                binding.tvWithdrawHistory.setText(R.string.no_withdrawal_requests);
            }
            withdrawAdapter.submit(Collections.emptyList());
            return;
        }
        if (binding.tvWithdrawHistory != null) binding.tvWithdrawHistory.setVisibility(View.GONE);
        withdrawAdapter.submit(result.data.items);
    }

    private void applyWallet(WalletDtos.WalletDto w) {
        if (w == null || binding == null) return;
        currentWallet = w;
        String coinsTxt = String.format(Locale.US, "%,d", w.coins);
        String diamondsTxt = String.format(Locale.US, "%,d", w.diamonds);
        binding.tvGoldBalance.setText(coinsTxt);
        binding.tvDiamondBalance.setText(diamondsTxt);
        if (binding.tvDebrisBalance != null) {
            binding.tvDebrisBalance.setText(coinsTxt);
        }
        binding.tvDiamondUsdValue.setText(String.format(Locale.US,
                "$%,.2f USD", w.diamonds * diamondUsdRate));
        if (binding.tvTraderDiamonds != null) {
            long trader = w.traderDiamonds;
            binding.tvTraderDiamonds.setText(String.format(Locale.US,
                    "تاجر: %,d", trader));
            binding.tvTraderDiamonds.setVisibility(View.VISIBLE);
        }
        if (binding.sectionHostTrade != null) {
            // Never show the form on the hub — only the focused "transfer" screen.
            boolean showForm = "transfer".equals(diamondActionMode);
            binding.sectionHostTrade.setVisibility(showForm ? View.VISIBLE : View.GONE);
        }
        if (binding.tvWithdrawHint != null) {
            binding.tvWithdrawHint.setText(String.format(Locale.US,
                    "التارجت: %,d ألماس · رصيدك القابل للسحب: %,d (≈ $%,.2f)",
                    minWithdrawDiamonds, w.diamonds, w.diamonds * diamondUsdRate));
            binding.tvWithdrawHint.setContentDescription(String.format(Locale.US,
                    "1 diamond equals $%.6f; exchange rate %.2f coins",
                    diamondUsdRate, diamondCoinRate));
        }
        if (binding.tvWithdrawTargetProgress != null) {
            binding.tvWithdrawTargetProgress.setText(String.format(Locale.US,
                    "%,d / %,d", w.diamonds, minWithdrawDiamonds));
        }
        if (binding.progressWithdrawTarget != null) {
            int pct = minWithdrawDiamonds <= 0 ? 100
                    : (int) Math.min(100, (w.diamonds * 100L) / minWithdrawDiamonds);
            binding.progressWithdrawTarget.setProgress(pct);
        }
    }

    private void bindHostMonthlyTarget(Result<Map<String, Object>> hostTarget) {
        if (binding.sectionHostMonthlyTarget == null) return;
        // Only reveal on the dedicated target screen (or when already visible).
        boolean showOnHub = "target".equals(diamondActionMode);
        if (!hostTarget.success || hostTarget.data == null
                || !Boolean.TRUE.equals(hostTarget.data.get("enabled"))) {
            if (binding.btnFocusTarget != null && diamondActionMode == null) {
                binding.btnFocusTarget.setVisibility(View.GONE);
            }
            if (!showOnHub) binding.sectionHostMonthlyTarget.setVisibility(View.GONE);
            return;
        }
        if (binding.btnFocusTarget != null && diamondActionMode == null) {
            binding.btnFocusTarget.setVisibility(View.VISIBLE);
        }
        if (!showOnHub) {
            binding.sectionHostMonthlyTarget.setVisibility(View.GONE);
            return;
        }
        binding.sectionHostMonthlyTarget.setVisibility(View.VISIBLE);
        long progress = 0;
        Object p = hostTarget.data.get("progress");
        if (p instanceof Number) progress = ((Number) p).longValue();
        long next = 0;
        Object n = hostTarget.data.get("nextThreshold");
        if (n instanceof Number) next = ((Number) n).longValue();
        long remaining = 0;
        Object r = hostTarget.data.get("remaining");
        if (r instanceof Number) remaining = ((Number) r).longValue();
        int cycle = 1;
        Object cyc = hostTarget.data.get("cycle");
        if (cyc instanceof Number) cycle = Math.max(1, ((Number) cyc).intValue());
        String month = String.valueOf(hostTarget.data.get("yearMonth"));
        if (binding.tvHostTargetMonth != null) {
            String monthLabel = month != null && !month.isEmpty() && !"null".equals(month)
                    ? month : "الشهر الحالي";
            binding.tvHostTargetMonth.setText(monthLabel + " · دورة " + cycle);
        }
        if (binding.tvHostMonthlyProgress != null) {
            if (next > 0) {
                binding.tvHostMonthlyProgress.setText(String.format(Locale.US,
                        "التقدّم %,d / %,d · متبقي %,d", progress, next, remaining));
            } else {
                binding.tvHostMonthlyProgress.setText(String.format(Locale.US,
                        "%,d — جاهز لإعادة الدورة من المرحلة 1", progress));
            }
        }
        if (binding.progressHostMonthly != null) {
            int pct = next <= 0 ? 100 : (int) Math.min(100, (progress * 100L) / Math.max(1, next));
            binding.progressHostMonthly.setProgress(pct);
        }
        if (binding.hostTargetStagesRow != null) {
            binding.hostTargetStagesRow.removeAllViews();
            Object stagesObj = hostTarget.data.get("stages");
            if (stagesObj instanceof List) {
                LayoutInflater inflater = LayoutInflater.from(this);
                int index = 0;
                for (Object row : (List<?>) stagesObj) {
                    if (!(row instanceof Map)) continue;
                    Map<?, ?> s = (Map<?, ?>) row;
                    index++;
                    View item = inflater.inflate(R.layout.item_host_target_stage,
                            binding.hostTargetStagesRow, false);
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
                        status = claimed || reached ? "done"
                                : (index == 1 ? "current" : "locked");
                    }
                    String title = String.valueOf(s.get("title"));
                    if (title == null || "null".equals(title) || title.isEmpty()) {
                        title = "مرحلة " + index;
                    }
                    long th = s.get("threshold") instanceof Number
                            ? ((Number) s.get("threshold")).longValue() : 0;
                    long rewardCoins = s.get("rewardCoins") instanceof Number
                            ? ((Number) s.get("rewardCoins")).longValue() : 0;
                    long rewardDiamonds = s.get("rewardDiamonds") instanceof Number
                            ? ((Number) s.get("rewardDiamonds")).longValue() : 0;
                    double hostSalary = s.get("hostSalaryUsd") instanceof Number
                            ? ((Number) s.get("hostSalaryUsd")).doubleValue() : 0;
                    double agentSalary = s.get("agentSalaryUsd") instanceof Number
                            ? ((Number) s.get("agentSalaryUsd")).doubleValue() : 0;
                    double totalSalary = s.get("totalUsd") instanceof Number
                            ? ((Number) s.get("totalUsd")).doubleValue()
                            : hostSalary + agentSalary;

                    if (tvIndex != null) {
                        Object idxObj = s.get("index");
                        int shown = idxObj instanceof Number
                                ? ((Number) idxObj).intValue() : index;
                        tvIndex.setText(String.valueOf(shown));
                    }
                    if (tvTitle != null) tvTitle.setText(title);
                    if (tvTh != null) {
                        tvTh.setText(String.format(Locale.US,
                                "التارجت: %,d كوين = %,d ألماسة", th, th));
                    }
                    if (tvSalary != null) {
                        if (hostSalary > 0 || agentSalary > 0 || totalSalary > 0) {
                            tvSalary.setVisibility(View.VISIBLE);
                            tvSalary.setText(String.format(Locale.US,
                                    "مضيف $%.0f · وكيل $%.0f · إجمالي $%.0f",
                                    hostSalary, agentSalary, totalSalary));
                        } else {
                            tvSalary.setVisibility(View.GONE);
                        }
                    }
                    if (tvReward != null) {
                        StringBuilder reward = new StringBuilder("مكافأة إضافية: ");
                        boolean any = false;
                        if (rewardCoins > 0) {
                            reward.append("+").append(String.format(Locale.US, "%,d", rewardCoins))
                                    .append(" كوينز");
                            any = true;
                        }
                        if (rewardDiamonds > 0) {
                            if (any) reward.append(" · ");
                            reward.append("+").append(String.format(Locale.US, "%,d", rewardDiamonds))
                                    .append(" ماسة");
                            any = true;
                        }
                        Object cosCode = s.get("rewardCosmeticCode");
                        if (cosCode != null && !"null".equals(String.valueOf(cosCode))
                                && !String.valueOf(cosCode).trim().isEmpty()) {
                            if (any) reward.append(" · ");
                            long days = s.get("rewardCosmeticDays") instanceof Number
                                    ? ((Number) s.get("rewardCosmeticDays")).longValue() : 7;
                            reward.append("إطار ").append(days).append("ي");
                            any = true;
                        }
                        Object vipLv = s.get("rewardVipLevel");
                        if (vipLv instanceof Number && ((Number) vipLv).intValue() > 0) {
                            if (any) reward.append(" · ");
                            int vl = ((Number) vipLv).intValue();
                            long vd = s.get("rewardVipDays") instanceof Number
                                    ? ((Number) s.get("rewardVipDays")).longValue() : 7;
                            reward.append("VIP").append(vl).append(" · ").append(vd).append("ي");
                            any = true;
                        }
                        if (!any) {
                            tvReward.setVisibility(View.GONE);
                        } else {
                            tvReward.setVisibility(View.VISIBLE);
                            tvReward.setText(reward.toString());
                        }
                    }

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
                            tvState.setText("مكتمل ✓");
                            tvState.setTextColor(0xFF0F766E);
                        }
                    } else if ("current".equals(status)) {
                        item.setBackgroundResource(R.drawable.bg_host_target_stage_current);
                        if (tvIndex != null) {
                            tvIndex.setBackgroundResource(R.drawable.bg_host_target_stage_badge);
                        }
                        if (imgStatus != null) imgStatus.setVisibility(View.GONE);
                        if (tvState != null) {
                            tvState.setText("الحالية");
                            tvState.setTextColor(0xFFB45309);
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
                            tvState.setText("مقفلة");
                            tvState.setTextColor(0xFF64748B);
                        }
                    }
                    binding.hostTargetStagesRow.addView(item);
                }
            }
        }
        if (binding.tvHostMonthlyStages != null) {
            binding.tvHostMonthlyStages.setVisibility(View.GONE);
        }
    }

    private void openAgentDirectoryWithPackageHint() {
        // Directory requires package pick before WhatsApp (avoids 0 coins message).
        startActivity(RechargeAgentDirectoryActivity.intent(this, null));
        Toast.makeText(this, "اختر باقة ثم راسل الوكيل", Toast.LENGTH_SHORT).show();
        if (binding.tabs != null && binding.tabs.getTabCount() > 0) {
            binding.tabs.getTabAt(0).select();
        }
    }

    private void setupAgentsStrip() {
        if (binding.recyclerAgentsStrip == null) return;
        AgentStripAdapter stripAdapter = new AgentStripAdapter(agent ->
                openAgentDirectoryWithPackageHint());
        binding.recyclerAgentsStrip.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerAgentsStrip.setAdapter(stripAdapter);
        if (binding.btnAgentsSeeAll != null) {
            binding.btnAgentsSeeAll.setOnClickListener(v -> openAgentDirectoryWithPackageHint());
        }
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.AgentDirectoryResult> result =
                    ApiCall.execute(c.getWalletApi().rechargeAgentDirectory(null));
            runOnUiThread(() -> {
                if (!result.success || result.data == null
                        || result.data.items == null || result.data.items.isEmpty()) {
                    if (binding.sectionAgentsStrip != null) {
                        binding.sectionAgentsStrip.setVisibility(View.GONE);
                    }
                    return;
                }
                if (binding.sectionAgentsStrip != null) {
                    binding.sectionAgentsStrip.setVisibility(View.VISIBLE);
                }
                List<WalletDtos.AgentDirectoryEntry> preview = new ArrayList<>();
                int limit = Math.min(12, result.data.items.size());
                for (int i = 0; i < limit; i++) preview.add(result.data.items.get(i));
                stripAdapter.submit(preview);
            });
        });
    }

    private static class AgentStripAdapter extends RecyclerView.Adapter<AgentStripAdapter.VH> {
        interface Listener { void onTap(WalletDtos.AgentDirectoryEntry agent); }
        private final List<WalletDtos.AgentDirectoryEntry> items = new ArrayList<>();
        private final Listener listener;
        AgentStripAdapter(Listener listener) { this.listener = listener; }
        void submit(List<WalletDtos.AgentDirectoryEntry> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recharge_agent_strip, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.AgentDirectoryEntry e = items.get(position);
            String name = e.displayName != null && !e.displayName.trim().isEmpty()
                    ? e.displayName.trim() : "وكيل شحن";
            h.tvName.setText(name);
            h.tvCountry.setText(com.Dramizo.Series.util.CountryCatalog.labelWithFlag(e.country));
            h.tvLetter.setText(name.substring(0, 1).toUpperCase(Locale.US));
            android.widget.ImageView cover = h.itemView.findViewById(R.id.imgAgentCover);
            android.widget.ImageView avatar = h.itemView.findViewById(R.id.imgAgentAvatar);
            if (cover != null) {
                if (e.coverUrl != null && !e.coverUrl.isEmpty()) {
                    com.bumptech.glide.Glide.with(cover)
                            .load(AssetCatalog.absoluteUrl(e.coverUrl))
                            .centerCrop()
                            .placeholder(R.drawable.bg_agent_card_cover)
                            .into(cover);
                } else {
                    cover.setImageResource(R.drawable.bg_agent_card_cover);
                }
            }
            if (avatar != null) {
                if (e.avatarUrl != null && !e.avatarUrl.isEmpty()) {
                    avatar.setVisibility(View.VISIBLE);
                    h.tvLetter.setVisibility(View.GONE);
                    com.bumptech.glide.Glide.with(avatar)
                            .load(AssetCatalog.absoluteUrl(e.avatarUrl))
                            .circleCrop()
                            .into(avatar);
                } else {
                    avatar.setVisibility(View.GONE);
                    h.tvLetter.setVisibility(View.VISIBLE);
                }
            }
            h.itemView.setOnClickListener(v -> listener.onTap(e));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvCountry;
            final TextView tvLetter;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvCountry = itemView.findViewById(R.id.tvCountry);
                tvLetter = itemView.findViewById(R.id.tvAvatarLetter);
            }
        }
    }

    private static class PkgAdapter extends RecyclerView.Adapter<PkgAdapter.VH> {
        interface Listener { void onBuy(WalletDtos.RechargePackageDto pkg); }
        private final List<WalletDtos.RechargePackageDto> items = new ArrayList<>();
        private final Listener listener;
        PkgAdapter(Listener listener) { this.listener = listener; }
        void submit(List<WalletDtos.RechargePackageDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemBagPackageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.RechargePackageDto p = items.get(position);
            int total = p.coins + Math.max(0, p.bonusCoins);
            h.b.tvPkgLabel.setText(String.format(Locale.US, "%,d", total));
            if (h.b.tvPkgTotal != null) {
                h.b.tvPkgTotal.setVisibility(View.GONE);
            }
            if (p.bonusCoins > 0) {
                h.b.tvPkgBonus.setVisibility(View.VISIBLE);
                h.b.tvPkgBonus.setText(String.format(Locale.US, "%,d", p.coins));
                h.b.tvPkgBonus.setPaintFlags(
                        h.b.tvPkgBonus.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.b.tvPkgBonus.setVisibility(View.INVISIBLE);
                h.b.tvPkgBonus.setText(" ");
            }
            if (h.b.tvPkgPopular != null) {
                h.b.tvPkgPopular.setVisibility(View.GONE);
            }
            h.b.tvPkgSku.setVisibility(View.GONE);
            h.b.tvPkgPrice.setText(p.displayPrice());
            bindPackageIcon(h.b.imgPackageImage, p, position);
            View.OnClickListener buy = v -> listener.onBuy(p);
            h.itemView.setOnClickListener(buy);
            h.b.tvPkgPrice.setOnClickListener(buy);
            if (h.b.tvPkgBuy != null) h.b.tvPkgBuy.setOnClickListener(buy);
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            final ItemBagPackageBinding b;
            VH(ItemBagPackageBinding b) { super(b.getRoot()); this.b = b; }
        }
    }

    private static class WithdrawPackageAdapter extends RecyclerView.Adapter<WithdrawPackageAdapter.VH> {
        interface Listener { void onSelect(WalletDtos.WithdrawPackageDto pkg); }
        private final List<WalletDtos.WithdrawPackageDto> items = new ArrayList<>();
        private final Listener listener;
        private int selected = -1;

        WithdrawPackageAdapter(Listener listener) { this.listener = listener; }

        void submit(List<WalletDtos.WithdrawPackageDto> data) {
            items.clear();
            selected = -1;
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_withdraw_package, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.WithdrawPackageDto p = items.get(position);
            String usd = p.label != null && !p.label.isEmpty()
                    ? p.label
                    : String.format(Locale.US, "$%.2f", p.usd);
            h.tvUsd.setText(usd);
            h.tvDiamonds.setText(String.format(Locale.US, "%,d ألماس", p.diamonds));
            h.card.setBackgroundResource(position == selected
                    ? R.drawable.bg_withdraw_package_selected
                    : R.drawable.bg_withdraw_package);
            h.itemView.setOnClickListener(v -> {
                int old = selected;
                selected = h.getBindingAdapterPosition();
                if (old >= 0) notifyItemChanged(old);
                if (selected >= 0) notifyItemChanged(selected);
                if (listener != null && selected >= 0 && selected < items.size()) {
                    listener.onSelect(items.get(selected));
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final View card;
            final TextView tvUsd;
            final TextView tvDiamonds;
            VH(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardPackage);
                tvUsd = itemView.findViewById(R.id.tvUsd);
                tvDiamonds = itemView.findViewById(R.id.tvDiamonds);
            }
        }
    }

    private static class WithdrawAdapter extends RecyclerView.Adapter<WithdrawAdapter.VH> {
        private final List<WalletDtos.WithdrawDto> items = new ArrayList<>();

        void submit(List<WalletDtos.WithdrawDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemWithdrawRequestBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            WalletDtos.WithdrawDto w = items.get(position);
            holder.b.tvWithdrawAmount.setText(holder.itemView.getContext().getString(
                    R.string.withdrawal_amount_summary, w.diamonds, w.amountFiat));
            String status = w.status != null ? w.status : "pending";
            int statusLabel = R.string.withdrawal_pending;
            int bg = R.drawable.bg_chip_member;
            if ("paid".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
                statusLabel = R.string.withdrawal_paid;
                bg = R.drawable.bg_chip_charm;
            } else if ("rejected".equalsIgnoreCase(status)) {
                statusLabel = R.string.withdrawal_rejected;
                bg = R.drawable.bg_chip_wealth;
            }
            holder.b.tvWithdrawStatus.setText(statusLabel);
            holder.b.tvWithdrawStatus.setBackgroundResource(bg);
            String time = w.createdAt != null ? w.createdAt.replace('T', ' ') : "";
            if (time.length() > 16) time = time.substring(0, 16);
            holder.b.tvWithdrawMeta.setText((w.method != null ? w.method.toUpperCase() : "PAYPAL")
                    + (time.isEmpty() ? "" : " · " + time));
            holder.b.tvWithdrawPayout.setText(w.adminNote != null && !w.adminNote.isEmpty()
                    ? w.adminNote
                    : holder.itemView.getContext().getString(R.string.withdrawal_pending_note));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ItemWithdrawRequestBinding b;
            VH(ItemWithdrawRequestBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
