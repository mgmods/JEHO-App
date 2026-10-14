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
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.cp.CpCenterActivity;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HTML presentation backed only by authenticated app API responses. */
public final class ProfileInsightsActivity extends ThemedActivity {
    public static final String EXTRA_MODE = "profile_insights_mode";
    public static final String MODE_CP = "cp";
    public static final String MODE_WEALTH = "wealth";
    private WebView webView;
    private AppContainer container;
    private boolean pageReady;
    private volatile boolean destroyed;
    private boolean dataLoadInFlight;
    private boolean dataLoadPending;
    private String pendingPeriod = "daily";
    private String pendingCategory = "rich";
    private String mode = MODE_WEALTH;
    private volatile String wealthPeriod = "daily";
    private volatile String wealthCategory = "rich";

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = ContainerProvider.from(this);
        String requestedMode = getIntent().getStringExtra(EXTRA_MODE);
        mode = MODE_CP.equals(requestedMode) ? MODE_CP : MODE_WEALTH;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 16, 32));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setBackgroundColor(Color.rgb(16, 16, 32));
        webView.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
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
        // Keep the HTML page inside the Android status/navigation safe areas.
        // Navigation is handled by the native device back gesture/button.
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(root);
        webView.loadUrl("file:///android_asset/" + (MODE_CP.equals(mode) ? "cp-insights.html" : "wealth-insights.html"));
    }

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override protected void onResume() {
        super.onResume();
        if (pageReady) loadLiveData();
    }

    private void loadLiveData() {
        loadLiveData(wealthPeriod, wealthCategory);
    }

    private void loadLiveData(String requestedPeriod) {
        loadLiveData(requestedPeriod, wealthCategory);
    }

    private void loadLiveData(String requestedPeriod, String requestedCategory) {
        if (container == null || destroyed) return;
        final String period = "weekly".equals(requestedPeriod) || "monthly".equals(requestedPeriod)
                ? requestedPeriod : "daily";
        String category = MODE_WEALTH.equals(mode) && isRankingCategory(requestedCategory) ? requestedCategory : "rich";
        if (MODE_WEALTH.equals(mode)) { wealthPeriod = period; wealthCategory = category; }
        synchronized (this) {
            if (destroyed) return;
            if (dataLoadInFlight) {
                dataLoadPending = true;
                pendingPeriod = period;
                pendingCategory = category;
                return;
            }
            dataLoadInFlight = true;
        }
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> userResult = ApiCall.execute(container.getUserApi().me());
            Result<List<Map<String, Object>>> relationResult = ApiCall.execute(container.getUserApi().myCpRelationships());
            Result<List<Map<String, Object>>> cpRankingResult = MODE_CP.equals(mode)
                    ? ApiCall.execute(container.getUserApi().discoverCp(null)) : null;
            Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> rankingResult =
                    container.getRankingsUseCase.execute(MODE_WEALTH.equals(mode) ? period : "daily", category);
            JSONObject payload = new JSONObject();
            try {
                if (userResult.success && userResult.data != null) {
                    AuthDtos.UserDto u = userResult.data;
                    JSONObject user = new JSONObject();
                    put(user, "id", u.id); put(user, "publicId", u.publicId);
                    put(user, "username", u.username); put(user, "displayName", u.displayName);
                    put(user, "avatarUrl", AssetCatalog.absoluteUrl(u.avatarUrl)); put(user, "vipBadgeUrl", AssetCatalog.absoluteUrl(u.vipBadgeUrl)); put(user, "hostBadgeUrl", AssetCatalog.absoluteUrl(u.hostBadgeUrl)); put(user, "frameUrl", absoluteIfPresent(firstNonBlank(u.hostBadgeUrl, u.vipBadgeUrl))); put(user, "levelBadgeUrl", u.levelBadgeUrl); put(user, "level", u.level);
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
                        put(partner, "vipBadgeUrl", p.get("vipBadgeUrl")); put(partner, "hostBadgeUrl", p.get("hostBadgeUrl")); put(partner, "frameUrl", firstNonBlank(p.get("frameUrl"), p.get("hostBadgeUrl"), p.get("vipBadgeUrl"))); put(partner, "levelBadgeUrl", p.get("levelBadgeUrl"));
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
                payload.put("relationshipError", !relationResult.success);
                JSONArray ranks = new JSONArray();
                if (rankingResult.success && rankingResult.data != null && rankingResult.data.items != null) {
                    for (MiscDtos.RankingEntryDto e : rankingResult.data.items) {
                        if (e == null) continue;
                        JSONObject item = new JSONObject();
                        put(item, "rank", e.rank); put(item, "score", e.score); put(item, "targetName", e.targetName);
                        if (e.user != null) {
                            JSONObject ru = new JSONObject();
                            put(ru, "id", e.user.id); put(ru, "username", e.user.username);
                            put(ru, "displayName", e.user.displayName); put(ru, "avatarUrl", e.user.avatarUrl); put(ru, "frameUrl", firstNonBlank(e.user.hostBadgeUrl, e.user.vipBadgeUrl)); put(ru, "hostBadgeUrl", e.user.hostBadgeUrl); put(ru, "vipBadgeUrl", e.user.vipBadgeUrl);
                            item.put("user", ru);
                        }
                        ranks.put(item);
                    }
                }
                payload.put("ranking", ranks);
                payload.put("rankingError", !rankingResult.success);
                payload.put("rankingPeriod", MODE_WEALTH.equals(mode) ? period : "daily");
                payload.put("rankingCategory", category);
                if (MODE_CP.equals(mode)) {
                    JSONArray cpRanks = new JSONArray();
                    if (cpRankingResult != null && cpRankingResult.success && cpRankingResult.data != null) {
                        int cpRank = 1;
                        for (Map<String, Object> row : cpRankingResult.data) {
                            if (cpRank > 30) break;
                            if (row == null) continue;
                            JSONObject item = new JSONObject();
                            put(item, "rank", cpRank++);
                            put(item, "id", row.get("id")); put(item, "type", row.get("type"));
                            put(item, "level", row.get("level")); put(item, "points", row.get("points"));
                            put(item, "durationDays", row.get("durationDays"));
                            Map<?, ?> a = row.get("userA") instanceof Map ? (Map<?, ?>) row.get("userA") : null;
                            Map<?, ?> b = row.get("userB") instanceof Map ? (Map<?, ?>) row.get("userB") : null;
                            if (a != null) {
                                JSONObject ua = new JSONObject();
                                put(ua, "id", a.get("id")); put(ua, "username", a.get("username"));
                                put(ua, "displayName", a.get("displayName")); put(ua, "avatarUrl", a.get("avatarUrl"));
                                put(ua, "vipBadgeUrl", a.get("vipBadgeUrl")); put(ua, "hostBadgeUrl", a.get("hostBadgeUrl")); put(ua, "frameUrl", firstNonBlank(a.get("frameUrl"), a.get("hostBadgeUrl"), a.get("vipBadgeUrl"))); put(ua, "levelBadgeUrl", a.get("levelBadgeUrl"));
                                put(ua, "level", a.get("level"));
                                item.put("userA", ua);
                            }
                            if (b != null) {
                                JSONObject ub = new JSONObject();
                                put(ub, "id", b.get("id")); put(ub, "username", b.get("username"));
                                put(ub, "displayName", b.get("displayName")); put(ub, "avatarUrl", b.get("avatarUrl"));
                                put(ub, "vipBadgeUrl", b.get("vipBadgeUrl")); put(ub, "hostBadgeUrl", b.get("hostBadgeUrl")); put(ub, "frameUrl", firstNonBlank(b.get("frameUrl"), b.get("hostBadgeUrl"), b.get("vipBadgeUrl"))); put(ub, "levelBadgeUrl", b.get("levelBadgeUrl"));
                                put(ub, "level", b.get("level"));
                                item.put("userB", ub);
                            }
                            cpRanks.put(item);
                        }
                    }
                    payload.put("cpRanking", cpRanks);
                    payload.put("cpRankingError", cpRankingResult == null || !cpRankingResult.success);
                }
            } catch (Exception ignored) { }
            final String json = payload.toString();
            runOnUiThread(() -> {
                if (destroyed || webView == null || isFinishing() || isDestroyed()) return;
                String renderFunction = MODE_CP.equals(mode) ? "renderCpData" : "renderWealthData";
                String errorFunction = MODE_CP.equals(mode) ? "showCpError" : "showWealthError";
                webView.evaluateJavascript("window." + renderFunction + " && window." + renderFunction + "(" + json + ");", null);
                if (!userResult.success) webView.evaluateJavascript("window." + errorFunction + " && window." + errorFunction + "();", null);
                String nextPeriod = null;
                String nextCategory = "rich";
                synchronized (ProfileInsightsActivity.this) {
                    dataLoadInFlight = false;
                    if (dataLoadPending && !destroyed) { nextPeriod = pendingPeriod; nextCategory = pendingCategory; }
                    dataLoadPending = false;
                }
                if (nextPeriod != null) loadLiveData(nextPeriod, nextCategory);
            });
        });
    }

    private static boolean isRankingCategory(String value) {
        return "rich".equals(value) || "popular".equals(value) || "gifts".equals(value)
                || "host".equals(value) || "agency".equals(value) || "room".equals(value);
    }

    private static String absoluteIfPresent(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        return AssetCatalog.absoluteUrl(String.valueOf(value).trim());
    }

    private static Object firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) return value;
        }
        return null;
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
        destroyed = true;
        synchronized (this) { dataLoadPending = false; }
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
                if (MODE_WEALTH.equals(mode) && !destroyed) {
                    startActivity(new Intent(ProfileInsightsActivity.this,
                            com.Dramizo.Series.presentation.ranking.WealthRankingHtmlActivity.class));
                }
            });
        }
        @JavascriptInterface public void loadWealthPeriod(String period) {
            if (!"daily".equals(period) && !"weekly".equals(period) && !"monthly".equals(period)) return;
            runOnUiThread(() -> loadLiveData(period, wealthCategory));
        }
        @JavascriptInterface public void loadWealthCategory(String category) {
            if (!isRankingCategory(category)) return;
            runOnUiThread(() -> loadLiveData(wealthPeriod, category));
        }
        @JavascriptInterface public void openCpCenter() {
            runOnUiThread(() -> startActivity(new Intent(ProfileInsightsActivity.this, CpCenterActivity.class)));
        }
        @JavascriptInterface public void openCpDiscovery() {
            runOnUiThread(() -> startActivity(new Intent(ProfileInsightsActivity.this, com.Dramizo.Series.presentation.cp.CpDiscoveryActivity.class)));
        }
        @JavascriptInterface public void openUserProfile(String userId) {
            if (userId == null || userId.trim().isEmpty()) return;
            runOnUiThread(() -> com.Dramizo.Series.presentation.common.UserProfileCardSheet.show(ProfileInsightsActivity.this, userId));
        }
    }
}
