package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.api.CosmeticsApi;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.CosmeticsRepository;
import com.Dramizo.Series.util.ApiCall;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CosmeticsRepositoryImpl implements CosmeticsRepository {
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private final CosmeticsApi api;
    private final EncryptedFeatureCache cache;
    private final SessionManager sessionManager;
    private final ExecutorService io;
    private final ConcurrentHashMap<String, AtomicBoolean> refreshes = new ConcurrentHashMap<>();

    public CosmeticsRepositoryImpl(CosmeticsApi api, EncryptedFeatureCache cache,
                                   SessionManager sessionManager, ExecutorService io) {
        this.api = api;
        this.cache = cache;
        this.sessionManager = sessionManager;
        this.io = io;
    }

    @Override
    public Result<List<CosmeticDtos.CosmeticDto>> catalog(String type) {
        String safeType = type == null || type.trim().isEmpty() ? "all" : type.trim();
        String namespace = "cosmetic_catalog_" + safeType;
        CosmeticDtos.CatalogList local = cache.getJson(
                EncryptedFeatureCache.UID_APP, namespace, CosmeticDtos.CatalogList.class, 0L);
        if (local != null) {
            if (!cache.isFresh(EncryptedFeatureCache.UID_APP, namespace, CACHE_TTL_MS)) {
                refreshCatalogInBackground(safeType, namespace);
            }
            return Result.ok(local);
        }
        Result<CosmeticDtos.CatalogList> network = ApiCall.execute(api.catalog(type));
        if (network.success && network.data != null) {
            cache.putJson(EncryptedFeatureCache.UID_APP, namespace, network.data);
            return Result.ok(network.data);
        }
        return Result.err(network.error);
    }

    private void refreshCatalogInBackground(String type, String namespace) {
        runRefresh(namespace, () -> {
            Result<CosmeticDtos.CatalogList> fresh = ApiCall.execute(api.catalog(type));
            if (fresh.success && fresh.data != null) {
                cache.putJson(EncryptedFeatureCache.UID_APP, namespace, fresh.data);
            }
        });
    }

    @Override
    public Result<List<CosmeticDtos.UserCosmeticDto>> inventory() {
        String userId = sessionManager != null ? sessionManager.getUserId() : null;
        if (userId == null || userId.trim().isEmpty()) {
            Result<CosmeticDtos.InventoryList> network = ApiCall.execute(api.inventory());
            if (network.success && network.data != null) return Result.ok(network.data);
            if (network.success) return Result.ok(Collections.emptyList());
            return Result.err(network.error);
        }
        String namespace = "cosmetic_inventory_" + userId;
        CosmeticDtos.InventoryList local = cache.getJson(
                EncryptedFeatureCache.UID_APP, namespace, CosmeticDtos.InventoryList.class, 0L);
        if (local != null) {
            if (!cache.isFresh(EncryptedFeatureCache.UID_APP, namespace, CACHE_TTL_MS)) {
                refreshInventoryInBackground(namespace);
            }
            return Result.ok(local);
        }
        Result<CosmeticDtos.InventoryList> network = ApiCall.execute(api.inventory());
        if (network.success && network.data != null) {
            cache.putJson(EncryptedFeatureCache.UID_APP, namespace, network.data);
            return Result.ok(network.data);
        }
        if (network.success) {
            cache.putJson(EncryptedFeatureCache.UID_APP, namespace, new CosmeticDtos.InventoryList());
            return Result.ok(Collections.emptyList());
        }
        return Result.err(network.error);
    }

    private void refreshInventoryInBackground(String namespace) {
        runRefresh(namespace, () -> {
            Result<CosmeticDtos.InventoryList> fresh = ApiCall.execute(api.inventory());
            if (fresh.success && fresh.data != null) {
                cache.putJson(EncryptedFeatureCache.UID_APP, namespace, fresh.data);
            } else if (fresh.success) {
                cache.putJson(EncryptedFeatureCache.UID_APP, namespace,
                        new CosmeticDtos.InventoryList());
            }
        });
    }

    private void runRefresh(String namespace, Runnable task) {
        AtomicBoolean gate = refreshes.computeIfAbsent(namespace, key -> new AtomicBoolean(false));
        if (!gate.compareAndSet(false, true)) return;
        try {
            io.execute(() -> {
                try { task.run(); }
                finally { gate.set(false); }
            });
        } catch (RuntimeException rejected) {
            gate.set(false);
        }
    }

    @Override
    public Result<CosmeticDtos.UserCosmeticDto> purchase(String cosmeticId) {
        return ApiCall.execute(api.purchase(new CosmeticDtos.PurchaseRequest(cosmeticId)));
    }

    @Override
    public Result<CosmeticDtos.EquipResult> equip(String cosmeticId) {
        return ApiCall.execute(api.equip(new CosmeticDtos.EquipRequest(cosmeticId)));
    }

    @Override
    public Result<CosmeticDtos.EquipResult> unequip(String cosmeticId) {
        return ApiCall.execute(api.unequip(new CosmeticDtos.EquipRequest(cosmeticId)));
    }
}
