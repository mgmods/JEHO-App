package com.Dramizo.Series.presentation.cosmetics;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiOrigin;
import com.Dramizo.Series.util.BalanceRedirect;

/**
 * JEHO store hosted HTML surface. The page owns its RTL layout and catalog UI;
 * the bridge exposes only the current session and native recharge/toast actions.
 */
public class CosmeticsActivity extends ThemedActivity {
    public static final String EXTRA_ROOM_ID = "room_id";
    static final String[] TYPES = {
            "vip_badge", "entry_effect",
            "level_badge", "room_card", "room_background"
    };

    @Nullable private WebView mallWebView;
    @Nullable private SessionManager session;
    @Nullable private String roomId;
    @Nullable private String mallOrigin;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = ContainerProvider.from(this).getSessionManager();
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        if (roomId == null || roomId.isEmpty()) roomId = getIntent().getStringExtra("roomId");

        mallOrigin = ApiOrigin.origin();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 14, 28));
        WebView webView = new WebView(this);
        mallWebView = webView;
        webView.setBackgroundColor(Color.rgb(12, 14, 28));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        android.webkit.WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new MallBridge(), "MallBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            private static final String ASSET_HOST = "appassets.androidplatform.net";
            private static final String ASSET_PREFIX = "/assets/mall/";

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (ASSET_HOST.equalsIgnoreCase(String.valueOf(uri.getHost()))
                        && uri.getPath() != null && uri.getPath().startsWith(ASSET_PREFIX)) {
                    String relative = Uri.decode(uri.getPath().substring(ASSET_PREFIX.length()));
                    if (relative.isEmpty() || relative.endsWith("/")) relative += "index.html";
                    try {
                        InputStream stream = getAssets().open("mall/" + relative);
                        Map<String, String> headers = new HashMap<>();
                        headers.put("Cache-Control", "no-store");
                        return new WebResourceResponse(mimeType(relative), "UTF-8", 200,
                                "OK", headers, stream);
                    } catch (Exception ignored) {
                        // Let WebView report missing generated assets rather than crash the Activity.
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();
                boolean sameApiHost = host != null && mallOrigin != null
                        && host.equalsIgnoreCase(Uri.parse(mallOrigin).getHost());
                boolean localMallHost = host != null && host.equalsIgnoreCase(ASSET_HOST);
                return !("https".equalsIgnoreCase(scheme) && (sameApiHost || localMallHost));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    Toast.makeText(CosmeticsActivity.this,
                            "تعذّر تحميل المتجر، تحقق من الاتصال وحاول مجدداً",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String type = normalizeType(getIntent().getStringExtra("type"));
        String initial = getIntent().getStringExtra(
                com.Dramizo.Series.presentation.cosmetics.CosmeticsViewModel.EXTRA_TYPE);
        if (initial != null && !initial.isEmpty()) type = normalizeType(initial);
        boolean bag = getIntent().getBooleanExtra("bag_mode", false);
        String section = getIntent().getStringExtra("store_section");
        if ("level".equals(section) || "badges".equals(section)
                || "id".equals(section) || "vip".equals(section)) {
            webView.loadUrl("https://appassets.androidplatform.net/assets/mall/" + section + ".html");
            return;
        }
        Uri.Builder page = Uri.parse("https://appassets.androidplatform.net/assets/mall/index.html")
                .buildUpon()
                .appendQueryParameter("type", type)
                .appendQueryParameter("bag", bag ? "1" : "0");
        if (roomId != null && !roomId.isEmpty()) {
            page.appendQueryParameter("roomId", roomId);
        }
        webView.loadUrl(page.build().toString());
    }

    /** The app uses the system back gesture/button; the HTML intentionally has no top back button. */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        WebView webView = mallWebView;
        mallWebView = null;
        if (webView != null) {
            try { webView.stopLoading(); } catch (Exception ignored) {}
            try { webView.removeJavascriptInterface("MallBridge"); } catch (Exception ignored) {}
            try { webView.loadUrl("about:blank"); } catch (Exception ignored) {}
            try { webView.destroy(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private static String mimeType(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".svga")) return "application/octet-stream";
        return "application/octet-stream";
    }

    static String normalizeType(@Nullable String type) {
        if (type == null || type.isEmpty()) return "vip_badge";
        if ("frames".equals(type) || "host_badge".equals(type) || "vip_badge".equals(type)) {
            return "vip_badge";
        }
        if ("join_toast".equals(type) || "entry_effect".equals(type)) return "entry_effect";
        if ("badges".equals(type) || "level_badge".equals(type)) return "level_badge";
        if ("room_card".equals(type)) return "room_card";
        if ("room_background".equals(type)) return "room_background";
        return "vip_badge";
    }

    private final class MallBridge {
        @JavascriptInterface
        public String getApiBase() { return ApiOrigin.apiV1(); }

        @JavascriptInterface
        public String getToken() {
            return session != null && session.getAccessToken() != null
                    ? session.getAccessToken() : "";
        }

        @JavascriptInterface
        public String getAvatarUrl() {
            return session != null && session.getAvatarUrl() != null
                    ? session.getAvatarUrl() : "";
        }

        @JavascriptInterface
        public String getDisplayName() {
            return session != null && session.getDisplayName() != null
                    ? session.getDisplayName() : "";
        }

        @JavascriptInterface
        public String getRoomId() { return roomId != null ? roomId : ""; }

        @JavascriptInterface
        public boolean hasNativeFramePreview() { return false; }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(CosmeticsActivity.this,
                    message == null ? "" : message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void openRecharge() {
            runOnUiThread(() -> BalanceRedirect.openRecharge(CosmeticsActivity.this));
        }

        @JavascriptInterface
        public void openRechargeIfNeeded(String message) {
            runOnUiThread(() -> BalanceRedirect.handleForced(CosmeticsActivity.this, message));
        }

        // The HTML preview draws the selected media itself. These compatibility methods
        // intentionally do not alter profile or room voice state.
        @JavascriptInterface
        public void previewWear(String type, String animationUrl, String previewUrl) { }

        @JavascriptInterface
        public void clearWear() { }

        @JavascriptInterface
        public void onEquipped(String itemId, String type) { }
    }
}
