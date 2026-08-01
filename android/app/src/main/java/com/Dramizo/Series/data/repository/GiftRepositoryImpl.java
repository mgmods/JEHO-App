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

public class GiftRepositoryImpl implements GiftRepository {
    private final GiftApi api;
    private final GiftDao dao;
    private final ExecutorService io;

    public GiftRepositoryImpl(GiftApi api, GiftDao dao, ExecutorService io) {
        this.api = api; this.dao = dao; this.io = io;
    }

    @Override public Result<GiftDtos.GiftList> getGifts() {
        Result<GiftDtos.GiftList> r = ApiCall.execute(api.list());
        if (r.success && r.data != null && !r.data.isEmpty()) {
            List<GiftEntity> cached = new ArrayList<>();
            for (GiftDtos.GiftDto g : r.data) {
                GiftEntity e = new GiftEntity();
                e.id = g.id != null ? g.id : "";
                e.name = g.name;
                e.iconUrl = g.iconUrl;
                e.animationUrl = g.animationUrl;
                e.coinPrice = g.coinPrice;
                e.type = g.type;
                e.sortOrder = g.sortOrder;
                e.cachedAt = System.currentTimeMillis();
                cached.add(e);
            }
            io.execute(() -> {
                try {
                    dao.clear();
                    dao.upsertAll(cached);
                } catch (Exception ignored) {
                }
            });
            return r;
        }
        // Network empty/failed → serve last good catalog so send UI still works.
        try {
            List<GiftEntity> local = dao.getAll();
            if (local != null && !local.isEmpty()) {
                GiftDtos.GiftList list = new GiftDtos.GiftList();
                for (GiftEntity e : local) {
                    if (e == null || e.id == null || e.id.isEmpty()) continue;
                    GiftDtos.GiftDto g = new GiftDtos.GiftDto();
                    g.id = e.id;
                    g.name = e.name;
                    g.iconUrl = e.iconUrl;
                    g.animationUrl = e.animationUrl;
                    g.coinPrice = e.coinPrice;
                    g.type = e.type;
                    g.sortOrder = e.sortOrder;
                    list.add(g);
                }
                if (!list.isEmpty()) return Result.ok(list);
            }
        } catch (Exception ignored) {
        }
        return r.success && r.data != null ? r : Result.err(
                r.error != null ? r.error : "لا توجد هدايا");
    }

    @Override public Result<GiftDtos.SendGiftResult> sendGift(String giftId, String receiverId, int qty, String roomId, int comboCount) {
        return ApiCall.execute(api.send(new GiftDtos.SendGiftRequest(giftId, receiverId, qty, roomId, comboCount)));
    }

    @Override public Result<GiftDtos.SendGiftResult> sendAllMic(String giftId, List<String> receiverIds, int qty, String roomId, int comboCount) {
        return ApiCall.execute(api.sendAllMic(
                new GiftDtos.SendAllMicRequest(giftId, receiverIds, qty, roomId, comboCount)));
    }
}
