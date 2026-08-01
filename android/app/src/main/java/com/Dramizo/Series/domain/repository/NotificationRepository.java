package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;

public interface NotificationRepository {
    Result<MiscDtos.ListResult<MiscDtos.NotificationDto>> list(int page);
    Result<Object> markRead(String id);
    Result<Object> registerDevice(String token, String platform);
    Result<Object> unregisterDevice();
}
