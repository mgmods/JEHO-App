package com.Dramizo.Series.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Starts HTTP room join as soon as the user taps a room — before VoiceRoomActivity
 * inflates its huge layout. Activity adopts the result when observers are ready.
 */
public final class RoomJoinPrefetch {
    public interface Callback {
        void onResult(@Nullable RoomDtos.JoinRoomResult session, @Nullable String error);
    }

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String targetRoomId;
    @Nullable private static RoomDtos.JoinRoomResult result;
    @Nullable private static String error;
    private static boolean inFlight;
    private static final List<Callback> waiters = new CopyOnWriteArrayList<>();

    private RoomJoinPrefetch() {}

    /** Fire-and-forget join on IO — safe from home list / search / launchers. */
    public static void begin(@Nullable Context context, @Nullable String roomId,
                             @Nullable String password) {
        if (context == null || roomId == null || roomId.isEmpty()) return;
        try {
            if (ActiveRoomSession.get().canResumeUi(roomId)) return;
        } catch (Exception ignored) {
        }
        final String rid = roomId;
        final String pass = password;
        synchronized (LOCK) {
            // Same room already joining / ready — don't restart.
            if (rid.equals(targetRoomId) && (inFlight || result != null)) return;
            targetRoomId = rid;
            result = null;
            error = null;
            inFlight = true;
            waiters.clear();
        }
        AppContainer c = ContainerProvider.from(context.getApplicationContext());
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> r;
            try {
                r = c.joinRoomUseCase.execute(rid, pass);
            } catch (Exception e) {
                r = Result.err(e.getMessage() != null ? e.getMessage() : "join failed");
            }
            List<Callback> toNotify = new ArrayList<>();
            synchronized (LOCK) {
                if (!rid.equals(targetRoomId)) return;
                inFlight = false;
                if (r != null && r.success && r.data != null) {
                    result = r.data;
                    error = null;
                } else {
                    result = null;
                    error = r != null ? r.error : "join failed";
                }
                toNotify.addAll(waiters);
                waiters.clear();
            }
            final RoomDtos.JoinRoomResult sessionOut = result;
            final String errOut = error;
            for (Callback cb : toNotify) {
                MAIN.post(() -> cb.onResult(sessionOut, errOut));
            }
        });
    }

    /** Consume completed prefetch for this room (null if still running or different room). */
    @Nullable
    public static RoomDtos.JoinRoomResult takeReady(@Nullable String roomId) {
        if (roomId == null) return null;
        synchronized (LOCK) {
            if (!roomId.equals(targetRoomId) || result == null) return null;
            RoomDtos.JoinRoomResult out = result;
            result = null;
            error = null;
            targetRoomId = null;
            inFlight = false;
            waiters.clear();
            return out;
        }
    }

    public static boolean isInFlight(@Nullable String roomId) {
        if (roomId == null) return false;
        synchronized (LOCK) {
            return roomId.equals(targetRoomId) && inFlight;
        }
    }

    /**
     * Deliver result when ready (prefetch finish or already cached).
     * If none in flight for roomId, callback runs with (null, null) so caller can join.
     */
    public static void await(@Nullable String roomId, @Nullable Callback callback) {
        if (callback == null) return;
        if (roomId == null || roomId.isEmpty()) {
            MAIN.post(() -> callback.onResult(null, null));
            return;
        }
        synchronized (LOCK) {
            if (!roomId.equals(targetRoomId)) {
                MAIN.post(() -> callback.onResult(null, null));
                return;
            }
            if (result != null || error != null) {
                final RoomDtos.JoinRoomResult s = result;
                final String e = error;
                result = null;
                error = null;
                targetRoomId = null;
                inFlight = false;
                MAIN.post(() -> callback.onResult(s, e));
                return;
            }
            if (inFlight) {
                waiters.add(callback);
                return;
            }
        }
        MAIN.post(() -> callback.onResult(null, null));
    }

    public static void clear(@Nullable String roomId) {
        synchronized (LOCK) {
            if (roomId == null || roomId.equals(targetRoomId)) {
                targetRoomId = null;
                result = null;
                error = null;
                inFlight = false;
                waiters.clear();
            }
        }
    }
}
