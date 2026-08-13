package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Seat mic reaction stickers — catalog from {@code GET /config/seat-stickers}.
 * Media is remote (GIF/WebP URLs); APK only keeps a tiny fallback drawable.
 */
public final class SeatReactionEmojis {
    private SeatReactionEmojis() {}

    public static final int FALLBACK_DRAWABLE = R.drawable.emoji_reaction_fallback;

    public static final class Item {
        public final String key;
        public final String url;
        public final String name;

        public Item(String key, String url, String name) {
            this.key = key;
            this.url = url;
            this.name = name;
        }
    }

    private static final CopyOnWriteArrayList<Item> ITEMS = new CopyOnWriteArrayList<>();

    /** Apply catalog from network/cache. Empty list disables stickers until loaded. */
    public static void setCatalog(@Nullable List<Item> items) {
        ITEMS.clear();
        if (items == null || items.isEmpty()) return;
        ITEMS.addAll(items);
    }

    public static void setFromDto(@Nullable MiscDtos.SeatStickersDto dto) {
        if (dto == null || dto.items == null || dto.items.isEmpty()) {
            setCatalog(Collections.emptyList());
            return;
        }
        List<Item> next = new ArrayList<>(dto.items.size());
        for (MiscDtos.SeatStickerItemDto row : dto.items) {
            if (row == null) continue;
            String key = row.key != null ? row.key.trim().toLowerCase(Locale.US) : "";
            String url = row.url != null ? row.url.trim() : "";
            if (key.isEmpty() || url.isEmpty()) continue;
            next.add(new Item(key, url, row.name != null ? row.name : key));
        }
        setCatalog(next);
    }

    public static List<Item> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    public static int size() {
        return ITEMS.size();
    }

    public static boolean isAllowed(String key) {
        return indexOf(key) >= 0;
    }

    public static int indexOf(String key) {
        if (key == null || key.isEmpty()) return -1;
        String k = key.trim().toLowerCase(Locale.US);
        for (int i = 0; i < ITEMS.size(); i++) {
            if (k.equals(ITEMS.get(i).key)) return i;
        }
        return -1;
    }

    public static int drawableForKey(String key) {
        return isAllowed(key) ? FALLBACK_DRAWABLE : 0;
    }

    @Nullable
    public static String urlForKey(String key) {
        int i = indexOf(key);
        if (i < 0) return null;
        String url = ITEMS.get(i).url;
        return url != null && !url.isEmpty() ? url : null;
    }

    /** @deprecated use {@link #urlForKey(String)} — assets no longer ship in APK. */
    @Deprecated
    @Nullable
    public static String assetUriForKey(String key) {
        return urlForKey(key);
    }

    public static boolean isWebpUrl(@Nullable String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.US);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".webp");
    }

    /** @deprecated prefer {@link #isWebpUrl(String)}. */
    @Deprecated
    public static boolean isWebpKey(String key) {
        return isWebpUrl(urlForKey(key));
    }

    /** Legacy KEYS array for old call sites — mirrors current catalog keys. */
    public static String[] keysArray() {
        String[] keys = new String[ITEMS.size()];
        for (int i = 0; i < ITEMS.size(); i++) keys[i] = ITEMS.get(i).key;
        return keys;
    }
}
