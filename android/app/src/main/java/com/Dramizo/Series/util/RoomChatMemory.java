package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory room chat for the active live session.
 * Survives Activity recreate; cleared only when the broadcast ends.
 */
public final class RoomChatMemory {
    private static final int SOFT_CAP = 800;
    private static final Map<String, List<Line>> BY_ROOM =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private RoomChatMemory() {}

    public static final class Line {
        public final String name;
        public final String text;
        public final int vipLevel;
        public final int userLevel;
        public final String frameUrl;
        public final String userId;
        public final String avatarUrl;
        public final String giftIconUrl;
        public final long wealthScore;
        public final long charmScore;

        public Line(
                String name,
                String text,
                int vipLevel,
                int userLevel,
                String frameUrl,
                String userId,
                String avatarUrl,
                String giftIconUrl,
                long wealthScore,
                long charmScore
        ) {
            this.name = name;
            this.text = text;
            this.vipLevel = vipLevel;
            this.userLevel = userLevel;
            this.frameUrl = frameUrl;
            this.userId = userId;
            this.avatarUrl = avatarUrl;
            this.giftIconUrl = giftIconUrl;
            this.wealthScore = wealthScore;
            this.charmScore = charmScore;
        }
    }

    public static void append(String roomId, Line line) {
        if (roomId == null || roomId.isEmpty() || line == null) return;
        synchronized (BY_ROOM) {
            List<Line> list = BY_ROOM.get(roomId);
            if (list == null) {
                list = new ArrayList<>();
                BY_ROOM.put(roomId, list);
            }
            list.add(line);
            while (list.size() > SOFT_CAP) list.remove(0);
        }
    }

    public static List<Line> snapshot(@Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return Collections.emptyList();
        synchronized (BY_ROOM) {
            List<Line> list = BY_ROOM.get(roomId);
            if (list == null || list.isEmpty()) return Collections.emptyList();
            return new ArrayList<>(list);
        }
    }

    /** Wipe chat for a room — call only when ending the broadcast. */
    public static void clear(@Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return;
        synchronized (BY_ROOM) {
            BY_ROOM.remove(roomId);
        }
    }
}
