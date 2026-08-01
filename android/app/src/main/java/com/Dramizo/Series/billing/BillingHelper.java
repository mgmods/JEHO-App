package com.Dramizo.Series.billing;

import android.app.Activity;
import android.content.Context;
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

public class BillingHelper implements PurchasesUpdatedListener {
    private static final String TAG = "BillingHelper";

    public interface PurchaseCallback {
        void onPurchaseSuccess(String sku, String purchaseToken, String orderId);
        void onPurchaseError(String message);
    }

    private final BillingClient billingClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private PurchaseCallback callback;
    private final List<ProductDetails> productDetailsList = new ArrayList<>();
    private Purchase pendingConsume;

    public BillingHelper(Context context) {
        billingClient = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build())
                .build();
        startConnection();
    }

    private void startConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                ready.set(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK);
                Log.i(TAG, "Billing setup: " + billingResult.getResponseCode());
            }

            @Override
            public void onBillingServiceDisconnected() {
                ready.set(false);
                Log.w(TAG, "Billing disconnected — retrying");
                main.postDelayed(BillingHelper.this::startConnection, 1500);
            }
        });
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

    /**
     * Localized price string from Google Play Console for this SKU
     * (e.g. "$0.99", "₺29,99"), or null if not queried yet / missing.
     */
    @Nullable
    public String getFormattedPrice(String sku) {
        ProductDetails details = findDetails(sku);
        if (details == null) return null;
        ProductDetails.OneTimePurchaseOfferDetails offer = oneTimeOffer(details);
        if (offer == null) return null;
        String formatted = offer.getFormattedPrice();
        return formatted != null && !formatted.isEmpty() ? formatted : null;
    }

    /** Price amount in the Play store currency (micros / 1e6), or {@code fallback} if unknown. */
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
        // Billing 8 may expose multiple one-time offers; take the first available.
        try {
            List<ProductDetails.OneTimePurchaseOfferDetails> list =
                    details.getOneTimePurchaseOfferDetailsList();
            if (list != null && !list.isEmpty()) return list.get(0);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Apply Play Console prices onto package DTOs (call after {@link #queryProducts}). */
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

    /** Same for first-recharge / promo offers that use Play SKUs. */
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
        if (callback != null) {
            callback.onPurchaseSuccess(sku, purchase.getPurchaseToken(), purchase.getOrderId());
        }
    }

    /** Call only after backend verification succeeds. */
    public void consumePendingPurchase() {
        Purchase purchase = pendingConsume;
        pendingConsume = null;
        if (purchase == null) return;
        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
            billingClient.acknowledgePurchase(ack, result -> Log.i(TAG, "Ack: " + result.getResponseCode()));
        }
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.consumeAsync(consumeParams, (result, token) -> Log.i(TAG, "Consumed: " + result.getResponseCode()));
    }

    public void endConnection() {
        billingClient.endConnection();
    }
}
