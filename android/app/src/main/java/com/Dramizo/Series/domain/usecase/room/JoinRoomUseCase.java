package com.Dramizo.Series.domain.usecase.room;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;
public class JoinRoomUseCase {
    private final RoomRepository repo;
    public JoinRoomUseCase(RoomRepository repo) { this.repo = repo; }
    public Result<RoomDtos.JoinRoomResult> execute(String id, String password) { return repo.join(id, password); }
}
