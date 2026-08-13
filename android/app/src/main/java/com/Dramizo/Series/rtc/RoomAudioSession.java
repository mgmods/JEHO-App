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

    private RoomAudioSession() {}

    public static void acquire(@Nullable Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        appContext = new WeakReference<>(app);
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
