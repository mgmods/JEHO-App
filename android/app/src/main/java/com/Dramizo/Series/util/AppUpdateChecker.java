package com.Dramizo.Series.util;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;

import java.util.Locale;

/**
 * Checks store version from {@code GET /config/app-update} and shows a professional
 * update sheet when the installed build is behind.
 */
public final class AppUpdateChecker {
    private static final String PREFS = "app_update_gate";
    private static final String KEY_SKIPPED_CODE = "skipped_version_code";
    private static boolean shownThisProcess;

    private AppUpdateChecker() {}

    public interface ContinueCallback {
        void onContinue();
    }

    /**
     * Soft check: may skip once per version; force update blocks until Play Store opens.
     * Calls {@code onContinue} when user can proceed (no update / dismissed soft update).
     */
    public static void check(@NonNull Activity activity, @Nullable ContinueCallback onContinue) {
        if (activity.isFinishing()) {
            if (onContinue != null) onContinue.onContinue();
            return;
        }
        AppContainer c = ContainerProvider.from(activity);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AppUpdateDto> r = ApiCall.execute(c.getConfigApi().appUpdate());
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (!r.success || r.data == null || r.data.latestVersionCode <= 0) {
                    if (onContinue != null) onContinue.onContinue();
                    return;
                }
                maybeShow(activity, r.data, onContinue);
            });
        });
    }

    private static void maybeShow(
            @NonNull Activity activity,
            @NonNull MiscDtos.AppUpdateDto dto,
            @Nullable ContinueCallback onContinue) {
        int installed = BuildConfig.VERSION_CODE;
        int latest = Math.max(0, dto.latestVersionCode);
        int minCode = Math.max(0, dto.minVersionCode);
        if (latest <= installed) {
            if (onContinue != null) onContinue.onContinue();
            return;
        }
        boolean force = dto.forceUpdate || (minCode > 0 && installed < minCode);
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        if (!force) {
            if (shownThisProcess) {
                if (onContinue != null) onContinue.onContinue();
                return;
            }
            int skipped = prefs.getInt(KEY_SKIPPED_CODE, 0);
            if (skipped >= latest) {
                if (onContinue != null) onContinue.onContinue();
                return;
            }
        }

        shownThisProcess = true;
        boolean arabic = isArabic(activity);
        String title = firstNonEmpty(
                arabic ? dto.titleAr : dto.titleEn,
                arabic ? dto.titleEn : dto.titleAr,
                arabic ? "تحديث جديد متوفر" : "Update available");
        String versionLabel = !TextUtils.isEmpty(dto.latestVersionName)
                ? dto.latestVersionName
                : ("v" + latest);
        String body = firstNonEmpty(
                arabic ? dto.messageAr : dto.messageEn,
                arabic ? dto.messageEn : dto.messageAr,
                arabic
                        ? "يتوفر إصدار أحدث من JEHO CHAT على المتجر. حدّث الآن لأفضل تجربة."
                        : "A newer JEHO CHAT build is on the store. Update now for the best experience.");
        String message = body + "\n\n"
                + (arabic ? "الإصدار الجديد: " : "New version: ")
                + versionLabel
                + "  ·  "
                + (arabic ? "عندك: " : "Yours: ")
                + BuildConfig.VERSION_NAME;

        String positive = arabic ? "تحديث الآن" : "Update now";
        String negative = force ? null : (arabic ? "لاحقاً" : "Later");

        AuraDialogHelper.confirm(
                activity,
                title,
                message,
                positive,
                () -> openStore(activity, dto.storeUrl),
                negative,
                () -> {
                    if (!force) {
                        prefs.edit().putInt(KEY_SKIPPED_CODE, latest).apply();
                    }
                    if (onContinue != null) onContinue.onContinue();
                },
                !force);

        // Soft update: also continue in background so splash isn't blocked forever
        // if user leaves sheet open — only when not forced.
        if (!force && onContinue != null) {
            // Wait for user action; do not auto-continue.
        }
        if (force && onContinue == null) {
            // blocking splash path — user must tap update
        }
    }

    /** Soft reminder after main screen is up (non-blocking). */
    public static void checkSoft(@NonNull Activity activity) {
        check(activity, null);
    }

    private static void openStore(@NonNull Activity activity, @Nullable String storeUrl) {
        String url = storeUrl != null && !storeUrl.trim().isEmpty()
                ? storeUrl.trim()
                : "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID;
        try {
            Intent market = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID));
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(market);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isArabic(@NonNull Activity activity) {
        Locale loc = activity.getResources().getConfiguration().getLocales().get(0);
        return loc != null && "ar".equalsIgnoreCase(loc.getLanguage());
    }

    @NonNull
    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
}
