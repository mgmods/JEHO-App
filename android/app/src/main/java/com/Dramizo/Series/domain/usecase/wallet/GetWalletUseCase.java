package com.Dramizo.Series.domain.usecase.wallet;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.WalletRepository;
public class GetWalletUseCase {
    private final WalletRepository repo;
    public GetWalletUseCase(WalletRepository repo) { this.repo = repo; }
    public Result<WalletDtos.WalletDto> execute() { return repo.getWallet(); }
}
