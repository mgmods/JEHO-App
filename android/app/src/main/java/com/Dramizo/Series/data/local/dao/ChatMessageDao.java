package com.Dramizo.Series.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.Dramizo.Series.data.local.entity.ChatMessageEntity;
import java.util.List;

@Dao
public interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ChatMessageEntity> messages);

    @Query("SELECT * FROM chat_messages_cache WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    List<ChatMessageEntity> getByConversation(String conversationId);

    @Query("DELETE FROM chat_messages_cache WHERE conversationId = :conversationId")
    void clearConversation(String conversationId);
}
