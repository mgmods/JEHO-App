package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.FragmentProfileBinding;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.agency.AgencyActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsActivity;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsViewModel;
import com.Dramizo.Series.presentation.friends.FriendsActivity;
import com.Dramizo.Series.presentation.friends.RequestsActivity;
import com.Dramizo.Series.presentation.notifications.NotificationsActivity;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.Dramizo.Series.presentation.settings.SettingsActivity;
import com.Dramizo.Series.presentation.settings.CustomerSupportActivity;
import com.Dramizo.Series.util.RemoteNavIcons;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.presentation.wallet.RechargeAgentActivity;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.HostSignalView;
import com.Dramizo.Series.util.RoomOpenChooser;
import com.Dramizo.Series.util.StaffRoleHelper;
import com.Dramizo.Series.util.VipStyle;

import java.util.Calendar;
import java.util.Locale;
import java.text.NumberFormat;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String myId;
    private MiscDtos.AgencyMineDto agencyMine;
    private long lastWalletRefreshAtMs = 0L;
    private AnimatorSet myCpHeartAnimator;
    private static final long MIN_WALLET_REFRESH_MS = 15_000L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // Keep the content flush to the top; offset only the floating edit/settings
        // controls below the system status bar so they never overlap battery/network icons.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            int top = bars.top + (int) (16 * getResources().getDisplayMetrics().density);
            if (binding != null) {
                android.view.ViewGroup.LayoutParams settingsLp = binding.btnSettingsTopReference.getLayoutParams();
                if (settingsLp instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams p = (android.view.ViewGroup.MarginLayoutParams) settingsLp;
                    if (p.topMargin != top) { p.topMargin = top; binding.btnSettingsTopReference.setLayoutParams(p); }
                }
                android.view.ViewGroup.LayoutParams editLp = binding.btnEditProfileIcon.getLayoutParams();
                if (editLp instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams p = (android.view.ViewGroup.MarginLayoutParams) editLp;
                    if (p.topMargin != top) { p.topMargin = top; binding.btnEditProfileIcon.setLayoutParams(p); }
                }
            }
            return insets;
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // The main "أنا" tab must use the dashboard's shared section wallpaper,
        // behind profile cards (not only as an activity/window background).
        String sectionWallpaper = com.Dramizo.Series.util.RemoteTheme.backgroundUrl(
                requireContext(), "profile");
        if (sectionWallpaper != null && !sectionWallpaper.trim().isEmpty()) {
            binding.imgThemeBg.setVisibility(View.VISIBLE);
            com.Dramizo.Series.util.RemoteTheme.loadInto(
                    binding.imgThemeBg, sectionWallpaper, R.drawable.bg_mikoo_page);
        } else {
            binding.imgThemeBg.setVisibility(View.GONE);
            binding.getRoot().setBackgroundResource(R.drawable.bg_home_full_gradient);
        }
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(ProfileViewModel.class);

        View.OnClickListener openProfile = v -> {
            Intent i = new Intent(requireContext(), ProfileActivity.class);
            if (myId != null) i.putExtra(ProfileActivity.EXTRA_USER_ID, myId);
            startActivity(i);
        };
        binding.headerProfile.setOnClickListener(openProfile);
        binding.imgAvatar.setOnClickListener(openProfile);
        // Keep the medals entry available from the Me tab; MedalActivity loads
        // the signed-in user's persisted medals from the backend.
        if (binding.rowProfileBadges != null) {
            binding.rowProfileBadges.setVisibility(View.VISIBLE);
            binding.rowProfileBadges.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MedalActivity.class)));
        }
        // The inline container is not used by this screen; the dedicated cabinet renders medals.

        // Bind only views that exist in fragment_profile.xml. The former action*/icon* IDs
        // belonged to a removed shortcut grid and caused FragmentProfileBinding compilation errors.
        bindRow(binding.btnAppearance, v ->
                startActivity(new Intent(requireContext(), StoreHubActivity.class)));
        bindRow(binding.btnTaskCenter, v ->
                startActivity(new Intent(requireContext(), TaskCenterActivity.class)));
        applyTasksFeatureVisibility();
        if (binding.btnContests != null) binding.btnContests.setVisibility(View.GONE);
        bindRow(binding.btnMyRoom, v -> RoomOpenChooser.open(requireActivity()));
        loadAgencyAction();
        bindRow(binding.btnVisitors, v ->
                startActivity(new Intent(requireContext(), VisitorsActivity.class)));
        bindRow(binding.btnRelations, v -> {
            Intent i = new Intent(requireContext(), RequestsActivity.class);
            i.putExtra(RequestsActivity.EXTRA_TAB, 2);
            startActivity(i);
        });
        if (binding.btnInviteCode != null) {
            bindRow(binding.btnInviteCode, v ->
                    startActivity(new Intent(requireContext(),
                            com.Dramizo.Series.presentation.invite.InvitationActivity.class)));
        }
        View.OnClickListener openEditProfile = v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class));
        bindRow(binding.btnEditProfile, openEditProfile);
        if (binding.btnEditProfileIcon != null) {
            binding.btnEditProfileIcon.setOnClickListener(openEditProfile);
        }
        bindRow(binding.btnRanking, v ->
                startActivity(new Intent(requireContext(), RankingActivity.class)));
        bindRow(binding.btnAgency, v ->
                startActivity(new Intent(requireContext(), AgencyActivity.class)));
        if (binding.btnRechargeAgent != null) {
            bindRow(binding.btnRechargeAgent, v ->
                    startActivity(new Intent(requireContext(), RechargeAgentActivity.class)));
        }
        if (binding.btnVanityId != null) {
            bindRow(binding.btnVanityId, v ->
                    startActivity(new Intent(requireContext(), VanityIdsActivity.class)));
        }
        if (binding.btnQuickAgency != null) {
            bindRow(binding.btnQuickAgency, v -> openAgencyHub());
        }
        bindRow(binding.btnNotifications, v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        View.OnClickListener openWallet = v ->
                startActivity(new Intent(requireContext(), BagActivity.class));
        binding.walletBalancesRow.setOnClickListener(openWallet);
        binding.btnWalletRecharge.setOnClickListener(openWallet);
        binding.btnWallet.setOnClickListener(openWallet);
        if (binding.btnWalletDiamondsCard != null) {
            binding.btnWalletDiamondsCard.setOnClickListener(openWallet);
        }
        binding.rowGiftHistory.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GiftHistoryActivity.class)));
        if (binding.rowMyEvents != null) {
            binding.rowMyEvents.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MyEventsActivity.class)));
        }
        binding.btnVip.setOnClickListener(v -> openVipHtmlPage());
        binding.btnSettingsQuick.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        if (binding.btnSettingsTopReference != null) {
            binding.btnSettingsTopReference.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), SettingsActivity.class)));
        }
        bindMysteriousModeShortcut();
        if (binding.levelCard != null) {
            binding.levelCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), UserLevelActivity.class)));
        }
        if (binding.vipCard != null) {
            binding.vipCard.setOnClickListener(v -> openVipHtmlPage());
        }
        View.OnClickListener openFriends = v -> {
            Intent i = new Intent(requireContext(), FriendsActivity.class);
            Object tag = v.getTag();
            if (tag instanceof Integer) i.putExtra(FriendsActivity.EXTRA_TAB, (Integer) tag);
            startActivity(i);
        };
        binding.statFriends.setOnClickListener(openFriends);
        binding.statVisitors.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VisitorsActivity.class)));
        binding.statFollowing.setTag(1);
        binding.statFans.setTag(2);
        binding.statFollowing.setOnClickListener(openFriends);
        binding.statFans.setOnClickListener(openFriends);
        binding.tvWealthChip.setOnClickListener(v -> openProfileInsights(ProfileInsightsActivity.MODE_WEALTH));
        if (binding.rowMyProfileCp != null) binding.rowMyProfileCp.setOnClickListener(v -> openProfileInsights(ProfileInsightsActivity.MODE_CP));
        binding.tvCharmChip.setOnClickListener(v -> openRanking("popular"));

        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            myId = user.id;
            // Badge cabinet is disabled platform-wide; avoid its additional network request.
            bindMyCpRelationship(user.id, user.avatarUrl, user.vipBadgeUrl);
            binding.tvDisplayName.setText(user.displayName);
            com.Dramizo.Series.util.GenderVerifiedBadge.bind(binding.tvDisplayName, null, user);
            bindGenderIcon(user);
            loadProfileBanner();
            bindStaffUi(user);
            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "—" : publicId);
            AppContainer vanityContainer = ContainerProvider.from(requireActivity());
            com.Dramizo.Series.util.VanityIdStyle.bind(binding.tvUsername,
                    vanityContainer.getVanityApi(), publicId, user.vipLevel);
            if (binding.btnCopyId != null) {
                binding.btnCopyId.setOnClickListener(v -> {
                    if (publicId.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), R.string.error_generic, android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("user_id", publicId));
                        android.widget.Toast.makeText(requireContext(), R.string.user_id_copied, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
            // The first statistic is Visitors: load its count from the visitors endpoint.
            binding.tvFollowers.setText("—");
            cLoadVisitorCount();
            binding.tvFollowing.setText(String.valueOf(Math.max(0, user.followingCount)));
            binding.tvFans.setText(String.valueOf(Math.max(0, user.followersCount)));
            if (binding.tvFriendsCount != null) {
                binding.tvFriendsCount.setText(String.valueOf(Math.max(0, user.friendsCount)));
            }
            // Profile: fixed VIP nobility frame (ud_vip_tou); mall SVGA stays on mics only.
            String nobilityFrame = VipStyle.profileNobilityFrameUrl(
                    Math.max(0, user.vipLevel), user.vipTouUrl);
            String profileFrame = user.vipBadgeUrl != null && !user.vipBadgeUrl.trim().isEmpty()
                    ? user.vipBadgeUrl : nobilityFrame;
            HostSignalView.prefetchWear(requireContext(), profileFrame);
            AvatarCosmetics.bindProfileWear(
                    binding.webProfileHostSignal,
                    binding.imgAvatar,
                    binding.imgFrame,
                    user.avatarUrl,
                    profileFrame,
                    user.hostBadgeUrl,
                    user.hostBadgeMeta,
                    1);
            // Frames / host signal live on the avatar only — never as a chip under the name.
            if (binding.imgHostBadge != null) {
                binding.imgHostBadge.setVisibility(View.GONE);
                binding.imgHostBadge.setImageDrawable(null);
            }
            if (binding.imgVipBadge != null) {
                binding.imgVipBadge.setVisibility(View.GONE);
                binding.imgVipBadge.setImageDrawable(null);
            }
            // Decorative Mikoo-style header — keep me_bg from XML.
            if (binding.imgCover.getDrawable() == null) {
                binding.imgCover.setImageResource(R.drawable.me_bg);
            }
            int level = Math.max(1, user.level);
            binding.tvLevelChip.setText("Lv." + level);
            com.Dramizo.Series.util.ServerAssets.loadCompoundStart(
                    binding.tvLevelChip, user.levelBadgeUrl, 14);
            binding.tvAgeChip.setText(String.valueOf(ageFrom(user.birthday)));

            // Profile shows the country flag only; do not render the country name.
            if (binding.imgCountryFlag != null) {
                boolean hasCountry = user.country != null && !user.country.trim().isEmpty();
                binding.imgCountryFlag.setVisibility(hasCountry ? View.VISIBLE : View.GONE);
                if (hasCountry) FlagImages.bind(binding.imgCountryFlag, user.country);
            }
            if (binding.tvCountryChip != null) binding.tvCountryChip.setVisibility(View.GONE);

            int vip = Math.max(0, user.vipLevel);
            long wealth = user.wealthLevel > 0 ? user.wealthLevel
                    : Math.max(0, user.wealthScore);
            long charm = user.popularityLevel > 0 ? user.popularityLevel
                    : Math.max(0, Math.max(user.charmScore, user.popularityScore));

            binding.tvAgeChip.setBackgroundResource(R.drawable.bg_chip_age);
            binding.tvAgeChip.setTextColor(0xFFFFFFFF);
            binding.tvLevelChip.setBackgroundResource(R.drawable.bg_chip_level);
            binding.tvLevelChip.setTextColor(0xFFFFFFFF);
            binding.tvWealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
            binding.tvWealthChip.setTextColor(0xFFFFFFFF);
            binding.tvWealthChip.setText(formatScore(wealth));
            binding.tvCharmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            binding.tvCharmChip.setTextColor(0xFFFFFFFF);
            binding.tvCharmChip.setText(formatScore(charm));
            if (binding.tvDiamondChip != null) {
                binding.tvDiamondChip.setVisibility(View.GONE);
            }
            // Use the real VIP head emblem asset, not a text-only "VIP" chip.
            if (binding.tvVipChip != null) binding.tvVipChip.setVisibility(View.GONE);
            if (binding.imgVipBadge != null) {
                String vipEmblem = user.vipHeadUrl;
                if (vip > 0 && vipEmblem != null && !vipEmblem.trim().isEmpty()) {
                    binding.imgVipBadge.setVisibility(View.VISIBLE);
                    Glide.with(ProfileFragment.this)
                            .load(AssetCatalog.absoluteUrl(vipEmblem))
                            .placeholder(R.drawable.ic_medal_default)
                            .error(R.drawable.ic_medal_default)
                            .into(binding.imgVipBadge);
                } else {
                    binding.imgVipBadge.setVisibility(View.GONE);
                    binding.imgVipBadge.setImageDrawable(null);
                }
            }
            AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.getError().observe(getViewLifecycleOwner(), e -> {
            if (e != null) Toast.makeText(requireContext(), e, Toast.LENGTH_SHORT).show();
            if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.loadMe();
    }

    /** Loads the profile-only banner; home carousel banners are managed independently. */
    private void loadProfileBanner() {
        if (binding == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            try {
                Result<java.util.List<MiscDtos.BannerDto>> result =
                        com.Dramizo.Series.util.ApiCall.execute(c.getConfigApi().profileBanner());
                if (!result.success || result.data == null || result.data.isEmpty()) {
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.profileBannerWrap.setVisibility(View.GONE);
                            binding.imgProfileBanner.setVisibility(View.GONE);
                        }
                    });
                    return;
                }
                MiscDtos.BannerDto selected = result.data.get(0);
                if (selected == null || selected.imageUrl == null || selected.imageUrl.trim().isEmpty()) {
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        if (binding != null) binding.profileBannerWrap.setVisibility(View.GONE);
                    });
                    return;
                }
                final String imageUrl = AssetCatalog.absoluteUrl(selected.imageUrl.trim());
                if (imageUrl == null || imageUrl.isEmpty() || !isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    binding.profileBannerWrap.setVisibility(View.VISIBLE);
                    binding.imgProfileBanner.setVisibility(View.VISIBLE);
                    Glide.with(ProfileFragment.this).load(imageUrl)
                            .placeholder(R.drawable.bg_mikoo_card)
                            .error(R.drawable.bg_mikoo_card)
                            .centerCrop().into(binding.imgProfileBanner);
                });
            } catch (Throwable ignored) {
                // A missing optional profile banner must never block the profile screen.
            }
        });
    }

    /** Blue male / pink female marker when gender metadata is available on the user DTO. */
    private void bindGenderIcon(Object user) {
        if (binding == null || binding.tvGenderIcon == null || user == null) return;
        String gender = "";
        for (String name : new String[]{"gender", "sex"}) {
            try {
                java.lang.reflect.Field field = user.getClass().getField(name);
                Object value = field.get(user);
                if (value != null) { gender = String.valueOf(value); break; }
            } catch (Throwable ignored) { }
            try {
                java.lang.reflect.Method getter = user.getClass().getMethod(
                        "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                Object value = getter.invoke(user);
                if (value != null) { gender = String.valueOf(value); break; }
            } catch (Throwable ignored) { }
        }
        String normalized = gender.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("male") || normalized.equals("m") || normalized.equals("1") || normalized.equals("ذكر")) {
            binding.tvGenderIcon.setText("♂");
            binding.tvGenderIcon.setTextColor(android.graphics.Color.rgb(52, 152, 219));
            binding.tvGenderIcon.setVisibility(View.VISIBLE);
        } else if (normalized.equals("female") || normalized.equals("f") || normalized.equals("2") || normalized.equals("أنثى") || normalized.equals("انثى")) {
            binding.tvGenderIcon.setText("♀");
            binding.tvGenderIcon.setTextColor(android.graphics.Color.rgb(232, 92, 157));
            binding.tvGenderIcon.setVisibility(View.VISIBLE);
        } else {
            binding.tvGenderIcon.setVisibility(View.GONE);
        }
    }

    private void cLoadVisitorCount() {
        if (!isAdded() || binding == null) return;
        com.Dramizo.Series.di.AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto>> result =
                    com.Dramizo.Series.util.ApiCall.execute(c.getUserApi().visitors(1));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                int total = result.success && result.data != null
                        ? Math.max(0, result.data.resolveTotal()) : 0;
                binding.tvFollowers.setText(String.valueOf(total));
            });
        });
    }

    private int ageFrom(String birthday) {
        if (birthday == null || birthday.length() < 4) return 18;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            return Math.max(1, Calendar.getInstance().get(Calendar.YEAR) - year);
        } catch (Exception e) {
            return 18;
        }
    }


    /** Visible self-service switch in the main profile tab; availability is VIP-controlled. */
    private boolean mysteriousFlag(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return value != null && ("true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value))
                || "yes".equalsIgnoreCase(String.valueOf(value)));
    }

    private void applyMysteriousModeVisual(boolean enabled) {
        if (binding == null || binding.rowMysteriousModeShortcut == null
                || binding.switchMysteriousModeShortcut == null) return;
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(getResources().getDisplayMetrics().density * 14f);
        bg.setColor(enabled ? 0xFF123F35 : 0xFF282832);
        bg.setStroke(Math.round(getResources().getDisplayMetrics().density * 1.2f),
                enabled ? 0xFF39D6A2 : 0xFF555563);
        binding.rowMysteriousModeShortcut.setBackground(bg);
        binding.switchMysteriousModeShortcut.setThumbTintList(
                android.content.res.ColorStateList.valueOf(enabled ? 0xFF39D6A2 : 0xFFB8B8C4));
        binding.switchMysteriousModeShortcut.setTrackTintList(
                android.content.res.ColorStateList.valueOf(enabled ? 0xFF287C64 : 0xFF686875));
    }

    private void bindMysteriousModeShortcut() {
        if (binding == null || binding.rowMysteriousModeShortcut == null
                || binding.switchMysteriousModeShortcut == null) return;
        binding.rowMysteriousModeShortcut.setVisibility(View.GONE);
        binding.switchMysteriousModeShortcut.setEnabled(false);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> result =
                    c.getVipRepository().mysteriousModeStatus();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                if (result == null || !result.success || result.data == null) {
                    binding.rowMysteriousModeShortcut.setVisibility(View.GONE);
                    return;
                }
                java.util.Map<String, Object> data = result.data;
                boolean eligible = mysteriousFlag(data.get("eligible"));
                boolean enabled = mysteriousFlag(data.get("enabled"));
                requireContext().getSharedPreferences("jeho_mysterious_mode", android.content.Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
                if (!eligible) {
                    binding.rowMysteriousModeShortcut.setVisibility(View.GONE);
                    return;
                }
                binding.rowMysteriousModeShortcut.setVisibility(View.VISIBLE);
                binding.switchMysteriousModeShortcut.setOnCheckedChangeListener(null);
                binding.switchMysteriousModeShortcut.setChecked(enabled);
                binding.switchMysteriousModeShortcut.setEnabled(true);
                applyMysteriousModeVisual(enabled);
                binding.tvMysteriousModeShortcutHint.setText(enabled
                        ? "مفعّل — هويتك مخفية داخل الغرف"
                        : "متاح لاشتراكك — اضغط للتفعيل");
                bindMysteriousModeShortcutListener(c);
            });
        });
    }

    private void bindMysteriousModeShortcutListener(AppContainer c) {
        if (binding == null || binding.switchMysteriousModeShortcut == null) return;
        binding.switchMysteriousModeShortcut.setOnCheckedChangeListener((button, requested) -> {
            button.setEnabled(false);
            c.getIoExecutor().execute(() -> {
                Result<java.util.Map<String, Object>> changed =
                        c.getVipRepository().setMysteriousMode(requested);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding == null || !isAdded()) return;
                    if (changed != null && changed.success && changed.data != null) {
                        boolean saved = mysteriousFlag(changed.data.get("enabled"));
                        requireContext().getSharedPreferences("jeho_mysterious_mode", android.content.Context.MODE_PRIVATE).edit().putBoolean("enabled", saved).apply();
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(saved);
                        button.setEnabled(true);
                        applyMysteriousModeVisual(saved);
                        binding.tvMysteriousModeShortcutHint.setText(saved
                                ? "مفعّل — هويتك مخفية داخل الغرف"
                                : "غير مفعّل — اضغط لتفعيل الوضع الغامض");
                        bindMysteriousModeShortcutListener(c);
                    } else {
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(!requested);
                        button.setEnabled(true);
                        applyMysteriousModeVisual(!requested);
                        bindMysteriousModeShortcutListener(c);
                        String error = changed != null && changed.error != null
                                ? changed.error : "تعذر حفظ الإعداد، تحقق من اشتراك VIP وحاول مرة أخرى";
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private void loadAgencyAction() {
        if (!isAdded()) return;
        com.Dramizo.Series.di.AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyMineDto> result = c.getAgencyRepository().mine();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                agencyMine = result.success ? result.data : null;
                // Keep quick-action label as short "غرفة" — agency has its own icon.
                binding.tvMyRoomQuickLabel.setText(R.string.room_short);
                binding.btnMyRoom.setVisibility(View.VISIBLE);
                boolean hasAgency = agencyMine != null
                        && agencyMine.agency != null
                        && agencyMine.agency.id != null
                        && !agencyMine.agency.id.isEmpty()
                        && "active".equalsIgnoreCase(String.valueOf(agencyMine.agency.status));
                if (binding.btnQuickAgency != null) {
                    binding.btnQuickAgency.setVisibility(hasAgency ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private String formatScore(long value) {
        return NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0))
                .format(Math.max(0, value));
    }

    /** Open the same data-backed VIP catalog as HTML, from the profile VIP entry only. */
    private void openVipHtmlPage() {
        Intent intent = new Intent(requireContext(), CosmeticsActivity.class);
        intent.putExtra("store_section", "vip");
        startActivity(intent);
    }

    private void bindRow(View row, View.OnClickListener click) {
        if (row != null) row.setOnClickListener(click);
    }

    private void openMall(String type) {
        Intent i = new Intent(requireContext(), CosmeticsActivity.class);
        if (type != null) i.putExtra(CosmeticsViewModel.EXTRA_TYPE, type);
        startActivity(i);
    }

    private void openAgencyHub() {
        startActivity(new Intent(requireContext(), AgencyActivity.class));
    }

    private void openProfileInsights(String mode) {
        Intent intent = new Intent(requireContext(), ProfileInsightsActivity.class);
        intent.putExtra(ProfileInsightsActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private void openRanking(String category) {
        Intent i = new Intent(requireContext(), RankingActivity.class);
        if (category != null) i.putExtra(RankingActivity.EXTRA_CATEGORY, category);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-fetch feature flags so master tasks switch takes effect without reinstall.
        try {
            AppContainer c = ContainerProvider.from(requireActivity());
            c.getIoExecutor().execute(() -> {
                com.Dramizo.Series.util.AppFeatures.refresh(c);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(this::applyTasksFeatureVisibility);
            });
        } catch (Exception ignored) {
            applyTasksFeatureVisibility();
        }
        // Soft refresh only — reloading profile + wallet every entry was flashing avatar / balances.
        if (viewModel != null) viewModel.loadMe(false);
        loadAgencyAction();
        // Keep the medals entry visible after returning from other screens.
        if (binding != null && binding.rowProfileBadges != null) {
            binding.rowProfileBadges.setVisibility(View.VISIBLE);
        }
        refreshWalletBalances(false);
    }

    /** Keep the requested profile shortcuts visible; their existing click handlers remain unchanged. */
    private void applyTasksFeatureVisibility() {
        if (binding == null) return;

        // Use a true vertical list for the five requested Me-page shortcuts.
        android.widget.LinearLayout list = binding.profileServicesGrid;
        if (list == null) return;
        list.setVisibility(View.VISIBLE);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);

        View[] shortcuts = {
                binding.rowMyEvents,
                binding.btnAgency,
                binding.btnTaskCenter,
                binding.btnRechargeAgent,
                binding.btnSettingsQuick
        };

        // Keep this section focused on the five requested actions, rather than
        // allowing legacy grid items to take space or push these rows off-screen.
        java.util.Set<View> keep = new java.util.HashSet<>();
        java.util.Collections.addAll(keep, shortcuts);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (!keep.contains(child)) child.setVisibility(View.GONE);
        }

        int density = getResources().getDisplayMetrics().densityDpi;
        int rowHeight = Math.round(62f * density / 160f);
        int horizontalPadding = Math.round(16f * density / 160f);
        int iconSize = Math.round(30f * density / 160f);
        int textGap = Math.round(12f * density / 160f);
        for (View shortcut : shortcuts) {
            if (!(shortcut instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) shortcut;
            row.setVisibility(View.VISIBLE);
            row.setEnabled(true);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(horizontalPadding, 0, horizontalPadding, 0);
            android.widget.LinearLayout.LayoutParams rowLp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
            rowLp.bottomMargin = Math.round(1f * density / 160f);
            row.setLayoutParams(rowLp);
            row.setBackgroundResource(R.drawable.bg_profile_reference_card);

            for (int j = 0; j < row.getChildCount(); j++) {
                View child = row.getChildAt(j);
                if (child instanceof android.widget.ImageView) {
                    android.widget.ImageView icon = (android.widget.ImageView) child;
                    if (j == 0) {
                        android.widget.LinearLayout.LayoutParams iconLp =
                                new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
                        icon.setLayoutParams(iconLp);
                    } else if (j == row.getChildCount() - 1) {
                        child.setVisibility(View.GONE);
                    }
                } else if (child instanceof android.widget.TextView) {
                    android.widget.LinearLayout.LayoutParams textLp =
                            new android.widget.LinearLayout.LayoutParams(
                                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    textLp.leftMargin = textGap;
                    textLp.rightMargin = textGap;
                    ((android.widget.TextView) child).setGravity(
                            android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
                    ((android.widget.TextView) child).setTextColor(
                            android.graphics.Color.parseColor("#FF332A40"));
                    ((android.widget.TextView) child).setTextSize(15);
                    child.setLayoutParams(textLp);
                }
            }
            row.requestLayout();
        }
        list.requestLayout();
    }

    private void bindStaffUi(com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (binding == null) return;
        String role = StaffRoleHelper.normalize(user);
        boolean isStaff = StaffRoleHelper.isStaff(user);
        if (binding.tvStaffBadge != null) {
            if (isStaff) {
                binding.tvStaffBadge.setVisibility(View.VISIBLE);
                binding.tvStaffBadge.setText(StaffRoleHelper.badgeAr(user));
                binding.tvStaffBadge.setBackgroundResource(
                        StaffRoleHelper.SUPER.equals(role)
                                ? R.drawable.bg_chip_staff_super
                                : R.drawable.bg_chip_staff_manager);
            } else {
                binding.tvStaffBadge.setVisibility(View.GONE);
            }
        }
        if (binding.btnOpenSuperAdminDashboard != null) {
            // One native entry point, only inside the user's actual profile screen.
            // The same authenticated console adapts its available modules to the server role.
            android.widget.TextView adminEntry = binding.btnOpenSuperAdminDashboard;
            int density = getResources().getDisplayMetrics().densityDpi;
            int horizontalPadding = Math.round(18 * density / 160f);
            int verticalPadding = Math.round(14 * density / 160f);
            String roleLabel = StaffRoleHelper.SUPER.equals(role) ? "السوبر أدمن"
                    : StaffRoleHelper.ADMIN.equals(role) ? "الأدمن" : "المانجر";
            adminEntry.setText("✦  لوحة التحكم  ·  " + roleLabel);
            adminEntry.setTextColor(android.graphics.Color.WHITE);
            adminEntry.setTextSize(15f);
            adminEntry.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.BOLD));
            adminEntry.setGravity(android.view.Gravity.CENTER);
            adminEntry.setMinHeight(Math.round(52 * getResources().getDisplayMetrics().density));
            adminEntry.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            android.graphics.drawable.GradientDrawable adminBackground =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                            new int[]{0xFF272A58, 0xFF6849C8});
            adminBackground.setCornerRadius(16 * getResources().getDisplayMetrics().density);
            adminBackground.setStroke(Math.round(getResources().getDisplayMetrics().density),
                    0x557B68D8);
            adminEntry.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    adminBackground, null));
            adminEntry.setElevation(3 * getResources().getDisplayMetrics().density);
            adminEntry.setVisibility(isStaff ? View.VISIBLE : View.GONE);
            adminEntry.setOnClickListener(v -> {
                if (!isAdded()) return;
                com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto current =
                        ContainerProvider.from(requireActivity()).getSessionManager().getUser();
                if (!StaffRoleHelper.isStaff(current)) {
                    android.widget.Toast.makeText(requireContext(),
                            "صلاحية لوحة التحكم غير متاحة لهذا الحساب",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(requireContext(),
                        com.Dramizo.Series.presentation.admin.preview.AdminPreviewActivity.class));
            });
        }
        // Use one unified entry point; avoid showing a duplicate admin button on the profile.
        if (binding.btnAdminPreview != null) {
            binding.btnAdminPreview.setVisibility(View.GONE);
        }
        if (binding.panelStaffPowers != null) {
            if (isStaff) {
                binding.panelStaffPowers.setVisibility(View.VISIBLE);
                boolean isSuperAdmin = StaffRoleHelper.SUPER.equals(role);
                if (binding.tvStaffPanelTitle != null) {
                    binding.tvStaffPanelTitle.setVisibility(isSuperAdmin ? View.GONE : View.VISIBLE);
                    if (!isSuperAdmin) binding.tvStaffPanelTitle.setText("صلاحيات المانجر");
                }
                if (binding.tvStaffPowers != null) {
                    binding.tvStaffPowers.setVisibility(isSuperAdmin ? View.GONE : View.VISIBLE);
                    if (!isSuperAdmin) binding.tvStaffPowers.setText(StaffRoleHelper.powersAr(user));
                }
                if (binding.tvStaffPowersHint != null) {
                    binding.tvStaffPowersHint.setVisibility(isSuperAdmin ? View.GONE : View.VISIBLE);
                }
            } else {
                binding.panelStaffPowers.setVisibility(View.GONE);
            }
        }
    }

    private void refreshWalletBalances(boolean force) {
        if (binding == null || binding.tvWalletCoins == null) return;
        if (!isAdded()) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastWalletRefreshAtMs < MIN_WALLET_REFRESH_MS
                && binding.tvWalletCoins.getText() != null
                && !"—".equals(binding.tvWalletCoins.getText().toString())) {
            return;
        }
        lastWalletRefreshAtMs = now;
        final androidx.fragment.app.FragmentActivity act = getActivity();
        if (act == null) return;
        final com.Dramizo.Series.di.AppContainer container = ContainerProvider.from(act);
        container.getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.WalletDtos.WalletDto> r =
                    com.Dramizo.Series.util.ApiCall.execute(container.getWalletApi().getWallet());
            if (!isAdded()) return;
            act.runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;
                if (r.success && r.data != null) {
                    NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
                    String coins = nf.format(Math.max(0, r.data.coins));
                    String diamonds = nf.format(Math.max(0, r.data.diamonds));
                    if (binding.tvWalletCoins != null
                            && !coins.contentEquals(binding.tvWalletCoins.getText())) {
                        binding.tvWalletCoins.setText(coins);
                    }
                    if (binding.tvWalletDiamonds != null
                            && !diamonds.contentEquals(binding.tvWalletDiamonds.getText())) {
                        binding.tvWalletDiamonds.setText(diamonds);
                    }
                }
            });
        });
    }

    private void bindMyCpRelationship(String userId, String selfAvatarUrl, String selfFrameUrl) {
        if (userId == null || userId.trim().isEmpty() || binding == null) {
            hideMyCpRelationship();
            return;
        }
        AppContainer cpContainer = ContainerProvider.from(requireActivity());
        cpContainer.getIoExecutor().execute(() -> {
            Result<java.util.List<java.util.Map<String, Object>>> result = com.Dramizo.Series.util.ApiCall.execute(
                    cpContainer.getUserApi().cpProfileRelationships(userId));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (!result.success || result.data == null || result.data.isEmpty()) {
                    hideMyCpRelationship();
                    return;
                }
                java.util.Map<String, Object> relation = result.data.get(0);
                Object partnerObject = relation.get("partner");
                java.util.Map<?, ?> partner = partnerObject instanceof java.util.Map
                        ? (java.util.Map<?, ?>) partnerObject : null;
                if (partner == null) {
                    hideMyCpRelationship();
                    return;
                }
                Object displayName = partner.get("displayName");
                Object username = partner.get("username");
                String partnerName = displayName != null && !String.valueOf(displayName).trim().isEmpty()
                        ? String.valueOf(displayName).trim()
                        : username != null ? String.valueOf(username).trim() : "";
                if (partnerName.isEmpty()) {
                    hideMyCpRelationship();
                    return;
                }
                String type = relation.get("type") == null ? "" : String.valueOf(relation.get("type"));
                Object avatarValue = partner.get("avatarUrl");
                String partnerAvatar = avatarValue == null ? "" : String.valueOf(avatarValue).trim();
                binding.tvMyProfileCpLabel.setText("مرتبط بـ " + partnerName);
                if (binding.tvMyProfileCpType != null) {
                    binding.tvMyProfileCpType.setText(myCpTypeLabel(type));
                }
                if (binding.imgMyProfileCpHeart != null) {
                    binding.imgMyProfileCpHeart.setText(myCpTypeSymbol(type));
                    binding.imgMyProfileCpHeart.setContentDescription(myCpTypeLabel(type));
                }
                bindMyCpAvatar(binding.imgMyProfileCpSelf, selfAvatarUrl);
                bindMyCpAvatar(binding.imgMyProfileCpPartner, partnerAvatar);

                String partnerFrameUrl = partner.get("vipBadgeUrl") == null
                        ? "" : String.valueOf(partner.get("vipBadgeUrl")).trim();
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(binding.imgMyProfileCpSelfFrame, selfFrameUrl);
                com.Dramizo.Series.util.AvatarCosmetics.applyFrame(binding.imgMyProfileCpPartnerFrame, partnerFrameUrl);
                // The "أنا" tab shows only the compact CP pair at the top.
                // Keep the ornate CV card for the full profile page to avoid a duplicate here.
                if (binding.rowMyProfileCpCv != null) binding.rowMyProfileCpCv.setVisibility(View.GONE);
                binding.rowMyProfileCp.setVisibility(View.VISIBLE);
                startMyCpHeartAnimation();
            });
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

    private void bindMyCpAvatar(android.widget.ImageView view, String avatarUrl) {
        if (view == null) return;
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.AvatarImageLoader.load(view, avatarUrl.trim());
    }

    private String myCpTypeLabel(String type) {
        switch (type) {
            case "friendship": return "علاقة صداقة";
            case "brotherhood": return "علاقة أخوّة";
            case "love": return "علاقة حب";
            case "engagement": return "علاقة خطوبة";
            case "marriage": return "علاقة زواج";
            default: return "علاقة";
        }
    }

    private String myCpTypeSymbol(String type) {
        switch (type) {
            case "friendship": return "🤝";
            case "brotherhood": return "🤝";
            case "engagement": return "💍";
            case "marriage": return "💞";
            case "love":
            default: return "♥";
        }
    }

    private void startMyCpHeartAnimation() {
        if (binding == null || binding.imgMyProfileCpHeart == null) return;
        if (myCpHeartAnimator != null) myCpHeartAnimator.cancel();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.imgMyProfileCpHeart, View.SCALE_X, 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.imgMyProfileCpHeart, View.SCALE_Y, 1f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(binding.imgMyProfileCpHeart, View.ALPHA, 0.72f, 1f, 0.72f);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            animator.setDuration(900L);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
        }
        myCpHeartAnimator = new AnimatorSet();
        myCpHeartAnimator.playTogether(scaleX, scaleY, alpha);
        myCpHeartAnimator.start();
    }

    private void hideMyCpRelationship() {
        if (myCpHeartAnimator != null) {
            myCpHeartAnimator.cancel();
            myCpHeartAnimator = null;
        }
        if (binding != null && binding.rowMyProfileCp != null) {
            binding.rowMyProfileCp.setVisibility(View.GONE);
        }
        if (binding != null && binding.rowMyProfileCpCv != null) {
            binding.rowMyProfileCpCv.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        if (myCpHeartAnimator != null) {
            myCpHeartAnimator.cancel();
            myCpHeartAnimator = null;
        }
        if (binding != null && binding.webProfileHostSignal != null) {
            binding.webProfileHostSignal.destroy();
        }
        super.onDestroyView();
        binding = null;
    }
}
