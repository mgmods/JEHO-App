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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private boolean mallHtmlReady;
    private String activeHtmlType = "vip_badge";

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
        activeHtmlType = initialType;
        vm.setBagMode(bagMode);
        refreshBagButton();
        setupTabs();
        observeVm();
        setupHtmlMall();
        // Keep the native animation host alive for real SVGA/frames preview.
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
        vm.getCatalogPage().observe(this, page -> {
            if (page != null && activeHtmlType != null
                    && activeHtmlType.equals(page.type)) {
                pushCatalogToHtml(page);
            }
        });
        vm.getMessage().observe(this, m -> {
            if (mallHtmlReady && m != null && !m.isEmpty()) {
                runOnUiThread(() -> {
                    if (binding != null) binding.mallWebView.evaluateJavascript(
                            "window.mallMessage && window.mallMessage(" + JSONObject.quote(m) + ")", null);
                });
            }
            if (m != null && !m.isEmpty()) Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
        });
        vm.getError().observe(this, e -> {
            if (e != null) com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });
    }

    /** HTML store shell. All commerce actions are forwarded to the existing ViewModel/repository. */
    private void setupHtmlMall() {
        WebView web = binding.mallWebView;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new MallBridge(), "JEHOStore");
        web.loadUrl("file:///android_asset/mall-store.html");
    }

    private void pushCatalogToHtml(@Nullable CosmeticsViewModel.CatalogPage page) {
        if (!mallHtmlReady || binding == null || page == null || page.items == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", page.type);
            JSONArray rows = new JSONArray();
            for (CosmeticDtos.CosmeticDto item : page.items) {
                if (item == null || item.id == null) continue;
                JSONObject row = new JSONObject();
                row.put("id", item.id);
                row.put("type", item.type);
                row.put("code", item.code);
                row.put("name", item.name);
                row.put("description", item.description);
                row.put("previewUrl", AssetCatalog.absoluteUrl(item.previewUrl));
                row.put("animationUrl", AssetCatalog.absoluteUrl(item.animationUrl));
                row.put("coinPrice", item.coinPrice);
                row.put("minVipLevel", item.minVipLevel);
                row.put("minUserLevel", item.minUserLevel);
                row.put("owned", vm.isOwned(item.id));
                row.put("equipped", vm.isEquipped(item.id));
                Integer days = vm.daysLeft(item.id);
                row.put("daysLeft", days != null ? days : -1);
                rows.put(row);
            }
            payload.put("items", rows);
            String serialized = payload.toString();
            binding.mallWebView.evaluateJavascript(
                    "window.mallRender && window.mallRender(" + JSONObject.quote(serialized) + ")", null);
        } catch (Exception ignored) {
            // A malformed optional catalog field must not crash the store Activity.
        }
    }

    private final class MallBridge {
        @JavascriptInterface public void ready() {
            runOnUiThread(() -> {
                if (binding == null || isFinishing()) return;
                mallHtmlReady = true;
                vm.setBagMode(bagMode);
                vm.setFilterType(activeHtmlType);
                vm.loadForType(activeHtmlType);
                CosmeticsViewModel.CatalogPage page = vm.getCatalogPage().getValue();
                if (page != null && activeHtmlType.equals(page.type)) pushCatalogToHtml(page);
            });
        }

        @JavascriptInterface public void selectType(String type) {
            runOnUiThread(() -> {
                if (binding == null || type == null) return;
                activeHtmlType = normalizeType(type);
                initialType = activeHtmlType;
                vm.setFilterType(activeHtmlType);
                vm.clearSelection();
                stopEntryVideoPreview();
                vm.loadForType(activeHtmlType);
            });
        }

        @JavascriptInterface public void setBagMode(boolean enabled) {
            runOnUiThread(() -> {
                if (binding == null) return;
                bagMode = enabled;
                vm.setBagMode(enabled);
                refreshBagButton();
                vm.loadForType(activeHtmlType);
            });
        }

        @JavascriptInterface public void select(String id) {
            runOnUiThread(() -> {
                if (id == null || binding == null) return;
                CosmeticsViewModel.CatalogPage page = vm.getCatalogPage().getValue();
                if (page == null || page.items == null) return;
                for (CosmeticDtos.CosmeticDto item : page.items) {
                    if (item != null && id.equals(item.id)) {
                        vm.select(item);
                        return;
                    }
                }
            });
        }

        @JavascriptInterface public void purchase(String id) {
            runOnUiThread(() -> { if (id != null && !id.isEmpty()) vm.purchase(id); });
        }

        @JavascriptInterface public void equip(String id) {
            runOnUiThread(() -> { if (id != null && !id.isEmpty()) vm.equip(id); });
        }

        @JavascriptInterface public void unequip(String id) {
            runOnUiThread(() -> { if (id != null && !id.isEmpty()) vm.unequip(id); });
        }

        @JavascriptInterface public void recharge() {
            runOnUiThread(() -> com.Dramizo.Series.util.BalanceRedirect.openRecharge(CosmeticsActivity.this));
        }
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
        String wear = animatedMediaUrl(item.animationUrl, item.previewUrl);
        String avatar = session.getAvatarUrl();

        // A video uploaded into either media field must always be previewed by
        // the audible in-app player, including video-based frames.
        if (com.Dramizo.Series.util.CosmeticMedia.kind(wear)
                == com.Dramizo.Series.util.CosmeticMedia.Kind.VIDEO) {
            binding.nativeHeroFrame.clearSignal();
            binding.heroMedia.setVisibility(View.GONE);
            Glide.with(binding.heroMedia).clear(binding.heroMedia);
            showEntryVideoPreview(AssetCatalog.absoluteUrl(wear),
                    item.name != null ? item.name : "معاينة المتجر");
            return;
        }

        stopEntryVideoPreview();
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
        // Upload forms may put motion in either field. Prefer real playable
        // media (SVGA/video/GIF/animated WebP) over a static thumbnail.
        if (com.Dramizo.Series.util.CosmeticMedia.isAnimatedWear(animationUrl)) {
            return animationUrl;
        }
        if (com.Dramizo.Series.util.CosmeticMedia.isAnimatedWear(previewUrl)) {
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
            if (lower.endsWith(".gif") || lower.endsWith(".webp")) return anim;
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
