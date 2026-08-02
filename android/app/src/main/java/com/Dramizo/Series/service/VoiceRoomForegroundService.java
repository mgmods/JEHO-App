package com.Dramizo.Series.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.ServiceCompat;
import androidx.core.content.LocusIdCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.zego.ZegoEngineManager;
import com.google.gson.JsonObject;

public class VoiceRoomForegroundService extends Service {
    public static final String CHANNEL_ID = "auralive_active_room_pro";
    public static final String BUBBLE_CHANNEL_ID = "auralive_room_bubble";
    private static final int NOTIFICATION_ID = 7301;
    private static final int BUBBLE_NOTIFICATION_ID = 7302;
    private static final int SUMMON_NOTIFICATION_ID = 7303;
    private static final int SEAT_INVITE_NOTIFICATION_ID = 7304;
    private static final String ACTION_START = "room.service.START";
    private static final String ACTION_LEAVE = "room.service.LEAVE";
    private static final String ACTION_TOGGLE_MIC = "room.service.TOGGLE_MIC";
    private static final String ACTION_UI_ATTACHED = "room.service.UI_ATTACHED";
    private static final String ACTION_UI_EXIT = "room.service.UI_EXIT";
    private static final String EXTRA_ROOM_ID = "roomId";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_COVER = "cover";
    private static final String EXTRA_MUSIC_URL = "musicUrl";
    private static final String EXTRA_MUSIC_STATUS = "musicStatus";
    private static final String EXTRA_MUSIC_POSITION = "musicPosition";
    private static final String EXTRA_MUSIC_STARTED_AT = "musicStartedAt";
    private static final String PREFS = "active_voice_room";
    private final Handler main = new Handler(Looper.getMainLooper());
    private String roomId;
    private String roomTitle;
    private String roomCoverUrl;
    private ExoPlayer musicPlayer;
    private boolean stopping;
    private android.graphics.Bitmap coverBitmap;

    private final ZegoEngineManager.RoomListener rtcListener =
            new ZegoEngineManager.RoomListener() {
                @Override public void onRoomStateChanged(String id, int state) {}
                @Override public void onStreamAdded(String streamId) {
                    String mine = ZegoEngineManager.audioStreamId(
                            ZegoEngineManager.getInstance().getCurrentUserId());
                    if (streamId != null && !streamId.equals(mine)
                            && !streamId.endsWith("_host")) {
                        ZegoEngineManager.getInstance().startPlayingAudio(streamId);
                    }
                }
                @Override public void onStreamRemoved(String streamId) {
                    ZegoEngineManager.getInstance().stopPlaying(streamId);
                }
            };

    private final RealtimeClient.RoomListener realtimeListener =
            new RealtimeClient.RoomListener() {
                @Override
                public void onRoomEvent(String id, String event, JsonObject payload,
                                        String fromUserId, String fromUsername) {
                    if (roomId == null || !roomId.equals(id) || event == null) return;
                    if ("chat:message".equals(event) || "room:chat".equals(event)) {
                        bufferChatWhileMinimized(payload, fromUserId, fromUsername);
                        return;
                    }
                    if ("room:music".equals(event)) {
                        String url = string(payload, "url");
                        String status = string(payload, "status");
                        long position = number(payload, "positionMs");
                        String startedAt = string(payload, "startedAt");
                        main.post(() -> applyMusic(url, status, position, startedAt));
                        return;
                    }
                    if ("room:summon".equals(event)) {
                        // Host is calling everyone back — including minimized guests.
                        String msg = string(payload, "message");
                        if (msg == null || msg.isEmpty()) {
                            msg = "صاحب الغرفة يستدعيك — ارجع للغرفة";
                        }
                        final String text = msg;
                        main.post(() -> postSummonAlert(text));
                        return;
                    }
                    if ("room:seat_invited".equals(event)) {
                        String target = string(payload, "userId");
                        // Targeted invite — only alert when this device's room is in background.
                        main.post(() -> postSeatInviteAlert(target));
                    }
                }
                @Override public void onUserJoined(String id, String userId, String username) {}
                @Override public void onUserLeft(String id, String userId) {}
                @Override public void onConnected() {}
                @Override public void onDisconnected() {}
            };

