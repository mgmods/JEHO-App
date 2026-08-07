package com.Dramizo.Series.presentation.vip;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.ServerAssets;
import com.Dramizo.Series.util.VipStyle;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full VIP cards: visual package + room features + agencies + mall unlocks.
 */
public class VipAdapter {
    public interface Listener {
        void onBuy(int level);
        default void onBuy(int level, int durationDays) { onBuy(level); }
    }

    private static final int MAX_DISPLAY_VIP = 7;
    private static final int[] DURATION_DAYS = { 7, 30, 40 };
    private static final double[] DURATION_MULT = { 0.28, 1.0, 1.25 };

    private final List<MiscDtos.VipPlanDto> items = new ArrayList<>();
    private final Listener listener;
    private final LayoutInflater inflater;
    private int currentVipLevel;
    private int wealthLevel;
    private boolean purchaseLocked;

    public VipAdapter(Context context, Listener listener) {
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void submit(
            @NonNull LinearLayout parent,
            @Nullable List<MiscDtos.VipPlanDto> data,
            int currentVipLevel,
            int wealthLevel) {
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
        parent.removeAllViews();
        for (MiscDtos.VipPlanDto plan : items) {
            parent.addView(createCard(parent, plan));
        }
    }

    public void setPurchaseLocked(boolean locked, @NonNull LinearLayout parent) {
        if (this.purchaseLocked == locked) return;
        this.purchaseLocked = locked;
        if (!items.isEmpty()) {
            parent.removeAllViews();
            for (MiscDtos.VipPlanDto plan : items) {
                parent.addView(createCard(parent, plan));
            }
        }
    }

    private View createCard(LinearLayout parent, MiscDtos.VipPlanDto plan) {
        View root = inflater.inflate(R.layout.item_vip_plan, parent, false);
        int level = VipCatalog.clamp(plan.level);

        root.findViewById(R.id.vipAccentBar).setBackgroundColor(VipCatalog.accent(level));

        TextView tvName = root.findViewById(R.id.tvName);
        TextView tvPrice = root.findViewById(R.id.tvPrice);
        TextView tvPlanTag = root.findViewById(R.id.tvPlanTag);
        ImageView imgMedal = root.findViewById(R.id.imgVipDefault);
        ImageView imgIncludeMedal = root.findViewById(R.id.imgIncludeMedal);
        ImageView imgIncludeFrame = root.findViewById(R.id.imgIncludeFrame);
        ImageView imgIncludeHead = root.findViewById(R.id.imgIncludeHead);
        ImageView imgIncludeMall = root.findViewById(R.id.imgIncludeMallFrame);
        TextView tvBubble = root.findViewById(R.id.tvIncludeBubble);
        TextView tvMallLabel = root.findViewById(R.id.tvIncludeMallLabel);
        LinearLayout layoutPrivileges = root.findViewById(R.id.layoutPrivileges);
        MaterialButton btnBuy = root.findViewById(R.id.btnBuy);

        tvName.setText(VipCatalog.headlineAr(level));
        tvName.setTextColor(darkenForLightBg(VipCatalog.accent(level)));
        tvPrice.setText(String.format(Locale.US, "%,d عملة / شهر · إيجار فقط",
                Math.max(0, plan.coinPriceMonthly)));
        tvPlanTag.setText("باقة شاملة: هوية بصرية · مزايا الغرف · بطاقات الوكالات · فتح المول");

        imgMedal.setImageResource(VipCatalog.medalDrawable(level));
        ServerAssets.load(imgMedal, VipCatalog.medalUrl(level));

        imgIncludeMedal.setImageResource(VipCatalog.medalDrawable(level));
        ServerAssets.load(imgIncludeMedal, VipCatalog.medalUrl(level));
        ServerAssets.load(imgIncludeFrame, VipCatalog.frameUrl(level));
        ServerAssets.load(imgIncludeHead, VipCatalog.headUrl(level));

        imgIncludeMall.setImageResource(VipCatalog.medalDrawable(level));
        String mallPreview = VipCatalog.mallBeastFramePreviewUrl(level);
        if (mallPreview != null) {
            ServerAssets.load(imgIncludeMall, mallPreview);
        }
        if (tvMallLabel != null) {
            tvMallLabel.setText("مول " + level);
        }

        tvBubble.setBackgroundResource(VipCatalog.bubbleDrawable(level));
        tvBubble.setText("VIP");

        // Soft VIP chip style for room/agency marks
        try {
            VipStyle.applyChip(root.findViewById(R.id.tvIncludeRoomChip), level);
            VipStyle.applyChip(root.findViewById(R.id.tvIncludeAgencyChip), Math.max(level, 2));
        } catch (Exception ignored) {
        }

        bindSections(layoutPrivileges, level);
        bindBuyButton(btnBuy, plan, level, root.getContext());
        return root;
    }

    private void bindSections(LinearLayout container, int vipLevel) {
        container.removeAllViews();
        for (VipCatalog.Section section : VipCatalog.sectionsForCard(vipLevel)) {
            View block = inflater.inflate(R.layout.item_vip_section, container, false);
            TextView st = block.findViewById(R.id.tvSectionTitle);
            TextView ss = block.findViewById(R.id.tvSectionSub);
            LinearLayout perks = block.findViewById(R.id.layoutSectionPerks);
            st.setText(section.title);
            ss.setText(section.subtitle);
            for (VipCatalog.Perk perk : section.perks) {
                View row = inflater.inflate(R.layout.item_vip_perk, perks, false);
                TextView mark = row.findViewById(R.id.tvPerkMark);
                TextView title = row.findViewById(R.id.tvPerkTitle);
                TextView hint = row.findViewById(R.id.tvPerkHint);
                TextView badge = row.findViewById(R.id.tvPerkBadge);

                title.setText(perk.title);
                hint.setText(perk.hint);

                if (perk.unlocked) {
                    mark.setText("✓");
                    mark.setTextColor(0xFF059669);
                    title.setTextColor(0xFF0F172A);
                    if (perk.includedWithRent) {
                        badge.setText("مشمول");
                        badge.setTextColor(0xFFB45309);
                    } else {
                        badge.setText("مفتوح");
                        badge.setTextColor(0xFF059669);
                    }
                } else {
                    mark.setText("↑");
                    mark.setTextColor(0xFF9CA3AF);
                    title.setTextColor(0xFF6B7280);
                    badge.setText("قفل");
                    badge.setTextColor(0xFF9CA3AF);
                }
                perks.addView(row);
            }
            container.addView(block);
        }
    }

    private void bindBuyButton(
            MaterialButton btn,
            MiscDtos.VipPlanDto plan,
            int level,
            Context ctx) {
        if (currentVipLevel > 0 && level <= currentVipLevel) {
            btn.setText(level == currentVipLevel ? "مستواك الحالي" : "مشمول في اشتراكك");
            btn.setEnabled(false);
            btn.setOnClickListener(null);
            btn.setAlpha(0.72f);
        } else if (purchaseLocked) {
            btn.setText("جاري التفعيل…");
            btn.setEnabled(false);
            btn.setOnClickListener(null);
            btn.setAlpha(0.85f);
        } else {
            btn.setAlpha(1f);
            btn.setText(currentVipLevel > 0 ? "ترقية إلى VIP " + level : "اختيار المدة والشراء");
            btn.setEnabled(true);
            btn.setOnClickListener(v -> showDurationSheet(ctx, plan));
        }
    }

    private void showDurationSheet(Context ctx, MiscDtos.VipPlanDto plan) {
        if (listener == null || ctx == null || plan == null) return;
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(ctx);
        View sheet = inflater.cloneInContext(ctx).inflate(R.layout.bottom_sheet_vip_duration, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.tvVipDurationTitle);
        title.setText(VipCatalog.headlineAr(plan.level) + " — اختر المدة");

        int p7 = Math.max(1, (int) Math.ceil(plan.coinPriceMonthly * DURATION_MULT[0]));
        int p30 = Math.max(1, (int) Math.ceil(plan.coinPriceMonthly * DURATION_MULT[1]));
        int p40 = Math.max(1, (int) Math.ceil(plan.coinPriceMonthly * DURATION_MULT[2]));

        ((TextView) sheet.findViewById(R.id.tvVipDur7Price)).setText(p7 + " عملة");
        ((TextView) sheet.findViewById(R.id.tvVipDur30Price)).setText(p30 + " عملة");
        ((TextView) sheet.findViewById(R.id.tvVipDur40Price)).setText(p40 + " عملة");

        sheet.findViewById(R.id.btnVipDur7).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onBuy(plan.level, DURATION_DAYS[0]);
        });
        sheet.findViewById(R.id.btnVipDur30).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onBuy(plan.level, DURATION_DAYS[1]);
        });
        sheet.findViewById(R.id.btnVipDur40).setOnClickListener(v -> {
            dialog.dismiss();
            listener.onBuy(plan.level, DURATION_DAYS[2]);
        });
        dialog.show();
    }

    private static int darkenForLightBg(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.55f;
        return Color.HSVToColor(hsv);
    }
}
