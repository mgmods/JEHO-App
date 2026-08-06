package com.Dramizo.Series.rtc;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.Nullable;

/**
 * Room voice routing: prefer wired / BT headsets over forced loudspeaker.
 * Forcing {@code setSpeakerphoneOn(true)} / Zego {@code setAudioRouteToSpeaker(true)}
 * after every publish is what keeps sound on the outer speaker with headsets plugged in.
 */
public final class RoomAudioRoute {
    private RoomAudioRoute() {}

    /** Wired headset, USB, or Bluetooth SCO/A2DP connected for playback. */
    public static boolean hasExternalHeadset(@Nullable Context context) {
        if (context == null) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                if (devices != null) {
                    for (AudioDeviceInfo d : devices) {
                        if (d == null) continue;
                        int t = d.getType();
                        if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                                || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                || t == AudioDeviceInfo.TYPE_USB_HEADSET
                                || t == AudioDeviceInfo.TYPE_USB_DEVICE
                                || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                                || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                            return true;
                        }
                    }
                }
            }
            // Legacy fallbacks.
            if (am.isWiredHeadsetOn()) return true;
            if (am.isBluetoothA2dpOn() || am.isBluetoothScoOn()) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * @param wantOpenSpeaker when false = earpiece / muted-sense; when true = loudspeaker
     *                        only if no external headset is attached.
     */
    public static void applyCommunicationRoute(@Nullable Context context, boolean wantOpenSpeaker) {
        if (context == null) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            boolean external = hasExternalHeadset(context);
            // Never force loudspeaker while a headset/BT route is available.
            am.setSpeakerphoneOn(wantOpenSpeaker && !external);
            if (external && am.isBluetoothScoAvailableOffCall()) {
                // Help SCO headsets pull voice; ignore failures on pure A2DP buds.
                try {
                    if (!am.isBluetoothScoOn()) am.startBluetoothSco();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Zego: route to speaker only when user wants open speaker AND no headset. */
    public static boolean zegoRouteToSpeaker(
            @Nullable Context context, boolean speakerMuted) {
        if (speakerMuted) return false;
        return !hasExternalHeadset(context);
    }
}
