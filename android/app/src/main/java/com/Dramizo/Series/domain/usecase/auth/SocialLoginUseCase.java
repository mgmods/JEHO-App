package com.Dramizo.Series.domain.usecase.auth;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AuthRepository;

public class SocialLoginUseCase {
    private final AuthRepository repo;

    public SocialLoginUseCase(AuthRepository repo) {
        this.repo = repo;
    }

    public Result<AuthDtos.AuthResult> execute(String provider, String token) {
        return execute(provider, token, null, null, null, null);
    }

    public Result<AuthDtos.AuthResult> execute(
            String provider,
            String token,
            String email,
            String displayName,
            String avatarUrl,
            String providerUserId) {
        return repo.socialLogin(provider, token, email, displayName, avatarUrl, providerUserId);
    }
}
