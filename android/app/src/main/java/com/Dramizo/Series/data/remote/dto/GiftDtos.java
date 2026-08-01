package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class GiftDtos {
    private GiftDtos() {}

    public static class GiftDto {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
        @SerializedName("description") public String description;
        @SerializedName("iconUrl") public String iconUrl;
        @SerializedName("animationUrl") public String animationUrl;
        @SerializedName("coinPrice") public int coinPrice;
        @SerializedName("diamondValue") public int diamondValue;
        @SerializedName("type") public String type;
        @SerializedName("category") public String category;
        @SerializedName("sortOrder") public int sortOrder;
    }

    public static class SendGiftRequest {
        @SerializedName("giftId") public String giftId;
        @SerializedName("receiverId") public String receiverId;
        @SerializedName("quantity") public int quantity;
        @SerializedName("roomId") public String roomId;
        @SerializedName("comboCount") public int comboCount;

        public SendGiftRequest(
                String giftId,
                String receiverId,
                int quantity,
                String roomId,
                int comboCount) {
            this.giftId = giftId;
            this.receiverId = receiverId;
            this.quantity = quantity;
            this.roomId = roomId;
            this.comboCount = Math.max(1, comboCount);
        }
    }

    public static class SendAllMicRequest {
        @SerializedName("giftId") public String giftId;
        @SerializedName("receiverIds") public java.util.List<String> receiverIds;
        @SerializedName("quantity") public int quantity;
        @SerializedName("roomId") public String roomId;
        @SerializedName("comboCount") public int comboCount;

        public SendAllMicRequest(
                String giftId,
                java.util.List<String> receiverIds,
                int quantity,
                String roomId,
                int comboCount) {
            this.giftId = giftId;
            this.receiverIds = receiverIds;
            this.quantity = quantity;
            this.roomId = roomId;
            this.comboCount = Math.max(1, comboCount);
        }
    }

    public static class SendGiftResult {
        @SerializedName("success") public boolean success;
        @SerializedName("coinsSpent") public long coinsSpent;
        @SerializedName("totalCoins") public long totalCoins;
        @SerializedName("wallet") public WalletDtos.WalletDto wallet;
        @SerializedName("comboCount") public int comboCount;
        @SerializedName("luckyMultiplier") public Double luckyMultiplier;
        @SerializedName("luckyCoinsWon") public long luckyCoinsWon;
        @SerializedName("coinPrice") public int coinPrice;
        @SerializedName("quantity") public int quantity;
        @SerializedName("personCount") public int personCount;
        @SerializedName("gift") public GiftDto gift;
        @SerializedName("senderBalance") public long senderBalance;
        @SerializedName("receiverDiamonds") public long receiverDiamonds;
        @SerializedName("hostDiamonds") public long hostDiamonds;
        @SerializedName("micsDiamonds") public long micsDiamonds;
        @SerializedName("platformCut") public long platformCut;
        @SerializedName("breakdown") public GiftBreakdown breakdown;
        @SerializedName("send") public GiftHistoryDto send;
        @SerializedName("receiverIds") public java.util.List<String> receiverIds;
    }

    /** Transparent coin/diamond split after a gift send (lucky + normal). */
    public static class GiftBreakdown {
        @SerializedName("coinsSpent") public long coinsSpent;
        @SerializedName("luckyReturn") public long luckyReturn;
        @SerializedName("luckyMultiplier") public double luckyMultiplier;
        @SerializedName("diamondsToHost") public long diamondsToHost;
        @SerializedName("diamondsToMics") public long diamondsToMics;
        @SerializedName("diamondsPlatform") public long diamondsPlatform;
        @SerializedName("diamondsAgency") public long diamondsAgency;
        @SerializedName("diamondPool") public long diamondPool;
    }

    public static class GiftHistoryDto {
        @SerializedName("id") public String id;
        @SerializedName("quantity") public int quantity;
        @SerializedName("totalCoins") public long totalCoins;
        @SerializedName("coinsSpent") public long coinsSpent;
        @SerializedName("diamondsAwarded") public long diamondsAwarded;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("gift") public GiftDto gift;
        @SerializedName("sender") public AuthDtos.UserDto sender;
        @SerializedName("receiver") public AuthDtos.UserDto receiver;

        public long displayCoins() {
            return totalCoins > 0 ? totalCoins : coinsSpent;
        }
    }

    /** Backend returns data as a raw JSON array of gifts. */
    public static class GiftList extends java.util.ArrayList<GiftDto> {}
}
