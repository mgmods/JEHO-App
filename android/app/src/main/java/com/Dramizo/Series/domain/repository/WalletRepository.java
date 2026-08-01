package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;

public interface WalletRepository {
    Result<WalletDtos.WalletDto> getWallet();
    Result<WalletDtos.PackagesResult> getPackages();
    Result<WalletDtos.WalletDto> verifyPurchase(String sku, String purchaseToken, String orderId,
                                                int coins, double amountFiat);
}
