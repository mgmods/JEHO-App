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
 * Room public chat for the active live session.
 * Survives Activity recreate / minimize; wiped on staff clear, auto-clear, or end of live.
 * Disk persists are debounced on a worker thread so append from UI does not rewrite JSON
 * on the main thread (ANR risk on mid-range MIUI devices).
 */
public final class RoomChatMemory {
    private static final int SOFT_CAP = 800;
    private static final String DIR = "room_chat_sessions";
    private static final Map<String, List<Line>> BY_ROOM =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, Long> CLEARED_AT =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, Long> PERSIST_SEQ =
            Collections.synchronizedMap(new LinkedHashMap<>());
    @Nullable private static Context appCtx;
    @Nullable private static java.util.concurrent.ExecutorService diskIo;

    private RoomChatMemory() {}

    public static void init(@Nullable Context context) {
        if (context == null) return;
        appCtx = context.getApplicationContext();
    }

    private static java.util.concurrent.ExecutorService diskIo() {
        if (diskIo == null) {
            synchronized (RoomChatMemory.class) {
                if (diskIo == null) {
                    diskIo = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "room-chat-disk");
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });
                }
            }
        }
        return diskIo;
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
        }
        // Never rewrite session JSON on the caller (often main) thread.
        schedulePersist(roomId);
    }

    /** Best-effort flush of pending lines without blocking the UI for long. */
    private static void schedulePersist(String roomId) {
        final long seq;
        synchronized (PERSIST_SEQ) {
            Long prev = PERSIST_SEQ.get(roomId);
            seq = (prev != null ? prev : 0L) + 1L;
            PERSIST_SEQ.put(roomId, seq);
        }
        diskIo().execute(() -> {
            try {
                Thread.sleep(180L); // coalesce bursts during join welcome + floods
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Long latest;
            synchronized (PERSIST_SEQ) {
                latest = PERSIST_SEQ.get(roomId);
            }
            if (latest == null || latest != seq) return; // superseded
            List<Line> copy;
            synchronized (BY_ROOM) {
                List<Line> list = BY_ROOM.get(roomId);
                if (list == null) return;
                copy = new ArrayList<>(list);
            }
            persistLocked(roomId, copy);
        });
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

    /**
     * Full wipe on this phone: memory + disk session file + tmp leftovers.
     * Call when staff clears, auto-clear fires, or live ends.
     */
    public static void clear(@Nullable String roomId) {
        clear(roomId, System.currentTimeMillis());
    }

    public static void clear(@Nullable String roomId, long clearedAtMs) {
        if (roomId == null || roomId.isEmpty()) return;
        long stamp = clearedAtMs > 0 ? clearedAtMs : System.currentTimeMillis();
        synchronized (BY_ROOM) {
            BY_ROOM.remove(roomId);
            CLEARED_AT.put(roomId, stamp);
        }
        deleteSessionArtifacts(roomId);
        writeClearedMeta(roomId, stamp);
    }

    /**
     * Honor server chatClearedAt so late-join / re-open does not restore pre-wipe messages.
     */
    public static void honorServerWipe(@Nullable String roomId, long serverClearedAtMs) {
        if (roomId == null || roomId.isEmpty() || serverClearedAtMs <= 0) return;
        long local = clearedEpoch(roomId);
        if (serverClearedAtMs > local) {
            clear(roomId, serverClearedAtMs);
        }
    }

    public static long clearedEpoch(@Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return 0L;
        Long mem = CLEARED_AT.get(roomId);
        if (mem != null && mem > 0) return mem;
        long disk = readClearedMeta(roomId);
        if (disk > 0) {
            CLEARED_AT.put(roomId, disk);
            return disk;
        }
        return 0L;
    }

    private static void deleteSessionArtifacts(String roomId) {
        File session = sessionFile(roomId);
        if (session != null && session.exists()) {
            //noinspection ResultOfMethodCallIgnored
            session.delete();
        }
        File tmp = sessionTmpFile(roomId);
        if (tmp != null && tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        // Wipe any legacy name variants under the sessions dir matching this room.
        Context ctx = appCtx;
        if (ctx == null) return;
        File dir = new File(ctx.getFilesDir(), DIR);
        if (!dir.isDirectory()) return;
        String base = safeName(roomId);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            String n = f.getName();
            if (n.startsWith(base + ".") || n.equals(base + ".json") || n.equals(base + ".tmp")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static void ensureLoaded(String roomId) {
        synchronized (BY_ROOM) {
            if (BY_ROOM.containsKey(roomId)) return;
            long cleared = readClearedMeta(roomId);
            if (cleared > 0) CLEARED_AT.put(roomId, cleared);
            List<Line> loaded = readDisk(roomId);
            // Drop disk cache if server wipe stamp is newer than file mtime.
            File f = sessionFile(roomId);
            if (f != null && f.exists() && cleared > 0 && f.lastModified() < cleared) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                loaded = new ArrayList<>();
            }
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

    @Nullable
    private static File sessionTmpFile(String roomId) {
        Context ctx = appCtx;
        if (ctx == null || roomId == null || roomId.isEmpty()) return null;
        return new File(new File(ctx.getFilesDir(), DIR), safeName(roomId) + ".tmp");
    }

    private static void writeClearedMeta(String roomId, long ms) {
        Context ctx = appCtx;
        if (ctx == null) return;
        try {
            File dir = new File(ctx.getFilesDir(), DIR);
            if (!dir.exists() && !dir.mkdirs()) return;
            File meta = new File(dir, safeName(roomId) + ".cleared");
            try (FileOutputStream fos = new FileOutputStream(meta)) {
                fos.write(String.valueOf(ms).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static long readClearedMeta(String roomId) {
        Context ctx = appCtx;
        if (ctx == null) return 0L;
        try {
            File meta = new File(new File(ctx.getFilesDir(), DIR), safeName(roomId) + ".cleared");
            if (!meta.exists() || meta.length() == 0) return 0L;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(meta), StandardCharsets.UTF_8))) {
                String s = br.readLine();
                if (s == null) return 0L;
                return Long.parseLong(s.trim());
            }
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String safeName(String roomId) {
        return roomId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    @Nullable
    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }
}
