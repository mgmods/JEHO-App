package com.Dramizo.Series.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.Dramizo.Series.data.local.entity.GiftEntity;
import java.util.List;

@Dao
public interface GiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<GiftEntity> gifts);

    @Query("SELECT * FROM gifts_cache ORDER BY sortOrder ASC")
    List<GiftEntity> getAll();

    @Query("DELETE FROM gifts_cache")
    void clear();
}
