package com.Dramizo.Series.util;

import androidx.annotation.Nullable;

/** Tracks the open DM so chat push/banners are muted for that conversation only. */
public final class ActiveChatTracker {
    private static volatile String activeConversationId;

    private ActiveChatTracker() {}

    public static void set(@Nullable String conversationId) {
        activeConversationId = conversationId;
    }

    public static void clear(@Nullable String conversationId) {
        if (conversationId == null) {
            activeConversationId = null;
            return;
        }
        if (conversationId.equals(activeConversationId)) {
            activeConversationId = null;
        }
    }

    @Nullable
    public static String get() {
        return activeConversationId;
    }

    public static boolean isViewing(@Nullable String conversationId) {
        return conversationId != null
                && !conversationId.isEmpty()
                && conversationId.equals(activeConversationId);
    }

    public static boolean shouldMute(@Nullable String type, @Nullable String conversationId) {
        if (type == null) return false;
        String t = type.trim().toLowerCase();
        if (!"chat".equals(t) && !"message".equals(t)) return false;
        return isViewing(conversationId);
    }

    /** True for chat/message notification types. */
    public static boolean isChatType(@Nullable String type) {
        if (type == null) return false;
        String t = type.trim().toLowerCase();
        return "chat".equals(t) || "message".equals(t);
    }
}
