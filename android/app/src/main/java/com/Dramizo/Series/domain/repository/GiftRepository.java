package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;

public interface GiftRepository {
    Result<GiftDtos.GiftList> getGifts();
    Result<GiftDtos.SendGiftResult> sendGift(String giftId, String receiverId, int qty, String roomId, int comboCount);
    Result<GiftDtos.SendGiftResult> sendAllMic(String giftId, java.util.List<String> receiverIds, int qty, String roomId, int comboCount);
}
