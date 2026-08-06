package com.Dramizo.Series.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;

/**
 * Master gate: when dashboard sets {@code tasks.enabled=false},
 * every tasks entry/UI and client progress path must disappear.
 */
public final class TasksFeature {
    private TasksFeature() {}

    public static boolean isEnabled(@Nullable Context context) {
        if (context == null) return true;
        try {
            return ContainerProvider.from(context).getSessionManager().isTasksEnabledFromServer();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isEnabled(@Nullable AppContainer container) {
        if (container == null) return true;
        try {
            return container.getSessionManager().isTasksEnabledFromServer();
        } catch (Exception e) {
            return true;
        }
    }

    /** Hide (GONE) when tasks are fully disabled. */
    public static void applyVisibility(@Nullable View view, @Nullable Context context) {
        if (view == null) return;
        view.setVisibility(isEnabled(context) ? View.VISIBLE : View.GONE);
    }

    public static void applyVisibility(@Nullable View view, boolean enabled) {
        if (view == null) return;
        view.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }
}
