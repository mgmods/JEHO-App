package com.Dramizo.Series.presentation.splash;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ActivitySplashBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.ProfileSetupActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.RemoteTheme;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.Dramizo.Series.util.GenderVerificationGate;
import com.Dramizo.Series.util.InviteReferralHelper;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends ThemedActivity {
    private static final String TAG = "SplashActivity";
    /** Show the branded fallback briefly, then bypass a slow/unavailable ad request. */
    private static final long SPLASH_MIN_MS = 350L;
    private static final long SPLASH_MAX_MS = 4000L;

    private boolean navigated;
    private boolean splashDecisionResolved;
    private boolean splashAdShowing;
    private boolean sessionPrepared;
    private boolean sessionLoggedIn;
    private boolean targetResolving;
    private AppContainer pendingContainer;
    private AuthDtos.UserDto pendingUser;
    private MiscDtos.SplashItemDto displayedSplashItem;
    private String pendingSplashTarget;
    private ImageView splashAdImage;
    private View splashContentView;
    private View skipSplashAd;
    private String pendingRoomId;
    private String pendingInviteCode;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hardTimeout = this::forceLeaveSplash;
    private final Runnable targetResolveTimeout = () -> {
        if (!targetResolving || navigated || isFinishing()) return;
        targetResolving = false;
        pendingSplashTarget = null;
        continueStartupNavigation();
    };

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
            splashAdImage = binding.imgSplashAd;
            splashContentView = binding.splashContent;
            skipSplashAd = binding.btnSkipSplashAd;
            splashAdImage.setOnClickListener(v -> finishSplashAd(true));
            skipSplashAd.setOnClickListener(v -> finishSplashAd(false));
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

            startSplashAdLoad();
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
        if ("messages".equals(pendingSplashTarget)) {
            i.putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true);
        } else if ("drama".equals(pendingSplashTarget)) {
            i.putExtra(MainActivity.EXTRA_OPEN_DRAMA, true);
        } else if ("create_room".equals(pendingSplashTarget)) {
            i.putExtra(MainActivity.EXTRA_OPEN_CREATE_ROOM, true);
        } else if ("home".equals(pendingSplashTarget)) {
            i.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
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
            sessionPrepared = true;
            sessionLoggedIn = loggedIn;
            pendingContainer = c;
            pendingUser = loggedIn ? c.getSessionManager().getUser() : null;
            if (loggedIn) refreshSessionInBackground(c);
            if (splashDecisionResolved) continueStartupNavigation();
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
        if (navigated || isFinishing() || splashAdShowing) return;
        splashDecisionResolved = true;
        if (sessionPrepared) {
            continueStartupNavigation();
            return;
        }
        if (!tryResumeLoggedInSession()) openLogin();
    }

    private void continueStartupNavigation() {
        if (!splashDecisionResolved || !sessionPrepared || navigated || isFinishing()) return;
        if (pendingContainer == null) {
            if (!tryResumeLoggedInSession()) openLogin();
            return;
        }
        if (!sessionLoggedIn) {
            openLogin();
            return;
        }
        routeLoggedInNow(pendingContainer, pendingUser);
    }

    private void routeLoggedIn(AppContainer c, AuthDtos.UserDto user) {
        if (!splashDecisionResolved) {
            sessionPrepared = true;
            sessionLoggedIn = true;
            pendingContainer = c;
            pendingUser = user;
            return;
        }
        routeLoggedInNow(c, user);
    }

    private void routeLoggedInNow(AppContainer c, AuthDtos.UserDto user) {
        if (navigated || isFinishing()) return;
        if (ProfileSetupActivity.isProfileComplete(user)) {
            if (GenderVerificationGate.needsVerification(user, c.getSessionManager())) {
                navigated = true;
                mainHandler.removeCallbacks(hardTimeout);
                startActivity(GenderVerificationGate.blockingIntent(this));
                finish();
                return;
            }
            if (isIdSplashTarget(pendingSplashTarget)) {
                openIdSplashTarget(c, pendingSplashTarget);
            } else {
                openDestination();
            }
        } else {
            openProfileSetup();
        }
    }

    private void startSplashAdLoad() {
        try {
            AppContainer c = ContainerProvider.from(this);
            c.getIoExecutor().execute(() -> {
                try {
                    // The public theme endpoint is refreshed before choosing the next ad.
                    // RemoteTheme keeps its last good cache if the request fails.
                    RemoteTheme.refreshFromApi(getApplicationContext(), c.getConfigApi());
                    MiscDtos.SplashItemDto item = RemoteTheme.nextSplash(getApplicationContext());
                    runOnUiThread(() -> {
                        if (isFinishing() || splashDecisionResolved) return;
                        if (item == null || item.url == null || item.url.trim().isEmpty()) {
                            finishSplashAd(false);
                            return;
                        }
                        try {
                            Glide.with(SplashActivity.this)
                                    .load(item.url)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .skipMemoryCache(true)
                                    .into(new CustomTarget<Drawable>() {
                                        @Override
                                        public void onResourceReady(
                                                Drawable resource,
                                                Transition<? super Drawable> transition) {
                                            if (isFinishing() || splashDecisionResolved) return;
                                            splashAdImage.setImageDrawable(resource);
                                            showSplashAd(item);
                                        }

                                        @Override
                                        public void onLoadCleared(Drawable placeholder) {
                                        }

                                        @Override
                                        public void onLoadFailed(Drawable errorDrawable) {
                                            finishSplashAd(false);
                                        }
                                    });
                        } catch (Throwable imageError) {
                            Log.w(TAG, "Splash ad image could not be started", imageError);
                            finishSplashAd(false);
                        }
                    });
                } catch (Throwable error) {
                    Log.w(TAG, "Splash ad config unavailable; continuing startup", error);
                    runOnUiThread(() -> finishSplashAd(false));
                }
            });
        } catch (Throwable error) {
            Log.w(TAG, "Splash ad setup unavailable; continuing startup", error);
            finishSplashAd(false);
        }
    }

    private void showSplashAd(MiscDtos.SplashItemDto item) {
        if (splashDecisionResolved || isFinishing()) return;
        displayedSplashItem = item;
        splashAdShowing = true;
        mainHandler.removeCallbacks(hardTimeout);
        splashContentVisibilityGone();
        splashAdImage.setVisibility(View.VISIBLE);
        skipSplashAd.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> finishSplashAd(false), RemoteTheme.splashDurationMs(item));
    }

    private void splashContentVisibilityGone() {
        if (splashContentView != null) splashContentView.setVisibility(View.GONE);
    }

    private void finishSplashAd(boolean clicked) {
        if (splashDecisionResolved || isFinishing()) return;
        if (clicked && displayedSplashItem != null) {
            String target = displayedSplashItem.target;
            if (target == null || target.trim().isEmpty()) target = displayedSplashItem.targetUrl;
            pendingSplashTarget = target != null ? target.trim() : null;
        } else {
            pendingSplashTarget = null;
        }
        splashDecisionResolved = true;
        splashAdShowing = false;
        mainHandler.removeCallbacks(hardTimeout);
        if (splashAdImage != null) splashAdImage.setVisibility(View.GONE);
        if (skipSplashAd != null) skipSplashAd.setVisibility(View.GONE);
        continueStartupNavigation();
    }

    private void finishSplashAd() {
        finishSplashAd(false);
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

    private boolean isIdSplashTarget(String target) {
        return target != null && target.matches("(?i)^(room|agency|user):.+$");
    }

    /** Resolve public IDs with existing APIs. Invalid IDs fall back to home. */
    private void openIdSplashTarget(AppContainer container, String target) {
        if (targetResolving || navigated || container == null) return;
        String[] parts = target.split(":", 2);
        if (parts.length != 2 || parts[1].trim().isEmpty()) {
            pendingSplashTarget = null;
            continueStartupNavigation();
            return;
        }
        final String kind = parts[0].trim().toLowerCase(java.util.Locale.ROOT);
        final String publicId = parts[1].trim();
        targetResolving = true;
        mainHandler.removeCallbacks(hardTimeout);
        mainHandler.postDelayed(targetResolveTimeout, 8000L);
        container.getIoExecutor().execute(() -> {
            Intent destination = null;
            try {
                if ("room".equals(kind)) {
                    Result<MiscDtos.ListResult<RoomDtos.RoomDto>> result = ApiCall.execute(container.getRoomApi().list(1, publicId));
                    if (result != null && result.success && result.data != null && result.data.items != null) {
                        for (RoomDtos.RoomDto room : result.data.items) {
                            if (room != null && room.id != null && (publicId.equals(room.publicId) || publicId.equals(room.id))) {
                                destination = new Intent(this, MainActivity.class).putExtra("pending_room_id", room.id);
                                break;
                            }
                        }
                    }
                } else if ("user".equals(kind)) {
                    Result<MiscDtos.ListResult<AuthDtos.UserDto>> result = ApiCall.execute(container.getUserApi().search(publicId, 1));
                    if (result != null && result.success && result.data != null && result.data.items != null) {
                        for (AuthDtos.UserDto user : result.data.items) {
                            if (user != null && user.id != null && (publicId.equals(user.publicId) || publicId.equals(user.id))) {
                                destination = new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN_PROFILE_USER_ID, user.id);
                                break;
                            }
                        }
                    }
                } else if ("agency".equals(kind)) {
                    Result<MiscDtos.AgencyDto> result = ApiCall.execute(container.getAgencyApi().get(publicId));
                    if (result != null && result.success && result.data != null && result.data.id != null) {
                        destination = new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN_AGENCY_ID, publicId);
                    }
                }
            } catch (Throwable error) {
                Log.w(TAG, "Splash ID target could not be resolved; falling back to home", error);
            }
            final Intent resolved = destination;
            runOnUiThread(() -> {
                if (!targetResolving || navigated || isFinishing()) return;
                targetResolving = false;
                mainHandler.removeCallbacks(targetResolveTimeout);
                if (resolved == null) {
                    pendingSplashTarget = null;
                    continueStartupNavigation();
                    return;
                }
                navigated = true;
                mainHandler.removeCallbacks(hardTimeout);
                startActivity(resolved);
                finish();
            });
        });
    }

    private void openDestination() {
        if (navigated || isFinishing()) return;
        navigated = true;
        mainHandler.removeCallbacks(hardTimeout);
        mainHandler.removeCallbacks(targetResolveTimeout);
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
        if (pendingSplashTarget != null && pendingSplashTarget.matches("(?i)^https?://.+")) {
            try {
                Intent external = new Intent(Intent.ACTION_VIEW, Uri.parse(pendingSplashTarget));
                startActivity(external);
            } catch (Throwable error) {
                Log.w(TAG, "Could not open splash ad destination", error);
            }
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
