package com.Dramizo.Series.domain.usecase.user;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.UserRepository;
public class UpdateProfileUseCase {
    private final UserRepository repo;
    public UpdateProfileUseCase(UserRepository repo) { this.repo = repo; }
    public Result<AuthDtos.UserDto> execute(MiscDtos.UpdateProfileRequest request) {
        return repo.updateProfile(request);
    }
}
