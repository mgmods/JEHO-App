package com.Dramizo.Series.domain.usecase.chat;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.ChatRepository;
public class SendMessageUseCase {
    private final ChatRepository repo;
    public SendMessageUseCase(ChatRepository repo) { this.repo = repo; }
    public Result<ChatDtos.MessageDto> execute(String conversationId, String content, String replyToId) {
        return repo.sendMessage(conversationId, content, replyToId);
    }

    public Result<ChatDtos.MessageDto> executeImage(String conversationId, String imageUrl, String replyToId) {
        return repo.sendImage(conversationId, imageUrl, replyToId);
    }

    public Result<ChatDtos.MessageDto> executeAudio(String conversationId, String audioUrl, int durationSec, String replyToId) {
        return repo.sendAudio(conversationId, audioUrl, durationSec, replyToId);
    }

    public Result<ChatDtos.MessageDto> executeGift(String conversationId, String giftName, String iconUrl, String replyToId) {
        return repo.sendGift(conversationId, giftName, iconUrl, replyToId);
    }
}
