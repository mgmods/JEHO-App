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
import com.Dramizo.Series.util.StaffRoleHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.VH> {
    private static final Object PAYLOAD_META = "meta";

    public interface Listener { void onClick(ChatDtos.ConversationDto item); }

    private final List<ChatDtos.ConversationDto> items = new ArrayList<>();
    private final Listener listener;

    public ChatPreviewAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<ChatDtos.ConversationDto> data) {
        if (sameList(items, data)) {
            // Presence / unread / last message — rebind avatar fully when face/frame changed.
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
        return true;
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
        return new VH(ItemChatPreviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        String displayName = null;
        if (item.peer != null) {
            displayName = item.peer.displayName;
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = item.title != null ? item.title
                    : holder.itemView.getContext().getString(R.string.user_default);
        }
        holder.b.tvTitle.setText(displayName);
        holder.b.tvUsername.setVisibility(View.GONE);
        holder.b.tvTitle.setTextColor(
                holder.itemView.getContext().getColor(R.color.text_primary));

        // Optional staff label under name
        if (holder.b.tvStaffChip != null) {
            if (item.peer != null && StaffRoleHelper.isStaff(item.peer)) {
                holder.b.tvStaffChip.setVisibility(View.VISIBLE);
                holder.b.tvStaffChip.setText(StaffRoleHelper.badgeAr(item.peer));
                String role = StaffRoleHelper.normalize(item.peer);
                holder.b.tvStaffChip.setBackgroundResource(
                        StaffRoleHelper.SUPER.equals(role)
                                ? R.drawable.bg_chip_staff_super
                                : R.drawable.bg_chip_staff_manager);
            } else {
                holder.b.tvStaffChip.setVisibility(View.GONE);
            }
        }
        if (holder.b.rowBadges != null) {
            holder.b.rowBadges.setVisibility(View.GONE);
        }

        bindMeta(holder, item);

        AvatarCosmetics.bindWear(
                holder.b.imgAvatar,
                holder.b.imgFrame,
                resolveAvatarUrl(item),
                resolveFrameUrl(item));
        if (holder.b.imgHostBadge != null) {
            holder.b.imgHostBadge.setVisibility(View.GONE);
            holder.b.imgHostBadge.setImageDrawable(null);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    private void bindMeta(@NonNull VH holder, ChatDtos.ConversationDto item) {
        android.content.Context ctx = holder.itemView.getContext();
        String last = item.lastMessage != null ? item.lastMessage.content : "";
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
        // Bold last line when unread
        holder.b.tvLast.setTextColor(item.unreadCount > 0
                ? ctx.getColor(R.color.text_primary)
                : ctx.getColor(R.color.text_secondary));
        holder.b.tvLast.setTypeface(null,
                item.unreadCount > 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        String timeSource = item.lastMessage != null ? item.lastMessage.createdAt : item.updatedAt;
        String time = DeviceTimeFormat.messageTime(ctx, timeSource);
        if (time != null && !time.isEmpty()) {
            holder.b.tvTime.setVisibility(View.VISIBLE);
            holder.b.tvTime.setText(time);
            holder.b.tvTime.setTextColor(item.unreadCount > 0
                    ? 0xFFFE2C55 : ctx.getColor(R.color.text_secondary));
        } else {
            holder.b.tvTime.setVisibility(View.GONE);
        }

        String lastSeen = item.peer != null ? item.peer.lastSeenAt : null;
        boolean showOnline = item.peer == null
                || item.peer.showOnlineStatus == null
                || Boolean.TRUE.equals(item.peer.showOnlineStatus);
        if (!showOnline) {
            holder.b.tvStatus.setText(R.string.offline);
            holder.b.tvStatus.setTextColor(0xFF94A3B8);
            holder.b.onlineDot.setVisibility(View.GONE);
        } else {
            java.util.Date d = DeviceTimeFormat.parse(lastSeen);
            boolean recentlyActive =
                    d != null && System.currentTimeMillis() - d.getTime() < 2 * 60_000L;
            boolean online = item.peer != null && item.peer.isOnline != null
                    ? Boolean.TRUE.equals(item.peer.isOnline)
                    : recentlyActive;
            holder.b.tvStatus.setText(online
                    ? ctx.getString(R.string.online_now)
                    : ctx.getString(R.string.offline));
            holder.b.tvStatus.setTextColor(online ? 0xFF10B981 : 0xFF94A3B8);
            holder.b.onlineDot.setVisibility(online ? View.VISIBLE : View.GONE);
        }

        int unread = Math.max(0, item.unreadCount);
        if (unread > 0) {
            holder.b.tvUnread.setVisibility(View.VISIBLE);
            holder.b.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
        } else {
            holder.b.tvUnread.setVisibility(View.GONE);
            holder.b.tvUnread.setText("");
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatPreviewBinding b;
        VH(ItemChatPreviewBinding b) { super(b.getRoot()); this.b = b; }
    }
}
