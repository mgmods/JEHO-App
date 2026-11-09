package com.Dramizo.Series.domain.usecase.chat;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.ChatRepository;
public class GetConversationsUseCase {
    private final ChatRepository repo;
    public GetConversationsUseCase(ChatRepository repo) { this.repo = repo; }
    public Result<ChatDtos.ConversationList> execute() { return repo.getConversations(); }
}
