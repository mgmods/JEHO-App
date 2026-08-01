package com.Dramizo.Series.presentation.contests;
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
import com.Dramizo.Series.data.remote.dto.ContestDtos;
import com.Dramizo.Series.databinding.ActivityContestsBinding;
import com.Dramizo.Series.databinding.ItemContestBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ApiCall;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContestsActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_AGENCY_ID = "agency_id";
    private ActivityContestsBinding binding;
    private AppContainer c;
    private final List<ContestDtos.ContestDto> items = new ArrayList<>();
    private Adapter adapter;
    private String roomId;
    private String agencyId;
    private final RealtimeClient.UserListener realtimeListener =
            new RealtimeClient.UserListener() {
                @Override
                public void onUserEvent(String event, JsonObject payload) {
                    if ("contest:score".equals(event)) runOnUiThread(() -> load());
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnOpenRanking.setVisibility(View.GONE);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        agencyId = getIntent().getStringExtra(EXTRA_AGENCY_ID);

        c = ContainerProvider.from(this);
        adapter = new Adapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        RealtimeClient.getInstance().addUserListener(realtimeListener);
    }

    @Override
    protected void onStop() {
        RealtimeClient.getInstance().removeUserListener(realtimeListener);
        super.onStop();
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<ContestDtos.ContestList> r = ApiCall.execute(
                    roomId != null || agencyId != null
                            ? c.getContestsApi().listScoped(roomId, agencyId)
                            : c.getContestsApi().list());
            runOnUiThread(() -> {
                items.clear();
                if (r.success && r.data != null && r.data.items != null) items.addAll(r.data.items);
                adapter.notifyDataSetChanged();
                if (items.isEmpty()) {
                    Toast.makeText(this, "لا مسابقات حالياً", Toast.LENGTH_SHORT).show();
                }
                if (r.error != null) Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void join(ContestDtos.ContestDto contest) {
        if (contest == null || contest.id == null) return;
        if (contest.joined) {
            Toast.makeText(this, "أنت منضم مسبقاً", Toast.LENGTH_SHORT).show();
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getContestsApi().join(contest.id));
            runOnUiThread(() -> {
                if (r.success) {
                    contest.joined = true;
                    contest.entrantsCount++;
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "تم الانضمام ✓", Toast.LENGTH_SHORT).show();
                } else {
                    com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                }
            });
        });
    }

    private static String formatCountdown(ContestDtos.ContestDto c) {
        if (c == null) return "";
        String raw = "ended".equals(c.status) ? null : c.endAt;
        if (raw == null || raw.isEmpty()) {
            if ("upcoming".equals(c.status) && c.startAt != null) raw = c.startAt;
            else return "—";
        }
        try {
            long target = java.time.Instant.parse(raw).toEpochMilli();
            long diff = target - System.currentTimeMillis();
            if (diff <= 0) return "active".equals(c.status) ? "تنتهي الآن" : "بدأت";
            long hours = diff / 3_600_000L;
            long mins = (diff % 3_600_000L) / 60_000L;
            if ("upcoming".equals(c.status)) {
                return hours >= 24
                        ? String.format(Locale.US, "تبدأ خلال %d يوم", hours / 24)
                        : String.format(Locale.US, "تبدأ خلال %dس %dد", hours, mins);
            }
            return hours >= 24
                    ? String.format(Locale.US, "متبقي %d يوم و%d ساعة", hours / 24, hours % 24)
                    : String.format(Locale.US, "متبقي %dس %dد", hours, mins);
        } catch (Exception e) {
            return "";
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemContestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ContestDtos.ContestDto c = items.get(position);
            h.b.tvTitle.setText(c.title != null ? c.title : "مسابقة");
            h.b.tvDesc.setText(c.description != null ? c.description : "");
            String status = "active".equals(c.status) ? "جارية"
                    : "upcoming".equals(c.status) ? "قادمة" : "منتهية";
            if (h.b.tvStatusChip != null) h.b.tvStatusChip.setText(status);
            String fee = c.entryFeeCoins > 0
                    ? String.format(Locale.US, "رسوم %,d كوينز", c.entryFeeCoins)
                    : "مجانية";
            String prize = c.prizeLabel != null ? c.prizeLabel
                    : String.format(Locale.US, "جائزة %,d", c.prizeCoins);
            h.b.tvMeta.setText(fee + " · " + prize + " · " + c.entrantsCount + " مشارك");
            if (h.b.tvCountdown != null) {
                h.b.tvCountdown.setText(formatCountdown(c));
            }
            h.itemView.setOnClickListener(v ->
                    startActivity(ContestDetailActivity.intent(ContestsActivity.this, c.id)));
            if (c.joined) {
                h.b.btnJoin.setText("الترتيب");
                h.b.btnJoin.setEnabled(true);
                h.b.btnJoin.setOnClickListener(v ->
                        startActivity(ContestDetailActivity.intent(ContestsActivity.this, c.id)));
            } else if (!"active".equals(c.status)) {
                h.b.btnJoin.setText(status);
                h.b.btnJoin.setEnabled(false);
                h.b.btnJoin.setOnClickListener(null);
            } else {
                h.b.btnJoin.setText("انضمام");
                h.b.btnJoin.setEnabled(true);
                h.b.btnJoin.setOnClickListener(v -> join(c));
            }
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            final ItemContestBinding b;
            VH(ItemContestBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
