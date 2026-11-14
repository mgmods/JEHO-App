package com.Dramizo.Series.domain.usecase.ranking;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RankingRepository;
public class GetRankingsUseCase {
    private final RankingRepository repo;
    public GetRankingsUseCase(RankingRepository repo) { this.repo = repo; }
    public Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> execute(String period, String type) {
        return repo.list(period, type);
    }
}
