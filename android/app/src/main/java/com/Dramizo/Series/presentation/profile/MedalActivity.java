package com.Dramizo.Series.presentation.profile;

import android.app.AlertDialog;
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
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Achievement badges catalog, independent from VIP subscriptions. */
public class MedalActivity extends ThemedActivity {
    private RecyclerView rv;
    private View empty;
    private TextView emptyText;
    private TextView tabBadge;
    private TextView tabMine;
    private final List<MedalRow> catalog = new ArrayList<>();
    private final List<MedalRow> mine = new ArrayList<>();
    private boolean showingMine;

    @Override
    protected boolean wantsRemoteThemeChrome() { return true; }

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
        boolean emptyList = rows.isEmpty();
        if (empty != null) empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);
        if (emptyText != null && emptyList) {
            emptyText.setText(showingMine
                    ? "ما حصلت على شارات حتى الآن"
                    : "لا توجد شارات متاحة حاليًا");
        }
        if (rv != null) {
            rv.setVisibility(emptyList ? View.GONE : View.VISIBLE);
            if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
        }
    }

    private void loadMedals() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.BadgeDto>> allResult =
                    ApiCall.execute(c.getUserApi().badges());
            Result<List<MiscDtos.BadgeDto>> mineResult =
                    ApiCall.execute(c.getUserApi().myBadges());
            List<MedalRow> all = new ArrayList<>();
            List<MedalRow> owned = new ArrayList<>();
            if (allResult != null && allResult.success && allResult.data != null) {
                for (MiscDtos.BadgeDto dto : allResult.data) {
                    if (dto == null || dto.id == null) continue;
                    all.add(toRow(dto, dto.earned));
                }
            }
            if (mineResult != null && mineResult.success && mineResult.data != null) {
                for (MiscDtos.BadgeDto dto : mineResult.data) {
                    if (dto == null || dto.id == null) continue;
                    owned.add(toRow(dto, true));
                }
            }
            boolean failed = (allResult == null || !allResult.success)
                    && (mineResult == null || !mineResult.success);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                catalog.clear();
                catalog.addAll(all);
                mine.clear();
                mine.addAll(owned);
                if (failed && catalog.isEmpty()) {
                    emptyText.setText("تعذّر تحميل الشارات. تحقق من اتصالك وحاول مجددًا.");
                }
                bindList();
            });
        });
    }

    private MedalRow toRow(MiscDtos.BadgeDto dto, boolean earned) {
        MedalRow row = new MedalRow();
        row.id = dto.id;
        row.title = dto.name != null && !dto.name.trim().isEmpty() ? dto.name : "شارة";
        row.description = dto.description;
        row.iconUrl = dto.imageUrl;
        row.earned = earned;
        row.awardedAt = dto.awardedAt;
        return row;
    }

    private List<MedalRow> currentRows() { return showingMine ? mine : catalog; }

    private void showDetails(MedalRow row) {
        String description = row.description == null || row.description.trim().isEmpty()
                ? "لا توجد تفاصيل إضافية لهذه الشارة."
                : row.description.trim();
        String status = row.earned ? "الحالة: مكتسبة" : "الحالة: غير مكتسبة";
        String when = row.awardedAt == null || row.awardedAt.isEmpty()
                ? "" : "\nتاريخ الاكتساب: " + row.awardedAt;
        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setMessage(description + "\n\n" + status + when)
                .setPositiveButton("حسنًا", null)
                .show();
    }

    private static final class MedalRow {
        String id;
        String title;
        String description;
        String iconUrl;
        String awardedAt;
        boolean earned;
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
            h.status.setText(row.earned ? "مكتسبة" : "غير مكتسبة");
            h.status.setTextColor(row.earned ? 0xFF168A55 : 0xFF8A8A8A);
            h.itemView.setAlpha(row.earned ? 1f : 0.62f);
            String abs = row.iconUrl == null || row.iconUrl.trim().isEmpty()
                    ? null : AssetCatalog.absoluteUrl(row.iconUrl);
            Glide.with(h.icon)
                    .load(abs)
                    .placeholder(R.drawable.ic_medal_default)
                    .error(R.drawable.ic_medal_default)
                    .into(h.icon);
            h.itemView.setOnClickListener(v -> showDetails(row));
        }

        @Override
        public int getItemCount() { return currentRows().size(); }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView status;
            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.imgMedal);
                title = itemView.findViewById(R.id.tvMedalName);
                status = itemView.findViewById(R.id.tvMedalStatus);
            }
        }
    }
}
