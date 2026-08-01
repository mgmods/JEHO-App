package com.Dramizo.Series.presentation.notifications;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemNotificationBinding;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {
    public interface Listener { void onClick(MiscDtos.NotificationDto n); }

    private final List<MiscDtos.NotificationDto> items = new ArrayList<>();
    private final Listener listener;

    public NotificationAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<MiscDtos.NotificationDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MiscDtos.NotificationDto n = items.get(position);
        String typeLabel = labelForType(holder.itemView.getContext(), n.type);
        holder.b.tvTitle.setText(n.title != null ? n.title : typeLabel);
        holder.b.tvBody.setText(n.body != null ? n.body : "");
        holder.b.tvTypeChip.setText(typeLabel);
        holder.b.imgNotificationType.setImageResource(iconForType(n.type));
        holder.b.imgNotificationType.setImageTintList(null);
        holder.b.imgNotificationType.clearColorFilter();
        String time = n.createdAt != null ? n.createdAt.replace('T', ' ') : "";
        if (time.length() > 16) time = time.substring(0, 16);
        holder.b.tvTime.setText(time);
        holder.b.viewUnreadDot.setVisibility(n.isRead ? View.GONE : View.VISIBLE);
        holder.itemView.setAlpha(n.isRead ? 0.72f : 1f);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(n);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String labelForType(Context context, String type) {
        if (type == null) return context.getString(R.string.notification_generic);
        switch (type.toLowerCase()) {
            case "gift": return context.getString(R.string.notification_gift);
            case "chat":
            case "message": return context.getString(R.string.notification_message);
            case "live":
            case "stream": return context.getString(R.string.notification_room);
            case "room":
            case "voice": return context.getString(R.string.notification_room);
            case "follow": return context.getString(R.string.notification_follow);
            case "friend":
            case "relation": return context.getString(R.string.notification_friendship);
            case "withdraw": return context.getString(R.string.notification_withdrawal);
            case "system": return context.getString(R.string.notification_system);
            default: return type;
        }
    }

    private static int iconForType(String type) {
        if (type == null) return R.drawable.jeho_logo;
        switch (type.toLowerCase()) {
            case "gift": return ImagePlaceholder.gift();
            case "chat":
            case "message": return R.drawable.jeho_logo;
            case "live":
            case "stream": return R.drawable.jeho_logo;
            case "follow":
            case "friend":
            case "relation": return ImagePlaceholder.avatar();
            case "system":
            case "agency":
            case "wallet":
            case "vip":
            case "withdraw":
                return R.drawable.jeho_logo;
            default: return R.drawable.jeho_logo;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;
        VH(ItemNotificationBinding b) { super(b.getRoot()); this.b = b; }
    }
}
