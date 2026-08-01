package com.Dramizo.Series.util;

import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.Dramizo.Series.R;

/** Mikoo-like VIP bubble / chip colors by VIP tier 1–7. */
public final class VipStyle {
    private VipStyle() {}

    public static final class Palette {
        @ColorInt public final int start;
        @ColorInt public final int end;
        @ColorInt public final int stroke;
        @ColorInt public final int name;
        @ColorInt public final int chipText;

        Palette(int start, int end, int stroke, int name, int chipText) {
            this.start = start;
            this.end = end;
            this.stroke = stroke;
            this.name = name;
            this.chipText = chipText;
        }
    }

    @NonNull
    public static Palette forLevel(int vipLevel) {
        int tier = Math.min(7, Math.max(0, vipLevel));
        switch (tier) {
            case 1:
                return new Palette(0xCC1B3A4A, 0x9900A3C4, 0xFF5EEAD4, 0xFFE0FFFA, 0xFFFFFFFF);
            case 2:
                return new Palette(0xCC16324F, 0x992563EB, 0xFF93C5FD, 0xFFDBEAFE, 0xFFFFFFFF);
            case 3:
                return new Palette(0xCC134E4A, 0x9900C2A8, 0xFF2DD4BF, 0xFFA5F3E0, 0xFFFFFFFF);
            case 4:
                return new Palette(0xCC3B0764, 0x997C3AED, 0xFFC4B5FD, 0xFFF3E8FF, 0xFFFFFFFF);
            case 5:
                return new Palette(0xCC7C2D12, 0x99EA580C, 0xFFFDBA74, 0xFFFFEDD5, 0xFFFFFFFF);
            case 6:
                return new Palette(0xCC78350F, 0x99B8860B, 0xFFFFE082, 0xFFFFF3C4, 0xFFFFF8E1);
            case 7:
                return new Palette(0xCC831843, 0x99DB2777, 0xFFFFD700, 0xFFFFF1B0, 0xFFFFF8E1);
            default:
                return new Palette(0x990B1220, 0x990B1220, 0x3300C2A8, 0xFFE2E8F0, 0xFFFFFFFF);
        }
    }

    public static void applyBubble(@NonNull View view, int vipLevel) {
        float density = view.getResources().getDisplayMetrics().density;
        if (vipLevel <= 0) {
            // Mikoo public screen: translucent black 8dp bubble.
            view.setBackgroundResource(R.drawable.bg_room_chat_bubble);
            return;
        }
        Palette p = forLevel(vipLevel);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{p.start, p.end});
        g.setCornerRadius(8f * density);
        g.setStroke(Math.max(1, Math.round(density)), p.stroke);
        view.setBackground(g);
    }

    public static void applyChip(@NonNull View view, int vipLevel) {
        float density = view.getResources().getDisplayMetrics().density;
        Palette p = forLevel(vipLevel);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{p.start, p.end});
        g.setCornerRadius(10f * density);
        g.setStroke(Math.max(1, Math.round(density)), p.stroke);
        view.setBackground(g);
    }

    public static int nameColor(int vipLevel) {
        return forLevel(vipLevel).name;
    }

    /** Mikoo room body gold (#FFDA81); VIP keeps white for contrast on tinted bubbles. */
    public static int messageColor(int vipLevel) {
        return vipLevel > 0 ? 0xFFFFFFFF : 0xFFFFDA81;
    }

    public static int chipTextColor(int vipLevel) {
        return forLevel(vipLevel).chipText;
    }

    public static int dp(@NonNull Resources res, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, res.getDisplayMetrics()));
    }
}
