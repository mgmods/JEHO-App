package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class VanityDtos {
    private VanityDtos() {}

    public static class Catalog {
        @SerializedName("items") public List<VanityItem> items;
    }

    public static class VanityItem {
        @SerializedName("id") public String id;
        @SerializedName("publicId") public String publicId;
        @SerializedName("status") public String status;
        @SerializedName("priceCoins") public long priceCoins;
        @SerializedName("ownerUserId") public String ownerUserId;
        @SerializedName("reservedUntil") public String reservedUntil;
        @SerializedName("purchasedAt") public String purchasedAt;
    }

    public static class PurchaseResult {
        @SerializedName("publicId") public String publicId;
        @SerializedName("priceCoins") public long priceCoins;
        @SerializedName("userId") public String userId;
    }
}
