package com.Dramizo.Series.presentation.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.realtime.RealtimeClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class ChatViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<ChatDtos.MessageDto>> messages = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> typing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingOlder = new MutableLiveData<>(false);
    private final MutableLiveData<ChatDtos.MessageDto> replyTarget = new MutableLiveData<>(null);
    private final MutableLiveData<String> peerLastReadAt = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> conversationDeleted = new MutableLiveData<>(false);
    private String replyToId;
    private String conversationId;
    private int currentPage = 1;
    private int totalPages = 1;
    private boolean loadingMore;
    private final RealtimeClient.ChatListener chatListener = new RealtimeClient.ChatListener() {
        @Override
        public void onChatMessage(String cid, ChatDtos.MessageDto message) {
            if (conversationId == null || cid == null || !conversationId.equals(cid) || message == null) return;
            appendMessage(message);
        }

        @Override
        public void onConversationUpdated(String cid, ChatDtos.MessageDto lastMessage, int unreadCount) {
            // list handles previews
        }

        @Override
        public void onMessageEdited(String cid, ChatDtos.MessageDto message) {
            if (conversationId == null || cid == null || !conversationId.equals(cid) || message == null) return;
            replaceMessage(message);
        }

        @Override
        public void onMessageUnsent(String cid, String messageId) {
            if (conversationId == null || cid == null || !conversationId.equals(cid) || messageId == null) return;
            removeMessage(messageId);
        }

        @Override
        public void onChatRead(String cid, String readerId, String lastReadAt) {
            if (conversationId == null || cid == null || !conversationId.equals(cid)) return;
            String me = c.getSessionManager().getUserId();
            // Peer read our messages.
            if (readerId != null && me != null && !readerId.equals(me) && lastReadAt != null) {
                peerLastReadAt.postValue(lastReadAt);
            }
        }

        @Override
        public void onTyping(String cid, String userId, boolean isTyping) {
            if (conversationId == null || cid == null || !conversationId.equals(cid)) return;
            String me = c.getSessionManager().getUserId();
            if (userId != null && userId.equals(me)) return;
            typing.postValue(isTyping);
        }

        @Override
        public void onConversationDeleted(String cid) {
            if (conversationId == null || cid == null || !conversationId.equals(cid)) return;
            conversationDeleted.postValue(true);
        }

        @Override
        public void onConnected() {
            if (conversationId != null) RealtimeClient.getInstance().joinConversation(conversationId);
        }
    };

    public ChatViewModel(AppContainer c) {
        this.c = c;
        RealtimeClient.getInstance().addChatListener(chatListener);
    }

    public LiveData<List<ChatDtos.MessageDto>> getMessages() { return messages; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getTyping() { return typing; }
    public LiveData<Boolean> getLoadingOlder() { return loadingOlder; }
    public LiveData<ChatDtos.MessageDto> getReplyTarget() { return replyTarget; }
    public LiveData<String> getPeerLastReadAt() { return peerLastReadAt; }
    public LiveData<Boolean> getConversationDeleted() { return conversationDeleted; }

    public void setReplyToId(String id) { replyToId = id; }

    public void setReplyTarget(ChatDtos.MessageDto msg) {
        if (msg == null || msg.id == null) {
            clearReply();
            return;
        }
        replyToId = msg.id;
        replyTarget.setValue(msg);
    }

    public void clearReply() {
        replyToId = null;
        replyTarget.setValue(null);
    }

    public void attach(String conversationId) {
        this.conversationId = conversationId;
        String token = c.getSessionManager().getAccessToken();
        RealtimeClient rt = RealtimeClient.getInstance();
        rt.connect(token);
        rt.joinConversation(conversationId);
    }

    public void load(String conversationId) {
        boolean switched = this.conversationId == null || !this.conversationId.equals(conversationId);
        attach(conversationId);
        currentPage = 1;
        totalPages = 1;
        c.getIoExecutor().execute(() -> {
            // Paint Room cache immediately so conversation opens without blank wait.
            if (switched) {
                try {
                    List<ChatDtos.MessageDto> cached =
                            c.getChatRepository().getCachedMessages(conversationId);
                    if (cached != null && !cached.isEmpty()) {
                        List<ChatDtos.MessageDto> painted = new ArrayList<>();
                        for (ChatDtos.MessageDto m : cached) {
                            if (m == null || m.id == null) continue;
                            if (isDismissed(m.id)) continue;
                            normalizeMine(m);
                            m.localPending = false;
                            painted.add(m);
                        }
                        if (!painted.isEmpty()) messages.postValue(painted);
                    } else {
                        messages.postValue(Collections.emptyList());
                    }
                } catch (Exception ignored) {
                    messages.postValue(Collections.emptyList());
                }
            }
            Result<ChatDtos.MessageList> r = c.getMessagesUseCase.execute(conversationId, 1);
            if (r.success && r.data != null && r.data.items != null) {
                if (r.data.meta != null) {
                    currentPage = r.data.meta.page > 0 ? r.data.meta.page : 1;
                    totalPages = r.data.meta.totalPages > 0 ? r.data.meta.totalPages : 1;
                }
                if (r.data.peerLastReadAt != null) {
                    peerLastReadAt.postValue(r.data.peerLastReadAt);
                }
                if (switched) {
                    List<ChatDtos.MessageDto> items = new ArrayList<>();
                    for (ChatDtos.MessageDto m : r.data.items) {
                        if (m == null || m.id == null) continue;
                        if (isDismissed(m.id)) continue;
                        normalizeMine(m);
                        m.localPending = false;
                        items.add(m);
                    }
                    messages.postValue(items);
                } else {
                    mergeLoadedMessages(r.data.items);
                }
            } else if (switched) {
                List<ChatDtos.MessageDto> cur = messages.getValue();
                if (cur == null || cur.isEmpty()) error.postValue(r.error);
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void loadOlder() {
        if (conversationId == null || loadingMore || currentPage >= totalPages) return;
        loadingMore = true;
        loadingOlder.postValue(true);
        int nextPage = currentPage + 1;
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageList> r = c.getMessagesUseCase.execute(conversationId, nextPage);
            loadingMore = false;
            loadingOlder.postValue(false);
            if (r.success && r.data != null && r.data.items != null && !r.data.items.isEmpty()) {
                if (r.data.meta != null) {
                    currentPage = r.data.meta.page > 0 ? r.data.meta.page : nextPage;
                    totalPages = r.data.meta.totalPages > 0 ? r.data.meta.totalPages : totalPages;
                } else {
                    currentPage = nextPage;
                }
                prependOlderMessages(r.data.items);
            } else if (!r.success) {
                error.postValue(r.error);
            }
        });
    }

    public void send(String conversationId, String content) {
        typing.postValue(false);
        RealtimeClient.getInstance().emitTyping(conversationId, false);
        final String reply = replyToId;
        clearReply();
        final String localId = "local:" + System.currentTimeMillis();
        ChatDtos.MessageDto pending = newOptimistic("text", localId, conversationId, reply);
        pending.content = content;
        appendMessage(pending);
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageDto> r = c.sendMessageUseCase.execute(conversationId, content, reply);
            if (r.success && r.data != null) {
                replaceLocalWithServer(localId, r.data);
            } else {
                // Keep bubble visible with error — do not vanish like a glitch.
                error.postValue(r.error != null ? r.error : "تعذر إرسال الرسالة");
            }
        });
    }

    public void sendImage(String conversationId, String imageUrl) {
        typing.postValue(false);
        RealtimeClient.getInstance().emitTyping(conversationId, false);
        final String reply = replyToId;
        clearReply();
        final String localId = "local:" + System.currentTimeMillis();
        ChatDtos.MessageDto pending = newOptimistic("image", localId, conversationId, reply);
        pending.media = new ChatDtos.MediaDto();
        pending.media.url = imageUrl;
        appendMessage(pending);
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageDto> r =
                    c.sendMessageUseCase.executeImage(conversationId, imageUrl, reply);
            if (r.success && r.data != null) replaceLocalWithServer(localId, r.data);
            else error.postValue(r.error != null ? r.error : "تعذر إرسال الصورة");
        });
    }

    public void sendAudio(String conversationId, String audioUrl, int durationSec) {
        typing.postValue(false);
        RealtimeClient.getInstance().emitTyping(conversationId, false);
        final String reply = replyToId;
        clearReply();
        final String localId = "local:" + System.currentTimeMillis();
        ChatDtos.MessageDto pending = newOptimistic("audio", localId, conversationId, reply);
        pending.media = new ChatDtos.MediaDto();
        pending.media.url = audioUrl;
        pending.media.duration = durationSec;
        appendMessage(pending);
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageDto> r =
                    c.sendMessageUseCase.executeAudio(conversationId, audioUrl, durationSec, reply);
            if (r.success && r.data != null) replaceLocalWithServer(localId, r.data);
            else error.postValue(r.error != null ? r.error : "تعذر إرسال الصوت");
        });
    }

    public void sendGiftMessage(String conversationId, String giftName, String iconUrl) {
        typing.postValue(false);
        RealtimeClient.getInstance().emitTyping(conversationId, false);
        final String reply = replyToId;
        clearReply();
        final String localId = "local:" + System.currentTimeMillis();
        ChatDtos.MessageDto pending = newOptimistic("gift", localId, conversationId, reply);
        pending.content = giftName != null && !giftName.isEmpty() ? giftName : "هدية";
        String safeIcon = iconUrl;
        if (safeIcon != null) {
            com.Dramizo.Series.util.CosmeticMedia.Kind k =
                    com.Dramizo.Series.util.CosmeticMedia.kind(safeIcon);
            if (k == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                    || k == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA) {
                safeIcon = null;
            }
        }
        if (safeIcon != null && !safeIcon.isEmpty()) {
            pending.media = new ChatDtos.MediaDto();
            pending.media.url = safeIcon;
        }
        appendMessage(pending);
        final String iconFinal = safeIcon;
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageDto> r =
                    c.sendMessageUseCase.executeGift(conversationId, pending.content, iconFinal, reply);
            if (r.success && r.data != null) replaceLocalWithServer(localId, r.data);
            else error.postValue(r.error != null ? r.error : "تعذر إرسال الهدية");
        });
    }

    private ChatDtos.MessageDto newOptimistic(String type, String localId, String conversationId, String reply) {
        ChatDtos.MessageDto pending = new ChatDtos.MessageDto();
        pending.id = localId;
        pending.conversationId = conversationId;
        pending.senderId = c.getSessionManager().getUserId();
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        if ((pending.senderId == null || pending.senderId.isEmpty()) && me != null) {
            pending.senderId = me.id;
        }
        pending.sender = me;
        pending.type = type;
        pending.replyToId = reply;
        pending.createdAt = java.time.Instant.now().toString();
        pending.localPending = true;
        return pending;
    }

    public void setTyping(boolean isTyping) {
        if (conversationId != null) RealtimeClient.getInstance().emitTyping(conversationId, isTyping);
    }

    public void editMessage(String messageId, String newContent) {
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("content", newContent);
            Result<ChatDtos.MessageDto> r = ApiCall.execute(c.getChatApi().edit(messageId, body));
            if (r.success && r.data != null) {
                // Ensure senderId stays so "mine" detection does not flip after edit.
                if (r.data.senderId == null || r.data.senderId.isEmpty()) {
                    r.data.senderId = c.getSessionManager().getUserId();
                }
                replaceMessage(r.data);
            } else {
                error.postValue(r.error != null ? r.error : "تعذر تعديل الرسالة");
            }
        });
    }

    public void unsendMessage(String messageId) {
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.MessageDto> r = ApiCall.execute(c.getChatApi().unsend(messageId));
            if (r.success) removeMessage(messageId);
            else error.postValue(r.error != null ? r.error : "تعذر حذف الرسالة");
        });
    }

    private synchronized void replaceLocalWithServer(String localId, ChatDtos.MessageDto server) {
        if (server == null) return;
        normalizeMine(server);
        server.localPending = false;
        List<ChatDtos.MessageDto> current = messages.getValue();
        List<ChatDtos.MessageDto> next = new ArrayList<>(current != null ? current : Collections.emptyList());

        // Drop the optimistic row (if still present).
        if (localId != null) {
            for (int i = next.size() - 1; i >= 0; i--) {
                if (localId.equals(next.get(i).id)) next.remove(i);
            }
        }

        // Upsert by real server id — never delete-then-lose the bubble.
        if (server.id != null) {
            for (int i = 0; i < next.size(); i++) {
                if (server.id.equals(next.get(i).id)) {
                    next.set(i, server);
                    messages.postValue(next);
                    return;
                }
            }
        }
        next.add(server);
        messages.postValue(next);
    }

    private synchronized void replaceMessage(ChatDtos.MessageDto updated) {
        if (updated == null || updated.id == null) return;
        normalizeMine(updated);
        updated.localPending = false;
        List<ChatDtos.MessageDto> current = messages.getValue();
        List<ChatDtos.MessageDto> next = new ArrayList<>(current != null ? current : Collections.emptyList());
        for (int i = 0; i < next.size(); i++) {
            if (updated.id.equals(next.get(i).id)) {
                next.set(i, updated);
                messages.postValue(next);
                return;
            }
        }
    }

    private synchronized void removeMessage(String messageId) {
        if (messageId == null) return;
        List<ChatDtos.MessageDto> current = messages.getValue();
        List<ChatDtos.MessageDto> next = new ArrayList<>(current != null ? current : Collections.emptyList());
        for (int i = 0; i < next.size(); i++) {
            if (messageId.equals(next.get(i).id)) {
                next.remove(i);
                messages.postValue(next);
                return;
            }
        }
    }

    /**
     * Hide a card message for this device only (family invite reject / after accept).
     * Survives reloads so the invite does not reappear in this conversation.
     */
    public void dismissMessageLocally(String messageId) {
        if (messageId == null || messageId.isEmpty()) return;
        rememberDismissed(messageId);
        removeMessage(messageId);
    }

    private void rememberDismissed(String messageId) {
        if (conversationId == null || conversationId.isEmpty()) return;
        try {
            android.content.SharedPreferences prefs = c.getAppContext()
                    .getSharedPreferences("chat_dismissed_msgs", android.content.Context.MODE_PRIVATE);
            String key = "d:" + conversationId;
            java.util.Set<String> set = new java.util.HashSet<>(
                    prefs.getStringSet(key, java.util.Collections.emptySet()));
            set.add(messageId);
            prefs.edit().putStringSet(key, set).apply();
        } catch (Exception ignored) {
        }
    }

    private boolean isDismissed(String messageId) {
        if (messageId == null || conversationId == null) return false;
        try {
            android.content.SharedPreferences prefs = c.getAppContext()
                    .getSharedPreferences("chat_dismissed_msgs", android.content.Context.MODE_PRIVATE);
            java.util.Set<String> set = prefs.getStringSet("d:" + conversationId, null);
            return set != null && set.contains(messageId);
        } catch (Exception ignored) {
            return false;
        }
    }

    @androidx.annotation.Nullable
    private List<ChatDtos.MessageDto> filterDismissed(
            @androidx.annotation.Nullable List<ChatDtos.MessageDto> list) {
        if (list == null || list.isEmpty()) return list;
        List<ChatDtos.MessageDto> out = new ArrayList<>(list.size());
        for (ChatDtos.MessageDto m : list) {
            if (m != null && m.id != null && isDismissed(m.id)) continue;
            out.add(m);
        }
        return out;
    }

    private synchronized void mergeLoadedMessages(List<ChatDtos.MessageDto> loaded) {
        List<ChatDtos.MessageDto> current = messages.getValue();
        if (loaded == null || loaded.isEmpty()) {
            if (current == null || current.isEmpty()) {
                messages.postValue(loaded != null ? loaded : Collections.emptyList());
            }
            return;
        }
        LinkedHashMap<String, ChatDtos.MessageDto> merged = new LinkedHashMap<>();
        for (ChatDtos.MessageDto m : loaded) {
            if (m == null || m.id == null) continue;
            if (isDismissed(m.id)) continue;
            normalizeMine(m);
            m.localPending = false;
            merged.put(m.id, m);
        }
        // Keep in-flight optimistic rows that are not on the server page yet.
        if (current != null) {
            for (ChatDtos.MessageDto m : current) {
                if (m == null || m.id == null) continue;
                if (isDismissed(m.id)) continue;
                if (m.localPending && m.id.startsWith("local:") && !merged.containsKey(m.id)) {
                    merged.put(m.id, m);
                }
            }
        }
        messages.postValue(new ArrayList<>(merged.values()));
    }

    private synchronized void prependOlderMessages(List<ChatDtos.MessageDto> older) {
        LinkedHashMap<String, ChatDtos.MessageDto> merged = new LinkedHashMap<>();
        if (older != null) {
            for (ChatDtos.MessageDto m : older) {
                if (m == null || m.id == null) continue;
                if (isDismissed(m.id)) continue;
                normalizeMine(m);
                m.localPending = false;
                merged.put(m.id, m);
            }
        }
        List<ChatDtos.MessageDto> current = messages.getValue();
        if (current != null) {
            for (ChatDtos.MessageDto m : current) {
                if (m == null || m.id == null) continue;
                if (isDismissed(m.id)) continue;
                if (!merged.containsKey(m.id)) merged.put(m.id, m);
            }
        }
        messages.postValue(new ArrayList<>(merged.values()));
    }

    private synchronized void appendMessage(ChatDtos.MessageDto message) {
        if (message == null) return;
        if (message.id != null && isDismissed(message.id)) return;
        normalizeMine(message);
        List<ChatDtos.MessageDto> current = messages.getValue();
        List<ChatDtos.MessageDto> next = new ArrayList<>(current != null ? current : Collections.emptyList());

        // Same id → update in place.
        if (message.id != null) {
            for (int i = 0; i < next.size(); i++) {
                if (message.id.equals(next.get(i).id)) {
                    next.set(i, message);
                    messages.postValue(next);
                    return;
                }
            }
        }

        // WhatsApp-style: socket echo of my send replaces the optimistic local bubble.
        if (isFromMe(message)) {
            message.localPending = false;
            int pendingIdx = findMatchingPending(next, message);
            if (pendingIdx >= 0) {
                next.set(pendingIdx, message);
                messages.postValue(next);
                return;
            }
        }

        next.add(message);
        messages.postValue(next);
    }

    private int findMatchingPending(List<ChatDtos.MessageDto> list, ChatDtos.MessageDto server) {
        if (list == null || server == null) return -1;
        String sType = server.type != null ? server.type.toLowerCase() : "text";
        String sContent = server.content != null ? server.content : "";
        String sMedia = server.media != null ? server.media.url : null;
        for (int i = list.size() - 1; i >= 0; i--) {
            ChatDtos.MessageDto m = list.get(i);
            if (m == null || !m.localPending) continue;
            if (m.id == null || !m.id.startsWith("local:")) continue;
            String t = m.type != null ? m.type.toLowerCase() : "text";
            if (!t.equals(sType)) continue;
            if ("text".equals(t) || "gift".equals(t)) {
                String c = m.content != null ? m.content : "";
                if (c.equals(sContent)) return i;
            } else {
                String media = m.media != null ? m.media.url : null;
                if (media != null && media.equals(sMedia)) return i;
                if ((sContent.length() > 0) && sContent.equals(m.content != null ? m.content : "")) return i;
            }
        }
        return -1;
    }

    private boolean isFromMe(ChatDtos.MessageDto msg) {
        if (msg == null) return false;
        if (msg.localPending) return true;
        String me = c.getSessionManager().getUserId();
        if (me == null || me.isEmpty()) {
            AuthDtos.UserDto u = c.getSessionManager().getUser();
            if (u != null) me = u.id;
        }
        if (me == null || me.isEmpty()) return false;
        if (me.equals(msg.senderId)) return true;
        return msg.sender != null && me.equals(msg.sender.id);
    }

    /** Keep senderId stable so bubbles never flip sides after socket/API merge. */
    private void normalizeMine(ChatDtos.MessageDto msg) {
        if (msg == null) return;
        String me = c.getSessionManager().getUserId();
        AuthDtos.UserDto user = c.getSessionManager().getUser();
        if ((me == null || me.isEmpty()) && user != null) me = user.id;
        if (me == null || me.isEmpty()) return;

        boolean mine = me.equals(msg.senderId)
                || (msg.sender != null && me.equals(msg.sender.id))
                || msg.localPending;
        if (!mine) return;

        if (msg.senderId == null || msg.senderId.isEmpty()) {
            msg.senderId = me;
        }
        if (msg.sender == null && user != null) {
            msg.sender = user;
        } else if (msg.sender != null && (msg.sender.id == null || msg.sender.id.isEmpty())) {
            msg.sender.id = me;
        }
    }

    @Override
    protected void onCleared() {
        if (conversationId != null) RealtimeClient.getInstance().leaveConversation(conversationId);
        RealtimeClient.getInstance().removeChatListener(chatListener);
        super.onCleared();
    }
}
