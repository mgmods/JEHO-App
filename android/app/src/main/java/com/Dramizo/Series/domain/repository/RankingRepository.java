package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;

public interface RankingRepository {
    Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> list(String period, String type);
}
