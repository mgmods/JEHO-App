package com.Dramizo.Series.util;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/** Loads all catalog visuals from API/dashboard URLs. */
public final class ServerAssets {
    private ServerAssets() {}

    public static String url(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return null;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl;
        String origin = ApiOrigin.origin();
        return pathOrUrl.startsWith("/") ? origin + pathOrUrl : origin + "/" + pathOrUrl;
    }

    public static void load(ImageView view, String pathOrUrl) {
        String resolved = url(pathOrUrl);
        if (resolved == null) {
            view.setImageDrawable(null);
            return;
        }
        Glide.with(view)
                .load(resolved)
                .placeholder(ImagePlaceholder.light())
                .error(ImagePlaceholder.light())
                .into(view);
    }

    public static void loadCompoundStart(TextView view, String pathOrUrl, int sizeDp) {
        String resolved = url(pathOrUrl);
        if (resolved == null) {
            view.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        Glide.with(view)
                .load(resolved)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(
                            @NonNull Drawable resource,
                            @Nullable Transition<? super Drawable> transition) {
                        int px = Math.round(sizeDp * view.getResources().getDisplayMetrics().density);
                        resource.setBounds(0, 0, px, px);
                        view.setCompoundDrawablePadding(
                                Math.round(5 * view.getResources().getDisplayMetrics().density));
                        view.setCompoundDrawablesRelative(resource, null, null, null);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        view.setCompoundDrawablesRelative(null, null, null, null);
                    }
                });
    }
}
