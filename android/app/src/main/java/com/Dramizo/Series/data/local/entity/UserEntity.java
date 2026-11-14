package com.Dramizo.Series.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users_cache")
public class UserEntity {
    @PrimaryKey @NonNull public String id = "";
    public String username;
    public String displayName;
    public String avatarUrl;
    public String bio;
    public int level;
    public int vipLevel;
    public int followersCount;
    public int followingCount;
    public long cachedAt;
}
