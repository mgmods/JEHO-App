package com.Dramizo.Series.zego;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.data.remote.api.ConfigApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoRoomMode;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoStream;
import im.zego.zegoexpress.entity.ZegoUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import retrofit2.Response;

/**
 * ZEGO Express — Token-only auth.
 * AppSign / ServerSecret stay on the server. The APK only keeps AppID (from GET /config/zego
 * or the join-room token payload) and always logs into rooms with a server-issued token.
 */
public class ZegoEngineManager {
    private static final String TAG = "ZegoEngineManager";
    private static final String PREFS = "auralive_zego_config";
    private static final String KEY_APP_ID = "app_id";
    /** Legacy — wiped; never store AppSign on device again. */
    private static final String KEY_APP_SIGN = "app_sign";
    private static ZegoEngineManager instance;
    private Application application;
    private ZegoExpressEngine engine;
    private boolean initialized;
    private long activeAppId;
    private String currentRoomId;
    private String currentUserId;
    private boolean micEnabled = true;
    private boolean speakerMuted = false;
    private String publishingStreamId;
    private final Set<String> playingStreamIds = new HashSet<>();
    private final Map<String, String> playingStreamRooms = new HashMap<>();

    public interface RoomListener {
        void onRoomStateChanged(String roomId, int state);
        /** Prefer this — includes Zego errorCode (0 = success). */
        default void onRoomStateChanged(String roomId, int state, int errorCode) {
            onRoomStateChanged(roomId, state);
        }
        void onStreamAdded(String streamId);
        void onStreamRemoved(String streamId);
        default void onSoundLevel(String userId, float level) {}
        default void onPlayerState(String streamId, int state, int errorCode) {}
        /** In-room text chat over Zego broadcast (JSON payload). */
        default void onRoomChatMessage(String roomId, String fromUserId, String fromUserName,
                                       String jsonOrText) {}
    }

    /** Fired when host-mixed MediaPlayer reaches PLAY_ENDED (YouTube/local mix). */
    public interface LocalMusicEndListener {
        void onLocalMusicEnded();
    }

    public interface RoomChatSendCallback {
        void onResult(boolean ok, int errorCode);
    }

    private final CopyOnWriteArrayList<RoomListener> roomListeners =
            new CopyOnWriteArrayList<>();
    @Nullable private LocalMusicEndListener localMusicEndListener;

    public void setLocalMusicEndListener(@Nullable LocalMusicEndListener listener) {
        localMusicEndListener = listener;
    }

    public static synchronized ZegoEngineManager getInstance() {
        if (instance == null) instance = new ZegoEngineManager();
        return instance;
    }

    public void init(Application application) {
        this.application = application;
        // Purge any previously cached AppSign from older builds.
        prefs(application).edit().remove(KEY_APP_SIGN).apply();
        long cachedId = prefs(application).getLong(KEY_APP_ID, 0L);
        long appId = cachedId > 0L ? cachedId : BuildConfig.ZEGO_APP_ID;
        createEngine(application, appId);
    }

    /** Blocking network fetch — call from a background thread. */
    public void fetchAndApplyRemote(Context context, ConfigApi configApi) {
        if (configApi == null) return;
        try {
            Response<ApiResponse<MiscDtos.ZegoConfigDto>> resp = configApi.zego().execute();
            if (!resp.isSuccessful() || resp.body() == null || resp.body().data == null) {
                Log.w(TAG, "remote ZEGO config unavailable status=" + resp.code());
                return;
            }
            MiscDtos.ZegoConfigDto remote = resp.body().data;
            applyAppId(context, remote.appId);
        } catch (Exception e) {
            Log.w(TAG, "remote ZEGO config fetch failed: " + e.getMessage());
        }
    }

