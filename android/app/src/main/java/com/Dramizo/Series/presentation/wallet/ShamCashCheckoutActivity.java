package com.Dramizo.Series.presentation.wallet;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.QrBitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class ShamCashCheckoutActivity extends ThemedActivity {
    private static final int REQUEST_RECEIPT = 4101;
    private static final int REQUEST_SAVE_QR = 4102;
    private static final int MAX_RECEIPT_BYTES = 15 * 1024 * 1024;

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
    private TextView tvReceiptName;
    private TextView tvTransferDate;
    private EditText etSenderName;
    private EditText etAmountSent;
    private EditText etTransferReference;
    private EditText etTransferNote;
    private View btnSubmitReceipt;
    private Uri selectedReceiptUri;
    private String selectedReceiptMime;
    private String selectedReceiptName;
    private String selectedTransferDate = "";
    private Bitmap generatedQrBitmap;
    private Bitmap pendingQrSaveBitmap;
    private boolean submitting;

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
        tvReceiptName = findViewById(R.id.tvReceiptName);
        tvTransferDate = findViewById(R.id.tvTransferDate);
        etSenderName = findViewById(R.id.etSenderName);
        etAmountSent = findViewById(R.id.etAmountSent);
        etTransferReference = findViewById(R.id.etTransferReference);
        etTransferNote = findViewById(R.id.etTransferNote);
        btnSubmitReceipt = findViewById(R.id.btnSubmitReceipt);

        int total = pkg.coins + Math.max(0, pkg.bonusCoins);
        String label = pkg.label != null && !pkg.label.isEmpty() ? pkg.label : getString(R.string.app_name);
        tvPackageSummary.setText(getString(R.string.pay_package_summary, label, total, pkg.usdPriceLabel()));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopyAccount).setOnClickListener(v -> copyAccount());
        findViewById(R.id.btnSaveQr).setOnClickListener(v -> saveQrImage());
        imgQr.setOnClickListener(v -> showQrPreview());
        findViewById(R.id.btnSelectReceipt).setOnClickListener(v -> chooseReceipt());
        findViewById(R.id.btnTransferDate).setOnClickListener(v -> chooseTransferDate());
        tvTransferDate.setOnClickListener(v -> chooseTransferDate());
        btnSubmitReceipt.setOnClickListener(v -> submitReceipt());
        findViewById(R.id.btnCancelOrder).setOnClickListener(v -> cancelOrder());

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
                if (!r.success || r.data == null || r.data.payment == null || r.data.order == null) {
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
                    if (r.data.qrImageUrl != null && !r.data.qrImageUrl.isEmpty()) {
                        p.qrImageUrl = r.data.qrImageUrl;
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

    private void applyAccountUi(String name, String accountId, WalletDtos.ShamCashPaymentInfo p) {
        tvAccountName.setText(name != null && !name.isEmpty()
                ? name : getString(R.string.pay_sham_cash_title));
        tvAccountId.setText(accountId != null ? accountId : "");
        tvAccountId.setVisibility(accountId != null && !accountId.isEmpty() ? View.VISIBLE : View.GONE);
        View idLabel = findViewById(R.id.tvAccountIdLabel);
        if (idLabel != null) idLabel.setVisibility(accountId != null && !accountId.isEmpty() ? View.VISIBLE : View.GONE);
        if (p.instructions != null && !p.instructions.isEmpty()) tvInstructions.setText(p.instructions);
        if (orderResult != null && orderResult.order != null && orderResult.order.claimCode != null) {
            tvClaimCode.setText(getString(R.string.pay_sham_cash_order_code, orderResult.order.claimCode));
        }

        String qr = p.qrPayload != null && !p.qrPayload.isEmpty() ? p.qrPayload : accountId;
        generatedQrBitmap = QrBitmap.encode(qr, 512);
        if (generatedQrBitmap != null) imgQr.setImageBitmap(generatedQrBitmap);
        if (p.qrImageUrl != null && !p.qrImageUrl.trim().isEmpty()) {
            String imageUrl = resolveMediaUrl(p.qrImageUrl);
            Glide.with(this).load(imageUrl).placeholder(imgQr.getDrawable()).error(imgQr.getDrawable()).into(imgQr);
        }
    }

    private String resolveMediaUrl(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("https://") || raw.startsWith("http://")) return raw;
        String base = BuildConfig.API_BASE_URL == null ? "" : BuildConfig.API_BASE_URL.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/api")) base = base.substring(0, base.length() - 4);
        if (base.isEmpty()) return raw;
        return base + (raw.startsWith("/") ? raw : "/" + raw);
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

    private void showQrPreview() {
        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageDrawable(imgQr.getDrawable());
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        preview.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("باركود شام كاش")
                .setView(preview)
                .setPositiveButton("تحميل الصورة", (dialog, which) -> saveQrImage())
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void saveQrImage() {
        Bitmap bitmap = null;
        if (imgQr.getDrawable() instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) imgQr.getDrawable()).getBitmap();
        }
        if (bitmap == null) bitmap = generatedQrBitmap;
        if (bitmap == null) {
            Toast.makeText(this, "صورة الباركود غير جاهزة بعد", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingQrSaveBitmap = bitmap;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "ShamCash-QR.png");
        startActivityForResult(intent, REQUEST_SAVE_QR);
    }

    private void chooseReceipt() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "application/pdf"});
        startActivityForResult(intent, REQUEST_RECEIPT);
    }

    private void chooseTransferDate() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedTransferDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            tvTransferDate.setText(selectedTransferDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void submitReceipt() {
        if (submitting) return;
        String sender = etSenderName.getText() == null ? "" : etSenderName.getText().toString().trim();
        String amountText = etAmountSent.getText() == null ? "" : etAmountSent.getText().toString().trim();
        if (sender.isEmpty()) {
            etSenderName.setError("اسم المرسل كما يظهر في الحوالة مطلوب");
            etSenderName.requestFocus();
            return;
        }
        if (amountText.isEmpty()) {
            etAmountSent.setError("المبلغ المرسل مطلوب");
            etAmountSent.requestFocus();
            return;
        }
        if (selectedTransferDate.isEmpty()) {
            Toast.makeText(this, "اختر تاريخ التحويل", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedReceiptUri == null) {
            Toast.makeText(this, "ارفع صورة الإيصال أو ملف PDF أولاً", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountText.replace(',', '.'));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etAmountSent.setError("أدخل مبلغاً صحيحاً");
            return;
        }
        if (orderResult == null || orderResult.order == null || orderResult.order.id == null) return;

        submitting = true;
        btnSubmitReceipt.setEnabled(false);
        showLoading(true);
        final double sentAmount = amount;
        final Uri receiptUri = selectedReceiptUri;
        final String receiptMime = selectedReceiptMime != null ? selectedReceiptMime : "application/octet-stream";
        final String receiptName = selectedReceiptName != null ? selectedReceiptName : "receipt";
        final String transferDate = selectedTransferDate;
        final String transferReference = etTransferReference.getText() == null ? "" : etTransferReference.getText().toString().trim();
        final String transferNote = etTransferNote.getText() == null ? "" : etTransferNote.getText().toString().trim();
        final String orderId = orderResult.order.id;
        c.getIoExecutor().execute(() -> {
            try {
                byte[] fileBytes;
                try (InputStream input = getContentResolver().openInputStream(receiptUri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalStateException("تعذر قراءة ملف الإيصال");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        if (output.size() > MAX_RECEIPT_BYTES) {
                            throw new IllegalArgumentException("حجم الإيصال يجب ألا يتجاوز 15 ميغابايت");
                        }
                    }
                    fileBytes = output.toByteArray();
                }
                RequestBody requestBody = RequestBody.create(MediaType.parse(receiptMime), fileBytes);
                MultipartBody.Part part = MultipartBody.Part.createFormData("file", receiptName, requestBody);
                Result<UploadApi.UploadResult> upload = ApiCall.execute(c.getUploadApi().upload(part));
                if (!upload.success || upload.data == null || upload.data.url == null || upload.data.url.isEmpty()) {
                    throw new IllegalStateException(upload.error != null && !upload.error.isEmpty()
                            ? upload.error : "تعذر رفع ملف الإيصال");
                }

                Map<String, Object> body = new HashMap<>();
                body.put("senderName", sender);
                body.put("amountSent", sentAmount);
                body.put("transferDate", transferDate);
                body.put("transferReference", transferReference);
                body.put("note", transferNote);
                body.put("receiptUrl", upload.data.url);
                body.put("receiptMimeType", receiptMime);
                body.put("receiptFilename", upload.data.filename != null ? upload.data.filename : receiptName);
                Result<Map<String, Object>> submit = ApiCall.execute(
                        c.getWalletApi().submitShamCashReceipt(orderId, body));
                if (!submit.success) {
                    throw new IllegalStateException(submit.error != null && !submit.error.isEmpty()
                            ? submit.error : "تعذر إرسال الإيصال للمراجعة");
                }
                runOnUiThread(() -> {
                    showLoading(false);
                    submitting = false;
                    new AlertDialog.Builder(this)
                            .setTitle("تم إرسال إيصالك للمراجعة")
                            .setMessage("تم استلام إيصال التحويل بنجاح. عند مراجعة الدفع والتأكد منه، سيتم تفعيل اشتراكك أو شحن حسابك بالباقة التي اخترتها.")
                            .setCancelable(false)
                            .setPositiveButton("موافق", (dialog, which) -> finish())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    submitting = false;
                    btnSubmitReceipt.setEnabled(true);
                    Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "تعذر إرسال الإيصال", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void cancelOrder() {
        if (orderResult == null || orderResult.order == null || orderResult.order.id == null) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إلغاء طلب التحويل")
                .setMessage("هل تريد إلغاء طلب شام كاش الحالي؟")
                .setNegativeButton("رجوع", null)
                .setPositiveButton("إلغاء الطلب", (dialog, which) -> {
                    showLoading(true);
                    c.getIoExecutor().execute(() -> {
                        Result<Map<String, Object>> result = ApiCall.execute(
                                c.getWalletApi().cancelShamCashOrder(orderResult.order.id));
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (result.success) {
                                Toast.makeText(this, "تم إلغاء الطلب", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, result.error != null ? result.error : "تعذر إلغاء الطلب", Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .show();
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_RECEIPT) {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "";
            if (!"image/jpeg".equalsIgnoreCase(mime)
                    && !"image/png".equalsIgnoreCase(mime)
                    && !"application/pdf".equalsIgnoreCase(mime)) {
                Toast.makeText(this, "الصيغ المدعومة JPG وPNG وPDF فقط", Toast.LENGTH_LONG).show();
                return;
            }
            selectedReceiptUri = uri;
            selectedReceiptMime = mime.toLowerCase(Locale.US);
            selectedReceiptName = displayName(uri);
            tvReceiptName.setText(selectedReceiptName != null ? selectedReceiptName : "تم اختيار الإيصال");
        } else if (requestCode == REQUEST_SAVE_QR && pendingQrSaveBitmap != null) {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("تعذر حفظ الصورة");
                pendingQrSaveBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                Toast.makeText(this, "تم حفظ صورة الباركود", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر حفظ صورة الباركود", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }
}
