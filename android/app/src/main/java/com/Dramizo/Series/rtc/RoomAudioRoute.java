package com.Dramizo.Series.rtc;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.Nullable;

/**
 * Room voice routing: prefer wired / BT headsets over forced loudspeaker.
 */
public final class RoomAudioRoute {
    private RoomAudioRoute() {}

    /** Wired/USB headset plugged in. */
    public static boolean hasWiredHeadset(@Nullable Context context) {
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
                                || t == AudioDeviceInfo.TYPE_USB_DEVICE) {
                            return true;
                        }
                    }
                }
            }
            return am.isWiredHeadsetOn();
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** BT voice (SCO/BLE headset) — safe to start SCO. Excludes A2DP-only buds. */
    public static boolean hasBluetoothScoHeadset(@Nullable Context context) {
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
                        if (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || t == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                            return true;
                        }
                    }
                }
            }
            return am.isBluetoothScoOn();
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Any external output (wired, USB, BT A2DP/SCO) — do not force loudspeaker. */
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
            am.setSpeakerphoneOn(wantOpenSpeaker && !external);
            // SCO only for HFP/BLE voice headsets — never on pure A2DP (causes lag/crash with WebRTC).
            if (hasBluetoothScoHeadset(context) && am.isBluetoothScoAvailableOffCall()) {
                try {
                    if (!am.isBluetoothScoOn()) am.startBluetoothSco();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Restore system audio after leaving a voice room. */
    public static void releaseCommunicationRoute(@Nullable Context context) {
        if (context == null) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            try {
                if (am.isBluetoothScoOn()) am.stopBluetoothSco();
            } catch (Throwable ignored) {
            }
            try {
                am.setBluetoothScoOn(false);
            } catch (Throwable ignored) {
            }
            am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Headset plug/unplug: switch speaker vs earpiece only — no mode/SCO churn.
     */
    public static void applyRouteOnly(@Nullable Context context, boolean wantOpenSpeaker) {
        if (context == null) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            boolean external = hasExternalHeadset(context);
            am.setSpeakerphoneOn(wantOpenSpeaker && !external);
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
