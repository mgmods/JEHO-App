package com.Dramizo.Series.domain.usecase.room;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;
public class RaiseHandUseCase {
    private final RoomRepository repo;
    public RaiseHandUseCase(RoomRepository repo) { this.repo = repo; }
    public Result<Object> execute(String id, boolean raised) {
        return execute(id, raised, null);
    }

    public Result<Object> execute(String id, boolean raised, Integer seatIndex) {
        return repo.raiseHand(id, raised, seatIndex);
    }
}
