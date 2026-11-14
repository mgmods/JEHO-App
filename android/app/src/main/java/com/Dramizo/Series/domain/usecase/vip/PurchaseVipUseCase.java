package com.Dramizo.Series.domain.usecase.vip;

import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.VipRepository;

public class PurchaseVipUseCase {
    private final VipRepository repo;
    public PurchaseVipUseCase(VipRepository repo) { this.repo = repo; }
    public Result<Object> execute(int level) { return execute(level, 30); }
    public Result<Object> execute(int level, int durationDays) {
        return repo.purchase(level, durationDays);
    }
}
