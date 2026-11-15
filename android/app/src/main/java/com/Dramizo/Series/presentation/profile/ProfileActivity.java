package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.BadgeDto;
import com.Dramizo.Series.databinding.ActivityProfileBinding;
import com.Dramizo.Series.databinding.ItemGiftHistoryBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;

import com.Dramizo.Series.presentation.common.AgencyFamilyInfoSheet;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.UserSocialNav;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AgencyUi;
import com.Dramizo.Series.util.AgencyRoleBadgeCache;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.GenderVerifiedBadge;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.ServerAssets;
import com.Dramizo.Series.util.StaffRoleHelper;
import com.Dramizo.Series.util.StaffIdentityConfigCache;
import com.Dramizo.Series.presentation.admin.AdminConsoleActivity;
import com.Dramizo.Series.util.VipStyle;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.text.NumberFormat;

public class ProfileActivity extends ThemedActivity {
    // Profile UI refinements: compact medals, clean supporters, moments-only navigation.
    // Profile inline supporters and gifts use live gift aggregates, never social followers.
    public static final String EXTRA_USER_ID = "user_id";
    private ActivityProfileBinding binding;
    private AppContainer container;
    private boolean isFollowingTarget;
    private boolean isOwnProfile;
    private final Handler profileHandler = new Handler(Looper.getMainLooper());
    private final Runnable mysteriousModeRefresh = new Runnable() {
        @Override public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (isOwnProfile) bindMysteriousMode(true);
            profileHandler.postDelayed(this, 60_000L);
        }
    };
    private AnimatorSet cpHeartAnimator;
    private String loadedProfileId = "";
    private final java.util.ArrayList<String> profileCoverGallery = new java.util.ArrayList<>();
    private int profileCoverIndex = 0;
    private float coverTouchStartX = 0f;
    private final Runnable profileCoverAdvance = new Runnable() {
        @Override public void run() {
            if (binding == null || isFinishing() || isDestroyed() || profileCoverGallery.size() < 2) return;
            showProfileCover((profileCoverIndex + 1) % profileCoverGallery.size());
            profileHandler.postDelayed(this, 3000L);
        }
    };
    private com.Dramizo.Series.realtime.RealtimeClient.UserListener cpConfigListener;
    private String cpProfileTargetId = "";
    private String cpProfileOwnAvatarUrl = "";
    private String cpProfileOwnFrameUrl = "";
    private int cpProfileOwnVipLevel = 0;
    private String cpProfileOwnVipTouUrl = "";
    private String cpProfileOwnHostBadgeUrl = "";
    private Map<String, Object> cpProfileOwnHostBadgeMeta;
    private String roomTrackingRoomId;
    private String roomTrackingRoomName = "";
    private final Runnable roomPresenceRefresh = new Runnable() {
        @Override public void run() {
            if (binding == null || isFinishing() || isDestroyed()) return;
            if (!isOwnProfile && loadedProfileId != null && !loadedProfileId.isEmpty()) {
                refreshRoomPresence(loadedProfileId);
                profileHandler.postDelayed(this, 15_000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.apply(this);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnBack, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = bars.top + (int) (8 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(lp);
            return insets;
        });
        EdgeToEdgeHelper.padBottom(binding.contentRoot);
        container = ContainerProvider.from(this);
        cpConfigListener = new com.Dramizo.Series.realtime.RealtimeClient.UserListener() {
            @Override public void onUserEvent(String event, com.google.gson.JsonObject payload) {
                if (!"cp:config-updated".equals(event)) return;
                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()
                            || cpProfileTargetId == null || cpProfileTargetId.trim().isEmpty()) return;
                    // Re-read the authoritative CP profile data so dashboard-edited
                    // level names, backgrounds and logos update without reopening the profile.
                    loadPublicCpRelationship(cpProfileTargetId, cpProfileOwnAvatarUrl,
                            cpProfileOwnFrameUrl, cpProfileOwnVipLevel, cpProfileOwnVipTouUrl,
                            cpProfileOwnHostBadgeUrl, cpProfileOwnHostBadgeMeta);
                });
            }
        };
        com.Dramizo.Series.realtime.RealtimeClient.getInstance().addUserListener(cpConfigListener);
        ProfileViewModel vm = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        String myId = container.getSessionManager().getUserId();
        AuthDtos.UserDto signedInUser = container.getSessionManager().getUser();
        if (myId != null && myId.equals(userId == null || userId.isEmpty() ? myId : userId)
                && StaffRoleHelper.isStaff(signedInUser)) {
            TextView adminEntry = new TextView(this);
            String roleLabel = StaffRoleHelper.badgeAr(signedInUser);
            adminEntry.setText("🛡️  مركز إدارة JEHO  ·  " + roleLabel);
            adminEntry.setTextSize(14f);
            adminEntry.setTypeface(adminEntry.getTypeface(), Typeface.BOLD);
            adminEntry.setTextColor(Color.WHITE);
            adminEntry.setGravity(Gravity.CENTER);
            adminEntry.setPadding(16, 16, 16, 16);
            GradientDrawable adminBackground = new GradientDrawable(
                    GradientDrawable.Orientation.RIGHT_LEFT,
                    new int[] { 0xFF282C63, 0xFF6A4CC4 });
            adminBackground.setCornerRadius(18f * getResources().getDisplayMetrics().density);
            adminEntry.setBackground(adminBackground);
            LinearLayout.LayoutParams adminLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            adminLp.setMargins(16, 16, 16, 12);
            binding.contentRoot.addView(adminEntry, adminLp);
            adminEntry.setOnClickListener(v -> {
                AuthDtos.UserDto latest = container.getSessionManager().getUser();
                if (!StaffRoleHelper.isStaff(latest)) {
                    Toast.makeText(this, "لم تعد لديك صلاحية الوصول إلى مركز الإدارة", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(this, AdminConsoleActivity.class));
            });
        }
        final String[] targetUserIdHold = {
                userId != null && !userId.isEmpty() ? userId : (myId != null ? myId : "")
        };

        // The badges-and-medals cabinet is available from the Details page only.
        if (binding.rowBadges != null) binding.rowBadges.setVisibility(View.GONE);
        if (binding.rowEarnedBadges != null) binding.rowEarnedBadges.setVisibility(View.GONE);
        if (binding.rowProfileAchievementBadges != null) {
            binding.rowProfileAchievementBadges.setVisibility(View.GONE);
            binding.rowProfileAchievementBadges.setOnClickListener(v -> openBadgeCabinet(targetUserIdHold[0]));
        }
        // Keep the VIP, charm and wealth chips visible on the light profile card.
        if (binding.rowProfileMedals != null) binding.rowProfileMedals.setVisibility(View.VISIBLE);

        if (binding.rowMoments != null) {
            binding.rowMoments.setOnClickListener(v -> openProfileDetails());
        }

        // Mikoo card taps on full profile page.
        if (binding.cellProfileFollowers != null) {
            binding.cellProfileFollowers.setOnClickListener(v -> {
                String id = loadedProfileId;
                if (id == null || id.isEmpty()) return;
                UserSocialNav.openFollowers(this, id, myId);
            });
        }
        if (binding.cellProfileFans != null) {
            binding.cellProfileFans.setOnClickListener(v -> {
                String id = targetUserIdHold[0];
                if (id == null || id.isEmpty()) return;
                UserSocialNav.openFollowing(this, id, myId);
            });
        }
        if (binding.cellProfileVisitors != null) {
            binding.cellProfileVisitors.setOnClickListener(v -> {
                String id = targetUserIdHold[0];
                if (id == null || id.isEmpty()) return;
                UserSocialNav.openVisitors(this, id, myId);
            });
        }
        if (binding.sectionProfileSupporters != null) {
            binding.sectionProfileSupporters.setVisibility(View.VISIBLE);
            binding.sectionProfileSupporters.setOnClickListener(v -> openProfileSupportersPage(targetUserIdHold[0]));
        }
        if (binding.tvProfileSupportersAll != null) {
            binding.tvProfileSupportersAll.setOnClickListener(v -> openProfileSupportersPage(targetUserIdHold[0]));
        }
        // Gift history is accessed from Details only; keep its existing dedicated screen/design.
        if (binding.recyclerProfileGiftHistory != null && binding.recyclerProfileGiftHistory.getParent() instanceof View) {
            ((View) binding.recyclerProfileGiftHistory.getParent()).setVisibility(View.GONE);
        }

        // Button 2 is the Details shortcut; the supporters panel remains independent.
        if (binding.propProfileSupporters != null) {
            binding.propProfileSupporters.setOnClickListener(v -> openProfileDetails());
        }
        if (binding.propProfileFriends != null) {
            binding.propProfileFriends.setOnClickListener(v -> {
                String id = targetUserIdHold[0];
                if (id == null || id.isEmpty()) return;
                UserSocialNav.openFriends(this, id, myId);
            });
        }

        binding.btnFollow.setOnClickListener(v -> {
            // Use the loaded profile UUID rather than a possible public-ID/deep-link value.
            String targetId = targetUserIdHold[0];
            if (targetId == null || targetId.trim().isEmpty()) return;
            if (myId != null && targetId.equals(myId)) {
                Toast.makeText(this, "هذا حسابك", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnFollow.setEnabled(false);
            final boolean unfollow = isFollowingTarget;
            container.getIoExecutor().execute(() -> {
                Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                        unfollow
                                ? container.getUserApi().unfollow(targetId)
                                : container.getUserApi().follow(targetId));
                runOnUiThread(() -> {
                    binding.btnFollow.setEnabled(true);
                    if (r.success) {
                        isFollowingTarget = !unfollow;
                        binding.btnFollow.setText(isFollowingTarget ? R.string.unfollow : R.string.follow);
                        if (binding.btnFollowSticky != null) binding.btnFollowSticky.setText(isFollowingTarget ? R.string.unfollow : R.string.follow);
                        Toast.makeText(this,
                                isFollowingTarget ? "تمت المتابعة" : "تم إلغاء المتابعة",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        // CP relationships are initiated only by sending the configured CP gift.
        // Keep the legacy view hidden for old layouts; never expose a profile request action.
        binding.btnCpRequest.setVisibility(View.GONE);
        binding.btnCpRequest.setOnClickListener(null);
        if (binding.btnMore != null) {
            binding.btnMore.setOnClickListener(v -> showProfileMoreMenu(targetUserIdHold[0]));
        }
        if (binding.btnRoom != null) {
            binding.btnRoom.setOnClickListener(v -> {
                if (roomTrackingRoomId == null || roomTrackingRoomId.trim().isEmpty()) {
                    Toast.makeText(this, "هذا المستخدم ليس في غرفة حالياً", Toast.LENGTH_SHORT).show();
                    return;
                }
                openTrackedRoom();
            });
        }
        binding.btnBack.setOnClickListener(v -> navigateUp());
        if (binding.btnFollowSticky != null) binding.btnFollowSticky.setOnClickListener(v -> binding.btnFollow.performClick());
        if (binding.btnMessageSticky != null) binding.btnMessageSticky.setOnClickListener(v -> binding.btnMessage.performClick());
        if (binding.btnRoomSticky != null) binding.btnRoomSticky.setOnClickListener(v -> binding.btnRoom.performClick());
        if (binding.btnRoomTracking != null) {
            binding.btnRoomTracking.setOnClickListener(v -> openTrackedRoom());
        }
        if (binding.btnRoomTrackingPrivacy != null) {
            binding.btnRoomTrackingPrivacy.setOnClickListener(v -> showRoomTrackingPrivacyDialog());
        }
        binding.btnMessage.setOnClickListener(v -> {
            if (userId == null || userId.isEmpty()) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "لا يمكن مراسلة نفسك", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnMessage.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Map<String, String> body = new HashMap<>();
                body.put("peerId", userId);
                Result<com.Dramizo.Series.data.remote.dto.ChatDtos.ConversationDto> r =
                        com.Dramizo.Series.util.ApiCall.execute(container.getChatApi().create(body));
                runOnUiThread(() -> {
                    binding.btnMessage.setEnabled(true);
                    if (!r.success || r.data == null || r.data.id == null) {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent i = new Intent(this, com.Dramizo.Series.presentation.chat.ChatConversationActivity.class);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_CONVERSATION_ID, r.data.id);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_PEER_ID, userId);
                    if (r.data.peer != null) {
                        String title = r.data.peer.displayName != null ? r.data.peer.displayName : r.data.peer.username;
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_TITLE, title);
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_AVATAR, r.data.peer.avatarUrl);
                        if (r.data.peer.vipBadgeUrl != null) {
                            i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_HOST_BADGE,
                                    r.data.peer.vipBadgeUrl);
                        }
                    }
                    startActivity(i);
                });
            });
        });

        // Received gifts are shown directly in the profile as a horizontal swipeable strip.
        if (userId != null && myId != null && !userId.equals(myId)) {
            container.getIoExecutor().execute(() -> {
                try { container.getUserApi().visit(userId).execute(); } catch (Exception ignored) {}
            });
        }

        // Wait for the incoming public ID/deep link to resolve to a UUID before enabling follow.
        binding.btnFollow.setEnabled(false);
        vm.getUser().observe(this, user -> {
            if (user == null) return;
            binding.btnFollow.setEnabled(true);
            boolean ownProfile = myId != null && myId.equals(user.id);
            isOwnProfile = ownProfile;
            binding.btnCpRequest.setVisibility(View.GONE);
            // The mysterious-mode activation control belongs only in Me -> Profile.
            // Never expose an activation/cancel switch from a public profile opened from a room.
            if (binding.rowProfileMysteriousMode != null) {
                binding.rowProfileMysteriousMode.setVisibility(View.GONE);
            }
            // Fail closed: only the owner can see the control, and it stays hidden until
            // the server confirms an active VIP that includes Mysterious Man.
            if (ownProfile) {
                bindMysteriousMode(true);
                profileHandler.removeCallbacks(mysteriousModeRefresh);
                profileHandler.postDelayed(mysteriousModeRefresh, 60_000L);
            } else {
                profileHandler.removeCallbacks(mysteriousModeRefresh);
            }
            if (binding.btnRoomTrackingPrivacy != null) {
                binding.btnRoomTrackingPrivacy.setVisibility(ownProfile ? View.VISIBLE : View.GONE);
            }
            if (ownProfile) {
                binding.btnRoomTracking.setVisibility(View.GONE);
                profileHandler.removeCallbacks(roomPresenceRefresh);
            } else {
                refreshRoomPresence(user.id);
                profileHandler.removeCallbacks(roomPresenceRefresh);
                profileHandler.postDelayed(roomPresenceRefresh, 15_000L);
            }
            loadPublicCpRelationship(user.id, user.avatarUrl, user.vipBadgeUrl,
                    user.vipLevel, user.vipTouUrl, user.hostBadgeUrl, user.hostBadgeMeta);
            int vip = Math.max(0, user.vipLevel);
            int level = Math.max(1, user.level);

            // Public profiles use the app's light settings palette, independent of room VIP-card styling.
            if (binding.userCardBg != null) {
                binding.userCardBg.setBackgroundResource(R.drawable.bg_profile_panel);
            }

            String profileDisplayName = user.displayName != null && !user.displayName.isEmpty()
                    ? user.displayName : "مستخدم";
            // Custom display-rank/medal system is retired from the app UI.
            binding.tvDisplayName.setText(profileDisplayName);
            com.Dramizo.Series.util.VipNameStyle.bind(binding.tvDisplayName, user);
            Glide.with(binding.imgCustomDisplayRank).clear(binding.imgCustomDisplayRank);
            binding.imgCustomDisplayRank.setVisibility(View.GONE);
            GenderVerifiedBadge.bind(binding.tvDisplayName, null, user.genderVerified);
            bindGenderIcon(binding.imgGender, user.gender);

            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "—" : publicId);
            binding.tvUsername.setTag(user.id);
            com.Dramizo.Series.util.VanityIdStyle.bind(binding.tvUsername,
                    container.getVanityApi(), publicId, user.vipLevel);
            binding.btnCopyId.setOnClickListener(v -> {
                if (publicId.isEmpty()) {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("user_id", publicId));
                    Toast.makeText(this, R.string.user_id_copied, Toast.LENGTH_SHORT).show();
                }
            });
            binding.tvBio.setText(user.bio != null && !user.bio.isEmpty()
                    ? user.bio
                    : getString(R.string.lazy_no_signature));

            // Mikoo counters: متابعون · مشجع · زائر
            int followers = Math.max(0, user.followersCount);
            int following = Math.max(0, user.followingCount);
            int visitors = Math.max(0, user.visitorsCount);
            int friends = Math.max(0, user.friendsCount);
            if (user.id != null && !user.id.isEmpty()) {
                targetUserIdHold[0] = user.id;
                loadedProfileId = user.id;
                if (binding.sectionProfileSupporters != null) {
                    binding.sectionProfileSupporters.setVisibility(View.VISIBLE);
                }
                loadProfileMedals(user.id);
                loadProfileAchievementBadges(user.id);
                loadProfileSupporters(user.id);
                loadProfileGiftStrip(user.id);
            }
            if (binding.tvStatFriends != null) binding.tvStatFriends.setText(formatScore(followers));
            if (binding.tvStatFollowing != null) binding.tvStatFollowing.setText(formatScore(following));
            if (binding.tvStatFans != null) binding.tvStatFans.setText(formatScore(visitors));
            if (binding.tvStats != null) {
                binding.tvStats.setText(getString(R.string.user_card_stat_following) + " " + followers
                        + " · " + getString(R.string.user_card_stat_fans) + " " + following
                        + " · " + getString(R.string.user_card_stat_visitors) + " " + visitors);
            }
            bindStaffBadge(user);
            bindAgencyRoleBadge(user);
            bindAgencyStrip(user.agency, user.agencyClub);

            // Level chip (same pill as card member chip).
            binding.tvLevelChip.setText(String.valueOf(level));
            binding.tvLevelChip.setBackgroundResource(R.drawable.bg_user_card_id_chip);
            binding.tvLevelChip.setTextColor(0xFF4F3C91);
            binding.tvLevelChip.setCompoundDrawablesRelative(null, null, null, null);

            if (binding.imgCountryFlag != null) {
                FlagImages.bind(binding.imgCountryFlag, user.country);
            }
            // Earned/gifted profile badges are loaded into the swipeable strip beneath the ID row.

            long charm = user.popularityLevel > 0 ? user.popularityLevel
                    : Math.max(0, Math.max(user.charmScore, user.popularityScore));
            long wealth = user.wealthLevel > 0 ? user.wealthLevel
                    : Math.max(0, user.wealthScore);
            binding.tvVipChip.setText("VIP" + vip);
            binding.tvVipChip.setVisibility(vip > 0 ? View.VISIBLE : View.GONE);
            if (vip > 0) {
                binding.tvVipChip.setBackgroundResource(R.drawable.bg_chip_vip);
                binding.tvVipChip.setTextColor(0xFFFFFFFF);
            }
            binding.tvCharmChip.setText(formatScore(charm));
            binding.tvCharmChip.setVisibility(View.VISIBLE);
            binding.tvCharmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            binding.tvCharmChip.setTextColor(0xFFFFFFFF);
            binding.tvWealthChip.setText(formatScore(wealth));
            binding.tvWealthChip.setVisibility(View.VISIBLE);
            binding.tvWealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
            binding.tvWealthChip.setTextColor(0xFFFFFFFF);

            // Do not overlay VIP head artwork on the cover: it was stretching across the banner.
            if (binding.imgVipHead != null) {
                binding.imgVipHead.setVisibility(View.GONE);
                binding.imgVipHead.setImageDrawable(null);
            }
            bindVipMedal(binding.imgVipMedal, vip, user.levelBadgeUrl);
            bindEntryRide(
                    binding.imgEntryRide,
                    firstNonEmpty(user.entryAnimationUrl, user.entryEffectUrl));

            // Profile: fixed VIP nobility frame (ud_vip_tou); mall SVGA stays on mics only.
            String nobilityFrame = VipStyle.profileNobilityFrameUrl(vip, user.vipTouUrl);
            String profileFrame = user.vipBadgeUrl != null && !user.vipBadgeUrl.trim().isEmpty()
                    ? user.vipBadgeUrl : nobilityFrame;
            AvatarCosmetics.bindProfileWear(
                    binding.webProfileHostSignal,
                    binding.imgAvatar,
                    binding.imgFrame,
                    user.avatarUrl,
                    profileFrame,
                    null,
                    null,
                    1);
            // Secure only the enlarged avatar viewer; the profile screen itself remains screenshot-able.
            binding.imgAvatar.setOnClickListener(v -> {
                if (user.avatarUrl == null || user.avatarUrl.trim().isEmpty()) return;
                Intent secureImage = new Intent(ProfileActivity.this, SecureProfileImageActivity.class);
                secureImage.putExtra(SecureProfileImageActivity.EXTRA_IMAGE_URL, user.avatarUrl);
                startActivity(secureImage);
            });
            com.Dramizo.Series.util.HostSignalView.prefetchWear(this, profileFrame);
            if (binding.imgHostBadge != null) {
                binding.imgHostBadge.setVisibility(View.GONE);
                binding.imgHostBadge.setImageDrawable(null);
            }
            if (binding.imgVipBadge != null) {
                binding.imgVipBadge.setVisibility(View.GONE);
                binding.imgVipBadge.setImageDrawable(null);
            }

            // Online pill (card style); offline falls back to last-seen text.
            if (Boolean.TRUE.equals(user.isOnline)) {
                binding.tvLastSeen.setVisibility(View.VISIBLE);
                binding.tvLastSeen.setText(R.string.online_now);
                binding.tvLastSeen.setTextColor(0xFF16865B);
            } else if (user.lastSeenAt != null && !user.lastSeenAt.isEmpty()) {
                binding.tvLastSeen.setVisibility(View.VISIBLE);
                binding.tvLastSeen.setText(DeviceTimeFormat.lastSeen(this, user.lastSeenAt));
                binding.tvLastSeen.setTextColor(0xFF73798D);
            } else {
                binding.tvLastSeen.setVisibility(View.GONE);
            }

            bindProfileCoverGallery(user.profileCoverUrls, user.coverUrl);
            if (myId != null && myId.equals(user.id)) {
                binding.btnFollow.setVisibility(View.GONE);
            } else {
                binding.btnFollow.setVisibility(View.VISIBLE);
                isFollowingTarget = Boolean.TRUE.equals(user.isFollowing);
                binding.btnFollow.setText(isFollowingTarget ? R.string.unfollow : R.string.follow);
                if (binding.btnFollowSticky != null) binding.btnFollowSticky.setText(isFollowingTarget ? R.string.unfollow : R.string.follow);
            }

            // Only show the agency/family tile when the backend confirms an active
            // membership. Deleted or suspended agencies must disappear from every profile.
            // Profile tabs are fixed: ملكية / لحظاتي. Agency navigation remains available
            // from the dedicated agency strip elsewhere, not by replacing the tab label.
            if (binding.propProfileFamily != null) {
                binding.propProfileFamily.setVisibility(View.VISIBLE);
                binding.propProfileFamily.setOnClickListener(v -> showProfileProperty());
            }
            if (binding.tvProfileAgencyName != null) binding.tvProfileAgencyName.setText("ملكية");
            if (binding.propProfileSupporters != null) {
                binding.propProfileSupporters.setVisibility(View.VISIBLE);
                binding.propProfileSupporters.setOnClickListener(v -> openProfileDetails());
            }
            if (binding.tvPropProfileSupporters != null) binding.tvPropProfileSupporters.setText("تفاصيل");
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        if (userId != null) vm.loadUser(userId); else vm.loadMe();
    }



    private void openProfileSupportersPage(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) return;
        Intent supporters = new Intent(this, SupportersActivity.class);
        supporters.putExtra(EXTRA_USER_ID, profileId);
        startActivity(supporters);
    }

    /** Top accounts that actually sent gifts to this profile — never followers. */
    private void loadProfileSupporters(String profileId) {
        if (binding == null || profileId == null || profileId.trim().isEmpty()) return;
        if (binding.sectionProfileSupporters != null) binding.sectionProfileSupporters.setVisibility(View.VISIBLE);
        RecyclerView recycler = binding.recyclerProfileSupporters;
        recycler.setVisibility(View.VISIBLE);
        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recycler.setNestedScrollingEnabled(false);
        container.getIoExecutor().execute(() -> {
            Result<List<com.Dramizo.Series.data.remote.dto.GiftDtos.GiftSupporterDto>> result =
                    com.Dramizo.Series.util.ApiCall.execute(container.getGiftApi().supporters(profileId));
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()) return;
                List<com.Dramizo.Series.data.remote.dto.GiftDtos.GiftSupporterDto> supporters =
                        result.success && result.data != null ? result.data : new java.util.ArrayList<>();
                recycler.setVisibility(View.VISIBLE);
                recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View item = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_profile_supporter, parent, false);
                        return new RecyclerView.ViewHolder(item) {};
                    }
                    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                        com.Dramizo.Series.data.remote.dto.GiftDtos.GiftSupporterDto supporter = supporters.get(position);
                        ImageView avatar = holder.itemView.findViewById(R.id.imgSupporterAvatar);
                        ImageView frame = holder.itemView.findViewById(R.id.imgSupporterFrame);
                        com.Dramizo.Series.util.HostSignalView liveFrame = holder.itemView.findViewById(R.id.supporterHostSignal);
                        TextView name = holder.itemView.findViewById(R.id.tvSupporterName);
                        name.setText(supporter.displayName == null || supporter.displayName.trim().isEmpty()
                                ? supporter.username : supporter.displayName);
                        TextView diamonds = holder.itemView.findViewById(R.id.tvSupporterDiamonds);
                        if (diamonds != null) {
                            diamonds.setVisibility(View.VISIBLE);
                            diamonds.setText("◆ " + formatSupportAmount(supporter.totalSupport));
                        }
                        AvatarCosmetics.bindStacked(avatar, frame, liveFrame, supporter.avatarUrl, supporter.frameUrl, null);
                        holder.itemView.setOnClickListener(v -> {
                            String id = profileId;
                            if (id == null || id.trim().isEmpty()) return;
                            Intent supporters = new Intent(ProfileActivity.this, SupportersActivity.class);
                            supporters.putExtra(EXTRA_USER_ID, id);
                            startActivity(supporters);
                        });
                    }
                    @Override public int getItemCount() { return supporters.size(); }
                });
                binding.tvProfileSupportersEmpty.setVisibility(supporters.isEmpty() ? View.VISIBLE : View.GONE);
                if (binding.tvPropProfileSupporters != null) {
                    binding.tvPropProfileSupporters.setText("تفاصيل");
                }
            });
        });
    }

    /** Compact gift cabinet embedded in the profile; horizontal swipe, no navigation tap. */
    private void loadProfileGiftStrip(String profileId) {
        if (binding == null || profileId == null || profileId.trim().isEmpty()) return;
        RecyclerView recycler = binding.recyclerProfileGiftHistory;
        int profileWidthDp = getResources().getConfiguration().screenWidthDp;
        int giftColumns = profileWidthDp >= 420 ? 4 : 3;
        recycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, giftColumns));
        recycler.setNestedScrollingEnabled(false);
        List<com.Dramizo.Series.data.remote.dto.GiftDtos.GiftReceivedSummaryDto> gifts = new java.util.ArrayList<>();
        RecyclerView.Adapter<GiftStripHolder> adapter = new RecyclerView.Adapter<GiftStripHolder>() {
            @NonNull @Override
            public GiftStripHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemGiftHistoryBinding item = ItemGiftHistoryBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false);
                item.getRoot().setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (132 * getResources().getDisplayMetrics().density)));
                return new GiftStripHolder(item);
            }
            @Override public void onBindViewHolder(@NonNull GiftStripHolder holder, int position) {
                com.Dramizo.Series.data.remote.dto.GiftDtos.GiftReceivedSummaryDto gift = gifts.get(position);
                holder.binding.tvGiftName.setText(gift.giftName == null ? "هدية" : gift.giftName);
                holder.binding.tvGiftMeta.setText("× " + NumberFormat.getNumberInstance(Locale.US)
                        .format(Math.max(0L, gift.quantity)));
                Glide.with(holder.binding.imgGift).load(AssetCatalog.absoluteUrl(gift.giftIconUrl))
                        .placeholder(ImagePlaceholder.gift()).error(ImagePlaceholder.gift())
                        .into(holder.binding.imgGift);
            }
            @Override public int getItemCount() { return gifts.size(); }
        };
        recycler.setAdapter(adapter);
        container.getIoExecutor().execute(() -> {
            Result<List<com.Dramizo.Series.data.remote.dto.GiftDtos.GiftReceivedSummaryDto>> result =
                    com.Dramizo.Series.util.ApiCall.execute(container.getGiftApi().receivedHistory(profileId));
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()) return;
                gifts.clear();
                if (result.success && result.data != null) gifts.addAll(result.data);
                adapter.notifyDataSetChanged();
                binding.tvProfileGiftsEmpty.setVisibility(gifts.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private static final class GiftStripHolder extends RecyclerView.ViewHolder {
        final ItemGiftHistoryBinding binding;
        GiftStripHolder(ItemGiftHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private void refreshRoomPresence(String targetId) {
        if (targetId == null || targetId.trim().isEmpty() || container == null) return;
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().roomPresence(targetId));
            boolean inRoom = result.success && result.data != null
                    && Boolean.TRUE.equals(result.data.get("inRoom"));
            String roomId = inRoom && result.data.get("roomId") != null
                    ? String.valueOf(result.data.get("roomId")) : null;
            String roomName = inRoom && result.data.get("roomName") != null
                    ? String.valueOf(result.data.get("roomName")) : "";
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()
                        || !targetId.equals(loadedProfileId)) return;
                roomTrackingRoomId = roomId;
                roomTrackingRoomName = roomName;
                if (binding.btnRoomTracking != null) {
                    boolean visible = inRoom && roomId != null && !roomId.isEmpty();
                    binding.btnRoomTracking.setText(visible
                            ? "●  في غرفة: " + (roomName.isEmpty() ? "غرفة صوتية" : roomName) : "");
                    binding.btnRoomTracking.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private void openTrackedRoom() {
        String roomId = roomTrackingRoomId;
        if (roomId == null || roomId.trim().isEmpty()) return;
        Intent intent = new Intent(this, com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.class);
        intent.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_ROOM_ID, roomId);
        startActivity(intent);
    }

    private void showRoomTrackingPrivacyDialog() {
        if (container == null) return;
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().roomTrackingPrivacy());
            String current = result.success && result.data != null && result.data.get("audience") != null
                    ? String.valueOf(result.data.get("audience")) : "everyone";
            String[] values = {"everyone", "followers", "friends", "nobody"};
            String[] labels = {"الجميع", "المتابعون", "الأصدقاء", "لا أحد"};
            int selected = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
            final int checked = selected;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("من يستطيع تتبعي؟")
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        dialog.dismiss();
                        saveRoomTrackingPrivacy(values[which]);
                    })
                    .setNegativeButton("إغلاق", null)
                    .show());
        });
    }

    private void saveRoomTrackingPrivacy(String audience) {
        container.getIoExecutor().execute(() -> {
            Map<String, String> body = new HashMap<>();
            body.put("audience", audience);
            Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().updateRoomTrackingPrivacy(body));
            runOnUiThread(() -> Toast.makeText(this,
                    result.success ? "تم تحديث خصوصية تتبع الغرف" :
                            (result.error != null ? result.error : "تعذر حفظ إعداد الخصوصية"),
                    Toast.LENGTH_SHORT).show());
        });
    }

    private void openBadgeCabinet(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) return;
        Intent intent = new Intent(this, MedalActivity.class);
        intent.putExtra(EXTRA_USER_ID, profileId);
        startActivity(intent);
    }

    private String stringProfile(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return "";
        return String.valueOf(map.get(key));
    }

    /** Switch back to the ownership tab; its content stays in the profile. */
    private void showProfileProperty() {
        if (binding == null) return;
        profileDetailsExpanded = false;
        setProfileTab(false);
        if (binding.profileDetailsInlinePanel != null) binding.profileDetailsInlinePanel.setVisibility(View.GONE);
        if (binding.rowProfileAgency != null) {
            binding.rowProfileAgency.setVisibility(profileAgencyVisibleBeforeDetails ? View.VISIBLE : View.GONE);
        }
        if (binding.rowProfileCpCv != null) {
            binding.rowProfileCpCv.setVisibility(profileCpVisibleBeforeDetails ? View.VISIBLE : View.GONE);
        }
        if (binding.rowProfileAgency != null && binding.rowProfileAgency.getVisibility() == View.GONE
                && binding.rowProfileCpCv != null && binding.rowProfileCpCv.getVisibility() == View.GONE) {
            // Keep the ownership tab useful even when the profile has no agency/CP content.
            // No fake ownership data is created.
        }
    }

    private void addProfileDetailStat(android.widget.LinearLayout parent, String label,
            String value, Runnable action) {
        android.widget.LinearLayout item = new android.widget.LinearLayout(this);
        item.setOrientation(android.widget.LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(6, 6, 6, 6);
        item.setClickable(true);
        item.setFocusable(true);
        TextView count = new TextView(this);
        count.setText(value);
        count.setTextColor(0xFFFFFFFF);
        count.setTextSize(16);
        count.setTypeface(null, android.graphics.Typeface.BOLD);
        item.addView(count);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(0xFFBDB3C4);
        title.setTextSize(11);
        item.addView(title);
        item.setOnClickListener(v -> action.run());
        parent.addView(item, new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void bindCpPartnerAvatarAndFrame(String avatarUrl, String frameUrl, String hostBadgeUrl) {
        if (binding == null) return;
        bindRealCpAvatar(binding.imgProfileCpPartner, avatarUrl);
        String selectedFrame = hostBadgeUrl != null && !hostBadgeUrl.trim().isEmpty()
                ? hostBadgeUrl.trim() : (frameUrl == null ? "" : frameUrl.trim());
        ImageView frame = binding.imgProfileCpPartnerFrame;
        if (frame == null) return;
        if (selectedFrame.isEmpty()) {
            Glide.with(this).clear(frame);
            frame.setImageDrawable(null);
            frame.setVisibility(View.GONE);
            return;
        }
        frame.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.setVisibility(View.VISIBLE);
        // Preserve the full transparent frame silhouette; do not circle-clip the artwork.
        Glide.with(this).load(AssetCatalog.absoluteUrl(selectedFrame))
                .fitCenter().into(frame);
        frame.bringToFront();
    }

}
