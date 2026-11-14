package com.Dramizo.Series.presentation.ranking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import java.util.Collections;
import java.util.List;

public class RankingViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<MiscDtos.RankingEntryDto>> entries =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public RankingViewModel(AppContainer c) { this.c = c; }
    public LiveData<List<MiscDtos.RankingEntryDto>> getEntries() { return entries; }
    public LiveData<String> getError() { return error; }

    public void load(String period) {
        load(period, "rich");
    }

    public void load(String period, String category) {
        final String p = period != null ? period : "daily";
        final String cat = category != null && !category.isEmpty() ? category : "rich";
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> r =
                    c.getRankingsUseCase.execute(p, cat);
            if (r.success && r.data != null && r.data.items != null) {
                entries.postValue(r.data.items);
            } else {
                error.postValue(r.error != null ? r.error : "تعذر تحميل الترتيب");
            }
        });
    }
}
