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
        if (c == null) return;
        try {
            Result<MiscDtos.SeatStickersDto> r = ApiCall.execute(c.getConfigApi().seatStickers());
            if (!r.success || r.data == null) return;
            cached = r.data;
            SeatReactionEmojis.setFromDto(r.data);
            c.getFeatureCache().putJson(
                    EncryptedFeatureCache.UID_APP,
                    EncryptedFeatureCache.NS_SEAT_STICKERS,
                    r.data);
        } catch (Exception ignored) {
        }
    }
}
