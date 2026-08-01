package com.Dramizo.Series.util;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

/**
 * Gift SFX: normal gifts stay silent (GIF/video only).
 * Lucky / مردود coin clinks remain enabled via {@link #playLuckyCoins}.
 */
public final class GiftAudioFx {
    @Nullable private static MediaPlayer player;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Host room toggle — applies to normal gift WAV only. */
    private static volatile boolean roomGiftSoundsEnabled = false;

    private GiftAudioFx() {}

    public static void setRoomGiftSoundsEnabled(boolean enabled) {
        // Product: keep catalog gift WAV off; lucky coins use RoomSoundFx.playCoin.
        roomGiftSoundsEnabled = false;
        if (!enabled) stop();
    }

    public static void resetRoomGiftSounds() {
        roomGiftSoundsEnabled = false;
        stop();
    }

    public static boolean isGiftAudioMuted(@Nullable Context context) {
        return true;
    }

    public static boolean isGloballyMuted(@Nullable Context context) {
        return RoomSoundFx.isMuted();
    }

    /** Normal gifts: silent. */
    public static void playForGift(Context context, @Nullable String giftIdOrName) {
        stop();
    }

    /** Coin clinks when lucky gift rains / مردود hits — bypasses gift mute. */
    public static void playLuckyCoins(Context context, int bursts) {
        if (context == null || RoomSoundFx.isMuted()) return;
        Context app = context.getApplicationContext();
        int count = Math.max(3, Math.min(10, bursts + 2));
        // One cascading gold sparkle sequence (not tiny single beeps).
        RoomSoundFx.playLuckyGold(app, count);
        // Second wave mid-flight so the FX stays audible while coins travel.
        MAIN.postDelayed(() -> {
            if (!RoomSoundFx.isMuted()) {
                RoomSoundFx.playLuckyGold(app, Math.max(2, count / 2));
            }
        }, 900L);
    }

    public static void stop() {
        MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.stop();
        } catch (Exception ignored) {
        }
        try {
            mp.release();
        } catch (Exception ignored) {
        }
    }
}
