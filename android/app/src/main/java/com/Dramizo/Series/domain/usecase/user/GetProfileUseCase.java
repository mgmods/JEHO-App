package com.Dramizo.Series.domain.usecase.user;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.UserRepository;
public class GetProfileUseCase {
    private final UserRepository repo;
    public GetProfileUseCase(UserRepository repo) { this.repo = repo; }
    public Result<AuthDtos.UserDto> me() { return repo.getMe(); }
    public Result<AuthDtos.UserDto> byId(String id) { return repo.getUser(id); }
}
