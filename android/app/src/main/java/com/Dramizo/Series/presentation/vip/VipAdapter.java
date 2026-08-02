package com.Dramizo.Series.presentation.vip;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemVipPlanBinding;

import java.util.ArrayList;
import java.util.List;

/** VIP 1–7 cards with privilege rows + wealth gates (Mikoo-style). */
public class VipAdapter extends RecyclerView.Adapter<VipAdapter.VH> {
    public interface Listener {
        void onBuy(int level);
        default void onBuy(int level, int durationDays) { onBuy(level); }
    }

    private static final int MAX_DISPLAY_VIP = 7;
    /** Colorful VIP rental packs — never permanent. */
    private static final int[] DURATION_DAYS = { 7, 30, 40 };
    private static final String[] DURATION_LABELS = { "أسبوعي · 7 أيام", "شهري · 30 يوم", "40 يوم" };
    private static final double[] DURATION_MULT = { 0.28, 1.0, 1.25 };

    private final List<MiscDtos.VipPlanDto> items = new ArrayList<>();
    private final Listener listener;
    private int currentVipLevel;
    private int wealthLevel;
    private boolean purchaseLocked;

    public VipAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<MiscDtos.VipPlanDto> data, int currentVipLevel, int wealthLevel) {
        items.clear();
        if (data != null) {
            for (MiscDtos.VipPlanDto p : data) {
                if (p != null && p.level >= 1 && p.level <= MAX_DISPLAY_VIP) {
                    items.add(p);
                }
            }
            items.sort((a, b) -> Integer.compare(a.level, b.level));
        }
        this.currentVipLevel = Math.max(0, currentVipLevel);
        this.wealthLevel = Math.max(0, wealthLevel);
        notifyDataSetChanged();
    }

    public void setPurchaseLocked(boolean locked) {
        if (this.purchaseLocked == locked) return;
        this.purchaseLocked = locked;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemVipPlanBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MiscDtos.VipPlanDto plan = items.get(position);
        int level = Math.max(1, Math.min(MAX_DISPLAY_VIP, plan.level));
        holder.b.tvName.setText("VIP " + level);
        holder.b.tvPrice.setText(plan.coinPriceMonthly + " عملة / شهر · إيجار فقط");

        // Same Mikoo medal art as dashboard (VIP1→medal1 … VIP7→medal7).
        String badge = plan.badgeUrl;
        if (badge == null || badge.trim().isEmpty()
                || !badge.contains("vip_medal_mikoo_" + level)) {
            badge = "/assets/cosmetics/vip/vip_medal_mikoo_" + level + ".png?v=20260801vip7";
        }
        holder.b.imgVipDefault.setImageResource(vipDrawable(level));
        com.Dramizo.Series.util.ServerAssets.load(holder.b.imgVipDefault, badge);

        bindPrivileges(holder.b.layoutPrivileges, level, plan.benefits);

        if (currentVipLevel > 0 && level <= currentVipLevel) {
            holder.b.btnBuy.setText(level == currentVipLevel ? "مستواك الحالي" : "مشمول");
            holder.b.btnBuy.setEnabled(false);
            holder.b.btnBuy.setOnClickListener(null);
        } else if (purchaseLocked) {
            holder.b.btnBuy.setText("جاري الشراء…");
            holder.b.btnBuy.setEnabled(false);
            holder.b.btnBuy.setOnClickListener(null);
        } else {
            holder.b.btnBuy.setText(currentVipLevel > 0 ? "ترقية" : "شراء مدة");
            holder.b.btnBuy.setEnabled(true);
            holder.b.btnBuy.setOnClickListener(v -> showDurationPicker(holder.b.getRoot().getContext(), plan));
        }
    }

    private void showDurationPicker(android.content.Context ctx, MiscDtos.VipPlanDto plan) {
        if (listener == null || ctx == null || plan == null) return;
        CharSequence[] rows = new CharSequence[DURATION_DAYS.length];
        for (int i = 0; i < DURATION_DAYS.length; i++) {
            int price = Math.max(1, (int) Math.ceil(plan.coinPriceMonthly * DURATION_MULT[i]));
            rows[i] = DURATION_LABELS[i] + " — " + price + " عملة";
        }
        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("مدة VIP " + plan.level + " (ليست دائمة)")
                .setItems(rows, (d, which) -> {
                    if (which >= 0 && which < DURATION_DAYS.length) {
                        listener.onBuy(plan.level, DURATION_DAYS[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void bindPrivileges(LinearLayout container, int vipLevel, MiscDtos.VipBenefits benefits) {
        container.removeAllViews();
        addRow(container, "تأثير دخول الغرفة", vipLevel >= 1, 0);
        addRow(container, "شارة المضيف", vipLevel >= 2, 0);
        addRow(container, "منع الطرد", vipLevel >= 3, 0);
        addRow(container, "منع الكتم", vipLevel >= 4, 0);
        addRow(container, "هدايا المشاهير · عادي", vipLevel >= 5, 0);
        addRow(container, "هدايا المشاهير · مميز", vipLevel >= 5, 40);
        addRow(container, "تعليق طائر", vipLevel >= 6, 0);
        addRow(container, "أولوية الغرفة", vipLevel >= 1, 0);
        if (benefits != null && benefits.customFrame) {
            addRow(container, "إطار خاص", true, 0);
        }
        if (benefits != null && benefits.extra != null) {
            for (String e : benefits.extra) {
                if (e == null || e.isEmpty()) continue;
                addRow(container, e, true, 0);
            }
        }
    }

    private void addRow(LinearLayout container, String title, boolean vipOk, int wealthNeed) {
        TextView tv = new TextView(container.getContext());
        tv.setTextSize(12f);
        tv.setPadding(0, 6, 0, 6);
        boolean wealthOk = wealthNeed <= 0 || wealthLevel >= wealthNeed;
        boolean open = vipOk && wealthOk;
        String status;
        int color;
        if (open) {
            status = " · مفتوح";
            color = 0xFF059669;
        } else if (vipOk && !wealthOk) {
            status = " · مستوى الثروة " + wealthNeed + " · لم يتم الفتح";
            color = 0xFFD97706;
        } else {
            status = " · لم يتم الفتح";
            color = 0xFF9CA3AF;
        }
        tv.setText(title + status);
        tv.setTextColor(color);
        container.addView(tv);
    }

    @DrawableRes
    private static int vipDrawable(int level) {
        switch (Math.min(7, Math.max(1, level))) {
            case 1: return R.drawable.vip_medal_mikoo_1;
            case 2: return R.drawable.vip_medal_mikoo_2;
            case 3: return R.drawable.vip_medal_mikoo_3;
            case 4: return R.drawable.vip_medal_mikoo_4;
            case 5: return R.drawable.vip_medal_mikoo_5;
            case 6: return R.drawable.vip_medal_mikoo_6;
            default: return R.drawable.vip_medal_mikoo_7;
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemVipPlanBinding b;
        VH(ItemVipPlanBinding b) { super(b.getRoot()); this.b = b; }
    }
}
