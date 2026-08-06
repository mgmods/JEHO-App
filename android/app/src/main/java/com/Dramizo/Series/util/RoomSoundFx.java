package com.Dramizo.Series.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Lightweight room sound FX (clap / gift / join / mute). */
public final class RoomSoundFx {
    private enum Kind { CLAP, GIFT, JOIN, MUTE }

    private static SoundPool pool;
    private static int clapId;
    private static int giftId;
    private static int joinId;
    private static int muteId;
    private static boolean ready;
    private static boolean loading;

    private static boolean mutedFx;

    private RoomSoundFx() {}

    public static void setMuted(boolean muted) {
        mutedFx = muted;
    }

    public static boolean isMuted() {
        return mutedFx;
    }

    public static void init(Context context) {
        if (ready || loading) return;
        loading = true;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
            clapId = loadRaw(context, "sfx_clap");
            giftId = loadRaw(context, "sfx_gift");
            joinId = loadRaw(context, "sfx_join");
            muteId = loadRaw(context, "sfx_mute");
            ready = clapId != 0 || giftId != 0 || joinId != 0 || muteId != 0;
        } catch (Exception ignored) {
            ready = false;
        } finally {
            loading = false;
        }
    }

    private static int loadRaw(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "raw", context.getPackageName());
        if (id == 0 || pool == null) return 0;
        return pool.load(context, id, 1);
    }

    public static void playClap(Context c) { play(c, Kind.CLAP, clapId, 0.95f); }
    public static void playGift(Context c) {
        if (GiftAudioFx.isGiftAudioMuted(c)) return;
        play(c, Kind.GIFT, giftId, 1f);
    }
    /** Lucky / مردود gold — multi-chime sparkle (ignores gift WAV mute). */
    public static void playCoin(Context c) {
        playLuckyGold(c, 1);
    }

    /** Professional gold cascade: rising coin tones over ~1–2s. */
    public static void playLuckyGold(Context c, int waves) {
        if (mutedFx || c == null) return;
        Context app = c.getApplicationContext();
        init(app);
        // Soft max — ToneGenerator multi-open native-crashes some MediaTek (Hot 30) phones.
        int count = Math.max(1, Math.min(2, waves));
        Handler main = new Handler(Looper.getMainLooper());
        for (int i = 0; i < count; i++) {
            final int step = i;
            main.postDelayed(() -> {
                if (mutedFx) return;
                try {
                    float rate = 0.95f + step * 0.08f;
                    if (pool != null && giftId != 0) {
                        float vol = 0.6f + step * 0.1f;
                        pool.play(giftId, vol, vol, 1, 0, Math.min(1.4f, rate));
                        return;
                    }
                    // Fallback: one short tone only (never cascade generators).
                    if (step == 0) {
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
                        tg.startTone(ToneGenerator.TONE_PROP_ACK, 90);
                        main.postDelayed(() -> {
                            try { tg.release(); } catch (Exception ignored) {}
                        }, 200);
                    }
                } catch (Throwable ignored) {
                }
            }, i * 160L);
        }
    }
    public static void playJoin(Context c) { play(c, Kind.JOIN, joinId, 0.8f); }
    public static void playMute(Context c) { play(c, Kind.MUTE, muteId, 0.7f); }

    private static void play(Context c, Kind kind, int soundId, float vol) {
        if (mutedFx) return;
        Context app = c.getApplicationContext();
        init(app);
        // SoundPool may still be decoding — retry shortly so gift send always has audio.
        if (pool != null && soundId != 0) {
            int played = pool.play(soundId, vol, vol, 1, 0, 1f);
            if (played == 0) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (pool != null) pool.play(soundId, vol, vol, 1, 0, 1f);
                }, 80);
            }
            return;
        }
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
            int tone;
            int durationMs;
            switch (kind) {
                case GIFT:
                    tone = ToneGenerator.TONE_PROP_ACK;
                    durationMs = 260;
                    break;
                case MUTE:
                    tone = ToneGenerator.TONE_PROP_NACK;
                    durationMs = 160;
                    break;
                case JOIN:
                    tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
                    durationMs = 200;
                    break;
                default:
                    tone = ToneGenerator.TONE_PROP_BEEP;
                    durationMs = 140;
                    break;
            }
            tg.startTone(tone, durationMs);
            new Handler(Looper.getMainLooper()).postDelayed(tg::release, durationMs + 80);
        } catch (Exception ignored) {
        }
    }

    public static void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        ready = false;
        loading = false;
        clapId = giftId = joinId = muteId = 0;
    }
}
