package com.Dramizo.Series.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.Dramizo.Series.data.local.entity.WalletEntity;

@Dao
public interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WalletEntity wallet);

    @Query("SELECT * FROM wallet_cache WHERE userId = :userId LIMIT 1")
    WalletEntity get(String userId);
}
