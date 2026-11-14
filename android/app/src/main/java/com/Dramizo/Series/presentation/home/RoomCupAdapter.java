package com.Dramizo.Series.presentation.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomCupDtos;
import com.Dramizo.Series.databinding.ItemRoomCupBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RoomCupAdapter extends RecyclerView.Adapter<RoomCupAdapter.VH> {
    public interface Listener {
        void onClick(RoomCupDtos.Item item);
    }

    private final List<RoomCupDtos.Item> items = new ArrayList<>();
    private final Listener listener;

    public RoomCupAdapter() {
        this(null);
    }

    public RoomCupAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<RoomCupDtos.Item> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRoomCupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RoomCupDtos.Item item = items.get(position);
        h.b.tvRank.setText("#" + Math.max(1, item.rank));
        h.b.tvTitle.setText(item.title != null && !item.title.isEmpty()
                ? item.title
                : h.itemView.getContext().getString(R.string.room));
        h.b.tvScore.setText(String.format(Locale.US, "%,d 💎", Math.max(0, item.score)));
        Glide.with(h.b.imgCover)
                .load(AssetCatalog.absoluteUrl(item.coverUrl))
                .placeholder(ImagePlaceholder.cover())
                .error(ImagePlaceholder.cover())
                .centerCrop()
                .into(h.b.imgCover);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRoomCupBinding b;
        VH(ItemRoomCupBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
