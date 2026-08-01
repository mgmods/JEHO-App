package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityGiftHistoryBinding;
import com.Dramizo.Series.databinding.ItemGiftHistoryBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GiftHistoryActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGiftHistoryBinding binding = ActivityGiftHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AppContainer c = ContainerProvider.from(this);
        List<GiftDtos.GiftHistoryDto> items = new ArrayList<>();
        RecyclerView.Adapter<VH> adapter = new RecyclerView.Adapter<>() {
            @NonNull @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(ItemGiftHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                GiftDtos.GiftHistoryDto h = items.get(position);
                String giftName = h.gift != null ? h.gift.name : "هدية";
                String from = h.sender != null ? h.sender.displayName : "?";
                String to = h.receiver != null ? h.receiver.displayName : "?";
                holder.b.tvGiftName.setText(giftName + " ×" + Math.max(1, h.quantity));
                holder.b.tvGiftMeta.setText("من " + from + " → " + to
                        + (h.displayCoins() > 0 ? " · " + h.displayCoins() + " عملة" : ""));
                long diamonds = h.diamondsAwarded > 0 ? h.diamondsAwarded : h.displayCoins();
                holder.b.tvCoins.setText(String.format(Locale.US, "%,d 💎", diamonds));
                String icon = h.gift != null ? h.gift.iconUrl : null;
                Glide.with(holder.b.imgGift)
                        .load(AssetCatalog.absoluteUrl(icon))
                        .placeholder(ImagePlaceholder.gift())
                        .error(ImagePlaceholder.gift())
                        .into(holder.b.imgGift);
            }
            @Override public int getItemCount() { return items.size(); }
        };
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<GiftDtos.GiftHistoryDto>> r = ApiCall.execute(c.getGiftApi().history(1));
            runOnUiThread(() -> {
                items.clear();
                if (r.success && r.data != null && r.data.items != null) items.addAll(r.data.items);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (r.error != null) Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private static final class VH extends RecyclerView.ViewHolder {
        final ItemGiftHistoryBinding b;
        VH(ItemGiftHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
