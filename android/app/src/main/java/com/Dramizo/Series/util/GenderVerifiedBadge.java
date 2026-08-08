package com.Dramizo.Series.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;

import java.io.ByteArrayOutputStream;

/** Green verified badge for admin-approved female hosts. */
public final class GenderVerifiedBadge {
    private GenderVerifiedBadge() {}

    public static void bind(@Nullable TextView nameView, @Nullable ImageView badgeView,
                            @Nullable AuthDtos.UserDto user) {
        boolean show = user != null && user.genderVerified;
        if (badgeView != null) {
            badgeView.setVisibility(show ? View.VISIBLE : View.GONE);
            // Dedicated badge image: don't also attach a compound drawable (would double).
            if (nameView != null) {
                nameView.setCompoundDrawablesRelative(null, null, null, null);
            }
            return;
        }
        if (nameView != null && show) {
            nameView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_gender_verified, 0);
            nameView.setCompoundDrawablePadding(
                    Math.round(6 * nameView.getResources().getDisplayMetrics().density));
        } else if (nameView != null) {
            nameView.setCompoundDrawablesRelative(null, null, null, null);
        }
    }

    public static void bind(@Nullable TextView nameView, @Nullable ImageView badgeView,
                            boolean genderVerified) {
        AuthDtos.UserDto u = new AuthDtos.UserDto();
        u.genderVerified = genderVerified;
        bind(nameView, badgeView, u);
    }

    @Nullable
    public static byte[] jpegFromBitmap(@Nullable Bitmap bitmap, int quality) {
        if (bitmap == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }
}
