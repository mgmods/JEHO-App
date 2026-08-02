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
                || e.contains("not enough")
                || e.contains("needrecharge")
                || e.contains("need_recharge")
                || e.contains("balance")
                || e.contains("coins")
                || error.contains("رصيد")
                || error.contains("عملات")
                || error.contains("غير كاف")
                || error.contains("اشحن");
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

    /** Open recharge without requiring a matching error string. */
    public static void handleForced(@Nullable Activity activity, @Nullable String message) {
        if (activity == null || activity.isFinishing()) return;
        if (message != null && !message.isEmpty()) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
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
