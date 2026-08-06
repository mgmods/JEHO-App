package com.Dramizo.Series.rtc;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.zego.ZegoEngineManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Voice RTC facade: {@code zego} (paid cloud) or {@code livekit} (free self-hosted OSS).
 * Call {@link #applyJoinSession} after room join — then use the same API as ZegoEngineManager.
 */
public final class RoomRtcEngine {
    private static final String TAG = "RoomRtcEngine";
    public static final String PROVIDER_ZEGO = "zego";
    public static final String PROVIDER_LIVEKIT = "livekit";

    private static RoomRtcEngine instance;
    private String provider = PROVIDER_ZEGO;
    @Nullable private String livekitUrl;
    @Nullable private String livekitRoomName;

    public static synchronized RoomRtcEngine getInstance() {
        if (instance == null) instance = new RoomRtcEngine();
        return instance;
    }

    private RoomRtcEngine() {}

    public String getProvider() {
        return provider;
    }

    public boolean isLiveKit() {
        return PROVIDER_LIVEKIT.equalsIgnoreCase(provider);
    }

    public boolean isZego() {
        return !isLiveKit();
    }

    /**
     * Read {@link RoomDtos.JoinRoomResult#voiceProvider} and connect the matching engine.
     */
    public synchronized void applyJoinSession(
            Context context,
            RoomDtos.JoinRoomResult session,
            @Nullable String fallbackRoomId,
            @Nullable String fallbackUserId) {
        if (session == null) return;
        String p = session.voiceProvider != null ? session.voiceProvider.trim() : PROVIDER_ZEGO;
        if (PROVIDER_LIVEKIT.equalsIgnoreCase(p)) {
            provider = PROVIDER_LIVEKIT;
            try {
                ZegoEngineManager.getInstance().setVoiceSessionActive(false);
                // Never block UI on engine teardown while painting the room.
                ZegoEngineManager.getInstance().hardLeaveRoom();
            } catch (Throwable ignored) {
            }
            livekitUrl = session.livekitUrl;
            livekitRoomName = session.livekitRoomName != null && !session.livekitRoomName.isEmpty()
                    ? session.livekitRoomName
                    : (session.zegoRoomId != null ? session.zegoRoomId : fallbackRoomId);
            String userId = session.userId != null ? session.userId
                    : (fallbackUserId != null ? fallbackUserId : "guest");
            LiveKitEngineManager.getInstance().connect(
                    context,
                    livekitUrl,
                    session.token,
                    livekitRoomName,
                    userId);
            Log.i(TAG, "RTC provider=livekit room=" + livekitRoomName
                    + " url=" + livekitUrl
                    + " canPublish=" + session.canPublish);
            return;
        }
        provider = PROVIDER_ZEGO;
        try {
            // Async disconnect — never run LiveKit disconnectBlocking on UI thread.
            LiveKitEngineManager.getInstance().hardLeaveRoom();
        } catch (Throwable ignored) {
        }
        try {
            ZegoEngineManager.getInstance().setVoiceSessionActive(true);
        } catch (Throwable ignored) {
        }
        if (session.appId > 0L) {
            ZegoEngineManager.getInstance().applyAppId(context, session.appId);
        }
        String zegoRoom = session.zegoRoomId != null ? session.zegoRoomId : fallbackRoomId;
        String userId = session.userId != null ? session.userId
                : (fallbackUserId != null ? fallbackUserId : "guest");
        ZegoEngineManager.getInstance().loginRoom(zegoRoom, userId, session.token);
        Log.i(TAG, "RTC provider=zego room=" + zegoRoom);
    }

    public void init(Application application) {
        ZegoEngineManager.getInstance().init(application);
        LiveKitEngineManager.getInstance().init(application);
    }

    public boolean isReady() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().isReady()
                : ZegoEngineManager.getInstance().isReady();
    }

    public void loginRoom(String roomId, String userId, String token) {
        if (isLiveKit()) {
            LiveKitEngineManager.getInstance().connect(
                    null, livekitUrl, token, roomId != null ? roomId : livekitRoomName, userId);
        } else {
            ZegoEngineManager.getInstance().loginRoom(roomId, userId, token);
        }
    }

    public void renewRoomToken(String roomId, String token) {
        if (isLiveKit()) {
            // LiveKit JWT is immutable — reconnect when publish rights change (audience → seat).
            // Skip only if already publishing with identical token payload (no-op renew).
            LiveKitEngineManager lk = LiveKitEngineManager.getInstance();
            if (lk.isReady() && lk.isPublishing()
                    && token != null && token.equals(lk.getLastToken())) {
                Log.i(TAG, "livekit skip reconnect — same token already live");
                return;
            }
            LiveKitEngineManager.getInstance().reconnectWithToken(
                    null, livekitUrl, token, roomId != null ? roomId : livekitRoomName, null);
            Log.i(TAG, "livekit reconnect with seat-aware token");
            return;
        }
        ZegoEngineManager.getInstance().renewRoomToken(roomId, token);
    }

    public void setMicEnabled(boolean enabled) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().setMicEnabled(enabled);
        else ZegoEngineManager.getInstance().setMicEnabled(enabled);
    }

    public boolean isMicEnabled() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().isMicEnabled()
                : ZegoEngineManager.getInstance().isMicEnabled();
    }

    public void setSpeakerMuted(boolean muted) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().setSpeakerMuted(muted);
        else ZegoEngineManager.getInstance().setSpeakerMuted(muted);
    }

    public boolean isSpeakerMuted() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().isSpeakerMuted()
                : ZegoEngineManager.getInstance().isSpeakerMuted();
    }

    public void startPublishingAudio(String streamId) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().startPublishingAudio(streamId);
        else ZegoEngineManager.getInstance().startPublishingAudio(streamId);
    }

    public void stopPublishing() {
        if (isLiveKit()) LiveKitEngineManager.getInstance().stopPublishing();
        else ZegoEngineManager.getInstance().stopPublishing();
    }

    public boolean isPublishing() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().isPublishing()
                : ZegoEngineManager.getInstance().isPublishing();
    }

    public String getPublishingStreamId() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().getPublishingStreamId()
                : ZegoEngineManager.getInstance().getPublishingStreamId();
    }

    public void startPlayingAudio(String streamId) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().startPlayingAudio(streamId);
        else ZegoEngineManager.getInstance().startPlayingAudio(streamId);
    }

    public void startPlayingAudio(String streamId, String roomId) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().startPlayingAudio(streamId, roomId);
        else ZegoEngineManager.getInstance().startPlayingAudio(streamId, roomId);
    }

    public void stopPlaying(String streamId) {
        if (isLiveKit()) LiveKitEngineManager.getInstance().stopPlaying(streamId);
        else ZegoEngineManager.getInstance().stopPlaying(streamId);
    }

    public void clearPausedPlayStreams() {
        if (isLiveKit()) LiveKitEngineManager.getInstance().clearPausedPlayStreams();
        else ZegoEngineManager.getInstance().clearPausedPlayStreams();
    }

    public Set<String> getPlayingStreamIds() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().getPlayingStreamIds()
                : ZegoEngineManager.getInstance().getPlayingStreamIds();
    }

    public boolean isInRoom(@Nullable String roomId) {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().isInRoom(roomId)
                : ZegoEngineManager.getInstance().isInRoom(roomId);
    }

    public String getCurrentRoomId() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().getCurrentRoomId()
                : ZegoEngineManager.getInstance().getCurrentRoomId();
    }

    public String getCurrentUserId() {
        return isLiveKit()
                ? LiveKitEngineManager.getInstance().getCurrentUserId()
                : ZegoEngineManager.getInstance().getCurrentUserId();
    }

    /**
     * Cut ALL voice providers (LiveKit + Zego). LiveKit disconnect is async so UI never freezes.
     */
    public void hardLeaveRoom() {
        try {
            LiveKitEngineManager.getInstance().hardLeaveRoom();
        } catch (Throwable ignored) {
        }
        try {
            ZegoEngineManager.getInstance().hardLeaveRoom();
        } catch (Throwable ignored) {
        }
    }

    public void logoutRoom() {
        // hardLeave already clears LiveKit + Zego sessions.
        hardLeaveRoom();
    }

    public void addRoomListener(ZegoEngineManager.RoomListener listener) {
        // Register on both; each engine only fires while it is the active session.
        ZegoEngineManager.getInstance().addRoomListener(listener);
        LiveKitEngineManager.getInstance().addRoomListener(listener);
    }

    public void removeRoomListener(ZegoEngineManager.RoomListener listener) {
        ZegoEngineManager.getInstance().removeRoomListener(listener);
        LiveKitEngineManager.getInstance().removeRoomListener(listener);
    }

    /** Drop listeners on the inactive engine so stray callbacks cannot zero seat waves. */
    public void preferActiveProviderListenersOnly(ZegoEngineManager.RoomListener listener) {
        if (listener == null) return;
        if (isLiveKit()) {
            ZegoEngineManager.getInstance().removeRoomListener(listener);
            LiveKitEngineManager.getInstance().addRoomListener(listener);
        } else {
            LiveKitEngineManager.getInstance().removeRoomListener(listener);
            ZegoEngineManager.getInstance().addRoomListener(listener);
        }
    }

    public boolean sendRoomChatMessage(
            @Nullable String roomId,
            @Nullable String message,
            @Nullable ZegoEngineManager.RoomChatSendCallback callback) {
        if (isLiveKit()) {
            // Socket.IO handles chat for LiveKit sessions.
            if (callback != null) callback.onResult(false, -10);
            return false;
        }
        return ZegoEngineManager.getInstance().sendRoomChatMessage(roomId, message, callback);
    }

    public static String audioStreamId(String userId) {
        return ZegoEngineManager.audioStreamId(userId);
    }

    // ── Music mix (Zego-only for now; LiveKit hosts use no-op) ──

    public void setLocalMusicEndListener(
            @Nullable ZegoEngineManager.LocalMusicEndListener listener) {
        ZegoEngineManager.getInstance().setLocalMusicEndListener(listener);
    }

    public void playLocalMusic(String path) {
        if (isLiveKit()) {
            Log.w(TAG, "room music mix not available on LiveKit yet");
            return;
        }
        ZegoEngineManager.getInstance().playLocalMusic(path);
    }

    public void playLocalMusic(String path, long seekMs) {
        if (isLiveKit()) return;
        ZegoEngineManager.getInstance().playLocalMusic(path, seekMs);
    }

    public void playLocalMusic(String path, long seekMs, boolean muteLocal) {
        if (isLiveKit()) return;
        ZegoEngineManager.getInstance().playLocalMusic(path, seekMs, muteLocal);
    }

    public void pauseLocalMusic() {
        if (!isLiveKit()) ZegoEngineManager.getInstance().pauseLocalMusic();
    }

    public void resumeLocalMusic() {
        if (!isLiveKit()) ZegoEngineManager.getInstance().resumeLocalMusic();
    }

    public void stopLocalMusic() {
        if (!isLiveKit()) ZegoEngineManager.getInstance().stopLocalMusic();
    }

    public void seekLocalMusic(long positionMs) {
        if (!isLiveKit()) ZegoEngineManager.getInstance().seekLocalMusic(positionMs);
    }

    public void boostMusicMixVolume() {
        if (!isLiveKit()) ZegoEngineManager.getInstance().boostMusicMixVolume();
    }

    public boolean isLocalMusicPlaying() {
        return !isLiveKit() && ZegoEngineManager.getInstance().isLocalMusicPlaying();
    }

    public boolean hasLocalMusicPlayer() {
        return !isLiveKit() && ZegoEngineManager.getInstance().hasLocalMusicPlayer();
    }

    public long getLocalMusicDurationMs() {
        return isLiveKit() ? 0L : ZegoEngineManager.getInstance().getLocalMusicDurationMs();
    }

    public long getLocalMusicPositionMs() {
        return isLiveKit() ? 0L : ZegoEngineManager.getInstance().getLocalMusicPositionMs();
    }

    public void applyAppId(Context context, long appId) {
        if (!isLiveKit()) ZegoEngineManager.getInstance().applyAppId(context, appId);
    }

    public void fetchAndApplyRemote(Context context, com.Dramizo.Series.data.remote.api.ConfigApi api) {
        ZegoEngineManager.getInstance().fetchAndApplyRemote(context, api);
    }

    /** Re-run headset/speaker routing (plug/unplug events). */
    public void reapplyAudioRoute() {
        if (isLiveKit()) {
            LiveKitEngineManager.getInstance().setSpeakerMuted(
                    LiveKitEngineManager.getInstance().isSpeakerMuted());
        } else {
            ZegoEngineManager.getInstance().setSpeakerMuted(
                    ZegoEngineManager.getInstance().isSpeakerMuted());
        }
    }
}