    /** Keep chat while UI is destroyed/minimized so re-open doesn't look emptied. */
    private void bufferChatWhileMinimized(JsonObject payload, String fromUserId, String fromUsername) {
        if (roomId == null || payload == null) return;
        String text = string(payload, "text");
        if (text == null || text.isEmpty()) return;
        String name = string(payload, "name");
        if (name == null) name = string(payload, "displayName");
        if (name == null) name = fromUsername != null ? fromUsername : "مستخدم";
        String uid = string(payload, "userId");
        if (uid == null) uid = fromUserId;
        com.Dramizo.Series.util.RoomChatMemory.append(roomId,
                new com.Dramizo.Series.util.RoomChatMemory.Line(
                        name,
                        text,
                        (int) number(payload, "vipLevel"),
                        (int) number(payload, "userLevel"),
                        string(payload, "frameUrl"),
                        uid,
                        string(payload, "avatarUrl"),
                        null,
                        number(payload, "wealthScore"),
                        number(payload, "charmScore")));
    }

    /** High-priority alert + reopen when host summons while room is minimized. */
    private void postSummonAlert(String message) {
        PendingIntent openIntent = openRoomIntent();
        Notification notification = new NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setContentTitle(roomTitle != null ? roomTitle : "الغرفة الصوتية")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openIntent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build();
        try {
            NotificationManagerCompat.from(this).notify(SUMMON_NOTIFICATION_ID, notification);
        } catch (RuntimeException ignored) {
        }
        try {
            Intent open = new Intent(this, VoiceRoomActivity.class)
                    .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
        } catch (RuntimeException ignored) {
            // Notification still lets the user return manually.
        }
    }

