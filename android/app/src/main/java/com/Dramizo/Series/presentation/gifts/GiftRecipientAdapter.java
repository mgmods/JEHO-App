package com.Dramizo.Series.presentation.gifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.databinding.ItemRvGiftTopAvatarBinding;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * Mikoo GiftTopAvatarAdapter cells + VIP/host frame cosmetics.
 */
public class GiftRecipientAdapter extends RecyclerView.Adapter<GiftRecipientAdapter.VH> {

    public interface Listener {
        void onRecipientSelected(GiftRecipient recipient);

        void onSelectAllMic();
    }

    private final List<GiftRecipient> items = new ArrayList<>();
    private final Listener listener;
    @Nullable private String selectedUserId;
    private boolean selectAll;
    private boolean agencyRoom;

    public GiftRecipientAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setAgencyRoom(boolean agency) {
        if (agencyRoom == agency) return;
        agencyRoom = agency;
        notifyDataSetChanged();
    }

    public void submit(List<GiftRecipient> data, @Nullable String selectedId, boolean allSelected) {
        items.clear();
        if (data != null) items.addAll(data);
        selectedUserId = selectedId;
        selectAll = allSelected;
        notifyDataSetChanged();
    }

    public void setSelectedUserId(@Nullable String userId) {
        selectedUserId = userId;
        selectAll = false;
        notifyDataSetChanged();
    }

    public void setSelectAll(boolean all) {
        selectAll = all;
        if (all) selectedUserId = GiftRecipient.ALL_MIC_ID;
        notifyDataSetChanged();
    }

    public boolean isSelectAll() {
        return selectAll;
    }

    @Nullable
    public String getSelectedUserId() {
        return selectedUserId;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRvGiftTopAvatarBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        GiftRecipient r = items.get(position);
        boolean selected = selectAll
                || (r.userId != null && r.userId.equals(selectedUserId));

        // Prefer room-type wear, then fall back so a worn frame always shows.
        String primary = agencyRoom ? r.hostBadgeUrl : r.vipBadgeUrl;
        String fallback = agencyRoom ? r.vipBadgeUrl : r.hostBadgeUrl;
        String frameUrl = (primary != null && !primary.isEmpty()) ? primary : fallback;

        // Keep fixed CircleImageView size — do not use fitAvatarInsideWear (blows up tiny cells).
        AvatarCosmetics.bindAvatar(holder.b.civUserAvatar, r.avatarUrl);
        AvatarCosmetics.applyFrame(holder.b.ivAvatarFrame, frameUrl);

        if (holder.b.selectLine != null) {
            holder.b.selectLine.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
        if (holder.b.ivCheckStatus != null) {
            holder.b.ivCheckStatus.setVisibility(selected && !selectAll ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            selectAll = false;
            selectedUserId = r.userId;
            notifyDataSetChanged();
            if (listener != null) listener.onRecipientSelected(r);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRvGiftTopAvatarBinding b;

        VH(ItemRvGiftTopAvatarBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
