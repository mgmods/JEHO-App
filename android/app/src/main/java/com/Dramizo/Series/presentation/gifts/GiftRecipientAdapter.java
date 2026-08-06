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
 * Gift dial recipient strip — frame, seat chip, support coins under each avatar.
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
        if ((frameUrl == null || frameUrl.isEmpty()) && r.vipBadgeUrl != null
                && !r.vipBadgeUrl.isEmpty()) {
            frameUrl = r.vipBadgeUrl;
        }
        if ((frameUrl == null || frameUrl.isEmpty()) && r.hostBadgeUrl != null
                && !r.hostBadgeUrl.isEmpty()) {
            frameUrl = r.hostBadgeUrl;
        }

        AvatarCosmetics.bindStacked(
                holder.b.civUserAvatar,
                holder.b.ivAvatarFrame,
                r.avatarUrl,
                frameUrl);

        // Seat / host number chip
        if (holder.b.tvSeatChip != null) {
            String seat = r.seatLabel();
            if (seat != null && !seat.isEmpty()) {
                holder.b.tvSeatChip.setVisibility(View.VISIBLE);
                holder.b.tvSeatChip.setText(seat);
            } else {
                holder.b.tvSeatChip.setVisibility(View.GONE);
            }
        }
        // Support amount
        if (holder.b.tvSupportCoins != null) {
            holder.b.tvSupportCoins.setText(r.supportLabel());
        }

        if (holder.b.selectLine != null) {
            holder.b.selectLine.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
        if (holder.b.ivCheckStatus != null) {
            holder.b.ivCheckStatus.setVisibility(selected && !selectAll ? View.VISIBLE : View.GONE);
        }

        float alpha = selected ? 1f : 0.92f;
        holder.itemView.setAlpha(alpha);
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
