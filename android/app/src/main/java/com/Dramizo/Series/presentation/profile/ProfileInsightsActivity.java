package com.Dramizo.Series.presentation.profile;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.cp.CpCenterActivity;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.Dramizo.Series.util.ApiCall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HTML presentation backed only by authenticated app API responses. */
public final class ProfileInsightsActivity extends ThemedActivity {
    private WebView webView;
    private AppContainer container;
    private boolean pageReady;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = ContainerProvider.from(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 16, 32));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setBackgroundColor(Color.rgb(16, 16, 32));
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PageBridge(), "JehoApp");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                loadLiveData();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/profile-insights.html");
    }

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override protected void onResume() {
        super.onResume();
        if (pageReady) loadLiveData();
    }

    private void loadLiveData() {
        if (container == null) return;
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> userResult = ApiCall.execute(container.getUserApi().me());
            Result<List<Map<String, Object>>> relationResult = ApiCall.execute(container.getUserApi().myCpRelationships());
            Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> rankingResult =
                    container.getRankingsUseCase.execute("daily", "rich");
            JSONObject payload = new JSONObject();
            try {
                if (userResult.success && userResult.data != null) {
                    AuthDtos.UserDto u = userResult.data;
                    JSONObject user = new JSONObject();
                    put(user, "id", u.id); put(user, "publicId", u.publicId);
                    put(user, "username", u.username); put(user, "displayName", u.displayName);
                    put(user, "avatarUrl", u.avatarUrl); put(user, "level", u.level);
                    put(user, "wealthLevel", u.wealthLevel); put(user, "wealthScore", u.wealthScore);
                    put(user, "charmScore", u.charmScore); put(user, "vipLevel", u.vipLevel);
                    payload.put("user", user);
                }
                if (relationResult.success && relationResult.data != null) {
                    for (Map<String, Object> row : relationResult.data) {
                        if (row == null || !"active".equals(String.valueOf(row.get("status")))) continue;
                        Object raw = row.get("partner");
                        if (!(raw instanceof Map)) continue;
                        Map<?, ?> p = (Map<?, ?>) raw;
                        JSONObject partner = new JSONObject();
                        put(partner, "id", p.get("id")); put(partner, "username", p.get("username"));
                        put(partner, "displayName", p.get("displayName")); put(partner, "avatarUrl", p.get("avatarUrl"));
                        put(partner, "level", p.get("level")); put(partner, "vipLevel", p.get("vipLevel"));
                        JSONObject relation = new JSONObject();
                        relation.put("partner", partner);
                        put(relation, "type", row.get("type"));
                        put(relation, "typeLabel", cpLabel(String.valueOf(row.get("type"))));
                        put(relation, "level", row.get("level")); put(relation, "points", row.get("points"));
                        payload.put("relationship", relation);
                        break;
                    }
                }
                JSONArray ranks = new JSONArray();
                if (rankingResult.success && rankingResult.data != null && rankingResult.data.items != null) {
                    for (MiscDtos.RankingEntryDto e : rankingResult.data.items) {
                        if (e == null) continue;
                        JSONObject item = new JSONObject();
                        put(item, "rank", e.rank); put(item, "score", e.score); put(item, "targetName", e.targetName);
                        if (e.user != null) {
                            JSONObject ru = new JSONObject();
                            put(ru, "id", e.user.id); put(ru, "username", e.user.username);
                            put(ru, "displayName", e.user.displayName); put(ru, "avatarUrl", e.user.avatarUrl);
                            item.put("user", ru);
                        }
                        ranks.put(item);
                    }
                }
                payload.put("ranking", ranks);
            } catch (Exception ignored) { }
            final String json = payload.toString();
            runOnUiThread(() -> {
                if (webView == null || isFinishing()) return;
                webView.evaluateJavascript("window.renderData && window.renderData(" + json + ");", null);
                if (!userResult.success) webView.evaluateJavascript("window.showError && window.showError();", null);
            });
        });
    }

    private static void put(JSONObject obj, String key, Object value) {
        try { if (value != null) obj.put(key, value); } catch (Exception ignored) { }
    }

    private String cpLabel(String type) {
        if ("love".equalsIgnoreCase(type)) return "الحب";
        if ("friendship".equalsIgnoreCase(type)) return "الصداقة";
        if ("brotherhood".equalsIgnoreCase(type)) return "الأخوّة";
        if ("engagement".equalsIgnoreCase(type)) return "الخطوبة";
        if ("marriage".equalsIgnoreCase(type)) return "الزواج";
        return type == null || "null".equals(type) || type.isEmpty() ? "علاقة CP" : type;
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("JehoApp");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private final class PageBridge {
        @JavascriptInterface public void refreshData() { runOnUiThread(ProfileInsightsActivity.this::loadLiveData); }
        @JavascriptInterface public void goBack() { runOnUiThread(ProfileInsightsActivity.this::finish); }
        @JavascriptInterface public void openWealthRanking() {
            runOnUiThread(() -> {
                Intent i = new Intent(ProfileInsightsActivity.this, RankingActivity.class);
                i.putExtra(RankingActivity.EXTRA_CATEGORY, "rich");
                startActivity(i);
            });
        }
        @JavascriptInterface public void openCpCenter() {
            runOnUiThread(() -> startActivity(new Intent(ProfileInsightsActivity.this, CpCenterActivity.class)));
        }
    }
}
