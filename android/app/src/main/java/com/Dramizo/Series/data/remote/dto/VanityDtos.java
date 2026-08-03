package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class VanityDtos {
    private VanityDtos() {}

    public static class Catalog {
        @SerializedName("items") public List<VanityItem> items;
        @SerializedName("leaseDays") public int leaseDays;
    }

    public static class MineResult {
        @SerializedName("item") public VanityItem item;
        @SerializedName("leaseDays") public int leaseDays;
    }

    public static class VanityItem {
        @SerializedName("id") public String id;
        @SerializedName("publicId") public String publicId;
        @SerializedName("status") public String status;
        @SerializedName("priceCoins") public long priceCoins;
        @SerializedName("renewPriceCoins") public long renewPriceCoins;
        @SerializedName("leaseDays") public int leaseDays;
        @SerializedName("ownerUserId") public String ownerUserId;
        @SerializedName("reservedUntil") public String reservedUntil;
        @SerializedName("purchasedAt") public String purchasedAt;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("active") public boolean active;
    }

    public static class PurchaseResult {
        @SerializedName("publicId") public String publicId;
        @SerializedName("priceCoins") public long priceCoins;
        @SerializedName("fullPriceCoins") public long fullPriceCoins;
        @SerializedName("renew") public boolean renew;
        @SerializedName("leaseDays") public int leaseDays;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("userId") public String userId;
    }
}
