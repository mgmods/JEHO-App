package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class GameStoreDtos {
    private GameStoreDtos() {}

    public static class CatalogItem {
        @SerializedName("sku") public String sku;
        @SerializedName("title") public String title;
        @SerializedName("costPoints") public int costPoints;
        @SerializedName("section") public String section;
    }

    public static class CatalogResult {
        @SerializedName("items") public List<CatalogItem> items;
    }

    public static class OwnedItem {
        @SerializedName("sku") public String sku;
        @SerializedName("title") public String title;
        @SerializedName("costPoints") public int costPoints;
        @SerializedName("section") public String section;
        @SerializedName("equipped") public boolean equipped;
    }

    public static class PurchaseResult {
        @SerializedName("coins") public long coins;
        @SerializedName("gamePoints") public long gamePoints;
        @SerializedName("item") public OwnedItem item;
        @SerializedName("loadout") public java.util.Map<String, String> loadout;
    }

    public static class EquipResult {
        @SerializedName("ok") public boolean ok;
        @SerializedName("section") public String section;
        @SerializedName("sku") public String sku;
        @SerializedName("loadout") public java.util.Map<String, String> loadout;
    }
}
