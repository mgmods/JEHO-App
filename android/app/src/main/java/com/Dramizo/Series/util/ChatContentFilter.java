package com.Dramizo.Series.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client-side chat filter (mirrors server ContentModerationService).
 * Zego room chat bypasses the API, so this must run before send.
 */
public final class ChatContentFilter {
    private static final Pattern[] DEFAULT_BLOCKED = new Pattern[] {
            Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("www\\.\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:t\\.me|telegram\\.me|wa\\.me|bit\\.ly|goo\\.gl)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:تليجرام|تلغرام|تيليجرام|واتس|واتساب|انستا|انستغرام|سناب)"),
            Pattern.compile("(?:telegram|whatsapp|instagram|snapchat|tiktok|discord)\\.?(?:com|me)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:حمل|نزّل|نزل)\\s*(?:التطبيق|البرنامج|الابلكيشن|الآب)"),
            Pattern.compile("(?:download|install)\\s+(?:app|apk)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\.apk\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:كود\\s*دعوة|رابط\\s*دعوة|invite\\s*code)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:سكس|جنس|نيك|زب|كس\\b|عري|عارية|سكسى|اباحي|إباحي)"),
            Pattern.compile("(?:porn|xxx|onlyfans|nude|naked|sex\\b)", Pattern.CASE_INSENSITIVE),
    };

    /** Personal name / bio / personal room title — not for real agency names. */
    private static final Pattern[] AGENCY_IMPERSONATION = new Pattern[] {
            Pattern.compile("وكالة|وكاله|وكالات"),
            Pattern.compile("\\bagenc(?:y|ies)\\b", Pattern.CASE_INSENSITIVE),
    };

    private ChatContentFilter() {}

    public static final String BLOCK_REASON =
            "ممنوع الترويج أو الروابط أو المحتوى المخالف في الدردشة";

    public static final String AGENCY_WORD_REASON =
            "لا يُسمح باستخدام كلمة «وكالة» في الاسم أو النبذة أو اسم الروم الشخصي. الكلمة محجوزة للوكالات الرسمية.";

    public static boolean containsAgencyImpersonation(@Nullable String text) {
        if (TextUtils.isEmpty(text)) return false;
        String raw = text.trim();
        for (Pattern p : AGENCY_IMPERSONATION) {
            if (p.matcher(raw).find()) return true;
        }
        return false;
    }

    public static boolean isBlocked(@Nullable String text, @Nullable List<String> extraKeywords) {
        if (TextUtils.isEmpty(text)) return false;
        String raw = text.trim();
        // Structured app cards (room share / agency family invite) are allowed.
        if (raw.startsWith("[[room_share|") || raw.startsWith("[[agency_invite|")) {
            return false;
        }
        for (Pattern p : DEFAULT_BLOCKED) {
            if (p.matcher(raw).find()) return true;
        }
        if (extraKeywords != null) {
            for (String word : extraKeywords) {
                if (TextUtils.isEmpty(word)) continue;
                if (raw.toLowerCase(Locale.ROOT).contains(word.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<String> parseExtraKeywords(@Nullable String raw) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        for (String part : raw.split("[,\\n]+")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
