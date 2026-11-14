package com.Dramizo.Series.domain.usecase.auth;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;
public class RegisterUseCase {
    private final AuthRepository repo;
    public RegisterUseCase(AuthRepository repo) { this.repo = repo; }
    public Result<AuthDtos.AuthResult> execute(String email, String password, String username, String displayName) {
        return repo.register(email, password, username, displayName);
    }
}
