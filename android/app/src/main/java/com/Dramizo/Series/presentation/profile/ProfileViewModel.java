package com.Dramizo.Series.presentation.profile;
import androidx.lifecycle.LiveData; import androidx.lifecycle.MutableLiveData; import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.AuthDtos; import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer; import com.Dramizo.Series.domain.model.Result;
public class ProfileViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<AuthDtos.UserDto> user = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>();
    public ProfileViewModel(AppContainer c) { this.c = c; }
    public LiveData<AuthDtos.UserDto> getUser() { return user; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaved() { return saved; }
    public void loadMe() {
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> r = c.getProfileUseCase.me();
            if (r.success) user.postValue(r.data); else error.postValue(r.error);
        });
    }
    public void loadUser(String id) {
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> r = c.getProfileUseCase.byId(id);
            if (r.success) user.postValue(r.data); else error.postValue(r.error);
        });
    }
    public void update(String displayName, String bio, String avatar, String cover, String gender) {
        update(displayName, bio, avatar, cover, gender, null, null);
    }
    public void update(String displayName, String bio, String avatar, String cover, String gender,
                       String birthday, String country) {
        updateRequest(new MiscDtos.UpdateProfileRequest(displayName, bio, avatar, cover, gender, birthday, country));
    }
    public void updateRequest(MiscDtos.UpdateProfileRequest req) {
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> r = c.updateProfileUseCase.execute(req);
            if (r.success) { user.postValue(r.data); saved.postValue(true); }
            else error.postValue(r.error);
        });
    }
}
