package com.Dramizo.Series.presentation.voiceroom;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;

/**
 * Checks that a room is live before opening {@link VoiceRoomActivity}.
 * Shows a broadcast-style loading animation; never lands on an empty closed room screen.
 */
public class RoomJoinGateActivity extends AppCompatActivity {
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_PASSWORD = "password";

    public static Intent intent(Context context, String roomId) {
        Intent i = new Intent(context, RoomJoinGateActivity.class);
        i.putExtra(EXTRA_ROOM_ID, roomId);
        return i;
    }

    public static void open(Context context, String roomId) {
        if (context == null || roomId == null || roomId.trim().isEmpty()) return;
        Intent i = intent(context, roomId.trim());
        if (!(context instanceof android.app.Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_join_gate);
        String roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        String password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (roomId == null || roomId.trim().isEmpty()) {
            finishQuiet(getString(R.string.room_not_live));
            return;
        }
        final String id = roomId.trim();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomUseCase.execute(id);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r.success && r.data != null) {
                    if (isClosed(r.data)) {
                        finishQuiet(getString(R.string.room_not_live));
                        return;
                    }
                    openLiveRoom(id, password);
                    return;
                }
                String err = r.error != null ? r.error.toLowerCase(java.util.Locale.US) : "";
                // Private/password rooms may forbid GET until join — still try enter.
                if (err.contains("join this room") || err.contains("forbidden")
                        || err.contains("password") || err.contains("كلمة")) {
                    openLiveRoom(id, password);
                    return;
                }
                if (err.contains("closed") || err.contains("not found")
                        || err.contains("مغلقة") || err.contains("agency")
                        || err.contains("suspend") || err.contains("unavailable")
                        || err.contains("غير متوفر") || err.contains("غير متاح")
                        || err.contains("does not exist") || err.contains("no longer")) {
                    finishQuiet(getString(R.string.room_not_live));
                    return;
                }
                // Unknown GET failure: probe join — closed rooms fail fast.
                probeJoin(c, id, password);
            });
        });
    }

    private void probeJoin(AppContainer c, String id, String password) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> join = c.joinRoomUseCase.execute(id, password);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (join.success && join.data != null) {
                    openLiveRoom(id, password);
                    return;
                }
                finishQuiet(getString(R.string.room_not_live));
            });
        });
    }

    private static boolean isClosed(RoomDtos.RoomDto room) {
        if (room == null || room.status == null) return false;
        return "closed".equalsIgnoreCase(room.status.trim());
    }

    private void openLiveRoom(String roomId, String password) {
        Intent i = new Intent(this, VoiceRoomActivity.class);
        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
        i.putExtra(VoiceRoomActivity.EXTRA_IS_HOST, false);
        if (password != null && !password.isEmpty()) {
            i.putExtra(VoiceRoomActivity.EXTRA_PASSWORD, password);
        }
        startActivity(i);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void finishQuiet(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
