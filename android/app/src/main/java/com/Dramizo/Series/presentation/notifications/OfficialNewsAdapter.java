package com.Dramizo.Series.presentation.notifications;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.core.text.util.LinkifyCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.presentation.web.PromoWebActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.BalanceRedirect;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official News:
 * · TYPE_HTML — full designed page rendered **inside** the feed row (system page in thread).
 * · TYPE_TEXT — classic chat bubble for plain / agency notice text.
 */
public class OfficialNewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_TEXT = 0;
    private static final int TYPE_HTML = 1;

    private static final Pattern ACTIVATION_CODE = Pattern.compile(
            "(?:كود\\s*(?:التفعيل|تفعيل)?\\s*[:：]?\\s*|activation\\s*code\\s*[:：]?\\s*)?"
                    + "([A-HJ-NP-Z2-9]{8})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern URL_IN_TEXT = Pattern.compile(
            "(https?://[^\\s<>\"']+)|(www\\.[^\\s<>\"']+)",
            Pattern.CASE_INSENSITIVE);

    private final List<MiscDtos.NotificationDto> items = new ArrayList<>();
    private final SimpleDateFormat iso =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat out =
            new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public OfficialNewsAdapter() {
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void submit(List<MiscDtos.NotificationDto> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        MiscDtos.NotificationDto n = items.get(position);
        String html = htmlFrom(n);
        return isRichHtmlPage(html) ? TYPE_HTML : TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HTML) {
            return new HtmlVH(inf.inflate(R.layout.item_official_news_html, parent, false));
        }
        return new TextVH(inf.inflate(R.layout.item_official_news_bubble, parent, false));
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof HtmlVH) {
            clearWeb((HtmlVH) holder);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MiscDtos.NotificationDto n = items.get(position);
        if (holder instanceof HtmlVH) {
            bindHtml((HtmlVH) holder, n);
        } else if (holder instanceof TextVH) {
            bindText((TextVH) holder, n);
        }
    }

    private void bindHtml(@NonNull HtmlVH h, @NonNull MiscDtos.NotificationDto n) {
        Context ctx = h.itemView.getContext();
        String title = n.title != null ? n.title : "";
        String html = htmlFrom(n);
        h.tvTime.setText(formatTime(n.createdAt));
        loadHtmlInBubble(h, html, title);

        String copyPayload = HtmlCompat.fromHtml(
                html != null ? html : "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString();
        h.itemView.setOnLongClickListener(v -> {
            copyText(ctx, copyPayload, false);
            return true;
        });
    }

    private void bindText(@NonNull TextVH h, @NonNull MiscDtos.NotificationDto n) {
        Context ctx = h.itemView.getContext();
        String title = n.title != null ? n.title : "";
        String body = n.body != null ? n.body : "";
        h.tvTitle.setText(title);
        h.tvTime.setText(formatTime(n.createdAt));

        String typeNorm = n.type != null ? n.type.trim().toLowerCase(Locale.US) : "";
        boolean isAgency = "agency".equals(typeNorm);
        boolean isSystemBroadcast = "system".equals(typeNorm)
                || "admin".equals(typeNorm)
                || "announcement".equals(typeNorm);
        String codeFromData = null;
        if (isAgency && !isSystemBroadcast) {
            codeFromData = firstString(n.data,
                    "activationCode", "activation_code", "agencyCode", "agency_code");
            if (TextUtils.isEmpty(codeFromData)) {
                codeFromData = extractActivationCode(body);
            }
        }
        String htmlFromData = htmlFrom(n);
        String imageFromData = firstString(n.data, "imageUrl", "image", "bannerUrl", "coverUrl");
        String linkFromData = firstString(n.data, "url", "link", "href", "webUrl");
        if (TextUtils.isEmpty(linkFromData)) {
            linkFromData = extractFirstUrl(!TextUtils.isEmpty(htmlFromData) ? htmlFromData : body);
        }

        bindPromoImage(h.imgMedia, imageFromData);
        if (!TextUtils.isEmpty(htmlFromData)) {
            bindHtmlBody(h.tvBody, htmlFromData, isAgency ? codeFromData : null);
        } else {
            bindRichBody(h.tvBody, body, isAgency ? codeFromData : null);
        }

        boolean hasCode = isAgency && !TextUtils.isEmpty(codeFromData);
        boolean hasLink = !TextUtils.isEmpty(linkFromData);
        h.rowActions.setVisibility(hasCode || hasLink ? View.VISIBLE : View.GONE);

        if (hasCode) {
            final String code = codeFromData;
            h.btnCopyCode.setVisibility(View.VISIBLE);
            h.btnCopyCode.setOnClickListener(v -> copyText(ctx, code, true));
        } else {
            h.btnCopyCode.setVisibility(View.GONE);
            h.btnCopyCode.setOnClickListener(null);
        }

        if (hasLink) {
            final String link = normalizeUrl(linkFromData);
            h.btnOpenLink.setVisibility(View.VISIBLE);
            h.btnOpenLink.setOnClickListener(v -> openLink(ctx, link, title));
        } else {
            h.btnOpenLink.setVisibility(View.GONE);
            h.btnOpenLink.setOnClickListener(null);
        }

        String copyPayload = !TextUtils.isEmpty(htmlFromData)
                ? HtmlCompat.fromHtml(htmlFromData, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                : (TextUtils.isEmpty(body) ? title : body);
        h.itemView.setOnLongClickListener(v -> {
            copyText(ctx, copyPayload, false);
            return true;
        });
        h.tvBody.setOnLongClickListener(v -> {
            copyText(ctx, copyPayload, false);
            return true;
        });
        if (h.imgMedia != null) {
            final String fullImage = AssetCatalog.absoluteUrl(imageFromData);
            h.imgMedia.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(fullImage)) openLink(ctx, fullImage, title);
            });
        }
    }

    @Nullable
    private static String htmlFrom(@Nullable MiscDtos.NotificationDto n) {
        if (n == null) return null;
        return firstString(n.data, "html", "bodyHtml", "richHtml");
    }

    /**
     * Any dashboard HTML card (DOCTYPE / style / layout classes) → system page row.
     * Short plain tags only stay as text bubble.
     */
    public static boolean isRichHtmlPage(@Nullable String html) {
        if (TextUtils.isEmpty(html)) return false;
        String h = html.toLowerCase(Locale.US);
        if (h.contains("<style") || h.contains("<!doctype") || h.contains("<html")) return true;
        if (h.contains("class=") && (h.contains("<div") || h.contains("<section"))) return true;
        if (h.contains("<div") && html.length() > 400) return true;
        return html.length() > 800 && (h.contains("<div") || h.contains("<table"));
    }

    /** Page painted inside the news feed row — full width, height = HTML content. */
    @SuppressLint("SetJavaScriptEnabled")
    private void loadHtmlInBubble(@NonNull HtmlVH h, @Nullable String html, @Nullable String title) {
        WebView web = h.web;
        if (web == null || h.host == null || TextUtils.isEmpty(html)) return;
        h.host.setVisibility(View.VISIBLE);
        h.pendingHtml = html;
        h.lastHeightPx = 0;
        // Start tiny until measure — no fixed 480 black box.
        applyWebHeight(h, 1);
        web.setTag(R.id.webBubbleHtml, h);

        if (!h.webConfigured) {
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(false);
            s.setLoadWithOverviewMode(false);
            s.setUseWideViewPort(true);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setDefaultTextEncodingName("utf-8");
            s.setMediaPlaybackRequiresUserGesture(true);
            web.setBackgroundColor(Color.TRANSPARENT);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String next = request.getUrl() != null ? request.getUrl().toString() : "";
                    return handleHtmlNavigation(view.getContext(), next);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    Object tag = view.getTag(R.id.webBubbleHtml);
                    if (!(tag instanceof HtmlVH)) return;
                    HtmlVH vh = (HtmlVH) tag;
                    injectOfferHelpers(view);
                    injectFitCss(view);
                    scheduleResize(vh, view, 16);
                    scheduleResize(vh, view, 150);
                    scheduleResize(vh, view, 400);
                }
            });
            h.webConfigured = true;
        }
        String page = ensureHtmlDocument(html);
        // Load after we know the host width so viewport matches feed column.
        h.host.post(() -> {
            if (h.web == null) return;
            if (h.web.getWidth() <= 0 && h.host.getWidth() > 0) {
                ViewGroup.LayoutParams lp = h.web.getLayoutParams();
                if (lp != null) {
                    lp.width = h.host.getWidth();
                    h.web.setLayoutParams(lp);
                }
            }
            h.web.loadDataWithBaseURL(
                    AssetCatalog.absoluteUrl("/"),
                    page,
                    "text/html",
                    "UTF-8",
                    null);
        });
    }

    private static void scheduleResize(@NonNull HtmlVH h, @NonNull WebView view, long delayMs) {
        view.postDelayed(() -> {
            if (h.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
            resizeWebToContent(h, view);
        }, delayMs);
    }

    /**
     * Set WebView height to exact HTML document height (CSS px × density).
     * No fixed 480dp black box — the card is only as tall as the offer.
     */
    private static void resizeWebToContent(@NonNull HtmlVH h, @NonNull WebView view) {
        // Prefer the designed card (.offer / .wrap); fall back to full body.
        String js =
                "(function(){"
                        + "try{"
                        + "var s=document.createElement('style');"
                        + "s.setAttribute('data-jeho-fit','1');"
                        + "if(!document.querySelector('style[data-jeho-fit]')){"
                        + "s.textContent='html,body{margin:0!important;padding:0!important;"
                        + "width:100%!important;height:auto!important;min-height:0!important;"
                        + "background:transparent!important;overflow:hidden!important;}"
                        + ".wrap{max-width:100%!important;width:100%!important;margin:0 auto!important;"
                        + "padding:8px!important;box-sizing:border-box!important;}"
                        + "img,video{max-width:100%!important;height:auto!important;}';"
                        + "document.head.appendChild(s);"
                        + "}"
                        + "var el=document.querySelector('.offer')"
                        + "||document.querySelector('.wrap')"
                        + "||document.body;"
                        + "if(!el)return 0;"
                        + "var r=el.getBoundingClientRect();"
                        + "var h=Math.max("
                        + "r.height||0,"
                        + "el.scrollHeight||0,"
                        + "el.offsetHeight||0,"
                        + "document.body?document.body.scrollHeight:0,"
                        + "document.documentElement?document.documentElement.scrollHeight:0"
                        + ");"
                        + "return Math.ceil(h);"
                        + "}catch(e){return 0;}"
                        + "})();";
        view.evaluateJavascript(js, value -> {
            try {
                if (value == null || "null".equals(value)) return;
                String raw = value.replace("\"", "").trim();
                double cssPx = Double.parseDouble(raw);
                if (cssPx < 8) return;
                /*
                 * Chromium on Android: scrollHeight/getBoundingClientRect are in CSS px for the
                 * layout viewport. LayoutParams need device pixels → × density.
                 * Prefer slightly open height (+2dp) so the card bottom border is not clipped.
                 */
                float density = view.getResources().getDisplayMetrics().density;
                int hPx = (int) Math.ceil(cssPx * density + density * 2f);
                int minPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 80f,
                        view.getResources().getDisplayMetrics());
                int maxPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 1600f,
                        view.getResources().getDisplayMetrics());
                hPx = Math.max(minPx, Math.min(maxPx, hPx));
                if (Math.abs(hPx - h.lastHeightPx) < Math.max(4, density)) return;
                h.lastHeightPx = hPx;
                applyWebHeight(h, hPx);
            } catch (Exception ignored) {
            }
        });
    }

    private static void applyWebHeight(@NonNull HtmlVH h, int heightPx) {
        WebView web = h.web;
        if (web == null) return;
        ViewGroup.LayoutParams lp = web.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = heightPx;
        }
        web.setLayoutParams(lp);
        if (h.host != null) {
            ViewGroup.LayoutParams hl = h.host.getLayoutParams();
            if (hl == null) {
                hl = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                hl.width = ViewGroup.LayoutParams.MATCH_PARENT;
                hl.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            h.host.setLayoutParams(hl);
            h.host.requestLayout();
        }
        web.requestLayout();
    }

    private static void injectFitCss(WebView view) {
        try {
            view.evaluateJavascript(
                    "(function(){"
                            + "var s=document.createElement('style');"
                            + "s.textContent='html,body{margin:0!important;padding:0!important;"
                            + "width:100%!important;height:auto!important;min-height:0!important;"
                            + "background:transparent!important;overflow:hidden!important;}"
                            + ".wrap{max-width:100%!important;width:100%!important;margin:0 auto!important;"
                            + "padding:8px!important;box-sizing:border-box!important;}';"
                            + "document.head.appendChild(s);"
                            + "})();",
                    null);
        } catch (Exception ignored) {
        }
    }

    private static void clearWeb(@NonNull HtmlVH h) {
        WebView web = h.web;
        h.lastHeightPx = 0;
        h.pendingHtml = null;
        if (web == null) return;
        try {
            web.stopLoading();
            web.loadUrl("about:blank");
        } catch (Exception ignored) {
        }
    }

    /**
     * Guarantee viewport meta + transparent full-width body so the offer fills the feed column
     * and height measurement matches visible card (no black padding).
     */
    public static String ensureHtmlDocument(String html) {
        String h = html == null ? "" : html.trim();
        String fitHead =
                "<meta charset=\"UTF-8\">"
                        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,"
                        + "maximum-scale=1,user-scalable=no\">"
                        + "<style id=\"jeho-fit\">"
                        + "html,body{margin:0!important;padding:0!important;width:100%!important;"
                        + "height:auto!important;min-height:0!important;"
                        + "background:transparent!important;overflow-x:hidden!important;}"
                        + ".wrap{max-width:100%!important;width:100%!important;margin:0 auto!important;"
                        + "padding:8px!important;box-sizing:border-box!important;}"
                        + "img,video{max-width:100%!important;height:auto!important;}"
                        + "</style>";
        String low = h.toLowerCase(Locale.US);
        if (low.contains("<html") || low.contains("<!doctype")) {
            // Inject fit CSS into existing head/body document.
            if (low.contains("<head")) {
                return h.replaceFirst("(?i)<head([^>]*)>", "<head$1>" + fitHead);
            }
            return h.replaceFirst("(?i)<html([^>]*)>", "<html$1><head>" + fitHead + "</head>");
        }
        return "<!DOCTYPE html><html dir=\"rtl\" lang=\"ar\"><head>"
                + fitHead
                + "</head><body>" + h + "</body></html>";
    }

    private static void injectOfferHelpers(WebView view) {
        String js = "(function(){"
                + "try{"
                + "window.openRecharge=function(){location.href='jeho://recharge';};"
                + "var btns=document.querySelectorAll('button.cta,.cta,a.cta,button');"
                + "for(var i=0;i<btns.length;i++){"
                + "  var el=btns[i];"
                + "  if(!el.getAttribute('data-jeho-bound')){"
                + "    el.setAttribute('data-jeho-bound','1');"
                + "    el.addEventListener('click',function(e){"
                + "      e.preventDefault();"
                + "      var href=(this.getAttribute('href')||'').trim();"
                + "      if(href&&href.indexOf('http')===0){location.href=href;return;}"
                + "      location.href='jeho://recharge';"
                + "    });"
                + "  }"
                + "}"
                + "}catch(e){}"
                + "})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /** @return true if handled (do not load in WebView). */
    public static boolean handleHtmlNavigation(Context ctx, @Nullable String url) {
        if (TextUtils.isEmpty(url)) return true;
        String u = url.trim();
        String low = u.toLowerCase(Locale.US);
        if ("about:blank".equals(low) || low.startsWith("data:")) return false;
        if (low.startsWith("jeho://recharge")
                || low.startsWith("auralive://recharge")
                || low.contains("/recharge")
                || low.endsWith("recharge")
                || low.contains("wallet/recharge")) {
            Activity act = findActivity(ctx);
            BalanceRedirect.openRecharge(act);
            return true;
        }
        if (low.startsWith("http://") || low.startsWith("https://")) {
            openLink(ctx, u, null);
            return true;
        }
        if (low.startsWith("jeho://") || low.startsWith("auralive://")) {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
            } catch (Exception e) {
                Toast.makeText(ctx, R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    @Nullable
    private static Activity findActivity(Context ctx) {
        Context c = ctx;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private void bindPromoImage(@Nullable ShapeableImageView img, @Nullable String imageUrl) {
        if (img == null) return;
        if (TextUtils.isEmpty(imageUrl)) {
            img.setVisibility(View.GONE);
            img.setImageDrawable(null);
            img.setOnClickListener(null);
            return;
        }
        img.setVisibility(View.VISIBLE);
        try {
            Glide.with(img)
                    .load(AssetCatalog.absoluteUrl(imageUrl))
                    .centerCrop()
                    .into(img);
        } catch (Exception e) {
            img.setVisibility(View.GONE);
        }
    }

    private void bindHtmlBody(
            TextView tv, String html, @Nullable String knownCode) {
        if (TextUtils.isEmpty(html)) {
            tv.setText("");
            return;
        }
        Spanned spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT);
        SpannableStringBuilder span = new SpannableStringBuilder(spanned);

        URLSpan[] urls = span.getSpans(0, span.length(), URLSpan.class);
        if (urls != null) {
            for (URLSpan urlSpan : urls) {
                int start = span.getSpanStart(urlSpan);
                int end = span.getSpanEnd(urlSpan);
                String url = urlSpan.getURL();
                span.removeSpan(urlSpan);
                if (start < 0 || end <= start) continue;
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openLink(widget.getContext(), normalizeUrl(url), null);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(0xFF1565C0);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (!TextUtils.isEmpty(knownCode)) {
            String plain = span.toString();
            Matcher m = ACTIVATION_CODE.matcher(plain);
            while (m.find()) {
                String code = m.group(1);
                if (code == null || !knownCode.equalsIgnoreCase(code)) continue;
                int start = m.start(1);
                int end = m.end(1);
                if (hasClickableOverlap(span, start, end)) continue;
                final String copyCode = code.toUpperCase(Locale.US);
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        copyText(widget.getContext(), copyCode, true);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(0xFFB8860B);
                        ds.setFakeBoldText(true);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        tv.setText(span);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
    }

    private void bindRichBody(TextView tv, String body, @Nullable String knownCode) {
        if (TextUtils.isEmpty(body)) {
            tv.setText("");
            return;
        }
        SpannableString span = new SpannableString(body);
        LinkifyCompat.addLinks(span, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);

        android.text.style.URLSpan[] urls = span.getSpans(0, span.length(), android.text.style.URLSpan.class);
        if (urls != null) {
            for (android.text.style.URLSpan urlSpan : urls) {
                int start = span.getSpanStart(urlSpan);
                int end = span.getSpanEnd(urlSpan);
                String url = urlSpan.getURL();
                span.removeSpan(urlSpan);
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openLink(widget.getContext(), normalizeUrl(url), null);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(0xFF1565C0);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (!TextUtils.isEmpty(knownCode)) {
            Matcher m = ACTIVATION_CODE.matcher(body);
            while (m.find()) {
                String code = m.group(1);
                if (code == null) continue;
                if (!knownCode.equalsIgnoreCase(code)) continue;
                int start = m.start(1);
                int end = m.end(1);
                if (hasClickableOverlap(span, start, end)) continue;
                final String copyCode = code.toUpperCase(Locale.US);
                span.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        copyText(widget.getContext(), copyCode, true);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(0xFFB8860B);
                        ds.setFakeBoldText(true);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        tv.setText(span);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
    }

    private static boolean hasClickableOverlap(Spannable span, int start, int end) {
        ClickableSpan[] existing = span.getSpans(start, end, ClickableSpan.class);
        return existing != null && existing.length > 0;
    }

    private static void openLink(Context ctx, String url, @Nullable String title) {
        if (TextUtils.isEmpty(url)) return;
        String u = normalizeUrl(url);
        try {
            if (u.startsWith("http://") || u.startsWith("https://")) {
                Intent i = new Intent(ctx, PromoWebActivity.class);
                i.putExtra(PromoWebActivity.EXTRA_URL, u);
                if (!TextUtils.isEmpty(title)) {
                    i.putExtra(PromoWebActivity.EXTRA_TITLE, title);
                }
                ctx.startActivity(i);
            } else {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                ctx.startActivity(i);
            }
        } catch (Exception e) {
            Toast.makeText(ctx, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private static void copyText(Context ctx, String text, boolean agencyCode) {
        if (TextUtils.isEmpty(text)) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(agencyCode ? "activation_code" : "official_news", text));
        }
        Toast.makeText(ctx,
                agencyCode ? R.string.agency_copied : R.string.message_copied,
                Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private static String extractActivationCode(String body) {
        if (TextUtils.isEmpty(body)) return null;
        Matcher labeled = Pattern.compile(
                "(?:كود\\s*(?:التفعيل|تفعيل)|activation\\s*code)\\s*[:：]?\\s*([A-HJ-NP-Z2-9]{8})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(body);
        if (labeled.find()) return labeled.group(1).toUpperCase(Locale.US);
        Matcher m = ACTIVATION_CODE.matcher(body);
        if (m.find() && m.group(1) != null) return m.group(1).toUpperCase(Locale.US);
        return null;
    }

    @Nullable
    private static String extractFirstUrl(String body) {
        if (TextUtils.isEmpty(body)) return null;
        Matcher m = URL_IN_TEXT.matcher(body);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            return g1 != null ? g1 : g2;
        }
        return null;
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith(".") || u.endsWith("،") || u.endsWith(",")
                || u.endsWith(")") || u.endsWith("]") || u.endsWith("»")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.startsWith("www.")) u = "https://" + u;
        return u;
    }

    @Nullable
    private static String firstString(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object raw = data.get(key);
            if (raw == null) continue;
            String v = String.valueOf(raw).trim();
            if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            String s = raw.length() > 19 ? raw.substring(0, 19) : raw;
            Date d = iso.parse(s);
            return d != null ? out.format(d) : raw;
        } catch (Exception e) {
            return raw.length() > 16 ? raw.substring(0, 16) : raw;
        }
    }

    static final class TextVH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvBody;
        final TextView tvTime;
        final ShapeableImageView imgMedia;
        final View rowActions;
        final TextView btnCopyCode;
        final TextView btnOpenLink;

        TextVH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvBubbleTitle);
            tvBody = v.findViewById(R.id.tvBubbleBody);
            tvTime = v.findViewById(R.id.tvBubbleTime);
            imgMedia = v.findViewById(R.id.imgBubbleMedia);
            rowActions = v.findViewById(R.id.rowActions);
            btnCopyCode = v.findViewById(R.id.btnCopyCode);
            btnOpenLink = v.findViewById(R.id.btnOpenLink);
        }
    }

    static final class HtmlVH extends RecyclerView.ViewHolder {
        final FrameLayout host;
        final WebView web;
        final TextView tvTime;
        boolean webConfigured;
        int lastHeightPx;
        @Nullable String pendingHtml;

        HtmlVH(View v) {
            super(v);
            host = v.findViewById(R.id.webHtmlHost);
            web = v.findViewById(R.id.webBubbleHtml);
            tvTime = v.findViewById(R.id.tvBubbleTime);
        }
    }
}
