package com.Dramizo.Series.domain.usecase.wallet;

import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.WalletRepository;

public class VerifyPurchaseUseCase {
    private final WalletRepository repo;
    public VerifyPurchaseUseCase(WalletRepository repo) { this.repo = repo; }
    public Result<WalletDtos.WalletDto> execute(String sku, String token, String orderId,
                                                int coins, double amountFiat) {
        return repo.verifyPurchase(sku, token, orderId, coins, amountFiat);
    }
}
