package com.Dramizo.Series.presentation.chat;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.databinding.ItemChatPreviewBinding;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.RoomShareCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Clean conversation list (inbox rows).
 * Layout: avatar · name · last message · time · unread badge.
 */
public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.VH> {
    private static final Object PAYLOAD_META = "meta";

    public interface Listener {
        void onClick(ChatDtos.ConversationDto item);
    }

    private final List<ChatDtos.ConversationDto> items = new ArrayList<>();
    private final Listener listener;

    public ChatPreviewAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ChatDtos.ConversationDto> data) {
        if (sameList(items, data)) {
            List<ChatDtos.ConversationDto> next = data != null ? data : Collections.emptyList();
            for (int i = 0; i < items.size() && i < next.size(); i++) {
                ChatDtos.ConversationDto a = items.get(i);
                ChatDtos.ConversationDto b = next.get(i);
                if (samePreview(a, b) && sameAvatar(a, b)) continue;
                items.set(i, b);
                if (!sameAvatar(a, b)) {
                    notifyItemChanged(i);
                } else {
                    notifyItemChanged(i, PAYLOAD_META);
                }
            }
            return;
        }
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    private static boolean sameList(
            List<ChatDtos.ConversationDto> a, List<ChatDtos.ConversationDto> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            String idA = a.get(i) != null ? a.get(i).id : null;
            String idB = b.get(i) != null ? b.get(i).id : null;
            if (idA == null ? idB != null : !idA.equals(idB)) return false;
        }
        return true;
    }

    private static boolean samePreview(
            ChatDtos.ConversationDto a, ChatDtos.ConversationDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.unreadCount != b.unreadCount) return false;
        String ca = a.lastMessage != null ? a.lastMessage.content : null;
        String cb = b.lastMessage != null ? b.lastMessage.content : null;
        if (ca == null ? cb != null : !ca.equals(cb)) return false;
        String ta = a.lastMessage != null ? a.lastMessage.createdAt : a.updatedAt;
        String tb = b.lastMessage != null ? b.lastMessage.createdAt : b.updatedAt;
        if (ta == null ? tb != null : !ta.equals(tb)) return false;
        Boolean oa = a.peer != null ? a.peer.isOnline : null;
        Boolean ob = b.peer != null ? b.peer.isOnline : null;
        if (oa == null ? ob != null : !oa.equals(ob)) return false;
        boolean va = a.peer != null && a.peer.genderVerified;
        boolean vb = b.peer != null && b.peer.genderVerified;
        return va == vb;
    }

    private static boolean sameAvatar(
            ChatDtos.ConversationDto a, ChatDtos.ConversationDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return eq(resolveAvatarUrl(a), resolveAvatarUrl(b))
                && eq(resolveFrameUrl(a), resolveFrameUrl(b));
    }

    private static String resolveAvatarUrl(ChatDtos.ConversationDto item) {
        if (item == null) return null;
        if (item.peer != null && item.peer.avatarUrl != null && !item.peer.avatarUrl.isEmpty()) {
            return item.peer.avatarUrl;
        }
        return item.avatarUrl;
    }

    private static String resolveFrameUrl(ChatDtos.ConversationDto item) {
        if (item == null || item.peer == null) return null;
        return item.peer.vipBadgeUrl;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemChatPreviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_META)) {
            bindMeta(holder, items.get(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatDtos.ConversationDto item = items.get(position);
        String displayName = item.peer != null ? item.peer.displayName : null;
        if (displayName == null || displayName.isEmpty()) {
            displayName = item.title != null && !item.title.isEmpty()
                    ? item.title
                    : holder.itemView.getContext().getString(R.string.user_default);
        }
        holder.b.tvTitle.setText(displayName);

        boolean verified = item.peer != null && item.peer.genderVerified;
        if (holder.b.imgOfficial != null) {
            holder.b.imgOfficial.setVisibility(verified ? View.VISIBLE : View.GONE);
        }

        bindMeta(holder, item);

        AvatarCosmetics.bindWear(
                holder.b.imgAvatar,
                holder.b.imgFrame,
                resolveAvatarUrl(item),
                resolveFrameUrl(item));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    private void bindMeta(@NonNull VH holder, ChatDtos.ConversationDto item) {
        android.content.Context ctx = holder.itemView.getContext();
        String last = previewText(ctx, item);
        holder.b.tvLast.setText(last);
        boolean unread = item.unreadCount > 0;
        holder.b.tvLast.setTextColor(ctx.getColor(
                unread ? R.color.text_primary : R.color.text_secondary));
        holder.b.tvLast.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.b.tvTitle.setTypeface(null, unread ? Typeface.BOLD : Typeface.BOLD);

        String timeSource = item.lastMessage != null ? item.lastMessage.createdAt : item.updatedAt;
        String time = DeviceTimeFormat.messageTime(ctx, timeSource);
        if (time != null && !time.isEmpty()) {
            holder.b.tvTime.setVisibility(View.VISIBLE);
            holder.b.tvTime.setText(time);
            holder.b.tvTime.setTextColor(unread
                    ? ctx.getColor(R.color.gift_accent)
                    : ctx.getColor(R.color.text_hint));
        } else {
            holder.b.tvTime.setVisibility(View.GONE);
        }

        // Online dot only
        boolean showOnline = item.peer == null
                || item.peer.showOnlineStatus == null
                || Boolean.TRUE.equals(item.peer.showOnlineStatus);
        boolean online = false;
        if (showOnline && item.peer != null) {
            if (item.peer.isOnline != null) {
                online = Boolean.TRUE.equals(item.peer.isOnline);
            } else {
                Date d = DeviceTimeFormat.parse(item.peer.lastSeenAt);
                online = d != null && System.currentTimeMillis() - d.getTime() < 2 * 60_000L;
            }
        }
        if (holder.b.onlineDot != null) {
            holder.b.onlineDot.setVisibility(online ? View.VISIBLE : View.GONE);
        }

        int unreadCount = Math.max(0, item.unreadCount);
        if (unreadCount > 0) {
            holder.b.tvUnread.setVisibility(View.VISIBLE);
            holder.b.tvUnread.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
        } else {
            holder.b.tvUnread.setVisibility(View.GONE);
            holder.b.tvUnread.setText("");
        }
    }

    private static String previewText(android.content.Context ctx, ChatDtos.ConversationDto item) {
        if (item.lastMessage == null) {
            return ctx.getString(R.string.no_messages_yet);
        }
        String type = item.lastMessage.type != null ? item.lastMessage.type : "";
        String content = item.lastMessage.content != null ? item.lastMessage.content : "";
        if ("image".equalsIgnoreCase(type)) {
            return ctx.getString(R.string.chat_preview_photo);
        }
        if ("audio".equalsIgnoreCase(type)) {
            return ctx.getString(R.string.chat_preview_voice);
        }
        if ("gift".equalsIgnoreCase(type)) {
            return ctx.getString(R.string.chat_preview_gift,
                    !content.isEmpty() ? content : ctx.getString(R.string.gift_message));
        }
        if (RoomShareCodec.isRoomShare(content)) {
            return RoomShareCodec.previewLabel(content);
        }
        if (com.Dramizo.Series.util.AgencyInviteCodec.isAgencyInvite(content)) {
            return com.Dramizo.Series.util.AgencyInviteCodec.previewLabel(content);
        }
        return !content.isEmpty() ? content : ctx.getString(R.string.no_messages_yet);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatPreviewBinding b;

        VH(ItemChatPreviewBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
