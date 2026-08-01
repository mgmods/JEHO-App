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
import java.util.Date;
import java.util.List;

public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.VH> {
    public interface Listener { void onClick(ChatDtos.ConversationDto item); }

    private final List<ChatDtos.ConversationDto> items = new ArrayList<>();
    private final Listener listener;

    public ChatPreviewAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<ChatDtos.ConversationDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
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
            AvatarCosmetics.bindAvatar(holder.b.imgAvatar, item.peer.avatarUrl);
            // Chat list: personal VIP frame only (host signal is agency-room wear).
            AvatarCosmetics.applyHostWear(
                    holder.b.imgFrame,
                    holder.b.imgHostBadge,
                    holder.b.imgAvatar,
                    item.peer.vipBadgeUrl,
                    null,
                    null,
                    null);
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
