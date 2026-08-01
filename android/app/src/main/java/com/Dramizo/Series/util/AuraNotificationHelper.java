package com.Dramizo.Series.util;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.notifications.OfficialNewsActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.service.VoiceRoomForegroundService;
import com.bumptech.glide.Glide;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuraNotificationHelper {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AuraNotificationHelper() {}

    public static void show(
            @NonNull Context context,
            @NonNull String channelId,
            @NonNull String title,
            @NonNull String body,
            @NonNull String type,
            @NonNull Intent tapIntent,
            int notificationId
    ) {
        show(context, channelId, title, body, type, tapIntent, notificationId, null);
    }

    public static void show(
            @NonNull Context context,
            @NonNull String channelId,
            @NonNull String title,
            @NonNull String body,
            @NonNull String type,
            @NonNull Intent tapIntent,
            int notificationId,
            @Nullable String avatarUrl
    ) {
        Context app = context.getApplicationContext();
        String safeTitle = title != null && !title.isEmpty() ? title : app.getString(R.string.app_name);
        String safeBody = body != null ? body : "";
        String safeType = type != null ? type : "system";
        String url = AssetCatalog.absoluteUrl(avatarUrl);

        // Show immediately with placeholder, then refresh when avatar loads (Redmi-safe).
        post(app, channelId, safeTitle, safeBody, safeType, tapIntent, notificationId, null);

        if (url == null || url.isEmpty()) return;
        IO.execute(() -> {
            Bitmap avatar = loadCircleAvatar(app, url);
            if (avatar != null) {
                post(app, channelId, safeTitle, safeBody, safeType, tapIntent, notificationId, avatar);
            }
        });
    }

    private static void post(
            @NonNull Context context,
            @NonNull String channelId,
            @NonNull String title,
            @NonNull String body,
            @NonNull String type,
            @NonNull Intent tapIntent,
            int notificationId,
            @Nullable Bitmap avatar
    ) {
        Intent launch = new Intent(tapIntent);
        // Keep CLEAR_TOP/SINGLE_TOP on the destination; root is Main via TaskStackBuilder.
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Intent home = new Intent(context, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (isChatIntent(launch)) {
            home.putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true);
        } else if (isOfficialNewsIntent(launch)) {
            home.putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true);
        } else {
            home.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
        }

        PendingIntent pi;
        if (launch.getComponent() != null
                && MainActivity.class.getName().equals(launch.getComponent().getClassName())) {
            pi = PendingIntent.getActivity(
                    context,
                    notificationId,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else if (isChatIntent(launch) && isVoiceRoomAlive(context)) {
            // Do NOT CLEAR_TOP Main — that would destroy VoiceRoom and kick the user out.
            Intent chat = new Intent(launch);
            chat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pi = PendingIntent.getActivity(
                    context,
                    notificationId,
                    chat,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            // Main → target so Back returns to the app instead of exiting.
            pi = TaskStackBuilder.create(context)
                    .addNextIntent(home)
                    .addNextIntent(launch)
                    .getPendingIntent(
                            notificationId,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        int fallbackIcon = iconForType(type);
        int underlay = ContextCompat.getColor(context, R.color.notification_avatar_fill);
        Bitmap contentBitmap = avatar != null
                ? avatar
                : bitmapFromDrawable(context, fallbackIcon, underlay);

        RemoteViews compact = new RemoteViews(context.getPackageName(), R.layout.notification_auralive_compact);
        compact.setTextViewText(R.id.tvNotificationTitle, title);
        compact.setTextViewText(R.id.tvNotificationBody, body);
        if (contentBitmap != null) {
            compact.setImageViewBitmap(R.id.imgNotificationContent, contentBitmap);
        } else {
            compact.setImageViewResource(R.id.imgNotificationContent, fallbackIcon);
        }

        RemoteViews expanded = new RemoteViews(context.getPackageName(), R.layout.notification_auralive_expanded);
        expanded.setTextViewText(R.id.tvNotificationTitle, title);
        expanded.setTextViewText(R.id.tvNotificationBody, body);
        expanded.setTextViewText(R.id.tvNotificationAction, actionLabelForType(type));
        if (contentBitmap != null) {
            expanded.setImageViewBitmap(R.id.imgNotificationContent, contentBitmap);
        } else {
            expanded.setImageViewResource(R.id.imgNotificationContent, fallbackIcon);
        }

        // Custom RemoteViews only: one avatar inside the layout.
        // No setLargeIcon / no DecoratedCustomViewStyle — those duplicate the photo / app icon.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setColor(ContextCompat.getColor(context, R.color.aurora_teal))
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(compact);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS denied on Android 13+.
        }
    }

    @Nullable
    private static Bitmap loadCircleAvatar(@NonNull Context context, @NonNull String url) {
        try {
            Bitmap raw = Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .submit(128, 128)
                    .get();
            int underlay = ContextCompat.getColor(context, R.color.notification_avatar_fill);
            return toCircle(raw, underlay);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Bitmap bitmapFromDrawable(@NonNull Context context, int resId, int underlayColor) {
        try {
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(context, resId);
            if (d == null) return null;
            int w = Math.max(1, d.getIntrinsicWidth());
            int h = Math.max(1, d.getIntrinsicHeight());
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            d.draw(canvas);
            return toCircle(bmp, underlayColor);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Circular avatar without transparent pixels.
     * RemoteViews / MIUI often paint transparent corners as solid black.
     */
    @Nullable
    private static Bitmap toCircle(@Nullable Bitmap src, int underlayColor) {
        if (src == null) return null;
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        paint.setColor(underlayColor != 0 ? underlayColor : Color.WHITE);
        canvas.drawRect(0, 0, size, size, paint);

        Bitmap masked = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(masked);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setFilterBitmap(true);
        maskCanvas.drawCircle(size / 2f, size / 2f, size / 2f, maskPaint);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        Rect srcRect = new Rect(
                (src.getWidth() - size) / 2,
                (src.getHeight() - size) / 2,
                (src.getWidth() + size) / 2,
                (src.getHeight() + size) / 2
        );
        Rect dst = new Rect(0, 0, size, size);
        maskCanvas.drawBitmap(src, srcRect, dst, maskPaint);

        canvas.drawBitmap(masked, 0, 0, null);
        masked.recycle();
        return output;
    }

    private static boolean isChatIntent(Intent launch) {
        return launch.getComponent() != null
                && ChatConversationActivity.class.getName().equals(launch.getComponent().getClassName());
    }

    private static boolean isVoiceRoomAlive(Context context) {
        if (VoiceRoomActivity.isRoomUiVisible()) return true;
        String active = VoiceRoomForegroundService.activeRoomId(context);
        return active != null && !active.isEmpty();
    }

    private static boolean isOfficialNewsIntent(Intent launch) {
        return launch.getComponent() != null
                && OfficialNewsActivity.class.getName().equals(launch.getComponent().getClassName());
    }

    private static int iconForType(String type) {
        if (type == null) return ImagePlaceholder.brandLogo();
        switch (type.toLowerCase(Locale.US)) {
            case "gift":
                return ImagePlaceholder.gift();
            case "chat":
            case "message":
            case "follow":
            case "friend":
            case "relation":
                // Prefer peer avatar when URL exists; brand mark as fallback (not a chat bubble).
                return ImagePlaceholder.brandLogo();
            case "system":
            case "agency":
            case "wallet":
            case "vip":
            case "withdraw":
                return ImagePlaceholder.brandLogo();
            default:
                return ImagePlaceholder.brandLogo();
        }
    }

    private static String actionLabelForType(String type) {
        if (type == null) return "فتح";
        switch (type.toLowerCase(Locale.US)) {
            case "gift":
                return "عرض الهدية";
            case "chat":
            case "message":
                return "فتح المحادثة";
            case "live":
            case "stream":
                return "دخول البث";
            case "follow":
            case "friend":
            case "relation":
                return "عرض الملف";
            default:
                return "فتح";
        }
    }
}
