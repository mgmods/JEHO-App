package com.Dramizo.Series.presentation.cosmetics;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.CosmeticDtos;
import com.Dramizo.Series.databinding.ActivityCosmeticsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.GenderVerifiedBadge;
import com.Dramizo.Series.util.HostSignalView;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Locale;

/**
 * Appearance mall — fully native (no WebView).
 * قطاع الراس uses {@link HostSignalView} SVGA — same engine as the live room.
 */
public class CosmeticsActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";

    static final String[] TYPES = {
            "vip_badge", "entry_effect",
            "level_badge", "room_card", "room_background"
    };

    private ActivityCosmeticsBinding binding;
    private CosmeticsViewModel vm;
    private SessionManager session;
    private boolean bagMode;
    private String initialType = "vip_badge";
    @Nullable private String roomId;
    @Nullable private String lastWearUrl;
    @Nullable private Dialog entryVideoDialog;
    @Nullable private ExoPlayer entryVideoPlayer;
    @Nullable private PlayerView entryVideoPlayerView;
    @Nullable private String activeEntryVideoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCosmeticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = ContainerProvider.from(this).getSessionManager();
        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(CosmeticsViewModel.class);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnRecharge.setOnClickListener(v ->
                com.Dramizo.Series.util.BalanceRedirect.openRecharge(this));
        binding.btnBag.setOnClickListener(v -> toggleBagMode());

        initialType = normalizeType(getIntent().getStringExtra(CosmeticsViewModel.EXTRA_TYPE));
        bagMode = getIntent().getBooleanExtra("bag_mode", false);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        if (roomId == null || roomId.isEmpty()) {
            roomId = getIntent().getStringExtra("roomId");
        }
        vm.setBagMode(bagMode);
        refreshBagButton();
        setupTabs();
        observeVm();
        // Show self + wear immediately (profile-style)
        previewItem(null);
    }

    private void setupTabs() {
        binding.pager.setAdapter(new Pager(this));
        binding.pager.setOffscreenPageLimit(1);
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) ->
                tab.setText(tabLabel(position))).attach();
        int start = tabForType(initialType);
        binding.pager.setCurrentItem(start, false);
        vm.setFilterType(TYPES[start]);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position < 0 || position >= TYPES.length) return;
                vm.setFilterType(TYPES[position]);
                vm.clearSelection();
                lastWearUrl = null;
                if (binding != null) {
                    stopEntryVideoPreview();
                    binding.nativeHeroFrame.clearSignal();
                    binding.heroMedia.setVisibility(View.GONE);
                    Glide.with(binding.heroMedia).clear(binding.heroMedia);
                    binding.tvHeroName.setText(R.string.mall_preview);
                    binding.tvHeroDays.setVisibility(View.GONE);
                }
            }
        });
    }

    private void observeVm() {
        vm.getSelectedItem().observe(this, this::previewItem);
        vm.getMessage().observe(this, m -> {
            if (m != null && !m.isEmpty()) Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
        });
        vm.getError().observe(this, e -> {
            if (e != null) com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });
    }

    private void previewItem(@Nullable CosmeticDtos.CosmeticDto item) {
        if (binding == null) return;
        if (item == null) {
            stopEntryVideoPreview();
            AuthDtos.UserDto me = session.getUser();
            if (me != null) {
                String n = me.displayName != null && !me.displayName.isEmpty()
                        ? me.displayName
                        : (me.username != null ? me.username : getString(R.string.mall_preview));
                binding.tvHeroName.setText(n);
                GenderVerifiedBadge.bind(binding.tvHeroName, null, me);
            } else {
                binding.tvHeroName.setText(R.string.mall_preview);
            }
            binding.tvHeroHint.setText(R.string.mall_preview_hint);
            binding.tvHeroDays.setVisibility(View.GONE);
            // Keep frame wear on avatar when no catalog selection.
            if (me != null) {
                lastWearUrl = null;
                binding.heroMedia.setVisibility(View.GONE);
                binding.nativeHeroFrame.setVisibility(View.VISIBLE);
                binding.nativeHeroFrame.bind(me.vipBadgeUrl, me.avatarUrl, null, 1);
            } else {
                binding.nativeHeroFrame.clearSignal();
                binding.heroMedia.setVisibility(View.GONE);
            }
            lastWearUrl = me != null ? me.vipBadgeUrl : null;
            return;
        }
        binding.tvHeroName.setText(item.name != null && !item.name.isEmpty()
                ? item.name : (item.code != null ? item.code : "—"));
        binding.tvHeroHint.setText(hintForType(item.type != null ? item.type : vm.getFilterType()));
        Integer days = vm.daysLeft(item.id);
        if (days != null && days > 0) {
            binding.tvHeroDays.setVisibility(View.VISIBLE);
            binding.tvHeroDays.setText(getString(R.string.mall_days_left, days));
        } else if (vm.isOwned(item.id) && days == null) {
            binding.tvHeroDays.setVisibility(View.VISIBLE);
            binding.tvHeroDays.setText(R.string.mall_permanent);
        } else {
            binding.tvHeroDays.setVisibility(View.GONE);
        }

        String type = normalizeType(item.type != null ? item.type : vm.getFilterType());
        String wear = firstNonEmpty(item.animationUrl, item.previewUrl);
        String avatar = session.getAvatarUrl();

        if ("vip_badge".equals(type) || "host_badge".equals(type)) {
            binding.heroMedia.setVisibility(View.GONE);
            Glide.with(binding.heroMedia).clear(binding.heroMedia);
            String abs = wear;
            if (abs != null && abs.equals(lastWearUrl) && binding.nativeHeroFrame.getVisibility() == View.VISIBLE) {
                return;
            }
            lastWearUrl = abs;
            binding.nativeHeroFrame.setVisibility(View.VISIBLE);
            binding.nativeHeroFrame.bind(abs, avatar, item.meta, 1);
        } else {
            lastWearUrl = null;
            String mediaUrl = animatedMediaUrl(item.animationUrl, item.previewUrl);
            com.Dramizo.Series.util.CosmeticMedia.Kind mediaKind =
                    com.Dramizo.Series.util.CosmeticMedia.kind(mediaUrl);

            // SVGA and video entry effects must use the native animation player,
            // not a generated PNG sibling. HostSignalView owns one player and
            // releases it when another item/category is selected.
            if (mediaKind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO) {
                binding.nativeHeroFrame.clearSignal();
                binding.heroMedia.setVisibility(View.GONE);
                Glide.with(binding.heroMedia).clear(binding.heroMedia);
                showEntryVideoPreview(AssetCatalog.absoluteUrl(mediaUrl),
                        item.name != null ? item.name : "معاينة الدخولية");
                return;
            }
            if (mediaKind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA) {
                stopEntryVideoPreview();
                Glide.with(binding.heroMedia).clear(binding.heroMedia);
                binding.heroMedia.setImageDrawable(null);
                binding.heroMedia.setVisibility(View.GONE);
                binding.nativeHeroFrame.setVisibility(View.VISIBLE);
                binding.nativeHeroFrame.bind(AssetCatalog.absoluteUrl(mediaUrl),
                        null, item.meta, 1);
                return;
            }
            stopEntryVideoPreview();

            binding.nativeHeroFrame.clearSignal();
            binding.heroMedia.setVisibility(View.VISIBLE);
            String still = stillPngUrl(item.previewUrl, item.animationUrl);
            String abs = AssetCatalog.absoluteUrl(still);
            if (abs == null || abs.isEmpty()) {
                binding.heroMedia.setImageResource(ImagePlaceholder.cover());
            } else {
                Glide.with(binding.heroMedia)
                        .load(abs)
                        .fitCenter()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(ImagePlaceholder.cover())
                        .error(ImagePlaceholder.cover())
                        .into(binding.heroMedia);
            }
        }
    }

    /**
     * Play video entry-effect previews in a centered, non-fullscreen in-app dialog.
     * Audio is enabled and native controls remain available to the user.
     */
    private void showEntryVideoPreview(@Nullable String url, String title) {
        if (url == null || url.trim().isEmpty()) return;
        if (url.equals(activeEntryVideoUrl) && entryVideoDialog != null
                && entryVideoDialog.isShowing()) {
            return;
        }
        stopEntryVideoPreview();
        final Dialog dialog = new Dialog(this);
        entryVideoDialog = dialog;
        activeEntryVideoUrl = url;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dpMall(12), dpMall(10), dpMall(12), dpMall(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(20, 24, 35));
        background.setCornerRadius(dpMall(16));
        shell.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(15f);
        heading.setMaxLines(1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0, dpMall(42), 1f));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20f);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(android.R.drawable.list_selector_background);
        header.addView(heading);
        header.addView(close, new LinearLayout.LayoutParams(dpMall(42), dpMall(42)));
        shell.addView(header);

        PlayerView playerView = new PlayerView(this);
        entryVideoPlayerView = playerView;
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpMall(220));
        videoParams.topMargin = dpMall(4);
        shell.addView(playerView, videoParams);

        dialog.setContentView(shell);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(d -> releaseEntryVideoPlayer());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnCancelListener(d -> releaseEntryVideoPlayer());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.62f;
            window.setAttributes(attrs);
        }
        dialog.show();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dpMall(32),
                    dpMall(420));
            window.setLayout(Math.max(dpMall(260), width), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        try {
            ExoPlayer player = new ExoPlayer.Builder(this).build();
            entryVideoPlayer = player;
            playerView.setPlayer(player);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.setVolume(1f);
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر تشغيل فيديو الدخولية", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
    }

    private int dpMall(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void releaseEntryVideoPlayer() {
        PlayerView view = entryVideoPlayerView;
        entryVideoPlayerView = null;
        ExoPlayer player = entryVideoPlayer;
        entryVideoPlayer = null;
        if (view != null) {
            try { view.setPlayer(null); } catch (Exception ignored) {}
        }
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
        }
        activeEntryVideoUrl = null;
    }

    private void stopEntryVideoPreview() {
        Dialog dialog = entryVideoDialog;
        entryVideoDialog = null;
        if (dialog != null && dialog.isShowing()) {
            try { dialog.dismiss(); } catch (Exception ignored) {}
        } else {
            releaseEntryVideoPlayer();
        }
    }

    private void toggleBagMode() {
        bagMode = !bagMode;
        vm.setBagMode(bagMode);
        refreshBagButton();
        Toast.makeText(this,
                bagMode ? getString(R.string.my_bag_mall) : getString(R.string.appearance_store),
                Toast.LENGTH_SHORT).show();
        vm.clearSelection();
        // Reload active fragment page.
        int page = binding.pager.getCurrentItem();
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof CosmeticsCategoryFragment) {
                ((CosmeticsCategoryFragment) f).reload();
            }
        }
        if (page >= 0 && page < TYPES.length) {
            vm.loadForType(TYPES[page]);
        }
    }

    private void refreshBagButton() {
        if (binding.btnBag == null) return;
        binding.btnBag.setAlpha(bagMode ? 1f : 0.75f);
        binding.btnBag.setTextColor(bagMode ? 0xFFE8A317 : 0xFFB8860B);
        if (binding.tvTitle != null) {
            binding.tvTitle.setText(bagMode
                    ? getString(R.string.my_bag_mall)
                    : getString(R.string.appearance_store));
        }
    }

    static String normalizeType(String type) {
        if (type == null || type.isEmpty()) return "vip_badge";
        if ("badges".equals(type) || "level_badge".equals(type)) return "level_badge";
        if ("frames".equals(type) || "vip_badge".equals(type) || "host_badge".equals(type)) {
            return "vip_badge";
        }
        if ("join_toast".equals(type) || "entry_effect".equals(type)) return "entry_effect";
        if ("room_card".equals(type)) return "room_card";
        if ("room_background".equals(type)) return "room_background";
        for (String t : TYPES) {
            if (t.equals(type)) return t;
        }
        return "vip_badge";
    }

    static int tabForType(String type) {
        String n = normalizeType(type);
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(n)) return i;
        }
        return 0;
    }

    private String tabLabel(int position) {
        switch (position) {
            case 0: return getString(R.string.mall_tab_frames);
            case 1: return getString(R.string.mall_tab_entry);
            case 2: return getString(R.string.mall_tab_level);
            case 3: return getString(R.string.mall_tab_room_card);
            case 4: return getString(R.string.mall_tab_room_bg);
            default: return "";
        }
    }

    private String hintForType(String type) {
        String n = normalizeType(type);
        switch (n) {
            case "vip_badge": return getString(R.string.mall_hint_frames);
            case "entry_effect": return getString(R.string.mall_hint_entry);
            case "level_badge": return getString(R.string.mall_hint_level);
            case "room_card": return getString(R.string.mall_hint_room_card);
            case "room_background": return getString(R.string.mall_hint_room_bg);
            default: return getString(R.string.mall_preview_hint);
        }
    }

    @Override
    protected void onDestroy() {
        stopEntryVideoPreview();
        if (binding != null) {
            try {
                binding.nativeHeroFrame.destroy();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }

    @Nullable
    private static String animatedMediaUrl(String animationUrl, String previewUrl) {
        // Admin catalogs can put the playable asset in either field; don't let
        // a static thumbnail in animationUrl mask an SVGA/video previewUrl.
        com.Dramizo.Series.util.CosmeticMedia.Kind animationKind =
                com.Dramizo.Series.util.CosmeticMedia.kind(animationUrl);
        if (animationKind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA
                || animationKind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO) {
            return animationUrl;
        }
        com.Dramizo.Series.util.CosmeticMedia.Kind previewKind =
                com.Dramizo.Series.util.CosmeticMedia.kind(previewUrl);
        if (previewKind == com.Dramizo.Series.util.CosmeticMedia.Kind.SVGA
                || previewKind == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO) {
            return previewUrl;
        }
        return firstNonEmpty(animationUrl, previewUrl);
    }

    private static String stillPngUrl(String preview, String anim) {
        // For a selected entry effect, let Glide play an actual GIF instead of
        // always replacing it with a static thumbnail. The product grid remains
        // static/lightweight; only this single hero preview can animate.
        if (anim != null) {
            String lower = anim.toLowerCase(java.util.Locale.US).split("\\?", 2)[0];
            if (lower.endsWith(".gif")) return anim;
        }
        // Keep the grid-compatible static fallback for SVGA/video and other media.
        return CosmeticsAdapter.stillPreviewUrl(preview, anim);
    }

    private static final class Pager extends FragmentStateAdapter {
        Pager(@NonNull FragmentActivity activity) { super(activity); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            String type = TYPES[Math.max(0, Math.min(position, TYPES.length - 1))];
            return CosmeticsCategoryFragment.newInstance(type, "");
        }

        @Override
        public int getItemCount() { return TYPES.length; }
    }
}
