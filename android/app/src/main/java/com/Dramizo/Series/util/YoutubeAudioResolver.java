package com.Dramizo.Series.util;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Resolve YouTube videoId → direct stream URL on-device.
 * Prefers progressive muxed video+audio (for thumbnail PlayerView),
 * then falls back to audio-only.
 */
public final class YoutubeAudioResolver {
    private static final String TAG = "YtAudio";
    private YoutubeAudioResolver() {}

    public static final class Resolved {
        public final String videoId;
        /** Best stream for ExoPlayer disc preview (may be muxed video). */
        public final String audioUrl;
        /** Prefer this for Zego room mix (audio-only when available). */
        @Nullable public final String mixUrl;
        public final String title;
        public final String artist;
        @Nullable public final String thumbnailUrl;
        @Nullable public final String mimeType;
        /** True when preview stream includes video. */
        public final boolean hasVideo;

        public Resolved(String videoId, String audioUrl, @Nullable String mixUrl,
                        String title, String artist,
                        @Nullable String thumbnailUrl, @Nullable String mimeType,
                        boolean hasVideo) {
            this.videoId = videoId;
            this.audioUrl = audioUrl;
            this.mixUrl = mixUrl != null && !mixUrl.isEmpty() ? mixUrl : audioUrl;
            this.title = title;
            this.artist = artist;
            this.thumbnailUrl = thumbnailUrl;
            this.mimeType = mimeType;
            this.hasVideo = hasVideo;
        }
    }

    public static final String PLAYER_USER_AGENT =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    /** Last human-readable failure (for toast/debug). */
    @Nullable public static volatile String lastError;

    @Nullable
    public static Resolved resolve(@Nullable String videoId) {
        lastError = null;
        if (videoId == null) {
            lastError = "empty id";
            return null;
        }
        String id = videoId.trim();
        if (!id.matches("^[A-Za-z0-9_-]{6,20}$")) {
            lastError = "bad id";
            return null;
        }

        // Prefer clients that often return plain progressive URLs.
        Resolved r = resolveWithClient(id, "ANDROID", "20.10.38", PLAYER_USER_AGENT, 34);
        if (r != null) return r;
        r = resolveWithClient(id, "ANDROID_VR", "1.60.19",
                "com.google.android.apps.youtube.vr.oculus/1.60.19 "
                        + "(Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip",
                31);
        if (r != null) return r;
        r = resolveWithClient(id, "IOS", "19.45.4",
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
                -1);
        if (r != null) return r;
        r = resolveWithClient(id, "TVHTML5", "7.20240101.00.00",
                "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
                -1);
        if (r != null) return r;
        if (lastError == null) lastError = "no playable stream";
        Log.w(TAG, "resolve failed id=" + id + " err=" + lastError);
        return null;
    }

