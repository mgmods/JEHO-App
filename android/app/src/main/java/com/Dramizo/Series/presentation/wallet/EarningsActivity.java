package com.Dramizo.Series.presentation.wallet;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityEarningsBinding;
import com.Dramizo.Series.databinding.ItemEarningsEntryBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EarningsActivity extends ThemedActivity {
    private ActivityEarningsBinding binding;
    private AppContainer c;
    private final EarningsAdapter adapter = new EarningsAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEarningsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);
        binding.btnBack.setOnClickListener(v -> navigateUp());
        if (binding.btnWithdrawRoomEarnings != null) {
            binding.btnWithdrawRoomEarnings.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(this, BagActivity.class);
                i.putExtra(BagActivity.EXTRA_TAB, 1);
                i.putExtra(BagActivity.EXTRA_DIAMOND_ACTION, "withdraw");
                startActivity(i);
            });
        }
        binding.recyclerEarnings.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerEarnings.setAdapter(adapter);
        load();
    }

    private void load() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> wallet = c.getWalletUseCase.execute();
            Result<MiscDtos.ListResult<WalletDtos.TransactionDto>> tx = ApiCall.execute(c.getWalletApi().transactions(1));
            runOnUiThread(() -> {
                if (wallet.success && wallet.data != null) {
                    binding.tvTotalDiamonds.setText(String.format(Locale.US, "%,d", wallet.data.diamonds));
                }
                List<WalletDtos.TransactionDto> items = new ArrayList<>();
                if (tx.success && tx.data != null && tx.data.items != null) {
                    for (WalletDtos.TransactionDto row : tx.data.items) {
                        if (row == null) continue;
                        if ("diamonds".equalsIgnoreCase(row.currency) && row.amount > 0) {
                            items.add(row);
                        }
                    }
                }
                adapter.submit(items);
                boolean empty = items.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerEarnings.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (!wallet.success && !tx.success) {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static class EarningsAdapter extends RecyclerView.Adapter<EarningsAdapter.VH> {
        private final List<WalletDtos.TransactionDto> items = new ArrayList<>();

        void submit(List<WalletDtos.TransactionDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemEarningsEntryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            WalletDtos.TransactionDto row = items.get(position);
            holder.b.tvEarningsTitle.setText(titleFor(holder.itemView.getContext(), row));
            holder.b.tvEarningsMeta.setText(metaFor(holder.itemView.getContext(), row));
            holder.b.tvEarningsAmount.setText(String.format(Locale.US, "+%d 💎", row.amount));
        }

        @Override public int getItemCount() { return items.size(); }

        private static String titleFor(android.content.Context context, WalletDtos.TransactionDto row) {
            if (row.description != null && !row.description.isEmpty()) return row.description;
            if ("gift_receive".equalsIgnoreCase(row.type)) {
                return context.getString(R.string.earnings_gift);
            }
            if ("room_entry".equalsIgnoreCase(row.type)) {
                return context.getString(R.string.earnings_room_entry);
            }
            return row.type != null ? row.type
                    : context.getString(R.string.earnings_generic);
        }

        private static String metaFor(android.content.Context context, WalletDtos.TransactionDto row) {
            String time = row.createdAt != null ? row.createdAt.replace('T', ' ') : "";
            if (time.length() > 16) time = time.substring(0, 16);
            String balance = context.getString(R.string.earnings_balance_after, row.balanceAfter);
            return balance + (time.isEmpty() ? "" : " · " + time);
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemEarningsEntryBinding b;
            VH(ItemEarningsEntryBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
