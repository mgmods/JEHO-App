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

/** User badges and VIP medal catalog. Keeps badge ownership tied to real /me data. */
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
        List<MedalRow> rows = currentRows();
        boolean emptyList = rows.isEmpty();
        if (empty != null) empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);
        if (emptyText != null && emptyList) {
            emptyText.setText(R.string.have_not_yet_received_badge);
        }
        if (rv != null) {
            rv.setVisibility(emptyList ? View.GONE : View.VISIBLE);
            if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
        }
    }

    private void loadMedals() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                c.getUserRepository().getMe();
            } catch (Exception ignored) {
                // Keep the cached session profile if refresh is temporarily unavailable.
            }
            AuthDtos.UserDto me = c.getSessionManager().getUser();
            int myVip = me != null ? Math.max(0, Math.min(MAX_VIP, me.vipLevel)) : 0;
            Result<MiscDtos.VipPlanList> plans = c.getVipPlansUseCase.execute();
            List<MedalRow> all = new ArrayList<>();
            List<MedalRow> owned = new ArrayList<>();

            if (plans.success && plans.data != null) {
                for (MiscDtos.VipPlanDto plan : plans.data) {
                    if (plan == null || plan.level < 1 || plan.level > MAX_VIP) continue;
                    all.add(toVipRow(plan.name, plan.level, plan.badgeUrl));
                }
            }
            if (all.isEmpty()) {
                for (int level = 1; level <= MAX_VIP; level++) {
                    all.add(toVipRow(null, level, null));
                }
            }

            if (me != null) {
                // Show only the current VIP tier, and only when the account really has VIP.
                if (myVip > 0) {
                    String vipUrl = nonEmpty(me.vipBadgeUrl) ? me.vipBadgeUrl : null;
                    MedalRow vip = findVipRow(all, myVip);
                    if (vip == null) vip = toVipRow(null, myVip, vipUrl);
                    else if (vipUrl != null) vip.iconUrl = vipUrl;
                    owned.add(vip);
                }
                // These badges are shown only when the API supplies their real image URL.
                addRemoteBadge(owned, "شارة المستوى " + Math.max(1, me.level),
                        me.levelBadgeUrl, "level");
                addRemoteBadge(owned, "شارة المضيف", me.hostBadgeUrl, "host");
                // The project already contains this dedicated verified badge asset.
                if (me.genderVerified) {
                    MedalRow verified = new MedalRow();
                    verified.title = "شارة التحقق";
                    verified.iconRes = R.drawable.ic_gender_verified;
                    verified.key = "gender-verified";
                    owned.add(verified);
                }
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                catalog.clear();
                catalog.addAll(all);
                mine.clear();
                mine.addAll(owned);
                bindList();
            });
        });
    }

    private void addRemoteBadge(List<MedalRow> rows, String title, String url, String key) {
        if (!nonEmpty(url)) return;
        MedalRow row = new MedalRow();
        row.title = title;
        row.iconUrl = url;
        row.key = key;
        rows.add(row);
    }

    private MedalRow findVipRow(List<MedalRow> rows, int level) {
        for (MedalRow row : rows) {
            if (row.level == level) return row;
        }
        return null;
    }

    private MedalRow toVipRow(String name, int level, String badgeUrl) {
        MedalRow row = new MedalRow();
        row.level = level;
        row.key = "vip-" + level;
        row.title = nonEmpty(name) ? name : getString(R.string.medal_vip_tier, level);
        row.iconUrl = nonEmpty(badgeUrl)
                ? badgeUrl
                : "/assets/visual-system/vip/vip_medal_mikoo_" + level + ".png";
        return row;
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<MedalRow> currentRows() {
        return showingMine ? mine : catalog;
    }

    private static final class MedalRow {
        int level;
        int iconRes;
        String title;
        String iconUrl;
        String key;
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
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MedalRow row = currentRows().get(position);
            holder.title.setText(row.title);
            Glide.with(holder.icon).clear(holder.icon);
            if (row.iconRes != 0) {
                holder.icon.setImageResource(row.iconRes);
            } else {
                String url = AssetCatalog.absoluteUrl(row.iconUrl);
                Glide.with(holder.icon)
                        .load(url)
                        .placeholder(R.drawable.ic_medal_default)
                        .error(R.drawable.ic_medal_default)
                        .into(holder.icon);
            }
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
