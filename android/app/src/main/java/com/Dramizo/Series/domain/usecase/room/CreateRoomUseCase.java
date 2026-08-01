package com.Dramizo.Series.domain.usecase.room;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;

public class CreateRoomUseCase {
    private final RoomRepository repository;

    public CreateRoomUseCase(RoomRepository repository) {
        this.repository = repository;
    }

    public Result<RoomDtos.JoinRoomResult> execute(String title, String type, int seatCount, String password, boolean isPublic) {
        return execute(title, type, seatCount, password, isPublic, null);
    }

    public Result<RoomDtos.JoinRoomResult> execute(String title, String type, int seatCount, String password, boolean isPublic, String coverUrl) {
        return repository.create(new RoomDtos.CreateRoomRequest(title, type, seatCount, password, isPublic, coverUrl));
    }

    public Result<RoomDtos.JoinRoomResult> executeAgencyRoom(
            String agencyId, String title, String coverUrl) {
        return repository.create(
                RoomDtos.CreateRoomRequest.agencyRoom(agencyId, title, coverUrl));
    }
}
