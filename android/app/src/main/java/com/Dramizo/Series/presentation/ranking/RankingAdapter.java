package com.Dramizo.Series.presentation.ranking;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemRankingBinding;
import com.Dramizo.Series.util.AvatarCosmetics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.VH> {
    public interface Listener {
        void onUserClick(String userId);
    }

    private final List<MiscDtos.RankingEntryDto> items = new ArrayList<>();
    private final Listener listener;
    private String category = "rich";

    public RankingAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "rich";
        notifyDataSetChanged();
    }

    public void submit(List<MiscDtos.RankingEntryDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRankingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MiscDtos.RankingEntryDto entry = items.get(position);
        int rank = entry.rank > 0 ? entry.rank : position + 1;
        holder.b.tvRank.setText(rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : String.valueOf(rank));
        holder.b.tvScore.setText(formatScore(entry.score, category));
        if (entry.user != null) {
            holder.b.tvName.setText(entry.user.displayName != null
                    ? entry.user.displayName : entry.user.username);
            AvatarCosmetics.bindWear(holder.b.imgAvatar, holder.b.imgFrame, entry.user);
            holder.b.tvSubtitle.setText("Lv." + Math.max(1, entry.user.level)
                    + (entry.user.displayPublicId().isEmpty() ? "" : " · ID " + entry.user.displayPublicId()));
        } else {
            String name = entry.targetName != null ? entry.targetName
                    : (entry.userId != null ? entry.userId : entry.targetId);
            holder.b.tvName.setText(name != null ? name : "—");
            holder.b.imgAvatar.setImageResource(ImagePlaceholder.avatar());
            AvatarCosmetics.applyFrame(holder.b.imgFrame, null);
            holder.b.tvSubtitle.setText(entityLabel(category));
        }
        String targetId = entry.user != null ? entry.user.id
                : (entry.userId != null ? entry.userId : entry.targetId);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && isUserCategory(category)
                    && targetId != null && !targetId.isEmpty()) {
                listener.onUserClick(targetId);
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String formatScore(long score, String category) {
        String value = String.format(Locale.US, "%,d", Math.max(0L, score));
        if ("rich".equals(category)) return value + " عملة";
        if ("popular".equals(category)) return value + " متابع";
        if ("gifts".equals(category) || "host".equals(category)) {
            return value + " ألماس";
        }
        if ("room".equals(category)) return value + " مشاهد";
        return value + " نقطة";
    }

    private static String entityLabel(String category) {
        if ("agency".equals(category)) return "وكالة";
        if ("room".equals(category)) return "غرفة صوتية";
        return "حساب JEHO CHAT";
    }

    private static boolean isUserCategory(String category) {
        return "rich".equals(category) || "popular".equals(category)
                || "gifts".equals(category) || "host".equals(category);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRankingBinding b;
        VH(ItemRankingBinding b) { super(b.getRoot()); this.b = b; }
    }
}
