package com.Dramizo.Series.presentation.voiceroom;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.annotation.OptIn;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.data.remote.dto.GameDtos;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.databinding.ActivityVoiceRoomBinding;
import com.Dramizo.Series.databinding.DialogRoomGamesBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.RoomEffectQueue;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.games.GameAdsHelper;
import com.Dramizo.Series.presentation.games.GamePlayActivity;
import com.Dramizo.Series.presentation.gifts.GiftBottomSheet;
import com.Dramizo.Series.util.CoinRainAnimator;
import com.Dramizo.Series.util.GiftFlyAnimator;
import com.Dramizo.Series.presentation.gifts.GiftRecipientSource;
import com.Dramizo.Series.presentation.gifts.GiftRecipient;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.service.VoiceRoomForegroundService;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.ActiveRoomSession;
import com.Dramizo.Series.util.RoomJoinPrefetch;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.BalanceRedirect;
import com.Dramizo.Series.util.GiftAudioFx;
import com.Dramizo.Series.util.GlobalCelebrationToast;
import com.Dramizo.Series.util.HostSignalView;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.RoomCardAnimator;
import com.Dramizo.Series.util.RoomChatMemory;
import com.Dramizo.Series.util.GameProbeLog;
import com.Dramizo.Series.util.GameUrls;
import com.Dramizo.Series.util.MikooGameBridge;
import com.Dramizo.Series.util.MikooGamesCatalog;
import com.Dramizo.Series.util.MediaAssetSync;
import com.Dramizo.Series.util.MikooHashBridge;
import com.Dramizo.Series.util.DeviceMusicScanner;
import com.Dramizo.Series.util.PermissionHelper;
import com.bumptech.glide.Glide;
import com.Dramizo.Series.data.remote.dto.SlotGameDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.data.remote.api.SlotGameApi;

import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.nio.charset.StandardCharsets;
import com.Dramizo.Series.util.RewardBurstOverlay;
import com.Dramizo.Series.util.RoomKenarHelper;
import com.Dramizo.Series.util.RoomSoundFx;
import com.Dramizo.Series.util.RoomUiHelper;
import com.Dramizo.Series.util.RoomVisualEffects;
import com.Dramizo.Series.util.SeatReactionEmojis;
import com.Dramizo.Series.util.VipStyle;
import com.Dramizo.Series.util.YoutubeAudioResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import com.Dramizo.Series.rtc.LiveKitEngineManager;
import com.Dramizo.Series.rtc.RoomRtcEngine;
import com.Dramizo.Series.zego.ZegoEngineManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OptIn(markerClass = UnstableApi.class)
public class VoiceRoomActivity extends ThemedActivity implements GiftRecipientSource {
    public static final String EXTRA_ROOM_ID = "room_id";
    /** True while any VoiceRoomActivity is started (for global invite routing). */
    private static volatile boolean sRoomUiVisible;
    /** Alive room activity (may be paused/minimized) — for world celebration delivery. */
    private static volatile java.lang.ref.WeakReference<VoiceRoomActivity> sAliveRoom =
            new java.lang.ref.WeakReference<>(null);
    public static final String EXTRA_IS_HOST = "is_host";
    public static final String EXTRA_PASSWORD = "room_password";
    public static final String EXTRA_PENDING_SEAT_INVITE = "pending_seat_invite";
    private boolean pendingSeatInviteDialog;

    private ActivityVoiceRoomBinding binding;
    private VoiceRoomViewModel viewModel;
    private SeatAdapter seatAdapter;
    private RecentJoinersAdapter audienceAdapter;
    private String roomId;
    private boolean micOn = false;
    /** True only after the user taps mute — survives refresh / WhatsApp return. */
    private boolean userChoseMute = false;
    private boolean locked;
    private boolean isHost;
    private boolean isOwner;
    private boolean isPersistentRoom;
    private boolean isAgencyRoom;
    private boolean roomWelcomePosted;
    /** Guards cancel of deferred chrome/bg when a newer room LiveData lands. */
    private int roomUiBindSeq;
    @Nullable private String welcomePostedForRoomId;
    /** Local mirror of room.chatZoneEnabled — prevents tip/composer from re-showing after OFF. */
    private boolean roomChatZoneVisible = true;
    private String myUserId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SlotGameDtos.SessionDto slotSession;
    private MikooGameBridge mikooBridge;
    private MikooHashBridge hashBridge;
    private String overlayGameId;
    private String overlayPendingUrl;
    private boolean overlayGameReady;
    private final Runnable heartbeatSlot = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || slotSession == null || slotSession.sessionId == null) return;
            String sid = slotSession.sessionId;
            ContainerProvider.from(VoiceRoomActivity.this).getIoExecutor().execute(() ->
                    ApiCall.execute(ContainerProvider.from(VoiceRoomActivity.this)
                            .getSlotGameApi().heartbeat(sid)));
            handler.postDelayed(this, 15_000);
        }
    };
    private static final long JOIN_EFFECT_DEDUPE_MS = 8_000L;
    private final Map<String, Long> recentJoinEffects = new HashMap<>();
    private final Map<String, Long> recentGiftEffects = new HashMap<>();
    private boolean zegoLoggedIn;
    private boolean exiting;
    /** True when user minimized to Main — keep Zego/realtime, don't tear room down. */
    private boolean minimizing;
    /** Mikoo-style: leave/logout runs once; onDestroy must not repeat it. */
    private boolean roomTeardownDone;
    /** Speaker was muted by us because the app went to background (WhatsApp / Home). */
    private boolean mutedForBackground;
    private boolean speakerMutedBeforeBackground;
    /** Re-apply loudspeaker vs headset when user plugs/unplugs headphones mid-room. */
    @Nullable private BroadcastReceiver audioRouteReceiver;
    private boolean hoppingRoom;
    private boolean realtimeJoined;
    private boolean realtimeJoinInFlight;
    /** Mikoo-style white chat composer overlay is visible. */
    private boolean roomComposerOpen;
    private boolean roomComposerImeWasOpen;
    private GestureDetector roomSwipeDetector;
    private boolean switchingRoom;
    private boolean roomSwitchTeardownComplete;
    private float likeTapDownX;
    private float likeTapDownY;
    private long likeTapDownAt;
    private long lastLikeSentAt;
    private boolean roomGestureEligible;
    private int roomTouchSlop;
    private long lastRoomRefreshAt;
    private boolean roomRefreshPending;
    private final Runnable coalescedRoomRefresh = () -> {
        roomRefreshPending = false;
        if (roomId == null || exiting) return;
        lastRoomRefreshAt = System.currentTimeMillis();
        viewModel.refresh(roomId);
    };
    private boolean supporterRefreshPending;
    private final Runnable supporterRefresh = () -> {
        supporterRefreshPending = false;
        if (roomId != null && !exiting) viewModel.loadSupporters(roomId);
    };
    private String supportersRoomId;
    private String lastBoundCoverUrl;
    private String lastBoundBackgroundUrl;
    private String lastBoundHostCardKey;
    private String lastBoundHostStageKey;
    private int lastSeatGridLayoutKey = Integer.MIN_VALUE;
    private long lastHostGiftCoins = Long.MIN_VALUE;
    private Boolean lastMicUiState;
    private boolean roomSpeakerMuted;
    private int realtimeJoinAttempts;
    /** True when this Activity instance restored UI from {@link ActiveRoomSession}. */
    private boolean resumedFromActiveSession;
    private final Runnable retryRealtimeJoinRunnable = () -> {
        if (exiting || isFinishing() || roomId == null) return;
        connectRealtimeRoom();
    };
    private final Runnable retryHttpJoinRunnable = () -> {
        if (exiting || isFinishing() || roomId == null) return;
        // Never force a full rejoin while the live session is still in-process.
        if (ActiveRoomSession.get().canResumeUi(roomId) || resumedFromActiveSession) return;
        roomJoinLoadingDismissed = false;
        showRoomJoinLoading();
        String pass = getIntent() != null ? getIntent().getStringExtra(EXTRA_PASSWORD) : null;
        viewModel.join(roomId, pass);
    };
    private final Map<String, Float> pendingSoundLevels = new HashMap<>();
    private final Set<String> speakingUsers = new HashSet<>();
    /** Last wall-clock ms we got a real sound tick (level>0 or active speak) per user. */
    private final Map<String, Long> lastSoundTickMs = new HashMap<>();
    private boolean soundLevelDrainPending;
    private boolean hostStageSpeaking;
    private String hostStageUserId;
    private RoomDtos.JoinRoomResult pendingSession;
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (roomId != null && !exiting) {
                lastRoomRefreshAt = System.currentTimeMillis();
                viewModel.refresh(roomId);
                if (canInviteMic) viewModel.loadSeatRequests(roomId);
            }
            handler.postDelayed(this, 60_000L);
        }
    };
    private final Runnable roomGmtClockTick = new Runnable() {
        @Override
        public void run() {
            updateRoomGmtClock();
            if (!exiting && !isFinishing()) {
                handler.postDelayed(this, 30_000L);
            }
        }
    };

    private String roomHostId;
    private String roomCohostId;
    private String hostReactionUserId;
    private Runnable hostReactionHide;
    private String currentRoomCoverUrl;
    private final List<String> roomModeratorIds = new ArrayList<>();
    private List<RoomDtos.SeatDto> currentSeats = new ArrayList<>();
    /** Full room presence (host + seated + listeners) for the viewers sheet. */
    private final List<JsonObject> roomMembersAll = new ArrayList<>();
    private final List<JsonObject> roomAudience = new ArrayList<>();
    private RoomDtos.SupportersResult latestSupporters;
    private RoomEffectQueue effectQueue;
    private android.app.Dialog roomJoinLoading;
    private boolean roomJoinLoadingDismissed;
    private RoomVisualEffects visualEffects;
    private RoomVisualEffects giftVisualEffects;
    /** Keeps giftOverlay visible while lucky coin rain / مردود FX run. */
    private boolean luckyOverlayActive;
    private Runnable luckyOverlayRelease;
    private ExoPlayer roomMusicPlayer;
    /** Lazy: create only when a Mikoo game opens (WebView cold-create freezes S23 if done in onCreate). */
    @Nullable private WebView roomGameWebView;
    /** Lazy: YouTube embed disc WebView. */
    @Nullable private WebView musicYoutubeWebView;
    /** Lazy media3 PlayerViews (PlayerView in XML freezes inflate). */
    @Nullable private androidx.media3.ui.PlayerView musicVideoPlayerView;
    @Nullable private androidx.media3.ui.PlayerView musicFloatPlayerView;
    /** HTTP join kicked off at start of onCreate (parallel with UI setup). */
    private boolean earlyJoinStarted;
    private boolean musicUiReady;
    /** Prefetch completed before observers existed — publish after wires. */
    @Nullable private RoomDtos.JoinRoomResult pendingPrefetchSession;
    private String currentMusicUrl;
    private String dismissedMusicUrl;
    private String preparedMusicUrl;
    private String currentMusicStatus = "stopped";
    private String currentMusicStartedAt;
    private boolean musicEndReported;
    private boolean musicPanelExpanded;
    private ObjectAnimator musicDiscAnimator;
    private android.animation.AnimatorSet taskFloatPulseAnimator;
    private long roomMusicServerOffsetMs;
    private List<RoomDtos.MusicTrackDto> cachedServerMusicTracks = new ArrayList<>();
    @Nullable private String pendingYtResolveKey;
    private boolean youtubePlayingVideo;
    private boolean youtubeUsingEmbed;
    @Nullable private String currentMusicThumbUrl;
    private boolean canManageMusic;
    private boolean canChangeFrames;
    private boolean canControlGames;
    private boolean canMuteUsers;
    private boolean canKickUsers;
    private boolean canBanUsers;
    private boolean canManageSeats;
    private boolean canInviteMic;
    private boolean canManageRoom;
    private long rtcPublishTokenExpiresAtMs;
    private boolean rtcCanPublish;
    private boolean rtcTokenRequestInFlight;
    private boolean rtcReconnectInFlight;
    private RealtimeClient.RoomListener realtimeRoomListener;
    private RealtimeClient.UserListener moderationUserListener;
    /** Local mirror of server chat mute (text), until epoch ms. */
    private long chatMutedUntilMs;
    private ZegoEngineManager.RoomListener zegoRoomListener;
    private final ActivityResultLauncher<String[]> musicPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) addLocalMusicAndPlay(uri);
            });
    @Nullable private Runnable pendingDeviceMusicPermissionAction;
    private final ActivityResultLauncher<String[]> deviceMusicPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean ok = true;
                        for (Boolean v : result.values()) {
                            if (!Boolean.TRUE.equals(v)) {
                                ok = false;
                                break;
                            }
                        }
                        Runnable pending = pendingDeviceMusicPermissionAction;
                        pendingDeviceMusicPermissionAction = null;
                        if (ok) {
                            if (pending != null) {
                                try {
                                    pending.run();
                                } catch (Exception ignored) {
                                }
                            }
                        } else {
                            Toast.makeText(this,
                                    "يلزم إذن الوصول للملفات الصوتية لعرض أغاني الجهاز",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
    @Nullable private List<DeviceMusicScanner.Track> cachedDeviceMusicTracks;
    private final ActivityResultLauncher<String> roomCoverPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) uploadRoomCover(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.applyImmersiveDark(this);
        binding = ActivityVoiceRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Room id + ViewModel ASAP so network join can overlap with the rest of onCreate.
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(VoiceRoomViewModel.class);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        pendingSeatInviteDialog = getIntent().getBooleanExtra(EXTRA_PENDING_SEAT_INVITE, false);
        isHost = getIntent().getBooleanExtra(EXTRA_IS_HOST, false);
        myUserId = ContainerProvider.from(this).getSessionManager().getUserId();
        // Critical path: HTTP join overlaps layout inflate.
        // Prefer room join started from Home/Search before Activity open (RoomJoinPrefetch).
        boolean canResume = roomId != null && !roomId.isEmpty()
                && ActiveRoomSession.get().canResumeUi(roomId);
        if (roomId != null && !roomId.isEmpty() && !"demo-room-1".equals(roomId) && !canResume) {
            earlyJoinStarted = true;
            RoomDtos.JoinRoomResult prefetched = RoomJoinPrefetch.takeReady(roomId);
            if (prefetched != null) {
                // Will publish to LiveData after observers are registered (end of onCreate).
                pendingPrefetchSession = prefetched;
            } else if (RoomJoinPrefetch.isInFlight(roomId)) {
                RoomJoinPrefetch.await(roomId, (session, err) -> {
                    if (isFinishing() || exiting) return;
                    if (session != null) {
                        viewModel.restoreLocalSession(session, session.room);
                    } else if (err != null && !err.isEmpty()) {
                        // Fall through to normal join if prefetch failed soft.
                        viewModel.join(roomId, getIntent().getStringExtra(EXTRA_PASSWORD));
                    } else {
                        viewModel.join(roomId, getIntent().getStringExtra(EXTRA_PASSWORD));
                    }
                });
            } else {
                String pass0 = getIntent().getStringExtra(EXTRA_PASSWORD);
                // No home prefetch (deep link / rare path) — start join here once.
                viewModel.join(roomId, pass0);
            }
        }
        registerAudioRouteReceiver();
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding != null && binding.gameOverlay != null
                        && binding.gameOverlay.getVisibility() == View.VISIBLE) {
                    closeGameOverlay();
                    return;
                }
                // Always open Mikoo side drawer (Minimize / Exit / Settings / More).
                // Never auto-minimize or finish the room on back — user chooses on the panel.
                if (roomSidePanelDialog != null && roomSidePanelDialog.isShowing()) {
                    roomSidePanelDialog.dismiss();
                    return;
                }
                showRoomSidePanel();
            }
        });
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(this, "voiceRoom");
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyVoiceRoomInsets();
        applyResponsiveRoomLayout();
        if (VoiceRoomForegroundService.hasActiveRoom(this, roomId)) {
            VoiceRoomForegroundService.attachUi(this);
        }
        roomTouchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
        // Defer ExoPlayer + any WebView config until first frame (huge win on mid devices).
        binding.getRoot().post(this::ensureMusicUiReady);
        // Effects wrappers are cheap (views already GONE); still defer gift preloads.
        visualEffects = new RoomVisualEffects(binding.webVisualEffects);
        if (binding.giftChatEffects != null) {
            giftVisualEffects = new RoomVisualEffects(binding.giftChatEffects);
        }
        binding.getRoot().postDelayed(this::preloadRoomGiftMedia, 1_200L);
        effectQueue = new RoomEffectQueue(effect -> {
            if (effect.isEntry()) {
                if (!roomEntryEffectsEnabled()) {
                    effectQueue.notifyFinished();
                    return;
                }
                // Single Mikoo join toast (ComingMsgView). Full-screen ride only when media exists.
                String rideMedia = preferEntryRideMedia(effect.entryAnimationUrl, effect.entryEffectUrl);
                boolean hasRide = com.Dramizo.Series.util.CosmeticMedia.playableUrl(rideMedia) != null;
                if (hasRide && visualEffects != null) {
                    visualEffects.showEntry(
                            effect.displayName,
                            effect.avatarUrl,
                            rideMedia,
                            effect.durationMs,
                            effect.vipLevel,
                            effect.userLevel,
                            effect.wealthScore,
                            effect.vipBadgeUrl,
                            effect.levelBadgeUrl,
                            effect.hostBadgeUrl,
                            effect.isHost,
                            effect.showHiBadge,
                            effectQueue::notifyFinished);
                    binding.webVisualEffects.bringToFront();
                } else {
                    effectQueue.notifyFinished();
                }
                if (binding.comingMsgView != null) {
                    binding.comingMsgView.setupView(
                            effect.displayName,
                            effect.avatarUrl,
                            Math.max(1, effect.userLevel),
                            Math.max(0, effect.vipLevel));
                    binding.comingMsgView.bringToFront();
                    binding.comingMsgView.play(Math.max(2800L, Math.min(5000L, effect.durationMs)));
                }
            } else if (effect.isGift()) {
                // Never suppress VIDEO/SVGA gifts (e.g. user-uploaded أسد mp4 at 10 coins).
                String animCheck = effect.giftAnimationUrl;
                String mappedCheck = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                        effect.giftName, effect.giftIconUrl, animCheck);
                if (mappedCheck != null) animCheck = mappedCheck;
                com.Dramizo.Series.util.CosmeticMedia.Kind animKind =
                        com.Dramizo.Series.util.CosmeticMedia.kind(
                                com.Dramizo.Series.util.CosmeticMedia.playableUrl(animCheck));
                boolean hasFullscreenMedia =
                        animKind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                                || animKind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA;
                if (!hasFullscreenMedia
                        && !roomLowGiftEffectsEnabled()
                        && effect.totalCoins > 0 && effect.totalCoins < 100) {
                    effectQueue.notifyFinished();
                    return;
                }
                playGiftToRecipient(effect.giftName, effect.giftIconUrl,
                        mappedCheck != null ? mappedCheck : effect.giftAnimationUrl,
                        effect.displayName, Math.max(1, effect.comboCount),
                        effect.senderUserId, effect.senderVipLevel, effect.avatarUrl,
                        effect.hostBadgeUrl, effect.userLevel,
                        effect.receiverId, effect.receiverGiftCoins, false);
                // Gift duration is enforced by the queue safety timer (native gift also finishes itself).
            } else {
                effectQueue.notifyFinished();
            }
        });
        roomSwipeDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(@NonNull MotionEvent e) { return true; }
                    @Override
                    public boolean onFling(MotionEvent start, MotionEvent end,
                                           float velocityX, float velocityY) {
                        // TikTok-style: guests swipe up/down to hop public rooms.
                        // Hosts stay in their own room (no accidental leave).
                        if (isHost || !roomGestureEligible || start == null || end == null
                                || switchingRoom || hoppingRoom || exiting) return false;
                        float dy = end.getY() - start.getY();
                        float dx = end.getX() - start.getX();
                        // Vertical-dominant fling; soft thresholds like short-video feeds.
                        if (Math.abs(dy) < dp(56) || Math.abs(velocityY) < 480f
                                || Math.abs(dy) < Math.abs(dx) * 1.15f) return false;
                        switchRoomFeed(dy < 0 ? 1 : -1);
                        return true;
                    }
                });
        // Mic/camera permission: never block cold open — ask when user hits mic/seat later.
        handler.postDelayed(() -> {
            if (!isFinishing() && !exiting) {
                try {
                    PermissionHelper.ensureMediaPermissions(VoiceRoomActivity.this, false);
                } catch (Exception ignored) {
                }
            }
        }, 900L);
        applyRoomToolbarIcons();
        startRoomGmtClock();
        setupRealtime();
        setupZegoListener();
        setupLuckyBoxUi();

        seatAdapter = new SeatAdapter(new SeatAdapter.Listener() {
            @Override
            public void onSeatClick(View anchor, RoomDtos.SeatDto seat) {
                if (roomId == null || seat == null) return;
                if (seat.locked()) {
                    if (canManageSeats) viewModel.lockSeat(roomId, seat.seatIndex, false);
                    else Toast.makeText(VoiceRoomActivity.this, R.string.seat_locked_toast, Toast.LENGTH_SHORT).show();
                    return;
                }
                String occupantId = seatUserId(seat);
                boolean empty = occupantId == null || occupantId.isEmpty();
                boolean mine = myUserId != null && sameUser(myUserId, occupantId);
                if (mine) {
                    showMySeatMenu(anchor, seat);
                    return;
                }
                if (!empty) {
                    String name = seat.user != null
                            ? (seat.user.displayName != null ? seat.user.displayName : seat.user.username)
                            : "مستخدم";
                    int vip = seat.user != null ? Math.max(0, seat.user.vipLevel) : 0;
                    int lv = seat.user != null ? Math.max(1, seat.user.level) : 1;
                    String avatar = seat.user != null ? seat.user.avatarUrl : null;
                    String vipBadge = seat.user != null ? seat.user.vipBadgeUrl : null;
                    String hostBadge = seat.user != null ? seat.user.hostBadgeUrl : null;
                    showUserCard(occupantId, name, avatar,
                            vipBadge,
                            isAgencyRoom ? hostBadge : null,
                            vip, lv);
                    return;
                }

                // Host / owner can sit freely and hop seats. Guests already on mic can hop;
                // first seat requires host approval unless dashboard enables free mic.
                boolean freeMic = isFreeMicEnabled();
                boolean canHop = isHost || isOwner || isOnSeat(currentSeats) || freeMic;
                if (!PermissionHelper.hasAudioPermission(VoiceRoomActivity.this)) {
                    PermissionHelper.ensureMediaPermissions(VoiceRoomActivity.this, false);
                    Toast.makeText(VoiceRoomActivity.this, "فعّل إذن الميكروفون أولاً", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    if (canHop) {
                        optimisticTakeSeat(seat.seatIndex);
                        viewModel.takeSeat(roomId, seat.seatIndex);
                    } else {
                        viewModel.requestSeat(roomId, seat.seatIndex);
                    }
                } catch (Exception e) {
                    android.util.Log.e("VoiceRoom", "takeSeat click failed", e);
                    Toast.makeText(VoiceRoomActivity.this,
                            "تعذر الانتقال للمقعد", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSeatLongClick(RoomDtos.SeatDto seat) {
                if (!isRoomStaff() || roomId == null || seat == null) return;
                boolean occupiedByOther = seat.userId != null && !seat.userId.isEmpty()
                        && (myUserId == null || !myUserId.equals(seat.userId));
                if (occupiedByOther) {
                    String name = seat.user != null
                            ? firstNonEmpty(seat.user.displayName, seat.user.username, "مستخدم")
                            : "مستخدم";
                    showUserCard(
                            seat.userId,
                            name,
                            seat.user != null ? seat.user.avatarUrl : null,
                            seat.user != null ? seat.user.vipBadgeUrl : null,
                            isAgencyRoom ? (seat.user != null ? seat.user.hostBadgeUrl : null) : null,
                            seat.user != null ? Math.max(0, seat.user.vipLevel) : 0,
                            seat.user != null ? Math.max(1, seat.user.level) : 1);
                    return;
                }
                if (!canManageSeats) return;
                boolean lock = !seat.locked();
                if (occupiedByOther && lock) {
                    Toast.makeText(VoiceRoomActivity.this,
                            R.string.cannot_lock_occupied_seat, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!canToggleSeatLock(seat, lock)) {
                    Toast.makeText(VoiceRoomActivity.this,
                            lock ? "لا يمكن قفل هذا المقعد" : "المقعد غير مقفل",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.lockSeat(roomId, seat.seatIndex, lock);
                Toast.makeText(VoiceRoomActivity.this,
                        lock ? "تم قفل المقعد" : "تم فتح المقعد", Toast.LENGTH_SHORT).show();
            }
        });
        binding.recyclerSeats.setClipChildren(false);
        binding.recyclerSeats.setClipToPadding(false);
        binding.recyclerSeats.setNestedScrollingEnabled(false);
        binding.recyclerSeats.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.recyclerSeats.setItemViewCacheSize(16);
        if (binding.recyclerSeats.getParent() instanceof ViewGroup) {
            ((ViewGroup) binding.recyclerSeats.getParent()).setClipChildren(false);
        }
        binding.recyclerSeats.setLayoutManager(new GridLayoutManager(this, MIKOO_SEAT_COLUMNS) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        binding.recyclerSeats.setAdapter(seatAdapter);
        audienceAdapter = new RecentJoinersAdapter(member ->
                showUserCard(member.userId, member.displayName, member.avatarUrl,
                        member.vipBadgeUrl,
                        isAgencyRoom ? member.hostBadgeUrl : null,
                        member.vipLevel, member.userLevel));
        binding.recyclerRecentJoiners.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerRecentJoiners.setAdapter(audienceAdapter);
        binding.recyclerRecentJoiners.setNestedScrollingEnabled(false);

        binding.btnClose.setVisibility(View.VISIBLE);
        binding.btnClose.setOnClickListener(v -> confirmExit());
        binding.btnCloseGameOverlay.setOnClickListener(v -> closeGameOverlay());
        // Seat count lives under أدوات → غرفة (not header chrome).
        if (binding.btnHeaderHome != null) {
            binding.btnHeaderHome.setVisibility(View.GONE);
            binding.btnHeaderHome.setOnClickListener(null);
        }
        if (binding.btnPlusWrap != null) {
            binding.btnPlusWrap.setVisibility(View.GONE);
        }
        if (binding.btnHeaderMore != null) {
            binding.btnHeaderMore.setVisibility(View.VISIBLE);
            binding.btnHeaderMore.setContentDescription(getString(R.string.share_room_title));
            binding.btnHeaderMore.setOnClickListener(v -> openRoomSharePicker());
        }
        if (binding.viewerPill != null) {
            binding.viewerPill.setOnClickListener(v -> showRoomViewersSheet());
        }
        if (binding.tvViewers != null) {
            binding.tvViewers.setOnClickListener(v -> showRoomViewersSheet());
        }
        if (binding.tvRoomViewerCount != null) {
            binding.tvRoomViewerCount.setVisibility(View.VISIBLE);
            binding.tvRoomViewerCount.setOnClickListener(v -> showRoomViewersSheet());
        }
        if (binding.tvAudienceCount != null) {
            binding.tvAudienceCount.setOnClickListener(v -> showRoomViewersSheet());
        }
        if (binding.rowAudienceStrip != null) {
            binding.rowAudienceStrip.setOnClickListener(v -> showRoomViewersSheet());
        }
        if (binding.rowTopHosts != null) {
            binding.rowTopHosts.setOnClickListener(v ->
                    RoomContributeSheet.showSheet(getSupportFragmentManager(), roomId));
        }
        if (binding.rowRoomCupChip != null) {
            binding.rowRoomCupChip.setOnClickListener(v ->
                    RoomContributeSheet.showSheet(getSupportFragmentManager(), roomId));
        }
        if (binding.hostCard != null) {
            // Mikoo: header avatar is boss mic (seat 0) — sit / leave / profile.
            binding.hostCard.setOnClickListener(v -> onHostStageClick());
        }
        if (binding.hostStage != null) {
            binding.hostStage.setVisibility(View.GONE);
            binding.hostStage.setClickable(false);
            binding.hostStage.setOnClickListener(null);
        }
        binding.btnPlus.setOnClickListener(v -> {
            if (canInviteMic) {
                // Primary host action: approve/reject pending mic join requests.
                showSeatRequestsDialog();
            } else if (Boolean.TRUE.equals(viewModel.getHandRaised().getValue())) {
                viewModel.cancelSeatRequest(roomId);
            } else {
                Toast.makeText(this, R.string.tap_empty_seat_for_mic, Toast.LENGTH_LONG).show();
            }
        });
        binding.btnPlus.setOnLongClickListener(v -> {
            if (isHost) {
                showHostTools();
                return true;
            }
            if (canInviteMic) {
                showSeatRequestsDialog();
                return true;
            }
            return false;
        });

        binding.btnMic.setOnClickListener(v -> {
            RoomDtos.SeatDto mySeat = findMySeat(currentSeats);
            if (!micOn && mySeat != null && mySeat.isModeratorMuted) {
                Toast.makeText(this,
                        "الميكروفون مكتوم من المشرف حتى يسمح لك",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            micOn = !micOn;
            userChoseMute = !micOn;
            if (roomId != null) viewModel.setMic(roomId, !micOn);
            RoomRtcEngine.getInstance().setMicEnabled(micOn);
            syncVoiceAudio(currentSeats);
            syncMicUi();
            Toast.makeText(this, micOn ? R.string.mic_on : R.string.mic_off, Toast.LENGTH_SHORT).show();
        });
        binding.btnLock.setOnClickListener(v -> {
            if (!canManageRoom) {
                Toast.makeText(this, R.string.host_only_lock_room, Toast.LENGTH_SHORT).show();
                return;
            }
            if (isAgencyRoom) {
                Toast.makeText(this, "غرف الوكالة عامة ولا تُقفل بكلمة مرور", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!locked) {
                promptRoomPassword(pwd -> {
                    viewModel.lockRoom(roomId, true, pwd);
                    Toast.makeText(this, R.string.room_locked_with_password, Toast.LENGTH_SHORT).show();
                });
            } else {
                viewModel.lockRoom(roomId, false, null);
                Toast.makeText(this, R.string.room_unlocked, Toast.LENGTH_SHORT).show();
            }
        });
        binding.btnGift.setOnClickListener(v -> {
            RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
            // Default to live host — including self when I am hosting (Mikoo support).
            String hostTarget = resolveLiveHostId(room);
            GiftBottomSheet.show(getSupportFragmentManager(), hostTarget, null, roomId);
        });
        if (binding.btnPrivateMsg != null) {
            binding.btnPrivateMsg.setOnClickListener(v ->
                    RoomPrivateMsgSheet.showSheet(getSupportFragmentManager()));
        }
        binding.btnTools.setOnClickListener(v -> showOtherTools());
        binding.btnHeaderMore.setOnClickListener(v -> openRoomSharePicker());
        if (binding.btnHeaderHome != null) {
            binding.btnHeaderHome.setVisibility(View.GONE);
            binding.btnHeaderHome.setOnClickListener(null);
        }
        // Tasks float disabled for now — keep views GONE so they never overlay the room.
        if (binding.taskFloatWrap != null) {
            stopTaskFloatPulse();
            binding.taskFloatWrap.setVisibility(View.GONE);
            binding.taskFloatWrap.setOnClickListener(null);
        }
        binding.btnEmoji.setVisibility(View.VISIBLE);
        binding.btnGift.setVisibility(View.VISIBLE);
        binding.btnEmoji.setOnClickListener(v -> showEmojiPicker());
        if (binding.btnSeatSticker != null) {
            binding.btnSeatSticker.setVisibility(View.VISIBLE);
            binding.btnSeatSticker.setOnClickListener(v -> showEmojiPicker());
        }
        binding.btnGames.setOnClickListener(v -> showRoomGames());
        binding.btnSendChat.setOnClickListener(v -> appendChatFromInput());
        if (binding.tvChatInputTips != null) {
            binding.tvChatInputTips.setOnClickListener(v -> openRoomChatComposer(true));
        }
        if (binding.roomChatComposer != null) {
            binding.roomChatComposer.setOnClickListener(v -> closeRoomChatComposer(true));
        }
        if (binding.roomChatComposerBar != null) {
            binding.roomChatComposerBar.setOnClickListener(v -> { /* keep open */ });
        }
        binding.etChat.setOnEditorActionListener((view, actionId, event) -> {
            boolean send = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                    || (event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN);
            if (send) appendChatFromInput();
            return send;
        });
        syncMicUi();
        updateAdminControls();

        viewModel.getError().observe(this, e -> {
            if (e == null) return;
            String msg = e.toLowerCase(java.util.Locale.US);
            if (msg.contains("password") || msg.contains("كلمة المرور")
                    || msg.contains("room password") || msg.contains("invalid password")) {
                dismissRoomJoinLoading();
                promptRoomPassword(pwd -> {
                    roomJoinLoadingDismissed = false;
                    showRoomJoinLoading();
                    viewModel.join(roomId, pwd);
                });
            } else if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(e)
                    || msg.contains("room entry")) {
                dismissRoomJoinLoading();
                hoppingRoom = false;
                switchingRoom = false;
                com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
                // Gift / lucky / in-room spend must NEVER kick the user out.
                // Only unpaid room-entry (never joined) may leave this screen.
                boolean alreadyInRoom = pendingSession != null
                        || resumedFromActiveSession
                        || ActiveRoomSession.get().canResumeUi(roomId)
                        || zegoLoggedIn;
                if (!alreadyInRoom && !exiting) {
                    exiting = true;
                    finish();
                }
            } else if (isJoinBlockedError(msg)) {
                // Ban / room missing only — never eject for network / socket noise.
                dismissRoomJoinLoading();
                hoppingRoom = false;
                switchingRoom = false;
                Toast.makeText(this, e, Toast.LENGTH_LONG).show();
                if (!exiting) {
                    exiting = true;
                    finish();
                }
            } else {
                // Stay in the room screen and soft-retry HTTP join.
                dismissRoomJoinLoading();
                hoppingRoom = false;
                switchingRoom = false;
                // Already live (minimize resume) — never spam "اتصال ضعيف" / rejoin.
                if (resumedFromActiveSession || pendingSession != null
                        || ActiveRoomSession.get().canResumeUi(roomId)) {
                    return;
                }
                Toast.makeText(this, R.string.connection_slow_retrying, Toast.LENGTH_SHORT).show();
                if (!exiting && roomId != null && !roomId.isEmpty()) {
                    handler.removeCallbacks(retryHttpJoinRunnable);
                    handler.postDelayed(retryHttpJoinRunnable, 2_500L);
                }
            }
        });
        viewModel.getInfo().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getSeatRequests().observe(this, list -> {
            updateSeatRequestBadge(list);
        });
        viewModel.getSupporters().observe(this, result -> {
            latestSupporters = result;
            binding.rowAgencyTop.setVisibility(View.GONE);
            if (result != null) {
                updateTopSupporters(result.room);
                updateRoomCupChip(result.dayGold);
            }
        });
        binding.rowAgencyTop.setVisibility(View.GONE);
        viewModel.getRoom().observe(this, room -> {
            if (room == null) return;
            dismissRoomJoinLoading();
            roomId = room.id;
            final int bindSeq = ++roomUiBindSeq;
            // Disk chat wipe + history restore: off main thread (ANR on mid-range MIUI).
            final String chatClearedAtIso = room.chatClearedAt;
            final String chatRoomId = room.id;
            final String welcomeTitle = room.title != null ? room.title : getString(R.string.voice_room);
            final String welcomeDesc = room.description;
            scheduleRoomChatBootstrap(bindSeq, chatRoomId, chatClearedAtIso, welcomeTitle, welcomeDesc);
            roomHostId = room.hostId;
            roomCohostId = room.cohostId;
            currentRoomCoverUrl = room.coverUrl;
            isPersistentRoom = room.isPersistent;
            isAgencyRoom = RoomUiHelper.isAgencyRoom(room);
            if (seatAdapter != null) {
                seatAdapter.setAgencyRoom(isAgencyRoom);
            }
            if (audienceAdapter != null) {
                audienceAdapter.setAgencyRoom(isAgencyRoom);
            }
            roomModeratorIds.clear();
            if (room.moderatorIds != null) roomModeratorIds.addAll(room.moderatorIds);
            isHost = myUserId != null
                    && (sameUser(myUserId, room.hostId)
                    || sameUser(myUserId, room.activeHostId));
            isOwner = myUserId != null && sameUser(myUserId, room.hostId);
            boolean fullStaff = isHost || (myUserId != null && sameUser(myUserId, room.cohostId));
            canManageMusic = fullStaff;
            canChangeFrames = fullStaff;
            canControlGames = fullStaff;
            canMuteUsers = fullStaff;
            canKickUsers = fullStaff;
            canBanUsers = fullStaff;
            canManageSeats = fullStaff;
            canInviteMic = fullStaff;
            // Host / cohost / owner always manage room settings (not only owner flags).
            canManageRoom = fullStaff || isOwner || isHost;
            if (room.moderatorPermissions != null) {
                for (RoomDtos.ModeratorPermissionDto permission : room.moderatorPermissions) {
                    if (permission != null && myUserId != null
                            && sameUser(myUserId, permission.userId)) {
                        canManageMusic = canManageMusic || permission.canManageMusic;
                        canChangeFrames = canChangeFrames || permission.canChangeFrames;
                        canControlGames = canControlGames || permission.canControlGames;
                        canMuteUsers = canMuteUsers || permission.canMute;
                        canKickUsers = canKickUsers || permission.canKick;
                        canBanUsers = canBanUsers || permission.canBan;
                        canManageSeats = canManageSeats || permission.canManageSeats;
                        canInviteMic = canInviteMic || permission.canInvite;
                        canManageRoom = canManageRoom || permission.canManageRoom;
                    }
                }
            }
            // Platform staff (manager / super) get room moderation tools in every room.
            if (isPlatformManager()) {
                canMuteUsers = true;
                canKickUsers = true;
                canBanUsers = true;
                canManageSeats = true;
                canInviteMic = true;
            }
            if (isPlatformSuper()) {
                canManageMusic = true;
                canChangeFrames = true;
                canControlGames = true;
                canMuteUsers = true;
                canKickUsers = true;
                canBanUsers = true;
                canManageSeats = true;
                canInviteMic = true;
                canManageRoom = true;
            }
            // Appointed room mods (and hosts) should open room tools reliably.
            boolean appointed =
                    (myUserId != null && roomModeratorIds.contains(myUserId))
                    || isHost
                    || isOwner
                    || (myUserId != null && sameUser(myUserId, roomCohostId));
            if (appointed || isPlatformSuper()) {
                canManageRoom = true;
                canManageSeats = true;
                canChangeFrames = true;
                canBanUsers = true;
                canManageMusic = true;
            }
            // Manager (platform) stays person-focused: mute/kick/ban, not full host settings.
            if (isPlatformManager() && !isPlatformSuper() && !appointed) {
                canMuteUsers = true;
                canKickUsers = true;
                canBanUsers = true;
                canManageSeats = true;
                canInviteMic = true;
                canManageRoom = false;
                canManageMusic = false;
                canChangeFrames = false;
                canControlGames = false;
            }
            updateAdminControls();
            seatAdapter.setHostUserId(roomHostId);
            refreshSelfHostWearOnSeats();
            prefetchSelfWear();
            String displayTitle = room.title != null ? room.title : getString(R.string.voice_room);
            boolean agencyRoom = RoomUiHelper.isAgencyRoom(room);
            if (agencyRoom && displayTitle != null && !displayTitle.contains("وكالة")) {
                displayTitle = "وكالة · " + displayTitle;
            }
            setTextIfChanged(binding.tvRoomTitle, displayTitle);
            setTextIfChanged(binding.tvRoomBannerTitle, displayTitle);
            if (binding.tvRoomTitle != null && (isHost || isOwner || canManageRoom)) {
                binding.tvRoomTitle.setOnLongClickListener(v -> {
                    promptRenameRoomTitle(room);
                    return true;
                });
            }
            setTextIfChanged(binding.tvRoomBannerSubtitle,
                    room.description != null && !room.description.trim().isEmpty()
                            ? room.description : "مرحبًا بكم في الغرفة الصوتية");
            setRoomViewerCount(Math.max(0, room.viewerCount));
            // Agency rooms show agency GID; personal rooms show host publicId.
            String idLabel = RoomUiHelper.isAgencyRoom(room) ? "GID:" : "ID:";
            binding.tvRoomId.setText(idLabel + com.Dramizo.Series.util.RoomUiHelper.displayRoomId(room));
            applyRoomGiftSounds(room.giftSoundsEnabled);
            applyRoomDisplaySettings(room);

            // First paint: seats only. Heavy cover/kenar/host wear/bg staged after.
            List<RoomDtos.SeatDto> seats = room.seats != null ? room.seats : new ArrayList<>();
            boolean wasOnSeat = isOnSeat(currentSeats);
            currentSeats = seats;
            try {
                List<RoomDtos.SeatDto> gridSeats = prepareGuestSeatsForGrid(seats);
                if (binding.hostStage != null) binding.hostStage.setVisibility(View.GONE);
                applySeatGridUi(gridSeats, seats, room);
            } catch (Exception seatUiError) {
                android.util.Log.e("VoiceRoom", "seat UI bind failed", seatUiError);
            }
            notifyGiftSheetRecipientsChanged();
            if (!Objects.equals(supportersRoomId, room.id)) {
                supportersRoomId = room.id;
                scheduleSupporterRefresh(0L);
            }
            boolean nowOnSeat = isOnSeat(seats);
            RoomDtos.SeatDto mySeat = findMySeat(seats);

            // New seat assignment: open mic unless moderator-muted or user already chose mute.
            if (nowOnSeat && !wasOnSeat) {
                boolean modMuted = mySeat != null && mySeat.isModeratorMuted;
                try {
                    if (modMuted || userChoseMute) {
                        micOn = false;
                        RoomRtcEngine.getInstance().setMicEnabled(false);
                    } else {
                        micOn = true;
                        RoomRtcEngine.getInstance().setMicEnabled(true);
                        if (roomId != null) viewModel.setMic(roomId, false);
                    }
                } catch (Exception zegoErr) {
                    android.util.Log.e("VoiceRoom", "mic activate failed", zegoErr);
                }
                Toast.makeText(this, isHost ? "أنت المبدع على المايك" : "أنت على المايك الآن", Toast.LENGTH_SHORT).show();
            } else if (!nowOnSeat && wasOnSeat) {
                micOn = false;
                userChoseMute = false;
                try {
                    RoomRtcEngine.getInstance().setMicEnabled(false);
                    RoomRtcEngine.getInstance().stopPublishing();
                } catch (Exception ignored) {
                }
            } else if (mySeat != null && mySeat.isModeratorMuted) {
                // Only moderator mute may force local hardware off.
                // Self isMuted lags behind local toggles and caused mic flicker.
                if (micOn) {
                    micOn = false;
                    try {
                        RoomRtcEngine.getInstance().setMicEnabled(false);
                    } catch (Exception ignored) {
                    }
                }
            } else if (nowOnSeat && isHost && !userChoseMute && !micOn
                    && (mySeat == null || !mySeat.isModeratorMuted)) {
                // Host already seated on first open (no wasOnSeat edge) — still open mic.
                micOn = true;
                try {
                    RoomRtcEngine.getInstance().setMicEnabled(true);
                } catch (Exception ignored) {
                }
            }
            syncMicUi();
            // Seat audio only after RTC is up (session observer) — avoid triple fan-out on paint.
            if (zegoLoggedIn) {
                activateSeatAudio(seats, nowOnSeat && (!wasOnSeat || isHost));
            }
            locked = room.hasPassword || "locked".equalsIgnoreCase(room.status);
            ActiveRoomSession.get().updateRoom(room);
            ActiveRoomSession.get().syncFlags(isHost, isAgencyRoom, micOn, roomSpeakerMuted);

            // Stage heavy visuals so G85/MIUI can keep the first frame under ANR budget.
            final RoomDtos.RoomDto roomSnap = room;
            final List<RoomDtos.SeatDto> seatsSnap = seats;
            handler.post(() -> {
                if (bindSeq != roomUiBindSeq || exiting || isFinishing() || isDestroyed()) return;
                if (binding == null || roomSnap == null) return;
                try {
                    bindDeferredRoomChrome(roomSnap, seatsSnap);
                } catch (Exception e) {
                    android.util.Log.e("VoiceRoom", "deferred chrome bind failed", e);
                }
            });
            handler.postDelayed(() -> {
                if (bindSeq != roomUiBindSeq || exiting || isFinishing() || isDestroyed()) return;
                try {
                    applyRoomBackground(roomSnap.backgroundUrl);
                    applyMusicState(roomSnap.musicUrl, roomSnap.musicTitle, roomSnap.musicArtist,
                            roomSnap.musicStatus, roomSnap.musicPositionMs, roomSnap.musicStartedAt);
                } catch (Exception e) {
                    android.util.Log.e("VoiceRoom", "deferred bg/music failed", e);
                }
            }, 280L);
            // The realtime join event is authoritative, including this viewer's own spend/cosmetics.
        });
        viewModel.getSession().observe(this, session -> {
            if (session == null) return;
            pendingSession = session;
            hoppingRoom = false;
            switchingRoom = false;
            ActiveRoomSession.get().capture(
                    roomId, session, viewModel.getRoom().getValue(),
                    isHost, isAgencyRoom, micOn, roomSpeakerMuted);
            rtcCanPublish = session.canPublish;
            // expireAt from API is unix seconds — needed so first publish does not wait forever.
            rtcPublishTokenExpiresAtMs = session.expireAt > 0
                    ? session.expireAt * 1000L
                    : System.currentTimeMillis() + 55_000L;
            connectRealtimeRoom();
            // Refresh VIP/level so chat bubbles use real VIP from /users/me
            ContainerProvider.from(this).getIoExecutor().execute(() -> {
                com.Dramizo.Series.domain.model.Result<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> me =
                        ContainerProvider.from(this).getUserRepository().getMe();
                if (me.success && me.data != null) {
                    ContainerProvider.from(this).getSessionManager().updateCachedUser(me.data);
                }
            });
            if (zegoLoggedIn) return;
            // Give seats/chat a frame budget before native RTC create/login/audio route.
            final RoomDtos.JoinRoomResult sess = session;
            handler.postDelayed(() -> attachRtcForSession(sess), 320L);
        });

        handler.postDelayed(refreshRunnable, 60_000L);

        if (roomId == null || roomId.isEmpty() || "demo-room-1".equals(roomId)) {
            Toast.makeText(this, R.string.open_or_create_room, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (tryRestoreActiveRoomSession()) {
            return;
        }
        // Join already started at top of onCreate / home prefetch. Only show loading UI.
        final String joinRoomId = roomId;
        binding.getRoot().post(() -> {
            if (isFinishing() || isDestroyed() || exiting) return;
            if (joinRoomId == null || !joinRoomId.equals(roomId)) return;
            showRoomJoinLoading();
            // Prefetch finished before observers were attached — apply now.
            if (pendingPrefetchSession != null) {
                RoomDtos.JoinRoomResult pre = pendingPrefetchSession;
                pendingPrefetchSession = null;
                viewModel.restoreLocalSession(pre, pre.room);
                return;
            }
            if (!earlyJoinStarted) {
                String pass = getIntent().getStringExtra(EXTRA_PASSWORD);
                viewModel.join(joinRoomId, pass);
                earlyJoinStarted = true;
            }
        });
    }

    /** RTC side of session observer — deferred so first room paint stays under ANR budget. */
    private void attachRtcForSession(@Nullable RoomDtos.JoinRoomResult session) {
        if (session == null || exiting || isFinishing() || zegoLoggedIn) return;
        zegoLoggedIn = true;
        RoomRtcEngine.getInstance().applyJoinSession(
                this, session, roomId, myUserId);
        if (zegoRoomListener != null) {
            RoomRtcEngine.getInstance().preferActiveProviderListenersOnly(zegoRoomListener);
        }
        if (session.room != null && myUserId != null && myUserId.equals(session.room.hostId)) {
            isHost = true;
        }
        if (session.room != null && session.room.seats != null
                && (currentSeats == null || currentSeats.isEmpty())) {
            currentSeats = session.room.seats;
        }
        if (isOnSeat(currentSeats)) {
            if (!userChoseMute) {
                RoomDtos.SeatDto mySeat = findMySeat(currentSeats);
                if (mySeat == null || !mySeat.isModeratorMuted) {
                    micOn = true;
                }
            }
        }
        RoomRtcEngine.getInstance().setMicEnabled(micOn);
        roomSpeakerMuted = false;
        try {
            RoomRtcEngine.getInstance().clearPausedPlayStreams();
            RoomRtcEngine.getInstance().setSpeakerMuted(false);
            RoomSoundFx.setMuted(false);
        } catch (Exception ignored) {
        }
        final int audioEpoch = roomAudioEpoch;
        // One audio activate + one retry (was 0/180/700 triple — overloaded MIUI audio path).
        activateSeatAudio(currentSeats, true);
        handler.postDelayed(() -> {
            if (audioEpoch != roomAudioEpoch || exiting) return;
            activateSeatAudio(currentSeats, true);
        }, 450);
    }

    /**
     * Cover/kenar/header host — run after first seats paint (not in the same main pass).
     */
    private void bindDeferredRoomChrome(
            @NonNull RoomDtos.RoomDto room, @Nullable List<RoomDtos.SeatDto> seats) {
        if (binding == null) return;
        if (!Objects.equals(lastBoundCoverUrl, room.coverUrl)) {
            lastBoundCoverUrl = room.coverUrl;
            if (binding.imgRoomBannerCover != null) {
                Glide.with(this)
                        .load(AssetCatalog.absoluteUrl(room.coverUrl))
                        .centerCrop()
                        .placeholder(R.drawable.placeholder_cover)
                        .error(R.drawable.placeholder_cover)
                        .into(binding.imgRoomBannerCover);
            }
            lastBoundHostStageKey = null;
        }
        if (binding.hostCard != null
                && (room.roomCardUrl == null || room.roomCardUrl.isEmpty()
                || RoomKenarHelper.sanitize(room.roomCardUrl) == null)) {
            binding.hostCard.setBackgroundResource(R.drawable.bg_room_compact_card);
        }
        if (binding.tvHostName != null) {
            binding.tvHostName.setVisibility(View.GONE);
        }
        String hostCardKey = hostVisualKey(room.host) + "|" + room.roomCardUrl
                + "|" + (room.host != null ? room.host.roomCardUrl : null);
        if (!Objects.equals(lastBoundHostCardKey, hostCardKey)) {
            lastBoundHostCardKey = hostCardKey;
            bindHostCardRankBadges(room.host);
            if (binding.imgHostKenar != null) {
                String cardUrl = room.roomCardUrl;
                if ((cardUrl == null || cardUrl.isEmpty()) && room.host != null) {
                    cardUrl = room.host.roomCardUrl;
                }
                String lower = cardUrl != null ? cardUrl.toLowerCase(java.util.Locale.US) : "";
                boolean animated = lower.contains(".gif") || lower.contains(".webp")
                        || RoomKenarHelper.isRoomFrameUrl(cardUrl);
                RoomKenarHelper.bind(
                        binding.imgHostKenar,
                        binding.hostCard,
                        cardUrl,
                        room.id,
                        animated,
                        true);
            }
        }
        List<RoomDtos.SeatDto> seatList = seats != null ? seats : currentSeats;
        bindHeaderHostVisual(room, seatList);
    }

    /**
     * Load chat disk + respect server wipe on I/O; paint history + welcome after seats are up.
     */
    private void scheduleRoomChatBootstrap(
            int bindSeq,
            @NonNull String chatRoomId,
            @Nullable String chatClearedAtIso,
            @NonNull String welcomeTitle,
            @Nullable String welcomeDesc) {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            try {
                if (chatClearedAtIso != null && !chatClearedAtIso.isEmpty()) {
                    RoomChatMemory.honorServerWipe(chatRoomId, parseIsoMillis(chatClearedAtIso));
                }
            } catch (Exception ignored) {
            }
            List<RoomChatMemory.Line> lines;
            try {
                lines = RoomChatMemory.snapshot(chatRoomId);
            } catch (Exception e) {
                lines = Collections.emptyList();
            }
            // Cap first paint rows so recreate doesn't inflate 800 bubbles at once.
            final List<RoomChatMemory.Line> paintLines;
            if (lines.size() > 40) {
                paintLines = new ArrayList<>(lines.subList(lines.size() - 40, lines.size()));
            } else {
                paintLines = lines;
            }
            final int fullCount = lines.size();
            runOnUiThread(() -> {
                if (bindSeq != roomUiBindSeq || exiting || isFinishing() || isDestroyed()) return;
                if (binding == null || binding.chatLog == null) return;
                if (!Objects.equals(roomId, chatRoomId)) return;
                if (!roomChatRestored) {
                    restoringRoomChat = true;
                    try {
                        if (!paintLines.isEmpty()) {
                            binding.chatLog.removeAllViews();
                            for (RoomChatMemory.Line line : paintLines) {
                                if (line == null) continue;
                                appendChatLine(
                                        line.name,
                                        line.text,
                                        line.vipLevel,
                                        line.userLevel,
                                        line.frameUrl,
                                        line.userId,
                                        line.avatarUrl,
                                        line.giftIconUrl,
                                        line.wealthScore,
                                        line.charmScore);
                            }
                        }
                    } finally {
                        restoringRoomChat = false;
                        roomChatRestored = true;
                        roomChatMemorySyncedCount = fullCount;
                    }
                    if (!paintLines.isEmpty()) scrollChatToBottom(false);
                }
                if (!roomWelcomePosted || !Objects.equals(welcomePostedForRoomId, chatRoomId)) {
                    roomWelcomePosted = true;
                    welcomePostedForRoomId = chatRoomId;
                    String welcomeName = welcomeTitle;
                    boolean agencyRoom = isAgencyRoom;
                    if (agencyRoom && welcomeName != null && !welcomeName.contains("وكالة")) {
                        welcomeName = "وكالة · " + welcomeName;
                    }
                    String welcomeBody = welcomeDesc != null && !welcomeDesc.trim().isEmpty()
                            ? welcomeDesc.trim()
                            : ("مرحباً بكم في الغرفة الصوتية · " + welcomeName);
                    appendChatLine("النظام", welcomeBody, 0, 1, null, null, null, null);
                }
            });
        });
    }

    /**
     * Mikoo reopen: same process still in Zego room → paint UI from cache, skip loading/join.
     */
    private boolean tryRestoreActiveRoomSession() {
        ActiveRoomSession ars = ActiveRoomSession.get();
        if (!ars.canResumeUi(roomId)) return false;
        RoomDtos.JoinRoomResult join = ars.session();
        if (join == null) return false;
        resumedFromActiveSession = true;
        roomJoinLoadingDismissed = true;
        dismissRoomJoinLoading();
        ars.setMinimized(false);
        isHost = ars.isHost() || getIntent().getBooleanExtra(EXTRA_IS_HOST, false);
        isAgencyRoom = ars.isAgencyRoom();
        try {
            micOn = RoomRtcEngine.getInstance().isMicEnabled();
        } catch (Exception ignored) {
            micOn = ars.isMicOn();
        }
        roomSpeakerMuted = ars.isSpeakerMuted();
        // Zego already logged in — do not call loginRoom again.
        zegoLoggedIn = true;
        realtimeJoined = RealtimeClient.getInstance().isJoinedRoom(roomId);
        realtimeJoinAttempts = 0;
        viewModel.restoreLocalSession(join, ars.latestRoom());
        // Soft sync only (no join loading).
        handler.post(() -> {
            if (exiting || roomId == null) return;
            viewModel.refresh(roomId);
            viewModel.loadSupporters(roomId);
            if (!realtimeJoined) connectRealtimeRoom();
            try {
                activateSeatAudio(currentSeats, true);
            } catch (Exception ignored) {
            }
            syncMicUi();
        });
        return true;
    }

    private void captureActiveRoomSession() {
        if (roomId == null || roomId.isEmpty() || pendingSession == null) return;
        ActiveRoomSession.get().capture(
                roomId,
                pendingSession,
                viewModel != null ? viewModel.getRoom().getValue() : null,
                isHost,
                isAgencyRoom,
                micOn,
                roomSpeakerMuted);
    }

    private void showRoomJoinLoading() {
        if (roomJoinLoadingDismissed) return;
        if (roomJoinLoading != null && roomJoinLoading.isShowing()) return;
        roomJoinLoading = com.Dramizo.Series.util.RoomJoinLoading.show(this, null);
        // Longer on weak networks — user should see loading, not a sudden eject.
        handler.postDelayed(this::dismissRoomJoinLoading, 20_000L);
    }

    private void ensureMusicUiReady() {
        if (musicUiReady || isFinishing() || isDestroyed() || binding == null) return;
        try {
            setupMusicUi();
            musicUiReady = true;
        } catch (Exception e) {
            android.util.Log.e("VoiceRoom", "music UI init failed", e);
        }
    }

    /** Lazy room game WebView — never inflate at Activity open (main ANR on WebView ctor). */
    @NonNull
    private WebView ensureRoomGameWebView() {
        if (roomGameWebView != null) return roomGameWebView;
        FrameLayout host = binding != null ? binding.webGameHost : null;
        roomGameWebView = new WebView(this);
        roomGameWebView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        roomGameWebView.setBackgroundColor(Color.TRANSPARENT);
        forceRoomGameLtr(roomGameWebView);
        if (host != null) {
            host.removeAllViews();
            host.addView(roomGameWebView);
        }
        return roomGameWebView;
    }

    @Nullable
    private WebView ensureMusicYoutubeWebView() {
        if (musicYoutubeWebView != null) return musicYoutubeWebView;
        if (binding == null || binding.musicYoutubeWebHost == null) return null;
        musicYoutubeWebView = new WebView(this);
        musicYoutubeWebView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings ws = musicYoutubeWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        musicYoutubeWebView.setBackgroundColor(Color.BLACK);
        musicYoutubeWebView.setWebChromeClient(new WebChromeClient());
        musicYoutubeWebView.setWebViewClient(new WebViewClient());
        binding.musicYoutubeWebHost.removeAllViews();
        binding.musicYoutubeWebHost.addView(musicYoutubeWebView);
        return musicYoutubeWebView;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Nullable
    private androidx.media3.ui.PlayerView ensureMusicVideoPlayerView() {
        if (musicVideoPlayerView != null) return musicVideoPlayerView;
        if (binding == null || binding.musicVideoSurface == null) return null;
        androidx.media3.ui.PlayerView pv = new androidx.media3.ui.PlayerView(this);
        pv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        pv.setUseController(false);
        try {
            pv.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        } catch (Exception ignored) {
        }
        binding.musicVideoSurface.removeAllViews();
        binding.musicVideoSurface.addView(pv);
        musicVideoPlayerView = pv;
        return musicVideoPlayerView;
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Nullable
    private androidx.media3.ui.PlayerView ensureMusicFloatPlayerView() {
        if (musicFloatPlayerView != null) return musicFloatPlayerView;
        if (binding == null || binding.musicFloatVideo == null) return null;
        androidx.media3.ui.PlayerView pv = new androidx.media3.ui.PlayerView(this);
        pv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        pv.setUseController(false);
        try {
            pv.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        } catch (Exception ignored) {
        }
        binding.musicFloatVideo.removeAllViews();
        binding.musicFloatVideo.addView(pv);
        musicFloatPlayerView = pv;
        return musicFloatPlayerView;
    }

    private void musicVideoSetPlayer(@Nullable ExoPlayer player) {
        try {
            if (player == null) {
                if (musicVideoPlayerView != null) musicVideoPlayerView.setPlayer(null);
                return;
            }
            androidx.media3.ui.PlayerView pv = ensureMusicVideoPlayerView();
            if (pv != null) pv.setPlayer(player);
        } catch (Exception ignored) {
        }
    }

    private void musicFloatSetPlayer(@Nullable ExoPlayer player) {
        try {
            if (player == null) {
                if (musicFloatPlayerView != null) musicFloatPlayerView.setPlayer(null);
                return;
            }
            androidx.media3.ui.PlayerView pv = ensureMusicFloatPlayerView();
            if (pv != null) pv.setPlayer(player);
        } catch (Exception ignored) {
        }
    }

    private void destroyLazyWebViews() {
        if (roomGameWebView != null) {
            try {
                roomGameWebView.stopLoading();
                roomGameWebView.loadUrl("about:blank");
                roomGameWebView.removeAllViews();
                roomGameWebView.destroy();
            } catch (Exception ignored) {
            }
            roomGameWebView = null;
            try {
                if (binding != null && binding.webGameHost != null) {
                    binding.webGameHost.removeAllViews();
                }
            } catch (Exception ignored) {
            }
        }
        if (musicYoutubeWebView != null) {
            try {
                musicYoutubeWebView.stopLoading();
                musicYoutubeWebView.loadUrl("about:blank");
                musicYoutubeWebView.destroy();
            } catch (Exception ignored) {
            }
            musicYoutubeWebView = null;
            try {
                if (binding != null && binding.musicYoutubeWebHost != null) {
                    binding.musicYoutubeWebHost.removeAllViews();
                    binding.musicYoutubeWebHost.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void dismissRoomJoinLoading() {
        roomJoinLoadingDismissed = true;
        com.Dramizo.Series.util.RoomJoinLoading.dismiss(roomJoinLoading);
        roomJoinLoading = null;
    }

    private void promptRoomPassword(java.util.function.Consumer<String> onOk) {
        AuraDialogHelper.prompt(this,
                getString(R.string.room_locked_title),
                getString(R.string.enter_room_password),
                getString(R.string.room_password_hint),
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                getString(android.R.string.ok),
                pwd -> {
                    if (pwd == null || pwd.isEmpty()) {
                        Toast.makeText(this, R.string.enter_room_password, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onOk.accept(pwd);
                });
    }

    private void appendChatFromInput() {
        String text = binding.etChat.getText() != null ? binding.etChat.getText().toString().trim() : "";
        if (text.isEmpty() || roomId == null) return;
        if (isChatTextMuted()) {
            Toast.makeText(this, chatMuteToastMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        com.Dramizo.Series.data.local.prefs.SessionManager sm =
                ContainerProvider.from(this).getSessionManager();
        if (sm.isChatPromoFilterFromServer()) {
            java.util.List<String> extra =
                    com.Dramizo.Series.util.ChatContentFilter.parseExtraKeywords(
                            sm.getChatExtraKeywordsFromServer());
            if (com.Dramizo.Series.util.ChatContentFilter.isBlocked(text, extra)) {
                Toast.makeText(
                                this,
                                com.Dramizo.Series.util.ChatContentFilter.BLOCK_REASON,
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }
        }
        String me = sm.getDisplayName();
        if (me == null || me.isEmpty()) me = "أنا";
        int vip = sm.getVipLevel();
        int level = Math.max(1, sm.getUserLevel());
        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto u = sm.getUser();
        String avatar = u != null ? u.avatarUrl : null;
        long wealth = u != null ? Math.max(u.wealthScore, u.totalSentCoins) : 0L;
        long charm = u != null ? Math.max(0L, u.charmScore) : 0L;
        String vipFrame = u != null ? u.vipBadgeUrl : null;
        String hostFrame = u != null ? u.hostBadgeUrl : null;
        // Agency rooms show host signal; personal rooms show VIP frame.
        String frame = isAgencyRoom ? hostFrame : vipFrame;
        JsonObject payload = new JsonObject();
        payload.addProperty("t", "chat");
        payload.addProperty("text", text);
        payload.addProperty("name", me);
        payload.addProperty("vipLevel", vip);
        payload.addProperty("userLevel", level);
        payload.addProperty("userId", myUserId != null ? myUserId : "");
        payload.addProperty("isHost", isHost || (myUserId != null && myUserId.equals(roomHostId)));
        payload.addProperty("wealthScore", wealth);
        payload.addProperty("charmScore", charm);
        if (avatar != null && !avatar.isEmpty()) payload.addProperty("avatarUrl", avatar);
        if (vipFrame != null && !vipFrame.isEmpty()) payload.addProperty("vipBadgeUrl", vipFrame);
        if (hostFrame != null && !hostFrame.isEmpty()) payload.addProperty("hostBadgeUrl", hostFrame);
        if (frame != null && !frame.isEmpty()) payload.addProperty("frameUrl", frame);

        final String displayMe = me;
        final String displayFrame = frame;
        final String displayAvatar = avatar;
        final long displayWealth = wealth;
        final long displayCharm = charm;
        final int displayVip = vip;
        final int displayLevel = level;
        final boolean serverModeration = sm.isChatPromoFilterFromServer();

        // When server promo moderation is on: socket-first so mute/kick can run.
        // Otherwise keep Zego as the primary broadcast path.
        if (serverModeration) {
            if (!realtimeJoined) {
                Toast.makeText(this, "جارٍ الاتصال بالغرفة… حاول مجدداً", Toast.LENGTH_SHORT).show();
                connectRealtimeRoom();
                return;
            }
            boolean emitted = RealtimeClient.getInstance().emitRoomEvent(
                    roomId, "chat:message", payload, (ok, error, code) -> runOnUiThread(() -> {
                        if (!ok) {
                            if ("CHAT_MUTED".equals(code) || (error != null && error.contains("مكتوم"))) {
                                applyChatMuteFromServer(null, error);
                            }
                            Toast.makeText(
                                            this,
                                            error != null && !error.isEmpty()
                                                    ? error
                                                    : com.Dramizo.Series.util.ChatContentFilter.BLOCK_REASON,
                                            Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                        // Socket already broadcast to peers; skip Zego to avoid duplicate lines.
                        appendChatLine(
                                displayMe, text, displayVip, displayLevel, displayFrame,
                                myUserId, displayAvatar, null, displayWealth, displayCharm);
                        binding.etChat.setText("");
                        closeRoomChatComposer(true);
                    }));
            if (!emitted) {
                Toast.makeText(this, "غير متصل — تعذر إرسال الرسالة", Toast.LENGTH_SHORT).show();
                connectRealtimeRoom();
            }
            return;
        }

        // Primary path: Zego in-room broadcast (same platform as voice).
        boolean zegoOk = RoomRtcEngine.getInstance().isInRoom(roomId)
                && RoomRtcEngine.getInstance().sendRoomChatMessage(
                        roomId, payload.toString(), (ok, err) -> {
                            if (!ok) {
                                runOnUiThread(() -> {
                                    boolean sent = RealtimeClient.getInstance()
                                            .emitRoomEvent(roomId, "chat:message", payload);
                                    if (!sent) {
                                        Toast.makeText(this, "تعذر إرسال الرسالة", Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                });
                            }
                        });
        if (!zegoOk) {
            if (!realtimeJoined) {
                Toast.makeText(this, "جارٍ الاتصال بالغرفة… حاول مجدداً", Toast.LENGTH_SHORT).show();
                connectRealtimeRoom();
                return;
            }
            boolean sent = RealtimeClient.getInstance().emitRoomEvent(roomId, "chat:message", payload);
            if (!sent) {
                Toast.makeText(this, "غير متصل — تعذر إرسال الرسالة", Toast.LENGTH_SHORT).show();
                connectRealtimeRoom();
                return;
            }
        }
        appendChatLine(me, text, vip, level, frame, myUserId, avatar, null, wealth, charm);
        binding.etChat.setText("");
        closeRoomChatComposer(true);
    }

    /** Mikoo MultiInputMsgView: white bar + keyboard only — hide tools bar. */
    private void openRoomChatComposer(boolean showKeyboard) {
        if (binding == null || binding.roomChatComposer == null) return;
        roomComposerOpen = true;
        // Gift / games / mute / settings must NOT rise with the keyboard.
        if (binding.bottomBar != null) {
            binding.bottomBar.setVisibility(View.GONE);
        }
        binding.roomChatComposer.setVisibility(View.VISIBLE);
        binding.roomChatComposer.bringToFront();
        if (binding.tvChatInputTips != null) binding.tvChatInputTips.setVisibility(View.INVISIBLE);
        binding.etChat.requestFocus();
        ViewCompat.requestApplyInsets(binding.getRoot());
        if (showKeyboard) {
            binding.etChat.post(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(binding.etChat,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void closeRoomChatComposer(boolean hideKeyboard) {
        if (binding == null) return;
        roomComposerOpen = false;
        roomComposerImeWasOpen = false;
        if (hideKeyboard && binding.etChat != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && binding.etChat.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(binding.etChat.getWindowToken(), 0);
            }
            binding.etChat.clearFocus();
        }
        if (binding.roomChatComposer != null) {
            binding.roomChatComposer.setVisibility(View.GONE);
            binding.roomChatComposer.setPadding(0, 0, 0, 0);
        }
        // Restore gift/games/settings row under the tip pill.
        if (binding.bottomBar != null) {
            binding.bottomBar.setVisibility(View.VISIBLE);
        }
        if (binding.tvChatInputTips != null) {
            binding.tvChatInputTips.setVisibility(View.VISIBLE);
        }
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void focusRoomChatComposer(String text, int selection) {
        openRoomChatComposer(true);
        if (text != null) {
            binding.etChat.setText(text);
            int sel = Math.max(0, Math.min(selection, text.length()));
            binding.etChat.setSelection(sel);
        }
    }

    /** Marker name for game/luck celebration rows (no white name / no profile photo). */
    private static final String CHAT_NAME_GAME_CEL = "\u200B#game_cel";

    private void appendChatLine(String name, String text, int vipLevel, int userLevel) {
        appendChatLine(name, text, vipLevel, userLevel, null);
    }

    private void appendChatLine(String name, String text, int vipLevel, int userLevel, String frameUrl) {
        appendChatLine(name, text, vipLevel, userLevel, frameUrl, null, null, null, 0L, 0L);
    }

    /**
     * Game / luck / return-gift celebrations: single-body strip with optional game cover.
     * Not a personal chat bubble (no avatar, no white name over the line).
     */
    private void appendGameCelebrationLine(String text, @Nullable String gameIconUrl) {
        appendChatLine(CHAT_NAME_GAME_CEL, text, 0, 0, null, null, null, gameIconUrl, 0L, 0L);
    }

    private void appendChatLine(
            String name,
            String text,
            int vipLevel,
            int userLevel,
            String frameUrl,
            String userId,
            String avatarUrl) {
        appendChatLine(name, text, vipLevel, userLevel, frameUrl, userId, avatarUrl, null, 0L, 0L);
    }

    private void appendChatLine(
            String name,
            String text,
            int vipLevel,
            int userLevel,
            String frameUrl,
            String userId,
            String avatarUrl,
            String giftIconUrl) {
        appendChatLine(name, text, vipLevel, userLevel, frameUrl, userId, avatarUrl, giftIconUrl, 0L, 0L);
    }

    private void appendChatLine(
            String name,
            String text,
            int vipLevel,
            int userLevel,
            String frameUrl,
            String userId,
            String avatarUrl,
            String giftIconUrl,
            long wealthScore,
            long charmScore) {
        boolean system = "النظام".equals(name) || getString(R.string.official_news).equals(name);
        boolean gameCel = CHAT_NAME_GAME_CEL.equals(name);
        if (!system && !gameCel && vipLevel <= 0) {
            var session = ContainerProvider.from(this).getSessionManager();
            // Only enrich cosmetics for the local user by id — never by display name.
            boolean self = userId != null && myUserId != null && sameUser(userId, myUserId);
            if (self) {
                vipLevel = session.getVipLevel();
                userLevel = Math.max(1, session.getUserLevel());
                if (avatarUrl == null) avatarUrl = session.getAvatarUrl();
                AuthDtos.UserDto selfUser = session.getUser();
                if (frameUrl == null && selfUser != null) {
                    if (isAgencyRoom) {
                        frameUrl = selfUser.hostBadgeUrl;
                        if (frameUrl == null || frameUrl.isEmpty()) frameUrl = selfUser.vipBadgeUrl;
                    } else {
                        frameUrl = selfUser.vipBadgeUrl;
                    }
                }
                if ((frameUrl == null || frameUrl.isEmpty()) && isAgencyRoom) {
                    frameUrl = session.getHostBadgeUrl();
                }
                if ((frameUrl == null || frameUrl.isEmpty()) && selfUser != null) {
                    frameUrl = selfUser.vipBadgeUrl;
                }
                if (selfUser != null) {
                    if (wealthScore <= 0) wealthScore = Math.max(selfUser.wealthScore, selfUser.totalSentCoins);
                    if (charmScore <= 0) charmScore = Math.max(0L, selfUser.charmScore);
                }
            }
        }
        // Self wear: always apply equipped VIP/host frame when payload omitted it (staff included).
        if (!system && !gameCel && userId != null && myUserId != null && sameUser(userId, myUserId)) {
            var session = ContainerProvider.from(this).getSessionManager();
            AuthDtos.UserDto selfUser = session.getUser();
            if (selfUser != null) {
                if (avatarUrl == null || avatarUrl.isEmpty()) avatarUrl = selfUser.avatarUrl;
                if (frameUrl == null || frameUrl.isEmpty()) {
                    if (isAgencyRoom && selfUser.hostBadgeUrl != null && !selfUser.hostBadgeUrl.isEmpty()) {
                        frameUrl = selfUser.hostBadgeUrl;
                    } else if (selfUser.vipBadgeUrl != null && !selfUser.vipBadgeUrl.isEmpty()) {
                        frameUrl = selfUser.vipBadgeUrl;
                    } else if (isAgencyRoom) {
                        frameUrl = session.getHostBadgeUrl();
                    }
                }
            }
        }
        View row = getLayoutInflater().inflate(R.layout.item_room_chat_line, binding.chatLog, false);
        View bubble = row.findViewById(R.id.chatBubbleRoot);
        ImageView avatar = row.findViewById(R.id.imgChatAvatar);
        ImageView frameView = row.findViewById(R.id.imgChatFrame);
        ImageView giftIcon = row.findViewById(R.id.imgChatGift);
        TextView hostChip = row.findViewById(R.id.tvHostChip);
        TextView vipChip = row.findViewById(R.id.tvVipChip);
        TextView levelChip = row.findViewById(R.id.tvLevelChip);
        TextView charmChip = row.findViewById(R.id.tvCharmChip);
        TextView wealthChip = row.findViewById(R.id.tvWealthChip);
        TextView nameView = row.findViewById(R.id.tvChatName);
        TextView meta = row.findViewById(R.id.tvChatMeta);
        TextView line = row.findViewById(R.id.tvChatLine);
        View userMore = row.findViewById(R.id.btnChatUserMore);
        View avatarWrap = row.findViewById(R.id.chatAvatarWrap);

        VipStyle.applyBubble(bubble, Math.max(0, vipLevel));
        line.setTextColor(VipStyle.messageColor(Math.max(0, vipLevel)));
        if (gameCel) {
            if (nameView != null) nameView.setVisibility(View.GONE);
            if (hostChip != null) hostChip.setVisibility(View.GONE);
            if (vipChip != null) vipChip.setVisibility(View.GONE);
            if (levelChip != null) levelChip.setVisibility(View.GONE);
            if (charmChip != null) charmChip.setVisibility(View.GONE);
            if (wealthChip != null) wealthChip.setVisibility(View.GONE);
            HostSignalView hostSignal = row.findViewById(R.id.webChatHostSignal);
            if (hostSignal != null) {
                hostSignal.clearSignal();
                hostSignal.setVisibility(View.GONE);
            }
            // Left: game cover only (not profile). Fallback: hide avatar slot.
            if (giftIconUrl != null && !giftIconUrl.isEmpty() && avatar != null) {
                if (avatarWrap != null) avatarWrap.setVisibility(View.VISIBLE);
                avatar.setVisibility(View.VISIBLE);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setPadding(0, 0, 0, 0);
                if (frameView != null) {
                    frameView.setVisibility(View.GONE);
                    frameView.setImageDrawable(null);
                }
                try {
                    Glide.with(avatar)
                            .load(AssetCatalog.absoluteUrl(giftIconUrl))
                            .centerCrop()
                            .placeholder(R.drawable.ic_screen_chat_lottery)
                            .error(R.drawable.ic_screen_chat_lottery)
                            .into(avatar);
                } catch (Exception ignored) {
                    avatar.setImageResource(R.drawable.ic_screen_chat_lottery);
                }
                giftIconUrl = null; // already used as left cover
            } else if (avatarWrap != null) {
                avatarWrap.setVisibility(View.GONE);
            } else if (avatar != null) {
                avatar.setVisibility(View.GONE);
            }
        } else {
            nameView.setText(name != null && !name.isEmpty() ? name : "—");
            nameView.setTextColor(VipStyle.nameColor(vipLevel));
            if (avatar != null) {
                HostSignalView hostSignal = row.findViewById(R.id.webChatHostSignal);
                if (system) {
                    avatar.setVisibility(View.VISIBLE);
                    AvatarImageLoader.applyCircularClip(avatar);
                    // App logo for system / official lines (not a blank white tile).
                    avatar.setImageResource(R.drawable.jeho_logo);
                    avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    avatar.setPadding(dp(4), dp(4), dp(4), dp(4));
                    resetChatWear(avatar, frameView, hostSignal);
                } else {
                    AvatarCosmetics.bindStacked(avatar, frameView, hostSignal, avatarUrl, frameUrl, null);
                }
            }
            boolean creator = !system && userId != null && roomHostId != null && userId.equals(roomHostId);
            if (hostChip != null) hostChip.setVisibility(creator ? View.VISIBLE : View.GONE);

            if (vipLevel > 0) {
                vipChip.setVisibility(View.VISIBLE);
                vipChip.setText("VIP" + vipLevel);
                VipStyle.applyChip(vipChip, vipLevel);
                vipChip.setTextColor(VipStyle.chipTextColor(vipLevel));
            } else {
                vipChip.setVisibility(View.GONE);
            }

            if (system) {
                levelChip.setVisibility(View.GONE);
                charmChip.setVisibility(View.GONE);
                wealthChip.setVisibility(View.GONE);
            } else {
                AvatarCosmetics.styleBadges(levelChip, charmChip, wealthChip, vipLevel, userLevel,
                        Math.max(0L, charmScore), Math.max(0L, wealthScore));
                if (!roomCharmEnabled() && charmChip != null) {
                    charmChip.setVisibility(View.GONE);
                }
            }
        }
        if (meta != null) meta.setVisibility(View.GONE);
        line.setText(text != null ? text : "");
        if (giftIcon != null) {
            if (giftIconUrl != null && !giftIconUrl.isEmpty()) {
                giftIcon.setVisibility(View.VISIBLE);
                Glide.with(giftIcon)
                        .load(AssetCatalog.absoluteUrl(giftIconUrl))
                        .placeholder(R.drawable.ic_screen_chat_lottery)
                        .error(R.drawable.ic_screen_chat_lottery)
                        .into(giftIcon);
            } else if (!gameCel && text != null && text.contains("هدية")) {
                giftIcon.setVisibility(View.VISIBLE);
                giftIcon.setImageResource(R.drawable.ic_screen_chat_lottery);
            } else {
                giftIcon.setVisibility(View.GONE);
                giftIcon.setImageDrawable(null);
            }
        }

        final String tapName = gameCel ? null : name;
        final String tapUserId = gameCel ? null : userId;
        final String tapAvatar = gameCel ? null : avatarUrl;
        final String tapFrame = gameCel ? null : frameUrl;
        final int tapVip = vipLevel;
        final int tapLv = userLevel;
        final String tapText = text;
        final View rowRef = row;
        final boolean noUserMenu = system || gameCel;
        View.OnClickListener openUserMenu = v -> {
            if (noUserMenu) return;
            showChatUserActions(v, tapUserId, tapName, tapAvatar, tapFrame, tapVip, tapLv, tapText, rowRef);
        };
        View.OnLongClickListener openUserMenuLong = v -> {
            if (noUserMenu) return false;
            showChatUserActions(v, tapUserId, tapName, tapAvatar, tapFrame, tapVip, tapLv, tapText, rowRef);
            return true;
        };
        // Tap / long-press name or avatar → moderation menu (no ⋮).
        if (nameView != null) {
            nameView.setOnClickListener(openUserMenu);
            nameView.setOnLongClickListener(openUserMenuLong);
        }
        if (avatar != null) {
            avatar.setOnClickListener(openUserMenu);
            avatar.setOnLongClickListener(openUserMenuLong);
        }
        bubble.setOnClickListener(null);
        bubble.setOnLongClickListener(openUserMenuLong);
        if (userMore != null) userMore.setVisibility(View.GONE);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 1;
        row.setLayoutParams(lp);
        // Mikoo room chat: all public lines start-aligned (no mine-on-right).
        if (row instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout rowLl = (android.widget.LinearLayout) row;
            rowLl.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            rowLl.setGravity(android.view.Gravity.START);
        }
        binding.chatLog.addView(row);
        // Keep all live-session messages — never drop mid-broadcast (only end live clears).
        if (roomId != null && !restoringRoomChat) {
            RoomChatMemory.append(roomId, new RoomChatMemory.Line(
                    name, text, vipLevel, userLevel, frameUrl, userId, avatarUrl, giftIconUrl,
                    wealthScore, charmScore));
        }
        // Soft memory safety only (very high); still never auto-wipe mid-session intentionally.
        while (binding.chatLog.getChildCount() > 800) {
            binding.chatLog.removeViewAt(0);
        }
        // TikTok-style: always stick to latest comments.
        scrollChatToBottom(true);
    }

    private boolean restoringRoomChat;
    private boolean roomChatRestored;
    private int roomChatMemorySyncedCount;

    /** Restore chat from this live session after Activity recreate / minimize return. */
    private void restoreRoomChatIfNeeded() {
        if (roomChatRestored || binding == null || binding.chatLog == null) return;
        if (roomId == null || roomId.isEmpty()) return;
        List<RoomChatMemory.Line> lines = RoomChatMemory.snapshot(roomId);
        if (lines.isEmpty()) {
            roomChatRestored = true;
            roomChatMemorySyncedCount = 0;
            return;
        }
        restoringRoomChat = true;
        try {
            binding.chatLog.removeAllViews();
            for (RoomChatMemory.Line line : lines) {
                appendChatLine(
                        line.name,
                        line.text,
                        line.vipLevel,
                        line.userLevel,
                        line.frameUrl,
                        line.userId,
                        line.avatarUrl,
                        line.giftIconUrl,
                        line.wealthScore,
                        line.charmScore);
            }
        } finally {
            restoringRoomChat = false;
            roomChatRestored = true;
            roomChatMemorySyncedCount = lines.size();
        }
        scrollChatToBottom(false);
    }

    /** Append chat lines buffered by FGS while UI was away (without full rebuild). */
    private void syncRoomChatFromMemory() {
        if (binding == null || binding.chatLog == null || roomId == null || roomId.isEmpty()) return;
        List<RoomChatMemory.Line> lines = RoomChatMemory.snapshot(roomId);
        if (lines.size() <= roomChatMemorySyncedCount) return;
        restoringRoomChat = true;
        try {
            for (int i = roomChatMemorySyncedCount; i < lines.size(); i++) {
                RoomChatMemory.Line line = lines.get(i);
                appendChatLine(
                        line.name,
                        line.text,
                        line.vipLevel,
                        line.userLevel,
                        line.frameUrl,
                        line.userId,
                        line.avatarUrl,
                        line.giftIconUrl,
                        line.wealthScore,
                        line.charmScore);
            }
        } finally {
            restoringRoomChat = false;
            roomChatMemorySyncedCount = lines.size();
            roomChatRestored = true;
        }
    }

    /** Wipe chat only when broadcast ends OR staff clears for everyone. */
    private void clearRoomChatSession() {
        clearRoomChatSession(0L);
    }

    private void clearRoomChatSession(long serverClearedAtMs) {
        if (roomId != null) {
            if (serverClearedAtMs > 0) {
                RoomChatMemory.clear(roomId, serverClearedAtMs);
            } else {
                RoomChatMemory.clear(roomId);
            }
        }
        if (binding != null && binding.chatLog != null) {
            binding.chatLog.removeAllViews();
        }
        roomChatMemorySyncedCount = 0;
        roomChatRestored = true;
    }

    /** Parse server chatClearedAt ISO stamp; apply full local invalidation. */
    private void honorServerChatWipe(@Nullable String iso) {
        long ms = parseIsoMillis(iso);
        if (ms > 0 && roomId != null) {
            RoomChatMemory.honorServerWipe(roomId, ms);
            if (binding != null && binding.chatLog != null) {
                // Always empty UI so wipe is visible even if memory was already stamped.
                binding.chatLog.removeAllViews();
            }
            roomChatMemorySyncedCount = 0;
            roomChatRestored = true;
        }
    }

    private static long parseIsoMillis(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            return java.time.Instant.parse(iso.trim()).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void resetChatWear(
            ImageView avatar, ImageView frameView, com.Dramizo.Series.util.HostSignalView hostSignal) {
        if (avatar != null) {
            avatar.setScaleX(1f);
            avatar.setScaleY(1f);
            avatar.setVisibility(View.VISIBLE);
        }
        if (frameView != null) {
            RoomCardAnimator.stop(frameView);
            frameView.setVisibility(View.GONE);
            frameView.setImageDrawable(null);
        }
        if (hostSignal != null) hostSignal.setVisibility(View.GONE);
    }

    private void scrollChatToBottom(boolean animated) {
        if (binding == null || binding.scrollChat == null || binding.chatLog == null) return;
        binding.scrollChat.post(() -> {
            if (binding == null || binding.scrollChat == null || binding.chatLog == null) return;
            int bottom = Math.max(0, binding.chatLog.getHeight() - binding.scrollChat.getHeight());
            if (animated) {
                binding.scrollChat.smoothScrollTo(0, bottom);
            } else {
                binding.scrollChat.scrollTo(0, bottom);
            }
        });
        // Second pass after layout settles (keyboard / new bubble height).
        binding.scrollChat.postDelayed(() -> {
            if (binding == null || binding.scrollChat == null || binding.chatLog == null) return;
            int bottom = Math.max(0, binding.chatLog.getHeight() - binding.scrollChat.getHeight());
            binding.scrollChat.scrollTo(0, bottom);
        }, 120);
    }

    private void showChatUserActions(
            View anchor,
            String userId,
            String name,
            String avatarUrl,
            String frameUrl,
            int vipLevel,
            int userLevel,
            String messageText,
            View chatRow) {
        if (userId == null || userId.isEmpty()) return;
        boolean anotherUser = myUserId == null || !userId.equals(myUserId);
        RoomDtos.RoomDto liveRoom = viewModel != null ? viewModel.getRoom().getValue() : null;
        String liveHostId = resolveLiveHostId(liveRoom);
        boolean protectedHost = userId.equals(roomHostId)
                || userId.equals(roomCohostId)
                || userId.equals(liveHostId)
                || (liveRoom != null && userId.equals(liveRoom.activeHostId));
        boolean targetModerator = roomModeratorIds.contains(userId);
        boolean canTarget = anotherUser && !protectedHost && (isOwner || !targetModerator);
        RoomDtos.SeatDto targetSeat = null;
        if (currentSeats != null) {
            for (RoomDtos.SeatDto seat : currentSeats) {
                if (seat != null && userId.equals(seatUserId(seat))) {
                    targetSeat = seat;
                    break;
                }
            }
        }
        final RoomDtos.SeatDto seatRef = targetSeat;

        android.widget.PopupMenu menu = new android.widget.PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_AuraLive_PopupMenu),
                anchor);
        int i = 0;
            menu.getMenu().add(0, i++, 0, "الملف الشخصي");
        menu.getMenu().add(0, i++, 0, "بطاقة المستخدم");
        menu.getMenu().add(0, i++, 0, "إرسال هدية");
        if (anotherUser) {
            menu.getMenu().add(0, i++, 0, "@ منشن");
        }
        if (canInviteMic && canTarget && seatRef == null) {
            menu.getMenu().add(0, i++, 0, "دعوة للمايك");
        }
        if (canInviteMic && canTarget && anotherUser && isHost) {
            menu.getMenu().add(0, i++, 0, "دعوة مهمة (+40 ماسة)");
        }
        if (canMuteUsers && canTarget && seatRef != null) {
            menu.getMenu().add(0, i++, 0,
                    (seatRef.isMuted || seatRef.isModeratorMuted) ? "فك كتم المايك" : "كتم المايك");
        }
        if ((canKickUsers || canBanUsers) && canTarget) {
            menu.getMenu().add(0, i++, 0, "طرد / إخراج");
        }
        if (messageText != null && !messageText.trim().isEmpty()) {
            menu.getMenu().add(0, i++, 0, "ترجمة التعليق");
            if (isOwner || canKickUsers) {
                menu.getMenu().add(0, i++, 0, "حذف التعليق");
            }
        }
        if (!anotherUser) {
            // Self: still allow profile + delete own line.
        }
        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (title == null) return false;
            String t = title.toString();
            if ("الملف الشخصي".equals(t)) {
                Intent intent = new Intent(this, com.Dramizo.Series.presentation.profile.ProfileActivity.class);
                intent.putExtra(com.Dramizo.Series.presentation.profile.ProfileActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            } else if ("بطاقة المستخدم".equals(t)) {
                showUserCard(userId, name, avatarUrl, frameUrl, vipLevel, userLevel);
            } else if ("إرسال هدية".equals(t)) {
                if (!anotherUser && !canHostGiftSelf()) {
                    Toast.makeText(this, R.string.cannot_gift_yourself, Toast.LENGTH_SHORT).show();
                } else {
                    binding.getRoot().post(() ->
                            GiftBottomSheet.show(getSupportFragmentManager(), userId, null, roomId));
                }
            } else if ("@ منشن".equals(t)) {
                String mention = "@" + (name != null ? name : "") + " ";
                focusRoomChatComposer(mention, mention.length());
            } else if ("دعوة للمايك".equals(t)) {
                viewModel.inviteSeat(roomId, userId);
            } else if ("دعوة مهمة (+40 ماسة)".equals(t)) {
                viewModel.inviteTaskGuest(roomId, userId);
            } else if ("كتم المايك".equals(t) || "فك كتم المايك".equals(t)) {
                if (seatRef != null && userId != null) {
                    boolean currentlyMuted = seatRef.isMuted || seatRef.isModeratorMuted;
                    viewModel.setMic(roomId, !currentlyMuted, userId);
                }
            } else if ("طرد / إخراج".equals(t)) {
                showKickAndBanDialog(userId);
            } else if ("ترجمة التعليق".equals(t)) {
                translateRoomComment(messageText);
            } else if ("حذف التعليق".equals(t)) {
                if (chatRow != null && binding != null && binding.chatLog != null) {
                    binding.chatLog.removeView(chatRow);
                }
            }
            return true;
        });
        menu.show();
    }

    /** Small seat controls when the user taps their own occupied mic. */
    private void showMySeatMenu(View anchor, RoomDtos.SeatDto seat) {
        if (seat == null || roomId == null) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_AuraLive_PopupMenu),
                anchor != null ? anchor : binding.getRoot());
        // Boss mic (seat 0 / header card) is fixed — host cannot leave it; guest seats can leave.
        boolean bossMic = seat.seatIndex <= 0;
        if (!bossMic) {
            menu.getMenu().add(0, 1, 0, R.string.seat_menu_leave);
        }
        menu.getMenu().add(0, 2, 0, micOn ? R.string.seat_menu_mute : R.string.seat_menu_unmute);
        menu.getMenu().add(0, 3, 0, R.string.seat_menu_profile);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                if (bossMic) {
                    Toast.makeText(this, "المايك الرئيسي ثابت ولا يمكن النزول منه", Toast.LENGTH_SHORT).show();
                    return true;
                }
                viewModel.leaveSeat(roomId);
                return true;
            }
            if (id == 2) {
                binding.btnMic.performClick();
                return true;
            }
            if (id == 3) {
                String name = seat.user != null
                        ? firstNonEmpty(seat.user.displayName, seat.user.username, "أنا")
                        : "أنا";
                showUserCard(
                        myUserId,
                        name,
                        seat.user != null ? seat.user.avatarUrl : null,
                        seat.user != null ? seat.user.vipBadgeUrl : null,
                        isAgencyRoom ? (seat.user != null ? seat.user.hostBadgeUrl : null) : null,
                        seat.user != null ? Math.max(0, seat.user.vipLevel) : 0,
                        seat.user != null ? Math.max(1, seat.user.level) : 1);
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** Compatibility for system lines. */
    private void appendChatLine(String line) {
        appendChatLine(getString(R.string.official_news), line, 0, 0, null);
    }

    /** Open room side-drawer (null when closed). */
    @Nullable private Dialog roomSidePanelDialog;

    /**
     * Mikoo end-live side panel: More / Settings / Minimize / Exit
     * + tabs يكتشف / تاريخ + live rooms.
     */
    private void confirmExit() {
        showRoomSidePanel();
    }

    private void showRoomSidePanel() {
        if (isFinishing() || isDestroyed()) return;
        // Second back / re-tap closes if already open.
        if (roomSidePanelDialog != null && roomSidePanelDialog.isShowing()) {
            roomSidePanelDialog.dismiss();
            return;
        }
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_room_side_panel, null);
        dialog.setContentView(sheet);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        roomSidePanelDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (roomSidePanelDialog == d) roomSidePanelDialog = null;
        });

        final View scrim = sheet.findViewById(R.id.sidePanelScrim);
        final View body = sheet.findViewById(R.id.sidePanelBody);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int panelWRaw = Math.round(dm.widthPixels * 0.78f);
        final int panelW = Math.max(Math.round(260f * dm.density),
                Math.min(panelWRaw, Math.round(360f * dm.density)));
        if (body != null) {
            // Force PHYSICAL right edge (Mikoo). layoutDirection rtl flips "end" → left.
            body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            FrameLayout.LayoutParams blp;
            if (body.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                blp = (FrameLayout.LayoutParams) body.getLayoutParams();
            } else {
                blp = new FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            blp.width = panelW;
            blp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            blp.gravity = Gravity.RIGHT | Gravity.TOP;
            body.setLayoutParams(blp);

            // Stay under status bar — don't eat the system clock/battery.
            ViewCompat.setOnApplyWindowInsetsListener(body, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                int bot = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bot);
                return insets;
            });
            ViewCompat.requestApplyInsets(body);
            body.setOnClickListener(v -> { /* consume */ });
            // Slide in from off-screen RIGHT.
            body.setTranslationX(panelW);
            body.post(() -> body.animate()
                    .translationX(0f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .start());
        }

        // Soft dismiss: slide back out to the right, then close.
        final Runnable dismissRight = () -> {
            if (body == null) {
                dialog.dismiss();
                return;
            }
            int w = body.getWidth() > 0 ? body.getWidth() : panelW;
            body.animate()
                    .translationX(w)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                    })
                    .start();
        };
        if (scrim != null) scrim.setOnClickListener(v -> dismissRight.run());
        // System / gesture back closes this sheet first (don't open another).
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                dismissRight.run();
                return true;
            }
            return false;
        });

        View btnMore = sheet.findViewById(R.id.btnSideMore);
        View btnSettings = sheet.findViewById(R.id.btnSideSettings);
        View btnMin = sheet.findViewById(R.id.btnSideMinimize);
        View btnExit = sheet.findViewById(R.id.btnSideExit);
        View rowHostActions = sheet.findViewById(R.id.rowSideHostActions);
        View btnEndLive = sheet.findViewById(R.id.btnSideEndLive);
        View btnSummon = sheet.findViewById(R.id.btnSideSummon);
        View tabDiscover = sheet.findViewById(R.id.tabDiscover);
        View tabHistory = sheet.findViewById(R.id.tabHistory);
        TextView tvDiscover = sheet.findViewById(R.id.tvTabDiscover);
        TextView tvHistory = sheet.findViewById(R.id.tvTabHistory);
        View lineDiscover = sheet.findViewById(R.id.lineTabDiscover);
        View lineHistory = sheet.findViewById(R.id.lineTabHistory);
        RecyclerView rv = sheet.findViewById(R.id.rvDiscoverRooms);

        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                dialog.dismiss();
                showOtherTools();
            });
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                dialog.dismiss();
                if (canModerateRoom() || isHost || isOwner || canManageRoom) {
                    showHostTools();
                } else {
                    showOtherTools();
                }
            });
        }
        if (btnMin != null) {
            btnMin.setOnClickListener(v -> {
                dialog.dismiss();
                keepRoomInBackground();
            });
        }
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                dialog.dismiss();
                exitRoom(true);
            });
        }

        // Host / owner: end live + summon (restored on side panel).
        boolean hostActions = isHost || isOwner || canManageRoom;
        if (rowHostActions != null) {
            rowHostActions.setVisibility(hostActions ? View.VISIBLE : View.GONE);
        }
        if (btnEndLive != null) {
            btnEndLive.setOnClickListener(v -> {
                dialog.dismiss();
                confirmEndBroadcast();
            });
        }
        if (btnSummon != null) {
            btnSummon.setOnClickListener(v -> {
                dialog.dismiss();
                summonRoomMembers();
            });
        }

        final DiscoverRoomAdapter adapter = new DiscoverRoomAdapter(targetId -> {
            if (targetId == null || targetId.isEmpty()) return;
            if (roomId != null && roomId.equals(targetId)) {
                dialog.dismiss();
                return;
            }
            dialog.dismiss();
            switchRoomInPlace(targetId, null);
        });
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(adapter);
        }

        final Runnable showDiscover = () -> {
            if (tvDiscover != null) tvDiscover.setTextColor(0xFFFFFFFF);
            if (tvHistory != null) tvHistory.setTextColor(0x99FFFFFF);
            if (lineDiscover != null) lineDiscover.setVisibility(View.VISIBLE);
            if (lineHistory != null) lineHistory.setVisibility(View.INVISIBLE);
            loadDiscoverRoomsForSidePanel(adapter, false);
        };
        final Runnable showHistory = () -> {
            if (tvDiscover != null) tvDiscover.setTextColor(0x99FFFFFF);
            if (tvHistory != null) tvHistory.setTextColor(0xFFFFFFFF);
            if (lineDiscover != null) lineDiscover.setVisibility(View.INVISIBLE);
            if (lineHistory != null) lineHistory.setVisibility(View.VISIBLE);
            loadDiscoverRoomsForSidePanel(adapter, true);
        };
        if (tabDiscover != null) tabDiscover.setOnClickListener(v -> showDiscover.run());
        if (tabHistory != null) tabHistory.setOnClickListener(v -> showHistory.run());
        showDiscover.run();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Draw under system bars so only panel body applies status padding (room stays full-bleed).
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            // Absolute gravity — never flip with RTL locale.
            window.setGravity(Gravity.TOP | Gravity.RIGHT);
            try {
                WindowManager.LayoutParams wlp = window.getAttributes();
                wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
                wlp.height = WindowManager.LayoutParams.MATCH_PARENT;
                wlp.gravity = Gravity.TOP | Gravity.RIGHT;
                window.setAttributes(wlp);
            } catch (Exception ignored) {
            }
        }
        dialog.show();
    }

    private void loadDiscoverRoomsForSidePanel(
            @NonNull DiscoverRoomAdapter adapter, boolean historyTab) {
        if (historyTab) {
            List<RoomDtos.RoomDto> recent =
                    com.Dramizo.Series.util.RecentRoomsStore.list(this);
            List<RoomDtos.RoomDto> rooms = new ArrayList<>();
            for (RoomDtos.RoomDto room : recent) {
                if (room == null || room.id == null || room.id.isEmpty()) continue;
                if (roomId != null && roomId.equals(room.id)) continue;
                rooms.add(room);
            }
            adapter.submit(rooms);
            return;
        }
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.ListResult<RoomDtos.RoomDto>> r =
                    ContainerProvider.from(this).getRoomRepository().list(1, 30);
            List<RoomDtos.RoomDto> rooms = new ArrayList<>();
            if (r != null && r.success && r.data != null && r.data.items != null) {
                for (RoomDtos.RoomDto room : r.data.items) {
                    if (room == null || room.id == null || room.id.isEmpty()) continue;
                    if (roomId != null && roomId.equals(room.id)) continue;
                    rooms.add(room);
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                adapter.submit(rooms);
            });
        });
    }

    /** Lightweight discover / history rows for the room side panel. */
    private static final class DiscoverRoomAdapter
            extends RecyclerView.Adapter<DiscoverRoomAdapter.VH> {
        interface Listener {
            void onOpen(@Nullable String roomId);
        }

        private final List<RoomDtos.RoomDto> items = new ArrayList<>();
        private final Listener listener;

        DiscoverRoomAdapter(Listener listener) {
            this.listener = listener;
        }

        void submit(@Nullable List<RoomDtos.RoomDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room_discover_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RoomDtos.RoomDto room = items.get(position);
            String title = room.title != null && !room.title.isEmpty()
                    ? room.title : "غرفة";
            h.tvTitle.setText(title);
            String sub = room.description != null && !room.description.trim().isEmpty()
                    ? room.description.trim()
                    : h.itemView.getContext().getString(R.string.room_default_welcome);
            if (h.tvSub != null) {
                h.tvSub.setText(sub);
                h.tvSub.setVisibility(View.VISIBLE);
            }
            if (h.tvViewers != null) {
                h.tvViewers.setText(String.valueOf(Math.max(0, room.viewerCount)));
            }
            String cover = room.coverUrl != null && !room.coverUrl.isEmpty()
                    ? room.coverUrl
                    : room.roomCardUrl;
            if (h.imgCover != null) {
                try {
                    Glide.with(h.imgCover)
                            .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(cover))
                            .centerCrop()
                            .placeholder(R.drawable.hams_background)
                            .into(h.imgCover);
                } catch (Exception ignored) {
                }
            }
            String country = room.host != null ? room.host.country : null;
            if (h.imgFlag != null) {
                com.Dramizo.Series.util.FlagImages.bind(h.imgFlag, country);
                if (h.imgFlag.getDrawable() != null) {
                    h.imgFlag.setVisibility(View.VISIBLE);
                }
            }
            if (h.rowAvatars != null) {
                h.rowAvatars.removeAllViews();
                List<String> avs = room.viewerAvatars;
                int shown = 0;
                if (avs != null) {
                    for (String a : avs) {
                        if (a == null || a.isEmpty()) continue;
                        if (shown >= 4) break;
                        ImageView iv = new ImageView(h.itemView.getContext());
                        int s = Math.round(18f * h.itemView.getResources().getDisplayMetrics().density);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
                        if (shown > 0) {
                            lp.setMarginStart(Math.round(
                                    -4f * h.itemView.getResources().getDisplayMetrics().density));
                        }
                        iv.setLayoutParams(lp);
                        AvatarImageLoader.applyCircularClip(iv);
                        AvatarImageLoader.load(iv, a);
                        h.rowAvatars.addView(iv);
                        shown++;
                    }
                }
            }
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onOpen(room.id);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final ImageView imgCover;
            final ImageView imgFlag;
            final TextView tvTitle;
            final TextView tvSub;
            final TextView tvViewers;
            final LinearLayout rowAvatars;

            VH(View itemView) {
                super(itemView);
                imgCover = itemView.findViewById(R.id.imgCover);
                imgFlag = itemView.findViewById(R.id.imgFlag);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSub = itemView.findViewById(R.id.tvSub);
                tvViewers = itemView.findViewById(R.id.tvViewers);
                rowAvatars = itemView.findViewById(R.id.rowAvatars);
            }
        }
    }

    private void endBroadcastAndExit() {
        if (exiting || roomId == null) return;
        clearRoomChatSession();
        viewModel.closeRoom(roomId);
        exitRoom(true);
    }

    /** Confirm before host ends broadcast and closes the room for everyone. */
    private void confirmEndBroadcast() {
        if (!(isHost || isOwner || canManageRoom)) {
            Toast.makeText(this, "إنهاء البث لصاحب الغرفة فقط", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.room_side_end_live)
                .setMessage("هل تريد إنهاء البث وإغلاق الغرفة للجميع؟")
                .setPositiveButton(R.string.room_side_end_live, (d, w) -> endBroadcastAndExit())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Room owner/host calls everyone currently in the room (incl. minimized) back. */
    private void summonRoomMembers() {
        if (roomId == null || roomId.isEmpty()) return;
        if (!(isHost || isOwner)) {
            Toast.makeText(this, "الاستدعاء لصاحب الغرفة فقط", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = RealtimeClient.getInstance().emitRoomEvent(roomId, "room:summon", new JsonObject());
        Toast.makeText(this,
                ok ? "تم إرسال الاستدعاء للجميع في الروم" : "تعذر إرسال الاستدعاء",
                Toast.LENGTH_SHORT).show();
        appendChatLine("النظام", "تم استدعاء الموجودين في الغرفة", 0, 1);
    }

    /**
     * Mikoo-style minimize: mark minimize → keep FGS/Zego/realtime → close UI only.
     * Music must hand off to FGS before Activity Exo dies (no cutouts).
     */
    private void keepRoomInBackground() {
        if (roomId == null || roomId.isEmpty() || minimizing || exiting || isFinishing()) return;
        captureActiveRoomSession();
        ActiveRoomSession.get().setMinimized(true);
        ensuringRoomKeepAlive(true);
        ensureMinimizedListeningState();
        // Handoff: fade Exo to FGS, keep Zego local music alive, then leave UI.
        try {
            if (roomMusicPlayer != null
                    && "playing".equalsIgnoreCase(currentMusicStatus)
                    && currentMusicUrl != null
                    && !currentMusicUrl.isEmpty()
                    && !isLocalMusicUrl(currentMusicUrl)) {
                // Avoid dual play: FGS will take over; dip volume briefly then finish.
                roomMusicPlayer.setVolume(0.35f);
            }
        } catch (Exception ignored) {
        }
        try {
            if (canManageMusic
                    && currentMusicUrl != null
                    && !currentMusicUrl.isEmpty()
                    && "playing".equalsIgnoreCase(currentMusicStatus)
                    && isLocalMusicUrl(currentMusicUrl)) {
                ensurePublishingForMusic();
                RoomRtcEngine.getInstance().boostMusicMixVolume();
                if (!RoomRtcEngine.getInstance().isLocalMusicPlaying()
                        && RoomRtcEngine.getInstance().hasLocalMusicPlayer()) {
                    RoomRtcEngine.getInstance().resumeLocalMusic();
                }
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(getApplicationContext(), R.string.room_minimized_audio_continues,
                Toast.LENGTH_SHORT).show();
        // Small delay so FGS can start mediaPlayback before Activity releases Exo.
        handler.postDelayed(() -> {
            if (isFinishing() || exiting) return;
            navigateHomeAndFinish();
        }, 280L);
    }

    /**
     * While UI is gone but room session stays: hear the room, own mic off.
     * Never call setSpeakerMuted(true) here — that stops remote play streams.
     * Never stop YouTube/room music here — FGS continues playback.
     */
    private void ensureMinimizedListeningState() {
        try {
            mutedForBackground = false;
            roomSpeakerMuted = false;
            speakerMutedBeforeBackground = false;
            RoomSoundFx.setMuted(false);
            RoomRtcEngine.getInstance().setSpeakerMuted(false);
            // Mute own mic while minimized (notification / mini player can unmute).
            micOn = false;
            RoomRtcEngine.getInstance().setMicEnabled(false);
            if (roomId != null && !roomId.isEmpty()) {
                final String rid = roomId;
                ContainerProvider.from(this).getIoExecutor().execute(() ->
                        ContainerProvider.from(this).getRoomRepository().setMic(rid, true));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Keep Zego + realtime alive when UI goes away (WhatsApp, Home, DM chat, etc.).
     * Without this, onDestroy leaves the room and looks like "room closed".
     */
    private void ensuringRoomKeepAlive(boolean markMinimizing) {
        if (roomId == null || roomId.isEmpty() || exiting) return;
        if (markMinimizing) {
            minimizing = true;
            captureActiveRoomSession();
            ActiveRoomSession.get().setMinimized(true);
        }
        long musicPosition = 0L;
        if (isLocalMusicUrl(currentMusicUrl) || isYoutubeMusicUrl(currentMusicUrl)) {
            musicPosition = isLocalMusicUrl(currentMusicUrl)
                    ? RoomRtcEngine.getInstance().getLocalMusicPositionMs()
                    : (roomMusicPlayer != null ? roomMusicPlayer.getCurrentPosition() : 0L);
        } else if (roomMusicPlayer != null) {
            musicPosition = roomMusicPlayer.getCurrentPosition();
        }
        CharSequence roomTitle = binding != null && binding.tvRoomTitle != null
                ? binding.tvRoomTitle.getText() : null;
        try {
            ContextCompat.startForegroundService(
                    this,
                    VoiceRoomForegroundService.startIntent(
                            this,
                            roomId,
                            roomTitle != null ? roomTitle.toString() : "الغرفة الصوتية",
                            currentRoomCoverUrl,
                            currentMusicUrl,
                            currentMusicStatus,
                            musicPosition,
                            currentMusicStartedAt));
        } catch (RuntimeException ignored) {
        }
    }

    /** Open another screen without leaving the voice room. */
    private void startActivityKeepingRoom(Intent intent) {
        ensuringRoomKeepAlive(true);
        startActivity(intent);
    }

    /** Follow heart on user card: solid when following, faded when not. */
    private static void applyFollowHeart(@Nullable ImageView heart, boolean following) {
        if (heart == null) return;
        if (following) {
            heart.setImageResource(R.drawable.icon_attentioned);
            heart.setAlpha(1f);
        } else {
            heart.setImageResource(R.drawable.icon_attention);
            heart.setAlpha(0.42f);
        }
    }

    private void openRoomSharePicker() {
        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        String title = binding != null && binding.tvRoomTitle.getText() != null
                ? binding.tvRoomTitle.getText().toString()
                : getString(R.string.voice_room);
        ShareRoomBottomSheet.show(getSupportFragmentManager(), roomId, title, currentRoomCoverUrl);
    }

    private void showOtherTools() {
        // Mikoo RoomMoreOperatorDialog — layouts/icons from Mikoo APK
        RoomMoreOperatorSheet.show(new RoomMoreOperatorSheet.Host() {
            @Override public android.content.Context context() { return VoiceRoomActivity.this; }
            @Override public boolean isStaff() {
                return isRoomStaff() || canModerateRoom() || isHost || isOwner;
            }
            @Override public boolean isOwner() { return isOwner; }
            @Override public boolean roomLocked() { return locked; }
            @Override public boolean giftSoundsOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.giftSoundsEnabled;
            }
            @Override public boolean entryEffectsOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.entryEffectsEnabled;
            }
            @Override public boolean lowGiftEffectsOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.lowGiftEffectsEnabled;
            }
            @Override public boolean charmOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.charmEnabled;
            }
            @Override public boolean chatZoneOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.chatZoneEnabled;
            }
            @Override public boolean micInteractOn() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                return room == null || room.micInteractEnabled;
            }
            @Override public boolean roomSpeakerMuted() { return roomSpeakerMuted; }
            @Override public boolean canControlMusic() {
                return canManageMusic || isHost || isOwner || isRoomStaff();
            }
            @Override public boolean canAdjustSeatCount() {
                return canManageRoom || isHost || isOwner;
            }
            @Override public boolean canReviewSeatRequests() {
                return canInviteMic && !isFreeMicEnabled();
            }
            @Override public int chatAutoClearMinutes() {
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                if (room == null) return 0;
                int m = room.chatAutoClearMinutes;
                return (m == 1 || m == 5 || m == 10) ? m : 0;
            }
            @Override public String roomId() { return roomId; }
            @Override public String roomTitle() {
                return binding != null && binding.tvRoomTitle.getText() != null
                        ? binding.tvRoomTitle.getText().toString() : null;
            }
            @Override public void onMoreAction(@NonNull String action) {
                handleRoomMoreAction(action);
            }
        });
    }

    private void handleRoomMoreAction(@NonNull String action) {
        switch (action) {
            case "settings":
                showHostTools();
                break;
            case "seat_count":
                if (canManageRoom || isHost || isOwner) showSeatCountSheet();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "seat_requests":
                if (canInviteMic && !isFreeMicEnabled()) showSeatRequestsDialog();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "lock":
                if (isAgencyRoom) {
                    Toast.makeText(this, "غرف الوكالة عامة ولا تُقفل بكلمة مرور", Toast.LENGTH_SHORT).show();
                    break;
                }
                if (!canManageRoom) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                if (!locked) {
                    promptRoomPassword(pwd -> {
                        viewModel.lockRoom(roomId, true, pwd);
                        Toast.makeText(this, R.string.room_locked_with_password, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    viewModel.lockRoom(roomId, false, null);
                    Toast.makeText(this, R.string.room_unlocked, Toast.LENGTH_SHORT).show();
                }
                break;
            case "mic_mode":
                if (canManageSeats || canManageRoom || isHost || isOwner) showSeatLockManager();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "theme":
                if (canChangeFrames || canManageRoom || isHost || isOwner) showRoomBackgroundPicker();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "chat_zone": {
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto r = viewModel.getRoom().getValue();
                boolean next = !(r == null || r.chatZoneEnabled);
                applyChatZone(next);
                if (r != null) r.chatZoneEnabled = next;
                patchDisplaySetting("chatZoneEnabled", next);
                Toast.makeText(this, withOnOff(R.string.room_more_chat_zone, next), Toast.LENGTH_SHORT).show();
                break;
            }
            case "clear_chat":
                confirmClearRoomChat();
                break;
            case "auto_clear_chat":
                cycleChatAutoClear();
                break;
            case "charm": {
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto r = viewModel.getRoom().getValue();
                boolean next = !(r == null || r.charmEnabled);
                if (r != null) r.charmEnabled = next;
                patchDisplaySetting("charmEnabled", next);
                Toast.makeText(this, withOnOff(R.string.room_more_charm, next), Toast.LENGTH_SHORT).show();
                break;
            }
            case "gift_sound":
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                boolean enabled = room == null || room.giftSoundsEnabled;
                boolean nextGift = !enabled;
                applyRoomGiftSounds(nextGift);
                if (room != null) room.giftSoundsEnabled = nextGift;
                viewModel.setGiftSounds(roomId, nextGift);
                Toast.makeText(this,
                        nextGift ? R.string.room_gift_sounds_on : R.string.room_gift_sounds_off,
                        Toast.LENGTH_SHORT).show();
                break;
            case "music": {
                if (!(canManageMusic || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, "التحكم بالموسيقى للمضيف والمشرف فقط", Toast.LENGTH_SHORT).show();
                    break;
                }
                // Open floating player + library (YouTube / device).
                showMusicPanelOrPicker();
                showSavedMusicLibrary();
                break;
            }
            case "blacklist":
                showRoomBlacklistSheet();
                break;
            case "admin":
                if (isOwner || isHost) showModeratorTools();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "mic_interact": {
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto r = viewModel.getRoom().getValue();
                boolean next = !(r == null || r.micInteractEnabled);
                if (r != null) r.micInteractEnabled = next;
                patchDisplaySetting("micInteractEnabled", next);
                Toast.makeText(this, withOnOff(R.string.room_more_mic_interact, next), Toast.LENGTH_SHORT).show();
                break;
            }
            case "photo":
                if (canChangeFrames || canManageRoom || isHost || isOwner) showRoomBackgroundPicker();
                else Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                break;
            case "mute":
                toggleRoomSpeakerMute();
                break;
            case "invite":
                openRoomSharePicker();
                break;
            case "copy_link":
                RoomMoreOperatorSheet.copyRoomLink(this, roomId);
                break;
            case "whatsapp": {
                String title = binding != null && binding.tvRoomTitle.getText() != null
                        ? binding.tvRoomTitle.getText().toString() : null;
                RoomMoreOperatorSheet.shareWhatsApp(this, roomId, title);
                break;
            }
            case "entry_effect": {
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto r = viewModel.getRoom().getValue();
                boolean next = !(r == null || r.entryEffectsEnabled);
                if (r != null) r.entryEffectsEnabled = next;
                patchDisplaySetting("entryEffectsEnabled", next);
                Toast.makeText(this,
                        next ? R.string.room_more_entry_effect_on : R.string.room_more_entry_effect_off,
                        Toast.LENGTH_SHORT).show();
                break;
            }
            case "low_gift": {
                if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
                    Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
                    break;
                }
                RoomDtos.RoomDto r = viewModel.getRoom().getValue();
                boolean next = !(r == null || r.lowGiftEffectsEnabled);
                if (r != null) r.lowGiftEffectsEnabled = next;
                patchDisplaySetting("lowGiftEffectsEnabled", next);
                Toast.makeText(this, withOnOff(R.string.room_more_low_gift, next), Toast.LENGTH_SHORT).show();
                break;
            }
            default:
                break;
        }
    }

    private String withOnOff(@androidx.annotation.StringRes int label, boolean on) {
        return getString(label) + " "
                + getString(on ? R.string.room_more_str_on : R.string.room_more_str_off);
    }

    private void patchDisplaySetting(@NonNull String key, boolean enabled) {
        if (roomId == null || roomId.isEmpty()) return;
        java.util.Map<String, Boolean> patch = new java.util.HashMap<>();
        patch.put(key, enabled);
        viewModel.setDisplaySettings(roomId, patch);
    }

    private void applyRoomDisplaySettings(@Nullable RoomDtos.RoomDto room) {
        if (room == null) return;
        applyChatZone(room.chatZoneEnabled);
        // Room promo banner permanently removed.
        applyRoomBanner(false);
    }

    private void applyChatZone(boolean enabled) {
        if (binding == null) return;
        // "منطقة الدردشة" = أيقونة الرسائل الخاصة (btnPrivateMsg) فقط —
        // لا تخفي بث الشات العام ولا صندوق "اكتب…".
        roomChatZoneVisible = enabled;
        int vis = enabled ? View.VISIBLE : View.GONE;
        if (binding.btnPrivateMsg != null) {
            binding.btnPrivateMsg.setVisibility(vis);
            binding.btnPrivateMsg.setEnabled(enabled);
            binding.btnPrivateMsg.setClickable(enabled);
        }
        // Ensure public room chat UI stays available.
        if (binding.chatPanel != null && binding.chatPanel.getVisibility() != View.VISIBLE) {
            binding.chatPanel.setVisibility(View.VISIBLE);
        }
        if (binding.scrollChat != null) binding.scrollChat.setVisibility(View.VISIBLE);
        if (binding.chatLog != null) binding.chatLog.setVisibility(View.VISIBLE);
        if (binding.rowOfficialNews != null) binding.rowOfficialNews.setVisibility(View.VISIBLE);
        if (binding.tvChatInputTips != null && !roomComposerOpen) {
            binding.tvChatInputTips.setVisibility(View.VISIBLE);
            binding.tvChatInputTips.setEnabled(true);
            binding.tvChatInputTips.setClickable(true);
        }
    }

    /** Open DM conversation without leaving the voice room (FGS keep-alive). */
    public void openPrivateConversation(
            @Nullable String conversationId,
            @Nullable String peerId,
            @Nullable String title,
            @Nullable String avatarUrl
    ) {
        if (conversationId == null || conversationId.isEmpty()) return;
        Intent i = new Intent(this, com.Dramizo.Series.presentation.chat.ChatConversationActivity.class);
        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_CONVERSATION_ID,
                conversationId);
        if (peerId != null) {
            i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_PEER_ID, peerId);
        }
        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_TITLE,
                title != null && !title.isEmpty() ? title : "محادثة");
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_AVATAR, avatarUrl);
        }
        startActivityKeepingRoom(i);
    }

    private void applyRoomBanner(boolean enabled) {
        if (binding == null || binding.roomBanner == null) return;
        // Feature removed — never show the strip.
        binding.roomBanner.setVisibility(View.GONE);
    }

    /** Celebrations always allowed in chat (banner feature retired). */
    private boolean roomBannerEnabled() {
        return true;
    }

    /** Highlight occupied seats matching gift recipients while the gift sheet is open. */
    public void setGiftSeatSelection(@Nullable java.util.Collection<String> userIds) {
        if (seatAdapter == null) return;
        java.util.HashSet<String> set = new java.util.HashSet<>();
        if (userIds != null) {
            for (String id : userIds) {
                if (id != null && !id.isEmpty()) set.add(id);
            }
        }
        seatAdapter.setGiftSelectedUsers(set);
    }

    public void clearGiftSeatSelection() {
        if (seatAdapter != null) seatAdapter.clearGiftSelection();
    }

    private boolean roomCharmEnabled() {
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        return room == null || room.charmEnabled;
    }

    private boolean roomMicInteractEnabled() {
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        return room == null || room.micInteractEnabled;
    }

    private boolean roomEntryEffectsEnabled() {
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        return room == null || room.entryEffectsEnabled;
    }

    private boolean roomLowGiftEffectsEnabled() {
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        return room == null || room.lowGiftEffectsEnabled;
    }

    private boolean areCelebrationPopupsMuted() {
        try {
            return ContainerProvider.from(this).getSessionManager().isMuteCelebrationPopups();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showRoomBlacklistSheet() {
        if (roomId == null || roomId.isEmpty()) return;
        if (!(canBanUsers || canModerateRoom() || isHost || isOwner || isRoomStaff() || canManageRoom)) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this, R.style.MikooBottomPanelDialog);
        View root = getLayoutInflater().inflate(R.layout.dialog_room_blacklist, null, false);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.dimAmount = 0.45f;
            window.setAttributes(lp);
            window.setNavigationBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.color_gift_dialog_bg));
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
        }
        TextView tvCount = root.findViewById(R.id.tvBlacklistCount);
        ProgressBar progress = root.findViewById(R.id.progressBlacklist);
        TextView tvEmpty = root.findViewById(R.id.tvBlacklistEmpty);
        RecyclerView recycler = root.findViewById(R.id.recyclerBlacklist);
        if (recycler != null) {
            recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        }
        dialog.show();

        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomBanListResult> r =
                    ApiCall.execute(c.getRoomApi().listBans(roomId));
            runOnUiThread(() -> {
                if (isFinishing() || !dialog.isShowing()) return;
                if (progress != null) progress.setVisibility(View.GONE);
                if (!r.success) {
                    if (tvEmpty != null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(r.error != null ? r.error : getString(R.string.error_generic));
                    }
                    return;
                }
                java.util.List<RoomDtos.RoomBanDto> items =
                        r.data != null && r.data.items != null
                                ? r.data.items
                                : java.util.Collections.emptyList();
                if (items.isEmpty()) {
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                if (tvCount != null) {
                    tvCount.setVisibility(View.VISIBLE);
                    tvCount.setText("العقوبات: " + items.size());
                }
                if (recycler == null) return;
                recycler.setVisibility(View.VISIBLE);
                recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(
                            @NonNull ViewGroup parent, int viewType) {
                        View row = getLayoutInflater().inflate(
                                R.layout.item_room_blacklist, parent, false);
                        return new RecyclerView.ViewHolder(row) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        RoomDtos.RoomBanDto ban = items.get(position);
                        if (ban == null) return;
                        ImageView avatar = holder.itemView.findViewById(R.id.imgAvatar);
                        TextView nameTv = holder.itemView.findViewById(R.id.tvName);
                        TextView metaTv = holder.itemView.findViewById(R.id.tvMeta);
                        TextView unbanBtn = holder.itemView.findViewById(R.id.btnUnban);
                        String name = ban.user != null
                                ? (ban.user.displayName != null ? ban.user.displayName : ban.user.username)
                                : ban.userId;
                        if (name == null || name.isEmpty()) name = "مستخدم";
                        String pid = ban.user != null ? ban.user.displayPublicId() : "";
                        String meta = pid != null && !pid.isEmpty() ? ("ID " + pid) : "";
                        boolean isChatMute = ban.kind != null
                                && "chat_mute".equalsIgnoreCase(ban.kind);
                        if (isChatMute) {
                            meta = meta.isEmpty() ? "كتم دردشة" : (meta + " · كتم دردشة");
                        }
                        if (ban.reason != null && !ban.reason.isEmpty()
                                && !"moderator_timed_ban".equals(ban.reason)
                                && !"moderator_kick".equals(ban.reason)) {
                            meta = meta.isEmpty() ? ban.reason : (meta + " · " + ban.reason);
                        }
                        if (nameTv != null) nameTv.setText(name);
                        if (metaTv != null) {
                            metaTv.setText(meta);
                            metaTv.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
                        }
                        if (avatar != null) {
                            String url = ban.user != null ? ban.user.avatarUrl : null;
                            try {
                                Glide.with(avatar)
                                        .load(url)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .error(R.drawable.ic_default_avatar)
                                        .circleCrop()
                                        .into(avatar);
                            } catch (Exception ignored) {
                                avatar.setImageResource(R.drawable.ic_default_avatar);
                            }
                        }
                        final String targetId = ban.userId;
                        final String displayName = name;
                        final boolean muteOnly = isChatMute;
                        if (unbanBtn != null) {
                            unbanBtn.setText(muteOnly ? "فك الكتم" : "إزالة");
                            unbanBtn.setOnClickListener(v -> AuraDialogHelper.confirm(VoiceRoomActivity.this,
                                    muteOnly ? "فك كتم الدردشة" : "إزالة الحظر",
                                    (muteOnly ? "فك كتم " : "إلغاء حظر ") + displayName + "؟",
                                    muteOnly ? "فك الكتم" : "إزالة",
                                    () -> {
                                        viewModel.unbanUser(roomId, targetId);
                                        dialog.dismiss();
                                    },
                                    getString(android.R.string.cancel),
                                    null));
                        }
                    }

                    @Override
                    public int getItemCount() {
                        return items.size();
                    }
                });
            });
        });
    }

    private void confirmClearRoomChat() {
        if (!(canManageRoom || canModerateRoom() || isHost || isOwner)) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        AuraDialogHelper.confirm(this,
                getString(R.string.clear_room_chat),
                getString(R.string.clear_room_chat_confirm),
                getString(R.string.clear_room_chat_short),
                this::clearRoomChatForEveryone,
                getString(android.R.string.cancel),
                null);
    }

    /** Staff: full wipe — API (DB stamp) + socket broadcast + every phone. */
    private void clearRoomChatForEveryone() {
        if (roomId == null || roomId.isEmpty()) return;
        if (!(canManageRoom || canModerateRoom() || isHost || isOwner)) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        // Local wipe immediately so the cleaner sees an empty screen.
        clearLocalRoomChat();
        // Persist stamp on server + fan-out to all clients (phone + memory).
        viewModel.clearPublicChat(roomId);
        // Socket fallback for older clients / offline peers that miss HTTP fan-out.
        RealtimeClient.getInstance().emitRoomEvent(
                roomId, "room:chat_cleared", new JsonObject());
        Toast.makeText(this, R.string.clear_room_chat_done, Toast.LENGTH_SHORT).show();
    }

    /** Cycle auto-clean: off → 1m → 5m → 10m → off (server-enforced). */
    private void cycleChatAutoClear() {
        if (!(canManageRoom || isHost || isOwner || isRoomStaff())) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        RoomDtos.RoomDto r = viewModel.getRoom().getValue();
        int cur = r != null ? r.chatAutoClearMinutes : 0;
        int next;
        if (cur == 1) next = 5;
        else if (cur == 5) next = 10;
        else if (cur == 10) next = 0;
        else next = 1;
        if (r != null) r.chatAutoClearMinutes = next;
        viewModel.setChatAutoClearMinutes(roomId, next);
        String label;
        if (next == 1) label = getString(R.string.room_auto_clear_1m);
        else if (next == 5) label = getString(R.string.room_auto_clear_5m);
        else if (next == 10) label = getString(R.string.room_auto_clear_10m);
        else label = getString(R.string.room_more_str_off);
        Toast.makeText(this, getString(R.string.room_auto_clear_set, label), Toast.LENGTH_SHORT).show();
    }

    private void clearLocalRoomChat() {
        clearRoomChatSession(0L);
    }

    private void setupMusicUi() {
        if (roomMusicPlayer != null) return;
        roomMusicPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(
                                new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                        .setUserAgent(YoutubeAudioResolver.PLAYER_USER_AGENT)
                                        .setAllowCrossProtocolRedirects(true)
                                        .setConnectTimeoutMs(12_000)
                                        .setReadTimeoutMs(20_000)
                                        .setDefaultRequestProperties(java.util.Map.of(
                                                "Referer", "https://www.youtube.com/",
                                                "Origin", "https://www.youtube.com"))))
                .build();
        RoomRtcEngine.getInstance().setLocalMusicEndListener(() -> {
            if (isFinishing()) return;
            runOnUiThread(() -> {
                if (!canManageMusic
                        || musicEndReported
                        || roomId == null
                        || !"playing".equalsIgnoreCase(currentMusicStatus)) {
                    return;
                }
                musicEndReported = true;
                skipMusicTrack();
            });
        });
        roomMusicPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED
                        && canManageMusic
                        && !musicEndReported
                        && !minimizing
                        && !exiting
                        && !isFinishing()
                        && roomId != null
                        && "playing".equalsIgnoreCase(currentMusicStatus)) {
                    musicEndReported = true;
                    // Auto-advance playlist instead of stopping the room music.
                    skipMusicTrack();
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                android.util.Log.w("VoiceRoomMusic",
                        "exo error: " + (error != null ? error.getMessage() : "null"), error);
                if (isYoutubeMusicUrl(currentMusicUrl)) {
                    String vid = youtubeIdFromUrl(currentMusicUrl);
                    if (vid != null && !youtubeUsingEmbed) {
                        // Progressive URL expired / blocked — fall back to embed video in disc.
                        playYoutubeEmbedInDisc(vid, true, roomMusicPlayer != null
                                ? roomMusicPlayer.getCurrentPosition() : 0L);
                        return;
                    }
                    Toast.makeText(VoiceRoomActivity.this,
                            "تعذر تشغيل الفيديو. جرّب أغنية أخرى",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                showMusicVideoSurface(true);
            }
        });
        if (binding.musicVideoSurface != null) {
            musicVideoSetPlayer(roomMusicPlayer);
        }
        clipOval(binding.musicDiscWrap);
        clipOval(binding.musicFloatWrap);
        // YouTube WebView is lazy — see ensureMusicYoutubeWebView().
        binding.btnMusicPlayPause.setOnClickListener(v -> toggleRoomMusicPlayback());
        binding.btnMusicStop.setOnClickListener(v -> {
            // Mikoo music_list_more — open library to add/pick tracks from the player.
            if (canManageMusic) showSavedMusicLibrary();
            else Toast.makeText(this, "يسمع الجميع الأغنية من بث المضيف. التحكم للمضيف فقط.",
                    Toast.LENGTH_SHORT).show();
        });
        binding.btnMusicDismiss.setOnClickListener(v -> dismissMusicCard());
        // Player body is for drag only — adding music is via list-more button (Mikoo).
        binding.musicCard.setOnClickListener(null);
        binding.imgMusicDisc.setOnClickListener(null);
        // Drag from disc / title / artist so seek + buttons keep working.
        View discDrag = binding.musicDiscWrap != null ? binding.musicDiscWrap : binding.imgMusicDisc;
        enableFloatingDrag(discDrag, binding.musicCard);
        enableFloatingDrag(binding.tvMusicTitle, binding.musicCard);
        enableFloatingDrag(binding.tvMusicArtist, binding.musicCard);
        if (binding.musicFloatWrap != null) {
            enableFloatingDrag(binding.musicFloatWrap);
        }
        if (binding.llMusicLibraryChip != null) {
            // Top "موسيقى" chip removed — staff use tools menu + circular float disc only.
            binding.llMusicLibraryChip.setVisibility(View.GONE);
            binding.llMusicLibraryChip.setOnClickListener(null);
        }
        binding.btnMusicSkip.setOnClickListener(v -> {
            if (canManageMusic && roomId != null) skipMusicTrack();
        });
        binding.seekMusic.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                if (!canManageMusic || roomId == null) return;
                if (isLocalMusicUrl(currentMusicUrl)) {
                    long duration = RoomRtcEngine.getInstance().getLocalMusicDurationMs();
                    if (duration > 0) {
                        long position = duration * seekBar.getProgress() / 1000L;
                        RoomRtcEngine.getInstance().seekLocalMusic(position);
                        viewModel.updateMusic(roomId, "seek", null, null, null, position);
                    }
                    return;
                }
                if (youtubeUsingEmbed) {
                    String vid = youtubeIdFromUrl(currentMusicUrl);
                    if (vid != null) {
                        long durationGuess = 180_000L;
                        long position = durationGuess * seekBar.getProgress() / 1000L;
                        playYoutubeEmbedInDisc(vid, true, position);
                        viewModel.updateMusic(roomId, "seek", null, null, null, position);
                    }
                    return;
                }
                if (isYoutubeMusicUrl(currentMusicUrl)) {
                    long duration = RoomRtcEngine.getInstance().getLocalMusicDurationMs();
                    if (duration <= 0 && roomMusicPlayer != null) {
                        duration = roomMusicPlayer.getDuration();
                    }
                    if (duration <= 0) return;
                    long position = duration * seekBar.getProgress() / 1000L;
                    RoomRtcEngine.getInstance().seekLocalMusic(position);
                    if (roomMusicPlayer != null) roomMusicPlayer.seekTo(position);
                    viewModel.updateMusic(roomId, "seek", null, null, null, position);
                    return;
                }
                if (roomMusicPlayer == null) return;
                long duration = roomMusicPlayer.getDuration();
                if (duration <= 0) return;
                long position = duration * seekBar.getProgress() / 1000L;
                viewModel.updateMusic(roomId, "seek", null, null, null, position);
            }
        });
        handler.post(musicProgressRunnable);
    }

    private void clipOval(@Nullable View view) {
        if (view == null) return;
        view.setClipToOutline(true);
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setOval(0, 0, Math.max(1, v.getWidth()), Math.max(1, v.getHeight()));
            }
        });
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> v.invalidateOutline());
    }

    private void enableFloatingDrag(View floatingView) {
        enableFloatingDrag(floatingView, floatingView);
    }

    private void enableFloatingDrag(View touchView, View floatingView) {
        final float[] downRaw = new float[2];
        final float[] downTranslation = new float[2];
        final boolean[] dragged = {false};
        final float slop = 8f * getResources().getDisplayMetrics().density;
        touchView.setOnTouchListener((view, event) -> {
            View parent = floatingView.getParent() instanceof View
                    ? (View) floatingView.getParent() : null;
            if (parent == null) return false;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downRaw[0] = event.getRawX();
                    downRaw[1] = event.getRawY();
                    downTranslation[0] = floatingView.getTranslationX();
                    downTranslation[1] = floatingView.getTranslationY();
                    dragged[0] = false;
                    floatingView.setElevation(dp(28));
                    floatingView.setTranslationZ(dp(28));
                    floatingView.bringToFront();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).invalidate();
                    }
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRaw[0];
                    float dy = event.getRawY() - downRaw[1];
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) dragged[0] = true;
                    float tx = downTranslation[0] + dx;
                    float ty = downTranslation[1] + dy;
                    // Use measured size so fixed-width overlay can travel freely.
                    int fw = Math.max(floatingView.getWidth(), floatingView.getMeasuredWidth());
                    int fh = Math.max(floatingView.getHeight(), floatingView.getMeasuredHeight());
                    float minX = -floatingView.getLeft();
                    float maxX = parent.getWidth() - floatingView.getLeft() - fw;
                    float minY = -floatingView.getTop();
                    float maxY = parent.getHeight() - floatingView.getTop() - fh;
                    if (maxX < minX) {
                        float mid = (minX + maxX) / 2f;
                        minX = maxX = mid;
                    }
                    if (maxY < minY) {
                        float mid = (minY + maxY) / 2f;
                        minY = maxY = mid;
                    }
                    floatingView.setTranslationX(Math.max(minX, Math.min(maxX, tx)));
                    floatingView.setTranslationY(Math.max(minY, Math.min(maxY, ty)));
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (!dragged[0]) view.performClick();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        });
    }

    private final Runnable musicProgressRunnable = new Runnable() {
        @Override public void run() {
            if (binding != null) {
                updateMusicProgressUi();
            }
            handler.postDelayed(this, 500);
        }
    };

    private static String formatMusicClock(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        if (min >= 100) {
            return String.format(java.util.Locale.US, "%d:%02d", min, sec);
        }
        return String.format(java.util.Locale.US, "%d:%02d", min, sec);
    }

    /** Resolve current/total from Zego local mix, ExoPlayer, or startedAt wall clock. */
    private long[] resolveMusicPositionAndDurationMs() {
        long duration = 0L;
        long position = 0L;
        try {
            if (isLocalMusicUrl(currentMusicUrl)
                    || (isYoutubeMusicUrl(currentMusicUrl)
                    && RoomRtcEngine.getInstance().getLocalMusicDurationMs() > 0)) {
                duration = RoomRtcEngine.getInstance().getLocalMusicDurationMs();
                position = RoomRtcEngine.getInstance().getLocalMusicPositionMs();
            }
        } catch (Exception ignored) {
        }
        if (duration <= 0 && roomMusicPlayer != null) {
            try {
                long d = roomMusicPlayer.getDuration();
                if (d > 0 && d != androidx.media3.common.C.TIME_UNSET) {
                    duration = d;
                    position = Math.max(0L, roomMusicPlayer.getCurrentPosition());
                }
            } catch (Exception ignored) {
            }
        }
        // Wall-clock fallback while duration still unknown (e.g. embed warmup).
        if (position <= 0 && currentMusicStartedAt != null
                && "playing".equalsIgnoreCase(currentMusicStatus)) {
            long started = parseMusicStartedAtMs(currentMusicStartedAt);
            if (started > 0) {
                position = Math.max(0L, System.currentTimeMillis() - started);
            }
        }
        if (duration <= 0 && position > 0) {
            // Keep seek near end rather than frozen at 0 while duration arrives.
            duration = Math.max(position + 1000L, position);
        }
        return new long[]{position, duration};
    }

    private static long parseMusicStartedAtMs(@Nullable String startedAt) {
        if (startedAt == null || startedAt.trim().isEmpty()) return 0L;
        String s = startedAt.trim();
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            // 2026-08-04T12:00:00.000Z already covered; try offset formats
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private void updateMusicProgressUi() {
        if (binding == null) return;
        long[] pair = resolveMusicPositionAndDurationMs();
        long position = pair[0];
        long duration = pair[1];
        if (binding.seekMusic != null && !binding.seekMusic.isPressed() && duration > 0) {
            int progress = (int) Math.min(1000L, Math.max(0L, position * 1000L / duration));
            binding.seekMusic.setProgress(progress);
        }
        if (binding.tvMusicElapsed != null) {
            binding.tvMusicElapsed.setText(formatMusicClock(position));
        }
        if (binding.tvMusicDuration != null) {
            binding.tvMusicDuration.setText(duration > 0
                    ? formatMusicClock(duration)
                    : "--:--");
        }
    }

    @SuppressLint("NewApi")
    private void applyMusicState(
            String url, String title, String artist, String status, long positionMs, String startedAt) {
        // ExoPlayer is deferred past first frame — spin up when room actually has music.
        if (url != null && !url.trim().isEmpty()) {
            ensureMusicUiReady();
        }
        String incomingMusicUrl = url != null ? url.trim() : null;
        boolean urlEmpty = incomingMusicUrl == null || incomingMusicUrl.isEmpty();
        String prevStatus = currentMusicStatus;
        String prevUrl = currentMusicUrl != null ? currentMusicUrl.trim() : null;
        // Never treat a missing status as "stopped" during room refresh (lock/settings).
        // That was killing YouTube mid-song when getRoom omitted or nulled musicStatus.
        if (urlEmpty) {
            currentMusicStatus = "stopped";
        } else if (status != null && !status.trim().isEmpty()) {
            currentMusicStatus = status.trim();
        } else if (prevUrl != null && prevUrl.equals(incomingMusicUrl)
                && ("playing".equalsIgnoreCase(prevStatus)
                || "paused".equalsIgnoreCase(prevStatus))) {
            currentMusicStatus = prevStatus;
        } else {
            currentMusicStatus = "playing";
        }
        if (startedAt != null && !startedAt.isEmpty()) {
            currentMusicStartedAt = startedAt;
        }
        if (!"playing".equalsIgnoreCase(currentMusicStatus)) {
            musicEndReported = false;
        }
        if (dismissedMusicUrl == null && roomId != null) {
            dismissedMusicUrl = getSharedPreferences("voice_room_ui", MODE_PRIVATE)
                    .getString("dismissed_music_" + roomId, null);
        }
        if (dismissedMusicUrl != null
                && incomingMusicUrl != null
                && !incomingMusicUrl.equals(dismissedMusicUrl)
                && "playing".equalsIgnoreCase(currentMusicStatus)) {
            clearDismissedMusic();
        }
        currentMusicUrl = url;
        boolean playing = "playing".equalsIgnoreCase(currentMusicStatus);
        boolean paused = "paused".equalsIgnoreCase(currentMusicStatus);
        boolean stopped = urlEmpty || "stopped".equalsIgnoreCase(currentMusicStatus);
        // Soft no-op: same track already prepared & playing — skip seek/restart after lock/save.
        if (!stopped
                && playing
                && incomingMusicUrl != null
                && incomingMusicUrl.equals(preparedMusicUrl)
                && (isLocalMusicUrl(incomingMusicUrl)
                ? RoomRtcEngine.getInstance().isLocalMusicPlaying()
                : (roomMusicPlayer != null && roomMusicPlayer.isPlaying())
                        || RoomRtcEngine.getInstance().isLocalMusicPlaying())) {
            if (title != null && !title.isEmpty() && binding != null && binding.tvMusicTitle != null) {
                binding.tvMusicTitle.setText(title);
            }
            if (artist != null && binding != null && binding.tvMusicArtist != null) {
                binding.tvMusicArtist.setText(artist);
            }
            return;
        }
        if (stopped) {
            binding.musicCard.setVisibility(View.GONE);
            musicPanelExpanded = false;
            stopMusicDiscAnimation();
            preparedMusicUrl = null;
            pendingYtResolveKey = null;
            clearMusicVideoUi();
            if (roomMusicPlayer != null) roomMusicPlayer.stop();
            if (canManageMusic) RoomRtcEngine.getInstance().stopLocalMusic();
            // No music → never float a player chip in the user's face (fresh install / idle).
            showMusicReopenChip(false);
            return;
        }

        boolean dismissed = incomingMusicUrl.equals(dismissedMusicUrl);
        if (canManageMusic) {
            // Manager/Host: sees full control card only while track is active & not dismissed.
            boolean showCard = !dismissed && (playing || paused);
            musicPanelExpanded = showCard;
            binding.musicCard.setVisibility(showCard ? View.VISIBLE : View.GONE);
            // After close (X): compact disc near mic — only while music still plays/pauses.
            showMusicReopenChip(!showCard && (playing || paused));
            if (showCard) {
                binding.musicCard.setElevation(dp(28));
                binding.musicCard.setTranslationZ(dp(28));
                binding.musicCard.bringToFront();
            }
        } else {
            // Listeners: NEVER see the control card. Only spinning disc while music plays.
            musicPanelExpanded = false;
            binding.musicCard.setVisibility(View.GONE);
            showMusicReopenChip(!dismissed && playing);
        }

        binding.tvMusicTitle.setText(title != null && !title.isEmpty() ? title : "موسيقى الغرفة");
        binding.tvMusicArtist.setText(artist != null && !artist.isEmpty() ? artist : "من هاتف المضيف");
        binding.btnMusicPlayPause.setEnabled(canManageMusic);
        binding.btnMusicSkip.setEnabled(canManageMusic);
        binding.btnMusicStop.setEnabled(canManageMusic);
        binding.seekMusic.setEnabled(canManageMusic);
        updateMusicProgressUi();
        binding.btnMusicPlayPause.setImageResource(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
        if (playing && !youtubePlayingVideo && !youtubeUsingEmbed) {
            startMusicDiscAnimation();
        } else {
            stopMusicDiscAnimation();
        }

        // Local phone tracks: host mixes into Zego publish (boosted volume for listeners).
        if (isLocalMusicUrl(incomingMusicUrl)) {
            clearMusicVideoUi();
            if (roomMusicPlayer != null && roomMusicPlayer.isPlaying()) {
                roomMusicPlayer.stop();
            }
            if (canManageMusic) {
                SavedMusicTrack track = findLocalTrackByUrl(incomingMusicUrl);
                if (track != null && track.localPath != null && !track.localPath.isEmpty()) {
                    if (playing && !incomingMusicUrl.equals(preparedMusicUrl)) {
                        ensurePublishingForMusic();
                        RoomRtcEngine.getInstance().playLocalMusic(track.localPath, positionMs);
                        preparedMusicUrl = incomingMusicUrl;
                    } else if (playing) {
                        ensurePublishingForMusic();
                        RoomRtcEngine.getInstance().boostMusicMixVolume();
                        if (!RoomRtcEngine.getInstance().isLocalMusicPlaying()) {
                            if (!RoomRtcEngine.getInstance().hasLocalMusicPlayer()) {
                                RoomRtcEngine.getInstance().playLocalMusic(
                                        track.localPath, positionMs);
                                preparedMusicUrl = incomingMusicUrl;
                            } else {
                                RoomRtcEngine.getInstance().resumeLocalMusic();
                            }
                        }
                    } else if (paused) {
                        RoomRtcEngine.getInstance().pauseLocalMusic();
                    }
                }
            }
            return;
        }

        // Internet (yt://): host mixes audio into Zego (loud for everyone);
        // disc shows muted video preview. Listeners hear via Zego, not local Exo.
        if (isYoutubeMusicUrl(incomingMusicUrl)) {
            streamYoutubeInDisc(incomingMusicUrl, playing, paused, positionMs, startedAt,
                    title, artist);
            return;
        }

        // Switching to remote/server uploaded audio — stop host local mix.
        if (canManageMusic) RoomRtcEngine.getInstance().stopLocalMusic();

        String absoluteUrl = AssetCatalog.absoluteUrl(url);
        if (roomMusicPlayer != null && absoluteUrl != null) {
            boolean changed = !absoluteUrl.equals(preparedMusicUrl);
            if (changed) {
                preparedMusicUrl = absoluteUrl;
                roomMusicPlayer.setMediaItem(MediaItem.fromUri(absoluteUrl));
                roomMusicPlayer.prepare();
            }
            long target = Math.max(0L, positionMs);
            if (playing && startedAt != null) {
                try {
                    target += Math.max(0L,
                            System.currentTimeMillis() + roomMusicServerOffsetMs
                                    - java.time.Instant.parse(startedAt).toEpochMilli());
                } catch (Exception ignored) {}
            }
            if (changed || Math.abs(roomMusicPlayer.getCurrentPosition() - target) > 1500L) {
                roomMusicPlayer.seekTo(target);
            }
            roomMusicPlayer.setPlayWhenReady(playing);
            if ("stopped".equalsIgnoreCase(currentMusicStatus)) {
                roomMusicPlayer.pause();
                roomMusicPlayer.seekTo(0);
            }
        }
    }

    private static boolean isLocalMusicUrl(@Nullable String url) {
        return url != null && url.regionMatches(true, 0, "local://", 0, 8);
    }

    private static boolean isYoutubeMusicUrl(@Nullable String url) {
        return url != null && url.regionMatches(true, 0, "yt://", 0, 5);
    }

    @Nullable
    private static String youtubeIdFromUrl(@Nullable String url) {
        if (!isYoutubeMusicUrl(url)) return null;
        String id = url.substring(5).trim();
        return id.isEmpty() ? null : id;
    }

    private void applyYoutubeMusicPlayback(
            @NonNull String ytUrl,
            boolean playing,
            boolean paused,
            long positionMs,
            @Nullable String startedAt) {
        streamYoutubeInDisc(ytUrl, playing, paused, positionMs, startedAt, null, null);
    }

    /**
     * YouTube in the floating disc:
     * - Host mixes audio into Zego publish (everyone in the room hears).
     * - Host also hears locally via ExoPlayer (MediaPlayer local monitor muted to avoid echo).
     * - Guests hear from the host Zego stream only (ExoPlayer silent).
     */
    private void streamYoutubeInDisc(
            @NonNull String ytUrl,
            boolean playing,
            boolean paused,
            long positionMs,
            @Nullable String startedAt,
            @Nullable String title,
            @Nullable String artist) {
        if (roomMusicPlayer == null) return;
        String videoId = youtubeIdFromUrl(ytUrl);
        if (videoId == null) return;

        long target = Math.max(0L, positionMs);
        if (playing && startedAt != null) {
            try {
                target += Math.max(0L,
                        System.currentTimeMillis() + roomMusicServerOffsetMs
                                - java.time.Instant.parse(startedAt).toEpochMilli());
            } catch (Exception ignored) {}
        }
        final long seekTarget = target;

        if (ytUrl.equals(preparedMusicUrl) && !youtubeUsingEmbed
                && roomMusicPlayer.getMediaItemCount() > 0) {
            if (Math.abs(roomMusicPlayer.getCurrentPosition() - seekTarget) > 1500L) {
                roomMusicPlayer.seekTo(seekTarget);
            }
            applyHostOrGuestMusicVolume();
            roomMusicPlayer.setPlayWhenReady(playing);
            if (canManageMusic) {
                ensurePublishingForMusic();
                if (playing) {
                    RoomRtcEngine.getInstance().boostMusicMixVolume();
                    if (!RoomRtcEngine.getInstance().isLocalMusicPlaying()) {
                        RoomRtcEngine.getInstance().resumeLocalMusic();
                    }
                    if (Math.abs(RoomRtcEngine.getInstance().getLocalMusicPositionMs()
                            - seekTarget) > 1500L) {
                        RoomRtcEngine.getInstance().seekLocalMusic(seekTarget);
                    }
                } else {
                    RoomRtcEngine.getInstance().pauseLocalMusic();
                }
            }
            bindMusicVideoSurfaces();
            return;
        }
        if (ytUrl.equals(preparedMusicUrl) && youtubeUsingEmbed) {
            if (!playing) {
                stopYoutubeEmbed();
                if (canManageMusic) RoomRtcEngine.getInstance().pauseLocalMusic();
            } else {
                playYoutubeEmbedInDisc(videoId, true, seekTarget);
            }
            return;
        }
        if (paused && !playing) {
            if (youtubeUsingEmbed) stopYoutubeEmbed();
            else {
                roomMusicPlayer.setPlayWhenReady(false);
            }
            if (canManageMusic) RoomRtcEngine.getInstance().pauseLocalMusic();
            return;
        }
        if (!playing) return;

        final String requestKey = ytUrl + "|stream|" + (playing ? "1" : "0");
        if (requestKey.equals(pendingYtResolveKey)) return;
        pendingYtResolveKey = requestKey;

        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            YoutubeAudioResolver.Resolved resolved = YoutubeAudioResolver.resolve(videoId);
            final String streamUrl = resolved != null ? resolved.audioUrl : null;
            final String mixUrl = resolved != null ? resolved.mixUrl : null;
            final boolean hasVideo = resolved != null && resolved.hasVideo;
            final String resolvedTitle = resolved != null ? resolved.title : title;
            final String resolvedArtist = resolved != null ? resolved.artist : artist;
            final String thumb = resolved != null ? resolved.thumbnailUrl : null;
            runOnUiThread(() -> {
                if (!requestKey.equals(pendingYtResolveKey)) return;
                pendingYtResolveKey = null;
                if (streamUrl == null || streamUrl.isEmpty()) {
                    playYoutubeEmbedInDisc(videoId, true, seekTarget);
                    if (resolvedTitle != null && !resolvedTitle.isEmpty() && binding != null
                            && binding.tvMusicTitle != null) {
                        binding.tvMusicTitle.setText(resolvedTitle);
                    }
                    return;
                }
                if (roomMusicPlayer == null) return;
                stopYoutubeEmbed();
                preparedMusicUrl = ytUrl;
                youtubePlayingVideo = hasVideo;
                currentMusicThumbUrl = thumb;
                applyMusicPoster(thumb);

                // Host: mix into Zego for the room; mute MediaPlayer local to avoid double sound.
                if (canManageMusic) {
                    ensurePublishingForMusic();
                    String zegoUrl = mixUrl != null && !mixUrl.isEmpty() ? mixUrl : streamUrl;
                    RoomRtcEngine.getInstance().playLocalMusic(zegoUrl, seekTarget, true);
                }

                // Host hears ExoPlayer; guests keep it silent and hear Zego.
                roomMusicPlayer.setMediaItem(MediaItem.fromUri(streamUrl));
                roomMusicPlayer.prepare();
                roomMusicPlayer.seekTo(seekTarget);
                applyHostOrGuestMusicVolume();
                roomMusicPlayer.setPlayWhenReady(true);
                bindMusicVideoSurfaces();
                if (hasVideo) {
                    showMusicVideoSurface(true);
                    stopMusicDiscAnimation();
                } else {
                    showMusicVideoSurface(false);
                    startMusicDiscAnimation();
                }
                if (resolvedTitle != null && !resolvedTitle.isEmpty() && binding != null
                        && binding.tvMusicTitle != null) {
                    binding.tvMusicTitle.setText(resolvedTitle);
                }
                if (resolvedArtist != null && binding != null && binding.tvMusicArtist != null) {
                    binding.tvMusicArtist.setText(resolvedArtist);
                }
            });
        });
    }

    /** Host monitors YouTube on device; guests only hear the Zego room mix. */
    private void applyHostOrGuestMusicVolume() {
        if (roomMusicPlayer == null) return;
        if (roomSpeakerMuted) {
            roomMusicPlayer.setVolume(0f);
            return;
        }
        if (canManageMusic) {
            roomMusicPlayer.setVolume(1f);
        } else {
            roomMusicPlayer.setVolume(0f);
        }
    }

    private void applyMusicPoster(@Nullable String thumbUrl) {
        if (binding == null) return;
        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            try {
                Glide.with(this).load(thumbUrl).centerCrop().into(binding.imgMusicDisc);
                if (binding.imgMusicFloatDisc != null) {
                    Glide.with(this).load(thumbUrl).centerCrop().into(binding.imgMusicFloatDisc);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void showMusicVideoSurface(boolean showVideo) {
        if (binding == null) return;
        boolean useEmbed = youtubeUsingEmbed;
        if (binding.musicVideoSurface != null) {
            binding.musicVideoSurface.setVisibility(
                    showVideo && !useEmbed ? View.VISIBLE : View.GONE);
        }
        if (binding.musicFloatVideo != null) {
            boolean floatVisible = binding.musicFloatWrap != null
                    && binding.musicFloatWrap.getVisibility() == View.VISIBLE;
            binding.musicFloatVideo.setVisibility(
                    showVideo && !useEmbed && floatVisible ? View.VISIBLE : View.GONE);
        }
        if (binding.musicYoutubeWebHost != null) {
            binding.musicYoutubeWebHost.setVisibility(useEmbed ? View.VISIBLE : View.GONE);
        }
        if (useEmbed) {
            WebView yt = ensureMusicYoutubeWebView();
            if (yt != null) yt.setVisibility(View.VISIBLE);
        } else if (musicYoutubeWebView != null) {
            musicYoutubeWebView.setVisibility(View.GONE);
        }
        if (binding.imgMusicDisc != null) {
            binding.imgMusicDisc.setVisibility(
                    showVideo || useEmbed ? View.INVISIBLE : View.VISIBLE);
        }
        if (binding.imgMusicFloatDisc != null) {
            boolean floatVisible = binding.musicFloatWrap != null
                    && binding.musicFloatWrap.getVisibility() == View.VISIBLE;
            binding.imgMusicFloatDisc.setVisibility(
                    (showVideo || useEmbed) && floatVisible ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void bindMusicVideoSurfaces() {
        if (roomMusicPlayer == null || binding == null) return;
        boolean cardOpen = binding.musicCard != null
                && binding.musicCard.getVisibility() == View.VISIBLE
                && musicPanelExpanded;
        if (cardOpen && binding.musicVideoSurface != null) {
            musicVideoSetPlayer(roomMusicPlayer);
            if (binding.musicFloatVideo != null) musicFloatSetPlayer(null);
        } else if (binding.musicFloatVideo != null
                && binding.musicFloatWrap != null
                && binding.musicFloatWrap.getVisibility() == View.VISIBLE) {
            musicFloatSetPlayer(roomMusicPlayer);
            if (binding.musicVideoSurface != null) musicVideoSetPlayer(null);
        } else if (binding.musicVideoSurface != null) {
            musicVideoSetPlayer(roomMusicPlayer);
        }
        if (youtubePlayingVideo || youtubeUsingEmbed) {
            showMusicVideoSurface(true);
        }
    }

    private void playYoutubeEmbedInDisc(@NonNull String videoId, boolean playing, long positionMs) {
        if (binding == null || ensureMusicYoutubeWebView() == null) return;
        if (roomMusicPlayer != null) {
            try {
                roomMusicPlayer.stop();
                roomMusicPlayer.clearMediaItems();
            } catch (Exception ignored) {
            }
        }
        youtubeUsingEmbed = true;
        youtubePlayingVideo = true;
        preparedMusicUrl = "yt://" + videoId;
        stopMusicDiscAnimation();
        if (binding.musicYoutubeWebHost != null) {
            binding.musicYoutubeWebHost.setVisibility(View.VISIBLE);
        }
        long startSec = Math.max(0L, positionMs / 1000L);
        String html = "<!DOCTYPE html><html><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1,maximum-scale=1'/>"
                + "<style>html,body{margin:0;padding:0;background:#000;overflow:hidden;height:100%;}"
                + "iframe{border:0;width:100%;height:100%;}</style></head><body>"
                + "<iframe src='https://www.youtube.com/embed/" + videoId
                + "?autoplay=" + (playing ? "1" : "0")
                + "&controls=0&playsinline=1&rel=0&modestbranding=1&fs=0&start=" + startSec
                + "' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe>"
                + "</body></html>";
        ensureMusicYoutubeWebView().loadDataWithBaseURL(
                "https://www.youtube.com", html, "text/html", "utf-8", null);
        showMusicVideoSurface(true);
        if (binding.musicVideoSurface != null) musicVideoSetPlayer(null);
    }

    private void stopYoutubeEmbed() {
        youtubeUsingEmbed = false;
        if (musicYoutubeWebView != null) {
            try {
                musicYoutubeWebView.loadUrl("about:blank");
            } catch (Exception ignored) {
            }
            musicYoutubeWebView.setVisibility(View.GONE);
        }
        if (binding != null && binding.musicYoutubeWebHost != null) {
            binding.musicYoutubeWebHost.setVisibility(View.GONE);
        }
    }

    private void clearMusicVideoUi() {
        stopYoutubeEmbed();
        youtubePlayingVideo = false;
        currentMusicThumbUrl = null;
        if (binding != null) {
            if (binding.musicVideoSurface != null) {
                musicVideoSetPlayer(null);
                binding.musicVideoSurface.setVisibility(View.GONE);
            }
            if (binding.musicFloatVideo != null) {
                musicFloatSetPlayer(null);
                binding.musicFloatVideo.setVisibility(View.GONE);
            }
            if (binding.imgMusicDisc != null) {
                binding.imgMusicDisc.setVisibility(View.VISIBLE);
                binding.imgMusicDisc.setImageResource(R.drawable.icon_room_music_voice);
            }
            if (binding.imgMusicFloatDisc != null) {
                binding.imgMusicFloatDisc.setVisibility(View.VISIBLE);
                binding.imgMusicFloatDisc.setImageResource(R.drawable.icon_room_music_voice);
            }
        }
    }

    private void streamYoutubeAudioOnly(
            @NonNull String ytUrl,
            boolean playing,
            boolean paused,
            long positionMs,
            @Nullable String startedAt,
            @Nullable String title,
            @Nullable String artist) {
        streamYoutubeInDisc(ytUrl, playing, paused, positionMs, startedAt, title, artist);
    }

    /** Host plays a saved local file into the Zego publish mix. */
    private void startHostMixedTrack(@Nullable String musicUrl,
                                     @Nullable String title,
                                     @Nullable String artist) {
        if (!canManageMusic || musicUrl == null || musicUrl.isEmpty()) return;
        if (isYoutubeMusicUrl(musicUrl)) {
            streamYoutubeAudioOnly(musicUrl, true, false, 0L, null, title, artist);
            return;
        }
        if (!isLocalMusicUrl(musicUrl)) return;
        SavedMusicTrack track = findLocalTrackByUrl(musicUrl);
        if (track != null && track.localPath != null && new File(track.localPath).exists()) {
            ensurePublishingForMusic();
            RoomRtcEngine.getInstance().playLocalMusic(track.localPath);
            preparedMusicUrl = musicUrl;
        }
    }

    /** Play/pause using room status (not flaky Zego state) — avoids freeze/no-resume. */
    private void toggleRoomMusicPlayback() {
        if (!canManageMusic || roomId == null) return;
        if (currentMusicUrl == null || currentMusicUrl.trim().isEmpty()
                || "stopped".equalsIgnoreCase(currentMusicStatus)) {
            showMusicPicker();
            return;
        }
        boolean currentlyPlaying = "playing".equalsIgnoreCase(currentMusicStatus);
        long pos;
        if (isLocalMusicUrl(currentMusicUrl)) {
            pos = RoomRtcEngine.getInstance().getLocalMusicPositionMs();
            if (currentlyPlaying) {
                RoomRtcEngine.getInstance().pauseLocalMusic();
            } else {
                ensurePublishingForMusic();
                if (!RoomRtcEngine.getInstance().hasLocalMusicPlayer()
                        || !currentMusicUrl.equals(preparedMusicUrl)) {
                    startHostMixedTrack(currentMusicUrl, null, null);
                } else {
                    RoomRtcEngine.getInstance().resumeLocalMusic();
                }
            }
        } else if (isYoutubeMusicUrl(currentMusicUrl)) {
            pos = youtubeUsingEmbed ? 0L
                    : RoomRtcEngine.getInstance().getLocalMusicPositionMs();
            if (pos <= 0L && roomMusicPlayer != null) {
                pos = roomMusicPlayer.getCurrentPosition();
            }
            if (currentlyPlaying) {
                if (youtubeUsingEmbed) {
                    stopYoutubeEmbed();
                    showMusicVideoSurface(false);
                    applyMusicPoster(currentMusicThumbUrl);
                }
                if (roomMusicPlayer != null) roomMusicPlayer.setPlayWhenReady(false);
                RoomRtcEngine.getInstance().pauseLocalMusic();
            } else {
                ensurePublishingForMusic();
                if (youtubeUsingEmbed) {
                    String vid = youtubeIdFromUrl(currentMusicUrl);
                    if (vid != null) playYoutubeEmbedInDisc(vid, true, pos);
                } else if (roomMusicPlayer != null && currentMusicUrl.equals(preparedMusicUrl)
                        && roomMusicPlayer.getMediaItemCount() > 0) {
                    applyHostOrGuestMusicVolume();
                    roomMusicPlayer.setPlayWhenReady(true);
                    if (!RoomRtcEngine.getInstance().isLocalMusicPlaying()) {
                        if (!RoomRtcEngine.getInstance().hasLocalMusicPlayer()) {
                            streamYoutubeInDisc(currentMusicUrl, true, false, pos, null, null, null);
                        } else {
                            RoomRtcEngine.getInstance().boostMusicMixVolume();
                            RoomRtcEngine.getInstance().resumeLocalMusic();
                        }
                    }
                } else {
                    streamYoutubeInDisc(currentMusicUrl, true, false, pos, null, null, null);
                }
            }
        } else {
            pos = roomMusicPlayer != null ? roomMusicPlayer.getCurrentPosition() : 0L;
            if (roomMusicPlayer != null) {
                roomMusicPlayer.setPlayWhenReady(!currentlyPlaying);
            }
        }
        currentMusicStatus = currentlyPlaying ? "paused" : "playing";
        binding.btnMusicPlayPause.setImageResource(currentlyPlaying
                ? android.R.drawable.ic_media_play
                : android.R.drawable.ic_media_pause);
        if (currentlyPlaying) stopMusicDiscAnimation();
        else if (!youtubePlayingVideo && !youtubeUsingEmbed) startMusicDiscAnimation();
        viewModel.updateMusic(roomId, currentlyPlaying ? "pause" : "play",
                null, null, null, pos);
    }

    private void ensurePublishingForMusic() {
        if (myUserId == null || myUserId.isEmpty()) return;
        RoomRtcEngine.getInstance().startPublishingAudio(
                RoomRtcEngine.audioStreamId(myUserId));
    }

    private void dismissMusicCard() {
        musicPanelExpanded = false;
        binding.musicCard.setVisibility(View.GONE);
        dismissedMusicUrl = currentMusicUrl != null ? currentMusicUrl.trim() : null;
        if (roomId != null && dismissedMusicUrl != null && !dismissedMusicUrl.isEmpty()) {
            getSharedPreferences("voice_room_ui", MODE_PRIVATE)
                    .edit()
                    .putString("dismissed_music_" + roomId, dismissedMusicUrl)
                    .apply();
        }
        boolean stillActive = currentMusicUrl != null && !currentMusicUrl.isEmpty()
                && ("playing".equalsIgnoreCase(currentMusicStatus)
                || "paused".equalsIgnoreCase(currentMusicStatus));
        // Close → compact disc near mic (only while music is still active).
        showMusicReopenChip(stillActive && (canManageMusic || isHost || isOwner));
        bindMusicVideoSurfaces();
    }

    private void showMusicReopenChip(boolean show) {
        if (binding == null) return;
        boolean staffMusic = canManageMusic || isHost || isOwner;
        // Remove the redundant top "موسيقى" chip near seats — only floating disc/player.
        if (binding.llMusicLibraryChip != null) {
            binding.llMusicLibraryChip.setVisibility(View.GONE);
        }
        if (binding.musicFloatWrap == null) return;
        binding.musicFloatWrap.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            if (staffMusic) {
                binding.musicFloatWrap.setOnClickListener(v -> reopenMusicCard());
            } else {
                binding.musicFloatWrap.setOnClickListener(v ->
                        Toast.makeText(this, "يتم تشغيل الموسيقى بواسطة المضيف 🎵", Toast.LENGTH_SHORT).show()
                );
            }
            binding.musicFloatWrap.bringToFront();
        }
    }

    private void startMusicDiscAnimation() {
        if (youtubePlayingVideo || youtubeUsingEmbed) return;
        if (musicDiscAnimator != null && musicDiscAnimator.isStarted()) return;
        if (binding == null) return;
        if (binding.imgMusicDisc != null) {
            musicDiscAnimator = ObjectAnimator.ofFloat(binding.imgMusicDisc, View.ROTATION, 0f, 360f);
            musicDiscAnimator.setDuration(4800L);
            musicDiscAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            musicDiscAnimator.setRepeatCount(ValueAnimator.INFINITE);
            musicDiscAnimator.start();
        }
        if (binding.imgMusicFloatDisc != null) {
            ObjectAnimator wrapAnimator = ObjectAnimator.ofFloat(
                    binding.imgMusicFloatDisc, View.ROTATION, 0f, 360f);
            wrapAnimator.setDuration(4800L);
            wrapAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            wrapAnimator.setRepeatCount(ValueAnimator.INFINITE);
            wrapAnimator.start();
            binding.imgMusicFloatDisc.setTag(R.id.musicFloatWrap, wrapAnimator);
        }
    }

    private void stopMusicDiscAnimation() {
        if (musicDiscAnimator != null) {
            musicDiscAnimator.cancel();
            musicDiscAnimator = null;
        }
        if (binding != null) {
            if (binding.imgMusicDisc != null) {
                binding.imgMusicDisc.setRotation(0f);
            }
            if (binding.imgMusicFloatDisc != null) {
                Object animator = binding.imgMusicFloatDisc.getTag(R.id.musicFloatWrap);
                if (animator instanceof ObjectAnimator) {
                    ((ObjectAnimator) animator).cancel();
                }
                binding.imgMusicFloatDisc.setRotation(0f);
            }
        }
    }

    private void startTaskFloatPulse() {
        if (binding == null || binding.taskFloatPulse == null) return;
        if (binding.taskFloatWrap.getVisibility() != View.VISIBLE) return;
        stopTaskFloatPulse();
        View target = binding.taskFloatPulse;
        ObjectAnimator sx = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.14f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.14f);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sx.setRepeatMode(ValueAnimator.REVERSE);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatMode(ValueAnimator.REVERSE);
        sx.setDuration(780L);
        sy.setDuration(780L);
        sx.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        sy.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        taskFloatPulseAnimator = new android.animation.AnimatorSet();
        taskFloatPulseAnimator.playTogether(sx, sy);
        taskFloatPulseAnimator.start();
    }

    private void stopTaskFloatPulse() {
        if (taskFloatPulseAnimator != null) {
            taskFloatPulseAnimator.cancel();
            taskFloatPulseAnimator = null;
        }
        if (binding != null && binding.taskFloatPulse != null) {
            binding.taskFloatPulse.setScaleX(1f);
            binding.taskFloatPulse.setScaleY(1f);
        }
    }

    private void clearDismissedMusic() {
        dismissedMusicUrl = null;
        if (roomId != null) {
            getSharedPreferences("voice_room_ui", MODE_PRIVATE)
                    .edit()
                    .remove("dismissed_music_" + roomId)
                    .apply();
        }
    }

    /** Reopen floating player only (used by musicFloatWrap chip). */
    private void reopenMusicCard() {
        if (!(canManageMusic || isHost || isOwner)) return;
        clearDismissedMusic();
        musicPanelExpanded = true;
        if (binding.tvMusicTitle != null
                && (currentMusicUrl == null || currentMusicUrl.trim().isEmpty())) {
            binding.tvMusicTitle.setText(R.string.music_list_play_empty_tips1);
        }
        binding.musicCard.setVisibility(View.VISIBLE);
        binding.musicCard.setElevation(dp(28));
        binding.musicCard.setTranslationZ(dp(28));
        binding.musicCard.bringToFront();
        showMusicReopenChip(false);
        bindMusicVideoSurfaces();
    }

    /** Tools entry: open Mikoo-style floating player; add tracks from list-more. */
    private void showMusicPanelOrPicker() {
        reopenMusicCard();
    }

    private void addLocalMusicAndPlay(Uri uri) {
        addLocalMusicAndPlay(uri, null, null, null);
    }

    private void addLocalMusicAndPlay(
            Uri uri,
            @Nullable String forcedTitle,
            @Nullable String forcedArtist,
            @Nullable Uri albumArtUri) {
        Toast.makeText(this, "جاري تجهيز الأغنية محلياً…", Toast.LENGTH_SHORT).show();
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            DeviceMusicScanner.Meta meta = DeviceMusicScanner.readMeta(this, uri);
            String metadataTitle = forcedTitle != null && !forcedTitle.trim().isEmpty()
                    ? forcedTitle.trim() : meta.title;
            String metadataArtist = forcedArtist != null && !forcedArtist.trim().isEmpty()
                    ? forcedArtist.trim() : meta.artist;
            try {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                String mime = getContentResolver().getType(uri);
                if (mime == null) mime = "audio/mpeg";
                String normalizedMime = mime.toLowerCase(java.util.Locale.US);
                String extension = normalizedMime.contains("wav")
                        ? ".wav"
                        : normalizedMime.contains("flac")
                        ? ".flac"
                        : normalizedMime.contains("ogg") || normalizedMime.contains("opus")
                        ? ".ogg"
                        : normalizedMime.contains("aac")
                        ? ".aac"
                        : (normalizedMime.contains("mp4") || normalizedMime.contains("m4a"))
                        ? ".m4a"
                        : ".mp3";
                String id = "t" + System.currentTimeMillis();
                File dir = new File(getFilesDir(), "room_music_local");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("تعذر إنشاء مجلد الموسيقى");
                }
                File out = new File(dir, id + extension);
                try (InputStream input = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    if (input == null) throw new IOException("تعذر فتح الملف");
                    byte[] buffer = new byte[8192];
                    int count;
                    long total = 0L;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 40L * 1024L * 1024L) {
                            throw new IOException("حجم الأغنية أكبر من 40 ميغابايت");
                        }
                        fos.write(buffer, 0, count);
                    }
                }

                // Real cover: album art URI, else embedded picture.
                String coverPath = null;
                File coversDir = new File(dir, "covers");
                if (!coversDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    coversDir.mkdirs();
                }
                File coverFile = new File(coversDir, id + ".jpg");
                try {
                    boolean wrote = false;
                    if (albumArtUri != null) {
                        try (InputStream in = getContentResolver().openInputStream(albumArtUri);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(coverFile)) {
                            if (in != null) {
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                                wrote = coverFile.length() > 64;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (!wrote) {
                        byte[] pic = DeviceMusicScanner.embeddedCover(this, uri);
                        if (pic != null && pic.length > 64) {
                            try (java.io.FileOutputStream fos =
                                         new java.io.FileOutputStream(coverFile)) {
                                fos.write(pic);
                                wrote = true;
                            }
                        }
                    }
                    if (wrote) coverPath = coverFile.getAbsolutePath();
                    else if (coverFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        coverFile.delete();
                    }
                } catch (Exception ignored) {
                }

                String title = metadataTitle;
                if (title == null || title.trim().isEmpty()) title = "أغنية";
                String artist = metadataArtist;
                if (artist == null || artist.trim().isEmpty()) artist = "فنان غير معروف";
                final String finalTitle = title.trim();
                final String finalArtist = artist.trim();
                final String localUrl = "local://" + id;
                SavedMusicTrack track = new SavedMusicTrack(
                        id, localUrl, out.getAbsolutePath(), finalTitle, finalArtist, coverPath);
                saveLocalTrack(track);
                runOnUiThread(() -> playLocalTrack(track));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        error.getMessage() != null ? error.getMessage() : "تعذر إضافة الموسيقى",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private static final class SavedMusicTrack {
        String id;
        String url;
        String localPath;
        String title;
        String artist;
        String thumbnailUrl;
        SavedMusicTrack(String id, String url, String localPath, String title, String artist) {
            this(id, url, localPath, title, artist, null);
        }
        SavedMusicTrack(String id, String url, String localPath, String title, String artist,
                        String thumbnailUrl) {
            this.id = id;
            this.url = url;
            this.localPath = localPath;
            this.title = title;
            this.artist = artist;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    private void playLocalTrack(@Nullable SavedMusicTrack track) {
        if (track == null || roomId == null) return;
        if (isYoutubeMusicUrl(track.url)) {
            playYoutubeMusic(youtubeIdFromUrl(track.url), track.title, track.artist);
            return;
        }
        if (track.url != null && (track.url.startsWith("http://")
                || track.url.startsWith("https://")
                || track.url.startsWith("/uploads/"))) {
            playRemoteMusicUrl(track.url, track.title, track.artist);
            return;
        }
        if (track.localPath == null || track.localPath.isEmpty()
                || !new File(track.localPath).exists()) {
            Toast.makeText(this, "الملف غير موجود على الجهاز", Toast.LENGTH_SHORT).show();
            return;
        }
        clearDismissedMusic();
        ensurePublishingForMusic();
        RoomRtcEngine.getInstance().playLocalMusic(track.localPath);
        preparedMusicUrl = track.url;
        currentMusicUrl = track.url;
        currentMusicStatus = "playing";
        binding.tvMusicTitle.setText(track.title != null ? track.title : "أغنية");
        binding.tvMusicArtist.setText(track.artist != null && !track.artist.isEmpty()
                ? track.artist : "فنان غير معروف");
        bindLocalMusicCover(track.thumbnailUrl);
        binding.btnMusicPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        startMusicDiscAnimation();
        viewModel.updateMusic(roomId, "load", track.url, track.title,
                track.artist != null && !track.artist.isEmpty()
                        ? track.artist : "فنان غير معروف", 0L);
        musicPanelExpanded = true;
        binding.musicCard.setVisibility(View.VISIBLE);
        binding.musicCard.bringToFront();
        showMusicReopenChip(false);
    }

    private void bindLocalMusicCover(@Nullable String thumbnailUrl) {
        if (binding == null) return;
        Object model = null;
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            if (thumbnailUrl.startsWith("http") || thumbnailUrl.startsWith("content:")) {
                model = thumbnailUrl;
            } else {
                File f = new File(thumbnailUrl);
                if (f.isFile()) model = f;
            }
        }
        if (binding.imgMusicDisc != null) {
            if (model != null) {
                try {
                    Glide.with(binding.imgMusicDisc)
                            .load(model)
                            .centerCrop()
                            .placeholder(R.drawable.icon_room_music_voice)
                            .error(R.drawable.icon_room_music_voice)
                            .into(binding.imgMusicDisc);
                } catch (Exception e) {
                    binding.imgMusicDisc.setImageResource(R.drawable.icon_room_music_voice);
                }
            } else {
                binding.imgMusicDisc.setImageResource(R.drawable.icon_room_music_voice);
            }
        }
        if (binding.imgMusicFloatDisc != null) {
            if (model != null) {
                try {
                    Glide.with(binding.imgMusicFloatDisc)
                            .load(model)
                            .centerCrop()
                            .placeholder(R.drawable.icon_room_music_voice)
                            .error(R.drawable.icon_room_music_voice)
                            .into(binding.imgMusicFloatDisc);
                } catch (Exception e) {
                    binding.imgMusicFloatDisc.setImageResource(R.drawable.icon_room_music_voice);
                }
            } else {
                binding.imgMusicFloatDisc.setImageResource(R.drawable.icon_room_music_voice);
            }
        }
    }

    /** Add internet search hit into My Music (انا) — does not start playback. */
    private boolean addInternetHitToMyMusic(@Nullable RoomDtos.InternetMusicHitDto hit) {
        if (hit == null || hit.id == null || hit.id.trim().isEmpty()) return false;
        String videoId = hit.id.trim();
        String musicUrl = "yt://" + videoId;
        if (findLocalTrackByUrl(musicUrl) != null) return false;
        String title = hit.title != null && !hit.title.trim().isEmpty()
                ? hit.title.trim() : "أغنية";
        String artist = hit.artist != null && !hit.artist.trim().isEmpty()
                ? hit.artist.trim() : "من الإنترنت";
        SavedMusicTrack track = new SavedMusicTrack(
                "yt_" + videoId,
                musicUrl,
                null,
                title,
                artist,
                hit.thumbnailUrl);
        saveLocalTrack(track);
        return true;
    }

    /** Play uploaded/server or direct internet audio URL (synced to the room via ExoPlayer). */
    private void playRemoteMusicUrl(@Nullable String url, @Nullable String title,
                                    @Nullable String artist) {
        if (roomId == null || url == null || url.trim().isEmpty()) return;
        String musicUrl = url.trim();
        if (isYoutubeMusicUrl(musicUrl)) {
            playYoutubeMusic(youtubeIdFromUrl(musicUrl), title, artist);
            return;
        }
        if (musicUrl.toLowerCase(java.util.Locale.ROOT).contains("youtube.com")
                || musicUrl.toLowerCase(java.util.Locale.ROOT).contains("youtu.be")) {
            Toast.makeText(this,
                    "استخدم تبويب بحث بالإنترنت لاختيار الأغنية",
                    Toast.LENGTH_LONG).show();
            return;
        }
        clearDismissedMusic();
        if (canManageMusic) RoomRtcEngine.getInstance().stopLocalMusic();
        preparedMusicUrl = null;
        currentMusicUrl = musicUrl;
        currentMusicStatus = "playing";
        String safeTitle = title != null && !title.trim().isEmpty() ? title.trim() : "موسيقى الإنترنت";
        String safeArtist = artist != null && !artist.trim().isEmpty() ? artist.trim() : "من الإنترنت";
        binding.tvMusicTitle.setText(safeTitle);
        binding.tvMusicArtist.setText(safeArtist);
        binding.btnMusicPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        startMusicDiscAnimation();
        viewModel.updateMusic(roomId, "load", musicUrl, safeTitle, safeArtist, 0L);
        musicPanelExpanded = true;
        binding.musicCard.setVisibility(View.VISIBLE);
        binding.musicCard.bringToFront();
        showMusicReopenChip(false);
    }

    private void playYoutubeMusic(@Nullable String videoId, @Nullable String title,
                                  @Nullable String artist) {
        if (roomId == null || videoId == null || videoId.isEmpty()) return;
        String musicUrl = "yt://" + videoId;
        clearDismissedMusic();
        preparedMusicUrl = null;
        pendingYtResolveKey = null;
        currentMusicUrl = musicUrl;
        currentMusicStatus = "playing";
        String safeTitle = title != null && !title.trim().isEmpty() ? title.trim() : "أغنية";
        String safeArtist = artist != null && !artist.trim().isEmpty() ? artist.trim() : "من الإنترنت";
        binding.tvMusicTitle.setText(safeTitle);
        binding.tvMusicArtist.setText(safeArtist);
        binding.btnMusicPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        startMusicDiscAnimation();
        musicPanelExpanded = true;
        binding.musicCard.setVisibility(View.VISIBLE);
        binding.musicCard.bringToFront();
        showMusicReopenChip(false);
        viewModel.updateMusic(roomId, "load", musicUrl, safeTitle, safeArtist, 0L);
        streamYoutubeAudioOnly(musicUrl, true, false, 0L, null, safeTitle, safeArtist);
    }

    private void showMusicPicker() {
        showSavedMusicLibrary();
    }

    private List<SavedMusicTrack> savedMusicTracks() {
        String raw = getSharedPreferences("room_music_local_v2", MODE_PRIVATE)
                .getString("tracks", "[]");
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<
                    List<SavedMusicTrack>>() {}.getType();
            List<SavedMusicTrack> tracks = new com.google.gson.Gson().fromJson(raw, type);
            if (tracks == null) return new ArrayList<>();
            // Keep playable local files + saved internet (yt://) picks.
            List<SavedMusicTrack> keep = new ArrayList<>();
            for (SavedMusicTrack t : tracks) {
                if (t == null) continue;
                if (isYoutubeMusicUrl(t.url)) {
                    keep.add(t);
                    continue;
                }
                if (t.localPath != null && new File(t.localPath).exists()) {
                    keep.add(t);
                } else if (t.url != null && (t.url.startsWith("/uploads/")
                        || t.url.startsWith("http://")
                        || t.url.startsWith("https://"))) {
                    keep.add(t);
                }
            }
            return keep;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void saveLocalTrack(SavedMusicTrack track) {
        List<SavedMusicTrack> tracks = savedMusicTracks();
        tracks.removeIf(item -> item != null && (
                (track.id != null && track.id.equals(item.id))
                        || (track.url != null && track.url.equals(item.url))));
        tracks.add(0, track);
        if (tracks.size() > 40) tracks = new ArrayList<>(tracks.subList(0, 40));
        cacheMusicTracks(tracks);
    }

    private void cacheMusicTracks(List<SavedMusicTrack> tracks) {
        getSharedPreferences("room_music_local_v2", MODE_PRIVATE)
                .edit()
                .putString("tracks", new com.google.gson.Gson().toJson(tracks))
                .apply();
    }

    @Nullable
    private SavedMusicTrack findLocalTrackByUrl(@Nullable String url) {
        if (url == null) return null;
        for (SavedMusicTrack t : savedMusicTracks()) {
            if (t != null && url.equals(t.url)) return t;
        }
        return null;
    }

    private void showSavedMusicLibrary() {
        List<SavedMusicTrack> tracks = savedMusicTracks();
        displayMusicLibrary(tracks);
    }

    private void styleInternetAddButton(@Nullable TextView play, boolean alreadyAdded) {
        if (play == null) return;
        play.setText(alreadyAdded ? R.string.music_internet_added : R.string.music_internet_add);
        play.setTextColor(alreadyAdded ? 0xFF8E8E93 : 0xFFFE2C55);
        play.setBackgroundResource(alreadyAdded
                ? R.drawable.bg_music_add_btn_done
                : R.drawable.bg_music_add_btn);
    }

    private void clipRounded(@Nullable View view, float radiusPx) {
        if (view == null) return;
        view.setClipToOutline(true);
        final float r = radiusPx;
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, Math.max(1, v.getWidth()), Math.max(1, v.getHeight()), r);
            }
        });
        view.addOnLayoutChangeListener((v, l, t, rgt, b, ol, ot, or, ob) -> v.invalidateOutline());
    }

    private void displayMusicLibrary(List<SavedMusicTrack> tracks) {
        if (!canManageMusic) {
            AuraDialogHelper.message(this, "موسيقى الغرفة",
                    "يسمع الجميع الأغنية من بث المضيف. التحكم للمضيف فقط.");
            return;
        }
        final List<SavedMusicTrack> safeTracks = new ArrayList<>(
                tracks != null ? tracks : new ArrayList<>());
        // Light JEHO theme (not Mikoo black fullscreen) + system bar insets.
        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        View root = getLayoutInflater().inflate(R.layout.activity_music_library, null);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setStatusBarColor(0xFFF7F7F7);
            dialog.getWindow().setNavigationBarColor(0xFFFFFFFF);
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        View pageMine = root.findViewById(R.id.pageMine);
        View pageHot = root.findViewById(R.id.pageHot);
        View pageInternet = root.findViewById(R.id.pageInternet);
        TextView tabMine = root.findViewById(R.id.tvMusicTabMine);
        TextView tabHot = root.findViewById(R.id.tvMusicTabHot);
        TextView tabInternet = root.findViewById(R.id.tvMusicTabInternet);
        View indMine = root.findViewById(R.id.indMine);
        View indHot = root.findViewById(R.id.indHot);
        View indInternet = root.findViewById(R.id.indInternet);
        View flMenu = root.findViewById(R.id.fl_menu);
        View llMenu = root.findViewById(R.id.ll_menu);
        View llLocal = root.findViewById(R.id.ll_localMusic);
        View back = root.findViewById(R.id.iv_back);
        // Vector ic_music_nav_back is autoMirrored — correct for AR (RTL) and EN (LTR).
        if (back instanceof ImageView) {
            ImageView backIv = (ImageView) back;
            backIv.setImageResource(R.drawable.ic_music_nav_back);
            backIv.setScaleX(1f);
            backIv.setScaleY(1f);
            backIv.setRotation(0f);
        }

        View listWrap = pageMine != null ? pageMine.findViewById(R.id.ll_musicList) : null;
        View emptyMine = pageMine != null ? pageMine.findViewById(R.id.ll_searchEmpty) : null;
        TextView tvTotal = pageMine != null ? pageMine.findViewById(R.id.tv_total) : null;
        RecyclerView rvMine = pageMine != null ? pageMine.findViewById(R.id.rv_musicList) : null;
        View emptyHot = pageHot != null ? pageHot.findViewById(R.id.ll_searchEmpty) : null;
        RecyclerView rvHot = pageHot != null ? pageHot.findViewById(R.id.rv_serverMusicList) : null;
        EditText searchMine = pageMine != null ? pageMine.findViewById(R.id.et_search) : null;
        EditText searchHot = pageHot != null ? pageHot.findViewById(R.id.et_search) : null;

        EditText etInternet = pageInternet != null
                ? pageInternet.findViewById(R.id.et_internetSearch) : null;
        TextView btnInternetGo = pageInternet != null
                ? pageInternet.findViewById(R.id.tv_internetSearchGo) : null;
        ProgressBar pbInternet = pageInternet != null
                ? pageInternet.findViewById(R.id.pb_internetSearch) : null;
        View emptyInternet = pageInternet != null
                ? pageInternet.findViewById(R.id.ll_internetEmpty) : null;
        RecyclerView rvInternet = pageInternet != null
                ? pageInternet.findViewById(R.id.rv_internetMusic) : null;
        final List<RoomDtos.InternetMusicHitDto> internetHits = new ArrayList<>();

        TextView tvMusicName = root.findViewById(R.id.tv_musicName);
        ImageView ivPlay = root.findViewById(R.id.iv_musicPlayStatus);
        if (tvMusicName != null) {
            tvMusicName.setText(currentMusicUrl != null && !currentMusicUrl.isEmpty()
                    ? (binding.tvMusicTitle.getText() != null
                    ? binding.tvMusicTitle.getText().toString() : "…")
                    : getString(R.string.music_list_play_empty_tips1));
        }
        if (ivPlay != null) {
            ivPlay.setOnClickListener(v -> {
                if (binding != null) binding.btnMusicPlayPause.performClick();
            });
        }

        final int tabOn = 0xFF1A1A1A;
        final int tabOff = 0xFF9F9F9F;
        java.util.function.Consumer<TextView> markTab = tv -> {
            if (tabMine != null) {
                tabMine.setTextColor(tv == tabMine ? tabOn : tabOff);
                tabMine.setTypeface(tv == tabMine
                        ? android.graphics.Typeface.DEFAULT_BOLD
                        : android.graphics.Typeface.DEFAULT);
            }
            if (tabHot != null) {
                tabHot.setTextColor(tv == tabHot ? tabOn : tabOff);
                tabHot.setTypeface(tv == tabHot
                        ? android.graphics.Typeface.DEFAULT_BOLD
                        : android.graphics.Typeface.DEFAULT);
            }
            if (tabInternet != null) {
                tabInternet.setTextColor(tv == tabInternet ? tabOn : tabOff);
                tabInternet.setTypeface(tv == tabInternet
                        ? android.graphics.Typeface.DEFAULT_BOLD
                        : android.graphics.Typeface.DEFAULT);
            }
            if (indMine != null) {
                indMine.setVisibility(tv == tabMine ? View.VISIBLE : View.INVISIBLE);
            }
            if (indHot != null) {
                indHot.setVisibility(tv == tabHot ? View.VISIBLE : View.INVISIBLE);
            }
            if (indInternet != null) {
                indInternet.setVisibility(tv == tabInternet ? View.VISIBLE : View.INVISIBLE);
            }
        };
        final List<SavedMusicTrack> visibleMine = new ArrayList<>();
        final Runnable[] refreshMineRef = new Runnable[1];
        Runnable bindMineList = () -> {
            safeTracks.clear();
            safeTracks.addAll(savedMusicTracks());
            String q = searchMine != null && searchMine.getText() != null
                    ? searchMine.getText().toString().trim() : "";
            visibleMine.clear();
            for (SavedMusicTrack t : safeTracks) {
                if (t == null) continue;
                if (musicTrackMatchesQuery(t.title, t.artist, q)) visibleMine.add(t);
            }
            boolean emptyAll = safeTracks.isEmpty();
            boolean emptyVisible = visibleMine.isEmpty();
            if (emptyMine != null) {
                emptyMine.setVisibility(emptyAll || emptyVisible ? View.VISIBLE : View.GONE);
            }
            if (listWrap != null) {
                listWrap.setVisibility(emptyAll || emptyVisible ? View.GONE : View.VISIBLE);
            }
            if (tvTotal != null) {
                if (!q.isEmpty()) {
                    tvTotal.setText(getString(R.string.music_tab_mine)
                            + " · " + visibleMine.size() + " / " + safeTracks.size());
                } else {
                    tvTotal.setText(getString(R.string.music_tab_mine) + " · " + safeTracks.size());
                }
            }
            if (rvMine == null) return;
            if (rvMine.getLayoutManager() == null) {
                rvMine.setLayoutManager(new LinearLayoutManager(this));
            }
            if (rvMine.getAdapter() == null) {
                rvMine.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(
                            @NonNull ViewGroup parent, int viewType) {
                        View row = getLayoutInflater().inflate(
                                R.layout.list_item_local_music, parent, false);
                        return new RecyclerView.ViewHolder(row) {};
                    }

                    @Override
                    public void onBindViewHolder(
                            @NonNull RecyclerView.ViewHolder holder, int position) {
                        SavedMusicTrack track = visibleMine.get(position);
                        TextView name = holder.itemView.findViewById(R.id.tv_musicName);
                        TextView singer = holder.itemView.findViewById(R.id.tv_singerName);
                        ImageView cover = holder.itemView.findViewById(R.id.iv_musicAlbumCover);
                        ImageView more = holder.itemView.findViewById(R.id.iv_musicAdd);
                        ImageView added = holder.itemView.findViewById(R.id.iv_musicAdded);
                        boolean current = track != null && track.url != null
                                && track.url.equals(currentMusicUrl);
                        boolean fromNet = track != null && isYoutubeMusicUrl(track.url);
                        if (name != null) {
                            name.setText(track != null && track.title != null
                                    ? track.title : "أغنية");
                        }
                        if (singer != null) {
                            if (fromNet) {
                                String who = track.artist != null && !track.artist.isEmpty()
                                        ? track.artist : "من الإنترنت";
                                singer.setText(who);
                            } else {
                                singer.setText(track != null && track.artist != null
                                        && !track.artist.isEmpty()
                                        ? track.artist : "فنان غير معروف");
                            }
                        }
                        if (cover != null) {
                            clipRounded(cover, dp(10));
                            if (fromNet && track.thumbnailUrl != null
                                    && !track.thumbnailUrl.isEmpty()) {
                                Glide.with(cover).load(track.thumbnailUrl)
                                        .centerCrop()
                                        .placeholder(R.drawable.bg_music_cover_rounded)
                                        .into(cover);
                            } else if (track != null && track.thumbnailUrl != null
                                    && !track.thumbnailUrl.isEmpty()) {
                                Object model = track.thumbnailUrl.startsWith("content:")
                                        || track.thumbnailUrl.startsWith("http")
                                        ? track.thumbnailUrl
                                        : new File(track.thumbnailUrl);
                                Glide.with(cover).load(model)
                                        .centerCrop()
                                        .placeholder(R.drawable.bg_music_disc)
                                        .error(R.drawable.bg_music_disc)
                                        .into(cover);
                            } else {
                                cover.setImageResource(R.drawable.bg_music_disc);
                            }
                        }
                        if (added != null) {
                            added.setVisibility(current ? View.VISIBLE : View.GONE);
                        }
                        if (more != null) {
                            more.setVisibility(View.VISIBLE);
                            more.setImageResource(R.drawable.ic_music_more);
                            more.clearColorFilter();
                            more.setOnClickListener(v -> showLocalTrackActions(
                                    v, safeTracks, track, () -> {
                                        if (refreshMineRef[0] != null) refreshMineRef[0].run();
                                    }, () -> dialog.dismiss()));
                        }
                        holder.itemView.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (track != null) playLocalTrack(track);
                        });
                        holder.itemView.setOnLongClickListener(v -> {
                            View anchor = more != null ? more : v;
                            showLocalTrackActions(anchor, safeTracks, track, () -> {
                                if (refreshMineRef[0] != null) refreshMineRef[0].run();
                            }, () -> dialog.dismiss());
                            return true;
                        });
                    }

                    @Override
                    public int getItemCount() {
                        return visibleMine.size();
                    }
                });
            } else {
                rvMine.getAdapter().notifyDataSetChanged();
            }
        };
        refreshMineRef[0] = bindMineList;
        Runnable showMine = () -> {
            if (pageMine != null) pageMine.setVisibility(View.VISIBLE);
            if (pageHot != null) pageHot.setVisibility(View.GONE);
            if (pageInternet != null) pageInternet.setVisibility(View.GONE);
            markTab.accept(tabMine);
            bindMineList.run();
        };
        final List<DeviceMusicScanner.Track> visibleHot = new ArrayList<>();
        final Runnable[] refreshHotRef = new Runnable[1];
        Runnable bindHotList = () -> {
            List<DeviceMusicScanner.Track> items = cachedDeviceMusicTracks != null
                    ? cachedDeviceMusicTracks : Collections.emptyList();
            String q = searchHot != null && searchHot.getText() != null
                    ? searchHot.getText().toString().trim() : "";
            visibleHot.clear();
            for (DeviceMusicScanner.Track t : items) {
                if (t == null) continue;
                if (musicTrackMatchesQuery(t.title, t.artist, q)) visibleHot.add(t);
            }
            boolean empty = visibleHot.isEmpty();
            if (emptyHot != null) emptyHot.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (rvHot != null) {
                rvHot.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (rvHot.getLayoutManager() == null) {
                    rvHot.setLayoutManager(new LinearLayoutManager(this));
                }
                if (rvHot.getAdapter() == null) {
                    rvHot.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(
                                @NonNull ViewGroup parent, int viewType) {
                            View row = getLayoutInflater().inflate(
                                    R.layout.list_item_local_music, parent, false);
                            return new RecyclerView.ViewHolder(row) {};
                        }

                        @Override
                        public void onBindViewHolder(
                                @NonNull RecyclerView.ViewHolder holder, int position) {
                            DeviceMusicScanner.Track track = visibleHot.get(position);
                            TextView name = holder.itemView.findViewById(R.id.tv_musicName);
                            TextView singer = holder.itemView.findViewById(R.id.tv_singerName);
                            ImageView cover = holder.itemView.findViewById(R.id.iv_musicAlbumCover);
                            ImageView more = holder.itemView.findViewById(R.id.iv_musicAdd);
                            ImageView added = holder.itemView.findViewById(R.id.iv_musicAdded);
                            TextView time = holder.itemView.findViewById(R.id.tv_musicTotalTime);
                            if (name != null) {
                                name.setText(track != null ? track.title : "أغنية");
                            }
                            if (singer != null) {
                                singer.setText(track != null && track.artist != null
                                        && !track.artist.isEmpty()
                                        ? track.artist : "من الجهاز");
                            }
                            if (added != null) added.setVisibility(View.GONE);
                            if (more != null) {
                                more.setVisibility(View.VISIBLE);
                                more.setImageResource(R.drawable.ic_music_add);
                                more.clearColorFilter();
                                more.setOnClickListener(v -> {
                                    if (track == null) return;
                                    dialog.dismiss();
                                    addLocalMusicAndPlay(
                                            track.contentUri, track.title, track.artist,
                                            track.albumArtUri);
                                });
                            }
                            if (time != null && track != null && track.durationMs > 0) {
                                long sec = track.durationMs / 1000L;
                                time.setVisibility(View.VISIBLE);
                                time.setText(String.format(Locale.US, "%d:%02d",
                                        sec / 60L, sec % 60L));
                            } else if (time != null) {
                                time.setVisibility(View.GONE);
                            }
                            if (cover != null) {
                                clipRounded(cover, dp(10));
                                Object model = track != null && track.albumArtUri != null
                                        ? track.albumArtUri : R.drawable.bg_music_disc;
                                try {
                                    Glide.with(cover)
                                            .load(model)
                                            .centerCrop()
                                            .placeholder(R.drawable.bg_music_disc)
                                            .error(R.drawable.bg_music_disc)
                                            .into(cover);
                                } catch (Exception e) {
                                    cover.setImageResource(R.drawable.bg_music_disc);
                                }
                            }
                            holder.itemView.setOnClickListener(v -> {
                                if (track == null) return;
                                dialog.dismiss();
                                addLocalMusicAndPlay(
                                        track.contentUri, track.title, track.artist,
                                        track.albumArtUri);
                            });
                        }

                        @Override
                        public int getItemCount() {
                            return visibleHot.size();
                        }
                    });
                } else {
                    rvHot.getAdapter().notifyDataSetChanged();
                }
            }
        };
        refreshHotRef[0] = bindHotList;
        Runnable loadDeviceHot = () -> {
            if (emptyHot != null) emptyHot.setVisibility(View.VISIBLE);
            if (rvHot != null) rvHot.setVisibility(View.GONE);
            AppContainer c = ContainerProvider.from(this);
            c.getIoExecutor().execute(() -> {
                List<DeviceMusicScanner.Track> deviceTracks = DeviceMusicScanner.scan(this);
                runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    cachedDeviceMusicTracks = deviceTracks != null
                            ? new ArrayList<>(deviceTracks) : new ArrayList<>();
                    bindHotList.run();
                });
            });
        };
        Runnable showHot = () -> {
            if (pageMine != null) pageMine.setVisibility(View.GONE);
            if (pageHot != null) pageHot.setVisibility(View.VISIBLE);
            if (pageInternet != null) pageInternet.setVisibility(View.GONE);
            markTab.accept(tabHot);
            // "موسيقى شعبية" = device library (MediaStore) — not YouTube, not a new screen.
            if (!PermissionHelper.hasDeviceMusicPermission(this)) {
                if (emptyHot != null) emptyHot.setVisibility(View.VISIBLE);
                if (rvHot != null) rvHot.setVisibility(View.GONE);
                pendingDeviceMusicPermissionAction = () -> {
                    if (dialog.isShowing()) loadDeviceHot.run();
                };
                deviceMusicPermissionLauncher.launch(PermissionHelper.deviceMusicPermissions());
                return;
            }
            if (cachedDeviceMusicTracks != null && !cachedDeviceMusicTracks.isEmpty()) {
                bindHotList.run();
            } else {
                loadDeviceHot.run();
            }
        };
        Runnable bindInternetList = () -> {
            boolean empty = internetHits.isEmpty();
            if (emptyInternet != null) emptyInternet.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (rvInternet != null) {
                rvInternet.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (rvInternet.getLayoutManager() == null) {
                    rvInternet.setLayoutManager(new LinearLayoutManager(this));
                }
                rvInternet.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(
                            @NonNull ViewGroup parent, int viewType) {
                        View row = getLayoutInflater().inflate(
                                R.layout.list_item_internet_music, parent, false);
                        return new RecyclerView.ViewHolder(row) {};
                    }

                    @Override
                    public void onBindViewHolder(
                            @NonNull RecyclerView.ViewHolder holder, int position) {
                        RoomDtos.InternetMusicHitDto hit = internetHits.get(position);
                        ImageView cover = holder.itemView.findViewById(R.id.imgInternetCover);
                        TextView title = holder.itemView.findViewById(R.id.tvInternetTitle);
                        TextView artist = holder.itemView.findViewById(R.id.tvInternetArtist);
                        TextView duration = holder.itemView.findViewById(R.id.tvInternetDuration);
                        TextView play = holder.itemView.findViewById(R.id.tvInternetPlay);
                        if (title != null) {
                            title.setText(hit != null && hit.title != null ? hit.title : "أغنية");
                        }
                        if (artist != null) {
                            artist.setText(hit != null && hit.artist != null
                                    ? hit.artist : "يوتيوب");
                        }
                        if (duration != null) {
                            if (hit != null && hit.durationSec != null && hit.durationSec > 0) {
                                int sec = hit.durationSec;
                                duration.setText(String.format(java.util.Locale.US,
                                        "%d:%02d", sec / 60, sec % 60));
                                duration.setVisibility(View.VISIBLE);
                            } else {
                                duration.setVisibility(View.GONE);
                            }
                        }
                        if (cover != null) {
                            clipRounded(cover, dp(10));
                            if (hit != null && hit.thumbnailUrl != null
                                    && !hit.thumbnailUrl.isEmpty()) {
                                Glide.with(cover)
                                        .load(hit.thumbnailUrl)
                                        .centerCrop()
                                        .placeholder(R.drawable.bg_music_cover_rounded)
                                        .into(cover);
                            } else {
                                cover.setImageResource(R.drawable.bg_music_disc);
                            }
                        }
                        View.OnClickListener addClick = v -> {
                            if (hit == null) return;
                            String ytUrl = "yt://" + hit.id;
                            boolean already = findLocalTrackByUrl(ytUrl) != null;
                            if (already) {
                                Toast.makeText(VoiceRoomActivity.this,
                                        R.string.music_internet_added, Toast.LENGTH_SHORT).show();
                            } else {
                                addInternetHitToMyMusic(hit);
                                Toast.makeText(VoiceRoomActivity.this,
                                        R.string.music_internet_added_toast,
                                        Toast.LENGTH_SHORT).show();
                            }
                            if (play != null) {
                                styleInternetAddButton(play, true);
                            }
                        };
                        holder.itemView.setOnClickListener(addClick);
                        if (play != null) {
                            boolean already = hit != null
                                    && findLocalTrackByUrl("yt://" + hit.id) != null;
                            styleInternetAddButton(play, already);
                            play.setOnClickListener(addClick);
                        }
                    }

                    @Override
                    public int getItemCount() {
                        return internetHits.size();
                    }
                });
            }
        };
        Runnable runInternetSearch = () -> {
            String q = etInternet != null && etInternet.getText() != null
                    ? etInternet.getText().toString().trim() : "";
            if (q.length() < 2) {
                Toast.makeText(this, "اكتب اسم الأغنية للبحث", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pbInternet != null) pbInternet.setVisibility(View.VISIBLE);
            if (emptyInternet != null) emptyInternet.setVisibility(View.GONE);
            if (rvInternet != null) rvInternet.setVisibility(View.GONE);
            AppContainer c = ContainerProvider.from(this);
            c.getIoExecutor().execute(() -> {
                Result<RoomDtos.InternetMusicSearchDto> r =
                        c.getRoomRepository().musicSearch(q);
                runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    if (pbInternet != null) pbInternet.setVisibility(View.GONE);
                    internetHits.clear();
                    if (r.success && r.data != null && r.data.items != null) {
                        internetHits.addAll(r.data.items);
                    } else if (!r.success) {
                        Toast.makeText(this,
                                r.error != null ? r.error : "تعذر البحث",
                                Toast.LENGTH_SHORT).show();
                    }
                    bindInternetList.run();
                });
            });
        };
        Runnable showInternet = () -> {
            if (pageMine != null) pageMine.setVisibility(View.GONE);
            if (pageHot != null) pageHot.setVisibility(View.GONE);
            if (pageInternet != null) pageInternet.setVisibility(View.VISIBLE);
            markTab.accept(tabInternet);
            bindInternetList.run();
            if (etInternet != null) etInternet.requestFocus();
        };
        View tabMineWrap = root.findViewById(R.id.tabMineWrap);
        View tabHotWrap = root.findViewById(R.id.tabHotWrap);
        View tabInternetWrap = root.findViewById(R.id.tabInternetWrap);
        if (tabMine != null) tabMine.setOnClickListener(v -> showMine.run());
        if (tabHot != null) tabHot.setOnClickListener(v -> showHot.run());
        if (tabInternet != null) tabInternet.setOnClickListener(v -> showInternet.run());
        if (tabMineWrap != null) tabMineWrap.setOnClickListener(v -> showMine.run());
        if (tabHotWrap != null) tabHotWrap.setOnClickListener(v -> showHot.run());
        if (tabInternetWrap != null) tabInternetWrap.setOnClickListener(v -> showInternet.run());
        if (btnInternetGo != null) btnInternetGo.setOnClickListener(v -> runInternetSearch.run());
        if (etInternet != null) {
            etInternet.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    runInternetSearch.run();
                    return true;
                }
                return false;
            });
        }
        showMine.run();

        // No separate "محلي" menu / new screen — device songs are the "موسيقى شعبية" tab.
        if (flMenu != null) flMenu.setVisibility(View.GONE);
        if (llMenu != null) llMenu.setVisibility(View.GONE);
        if (llLocal != null) llLocal.setOnClickListener(null);
        if (back != null) back.setOnClickListener(v -> dialog.dismiss());

        bindMineList.run();
        if (searchMine != null) {
            searchMine.setHint(R.string.music_search_hint);
            searchMine.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    bindMineList.run();
                }
            });
        }
        if (searchHot != null) {
            searchHot.setHint("ابحث في أغاني الجهاز…");
            searchHot.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    if (refreshHotRef[0] != null) refreshHotRef[0].run();
                }
            });
        }
        dialog.show();
    }

    private static boolean musicTrackMatchesQuery(
            @Nullable String title, @Nullable String artist, @Nullable String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        String t = title != null ? title.toLowerCase(java.util.Locale.ROOT) : "";
        String a = artist != null ? artist.toLowerCase(java.util.Locale.ROOT) : "";
        return t.contains(q) || a.contains(q);
    }

    private int indexOfSavedTrack(
            @Nullable List<SavedMusicTrack> tracks, @Nullable SavedMusicTrack track) {
        if (tracks == null || track == null) return -1;
        for (int i = 0; i < tracks.size(); i++) {
            SavedMusicTrack item = tracks.get(i);
            if (item == null) continue;
            if (track.id != null && track.id.equals(item.id)) return i;
            if (track.url != null && track.url.equals(item.url)) return i;
        }
        return -1;
    }

    private void showLocalTrackActions(
            @NonNull View anchor,
            @NonNull List<SavedMusicTrack> tracks,
            @Nullable SavedMusicTrack track,
            @Nullable Runnable onChanged,
            @Nullable Runnable onPlayClose) {
        if (track == null) return;
        final int index = indexOfSavedTrack(tracks, track);
        if (index < 0) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_AuraLive_PopupMenu),
                anchor);
        menu.getMenu().add(0, 0, 0, "تشغيل");
        menu.getMenu().add(0, 1, 0, "تعديل الاسم");
        if (index > 0) menu.getMenu().add(0, 2, 0, "رفع للأعلى");
        if (index < tracks.size() - 1) menu.getMenu().add(0, 3, 0, "إنزال للأسفل");
        menu.getMenu().add(0, 4, 0, "حذف من القائمة");
        menu.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which == 0) {
                if (onPlayClose != null) onPlayClose.run();
                playLocalTrack(track);
                return true;
            }
            if (which == 1) {
                renameLocalTrack(tracks, index, onChanged);
                return true;
            }
            if (which == 2 && index > 0) {
                java.util.Collections.swap(tracks, index, index - 1);
                cacheMusicTracks(tracks);
                Toast.makeText(this, "تم الترتيب", Toast.LENGTH_SHORT).show();
                if (onChanged != null) onChanged.run();
                return true;
            }
            if (which == 3 && index < tracks.size() - 1) {
                java.util.Collections.swap(tracks, index, index + 1);
                cacheMusicTracks(tracks);
                Toast.makeText(this, "تم الترتيب", Toast.LENGTH_SHORT).show();
                if (onChanged != null) onChanged.run();
                return true;
            }
            if (which == 4) {
                if (track.localPath != null && !isYoutubeMusicUrl(track.url)) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(track.localPath).delete();
                }
                tracks.remove(index);
                cacheMusicTracks(tracks);
                if (track.url != null && track.url.equals(currentMusicUrl) && roomId != null) {
                    RoomRtcEngine.getInstance().stopLocalMusic();
                    viewModel.updateMusic(roomId, "stop", null, null, null, 0L);
                }
                Toast.makeText(this, "تم الحذف", Toast.LENGTH_SHORT).show();
                if (onChanged != null) onChanged.run();
                return true;
            }
            return false;
        });
        // No per-item icons — forcing icon slots makes a blank white strip.
        menu.show();
    }

    private void showServerTrackActions(
            @NonNull View anchor,
            @Nullable RoomDtos.MusicTrackDto track,
            @Nullable Runnable onPlayClose) {
        if (track == null) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_AuraLive_PopupMenu),
                anchor);
        menu.getMenu().add(0, 0, 0, "تشغيل");
        boolean already = track.url != null && findLocalTrackByUrl(track.url) != null;
        menu.getMenu().add(0, 1, 0, already ? "موجودة في قائمتي" : "إضافة لقائمتي");
        menu.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which == 0) {
                if (onPlayClose != null) onPlayClose.run();
                playRemoteMusicUrl(track.url, track.title, track.artist);
                return true;
            }
            if (which == 1) {
                if (already) {
                    Toast.makeText(this, R.string.music_internet_added, Toast.LENGTH_SHORT).show();
                } else if (addServerTrackToMyMusic(track)) {
                    Toast.makeText(this, R.string.music_internet_added_toast, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private boolean addServerTrackToMyMusic(@Nullable RoomDtos.MusicTrackDto track) {
        if (track == null || track.url == null || track.url.trim().isEmpty()) return false;
        String musicUrl = track.url.trim();
        if (findLocalTrackByUrl(musicUrl) != null) return false;
        String title = track.title != null && !track.title.trim().isEmpty()
                ? track.title.trim() : "أغنية";
        String artist = track.artist != null && !track.artist.trim().isEmpty()
                ? track.artist.trim()
                : (track.uploadedBy != null ? track.uploadedBy : "المكتبة");
        String id = "srv_" + Integer.toHexString(musicUrl.hashCode());
        saveLocalTrack(new SavedMusicTrack(id, musicUrl, null, title, artist, null));
        return true;
    }

    private void renameLocalTrack(
            List<SavedMusicTrack> tracks, int index, @Nullable Runnable onChanged) {
        SavedMusicTrack track = tracks.get(index);
        if (track == null) return;
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(track.title);
        input.setSelectAllOnFocus(true);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("تعديل اسم الأغنية")
                .setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    String name = input.getText() != null ? input.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    track.title = name;
                    cacheMusicTracks(tracks);
                    if (track.url != null && track.url.equals(currentMusicUrl)) {
                        binding.tvMusicTitle.setText(name);
                    }
                    Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show();
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void renameLocalTrack(List<SavedMusicTrack> tracks, int index) {
        renameLocalTrack(tracks, index, null);
    }

    private void skipMusicTrack() {
        List<SavedMusicTrack> tracks = savedMusicTracks();
        skipMusicTrack(tracks);
    }

    private void skipMusicTrack(List<SavedMusicTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            // Fall back to server/popular playlist if local list is empty.
            if (cachedServerMusicTracks != null && !cachedServerMusicTracks.isEmpty()) {
                skipServerMusicTrack(cachedServerMusicTracks);
                return;
            }
            showMusicPicker();
            return;
        }
        int current = -1;
        for (int i = 0; i < tracks.size(); i++) {
            SavedMusicTrack track = tracks.get(i);
            if (track != null && track.url != null && track.url.equals(currentMusicUrl)) {
                current = i;
                break;
            }
        }
        // Try next playable track (local file or saved internet).
        for (int step = 1; step <= tracks.size(); step++) {
            int next = (current + step) % tracks.size();
            SavedMusicTrack candidate = tracks.get(next);
            if (candidate == null) continue;
            if (isYoutubeMusicUrl(candidate.url)) {
                playLocalTrack(candidate);
                return;
            }
            if (candidate.localPath != null && new File(candidate.localPath).exists()) {
                playLocalTrack(candidate);
                return;
            }
        }
        if (cachedServerMusicTracks != null && !cachedServerMusicTracks.isEmpty()) {
            skipServerMusicTrack(cachedServerMusicTracks);
            return;
        }
        Toast.makeText(this, "لا توجد أغنية تالية", Toast.LENGTH_SHORT).show();
    }

    private void skipServerMusicTrack(List<RoomDtos.MusicTrackDto> serverTracks) {
        if (serverTracks == null || serverTracks.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < serverTracks.size(); i++) {
            RoomDtos.MusicTrackDto t = serverTracks.get(i);
            if (t != null && t.url != null && t.url.equals(currentMusicUrl)) {
                current = i;
                break;
            }
        }
        int next = (current + 1) % serverTracks.size();
        RoomDtos.MusicTrackDto track = serverTracks.get(next);
        if (track != null) {
            playRemoteMusicUrl(track.url, track.title, track.artist);
        }
    }

    private void showRoomViewersSheet() {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_room_viewers, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.tvViewersTitle);
        TextView empty = sheet.findViewById(R.id.tvViewersEmpty);
        ImageView btnSearch = sheet.findViewById(R.id.btnViewersSearch);
        View rowSearch = sheet.findViewById(R.id.rowViewersSearch);
        android.widget.EditText etSearch = sheet.findViewById(R.id.etViewersSearch);
        TextView btnCancel = sheet.findViewById(R.id.btnViewersSearchCancel);
        androidx.recyclerview.widget.RecyclerView recycler = sheet.findViewById(R.id.recyclerViewers);

        java.util.ArrayList<JsonObject> allRows = new java.util.ArrayList<>();
        java.util.ArrayList<JsonObject> rows = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        List<JsonObject> source = !roomMembersAll.isEmpty() ? roomMembersAll : roomAudience;
        for (JsonObject m : source) {
            if (m == null || !m.has("userId") || m.get("userId").isJsonNull()) continue;
            String uid = m.get("userId").getAsString();
            if (uid == null || uid.isEmpty() || !seen.add(uid)) continue;
            allRows.add(m);
        }
        if (currentSeats != null) {
            for (RoomDtos.SeatDto seat : currentSeats) {
                if (seat == null || seat.user == null || seat.user.id == null) continue;
                if (!seen.add(seat.user.id)) continue;
                JsonObject row = new JsonObject();
                row.addProperty("userId", seat.user.id);
                row.addProperty("displayName", seat.user.displayName != null
                        ? seat.user.displayName : seat.user.username);
                row.addProperty("avatarUrl", seat.user.avatarUrl);
                row.addProperty("frameUrl",
                        isAgencyRoom
                                ? firstNonEmpty(seat.user.hostBadgeUrl, seat.user.vipBadgeUrl)
                                : seat.user.vipBadgeUrl);
                row.addProperty("vipLevel", seat.user.vipLevel);
                row.addProperty("userLevel", seat.user.level);
                allRows.add(row);
            }
        }
        RoomDtos.RoomDto liveRoom = viewModel != null ? viewModel.getRoom().getValue() : null;
        if (liveRoom != null && liveRoom.host != null && liveRoom.host.id != null
                && seen.add(liveRoom.host.id)) {
            JsonObject row = new JsonObject();
            row.addProperty("userId", liveRoom.host.id);
            row.addProperty("displayName", liveRoom.host.displayName != null
                    ? liveRoom.host.displayName : liveRoom.host.username);
            row.addProperty("avatarUrl", liveRoom.host.avatarUrl);
            row.addProperty("frameUrl",
                    isAgencyRoom
                            ? firstNonEmpty(liveRoom.host.hostBadgeUrl, liveRoom.host.vipBadgeUrl)
                            : liveRoom.host.vipBadgeUrl);
            row.addProperty("vipLevel", liveRoom.host.vipLevel);
            row.addProperty("userLevel", liveRoom.host.level);
            allRows.add(row);
        }
        rows.addAll(allRows);
        title.setText("مستخدمين متصلين على النت · " + rows.size());

        final Runnable[] refreshRef = new Runnable[1];
        androidx.recyclerview.widget.RecyclerView.Adapter<?> listAdapter =
                new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_room_viewer, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(
                    @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                JsonObject m = rows.get(position);
                final String uid = memberStr(m, "userId");
                String rawName = memberStr(m, "displayName");
                if (rawName == null || rawName.isEmpty()) rawName = memberStr(m, "username");
                if (rawName == null || rawName.isEmpty()) rawName = "عضو";
                final String name = rawName;
                final String avatar = memberStr(m, "avatarUrl");
                final String frame = memberStr(m, "frameUrl");
                final int vip = m.has("vipLevel") && !m.get("vipLevel").isJsonNull()
                        ? m.get("vipLevel").getAsInt() : 0;
                final int level = m.has("userLevel") && !m.get("userLevel").isJsonNull()
                        ? m.get("userLevel").getAsInt() : 1;

                TextView tvName = holder.itemView.findViewById(R.id.tvViewerName);
                TextView tvMeta = holder.itemView.findViewById(R.id.tvViewerMeta);
                ImageView img = holder.itemView.findViewById(R.id.imgViewerAvatar);
                ImageView imgFrame = holder.itemView.findViewById(R.id.imgViewerFrame);
                tvName.setText(name);
                String role = (roomHostId != null && roomHostId.equals(uid)) ? "المضيف"
                        : (roomCohostId != null && roomCohostId.equals(uid)) ? "مساعد"
                        : (myUserId != null && myUserId.equals(uid)) ? "أنت"
                        : "مشاهد";
                String meta = role;
                if (vip > 0) meta += " · VIP" + vip;
                else meta += " · Lv." + Math.max(1, level);
                tvMeta.setText(meta);
                AvatarCosmetics.bindStacked(img, imgFrame, avatar, frame);
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (uid != null && !uid.isEmpty()) {
                        showUserCard(uid, name, avatar,
                                frame,
                                isAgencyRoom ? frame : null,
                                vip, Math.max(1, level));
                    }
                });
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }
        };

        Runnable applyFilter = () -> {
            String q = etSearch != null && etSearch.getText() != null
                    ? etSearch.getText().toString().trim().toLowerCase(Locale.US) : "";
            rows.clear();
            if (q.isEmpty()) {
                rows.addAll(allRows);
            } else {
                for (JsonObject m : allRows) {
                    String n = memberStr(m, "displayName");
                    if (n == null || n.isEmpty()) n = memberStr(m, "username");
                    if (n != null && n.toLowerCase(Locale.US).contains(q)) rows.add(m);
                }
            }
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
            title.setText("مستخدمين متصلين على النت · " + rows.size());
            listAdapter.notifyDataSetChanged();
        };
        refreshRef[0] = applyFilter;

        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (!rows.isEmpty()) {
            int maxListH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.48f);
            ViewGroup.LayoutParams rlp = recycler.getLayoutParams();
            rlp.height = Math.min(maxListH, Math.max(dp(70) * rows.size(), dp(70)));
            if (rows.size() > 6) rlp.height = maxListH;
            recycler.setLayoutParams(rlp);
        }
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recycler.setAdapter(listAdapter);

        if (btnSearch != null && rowSearch != null) {
            btnSearch.setOnClickListener(v -> {
                rowSearch.setVisibility(View.VISIBLE);
                btnSearch.setVisibility(View.GONE);
                    if (etSearch != null) {
                        etSearch.requestFocus();
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager)
                                        getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(etSearch,
                                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
            });
        }
        if (btnCancel != null && rowSearch != null && btnSearch != null) {
            btnCancel.setOnClickListener(v -> {
                rowSearch.setVisibility(View.GONE);
                btnSearch.setVisibility(View.VISIBLE);
                if (etSearch != null) etSearch.setText("");
                applyFilter.run();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && etSearch != null) {
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
            });
        }
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    applyFilter.run();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
        dialog.show();
    }

    private static String memberStr(JsonObject m, String key) {
        if (m == null || !m.has(key) || m.get(key).isJsonNull()) return null;
        try {
            return m.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int memberInt(JsonObject object, String key, int fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long memberLong(JsonObject object, String key, long fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float memberFloat(JsonObject object, String key, float fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsFloat() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean memberBool(JsonObject object, String key, boolean fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String shortUserId(String userId) {
        if (userId == null || userId.isEmpty()) return "مستخدم";
        return userId.substring(0, Math.min(6, userId.length()));
    }

    private void sendSeatReaction(String emojiKey, int emojiResId) {
        if (myUserId == null || roomId == null) return;
        if (!roomMicInteractEnabled()) {
            Toast.makeText(this, withOnOff(R.string.room_more_mic_interact, false), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SeatReactionEmojis.isAllowed(emojiKey)) return;
        int allowedRes = SeatReactionEmojis.drawableForKey(emojiKey);
        if (allowedRes == 0) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("emojiKey", emojiKey.toLowerCase(java.util.Locale.US));
        payload.addProperty("userId", myUserId);
        boolean emitted = RealtimeClient.getInstance()
                .emitRoomEvent(roomId, "room:reaction", payload);
        if (!emitted) {
            Toast.makeText(this,
                    "تعذر إرسال التفاعل: الاتصال اللحظي غير متاح",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        showRoomReaction(myUserId, emojiKey, allowedRes);
    }

    private void showRoomReaction(String userId, String emojiKey, int emojiResId) {
        if (userId == null || userId.isEmpty() || emojiResId == 0) return;
        // Prefer the guest-grid seat when the user is sitting there (incl. room host hopping).
        boolean onGuest = seatAdapter != null && seatAdapter.isUserOnGuestSeat(userId);
        if (onGuest) {
            boolean shown = seatAdapter.showReaction(userId, emojiKey, emojiResId, 3000L);
            if (!shown) {
                // Seat list may still be refreshing — retry once so remotes see the sticker.
                handler.postDelayed(() -> {
                    if (seatAdapter != null
                            && seatAdapter.showReaction(userId, emojiKey, emojiResId, 3000L)) {
                        return;
                    }
                    if (isBossCardReactionUser(userId)) {
                        showHeaderHostReaction(userId, emojiKey, emojiResId);
                    }
                }, 350L);
            }
            return;
        }
        // Boss card / seat 0 only — never the hidden hostStage overlay.
        if (isBossCardReactionUser(userId)) {
            showHeaderHostReaction(userId, emojiKey, emojiResId);
        } else if (seatAdapter != null) {
            // Not yet in adapter snapshot — retry then fall back to header if host.
            handler.postDelayed(() -> {
                if (seatAdapter != null && seatAdapter.isUserOnGuestSeat(userId)) {
                    seatAdapter.showReaction(userId, emojiKey, emojiResId);
                } else if (isBossCardReactionUser(userId)) {
                    showHeaderHostReaction(userId, emojiKey, emojiResId);
                }
            }, 350L);
        }
    }

    /** Room host identity on the header card (or still occupying seat 0). */
    private boolean isBossCardReactionUser(String userId) {
        if (userId == null || userId.isEmpty()) return false;
        if (roomHostId != null && sameUser(userId, roomHostId)) return true;
        if (hostStageUserId != null && sameUser(userId, hostStageUserId)) return true;
        RoomDtos.SeatDto hostSeat = findHostSeat(currentSeats);
        return sameUser(userId, seatUserId(hostSeat));
    }

    private void showHeaderHostReaction(String userId, String emojiKey, int emojiResId) {
        if (binding == null || !SeatReactionEmojis.isAllowed(emojiKey)) return;
        ImageView target = binding.imgRoomHostReaction;
        if (target == null) target = binding.imgHostStageReaction;
        if (target == null) return;
        if (hostReactionHide != null) handler.removeCallbacks(hostReactionHide);
        hostReactionUserId = userId;
        String uri = SeatReactionEmojis.assetUriForKey(emojiKey);
        target.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (uri != null) {
            if (SeatReactionEmojis.isWebpKey(emojiKey)) {
                Glide.with(target)
                        .load(uri)
                        .fitCenter()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .error(emojiResId != 0 ? emojiResId : SeatReactionEmojis.FALLBACK_DRAWABLE)
                        .into(target);
            } else {
                Glide.with(target)
                        .asGif()
                        .load(uri)
                        .fitCenter()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .error(emojiResId != 0 ? emojiResId : SeatReactionEmojis.FALLBACK_DRAWABLE)
                        .into(target);
            }
        } else {
            target.setImageResource(
                    emojiResId != 0 ? emojiResId : SeatReactionEmojis.FALLBACK_DRAWABLE);
        }
        target.setVisibility(View.VISIBLE);
        target.bringToFront();
        target.animate().cancel();
        target.setAlpha(0f);
        target.setScaleX(0.55f);
        target.setScaleY(0.55f);
        target.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                .start();
        final ImageView hideTarget = target;
        hostReactionHide = () -> clearHeaderHostReaction(hideTarget);
        handler.postDelayed(hostReactionHide, 3000L);
    }

    private void clearHeaderHostReaction(@Nullable ImageView target) {
        if (hostReactionHide != null) {
            handler.removeCallbacks(hostReactionHide);
            hostReactionHide = null;
        }
        hostReactionUserId = null;
        ImageView view = target;
        if (view == null && binding != null) {
            view = binding.imgRoomHostReaction != null
                    ? binding.imgRoomHostReaction : binding.imgHostStageReaction;
        }
        if (view == null) return;
        view.animate().cancel();
        Glide.with(getApplicationContext()).clear(view);
        view.setVisibility(View.GONE);
    }

    /** @deprecated path kept for clearHostStageReaction call sites */
    private void clearHostStageReaction() {
        clearHeaderHostReaction(null);
    }

    private Runnable luckyFloatPulse;
    private Runnable luckyFloatHide;
    private Runnable luckyWinHide;
    private boolean luckyFloatDismissed;

    private void setupLuckyBoxUi() {
        if (binding.luckyFloatWrap == null) return;
        binding.luckyFloatWrap.setOnClickListener(v -> openLuckyBox());
        binding.luckyFloatWrap.setVisibility(View.GONE);
        enableLuckyFloatDrag(binding.luckyFloatWrap);
        if (binding.btnLuckyFloatClose != null) {
            binding.btnLuckyFloatClose.setOnClickListener(v -> {
                hideLuckyFloat();
                luckyFloatDismissed = true;
            });
        }
        if (binding.luckyWinOverlay != null) {
            binding.luckyWinOverlay.setOnClickListener(v -> hideLuckyWinOverlay());
        }
        refreshLuckyFloatAvailability();
    }

    private void enableLuckyFloatDrag(View floatView) {
        final float[] last = new float[2];
        final boolean[] dragging = {false};
        floatView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    last[0] = event.getRawX();
                    last[1] = event.getRawY();
                    dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - last[0];
                    float dy = event.getRawY() - last[1];
                    if (!dragging[0] && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
                        dragging[0] = true;
                    }
                    if (dragging[0]) {
                        v.setTranslationX(v.getTranslationX() + dx);
                        v.setTranslationY(v.getTranslationY() + dy);
                        last[0] = event.getRawX();
                        last[1] = event.getRawY();
                        return true;
                    }
                    return false;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        dragging[0] = false;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private void refreshLuckyFloatAvailability() {
        if (binding == null || binding.luckyFloatWrap == null) return;
        if (luckyFloatDismissed) {
            hideLuckyFloat();
            return;
        }
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.List<MiscDtos.LuckyBoxDto>> listResult =
                    ApiCall.execute(c.getLuckyBoxesApi().list());
            boolean show = false;
            if (listResult.success && listResult.data != null) {
                for (MiscDtos.LuckyBoxDto box : listResult.data) {
                    if (box != null && box.isActive
                            && "free_daily".equalsIgnoreCase(box.kind)
                            && box.remainingToday > 0) {
                        show = true;
                        break;
                    }
                }
            }
            final boolean visible = show;
            runOnUiThread(() -> {
                if (binding == null || binding.luckyFloatWrap == null) return;
                if (visible) {
                    binding.luckyFloatWrap.setVisibility(View.VISIBLE);
                    startLuckyFloatPulse();
                    if (luckyFloatHide != null) handler.removeCallbacks(luckyFloatHide);
                    luckyFloatHide = this::hideLuckyFloat;
                    handler.postDelayed(luckyFloatHide, 60_000L);
                } else {
                    hideLuckyFloat();
                }
            });
        });
    }

    private void hideLuckyFloat() {
        if (binding != null && binding.luckyFloatWrap != null) {
            binding.luckyFloatWrap.setVisibility(View.GONE);
        }
        stopLuckyFloatPulse();
        if (luckyFloatHide != null) handler.removeCallbacks(luckyFloatHide);
        luckyFloatHide = null;
    }

    private void startLuckyFloatPulse() {
        stopLuckyFloatPulse();
        if (binding == null || binding.imgLuckyFloat == null) return;
        luckyFloatPulse = new Runnable() {
            @Override
            public void run() {
                if (binding == null || binding.imgLuckyFloat == null
                        || binding.luckyFloatWrap == null
                        || binding.luckyFloatWrap.getVisibility() != View.VISIBLE) return;
                View box = binding.imgLuckyFloat;
                box.animate().cancel();
                box.setScaleX(1f);
                box.setScaleY(1f);
                box.animate().scaleX(1.08f).scaleY(1.08f).setDuration(420)
                        .withEndAction(() -> {
                            if (binding != null && binding.imgLuckyFloat != null) {
                                binding.imgLuckyFloat.animate().scaleX(1f).scaleY(1f).setDuration(420).start();
                            }
                        }).start();
                handler.postDelayed(this, 2800);
            }
        };
        handler.postDelayed(luckyFloatPulse, 800);
    }

    private void stopLuckyFloatPulse() {
        if (luckyFloatPulse != null) handler.removeCallbacks(luckyFloatPulse);
        luckyFloatPulse = null;
    }

    private void showLuckyFloatAgain() {
        luckyFloatDismissed = false;
        refreshLuckyFloatAvailability();
    }

    private void showLuckyWinOverlay(String openerName, String rewardLabel) {
        String title = (openerName != null && !openerName.isEmpty() ? openerName : "مستخدم")
                + " · صندوق الحظ";
        // Premium 3D PNG burst — no Lottie / DotLottie.
        RewardBurstOverlay.showLuckyWin(this,
                openerName != null ? openerName : "",
                rewardLabel != null ? rewardLabel : "جائزة");
        if (binding != null && binding.luckyWinOverlay != null) {
            try {
                if (binding.lottieLuckyWin != null) {
                    binding.lottieLuckyWin.cancelAnimation();
                    binding.lottieLuckyWin.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
            }
            binding.luckyWinOverlay.setVisibility(View.GONE);
        }
        appendChatLine("النظام", title + " · " + (rewardLabel != null ? rewardLabel : ""), 0, 1);
    }

    private void hideLuckyWinOverlay() {
        if (binding == null || binding.luckyWinOverlay == null) return;
        if (luckyWinHide != null) handler.removeCallbacks(luckyWinHide);
        luckyWinHide = null;
        try { binding.lottieLuckyWin.cancelAnimation(); } catch (Exception ignored) {}
        binding.luckyWinOverlay.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> {
                    if (binding != null && binding.luckyWinOverlay != null) {
                        binding.luckyWinOverlay.setVisibility(View.GONE);
                    }
                }).start();
    }

    private void showEmojiPicker() {
        Dialog dialog = new Dialog(this, R.style.MikooBottomPanelDialog);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_emoji_picker, null);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.dimAmount = 0.45f;
            window.setAttributes(lp);
            window.setNavigationBarColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.color_gift_dialog_bg));
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
        }

        String[] stickerKeys = SeatReactionEmojis.KEYS;
        int[] stickerRes = SeatReactionEmojis.DRAWABLES;
        String[] emojis = {
                "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎",
                "😍", "😘", "🥰", "😗", "😙", "😚", "🙂", "🤗", "🤩", "🤔", "🤨", "😐",
                "😏", "😣", "😥", "😮", "🤐", "😯", "😪", "😫", "🥱", "😴", "😌", "😛",
                "😜", "😝", "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲", "☹️", "🙁",
                "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯", "😬",
                "😰", "😱", "🥵", "🥶", "😳", "🤪", "😵", "😡", "😠", "🤬", "😷", "🤒",
                "👍", "👎", "👏", "🙌", "👐", "🤝", "🙏", "💪", "✌️", "🤞", "🤟", "🤘",
                "👌", "🤌", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋",
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
                "💞", "💓", "💗", "💖", "💘", "💝", "💟", "🔥", "✨", "⭐", "🌟", "💫",
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🎯", "🎮", "🎵", "🎶", "🌹", "🌸",
                "☕", "🍕", "🍔", "🍟", "🍰", "🎂", "🍩", "🍪", "🍎", "🍓", "🍒", "🍉"
        };

        RecyclerView stickersRv = sheet.findViewById(R.id.recyclerStickers);
        // Horizontal scroll — stickers side-by-side (animated GIFs from assets/emoji).
        stickersRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        stickersRv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji_sticker, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageView img = holder.itemView.findViewById(R.id.imgSticker);
                img.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                final String key = stickerKeys[position];
                final int resId = stickerRes[position];
                String uri = SeatReactionEmojis.assetUriForKey(key);
                if (uri != null && SeatReactionEmojis.isWebpKey(key)) {
                    com.bumptech.glide.Glide.with(img)
                            .load(uri)
                            .fitCenter()
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .error(resId)
                            .into(img);
                } else {
                    com.bumptech.glide.Glide.with(img)
                            .asGif()
                            .load(uri != null ? uri : resId)
                            .fitCenter()
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .error(resId)
                            .into(img);
                }
                holder.itemView.setOnClickListener(v -> {
                    if (!isOnSeat(currentSeats)) {
                        Toast.makeText(VoiceRoomActivity.this,
                                "اجلس على المايك أولاً لإرسال رد الفعل", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendSeatReaction(key, resId);
                    dialog.dismiss();
                });
            }
            @Override public int getItemCount() { return stickerRes.length; }
        });
        stickersRv.setNestedScrollingEnabled(false);

        RecyclerView emojiRv = sheet.findViewById(R.id.recyclerEmojis);
        emojiRv.setLayoutManager(new GridLayoutManager(this, 8));
        emojiRv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji_cell, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TextView tv = holder.itemView.findViewById(R.id.tvEmoji);
                tv.setText(emojis[position]);
                holder.itemView.setOnClickListener(v -> insertChatEmoji(emojis[position]));
            }
            @Override public int getItemCount() { return emojis.length; }
        });
        AuraDialogHelper.ownVerticalScroll(emojiRv);
        dialog.show();
    }

    private void showSoundEffectsPicker() {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_sound_effects, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        sheet.findViewById(R.id.sfxClap).setOnClickListener(v -> {
            dialog.dismiss();
            RoomSoundFx.playClap(this);
            appendChatLine("النظام", "تصفيق", 0, 1);
        });
        sheet.findViewById(R.id.sfxGift).setOnClickListener(v -> {
            dialog.dismiss();
            RoomSoundFx.playGift(this);
            appendChatLine("النظام", "مؤثر هدية", 0, 1);
        });
        sheet.findViewById(R.id.sfxJoin).setOnClickListener(v -> {
            dialog.dismiss();
            RoomSoundFx.playJoin(this);
            appendChatLine("النظام", "مؤثر انضمام", 0, 1);
        });
        sheet.findViewById(R.id.sfxMute).setOnClickListener(v -> {
            dialog.dismiss();
            RoomSoundFx.playMute(this);
            appendChatLine("النظام", "مؤثر كتم", 0, 1);
        });
        dialog.show();
    }

    private void insertChatEmoji(String emoji) {
        openRoomChatComposer(true);
        CharSequence cur = binding.etChat.getText();
        binding.etChat.setText((cur != null ? cur.toString() : "") + emoji);
        if (binding.etChat.getText() != null) {
            binding.etChat.setSelection(binding.etChat.getText().length());
        }
    }

    private RoomDtos.SeatDto findMySeat(List<RoomDtos.SeatDto> seats) {
        if (myUserId == null || seats == null) return null;
        RoomDtos.SeatDto best = null;
        for (RoomDtos.SeatDto s : seats) {
            if (s == null || !sameUser(myUserId, seatUserId(s))) continue;
            if (best == null || s.seatIndex < best.seatIndex) best = s;
        }
        return best;
    }

    private boolean isUserSeated(String userId) {
        if (userId == null || currentSeats == null) return false;
        for (RoomDtos.SeatDto seat : currentSeats) {
            if (seat != null && sameUser(userId, seatUserId(seat))) return true;
        }
        return false;
    }

    private void translateRoomComment(String text) {
        if (text == null || text.trim().isEmpty()) return;
        Toast.makeText(this, R.string.translating, Toast.LENGTH_SHORT).show();
        String target = java.util.Locale.getDefault().getLanguage();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("text", text);
            body.put("targetLang", target);
            Result<java.util.Map<String, Object>> r = ApiCall.execute(c.getConfigApi().translate(body));
            runOnUiThread(() -> {
                if (!r.success || r.data == null) {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Object translated = r.data.get("translated");
                String out = translated != null ? String.valueOf(translated) : text;
                AuraDialogHelper.message(this, getString(R.string.translate), out);
            });
        });
    }

    private void showUserCard(String userId, String name, String avatarUrl, String frameUrl,
                              int vipLevel, int userLevel) {
        showUserCard(userId, name, avatarUrl, frameUrl, null, vipLevel, userLevel);
    }

    private void bindVipUserCardHead(@Nullable ImageView vipHead, int vipLevel,
                                     @Nullable String headUrl) {
        if (vipHead == null) return;
        if (vipLevel <= 0) {
            vipHead.setVisibility(View.GONE);
            vipHead.setImageDrawable(null);
            return;
        }
        String path = headUrl != null && !headUrl.isEmpty()
                ? headUrl
                : VipStyle.fixedHeadPath(vipLevel);
        if (path == null || path.isEmpty()) {
            vipHead.setVisibility(View.GONE);
            return;
        }
        vipHead.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.ServerAssets.load(vipHead, path);
    }

    private void bindVipUserCardMedal(
            @Nullable ImageView medal,
            int vipLevel,
            @Nullable String levelBadgeUrl) {
        if (medal == null) return;
        if (vipLevel <= 0) {
            medal.setVisibility(View.GONE);
            medal.setImageDrawable(null);
            return;
        }
        String path = levelBadgeUrl;
        if (path == null || path.isEmpty() || !path.contains("vip_medal")) {
            int t = Math.min(7, Math.max(1, vipLevel));
            path = "/assets/cosmetics/vip/vip_medal_mikoo_" + t + ".png";
        }
        medal.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.ServerAssets.load(medal, path);
    }

    private void styleMikooStatChips(@Nullable TextView charm, @Nullable TextView wealth) {
        // Compact digit chips like Mikoo (heart / crown rows).
        if (charm != null) {
            charm.setTextColor(0xFFFFFFFF);
        }
        if (wealth != null) {
            wealth.setTextColor(0xFFFFFFFF);
        }
    }

    private void bindUserCardAgency(
            @Nullable View rowAgency,
            @Nullable ImageView logoView,
            @Nullable TextView nameView,
            @Nullable TextView gidView,
            @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto.AgencySnip agency) {
        bindUserCardAgency(rowAgency, logoView, nameView, gidView, null, null, agency);
    }

    private void bindUserCardAgency(
            @Nullable View rowAgency,
            @Nullable ImageView logoView,
            @Nullable TextView nameView,
            @Nullable TextView gidView,
            @Nullable ImageView bannerBg,
            @Nullable TextView levelView,
            @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto.AgencySnip agency) {
        if (rowAgency == null) return;
        if (agency == null || agency.name == null || agency.name.trim().isEmpty()) {
            rowAgency.setVisibility(View.GONE);
            return;
        }
        rowAgency.setVisibility(View.VISIBLE);
        if (nameView != null) nameView.setText(agency.name.trim());
        if (gidView != null) {
            String gid = agency.publicId != null ? agency.publicId.trim() : "";
            gidView.setVisibility(View.VISIBLE);
            gidView.setText(gid.isEmpty() ? "GID: —" : ("GID:" + gid));
        }
        int tier = agency.level > 0
                ? agency.level
                : com.Dramizo.Series.util.AgencyUi.bannerTierFromDiamonds(agency.totalDiamonds);
        if (bannerBg == null && rowAgency != null) {
            bannerBg = rowAgency.findViewById(R.id.imgAgencyBannerBg);
        }
        if (levelView == null && rowAgency != null) {
            levelView = rowAgency.findViewById(R.id.tvAgencyLevel);
        }
        com.Dramizo.Series.util.AgencyUi.bindBanner(bannerBg, levelView, tier);
        if (logoView != null) {
            com.Dramizo.Series.util.AgencyUi.bindLogo(
                    logoView,
                    agency.logoUrl,
                    firstNonEmpty(agency.coverUrl,
                            isAgencyRoom ? currentRoomCoverUrl : null));
        }
        // Open family info card (Mikoo guild homepage) — follow agency lives there.
        rowAgency.setOnClickListener(v -> {
            if (agency.id == null || agency.id.isEmpty()) return;
            try {
                com.Dramizo.Series.presentation.common.AgencyFamilyInfoSheet.show(
                        this,
                        agency.id,
                        agency.logoUrl,
                        firstNonEmpty(agency.coverUrl,
                                isAgencyRoom ? currentRoomCoverUrl : null));
            } catch (Exception ignored) {
            }
        });
    }

    private void showUserCard(String userId, String name, String avatarUrl, String frameUrl,
                              String hostBadgeUrl, int vipLevel, int userLevel) {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_room_user_card, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            AuraDialogHelper.configureShown(dialog);
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) bs.setBackgroundResource(android.R.color.transparent);
        });
        TextView tvName = sheet.findViewById(R.id.tvUserName);
        TextView tvUserId = sheet.findViewById(R.id.tvUserId);
        ImageView btnCopyId = sheet.findViewById(R.id.btnCopyId);
        ImageView imgCountryFlag = sheet.findViewById(R.id.imgCountryFlag);
        ImageView imgGender = sheet.findViewById(R.id.imgGender);
        TextView tvOnlineStatus = sheet.findViewById(R.id.tvOnlineStatus);
        TextView tvStatFollowing = sheet.findViewById(R.id.tvStatFollowing);
        TextView tvStatFans = sheet.findViewById(R.id.tvStatFans);
        TextView tvStatVisitors = sheet.findViewById(R.id.tvStatVisitors);
        TextView tvPropFamily = sheet.findViewById(R.id.tvPropFamily);
        TextView tvPropSupporters = sheet.findViewById(R.id.tvPropSupportersCount);
        TextView tvPropFriends = sheet.findViewById(R.id.tvPropFriendsCount);
        View propFamily = sheet.findViewById(R.id.propFamily);
        ImageView imgVipMedal = sheet.findViewById(R.id.imgVipMedal);
        ImageView imgEntryRide = sheet.findViewById(R.id.imgEntryRide);
        ImageView img = sheet.findViewById(R.id.imgUserAvatar);
        ImageView frame = sheet.findViewById(R.id.imgUserFrame);
        ImageView vipHead = sheet.findViewById(R.id.imgVipHead);
        View rowAgency = sheet.findViewById(R.id.rowAgency);
        ImageView imgAgencyLogo = sheet.findViewById(R.id.imgAgencyLogo);
        TextView tvAgencyName = sheet.findViewById(R.id.tvAgencyName);
        TextView tvAgencyGid = sheet.findViewById(R.id.tvAgencyGid);
        View userCardBg = sheet.findViewById(R.id.userCardBg);
        View moreActionsBlock = sheet.findViewById(R.id.moreActionsBlock);
        TextView chipVip = sheet.findViewById(R.id.chipVip);
        TextView chipMember = sheet.findViewById(R.id.chipMember);
        TextView chipCharm = sheet.findViewById(R.id.chipCharm);
        TextView chipWealth = sheet.findViewById(R.id.chipWealth);
        TextView chipFirstDay = sheet.findViewById(R.id.chipFirstDay);
        TextView btnHi = sheet.findViewById(R.id.btnHi);
        View rowHostTasks = sheet.findViewById(R.id.rowHostTasks);
        final String[] publicIdHold = { "" };
        tvName.setText(name != null ? name : "مستخدم");
        AvatarCosmetics.styleUserCardBadges(
                chipVip, chipMember, chipCharm, chipWealth, vipLevel, userLevel, 0L, 0L);
        if (chipMember != null) {
            chipMember.setText(String.valueOf(Math.max(1, userLevel)));
        }
        styleMikooStatChips(chipCharm, chipWealth);
        if (userId != null && !userId.isEmpty()) {
            com.Dramizo.Series.presentation.common.UserSocialNav.wireUserCard(
                    this, sheet, userId, myUserId, dialog::dismiss);
        }
        com.Dramizo.Series.util.AgencyUi.applyUserCardSheet(userCardBg, moreActionsBlock, vipLevel);
        bindVipUserCardMedal(imgVipMedal, vipLevel, null);
        ImageView hostBadge = sheet.findViewById(R.id.imgUserHostBadge);
        // In-room profile card: always fixed VIP nobility frame (ud_vip_tou_N) when VIP.
        // Mall/SVGA wear stays on seat mics via wearFrameUrl — not here.
        final String[] chatVipFrame = {
                VipStyle.profileNobilityFrameUrl(vipLevel, null)
        };
        bindVipUserCardHead(vipHead, vipLevel, null);
        AvatarCosmetics.bindStacked(img, frame, avatarUrl, chatVipFrame[0]);
        // Agency room: host signal only as overlay; VIP frame still fixed ud_vip_tou.
        if (isAgencyRoom) {
            AvatarCosmetics.applyHostWear(frame, hostBadge, img, chatVipFrame[0], hostBadgeUrl, null, null);
        } else {
            AvatarCosmetics.applyHostWear(frame, hostBadge, img, chatVipFrame[0], null, null, null);
        }
        if (rowAgency != null) rowAgency.setVisibility(View.GONE);
        if (btnCopyId != null) {
            btnCopyId.setOnClickListener(v -> {
                String pid = publicIdHold[0];
                if (pid == null || pid.isEmpty()) return;
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ID", pid));
                    Toast.makeText(this, "تم نسخ المعرف", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (userId != null && !userId.isEmpty()) {
            ContainerProvider.from(this).getIoExecutor().execute(() -> {
                String myId = ContainerProvider.from(this).getSessionManager().getUserId();
                if (myId != null && !myId.equals(userId)) {
                    try {
                        ContainerProvider.from(this).getUserApi().visit(userId).execute();
                    } catch (Exception ignored) {
                    }
                }
                Result<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> r =
                        ApiCall.execute(ContainerProvider.from(this).getUserApi().getUser(userId));
                if (!r.success || r.data == null) return;
                runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    int liveVip = Math.max(0, r.data.vipLevel);
                    chatVipFrame[0] = VipStyle.profileNobilityFrameUrl(liveVip, r.data.vipTouUrl);
                    AvatarCosmetics.bindAvatar(img,
                            r.data.avatarUrl != null ? r.data.avatarUrl : avatarUrl);
                    AvatarCosmetics.applyHostWear(
                            frame,
                            hostBadge,
                            img,
                            chatVipFrame[0],
                            isAgencyRoom ? r.data.hostBadgeUrl : null,
                            null,
                            isAgencyRoom ? r.data.hostBadgeMeta : null);
                    bindVipUserCardHead(vipHead, liveVip,
                            r.data.vipHeadUrl != null ? r.data.vipHeadUrl
                                    : VipStyle.fixedHeadPath(liveVip));
                    // Head owns: entry ride + VIP medal.
                    if (imgEntryRide != null) {
                        String ride = r.data.entryAnimationUrl != null
                                && !r.data.entryAnimationUrl.isEmpty()
                                ? r.data.entryAnimationUrl
                                : r.data.entryEffectUrl;
                        if (ride != null && !ride.trim().isEmpty()) {
                            String preview = ride.trim();
                            if (preview.endsWith(".mp4") || preview.endsWith(".svga")) {
                                int dot = preview.lastIndexOf('.');
                                if (dot > 0) preview = preview.substring(0, dot) + ".png";
                            }
                            imgEntryRide.setVisibility(View.VISIBLE);
                            try {
                                com.bumptech.glide.Glide.with(imgEntryRide.getContext())
                                        .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(preview))
                                        .error(R.drawable.ic_medal_default)
                                        .into(imgEntryRide);
                            } catch (Exception ignored) {
                                imgEntryRide.setImageResource(R.drawable.ic_medal_default);
                            }
                        } else {
                            imgEntryRide.setVisibility(View.GONE);
                        }
                    }
                    bindVipUserCardMedal(imgVipMedal, liveVip, r.data.levelBadgeUrl);
                    bindUserCardAgency(rowAgency, imgAgencyLogo, tvAgencyName, tvAgencyGid, r.data.agency);
                    com.Dramizo.Series.util.AgencyUi.applyUserCardSheet(
                            userCardBg, moreActionsBlock, liveVip);
                    if (r.data.displayName != null && !r.data.displayName.isEmpty()) {
                        tvName.setText(r.data.displayName);
                    }
                    com.Dramizo.Series.util.GenderVerifiedBadge.bind(
                            tvName, null, r.data.genderVerified);
                    if (imgGender != null) {
                        String g = r.data.gender != null ? r.data.gender.trim().toLowerCase() : "";
                        if ("male".equals(g) || "m".equals(g)) {
                            imgGender.setVisibility(View.VISIBLE);
                            imgGender.setImageResource(R.drawable.ic_gender_male);
                        } else if ("female".equals(g) || "f".equals(g)) {
                            imgGender.setVisibility(View.VISIBLE);
                            imgGender.setImageResource(R.drawable.ic_gender_female);
                        } else {
                            imgGender.setVisibility(View.GONE);
                        }
                    }
                    if (tvOnlineStatus != null) {
                        if (Boolean.TRUE.equals(r.data.isOnline)) {
                            tvOnlineStatus.setVisibility(View.VISIBLE);
                            tvOnlineStatus.setText(R.string.online_now);
                            tvOnlineStatus.setTextColor(0xFF7CFFB2);
                        } else {
                            tvOnlineStatus.setVisibility(View.GONE);
                        }
                    }
                    if (tvStatFollowing != null) {
                        tvStatFollowing.setText(String.valueOf(Math.max(0, r.data.followersCount)));
                    }
                    if (tvStatFans != null) {
                        tvStatFans.setText(String.valueOf(Math.max(0, r.data.followingCount)));
                    }
                    if (tvStatVisitors != null) {
                        tvStatVisitors.setText(String.valueOf(Math.max(0, r.data.visitorsCount)));
                    }
                    if (tvPropFriends != null) {
                        tvPropFriends.setText(String.valueOf(Math.max(0, r.data.friendsCount)));
                    }
                    if (tvPropSupporters != null) {
                        tvPropSupporters.setText(String.valueOf(Math.max(0, r.data.followersCount)));
                    }
                    if (tvPropFamily != null) {
                        if (r.data.agency != null && r.data.agency.name != null
                                && !r.data.agency.name.isEmpty()) {
                            tvPropFamily.setText(r.data.agency.name.trim());
                        } else {
                            tvPropFamily.setText(R.string.user_card_prop_family);
                        }
                    }
                    com.Dramizo.Series.presentation.common.UserSocialNav.bindFamilyClick(
                            VoiceRoomActivity.this,
                            propFamily,
                            r.data.agency != null ? r.data.agency.id : null,
                            dialog::dismiss);
                    if (tvUserId != null) {
                        String pid = r.data.publicId != null ? r.data.publicId.trim() : "";
                        publicIdHold[0] = pid;
                        if (!pid.isEmpty()) {
                            tvUserId.setVisibility(View.VISIBLE);
                            tvUserId.setText("ID:" + pid);
                            if (btnCopyId != null) btnCopyId.setVisibility(View.VISIBLE);
                        } else {
                            tvUserId.setVisibility(View.GONE);
                            if (btnCopyId != null) btnCopyId.setVisibility(View.GONE);
                        }
                    }
                    if (imgCountryFlag != null) {
                        com.Dramizo.Series.util.FlagImages.bind(imgCountryFlag, r.data.country);
                    }
                    long popularity = Math.max(
                            Math.max(0, r.data.popularityLevel),
                            Math.max(0, Math.max(r.data.charmScore, r.data.popularityScore)));
                    AvatarCosmetics.styleUserCardBadges(
                            chipVip, chipMember, chipCharm, chipWealth,
                            liveVip, Math.max(1, r.data.level),
                            popularity, Math.max(0, r.data.wealthScore));
                    if (chipMember != null) {
                        chipMember.setText(String.valueOf(Math.max(1, r.data.level)));
                    }
                    if (chipCharm != null) {
                        long popLv = r.data.popularityLevel > 0 ? r.data.popularityLevel : popularity;
                        chipCharm.setText(String.valueOf(Math.max(0, popLv)));
                    }
                    if (chipWealth != null) {
                        long wl = r.data.wealthLevel > 0 ? r.data.wealthLevel : r.data.wealthScore;
                        chipWealth.setText(String.valueOf(Math.max(0, wl)));
                    }
                    styleMikooStatChips(chipCharm, chipWealth);
                    boolean showHi = r.data.showHiBadge || r.data.isFirstDay;
                    if (chipFirstDay != null) {
                        chipFirstDay.setVisibility(showHi ? View.VISIBLE : View.GONE);
                    }
                    if (btnHi != null) {
                        btnHi.setVisibility(showHi ? View.VISIBLE : View.GONE);
                    }
                    boolean showHostTasks = isHost
                            && com.Dramizo.Series.util.TasksFeature.isEnabled(this)
                            && r.data.isNewMale
                            && userId != null && !userId.equals(myUserId);
                    if (rowHostTasks != null) {
                        rowHostTasks.setVisibility(showHostTasks ? View.VISIBLE : View.GONE);
                    }
                    View taskInvite = sheet.findViewById(R.id.actTaskInvite);
                    if (taskInvite != null && isHost) {
                        boolean inviteOn = com.Dramizo.Series.util.TasksFeature.isEnabled(this)
                                && r.data.isNewMale
                                && userId != null
                                && !userId.equals(myUserId);
                        taskInvite.setVisibility(inviteOn ? View.VISIBLE : View.GONE);
                    }
                });
            });
        }

        RoomDtos.SeatDto occupiedSeat = null;
        if (userId != null) {
            for (RoomDtos.SeatDto seat : currentSeats) {
                if (seat != null && sameUser(userId, seat.userId)) {
                    occupiedSeat = seat;
                    break;
                }
            }
        }
        final RoomDtos.SeatDto targetSeat = occupiedSeat;
        boolean anotherUser = userId != null && !userId.isEmpty()
                && (myUserId == null || !sameUser(userId, myUserId));
        RoomDtos.RoomDto liveRoom = viewModel != null ? viewModel.getRoom().getValue() : null;
        String liveHostId = resolveLiveHostId(liveRoom);
        boolean protectedHost = userId != null
                && (sameUser(userId, roomHostId)
                || sameUser(userId, roomCohostId)
                || sameUser(userId, liveHostId)
                || (liveRoom != null && sameUser(userId, liveRoom.activeHostId)));
        boolean targetModeratorFlag = false;
        if (userId != null) {
            for (String mid : roomModeratorIds) {
                if (sameUser(userId, mid)) {
                    targetModeratorFlag = true;
                    break;
                }
            }
        }
        final boolean targetModerator = targetModeratorFlag;
        boolean canTarget = anotherUser && !protectedHost && (isOwner || !targetModerator);

        TextView actMute = sheet.findViewById(R.id.actMute);
        if (actMute != null) {
            boolean showMute = canMuteUsers && canTarget && targetSeat != null;
            actMute.setVisibility(showMute ? View.VISIBLE : View.GONE);
            if (showMute) {
                final boolean currentlyMuted = targetSeat.isMuted || targetSeat.isModeratorMuted;
                actMute.setText(currentlyMuted ? "فك كتم المايك" : "كتم المايك");
                actMute.setOnClickListener(v -> {
                    dialog.dismiss();
                    viewModel.setMic(roomId, !currentlyMuted, userId);
                });
            }
        }

        TextView actModerator = sheet.findViewById(R.id.actModerator);
        if (actModerator != null) {
            boolean showModerator = isOwner && anotherUser && !protectedHost;
            actModerator.setVisibility(showModerator ? View.VISIBLE : View.GONE);
            if (showModerator) {
                actModerator.setText(targetModerator ? "إزالة المشرف" : "تعيين مشرف");
                actModerator.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (targetModerator) {
                        viewModel.removeModerator(roomId, userId);
                        Toast.makeText(this, "تمت إزالة المشرف", Toast.LENGTH_SHORT).show();
                    } else {
                        viewModel.addModerator(roomId, userId);
                        Toast.makeText(this,
                                "تم الترقية بتحكم كامل · يمكنك تقييد الصلاحيات",
                                Toast.LENGTH_LONG).show();
                        binding.getRoot().postDelayed(
                                () -> showModeratorPermissionPresets(userId), 400);
                    }
                });
            }
        }

        sheet.findViewById(R.id.actProfile).setOnClickListener(v -> {
            dialog.dismiss();
            if (userId == null || userId.isEmpty()) return;
            com.Dramizo.Series.presentation.common.UserSocialNav.openFullProfile(
                    VoiceRoomActivity.this, userId);
        });
        sheet.findViewById(R.id.actGift).setOnClickListener(v -> {
            dialog.dismiss();
            boolean self = userId != null && userId.equals(myUserId);
            if (self && !canHostGiftSelf()) {
                Toast.makeText(this, R.string.cannot_gift_yourself, Toast.LENGTH_SHORT).show();
                return;
            }
            String target = userId != null ? userId : resolveLiveHostId(
                    viewModel != null ? viewModel.getRoom().getValue() : null);
            if (target == null || target.isEmpty()) target = roomHostId;
            if (target == null || target.isEmpty()) {
                Toast.makeText(this, R.string.choose_gift_receiver, Toast.LENGTH_SHORT).show();
                return;
            }
            final String giftTarget = target;
            // Open gifts after the user-card sheet fully dismisses.
            binding.getRoot().post(() ->
                    GiftBottomSheet.show(getSupportFragmentManager(), giftTarget, null, roomId));
        });
        View actXo = sheet.findViewById(R.id.actXo);
        if (actXo != null) {
            actXo.setVisibility(View.GONE);
        }
        View actInviteMic = sheet.findViewById(R.id.actInviteGuest);
        if (actInviteMic != null) {
            boolean seated = false;
            if (userId != null && currentSeats != null) {
                for (RoomDtos.SeatDto seat : currentSeats) {
                    if (seat != null && userId.equals(seat.userId)) {
                        seated = true;
                        break;
                    }
                }
            }
            boolean canInvite = canInviteMic && userId != null
                    && !userId.equals(myUserId) && !seated;
            actInviteMic.setVisibility(canInvite ? View.VISIBLE : View.GONE);
            actInviteMic.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.inviteSeat(roomId, userId);
            });
        }
        View actTaskInvite = sheet.findViewById(R.id.actTaskInvite);
        if (actTaskInvite != null) {
            // Shown after profile load when target is a new male (agency hostess tasks).
            actTaskInvite.setVisibility(View.GONE);
            actTaskInvite.setOnClickListener(v -> {
                dialog.dismiss();
                viewModel.inviteTaskGuest(roomId, userId);
            });
        }
        if (btnHi != null) {
            btnHi.setOnClickListener(v -> {
                View chat = sheet.findViewById(R.id.actChat);
                if (chat != null) chat.performClick();
            });
        }
        ImageView actFollow = sheet.findViewById(R.id.actFollow);
        if (actFollow != null) {
            // Mikoo icon_attention — solid when following, faded when not.
            final boolean[] following = {false};
            final boolean[] followBusy = {false};
            applyFollowHeart(actFollow, false);
            if (userId != null && !userId.isEmpty() && !userId.equals(myUserId)) {
                AppContainer followContainer = ContainerProvider.from(this);
                followContainer.getIoExecutor().execute(() -> {
                    Result<AuthDtos.UserDto> profile = com.Dramizo.Series.util.ApiCall.execute(
                            followContainer.getUserApi().getUser(userId));
                    if (profile.success && profile.data != null
                            && Boolean.TRUE.equals(profile.data.isFollowing)) {
                        following[0] = true;
                        runOnUiThread(() -> {
                            if (actFollow.getWindowToken() != null) {
                                applyFollowHeart(actFollow, true);
                            }
                        });
                    }
                });
            }
            actFollow.setOnClickListener(v -> {
                if (userId == null || userId.isEmpty()) return;
                if (userId.equals(myUserId)) {
                    Toast.makeText(this, R.string.cannot_follow_yourself, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (followBusy[0]) return;
                followBusy[0] = true;
                actFollow.setEnabled(false);
                final boolean unfollow = following[0];
                applyFollowHeart(actFollow, !unfollow);
                AppContainer container = ContainerProvider.from(this);
                container.getIoExecutor().execute(() -> {
                    try {
                        retrofit2.Response<?> resp = (unfollow
                                ? container.getUserApi().unfollow(userId)
                                : container.getUserApi().follow(userId)).execute();
                        runOnUiThread(() -> {
                            followBusy[0] = false;
                            actFollow.setEnabled(true);
                            if (resp.isSuccessful()) {
                                following[0] = !unfollow;
                                applyFollowHeart(actFollow, following[0]);
                                Toast.makeText(this,
                                        unfollow ? "تم إلغاء المتابعة ✓" : "تمت المتابعة ✓",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                applyFollowHeart(actFollow, following[0]);
                                Toast.makeText(this, getString(R.string.error_generic),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            followBusy[0] = false;
                            actFollow.setEnabled(true);
                            applyFollowHeart(actFollow, following[0]);
                            Toast.makeText(this, getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        }
        View actFriend = sheet.findViewById(R.id.actFriend);
        if (actFriend != null) {
            actFriend.setOnClickListener(v -> {
                dialog.dismiss();
                if (userId == null || userId.isEmpty()) return;
                if (userId.equals(myUserId)) {
                    Toast.makeText(this, R.string.cannot_friend_yourself, Toast.LENGTH_SHORT).show();
                    return;
                }
                AppContainer container = ContainerProvider.from(this);
                container.getIoExecutor().execute(() -> {
                    try {
                        java.util.Map<String, String> body = new java.util.HashMap<>();
                        body.put("type", "friend");
                        retrofit2.Response<?> resp = container.getUserApi().sendRequest(userId, body).execute();
                        runOnUiThread(() -> Toast.makeText(this,
                                resp.isSuccessful() ? "تم إرسال طلب الصداقة ✓" : getString(R.string.error_generic),
                                Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }
        sheet.findViewById(R.id.actChat).setOnClickListener(v -> {
            dialog.dismiss();
            if (userId == null || userId.isEmpty()) return;
            if (userId.equals(myUserId)) {
                Toast.makeText(this, R.string.cannot_message_yourself, Toast.LENGTH_SHORT).show();
                return;
            }
            AppContainer container = ContainerProvider.from(this);
            container.getIoExecutor().execute(() -> {
                java.util.Map<String, String> body = new java.util.HashMap<>();
                body.put("peerId", userId);
                Result<com.Dramizo.Series.data.remote.dto.ChatDtos.ConversationDto> r =
                        com.Dramizo.Series.util.ApiCall.execute(container.getChatApi().create(body));
                runOnUiThread(() -> {
                    if (!r.success || r.data == null || r.data.id == null) {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent i = new Intent(this, com.Dramizo.Series.presentation.chat.ChatConversationActivity.class);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_CONVERSATION_ID, r.data.id);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_PEER_ID, userId);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_TITLE,
                            name != null ? name : "محادثة");
                    if (avatarUrl != null) {
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_AVATAR, avatarUrl);
                    }
                    // Chat is personal context — VIP frame only (never agency host signal).
                    if (chatVipFrame[0] != null && !chatVipFrame[0].isEmpty()) {
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_HOST_BADGE,
                                chatVipFrame[0]);
                    }
                    startActivityKeepingRoom(i);
                });
            });
        });
        sheet.findViewById(R.id.actAt).setOnClickListener(v -> {
            dialog.dismiss();
            String mention = "@" + (name != null ? name : "") + " ";
            focusRoomChatComposer(mention, mention.length());
        });
        sheet.findViewById(R.id.actReport).setOnClickListener(v -> {
            dialog.dismiss();
            showRoomUserReport(userId, name);
        });
        TextView actKick = sheet.findViewById(R.id.actKick);
        TextView actBan = sheet.findViewById(R.id.actBan);
        boolean canModerate = (canKickUsers || canBanUsers) && canTarget;
        TextView actionsTitle = sheet.findViewById(R.id.tvUserActionsTitle);
        actionsTitle.setText(canModerate
                ? "إدارة المستخدم: كتم، طرد أو حظر"
                : "خيارات المستخدم والتفاعل");
        actionsTitle.setTextColor(0xFF374151);
        if (actKick != null) {
            actKick.setVisibility(canModerate && userId != null && !userId.equals(myUserId) ? View.VISIBLE : View.GONE);
            actKick.setText("طرد مؤقت");
            actKick.setOnClickListener(v -> {
                dialog.dismiss();
                showKickAndBanDialog(userId);
            });
        }
        if (actBan != null) {
            boolean showBan = canModerate && userId != null && !userId.equals(myUserId);
            actBan.setVisibility(View.GONE);
            if (showBan) {
                final String targetId = userId;
                AppContainer container = ContainerProvider.from(this);
                container.getIoExecutor().execute(() -> {
                    Result<Boolean> status = container.getRoomRepository().isBanned(roomId, targetId);
                    boolean banned = status.success && Boolean.TRUE.equals(status.data);
                    runOnUiThread(() -> {
                        if (!dialog.isShowing() || actBan == null) return;
                        actBan.setVisibility(banned ? View.VISIBLE : View.GONE);
                        actBan.setText("إلغاء الطرد");
                        actBan.setOnClickListener(v -> {
                            dialog.dismiss();
                            viewModel.unbanUser(roomId, targetId);
                        });
                    });
                });
            }
        }
        android.widget.GridLayout actionsGrid = sheet.findViewById(R.id.userActionsGrid);
        View actInvite = sheet.findViewById(R.id.actInviteGuest);
        View[] priorityActions = {actKick, actBan, actMute, actInvite, actModerator};
        for (int index = priorityActions.length - 1; index >= 0; index--) {
            View action = priorityActions[index];
            if (action != null && action.getParent() == actionsGrid) {
                actionsGrid.removeView(action);
                actionsGrid.addView(action, 0);
            }
        }
        dialog.show();
    }

    private void showKickAndBanDialog(String userId) {
        if (roomId == null || userId == null || userId.isEmpty()) return;
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (canKickUsers) {
            labels.add("إخراج من الغرفة الآن");
            actions.add("kick");
        }
        if (canBanUsers) {
            labels.add("طرد مؤقت ومنع الدخول");
            actions.add("ban");
        }
        if (labels.isEmpty()) return;
        AuraDialogHelper.list(this, "إدارة المستخدم", labels.toArray(new String[0]), which -> {
            if ("kick".equals(actions.get(which))) {
                viewModel.kickUser(roomId, userId, "moderator_kick");
            } else {
                showTimedBanDialog(userId);
            }
        });
    }

    private void showTimedBanDialog(String userId) {
        if (!canBanUsers || roomId == null || userId == null || userId.isEmpty()) return;
        String[] durations = {"10 دقائق", "30 دقيقة", "150 يوماً"};
        int[] minutes = {10, 30, 150 * 24 * 60};
        AuraDialogHelper.list(this, "مدة الطرد", durations, which -> {
            if (which < 0 || which >= minutes.length) return;
            viewModel.banUser(roomId, userId, "moderator_timed_ban", minutes[which]);
        });
    }

    private void showRoomUserReport(String userId, String displayName) {
        if (roomId == null || userId == null || userId.isEmpty()
                || userId.equals(myUserId)) return;
        String[] labels = {"إساءة أو ألفاظ", "مضايقة", "رسائل مزعجة", "انتحال شخصية", "سبب آخر"};
        String[] reasons = {"abuse", "harassment", "spam", "impersonation", "other"};
        AuraDialogHelper.list(this,
                "الإبلاغ عن " + (displayName != null ? displayName : "المستخدم"),
                labels,
                which -> AuraDialogHelper.prompt(
                        this,
                        "تفاصيل البلاغ",
                        "اكتب تفاصيل مختصرة تساعد الإدارة على المراجعة.",
                        "التفاصيل (اختياري)",
                        android.text.InputType.TYPE_CLASS_TEXT
                                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                        "إرسال البلاغ",
                        description -> viewModel.reportUser(
                                roomId, userId, reasons[which], description)));
    }

    private boolean canModerateRoom() {
        // Full room settings / admin panel: host, owner, or explicit canManageRoom only.
        // Presence in moderatorIds alone must NOT unlock room admin for everyone.
        if (isPlatformSuper()) return true;
        if (isPlatformManager()) return true;
        if (isHost || isOwner) return true;
        return canManageRoom;
    }

    /** Soft staff (appointed mods) — used for seat long-press / limited actions. */
    private boolean isRoomStaff() {
        if (isPlatformStaff()) return true;
        if (isHost || isOwner) return true;
        if (myUserId == null) return false;
        if (myUserId.equals(roomHostId) || myUserId.equals(roomCohostId)) return true;
        return roomModeratorIds.contains(myUserId);
    }

    /** Platform manager: mute/kick/ban people in any room. */
    private boolean isPlatformManager() {
        try {
            return com.Dramizo.Series.util.StaffRoleHelper.isManager(
                    ContainerProvider.from(this).getSessionManager().getUser());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Platform super admin (or legacy isAdmin): full room + host-level powers. */
    private boolean isPlatformSuper() {
        try {
            return com.Dramizo.Series.util.StaffRoleHelper.isSuper(
                    ContainerProvider.from(this).getSessionManager().getUser());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isPlatformStaff() {
        return isPlatformSuper() || isPlatformManager();
    }

    private boolean isFreeMicEnabled() {
        try {
            return ContainerProvider.from(this)
                    .getSessionManager()
                    .isMicWithoutHostApprovalFromServer();
        } catch (Exception ignored) {
            return true;
        }
    }

    private void updateAdminControls() {
        if (binding == null) return;
        // Seat settings + mic-request bell stay out of the header (tools / room tab only).
        if (binding.btnHeaderHome != null) {
            binding.btnHeaderHome.setVisibility(View.GONE);
        }
        if (binding.btnPlusWrap != null) {
            binding.btnPlusWrap.setVisibility(View.GONE);
        }
        if (binding.btnPlus != null) {
            binding.btnPlus.setVisibility(View.GONE);
        }
        if (binding.tvSeatRequestBadge != null) {
            binding.tvSeatRequestBadge.setVisibility(View.GONE);
        }
        if (binding.llMusicLibraryChip != null) {
            binding.llMusicLibraryChip.setVisibility(View.GONE);
        }
        // Keep request list warm so tools / host dialogs stay current.
        if (canInviteMic && !isFreeMicEnabled() && roomId != null) {
            viewModel.loadSeatRequests(roomId);
        } else {
            updateSeatRequestBadge(null);
        }
        if (binding.tvRoomViewerCount != null) {
            binding.tvRoomViewerCount.setVisibility(View.VISIBLE);
            binding.tvRoomViewerCount.bringToFront();
        }
    }

    private void updateSeatRequestBadge(List<RoomDtos.SeatRequestDto> list) {
        // Header badge removed — mic requests open from أدوات → طلبات المايك.
        if (binding == null) return;
        if (binding.btnPlusWrap != null) binding.btnPlusWrap.setVisibility(View.GONE);
        if (binding.tvSeatRequestBadge != null) {
            binding.tvSeatRequestBadge.setVisibility(View.GONE);
            int count = list != null ? list.size() : 0;
            if (count > 0) {
                binding.tvSeatRequestBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            }
        }
    }

    private void syncMicUi() {
        if (binding == null || binding.imgMicIcon == null) return;
        // Mic control only while seated (TikTok-style: viewers don't get a mic button).
        boolean seated = isOnSeat(currentSeats);
        if (binding.btnMic != null) {
            binding.btnMic.setVisibility(seated ? View.VISIBLE : View.GONE);
        }
        if (!seated) {
            lastMicUiState = null;
            if (seatAdapter != null) seatAdapter.setSelfMicMuted(false);
            updateHostMuteBadge();
            return;
        }
        if (seatAdapter != null) seatAdapter.setSelfMicMuted(!micOn);
        if (!Objects.equals(lastMicUiState, micOn)) {
            lastMicUiState = micOn;
            binding.imgMicIcon.setImageResource(
                    micOn ? R.drawable.ic_asset_mic_open : R.drawable.ic_asset_mic_close);
            // Keep the professional mic art without a colored circle behind it.
            binding.btnMic.setBackgroundResource(android.R.color.transparent);
        }
        updateHostMuteBadge();
    }

    private void toggleRoomSpeakerMute() {
        roomSpeakerMuted = !roomSpeakerMuted;
        applyRoomSpeakerMute(roomSpeakerMuted);
        syncRoomSpeakerUi();
        Toast.makeText(this,
                roomSpeakerMuted
                        ? "تم كتم أصوات الغرفة — توقف عدّ دقائق زيجو"
                        : "تم تشغيل أصوات الغرفة",
                Toast.LENGTH_SHORT).show();
    }

    private void applyRoomSpeakerMute(boolean muted) {
        RoomSoundFx.setMuted(muted);
        RoomRtcEngine.getInstance().setSpeakerMuted(muted);
        if (roomMusicPlayer != null) {
            if (isYoutubeMusicUrl(currentMusicUrl)) {
                applyHostOrGuestMusicVolume();
            } else {
                roomMusicPlayer.setVolume(muted ? 0f : 1f);
            }
        }
        if (!muted) {
            // Resume pulling seat streams after mute (minutes billing resumes only now).
            activateSeatAudio(currentSeats);
        }
    }

    private void syncRoomSpeakerUi() {
        // Icon lives in the room tools sheet; state is applied on open / tap.
    }

    private void bindRoomSpeakerToolUi(@Nullable ImageView img, @Nullable TextView label) {
        if (img != null) {
            img.setImageResource(
                    roomSpeakerMuted ? R.drawable.ic_room_speaker_mute : R.drawable.ic_hub_sound);
        }
        if (label != null) {
            label.setText(roomSpeakerMuted ? "تشغيل أصوات الغرفة" : "كتم أصوات الغرفة");
        }
    }

    /** Mute badge under room ID / header: room owner mic only — never a guest seat. */
    private void updateHostMuteBadge() {
        updateHeaderMicMuted(currentSeats);
        if (binding != null && binding.imgHostStageMuted != null) {
            binding.imgHostStageMuted.setVisibility(View.GONE);
        }
    }

    private void applyRoomToolbarIcons() {
        if (binding == null) return;
        // Static room icons come from activity_voice_room.xml — only mic state is dynamic.
        if (binding.imgMicIcon != null) {
            binding.imgMicIcon.setImageResource(
                    micOn ? R.drawable.ic_asset_mic_open : R.drawable.ic_asset_mic_close);
            binding.btnMic.setBackgroundResource(android.R.color.transparent);
        }
        syncMicUi();
    }

    private void bumpRoomViewers(int delta) {
        if (binding == null || binding.tvViewers == null) return;
        CharSequence cur = binding.tvViewers.getText();
        int n = 1;
        if (cur != null) {
            String digits = cur.toString().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try { n = Integer.parseInt(digits); } catch (Exception ignored) { }
            }
        }
        setRoomViewerCount(Math.max(0, n + delta));
    }

    private void setRoomViewerCount(int count) {
        if (binding == null) return;
        int n = Math.max(0, count);
        String value = String.valueOf(n);
        if (binding.tvViewers != null) setTextIfChanged(binding.tvViewers, value);
        if (binding.tvRoomViewerCount != null) {
            setTextIfChanged(binding.tvRoomViewerCount, getString(R.string.room_viewers_label, n));
            binding.tvRoomViewerCount.setVisibility(View.VISIBLE);
        }
        if (binding.tvAudienceCount != null) {
            setTextIfChanged(binding.tvAudienceCount, value);
            binding.tvAudienceCount.setVisibility(View.VISIBLE);
        }
        if (binding.rowAudienceStrip != null) {
            binding.rowAudienceStrip.setVisibility(View.VISIBLE);
        }
    }

    private boolean markUniqueJoin(@Nullable String joinedRoomId, @Nullable String userId) {
        String normalizedUser = normalizeUserId(userId);
        if (normalizedUser.isEmpty()) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        recentJoinEffects.entrySet().removeIf(entry -> now - entry.getValue() > JOIN_EFFECT_DEDUPE_MS);
        String key = String.valueOf(joinedRoomId) + '|' + normalizedUser;
        Long previous = recentJoinEffects.put(key, now);
        return previous == null || now - previous > JOIN_EFFECT_DEDUPE_MS;
    }

    private static String normalizeUserId(@Nullable String userId) {
        return userId == null ? "" : userId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean sameUser(@Nullable String first, @Nullable String second) {
        String normalizedFirst = normalizeUserId(first);
        return !normalizedFirst.isEmpty() && normalizedFirst.equals(normalizeUserId(second));
    }

    private void updateViewerStack(List<JsonObject> members) {
        // Header top slots are reserved for room supporters (TikTok-style).
        // Audience avatars live in rowAudienceStrip / recyclerRecentJoiners.
    }

    private void updateTopSupporters(List<RoomDtos.SupporterDto> supporters) {
        if (binding == null) return;
        View[] slots = {binding.topSlot1, binding.topSlot2, binding.topSlot3};
        ImageView[] imgs = {binding.imgTop1, binding.imgTop2, binding.imgTop3};
        ImageView[] frames = {binding.imgTopFrame1, binding.imgTopFrame2, binding.imgTopFrame3};
        TextView[] scores = {binding.tvTopScore1, binding.tvTopScore2, binding.tvTopScore3};
        for (View slot : slots) {
            if (slot != null) slot.setVisibility(View.GONE);
        }
        for (TextView score : scores) {
            if (score != null) score.setVisibility(View.GONE);
        }
        // Only the current top-3 by score (rank shifts when someone overtakes).
        List<RoomDtos.SupporterDto> all = new ArrayList<>();
        if (supporters != null) {
            for (RoomDtos.SupporterDto item : supporters) {
                if (item == null) continue;
                all.add(item);
            }
            all.sort((a, b) -> Long.compare(Math.max(0L, b.score), Math.max(0L, a.score)));
        }
        List<RoomDtos.SupporterDto> top = all.size() > 3
                ? new ArrayList<>(all.subList(0, 3))
                : all;
        boolean hasAny = !top.isEmpty();
        if (binding.rowTopHosts != null) {
            binding.rowTopHosts.setVisibility(hasAny ? View.VISIBLE : View.GONE);
        }
        if (binding.tvSupportersCount != null) {
            if (hasAny) {
                int total = Math.max(top.size(), all.size());
                binding.tvSupportersCount.setText(total > 99 ? "99+" : String.valueOf(total));
                binding.tvSupportersCount.setVisibility(View.VISIBLE);
            } else {
                binding.tvSupportersCount.setVisibility(View.GONE);
            }
        }
        // Keep room card readable beside the compact stack.
        if (binding.hostCardWrap != null) {
            binding.hostCardWrap.setVisibility(View.VISIBLE);
            binding.hostCardWrap.bringToFront();
        }
        if (binding.hostCard != null) {
            binding.hostCard.setVisibility(View.VISIBLE);
        }
        if (!hasAny) return;
        for (int i = 0; i < top.size(); i++) {
            RoomDtos.SupporterDto supporter = top.get(i);
            bindTop(slots[i], imgs[i], frames[i], supporter.avatarUrl, null);
            // Front avatar (left) highest elevation — Mikoo cascade.
            if (slots[i] != null) slots[i].setElevation(dp(8 - i * 2));
            slots[i].setContentDescription(
                    "داعم " + (i + 1) + " " + supporter.displayName);
            final RoomDtos.SupporterDto tap = supporter;
            slots[i].setOnClickListener(v -> showUserCard(
                    tap.userId,
                    tap.displayName,
                    tap.avatarUrl,
                    tap.vipBadgeUrl,
                    isAgencyRoom ? tap.hostBadgeUrl : null,
                    0,
                    1));
        }
    }

    private static String formatAudienceScore(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "%.1fB", value / 1_000_000_000f);
        }
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f);
        }
        if (value >= 1_000L) {
            return String.format(java.util.Locale.US, "%.1fK", value / 1_000f);
        }
        return String.valueOf(Math.max(0L, value));
    }

    /** Mikoo in-room yellow cup chip (dayGold → 8.02M). */
    private void updateRoomCupChip(long dayGold) {
        if (binding == null || binding.tvRoomCupScore == null) return;
        binding.tvRoomCupScore.setText(formatAudienceScore(Math.max(0L, dayGold)));
        if (binding.rowRoomCupChip != null) {
            binding.rowRoomCupChip.setVisibility(View.VISIBLE);
        }
    }

    private void updateAgencySupporters(List<RoomDtos.SupporterDto> supporters) {
        View[] slots = {
                binding.agencyTopSlot1,
                binding.agencyTopSlot2,
                binding.agencyTopSlot3
        };
        ImageView[] images = {
                binding.imgAgencyTop1,
                binding.imgAgencyTop2,
                binding.imgAgencyTop3
        };
        ImageView[] frames = {
                binding.imgAgencyTopFrame1,
                binding.imgAgencyTopFrame2,
                binding.imgAgencyTopFrame3
        };
        for (View slot : slots) slot.setVisibility(View.GONE);
        boolean visible = supporters != null && !supporters.isEmpty();
        binding.rowAgencyTop.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        for (int i = 0; i < Math.min(3, supporters.size()); i++) {
            RoomDtos.SupporterDto supporter = supporters.get(i);
            bindTop(slots[i], images[i], frames[i], supporter.avatarUrl, null);
            slots[i].setContentDescription(
                    "داعم الوكالة " + (i + 1) + " " + supporter.displayName);
            slots[i].setOnClickListener(v -> showUserCard(
                    supporter.userId,
                    supporter.displayName,
                    supporter.avatarUrl,
                    supporter.vipBadgeUrl,
                    isAgencyRoom ? supporter.hostBadgeUrl : null,
                    0,
                    1));
        }
    }

    private void bindTop(View slot, ImageView img, ImageView frame, String url, String frameUrl) {
        if (slot == null || img == null) return;
        // Skip empty black circles: only show a slot when we have a real avatar.
        if (url == null || url.trim().isEmpty()) {
            slot.setVisibility(View.GONE);
            return;
        }
        slot.setVisibility(View.VISIBLE);
        // Top supporters: avatar only — no worn host frame.
        if (frame != null) {
            frame.setVisibility(View.GONE);
            frame.setImageDrawable(null);
        }
        img.setPadding(0, 0, 0, 0);
        img.setBackground(null);
        AvatarImageLoader.applyCircularClip(img);
        AvatarImageLoader.load(img, url);
    }

    private void clearTopSupportersUi() {
        latestSupporters = null;
        if (binding == null) return;
        View[] slots = {binding.topSlot1, binding.topSlot2, binding.topSlot3};
        for (View slot : slots) {
            if (slot != null) slot.setVisibility(View.GONE);
        }
        if (binding.tvSupportersCount != null) {
            binding.tvSupportersCount.setVisibility(View.GONE);
        }
        if (binding.rowTopHosts != null) binding.rowTopHosts.setVisibility(View.GONE);
        if (binding.rowAgencyTop != null) binding.rowAgencyTop.setVisibility(View.GONE);
    }

    private void clearSeatRequestsUi() {
        updateSeatRequestBadge(null);
        if (binding != null && binding.btnPlusWrap != null) {
            binding.btnPlusWrap.setVisibility(View.GONE);
        }
    }

    private void showSupportersBoard() {
        RoomContributeSheet.showSheet(getSupportFragmentManager(), roomId);
    }

    /** Called from RoomContributeSheet list row. */
    void showUserCardFromContribute(@Nullable String userId, @Nullable String name,
                                    @Nullable String avatar) {
        if (userId == null || userId.isEmpty()) return;
        showUserCard(userId, name != null ? name : "عضو", avatar, null, null, 0, 1);
    }

    private void showSupporterList(String title, List<RoomDtos.SupporterDto> supporters) {
        if (supporters == null || supporters.isEmpty()) {
            AuraDialogHelper.message(this, title, "لا توجد هدايا مسجلة بعد");
            return;
        }
        String[] rows = new String[supporters.size()];
        for (int i = 0; i < supporters.size(); i++) {
            RoomDtos.SupporterDto item = supporters.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
            rows[i] = medal + "  " + item.displayName + "  ·  " + item.score + " عملة";
        }
        AuraDialogHelper.list(this, title, rows, which -> {
            RoomDtos.SupporterDto item = supporters.get(which);
            showUserCard(item.userId, item.displayName, item.avatarUrl,
                    item.vipBadgeUrl,
                    isAgencyRoom ? item.hostBadgeUrl : null, 0, 1);
        });
    }

    /**
     * Mikoo-style intentional leave: leave UI first (smooth), then tear RTC/session.
     * Never wait on LiveKit/Zego disconnect before finish — that was black-screen freeze.
     * Minimize must never call this — only احتفظ / FGS keep-alive.
     */
    private void exitRoom(boolean finishNow) {
        if (exiting) return;
        exiting = true;
        minimizing = false;
        try {
            dismissRoomJoinLoading();
        } catch (Exception ignored) {
        }
        clearSeatRequestsUi();
        clearTopSupportersUi();
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(retryRealtimeJoinRunnable);
        handler.removeCallbacks(retryHttpJoinRunnable);
        // Instant silence so leave feels snappy even if RTC cleanup lags.
        try {
            roomSpeakerMuted = true;
            RoomSoundFx.setMuted(true);
            GiftAudioFx.resetRoomGiftSounds();
            RoomRtcEngine.getInstance().setMicEnabled(false);
            RoomRtcEngine.getInstance().setSpeakerMuted(true);
        } catch (Exception ignored) {
        }
        // Leave the UI first, then clean session (RTC disconnect is non-blocking).
        if (finishNow) {
            try {
                navigateHomeAndFinish();
            } catch (Exception e) {
                try {
                    finish();
                } catch (Exception ignored) {
                }
            }
            try {
                overridePendingTransition(0, 0);
            } catch (Exception ignored) {
            }
        }
        try {
            teardownRoomSession(true);
        } catch (Exception ignored) {
        }
    }

    /**
     * Single leave/logout path (Mikoo BaseRoomServiceScheduler.exitRoom analogue).
     * Safe to call from exitRoom, forceExitRoom, room-hop, or onDestroy fallback.
     * Order: hard-cut audio (no mute-restore stash) → stop music → socket/HTTP leave → Zego logout.
     *
     * @param force when true, run even if a previous teardown already marked done
     *              (required for swipe room-hop so old audio always dies).
     */
    private void teardownRoomSession(boolean stopForeground) {
        teardownRoomSession(stopForeground, false);
    }

    private void teardownRoomSession(boolean stopForeground, boolean force) {
        if (roomTeardownDone && !force) return;
        roomTeardownDone = true;
        // 1) Cut all room sound IMMEDIATELY — never stash streams for unmute restore.
        try {
            roomSpeakerMuted = true;
            RoomSoundFx.setMuted(true);
            GiftAudioFx.resetRoomGiftSounds();
        } catch (Exception ignored) {
        }
        try {
            if (roomMusicPlayer != null) {
                roomMusicPlayer.setPlayWhenReady(false);
                roomMusicPlayer.stop();
                roomMusicPlayer.setVolume(0f);
            }
        } catch (Exception ignored) {
        }
        try {
            RoomRtcEngine.getInstance().stopLocalMusic();
        } catch (Exception ignored) {
        }
        if (stopForeground) {
            try {
                VoiceRoomForegroundService.stopForUiExit(this);
            } catch (Exception ignored) {
            }
        }
        String leavingRoomId = roomId;
        try {
            micOn = false;
            RoomRtcEngine.getInstance().setMicEnabled(false);
            // Hard leave: stop play/publish + logout. Do NOT setSpeakerMuted(true) first —
            // that would stash streams and unmute after hop could revive the previous room.
            RoomRtcEngine.getInstance().hardLeaveRoom();
        } catch (Exception ignored) {
        }
        if (leavingRoomId != null) {
            try {
                RealtimeClient.getInstance().leaveRoom(leavingRoomId);
            } catch (Exception ignored) {
            }
            try {
                viewModel.leaveRoom(leavingRoomId);
            } catch (Exception ignored) {
            }
        }
        try {
            ActiveRoomSession.get().clear();
        } catch (Exception ignored) {
        }
    }

    /** Leave current room and open the host's invited room for the 2-minute task dwell. */
    private void switchToInvitedRoom(String targetRoomId) {
        if (targetRoomId == null || targetRoomId.isEmpty()) return;
        if (targetRoomId.equals(roomId)) return;
        switchRoomInPlace(targetRoomId, null);
    }

    public static boolean isRoomUiVisible() {
        return sRoomUiVisible;
    }

    /** Current voice-room activity if still alive (including minimized / Home). */
    @Nullable
    public static VoiceRoomActivity getAliveInstance() {
        VoiceRoomActivity room = sAliveRoom != null ? sAliveRoom.get() : null;
        if (room == null || room.isFinishing() || room.isDestroyed()) return null;
        return room;
    }

    private void navigateHomeAndFinish() {
        Intent home = new Intent(this,
                com.Dramizo.Series.presentation.main.MainActivity.class);
        home.putExtra(com.Dramizo.Series.presentation.main.MainActivity.EXTRA_OPEN_HOME, true);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(home);
        finish();
        try {
            overridePendingTransition(0, 0);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Swipe works over seats/stage (TikTok-like). Only block bottom chrome / overlays.
            roomGestureEligible = !isHost && !isTouchOnRoomSwipeChrome(event.getRawX(), event.getRawY());
        }
        if (roomGestureEligible && roomSwipeDetector != null) {
            roomSwipeDetector.onTouchEvent(event);
        }
        if (!isHost && binding != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    likeTapDownX = event.getRawX();
                    likeTapDownY = event.getRawY();
                    likeTapDownAt = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(event.getRawX() - likeTapDownX,
                            event.getRawY() - likeTapDownY) > roomTouchSlop) {
                        likeTapDownAt = 0L;
                    }
                    break;
                case MotionEvent.ACTION_UP: {
                    long dt = System.currentTimeMillis() - likeTapDownAt;
                    float dx = event.getRawX() - likeTapDownX;
                    float dy = event.getRawY() - likeTapDownY;
                    if (likeTapDownAt > 0L
                            && !isTouchOnInteractiveUi(event.getRawX(), event.getRawY())
                            && dt < 280L && Math.hypot(dx, dy) < roomTouchSlop
                            && !isLikeTapOnChrome(event.getRawX(), event.getRawY())) {
                        sendRoomLikeAt(event.getRawX(), event.getRawY());
                    }
                    roomGestureEligible = false;
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    roomGestureEligible = false;
                    likeTapDownAt = 0L;
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void sendRoomLikeAt(float rawX, float rawY) {
        if (binding == null || roomId == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLikeSentAt < 180L) {
            spawnFloatingHearts(rawX, rawY);
            return;
        }
        lastLikeSentAt = now;
        int[] loc = new int[2];
        binding.likeHeartsOverlay.getLocationOnScreen(loc);
        float x = rawX - loc[0];
        float y = rawY - loc[1];
        if (x < 0 || y < 0) {
            x = binding.likeHeartsOverlay.getWidth() / 2f;
            y = binding.likeHeartsOverlay.getHeight() * 0.65f;
        }
        spawnFloatingHearts(x, y);
        JsonObject payload = new JsonObject();
        payload.addProperty("x", x);
        payload.addProperty("y", y);
        String name = ContainerProvider.from(this).getSessionManager().getDisplayName();
        payload.addProperty("displayName", name != null ? name : "مستخدم");
        RealtimeClient.getInstance().emitRoomEvent(roomId, "room:like", payload);
    }

    private void spawnFloatingHearts(float x, float y) {
        if (binding == null || binding.likeHeartsOverlay == null) return;
        ViewGroup overlay = binding.likeHeartsOverlay;
        java.util.Random rnd = new java.util.Random();
        int count = 3 + rnd.nextInt(3);
        int[] colors = {
                0xFFFF2D55, 0xFFFF5A7A, 0xFFFF8FAB, 0xFFFF1744, 0xFFE91E63, 0xFFFF4081
        };
        for (int i = 0; i < count; i++) {
            ImageView heart = new ImageView(this);
            heart.setImageResource(R.drawable.ic_heart_like);
            heart.setColorFilter(colors[rnd.nextInt(colors.length)]);
            int size = dp(10 + rnd.nextInt(6));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            heart.setLayoutParams(lp);
            float startX = x - size / 2f + (rnd.nextFloat() - 0.5f) * dp(22);
            float startY = y - size / 2f + (rnd.nextFloat() - 0.5f) * dp(10);
            heart.setX(startX);
            heart.setY(startY);
            heart.setAlpha(0f);
            heart.setScaleX(0.4f);
            heart.setScaleY(0.4f);
            overlay.addView(heart);
            float driftX = (rnd.nextFloat() - 0.5f) * dp(48);
            float rise = dp(90 + rnd.nextInt(70));
            long delay = i * 35L;
            long duration = 750L + rnd.nextInt(350);
            heart.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(140).setStartDelay(delay)
                    .withEndAction(() -> heart.animate()
                            .translationXBy(driftX)
                            .translationYBy(-rise)
                            .alpha(0f)
                            .scaleX(0.65f).scaleY(0.65f)
                            .setDuration(duration)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> {
                                if (binding != null && binding.likeHeartsOverlay != null) {
                                    binding.likeHeartsOverlay.removeView(heart);
                                }
                            })
                            .start())
                    .start();
        }
    }

    private boolean isLikeTapOnChrome(float x, float y) {
        return isTouchOnInteractiveUi(x, y);
    }

    /** Controls / overlays where vertical room-swipe must not steal touches. */
    private boolean isTouchOnRoomSwipeChrome(float x, float y) {
        if (binding == null) return true;
        return hitView(binding.bottomBar, x, y)
                || hitView(binding.chatPanel, x, y)
                || hitView(binding.headerRoom, x, y)
                || hitView(binding.musicFloatWrap, x, y)
                || hitView(binding.musicCard, x, y)
                || hitView(binding.taskFloatWrap, x, y)
                || hitView(binding.luckyFloatWrap, x, y)
                // Only the bottom game panel captures gestures — not a full-screen mask.
                || hitView(binding.gamePanel, x, y);
    }

    private boolean isTouchOnInteractiveUi(float x, float y) {
        if (binding == null) return true;
        return isTouchOnRoomSwipeChrome(x, y)
                || hitView(binding.recyclerSeats, x, y)
                || hitView(binding.recyclerRecentJoiners, x, y)
                || hitView(binding.hostStage, x, y)
                || hitView(binding.hostCard, x, y);
    }

    private static boolean hitView(View view, float x, float y) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return x >= loc[0] && x <= loc[0] + view.getWidth()
                && y >= loc[1] && y <= loc[1] + view.getHeight();
    }

    private void switchRoomFeed(int direction) {
        if (isHost || roomId == null || switchingRoom || hoppingRoom || exiting) return;
        switchingRoom = true;
        final String fromRoomId = roomId;
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<RoomDtos.RoomDto>> result =
                    container.listRoomsUseCase.execute(1, 50);
            RoomDtos.RoomDto target = null;
            if (result.success && result.data != null && result.data.items != null) {
                List<RoomDtos.RoomDto> publicRooms = new ArrayList<>();
                for (RoomDtos.RoomDto room : result.data.items) {
                    if (room == null || room.id == null || room.hasPassword) continue;
                    // Skip closed / empty shells if status is exposed.
                    if (room.status != null && "CLOSED".equalsIgnoreCase(room.status)) continue;
                    publicRooms.add(room);
                }
                if (!publicRooms.isEmpty()) {
                    int current = -1;
                    for (int i = 0; i < publicRooms.size(); i++) {
                        if (fromRoomId.equals(publicRooms.get(i).id)) {
                            current = i;
                            break;
                        }
                    }
                    int next;
                    if (current < 0) {
                        next = direction > 0 ? 0 : publicRooms.size() - 1;
                    } else {
                        // Wrap like a feed: end → first, first → last.
                        next = (current + direction) % publicRooms.size();
                        if (next < 0) next += publicRooms.size();
                    }
                    if (next != current) target = publicRooms.get(next);
                    else if (publicRooms.size() > 1) {
                        next = (current + (direction >= 0 ? 1 : -1) + publicRooms.size())
                                % publicRooms.size();
                        target = publicRooms.get(next);
                    }
                }
            }
            RoomDtos.RoomDto nextRoom = target;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    switchingRoom = false;
                    return;
                }
                if (nextRoom == null || nextRoom.id == null) {
                    switchingRoom = false;
                    Toast.makeText(this,
                            direction > 0 ? "لا توجد غرفة تالية" : "لا توجد غرفة سابقة",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                switchingRoom = false;
                switchRoomInPlace(nextRoom.id, null);
                overridePendingTransition(
                        direction > 0 ? R.anim.slide_in_bottom : R.anim.slide_in_top,
                        direction > 0 ? R.anim.slide_out_top : R.anim.slide_out_bottom);
            });
        });
    }

    private int responsiveSeatColumns() {
        android.content.res.Configuration configuration = getResources().getConfiguration();
        int widthDp = configuration.screenWidthDp > 0
                ? configuration.screenWidthDp
                : Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        return widthDp >= 600 ? 8 : 4;
    }

    private int responsivePanelColumns() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return widthDp >= 840 ? 5 : widthDp >= 600 ? 4 : 3;
    }

    private int responsiveGameColumns() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return widthDp >= 840 ? 4 : widthDp >= 600 ? 3 : 2;
    }

    private void applyResponsiveRoomLayout() {
        android.content.res.Configuration configuration =
                getResources().getConfiguration();
        float density = getResources().getDisplayMetrics().density;
        int widthDp = configuration.screenWidthDp > 0
                ? configuration.screenWidthDp
                : Math.round(getResources().getDisplayMetrics().widthPixels / density);
        int heightDp = configuration.screenHeightDp > 0
                ? configuration.screenHeightDp
                : Math.round(getResources().getDisplayMetrics().heightPixels / density);
        boolean narrow = widthDp <= 360;
        boolean veryNarrow = widthDp <= 340;
        boolean shortScreen = heightDp < 680;
        boolean tablet = widthDp >= 600;

        // Keep red promo banner open when enabled — do not force GONE on every layout pass.
        applyRoomBanner(false);
        binding.tvRoomRules.setVisibility(shortScreen ? View.GONE : View.VISIBLE);
        // Give chat more vertical room so bubbles stay readable while typing.
        binding.chatPanel.setMinimumHeight(dp(shortScreen ? 180 : tablet ? 260 : 220));
        // Keep private-msg icon sync with host toggle (does not hide public chat).
        applyChatZone(roomChatZoneVisible);

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams chatParams =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                        binding.chatPanel.getLayoutParams();
        chatParams.matchConstraintPercentWidth = veryNarrow ? 0.98f : tablet ? 0.78f : 0.92f;
        chatParams.setMarginStart(dp(narrow ? 6 : 12));
        chatParams.setMarginEnd(dp(narrow ? 6 : 12));
        binding.chatPanel.setLayoutParams(chatParams);

        int musicWidth = Math.max(240, Math.min(tablet ? 300 : 292, widthDp - 28));
        ViewGroup.LayoutParams musicParams = binding.musicCard.getLayoutParams();
        musicParams.width = dp(musicWidth);
        if (musicParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) musicParams).bottomMargin = dp(10);
        }
        binding.musicCard.setLayoutParams(musicParams);

        // Compact host stage so guest seats sit higher and chat grows below.
        int hostFrameSize = shortScreen ? 118 : tablet ? 140 : 132;
        setViewSize(binding.hostStageAvatarWrap, hostFrameSize, hostFrameSize);
        // webHostSignal is match_parent inside the wrap — size the wrap only.

        ViewGroup.MarginLayoutParams hostStageParams =
                (ViewGroup.MarginLayoutParams) binding.hostStage.getLayoutParams();
        hostStageParams.topMargin = dp(shortScreen ? 0 : 2);
        binding.hostStage.setLayoutParams(hostStageParams);

        if (currentSeats != null && !currentSeats.isEmpty()) {
            applyMikooSeatGrid(guestSeatsOnly(currentSeats).size());
        } else if (binding.recyclerSeats.getLayoutParams()
                instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams seatsParams) {
            seatsParams.matchConstraintMaxHeight = 0;
            seatsParams.topMargin = dp(shortScreen ? 2 : 4);
            binding.recyclerSeats.setLayoutParams(seatsParams);
        }

        binding.taskFloatWrap.setScaleX(shortScreen ? 0.9f : 1f);
        binding.taskFloatWrap.setScaleY(shortScreen ? 0.9f : 1f);
        binding.luckyFloatWrap.setScaleX(shortScreen ? 0.84f : 1f);
        binding.luckyFloatWrap.setScaleY(shortScreen ? 0.84f : 1f);

        binding.btnTools.setVisibility(View.VISIBLE);
        int controlMargin = narrow ? 3 : 6;
        setStartMargin(binding.btnMic, controlMargin);
        setStartMargin(binding.btnGift, controlMargin);
        setStartMargin(binding.btnGames, controlMargin);
        setStartMargin(binding.btnTools, controlMargin);
        binding.bottomBar.setPadding(
                dp(narrow ? 4 : 10),
                binding.bottomBar.getPaddingTop(),
                dp(narrow ? 4 : 10),
                binding.bottomBar.getPaddingBottom());
    }

    private void setViewSize(View view, int widthDp, int heightDp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = dp(widthDp);
        params.height = dp(heightDp);
        view.setLayoutParams(params);
    }

    private void setStartMargin(View view, int marginDp) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        params.setMarginStart(dp(marginDp));
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static void setTextIfChanged(TextView view, CharSequence value) {
        if (view != null && !android.text.TextUtils.equals(view.getText(), value)) {
            view.setText(value);
        }
    }

    private static String hostVisualKey(@Nullable AuthDtos.UserDto user) {
        if (user == null) return "";
        return String.valueOf(user.id) + '|' + user.avatarUrl + '|' + user.hostBadgeUrl
                + '|' + user.hostBadgeMeta + '|' + user.levelBadgeUrl + '|'
                + user.vipBadgeUrl + '|' + user.level + '|' + user.vipLevel;
    }

    private void requestRoomRefresh(boolean immediate) {
        if (roomId == null || exiting) return;
        long now = System.currentTimeMillis();
        // Coalesce socket/API churn so seat DiffUtil + Glide/SVGA are not thrashed.
        long delay = immediate ? 120L : 700L;
        if (now - lastRoomRefreshAt < 1_500L) {
            delay = Math.max(delay, 1_500L - (now - lastRoomRefreshAt));
        }
        handler.removeCallbacks(coalescedRoomRefresh);
        roomRefreshPending = true;
        handler.postDelayed(coalescedRoomRefresh, delay);
    }

    private void scheduleSupporterRefresh(long delayMs) {
        if (roomId == null || exiting) return;
        handler.removeCallbacks(supporterRefresh);
        supporterRefreshPending = true;
        handler.postDelayed(supporterRefresh, Math.max(0L, delayMs));
    }

    private void applyRoomBackground(String url) {
        String backgroundKey = url != null ? url : "";
        if (Objects.equals(lastBoundBackgroundUrl, backgroundKey)) return;
        lastBoundBackgroundUrl = backgroundKey;
        if (url == null || url.isEmpty()) {
            String themeBg = com.Dramizo.Series.util.RemoteTheme.roomDefaultBackgroundUrl(this);
            if (themeBg != null && !themeBg.isEmpty()) {
                // Keep XML android:src as fallback (hams_background).
                com.Dramizo.Series.util.RemoteTheme.loadInto(binding.imgRoomBg, themeBg, 0);
            }
            // else leave activity_voice_room.xml android:src
            return;
        }
        if (url.startsWith("drawable://")) {
            String name = url.substring("drawable://".length());
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) binding.imgRoomBg.setImageResource(resId);
            return;
        }
        Glide.with(this)
                .load(AssetCatalog.absoluteUrl(url))
                .placeholder(binding.imgRoomBg.getDrawable())
                .error(binding.imgRoomBg.getDrawable())
                .centerCrop()
                .into(binding.imgRoomBg);
    }

    private void showRoomBackgroundPicker() {
        showRoomCosmeticPicker("room_background", false, false);
    }

    private void showRoomFramePicker() {
        showRoomCosmeticPicker("room_card", true, false);
    }

    private void showHostSignalPicker() {
        showRoomCosmeticPicker("vip_badge", false, true);
    }

    /** Owner/permitted moderator picks an owned room cosmetic without changing shared media. */
    private void showRoomCosmeticPicker(
            String cosmeticType, boolean roomFrame, boolean hostSignal) {
        if (!canChangeFrames) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_room_backgrounds, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        View progress = sheet.findViewById(R.id.progressBgPicker);
        TextView empty = sheet.findViewById(R.id.tvBgPickerEmpty);
        androidx.recyclerview.widget.RecyclerView recycler = sheet.findViewById(R.id.recyclerRoomBackgrounds);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        int maxListH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.48f);
        ViewGroup.LayoutParams rlp = recycler.getLayoutParams();
        rlp.height = maxListH;
        recycler.setLayoutParams(rlp);

        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.List<com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto>> cat =
                    c.getCosmeticsRepository().catalog(cosmeticType);
            Result<java.util.List<com.Dramizo.Series.data.remote.dto.CosmeticDtos.UserCosmeticDto>> inv =
                    c.getCosmeticsRepository().inventory();
            java.util.HashSet<String> owned = new java.util.HashSet<>();
            if (inv.success && inv.data != null) {
                for (com.Dramizo.Series.data.remote.dto.CosmeticDtos.UserCosmeticDto row : inv.data) {
                    if (row != null && row.cosmeticId != null) owned.add(row.cosmeticId);
                    if (row != null && row.cosmetic != null && row.cosmetic.id != null) {
                        owned.add(row.cosmetic.id);
                    }
                }
            }
            java.util.ArrayList<com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto> items =
                    new java.util.ArrayList<>();
            if (cat.success && cat.data != null) {
                for (com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto item : cat.data) {
                    if (item == null) continue;
                    // Room frame picker: Mikoo borders only (ignore retired scene covers).
                    if (roomFrame) {
                        String preview = item.previewUrl != null ? item.previewUrl.toLowerCase() : "";
                        if (preview.contains("/assets/rooms/card_") && !preview.contains("/mikoo/")) {
                            continue;
                        }
                        if (preview.contains("/kenar/")) {
                            continue;
                        }
                    }
                    items.add(item);
                }
            }
            if (roomFrame && items.isEmpty()) {
                // Offline fallback: Mikoo rank borders.
                String[] codes = {
                        "room_mikoo_border_top1",
                        "room_mikoo_border_top2",
                        "room_mikoo_border_top3",
                        "room_mikoo_border_top4",
                        "room_mikoo_border_top5",
                        "room_mikoo_border_top6",
                        "room_mikoo_border_top7"
                };
                String[] files = {
                        "bg_room_border_top1.webp",
                        "bg_room_border_top2.webp",
                        "bg_room_border_top3.webp",
                        "bg_room_border_top4.webp",
                        "bg_room_border_top5.webp",
                        "bg_room_border_top6.webp",
                        "bg_room_border_top7.webp"
                };
                String[] names = {
                        "إطار الروم · المركز 1",
                        "إطار الروم · المركز 2",
                        "إطار الروم · المركز 3",
                        "إطار الروم · المركز 4",
                        "إطار الروم · المركز 5",
                        "إطار الروم · المركز 6",
                        "إطار الروم · المركز 7"
                };
                int[] prices = {299, 249, 199, 179, 159, 139, 119};
                for (int i = 0; i < codes.length; i++) {
                    com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto dto =
                            new com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto();
                    dto.id = "local-room-border-" + (i + 1);
                    dto.code = codes[i];
                    dto.name = names[i];
                    dto.previewUrl = "/assets/rooms/mikoo/" + files[i] + "?v=20260806r7";
                    dto.animationUrl = null;
                    dto.coinPrice = prices[i];
                    dto.minVipLevel = 0;
                    dto.minUserLevel = 0;
                    items.add(dto);
                }
            }
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                progress.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    empty.setVisibility(View.VISIBLE);
                    return;
                }
                recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(
                            @NonNull ViewGroup parent, int viewType) {
                        View v = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_room_background, parent, false);
                        return new RecyclerView.ViewHolder(v) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        com.Dramizo.Series.data.remote.dto.CosmeticDtos.CosmeticDto item = items.get(position);
                        ImageView img = holder.itemView.findViewById(R.id.imgRoomBgThumb);
                        TextView name = holder.itemView.findViewById(R.id.tvRoomBgName);
                        TextView lock = holder.itemView.findViewById(R.id.tvRoomBgLock);
                        name.setText(item.name != null ? item.name : item.code);
                        boolean free = item.coinPrice <= 0
                                && item.minVipLevel <= 0
                                && item.minUserLevel <= 0;
                        boolean canUse = hostSignal ? owned.contains(item.id) : free || owned.contains(item.id);
                        lock.setVisibility(canUse ? View.GONE : View.VISIBLE);
                        if (!canUse) {
                            lock.setText(item.coinPrice + "🪙");
                        }
                        String preview = AssetCatalog.absoluteUrl(item.previewUrl);
                        Glide.with(img.getContext())
                                .load(preview)
                                .placeholder(R.drawable.voice_room_bg)
                                .error(R.drawable.voice_room_bg)
                                .centerCrop()
                                .into(img);
                        holder.itemView.setOnClickListener(v -> {
                            if (!canUse) {
                                Toast.makeText(VoiceRoomActivity.this,
                                        hostSignal
                                                ? "اشترِ إشارة المضيف من متجر المظهر أولاً"
                                                : roomFrame
                                                ? "اشترِ إطار الغرفة من متجر المظهر أولاً"
                                                : "اشترِ الخلفية من متجر المظهر أولاً",
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(VoiceRoomActivity.this,
                                        com.Dramizo.Series.presentation.cosmetics.CosmeticsActivity.class)
                                        .putExtra(com.Dramizo.Series.presentation.cosmetics.CosmeticsViewModel.EXTRA_TYPE,
                                                cosmeticType));
                                return;
                            }
                            String url = RoomKenarHelper.preferAnimated(item.animationUrl, item.previewUrl);
                            if (url == null || url.isEmpty()) return;
                            if (hostSignal) {
                                c.getIoExecutor().execute(() -> {
                                    Result<com.Dramizo.Series.data.remote.dto.CosmeticDtos.EquipResult> result =
                                            c.getCosmeticsRepository().equip(item.id);
                                    runOnUiThread(() -> {
                                        if (!result.success) {
                                            Toast.makeText(VoiceRoomActivity.this,
                                                    result.error != null
                                                            ? result.error
                                                            : "تعذر تغيير إشارة المضيف",
                                                    Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        requestRoomRefresh(true);
                                        Toast.makeText(VoiceRoomActivity.this,
                                                "تم تطبيق إشارة المضيف المتحركة",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                });
                            } else if (roomFrame) {
                                viewModel.setRoomFrame(roomId, url);
                            } else {
                                String bg = item.previewUrl;
                                if (bg == null || bg.isEmpty()) return;
                                applyRoomBackground(bg);
                                viewModel.setBackground(roomId, bg);
                            }
                            dialog.dismiss();
                            Toast.makeText(VoiceRoomActivity.this,
                                    hostSignal
                                            ? "جاري تطبيق إشارة المضيف"
                                            : roomFrame
                                            ? "تم تغيير إطار الغرفة"
                                            : "تم تغيير خلفية الغرفة للجميع",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public int getItemCount() {
                        return items.size();
                    }
                });
            });
        });
        dialog.show();
    }

    private void showSeatCountSheet() {
        if (!canManageRoom && !isHost) {
            // Guests: home/minimize shortcut instead of seat admin.
            keepRoomInBackground();
            return;
        }
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_room_settings, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.tvDialogTitle);
        if (title != null) title.setText("ضبط المقاعد");

        // Hide non-seat admin rows for a focused seats sheet.
        int[] hideIds = {
                R.id.btnChangeRoomName,
                R.id.btnChangeRoomPhoto,
                R.id.btnSeatRequests,
                R.id.btnToggleLock,
                R.id.rowGiftSounds,
                R.id.tvGiftSoundsHint,
                R.id.btnPickRoomBackground,
                R.id.btnManageSeats,
                R.id.btnManageMods
        };
        for (int id : hideIds) {
            View row = sheet.findViewById(id);
            if (row != null) row.setVisibility(View.GONE);
        }
        View close = sheet.findViewById(R.id.btnDialogClose);
        if (close != null) {
            close.setOnClickListener(v -> dialog.dismiss());
        }

        wireSeatCountButtons(sheet, dialog::dismiss);
        dialog.show();
    }

    private void showHostTools() {
        if (!canModerateRoom()) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_room_settings, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.tvDialogTitle);
        if (title != null) title.setText(R.string.settings);

        TextView btnChangeName = sheet.findViewById(R.id.btnChangeRoomName);
        if (btnChangeName != null) {
            btnChangeName.setVisibility(canManageRoom ? View.VISIBLE : View.GONE);
            if (isAgencyRoom) {
                btnChangeName.setText("تغيير اسم الوكالة");
            }
            btnChangeName.setOnClickListener(v -> {
                dialog.dismiss();
                promptRenameRoomTitle(viewModel != null ? viewModel.getRoom().getValue() : null);
            });
        }
        TextView btnManageAgency = sheet.findViewById(R.id.btnManageAgencyFromRoom);
        if (btnManageAgency != null) {
            RoomDtos.RoomDto live = viewModel != null ? viewModel.getRoom().getValue() : null;
            boolean showAgencyManage = isAgencyRoom && canManageRoom
                    && live != null && live.agencyId != null && !live.agencyId.isEmpty();
            btnManageAgency.setVisibility(showAgencyManage ? View.VISIBLE : View.GONE);
            if (showAgencyManage) {
                final String agencyId = live.agencyId;
                btnManageAgency.setOnClickListener(v -> {
                    dialog.dismiss();
                    android.content.Intent i = new android.content.Intent(this,
                            com.Dramizo.Series.presentation.agency.AgencyManageActivity.class);
                    i.putExtra(com.Dramizo.Series.presentation.agency.AgencyManageActivity.EXTRA_AGENCY_ID,
                            agencyId);
                    startActivity(i);
                });
            }
        }
        TextView btnChangePhoto = sheet.findViewById(R.id.btnChangeRoomPhoto);
        if (btnChangePhoto != null) {
            btnChangePhoto.setVisibility(canManageRoom ? View.VISIBLE : View.GONE);
            btnChangePhoto.setOnClickListener(v -> {
                dialog.dismiss();
                roomCoverPicker.launch("image/*");
            });
        }

        TextView requestsBtn = sheet.findViewById(R.id.btnSeatRequests);
        if (requestsBtn != null) {
            boolean showQueue = canInviteMic && !isFreeMicEnabled();
            requestsBtn.setVisibility(showQueue ? View.VISIBLE : View.GONE);
            List<RoomDtos.SeatRequestDto> pending = viewModel.getSeatRequests().getValue();
            int n = pending != null ? pending.size() : 0;
            requestsBtn.setText(n > 0
                    ? ("طلبات الانضمام للمايك (" + n + ")")
                    : "طلبات الانضمام للمايك");
            requestsBtn.setOnClickListener(v -> {
                dialog.dismiss();
                showSeatRequestsDialog();
            });
        }
        TextView lockBtn = sheet.findViewById(R.id.btnToggleLock);
        if (lockBtn != null) {
            // Agency rooms stay public on the server; personal rooms can lock with a password.
            lockBtn.setVisibility(canManageRoom && !isAgencyRoom ? View.VISIBLE : View.GONE);
            lockBtn.setText(locked ? R.string.unlock_room : R.string.lock_room);
            lockBtn.setOnClickListener(v -> {
                if (isAgencyRoom) {
                    Toast.makeText(this, "غرف الوكالة عامة ولا تُقفل بكلمة مرور", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!locked) {
                    promptRoomPassword(pwd -> {
                        viewModel.lockRoom(roomId, true, pwd);
                        Toast.makeText(this, R.string.room_locked_with_password, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    viewModel.lockRoom(roomId, false, null);
                    Toast.makeText(this, R.string.room_unlocked, Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            });
        }
        if (canManageRoom) {
            wireSeatCountButtons(sheet, dialog::dismiss);
        } else {
            View rowCounts = sheet.findViewById(R.id.rowSeatCounts);
            if (rowCounts != null) rowCounts.setVisibility(View.GONE);
        }

        View pickBg = sheet.findViewById(R.id.btnPickRoomBackground);
        if (pickBg != null) {
            pickBg.setVisibility(canChangeFrames ? View.VISIBLE : View.GONE);
            pickBg.setOnClickListener(v -> {
                dialog.dismiss();
                showRoomBackgroundPicker();
            });
        }
        sheet.findViewById(R.id.btnDialogClose).setOnClickListener(v -> dialog.dismiss());
        TextView btnSeatsLock = sheet.findViewById(R.id.btnManageSeats);
        if (btnSeatsLock != null) {
            btnSeatsLock.setVisibility(canManageSeats ? View.VISIBLE : View.GONE);
            btnSeatsLock.setOnClickListener(v -> {
                dialog.dismiss();
                showSeatLockManager();
            });
        }
        TextView btnMods = sheet.findViewById(R.id.btnManageMods);
        if (btnMods != null) {
            btnMods.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            btnMods.setOnClickListener(v -> {
                dialog.dismiss();
                showModeratorTools();
            });
        }
        wireGiftSoundsSwitch(sheet);
        dialog.show();
    }

    /** Host/mod toggle — syncs to server + realtime for everyone in the room (Mikoo-style). */
    private void wireGiftSoundsSwitch(View sheet) {
        View row = sheet.findViewById(R.id.rowGiftSounds);
        com.google.android.material.switchmaterial.SwitchMaterial toggle =
                sheet.findViewById(R.id.switchGiftSounds);
        TextView hint = sheet.findViewById(R.id.tvGiftSoundsHint);
        if (row == null || toggle == null) return;
        if (!canManageRoom) {
            row.setVisibility(View.GONE);
            if (hint != null) hint.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        if (hint != null) hint.setVisibility(View.VISIBLE);
        RoomDtos.RoomDto room = viewModel.getRoom().getValue();
        boolean enabled = room == null || room.giftSoundsEnabled;
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(enabled);
        updateGiftSoundsHint(hint, enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (roomId == null || roomId.isEmpty()) return;
            applyRoomGiftSounds(checked);
            updateGiftSoundsHint(hint, checked);
            viewModel.setGiftSounds(roomId, checked);
            Toast.makeText(this,
                    checked ? R.string.room_gift_sounds_on : R.string.room_gift_sounds_off,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private static void updateGiftSoundsHint(@Nullable TextView hint, boolean enabled) {
        if (hint == null) return;
        hint.setText(enabled
                ? "صوت الهدايا مفعّل لكل من في الغرفة"
                : "صوت الهدايا مكتوم لكل من في الغرفة");
    }

    private void applyRoomGiftSounds(boolean enabled) {
        GiftAudioFx.setRoomGiftSoundsEnabled(enabled);
    }

    private void showSeatLockManager() {
        if (!canManageSeats || currentSeats == null || currentSeats.isEmpty()) {
            Toast.makeText(this, R.string.no_seats_now, Toast.LENGTH_SHORT).show();
            return;
        }
        final java.util.ArrayList<RoomDtos.SeatDto> seats = new java.util.ArrayList<>(guestSeatsOnly(currentSeats));
        if (seats.isEmpty()) {
            Toast.makeText(this, R.string.no_lockable_seats, Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_seat_lock_manager, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView hint = sheet.findViewById(R.id.tvSeatLockHint);
        if (hint != null) {
            hint.setText("اضغط على أي مقعد فارغ لقفله، أو على المقفل لفتحه");
            hint.setVisibility(View.VISIBLE);
        }

        androidx.recyclerview.widget.RecyclerView recycler = sheet.findViewById(R.id.recyclerSeatLocks);
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        final androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>[] adapterRef =
                new androidx.recyclerview.widget.RecyclerView.Adapter[1];
        adapterRef[0] = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_seat_lock_row, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(
                    @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                RoomDtos.SeatDto seat = seats.get(position);
                TextView tvIndex = holder.itemView.findViewById(R.id.tvSeatLockIndex);
                TextView tvName = holder.itemView.findViewById(R.id.tvSeatLockName);
                TextView tvState = holder.itemView.findViewById(R.id.tvSeatLockState);
                TextView tvAction = holder.itemView.findViewById(R.id.tvSeatLockAction);
                // Guest index is already 1..N (host is 0 on stage).
                int displayNo = Math.max(1, seat.seatIndex);
                tvIndex.setText(String.valueOf(displayNo));
                boolean occupied = seat.userId != null && !seat.userId.isEmpty();
                String who = occupied
                        ? (seat.user != null
                        ? (seat.user.displayName != null ? seat.user.displayName : seat.user.username)
                        : "مشغول")
                        : "فارغ";
                tvName.setText("مقعد " + displayNo + " · " + who);
                boolean lockedSeat = seat.locked();
                boolean canToggle = canToggleSeatLock(seat, !lockedSeat);
                tvState.setText(lockedSeat ? "مقفل حالياً" : (occupied ? "مشغول — لا يمكن القفل" : "مفتوح"));
                tvAction.setText(lockedSeat ? "فتح" : "قفل");
                tvAction.setEnabled(canToggle);
                tvAction.setAlpha(canToggle ? 1f : 0.35f);
                tvAction.setTextColor(lockedSeat ? 0xFF80CBC4 : 0xFFFFD54F);
                View.OnClickListener toggle = v -> {
                    boolean lock = !seat.locked();
                    if (!canToggleSeatLock(seat, lock)) {
                        Toast.makeText(VoiceRoomActivity.this,
                                lock ? "لا يمكن قفل هذا المقعد" : "المقعد غير مقفل",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (occupied && lock) {
                        Toast.makeText(VoiceRoomActivity.this,
                                R.string.cannot_lock_occupied_seat, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.lockSeat(roomId, seat.seatIndex, lock);
                    seat.isLocked = lock;
                    seat.status = lock ? "locked" : "empty";
                    if (adapterRef[0] != null) adapterRef[0].notifyDataSetChanged();
                    Toast.makeText(VoiceRoomActivity.this,
                            lock ? "تم قفل المقعد" : "تم فتح المقعد", Toast.LENGTH_SHORT).show();
                };
                holder.itemView.setOnClickListener(toggle);
                tvAction.setOnClickListener(toggle);
            }

            @Override
            public int getItemCount() {
                return seats.size();
            }
        };
        recycler.setAdapter(adapterRef[0]);
        sheet.findViewById(R.id.btnSeatLockDone).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showModeratorTools() {
        if (!isOwner) return;
        RoomDtos.RoomDto room = viewModel.getRoom().getValue();
        List<String> labels = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (currentSeats != null) {
            for (RoomDtos.SeatDto seat : currentSeats) {
                if (seat == null || seat.userId == null || seat.userId.isEmpty()) continue;
                if (myUserId != null && myUserId.equals(seat.userId)) continue;
                if (room != null && seat.userId.equals(room.hostId)) continue;
                if (!seen.add(seat.userId)) continue;
                String name = seat.user != null
                        ? (seat.user.displayName != null ? seat.user.displayName : seat.user.username)
                        : seat.userId;
                boolean isCohost = room != null && seat.userId.equals(room.cohostId);
                boolean isMod = room != null && room.moderatorIds != null
                        && room.moderatorIds.contains(seat.userId);
                String role = isCohost ? "مساعد مضيف" : (isMod ? "مشرف" : "عضو");
                labels.add(name + " · " + role);
                userIds.add(seat.userId);
                roles.add(role);
            }
        }
        // Include everyone currently inside the room (audience), not only seated users.
        for (JsonObject member : roomAudience) {
            if (member == null || !member.has("userId") || member.get("userId").isJsonNull()) continue;
            String uid = member.get("userId").getAsString();
            if (uid == null || uid.isEmpty()) continue;
            if (myUserId != null && myUserId.equals(uid)) continue;
            if (room != null && uid.equals(room.hostId)) continue;
            if (!seen.add(uid)) continue;
            String name = member.has("displayName") && !member.get("displayName").isJsonNull()
                    ? member.get("displayName").getAsString()
                    : (member.has("username") && !member.get("username").isJsonNull()
                    ? member.get("username").getAsString() : uid);
            boolean isCohost = room != null && uid.equals(room.cohostId);
            boolean isMod = room != null && room.moderatorIds != null
                    && room.moderatorIds.contains(uid);
            String role = isCohost ? "مساعد مضيف" : (isMod ? "مشرف" : "عضو");
            labels.add(name + " · " + role);
            userIds.add(uid);
            roles.add(role);
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.no_users_in_room, Toast.LENGTH_LONG).show();
            return;
        }
        AuraDialogHelper.list(this, "إدارة المشرفين", labels.toArray(new String[0]), which -> {
            String uid = userIds.get(which);
            String role = roles.get(which);
            String[] actions;
            if ("عضو".equals(role)) {
                actions = new String[]{"ترقية مشرف", "تعيين مساعد مضيف"};
            } else if ("مشرف".equals(role)) {
                actions = new String[]{"صلاحيات المشرف", "إزالة مشرف", "تعيين مساعد مضيف"};
            } else {
                actions = new String[]{"إزالة مساعد المضيف / المشرف"};
            }
            AuraDialogHelper.list(this, labels.get(which), actions, a -> {
                if ("عضو".equals(role)) {
                    if (a == 0) {
                        viewModel.addModerator(roomId, uid);
                        Toast.makeText(this,
                                "تم الترقية بتحكم كامل · يمكنك تقييد الصلاحيات",
                                Toast.LENGTH_LONG).show();
                        binding.getRoot().postDelayed(
                                () -> showModeratorPermissionPresets(uid), 400);
                    } else {
                        viewModel.setCohost(roomId, uid);
                    }
                } else if ("مشرف".equals(role)) {
                    if (a == 0) showModeratorPermissionPresets(uid);
                    else if (a == 1) viewModel.removeModerator(roomId, uid);
                    else viewModel.setCohost(roomId, uid);
                } else {
                    viewModel.removeModerator(roomId, uid);
                }
            });
        });
    }

    /** Owner picks Full / Mute-only / Custom for a moderator. */
    private void showModeratorPermissionPresets(String userId) {
        if (!isOwner || userId == null || userId.isEmpty()) return;
        String[] presets = {
                "تحكم كامل (مثل المضيف)",
                "كتم فقط",
                "تخصيص الصلاحيات…"
        };
        AuraDialogHelper.list(this, "صلاحيات المشرف", presets, which -> {
            if (which == 0) {
                applyModeratorPermissionPreset(userId, true);
            } else if (which == 1) {
                applyModeratorPermissionPreset(userId, false);
            } else {
                showModeratorPermissions(userId);
            }
        });
    }

    private void applyModeratorPermissionPreset(String userId, boolean full) {
        viewModel.updateModeratorPermissions(
                roomId, userId,
                full, full, full, true,
                full, full, full, full, full);
        Toast.makeText(this,
                full ? "تم منح التحكم الكامل" : "تم تقييد المشرف على الكتم فقط",
                Toast.LENGTH_SHORT).show();
    }

    private void showModeratorPermissions(String userId) {
        RoomDtos.RoomDto room = viewModel.getRoom().getValue();
        RoomDtos.ModeratorPermissionDto current = null;
        if (room != null && room.moderatorPermissions != null) {
            for (RoomDtos.ModeratorPermissionDto item : room.moderatorPermissions) {
                if (item != null && userId.equals(item.userId)) {
                    current = item;
                    break;
                }
            }
        }
        // Missing DTO → treat as full (Mikoo appoint default); never invent "on" from null wrongly mixed.
        final boolean music = current == null || current.canManageMusic;
        final boolean frames = current == null || current.canChangeFrames;
        final boolean games = current == null || current.canControlGames;
        final boolean mute = current == null || current.canMute;
        final boolean kick = current == null || current.canKick;
        final boolean ban = current == null || current.canBan;
        final boolean seats = current == null || current.canManageSeats;
        final boolean invite = current == null || current.canInvite;
        final boolean manageRoom = current == null || current.canManageRoom;
        final boolean[] values = {
                music, frames, games, mute, kick, ban, seats, invite, manageRoom
        };
        final String[] names = {
                "تشغيل الموسيقى",
                "تغيير الإطارات",
                "التحكم بالألعاب",
                "كتم المستخدمين",
                "إخراج المستخدمين",
                "الطرد المؤقت ومنع الدخول",
                "إدارة وقفل المقاعد",
                "دعوة وقبول طلبات المايك",
                "إعدادات الغرفة وعدد المقاعد"
        };
        AuraDialogHelper.multiChoice(this, "صلاحيات المشرف", names, values, selected -> {
            viewModel.updateModeratorPermissions(
                    roomId, userId,
                    selected[0], selected[1], selected[2], selected[3],
                    selected[4], selected[5], selected[6], selected[7], selected[8]);
            Toast.makeText(this, "تم حفظ صلاحيات المشرف", Toast.LENGTH_SHORT).show();
        });
    }

    private void notifyGiftSheetRecipientsChanged() {
        try {
            androidx.fragment.app.Fragment f = getSupportFragmentManager().findFragmentByTag("gifts");
            if (f instanceof GiftBottomSheet sheet) {
                sheet.refreshRecipients();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @NonNull
    public List<GiftRecipient> getGiftRecipients() {
        // Seated guests + live host on stage. Host is always first (TikTok-style).
        // Never include yourself — room owner/host cannot self-support.
        List<GiftRecipient> out = new ArrayList<>();
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        String liveHostId = resolveLiveHostId(room);
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

        // 1) Pin the live host first when giftable (skip if that host is me).
        if (liveHostId != null && !liveHostId.isEmpty()
                && (myUserId == null || !myUserId.equals(liveHostId))) {
            String name = null;
            String avatar = null;
            String hostBadge = null;
            String vipBadge = null;
            RoomDtos.SeatDto hostSeat = findHostSeat(currentSeats);
            if (hostSeat != null && liveHostId.equals(seatUserId(hostSeat)) && hostSeat.user != null) {
                name = hostSeat.user.displayName != null
                        ? hostSeat.user.displayName : hostSeat.user.username;
                avatar = hostSeat.user.avatarUrl;
                hostBadge = hostSeat.user.hostBadgeUrl;
                vipBadge = hostSeat.user.vipBadgeUrl;
            }
            if ((name == null || name.isEmpty()) && room != null && room.host != null) {
                name = room.host.displayName != null ? room.host.displayName : room.host.username;
                avatar = room.host.avatarUrl;
                hostBadge = room.host.hostBadgeUrl;
                vipBadge = room.host.vipBadgeUrl;
            }
            if ((name == null || name.isEmpty()) && binding != null && binding.tvHostStageName != null) {
                CharSequence stageName = binding.tvHostStageName.getText();
                if (stageName != null && stageName.length() > 0) name = stageName.toString();
            }
            if (name == null || name.isEmpty()) name = "المضيف";
            int hostSeatIdx = -1;
            if (hostSeat != null) hostSeatIdx = hostSeat.seatIndex;
            long hostSupport = seatAdapter != null ? seatAdapter.getGiftCoins(liveHostId) : 0L;
            int hostLevel = 0;
            if (hostSeat != null && hostSeat.user != null) {
                hostLevel = Math.max(0, hostSeat.user.level);
            } else if (room != null && room.host != null) {
                hostLevel = Math.max(0, room.host.level);
            }
            out.add(new GiftRecipient(
                    liveHostId, name, avatar, hostBadge, vipBadge,
                    hostSeatIdx, true, hostSupport, hostLevel));
            seen.add(liveHostId);
        }

        // 2) Everyone else currently on a mic seat (never include self).
        if (currentSeats != null) {
            List<RoomDtos.SeatDto> ordered = new ArrayList<>(currentSeats);
            ordered.sort((a, b) -> Integer.compare(
                    a != null ? a.seatIndex : 0, b != null ? b.seatIndex : 0));
            for (RoomDtos.SeatDto seat : ordered) {
                String uid = seatUserId(seat);
                if (uid == null || uid.isEmpty()) continue;
                if (myUserId != null && myUserId.equals(uid)) continue;
                if (!seen.add(uid)) continue;
                String name = seat.user != null
                        ? (seat.user.displayName != null ? seat.user.displayName : seat.user.username)
                        : getString(R.string.voice_room);
                if (name == null || name.isEmpty()) name = getString(R.string.voice_room);
                String avatar = seat.user != null ? seat.user.avatarUrl : null;
                String hostBadge = seat.user != null ? seat.user.hostBadgeUrl : null;
                String vipBadge = seat.user != null ? seat.user.vipBadgeUrl : null;
                boolean host = liveHostId != null && liveHostId.equals(uid);
                long support = seatAdapter != null ? seatAdapter.getGiftCoins(uid) : 0L;
                int level = seat.user != null ? Math.max(0, seat.user.level) : 0;
                out.add(new GiftRecipient(
                        uid, name, avatar, hostBadge, vipBadge,
                        seat.seatIndex, host, support, level));
            }
        }
        return out;
    }

    /** Prefer the broadcaster currently on stage over the persistent room owner id. */
    @Nullable
    private String resolveLiveHostId(@Nullable RoomDtos.RoomDto room) {
        if (room != null && room.activeHostId != null && !room.activeHostId.isEmpty()) {
            return room.activeHostId;
        }
        if (room != null && room.hostId != null && !room.hostId.isEmpty()) return room.hostId;
        if (roomHostId != null && !roomHostId.isEmpty()) return roomHostId;
        if (hostStageUserId != null && !hostStageUserId.isEmpty()) return hostStageUserId;
        RoomDtos.SeatDto hostSeat = findHostSeat(currentSeats);
        return seatUserId(hostSeat);
    }

    /** Live host / owner / cohost may send gifts to themselves (room support). */
    private boolean canHostGiftSelf() {
        if (myUserId == null || myUserId.isEmpty()) return false;
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        String liveHostId = resolveLiveHostId(room);
        return sameUser(myUserId, liveHostId)
                || sameUser(myUserId, roomHostId)
                || sameUser(myUserId, roomCohostId)
                || (room != null && sameUser(myUserId, room.activeHostId));
    }

    @Override
    @Nullable
    public String getDefaultGiftReceiverId() {
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        String liveHostId = resolveLiveHostId(room);
        // Never default to yourself — room owner cannot self-support.
        if (liveHostId != null && !liveHostId.isEmpty()
                && (myUserId == null || !myUserId.equals(liveHostId))) {
            return liveHostId;
        }
        if (currentSeats != null) {
            for (RoomDtos.SeatDto seat : currentSeats) {
                String uid = seatUserId(seat);
                if (uid == null || uid.isEmpty()) continue;
                if (myUserId != null && myUserId.equals(uid)) continue;
                if (isFemaleUser(seat.user)) return uid;
            }
        }
        List<GiftRecipient> recipients = getGiftRecipients();
        for (GiftRecipient r : recipients) {
            if (r == null || r.userId == null || r.userId.isEmpty()) continue;
            if (myUserId != null && myUserId.equals(r.userId)) continue;
            return r.userId;
        }
        return null;
    }

    @Override
    public boolean isAgencyGiftRoom() {
        return isAgencyRoom;
    }

    private static boolean isFemaleUser(@Nullable AuthDtos.UserDto user) {
        if (user == null || user.gender == null) return false;
        String g = user.gender.trim().toLowerCase(java.util.Locale.US);
        return "female".equals(g) || "f".equals(g) || "woman".equals(g);
    }

    @Nullable
    private View findSeatViewForUser(String userId) {
        if (userId == null || userId.isEmpty() || binding == null) return null;
        // Prefer guest-grid seat when host hopped down; card is fixed identity only.
        if (binding.recyclerSeats != null) {
            int adapterPos = guestSeatAdapterIndex(userId);
            if (adapterPos >= 0) {
                RecyclerView.ViewHolder holder =
                        binding.recyclerSeats.findViewHolderForAdapterPosition(adapterPos);
                if (holder != null) return holder.itemView;
            }
        }
        if (roomHostId != null && sameUser(userId, roomHostId)) {
            if (binding.hostCard != null) return binding.hostCard;
            if (binding.headerHostAvatarWrap != null) return binding.headerHostAvatarWrap;
        }
        RoomDtos.SeatDto hostSeat = findHostSeat(currentSeats);
        if (hostSeat != null && sameUser(userId, seatUserId(hostSeat))) {
            if (binding.hostCard != null) return binding.hostCard;
            if (binding.headerHostAvatarWrap != null) return binding.headerHostAvatarWrap;
        }
        return null;
    }

    /** Same order as SeatAdapter grid (guest seats only). */
    private int guestSeatAdapterIndex(@Nullable String userId) {
        if (userId == null || userId.isEmpty()) return -1;
        List<RoomDtos.SeatDto> guests = prepareGuestSeatsForGrid(currentSeats);
        for (int i = 0; i < guests.size(); i++) {
            if (sameUser(userId, seatUserId(guests.get(i)))) return i;
        }
        return -1;
    }

    private void applyGiftToSeat(String receiverId, long coins, long roomTotal) {
        if (receiverId == null || receiverId.isEmpty() || seatAdapter == null) return;
        if (roomTotal > 0) seatAdapter.setGiftCoins(receiverId, roomTotal);
        else if (coins > 0) seatAdapter.addGiftCoins(receiverId, coins);
        updateHostStageGiftCount(receiverId);
    }

    private void updateHostStageGiftCount(@Nullable String receiverId) {
        if (binding == null || binding.tvHostStageGiftCount == null || seatAdapter == null) return;
        RoomDtos.SeatDto hostSeat = findHostSeat(currentSeats);
        String seat0User = seatUserId(hostSeat);
        if (seat0User == null || seat0User.isEmpty()) {
            binding.tvHostStageGiftCount.setVisibility(View.GONE);
            lastHostGiftCoins = 0L;
            return;
        }
        if (receiverId != null && !receiverId.isEmpty() && !sameUser(receiverId, seat0User)) return;
        long coins = seatAdapter.getGiftCoins(seat0User);
        if (coins <= 0) {
            binding.tvHostStageGiftCount.setVisibility(View.GONE);
            lastHostGiftCoins = 0L;
            return;
        }
        binding.tvHostStageGiftCount.setVisibility(View.VISIBLE);
        setTextIfChanged(binding.tvHostStageGiftCount, SeatAdapter.formatGiftCoinsLabel(coins));
        if (lastHostGiftCoins == coins) return;
        lastHostGiftCoins = coins;
        int coinPx = Math.round(14 * getResources().getDisplayMetrics().density);
        android.graphics.drawable.Drawable coin =
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.icon_coin);
        if (coin != null) {
            coin = coin.mutate();
            coin.setBounds(0, 0, coinPx, coinPx);
            binding.tvHostStageGiftCount.setCompoundDrawablesRelative(coin, null, null, null);
            binding.tvHostStageGiftCount.setCompoundDrawablePadding(Math.round(4 * getResources().getDisplayMetrics().density));
        }
        binding.tvHostStageGiftCount.animate().cancel();
        binding.tvHostStageGiftCount.setScaleX(1.25f);
        binding.tvHostStageGiftCount.setScaleY(1.25f);
        binding.tvHostStageGiftCount.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .start();
    }

    /** Chat line for lucky gift without killing the coin-rain overlay. */
    public void announceLuckyGiftChat(
            @Nullable String giftName,
            @Nullable String iconUrl,
            @Nullable String senderName,
            int comboCount,
            @Nullable String senderUserId,
            int senderVipLevel,
            @Nullable String senderAvatarUrl,
            int senderUserLevel,
            @Nullable String senderFrameUrl
    ) {
        String me = senderName;
        if (me == null || me.isEmpty()) me = "مستخدم";
        String chatUserId = senderUserId;
        boolean selfGift = chatUserId != null && myUserId != null && sameUser(chatUserId, myUserId);
        int vip = Math.max(0, senderVipLevel);
        int level = Math.max(1, senderUserLevel);
        String frame = senderFrameUrl;
        if (selfGift) {
            var session = ContainerProvider.from(this).getSessionManager();
            if (vip <= 0) vip = Math.max(0, session.getVipLevel());
            if (level <= 1) level = Math.max(1, session.getUserLevel());
            if (frame == null || frame.isEmpty()) {
                AuthDtos.UserDto selfUser = session.getUser();
                frame = isAgencyRoom
                        ? (selfUser != null ? selfUser.hostBadgeUrl : session.getHostBadgeUrl())
                        : (selfUser != null ? selfUser.vipBadgeUrl : null);
            }
            if ("مستخدم".equals(me)) {
                String dn = session.getDisplayName();
                if (dn != null && !dn.isEmpty()) me = dn;
            }
        }
        String chatGift = "أرسل هدية حظ " + (giftName != null ? giftName : "");
        // Quantity/combo streak is independent of مـردود — do not show ×N here.
        // (مردود appears only via announceLuckyWinChat / lucky:hit banner.)
        appendChatLine(me, chatGift, vip, level, frame, chatUserId, senderAvatarUrl, iconUrl);
        // Always toast as 1 — gift combo strip is for normal/combo gifts only.
        showGiftSendToast(me, giftName, senderAvatarUrl, iconUrl, 1, 0, "أرسل هدية حظ");
    }

    /** Chat-only lucky win line (toast shown separately via ComingMsgView). */
    public void announceLuckyWinChat(
            @Nullable String displayName,
            @Nullable String line,
            @Nullable String avatarUrl
    ) {
        if (line == null || line.isEmpty()) return;
        String who = displayName != null && !displayName.isEmpty() ? displayName : "مستخدم";
        if (roomBannerEnabled()) {
            appendChatLine(who, line, 0, 1, null, null, avatarUrl, null);
        }
    }

    /** Small join-style toast for lucky win (never a full-screen dialog). */
    public void showLuckyResultToast(
            @Nullable String displayName,
            @Nullable String avatarUrl,
            int userLevel,
            int vipLevel,
            @Nullable String message
    ) {
        if (areCelebrationPopupsMuted()) return;
        // Lucky gift result uses game-style crawl bubble — not ComingMsg (personal join strip).
        String who = displayName != null && !displayName.isEmpty() ? displayName : "لاعب";
        String msg = message != null && !message.isEmpty() ? message : "حظ سعيد";
        RoomVisualEffects bubbleFx = giftVisualEffects != null ? giftVisualEffects : visualEffects;
        if (bubbleFx != null) {
            bubbleFx.showSlotWinBubble(who, null, Math.max(1L, extractCoinsFromBody(msg)), "حظ", null);
            if (binding != null && binding.giftChatEffects != null) {
                binding.giftChatEffects.setVisibility(View.VISIBLE);
                binding.giftChatEffects.bringToFront();
            }
        } else if (binding != null && binding.comingMsgView != null) {
            binding.comingMsgView.setupView(displayName, avatarUrl, userLevel, vipLevel, msg);
            binding.comingMsgView.bringToFront();
            binding.comingMsgView.play(3600L);
        }
        if (roomBannerEnabled()) {
            appendGameCelebrationLine(msg, null);
        }
    }

    /** World celebration while inside a room — Migo bubble (name/avatar/game/win) + chat. */
    public void onGlobalCelebration(
            @Nullable String kind,
            @Nullable String title,
            @Nullable String body,
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey
    ) {
        onGlobalCelebration(kind, title, body, displayName, avatarUrl, badgeUrl, dedupeKey,
                0L, null, null);
    }

    public void onGlobalCelebration(
            @Nullable String kind,
            @Nullable String title,
            @Nullable String body,
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            long coinsWon,
            @Nullable String gameTitle
    ) {
        onGlobalCelebration(kind, title, body, displayName, avatarUrl, badgeUrl, dedupeKey,
                coinsWon, gameTitle, null);
    }

    public void onGlobalCelebration(
            @Nullable String kind,
            @Nullable String title,
            @Nullable String body,
            @Nullable String displayName,
            @Nullable String avatarUrl,
            @Nullable String badgeUrl,
            @Nullable String dedupeKey,
            long coinsWon,
            @Nullable String gameTitle,
            @Nullable String targetRoomId
    ) {
        if (areCelebrationPopupsMuted()) return;
        String who = displayName != null && !displayName.isEmpty() ? displayName : "لاعب";
        String line = body != null && !body.isEmpty()
                ? body
                : (title != null ? title : "مبروك!");
        boolean isGame = kind != null && "game_win".equalsIgnoreCase(kind);
        boolean isLucky = kind != null && "lucky_hit".equalsIgnoreCase(kind);
        boolean isPlanet = kind != null && "planet_summon".equalsIgnoreCase(kind);
        // Prefer chat-lane stage so bubbles match message recycle area (Mikoo).
        RoomVisualEffects bubbleFx = giftVisualEffects != null ? giftVisualEffects : visualEffects;

        if (isPlanet) {
            boolean otherRoom = targetRoomId != null && !targetRoomId.isEmpty()
                    && (roomId == null || !targetRoomId.equals(roomId));
            if (otherRoom) {
                // Other room summons keep a Go chip — still not a system Toast.
                GlobalCelebrationToast.show(
                        this, title, line, avatarUrl, badgeUrl, dedupeKey,
                        56, 0, 0L, who, kind, targetRoomId);
            } else if (bubbleFx != null) {
                String msg = title != null && !title.isEmpty() ? title : line;
                bubbleFx.showRoomEventBubble(who, avatarUrl, msg, badgeUrl);
                if (binding != null && binding.giftChatEffects != null) {
                    binding.giftChatEffects.setVisibility(View.VISIBLE);
                    binding.giftChatEffects.bringToFront();
                }
            }
            if (roomBannerEnabled()) {
                appendChatLine(who, line, 0, 1, null, null, avatarUrl, badgeUrl);
            }
            return;
        }

        if (isGame) {
            long coins = coinsWon > 0 ? coinsWon : extractCoinsFromBody(line);
            String game = gameTitle != null && !gameTitle.isEmpty()
                    ? gameTitle
                    : extractGameTitleFromBody(line);
            if (bubbleFx != null) {
                // No personal avatar on game win crawl.
                bubbleFx.showSlotWinBubble(who, null, Math.max(1L, coins), game, badgeUrl);
            }
            if (binding != null && binding.giftChatEffects != null) {
                binding.giftChatEffects.setVisibility(View.VISIBLE);
                binding.giftChatEffects.bringToFront();
                binding.giftChatEffects.setElevation(42f);
            }
            String chat = "مبروك " + who + " حصل على " + Math.max(1L, coins)
                    + (game != null && !game.isEmpty() ? (" · " + game) : "");
            if (roomBannerEnabled()) {
                appendGameCelebrationLine(chat, badgeUrl);
            }
            return;
        }

        String toastMsg;
        String chat;
        if (isLucky) {
            long coins = coinsWon > 0 ? coinsWon : extractCoinsFromBody(line);
            toastMsg = coins > 0
                    ? ("أرسل هدايا حظ وفاز بـ " + coins)
                    : "أرسل هدايا حظ وفاز";
            chat = coins > 0
                    ? ("مبروك " + who + " فاز بـ " + coins + " عملة · حظ")
                    : ("مبروك " + who + " · حظ");
            if (bubbleFx != null && coins > 0) {
                bubbleFx.showSlotWinBubble(who, null, coins, "حظ", badgeUrl);
                if (binding != null && binding.giftChatEffects != null) {
                    binding.giftChatEffects.setVisibility(View.VISIBLE);
                    binding.giftChatEffects.bringToFront();
                }
            } else if (bubbleFx != null) {
                bubbleFx.showRoomEventBubble(null, null, who + " · " + toastMsg, badgeUrl);
            }
            if (roomBannerEnabled()) {
                appendGameCelebrationLine(chat, badgeUrl);
            }
            return;
        } else {
            toastMsg = line;
            chat = line;
        }
        if (bubbleFx != null) {
            bubbleFx.showRoomEventBubble(who, avatarUrl, toastMsg, badgeUrl);
            if (binding != null && binding.giftChatEffects != null) {
                binding.giftChatEffects.setVisibility(View.VISIBLE);
                binding.giftChatEffects.bringToFront();
            }
        }
        if (roomBannerEnabled()) {
            appendChatLine(who, chat, 0, 1, null, null, avatarUrl, badgeUrl);
        }
    }

    private static long extractCoinsFromBody(@Nullable String body) {
        if (body == null || body.isEmpty()) return 0L;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:فاز\\s*بـ?\\s*|\\+|·\\s*)(\\d{2,})")
                .matcher(body);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {
            }
        }
        m = java.util.regex.Pattern.compile("(\\d{2,})").matcher(body);
        long best = 0L;
        while (m.find()) {
            try {
                best = Math.max(best, Long.parseLong(m.group(1)));
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    @Nullable
    private static String extractGameTitleFromBody(@Nullable String body) {
        if (body == null || body.isEmpty()) return null;
        // "… لعب Solo 77 وفاز بـ 777"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("لعب\\s+(.+?)\\s+وفاز")
                .matcher(body);
        if (m.find()) {
            String g = m.group(1).trim();
            return g.isEmpty() ? null : g;
        }
        return null;
    }

    /** Credit gift coins on a seat without playing another stage animation. */
    public void creditGiftCoinsOnSeat(@Nullable String receiverId, long coinValue) {
        if (receiverId == null || receiverId.isEmpty() || coinValue <= 0) return;
        applyGiftToSeat(receiverId, coinValue, 0L);
    }

    private void markLuckyOverlayActive(long holdMs) {
        luckyOverlayActive = true;
        if (luckyOverlayRelease != null) handler.removeCallbacks(luckyOverlayRelease);
        luckyOverlayRelease = () -> {
            luckyOverlayActive = false;
            luckyOverlayRelease = null;
            if (binding != null && binding.giftOverlay != null
                    && binding.giftOverlay.getChildCount() == 0) {
                binding.giftOverlay.setVisibility(View.GONE);
            }
        };
        handler.postDelayed(luckyOverlayRelease, Math.max(1200L, holdMs));
    }

    /** Gold coins rain toward seated targets (lucky / مردود mega-style). */
    public void playCoinRainToUsers(@Nullable List<String> userIds, int coinCount) {
        playCoinRainToUsers(userIds, coinCount, true);
    }

    private void playCoinRainToUsers(@Nullable List<String> userIds, int coinCount, boolean allowRetry) {
        if (binding == null || binding.giftOverlay == null) return;
        // Prevent stacking rains (gift + mardood) from exploding view count on budget phones.
        try {
            if (binding.giftOverlay.getChildCount() > 40) {
                return;
            }
        } catch (Exception ignored) {
        }
        int safeCount = Math.max(4, Math.min(18, coinCount));
        List<android.graphics.PointF> targets = resolveMicCenters(userIds);
        if (targets.isEmpty() && allowRetry && userIds != null && !userIds.isEmpty()) {
            final List<String> ids = new ArrayList<>(userIds);
            final int n = safeCount;
            binding.giftOverlay.setVisibility(View.VISIBLE);
            markLuckyOverlayActive(2500L);
            binding.giftOverlay.post(() -> playCoinRainToUsers(ids, n, false));
            return;
        }
        binding.giftOverlay.setVisibility(View.VISIBLE);
        binding.giftOverlay.bringToFront();
        binding.giftOverlay.setElevation(28f);
        markLuckyOverlayActive(4500L);
        try {
            CoinRainAnimator.rain(binding.giftOverlay, targets.isEmpty() ? null : targets, safeCount, null);
        } catch (OutOfMemoryError | Exception ignored) {
        }
    }

    @NonNull
    private List<android.graphics.PointF> resolveMicCenters(@Nullable List<String> userIds) {
        List<android.graphics.PointF> targets = new ArrayList<>();
        if (binding == null || binding.giftOverlay == null || userIds == null) return targets;
        for (String uid : userIds) {
            if (uid == null || uid.isEmpty()) continue;
            View seat = findSeatViewForUser(uid);
            if (seat == null) continue;
            android.graphics.PointF p =
                    GiftFlyAnimator.centerInOverlay(binding.giftOverlay, seat);
            if (p != null) targets.add(p);
        }
        return targets;
    }

    private void startRoomGmtClock() {
        updateRoomGmtClock();
        handler.removeCallbacks(roomGmtClockTick);
        handler.postDelayed(roomGmtClockTick, 30_000L);
    }

    private void updateRoomGmtClock() {
        if (binding == null || binding.tvRoomGmtClock == null) return;
        try {
            java.util.TimeZone tz = java.util.TimeZone.getDefault();
            long now = System.currentTimeMillis();
            int totalMin = tz.getOffset(now) / 60_000;
            int absMin = Math.abs(totalMin);
            String sign = totalMin >= 0 ? "+" : "-";
            int hours = absMin / 60;
            int mins = absMin % 60;
            String gmt = mins == 0
                    ? String.format(java.util.Locale.US, "GMT%s%d", sign, hours)
                    : String.format(java.util.Locale.US, "GMT%s%d:%02d", sign, hours, mins);
            java.text.SimpleDateFormat dateFmt =
                    new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US);
            dateFmt.setTimeZone(tz);
            binding.tvRoomGmtClock.setText(gmt + "\n" + dateFmt.format(new java.util.Date(now)));
            binding.tvRoomGmtClock.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
            binding.tvRoomGmtClock.setVisibility(View.GONE);
        }
    }

    /**
     * Mikoo lucky / multi-mic FX:
     * large center gift · Nx · banner · simultaneous clone arcs to mics.
     */
    public void playLuckyGiftStage(
            @Nullable String giftIconUrl,
            @Nullable List<String> targetIds,
            long coinsSpent,
            int quantity,
            int personCount,
            @Nullable Runnable onScatterStart
    ) {
        playLuckyGiftStage(giftIconUrl, targetIds, coinsSpent, quantity, personCount,
                null, onScatterStart);
    }

    public void playLuckyGiftStage(
            @Nullable String giftIconUrl,
            @Nullable List<String> targetIds,
            long coinsSpent,
            int quantity,
            int personCount,
            @Nullable String senderName,
            @Nullable Runnable onScatterStart
    ) {
        if (binding == null || binding.giftOverlay == null) {
            if (onScatterStart != null) {
                try { onScatterStart.run(); } catch (Exception ignored) {}
            }
            return;
        }
        List<String> ids = targetIds;
        if (ids == null || ids.isEmpty()) {
            ids = collectOccupiedMicUserIds();
        }
        final List<String> rainIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>();
        if (rainIds.isEmpty()) {
            List<String> all = collectOccupiedMicUserIds();
            if (all != null) rainIds.addAll(all);
        }

        binding.giftOverlay.setVisibility(View.VISIBLE);
        binding.giftOverlay.bringToFront();
        binding.giftOverlay.setElevation(36f);
        markLuckyOverlayActive(5600L);

        final List<String> pulseIds = rainIds;
        final int qtyShow = Math.max(1, quantity);
        final int people = Math.max(1, rainIds.isEmpty() ? personCount : rainIds.size());
        final long totalScore = Math.max(0L, coinsSpent > 0 ? coinsSpent : (long) qtyShow * people);
        final long hold = Math.min(1600L, 620L + Math.max(1, quantity) * 70L);
        final String who = senderName != null && !senderName.isEmpty()
                ? senderName
                : (myUserId != null ? displayNameForSeatUser(myUserId) : "مستخدم");
        final String icon = giftIconUrl;
        final Runnable scatterCb = onScatterStart;

        Runnable startClone = () -> {
            if (binding == null || binding.giftOverlay == null) {
                if (scatterCb != null) {
                    try { scatterCb.run(); } catch (Exception ignored) {}
                }
                return;
            }
            List<android.graphics.PointF> seatPts = resolveMicCenters(pulseIds);
            if (seatPts.isEmpty()) {
                List<String> all = collectOccupiedMicUserIds();
                seatPts = resolveMicCenters(all);
            }
            int w = Math.max(binding.giftOverlay.getWidth(), 1);
            int h = Math.max(binding.giftOverlay.getHeight(), 1);
            android.graphics.PointF center = new android.graphics.PointF(w / 2f, h * 0.40f);
            GiftFlyAnimator.allMicClone(
                    binding.giftOverlay,
                    icon,
                    center,
                    seatPts,
                    qtyShow,
                    totalScore,
                    who,
                    hold,
                    () -> {
                        if (scatterCb != null) {
                            try { scatterCb.run(); } catch (Exception ignored) {}
                        }
                        for (String uid : pulseIds) {
                            View seat = findSeatViewForUser(uid);
                            if (seat != null) GiftFlyAnimator.pulseTarget(seat);
                        }
                    },
                    () -> {
                        if (coinsSpent >= 200 && !pulseIds.isEmpty()) {
                            playCoinRainToUsers(pulseIds, 6);
                        }
                    });
        };
        if (binding.giftOverlay.getWidth() <= 0 || binding.giftOverlay.getHeight() <= 0) {
            binding.giftOverlay.post(startClone);
        } else {
            startClone.run();
        }
    }

    /** Center → clone → mics for regular all-mic gift sends. */
    public void scatterGiftToMics(
            @Nullable String giftIconUrl,
            @Nullable List<String> targetIds,
            int comboCount
    ) {
        scatterGiftToMics(giftIconUrl, targetIds, comboCount, 1, 0, null);
    }

    public void scatterGiftToMics(
            @Nullable String giftIconUrl,
            @Nullable List<String> targetIds,
            int comboCount,
            int quantity,
            long coinValuePerPerson,
            @Nullable String senderName
    ) {
        if (binding == null || binding.giftOverlay == null) return;
        List<String> ids = targetIds != null && !targetIds.isEmpty()
                ? targetIds : collectOccupiedMicUserIds();
        binding.giftOverlay.setVisibility(View.VISIBLE);
        binding.giftOverlay.bringToFront();
        markLuckyOverlayActive(4800L);
        final List<String> finalIds = ids;
        final int qtyShow = Math.max(1, quantity > 1 ? quantity : Math.max(1, comboCount));
        final long total = coinValuePerPerson > 0
                ? coinValuePerPerson * Math.max(1, ids.size())
                : (long) qtyShow * Math.max(1, ids.size());
        final String who = senderName != null && !senderName.isEmpty()
                ? senderName
                : displayNameForSeatUser(myUserId);
        final String icon = giftIconUrl;
        Runnable startClone = () -> {
            if (binding == null || binding.giftOverlay == null) return;
            List<android.graphics.PointF> seats = resolveMicCenters(finalIds);
            if (seats.isEmpty()) return;
            int w = Math.max(binding.giftOverlay.getWidth(), 1);
            int h = Math.max(binding.giftOverlay.getHeight(), 1);
            android.graphics.PointF center = new android.graphics.PointF(w / 2f, h * 0.40f);
            GiftFlyAnimator.allMicClone(
                    binding.giftOverlay,
                    icon,
                    center,
                    seats,
                    qtyShow,
                    total,
                    who,
                    780L,
                    () -> {
                        for (String uid : finalIds) {
                            View seat = findSeatViewForUser(uid);
                            if (seat != null) GiftFlyAnimator.pulseTarget(seat);
                        }
                    },
                    null);
        };
        if (binding.giftOverlay.getWidth() <= 0 || binding.giftOverlay.getHeight() <= 0) {
            binding.giftOverlay.post(startClone);
        } else {
            startClone.run();
        }
    }

    /** All-mic send: center hold → clone to every target seat + one media stage. */
    public void playLuckyGiftToAllMics(String giftName, String iconUrl, String animationUrl,
                                       String senderName, int comboCount,
                                       String senderUserId, int senderVipLevel,
                                       String senderAvatarUrl, List<String> targetIds, long coinValue) {
        playLuckyGiftToAllMics(giftName, iconUrl, animationUrl, senderName, comboCount,
                1, senderUserId, senderVipLevel, senderAvatarUrl, targetIds, coinValue);
    }

    public void playLuckyGiftToAllMics(String giftName, String iconUrl, String animationUrl,
                                       String senderName, int comboCount, int quantity,
                                       String senderUserId, int senderVipLevel,
                                       String senderAvatarUrl, List<String> targetIds, long coinValue) {
        if (targetIds == null || targetIds.isEmpty()) return;
        String playTo = null;
        for (String tid : targetIds) {
            if (tid == null || tid.isEmpty()) continue;
            if (playTo == null) {
                playTo = tid;
            } else if (coinValue > 0) {
                creditGiftCoinsOnSeat(tid, coinValue);
            }
        }
        if (playTo == null) return;
        scatterGiftToMics(iconUrl, targetIds, comboCount, Math.max(1, quantity), coinValue, senderName);
        // First target: seat credit + effect queue / media (clones already flying).
        playGiftToRecipient(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, playTo, coinValue, true);
    }

    /** @deprecated Big center ×N card removed. */
    @Deprecated
    public void playLuckyWinBurst(int multiplier, long coinsWon, @Nullable Runnable after) {
        if (after != null) after.run();
    }

    /** @deprecated Big center ×N card removed. */
    @Deprecated
    public void playLuckyWinBurst(
            int multiplier,
            long coinsWon,
            @Nullable List<String> targetIds,
            @Nullable Runnable after
    ) {
        if (after != null) after.run();
    }

    /** Burst coins from screen center on soft مردود. */
    public void playLuckyCoinBurst(int multiplier) {
        if (binding == null || binding.giftOverlay == null) return;
        binding.giftOverlay.setVisibility(View.VISIBLE);
        binding.giftOverlay.bringToFront();
        markLuckyOverlayActive(2200L);
        int w = Math.max(binding.giftOverlay.getWidth(), 1);
        int h = Math.max(binding.giftOverlay.getHeight(), 1);
        android.graphics.PointF center = new android.graphics.PointF(w / 2f, h * 0.4f);
        CoinRainAnimator.burstFrom(
                binding.giftOverlay,
                center,
                Math.min(70, 22 + Math.max(1, multiplier) * 10),
                null);
    }

    /** Float "+N" above the winner seat only (sender), not every mic. */
    public void showLuckyReturnOnMics(@Nullable List<String> userIds, long coinsWon) {
        if (binding == null || binding.giftOverlay == null || coinsWon <= 0) return;
        List<String> ids = userIds;
        if (ids == null || ids.isEmpty()) return;
        binding.giftOverlay.setVisibility(View.VISIBLE);
        binding.giftOverlay.bringToFront();
        markLuckyOverlayActive(4200L);
        float density = getResources().getDisplayMetrics().density;
        int index = 0;
        for (String uid : ids) {
            View seat = findSeatViewForUser(uid);
            if (seat == null) continue;
            android.graphics.PointF p =
                    GiftFlyAnimator.centerInOverlay(binding.giftOverlay, seat);
            if (p == null) continue;
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("+" + coinsWon);
            tv.setTextColor(0xFFFFE082);
            tv.setTextSize(14f);
            tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tv.setShadowLayer(5f, 0f, 2f, 0xF0000000);
            tv.setMaxLines(1);
            tv.setPadding((int) (10 * density), (int) (4 * density),
                    (int) (10 * density), (int) (4 * density));
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(14f * density);
            bg.setColor(0xCC1A0A00);
            bg.setStroke((int) (1.5f * density), 0xFFFFC107);
            tv.setBackground(bg);
            tv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            tv.setX(Math.max(8f * density,
                    Math.min(p.x - tv.getMeasuredWidth() / 2f,
                            (binding.giftOverlay.getWidth() > 0
                                    ? binding.giftOverlay.getWidth()
                                    : getResources().getDisplayMetrics().widthPixels)
                                    - tv.getMeasuredWidth() - 8f * density)));
            tv.setY(p.y - 36f * density);
            tv.setAlpha(0f);
            tv.setScaleX(0.55f);
            tv.setScaleY(0.55f);
            binding.giftOverlay.addView(tv);
            long stagger = index * 120L;
            index++;
            tv.animate()
                    .alpha(1f)
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .translationYBy(-28f * density)
                    .setStartDelay(stagger)
                    .setDuration(560)
                    .withEndAction(() -> tv.animate()
                            .alpha(0f)
                            .translationYBy(-18f * density)
                            .setDuration(480)
                            .setStartDelay(1800)
                            .withEndAction(() -> {
                                if (tv.getParent() instanceof android.view.ViewGroup parent) {
                                    parent.removeView(tv);
                                }
                            })
                            .start())
                    .start();
        }
    }

    @NonNull
    private String displayNameForSeatUser(@Nullable String userId) {
        if (userId == null || userId.isEmpty() || currentSeats == null) return "لاعب";
        for (RoomDtos.SeatDto seat : currentSeats) {
            if (seat == null || !sameUser(userId, seatUserId(seat))) continue;
            if (seat.user != null) {
                return firstNonEmpty(seat.user.displayName, seat.user.username, "لاعب");
            }
        }
        return "لاعب";
    }

    @NonNull
    private List<String> collectOccupiedMicUserIds() {
        List<String> out = new ArrayList<>();
        if (currentSeats == null) return out;
        for (RoomDtos.SeatDto seat : currentSeats) {
            String uid = seatUserId(seat);
            if (uid == null || uid.isEmpty()) continue;
            if (!out.contains(uid)) out.add(uid);
        }
        return out;
    }

    @NonNull
    public List<String> collectOccupiedMicUserIdsPublic() {
        return collectOccupiedMicUserIds();
    }

    /** Room banner when the sender hits a lucky multiplier (or soft partial return). */
    public void showLuckyHitBanner(@Nullable String displayName, @Nullable String avatarUrl,
                                   long wonCoins, int multiplier) {
        String who = displayName != null && !displayName.isEmpty() ? displayName : "مستخدم";
        String line;
        if (multiplier <= 0) {
            line = "ضرب حظه · مردود +" + Math.max(0L, wonCoins);
        } else {
            int mul = Math.max(1, multiplier);
            line = "ضرب حظه وربح ×" + mul + " · +" + Math.max(0L, wonCoins);
        }
        // Small join-style toast only — never the big crawling overlay.
        showLuckyResultToast(who, avatarUrl, 1, 0, line);
        if (roomBannerEnabled()) {
            appendChatLine(who, line, 0, 1, null, null, avatarUrl, null);
        }
    }

    /** Official-news style crawl — disabled (too large). Use ComingMsgView instead. */
    private void showLuckyScreenNotice(
            @Nullable String who,
            @Nullable String avatarUrl,
            @NonNull String line
    ) {
        // Intentionally empty — big crawling strip removed.
        showLuckyResultToast(who, avatarUrl, 1, 0, line);
    }

    /** System line for gift payout / host diamonds (lucky + normal). */
    public void appendGiftEconomyLine(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return;
        appendChatLine("النظام", text.trim(), 0, 1, null, null, null, null);
    }

    public void playGiftToRecipient(String giftName, String iconUrl, String animationUrl,
                                    String senderName, int comboCount,
                                    String senderUserId, int senderVipLevel,
                                    String senderAvatarUrl, String senderFrameUrl,
                                    String receiverId, long coinValue) {
        playGiftToRecipient(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, senderFrameUrl, 1,
                receiverId, coinValue, true);
    }

    public void playGiftToRecipient(String giftName, String iconUrl, String animationUrl,
                                    String senderName, int comboCount,
                                    String senderUserId, int senderVipLevel,
                                    String senderAvatarUrl,
                                    String receiverId, long coinValue) {
        playGiftToRecipient(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, null, 1,
                receiverId, coinValue, true);
    }

    public void playGiftToRecipient(String giftName, String iconUrl, String animationUrl,
                                    String senderName, int comboCount,
                                    String senderUserId, int senderVipLevel,
                                    String senderAvatarUrl,
                                    String receiverId, long coinValue, boolean updateSeatCoins) {
        playGiftToRecipient(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, null, 1,
                receiverId, coinValue, updateSeatCoins);
    }

    public void playGiftToRecipient(String giftName, String iconUrl, String animationUrl,
                                    String senderName, int comboCount,
                                    String senderUserId, int senderVipLevel,
                                    String senderAvatarUrl, String senderFrameUrl, int senderUserLevel,
                                    String receiverId, long coinValue, boolean updateSeatCoins) {
        if (coinValue > 0 && updateSeatCoins) applyGiftToSeat(receiverId, coinValue, 0L);
        if (effectQueue != null && updateSeatCoins) {
            // Local optimistic send: enqueue once so remote echo can be deduped.
            RoomEffectQueue.EffectType type = coinValue >= 5000
                    ? RoomEffectQueue.EffectType.LEGENDARY_GIFT
                    : RoomEffectQueue.EffectType.GIFT;
            String playAnim = animationUrl;
            String mapped = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                    giftName, iconUrl, animationUrl);
            if (mapped != null) playAnim = mapped;
            boolean video = com.Dramizo.Series.util.CosmeticMedia.kind(
                    com.Dramizo.Series.util.CosmeticMedia.playableUrl(playAnim))
                    == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO;
            // Kick disk cache before the queue plays — first frame from file, not HTTP.
            if (video && playAnim != null) {
                com.Dramizo.Series.util.NativeRoomEffectsView.preloadGiftUrls(
                        this, java.util.Collections.singletonList(playAnim));
            }
            effectQueue.enqueue(new RoomEffectQueue.Builder()
                    .type(type)
                    .displayName(senderName)
                    .senderUserId(senderUserId)
                    .senderVipLevel(senderVipLevel)
                    .userLevel(senderUserLevel)
                    .avatarUrl(senderAvatarUrl)
                    .hostBadgeUrl(senderFrameUrl)
                    .giftName(giftName)
                    .giftIconUrl(iconUrl)
                    .giftAnimationUrl(playAnim)
                    .comboCount(comboCount)
                    .totalCoins(coinValue)
                    .receiverId(receiverId)
                    .receiverGiftCoins(coinValue)
                    .durationMs(video ? 90_000L : 0L)
                    .build(effectQueue));
            return;
        }
        playGiftAnimationRemote(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, senderUserLevel, senderFrameUrl);
        // Icon fly to the receiver seat (Mikoo mid → mic), independent of video stage.
        if (receiverId != null && !receiverId.isEmpty()) {
            flyGiftIconToUser(receiverId, iconUrl, null);
        }
        if (updateSeatCoins && coinValue > 0) {
            applyGiftToSeat(receiverId, coinValue, 0L);
        }
    }

    /** Resolve the real seat/host view (scroll into place if recycled), then fly. */
    private void flyGiftIconToUser(@Nullable String receiverId, @Nullable String iconUrl,
                                   @Nullable Runnable onArrive) {
        if (binding == null || binding.giftOverlay == null) {
            if (onArrive != null) onArrive.run();
            return;
        }
        View seatView = findSeatViewForUser(receiverId);
        int adapterPos = guestSeatAdapterIndex(receiverId);
        if (seatView == null && adapterPos >= 0 && binding.recyclerSeats != null) {
            binding.recyclerSeats.post(() -> {
                View resolved = findSeatViewForUser(receiverId);
                if (resolved == null) {
                    RecyclerView.ViewHolder holder =
                            binding.recyclerSeats.findViewHolderForAdapterPosition(adapterPos);
                    if (holder != null) resolved = holder.itemView;
                }
                startGiftFly(resolved, iconUrl, onArrive);
            });
            return;
        }
        startGiftFly(seatView, iconUrl, onArrive);
    }

    private void startGiftFly(@Nullable View seatView, @Nullable String iconUrl,
                              @Nullable Runnable onArrive) {
        if (binding == null || binding.giftOverlay == null || seatView == null) {
            if (onArrive != null) onArrive.run();
            return;
        }
        View fromView = binding.webVisualEffects != null ? binding.webVisualEffects : binding.giftOverlay;
        PointF from = GiftFlyAnimator.centerInOverlay(binding.giftOverlay, fromView);
        PointF to = GiftFlyAnimator.centerInOverlay(binding.giftOverlay, seatView);
        final View pulseTarget = seatView;
        GiftFlyAnimator.fly(binding.giftOverlay, iconUrl, from, to, () -> {
            GiftFlyAnimator.pulseTarget(pulseTarget);
            if (onArrive != null) onArrive.run();
        });
    }

    public void playGiftAnimation(String giftName) {
        playGiftAnimation(giftName, null, null);
    }

    public void playGiftAnimation(String giftName, String iconUrl, String animationUrl) {
        playGiftAnimation(giftName, iconUrl, animationUrl, 1);
    }

    public void playGiftAnimation(String giftName, String iconUrl, String animationUrl, int comboCount) {
        playGiftAnimationRemote(giftName, iconUrl, animationUrl, null, comboCount);
    }

    public void playGiftAnimationRemote(String giftName, String iconUrl, String animationUrl, String senderName) {
        playGiftAnimationRemote(giftName, iconUrl, animationUrl, senderName, 1, null, 0, null, 1, null);
    }

    public void playGiftAnimationRemote(String giftName, String iconUrl, String animationUrl, String senderName, int comboCount) {
        playGiftAnimationRemote(giftName, iconUrl, animationUrl, senderName, comboCount, null, 0, null, 1, null);
    }

    public void playGiftAnimationRemote(String giftName, String iconUrl, String animationUrl, String senderName,
                                        int comboCount, String senderUserId, int senderVipLevel,
                                        String senderAvatarUrl) {
        playGiftAnimationRemote(giftName, iconUrl, animationUrl, senderName, comboCount,
                senderUserId, senderVipLevel, senderAvatarUrl, 1, null);
    }

    /** Gift send toast — profile + gift icon + combo xN (Mikoo crawl strip). */
    public void showGiftSendToast(@Nullable String senderName,
                                  @Nullable String giftName,
                                  @Nullable String senderAvatarUrl,
                                  @Nullable String giftIconUrl,
                                  int comboCount,
                                  int totalCoins) {
        showGiftSendToast(senderName, giftName, senderAvatarUrl, giftIconUrl,
                comboCount, totalCoins, null);
    }

    public void showGiftSendToast(@Nullable String senderName,
                                  @Nullable String giftName,
                                  @Nullable String senderAvatarUrl,
                                  @Nullable String giftIconUrl,
                                  int comboCount,
                                  int totalCoins,
                                  @Nullable String receiverLabel) {
        if (binding == null || binding.comboGiftView == null) return;
        String recv = receiverLabel != null && !receiverLabel.isEmpty()
                ? receiverLabel
                : (giftName != null && !giftName.isEmpty()
                ? ("أرسل " + giftName)
                : getString(R.string.send));
        String icon = giftIconUrl;
        if (icon == null || icon.trim().isEmpty()) {
            String mapped = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                    giftName, null, null);
            if (mapped != null && mapped.toLowerCase(java.util.Locale.US).endsWith(".mp4")) {
                icon = mapped.replaceAll("(?i)\\.mp4(\\?.*)?$", ".png$1");
            } else if (mapped != null
                    && !mapped.toLowerCase(java.util.Locale.US).endsWith(".mp4")
                    && !mapped.toLowerCase(java.util.Locale.US).endsWith(".svga")) {
                icon = mapped;
            }
        }
        binding.comboGiftView.bringToFront();
        binding.comboGiftView.setElevation(52f);
        binding.comboGiftView.show(
                senderName,
                recv,
                senderAvatarUrl,
                icon,
                Math.max(1, comboCount),
                Math.max(0, totalCoins));
        // Giant left "37x" floating multiplier — gift combo only (never merdood/luck).
        // Do not use totalCoins as fake combo.
        if (comboCount >= 2 && binding.giftOverlay != null) {
            binding.giftOverlay.setVisibility(View.VISIBLE);
            float d = getResources().getDisplayMetrics().density;
            int h = Math.max(binding.giftOverlay.getHeight(),
                    getResources().getDisplayMetrics().heightPixels);
            GiftFlyAnimator.showComboBurst(
                    binding.giftOverlay,
                    comboCount,
                    new android.graphics.PointF(56f * d, h * 0.60f),
                    null);
            // Short overlay hold for gift combo only — do not mark as lucky/mardood.
            try {
                if (luckyOverlayRelease != null) { /* leave lucky alone */ }
            } catch (Exception ignored) {
            }
        }
    }

    public void playGiftAnimationRemote(String giftName, String iconUrl, String animationUrl, String senderName,
                                        int comboCount, String senderUserId, int senderVipLevel,
                                        String senderAvatarUrl, int senderUserLevel, String senderFrameUrl) {
        if (binding == null) return;
        String animUrl = animationUrl;
        String playable = com.Dramizo.Series.util.CosmeticMedia.playableUrl(animUrl);
        if (playable == null
                || (com.Dramizo.Series.util.CosmeticMedia.kind(playable)
                != com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                && com.Dramizo.Series.util.CosmeticMedia.kind(playable)
                != com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA)) {
            String mapped = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                    giftName, iconUrl, animUrl);
            if (mapped != null) {
                animUrl = mapped;
            }
        }
        final String playAnimUrl = animUrl;
        final boolean isVideoGift = com.Dramizo.Series.util.CosmeticMedia.kind(
                com.Dramizo.Series.util.CosmeticMedia.playableUrl(playAnimUrl))
                == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO;
        // Video gifts play inside chatPanel bounds (same as message recycle area), not mid-screen.
        RoomVisualEffects giftFx = isVideoGift && giftVisualEffects != null
                ? giftVisualEffects
                : (visualEffects != null ? visualEffects : giftVisualEffects);
        Runnable onDone = () -> {
            if (effectQueue != null) effectQueue.notifyFinished();
            if (binding == null) return;
            if (isVideoGift) {
                showGiftSendToast(senderName, giftName, senderAvatarUrl, iconUrl, comboCount, 0);
            }
            if (binding.webVisualEffects != null
                    && binding.webVisualEffects.getChildCount() == 0) {
                binding.webVisualEffects.setVisibility(View.GONE);
            }
            if (binding.giftChatEffects != null
                    && binding.giftChatEffects.getChildCount() == 0) {
                binding.giftChatEffects.setVisibility(View.GONE);
            }
        };
        boolean htmlPlayed = giftFx != null
                && giftFx.showGift(giftName, iconUrl, playAnimUrl, senderName, comboCount, onDone);
        if (htmlPlayed && isVideoGift && binding.giftChatEffects != null) {
            binding.giftChatEffects.setVisibility(View.VISIBLE);
            binding.giftChatEffects.bringToFront();
            binding.giftChatEffects.setElevation(48f);
            // Keep combo toast above the gift stage but don't shrink the video layer.
            if (binding.comboGiftView != null) {
                binding.comboGiftView.bringToFront();
                binding.comboGiftView.setElevation(52f);
            }
            if (binding.comingMsgView != null) {
                binding.comingMsgView.bringToFront();
            }
            if (binding.bottomBar != null) {
                binding.bottomBar.bringToFront();
            }
        } else if (htmlPlayed && binding.webVisualEffects != null) {
            binding.webVisualEffects.setVisibility(View.VISIBLE);
            binding.webVisualEffects.bringToFront();
            binding.webVisualEffects.setElevation(31f);
        } else if (htmlPlayed && binding.giftChatEffects != null) {
            binding.giftChatEffects.setVisibility(View.VISIBLE);
            binding.giftChatEffects.bringToFront();
            binding.giftChatEffects.setElevation(48f);
        }
        // Keep giftOverlay while lucky coin rain / مردود is active.
        if (binding.giftOverlay != null
                && !luckyOverlayActive
                && binding.giftOverlay.getChildCount() == 0) {
            binding.giftOverlay.setVisibility(View.GONE);
        }
        // Non-video: Mikoo toast immediately. Video: after stage ends (see onDone).
        if (!isVideoGift) {
            showGiftSendToast(senderName, giftName, senderAvatarUrl, iconUrl, comboCount, 0);
        }

        String me = senderName;
        if (me == null || me.isEmpty()) {
            me = "مستخدم";
        }
        // Never attach the viewer's id to someone else's gift line.
        String chatUserId = senderUserId;
        boolean selfGift = chatUserId != null && myUserId != null && sameUser(chatUserId, myUserId);
        int vip = Math.max(0, senderVipLevel);
        int level = Math.max(1, senderUserLevel);
        String frame = senderFrameUrl;
        if (selfGift) {
            var session = ContainerProvider.from(this).getSessionManager();
            if (vip <= 0) vip = Math.max(0, session.getVipLevel());
            if (level <= 1) level = Math.max(1, session.getUserLevel());
            if (frame == null || frame.isEmpty()) {
                AuthDtos.UserDto selfUser = session.getUser();
                frame = isAgencyRoom
                        ? (selfUser != null ? selfUser.hostBadgeUrl : session.getHostBadgeUrl())
                        : (selfUser != null ? selfUser.vipBadgeUrl : null);
            }
            if (me.isEmpty() || "مستخدم".equals(me)) {
                String dn = session.getDisplayName();
                if (dn != null && !dn.isEmpty()) me = dn;
            }
        }
        String chatGift = "أرسل هدية " + (giftName != null ? giftName : "");
        if (comboCount > 1) chatGift += " ×" + comboCount;
        appendChatLine(me, chatGift, vip, level, frame, chatUserId, senderAvatarUrl, iconUrl);
    }

    private void preloadRoomGiftMedia() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                Result<com.Dramizo.Series.data.remote.dto.GiftDtos.GiftList> r =
                        c.getGiftsUseCase.execute();
                if (!r.success || r.data == null || r.data.isEmpty()) return;
                java.util.ArrayList<String> urls = new java.util.ArrayList<>();
                for (com.Dramizo.Series.data.remote.dto.GiftDtos.GiftDto g : r.data) {
                    if (g == null) continue;
                    String resolved = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                            g.name, g.iconUrl, g.animationUrl);
                    if (resolved != null && !resolved.isEmpty()) {
                        urls.add(resolved);
                    } else if (g.animationUrl != null && !g.animationUrl.isEmpty()) {
                        urls.add(g.animationUrl);
                    }
                    if (g.iconUrl != null && !g.iconUrl.isEmpty()) urls.add(g.iconUrl);
                    if (urls.size() > 100) break;
                }
                com.Dramizo.Series.util.NativeRoomEffectsView.preloadGiftUrls(this, urls);
            } catch (Exception ignored) {
            }
        });
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null) {
                String t = value.trim();
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }

    /** Always prefer playable ride video/GIF over static PNG preview. */
    @Nullable
    private static String preferEntryRideMedia(@Nullable String animationUrl,
                                              @Nullable String previewUrl) {
        String anim = com.Dramizo.Series.util.CosmeticMedia.playableUrl(animationUrl);
        String preview = com.Dramizo.Series.util.CosmeticMedia.playableUrl(previewUrl);
        com.Dramizo.Series.util.CosmeticMedia.Kind ak =
                com.Dramizo.Series.util.CosmeticMedia.kind(anim);
        if (ak == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                || ak == com.Dramizo.Series.util.CosmeticMedia.Kind.GIF) {
            return anim;
        }
        com.Dramizo.Series.util.CosmeticMedia.Kind pk =
                com.Dramizo.Series.util.CosmeticMedia.kind(preview);
        if (pk == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                || pk == com.Dramizo.Series.util.CosmeticMedia.Kind.GIF) {
            return preview;
        }
        // Static IMAGE URLs often have a sibling .mp4 on CDN — never ride a still PNG.
        String forcedAnim = forceMp4Sibling(anim);
        if (forcedAnim != null) return forcedAnim;
        String forcedPreview = forceMp4Sibling(preview);
        if (forcedPreview != null) return forcedPreview;
        // No video available: skip still image so UI does not flash a half-white PNG.
        return null;
    }

    @Nullable
    private static String forceMp4Sibling(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        String forced = url.replaceAll("(?i)\\.(png|jpe?g|webp)(\\?.*)?$", ".mp4$2");
        if (forced.equals(url)) return null;
        if (com.Dramizo.Series.util.CosmeticMedia.kind(forced)
                == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO) {
            return forced;
        }
        return null;
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (binding != null && binding.gameOverlay != null
                && binding.gameOverlay.getVisibility() == View.VISIBLE) {
            closeGameOverlay();
            return;
        }
        // First back: close side panel if open.
        if (roomSidePanelDialog != null && roomSidePanelDialog.isShowing()) {
            roomSidePanelDialog.dismiss();
            return;
        }
        // Next back: open Mikoo side panel (actions + يكتشف / تاريخ).
        showRoomSidePanel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        sRoomUiVisible = true;
        sAliveRoom = new java.lang.ref.WeakReference<>(this);
        if (binding != null && binding.webHostSignal != null) {
            binding.webHostSignal.resumeMotion();
        }
        if ("playing".equalsIgnoreCase(currentMusicStatus)) {
            startMusicDiscAnimation();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        // Start FGS while still foreground-eligible (API 34+ microphone rules).
        // Waiting until onStop often causes SecurityException with microphone type.
        if (!exiting && !isChangingConfigurations()
                && roomId != null && !roomId.isEmpty()
                && pendingSession != null) {
            ensuringRoomKeepAlive(true);
        }
        super.onUserLeaveHint();
    }

    @Override
    protected void onStop() {
        sRoomUiVisible = false;
        // Do NOT clear chat / gift queues — user expects session to continue while in WhatsApp.
        if (binding != null && binding.webHostSignal != null) {
            binding.webHostSignal.pauseMotion();
        }
        stopMusicDiscAnimation();
        boolean keepAlive = !exiting && !isChangingConfigurations()
                && roomId != null && !roomId.isEmpty()
                && pendingSession != null;
        if (keepAlive) {
            // Mikoo minimize / leave-UI: keep hearing others; mute only own mic.
            ensuringRoomKeepAlive(true);
            ensureMinimizedListeningState();
        } else if (!exiting) {
            muteAudioForBackground();
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sRoomUiVisible = true;
        minimizing = false;
        // UI is back — stop FGS owner and restore intended mic (never force open).
        try {
            VoiceRoomForegroundService.attachUi(this);
        } catch (RuntimeException ignored) {
        }
        unmuteAudioAfterBackground();
        try {
            // Prefer live Zego mic state (may have been toggled from notification / mini).
            micOn = RoomRtcEngine.getInstance().isMicEnabled();
            RoomRtcEngine.getInstance().setMicEnabled(micOn);
            syncMicUi();
        } catch (Exception ignored) {
        }
        try {
            // Re-pull seat audio in case anything was paused while away.
            activateSeatAudio(currentSeats, true);
        } catch (Exception ignored) {
        }
        // Pull any chat lines buffered by FGS while we were away.
        try {
            if (roomChatRestored) syncRoomChatFromMemory();
            else restoreRoomChatIfNeeded();
        } catch (Exception ignored) {
        }
        // After wallet recharge from in-room game, refresh coins into WebView.
        if (slotSession != null && binding != null
                && binding.gameOverlay != null
                && binding.gameOverlay.getVisibility() == View.VISIBLE) {
            refreshRoomGameWalletAfterRecharge();
        }
        if (pendingSeatInviteDialog) {
            pendingSeatInviteDialog = false;
            handler.postDelayed(this::showPendingSeatInviteDialog, 300);
        }
    }

    /** Auto-mute room speakers while user is in another app — keep host music publish alive. */
    private void muteAudioForBackground() {
        if (mutedForBackground || exiting) return;
        try {
            speakerMutedBeforeBackground = roomSpeakerMuted
                    || RoomRtcEngine.getInstance().isSpeakerMuted();
            mutedForBackground = true;
            roomSpeakerMuted = true;
            RoomSoundFx.setMuted(true);
            RoomRtcEngine.getInstance().setSpeakerMuted(true);
            // Re-assert music mix after speaker mute (mute stops remote pulls only).
            if (canManageMusic
                    && currentMusicUrl != null
                    && !currentMusicUrl.isEmpty()
                    && "playing".equalsIgnoreCase(currentMusicStatus)) {
                ensurePublishingForMusic();
                RoomRtcEngine.getInstance().boostMusicMixVolume();
                if (RoomRtcEngine.getInstance().hasLocalMusicPlayer()
                        && !RoomRtcEngine.getInstance().isLocalMusicPlaying()) {
                    RoomRtcEngine.getInstance().resumeLocalMusic();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void unmuteAudioAfterBackground() {
        if (!mutedForBackground) return;
        mutedForBackground = false;
        try {
            boolean keepMuted = speakerMutedBeforeBackground;
            roomSpeakerMuted = keepMuted;
            RoomSoundFx.setMuted(keepMuted);
            RoomRtcEngine.getInstance().setSpeakerMuted(keepMuted);
        } catch (Exception ignored) {
        }
    }

    private void refreshRoomGameWalletAfterRecharge() {
        if (slotSession == null) return;
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = ApiCall.execute(
                    ContainerProvider.from(this).getWalletApi().getWallet());
            if (isFinishing() || !r.success || r.data == null) return;
            long coins = Math.max(0L, r.data.coins);
            runOnUiThread(() -> {
                if (slotSession == null) return;
                slotSession.balance = coins;
                if (mikooBridge != null) mikooBridge.notifyWalletUpdate();
                if (binding != null && roomGameWebView != null) {
                    injectRoomGameBalanceSync(ensureRoomGameWebView());
                }
            });
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        // singleTask: re-entering same activity — either restore UI or hop to another room.
        String incomingRoomId = intent.getStringExtra(EXTRA_ROOM_ID);
        if (incomingRoomId != null && !incomingRoomId.isEmpty()
                && roomId != null && !incomingRoomId.equals(roomId)
                && !exiting) {
            switchRoomInPlace(incomingRoomId, intent.getStringExtra(EXTRA_PASSWORD));
            return;
        }
        minimizing = false;
        ActiveRoomSession.get().setMinimized(false);
        // Same room brought back (notification / singleTask) — never rejoin.
        if (incomingRoomId != null && incomingRoomId.equals(roomId) && pendingSession != null) {
            resumedFromActiveSession = true;
            if (!realtimeJoined) connectRealtimeRoom();
            try {
                activateSeatAudio(currentSeats, true);
            } catch (Exception ignored) {
            }
        }
        if (intent.getBooleanExtra(EXTRA_PENDING_SEAT_INVITE, false)) {
            pendingSeatInviteDialog = true;
            handler.postDelayed(this::showPendingSeatInviteDialog, 300);
        }
    }

    /**
     * Mikoo singleTask room hop: fully leave old room (audio + socket + HTTP), then join the new one.
     * Swipe feed must never keep hearing the previous room.
     */
    private void switchRoomInPlace(String targetRoomId, String password) {
        if (targetRoomId == null || targetRoomId.isEmpty()) return;
        if (targetRoomId.equals(roomId)) return;
        hoppingRoom = true;
        switchingRoom = true;
        roomSwitchTeardownComplete = true;

        // Cancel delayed audio / join retries from the previous room.
        handler.removeCallbacks(retryRealtimeJoinRunnable);
        handler.removeCallbacks(retryHttpJoinRunnable);
        handler.removeCallbacks(coalescedRoomRefresh);
        handler.removeCallbacks(supporterRefresh);
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(rtcTokenRefreshRunnable);

        // Drop local seat/audio state so delayed activateSeatAudio cannot revive old streams.
        roomAudioEpoch++;
        currentSeats = new ArrayList<>();
        pendingSession = null;
        lastActivateSeatAudioKey = null;
        lastActivateSeatAudioAt = 0L;
        rtcCanPublish = false;
        rtcPublishTokenExpiresAtMs = 0L;
        micOn = false;
        userChoseMute = false;
        zegoLoggedIn = false;
        realtimeJoined = false;
        realtimeJoinInFlight = false;
        resumedFromActiveSession = false;
        currentMusicUrl = null;
        currentMusicStatus = "stopped";
        preparedMusicUrl = null;

        try {
            clearLocalRoomChat();
        } catch (Exception ignored) {
        }
        clearSeatRequestsUi();
        clearTopSupportersUi();

        // Force leave even if a prior teardown flag was stuck.
        teardownRoomSession(true, true);
        ActiveRoomSession.get().clear();

        exiting = false;
        minimizing = false;
        roomTeardownDone = false;
        roomSwitchTeardownComplete = false;

        roomId = targetRoomId;
        Intent updated = getIntent() != null ? getIntent() : new Intent(this, VoiceRoomActivity.class);
        updated.putExtra(EXTRA_ROOM_ID, targetRoomId);
        if (password != null) updated.putExtra(EXTRA_PASSWORD, password);
        else updated.removeExtra(EXTRA_PASSWORD);
        setIntent(updated);
        if (binding != null && binding.tvRoomTitle != null) {
            binding.tvRoomTitle.setText(R.string.voice_room);
        }
        if (binding != null && binding.musicCard != null) {
            binding.musicCard.setVisibility(View.GONE);
        }
        showMusicReopenChip(false);
        musicPanelExpanded = false;

        roomJoinLoadingDismissed = false;
        showRoomJoinLoading();
        viewModel.join(targetRoomId, password);
        // hoppingRoom stays true until the new session arrives (see session observer).
    }

    private void showPendingSeatInviteDialog() {
        if (isFinishing() || roomId == null) return;
        AuraDialogHelper.confirm(this,
                "دعوة إلى المايك",
                "دعاك مشرف الغرفة للصعود إلى المايك. هل تقبل؟",
                "قبول",
                () -> viewModel.respondSeatInvite(roomId, true),
                "رفض",
                () -> viewModel.respondSeatInvite(roomId, false));
    }

    private void registerAudioRouteReceiver() {
        if (audioRouteReceiver != null) return;
        audioRouteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (AudioManager.ACTION_HEADSET_PLUG.equals(action)
                        || AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)
                        || Intent.ACTION_HEADSET_PLUG.equals(action)
                        || AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
                    try {
                        RoomRtcEngine.getInstance().reapplyAudioRoute();
                    } catch (Exception ignored) {
                    }
                }
            }
        };
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_HEADSET_PLUG);
            f.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            f.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            registerReceiver(audioRouteReceiver, f);
        } catch (Exception e) {
            audioRouteReceiver = null;
        }
    }

    private void unregisterAudioRouteReceiver() {
        if (audioRouteReceiver == null) return;
        try {
            unregisterReceiver(audioRouteReceiver);
        } catch (Exception ignored) {
        }
        audioRouteReceiver = null;
    }

    @Override
    protected void onDestroy() {
        unregisterAudioRouteReceiver();
        if (sAliveRoom != null && sAliveRoom.get() == this) {
            sAliveRoom.clear();
        }
        handler.removeCallbacks(roomGmtClockTick);
        // Never interstitials/ads on activity death — they freeze black screen on room leave.
        try {
            closeGameOverlayInternal();
        } catch (Exception ignored) {
        }
        try {
            destroyLazyWebViews();
        } catch (Exception ignored) {
        }
        stopMusicDiscAnimation();
        stopTaskFloatPulse();
        // Mikoo-style: while minimizing, UI dies — session/engine stay in process + FGS.
        if (!minimizing) {
            // Leaving for real — keep speakers muted (already cut in teardown).
            mutedForBackground = false;
            roomSpeakerMuted = true;
            RoomSoundFx.setMuted(true);
            GiftAudioFx.resetRoomGiftSounds();
            try {
                RoomRtcEngine.getInstance().setSpeakerMuted(true);
            } catch (Exception ignored) {
            }
        } else {
            mutedForBackground = false;
            roomSpeakerMuted = false;
            RoomSoundFx.setMuted(false);
            GiftAudioFx.resetRoomGiftSounds();
            try {
                RoomRtcEngine.getInstance().setSpeakerMuted(false);
            } catch (Exception ignored) {
            }
        }
        if (seatAdapter != null) seatAdapter.cleanup();
        clearHostStageReaction();
        if (!minimizing) {
            if (effectQueue != null) effectQueue.clear();
            if (visualEffects != null) {
                visualEffects.destroy();
                visualEffects = null;
            }
            if (giftVisualEffects != null) {
                giftVisualEffects.destroy();
                giftVisualEffects = null;
            }
        } else {
            if (visualEffects != null) visualEffects.stopAll();
            if (giftVisualEffects != null) giftVisualEffects.stopAll();
        }
        if (binding != null && binding.webHostSignal != null) {
            binding.webHostSignal.destroy();
        }
        // Keep YouTube/Zego mix alive while minimized. Only tear down the Activity's Exo UI player.
        if (roomMusicPlayer != null) {
            try {
                roomMusicPlayer.clearMediaItems();
                if (binding != null && binding.musicVideoSurface != null) {
                    musicVideoSetPlayer(null);
                }
                if (binding != null && binding.musicFloatVideo != null) {
                    musicFloatSetPlayer(null);
                }
                roomMusicPlayer.release();
            } catch (Exception ignored) {
            }
            roomMusicPlayer = null;
        }
        if (!minimizing) {
            clearMusicVideoUi();
            RoomRtcEngine.getInstance().setLocalMusicEndListener(null);
            // Don't stop host music here on accidental destroy if session still active via FGS —
            // intentional leave already called logoutRoom → stopLocalMusic.
            if (exiting || roomTeardownDone) {
                RoomRtcEngine.getInstance().stopLocalMusic();
            }
        } else {
            try {
                if (canManageMusic
                        && currentMusicUrl != null
                        && !currentMusicUrl.isEmpty()
                        && "playing".equalsIgnoreCase(currentMusicStatus)) {
                    ensurePublishingForMusic();
                    RoomRtcEngine.getInstance().boostMusicMixVolume();
                    if (!RoomRtcEngine.getInstance().isLocalMusicPlaying()
                            && RoomRtcEngine.getInstance().hasLocalMusicPlayer()) {
                        RoomRtcEngine.getInstance().resumeLocalMusic();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(retryRealtimeJoinRunnable);
        handler.removeCallbacks(retryHttpJoinRunnable);
        if (realtimeRoomListener != null) {
            RealtimeClient.getInstance().removeRoomListener(realtimeRoomListener);
            realtimeRoomListener = null;
        }
        if (moderationUserListener != null) {
            RealtimeClient.getInstance().removeUserListener(moderationUserListener);
            moderationUserListener = null;
        }
        if (zegoRoomListener != null) {
            RoomRtcEngine.getInstance().removeRoomListener(zegoRoomListener);
            zegoRoomListener = null;
        }
        if (minimizing) {
            // Mikoo: Activity UI gone — session stays in Zego + FGS. Never leave here.
            super.onDestroy();
            return;
        }
        // Mikoo: onDestroy never calls exitRoom. Leave only via exitRoom / forceExitRoom.
        super.onDestroy();
    }

    private void setupRealtime() {
        String token = ContainerProvider.from(this).getSessionManager().getAccessToken();
        RealtimeClient rt = RealtimeClient.getInstance();
        realtimeRoomListener = new RealtimeClient.RoomListener() {
            @Override
            public void onRoomEvent(String rid, String event, JsonObject payload, String fromUserId, String fromUsername) {
                if (rid == null || roomId == null || !roomId.equals(rid) || event == null) return;
                handler.post(() -> handleRoomEvent(event, payload, fromUserId, fromUsername));
            }

            @Override
            public void onUserJoined(String rid, String userId, String username) {
                onUserJoined(
                        rid, userId, username, 0, null, null, null,
                        username, "normal", 1, null, null, null, false,
                        0L, 0L, 0L, 0, null, 0f, 0f, 0f, 0f, 0f, 0L, false);
            }

            @Override
            public void onUserJoined(String rid, String userId, String username, int vipLevel,
                                     String entryEffectUrl,
                                     String entryAnimationUrl, String avatarUrl, String displayName,
                                     String supporterTier, int userLevel,
                                     String vipBadgeUrl, String levelBadgeUrl, String hostBadgeUrl,
                                     boolean userIsHost, long wealthScore, long totalSentCoins,
                                     long roomSpendCoins, int effectPriority, String renderMode,
                                     float aspectRatio, float safeLeft, float safeTop,
                                     float safeRight, float safeBottom, long durationMs,
                                     boolean showHiBadge) {
                if (rid == null || roomId == null || !roomId.equals(rid)) return;
                handler.post(() -> {
                    boolean selfJoin = sameUser(userId, myUserId);
                    if (!markUniqueJoin(rid, userId)) return;
                    String name = displayName != null && !displayName.isEmpty()
                            ? displayName
                            : (username != null && !username.isEmpty() ? username : "مستخدم");
                    int level = Math.max(1, userLevel);
                    if (!selfJoin) {
                        RoomSoundFx.playJoin(VoiceRoomActivity.this);
                        bumpRoomViewers(1);
                    }

                    // Mikoo: owner does NOT see their own entry effect — only other users.
                    if (selfJoin || myUserId == null || myUserId.isEmpty()) {
                        return;
                    }

                    // Always show join entry for others so equipped rides play in-room.
                    if (effectQueue != null) {
                        RoomEffectQueue.EffectType entryType;
                        if ("supporter".equals(supporterTier) || "legendary".equals(supporterTier)) {
                            entryType = RoomEffectQueue.EffectType.SUPPORTER_ENTRY;
                        } else if (vipLevel >= 1) {
                            entryType = RoomEffectQueue.EffectType.VIP_ENTRY;
                        } else {
                            entryType = RoomEffectQueue.EffectType.NORMAL_ENTRY;
                        }
                        String rideAnim = entryAnimationUrl;
                        String ridePreview = entryEffectUrl;
                        String ride = firstNonEmpty(rideAnim, ridePreview);
                        long hold = durationMs > 0
                                ? Math.min(durationMs, 5500L)
                                : (ride != null && !ride.isEmpty() ? 4500L : 2600L);
                        effectQueue.enqueue(new RoomEffectQueue.Builder()
                                .type(entryType)
                                .displayName(name)
                                .avatarUrl(avatarUrl)
                                .entryEffectUrl(ridePreview)
                                .entryAnimationUrl(rideAnim)
                                .vipLevel(vipLevel)
                                .supporterTier(supporterTier)
                                .userLevel(level)
                                .vipBadgeUrl(vipBadgeUrl)
                                .levelBadgeUrl(levelBadgeUrl)
                                .hostBadgeUrl(hostBadgeUrl)
                                .isHost(userIsHost)
                                .showHiBadge(showHiBadge)
                                .wealthScore(Math.max(wealthScore, totalSentCoins))
                                .roomSpendCoins(roomSpendCoins)
                                .effectPriority(effectPriority)
                                .renderMode(renderMode)
                                .aspectRatio(aspectRatio)
                                .textSafeArea(safeLeft, safeTop, safeRight, safeBottom)
                                .durationMs(hold)
                                .build(effectQueue));
                    }
                });
            }

            @Override
            public void onUserLeft(String rid, String userId) {
                if (rid == null || roomId == null || !roomId.equals(rid)) return;
                if (userId != null && userId.equals(myUserId)) return;
                handler.post(() -> {
                    bumpRoomViewers(-1);
                });
            }

            @Override
            public void onRoomMembers(String rid, com.google.gson.JsonArray members) {
                if (rid == null || roomId == null || !roomId.equals(rid)) return;
                handler.post(() -> {
                    roomAudience.clear();
                    roomMembersAll.clear();
                    List<RecentJoinersAdapter.Joiner> audienceRows = new ArrayList<>();
                    List<RecentJoinersAdapter.Joiner> framedFirst = new ArrayList<>();
                    List<RecentJoinersAdapter.Joiner> seatedNext = new ArrayList<>();
                    List<RecentJoinersAdapter.Joiner> others = new ArrayList<>();
                    Set<String> seenAudience = new HashSet<>();
                    Set<String> seenAll = new HashSet<>();
                    if (members != null) {
                        for (int i = 0; i < members.size(); i++) {
                            if (!members.get(i).isJsonObject()) continue;
                            JsonObject member = members.get(i).getAsJsonObject();
                            String uid = memberStr(member, "userId");
                            if (uid == null || uid.isEmpty()) continue;
                            String normalizedUid = normalizeUserId(uid);
                            if (normalizedUid.isEmpty()) continue;
                            if (seenAll.add(normalizedUid)) {
                                roomMembersAll.add(member);
                            }
                            if (!seenAudience.add(normalizedUid)) continue;
                            if (sameUser(uid, myUserId)) continue;
                            boolean seated = sameUser(uid, roomHostId)
                                    || sameUser(uid, roomCohostId)
                                    || isUserSeated(uid);
                            if (!seated) {
                                roomAudience.add(member);
                            }
                            String displayName = memberStr(member, "displayName");
                            if (displayName == null || displayName.isEmpty()) {
                                displayName = memberStr(member, "username");
                            }
                            int vip = Math.max(0, memberInt(member, "vipLevel", 0));
                            int level = Math.max(1, memberInt(member, "userLevel", 1));
                            String hostFrame = memberStr(member, "hostBadgeUrl");
                            String vipFrame = memberStr(member, "vipBadgeUrl");
                            String frame = isAgencyRoom ? hostFrame : vipFrame;
                            RecentJoinersAdapter.Joiner row = new RecentJoinersAdapter.Joiner(
                                    uid,
                                    displayName != null ? displayName : "مستخدم",
                                    memberStr(member, "avatarUrl"),
                                    hostFrame,
                                    vipFrame,
                                    vip,
                                    level);
                            if (frame != null && !frame.trim().isEmpty()) {
                                framedFirst.add(row);
                            } else if (seated) {
                                seatedNext.add(row);
                            } else {
                                others.add(row);
                            }
                        }
                        setRoomViewerCount(Math.max(1, members.size()));
                    }
                    audienceRows.addAll(framedFirst);
                    audienceRows.addAll(seatedNext);
                    audienceRows.addAll(others);
                    if (audienceRows.size() > 10) {
                        audienceRows = new ArrayList<>(audienceRows.subList(0, 10));
                    }
                    updateViewerStack(roomAudience);
                    if (audienceAdapter != null) audienceAdapter.submit(audienceRows);
                    binding.recyclerRecentJoiners.setVisibility(
                            audienceRows.isEmpty() ? View.GONE : View.VISIBLE);
                    if (binding.rowAudienceStrip != null) {
                        binding.rowAudienceStrip.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onConnected() {
                connectRealtimeRoom();
            }

            @Override
            public void onDisconnected() {
                realtimeJoined = false;
                realtimeJoinInFlight = false;
                // Soft reconnect — do not forceExit (looks like "app closed" on weak nets).
                if (!exiting && roomId != null && !roomId.isEmpty()) {
                    scheduleRealtimeJoinRetry(2_000L);
                }
            }
        };
        rt.addRoomListener(realtimeRoomListener);
        if (moderationUserListener == null) {
            moderationUserListener = new RealtimeClient.UserListener() {
                @Override
                public void onUserEvent(String event, JsonObject payload) {
                    if (payload == null || event == null) return;
                    String rid = payload.has("roomId") && !payload.get("roomId").isJsonNull()
                            ? payload.get("roomId").getAsString() : null;
                    if (roomId == null || rid == null || !roomId.equals(rid)) return;
                    handler.post(() -> {
                        if ("moderation:blocked".equals(event) || "moderation:action".equals(event)) {
                            String action = payload.has("action") && !payload.get("action").isJsonNull()
                                    ? payload.get("action").getAsString() : "";
                            String reason = payload.has("reason") && !payload.get("reason").isJsonNull()
                                    ? payload.get("reason").getAsString() : null;
                            String until = payload.has("until") && !payload.get("until").isJsonNull()
                                    ? payload.get("until").getAsString() : null;
                            if ("unmute".equalsIgnoreCase(action)) {
                                chatMutedUntilMs = 0L;
                                Toast.makeText(VoiceRoomActivity.this,
                                        reason != null ? reason : "تم فك الكتم",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if ("mute".equalsIgnoreCase(action)
                                    || "CHAT_MUTED".equals(
                                    payload.has("code") && !payload.get("code").isJsonNull()
                                            ? payload.get("code").getAsString() : "")) {
                                int mins = 0;
                                try {
                                    if (payload.has("muteMinutes") && !payload.get("muteMinutes").isJsonNull()) {
                                        mins = payload.get("muteMinutes").getAsInt();
                                    }
                                } catch (Exception ignored) {}
                                applyChatMuteFromServer(until, reason, mins);
                                requestRoomRefresh(false);
                            }
                        }
                    });
                }
            };
            rt.addUserListener(moderationUserListener);
        }
        rt.connect(token);
    }

    private boolean isChatTextMuted() {
        return chatMutedUntilMs > System.currentTimeMillis();
    }

    private String chatMuteToastMessage() {
        long leftMs = Math.max(0L, chatMutedUntilMs - System.currentTimeMillis());
        int mins = (int) Math.max(1, Math.ceil(leftMs / 60_000.0));
        return "أنت مكتوم من الدردشة · تبقّى تقريباً " + mins + " د";
    }

    private void applyChatMuteFromServer(@Nullable String untilIso, @Nullable String reason) {
        long until = 0L;
        if (untilIso != null && !untilIso.isEmpty()) {
            try {
                until = java.time.Instant.parse(untilIso).toEpochMilli();
            } catch (Exception ignored) {
                until = 0L;
            }
        }
        if (until <= 0L) {
            until = System.currentTimeMillis() + 5 * 60_000L;
        }
        chatMutedUntilMs = Math.max(chatMutedUntilMs, until);
        Toast.makeText(this,
                reason != null && !reason.isEmpty() ? reason : chatMuteToastMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void applyChatMuteFromServer(
            @Nullable String untilIso,
            @Nullable String reason,
            int muteMinutes
    ) {
        long until = 0L;
        if (untilIso != null && !untilIso.isEmpty()) {
            try {
                until = java.time.Instant.parse(untilIso).toEpochMilli();
            } catch (Exception ignored) {
                until = 0L;
            }
        }
        if (until <= 0L && muteMinutes > 0) {
            until = System.currentTimeMillis() + muteMinutes * 60_000L;
        }
        if (until <= 0L) {
            until = System.currentTimeMillis() + 5 * 60_000L;
        }
        chatMutedUntilMs = Math.max(chatMutedUntilMs, until);
        Toast.makeText(this,
                reason != null && !reason.isEmpty() ? reason : chatMuteToastMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void connectRealtimeRoom() {
        if (roomId == null || realtimeJoined || realtimeJoinInFlight) return;
        RealtimeClient rt = RealtimeClient.getInstance();
        // Already in the socket room from minimize — skip rejoin / "اتصال ضعيف".
        if (rt.isJoinedRoom(roomId)) {
            realtimeJoined = true;
            realtimeJoinAttempts = 0;
            return;
        }
        if (!rt.isConnected()) {
            // Socket still connecting — retry softly; never eject the user.
            scheduleRealtimeJoinRetry(1_800L);
            return;
        }
        var session = ContainerProvider.from(this).getSessionManager();
        String joiningRoomId = roomId;
        realtimeJoinInFlight = true;
        rt.joinRoom(
                roomId,
                session.getDisplayName(),
                session.getAvatarUrl(),
                session.getVipLevel(),
                session.getUserLevel(),
                (success, error, profile) -> runOnUiThread(() -> {
                    realtimeJoinInFlight = false;
                    if (!joiningRoomId.equals(roomId) || exiting) return;
                    realtimeJoined = success;
                    if (success) {
                        realtimeJoinAttempts = 0;
                        return;
                    }
                    // Socket/join blip must NEVER eject — looks like "تم إغلاق التطبيق".
                    // Keep the HTTP/Zego room open and retry quietly.
                    realtimeJoinAttempts++;
                    boolean quiet = resumedFromActiveSession
                            || ActiveRoomSession.get().canResumeUi(roomId)
                            || pendingSession != null;
                    if (!quiet && (realtimeJoinAttempts <= 1 || realtimeJoinAttempts % 3 == 0)) {
                        Toast.makeText(this, R.string.connection_slow_retrying, Toast.LENGTH_SHORT).show();
                    }
                    long delay = Math.min(12_000L, 1_500L * Math.max(1, realtimeJoinAttempts));
                    scheduleRealtimeJoinRetry(delay);
                }));
    }

    private void scheduleRealtimeJoinRetry(long delayMs) {
        handler.removeCallbacks(retryRealtimeJoinRunnable);
        handler.postDelayed(retryRealtimeJoinRunnable, Math.max(800L, delayMs));
    }

    /** Ban / missing room only — never treat network/socket noise as "room closed". */
    private static boolean isJoinBlockedError(@Nullable String msg) {
        if (msg == null || msg.isEmpty()) return false;
        String m = msg.toLowerCase(java.util.Locale.US);
        if (isTransientNetworkError(m)) return false;
        return m.contains("ban") || m.contains("حظر") || m.contains("طرد")
                || m.contains("forbidden") || m.contains("not found")
                || m.contains("does not exist") || m.contains("no longer")
                || m.contains("suspend") || m.contains("الوكالة غير نشطة");
    }

    private static boolean isTransientNetworkError(@Nullable String msg) {
        if (msg == null || msg.isEmpty()) return true;
        String m = msg.toLowerCase(java.util.Locale.US);
        return m.contains("timeout") || m.contains("timed out")
                || m.contains("socket") || m.contains("network")
                || m.contains("connect") || m.contains("unreachable")
                || m.contains("unavailable") || m.contains("connection reset")
                || m.contains("connection refused") || m.contains("failed to connect")
                || m.contains("unable to resolve") || m.contains("unknownhost")
                || m.contains("ssl") || m.contains("handshake")
                || m.contains("502") || m.contains("503") || m.contains("504")
                || m.contains("408") || m.contains("slow")
                || m.contains("try again") || m.contains("retry")
                || m.contains("temporarily") || m.contains("join failed")
                || m.contains("join timeout") || m.contains("closed")
                || m.contains("الاتصال") || m.contains("الشبكة") || m.contains("انتهت المهلة");
    }

    /** Deprecated: self entry is intentionally skipped (others-only). */
    private void playSelfJoinEntry(@Nullable org.json.JSONObject profile) {
        // no-op — entry effects are for other users in the room only
    }

    @Nullable
    private static String jsonStr(@Nullable org.json.JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, null);
        return v != null && !v.isEmpty() ? v : null;
    }

    private static int jsonInt(@Nullable org.json.JSONObject o, String key, int fallback) {
        if (o == null || !o.has(key) || o.isNull(key)) return fallback;
        return o.optInt(key, fallback);
    }

    private static long jsonLong(@Nullable org.json.JSONObject o, String key, long fallback) {
        if (o == null || !o.has(key) || o.isNull(key)) return fallback;
        return o.optLong(key, fallback);
    }

    private static double jsonDouble(@Nullable org.json.JSONObject o, String key, double fallback) {
        if (o == null || !o.has(key) || o.isNull(key)) return fallback;
        return o.optDouble(key, fallback);
    }

    @SuppressLint("NewApi")
    private void handleRoomEvent(String event, JsonObject payload, String fromUserId, String fromUsername) {
        if (payload == null) payload = new JsonObject();
        if ("chat:message".equals(event)) {
            String name = memberStr(payload, "name");
            if (name == null) name = memberStr(payload, "displayName");
            if (name == null) name = fromUsername != null ? fromUsername : "مستخدم";
            String text = memberStr(payload, "text");
            if (text == null) text = "";
            int vip = memberInt(payload, "vipLevel", 0);
            int level = memberInt(payload, "userLevel", 0);
            String frame = memberStr(payload, "frameUrl");
            if (frame == null) {
                frame = isAgencyRoom
                        ? memberStr(payload, "hostBadgeUrl")
                        : memberStr(payload, "vipBadgeUrl");
            }
            if (frame == null) frame = memberStr(payload, "hostBadgeUrl");
            if (frame == null) frame = memberStr(payload, "vipBadgeUrl");
            String avatar = memberStr(payload, "avatarUrl");
            String uid = memberStr(payload, "userId");
            if (uid == null) uid = fromUserId;
            long wealth = memberLong(payload, "wealthScore", 0L);
            if (wealth <= 0) wealth = memberLong(payload, "totalSentCoins", 0L);
            long charm = memberLong(payload, "charmScore", 0L);
            if (!text.isEmpty()) {
                if (fromUserId != null && fromUserId.equals(myUserId)) return;
                appendChatLine(name, text, vip, level, frame, uid, avatar, null, wealth, charm);
            }
        } else if ("room:seat_invited".equals(event)) {
            String targetUserId = memberStr(payload, "userId");
            if (myUserId == null || !myUserId.equals(targetUserId)) return;
            if (isFinishing() || !hasWindowFocus()) {
                // Background / another app — bring room forward then show dialog.
                Intent open = new Intent(this, VoiceRoomActivity.class);
                open.putExtra(EXTRA_ROOM_ID, roomId);
                open.putExtra(EXTRA_PENDING_SEAT_INVITE, true);
                open.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(open);
                handler.postDelayed(this::showPendingSeatInviteDialog, 400);
                return;
            }
            showPendingSeatInviteDialog();
        } else if ("room:task_invited".equals(event)) {
            String guestId = memberStr(payload, "guestId");
            if (myUserId == null || !myUserId.equals(guestId)) return;
            String inviteRoomId = memberStr(payload, "roomId");
            if (inviteRoomId == null || inviteRoomId.isEmpty()) inviteRoomId = roomId;
            if (inviteRoomId != null && inviteRoomId.equals(roomId)) {
                Toast.makeText(this,
                        "أنت في غرفة المضيفة — ابقَ دقيقتين لتحصل على مكافأة المهمة",
                        Toast.LENGTH_LONG).show();
                return;
            }
            final String targetRoomId = inviteRoomId;
            AuraDialogHelper.confirm(this,
                    "دعوة مهمة من المضيفة",
                    "ادخل غرفتها وابقَ دقيقتين لتكتمل المهمة (+40 ماسة لها).",
                    "دخول",
                    () -> switchToInvitedRoom(targetRoomId),
                    "لاحقاً",
                    null);
        } else if ("room:summon".equals(event)) {
            if (fromUserId != null && myUserId != null && fromUserId.equals(myUserId)) return;
            String msg = memberStr(payload, "message");
            if (msg == null || msg.isEmpty()) msg = "يستدعيك للغرفة — ارجع الآن";
            String who = firstNonEmpty(
                    memberStr(payload, "displayName"),
                    fromUsername,
                    "صاحب الغرفة");
            String avatar = memberStr(payload, "avatarUrl");
            // Mikoo-style strip (avatar + message) instead of plain system Toast.
            showLuckyResultToast(who, avatar, 1, Math.max(0,
                    memberInt(payload, "vipLevel", 0)), msg);
            appendChatLine(who, msg, 0, 1, null, fromUserId, avatar, null);
            if (!isFinishing() && !hasWindowFocus()) {
                Intent open = new Intent(this, VoiceRoomActivity.class);
                open.putExtra(EXTRA_ROOM_ID, roomId);
                open.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(open);
            }
        } else if ("room:chat_cleared".equals(event)) {
            // Full wipe for every client: phone cache + UI (+ server already stamped).
            long stamp = parseIsoMillis(memberStr(payload, "chatClearedAt"));
            if (stamp > 0) clearRoomChatSession(stamp);
            else clearLocalRoomChat();
            String who = firstNonEmpty(
                    memberStr(payload, "displayName"),
                    fromUsername,
                    "مشرف");
            boolean auto = memberBool(payload, "auto", false);
            String note = auto
                    ? getString(R.string.clear_room_chat_done) + " · " + who
                    : getString(R.string.clear_room_chat_done) + " · " + who;
            appendChatLine("النظام", note, 0, 1);
        } else if ("room:chat_auto_clear".equals(event)) {
            int mins = memberInt(payload, "chatAutoClearMinutes", 0);
            RoomDtos.RoomDto r = viewModel.getRoom().getValue();
            if (r != null) r.chatAutoClearMinutes = mins;
            long stamp = parseIsoMillis(memberStr(payload, "chatClearedAt"));
            if (stamp > 0) RoomChatMemory.honorServerWipe(roomId, stamp);
        } else if ("room:music".equals(event)) {
            String serverTime = memberStr(payload, "serverTime");
            if (serverTime != null) {
                try {
                    roomMusicServerOffsetMs = java.time.Instant.parse(serverTime).toEpochMilli()
                            - System.currentTimeMillis();
                } catch (Exception ignored) {}
            }
            applyMusicState(
                    memberStr(payload, "url"),
                    memberStr(payload, "title"),
                    memberStr(payload, "artist"),
                    memberStr(payload, "status"),
                    memberLong(payload, "positionMs", 0L),
                    memberStr(payload, "startedAt"));
        } else if ("room:reaction".equals(event)) {
            String uid = memberStr(payload, "userId");
            if (uid == null) uid = fromUserId;
            if (uid == null || uid.isEmpty()) return;
            if (myUserId != null && sameUser(myUserId, uid)) return; // already shown locally
            String key = memberStr(payload, "emojiKey");
            int res = SeatReactionEmojis.drawableForKey(key);
            if (res != 0) showRoomReaction(uid, key, res);
        } else if ("lucky:opened".equals(event)) {
            if (fromUserId != null && fromUserId.equals(myUserId)) return;
            String opener = memberStr(payload, "displayName");
            if (opener == null) opener = fromUsername != null ? fromUsername : "مستخدم";
            String rewardLabel = memberStr(payload, "rewardLabel");
            if (rewardLabel == null) rewardLabel = "جائزة";
            showLuckyWinOverlay(opener, rewardLabel);
        } else if ("lucky:hit".equals(event)) {
            // Lucky gift multiplier rain — show for everyone except the sender (local already did).
            String senderId = memberStr(payload, "senderId");
            if (senderId == null) senderId = fromUserId;
            if (myUserId != null && sameUser(myUserId, senderId)) return;
            String who = memberStr(payload, "senderName");
            if (who == null || who.isEmpty()) {
                who = fromUsername != null ? fromUsername : "مستخدم";
            }
            long won = Math.max(0L, memberLong(payload, "luckyCoinsWon", 0L));
            if (won <= 0) return;
            int mul = 1;
            boolean soft = false;
            if (payload.has("luckyMultiplier") && !payload.get("luckyMultiplier").isJsonNull()) {
                try {
                    double raw = payload.get("luckyMultiplier").getAsDouble();
                    soft = raw > 0 && raw < 1.0;
                    mul = soft ? 0 : Math.max(1, (int) Math.round(raw));
                } catch (Exception ignored) {
                    mul = Math.max(1, memberInt(payload, "luckyMultiplier", 1));
                }
            }
            showLuckyHitBanner(who, memberStr(payload, "senderAvatarUrl"), won, mul);
            GiftAudioFx.playLuckyCoins(this, soft ? 2 : Math.min(4, Math.max(2, mul)));
            // Only float/rain on the winner's seat.
            if (senderId != null && !senderId.isEmpty()) {
                showLuckyReturnOnMics(java.util.Collections.singletonList(senderId), won);
                playCoinRainToUsers(java.util.Collections.singletonList(senderId), 16);
            }
        } else if ("game:update".equals(event) || "game:invite".equals(event)) {
            // XO retired — ignore legacy realtime payloads
        } else if ("room:host_away".equals(event)) {
            // Host left UI / Zego but live session stays open — refresh seats, do not close.
            requestRoomRefresh(true);
            String awayId = memberStr(payload, "userId");
            if (myUserId == null || awayId == null || !sameUser(myUserId, awayId)) {
                Toast.makeText(this, "المضيف غادر مؤقتاً · الغرفة ما زالت مفتوحة",
                        Toast.LENGTH_SHORT).show();
            }
        } else if ("gift:animation".equals(event)) {
            scheduleSupporterRefresh(1_200L);
            String name = memberStr(payload, "giftName");
            if (name == null) name = "هدية";
            String icon = memberStr(payload, "iconUrl");
            if (icon == null || icon.isEmpty()) {
                icon = memberStr(payload, "giftIconUrl");
            }
            String anim = memberStr(payload, "animationUrl");
            String sender = memberStr(payload, "senderName");
            if (sender == null) sender = "مستخدم";
            int combo = Math.max(1, memberInt(payload, "comboCount", 1));
            int personCount = Math.max(1, memberInt(payload, "personCount", 1));
            boolean allMic = memberBool(payload, "allMic", false) || personCount > 1;
            long totalCoins = Math.max(0L, memberLong(payload, "totalCoins", 0L));
            if (totalCoins <= 0) {
                long price = Math.max(0L, memberLong(payload, "coinPrice", 0L));
                int qty = Math.max(1, memberInt(payload, "quantity", 1));
                totalCoins = price * qty * Math.max(1, personCount);
            }
            long perSeatCoins = personCount > 1
                    ? Math.max(1L, totalCoins / personCount)
                    : totalCoins;
            String receiverId = memberStr(payload, "receiverId");
            String senderId = memberStr(payload, "senderId");
            if (senderId == null) senderId = fromUserId;
            if (myUserId != null && (myUserId.equals(fromUserId) || myUserId.equals(senderId))) {
                return; // The successful send response already queued the local effect.
            }
            String giftKind = memberStr(payload, "giftType");
            if (giftKind != null && "lucky".equalsIgnoreCase(giftKind)) {
                GiftAudioFx.playLuckyCoins(this, 3);
            }
            List<String> allReceivers = new ArrayList<>();
            if (payload != null && payload.has("receiverIds") && payload.get("receiverIds").isJsonArray()) {
                for (com.google.gson.JsonElement el : payload.getAsJsonArray("receiverIds")) {
                    if (el == null || el.isJsonNull()) continue;
                    String id = el.getAsString();
                    if (id != null && !id.isEmpty() && !allReceivers.contains(id)) {
                        allReceivers.add(id);
                    }
                }
            }
            if (allReceivers.isEmpty() && receiverId != null && !receiverId.isEmpty()) {
                allReceivers.add(receiverId);
            }
            String giftEventKey = firstNonEmpty(memberStr(payload, "sendId"),
                    memberStr(payload, "eventId"), memberStr(payload, "at"));
            if (giftEventKey == null) {
                giftEventKey = String.valueOf(senderId) + '|' + memberStr(payload, "giftId")
                        + '|' + receiverId + '|' + combo + '|' + personCount;
            }
            long giftNow = System.currentTimeMillis();
            recentGiftEffects.entrySet().removeIf(entry -> giftNow - entry.getValue() > 15_000L);
            if (recentGiftEffects.put(giftEventKey, giftNow) != null) return;
            boolean isLuckyGift = giftKind != null && "lucky".equalsIgnoreCase(giftKind);
            int qtyRemote = Math.max(1, memberInt(payload, "quantity", 1));
            // Lucky: Mikoo center-hold → scatter (no JSON/Lottie pulse).
            if (isLuckyGift) {
                anim = null;
                List<String> rainTargets = collectOccupiedMicUserIds();
                if (rainTargets.isEmpty() && !allReceivers.isEmpty()) {
                    rainTargets = allReceivers;
                }
                playLuckyGiftStage(
                        icon,
                        rainTargets,
                        Math.max(1L, totalCoins),
                        qtyRemote,
                        Math.max(1, rainTargets.isEmpty() ? personCount : rainTargets.size()),
                        sender,
                        null);
                int senderVipChat = Math.max(0, memberInt(payload, "senderVipLevel", 0));
                int senderLevelChat = Math.max(1, memberInt(payload, "senderUserLevel", 1));
                String senderFrameChat = memberStr(payload, "senderHostBadgeUrl");
                String senderVipFrameChat = memberStr(payload, "senderVipBadgeUrl");
                if (isAgencyRoom) {
                    if (senderFrameChat == null || senderFrameChat.isEmpty()) {
                        senderFrameChat = senderVipFrameChat;
                    }
                } else if (senderVipFrameChat != null && !senderVipFrameChat.isEmpty()) {
                    senderFrameChat = senderVipFrameChat;
                }
                announceLuckyGiftChat(
                        name, icon, sender, 1, senderId, senderVipChat,
                        memberStr(payload, "senderAvatarUrl"), senderLevelChat, senderFrameChat);
            } else if (allMic && !allReceivers.isEmpty()) {
                // Mikoo: center gift → simultaneous clones to every target mic.
                String sendName = sender != null && !sender.isEmpty() ? sender : "مستخدم";
                long perPerson = personCount > 0 ? Math.max(1L, totalCoins / personCount) : totalCoins;
                scatterGiftToMics(icon, allReceivers, combo,
                        Math.max(1, qtyRemote), perPerson, sendName);
            }
            long receiverRoomGiftTotal =
                    Math.max(0L, memberLong(payload, "receiverRoomGiftTotal", 0L));
            if (effectQueue != null) {
                String tier = memberStr(payload, "supporterTier");
                if (tier == null) tier = "normal";
                int senderVip = Math.max(0, memberInt(payload, "senderVipLevel", 0));
                int senderLevel = Math.max(1, memberInt(payload, "senderUserLevel", 1));
                String senderFrame = memberStr(payload, "senderHostBadgeUrl");
                String senderVipFrame = memberStr(payload, "senderVipBadgeUrl");
                if (isAgencyRoom) {
                    if (senderFrame == null || senderFrame.isEmpty()) senderFrame = senderVipFrame;
                } else {
                    senderFrame = senderVipFrame != null && !senderVipFrame.isEmpty()
                            ? senderVipFrame : senderFrame;
                }
                if (sender == null || sender.isEmpty() || "null".equals(sender)) sender = "مستخدم";
                RoomEffectQueue.EffectType giftType = totalCoins >= 5000
                        || "legendary".equals(tier)
                        ? RoomEffectQueue.EffectType.LEGENDARY_GIFT
                        : RoomEffectQueue.EffectType.GIFT;
                String playTo = allReceivers.isEmpty() ? receiverId : allReceivers.get(0);
                // Lucky stage already plays the visual — queue only chat/banner, no second Lottie.
                if (!isLuckyGift) {
                String playAnimRemote = anim;
                String mappedRemote = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                        name, icon, anim);
                if (mappedRemote != null) playAnimRemote = mappedRemote;
                boolean videoRemote = com.Dramizo.Series.util.CosmeticMedia.kind(
                        com.Dramizo.Series.util.CosmeticMedia.playableUrl(playAnimRemote))
                        == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO;
                if (videoRemote && playAnimRemote != null) {
                    com.Dramizo.Series.util.NativeRoomEffectsView.preloadGiftUrls(
                            this, java.util.Collections.singletonList(playAnimRemote));
                }
                effectQueue.enqueue(new RoomEffectQueue.Builder()
                        .type(giftType)
                        .displayName(sender)
                        .senderUserId(senderId)
                        .senderVipLevel(senderVip)
                        .userLevel(senderLevel)
                        .avatarUrl(memberStr(payload, "senderAvatarUrl"))
                        .hostBadgeUrl(senderFrame)
                        .supporterTier(tier)
                        .giftName(name)
                        .giftIconUrl(icon)
                        .giftAnimationUrl(playAnimRemote)
                        .comboCount(combo)
                        .totalCoins(perSeatCoins)
                        .receiverId(playTo)
                        .receiverGiftCoins(perSeatCoins)
                        .durationMs(videoRemote ? 90_000L : 0L)
                        .build(effectQueue));
                }
                for (String rid : allReceivers) {
                    applyGiftToSeat(rid, perSeatCoins,
                            rid.equals(playTo) ? receiverRoomGiftTotal : 0L);
                }
            } else if (!isLuckyGift) {
                int senderVip = Math.max(0, memberInt(payload, "senderVipLevel", 0));
                int senderLevel = Math.max(1, memberInt(payload, "senderUserLevel", 1));
                playGiftAnimationRemote(name, icon, anim, sender, combo, senderId, senderVip,
                        memberStr(payload, "senderAvatarUrl"), senderLevel,
                        memberStr(payload, "senderHostBadgeUrl"));
                for (String rid : allReceivers) {
                    if (perSeatCoins > 0) applyGiftToSeat(rid, perSeatCoins, 0L);
                }
            } else {
                for (String rid : allReceivers) {
                    if (perSeatCoins > 0) applyGiftToSeat(rid, perSeatCoins, 0L);
                }
            }
        } else if ("room:like".equals(event)) {
            float hx = memberFloat(payload, "x", -1f);
            float hy = memberFloat(payload, "y", -1f);
            if (hx < 0 || hy < 0) {
                if (binding != null && binding.likeHeartsOverlay != null) {
                    hx = binding.likeHeartsOverlay.getWidth() / 2f;
                    hy = binding.likeHeartsOverlay.getHeight() * 0.65f;
                }
            }
            spawnFloatingHearts(hx, hy);
        } else if ("room:background".equals(event)) {
            String backgroundUrl = payload.has("backgroundUrl")
                    && !payload.get("backgroundUrl").isJsonNull()
                    ? payload.get("backgroundUrl").getAsString() : null;
            applyRoomBackground(backgroundUrl);
        } else if ("room:frame".equals(event)) {
            requestRoomRefresh(true);
        } else if ("agency:supporters_updated".equals(event)) {
            scheduleSupporterRefresh(600L);
        } else if ("room:viewer_count".equals(event)) {
            setRoomViewerCount(Math.max(0, memberInt(payload, "viewerCount", 0)));
        } else if ("room:updated".equals(event)) {
            requestRoomRefresh(false);
        } else if ("room:gift_sounds".equals(event)) {
            boolean enabled = memberBool(payload, "giftSoundsEnabled", true);
            applyRoomGiftSounds(enabled);
            if (!canManageRoom) {
                Toast.makeText(this,
                        enabled ? R.string.room_gift_sounds_on : R.string.room_gift_sounds_off,
                        Toast.LENGTH_SHORT).show();
            }
        } else if ("room:display_settings".equals(event)) {
            RoomDtos.RoomDto cur = viewModel.getRoom().getValue();
            if (cur != null) {
                cur.chatZoneEnabled = memberBool(payload, "chatZoneEnabled", cur.chatZoneEnabled);
                cur.charmEnabled = memberBool(payload, "charmEnabled", cur.charmEnabled);
                cur.bannerEnabled = memberBool(payload, "bannerEnabled", cur.bannerEnabled);
                cur.micInteractEnabled = memberBool(payload, "micInteractEnabled", cur.micInteractEnabled);
                cur.entryEffectsEnabled = memberBool(payload, "entryEffectsEnabled", cur.entryEffectsEnabled);
                cur.lowGiftEffectsEnabled = memberBool(payload, "lowGiftEffectsEnabled", cur.lowGiftEffectsEnabled);
                applyRoomDisplaySettings(cur);
            } else {
                applyChatZone(memberBool(payload, "chatZoneEnabled", true));
                applyRoomBanner(memberBool(payload, "bannerEnabled", true));
            }
        } else if ("room:mic_changed".equals(event)) {
            String targetUserId = memberStr(payload, "userId");
            boolean muted = memberBool(payload, "muted", false);
            boolean moderatorMuted = memberBool(payload, "moderatorMuted", false)
                    || memberBool(payload, "isModeratorMuted", false);
            if (myUserId != null && sameUser(myUserId, targetUserId)) {
                if (moderatorMuted || muted) {
                    RoomSoundFx.playMute(this);
                    micOn = false;
                    RoomRtcEngine.getInstance().setMicEnabled(false);
                    syncMicUi();
                    if (moderatorMuted) {
                        Toast.makeText(this, "قام مشرف الغرفة بكتم المايك", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Server unmuted us — restore publish so others can hear again.
                    userChoseMute = false;
                    micOn = true;
                    RoomRtcEngine.getInstance().setMicEnabled(true);
                    syncMicUi();
                    activateSeatAudio(currentSeats);
                }
            }
            requestRoomRefresh(true);
        } else if ("lucky-wheel:spin".equals(event)) {
            String player = firstNonEmpty(memberStr(payload, "displayName"),
                    memberStr(payload, "username"), shortUserId(memberStr(payload, "userId")));
            long payout = Math.max(0L, memberLong(payload, "payout", 0L));
            appendChatLine(
                    "عجلة الحظ",
                    player + (payout > 0 ? " ربح " + payout : " لم يربح هذه الجولة"),
                    0,
                    1);
        } else if ("dice:roll".equals(event)) {
            String player = firstNonEmpty(memberStr(payload, "displayName"),
                    memberStr(payload, "username"), shortUserId(memberStr(payload, "userId")));
            int total = Math.max(0, memberInt(payload, "total", 0));
            boolean won = memberBool(payload, "won", false);
            appendChatLine(
                    "لعبة النرد",
                    player + " رمى " + total + (won ? " وفاز" : ""),
                    0,
                    1);
        } else if ("room:slot_active".equals(event)) {
            String who = firstNonEmpty(memberStr(payload, "displayName"),
                    shortUserId(memberStr(payload, "userId")));
            String gameTitle = firstNonEmpty(memberStr(payload, "gameTitle"),
                    memberStr(payload, "gameId"), "لعبة");
            String gameIcon = firstNonEmpty(memberStr(payload, "gameCoverUrl"),
                    memberStr(payload, "gameIconUrl"));
            String avatar = memberStr(payload, "avatarUrl");
            String uid = memberStr(payload, "userId");
            appendChatLine(who, "يلعب الآن · " + gameTitle, 0, 1, null, uid, avatar, gameIcon);
        } else if ("room:slot_play".equals(event)) {
            String who = firstNonEmpty(memberStr(payload, "displayName"),
                    shortUserId(memberStr(payload, "userId")));
            String gameTitle = firstNonEmpty(memberStr(payload, "gameTitle"),
                    memberStr(payload, "gameId"), "لعبة");
            long bet = Math.max(0L, memberLong(payload, "betCoins", 0L));
            long win = Math.max(0L, memberLong(payload, "winCoins", 0L));
            boolean won = memberBool(payload, "won", false) || win > 0;
            if (bet <= 0 && !won) return;
            String gameIcon = firstNonEmpty(memberStr(payload, "gameCoverUrl"),
                    memberStr(payload, "gameIconUrl"));
            if (won) {
                String line = "مبروك " + who + " حصل على " + win
                        + (gameTitle != null ? (" · " + gameTitle) : "");
                appendGameCelebrationLine(line, gameIcon);
            } else {
                String avatar = memberStr(payload, "avatarUrl");
                String uid = memberStr(payload, "userId");
                appendChatLine(who, who + " لعب " + gameTitle + (bet > 0 ? " · رهان " + bet : ""),
                        0, 1, null, uid, avatar, gameIcon);
            }
        } else if ("room:slot_win".equals(event)) {
            String who = firstNonEmpty(memberStr(payload, "displayName"),
                    shortUserId(memberStr(payload, "userId")));
            String gameTitle = firstNonEmpty(memberStr(payload, "gameTitle"),
                    memberStr(payload, "gameId"), "لعبة");
            long win = Math.max(0L, memberLong(payload, "winCoins", 0L));
            if (win <= 0) return;
            String gameIcon = firstNonEmpty(memberStr(payload, "gameCoverUrl"),
                    memberStr(payload, "gameIconUrl"));
            String chat = "مبروك " + who + " حصل على " + win
                    + (gameTitle != null ? (" · " + gameTitle) : "");
            appendGameCelebrationLine(chat, gameIcon);
            if (!areCelebrationPopupsMuted()) {
                RoomVisualEffects bubbleFx = giftVisualEffects != null ? giftVisualEffects : visualEffects;
                if (bubbleFx != null) {
                    bubbleFx.showSlotWinBubble(who, null, win, gameTitle, gameIcon);
                }
                if (binding != null && binding.giftChatEffects != null) {
                    binding.giftChatEffects.setVisibility(View.VISIBLE);
                    binding.giftChatEffects.bringToFront();
                }
            }
            // Stacked in-room bubble only — no ComingMsg strip / no personal portrait.
        } else if ("room:slot_lose".equals(event)) {
            String who = firstNonEmpty(memberStr(payload, "displayName"),
                    shortUserId(memberStr(payload, "userId")));
            String gameTitle = firstNonEmpty(memberStr(payload, "gameTitle"),
                    memberStr(payload, "gameId"), "لعبة");
            long bet = Math.max(0L, memberLong(payload, "betCoins", 0L));
            if (bet <= 0) return;
            String gameIcon = firstNonEmpty(memberStr(payload, "gameCoverUrl"),
                    memberStr(payload, "gameIconUrl"));
            appendGameCelebrationLine(who + " خسر " + bet + " · " + gameTitle, gameIcon);
            RoomVisualEffects bubbleFx = giftVisualEffects != null ? giftVisualEffects : visualEffects;
            if (bubbleFx != null) {
                bubbleFx.showSlotLoseBubble(who, null, bet, gameTitle, gameIcon);
            }
            if (binding != null && binding.giftChatEffects != null) {
                binding.giftChatEffects.setVisibility(View.VISIBLE);
                binding.giftChatEffects.bringToFront();
            }
        } else if ("room:slot_ended".equals(event)) {
            String who = firstNonEmpty(memberStr(payload, "displayName"),
                    shortUserId(memberStr(payload, "userId")));
            String gameTitle = firstNonEmpty(memberStr(payload, "gameTitle"),
                    memberStr(payload, "gameId"), "لعبة");
            String gameIcon = firstNonEmpty(memberStr(payload, "gameCoverUrl"),
                    memberStr(payload, "gameIconUrl"));
            appendGameCelebrationLine(who + " أنهى اللعب · " + gameTitle, gameIcon);
        } else if ("room:kicked".equals(event)) {
            String kickedUserId = memberStr(payload, "userId");
            if (myUserId != null && sameUser(myUserId, kickedUserId)) {
                forceExitRoom("تم طردك من الغرفة");
            } else {
                appendChatLine("النظام", "تم طرد مستخدم من الغرفة", 0, 1);
                if (kickedUserId != null) {
                    RoomRtcEngine.getInstance().stopPlaying(
                            RoomRtcEngine.audioStreamId(kickedUserId));
                }
                requestRoomRefresh(true);
            }
        } else if ("room:banned".equals(event)) {
            String bannedUserId = memberStr(payload, "userId");
            if (myUserId != null && sameUser(myUserId, bannedUserId)) {
                forceExitRoom("تم حظرك من الغرفة");
            } else {
                appendChatLine("النظام", "تم حظر مستخدم من الغرفة", 0, 1);
                if (bannedUserId != null) {
                    RoomRtcEngine.getInstance().stopPlaying(
                            RoomRtcEngine.audioStreamId(bannedUserId));
                }
                requestRoomRefresh(true);
            }
        } else if ("room:chat_muted".equals(event)) {
            String mutedId = memberStr(payload, "userId");
            if (myUserId != null && sameUser(myUserId, mutedId)) {
                applyChatMuteFromServer(
                        memberStr(payload, "until"),
                        memberStr(payload, "reason"));
            }
            requestRoomRefresh(false);
        } else if ("room:unbanned".equals(event)) {
            String uid = memberStr(payload, "userId");
            if (myUserId != null && sameUser(myUserId, uid)) {
                chatMutedUntilMs = 0L;
                Toast.makeText(this, "تم رفع العقوبة عنك", Toast.LENGTH_SHORT).show();
            }
        } else if ("room:suspended".equals(event)) {
            forceExitRoom("تم تعليق الوكالة وإيقاف الغرفة");
        } else if ("room:seat_requests_cleared".equals(event)) {
            clearSeatRequestsUi();
        } else if ("room:closed".equals(event) || "room:auto_closed".equals(event)) {
            clearSeatRequestsUi();
            clearTopSupportersUi();
            clearRoomChatSession();
            if (isHost) {
                forceExitRoom("تم إغلاق الغرفة");
            } else {
                hopToNextRoomOrExit("تم إغلاق الغرفة");
            }
        } else if ("room:hand_raised".equals(event)) {
            if (isFreeMicEnabled()) {
                // Free mic: ignore raise-hand queue noise.
                return;
            }
            String name = memberStr(payload, "displayName");
            if (name == null) name = "ضيف";
            appendChatLine("النظام", name + " يطلب الانضمام للمايك ✋", 0, 1);
            if (canInviteMic && roomId != null) {
                viewModel.loadSeatRequests(roomId);
                Toast.makeText(this, name + " يطلب المايك — اضغط الجرس للموافقة", Toast.LENGTH_LONG).show();
            }
        } else if ("room:hand_lowered".equals(event)) {
            if (canInviteMic && roomId != null) viewModel.loadSeatRequests(roomId);
            requestRoomRefresh(false);
        } else if ("room:seat_approved".equals(event) || "room:seat_taken".equals(event)) {
            String approvedId = memberStr(payload, "userId");
            String approvedName = memberStr(payload, "displayName");
            if (approvedName == null) approvedName = "مستخدم";
            // Free-mic sit uses room:seat_taken — still show climb line for others.
            if (!sameUser(myUserId, approvedId) || "room:seat_approved".equals(event)) {
                appendChatLine("النظام", approvedName + " صعد إلى المايك 🎤", 0, 1);
            }
            if (myUserId != null && sameUser(myUserId, approvedId)) {
                if ("room:seat_approved".equals(event)) {
                    Toast.makeText(this, R.string.mic_request_approved, Toast.LENGTH_SHORT).show();
                }
                if (!PermissionHelper.hasAudioPermission(this)) {
                    PermissionHelper.ensureMediaPermissions(this, false);
                }
                userChoseMute = false;
                micOn = true;
                RoomRtcEngine.getInstance().setMicEnabled(true);
                if (roomId != null) viewModel.setMic(roomId, false);
                handler.postDelayed(() -> activateSeatAudio(currentSeats), 400);
            }
            if (canInviteMic && roomId != null) viewModel.loadSeatRequests(roomId);
            requestRoomRefresh(true);
        } else if ("room:seat_rejected".equals(event)) {
            String rejectedId = memberStr(payload, "userId");
            if (myUserId != null && sameUser(myUserId, rejectedId)) {
                Toast.makeText(this, R.string.mic_request_rejected, Toast.LENGTH_SHORT).show();
            }
            if (canInviteMic && roomId != null) viewModel.loadSeatRequests(roomId);
        } else if ("room:seat_left".equals(event)) {
            String leftId = payload.has("userId") ? payload.get("userId").getAsString() : null;
            boolean forced = payload.has("forced")
                    && !payload.get("forced").isJsonNull()
                    && payload.get("forced").getAsBoolean();
            if (myUserId != null && sameUser(myUserId, leftId)) {
                micOn = false;
                userChoseMute = false;
                RoomRtcEngine.getInstance().setMicEnabled(false);
                RoomRtcEngine.getInstance().stopPublishing();
                if (forced) {
                    Toast.makeText(this, "تم إنزالك من المقعد بسبب مخالفة", Toast.LENGTH_LONG).show();
                }
            } else if (leftId != null) {
                RoomRtcEngine.getInstance().stopPlaying(RoomRtcEngine.audioStreamId(leftId));
            }
            appendChatLine("النظام",
                    forced ? "تم إنزال مستخدم من المقعد (رقابة)" : "مستخدم نزل من المايك",
                    0, 1);
            requestRoomRefresh(true);
        } else if ("room:staff_updated".equals(event)
                || "room:seat_locked".equals(event)
                || "room:seats_resized".equals(event)
                || "room:lock_changed".equals(event)) {
            // Permissions / layout / password — pull full snapshot immediately.
            requestRoomRefresh(true);
        } else if ("room:user_left".equals(event)) {
            int vc = memberInt(payload, "viewerCount", -1);
            if (vc >= 0) {
                setRoomViewerCount(vc);
            } else {
                String leftId = memberStr(payload, "userId");
                if (leftId != null && !sameUser(leftId, myUserId)) {
                    bumpRoomViewers(-1);
                }
            }
            requestRoomRefresh(false);
        } else if ("room:supporters_cleared".equals(event)) {
            clearTopSupportersUi();
        }
    }

    private void forceExitRoom(String message) {
        if (exiting || hoppingRoom) return;
        clearSeatRequestsUi();
        clearTopSupportersUi();
        exiting = true;
        minimizing = false;
        handler.removeCallbacks(refreshRunnable);
        teardownRoomSession(true);
        if (message != null && !message.isEmpty()) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
        navigateHomeAndFinish();
    }

    /** When the current room closes, open another public room; otherwise leave. */
    private void hopToNextRoomOrExit(String message) {
        if (exiting || hoppingRoom || isHost || roomId == null) return;
        hoppingRoom = true;
        final String closedId = roomId;
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            RoomDtos.RoomDto next = null;
            try {
                Result<MiscDtos.ListResult<RoomDtos.RoomDto>> result =
                        container.listRoomsUseCase.execute(1);
                if (result.success && result.data != null && result.data.items != null) {
                    for (RoomDtos.RoomDto room : result.data.items) {
                        if (room == null || room.id == null || room.hasPassword) continue;
                        if (closedId.equals(room.id)) continue;
                        next = room;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            RoomDtos.RoomDto target = next;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (target == null || target.id == null) {
                    hoppingRoom = false;
                    forceExitRoom(message != null ? message : "تم إغلاق الغرفة");
                    return;
                }
                Toast.makeText(getApplicationContext(), "تم الانتقال إلى غرفة أخرى",
                        Toast.LENGTH_SHORT).show();
                hoppingRoom = false;
                switchRoomInPlace(target.id, null);
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
            });
        });
    }

    private void setupZegoListener() {
        zegoRoomListener = new ZegoEngineManager.RoomListener() {
            @Override
            public void onRoomStateChanged(String roomID, int state) {
                // LOGINED=1, RECONNECTING/RECONNECTED≈4/5 — force re-publish after flaps.
                if (state == 1 || state == 4 || state == 5) {
                    handler.post(() -> activateSeatAudio(currentSeats, true));
                }
            }

            @Override
            public void onRoomStateChanged(String roomID, int state, int errorCode) {
                onRoomStateChanged(roomID, state);
                if (errorCode != 0 && !exiting && realtimeJoined && roomId != null) {
                    handler.postDelayed(
                            VoiceRoomActivity.this::reconnectRtcWithFreshToken,
                            1_200L);
                }
            }

            @Override
            public void onStreamAdded(String streamId) {
                handler.post(() -> playRemoteAudio(streamId));
            }

            @Override
            public void onStreamRemoved(String streamId) {
                handler.post(() -> RoomRtcEngine.getInstance().stopPlaying(streamId));
            }

            @Override
            public void onSoundLevel(String userId, float level) {
                queueSoundLevel(userId, level);
            }

            @Override
            public void onRoomChatMessage(String rid, String fromUserId, String fromUserName,
                                          String jsonOrText) {
                if (rid == null || roomId == null || !roomId.equals(rid)) return;
                if (fromUserId != null && myUserId != null && sameUser(fromUserId, myUserId)) {
                    return; // optimistic local append already shown
                }
                handler.post(() -> applyZegoRoomChat(fromUserId, fromUserName, jsonOrText));
            }
        };
        RoomRtcEngine.getInstance().addRoomListener(zegoRoomListener);
    }

    private void applyZegoRoomChat(
            @Nullable String fromUserId,
            @Nullable String fromUserName,
            @Nullable String jsonOrText
    ) {
        if (jsonOrText == null || jsonOrText.isEmpty() || exiting) return;
        String text = jsonOrText;
        String name = fromUserName != null ? fromUserName : "مستخدم";
        String avatar = null;
        String frame = null;
        int vip = 0;
        int level = 1;
        long wealth = 0L;
        long charm = 0L;
        String uid = fromUserId;
        try {
            if (jsonOrText.trim().startsWith("{")) {
                JsonObject o = com.google.gson.JsonParser.parseString(jsonOrText).getAsJsonObject();
                if (o.has("text") && !o.get("text").isJsonNull()) text = o.get("text").getAsString();
                if (o.has("name") && !o.get("name").isJsonNull()) name = o.get("name").getAsString();
                if (o.has("avatarUrl") && !o.get("avatarUrl").isJsonNull()) {
                    avatar = o.get("avatarUrl").getAsString();
                }
                if (o.has("frameUrl") && !o.get("frameUrl").isJsonNull()) {
                    frame = o.get("frameUrl").getAsString();
                } else if (o.has("vipBadgeUrl") && !o.get("vipBadgeUrl").isJsonNull()) {
                    frame = o.get("vipBadgeUrl").getAsString();
                }
                if (o.has("userId") && !o.get("userId").isJsonNull()) {
                    uid = o.get("userId").getAsString();
                }
                if (o.has("vipLevel") && !o.get("vipLevel").isJsonNull()) {
                    vip = o.get("vipLevel").getAsInt();
                }
                if (o.has("userLevel") && !o.get("userLevel").isJsonNull()) {
                    level = Math.max(1, o.get("userLevel").getAsInt());
                }
                if (o.has("wealthScore") && !o.get("wealthScore").isJsonNull()) {
                    wealth = o.get("wealthScore").getAsLong();
                }
                if (o.has("charmScore") && !o.get("charmScore").isJsonNull()) {
                    charm = o.get("charmScore").getAsLong();
                }
            }
        } catch (Exception ignored) {
        }
        if (text == null || text.trim().isEmpty()) return;
        appendChatLine(name, text.trim(), vip, level, frame, uid, avatar, null, wealth, charm);
    }

    private void queueSoundLevel(String userId, float level) {
        if (userId == null || userId.isEmpty()) return;
        synchronized (pendingSoundLevels) {
            pendingSoundLevels.put(userId, level);
            if (soundLevelDrainPending) return;
            soundLevelDrainPending = true;
        }
        handler.postDelayed(this::drainSoundLevels, 50L);
    }

    private void drainSoundLevels() {
        Map<String, Float> levels;
        synchronized (pendingSoundLevels) {
            levels = new HashMap<>(pendingSoundLevels);
            pendingSoundLevels.clear();
            soundLevelDrainPending = false;
        }
        if (exiting) return;
        long now = System.currentTimeMillis();
        boolean hostUpdated = false;
        for (Map.Entry<String, Float> entry : levels.entrySet()) {
            String userId = entry.getKey();
            String key = normalizeUserId(userId);
            if (key.isEmpty()) continue;
            float level = entry.getValue() != null ? entry.getValue() : 0f;
            boolean wasSpeaking = speakingUsers.contains(key);
            // Sensitive enough for quiet speech; hangover prevents flicker.
            boolean speaking = level >= (wasSpeaking ? 1.2f : 2.0f);
            if (speaking) {
                speakingUsers.add(key);
                lastSoundTickMs.put(key, now);
            } else if (wasSpeaking && level >= 0.6f
                    && (now - lastSoundTickMs.getOrDefault(key, 0L)) < 420L) {
                speaking = true; // hangover so waves feel continuous
            } else {
                speakingUsers.remove(key);
                if (level <= 0.01f) lastSoundTickMs.remove(key);
            }
            if (seatAdapter != null) {
                seatAdapter.setSpeaking(userId, speaking, level);
            }
            if (isHostStageUser(userId)) {
                setHostStageSpeaking(speaking);
                hostUpdated = true;
            }
        }
        // Stop FX for anyone who stopped reporting levels (left/mic closed).
        if (seatAdapter != null && !speakingUsers.isEmpty()) {
            java.util.ArrayList<String> stale = new java.util.ArrayList<>();
            for (String key : speakingUsers) {
                if (now - lastSoundTickMs.getOrDefault(key, 0L) > 700L) {
                    stale.add(key);
                }
            }
            for (String key : stale) {
                lastSoundTickMs.remove(key);
                seatAdapter.setSpeaking(key, false);
                if (isHostStageUser(key)) {
                    setHostStageSpeaking(false);
                    hostUpdated = true;
                }
            }
        }
        if (!hostUpdated && hostStageUserId != null
                && !speakingUsers.contains(normalizeUserId(hostStageUserId))) {
            setHostStageSpeaking(false);
        }
    }

    private boolean isHostStageUser(@Nullable String userId) {
        if (userId == null || userId.isEmpty()) return false;
        if (hostStageUserId != null && sameUser(userId, hostStageUserId)) return true;
        if (roomHostId != null && sameUser(userId, roomHostId)) return true;
        // Local captured levels use myUserId — stage host is often me.
        return myUserId != null && sameUser(userId, myUserId)
                && (sameUser(myUserId, hostStageUserId) || sameUser(myUserId, roomHostId));
    }

    private void setHostStageSpeaking(boolean speaking) {
        if (binding == null) return;
        // hostStage is retired (0dp/gone) — speaking waves live on SeatAdapter cells only.
        if (binding.hostStage == null || binding.hostStage.getVisibility() != View.VISIBLE) {
            hostStageSpeaking = false;
            return;
        }
        if (hostStageSpeaking == speaking) {
            if (speaking) ensureHostRipplesRunning();
            return;
        }
        hostStageSpeaking = speaking;
        View outer = binding.hostRippleOuter;
        View mid = binding.hostRippleMid;
        View inner = binding.hostRippleInner;
        if (!speaking) {
            stopHostRipple(outer);
            stopHostRipple(mid);
            stopHostRipple(inner);
            return;
        }
        if (binding.hostStageAvatarWrap != null) {
            binding.hostStageAvatarWrap.setClipChildren(false);
            binding.hostStageAvatarWrap.setClipToPadding(false);
        }
        if (outer != null) {
            outer.bringToFront();
            outer.setAlpha(1f);
            outer.setVisibility(View.VISIBLE);
            startHostRipple(outer, 0);
        }
        if (mid != null) {
            mid.bringToFront();
            mid.setAlpha(1f);
            mid.setVisibility(View.VISIBLE);
            startHostRipple(mid, 300);
        }
        if (inner != null) {
            inner.bringToFront();
            inner.setAlpha(1f);
            inner.setVisibility(View.VISIBLE);
            startHostRipple(inner, 600);
        }
        if (binding.imgHostStageMuted != null) binding.imgHostStageMuted.bringToFront();
        if (binding.imgRoomHostReaction != null) binding.imgRoomHostReaction.bringToFront();
        if (binding.imgHostStageReaction != null) binding.imgHostStageReaction.bringToFront();
    }

    private void ensureHostRipplesRunning() {
        if (binding == null || !hostStageSpeaking) return;
        if (binding.hostRippleOuter != null && binding.hostRippleOuter.getAnimation() == null) {
            hostStageSpeaking = false;
            setHostStageSpeaking(true);
        }
    }

    private void startHostRipple(View view, long offset) {
        if (view == null) return;
        view.clearAnimation();
        view.setAlpha(1f);
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        android.view.animation.ScaleAnimation scale = new android.view.animation.ScaleAnimation(
                0.92f, 1.12f, 0.92f, 1.12f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        android.view.animation.AlphaAnimation alpha = new android.view.animation.AlphaAnimation(0.85f, 0.08f);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        set.setDuration(900);
        set.setStartOffset(offset);
        set.setRepeatCount(android.view.animation.Animation.INFINITE);
        set.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        set.setFillAfter(false);
        view.startAnimation(set);
    }

    private void stopHostRipple(View view) {
        if (view == null) return;
        view.clearAnimation();
        view.setVisibility(View.INVISIBLE);
        view.setAlpha(1f);
    }

    private void playRemoteAudio(String streamId) {
        if (streamId == null) return;
        String mine = RoomRtcEngine.audioStreamId(myUserId);
        if (streamId.equals(mine) || streamId.equals(RoomRtcEngine.getInstance().getPublishingStreamId())) return;
        if (streamId.endsWith("_host")) return;
        RoomRtcEngine.getInstance().startPlayingAudio(streamId);
    }

    private void applyVoiceRoomInsets() {
        final float density = getResources().getDisplayMetrics().density;
        final int headerBase = Math.round(6 * density);
        final int edgeBase = Math.round(8 * density);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Status bar / notch: lift header without shifting the whole room.
            setTopMargin(binding.headerRoom, headerBase + bars.top);

            // Symmetric edge inset so the room card and close control hug the screen
            // edges equally (cutout when present; otherwise a tight 8dp pad).
            if (binding.headerRoom != null) {
                int padStart = Math.max(edgeBase, bars.left);
                int padEnd = Math.max(edgeBase, bars.right);
                binding.headerRoom.setPaddingRelative(padStart, 0, padEnd, 0);
            }

            int navBottom = bars.bottom;
            int imeBottom = ime.bottom;
            // Tools row never climbs the keyboard — only the white send bar does.
            if (binding.bottomBar != null) {
                setBottomMargin(binding.bottomBar, navBottom);
                if (roomComposerOpen) {
                    binding.bottomBar.setVisibility(View.GONE);
                }
            }
            // Mikoo MultiInputMsgView: pad overlay so white bar sits on keyboard; dismiss on IME hide.
            if (binding.roomChatComposer != null) {
                if (roomComposerOpen) {
                    binding.roomChatComposer.setPadding(0, 0, 0, Math.max(navBottom, imeBottom));
                    if (imeBottom > 0) {
                        roomComposerImeWasOpen = true;
                    } else if (roomComposerImeWasOpen) {
                        roomComposerImeWasOpen = false;
                        closeRoomChatComposer(false);
                    }
                } else {
                    binding.roomChatComposer.setPadding(0, 0, 0, 0);
                    roomComposerImeWasOpen = false;
                }
            }
            if (imeBottom > 0) {
                scrollChatToBottom(false);
            }
            applyGameOverlaySafeInsets(navBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    /** Lift room-game controls above Android nav bar (Mikoo decorView padding). */
    private void applyGameOverlaySafeInsets(int navBottom) {
        if (binding == null || binding.gameOverlay == null) return;
        // Width full-bleed; dock sits above bottomBar (no mid-screen raise).
        if (binding.gameOverlay.getVisibility() != View.VISIBLE) {
            binding.gameOverlay.setPadding(0, 0, 0, 0);
            return;
        }
        binding.gameOverlay.setPadding(0, 0, 0, 0);
        if (roomGameWebView != null) {
            ensureRoomGameWebView().setPadding(0, 0, 0, 0);
        }
        if (binding.gamePanel != null) {
            binding.gamePanel.setPadding(0, 0, 0, 0);
        }
        try {
            if (binding.gameOverlay.getVisibility() == View.VISIBLE) {
                layoutRoomGamePanel(true);
            }
        } catch (Exception ignored) {
        }
    }

    private void setTopMargin(View view, int top) {
        if (view.getLayoutParams() instanceof ConstraintLayout.LayoutParams lp) {
            lp.topMargin = top;
            view.setLayoutParams(lp);
        }
    }

    private void setBottomMargin(View view, int bottom) {
        if (view.getLayoutParams() instanceof ConstraintLayout.LayoutParams lp) {
            lp.bottomMargin = bottom;
            view.setLayoutParams(lp);
        }
    }

    private boolean isOnSeat(List<RoomDtos.SeatDto> seats) {
        return findMySeat(seats) != null;
    }

    private void reconnectRtcWithFreshToken() {
        if (rtcReconnectInFlight || exiting || roomId == null || myUserId == null) return;
        rtcReconnectInFlight = true;
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> result =
                    ApiCall.execute(container.getRoomApi().zegoToken(roomId));
            runOnUiThread(() -> {
                if (!result.success || result.data == null || result.data.token == null
                        || exiting || roomId == null) {
                    rtcReconnectInFlight = false;
                    return;
                }
                rtcCanPublish = result.data.canPublish;
                rtcPublishTokenExpiresAtMs = result.data.expireAt > 0
                        ? result.data.expireAt * 1000L : 0L;
                RoomRtcEngine.getInstance().applyJoinSession(
                        VoiceRoomActivity.this, result.data, roomId, myUserId);
                if (zegoRoomListener != null) {
                    RoomRtcEngine.getInstance().preferActiveProviderListenersOnly(zegoRoomListener);
                }
                RoomRtcEngine.getInstance().setMicEnabled(micOn);
                zegoLoggedIn = true;
                handler.postDelayed(() -> {
                    rtcReconnectInFlight = false;
                    activateSeatAudio(currentSeats);
                }, 600);
            });
        });
    }

    private void syncVoiceAudio(List<RoomDtos.SeatDto> seats) {
        if (myUserId == null || myUserId.isEmpty()) return;
        if (!PermissionHelper.hasAudioPermission(this)) {
            if (isOnSeat(seats)) {
                PermissionHelper.ensureMediaPermissions(this, false);
            }
            return;
        }
        // LiveKit connect is async — retry until ready instead of dropping publish.
        if (!RoomRtcEngine.getInstance().isReady()) {
            if (isOnSeat(seats) && RoomRtcEngine.getInstance().isLiveKit()) {
                handler.postDelayed(() -> {
                    if (!exiting) syncVoiceAudio(currentSeats);
                }, 400L);
            }
            return;
        }
        String streamId = RoomRtcEngine.audioStreamId(myUserId);
        if (streamId == null) return;
        boolean onSeat = isOnSeat(seats);
        boolean moderatorMuted = false;
        if (onSeat && seats != null) {
            for (RoomDtos.SeatDto seat : seats) {
                if (seat != null && sameUser(myUserId, seatUserId(seat))) {
                    moderatorMuted = seat.isModeratorMuted;
                    break;
                }
            }
        }
        if (onSeat) {
            RoomRtcEngine.getInstance().setMicEnabled(micOn);
            if (moderatorMuted) {
                rtcCanPublish = false;
                handler.removeCallbacks(rtcTokenRefreshRunnable);
                RoomRtcEngine.getInstance().stopPublishing();
                return;
            }
            if (rtcCanPublish
                    && System.currentTimeMillis() < rtcPublishTokenExpiresAtMs - 20_000L) {
                RoomRtcEngine.getInstance().startPublishingAudio(streamId);
            } else {
                refreshRtcPublishToken(streamId);
            }
        } else {
            rtcPublishTokenExpiresAtMs = 0L;
            handler.removeCallbacks(rtcTokenRefreshRunnable);
            RoomRtcEngine.getInstance().stopPublishing();
        }
    }

    private void refreshRtcPublishToken(String streamId) {
        if (rtcTokenRequestInFlight || roomId == null) return;
        rtcTokenRequestInFlight = true;
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Result<RoomDtos.JoinRoomResult> result =
                    ApiCall.execute(container.getRoomApi().zegoToken(roomId));
            runOnUiThread(() -> {
                rtcTokenRequestInFlight = false;
                if (!result.success || result.data == null || result.data.token == null) {
                    Toast.makeText(this, "تعذر تفعيل صلاحية المايك", Toast.LENGTH_SHORT).show();
                    return;
                }
                String zegoRoom = result.data.zegoRoomId != null
                        ? result.data.zegoRoomId
                        : (pendingSession != null ? pendingSession.zegoRoomId : roomId);
                rtcPublishTokenExpiresAtMs = result.data.expireAt > 0
                        ? result.data.expireAt * 1000L
                        : System.currentTimeMillis() + 60_000L;
                rtcCanPublish = result.data.canPublish;
                RoomRtcEngine.getInstance().setSessionCanPublish(rtcCanPublish);
                // LiveKit: JWT is join-time only. If already connected AND grant already allows
                // publish, just open the mic (no reconnect thrash). Audience→seat still renews.
                boolean livekitAlreadyPublishable =
                        RoomRtcEngine.getInstance().isLiveKit()
                                && RoomRtcEngine.getInstance().isReady()
                                && rtcCanPublish
                                && LiveKitEngineManager.getInstance().isSessionCanPublish();
                if (livekitAlreadyPublishable) {
                    if (isOnSeat(currentSeats)) {
                        RoomRtcEngine.getInstance().startPublishingAudio(streamId);
                        RoomRtcEngine.getInstance().setMicEnabled(micOn);
                        handler.removeCallbacks(rtcTokenRefreshRunnable);
                        long refreshDelay = Math.max(
                                60_000L,
                                rtcPublishTokenExpiresAtMs - System.currentTimeMillis() - 20_000L);
                        handler.postDelayed(rtcTokenRefreshRunnable, refreshDelay);
                    } else {
                        handler.removeCallbacks(rtcTokenRefreshRunnable);
                        RoomRtcEngine.getInstance().stopPublishing();
                    }
                    return;
                }
                RoomRtcEngine.getInstance().renewRoomToken(zegoRoom, result.data.token);
                if (isOnSeat(currentSeats) && rtcCanPublish) {
                    RoomRtcEngine.getInstance().startPublishingAudio(streamId);
                    RoomRtcEngine.getInstance().setMicEnabled(micOn);
                    handler.removeCallbacks(rtcTokenRefreshRunnable);
                    long refreshDelay = Math.max(
                            15_000L,
                            rtcPublishTokenExpiresAtMs - System.currentTimeMillis() - 20_000L);
                    handler.postDelayed(rtcTokenRefreshRunnable, refreshDelay);
                } else {
                    handler.removeCallbacks(rtcTokenRefreshRunnable);
                    RoomRtcEngine.getInstance().stopPublishing();
                }
            });
        });
    }

    private final Runnable rtcTokenRefreshRunnable = () -> {
        rtcPublishTokenExpiresAtMs = 0L;
        syncVoiceAudio(currentSeats);
    };

    private long lastActivateSeatAudioAt;
    private String lastActivateSeatAudioKey;
    /** Bumped on room hop so delayed audio posts from the previous room are ignored. */
    private int roomAudioEpoch;

    private void activateSeatAudio(List<RoomDtos.SeatDto> seats) {
        activateSeatAudio(seats, false);
    }

    private void activateSeatAudio(List<RoomDtos.SeatDto> seats, boolean force) {
        boolean onSeat = isOnSeat(seats);
        String key = (onSeat ? "1" : "0") + "|" + micOn + "|"
                + (myUserId != null ? myUserId : "");
        long now = android.os.SystemClock.elapsedRealtime();
        // Avoid thrashing publish start/stop on every room poll — but never skip after
        // Zego login (force), or host publish stays dead until leave/rejoin.
        if (!force && key.equals(lastActivateSeatAudioKey) && now - lastActivateSeatAudioAt < 800L) {
            ensurePlayingSeatedAudio(seats);
            return;
        }
        lastActivateSeatAudioKey = key;
        lastActivateSeatAudioAt = now;
        syncVoiceAudio(seats);
        ensurePlayingSeatedAudio(seats);
        if (onSeat) {
            handler.postDelayed(() -> {
                syncVoiceAudio(currentSeats);
                ensurePlayingSeatedAudio(currentSeats);
            }, 350);
        }
    }

    private void refreshSelfHostWearOnSeats() {
        if (seatAdapter == null) return;
        String hostBadge = null;
        String vipBadge = null;
        java.util.Map<String, Object> meta = null;
        String displayName = null;
        String avatarUrl = null;
        try {
            AuthDtos.UserDto me = ContainerProvider.from(this).getSessionManager().getUser();
            if (me != null) {
                hostBadge = me.hostBadgeUrl;
                vipBadge = me.vipBadgeUrl;
                meta = me.hostBadgeMeta;
                displayName = me.displayName != null && !me.displayName.isEmpty()
                        ? me.displayName : me.username;
                avatarUrl = me.avatarUrl;
            }
            if (hostBadge == null || hostBadge.isEmpty()) {
                hostBadge = ContainerProvider.from(this).getSessionManager().getHostBadgeUrl();
            }
        } catch (Exception ignored) {
        }
        seatAdapter.setSelfHostWear(myUserId, hostBadge, vipBadge, meta, displayName, avatarUrl);
    }

    private RoomDtos.SeatDto findHostSeat(@Nullable List<RoomDtos.SeatDto> seats) {
        if (seats == null) return null;
        for (RoomDtos.SeatDto seat : seats) {
            if (seat != null && seat.seatIndex == 0) return seat;
        }
        return null;
    }

    @Nullable
    private static String seatUserId(@Nullable RoomDtos.SeatDto seat) {
        if (seat == null) return null;
        if (seat.userId != null && !seat.userId.isEmpty()) return seat.userId;
        return seat.user != null ? seat.user.id : null;
    }

    private List<RoomDtos.SeatDto> guestSeatsOnly(@Nullable List<RoomDtos.SeatDto> seats) {
        List<RoomDtos.SeatDto> guestSeats = new ArrayList<>();
        if (seats == null) return guestSeats;
        // Mikoo: seat 0 = header boss mic; grid shows guest mics 1..N only.
        for (RoomDtos.SeatDto seat : seats) {
            if (seat != null && seat.seatIndex > 0) {
                guestSeats.add(seat);
            }
        }
        guestSeats.sort((a, b) -> Integer.compare(a.seatIndex, b.seatIndex));
        return guestSeats;
    }

    /** Guest grid only — hide duplicate user rows (keep lowest seat index). */
    private List<RoomDtos.SeatDto> prepareGuestSeatsForGrid(@Nullable List<RoomDtos.SeatDto> seats) {
        List<RoomDtos.SeatDto> guests = guestSeatsOnly(seats);
        java.util.Map<String, Integer> canonical = new java.util.HashMap<>();
        List<RoomDtos.SeatDto> out = new ArrayList<>(guests.size());
        for (RoomDtos.SeatDto seat : guests) {
            String uid = seatUserId(seat);
            boolean duplicate = uid != null && !uid.isEmpty() && canonical.containsKey(uid);
            if (uid != null && !uid.isEmpty() && !duplicate) {
                canonical.put(uid, seat.seatIndex);
            }
            out.add(duplicate ? cloneSeatAsEmpty(seat) : seat);
        }
        return out;
    }

    private static RoomDtos.SeatDto cloneSeatAsEmpty(RoomDtos.SeatDto seat) {
        RoomDtos.SeatDto copy = new RoomDtos.SeatDto();
        copy.seatIndex = seat.seatIndex;
        copy.isLocked = seat.isLocked;
        copy.status = seat.locked() ? "locked" : "empty";
        return copy;
    }

    private static final int MIKOO_SEAT_COLUMNS = 5;

    /** UI guest mic presets (10/15/20) → backend total including host seat 0. */
    private static int totalSeatCountForGuestMics(int guestMics) {
        return Math.min(31, Math.max(2, guestMics + 1));
    }

    private void wireSeatCountButtons(View sheet, @Nullable Runnable onPicked) {
        View.OnClickListener seats = v -> {
            int guestMics = 10;
            int id = v.getId();
            if (id == R.id.btnSeats15) guestMics = 15;
            else if (id == R.id.btnSeats20) guestMics = 20;
            viewModel.resizeSeats(roomId, totalSeatCountForGuestMics(guestMics));
            Toast.makeText(this, "تم ضبط المقاعد: " + guestMics, Toast.LENGTH_SHORT).show();
            if (onPicked != null) onPicked.run();
        };
        int[] ids = {R.id.btnSeats10, R.id.btnSeats15, R.id.btnSeats20};
        for (int btnId : ids) {
            View button = sheet.findViewById(btnId);
            if (button != null) {
                button.setVisibility(View.VISIBLE);
                button.setOnClickListener(seats);
            }
        }
    }

    /** Mikoo grid: 5 columns, no scroll, compact scale for 15/20 mics. */
    private void applyMikooSeatGrid(int guestCount) {
        if (binding == null || binding.recyclerSeats == null || seatAdapter == null) return;
        int scale = guestCount <= 10
                ? SeatAdapter.SCALE_NORMAL
                : guestCount <= 15
                ? SeatAdapter.SCALE_MEDIUM
                : SeatAdapter.SCALE_COMPACT;
        seatAdapter.setScaleMode(scale);

        RecyclerView.LayoutManager lm = binding.recyclerSeats.getLayoutManager();
        if (lm instanceof GridLayoutManager grid && grid.getSpanCount() != MIKOO_SEAT_COLUMNS) {
            grid.setSpanCount(MIKOO_SEAT_COLUMNS);
        }

        int rows = guestCount <= 0 ? 1 : (guestCount + MIKOO_SEAT_COLUMNS - 1) / MIKOO_SEAT_COLUMNS;
        int rowHeightDp = scale == SeatAdapter.SCALE_COMPACT ? 70
                : scale == SeatAdapter.SCALE_MEDIUM ? 76 : 86;
        int totalHeightPx = dp(rows * rowHeightDp + 8);
        // Skip requestLayout on mute-only room refreshes — that was blinking seats.
        int layoutKey = (guestCount << 16) ^ (scale << 8) ^ totalHeightPx;
        if (layoutKey == lastSeatGridLayoutKey) return;
        lastSeatGridLayoutKey = layoutKey;
        ViewGroup.LayoutParams raw = binding.recyclerSeats.getLayoutParams();
        if (raw instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp) {
            clp.height = totalHeightPx;
            clp.matchConstraintMaxHeight = 0;
            clp.matchConstraintMinHeight = 0;
            clp.topMargin = dp(4);
            binding.recyclerSeats.setLayoutParams(clp);
        }
        binding.recyclerSeats.setNestedScrollingEnabled(false);
        binding.recyclerSeats.requestLayout();
    }

    /** Lock/unlock any empty guest seat individually (Mikoo-style). */
    private boolean canToggleSeatLock(@Nullable RoomDtos.SeatDto target, boolean wantLock) {
        if (target == null || target.seatIndex <= 0) return false;
        if (wantLock) {
            if (target.userId != null && !target.userId.isEmpty()) return false;
            return !target.locked();
        }
        return target.locked();
    }

    private void bindHostCardRankBadges(@Nullable AuthDtos.UserDto host) {
        if (binding == null) return;
        int vip = host != null ? Math.max(0, host.vipLevel) : 0;
        String vipBadgeUrl = host != null ? host.vipBadgeUrl : null;

        // Do not duplicate the host's numeric level over the room header avatar.
        if (binding.imgHostLevelBadge != null) {
            binding.imgHostLevelBadge.setVisibility(View.GONE);
            binding.imgHostLevelBadge.setImageDrawable(null);
        }
        if (binding.tvHostLevelChip != null) {
            binding.tvHostLevelChip.setVisibility(View.GONE);
            binding.tvHostLevelChip.setCompoundDrawablesRelative(null, null, null, null);
        }
        if (binding.imgHostVipBadge != null) {
            if (vip > 0 && vipBadgeUrl != null && !vipBadgeUrl.isEmpty()) {
                binding.imgHostVipBadge.setVisibility(View.VISIBLE);
                com.Dramizo.Series.util.ServerAssets.load(binding.imgHostVipBadge, vipBadgeUrl);
            } else {
                binding.imgHostVipBadge.setVisibility(View.GONE);
                binding.imgHostVipBadge.setImageDrawable(null);
            }
        }
        if (binding.tvHostVipChip != null) {
            if (vip > 0) {
                binding.tvHostVipChip.setVisibility(View.VISIBLE);
                binding.tvHostVipChip.setText("VIP" + vip);
                VipStyle.applyChip(binding.tvHostVipChip, vip);
                binding.tvHostVipChip.setTextColor(VipStyle.chipTextColor(vip));
            } else {
                binding.tvHostVipChip.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Mikoo boss mic = header avatar (seat 0). Room title once; mute chip under ID.
     * Guest mics only in the grid — hostStage stays gone (was overlapping seats).
     */
    /**
     * Mikoo boss card = fixed room-host identity (not a leaveable mic).
     * Guest mics only in the grid — host may sit/leave those; card always shows host.
     */
    private void bindHeaderHostVisual(@Nullable RoomDtos.RoomDto room,
                                      @Nullable List<RoomDtos.SeatDto> seats) {
        if (binding == null || room == null) return;
        if (binding.hostStage != null) {
            binding.hostStage.setVisibility(View.GONE);
        }
        if (binding.tvHostName != null) {
            binding.tvHostName.setVisibility(View.GONE);
        }
        if (binding.imgRoomHostAvatar == null) return;

        // Agency room header = agency brand only (logo + Lv under image). Never host heart/badge.
        if (RoomUiHelper.isAgencyRoom(room) && room.agencyId != null && !room.agencyId.isEmpty()) {
            String logo = room.agencyLogoUrl != null ? room.agencyLogoUrl.trim() : "";
            String cover = room.coverUrl != null ? room.coverUrl.trim() : "";
            int tier = room.agencyLevel > 0
                    ? room.agencyLevel
                    : com.Dramizo.Series.util.AgencyUi.bannerTierFromDiamonds(room.agencyTotalDiamonds);
            String visualKey = "agency|" + room.agencyId + "|" + logo + "|" + cover + "|" + tier;
            if (!Objects.equals(lastBoundHostStageKey, visualKey)) {
                lastBoundHostStageKey = visualKey;
                if (binding.imgRoomCover != null) binding.imgRoomCover.setVisibility(View.GONE);
                binding.imgRoomHostAvatar.setVisibility(View.VISIBLE);
                if (binding.imgRoomHostFrame != null) {
                    binding.imgRoomHostFrame.setVisibility(View.GONE);
                    binding.imgRoomHostFrame.setImageDrawable(null);
                }
                if (binding.imgRoomHostBadge != null) {
                    binding.imgRoomHostBadge.setVisibility(View.GONE);
                    binding.imgRoomHostBadge.setImageDrawable(null);
                }
                if (binding.headerHostSignal != null) {
                    binding.headerHostSignal.clearSignal();
                    binding.headerHostSignal.setVisibility(View.GONE);
                }
                com.Dramizo.Series.util.AgencyUi.bindLogo(
                        binding.imgRoomHostAvatar,
                        logo.isEmpty() ? null : logo,
                        cover.isEmpty() ? null : cover);
                if (binding.tvHeaderAgencyLevel != null) {
                    binding.tvHeaderAgencyLevel.setVisibility(View.VISIBLE);
                    binding.tvHeaderAgencyLevel.setText("Lv." + Math.max(1, tier));
                }
            }
            AuthDtos.UserDto host = room.host;
            hostStageUserId = room.hostId != null ? room.hostId
                    : (host != null ? host.id : null);
            updateHeaderMicMuted(seats);
            return;
        }

        if (binding.tvHeaderAgencyLevel != null) {
            binding.tvHeaderAgencyLevel.setVisibility(View.GONE);
        }

        AuthDtos.UserDto host = room.host;
        String hostId = room.hostId != null ? room.hostId
                : (host != null ? host.id : null);
        // Card always shows room host — never an empty chair when host profile exists.
        if (hostId == null || hostId.isEmpty()) {
            hostStageUserId = null;
            if (!Objects.equals(lastBoundHostStageKey, "empty")) {
                lastBoundHostStageKey = "empty";
                binding.imgRoomHostAvatar.setVisibility(View.GONE);
                if (binding.imgRoomCover != null) {
                    binding.imgRoomCover.setImageResource(R.drawable.icon_classic_seat_normal);
                    binding.imgRoomCover.setVisibility(View.VISIBLE);
                }
                if (binding.imgRoomHostFrame != null) binding.imgRoomHostFrame.setVisibility(View.GONE);
                if (binding.imgRoomHostBadge != null) binding.imgRoomHostBadge.setVisibility(View.GONE);
                if (binding.headerHostSignal != null) {
                    binding.headerHostSignal.clearSignal();
                    binding.headerHostSignal.setVisibility(View.GONE);
                }
            }
            updateHeaderMicMuted(null);
            return;
        }

        hostStageUserId = hostId;
        AuthDtos.UserDto sessionUser = null;
        try {
            sessionUser = ContainerProvider.from(this).getSessionManager().getUser();
        } catch (Exception ignored) {
        }
        boolean selfHost = myUserId != null && sameUser(myUserId, hostId);
        if (host == null && selfHost) {
            host = sessionUser;
        }

        // Room face = permanent room cover (separate from host profile avatar).
        String roomFace = room.coverUrl != null ? room.coverUrl.trim() : "";
        if (roomFace.isEmpty()) {
            roomFace = host != null && host.avatarUrl != null ? host.avatarUrl : null;
            if ((roomFace == null || roomFace.isEmpty()) && selfHost && sessionUser != null) {
                roomFace = sessionUser.avatarUrl;
            }
        }
        String vipUrl = host != null ? host.vipBadgeUrl : null;
        String hostBadgeUrl = host != null ? host.hostBadgeUrl : null;
        java.util.Map<String, Object> hostMeta = host != null ? host.hostBadgeMeta : null;
        // Session backfill: join payload often omits equipped wear on host DTO.
        if (selfHost && sessionUser != null) {
            if (vipUrl == null || vipUrl.isEmpty()) vipUrl = sessionUser.vipBadgeUrl;
            if (hostBadgeUrl == null || hostBadgeUrl.isEmpty()) {
                hostBadgeUrl = sessionUser.hostBadgeUrl;
                if (hostMeta == null) hostMeta = sessionUser.hostBadgeMeta;
            }
            if ((hostBadgeUrl == null || hostBadgeUrl.isEmpty())) {
                try {
                    hostBadgeUrl = ContainerProvider.from(this).getSessionManager().getHostBadgeUrl();
                } catch (Exception ignored) {
                }
            }
        }

        String visualKey = hostId + "|" + roomFace + "|" + vipUrl + "|" + hostBadgeUrl
                + "|" + hostMeta + "|" + isAgencyRoom;
        if (!Objects.equals(lastBoundHostStageKey, visualKey)) {
            lastBoundHostStageKey = visualKey;
            if (binding.imgRoomCover != null) binding.imgRoomCover.setVisibility(View.GONE);
            binding.imgRoomHostAvatar.setVisibility(View.VISIBLE);
            HostSignalView headerSignal = binding.headerHostSignal;
            AvatarCosmetics.bindRoomWear(
                    headerSignal,
                    binding.imgRoomHostAvatar,
                    binding.imgRoomHostFrame,
                    binding.imgRoomHostBadge,
                    roomFace,
                    vipUrl,
                    hostBadgeUrl,
                    hostMeta,
                    isAgencyRoom,
                    1);
        }
        if (binding.headerHostSignal != null
                && binding.headerHostSignal.getVisibility() == View.VISIBLE) {
            binding.headerHostSignal.resumeMotion();
        }
        // Header mute chip follows room owner only (not whoever sits on seat 0).
        updateHeaderMicMuted(seats);
    }

    /** Apply seat grid now; only post when RecyclerView is mid-layout. */
    private void applySeatGridUi(@NonNull List<RoomDtos.SeatDto> gridSeats,
                                 @Nullable List<RoomDtos.SeatDto> allSeats,
                                 @NonNull RoomDtos.RoomDto room) {
        Runnable bind = () -> {
            if (binding == null || isFinishing()) return;
            try {
                applyMikooSeatGrid(gridSeats.size());
                if (seatAdapter != null) seatAdapter.submit(gridSeats);
                RoomDtos.RoomDto latest = viewModel != null ? viewModel.getRoom().getValue() : null;
                bindHostStage(findHostSeat(allSeats), latest != null ? latest : room);
            } catch (Exception e) {
                android.util.Log.e("VoiceRoom", "seat grid bind failed", e);
            }
        };
        if (binding.recyclerSeats != null && binding.recyclerSeats.isComputingLayout()) {
            binding.recyclerSeats.post(bind);
        } else {
            bind.run();
        }
    }

    /** Instant local sit so wear shows before takeSeat API returns. */
    private void optimisticTakeSeat(int seatIndex) {
        refreshSelfHostWearOnSeats();
        AuthDtos.UserDto me = null;
        try {
            me = ContainerProvider.from(this).getSessionManager().getUser();
        } catch (Exception ignored) {
        }
        if (me == null || myUserId == null || myUserId.isEmpty()) return;
        // Ensure wear fields present on optimistic user.
        if ((me.vipBadgeUrl == null || me.vipBadgeUrl.isEmpty())
                || (me.hostBadgeUrl == null || me.hostBadgeUrl.isEmpty())) {
            // keep as-is; SeatAdapter self-backfill covers empty URLs
        }
        // Snapshot BEFORE mutating currentSeats — room observer uses wasOnSeat from currentSeats
        // and would otherwise skip the open-mic path after optimistic sit.
        boolean wasSeatedBefore = isOnSeat(currentSeats);
        List<RoomDtos.SeatDto> src = currentSeats != null ? currentSeats : new ArrayList<>();
        List<RoomDtos.SeatDto> next = new ArrayList<>(src.size());
        boolean placed = false;
        for (RoomDtos.SeatDto s : src) {
            if (s == null) continue;
            RoomDtos.SeatDto copy = shallowCopySeat(s);
            if (sameUser(myUserId, seatUserId(copy))) {
                copy.userId = null;
                copy.user = null;
                copy.status = copy.locked() ? "locked" : "empty";
            }
            if (copy.seatIndex == seatIndex) {
                copy.userId = myUserId;
                copy.user = me;
                copy.status = "occupied";
                copy.isLocked = false;
                // Don't inherit previous occupant's mute badge.
                copy.isMuted = false;
                copy.isModeratorMuted = false;
                placed = true;
            }
            next.add(copy);
        }
        if (!placed) {
            RoomDtos.SeatDto neu = new RoomDtos.SeatDto();
            neu.seatIndex = seatIndex;
            neu.userId = myUserId;
            neu.user = me;
            neu.status = "occupied";
            neu.isMuted = false;
            neu.isModeratorMuted = false;
            next.add(neu);
        }
        currentSeats = next;
        // Open mic immediately on first sit (room LiveData arrives later with wasOnSeat=true).
        if (!wasSeatedBefore && !userChoseMute) {
            micOn = true;
            try {
                RoomRtcEngine.getInstance().setMicEnabled(true);
            } catch (Exception ignored) {
            }
            if (roomId != null) viewModel.setMic(roomId, false);
            syncMicUi();
        }
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        if (binding != null && room != null) {
            applySeatGridUi(prepareGuestSeatsForGrid(next), next, room);
            bindHeaderHostVisual(room, next);
        }
    }

    private static RoomDtos.SeatDto shallowCopySeat(RoomDtos.SeatDto s) {
        RoomDtos.SeatDto c = new RoomDtos.SeatDto();
        c.id = s.id;
        c.seatIndex = s.seatIndex;
        c.userId = s.userId;
        c.isMuted = s.isMuted;
        c.isModeratorMuted = s.isModeratorMuted;
        c.isLocked = s.isLocked;
        c.status = s.status;
        c.user = s.user;
        return c;
    }

    private void prefetchSelfWear() {
        try {
            AuthDtos.UserDto me = ContainerProvider.from(this).getSessionManager().getUser();
            if (me == null) return;
            HostSignalView.prefetchWear(this, me.vipBadgeUrl);
            HostSignalView.prefetchWear(this, me.hostBadgeUrl);
            if (me.avatarUrl != null && !me.avatarUrl.isEmpty()) {
                Glide.with(getApplicationContext())
                        .load(AssetCatalog.absoluteUrl(me.avatarUrl))
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .preload(128, 128);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * "صوت مكتوم" under room ID = the room host muted their own mic,
     * wherever they sit (seat 0 / 1 / 2 / …). Guest self-mute never shows this.
     */
    private void updateHeaderMicMuted(@Nullable List<RoomDtos.SeatDto> seats) {
        if (binding == null) return;
        ImageView mute = binding.getRoot().findViewById(R.id.imgHeaderMicMuted);

        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        // Strict host identity only — never fall back to "whoever is on seat 0".
        String hostPersonId = null;
        if (room != null) {
            if (room.activeHostId != null && !room.activeHostId.isEmpty()) {
                hostPersonId = room.activeHostId;
            } else if (room.hostId != null && !room.hostId.isEmpty()) {
                hostPersonId = room.hostId;
            } else if (room.host != null && room.host.id != null && !room.host.id.isEmpty()) {
                hostPersonId = room.host.id;
            }
        }

        boolean showHostMutedChip = false;
        if (hostPersonId != null && seats != null) {
            RoomDtos.SeatDto hostSeat = findSeatForUser(seats, hostPersonId);
            if (hostSeat != null) {
                boolean iAmThatHost = myUserId != null && sameUser(myUserId, hostPersonId);
                if (iAmThatHost) {
                    // Host's own mic button (local), or moderator-forced mute.
                    showHostMutedChip = !micOn || hostSeat.isModeratorMuted;
                } else {
                    // Everyone else: only the host seat's server mute flags.
                    showHostMutedChip = hostSeat.isMuted || hostSeat.isModeratorMuted;
                }
            }
        }

        if (mute != null) {
            mute.setImageResource(R.drawable.ic_asset_mic_close);
            mute.setVisibility(showHostMutedChip ? View.VISIBLE : View.GONE);
        }
        if (binding.tvRoomMutedChip != null) {
            binding.tvRoomMutedChip.setText("صوت مكتوم");
            binding.tvRoomMutedChip.setVisibility(showHostMutedChip ? View.VISIBLE : View.GONE);
        }
    }

    @Nullable
    private RoomDtos.SeatDto findSeatForUser(@Nullable List<RoomDtos.SeatDto> seats,
                                             @Nullable String userId) {
        if (userId == null || userId.isEmpty() || seats == null) return null;
        for (RoomDtos.SeatDto seat : seats) {
            if (seat != null && sameUser(userId, seatUserId(seat))) return seat;
        }
        return null;
    }

    private void promptRenameRoomTitle(@Nullable RoomDtos.RoomDto room) {
        if (room == null || roomId == null || viewModel == null) return;
        if (!(isHost || isOwner || canManageRoom)) return;
        String current = room.title != null ? room.title : "";
        if (current.startsWith("وكالة · ")) current = current.substring("وكالة · ".length()).trim();
        if (current.startsWith("وكالة ")) current = current.substring("وكالة ".length()).trim();
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(current);
        final boolean agency = RoomUiHelper.isAgencyRoom(room);
        input.setHint(agency ? "اسم الوكالة" : "اسم الروم (دائم)");
        input.setSelection(input.getText() != null ? input.getText().length() : 0);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(agency ? "اسم الوكالة" : "اسم الروم")
                .setMessage(agency
                        ? "يُحدَّث اسم الوكالة للجميع ويظهر في الروم كـ «وكالة · الاسم»."
                        : "اسم الروم دائم ومستقل عن اسمك الشخصي — يظهر للجميع.")
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String next = input.getText() != null ? input.getText().toString().trim() : "";
                    if (next.isEmpty()) {
                        Toast.makeText(this, "الاسم مطلوب", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (next.startsWith("وكالة · ")) next = next.substring("وكالة · ".length()).trim();
                    if (next.startsWith("وكالة ")) next = next.substring("وكالة ".length()).trim();
                    if (!agency && com.Dramizo.Series.util.ChatContentFilter.containsAgencyImpersonation(next)) {
                        Toast.makeText(this,
                                com.Dramizo.Series.util.ChatContentFilter.AGENCY_WORD_REASON,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (agency && room.agencyId != null && !room.agencyId.isEmpty()) {
                        final String agencyId = room.agencyId;
                        final String name = next;
                        AppContainer c = ContainerProvider.from(this);
                        c.getIoExecutor().execute(() -> {
                            java.util.Map<String, String> body = new java.util.HashMap<>();
                            body.put("name", name);
                            Result<Object> r = ApiCall.execute(
                                    c.getAgencyApi().updateSettings(agencyId, body));
                            runOnUiThread(() -> {
                                if (r.success) {
                                    Toast.makeText(this, "تم تحديث اسم الوكالة", Toast.LENGTH_SHORT).show();
                                    viewModel.refresh(roomId);
                                } else {
                                    Toast.makeText(this,
                                            r.error != null ? r.error : getString(R.string.error_generic),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        });
                    } else {
                        viewModel.updateTitle(roomId, next);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void uploadRoomCover(@NonNull android.net.Uri uri) {
        if (roomId == null || roomId.isEmpty() || viewModel == null) return;
        if (!canManageRoom) {
            Toast.makeText(this, R.string.host_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                okhttp3.MultipartBody.Part part = AvatarImageLoader.multipartFromUri(
                        getContentResolver(), uri, "cover");
                retrofit2.Response<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>> resp =
                        c.getUploadApi().upload(part).execute();
                if (!resp.isSuccessful() || resp.body() == null || !resp.body().success
                        || resp.body().data == null || resp.body().data.url == null
                        || resp.body().data.url.isEmpty()) {
                    throw new IllegalStateException(getString(R.string.room_photo_upload_failed));
                }
                String url = AssetCatalog.absoluteUrl(resp.body().data.url);
                runOnUiThread(() -> {
                    if (isFinishing() || viewModel == null) return;
                    viewModel.updateCover(roomId, url);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    Toast.makeText(this,
                            e.getMessage() != null ? e.getMessage() : getString(R.string.room_photo_upload_failed),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onHostStageClick() {
        if (roomId == null || binding == null) return;
        RoomDtos.RoomDto room = viewModel != null ? viewModel.getRoom().getValue() : null;
        // Agency room header card → Mikoo family info (name / GID / stats / follow).
        if (isAgencyRoom && room != null && room.agencyId != null && !room.agencyId.isEmpty()) {
            com.Dramizo.Series.presentation.common.AgencyFamilyInfoSheet.show(
                    this,
                    room.agencyId,
                    room.agencyLogoUrl,
                    room.coverUrl);
            return;
        }
        AuthDtos.UserDto host = room != null ? room.host : null;
        String hostId = room != null && room.hostId != null ? room.hostId
                : (host != null ? host.id : roomHostId);
        if (hostId == null || hostId.isEmpty()) return;

        // Fixed boss card: profile only — never take/leave seat 0 from the card.
        String name = host != null
                ? firstNonEmpty(host.displayName, host.username, "مضيف الغرفة")
                : (binding.tvRoomTitle != null && binding.tvRoomTitle.getText() != null
                ? String.valueOf(binding.tvRoomTitle.getText()) : "مضيف الغرفة");
        showUserCard(
                hostId,
                name,
                host != null ? host.avatarUrl : null,
                host != null ? host.vipBadgeUrl : null,
                isAgencyRoom ? (host != null ? host.hostBadgeUrl : null) : null,
                host != null ? Math.max(0, host.vipLevel) : 0,
                host != null ? Math.max(1, host.level) : 1);
    }

    private void bindHostStage(@Nullable RoomDtos.SeatDto hostSeat, @Nullable RoomDtos.RoomDto room) {
        // Mikoo: boss mic is header avatar only — never a floating stage over the grid.
        if (binding != null && binding.hostStage != null) {
            binding.hostStage.setVisibility(View.GONE);
        }
        if (room != null) {
            bindHeaderHostVisual(room, currentSeats);
        }
        updateHostMuteBadge();
    }

    /** Ensure we play audio for every seated remote user — and stop streams from the previous room. */
    private void ensurePlayingSeatedAudio(List<RoomDtos.SeatDto> seats) {
        if (!RoomRtcEngine.getInstance().isReady()) return;
        try {
            java.util.HashSet<String> want = new java.util.HashSet<>();
            if (seats != null) {
                for (RoomDtos.SeatDto seat : seats) {
                    String uid = seatUserId(seat);
                    if (uid == null || uid.isEmpty()) continue;
                    if (myUserId != null && sameUser(myUserId, uid)) continue;
                    String streamId = RoomRtcEngine.audioStreamId(uid);
                    if (streamId != null) want.add(streamId);
                }
            }
            // Prune leftovers from the previous room / seats that left.
            for (String playing : new java.util.HashSet<>(
                    RoomRtcEngine.getInstance().getPlayingStreamIds())) {
                if (!want.contains(playing)) {
                    RoomRtcEngine.getInstance().stopPlaying(playing);
                }
            }
            for (String streamId : want) {
                playRemoteAudio(streamId);
            }
        } catch (Exception e) {
            android.util.Log.e("VoiceRoom", "ensurePlayingSeatedAudio failed", e);
        }
    }

    private void showSeatRequestsDialog() {
        if (roomId == null) return;
        if (!canInviteMic) {
            Toast.makeText(this, R.string.host_mod_only_mic_requests, Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_seat_requests, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);

        TextView title = sheet.findViewById(R.id.tvSeatRequestsTitle);
        TextView empty = sheet.findViewById(R.id.tvSeatRequestsEmpty);
        ProgressBar progress = sheet.findViewById(R.id.progressSeatRequests);
        androidx.recyclerview.widget.RecyclerView recycler = sheet.findViewById(R.id.recyclerSeatRequests);
        View btnSettings = sheet.findViewById(R.id.btnSeatRequestsSettings);
        View btnClose = sheet.findViewById(R.id.btnSeatRequestsClose);

        final java.util.ArrayList<RoomDtos.SeatRequestDto> rows = new java.util.ArrayList<>();
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        final androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>[] adapterRef =
                new androidx.recyclerview.widget.RecyclerView.Adapter[1];
        adapterRef[0] = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_seat_request, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(
                    @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                RoomDtos.SeatRequestDto req = rows.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvRequestName);
                TextView tvMeta = holder.itemView.findViewById(R.id.tvRequestMeta);
                TextView btnApprove = holder.itemView.findViewById(R.id.btnRequestApprove);
                TextView btnReject = holder.itemView.findViewById(R.id.btnRequestReject);
                String name = req.displayName != null && !req.displayName.isEmpty()
                        ? req.displayName : "ضيف";
                tvName.setText(name);
                tvMeta.setText(req.seatIndex != null
                        ? ("يريد مقعد " + Math.max(1, req.seatIndex))
                        : "طلب انضمام للمايك");
                btnApprove.setOnClickListener(v -> {
                    viewModel.approveSeat(roomId, req.userId, req.seatIndex);
                    int pos = holder.getBindingAdapterPosition();
                    if (pos >= 0 && pos < rows.size()) {
                        rows.remove(pos);
                        adapterRef[0].notifyItemRemoved(pos);
                        title.setText("طلبات المايك · " + rows.size());
                        if (rows.isEmpty()) {
                            empty.setVisibility(View.VISIBLE);
                            recycler.setVisibility(View.GONE);
                            empty.setText("لا توجد طلبات حالياً");
                        }
                    }
                });
                btnReject.setOnClickListener(v -> {
                    viewModel.rejectSeat(roomId, req.userId);
                    int pos = holder.getBindingAdapterPosition();
                    if (pos >= 0 && pos < rows.size()) {
                        rows.remove(pos);
                        adapterRef[0].notifyItemRemoved(pos);
                        title.setText("طلبات المايك · " + rows.size());
                        if (rows.isEmpty()) {
                            empty.setVisibility(View.VISIBLE);
                            recycler.setVisibility(View.GONE);
                            empty.setText("لا توجد طلبات حالياً");
                        }
                    }
                });
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }
        };
        recycler.setAdapter(adapterRef[0]);

        Runnable bindList = () -> {
            if (isFinishing() || isDestroyed()) return;
            List<RoomDtos.SeatRequestDto> list = viewModel.getSeatRequests().getValue();
            rows.clear();
            if (list != null) rows.addAll(list);
            progress.setVisibility(View.GONE);
            title.setText("طلبات المايك · " + rows.size());
            if (rows.isEmpty()) {
                empty.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
                empty.setText("لا توجد طلبات حالياً\nعند طلب ضيف للمقعد سيظهر هنا");
            } else {
                empty.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
                adapterRef[0].notifyDataSetChanged();
            }
        };

        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        viewModel.loadSeatRequests(roomId);
        List<RoomDtos.SeatRequestDto> cached = viewModel.getSeatRequests().getValue();
        if (cached != null && !cached.isEmpty()) {
            bindList.run();
        } else {
            handler.postDelayed(bindList, 650);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        boolean canOpenSettings = canModerateRoom();
        btnSettings.setVisibility(canOpenSettings ? View.VISIBLE : View.GONE);
        btnSettings.setOnClickListener(v -> {
            dialog.dismiss();
            if (canOpenSettings) showHostTools();
        });
        dialog.show();
    }

    private void showRoomGames() {
        if (roomId == null || roomId.isEmpty()) return;
        // Browsing and joining are available to everyone in the room. Individual
        // game controllers still enforce host/moderator-only actions where needed.
        showRoomGamesGrid();
    }

    private void showRoomHub() {
        BottomSheetDialog hubDialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_room_hub, null);
        AuraDialogHelper.applyContent(sheet);
        hubDialog.setContentView(sheet);
        android.widget.GridLayout hubGrid = sheet.findViewById(R.id.hubGrid);
        if (hubGrid != null) hubGrid.setColumnCount(responsivePanelColumns());

        sheet.findViewById(R.id.hubContest).setOnClickListener(v -> {
            hubDialog.dismiss();
            Intent intent = new Intent(this,
                    com.Dramizo.Series.presentation.contests.ContestsActivity.class);
            intent.putExtra(
                    com.Dramizo.Series.presentation.contests.ContestsActivity.EXTRA_ROOM_ID,
                    roomId);
            RoomDtos.RoomDto room = viewModel.getRoom().getValue();
            if (room != null && room.agencyId != null) {
                intent.putExtra(
                        com.Dramizo.Series.presentation.contests.ContestsActivity.EXTRA_AGENCY_ID,
                        room.agencyId);
            }
            startActivity(intent);
        });
        sheet.findViewById(R.id.hubXo).setVisibility(View.GONE);
        View hubWheel = sheet.findViewById(R.id.hubWheel);
        if (hubWheel != null) hubWheel.setVisibility(View.GONE);
        View hubDice = sheet.findViewById(R.id.hubDice);
        if (hubDice != null) hubDice.setVisibility(View.GONE);
        sheet.findViewById(R.id.hubGames).setOnClickListener(v -> {
            hubDialog.dismiss();
            showRoomGamesGrid();
        });
        sheet.findViewById(R.id.hubLeaderboard).setOnClickListener(v -> {
            hubDialog.dismiss();
            showRoomGameLeaderboard();
        });
        View hubTasks = sheet.findViewById(R.id.hubTasks);
        if (hubTasks != null) {
            if (!com.Dramizo.Series.util.TasksFeature.isEnabled(this)) {
                hubTasks.setVisibility(View.GONE);
                hubTasks.setOnClickListener(null);
            } else {
                hubTasks.setVisibility(View.VISIBLE);
                hubTasks.setOnClickListener(v -> {
                    hubDialog.dismiss();
                    Intent intent = new Intent(this,
                            com.Dramizo.Series.presentation.profile.TaskCenterActivity.class);
                    intent.putExtra(
                            com.Dramizo.Series.presentation.profile.TaskCenterActivity.EXTRA_ROOM_ID,
                            roomId);
                    RoomDtos.RoomDto room = viewModel.getRoom().getValue();
                    if (room != null && room.agencyId != null) {
                        intent.putExtra(
                                com.Dramizo.Series.presentation.profile.TaskCenterActivity.EXTRA_AGENCY_ID,
                                room.agencyId);
                    }
                    startActivity(intent);
                });
            }
        }
        sheet.findViewById(R.id.hubLuckyBox).setOnClickListener(v -> {
            hubDialog.dismiss();
            showLuckyFloatAgain();
            openLuckyBox();
        });

        View fund = sheet.findViewById(R.id.hubFund);
        RoomDtos.RoomDto currentRoom = viewModel.getRoom().getValue();
        String myId = ContainerProvider.from(this).getSessionManager().getUserId();
        boolean isHost = currentRoom != null && myId != null
                && (myId.equals(currentRoom.hostId) || myId.equals(currentRoom.activeHostId));
        fund.setVisibility(isHost ? View.VISIBLE : View.GONE);
        fund.setOnClickListener(v -> {
            hubDialog.dismiss();
            promptFundRoomLuckyBox();
        });

        View followRoom = sheet.findViewById(R.id.hubFollowRoom);
        TextView tvFollowRoom = sheet.findViewById(R.id.tvHubFollowRoom);
        if (isHost) {
            followRoom.setVisibility(View.GONE);
        } else {
            followRoom.setVisibility(View.VISIBLE);
            refreshRoomFollowHubLabel(tvFollowRoom);
            followRoom.setOnClickListener(v -> {
                hubDialog.dismiss();
                toggleFollowRoom();
            });
        }

        hubDialog.show();
    }

    private void refreshRoomFollowHubLabel(TextView label) {
        if (label == null || roomId == null) return;
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r =
                    ApiCall.execute(ContainerProvider.from(this).getRoomApi().roomFollowStatus(roomId));
            boolean following = r.success && r.data != null
                    && Boolean.TRUE.equals(r.data.get("following"));
            runOnUiThread(() -> label.setText(following
                    ? R.string.unfollow_room : R.string.follow_room));
        });
    }

    private void toggleFollowRoom() {
        if (roomId == null || roomId.isEmpty()) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> status =
                    ApiCall.execute(c.getRoomApi().roomFollowStatus(roomId));
            boolean following = status.success && status.data != null
                    && Boolean.TRUE.equals(status.data.get("following"));
            Result<Object> r = following
                    ? ApiCall.execute(c.getRoomApi().unfollowRoom(roomId))
                    : ApiCall.execute(c.getRoomApi().followRoom(roomId));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this,
                            following ? R.string.unfollow_room : R.string.follow_room,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void openLuckyBox() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.List<MiscDtos.LuckyBoxDto>> listResult =
                    ApiCall.execute(c.getLuckyBoxesApi().list());
            if (!listResult.success || listResult.data == null || listResult.data.isEmpty()) {
                runOnUiThread(() -> {
                    hideLuckyFloat();
                    Toast.makeText(this,
                            listResult.error != null ? listResult.error : "لا يوجد صندوق حظ متاح",
                            Toast.LENGTH_SHORT).show();
                });
                return;
            }
            MiscDtos.LuckyBoxDto free = null;
            for (MiscDtos.LuckyBoxDto box : listResult.data) {
                if (box != null && box.isActive
                        && "free_daily".equalsIgnoreCase(box.kind)
                        && box.remainingToday > 0) {
                    free = box;
                    break;
                }
            }
            if (free == null) {
                runOnUiThread(() -> {
                    hideLuckyFloat();
                    Toast.makeText(this, "أخذت صندوق الحظ مسبقاً", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            final MiscDtos.LuckyBoxDto target = free;
            runOnUiThread(() -> openLuckyBoxById(target));
        });
    }

    private void openLuckyBoxById(MiscDtos.LuckyBoxDto target) {
        if (target == null || target.id == null) return;
        if (binding != null && binding.luckyFloatWrap != null) {
            binding.luckyFloatWrap.setEnabled(false);
        }
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("roomId", roomId);
            RoomDtos.RoomDto room = viewModel.getRoom().getValue();
            if (room != null && room.agencyId != null) {
                body.put("agencyId", room.agencyId);
            }
            Result<MiscDtos.LuckyBoxRewardDto> openResult =
                    ApiCall.execute(c.getLuckyBoxesApi().open(target.id, body));
            runOnUiThread(() -> {
                if (binding != null && binding.luckyFloatWrap != null) {
                    binding.luckyFloatWrap.setEnabled(true);
                }
                if (openResult.success && openResult.data != null) {
                    MiscDtos.LuckyReward reward = openResult.data.reward;
                    String rewardLabel = reward != null
                            ? ("ربحت: " + reward.amount + " " + reward.type)
                            : "تم فتح الصندوق";
                    String me = ContainerProvider.from(this).getSessionManager().getDisplayName();
                    if (me == null || me.isEmpty()) me = "أنا";
                    showLuckyWinOverlay(me, rewardLabel);
                    hideLuckyFloat();
                    JsonObject payload = new JsonObject();
                    payload.addProperty("userId", myUserId != null ? myUserId : "");
                    payload.addProperty("displayName", me);
                    payload.addProperty("rewardLabel", rewardLabel);
                    if (reward != null) {
                        payload.addProperty("coins", reward.amount);
                        payload.addProperty("rewardType", reward.type != null ? reward.type : "");
                    }
                    if (roomId != null) {
                        RealtimeClient.getInstance().emitRoomEvent(roomId, "lucky:opened", payload);
                    }
                } else {
                    String err = openResult.error != null ? openResult.error : "حاول لاحقاً";
                    if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(err)) {
                        com.Dramizo.Series.util.BalanceRedirect.handle(this, err);
                    } else {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                    }
                    if (err.toLowerCase(java.util.Locale.US).contains("already")
                            || err.contains("مسبقا")
                            || err.contains("limit")
                            || err.contains("claimed")) {
                        hideLuckyFloat();
                    }
                }
            });
        });
    }

    private void promptFundRoomLuckyBox() {
        AuraDialogHelper.prompt(this,
                "تمويل صندوق حظ الروم",
                null,
                "المبلغ بالكوينز",
                android.text.InputType.TYPE_CLASS_NUMBER,
                "تمويل",
                valueStr -> {
                    long amount;
                    try {
                        amount = Long.parseLong(valueStr.trim());
                    } catch (Exception ignored) {
                        amount = 0;
                    }
                    if (amount <= 0) {
                        Toast.makeText(this, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final long value = amount;
                    AppContainer c = ContainerProvider.from(this);
                    c.getIoExecutor().execute(() -> {
                        java.util.Map<String, Long> body = new java.util.HashMap<>();
                        body.put("amount", value);
                        Result<java.util.Map<String, Object>> result =
                                ApiCall.execute(c.getLuckyBoxesApi().fundRoom(roomId, body));
                        runOnUiThread(() -> {
                            if (result.success) {
                                Toast.makeText(this, "تم تمويل صندوق الروم", Toast.LENGTH_LONG).show();
                            } else if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(result.error)) {
                                com.Dramizo.Series.util.BalanceRedirect.handle(this, result.error);
                            } else {
                                Toast.makeText(
                                        this,
                                        result.error != null ? result.error : "تعذر التمويل",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                });
    }

    private void showRoomGameLeaderboard() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> result =
                    ApiCall.execute(c.getCasualGameApi().roomLeaderboard(roomId));
            runOnUiThread(() -> {
                if (!result.success || result.data == null) {
                    Toast.makeText(
                            this,
                            result.error != null ? result.error : "تعذر تحميل الترتيب",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Object rawItems = result.data.get("items");
                StringBuilder text = new StringBuilder();
                if (rawItems instanceof java.util.List) {
                    int rank = 1;
                    for (Object raw : (java.util.List<?>) rawItems) {
                        if (!(raw instanceof java.util.Map) || rank > 10) continue;
                        java.util.Map<?, ?> item = (java.util.Map<?, ?>) raw;
                        text.append(rank++)
                                .append(". ")
                                .append(String.valueOf(item.get("displayName")))
                                .append(" · ")
                                .append(String.valueOf(item.get("wins")))
                                .append(" فوز\n");
                    }
                }
                if (text.length() == 0) text.append("لا توجد نتائج بعد");
                AuraDialogHelper.message(this, "ترتيب ألعاب الروم", text.toString());
            });
        });
    }

    private void showRoomGamesGrid() {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        DialogRoomGamesBinding db = DialogRoomGamesBinding.inflate(getLayoutInflater());
        AuraDialogHelper.applyContent(db.getRoot());
        dialog.setContentView(db.getRoot());

        RoomGamesAdapter adapter = new RoomGamesAdapter(game -> {
            openMatchedOrLocalGame(game);
            dialog.dismiss();
        });

        // Mikoo dialog_more: 4-column vertical grid inside 485dp sheet.
        db.recyclerGames.setLayoutManager(new GridLayoutManager(this, 4));
        db.recyclerGames.setAdapter(adapter);
        List<MiscDtos.GameDto> initial = buildRoomGamesCatalog(null);
        adapter.submit(initial);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
        });
        dialog.show();

        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<List<MiscDtos.GameDto>> r =
                    ApiCall.execute(ContainerProvider.from(this).getConfigApi().games());
            if (isFinishing()) return;
            List<MiscDtos.GameDto> api = (r.success && r.data != null) ? r.data : null;
            List<MiscDtos.GameDto> merged = buildRoomGamesCatalog(api);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (merged.isEmpty()) {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                } else {
                    adapter.submit(merged);
                }
            });
        });
    }

    /**
     * Same source as {@link com.Dramizo.Series.presentation.games.GamesFragment}:
     * ConfigApi.games() + MikooGamesCatalog.mergeSlots.
     */
    private List<MiscDtos.GameDto> buildRoomGamesCatalog(
            @Nullable List<MiscDtos.GameDto> apiList) {
        return MikooGamesCatalog.mergeSlots(apiList);
    }

    private String serverHtmlGamePath(String fileName) {
        return com.Dramizo.Series.util.ApiOrigin.origin() + "/games/" + fileName;
    }

    private void openMatchedOrLocalGame(MiscDtos.GameDto game) {
        if (game == null) return;
        String title = MikooGamesCatalog.displayTitle(this, game);
        String play = game.playUrl != null ? game.playUrl : "";
        String url = GameUrls.resolve(this, play);
        String mode = game.mode;
        String gameId = game.id != null && !game.id.isEmpty()
                ? game.id
                : MikooGameBridge.extractGameId(url);
        if (mode == null || mode.isEmpty()) {
            mode = MikooGameBridge.isMikooSlot(null, url) ? "mikoo_slot" : null;
        }
        final String openUrl = url;
        final String openTitle = title;
        final String openGameId = gameId;
        final String cover = game.coverUrl;
        final boolean mikoo = MikooGameBridge.isMikooSlot(mode, url) || MikooGamesCatalog.isMikooEntry(game);
        GameAdsHelper ads = GameAdsHelper.get(this);
        ads.refreshConfig(this);
        ads.maybeShowOnOpen(this, () -> {
            if (isDiceOrWheelUrl(openUrl)) {
                Toast.makeText(this, "هذه اللعبة لم تعد متاحة", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mikoo) {
                openMikooGameInRoom(openUrl, openTitle, openGameId);
            } else {
                openGameOverlay(openUrl, openTitle, cover);
            }
        });
    }

    private static boolean isDiceOrWheelUrl(@Nullable String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("lucky-wheel") || lower.contains("/games/dice.html")
                || lower.contains("dice.html")
                || lower.contains("tic_tac") || lower.contains("tictactoe");
    }

    private String resolveRoomHtmlGameUrl(String url) {
        // Legacy HTML casual games removed — never rewrite/open them.
        if (isDiceOrWheelUrl(url)) return null;
        return url;
    }

    private void openMikooGameInRoom(String rawUrl, String title, @Nullable String gameId) {
        if (binding == null || roomId == null || roomId.isEmpty()) return;
        endOverlaySlotSession();

        overlayPendingUrl = rawUrl;
        overlayGameId = (gameId != null && !gameId.isEmpty())
                ? gameId
                : MikooGameBridge.extractGameId(rawUrl);
        overlayGameReady = false;

        if (binding.btnCloseGameOverlay != null) {
            binding.btnCloseGameOverlay.setVisibility(View.GONE);
        }
        showRoomGameLoading(false);
        setRoomChromeHidden(false); // Mikoo: seats/chat stay visible under half-scene H5
        layoutRoomGamePanel(true);
        configureRoomGameWebView(true);
        ensureRoomGameWebView().setRotation(0f);
        ensureRoomGameWebView().setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    GameProbeLog.js(String.valueOf(consoleMessage.messageLevel()),
                            consoleMessage.message()
                                    + " @" + consoleMessage.sourceId()
                                    + ":" + consoleMessage.lineNumber());
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
        ensureRoomGameWebView().setWebViewClient(createMikooRoomWebViewClient());
        GameProbeLog.i("OPEN.mikoo", "title=" + title + " gameId=" + overlayGameId + " url=" + rawUrl);
        binding.gameOverlay.setVisibility(View.VISIBLE);
        binding.gameOverlay.bringToFront();
        if (binding.luckyFloatWrap != null) {
            binding.luckyFloatWrap.setVisibility(View.GONE);
        }

        SlotGameDtos.StartRequest req = new SlotGameDtos.StartRequest();
        req.gameId = overlayGameId;
        req.roomId = roomId;
        SlotGameApi slotApi = ContainerProvider.from(this).getSlotGameApi();
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<SlotGameDtos.SessionDto> r = ApiCall.execute(slotApi.startSession(req));
            if (isFinishing() || binding == null) return;
            runOnUiThread(() -> {
                if (!r.success || r.data == null) {
                    showRoomGameLoading(false);
                    String err = r.error != null ? r.error : getString(R.string.error_generic);
                    GameProbeLog.e("SESSION.fail", err + " gameId=" + overlayGameId);
                    Toast.makeText(this, "تعذر بدء اللعبة: " + err, Toast.LENGTH_LONG).show();
                    closeGameOverlay();
                    return;
                }
                slotSession = r.data;
                GameProbeLog.i("SESSION.ok",
                        "sid=" + slotSession.sessionId
                                + " game=" + slotSession.gameId
                                + " bridge=" + slotSession.bridge
                                + " bal=" + slotSession.balance
                                + " user=" + slotSession.userId
                                + " room=" + slotSession.roomId
                                + " code=" + slotSession.code
                                + " container=" + slotSession.containerUrl
                                + " route=" + slotSession.routeUrl);
                if (slotSession.balance <= 0) {
                    BalanceRedirect.handleForced(this, "رصيدك 0 — اشحن كوينز للعب");
                }
                hashBridge = new MikooHashBridge(slotSession, new MikooHashBridge.Callbacks() {
                    @Override
                    public void onClose() {
                        closeGameOverlay();
                    }

                    @Override
                    public void onRecharge() {
                        BalanceRedirect.handleForced(VoiceRoomActivity.this,
                                "رصيد غير كافٍ — اشحن كوينز");
                    }
                });
                // Hash games need androidJsObj; BaiShun needs NativeBridge. Attach both safely.
                MikooHashBridge.attach(ensureRoomGameWebView(), hashBridge);

                mikooBridge = new MikooGameBridge(ensureRoomGameWebView(), slotSession, new MikooGameBridge.Callbacks() {
                    @Override
                    public void onDestroy() {
                        closeGameOverlay();
                    }

                    @Override
                    public void onRecharge() {
                        BalanceRedirect.handleForced(VoiceRoomActivity.this,
                                "رصيد غير كافٍ — اشحن كوينز");
                    }

                    @Override
                    public void onLoaded() {
                        overlayGameReady = true;
                        showRoomGameLoading(false);
                        if (binding != null && roomGameWebView != null) {
                            scheduleRoomGameCanvasFit(ensureRoomGameWebView(), 0);
                            scheduleRoomGameCanvasFit(ensureRoomGameWebView(), 300);
                            scheduleRoomGameCanvasFit(ensureRoomGameWebView(), 900);
                            scheduleRoomGameCanvasFit(ensureRoomGameWebView(), 1800);
                        }
                    }
                });
                mikooBridge.attach();

                // Same dock for every mikoo game (not fishing-only).
                final boolean halfScene = true;
                setRoomChromeHidden(false);
                layoutRoomGamePanel(halfScene);

                String loadUrl = MikooGameBridge.appendSessionParams(overlayPendingUrl, slotSession);
                loadUrl = appendGameAuthParams(loadUrl);
                handler.post(heartbeatSlot);
                final String finalUrl = loadUrl;
                View panel = binding.gamePanel != null ? binding.gamePanel : ensureRoomGameWebView();
                panel.post(() -> {
                    if (isFinishing() || binding == null) return;
                    layoutRoomGamePanel(halfScene);
                    ensureRoomGameWebView().post(() -> {
                        if (isFinishing() || binding == null) return;
                        ensureRoomGameWebView().loadUrl(finalUrl);
                    });
                });
                handler.postDelayed(() -> {
                    if (!overlayGameReady && !isFinishing()) {
                        showRoomGameLoading(false);
                    }
                }, 2800);
            });
        });
    }

    /**
     * In-room games — Mikoo BSGameWebDialog layout for EVERY game:
     * full-width WebView from top → above bottomBar, transparent, LTR.
     * Half-UI comes from engine sceneMode=0 (not Android letterboxing).
     */
    private void layoutRoomGamePanel(boolean mikooHalfScene) {
        layoutRoomGamePanel(mikooHalfScene, false);
    }

    private void layoutRoomGamePanel(boolean mikooHalfScene, boolean ignoredFishingFlag) {
        if (binding == null || binding.gamePanel == null) return;
        int navBottom = navigationBarInsetPx();
        int bottomChrome = roomGameBottomChromePx(navBottom);

        // Mirror Mikoo dialog_bai_shun_webview: WebView fills the host area (full height).
        // Host keeps chat bottomBar free; seats stay visible via transparent + sceneMode 0.
        if (binding.gameOverlay != null) {
            ViewGroup.LayoutParams olp = binding.gameOverlay.getLayoutParams();
            if (olp instanceof ConstraintLayout.LayoutParams clp) {
                clp.width = 0;
                clp.height = 0;
                clp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                clp.topToBottom = ConstraintLayout.LayoutParams.UNSET;
                if (binding.bottomBar != null) {
                    clp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
                    clp.bottomToTop = binding.bottomBar.getId();
                    clp.setMargins(0, 0, 0, 0);
                } else {
                    clp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
                    clp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    clp.setMargins(0, 0, 0, bottomChrome);
                }
                clp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
                clp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
                clp.horizontalWeight = 0f;
                clp.verticalBias = 1f;
                binding.gameOverlay.setLayoutParams(clp);
            } else if (olp != null) {
                olp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                olp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                binding.gameOverlay.setLayoutParams(olp);
            }
            binding.gameOverlay.setPadding(0, 0, 0, 0);
            binding.gameOverlay.setBackgroundColor(Color.TRANSPARENT);
            binding.gameOverlay.setClickable(false);
            binding.gameOverlay.setFocusable(false);
            binding.gameOverlay.setClipChildren(false);
            binding.gameOverlay.setClipToPadding(false);
            if (binding.bottomBar != null) binding.bottomBar.bringToFront();
        }

        ViewGroup.LayoutParams plp = binding.gamePanel.getLayoutParams();
        if (plp instanceof FrameLayout.LayoutParams flp) {
            flp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.gravity = Gravity.BOTTOM;
            flp.setMargins(0, 0, 0, 0);
            binding.gamePanel.setLayoutParams(flp);
        } else if (plp != null) {
            plp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            plp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.gamePanel.setLayoutParams(plp);
        }
        binding.gamePanel.setBackgroundColor(Color.TRANSPARENT);
        binding.gamePanel.setClickable(true);
        binding.gamePanel.setFocusable(true);
        binding.gamePanel.setClipChildren(false);
        binding.gamePanel.setClipToPadding(false);
        forceRoomGameLtr(binding.gamePanel);
        forceRoomGameLtr(binding.gameOverlay);

        // No per-game letterbox — stage/WebView are fill_parent like Mikoo.
        View stage = binding.getRoot().findViewById(R.id.gameStage);
        if (stage != null) {
            stage.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            slp.gravity = Gravity.BOTTOM;
            slp.setMargins(0, 0, 0, 0);
            stage.setLayoutParams(slp);
            if (stage instanceof ViewGroup) {
                ((ViewGroup) stage).setClipChildren(false);
            }
            forceRoomGameLtr(stage);
        }
        if (roomGameWebView != null) {
            forceRoomGameLtr(roomGameWebView);
            roomGameWebView.setRotation(0f);
            roomGameWebView.setTranslationX(0f);
            roomGameWebView.setTranslationY(0f);
            roomGameWebView.setScaleX(1f);
            roomGameWebView.setScaleY(1f);
            roomGameWebView.setBackgroundColor(Color.TRANSPARENT);
            roomGameWebView.setPadding(0, 0, 0, 0);
            roomGameWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (binding.btnCloseGameOverlay != null) {
            binding.btnCloseGameOverlay.setVisibility(View.GONE);
        }
        showRoomGameLoading(false);
        setRoomChromeHidden(false);
    }

    /** Games/engines are designed LTR; RTL rooms must not mirror the WebView canvas. */
    private void forceRoomGameLtr(@Nullable View v) {
        if (v == null) return;
        try {
            ViewCompat.setLayoutDirection(v, ViewCompat.LAYOUT_DIRECTION_LTR);
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
            if (v instanceof TextView) {
                ((TextView) v).setTextDirection(View.TEXT_DIRECTION_LTR);
            } else {
                v.setTextDirection(View.TEXT_DIRECTION_LTR);
            }
        } catch (Exception ignored) {
        }
    }

    private int navigationBarInsetPx() {
        try {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.getRoot());
            if (insets != null) {
                return Math.max(0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
            }
        } catch (Exception ignored) {
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.view.WindowInsets wi = getWindow().getDecorView().getRootWindowInsets();
                if (wi != null) return Math.max(0, wi.getSystemWindowInsetBottom());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * Space reserved under the game dock: bottom send/gift/emoji bar (+ nav fallback).
     * Game bottom edge sits on top of this chrome — never floats mid-screen.
     */
    private int roomGameBottomChromePx(int navBottom) {
        float density = getResources().getDisplayMetrics().density;
        int bar = 0;
        if (binding != null && binding.bottomBar != null
                && binding.bottomBar.getVisibility() != View.GONE) {
            bar = binding.bottomBar.getHeight();
            if (bar <= 0) {
                try {
                    binding.bottomBar.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                    Math.max(1, binding.getRoot().getWidth()),
                                    View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    bar = binding.bottomBar.getMeasuredHeight();
                } catch (Exception ignored) {
                }
            }
            if (bar <= 0) bar = Math.round(56f * density);
            if (binding.bottomBar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams mlp) {
                bar += Math.max(0, mlp.bottomMargin);
            }
        }
        // If bar already sits at parent bottom above system gesture area, nav may be 0
        // in layout coords; keep a tiny pad when bar missing.
        if (bar > 0) return bar;
        return Math.max(0, navBottom);
    }

    private void showRoomGameLoading(boolean show) {
        // User request: never show «جاري التحميل» over room games.
        if (binding == null || binding.gameLoadingOverlay == null) return;
        binding.gameLoadingOverlay.setVisibility(View.GONE);
    }

    private void configureRoomGameWebView(boolean mikoo) {
        if (binding == null) return;
        WebView web = ensureRoomGameWebView();
        forceRoomGameLtr(web);
        if (binding.gamePanel != null) forceRoomGameLtr(binding.gamePanel);
        if (binding.gameOverlay != null) forceRoomGameLtr(binding.gameOverlay);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(!mikoo);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 JEHO CHAT/1.0");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(mikoo ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setPadding(0, 0, 0, 0);
    }

    private WebViewClient createMikooRoomWebViewClient() {
        return new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                GameProbeLog.net("PAGE.start", url);
                forceRoomGameLtr(view);
                injectRoomGameForceLocalHosts(view);
                injectGameProbeHooks(view);
                // Early LTR / no-rotate before Cocos can apply portrait spin.
                scheduleRoomGameCanvasFit(view, 0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                GameProbeLog.net("PAGE.finish", url);
                injectRoomGameForceLocalHosts(view);
                injectGameProbeHooks(view);
                // Stretch full game into mid→bottom dock (re-apply after Cocos boots).
                scheduleRoomGameCanvasFit(view, 0);
                scheduleRoomGameCanvasFit(view, 400);
                scheduleRoomGameCanvasFit(view, 1200);
                scheduleRoomGameCanvasFit(view, 2500);
                if (slotSession != null) {
                    injectRoomGameRechargeHook(view);
                    injectRoomGameBalanceSync(view);
                }
                if (!overlayGameReady) {
                    handler.postDelayed(() -> showRoomGameLoading(false), 2800);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String u = request.getUrl().toString();
                    String lower = u.toLowerCase(Locale.US);
                    if (lower.contains("/games") || lower.contains("ws") || lower.contains("get_addr")
                            || lower.contains("bet") || lower.contains("slot")
                            || lower.contains("baishun") || lower.contains("jieyou")
                            || lower.contains("sruner") || lower.contains("route")
                            || lower.contains("protobuf") || lower.contains("api")) {
                        GameProbeLog.net("HTTP." + request.getMethod(), u);
                    }
                }
                if (request != null && isExternalBaiShunHost(request.getUrl())) {
                    Uri u = request.getUrl();
                    String path = u != null && u.getPath() != null ? u.getPath() : "";
                    String origin = com.Dramizo.Series.util.ApiOrigin.origin();
                    String wsOrigin = origin.replaceFirst("^https://", "wss://")
                            .replaceFirst("^http://", "ws://");
                    String body;
                    if (path.contains("get_addr")) {
                        String slug = overlayGameId != null && !overlayGameId.isEmpty()
                                ? overlayGameId
                                : "cleopatra-slot";
                        body = "{\"code\":200,\"data\":{"
                                + "\"http_addr\":\"" + origin + "/games/route/\","
                                + "\"ws_addr\":\"" + wsOrigin + "/games/ws/" + slug + "\"}}";
                        GameProbeLog.i("INTERCEPT.get_addr", body);
                    } else {
                        body = "{\"code\":200,\"data\":true}";
                        GameProbeLog.i("INTERCEPT.baishun", path + " → ok stub");
                    }
                    return new WebResourceResponse(
                            "application/json",
                            "utf-8",
                            new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) {
                    GameProbeLog.w("PAGE.subError",
                            (request.getUrl() != null ? request.getUrl().toString() : "?")
                                    + " " + (error != null ? error.getDescription() : ""));
                    return;
                }
                GameProbeLog.e("PAGE.mainError",
                        (request != null && request.getUrl() != null ? request.getUrl().toString() : "?")
                                + " " + (error != null ? error.getDescription() : ""));
                showRoomGameLoading(false);
                Toast.makeText(VoiceRoomActivity.this, "تعذر فتح اللعبة", Toast.LENGTH_SHORT).show();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                GameProbeLog.e("PAGE.errorLegacy", errorCode + " " + description + " " + failingUrl);
                showRoomGameLoading(false);
                Toast.makeText(VoiceRoomActivity.this, "تعذر فتح اللعبة", Toast.LENGTH_SHORT).show();
            }
        };
    }

    /** Console / click probe only — never wrap WebSocket (breaks Cocos OPEN/CONNECTING). */
    private void injectGameProbeHooks(WebView view) {
        if (view == null) return;
        String js = "(function(){try{"
                + "if(window.__jehoGameProbe)return;window.__jehoGameProbe=1;"
                + "function P(m){try{if(window.JehoGameProbe&&JehoGameProbe.log)"
                + "JehoGameProbe.log(String(m).slice(0,1500));}catch(e){}}"
                + "['log','warn','error','info'].forEach(function(k){"
                + "var o=console[k];console[k]=function(){try{P('console.'+k+' '"
                + "+Array.prototype.slice.call(arguments).join(' '));}catch(e){}"
                + "return o&&o.apply(console,arguments);};});"
                + "document.addEventListener('click',function(ev){try{"
                + "var t=ev.target;var tag=t&&t.tagName||'?';"
                + "var id=t&&t.id||'';var cls=(t&&t.className)||'';"
                + "var txt=(t&&(t.innerText||t.textContent)||'').trim().slice(0,40);"
                + "P('CLICK '+tag+'#'+id+'.'+String(cls).slice(0,40)+' txt='+txt"
                + "+' @'+Math.round(ev.clientX)+','+Math.round(ev.clientY));"
                + "}catch(e){}},true);"
                + "P('probe.ready '+location.href);"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private static boolean isExternalBaiShunHost(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.endsWith("jieyou.shop")
                || h.endsWith("sruner.com")
                || h.endsWith("zkruner.com");
    }

    private void injectRoomGameForceLocalHosts(WebView view) {
        if (view == null) return;
        String origin = com.Dramizo.Series.util.ApiOrigin.origin();
        String wsOrigin = origin.replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        String safeNick = MikooGameBridge.safeDisplayName(
                slotSession != null ? slotSession.displayName : null, null);
        String uid = slotSession != null && slotSession.userId != null
                ? slotSession.userId.replace("'", "") : "";
        String nick = safeNick
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ");
        String avatar = slotSession != null && slotSession.avatarUrl != null
                ? slotSession.avatarUrl.replace("'", "") : "";
        long bal = slotSession != null ? Math.max(0L, slotSession.balance) : 0L;
        // Match GamePlayActivity: rewrite jieyou + preserve WebSocket state constants.
        String js = "(function(){try{"
                + "window.__jehoUid='" + uid + "';"
                + "window.__jehoNick='" + nick + "';"
                + "window.__jehoAvatar='" + avatar + "';"
                + "window.__jehoBal=" + bal + ";"
                + "if(window.__jehoForceLocal)return;window.__jehoForceLocal=1;"
                + "var LOCAL='" + origin + "',LOCAL_WS='" + wsOrigin + "';"
                + "var BAD=/(jieyou\\.shop|sruner\\.com|zkruner\\.com)/i;"
                + "function rewrite(u){if(u==null)return u;var s=String(u);if(!BAD.test(s))return s;"
                + "try{var a=document.createElement('a');a.href=s;"
                + "var path=(a.pathname||'/')+(a.search||'')+(a.hash||'');"
                + "return /^wss?:/i.test(s)?(LOCAL_WS+path):(LOCAL+path);"
                + "}catch(e){return s.replace(/^https?:\\/\\/[^/]+/i,LOCAL).replace(/^wss?:\\/\\/[^/]+/i,LOCAL_WS);}}"
                + "try{var q=new URLSearchParams(location.search||'');var dom=q.get('DOMAIN')||q.get('domain');"
                + "if(dom&&!BAD.test(dom)){LOCAL=String(dom).replace(/\\/$/,'');"
                + "if(/^https?:/i.test(LOCAL))LOCAL_WS=LOCAL.replace(/^https:/i,'wss:').replace(/^http:/i,'ws:');}}"
                + "catch(e){}"
                + "function fixUid(u){try{var s=String(u||'');"
                + "if(!window.__jehoUid)return s;"
                + "return s.replace(/([?&]user_id=)(undefined|null|NaN)(?=&|$)/ig,'$1'+window.__jehoUid)"
                + ".replace(/([?&]userId=)(undefined|null|NaN)(?=&|$)/ig,'$1'+window.__jehoUid);}"
                + "catch(e){return u;}}"
                + "var OW=window.WebSocket;if(OW&&!OW.__jehoHostFix){"
                + "function W(u,p){u=fixUid(rewrite(u));"
                + "return p!==undefined?new OW(u,p):new OW(u);}"
                + "W.prototype=OW.prototype;"
                + "W.CONNECTING=OW.CONNECTING;W.OPEN=OW.OPEN;"
                + "W.CLOSING=OW.CLOSING;W.CLOSED=OW.CLOSED;W.__jehoHostFix=1;window.WebSocket=W;}"
                + "if(window.fetch){var of=window.fetch.bind(window);window.fetch=function(i,n){"
                + "if(typeof i==='string')i=rewrite(i);"
                + "else if(i&&typeof Request!=='undefined'&&i instanceof Request)i=new Request(rewrite(i.url),i);"
                + "return of(i,n);};}"
                + "var XO=window.XMLHttpRequest;if(XO&&XO.prototype){var op=XO.prototype.open;"
                + "XO.prototype.open=function(){var a=Array.prototype.slice.call(arguments);"
                + "if(a.length>1)a[1]=rewrite(a[1]);return op.apply(this,a);};}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void scheduleRoomGameCanvasFit(@Nullable WebView view, long delayMs) {
        if (view == null || handler == null) return;
        handler.postDelayed(() -> {
            if (isFinishing() || binding == null || roomGameWebView == null) return;
            if (binding.gameOverlay == null
                    || binding.gameOverlay.getVisibility() != View.VISIBLE) return;
            // Same layout+fit for every room game (including fishing / cleopatra / crash…).
            layoutRoomGamePanel(true);
            injectRoomGameCanvasFit(ensureRoomGameWebView());
        }, Math.max(0L, delayMs));
    }

    /**
     * Light host hygiene only — Mikoo does NOT override Cocos design resolution.
     * Forcing SHOW_ALL / design sizes made portrait titles (Cleopatra…) tiny.
     */
    private void injectRoomGameCanvasFit(WebView view) {
        if (view == null) return;
        String js = "(function(){try{"
                + "try{document.documentElement.setAttribute('dir','ltr');"
                + "document.documentElement.dir='ltr';"
                + "document.documentElement.style.direction='ltr';"
                + "document.documentElement.style.background='transparent';"
                + "if(document.body){document.body.setAttribute('dir','ltr');"
                + "document.body.dir='ltr';document.body.style.direction='ltr';"
                + "document.body.style.background='transparent';"
                + "document.body.style.margin='0';document.body.style.padding='0';}"
                + "}catch(e0){}"
                + "try{if(window.cc&&cc.view){"
                + "if(cc.view.resizeWithBrowserSize)cc.view.resizeWithBrowserSize(true);"
                + "try{if(cc.view._resizeEvent)cc.view._resizeEvent();}catch(e2){}"
                + "try{window.dispatchEvent(new Event('resize'));}catch(e3){}"
                + "}}catch(e){}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void injectFishingRoomFit(WebView view) {
        // No fishing-only path — every game uses the same canvas fit.
        injectRoomGameCanvasFit(view);
    }

    private void injectRoomGameRechargeHook(WebView view) {
        if (view == null) return;
        // Must preserve WebSocket.OPEN/CONNECTING — omitting them sticks BaiShun on splash.
        String js = "(function(){try{"
                + "if(window.__jehoEcoHook)return;window.__jehoEcoHook=1;"
                + "function jehoOpenCharge(){try{"
                + "if(window.androidJsObj&&androidJsObj.openChargePage)androidJsObj.openChargePage();"
                + "else if(window.NativeBridge&&NativeBridge.gameRecharge)NativeBridge.gameRecharge('{}');"
                + "else if(window.AuraBridge&&AuraBridge.openWallet)AuraBridge.openWallet();"
                + "}catch(e){}}"
                + "function jehoNeed(t){if(!t)return false;t=String(t);"
                + "return t.indexOf('Insufficient')>=0||t.indexOf('NeedRecharge')>=0"
                + "||t.indexOf('need_recharge')>=0||t.indexOf('\"Code\":2')>=0||t.indexOf('\"Code\": 2')>=0"
                + "||t.indexOf('\"tipType\":2')>=0||t.indexOf('\"tipType\": 2')>=0"
                + "||t.indexOf('errCode\":5')>=0||t.indexOf('errCode\": 5')>=0;}"
                + "var OW=window.WebSocket;if(OW&&!OW.__jehoEcoWrap){"
                + "function W(u,p){var ws=p!==undefined?new OW(u,p):new OW(u);"
                + "ws.addEventListener('message',function(ev){try{"
                + "var d=ev&&ev.data;if(typeof d==='string'&&jehoNeed(d))jehoOpenCharge();"
                + "}catch(e){}});return ws;}"
                + "W.prototype=OW.prototype;"
                + "W.CONNECTING=OW.CONNECTING;W.OPEN=OW.OPEN;"
                + "W.CLOSING=OW.CLOSING;W.CLOSED=OW.CLOSED;W.__jehoEcoWrap=1;window.WebSocket=W;}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void injectRoomGameBalanceSync(WebView view) {
        if (view == null || slotSession == null) return;
        long bal = Math.max(0L, slotSession.balance);
        String uid = slotSession.userId != null ? slotSession.userId.replace("'", "") : "";
        String nick = MikooGameBridge.safeDisplayName(slotSession.displayName, null)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ");
        String avatar = slotSession.avatarUrl != null
                ? slotSession.avatarUrl.replace("'", "") : "";
        // Push coins locally only — do NOT call walletUpdate early (ReqBalance before WS auth).
        String js = "(function(){try{var b=" + bal + ";var uid='" + uid + "';"
                + "var nick='" + nick + "';var av='" + avatar + "';"
                + "window.__jehoUid=uid;window.__jehoNick=nick;window.__jehoAvatar=av;window.__jehoBal=b;"
                + "window.BSGameUser=window.BSGameUser||{};"
                + "BSGameUser.userId=uid;BSGameUser.openId=uid;BSGameUser.nickname=nick;"
                + "BSGameUser.nickName=nick;BSGameUser.name=nick;BSGameUser.avatar=av;"
                + "BSGameUser.balance=b;BSGameUser.coin=b;BSGameUser.userMoney=b;"
                + "function push(){try{"
                + "if(window.androidJsObj){"
                + "try{if(androidJsObj.getBalance)b=Number(androidJsObj.getBalance())||b;}catch(e){}"
                + "try{if(androidJsObj.getUserMoney)b=Number(androidJsObj.getUserMoney())||b;}catch(e){}"
                + "}"
                + "if(window.NativeBridge){"
                + "try{if(NativeBridge.getBalance)b=Number(NativeBridge.getBalance())||b;}catch(e){}"
                + "}"
                + "try{if(window.PlayerData&&PlayerData.SetCoins)PlayerData.SetCoins(b);}catch(e){}"
                + "try{if(window.CoinSafe){CoinSafe.coin=b;CoinSafe.balance=b;}}catch(e){}"
                + "if(typeof SelfBalance==='function')SelfBalance({balance:b,coin:b,gold:b,userMoney:b,userId:uid});"
                + "}catch(e){}}push();setTimeout(push,1200);setTimeout(push,2800);"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void endOverlaySlotSession() {
        handler.removeCallbacks(heartbeatSlot);
        if (slotSession != null && slotSession.sessionId != null) {
            String sid = slotSession.sessionId;
            ContainerProvider.from(this).getIoExecutor().execute(() ->
                    ApiCall.execute(ContainerProvider.from(this).getSlotGameApi().endSession(sid)));
        }
        slotSession = null;
        overlayGameId = null;
        overlayPendingUrl = null;
        overlayGameReady = false;
        if (mikooBridge != null) {
            mikooBridge.detach();
            mikooBridge = null;
        }
        if (hashBridge != null && binding != null) {
            MikooHashBridge.detach(ensureRoomGameWebView());
            hashBridge = null;
        }
    }

    private void openGameOverlay(String url, String title, String coverUrl) {
        if (binding == null) return;
        endOverlaySlotSession();
        // Keep absolute HTTPS game URLs (auth/query) — only resolve bare filenames to assets.
        if (url == null || url.isEmpty()
                || (!url.startsWith("http://") && !url.startsWith("https://")
                && !url.startsWith("file://"))) {
            url = GameUrls.resolve(this, url);
        }
        url = appendGameAuthParams(url);

        boolean immersiveFruit = isDiceOrWheelUrl(url);

        // Overlay close only — Jul 29 layout has no gameHeader/cover.
        if (binding.btnCloseGameOverlay != null) {
            binding.btnCloseGameOverlay.setVisibility(View.GONE);
        }

        setRoomChromeHidden(false);
        showRoomGameLoading(false);
        layoutRoomGamePanel(true);

        try {
            configureRoomGameWebView(false);
            if (immersiveFruit) {
                try {
                    ensureRoomGameWebView().clearCache(false);
                    ensureRoomGameWebView().getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                } catch (Exception ignored) {
                }
            } else {
                try {
                    ensureRoomGameWebView().getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                } catch (Exception ignored) {
                }
            }
            ensureRoomGameWebView().setWebChromeClient(new WebChromeClient());
            // Keep top controls clear of status bar / notch.
            int insetTop = 0;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    android.view.WindowInsets wi = getWindow().getDecorView().getRootWindowInsets();
                    if (wi != null) insetTop = wi.getSystemWindowInsetTop();
                }
                if (insetTop <= 0) {
                    int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                    if (resId > 0) insetTop = getResources().getDimensionPixelSize(resId);
                }
            } catch (Exception ignored) {
            }
            if (immersiveFruit) {
                ensureRoomGameWebView().setPadding(0, Math.max(insetTop, (int) (24 * getResources().getDisplayMetrics().density)), 0, 0);
            } else {
                ensureRoomGameWebView().setPadding(0, 0, 0, 0);
            }
            ensureRoomGameWebView().removeJavascriptInterface("AuraBridge");
            ensureRoomGameWebView().removeJavascriptInterface("NativeBridge");
            ensureRoomGameWebView().removeJavascriptInterface("gameBridge");
            ensureRoomGameWebView().removeJavascriptInterface("androidJsObj");
            ensureRoomGameWebView().addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public String getAccessToken() {
                    String accessToken = ContainerProvider.from(VoiceRoomActivity.this)
                            .getSessionManager()
                            .getAccessToken();
                    return accessToken != null ? accessToken : "";
                }

                @android.webkit.JavascriptInterface
                public void closeGame() {
                    runOnUiThread(() -> closeGameOverlay());
                }

                @android.webkit.JavascriptInterface
                public void openWallet() {
                    runOnUiThread(() -> {
                        try {
                            startActivity(new android.content.Intent(
                                    VoiceRoomActivity.this,
                                    com.Dramizo.Series.presentation.wallet.BagActivity.class));
                        } catch (Exception e) {
                            Toast.makeText(VoiceRoomActivity.this, "تعذر فتح الشحن", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }, "AuraBridge");
            ensureRoomGameWebView().setWebViewClient(new WebViewClient());
            ensureRoomGameWebView().loadUrl(url);
        } catch (Exception ignored) {
        }
        binding.gameOverlay.setVisibility(View.VISIBLE);
        binding.gameOverlay.bringToFront();
        if (binding.luckyFloatWrap != null) {
            binding.luckyFloatWrap.setVisibility(View.GONE);
        }
    }

    private void setRoomChromeHidden(boolean hide) {
        if (binding == null) return;
        int v = hide ? View.GONE : View.VISIBLE;
        // Don't revive tools while the send-only composer is open.
        if (binding.bottomBar != null) {
            binding.bottomBar.setVisibility(
                    (!hide && roomComposerOpen) ? View.GONE : v);
        }
        try {
            int chatId = getResources().getIdentifier("chatPanel", "id", getPackageName());
            if (chatId != 0) {
                View chat = findViewById(chatId);
                if (chat != null) chat.setVisibility(v);
            }
        } catch (Exception ignored) {
        }
    }

    private String appendGameAuthParams(String url) {
        if (url == null || url.isEmpty()) return url;
        String origin = com.Dramizo.Series.util.ApiOrigin.origin();
        StringBuilder out = new StringBuilder(url);
        char sep = url.contains("?") ? '&' : '?';
        if (roomId != null && !roomId.isEmpty() && !url.contains("roomId=")) {
            out.append(sep).append("roomId=").append(android.net.Uri.encode(roomId));
            sep = '&';
        }
        if (!url.contains("apiBase=")) {
            out.append(sep).append("apiBase=").append(android.net.Uri.encode(origin));
            sep = '&';
        }
        if (!url.contains("lang=")) {
            String lang = "ar";
            try {
                String saved = ContainerProvider.from(this).getSessionManager().getLanguage();
                if (saved != null && !saved.isEmpty()) lang = saved;
            } catch (Exception ignored) {
            }
            out.append(sep).append("lang=").append(android.net.Uri.encode(lang));
        }
        return out.toString();
    }

    private void closeGameOverlay() {
        if (binding == null) return;
        if (binding.gameOverlay.getVisibility() != View.VISIBLE) {
            closeGameOverlayInternal();
            return;
        }
        GameAdsHelper ads = GameAdsHelper.get(this);
        ads.maybeShowOnClose(this, this::closeGameOverlayInternal);
    }

    private void closeGameOverlayInternal() {
        if (binding == null) return;
        endOverlaySlotSession();
        showRoomGameLoading(false);
        try {
            if (roomGameWebView != null) {
                roomGameWebView.setPadding(0, 0, 0, 0);
                roomGameWebView.stopLoading();
                roomGameWebView.loadUrl("about:blank");
                roomGameWebView.removeJavascriptInterface("AuraBridge");
                roomGameWebView.removeJavascriptInterface("NativeBridge");
                roomGameWebView.removeJavascriptInterface("gameBridge");
                roomGameWebView.removeJavascriptInterface("androidJsObj");
                try {
                    if (binding != null && binding.webGameHost != null) {
                        binding.webGameHost.removeView(roomGameWebView);
                    }
                    roomGameWebView.destroy();
                } catch (Exception ignored2) {
                }
                roomGameWebView = null;
            }
        } catch (Exception ignored) {
        }
        binding.gameOverlay.setVisibility(View.GONE);
        setRoomChromeHidden(false);
    }

    private static MiscDtos.GameDto game(String title, String play, String cover) {
        MiscDtos.GameDto g = new MiscDtos.GameDto();
        g.title = title;
        g.playUrl = play;
        g.coverUrl = cover;
        return g;
    }

    private static class RoomGamesAdapter extends RecyclerView.Adapter<RoomGamesAdapter.VH> {
        interface Listener { void onPlay(MiscDtos.GameDto game); }

        private final List<MiscDtos.GameDto> items = new ArrayList<>();
        private final Listener listener;

        RoomGamesAdapter(Listener listener) { this.listener = listener; }

        void submit(List<MiscDtos.GameDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room_game_icon, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MiscDtos.GameDto g = items.get(position);
            h.tvTitle.setText(MikooGamesCatalog.displayTitle(h.itemView.getContext(), g));
            String cover = g.coverUrl;
            if (g.id != null && !g.id.isEmpty()
                    && (cover == null || cover.isEmpty()
                    || MediaAssetSync.isPackageDefaultCover(cover, g.id))) {
                cover = MediaAssetSync.mikooCoverUrl(g.id);
            } else {
                cover = MediaAssetSync.bust(cover);
            }
            MediaAssetSync.loadInto(h.imgIcon, cover, ImagePlaceholder.game(), 192);
            h.itemView.setOnClickListener(v -> listener.onPlay(g));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView imgIcon;
            final TextView tvTitle;

            VH(View itemView) {
                super(itemView);
                imgIcon = itemView.findViewById(R.id.imgIcon);
                tvTitle = itemView.findViewById(R.id.tvTitle);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQ_MEDIA && PermissionHelper.hasAudioPermission(this)) {
            activateSeatAudio(currentSeats);
        }
    }
}
