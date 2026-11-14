package com.Dramizo.Series.util;

import android.view.View;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.IncludeMyRoomCardBinding;
import com.bumptech.glide.Glide;

import java.util.List;

/** Binds Mikoo-style "My Room" card on Home / Messages. */
public final class MyRoomCardBinder {
    private MyRoomCardBinder() {}

    public static void bind(
            @Nullable IncludeMyRoomCardBinding card,
            @Nullable AuthDtos.UserDto me,
            @Nullable String myUserId,
            @Nullable List<RoomDtos.RoomDto> rooms,
            @Nullable View.OnClickListener onOpenMyRoom,
            @Nullable View.OnClickListener ignoredCreate
    ) {
        if (card == null) return;
        RoomDtos.RoomDto owned = findOwnedPersonal(rooms, myUserId);
        card.imgMyRoomCreateIcon.setVisibility(View.GONE);
        card.imgMyRoomAvatar.setVisibility(View.VISIBLE);
        String cover = null;
        if (owned != null) {
            cover = owned.coverUrl != null ? owned.coverUrl.trim() : "";
            if (cover.isEmpty() || RoomCardBinder.isGenericServerCoverForList(cover)) {
                if (owned.host != null && owned.host.avatarUrl != null && !owned.host.avatarUrl.isEmpty()) {
                    cover = owned.host.avatarUrl;
                } else {
                    cover = owned.coverUrl;
                }
            }
        }
        if ((cover == null || cover.isEmpty()) && me != null) {
            cover = me.avatarUrl;
        }
        Glide.with(card.imgMyRoomAvatar)
                .load(AssetCatalog.absoluteUrl(cover))
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .centerCrop()
                .into(card.imgMyRoomAvatar);

        String hostName = resolveDisplayName(me);
        if ((hostName == null || hostName.isEmpty()) && owned != null && owned.host != null) {
            hostName = resolveDisplayName(owned.host);
        }
        String roomTitle = owned != null && owned.title != null && !owned.title.isEmpty()
                ? owned.title.trim() : null;
        if (hostName != null && !hostName.isEmpty()) {
            card.tvMyRoomTitle.setText(hostName);
            if (roomTitle != null && !roomTitle.isEmpty()) {
                card.tvMyRoomSubtitle.setText(roomTitle);
            } else {
                card.tvMyRoomSubtitle.setText(card.getRoot().getContext().getString(R.string.my_room));
            }
        } else if (roomTitle != null && !roomTitle.isEmpty()) {
            card.tvMyRoomTitle.setText(roomTitle);
            card.tvMyRoomSubtitle.setText(card.getRoot().getContext().getString(R.string.my_room));
        } else {
            card.tvMyRoomTitle.setText(card.getRoot().getContext().getString(R.string.my_room));
            card.tvMyRoomSubtitle.setText(card.getRoot().getContext().getString(R.string.my_room));
        }
        card.btnMyRoomGo.setText(R.string.enter_room);
        card.tvMyRoomTitle.setSelected(true);
        card.tvMyRoomSubtitle.setSelected(true);
        card.getRoot().setOnClickListener(onOpenMyRoom);
        card.btnMyRoomGo.setOnClickListener(onOpenMyRoom);
    }

    @Nullable
    public static RoomDtos.RoomDto findOwned(
            @Nullable List<RoomDtos.RoomDto> rooms,
            @Nullable String myUserId
    ) {
        RoomDtos.RoomDto personal = findOwnedPersonal(rooms, myUserId);
        if (personal != null) return personal;
        return findOwnedAgency(rooms, myUserId);
    }

    /** Personal STANDARD room only (not agency). */
    @Nullable
    public static RoomDtos.RoomDto findOwnedPersonal(
            @Nullable List<RoomDtos.RoomDto> rooms,
            @Nullable String myUserId
    ) {
        if (rooms == null || myUserId == null || myUserId.isEmpty()) return null;
        for (RoomDtos.RoomDto r : rooms) {
            if (r == null) continue;
            if (!isOwnedBy(r, myUserId)) continue;
            if (RoomUiHelper.isAgencyRoom(r)) continue;
            return r;
        }
        return null;
    }

    /** Agency room owned/hosted by this user. */
    @Nullable
    public static RoomDtos.RoomDto findOwnedAgency(
            @Nullable List<RoomDtos.RoomDto> rooms,
            @Nullable String myUserId
    ) {
        if (rooms == null || myUserId == null || myUserId.isEmpty()) return null;
        for (RoomDtos.RoomDto r : rooms) {
            if (r == null) continue;
            if (!isOwnedBy(r, myUserId)) continue;
            if (RoomUiHelper.isAgencyRoom(r)) return r;
        }
        return null;
    }

    private static boolean isOwnedBy(RoomDtos.RoomDto r, String myUserId) {
        if (myUserId.equals(r.hostId)) return true;
        return r.host != null && myUserId.equals(r.host.id);
    }

    @Nullable
    private static String resolveDisplayName(@Nullable AuthDtos.UserDto user) {
        if (user == null) return null;
        if (user.displayName != null && !user.displayName.isEmpty()) return user.displayName.trim();
        if (user.username != null && !user.username.isEmpty()) return user.username.trim();
        return null;
    }
}
