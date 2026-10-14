package com.Dramizo.Series.presentation.home;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.FragmentHomeMeBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.MyRoomCardBinder;
import com.Dramizo.Series.util.RemoteTheme;
import com.Dramizo.Series.util.RecentRoomsStore;
import com.Dramizo.Series.util.RoomBrowseFilter;
import com.Dramizo.Series.util.RoomOpenChooser;

import java.util.ArrayList;
import java.util.List;

/** Mikoo-style «أنا»: غرفتي + مؤخراً / غرف المتابعين. */
public class HomeMeFragment extends Fragment {
    public static final int TAB_ME = 0;
    private static final int SUB_RECENT = 0;
    private static final int SUB_FOLLOWING = 1;

    private FragmentHomeMeBinding binding;
    private HomeViewModel viewModel;
    private PartyRoomAdapter roomAdapter;
    private int subTab = SUB_RECENT;
    private final Runnable wallpaperRefreshListener = this::applySectionWallpaper;

    public static HomeMeFragment newInstance() {
        return new HomeMeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeMeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applySectionWallpaper();
        bindProfileDashboard();
        viewModel = new ViewModelProvider(requireParentFragment(),
                new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(HomeViewModel.class);

        float d = getResources().getDisplayMetrics().density;
        roomAdapter = new PartyRoomAdapter(this::openRoom);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        binding.recyclerMeRooms.setLayoutManager(glm);
        roomAdapter.attachSpanSize(glm);
        binding.recyclerMeRooms.setHasFixedSize(true);
        binding.recyclerMeRooms.setItemAnimator(null);
        binding.recyclerMeRooms.setItemViewCacheSize(12);
        binding.recyclerMeRooms.setOverScrollMode(View.OVER_SCROLL_NEVER);
        binding.recyclerMeRooms.setNestedScrollingEnabled(false);
        binding.recyclerMeRooms.addItemDecoration(
                new com.Dramizo.Series.util.RoomGridSpacingDecoration(Math.round(10 * d)));
        binding.recyclerMeRooms.setAdapter(roomAdapter);

        binding.subRecentWrap.setOnClickListener(v -> selectSub(SUB_RECENT));
        binding.tvSubRecent.setOnClickListener(v -> selectSub(SUB_RECENT));
        binding.subFollowingWrap.setOnClickListener(v -> selectSub(SUB_FOLLOWING));
        binding.tvSubFollowing.setOnClickListener(v -> selectSub(SUB_FOLLOWING));

        viewModel.getRooms().observe(getViewLifecycleOwner(), list -> {
            refreshMyRoom();
            if (subTab == SUB_RECENT) bindList();
        });
        viewModel.getFollowingRooms().observe(getViewLifecycleOwner(), list -> {
            if (subTab == SUB_FOLLOWING) bindList();
        });

        selectSub(SUB_RECENT);
        refreshMyRoom();
        viewModel.loadFollowingRooms();
    }

    @Override
    public void onStart() {
        super.onStart();
        RemoteTheme.addRefreshListener(wallpaperRefreshListener);
    }

    @Override
    public void onStop() {
        RemoteTheme.removeRefreshListener(wallpaperRefreshListener);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        applySectionWallpaper();
        refreshMyRoom();
        if (subTab == SUB_RECENT) bindList();
        else viewModel.loadFollowingRooms();
    }

    public void reapply() {
        refreshMyRoom();
        bindList();
    }

    private void selectSub(int sub) {
        subTab = sub;
        boolean recent = sub == SUB_RECENT;
        binding.tvSubRecent.setAlpha(recent ? 1f : 0.5f);
        binding.tvSubFollowing.setAlpha(recent ? 0.5f : 1f);
        binding.dotSubRecent.setVisibility(recent ? View.VISIBLE : View.INVISIBLE);
        binding.dotSubFollowing.setVisibility(recent ? View.INVISIBLE : View.VISIBLE);
        if (sub == SUB_FOLLOWING) viewModel.loadFollowingRooms();
        bindList();
    }


    private void bindProfileDashboard() {
        if (binding == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        if (me != null) {
            String name = me.displayName;
            if (name == null || name.trim().isEmpty()) name = me.username;
            if (name == null || name.trim().isEmpty()) name = "عضو جيهو";
            binding.tvProfileName.setText(name);
            String publicId = me.displayPublicId();
            binding.tvProfileId.setText(publicId.isEmpty() ? "ID —" : "ID " + publicId);
            String bio = me.bio;
            binding.tvProfileBio.setText(bio == null || bio.trim().isEmpty()
                    ? "أهلاً بك في جيهو" : bio.trim());
            binding.tvProfileDiamonds.setText(String.valueOf(Math.max(0L, me.totalReceivedDiamonds)));
            binding.tvProfileGold.setText(String.valueOf(Math.max(0L, me.totalSentCoins)));
            binding.tvProfileVip.setText("VIP " + Math.max(0, me.vipLevel));
            binding.tvProfileVerified.setVisibility(me.genderVerified
                    ? View.VISIBLE : View.GONE);
            if (me.avatarUrl != null && !me.avatarUrl.trim().isEmpty()) {
                Glide.with(this).load(me.avatarUrl).placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar).into(binding.imgProfileAvatar);
            } else {
                binding.imgProfileAvatar.setImageResource(R.drawable.ic_default_avatar);
            }
        }

        binding.imgProfileAvatar.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.ProfileActivity.class)));
        binding.btnCopyProfileId.setOnClickListener(v -> {
            AuthDtos.UserDto current = ContainerProvider.from(requireActivity()).getSessionManager().getUser();
            String id = current != null ? current.displayPublicId() : "";
            if (id == null || id.trim().isEmpty()) {
                Toast.makeText(requireContext(), "المعرف غير متاح", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("JEHO ID", id));
                Toast.makeText(requireContext(), "تم نسخ ID", Toast.LENGTH_SHORT).show();
            }
        });
        binding.tvProfileName.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.ProfileActivity.class)));
        binding.btnProfileSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.settings.SettingsActivity.class)));
        binding.actionWallet.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.wallet.BagActivity.class)));
        binding.actionVip.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.vip.VipActivity.class)));
        binding.actionAgency.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.agency.AgencyActivity.class)));
        binding.actionRechargeAgent.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.wallet.RechargeAgentActivity.class)));
        binding.actionStore.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.StoreHubActivity.class)));
        binding.actionEditProfileTop.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.EditProfileActivity.class)));
        binding.actionSecurity.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.settings.AccountSecurityActivity.class)));
        binding.actionActivities.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.MyEventsActivity.class)));
        binding.actionSupport.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.settings.CustomerSupportActivity.class)));
    }

    private void applySectionWallpaper() {
        if (binding == null || !isAdded()) return;
        String sectionWallpaper = RemoteTheme.backgroundUrl(requireContext(), "profile");
        if (sectionWallpaper == null || sectionWallpaper.trim().isEmpty()) {
            binding.imgThemeBg.setVisibility(View.GONE);
            binding.getRoot().setBackgroundResource(R.drawable.bg_home_full_gradient);
            return;
        }
        binding.getRoot().setBackgroundResource(R.drawable.bg_home_full_gradient);
        binding.imgThemeBg.setVisibility(View.VISIBLE);
        RemoteTheme.loadInto(binding.imgThemeBg, sectionWallpaper, R.drawable.bg_home_full_gradient);
    }

    private void refreshMyRoom() {
        if (binding == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        String myId = c.getSessionManager().getUserId();
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        List<RoomDtos.RoomDto> rooms = viewModel.getRooms().getValue();
        View.OnClickListener open = v -> RoomOpenChooser.open(requireActivity());
        MyRoomCardBinder.bind(binding.myRoomCardMe, me, myId, rooms, open, open);
    }

    private void bindList() {
        if (binding == null || roomAdapter == null || !isAdded()) return;
        List<RoomDtos.RoomDto> live = viewModel.getRooms().getValue();
        java.util.LinkedHashMap<String, RoomDtos.RoomDto> liveById = new java.util.LinkedHashMap<>();
        if (live != null) {
            for (RoomDtos.RoomDto r : live) {
                if (RoomBrowseFilter.isBrowsablePersonal(r)) liveById.put(r.id, r);
            }
        }
        RecentRoomsStore.retainOnly(requireContext(), liveById.keySet());

        List<RoomDtos.RoomDto> src;
        if (subTab == SUB_FOLLOWING) {
            src = viewModel.getFollowingRooms().getValue();
            binding.tvMeEmpty.setText(R.string.home_empty_following);
        } else {
            src = RecentRoomsStore.list(requireContext());
            binding.tvMeEmpty.setText(R.string.home_empty_recent);
        }
        List<RoomDtos.RoomDto> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (src != null) {
            for (RoomDtos.RoomDto r : src) {
                if (r == null || r.id == null || !seen.add(r.id)) continue;
                RoomDtos.RoomDto liveRoom = liveById.get(r.id);
                if (subTab == SUB_RECENT) {
                    if (liveRoom == null) continue;
                    out.add(liveRoom);
                } else if (RoomBrowseFilter.isBrowsablePersonal(r)) {
                    out.add(liveRoom != null ? liveRoom : r);
                }
            }
        }
        roomAdapter.submit(out);
        binding.tvMeEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).onFeedPageEmpty(TAB_ME, false);
        }
    }

    private void openRoom(RoomDtos.RoomDto room) {
        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).openVoiceRoomFromPage(room);
        }
    }

    public boolean canScrollListUp() {
        return binding != null
                && binding.recyclerMeRooms != null
                && binding.recyclerMeRooms.canScrollVertically(-1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
