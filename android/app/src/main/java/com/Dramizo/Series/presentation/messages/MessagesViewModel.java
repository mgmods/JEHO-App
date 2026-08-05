package com.Dramizo.Series.presentation.messages;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.realtime.RealtimeClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessagesViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<ChatDtos.ConversationDto>> conversations =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private long lastLoadAtMs = 0L;
    private static final long MIN_RELOAD_MS = 25_000L;
    private final RealtimeClient.UserListener presenceListener = new RealtimeClient.UserListener() {
        @Override
        public void onPresenceOnline(String userId, String username) {
            updatePresence(userId, true);
        }

        @Override
        public void onPresenceOffline(String userId) {
            updatePresence(userId, false);
        }
    };

    private final RealtimeClient.ChatListener chatListener = new RealtimeClient.ChatListener() {
        @Override
        public void onChatMessage(String conversationId, ChatDtos.MessageDto message) {
            // conversation:updated carries preview + unread
        }

        @Override
        public void onConversationUpdated(String conversationId, ChatDtos.MessageDto lastMessage, int unreadCount) {
            if (conversationId == null) return;
            List<ChatDtos.ConversationDto> current = conversations.getValue();
            if (current == null || current.isEmpty()) {
                load(true);
                return;
            }
            List<ChatDtos.ConversationDto> next = new ArrayList<>(current);
            ChatDtos.ConversationDto match = null;
            int idx = -1;
            for (int i = 0; i < next.size(); i++) {
                if (conversationId.equals(next.get(i).id)) {
                    match = next.get(i);
                    idx = i;
                    break;
                }
            }
            if (match == null) {
                load(true);
                return;
            }
            match.lastMessage = lastMessage;
            match.unreadCount = unreadCount;
            if (lastMessage != null && lastMessage.createdAt != null) {
                match.updatedAt = lastMessage.createdAt;
            }
            next.remove(idx);
            next.add(0, match);
            conversations.postValue(next);
        }

        @Override
        public void onMessageEdited(String conversationId, ChatDtos.MessageDto message) {
            if (conversationId == null || message == null || message.id == null) return;
            List<ChatDtos.ConversationDto> current = conversations.getValue();
            if (current == null) return;
            List<ChatDtos.ConversationDto> next = new ArrayList<>(current);
            for (ChatDtos.ConversationDto item : next) {
                if (!conversationId.equals(item.id) || item.lastMessage == null) continue;
                if (message.id.equals(item.lastMessage.id)) {
                    item.lastMessage = message;
                    conversations.postValue(next);
                    return;
                }
            }
        }

        @Override
        public void onMessageUnsent(String conversationId, String messageId) {
            // The server selects the previous visible message as the new preview.
            load(true);
        }

        @Override
        public void onTyping(String conversationId, String userId, boolean isTyping) {
        }

        @Override
        public void onConversationDeleted(String conversationId) {
            if (conversationId == null) return;
            List<ChatDtos.ConversationDto> current = conversations.getValue();
            if (current == null || current.isEmpty()) return;
            List<ChatDtos.ConversationDto> next = new ArrayList<>();
            boolean removed = false;
            for (ChatDtos.ConversationDto item : current) {
                if (conversationId.equals(item.id)) {
                    removed = true;
                    continue;
                }
                next.add(item);
            }
            if (removed) conversations.postValue(next);
        }
    };

    public MessagesViewModel(AppContainer c) {
        this.c = c;
        RealtimeClient.getInstance().addChatListener(chatListener);
        RealtimeClient.getInstance().addUserListener(presenceListener);
        String token = c.getSessionManager().getAccessToken();
        if (token != null) RealtimeClient.getInstance().connect(token);
    }

    public LiveData<List<ChatDtos.ConversationDto>> getConversations() { return conversations; }
    public LiveData<String> getError() { return error; }

    public void load() {
        load(false);
    }

    /** @param force true from pull-to-refresh / deleted / missing conversation */
    public void load(boolean force) {
        long now = System.currentTimeMillis();
        List<ChatDtos.ConversationDto> cached = conversations.getValue();
        if (!force && cached != null && !cached.isEmpty() && now - lastLoadAtMs < MIN_RELOAD_MS) {
            return;
        }
        lastLoadAtMs = now;
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.ConversationList> r = c.getConversationsUseCase.execute();
            if (r.success && r.data != null && r.data.items != null) {
                conversations.postValue(r.data.items);
            } else {
                error.postValue(r.error != null ? r.error
                        : c.getAppContext().getString(R.string.load_conversations_failed));
            }
        });
    }

    private synchronized void updatePresence(String userId, boolean online) {
        if (userId == null) return;
        List<ChatDtos.ConversationDto> current = conversations.getValue();
        if (current == null || current.isEmpty()) return;
        List<ChatDtos.ConversationDto> next = new ArrayList<>(current);
        boolean changed = false;
        for (ChatDtos.ConversationDto item : next) {
            if (item.peer == null || !userId.equals(item.peer.id)) continue;
            item.peer.isOnline = online;
            if (!online) item.peer.lastSeenAt = String.valueOf(System.currentTimeMillis());
            changed = true;
        }
        if (changed) conversations.postValue(next);
    }

    @Override
    protected void onCleared() {
        RealtimeClient.getInstance().removeChatListener(chatListener);
        RealtimeClient.getInstance().removeUserListener(presenceListener);
        super.onCleared();
    }
}
