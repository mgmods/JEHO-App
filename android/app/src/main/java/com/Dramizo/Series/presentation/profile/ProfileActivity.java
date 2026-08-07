package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
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
import com.Dramizo.Series.util.VipStyle;
import com.bumptech.glide.Glide;

import java.util.HashMap;
import java.util.Map;
import java.text.NumberFormat;

public class ProfileActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "user_id";
    private ActivityProfileBinding binding;
    private boolean isFollowingTarget;

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
        AppContainer container = ContainerProvider.from(this);
        ProfileViewModel vm = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        String myId = container.getSessionManager().getUserId();
        final String[] targetUserIdHold = {
                userId != null && !userId.isEmpty() ? userId : (myId != null ? myId : "")
        };

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
            if (userId == null) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "هذا حسابك", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnFollow.setEnabled(false);
            final boolean unfollow = isFollowingTarget;
            container.getIoExecutor().execute(() -> {
                Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                        unfollow
                                ? container.getUserApi().unfollow(userId)
                                : container.getUserApi().follow(userId));
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

        vm.getUser().observe(this, user -> {
            if (user == null) return;
            int vip = Math.max(0, user.vipLevel);
            int level = Math.max(1, user.level);

            // Same dark VIP sheet wash as room user card.
            if (binding.userCardBg != null) {
                AgencyUi.applyUserCardSheet(binding.userCardBg, null, vip);
            }

            binding.tvDisplayName.setText(
                    user.displayName != null && !user.displayName.isEmpty()
                            ? user.displayName
                            : "مستخدم");
            GenderVerifiedBadge.bind(binding.tvDisplayName, null, user.genderVerified);
            bindGenderIcon(binding.imgGender, user.gender);

            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "ID:—" : ("ID:" + publicId));
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
            binding.tvLevelChip.setTextColor(0xFFE0F7FA);
            binding.tvLevelChip.setCompoundDrawablesRelative(null, null, null, null);

            if (binding.imgCountryFlag != null) {
                FlagImages.bind(binding.imgCountryFlag, user.country);
            }

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

            bindVipHead(
                    binding.imgVipHead,
                    vip,
                    user.vipHeadUrl != null ? user.vipHeadUrl : VipStyle.fixedHeadPath(vip));
            bindVipMedal(binding.imgVipMedal, vip, user.levelBadgeUrl);
            bindEntryRide(
                    binding.imgEntryRide,
                    firstNonEmpty(user.entryAnimationUrl, user.entryEffectUrl));

            // Profile: fixed VIP nobility frame (ud_vip_tou); mall SVGA stays on mics only.
            String nobilityFrame = VipStyle.profileNobilityFrameUrl(vip, user.vipTouUrl);
            String profileFrame = nobilityFrame != null ? nobilityFrame : user.vipBadgeUrl;
            AvatarCosmetics.bindProfileWear(
                    binding.webProfileHostSignal,
                    binding.imgAvatar,
                    binding.imgFrame,
                    user.avatarUrl,
                    profileFrame,
                    user.hostBadgeUrl,
                    user.hostBadgeMeta,
                    1);
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
                binding.tvLastSeen.setTextColor(0xFF7CFFB2);
            } else if (user.lastSeenAt != null && !user.lastSeenAt.isEmpty()) {
                binding.tvLastSeen.setVisibility(View.VISIBLE);
                binding.tvLastSeen.setText(DeviceTimeFormat.lastSeen(this, user.lastSeenAt));
                binding.tvLastSeen.setTextColor(0x99FFFFFF);
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

            // Property strip
            String agencyId = null;
            if (binding.tvProfileAgencyName != null) {
                if (user.agency != null
                        && user.agency.name != null
                        && !user.agency.name.isEmpty()) {
                    binding.tvProfileAgencyName.setText(user.agency.name.trim());
                } else {
                    binding.tvProfileAgencyName.setText(R.string.user_card_prop_family);
                }
            }
            if (user.agency != null && user.agency.id != null && !user.agency.id.isEmpty()) {
                agencyId = user.agency.id;
            }
            UserSocialNav.bindFamilyClick(
                    this,
                    binding.propProfileFamily,
                    agencyId,
                    null);
            bindAgencyStrip(user.agency);
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
        if (!StaffRoleHelper.isStaff(user)) {
            binding.tvStaffBadge.setVisibility(View.GONE);
            return;
        }
        String role = StaffRoleHelper.normalize(user);
        binding.tvStaffBadge.setVisibility(View.VISIBLE);
        binding.tvStaffBadge.setText(StaffRoleHelper.badgeAr(user));
        binding.tvStaffBadge.setBackgroundResource(
                StaffRoleHelper.SUPER.equals(role)
                        ? R.drawable.bg_chip_staff_super
                        : R.drawable.bg_chip_staff_manager);
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
        ServerAssets.load(medal, path);
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
        if (binding != null && binding.webProfileHostSignal != null) {
            binding.webProfileHostSignal.destroy();
        }
        super.onDestroy();
    }
}
