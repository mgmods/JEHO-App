package com.Dramizo.Series.rtc;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.zego.ZegoEngineManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.livekit.android.ConnectOptions;
import io.livekit.android.LiveKit;
import io.livekit.android.LiveKitOverrides;
import io.livekit.android.RoomOptions;
import io.livekit.android.room.ClientProtocolVersion;
import io.livekit.android.room.ProtocolVersion;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.LocalParticipant;
import io.livekit.android.room.participant.Participant;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

/**
 * LiveKit (open source) voice path — free when self-hosted on your VPS.
 * Mic + remote audio; speaking indicators via ActiveSpeakers.
 */
public final class LiveKitEngineManager {
    private static final String TAG = "LiveKitEngine";
    private static LiveKitEngineManager instance;

    private Application application;
    @Nullable private Room room;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "livekit-rtc");
        t.setDaemon(true);
        return t;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ZegoEngineManager.RoomListener> listeners =
            new CopyOnWriteArrayList<>();
    private final Set<String> syntheticPlaying = new HashSet<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connectInFlight = new AtomicBoolean(false);
    /** Poll remotes — avoid SharedFlow.collect from Java (crashes process). */
    private final Runnable pollRemotesRunnable = this::pollRemoteParticipants;
    /** Combined local+remote speak levels (no SharedFlow collect). */
    private final Runnable speakLevelPollRunnable = this::pollSpeakLevels;

    private String currentRoomId;
    private String currentUserId;
    private boolean micEnabled = false;
    private boolean speakerMuted = false;
    private boolean publishing;
    /** JWT canPublish grant of the currently connected LiveKit session. */
    private boolean sessionCanPublish = false;
    @Nullable private String publishingStreamId;
    /** Publish requested before room was CONNECTED — applied on connect. */
    @Nullable private String pendingPublishStreamId;

    private String lastUrl;
    private String lastToken;
    private String lastRoomName;

    public static synchronized LiveKitEngineManager getInstance() {
        if (instance == null) instance = new LiveKitEngineManager();
        return instance;
    }

    public void init(Application application) {
        this.application = application;
    }

    public void addRoomListener(ZegoEngineManager.RoomListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeRoomListener(ZegoEngineManager.RoomListener listener) {
        listeners.remove(listener);
    }

    public boolean isReady() {
        return application != null && connected.get() && room != null;
    }

    public void connect(
            @Nullable Context context,
            @Nullable String url,
            @Nullable String token,
            @Nullable String roomName,
            @Nullable String userId) {
        Context appCtx = context != null ? context.getApplicationContext() : application;
        if (appCtx instanceof Application) application = (Application) appCtx;
        if (application == null || url == null || url.isEmpty()
                || token == null || token.isEmpty()
                || roomName == null || roomName.isEmpty()) {
            Log.w(TAG, "connect skipped — missing url/token/room");
            return;
        }
        currentRoomId = roomName;
        currentUserId = userId != null ? userId : "guest";
        lastUrl = url;
        lastToken = token;
        lastRoomName = roomName;
        final String urlF = url;
        final String tokenF = token;
        final String roomF = roomName;
        final String userF = currentUserId;
        connectInFlight.set(true);
        io.execute(() -> {
            try {
                disconnectBlocking(false);
                RoomOptions options = new RoomOptions(
                        false, false, null, null, null, null, null, null, null, null);
                LiveKitOverrides overrides =
                        new LiveKitOverrides(null, null, null, null, null, null);
                Room newRoom = LiveKit.INSTANCE.create(application, options, overrides);
                room = newRoom;
                // NOTE: Do NOT collect Room.events SharedFlow from pure Java — it fatal-crashes
                // the process (ClassCastException CompletedContinuation / DispatchedContinuation).
                // Remote audio still plays via SDK autoSubscribe; we poll participants for UI.
                ConnectOptions connectOptions = new ConnectOptions(
                        /* autoSubscribe */ true,
                        /* iceServers */ null,
                        /* rtcConfig */ null,
                        /* audio */ false,
                        /* video */ false,
                        ProtocolVersion.v13,
                        ClientProtocolVersion.DATA_STREAM_RPC);
                BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, cont) -> {
                            try {
                                return newRoom.connect(urlF, tokenF, connectOptions, cont);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                connected.set(true);
                connectInFlight.set(false);
                applyAudioRoute();
                // Force mic publish path after CONNECTED (fixes race with Activity.publish).
                if (pendingPublishStreamId != null || publishing) {
                    String sid = pendingPublishStreamId != null
                            ? pendingPublishStreamId
                            : publishingStreamId;
                    if (sid != null) {
                        publishing = true;
                        publishingStreamId = sid;
                        pendingPublishStreamId = null;
                    }
                }
                // Slight delay so WebRTC media plane is ready before first mic open.
                try {
                    Thread.sleep(180L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                // Always re-apply intended mic after connect (tracks often join muted:true).
                if (publishing || pendingPublishStreamId != null || micEnabled) {
                    applyMicOnEngine(micEnabled);
                    // Second nudge — some devices need capture after ICE settles.
                    try {
                        Thread.sleep(220L);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    if (micEnabled && (publishing || pendingPublishStreamId != null)) {
                        applyMicOnEngine(true);
                    }
                } else {
                    trySetRemotePlaybackGain(newRoom, speakerMuted ? 0f : 1f);
                }
                seedRemoteAudioTracks(newRoom);
                startPolling();
                notifyRoomState(roomF, 1, 0);
                Log.i(TAG, "connected room=" + roomF + " user=" + userF
                        + " publishing=" + publishing + " mic=" + micEnabled);
            } catch (Throwable t) {
                connected.set(false);
                connectInFlight.set(false);
                Log.e(TAG, "connect failed: " + t.getMessage(), t);
                notifyRoomState(roomF, -1, -1);
            }
        });
    }

    /**
     * LiveKit has no stream "renewToken" like Zego — reconnect with a new JWT when
     * seat publish rights change (audience → on mic).
     */
    public void reconnectWithToken(
            @Nullable Context context,
            @Nullable String url,
            @Nullable String token,
            @Nullable String roomName,
            @Nullable String userId) {
        String u = url != null && !url.isEmpty() ? url : lastUrl;
        String r = roomName != null && !roomName.isEmpty() ? roomName : lastRoomName;
        String uid = userId != null ? userId : currentUserId;
        // Keep intended mic/publish across reconnect.
        String keepStream = publishingStreamId != null
                ? publishingStreamId
                : pendingPublishStreamId;
        boolean keepMic = micEnabled;
        boolean keepPub = publishing || pendingPublishStreamId != null;
        connect(context, u, token, r, uid);
        if (keepPub && keepStream != null) {
            pendingPublishStreamId = keepStream;
            publishing = true;
            publishingStreamId = keepStream;
        }
        micEnabled = keepMic;
        // Grant from caller (RoomRtcEngine.setSessionCanPublish) must already be set.
    }

    private void seedRemoteAudioTracks(Room r) {
        try {
            for (Participant p : r.getRemoteParticipants().values()) {
                if (p == null) continue;
                String identity = participantIdentity(p);
                if (identity == null || identity.isEmpty()) continue;
                String streamId = identity + "_audio";
                if (syntheticPlaying.add(streamId)) {
                    notifyStreamAdded(streamId);
                    Log.i(TAG, "remote peer seen: " + streamId);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "seedRemote: " + t.getMessage());
        }
    }

    private void startPolling() {
        stopPolling();
        mainHandler.post(pollRemotesRunnable);
        mainHandler.post(speakLevelPollRunnable);
    }

    private void stopPolling() {
        mainHandler.removeCallbacks(pollRemotesRunnable);
        mainHandler.removeCallbacks(speakLevelPollRunnable);
    }

    private void pollRemoteParticipants() {
        if (!connected.get()) return;
        Room r = room;
        if (r != null) {
            try {
                Set<String> live = new HashSet<>();
                for (Participant p : r.getRemoteParticipants().values()) {
                    if (p == null) continue;
                    String identity = participantIdentity(p);
                    if (identity == null || identity.isEmpty()) continue;
                    String streamId = identity + "_audio";
                    live.add(streamId);
                    if (syntheticPlaying.add(streamId)) {
                        notifyStreamAdded(streamId);
                        Log.i(TAG, "remote peer joined: " + streamId);
                    }
                }
                // Remove left peers from UI set
                Set<String> gone = new HashSet<>(syntheticPlaying);
                gone.removeAll(live);
                // Keep local stream marker if any
                for (String streamId : gone) {
                    if (currentUserId != null && streamId.startsWith(currentUserId)) continue;
                    syntheticPlaying.remove(streamId);
                    notifyStreamRemoved(streamId);
                    Log.i(TAG, "remote peer left: " + streamId);
                }
            } catch (Throwable t) {
                Log.w(TAG, "pollRemote: " + t.getMessage());
            }
        }
        if (connected.get()) {
            mainHandler.postDelayed(pollRemotesRunnable, 900L);
        }
    }

    /** LiveKit audioLevel is 0–1; isSpeaking is preferred. Fallback for silent-but-open mic. */
    private static float participantLevel0to100(
            @Nullable Participant participant, boolean treatLocalPublishingMicAsActive) {
        if (participant == null) return 0f;
        boolean speaking = isParticipantSpeaking(participant);
        float al = participantAudioLevel(participant);
        if (speaking) {
            // Keep ripples lively for the whole speak window.
            float base = al > 0.01f ? al * 100f : 42f;
            return Math.max(36f, Math.min(100f, base + 12f));
        }
        if (al > 0.015f) {
            return Math.min(100f, Math.max(18f, al * 100f));
        }
        // Local host: if mic track is publishing but VAD hasn't fired yet, keep a soft pulse
        // so the seat ring proves LiveKit is live (never zeros the UI immediately).
        if (treatLocalPublishingMicAsActive) {
            return 22f;
        }
        return 0f;
    }

    private static boolean isParticipantSpeaking(@Nullable Participant participant) {
        if (participant == null) return false;
        // Direct SDK interop first (LiveKit Android Kotlin properties → isSpeaking / getIsSpeaking).
        try {
            Method m = participant.getClass().getMethod("isSpeaking");
            Object v = m.invoke(participant);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = participant.getClass().getMethod("getIsSpeaking");
            Object v = m.invoke(participant);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        return readBooleanProp(participant, "isSpeaking", "getIsSpeaking", "speaking");
    }

    private static float participantAudioLevel(@Nullable Participant participant) {
        if (participant == null) return 0f;
        try {
            Method m = participant.getClass().getMethod("getAudioLevel");
            Object v = m.invoke(participant);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignored) {
        }
        return readFloatProp(participant, "getAudioLevel", "audioLevel");
    }

    private void pollSpeakLevels() {
        if (!connected.get()) return;
        Room r = room;
        if (r != null) {
            try {
                LocalParticipant local = r.getLocalParticipant();
                if (local != null && currentUserId != null) {
                    boolean localActiveMic = micEnabled && (publishing || pendingPublishStreamId != null);
                    float level = participantLevel0to100(local, localActiveMic);
                    // Only inject soft pulse when truly open mic; silence still can dip lower.
                    if (localActiveMic && level < 12f && isParticipantSpeaking(local)) {
                        level = 36f;
                    }
                    notifySoundLevel(currentUserId, level);
                }
                for (Participant p : r.getRemoteParticipants().values()) {
                    if (p == null) continue;
                    String identity = participantIdentity(p);
                    if (identity == null || identity.isEmpty()) continue;
                    notifySoundLevel(identity, participantLevel0to100(p, false));
                }
            } catch (Throwable t) {
                Log.w(TAG, "pollSpeak: " + t.getMessage());
            }
        }
        if (connected.get()) {
            // Faster than 220ms so waves feel responsive on LiveKit free engine.
            mainHandler.postDelayed(speakLevelPollRunnable, 140L);
        }
    }

    private static boolean readBooleanProp(Object target, String... names) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) {
                    continue;
                }
                String n = m.getName();
                for (String want : names) {
                    if (n.equals(want) || n.equalsIgnoreCase(want)
                            || n.equalsIgnoreCase("get" + want)
                            || n.equalsIgnoreCase("is" + want)) {
                        Object v = m.invoke(target);
                        if (v instanceof Boolean) return (Boolean) v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static float readFloatProp(Object target, String... names) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt != float.class && rt != Float.class
                        && rt != double.class && rt != Double.class) {
                    continue;
                }
                String n = m.getName();
                for (String want : names) {
                    if (n.equals(want) || n.equalsIgnoreCase(want)
                            || n.equalsIgnoreCase("get" + want)) {
                        Object v = m.invoke(target);
                        if (v instanceof Number) return ((Number) v).floatValue();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    private void notifyRoomState(@Nullable String roomId, int state, int err) {
        mainHandler.post(() -> {
            for (ZegoEngineManager.RoomListener l : listeners) {
                try {
                    l.onRoomStateChanged(roomId, state, err);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void notifyStreamAdded(String streamId) {
        mainHandler.post(() -> {
            for (ZegoEngineManager.RoomListener l : listeners) {
                try {
                    l.onStreamAdded(streamId);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void notifyStreamRemoved(String streamId) {
        mainHandler.post(() -> {
            for (ZegoEngineManager.RoomListener l : listeners) {
                try {
                    l.onStreamRemoved(streamId);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void notifySoundLevel(String userId, float level) {
        if (userId == null || userId.isEmpty()) return;
        Runnable deliver = () -> {
            for (ZegoEngineManager.RoomListener l : listeners) {
                try {
                    l.onSoundLevel(userId, level);
                } catch (Throwable ignored) {
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver.run();
        } else {
            mainHandler.post(deliver);
        }
    }

    /** Identity / Sid are Kotlin value classes + flowDelegate — resolve from Java. */
    @Nullable
    private static String participantIdentity(@Nullable Participant participant) {
        if (participant == null) return null;
        String id = invokeStringProp(participant, "getIdentity", "identity");
        if (id != null && !id.isEmpty()) return id;
        return invokeStringProp(participant, "getSid", "sid");
    }

    @Nullable
    private static String invokeStringProp(Object target, String getter, String fieldName) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().startsWith(getter) && !m.getName().equals(getter)) continue;
                if (m.getParameterTypes().length != 0) continue;
                Object id = m.invoke(target);
                String s = unwrapString(id);
                if (s != null) return s;
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field f = target.getClass().getField(fieldName);
            return unwrapString(f.get(target));
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static String unwrapString(@Nullable Object id) {
        if (id == null) return null;
        if (id instanceof String) return (String) id;
        try {
            Method gv = id.getClass().getMethod("getValue");
            Object v = gv.invoke(id);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }
        String s = String.valueOf(id);
        if (s == null || "null".equals(s) || s.contains("@")) return null;
        return s;
    }

    private void cancelEvents() {
        stopPolling();
    }

    private void disconnectBlocking(boolean clearPendingPublish) {
        cancelEvents();
        Room r = room;
        room = null;
        if (clearPendingPublish) {
            publishing = false;
            publishingStreamId = null;
            pendingPublishStreamId = null;
        }
        syntheticPlaying.clear();
        connected.set(false);
        if (r != null) {
            try {
                r.disconnect();
            } catch (Throwable t) {
                Log.w(TAG, "disconnect: " + t.getMessage());
            }
        }
    }

    public void disconnect() {
        io.execute(() -> disconnectBlocking(true));
    }

    public void hardLeaveRoom() {
        // Never block the UI thread — r.disconnect() can freeze enter/exit transitions.
        micEnabled = false;
        speakerMuted = false; // don't leave speaker sticky-muted for next join
        publishing = false;
        publishingStreamId = null;
        pendingPublishStreamId = null;
        sessionCanPublish = false;
        currentRoomId = null;
        lastToken = null;
        connected.set(false);
        cancelEvents();
        io.execute(() -> {
            try {
                disconnectBlocking(true);
            } catch (Throwable t) {
                Log.w(TAG, "hardLeaveRoom bg failed: " + t.getMessage());
            }
        });
    }

    public void logoutRoom() {
        hardLeaveRoom();
        currentUserId = null;
        lastToken = null;
    }

    public void setMicEnabled(boolean enabled) {
        micEnabled = enabled;
        // Remember intent even before startPublishing — when already CONNECTED apply immediately
        // so take-seat mic does not sit muted until a later publish race.
        if (!connected.get() && !publishing && pendingPublishStreamId == null) {
            return;
        }
        if (!publishing && pendingPublishStreamId == null) {
            // Connected as audience: keep track off; intent stored in micEnabled.
            return;
        }
        io.execute(() -> applyMicOnEngine(enabled));
    }

    private void applyMicOnEngine(boolean enabled) {
        Room r = room;
        if (r == null || !connected.get()) {
            Log.w(TAG, "applyMic deferred — not connected yet mic=" + enabled);
            return;
        }
        // Retry — WebRTC capture can fail once right after connect on mid-range devices.
        Throwable last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LocalParticipant local = r.getLocalParticipant();
                if (local == null) {
                    last = new IllegalStateException("local participant null");
                    Thread.sleep(120L * attempt);
                    continue;
                }
                Object result = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, cont) -> {
                            try {
                                return local.setMicrophoneEnabled(enabled, cont);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                applyAudioRoute();
                // Keep remote levels audible when SPEAKER is the route (common room mode).
                trySetRemotePlaybackGain(r, speakerMuted ? 0f : 1f);
                Log.i(TAG, "setMicrophoneEnabled=" + enabled
                        + " attempt=" + attempt
                        + " result="
                        + (result != null ? result.getClass().getSimpleName() : "null"));
                return;
            } catch (Throwable t) {
                last = t;
                Log.w(TAG, "setMicEnabled attempt " + attempt + " failed: " + t.getMessage());
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last != null) {
            Log.e(TAG, "setMicEnabled failed after retries: " + last.getMessage(), last);
        }
    }

    /** Best-effort remote volume so some devices do not mix at near-zero after soft mute. */
    private static void trySetRemotePlaybackGain(@Nullable Room r, float gain) {
        if (r == null) return;
        try {
            for (Participant p : r.getRemoteParticipants().values()) {
                if (p == null) continue;
                try {
                    Method m = p.getClass().getMethod("setVolume", double.class);
                    m.invoke(p, (double) Math.max(0f, Math.min(1f, gain)));
                } catch (Throwable ignored) {
                    try {
                        Method m = p.getClass().getMethod("setVolume", float.class);
                        m.invoke(p, Math.max(0f, Math.min(1f, gain)));
                    } catch (Throwable ignored2) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isMicEnabled() {
        return micEnabled;
    }

    public void setSpeakerMuted(boolean muted) {
        speakerMuted = muted;
        applyAudioRoute();
    }

    public boolean isSpeakerMuted() {
        return speakerMuted;
    }

    private void applyAudioRoute() {
        // wantOpenSpeaker = !speakerMuted (room “speaker” button on).
        RoomAudioRoute.applyCommunicationRoute(application, !speakerMuted);
    }

    public void startPublishingAudio(String streamId) {
        if (streamId == null || streamId.isEmpty()) return;
        publishing = true;
        publishingStreamId = streamId;
        if (!connected.get() || room == null) {
            pendingPublishStreamId = streamId;
            Log.w(TAG, "defer publish until connect stream=" + streamId);
            // Nudge: if connect already finished racing, still try shortly.
            io.execute(() -> {
                if (connected.get() && room != null && publishing) {
                    pendingPublishStreamId = null;
                    applyMicOnEngine(micEnabled);
                }
            });
            return;
        }
        pendingPublishStreamId = null;
        io.execute(() -> {
            applyMicOnEngine(micEnabled);
            Log.i(TAG, "publishing mic stream=" + streamId + " mic=" + micEnabled);
        });
    }

    public void stopPublishing() {
        publishing = false;
        publishingStreamId = null;
        pendingPublishStreamId = null;
        io.execute(() -> applyMicOnEngine(false));
    }

    public boolean isPublishing() {
        return publishing;
    }

    public String getPublishingStreamId() {
        return publishingStreamId;
    }

    /** LiveKit auto-subscribes remote audio — synthetic stream ids for seat UI. */
    public void startPlayingAudio(String streamId) {
        if (streamId != null) syntheticPlaying.add(streamId);
    }

    public void startPlayingAudio(String streamId, String roomId) {
        startPlayingAudio(streamId);
    }

    public void stopPlaying(String streamId) {
        if (streamId != null) syntheticPlaying.remove(streamId);
    }

    public void clearPausedPlayStreams() {
        /* no-op */
    }

    public Set<String> getPlayingStreamIds() {
        return new HashSet<>(syntheticPlaying);
    }

    public boolean isInRoom(@Nullable String roomId) {
        if (!connected.get() || roomId == null) return false;
        if (roomId.equals(currentRoomId) || roomId.equals(lastRoomName)) return true;
        // App room UUID may differ from LiveKit room name (zegoRoomId).
        return lastRoomName != null || currentRoomId != null;
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    @Nullable
    public String getLastToken() {
        return lastToken;
    }

    public void setSessionCanPublish(boolean canPublish) {
        this.sessionCanPublish = canPublish;
    }

    public boolean isSessionCanPublish() {
        return sessionCanPublish;
    }

    public boolean isConnectInFlight() {
        return connectInFlight.get();
    }
}
