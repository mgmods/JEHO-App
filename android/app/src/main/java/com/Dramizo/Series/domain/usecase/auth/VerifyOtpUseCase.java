package com.Dramizo.Series.domain.usecase.auth;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
public class VerifyOtpUseCase {
    private final AuthRepository repo;
    public VerifyOtpUseCase(AuthRepository repo) { this.repo = repo; }
    public Result<AuthDtos.AuthResult> execute(String phone, String code, String username) {
        return repo.verifyOtp(phone, code, username);
    }
}
