package com.Dramizo.Series.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;

/**
 * One-tap "My Room": enter/create a <b>personal</b> STANDARD room using
 * profile name + avatar. Agency live is a separate flow ({@link AgencyRoomLauncher}).
 */
public final class MyRoomLauncher {
    private MyRoomLauncher() {}

    public static void open(@NonNull Activity activity) {
        AppContainer c = ContainerProvider.from(activity);
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        String myId = c.getSessionManager().getUserId();
        if (myId == null || myId.isEmpty()) {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        String display = displayName(me);
        String avatar = me != null ? me.avatarUrl : null;
        if (display == null || display.trim().isEmpty()) {
            Toast.makeText(activity, "أكمل اسمك في الملف الشخصي أولاً", Toast.LENGTH_LONG).show();
            return;
        }
        if (avatar == null || avatar.trim().isEmpty()) {
            Toast.makeText(activity, "أضف صورة شخصية أولاً", Toast.LENGTH_LONG).show();
            return;
        }

        c.getIoExecutor().execute(() -> {
            // Prefer existing PERSONAL owned room — never open agency room from "My room".
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.ListResult<RoomDtos.RoomDto>> listed =
                    ApiCall.execute(c.getRoomApi().list(1));
            if (listed.success && listed.data != null && listed.data.items != null) {
                RoomDtos.RoomDto owned = MyRoomCardBinder.findOwnedPersonal(listed.data.items, myId);
                if (owned != null && owned.id != null && !owned.id.isEmpty()) {
                    activity.runOnUiThread(() -> enter(activity, owned.id));
                    return;
                }
            }

            RoomDtos.CreateRoomRequest req =
                    RoomDtos.CreateRoomRequest.personalRoom(display.trim(), avatar);
            Result<RoomDtos.JoinRoomResult> created = ApiCall.execute(c.getRoomApi().create(req));
            activity.runOnUiThread(() -> {
                if (!created.success || created.data == null || created.data.room == null
                        || created.data.room.id == null) {
                    Toast.makeText(activity,
                            created.error != null ? created.error : activity.getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                enter(activity, created.data.room.id);
            });
        });
    }

    private static void enter(@NonNull Activity activity, @NonNull String roomId) {
        Intent i = new Intent(activity, VoiceRoomActivity.class);
        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
        i.putExtra(VoiceRoomActivity.EXTRA_IS_HOST, true);
        activity.startActivity(i);
    }

    @Nullable
    private static String displayName(@Nullable AuthDtos.UserDto me) {
        if (me == null) return null;
        if (me.displayName != null && !me.displayName.trim().isEmpty()) return me.displayName;
        if (me.username != null && !me.username.trim().isEmpty()) return me.username;
        return null;
    }
}
