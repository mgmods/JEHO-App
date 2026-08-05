package com.Dramizo.Series.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "gifts_cache")
public class GiftEntity {
    @PrimaryKey @NonNull public String id = "";
    public String name;
    public String iconUrl;
    public String animationUrl;
    public int coinPrice;
    public int diamondValue;
    public String type;
    public String category;
    public int sortOrder;
    public long cachedAt;
}
