package com.Dramizo.Series.presentation.wallet;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.QrBitmap;
import com.Dramizo.Series.util.RemoteTheme;
import com.Dramizo.Series.util.RewardBurstOverlay;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full payment flow on one screen: methods → (crypto network → deposit) / Google Play / card / Sham Cash / agent.
 */
public class RechargePaymentActivity extends ThemedActivity {

    public static final String EXTRA_SKU = "sku";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_COINS = "coins";
    public static final String EXTRA_BONUS = "bonus";
    /** Server USD price — card / crypto / agent. */
    public static final String EXTRA_PRICE = "price";
    /** Play Console formatted label (may be TRY/EUR/…). Optional. */
    public static final String EXTRA_PRICE_LABEL = "price_label";
    /** Play local numeric amount for purchase verify. Optional. */
    public static final String EXTRA_PLAY_AMOUNT = "play_amount";
    public static final String EXTRA_PLAY_LOADED = "play_loaded";

    private enum Step { METHODS, NETWORK, DEPOSIT }

    private AppContainer c;
    private WalletDtos.RechargePackageDto pkg;
    private Step step = Step.METHODS;
    @Nullable private MiscDtos.ShamCashConfigDto shamCashConfig;

    private TextView tvTitle;
    private View panelMethods;
    private View panelNetwork;
    private View panelDeposit;
    private FrameLayout loadingOverlay;
    private TextView tvLoading;

    private TextView tvDepositNetwork;
    private TextView tvDepositAmount;
    private TextView tvDepositCoins;
    private TextView tvDepositAddress;
    private TextView tvDepositTag;
    private TextView tvDepositCountdown;
    private TextView tvDepositStatus;
    private TextView tvHourglass;
    private View tagRow;
    private View btnCopyTag;
    private ImageView imgQr;
    private ProgressBar progressQr;
    private ProgressBar progressDepositRing;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private Runnable countdownRunnable;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private ObjectAnimator hourglassAnimator;
    private long depositExpiresAtMs;
    private long depositCreatedAtMs;
    private String depositAmountPlain = "";

