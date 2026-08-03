package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.zego.ZegoEngineManager;

/**
 * Process-wide live-room session (Mikoo RoomDataManager analogue).
 * Minimize finishes the Activity UI but keeps this + Zego/realtime/FGS alive.
 * Re-opening the same room restores UI without HTTP/Zego rejoin.
 */
public final class ActiveRoomSession {
    private static final ActiveRoomSession INSTANCE = new ActiveRoomSession();

    private String roomId;
    private String zegoRoomId;
    private RoomDtos.JoinRoomResult session;
    private RoomDtos.RoomDto latestRoom;
    private boolean minimized;
    private boolean host;
    private boolean agencyRoom;
    private boolean micOn;
    private boolean speakerMuted;

    private ActiveRoomSession() {}

    public static ActiveRoomSession get() {
        return INSTANCE;
    }

    public synchronized void capture(
            @Nullable String roomId,
            @Nullable RoomDtos.JoinRoomResult session,
            @Nullable RoomDtos.RoomDto room,
            boolean host,
            boolean agencyRoom,
            boolean micOn,
            boolean speakerMuted
    ) {
        if (roomId == null || roomId.isEmpty() || session == null) return;
        this.roomId = roomId;
        this.session = session;
        this.zegoRoomId = session.zegoRoomId != null && !session.zegoRoomId.isEmpty()
                ? session.zegoRoomId : roomId;
        if (room != null) this.latestRoom = room;
        else if (session.room != null) this.latestRoom = session.room;
        this.host = host;
        this.agencyRoom = agencyRoom;
        this.micOn = micOn;
        this.speakerMuted = speakerMuted;
    }

    public synchronized void updateRoom(@Nullable RoomDtos.RoomDto room) {
        if (room != null) latestRoom = room;
    }

    public synchronized void setMinimized(boolean value) {
        minimized = value;
    }

    public synchronized boolean isMinimized() {
        return minimized;
    }

    public synchronized void setMicOn(boolean value) {
        micOn = value;
    }

    public synchronized void setSpeakerMuted(boolean value) {
        speakerMuted = value;
    }

    @Nullable
    public synchronized String roomId() {
        return roomId;
    }

    @Nullable
    public synchronized RoomDtos.JoinRoomResult session() {
        return session;
    }

    @Nullable
    public synchronized RoomDtos.RoomDto latestRoom() {
        return latestRoom;
    }

    public synchronized boolean isHost() {
        return host;
    }

    public synchronized boolean isAgencyRoom() {
        return agencyRoom;
    }

    public synchronized boolean isMicOn() {
        return micOn;
    }

    public synchronized boolean isSpeakerMuted() {
        return speakerMuted;
    }

    /**
     * Same API room + Zego still logged in (minimize / Home / notification reopen).
     * Prefer this over HTTP join so UI restores instantly like Mikoo.
     */
    public synchronized boolean canResumeUi(@Nullable String targetRoomId) {
        if (targetRoomId == null || targetRoomId.isEmpty()) return false;
        if (session == null || roomId == null || !roomId.equals(targetRoomId)) return false;
        ZegoEngineManager zego = ZegoEngineManager.getInstance();
        String zegoId = zegoRoomId != null ? zegoRoomId : roomId;
        return zego.isInRoom(zegoId) || zego.isInRoom(roomId);
    }

    /** Snapshot flags for UI restore without inventing a new join. */
    public synchronized void syncFlags(boolean host, boolean agencyRoom, boolean micOn, boolean speakerMuted) {
        this.host = host;
        this.agencyRoom = agencyRoom;
        this.micOn = micOn;
        this.speakerMuted = speakerMuted;
    }

    public synchronized boolean matches(@Nullable String targetRoomId) {
        return targetRoomId != null && targetRoomId.equals(roomId) && session != null;
    }

    public synchronized void clear() {
        roomId = null;
        zegoRoomId = null;
        session = null;
        latestRoom = null;
        minimized = false;
        host = false;
        agencyRoom = false;
        micOn = false;
        speakerMuted = false;
    }
}
