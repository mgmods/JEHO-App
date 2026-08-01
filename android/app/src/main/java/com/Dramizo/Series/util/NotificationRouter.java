package com.Dramizo.Series.util;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.Dramizo.Series.presentation.agency.AgencyManageActivity;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.contests.ContestsActivity;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.notifications.OfficialNewsActivity;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;

import java.util.Map;

/** One routing contract shared by push, foreground banners, and the inbox. */
public final class NotificationRouter {
    private NotificationRouter() {}

    public static Intent build(Context context, String type, Map<String, ?> data) {
        String t = type != null ? type.toLowerCase() : "system";
        String nested = first(data, "type");
        if (nested != null) {
            String n = nested.toLowerCase();
            // Push/inbox may wrap room-open as LIVE with data.type=room
            if ("room".equals(n) || "voice".equals(n) || "party".equals(n)) t = "room";
            else if ("chat".equals(n) || "message".equals(n)) t = "chat";
        }
        String conversationId = first(data, "conversation_id", "conversationId");
        String roomId = first(data, "room_id", "roomId");
        String userId = first(data, "user_id", "userId", "senderId", "fromUserId", "actorUserId");

        if (("chat".equals(t) || "message".equals(t)) && !TextUtils.isEmpty(conversationId)) {
            Intent i = new Intent(context, ChatConversationActivity.class);
            i.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversationId);
            put(i, ChatConversationActivity.EXTRA_PEER_ID,
                    first(data, "peer_id", "peerId", "senderId", "user_id", "userId"));
            put(i, ChatConversationActivity.EXTRA_TITLE,
                    first(data, "senderName", "peer_name", "peerName", "name", "title"));
            put(i, ChatConversationActivity.EXTRA_AVATAR,
                    first(data, "avatarUrl", "senderAvatarUrl", "avatar", "imageUrl"));
            put(i, ChatConversationActivity.EXTRA_HOST_BADGE,
                    first(data, "hostBadgeUrl", "host_badge"));
            return i;
        }
        if (("room".equals(t) || "voice".equals(t) || "party".equals(t)
                || "live".equals(t) || "stream".equals(t))
                && !TextUtils.isEmpty(roomId)) {
            Intent i = new Intent(context, VoiceRoomActivity.class);
            i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
            return i;
        }
        if (("follow".equals(t) || "gift".equals(t) || "profile".equals(t)
                || "friend".equals(t) || "relation".equals(t) || "guardian".equals(t))
                && !TextUtils.isEmpty(userId)) {
            Intent i = new Intent(context, ProfileActivity.class);
            i.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
            return i;
        }
        if ("contest".equals(t) || "contests".equals(t)) {
            Intent i = new Intent(context, ContestsActivity.class);
            put(i, ContestsActivity.EXTRA_ROOM_ID, roomId);
            put(i, ContestsActivity.EXTRA_AGENCY_ID, first(data, "agency_id", "agencyId"));
            return i;
        }
        String action = first(data, "action");
        if ("agency".equals(t) && "join_request".equalsIgnoreCase(action != null ? action : "")) {
            Intent i = new Intent(context, AgencyManageActivity.class);
            put(i, AgencyManageActivity.EXTRA_AGENCY_ID, first(data, "agency_id", "agencyId"));
            return i;
        }
        if ("system".equals(t) || "agency".equals(t)
                || "wallet".equals(t) || "withdraw".equals(t) || "vip".equals(t)) {
            return new Intent(context, OfficialNewsActivity.class);
        }
        return new Intent(context, MainActivity.class);
    }

    private static void put(Intent intent, String key, String value) {
        if (!TextUtils.isEmpty(value)) intent.putExtra(key, value);
    }

    private static String first(Map<String, ?> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object raw = data.get(key);
            if (raw == null) continue;
            String value = String.valueOf(raw).trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return null;
    }
}
