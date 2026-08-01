package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.domain.model.Result;
import java.util.List;

public interface CosmeticsRepository {
    Result<List<CosmeticDtos.CosmeticDto>> catalog(String type);
    Result<List<CosmeticDtos.UserCosmeticDto>> inventory();
    Result<CosmeticDtos.UserCosmeticDto> purchase(String cosmeticId);
    Result<CosmeticDtos.EquipResult> equip(String cosmeticId);
}
