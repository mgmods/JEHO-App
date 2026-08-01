package com.Dramizo.Series.presentation.wallet;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Become / operate as a recharge agent.
 * Apply flow: coins → network (TRX/BSC) → deposit sheet → order form → submit.
 */
public class RechargeAgentActivity extends ThemedActivity {
    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private AppContainer container;
    private ProgressBar progress;
    private LinearLayout cardPricing;
    private LinearLayout cardStepCoins;
    private LinearLayout cardNetworkSelected;
    private LinearLayout cardStepOrder;
    private LinearLayout cardStatus;
    private LinearLayout cardSell;

    private TextView tvMembershipFee;
    private TextView tvWholesale;
    private TextView tvRetailHint;
    private TextView tvQuote;
    private TextView tvSelectedNetwork;
    private TextInputEditText etRequestedCoins;
    private TextInputEditText etPaymentReference;
    private TextInputEditText etContact;
    private TextInputEditText etRegion;
    private TextInputEditText etReason;
    private MaterialButton btnContinueNetwork;
    private MaterialButton btnSubmitApplication;

    private Map<String, Object> pricing = new HashMap<>();
    private String selectedNetwork = "TRX";
    private String depositAddress = "";
    private String depositTag = "";
    private double quoteTotalUsdt;
    private double depositPayAmount;
    private String depositOrderId;
    private String verifiedRecipient;
    private boolean depositPaid;
    @Nullable private ObjectAnimator hourglassAnimator;
    @Nullable private BottomSheetDialog depositSheet;
    @Nullable private MaterialButton btnAgentPaidUi;
    @Nullable private TextView tvAgentDepositStatusUi;
    private final Handler depositPollHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean depositPolling = new AtomicBoolean(false);
    @Nullable private Runnable depositPollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recharge_agent);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.header));
        container = ContainerProvider.from(this);

        progress = findViewById(R.id.progress);
        cardPricing = findViewById(R.id.cardPricing);
        cardStepCoins = findViewById(R.id.cardStepCoins);
        cardNetworkSelected = findViewById(R.id.cardNetworkSelected);
        cardStepOrder = findViewById(R.id.cardStepOrder);
        cardStatus = findViewById(R.id.cardStatus);
        cardSell = findViewById(R.id.cardSell);

        tvMembershipFee = findViewById(R.id.tvMembershipFee);
        tvWholesale = findViewById(R.id.tvWholesale);
        tvRetailHint = findViewById(R.id.tvRetailHint);
        tvQuote = findViewById(R.id.tvQuote);
        tvSelectedNetwork = findViewById(R.id.tvSelectedNetwork);
        etRequestedCoins = findViewById(R.id.etRequestedCoins);
        etPaymentReference = findViewById(R.id.etPaymentReference);
        etContact = findViewById(R.id.etContact);
        etRegion = findViewById(R.id.etRegion);
        etReason = findViewById(R.id.etReason);
        btnContinueNetwork = findViewById(R.id.btnContinueNetwork);
        btnSubmitApplication = findViewById(R.id.btnSubmitApplication);

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        findViewById(R.id.btnChangeNetwork).setOnClickListener(v -> showNetworkSheet());
        btnContinueNetwork.setOnClickListener(v -> onContinueFromCoins());
        btnSubmitApplication.setOnClickListener(v -> submitApplication());

        etRequestedCoins.addTextChangedListener(simpleWatcher(this::updateQuote));
        loadStatus();
    }

    private void loadStatus() {
        setLoading(true);
        hideAllPanels();
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result =
                    ApiCall.execute(container.getWalletApi().rechargeAgentMe());
            runOnUiThread(() -> {
                setLoading(false);
                if (!result.success || result.data == null) {
                    Toast.makeText(this,
                            result.error != null ? result.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    showApplyUi(null);
                    return;
                }
                pricing = mutableMap(asMap(result.data.get("pricing")));
                Map<?, ?> agent = asMap(result.data.get("agent"));
                if (agent != null && "active".equals(String.valueOf(agent.get("status")))) {
                    showSellUi(agent);
                    return;
                }
                if (agent != null && "suspended".equals(String.valueOf(agent.get("status")))) {
                    showStatusCard("حساب الوكيل معلّق",
                            "السبب: " + string(agent.get("notes")), false);
                    return;
                }
                showApplyUi(asMap(result.data.get("application")));
            });
        });
    }

    private void hideAllPanels() {
        cardPricing.setVisibility(View.GONE);
        cardStepCoins.setVisibility(View.GONE);
        cardNetworkSelected.setVisibility(View.GONE);
        cardStepOrder.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        cardSell.setVisibility(View.GONE);
        cardSell.removeAllViews();
        cardStatus.removeAllViews();
    }

    private void showApplyUi(@Nullable Map<?, ?> application) {
        if (application != null && "pending".equals(String.valueOf(application.get("status")))) {
            showStatusCard("طلبك قيد المراجعة",
                    "المبلغ: " + money(application.get("totalPaidUsdt")) + " USDT\n"
                            + "الرصيد: " + formatLong(application.get("requestedCoins")) + " عملة\n"
                            + "المرجع: " + string(application.get("paymentReference")),
                    true);
            return;
        }
        if (application != null && "rejected".equals(String.valueOf(application.get("status")))) {
            showStatusCard("تم رفض الطلب",
                    "السبب: " + string(application.get("reviewNote")), false);
        }

        cardPricing.setVisibility(View.VISIBLE);
        cardStepCoins.setVisibility(View.VISIBLE);
        bindPricing();
        long min = Math.max(10_000L, number(pricing.get("minInitialCoins")));
        if (etRequestedCoins.getText() == null || etRequestedCoins.getText().length() == 0) {
            etRequestedCoins.setText(String.valueOf(min));
        }
        updateQuote();
        cardStepOrder.setVisibility(View.GONE);
        cardNetworkSelected.setVisibility(View.GONE);
    }

    private void bindPricing() {
        tvMembershipFee.setText("رسوم العضوية: " + money(pricing.get("membershipFeeUsdt")) + " USDT");
        tvWholesale.setText("سعر الجملة: " + money(pricing.get("wholesalePer100CoinsUsdt"))
                + " USDT لكل 100 عملة");
        tvRetailHint.setText("سعر البيع المقترح: "
                + money(pricing.get("suggestedRetailPer100CoinsUsdt")) + " USDT / 100");
    }

    private void updateQuote() {
        long coins = parseLong(value(etRequestedCoins));
        double membership = decimal(pricing.get("membershipFeeUsdt"));
        double wholesale = decimal(pricing.get("wholesalePer100CoinsUsdt"));
        double stock = (coins / 100.0) * wholesale;
        quoteTotalUsdt = membership + stock;
        tvQuote.setText(String.format(Locale.US,
                "الإجمالي المطلوب: %s USDT\n(رسوم %s + رصيد %s)",
                MONEY.format(quoteTotalUsdt),
                MONEY.format(membership),
                MONEY.format(stock)));
    }

    private void onContinueFromCoins() {
        long coins = parseLong(value(etRequestedCoins));
        long min = number(pricing.get("minInitialCoins"));
        long max = number(pricing.get("maxInitialCoins"));
        if (min <= 0) min = 10_000;
        if (max <= 0) max = 10_000_000;
        if (coins < min || coins > max) {
            Toast.makeText(this,
                    "الرصيد يجب أن يكون بين " + formatLong(min) + " و " + formatLong(max),
                    Toast.LENGTH_LONG).show();
            return;
        }
        updateQuote();
        showNetworkSheet();
    }

    private void showNetworkSheet() {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_binance_network, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        sheet.findViewById(R.id.btnNetworkTrx).setOnClickListener(v -> {
            dialog.dismiss();
            onNetworkChosen("TRX");
        });
        sheet.findViewById(R.id.btnNetworkBsc).setOnClickListener(v -> {
            dialog.dismiss();
            onNetworkChosen("BSC");
        });
        dialog.show();
    }

    private void onNetworkChosen(String network) {
        selectedNetwork = network;
        String label = "TRX".equals(network) ? "TRX · TRC20" : "BSC · BEP20";
        tvSelectedNetwork.setText("الشبكة: " + label);
        cardNetworkSelected.setVisibility(View.VISIBLE);
        createPaymentDetails(network);
    }

    private void createPaymentDetails(String network) {
        setLoading(true);
        Toast.makeText(this, "جاري إنشاء طلب الدفع…", Toast.LENGTH_SHORT).show();
        long coins = parseLong(value(etRequestedCoins));
        container.getIoExecutor().execute(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("network", network);
            body.put("requestedCoins", coins);
            Result<Map<String, Object>> result = ApiCall.execute(
                    container.getWalletApi().createAgentDepositOrder(body));
            runOnUiThread(() -> {
                setLoading(false);
                if (!result.success || result.data == null) {
                    Toast.makeText(this,
                            result.error != null ? result.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Map<String, Object> data = result.data;
                depositAddress = string(data.get("address"));
                Object tagObj = data.get("tag");
                depositTag = tagObj != null ? string(tagObj) : "";
                depositPayAmount = decimal(data.get("amount"));
                if (depositPayAmount <= 0) depositPayAmount = quoteTotalUsdt;
                Object orderObj = data.get("order");
                depositOrderId = null;
                if (orderObj instanceof Map) {
                    depositOrderId = string(((Map<?, ?>) orderObj).get("id"));
                }
                if (depositOrderId == null || depositOrderId.isEmpty()) {
                    depositOrderId = string(data.get("orderId"));
                }
                if ("—".equals(depositAddress) || depositAddress.isEmpty()) {
                    Toast.makeText(this, "عنوان الإيداع غير متوفر حالياً", Toast.LENGTH_LONG).show();
                    return;
                }
                if (depositOrderId == null || depositOrderId.isEmpty()) {
                    Toast.makeText(this, "تعذر إنشاء طلب التتبع", Toast.LENGTH_LONG).show();
                    return;
                }
                depositPaid = false;
                showDepositSheet();
            });
        });
    }

    private void showDepositSheet() {
        dismissDepositSheet();
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_agent_deposit, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        depositSheet = dialog;

        String networkLabel = "TRX".equalsIgnoreCase(selectedNetwork) ? "TRX · TRC20" : "BSC · BEP20";
        TextView tvNet = sheet.findViewById(R.id.tvAgentDepositNetwork);
        TextView tvAmount = sheet.findViewById(R.id.tvAgentDepositAmount);
        TextView tvCoins = sheet.findViewById(R.id.tvAgentDepositCoins);
        TextView tvAddress = sheet.findViewById(R.id.tvAgentDepositAddress);
        TextView tvTag = sheet.findViewById(R.id.tvAgentDepositTag);
        View tagRow = sheet.findViewById(R.id.agentTagRow);
        View btnCopyTag = sheet.findViewById(R.id.btnAgentCopyTag);
        TextView tvHourglass = sheet.findViewById(R.id.tvHourglass);
        btnAgentPaidUi = sheet.findViewById(R.id.btnAgentPaid);
        tvAgentDepositStatusUi = sheet.findViewById(R.id.tvAgentDepositStatus);

        tvNet.setText(networkLabel);
        String amountPlain = MONEY.format(depositPayAmount > 0 ? depositPayAmount : quoteTotalUsdt);
        tvAmount.setText(amountPlain + " USDT");
        tvCoins.setText(String.format(Locale.US, "%,d عملة افتتاحية",
                parseLong(value(etRequestedCoins))));
        tvAddress.setText(depositAddress);

        boolean hasTag = depositTag != null && !depositTag.isEmpty() && !"—".equals(depositTag);
        tagRow.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        btnCopyTag.setVisibility(hasTag ? View.VISIBLE : View.GONE);
        if (hasTag) tvTag.setText(depositTag);

        sheet.findViewById(R.id.btnAgentCopyAddress).setOnClickListener(v ->
                copy("deposit_address", depositAddress));
        sheet.findViewById(R.id.btnAgentCopyAmount).setOnClickListener(v ->
                copy("deposit_amount", amountPlain));
        if (hasTag) {
            btnCopyTag.setOnClickListener(v -> copy("deposit_tag", depositTag));
        }

        updateDepositPaidUi(false, "بانتظار تأكيد الشبكة…",
                "أرسل المبلغ بالضبط — الكشف تلقائي فوري");
        startHourglass(tvHourglass);
        startDepositStatusPolling(depositOrderId);
        dialog.setOnDismissListener(d -> {
            if (depositSheet == dialog) depositSheet = null;
            stopHourglass();
            // Keep polling briefly only while sheet open; stop when dismissed unless paid.
            if (!depositPaid) stopDepositStatusPolling();
            btnAgentPaidUi = null;
            tvAgentDepositStatusUi = null;
        });
        dialog.show();
    }

    private void updateDepositPaidUi(boolean paid, String buttonText, String statusText) {
        if (btnAgentPaidUi != null) {
            btnAgentPaidUi.setEnabled(false);
            btnAgentPaidUi.setText(buttonText);
            btnAgentPaidUi.setBackgroundTintList(ContextCompat.getColorStateList(
                    this, paid ? R.color.aurora_mint : R.color.aurora_cyan));
            if (!paid) {
                btnAgentPaidUi.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF445566));
            }
        }
        if (tvAgentDepositStatusUi != null) {
            tvAgentDepositStatusUi.setText(statusText);
            tvAgentDepositStatusUi.setTextColor(paid
                    ? ContextCompat.getColor(this, R.color.aurora_mint)
                    : 0x88FFFFFF);
        }
    }

    private void startDepositStatusPolling(String orderId) {
        stopDepositStatusPolling();
        if (orderId == null || orderId.isEmpty()) return;
        depositPolling.set(true);
        final int[] attempts = {0};
        depositPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!depositPolling.get() || isFinishing()) return;
                attempts[0]++;
                container.getIoExecutor().execute(() -> {
                    Result<WalletDtos.BinanceWalletStatusResult> status = ApiCall.execute(
                            container.getWalletApi().binanceWalletOrderStatus(orderId));
                    runOnUiThread(() -> {
                        if (!depositPolling.get() || isFinishing()) return;
                        String orderStatus = status.success && status.data != null
                                && status.data.order != null ? status.data.order.status : null;
                        if ("paid".equalsIgnoreCase(orderStatus)
                                || "completed".equalsIgnoreCase(orderStatus)
                                || "success".equalsIgnoreCase(orderStatus)) {
                            stopDepositStatusPolling();
                            depositPaid = true;
                            stopHourglass();
                            String ref = orderId;
                            if (status.data != null && status.data.order != null) {
                                if (status.data.order.providerPaymentId != null
                                        && !status.data.order.providerPaymentId.isEmpty()) {
                                    ref = status.data.order.providerPaymentId;
                                } else if (status.data.order.id != null) {
                                    ref = status.data.order.id;
                                }
                            }
                            onDepositDetected(ref);
                            return;
                        }
                        if ("failed".equalsIgnoreCase(orderStatus)
                                || "refunded".equalsIgnoreCase(orderStatus)
                                || "expired".equalsIgnoreCase(orderStatus)
                                || "cancelled".equalsIgnoreCase(orderStatus)) {
                            stopDepositStatusPolling();
                            stopHourglass();
                            updateDepositPaidUi(false, "انتهى الطلب",
                                    "انتهى الطلب · " + orderStatus);
                            Toast.makeText(RechargeAgentActivity.this,
                                    "انتهى طلب الإيداع: " + orderStatus, Toast.LENGTH_LONG).show();
                            return;
                        }
                        updateDepositPaidUi(false, "بانتظار تأكيد الشبكة…",
                                "جاري الكشف التلقائي… (" + attempts[0] + ")");
                        if (attempts[0] >= 90) {
                            stopDepositStatusPolling();
                            updateDepositPaidUi(false, "ما زال معلّقاً",
                                    "سيُكتشف الدفع تلقائياً بعد تأكيد الشبكة");
                            return;
                        }
                        depositPollHandler.postDelayed(this, 3500L);
                    });
                });
            }
        };
        depositPollHandler.postDelayed(depositPollRunnable, 2000L);
    }

    private void stopDepositStatusPolling() {
        depositPolling.set(false);
        if (depositPollRunnable != null) {
            depositPollHandler.removeCallbacks(depositPollRunnable);
            depositPollRunnable = null;
        }
    }

    private void onDepositDetected(String paymentRef) {
        updateDepositPaidUi(true, "تم الدفع ✓", "تم الكشف عن الدفع بنجاح");
        Toast.makeText(this, "تم الدفع ✓", Toast.LENGTH_SHORT).show();
        if (etPaymentReference != null && paymentRef != null && !paymentRef.isEmpty()) {
            etPaymentReference.setText(paymentRef);
        }
        depositPollHandler.postDelayed(() -> {
            dismissDepositSheet();
            cardStepOrder.setVisibility(View.VISIBLE);
            Toast.makeText(this, "أكمل بيانات الطلب", Toast.LENGTH_SHORT).show();
            if (etContact != null) etContact.requestFocus();
        }, 1400L);
    }

    private void onMarkedPaid() {
        cardStepOrder.setVisibility(View.VISIBLE);
        Toast.makeText(this, "أكمل بيانات الطلب بعد الدفع", Toast.LENGTH_SHORT).show();
        etPaymentReference.requestFocus();
    }

    private void submitApplication() {
        long coins = parseLong(value(etRequestedCoins));
        String ref = value(etPaymentReference);
        String contact = value(etContact);
        if (coins <= 0 || ref.length() < 8 || contact.isEmpty()) {
            Toast.makeText(this, "أكمل المرجع ووسيلة التواصل والرصيد", Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("contact", contact);
        body.put("region", value(etRegion));
        body.put("reason", value(etReason));
        body.put("requestedCoins", coins);
        body.put("paymentNetwork", selectedNetwork);
        body.put("paymentReference", ref);
        if (depositOrderId != null && !depositOrderId.isEmpty()) {
            body.put("depositOrderId", depositOrderId);
        }

        btnSubmitApplication.setEnabled(false);
        setLoading(true);
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result =
                    ApiCall.execute(container.getWalletApi().applyRechargeAgent(body));
            runOnUiThread(() -> {
                btnSubmitApplication.setEnabled(true);
                setLoading(false);
                if (result.success) {
                    Toast.makeText(this, "تم إرسال الطلب للمراجعة", Toast.LENGTH_LONG).show();
                    loadStatus();
                } else {
                    Toast.makeText(this,
                            result.error != null ? result.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showStatusCard(String title, String body, boolean ok) {
        cardStatus.setVisibility(View.VISIBLE);
        cardStatus.removeAllViews();
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTextColor(ok ? getColor(R.color.aurora_gold) : getColor(R.color.aurora_coral));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14);
        b.setTextColor(0xFF1A1A1A);
        b.setPadding(0, dp(8), 0, 0);
        cardStatus.addView(t);
        cardStatus.addView(b);
    }

    private void showSellUi(Map<?, ?> agent) {
        cardSell.setVisibility(View.VISIBLE);
        cardSell.removeAllViews();

        TextView balLabel = label("رصيد الوكيل المتاح", 13, 0xFF595959);
        TextView bal = label(formatLong(agent.get("floatCoins")) + " عملة", 28,
                getColor(R.color.aurora_gold));
        bal.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView daily = label("مبيعات اليوم: " + formatLong(agent.get("dailySoldCoins"))
                        + " / " + formatLong(agent.get("dailyLimitCoins")),
                12, 0xFF595959);

        LinearLayout balCard = bubble();
        balCard.addView(balLabel);
        balCard.addView(bal);
        balCard.addView(daily);
        cardSell.addView(balCard);

        LinearLayout form = bubble();
        TextView formTitle = label("شحن مستخدم", 16, getColor(R.color.aurora_cyan));
        formTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        EditText recipient = field("ID أو اسم المستخدم", false);
        Button verify = goldButton("تحقق من المستخدم");
        TextView verified = label("يجب التحقق قبل التحويل", 13, 0xFF595959);
        EditText coins = field("عدد العملات", true);
        EditText retail = field("سعر البيع لكل 100 عملة (USDT)", false);
        retail.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        retail.setText(String.valueOf(decimal(pricing.get("suggestedRetailPer100CoinsUsdt"))));
        TextView total = label("السعر النهائي: 0 USDT", 15, getColor(R.color.aurora_gold));
        total.setTypeface(null, android.graphics.Typeface.BOLD);
        Button sell = goldButton("تأكيد شحن العملات");
        sell.setEnabled(false);

        form.addView(formTitle);
        form.addView(recipient);
        form.addView(verify);
        form.addView(verified);
        form.addView(coins);
        form.addView(retail);
        form.addView(total);
        form.addView(sell);
        cardSell.addView(form);

        Runnable update = () -> total.setText("السعر النهائي للعميل: "
                + MONEY.format((parseLong(value(coins)) / 100.0)
                * parseDouble(value(retail))) + " USDT");
        coins.addTextChangedListener(simpleWatcher(update));
        retail.addTextChangedListener(simpleWatcher(update));
        update.run();

        verify.setOnClickListener(v -> {
            verifiedRecipient = null;
            sell.setEnabled(false);
            verify.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().resolveRechargeRecipient(value(recipient)));
                runOnUiThread(() -> {
                    verify.setEnabled(true);
                    if (!result.success || result.data == null) {
                        verified.setText("لم يتم العثور على المستخدم");
                        verified.setTextColor(getColor(R.color.aurora_coral));
                        return;
                    }
                    recipient.setText(string(result.data.get("publicId")) != null
                            && !string(result.data.get("publicId")).isEmpty()
                            ? string(result.data.get("publicId"))
                            : string(result.data.get("username")));
                    verifiedRecipient = string(result.data.get("publicId"));
                    if (verifiedRecipient == null || verifiedRecipient.isEmpty()) {
                        verifiedRecipient = string(result.data.get("id"));
                    }
                    verified.setText("المستلم: " + string(result.data.get("displayName"))
                            + " · ID " + string(result.data.get("publicId")));
                    verified.setTextColor(getColor(R.color.aurora_mint));
                    sell.setEnabled(true);
                });
            });
        });

        recipient.addTextChangedListener(simpleWatcher(() -> {
            verifiedRecipient = null;
            sell.setEnabled(false);
            verified.setText("اضغط تحقق من المستخدم");
            verified.setTextColor(0xFF595959);
        }));

        sell.setOnClickListener(v -> {
            int amount = (int) parseLong(value(coins));
            if (verifiedRecipient == null || amount <= 0) {
                Toast.makeText(this, "تحقق من المستخدم وأدخل كمية صحيحة", Toast.LENGTH_SHORT).show();
                return;
            }
            AuraDialogHelper.confirm(this,
                    "تأكيد الشحن",
                    "سيتم تحويل " + amount + " عملة إلى "
                            + verified.getText() + "\n" + total.getText(),
                    "تأكيد",
                    () -> {
                        Map<String, Object> body = new HashMap<>();
                        body.put("recipientUsernameOrId", verifiedRecipient);
                        body.put("coins", amount);
                        body.put("idempotencyKey", UUID.randomUUID().toString());
                        sell.setEnabled(false);
                        container.getIoExecutor().execute(() -> {
                            Result<Map<String, Object>> result = ApiCall.execute(
                                    container.getWalletApi().sellAgentRecharge(body));
                            runOnUiThread(() -> {
                                sell.setEnabled(true);
                                Toast.makeText(this,
                                        result.success ? "تم شحن العملات بنجاح"
                                                : (result.error != null ? result.error
                                                : getString(R.string.error_generic)),
                                        Toast.LENGTH_LONG).show();
                                if (result.success) loadStatus();
                            });
                        });
                    },
                    "إلغاء",
                    null);
        });

        // —— User gifts lookup (so agent can credit account) ——
        LinearLayout giftCard = bubble();
        TextView giftTitle = label("هدايا المستخدم", 16, getColor(R.color.aurora_cyan));
        giftTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView giftHint = label("ابحث بالآي دي الرقمي لمعرفة هداياه ورصيده ثم اشحن حسابه", 12, 0xFF595959);
        EditText giftQuery = field("آي دي المستخدم / اسم المستخدم", false);
        Button giftLookup = goldButton("عرض الهدايا والرصيد");
        TextView giftResult = label("—", 13, 0xFF1A1A1A);
        giftCard.addView(giftTitle);
        giftCard.addView(giftHint);
        giftCard.addView(giftQuery);
        giftCard.addView(giftLookup);
        giftCard.addView(giftResult);
        cardSell.addView(giftCard);

        giftLookup.setOnClickListener(v -> {
            String q = value(giftQuery);
            if (q.isEmpty()) {
                Toast.makeText(this, "أدخل آي دي المستخدم", Toast.LENGTH_SHORT).show();
                return;
            }
            giftLookup.setEnabled(false);
            giftResult.setText("جاري التحميل…");
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().agentUserOverview(q));
                runOnUiThread(() -> {
                    giftLookup.setEnabled(true);
                    if (!result.success || result.data == null) {
                        giftResult.setText(result.error != null ? result.error : "تعذر التحميل");
                        giftResult.setTextColor(getColor(R.color.aurora_coral));
                        return;
                    }
                    Map<?, ?> user = mapOf(result.data.get("user"));
                    Map<?, ?> wallet = mapOf(result.data.get("wallet"));
                    Map<?, ?> gifts = mapOf(result.data.get("gifts"));
                    StringBuilder sb = new StringBuilder();
                    sb.append(string(user.get("displayName")))
                            .append(" · ID ").append(string(user.get("publicId"))).append('\n')
                            .append("عملات: ").append(formatLong(wallet.get("coins")))
                            .append(" · ألماس: ").append(formatLong(wallet.get("diamonds"))).append('\n')
                            .append("هدايا مستلمة: ").append(formatLong(gifts.get("receivedCount")))
                            .append(" · ألماس من الهدايا: ")
                            .append(formatLong(gifts.get("receivedDiamondsTotal"))).append('\n');
                    Object recentObj = gifts.get("recent");
                    if (recentObj instanceof java.util.List) {
                        java.util.List<?> recent = (java.util.List<?>) recentObj;
                        int n = Math.min(8, recent.size());
                        for (int i = 0; i < n; i++) {
                            Map<?, ?> g = mapOf(recent.get(i));
                            sb.append("• ").append(string(g.get("giftName")))
                                    .append(" من ").append(string(g.get("senderName")))
                                    .append(" (+").append(formatLong(g.get("diamondsAwarded")))
                                    .append("💎)\n");
                        }
                    }
                    giftResult.setText(sb.toString().trim());
                    giftResult.setTextColor(getColor(R.color.aurora_mint));
                    // Prefill sell recipient with publicId for quick credit
                    String pid = string(user.get("publicId"));
                    if (!pid.isEmpty()) {
                        recipient.setText(pid);
                        verifiedRecipient = null;
                        sell.setEnabled(false);
                        verified.setText("اضغط تحقق بعد تعبئة الآي دي");
                    }
                });
            });
        });

        // —— Sell history ——
        LinearLayout histCard = bubble();
        TextView histTitle = label("سجل عمليات الشحن", 16, getColor(R.color.aurora_cyan));
        histTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        Button histRefresh = goldButton("تحديث السجل");
        LinearLayout histList = new LinearLayout(this);
        histList.setOrientation(LinearLayout.VERTICAL);
        histCard.addView(histTitle);
        histCard.addView(histRefresh);
        histCard.addView(histList);
        cardSell.addView(histCard);

        final Runnable[] loadHistoryHolder = new Runnable[1];
        loadHistoryHolder[0] = () -> {
            histRefresh.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().agentRecharges());
                runOnUiThread(() -> {
                    histRefresh.setEnabled(true);
                    histList.removeAllViews();
                    if (!result.success || result.data == null) {
                        histList.addView(label(result.error != null ? result.error : "تعذر التحميل",
                                13, getColor(R.color.aurora_coral)));
                        return;
                    }
                    Object itemsObj = result.data.get("items");
                    if (!(itemsObj instanceof java.util.List) || ((java.util.List<?>) itemsObj).isEmpty()) {
                        histList.addView(label("لا توجد عمليات شحن بعد", 13, 0xFF595959));
                        return;
                    }
                    for (Object row : (java.util.List<?>) itemsObj) {
                        if (!(row instanceof Map)) continue;
                        Map<?, ?> item = (Map<?, ?>) row;
                        Map<?, ?> recipientMap = item.get("recipient") instanceof Map
                                ? (Map<?, ?>) item.get("recipient") : null;
                        String name = recipientMap != null
                                ? string(recipientMap.get("displayName")) : "مستخدم";
                        String line = formatLong(item.get("coins")) + " عملة → " + name
                                + " · " + string(item.get("createdAt"));
                        histList.addView(label(line, 12, 0xFF1A1A1A));
                    }
                });
            });
        };
        histRefresh.setOnClickListener(v -> loadHistoryHolder[0].run());
        loadHistoryHolder[0].run();

        // —— Agent withdraw inbox ——
        LinearLayout wdCard = bubble();
        TextView wdTitle = label("طلبات السحب عبر وكيلك", 16, getColor(R.color.aurora_gold));
        wdTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        Button wdRefresh = goldButton("تحديث الطلبات");
        LinearLayout wdList = new LinearLayout(this);
        wdList.setOrientation(LinearLayout.VERTICAL);
        wdCard.addView(wdTitle);
        wdCard.addView(wdRefresh);
        wdCard.addView(wdList);
        cardSell.addView(wdCard);

        final Runnable[] loadWithdrawsHolder = new Runnable[1];
        loadWithdrawsHolder[0] = () -> {
            wdRefresh.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().agentWithdraws());
                runOnUiThread(() -> {
                    wdRefresh.setEnabled(true);
                    wdList.removeAllViews();
                    if (!result.success || result.data == null) {
                        wdList.addView(label(result.error != null ? result.error : "تعذر التحميل",
                                13, getColor(R.color.aurora_coral)));
                        return;
                    }
                    Object itemsObj = result.data.get("items");
                    if (!(itemsObj instanceof java.util.List) || ((java.util.List<?>) itemsObj).isEmpty()) {
                        wdList.addView(label("لا توجد طلبات سحب حالياً", 13, 0xFF595959));
                        return;
                    }
                    for (Object rowObj : (java.util.List<?>) itemsObj) {
                        Map<?, ?> row = mapOf(rowObj);
                        Map<?, ?> u = mapOf(row.get("user"));
                        String status = string(row.get("status"));
                        String line = string(u.get("displayName"))
                                + " · ID " + string(u.get("publicId"))
                                + "\n" + formatLong(row.get("diamonds")) + " ألماس · "
                                + status;
                        TextView tv = label(line, 13, 0xEEFFFFFF);
                        wdList.addView(tv);
                        if ("pending".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
                            LinearLayout actions = new LinearLayout(this);
                            actions.setOrientation(LinearLayout.HORIZONTAL);
                            Button ok = goldButton("تم الدفع");
                            Button no = outlinedButton("رفض");
                            String wid = string(row.get("id"));
                            ok.setOnClickListener(v2 -> agentReviewWithdraw(wid, true, loadWithdrawsHolder[0]));
                            no.setOnClickListener(v2 -> agentReviewWithdraw(wid, false, loadWithdrawsHolder[0]));
                            actions.addView(ok);
                            actions.addView(no);
                            wdList.addView(actions);
                        }
                        View spacer = new View(this);
                        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
                        wdList.addView(spacer);
                    }
                });
            });
        };
        wdRefresh.setOnClickListener(v -> loadWithdrawsHolder[0].run());
        loadWithdrawsHolder[0].run();
    }

    private void agentReviewWithdraw(String id, boolean complete, Runnable onDone) {
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(complete
                    ? container.getWalletApi().agentCompleteWithdraw(id, new HashMap<>())
                    : container.getWalletApi().agentRejectWithdraw(id, new HashMap<>()));
            runOnUiThread(() -> {
                Toast.makeText(this,
                        r.success ? (complete ? "تم تأكيد الدفع" : "تم الرفض وإرجاع الألماس")
                                : (r.error != null ? r.error : getString(R.string.error_generic)),
                        Toast.LENGTH_LONG).show();
                if (r.success && onDone != null) onDone.run();
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> mapOf(Object o) {
        if (o instanceof Map) return (Map<?, ?>) o;
        return new HashMap<>();
    }

    private Button outlinedButton(String text) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout bubble() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_list_card);
        int p = dp(16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView label(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private EditText field(String hint, boolean numeric) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x88FFFFFF);
        e.setTextColor(0xFF1A1A1A);
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.bg_list_card);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }

    private Button goldButton(String title) {
        MaterialButton b = new MaterialButton(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.aurora_gold)));
        b.setTextColor(getColor(R.color.aurora_night));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void startHourglass(TextView tv) {
        stopHourglass();
        if (tv == null) return;
        hourglassAnimator = ObjectAnimator.ofFloat(tv, View.ROTATION, 0f, 180f);
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
    }

    private void dismissDepositSheet() {
        stopHourglass();
        stopDepositStatusPolling();
        if (depositSheet != null) {
            try { depositSheet.dismiss(); } catch (Exception ignored) {}
            depositSheet = null;
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void copy(String label, String value) {
        if (value == null || value.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        stopDepositStatusPolling();
        dismissDepositSheet();
        super.onDestroy();
    }

    private TextWatcher simpleWatcher(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                action.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static Map<String, Object> mutableMap(Map<?, ?> source) {
        Map<String, Object> out = new HashMap<>();
        if (source != null) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    private static String value(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static String string(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return 0; }
    }

    private static double decimal(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return parseDouble(String.valueOf(value));
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return 0; }
    }

    private static String money(Object value) {
        return MONEY.format(decimal(value));
    }

    private static String formatLong(Object value) {
        return String.format(Locale.US, "%,d", number(value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
