package com.Dramizo.Series.domain.usecase.agency;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AgencyRepository;
public class GetAgenciesUseCase {
    private final AgencyRepository repo;
    public GetAgenciesUseCase(AgencyRepository repo) { this.repo = repo; }
    public Result<MiscDtos.ListResult<MiscDtos.AgencyDto>> execute(int page) { return repo.list(page); }
}
