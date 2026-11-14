package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.databinding.ActivityVisitorsBinding;
import com.Dramizo.Series.databinding.ItemProfileSupporterBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full list of accounts that supported a profile through gifts. */
public class SupportersActivity extends ThemedActivity {
    private ActivityVisitorsBinding binding;
    private final List<GiftDtos.GiftSupporterDto> items = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisitorsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        setHeaderTitle("الداعمون");

        String userId = getIntent().getStringExtra(ProfileActivity.EXTRA_USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            finish();
            return;
        }

        RecyclerView recycler = binding.recycler;
        recycler.setLayoutManager(new LinearLayoutManager(this));
        RecyclerView.Adapter<Holder> adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                return new Holder(ItemProfileSupporterBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
                GiftDtos.GiftSupporterDto item = items.get(position);
                holder.binding.tvSupporterName.setText(item.displayName == null || item.displayName.trim().isEmpty()
                        ? (item.username == null ? "مستخدم" : item.username) : item.displayName);
                holder.binding.tvSupporterAmount.setText("💎 " + NumberFormat.getNumberInstance(Locale.US)
                        .format(Math.max(0L, item.totalSupport)));
                AvatarCosmetics.bindStacked(holder.binding.imgSupporterAvatar,
                        holder.binding.imgSupporterFrame, holder.binding.supporterHostSignal,
                        item.avatarUrl, item.frameUrl, null);
                holder.itemView.setOnClickListener(v -> {
                    if (item.id == null || item.id.trim().isEmpty()) return;
                    Intent intent = new Intent(SupportersActivity.this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER_ID, item.id);
                    startActivity(intent);
                });
            }
            @Override public int getItemCount() { return items.size(); }
        };
        recycler.setAdapter(adapter);
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Result<List<GiftDtos.GiftSupporterDto>> result =
                    ApiCall.execute(container.getGiftApi().supporters(userId.trim()));
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()) return;
                items.clear();
                if (result.success && result.data != null) items.addAll(result.data);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                binding.tvEmpty.setText("لا يوجد داعمون حتى الآن");
            });
        });
    }

    private void setHeaderTitle(String title) {
        try {
            ViewGroup header = (ViewGroup) binding.btnBack.getParent();
            for (int i = 0; i < header.getChildCount(); i++) {
                View child = header.getChildAt(i);
                if (child instanceof TextView && child.getId() != binding.btnBack.getId()) {
                    ((TextView) child).setText(title);
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ItemProfileSupporterBinding binding;
        Holder(ItemProfileSupporterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
