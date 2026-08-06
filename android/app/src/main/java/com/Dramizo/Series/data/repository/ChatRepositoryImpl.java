package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.local.dao.ChatMessageDao;
import com.Dramizo.Series.data.local.entity.ChatMessageEntity;
import com.Dramizo.Series.data.remote.api.ChatApi;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.ChatRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ChatRepositoryImpl implements ChatRepository {
    private final ChatApi api;
    private final ChatMessageDao dao;
    private final ExecutorService io;

    public ChatRepositoryImpl(ChatApi api, ChatMessageDao dao, ExecutorService io) {
        this.api = api; this.dao = dao; this.io = io;
    }

    @Override public Result<ChatDtos.ConversationList> getConversations() {
        return ApiCall.execute(api.conversations());
    }

    @Override public Result<ChatDtos.MessageList> getMessages(String conversationId, int page) {
        Result<ChatDtos.MessageList> result = ApiCall.execute(api.messages(conversationId, page, 100));
        if (result.success && result.data != null && result.data.items != null) {
            List<ChatMessageEntity> cached = new ArrayList<>();
            for (ChatDtos.MessageDto m : result.data.items) {
                ChatMessageEntity e = new ChatMessageEntity();
                e.id = m.id; e.conversationId = m.conversationId; e.senderId = m.senderId;
                e.content = m.content; e.type = m.type; e.replyToId = m.replyToId;
                e.createdAt = m.createdAt; e.cachedAt = System.currentTimeMillis();
                cached.add(e);
            }
            io.execute(() -> dao.upsertAll(cached));
        }
        return result;
    }

    @Override public Result<ChatDtos.MessageDto> sendMessage(String conversationId, String content, String replyToId) {
        return ApiCall.execute(api.send(conversationId, new ChatDtos.SendMessageRequest(content, "text", replyToId)));
    }

    @Override public Result<ChatDtos.MessageDto> sendImage(String conversationId, String imageUrl, String replyToId) {
        ChatDtos.MediaDto media = new ChatDtos.MediaDto();
        media.url = imageUrl;
        media.mimeType = "image/jpeg";
        return ApiCall.execute(api.send(conversationId,
                new ChatDtos.SendMessageRequest("", "image", replyToId, media)));
    }

    @Override public Result<ChatDtos.MessageDto> sendAudio(String conversationId, String audioUrl, int durationSec, String replyToId) {
        ChatDtos.MediaDto media = new ChatDtos.MediaDto();
        media.url = audioUrl;
        media.mimeType = "audio/mp4";
        media.duration = Math.max(0, durationSec);
        return ApiCall.execute(api.send(conversationId,
                new ChatDtos.SendMessageRequest("", "audio", replyToId, media)));
    }

    @Override public Result<ChatDtos.MessageDto> sendGift(String conversationId, String giftName, String iconUrl, String replyToId) {
        ChatDtos.MediaDto media = null;
        if (iconUrl != null && !iconUrl.isEmpty()) {
            // Never store gift video/SVGA into DM bubbles — decode kills low-RAM phones.
            com.Dramizo.Series.util.CosmeticMedia.Kind kind =
                    com.Dramizo.Series.util.CosmeticMedia.kind(iconUrl);
            if (kind != com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                    && kind != com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA) {
                media = new ChatDtos.MediaDto();
                media.url = iconUrl;
                media.mimeType = "image/png";
                media.size = 0;
            }
        }
        String content = giftName != null ? giftName : "هدية";
        return ApiCall.execute(api.send(conversationId,
                new ChatDtos.SendMessageRequest(content, "gift", replyToId, media)));
    }
}
