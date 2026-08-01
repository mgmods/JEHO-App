package com.Dramizo.Series.presentation.gifts;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.databinding.ItemRvGiftListBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Binds Mikoo {@code item_rv_gift_list} cells — static icons, no marquee flicker. */
public class GiftAdapter extends ListAdapter<GiftDtos.GiftDto, GiftAdapter.VH> {
    public interface Listener { void onClick(GiftDtos.GiftDto gift); }

    private static final Object PAYLOAD_SELECT = new Object();

    @Nullable private Listener listener;
    private String selectedId;

    private static final DiffUtil.ItemCallback<GiftDtos.GiftDto> DIFF =
            new DiffUtil.ItemCallback<GiftDtos.GiftDto>() {
                @Override
                public boolean areItemsTheSame(@NonNull GiftDtos.GiftDto oldItem,
                                               @NonNull GiftDtos.GiftDto newItem) {
                    return stableKey(oldItem).equals(stableKey(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull GiftDtos.GiftDto oldItem,
                                                  @NonNull GiftDtos.GiftDto newItem) {
                    return Objects.equals(oldItem.name, newItem.name)
                            && Objects.equals(oldItem.iconUrl, newItem.iconUrl)
                            && Objects.equals(oldItem.animationUrl, newItem.animationUrl)
                            && oldItem.coinPrice == newItem.coinPrice
                            && oldItem.sortOrder == newItem.sortOrder;
                }
            };

    public GiftAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void submit(List<GiftDtos.GiftDto> data) {
        Map<String, GiftDtos.GiftDto> unique = new LinkedHashMap<>();
        if (data != null) {
            for (GiftDtos.GiftDto gift : data) {
                if (gift != null) unique.put(stableKey(gift), gift);
            }
        }
        submitList(new ArrayList<>(unique.values()));
    }

    public void setSelectedId(String id) {
        if (Objects.equals(selectedId, id)) return;
        int oldPosition = selectedPosition();
        selectedId = id;
        int newPosition = selectedPosition();
        if (oldPosition >= 0) notifyItemChanged(oldPosition, PAYLOAD_SELECT);
        if (newPosition >= 0 && newPosition != oldPosition) {
            notifyItemChanged(newPosition, PAYLOAD_SELECT);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRvGiftListBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        bindFull(holder, getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_SELECT)) {
            applySelection(holder, getItem(position));
            return;
        }
        bindFull(holder, getItem(position));
    }

    private void bindFull(@NonNull VH holder, @NonNull GiftDtos.GiftDto gift) {
        holder.b.tvGiftName.setText(gift.name != null ? gift.name : "");
        // Kill Mikoo marquee flicker — static ellipsize only.
        holder.b.tvGiftName.setSelected(false);
        holder.b.tvGiftName.setEllipsize(TextUtils.TruncateAt.END);
        holder.b.tvGiftName.setSingleLine(true);
        holder.b.tvGiftName.setHorizontallyScrolling(false);
        holder.b.giftGold.setText(String.valueOf(gift.coinPrice));
        applySelection(holder, gift);
        Glide.with(holder.b.giftImage)
                .load(AssetCatalog.absoluteUrl(gift.iconUrl))
                .placeholder(ImagePlaceholder.gift())
                .error(ImagePlaceholder.gift())
                .dontAnimate()
                .centerInside()
                .into(holder.b.giftImage);
        if (holder.b.ivGiftEffect != null) holder.b.ivGiftEffect.setVisibility(android.view.View.GONE);
        if (holder.b.ivGiftLuck != null) holder.b.ivGiftLuck.setVisibility(android.view.View.GONE);
        if (holder.b.ivGiftLimitTime != null) {
            holder.b.ivGiftLimitTime.setVisibility(android.view.View.GONE);
        }
        if (holder.b.ivGiftNew != null) holder.b.ivGiftNew.setVisibility(android.view.View.GONE);
        if (holder.b.giftAnimImage != null) {
            holder.b.giftAnimImage.setVisibility(android.view.View.GONE);
        }
        holder.itemView.setOnClickListener(v -> {
            setSelectedId(gift.id);
            if (listener != null) listener.onClick(gift);
        });
    }

    private void applySelection(@NonNull VH holder, @NonNull GiftDtos.GiftDto gift) {
        boolean selected = gift.id != null && gift.id.equals(selectedId);
        holder.b.relaData.setBackgroundResource(
                selected ? R.drawable.bg_gift_item_selected : 0);
    }

    @Override
    public long getItemId(int position) {
        String key = stableKey(getItem(position));
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private int selectedPosition() {
        if (selectedId == null) return RecyclerView.NO_POSITION;
        for (int i = 0; i < getCurrentList().size(); i++) {
            if (selectedId.equals(getCurrentList().get(i).id)) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private static String stableKey(GiftDtos.GiftDto gift) {
        if (gift.id != null && !gift.id.trim().isEmpty()) return "id:" + gift.id;
        if (gift.iconUrl != null && !gift.iconUrl.trim().isEmpty()) return "icon:" + gift.iconUrl;
        return "gift:" + String.valueOf(gift.name) + ':' + gift.coinPrice + ':' + gift.sortOrder;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemRvGiftListBinding b;
        VH(ItemRvGiftListBinding b) { super(b.getRoot()); this.b = b; }
    }
}
