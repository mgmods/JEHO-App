package com.Dramizo.Series;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.friends.RequestsActivity;
import com.Dramizo.Series.presentation.createroom.CreateRoomActivity;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ActiveChatTracker;
import com.Dramizo.Series.util.InAppNotificationBanner;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraNotificationHelper;
import com.Dramizo.Series.util.NotificationRouter;
import com.Dramizo.Series.util.RoomSoundFx;
import com.Dramizo.Series.rtc.RoomRtcEngine;
import com.Dramizo.Series.rtc.RoomAudioSession;
import com.Dramizo.Series.service.VoiceRoomForegroundService;
import com.Dramizo.Series.zego.ZegoEngineManager;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

public class AuraLiveApp extends Application {
    /** Unwrapped context — SharedPreferences must not use locale ConfigurationContext. */
    private static volatile Context prefsStorageBase;

    private AppContainer container;
    private static volatile Activity foregroundActivity;
    private static volatile int startedActivities;
    private volatile String registeredFcmIdentity;
    // Release the VoIP microphone/audio mode while another app (e.g. WhatsApp)
    // is foregrounded. The room connection and playback remain alive.
    private boolean roomAudioSuspendedForExternalApp;
    private boolean roomAudioReleasedForBackground;
    private boolean roomMicWasEnabledBeforeExternalApp;
    private boolean roomSpeakerWasMutedBeforeExternalApp;
    // Android session ID of JEHO's own capture stream, used to distinguish it
    // from WhatsApp's stream after JEHO mutes its microphone.
    private volatile int roomMicCaptureSessionIdBeforeExternalApp = -1;
    // Updated for every Android recording-config callback, including while JEHO is foreground.
    private volatile boolean externalRecorderActive;
    @Nullable private String roomMicRoomIdBeforeExternalApp;
    @Nullable private AudioManager externalCaptureAudioManager;
    @Nullable private AudioManager.AudioRecordingCallback externalCaptureCallback;
    @Nullable private HandlerThread externalCaptureThread;
    @Nullable private Handler externalCaptureHandler;
    private final Runnable externalCaptureRestoreCheck = () -> {
        if (!roomAudioSuspendedForExternalApp) {
            if (!(roomAudioReleasedForBackground && isAppInForeground())) return;
        }
        boolean stillCapturing = snapshotExternalCaptureActive();
        externalRecorderActive = stillCapturing;
        if (!stillCapturing) {
            // Restore the room even if the user is still in WhatsApp. Audio-session
            // acquisition remains guarded by app foreground state, so this resumes
            // RTC mic/playback without reclaiming Android's communication mode.
            restoreRoomAudioAfterExternalApp();
        }
    };

    private static final int REQUEST_POST_NOTIFICATIONS = 8104;
    private static final String NOTIFICATION_PERMISSION_PREFS = "jeho_notification_permission";

