package com.Dramizo.Series.realtime;

import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.client.Ack;
import io.socket.emitter.Emitter;

/**
 * Socket.IO client for AuraLive /realtime namespace (rooms + DM chat).
 */
public class RealtimeClient {
    public interface JoinCallback {
        void onResult(boolean success, String error, @Nullable org.json.JSONObject profile);
    }

    private static final String TAG = "RealtimeClient";
    private static RealtimeClient instance;
    private static final Gson GSON = new Gson();
    private static final long JOIN_EVENT_DEDUPE_MS = 8_000L;

    public interface RoomListener {
        void onRoomEvent(String roomId, String event, JsonObject payload, String fromUserId, String fromUsername);
        void onUserJoined(String roomId, String userId, String username);
        /** Authoritative HTML entry-effect contract. */
        default void onUserJoined(String roomId, String userId, String username, int vipLevel,
                                  String entryEffectUrl,
                                  String entryAnimationUrl, String avatarUrl, String displayName,
                                  String supporterTier, int userLevel,
                                  String vipBadgeUrl, String levelBadgeUrl, String hostBadgeUrl,
                                  boolean isHost, long wealthScore, long totalSentCoins,
                                  long roomSpendCoins, int effectPriority, String renderMode,
                                  float aspectRatio, float safeLeft, float safeTop,
                                  float safeRight, float safeBottom, long durationMs) {
            onUserJoined(roomId, userId, username, vipLevel, entryEffectUrl, entryAnimationUrl,
                    avatarUrl, displayName, supporterTier, userLevel, vipBadgeUrl, levelBadgeUrl,
                    hostBadgeUrl, isHost, wealthScore, totalSentCoins, roomSpendCoins,
                    effectPriority, renderMode, aspectRatio, safeLeft, safeTop, safeRight,
                    safeBottom, durationMs, false);
        }
        default void onUserJoined(String roomId, String userId, String username, int vipLevel,
                                  String entryEffectUrl,
                                  String entryAnimationUrl, String avatarUrl, String displayName,
                                  String supporterTier, int userLevel,
                                  String vipBadgeUrl, String levelBadgeUrl, String hostBadgeUrl,
                                  boolean isHost, long wealthScore, long totalSentCoins,
                                  long roomSpendCoins, int effectPriority, String renderMode,
                                  float aspectRatio, float safeLeft, float safeTop,
                                  float safeRight, float safeBottom, long durationMs,
                                  boolean showHiBadge) {
            onUserJoined(roomId, userId,
                    displayName != null && !displayName.isEmpty() ? displayName : username);
        }
        void onUserLeft(String roomId, String userId);
        default void onRoomMembers(String roomId, JsonArray members) {}
        default void onConnected() {}
        default void onDisconnected() {}
    }

    public interface ChatListener {
        void onChatMessage(String conversationId, ChatDtos.MessageDto message);
        void onConversationUpdated(String conversationId, ChatDtos.MessageDto lastMessage, int unreadCount);
        void onTyping(String conversationId, String userId, boolean isTyping);
        default void onMessageEdited(String conversationId, ChatDtos.MessageDto message) {}
        default void onMessageUnsent(String conversationId, String messageId) {}
        default void onChatRead(String conversationId, String readerId, String lastReadAt) {}
        default void onConversationDeleted(String conversationId) {}
        default void onConnected() {}
        default void onDisconnected() {}
    }

    public interface UserListener {
        default void onUserEvent(String event, JsonObject payload) {}
        default void onPresenceOnline(String userId, String username) {}
        default void onPresenceOffline(String userId) {}
        default void onConnected() {}
        default void onDisconnected() {}
    }

    private Socket socket;
    private String token;
    private final CopyOnWriteArrayList<RoomListener> roomListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChatListener> chatListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UserListener> userListeners = new CopyOnWriteArrayList<>();
    private final Set<String> joinedRooms = new HashSet<>();
    private final Set<String> joinedConversations = new HashSet<>();
    private final Map<String, Long> recentJoinEvents = new HashMap<>();

    public static synchronized RealtimeClient getInstance() {
        if (instance == null) instance = new RealtimeClient();
        return instance;
    }

    public static String socketOrigin() {
        return com.Dramizo.Series.util.ApiOrigin.origin();
    }

