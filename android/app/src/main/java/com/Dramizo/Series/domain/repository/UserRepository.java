package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;

public interface UserRepository {
    Result<AuthDtos.UserDto> getMe();
    Result<AuthDtos.UserDto> getUser(String id);
    Result<AuthDtos.UserDto> updateProfile(MiscDtos.UpdateProfileRequest request);
    Result<Object> follow(String id);
    Result<Object> unfollow(String id);
    Result<MiscDtos.ListResult<AuthDtos.UserDto>> search(String q, int page);
    Result<MiscDtos.ListResult<AuthDtos.UserDto>> blocked();
    Result<Object> unblock(String id);
}