    /** Always use for auth/session prefs (survives attachBaseContext locale wrap). */
    public static Context storageContext(Context any) {
        if (prefsStorageBase != null) return prefsStorageBase;
        if (any == null) return null;
        Context app = any.getApplicationContext();
        if (app instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) app).getBaseContext();
            if (base != null) return base;
        }
        return app;
    }

    public static Activity getForegroundActivity() {
        return foregroundActivity;
    }

    public static boolean isAppInForeground() {
        return startedActivities > 0;
    }

    /** Called only after Android reports that another foreground recorder has silenced JEHO capture. */
    private synchronized void suspendRoomAudioForExternalApp() {
        if (roomAudioSuspendedForExternalApp) return;
        RoomRtcEngine rtc = RoomRtcEngine.getInstance();
        String activeRoomId = rtc.getCurrentRoomId();
        if (activeRoomId == null || activeRoomId.isEmpty() || !rtc.isInRoom(activeRoomId)) {
            // The foreground room service remains authoritative after the room UI is
            // minimized/destroyed; do not miss WhatsApp capture just because the
            // Activity-owned RTC room marker is temporarily unavailable.
            activeRoomId = VoiceRoomForegroundService.activeRoomId(this);
        }
        if (activeRoomId == null || activeRoomId.isEmpty()
                || !VoiceRoomForegroundService.hasActiveRoom(this, activeRoomId)
                        && !rtc.isInRoom(activeRoomId)) return;

        roomMicRoomIdBeforeExternalApp = activeRoomId;
        roomMicWasEnabledBeforeExternalApp = rtc.isMicEnabled() && rtc.isPublishing();
        roomSpeakerWasMutedBeforeExternalApp = rtc.isSpeakerMuted();
        // Set the latch before muting our RTC capture. The follow-up Android callback
        // may then contain only WhatsApp's capture configuration (not JEHO's silenced
        // configuration), which still means the external recorder is active.
        roomAudioSuspendedForExternalApp = true;
        RoomAudioSession.setExternalCaptureSuspended(true);
        VoiceRoomForegroundService.suspendMusicForExternalCapture();
        VoiceRoomActivity.suspendMusicForExternalCapture();

        // Android has already granted capture priority to the external recorder.
        // Stop publishing locally so no silent/stale room microphone remains active.
        if (roomMicWasEnabledBeforeExternalApp) {
            rtc.setMicEnabled(false);
            updateRoomMicMutedState(activeRoomId, true);
        }
        // Also silence incoming room audio locally while the external app owns
        // the microphone. Preserve the user's previous manual speaker-mute state.
        if (!roomSpeakerWasMutedBeforeExternalApp) {
            rtc.setSpeakerMuted(true);
        }

        // MODE_IN_COMMUNICATION + voice focus can make Android report that a call
        // owns the microphone. Restore normal system audio routing for other apps.
        RoomAudioSession.release(this);
        VoiceRoomForegroundService.refreshAudioForegroundType();
    }

    private synchronized void restoreRoomAudioAfterExternalApp() {
        // Do a synchronous snapshot before reclaiming the microphone. The
        // AudioRecordingCallback can be delivered after onActivityStarted(), so
        // relying only on its last value can steal the mic back from WhatsApp.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && externalCaptureAudioManager != null) {
            try {
                java.util.List<AudioRecordingConfiguration> activeConfigs =
                        externalCaptureAudioManager.getActiveRecordingConfigurations();
                boolean anotherAppRecording = hasExternalCapture(activeConfigs,
                        roomAudioSuspendedForExternalApp);
                externalRecorderActive = anotherAppRecording;
            } catch (Throwable t) {
                android.util.Log.w("AuraLiveApp",
                        "Could not snapshot active recorders before audio restore: "
                                + t.getMessage());
            }
        }
        RoomRtcEngine rtc = RoomRtcEngine.getInstance();
        String expectedRoomId = roomMicRoomIdBeforeExternalApp != null
                ? roomMicRoomIdBeforeExternalApp : rtc.getCurrentRoomId();
        boolean sameRoomStillActive = expectedRoomId != null
                && (expectedRoomId.equals(rtc.getCurrentRoomId()) && rtc.isInRoom(expectedRoomId)
                || VoiceRoomForegroundService.hasActiveRoom(this, expectedRoomId));

        if (externalRecorderActive) {
            // The user may return to JEHO while WhatsApp is still recording. Do not
            // reclaim MODE_IN_COMMUNICATION or the microphone until that capture ends.
            return;
        }
        if (!roomAudioSuspendedForExternalApp && !roomAudioReleasedForBackground) return;

        roomAudioSuspendedForExternalApp = false;
        roomAudioReleasedForBackground = false;
        roomMicRoomIdBeforeExternalApp = null;
        roomMicCaptureSessionIdBeforeExternalApp = -1;

        RoomAudioSession.setExternalCaptureSuspended(false);
        if (sameRoomStillActive) {
            // Restore RTC mic/playback as soon as the external capture ends.
            // Reacquire the room route only after the external capture snapshot
            // confirms that WhatsApp (or the other recorder) has released the mic.
            // Restore the device route, then re-enable only the mic that was
            // actively publishing before the external recorder took the input.
            rtc.reapplyAudioRoute();

            if (roomMicWasEnabledBeforeExternalApp && rtc.isPublishing()) {
                rtc.setMicEnabledForExternalRestore(true, this);
                updateRoomMicMutedState(expectedRoomId, false);
                VoiceRoomActivity.onExternalMicRestored(true);
            } else {
                // Playback must resume even when the user had muted their own mic.
                RoomAudioSession.acquire(this);
            }
            // Restore room playback only if it was audible before the handoff.
            if (!roomSpeakerWasMutedBeforeExternalApp) {
                rtc.setSpeakerMuted(false);
            }
        }
        // Always clear both music handoff latches, even if the user left the room
        // while WhatsApp was recording. Each owner checks the preserved speaker
        // preference and its own active-room/player state before producing sound.
        VoiceRoomForegroundService.restoreMusicAfterExternalCapture();
        VoiceRoomActivity.restoreMusicAfterExternalCapture();
        roomMicWasEnabledBeforeExternalApp = false;
        roomSpeakerWasMutedBeforeExternalApp = false;
        VoiceRoomForegroundService.refreshAudioForegroundType();
    }

    /** Release Android's call audio mode while JEHO is backgrounded, but keep the
     * RTC room and local publishing state alive. This is what allows ordinary
     * recorders (WhatsApp, voice recorder, etc.) to capture the microphone. */
    private synchronized void releaseAudioModeForBackground() {
        if (roomAudioReleasedForBackground || isAppInForeground()) return;
        RoomRtcEngine rtc = RoomRtcEngine.getInstance();
        String roomId = rtc.getCurrentRoomId();
        boolean roomActive = roomId != null && !roomId.isEmpty() && rtc.isInRoom(roomId);
        if (!roomActive) {
            // The foreground service may own the live room while its Activity is
            // minimized. Keep the same background handoff behavior in that case.
            roomId = VoiceRoomForegroundService.activeRoomId(this);
            roomActive = roomId != null && !roomId.isEmpty()
                    && VoiceRoomForegroundService.hasActiveRoom(this, roomId);
        }
        if (!roomActive) return;

        // If another app was already recording before JEHO moved to background,
        // the recording callback may not fire again after the lifecycle transition.
        // Apply the full temporary handoff immediately in that case.
        if (externalRecorderActive) {
            suspendRoomAudioForExternalApp();
            return;
        }
        roomAudioReleasedForBackground = true;
        RoomAudioSession.release(this);
        VoiceRoomForegroundService.refreshAudioForegroundType();
    }

    /**
     * Wait for Android to report that JEHO's own microphone capture was silenced
     * by a higher-priority foreground recorder/call. Merely backgrounding JEHO
     * must never mute the room microphone.
     */
    private void registerExternalCaptureMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        externalCaptureAudioManager = manager;
        // Process capture arbitration away from the UI thread. Android can notify
        // us while WhatsApp is starting a voice note; keeping this callback off the
        // main looper avoids extra delay if JEHO's UI is busy or animating.
        externalCaptureThread = new HandlerThread("jeho-external-audio-monitor");
        externalCaptureThread.start();
        externalCaptureHandler = new Handler(externalCaptureThread.getLooper());
        externalCaptureCallback = new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs) {
                // AudioRecordingCallback reports the active capture set. Do not gate this
                // on foreground state: if the user returns to JEHO while WhatsApp is
                // still recording, we must keep JEHO's mic and room playback suspended.
                boolean anotherAppRecording = hasExternalCapture(configs,
                        roomAudioSuspendedForExternalApp);
                externalRecorderActive = anotherAppRecording;

                RoomRtcEngine rtc = RoomRtcEngine.getInstance();
                String roomId = rtc.getCurrentRoomId();
                boolean roomActive = roomId != null && !roomId.isEmpty()
                        && rtc.isInRoom(roomId);
                if (!roomActive) {
                    String serviceRoomId = VoiceRoomForegroundService.activeRoomId(AuraLiveApp.this);
                    roomActive = serviceRoomId != null && !serviceRoomId.isEmpty()
                            && VoiceRoomForegroundService.hasActiveRoom(AuraLiveApp.this, serviceRoomId);
                }
                if (!roomActive) {
                    // Clear the temporary audio-focus latch if the user left the room
                    // while WhatsApp was recording; otherwise the next room would stay muted.
                    if (!anotherAppRecording && roomAudioSuspendedForExternalApp) {
                        restoreRoomAudioAfterExternalApp();
                    }
                    return;
                }

                if (anotherAppRecording) {
                    Handler handler = externalCaptureHandler;
                    if (handler != null) handler.removeCallbacks(externalCaptureRestoreCheck);
                    // Pause JEHO room audio whenever another app owns the microphone,
                    // including WhatsApp voice recording. Do not depend on whether JEHO
                    // is currently foreground: Android may deliver lifecycle callbacks
                    // after the external recorder has already taken capture priority.
                    suspendRoomAudioForExternalApp();
                } else if (roomAudioSuspendedForExternalApp) {
                    // Require a stable capture-free window. Some Android/OEM builds
                    // briefly emit an empty recording-config list while WhatsApp changes
                    // recorder state; a 500 ms restore caused room music to flicker back
                    // on during a still-active voice note.
                    Handler handler = externalCaptureHandler;
                    if (handler != null) {
                        handler.removeCallbacks(externalCaptureRestoreCheck);
                        handler.postDelayed(externalCaptureRestoreCheck, 1500L);
                    } else {
                        restoreRoomAudioAfterExternalApp();
                    }
                } else if (anotherAppRecording && externalCaptureHandler != null) {
                    externalCaptureHandler.removeCallbacks(externalCaptureRestoreCheck);
                }
            }
        };
        try {
            manager.registerAudioRecordingCallback(
                    externalCaptureCallback, externalCaptureHandler);
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "External recorder monitor unavailable: "
                    + t.getMessage());
        }
    }

    /**
     * While JEHO capture is still running, Android marks JEHO's configuration as
     * silenced when WhatsApp owns the input. After JEHO mutes its RTC mic, that
     * configuration disappears; any remaining active capture configuration then
     * belongs to the external recorder. This lets us detect its stop reliably.
     */
    private synchronized boolean hasExternalCapture(
            @Nullable java.util.List<AudioRecordingConfiguration> configs,
            boolean ourCaptureAlreadySuspended) {
        if (configs == null) return ourCaptureAlreadySuspended;

        // Do not rely only on isClientSilenced(): several Android/OEM builds do
        // not mark JEHO's AudioRecord as silenced even after WhatsApp has taken
        // microphone priority. The recording configuration exposes the client
        // package on Android 10+, so identify external capture directly.
        boolean externalPackageActive = false;
        for (AudioRecordingConfiguration config : configs) {
            if (config == null) continue;
            try {
                String clientPackage = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // getClientPackageName() is available on newer Android SDKs but
                    // is not exposed by every compileSdk used by this project. Use
                    // reflection so the external-recorder monitor remains compatible
                    // with the current Android build toolchain while still using the
                    // API at runtime when the platform provides it.
                    try {
                        java.lang.reflect.Method method =
                                AudioRecordingConfiguration.class
                                        .getMethod("getClientPackageName");
                        Object value = method.invoke(config);
                        if (value instanceof String) clientPackage = (String) value;
                    } catch (Throwable ignored) {
                        clientPackage = null;
                    }
                }
                if (clientPackage != null && !clientPackage.isEmpty()) {
                    if (!getPackageName().equals(clientPackage)) {
                        externalPackageActive = true;
                    } else {
                        if (roomMicCaptureSessionIdBeforeExternalApp < 0) {
                            roomMicCaptureSessionIdBeforeExternalApp =
                                    config.getClientAudioSessionId();
                        }
                    }
                } else if (config.isClientSilenced() && !ourCaptureAlreadySuspended) {
                    roomMicCaptureSessionIdBeforeExternalApp =
                            config.getClientAudioSessionId();
                    externalPackageActive = true;
                } else {
                    int sessionId = config.getClientAudioSessionId();
                    if (ourCaptureAlreadySuspended) {
                        // Fallback for Android/OEM builds that omit package names:
                        // WhatsApp owns a different AudioRecord session from JEHO.
                        if (sessionId != roomMicCaptureSessionIdBeforeExternalApp) {
                            externalPackageActive = true;
                        }
                    } else if (config.isClientSilenced()) {
                        roomMicCaptureSessionIdBeforeExternalApp = sessionId;
                        externalPackageActive = true;
                    } else if (roomMicCaptureSessionIdBeforeExternalApp < 0) {
                        // Remember JEHO's first active capture session before another
                        // app starts recording, so a second session can be identified.
                        roomMicCaptureSessionIdBeforeExternalApp = sessionId;
                    } else if (sessionId != roomMicCaptureSessionIdBeforeExternalApp) {
                        externalPackageActive = true;
                    }
                }
            } catch (Throwable ignored) {
                // If an active recorder cannot be classified, fail safe and keep
                // JEHO muted rather than stealing the microphone from WhatsApp.
                externalPackageActive = true;
            }
        }
        if (externalPackageActive) return true;
        if (ourCaptureAlreadySuspended) {
            // Zego may keep JEHO's muted AudioRecord alive. Its own session is
            // not evidence that an external app is still recording.
            return false;
        }
        return false;
    }

    /** Keep the microphone FGS type that was started while JEHO was visible.
     * This lets RTC restore its previously-active mic after WhatsApp finishes,
     * even when JEHO is still backgrounded (Android 14+ while-in-use rules). */
    public boolean shouldKeepRoomMicrophoneForegroundType() {
        return roomAudioSuspendedForExternalApp && roomMicWasEnabledBeforeExternalApp;
    }

    /** Synchronous recheck used by the delayed restore and foreground transition. */
    private boolean snapshotExternalCaptureActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || externalCaptureAudioManager == null) return externalRecorderActive;
        try {
            java.util.List<AudioRecordingConfiguration> configs =
                    externalCaptureAudioManager.getActiveRecordingConfigurations();
            return hasExternalCapture(configs, roomAudioSuspendedForExternalApp);
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp",
                    "Could not recheck active recorder before restoring room audio: "
                            + t.getMessage());
            return true; // Fail safe: do not steal the microphone on an uncertain snapshot.
        }
    }

    /** Stop monitoring once the application process is terminating. */
    @Override
    public void onTerminate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && externalCaptureAudioManager != null
                && externalCaptureCallback != null) {
            try {
                externalCaptureAudioManager.unregisterAudioRecordingCallback(
                        externalCaptureCallback);
            } catch (Throwable ignored) {
            }
        }
        externalCaptureAudioManager = null;
        externalCaptureCallback = null;
        if (externalCaptureThread != null) {
            try {
                externalCaptureThread.quitSafely();
            } catch (Throwable ignored) {
            }
        }
        externalCaptureThread = null;
        externalCaptureHandler = null;
        super.onTerminate();
    }

    private void updateRoomMicMutedState(@Nullable String roomId, boolean muted) {
        if (roomId == null || roomId.isEmpty() || container == null) return;
        try {
            container.getIoExecutor().execute(() ->
                    container.getRoomRepository().setMic(roomId, muted));
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "Background room mic state update skipped: "
                    + t.getMessage());
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        prefsStorageBase = base;
        // Language applied after container exists; use plain prefs fallback for early boot.
        String lang = "ar";
        try {
            lang = base.getSharedPreferences("auralive_lang", MODE_PRIVATE).getString("language", "ar");
        } catch (Exception ignored) {
        }
        super.attachBaseContext(com.Dramizo.Series.util.LocaleHelper.wrap(base, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        com.Dramizo.Series.util.MediaAssetSync.hydrate(this);
        com.Dramizo.Series.util.RoomChatMemory.init(this);
        // Fetch runtime Zego client settings early (non-blocking).
        try {
            container.getIoExecutor().execute(() -> {
                ZegoEngineManager.getInstance().fetchAndApplyRemote(this, container.getConfigApi());
                AppFeatures.refresh(container);
                // Paint cached navigation immediately; MainActivity performs the single
                // network refresh after its first frame instead of racing SplashActivity.
                com.Dramizo.Series.util.RemoteNavIcons.hydrateFromCache(container);
                com.Dramizo.Series.util.RemoteSeatStickers.hydrateFromCache(container);
                com.Dramizo.Series.util.RemoteSeatStickers.refreshBlocking(container);
            });
        } catch (Exception ignored) {
        }
        // Sync language helper prefs with secure session
        try {
            String lang = container.getSessionManager().getLanguage();
            getSharedPreferences("auralive_lang", MODE_PRIVATE).edit().putString("language", lang).apply();
            com.Dramizo.Series.util.LocaleHelper.wrap(this, lang);
            String tag = com.Dramizo.Series.util.LocaleHelper.normalizeTag(lang);
            AppCompatDelegate.setApplicationLocales(
                    "system".equals(tag)
                            ? LocaleListCompat.getEmptyLocaleList()
                            : LocaleListCompat.forLanguageTags(tag));
        } catch (Exception ignored) {
        }
        createNotificationChannel();
        try {
            com.Dramizo.Series.util.LocalEngagementScheduler.ensureScheduled(this);
        } catch (Exception ignored) {
        }
        registerRealtimeNotifications();
        try {
            com.Dramizo.Series.util.InviteReferralHelper.captureInstallReferrer(this);
        } catch (Exception ignored) {
        }
        registerExternalCaptureMonitor();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                EdgeToEdgeHelper.apply(activity);
                if (activity.getClass().getSimpleName().equals("SplashActivity")) return;
                if (activity instanceof MainActivity
                        || activity instanceof VoiceRoomActivity
                        || activity instanceof CreateRoomActivity) {
                    return;
                }
                activity.getWindow().getDecorView().post(() ->
                        AppLoadingOverlay.showOnEntry(activity));
            }
            @Override public void onActivityStarted(Activity activity) {
                boolean returningFromBackground = startedActivities == 0;
                startedActivities++;
                if (returningFromBackground) restoreRoomAudioAfterExternalApp();
                // ThemedActivity pads contentRoot once in onPostCreate — skip global double-pad.
                if (activity instanceof ThemedActivity) return;
                // Skip shells that manage their own insets
                if (activity instanceof MainActivity
                        || activity instanceof VoiceRoomActivity
                        || activity instanceof ProfileActivity
                        || activity instanceof CreateRoomActivity
                        || activity instanceof RequestsActivity
                        || activity instanceof ChatConversationActivity
                        || activity instanceof LoginActivity) {
                    return;
                }
                View content = activity.findViewById(android.R.id.content);
                if (!(content instanceof ViewGroup)) return;
                View root = ((ViewGroup) content).getChildAt(0);
                if (root == null) return;
                Object tagged = root.getTag(R.id.tag_edge_padded);
                if (tagged != null) return;
                root.setTag(R.id.tag_edge_padded, Boolean.TRUE);
                EdgeToEdgeHelper.padSystemBars(root);
            }
            @Override public void onActivityResumed(Activity activity) {
                foregroundActivity = activity;
                if (activity instanceof MainActivity) {
                    requestNotificationPermission(activity);
                }
                registerFcmDeviceIfReady();
                View decor = activity.getWindow() != null
                        ? activity.getWindow().getDecorView()
                        : null;
                if (decor != null) {
                    decor.post(() ->
                            com.Dramizo.Series.util.RemoteTheme.applyMappedAssets(decor));
                }
            }
            @Override public void onActivityPaused(Activity activity) {
                if (foregroundActivity == activity) foregroundActivity = null;
            }
            @Override public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (foregroundActivity == activity) foregroundActivity = null;
                // Release only Android's call mode/focus when the last JEHO screen
                // goes to background. Keep RTC publishing, room playback and the music
                // foreground service alive; this lets WhatsApp acquire the microphone.
                // The recording monitor performs the full temporary room-audio handoff
                // only when an external recorder actually starts capturing.
                if (startedActivities == 0) releaseAudioModeForBackground();
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        try {
            container.getIoExecutor().execute(() -> {
                try {
                    RoomRtcEngine.getInstance().init(AuraLiveApp.this);
                } catch (Throwable t) {
                    android.util.Log.w("AuraLiveApp", "ZEGO init deferred: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "ZEGO init schedule failed: " + t.getMessage());
        }
        try {
            RoomSoundFx.init(this);
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "RoomSoundFx init skipped: " + t.getMessage());
        }
        try {
            FirebaseMessaging.getInstance().setAutoInitEnabled(true);
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        container.getSessionManager().saveFcmToken(token);
                        registerFcmDeviceIfReady();
                    })
                    .addOnFailureListener(e -> android.util.Log.w("AuraLiveApp", "FCM token skipped: " + e.getMessage()));
        } catch (Throwable t) {
            android.util.Log.w("AuraLiveApp", "Firebase not configured yet: " + t.getMessage());
        }
    }

    public AppContainer getContainer() {
        return container;
    }

    /** Register the current FCM token after authentication is available.
     * Token creation can happen before login, so onNewToken alone is not enough.
     */
    public void registerFcmDeviceIfReady() {
        if (container == null) return;
        String token = container.getSessionManager().getFcmToken();
        String userId = container.getSessionManager().getUserId();
        String accessToken = container.getSessionManager().getAccessToken();
        if (token == null || token.trim().isEmpty()
                || userId == null || userId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()) {
            // Clear the in-memory dedupe key while logged out. Otherwise a
            // logout/login to the same account can skip registration after the
            // backend has marked that device inactive.
            registeredFcmIdentity = null;
            return;
        }
        String identity = userId + ":" + token;
        if (identity.equals(registeredFcmIdentity)) return;
        registeredFcmIdentity = identity;
        container.getIoExecutor().execute(() -> {
            try {
                com.Dramizo.Series.domain.model.Result<Object> result =
                        container.getNotificationRepository().registerDevice(token, "android");
                if (result == null || !result.success) {
                    registeredFcmIdentity = null;
                    android.util.Log.w("AuraLiveApp", "FCM device registration failed: "
                            + (result != null ? result.error : "empty API result"));
                } else {
                    android.util.Log.i("AuraLiveApp", "FCM device registered for current account");
                }
            } catch (Exception e) {
                registeredFcmIdentity = null;
                android.util.Log.w("AuraLiveApp", "FCM device registration deferred: " + e.getMessage());
            }
        });
    }

    private void registerRealtimeNotifications() {
        RealtimeClient.getInstance().addUserListener(new RealtimeClient.UserListener() {
            @Override
            public void onUserEvent(String event, JsonObject payload) {
                if ("account:restricted".equals(event)) {
                    if (container != null) container.getSessionManager().clearAuth();
                    RealtimeClient.getInstance().disconnect();
                    Activity activity = foregroundActivity;
                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() -> {
                            Intent login = new Intent(
                                    activity,
                                    com.Dramizo.Series.presentation.auth.LoginActivity.class);
                            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            activity.startActivity(login);
                            activity.finishAffinity();
                        });
                    }
                    return;
                }
                if ("account:reset".equals(event)) {
                    // Admin baseline wipe — refresh /me so wallet/VIP/frames clear immediately.
                    if (container == null) return;
                    container.getIoExecutor().execute(() -> {
                        try {
                            container.getUserRepository().getMe();
                        } catch (Exception ignored) {
                        }
                    });
                    return;
                }
                if ("celebration:toast".equals(event) && payload != null) {
                    if (container != null
                            && container.getSessionManager().isMuteCelebrationPopups()) {
                        return;
                    }
                    // Event payloads have existed in both generic and lucky-specific shapes.
                    // Normalize them here so the global toast always receives the winner portrait,
                    // name, coin amount and X multiplier instead of rendering an empty template.
                    String kindValue = jsonStringAny(payload, null, "kind", "eventType", "type");
                    if ("lucky:hit".equalsIgnoreCase(kindValue) || "lucky_hit_win".equalsIgnoreCase(kindValue)) {
                        kindValue = "lucky_hit";
                    }
                    String displayNameValue = jsonStringAny(payload, null,
                            "displayName", "winnerName", "senderName", "username", "userName", "name");
                    String titleValue = jsonStringAny(payload, null, "title", "headline");
                    if (titleValue == null || titleValue.trim().isEmpty()
                            || "مبروك!".equals(titleValue.trim()) || "مبروك".equals(titleValue.trim())) {
                        titleValue = displayNameValue != null ? displayNameValue : "مبروك الفوز!";
                    }
                    String bodyValue = jsonStringAny(payload, null, "body", "message", "description", "resultText");
                    String avatarValue = jsonStringAny(payload, null,
                            "avatarUrl", "senderAvatarUrl", "winnerAvatarUrl", "profileImageUrl",
                            "profilePictureUrl", "avatar", "photoUrl", "imageUrl");
                    String roomIdValue = jsonStringAny(payload, null, "roomId", "targetRoomId");
                    String badgeCandidate = jsonStringAny(payload, null,
                            "giftIconUrl", "gameIconUrl", "gameCoverUrl", "giftImageUrl");
                    final String badge = badgeCandidate;
                    long coinsValue = jsonLongAny(payload, 0L,
                            "coinsWon", "winCoins", "luckyCoinsWon", "wonCoins", "coins", "amount");
                    String gameTitleValue = jsonStringAny(payload, null, "gameTitle", "gameName");
                    double multiplierValue = Math.max(0d, jsonDoubleAny(payload, 0d,
                            "multiplier", "luckyMultiplier", "winMultiplier", "times", "x", "count"));
                    if ("lucky_hit".equalsIgnoreCase(kindValue)) {
                        if (coinsValue <= 0L) coinsValue = jsonLongAny(payload, 0L, "rewardCoins", "winAmount");
                        if (multiplierValue <= 0d) multiplierValue = Math.max(0d,
                                jsonDoubleAny(payload, 0d, "luckyX", "multiplierX", "xCount"));
                        if (bodyValue == null || bodyValue.trim().isEmpty()) {
                            bodyValue = coinsValue > 0L
                                    ? ("فاز بـ " + String.format(java.util.Locale.US, "%,d", coinsValue) + " كوينز")
                                    : "مبروك الفوز بهدية الحظ";
                        }
                    } else if (bodyValue == null) {
                        bodyValue = "";
                    }
                    final String kind = kindValue;
                    final String displayName = displayNameValue;
                    final String title = titleValue;
                    final String body = bodyValue;
                    final String avatar = avatarValue;
                    final String roomId = roomIdValue;
                    final String dedupe = jsonStringAny(payload, body, "id", "eventId", "dedupeKey");
                    final long coinsFinal = coinsValue;
                    final String gameFinal = gameTitleValue;
                    final double mulFinal = multiplierValue;
                    final String roomFinal = roomId;
                    final String winnerUserId = jsonStringAny(payload, null,
                            "userId", "senderId", "winnerId", "winnerUserId");
                    final double winnerMultiplier = mulFinal;
                    final boolean appVisible = isAppInForeground();
                    VoiceRoomActivity aliveRoom = VoiceRoomActivity.getAliveInstance();
                    Activity activity = foregroundActivity;

                    // Lucky wins use one compact global banner on whichever app section is visible,
                    // including Home while a voice room remains alive in the background.
                    if ("lucky_hit".equalsIgnoreCase(kind) && appVisible
                            && activity != null && !activity.isFinishing()
                            // In the winning room, VoiceRoomActivity renders the dedicated center toast.
                            // Other rooms and app sections keep the standard top toast.
                            && !(activity instanceof VoiceRoomActivity roomActivity
                                    && roomActivity.isSameRoom(roomFinal))) {
                        activity.runOnUiThread(() ->
                                com.Dramizo.Series.util.GlobalCelebrationToast.show(
                                        activity, title, body, avatar, badge, dedupe,
                                        28, mulFinal, coinsFinal, displayName, kind, roomFinal));
                    }

                    // Prefer live room (even when Home/minimized UI paused) so chat bubbles land.
                    if (aliveRoom != null) {
                        aliveRoom.runOnUiThread(() -> aliveRoom.onGlobalCelebration(
                                kind, title, body, displayName, avatar, badge, dedupe,
                                coinsFinal, gameFinal, roomFinal, winnerMultiplier, winnerUserId));
                        if (!appVisible) {
                            showCelebrationSystemNotification(title, body, avatar, dedupe);
                        }
                        return;
                    }

                    if (activity != null && !activity.isFinishing()) {
                        activity.runOnUiThread(() -> {
                            if (activity instanceof VoiceRoomActivity room) {
                                room.onGlobalCelebration(
                                        kind, title, body, displayName, avatar, badge, dedupe,
                                        coinsFinal, gameFinal, roomFinal, winnerMultiplier, winnerUserId);
                            } else {
                                com.Dramizo.Series.util.GlobalCelebrationToast.show(
                                        activity, title, body, avatar, badge, dedupe,
                                        28, mulFinal, coinsFinal, displayName, kind, roomFinal);
                            }
                        });
                        return;
                    }

                    // Fully outside the app — system heads-up notification.
                    showCelebrationSystemNotification(title, body, avatar, dedupe);
                    return;
                }
                // Direct-message realtime events must produce an in-app banner even though
                // they are not persisted as generic notification:new inbox rows.
                if ("chat:message".equals(event) && payload != null) {
                    try {
                        JsonObject message = payload.has("message") && payload.get("message").isJsonObject()
                                ? payload.getAsJsonObject("message") : payload;
                        String conversationId = jsonString(payload, "conversationId",
                                jsonString(message, "conversationId", null));
                        String senderId = jsonString(message, "senderId",
                                message.has("sender") && message.get("sender").isJsonObject()
                                        ? jsonString(message.getAsJsonObject("sender"), "id", null) : null);
                        String currentUserId = container != null
                                ? container.getSessionManager().getUserId() : null;
                        if (senderId != null && senderId.equals(currentUserId)) return;
                        if (ActiveChatTracker.isViewing(conversationId)) return;
                        if (container != null
                                && container.getSessionManager().isMuteMessageNotifications()) return;
                        Activity chatActivity = foregroundActivity;
                        if (chatActivity == null || chatActivity.isFinishing()
                                || chatActivity.isDestroyed()) return;
                        JsonObject sender = message.has("sender") && message.get("sender").isJsonObject()
                                ? message.getAsJsonObject("sender") : new JsonObject();
                        String senderName = jsonString(sender, "displayName",
                                jsonString(sender, "username", "رسالة جديدة"));
                        String body = jsonString(message, "content", "لديك رسالة جديدة");
                        String messageId = jsonString(message, "id", null);
                        String avatar = jsonString(sender, "avatarUrl", null);
                        Map<String, Object> routeData = new HashMap<>();
                        routeData.put("conversationId", conversationId);
                        routeData.put("senderName", senderName);
                        if (senderId != null) routeData.put("peer_id", senderId);
                        Intent chatIntent = NotificationRouter.build(chatActivity, "chat", routeData);
                        if (conversationId != null) {
                            chatIntent.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, conversationId);
                        }
                        chatIntent.putExtra(ChatConversationActivity.EXTRA_TITLE, senderName);
                        if (senderId != null) chatIntent.putExtra(ChatConversationActivity.EXTRA_PEER_ID, senderId);
                        if (avatar != null) chatIntent.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatar);
                        String dedupe = conversationId != null && messageId != null
                                ? "chat:" + conversationId + ":" + messageId : null;
                        InAppNotificationBanner.show(chatActivity, senderName, body, "chat",
                                chatIntent, dedupe, avatar);
                    } catch (Exception e) {
                        android.util.Log.w("AuraLiveApp", "Realtime chat banner failed: " + e.getMessage());
                    }
                    return;
                }
                if (!"notification:new".equals(event) || payload == null) return;
                Activity activity = foregroundActivity;
                if (activity == null || activity.isFinishing()) return;

                String title = jsonString(payload, "title", getString(R.string.app_name));
                String body = jsonString(payload, "body", "");
                String type = jsonString(payload, "type", "system");
                String notifRowId = jsonString(payload, "id", null);
                Map<String, Object> data = new HashMap<>();
                JsonElement rawData = payload.get("data");
                if (rawData != null && rawData.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry
                            : rawData.getAsJsonObject().entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value != null && value.isJsonPrimitive()) {
                            data.put(entry.getKey(), value.getAsString());
                        }
                    }
                }
                Object convObj = data.get("conversationId");
                if (convObj == null) convObj = data.get("conversation_id");
                String conversationId = convObj != null ? String.valueOf(convObj) : null;
                if (ActiveChatTracker.shouldMute(type, conversationId)) {
                    return;
                }
                if (ActiveChatTracker.isChatType(type)
                        && container != null
                        && container.getSessionManager().isMuteMessageNotifications()) {
                    return;
                }
                Intent intent = NotificationRouter.build(activity, type, data);
                if (conversationId != null && !conversationId.isEmpty()) {
                    intent.putExtra(
                            ChatConversationActivity.EXTRA_CONVERSATION_ID,
                            conversationId);
                }
                Object nameObj = data.get("senderName");
                if (nameObj == null) nameObj = data.get("peer_name");
                if (nameObj != null) {
                    intent.putExtra(ChatConversationActivity.EXTRA_TITLE, String.valueOf(nameObj));
                }
                Object avatarObj = data.get("avatarUrl");
                if (avatarObj == null) avatarObj = data.get("senderAvatarUrl");
                String avatarUrl = avatarObj != null ? String.valueOf(avatarObj) : null;
                if (avatarUrl != null) {
                    intent.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatarUrl);
                }
                int notifyId = conversationId != null && !conversationId.isEmpty()
                        ? ("chat:" + conversationId).hashCode()
                        : notificationIdKey(notifRowId);
                // Custom system notification while in chat list / party / any other screen.
                boolean directMessage = ActiveChatTracker.isChatType(type)
                        && conversationId != null && !conversationId.isEmpty();
                AuraNotificationHelper.show(
                        activity,
                        directMessage ? AuraNotificationHelper.CHANNEL_MESSAGES : "auralive_default",
                        title,
                        body,
                        type,
                        intent,
                        notifyId,
                        avatarUrl);
                try {
                    Intent refresh = new Intent(
                            com.Dramizo.Series.presentation.messages.MessagesFragment
                                    .ACTION_OFFICIAL_NEWS_UPDATED);
                    refresh.setPackage(getPackageName());
                    sendBroadcast(refresh);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static int notificationIdKey(@Nullable String id) {
        if (id != null && !id.isEmpty()) return id.hashCode();
        return ("rt:" + System.currentTimeMillis()).hashCode();
    }

    private void showCelebrationSystemNotification(
            @Nullable String title,
            @Nullable String body,
            @Nullable String avatarUrl,
            @Nullable String dedupe
    ) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String safeTitle = title != null && !title.isEmpty() ? title : "مبروك!";
            String safeBody = body != null ? body : "";
            AuraNotificationHelper.show(
                    this,
                    "auralive_default",
                    safeTitle,
                    safeBody,
                    "celebration",
                    intent,
                    notificationIdKey(dedupe != null ? dedupe : safeBody),
                    avatarUrl);
        } catch (Exception ignored) {
        }
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        try {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) return value.getAsString();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static long jsonLong(JsonObject object, String key, long fallback) {
        try {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isNumber()) return value.getAsLong();
                String s = value.getAsString();
                if (s != null && !s.isEmpty()) return Long.parseLong(s.trim());
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static double jsonDouble(JsonObject object, String key, double fallback) {
        try {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                double parsed = value.getAsDouble();
                if (Double.isFinite(parsed)) return parsed;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static double jsonDoubleAny(JsonObject object, double fallback, String... keys) {
        if (object == null || keys == null) return fallback;
        for (String key : keys) {
            double value = jsonDouble(object, key, Double.NaN);
            if (Double.isFinite(value) && value > 0d) return value;
        }
        for (String nestedKey : new String[] {"winner", "user", "sender", "reward", "result"}) {
            try {
                JsonElement nested = object.get(nestedKey);
                if (nested == null || !nested.isJsonObject()) continue;
                JsonObject nestedObject = nested.getAsJsonObject();
                for (String key : keys) {
                    double value = jsonDouble(nestedObject, key, Double.NaN);
                    if (Double.isFinite(value) && value > 0d) return value;
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /** Read the first non-empty value across current and legacy event payload keys. */
    private static String jsonStringAny(JsonObject object, String fallback, String... keys) {
        if (object == null || keys == null) return fallback;
        for (String key : keys) {
            String value = jsonString(object, key, null);
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        for (String nestedKey : new String[] {"winner", "user", "sender", "profile"}) {
            try {
                JsonElement nested = object.get(nestedKey);
                if (nested == null || !nested.isJsonObject()) continue;
                JsonObject nestedObject = nested.getAsJsonObject();
                for (String key : keys) {
                    String value = jsonString(nestedObject, key, null);
                    if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                        return value.trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /** Read the first positive numeric value across current and legacy event payload keys. */
    private static long jsonLongAny(JsonObject object, long fallback, String... keys) {
        if (object == null || keys == null) return fallback;
        for (String key : keys) {
            long value = jsonLong(object, key, 0L);
            if (value > 0L) return value;
        }
        for (String nestedKey : new String[] {"winner", "user", "sender", "reward", "result"}) {
            try {
                JsonElement nested = object.get(nestedKey);
                if (nested == null || !nested.isJsonObject()) continue;
                JsonObject nestedObject = nested.getAsJsonObject();
                for (String key : keys) {
                    long value = jsonLong(nestedObject, key, 0L);
                    if (value > 0L) return value;
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /**
     * Android 13+ blocks app notifications until runtime permission is granted.
     * Ask on the main screen and allow one retry after a real denial (when Android
     * reports that a rationale should be shown). If permission was denied permanently,
     * stop prompting on every launch; the user can enable it in system app settings.
     */
    private void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || activity == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                activity, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Context base = storageContext(this);
        if (base == null) return;
        android.content.SharedPreferences prefs =
                base.getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, MODE_PRIVATE);
        boolean prompted = prefs.getBoolean("prompted", false);
        boolean retryPrompted = prefs.getBoolean("retry_prompted", false);
        boolean shouldExplain = androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(
                        activity, android.Manifest.permission.POST_NOTIFICATIONS);
        if (prompted && (!shouldExplain || retryPrompted)) return;

        // Persist before opening the system dialog to avoid duplicate prompts if
        // MainActivity resumes while the permission request is still in progress.
        android.content.SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("prompted", true);
        if (prompted) editor.putBoolean("retry_prompted", true);
        editor.apply();

        activity.getWindow().getDecorView().post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()
                    && androidx.core.content.ContextCompat.checkSelfPermission(
                            activity, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "auralive_default",
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.channel_description));
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
                NotificationChannel messageChannel = new NotificationChannel(
                        com.Dramizo.Series.util.AuraNotificationHelper.CHANNEL_MESSAGES,
                        "رسائل JEHO",
                        NotificationManager.IMPORTANCE_HIGH);
                messageChannel.setDescription("إشعارات الرسائل الخاصة ومحادثاتك");
                messageChannel.setShowBadge(true);
                messageChannel.enableVibration(true);
                messageChannel.enableLights(true);
                nm.createNotificationChannel(messageChannel);
                NotificationChannel roomChannel = new NotificationChannel(
                        com.Dramizo.Series.service.VoiceRoomForegroundService.CHANNEL_ID,
                        getString(R.string.notif_room_channel),
                        NotificationManager.IMPORTANCE_DEFAULT);
                roomChannel.setDescription(getString(R.string.notif_room_channel_desc));
                roomChannel.setShowBadge(false);
                roomChannel.enableVibration(false);
                roomChannel.setSound(null, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    roomChannel.setAllowBubbles(true);
                }
                nm.createNotificationChannel(roomChannel);
                NotificationChannel bubbleChannel = new NotificationChannel(
                        com.Dramizo.Series.service.VoiceRoomForegroundService.BUBBLE_CHANNEL_ID,
                        getString(R.string.notif_room_bubble_channel),
                        NotificationManager.IMPORTANCE_DEFAULT);
                bubbleChannel.setDescription(getString(R.string.notif_room_bubble_channel_desc));
                bubbleChannel.setShowBadge(false);
                bubbleChannel.enableVibration(false);
                bubbleChannel.setSound(null, null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bubbleChannel.setAllowBubbles(true);
                }
                nm.createNotificationChannel(bubbleChannel);
                NotificationChannel engageChannel = new NotificationChannel(
                        com.Dramizo.Series.util.LocalEngagementScheduler.CHANNEL_ID,
                        getString(R.string.notif_engage_channel),
                        NotificationManager.IMPORTANCE_DEFAULT);
                engageChannel.setDescription(getString(R.string.notif_engage_channel_desc));
                engageChannel.setShowBadge(true);
                engageChannel.enableVibration(true);
                nm.createNotificationChannel(engageChannel);
            }
        }
    }
}
