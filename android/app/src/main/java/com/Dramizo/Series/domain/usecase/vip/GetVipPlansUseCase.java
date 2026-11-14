package com.Dramizo.Series.domain.usecase.vip;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.VipRepository;

public class GetVipPlansUseCase {
    private final VipRepository repo;
    public GetVipPlansUseCase(VipRepository repo) { this.repo = repo; }
    public Result<MiscDtos.VipPlanList> execute() { return repo.getPlans(); }
}
