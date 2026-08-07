package com.Dramizo.Series.presentation.agency;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ItemWithdrawRequestBinding;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Withdrawal log for agency room pool (host or agency owner commission).
 * Debit happens on request; reject refunds agencyDiamonds to the wallet.
 */
public class AgencyWithdrawHistoryActivity extends ThemedActivity {
    public static final String EXTRA_FOR_HOST = "for_host";

    private boolean forHost;
    private ProgressBar progress;
    private TextView tvEmpty;
    private final HistoryAdapter adapter = new HistoryAdapter();

    public static Intent intent(Context ctx, boolean forHost) {
        Intent i = new Intent(ctx, AgencyWithdrawHistoryActivity.class);
        i.putExtra(EXTRA_FOR_HOST, forHost);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agency_withdraw_history);
        forHost = getIntent().getBooleanExtra(EXTRA_FOR_HOST, false);

        TextView title = findViewById(R.id.tvTitle);
        if (title != null) {
            title.setText(forHost
                    ? R.string.agency_host_withdraw_history_title
                    : R.string.agency_owner_withdraw_history_title);
        }
        TextView hint = findViewById(R.id.tvHint);
        if (hint != null) {
            hint.setText(forHost
                    ? R.string.agency_host_withdraw_history_hint
                    : R.string.agency_owner_withdraw_history_hint);
        }

        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView recycler = findViewById(R.id.recycler);
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setAdapter(adapter);
        }
        ImageView back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());
        ImageView refresh = findViewById(R.id.btnRefresh);
        if (refresh != null) refresh.setOnClickListener(v -> load());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<WalletDtos.WithdrawList> result =
                    ApiCall.execute(ContainerProvider.from(this).getWalletApi().withdraws());
            runOnUiThread(() -> {
                if (progress != null) progress.setVisibility(View.GONE);
                if (!result.success || result.data == null) {
                    Toast.makeText(this,
                            result.error != null ? result.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                List<WalletDtos.WithdrawDto> filtered = new ArrayList<>();
                if (result.data.items != null) {
                    for (WalletDtos.WithdrawDto w : result.data.items) {
                        if (forHost) {
                            if (isHostSource(w)) filtered.add(w);
                        } else if (isOwnerSource(w)) {
                            filtered.add(w);
                        }
                    }
                }
                adapter.submit(filtered);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private static boolean isHostSource(WalletDtos.WithdrawDto w) {
        String src = sourceOf(w);
        return src.contains("agency_host") || src.equals("host");
    }

    private static boolean isOwnerSource(WalletDtos.WithdrawDto w) {
        String src = sourceOf(w);
        String stream = streamOf(w);
        if (src.contains("agency_host")) return false;
        if (src.contains("agency_commission")
                || "agency".equals(src)
                || src.contains("commission")) {
            return true;
        }
        // stream=agency without host source (legacy commission rows)
        return "agency".equals(stream) && !src.contains("host");
    }

    private static String sourceOf(WalletDtos.WithdrawDto w) {
        if (w == null) return "";
        if (w.source != null && !w.source.isEmpty()) {
            return w.source.toLowerCase(Locale.US);
        }
        Map<String, Object> d = w.payoutDetails;
        if (d == null) return "";
        Object s = d.get("source");
        if (s == null) s = d.get("channel");
        return s != null ? String.valueOf(s).toLowerCase(Locale.US) : "";
    }

    private static String streamOf(WalletDtos.WithdrawDto w) {
        if (w == null) return "";
        if (w.stream != null && !w.stream.isEmpty()) {
            return w.stream.toLowerCase(Locale.US);
        }
        Map<String, Object> d = w.payoutDetails;
        if (d == null) return "";
        Object s = d.get("stream");
        return s != null ? String.valueOf(s).toLowerCase(Locale.US) : "";
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        private final List<WalletDtos.WithdrawDto> items = new ArrayList<>();

        void submit(List<WalletDtos.WithdrawDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemWithdrawRequestBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            WalletDtos.WithdrawDto w = items.get(position);
            holder.b.tvWithdrawAmount.setText(holder.itemView.getContext().getString(
                    R.string.withdrawal_amount_summary, w.diamonds, w.amountFiat));
            String status = w.status != null ? w.status : "pending";
            int statusLabel = R.string.withdrawal_pending;
            int bg = R.drawable.bg_chip_member;
            if ("paid".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
                statusLabel = R.string.withdrawal_paid;
                bg = R.drawable.bg_chip_charm;
            } else if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
                statusLabel = R.string.withdrawal_rejected;
                bg = R.drawable.bg_chip_wealth;
            }
            holder.b.tvWithdrawStatus.setText(statusLabel);
            holder.b.tvWithdrawStatus.setBackgroundResource(bg);
            String time = w.createdAt != null ? w.createdAt.replace('T', ' ') : "";
            if (time.length() > 16) time = time.substring(0, 16);
            holder.b.tvWithdrawMeta.setText((w.method != null ? w.method.toUpperCase(Locale.US) : "—")
                    + (time.isEmpty() ? "" : " · " + time));
            String note;
            if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
                note = w.adminNote != null && !w.adminNote.isEmpty()
                        ? w.adminNote
                        : holder.itemView.getContext()
                        .getString(R.string.agency_withdraw_rejected_refund_note);
            } else if ("paid".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
                note = w.adminNote != null && !w.adminNote.isEmpty()
                        ? w.adminNote
                        : holder.itemView.getContext().getString(R.string.agency_withdraw_paid_note);
            } else {
                note = holder.itemView.getContext().getString(R.string.withdrawal_pending_note);
            }
            holder.b.tvWithdrawPayout.setText(note);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemWithdrawRequestBinding b;

            VH(ItemWithdrawRequestBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
