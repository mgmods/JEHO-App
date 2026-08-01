package com.Dramizo.Series.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.TaskCenterActivity;
import com.Dramizo.Series.service.VoiceRoomForegroundService;

import java.util.concurrent.TimeUnit;

/**
 * Local engagement notifications (tasks / rooms / wins) — custom professional layout.
 * Encourages users to reopen the app; skipped while app is in foreground or in a live room.
 */
public final class LocalEngagementScheduler {
    public static final String CHANNEL_ID = "auralive_engagement";
    private static final String WORK_NAME = "jeho_local_engagement";
    private static final String PREFS = "jeho_engagement";
    private static final String KEY_INDEX = "rotate_index";

    private LocalEngagementScheduler() {}

    public static void ensureScheduled(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                EngagementWorker.class, 6, TimeUnit.HOURS)
                .setInitialDelay(45, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req);
    }

    public static void rescheduleNow(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                EngagementWorker.class, 6, TimeUnit.HOURS)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req);
    }

    public static final class EngagementWorker extends Worker {
        public EngagementWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context ctx = getApplicationContext();
            if (AuraLiveApp.isAppInForeground()) return Result.success();
            if (VoiceRoomForegroundService.activeRoomId(ctx) != null) return Result.success();
            // Only for logged-in users.
            try {
                String token = ((AuraLiveApp) ctx).getContainer().getSessionManager().getAccessToken();
                if (token == null || token.isEmpty()) return Result.success();
            } catch (Exception e) {
                return Result.success();
            }
            postRotated(ctx);
            return Result.success();
        }
    }

    private static void postRotated(@NonNull Context context) {
        int index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_INDEX, 0);
        Kind kind = Kind.values()[Math.floorMod(index, Kind.values().length)];
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_INDEX, index + 1).apply();
        show(context, kind);
    }

    enum Kind {
        TASKS, ROOMS, WIN, GIFT
    }

    private static void show(@NonNull Context context, @NonNull Kind kind) {
        String badge;
        String title;
        String body;
        Intent tap;
        int notifId;
        switch (kind) {
            case TASKS:
                badge = context.getString(R.string.notif_engage_badge_tasks);
                title = context.getString(R.string.notif_engage_tasks_title);
                body = context.getString(R.string.notif_engage_tasks_body);
                tap = new Intent(context, TaskCenterActivity.class);
                notifId = 9101;
                break;
            case ROOMS:
                badge = context.getString(R.string.notif_engage_badge_rooms);
                title = context.getString(R.string.notif_engage_rooms_title);
                body = context.getString(R.string.notif_engage_rooms_body);
                tap = new Intent(context, MainActivity.class);
                tap.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
                notifId = 9102;
                break;
            case WIN:
                badge = context.getString(R.string.notif_engage_badge_win);
                title = context.getString(R.string.notif_engage_win_title);
                body = context.getString(R.string.notif_engage_win_body);
                tap = new Intent(context, MainActivity.class);
                notifId = 9103;
                break;
            default:
                badge = context.getString(R.string.notif_engage_badge_gift);
                title = context.getString(R.string.notif_engage_gift_title);
                body = context.getString(R.string.notif_engage_gift_body);
                tap = new Intent(context, MainActivity.class);
                notifId = 9104;
                break;
        }
        tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context, notifId, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_engagement);
        views.setTextViewText(R.id.tvEngageBadge, badge);
        views.setTextViewText(R.id.tvEngageTitle, title);
        views.setTextViewText(R.id.tvEngageBody, body);
        views.setImageViewResource(R.id.imgEngageIcon, R.mipmap.ic_launcher);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setColor(ContextCompat.getColor(context, R.color.aurora_teal))
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setCustomHeadsUpContentView(views);

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
