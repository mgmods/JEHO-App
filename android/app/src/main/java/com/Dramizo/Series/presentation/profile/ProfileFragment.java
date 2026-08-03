package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.FragmentProfileBinding;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
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
import com.Dramizo.Series.presentation.vip.VipActivity;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.HostSignalView;
import com.Dramizo.Series.util.RoomOpenChooser;

import java.util.Calendar;
import java.text.NumberFormat;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private String myId;
    private MiscDtos.AgencyMineDto agencyMine;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(binding.getRoot(), "profile");
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(ProfileViewModel.class);

        View.OnClickListener openProfile = v -> {
            Intent i = new Intent(requireContext(), ProfileActivity.class);
            if (myId != null) i.putExtra(ProfileActivity.EXTRA_USER_ID, myId);
            startActivity(i);
        };
        binding.headerProfile.setOnClickListener(openProfile);
        binding.imgAvatar.setOnClickListener(openProfile);

        bindRow(binding.btnAppearance, v -> openMall(null));
        bindRow(binding.btnTaskCenter, v ->
                startActivity(new Intent(requireContext(), TaskCenterActivity.class)));
        bindRow(binding.btnContests, v ->
                startActivity(new Intent(requireContext(), MedalActivity.class)));
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
        if (binding.btnVanityId != null) {
            bindRow(binding.btnVanityId, v ->
                    startActivity(new Intent(requireContext(), VanityIdsActivity.class)));
        }
        if (binding.btnQuickAgency != null) {
            bindRow(binding.btnQuickAgency, v -> openAgencyHub());
        }
        bindRow(binding.btnNotifications, v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        binding.btnWallet.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BagActivity.class)));
        binding.rowGiftHistory.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GiftHistoryActivity.class)));
        if (binding.rowMyEvents != null) {
            binding.rowMyEvents.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MyEventsActivity.class)));
        }
        binding.btnVip.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), VipActivity.class)));
        binding.btnSettingsQuick.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        if (binding.levelCard != null) {
            binding.levelCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), UserLevelActivity.class)));
        }
        if (binding.vipCard != null) {
            binding.vipCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), VipActivity.class)));
        }
        View.OnClickListener openFriends = v -> {
            Intent i = new Intent(requireContext(), FriendsActivity.class);
            Object tag = v.getTag();
            if (tag instanceof Integer) i.putExtra(FriendsActivity.EXTRA_TAB, (Integer) tag);
            startActivity(i);
        };
        binding.statFriends.setTag(0);
        binding.statFollowing.setTag(1);
        binding.statFans.setTag(2);
        binding.statFriends.setOnClickListener(openFriends);
        binding.statFollowing.setOnClickListener(openFriends);
        binding.statFans.setOnClickListener(openFriends);
        binding.tvWealthChip.setOnClickListener(v -> openRanking("rich"));
        binding.tvCharmChip.setOnClickListener(v -> openRanking("popular"));

        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            myId = user.id;
            binding.tvDisplayName.setText(user.displayName);
            com.Dramizo.Series.util.GenderVerifiedBadge.bind(binding.tvDisplayName, null, user);
            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "ID: —" : ("ID: " + publicId));
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
            binding.tvFollowers.setText(String.valueOf(Math.max(0, user.friendsCount)));
            binding.tvFollowing.setText(String.valueOf(Math.max(0, user.followingCount)));
            binding.tvFans.setText(String.valueOf(Math.max(0, user.followersCount)));
            HostSignalView.prefetchWear(requireContext(), user.vipBadgeUrl);
            AvatarCosmetics.bindProfileWear(
                    binding.webProfileHostSignal,
                    binding.imgAvatar,
                    binding.imgFrame,
                    user.avatarUrl,
                    user.vipBadgeUrl,
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

            CountryCatalog.Entry countryEntry = CountryCatalog.resolve(user.country);
            if (binding.imgCountryFlag != null) {
                FlagImages.bind(binding.imgCountryFlag, user.country);
            }
            if (binding.tvCountryChip != null) {
                if (countryEntry != null && countryEntry.nameAr != null && !countryEntry.nameAr.isEmpty()) {
                    binding.tvCountryChip.setVisibility(View.VISIBLE);
                    binding.tvCountryChip.setText(countryEntry.nameAr);
                } else if (user.country != null && !user.country.isEmpty()) {
                    binding.tvCountryChip.setVisibility(View.VISIBLE);
                    binding.tvCountryChip.setText(user.country);
                } else {
                    binding.tvCountryChip.setVisibility(View.GONE);
                }
            }

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
            binding.tvWealthChip.setText("❤ " + formatScore(wealth));
            binding.tvCharmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            binding.tvCharmChip.setTextColor(0xFFFFFFFF);
            binding.tvCharmChip.setText("🌙 " + formatScore(charm));
            if (binding.tvDiamondChip != null) {
                binding.tvDiamondChip.setVisibility(View.GONE);
            }
            if (vip > 0) {
                binding.tvVipChip.setVisibility(View.VISIBLE);
                binding.tvVipChip.setBackgroundResource(R.drawable.bg_chip_vip);
                binding.tvVipChip.setTextColor(0xFFFFFFFF);
                binding.tvVipChip.setText("VIP" + vip);
            } else {
                binding.tvVipChip.setVisibility(View.GONE);
            }
            AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.getError().observe(getViewLifecycleOwner(), e -> {
            if (e != null) Toast.makeText(requireContext(), e, Toast.LENGTH_SHORT).show();
            if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.loadMe();
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
                        && (agencyMine.agency.status == null
                        || !"rejected".equalsIgnoreCase(agencyMine.agency.status));
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

    private void openRanking(String category) {
        Intent i = new Intent(requireContext(), RankingActivity.class);
        if (category != null) i.putExtra(RankingActivity.EXTRA_CATEGORY, category);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.loadMe();
        loadAgencyAction();
        refreshWalletBalances();
        if (binding != null && binding.webProfileHostSignal != null
                && binding.webProfileHostSignal.getVisibility() == View.VISIBLE) {
            binding.webProfileHostSignal.resumeMotion();
        }
    }

    private void refreshWalletBalances() {
        if (binding == null || binding.tvWalletCoins == null) return;
        if (!isAdded()) return;
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
                    NumberFormat nf = NumberFormat.getInstance();
                    if (binding.tvWalletCoins != null) {
                        binding.tvWalletCoins.setText(nf.format(Math.max(0, r.data.coins)));
                    }
                    if (binding.tvWalletDiamonds != null) {
                        binding.tvWalletDiamonds.setText(nf.format(Math.max(0, r.data.diamonds)));
                    }
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        if (binding != null && binding.webProfileHostSignal != null) {
            binding.webProfileHostSignal.destroy();
        }
        super.onDestroyView();
        binding = null;
    }
}
