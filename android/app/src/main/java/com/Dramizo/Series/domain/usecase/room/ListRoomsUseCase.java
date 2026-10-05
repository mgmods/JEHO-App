package com.Dramizo.Series.domain.usecase.room;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;

public class ListRoomsUseCase {
    private final RoomRepository repository;

    public ListRoomsUseCase(RoomRepository repository) {
        this.repository = repository;
    }

    public Result<MiscDtos.ListResult<RoomDtos.RoomDto>> execute(int page) {
        return repository.list(page, 20);
    }

    public Result<MiscDtos.ListResult<RoomDtos.RoomDto>> execute(int page, int limit) {
        return repository.list(page, limit);
    }

    public Result<MiscDtos.ListResult<RoomDtos.RoomDto>> execute(int page, int limit, String tag, String country) {
        return repository.list(page, limit, tag, country);
    }
}
