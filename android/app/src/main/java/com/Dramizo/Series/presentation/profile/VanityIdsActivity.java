package com.Dramizo.Series.presentation.profile;

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
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.VanityDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityVanityIdsBinding;
import com.Dramizo.Series.databinding.ItemVanityIdBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VanityIdsActivity extends ThemedActivity {
    private ActivityVanityIdsBinding binding;
    private AppContainer c;
    private final List<VanityDtos.VanityItem> items = new ArrayList<>();
    private Adapter adapter;
    private VanityDtos.VanityItem myLease;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVanityIdsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);
        binding.btnBack.setOnClickListener(v -> navigateUp());
        adapter = new Adapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        load();
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            Result<AuthDtos.UserDto> me = ApiCall.execute(c.getUserApi().me());
            Result<VanityDtos.Catalog> cat = ApiCall.execute(c.getVanityApi().list(null));
            Result<VanityDtos.MineResult> mine = ApiCall.execute(c.getVanityApi().mine());
            if (me.success && me.data != null) {
                c.getSessionManager().updateCachedUser(me.data);
            }
            runOnUiThread(() -> {
                if (w.success && w.data != null) {
                    binding.tvBalance.setText(String.format(Locale.US, "%,d", w.data.coins));
                }
                myLease = mine.success && mine.data != null ? mine.data.item : null;
                if (me.success && me.data != null) {
                    String pid = me.data.displayPublicId();
                    String leaseNote = "";
                    if (myLease != null && myLease.expiresAt != null && myLease.active) {
                        leaseNote = " · ينتهي: " + myLease.expiresAt.replace('T', ' ').replace("Z", "");
                    }
                    binding.tvCurrentId.setText(
                            "آيدي الحالي: " + (pid.isEmpty() ? "—" : pid) + leaseNote);
                }
                items.clear();
                if (cat.success && cat.data != null && cat.data.items != null) {
                    items.addAll(cat.data.items);
                }
                // Show own active lease at top for renew.
                if (myLease != null && myLease.active && myLease.publicId != null) {
                    boolean listed = false;
                    for (VanityDtos.VanityItem it : items) {
                        if (myLease.publicId.equals(it.publicId)) {
                            listed = true;
                            break;
                        }
                    }
                    if (!listed) items.add(0, myLease);
                }
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (cat.error != null && items.isEmpty()) {
                    Toast.makeText(this, cat.error, Toast.LENGTH_SHORT).show();
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
                ? ("تجديد آي دي " + item.publicId)
                : ("شراء آي دي " + item.publicId);
        String msg = renew
                ? ("تجديد 30 يوماً بنصف السعر: " + price + " كوينز (بدلاً من " + full + ").")
                : ("مدة الاشتراك 30 يوماً. سيتم خصم " + price + " كوينز واستبدال آيديك الحالي.");
        AuraDialogHelper.confirm(this, title, msg, renew ? "تجديد" : "شراء",
                () -> c.getIoExecutor().execute(() -> {
                    ApiCall.execute(c.getVanityApi().reserve(item.publicId));
                    Result<VanityDtos.PurchaseResult> r =
                            ApiCall.execute(c.getVanityApi().purchase(item.publicId));
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(this,
                                    r.data != null && r.data.renew
                                            ? "تم تجديد الآي دي (+" + days + " يوم)"
                                            : "تم شراء الآي دي لمدة " + days + " يوم",
                                    Toast.LENGTH_SHORT).show();
                            load();
                        } else {
                            com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                        }
                    });
                }),
                getString(android.R.string.cancel),
                null);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemVanityIdBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            VanityDtos.VanityItem item = items.get(position);
            h.b.tvPublicId.setText(item.publicId != null ? item.publicId : "—");
            boolean renew = myLease != null
                    && item.publicId != null
                    && item.publicId.equals(myLease.publicId)
                    && myLease.active;
            long full = Math.max(0, item.priceCoins);
            long half = item.renewPriceCoins > 0 ? item.renewPriceCoins : (full + 1) / 2;
            if (renew) {
                h.b.tvPrice.setText(String.format(Locale.US,
                        "تجديد %,d كوينز (½) · +30 يوم", half));
                h.b.btnBuy.setText("تجديد");
            } else {
                h.b.tvPrice.setText(String.format(Locale.US,
                        "%,d كوينز · 30 يوم", full));
                h.b.btnBuy.setText("شراء");
            }
            h.b.btnBuy.setOnClickListener(v -> purchase(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemVanityIdBinding b;
            VH(ItemVanityIdBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
