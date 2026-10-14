package com.Dramizo.Series.presentation.ranking;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class WealthRankingHtmlActivity extends ThemedActivity {
    private WebView web;
    private RankingViewModel viewModel;
    private final Gson gson = new Gson();
    private boolean pageReady;
    private String period = "daily";
    private String category = "rich";

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF100E20);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
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
                loadRanking();
            }
        });
        web.addJavascriptInterface(new RankingBridge(), "JehoRanking");
        setContentView(web);
        viewModel = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this))).get(RankingViewModel.class);
        viewModel.getEntries().observe(this, entries -> {
            if (!pageReady || entries == null) return;
            String json = gson.toJson(entries);
            web.evaluateJavascript("window.renderJehoRankings(" + JSONObjectQuote(json) + ")", null);
        });
        viewModel.getError().observe(this, error -> {
            if (pageReady && error != null) {
                web.evaluateJavascript("window.showJehoRankingError(" + JSONObjectQuote(error) + ")", null);
            }
        });
        web.loadUrl("file:///android_asset/wealth-ranking.html");
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
        for (String allowed : new String[]{"rich", "popular", "gifts", "host", "agency", "room"}) {
            if (allowed.equals(value)) return value;
        }
        return "rich";
    }

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