package com.Dramizo.Series.rtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * Standard VoIP audio session: focus + communication mode on enter, restore on leave.
 */
public final class RoomAudioSession {
    private static WeakReference<Context> appContext = new WeakReference<>(null);
    private static AudioFocusRequest focusRequest;
    private static boolean focusHeld;
    // True only while an external app (for example WhatsApp voice recording) owns capture.
    private static volatile boolean externalCaptureSuspended;

    public static void setExternalCaptureSuspended(boolean suspended) {
        externalCaptureSuspended = suspended;
    }

    public static boolean isExternalCaptureSuspended() {
        return externalCaptureSuspended;
    }

    private RoomAudioSession() {}

    /** Re-acquire VoIP routing after an external recorder has been confirmed stopped. */
    public static void acquireForExternalRestore(@Nullable Context context) {
        Context app = context != null ? context.getApplicationContext() : appContext.get();
        if (app == null) return;
        appContext = new WeakReference<>(app);
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (focusHeld && am.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                return;
            }
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
            if (focusHeld) am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } catch (Throwable ignored) {
        }
    }

    public static void acquire(@Nullable Context context) {
        Context app = context != null
                ? context.getApplicationContext()
                : appContext.get();
        if (app == null) return;
        appContext = new WeakReference<>(app);

        // Keep the room playback route stable while the room is active, including
        // when the user minimizes JEHO or has manually muted their microphone.
        // Only an explicitly detected external recorder may suspend this session.
        if (externalCaptureSuspended) {
            // A confirmed external recorder owns capture. Release only for this
            // explicit handoff, never merely because JEHO moved to background.
            release(app);
            return;
        }

        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
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
                am.requestAudioFocus(focusRequest);
            } else {
                am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            focusHeld = true;
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } catch (Throwable ignored) {
        }
    }

    public static void release(@Nullable Context context) {
        Context app = context != null
                ? context.getApplicationContext()
                : appContext.get();
        if (app == null) return;
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
