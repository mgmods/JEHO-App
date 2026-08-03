package com.Dramizo.Series.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;

/**
 * Opens the persistent <b>agency</b> voice room (separate from personal My Room).
 * Uses {@code POST /agencies/:id/room/open} so the lobby title stays the agency name.
 */
public final class AgencyRoomLauncher {
    private AgencyRoomLauncher() {}

    public static void open(
            @NonNull Activity activity,
            @Nullable String agencyId,
            @Nullable String agencyName
    ) {
        if (agencyId == null || agencyId.trim().isEmpty()) {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        final String id = agencyId.trim();
        final String title = agencyName != null && !agencyName.trim().isEmpty()
                ? agencyName.trim()
                : "وكالة";

        AppContainer c = ContainerProvider.from(activity);
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> opened =
                    c.getAgencyRepository().openRoom(id, title, null);
            activity.runOnUiThread(() -> {
                if (!opened.success || opened.data == null || opened.data.room == null
                        || opened.data.room.id == null) {
                    Toast.makeText(activity,
                            opened.error != null ? opened.error
                                    : activity.getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(activity, VoiceRoomActivity.class);
                i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, opened.data.room.id);
                i.putExtra(VoiceRoomActivity.EXTRA_IS_HOST, true);
                activity.startActivity(i);
            });
        });
    }
}
