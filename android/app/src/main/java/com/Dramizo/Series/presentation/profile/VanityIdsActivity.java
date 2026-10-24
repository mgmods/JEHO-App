package com.Dramizo.Series.presentation.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.VanityDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.ErrorToasts;
import com.Dramizo.Series.util.VanityIdStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Professional special-ID store: hero current lease + coin bal + catalog cards.
 */
public class VanityIdsActivity extends ThemedActivity {

    private AppContainer c;
    private TextView tvCurrentIdNumber;
    private TextView tvCurrentId;
    private TextView tvBalance;
    private TextView tvEmpty;
    private View searchPreviewWrap;
    private TextView tvSearchPreviewNumber;
    private TextView tvSearchPreviewLabel;
    private EditText etSearch;
    private RecyclerView recycler;
    private CatalogAdapter adapter;

    private final List<VanityDtos.VanityItem> allItems = new ArrayList<>();
    @Nullable private VanityDtos.VanityItem myLease;
    private long coinsBal;
    @Nullable private String userPublicId;
    private boolean canApplyToAgency;

    @Override
    protected boolean wantsRemoteThemeChrome() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vanity_ids);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.contentRoot));
        c = ContainerProvider.from(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        tvCurrentIdNumber = findViewById(R.id.tvCurrentIdNumber);
        tvCurrentId = findViewById(R.id.tvCurrentId);
        tvBalance = findViewById(R.id.tvBalance);
        tvEmpty = findViewById(R.id.tvEmpty);
        searchPreviewWrap = findViewById(R.id.searchPreviewWrap);
        tvSearchPreviewNumber = findViewById(R.id.tvSearchPreviewNumber);
        tvSearchPreviewLabel = findViewById(R.id.tvSearchPreviewLabel);
        recycler = findViewById(R.id.recycler);
        etSearch = findViewById(R.id.etVanitySearch);

        adapter = new CatalogAdapter();
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setItemAnimator(null);
        recycler.setAdapter(adapter);
        recycler.setNestedScrollingEnabled(false);

        if (etSearch != null) {
            EdgeToEdgeHelper.keepAboveImeOnFocus(etSearch);
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    applyFilter();
                }
            });
        }

        AuthDtos.UserDto me = c.getSessionManager().getUser();
        if (me != null) {
            userPublicId = me.displayPublicId() != null ? me.displayPublicId() : me.publicId;
            bindHero();
        }
        load();
    }

    private void bindHero() {
        if (myLease != null && myLease.active && myLease.publicId != null) {
            if (tvCurrentIdNumber != null) {
                tvCurrentIdNumber.setText(myLease.publicId);
                VanityIdStyle.bind(tvCurrentIdNumber, c == null ? null : c.getVanityApi(), myLease.publicId, 0);
            }
            if (tvCurrentId != null) {
                String exp = formatExpires(myLease.expiresAt);
                tvCurrentId.setText(getString(R.string.vanity_hero_active_until, exp));
            }
        } else if (userPublicId != null && !userPublicId.isEmpty()) {
            if (tvCurrentIdNumber != null) {
                tvCurrentIdNumber.setText(userPublicId);
                VanityIdStyle.bind(tvCurrentIdNumber, c == null ? null : c.getVanityApi(), userPublicId, 0);
            }
            if (tvCurrentId != null) {
                tvCurrentId.setText(R.string.vanity_hero_current_hint);
            }
        } else {
            if (tvCurrentIdNumber != null) {
                tvCurrentIdNumber.setText("—");
                VanityIdStyle.bind(tvCurrentIdNumber, null, null, 0);
            }
            if (tvCurrentId != null) {
                tvCurrentId.setText(R.string.vanity_hero_pick_hint);
            }
        }
        if (tvBalance != null) {
            tvBalance.setText(String.format(Locale.US, "%,d", coinsBal));
        }
    }

    private void applyFilter() {
        String q = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim() : "";
        List<VanityDtos.VanityItem> shown = new ArrayList<>();
        for (VanityDtos.VanityItem it : allItems) {
            if (it == null || it.publicId == null) continue;
            if (!q.isEmpty() && !it.publicId.contains(q)) continue;
            shown.add(it);
        }
        adapter.submit(shown);

        // Show a larger live preview only for a complete catalog ID match. This
        // reuses the exact dashboard-configured colors/glow/animation via the
        // shared renderer and avoids sending a style request on every typed digit.
        VanityDtos.VanityItem exactMatch = null;
        if (!q.isEmpty()) {
            for (VanityDtos.VanityItem it : shown) {
                if (it.publicId != null && it.publicId.equals(q)) {
                    exactMatch = it;
                    break;
                }
            }
        }
        if (searchPreviewWrap != null) {
            boolean showPreview = exactMatch != null && tvSearchPreviewNumber != null;
            searchPreviewWrap.setVisibility(showPreview ? View.VISIBLE : View.GONE);
            if (showPreview) {
                tvSearchPreviewNumber.setText(exactMatch.publicId);
                if (tvSearchPreviewLabel != null) {
                    tvSearchPreviewLabel.setText("معاينة حية للآيدي " + exactMatch.publicId);
                }
                VanityIdStyle.bind(tvSearchPreviewNumber, c == null ? null : c.getVanityApi(),
                        exactMatch.publicId, 0);
            } else if (tvSearchPreviewNumber != null) {
                VanityIdStyle.bind(tvSearchPreviewNumber, null, null, 0);
            }
        }

        if (tvEmpty != null) {
            boolean empty = shown.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvEmpty.setText(q.isEmpty()
                    ? R.string.vanity_empty_refresh
                    : R.string.vanity_no_search_results);
        }
    }

    private void load() {
        AppLoadingOverlay.showUntilReady(this);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            Result<AuthDtos.UserDto> me = ApiCall.execute(c.getUserApi().me());
            Result<VanityDtos.Catalog> cat = ApiCall.execute(
                    c.getVanityApi().list("available"));
            Result<VanityDtos.MineResult> mine = ApiCall.execute(c.getVanityApi().mine());
            if (me.success && me.data != null) {
                c.getSessionManager().updateCachedUser(me.data);
                userPublicId = me.data.displayPublicId() != null
                        ? me.data.displayPublicId() : me.data.publicId;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                AppLoadingOverlay.hide(this);
                coinsBal = (w.success && w.data != null) ? w.data.coins : 0;
                myLease = mine.success && mine.data != null ? mine.data.item : null;
                canApplyToAgency = mine.success && mine.data != null && mine.data.canApplyToAgency;

                allItems.clear();
                if (cat.success && cat.data != null && cat.data.items != null) {
                    allItems.addAll(cat.data.items);
                }
                if (myLease != null && myLease.active && myLease.publicId != null) {
                    boolean listed = false;
                    for (VanityDtos.VanityItem it : allItems) {
                        if (myLease.publicId.equals(it.publicId)) {
                            listed = true;
                            break;
                        }
                    }
                    if (!listed) allItems.add(0, myLease);
                }
                Collections.sort(allItems, Comparator
                        .comparingInt((VanityDtos.VanityItem a) ->
                                a.publicId != null ? a.publicId.length() : 99)
                        .thenComparingLong(a -> a.priceCoins));
                // Own lease first
                if (myLease != null && myLease.active && myLease.publicId != null) {
                    for (int i = 0; i < allItems.size(); i++) {
                        if (myLease.publicId.equals(allItems.get(i).publicId)) {
                            allItems.add(0, allItems.remove(i));
                            break;
                        }
                    }
                }
                bindHero();
                applyFilter();
                if (!cat.success && allItems.isEmpty()) {
                    Toast.makeText(this,
                            cat.error != null ? cat.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void purchase(VanityDtos.VanityItem item) {
        if (item == null || item.publicId == null) return;
        boolean renew = myLease != null
                && item.publicId.equals(myLease.publicId)
                && myLease.active;
        long full = Math.max(0, item.priceCoins);
        long price = renew
                ? (item.renewPriceCoins > 0 ? item.renewPriceCoins : (full + 1) / 2)
                : full;
        int days = item.leaseDays > 0 ? item.leaseDays : 30;
        String title = renew
                ? getString(R.string.vanity_renew_title, item.publicId)
                : getString(R.string.vanity_buy_title, item.publicId);
        String msg = renew
                ? getString(R.string.vanity_renew_msg, days, price, full)
                : getString(R.string.vanity_buy_msg, days, price, item.publicId);
        AuraDialogHelper.confirm(this, title, msg,
                renew ? getString(R.string.vanity_renew) : getString(R.string.vanity_buy),
                () -> c.getIoExecutor().execute(() -> {
                    ApiCall.execute(c.getVanityApi().reserve(item.publicId));
                    Result<VanityDtos.PurchaseResult> r =
                            ApiCall.execute(c.getVanityApi().purchase(item.publicId));
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(this,
                                    r.data != null && r.data.renew
                                            ? getString(R.string.vanity_renewed_toast, days)
                                            : getString(R.string.vanity_purchased_toast, days),
                                    Toast.LENGTH_SHORT).show();
                            if (r.data != null && r.data.publicId != null) {
                                showApplyTargetDialog(r.data.publicId, r.data.canApplyToAgency);
                            }
                            load();
                        } else {
                            com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                        }
                    });
                }),
                getString(android.R.string.cancel),
                null);
    }


    private void showApplyTargetDialog(String publicId, boolean canApplyToAgency) {
        List<String> labels = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        labels.add("الروم الشخصي");
        targets.add("room");
        if (canApplyToAgency) {
            labels.add("الوكالة");
            targets.add("agency");
        }
        labels.add("الحساب الشخصي");
        targets.add("account");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("أين تريد استخدام الآيدي " + publicId + "؟")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String target = targets.get(which);
                    AppLoadingOverlay.showUntilReady(this);
                    c.getIoExecutor().execute(() -> {
                        java.util.Map<String, String> body = new java.util.HashMap<>();
                        body.put("targetType", target);
                        Result<VanityDtos.ApplyResult> result =
                                ApiCall.execute(c.getVanityApi().apply(publicId, body));
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            AppLoadingOverlay.hide(this);
                            if (result.success && result.data != null && result.data.success) {
                                String label = "account".equals(target) ? "الحساب الشخصي"
                                        : "room".equals(target) ? "الروم الشخصي" : "الوكالة";
                                Toast.makeText(this, "تم تطبيق الآيدي على " + label,
                                        Toast.LENGTH_LONG).show();
                                load();
                            } else {
                                Toast.makeText(this,
                                        result.error != null ? result.error
                                                : "تعذر تطبيق الآيدي، تأكد من امتلاك الروم أو الوكالة",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String formatExpires(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        String s = iso.replace('T', ' ').replace("Z", "");
        if (s.length() > 16) s = s.substring(0, 16);
        return s;
    }

    private String tierLabel(String publicId) {
        int n = publicId != null ? publicId.length() : 0;
        if (n > 0 && n <= 3) return getString(R.string.vanity_tier_legendary);
        if (n <= 4) return getString(R.string.vanity_tier_ultra_rare);
        if (n <= 6) return getString(R.string.vanity_tier_rare);
        if (n <= 8) return getString(R.string.vanity_tier_special);
        return getString(R.string.vanity_tier_classic);
    }

    private final class CatalogAdapter extends RecyclerView.Adapter<CatalogAdapter.VH> {
        private final List<VanityDtos.VanityItem> items = new ArrayList<>();

        void submit(List<VanityDtos.VanityItem> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vanity_id, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            VanityDtos.VanityItem item = items.get(position);
            String pid = item.publicId != null ? item.publicId : "—";
            boolean renew = myLease != null
                    && item.publicId != null
                    && item.publicId.equals(myLease.publicId)
                    && myLease.active;
            long full = Math.max(0, item.priceCoins);
            long half = item.renewPriceCoins > 0 ? item.renewPriceCoins : (full + 1) / 2;
            long showPrice = renew ? half : full;
            int days = item.leaseDays > 0 ? item.leaseDays : 30;

            h.tvPublicId.setText(pid);
            com.Dramizo.Series.util.VanityIdStyle.bind(h.tvPublicId, c.getVanityApi(), pid, 0);
            h.tvTier.setText(tierLabel(pid));
            if (h.tvMineBadge != null) {
                h.tvMineBadge.setVisibility(renew ? View.VISIBLE : View.GONE);
            }
            h.tvPrice.setText(getString(R.string.vanity_price_coins, showPrice));
            String meta;
            if (renew) {
                meta = getString(R.string.vanity_meta_renew,
                        days, half, full, formatExpires(item.expiresAt));
            } else {
                meta = getString(R.string.vanity_meta_lease, days);
            }
            h.tvMeta.setText(meta);
            h.btnBuy.setText(renew ? R.string.vanity_renew : R.string.vanity_buy);
            h.btnBuy.setOnClickListener(v -> {
                if (renew) {
                    new androidx.appcompat.app.AlertDialog.Builder(VanityIdsActivity.this)
                            .setTitle("الآيدي " + pid)
                            .setItems(new String[]{"تطبيق الآيدي", "تجديد الآيدي 30 يوم"}, (dialog, which) -> {
                                if (which == 0) showApplyTargetDialog(pid, canApplyToAgency);
                                else purchase(item);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    purchase(item);
                }
            });
            h.tvPublicId.setOnClickListener(v -> {
                if (renew) showApplyTargetDialog(pid, canApplyToAgency);
                else purchase(item);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView tvPublicId;
            final TextView tvTier;
            final TextView tvMineBadge;
            final TextView tvPrice;
            final TextView tvMeta;
            final TextView btnBuy;

            VH(@NonNull View itemView) {
                super(itemView);
                tvPublicId = itemView.findViewById(R.id.tvPublicId);
                tvTier = itemView.findViewById(R.id.tvTier);
                tvMineBadge = itemView.findViewById(R.id.tvMineBadge);
                tvPrice = itemView.findViewById(R.id.tvPrice);
                tvMeta = itemView.findViewById(R.id.tvMeta);
                btnBuy = itemView.findViewById(R.id.btnBuy);
            }
        }
    }
}
