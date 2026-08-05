package com.Dramizo.Series.presentation.chat;

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

public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.VH> {
    public interface Listener { void onClick(ChatDtos.ConversationDto item); }

    private final List<ChatDtos.ConversationDto> items = new ArrayList<>();
    private final Listener listener;

    public ChatPreviewAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<ChatDtos.ConversationDto> data) {
        if (sameList(items, data)) {
            // Presence / unread only: soft update existing views without clearing avatars.
            List<ChatDtos.ConversationDto> next = data != null ? data : Collections.emptyList();
            for (int i = 0; i < items.size() && i < next.size(); i++) {
                ChatDtos.ConversationDto a = items.get(i);
                ChatDtos.ConversationDto b = next.get(i);
                if (!samePreview(a, b)) {
                    items.set(i, b);
                    notifyItemChanged(i);
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
        Boolean oa = a.peer != null ? a.peer.isOnline : null;
        Boolean ob = b.peer != null ? b.peer.isOnline : null;
        if (oa == null ? ob != null : !oa.equals(ob)) return false;
        return true;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemChatPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatDtos.ConversationDto item = items.get(position);
        String displayName = null;
        if (item.peer != null) {
            displayName = item.peer.displayName;
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = item.title != null ? item.title
                    : holder.itemView.getContext().getString(R.string.user_default);
        }
        holder.b.tvTitle.setText(displayName);
        // Mikoo chat list: no ID under the name.
        holder.b.tvUsername.setVisibility(View.GONE);

        // Chat list: keep names consistent black (no VIP rainbow colors).
        holder.b.tvTitle.setTextColor(
                holder.itemView.getContext().getColor(R.color.text_primary));
        // Mikoo-style list: name + last message only (no VIP/level chip clutter).
        holder.b.rowBadges.setVisibility(View.GONE);
        holder.b.tvVipChip.setVisibility(View.GONE);

        String last = item.lastMessage != null ? item.lastMessage.content : "";
        android.content.Context ctx = holder.itemView.getContext();
        if (item.lastMessage != null && "image".equalsIgnoreCase(item.lastMessage.type)) {
            last = ctx.getString(R.string.chat_preview_photo);
        } else if (item.lastMessage != null && "audio".equalsIgnoreCase(item.lastMessage.type)) {
            last = ctx.getString(R.string.chat_preview_voice);
        } else if (item.lastMessage != null && "gift".equalsIgnoreCase(item.lastMessage.type)) {
            last = ctx.getString(R.string.chat_preview_gift,
                    item.lastMessage.content != null ? item.lastMessage.content
                            : ctx.getString(R.string.gift_message));
        } else if (RoomShareCodec.isRoomShare(last)) {
            last = RoomShareCodec.previewLabel(last);
        }
        holder.b.tvLast.setText(last != null && !last.isEmpty()
                ? last : ctx.getString(R.string.no_messages_yet));

        String timeSource = item.lastMessage != null ? item.lastMessage.createdAt : item.updatedAt;
        String time = DeviceTimeFormat.messageTime(holder.itemView.getContext(), timeSource);
        if (time != null && !time.isEmpty()) {
            holder.b.tvTime.setVisibility(View.VISIBLE);
            holder.b.tvTime.setText(time);
        } else {
            holder.b.tvTime.setVisibility(View.GONE);
        }

        String lastSeen = item.peer != null ? item.peer.lastSeenAt : null;
        boolean showOnline = item.peer == null
                || item.peer.showOnlineStatus == null
                || Boolean.TRUE.equals(item.peer.showOnlineStatus);
        if (!showOnline) {
            holder.b.tvStatus.setText(R.string.offline);
            holder.b.tvStatus.setTextColor(0xFF595959);
            holder.b.onlineDot.setVisibility(View.GONE);
        } else {
            Date d = DeviceTimeFormat.parse(lastSeen);
            boolean recentlyActive =
                    d != null && System.currentTimeMillis() - d.getTime() < 2 * 60_000L;
            boolean online = item.peer != null && item.peer.isOnline != null
                    ? Boolean.TRUE.equals(item.peer.isOnline)
                    : recentlyActive;
            holder.b.tvStatus.setText(online
                    ? holder.itemView.getContext().getString(R.string.online_now)
                    : holder.itemView.getContext().getString(R.string.offline));
            holder.b.tvStatus.setTextColor(online
                    ? holder.itemView.getContext().getColor(R.color.aurora_mint)
                    : holder.itemView.getContext().getColor(R.color.text_secondary));
            holder.b.onlineDot.setVisibility(online ? View.VISIBLE : View.GONE);
        }

        if (item.unreadCount > 0) {
            holder.b.tvUnread.setVisibility(View.VISIBLE);
            holder.b.tvUnread.setText(String.valueOf(item.unreadCount));
        } else {
            holder.b.tvUnread.setVisibility(View.GONE);
        }

        if (item.peer != null) {
            // VIP/head frame for every peer (admin / manager / super included).
            AvatarCosmetics.bindWear(holder.b.imgAvatar, holder.b.imgFrame, item.peer);
            if (holder.b.imgHostBadge != null) {
                holder.b.imgHostBadge.setVisibility(View.GONE);
                holder.b.imgHostBadge.setImageDrawable(null);
            }
        } else {
            AvatarCosmetics.bindWear(holder.b.imgAvatar, holder.b.imgFrame, null, null);
            if (holder.b.imgHostBadge != null) {
                holder.b.imgHostBadge.setVisibility(View.GONE);
            }
        }
        // Never overlay room kenar/frames on chat list rows — ruins Mikoo-style list.
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatPreviewBinding b;
        VH(ItemChatPreviewBinding b) { super(b.getRoot()); this.b = b; }
    }
}
