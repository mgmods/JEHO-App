package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.GameApi;
import com.Dramizo.Series.data.remote.dto.GameDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.GameRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class GameRepositoryImpl implements GameRepository {
    private final GameApi api;

    public GameRepositoryImpl(GameApi api, ExecutorService io) {
        this.api = api;
    }

    @Override
    public Result<GameDtos.RoomGameDto> start(String roomId, String opponentId) {
        GameDtos.StartGameRequest body = opponentId != null && !opponentId.isEmpty()
                ? new GameDtos.StartGameRequest("tic_tac_toe", opponentId)
                : new GameDtos.StartGameRequest("tic_tac_toe");
        return ApiCall.execute(api.start(roomId, body));
    }

    @Override
    public Result<GameDtos.RoomGameDto> startHtml(String roomId, String type, String htmlGameKey, String opponentId) {
        GameDtos.StartGameRequest body = new GameDtos.StartGameRequest(
                type != null && !type.isEmpty() ? type : "html5",
                opponentId != null && !opponentId.isEmpty() ? opponentId : null,
                htmlGameKey);
        return ApiCall.execute(api.start(roomId, body));
    }

    @Override
    public Result<GameDtos.RoomGameDto> join(String roomId, String gameId) {
        return ApiCall.execute(api.join(roomId, gameId));
    }

    @Override
    public Result<GameDtos.RoomGameDto> reject(String roomId, String gameId) {
        return ApiCall.execute(api.reject(roomId, gameId));
    }

    @Override
    public Result<GameDtos.RoomGameDto> move(String roomId, String gameId, int cell) {
        return ApiCall.execute(api.move(roomId, gameId, new GameDtos.MoveRequest(cell)));
    }

    @Override
    public Result<GameDtos.RoomGameDto> syncState(String roomId, String gameId, Map<String, Object> state) {
        return ApiCall.execute(api.syncState(roomId, gameId, new GameDtos.StateRequest(state)));
    }

    @Override
    public Result<GameDtos.RoomGameDto> get(String roomId, String gameId) {
        return ApiCall.execute(api.get(roomId, gameId));
    }

    @Override
    public Result<GameDtos.RoomGameDto> cancel(String roomId, String gameId) {
        return ApiCall.execute(api.cancel(roomId, gameId));
    }
}
