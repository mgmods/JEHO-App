package com.Dramizo.Series.presentation.voiceroom;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

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
        List<Joiner> next = members != null ? new ArrayList<>(members) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }

            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Joiner oldItem = items.get(oldPosition);
                Joiner newItem = next.get(newPosition);
                return oldItem != null && newItem != null
                        && !oldItem.userId.isEmpty()
                        && oldItem.userId.equals(newItem.userId);
            }

            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Joiner a = items.get(oldPosition);
                Joiner b = next.get(newPosition);
                return a == b || (a != null && b != null
                        && a.displayName.equals(b.displayName)
                        && java.util.Objects.equals(a.avatarUrl, b.avatarUrl)
                        && java.util.Objects.equals(a.hostBadgeUrl, b.hostBadgeUrl)
                        && java.util.Objects.equals(a.vipBadgeUrl, b.vipBadgeUrl)
                        && a.vipLevel == b.vipLevel
                        && a.userLevel == b.userLevel);
            }
        }, false);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
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
        if ("الرجل الغامض".equals(j.displayName)) {
            String sharedAvatar = h.itemView.getContext()
                    .getSharedPreferences("jeho_mysterious_mode", android.content.Context.MODE_PRIVATE)
                    .getString("avatar_url", "");
            if (sharedAvatar != null && !sharedAvatar.trim().isEmpty()) {
                com.bumptech.glide.Glide.with(h.b.imgJoinerAvatar)
                        .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(sharedAvatar.trim()))
                        .apply(com.Dramizo.Series.util.MediaAssetSync.requestOptions())
                        .placeholder(com.Dramizo.Series.R.drawable.ic_mysterious_man)
                        .error(com.Dramizo.Series.R.drawable.ic_mysterious_man)
                        .into(h.b.imgJoinerAvatar);
            } else {
                h.b.imgJoinerAvatar.setImageResource(com.Dramizo.Series.R.drawable.ic_mysterious_man);
            }
            h.b.imgJoinerFrame.setVisibility(android.view.View.GONE);
        } else {
            // Agency/host/competition marks are role labels, never avatar artwork.
            AvatarCosmetics.bindWear(
                    h.b.imgJoinerAvatar, h.b.imgJoinerFrame, j.avatarUrl,
                    j.vipBadgeUrl);
        }
        h.itemView.setContentDescription(j.displayName);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(j);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRoomRecentJoinerBinding b;

        VH(ItemRoomRecentJoinerBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
