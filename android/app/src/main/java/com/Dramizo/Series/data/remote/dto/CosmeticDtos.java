package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class CosmeticDtos {
    private CosmeticDtos() {}

    public static class CosmeticDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("code") public String code;
        @SerializedName("name") public String name;
        @SerializedName("description") public String description;
        @SerializedName("previewUrl") public String previewUrl;
        @SerializedName("animationUrl") public String animationUrl;
        @SerializedName("coinPrice") public int coinPrice;
        @SerializedName("minVipLevel") public int minVipLevel;
        @SerializedName("minUserLevel") public int minUserLevel;
        @SerializedName("isActive") public boolean isActive;
        @SerializedName("meta") public java.util.Map<String, Object> meta;

    }

    public static class UserCosmeticDto {
        @SerializedName("id") public String id;
        @SerializedName("cosmeticId") public String cosmeticId;
        @SerializedName("equipped") public boolean equipped;
        @SerializedName("cosmetic") public CosmeticDto cosmetic;
    }

    public static class PurchaseRequest {
        @SerializedName("cosmeticId") public String cosmeticId;
        public PurchaseRequest(String cosmeticId) { this.cosmeticId = cosmeticId; }
    }

    public static class EquipRequest {
        @SerializedName("cosmeticId") public String cosmeticId;
        public EquipRequest(String cosmeticId) { this.cosmeticId = cosmeticId; }
    }

    public static class EquipResult {
        @SerializedName("equipped") public boolean equipped;
        @SerializedName("cosmetic") public CosmeticDto cosmetic;
        @SerializedName("profile") public EquipProfile profile;
    }

    public static class EquipProfile {
        @SerializedName("entryEffectUrl") public String entryEffectUrl;
        @SerializedName("entryAnimationUrl") public String entryAnimationUrl;
        @SerializedName("roomCardUrl") public String roomCardUrl;
        @SerializedName("levelBadgeUrl") public String levelBadgeUrl;
        @SerializedName("vipBadgeUrl") public String vipBadgeUrl;
        @SerializedName("hostBadgeUrl") public String hostBadgeUrl;
    }

    public static class CatalogList extends java.util.ArrayList<CosmeticDto> {}

    public static class InventoryList extends java.util.ArrayList<UserCosmeticDto> {}
}
