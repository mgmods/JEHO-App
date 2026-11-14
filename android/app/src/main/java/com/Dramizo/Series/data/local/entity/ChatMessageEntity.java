package com.Dramizo.Series.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages_cache")
public class ChatMessageEntity {
    @PrimaryKey @NonNull public String id = "";
    public String conversationId;
    public String senderId;
    public String content;
    public String type;
    public String replyToId;
    public String createdAt;
    public long cachedAt;
}
