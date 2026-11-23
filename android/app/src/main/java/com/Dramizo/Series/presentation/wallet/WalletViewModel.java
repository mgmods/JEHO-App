package com.Dramizo.Series.presentation.wallet;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

import java.util.Collections;
import java.util.List;

public class WalletViewModel extends ViewModel {
    private static final long WALLET_CACHE_MS = 5_000L;
    private static final long PACKAGES_CACHE_MS = 60_000L;

    private final AppContainer c;
    private final MutableLiveData<WalletDtos.WalletDto> wallet = new MutableLiveData<>();
    private final MutableLiveData<List<WalletDtos.RechargePackageDto>> packages =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    private volatile boolean walletLoading;
    private volatile boolean packagesLoading;
    private volatile long lastWalletLoadAt;
    private volatile long lastPackagesLoadAt;

    public WalletViewModel(AppContainer c) { this.c = c; }
    public LiveData<WalletDtos.WalletDto> getWallet() { return wallet; }
    public LiveData<List<WalletDtos.RechargePackageDto>> getPackages() { return packages; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getMessage() { return message; }

    /** Reuse warm wallet/package data when the screen is reopened rapidly. */
    public void load() {
        final long now = System.currentTimeMillis();
        final boolean loadWallet;
        final boolean loadPackages;
        synchronized (this) {
            loadWallet = !walletLoading && (lastWalletLoadAt == 0L
                    || now - lastWalletLoadAt >= WALLET_CACHE_MS);
            loadPackages = !packagesLoading && (lastPackagesLoadAt == 0L
                    || now - lastPackagesLoadAt >= PACKAGES_CACHE_MS);
            if (!loadWallet && !loadPackages) return;
            if (loadWallet) walletLoading = true;
            if (loadPackages) packagesLoading = true;
        }
        try {
            c.getIoExecutor().execute(() -> {
                if (loadWallet) {
                    boolean success = false;
                    try {
                        Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
                        if (w.success && w.data != null) {
                            wallet.postValue(w.data);
                            success = true;
                        } else if (!w.success) {
                            error.postValue(w.error);
                        }
                    } catch (RuntimeException failure) {
                        android.util.Log.w("WalletViewModel", "Wallet refresh failed", failure);
                    } finally {
                        synchronized (WalletViewModel.this) {
                            walletLoading = false;
                            lastWalletLoadAt = success ? System.currentTimeMillis() : 0L;
                        }
                    }
                }
                if (loadPackages) {
                    boolean success = false;
                    try {
                        Result<WalletDtos.PackagesResult> p =
                                c.getRechargePackagesUseCase.execute();
                        if (p.success && p.data != null && p.data.items != null) {
                            packages.postValue(p.data.items);
                            success = true;
                        }
                    } catch (RuntimeException failure) {
                        android.util.Log.w("WalletViewModel", "Recharge packages refresh failed", failure);
                    } finally {
                        synchronized (WalletViewModel.this) {
                            packagesLoading = false;
                            lastPackagesLoadAt = success
                                    ? System.currentTimeMillis() : 0L;
                        }
                    }
                }
            });
        } catch (RuntimeException rejected) {
            synchronized (this) {
                if (loadWallet) walletLoading = false;
                if (loadPackages) packagesLoading = false;
            }
            android.util.Log.w("WalletViewModel", "Wallet refresh rejected", rejected);
        }
    }

    public void verifyPurchase(String sku, String token, String orderId,
                               int coins, double amountFiat) {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r =
                    c.verifyPurchaseUseCase.execute(sku, token, orderId, coins, amountFiat);
            if (r.success && r.data != null) {
                wallet.postValue(r.data);
                synchronized (WalletViewModel.this) {
                    // The purchase response is authoritative; force the next explicit
                    // refresh to bypass the normal short wallet cache.
                    lastWalletLoadAt = 0L;
                }
                message.postValue("تم الشحن بنجاح");
            } else {
                error.postValue(r.error);
            }
        });
    }
}
