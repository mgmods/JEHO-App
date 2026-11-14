package com.Dramizo.Series.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "wallet_cache")
public class WalletEntity {
    @PrimaryKey @NonNull public String userId = "";
    public long coins;
    public long diamonds;
    public long cachedAt;
}
