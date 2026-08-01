package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.WalletDao;
import com.Dramizo.Series.data.local.entity.WalletEntity;
import com.Dramizo.Series.data.remote.api.WalletApi;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.WalletRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.concurrent.ExecutorService;

public class WalletRepositoryImpl implements WalletRepository {
    private final WalletApi api;
    private final WalletDao dao;
    private final ExecutorService io;

    public WalletRepositoryImpl(WalletApi api, WalletDao dao, ExecutorService io) {
        this.api = api; this.dao = dao; this.io = io;
    }

    private void cache(WalletDtos.WalletDto w) {
        if (w == null) return;
        WalletEntity e = new WalletEntity();
        e.userId = w.userId; e.coins = w.coins; e.diamonds = w.diamonds;
        e.cachedAt = System.currentTimeMillis();
        io.execute(() -> dao.upsert(e));
    }

    @Override public Result<WalletDtos.WalletDto> getWallet() {
        Result<WalletDtos.WalletDto> r = ApiCall.execute(api.getWallet());
        if (r.success) cache(r.data);
        return r;
    }
    @Override public Result<WalletDtos.PackagesResult> getPackages() {
        return ApiCall.execute(api.packages());
    }
    @Override public Result<WalletDtos.WalletDto> verifyPurchase(String sku, String purchaseToken, String orderId,
                                                                 int coins, double amountFiat) {
        Result<WalletDtos.WalletDto> r = ApiCall.execute(api.verifyPurchase(
                new WalletDtos.VerifyPurchaseRequest(sku, purchaseToken, orderId, coins, amountFiat)));
        if (r.success) cache(r.data);
        return r;
    }
}
