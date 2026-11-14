package com.Dramizo.Series.domain.usecase.wallet;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.WalletRepository;
public class GetRechargePackagesUseCase {
    private final WalletRepository repo;
    public GetRechargePackagesUseCase(WalletRepository repo) { this.repo = repo; }
    public Result<WalletDtos.PackagesResult> execute() { return repo.getPackages(); }
}