    /** Prefer this — AppID only (never AppSign). */
    public synchronized void applyAppId(Context context, long appId) {
        if (appId <= 0L) {
            Log.w(TAG, "remote ZEGO appId missing — keeping current engine");
            return;
        }
        Context appCtx = context != null ? context.getApplicationContext() : application;
        if (appCtx != null) {
            prefs(appCtx).edit()
                    .putLong(KEY_APP_ID, appId)
                    .remove(KEY_APP_SIGN)
                    .apply();
        }
        Application app = application;
        if (app == null && appCtx instanceof Application) {
            app = (Application) appCtx;
            application = app;
        }
        if (app == null) return;
        if (initialized && activeAppId == appId) {
            return;
        }
        if (initialized) {
            Log.i(TAG, "recreating ZEGO engine with server AppID");
            destroyEngineOnly();
        }
        createEngine(app, appId);
    }

    /** @deprecated AppSign must not reach the client — ignored; uses AppID only. */
    @Deprecated
    public synchronized void applyRemoteCredentials(Context context, long appId, String appSign) {
        applyAppId(context, appId);
    }

    /** Clears a poisoned AppSign leftover from older builds. */
    public synchronized void repairCredentialsIfPoisoned(Context context) {
        Context appCtx = context != null ? context.getApplicationContext() : application;
        if (appCtx == null) return;
        prefs(appCtx).edit().remove(KEY_APP_SIGN).apply();
        if (!initialized && application != null) {
            long appId = prefs(appCtx).getLong(KEY_APP_ID, 0L);
            if (appId <= 0L) appId = BuildConfig.ZEGO_APP_ID;
            createEngine(application, appId);
        }
    }

