package com.Dramizo.Series.data.local.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.Dramizo.Series.data.local.dao.ChatMessageDao;
import com.Dramizo.Series.data.local.dao.GiftDao;
import com.Dramizo.Series.data.local.dao.UserDao;
import com.Dramizo.Series.data.local.dao.WalletDao;
import com.Dramizo.Series.data.local.entity.ChatMessageEntity;
import com.Dramizo.Series.data.local.entity.GiftEntity;
import com.Dramizo.Series.data.local.entity.UserEntity;
import com.Dramizo.Series.data.local.entity.WalletEntity;

@Database(
        entities = {
                UserEntity.class,
                ChatMessageEntity.class,
                WalletEntity.class,
                GiftEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AuraDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract WalletDao walletDao();
    public abstract GiftDao giftDao();
}
