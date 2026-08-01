package com.Dramizo.Series.presentation.wallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.QrBitmap;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sham Cash manual pay: show account QR + name, then WhatsApp proof (admin confirms order).
 */
public class ShamCashCheckoutActivity extends ThemedActivity {

    public static Intent intent(Context ctx, WalletDtos.RechargePackageDto pkg) {
        Intent i = new Intent(ctx, ShamCashCheckoutActivity.class);
        i.putExtra(RechargePaymentActivity.EXTRA_SKU, pkg.sku);
        i.putExtra(RechargePaymentActivity.EXTRA_LABEL, pkg.label);
        i.putExtra(RechargePaymentActivity.EXTRA_COINS, pkg.coins);
        i.putExtra(RechargePaymentActivity.EXTRA_BONUS, pkg.bonusCoins);
        i.putExtra(RechargePaymentActivity.EXTRA_PRICE, pkg.priceUsd);
        return i;
    }

    private AppContainer c;
    private WalletDtos.RechargePackageDto pkg;
    private FrameLayout loadingOverlay;
    private ImageView imgQr;
    private TextView tvAccountName;
    private TextView tvAccountId;
    private TextView tvClaimCode;
    private TextView tvInstructions;
    private TextView tvPackageSummary;

    @Nullable private WalletDtos.ShamCashOrderResult orderResult;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sham_cash_checkout);
        EdgeToEdgeHelper.apply(this);
        c = ContainerProvider.from(this);

        pkg = new WalletDtos.RechargePackageDto();
        pkg.sku = getIntent().getStringExtra(RechargePaymentActivity.EXTRA_SKU);
        pkg.label = getIntent().getStringExtra(RechargePaymentActivity.EXTRA_LABEL);
        pkg.coins = getIntent().getIntExtra(RechargePaymentActivity.EXTRA_COINS, 0);
        pkg.bonusCoins = getIntent().getIntExtra(RechargePaymentActivity.EXTRA_BONUS, 0);
        pkg.priceUsd = getIntent().getDoubleExtra(RechargePaymentActivity.EXTRA_PRICE, 0);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        imgQr = findViewById(R.id.imgQr);
        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountId = findViewById(R.id.tvAccountId);
        tvClaimCode = findViewById(R.id.tvClaimCode);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvPackageSummary = findViewById(R.id.tvPackageSummary);

        int total = pkg.coins + Math.max(0, pkg.bonusCoins);
        String label = pkg.label != null && !pkg.label.isEmpty() ? pkg.label : getString(R.string.app_name);
        tvPackageSummary.setText(getString(R.string.pay_package_summary, label, total, pkg.usdPriceLabel()));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopyAccount).setOnClickListener(v -> copyAccount());
        findViewById(R.id.btnWhatsapp).setOnClickListener(v -> openWhatsAppProof());

        createOrder();
    }

    private void createOrder() {
        showLoading(true);
        c.getIoExecutor().execute(() -> {
            Map<String, Object> body = new HashMap<>();
            body.put("sku", pkg.sku);
            Result<WalletDtos.ShamCashOrderResult> r =
                    ApiCall.execute(c.getWalletApi().createShamCashOrder(body));
            runOnUiThread(() -> {
                showLoading(false);
                if (isFinishing()) return;
                if (!r.success || r.data == null || r.data.payment == null) {
                    Toast.makeText(this,
                            r.error != null && !r.error.isEmpty()
                                    ? r.error
                                    : getString(R.string.pay_sham_cash_coming_soon),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                orderResult = r.data;
                bindPayment(r.data);
            });
        });
    }

    private void bindPayment(WalletDtos.ShamCashOrderResult data) {
        WalletDtos.ShamCashPaymentInfo p = data.payment;
        String accountId = p.accountId != null ? p.accountId.trim() : "";
        String name = p.accountName != null && !p.accountName.isEmpty()
                ? p.accountName
                : (p.displayName != null ? p.displayName : getString(R.string.pay_sham_cash_title));
        // If order payload omitted accountId, fall back to public config.
        if (accountId.length() < 8) {
            fillAccountFromConfig(name, p);
            return;
        }
        applyAccountUi(name, accountId, p);
    }

    private void fillAccountFromConfig(String fallbackName, WalletDtos.ShamCashPaymentInfo p) {
        c.getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.ShamCashConfigDto> r =
                    ApiCall.execute(c.getConfigApi().shamCash());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String accountId = "";
                String name = fallbackName;
                if (r.success && r.data != null) {
                    if (r.data.accountId != null) accountId = r.data.accountId.trim();
                    if (r.data.accountName != null && !r.data.accountName.isEmpty()) {
                        name = r.data.accountName.trim();
                    } else if (r.data.displayName != null && !r.data.displayName.isEmpty()) {
                        name = r.data.displayName.trim();
                    }
                    if ((p.whatsapp == null || p.whatsapp.trim().isEmpty())
                            && r.data.whatsapp != null) {
                        p.whatsapp = r.data.whatsapp;
                    }
                    if ((p.instructions == null || p.instructions.isEmpty())
                            && r.data.instructions != null) {
                        p.instructions = r.data.instructions;
                    }
                }
                p.accountId = accountId;
                p.accountName = name;
                p.qrPayload = accountId;
                applyAccountUi(name, accountId, p);
            });
        });
    }

    private void applyAccountUi(
            String name,
            String accountId,
            WalletDtos.ShamCashPaymentInfo p) {
        tvAccountName.setText(name != null && !name.isEmpty()
                ? name
                : getString(R.string.pay_sham_cash_title));
        tvAccountId.setText(accountId != null ? accountId : "");
        tvAccountId.setVisibility(
                accountId != null && !accountId.isEmpty() ? View.VISIBLE : View.GONE);
        View idLabel = findViewById(R.id.tvAccountIdLabel);
        if (idLabel != null) {
            idLabel.setVisibility(
                    accountId != null && !accountId.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (p.instructions != null && !p.instructions.isEmpty()) {
            tvInstructions.setText(p.instructions);
        }
        if (orderResult != null && orderResult.order != null && orderResult.order.claimCode != null) {
            tvClaimCode.setText(getString(R.string.pay_sham_cash_order_code, orderResult.order.claimCode));
        }
        String qr = p.qrPayload != null && !p.qrPayload.isEmpty() ? p.qrPayload : accountId;
        Bitmap bmp = QrBitmap.encode(qr, 512);
        if (bmp != null) imgQr.setImageBitmap(bmp);
    }

    private void copyAccount() {
        String id = tvAccountId.getText() != null ? tvAccountId.getText().toString() : "";
        if (id.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("sham_cash_account", id));
            Toast.makeText(this, R.string.pay_sham_cash_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsAppProof() {
        WalletDtos.ShamCashOrderResult data = orderResult;
        if (data == null || data.payment == null) {
            Toast.makeText(this, R.string.pay_sham_cash_coming_soon, Toast.LENGTH_SHORT).show();
            return;
        }
        String wa = data.payment.whatsapp;
        String digits = wa == null ? "" : wa.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            Toast.makeText(this, R.string.pay_sham_cash_coming_soon, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encoded = URLEncoder.encode(buildMessage(data), StandardCharsets.UTF_8.name());
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + digits + "?text=" + encoded)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.pay_sham_cash_whatsapp_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private String buildMessage(WalletDtos.ShamCashOrderResult data) {
        SessionManager session = c.getSessionManager();
        String displayName = session.getDisplayName();
        String publicId = null;
        AuthDtos.UserDto user = session.getUser();
        if (user != null) {
            publicId = user.displayPublicId();
            if ((displayName == null || displayName.isEmpty()) && user.displayName != null) {
                displayName = user.displayName;
            }
        }
        if (publicId == null || publicId.isEmpty()) publicId = session.getUserId();

        int total = pkg.coins + Math.max(0, pkg.bonusCoins);
        String pkgName = pkg.label != null && !pkg.label.isEmpty()
                ? pkg.label
                : (pkg.sku != null ? pkg.sku : "باقة");
        String claim = data.order != null && data.order.claimCode != null
                ? data.order.claimCode
                : "—";
        String accountId = data.payment.accountId != null ? data.payment.accountId : "—";

        StringBuilder sb = new StringBuilder();
        sb.append("مرحباً، أريد شراء كوينز من تطبيق JEHO CHAT").append('\n');
        sb.append("طريقة الدفع: شام كاش").append('\n').append('\n');
        sb.append("📦 الباقة: ").append(pkgName).append('\n');
        sb.append("🪙 الكوينز: ").append(pkg.coins);
        if (pkg.bonusCoins > 0) sb.append(" + هدية ").append(pkg.bonusCoins);
        sb.append('\n');
        sb.append("✅ الإجمالي المطلوب شحنه: ").append(total).append(" كوينز").append('\n');
        sb.append("💵 السعر: $").append(String.format(Locale.US, "%.2f", pkg.priceUsd)).append('\n');
        if (pkg.sku != null && !pkg.sku.isEmpty()) {
            sb.append("🔖 SKU: ").append(pkg.sku).append('\n');
        }
        sb.append("🧾 رقم الطلب: ").append(claim).append('\n');
        sb.append("🏦 حساب شام كاش: ").append(accountId).append('\n');
        sb.append('\n');
        sb.append("👤 اسمي: ").append(displayName != null ? displayName : "—").append('\n');
        sb.append("🆔 رقم المستخدم: ").append(publicId != null ? publicId : "—").append('\n');
        sb.append('\n');
        sb.append("حوّلت عبر شام كاش — أرسل صورة الإثبات هنا. شكراً لك 🙏");
        return sb.toString();
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
