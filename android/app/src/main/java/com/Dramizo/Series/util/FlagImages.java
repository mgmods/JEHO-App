package com.Dramizo.Series.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

/** Load official country flag images (flagcdn) with emoji fallback. */
public final class FlagImages {
    private FlagImages() {}

    public static void bind(@Nullable ImageView imageView, @Nullable TextView emojiFallback,
                            @Nullable String country) {
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        String url = CountryCatalog.flagImageUrl(country);
        if (imageView != null) {
            if (url != null) {
                imageView.setVisibility(View.VISIBLE);
                Glide.with(imageView.getContext())
                        .load(url)
                        .fitCenter()
                        .into(imageView);
                if (emojiFallback != null) emojiFallback.setVisibility(View.GONE);
                return;
            }
            imageView.setVisibility(View.GONE);
            imageView.setImageDrawable(null);
        }
        if (emojiFallback != null) {
            if (entry != null && entry.flag != null && !entry.flag.isEmpty()) {
                emojiFallback.setVisibility(View.VISIBLE);
                emojiFallback.setText(entry.flag);
            } else {
                emojiFallback.setVisibility(View.GONE);
            }
        }
    }

    public static void bind(@Nullable ImageView imageView, @Nullable String country) {
        bind(imageView, null, country);
    }
}
