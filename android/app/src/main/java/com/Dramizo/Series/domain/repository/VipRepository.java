package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;

public interface VipRepository {
    Result<MiscDtos.VipPlanList> getPlans();
    Result<Object> purchase(int level, int durationDays);
    Result<java.util.Map<String, Object>> mysteriousModeStatus();
    Result<java.util.Map<String, Object>> setMysteriousMode(boolean enabled);
}
