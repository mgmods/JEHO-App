package com.Dramizo.Series.presentation.wallet;
import androidx.lifecycle.LiveData; import androidx.lifecycle.MutableLiveData; import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.WalletDtos; import com.Dramizo.Series.di.AppContainer; import com.Dramizo.Series.domain.model.Result;
import java.util.Collections; import java.util.List;
public class WalletViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<WalletDtos.WalletDto> wallet = new MutableLiveData<>();
    private final MutableLiveData<List<WalletDtos.RechargePackageDto>> packages = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    public WalletViewModel(AppContainer c) { this.c = c; }
    public LiveData<WalletDtos.WalletDto> getWallet() { return wallet; }
    public LiveData<List<WalletDtos.RechargePackageDto>> getPackages() { return packages; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getMessage() { return message; }
    public void load() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            if (w.success) wallet.postValue(w.data); else error.postValue(w.error);
            Result<WalletDtos.PackagesResult> p = c.getRechargePackagesUseCase.execute();
            if (p.success && p.data != null && p.data.items != null) packages.postValue(p.data.items);
        });
    }
    public void verifyPurchase(String sku, String token, String orderId, int coins, double amountFiat) {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = c.verifyPurchaseUseCase.execute(sku, token, orderId, coins, amountFiat);
            if (r.success) { wallet.postValue(r.data); message.postValue("تم الشحن بنجاح"); }
            else error.postValue(r.error);
        });
    }
}
