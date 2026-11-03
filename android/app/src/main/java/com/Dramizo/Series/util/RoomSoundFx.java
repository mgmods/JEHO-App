package com.Dramizo.Series.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Lightweight room sound FX (clap / gift / join / mute). */
public final class RoomSoundFx {
    private enum Kind { CLAP, GIFT, JOIN, MUTE, CHEST }

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
            pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build();
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
    /**
     * Full chest blast: deep low boom, sharp explosive crack, airy debris and
     * bright metallic coin cascades. Synthesized locally so every room member
     * hears the same clear effect without relying on an external audio URL.
     */
    public static void playChestExplosion(Context c) {
        if (mutedFx || c == null) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> synthesizeChestExplosion(app), "jeho-chest-audio").start();
    }

    private static void synthesizeChestExplosion(Context context) {
        final int sampleRate = 24000;
        final int count = (int) (sampleRate * 2.25f);
        final short[] pcm = new short[count];
        final float[] coinStarts = {0.48f, 0.62f, 0.78f, 0.94f, 1.12f,
                1.28f, 1.43f, 1.61f, 1.78f, 1.92f};
        final float[] coinFreqs = {1480f, 1960f, 1720f, 2250f, 1320f,
                2080f, 1840f, 2460f, 1580f, 2210f};
        final float[] coinAmps = {0.16f, 0.13f, 0.12f, 0.10f, 0.12f,
                0.10f, 0.09f, 0.08f, 0.08f, 0.07f};
        double lowNoise = 0d, highNoiseLow = 0d, debrisLow = 0d;
        long seed = 0x5EED1234L;
        double peak = 0d;
        double[] mix = new double[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) sampleRate;
            seed = (seed * 1664525L + 1013904223L) & 0xffffffffL;
            double noise = ((seed / 4294967295.0) * 2.0) - 1.0;

            double lowA = Math.exp(-2.0 * Math.PI * 180.0 / sampleRate);
            lowNoise = (1.0 - lowA) * noise + lowA * lowNoise;
            double highA = Math.exp(-2.0 * Math.PI * 900.0 / sampleRate);
            highNoiseLow = (1.0 - highA) * noise + highA * highNoiseLow;
            double debrisA = Math.exp(-2.0 * Math.PI * 1800.0 / sampleRate);
            debrisLow = (1.0 - debrisA) * noise + debrisA * debrisLow;

            double boomEnv = Math.exp(-t * 3.2);
            double boom = 0.72 * Math.sin(2.0 * Math.PI * (48.0 * t - 9.0 * t * t)) * boomEnv
                    + 1.26 * lowNoise * boomEnv;
            double crack = 0.23 * (noise - highNoiseLow) * Math.exp(-t * 10.0);
            double transientHit = 0.42 * Math.exp(-Math.pow((t - 0.018) / 0.012, 2.0))
                    * Math.sin(2.0 * Math.PI * 95.0 * t);
            double tailEnv = Math.max(0.0, Math.min(1.0, (t - 0.12) / 0.18))
                    * Math.exp(-Math.max(0.0, t - 0.12) * 2.1);
            double debris = 0.055 * (noise - debrisLow) * tailEnv;
            double coins = 0d;
            for (int k = 0; k < coinStarts.length; k++) {
                double elapsed = t - coinStarts[k];
                if (elapsed >= 0d && elapsed < 0.48d) {
                    double env = Math.exp(-elapsed * 13.0);
                    double f = coinFreqs[k];
                    coins += coinAmps[k] * env * (Math.sin(2.0 * Math.PI * f * elapsed)
                            + 0.38 * Math.sin(2.0 * Math.PI * f * 1.47 * elapsed));
                }
            }
            double whoosh = 0.035 * (noise - highNoiseLow)
                    * Math.max(0.0, Math.min(1.0, t / 0.12)) * Math.exp(-t * 2.0);
            double value = Math.tanh((boom + crack + transientHit + debris + coins + whoosh) * 1.45);
            mix[i] = value;
            peak = Math.max(peak, Math.abs(value));
        }
        double gain = peak > 0.0001 ? 0.96 / peak : 1.0;
        for (int i = 0; i < count; i++) {
            pcm[i] = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, Math.round(mix[i] * gain * 32767.0)));
        }
        AudioTrack track = null;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack(attributes, format, pcm.length * 2,
                    AudioTrack.MODE_STATIC, 0);
            track.setVolume(1.0f);
            int written = track.write(pcm, 0, pcm.length);
            if (written <= 0) {
                track.release();
                return;
            }
            track.play();
            final AudioTrack playing = track;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { playing.stop(); } catch (Exception ignored) { }
                try { playing.release(); } catch (Exception ignored) { }
            }, 2600L);
        } catch (Throwable error) {
            if (track != null) {
                try { track.release(); } catch (Exception ignored) { }
            }
            playChestFallbackTone();
        }
    }

    /** Last-resort audible signal only if the bundled explosion file cannot be opened. */
    private static void playChestFallbackTone() {
        if (mutedFx) return;
        try {
            ToneGenerator generator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            generator.startTone(ToneGenerator.TONE_PROP_NACK, 650);
            new Handler(Looper.getMainLooper()).postDelayed(generator::release, 760L);
        } catch (Exception ignored) {
        }
    }

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
                case CHEST:
                    tone = ToneGenerator.TONE_PROP_NACK;
                    durationMs = 420;
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
