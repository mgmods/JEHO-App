package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.GameDtos;
import com.Dramizo.Series.domain.model.Result;
import java.util.Map;

public interface GameRepository {
    Result<GameDtos.RoomGameDto> start(String roomId, String opponentId);
    Result<GameDtos.RoomGameDto> startHtml(String roomId, String type, String htmlGameKey, String opponentId);
    Result<GameDtos.RoomGameDto> join(String roomId, String gameId);
    Result<GameDtos.RoomGameDto> reject(String roomId, String gameId);
    Result<GameDtos.RoomGameDto> move(String roomId, String gameId, int cell);
    Result<GameDtos.RoomGameDto> syncState(String roomId, String gameId, Map<String, Object> state);
    Result<GameDtos.RoomGameDto> get(String roomId, String gameId);
    Result<GameDtos.RoomGameDto> cancel(String roomId, String gameId);
}
