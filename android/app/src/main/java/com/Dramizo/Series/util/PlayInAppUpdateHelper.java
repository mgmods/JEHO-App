package com.Dramizo.Series.util;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Google Play In-App Updates — shows the official Play update UI when a newer
 * store build is available. Flexible for normal releases; immediate for high
 * priority / stale installs. Completes with a restart snackbar after download.
 */
public final class PlayInAppUpdateHelper {
    /** Prefer immediate if the installed build is this many days behind. */
    private static final int IMMEDIATE_STALENESS_DAYS = 7;
    /** Play Console updatePriority threshold for forcing immediate flow. */
    private static final int IMMEDIATE_PRIORITY = 4;

    private final ComponentActivity activity;
    private final AppUpdateManager manager;
    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher;
    @Nullable private InstallStateUpdatedListener installListener;
    private boolean flexiblePromptedThisProcess;
    private boolean flowStarting;

    public PlayInAppUpdateHelper(@NonNull ComponentActivity activity) {
        this.activity = activity;
        this.manager = AppUpdateManagerFactory.create(activity.getApplicationContext());
        this.updateLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> flowStarting = false);
    }

    /** Call once after the main UI is ready (e.g. delayed from onCreate). */
    public void checkForUpdate() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        manager.getAppUpdateInfo()
                .addOnSuccessListener(this::handleUpdateInfo)
                .addOnFailureListener(ignored -> AppUpdateChecker.checkSoft(activity));
    }

    /**
     * Call from Activity.onResume to resume an interrupted immediate update
     * and to offer restart when a flexible download finished in the background.
     */
    public void onResume() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        manager.getAppUpdateInfo()
                .addOnSuccessListener(info -> {
                    if (info.updateAvailability()
                            == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                            && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        startFlow(info, AppUpdateType.IMMEDIATE);
                        return;
                    }
                    if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        showRestartSnackbar();
                    }
                });
    }

    public void onDestroy() {
        unregisterInstallListener();
    }

    private void handleUpdateInfo(@NonNull AppUpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            showRestartSnackbar();
            return;
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
            // Sideload / no Play listing yet — fall back to server update sheet.
            AppUpdateChecker.checkSoft(activity);
            return;
        }

        int staleness = 0;
        Integer days = info.clientVersionStalenessDays();
        if (days != null && days > 0) staleness = days;

        int priority = 0;
        try {
            priority = info.updatePriority();
        } catch (Throwable ignored) {
        }

        boolean immediate = priority >= IMMEDIATE_PRIORITY
                || staleness >= IMMEDIATE_STALENESS_DAYS;

        if (immediate && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startFlow(info, AppUpdateType.IMMEDIATE);
            return;
        }

        if (flexiblePromptedThisProcess) return;

        if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            flexiblePromptedThisProcess = true;
            registerInstallListener();
            startFlow(info, AppUpdateType.FLEXIBLE);
            return;
        }

        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startFlow(info, AppUpdateType.IMMEDIATE);
        }
    }

    private void startFlow(@NonNull AppUpdateInfo info, int type) {
        if (flowStarting || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            flowStarting = true;
            manager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(type).build());
        } catch (Exception e) {
            flowStarting = false;
        }
    }

    private void registerInstallListener() {
        if (installListener != null) return;
        installListener = state -> {
            if (state == null) return;
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                showRestartSnackbar();
                unregisterInstallListener();
            }
        };
        manager.registerListener(installListener);
    }

    private void unregisterInstallListener() {
        if (installListener == null) return;
        try {
            manager.unregisterListener(installListener);
        } catch (Exception ignored) {
        }
        installListener = null;
    }

    private void showRestartSnackbar() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        String message = activity.getString(R.string.play_update_ready);
        String action = activity.getString(R.string.play_update_restart);

        Snackbar snackbar = Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(action, v -> {
            try {
                manager.completeUpdate();
            } catch (Exception ignored) {
            }
        });
        snackbar.setActionTextColor(0xFFF5C542);
        snackbar.show();
    }
}
