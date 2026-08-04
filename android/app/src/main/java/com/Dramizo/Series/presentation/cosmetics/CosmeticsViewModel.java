package com.Dramizo.Series.presentation.cosmetics;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class CosmeticsViewModel extends ViewModel {
    public static final String EXTRA_TYPE = "cosmetic_type";
    /** Default mall lease length — matches backend mallLeaseDays(). */
    public static final int LEASE_DAYS = 30;

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
    private final MutableLiveData<String> selectedId = new MutableLiveData<>(null);
    private final MutableLiveData<CosmeticDtos.CosmeticDto> selectedItem = new MutableLiveData<>(null);
    private final Set<String> ownedIds = new HashSet<>();
    private final Set<String> equippedIds = new HashSet<>();
    private final Map<String, String> expiresAtById = new HashMap<>();
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
    public LiveData<String> getSelectedId() { return selectedId; }
    public LiveData<CosmeticDtos.CosmeticDto> getSelectedItem() { return selectedItem; }

    public String getFilterType() { return filterType; }

    public void setFilterType(String type) { this.filterType = type; }

    public boolean isBagMode() { return bagMode; }

    public void setBagMode(boolean bagMode) { this.bagMode = bagMode; }

    public boolean isOwned(String cosmeticId) {
        if (cosmeticId == null) return false;
        synchronized (ownedIds) {
            if (!ownedIds.contains(cosmeticId)) return false;
            return !isExpired(cosmeticId);
        }
    }

    public boolean isEquipped(String cosmeticId) {
        if (cosmeticId == null) return false;
        synchronized (ownedIds) {
            return equippedIds.contains(cosmeticId) && isOwned(cosmeticId);
        }
    }

    /** Days left (1+) or null if permanent / not owned. 0 if expired. */
    @Nullable
    public Integer daysLeft(String cosmeticId) {
        if (cosmeticId == null) return null;
        String exp;
        synchronized (ownedIds) {
            if (!ownedIds.contains(cosmeticId)) return null;
            exp = expiresAtById.get(cosmeticId);
        }
        if (exp == null || exp.isEmpty()) return null;
        long end = parseExpiresMs(exp);
        if (end <= 0L) return null;
        long ms = end - System.currentTimeMillis();
        if (ms <= 0L) return 0;
        return (int) Math.max(1L, (ms + 86_399_999L) / 86_400_000L);
    }

    public boolean isExpired(String cosmeticId) {
        Integer d = daysLeftInternal(cosmeticId);
        return d != null && d == 0;
    }

    @Nullable
    private Integer daysLeftInternal(String cosmeticId) {
        String exp = expiresAtById.get(cosmeticId);
        if (exp == null || exp.isEmpty()) return null;
        long end = parseExpiresMs(exp);
        if (end <= 0L) return null;
        long ms = end - System.currentTimeMillis();
        if (ms <= 0L) return 0;
        return (int) Math.max(1L, (ms + 86_399_999L) / 86_400_000L);
    }

    public void select(@Nullable CosmeticDtos.CosmeticDto item) {
        if (item == null) {
            selectedId.postValue(null);
            selectedItem.postValue(null);
            return;
        }
        selectedId.postValue(item.id);
        selectedItem.postValue(item);
    }

    public void clearSelection() {
        selectedId.setValue(null);
        selectedItem.setValue(null);
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
                        expiresAtById.clear();
                        for (CosmeticDtos.UserCosmeticDto row : inv.data) {
                            if (row.cosmeticId == null) continue;
                            ownedIds.add(row.cosmeticId);
                            if (row.expiresAt != null && !row.expiresAt.isEmpty()) {
                                expiresAtById.put(row.cosmeticId, row.expiresAt);
                            }
                            if (row.equipped) equippedIds.add(row.cosmeticId);
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
                            if (row != null && row.id != null && isOwned(row.id)) {
                                ownedOnly.add(row);
                            }
                        }
                    }
                    items = ownedOnly;
                }
                catalogPage.postValue(new CatalogPage(loadType, items));
                // Keep selection if still in this page.
                CosmeticDtos.CosmeticDto sel = selectedItem.getValue();
                if (sel != null && loadType != null && loadType.equals(filterType)) {
                    CosmeticDtos.CosmeticDto found = null;
                    for (CosmeticDtos.CosmeticDto row : items) {
                        if (row != null && sel.id != null && sel.id.equals(row.id)) {
                            found = row;
                            break;
                        }
                    }
                    if (found == null && !items.isEmpty()) {
                        select(items.get(0));
                    } else if (found != null) {
                        selectedItem.postValue(found);
                    }
                } else if ((selectedItem.getValue() == null) && !items.isEmpty()
                        && loadType != null && loadType.equals(filterType)) {
                    // Auto-preview first item when opening a tab (Majlis-like feel).
                    select(items.get(0));
                }
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
            if (r.data != null && r.data.expiresAt != null) {
                synchronized (ownedIds) {
                    ownedIds.add(cosmeticId);
                    expiresAtById.put(cosmeticId, r.data.expiresAt);
                }
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

    /** Buy again / half-price extend — same API as purchase when already owned. */
    public void renew(String cosmeticId) {
        purchase(cosmeticId);
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

    private static long parseExpiresMs(String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        String s = raw.trim();
        try {
            // Instant / ISO-8601 with Z
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(s);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }
}
