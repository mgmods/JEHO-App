package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.GiftDao;
import com.Dramizo.Series.data.local.entity.GiftEntity;
import com.Dramizo.Series.data.remote.api.GiftApi;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.GiftRepository;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stale-while-revalidate gift catalog: memory → Room → network.
 * Sheet open reads local first so the UI is never blocked on RTT.
 */
public class GiftRepositoryImpl implements GiftRepository {
    private static final AtomicReference<GiftDtos.GiftList> MEMORY = new AtomicReference<>();
    private static final AtomicReference<List<GiftDtos.GiftCategoryDto>> MEMORY_CATS =
            new AtomicReference<>();
    private static volatile long memoryAt;
    private static final long MEMORY_SOFT_TTL_MS = 10 * 60_000L;

    private final GiftApi api;
    private final GiftDao dao;
    private final ExecutorService io;

    public GiftRepositoryImpl(GiftApi api, GiftDao dao, ExecutorService io) {
        this.api = api;
        this.dao = dao;
        this.io = io;
    }

    @Override
    public Result<GiftDtos.GiftList> getGifts() {
        // Prefer a fresh network catalog; callers wanting instant UI use peekLocal first.
        Result<GiftDtos.GiftList> net = fetchNetwork();
        if (net.success && net.data != null && !net.data.isEmpty()) {
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
    public GiftDtos.GiftList peekLocalOrNull() {
        GiftDtos.GiftList mem = MEMORY.get();
        if (mem != null && !mem.isEmpty()) {
            return copyList(mem);
        }
        try {
            List<GiftEntity> local = dao.getAll();
            if (local == null || local.isEmpty()) return null;
            GiftDtos.GiftList list = fromEntities(local);
            if (!list.isEmpty()) {
                MEMORY.set(copyList(list));
                return list;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public Result<GiftDtos.GiftList> refreshGifts() {
        Result<GiftDtos.GiftList> net = fetchNetwork();
        if (net.success && net.data != null && !net.data.isEmpty()) {
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
        return mem != null && !mem.isEmpty()
                ? new ArrayList<>(mem)
                : null;
    }

    @Override
    public Result<List<GiftDtos.GiftCategoryDto>> refreshCategories() {
        Result<GiftDtos.GiftCategoryList> r = ApiCall.execute(api.categories());
        if (r.success && r.data != null && !r.data.isEmpty()) {
            MEMORY_CATS.set(new ArrayList<>(r.data));
            return Result.ok(new ArrayList<>(r.data));
        }
        List<GiftDtos.GiftCategoryDto> mem = peekCategoriesOrNull();
        if (mem != null) return Result.ok(mem);
        return Result.ok(defaultCategories());
    }

    private Result<GiftDtos.GiftList> fetchNetwork() {
        Result<GiftDtos.GiftList> r = ApiCall.execute(api.list());
        if (r.success && r.data != null && !r.data.isEmpty()) {
            GiftDtos.GiftList list = copyList(r.data);
            MEMORY.set(list);
            memoryAt = System.currentTimeMillis();
            persistAsync(list);
            return Result.ok(copyList(list));
        }
        return r;
    }

    private void persistAsync(GiftDtos.GiftList list) {
        final List<GiftEntity> cached = new ArrayList<>();
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
            cached.add(e);
        }
        if (cached.isEmpty()) return;
        io.execute(() -> {
            try {
                dao.clear();
                dao.upsertAll(cached);
            } catch (Exception ignored) {
            }
        });
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

    public static List<GiftDtos.GiftCategoryDto> defaultCategories() {
        List<GiftDtos.GiftCategoryDto> out = new ArrayList<>();
        String[][] rows = {
                {"normal", "عادي", "Normal"},
                {"lucky", "حظ", "Lucky"},
                {"combo", "كومبو", "Combo"},
                {"premium", "مميز", "Premium"},
        };
        for (int i = 0; i < rows.length; i++) {
            GiftDtos.GiftCategoryDto c = new GiftDtos.GiftCategoryDto();
            c.key = rows[i][0];
            c.labelAr = rows[i][1];
            c.labelEn = rows[i][2];
            c.sortOrder = i;
            c.isActive = true;
            out.add(c);
        }
        return out;
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
