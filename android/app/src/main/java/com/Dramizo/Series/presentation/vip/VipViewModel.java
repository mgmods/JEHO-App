package com.Dramizo.Series.presentation.vip;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VipViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<MiscDtos.VipPlanDto>> plans = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> vipLevel = new MutableLiveData<>(0);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> purchasing = new MutableLiveData<>(false);
    private final AtomicBoolean purchaseInFlight = new AtomicBoolean(false);

    public VipViewModel(AppContainer c) { this.c = c; }
    public LiveData<List<MiscDtos.VipPlanDto>> getPlans() { return plans; }
    public LiveData<Integer> getVipLevel() { return vipLevel; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getPurchasing() { return purchasing; }

    public void load() {
        AuthDtos.UserDto cached = c.getSessionManager().getUser();
        if (cached != null) vipLevel.postValue(Math.max(0, cached.vipLevel));
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.VipPlanList> r = c.getVipPlansUseCase.execute();
            if (r.success && r.data != null) {
                plans.postValue(new ArrayList<>(r.data));
            } else {
                error.postValue(r.error != null ? r.error : "تعذر تحميل خطط VIP");
            }
            refreshMe();
        });
    }

    public void purchase(int level) {
        if (!purchaseInFlight.compareAndSet(false, true)) return;
        purchasing.postValue(true);
        c.getIoExecutor().execute(() -> {
            try {
                Result<Object> r = c.purchaseVipUseCase.execute(level);
                if (r.success) {
                    refreshMe();
                    message.postValue("تمت الترقية بنجاح — أنت الآن VIP " + level);
                } else {
                    error.postValue(r.error != null ? r.error : "تعذر شراء VIP");
                }
            } finally {
                purchaseInFlight.set(false);
                purchasing.postValue(false);
            }
        });
    }

    private void refreshMe() {
        Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
        if (me.success && me.data != null) {
            c.getSessionManager().updateCachedUser(me.data);
            vipLevel.postValue(Math.max(0, me.data.vipLevel));
        }
    }
}
