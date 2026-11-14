package com.Dramizo.Series.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Small process-local cache of active PK room IDs for home room-card badges. */
public final class RoomPkState {
    private static final Set<String> ACTIVE_ROOM_IDS = new HashSet<>();
    private RoomPkState() {}

    public static synchronized void replaceActiveRooms(Set<String> ids) {
        ACTIVE_ROOM_IDS.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.trim().isEmpty()) ACTIVE_ROOM_IDS.add(id.trim());
            }
        }
    }

    public static synchronized boolean isRoomChallenging(String roomId) {
        return roomId != null && ACTIVE_ROOM_IDS.contains(roomId);
    }

    public static synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(ACTIVE_ROOM_IDS));
    }
}
