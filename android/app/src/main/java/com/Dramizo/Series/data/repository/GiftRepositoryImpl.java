package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.GiftDao;
import com.Dramizo.Series.data.local.entity.GiftEntity;
import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.remote.api.GiftApi;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.GiftRepository;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Stale-while-revalidate gift catalog: memory → Room → network.
 * Sheet open reads local first so the UI is never blocked on RTT.
 */
public class GiftRepositoryImpl implements GiftRepository {
    private static final AtomicReference<GiftDtos.GiftList> MEMORY = new AtomicReference<>();
    private static final AtomicReference<List<GiftDtos.GiftCategoryDto>> MEMORY_CATS =
            new AtomicReference<>();
    private static final AtomicBoolean REFRESH_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean CATEGORIES_REFRESH_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile long memoryAt;
    private static volatile long categoriesMemoryAt;
    private static final long MEMORY_SOFT_TTL_MS = 10 * 60_000L;

    private final GiftApi api;
    private final GiftDao dao;
    private final ExecutorService io;
    private final EncryptedFeatureCache featureCache;

    public GiftRepositoryImpl(GiftApi api, GiftDao dao, ExecutorService io) {
        this(api, dao, io, null);
    }

    public GiftRepositoryImpl(GiftApi api, GiftDao dao, ExecutorService io, EncryptedFeatureCache featureCache) {
        this.api = api;
        this.dao = dao;
        this.io = io;
        this.featureCache = featureCache;
    }

    @Override
    public Result<GiftDtos.GiftList> getGifts() {
        // Stale-while-revalidate: never put a catalog network round-trip in front of
        // a usable in-memory or Room catalog, even after the soft TTL expires.
        GiftDtos.GiftList mem = MEMORY.get();
        if (mem != null) {
            if (System.currentTimeMillis() - memoryAt >= MEMORY_SOFT_TTL_MS) {
                refreshInBackground();
            }
            if (!mem.isEmpty() || System.currentTimeMillis() - memoryAt < MEMORY_SOFT_TTL_MS) {
                return Result.ok(copyList(mem));
            }
        }

        // Cold process start: Room is the first fallback, not the last one.
        GiftDtos.GiftList local = peekLocalOrNull();
        if (local != null && !local.isEmpty()) {
            refreshInBackground();
            return Result.ok(local);
        }

        // First-ever install (no memory and no Room rows) still needs the network.
        Result<GiftDtos.GiftList> net = fetchNetwork();
        if (net.success && net.data != null && !net.data.isEmpty()) {
            return net;
        }
        return net.success && net.data != null
                ? net
                : Result.err(net.error != null ? net.error : "لا توجد هدايا");
    }

