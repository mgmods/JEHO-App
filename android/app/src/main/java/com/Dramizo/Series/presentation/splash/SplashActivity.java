package com.Dramizo.Series.presentation.splash;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import androidx.appcompat.app.AppCompatDelegate;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.databinding.ActivitySplashBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.ProfileSetupActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.GenderVerificationGate;
import com.Dramizo.Series.util.InviteReferralHelper;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends ThemedActivity {
    private static final String TAG = "SplashActivity";
    /** Keep splash short — never wait on network gates. */
    private static final long SPLASH_MIN_MS = 350L;
    private static final long SPLASH_MAX_MS = 1800L;

    private boolean navigated;
    private String pendingRoomId;
    private String pendingInviteCode;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hardTimeout = this::forceLeaveSplash;

    @Override
    protected boolean wantsEdgeToEdgeWallpaper() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            super.onCreate(savedInstanceState);
            InviteReferralHelper.captureInstallReferrer(this);
            parseDeepLink(getIntent());
            ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            EdgeToEdgeHelper.apply(this);
            EdgeToEdgeHelper.padStatusOnly(binding.splashContent);
            EdgeToEdgeHelper.padBottom(binding.splashContent);
            if (getWindow() != null) {
                getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
                getWindow().setNavigationBarColor(
                        androidx.core.content.ContextCompat.getColor(this, R.color.bg_light));
            }

            android.view.animation.AnimationSet logoIn = brandEnter(0.86f, 1f, SPLASH_MIN_MS);
            binding.imgSplashLogo.startAnimation(logoIn);

            // Stagger brand text + tagline for a calmer premiere.
            if (binding.tvSplashBrand != null) {
                AlphaAnimation brandFade = new AlphaAnimation(0f, 1f);
                brandFade.setDuration(420L);
                brandFade.setStartOffset(120L);
                brandFade.setFillAfter(true);
                binding.tvSplashBrand.startAnimation(brandFade);
            }
            if (binding.tvSplashTagline != null) {
                AlphaAnimation tagFade = new AlphaAnimation(0f, 1f);
                tagFade.setDuration(480L);
                tagFade.setStartOffset(220L);
                tagFade.setFillAfter(true);
                binding.tvSplashTagline.startAnimation(tagFade);
            }

            mainHandler.postDelayed(this::prepareSession, SPLASH_MIN_MS);
            mainHandler.postDelayed(hardTimeout, SPLASH_MAX_MS);
        } catch (Throwable t) {
            Log.e(TAG, "Splash failed", t);
            if (!tryResumeLoggedInSession()) openLogin();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseDeepLink(intent);
    }

    private void parseDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        String scheme = data.getScheme() != null ? data.getScheme() : "";
        String host = data.getHost() != null ? data.getHost() : "";
        String path = data.getPath() != null ? data.getPath() : "";

        String invite = InviteReferralHelper.parseCodeFromUri(data);
        if (invite != null) {
            pendingInviteCode = invite;
            InviteReferralHelper.savePendingCode(this, invite);
        }

        if ("hamslive".equalsIgnoreCase(scheme) || "jehochat".equalsIgnoreCase(scheme)) {
            String id = data.getLastPathSegment();
            if (id == null || id.isEmpty()) id = data.getQueryParameter("id");
            if ("room".equalsIgnoreCase(host) && id != null && !id.isEmpty()
                    && !"invite".equalsIgnoreCase(id)) {
                pendingRoomId = id;
            }
            return;
        }
        if (path.startsWith("/open/room")) {
            String id = path.length() > "/open/room/".length()
                    ? path.substring("/open/room/".length()).split("[/?#]")[0]
                    : data.getQueryParameter("id");
            if (id != null && !id.isEmpty()) {
                pendingRoomId = id;
                InviteReferralHelper.savePendingRoom(this, id);
            }
        }
    }

    private void putPendingExtras(Intent i) {
        if (i == null) return;
        if (pendingRoomId == null || pendingRoomId.isEmpty()) {
            pendingRoomId = InviteReferralHelper.peekPendingRoom(this);
        }
        if (pendingRoomId != null && !pendingRoomId.isEmpty()) {
            i.putExtra("pending_room_id", pendingRoomId);
        }
        String invite = pendingInviteCode != null
                ? pendingInviteCode
                : InviteReferralHelper.peekPendingCode(this);
        if (invite != null && !invite.isEmpty()) {
            i.putExtra(InviteReferralHelper.EXTRA_PENDING_INVITE, invite);
            i.putExtra(InviteReferralHelper.EXTRA_OPEN_INVITE, true);
        }
    }

    private void prepareSession() {
        try {
            AppContainer c = ContainerProvider.from(this);
            // Features + catalogs on IO — never block splash/main paint.
            c.getIoExecutor().execute(() -> {
                try {
                    AppFeatures.refresh(c);
                } catch (Throwable ignored) {}
            });
            boolean loggedIn = c.getSessionManager().isLoggedIn();
            if (!loggedIn) {
                openLogin();
                return;
            }
            AuthDtos.UserDto cached = c.getSessionManager().getUser();
            routeLoggedIn(c, cached);
            refreshSessionInBackground(c);
        } catch (Throwable t) {
            Log.e(TAG, "Session prepare failed", t);
            if (!tryResumeLoggedInSession()) openLogin();
        }
    }

    /** Resume main flow when splash UI fails but tokens are still on disk. */
    private boolean tryResumeLoggedInSession() {
        try {
            AppContainer c = ContainerProvider.from(this);
            if (!c.getSessionManager().isLoggedIn()) return false;
            routeLoggedIn(c, c.getSessionManager().getUser());
            refreshSessionInBackground(c);
            return navigated;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void forceLeaveSplash() {
        if (navigated || isFinishing()) return;
        try {
            AppContainer c = ContainerProvider.from(this);
            if (c.getSessionManager().isLoggedIn()) {
                routeLoggedIn(c, c.getSessionManager().getUser());
            } else {
                openLogin();
            }
        } catch (Throwable t) {
            openLogin();
        }
    }

    private void routeLoggedIn(AppContainer c, AuthDtos.UserDto user) {
        if (navigated || isFinishing()) return;
        if (ProfileSetupActivity.isProfileComplete(user)) {
            if (GenderVerificationGate.needsVerification(user, c.getSessionManager())) {
                navigated = true;
                mainHandler.removeCallbacks(hardTimeout);
                startActivity(GenderVerificationGate.blockingIntent(this));
                finish();
                return;
            }
            openDestination();
        } else {
            openProfileSetup();
        }
    }

    /** Refresh tokens + profile without holding the splash. */
    private void refreshSessionInBackground(AppContainer c) {
        c.getIoExecutor().execute(() -> {
            try {
                Result<?> refreshed = c.refreshSessionUseCase.execute();
                if (refreshed != null && refreshed.success) {
                    Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
                    if (me != null && me.success && me.data != null) {
                        c.getSessionManager().updateCachedUser(me.data);
                    }
                }
                AppFeatures.refresh(c);
            } catch (Throwable t) {
                Log.w(TAG, "Background session refresh skipped", t);
            }
        });
    }

    private void openDestination() {
        if (navigated || isFinishing()) return;
        navigated = true;
        mainHandler.removeCallbacks(hardTimeout);
        // Resume voice room after process death / launcher reopen when still in a room.
        String activeRoom = null;
        try {
            activeRoom = com.Dramizo.Series.service.VoiceRoomForegroundService.activeRoomId(this);
        } catch (Exception ignored) {
        }
        if ((pendingRoomId == null || pendingRoomId.isEmpty())
                && activeRoom != null && !activeRoom.isEmpty()) {
            pendingRoomId = activeRoom;
        }
        if (pendingRoomId != null && !pendingRoomId.isEmpty()) {
            Intent i = new Intent(this, VoiceRoomActivity.class);
            i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, pendingRoomId);
            i.putExtra(VoiceRoomActivity.EXTRA_IS_HOST, false);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            putPendingExtras(i);
            startActivity(i);
        } else {
            Intent i = new Intent(this, MainActivity.class);
            putPendingExtras(i);
            startActivity(i);
        }
        finish();
    }

    private void openProfileSetup() {
        if (navigated || isFinishing()) return;
        navigated = true;
        mainHandler.removeCallbacks(hardTimeout);
        Intent i = new Intent(this, ProfileSetupActivity.class);
        putPendingExtras(i);
        startActivity(i);
        finish();
    }

    private void openLogin() {
        if (navigated || isFinishing()) return;
        navigated = true;
        mainHandler.removeCallbacks(hardTimeout);
        Intent i = new Intent(this, LoginActivity.class);
        putPendingExtras(i);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private static android.view.animation.AnimationSet brandEnter(float fromScale, float toScale, long ms) {
        ScaleAnimation scale = new ScaleAnimation(
                fromScale, toScale, fromScale, toScale,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(ms);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(ms);
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        set.addAnimation(scale);
        set.addAnimation(fade);
        set.setFillAfter(true);
        return set;
    }
}