    /** Mic invite while minimized — open room and surface the accept dialog. */
    private void postSeatInviteAlert(String targetUserId) {
        String myId = null;
        try {
            myId = ((AuraLiveApp) getApplication()).getContainer()
                    .getSessionManager().getUserId();
        } catch (Exception ignored) {
        }
        if (myId == null || targetUserId == null || !myId.equals(targetUserId)) return;
        if (VoiceRoomActivity.isRoomUiVisible()) return;
        String message = "دعوة إلى المايك — اضغط للقبول أو الرفض";
        Intent open = new Intent(this, VoiceRoomActivity.class)
                .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId)
                .putExtra(VoiceRoomActivity.EXTRA_PENDING_SEAT_INVITE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(
                this, 7304, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setContentTitle(roomTitle != null ? roomTitle : "الغرفة الصوتية")
                .setContentText(message)
                .setContentIntent(openIntent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build();
        try {
            NotificationManagerCompat.from(this).notify(SEAT_INVITE_NOTIFICATION_ID, notification);
        } catch (RuntimeException ignored) {
        }
        try {
            startActivity(open);
        } catch (RuntimeException ignored) {
        }
    }

    private final Runnable renewToken = new Runnable() {
        @Override public void run() {
            if (roomId == null) return;
            AppContainer container = ((AuraLiveApp) getApplication()).getContainer();
            container.getIoExecutor().execute(() -> {
                Result<RoomDtos.JoinRoomResult> result =
                        ApiCall.execute(container.getRoomApi().zegoToken(roomId));
                if (result.success && result.data != null && result.data.token != null) {
                    String rtcRoom = result.data.zegoRoomId != null
                            ? result.data.zegoRoomId
                            : ZegoEngineManager.getInstance().getCurrentRoomId();
                    ZegoEngineManager.getInstance().renewRoomToken(rtcRoom, result.data.token);
                }
            });
            main.postDelayed(this, 35_000L);
        }
    };

    public static Intent startIntent(Context context, String roomId, String title, String coverUrl,
                                     String musicUrl, String musicStatus,
                                     long musicPosition, String musicStartedAt) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(EXTRA_ROOM_ID, roomId)
                .putString(EXTRA_TITLE, title)
                .putString(EXTRA_COVER, coverUrl)
                .apply();
        return new Intent(context, VoiceRoomForegroundService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROOM_ID, roomId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_COVER, coverUrl)
                .putExtra(EXTRA_MUSIC_URL, musicUrl)
                .putExtra(EXTRA_MUSIC_STATUS, musicStatus)
                .putExtra(EXTRA_MUSIC_POSITION, musicPosition)
                .putExtra(EXTRA_MUSIC_STARTED_AT, musicStartedAt);
    }

    public static void attachUi(Context context) {
        // UI is visible again — drop FGS notification/listeners, but KEEP active-room prefs
        // so Splash / mini-player can restore after process death.
        try {
            context.startService(new Intent(context, VoiceRoomForegroundService.class)
                    .setAction(ACTION_UI_ATTACHED));
        } catch (IllegalStateException ignored) {
        }
    }

    public static boolean hasActiveRoom(Context context, String roomId) {
        String active = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(EXTRA_ROOM_ID, null);
        return roomId != null && roomId.equals(active);
    }

    public static String activeRoomId(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(EXTRA_ROOM_ID, null);
    }

    public static String activeRoomTitle(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(EXTRA_TITLE, "الغرفة الصوتية");
    }

    public static String activeRoomCover(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(EXTRA_COVER, null);
    }

    public static void leaveActiveRoom(Context context) {
        String activeRoom = activeRoomId(context);
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        try {
            context.startService(new Intent(context, VoiceRoomForegroundService.class)
                    .setAction(ACTION_LEAVE)
                    .putExtra(EXTRA_ROOM_ID, activeRoom));
        } catch (IllegalStateException ignored) {
            context.stopService(new Intent(context, VoiceRoomForegroundService.class));
        }
    }

    /** Stop only the background owner; the attached activity performs the actual room leave. */
    public static void stopForUiExit(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        try {
            context.startService(new Intent(context, VoiceRoomForegroundService.class)
                    .setAction(ACTION_UI_EXIT));
        } catch (IllegalStateException ignored) {
            context.stopService(new Intent(context, VoiceRoomForegroundService.class));
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        musicPlayer = new ExoPlayer.Builder(this).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Process restart after kill — rebuild from prefs if possible.
        if (intent == null) {
            String savedRoom = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(EXTRA_ROOM_ID, null);
            if (savedRoom == null || savedRoom.isEmpty()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            intent = startIntent(
                    this,
                    savedRoom,
                    getSharedPreferences(PREFS, MODE_PRIVATE).getString(EXTRA_TITLE, "الغرفة الصوتية"),
                    getSharedPreferences(PREFS, MODE_PRIVATE).getString(EXTRA_COVER, null),
                    null, "stopped", 0L, null);
        }
        String action = intent.getAction();
        if (ACTION_LEAVE.equals(action)) {
            leaveRoom(intent.getStringExtra(EXTRA_ROOM_ID));
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE_MIC.equals(action)) {
            toggleMicFromNotification();
            return START_STICKY;
        }
        if (ACTION_UI_EXIT.equals(action)) {
            stopBackgroundOwner(true);
            return START_NOT_STICKY;
        }
        if (ACTION_UI_ATTACHED.equals(action)) {
            // Activity visible — hide notification, keep prefs for cold-start resume.
            stopBackgroundOwner(false);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_STICKY;

        roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        roomTitle = intent.getStringExtra(EXTRA_TITLE);
        roomCoverUrl = intent.getStringExtra(EXTRA_COVER);
        if (roomId == null || roomId.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(EXTRA_ROOM_ID, roomId)
                .putString(EXTRA_TITLE, roomTitle)
                .putString(EXTRA_COVER, roomCoverUrl)
                .apply();
        int foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
                && ZegoEngineManager.getInstance().isPublishing()) {
            foregroundTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildForegroundNotification(),
                foregroundTypes);
        loadCoverAsync();
        postRoomBubble();
        ZegoEngineManager.getInstance().addRoomListener(rtcListener);
        RealtimeClient.getInstance().addRoomListener(realtimeListener);
        main.removeCallbacks(renewToken);
        main.post(renewToken);
        applyMusic(
                intent.getStringExtra(EXTRA_MUSIC_URL),
                intent.getStringExtra(EXTRA_MUSIC_STATUS),
                intent.getLongExtra(EXTRA_MUSIC_POSITION, 0L),
                intent.getStringExtra(EXTRA_MUSIC_STARTED_AT));
        return START_STICKY;
    }

    private void toggleMicFromNotification() {
        boolean currentlyOn = ZegoEngineManager.getInstance().isPublishing();
        ZegoEngineManager.getInstance().setMicEnabled(!currentlyOn);
        try {
            NotificationManagerCompat.from(this)
                    .notify(NOTIFICATION_ID, buildForegroundNotification());
        } catch (Exception ignored) {
        }
    }

    private void loadCoverAsync() {
        final String url = AssetCatalog.absoluteUrl(roomCoverUrl);
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                android.graphics.Bitmap raw = com.bumptech.glide.Glide.with(getApplicationContext())
                        .asBitmap()
                        .load(url)
                        .submit(128, 128)
                        .get();
                if (raw == null) return;
                coverBitmap = roundBitmap(raw);
                main.post(() -> {
                    if (stopping || roomId == null) return;
                    try {
                        NotificationManagerCompat.from(this)
                                .notify(NOTIFICATION_ID, buildForegroundNotification());
                        ServiceCompat.startForeground(
                                this,
                                NOTIFICATION_ID,
                                buildForegroundNotification(),
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "room-notif-cover").start();
    }

    private static android.graphics.Bitmap roundBitmap(android.graphics.Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.Rect rect = new android.graphics.Rect(0, 0, size, size);
        canvas.drawOval(new android.graphics.RectF(rect), paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, null, rect, paint);
        return out;
    }

    private PendingIntent openRoomIntent() {
        Intent open = new Intent(this, VoiceRoomActivity.class)
                .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                this, 7301, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceAction(String action, int req) {
        return PendingIntent.getService(
                this, req,
                new Intent(this, VoiceRoomForegroundService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildForegroundNotification() {
        PendingIntent openIntent = openRoomIntent();
        PendingIntent leaveIntent = serviceAction(ACTION_LEAVE, 7302);
        PendingIntent micIntent = serviceAction(ACTION_TOGGLE_MIC, 7305);

        boolean micOn = ZegoEngineManager.getInstance().isPublishing();
        String title = roomTitle != null && !roomTitle.isEmpty()
                ? roomTitle : getString(R.string.voice_room);

        android.widget.RemoteViews compact = new android.widget.RemoteViews(
                getPackageName(), R.layout.notification_voice_room_compact);
        android.widget.RemoteViews expanded = new android.widget.RemoteViews(
                getPackageName(), R.layout.notification_voice_room_expanded);

        bindRoomRemoteViews(compact, title, micOn, openIntent, micIntent, leaveIntent);
        bindRoomRemoteViews(expanded, title, micOn, openIntent, micIntent, leaveIntent);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setContentTitle(title)
                .setContentText(getString(R.string.notif_room_in_room))
                .setContentIntent(openIntent)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(expanded)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(0xFF6C5CE7)
                .build();
    }

    private void bindRoomRemoteViews(
            android.widget.RemoteViews views,
            String title,
            boolean micOn,
            PendingIntent openIntent,
            PendingIntent micIntent,
            PendingIntent leaveIntent
    ) {
        views.setTextViewText(R.id.tvRoomTitle, title);
        views.setTextViewText(R.id.tvRoomSubtitle, getString(R.string.notif_room_in_room_hint));
        if (coverBitmap != null) {
            views.setImageViewBitmap(R.id.imgRoomCover, coverBitmap);
        } else {
            android.graphics.Bitmap logo =
                    com.Dramizo.Series.util.AuraNotificationHelper.circularLauncherIcon(this);
            if (logo != null) {
                views.setImageViewBitmap(R.id.imgRoomCover, logo);
            } else {
                views.setImageViewResource(R.id.imgRoomCover, R.mipmap.ic_launcher);
            }
        }
        views.setOnClickPendingIntent(R.id.btnNotifReturn, openIntent);
        views.setOnClickPendingIntent(R.id.btnNotifLeave, leaveIntent);
        views.setOnClickPendingIntent(R.id.btnNotifMic, micIntent);
        try {
            views.setImageViewResource(R.id.imgNotifMic,
                    micOn ? R.drawable.ic_pro_mic : R.drawable.ic_room_mic_muted);
            views.setTextViewText(R.id.tvNotifMic,
                    micOn ? getString(R.string.notif_room_mic) : getString(R.string.notif_room_mic_off));
        } catch (Exception ignored) {
            // Compact layout has no mic image/text ids.
        }
    }

    private void postRoomBubble() {
        PendingIntent openIntent = openRoomIntent();
        Intent bubbleActivity = new Intent(this, VoiceRoomActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
        Person roomPerson = new Person.Builder()
                .setName(roomTitle != null ? roomTitle : "الغرفة الصوتية")
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .build();
        String shortcutId = "voice_room_" + roomId;
        ShortcutManagerCompat.pushDynamicShortcut(
                this,
                new ShortcutInfoCompat.Builder(this, shortcutId)
                        .setShortLabel(roomTitle != null ? roomTitle : "الغرفة الصوتية")
                        .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                        .setIntent(bubbleActivity)
                        .setPerson(roomPerson)
                        .setLongLived(true)
                        .setLocusId(new LocusIdCompat(shortcutId))
                        .build());
        NotificationCompat.BubbleMetadata bubble =
                new NotificationCompat.BubbleMetadata.Builder(
                        openIntent,
                        IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                        .setDesiredHeight(640)
                        .setSuppressNotification(false)
                        .build();
        Notification notification = new NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jeho)
                .setContentTitle(roomTitle != null ? roomTitle : "الغرفة الصوتية")
                .setContentText("اضغط للرجوع إلى الغرفة")
                .setContentIntent(openIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setShortcutId(shortcutId)
                .setLocusId(new LocusIdCompat(shortcutId))
                .setBubbleMetadata(bubble)
                .addPerson(roomPerson)
                .build();
        try {
            NotificationManagerCompat.from(this)
                    .notify(BUBBLE_NOTIFICATION_ID, notification);
        } catch (RuntimeException ignored) {
            // The room stays alive even when this device rejects optional bubbles.
        }
    }

    private void applyMusic(String url, String status, long positionMs, String startedAt) {
        if (musicPlayer == null) return;
        if (url == null || url.isEmpty() || "stopped".equalsIgnoreCase(status)
                || url.regionMatches(true, 0, "local://", 0, 8)) {
            // Local phone music is mixed by the host into Zego — never stream via ExoPlayer here.
            musicPlayer.stop();
            return;
        }
        String absolute = AssetCatalog.absoluteUrl(url);
        if (absolute == null) return;
        musicPlayer.setMediaItem(MediaItem.fromUri(absolute));
        musicPlayer.prepare();
        long target = Math.max(0L, positionMs);
        if ("playing".equalsIgnoreCase(status) && startedAt != null) {
            try {
                target += Math.max(0L,
                        System.currentTimeMillis()
                                - java.time.Instant.parse(startedAt).toEpochMilli());
            } catch (Exception ignored) {
            }
        }
        musicPlayer.seekTo(target);
        musicPlayer.setPlayWhenReady("playing".equalsIgnoreCase(status));
    }

    private void leaveRoom(String requestedRoomId) {
        if (stopping) return;
        stopping = true;
        String leavingRoom = requestedRoomId != null ? requestedRoomId : roomId != null ? roomId
                : getSharedPreferences(PREFS, MODE_PRIVATE).getString(EXTRA_ROOM_ID, null);
        if (leavingRoom != null) {
            RealtimeClient.getInstance().leaveRoom(leavingRoom);
            AppContainer container = ((AuraLiveApp) getApplication()).getContainer();
            container.getIoExecutor().execute(() ->
                    container.getRoomRepository().leave(leavingRoom));
        }
        ZegoEngineManager.getInstance().logoutRoom();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        detachBackgroundOwner();
        NotificationManagerCompat.from(this).cancel(BUBBLE_NOTIFICATION_ID);
        NotificationManagerCompat.from(this).cancel(SUMMON_NOTIFICATION_ID);
        NotificationManagerCompat.from(this).cancel(SEAT_INVITE_NOTIFICATION_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** @param clearPrefs true on leave/exit; false when UI re-attaches (keep mini + Splash resume). */
    private void stopBackgroundOwner(boolean clearPrefs) {
        if (clearPrefs) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        }
        detachBackgroundOwner();
        NotificationManagerCompat.from(this).cancel(BUBBLE_NOTIFICATION_ID);
        NotificationManagerCompat.from(this).cancel(SUMMON_NOTIFICATION_ID);
        NotificationManagerCompat.from(this).cancel(SEAT_INVITE_NOTIFICATION_ID);
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    private void detachBackgroundOwner() {
        main.removeCallbacks(renewToken);
        RealtimeClient.getInstance().removeRoomListener(realtimeListener);
        ZegoEngineManager.getInstance().removeRoomListener(rtcListener);
        if (musicPlayer != null) {
            try {
                musicPlayer.stop();
            } catch (Exception ignored) {
            }
            // Keep player instance for reuse if service restarts quickly; release on destroy.
        }
    }

    @Override
    public void onDestroy() {
        detachBackgroundOwner();
        if (musicPlayer != null) {
            try {
                musicPlayer.release();
            } catch (Exception ignored) {
            }
            musicPlayer = null;
        }
        super.onDestroy();
    }

    private static String string(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long number(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsLong() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
