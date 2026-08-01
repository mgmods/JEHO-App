package com.Dramizo.Series.presentation.contests;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.ContestDtos;
import com.Dramizo.Series.databinding.ActivityContestDetailBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContestDetailActivity extends ThemedActivity {
    public static final String EXTRA_CONTEST_ID = "contest_id";

    private ActivityContestDetailBinding binding;
    private AppContainer c;
    private String contestId;
    private final List<ContestDtos.LeaderRow> rows = new ArrayList<>();
    private BoardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContestDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        c = ContainerProvider.from(this);
        contestId = getIntent().getStringExtra(EXTRA_CONTEST_ID);
        adapter = new BoardAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.btnJoin.setOnClickListener(v -> join());
        if (contestId == null || contestId.isEmpty()) {
            Toast.makeText(this, "مسابقة غير صالحة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        load();
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<ContestDtos.ContestDetail> r = ApiCall.execute(c.getContestsApi().get(contestId));
            runOnUiThread(() -> {
                if (!r.success || r.data == null || r.data.contest == null) {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                ContestDtos.ContestDto c0 = r.data.contest;
                binding.tvTitle.setText(c0.title != null ? c0.title : "مسابقة");
                binding.tvDesc.setText(c0.description != null ? c0.description : "");
                String status = "active".equals(c0.status) ? "جارية"
                        : "upcoming".equals(c0.status) ? "قادمة" : "منتهية";
                String prize = c0.prizeLabel != null ? c0.prizeLabel
                        : String.format(Locale.US, "جائزة %,d", c0.prizeCoins);
                binding.tvMeta.setText(status + " · " + prize + " · " + c0.entrantsCount + " مشارك");
                if (c0.joined) {
                    binding.btnJoin.setText("منضم ✓");
                    binding.btnJoin.setEnabled(false);
                } else if (!"active".equals(c0.status)) {
                    binding.btnJoin.setText(status);
                    binding.btnJoin.setEnabled(false);
                } else {
                    binding.btnJoin.setText("انضمام");
                    binding.btnJoin.setEnabled(true);
                }
                rows.clear();
                if (r.data.leaderboard != null) rows.addAll(r.data.leaderboard);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void join() {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getContestsApi().join(contestId));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, "تم الانضمام ✓", Toast.LENGTH_SHORT).show();
                    load();
                } else {
                    com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                }
            });
        });
    }

    private class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_contest_leader, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ContestDtos.LeaderRow row = rows.get(position);
            h.tvRank.setText("#" + row.rank);
            h.tvName.setText(row.displayName != null ? row.displayName : "مستخدم");
            h.tvScore.setText(String.format(Locale.US, "%,d", row.score));
            android.widget.ImageView imgFrame = h.itemView.findViewById(R.id.imgFrame);
            AvatarCosmetics.bindWear(h.imgAvatar, imgFrame, row.avatarUrl, null, row.hostBadgeUrl);
        }
        @Override public int getItemCount() { return rows.size(); }
        class VH extends RecyclerView.ViewHolder {
            final TextView tvRank, tvName, tvScore;
            final android.widget.ImageView imgAvatar;
            VH(View v) {
                super(v);
                tvRank = v.findViewById(R.id.tvRank);
                tvName = v.findViewById(R.id.tvName);
                tvScore = v.findViewById(R.id.tvScore);
                imgAvatar = v.findViewById(R.id.imgAvatar);
            }
        }
    }

    public static Intent intent(android.content.Context ctx, String contestId) {
        Intent i = new Intent(ctx, ContestDetailActivity.class);
        i.putExtra(EXTRA_CONTEST_ID, contestId);
        return i;
    }
}
