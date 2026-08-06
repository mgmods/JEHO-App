package com.Dramizo.Series.presentation.cosmetics;

import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.databinding.ItemCosmeticBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.CosmeticMedia;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mall grid adapter — static cell previews only (PNG/WebP/GIF).
 * Selection drives the activity hero ({@link com.Dramizo.Series.util.HostSignalView}) —
 * never decode SVGA/MP4 in the grid.
 */
public class CosmeticsAdapter extends RecyclerView.Adapter<CosmeticsAdapter.VH> {
    public interface Listener {
        void onSelect(CosmeticDtos.CosmeticDto item);
        void onPurchase(String cosmeticId);
        void onEquip(String cosmeticId);
        void onUnequip(String cosmeticId);
        boolean isOwned(String cosmeticId);
        boolean isEquipped(String cosmeticId);
        @Nullable Integer daysLeft(String cosmeticId);
        @Nullable String selectedId();
        /** Active paid VIP plan (1–100). */
        int myVipLevel();
        /** Account level (not VIP). Used only for non-frame gates. */
        int myUserLevel();
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
        boolean owned = listener.isOwned(item.id);
        boolean equipped = listener.isEquipped(item.id);
        Integer days = listener.daysLeft(item.id);
        int myVip = Math.max(0, listener.myVipLevel());
        int needVip = Math.max(0, item.minVipLevel);
        boolean vipLocked = !owned && needVip > 0 && myVip < needVip;
        // Frames/host wear: VIP-only; ignore minUserLevel (account level) for display locks.
        boolean isFrame = isHeadwear(item.type);
        int needLvl = isFrame ? 0 : Math.max(0, item.minUserLevel);
        int myLvl = Math.max(1, listener.myUserLevel());
        boolean levelLocked = !owned && !vipLocked && needLvl > 0 && myLvl < needLvl;
        boolean locked = vipLocked || levelLocked;

        if (owned && days != null && days > 0) {
            holder.b.tvPrice.setText(holder.itemView.getContext()
                    .getString(R.string.mall_days_left, days));
        } else if (owned && days == null) {
            holder.b.tvPrice.setText(R.string.mall_permanent);
        } else if (vipLocked) {
            holder.b.tvPrice.setText(holder.itemView.getContext()
                    .getString(R.string.mall_requires_vip, needVip));
        } else if (levelLocked) {
            holder.b.tvPrice.setText(holder.itemView.getContext()
                    .getString(R.string.mall_requires_level, needLvl));
        } else if (needVip > 0 && item.coinPrice <= 0) {
            holder.b.tvPrice.setText(holder.itemView.getContext()
                    .getString(R.string.mall_vip_unlock, needVip));
        } else {
            holder.b.tvPrice.setText(item.coinPrice > 0
                    ? holder.itemView.getContext().getString(
                            R.string.mall_price_days, item.coinPrice, CosmeticsViewModel.LEASE_DAYS)
                    : holder.itemView.getContext().getString(R.string.mall_free_days, CosmeticsViewModel.LEASE_DAYS));
        }

        if (holder.b.tvVipLock != null) {
            if (needVip > 0) {
                holder.b.tvVipLock.setVisibility(View.VISIBLE);
                holder.b.tvVipLock.setText(holder.itemView.getContext()
                        .getString(R.string.mall_vip_badge, needVip));
                holder.b.tvVipLock.setAlpha(vipLocked ? 1f : 0.85f);
            } else {
                holder.b.tvVipLock.setVisibility(View.GONE);
            }
        }

        String sel = listener.selectedId();
        boolean selected = sel != null && sel.equals(item.id);
        holder.b.getRoot().setStrokeWidth(selected ? 3 : 1);
        holder.b.getRoot().setStrokeColor(selected ? 0xFFE8A317 : 0x1A000000);
        holder.b.getRoot().setAlpha(locked ? 0.72f : 1f);

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

