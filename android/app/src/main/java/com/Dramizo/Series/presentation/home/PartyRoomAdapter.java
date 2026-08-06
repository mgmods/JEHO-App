package com.Dramizo.Series.presentation.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ItemPartyRoomBinding;
import com.Dramizo.Series.util.MikooLoadingAnim;
import com.Dramizo.Series.util.RoomCardAnimator;
import com.Dramizo.Series.util.RoomCardBinder;
import com.opensource.svgaplayer.SVGAImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mikoo HomeLiveListAdapter parity: DiffUtil, append, optional load-more footer SVGA.
 */
public class PartyRoomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_ROOM = 1;
    private static final int TYPE_LOADING = 2;

    public interface Listener {
        void onClick(RoomDtos.RoomDto room);
    }

    private final List<RoomDtos.RoomDto> items = new ArrayList<>();
    private final Listener listener;
    private boolean showLoadingFooter;

    public PartyRoomAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /** Attach full-span lookup when using GridLayoutManager + footer. */
    public void attachSpanSize(@NonNull GridLayoutManager glm) {
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_LOADING ? glm.getSpanCount() : 1;
            }
        });
    }

    public void setLoadingMore(boolean loading) {
        if (showLoadingFooter == loading) return;
        boolean had = showLoadingFooter;
        showLoadingFooter = loading;
        if (loading && !had) {
            notifyItemInserted(items.size());
        } else if (!loading && had) {
            notifyItemRemoved(items.size());
        }
    }

    public boolean isLoadingMore() {
        return showLoadingFooter;
    }

    /** Full replace (pull-to-refresh / first page) — DiffUtil, not notifyDataSetChanged. */
    public void submit(@Nullable List<RoomDtos.RoomDto> data) {
        boolean foot = showLoadingFooter;
        if (foot) {
            showLoadingFooter = false;
            notifyItemRemoved(items.size());
        }
        List<RoomDtos.RoomDto> old = new ArrayList<>(items);
        List<RoomDtos.RoomDto> next = data != null ? new ArrayList<>(data) : new ArrayList<>();
        // Must snapshot old list — DiffResult reads oldList during dispatch; mutating
        // `items` in place caused IndexOutOfBoundsException (e.g. index 14 of size 10).
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new RoomDiff(old, next), true);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
        if (foot) {
            showLoadingFooter = true;
            notifyItemInserted(items.size());
        }
    }

    /** Mikoo addData: append only (load more). */
    public void append(@Nullable List<RoomDtos.RoomDto> more) {
        if (more == null || more.isEmpty()) return;
        int start = items.size();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (RoomDtos.RoomDto r : items) {
            if (r != null && r.id != null) seen.add(r.id);
        }
        List<RoomDtos.RoomDto> added = new ArrayList<>();
        for (RoomDtos.RoomDto r : more) {
            if (r == null || r.id == null || !seen.add(r.id)) continue;
            added.add(r);
        }
        if (added.isEmpty()) return;
        items.addAll(added);
        notifyItemRangeInserted(start, added.size());
    }

    public void patchRoom(String roomId, int viewerCount, List<String> viewerAvatars) {
        for (int i = 0; i < items.size(); i++) {
            RoomDtos.RoomDto room = items.get(i);
            if (room != null && roomId.equals(room.id)) {
                room.viewerCount = viewerCount;
                if (viewerAvatars != null) room.viewerAvatars = viewerAvatars;
                notifyItemChanged(i, "patch");
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (showLoadingFooter && position == items.size()) return TYPE_LOADING;
        return TYPE_ROOM;
    }

    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_LOADING) return Long.MIN_VALUE + 7;
        RoomDtos.RoomDto r = items.get(position);
        if (r == null || r.id == null) return RecyclerView.NO_ID;
        return r.id.hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOADING) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list_loading, parent, false);
            return new LoadingVH(v);
        }
        return new VH(
                ItemPartyRoomBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        onBindViewHolder(holder, position, java.util.Collections.emptyList());
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (holder instanceof LoadingVH) {
            MikooLoadingAnim.bind(((LoadingVH) holder).svga);
            return;
        }
        VH h = (VH) holder;
        RoomDtos.RoomDto room = items.get(position);
        if (payloads != null && !payloads.isEmpty() && "patch".equals(payloads.get(0))) {
            RoomCardBinder.bindViewerOnly(h.b, room);
            return;
        }
        RoomCardBinder.bind(h.b, room, false);
        h.itemView.setOnClickListener(v -> listener.onClick(room));
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof LoadingVH) {
            MikooLoadingAnim.stop(((LoadingVH) holder).svga);
        } else if (holder instanceof VH) {
            // Stop kenar animator only — successful cover tags stay so rebind can skip reloads.
            RoomCardAnimator.stop(((VH) holder).b.imgCardFrame);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size() + (showLoadingFooter ? 1 : 0);
    }

    public int getRoomCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemPartyRoomBinding b;

        VH(ItemPartyRoomBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static class LoadingVH extends RecyclerView.ViewHolder {
        final SVGAImageView svga;

        LoadingVH(View v) {
            super(v);
            svga = v.findViewById(R.id.svListLoading);
        }
    }

    private static final class RoomDiff extends DiffUtil.Callback {
        private final List<RoomDtos.RoomDto> oldList;
        private final List<RoomDtos.RoomDto> newList;

        RoomDiff(List<RoomDtos.RoomDto> oldList, List<RoomDtos.RoomDto> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            RoomDtos.RoomDto a = oldList.get(oldItemPosition);
            RoomDtos.RoomDto b = newList.get(newItemPosition);
            if (a == null || b == null) return a == b;
            return Objects.equals(a.id, b.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            RoomDtos.RoomDto a = oldList.get(oldItemPosition);
            RoomDtos.RoomDto b = newList.get(newItemPosition);
            if (a == null || b == null) return a == b;
            return a.viewerCount == b.viewerCount
                    && a.roomLevel == b.roomLevel
                    && Objects.equals(a.title, b.title)
                    && Objects.equals(a.coverUrl, b.coverUrl)
                    && Objects.equals(a.roomCardUrl, b.roomCardUrl)
                    && Objects.equals(a.displayRoomId, b.displayRoomId)
                    && Objects.equals(hostAvatar(a), hostAvatar(b))
                    && Objects.equals(a.status, b.status);
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            RoomDtos.RoomDto a = oldList.get(oldItemPosition);
            RoomDtos.RoomDto b = newList.get(newItemPosition);
            if (a == null || b == null) return null;
            boolean heavy = a.roomLevel != b.roomLevel
                    || !Objects.equals(a.title, b.title)
                    || !Objects.equals(a.coverUrl, b.coverUrl)
                    || !Objects.equals(a.roomCardUrl, b.roomCardUrl)
                    || !Objects.equals(hostAvatar(a), hostAvatar(b))
                    || !Objects.equals(a.status, b.status);
            if (heavy) return null;
            if (a.viewerCount != b.viewerCount) return "patch";
            return null;
        }

        private static String hostAvatar(RoomDtos.RoomDto r) {
            return r.host != null ? r.host.avatarUrl : null;
        }
    }
}
