package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.domain.model.Result;

import java.util.List;

public interface GiftRepository {
    Result<GiftDtos.GiftList> getGifts();

    /** Instant local catalog (memory / Room). Null if nothing cached yet. */
    GiftDtos.GiftList peekLocalOrNull();

    /** Network refresh (falls back to local). */
    Result<GiftDtos.GiftList> refreshGifts();

    List<GiftDtos.GiftCategoryDto> peekCategoriesOrNull();

    Result<List<GiftDtos.GiftCategoryDto>> refreshCategories();

    Result<GiftDtos.SendGiftResult> sendGift(
            String giftId, String receiverId, int qty, String roomId, int comboCount);

    Result<GiftDtos.SendGiftResult> sendAllMic(
            String giftId, java.util.List<String> receiverIds, int qty, String roomId, int comboCount);
}
