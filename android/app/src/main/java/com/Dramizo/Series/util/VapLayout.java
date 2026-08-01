package com.Dramizo.Series.util;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Mikoo/YY VAP {@code vapc} metadata embedded in entry MP4s.
 * Layout is side-by-side RGB + alpha plates (not a corrupt download).
 */
public final class VapLayout {
    private static final String TAG = "VapLayout";
    private static final Pattern RGB =
            Pattern.compile("\"rgbFrame\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");
    private static final Pattern A =
            Pattern.compile("\"aFrame\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]");
    private static final Pattern VIDEO_W = Pattern.compile("\"videoW\"\\s*:\\s*(\\d+)");
    private static final Pattern VIDEO_H = Pattern.compile("\"videoH\"\\s*:\\s*(\\d+)");
    private static final Pattern CONTENT_W = Pattern.compile("\"w\"\\s*:\\s*(\\d+)");
    private static final Pattern CONTENT_H = Pattern.compile("\"h\"\\s*:\\s*(\\d+)");

    /** Normalized UV rect: x, y, width, height in 0..1 of the full video texture. */
    public final float rgbX, rgbY, rgbW, rgbH;
    public final float aX, aY, aW, aH;
    public final int videoW, videoH;
    public final int contentW, contentH;

    public VapLayout(float rgbX, float rgbY, float rgbW, float rgbH,
                     float aX, float aY, float aW, float aH,
                     int videoW, int videoH, int contentW, int contentH) {
        this.rgbX = rgbX;
        this.rgbY = rgbY;
        this.rgbW = rgbW;
        this.rgbH = rgbH;
        this.aX = aX;
        this.aY = aY;
        this.aW = aW;
        this.aH = aH;
        this.videoW = videoW;
        this.videoH = videoH;
        this.contentW = contentW;
        this.contentH = contentH;
    }

    /** Classic SBS fallback when vapc is missing. */
    public static VapLayout leftRightHalf() {
        return new VapLayout(0f, 0f, 0.5f, 1f, 0.5f, 0f, 0.5f, 1f, 1504, 1632, 750, 1624);
    }

    public float contentAspect() {
        if (contentW > 0 && contentH > 0) return contentW / (float) contentH;
        if (rgbW > 0f && videoW > 0 && videoH > 0) {
            return (rgbW * videoW) / (float) videoH;
        }
        return 750f / 1624f;
    }

    /** RGB plate pixel width for Exo crop fallback. */
    public int rgbPixelWidth() {
        if (videoW > 0 && rgbW > 0f) return Math.max(1, Math.round(rgbW * videoW));
        return Math.max(1, videoW / 2);
    }

    @Nullable
    public static VapLayout parse(@Nullable byte[] data) {
        if (data == null || data.length < 32) return null;
        int vapc = indexOf(data, "vapc".getBytes(StandardCharsets.US_ASCII));
        if (vapc < 0) return null;
        int brace = -1;
        for (int i = vapc; i < Math.min(data.length, vapc + 64); i++) {
            if (data[i] == '{') {
                brace = i;
                break;
            }
        }
        if (brace < 0) return null;
        int end = brace;
        int depth = 0;
        for (int i = brace; i < Math.min(data.length, brace + 4096); i++) {
            byte b = data[i];
            if (b == '{') depth++;
            else if (b == '}') {
                depth--;
                if (depth == 0) {
                    end = i + 1;
                    break;
                }
            }
        }
        if (end <= brace) return null;
        String json = new String(data, brace, end - brace, StandardCharsets.UTF_8);
        Matcher rgb = RGB.matcher(json);
        Matcher a = A.matcher(json);
        if (!rgb.find() || !a.find()) return null;
        int videoW = matchInt(VIDEO_W, json, 0);
        int videoH = matchInt(VIDEO_H, json, 0);
        int contentW = matchInt(CONTENT_W, json, 0);
        int contentH = matchInt(CONTENT_H, json, 0);
        if (videoW <= 0 || videoH <= 0) return null;
        float invW = 1f / videoW;
        float invH = 1f / videoH;
        float rgbX = Integer.parseInt(rgb.group(1)) * invW;
        float rgbY = Integer.parseInt(rgb.group(2)) * invH;
        float rgbW = Integer.parseInt(rgb.group(3)) * invW;
        float rgbH = Integer.parseInt(rgb.group(4)) * invH;
        float aX = Integer.parseInt(a.group(1)) * invW;
        float aY = Integer.parseInt(a.group(2)) * invH;
        float aW = Integer.parseInt(a.group(3)) * invW;
        float aH = Integer.parseInt(a.group(4)) * invH;
        if (contentW <= 0) contentW = Integer.parseInt(rgb.group(3));
        if (contentH <= 0) contentH = Integer.parseInt(rgb.group(4));
        Log.i(TAG, String.format(Locale.US,
                "vapc video=%dx%d rgb=[%.3f,%.3f,%.3f,%.3f] a=[%.3f,%.3f,%.3f,%.3f] content=%dx%d",
                videoW, videoH, rgbX, rgbY, rgbW, rgbH, aX, aY, aW, aH, contentW, contentH));
        return new VapLayout(rgbX, rgbY, rgbW, rgbH, aX, aY, aW, aH,
                videoW, videoH, contentW, contentH);
    }

    /** Download the head of an MP4 and parse vapc (safe on IO thread). */
    @Nullable
    public static VapLayout fetch(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url.trim()).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Range", "bytes=0-262143");
            conn.setRequestProperty("User-Agent", "JEHO-CHAT-VAP/1.0");
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) return null;
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0 && total < 262144) {
                bos.write(buf, 0, n);
                total += n;
            }
            return parse(bos.toByteArray());
        } catch (Exception e) {
            Log.w(TAG, "fetch vapc failed: " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int matchInt(Pattern p, String json, int fallback) {
        Matcher m = p.matcher(json);
        if (!m.find()) return fallback;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
