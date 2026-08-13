package com.Dramizo.Series.billing;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.WalletRepository;

/**
 * Retries Google Play server credit a few times (network / brief Publisher lag).
 * Acknowledge already happened in {@link BillingHelper} — this only grants coins.
 */
public final class PlayPurchaseFulfillment {
    private PlayPurchaseFulfillment() {}

    public static Result<WalletDtos.WalletDto> verifyWithRetry(
            WalletRepository repo,
            String sku,
            String purchaseToken,
            @Nullable String orderId,
            int coins,
            double amountFiat) {
        Result<WalletDtos.WalletDto> last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            last = repo.verifyPurchase(
                    sku,
                    purchaseToken,
                    orderId != null ? orderId : "",
                    coins,
                    amountFiat);
            if (last != null && last.success) return last;
            String err = last != null ? String.valueOf(last.error) : "";
            // Don't hammer on permanent cancel.
            if (err.contains("إلغاء") || err.toLowerCase().contains("cancel")) {
                return last;
            }
            try {
                Thread.sleep(700L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last != null
                ? last
                : Result.err("تعذر إضافة الرصيد — أعد فتح المحفظة");
    }
}
