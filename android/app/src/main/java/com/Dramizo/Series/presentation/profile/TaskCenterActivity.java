package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityTaskCenterBinding;
import com.Dramizo.Series.databinding.ItemDailyTaskBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.RewardBurstOverlay;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Full-screen task center — real claim against /tasks/daily API. */
public class TaskCenterActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";
    public static final String EXTRA_AGENCY_ID = "agency_id";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_IN_PROGRESS = 1;
    private static final int FILTER_NOT_STARTED = 2;

    private TaskAdapter adapter;
    private AppContainer c;
    private ActivityTaskCenterBinding binding;
    private String roomId;
    private String agencyId;
    private int taskFilter = FILTER_ALL;
    private List<MiscDtos.TaskDto> allTasks = new ArrayList<>();
    private final RealtimeClient.UserListener realtimeListener =
            new RealtimeClient.UserListener() {
                @Override
                public void onUserEvent(String event, JsonObject payload) {
                    if ("task:progress".equals(event)) runOnUiThread(() -> load());
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTaskCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        agencyId = getIntent().getStringExtra(EXTRA_AGENCY_ID);
        binding.btnBack.setOnClickListener(v -> navigateUp());

        adapter = new TaskAdapter(this::claim, this::checkIn, this::watchRewardedAd);
        binding.recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTasks.setAdapter(adapter);
        // Warm AdMob rewarded unit for ad_* tasks.
        try {
            com.Dramizo.Series.presentation.games.GameAdsHelper ads =
                    com.Dramizo.Series.presentation.games.GameAdsHelper.get(this);
            ads.refreshConfig(this);
            ads.preload(this);
        } catch (Exception ignored) {
        }
        if (binding.chipFilterAll != null) {
            binding.chipFilterAll.setOnClickListener(v -> setTaskFilter(FILTER_ALL));
        }
        if (binding.chipFilterInProgress != null) {
            binding.chipFilterInProgress.setOnClickListener(v -> setTaskFilter(FILTER_IN_PROGRESS));
        }
        if (binding.chipFilterNotStarted != null) {
            binding.chipFilterNotStarted.setOnClickListener(v -> setTaskFilter(FILTER_NOT_STARTED));
        }
        syncFilterChips();
        loadHeader();
        load();
    }

    private void setTaskFilter(int filter) {
        if (taskFilter == filter) return;
        taskFilter = filter;
        syncFilterChips();
        applyTaskFilter();
    }

    private void syncFilterChips() {
        if (binding == null) return;
        if (binding.chipFilterAll != null) {
            binding.chipFilterAll.setBackgroundResource(
                    taskFilter == FILTER_ALL
                            ? R.drawable.bg_task_filter_selected : R.drawable.bg_task_item);
        }
        if (binding.chipFilterInProgress != null) {
            binding.chipFilterInProgress.setBackgroundResource(
                    taskFilter == FILTER_IN_PROGRESS
                            ? R.drawable.bg_task_filter_selected : R.drawable.bg_task_item);
        }
        if (binding.chipFilterNotStarted != null) {
            binding.chipFilterNotStarted.setBackgroundResource(
                    taskFilter == FILTER_NOT_STARTED
                            ? R.drawable.bg_task_filter_selected : R.drawable.bg_task_item);
        }
    }

    private void applyTaskFilter() {
        List<MiscDtos.TaskDto> filtered = new ArrayList<>();
        for (MiscDtos.TaskDto task : allTasks) {
            if (task == null) continue;
            if (taskFilter == FILTER_ALL) {
                filtered.add(task);
            } else if (taskFilter == FILTER_IN_PROGRESS) {
                if (!task.claimed && (task.claimable || task.current > 0)) filtered.add(task);
            } else if (taskFilter == FILTER_NOT_STARTED) {
                if (!task.claimed && !task.claimable && task.current <= 0) filtered.add(task);
            }
        }
        adapter.submit(filtered);
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

    private void checkIn() {
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().checkin());
            runOnUiThread(() -> {
                if (r.success) {
                    RewardBurstOverlay.showCheckIn(this);
                    load();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Show AdMob rewarded video, then POST progress for ad_1 / ad_3 / ad_5. */
    private void watchRewardedAd() {
        com.Dramizo.Series.presentation.games.GameAdsHelper ads =
                com.Dramizo.Series.presentation.games.GameAdsHelper.get(this);
        ads.refreshConfig(this);
        ads.showRewardedForTask(this, new com.Dramizo.Series.presentation.games.GameAdsHelper.TaskWatchCallback() {
            @Override
            public void onPreparing() {
                Toast.makeText(TaskCenterActivity.this, "جارٍ تحميل الإعلان…", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFinished(boolean earned, @androidx.annotation.Nullable String message) {
                if (!earned) {
                    Toast.makeText(
                            TaskCenterActivity.this,
                            message != null ? message : "أكمل مشاهدة الإعلان",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                c.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().watchRewardedAd());
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(TaskCenterActivity.this, "تم تسجيل المشاهدة ✓", Toast.LENGTH_SHORT).show();
                            load();
                            loadHeader();
                        } else {
                            Toast.makeText(
                                    TaskCenterActivity.this,
                                    r.error != null ? r.error : getString(R.string.error_generic),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
            }
        });
    }

    private void loadHeader() {
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
            Result<WalletDtos.WalletDto> wallet = c.getWalletUseCase.execute();
            Result<Map<String, Object>> hostTarget = ApiCall.execute(c.getUserApi().hostTargetMe());
            runOnUiThread(() -> {
                if (binding == null) return;
                if (me.success && me.data != null) {
                    binding.tvDisplayName.setText(
                            me.data.displayName != null && !me.data.displayName.isEmpty()
                                    ? me.data.displayName
                                    : (me.data.username != null ? me.data.username : getString(R.string.app_name)));
                }
                long points = wallet.success && wallet.data != null ? wallet.data.coins : 0;
                binding.tvPointsSummary.setText(points + " كوينز");
                bindHostMonthlyTarget(hostTarget);
            });
        });
    }

    private void bindHostMonthlyTarget(Result<Map<String, Object>> hostTarget) {
        // Monthly host target is shown in Wallet only — never cover daily tasks here.
        if (binding.sectionHostMonthlyTarget != null) {
            binding.sectionHostMonthlyTarget.setVisibility(android.view.View.GONE);
        }
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.TaskDto>> r = ApiCall.execute(
                    roomId != null || agencyId != null
                            ? c.getTasksApi().dailyScoped(roomId, agencyId)
                            : c.getTasksApi().daily());
            runOnUiThread(() -> {
                if (r.success && r.data != null) {
                    allTasks = new ArrayList<>(r.data);
                    int all = r.data.size();
                    int claimed = 0;
                    int inProgress = 0;
                    int notStarted = 0;
                    for (MiscDtos.TaskDto task : r.data) {
                        if (task == null) continue;
                        if (task.claimed) claimed++;
                        else if (task.claimable) inProgress++;
                        else if (task.current > 0) inProgress++;
                        else notStarted++;
                    }
                    binding.tvAllCount.setText(String.valueOf(all));
                    binding.tvInProgressCount.setText(String.valueOf(inProgress));
                    binding.tvNotStartedCount.setText(String.valueOf(notStarted));
                    applyTaskFilter();
                    if (all == 0) Toast.makeText(this, getString(R.string.no_data), Toast.LENGTH_SHORT).show();
                }
                else Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void claim(MiscDtos.TaskDto task) {
        if (task.claimed) {
            Toast.makeText(this, "تم الاستلام مسبقاً", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!task.claimable) {
            Toast.makeText(this, "أكمل المهمة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().claim(task.id));
            Result<AuthDtos.UserDto> me = null;
            if (r.success) me = c.getUserRepository().getMe();
            final Result<AuthDtos.UserDto> meResult = me;
            runOnUiThread(() -> {
                if (r.success) {
                    RewardBurstOverlay.showForTask(this, task);
                    if (meResult != null && meResult.success && meResult.data != null) {
                        c.getSessionManager().updateCachedUser(meResult.data);
                    }
                    loadHeader();
                    load();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static class Row {
        static final int TYPE_SECTION = 0;
        static final int TYPE_TASK = 1;
        final int type;
        final String sectionTitle;
        final MiscDtos.TaskDto task;
        Row(String sectionTitle) {
            this.type = TYPE_SECTION;
            this.sectionTitle = sectionTitle;
            this.task = null;
        }
        Row(MiscDtos.TaskDto task) {
            this.type = TYPE_TASK;
            this.sectionTitle = null;
            this.task = task;
        }
    }

    private static class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface ClaimListener { void onClaim(MiscDtos.TaskDto task); }
        private final List<Row> rows = new ArrayList<>();
        private final ClaimListener claimListener;
        private final Runnable checkInListener;
        private final Runnable watchAdListener;
        TaskAdapter(ClaimListener claimListener, Runnable checkInListener, Runnable watchAdListener) {
            this.claimListener = claimListener;
            this.checkInListener = checkInListener;
            this.watchAdListener = watchAdListener;
        }
        void submit(List<MiscDtos.TaskDto> data) {
            rows.clear();
            if (data == null || data.isEmpty()) {
                notifyDataSetChanged();
                return;
            }
            List<MiscDtos.TaskDto> daily = new ArrayList<>();
            List<MiscDtos.TaskDto> host = new ArrayList<>();
            for (MiscDtos.TaskDto t : data) {
                if (t == null) continue;
                if (t.isHostTask()) host.add(t);
                else daily.add(t);
            }
            if (!daily.isEmpty()) {
                rows.add(new Row("المهام اليومية"));
                for (MiscDtos.TaskDto t : daily) rows.add(new Row(t));
            }
            if (!host.isEmpty()) {
                rows.add(new Row("مهام المضيفة · مكافآت كوينز"));
                for (MiscDtos.TaskDto t : host) rows.add(new Row(t));
            }
            notifyDataSetChanged();
        }
        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == Row.TYPE_SECTION) {
                return new SectionVH(inf.inflate(R.layout.item_task_section, parent, false));
            }
            return new VH(ItemDailyTaskBinding.inflate(inf, parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof SectionVH) {
                ((SectionVH) holder).title.setText(row.sectionTitle);
                return;
            }
            VH h = (VH) holder;
            MiscDtos.TaskDto t = row.task;
            h.b.tvTitle.setText(t.title);
            String progress = t.progressLabel != null && !t.progressLabel.isEmpty()
                    ? t.progressLabel
                    : (t.current + "/" + Math.max(1, t.target));
            long rewardCoins = Math.max(0, t.rewardPoints) + Math.max(0, t.rewardSilver);
            String reward = progress + (rewardCoins > 0 ? (" · " + rewardCoins + " كوينز") : "");
            if (!t.isHostTask() && t.rewardDiamonds > 0) reward += " · " + t.rewardDiamonds + " ماس";
            h.b.tvReward.setText(reward);

            int target = Math.max(1, t.target);
            int current = Math.max(0, Math.min(t.current, target));
            h.b.progressTask.setVisibility(android.view.View.VISIBLE);
            h.b.progressTask.setMax(target);
            h.b.progressTask.setProgress(current);

            String type = t.type != null ? t.type.toLowerCase() : "";
            boolean isCheckin = type.startsWith("checkin") || type.startsWith("host_checkin");
            boolean isAdTask = type.startsWith("ad_")
                    || type.startsWith("admob")
                    || type.startsWith("watch_ad")
                    || type.startsWith("rewarded_ad")
                    || "ad".equals(type)
                    || "admob".equals(type);
            if (h.b.imgTaskIcon != null) {
                int icon = R.drawable.ic_asset_tasks;
                if (isCheckin) icon = R.drawable.ic_asset_chest_gold;
                else if (isAdTask) icon = R.drawable.ic_asset_coin_gold;
                else if (t.claimed) icon = R.drawable.ic_asset_gift;
                else if (t.isHostTask()) icon = R.drawable.ic_asset_diamond;
                else if (t.claimable) icon = R.drawable.ic_asset_coin_gold;
                h.b.imgTaskIcon.setImageResource(icon);
            }
            if (h.b.tvTaskIcon != null) h.b.tvTaskIcon.setVisibility(android.view.View.GONE);

            if (t.claimed) {
                h.b.btnClaim.setText("✓ تم");
                h.b.btnClaim.setEnabled(false);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF3A3A48));
                h.b.btnClaim.setTextColor(0xFFB8B8C8);
                h.b.btnClaim.setOnClickListener(null);
            } else if (t.claimable) {
                h.b.btnClaim.setText(R.string.claim);
                h.b.btnClaim.setEnabled(true);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                h.itemView.getContext().getColor(R.color.aurora_gold)));
                h.b.btnClaim.setTextColor(0xFF1A1208);
                h.b.btnClaim.setOnClickListener(v -> claimListener.onClaim(t));
            } else if (isCheckin) {
                h.b.btnClaim.setText("حضور");
                h.b.btnClaim.setEnabled(true);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                h.itemView.getContext().getColor(R.color.aurora_teal)));
                h.b.btnClaim.setTextColor(0xFFFFFFFF);
                h.b.btnClaim.setOnClickListener(v -> checkInListener.run());
            } else if (isAdTask) {
                h.b.btnClaim.setText("مشاهدة إعلان");
                h.b.btnClaim.setEnabled(true);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                h.itemView.getContext().getColor(R.color.aurora_teal)));
                h.b.btnClaim.setTextColor(0xFFFFFFFF);
                h.b.btnClaim.setOnClickListener(v -> {
                    if (watchAdListener != null) watchAdListener.run();
                });
            } else {
                h.b.btnClaim.setText("أكمل أولاً");
                h.b.btnClaim.setEnabled(false);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF2A2A38));
                h.b.btnClaim.setTextColor(0xFFE8C96A);
                h.b.btnClaim.setOnClickListener(null);
            }
        }
        @Override public int getItemCount() { return rows.size(); }
        static class VH extends RecyclerView.ViewHolder {
            final ItemDailyTaskBinding b;
            VH(ItemDailyTaskBinding b) { super(b.getRoot()); this.b = b; }
        }
        static class SectionVH extends RecyclerView.ViewHolder {
            final TextView title;
            SectionVH(android.view.View v) {
                super(v);
                title = v.findViewById(R.id.tvSectionTitle);
            }
        }
    }
}
