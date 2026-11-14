package com.Dramizo.Series.presentation.drama;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.remote.dto.DramaDtos;
import com.Dramizo.Series.databinding.ItemDramaSeriesBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DramaSeriesAdapter extends RecyclerView.Adapter<DramaSeriesAdapter.VH> {

    public interface Listener {
        void onSeriesClick(DramaDtos.SeriesDto series);
    }

    private final List<DramaDtos.SeriesDto> items = new ArrayList<>();
    private final Listener listener;
    private int coinsPerEpisode = 0;

    public DramaSeriesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCoinsPerEpisode(int coins) {
        this.coinsPerEpisode = coins;
        notifyDataSetChanged();
    }

    public void submit(List<DramaDtos.SeriesDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemDramaSeriesBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DramaDtos.SeriesDto s = items.get(position);
        h.b.tvTitle.setText(s.title != null ? s.title : "");
        boolean arabic = isArabic(h.itemView.getContext());
        h.b.tvEpisodeCount.setText(arabic
                ? (s.episodeCount + " حلقات")
                : (s.episodeCount + " eps"));
        // Numbers only — icons live in XML (no emoji / no text labels).
        h.b.tvViews.setText(formatCount(Math.max(0, s.totalViews)));
        h.b.tvLikes.setText(formatCount(Math.max(0, s.totalLikes)));

        if (coinsPerEpisode > 0) {
            h.b.layoutRewardBadge.setVisibility(View.VISIBLE);
            h.b.tvRewardCoins.setText("+" + coinsPerEpisode);
            long seriesTotal = (long) coinsPerEpisode * Math.max(0, s.episodeCount);
            String coverLine = arabic
                    ? ("+" + coinsPerEpisode + " / حلقة"
                    + (seriesTotal > 0 ? (" · حتى +" + formatCount(seriesTotal)) : ""))
                    : ("+" + coinsPerEpisode + " / ep"
                    + (seriesTotal > 0 ? (" · up to +" + formatCount(seriesTotal)) : ""));
            if (h.b.tvCoverReward != null) {
                h.b.tvCoverReward.setVisibility(View.VISIBLE);
                h.b.tvCoverReward.setText(coverLine);
            }
            if (h.b.tvSeriesReward != null) {
                if (seriesTotal > 0) {
                    h.b.tvSeriesReward.setVisibility(View.VISIBLE);
                    h.b.tvSeriesReward.setText(arabic
                            ? ("حتى +" + formatCount(seriesTotal) + " عملة")
                            : ("Up to +" + formatCount(seriesTotal) + " coins"));
                } else {
                    h.b.tvSeriesReward.setVisibility(View.GONE);
                }
            }
        } else {
            h.b.layoutRewardBadge.setVisibility(View.GONE);
            if (h.b.tvCoverReward != null) h.b.tvCoverReward.setVisibility(View.GONE);
            if (h.b.tvSeriesReward != null) h.b.tvSeriesReward.setVisibility(View.GONE);
        }

        Glide.with(h.itemView.getContext())
                .load(s.coverUrl)
                .centerCrop()
                .into(h.b.imgCover);
        h.itemView.setOnClickListener(v -> listener.onSeriesClick(s));
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
        final ItemDramaSeriesBinding b;
        VH(ItemDramaSeriesBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
