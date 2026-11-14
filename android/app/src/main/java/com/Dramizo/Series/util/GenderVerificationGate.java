package com.Dramizo.Series.util;

import android.content.Context;
import android.content.Intent;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.presentation.profile.FemaleIdentityVerificationActivity;

/**
 * Female identity verification gate.
 * After the user submits (status pending), she can use the app;
 * female-host features stay locked until approved.
 */
public final class GenderVerificationGate {
    private GenderVerificationGate() {}

    /** True only when we must force the verification screen (not yet submitted). */
    public static boolean needsVerification(AuthDtos.UserDto user, SessionManager sm) {
        if (user == null || sm == null) return false;
        if (!"female".equalsIgnoreCase(user.gender)) return false;
        if (user.genderVerified) return false;
        if (!sm.isFemaleOnlyVoiceHostsFromServer()) return false;
        String st = user.genderVerificationStatus;
        if (st != null) {
            String s = st.trim().toLowerCase(java.util.Locale.US);
            // Already submitted — allow app entry; features stay locked until approved.
            if ("pending".equals(s) || "approved".equals(s) || "under_review".equals(s)) {
                return false;
            }
        }
        return true;
    }

    /** Female voice-room host features require approval. */
    public static boolean canUseFemaleHostFeatures(AuthDtos.UserDto user, SessionManager sm) {
        if (user == null) return false;
        if (!"female".equalsIgnoreCase(user.gender)) return true;
        if (!sm.isFemaleOnlyVoiceHostsFromServer()) return true;
        return user.genderVerified;
    }

    public static Intent blockingIntent(Context ctx) {
        Intent i = new Intent(ctx, FemaleIdentityVerificationActivity.class);
        i.putExtra(FemaleIdentityVerificationActivity.EXTRA_BLOCKING, true);
        return i;
    }
}
