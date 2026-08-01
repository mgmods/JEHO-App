package com.Dramizo.Series.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.Dramizo.Series.data.local.entity.UserEntity;
import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserEntity user);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<UserEntity> users);

    @Query("SELECT * FROM users_cache WHERE id = :id LIMIT 1")
    UserEntity getById(String id);

    @Query("DELETE FROM users_cache")
    void clear();
}
