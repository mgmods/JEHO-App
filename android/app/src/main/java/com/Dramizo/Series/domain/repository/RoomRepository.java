package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;

public interface RoomRepository {
    Result<RoomDtos.MusicLibraryDto> musicLibrary();
    Result<RoomDtos.InternetMusicSearchDto> musicSearch(String query);
    Result<RoomDtos.InternetMusicResolvedDto> musicResolve(String videoId);

    Result<MiscDtos.ListResult<RoomDtos.RoomDto>> list(int page);
    Result<MiscDtos.ListResult<RoomDtos.RoomDto>> list(int page, int limit);
    Result<RoomDtos.JoinRoomResult> create(RoomDtos.CreateRoomRequest request);
    Result<RoomDtos.RoomDto> get(String id);
    Result<RoomDtos.SupportersResult> supporters(String id);
    Result<Object> reportUser(String id, String targetUserId, String reason, String description);
    Result<java.util.Map<String, Object>> updateMusic(
            String id, String action, String url, String title, String artist, Long positionMs);
    Result<RoomDtos.JoinRoomResult> join(String id, String password);
    Result<Object> raiseHand(String id, boolean raised);
    Result<Object> raiseHand(String id, boolean raised, Integer seatIndex);
    Result<RoomDtos.SeatRequestsResult> seatRequests(String id);
    Result<RoomDtos.RoomDto> approveSeat(String id, String userId, Integer seatIndex);
    Result<Object> rejectSeat(String id, String userId);
    Result<Object> inviteSeat(String id, String userId, Integer seatIndex);
    Result<java.util.Map<String, Object>> inviteTaskGuest(String id, String userId);
    Result<RoomDtos.RoomDto> respondSeatInvite(String id, boolean accept);
    Result<RoomDtos.RoomDto> lock(String id, boolean locked, String password);
    Result<RoomDtos.RoomDto> setGiftSounds(String id, boolean enabled);
    Result<RoomDtos.RoomDto> setDisplaySettings(String id, java.util.Map<String, Boolean> patch);
    Result<java.util.Map<String, Object>> clearPublicChat(String id);
    Result<RoomDtos.RoomDto> setChatAutoClearMinutes(String id, int minutes);
    Result<Object> setMic(String id, boolean muted);
    Result<Object> setMic(String id, boolean muted, String targetUserId);
    Result<RoomDtos.RoomDto> takeSeat(String id, int seatIndex);
    Result<RoomDtos.RoomDto> leaveSeat(String id);
    Result<RoomDtos.RoomDto> lockSeat(String id, int seatIndex, boolean locked);
    Result<RoomDtos.RoomDto> resizeSeats(String id, int seatCount);
    Result<Object> kick(String id, String userId, String reason);
    Result<Object> ban(String id, String userId, String reason, Integer durationMinutes);
    Result<Object> unban(String id, String userId);
    Result<Boolean> isBanned(String id, String userId);
    Result<Object> setCohost(String id, String targetUserId);
    Result<Object> addModerator(String id, String targetUserId);
    Result<Object> removeModerator(String id, String targetUserId);
    Result<Object> updateModeratorPermissions(
            String id, String targetUserId, boolean canManageMusic,
            boolean canChangeFrames, boolean canControlGames,
            boolean canMute, boolean canKick, boolean canBan,
            boolean canManageSeats, boolean canInvite, boolean canManageRoom);
    Result<Object> leave(String id);
    Result<Object> close(String id);
    Result<RoomDtos.RoomDto> update(String id, String title, String coverUrl);
    Result<Object> setBackground(String id, String backgroundUrl);
    Result<Object> setFrame(String id, String roomCardUrl);
}
