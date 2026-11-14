package com.Dramizo.Series.domain.usecase.chat;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.ChatRepository;
public class GetMessagesUseCase {
    private final ChatRepository repo;
    public GetMessagesUseCase(ChatRepository repo) { this.repo = repo; }
    public Result<ChatDtos.MessageList> execute(String conversationId, int page) {
        return repo.getMessages(conversationId, page);
    }
}
