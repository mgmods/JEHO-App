package com.Dramizo.Series.domain.usecase.gift;

import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.GiftRepository;

public class SendGiftUseCase {
    private final GiftRepository repo;
    public SendGiftUseCase(GiftRepository repo) { this.repo = repo; }
    public Result<GiftDtos.SendGiftResult> execute(String giftId, String receiverId, int qty, String roomId) {
        return execute(giftId, receiverId, qty, roomId, 1);
    }
    public Result<GiftDtos.SendGiftResult> execute(String giftId, String receiverId, int qty, String roomId, int comboCount) {
        return repo.sendGift(giftId, receiverId, qty, roomId, comboCount);
    }

    public Result<GiftDtos.SendGiftResult> executeAllMic(
            String giftId,
            java.util.List<String> receiverIds,
            int qty,
            String roomId,
            int comboCount) {
        return repo.sendAllMic(giftId, receiverIds, qty, roomId, comboCount);
    }
}
