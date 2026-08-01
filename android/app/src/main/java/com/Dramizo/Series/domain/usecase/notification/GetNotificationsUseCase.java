package com.Dramizo.Series.domain.usecase.notification;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.NotificationRepository;
public class GetNotificationsUseCase {
    private final NotificationRepository repo;
    public GetNotificationsUseCase(NotificationRepository repo) { this.repo = repo; }
    public Result<MiscDtos.ListResult<MiscDtos.NotificationDto>> execute(int page) { return repo.list(page); }
}
