package com.Dramizo.Series.domain.usecase.gift;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.GiftRepository;
public class GetGiftsUseCase {
    private final GiftRepository repo;
    public GetGiftsUseCase(GiftRepository repo) { this.repo = repo; }
    public Result<GiftDtos.GiftList> execute() { return repo.getGifts(); }
}
