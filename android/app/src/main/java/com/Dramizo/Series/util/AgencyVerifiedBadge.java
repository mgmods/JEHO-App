package com.Dramizo.Series.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;

/** Official verified badge for admin-approved active agencies. */
public final class AgencyVerifiedBadge {
    private AgencyVerifiedBadge() {}

    public static void bind(@Nullable TextView nameView, @Nullable ImageView badgeView,
                            @Nullable MiscDtos.AgencyDto agency) {
        boolean show = agency != null && agency.isVerified;
        bind(nameView, badgeView, show);
    }

    public static void bind(@Nullable TextView nameView, @Nullable ImageView badgeView,
                            boolean verified) {
        if (badgeView != null) {
            badgeView.setVisibility(verified ? View.VISIBLE : View.GONE);
            if (verified) {
                badgeView.setImageResource(R.drawable.ic_agency_verified);
            }
        }
        if (nameView != null) {
            if (verified) {
                nameView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0, 0, R.drawable.ic_agency_verified, 0);
                nameView.setCompoundDrawablePadding(
                        Math.round(6 * nameView.getResources().getDisplayMetrics().density));
            } else {
                nameView.setCompoundDrawablesRelative(null, null, null, null);
            }
        }
    }
}
