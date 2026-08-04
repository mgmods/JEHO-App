package com.Dramizo.Series.presentation.wallet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;

import java.util.Locale;

/**
 * In-app card checkout: app chrome + loading → secure payment WebView.
 * After Fourthwall shows the post-pay "order" page we confirm crediting and
 * return to {@link BagActivity} with a clear recharge-success result.
 */
public class CardCheckoutActivity extends ThemedActivity {

    public static final String EXTRA_RECHARGE_SUCCESS = "extra_recharge_success";
    public static final String EXTRA_RECHARGE_COINS = "extra_recharge_coins";

    private AppContainer c;
    private WalletDtos.RechargePackageDto pkg;
    private WebView webView;
    private FrameLayout loadingOverlay;
    private TextView tvLoading;
    private TextView tvLoadingHint;
    private ProgressBar progressTop;
    private String orderId;
    /** True after the first checkout page is shown — later navigations use thin progress only. */
    private boolean firstPageShown;
    private boolean finishedSuccess;
    /** User reached Fourthwall thank-you / order page after paying. */
    private boolean looksPaidPage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private Runnable loadTimeoutRunnable;
    private int pollAttempts;

    public static Intent intent(Context ctx, WalletDtos.RechargePackageDto pkg) {
        return RechargePaymentActivity.intent(ctx, pkg)
                .setClass(ctx, CardCheckoutActivity.class);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_checkout);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.contentRoot));
        c = ContainerProvider.from(this);
        pkg = RechargePaymentActivity.packageFromIntent(getIntent());
        if (pkg == null || pkg.sku == null || pkg.sku.isEmpty()) {
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        webView = findViewById(R.id.webView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoading = findViewById(R.id.tvLoading);
        tvLoadingHint = findViewById(R.id.tvLoadingHint);
        progressTop = findViewById(R.id.progressTop);

        TextView summary = findViewById(R.id.tvPackageSummary);
        String label = pkg.label != null && !pkg.label.isEmpty()
                ? pkg.label
                : getString(R.string.app_name);
        int totalCoins = pkg.coins + Math.max(0, pkg.bonusCoins);
        summary.setText(getString(R.string.pay_package_summary,
                label, totalCoins, pkg.usdPriceLabel()));

        configureWebView();
        startCheckout();
    }

    private void configureWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressTop == null) return;
                if (newProgress >= 100) {
                    progressTop.setVisibility(View.GONE);
                } else {
                    progressTop.setVisibility(View.VISIBLE);
                    progressTop.setProgress(newProgress);
                }
                // First paint ready — drop full-screen overlay so user can pay.
                if (newProgress >= 55 && !firstPageShown) {
                    revealFirstPage();
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    maybeHandleCheckoutUrl(request.getUrl().toString());
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // After first reveal: NEVER cover the WebView again (stuck "opening…" bug),
                // unless we already know payment finished and we are confirming credit.
                if (!firstPageShown) {
                    showLoading(getString(R.string.card_checkout_opening));
                    if (tvLoadingHint != null) {
                        tvLoadingHint.setText(R.string.card_checkout_opening_hint);
                    }
                    armLoadTimeout();
                } else if (looksPaidPage) {
                    showConfirmingUi();
                } else if (progressTop != null) {
                    progressTop.setVisibility(View.VISIBLE);
                    progressTop.setProgress(5);
                }
                maybeHandleCheckoutUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!looksPaidPage) {
                    revealFirstPage();
                }
                maybeHandleCheckoutUrl(url);
                injectAccountEmail(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request != null && request.isForMainFrame() && !firstPageShown) {
                    failOpen(getString(R.string.card_checkout_load_failed));
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                if (!firstPageShown) {
                    failOpen(getString(R.string.card_checkout_load_failed));
                }
            }
        });
    }

    private void startCheckout() {
        showLoading(getString(R.string.card_checkout_preparing));
        if (tvLoadingHint != null) {
            tvLoadingHint.setText(R.string.card_checkout_preparing_hint);
        }
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.FourthwallCheckoutResult> r = ApiCall.execute(
                    c.getWalletApi().createFourthwallCheckout(
                            new WalletDtos.FourthwallCheckoutRequest(pkg.sku)));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!r.success || r.data == null || r.data.checkoutUrl == null
                        || r.data.checkoutUrl.isEmpty()) {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                orderId = r.data.order != null ? r.data.order.id : null;
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(r.data.checkoutUrl);
                if (orderId != null && !orderId.isEmpty()) {
                    startPolling();
                }
            });
        });
    }

    private void revealFirstPage() {
        if (isFinishing() || looksPaidPage) return;
        firstPageShown = true;
        cancelLoadTimeout();
        hideLoading();
        if (progressTop != null) progressTop.setVisibility(View.GONE);
    }

    /** Prefill Fourthwall Contact email with the signed-in Google/app account email. */
    private void injectAccountEmail(@Nullable WebView view) {
        if (view == null) return;
        String email = null;
        try {
            com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto u =
                    c.getSessionManager().getUser();
            if (u != null && u.email != null && u.email.contains("@")) {
                email = u.email.trim();
            }
        } catch (Exception ignored) {
        }
        if (email == null || email.isEmpty()) return;
        String safe = email
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "")
                .replace("\r", "");
        String js =
                "(function(){try{"
                        + "var email='" + safe + "';"
                        + "var nodes=document.querySelectorAll("
                        + "'input[type=email],input[name*=email i],input[id*=email i],"
                        + "input[autocomplete=email],input[placeholder*=mail i],"
                        + "input[placeholder*=Email i],input[placeholder*=بريد]');"
                        + "for(var i=0;i<nodes.length;i++){"
                        + "var el=nodes[i]; if(!el||el.disabled) continue;"
                        + "var cur=(el.value||'').trim();"
                        + "if(cur && cur.indexOf('@')>=0) continue;"
                        + "el.focus(); el.value=email;"
                        + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                        + "}"
                        + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Thank-you / order-complete pages → confirm credit and leave checkout quickly.
     * Fourthwall does not redirect back to the app; it stays on their order page.
     */
    private void maybeHandleCheckoutUrl(@Nullable String url) {
        if (url == null || url.isEmpty() || finishedSuccess) return;
        String u = url.toLowerCase(Locale.US);

        // Still mid-checkout — do not treat as paid.
        if (u.contains("/cart/checkout")
                || u.contains("/checkout?")
                || (u.contains("/checkout/")
                && !u.contains("thank")
                && !u.contains("complete")
                && !u.contains("success")
                && !u.contains("order"))) {
            // Pure /checkout/{id} is the payment form; keep paying.
            if (!(u.contains("thank") || u.contains("confirmation")
                    || u.contains("/order") || u.contains("purchase"))) {
                return;
            }
        }

        boolean looksPaid = u.contains("thank")
                || u.contains("thankyou")
                || u.contains("thank-you")
                || u.contains("success")
                || u.contains("order-confirmation")
                || u.contains("order_confirmation")
                || u.contains("/confirmation")
                || u.contains("checkout/complete")
                || u.contains("payment-complete")
                || u.contains("purchase-complete")
                || u.contains("paid")
                || u.contains("order-status")
                || u.contains("orderstatus")
                || u.contains("/order/")
                || u.contains("/orders/")
                || (u.contains("order") && (u.contains("complete")
                || u.contains("confirm")
                || u.contains("status")
                || u.contains("summary")
                || u.contains("receipt")))
                || (u.contains("checkout") && u.contains("complete"));

        if (!looksPaid) return;

        looksPaidPage = true;
        firstPageShown = true;
        showConfirmingUi();
        // Kick an immediate status check (backend also reconciles pending vs FW Open API).
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            handler.post(pollRunnable);
        }
    }

    private void showConfirmingUi() {
        cancelLoadTimeout();
        showLoading(getString(R.string.card_checkout_confirming));
        if (tvLoadingHint != null) {
            tvLoadingHint.setText(R.string.card_checkout_confirming_hint);
        }
        if (progressTop != null) progressTop.setVisibility(View.GONE);
    }

    private void armLoadTimeout() {
        cancelLoadTimeout();
        loadTimeoutRunnable = () -> {
            if (isFinishing() || firstPageShown || looksPaidPage) return;
            // Don't close — just uncover whatever loaded so user can continue.
            revealFirstPage();
        };
        handler.postDelayed(loadTimeoutRunnable, 25000);
    }

    private void cancelLoadTimeout() {
        if (loadTimeoutRunnable != null) {
            handler.removeCallbacks(loadTimeoutRunnable);
            loadTimeoutRunnable = null;
        }
    }

    private void failOpen(String msg) {
        cancelLoadTimeout();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (!isFinishing()) finish();
    }

    private void startPolling() {
        pollAttempts = 0;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || orderId == null || finishedSuccess) return;
                pollAttempts++;
                final String oid = orderId;
                c.getIoExecutor().execute(() -> {
                    Result<WalletDtos.FourthwallOrderStatus> st = ApiCall.execute(
                            c.getWalletApi().fourthwallOrderStatus(oid));
                    runOnUiThread(() -> {
                        if (isFinishing() || finishedSuccess) return;
                        if (st.success && st.data != null && isPaidStatus(st.data.status)) {
                            onPaymentCompleted(st.data, true);
                            return;
                        }
                        // After user lands on order page: recon for longer / faster.
                        int maxAttempts = looksPaidPage ? 90 : 60;
                        long delay;
                        if (looksPaidPage) {
                            delay = pollAttempts < 20 ? 1500L : 2500L;
                        } else {
                            delay = pollAttempts < 6 ? 2500L : 4000L;
                        }
                        if (pollAttempts < maxAttempts) {
                            handler.postDelayed(this, delay);
                        } else if (looksPaidPage) {
                            // Paid page seen but credit still pending — don't leave them stuck on FW order.
                            onPaymentCompleted(st.data, false);
                        }
                    });
                });
            }
        };
        // Start sooner — payment can finish quickly.
        handler.postDelayed(pollRunnable, 3500);
    }

    private static boolean isPaidStatus(@Nullable String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(Locale.US);
        return "completed".equals(s) || "paid".equals(s) || "success".equals(s);
    }

    private void onPaymentCompleted(@Nullable WalletDtos.FourthwallOrderStatus data,
                                    boolean creditedKnown) {
        if (finishedSuccess || isFinishing()) return;
        finishedSuccess = true;
        handler.removeCallbacks(pollRunnable);
        cancelLoadTimeout();

        int total = 0;
        if (data != null) {
            total = data.coins + Math.max(0, data.bonusCoins);
        }
        if (total <= 0 && pkg != null) {
            total = pkg.coins + Math.max(0, pkg.bonusCoins);
        }

        if (creditedKnown && total > 0) {
            hideLoading();
            // Navigation to Bag shows the success burst once (avoid double overlay).
            final int coinsForNav = total;
            handler.postDelayed(() -> goToWalletSuccess(coinsForNav, true), 400);
        } else {
            showLoading(getString(R.string.card_checkout_success));
            if (tvLoadingHint != null) {
                tvLoadingHint.setText(R.string.card_checkout_success_pending_hint);
            }
            final int coinsForNav = total;
            handler.postDelayed(() -> goToWalletSuccess(coinsForNav, creditedKnown), 900);
        }
    }

    private void goToWalletSuccess(int coins, boolean credited) {
        if (isFinishing()) return;
        Intent bag = new Intent(this, BagActivity.class);
        bag.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        bag.putExtra(EXTRA_RECHARGE_SUCCESS, true);
        bag.putExtra(EXTRA_RECHARGE_COINS, Math.max(0, coins));
        bag.putExtra(BagActivity.EXTRA_TAB, 0);
        if (!credited) {
            bag.putExtra("extra_recharge_pending", true);
        }
        startActivity(bag);
        finish();
    }

    private void showLoading(String msg) {
        if (tvLoading != null) tvLoading.setText(msg);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (finishedSuccess || looksPaidPage) {
            // Never leave user "stuck" on Fourthwall order via back stack thrashing.
            if (!finishedSuccess) {
                onPaymentCompleted(null, false);
            } else {
                finish();
            }
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancelLoadTimeout();
        if (pollRunnable != null) handler.removeCallbacks(pollRunnable);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
