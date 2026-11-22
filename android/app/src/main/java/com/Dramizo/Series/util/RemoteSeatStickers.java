package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

/**
 * Loads seat stickers from {@code GET /config/seat-stickers} into
 * {@link SeatReactionEmojis} (+ encrypted disk cache).
 */
public final class RemoteSeatStickers {
    private static volatile MiscDtos.SeatStickersDto cached;
    private static final java.util.concurrent.atomic.AtomicBoolean REFRESH_IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicLong LAST_REFRESH_AT =
            new java.util.concurrent.atomic.AtomicLong(-1L);
    private static final long REFRESH_INTERVAL_MS = 60_000L;

    private RemoteSeatStickers() {}

    @Nullable
    public static MiscDtos.SeatStickersDto get() {
        return cached;
    }

    public static void hydrateFromCache(AppContainer c) {
        if (c == null) return;
        try {
            MiscDtos.SeatStickersDto disk = c.getFeatureCache().getJson(
                    EncryptedFeatureCache.UID_APP,
                    EncryptedFeatureCache.NS_SEAT_STICKERS,
                    MiscDtos.SeatStickersDto.class,
                    0L);
            if (disk != null) {
                cached = disk;
                SeatReactionEmojis.setFromDto(disk);
            }
        } catch (Exception ignored) {
        }
    }

    /** Network refresh + encrypted write. Call from a background thread. */
    public static void refreshBlocking(AppContainer c) {
        if (c == null || !REFRESH_IN_FLIGHT.compareAndSet(false, true)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long previous = LAST_REFRESH_AT.get();
        if (previous >= 0L && now - previous < REFRESH_INTERVAL_MS) {
            REFRESH_IN_FLIGHT.set(false);
            return;
        }
        boolean completed = false;
        try {
            Result<MiscDtos.SeatStickersDto> r = ApiCall.execute(c.getConfigApi().seatStickers(System.currentTimeMillis()));
            if (r == null || !r.success || r.data == null) return;
            cached = r.data;
            SeatReactionEmojis.setFromDto(r.data);
            c.getFeatureCache().putJson(
                    EncryptedFeatureCache.UID_APP,
                    EncryptedFeatureCache.NS_SEAT_STICKERS,
                    r.data);
            completed = true;
        } catch (Exception ignored) {
        } finally {
            if (completed) LAST_REFRESH_AT.set(android.os.SystemClock.elapsedRealtime());
            REFRESH_IN_FLIGHT.set(false);
        }
    }}
