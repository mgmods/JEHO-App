package com.Dramizo.Series.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Local recently-visited voice rooms for the Home «مؤخراً» tab. */
public final class RecentRoomsStore {
    private static final String PREFS = "home_recent_rooms";
    private static final String KEY = "rooms_json";
    private static final int MAX = 40;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<RoomDtos.RoomDto>>() {}.getType();

    private RecentRoomsStore() {}

    public static void remember(@NonNull Context context, @Nullable RoomDtos.RoomDto room) {
        if (room == null || TextUtils.isEmpty(room.id)) return;
        if (!RoomBrowseFilter.isBrowsablePersonal(room)) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RoomDtos.RoomDto> list = load(prefs);
        Iterator<RoomDtos.RoomDto> it = list.iterator();
        while (it.hasNext()) {
            RoomDtos.RoomDto r = it.next();
            if (r != null && room.id.equals(r.id)) it.remove();
        }
        list.add(0, room);
        while (list.size() > MAX) list.remove(list.size() - 1);
        prefs.edit().putString(KEY, GSON.toJson(list)).apply();
    }

    /** Drop stale / closed / agency rooms that are no longer joinable. */
    public static void retainOnly(@NonNull Context context, @NonNull java.util.Set<String> liveIds) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<RoomDtos.RoomDto> list = load(prefs);
        boolean changed = false;
        Iterator<RoomDtos.RoomDto> it = list.iterator();
        while (it.hasNext()) {
            RoomDtos.RoomDto r = it.next();
            if (r == null || r.id == null || !liveIds.contains(r.id)
                    || !RoomBrowseFilter.isBrowsablePersonal(r)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) prefs.edit().putString(KEY, GSON.toJson(list)).apply();
    }

    @NonNull
    public static List<RoomDtos.RoomDto> list(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return load(prefs);
    }

    @NonNull
    private static List<RoomDtos.RoomDto> load(SharedPreferences prefs) {
        String raw = prefs.getString(KEY, null);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        try {
            List<RoomDtos.RoomDto> parsed = GSON.fromJson(raw, LIST_TYPE);
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
