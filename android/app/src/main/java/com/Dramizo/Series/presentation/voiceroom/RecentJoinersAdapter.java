package com.Dramizo.Series.presentation.voiceroom;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.databinding.ItemRoomRecentJoinerBinding;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.util.ArrayList;
import java.util.List;

/** Live horizontal strip of everyone currently connected to the room. */
public class RecentJoinersAdapter extends RecyclerView.Adapter<RecentJoinersAdapter.VH> {
    public interface Listener {
        void onClick(Joiner joiner);
    }

    public static final class Joiner {
        public final String userId;
        public final String displayName;
        public final String avatarUrl;
        public final String hostBadgeUrl;
        public final String vipBadgeUrl;
        public final int vipLevel;
        public final int userLevel;

        public Joiner(String userId, String displayName, String avatarUrl, String hostBadgeUrl,
                      int vipLevel, int userLevel) {
            this(userId, displayName, avatarUrl, hostBadgeUrl, null, vipLevel, userLevel);
        }

        public Joiner(String userId, String displayName, String avatarUrl, String hostBadgeUrl,
                      String vipBadgeUrl, int vipLevel, int userLevel) {
            this.userId = userId != null ? userId : "";
            this.displayName = displayName != null ? displayName : "";
            this.avatarUrl = avatarUrl;
            this.hostBadgeUrl = hostBadgeUrl;
            this.vipBadgeUrl = vipBadgeUrl;
            this.vipLevel = Math.max(0, vipLevel);
            this.userLevel = Math.max(1, userLevel);
        }
    }

    private final List<Joiner> items = new ArrayList<>();
    private final Listener listener;
    private boolean agencyRoom;

    public RecentJoinersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setAgencyRoom(boolean agency) {
        if (agencyRoom == agency) return;
        agencyRoom = agency;
        notifyDataSetChanged();
    }

    public void submit(List<Joiner> members) {
        items.clear();
        if (members != null) items.addAll(members);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRoomRecentJoinerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Joiner j = items.get(position);
        AvatarCosmetics.bindAvatar(h.b.imgJoinerAvatar, j.avatarUrl);
        AvatarCosmetics.applyFrame(h.b.imgJoinerFrame,
                agencyRoom ? j.hostBadgeUrl : j.vipBadgeUrl);
        h.itemView.setContentDescription(j.displayName);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(j);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRoomRecentJoinerBinding b;

        VH(ItemRoomRecentJoinerBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