    @Nullable
    private static Resolved resolveWithClient(
            String videoId,
            String clientName,
            String clientVersion,
            String userAgent,
            int androidSdk) {
        try {
            JsonObject client = new JsonObject();
            client.addProperty("clientName", clientName);
            client.addProperty("clientVersion", clientVersion);
            client.addProperty("hl", "en");
            client.addProperty("gl", "US");
            if (androidSdk > 0) client.addProperty("androidSdkVersion", androidSdk);
            if ("IOS".equals(clientName)) client.addProperty("deviceModel", "iPhone16,2");

            JsonObject context = new JsonObject();
            context.add("client", client);

            JsonObject body = new JsonObject();
            body.add("context", context);
            body.addProperty("videoId", videoId);
            body.addProperty("contentCheckOk", true);
            body.addProperty("racyCheckOk", true);

            Request req = new Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(RequestBody.create(
                            body.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", "3")
                    .header("X-YouTube-Client-Version", clientVersion)
                    .build();

            try (Response resp = HTTP.newCall(req).execute()) {
                ResponseBody rb = resp.body();
                String raw = rb != null ? rb.string() : "";
                if (!resp.isSuccessful()) {
                    lastError = clientName + " HTTP " + resp.code();
                    Log.w(TAG, lastError);
                    return null;
                }
                if (raw.isEmpty()) {
                    lastError = clientName + " empty body";
                    return null;
                }
                JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
                String status = null;
                if (root.has("playabilityStatus")
                        && root.get("playabilityStatus").isJsonObject()) {
                    JsonObject ps = root.getAsJsonObject("playabilityStatus");
                    if (ps.has("status") && !ps.get("status").isJsonNull()) {
                        status = ps.get("status").getAsString();
                    }
                }
                if (status != null && !"OK".equalsIgnoreCase(status)) {
                    lastError = clientName + " " + status;
                    Log.w(TAG, lastError + " id=" + videoId);
                    return null;
                }

                List<JsonObject> formats = new ArrayList<>();
                if (root.has("streamingData") && root.get("streamingData").isJsonObject()) {
                    JsonObject sd = root.getAsJsonObject("streamingData");
                    // Progressive muxed first (formats), then adaptive.
                    appendFormats(formats, sd.get("formats"));
                    appendFormats(formats, sd.get("adaptiveFormats"));
                }

                JsonObject preferred = pickBestStream(formats);
                if (preferred == null) {
                    lastError = clientName + " no stream url";
                    return null;
                }
                JsonObject audioMix = pickBestAudioOnly(formats);

                String mime = preferred.has("mimeType") && !preferred.get("mimeType").isJsonNull()
                        ? preferred.get("mimeType").getAsString() : "";
                boolean hasVideo = mime.toLowerCase(Locale.US).contains("video");

                String title = "أغنية";
                String artist = "يوتيوب";
                String thumb = null;
                if (root.has("videoDetails") && root.get("videoDetails").isJsonObject()) {
                    JsonObject details = root.getAsJsonObject("videoDetails");
                    if (details.has("title") && !details.get("title").isJsonNull()) {
                        title = details.get("title").getAsString();
                    }
                    if (details.has("author") && !details.get("author").isJsonNull()) {
                        artist = details.get("author").getAsString();
                    }
                    if (details.has("thumbnail") && details.get("thumbnail").isJsonObject()) {
                        JsonArray thumbs = details.getAsJsonObject("thumbnail")
                                .getAsJsonArray("thumbnails");
                        if (thumbs != null && thumbs.size() > 0) {
                            JsonObject last = thumbs.get(thumbs.size() - 1).getAsJsonObject();
                            if (last.has("url") && !last.get("url").isJsonNull()) {
                                thumb = last.get("url").getAsString();
                            }
                        }
                    }
                }

                String previewUrl = preferred.get("url").getAsString();
                String mixUrl = audioMix != null ? audioMix.get("url").getAsString() : previewUrl;
                Log.i(TAG, "resolved via " + clientName + " id=" + videoId
                        + " video=" + hasVideo + " mime=" + mime
                        + " mixAudio=" + (audioMix != null));
                return new Resolved(
                        videoId,
                        previewUrl,
                        mixUrl,
                        title,
                        artist,
                        thumb,
                        mime.isEmpty() ? null : mime,
                        hasVideo);
            }
        } catch (Exception e) {
            lastError = clientName + " " + e.getClass().getSimpleName()
                    + ": " + (e.getMessage() != null ? e.getMessage() : "");
            Log.w(TAG, "resolveWithClient failed " + lastError, e);
            return null;
        }
    }

    /**
     * Prefer progressive muxed mp4 (itag 18/22) for thumbnail video;
     * then any progressive video that includes an audio codec; then best audio-only.
     */
    @Nullable
    private static JsonObject pickBestStream(List<JsonObject> formats) {
        List<JsonObject> progressiveVideo = new ArrayList<>();
        List<JsonObject> audioOnly = new ArrayList<>();
        for (JsonObject f : formats) {
            if (f == null || !f.has("url") || f.get("url").isJsonNull()) continue;
            String mime = f.has("mimeType") && !f.get("mimeType").isJsonNull()
                    ? f.get("mimeType").getAsString().toLowerCase(Locale.US) : "";
            // Muxed progressive: video mime that also declares an audio codec (mp4a/aac).
            boolean muxedVideo = mime.contains("video")
                    && (mime.contains("mp4") || mime.contains("3gpp"))
                    && (mime.contains("mp4a") || mime.contains("aac") || mime.contains(","));
            // itag 18/22 are classic progressive muxed even if codecs string is odd.
            int itag = f.has("itag") && !f.get("itag").isJsonNull()
                    ? f.get("itag").getAsInt() : -1;
            if (itag == 18 || itag == 22 || itag == 36 || muxedVideo) {
                progressiveVideo.add(f);
            } else if (mime.contains("audio")) {
                audioOnly.add(f);
            }
        }

        if (!progressiveVideo.isEmpty()) {
            for (JsonObject f : progressiveVideo) {
                int itag = f.has("itag") && !f.get("itag").isJsonNull()
                        ? f.get("itag").getAsInt() : -1;
                if (itag == 18) return f;
            }
            Collections.sort(progressiveVideo, Comparator.comparingInt(YoutubeAudioResolver::heightOf)
                    .thenComparingInt(YoutubeAudioResolver::bitrateOf));
            for (JsonObject f : progressiveVideo) {
                int h = heightOf(f);
                if (h >= 240 && h <= 480) return f;
            }
            return progressiveVideo.get(0);
        }

        if (audioOnly.isEmpty()) return null;
        Collections.sort(audioOnly, Comparator.comparingInt(YoutubeAudioResolver::bitrateOf).reversed());
        for (JsonObject f : audioOnly) {
            String mime = f.get("mimeType").getAsString().toLowerCase(Locale.US);
            if (mime.contains("mp4") || mime.contains("mp4a") || mime.contains("aac")) {
                return f;
            }
        }
        return audioOnly.get(0);
    }

    @Nullable
    private static JsonObject pickBestAudioOnly(List<JsonObject> formats) {
        List<JsonObject> audioOnly = new ArrayList<>();
        for (JsonObject f : formats) {
            if (f == null || !f.has("url") || f.get("url").isJsonNull()) continue;
            String mime = f.has("mimeType") && !f.get("mimeType").isJsonNull()
                    ? f.get("mimeType").getAsString().toLowerCase(Locale.US) : "";
            if (mime.contains("audio")) audioOnly.add(f);
        }
        if (audioOnly.isEmpty()) return null;
        Collections.sort(audioOnly, Comparator.comparingInt(YoutubeAudioResolver::bitrateOf).reversed());
        for (JsonObject f : audioOnly) {
            String mime = f.get("mimeType").getAsString().toLowerCase(Locale.US);
            if (mime.contains("mp4") || mime.contains("mp4a") || mime.contains("aac")) {
                return f;
            }
        }
        return audioOnly.get(0);
    }

    private static int heightOf(JsonObject f) {
        try {
            if (f.has("height") && !f.get("height").isJsonNull()) {
                return f.get("height").getAsInt();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int bitrateOf(JsonObject f) {
        try {
            if (f.has("bitrate") && !f.get("bitrate").isJsonNull()) {
                return f.get("bitrate").getAsInt();
            }
            if (f.has("averageBitrate") && !f.get("averageBitrate").isJsonNull()) {
                return f.get("averageBitrate").getAsInt();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void appendFormats(List<JsonObject> out, JsonElement el) {
        if (el == null || !el.isJsonArray()) return;
        for (JsonElement item : el.getAsJsonArray()) {
            if (item != null && item.isJsonObject()) out.add(item.getAsJsonObject());
        }
    }
}
