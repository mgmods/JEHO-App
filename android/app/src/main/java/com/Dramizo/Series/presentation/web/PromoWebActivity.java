package com.Dramizo.Series.presentation.web;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.notifications.OfficialNewsAdapter;
import com.Dramizo.Series.util.AssetCatalog;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.Dramizo.Series.R;

public class PromoWebActivity extends ThemedActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    /** Raw HTML document (official news branded cards). */
    public static final String EXTRA_HTML = "html";
    /** When true, skip legal-page CSS overrides so designs keep their colors. */
    public static final String EXTRA_OFFICIAL = "official";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promo_web);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String html = getIntent().getStringExtra(EXTRA_HTML);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        boolean official = getIntent().getBooleanExtra(EXTRA_OFFICIAL, false)
                || (html != null && !html.isEmpty());
        TextView tv = findViewById(R.id.tvTitle);
        ImageView back = findViewById(R.id.btnBack);
        WebView web = findViewById(R.id.webView);
        tv.setText(title != null && !title.isEmpty() ? title : getString(R.string.app_name));
        back.setOnClickListener(v -> navigateUp());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setDefaultFontSize(16);
        s.setMinimumFontSize(14);
        s.setTextZoom(official ? 100 : 110);
        s.setDefaultTextEncodingName("utf-8");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String next = request.getUrl() != null ? request.getUrl().toString() : "";
                if (next.contains("/admin") || next.startsWith("auralive://admin")) {
                    Toast.makeText(PromoWebActivity.this, "ارجع للتطبيق للمتابعة", Toast.LENGTH_SHORT).show();
                    finish();
                    return true;
                }
                if (OfficialNewsAdapter.handleHtmlNavigation(PromoWebActivity.this, next)) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (official) {
                    // Wire CTAs; keep original CSS for news cards.
                    view.evaluateJavascript(
                            "(function(){try{"
                                    + "window.openRecharge=function(){location.href='jeho://recharge';};"
                                    + "var btns=document.querySelectorAll('button.cta,.cta,a.cta,button');"
                                    + "for(var i=0;i<btns.length;i++){"
                                    + " var el=btns[i];"
                                    + " if(!el.getAttribute('data-jeho-bound')){"
                                    + "  el.setAttribute('data-jeho-bound','1');"
                                    + "  el.addEventListener('click',function(e){"
                                    + "   e.preventDefault();"
                                    + "   var href=(this.getAttribute('href')||'').trim();"
                                    + "   if(href&&href.indexOf('http')===0){location.href=href;return;}"
                                    + "   location.href='jeho://recharge';"
                                    + "  });"
                                    + " }"
                                    + "}"
                                    + "}catch(e){}})();",
                            null);
                    return;
                }
                // Force readable mobile typography for legal pages.
                view.evaluateJavascript(
                        "(function(){var s=document.createElement('style');"
                                + "s.innerHTML='body{max-width:100%!important;padding:16px!important;"
                                + "font-size:16px!important;line-height:1.7!important;"
                                + "color:#222!important;background:#fff!important;}"
                                + ".navbar,.glowing-orb,.orb-1,.orb-2{display:none!important;}"
                                + ".glass-panel,.container{padding:0!important;margin:0!important;"
                                + "box-shadow:none!important;border:none!important;backdrop-filter:none!important;}"
                                + "h1{font-size:22px!important;}h2{font-size:18px!important;}"
                                + "p,li{font-size:15px!important;}';"
                                + "document.head.appendChild(s);})();",
                        null);
            }
        });
        if (html != null && !html.isEmpty()) {
            web.loadDataWithBaseURL(
                    AssetCatalog.absoluteUrl("/"),
                    OfficialNewsAdapter.ensureHtmlDocument(html),
                    "text/html",
                    "UTF-8",
                    null);
        } else if (url != null && !url.isEmpty()) {
            web.loadUrl(url);
        } else {
            web.loadUrl(com.Dramizo.Series.util.ApiOrigin.origin() + "/legal/terms.html");
        }
    }
}
