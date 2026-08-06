package com.Dramizo.Series.presentation.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Home rooms: pageSize=10, append load-more with footer anim (Mikoo-style). */
public class HomeViewModel extends ViewModel {
    private static final int PAGE_SIZE = 10;

    private final AppContainer c;
    private final MutableLiveData<List<RoomDtos.RoomDto>> rooms =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<MiscDtos.BannerDto>> banners =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> offersCount = new MutableLiveData<>(0);
    private final MutableLiveData<List<MiscDtos.RankingEntryDto>> richRanking =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<MiscDtos.RankingEntryDto>> billionRanking =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<RoomDtos.RoomDto>> followingRooms =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<RoomDtos.RoomDto> createdRoom = new MutableLiveData<>();
    private boolean creating;
    private int roomsPage = 1;
    private boolean roomsHasMore = true;
    private boolean roomsLoadingMore;

    public HomeViewModel(AppContainer c) {
        this.c = c;
    }

    public LiveData<List<RoomDtos.RoomDto>> getRooms() {
        return rooms;
    }

    public LiveData<List<MiscDtos.BannerDto>> getBanners() {
        return banners;
    }

    public LiveData<Integer> getOffersCount() {
        return offersCount;
    }

    public LiveData<List<MiscDtos.RankingEntryDto>> getRichRanking() {
        return richRanking;
    }

    public LiveData<List<MiscDtos.RankingEntryDto>> getBillionRanking() {
        return billionRanking;
    }

