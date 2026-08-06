package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.RoomApi;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RoomRepository;
import com.Dramizo.Series.util.ApiCall;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class RoomRepositoryImpl implements RoomRepository {
    private final RoomApi api;
    private final ExecutorService io;

    public RoomRepositoryImpl(RoomApi api, ExecutorService io) {
        this.api = api;
        this.io = io;
    }

    @Override
    public Result<RoomDtos.MusicLibraryDto> musicLibrary() {
        return ApiCall.execute(api.musicLibrary());
    }

    @Override
    public Result<RoomDtos.InternetMusicSearchDto> musicSearch(String query) {
        return ApiCall.execute(api.musicSearch(query));
    }

    @Override
    public Result<RoomDtos.InternetMusicResolvedDto> musicResolve(String videoId) {
        return ApiCall.execute(api.musicResolve(videoId));
    }

    @Override
    public Result<MiscDtos.ListResult<RoomDtos.RoomDto>> list(int page) {
        return list(page, 20);
    }

    @Override
    public Result<MiscDtos.ListResult<RoomDtos.RoomDto>> list(int page, int limit) {
        int safe = Math.max(1, Math.min(100, limit));
        return ApiCall.execute(api.list(page, safe));
    }

    @Override
    public Result<RoomDtos.JoinRoomResult> create(RoomDtos.CreateRoomRequest request) {
        return ApiCall.execute(api.create(request));
    }

    @Override
    public Result<RoomDtos.RoomDto> get(String id) {
        return ApiCall.execute(api.get(id));
    }

    @Override
    public Result<RoomDtos.SupportersResult> supporters(String id) {
        return ApiCall.execute(api.supporters(id));
    }

    @Override
    public Result<Object> reportUser(
            String id, String targetUserId, String reason, String description) {
        Map<String, String> body = new HashMap<>();
        body.put("targetUserId", targetUserId);
        body.put("reason", reason);
        if (description != null && !description.trim().isEmpty()) {
            body.put("description", description.trim());
        }
        return ApiCall.execute(api.reportUser(id, body));
    }

    @Override
    public Result<Map<String, Object>> updateMusic(
            String id, String action, String url, String title, String artist, Long positionMs) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        if (url != null) body.put("url", url);
        if (title != null) body.put("title", title);
        if (artist != null) body.put("artist", artist);
        if (positionMs != null) body.put("positionMs", positionMs);
        return ApiCall.execute(api.music(id, body));
    }

    @Override
    public Result<RoomDtos.JoinRoomResult> join(String id, String password) {
        Map<String, String> body = new HashMap<>();
        if (password != null) body.put("password", password);
        return ApiCall.execute(api.join(id, body));
    }

    @Override
    public Result<Object> raiseHand(String id, boolean raised) {
        return raiseHand(id, raised, null);
    }

    @Override
    public Result<Object> raiseHand(String id, boolean raised, Integer seatIndex) {
        return ApiCall.execute(api.raiseHand(id, new RoomDtos.RaiseHandRequest(raised, seatIndex)));
    }

    @Override
    public Result<RoomDtos.SeatRequestsResult> seatRequests(String id) {
        return ApiCall.execute(api.seatRequests(id));
    }

    @Override
    public Result<RoomDtos.RoomDto> approveSeat(String id, String userId, Integer seatIndex) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        if (seatIndex != null) body.put("seatIndex", seatIndex);
        return ApiCall.execute(api.approveSeat(id, body));
    }

    @Override
    public Result<Object> rejectSeat(String id, String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        return ApiCall.execute(api.rejectSeat(id, body));
    }

    @Override
    public Result<Object> inviteSeat(String id, String userId, Integer seatIndex) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        if (seatIndex != null) body.put("seatIndex", seatIndex);
        return ApiCall.execute(api.inviteSeat(id, body));
    }

    @Override
    public Result<Map<String, Object>> inviteTaskGuest(String id, String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        return ApiCall.execute(api.inviteTaskGuest(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> respondSeatInvite(String id, boolean accept) {
        return ApiCall.execute(api.respondSeatInvite(
                id, Collections.singletonMap("accept", accept)));
    }

    @Override
    public Result<RoomDtos.RoomDto> setGiftSounds(String id, boolean enabled) {
        Map<String, Boolean> body = new HashMap<>();
        body.put("enabled", enabled);
        return ApiCall.execute(api.setGiftSounds(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> setDisplaySettings(String id, Map<String, Boolean> patch) {
        Map<String, Boolean> body = patch != null ? patch : new HashMap<>();
        return ApiCall.execute(api.setDisplaySettings(id, body));
    }

    @Override
    public Result<Map<String, Object>> clearPublicChat(String id) {
        return ApiCall.execute(api.clearPublicChat(id));
    }

    @Override
    public Result<RoomDtos.RoomDto> setChatAutoClearMinutes(String id, int minutes) {
        Map<String, Integer> body = new HashMap<>();
        body.put("minutes", minutes);
        return ApiCall.execute(api.setChatAutoClear(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> lock(String id, boolean locked, String password) {
        return ApiCall.execute(api.lock(id, new RoomDtos.LockRoomRequest(locked, password)));
    }

    @Override
    public Result<Object> setMic(String id, boolean muted) {
        return ApiCall.execute(api.setMic(id, Collections.singletonMap("muted", muted)));
    }

    @Override
    public Result<Object> setMic(String id, boolean muted, String targetUserId) {
        Map<String, Object> body = new HashMap<>();
        body.put("muted", muted);
        body.put("userId", targetUserId);
        return ApiCall.execute(api.setMic(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> takeSeat(String id, int seatIndex) {
        Map<String, Integer> body = new HashMap<>();
        body.put("seatIndex", seatIndex);
        return ApiCall.execute(api.takeSeat(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> leaveSeat(String id) {
        return ApiCall.execute(api.leaveSeat(id));
    }

    @Override
    public Result<RoomDtos.RoomDto> lockSeat(String id, int seatIndex, boolean locked) {
        Map<String, Object> body = new HashMap<>();
        body.put("seatIndex", seatIndex);
        body.put("locked", locked);
        return ApiCall.execute(api.lockSeat(id, body));
    }

    @Override
    public Result<RoomDtos.RoomDto> resizeSeats(String id, int seatCount) {
        return ApiCall.execute(api.resizeSeats(id, Collections.singletonMap("seatCount", seatCount)));
    }

    @Override
    public Result<Object> kick(String id, String userId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        if (reason != null) body.put("reason", reason);
        return ApiCall.execute(api.kick(id, body));
    }

    @Override
    public Result<Object> ban(String id, String userId, String reason, Integer durationMinutes) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        if (reason != null) body.put("reason", reason);
        if (durationMinutes != null) body.put("durationMinutes", durationMinutes);
        return ApiCall.execute(api.ban(id, body));
    }

    @Override
    public Result<Object> unban(String id, String userId) {
        return ApiCall.execute(api.unban(id, userId));
    }

    @Override
    public Result<Boolean> isBanned(String id, String userId) {
        Result<Map<String, Object>> r = ApiCall.execute(api.banStatus(id, userId));
        if (!r.success) return Result.err(r.error);
        Object banned = r.data != null ? r.data.get("banned") : null;
        boolean value = banned instanceof Boolean ? (Boolean) banned
                : banned != null && Boolean.parseBoolean(String.valueOf(banned));
        return Result.ok(value);
    }

    @Override
    public Result<Object> setCohost(String id, String targetUserId) {
        Map<String, String> body = new HashMap<>();
        body.put("userId", targetUserId);
        return ApiCall.execute(api.setCohost(id, body));
    }

    @Override
    public Result<Object> addModerator(String id, String targetUserId) {
        return ApiCall.execute(api.addModerator(id, targetUserId));
    }

    @Override
    public Result<Object> removeModerator(String id, String targetUserId) {
        return ApiCall.execute(api.removeModerator(id, targetUserId));
    }

    @Override
    public Result<Object> updateModeratorPermissions(
            String id, String targetUserId, boolean canManageMusic,
            boolean canChangeFrames, boolean canControlGames,
            boolean canMute, boolean canKick, boolean canBan,
            boolean canManageSeats, boolean canInvite, boolean canManageRoom) {
        Map<String, Boolean> body = new HashMap<>();
        body.put("canManageMusic", canManageMusic);
        body.put("canChangeFrames", canChangeFrames);
        body.put("canControlGames", canControlGames);
        body.put("canMute", canMute);
        body.put("canKick", canKick);
        body.put("canBan", canBan);
        body.put("canManageSeats", canManageSeats);
        body.put("canInvite", canInvite);
        body.put("canManageRoom", canManageRoom);
        return ApiCall.execute(api.moderatorPermissions(id, targetUserId, body));
    }

    @Override
    public Result<Object> leave(String id) {
        return ApiCall.execute(api.leave(id));
    }

    @Override
    public Result<Object> close(String id) {
        return ApiCall.execute(api.close(id));
    }

    @Override
    public Result<RoomDtos.RoomDto> update(String id, String title, String coverUrl) {
        Map<String, String> body = new HashMap<>();
        if (title != null) body.put("title", title);
        if (coverUrl != null) body.put("coverUrl", coverUrl);
        return ApiCall.execute(api.update(id, body));
    }

    @Override
    public Result<Object> setBackground(String id, String backgroundUrl) {
        Map<String, String> body = new HashMap<>();
        body.put("backgroundUrl", backgroundUrl);
        return ApiCall.execute(api.setBackground(id, body));
    }

    @Override
    public Result<Object> setFrame(String id, String roomCardUrl) {
        Map<String, String> body = new HashMap<>();
        body.put("roomCardUrl", roomCardUrl);
        return ApiCall.execute(api.setFrame(id, body));
    }
}
