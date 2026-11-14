package com.Dramizo.Series.presentation.ranking;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AssetCatalog;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class WealthRankingHtmlActivity extends ThemedActivity {
    public static final String EXTRA_PERIOD = "wealth_ranking_period";
    public static final String EXTRA_CATEGORY = "wealth_ranking_category";
    private WebView web;
    private RankingViewModel viewModel;
    private final Gson gson = new Gson();
    private boolean pageReady;
    private String period = "daily";
    private String category = "rich";

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        period = safePeriod(getIntent().getStringExtra(EXTRA_PERIOD));
        category = safeCategory(getIntent().getStringExtra(EXTRA_CATEGORY));
        web = new WebView(this);
        web.setBackgroundColor(0xFF100E20);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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
                web.evaluateJavascript("window.setInitialRanking && window.setInitialRanking(" + gson.toJson(period) + "," + gson.toJson(category) + ")", null);
                loadRanking();
            }
        });
        web.addJavascriptInterface(new RankingBridge(), "JehoRanking");
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(root);
        viewModel = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this))).get(RankingViewModel.class);
        viewModel.getEntries().observe(this, entries -> {
            if (!pageReady || entries == null) return;
            List<java.util.Map<String, Object>> safeEntries = new ArrayList<>();
            for (MiscDtos.RankingEntryDto entry : entries) {
                if (entry == null) continue;
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("rank", entry.rank);
                row.put("userId", entry.userId);
                row.put("targetId", entry.targetId);
                row.put("targetName", entry.targetName);
                row.put("score", entry.score);
                java.util.Map<String, Object> user = new java.util.LinkedHashMap<>();
                if (entry.user != null) {
                    user.put("id", entry.user.id);
                    user.put("username", entry.user.username);
                    user.put("displayName", entry.user.displayName);
                    user.put("avatarUrl", webImageUrl(entry.user.avatarUrl));
                    user.put("hostBadgeUrl", webImageUrl(entry.user.hostBadgeUrl));
                    user.put("vipBadgeUrl", webImageUrl(entry.user.vipBadgeUrl));
                    user.put("frameUrl", webImageUrl(entry.user.hostBadgeUrl != null && !entry.user.hostBadgeUrl.trim().isEmpty() ? entry.user.hostBadgeUrl : entry.user.vipBadgeUrl));
                }
                row.put("user", user);
                safeEntries.add(row);
            }
            String json = gson.toJson(safeEntries);
            web.evaluateJavascript("window.renderJehoRankings(" + JSONObjectQuote(json) + ")", null);
        });
        viewModel.getError().observe(this, error -> {
            if (pageReady && error != null) {
                web.evaluateJavascript("window.showJehoRankingError(" + JSONObjectQuote(error) + ")", null);
            }
        });
        web.loadUrl("file:///android_asset/wealth-ranking.html");
    }

    private static String webImageUrl(String pathOrUrl) {
        String resolved = AssetCatalog.absoluteUrl(pathOrUrl);
        if (resolved == null) return null;
        if (resolved.startsWith("http://")) return "https://" + resolved.substring("http://".length());
        return resolved;
    }

    private void loadRanking() {
        if (viewModel != null) viewModel.load(period, category);
    }

    private String JSONObjectQuote(String value) {
        return gson.toJson(value == null ? "" : value);
    }

    private final class RankingBridge {
        @JavascriptInterface public void load(String nextPeriod, String nextCategory) {
            runOnUiThread(() -> {
                period = safePeriod(nextPeriod);
                category = safeCategory(nextCategory);
                loadRanking();
            });
        }
        @JavascriptInterface public void openProfile(String userId) {
            runOnUiThread(() -> {
                if (userId != null && !userId.trim().isEmpty()) UserProfileCardSheet.show(WealthRankingHtmlActivity.this, userId);
            });
        }
        @JavascriptInterface public void back() {
            runOnUiThread(WealthRankingHtmlActivity.this::finish);
        }
    }

    private String safePeriod(String value) {
        return "weekly".equals(value) || "monthly".equals(value) ? value : "daily";
    }
    private String safeCategory(String value) {
        for (String allowed : new String[]{"popular", "gifts", "host", "agency", "room"}) {
            if (allowed.equals(value)) return value;
        }
        return "popular";
    }

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override public void onBackPressed() {
        finish();
    }

    @Override protected void onDestroy() {
        pageReady = false;
        if (web != null) {
            web.removeJavascriptInterface("JehoRanking");
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}