    public LiveData<List<RoomDtos.RoomDto>> getFollowingRooms() {
        return followingRooms;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    public boolean hasMoreRooms() {
        return roomsHasMore;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<RoomDtos.RoomDto> getCreatedRoom() {
        return createdRoom;
    }

    public void consumeCreatedRoom() {
        createdRoom.setValue(null);
    }

    public void loadRooms() {
        loadRooms(false, true);
    }

    /**
     * Quiet poll: refresh Hot order from server (live viewers / gifts move rooms up)
     * while keeping pages beyond 1 in place.
     */
    public void refreshRoomsQuietly() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<RoomDtos.RoomDto>> r =
                    c.listRoomsUseCase.execute(1, PAGE_SIZE);
            if (!r.success || r.data == null || r.data.items == null) return;
            List<RoomDtos.RoomDto> pageItems = dedupeRooms(r.data.items);
            List<RoomDtos.RoomDto> cur = rooms.getValue();
            if (cur == null || cur.isEmpty()) {
                rooms.postValue(pageItems);
                roomsPage = 1;
                roomsHasMore = pageItems.size() >= PAGE_SIZE;
                return;
            }
            Map<String, RoomDtos.RoomDto> oldById = new HashMap<>();
            for (RoomDtos.RoomDto x : cur) {
                if (x != null && x.id != null) oldById.put(x.id, x);
            }
            HashSet<String> onHot = new HashSet<>();
            List<RoomDtos.RoomDto> next = new ArrayList<>(cur.size() + 4);
            boolean changed = false;
            // Page-1 slots follow server explore/heat order.
            for (int i = 0; i < pageItems.size(); i++) {
                RoomDtos.RoomDto neu = pageItems.get(i);
                if (neu == null || neu.id == null) continue;
                onHot.add(neu.id);
                RoomDtos.RoomDto old = oldById.get(neu.id);
                RoomDtos.RoomDto row = old != null ? copyRoomVolatile(old, neu) : neu;
                next.add(row);
                if (old == null
                        || (i < cur.size() && cur.get(i) != null && !neu.id.equals(cur.get(i).id))
                        || (old != null && (old.viewerCount != neu.viewerCount
                        || old.roomLevel != neu.roomLevel
                        || old.exploreRank != neu.exploreRank
                        || !eq(old.coverUrl, neu.coverUrl)
                        || !eq(old.title, neu.title)))) {
                    changed = true;
                }
            }
            // Append remaining loaded pages that are not on Hot page 1.
            for (RoomDtos.RoomDto old : cur) {
                if (old == null || old.id == null || onHot.contains(old.id)) continue;
                next.add(old);
            }
            if (changed || next.size() != cur.size()) {
                rooms.postValue(next);
            }
        });
    }

    /** Append next page (Mikoo load more). */
    public void loadMoreRooms() {
        if (roomsLoadingMore || !roomsHasMore) return;
        loadRooms(true, false);
    }

    private static boolean eq(String a, String b) {
        if (a == null) return b == null || b.isEmpty();
        if (b == null) return a.isEmpty();
        return a.equals(b);
    }

    /** Shallow copy with refreshed volatile fields — preserves list order for quiet poll. */
    private static RoomDtos.RoomDto copyRoomVolatile(RoomDtos.RoomDto old, RoomDtos.RoomDto neu) {
        RoomDtos.RoomDto c = new RoomDtos.RoomDto();
        c.id = old.id;
        c.title = neu.title;
        c.description = old.description;
        c.coverUrl = neu.coverUrl;
        c.backgroundUrl = old.backgroundUrl;
        c.roomCardUrl = neu.roomCardUrl;
        c.type = old.type;
        c.status = neu.status;
        c.hostId = old.hostId;
        c.cohostId = old.cohostId;
        // Identity must track server (personal vs agency), not stick to first poll.
        c.agencyId = neu.agencyId != null ? neu.agencyId : old.agencyId;
        c.roomKind = neu.roomKind != null ? neu.roomKind : old.roomKind;
        c.isSupport = neu.isSupport || "support".equalsIgnoreCase(
                neu.roomKind != null ? neu.roomKind : "");
        c.isPersistent = neu.isPersistent;
        c.activeHostId = neu.activeHostId != null ? neu.activeHostId : old.activeHostId;
        c.moderatorIds = old.moderatorIds;
        c.moderatorPermissions = old.moderatorPermissions;
        c.musicUrl = old.musicUrl;
        c.musicTitle = old.musicTitle;
        c.musicArtist = old.musicArtist;
        c.musicStatus = old.musicStatus;
        c.musicPositionMs = old.musicPositionMs;
        c.musicStartedAt = old.musicStartedAt;
        c.seatCount = old.seatCount;
        c.viewerCount = neu.viewerCount;
        c.zegoRoomId = old.zegoRoomId;
        c.hasPassword = neu.hasPassword;
        c.isPublic = neu.isPublic;
        c.seats = neu.seats != null ? neu.seats : old.seats;
        c.host = neu.host != null ? neu.host : old.host;
        c.challengeBadge = old.challengeBadge;
        c.viewerAvatars = neu.viewerAvatars != null ? neu.viewerAvatars : old.viewerAvatars;
        c.roomLevel = neu.roomLevel;
        // Keep live Hot rank from server (1/2/3 badges); fall back only if missing.
        c.exploreRank = neu.exploreRank > 0 ? neu.exploreRank : old.exploreRank;
        c.giftSoundsEnabled = old.giftSoundsEnabled;
        c.chatZoneEnabled = old.chatZoneEnabled;
        c.charmEnabled = old.charmEnabled;
        c.bannerEnabled = old.bannerEnabled;
        c.micInteractEnabled = old.micInteractEnabled;
        c.entryEffectsEnabled = old.entryEffectsEnabled;
        c.lowGiftEffectsEnabled = old.lowGiftEffectsEnabled;
        c.displayRoomId = old.displayRoomId;
        return c;
    }

    private List<RoomDtos.RoomDto> dedupeRooms(List<RoomDtos.RoomDto> src) {
        if (src == null || src.isEmpty()) return Collections.emptyList();
        LinkedHashMap<String, RoomDtos.RoomDto> map = new LinkedHashMap<>();
        for (RoomDtos.RoomDto room : src) {
            if (room == null || room.id == null) continue;
            map.putIfAbsent(room.id, room);
        }
        // Fail-safe pin: official CS rooms always first (server also orders this way).
        List<RoomDtos.RoomDto> support = new ArrayList<>();
        List<RoomDtos.RoomDto> rest = new ArrayList<>();
        for (RoomDtos.RoomDto room : map.values()) {
            if (com.Dramizo.Series.util.RoomUiHelper.isSupportRoom(room)) {
                support.add(room);
            } else {
                rest.add(room);
            }
        }
        List<RoomDtos.RoomDto> out = new ArrayList<>(support.size() + rest.size());
        out.addAll(support);
        out.addAll(rest);
        return out;
    }

    private void loadRooms(boolean quiet, boolean reset) {
        if (reset) {
            roomsPage = 1;
            roomsHasMore = true;
            roomsLoadingMore = false;
            loadingMore.postValue(false);
            if (!quiet) loading.postValue(true);
        } else {
            if (roomsLoadingMore || !roomsHasMore) return;
            roomsLoadingMore = true;
            loadingMore.postValue(true);
        }
        final int page = reset ? 1 : roomsPage + 1;
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<RoomDtos.RoomDto>> r =
                    c.listRoomsUseCase.execute(page, PAGE_SIZE);
            roomsLoadingMore = false;
            loadingMore.postValue(false);
            if (!quiet && reset) loading.postValue(false);
            if (r.success && r.data != null && r.data.items != null) {
                List<RoomDtos.RoomDto> pageItems = dedupeRooms(r.data.items);
                roomsHasMore = pageItems.size() >= PAGE_SIZE;
                roomsPage = page;
                if (reset) {
                    rooms.postValue(pageItems);
                } else {
                    List<RoomDtos.RoomDto> merged = new ArrayList<>();
                    List<RoomDtos.RoomDto> cur = rooms.getValue();
                    if (cur != null) merged.addAll(cur);
                    HashSet<String> seen = new HashSet<>();
                    for (RoomDtos.RoomDto x : merged) {
                        if (x != null && x.id != null) seen.add(x.id);
                    }
                    for (RoomDtos.RoomDto x : pageItems) {
                        if (x == null || x.id == null || seen.contains(x.id)) continue;
                        seen.add(x.id);
                        merged.add(x);
                    }
                    rooms.postValue(merged);
                }
            } else if (!quiet && reset) {
                if (!ApiCall.isRateLimited(r.error)) {
                    error.postValue(r.error != null ? r.error
                            : c.getAppContext().getString(R.string.load_rooms_failed));
                }
            } else if (!reset) {
                roomsHasMore = false;
            }
        });
    }

    public void loadBanners() {
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.BannerDto>> r = ApiCall.execute(c.getConfigApi().banners());
            if (r.success && r.data != null) banners.postValue(r.data);
        });
    }

    public void loadOffers() {
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.OfferDto>> r = ApiCall.execute(c.getConfigApi().offers());
            if (r.success && r.data != null) offersCount.postValue(r.data.size());
            else offersCount.postValue(0);
        });
    }

    public void loadHomeRankings() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> rich =
                    c.getRankingsUseCase.execute("weekly", "rich");
            if (rich.success && rich.data != null && rich.data.items != null) {
                richRanking.postValue(rich.data.items);
            }
            Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> gifts =
                    c.getRankingsUseCase.execute("weekly", "gifts");
            if (gifts.success && gifts.data != null && gifts.data.items != null) {
                billionRanking.postValue(gifts.data.items);
            }
        });
    }

    public void loadFollowingRooms() {
        loading.postValue(true);
        c.getIoExecutor().execute(() -> {
            String myId = c.getSessionManager().getUserId();
            java.util.Set<String> followingIds = new java.util.HashSet<>();
            if (myId != null && !myId.isEmpty()) {
                Result<MiscDtos.ListResult<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto>> following =
                        ApiCall.execute(c.getUserApi().following(myId, 1));
                if (following.success && following.data != null && following.data.items != null) {
                    for (com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user : following.data.items) {
                        if (user != null && user.id != null) followingIds.add(user.id);
                    }
                }
            }
            // Paginate like Hot so «أنا» scales (not only first 20).
            List<RoomDtos.RoomDto> matched = new ArrayList<>();
            int page = 1;
            while (page <= 10) {
                Result<MiscDtos.ListResult<RoomDtos.RoomDto>> roomResult =
                        c.listRoomsUseCase.execute(page, PAGE_SIZE);
                if (!roomResult.success || roomResult.data == null || roomResult.data.items == null) {
                    break;
                }
                List<RoomDtos.RoomDto> batch = dedupeRooms(roomResult.data.items);
                if (batch.isEmpty()) break;
                for (RoomDtos.RoomDto room : batch) {
                    if (room == null) continue;
                    if (!com.Dramizo.Series.util.RoomBrowseFilter.isBrowsablePersonal(room)) continue;
                    String hostId = room.hostId;
                    if (hostId == null && room.host != null) hostId = room.host.id;
                    if (hostId != null && followingIds.contains(hostId)) matched.add(room);
                }
                if (batch.size() < PAGE_SIZE) break;
                page++;
            }
            loading.postValue(false);
            followingRooms.postValue(matched);
        });
    }

    public void createRoom() {
        if (creating) return;
        creating = true;
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> r =
                    c.createRoomUseCase.execute(
                            c.getAppContext().getString(R.string.my_room), "voice", 20, null, true);
            creating = false;
            if (r.success && r.data != null && r.data.room != null) createdRoom.postValue(r.data.room);
            else error.postValue(r.error != null ? r.error
                    : c.getAppContext().getString(R.string.create_room_failed));
        });
    }
}
