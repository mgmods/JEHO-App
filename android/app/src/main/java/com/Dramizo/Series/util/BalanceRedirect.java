package com.Dramizo.Series.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.presentation.wallet.RechargePackagesBottomSheet;

/**
 * When a purchase/send fails for insufficient coins, open recharge UX.
 */
public final class BalanceRedirect {
    private BalanceRedirect() {}

    public static boolean looksLikeInsufficient(@Nullable String error) {
        if (error == null || error.isEmpty()) return false;
        String e = error.toLowerCase(java.util.Locale.US);
        return e.contains("insufficient")
                || error.contains("رصيد")
                || error.contains("عملات")
                || e.contains("not enough")
                || e.contains("balance");
    }

    /** Toast + open packages sheet when possible; otherwise BagActivity. */
    public static void handle(@Nullable Activity activity, @Nullable String error) {
        if (activity == null || activity.isFinishing()) return;
        String msg = error != null && !error.isEmpty()
                ? error
                : activity.getString(R.string.error_generic);
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        if (!looksLikeInsufficient(error)) return;
        openRecharge(activity);
    }

    public static void openRecharge(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity instanceof FragmentActivity) {
            FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
            if (!fm.isStateSaved()) {
                RechargePackagesBottomSheet.show(fm);
                return;
            }
        }
        activity.startActivity(new Intent(activity, BagActivity.class));
    }
}