    private void refreshInBackground() {
        if (!REFRESH_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            io.execute(() -> {
                try {
                    fetchNetwork();
                } finally {
                    REFRESH_IN_FLIGHT.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            REFRESH_IN_FLIGHT.set(false);
        }
    }

    @Override
    public GiftDtos.GiftList peekLocalOrNull() {
        GiftDtos.GiftList mem = MEMORY.get();
        if (mem != null) {
            return copyList(mem);
        }
        try {
            List<GiftEntity> local = dao.getAll();
            if (local == null || local.isEmpty()) return null;
            GiftDtos.GiftList list = fromEntities(local);
            if (!list.isEmpty()) {
                MEMORY.set(copyList(list));
                memoryAt = System.currentTimeMillis();
                return list;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Result<GiftDtos.GiftList> refreshGifts() {
        GiftDtos.GiftList warm = MEMORY.get();
        if (warm != null && System.currentTimeMillis() - memoryAt < MEMORY_SOFT_TTL_MS) {
            return Result.ok(copyList(warm));
        }
        GiftDtos.GiftList localWarm = peekLocalOrNull();
        if (localWarm != null && !localWarm.isEmpty()
                && System.currentTimeMillis() - memoryAt < MEMORY_SOFT_TTL_MS) {
            return Result.ok(localWarm);
        }
        Result<GiftDtos.GiftList> net = fetchNetwork();
        if (net.success && net.data != null) {
            return net;
        }
        GiftDtos.GiftList local = peekLocalOrNull();
        if (local != null && !local.isEmpty()) {
            return Result.ok(local);
        }
        return net.success && net.data != null
                ? net
                : Result.err(net.error != null ? net.error : "لا توجد هدايا");
    }

    @Override
    public List<GiftDtos.GiftCategoryDto> peekCategoriesOrNull() {
        List<GiftDtos.GiftCategoryDto> mem = MEMORY_CATS.get();
        if (mem != null) return new ArrayList<>(mem);
        if (featureCache != null) {
            Type type = new TypeToken<List<GiftDtos.GiftCategoryDto>>() {}.getType();
            List<GiftDtos.GiftCategoryDto> disk = featureCache.getJson(
                    EncryptedFeatureCache.UID_APP, "gift_categories", type, 0L);
            if (disk != null) {
                MEMORY_CATS.set(new ArrayList<>(disk));
                categoriesMemoryAt = System.currentTimeMillis();
                return new ArrayList<>(disk);
            }
        }
        return null;
    }

    @Override
    public Result<List<GiftDtos.GiftCategoryDto>> refreshCategories() {
        List<GiftDtos.GiftCategoryDto> cached = peekCategoriesOrNull();
        if (cached != null) {
            if (System.currentTimeMillis() - categoriesMemoryAt >= MEMORY_SOFT_TTL_MS) {
                refreshCategoriesInBackground();
            }
            return Result.ok(cached);
        }
        Result<GiftDtos.GiftCategoryList> r = ApiCall.execute(api.categories());
        if (r.success && r.data != null) {
            // Dashboard is authoritative — even an empty list wins over any cached fallback.
            MEMORY_CATS.set(new ArrayList<>(r.data));
            categoriesMemoryAt = System.currentTimeMillis();
            if (featureCache != null) featureCache.putJson(
                    EncryptedFeatureCache.UID_APP, "gift_categories", r.data);
            return Result.ok(new ArrayList<>(r.data));
        }
        List<GiftDtos.GiftCategoryDto> mem = peekCategoriesOrNull();
        if (mem != null) return Result.ok(mem);
        return Result.ok(new ArrayList<>());
    }

    private void refreshCategoriesInBackground() {
        if (!CATEGORIES_REFRESH_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            io.execute(() -> {
                try {
                    Result<GiftDtos.GiftCategoryList> result = ApiCall.execute(api.categories());
                    if (result.success && result.data != null) {
                        List<GiftDtos.GiftCategoryDto> fresh = new ArrayList<>(result.data);
                        MEMORY_CATS.set(fresh);
                        categoriesMemoryAt = System.currentTimeMillis();
                        if (featureCache != null) featureCache.putJson(
                                EncryptedFeatureCache.UID_APP, "gift_categories", fresh);
                    }
                } finally { CATEGORIES_REFRESH_IN_FLIGHT.set(false); }
            });
        } catch (RuntimeException rejected) { CATEGORIES_REFRESH_IN_FLIGHT.set(false); }
    }

    private Result<GiftDtos.GiftList> fetchNetwork() {
        Result<GiftDtos.GiftList> r = ApiCall.execute(api.list());
        if (r.success && r.data != null) {
            GiftDtos.GiftList list = copyList(r.data);
            // A successful empty catalogue is authoritative; never resurrect deleted rows.
            MEMORY.set(list);
            memoryAt = System.currentTimeMillis();
            if (list.isEmpty()) {
                io.execute(() -> {
                    try { dao.clear(); } catch (Exception ignored) { }
                });
            } else {
                persistAsync(list);
            }
            return Result.ok(copyList(list));
        }
        return r;
    }

    private void persistAsync(GiftDtos.GiftList list) {
        final List<GiftEntity> cached = new ArrayList<>();
        java.util.LinkedHashMap<String, GiftEntity> uniq = new java.util.LinkedHashMap<>();
        for (GiftDtos.GiftDto g : list) {
            if (g == null || g.id == null || g.id.isEmpty()) continue;
            GiftEntity e = new GiftEntity();
            e.id = g.id;
            e.name = g.name;
            e.iconUrl = g.iconUrl;
            e.animationUrl = g.animationUrl;
            e.coinPrice = g.coinPrice;
            e.diamondValue = g.diamondValue;
            e.type = g.type;
            e.category = g.category;
            e.sortOrder = g.sortOrder;
            e.cachedAt = System.currentTimeMillis();
            // The server gift ID is the unique identity. Multiple gifts may share art.
            String key = "id:" + g.id;
            if (uniq.containsKey(key)) continue;
            uniq.put(key, e);
        }
        cached.addAll(uniq.values());
        if (cached.isEmpty()) return;
        io.execute(() -> {
            try {
                dao.clear();
                dao.upsertAll(cached);
            } catch (Exception ignored) {
            }
        });
    }

    private static String localArtStem(String url) {
        if (url == null || url.isEmpty()) return "";
        String raw = url.trim().toLowerCase(java.util.Locale.US).split("#")[0].split("\\?")[0]
                .replace('\\', '/');
        int slash = raw.lastIndexOf('/');
        String base = slash >= 0 ? raw.substring(slash + 1) : raw;
        if (base.isEmpty() || base.endsWith(".html")) return "";
        base = base.replaceAll("\\.(png|webp|jpe?g|gif|svg|mp4|webm)$", "");
        base = base.replaceAll("^bg_", "");
        base = base.replaceAll("^gift[-_]?", "");
        base = base.replaceAll("[-_\\s]+", "");
        return base;
    }

    private static GiftDtos.GiftList fromEntities(List<GiftEntity> local) {
        GiftDtos.GiftList list = new GiftDtos.GiftList();
        for (GiftEntity e : local) {
            if (e == null || e.id == null || e.id.isEmpty()) continue;
            GiftDtos.GiftDto g = new GiftDtos.GiftDto();
            g.id = e.id;
            g.name = e.name;
            g.iconUrl = e.iconUrl;
            g.animationUrl = e.animationUrl;
            g.coinPrice = e.coinPrice;
            g.diamondValue = e.diamondValue;
            g.type = e.type;
            g.category = e.category;
            g.sortOrder = e.sortOrder;
            list.add(g);
        }
        return list;
    }

    private static GiftDtos.GiftList copyList(GiftDtos.GiftList src) {
        GiftDtos.GiftList out = new GiftDtos.GiftList();
        if (src != null) out.addAll(src);
        return out;
    }

    /** No hardcoded categories — dashboard/API only. Empty list = board shows no tabs. */
    public static List<GiftDtos.GiftCategoryDto> defaultCategories() {
        return new ArrayList<>();
    }

    @Override
    public Result<GiftDtos.SendGiftResult> sendGift(
            String giftId, String receiverId, int qty, String roomId, int comboCount) {
        return ApiCall.execute(
                api.send(new GiftDtos.SendGiftRequest(giftId, receiverId, qty, roomId, comboCount)));
    }

    @Override
    public Result<GiftDtos.SendGiftResult> sendAllMic(
            String giftId, List<String> receiverIds, int qty, String roomId, int comboCount) {
        return ApiCall.execute(api.sendAllMic(
                new GiftDtos.SendAllMicRequest(giftId, receiverIds, qty, roomId, comboCount)));
    }

    /** True when memory catalog is young enough to skip spinner entirely. */
    public static boolean hasWarmMemory() {
        GiftDtos.GiftList mem = MEMORY.get();
        return mem != null && !mem.isEmpty()
                && (System.currentTimeMillis() - memoryAt) < MEMORY_SOFT_TTL_MS;
    }
}
