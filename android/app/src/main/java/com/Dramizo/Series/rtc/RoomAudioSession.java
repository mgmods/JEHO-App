package com.Dramizo.Series.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.List;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * Standard VoIP audio session: focus + communication mode on enter, restore on leave.
 */
public final class RoomAudioSession {
    private static WeakReference<Context> appContext = new WeakReference<>(null);
    private static AudioFocusRequest focusRequest;
    private static boolean focusHeld;
    private static AudioManager recordingAudioManager;
    private static AudioManager.AudioRecordingCallback recordingCallback;
    private static boolean externalRecordingActive;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RoomAudioSession() {}

    /**
     * Android 10+ reports active microphone clients by UID. When another app records,
     * temporarily mute JEHO's uplink AND room playback so the voice note cannot pick up
     * the room acoustically. Restore the user's exact mic/speaker choices afterwards.
     */
    private static synchronized void startExternalRecordingMonitor(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        AudioManager manager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        if (recordingAudioManager == manager && recordingCallback != null) {
            refreshExternalRecordingState(manager);
            return;
        }
        stopExternalRecordingMonitor();
        recordingAudioManager = manager;
        recordingCallback = new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                refreshExternalRecordingState(manager, configs);
            }
        };
        try {
            manager.registerAudioRecordingCallback(recordingCallback, MAIN);
            refreshExternalRecordingState(manager);
        } catch (Throwable error) {
            recordingCallback = null;
            recordingAudioManager = null;
        }
    }

    private static void refreshExternalRecordingState(AudioManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            refreshExternalRecordingState(manager, manager.getActiveRecordingConfigurations());
        } catch (Throwable ignored) {
        }
    }

    private static void refreshExternalRecordingState(
            AudioManager manager, List<AudioRecordingConfiguration> configs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || manager != recordingAudioManager) return;
        boolean external = false;
        if (configs != null) {
            int ownUid = Process.myUid();
            for (AudioRecordingConfiguration config : configs) {
                if (config != null && config.getClientUid() != ownUid) {
                    external = true;
                    break;
                }
            }
        }
        if (external == externalRecordingActive) return;
        externalRecordingActive = external;
        if (external) {
            RoomRtcEngine.getInstance().setExternalCaptureActive(true);
            suspendForExternalRecording(manager);
        } else {
            resumeAfterExternalRecording(manager);
            RoomRtcEngine.getInstance().setExternalCaptureActive(false);
        }
    }

    private static void suspendForExternalRecording(AudioManager manager) {
        try {
            if (focusHeld) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    manager.abandonAudioFocusRequest(focusRequest);
                } else {
                    manager.abandonAudioFocus(null);
                }
                focusHeld = false;
                focusRequest = null;
            }
        } catch (Throwable ignored) {
        }
        RoomAudioRoute.releaseCommunicationRoute(appContext.get());
    }

    private static void resumeAfterExternalRecording(AudioManager manager) {
        Context app = appContext.get();
        if (app == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(false)
                        .build();
                focusHeld = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                focusHeld = manager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            RoomAudioRoute.applyCommunicationRoute(app, !RoomRtcEngine.getInstance().isSpeakerMuted());
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void stopExternalRecordingMonitor() {
        AudioManager manager = recordingAudioManager;
        AudioManager.AudioRecordingCallback callback = recordingCallback;
        recordingAudioManager = null;
        recordingCallback = null;
        externalRecordingActive = false;
        if (manager != null && callback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { manager.unregisterAudioRecordingCallback(callback); } catch (Throwable ignored) {}
        }
        RoomRtcEngine.getInstance().setExternalCaptureActive(false);
    }

    public static void acquire(@Nullable Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        appContext = new WeakReference<>(app);
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (!externalRecordingActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attrs)
                            .setAcceptsDelayedFocusGain(true)
                            .setWillPauseWhenDucked(false)
                            .build();
                    focusHeld = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                } else {
                    focusHeld = am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                }
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            }
        } catch (Throwable ignored) {
        }
        startExternalRecordingMonitor(app);
    }

    public static void release(@Nullable Context context) {
        Context app = context != null
                ? context.getApplicationContext()
                : appContext.get();
        if (app == null) return;
        stopExternalRecordingMonitor();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (focusHeld) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    am.abandonAudioFocusRequest(focusRequest);
                } else {
                    am.abandonAudioFocus(null);
                }
                focusHeld = false;
                focusRequest = null;
            }
        } catch (Throwable ignored) {
        }
        RoomAudioRoute.releaseCommunicationRoute(app);
    }
}
