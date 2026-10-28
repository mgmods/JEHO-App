package com.Dramizo.Series.presentation.cp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.invite.InvitationActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All CP screens are rendered in the local HTML page; API and identity remain native-side. */
public class CpCenterActivity extends ThemedActivity {
    public static final String EXTRA_VIEW = "cp_html_view";
    public static final String EXTRA_TYPE = "cp_html_type";
    private WebView web;
    private AppContainer c;
    private final Gson gson = new Gson();
    private boolean pageReady;
    private String initialView = "home";
    private String initialType = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        c = ContainerProvider.from(this);
        initialView = safeView(getIntent().getStringExtra(EXTRA_VIEW));
        initialType = safeType(getIntent().getStringExtra(EXTRA_TYPE));
        web = new WebView(this);
        web.setBackgroundColor(0xFFFFF7F5);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                web.evaluateJavascript("window.setCpView && window.setCpView(" +
                        gson.toJson(initialView) + "," + gson.toJson(initialType) + ")", null);
                loadHome();
                loadRequests();
            }
        });
        web.addJavascriptInterface(new CpBridge(), "JehoCP");
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(root);
        web.loadUrl("file:///android_asset/cp-center.html");
    }

    private String safeView(String value) {
        return "send".equals(value) || "incoming".equals(value) || "sent".equals(value)
                || "discover".equals(value) || "categories".equals(value) || "store".equals(value)
                || "tasks".equals(value) || "insights".equals(value) || "gifts".equals(value)
                ? value : "home";
    }
    private String safeType(String value) {
        for (String allowed : new String[]{"brotherhood", "friendship", "love", "engagement", "marriage"}) {
            if (allowed.equals(value)) return value;
        }
        return "";
    }

    private void js(String expression) {
        runOnUiThread(() -> {
            if (web != null && pageReady && !isFinishing()) web.evaluateJavascript(expression, null);
        });
    }
    private void message(String text) { js("window.showCpMessage && window.showCpMessage(" + gson.toJson(text) + ")"); }
    private String nameFor(String type) {
        switch (type == null ? "" : type) {
            case "brotherhood": return "الأخوّة";
            case "friendship": return "الصداقة";
            case "love": return "الحب";
            case "engagement": return "الخطوبة";
            case "marriage": return "الزواج";
            default: return "علاقة CP";
        }
    }

    @SuppressWarnings("unchecked")
    private static String webImageUrl(String pathOrUrl) {
        String resolved = AssetCatalog.absoluteUrl(pathOrUrl);
        if (resolved == null) return null;
        if (resolved.startsWith("http://")) return "https://" + resolved.substring("http://".length());
        return resolved;
    }

    private Object normalizeUrls(Object value) {
        if (value instanceof Map) {
            Map<String, Object> normalized = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object item = entry.getValue();
                if ("avatarUrl".equals(key) && item != null) {
                    normalized.put(key, webImageUrl(String.valueOf(item)));
                } else {
                    normalized.put(key, normalizeUrls(item));
                }
            }
            return normalized;
        }
        if (value instanceof List) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : (List<?>) value) normalized.add(normalizeUrls(item));
            return normalized;
        }
        return value;
    }

    private void loadHome() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().myCpRelationships());
            if (r.success && r.data != null && !r.data.isEmpty()) {
                js("window.renderCpHome && window.renderCpHome(" + gson.toJson(normalizeUrls(r.data)) + ")");
                return;
            }

            // Older installations stored accepted CP pairs in social_requests rather than
            // cp_relationships. Read the legacy endpoint as a fallback; never rewrite or
            // charge for the existing relationship just to make it visible again.
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.CpStatusDto> legacy =
                    ApiCall.execute(c.getUserApi().myCp());
            if (legacy.success && legacy.data != null && legacy.data.hasCp
                    && legacy.data.partner != null) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", legacy.data.relationId != null ? legacy.data.relationId : "legacy-cp");
                row.put("type", "love");
                row.put("status", "active");
                row.put("points", legacy.data.bondScore);
                row.put("level", Math.max(1, legacy.data.level));
                row.put("legacy", true);
                row.put("partner", gson.fromJson(gson.toJson(legacy.data.partner), Map.class));
                com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto self =
                        c.getSessionManager().getUser();
                if (self != null) {
                    row.put("self", gson.fromJson(gson.toJson(self), Map.class));
                }
                js("window.renderCpHome && window.renderCpHome(" +
                        gson.toJson(normalizeUrls(java.util.Collections.singletonList(row))) + ")");
                return;
            }

            js("window.renderCpHome && window.renderCpHome([])");
            if (!r.success && r.error != null) message(r.error);
        });
    }

    private void loadRequests() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> incoming = ApiCall.execute(c.getUserApi().cpRequests());
            Result<List<Map<String, Object>>> sent = ApiCall.execute(c.getUserApi().cpSentRequests());
            List<Map<String, Object>> in = incoming.success && incoming.data != null
                    ? incoming.data : new ArrayList<>();
            List<Map<String, Object>> out = sent.success && sent.data != null
                    ? sent.data : new ArrayList<>();
            js("window.renderCpRequests && window.renderCpRequests(" +
                    gson.toJson(normalizeUrls(in)) + "," + gson.toJson(normalizeUrls(out)) + ")");
            if (!incoming.success && incoming.error != null) message(incoming.error);
            else if (!sent.success && sent.error != null) message(sent.error);
        });
    }

    private void loadDiscover(String type) {
        String safe = safeType(type);
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().discoverCp(safe.isEmpty() ? null : safe));
            if (r.success && r.data != null) {
                js("window.renderCpDiscover && window.renderCpDiscover(" + gson.toJson(normalizeUrls(r.data)) + ")");
            } else {
                js("window.renderCpDiscover && window.renderCpDiscover([])");
                message(r.error != null ? r.error : "تعذر تحميل العلاقات النشطة");
            }
        });
    }

    private void loadLeaderboard(String period) {
        String safePeriod = "world".equals(period) ? "world" : "weekly";
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().cpLeaderboard(safePeriod));
            if (r.success && r.data != null) {
                js("window.renderCpLeaderboard && window.renderCpLeaderboard(" +
                        gson.toJson(normalizeUrls(r.data)) + "," + gson.toJson(safePeriod) + ")");
            } else {
                js("window.renderCpLeaderboard && window.renderCpLeaderboard([]," + gson.toJson(safePeriod) + ")");
                message(r.error != null ? r.error : "تعذر تحميل ترتيب الثنائيات");
            }
        });
    }

    private void loadRewards() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().cpRewards());
            List<Map<String, Object>> rows = r.success && r.data != null ? r.data : new ArrayList<>();
            js("window.renderCpRewards && window.renderCpRewards(" + gson.toJson(normalizeUrls(rows)) + ")");
            if (!r.success && r.error != null) message(r.error);
        });
    }

    private void loadCatalog() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().cpCatalog());
            List<Map<String, Object>> rows = r.success && r.data != null ? r.data : new ArrayList<>();
            js("window.renderCpCatalog && window.renderCpCatalog(" + gson.toJson(normalizeUrls(rows)) + ")");
        });
    }

    private void loadTasks() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> r = ApiCall.execute(c.getUserApi().cpTasks());
            List<Map<String, Object>> rows = r.success && r.data != null ? r.data : new ArrayList<>();
            js("window.renderCpTasks && window.renderCpTasks(" + gson.toJson(normalizeUrls(rows)) + ")");
        });
    }

    private final class CpBridge {
        @JavascriptInterface public void loadHome() { CpCenterActivity.this.loadHome(); }
        @JavascriptInterface public void loadRequests() { CpCenterActivity.this.loadRequests(); }
        @JavascriptInterface public void loadDiscover(String type) { CpCenterActivity.this.loadDiscover(type); }
        @JavascriptInterface public void loadLeaderboard(String period) { CpCenterActivity.this.loadLeaderboard(period); }
        @JavascriptInterface public void loadRewards() { CpCenterActivity.this.loadRewards(); }
        @JavascriptInterface public void loadCatalog() { CpCenterActivity.this.loadCatalog(); }
        @JavascriptInterface public void loadTasks() { CpCenterActivity.this.loadTasks(); }

        @JavascriptInterface public void sendRequest(String recipientId, String type) {
            String recipient = recipientId == null ? "" : recipientId.trim();
            String relationshipType = safeType(type);
            if (recipient.isEmpty() || relationshipType.isEmpty()) {
                message("أدخل معرّف المستخدم واختر نوع العلاقة.");
                return;
            }
            Map<String, String> body = new HashMap<>();
            body.put("recipientId", recipient);
            body.put("type", relationshipType);
            body.put("idempotencyKey", UUID.randomUUID().toString());
            c.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> r = ApiCall.execute(c.getUserApi().createProfileCpRequest(body));
                if (r.success) {
                    message("تم إرسال طلب " + nameFor(relationshipType) + " مباشرة. سيصل للطرف الآخر إشعار للقبول أو الرفض.");
                    js("document.getElementById('recipientId').value='';");
                    loadRequests();
                } else {
                    message(r.error != null ? r.error : "تعذر إرسال طلب العلاقة");
                }
            });
        }

        @JavascriptInterface public void respondRequest(String requestId, boolean accept) {
            Map<String, Boolean> body = new HashMap<>();
            body.put("accept", accept);
            c.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> r = ApiCall.execute(c.getUserApi().respondCpRequest(requestId, body));
                String responseText;
                if (r.success) {
                    String status = r.data == null ? "" : String.valueOf(r.data.get("status"));
                    responseText = "expired".equals(status) ? "انتهت مهلة الطلب."
                            : accept ? "تم قبول العلاقة بنجاح." : "تم رفض الطلب.";
                    message(responseText);
                    loadHome();
                    loadRequests();
                    js("window.onCpRequestResponse && window.onCpRequestResponse("
                            + gson.toJson(requestId) + "," + accept + ",true," + gson.toJson(responseText) + ")");
                } else {
                    responseText = r.error != null ? r.error : "تعذرت معالجة الطلب";
                    message(responseText);
                    js("window.onCpRequestResponse && window.onCpRequestResponse("
                            + gson.toJson(requestId) + "," + accept + ",false," + gson.toJson(responseText) + ")");
                }
            });
        }

        @JavascriptInterface public void cancelRequest(String requestId) {
            c.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> r = ApiCall.execute(c.getUserApi().cancelCpRequest(requestId));
                if (r.success) {
                    message("تم إلغاء الطلب.");
                    loadRequests();
                } else message(r.error != null ? r.error : "تعذر إلغاء الطلب");
            });
        }

        @JavascriptInterface public void endRelationship(String relationshipId) {
            c.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> r = ApiCall.execute(c.getUserApi().endCpRelationship(relationshipId));
                if (r.success) {
                    message("تم إنهاء العلاقة.");
                    loadHome();
                    loadDiscover("");
                } else message(r.error != null ? r.error : "تعذر إنهاء العلاقة");
            });
        }

        @JavascriptInterface public void openProfile(String userId) {
            runOnUiThread(() -> {
                if (userId == null || userId.trim().isEmpty()) return;
                UserProfileCardSheet.show(CpCenterActivity.this, userId.trim());
            });
        }
        @JavascriptInterface public void openInvite() {
            runOnUiThread(() -> startActivity(new Intent(CpCenterActivity.this, InvitationActivity.class)));
        }
        @JavascriptInterface public void back() { runOnUiThread(CpCenterActivity.this::finish); }
    }

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override public void onBackPressed() {
        if (web == null || !pageReady) { finish(); return; }
        web.evaluateJavascript("(window.getCpView && window.getCpView() !== 'home') ? (window.showView('home'), 'handled') : 'finish'", value -> {
            if ("\"finish\"".equals(value)) finish();
        });
    }

    @Override protected void onDestroy() {
        pageReady = false;
        if (web != null) {
            web.removeJavascriptInterface("JehoCP");
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}