    public synchronized void connect(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            Log.w(TAG, "No access token — realtime skipped");
            return;
        }
        if (socket != null && socket.connected() && accessToken.equals(token)) return;
        disconnect(false);
        token = accessToken;
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.reconnectionDelay = 1500;
            opts.transports = new String[]{"websocket", "polling"};
            Map<String, String> auth = new HashMap<>();
            auth.put("token", accessToken);
            opts.auth = auth;

            String url = socketOrigin() + "/realtime";
            socket = IO.socket(url, opts);
            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            socket.on("room:event", onRoomEvent);
            socket.on("room:background", onRoomBackground);
            socket.on("room:user_joined", onUserJoined);
            socket.on("room:user_left", onUserLeft);
            socket.on("room:members", onRoomMembers);
            socket.on("chat:message", onChatMessage);
            socket.on("chat:message:edited", onMessageEdited);
            socket.on("chat_message_edited", onMessageEdited);
            socket.on("chat:message:unsent", onMessageUnsent);
            socket.on("chat_message_unsent", onMessageUnsent);
            socket.on("chat:conversation:updated", onConversationUpdated);
            socket.on("chat:conversation:deleted", onConversationDeleted);
            socket.on("chat:typing", onTyping);
            socket.on("chat:read", onChatRead);
            socket.on("presence:online", onPresenceOnline);
            socket.on("presence:offline", onPresenceOffline);
            socket.on("match:found", onMatchFound);
            socket.on("match:update", onMatchUpdate);
            socket.on("game:update", onGameUpdate);
            socket.on("game:invite", onGameInvite);
            socket.on("lucky-wheel:spin", onLuckyWheelSpin);
            socket.on("dice:roll", onDiceRoll);
            socket.on("room:slot_active", onSlotActive);
            socket.on("room:slot_play", onSlotPlay);
            socket.on("room:slot_win", onSlotWin);
            socket.on("room:slot_lose", onSlotLose);
            socket.on("room:slot_ended", onSlotEnded);
            socket.on("room:gift_sounds", onRoomGiftSounds);
            socket.on("task:progress", onTaskProgress);
            socket.on("contest:score", onContestScore);
            socket.on("social:request", onSocialRequest);
            socket.on("social:request_responded", onSocialResponded);
            socket.on("notification:new", onNotificationNew);
            socket.on("celebration:toast", onCelebrationToast);
            socket.on("account:restricted", onAccountRestricted);
            socket.connect();
            Log.i(TAG, "Connecting to " + url);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Realtime connect failed", e);
        }
    }

    /** Prefer {@link #addRoomListener(RoomListener)} so chat + room can coexist. */
    @Deprecated
    public void setRoomListener(RoomListener listener) {
        roomListeners.clear();
        if (listener != null) roomListeners.add(listener);
    }

    public void addRoomListener(RoomListener listener) {
        if (listener != null && !roomListeners.contains(listener)) roomListeners.add(listener);
    }

    public void removeRoomListener(RoomListener listener) {
        roomListeners.remove(listener);
    }

    public void addChatListener(ChatListener listener) {
        if (listener != null && !chatListeners.contains(listener)) chatListeners.add(listener);
    }

    public void removeChatListener(ChatListener listener) {
        chatListeners.remove(listener);
    }

    public void addUserListener(UserListener listener) {
        if (listener != null && !userListeners.contains(listener)) userListeners.add(listener);
    }

    public void removeUserListener(UserListener listener) {
        userListeners.remove(listener);
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void joinRoom(String roomId) {
        joinRoom(roomId, null, null, 0, 1, null);
    }

    public void joinRoom(String roomId, String displayName, String avatarUrl, int vipLevel,
                         int userLevel,
                         JoinCallback callback) {
        if (roomId == null || roomId.isEmpty() || socket == null) {
            if (callback != null) callback.onResult(false, "Socket is unavailable", null);
            return;
        }
        // Always emit room:join — skipping when joinedRooms is stale prevents entry effects
        // and leaves the socket outside the room after process death / incomplete leave.
        try {
            JSONObject body = new JSONObject();
            body.put("roomId", roomId);
            if (displayName != null && !displayName.isEmpty()) body.put("displayName", displayName);
            if (avatarUrl != null && !avatarUrl.isEmpty()) body.put("avatarUrl", avatarUrl);
            body.put("vipLevel", Math.max(0, vipLevel));
            body.put("userLevel", Math.max(1, userLevel));
            socket.emit("room:join", body, (Ack) args -> {
                JSONObject response =
                        args != null && args.length > 0 && args[0] instanceof JSONObject
                                ? (JSONObject) args[0] : null;
                String error = response != null ? response.optString("error", null) : null;
                boolean success = response != null
                        && !response.isNull("joined")
                        && (error == null || error.isEmpty());
                if (success) joinedRooms.add(roomId);
                JSONObject profile = null;
                if (success && response != null && !response.isNull("profile")) {
                    Object raw = response.opt("profile");
                    if (raw instanceof JSONObject) profile = (JSONObject) raw;
                }
                if (callback != null) callback.onResult(success, error, profile);
            });
            Log.d(TAG, "room:join " + roomId);
        } catch (JSONException e) {
            Log.w(TAG, "joinRoom failed", e);
            if (callback != null) callback.onResult(false, e.getMessage(), null);
        }
    }

    public void leaveRoom(String roomId) {
        if (roomId == null || roomId.isEmpty() || socket == null) return;
        if (!joinedRooms.remove(roomId)) return;
        try {
            JSONObject body = new JSONObject();
            body.put("roomId", roomId);
            socket.emit("room:leave", body);
        } catch (JSONException e) {
            Log.w(TAG, "leaveRoom failed", e);
        }
    }

    public void joinConversation(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return;
        joinedConversations.add(conversationId);
        if (socket == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("conversationId", conversationId);
            socket.emit("chat:join", body);
            Log.d(TAG, "chat:join " + conversationId);
        } catch (JSONException e) {
            Log.w(TAG, "joinConversation failed", e);
        }
    }

    public void leaveConversation(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return;
        joinedConversations.remove(conversationId);
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("conversationId", conversationId);
            socket.emit("chat:leave", body);
        } catch (JSONException e) {
            Log.w(TAG, "leaveConversation failed", e);
        }
    }

    public void emitTyping(String conversationId, boolean isTyping) {
        if (conversationId == null || socket == null || !socket.connected()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("conversationId", conversationId);
            body.put("isTyping", isTyping);
            socket.emit("chat:typing", body);
        } catch (JSONException e) {
            Log.w(TAG, "emitTyping failed", e);
        }
    }

    /** Ask server whether {@code userId} is currently socket-online. */
    public void checkPresence(String userId, PresenceCallback callback) {
        if (callback == null) return;
        if (userId == null || userId.isEmpty() || socket == null || !socket.connected()) {
            callback.onResult(userId, false);
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            socket.emit("presence:check", body, (io.socket.client.Ack) args -> {
                boolean online = false;
                try {
                    if (args != null && args.length > 0 && args[0] != null) {
                        JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
                        online = data.has("online") && data.get("online").getAsBoolean();
                    }
                } catch (Exception ignored) {
                }
                callback.onResult(userId, online);
            });
        } catch (Exception e) {
            Log.w(TAG, "checkPresence failed", e);
            callback.onResult(userId, false);
        }
    }

    public interface PresenceCallback {
        void onResult(String userId, boolean online);
    }

    /**
     * Emits a room event immediately.
     *
     * @return {@code false} when the socket is disconnected or the payload could not be emitted.
     */
    public boolean emitRoomEvent(String roomId, String event, JsonObject payload) {
        if (roomId == null || event == null || socket == null || !socket.connected()) return false;
        try {
            JSONObject body = new JSONObject();
            body.put("roomId", roomId);
            body.put("event", event);
            if (payload != null) {
                body.put("payload", new JSONObject(payload.toString()));
            }
            socket.emit("room:event", body);
            return true;
        } catch (JSONException e) {
            Log.w(TAG, "emitRoomEvent failed", e);
            return false;
        }
    }

    public synchronized void disconnect() {
        disconnect(true);
    }

    private synchronized void disconnect(boolean clearToken) {
        joinedRooms.clear();
        joinedConversations.clear();
        if (socket != null) {
            socket.off();
            socket.disconnect();
            socket.close();
            socket = null;
        }
        if (clearToken) token = null;
    }

    private final Emitter.Listener onConnect = args -> {
        Log.i(TAG, "Connected");
        for (RoomListener l : roomListeners) l.onConnected();
        for (ChatListener l : chatListeners) l.onConnected();
        for (UserListener l : userListeners) l.onConnected();
        for (String roomId : new HashSet<>(joinedRooms)) {
            try {
                JSONObject body = new JSONObject();
                body.put("roomId", roomId);
                if (socket != null) socket.emit("room:join", body);
            } catch (JSONException ignored) {
            }
        }
        for (String cid : new HashSet<>(joinedConversations)) {
            try {
                JSONObject body = new JSONObject();
                body.put("conversationId", cid);
                if (socket != null) socket.emit("chat:join", body);
            } catch (JSONException ignored) {
            }
        }
    };

    private final Emitter.Listener onDisconnect = args -> {
        Log.i(TAG, "Disconnected");
        for (RoomListener l : roomListeners) l.onDisconnected();
        for (ChatListener l : chatListeners) l.onDisconnected();
        for (UserListener l : userListeners) l.onDisconnected();
    };

    private final Emitter.Listener onRoomEvent = args -> {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = data.has("roomId") ? data.get("roomId").getAsString() : null;
            String event = data.has("event") ? data.get("event").getAsString() : null;
            JsonObject payload = data.has("payload") && data.get("payload").isJsonObject()
                    ? data.getAsJsonObject("payload") : null;
            String fromUserId = null;
            String fromUsername = null;
            if (data.has("from") && data.get("from").isJsonObject()) {
                JsonObject from = data.getAsJsonObject("from");
                if (from.has("userId")) fromUserId = from.get("userId").getAsString();
                if (from.has("username")) fromUsername = from.get("username").getAsString();
            }
            for (RoomListener l : roomListeners) {
                l.onRoomEvent(roomId, event, payload, fromUserId, fromUsername);
            }
        } catch (Exception e) {
            Log.w(TAG, "room:event parse error", e);
        }
    };

    private final Emitter.Listener onRoomBackground = args -> {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject payload = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = payload.has("roomId") ? payload.get("roomId").getAsString() : null;
            for (RoomListener l : roomListeners) {
                l.onRoomEvent(roomId, "room:background", payload, null, null);
            }
        } catch (Exception e) {
            Log.w(TAG, "room:background parse error", e);
        }
    };

    private final Emitter.Listener onLuckyWheelSpin =
            args -> dispatchRoomBroadcast(args, "lucky-wheel:spin");
    private final Emitter.Listener onDiceRoll =
            args -> dispatchRoomBroadcast(args, "dice:roll");
    private final Emitter.Listener onSlotActive =
            args -> dispatchRoomBroadcast(args, "room:slot_active");
    private final Emitter.Listener onSlotPlay =
            args -> dispatchRoomBroadcast(args, "room:slot_play");
    private final Emitter.Listener onSlotWin =
            args -> dispatchRoomBroadcast(args, "room:slot_win");
    private final Emitter.Listener onSlotLose =
            args -> dispatchRoomBroadcast(args, "room:slot_lose");
    private final Emitter.Listener onSlotEnded =
            args -> dispatchRoomBroadcast(args, "room:slot_ended");
    private final Emitter.Listener onRoomGiftSounds =
            args -> dispatchRoomBroadcast(args, "room:gift_sounds");

    private void dispatchRoomBroadcast(Object[] args, String event) {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject payload =
                    JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = null;
            if (payload.has("roomId") && !payload.get("roomId").isJsonNull()) {
                roomId = payload.get("roomId").getAsString();
            }
            // Slot emits often omit roomId — fan-out to every joined voice room.
            if (roomId == null || roomId.isEmpty()) {
                for (String joined : new HashSet<>(joinedRooms)) {
                    for (RoomListener listener : roomListeners) {
                        listener.onRoomEvent(joined, event, payload, null, null);
                    }
                }
                return;
            }
            for (RoomListener listener : roomListeners) {
                listener.onRoomEvent(roomId, event, payload, null, null);
            }
        } catch (Exception error) {
            Log.w(TAG, event + " parse error", error);
        }
    }

    private final Emitter.Listener onUserJoined = args -> {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = data.has("roomId") ? data.get("roomId").getAsString() : null;
            String userId = data.has("userId") ? data.get("userId").getAsString() : null;
            if (isDuplicateJoinEvent(roomId, userId)) return;
            String username = data.has("username") && !data.get("username").isJsonNull()
                    ? data.get("username").getAsString() : null;
            String displayName = data.has("displayName") && !data.get("displayName").isJsonNull()
                    ? data.get("displayName").getAsString() : username;
            String avatarUrl = data.has("avatarUrl") && !data.get("avatarUrl").isJsonNull()
                    ? data.get("avatarUrl").getAsString() : null;
            String entryEffectUrl = data.has("entryEffectUrl") && !data.get("entryEffectUrl").isJsonNull()
                    ? data.get("entryEffectUrl").getAsString() : null;
            String entryAnimationUrl = data.has("entryAnimationUrl") && !data.get("entryAnimationUrl").isJsonNull()
                    ? data.get("entryAnimationUrl").getAsString()
                    : data.has("animationUrl") && !data.get("animationUrl").isJsonNull()
                    ? data.get("animationUrl").getAsString() : null;
            int vipLevel = data.has("vipLevel") && !data.get("vipLevel").isJsonNull()
                    ? data.get("vipLevel").getAsInt() : 0;
            int userLevel = data.has("userLevel") && !data.get("userLevel").isJsonNull()
                    ? Math.max(1, data.get("userLevel").getAsInt()) : 1;
            String supporterTier = data.has("supporterTier") && !data.get("supporterTier").isJsonNull()
                    ? data.get("supporterTier").getAsString() : "normal";
            String vipBadgeUrl = data.has("vipBadgeUrl") && !data.get("vipBadgeUrl").isJsonNull()
                    ? data.get("vipBadgeUrl").getAsString() : null;
            String levelBadgeUrl = data.has("levelBadgeUrl") && !data.get("levelBadgeUrl").isJsonNull()
                    ? data.get("levelBadgeUrl").getAsString() : null;
            String hostBadgeUrl = nullableString(data, "hostBadgeUrl");
            boolean isHost = data.has("isHost") && !data.get("isHost").isJsonNull()
                    && data.get("isHost").getAsBoolean();
            long wealthScore = safeLong(data, "wealthScore", safeLong(data, "totalSentCoins", 0L));
            long totalSentCoins = safeLong(data, "totalSentCoins", wealthScore);
            long roomSpendCoins = safeLong(data, "roomSpendCoins", 0L);
            int effectPriority = (int) safeLong(data, "effectPriority", 0L);
            String renderMode = nullableString(data, "renderMode");
            float aspectRatio = safeFloat(data, "aspectRatio", 0f);
            long durationMs = safeLong(data, "durationMs", 0L);
            boolean showHiBadge = data.has("showHiBadge") && !data.get("showHiBadge").isJsonNull()
                    && data.get("showHiBadge").getAsBoolean()
                    || data.has("isFirstDay") && !data.get("isFirstDay").isJsonNull()
                    && data.get("isFirstDay").getAsBoolean();
            float safeLeft = 0f, safeTop = 0f, safeRight = 0f, safeBottom = 0f;
            if (data.has("textSafeArea") && data.get("textSafeArea").isJsonObject()) {
                JsonObject safe = data.getAsJsonObject("textSafeArea");
                safeLeft = safeFloat(safe, "left", 0f);
                safeTop = safeFloat(safe, "top", 0f);
                safeRight = safeFloat(safe, "right", 0f);
                safeBottom = safeFloat(safe, "bottom", 0f);
            }
            for (RoomListener l : roomListeners) {
                l.onUserJoined(roomId, userId, username, vipLevel,
                        entryEffectUrl, entryAnimationUrl, avatarUrl, displayName,
                        supporterTier, userLevel, vipBadgeUrl, levelBadgeUrl, hostBadgeUrl, isHost,
                        wealthScore, totalSentCoins, roomSpendCoins, effectPriority, renderMode,
                        aspectRatio, safeLeft, safeTop, safeRight, safeBottom, durationMs,
                        showHiBadge);
            }
        } catch (Exception ignored) {
        }
    };

    private synchronized boolean isDuplicateJoinEvent(String roomId, String userId) {
        if (roomId == null || userId == null || userId.trim().isEmpty()) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        recentJoinEvents.entrySet().removeIf(
                entry -> now - entry.getValue() > JOIN_EVENT_DEDUPE_MS);
        String key = roomId.trim().toLowerCase(Locale.ROOT) + '|'
                + userId.trim().toLowerCase(Locale.ROOT);
        Long previous = recentJoinEvents.put(key, now);
        return previous != null && now - previous <= JOIN_EVENT_DEDUPE_MS;
    }

    private static String nullableString(JsonObject data, String key) {
        return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : null;
    }

    private static long safeLong(JsonObject data, String key, long fallback) {
        try {
            return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float safeFloat(JsonObject data, String key, float fallback) {
        try {
            return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsFloat() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private final Emitter.Listener onUserLeft = args -> {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = data.has("roomId") ? data.get("roomId").getAsString() : null;
            String userId = data.has("userId") ? data.get("userId").getAsString() : null;
            for (RoomListener l : roomListeners) l.onUserLeft(roomId, userId);
        } catch (Exception ignored) {
        }
    };

    private final Emitter.Listener onRoomMembers = args -> {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String roomId = data.has("roomId") ? data.get("roomId").getAsString() : null;
            JsonArray members = data.has("members") && data.get("members").isJsonArray()
                    ? data.getAsJsonArray("members") : new JsonArray();
            for (RoomListener l : roomListeners) l.onRoomMembers(roomId, members);
        } catch (Exception e) {
            Log.w(TAG, "room:members parse error", e);
        }
    };

    private final Emitter.Listener onChatMessage = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = toJsonObject(args[0]);
            if (data == null) return;
            String conversationId = data.has("conversationId") ? data.get("conversationId").getAsString() : null;
            ChatDtos.MessageDto message = null;
            if (data.has("message") && data.get("message").isJsonObject()) {
                message = GSON.fromJson(data.get("message"), ChatDtos.MessageDto.class);
            }
            if (conversationId == null || message == null) return;
            for (ChatListener l : chatListeners) l.onChatMessage(conversationId, message);
        } catch (Exception e) {
            Log.w(TAG, "chat:message parse error", e);
        }
    };

    private final Emitter.Listener onMessageEdited = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = toJsonObject(args[0]);
            if (data == null) return;
            String conversationId = data.has("conversationId")
                    ? data.get("conversationId").getAsString() : null;
            ChatDtos.MessageDto message = data.has("message") && data.get("message").isJsonObject()
                    ? GSON.fromJson(data.get("message"), ChatDtos.MessageDto.class) : null;
            if (conversationId == null || message == null) return;
            for (ChatListener l : chatListeners) l.onMessageEdited(conversationId, message);
        } catch (Exception e) {
            Log.w(TAG, "chat:message:edited parse error", e);
        }
    };

    private final Emitter.Listener onMessageUnsent = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = toJsonObject(args[0]);
            if (data == null) return;
            String conversationId = data.has("conversationId")
                    ? data.get("conversationId").getAsString() : null;
            String messageId = data.has("messageId")
                    ? data.get("messageId").getAsString() : null;
            if (conversationId == null || messageId == null) return;
            for (ChatListener l : chatListeners) l.onMessageUnsent(conversationId, messageId);
        } catch (Exception e) {
            Log.w(TAG, "chat:message:unsent parse error", e);
        }
    };

    private static JsonObject toJsonObject(Object arg) {
        if (arg == null) return null;
        if (arg instanceof JsonObject) return (JsonObject) arg;
        return JsonParser.parseString(String.valueOf(arg)).getAsJsonObject();
    }

    private final Emitter.Listener onConversationUpdated = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String conversationId = data.has("conversationId") ? data.get("conversationId").getAsString() : null;
            ChatDtos.MessageDto lastMessage = null;
            if (data.has("lastMessage") && data.get("lastMessage").isJsonObject()) {
                lastMessage = GSON.fromJson(data.get("lastMessage"), ChatDtos.MessageDto.class);
            }
            int unread = data.has("unreadCount") && !data.get("unreadCount").isJsonNull()
                    ? data.get("unreadCount").getAsInt() : 0;
            if (conversationId == null) return;
            for (ChatListener l : chatListeners) {
                l.onConversationUpdated(conversationId, lastMessage, unread);
            }
        } catch (Exception e) {
            Log.w(TAG, "chat:conversation:updated parse error", e);
        }
    };

    private final Emitter.Listener onConversationDeleted = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String conversationId = data.has("conversationId") ? data.get("conversationId").getAsString() : null;
            if (conversationId == null || conversationId.isEmpty()) return;
            for (ChatListener l : chatListeners) {
                l.onConversationDeleted(conversationId);
            }
        } catch (Exception e) {
            Log.w(TAG, "chat:conversation:deleted parse error", e);
        }
    };

    private final Emitter.Listener onTyping = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String conversationId = data.has("conversationId") ? data.get("conversationId").getAsString() : null;
            String userId = data.has("userId") ? data.get("userId").getAsString() : null;
            boolean isTyping = data.has("isTyping") && data.get("isTyping").getAsBoolean();
            for (ChatListener l : chatListeners) l.onTyping(conversationId, userId, isTyping);
        } catch (Exception ignored) {
        }
    };

    private final Emitter.Listener onChatRead = args -> {
        if (args.length == 0 || chatListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String conversationId = data.has("conversationId") ? data.get("conversationId").getAsString() : null;
            String readerId = data.has("userId") ? data.get("userId").getAsString()
                    : (data.has("readerId") ? data.get("readerId").getAsString() : null);
            String lastReadAt = data.has("lastReadAt") && !data.get("lastReadAt").isJsonNull()
                    ? data.get("lastReadAt").getAsString() : null;
            if (conversationId == null || lastReadAt == null) return;
            for (ChatListener l : chatListeners) l.onChatRead(conversationId, readerId, lastReadAt);
        } catch (Exception e) {
            Log.w(TAG, "chat:read parse error", e);
        }
    };

    private final Emitter.Listener onPresenceOnline = args -> {
        if (args.length == 0 || userListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String userId = data.has("userId") ? data.get("userId").getAsString() : null;
            String username = data.has("username") && !data.get("username").isJsonNull()
                    ? data.get("username").getAsString() : null;
            for (UserListener l : userListeners) l.onPresenceOnline(userId, username);
        } catch (Exception e) {
            Log.w(TAG, "presence:online parse error", e);
        }
    };

    private final Emitter.Listener onPresenceOffline = args -> {
        if (args.length == 0 || userListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            String userId = data.has("userId") ? data.get("userId").getAsString() : null;
            for (UserListener l : userListeners) l.onPresenceOffline(userId);
        } catch (Exception e) {
            Log.w(TAG, "presence:offline parse error", e);
        }
    };

    private final Emitter.Listener onMatchFound = args ->
            dispatchUserEvent("match:found", args);

    private final Emitter.Listener onMatchUpdate = args ->
            dispatchUserEvent("match:update", args);

    private final Emitter.Listener onGameUpdate = args -> dispatchGameToRoom("game:update", args);

    private final Emitter.Listener onGameInvite = args -> dispatchGameToRoom("game:invite", args);

    private void dispatchGameToRoom(String event, Object[] args) {
        if (args.length == 0 || roomListeners.isEmpty()) return;
        try {
            JsonObject payload = toJsonObject(args[0]);
            if (payload == null) return;
            String roomId = payload.has("roomId") && !payload.get("roomId").isJsonNull()
                    ? payload.get("roomId").getAsString() : null;
            for (RoomListener l : roomListeners) {
                l.onRoomEvent(roomId, event, payload, null, null);
            }
        } catch (Exception e) {
            Log.w(TAG, event + " parse error", e);
        }
    }

    private final Emitter.Listener onTaskProgress = args ->
            dispatchUserEvent("task:progress", args);

    private final Emitter.Listener onContestScore = args ->
            dispatchUserEvent("contest:score", args);

    private final Emitter.Listener onNotificationNew = args ->
            dispatchUserEvent("notification:new", args);

    private final Emitter.Listener onCelebrationToast = args ->
            dispatchUserEvent("celebration:toast", args);

    private final Emitter.Listener onAccountRestricted = args ->
            dispatchUserEvent("account:restricted", args);

    private void dispatchUserEvent(String event, Object[] args) {
        if (args.length == 0 || userListeners.isEmpty()) return;
        try {
            JsonObject data = toJsonObject(args[0]);
            if (data == null) return;
            for (UserListener l : userListeners) l.onUserEvent(event, data);
        } catch (Exception e) {
            Log.w(TAG, event + " parse error", e);
        }
    }

    private final Emitter.Listener onSocialRequest = args -> {
        if (args.length == 0 || userListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            for (UserListener l : userListeners) l.onUserEvent("social:request", data);
        } catch (Exception e) {
            Log.w(TAG, "social request parse error", e);
        }
    };

    private final Emitter.Listener onSocialResponded = args -> {
        if (args.length == 0 || userListeners.isEmpty()) return;
        try {
            JsonObject data = JsonParser.parseString(args[0].toString()).getAsJsonObject();
            for (UserListener l : userListeners) l.onUserEvent("social:request_responded", data);
        } catch (Exception e) {
            Log.w(TAG, "social responded parse error", e);
        }
    };
}
