package com.Dramizo.Series.presentation.main;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityMainBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.drama.DramaFragment;
import com.Dramizo.Series.presentation.games.GamesFragment;
import com.Dramizo.Series.presentation.home.HomeFragment;
import com.Dramizo.Series.presentation.messages.MessagesFragment;
import com.Dramizo.Series.presentation.profile.ProfileFragment;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.service.VoiceRoomForegroundService;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.zego.ZegoEngineManager;
import com.bumptech.glide.Glide;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.GenderVerificationGate;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends ThemedActivity {
    public static final String EXTRA_OPEN_CREATE_ROOM = "open_create_room";
    public static final String EXTRA_OPEN_HOME = "open_home";
    public static final String EXTRA_OPEN_MESSAGES = "open_messages";
    public static final String EXTRA_OPEN_DRAMA = "open_drama";
    private static final String FLOATING_PREFS = "home_floating_widgets";

    private ActivityMainBinding binding;
    private ViewPager2 mainPager;
    private int currentPage;
    private boolean dramaEnabled = true;
    private com.Dramizo.Series.data.remote.dto.MiscDtos.AgencyMineDto agencyMine;
    private boolean agencyStateLoaded;
    private boolean activeRoomDragConfigured;
    private RealtimeClient.RoomListener taskInviteListener;
    @Nullable private com.Dramizo.Series.util.PlayInAppUpdateHelper playInAppUpdateHelper;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> syncFcmToken());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.apply(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Must register after Activity is created; launcher is bound in the helper ctor.
        playInAppUpdateHelper = new com.Dramizo.Series.util.PlayInAppUpdateHelper(this);
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(this, "home");
        EdgeToEdgeHelper.padBottom(binding.customBottomBar);

        AppContainer container = ContainerProvider.from(this);
        // Never block first paint on feature flags — fetch off the main thread.
        container.getIoExecutor().execute(() -> AppFeatures.refresh(container));
        if (GenderVerificationGate.needsVerification(
                container.getSessionManager().getUser(), container.getSessionManager())) {
            startActivity(GenderVerificationGate.blockingIntent(this));
            finish();
            return;
        }
        applyNavIcons();
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(this, "home");
        binding.labelCreateRoom.setText(R.string.my_room);

        String token = container.getSessionManager().getAccessToken();
        if (token != null) RealtimeClient.getInstance().connect(token);
        attachTaskInviteListener(container);

        requestNotificationsPermission();
        syncFcmToken();

        setupMainPager();

        binding.tabPartyWrap.setOnClickListener(v -> go(R.id.nav_home));
        binding.tabDramaWrap.setOnClickListener(v -> go(R.id.nav_drama));
        binding.tabGamesWrap.setOnClickListener(v -> go(R.id.nav_games));
        binding.tabChatWrap.setOnClickListener(v -> go(R.id.nav_messages));
        binding.tabMeWrap.setOnClickListener(v -> go(R.id.nav_profile));
        binding.tabParty.setOnClickListener(v -> go(R.id.nav_home));
        binding.tabDrama.setOnClickListener(v -> go(R.id.nav_drama));
        binding.tabGames.setOnClickListener(v -> go(R.id.nav_games));
        binding.tabChat.setOnClickListener(v -> go(R.id.nav_messages));
        binding.tabMe.setOnClickListener(v -> go(R.id.nav_profile));
        binding.tabCreateRoomWrap.setOnClickListener(v -> handleAgencyAction());
        binding.tabCreateRoom.setOnClickListener(v -> binding.tabCreateRoomWrap.performClick());

        loadAgencyAction(container);

        highlightPage(0);
        loadDramaConfig(container);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_CREATE_ROOM, false)) {
            getIntent().removeExtra(EXTRA_OPEN_CREATE_ROOM);
            com.Dramizo.Series.util.MyRoomLauncher.open(this);
        }
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            getIntent().removeExtra(EXTRA_OPEN_HOME);
            go(R.id.nav_home);
        }
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            getIntent().removeExtra(EXTRA_OPEN_MESSAGES);
            go(R.id.nav_messages);
        }
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_DRAMA, false)) {
            getIntent().removeExtra(EXTRA_OPEN_DRAMA);
            go(R.id.nav_drama);
        }

        // Bottom tabs are siblings — back must not replay tab history.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mainPager != null && mainPager.getCurrentItem() != 0) {
                    go(R.id.nav_home);
                    return;
                }
                finish();
            }
        });

        // Google Play In-App Updates — official store prompt when a newer build is live.
        if (binding != null && playInAppUpdateHelper != null) {
            binding.getRoot().postDelayed(() -> {
                if (isFinishing() || playInAppUpdateHelper == null) return;
                playInAppUpdateHelper.checkForUpdate();
            }, 1500L);
        }

        maybeOpenPendingInvite(getIntent());
        maybeOpenPendingRoom(getIntent());
        // Install Referrer is async — retry once so post-Play installs still open رمز دعوتي.
        if (binding != null) {
            binding.getRoot().postDelayed(() -> {
                if (isFinishing()) return;
                if (com.Dramizo.Series.util.InviteReferralHelper.shouldAutoOpenInvite(this)) {
                    android.content.Intent synthetic = new android.content.Intent();
                    synthetic.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
                    maybeOpenPendingInvite(synthetic);
                }
                maybeOpenPendingRoom(null);
            }, 2200L);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_CREATE_ROOM, false)) {
            intent.removeExtra(EXTRA_OPEN_CREATE_ROOM);
            com.Dramizo.Series.util.MyRoomLauncher.open(this);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            intent.removeExtra(EXTRA_OPEN_HOME);
            go(R.id.nav_home);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            intent.removeExtra(EXTRA_OPEN_MESSAGES);
            go(R.id.nav_messages);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_DRAMA, false)) {
            intent.removeExtra(EXTRA_OPEN_DRAMA);
            go(R.id.nav_drama);
        }
        maybeOpenPendingInvite(intent);
        maybeOpenPendingRoom(intent);
    }

    private void maybeOpenPendingRoom(@Nullable android.content.Intent intent) {
        String roomId = intent != null ? intent.getStringExtra("pending_room_id") : null;
        if (roomId == null || roomId.isEmpty()) {
            roomId = com.Dramizo.Series.util.InviteReferralHelper.takePendingRoom(this);
        } else if (intent != null) {
            intent.removeExtra("pending_room_id");
            com.Dramizo.Series.util.InviteReferralHelper.takePendingRoom(this);
        }
        if (roomId == null || roomId.isEmpty()) return;
        Intent i = new Intent(this, VoiceRoomActivity.class);
        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
        i.putExtra(VoiceRoomActivity.EXTRA_IS_HOST, false);
        startActivity(i);
    }

    private void maybeOpenPendingInvite(@Nullable android.content.Intent intent) {
        // Explicit deep-link / login flag, or one-shot after Play Install Referrer.
        boolean openFlag = intent != null
                && intent.getBooleanExtra(
                        com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, false);
        boolean auto = com.Dramizo.Series.util.InviteReferralHelper.shouldAutoOpenInvite(this);
        if (!openFlag && !auto) return;

        String code = intent != null
                ? intent.getStringExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE)
                : null;
        if (code == null || code.isEmpty()) {
            code = com.Dramizo.Series.util.InviteReferralHelper.peekPendingCode(this);
        }
        if (intent != null) {
            intent.removeExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE);
            intent.removeExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE);
        }
        com.Dramizo.Series.util.InviteReferralHelper.markInvitePrompted(this);
        final String bindCode = code;
        if (binding == null) return;
        binding.getRoot().postDelayed(() -> {
            if (isFinishing()) return;
            android.content.Intent i = new android.content.Intent(
                    this, com.Dramizo.Series.presentation.invite.InvitationActivity.class);
            if (bindCode != null && !bindCode.isEmpty()) {
                i.putExtra(com.Dramizo.Series.presentation.invite.InvitationActivity.EXTRA_CODE, bindCode);
            }
            i.putExtra(com.Dramizo.Series.presentation.invite.InvitationActivity.EXTRA_AUTO_BIND, true);
            startActivity(i);
        }, 700L);
    }

    private void requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void syncFcmToken() {
        AppContainer c = ContainerProvider.from(this);
        if (c.getSessionManager().getAccessToken() == null) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(fcm ->
                    c.getIoExecutor().execute(() ->
                            c.getNotificationRepository().registerDevice(fcm, "android")));
        } catch (Throwable ignored) {
        }
    }

    public void go(int destId) {
        int page = destToPage(destId);
        if (page < 0 || mainPager == null) return;
        if (page == 1 && !dramaEnabled) return;
        if (mainPager.getCurrentItem() == page) return;
        if (destId != R.id.nav_create_room) {
            com.Dramizo.Series.util.AppLoadingOverlay.showUntilReady(this);
        } else {
            com.Dramizo.Series.util.AppLoadingOverlay.hide(this);
        }
        mainPager.setCurrentItem(page, true);
    }

    private void setupMainPager() {
        mainPager = binding.mainPager;
        // Bottom tabs only — disable edge swipe so vertical lists never change the main page.
        mainPager.setUserInputEnabled(false);
        // Keep only neighbors warm — offscreen=4 forced drama/games/chat/profile to all
        // fetch on first install and made the whole app feel stuck.
        mainPager.setOffscreenPageLimit(1);
        mainPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1: return new DramaFragment();
                    case 2: return new GamesFragment();
                    case 3: return new MessagesFragment();
                    case 4: return new ProfileFragment();
                    case 0:
                    default: return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 5;
            }
        });
        mainPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                highlightPage(position);
                com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(
                        MainActivity.this, pageScreenKey(position));
                com.Dramizo.Series.util.AppLoadingOverlay.hide(MainActivity.this);
            }
        });
    }

    private static int destToPage(int destId) {
        if (destId == R.id.nav_home) return 0;
        if (destId == R.id.nav_drama) return 1;
        if (destId == R.id.nav_games) return 2;
        if (destId == R.id.nav_messages) return 3;
        if (destId == R.id.nav_profile) return 4;
        return -1;
    }

    private void highlightPage(int page) {
        highlight(page == 0, binding.tabParty, binding.labelParty);
        highlight(page == 1, binding.tabDrama, binding.labelDrama);
        highlight(page == 2, binding.tabGames, binding.labelGames);
        highlight(false, binding.tabCreateRoom, binding.labelCreateRoom);
        highlight(page == 3, binding.tabChat, binding.labelChat);
        highlight(page == 4, binding.tabMe, binding.labelMe);
    }

    private static String pageScreenKey(int page) {
        switch (page) {
            case 1: return "drama";
            case 2: return "games";
            case 3: return "chat";
            case 4: return "profile";
            default: return "home";
        }
    }

    private void handleAgencyAction() {
        // One-tap personal/agency room — no create form.
        com.Dramizo.Series.util.MyRoomLauncher.open(this);
    }

    private void loadAgencyAction(AppContainer container) {
        if (container == null) return;
        container.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<
                    com.Dramizo.Series.data.remote.dto.MiscDtos.AgencyMineDto> result =
                    container.getAgencyRepository().mine();
            runOnUiThread(() -> {
                if (binding == null) return;
                agencyStateLoaded = true;
                agencyMine = result.success ? result.data : null;
                bindAgencyAction();
            });
        });
    }

    private void bindAgencyAction() {
        // Live tab is always available — personal rooms need no agency.
        int label = R.string.my_room;
        if (agencyMine != null && agencyMine.isEligibleHost()) {
            label = R.string.my_room;
        } else if (agencyMine != null && agencyMine.application != null) {
            String status = agencyMine.application.status;
            if (status == null || "pending".equalsIgnoreCase(status)) {
                label = R.string.agency_under_review;
            }
        }
        // My Room lives on profile (Mikoo) — keep bottom-tab create slot hidden.
        binding.tabCreateRoomWrap.setVisibility(View.GONE);
        binding.labelCreateRoom.setText(label);
        binding.tabCreateRoom.setContentDescription(getString(label));
    }

    private void applyNavIcons() {
        // Tab icons come from activity_main.xml android:src — only clear tints here.
        clearNavIconTint(binding.tabParty);
        clearNavIconTint(binding.tabDrama);
        clearNavIconTint(binding.tabGames);
        clearNavIconTint(binding.tabCreateRoom);
        clearNavIconTint(binding.tabChat);
        clearNavIconTint(binding.tabMe);
    }

    private static void clearNavIconTint(ImageView icon) {
        if (icon == null) return;
        icon.clearColorFilter();
        icon.setImageTintList(null);
    }

    private void loadDramaConfig(AppContainer container) {
        container.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<com.Dramizo.Series.data.remote.dto.DramaDtos.DramaConfigDto> r =
                    com.Dramizo.Series.util.ApiCall.execute(container.getDramaApi().config());
            runOnUiThread(() -> {
                if (binding == null) return;
                boolean show = r.success && r.data != null && r.data.enabled;
                dramaEnabled = show;
                binding.tabDramaWrap.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
                if (!show && mainPager != null && mainPager.getCurrentItem() == 1) {
                    mainPager.setCurrentItem(0, false);
                }
            });
        });
    }

    private String currentScreenKey() {
        return pageScreenKey(mainPager != null ? mainPager.getCurrentItem() : currentPage);
    }

    @Override
    protected String inferScreenKey() {
        return currentScreenKey();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppContainer container = ContainerProvider.from(this);
        loadAgencyAction(container);
        AppFeatures.refresh(container);
        bindActiveRoomMini();
        if (playInAppUpdateHelper != null) {
            playInAppUpdateHelper.onResume();
        }
    }

    private void bindActiveRoomMini() {
        if (binding == null || binding.activeRoomMini == null) return;
        String activeRoomId = VoiceRoomForegroundService.activeRoomId(this);
        boolean visible = activeRoomId != null && !activeRoomId.isEmpty();
        View miniRoot = binding.activeRoomMini.getRoot();
        miniRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            View cover = binding.activeRoomMini.imgMiniRoomCover;
            if (cover != null) cover.clearAnimation();
            return;
        }
        if (!activeRoomDragConfigured) {
            activeRoomDragConfigured = true;
            enableFloatingDrag(miniRoot, "active_room");
        } else {
            // ConstraintLayout can reset placement after rebind — restore saved spot.
            SharedPreferences prefs = getSharedPreferences(FLOATING_PREFS, MODE_PRIVATE);
            miniRoot.post(() -> restoreFloatingPosition(miniRoot, "active_room", prefs));
        }
        miniRoot.bringToFront();
        String title = VoiceRoomForegroundService.activeRoomTitle(this);
        binding.activeRoomMini.tvMiniRoomTitle.setText(
                title != null && !title.isEmpty() ? title : getString(R.string.voice_room));
        Glide.with(this)
                .load(AssetCatalog.absoluteUrl(
                        VoiceRoomForegroundService.activeRoomCover(this)))
                .placeholder(R.drawable.placeholder_cover)
                .error(R.drawable.placeholder_cover)
                .circleCrop()
                .into(binding.activeRoomMini.imgMiniRoomCover);
        binding.activeRoomMini.imgMiniRoomCover.setClipToOutline(true);
        binding.activeRoomMini.imgMiniRoomCover.setOutlineProvider(
                new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
                    }
                });
        View ring = miniRoot.findViewById(R.id.miniRoomRing);
        if (ring != null) {
            ring.setClipToOutline(true);
            ring.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
                }
            });
            ring.setClickable(false);
            ring.setFocusable(false);
            ring.setOnClickListener(null);
        }
        // Tap opens room (drag helper calls performClick when not moved).
        miniRoot.setOnClickListener(v -> {
            Intent room = new Intent(this, VoiceRoomActivity.class);
            room.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, activeRoomId);
            startActivity(room);
        });
        // Spin only the cover — translation drag stays stable on the root.
        android.view.animation.RotateAnimation spin = new android.view.animation.RotateAnimation(
                0f, 360f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        spin.setDuration(4200);
        spin.setRepeatCount(android.view.animation.Animation.INFINITE);
        spin.setInterpolator(new android.view.animation.LinearInterpolator());
        binding.activeRoomMini.imgMiniRoomCover.clearAnimation();
        binding.activeRoomMini.imgMiniRoomCover.startAnimation(spin);

        binding.activeRoomMini.btnMiniRoomClose.bringToFront();
        binding.activeRoomMini.btnMiniRoomClose.setOnClickListener(v -> {
            VoiceRoomForegroundService.leaveActiveRoom(this);
            binding.activeRoomMini.imgMiniRoomCover.clearAnimation();
            miniRoot.setVisibility(View.GONE);
            Toast.makeText(this, R.string.left_room, Toast.LENGTH_SHORT).show();
        });

        // Center mic — like Lucky float: tap toggles mute without opening the room.
        ImageView micBtn = binding.activeRoomMini.btnMiniRoomMic;
        if (micBtn != null) {
            micBtn.bringToFront();
            syncMiniRoomMicUi(micBtn);
            micBtn.setOnClickListener(v -> toggleMiniRoomMic(activeRoomId, micBtn));
        }
    }

    private void syncMiniRoomMicUi(ImageView micBtn) {
        if (micBtn == null) return;
        boolean micOn = ZegoEngineManager.getInstance().isMicEnabled();
        micBtn.setImageResource(
                micOn ? R.drawable.ic_asset_mic_open : R.drawable.ic_asset_mic_close);
        micBtn.setBackgroundResource(
                micOn ? R.drawable.bg_mini_room_mic : R.drawable.bg_mini_room_mic_off);
        micBtn.setContentDescription(micOn
                ? getString(R.string.mini_mic_mute_desc)
                : getString(R.string.mini_mic_unmute_desc));
    }

    private void toggleMiniRoomMic(String activeRoomId, ImageView micBtn) {
        boolean nextOn = !ZegoEngineManager.getInstance().isMicEnabled();
        ZegoEngineManager.getInstance().setMicEnabled(nextOn);
        syncMiniRoomMicUi(micBtn);
        Toast.makeText(this,
                nextOn ? R.string.mini_mic_enabled_toast : R.string.mini_mic_muted_toast,
                Toast.LENGTH_SHORT).show();
        if (activeRoomId == null || activeRoomId.isEmpty()) return;
        // Keep server seat mute in sync while minimized.
        AppContainer c = ContainerProvider.from(this);
        final boolean muted = !nextOn;
        c.getIoExecutor().execute(() -> c.getRoomRepository().setMic(activeRoomId, muted));
    }

    private void enableFloatingDrag(View floatingView, String preferenceKey) {
        SharedPreferences prefs = getSharedPreferences(FLOATING_PREFS, MODE_PRIVATE);
        floatingView.setClickable(true);
        floatingView.setFocusable(true);
        floatingView.post(() -> restoreFloatingPosition(floatingView, preferenceKey, prefs));
        final float[] downRaw = new float[2];
        final float[] startTrans = new float[2];
        final boolean[] moved = {false};
        final float touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        View.OnTouchListener dragListener = (view, event) -> {
            // Always drag the floating root, even if the event started on a child.
            View target = floatingView;
            View parentView = target.getParent() instanceof View ? (View) target.getParent() : null;
            if (parentView == null) return false;
            View closeBtn = target.findViewById(R.id.btnMiniRoomClose);
            View micBtn = target.findViewById(R.id.btnMiniRoomMic);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (closeBtn != null && closeBtn.getVisibility() == View.VISIBLE
                            && touchInsideChild(target, closeBtn, event)) {
                        return false;
                    }
                    if (micBtn != null && micBtn.getVisibility() == View.VISIBLE
                            && touchInsideChild(target, micBtn, event)) {
                        return false;
                    }
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    startTrans[0] = target.getTranslationX();
                    startTrans[1] = target.getTranslationY();
                    moved[0] = false;
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downRaw[0];
                    float dy = event.getRawY() - downRaw[1];
                    if (Math.hypot(dx, dy) > touchSlop) moved[0] = true;
                    float bottomLimit = binding != null && binding.customBottomBar.getTop() > 0
                            ? binding.customBottomBar.getTop() : parentView.getHeight();
                    float minTx = -target.getLeft();
                    float maxTx = parentView.getWidth() - target.getLeft() - target.getWidth();
                    float minTy = -target.getTop();
                    float maxTy = bottomLimit - target.getTop() - target.getHeight();
                    if (maxTx < minTx) maxTx = minTx;
                    if (maxTy < minTy) maxTy = minTy;
                    float tx = Math.max(minTx, Math.min(maxTx, startTrans[0] + dx));
                    float ty = Math.max(minTy, Math.min(maxTy, startTrans[1] + dy));
                    target.setTranslationX(tx);
                    target.setTranslationY(ty);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    saveFloatingPosition(target, preferenceKey, prefs, parentView);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved[0]) {
                        target.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        };
        floatingView.setOnTouchListener(dragListener);
        // Children must not steal the gesture — attach the same listener.
        View ring = floatingView.findViewById(R.id.miniRoomRing);
        View cover = floatingView.findViewById(R.id.imgMiniRoomCover);
        if (ring != null) {
            ring.setClickable(false);
            ring.setFocusable(false);
            ring.setOnTouchListener(dragListener);
        }
        if (cover != null) {
            cover.setClickable(false);
            cover.setFocusable(false);
            cover.setOnTouchListener(dragListener);
        }
    }

    private static boolean touchInsideChild(View parent, View child, MotionEvent event) {
        if (parent == null || child == null) return false;
        int[] parentLoc = new int[2];
        int[] childLoc = new int[2];
        parent.getLocationOnScreen(parentLoc);
        child.getLocationOnScreen(childLoc);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= childLoc[0] && x <= childLoc[0] + child.getWidth()
                && y >= childLoc[1] && y <= childLoc[1] + child.getHeight();
    }

    private void restoreFloatingPosition(View view, String key, SharedPreferences prefs) {
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        if (parent == null || !prefs.contains(key + "_tx")) {
            // Migrate old absolute X/Y prefs once.
            if (parent != null && prefs.contains(key + "_x")) {
                float maxX = Math.max(1f, parent.getWidth() - view.getWidth());
                float bottom = binding != null && binding.customBottomBar.getTop() > 0
                        ? binding.customBottomBar.getTop() : parent.getHeight();
                float maxY = Math.max(1f, bottom - view.getHeight());
                float absX = maxX * prefs.getFloat(key + "_x", 0f);
                float absY = maxY * prefs.getFloat(key + "_y", 1f);
                view.setTranslationX(absX - view.getLeft());
                view.setTranslationY(absY - view.getTop());
            }
            return;
        }
        float bottom = binding != null && binding.customBottomBar.getTop() > 0
                ? binding.customBottomBar.getTop() : parent.getHeight();
        float minTx = -view.getLeft();
        float maxTx = parent.getWidth() - view.getLeft() - view.getWidth();
        float minTy = -view.getTop();
        float maxTy = bottom - view.getTop() - view.getHeight();
        float tx = minTx + (maxTx - minTx) * prefs.getFloat(key + "_tx", 0f);
        float ty = minTy + (maxTy - minTy) * prefs.getFloat(key + "_ty", 0f);
        view.setTranslationX(tx);
        view.setTranslationY(ty);
    }

    private void saveFloatingPosition(
            View view, String key, SharedPreferences prefs, View parent) {
        float bottom = binding != null && binding.customBottomBar.getTop() > 0
                ? binding.customBottomBar.getTop() : parent.getHeight();
        float minTx = -view.getLeft();
        float maxTx = Math.max(minTx + 1f, parent.getWidth() - view.getLeft() - view.getWidth());
        float minTy = -view.getTop();
        float maxTy = Math.max(minTy + 1f, bottom - view.getTop() - view.getHeight());
        float nx = (view.getTranslationX() - minTx) / (maxTx - minTx);
        float ny = (view.getTranslationY() - minTy) / (maxTy - minTy);
        prefs.edit()
                .putFloat(key + "_tx", Math.max(0f, Math.min(1f, nx)))
                .putFloat(key + "_ty", Math.max(0f, Math.min(1f, ny)))
                .apply();
    }

    private void highlight(boolean on, ImageView icon, TextView label) {
        icon.setAlpha(on ? 1f : 0.55f);
        label.setAlpha(1f);
        label.setTextColor(getColor(on
                ? R.color.bottom_chrome_label
                : R.color.bottom_chrome_label_muted));
        label.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        // Keep JEHO tab icons full-color (no monochrome tint).
        clearNavIconTint(icon);
        icon.setScaleX(on ? 1.05f : 1f);
        icon.setScaleY(on ? 1.05f : 1f);
        icon.setBackground(null);
    }

    private void attachTaskInviteListener(AppContainer container) {
        if (taskInviteListener != null) return;
        taskInviteListener = new RealtimeClient.RoomListener() {
            @Override
            public void onRoomEvent(String roomId, String event,
                                    com.google.gson.JsonObject payload,
                                    String fromUserId, String fromUsername) {
                if (!"room:task_invited".equals(event) || payload == null) return;
                // VoiceRoomActivity handles the prompt when its UI is visible.
                if (VoiceRoomActivity.isRoomUiVisible()) return;
                String guestId = payload.has("guestId") && !payload.get("guestId").isJsonNull()
                        ? payload.get("guestId").getAsString() : null;
                String myId = container.getSessionManager().getUserId();
                if (myId == null || guestId == null || !myId.equals(guestId)) return;
                String inviteRoomId = payload.has("roomId") && !payload.get("roomId").isJsonNull()
                        ? payload.get("roomId").getAsString() : roomId;
                if (inviteRoomId == null || inviteRoomId.isEmpty()) return;
                runOnUiThread(() -> promptTaskRoomInvite(inviteRoomId));
            }

            @Override public void onUserJoined(String roomId, String userId, String username) {}
            @Override public void onUserLeft(String roomId, String userId) {}
            @Override public void onConnected() {}
            @Override public void onDisconnected() {}
        };
        RealtimeClient.getInstance().addRoomListener(taskInviteListener);
    }

    private void promptTaskRoomInvite(String inviteRoomId) {
        if (isFinishing()) return;
        com.Dramizo.Series.util.AuraDialogHelper.confirm(this,
                getString(R.string.task_invite_title),
                getString(R.string.task_invite_message),
                getString(R.string.enter_room),
                () -> {
                    Intent room = new Intent(this, VoiceRoomActivity.class);
                    room.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, inviteRoomId);
                    startActivity(room);
                },
                getString(R.string.later),
                null);
    }

    @Override
    protected void onDestroy() {
        if (playInAppUpdateHelper != null) {
            playInAppUpdateHelper.onDestroy();
            playInAppUpdateHelper = null;
        }
        if (taskInviteListener != null) {
            RealtimeClient.getInstance().removeRoomListener(taskInviteListener);
            taskInviteListener = null;
        }
        super.onDestroy();
    }
}
