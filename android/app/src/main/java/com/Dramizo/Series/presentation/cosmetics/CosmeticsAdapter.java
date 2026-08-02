package com.Dramizo.Series.presentation.cosmetics;

import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.databinding.ItemCosmeticBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mall grid adapter — PNG-only previews. Never binds HostSignalView / SVGA here
 * (that caused OutOfMemoryError and closed the app when opening قطاع الراس).
 */
public class CosmeticsAdapter extends RecyclerView.Adapter<CosmeticsAdapter.VH> {
    public interface Listener {
        void onPurchase(String cosmeticId);
        void onEquip(String cosmeticId);
        void onUnequip(String cosmeticId);
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
        holder.b.btnEquip.setEnabled(true);
        holder.b.btnEquip.setText(equipped
                ? holder.itemView.getContext().getString(R.string.unequip)
                : holder.itemView.getContext().getString(R.string.equip));
        holder.b.btnEquip.setBackgroundResource(equipped
                ? R.drawable.bg_cosmetic_btn_equipped
                : R.drawable.bg_cosmetic_btn_equip);
        holder.b.btnEquip.setTextColor(equipped ? 0xFF1A1200 : 0xFF0F766E);
        holder.b.btnPurchase.setOnClickListener(v -> listener.onPurchase(item.id));
        holder.b.btnEquip.setOnClickListener(v -> {
            if (equipped) listener.onUnequip(item.id);
            else listener.onEquip(item.id);
        });

        boolean headwear = "vip_badge".equalsIgnoreCase(item.type)
                || "host_badge".equalsIgnoreCase(item.type)
                || "frames".equalsIgnoreCase(item.type);

        String still = stillPngUrl(item.previewUrl, item.animationUrl);
        String abs = AssetCatalog.absoluteUrl(still);

        holder.b.imgPreview.setVisibility(View.VISIBLE);
        holder.b.imgPreview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        holder.b.imgPreview.setPadding(dp(holder, headwear ? 4 : 8), dp(holder, headwear ? 4 : 8),
                dp(holder, headwear ? 4 : 8), dp(holder, headwear ? 4 : 8));

        if (abs == null || abs.isEmpty()) {
            holder.b.imgPreview.setImageResource(ImagePlaceholder.cover());
        } else {
            Glide.with(holder.b.imgPreview)
                    .load(abs)
                    .override(256, 256)
                    .fitCenter()
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(ImagePlaceholder.cover())
                    .error(ImagePlaceholder.cover())
                    .into(holder.b.imgPreview);
        }

        if (headwear) {
            holder.b.imgFace.setVisibility(View.VISIBLE);
            ensureCircle(holder.b.imgFace);
            String avatarUrl = null;
            try {
                var app = (AuraLiveApp) holder.itemView.getContext().getApplicationContext();
                avatarUrl = app.getContainer().getSessionManager().getAvatarUrl();
            } catch (Exception ignored) {
            }
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                holder.b.imgFace.setImageResource(ImagePlaceholder.avatar());
            } else {
                Glide.with(holder.b.imgFace)
                        .load(AssetCatalog.absoluteUrl(avatarUrl))
                        .override(128, 128)
                        .centerCrop()
                        .dontAnimate()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(ImagePlaceholder.avatar())
                        .error(ImagePlaceholder.avatar())
                        .into(holder.b.imgFace);
            }
        } else {
            Glide.with(holder.b.imgFace).clear(holder.b.imgFace);
            holder.b.imgFace.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        Glide.with(holder.b.imgPreview).clear(holder.b.imgPreview);
        Glide.with(holder.b.imgFace).clear(holder.b.imgFace);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static void ensureCircle(View view) {
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, Math.max(1, v.getWidth()), Math.max(1, v.getHeight()));
            }
        });
    }

    private static int dp(VH holder, int value) {
        return Math.round(value * holder.itemView.getResources().getDisplayMetrics().density);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }

    /** Always prefer a still PNG — never SVGA/GIF/WebP/MP4 in the mall grid. */
    private static String stillPngUrl(String preview, String anim) {
        String base = firstNonEmpty(preview, anim);
        if (base == null) return null;
        String lower = base.toLowerCase(Locale.US);
        if (lower.contains(".svga") || lower.contains(".gif")
                || lower.contains(".webp") || lower.contains(".mp4")
                || lower.contains(".webm") || lower.endsWith(".json")
                || lower.contains(".html")) {
            String png = base.replaceAll("(?i)\\.(svga|gif|webp|mp4|webm|json|html)(\\?.*)?$", ".png$1");
            if (!png.equals(base)) return png;
            // No PNG sibling — fall back to preview if it was already a still.
            if (preview != null && preview.toLowerCase(Locale.US).contains(".png")) return preview;
            return null;
        }
        return base;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCosmeticBinding b;
        VH(ItemCosmeticBinding b) { super(b.getRoot()); this.b = b; }
    }
}
