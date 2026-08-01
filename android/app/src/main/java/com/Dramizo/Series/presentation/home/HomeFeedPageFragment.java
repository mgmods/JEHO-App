package com.Dramizo.Series.presentation.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.RecentRoomsStore;

import java.util.ArrayList;
import java.util.List;

/** Hot / Location feed — recycled grid, pageSize load-more with Mikoo SVGA footer. */
public class HomeFeedPageFragment extends Fragment {
    public static final String ARG_TAB = "tab";
    public static final int TAB_HOT = 1;
    public static final int TAB_LOCATION = 2;

    private RecyclerView recycler;
    private HomeViewModel viewModel;
    private PartyRoomAdapter roomAdapter;
    private GridLayoutManager layoutManager;
    private int tab;
    private int lastSubmittedSize;

    public static HomeFeedPageFragment newInstance(int tab) {
        HomeFeedPageFragment f = new HomeFeedPageFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_TAB, tab);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        recycler = new RecyclerView(requireContext());
        recycler.setId(View.generateViewId());
        recycler.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recycler.setHasFixedSize(true);
        recycler.setNestedScrollingEnabled(true);
        recycler.setClipToPadding(false);
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recycler.setItemAnimator(null);
        recycler.setItemViewCacheSize(20);
        float d = getResources().getDisplayMetrics().density;
        // Mikoo fragment_home_live_list: paddingStart=3, paddingEnd=10
        recycler.setPadding((int) (3 * d), (int) (10 * d), (int) (10 * d), (int) (96 * d));
        layoutManager = new GridLayoutManager(requireContext(), 2);
        recycler.setLayoutManager(layoutManager);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (tab != TAB_HOT && tab != TAB_LOCATION) return;
                if (layoutManager == null || roomAdapter == null) return;
                int total = roomAdapter.getRoomCount();
                if (total == 0) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= total - 4) {
                    Fragment parent = getParentFragment();
                    if (parent instanceof HomeFragment) {
                        ((HomeFragment) parent).onFeedNearBottom();
                    }
                }
            }
        });
        return recycler;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tab = getArguments() != null ? getArguments().getInt(ARG_TAB, TAB_HOT) : TAB_HOT;
        viewModel = new ViewModelProvider(requireParentFragment(),
                new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(HomeViewModel.class);

        roomAdapter = new PartyRoomAdapter(this::openRoom);
        roomAdapter.attachSpanSize(layoutManager);
        recycler.setAdapter(roomAdapter);
        viewModel.getRooms().observe(getViewLifecycleOwner(), this::bindRooms);
        viewModel.getLoadingMore().observe(getViewLifecycleOwner(), loading -> {
            if (roomAdapter == null) return;
            boolean show = Boolean.TRUE.equals(loading) && viewModel.hasMoreRooms();
            // Location tab also paginates from same list.
            if (tab == TAB_HOT || tab == TAB_LOCATION) {
                roomAdapter.setLoadingMore(show);
            }
        });
    }

    public int getTab() {
        return tab;
    }

    public boolean canScrollListUp() {
        return recycler != null && recycler.canScrollVertically(-1);
    }

    @Nullable
    public RecyclerView getRecycler() {
        return recycler;
    }

    public boolean isGridEmpty() {
        return roomAdapter == null || roomAdapter.getRoomCount() == 0;
    }

    public void reapplyFilter() {
        if (viewModel == null || recycler == null) return;
        bindRooms(viewModel.getRooms().getValue());
    }

    private void bindRooms(List<RoomDtos.RoomDto> list) {
        if (roomAdapter == null) return;
        boolean hot = tab == TAB_HOT;
        List<RoomDtos.RoomDto> filtered = filterRooms(list, hot);
        int newSize = filtered.size();
        if (lastSubmittedSize > 0 && newSize > lastSubmittedSize
                && idsPrefixMatch(filtered, lastSubmittedSize)) {
            roomAdapter.append(filtered.subList(lastSubmittedSize, newSize));
        } else {
            roomAdapter.submit(filtered);
        }
        lastSubmittedSize = newSize;
        notifyHostEmpty(filtered.isEmpty());
    }

    private boolean idsPrefixMatch(List<RoomDtos.RoomDto> filtered, int prefix) {
        if (roomAdapter == null || roomAdapter.getRoomCount() < prefix) return false;
        return roomAdapter.getRoomCount() == prefix;
    }

    private String countryFilter() {
        Fragment parent = getParentFragment();
        if (!(parent instanceof HomeFragment)) return null;
        HomeFragment home = (HomeFragment) parent;
        if (tab == TAB_LOCATION) return home.getMyCountryCode();
        return home.getSelectedCountry();
    }

    private List<RoomDtos.RoomDto> filterRooms(List<RoomDtos.RoomDto> src, boolean unused) {
        List<RoomDtos.RoomDto> out = new ArrayList<>();
        if (src == null) return out;
        String c = countryFilter();
        if (tab == TAB_LOCATION && (c == null || c.isEmpty())) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (RoomDtos.RoomDto r : src) {
            if (r == null || r.id == null) continue;
            if (!seen.add(r.id)) continue;
            if (c == null || c.isEmpty()) {
                out.add(r);
                continue;
            }
            String country = r.host != null ? r.host.country : null;
            if (CountryCatalog.matchesFilter(country, c)) out.add(r);
        }
        return out;
    }

    private void notifyHostEmpty(boolean empty) {
        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).onFeedPageEmpty(tab, empty);
        }
    }

    private void openRoom(RoomDtos.RoomDto room) {
        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).openVoiceRoomFromPage(room);
        } else if (room != null && room.id != null) {
            RecentRoomsStore.remember(requireContext(), room);
            Intent i = new Intent(requireContext(), VoiceRoomActivity.class);
            i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, room.id);
            startActivity(i);
        }
    }
}
