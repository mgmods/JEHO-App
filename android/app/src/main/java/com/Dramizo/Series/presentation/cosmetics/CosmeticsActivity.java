package com.Dramizo.Series.presentation.cosmetics;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.BalanceRedirect;
import com.Dramizo.Series.util.CosmeticMedia;
import com.Dramizo.Series.util.HostSignalView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Android appearance store. The catalogue, ownership, purchase and equipment
 * continue to use the existing repository/API; no WebView or hosted HTML is required.
 */
public class CosmeticsActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";
    static final String[] TYPES = {
            "vip_badge", "entry_effect", "level_badge", "room_card",
            "room_background", "profile_card", "chat_bubble", "nameplate"
    };
    private static final String[] TITLES = {
            "الإطارات", "تأثيرات الدخول", "الشارات", "بطاقات الغرف",
            "خلفيات الغرف", "إطارات الملف", "فقاعات الدردشة", "بطاقات الأسماء"
    };

    private CosmeticsViewModel vm;
    private SessionManager session;
    private TabLayout tabs;
    private androidx.viewpager2.widget.ViewPager2 pager;
    private TextView title, bagButton, rechargeButton, balanceView, heroName, heroHint, heroDays;
    private HostSignalView heroFrame;
    private ImageView heroMedia;
    private FrameLayout heroMediaHolder;
    private VideoView heroVideo;
    private String roomId;
    private boolean bagMode;
    private String initialType = "vip_badge";
    private TabLayoutMediator mediator;
    private final List<CosmeticsCategoryFragment> pages = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cosmetics);
        session = ContainerProvider.from(this).getSessionManager();
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        if (roomId == null || roomId.isEmpty()) roomId = getIntent().getStringExtra("roomId");
        bagMode = getIntent().getBooleanExtra("bag_mode", false);

        String type = getIntent().getStringExtra(CosmeticsViewModel.EXTRA_TYPE);
        if (type == null || type.isEmpty()) type = getIntent().getStringExtra("type");
        String section = getIntent().getStringExtra("store_section");
        // These legacy store entry points already have native flows backed by their
        // dedicated APIs. Preserve them instead of silently mapping them to cosmetics.
        if ("id".equals(section)) {
            startActivity(new android.content.Intent(this,
                    com.Dramizo.Series.presentation.profile.VanityIdsActivity.class));
            finish();
            return;
        }
        if ("level".equals(section)) {
            startActivity(new android.content.Intent(this,
                    com.Dramizo.Series.presentation.profile.UserLevelActivity.class));
            finish();
            return;
        }
        if ("badges".equals(section)) {
            startActivity(new android.content.Intent(this,
                    com.Dramizo.Series.presentation.profile.BadgeActivity.class));
            finish();
            return;
        }
        if ("vip".equals(section)) {
            startActivity(new android.content.Intent(this,
                    com.Dramizo.Series.presentation.vip.VipActivity.class));
            finish();
            return;
        }
        initialType = normalizeType(type);

        title = findViewById(R.id.tvTitle);
        bagButton = findViewById(R.id.btnBag);
        rechargeButton = findViewById(R.id.btnRecharge);
        balanceView = findViewById(R.id.tvStoreBalance);
        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);
        heroName = findViewById(R.id.tvHeroName);
        heroHint = findViewById(R.id.tvHeroHint);
        heroDays = findViewById(R.id.tvHeroDays);
        heroFrame = findViewById(R.id.nativeHeroFrame);
        heroMedia = findViewById(R.id.heroMedia);
        heroMediaHolder = (FrameLayout) heroMedia.getParent();

        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        bagButton.setOnClickListener(v -> {
            if (!bagMode) {
                android.content.Intent intent = getIntent();
                intent.putExtra("bag_mode", true);
                recreate();
            } else {
                getIntent().putExtra("bag_mode", false);
                recreate();
            }
        });
        bagButton.setText(bagMode ? "المتجر" : "حقيبتي");
        rechargeButton.setOnClickListener(v -> BalanceRedirect.openRecharge(this));
        balanceView.setOnClickListener(v -> BalanceRedirect.openRecharge(this));
        findViewById(R.id.tvStoreBalanceLabel).setOnClickListener(v -> BalanceRedirect.openRecharge(this));
        title.setText(bagMode ? "حقيبتي" : "متجر JEHO");

        vm = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this))).get(CosmeticsViewModel.class);
        vm.setBagMode(bagMode);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @Override public int getItemCount() { return TYPES.length; }
            @Override public Fragment createFragment(int position) {
                CosmeticsCategoryFragment f = CosmeticsCategoryFragment.newInstance(
                        TYPES[position], bagMode ? "العناصر التي تملكها في هذا القسم" : "");
                pages.add(f);
                return f;
            }
        });
        mediator = new TabLayoutMediator(tabs, pager,
                (tab, position) -> tab.setText(TITLES[position]));
        mediator.attach();
        int start = indexForType(initialType);
        pager.setCurrentItem(start, false);
        vm.setFilterType(initialType);

        vm.getSelectedItem().observe(this, this::showPreview);
        vm.getMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                rechargeButton.postInvalidate();
            }
        });
        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.trim().isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                if (msg.contains("رصيد") || msg.toLowerCase(java.util.Locale.ROOT).contains("balance")) {
                    BalanceRedirect.handleForced(this, msg);
                }
            }
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() >= 0 && tab.getPosition() < TYPES.length) {
                    vm.setFilterType(TYPES[tab.getPosition()]);
                    vm.clearSelection();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        pager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                if (position >= 0 && position < TYPES.length) vm.setFilterType(TYPES[position]);
            }
        });
    }

    private void refreshWalletBalance() {
        if (balanceView == null) return;
        balanceView.setText("…");
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> result =
                    ContainerProvider.from(this).getWalletRepository().getWallet();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || balanceView == null) return;
                if (result != null && result.success && result.data != null) {
                    balanceView.setText(String.format(java.util.Locale.US, "%,d", result.data.coins));
                } else {
                    balanceView.setText("عرض الرصيد");
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshWalletBalance();
    }

    private void showPreview(@Nullable CosmeticDtos.CosmeticDto item) {
        if (item == null) {
            heroName.setText("اختر قطعة لمعاينتها");
            heroHint.setText("معاينة قبل الشراء أو الارتداء");
            heroDays.setVisibility(View.GONE);
            stopVideo();
            heroMedia.setVisibility(View.GONE);
            heroFrame.clearSignal();
            return;
        }
        heroName.setText(item.name == null ? "عنصر JEHO" : item.name);
        heroHint.setText(item.coinPrice > 0 ? item.coinPrice + " عملة • صلاحية " +
                vm.leaseDays(item) + " يوم" : "عنصر مميز في متجر JEHO • صلاحية " + vm.leaseDays(item) + " يوم");
        Integer days = vm.daysLeft(item.id);
        if (days != null && days > 0) {
            heroDays.setText("متبقي " + days + " يوم");
            heroDays.setVisibility(View.VISIBLE);
        } else {
            heroDays.setVisibility(View.GONE);
        }

        String media = item.animationUrl;
        if (media == null || media.trim().isEmpty()) media = item.previewUrl;
        String playable = CosmeticMedia.playableUrl(media);
        CosmeticMedia.Kind kind = CosmeticMedia.kind(playable);
        stopVideo();
        if ("vip_badge".equals(item.type) || "host_badge".equals(item.type)) {
            heroMedia.setVisibility(View.GONE);
            heroFrame.setVisibility(View.VISIBLE);
            String avatar = session == null ? null : session.getAvatarUrl();
            heroFrame.bindStaticPreview(playable, avatar, null, 1);
        } else if (kind == CosmeticMedia.Kind.VIDEO && playable != null) {
            heroFrame.clearSignal();
            heroFrame.setVisibility(View.GONE);
            heroMedia.setVisibility(View.GONE);
            heroVideo = new VideoView(this);
            heroVideo.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            heroMediaHolder.addView(heroVideo, lp);
            String url = AssetCatalog.absoluteUrl(playable);
            heroVideo.setVideoURI(Uri.parse(url == null ? playable : url));
            heroVideo.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                mp.start();
            });
            heroVideo.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "تعذرت معاينة الفيديو لهذا العنصر", Toast.LENGTH_SHORT).show();
                return true;
            });
        } else {
            heroFrame.clearSignal();
            heroFrame.setVisibility(View.GONE);
            heroMedia.setVisibility(View.VISIBLE);
            String url = AssetCatalog.absoluteUrl(playable);
            Glide.with(this).load(url == null ? playable : url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .fitCenter().into(heroMedia);
        }
    }

    private void stopVideo() {
        if (heroVideo != null) {
            try { heroVideo.stopPlayback(); } catch (Exception ignored) { }
            if (heroMediaHolder != null) heroMediaHolder.removeView(heroVideo);
            heroVideo = null;
        }
    }

    private static int indexForType(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return i;
        return 0;
    }

    static String normalizeType(@Nullable String type) {
        if (type == null || type.isEmpty()) return "vip_badge";
        if ("frames".equals(type) || "host_badge".equals(type) || "vip_badge".equals(type)) return "vip_badge";
        if ("join_toast".equals(type) || "entry_effect".equals(type)) return "entry_effect";
        if ("badges".equals(type) || "level_badge".equals(type)) return "level_badge";
        if (TYPES[3].equals(type) || TYPES[4].equals(type) || TYPES[5].equals(type)
                || TYPES[6].equals(type) || TYPES[7].equals(type)) return type;
        return "vip_badge";
    }

    @Override
    protected void onDestroy() {
        stopVideo();
        if (mediator != null) mediator.detach();
        super.onDestroy();
    }
}
