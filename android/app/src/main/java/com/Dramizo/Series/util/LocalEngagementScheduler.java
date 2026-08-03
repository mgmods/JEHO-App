package com.Dramizo.Series.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.TaskCenterActivity;
import com.Dramizo.Series.service.VoiceRoomForegroundService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local engagement notifications.
 * Prefers real daily tasks from {@code GET /tasks/daily}, same compact style as chat.
 */
public final class LocalEngagementScheduler {
    public static final String CHANNEL_ID = "auralive_engagement";
    private static final String WORK_NAME = "jeho_local_engagement";
    private static final String PREFS = "jeho_engagement";
    private static final String KEY_INDEX = "rotate_index";
    private static final int ALARM_REQ = 9201;
    /** First nudge sooner, then roughly hourly so daily tasks stay visible. */
    private static final long FIRST_ALARM_MS = 12L * 60L * 1000L;
    private static final long NEXT_ALARM_MS = 55L * 60L * 1000L;
    private static final int NOTIF_TASKS = 9101;
    private static final int NOTIF_ROOMS = 9102;
    private static final int NOTIF_WIN = 9103;
    private static final int NOTIF_GIFT = 9104;

    private LocalEngagementScheduler() {}

    public static void ensureScheduled(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                EngagementWorker.class, 3, TimeUnit.HOURS)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req);
        scheduleAlarm(app, FIRST_ALARM_MS);
    }

    public static void rescheduleNow(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                EngagementWorker.class, 3, TimeUnit.HOURS)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req);
        scheduleAlarm(app, 10L * 60L * 1000L);
    }

    public static void onAlarmFired(@NonNull Context context) {
        maybePost(context);
        scheduleAlarm(context.getApplicationContext(), NEXT_ALARM_MS);
    }

    private static void scheduleAlarm(@NonNull Context app, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(app, EngagementAlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                    app, ALARM_REQ, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long trigger = SystemClock.elapsedRealtime() + Math.max(60_000L, delayMs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        } catch (Exception ignored) {
        }
    }

    public static final class EngagementWorker extends Worker {
        public EngagementWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            maybePost(getApplicationContext());
            return Result.success();
        }
    }

    private static void maybePost(@NonNull Context ctx) {
        if (AuraLiveApp.isAppInForeground()) return;
        if (VoiceRoomForegroundService.activeRoomId(ctx) != null) return;
        AppContainer container;
        try {
            container = ((AuraLiveApp) ctx).getContainer();
            String token = container.getSessionManager().getAccessToken();
            if (token == null || token.isEmpty()) return;
        } catch (Exception e) {
            return;
        }
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return;

        // Prefer real unfinished daily tasks from the server.
        TaskDigest digest = loadTaskDigest(container);
        if (digest != null && digest.pendingCount > 0) {
            postTasksFromServer(ctx, digest);
            bumpIndex(ctx);
            return;
        }
        postRotated(ctx);
    }

    private static void bumpIndex(@NonNull Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INDEX, 0);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_INDEX, index + 1).apply();
    }

    private static void postRotated(@NonNull Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INDEX, 0);
        Kind kind = Kind.values()[Math.floorMod(index, Kind.values().length)];
        bumpIndex(context);
        showFallback(context, kind);
    }

    enum Kind {
        TASKS, ROOMS, WIN, GIFT
    }

    private static final class TaskDigest {
        int pendingCount;
        int claimableCount;
        String bodyLine;
    }

    @Nullable
    private static TaskDigest loadTaskDigest(@NonNull AppContainer container) {
        try {
            Result<List<MiscDtos.TaskDto>> r = ApiCall.execute(container.getTasksApi().daily());
            if (!r.success || r.data == null || r.data.isEmpty()) return null;
            List<String> pendingTitles = new ArrayList<>();
            int claimable = 0;
            for (MiscDtos.TaskDto t : r.data) {
                if (t == null || t.claimed) continue;
                claimable += t.claimable ? 1 : 0;
                String title = t.title != null ? t.title.trim() : "";
                if (!title.isEmpty() && pendingTitles.size() < 3) {
                    if (t.progressLabel != null && !t.progressLabel.trim().isEmpty()) {
                        pendingTitles.add(title + " (" + t.progressLabel.trim() + ")");
                    } else if (t.target > 0) {
                        pendingTitles.add(title + " (" + Math.max(0, t.current) + "/" + t.target + ")");
                    } else {
                        pendingTitles.add(title);
                    }
                }
            }
            if (pendingTitles.isEmpty()) return null;
            TaskDigest d = new TaskDigest();
            d.pendingCount = pendingTitles.size();
            // Count all unclaimed, not only titles shown.
            int allPending = 0;
            for (MiscDtos.TaskDto t : r.data) {
                if (t != null && !t.claimed) allPending++;
            }
            d.pendingCount = Math.max(allPending, pendingTitles.size());
            d.claimableCount = claimable;
            d.bodyLine = android.text.TextUtils.join(" · ", pendingTitles);
            return d;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void postTasksFromServer(@NonNull Context context, @NonNull TaskDigest digest) {
        String title = digest.claimableCount > 0
                ? context.getString(R.string.notif_engage_tasks_claimable_title, digest.claimableCount)
                : context.getString(R.string.notif_engage_tasks_pending_title, digest.pendingCount);
        String body = digest.bodyLine != null && !digest.bodyLine.isEmpty()
                ? digest.bodyLine
                : context.getString(R.string.notif_engage_tasks_body);
        Intent tap = new Intent(context, TaskCenterActivity.class);
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        AuraNotificationHelper.show(
                context,
                CHANNEL_ID,
                title,
                body,
                "tasks",
                tap,
                NOTIF_TASKS,
                null);
    }

    private static void showFallback(@NonNull Context context, @NonNull Kind kind) {
        String title;
        String body;
        Intent tap;
        int notifId;
        String type;
        switch (kind) {
            case TASKS:
                title = context.getString(R.string.notif_engage_tasks_title);
                body = context.getString(R.string.notif_engage_tasks_body);
                tap = new Intent(context, TaskCenterActivity.class);
                notifId = NOTIF_TASKS;
                type = "tasks";
                break;
            case ROOMS:
                title = context.getString(R.string.notif_engage_rooms_title);
                body = context.getString(R.string.notif_engage_rooms_body);
                tap = new Intent(context, MainActivity.class);
                tap.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
                notifId = NOTIF_ROOMS;
                type = "live";
                break;
            case WIN:
                title = context.getString(R.string.notif_engage_win_title);
                body = context.getString(R.string.notif_engage_win_body);
                tap = new Intent(context, MainActivity.class);
                notifId = NOTIF_WIN;
                type = "system";
                break;
            default:
                title = context.getString(R.string.notif_engage_gift_title);
                body = context.getString(R.string.notif_engage_gift_body);
                tap = new Intent(context, MainActivity.class);
                notifId = NOTIF_GIFT;
                type = "gift";
                break;
        }
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        AuraNotificationHelper.show(
                context, CHANNEL_ID, title, body, type, tap, notifId, null);
    }
}
