package com.Dramizo.Series.presentation.home;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.FragmentHomeBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.util.ErrorToasts;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.offers.OffersBottomSheet;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.presentation.vip.VipActivity;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.presentation.web.PromoWebActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AssetIcons;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.FlagImages;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private HomeBannerAdapter homeBannerAdapter;
    private int bannerPage;
    private FragmentStateAdapter feedPagerAdapter;
    private static final int TAB_ME = 0;
    private static final int TAB_HOT = 1;
    private static final int TAB_LOCATION = 2;
    private static final int TAB_ACTIVITIES = 3;
    private static final int TAB_COUNT = 4;
    private static final String FLOATING_PREFS = "home_floating_widgets";
    private static final String OFFERS_DISMISSED_COUNT = "offers_dismissed_count";
    private int activeTab = TAB_HOT;
    private int currentOffersCount;
    private boolean walletLoading;
    private final List<com.Dramizo.Series.data.remote.dto.RoomDtos.RoomDto> roomCache = new ArrayList<>();
    private LeadingRoomAdapter exploreAdapter;
    private final java.util.LinkedHashSet<String> exploreRoomIds = new java.util.LinkedHashSet<>();
    private String selectedCountry;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RealtimeClient.RoomListener roomListListener;
    private final Runnable roomRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (binding != null && (activeTab == TAB_HOT || activeTab == TAB_LOCATION) && viewModel != null) {
                viewModel.refreshRoomsQuietly();
            }
            handler.postDelayed(this, 30000);
        }
    };
    private final Runnable bannerAutoScroll = new Runnable() {
        @Override public void run() {
            if (binding == null || homeBannerAdapter == null || homeBannerAdapter.count() <= 1) return;
            bannerPage = (bannerPage + 1) % homeBannerAdapter.count();
            binding.pagerBanners.setCurrentItem(bannerPage, true);
            handler.postDelayed(this, 4500);
        }
    };
    private final Runnable offersGiftAnimation = new Runnable() {
        @Override public void run() {
            if (binding == null) return;
            View box = binding.imgOffersGiftBox;
            box.animate().cancel();
            box.setRotation(0f);
            box.setScaleX(1f);
            box.setScaleY(1f);
            android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(
                    box, View.ROTATION, 0f, -10f, 10f, -8f, 8f, 0f);
            shake.setDuration(650);
            shake.start();
            box.animate()
                    .scaleX(1.16f).scaleY(0.88f)
                    .setStartDelay(520)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (binding == null) return;
                        box.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                        binding.lottieOffersBurst.setVisibility(View.VISIBLE);
                        binding.lottieOffersBurst.setProgress(0f);
                        binding.lottieOffersBurst.playAnimation();
                        handler.postDelayed(() -> {
                            if (binding != null) binding.lottieOffersBurst.setVisibility(View.INVISIBLE);
                        }, 1400);
                    }).start();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(HomeViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        applyRemoteHomeBackground();
        applyRemoteHomeAssets();

        homeBannerAdapter = new HomeBannerAdapter();
        homeBannerAdapter.setListener(this::openBannerLink);
        binding.pagerBanners.setAdapter(homeBannerAdapter);
        binding.pagerBanners.setOffscreenPageLimit(1);
        configureBannerPagerClip();
        binding.pagerBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                bannerPage = position;
                updateBannerDots(position);
                handler.removeCallbacks(bannerAutoScroll);
                handler.postDelayed(bannerAutoScroll, 4500);
            }
        });

        binding.pagerFeed.setOffscreenPageLimit(1);
        binding.pagerFeed.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                onLogicalTabSelected(logicalTabFromPager(position));
            }
        });

        binding.tvTabMe.setOnClickListener(v -> selectLogicalTab(TAB_ME));
        binding.tvTabHot.setOnClickListener(v -> selectLogicalTab(TAB_HOT));
        binding.tvTabLocation.setOnClickListener(v -> selectLogicalTab(TAB_LOCATION));
        binding.tvTabActivities.setOnClickListener(v -> selectLogicalTab(TAB_ACTIVITIES));
        binding.tabMeWrap.setOnClickListener(v -> selectLogicalTab(TAB_ME));
        binding.tabHotWrap.setOnClickListener(v -> selectLogicalTab(TAB_HOT));
        binding.tabLocationWrap.setOnClickListener(v -> selectLogicalTab(TAB_LOCATION));
        binding.tabActivitiesWrap.setOnClickListener(v -> selectLogicalTab(TAB_ACTIVITIES));
        setupFeedPager();
        refreshLocationTabChrome();
        onLogicalTabSelected(activeTab);
        // Home header icons from assets/icons by original file name.
        AssetIcons.load(binding.btnSearch, AssetIcons.HOME_SEARCH);
        AssetIcons.load(binding.imgFilterIcon, AssetIcons.HOME_FILTER);
        binding.btnSearch.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.search.SearchActivity.class)));
        binding.btnFilter.setOnClickListener(v -> showCountryPicker());
        setupOffersFloatingWidget();
        binding.swipe.setOnChildScrollUpCallback((parent, child) -> canActiveFeedScrollUp());
        binding.swipe.setOnRefreshListener(() -> {
            if (activeTab == TAB_ME) {
                viewModel.loadFollowingRooms();
                reapplyFeedPages();
            } else if (activeTab == TAB_HOT || activeTab == TAB_LOCATION) {
                viewModel.loadRooms();
            } else {
                reapplyFeedPages();
                binding.swipe.setRefreshing(false);
            }
        });
        binding.btnOffersGift.setOnClickListener(v -> {
            handler.removeCallbacks(offersGiftAnimation);
            offersGiftAnimation.run();
            OffersBottomSheet.show(getParentFragmentManager());
        });

        binding.tileBillion.setOnClickListener(v -> openRanking("gifts"));
        binding.tileRich.setOnClickListener(v -> openRanking("rich"));
        setupHomeActBanners();
        // Explore strip removed — Mikoo Hot is a single recycled grid (item_home_live_list).
        binding.tileActivity.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.profile.TaskCenterActivity.class)));

        if (binding.myRoomCardHome != null) {
            binding.myRoomCardHome.getRoot().setVisibility(View.GONE);
        }

        viewModel.getRooms().observe(getViewLifecycleOwner(), list -> {
            roomCache.clear();
            if (list != null) roomCache.addAll(list);
            exploreRoomIds.clear();
            // Feed pages observe rooms; never hide pager before they bind.
        });
        viewModel.getFollowingRooms().observe(getViewLifecycleOwner(), list -> reapplyFeedPages());
        viewModel.getBanners().observe(getViewLifecycleOwner(), this::bindServerBanners);
        // No local default slides — wait for server banners only.
        hideBannerSlider();
        viewModel.getRichRanking().observe(getViewLifecycleOwner(), list -> {
                bindTileAvatars(list,
                        new ImageView[]{binding.imgRichAvatar1, binding.imgRichAvatar2, binding.imgRichAvatar3},
                        new ImageView[]{binding.imgRichFrame1, binding.imgRichFrame2, binding.imgRichFrame3});
                bindRankFlipPage(0, list);
        });
        viewModel.getBillionRanking().observe(getViewLifecycleOwner(), list -> {
                bindTileAvatars(list,
                        new ImageView[]{binding.imgBillionAvatar1, binding.imgBillionAvatar2, binding.imgBillionAvatar3},
                        new ImageView[]{binding.imgBillionFrame1, binding.imgBillionFrame2, binding.imgBillionFrame3});
                bindRankFlipPage(1, list);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), l -> {
            boolean loading = Boolean.TRUE.equals(l);
            binding.swipe.setRefreshing(loading);
            if (!loading && isAdded()) {
                AppLoadingOverlay.hide(requireActivity());
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), e -> {
            if (e != null) ErrorToasts.show(requireContext(), e);
        });
        viewModel.getOffersCount().observe(getViewLifecycleOwner(), count -> {
            int n = count != null ? count : 0;
            currentOffersCount = n;
            SharedPreferences prefs = requireContext().getSharedPreferences(
                    FLOATING_PREFS, android.content.Context.MODE_PRIVATE);
            boolean dismissed = n > 0 && prefs.getInt(OFFERS_DISMISSED_COUNT, -1) == n;
            if (binding.btnOffersGift != null) {
                binding.btnOffersGift.setVisibility(n > 0 && !dismissed ? View.VISIBLE : View.GONE);
                if (n <= 0 || dismissed) {
                    handler.removeCallbacks(offersGiftAnimation);
                } else {
                    binding.btnOffersGift.post(this::restoreOffersPosition);
                }
            }
            if (binding.tvOffersBadge == null) return;
            binding.tvOffersBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            binding.tvOffersBadge.setText(String.valueOf(n));
        });

        viewModel.loadRooms();
        viewModel.loadBanners();
        viewModel.loadHomeRankings();
        viewModel.loadOffers();
        loadWalletBalance();
    }

    private void setupOffersFloatingWidget() {
        SharedPreferences prefs = requireContext().getSharedPreferences(
                FLOATING_PREFS, android.content.Context.MODE_PRIVATE);
        binding.btnOffersClose.setOnClickListener(v -> {
            prefs.edit().putInt(OFFERS_DISMISSED_COUNT, currentOffersCount).apply();
            handler.removeCallbacks(offersGiftAnimation);
            binding.btnOffersGift.setVisibility(View.GONE);
        });
        final float[] offset = new float[2];
        final float[] down = new float[2];
        final boolean[] moved = {false};
        final float slop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        binding.btnOffersGift.setOnTouchListener((view, event) -> {
            View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
            if (parent == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    offset[0] = event.getRawX() - view.getX();
                    offset[1] = event.getRawY() - view.getY();
                    moved[0] = false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    moved[0] = moved[0]
                            || Math.hypot(event.getRawX() - down[0], event.getRawY() - down[1]) > slop;
                    float maxX = Math.max(0f, parent.getWidth() - view.getWidth());
                    float maxY = Math.max(0f, parent.getHeight() - view.getHeight());
                    view.setX(Math.max(0f, Math.min(event.getRawX() - offset[0], maxX)));
                    view.setY(Math.max(0f, Math.min(event.getRawY() - offset[1], maxY)));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    saveOffersPosition(parent);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved[0]) {
                        view.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void restoreOffersPosition() {
        if (binding == null || binding.btnOffersGift == null) return;
        View view = binding.btnOffersGift;
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        if (parent == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(
                FLOATING_PREFS, android.content.Context.MODE_PRIVATE);
        if (!prefs.contains("offers_x")) return;
        float maxX = Math.max(0f, parent.getWidth() - view.getWidth());
        float maxY = Math.max(0f, parent.getHeight() - view.getHeight());
        view.setX(maxX * prefs.getFloat("offers_x", 1f));
        view.setY(maxY * prefs.getFloat("offers_y", 0.75f));
    }

    private void saveOffersPosition(View parent) {
        if (binding == null || binding.btnOffersGift == null) return;
        View view = binding.btnOffersGift;
        float maxX = Math.max(1f, parent.getWidth() - view.getWidth());
        float maxY = Math.max(1f, parent.getHeight() - view.getHeight());
        requireContext().getSharedPreferences(FLOATING_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putFloat("offers_x", Math.max(0f, Math.min(1f, view.getX() / maxX)))
                .putFloat("offers_y", Math.max(0f, Math.min(1f, view.getY() / maxY)))
                .apply();
    }

    private void loadWalletBalance() {
        // Home wallet bar removed (Mikoo-style). Balance lives in Bag / Profile.
    }

    private void setupExploreStrip() {
        // Intentionally unused — Mikoo uses one Hot list, not a second AppBar grid.
        View exploreRoot = binding != null ? binding.getRoot().findViewById(R.id.homeExplore) : null;
        if (exploreRoot != null) exploreRoot.setVisibility(View.GONE);
        exploreAdapter = null;
        exploreRoomIds.clear();
    }

    /** Always empty — Hot feed shows every room (Mikoo fragment_home_live_list). */
    java.util.Set<String> getExploreRoomIds() {
        return java.util.Collections.emptySet();
    }

    private void setupHomeActBanners() {
        if (binding == null || binding.homeActBanners == null) return;
        var act = binding.homeActBanners;
        if (act.rankBannerFlip.getChildCount() == 0) {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            View wealth = inflater.inflate(R.layout.item_home_rank_flip, act.rankBannerFlip, false);
            ((ImageView) wealth.findViewById(R.id.imgRankCover)).setImageResource(R.drawable.home_bg_wealth);
            ((android.widget.TextView) wealth.findViewById(R.id.tvRankTitle))
                    .setText(R.string.ranking_wealth);
            wealth.setOnClickListener(v -> openRanking("rich"));
            act.rankBannerFlip.addView(wealth);

            View charm = inflater.inflate(R.layout.item_home_rank_flip, act.rankBannerFlip, false);
            ((ImageView) charm.findViewById(R.id.imgRankCover)).setImageResource(R.drawable.home_bg_charm);
            ((android.widget.TextView) charm.findViewById(R.id.tvRankTitle))
                    .setText(R.string.ranking_charm);
            charm.setOnClickListener(v -> openRanking("popular"));
            act.rankBannerFlip.addView(charm);
            act.rankBannerFlip.startFlipping();
        }
        act.cpBannerCard.setOnClickListener(v ->
                startActivity(new Intent(requireContext(),
                        com.Dramizo.Series.presentation.cp.CpCenterActivity.class)));
        act.activityBannerCard.setOnClickListener(v ->
                startActivity(new Intent(requireContext(),
                        com.Dramizo.Series.presentation.profile.TaskCenterActivity.class)));
    }

    private void bindRankFlipPage(int pageIndex, List<MiscDtos.RankingEntryDto> list) {
        if (binding == null || binding.homeActBanners == null) return;
        ViewFlipper flip = binding.homeActBanners.rankBannerFlip;
        if (flip.getChildCount() <= pageIndex) return;
        View page = flip.getChildAt(pageIndex);
        ImageView[] avatars = {
                page.findViewById(R.id.imgRank1),
                page.findViewById(R.id.imgRank2),
                page.findViewById(R.id.imgRank3)
        };
        for (int i = 0; i < avatars.length; i++) {
            if (avatars[i] == null) continue;
            String url = null;
            if (list != null && i < list.size() && list.get(i) != null && list.get(i).user != null) {
                url = list.get(i).user.avatarUrl;
            }
            AvatarImageLoader.load(avatars[i], url);
        }
        // CP pair uses top rich #1 and #2 as decorative placeholders.
        if (pageIndex == 0 && binding.homeActBanners.imgCpLeft != null) {
            String left = null;
            String right = null;
            if (list != null && !list.isEmpty() && list.get(0) != null && list.get(0).user != null) {
                left = list.get(0).user.avatarUrl;
            }
            if (list != null && list.size() > 1 && list.get(1) != null && list.get(1).user != null) {
                right = list.get(1).user.avatarUrl;
            }
            AvatarImageLoader.load(binding.homeActBanners.imgCpLeft, left);
            AvatarImageLoader.load(binding.homeActBanners.imgCpRight, right);
        }
    }

    private void openRanking(String category) {
        Intent intent = new Intent(requireContext(), RankingActivity.class);
        intent.putExtra(RankingActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    public String getSelectedCountry() {
        return selectedCountry;
    }

    /** ISO country from profile, then device locale (e.g. TR for Turkey). */
    @Nullable
    public String getMyCountryCode() {
        try {
            AppContainer c = ContainerProvider.from(requireActivity());
            AuthDtos.UserDto me = c.getSessionManager().getUser();
            if (me != null && me.country != null && !me.country.trim().isEmpty()) {
                CountryCatalog.Entry e = CountryCatalog.resolve(me.country);
                if (e != null) return e.code;
            }
        } catch (Exception ignored) {}
        String localeCountry = Locale.getDefault().getCountry();
        if (localeCountry != null && !localeCountry.isEmpty()) {
            CountryCatalog.Entry e = CountryCatalog.resolve(localeCountry);
            if (e != null) return e.code;
        }
        return null;
    }

    public void openVoiceRoomFromPage(com.Dramizo.Series.data.remote.dto.RoomDtos.RoomDto room) {
        openVoiceRoom(room);
    }

    public void onFeedPageEmpty(int tab, boolean gridEmpty) {
        if (activeTab != tab) return;
        updateEmptyState(shouldShowEmptyState(tab, gridEmpty));
    }

    /** Grid empty → show empty state (no separate explore strip). */
    private boolean shouldShowEmptyState(int tab, boolean gridEmpty) {
        if (!gridEmpty) return false;
        if (tab == TAB_ME) return false;
        if (tab == TAB_ACTIVITIES) return false;
        return true;
    }

    private void refreshEmptyStateForActiveTab() {
        if (binding == null) return;
        if (activeTab == TAB_ME || activeTab == TAB_ACTIVITIES) {
            updateEmptyState(false);
            return;
        }
        boolean gridEmpty = true;
        for (Fragment f : getChildFragmentManager().getFragments()) {
            if (f instanceof HomeFeedPageFragment) {
                HomeFeedPageFragment page = (HomeFeedPageFragment) f;
                if (page.getTab() == activeTab) {
                    gridEmpty = page.isGridEmpty();
                    break;
                }
            }
        }
        updateEmptyState(shouldShowEmptyState(activeTab, gridEmpty));
    }

    public void onFeedPageSized(int tab, int heightPx) {
        // Feed uses match_parent + recycling — no wrap_content height sync.
    }

    /** Called from Hot feed when user scrolls near bottom. */
    public void onFeedNearBottom() {
        if (activeTab != TAB_HOT && activeTab != TAB_LOCATION) return;
        if (viewModel == null) return;
        viewModel.loadMoreRooms();
    }

    /** Refresh only when AppBar is expanded and the active grid is at the top. */
    private boolean canActiveFeedScrollUp() {
        if (binding == null) return false;
        View appBar = binding.getRoot().findViewById(R.id.homeAppBar);
        if (appBar != null && appBar.getTop() < 0) return true;
        for (Fragment f : getChildFragmentManager().getFragments()) {
            if (f instanceof HomeFeedPageFragment) {
                HomeFeedPageFragment page = (HomeFeedPageFragment) f;
                if (page.getTab() == activeTab && page.isResumed()) {
                    return page.canScrollListUp();
                }
            }
        }
        return false;
    }

    private int logicalTabFromPager(int pagerPos) {
        return pagerPos;
    }

    private int pagerPosFromLogicalTab(int tab) {
        return tab;
    }

    private void setupFeedPager() {
        feedPagerAdapter = new FragmentStateAdapter(this) {
            @NonNull @Override
            public Fragment createFragment(int position) {
                if (position == TAB_ME) return HomeMeFragment.newInstance();
                if (position == TAB_ACTIVITIES) return HomeActivitiesFragment.newInstance();
                return HomeFeedPageFragment.newInstance(position);
            }
            @Override public int getItemCount() { return TAB_COUNT; }
        };
        binding.pagerFeed.setAdapter(feedPagerAdapter);
        // Tabs only via top labels — no horizontal swipe between Me/Hot/Location/Activities.
        binding.pagerFeed.setUserInputEnabled(false);
        binding.pagerFeed.setOffscreenPageLimit(1);
        if (binding.pagerFeed.getCurrentItem() != TAB_HOT) {
            binding.pagerFeed.setCurrentItem(TAB_HOT, false);
        }
    }

    private void selectLogicalTab(int tab) {
        if (tab < TAB_ME || tab > TAB_ACTIVITIES) return;
        if (binding == null || binding.pagerFeed == null) return;
        onLogicalTabSelected(tab);
        if (binding.pagerFeed.getCurrentItem() != tab) {
            binding.pagerFeed.setCurrentItem(tab, true);
        }
    }

    private void onLogicalTabSelected(int tab) {
        activeTab = tab;
        styleTabs(tab);
        updateHeaderForTab(tab);
        if (tab == TAB_ME) {
            viewModel.loadFollowingRooms();
        } else if (tab == TAB_HOT || tab == TAB_LOCATION) {
            viewModel.loadRooms();
        }
        reapplyFeedPages();
        refreshEmptyStateForActiveTab();
    }

    private void updateHeaderForTab(int tab) {
        if (binding == null) return;
        View exploreRoot = binding.getRoot().findViewById(R.id.homeExplore);
        if (exploreRoot != null) exploreRoot.setVisibility(View.GONE);
        if (binding.homeActBanners != null) {
            binding.homeActBanners.getRoot().setVisibility(
                    tab == TAB_ACTIVITIES ? View.GONE : View.VISIBLE);
        }
        // Country filter picker only on Hot (Mikoo: flag lives next to country tab title).
        if (binding.btnFilter != null) {
            binding.btnFilter.setVisibility(tab == TAB_HOT ? View.VISIBLE : View.GONE);
        }
        if (binding.imgFilterIcon != null) {
            AssetIcons.load(binding.imgFilterIcon, AssetIcons.HOME_FILTER);
        }
        refreshLocationTabChrome();
    }

    /**
     * Mikoo home_country + iv_guoqi: always show the user's country name + flag as the
     * third tab label — not "موقعي"/Nearby, and not a pressable location picker.
     */
    private void refreshLocationTabChrome() {
        if (binding == null || binding.tvTabLocation == null) return;
        String code = getMyCountryCode();
        CountryCatalog.Entry e = code != null ? CountryCatalog.resolve(code) : null;
        if (e != null) {
            binding.tvTabLocation.setText(e.displayName());
            if (binding.imgTabLocationFlag != null) {
                FlagImages.bind(binding.imgTabLocationFlag, e.code);
            }
        } else {
            binding.tvTabLocation.setText(R.string.tab_location);
            if (binding.imgTabLocationFlag != null) {
                binding.imgTabLocationFlag.setVisibility(View.GONE);
                binding.imgTabLocationFlag.setImageDrawable(null);
            }
        }
    }

    private void reapplyFeedPages() {
        for (Fragment f : getChildFragmentManager().getFragments()) {
            if (f instanceof HomeFeedPageFragment) {
                ((HomeFeedPageFragment) f).reapplyFilter();
            } else if (f instanceof HomeMeFragment) {
                ((HomeMeFragment) f).reapply();
            }
        }
    }

    private void styleTabs(int selected) {
        // Mikoo fragment_home_live_pager: all tabs 17sp bold; active = full alpha.
        float activeAlpha = 1f;
        float inactiveAlpha = 0.45f;
        float activeSize = 17f;
        float inactiveSize = 17f;
        styleOneTab(binding.tvTabMe, binding.dotMe, selected == TAB_ME,
                activeAlpha, inactiveAlpha, activeSize, inactiveSize);
        styleOneTab(binding.tvTabHot, binding.dotHot, selected == TAB_HOT,
                activeAlpha, inactiveAlpha, activeSize, inactiveSize);
        styleOneTab(binding.tvTabLocation, binding.dotLocation, selected == TAB_LOCATION,
                activeAlpha, inactiveAlpha, activeSize, inactiveSize);
        if (binding.imgTabLocationFlag != null) {
            binding.imgTabLocationFlag.setAlpha(selected == TAB_LOCATION ? activeAlpha : inactiveAlpha);
        }
        styleOneTab(binding.tvTabActivities, binding.dotActivities, selected == TAB_ACTIVITIES,
                activeAlpha, inactiveAlpha, activeSize, inactiveSize);
    }

    private static void styleOneTab(android.widget.TextView tv, View dot, boolean selected,
                                    float activeAlpha, float inactiveAlpha,
                                    float activeSize, float inactiveSize) {
        if (tv == null) return;
        tv.setAlpha(selected ? activeAlpha : inactiveAlpha);
        tv.setTextSize(selected ? activeSize : inactiveSize);
        if (dot != null) {
            dot.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void showCountryPicker() {
        // Location tab uses fixed country chrome (name+flag); filter picker is Hot-only.
        if (activeTab != TAB_HOT) return;
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(requireContext());
        View sheet = getLayoutInflater().inflate(R.layout.dialog_country_filter, null, false);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        androidx.recyclerview.widget.RecyclerView rv = sheet.findViewById(R.id.recyclerCountries);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2));
        java.util.List<CountryCatalog.Entry> entries = CountryCatalog.all();
        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_country_chip, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                android.widget.TextView tv = holder.itemView.findViewById(R.id.tvCountry);
                android.widget.ImageView imgFlag = holder.itemView.findViewById(R.id.imgCountryFlag);
                if (position == 0) {
                    tv.setText(R.string.all_countries);
                    if (imgFlag != null) {
                        imgFlag.setVisibility(View.GONE);
                        imgFlag.setImageDrawable(null);
                    }
                } else {
                    CountryCatalog.Entry e = entries.get(position - 1);
                    tv.setText(e.nameAr);
                    FlagImages.bind(imgFlag, e.code);
                }
                boolean selected = position == 0
                        ? selectedCountry == null
                        : selectedCountry != null && selectedCountry.equals(entries.get(position - 1).code);
                holder.itemView.setAlpha(selected ? 1f : 0.75f);
                holder.itemView.setOnClickListener(v -> {
                    if (position == 0) selectedCountry = null;
                    else selectedCountry = entries.get(position - 1).code;
                    reapplyFeedPages();
                    dialog.dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return entries.size() + 1;
            }
        });
        dialog.show();
    }

    private void openVoiceRoom(com.Dramizo.Series.data.remote.dto.RoomDtos.RoomDto room) {
        if (room == null) return;
        com.Dramizo.Series.util.RecentRoomsStore.remember(requireContext(), room);
        if (room.hasPassword) {
            AuraDialogHelper.prompt(requireContext(),
                    getString(R.string.room_locked_title),
                    null,
                    getString(R.string.room_password_hint),
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    getString(R.string.enter_room),
                    pwd -> launchVoiceRoom(room, pwd));
            return;
        }
        launchVoiceRoom(room, null);
    }

    private void launchVoiceRoom(
            com.Dramizo.Series.data.remote.dto.RoomDtos.RoomDto room,
            @Nullable String pwd) {
        // Loading lives inside VoiceRoomActivity (Mikoo), not over the home list.
        Intent i = new Intent(requireContext(), VoiceRoomActivity.class);
        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, room.id);
        if (pwd != null && !pwd.isEmpty()) {
            i.putExtra(VoiceRoomActivity.EXTRA_PASSWORD, pwd);
        }
        // Bring existing singleTask room to front when reopening same live session.
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void updateEmptyState(boolean empty) {
        if (binding == null) return;
        if (binding.emptyRoomsWrap != null) {
            binding.emptyRoomsWrap.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (binding.tvEmptyRooms != null) {
            int msg = R.string.home_empty_hot;
            if (activeTab == TAB_LOCATION) {
                msg = getMyCountryCode() == null
                        ? R.string.home_location_unknown
                        : R.string.home_empty_location;
            } else if (activeTab == TAB_ME) {
                msg = R.string.home_empty_me;
            } else if (activeTab == TAB_ACTIVITIES) {
                msg = R.string.home_empty_activities;
            }
            binding.tvEmptyRooms.setText(msg);
            binding.tvEmptyRooms.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        // Keep pager always visible — GONE destroys feed fragments and rooms never return.
        if (binding.pagerFeed != null) {
            binding.pagerFeed.setVisibility(View.VISIBLE);
        }
    }

    private void openPromo(String url, String title) {
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.contains("vip") || "vip".equalsIgnoreCase(url)) {
                startActivity(new Intent(requireContext(), VipActivity.class));
                return;
            }
            if (lower.contains("recharge") || lower.contains("wallet") || "wallet".equalsIgnoreCase(url)) {
                startActivity(new Intent(requireContext(), BagActivity.class));
                return;
            }
            if (lower.contains("agenc")) {
                startActivity(new Intent(requireContext(), com.Dramizo.Series.presentation.agency.AgencyActivity.class));
                return;
            }
            if (lower.contains("contest") || lower.contains("مسابقة")) {
                startActivity(new Intent(requireContext(),
                        com.Dramizo.Series.presentation.contests.ContestsActivity.class));
                return;
            }
            if (lower.contains("ranking") || lower.contains("rank")) {
                startActivity(new Intent(requireContext(), RankingActivity.class));
                return;
            }
        }
        Intent i = new Intent(requireContext(), PromoWebActivity.class);
        i.putExtra(PromoWebActivity.EXTRA_URL, url);
        i.putExtra(PromoWebActivity.EXTRA_TITLE, title);
        startActivity(i);
    }

    private void configureBannerPagerClip() {
        binding.pagerBanners.post(() -> {
            View child = binding.pagerBanners.getChildAt(0);
            if (child instanceof RecyclerView rv) {
                rv.setClipToPadding(true);
                rv.setClipChildren(true);
                rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                rv.setPadding(0, 0, 0, 0);
            }
        });
    }

    private void bindServerBanners(List<MiscDtos.BannerDto> all) {
        if (binding == null || homeBannerAdapter == null) return;
        List<MiscDtos.BannerDto> filtered = filterBannersForLocale(all);
        if (filtered.isEmpty()) {
            hideBannerSlider();
            return;
        }

        final int[] pending = {filtered.size()};
        final List<MiscDtos.BannerDto> ready = new ArrayList<>();

        for (MiscDtos.BannerDto b : filtered) {
            String url = AssetCatalog.absoluteUrl(b.imageUrl);
            if (url == null || url.trim().isEmpty()) {
                if (--pending[0] == 0) applyLoadedBanners(ready);
                continue;
            }
            Glide.with(this)
                    .load(url)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e,
                                Object model,
                                @NonNull Target<Drawable> target,
                                boolean isFirstResource) {
                            if (--pending[0] == 0) applyLoadedBanners(ready);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                @NonNull Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                @NonNull DataSource dataSource,
                                boolean isFirstResource) {
                            ready.add(b);
                            if (--pending[0] == 0) applyLoadedBanners(ready);
                            return false;
                        }
                    })
                    .preload();
        }
    }

    /** Hide carousel when dashboard has zero live banners — no local/default art. */
    private void hideBannerSlider() {
        if (binding == null) return;
        handler.removeCallbacks(bannerAutoScroll);
        if (homeBannerAdapter != null) {
            homeBannerAdapter.submit(Collections.emptyList());
        }
        if (binding.bannerRow != null) binding.bannerRow.setVisibility(View.GONE);
        if (binding.pagerBanners != null) binding.pagerBanners.setVisibility(View.GONE);
        if (binding.bannerDots != null) {
            binding.bannerDots.removeAllViews();
            binding.bannerDots.setVisibility(View.GONE);
        }
        View carousel = binding.pagerBanners != null
                ? (View) binding.pagerBanners.getParent()
                : null;
        if (carousel != null) carousel.setVisibility(View.GONE);
    }

    private void applyLoadedBanners(List<MiscDtos.BannerDto> ready) {
        if (binding == null || !isAdded()) return;
        if (ready == null || ready.isEmpty()) {
            hideBannerSlider();
            return;
        }
        List<MiscDtos.BannerDto> slides = ready.size() > 3 ? new ArrayList<>(ready.subList(0, 3)) : ready;
        applyBannerSlider(slides);
    }

    private void applyBannerSlider(List<MiscDtos.BannerDto> slides) {
        if (binding == null || homeBannerAdapter == null || slides == null || slides.isEmpty()) {
            hideBannerSlider();
            return;
        }
        View carousel = (View) binding.pagerBanners.getParent();
        if (carousel != null) carousel.setVisibility(View.VISIBLE);
        homeBannerAdapter.submit(slides);
        binding.bannerRow.setVisibility(View.GONE);
        binding.pagerBanners.setVisibility(View.VISIBLE);
        binding.bannerDots.setVisibility(View.VISIBLE);
        bindBannerDots(slides.size());
        updateBannerDots(0);
        bannerPage = 0;
        binding.pagerBanners.setCurrentItem(0, false);
        // Soft page peek like Mikoo carousel.
        binding.pagerBanners.setPageTransformer((page, position) -> {
            float abs = Math.abs(position);
            page.setScaleY(0.92f + (1f - Math.min(1f, abs)) * 0.08f);
            page.setAlpha(0.55f + (1f - Math.min(1f, abs)) * 0.45f);
        });
        handler.removeCallbacks(bannerAutoScroll);
        if (slides.size() > 1) {
            handler.postDelayed(bannerAutoScroll, 3500);
        }
    }

    private List<MiscDtos.BannerDto> filterBannersForLocale(List<MiscDtos.BannerDto> all) {
        List<MiscDtos.BannerDto> out = new ArrayList<>();
        if (all == null) return out;
        boolean ar = Locale.getDefault().getLanguage().toLowerCase().startsWith("ar");
        for (MiscDtos.BannerDto b : all) {
            if (b == null || b.imageUrl == null || b.imageUrl.trim().isEmpty()) continue;
            String bl = b.lang != null ? b.lang.trim().toLowerCase() : "all";
            if ("all".equals(bl) || bl.isEmpty()) out.add(b);
            else if (ar && "ar".equals(bl)) out.add(b);
            else if (!ar && "en".equals(bl)) out.add(b);
        }
        if (out.isEmpty()) {
            for (MiscDtos.BannerDto b : all) {
                if (b != null && b.imageUrl != null && !b.imageUrl.trim().isEmpty()) out.add(b);
            }
        }
        return out.size() > 6 ? out.subList(0, 6) : out;
    }

    private void bindBannerDots(int count) {
        if (binding == null || binding.bannerDots == null) return;
        binding.bannerDots.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (6 * d), (int) (6 * d));
            if (i > 0) lp.setMarginStart((int) (5 * d));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_banner_dot_inactive);
            binding.bannerDots.addView(dot);
        }
    }

    private void updateBannerDots(int active) {
        if (binding == null || binding.bannerDots == null) return;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < binding.bannerDots.getChildCount(); i++) {
            View dot = binding.bannerDots.getChildAt(i);
            boolean on = i == active;
            dot.setBackgroundResource(on ? R.drawable.bg_banner_dot_active : R.drawable.bg_banner_dot_inactive);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            int size = (int) ((on ? 7 : 6) * d);
            lp.width = size;
            lp.height = size;
            dot.setLayoutParams(lp);
        }
    }

        private void openBannerLink(MiscDtos.BannerDto banner) {
        if (banner == null) return;
        String link = banner.link != null ? banner.link.trim().toLowerCase() : "";
        if ("rich".equals(link) || "gifts".equals(link) || "popular".equals(link)) {
            openRanking(link);
            return;
        }
        if (link.isEmpty()) return;
        if ("party".equals(link) || link.contains("room")) {
            binding.pagerFeed.setCurrentItem(TAB_HOT, true);
            return;
        }
        if ("wallet".equals(link) || link.contains("bag") || link.contains("recharge")) {
            startActivity(new Intent(requireContext(), BagActivity.class));
            return;
        }
        if ("drama".equals(link)) {
            if (requireActivity() instanceof com.Dramizo.Series.presentation.main.MainActivity) {
                ((com.Dramizo.Series.presentation.main.MainActivity) requireActivity()).go(R.id.nav_drama);
            }
            return;
        }
        if ("messages".equals(link) || "chat".equals(link)) {
            if (requireActivity() instanceof com.Dramizo.Series.presentation.main.MainActivity) {
                ((com.Dramizo.Series.presentation.main.MainActivity) requireActivity()).go(R.id.nav_messages);
            }
            return;
        }
        if ("tasks".equals(link) || link.contains("task")) {
            startActivity(new Intent(requireContext(),
                    com.Dramizo.Series.presentation.profile.TaskCenterActivity.class));
            return;
        }
        if ("ranking".equals(link) || link.contains("rank")) {
            startActivity(new Intent(requireContext(), RankingActivity.class));
            return;
        }
        String title = banner.title != null ? banner.title : "";
        openPromo(banner.link, title);
    }

    private void bindTileAvatars(List<MiscDtos.RankingEntryDto> list, ImageView[] avatars, ImageView[] frames) {
        if (binding == null || avatars == null) return;
        for (int i = 0; i < avatars.length; i++) {
            AvatarCosmetics.bindWear(avatars[i], frames != null && i < frames.length ? frames[i] : null, null);
        }
        if (list == null) return;
        for (int i = 0; i < avatars.length && i < list.size(); i++) {
            MiscDtos.RankingEntryDto entry = list.get(i);
            AvatarCosmetics.bindWear(
                    avatars[i],
                    frames != null && i < frames.length ? frames[i] : null,
                    entry != null ? entry.user : null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocationTabChrome();
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            AppFeatures.refresh(c);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                // Feature flags refreshed (e.g. female-only voice hosts).
            });
        });
        if (binding != null
                && binding.btnOffersGift.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(offersGiftAnimation);
            handler.postDelayed(offersGiftAnimation, 600);
        }
        if (binding != null && homeBannerAdapter != null && homeBannerAdapter.count() > 1) {
            handler.removeCallbacks(bannerAutoScroll);
            handler.postDelayed(bannerAutoScroll, 4500);
        }
        loadWalletBalance();
        if (viewModel == null) return;
        if (activeTab == TAB_ME) {
            viewModel.loadFollowingRooms();
            reapplyFeedPages();
        } else if (activeTab == TAB_ACTIVITIES) {
            /* activities fragment is static */
        } else {
            viewModel.loadRooms();
            viewModel.loadHomeRankings();
            attachRoomListRealtime();
            handler.removeCallbacks(roomRefreshRunnable);
            handler.postDelayed(roomRefreshRunnable, 12000);
        }
    }

    private void attachRoomListRealtime() {
        if (roomListListener != null) return;
        roomListListener = new RealtimeClient.RoomListener() {
            @Override public void onConnected() {}
            @Override public void onDisconnected() {}
            @Override public void onUserJoined(String roomId, String userId, String username) {
                viewModel.refreshRoomsQuietly();
            }
            @Override public void onUserLeft(String roomId, String userId) {
                viewModel.refreshRoomsQuietly();
            }
            @Override
            public void onRoomEvent(String roomId, String event, JsonObject payload, String fromUserId, String fromUsername) {
                if ("room:updated".equals(event) || "room:closed".equals(event)) {
                    viewModel.refreshRoomsQuietly();
                }
            }
        };
        RealtimeClient.getInstance().addRoomListener(roomListListener);
    }

    private void applyRemoteHomeBackground() {
        if (binding == null || binding.imgHomeBg == null) return;
        String url = com.Dramizo.Series.util.RemoteTheme.backgroundUrl(requireContext(), "home");
        if (url == null || url.isEmpty()) {
            binding.imgHomeBg.setImageResource(R.drawable.home_page_bg);
            return;
        }
        com.Dramizo.Series.util.RemoteTheme.loadInto(
                binding.imgHomeBg, url, R.drawable.home_page_bg);
    }

    private void applyRemoteHomeAssets() {
        // Icons come from fragment_home.xml android:src — do not override in code.
    }

    private void refreshMyRoomCard() {
        // My room lives in HomeMeFragment (Mikoo «أنا»).
    }

    private void detachRoomListRealtime() {
        if (roomListListener != null) {
            RealtimeClient.getInstance().removeRoomListener(roomListListener);
            roomListListener = null;
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(roomRefreshRunnable);
        handler.removeCallbacks(offersGiftAnimation);
        handler.removeCallbacks(bannerAutoScroll);
        detachRoomListRealtime();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(roomRefreshRunnable);
        handler.removeCallbacks(offersGiftAnimation);
        handler.removeCallbacks(bannerAutoScroll);
        detachRoomListRealtime();
        super.onDestroyView();
        binding = null;
        homeBannerAdapter = null;
    }
}
