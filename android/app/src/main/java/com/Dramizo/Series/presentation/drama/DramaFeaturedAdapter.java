package com.Dramizo.Series.presentation.drama;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.remote.dto.DramaDtos;
import com.Dramizo.Series.databinding.ItemDramaFeaturedBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Top-viewed drama series slider (home-banner style). */
public class DramaFeaturedAdapter extends RecyclerView.Adapter<DramaFeaturedAdapter.VH> {

    public interface Listener {
        void onFeaturedClick(DramaDtos.SeriesDto series);
    }

    private final List<DramaDtos.SeriesDto> items = new ArrayList<>();
    private final Listener listener;

    public DramaFeaturedAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DramaDtos.SeriesDto> data) {
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
        return new VH(ItemDramaFeaturedBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DramaDtos.SeriesDto s = items.get(position);
        boolean arabic = isArabic(h.itemView.getContext());
        h.b.tvFeaturedTitle.setText(s.title != null ? s.title : "");
        h.b.tvFeaturedBadge.setText(arabic ? "الأكثر مشاهدة" : "Top views");
        h.b.tvFeaturedMeta.setText(String.format(Locale.US, "%s  ·  %s",
                formatCount(Math.max(0, s.totalViews)),
                arabic ? (s.episodeCount + " حلقات") : (s.episodeCount + " eps")));
        Glide.with(h.itemView.getContext())
                .load(s.coverUrl)
                .centerCrop()
                .into(h.b.imgFeaturedCover);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFeaturedClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static boolean isArabic(android.content.Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        if (locale == null) locale = Locale.getDefault();
        String lang = locale.getLanguage();
        return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("ar");
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemDramaFeaturedBinding b;
        VH(ItemDramaFeaturedBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
