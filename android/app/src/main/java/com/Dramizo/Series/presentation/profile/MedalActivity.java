package com.Dramizo.Series.presentation.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Honour/medals — VIP 1–7 only, light JEHO chrome. */
public class MedalActivity extends ThemedActivity {
    private static final int MAX_VIP = 7;

    private RecyclerView rv;
    private View empty;
    private TextView emptyText;
    private TextView tabBadge;
    private TextView tabMine;
    private final List<MedalRow> catalog = new ArrayList<>();
    private final List<MedalRow> mine = new ArrayList<>();
    private boolean showingMine;

    @Override
    protected boolean wantsRemoteThemeChrome() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medal);
        findViewById(R.id.vBack).setOnClickListener(v -> navigateUp());
        rv = findViewById(R.id.rvMedals);
        empty = findViewById(R.id.llEmpty);
        emptyText = findViewById(R.id.tvEmptyMedal);
        tabBadge = findViewById(R.id.tvBadgeTab);
        tabMine = findViewById(R.id.tvMineTab);
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setAdapter(new Adapter());

        tabBadge.setOnClickListener(v -> showTab(false));
        tabMine.setOnClickListener(v -> showTab(true));
        showTab(false);
        loadMedals();
    }

    private void showTab(boolean mineTab) {
        showingMine = mineTab;
        if (tabBadge != null) {
            tabBadge.setBackgroundResource(mineTab ? 0 : R.drawable.bg_wallet_channel_on);
            tabBadge.setTextColor(mineTab ? getColor(R.color.text_secondary) : 0xFF1A1200);
        }
        if (tabMine != null) {
            tabMine.setBackgroundResource(mineTab ? R.drawable.bg_wallet_channel_on : 0);
            tabMine.setTextColor(mineTab ? 0xFF1A1200 : getColor(R.color.text_secondary));
        }
        bindList();
    }

    private void bindList() {
        List<MedalRow> rows = showingMine ? mine : catalog;
        boolean emptyList = rows == null || rows.isEmpty();
        if (empty != null) empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);
        if (emptyText != null && emptyList) {
            emptyText.setText(showingMine
                    ? R.string.have_not_yet_received_badge
                    : R.string.have_not_yet_received_badge);
        }
        if (rv != null) {
            rv.setVisibility(emptyList ? View.GONE : View.VISIBLE);
            if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
        }
    }

    private void loadMedals() {
        AppContainer c = ContainerProvider.from(this);
        // Refresh /me so vipLevel is current.
        c.getIoExecutor().execute(() -> {
            try {
                c.getUserRepository().getMe();
            } catch (Exception ignored) {
            }
            AuthDtos.UserDto me = c.getSessionManager().getUser();
            int myVip = me != null ? Math.max(0, Math.min(MAX_VIP, me.vipLevel)) : 0;

            Result<MiscDtos.VipPlanList> plans = c.getVipPlansUseCase.execute();
            List<MedalRow> all = new ArrayList<>();
            List<MedalRow> owned = new ArrayList<>();

            if (plans.success && plans.data != null) {
                for (MiscDtos.VipPlanDto p : plans.data) {
                    if (p == null) continue;
                    int level = Math.max(1, p.level);
                    if (level > MAX_VIP) continue; // skip VIP 8…100 noise
                    MedalRow row = toRow(p.name, level, p.badgeUrl);
                    all.add(row);
                }
            }
            if (all.isEmpty()) {
                for (int i = 1; i <= MAX_VIP; i++) {
                    all.add(toRow(null, i, null));
                }
            }
            // خاصتي = current VIP badge only (not every lower tier).
            if (myVip >= 1) {
                MedalRow current = null;
                for (MedalRow row : all) {
                    if (row.level == myVip) {
                        current = row;
                        break;
                    }
                }
                if (current == null) current = toRow(null, myVip, null);
                owned.add(current);
            }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                catalog.clear();
                catalog.addAll(all);
                mine.clear();
                mine.addAll(owned);
                bindList();
            });
        });
    }

    private MedalRow toRow(String name, int level, String badgeUrl) {
        MedalRow row = new MedalRow();
        row.level = level;
        row.title = name != null && !name.isEmpty()
                ? name : getString(R.string.medal_vip_tier, level);
        if (badgeUrl != null && !badgeUrl.isEmpty()) {
            row.iconUrl = badgeUrl;
        } else {
            row.iconUrl = "/assets/visual-system/vip/vip_medal_mikoo_" + level + ".png";
        }
        return row;
    }

    private List<MedalRow> currentRows() {
        return showingMine ? mine : catalog;
    }

    private static final class MedalRow {
        int level;
        String title;
        String iconUrl;
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_medal, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MedalRow row = currentRows().get(position);
            h.title.setText(row.title);
            String abs = AssetCatalog.absoluteUrl(row.iconUrl);
            Glide.with(h.icon)
                    .load(abs)
                    .placeholder(R.drawable.ic_medal_default)
                    .error(R.drawable.ic_medal_default)
                    .into(h.icon);
        }

        @Override
        public int getItemCount() {
            return currentRows().size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.imgMedal);
                title = itemView.findViewById(R.id.tvMedalName);
            }
        }
    }
}
