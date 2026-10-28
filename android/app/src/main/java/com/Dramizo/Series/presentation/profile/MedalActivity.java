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
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.BadgeDto;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import android.widget.Toast;

import retrofit2.Response;

/** Honour/medals (ميداليات) — VIP 1–7, system-bar safe. */
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
    private String profileUserId;
    private boolean ownCabinet;
    private final Set<String> selectedKeys = new HashSet<>();

    @Override
    protected boolean wantsRemoteThemeChrome() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medal);
        // contentRoot is padded by ThemedActivity (status + nav) like the rest of the app.
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
        // Open directly on the user's received medals so they can equip one immediately.
        showTab(true);
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
            String requestedUserId = getIntent().getStringExtra(ProfileActivity.EXTRA_USER_ID);
            AuthDtos.UserDto me = c.getSessionManager().getUser();
            AuthDtos.UserDto profileUser = me;
            if (requestedUserId != null && !requestedUserId.trim().isEmpty()) {
                try {
                    Response<ApiResponse<AuthDtos.UserDto>> profileResponse =
                            c.getUserApi().getUser(requestedUserId.trim()).execute();
                    if (profileResponse.isSuccessful() && profileResponse.body() != null
                            && profileResponse.body().data != null) {
                        profileUser = profileResponse.body().data;
                    }
                } catch (Exception ignored) {
                    // Fall back to the signed-in user's profile only if this is their own cabinet.
                }
            }
            final String resolvedProfileUserId = profileUser != null ? profileUser.id : null;
            final List<String> savedSelection = profileUser != null && profileUser.displayedMedalIds != null ? new ArrayList<>(profileUser.displayedMedalIds) : new ArrayList<>();
            final boolean canEdit = me != null && me.id != null && me.id.equals(resolvedProfileUserId);
            int myVip = profileUser != null ? Math.max(0, Math.min(MAX_VIP, profileUser.vipLevel)) : 0;

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

            // Keep the legacy VIP catalogue fallback even when dashboard badges exist.
            if (all.isEmpty()) {
                for (int i = 1; i <= MAX_VIP; i++) {
                    all.add(toRow(null, i, null));
                }
            }

            // Dashboard-awarded achievement badges are also medals, not just VIP cosmetics.
            // Keep the VIP rows intact and merge real badge ownership from the same API
            // used by the profile page and badge notification.
            if (resolvedProfileUserId != null && !resolvedProfileUserId.trim().isEmpty()) {
                try {
                    Response<ApiResponse<List<BadgeDto>>> response =
                            c.getUserApi().badges(resolvedProfileUserId).execute();
                    if (response.isSuccessful() && response.body() != null
                            && response.body().data != null) {
                        for (BadgeDto badge : response.body().data) {
                            if (badge == null || badge.imageUrl == null || badge.imageUrl.trim().isEmpty()) continue;
                            MedalRow row = toRow(
                                    badge.name,
                                    1000,
                                    badge.imageUrl);
                            row.key = badge.id != null && !badge.id.trim().isEmpty() ? badge.id : "badge:" + row.title;
                            all.add(row);
                            if (badge.earned) owned.add(row);
                        }
                    }
                } catch (Exception ignored) {
                    // Badge API failure must not hide the existing VIP medals.
                }
            }

            // Keep the VIP badge and dashboard-awarded medals belonging to the viewed profile.
            if (myVip >= 1) {
                MedalRow current = null;
                for (MedalRow row : all) {
                    if (row.level == myVip) {
                        current = row;
                        break;
                    }
                }
                if (current == null) current = toRow(null, myVip, null);
                current.key = "vip:" + myVip;
                owned.add(current);
            }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                profileUserId = resolvedProfileUserId;
                ownCabinet = canEdit;
                selectedKeys.clear();
                selectedKeys.addAll(savedSelection);
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
        row.key = "vip:" + level;
        row.title = name != null && !name.isEmpty()
                ? name : getString(R.string.medal_vip_tier, level);
        if (badgeUrl != null && !badgeUrl.isEmpty()) {
            row.iconUrl = badgeUrl;
        } else {
            row.iconUrl = "/assets/visual-system/vip/vip_medal_mikoo_" + level + ".png";
        }
        return row;
    }


    private void saveSelection() {
        if (!ownCabinet || profileUserId == null) return;
        List<String> snapshot = new ArrayList<>(selectedKeys);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                MiscDtos.UpdateProfileRequest request = new MiscDtos.UpdateProfileRequest();
                request.displayedMedalIds = snapshot;
                Response<ApiResponse<AuthDtos.UserDto>> response = c.getUserApi().updateProfile(request).execute();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                        Toast.makeText(this, "تم حفظ الميداليات المختارة", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "تعذر حفظ الاختيار، حاول مجدداً", Toast.LENGTH_SHORT).show();
                        loadMedals();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    Toast.makeText(this, "تعذر الاتصال لحفظ الميداليات", Toast.LENGTH_SHORT).show();
                    loadMedals();
                });
            }
        });
    }

    private List<MedalRow> currentRows() {
        return showingMine ? mine : catalog;
    }

    private static final class MedalRow {
        int level;
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
        public void onBindViewHolder(@NonNull VH h, int position) {
            MedalRow row = currentRows().get(position);
            h.title.setText(row.title);
            String abs = AssetCatalog.absoluteUrl(row.iconUrl);
            Glide.with(h.icon)
                    .load(abs)
                    .placeholder(R.drawable.ic_medal_default)
                    .error(R.drawable.ic_medal_default)
                    .into(h.icon);
            boolean selected = selectedKeys.contains(row.key);
            h.itemView.setAlpha(selected ? 1f : 0.96f);
            h.action.setVisibility(showingMine && ownCabinet ? View.VISIBLE : View.GONE);
            h.action.setText(selected ? "إلغاء التجهيز" : "تجهيز للملف الشخصي");
            h.action.setBackgroundResource(selected
                    ? R.drawable.bg_cosmetic_btn_equipped
                    : R.drawable.bg_cosmetic_btn_equip);
            h.action.setOnClickListener(v -> {
                if (!showingMine || !ownCabinet) return;
                if (row.key == null || row.key.trim().isEmpty()) return;
                boolean wasSelected = selectedKeys.contains(row.key);
                if (wasSelected) {
                    selectedKeys.remove(row.key);
                } else {
                    if (selectedKeys.size() >= 5) {
                        Toast.makeText(MedalActivity.this, "يمكنك عرض 5 ميداليات كحد أقصى", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedKeys.add(row.key);
                }
                saveSelection();
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return currentRows().size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView action;
            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.imgMedal);
                title = itemView.findViewById(R.id.tvMedalName);
                action = itemView.findViewById(R.id.btnMedalAction);
            }
        }
    }
}
