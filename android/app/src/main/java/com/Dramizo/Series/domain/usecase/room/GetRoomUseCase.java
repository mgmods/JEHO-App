package com.Dramizo.Series.domain.usecase.room;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;
public class GetRoomUseCase {
    private final RoomRepository repo;
    public GetRoomUseCase(RoomRepository repo) { this.repo = repo; }
    public Result<RoomDtos.RoomDto> execute(String id) { return repo.get(id); }
}
