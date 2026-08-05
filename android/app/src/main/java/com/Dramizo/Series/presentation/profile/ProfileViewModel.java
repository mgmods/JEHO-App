package com.Dramizo.Series.presentation.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

public class ProfileViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<AuthDtos.UserDto> user = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>();
    private long lastLoadMeAtMs = 0L;
    private static final long MIN_RELOAD_MS = 20_000L;

    public ProfileViewModel(AppContainer c) { this.c = c; }
    public LiveData<AuthDtos.UserDto> getUser() { return user; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaved() { return saved; }

    public void loadMe() {
        loadMe(false);
    }

    /** @param force true after edits / pull when fresh data is required */
    public void loadMe(boolean force) {
        // Paint cached profile only once so tab re-entry does not rebuild the whole header.
        AuthDtos.UserDto cached = c.getSessionManager().getUser();
        if (user.getValue() == null && cached != null) {
            user.setValue(cached);
        }
        long now = System.currentTimeMillis();
        if (!force && user.getValue() != null && now - lastLoadMeAtMs < MIN_RELOAD_MS) {
            return;
        }
        lastLoadMeAtMs = now;
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> r = c.getProfileUseCase.me();
            if (r.success && r.data != null) {
                c.getSessionManager().updateCachedUser(r.data);
                if (!sameProfileSnapshot(user.getValue(), r.data)) {
                    user.postValue(r.data);
                }
            } else if (cached == null && user.getValue() == null) {
                error.postValue(r.error);
            }
        });
    }

    private static boolean sameProfileSnapshot(AuthDtos.UserDto a, AuthDtos.UserDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (!eq(a.id, b.id)) return false;
        if (!eq(a.displayName, b.displayName)) return false;
        if (!eq(a.avatarUrl, b.avatarUrl)) return false;
        if (!eq(a.vipBadgeUrl, b.vipBadgeUrl)) return false;
        if (!eq(a.hostBadgeUrl, b.hostBadgeUrl)) return false;
        if (!eq(a.staffRole, b.staffRole)) return false;
        if (a.isAdmin != b.isAdmin) return false;
        if (a.level != b.level) return false;
        if (a.vipLevel != b.vipLevel) return false;
        if (a.friendsCount != b.friendsCount) return false;
        if (a.followingCount != b.followingCount) return false;
        if (a.followersCount != b.followersCount) return false;
        if (a.wealthLevel != b.wealthLevel) return false;
        if (a.popularityLevel != b.popularityLevel) return false;
        if (a.wealthScore != b.wealthScore) return false;
        if (a.charmScore != b.charmScore) return false;
        if (a.popularityScore != b.popularityScore) return false;
        if (!eq(a.birthday, b.birthday)) return false;
        if (!eq(a.country, b.country)) return false;
        return eq(a.levelBadgeUrl, b.levelBadgeUrl);
    }

    private static boolean eq(String x, String y) {
        if (x == null) return y == null || y.isEmpty();
        if (y == null) return x.isEmpty();
        return x.equals(y);
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
            if (r.success) {
                lastLoadMeAtMs = 0L;
                user.postValue(r.data);
                saved.postValue(true);
            } else {
                error.postValue(r.error);
            }
        });
    }
}
