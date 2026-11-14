package com.Dramizo.Series.domain.usecase.auth;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
public class LoginUseCase {
    private final AuthRepository repo;
    public LoginUseCase(AuthRepository repo) { this.repo = repo; }
    public Result<AuthDtos.AuthResult> execute(String identifier, String password) {
        return repo.login(identifier, password);
    }
}
