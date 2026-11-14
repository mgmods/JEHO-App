package com.Dramizo.Series.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.Dramizo.Series.R;

import java.util.List;

/**
 * Country labels that draw the current Syrian flag as an image.
 * The 🇸🇾 emoji still paints the retired flag in every system font,
 * so Syria is rendered from a bundled vector instead.
 */
public final class CountryFlagText {
    private CountryFlagText() {}

    private static boolean isSyria(@Nullable String country) {
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        return entry != null && "SY".equals(entry.code);
    }

    /** Flag + Arabic name, e.g. "[flag] سوريا". */
    public static CharSequence label(@Nullable Context ctx, @Nullable String country) {
        if (ctx == null || !isSyria(country)) return CountryCatalog.labelWithFlag(country);
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        return withFlag(ctx, entry != null ? entry.nameAr : "سوريا");
    }

    /** Flag glyph only. */
    public static CharSequence flagOnly(@Nullable Context ctx, @Nullable String country) {
        if (ctx == null || !isSyria(country)) return CountryCatalog.flagOnly(country);
        return withFlag(ctx, "");
    }

    /** Sets a country label on a TextView, keeping the flag image span intact. */
    public static void apply(@Nullable TextView view, @Nullable String country) {
        if (view == null) return;
        view.setText(label(view.getContext(), country));
    }

    /** Prefix + country label as one styled sequence. */
    public static CharSequence labelWithPrefix(
            @Nullable Context ctx, String prefix, @Nullable String country) {
        return android.text.TextUtils.concat(prefix, label(ctx, country));
    }

    /** Spinner rows (index 0 is the "choose" placeholder), flag images included. */
    public static CharSequence[] spinnerLabels(@Nullable Context ctx) {
        List<CountryCatalog.Entry> all = CountryCatalog.all();
        CharSequence[] out = new CharSequence[all.size() + 1];
        out[0] = "اختر الدولة";
        for (int i = 0; i < all.size(); i++) {
            out[i + 1] = label(ctx, all.get(i).code);
        }
        return out;
    }

    private static CharSequence withFlag(Context ctx, String trailingText) {
        Drawable flag = ContextCompat.getDrawable(ctx, R.drawable.ic_flag_syria);
        if (flag == null) {
            return trailingText.isEmpty() ? "🌍" : "🌍  " + trailingText;
        }
        int height = Math.round(ctx.getResources().getDisplayMetrics().density * 13f);
        int width = Math.round(height * 1.5f);
        flag.setBounds(0, 0, width, height);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("\u00A0\u00A0");
        sb.setSpan(new ImageSpan(flag, ImageSpan.ALIGN_BOTTOM), 0, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!trailingText.isEmpty()) sb.append("  ").append(trailingText);
        return sb;
    }
}
