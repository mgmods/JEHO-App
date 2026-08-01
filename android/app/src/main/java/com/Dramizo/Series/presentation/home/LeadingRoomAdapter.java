package com.Dramizo.Series.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ItemPartyRoomBinding;
import com.Dramizo.Series.util.RoomCardAnimator;
import com.Dramizo.Series.util.RoomCardBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explore rooms — same DiffUtil card grid as Hot (no flicker). */
public class LeadingRoomAdapter extends RecyclerView.Adapter<LeadingRoomAdapter.VH> {
    public interface Listener {
        void onClick(RoomDtos.RoomDto room);
    }

    private final List<RoomDtos.RoomDto> items = new ArrayList<>();
    private final Listener listener;

    public LeadingRoomAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<RoomDtos.RoomDto> data) {
        List<RoomDtos.RoomDto> next = data != null ? new ArrayList<>(data) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override
            public boolean areItemsTheSame(int o, int n) {
                RoomDtos.RoomDto a = items.get(o);
                RoomDtos.RoomDto b = next.get(n);
                return a != null && b != null && Objects.equals(a.id, b.id);
            }
            @Override
            public boolean areContentsTheSame(int o, int n) {
                RoomDtos.RoomDto a = items.get(o);
                RoomDtos.RoomDto b = next.get(n);
                if (a == null || b == null) return a == b;
                return a.viewerCount == b.viewerCount
                        && Objects.equals(a.coverUrl, b.coverUrl)
                        && Objects.equals(a.roomCardUrl, b.roomCardUrl)
                        && Objects.equals(a.title, b.title);
            }

            @Nullable
            @Override
            public Object getChangePayload(int o, int n) {
                RoomDtos.RoomDto a = items.get(o);
                RoomDtos.RoomDto b = next.get(n);
                if (a == null || b == null) return null;
                boolean heavy = !Objects.equals(a.coverUrl, b.coverUrl)
                        || !Objects.equals(a.roomCardUrl, b.roomCardUrl)
                        || !Objects.equals(a.title, b.title);
                if (heavy) return null;
                if (a.viewerCount != b.viewerCount) return "patch";
                return null;
            }
        }, true);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        RoomDtos.RoomDto r = items.get(position);
        return r != null && r.id != null ? r.id.hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPartyRoomBinding b = ItemPartyRoomBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        b.getRoot().setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        onBindViewHolder(holder, position, java.util.Collections.emptyList());
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        RoomDtos.RoomDto room = items.get(position);
        if (room == null) return;
        if (payloads != null && !payloads.isEmpty() && "patch".equals(payloads.get(0))) {
            RoomCardBinder.bindViewerOnly(holder.b, room);
            return;
        }
        RoomCardBinder.bind(holder.b, room, false);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(room);
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        RoomCardAnimator.stop(holder.b.imgCardFrame);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPartyRoomBinding b;

        VH(ItemPartyRoomBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
