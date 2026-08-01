package com.Dramizo.Series.presentation.gifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;

import java.util.ArrayList;
import java.util.List;

/** One Mikoo gift page = 4×2 grid (8 cells). */
public final class GiftPageAdapter extends RecyclerView.Adapter<GiftPageAdapter.PageVH> {
    public static final int PAGE_SIZE = 8;

    public interface Listener {
        void onGiftClick(@Nullable GiftDtos.GiftDto gift);
    }

    private final Listener listener;
    private final List<List<GiftDtos.GiftDto>> pages = new ArrayList<>();
    @Nullable private String selectedId;

    public GiftPageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(@Nullable List<GiftDtos.GiftDto> gifts, @Nullable String selectedGiftId) {
        pages.clear();
        selectedId = selectedGiftId;
        if (gifts != null && !gifts.isEmpty()) {
            for (int i = 0; i < gifts.size(); i += PAGE_SIZE) {
                int end = Math.min(i + PAGE_SIZE, gifts.size());
                pages.add(new ArrayList<>(gifts.subList(i, end)));
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedId(@Nullable String id) {
        selectedId = id;
        notifyDataSetChanged();
    }

    public int pageCount() {
        return pages.size();
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gift_page, parent, false);
        return new PageVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {
        holder.bind(pages.get(position), selectedId, listener);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static final class PageVH extends RecyclerView.ViewHolder {
        private final RecyclerView recycler;
        private final GiftAdapter adapter;

        PageVH(@NonNull View itemView) {
            super(itemView);
            recycler = itemView.findViewById(R.id.rvGiftPage);
            adapter = new GiftAdapter(null);
            recycler.setLayoutManager(new GridLayoutManager(itemView.getContext(), 4));
            recycler.setNestedScrollingEnabled(false);
            recycler.setAdapter(adapter);
        }

        void bind(List<GiftDtos.GiftDto> gifts, @Nullable String selectedId, Listener listener) {
            adapter.setListener(listener::onGiftClick);
            adapter.submit(gifts);
            adapter.setSelectedId(selectedId);
        }
    }
}
