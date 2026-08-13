package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;

public interface ChatRepository {
    Result<ChatDtos.ConversationList> getConversations();
    Result<ChatDtos.MessageList> getMessages(String conversationId, int page);
    /** Local Room cache for instant conversation open (may be empty). */
    java.util.List<ChatDtos.MessageDto> getCachedMessages(String conversationId);
    Result<ChatDtos.MessageDto> sendMessage(String conversationId, String content, String replyToId);
    Result<ChatDtos.MessageDto> sendImage(String conversationId, String imageUrl, String replyToId);
    Result<ChatDtos.MessageDto> sendAudio(String conversationId, String audioUrl, int durationSec, String replyToId);
    Result<ChatDtos.MessageDto> sendGift(String conversationId, String giftName, String iconUrl, String replyToId);
}
