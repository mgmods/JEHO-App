package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GiftHistoryActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "gift_history_user_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGiftHistoryBinding binding = ActivityGiftHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AppContainer c = ContainerProvider.from(this);
        String profileId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (profileId == null || profileId.trim().isEmpty()) {
            profileId = c.getSessionManager().getUserId();
        }
        final String targetId = profileId == null ? "" : profileId.trim();

        List<GiftDtos.GiftReceivedSummaryDto> items = new ArrayList<>();
        RecyclerView.Adapter<VH> adapter = new RecyclerView.Adapter<>() {
            @NonNull @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(ItemGiftHistoryBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                GiftDtos.GiftReceivedSummaryDto item = items.get(position);
                holder.b.tvGiftName.setText(item.giftName == null || item.giftName.trim().isEmpty()
                        ? "هدية" : item.giftName);
                holder.b.tvGiftMeta.setText("× " + NumberFormat.getNumberInstance(Locale.US)
                        .format(Math.max(0L, item.quantity)) + " مرة");
                long support = Math.max(0L, item.diamondsAwarded);
                holder.b.tvCoins.setText(NumberFormat.getNumberInstance(Locale.US).format(support) + " 💎");
                Glide.with(holder.b.imgGift)
                        .load(AssetCatalog.absoluteUrl(item.giftIconUrl))
                        .placeholder(ImagePlaceholder.gift())
                        .error(ImagePlaceholder.gift())
                        .into(holder.b.imgGift);
            }

            @Override public int getItemCount() { return items.size(); }
        };

        GridLayoutManager grid = new GridLayoutManager(this, 4);
        binding.recycler.setLayoutManager(grid);
        binding.recycler.setAdapter(adapter);

        if (targetId.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<java.util.List<GiftDtos.GiftReceivedSummaryDto>> r =
                    ApiCall.execute(c.getGiftApi().receivedHistory(targetId));
            runOnUiThread(() -> {
                items.clear();
                if (r.success && r.data != null) items.addAll(r.data);
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
