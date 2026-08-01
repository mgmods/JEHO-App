package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public static class ConversationDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("title") public String title;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("lastMessage") public MessageDto lastMessage;
        @SerializedName("lastMessageAt") public String lastMessageAt;
        @SerializedName("unreadCount") public int unreadCount;
        @SerializedName("updatedAt") public String updatedAt;
        @SerializedName("peer") public AuthDtos.UserDto peer;
    }

    public static class MessageDto {
        @SerializedName("id") public String id;
        @SerializedName("conversationId") public String conversationId;
        @SerializedName("senderId") public String senderId;
        @SerializedName("type") public String type;
        @SerializedName("content") public String content;
        @SerializedName("media") public MediaDto media;
        @SerializedName("replyToId") public String replyToId;
        @SerializedName("replyTo") public MessageDto replyTo;
        @SerializedName("isEdited") public boolean isEdited;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("sender") public AuthDtos.UserDto sender;
        /** Client-only: pending until server ack. */
        public transient boolean localPending;
    }

    public static class MediaDto {
        @SerializedName("url") public String url;
        @SerializedName("mimeType") public String mimeType;
        @SerializedName("size") public long size;
        @SerializedName("width") public int width;
        @SerializedName("height") public int height;
        @SerializedName("duration") public int duration;
    }

    public static class SendMessageRequest {
        @SerializedName("content") public String content;
        @SerializedName("type") public String type;
        @SerializedName("replyToId") public String replyToId;
        @SerializedName("media") public MediaDto media;
        public SendMessageRequest(String content, String type, String replyToId) {
            this.content = content; this.type = type; this.replyToId = replyToId;
        }
        public SendMessageRequest(String content, String type, String replyToId, MediaDto media) {
            this.content = content; this.type = type; this.replyToId = replyToId; this.media = media;
        }
    }

    public static class ConversationList {
        @SerializedName("items") public List<ConversationDto> items;
    }

    public static class MessageList {
        @SerializedName("items") public List<MessageDto> items;
        @SerializedName("meta") public PaginationMeta meta;
        @SerializedName("peerLastReadAt") public String peerLastReadAt;
    }

    public static class PaginationMeta {
        @SerializedName("total") public int total;
        @SerializedName("page") public int page;
        @SerializedName("limit") public int limit;
        @SerializedName("totalPages") public int totalPages;
    }
}
