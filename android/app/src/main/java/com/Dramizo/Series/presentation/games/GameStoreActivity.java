package com.Dramizo.Series.presentation.games;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GameStoreDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityGameStoreBinding;
import com.Dramizo.Series.databinding.ItemGameStoreBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsActivity;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GameStoreActivity extends ThemedActivity {
    public static final String EXTRA_SECTION = "section";

    private ActivityGameStoreBinding binding;
    private AppContainer c;
    private final List<GameStoreDtos.CatalogItem> items = new ArrayList<>();
    private final Set<String> owned = new HashSet<>();
    private final Map<String, String> equipped = new HashMap<>();
    private Adapter adapter;
    private String section = "store";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameStoreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) section = "store";
        binding.tvTitle.setText(sectionTitle(section));
        c = ContainerProvider.from(this);

        binding.btnBag.setOnClickListener(v -> startActivity(new Intent(this, BagActivity.class)));
        binding.btnMall.setOnClickListener(v -> startActivity(new Intent(this, CosmeticsActivity.class)));

        adapter = new Adapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        load();
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            Result<GameStoreDtos.CatalogResult> cat = ApiCall.execute(c.getGameStoreApi().catalog(section));
            Result<List<GameStoreDtos.OwnedItem>> inv = ApiCall.execute(c.getGameStoreApi().inventory());
            Result<Map<String, String>> loadout = ApiCall.execute(c.getGameStoreApi().loadout());
            runOnUiThread(() -> {
                if (w.success && w.data != null) {
                    binding.tvBalance.setText(String.format(Locale.US,
                            "كوينز: %,d", w.data.coins));
                }
                owned.clear();
                equipped.clear();
                if (loadout.success && loadout.data != null) {
                    equipped.putAll(loadout.data);
                }
                if (inv.success && inv.data != null) {
                    for (GameStoreDtos.OwnedItem o : inv.data) {
                        if (o == null || o.sku == null) continue;
                        owned.add(o.sku);
                        if (o.equipped && o.section != null) {
                            equipped.put(o.section, o.sku);
                        }
                    }
                }
                items.clear();
                if (cat.success && cat.data != null && cat.data.items != null) {
                    items.addAll(cat.data.items);
                }
                adapter.notifyDataSetChanged();
                if (cat.error != null) Toast.makeText(this, cat.error, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void purchase(GameStoreDtos.CatalogItem item) {
        if (item == null || item.sku == null) return;
        if (owned.contains(item.sku)) {
            equip(item);
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("sku", item.sku);
        c.getIoExecutor().execute(() -> {
            Result<GameStoreDtos.PurchaseResult> r = ApiCall.execute(c.getGameStoreApi().purchase(body));
            runOnUiThread(() -> {
                if (r.success) {
                    owned.add(item.sku);
                    String sec = item.section != null ? item.section : section;
                    equipped.put(sec, item.sku);
                    if (r.data != null && r.data.loadout != null) {
                        equipped.clear();
                        equipped.putAll(r.data.loadout);
                    }
                    adapter.notifyDataSetChanged();
                    if (r.data != null) {
                        long coins = r.data.coins > 0 ? r.data.coins : 0L;
                        if (coins <= 0 && r.data.gamePoints > 0) coins = r.data.gamePoints;
                        binding.tvBalance.setText(String.format(Locale.US, "كوينز: %,d", coins));
                    } else {
                        load();
                    }
                    Toast.makeText(this, "تم الشراء وتجهيز: " + item.title, Toast.LENGTH_SHORT).show();
                } else {
                    com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                }
            });
        });
    }

    private void equip(GameStoreDtos.CatalogItem item) {
        if (item == null || item.sku == null) return;
        Map<String, String> body = new HashMap<>();
        body.put("sku", item.sku);
        c.getIoExecutor().execute(() -> {
            Result<GameStoreDtos.EquipResult> r = ApiCall.execute(c.getGameStoreApi().equip(body));
            runOnUiThread(() -> {
                if (r.success) {
                    if (r.data != null && r.data.loadout != null) {
                        equipped.clear();
                        equipped.putAll(r.data.loadout);
                    } else {
                        String sec = item.section != null ? item.section : section;
                        equipped.put(sec, item.sku);
                    }
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "تم التجهيز: " + item.title, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String sectionTitle(String s) {
        switch (s) {
            case "dice": return getString(R.string.store_dice);
            case "tiles": return getString(R.string.store_tiles);
            case "stamp": return getString(R.string.store_stamp);
            case "emoji": return getString(R.string.emoji);
            default: return getString(R.string.game_store);
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemGameStoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            GameStoreDtos.CatalogItem item = items.get(position);
            boolean have = owned.contains(item.sku);
            String sec = item.section != null ? item.section : section;
            boolean isEq = have && item.sku.equals(equipped.get(sec));
            String status = isEq ? "  ✓ مجهّز" : (have ? "  ✓" : "");
            h.b.tvItem.setText(item.title + " — " + item.costPoints + " كوينز" + status);
            h.itemView.setAlpha(have && !isEq ? 0.75f : 1f);
            if (isEq) h.b.tvAction.setText(R.string.equipped);
            else if (have) h.b.tvAction.setText(R.string.equip);
            else h.b.tvAction.setText("شراء");
            h.itemView.setOnClickListener(v -> {
                if (have) equip(item);
                else purchase(item);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            final ItemGameStoreBinding b;
            VH(ItemGameStoreBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
