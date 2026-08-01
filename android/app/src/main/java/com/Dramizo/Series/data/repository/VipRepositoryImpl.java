package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.VipApi;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.VipRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.concurrent.ExecutorService;

public class VipRepositoryImpl implements VipRepository {
    private final VipApi api;
    public VipRepositoryImpl(VipApi api, ExecutorService io) { this.api = api; }
    @Override public Result<MiscDtos.VipPlanList> getPlans() {
        return ApiCall.execute(api.plans());
    }
    @Override public Result<Object> purchase(int level) {
        return ApiCall.execute(api.purchase(new MiscDtos.PurchaseVipRequest(level)));
    }
}
