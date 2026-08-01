package com.Dramizo.Series.presentation.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class HomeBannerAdapter extends RecyclerView.Adapter<HomeBannerAdapter.VH> {
    public interface Listener {
        void onBannerClick(MiscDtos.BannerDto banner);
    }

    private final List<MiscDtos.BannerDto> items = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<MiscDtos.BannerDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public int count() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_home_server_banner, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MiscDtos.BannerDto banner = items.get(position);
        String raw = banner.imageUrl != null ? banner.imageUrl.trim() : "";
        if (raw.startsWith("drawable://")) {
            String name = raw.substring("drawable://".length());
            int resId = holder.img.getResources()
                    .getIdentifier(name, "drawable", holder.img.getContext().getPackageName());
            if (resId != 0) {
                holder.img.setImageResource(resId);
            } else {
                holder.img.setImageResource(R.drawable.home_bg_wealth);
            }
        } else {
            String url = AssetCatalog.absoluteUrl(raw);
            Glide.with(holder.img)
                    .load(url)
                    .placeholder(ImagePlaceholder.cover())
                    .error(ImagePlaceholder.cover())
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(180))
                    .into(holder.img);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBannerClick(banner);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ShapeableImageView img;

        VH(View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgBanner);
        }
    }
}
