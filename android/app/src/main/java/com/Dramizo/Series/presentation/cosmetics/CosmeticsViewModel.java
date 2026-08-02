package com.Dramizo.Series.presentation.cosmetics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class CosmeticsViewModel extends ViewModel {
    public static final String EXTRA_TYPE = "cosmetic_type";

    public static final class CatalogPage {
        public final String type;
        public final List<CosmeticDtos.CosmeticDto> items;

        public CatalogPage(String type, List<CosmeticDtos.CosmeticDto> items) {
            this.type = type;
            this.items = items != null ? items : Collections.emptyList();
        }
    }

    private final AppContainer container;
    private final MutableLiveData<CatalogPage> catalogPage =
            new MutableLiveData<>(new CatalogPage(null, Collections.emptyList()));
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final Set<String> ownedIds = new HashSet<>();
    private final Set<String> equippedIds = new HashSet<>();
    private final AtomicInteger loadSeq = new AtomicInteger();
    private volatile String filterType;
    private volatile boolean bagMode;
    private volatile long inventoryLoadedAtMs;
    private static final long INVENTORY_CACHE_MS = 8_000L;

    public CosmeticsViewModel(AppContainer container) {
        this.container = container;
    }

    public LiveData<CatalogPage> getCatalogPage() { return catalogPage; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<String> getError() { return error; }

    public String getFilterType() { return filterType; }

    public void setFilterType(String type) { this.filterType = type; }

    public boolean isBagMode() { return bagMode; }

    public void setBagMode(boolean bagMode) { this.bagMode = bagMode; }

    public boolean isOwned(String cosmeticId) {
        synchronized (ownedIds) {
            return ownedIds.contains(cosmeticId);
        }
    }

    public boolean isEquipped(String cosmeticId) {
        synchronized (equippedIds) {
            return equippedIds.contains(cosmeticId);
        }
    }

    public void load() {
        loadForType(filterType);
    }

    public void loadForType(String type) {
        final String loadType = type;
        final int seq = loadSeq.incrementAndGet();
        filterType = loadType;
        container.getIoExecutor().execute(() -> {
            boolean needInventory;
            synchronized (ownedIds) {
                needInventory = System.currentTimeMillis() - inventoryLoadedAtMs > INVENTORY_CACHE_MS
                        || ownedIds.isEmpty();
            }
            if (needInventory) {
                Result<List<CosmeticDtos.UserCosmeticDto>> inv =
                        container.getCosmeticsRepository().inventory();
                if (inv.success && inv.data != null) {
                    synchronized (ownedIds) {
                        ownedIds.clear();
                        equippedIds.clear();
                        for (CosmeticDtos.UserCosmeticDto row : inv.data) {
                            if (row.cosmeticId != null) ownedIds.add(row.cosmeticId);
                            if (row.equipped && row.cosmeticId != null) equippedIds.add(row.cosmeticId);
                        }
                    }
                    inventoryLoadedAtMs = System.currentTimeMillis();
                }
            }
            Result<List<CosmeticDtos.CosmeticDto>> cat =
                    container.getCosmeticsRepository().catalog(loadType);
            // Accept only the latest in-flight request.
            if (seq != loadSeq.get()) return;
            if (cat.success && cat.data != null) {
                List<CosmeticDtos.CosmeticDto> items = new ArrayList<>(cat.data);
                if (bagMode) {
                    List<CosmeticDtos.CosmeticDto> ownedOnly = new ArrayList<>();
                    synchronized (ownedIds) {
                        for (CosmeticDtos.CosmeticDto row : items) {
                            if (row != null && row.id != null && ownedIds.contains(row.id)) {
                                ownedOnly.add(row);
                            }
                        }
                    }
                    items = ownedOnly;
                }
                catalogPage.postValue(new CatalogPage(loadType, items));
            } else {
                error.postValue(cat.error);
            }
        });
    }

    public void purchase(String cosmeticId) {
        final String type = filterType;
        container.getIoExecutor().execute(() -> {
            Result<CosmeticDtos.UserCosmeticDto> r =
                    container.getCosmeticsRepository().purchase(cosmeticId);
            if (!r.success) {
                error.postValue(r.error);
                return;
            }
            // Server already auto-equips; call equip again to sync worn URLs into session.
            Result<CosmeticDtos.EquipResult> eq =
                    container.getCosmeticsRepository().equip(cosmeticId);
            if (eq.success && eq.data != null && eq.data.profile != null) {
                applyEquipProfile(eq.data.profile);
            } else {
                Result<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> me =
                        container.getUserRepository().getMe();
                if (me.success && me.data != null) {
                    container.getSessionManager().updateCachedUser(me.data);
                }
            }
            inventoryLoadedAtMs = 0;
            message.postValue("تم الشراء والارتداء");
            loadForType(type);
        });
    }

    public void equip(String cosmeticId) {
        final String type = filterType;
        container.getIoExecutor().execute(() -> {
            Result<CosmeticDtos.EquipResult> r =
                    container.getCosmeticsRepository().equip(cosmeticId);
            if (r.success) {
                if (r.data != null && r.data.profile != null) {
                    applyEquipProfile(r.data.profile);
                }
                Result<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> me =
                        container.getUserRepository().getMe();
                if (me.success && me.data != null) {
                    container.getSessionManager().updateCachedUser(me.data);
                }
                inventoryLoadedAtMs = 0;
                message.postValue("تم الارتداء");
                loadForType(type);
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void unequip(String cosmeticId) {
        final String type = filterType;
        container.getIoExecutor().execute(() -> {
            Result<CosmeticDtos.EquipResult> r =
                    container.getCosmeticsRepository().unequip(cosmeticId);
            if (r.success) {
                if (r.data != null && r.data.profile != null) {
                    applyEquipProfile(r.data.profile);
                } else {
                    Result<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> me =
                            container.getUserRepository().getMe();
                    if (me.success && me.data != null) {
                        container.getSessionManager().updateCachedUser(me.data);
                    }
                }
                inventoryLoadedAtMs = 0;
                message.postValue("تم الخلع");
                loadForType(type);
            } else {
                error.postValue(r.error);
            }
        });
    }

    private void applyEquipProfile(CosmeticDtos.EquipProfile profile) {
        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto cached =
                container.getSessionManager().getUser();
        if (cached == null || profile == null) return;
        cached.entryEffectUrl = profile.entryEffectUrl;
        cached.entryAnimationUrl = profile.entryAnimationUrl;
        cached.roomCardUrl = profile.roomCardUrl;
        cached.levelBadgeUrl = profile.levelBadgeUrl;
        cached.vipBadgeUrl = profile.vipBadgeUrl;
        cached.hostBadgeUrl = profile.hostBadgeUrl;
        container.getSessionManager().updateCachedUser(cached);
    }
}
