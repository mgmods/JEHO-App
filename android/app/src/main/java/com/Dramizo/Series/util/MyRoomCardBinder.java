package com.Dramizo.Series.util;

import android.view.View;
import android.widget.TextView;

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
        RoomDtos.RoomDto owned = findOwned(rooms, myUserId);
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

        String title;
        if (owned != null && owned.title != null && !owned.title.isEmpty()) {
            title = owned.title;
        } else if (me != null && me.displayName != null && !me.displayName.isEmpty()) {
            title = me.displayName;
        } else {
            title = card.getRoot().getContext().getString(R.string.my_room);
        }
        card.tvMyRoomTitle.setText(title);
        card.tvMyRoomSubtitle.setText(card.getRoot().getContext().getString(R.string.my_room));
        card.btnMyRoomGo.setText(R.string.enter_room);
        card.getRoot().setOnClickListener(onOpenMyRoom);
        card.btnMyRoomGo.setOnClickListener(onOpenMyRoom);
    }

    @Nullable
    public static RoomDtos.RoomDto findOwned(
            @Nullable List<RoomDtos.RoomDto> rooms,
            @Nullable String myUserId
    ) {
        if (rooms == null || myUserId == null || myUserId.isEmpty()) return null;
        for (RoomDtos.RoomDto r : rooms) {
            if (r == null) continue;
            if (myUserId.equals(r.hostId)) return r;
            if (r.host != null && myUserId.equals(r.host.id)) return r;
        }
        return null;
    }
}
