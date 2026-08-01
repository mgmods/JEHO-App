package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.CosmeticsApi;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.CosmeticsRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.Collections;
import java.util.List;

public class CosmeticsRepositoryImpl implements CosmeticsRepository {
    private final CosmeticsApi api;

    public CosmeticsRepositoryImpl(CosmeticsApi api) {
        this.api = api;
    }

    @Override
    public Result<List<CosmeticDtos.CosmeticDto>> catalog(String type) {
        Result<CosmeticDtos.CatalogList> r = ApiCall.execute(api.catalog(type));
        if (r.success && r.data != null) return Result.ok(r.data);
        return Result.err(r.error);
    }

    @Override
    public Result<List<CosmeticDtos.UserCosmeticDto>> inventory() {
        Result<CosmeticDtos.InventoryList> r = ApiCall.execute(api.inventory());
        if (r.success && r.data != null) return Result.ok(r.data);
        if (r.success) return Result.ok(Collections.emptyList());
        return Result.err(r.error);
    }

    @Override
    public Result<CosmeticDtos.UserCosmeticDto> purchase(String cosmeticId) {
        return ApiCall.execute(api.purchase(new CosmeticDtos.PurchaseRequest(cosmeticId)));
    }

    @Override
    public Result<CosmeticDtos.EquipResult> equip(String cosmeticId) {
        return ApiCall.execute(api.equip(new CosmeticDtos.EquipRequest(cosmeticId)));
    }
}
