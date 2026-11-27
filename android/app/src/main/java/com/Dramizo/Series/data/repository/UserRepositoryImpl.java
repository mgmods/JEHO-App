package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.UserDao;
import com.Dramizo.Series.data.local.entity.UserEntity;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.remote.api.UserApi;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.UserRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepositoryImpl implements UserRepository {
    private final UserApi api;
    private final UserDao userDao;
    private final ExecutorService io;
    private final SessionManager sessionManager;
    private final EncryptedFeatureCache featureCache;
    private final ConcurrentHashMap<String, Long> profileRefreshAt = new ConcurrentHashMap<>();
    private final java.util.Set<String> profileRefreshes = ConcurrentHashMap.newKeySet();

    public UserRepositoryImpl(UserApi api, UserDao userDao, ExecutorService io, SessionManager sessionManager) {
        this(api, userDao, io, sessionManager, null);
    }

    public UserRepositoryImpl(UserApi api, UserDao userDao, ExecutorService io,
                              SessionManager sessionManager, EncryptedFeatureCache featureCache) {
        this.api = api; this.userDao = userDao; this.io = io;
        this.sessionManager = sessionManager; this.featureCache = featureCache;
    }

    private void persistFullProfile(String userId, AuthDtos.UserDto user) {
        if (featureCache == null || userId == null || userId.isEmpty() || user == null) return;
        featureCache.putJson(EncryptedFeatureCache.UID_APP, "user_profile_" + userId, user);
    }

    private void refreshProfileInBackground(String userId) {
        if (featureCache == null || userId == null || userId.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = profileRefreshAt.get(userId);
        if (last != null && now - last < 30_000L) return;
        if (!profileRefreshes.add(userId)) return;
        profileRefreshAt.put(userId, now);
        try {
            io.execute(() -> {
                try {
                    Result<AuthDtos.UserDto> fresh = ApiCall.execute(api.getUser(userId));
                    if (fresh.success && fresh.data != null) {
                        persistFullProfile(userId, fresh.data);
                        if (sessionManager != null && userId.equals(sessionManager.getUserId())) cache(fresh.data);
                        else {
                            UserEntity entity = new UserEntity();
                            entity.id = fresh.data.id; entity.username = fresh.data.username;
                            entity.displayName = fresh.data.displayName; entity.avatarUrl = fresh.data.avatarUrl;
                            entity.bio = fresh.data.bio; entity.level = fresh.data.level;
                            entity.vipLevel = fresh.data.vipLevel; entity.followersCount = fresh.data.followersCount;
                            entity.followingCount = fresh.data.followingCount; entity.cachedAt = System.currentTimeMillis();
                            userDao.upsert(entity);
                        }
                    }
                } finally { profileRefreshes.remove(userId); }
            });
        } catch (RuntimeException rejected) { profileRefreshes.remove(userId); }
    }

    private void cache(AuthDtos.UserDto u) {
        if (u == null) return;
        if (sessionManager != null) sessionManager.updateCachedUser(u);
        persistFullProfile(u.id, u);
        UserEntity e = new UserEntity();
        e.id = u.id; e.username = u.username; e.displayName = u.displayName;
        e.avatarUrl = u.avatarUrl; e.bio = u.bio; e.level = u.level;
        e.vipLevel = u.vipLevel; e.followersCount = u.followersCount;
        e.followingCount = u.followingCount; e.cachedAt = System.currentTimeMillis();
        io.execute(() -> userDao.upsert(e));
    }

    @Override public Result<AuthDtos.UserDto> getMe() {
        Result<AuthDtos.UserDto> r = ApiCall.execute(api.me());
        if (r.success) cache(r.data);
        return r;
    }
    @Override public Result<AuthDtos.UserDto> getUser(String id) {
        if (featureCache != null && id != null && !id.trim().isEmpty()) {
            AuthDtos.UserDto local = featureCache.getJson(EncryptedFeatureCache.UID_APP,
                    "user_profile_" + id, AuthDtos.UserDto.class, 0L);
            if (local != null) {
                refreshProfileInBackground(id);
                return Result.ok(local);
            }
        }
        Result<AuthDtos.UserDto> r = ApiCall.execute(api.getUser(id));
        if (r.success && r.data != null) persistFullProfile(id, r.data);
        // Never overwrite MY session cache with another user's profile (kills own frame).
        if (r.success && r.data != null && sessionManager != null
                && id != null && id.equals(sessionManager.getUserId())) {
            cache(r.data);
        } else if (r.success && r.data != null) {
            UserEntity e = new UserEntity();
            e.id = r.data.id; e.username = r.data.username; e.displayName = r.data.displayName;
            e.avatarUrl = r.data.avatarUrl; e.bio = r.data.bio; e.level = r.data.level;
            e.vipLevel = r.data.vipLevel; e.followersCount = r.data.followersCount;
            e.followingCount = r.data.followingCount; e.cachedAt = System.currentTimeMillis();
            io.execute(() -> userDao.upsert(e));
        }
        return r;
    }
    @Override public Result<AuthDtos.UserDto> updateProfile(MiscDtos.UpdateProfileRequest request) {
        Result<AuthDtos.UserDto> r = ApiCall.execute(api.updateProfile(request));
        if (r.success) cache(r.data);
        return r;
    }
    @Override public Result<Object> follow(String id) { return ApiCall.execute(api.follow(id)); }
    @Override public Result<Object> unfollow(String id) { return ApiCall.execute(api.unfollow(id)); }
    @Override public Result<MiscDtos.ListResult<AuthDtos.UserDto>> search(String q, int page) {
        return ApiCall.execute(api.search(q, page));
    }
    @Override public Result<MiscDtos.ListResult<AuthDtos.UserDto>> blocked() {
        return ApiCall.execute(api.blocked());
    }
    @Override public Result<Object> unblock(String id) {
        return ApiCall.execute(api.unblock(id));
    }
}