    private synchronized void createEngine(Application application, long appId) {
        if (initialized) return;
        if (appId == 0L) {
            Log.w(TAG, "ZEGO AppID missing — engine deferred until server config arrives");
            return;
        }
        try {
            // This client uses one audio room at a time.
            ZegoExpressEngine.setRoomMode(ZegoRoomMode.SINGLE_ROOM);
        } catch (Throwable t) {
            Log.w(TAG, "setRoomMode SINGLE_ROOM unavailable: " + t.getMessage());
        }
        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = appId;
        // Token auth: empty AppSign — credentials stay on the server.
        profile.appSign = "";
        profile.application = application;
        profile.scenario = ZegoScenario.GENERAL;
        engine = ZegoExpressEngine.createEngine(profile, new IZegoEventHandler() {
            @Override
            public void onRoomStateChanged(String roomID, im.zego.zegoexpress.constants.ZegoRoomStateChangedReason reason, int errorCode, org.json.JSONObject extendedData) {
                int state = reason != null ? reason.value() : -1;
                Log.i(TAG, "room state room=" + roomID + " reason=" + state + " err=" + errorCode);
                for (RoomListener listener : roomListeners) {
                    try {
                        listener.onRoomStateChanged(roomID, state, errorCode);
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoStream> streamList, org.json.JSONObject extendedData) {
                if (roomListeners.isEmpty() || streamList == null) return;
                for (ZegoStream stream : streamList) {
                    for (RoomListener listener : roomListeners) {
                        try {
                            if (updateType == ZegoUpdateType.ADD) {
                                listener.onStreamAdded(stream.streamID);
                            } else {
                                listener.onStreamRemoved(stream.streamID);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            @Override
            public void onPublisherStateUpdate(String streamID,
                    im.zego.zegoexpress.constants.ZegoPublisherState state,
                    int errorCode, org.json.JSONObject extendedData) {
                Log.i(TAG, "publisher state stream=" + streamID
                        + " state=" + (state != null ? state.value() : -1)
                        + " err=" + errorCode);
            }

            @Override
            public void onPlayerStateUpdate(String streamID,
                    im.zego.zegoexpress.constants.ZegoPlayerState state,
                    int errorCode, org.json.JSONObject extendedData) {
                if (errorCode != 0) {
                    Log.w(TAG, "player error stream=" + streamID + " err=" + errorCode);
                }
                for (RoomListener listener : roomListeners) {
                    try {
                        listener.onPlayerState(
                                streamID,
                                state != null ? state.value() : -1,
                                errorCode);
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void onCapturedSoundLevelUpdate(float soundLevel) {
                if (currentUserId != null) {
                    for (RoomListener listener : roomListeners) {
                        try {
                            listener.onSoundLevel(currentUserId, soundLevel);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            @Override
            public void onRemoteSoundLevelUpdate(HashMap<String, Float> soundLevels) {
                if (roomListeners.isEmpty() || soundLevels == null) return;
                for (java.util.Map.Entry<String, Float> entry : soundLevels.entrySet()) {
                    String streamId = entry.getKey();
                    String userId = streamId != null && streamId.endsWith("_audio")
                            ? streamId.substring(0, streamId.length() - 6)
                            : streamId;
                    for (RoomListener listener : roomListeners) {
                        try {
                            listener.onSoundLevel(
                                    userId,
                                    entry.getValue() != null ? entry.getValue() : 0f);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            @Override
            public void onIMRecvBroadcastMessage(
                    String roomID,
                    ArrayList<im.zego.zegoexpress.entity.ZegoBroadcastMessageInfo> messageList) {
                if (messageList == null || messageList.isEmpty()) return;
                for (im.zego.zegoexpress.entity.ZegoBroadcastMessageInfo info : messageList) {
                    if (info == null) continue;
                    String fromId = info.fromUser != null ? info.fromUser.userID : "";
                    String fromName = info.fromUser != null ? info.fromUser.userName : "";
                    String msg = info.message != null ? info.message : "";
                    for (RoomListener listener : roomListeners) {
                        try {
                            listener.onRoomChatMessage(roomID, fromId, fromName, msg);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            @Override
            public void onIMRecvBarrageMessage(
                    String roomID,
                    ArrayList<im.zego.zegoexpress.entity.ZegoBarrageMessageInfo> messageList) {
                if (messageList == null || messageList.isEmpty()) return;
                for (im.zego.zegoexpress.entity.ZegoBarrageMessageInfo info : messageList) {
                    if (info == null) continue;
                    String fromId = info.fromUser != null ? info.fromUser.userID : "";
                    String fromName = info.fromUser != null ? info.fromUser.userName : "";
                    String msg = info.message != null ? info.message : "";
                    for (RoomListener listener : roomListeners) {
                        try {
                            listener.onRoomChatMessage(roomID, fromId, fromName, msg);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        });
        initialized = engine != null;
        if (initialized) {
            activeAppId = appId;
            engine.startSoundLevelMonitor();
            try {
                engine.muteSpeaker(speakerMuted);
                if (!speakerMuted) engine.setAudioRouteToSpeaker(true);
            } catch (Exception ignored) {
            }
        }
        Log.i(TAG, "ZEGO Express engine initialized=" + initialized + " appId=" + appId + " tokenAuth=true");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setRoomListener(RoomListener listener) {
        roomListeners.clear();
        if (listener != null) roomListeners.add(listener);
    }

    public void addRoomListener(RoomListener listener) {
        if (listener != null && !roomListeners.contains(listener)) {
            roomListeners.add(listener);
        }
    }

    public void removeRoomListener(RoomListener listener) {
        roomListeners.remove(listener);
    }

    public boolean isReady() {
        return initialized && engine != null;
    }

    public void loginRoom(String roomId, String userId, String token) {
        ensureEngine();
        if (engine == null) {
            Log.w(TAG, "loginRoom skipped — engine null (waiting for server AppID)");
            return;
        }
        if (roomId == null || roomId.isEmpty() || userId == null || userId.isEmpty()) {
            Log.w(TAG, "loginRoom skipped — missing room/user");
            return;
        }
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "loginRoom skipped — token required (no AppSign fallback)");
            return;
        }
        if (roomId.equals(currentRoomId) && userId.equals(currentUserId)
                && engine != null && initialized) {
            renewRoomToken(roomId, token);
            Log.i(TAG, "adopted existing room session room=" + roomId);
            return;
        }
        // SINGLE_ROOM: leave the previous voice room before joining another.
        if (currentRoomId != null && !currentRoomId.equals(roomId)) {
            Log.i(TAG, "logout previous room before login old=" + currentRoomId + " new=" + roomId);
            try {
                engine.logoutRoom(currentRoomId);
            } catch (Exception e) {
                Log.w(TAG, "pre-login logout failed", e);
            }
            currentRoomId = null;
        }
        currentRoomId = roomId;
        currentUserId = userId;
        // Keep the Activity's intended mic state — do not force-unmute on every login
        // (that caused hear-me / mute flicker after reconnect).
        try {
            engine.muteSpeaker(speakerMuted);
            if (!speakerMuted) engine.setAudioRouteToSpeaker(true);
            engine.muteMicrophone(!micEnabled);
        } catch (Exception e) {
            Log.w(TAG, "audio route setup failed", e);
        }
        ZegoUser user = new ZegoUser(userId, userId);
        Log.i(TAG, "loginRoom room=" + roomId + " user=" + userId
                + " tokenLen=" + token.length());
        im.zego.zegoexpress.entity.ZegoRoomConfig config = new im.zego.zegoexpress.entity.ZegoRoomConfig();
        config.token = token;
        // Max member count for broadcast/barrage IM in room.
        try {
            config.maxMemberCount = 0; // 0 = no limit / SDK default
        } catch (Throwable ignored) {
        }
        engine.loginRoom(roomId, user, config);
    }

    /**
     * Room text chat over Zego (reliable broadcast). Payload should be JSON ≤ 1024 bytes.
     * Returns false if engine/room is not ready (caller may fall back).
     */
    public boolean sendRoomChatMessage(@Nullable String roomId, @Nullable String message,
                                       @Nullable RoomChatSendCallback callback) {
        ensureEngine();
        if (engine == null || roomId == null || roomId.isEmpty()
                || message == null || message.isEmpty()) {
            if (callback != null) callback.onResult(false, -1);
            return false;
        }
        if (currentRoomId == null || !roomId.equals(currentRoomId)) {
            if (callback != null) callback.onResult(false, -2);
            return false;
        }
        try {
            engine.sendBroadcastMessage(roomId, message,
                    (errorCode, messageID) -> {
                        if (callback != null) callback.onResult(errorCode == 0, errorCode);
                        if (errorCode != 0) {
                            Log.w(TAG, "sendBroadcastMessage err=" + errorCode);
                        }
                    });
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "sendBroadcastMessage failed: " + t.getMessage());
            if (callback != null) callback.onResult(false, -3);
            return false;
        }
    }

    public boolean isInRoom(@Nullable String roomId) {
        return roomId != null && roomId.equals(currentRoomId) && engine != null;
    }

    public void renewRoomToken(String roomId, String token) {
        if (engine == null || roomId == null || roomId.isEmpty()
                || token == null || token.isEmpty()) return;
        try {
            engine.renewToken(roomId, token);
        } catch (Exception error) {
            Log.w(TAG, "renewToken failed", error);
        }
    }

    public void startPublishingAudio(String streamId) {
        ensureEngine();
        if (engine == null) return;
        if (streamId != null && streamId.equals(publishingStreamId)) {
            engine.muteMicrophone(!micEnabled);
            return;
        }
        stopPublishing();
        engine.muteMicrophone(!micEnabled);
        try {
            engine.muteSpeaker(speakerMuted);
            if (!speakerMuted) engine.setAudioRouteToSpeaker(true);
        } catch (Exception ignored) {
        }
        engine.startPublishingStream(streamId);
        publishingStreamId = streamId;
        Log.i(TAG, "publishing audio stream=" + streamId + " room=" + currentRoomId
                + " micEnabled=" + micEnabled);
    }

    public void stopPublishing() {
        if (engine != null) engine.stopPublishingStream();
        publishingStreamId = null;
    }


    public void startPlayingAudio(String streamId) {
        startPlayingAudio(streamId, currentRoomId);
    }

    public void startPlayingAudio(String streamId, String roomId) {
        ensureEngine();
        if (engine == null || streamId == null) return;
        // Muted speaker = do not pull streams (saves Zego play minutes).
        if (speakerMuted) return;
        if (playingStreamIds.contains(streamId)) {
            try {
                engine.mutePlayStreamAudio(streamId, false);
                engine.setPlayVolume(streamId, 100);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            engine.muteSpeaker(false);
            engine.setAudioRouteToSpeaker(true);
        } catch (Exception ignored) {
        }
        engine.startPlayingStream(streamId);
        try {
            engine.mutePlayStreamAudio(streamId, false);
            engine.setPlayVolume(streamId, 100);
        } catch (Exception ignored) {
        }
        playingStreamIds.add(streamId);
        if (roomId != null && !roomId.isEmpty()) playingStreamRooms.put(streamId, roomId);
        else if (currentRoomId != null) playingStreamRooms.put(streamId, currentRoomId);
        Log.i(TAG, "playing audio stream=" + streamId + " room="
                + (roomId != null ? roomId : currentRoomId));
    }

    public void stopPlaying(String streamId) {
        if (engine != null && streamId != null) {
            engine.stopPlayingStream(streamId);
            playingStreamIds.remove(streamId);
            playingStreamRooms.remove(streamId);
        }
    }

    public void stopAllPlaying() {
        if (engine == null) return;
        for (String id : new HashSet<>(playingStreamIds)) {
            engine.stopPlayingStream(id);
        }
        playingStreamIds.clear();
        playingStreamRooms.clear();
    }

    public void setMicEnabled(boolean enabled) {
        micEnabled = enabled;
        if (engine != null) engine.muteMicrophone(!enabled);
    }

    /**
     * Mute room playback. When muted we fully stop pulling remote streams
     * so Zego does not bill play minutes — unmute resumes via the activity.
     */
    public void setSpeakerMuted(boolean muted) {
        speakerMuted = muted;
        if (engine == null) return;
        if (muted) {
            try {
                engine.muteSpeaker(true);
            } catch (Exception ignored) {
            }
            // Stop pull = no listener minutes while user has sound off.
            stopAllPlaying();
            try {
                if (localMusicPlayer != null) {
                    localMusicPlayer.setPlayVolume(0);
                }
            } catch (Throwable ignored) {
            }
            Log.i(TAG, "speaker muted — stopped all play streams (save minutes)");
            return;
        }
        try {
            engine.muteSpeaker(false);
            engine.setAudioRouteToSpeaker(true);
        } catch (Exception ignored) {
        }
        try {
            if (localMusicPlayer != null) {
                localMusicPlayer.setPlayVolume(
                        musicMuteLocalMonitor || speakerMuted ? 0 : MUSIC_PLAY_VOLUME);
            }
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "speaker unmuted — activity should re-subscribe seat audio");
    }

    public boolean isSpeakerMuted() {
        return speakerMuted;
    }

    public String getPublishingStreamId() { return publishingStreamId; }

    public boolean isPlayingStream(String streamId) {
        return streamId != null && playingStreamIds.contains(streamId);
    }

    public static String audioStreamId(String userId) {
        return userId != null ? userId + "_audio" : null;
    }


    public void logoutRoom() {
        stopLocalMusic();
        stopPublishing();
        stopAllPlaying();
        if (engine != null && currentRoomId != null) {
            engine.logoutRoom(currentRoomId);
        }
        currentRoomId = null;
        // Stay muted until the next explicit setMicEnabled — never force-open on logout
        // (reconnect / WhatsApp return used to unmute a muted user here).
        micEnabled = false;
        speakerMuted = false;
        if (engine != null) {
            try {
                engine.muteMicrophone(true);
                engine.muteSpeaker(false);
            } catch (Exception ignored) {
            }
        }
    }

    public void destroy() {
        logoutRoom();
        destroyEngineOnly();
    }

    private void destroyEngineOnly() {
        stopLocalMusic();
        stopPublishing();
        stopAllPlaying();
        // Never leave stale room ids after destroy — adopt-login would skip real login.
        currentRoomId = null;
        currentUserId = null;
        publishingStreamId = null;
        playingStreamIds.clear();
        playingStreamRooms.clear();
        if (initialized) {
            if (engine != null) {
                try {
                    engine.stopSoundLevelMonitor();
                } catch (Throwable ignored) {
                }
            }
            try {
                ZegoExpressEngine.destroyEngine(null);
            } catch (Throwable t) {
                Log.w(TAG, "destroyEngine failed: " + t.getMessage());
            }
            engine = null;
            initialized = false;
            activeAppId = 0L;
        }
    }

    // ── Local / network room music mixed into the host publish stream ──

    @Nullable private im.zego.zegoexpress.ZegoMediaPlayer localMusicPlayer;
    @Nullable private String localMusicPath;
    /** Publish mix loudness 0–200 (Zego default is only 60 — too quiet for listeners). */
    private static final int MUSIC_PUBLISH_VOLUME = 200;
    /** Host local monitor via MediaPlayer (local files). 0–200. */
    private static final int MUSIC_PLAY_VOLUME = 160;
    private boolean musicMuteLocalMonitor;

    public void playLocalMusic(String absolutePath) {
        playLocalMusic(absolutePath, 0L, false);
    }

    public void playLocalMusic(String absolutePath, long seekPositionMs) {
        playLocalMusic(absolutePath, seekPositionMs, false);
    }

    /**
     * @param muteLocalMonitor true = mix into publish only (host hears via ExoPlayer);
     *                         false = host also hears MediaPlayer locally (local files).
     */
    public void playLocalMusic(String absolutePath, long seekPositionMs, boolean muteLocalMonitor) {
        ensureEngine();
        if (engine == null || absolutePath == null || absolutePath.isEmpty()) return;
        try {
            if (localMusicPlayer == null) {
                localMusicPlayer = engine.createMediaPlayer();
            }
            if (localMusicPlayer == null) return;
            musicMuteLocalMonitor = muteLocalMonitor;
            localMusicPlayer.enableAux(true);
            localMusicPlayer.enableRepeat(false);
            try {
                localMusicPlayer.setEventHandler(
                        new im.zego.zegoexpress.callback.IZegoMediaPlayerEventHandler() {
                            @Override
                            public void onMediaPlayerStateUpdate(
                                    im.zego.zegoexpress.ZegoMediaPlayer mediaPlayer,
                                    im.zego.zegoexpress.constants.ZegoMediaPlayerState state,
                                    int errorCode) {
                                if (state == im.zego.zegoexpress.constants.ZegoMediaPlayerState
                                        .PLAY_ENDED) {
                                    LocalMusicEndListener cb = localMusicEndListener;
                                    if (cb != null) {
                                        try {
                                            cb.onLocalMusicEnded();
                                        } catch (Throwable t) {
                                            Log.w(TAG, "local music end callback: " + t.getMessage());
                                        }
                                    }
                                }
                            }
                        });
            } catch (Throwable t) {
                Log.w(TAG, "media player event handler: " + t.getMessage());
            }
            try {
                localMusicPlayer.muteLocal(muteLocalMonitor);
            } catch (Throwable ignored) {
            }
            applyMusicMixVolumes(localMusicPlayer);
            localMusicPath = absolutePath;
            final long seekTo = Math.max(0L, seekPositionMs);
            localMusicPlayer.stop();
            localMusicPlayer.loadResource(absolutePath, errorCode -> {
                if (errorCode == 0 && localMusicPlayer != null) {
                    try {
                        localMusicPlayer.muteLocal(musicMuteLocalMonitor);
                    } catch (Throwable ignored) {
                    }
                    applyMusicMixVolumes(localMusicPlayer);
                    if (seekTo > 0L) {
                        localMusicPlayer.seekTo(seekTo, code -> {
                            if (localMusicPlayer != null) localMusicPlayer.start();
                        });
                    } else {
                        localMusicPlayer.start();
                    }
                    Log.i(TAG, "local music started path=" + absolutePath
                            + " seek=" + seekTo
                            + " publishVol=" + MUSIC_PUBLISH_VOLUME
                            + " muteLocal=" + musicMuteLocalMonitor);
                } else {
                    Log.w(TAG, "local music load failed err=" + errorCode);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "playLocalMusic failed: " + t.getMessage());
        }
    }

    /** Boost music into the publish stream so remote listeners hear it clearly. */
    private void applyMusicMixVolumes(
            @Nullable im.zego.zegoexpress.ZegoMediaPlayer player) {
        if (player == null) return;
        try {
            player.setPublishVolume(MUSIC_PUBLISH_VOLUME);
            // When muteLocal is used, play volume is irrelevant; otherwise host must hear.
            player.setPlayVolume(musicMuteLocalMonitor || speakerMuted ? 0 : MUSIC_PLAY_VOLUME);
        } catch (Throwable t) {
            try {
                player.setVolume(MUSIC_PUBLISH_VOLUME);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Re-apply loud mix if the player already exists (e.g. after reconnect). */
    public void boostMusicMixVolume() {
        applyMusicMixVolumes(localMusicPlayer);
    }

    public void pauseLocalMusic() {
        try {
            if (localMusicPlayer != null) localMusicPlayer.pause();
        } catch (Throwable ignored) {
        }
    }

    public void resumeLocalMusic() {
        try {
            if (localMusicPlayer != null) {
                localMusicPlayer.start();
                return;
            }
            // Player was destroyed after stop — restart from last path.
            if (localMusicPath != null && !localMusicPath.isEmpty()) {
                playLocalMusic(localMusicPath);
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean hasLocalMusicPlayer() {
        return localMusicPlayer != null;
    }

    @Nullable
    public String getLocalMusicPath() {
        return localMusicPath;
    }

    public void stopLocalMusic() {
        try {
            if (localMusicPlayer != null) {
                localMusicPlayer.stop();
                if (engine != null) {
                    engine.destroyMediaPlayer(localMusicPlayer);
                }
            }
        } catch (Throwable ignored) {
        }
        localMusicPlayer = null;
        localMusicPath = null;
    }

    public boolean isLocalMusicPlaying() {
        try {
            return localMusicPlayer != null
                    && localMusicPlayer.getCurrentState()
                    == im.zego.zegoexpress.constants.ZegoMediaPlayerState.PLAYING;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public long getLocalMusicPositionMs() {
        try {
            return localMusicPlayer != null ? localMusicPlayer.getCurrentProgress() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public long getLocalMusicDurationMs() {
        try {
            return localMusicPlayer != null ? localMusicPlayer.getTotalDuration() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public void seekLocalMusic(long positionMs) {
        try {
            if (localMusicPlayer != null) {
                localMusicPlayer.seekTo(Math.max(0L, positionMs), errorCode -> {});
            }
        } catch (Throwable ignored) {
        }
    }

    private void ensureEngine() {
        if (!initialized && application != null) {
            init(application);
        }
        if (!initialized) {
            Log.w(TAG, "ZEGO engine not ready — waiting for server AppID + room token");
        }
    }

    public String getCurrentRoomId() { return currentRoomId; }
    public String getCurrentUserId() { return currentUserId; }
    public boolean isPublishing() { return publishingStreamId != null; }
    public boolean isMicEnabled() { return micEnabled; }
}
