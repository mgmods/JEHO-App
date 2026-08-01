package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.RoomDtos;

import java.util.Locale;

/** Home «مؤخراً / غرف المتابعين»: only joinable personal rooms. */
public final class RoomBrowseFilter {
    private RoomBrowseFilter() {}

    public static boolean isBrowsablePersonal(@Nullable RoomDtos.RoomDto room) {
        if (room == null || room.id == null || room.id.trim().isEmpty()) return false;
        if (room.agencyId != null && !room.agencyId.trim().isEmpty()) return false;
        if (room.roomKind != null && "agency".equalsIgnoreCase(room.roomKind.trim())) return false;
        if (room.status != null) {
            String s = room.status.trim().toLowerCase(Locale.US);
            if ("closed".equals(s) || "ended".equals(s)) return false;
        }
        return true;
    }
}
