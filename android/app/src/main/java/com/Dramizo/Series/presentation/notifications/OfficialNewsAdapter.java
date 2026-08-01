package com.Dramizo.Series.presentation.notifications;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.util.LinkifyCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemOfficialNewsBubbleBinding;
import com.Dramizo.Series.presentation.web.PromoWebActivity;

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
 * Official News bubbles: clickable http(s) links + tap/copy agency activation codes.
 */
public class OfficialNewsAdapter extends RecyclerView.Adapter<OfficialNewsAdapter.VH> {
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemOfficialNewsBubbleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MiscDtos.NotificationDto n = items.get(position);
        Context ctx = h.itemView.getContext();
        String title = n.title != null ? n.title : "";
        String body = n.body != null ? n.body : "";
        h.b.tvBubbleTitle.setText(title);
        h.b.tvBubbleTime.setText(formatTime(n.createdAt));

        // Agency copy-code UI only for real agency notifications — never for system/admin news.
        // Guard: even if data accidentally carries activationCode on a system broadcast, hide it.
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
        String linkFromData = firstString(n.data, "url", "link", "href", "webUrl");
        if (TextUtils.isEmpty(linkFromData)) {
            linkFromData = extractFirstUrl(body);
        }

        bindRichBody(h.b.tvBubbleBody, body, isAgency ? codeFromData : null);

        boolean hasCode = isAgency && !TextUtils.isEmpty(codeFromData);
        boolean hasLink = !TextUtils.isEmpty(linkFromData);
        h.b.rowActions.setVisibility(hasCode || hasLink ? View.VISIBLE : View.GONE);

        if (hasCode) {
            final String code = codeFromData;
            h.b.btnCopyCode.setVisibility(View.VISIBLE);
            h.b.btnCopyCode.setOnClickListener(v -> copyText(ctx, code, true));
        } else {
            h.b.btnCopyCode.setVisibility(View.GONE);
            h.b.btnCopyCode.setOnClickListener(null);
        }

        if (hasLink) {
            final String link = normalizeUrl(linkFromData);
            h.b.btnOpenLink.setVisibility(View.VISIBLE);
            h.b.btnOpenLink.setOnClickListener(v -> openLink(ctx, link, title));
        } else {
            h.b.btnOpenLink.setVisibility(View.GONE);
            h.b.btnOpenLink.setOnClickListener(null);
        }

        String copyPayload = TextUtils.isEmpty(body) ? title : body;
        h.itemView.setOnLongClickListener(v -> {
            copyText(ctx, copyPayload, false);
            return true;
        });
        h.b.tvBubbleBody.setOnLongClickListener(v -> {
            copyText(ctx, copyPayload, false);
            return true;
        });
    }

    private void bindRichBody(android.widget.TextView tv, String body, @Nullable String knownCode) {
        if (TextUtils.isEmpty(body)) {
            tv.setText("");
            return;
        }
        SpannableString span = new SpannableString(body);
        LinkifyCompat.addLinks(span, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);

        // Replace default URL spans with in-app / external openers.
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

        // Make activation codes tappable to copy — only when this bubble is agency.
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

    private static boolean hasClickableOverlap(SpannableString span, int start, int end) {
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
        // Prefer explicit "كود التفعيل: XXXXXXXX"
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
        // Strip trailing punctuation common in Arabic sentences.
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

    static class VH extends RecyclerView.ViewHolder {
        final ItemOfficialNewsBubbleBinding b;
        VH(ItemOfficialNewsBubbleBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
