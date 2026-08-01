package com.Dramizo.Series.presentation.cosmetics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.Dramizo.Series.R;
import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.databinding.ItemCosmeticBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class CosmeticsAdapter extends RecyclerView.Adapter<CosmeticsAdapter.VH> {
    public interface Listener {
        void onPurchase(String cosmeticId);
        void onEquip(String cosmeticId);
        boolean isOwned(String cosmeticId);
        boolean isEquipped(String cosmeticId);
    }

    private final List<CosmeticDtos.CosmeticDto> items = new ArrayList<>();
    private final Listener listener;

    public CosmeticsAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<CosmeticDtos.CosmeticDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCosmeticBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CosmeticDtos.CosmeticDto item = items.get(position);
        holder.b.tvName.setText(item.name != null ? item.name : item.code);
        holder.b.tvPrice.setText(item.coinPrice > 0
                ? (item.coinPrice + " عملة")
                : "مجاني");
        boolean owned = listener.isOwned(item.id);
        boolean equipped = listener.isEquipped(item.id);
        holder.b.btnPurchase.setVisibility(owned ? View.GONE : View.VISIBLE);
        holder.b.btnEquip.setVisibility(owned ? View.VISIBLE : View.GONE);
        holder.b.btnEquip.setEnabled(!equipped);
        holder.b.btnEquip.setText(equipped
                ? holder.itemView.getContext().getString(R.string.equipped)
                : holder.itemView.getContext().getString(R.string.equip));
        holder.b.btnEquip.setBackgroundResource(equipped
                ? R.drawable.bg_cosmetic_btn_equipped
                : R.drawable.bg_cosmetic_btn_equip);
        holder.b.btnEquip.setTextColor(equipped ? 0xFF1A1200 : 0xFF0F766E);
        holder.b.btnPurchase.setOnClickListener(v -> listener.onPurchase(item.id));
        holder.b.btnEquip.setOnClickListener(v -> {
            if (!equipped) listener.onEquip(item.id);
        });

        // قطاع الراس: avatar inside animated frame (HostSignalView / SVGA / wing flap).
        String wear = preferWearMedia(item.animationUrl, item.previewUrl);
        boolean headwear = "vip_badge".equalsIgnoreCase(item.type)
                || "frames".equalsIgnoreCase(item.type);
        if (headwear) {
            var app = (AuraLiveApp) holder.itemView.getContext().getApplicationContext();
            var session = app.getContainer().getSessionManager();
            Glide.with(holder.b.imgPreview).clear(holder.b.imgPreview);
            holder.b.imgPreview.setImageDrawable(null);
            holder.b.imgPreview.setVisibility(View.GONE);
            holder.b.webHostPreview.setVisibility(View.VISIBLE);
            holder.b.webHostPreview.bind(
                    wear != null ? wear : item.previewUrl,
                    session.getAvatarUrl(),
                    item.meta,
                    Math.max(1, session.getUserLevel()));
        } else {
            holder.b.webHostPreview.clearSignal();
            holder.b.webHostPreview.setVisibility(View.GONE);
            holder.b.imgPreview.setVisibility(View.VISIBLE);
            holder.b.imgPreview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            holder.b.imgPreview.setBackgroundColor(0x00000000);
            holder.b.imgPreview.setScaleX(1f);
            holder.b.imgPreview.setScaleY(1f);
            holder.b.imgPreview.setRotation(0f);
            holder.b.imgPreview.setAlpha(1f);
            String preview = AssetCatalog.absoluteUrl(
                    firstNonEmpty(item.previewUrl, nonJsonAnim(item.animationUrl)));
            if (preview == null || preview.isEmpty()) {
                holder.b.imgPreview.setImageResource(ImagePlaceholder.cover());
            } else {
                String lower = preview.toLowerCase(java.util.Locale.US);
                if (lower.contains(".gif") || lower.endsWith(".webp") || lower.endsWith(".svga")) {
                    if (lower.endsWith(".svga")) {
                        holder.b.imgPreview.setImageResource(ImagePlaceholder.cover());
                    } else {
                        Glide.with(holder.b.imgPreview)
                                .asGif()
                                .load(preview)
                                .placeholder(ImagePlaceholder.cover())
                                .error(ImagePlaceholder.cover())
                                .fitCenter()
                                .into(holder.b.imgPreview);
                    }
                } else {
                    Glide.with(holder.b.imgPreview).load(preview)
                            .placeholder(ImagePlaceholder.cover())
                            .error(ImagePlaceholder.cover())
                            .fitCenter()
                            .into(holder.b.imgPreview);
                }
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        holder.b.webHostPreview.clearSignal();
        Glide.with(holder.b.imgPreview).clear(holder.b.imgPreview);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }

    private static String nonJsonAnim(String anim) {
        if (anim == null || anim.isEmpty()) return null;
        if (anim.toLowerCase(java.util.Locale.US).endsWith(".json")) return null;
        return anim;
    }

    /** Prefer playable motion (GIF/MP4/WebP) over still preview / HTML engines. */
    private static String preferWearMedia(String anim, String preview) {
        String playableAnim = com.Dramizo.Series.util.CosmeticMedia.playableUrl(anim);
        if (playableAnim != null
                && com.Dramizo.Series.util.CosmeticMedia.isAnimatedWear(playableAnim)) {
            return playableAnim;
        }
        String playablePreview = com.Dramizo.Series.util.CosmeticMedia.playableUrl(preview);
        if (playablePreview != null) return playablePreview;
        return firstNonEmpty(preview, nonJsonAnim(anim));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCosmeticBinding b;
        VH(ItemCosmeticBinding b) { super(b.getRoot()); this.b = b; }
    }
}
