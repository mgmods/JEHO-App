package com.Dramizo.Series.presentation.cosmetics;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.databinding.ActivityCosmeticsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiOrigin;
import com.Dramizo.Series.util.AssetCatalog;

import org.json.JSONObject;

/**
 * Appearance mall — HTML catalog in WebView.
 * قطاع الراس preview uses native {@link HostSignalView} (same SVGA engine as the live room).
 */
public class CosmeticsActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";

    /** Order kept for Intent type mapping from older callers. */
    static final String[] TYPES = {
            "vip_badge", "entry_effect",
            "level_badge", "room_card", "room_background"
    };

    private ActivityCosmeticsBinding binding;
    private SessionManager session;
    private boolean bagMode;
    private String initialType = "vip_badge";
    @Nullable private String roomId;
    private boolean pageReady;
    @Nullable private String lastWearUrl;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCosmeticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = ContainerProvider.from(this).getSessionManager();

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
        refreshBagButton();
        setNativeHeroVisible("vip_badge".equals(initialType));
        setupWebView();
        loadMall();
    }

    private void setupWebView() {
        WebView web = binding.webMall;
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Cache heavy assets (PNG/SVGA). HTML is busted via ?nocache= timestamp on each open.
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new MallBridge(), "MallBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                injectConfig();
            }
        });
    }

    private void loadMall() {
        // Fresh HTML/JS only — keep disk cache for frame PNGs / one selected SVGA.
        long bust = System.currentTimeMillis();
        StringBuilder url = new StringBuilder(ApiOrigin.origin())
                .append("/mall/index.html?nocache=").append(bust)
                .append("&type=").append(initialType)
                .append("&bag=").append(bagMode ? "1" : "0");
        if (roomId != null && !roomId.isEmpty()) {
            url.append("&roomId=").append(roomId);
        }
        binding.webMall.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        binding.webMall.loadUrl(url.toString());
    }

    private void injectConfig() {
        if (!pageReady || binding == null) return;
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("apiBase", ApiOrigin.apiV1());
            cfg.put("token", session.getAccessToken() != null ? session.getAccessToken() : "");
            String avatar = session.getAvatarUrl();
            cfg.put("avatarUrl", avatar != null ? AssetCatalog.absoluteUrl(avatar) : "");
            cfg.put("displayName", session.getDisplayName() != null ? session.getDisplayName() : "");
            cfg.put("roomId", roomId != null ? roomId : "");
            cfg.put("bagMode", bagMode);
            cfg.put("type", initialType);
            cfg.put("nativeFramePreview", false);
            String js = "window.MallPage&&window.MallPage.setConfig(" + cfg + ");";
            binding.webMall.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void setNativeHeroVisible(boolean show) {
        if (binding == null || binding.nativeHeroWrap == null) return;
        // Hidden: mall plays selected-frame SVGA in WebView (avoids HostSignalView
        // download semaphore starving when the live room already holds SVGA slots).
        binding.nativeHeroWrap.setVisibility(View.GONE);
        lastWearUrl = null;
    }

    private void previewNativeFrame(@Nullable String wearUrl) {
        // No-op — WebView mall.js plays the single selected SVGA.
    }

    private void toggleBagMode() {
        bagMode = !bagMode;
        refreshBagButton();
        Toast.makeText(this,
                bagMode ? getString(R.string.my_bag_mall) : getString(R.string.appearance_store),
                Toast.LENGTH_SHORT).show();
        if (pageReady) {
            binding.webMall.evaluateJavascript(
                    "window.MallPage&&window.MallPage.setBagMode(" + bagMode + ");", null);
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

    @Override
    protected void onDestroy() {
        if (binding != null && binding.webMall != null) {
            binding.webMall.removeJavascriptInterface("MallBridge");
            binding.webMall.destroy();
        }
        super.onDestroy();
    }

    private final class MallBridge {
        @JavascriptInterface
        public String getApiBase() {
            return ApiOrigin.apiV1();
        }

        @JavascriptInterface
        public String getToken() {
            String t = session.getAccessToken();
            return t != null ? t : "";
        }

        @JavascriptInterface
        public String getAvatarUrl() {
            String a = session.getAvatarUrl();
            return a != null ? AssetCatalog.absoluteUrl(a) : "";
        }

        @JavascriptInterface
        public String getDisplayName() {
            String n = session.getDisplayName();
            return n != null ? n : "";
        }

        @JavascriptInterface
        public String getRoomId() {
            return roomId != null ? roomId : "";
        }

        @JavascriptInterface
        public boolean hasNativeFramePreview() {
            return false;
        }

        /** Kept for JS bridge compatibility — motion is played inside the mall WebView. */
        @JavascriptInterface
        public void previewWear(String type, String wearUrl, String previewUrl) {
            // no-op
        }

        @JavascriptInterface
        public void clearWear() {
            runOnUiThread(() -> setNativeHeroVisible(false));
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> {
                if (msg != null && !msg.isEmpty()) {
                    Toast.makeText(CosmeticsActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /** Open coin packages when mall purchase fails for low balance. */
        @JavascriptInterface
        public void openRecharge() {
            runOnUiThread(() ->
                    com.Dramizo.Series.util.BalanceRedirect.openRecharge(CosmeticsActivity.this));
        }

        @JavascriptInterface
        public void openRechargeIfNeeded(String msg) {
            runOnUiThread(() -> {
                if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(msg)) {
                    com.Dramizo.Series.util.BalanceRedirect.handle(CosmeticsActivity.this, msg);
                } else if (msg != null && !msg.isEmpty()) {
                    Toast.makeText(CosmeticsActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void onEquipped(String cosmeticId, String type) {
            // Profile wear URLs are updated server-side; room refreshes on next bind/resume.
        }

        @JavascriptInterface
        public void close() {
            runOnUiThread(CosmeticsActivity.this::navigateUp);
        }
    }
}
