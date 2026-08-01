package com.Dramizo.Series.presentation.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import com.Dramizo.Series.util.MyRoomLauncher;
import com.Dramizo.Series.util.RecentRoomsStore;
import com.Dramizo.Series.util.RoomBrowseFilter;

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
        binding.recyclerMeRooms.setNestedScrollingEnabled(true);
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
    public void onResume() {
        super.onResume();
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

    private void refreshMyRoom() {
        if (binding == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        String myId = c.getSessionManager().getUserId();
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        List<RoomDtos.RoomDto> rooms = viewModel.getRooms().getValue();
        View.OnClickListener open = v -> MyRoomLauncher.open(requireActivity());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
