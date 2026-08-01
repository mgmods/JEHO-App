package com.Dramizo.Series.domain.usecase.auth;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
public class SendOtpUseCase {
    private final AuthRepository repo;
    public SendOtpUseCase(AuthRepository repo) { this.repo = repo; }
    public Result<AuthDtos.OtpSentResult> execute(String phone) { return repo.sendOtp(phone); }
}
