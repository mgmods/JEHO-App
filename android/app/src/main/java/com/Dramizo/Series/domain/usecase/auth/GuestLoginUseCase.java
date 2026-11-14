package com.Dramizo.Series.domain.usecase.auth;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
public class GuestLoginUseCase {
    private final AuthRepository repo;
    public GuestLoginUseCase(AuthRepository repo) { this.repo = repo; }
    public Result<AuthDtos.AuthResult> execute(String deviceId, String displayName) {
        return repo.guestLogin(deviceId, displayName);
    }
}
