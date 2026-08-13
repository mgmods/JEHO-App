package com.Dramizo.Series.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.Dramizo.Series.data.remote.dto.WalletDtos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple Play Billing flow (normal apps):
 * 1) User pays → PURCHASED
 * 2) Acknowledge immediately (stops Google auto-refund after ~3 days)
 * 3) App/server credits coins (idempotent by purchaseToken)
 * 4) Consume so the SKU can be bought again
 */
public class BillingHelper implements PurchasesUpdatedListener {
    private static final String TAG = "BillingHelper";
    private static final String PREFS = "play_billing_pending";
    private static final String KEY_SKU = "sku";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ORDER = "orderId";

    public interface PurchaseCallback {
        void onPurchaseSuccess(String sku, String purchaseToken, String orderId);
        void onPurchaseError(String message);
    }

    public interface RecoverCallback {
        void onPending(@Nullable String sku, @Nullable String token, @Nullable String orderId);
    }

    private final Context appContext;
    private final BillingClient billingClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private PurchaseCallback callback;
    private final List<ProductDetails> productDetailsList = new ArrayList<>();
    @Nullable private Purchase pendingConsume;

    public BillingHelper(Context context) {
        this.appContext = context.getApplicationContext();
        billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();
        startConnection();
    }

    public void startConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                ready.set(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK);
                Log.i(TAG, "Billing setup: " + billingResult.getResponseCode());
                if (ready.get()) {
                    queryAndRestoreUnacked();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                ready.set(false);
                Log.w(TAG, "Billing disconnected — retrying");
                main.postDelayed(BillingHelper.this::startConnection, 1500);
            }
        });
    }

    public void setPurchaseCallback(@Nullable PurchaseCallback cb) {
        this.callback = cb;
    }

    /** Re-query owned INAPP purchases that still need server credit / consume. */
    public void queryAndRestoreUnacked() {
        queryAndRestoreUnacked(null);
    }

    public void queryAndRestoreUnacked(@Nullable Runnable onDone) {
        if (!isReady()) {
            if (onDone != null) main.post(onDone);
            return;
        }
        try {
            billingClient.queryPurchasesAsync(
                    com.android.billingclient.api.QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    (billingResult, purchases) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                && purchases != null) {
                            for (Purchase purchase : purchases) {
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    pendingConsume = purchase;
                                    persistPending(purchase);
                                    // Always ack early — refunds happen when unacked.
                                    acknowledgeIfNeeded(purchase);
                                    Log.i(TAG, "restore unconsumed product="
                                            + (purchase.getProducts().isEmpty()
                                            ? "?" : purchase.getProducts().get(0)));
                                }
                            }
                        }
                        if (onDone != null) main.post(onDone);
                    });
        } catch (Throwable t) {
            Log.w(TAG, "queryPurchases restore failed: " + t.getMessage());
            if (onDone != null) main.post(onDone);
        }
    }

    /**
     * After wallet / app opens: re-run server credit for any paid-but-unconsumed purchase.
     * Waits for Play query so we do not race an empty pendingConsume.
     */
    public void recoverPendingIfAny(@Nullable PurchaseCallback cb) {
        if (cb != null) callback = cb;
        Runnable deliver = () -> {
            String sku = null;
            String token = null;
            String orderId = null;
            Purchase p = pendingConsume;
            if (p != null && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                sku = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
                token = p.getPurchaseToken();
                orderId = p.getOrderId();
            } else {
                SharedPreferences prefs = prefs();
                sku = prefs.getString(KEY_SKU, null);
                token = prefs.getString(KEY_TOKEN, null);
                orderId = prefs.getString(KEY_ORDER, null);
            }
            if (token == null || token.isEmpty() || sku == null || sku.isEmpty()) return;
            if (callback != null) {
                callback.onPurchaseSuccess(sku, token, orderId != null ? orderId : "");
            }
        };
        if (pendingConsume != null) {
            deliver.run();
            return;
        }
        queryAndRestoreUnacked(deliver);
    }

    public boolean isReady() {
        return ready.get() && billingClient.isReady();
    }

    public void queryProducts(List<String> skus, Runnable onReady) {
        if (skus == null || skus.isEmpty()) {
            if (onReady != null) main.post(onReady);
            return;
        }
        Runnable runQuery = () -> {
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            for (String sku : skus) {
                if (sku == null || sku.isEmpty()) continue;
                products.add(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build());
            }
            if (products.isEmpty()) {
                if (onReady != null) main.post(onReady);
                return;
            }
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(products)
                    .build();
            billingClient.queryProductDetailsAsync(params, (billingResult, detailsResult) -> {
                productDetailsList.clear();
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                        && detailsResult != null) {
                    List<ProductDetails> fetched = detailsResult.getProductDetailsList();
                    if (fetched != null) productDetailsList.addAll(fetched);
                    if (detailsResult.getUnfetchedProductList() != null
                            && !detailsResult.getUnfetchedProductList().isEmpty()) {
                        Log.w(TAG, "Unfetched products: "
                                + detailsResult.getUnfetchedProductList().size());
                    }
                } else {
                    Log.w(TAG, "queryProductDetails failed: " + billingResult.getResponseCode()
                            + " " + billingResult.getDebugMessage());
                }
                if (onReady != null) main.post(onReady);
            });
        };
        if (!isReady()) {
            startConnection();
            main.postDelayed(runQuery, 3000);
        } else {
            runQuery.run();
        }
    }

    public void launchPurchase(Activity activity, String sku, PurchaseCallback cb) {
        this.callback = cb;
        if (!isReady()) {
            startConnection();
            main.postDelayed(() -> {
                if (!isReady()) {
                    if (cb != null) cb.onPurchaseError("Google Play غير جاهز — حاول مرة أخرى");
                    return;
                }
                launchPurchase(activity, sku, cb);
            }, 2500);
            return;
        }
        ProductDetails details = findDetails(sku);
        if (details == null) {
            queryProducts(Collections.singletonList(sku), () -> {
                ProductDetails again = findDetails(sku);
                if (again == null) {
                    if (cb != null) cb.onPurchaseError("المنتج غير متوفر في Google Play: " + sku);
                    return;
                }
                startFlow(activity, again, cb);
            });
            return;
        }
        startFlow(activity, details, cb);
    }

    private void startFlow(Activity activity, ProductDetails details, PurchaseCallback cb) {
        List<BillingFlowParams.ProductDetailsParams> paramsList = Collections.singletonList(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
        );
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(paramsList)
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            String dbg = result.getDebugMessage();
            if (cb != null) {
                cb.onPurchaseError(dbg != null && !dbg.isEmpty()
                        ? dbg
                        : ("تعذر فتح الشراء (" + result.getResponseCode() + ")"));
            }
        }
    }

    @Nullable
    private ProductDetails findDetails(String sku) {
        if (sku == null) return null;
        for (ProductDetails d : productDetailsList) {
            if (d.getProductId().equals(sku)) return d;
        }
        return null;
    }

    @Nullable
    public String getFormattedPrice(String sku) {
        ProductDetails details = findDetails(sku);
        if (details == null) return null;
        ProductDetails.OneTimePurchaseOfferDetails offer = oneTimeOffer(details);
        if (offer == null) return null;
        String formatted = offer.getFormattedPrice();
        return formatted != null && !formatted.isEmpty() ? formatted : null;
    }

    public double getPriceAmount(String sku, double fallback) {
        ProductDetails details = findDetails(sku);
        if (details == null) return fallback;
        ProductDetails.OneTimePurchaseOfferDetails offer = oneTimeOffer(details);
        if (offer == null) return fallback;
        return offer.getPriceAmountMicros() / 1_000_000.0;
    }

    @Nullable
    private static ProductDetails.OneTimePurchaseOfferDetails oneTimeOffer(
            @NonNull ProductDetails details) {
        ProductDetails.OneTimePurchaseOfferDetails offer = details.getOneTimePurchaseOfferDetails();
        if (offer != null) return offer;
        try {
            List<ProductDetails.OneTimePurchaseOfferDetails> list =
                    details.getOneTimePurchaseOfferDetailsList();
            if (list != null && !list.isEmpty()) return list.get(0);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void applyPlayPrices(List<WalletDtos.RechargePackageDto> packages) {
        if (packages == null) return;
        for (WalletDtos.RechargePackageDto pkg : packages) {
            if (pkg == null || pkg.sku == null) continue;
            String label = getFormattedPrice(pkg.sku);
            if (label != null) {
                pkg.playPriceLabel = label;
                pkg.playPriceAmount = getPriceAmount(pkg.sku, pkg.priceUsd);
                pkg.playPriceLoaded = true;
            }
        }
    }

    public void applyPlayPricesToOffers(List<com.Dramizo.Series.data.remote.dto.MiscDtos.OfferDto> offers) {
        if (offers == null) return;
        for (com.Dramizo.Series.data.remote.dto.MiscDtos.OfferDto offer : offers) {
            if (offer == null || offer.sku == null) continue;
            String label = getFormattedPrice(offer.sku);
            if (label != null) {
                offer.playPriceLabel = label;
                offer.playPriceAmount = getPriceAmount(offer.sku, offer.priceUsd);
                offer.playPriceLoaded = true;
            }
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (callback != null) callback.onPurchaseError("تم إلغاء الشراء");
        } else {
            if (callback != null) {
                String dbg = billingResult.getDebugMessage();
                callback.onPurchaseError(dbg != null && !dbg.isEmpty() ? dbg : "تعذر إتمام الشراء");
            }
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        String sku = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);
        pendingConsume = purchase;
        persistPending(purchase);
        // CRITICAL: acknowledge immediately so Google does not auto-refund.
        acknowledgeIfNeeded(purchase);
        if (callback != null) {
            callback.onPurchaseSuccess(sku, purchase.getPurchaseToken(), purchase.getOrderId());
        }
    }

    private void acknowledgeIfNeeded(@NonNull Purchase purchase) {
        if (purchase.isAcknowledged()) return;
        try {
            AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(ack, result ->
                    Log.i(TAG, "Ack: " + result.getResponseCode()
                            + " " + result.getDebugMessage()));
        } catch (Throwable t) {
            Log.w(TAG, "acknowledge failed: " + t.getMessage());
        }
    }

    /** Call after backend credits coins successfully. */
    public void consumePendingPurchase() {
        Purchase purchase = pendingConsume;
        String tokenFromPrefs = prefs().getString(KEY_TOKEN, null);
        pendingConsume = null;
        clearPersisted();

        String token = purchase != null ? purchase.getPurchaseToken() : tokenFromPrefs;
        if (token == null || token.isEmpty()) return;

        if (purchase != null) {
            acknowledgeIfNeeded(purchase);
        }
        try {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(token)
                    .build();
            billingClient.consumeAsync(consumeParams, (result, consumed) ->
                    Log.i(TAG, "Consumed: " + result.getResponseCode()));
        } catch (Throwable t) {
            Log.w(TAG, "consume failed: " + t.getMessage());
        }
    }

    private void persistPending(@NonNull Purchase purchase) {
        String sku = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);
        prefs().edit()
                .putString(KEY_SKU, sku)
                .putString(KEY_TOKEN, purchase.getPurchaseToken())
                .putString(KEY_ORDER, purchase.getOrderId() != null ? purchase.getOrderId() : "")
                .apply();
    }

    private void clearPersisted() {
        prefs().edit().clear().apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void endConnection() {
        billingClient.endConnection();
    }
}
