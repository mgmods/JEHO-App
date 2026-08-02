package com.Dramizo.Series.util;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Room chat for the active live session.
 * Survives Activity recreate and process restart while the same roomId is live.
 * Cleared only when the broadcast ends.
 */
public final class RoomChatMemory {
    private static final int SOFT_CAP = 800;
    private static final String DIR = "room_chat_sessions";
    private static final Map<String, List<Line>> BY_ROOM =
            Collections.synchronizedMap(new LinkedHashMap<>());
    @Nullable private static Context appCtx;

    private RoomChatMemory() {}

    public static void init(@Nullable Context context) {
        if (context == null) return;
        appCtx = context.getApplicationContext();
    }

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
        ensureLoaded(roomId);
        synchronized (BY_ROOM) {
            List<Line> list = BY_ROOM.get(roomId);
            if (list == null) {
                list = new ArrayList<>();
                BY_ROOM.put(roomId, list);
            }
            list.add(line);
            while (list.size() > SOFT_CAP) list.remove(0);
            persistLocked(roomId, list);
        }
    }

    public static List<Line> snapshot(@Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return Collections.emptyList();
        ensureLoaded(roomId);
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
        File f = sessionFile(roomId);
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void ensureLoaded(String roomId) {
        synchronized (BY_ROOM) {
            if (BY_ROOM.containsKey(roomId)) return;
            List<Line> loaded = readDisk(roomId);
            BY_ROOM.put(roomId, loaded != null ? loaded : new ArrayList<>());
        }
    }

    private static void persistLocked(String roomId, List<Line> list) {
        Context ctx = appCtx;
        if (ctx == null || list == null) return;
        try {
            File dir = new File(ctx.getFilesDir(), DIR);
            if (!dir.exists() && !dir.mkdirs()) return;
            JSONArray arr = new JSONArray();
            for (Line line : list) {
                if (line == null) continue;
                JSONObject o = new JSONObject();
                o.put("name", line.name != null ? line.name : "");
                o.put("text", line.text != null ? line.text : "");
                o.put("vipLevel", line.vipLevel);
                o.put("userLevel", line.userLevel);
                o.put("frameUrl", line.frameUrl != null ? line.frameUrl : "");
                o.put("userId", line.userId != null ? line.userId : "");
                o.put("avatarUrl", line.avatarUrl != null ? line.avatarUrl : "");
                o.put("giftIconUrl", line.giftIconUrl != null ? line.giftIconUrl : "");
                o.put("wealthScore", line.wealthScore);
                o.put("charmScore", line.charmScore);
                arr.put(o);
            }
            File out = new File(dir, safeName(roomId) + ".json");
            File tmp = new File(dir, safeName(roomId) + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(out)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static List<Line> readDisk(String roomId) {
        File f = sessionFile(roomId);
        if (f == null || !f.exists() || f.length() <= 2) return null;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            List<Line> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new Line(
                        o.optString("name", ""),
                        o.optString("text", ""),
                        o.optInt("vipLevel", 0),
                        o.optInt("userLevel", 1),
                        emptyToNull(o.optString("frameUrl", "")),
                        emptyToNull(o.optString("userId", "")),
                        emptyToNull(o.optString("avatarUrl", "")),
                        emptyToNull(o.optString("giftIconUrl", "")),
                        o.optLong("wealthScore", 0L),
                        o.optLong("charmScore", 0L)
                ));
            }
            while (out.size() > SOFT_CAP) out.remove(0);
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static File sessionFile(String roomId) {
        Context ctx = appCtx;
        if (ctx == null || roomId == null || roomId.isEmpty()) return null;
        return new File(new File(ctx.getFilesDir(), DIR), safeName(roomId) + ".json");
    }

    private static String safeName(String roomId) {
        return roomId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    @Nullable
    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }
}
