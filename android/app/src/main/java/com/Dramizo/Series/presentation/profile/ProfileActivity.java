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
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.databinding.ActivityProfileBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import android.widget.ImageView;

import com.Dramizo.Series.presentation.common.AgencyFamilyInfoSheet;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.UserSocialNav;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AgencyUi;
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
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.text.NumberFormat;

public class ProfileActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "user_id";
    private ActivityProfileBinding binding;
    private AppContainer container;
    private boolean isFollowingTarget;
    private AnimatorSet cpHeartAnimator;

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

        // Medal cabinet is shown on the profile itself, beneath the identity and moments rows.
        // Public/custom IDs are resolved to the actual UUID by ProfileViewModel before loading.
        if (binding.rowBadges != null) {
            binding.rowBadges.setVisibility(View.GONE);
            binding.rowBadges.setOnClickListener(v -> {
                String profileId = targetUserIdHold[0];
                if (profileId == null || profileId.trim().isEmpty()) {
                    Toast.makeText(this, "تعذر تحديد الحساب لعرض الميداليات", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent medals = new Intent(this, MedalActivity.class);
                medals.putExtra(EXTRA_USER_ID, profileId);
                startActivity(medals);
            });
        }
        if (binding.rowEarnedBadges != null) binding.rowEarnedBadges.setVisibility(View.GONE);
        // Keep the VIP, charm and wealth chips visible on the light profile card.
        if (binding.rowProfileMedals != null) binding.rowProfileMedals.setVisibility(View.VISIBLE);

        if (binding.rowMoments != null) {
            binding.rowMoments.setOnClickListener(v -> {
                String profileId = targetUserIdHold[0];
                if (profileId == null || profileId.trim().isEmpty()) {
                    Toast.makeText(this, "تعذر تحديد الحساب لعرض اللحظات", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, com.Dramizo.Series.presentation.home.MomentsActivity.class);
                intent.putExtra(com.Dramizo.Series.presentation.home.MomentsActivity.EXTRA_USER_ID, profileId);
                startActivity(intent);
            });
        }

        // Mikoo card taps on full profile page.
        if (binding.cellProfileFollowers != null) {
            binding.cellProfileFollowers.setOnClickListener(v -> {
                String id = targetUserIdHold[0];
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
        if (binding.propProfileSupporters != null) {
            binding.propProfileSupporters.setOnClickListener(v -> {
                String id = targetUserIdHold[0];
                if (id == null || id.isEmpty()) return;
                UserSocialNav.openFollowers(this, id, myId);
            });
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
                        binding.btnFollow.setText(
                                isFollowingTarget ? R.string.unfollow : R.string.follow);
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
        binding.btnCpRequest.setOnClickListener(v -> {
            if (userId == null || myId == null || userId.equals(myId)) return;
            showCpRequestDialog(userId);
        });
        binding.btnFriend.setOnClickListener(v -> {
            if (userId == null) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "هذا حسابك", Toast.LENGTH_SHORT).show();
                return;
            }
            sendSocialRequest(userId, "friend", "صداقة");
        });
        if (binding.btnFriend != null) {
            binding.btnFriend.setOnLongClickListener(v -> {
                if (userId == null || myId == null || userId.equals(myId)) return false;
                AuraDialogHelper.confirm(this,
                        "إلغاء الصداقة",
                        "هل تريد إزالة هذا المستخدم من قائمة الأصدقاء؟",
                        "إلغاء الصداقة",
                        () -> {
                            container.getIoExecutor().execute(() -> {
                                Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                                        container.getUserApi().unfriend(userId));
                                runOnUiThread(() -> {
                                    if (r.success) {
                                        Toast.makeText(this, "تم إلغاء الصداقة", Toast.LENGTH_SHORT).show();
                                        vm.loadUser(userId);
                                    } else {
                                        Toast.makeText(this,
                                                r.error != null ? r.error : getString(R.string.error_generic),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                        },
                        getString(android.R.string.cancel),
                        null);
                return true;
            });
        }
        binding.btnBack.setOnClickListener(v -> navigateUp());
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

        binding.rowGiftHistory.setOnClickListener(v ->
                startActivity(new Intent(this, GiftHistoryActivity.class)));

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
            binding.btnCpRequest.setVisibility(ownProfile ? View.GONE : View.VISIBLE);
            // The mysterious-mode activation control belongs only in Me -> Profile.
            // Never expose an activation/cancel switch from a public profile opened from a room.
            if (binding.rowProfileMysteriousMode != null) {
                binding.rowProfileMysteriousMode.setVisibility(View.GONE);
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
            Glide.with(binding.imgCustomDisplayRank).clear(binding.imgCustomDisplayRank);
            binding.imgCustomDisplayRank.setVisibility(View.GONE);
            GenderVerifiedBadge.bind(binding.tvDisplayName, null, user.genderVerified);
            bindGenderIcon(binding.imgGender, user.gender);

            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "—" : publicId);
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
                loadProfileMedals(user.id);
            }
            if (binding.tvStatFriends != null) binding.tvStatFriends.setText(formatScore(followers));
            if (binding.tvStatFollowing != null) binding.tvStatFollowing.setText(formatScore(following));
            if (binding.tvStatFans != null) binding.tvStatFans.setText(formatScore(visitors));
            if (binding.tvPropProfileSupporters != null) {
                binding.tvPropProfileSupporters.setText(formatScore(followers));
            }
            if (binding.tvPropProfileFriends != null) {
                binding.tvPropProfileFriends.setText(formatScore(friends));
            }
            if (binding.tvStats != null) {
                binding.tvStats.setText(getString(R.string.user_card_stat_following) + " " + followers
                        + " · " + getString(R.string.user_card_stat_fans) + " " + following
                        + " · " + getString(R.string.user_card_stat_visitors) + " " + visitors);
            }
            bindStaffBadge(user);

            // Level chip (same pill as card member chip).
            binding.tvLevelChip.setText(String.valueOf(level));
            binding.tvLevelChip.setBackgroundResource(R.drawable.bg_user_card_id_chip);
            binding.tvLevelChip.setTextColor(0xFF4F3C91);
            binding.tvLevelChip.setCompoundDrawablesRelative(null, null, null, null);

            if (binding.imgCountryFlag != null) {
                FlagImages.bind(binding.imgCountryFlag, user.country);
            }
            // Profile badge cabinet is disabled platform-wide; skip its extra API request.

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
                    user.hostBadgeUrl,
                    user.hostBadgeMeta,
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

            if (user.coverUrl != null && !user.coverUrl.isEmpty()) {
                Glide.with(this)
                        .load(AssetCatalog.absoluteUrl(user.coverUrl))
                        .placeholder(ImagePlaceholder.cover())
                        .into(binding.imgCover);
            } else {
                binding.imgCover.setImageResource(ImagePlaceholder.cover());
            }
            if (myId != null && myId.equals(user.id)) {
                binding.btnFollow.setVisibility(View.GONE);
                if (binding.btnFriend != null) binding.btnFriend.setVisibility(View.GONE);
            } else {
                binding.btnFollow.setVisibility(View.VISIBLE);
                if (binding.btnFriend != null) binding.btnFriend.setVisibility(View.VISIBLE);
                isFollowingTarget = Boolean.TRUE.equals(user.isFollowing);
                binding.btnFollow.setText(
                        isFollowingTarget ? R.string.unfollow : R.string.follow);
            }

            // Only show the agency/family tile when the backend confirms an active
            // membership. Deleted or suspended agencies must disappear from every profile.
            boolean hasActiveAgency = user.agency != null
                    && user.agency.id != null && !user.agency.id.trim().isEmpty()
                    && user.agency.name != null && !user.agency.name.trim().isEmpty();
            String agencyId = hasActiveAgency ? user.agency.id : null;
            if (binding.propProfileFamily != null) {
                binding.propProfileFamily.setVisibility(hasActiveAgency ? View.VISIBLE : View.GONE);
            }
            if (binding.tvProfileAgencyName != null) {
                binding.tvProfileAgencyName.setText(hasActiveAgency
                        ? user.agency.name.trim() : "");
            }
            UserSocialNav.bindFamilyClick(
                    this,
                    binding.propProfileFamily,
                    agencyId,
                    null);
            bindAgencyStrip(hasActiveAgency ? user.agency : null);
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        if (userId != null) vm.loadUser(userId); else vm.loadMe();
    }

    private String formatScore(long value) {
        return NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0))
                .format(Math.max(0, value));
    }

    private void bindStaffBadge(com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (binding == null || binding.tvStaffBadge == null) return;
        boolean rechargeAgent = user.isRechargeAgent;
        boolean agencyOwner = user.agency != null
                && "owner".equalsIgnoreCase(user.agency.role);
        if (!StaffRoleHelper.isStaff(user) && !rechargeAgent && !agencyOwner) {
            binding.tvStaffBadge.setVisibility(View.GONE);
            return;
        }
        String role = StaffRoleHelper.normalize(user);
        String label = StaffRoleHelper.isStaff(user)
                ? StaffRoleHelper.badgeAr(user)
                : (rechargeAgent ? "وكيل شحن" : "وكالة");
        StaffIdentityConfigCache.RoleIdentity identity = StaffIdentityConfigCache.get(role);
        if (StaffRoleHelper.isStaff(user) && !identity.label.isEmpty()) label = identity.label;
        binding.tvStaffBadge.setVisibility(View.VISIBLE);
        binding.tvStaffBadge.setTag(StaffRoleHelper.isStaff(user) ? role : null);
        binding.tvStaffBadge.setText(label);
        binding.tvStaffBadge.setBackgroundResource(
                StaffRoleHelper.SUPER.equals(role)
                        ? R.drawable.bg_chip_staff_super
                        : R.drawable.bg_chip_staff_manager);
        if (StaffRoleHelper.isStaff(user)) {
            final String fallbackLabel = StaffRoleHelper.badgeAr(user);
            StaffIdentityConfigCache.load(this, () -> {
                if (binding == null || binding.tvStaffBadge == null || !role.equals(binding.tvStaffBadge.getTag())) return;
                StaffIdentityConfigCache.RoleIdentity latest = StaffIdentityConfigCache.get(role);
                binding.tvStaffBadge.setText(latest.label.isEmpty() ? fallbackLabel : latest.label);
                if (!latest.frameUrl.isEmpty() && binding.imgFrame != null) {
                    try {
                        binding.imgFrame.setVisibility(View.VISIBLE);
                        com.bumptech.glide.Glide.with(this)
                                .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(latest.frameUrl))
                                .into(binding.imgFrame);
                    } catch (Exception ignored) { }
                }
            });
        }
    }

    private void bindAgencyStrip(
            @androidx.annotation.Nullable
            com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto.AgencySnip agency) {
        if (binding == null || binding.rowProfileAgency == null) return;
        if (agency == null || agency.name == null || agency.name.trim().isEmpty()) {
            binding.rowProfileAgency.setVisibility(View.GONE);
            return;
        }
        binding.rowProfileAgency.setVisibility(View.VISIBLE);
        if (binding.tvAgencyName != null) {
            binding.tvAgencyName.setText(agency.name.trim());
        }
        if (binding.tvAgencyGid != null) {
            String gid = agency.publicId != null ? agency.publicId.trim() : "";
            binding.tvAgencyGid.setVisibility(View.VISIBLE);
            binding.tvAgencyGid.setText(gid.isEmpty() ? "GID: —" : ("GID:" + gid));
        }
        int tier = agency.level > 0
                ? agency.level
                : AgencyUi.bannerTierFromDiamonds(agency.totalDiamonds);
        AgencyUi.bindBanner(binding.imgAgencyBannerBg, binding.tvAgencyLevel, tier);
        if (binding.imgAgencyLogo != null) {
            AgencyUi.bindLogo(binding.imgAgencyLogo, agency.logoUrl, agency.coverUrl);
        }
        final String agencyId = agency.id;
        binding.rowProfileAgency.setOnClickListener(v -> {
            if (agencyId == null || agencyId.isEmpty()) return;
            try {
                AgencyFamilyInfoSheet.show(
                        this, agencyId, agency.logoUrl, agency.coverUrl);
            } catch (Exception ignored) {
            }
        });
    }

    private void bindMysteriousMode(boolean ownProfile) {
        if (binding == null || binding.rowProfileMysteriousMode == null) return;
        binding.rowProfileMysteriousMode.setVisibility(ownProfile ? View.VISIBLE : View.GONE);
        if (!ownProfile) return;

        binding.switchMysteriousMode.setOnCheckedChangeListener(null);
        binding.switchMysteriousMode.setEnabled(false);
        binding.tvMysteriousModeHint.setText("جارٍ التحقق من اشتراك VIP...");
        new Thread(() -> {
            Result<Map<String, Object>> result = container.getVipRepository().mysteriousModeStatus();
            runOnUiThread(() -> {
                if (binding == null || isFinishing()) return;
                if (result == null || !result.success || result.data == null) {
                    binding.tvMysteriousModeHint.setText("تعذر تحميل الحالة، حاول مجددًا لاحقًا");
                    binding.switchMysteriousMode.setEnabled(false);
                    return;
                }
                Map<String, Object> status = result.data;
                boolean eligible = Boolean.TRUE.equals(status.get("eligible"));
                boolean enabled = Boolean.TRUE.equals(status.get("enabled"));
                binding.switchMysteriousMode.setOnCheckedChangeListener(null);
                binding.switchMysteriousMode.setChecked(enabled);
                binding.switchMysteriousMode.setEnabled(eligible);
                binding.tvMysteriousModeHint.setText(eligible
                        ? (enabled ? "مفعّل — هويتك مخفية داخل الغرف" : "متاح لاشتراكك — يمكنك تفعيله أو إلغاؤه")
                        : "يتطلب اشتراك VIP يتضمن ميزة الرجل الغامض");
                binding.switchMysteriousMode.setOnCheckedChangeListener((button, checked) -> {
                    button.setEnabled(false);
                    new Thread(() -> {
                        Result<Map<String, Object>> changed = container.getVipRepository().setMysteriousMode(checked);
                        runOnUiThread(() -> {
                            if (binding == null || isFinishing()) return;
                            button.setEnabled(eligible);
                            if (changed != null && changed.success && changed.data != null) {
                                boolean actual = Boolean.TRUE.equals(changed.data.get("enabled"));
                                button.setOnCheckedChangeListener(null);
                                button.setChecked(actual);
                                button.setOnCheckedChangeListener((b, value) -> {
                                    b.setEnabled(false);
                                    new Thread(() -> {
                                        Result<Map<String, Object>> next = container.getVipRepository().setMysteriousMode(value);
                                        runOnUiThread(() -> {
                                            if (binding == null || isFinishing()) return;
                                            b.setEnabled(eligible);
                                            if (next != null && next.success && next.data != null) {
                                                boolean saved = Boolean.TRUE.equals(next.data.get("enabled"));
                                                b.setOnCheckedChangeListener(null);
                                                b.setChecked(saved);
                                                binding.tvMysteriousModeHint.setText(saved
                                                        ? "مفعّل — هويتك مخفية داخل الغرف"
                                                        : "متاح لاشتراكك — يمكنك تفعيله أو إلغاؤه");
                                                bindMysteriousModeListener(eligible);
                                            } else {
                                                b.setOnCheckedChangeListener(null);
                                                b.setChecked(!value);
                                                bindMysteriousModeListener(eligible);
                                                Toast.makeText(ProfileActivity.this, "تعذر حفظ الإعداد، حاول مرة أخرى", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }).start();
                                });
                                binding.tvMysteriousModeHint.setText(actual
                                        ? "مفعّل — هويتك مخفية داخل الغرف"
                                        : "متاح لاشتراكك — يمكنك تفعيله أو إلغاؤه");
                            } else {
                                button.setOnCheckedChangeListener(null);
                                button.setChecked(!checked);
                                bindMysteriousModeListener(eligible);
                                Toast.makeText(ProfileActivity.this, "تعذر حفظ الإعداد، حاول مرة أخرى", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                });
            });
        }).start();
    }

    private void bindMysteriousModeListener(boolean eligible) {
        if (binding == null || binding.switchMysteriousMode == null) return;
        binding.switchMysteriousMode.setOnCheckedChangeListener((button, checked) -> {
            button.setEnabled(false);
            new Thread(() -> {
                Result<Map<String, Object>> changed = container.getVipRepository().setMysteriousMode(checked);
                runOnUiThread(() -> {
                    if (binding == null || isFinishing()) return;
                    button.setEnabled(eligible);
                    if (changed != null && changed.success && changed.data != null) {
                        boolean saved = Boolean.TRUE.equals(changed.data.get("enabled"));
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(saved);
                        binding.tvMysteriousModeHint.setText(saved
                                ? "مفعّل — هويتك مخفية داخل الغرف"
                                : "متاح لاشتراكك — يمكنك تفعيله أو إلغاؤه");
                        bindMysteriousModeListener(eligible);
                    } else {
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(!checked);
                        bindMysteriousModeListener(eligible);
                        Toast.makeText(ProfileActivity.this, "تعذر حفظ الإعداد، حاول مرة أخرى", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
    }

    private void bindVipHead(
            @androidx.annotation.Nullable ImageView vipHead,
            int vipLevel,
            @androidx.annotation.Nullable String headUrl) {
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
        ServerAssets.load(vipHead, path);
    }

    private void bindVipMedal(
            @androidx.annotation.Nullable ImageView medal,
            int vipLevel,
            @androidx.annotation.Nullable String levelBadgeUrl) {
        // Medal visuals are intentionally hidden with the retired profile badge cabinet.
        if (medal != null) {
            medal.setVisibility(View.GONE);
            medal.setImageDrawable(null);
        }
    }

    private void bindEntryRide(
            @androidx.annotation.Nullable ImageView rideView,
            @androidx.annotation.Nullable String entryUrl) {
        if (rideView == null) return;
        String url = entryUrl != null ? entryUrl.trim() : "";
        if (url.isEmpty()) {
            rideView.setVisibility(View.GONE);
            rideView.setImageDrawable(null);
            return;
        }
        String preview = url;
        if (preview.endsWith(".mp4") || preview.endsWith(".svga")) {
            int dot = preview.lastIndexOf('.');
            if (dot > 0) preview = preview.substring(0, dot) + ".png";
        }
        rideView.setVisibility(View.VISIBLE);
        try {
            Glide.with(this)
                    .load(AssetCatalog.absoluteUrl(preview))
                    .error(R.drawable.ic_medal_default)
                    .into(rideView);
        } catch (Exception e) {
            rideView.setImageResource(R.drawable.ic_medal_default);
        }
    }

    private void bindGenderIcon(
            @androidx.annotation.Nullable ImageView view,
            @androidx.annotation.Nullable String gender) {
        if (view == null) return;
        if (gender == null || gender.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        String g = gender.trim().toLowerCase();
        if ("male".equals(g) || "m".equals(g) || "ذكر".equals(gender)) {
            view.setVisibility(View.VISIBLE);
            view.setImageResource(R.drawable.ic_gender_male);
        } else if ("female".equals(g) || "f".equals(g)
                || "أنثى".equals(gender) || "انثى".equals(gender)) {
            view.setVisibility(View.VISIBLE);
            view.setImageResource(R.drawable.ic_gender_female);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    @androidx.annotation.Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private void showCpRequestDialog(String recipientId) {
        final String[] types = {"friendship", "brotherhood", "love", "engagement", "marriage"};
        final String[] labels = {"الصداقة", "الأخوّة", "الحب", "الخطوبة", "الزواج"};
        new AlertDialog.Builder(this).setTitle("اختر نوع العلاقة")
                .setItems(labels, (dialog, which) -> {
                    Map<String, String> body = new HashMap<>();
                    body.put("recipientId", recipientId);
                    body.put("type", types[which]);
                    body.put("idempotencyKey", UUID.randomUUID().toString());
                    binding.btnCpRequest.setEnabled(false);
                    container.getIoExecutor().execute(() -> {
                        Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                                container.getUserApi().createProfileCpRequest(body));
                        runOnUiThread(() -> {
                            binding.btnCpRequest.setEnabled(true);
                            Toast.makeText(this, result.success ? "تم إرسال طلب " + labels[which] + " ✓"
                                    : (result.error != null ? result.error : "تعذر إرسال الطلب"),
                                    Toast.LENGTH_LONG).show();
                        });
                    });
                }).setNegativeButton("إلغاء", null).show();
    }

    private void loadPublicCpRelationship(String targetId, String ownAvatarUrl, String ownFrameUrl,
            int ownVipLevel, String ownVipTouUrl, String ownHostBadgeUrl,
            Map<String, Object> ownHostBadgeMeta) {
        if (targetId == null || targetId.trim().isEmpty()) {
            hideProfileCpRelationship();
            return;
        }
        container.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().cpProfileRelationships(targetId));
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (!result.success || result.data == null || result.data.isEmpty()) {
                    // Older accounts may still have their active CP stored in the legacy
                    // social-bond ledger. Preserve that relationship instead of hiding it
                    // just because the newer cp_relationships table has no row yet.
                    loadLegacyOwnCpRelationship(targetId, ownAvatarUrl, ownFrameUrl);
                    return;
                }
                Map<String, Object> relation = result.data.get(0);
                // The public CP endpoint has returned several compatible payload shapes over
                // time. Resolve the linked account from explicit partner fields so every entry
                // point (search, ranking, chat, room, and direct profile) uses the same real data.
                String relationStatus = relation.get("status") == null ? ""
                        : String.valueOf(relation.get("status")).trim().toLowerCase(java.util.Locale.ROOT);
                if (Boolean.FALSE.equals(relation.get("isActive"))
                        || Boolean.FALSE.equals(relation.get("active"))
                        || relation.get("endedAt") != null
                        || relationStatus.contains("ended") || relationStatus.contains("reject")
                        || relationStatus.contains("expired") || relationStatus.contains("pending")
                        || relationStatus.contains("inactive")) {
                    hideProfileCpRelationship();
                    return;
                }
                Object partnerObj = relation.get("partner");
                if (!(partnerObj instanceof Map)) partnerObj = relation.get("partnerUser");
                if (!(partnerObj instanceof Map)) partnerObj = relation.get("linkedUser");
                if (!(partnerObj instanceof Map)) partnerObj = relation.get("relatedUser");
                if (!(partnerObj instanceof Map)) partnerObj = relation.get("otherUser");
                if (!(partnerObj instanceof Map)) partnerObj = relation.get("counterpart");
                Map<?, ?> partner = partnerObj instanceof Map ? (Map<?, ?>) partnerObj : null;
                // Compatibility fallback for older API/search payloads that flatten the
                // partner fields instead of returning the nested "partner" object.
                if (partner == null) {
                    Map<String, Object> flattenedPartner = new HashMap<>();
                    Object flattenedId = firstNonEmpty(
                            relation.get("partnerId") == null ? null : String.valueOf(relation.get("partnerId")),
                            relation.get("linkedUserId") == null ? null : String.valueOf(relation.get("linkedUserId")),
                            relation.get("relatedUserId") == null ? null : String.valueOf(relation.get("relatedUserId")),
                            relation.get("otherUserId") == null ? null : String.valueOf(relation.get("otherUserId")),
                            relation.get("counterpartId") == null ? null : String.valueOf(relation.get("counterpartId")));
                    if (flattenedId != null) flattenedPartner.put("id", flattenedId);
                    Object flattenedName = firstNonEmpty(
                            relation.get("partnerDisplayName") == null ? null : String.valueOf(relation.get("partnerDisplayName")),
                            relation.get("partnerUsername") == null ? null : String.valueOf(relation.get("partnerUsername")));
                    if (flattenedName != null) flattenedPartner.put("displayName", flattenedName);
                    Object flattenedAvatar = firstNonEmpty(
                            relation.get("partnerAvatarUrl") == null ? null : String.valueOf(relation.get("partnerAvatarUrl")),
                            relation.get("partner_avatar_url") == null ? null : String.valueOf(relation.get("partner_avatar_url")),
                            relation.get("partnerProfileImageUrl") == null ? null : String.valueOf(relation.get("partnerProfileImageUrl")),
                            relation.get("partnerPhotoUrl") == null ? null : String.valueOf(relation.get("partnerPhotoUrl")),
                            relation.get("partnerPicture") == null ? null : String.valueOf(relation.get("partnerPicture")),
                            relation.get("partnerImageUrl") == null ? null : String.valueOf(relation.get("partnerImageUrl")));
                    if (flattenedAvatar != null) flattenedPartner.put("avatarUrl", flattenedAvatar);
                    Object flattenedFrame = firstNonEmpty(
                            relation.get("partnerFrameUrl") == null ? null : String.valueOf(relation.get("partnerFrameUrl")),
                            relation.get("partnerVipBadgeUrl") == null ? null : String.valueOf(relation.get("partnerVipBadgeUrl")));
                    if (flattenedFrame != null) flattenedPartner.put("vipBadgeUrl", flattenedFrame);
                    if (!flattenedPartner.isEmpty()) partner = flattenedPartner;
                }
                String name = "";
                String avatar = "";
                String partnerFrameUrl = "";
                String partnerHostBadgeUrl = "";
                Map<String, ?> partnerHostBadgeMeta = null;
                int partnerVipLevel = 0;
                String partnerVipTouUrl = "";
                String type = relation.get("type") == null ? "" : String.valueOf(relation.get("type")).trim();
                if (partner != null) {
                    Object displayName = partner.get("displayName");
                    Object username = partner.get("username");
                    name = displayName != null && !String.valueOf(displayName).trim().isEmpty()
                            ? String.valueOf(displayName).trim()
                            : username != null ? String.valueOf(username).trim() : "";
                    Object avatarValue = firstNonEmpty(
                            partner.get("avatarUrl") == null ? null : String.valueOf(partner.get("avatarUrl")),
                            partner.get("avatar") == null ? null : String.valueOf(partner.get("avatar")),
                            partner.get("profileImageUrl") == null ? null : String.valueOf(partner.get("profileImageUrl")),
                            partner.get("photoUrl") == null ? null : String.valueOf(partner.get("photoUrl")),
                            partner.get("picture") == null ? null : String.valueOf(partner.get("picture")),
                            partner.get("imageUrl") == null ? null : String.valueOf(partner.get("imageUrl")),
                            partner.get("profilePicture") == null ? null : String.valueOf(partner.get("profilePicture")),
                            partner.get("photo") == null ? null : String.valueOf(partner.get("photo")),
                            partner.get("avatar_url") == null ? null : String.valueOf(partner.get("avatar_url")),
                            partner.get("profile_image_url") == null ? null : String.valueOf(partner.get("profile_image_url")));
                    avatar = avatarValue == null ? "" : String.valueOf(avatarValue).trim();
                    Object frameValue = partner.get("vipBadgeUrl");
                    partnerFrameUrl = frameValue == null ? "" : String.valueOf(frameValue).trim();
                    Object vipLevelValue = partner.get("vipLevel");
                    partnerVipLevel = parsePositiveInt(vipLevelValue, 0);
                    Object vipTouValue = partner.get("vipTouUrl");
                    partnerVipTouUrl = vipTouValue == null ? "" : String.valueOf(vipTouValue).trim();
                    Object hostBadgeValue = partner.get("hostBadgeUrl");
                    partnerHostBadgeUrl = hostBadgeValue == null ? "" : String.valueOf(hostBadgeValue).trim();
                    Object hostMetaValue = partner.get("hostBadgeMeta");
                    if (hostMetaValue instanceof Map) {
                        partnerHostBadgeMeta = (Map<String, ?>) hostMetaValue;
                    }
                    // Match the same profile-frame resolution used for the profile owner:
                    // equipped frame first, otherwise the partner's real VIP nobility frame.
                    if (partnerFrameUrl.isEmpty()) {
                        String vipFrame = VipStyle.profileNobilityFrameUrl(partnerVipLevel, partnerVipTouUrl);
                        partnerFrameUrl = vipFrame == null ? "" : vipFrame;
                    }
                }
                // Only the active relationship and its real partner payload are required;
                // the top pair intentionally shows no partner name or relationship text.
                if (partner == null) {
                    hideProfileCpRelationship();
                    return;
                }
                binding.tvProfileCpLabel.setVisibility(View.GONE);
                // The profile owner is the first avatar; the actual linked account is the
                // second. Keep this same pair when the profile is opened from search,
                // ranking, chat, or a direct profile link.
                String resolvedOwnFrame = ownFrameUrl == null ? "" : ownFrameUrl.trim();
                if (resolvedOwnFrame.isEmpty()) {
                    String vipFrame = VipStyle.profileNobilityFrameUrl(ownVipLevel, ownVipTouUrl);
                    resolvedOwnFrame = vipFrame == null ? "" : vipFrame;
                }
                // These two avatar views start as GONE in the shared profile layout.
                // Make them visible before binding; AvatarCosmetics loads the real server URL
                // and wear, but intentionally does not change the caller's visibility.
                binding.imgProfileCpSelf.setVisibility(View.VISIBLE);
                binding.imgProfileCpPartner.setVisibility(View.VISIBLE);
                com.Dramizo.Series.util.AvatarCosmetics.bindWear(
                        binding.imgProfileCpSelf,
                        binding.imgProfileCpSelfFrame,
                        ownAvatarUrl,
                        resolvedOwnFrame,
                        ownHostBadgeUrl,
                        null,
                        ownHostBadgeMeta);
                // The partner's frame comes from that linked account's real equipped wear,
                // with the same VIP fallback used on their own profile.
                com.Dramizo.Series.util.AvatarCosmetics.bindWear(
                        binding.imgProfileCpPartner,
                        binding.imgProfileCpPartnerFrame,
                        avatar,
                        partnerFrameUrl,
                        partnerHostBadgeUrl,
                        null,
                        partnerHostBadgeMeta);

                // Mirror the same live relationship into the decorative CV card.
                bindRealCpAvatar(binding.imgProfileCpCvSelf, ownAvatarUrl);
                bindRealCpAvatar(binding.imgProfileCpCvPartner, avatar);
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(binding.imgProfileCpCvSelfFrame, resolvedOwnFrame);
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(binding.imgProfileCpCvPartnerFrame, partnerFrameUrl);
                int relationshipLevel = parsePositiveInt(relation.get("level"), 1);
                long relationshipDays = cpDurationDays(relation.get("startedAt"));
                binding.tvProfileCpCvType.setText(cpTypeLabel(type));
                binding.tvProfileCpCvHeart.setText("♥");
                Object cardBackground = relation.get("cardBackgroundUrl");
                applyCpCardBackground(cardBackground == null ? "" : String.valueOf(cardBackground));
                binding.tvProfileCpCvLevel.setText("Lv." + relationshipLevel);
                binding.tvProfileCpCvDays.setText(relationshipDays + " أيام");
                binding.rowProfileCpCv.setVisibility(View.VISIBLE);

                // Show exactly two portraits in the upper profile area: reuse the larger
                // profile portrait slot for the linked pair instead of leaving a third copy.
                if (binding.profileAvatarWrap != null) binding.profileAvatarWrap.setVisibility(View.GONE);
                binding.rowProfileCp.setVisibility(View.VISIBLE);
                binding.btnCpRequest.setVisibility(View.GONE);
                startProfileCpHeartAnimation();
            });
        });
    }

    /**
     * Backward-compatible display for the signed-in user's CP from the legacy social-bond API.
     * This is read-only: it does not create, migrate, or change any relationship data.
     */
    private void loadLegacyOwnCpRelationship(String targetId, String ownAvatarUrl, String ownFrameUrl) {
        String myId = container != null ? container.getSessionManager().getUserId() : null;
        if (myId == null || targetId == null || !myId.equals(targetId)) {
            hideProfileCpRelationship();
            return;
        }
        container.getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.CpStatusDto> legacy =
                    com.Dramizo.Series.util.ApiCall.execute(container.getUserApi().myCp());
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (!legacy.success || legacy.data == null || !legacy.data.hasCp
                        || legacy.data.partner == null) {
                    hideProfileCpRelationship();
                    return;
                }
                AuthDtos.UserDto partner = legacy.data.partner;
                bindRealCpAvatar(binding.imgProfileCpSelf, ownAvatarUrl);
                bindRealCpAvatar(binding.imgProfileCpPartner, partner.avatarUrl);
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(
                        binding.imgProfileCpSelfFrame, ownFrameUrl);
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(
                        binding.imgProfileCpPartnerFrame, partner.vipBadgeUrl);
                binding.tvProfileCpLabel.setVisibility(View.GONE);
                binding.tvProfileCpCvType.setText("♥ علاقة CP");
                binding.tvProfileCpCvHeart.setText("♥");
                binding.tvProfileCpCvLevel.setText("Lv." + Math.max(1, legacy.data.level));
                binding.tvProfileCpCvDays.setText("علاقة نشطة");
                applyCpCardBackground("");
                binding.rowProfileCpCv.setVisibility(View.VISIBLE);
                if (binding.profileAvatarWrap != null) binding.profileAvatarWrap.setVisibility(View.GONE);
                binding.rowProfileCp.setVisibility(View.VISIBLE);
                binding.btnCpRequest.setVisibility(View.GONE);
                startProfileCpHeartAnimation();
            });
        });
    }

    private void applyCpCardBackground(String url) {
        if (binding == null || binding.rowProfileCpCv == null) return;
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) {
            binding.rowProfileCpCv.setBackgroundResource(R.drawable.home_bg_cp);
            return;
        }
        Glide.with(this).load(value).into(new CustomTarget<Drawable>() {
            @Override public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                if (binding != null && !isFinishing()) {
                    // Set only the card background; layout bounds and child positions remain unchanged.
                    binding.rowProfileCpCv.setBackground(resource);
                }
            }
            @Override public void onLoadCleared(Drawable placeholder) {
                if (binding != null) binding.rowProfileCpCv.setBackgroundResource(R.drawable.home_bg_cp);
            }
        });
    }

    private int parsePositiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long cpDurationDays(Object startedAtValue) {
        if (startedAtValue == null) return 0L;
        java.util.Date startedAt = null;
        if (startedAtValue instanceof java.util.Date) {
            startedAt = (java.util.Date) startedAtValue;
        } else {
            String raw = String.valueOf(startedAtValue).trim();
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                    "yyyy-MM-dd'T'HH:mm:ssX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd"
            };
            for (String pattern : patterns) {
                try {
                    java.text.SimpleDateFormat format =
                            new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                    format.setLenient(false);
                    format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    startedAt = format.parse(raw);
                    if (startedAt != null) break;
                } catch (Exception ignored) { }
            }
        }
        if (startedAt == null) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - startedAt.getTime()) / 86400000L);
    }

    private void bindRealCpAvatar(ImageView imageView, String avatarUrl) {
        if (imageView == null) return;
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.GONE);
            return;
        }
        imageView.setVisibility(View.VISIBLE);
        // Shared loader normalizes relative API paths, retries failed placeholders correctly,
        // and preserves animated GIF/WebP profile photos.
        com.Dramizo.Series.util.AvatarImageLoader.load(imageView, avatarUrl.trim());
    }

    private void startProfileCpHeartAnimation() {
        if (binding == null || binding.tvProfileCpHeart == null) return;
        if (cpHeartAnimator != null) cpHeartAnimator.cancel();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.tvProfileCpHeart, View.SCALE_X, 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.tvProfileCpHeart, View.SCALE_Y, 1f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(binding.tvProfileCpHeart, View.ALPHA, 0.72f, 1f, 0.72f);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            animator.setDuration(900L);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
        }
        cpHeartAnimator = new AnimatorSet();
        cpHeartAnimator.playTogether(scaleX, scaleY, alpha);
        cpHeartAnimator.start();
    }

    private void hideProfileCpRelationship() {
        if (binding == null) return;
        if (cpHeartAnimator != null) {
            cpHeartAnimator.cancel();
            cpHeartAnimator = null;
        }
        binding.rowProfileCp.setVisibility(View.GONE);
        if (binding.profileAvatarWrap != null) binding.profileAvatarWrap.setVisibility(View.VISIBLE);
        if (binding.rowProfileCpCv != null) {
            binding.rowProfileCpCv.setVisibility(View.GONE);
        }
    }

    private String cpTypeLabel(String type) {
        switch (type) {
            case "friendship": return "✦ علاقة صداقة";
            case "brotherhood": return "♛ علاقة أخوّة";
            case "love": return "♥ علاقة حب";
            case "engagement": return "💍 علاقة خطوبة";
            case "marriage": return "💞 علاقة زواج";
            default: return "علاقة CP";
        }
    }

    /** Render the server-saved medal showcase under the public ID for every viewer. */
    private void loadProfileMedals(String profileId) {
        if (profileId == null || profileId.trim().isEmpty() || binding == null) return;
        AppContainer c = container != null ? container : ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            AuthDtos.UserDto viewed = null;
            List<com.Dramizo.Series.data.remote.dto.BadgeDto> earned = java.util.Collections.emptyList();
            try {
                retrofit2.Response<com.Dramizo.Series.data.remote.dto.ApiResponse<AuthDtos.UserDto>> ur =
                        c.getUserApi().getUser(profileId).execute();
                if (ur.isSuccessful() && ur.body() != null) viewed = ur.body().data;
            } catch (Exception ignored) { }
            try {
                retrofit2.Response<com.Dramizo.Series.data.remote.dto.ApiResponse<List<com.Dramizo.Series.data.remote.dto.BadgeDto>>> br =
                        c.getUserApi().earnedBadges(profileId).execute();
                if (br.isSuccessful() && br.body() != null && br.body().data != null) earned = br.body().data;
            } catch (Exception ignored) { }
            final AuthDtos.UserDto profile = viewed;
            final List<com.Dramizo.Series.data.remote.dto.BadgeDto> owned = earned;
            runOnUiThread(() -> {
                if (binding == null || isFinishing()) return;
                LinearLayout strip = findViewById(R.id.medalShowcaseItems);
                View showcase = findViewById(R.id.medalShowcase);
                if (strip == null || showcase == null) return;
                strip.removeAllViews();
                List<String> selected = profile != null && profile.displayedMedalIds != null
                        ? profile.displayedMedalIds : java.util.Collections.emptyList();
                // Always refresh the showcase after profile data arrives; an empty selection
                // must not leave a stale hidden row when switching between profile owners.
                showcase.setVisibility(View.GONE);
                if (!selected.isEmpty()) {
                    for (String key : selected) {
                        if (key == null) continue;
                        String imageUrl = null;
                        String label = "ميدالية";
                        if (key.startsWith("vip:")) {
                            int vip = profile != null ? Math.max(0, profile.vipLevel) : 0;
                            if (vip <= 0) {
                                try { vip = Integer.parseInt(key.substring(4)); } catch (Exception ignored) { }
                            }
                            if (vip <= 0 || vip > 7) continue;
                            imageUrl = profile != null && profile.levelBadgeUrl != null
                                    && !profile.levelBadgeUrl.trim().isEmpty()
                                    ? profile.levelBadgeUrl
                                    : "/assets/visual-system/vip/vip_medal_mikoo_" + vip + ".png";
                            label = "VIP " + vip;
                        } else {
                            for (com.Dramizo.Series.data.remote.dto.BadgeDto badge : owned) {
                                if (badge != null && badge.earned && key.equals(badge.id)
                                        && badge.imageUrl != null && !badge.imageUrl.trim().isEmpty()) {
                                    imageUrl = badge.imageUrl;
                                    label = badge.name == null ? "ميدالية" : badge.name;
                                    break;
                                }
                            }
                        }
                        if (imageUrl == null) continue;
                        LinearLayout item = new LinearLayout(this);
                        item.setOrientation(LinearLayout.VERTICAL);
                        item.setGravity(Gravity.CENTER);
                        int width = Math.round(58 * getResources().getDisplayMetrics().density);
                        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                        itemLp.setMargins(0, 0, Math.round(8 * getResources().getDisplayMetrics().density), 0);
                        item.setLayoutParams(itemLp);
                        ImageView image = new ImageView(this);
                        int size = Math.round(44 * getResources().getDisplayMetrics().density);
                        image.setLayoutParams(new LinearLayout.LayoutParams(size, size));
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        image.setContentDescription(label);
                        item.addView(image);
                        TextView caption = new TextView(this);
                        caption.setText(label);
                        caption.setTextColor(0xFF697187);
                        caption.setTextSize(9);
                        caption.setGravity(Gravity.CENTER);
                        caption.setMaxLines(1);
                        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        item.addView(caption);
                        strip.addView(item);
                        Glide.with(this).load(AssetCatalog.absoluteUrl(imageUrl))
                                .placeholder(R.drawable.ic_medal_default)
                                .error(R.drawable.ic_medal_default).into(image);
                    }
                    showcase.setVisibility(strip.getChildCount() > 0 ? View.VISIBLE : View.GONE);
                }
                // Keep the existing earned-medal cabinet populated for backward compatibility.
                List<com.Dramizo.Series.data.remote.dto.BadgeDto> badges =
                        owned == null ? java.util.Collections.emptyList() : owned;
                binding.rowBadges.setVisibility(View.VISIBLE);
                binding.tvBadgeCount.setText(badges.size() + " مكتسبة");
                binding.rowEarnedBadges.removeAllViews();
                if (badges.isEmpty()) {
                    binding.rowEarnedBadges.setVisibility(View.GONE);
                    binding.tvBadgesEmpty.setVisibility(View.VISIBLE);
                    binding.tvBadgesEmpty.setText("لا توجد ميداليات مكتسبة بعد");
                } else {
                    binding.tvBadgesEmpty.setVisibility(View.GONE);
                    binding.rowEarnedBadges.setVisibility(View.VISIBLE);
                    for (com.Dramizo.Series.data.remote.dto.BadgeDto badge : badges) {
                        if (badge == null || !badge.earned || badge.imageUrl == null || badge.imageUrl.trim().isEmpty()) continue;
                        ImageView image = new ImageView(this);
                        int size = Math.round(34 * getResources().getDisplayMetrics().density);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                        lp.setMargins(0, 0, Math.round(8 * getResources().getDisplayMetrics().density), 0);
                        image.setLayoutParams(lp);
                        image.setContentDescription(badge.name == null ? "ميدالية" : badge.name);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        binding.rowEarnedBadges.addView(image);
                        Glide.with(this).load(AssetCatalog.absoluteUrl(badge.imageUrl))
                                .placeholder(R.drawable.ic_medal_default)
                                .error(R.drawable.ic_medal_default).into(image);
                    }
                }
            });
        });
    }

    private void sendSocialRequest(String targetId, String type, String labelAr) {
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Map<String, String> body = new HashMap<>();
            body.put("type", type);
            Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().sendRequest(targetId, body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, "تم إرسال طلب " + labelAr, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && binding.webProfileHostSignal != null
                && binding.webProfileHostSignal.getVisibility() == android.view.View.VISIBLE) {
            binding.webProfileHostSignal.resumeMotion();
        }
    }

    @Override
    protected void onDestroy() {
        if (cpHeartAnimator != null) {
            cpHeartAnimator.cancel();
            cpHeartAnimator = null;
        }
        if (binding != null && binding.webProfileHostSignal != null) {
            binding.webProfileHostSignal.destroy();
        }
        super.onDestroy();
    }
}
