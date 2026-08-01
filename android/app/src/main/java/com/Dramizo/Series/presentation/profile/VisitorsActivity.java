package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityVisitorsBinding;
import com.Dramizo.Series.databinding.ItemFriendRowBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class VisitorsActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityVisitorsBinding binding = ActivityVisitorsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AppContainer c = ContainerProvider.from(this);
        List<AuthDtos.UserDto> items = new ArrayList<>();
        RecyclerView.Adapter<VH> adapter = new RecyclerView.Adapter<>() {
            @NonNull @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(ItemFriendRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                AuthDtos.UserDto u = items.get(position);
                holder.b.tvName.setText(u.displayName != null ? u.displayName : u.username);
                holder.b.tvId.setText(u.displayPublicId().isEmpty() ? "" : ("ID: " + u.displayPublicId()));
                AvatarCosmetics.bindWear(holder.b.imgAvatar, holder.b.imgFrame, u);
                holder.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(VisitorsActivity.this, ProfileActivity.class);
                    i.putExtra(ProfileActivity.EXTRA_USER_ID, u.id);
                    startActivity(i);
                });
            }
            @Override public int getItemCount() { return items.size(); }
        };
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> r = ApiCall.execute(c.getUserApi().visitors(1));
            runOnUiThread(() -> {
                items.clear();
                if (r.success && r.data != null && r.data.items != null) items.addAll(r.data.items);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (r.error != null) Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private static class VH extends RecyclerView.ViewHolder {
        final ItemFriendRowBinding b;
        VH(ItemFriendRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
