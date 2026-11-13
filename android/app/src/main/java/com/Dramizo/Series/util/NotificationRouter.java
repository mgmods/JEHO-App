package com.Dramizo.Series.util;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.Dramizo.Series.presentation.agency.AgencyManageActivity;
import com.Dramizo.Series.presentation.agency.AgencyActivity;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.contests.ContestsActivity;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.notifications.OfficialNewsActivity;
import com.Dramizo.Series.presentation.notifications.NotificationsActivity;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;

import java.util.Map;

/** One routing contract shared by push, foreground banners, and the inbox. */
public final class NotificationRouter {
    private NotificationRouter() {}

    public static Intent build(Context context, String type, Map<String, ?> data) {
        String t = type != null ? type.toLowerCase() : "system";
        String nested = first(data, "type");
        String notificationKind = first(data, "notificationKind", "eventType");
        if (nested != null) {
            String n = nested.toLowerCase();
            // Push/inbox may wrap room-open as LIVE with data.type=room
            if ("room".equals(n) || "voice".equals(n) || "party".equals(n)) t = "room";
            else if ("chat".equals(n) || "message".equals(n) || "new_message".equals(n)
                    || "chat_message".equals(n) || "direct_message".equals(n)
                    || "message_received".equals(n)) t = "chat";
        }
        String conversationId = first(data, "conversation_id", "conversationId");
        String roomId = first(data, "room_id", "roomId");
        String userId = first(data, "user_id", "userId", "senderId", "fromUserId", "actorUserId");
        String action = first(data, "action", "kind", "requestType", "notificationKind", "eventType");
        String normalizedAction = action == null ? "" : action.toLowerCase(java.util.Locale.ROOT);
        String normalizedType = t.toLowerCase(java.util.Locale.ROOT);
        String normalizedNested = nested == null ? "" : nested.toLowerCase(java.util.Locale.ROOT);
        boolean friendRequest = isOneOf(normalizedType, "friend_request", "friend_request_received",
                "new_friend_request", "friendship_request", "friendship_request_received")
                || isOneOf(normalizedNested, "friend_request", "friend_request_received",
                "new_friend_request", "friendship_request", "friendship_request_received")
                || isOneOf(normalizedAction, "friend_request", "friend_request_received",
                "new_friend_request", "friendship_request", "request_friend");
        if (friendRequest) {
            Intent i = new Intent(context, com.Dramizo.Series.presentation.friends.FriendsActivity.class);
            i.putExtra(com.Dramizo.Series.presentation.friends.FriendsActivity.EXTRA_TAB,
                    com.Dramizo.Series.presentation.friends.FriendsActivity.TAB_REQ_FRIEND);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return i;
        }
        boolean newFollower = isOneOf(normalizedType, "follow", "new_follower", "followed_you",
                "user_followed", "new_follow")
                || isOneOf(normalizedNested, "follow", "new_follower", "followed_you",
                "user_followed", "new_follow")
                || isOneOf(normalizedAction, "follow", "new_follower", "followed_you", "user_followed");
        if (newFollower) {
            Intent i = new Intent(context, com.Dramizo.Series.presentation.friends.FriendsActivity.class);
            i.putExtra(com.Dramizo.Series.presentation.friends.FriendsActivity.EXTRA_TAB,
                    com.Dramizo.Series.presentation.friends.FriendsActivity.TAB_FANS);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return i;
        }

        if ("cp_relationship_request".equalsIgnoreCase(nested)
                || "cp_relationship_request".equalsIgnoreCase(notificationKind)) {
            Intent i = new Intent(context, NotificationsActivity.class);
            put(i, NotificationsActivity.EXTRA_CP_REQUEST_ID, first(data, "requestId"));
            put(i, NotificationsActivity.EXTRA_CP_REQUEST_TITLE, first(data, "title"));
            put(i, NotificationsActivity.EXTRA_CP_REQUEST_BODY, first(data, "body"));
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return i;
        }

        // Admin-originated notifications (including legacy system-type broadcasts)
        // must open the dedicated Admin Messages inbox, not the generic notification list.
        if ("true".equalsIgnoreCase(first(data, "adminMessage"))
                || "admin".equalsIgnoreCase(first(data, "source"))) {
            Intent i = new Intent(context,
                    com.Dramizo.Series.presentation.notifications.AdminMessagesActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return i;
        }

        if (isChatType(t) && !TextUtils.isEmpty(conversationId)) {
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
            put(i, VoiceRoomActivity.EXTRA_ROOM_DISPLAY_ID,
                    first(data, "display_room_id", "displayRoomId", "room_display_id", "roomDisplayId"));
            String roomKind = first(data, "room_type", "roomType", "room_kind", "roomKind");
            String agencyFlag = first(data, "is_agency", "isAgency", "agencyRoom");
            boolean isAgency = "true".equalsIgnoreCase(agencyFlag)
                    || "agency".equalsIgnoreCase(roomKind);
            i.putExtra(VoiceRoomActivity.EXTRA_ROOM_IS_AGENCY, isAgency);
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
        if ("agency".equals(t) && ("agency_club_approved".equalsIgnoreCase(action != null ? action : "")
                || "agency_club_rejected".equalsIgnoreCase(action != null ? action : ""))) {
            Intent i = new Intent(context, AgencyActivity.class);
            put(i, "open_agency_club_details_id", first(data, "agency_id", "agencyId"));
            return i;
        }
        if ("agency".equals(t) && "join_request".equalsIgnoreCase(action != null ? action : "")) {
            Intent i = new Intent(context, AgencyManageActivity.class);
            put(i, AgencyManageActivity.EXTRA_AGENCY_ID, first(data, "agency_id", "agencyId"));
            i.putExtra(AgencyManageActivity.EXTRA_OPEN_PENDING_REQUESTS, true);
            return i;
        }
        if ("system".equals(t) || "agency".equals(t)
                || "wallet".equals(t) || "withdraw".equals(t) || "vip".equals(t)) {
            return new Intent(context, OfficialNewsActivity.class);
        }
        return new Intent(context, MainActivity.class);
    }

    private static boolean isOneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static boolean isChatType(String type) {
        if (type == null) return false;
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        return "chat".equals(normalized)
                || "message".equals(normalized)
                || "new_message".equals(normalized)
                || "chat_message".equals(normalized)
                || "direct_message".equals(normalized)
                || "message_received".equals(normalized);
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
