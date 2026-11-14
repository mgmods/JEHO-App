package com.Dramizo.Series.presentation.agency;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ItemWithdrawRequestBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared compact withdraw request list for host + agency pro dashboards. */
final class ProDashWithdrawAdapter extends RecyclerView.Adapter<ProDashWithdrawAdapter.VH> {
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
    public void onBindViewHolder(@NonNull VH h, int position) {
        WalletDtos.WithdrawDto w = items.get(position);
        h.b.tvWithdrawAmount.setText(h.itemView.getContext().getString(
                R.string.withdrawal_amount_summary, w.diamonds, w.amountFiat));
        String status = w.status != null ? w.status : "pending";
        int statusLabel = R.string.withdrawal_pending;
        int bg = R.drawable.bg_dash_badge_pending;
        if ("paid".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
            statusLabel = R.string.withdrawal_paid;
            bg = R.drawable.bg_dash_badge_paid;
        } else if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            statusLabel = R.string.withdrawal_rejected;
            bg = R.drawable.bg_dash_badge_rejected;
        }
        h.b.tvWithdrawStatus.setText(statusLabel);
        h.b.tvWithdrawStatus.setBackgroundResource(bg);
        h.b.tvWithdrawStatus.setTextColor(0xFFFFFFFF);
        String time = w.createdAt != null ? w.createdAt.replace('T', ' ') : "";
        if (time.length() > 16) time = time.substring(0, 16);
        h.b.tvWithdrawMeta.setText((w.method != null ? w.method.toUpperCase(Locale.US) : "—")
                + (time.isEmpty() ? "" : " · " + time));
        if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            h.b.tvWithdrawPayout.setText(w.adminNote != null && !w.adminNote.isEmpty()
                    ? w.adminNote
                    : h.itemView.getContext().getString(R.string.agency_withdraw_rejected_refund_note));
        } else if ("paid".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
            h.b.tvWithdrawPayout.setText(w.adminNote != null && !w.adminNote.isEmpty()
                    ? w.adminNote
                    : h.itemView.getContext().getString(R.string.agency_withdraw_paid_note));
        } else {
            h.b.tvWithdrawPayout.setText(R.string.withdrawal_pending_note);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static boolean isHostSource(WalletDtos.WithdrawDto w) {
        String src = sourceOf(w);
        return src.contains("agency_host") || "host".equals(src);
    }

    static boolean isOwnerSource(WalletDtos.WithdrawDto w) {
        String src = sourceOf(w);
        String stream = streamOf(w);
        if (src.contains("agency_host")) return false;
        if (src.contains("agency_commission") || "agency".equals(src) || src.contains("commission")) {
            return true;
        }
        return "agency".equals(stream) && !src.contains("host");
    }

    private static String sourceOf(WalletDtos.WithdrawDto w) {
        if (w == null) return "";
        if (w.source != null && !w.source.isEmpty()) return w.source.toLowerCase(Locale.US);
        Map<String, Object> d = w.payoutDetails;
        if (d == null) return "";
        Object s = d.get("source");
        if (s == null) s = d.get("channel");
        return s != null ? String.valueOf(s).toLowerCase(Locale.US) : "";
    }

    private static String streamOf(WalletDtos.WithdrawDto w) {
        if (w == null) return "";
        if (w.stream != null && !w.stream.isEmpty()) return w.stream.toLowerCase(Locale.US);
        Map<String, Object> d = w.payoutDetails;
        if (d == null) return "";
        Object s = d.get("stream");
        return s != null ? String.valueOf(s).toLowerCase(Locale.US) : "";
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemWithdrawRequestBinding b;

        VH(ItemWithdrawRequestBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