    public static Intent intent(Context ctx, WalletDtos.RechargePackageDto pkg) {
        Intent i = new Intent(ctx, RechargePaymentActivity.class);
        if (pkg != null) {
            i.putExtra(EXTRA_SKU, pkg.sku);
            i.putExtra(EXTRA_LABEL, pkg.label);
            i.putExtra(EXTRA_COINS, pkg.coins);
            i.putExtra(EXTRA_BONUS, pkg.bonusCoins);
            i.putExtra(EXTRA_PRICE, pkg.priceUsd);
            i.putExtra(EXTRA_PRICE_LABEL, pkg.playPriceLabel);
            i.putExtra(EXTRA_PLAY_AMOUNT, pkg.playPriceAmount);
            i.putExtra(EXTRA_PLAY_LOADED, pkg.playPriceLoaded);
        }
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recharge_payment);
        RemoteTheme.applyActivityBackground(this, "app");
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.contentRoot));
        c = ContainerProvider.from(this);

        pkg = packageFromIntent(getIntent());
        if (pkg == null || pkg.sku == null || pkg.sku.isEmpty()) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvTitle);
        panelMethods = findViewById(R.id.panelMethods);
        panelNetwork = findViewById(R.id.panelNetwork);
        panelDeposit = findViewById(R.id.panelDeposit);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoading = findViewById(R.id.tvLoading);

        tvDepositNetwork = findViewById(R.id.tvDepositNetwork);
        tvDepositAmount = findViewById(R.id.tvDepositAmount);
        tvDepositCoins = findViewById(R.id.tvDepositCoins);
        tvDepositAddress = findViewById(R.id.tvDepositAddress);
        tvDepositTag = findViewById(R.id.tvDepositTag);
        tvDepositCountdown = findViewById(R.id.tvCountdown);
        tvDepositStatus = findViewById(R.id.tvDepositStatus);
        tvHourglass = findViewById(R.id.tvHourglass);
        tagRow = findViewById(R.id.tagRow);
        btnCopyTag = findViewById(R.id.btnCopyTag);
        imgQr = findViewById(R.id.imgQr);
        progressQr = findViewById(R.id.progressQr);
        progressDepositRing = findViewById(R.id.progressRing);

        TextView summary = findViewById(R.id.tvPackageSummary);
        String label = pkg.label != null && !pkg.label.isEmpty()
                ? pkg.label
                : getString(R.string.app_name);
        int totalCoins = pkg.coins + Math.max(0, pkg.bonusCoins);
        // Methods screen: show USD (card/crypto) — Play has its own local price at purchase time.
        summary.setText(getString(R.string.pay_package_summary, label, totalCoins, pkg.usdPriceLabel()));

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackNav());
        findViewById(R.id.btnPayGoogle).setOnClickListener(v -> buyWithGooglePlay());
        findViewById(R.id.btnPayCrypto).setOnClickListener(v -> showStep(Step.NETWORK));
        findViewById(R.id.btnPayCard).setOnClickListener(v ->
                startActivity(CardCheckoutActivity.intent(this, pkg)));
        findViewById(R.id.btnPayShamCash).setOnClickListener(v -> payWithShamCash());
        View btnPayAgent = findViewById(R.id.btnPayAgent);
        if (btnPayAgent != null) btnPayAgent.setVisibility(View.GONE);
        findViewById(R.id.btnNetworkTrx).setOnClickListener(v -> buyWithCrypto("TRX"));
        findViewById(R.id.btnNetworkBsc).setOnClickListener(v -> buyWithCrypto("BSC"));

        showStep(Step.METHODS);
        loadShamCashMethod();
    }

    private void loadShamCashMethod() {
        View btn = findViewById(R.id.btnPayShamCash);
        if (btn != null) btn.setVisibility(View.VISIBLE);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ShamCashConfigDto> r = ApiCall.execute(c.getConfigApi().shamCash());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!r.success || r.data == null) {
                    shamCashConfig = null;
                    return;
                }
                shamCashConfig = r.data;
                TextView title = findViewById(R.id.tvPayShamCashTitle);
                TextView hint = findViewById(R.id.tvPayShamCashHint);
                if (title != null && r.data.displayName != null && !r.data.displayName.trim().isEmpty()) {
                    title.setText(r.data.displayName.trim());
                }
                if (hint != null) {
                    String accountId = r.data.accountId != null ? r.data.accountId.trim() : "";
                    if (accountId.length() < 8) {
                        hint.setText(R.string.pay_sham_cash_coming_soon);
                    } else if (r.data.instructions != null && !r.data.instructions.trim().isEmpty()) {
                        hint.setText(r.data.instructions.trim());
                    } else {
                        hint.setText(R.string.pay_sham_cash_hint);
                    }
                }
            });
        });
    }

    private void payWithShamCash() {
        String accountId = shamCashConfig != null ? shamCashConfig.accountId : null;
        if (accountId == null || accountId.trim().length() < 8) {
            Toast.makeText(this, R.string.pay_sham_cash_coming_soon, Toast.LENGTH_SHORT).show();
            loadShamCashMethod();
            return;
        }
        startActivity(ShamCashCheckoutActivity.intent(this, pkg));
    }

    private void onBackNav() {
        if (step == Step.DEPOSIT) {
            stopPolling();
            stopCountdown();
            stopHourglass();
            showStep(Step.NETWORK);
            return;
        }
        if (step == Step.NETWORK) {
            showStep(Step.METHODS);
            return;
        }
        navigateUp();
    }

    @Override
    public void onBackPressed() {
        if (step != Step.METHODS) {
            onBackNav();
            return;
        }
        super.onBackPressed();
    }

    private void showStep(Step next) {
        step = next;
        panelMethods.setVisibility(next == Step.METHODS ? View.VISIBLE : View.GONE);
        panelNetwork.setVisibility(next == Step.NETWORK ? View.VISIBLE : View.GONE);
        panelDeposit.setVisibility(next == Step.DEPOSIT ? View.VISIBLE : View.GONE);
        if (tvTitle != null) {
            if (next == Step.NETWORK) tvTitle.setText(R.string.pay_crypto_network_title);
            else if (next == Step.DEPOSIT) tvTitle.setText(R.string.pay_usdt_title);
            else tvTitle.setText(R.string.choose_payment_method);
        }
    }

    private void buyWithGooglePlay() {
        showLoading(getString(R.string.pay_google_opening));
        int totalCoins = pkg.coins + Math.max(0, pkg.bonusCoins);
        c.getBillingHelper().queryProducts(Collections.singletonList(pkg.sku), () -> {
            hideLoading();
            c.getBillingHelper().launchPurchase(this, pkg.sku,
                    new com.Dramizo.Series.billing.BillingHelper.PurchaseCallback() {
                        @Override
                            public void onPurchaseSuccess(String sku, String purchaseToken, String orderId) {
                            showLoading(getString(R.string.loading));
                            c.getIoExecutor().execute(() -> {
                                Result<WalletDtos.WalletDto> r = c.getWalletRepository().verifyPurchase(
                                        sku, purchaseToken, orderId, totalCoins, pkg.amountForVerify());
                                runOnUiThread(() -> {
                                    hideLoading();
                                    if (r.success && r.data != null) {
                                        c.getBillingHelper().consumePendingPurchase();
                                        long newCoins = r.data.coins;
                                        RewardBurstOverlay.showCoins(
                                                RechargePaymentActivity.this,
                                                getString(R.string.pay_recharge_success),
                                                String.format(Locale.US, "+%,d", totalCoins));
                                        Toast.makeText(RechargePaymentActivity.this,
                                                String.format(Locale.US,
                                                        "تم إضافة الرصيد · رصيدك الآن %,d",
                                                        newCoins),
                                                Toast.LENGTH_LONG).show();
                                        pollHandler.postDelayed(() -> {
                                            if (!isFinishing()) finish();
                                        }, 1400);
                                    } else {
                                        // Purchase may already be paid on Play — keep token for retry;
                                        // do NOT consume until server credits.
                                        String err = r.error != null ? r.error
                                                : "تم الدفع لكن لم يُضف الرصيد — أعد فتح المحفظة";
                                        Toast.makeText(RechargePaymentActivity.this,
                                                err, Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                        }

                        @Override
                        public void onPurchaseError(String message) {
                            hideLoading();
                            if (message != null && (message.contains("تم إلغاء")
                                    || message.toLowerCase(Locale.US).contains("cancel"))) {
                                return;
                            }
                            Toast.makeText(RechargePaymentActivity.this,
                                    message != null ? message : getString(R.string.error_generic),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void buyWithCrypto(String network) {
        showLoading(getString(R.string.pay_crypto_creating));
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.BinanceWalletOrderResult> created = ApiCall.execute(
                    c.getWalletApi().createBinanceWalletOrder(
                            new WalletDtos.BinanceWalletOrderRequest(pkg.sku, network)));
            runOnUiThread(() -> {
                hideLoading();
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
                                    : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                bindDeposit(result);
                showStep(Step.DEPOSIT);
                startPolling(result.order.id);
            });
        });
    }

    private void bindDeposit(WalletDtos.BinanceWalletOrderResult result) {
        String networkLabel = result.network != null ? result.network : "USDT";
        if ("TRX".equalsIgnoreCase(networkLabel)) networkLabel = "TRX · TRC20";
        else if ("BSC".equalsIgnoreCase(networkLabel)) networkLabel = "BSC · BEP20";
        tvDepositNetwork.setText(networkLabel);

        double payAmount = result.amount > 0
                ? result.amount
                : (result.order != null ? result.order.amountFiat : 0);
        depositAmountPlain = formatUsdtAmount(payAmount);
        tvDepositAmount.setText(depositAmountPlain + " USDT");
        int coins = result.order.coins + Math.max(0, result.order.bonusCoins);
        tvDepositCoins.setText(String.format(Locale.US, "%,d", coins));
        tvDepositAddress.setText(result.address);

        boolean hasTag = result.tag != null && !result.tag.isEmpty();
        tagRow.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        btnCopyTag.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        if (hasTag) tvDepositTag.setText(result.tag);

        findViewById(R.id.btnCopyAddress).setOnClickListener(v ->
                copyToClipboard("deposit_address", result.address));
        findViewById(R.id.btnCopyAmount).setOnClickListener(v ->
                copyToClipboard("deposit_amount", depositAmountPlain));
        if (hasTag) {
            btnCopyTag.setOnClickListener(v -> copyToClipboard("deposit_tag", result.tag));
        }

        String expiresRaw = result.expiresAt != null ? result.expiresAt
                : (result.order != null ? result.order.expiresAt : null);
        depositExpiresAtMs = parseIsoMillis(expiresRaw, System.currentTimeMillis() + 30 * 60_000L);
        depositCreatedAtMs = System.currentTimeMillis();
        updateDepositStatus(getString(R.string.pay_deposit_waiting), false, false);
        startCountdown();
        startHourglass();

        if (progressQr != null) progressQr.setVisibility(View.VISIBLE);
        if (imgQr != null) imgQr.setImageDrawable(null);
        c.getIoExecutor().execute(() -> {
            Bitmap qr = QrBitmap.encode(result.address, 512);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (progressQr != null) progressQr.setVisibility(View.GONE);
                if (qr != null && imgQr != null) imgQr.setImageBitmap(qr);
            });
        });
    }

    private void startPolling(String orderId) {
        stopPolling();
        polling.set(true);
        final int[] attempts = {0};
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!polling.get() || isFinishing()) return;
                attempts[0]++;
                c.getIoExecutor().execute(() -> {
                    Result<WalletDtos.BinanceWalletStatusResult> status = ApiCall.execute(
                            c.getWalletApi().binanceWalletOrderStatus(orderId));
                    runOnUiThread(() -> {
                        if (!polling.get() || isFinishing()) return;
                        String orderStatus = status.success && status.data != null
                                && status.data.order != null ? status.data.order.status : null;
                        if ("paid".equalsIgnoreCase(orderStatus)
                                || "completed".equalsIgnoreCase(orderStatus)
                                || "success".equalsIgnoreCase(orderStatus)) {
                            stopPolling();
                            stopHourglass();
                            updateDepositStatus(getString(R.string.pay_recharge_success), true, false);
                            int total = pkg.coins + Math.max(0, pkg.bonusCoins);
                            RewardBurstOverlay.showCoins(
                                    RechargePaymentActivity.this,
                                    getString(R.string.pay_recharge_success),
                                    String.format(Locale.US, "+%,d", total));
                            pollHandler.postDelayed(() -> {
                                if (!isFinishing()) finish();
                            }, 1600);
                            return;
                        }
                        if ("failed".equalsIgnoreCase(orderStatus)
                                || "refunded".equalsIgnoreCase(orderStatus)
                                || "expired".equalsIgnoreCase(orderStatus)
                                || "cancelled".equalsIgnoreCase(orderStatus)) {
                            stopPolling();
                            stopHourglass();
                            updateDepositStatus(orderStatus, false, true);
                            return;
                        }
                        updateDepositStatus(getString(R.string.pay_deposit_waiting), false, false);
                        if (attempts[0] < 90) {
                            pollHandler.postDelayed(this, 4000L);
                        }
                    });
                });
            }
        };
        pollHandler.postDelayed(pollRunnable, 2500L);
    }

    private void stopPolling() {
        polling.set(false);
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void startCountdown() {
        stopCountdown();
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || step != Step.DEPOSIT) return;
                long left = Math.max(0L, depositExpiresAtMs - System.currentTimeMillis());
                int total = (int) Math.max(1L, depositExpiresAtMs - depositCreatedAtMs);
                int pct = (int) Math.min(100, Math.max(0, (left * 100L) / total));
                int sec = (int) (left / 1000L);
                if (tvDepositCountdown != null) {
                    tvDepositCountdown.setText(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
                }
                if (progressDepositRing != null) progressDepositRing.setProgress(pct);
                if (left <= 0) {
                    updateDepositStatus("انتهت المهلة", false, true);
                    stopHourglass();
                    return;
                }
                pollHandler.postDelayed(this, 1000L);
            }
        };
        pollHandler.post(countdownRunnable);
    }

    private void stopCountdown() {
        if (countdownRunnable != null) {
            pollHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    private void startHourglass() {
        stopHourglass();
        if (tvHourglass == null) return;
        hourglassAnimator = ObjectAnimator.ofFloat(tvHourglass, View.ROTATION, 0f, 180f);
        hourglassAnimator.setDuration(1600L);
        hourglassAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        hourglassAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        hourglassAnimator.setInterpolator(new LinearInterpolator());
        hourglassAnimator.start();
    }

    private void stopHourglass() {
        if (hourglassAnimator != null) {
            hourglassAnimator.cancel();
            hourglassAnimator = null;
        }
        if (tvHourglass != null) tvHourglass.setRotation(0f);
    }

    private void updateDepositStatus(String text, boolean success, boolean failed) {
        if (tvDepositStatus == null) return;
        tvDepositStatus.setText(text);
        tvDepositStatus.setTextColor(getColor(success ? R.color.aurora_success
                : failed ? R.color.aurora_coral : R.color.aurora_mint));
    }

    private void showLoading(String msg) {
        if (tvLoading != null) tvLoading.setText(msg);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void copyToClipboard(String label, String value) {
        if (value == null || value.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatUsdtAmount(double amount) {
        if (!(amount > 0) || Double.isNaN(amount) || Double.isInfinite(amount)) return "0";
        java.math.BigDecimal bd = java.math.BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return bd.toPlainString();
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

    static WalletDtos.RechargePackageDto packageFromIntent(Intent intent) {
        if (intent == null) return null;
        String sku = intent.getStringExtra(EXTRA_SKU);
        if (sku == null || sku.isEmpty()) return null;
        WalletDtos.RechargePackageDto p = new WalletDtos.RechargePackageDto();
        p.sku = sku;
        p.label = intent.getStringExtra(EXTRA_LABEL);
        p.coins = intent.getIntExtra(EXTRA_COINS, 0);
        p.bonusCoins = intent.getIntExtra(EXTRA_BONUS, 0);
        p.priceUsd = intent.getDoubleExtra(EXTRA_PRICE, 0);
        p.playPriceLoaded = intent.getBooleanExtra(EXTRA_PLAY_LOADED, false);
        p.playPriceAmount = intent.getDoubleExtra(EXTRA_PLAY_AMOUNT, 0);
        String priceLabel = intent.getStringExtra(EXTRA_PRICE_LABEL);
        if (priceLabel != null && !priceLabel.isEmpty()) {
            p.playPriceLabel = priceLabel;
            // Legacy intents only sent a label + muddled amount — keep label for Play UI.
            if (!p.playPriceLoaded) {
                p.playPriceLoaded = true;
                if (p.playPriceAmount <= 0) p.playPriceAmount = p.priceUsd;
            }
        }
        if (p.label == null || p.label.isEmpty()) {
            p.label = String.format(Locale.US, "%,d", p.coins);
        }
        return p;
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        stopCountdown();
        stopHourglass();
        super.onDestroy();
    }
}