        if (!owned) {
            if (locked) {
                holder.b.btnPurchase.setText(vipLocked
                        ? holder.itemView.getContext().getString(R.string.mall_requires_vip, needVip)
                        : holder.itemView.getContext().getString(R.string.mall_requires_level, needLvl));
                holder.b.btnPurchase.setAlpha(0.85f);
            } else {
                holder.b.btnPurchase.setText(R.string.purchase);
                holder.b.btnPurchase.setAlpha(1f);
            }
        }

        holder.b.btnPurchase.setOnClickListener(v -> {
            if (locked) {
                listener.onPurchase(item.id); // ViewModel shows VIP/level message
                return;
            }
            listener.onPurchase(item.id);
        });
        holder.b.btnEquip.setOnClickListener(v -> {
            if (equipped) listener.onUnequip(item.id);
            else listener.onEquip(item.id);
        });
        holder.itemView.setOnClickListener(v -> listener.onSelect(item));

        boolean headwear = isFrame;
        String still = stillPreviewUrl(item.previewUrl, item.animationUrl);
        String abs = AssetCatalog.absoluteUrl(still);

        holder.b.imgPreview.setVisibility(View.VISIBLE);
        holder.b.imgPreview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        holder.b.imgPreview.setPadding(dp(holder, headwear ? 4 : 4), dp(holder, headwear ? 4 : 4),
                dp(holder, headwear ? 4 : 4), dp(holder, headwear ? 4 : 4));

        if (abs == null || abs.isEmpty()) {
            holder.b.imgPreview.setImageResource(ImagePlaceholder.cover());
        } else {
            Glide.with(holder.b.imgPreview.getContext().getApplicationContext())
                    .load(abs)
                    .override(320, 320)
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
                Glide.with(holder.b.imgFace.getContext().getApplicationContext())
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
            Glide.with(holder.b.imgFace.getContext().getApplicationContext()).clear(holder.b.imgFace);
            holder.b.imgFace.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        try {
            Glide.with(holder.b.imgPreview.getContext().getApplicationContext()).clear(holder.b.imgPreview);
            Glide.with(holder.b.imgFace.getContext().getApplicationContext()).clear(holder.b.imgFace);
        } catch (Exception ignored) {
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static boolean isHeadwear(@Nullable String type) {
        if (type == null) return false;
        return "vip_badge".equalsIgnoreCase(type)
                || "host_badge".equalsIgnoreCase(type)
                || "frames".equalsIgnoreCase(type);
    }

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

    /**
     * Grid preview URL: prefer static still; allow WebP/GIF (Glide handles);
     * convert SVGA/video to sibling .png when possible — never invent broken ".pngwebp".
     */
    @Nullable
    static String stillPreviewUrl(@Nullable String preview, @Nullable String anim) {
        String preferPreview = firstNonEmpty(preview, null);
        if (preferPreview != null && isStaticStill(preferPreview)) return preferPreview;

        String base = firstNonEmpty(preview, anim);
        if (base == null) return null;
        CosmeticMedia.Kind kind = CosmeticMedia.kind(base);
        if (kind == CosmeticMedia.Kind.IMAGE || kind == CosmeticMedia.Kind.GIF) {
            return base;
        }
        if (kind == CosmeticMedia.Kind.VIDEO || kind == CosmeticMedia.Kind.SVGA) {
            String png = base.replaceAll(
                    "(?i)\\.(svga|mp4|webm|mov|html)(\\?.*)?$", ".png$2");
            if (!png.equals(base) && isStaticStill(png)) return png;
            if (preferPreview != null && isStaticStill(preferPreview)) return preferPreview;
            return null;
        }
        // Unknown CDN path without extension — try as image.
        return base;
    }

    private static boolean isStaticStill(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".svga") || lower.contains(".mp4") || lower.contains(".webm")
                || lower.contains(".mov") || lower.contains(".html") || lower.endsWith(".json")) {
            return false;
        }
        CosmeticMedia.Kind k = CosmeticMedia.kind(url);
        return k == CosmeticMedia.Kind.IMAGE || k == CosmeticMedia.Kind.GIF
                || k == CosmeticMedia.Kind.NONE;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCosmeticBinding b;
        VH(ItemCosmeticBinding b) { super(b.getRoot()); this.b = b; }
    }
}
