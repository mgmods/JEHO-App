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
            runOnUiThread(() -> {
                if (w.success && w.data != null) {
                    binding.tvBalance.setText(String.format(Locale.US, "%,d", w.data.coins));
                }
                if (me.success && me.data != null) {
                    String pid = me.data.displayPublicId();
                    binding.tvCurrentId.setText("آيدي الحالي: " + (pid.isEmpty() ? "—" : pid));
                }
                items.clear();
                if (cat.success && cat.data != null && cat.data.items != null) {
                    items.addAll(cat.data.items);
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
        AuraDialogHelper.confirm(this,
                "شراء آي دي " + item.publicId,
                "سيتم خصم " + item.priceCoins + " كوينز واستبدال آيديك الحالي.",
                "شراء",
                () -> c.getIoExecutor().execute(() -> {
                    ApiCall.execute(c.getVanityApi().reserve(item.publicId));
                    Result<VanityDtos.PurchaseResult> r =
                            ApiCall.execute(c.getVanityApi().purchase(item.publicId));
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(this, "تم شراء الآي دي بنجاح", Toast.LENGTH_SHORT).show();
                            load();
                        } else {
                            Toast.makeText(this,
                                    r.error != null ? r.error : getString(R.string.error_generic),
                                    Toast.LENGTH_LONG).show();
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
            return new VH(ItemVanityIdBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            VanityDtos.VanityItem item = items.get(position);
            h.b.tvPublicId.setText(item.publicId != null ? item.publicId : "—");
            h.b.tvPrice.setText(String.format(Locale.US, "%,d كوينز", Math.max(0, item.priceCoins)));
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
