package com.Dramizo.Series.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;

/** Room feed badges: personal vs agency, lock overlay. */
public final class RoomUiHelper {
    private RoomUiHelper() {}

    public static boolean isSupportRoom(@Nullable RoomDtos.RoomDto room) {
        if (room == null) return false;
        if (room.isSupport) return true;
        if (room.roomKind == null) return false;
        return "support".equalsIgnoreCase(room.roomKind.trim());
    }

    public static boolean isAgencyRoom(@Nullable RoomDtos.RoomDto room) {
        if (room == null) return false;
        if (isSupportRoom(room)) return false;
        // Prefer explicit roomKind — personal/standard rooms must never show as agency
        // even if agencyId is stale or the host belongs to an agency.
        if (room.roomKind != null && !room.roomKind.trim().isEmpty()) {
            return "agency".equalsIgnoreCase(room.roomKind.trim());
        }
        return room.agencyId != null && !room.agencyId.isEmpty();
    }

    public static void bindTypeBadge(@Nullable TextView badge, @Nullable RoomDtos.RoomDto room) {
        bindTypeBadge(badge, null, room);
    }

    /**
     * Type chip + optional agency verification icon beside the green «وكالة» label.
     */
    public static void bindTypeBadge(
            @Nullable TextView badge,
            @Nullable ImageView verifiedIcon,
            @Nullable RoomDtos.RoomDto room) {
        if (badge == null) return;
        if (room == null) {
            badge.setVisibility(View.GONE);
            if (verifiedIcon != null) verifiedIcon.setVisibility(View.GONE);
            return;
        }
        badge.setVisibility(View.VISIBLE);
        if (isSupportRoom(room)) {
            badge.setText(R.string.room_badge_support);
            badge.setBackgroundResource(R.drawable.bg_room_badge_support);
            if (verifiedIcon != null) verifiedIcon.setVisibility(View.GONE);
        } else if (isAgencyRoom(room)) {
            badge.setText(R.string.room_badge_agency);
            badge.setBackgroundResource(R.drawable.bg_room_badge_agency);
            if (verifiedIcon != null) {
                verifiedIcon.setVisibility(room.agencyIsVerified ? View.VISIBLE : View.GONE);
                if (room.agencyIsVerified) {
                    verifiedIcon.setImageResource(R.drawable.ic_agency_verified);
                }
            }
        } else {
            badge.setText(R.string.room_badge_personal);
            badge.setBackgroundResource(R.drawable.bg_room_badge_personal);
            if (verifiedIcon != null) verifiedIcon.setVisibility(View.GONE);
        }
    }

    public static void bindLockOverlay(@Nullable View lockOverlay, @Nullable RoomDtos.RoomDto room) {
        if (lockOverlay == null) return;
        lockOverlay.setVisibility(room != null && room.hasPassword ? View.VISIBLE : View.GONE);
    }

    /** Numeric id for room header/search — prefers API displayRoomId, then host publicId. */
    @NonNull
    public static String displayRoomId(@Nullable RoomDtos.RoomDto room) {
        if (room == null) return "—";
        if (room.displayRoomId != null && !room.displayRoomId.trim().isEmpty()) {
            return room.displayRoomId.trim();
        }
        if (room.host != null) {
            String pid = room.host.displayPublicId();
            if (!pid.isEmpty()) return pid;
        }
        return "—";
    }
}
