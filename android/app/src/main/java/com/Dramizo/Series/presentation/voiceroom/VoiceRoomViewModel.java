package com.Dramizo.Series.presentation.voiceroom;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

import java.util.ArrayList;
import java.util.List;

public class VoiceRoomViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<RoomDtos.JoinRoomResult> session = new MutableLiveData<>();
    private final MutableLiveData<RoomDtos.RoomDto> room = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> handRaised = new MutableLiveData<>(false);
    private final MutableLiveData<List<RoomDtos.SeatRequestDto>> seatRequests =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<RoomDtos.SupportersResult> supporters = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();

    public VoiceRoomViewModel(AppContainer c) { this.c = c; }

    public LiveData<RoomDtos.JoinRoomResult> getSession() { return session; }
    public LiveData<RoomDtos.RoomDto> getRoom() { return room; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getHandRaised() { return handRaised; }
    public LiveData<List<RoomDtos.SeatRequestDto>> getSeatRequests() { return seatRequests; }
    public LiveData<RoomDtos.SupportersResult> getSupporters() { return supporters; }
    public LiveData<String> getInfo() { return info; }

    public void join(String roomId) {
        join(roomId, null);
    }

    public void join(String roomId, String password) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> r = c.joinRoomUseCase.execute(roomId, password);
            if (r.success) {
                session.postValue(r.data);
                if (r.data != null && r.data.room != null) room.postValue(r.data.room);
            } else error.postValue(r.error);
        });
    }

    public void refresh(String roomId) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomUseCase.execute(roomId);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void loadSupporters(String roomId) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.SupportersResult> r = c.getRoomRepository().supporters(roomId);
            if (r.success) supporters.postValue(r.data);
        });
    }

    public void reportUser(String roomId, String userId, String reason, String description) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().reportUser(
                    roomId, userId, reason, description);
            if (r.success) info.postValue("تم إرسال البلاغ إلى الإدارة");
            else error.postValue(r.error != null ? r.error : "تعذر إرسال البلاغ");
        });
    }

    public void updateMusic(
            String roomId, String action, String url, String title, String artist, Long positionMs) {
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r = c.getRoomRepository().updateMusic(
                    roomId, action, url, title, artist, positionMs);
            // Do not full-refresh here — realtime room:music + optimistic UI avoid play/pause freeze.
            if (!r.success) error.postValue(r.error != null ? r.error : "تعذر تحديث الموسيقى");
        });
    }

    public void requestSeat(String roomId, int seatIndex) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.raiseHandUseCase.execute(roomId, true, seatIndex);
            if (r.success) {
                handRaised.postValue(true);
                info.postValue("تم إرسال طلب الانضمام للمايك — انتظر موافقة المضيف");
            } else error.postValue(r.error != null ? r.error : "تعذر إرسال الطلب");
        });
    }

    public void cancelSeatRequest(String roomId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.raiseHandUseCase.execute(roomId, false, null);
            if (r.success) {
                handRaised.postValue(false);
                info.postValue("تم إلغاء طلب المايك");
            } else error.postValue(r.error);
        });
    }

    public void loadSeatRequests(String roomId) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.SeatRequestsResult> r = c.getRoomRepository().seatRequests(roomId);
            if (r.success && r.data != null && r.data.items != null) {
                seatRequests.postValue(r.data.items);
            } else if (r.success) {
                seatRequests.postValue(new ArrayList<>());
            }
        });
    }

    public void approveSeat(String roomId, String userId, Integer seatIndex) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().approveSeat(roomId, userId, seatIndex);
            if (r.success) {
                room.postValue(r.data);
                loadSeatRequests(roomId);
                info.postValue("تمت الموافقة على الطلب");
            } else error.postValue(r.error);
        });
    }

    public void rejectSeat(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().rejectSeat(roomId, userId);
            if (r.success) {
                loadSeatRequests(roomId);
                info.postValue("تم رفض الطلب");
            } else error.postValue(r.error);
        });
    }

    public void inviteSeat(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().inviteSeat(roomId, userId, null);
            if (r.success) info.postValue("تم إرسال دعوة المايك");
            else error.postValue(r.error != null ? r.error : "تعذر إرسال الدعوة");
        });
    }

    public void inviteTaskGuest(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r =
                    c.getRoomRepository().inviteTaskGuest(roomId, userId);
            if (r.success) {
                Object msg = r.data != null ? r.data.get("message") : null;
                info.postValue(msg != null ? String.valueOf(msg) : "تم إرسال دعوة المهمة (40 ماسة)");
            } else {
                error.postValue(r.error != null ? r.error : "تعذر إرسال دعوة المهمة");
            }
        });
    }

    public void respondSeatInvite(String roomId, boolean accept) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().respondSeatInvite(roomId, accept);
            if (r.success && accept) room.postValue(r.data);
            else if (!r.success) error.postValue(r.error);
        });
    }

    public void setGiftSounds(String roomId, boolean enabled) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().setGiftSounds(roomId, enabled);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void setDisplaySettings(String roomId, java.util.Map<String, Boolean> patch) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().setDisplaySettings(roomId, patch);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void lockRoom(String roomId, boolean locked) {
        lockRoom(roomId, locked, null);
    }

    public void lockRoom(String roomId, boolean locked, String password) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().lock(roomId, locked, password);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void setMic(String roomId, boolean muted) {
        c.getIoExecutor().execute(() -> c.getRoomRepository().setMic(roomId, muted));
    }

    public void setMic(String roomId, boolean muted, String targetUserId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().setMic(roomId, muted, targetUserId);
            if (r.success) {
                info.postValue(muted ? "تم كتم المايك" : "تم فك كتم المايك");
                refresh(roomId);
            } else {
                error.postValue(r.error != null ? r.error : "تعذر تغيير حالة المايك");
            }
        });
    }

    public void takeSeat(String roomId, int seatIndex) {
        c.getIoExecutor().execute(() -> {
            try {
                Result<RoomDtos.RoomDto> r = c.getRoomRepository().takeSeat(roomId, seatIndex);
                if (r.success) room.postValue(r.data);
                else error.postValue(r.error != null ? r.error : "تعذر الصعود على المايك");
            } catch (Exception e) {
                error.postValue(e.getMessage() != null ? e.getMessage() : "تعذر الصعود على المايك");
            }
        });
    }

    public void leaveSeat(String roomId) {
        c.getIoExecutor().execute(() -> {
            try {
                Result<RoomDtos.RoomDto> r = c.getRoomRepository().leaveSeat(roomId);
                if (r.success) room.postValue(r.data);
                else error.postValue(r.error);
            } catch (Exception e) {
                error.postValue(e.getMessage() != null ? e.getMessage() : "تعذر مغادرة المقعد");
            }
        });
    }

    public void lockSeat(String roomId, int seatIndex, boolean locked) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().lockSeat(roomId, seatIndex, locked);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void resizeSeats(String roomId, int seatCount) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().resizeSeats(roomId, seatCount);
            if (r.success) room.postValue(r.data);
            else error.postValue(r.error);
        });
    }

    public void leaveRoom(String roomId) {
        c.getIoExecutor().execute(() -> c.getRoomRepository().leave(roomId));
    }

    public void closeRoom(String roomId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().close(roomId);
            if (!r.success) error.postValue(r.error != null ? r.error : "تعذر إيقاف البث");
        });
    }

    public void kickUser(String roomId, String userId, String reason) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().kick(roomId, userId, reason);
            if (r.success) info.postValue("تم إخراج المستخدم من الغرفة");
            else error.postValue(r.error != null ? r.error : "تعذر الإخراج");
        });
    }

    public void banUser(String roomId, String userId, String reason, Integer durationMinutes) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().ban(roomId, userId, reason, durationMinutes);
            if (r.success) info.postValue("تم تطبيق الطرد المؤقت");
            else error.postValue(r.error != null ? r.error : "تعذر الحظر");
        });
    }

    public void unbanUser(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().unban(roomId, userId);
            if (r.success) info.postValue("تم رفع الحظر");
            else error.postValue(r.error != null ? r.error : "تعذر رفع الحظر");
        });
    }

    public void addModerator(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().addModerator(roomId, userId);
            if (r.success) {
                info.postValue("تم إضافة مشرف");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }

    public void removeModerator(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().removeModerator(roomId, userId);
            if (r.success) {
                info.postValue("تم إزالة المشرف");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }

    public void updateModeratorPermissions(
            String roomId, String userId, boolean music, boolean frames, boolean games,
            boolean mute, boolean kick, boolean ban, boolean seats, boolean invite,
            boolean manageRoom) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().updateModeratorPermissions(
                    roomId, userId, music, frames, games, mute, kick, ban,
                    seats, invite, manageRoom);
            if (r.success) {
                info.postValue("تم تحديث صلاحيات المشرف");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }

    public void setCohost(String roomId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().setCohost(roomId, userId);
            if (r.success) {
                info.postValue("تم تعيين مساعد المضيف");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }

    public void updateCover(String roomId, String coverUrl) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().update(roomId, null, coverUrl);
            if (r.success) {
                room.postValue(r.data);
                info.postValue("تم تحديث صورة الروم");
            } else error.postValue(r.error);
        });
    }

    public void updateTitle(String roomId, String title) {
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomRepository().update(roomId, title, null);
            if (r.success) {
                room.postValue(r.data);
                info.postValue("تم تحديث اسم الغرفة");
            } else error.postValue(r.error);
        });
    }

    public void setBackground(String roomId, String backgroundUrl) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().setBackground(roomId, backgroundUrl);
            if (r.success) {
                info.postValue("تم تغيير خلفية الغرفة");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }

    public void setRoomFrame(String roomId, String roomCardUrl) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getRoomRepository().setFrame(roomId, roomCardUrl);
            if (r.success) {
                info.postValue("تم تغيير إطار الغرفة");
                refresh(roomId);
            } else error.postValue(r.error);
        });
    }
}
