package com.Dramizo.Series.presentation.notifications;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<MiscDtos.NotificationDto>> items = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public NotificationsViewModel(AppContainer c) { this.c = c; }

    public LiveData<List<MiscDtos.NotificationDto>> getItems() { return items; }
    public LiveData<String> getError() { return error; }

    public void load() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<MiscDtos.NotificationDto>> r = c.getNotificationsUseCase.execute(1);
            if (r.success && r.data != null && r.data.items != null) items.postValue(r.data.items);
            else error.postValue(r.error != null ? r.error : "تعذر تحميل الإشعارات");
        });
    }

    public void markRead(String id) {
        if (id == null) return;
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getNotificationRepository().markRead(id);
            if (!r.success) return;
            List<MiscDtos.NotificationDto> current = items.getValue();
            if (current == null) return;
            List<MiscDtos.NotificationDto> next = new ArrayList<>(current);
            for (MiscDtos.NotificationDto n : next) {
                if (id.equals(n.id)) {
                    n.isRead = true;
                    break;
                }
            }
            items.postValue(next);
        });
    }
}
