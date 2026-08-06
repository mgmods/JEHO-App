package com.Dramizo.Series.presentation.gifts;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.DialogRoomGiftBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.GiftAudioFx;
import com.Dramizo.Series.widget.GiftUserAvatarView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class GiftBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "gifts";
    private static final String ARG_RECEIVER = "receiver";
    private static final String ARG_ROOM = "room";
    private static final String ARG_CHAT = "chat";
    private static final String STATE_GIFT = "sel_gift";
    private static final String STATE_QTY = "qty";
    private static final String STATE_ALL_MIC = "all_mic";

    @Nullable private GiftDtos.GiftDto selected;
    @Nullable private String receiverId;
    @Nullable private String pendingGiftId;
    @Nullable private GiftRecipient selectedRecipient;
    private boolean sending;
    private boolean sendToAllMic;
    private boolean chatMode;
    private int giftQty = 1;

    private final List<GiftRecipient> micRecipients = new ArrayList<>();
    private final List<String> lastTargets = new ArrayList<>();
    private final List<GiftDtos.GiftDto> allGifts = new ArrayList<>();
    @Nullable private String giftTypeFilter;

    private DialogRoomGiftBinding binding;
    private GiftViewModel vm;
    @Nullable private GiftCategoryPagerAdapter giftPageAdapter;
    @Nullable private TabLayoutMediator tabMediator;
    private boolean syncingTabs;
    @Nullable private ProgressBar loadingBar;
    @Nullable private PopupWindow qtyPopup;
    @Nullable private Long lastCoinsBalance;
    private static final String[] FALLBACK_TAB_LABELS = {
            "عادي", "حظ", "كومبو", "مميز"
    };
    private static final String[] FALLBACK_TAB_KEYS = {
            "normal", "lucky", "combo", "premium"
    };
    private final List<GiftDtos.GiftCategoryDto> tabCategories = new ArrayList<>();
    private String[] tabLabels = FALLBACK_TAB_LABELS;
    private String[] tabKeys = FALLBACK_TAB_KEYS;

    // From included layouts (not exposed on DialogRoomGiftBinding without include ids).
    @Nullable private com.Dramizo.Series.widget.XProgressBar wealthProgress;
    @Nullable private android.widget.TextView wealthLevelDesc;
    @Nullable private android.widget.TextView wealthLevelBadge;
    @Nullable private android.widget.ImageView wealthLevelAvatar;
    @Nullable private android.widget.ImageView wealthLevelFrame;
    @Nullable private View noCpLayout;
    @Nullable private View hascpLayout;

    // ─── Factory ──────────────────────────────────────────────────────────────

    /** Called from VoiceRoom (has liveId param that is unused — kept for compat). */
    public static void show(FragmentManager fm, String receiverId,
                            String liveId, String roomId) {
        show(fm, receiverId, roomId, false);
    }

    /** Called from chat / DM. */
    public static void showForChat(FragmentManager fm, String receiverId) {
        show(fm, receiverId, null, true);
    }

    public static void show(FragmentManager fm, String receiverId,
                            String roomId, boolean chatMode) {
        if (fm == null || fm.isStateSaved()) return;
        Fragment old = fm.findFragmentByTag(TAG);
        if (old != null) {
            fm.beginTransaction().remove(old).commitAllowingStateLoss();
            try { fm.executePendingTransactions(); } catch (Exception ignored) {}
        }
        GiftBottomSheet sheet = new GiftBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_RECEIVER, receiverId);
        args.putString(ARG_ROOM, roomId);
        args.putBoolean(ARG_CHAT, chatMode);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    // ─── Dialog setup ─────────────────────────────────────────────────────────

    @Override
    public int getTheme() {
        return R.style.Theme_AuraLive_BottomSheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;

            // Same as room settings: hug content — no forced tall empty sheet.
            sheet.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.color_gift_dialog_bg));
            ViewGroup.LayoutParams lp = sheet.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(lp);
            }

            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setFitToContents(true);
            behavior.setSkipCollapsed(true);
            // Keep sheet fixed so vertical swipes scroll the gift grid, not the sheet.
            behavior.setDraggable(false);
            int screenH = sheet.getResources().getDisplayMetrics().heightPixels;
            behavior.setMaxHeight(Math.round(screenH * 0.88f));
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            // Gift grid: ~3.5 rows visible, then scroll for the rest.
            if (binding != null && binding.vpGiftListContainer != null) {
                ViewGroup.LayoutParams vplp = binding.vpGiftListContainer.getLayoutParams();
                if (vplp != null) {
                    vplp.height = Math.max(
                            Math.round(220 * sheet.getResources().getDisplayMetrics().density),
                            Math.round(screenH * 0.36f));
                    binding.vpGiftListContainer.setLayoutParams(vplp);
                }
            }

            if (dialog.getWindow() != null) {
                int bg = ContextCompat.getColor(requireContext(), R.color.color_gift_dialog_bg);
                dialog.getWindow().setDimAmount(0.45f);
                // Same solid Mikoo panel under Android nav buttons (no light strip).
                dialog.getWindow().addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                dialog.getWindow().clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                dialog.getWindow().setNavigationBarColor(bg);
                dialog.getWindow().setStatusBarColor(0x00000000);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    dialog.getWindow().setNavigationBarContrastEnforced(false);
                }
            }

            // Pad bottom dock for nav inset so send/balance stay above buttons.
            if (binding != null && binding.clBottom != null) {
                binding.clBottom.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.color_gift_dialog_bg));
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                        binding.clBottom, (v, insets) -> {
                            int nav = insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                                    v.getPaddingRight(), Math.max(dp(10), nav));
                            return insets;
                        });
                androidx.core.view.ViewCompat.requestApplyInsets(binding.clBottom);
            }
            publishSeatGiftSelection();
        });
        return dialog;
    }

    // ─── View ─────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedState) {
        binding = DialogRoomGiftBinding.inflate(inflater, container, false);

        vm = new ViewModelProvider(requireActivity(),
                new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(GiftViewModel.class);

        Bundle args = getArguments();
        receiverId = args != null ? args.getString(ARG_RECEIVER) : null;
        String roomId = args != null ? args.getString(ARG_ROOM) : null;
        chatMode = args != null && args.getBoolean(ARG_CHAT, false);

        if (savedState != null) {
            pendingGiftId = savedState.getString(STATE_GIFT);
            giftQty = Math.max(1, savedState.getInt(STATE_QTY, 1));
            sendToAllMic = savedState.getBoolean(STATE_ALL_MIC, false);
            if (receiverId == null || receiverId.isEmpty()) {
                receiverId = savedState.getString(ARG_RECEIVER);
            }
        }

        bindIncludedViews();
        hideUnusedMikooPanels();
        setupLoadingBar();
        setupGiftPagerAndTabs();
        setupRecipientStrip(chatMode);
        bindWealthLevel();

        View.OnClickListener openCoins = v -> openRechargeOnly();
        if (binding.rlWealth != null) binding.rlWealth.setOnClickListener(openCoins);
        if (binding.tvGiftUserGold != null) binding.tvGiftUserGold.setOnClickListener(openCoins);

        binding.bltvSendGift.setOnClickListener(v -> sendGift(roomId));
        View.OnClickListener openQty = v -> showQtyPopup();
        if (binding.bltvSendGiftCount != null) binding.bltvSendGiftCount.setOnClickListener(openQty);
        if (binding.ivFx != null) binding.ivFx.setOnClickListener(openQty);
        if (binding.lineSend != null) {
            // Qty chips via popup only — leave giftNumberRv GONE.
            binding.lineSend.setOnClickListener(null);
        }

        vm.getGifts().observe(getViewLifecycleOwner(), gifts -> {
            if (binding == null || giftPageAdapter == null) return;
            allGifts.clear();
            if (gifts != null) allGifts.addAll(gifts);
            applyFilter();
            setLoading(false);
            // Room only: warm gift MP4s. DM chat must not buffer dozens of videos (OOM / process kill).
            if (!chatMode && gifts != null && !gifts.isEmpty() && getContext() != null) {
                java.util.ArrayList<String> warm = new java.util.ArrayList<>();
                for (com.Dramizo.Series.data.remote.dto.GiftDtos.GiftDto g : gifts) {
                    if (g == null) continue;
                    String resolved = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(
                            g.name, g.iconUrl, g.animationUrl);
                    if (resolved != null) warm.add(resolved);
                    else if (g.animationUrl != null) warm.add(g.animationUrl);
                    if (warm.size() > 40) break;
                }
                com.Dramizo.Series.util.NativeRoomEffectsView.preloadGiftUrls(
                        requireContext().getApplicationContext(), warm);
            }
        });

        vm.getCategories().observe(getViewLifecycleOwner(), this::applyCategories);

        vm.getCoinsBalance().observe(getViewLifecycleOwner(), this::bindCoins);

        // Clear sticky so observe does not immediately re-run last room/DM send.
        vm.clearSent();
        vm.getSent().observe(getViewLifecycleOwner(),
                result -> onGiftSent(result, roomId));

        vm.getError().observe(getViewLifecycleOwner(), e -> {
            sending = false;
            if (binding == null) return;
            setLoading(false);
            refreshSendBtn();
            updateDockText();
            if (e == null || e.isEmpty()) return;
            if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(e)) {
                Toast.makeText(requireContext(),
                        "رصيدك غير كافٍ — اشحن عملاتك", Toast.LENGTH_SHORT).show();
                openRechargeOnly();
            } else {
                Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            }
        });

        // Spinner only when we truly have nothing to show yet.
        setLoading(!vm.hasGiftsNow());
        updateDockText();
        // Already-warm catalog: paint instantly without waiting for network.
        if (vm.hasGiftsNow()) {
            List<GiftDtos.GiftDto> warm = vm.getGifts().getValue();
            if (warm != null) {
                allGifts.clear();
                allGifts.addAll(warm);
                applyFilter();
            }
            List<GiftDtos.GiftCategoryDto> warmCats = vm.getCategories().getValue();
            if (warmCats != null) applyCategories(warmCats);
        }
        vm.load();
        vm.loadWallet();

        return binding.getRoot();
    }

    private void bindIncludedViews() {
        if (binding == null) return;
        View root = binding.getRoot();
        wealthProgress = root.findViewById(R.id.progress_bar);
        wealthLevelDesc = root.findViewById(R.id.tv_level_desc);
        wealthLevelBadge = root.findViewById(R.id.tv_level_badge);
        wealthLevelAvatar = root.findViewById(R.id.iv_level);
        wealthLevelFrame = root.findViewById(R.id.iv_level_frame);
        noCpLayout = root.findViewById(R.id.no_cp_layout);
        hascpLayout = root.findViewById(R.id.hascp_layout);
    }

    private void hideUnusedMikooPanels() {
        if (binding == null) return;
        binding.giftNumberRv.setVisibility(View.GONE);
        if (noCpLayout != null) noCpLayout.setVisibility(View.GONE);
        if (hascpLayout != null) hascpLayout.setVisibility(View.GONE);
        // Promotional top band used only for CP tab banner.
        if (binding.flTop != null) binding.flTop.setVisibility(View.GONE);
    }

    private void refreshCpBanner() {
        if (binding == null) return;
        boolean isCpTab = giftTypeFilter != null
                && "cp".equalsIgnoreCase(giftTypeFilter.trim());
        if (!isCpTab) {
            if (noCpLayout != null) noCpLayout.setVisibility(View.GONE);
            if (hascpLayout != null) hascpLayout.setVisibility(View.GONE);
            if (binding.flTop != null) binding.flTop.setVisibility(View.GONE);
            return;
        }
        if (binding.flTop != null) binding.flTop.setVisibility(View.VISIBLE);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.CpStatusDto> r = ApiCall.execute(c.getUserApi().myCp());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                boolean has = r.success && r.data != null && r.data.hasCp && r.data.partner != null;
                if (noCpLayout != null) noCpLayout.setVisibility(has ? View.GONE : View.VISIBLE);
                if (hascpLayout != null) hascpLayout.setVisibility(has ? View.VISIBLE : View.GONE);
                if (!has && noCpLayout != null) {
                    noCpLayout.setOnClickListener(v ->
                            startActivity(new android.content.Intent(
                                    requireContext(),
                                    com.Dramizo.Series.presentation.cp.CpCenterActivity.class)));
                    View marquee = noCpLayout.findViewById(R.id.marquee_view);
                    if (marquee instanceof android.widget.TextView) {
                        ((android.widget.TextView) marquee).setText(
                                "لا يوجد CP — اضغط هنا لإرسال طلب CP");
                    }
                }
                if (has) {
                    MiscDtos.CpStatusDto st = r.data;
                    android.widget.TextView lv = hascpLayout != null
                            ? hascpLayout.findViewById(R.id.has_level) : null;
                    android.widget.TextView desc = hascpLayout != null
                            ? hascpLayout.findViewById(R.id.has_level_desc) : null;
                    android.widget.ProgressBar bar = hascpLayout != null
                            ? hascpLayout.findViewById(R.id.has_cp_progress) : null;
                    com.google.android.material.imageview.ShapeableImageView meA =
                            hascpLayout != null ? hascpLayout.findViewById(R.id.has_cp_me) : null;
                    com.google.android.material.imageview.ShapeableImageView peerA =
                            hascpLayout != null ? hascpLayout.findViewById(R.id.has_cp_right) : null;
                    if (lv != null) lv.setText("LV." + Math.max(0, st.level));
                    if (desc != null) {
                        long next = Math.max(st.nextLevelAt, st.level * 500L);
                        desc.setText(String.format(java.util.Locale.US,
                                "ألفة %,d · الهدف %,d", st.bondScore, next));
                    }
                    if (bar != null) {
                        bar.setMax(100);
                        long span = 500;
                        long into = st.bondScore % span;
                        bar.setProgress((int) Math.min(100, (into * 100) / span));
                    }
                    String myAvatar = null;
                    try {
                        Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
                        if (me.success && me.data != null) myAvatar = me.data.avatarUrl;
                    } catch (Exception ignored) {}
                    if (meA != null) AvatarImageLoader.load(meA, myAvatar);
                    if (peerA != null && st.partner != null) {
                        AvatarImageLoader.load(peerA, st.partner.avatarUrl);
                    }
                    if (hascpLayout != null) {
                        hascpLayout.setOnClickListener(v ->
                                startActivity(new android.content.Intent(
                                        requireContext(),
                                        com.Dramizo.Series.presentation.cp.CpCenterActivity.class)));
                    }
                }
            });
        });
    }

    private void setupLoadingBar() {
        if (binding == null) return;
        ConstraintLayout root = (ConstraintLayout) binding.getRoot();
        loadingBar = new ProgressBar(requireContext());
        loadingBar.setId(View.generateViewId());
        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                dp(36), dp(36));
        lp.topToTop = binding.vpGiftListContainer.getId();
        lp.bottomToBottom = binding.vpGiftListContainer.getId();
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        loadingBar.setVisibility(View.GONE);
        root.addView(loadingBar, lp);
    }

    private void setLoading(boolean on) {
        if (loadingBar != null) {
            loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    // ─── Gift pager = category tabs (Mikoo MagicIndicator ↔ ViewPager) ─────────

    private void setupGiftPagerAndTabs() {
        if (binding == null) return;

        giftPageAdapter = new GiftCategoryPagerAdapter(gift -> {
            if (gift != null) applySelectedGift(gift);
        });
        binding.vpGiftListContainer.setAdapter(giftPageAdapter);
        binding.vpGiftListContainer.setOffscreenPageLimit(1);
        // Seed empty pages so TabLayoutMediator can attach.
        List<List<GiftDtos.GiftDto>> empty = new ArrayList<>();
        for (int i = 0; i < tabLabels.length; i++) empty.add(new ArrayList<>());
        giftPageAdapter.submit(empty, null);

        TabLayout tabs = binding.miGiftCategory;
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.setTabGravity(TabLayout.GRAVITY_START);
        tabs.setInlineLabel(true);

        if (tabMediator != null) tabMediator.detach();
        tabMediator = new TabLayoutMediator(tabs, binding.vpGiftListContainer,
                (tab, position) -> {
                    android.widget.TextView tv = new android.widget.TextView(requireContext());
                    String label = position >= 0 && position < tabLabels.length
                            ? tabLabels[position] : "";
                    tv.setText(label);
                    tv.setSingleLine(true);
                    tv.setMaxLines(1);
                    tv.setIncludeFontPadding(false);
                    tv.setTextSize(13f);
                    tv.setGravity(android.view.Gravity.CENTER);
                    tv.setPadding(dp(8), 0, dp(8), 0);
                    tv.setTextColor(0x99FFFFFF);
                    tab.setCustomView(tv);
                });
        tabMediator.attach();

        // Default tab: first
        binding.vpGiftListContainer.setCurrentItem(0, false);
        giftTypeFilter = typeForTab(0);
        styleGiftTab(tabs.getTabAt(0), true);

        // Do NOT clearOnTabSelectedListeners — that kills TabLayoutMediator sync.
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                styleGiftTab(tab, true);
                giftTypeFilter = typeForTab(tab.getPosition());
                if (binding != null
                        && binding.vpGiftListContainer.getCurrentItem() != tab.getPosition()) {
                    binding.vpGiftListContainer.setCurrentItem(tab.getPosition(), true);
                }
                refreshCpBanner();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                styleGiftTab(tab, false);
            }
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        binding.vpGiftListContainer.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        giftTypeFilter = typeForTab(position);
                        styleGiftTab(tabs.getTabAt(position), true);
                        if (giftPageAdapter != null) {
                            giftPageAdapter.setSelectedId(
                                    selected != null ? selected.id : null);
                        }
                        refreshCpBanner();
                    }
                });
    }

    private void styleGiftTab(@Nullable TabLayout.Tab tab, boolean selected) {
        if (tab == null || !(tab.getCustomView() instanceof android.widget.TextView tv)) return;
        tv.setTextColor(selected ? 0xFFFFFFFF : 0x99FFFFFF);
        tv.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
    }

    private void applyCategories(@Nullable List<GiftDtos.GiftCategoryDto> cats) {
        if (cats == null || cats.isEmpty()) return;
        List<GiftDtos.GiftCategoryDto> active = new ArrayList<>();
        for (GiftDtos.GiftCategoryDto c : cats) {
            if (c == null) continue;
            String key = c.key != null ? c.key.trim().toLowerCase(Locale.US) : "";
            if (key.isEmpty()) continue;
            // Client/categories endpoint only returns active tabs.
            active.add(c);
        }
        if (active.isEmpty()) return;
        String[] keys = new String[active.size()];
        String[] labels = new String[active.size()];
        for (int i = 0; i < active.size(); i++) {
            GiftDtos.GiftCategoryDto c = active.get(i);
            keys[i] = c.key.trim().toLowerCase(Locale.US);
            String label = c.labelAr;
            if (label == null || label.trim().isEmpty()) label = c.labelEn;
            if (label == null || label.trim().isEmpty()) label = keys[i];
            labels[i] = label.trim();
        }
        boolean same = keys.length == tabKeys.length;
        if (same) {
            for (int i = 0; i < keys.length; i++) {
                if (!keys[i].equals(tabKeys[i]) || !labels[i].equals(tabLabels[i])) {
                    same = false;
                    break;
                }
            }
        }
        tabCategories.clear();
        tabCategories.addAll(active);
        tabKeys = keys;
        tabLabels = labels;
        if (!same && binding != null) {
            rebuildGiftTabs();
        }
        applyFilter();
    }

    private void rebuildGiftTabs() {
        if (binding == null || giftPageAdapter == null) return;
        int keep = binding.vpGiftListContainer.getCurrentItem();
        if (tabMediator != null) {
            try { tabMediator.detach(); } catch (Exception ignored) {}
            tabMediator = null;
        }
        TabLayout tabs = binding.miGiftCategory;
        tabs.removeAllTabs();
        List<List<GiftDtos.GiftDto>> empty = new ArrayList<>();
        for (int i = 0; i < tabLabels.length; i++) empty.add(new ArrayList<>());
        giftPageAdapter.submit(empty, selected != null ? selected.id : null);
        tabMediator = new TabLayoutMediator(tabs, binding.vpGiftListContainer,
                (tab, position) -> {
                    android.widget.TextView tv = new android.widget.TextView(requireContext());
                    String label = position >= 0 && position < tabLabels.length
                            ? tabLabels[position] : "";
                    tv.setText(label);
                    tv.setSingleLine(true);
                    tv.setMaxLines(1);
                    tv.setIncludeFontPadding(false);
                    tv.setTextSize(13f);
                    tv.setGravity(android.view.Gravity.CENTER);
                    tv.setPadding(dp(8), 0, dp(8), 0);
                    tv.setTextColor(0x99FFFFFF);
                    tab.setCustomView(tv);
                });
        tabMediator.attach();
        if (keep < 0 || keep >= tabLabels.length) keep = 0;
        binding.vpGiftListContainer.setCurrentItem(keep, false);
        giftTypeFilter = typeForTab(keep);
        styleGiftTab(tabs.getTabAt(keep), true);
    }

    @Nullable
    private String typeForTab(int pos) {
        if (pos >= 0 && pos < tabKeys.length) return tabKeys[pos];
        return "normal";
    }

    // ─── Filter: build one gift list per tab (no sub-page dots) ────────────────

    private void applyFilter() {
        if (binding == null || giftPageAdapter == null) return;
        List<List<GiftDtos.GiftDto>> byTab = new ArrayList<>();
        for (int i = 0; i < tabKeys.length; i++) {
            byTab.add(filterForTab(typeForTab(i)));
        }
        String selId = selected != null ? selected.id : null;
        giftPageAdapter.submit(byTab, selId);
        binding.vpGiftListContainer.setVisibility(View.VISIBLE);

        int cur = binding.vpGiftListContainer.getCurrentItem();
        if (cur < 0 || cur >= byTab.size()) cur = 0;
        List<GiftDtos.GiftDto> current = byTab.isEmpty()
                ? Collections.emptyList()
                : byTab.get(Math.min(cur, byTab.size() - 1));
        if (current.isEmpty() && !allGifts.isEmpty()) {
            // Soft empty — no toast spam on every swipe.
        }
        restoreSelected(current);
    }

    @NonNull
    private List<GiftDtos.GiftDto> filterForTab(@Nullable String filter) {
        String want = filter != null && !filter.isEmpty()
                ? filter.trim().toLowerCase(Locale.US)
                : "normal";
        List<GiftDtos.GiftDto> out = new ArrayList<>();
        for (GiftDtos.GiftDto g : allGifts) {
            if (g == null || isLuckyBoxGift(g)) continue;
            if (want.equals(giftBucket(g))) out.add(g);
        }
        return out;
    }

    /**
     * Prefer explicit gift.category (dashboard tabs); fall back to type-based bucket
     * for legacy rows that only set type.
     */
    @NonNull
    private static String giftBucket(@NonNull GiftDtos.GiftDto g) {
        String c = g.category != null ? g.category.trim().toLowerCase(Locale.US) : "";
        if (!c.isEmpty()) return c;
        String t = g.type != null ? g.type.trim().toLowerCase(Locale.US) : "";
        if ("lucky".equals(t)) return "lucky";
        if ("combo".equals(t)) return "combo";
        if ("premium".equals(t)) return "premium";
        return "normal";
    }

    /** صندوق الحظ العائم في الغرفة — ليس من تبويب المحظوظ. */
    private static boolean isLuckyBoxGift(@Nullable GiftDtos.GiftDto g) {
        if (g == null) return false;
        String icon = g.iconUrl != null ? g.iconUrl.toLowerCase(Locale.US) : "";
        String name = g.name != null ? g.name.toLowerCase(Locale.US) : "";
        return icon.contains("lucky-box") || name.contains("صندوق");
    }

    // ─── Recipients (GiftUserAvatarView) ──────────────────────────────────────

    private void setupRecipientStrip(boolean isChat) {
        if (binding == null) return;

        if (isChat || !(getActivity() instanceof GiftRecipientSource source)) {
            binding.guavGiftUserAvatar.setVisibility(View.GONE);
            return;
        }

        List<GiftRecipient> all = source.getGiftRecipients();
        micRecipients.clear();
        if (all != null) micRecipients.addAll(all);

        if (micRecipients.isEmpty()) {
            if (receiverId == null || receiverId.isEmpty()) {
                receiverId = source.getDefaultGiftReceiverId();
            }
            binding.guavGiftUserAvatar.setVisibility(View.GONE);
            refreshSendBtn();
            return;
        }

        binding.guavGiftUserAvatar.setVisibility(View.VISIBLE);
        binding.guavGiftUserAvatar.setAgencyRoom(source.isAgencyGiftRoom());
        binding.guavGiftUserAvatar.setListener(new GiftUserAvatarView.Listener() {
            @Override
            public void onRecipientSelected(@NonNull GiftRecipient recipient) {
                selectRecipient(recipient);
            }

            @Override
            public void onSelectAllMic() {
                sendToAllMic = true;
                if (binding != null) {
                    binding.guavGiftUserAvatar.setAvatarAllMic(true);
                }
                refreshSendBtn();
                updateDockText();
                publishSeatGiftSelection();
            }
        });

        if (receiverId == null || receiverId.isEmpty()) {
            for (GiftRecipient r : micRecipients) {
                if (r != null && r.host && r.userId != null) {
                    receiverId = r.userId;
                    break;
                }
            }
            if (receiverId == null || receiverId.isEmpty()) {
                receiverId = source.getDefaultGiftReceiverId();
            }
        }

        if (sendToAllMic) {
            binding.guavGiftUserAvatar.submit(micRecipients, receiverId, true);
            refreshSendBtn();
            updateDockText();
            publishSeatGiftSelection();
            return;
        }

        GiftRecipient initial = findRecipient(receiverId);
        if (initial != null) {
            selectedRecipient = initial;
            receiverId = initial.userId;
        } else if (!micRecipients.isEmpty()) {
            selectedRecipient = micRecipients.get(0);
            receiverId = selectedRecipient.userId;
        }
        binding.guavGiftUserAvatar.submit(micRecipients, receiverId, false);
        refreshSendBtn();
        updateDockText();
        publishSeatGiftSelection();
    }

    private void selectRecipient(@NonNull GiftRecipient r) {
        sendToAllMic = false;
        selectedRecipient = r;
        receiverId = r.userId;
        if (binding != null) {
            binding.guavGiftUserAvatar.setPersonalAvatar(true);
            binding.guavGiftUserAvatar.submit(micRecipients, receiverId, false);
        }
        refreshSendBtn();
        updateDockText();
        publishSeatGiftSelection();
    }

    /** Paint teal underline on room seats matching current gift targets. */
    private void publishSeatGiftSelection() {
        if (!(getActivity() instanceof VoiceRoomActivity room)) return;
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (sendToAllMic) {
            for (GiftRecipient r : micRecipients) {
                if (r != null && r.userId != null && !r.userId.isEmpty()) ids.add(r.userId);
            }
        } else if (receiverId != null && !receiverId.isEmpty()) {
            ids.add(receiverId);
        }
        room.setGiftSeatSelection(ids);
    }

    private void clearSeatGiftSelection() {
        if (getActivity() instanceof VoiceRoomActivity room) {
            room.clearGiftSeatSelection();
        }
    }

    @Nullable
    private GiftRecipient findRecipient(@Nullable String userId) {
        if (userId == null) return null;
        for (GiftRecipient r : micRecipients) {
            if (userId.equals(r.userId)) return r;
        }
        return null;
    }

    private int uniqueMicCount() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (GiftRecipient r : micRecipients) {
            if (r != null && r.userId != null && !r.userId.isEmpty()) ids.add(r.userId);
        }
        return ids.size();
    }

    // ─── Qty popup ────────────────────────────────────────────────────────────

    private void showQtyPopup() {
        if (binding == null || !isAdded()) return;
        dismissQtyPopup();
        View anchor = binding.lineSend != null ? binding.lineSend : binding.bltvSendGift;
        if (anchor == null) return;

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_gift_qty, null, false);
        int[] values = new int[]{1, 7, 17, 77, 177};
        View[] chips = {
                content.findViewById(R.id.popupQty1),
                content.findViewById(R.id.popupQty7),
                content.findViewById(R.id.popupQty17),
                content.findViewById(R.id.popupQty27),
                content.findViewById(R.id.popupQty77)
        };
        boolean known = false;
        for (int v : values) if (giftQty == v) { known = true; break; }
        if (!known) giftQty = 1;

        for (int i = 0; i < chips.length; i++) {
            if (!(chips[i] instanceof android.widget.TextView tv)) continue;
            final int qty = values[i];
            boolean on = giftQty == qty;
            long unit = selected != null ? Math.max(0, selected.coinPrice) : 0L;
            int targets = Math.max(1, previewTargetCount());
            long cost = unit * qty * targets;
            if (unit > 0) {
                tv.setText("×" + qty + " · " + fmt(cost));
            } else {
                tv.setText("×" + qty);
            }
            tv.setBackgroundColor(on ? 0x3355F2BC : 0x00000000);
            tv.setTextColor(ContextCompat.getColor(requireContext(),
                    on ? R.color.gift_select_stroke : R.color.mikoo_gift_text));
            tv.setOnClickListener(v -> {
                giftQty = qty;
                updateDockText();
                dismissQtyPopup();
            });
        }

        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        PopupWindow popup = new PopupWindow(
                content,
                Math.max(Math.max(anchor.getWidth(), content.getMeasuredWidth()), dp(148)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);
        popup.setElevation(14f);
        popup.setOverlapAnchor(false);
        qtyPopup = popup;
        int yOff = -(anchor.getHeight() + content.getMeasuredHeight() + dp(4));
        popup.showAsDropDown(anchor, 0, yOff);
    }

    private void dismissQtyPopup() {
        if (qtyPopup != null) {
            try { qtyPopup.dismiss(); } catch (Exception ignored) {}
            qtyPopup = null;
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    private boolean isLuckyMode() {
        if ("lucky".equalsIgnoreCase(giftTypeFilter)) return true;
        return selected != null && selected.type != null
                && "lucky".equalsIgnoreCase(selected.type.trim());
    }

    // ─── Dock ─────────────────────────────────────────────────────────────────

    private void updateDockText() {
        if (binding == null) return;
        int qty = Math.max(1, effectiveQty());
        if (binding.bltvSendGiftCount != null) {
            binding.bltvSendGiftCount.setText("×" + qty);
        }
        if (binding.bltvSendGift != null && !sending) {
            binding.bltvSendGift.setText(R.string.send);
        }
        refreshCostPreview();
    }

    private int previewTargetCount() {
        if (sendToAllMic) {
            int n = uniqueMicCount();
            return Math.max(1, n);
        }
        return 1;
    }

    private void refreshCostPreview() {
        if (binding == null || binding.tvGiftCostPreview == null) return;
        android.widget.TextView tv = binding.tvGiftCostPreview;
        if (selected == null || selected.coinPrice <= 0) {
            tv.setText("اختر هدية لعرض التكلفة");
            tv.setTextColor(0x99FFFFFF);
            return;
        }
        int qty = Math.max(1, effectiveQty());
        int targets = Math.max(1, previewTargetCount());
        long unit = selected.coinPrice;
        long need = unit * qty * targets;
        Long bal = lastCoinsBalance;
        StringBuilder sb = new StringBuilder();
        if (targets > 1) {
            sb.append(fmt(unit)).append(" × ").append(qty)
                    .append(" × ").append(targets)
                    .append(" = ").append(fmt(need));
        } else {
            sb.append(fmt(unit)).append(" × ").append(qty)
                    .append(" = ").append(fmt(need));
        }
        if (bal != null) {
            long left = bal - need;
            if (left >= 0) {
                sb.append(" · يتبقى ").append(fmt(left));
                tv.setTextColor(0xFFFFD54F);
            } else {
                sb.append(" · ناقص ").append(fmt(-left));
                tv.setTextColor(0xFFFF6B6B);
            }
        } else {
            tv.setTextColor(0xFFFFD54F);
        }
        tv.setText(sb.toString());
    }

    private void refreshSendBtn() {
        if (binding == null) return;
        boolean ok = selected != null && (sendToAllMic
                ? !micRecipients.isEmpty()
                : receiverId != null && !receiverId.isEmpty());
        boolean enabled = ok && !sending;
        binding.bltvSendGift.setEnabled(enabled);
        binding.bltvSendGift.setAlpha(enabled ? 1f : 0.45f);
        refreshCostPreview();
    }

    // ─── Selected gift ────────────────────────────────────────────────────────

    private void applySelectedGift(@NonNull GiftDtos.GiftDto gift) {
        selected = gift;
        pendingGiftId = gift.id;
        if (giftPageAdapter != null) giftPageAdapter.setSelectedId(gift.id);
        refreshSendBtn();
        updateDockText();
    }

    private void restoreSelected(List<GiftDtos.GiftDto> catalog) {
        if (catalog == null || catalog.isEmpty()) return;
        String want = selected != null && selected.id != null
                ? selected.id : pendingGiftId;
        if (want == null) return;
        for (GiftDtos.GiftDto g : catalog) {
            if (g != null && want.equals(g.id)) { applySelectedGift(g); return; }
        }
    }

    // ─── Send ─────────────────────────────────────────────────────────────────

    private void sendGift(@Nullable String roomId) {
        if (sending) return;
        if (selected == null) {
            Toast.makeText(requireContext(), "اختر هدية أولاً",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!chatMode && (receiverId == null || receiverId.isEmpty())
                && getActivity() instanceof GiftRecipientSource src) {
            receiverId = src.getDefaultGiftReceiverId();
        }

        List<String> targets = new ArrayList<>();
        if (sendToAllMic) {
            if (!chatMode) setupRecipientStrip(false);
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (GiftRecipient r : micRecipients) {
                if (r != null && r.userId != null && !r.userId.isEmpty())
                    unique.add(r.userId);
            }
            targets.addAll(unique);
        } else if (receiverId != null && !receiverId.isEmpty()) {
            targets.add(receiverId);
        } else if (!chatMode && getActivity() instanceof GiftRecipientSource src) {
            String fb = src.getDefaultGiftReceiverId();
            if (fb != null && !fb.isEmpty()) { receiverId = fb; targets.add(fb); }
        }

        // Never allow self-support (room owner/host gifting themselves).
        String myId = null;
        try {
            myId = ContainerProvider.from(requireActivity()).getSessionManager().getUserId();
        } catch (Exception ignored) {
        }
        if (myId != null && !myId.isEmpty()) {
            final String me = myId;
            boolean removedSelf = targets.removeIf(id -> me.equals(id));
            if (removedSelf && targets.isEmpty()) {
                Toast.makeText(requireContext(),
                        "لا يمكنك دعم نفسك — اختر مستلم آخر",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (targets.isEmpty()) {
            Toast.makeText(requireContext(),
                    R.string.choose_recipient_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (!chatMode && (roomId == null || roomId.isEmpty())) {
            Toast.makeText(requireContext(),
                    "الغرفة غير جاهزة، أعد المحاولة", Toast.LENGTH_SHORT).show();
            return;
        }

        int qty = effectiveQty();
        long need = (long) selected.coinPrice * qty * targets.size();
        Long coins = vm.getCoinsBalance().getValue();
        if (coins != null && selected.coinPrice > 0 && coins < need) {
            Toast.makeText(requireContext(),
                    "رصيدك غير كافٍ · المطلوب " + fmt(need),
                    Toast.LENGTH_SHORT).show();
            openRechargeOnly();
            return;
        }

        sending = true;
        binding.bltvSendGift.setEnabled(false);
        binding.bltvSendGift.setText("جارٍ الإرسال ×" + qty
                + (targets.size() > 1 ? " · " + targets.size() : "") + "...");

        lastTargets.clear();
        lastTargets.addAll(targets);
        vm.sendMany(selected.id, targets, qty, chatMode ? null : roomId);
    }

    // ─── Gift sent ────────────────────────────────────────────────────────────

    private void onGiftSent(@Nullable GiftDtos.SendGiftResult result,
                            @Nullable String roomId) {
        sending = false;
        if (result == null) return;
        // Consume sticky result first so re-observe never double-fires this payload.
        try {
            vm.clearSent();
        } catch (Exception ignored) {
        }
        if (binding == null) return;
        refreshSendBtn();
        updateDockText();

        GiftDtos.GiftDto gift = selected;
        if (result.gift != null) {
            // Prefer server row (real uploaded MP4) over stale local catalog HTML placeholders.
            gift = result.gift;
        }
        final String name = gift != null && gift.name != null && !gift.name.isEmpty()
                ? gift.name
                : (selected != null ? selected.name : "هدية");
        String iconTmp = gift != null ? gift.iconUrl : null;
        if ((iconTmp == null || iconTmp.isEmpty()) && selected != null) iconTmp = selected.iconUrl;
        // Bubble / toast: never feed video/SVGA into Glide (OOM kill on mid-range devices).
        String safeIcon = stillGiftIconUrl(iconTmp);
        String animTmp = gift != null ? gift.animationUrl : null;
        if ((animTmp == null || animTmp.isEmpty()
                || animTmp.toLowerCase(java.util.Locale.US).contains("runtime.html"))
                && selected != null
                && selected.animationUrl != null
                && !selected.animationUrl.toLowerCase(java.util.Locale.US).contains("runtime.html")) {
            animTmp = selected.animationUrl;
        }
        // Last resort: map known names (أسد…) to CDN entry MP4s.
        String mapped = com.Dramizo.Series.util.GiftMediaResolver.resolvePlayable(name, iconTmp, animTmp);
        if (mapped != null) animTmp = mapped;
        final String icon = safeIcon != null ? safeIcon : iconTmp;
        final String anim = animTmp;
        int combo = result.comboCount > 0 ? result.comboCount : vm.getCombo();
        int qty = effectiveQty();
        int ppl = Math.max(1, lastTargets.size());

        if (result.wallet != null) {
            bindCoins(result.wallet.coins);
            vm.setCoinsBalance(result.wallet.coins);
        } else if (result.senderBalance > 0 || result.coinsSpent > 0) {
            bindCoins(result.senderBalance);
            vm.setCoinsBalance(result.senderBalance);
        }

        boolean luckyGift = isLuckyMode()
                || (gift != null && gift.type != null
                && "lucky".equalsIgnoreCase(gift.type.trim()))
                || (gift != null && gift.category != null
                && "lucky".equalsIgnoreCase(gift.category.trim()));

        // Private chat: post bubble only — no room rains, no full-screen celebration overlays.
        if (chatMode) {
            finishChatGiftSend(name, icon, luckyGift, result);
            return;
        }

        long coinValue = gift != null
                ? (long) gift.coinPrice * Math.max(1, qty) : 0L;
        long spentTotal = result.coinsSpent > 0
                ? result.coinsSpent
                : (result.totalCoins > 0 ? result.totalCoins : coinValue * Math.max(1, ppl));
        android.app.Activity hostAct = getActivity();
        if (hostAct instanceof VoiceRoomActivity room) {
            SessionManager session = ContainerProvider.from(hostAct).getSessionManager();
            List<String> tgts = !lastTargets.isEmpty()
                    ? new ArrayList<>(lastTargets)
                    : (receiverId != null
                    ? java.util.Collections.singletonList(receiverId)
                    : java.util.Collections.emptyList());
            boolean allMic = sendToAllMic && tgts.size() > 1;
            if (luckyGift) {
                // Lightweight send feedback only — heavy rain is for مردود win path (once).
                try {
                    GiftAudioFx.playLuckyCoins(requireContext(), 1);
                    List<String> rainIds = !tgts.isEmpty()
                            ? tgts
                            : room.collectOccupiedMicUserIdsPublic();
                    if (rainIds.size() > 6) {
                        rainIds = new ArrayList<>(rainIds.subList(0, 6));
                    }
                    room.playLuckyGiftStage(
                            icon,
                            rainIds,
                            Math.max(1L, spentTotal),
                            Math.max(1, Math.min(qty, 5)),
                            Math.max(1, rainIds.size()),
                            null);
                    for (String tid : tgts) {
                        if (tid != null && !tid.isEmpty() && coinValue > 0) {
                            room.creditGiftCoinsOnSeat(tid, coinValue);
                        }
                    }
                    room.announceLuckyGiftChat(
                            name, icon, session.getDisplayName(), 1,
                            session.getUserId(), Math.max(0, session.getVipLevel()),
                            session.getAvatarUrl(), Math.max(1, session.getUserLevel()),
                            session.getHostBadgeUrl());
                } catch (OutOfMemoryError | Exception e) {
                    try {
                        room.announceLuckyGiftChat(
                                name, icon, session.getDisplayName(), 1,
                                session.getUserId(), Math.max(0, session.getVipLevel()),
                                session.getAvatarUrl(), Math.max(1, session.getUserLevel()),
                                session.getHostBadgeUrl());
                    } catch (Exception ignored) {
                    }
                }
            } else if (allMic) {
                room.playLuckyGiftToAllMics(name, icon, anim,
                        session.getDisplayName(), combo,
                        session.getUserId(), Math.max(0, session.getVipLevel()),
                        session.getAvatarUrl(), tgts, coinValue);
            } else {
                String playTo = null;
                for (String tid : tgts) {
                    if (tid == null || tid.isEmpty()) continue;
                    if (playTo == null) {
                        playTo = tid;
                    } else if (coinValue > 0) {
                        room.creditGiftCoinsOnSeat(tid, coinValue);
                    }
                }
                if (playTo != null) {
                    room.playGiftToRecipient(name, icon, anim,
                            session.getDisplayName(), combo,
                            session.getUserId(), Math.max(0, session.getVipLevel()),
                            session.getAvatarUrl(), session.getHostBadgeUrl(),
                            Math.max(1, session.getUserLevel()),
                            playTo, coinValue, true);
                }
            }
        }

        if (luckyGift) {
            long wonFromResult = result.luckyCoinsWon > 0
                    ? result.luckyCoinsWon
                    : (result.breakdown != null ? result.breakdown.luckyReturn : 0L);
            Double mulBox = result.luckyMultiplier;
            if ((mulBox == null || mulBox <= 0)
                    && result.breakdown != null
                    && result.breakdown.luckyMultiplier > 0) {
                mulBox = result.breakdown.luckyMultiplier;
            }
            double mulRaw = mulBox != null ? mulBox : 0;
            int mul = mulRaw >= 1.0 ? Math.max(1, (int) Math.round(mulRaw)) : 0;
            boolean softReturn = mulRaw > 0 && mulRaw < 1.0;
            int unit = result.coinPrice > 0
                    ? result.coinPrice
                    : (gift != null ? gift.coinPrice : 0);
            int q = result.quantity > 0 ? result.quantity : qty;
            long spent = result.breakdown != null && result.breakdown.coinsSpent > 0
                    ? result.breakdown.coinsSpent
                    : Math.max(result.coinsSpent, result.totalCoins);
            if (spent <= 0) spent = (long) unit * Math.max(1, q) * Math.max(1, ppl);
            long base = unit > 0 ? (long) unit * Math.max(1, q) : Math.max(1L, spent);
            if (!softReturn && mul <= 1 && base > 0 && wonFromResult > 0 && mulRaw >= 1.0) {
                mul = Math.max(1, (int) Math.round((double) wonFromResult / (double) base));
            }
            // Soft roll must never look like "no return" due to floor→0.
            long wonCoinsResolved = wonFromResult;
            if (softReturn && wonCoinsResolved <= 0 && spent > 0) {
                wonCoinsResolved = Math.max(1L, (long) Math.floor(spent * mulRaw));
            }
            final long wonCoins = wonCoinsResolved;
            final int mulFinal = mul;
            final boolean softFinal = softReturn;
            final boolean didWin = wonCoins > 0;
            final Context sfxCtx = hostAct != null
                    ? hostAct.getApplicationContext()
                    : (getContext() != null ? getContext().getApplicationContext() : null);

            dismissAllowingStateLoss();
            if (hostAct instanceof VoiceRoomActivity room) {
                SessionManager session = ContainerProvider.from(hostAct).getSessionManager();
                Runnable showSmallToast = () -> {
                    if (hostAct == null || hostAct.isFinishing() || !didWin) return;
                    String msg = softFinal
                            ? ("مردود +" + wonCoins)
                            : ("ضرب حظه ×" + Math.max(1, mulFinal) + " · +" + wonCoins);
                    room.showLuckyResultToast(
                            session.getDisplayName(),
                            session.getAvatarUrl(),
                            Math.max(1, session.getUserLevel()),
                            Math.max(0, session.getVipLevel()),
                            msg);
                };
                if (didWin) {
                    String who = session.getDisplayName();
                    if (who == null || who.isEmpty()) who = "مستخدم";
                    String chat = softFinal
                            ? ("ضرب حظه · مردود +" + wonCoins)
                            : ("ضرب حظه وربح ×" + Math.max(1, mulFinal) + " · +" + wonCoins);
                    try {
                        room.announceLuckyWinChat(who, chat, session.getAvatarUrl());
                        String meId = session.getUserId();
                        if (meId != null && !meId.isEmpty()) {
                            room.showLuckyReturnOnMics(
                                    java.util.Collections.singletonList(meId), wonCoins);
                            // One light rain only — not a second full particle storm.
                            room.playCoinRainToUsers(
                                    java.util.Collections.singletonList(meId), 8);
                        }
                        if (sfxCtx != null) {
                            GiftAudioFx.playLuckyCoins(sfxCtx, 1);
                        }
                    } catch (OutOfMemoryError | Exception ignored) {
                    }
                    hostAct.getWindow().getDecorView().postDelayed(showSmallToast, 200L);
                }
                // Empty roll: silent.
            } else if (didWin) {
                if (sfxCtx != null) {
                    GiftAudioFx.playLuckyCoins(sfxCtx, softFinal ? 3 : 4);
                }
                if (hostAct != null && !hostAct.isFinishing()) {
                    String body = softFinal
                            ? ("مردود +" + wonCoins)
                            : ("ضرب حظه ×" + Math.max(1, mulFinal) + " · +" + wonCoins);
                    hostAct.getWindow().getDecorView().post(() ->
                            com.Dramizo.Series.util.GlobalCelebrationToast.show(
                                    hostAct,
                                    softFinal ? "مردود جزئي" : "حظ سعيد!",
                                    body,
                                    null,
                                    icon,
                                    "lucky-local:" + System.currentTimeMillis()));
                }
            }
            // Empty roll outside room: silent — never show lose dialog.
            return;
        }

        // Normal gift: close sheet; Mikoo center toast is shown by VoiceRoomActivity.
        dismissAllowingStateLoss();
    }

    /**
     * DM gift: only bubble in conversation. Room rains / celebration toast OOM some handsets
     * (Hot 30) when misused after private send; sticky LiveData also re-fired this path.
     */
    private void finishChatGiftSend(
            @Nullable String name,
            @Nullable String icon,
            boolean luckyGift,
            @NonNull GiftDtos.SendGiftResult result) {
        try {
            Bundle out = new Bundle();
            out.putString("name", name != null && !name.isEmpty() ? name : "هدية");
            if (icon != null && !icon.isEmpty()) {
                out.putString("icon", icon);
            }
            long won = result.luckyCoinsWon > 0
                    ? result.luckyCoinsWon
                    : (result.breakdown != null ? result.breakdown.luckyReturn : 0L);
            if (luckyGift && won > 0) {
                android.content.Context ctx = getContext();
                if (ctx != null) {
                    Toast.makeText(ctx, "مردود +" + won, Toast.LENGTH_SHORT).show();
                }
            }
            if (isAdded()) {
                getParentFragmentManager().setFragmentResult("gift_sent_chat", out);
            }
        } catch (Exception ignored) {
        }
        try {
            dismissAllowingStateLoss();
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static String stillGiftIconUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String u = url.trim();
        com.Dramizo.Series.util.CosmeticMedia.Kind kind =
                com.Dramizo.Series.util.CosmeticMedia.kind(u);
        if (kind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO
                || kind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA) {
            return null;
        }
        return u;
    }

    // ─── Wealth / coins ───────────────────────────────────────────────────────

    private void bindWealthLevel() {
        if (binding == null) return;
        try {
            SessionManager session =
                    ContainerProvider.from(requireActivity()).getSessionManager();
            AuthDtos.UserDto user = session.getUser();
            int level = 1;
            long sent = 0L;
            String avatar = session.getAvatarUrl();
            if (user != null) {
                sent = Math.max(0L, user.totalSentCoins);
                if (user.wealthLevel > 0) {
                    level = user.wealthLevel;
                } else if (user.wealthScore > 0) {
                    level = Math.max(1, (int) Math.round(
                            Math.cbrt(Math.max(1, user.wealthScore) / 10.0)));
                } else if (sent > 0) {
                    level = Math.max(1, (int) Math.round(Math.cbrt(sent / 10.0)));
                }
                if (user.avatarUrl != null && !user.avatarUrl.isEmpty()) {
                    avatar = user.avatarUrl;
                }
            }
            level = Math.max(1, level);
            long cur = scoreForLevel(level);
            long next = scoreForLevel(level + 1);
            long done = Math.max(0L, Math.min(sent - cur, next - cur));
            long span = Math.max(1L, next - cur);

            if (wealthProgress != null) {
                wealthProgress.setMax(100);
                wealthProgress.setProgress((int) Math.min(100, done * 100 / span));
            }
            if (wealthLevelBadge != null) {
                wealthLevelBadge.setText(String.format(Locale.US, "LV.%d", level));
            }
            if (wealthLevelDesc != null) {
                wealthLevelDesc.setText(String.format(Locale.US,
                        "التقدم · %s / %s", fmt(done), fmt(span)));
            }
            if (wealthLevelAvatar != null) {
                String frame = null;
                if (user != null) {
                    if (user.vipBadgeUrl != null && !user.vipBadgeUrl.isEmpty()) {
                        frame = user.vipBadgeUrl;
                    } else if (user.hostBadgeUrl != null && !user.hostBadgeUrl.isEmpty()) {
                        frame = user.hostBadgeUrl;
                    }
                }
                if (frame == null || frame.isEmpty()) {
                    try {
                        String host = session.getHostBadgeUrl();
                        if (host != null && !host.isEmpty()) frame = host;
                    } catch (Exception ignoredFrame) {}
                }
                // Fixed sizes in room_gift_level_layout — bind avatar + frame without stack resize.
                AvatarCosmetics.bindStacked(wealthLevelAvatar, wealthLevelFrame, avatar, frame);
            }
        } catch (Exception ignored) {}
    }

    /** Inverse of backend levelFromScore: score ≈ 10 · level³. */
    private static long scoreForLevel(int level) {
        long l = Math.max(1, level);
        return 10L * l * l * l;
    }

    private int effectiveQty() {
        return Math.max(1, Math.min(177, giftQty));
    }

    private String fmt(long n) {
        return NumberFormat.getNumberInstance(Locale.US).format(n);
    }

    private void bindCoins(@Nullable Long coins) {
        lastCoinsBalance = coins;
        if (binding == null || binding.tvGiftUserGold == null) return;
        binding.tvGiftUserGold.setText(coins == null ? "—" : fmt(coins));
        refreshCostPreview();
    }

    private void openRechargeOnly() {
        // Open recharge over the room — never navigate away / finish VoiceRoom.
        Activity host = getActivity();
        if (host == null || host.isFinishing()) return;
        try {
            com.Dramizo.Series.util.BalanceRedirect.openRecharge(host);
        } catch (Exception ignored) {
        }
        // Dismiss gift sheet after recharge is queued so the room stays underneath.
        try {
            dismissAllowingStateLoss();
        } catch (Exception ignored) {
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void refreshRecipients() {
        if (binding == null || chatMode) return;
        setupRecipientStrip(false);
        refreshSendBtn();
        updateDockText();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onResume() {
        super.onResume();
        refreshRecipients();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        String id = selected != null && selected.id != null
                ? selected.id : pendingGiftId;
        if (id != null) out.putString(STATE_GIFT, id);
        out.putInt(STATE_QTY, giftQty);
        out.putBoolean(STATE_ALL_MIC, sendToAllMic);
        if (receiverId != null) out.putString(ARG_RECEIVER, receiverId);
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        clearSeatGiftSelection();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        dismissQtyPopup();
        clearSeatGiftSelection();
        if (tabMediator != null) {
            try { tabMediator.detach(); } catch (Exception ignored) {}
            tabMediator = null;
        }
        binding = null;
        giftPageAdapter = null;
        loadingBar = null;
        wealthProgress = null;
        wealthLevelDesc = null;
        wealthLevelBadge = null;
        wealthLevelAvatar = null;
        wealthLevelFrame = null;
        noCpLayout = null;
        hascpLayout = null;
        super.onDestroyView();
    }
}
