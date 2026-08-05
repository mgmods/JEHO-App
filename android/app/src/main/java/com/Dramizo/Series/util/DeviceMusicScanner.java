package com.Dramizo.Series.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans device music via MediaStore — real titles/artists/covers.
 * Excludes WhatsApp / Telegram / chat voice notes / ringtones / recordings.
 */
public final class DeviceMusicScanner {
    private static final Pattern ARTIST_TITLE = Pattern.compile(
            "^\\s*(.+?)\\s*[-–—_|]+\\s*(.+)\\s*$");
    private static final long MIN_DURATION_MS = 20_000L; // skip ultra-short clips
    private static final long CHAT_CLIP_MAX_MS = 90_000L; // short clips in chat-like names

    private DeviceMusicScanner() {}

    public static final class Track {
        public final long mediaId;
        @NonNull public final Uri contentUri;
        @Nullable public final String absolutePath;
        @NonNull public final String title;
        @NonNull public final String artist;
        @Nullable public final String album;
        public final long durationMs;
        public final long albumId;
        @Nullable public final Uri albumArtUri;

        public Track(
                long mediaId,
                @NonNull Uri contentUri,
                @Nullable String absolutePath,
                @NonNull String title,
                @NonNull String artist,
                @Nullable String album,
                long durationMs,
                long albumId,
                @Nullable Uri albumArtUri) {
            this.mediaId = mediaId;
            this.contentUri = contentUri;
            this.absolutePath = absolutePath;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
            this.albumId = albumId;
            this.albumArtUri = albumArtUri;
        }
    }

    @NonNull
    public static List<Track> scan(@NonNull Context context) {
        List<Track> out = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        final List<String> cols = new ArrayList<>();
        cols.add(MediaStore.Audio.Media._ID);
        cols.add(MediaStore.Audio.Media.DISPLAY_NAME);
        cols.add(MediaStore.Audio.Media.TITLE);
        cols.add(MediaStore.Audio.Media.ARTIST);
        cols.add(MediaStore.Audio.Media.ALBUM);
        cols.add(MediaStore.Audio.Media.ALBUM_ID);
        cols.add(MediaStore.Audio.Media.DURATION);
        cols.add(MediaStore.Audio.Media.SIZE);
        cols.add(MediaStore.Audio.Media.MIME_TYPE);
        cols.add(MediaStore.Audio.Media.DATA);
        cols.add(MediaStore.Audio.Media.IS_MUSIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cols.add(MediaStore.Audio.Media.RELATIVE_PATH);
        }
        String[] projection = cols.toArray(new String[0]);

        // Prefer music, but also keep audio that is not flagged as music
        // (some OEMs leave IS_MUSIC=0 on real songs). Junk is path-filtered later.
        String selection = "("
                + MediaStore.Audio.Media.IS_MUSIC + "!=0"
                + " OR " + MediaStore.Audio.Media.MIME_TYPE + " LIKE 'audio/%'"
                + ") AND " + MediaStore.Audio.Media.SIZE + ">1024";

        try (Cursor c = context.getContentResolver().query(
                collection,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c == null) return out;
            int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int iDisplay = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int iAlbumId = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int iDur = c.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int iMime = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
            int iData = c.getColumnIndex(MediaStore.Audio.Media.DATA);
            int iRel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    : -1;
            int iIsMusic = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);

            while (c.moveToNext()) {
                long id = c.getLong(iId);
                if (!seenIds.add(id)) continue;

                String display = iDisplay >= 0 ? c.getString(iDisplay) : null;
                String titleRaw = iTitle >= 0 ? c.getString(iTitle) : null;
                String artistRaw = iArtist >= 0 ? c.getString(iArtist) : null;
                String album = iAlbum >= 0 ? c.getString(iAlbum) : null;
                long albumId = iAlbumId >= 0 ? c.getLong(iAlbumId) : 0L;
                long duration = iDur >= 0 ? c.getLong(iDur) : 0L;
                String mime = iMime >= 0 ? c.getString(iMime) : null;
                String path = iData >= 0 ? c.getString(iData) : null;
                String rel = iRel >= 0 ? c.getString(iRel) : null;
                int isMusic = iIsMusic >= 0 ? c.getInt(iIsMusic) : 1;

                if (isJunkMime(mime)) continue;
                if (isJunkLocation(path, rel, display)) continue;
                if (duration > 0 && duration < MIN_DURATION_MS) continue;
                // Chat-like short clips even if outside blocked folders.
                if (duration > 0 && duration < CHAT_CLIP_MAX_MS && looksLikeChatVoice(display, titleRaw)) {
                    continue;
                }
                // Reject obvious non-music when IS_MUSIC is 0 and duration short.
                if (isMusic == 0 && duration > 0 && duration < 45_000L) continue;

                Uri contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                String title = resolveTitle(titleRaw, display, path);
                String artist = resolveArtist(artistRaw, title, display, path, album);
                Uri art = albumId > 0 ? albumArtUri(albumId) : null;

                String key = (title + "|" + artist + "|" + (duration / 1000)).toLowerCase(Locale.US);
                if (!seenKeys.add(key)) continue;

                out.add(new Track(id, contentUri, path, title, artist, album, duration, albumId, art));
            }
        } catch (SecurityException ignored) {
            // Missing permission.
        } catch (Exception ignored) {
        }

        Collections.sort(out, Comparator
                .comparing((Track t) -> t.title.toLowerCase(Locale.ROOT))
                .thenComparing(t -> t.artist.toLowerCase(Locale.ROOT)));
        return out;
    }

    @Nullable
    public static Uri albumArtUri(long albumId) {
        if (albumId <= 0) return null;
        try {
            return ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
        } catch (Exception e) {
            return null;
        }
    }

    /** Best-effort embedded cover bytes (caller may write to cache file). */
    @Nullable
    public static byte[] embeddedCover(@NonNull Context context, @NonNull Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            return mmr.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }
    }

    @NonNull
    public static Meta readMeta(@NonNull Context context, @NonNull Uri uri) {
        String title = null;
        String artist = null;
        String album = null;
        // MediaStore first (usually better/localized metadata on Samsung etc.)
        try (Cursor c = context.getContentResolver().query(
                uri,
                new String[] {
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.ALBUM_ID,
                },
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                title = safe(c.getString(0));
                artist = safe(c.getString(1));
                album = safe(c.getString(2));
                String display = safe(c.getString(3));
                if (isUnknown(title) && display != null) {
                    title = stripExtension(display);
                }
            }
        } catch (Exception ignored) {
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            if (isUnknown(title)) {
                title = safe(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            }
            if (isUnknown(artist)) {
                artist = firstNonUnknown(
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
            }
            if (isUnknown(album)) {
                album = safe(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }

        if (isUnknown(title)) {
            String seg = uri.getLastPathSegment();
            title = stripExtension(seg != null ? Uri.decode(seg) : null);
        }
        if (isUnknown(title)) title = "أغنية";

        // "Artist - Title.mp3" filename pattern
        if (isUnknown(artist) || title.contains(" - ")) {
            Matcher m = ARTIST_TITLE.matcher(title);
            if (m.matches()) {
                String a = m.group(1).trim();
                String t = m.group(2).trim();
                if (!isUnknown(a) && !isUnknown(t)) {
                    if (isUnknown(artist)) artist = a;
                    title = t;
                }
            }
        }
        if (isUnknown(artist)) artist = "فنان غير معروف";
        return new Meta(title.trim(), artist.trim(), album);
    }

    public static final class Meta {
        @NonNull public final String title;
        @NonNull public final String artist;
        @Nullable public final String album;

        public Meta(@NonNull String title, @NonNull String artist, @Nullable String album) {
            this.title = title;
            this.artist = artist;
            this.album = album;
        }
    }

    private static boolean isJunkMime(@Nullable String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.US);
        // Drop phone-voice containers; keep mp3/m4a/flac/ogg music.
        return m.contains("amr")
                || m.equals("audio/3gpp")
                || m.equals("audio/3gpp2")
                || m.contains("midi")
                || m.contains("x-midi");
    }

    private static boolean isJunkLocation(
            @Nullable String path, @Nullable String relativePath, @Nullable String display) {
        String joined = ((path != null ? path : "")
                + " " + (relativePath != null ? relativePath : "")
                + " " + (display != null ? display : "")).toLowerCase(Locale.US);
        // Messaging & non-music containers
        String[] blocked = {
                "whatsapp", "w4b", "telegram", "org.telegram", "signal", "messenger",
                "facebook", "fb_messenger", "viber", "imo/", "imo ", "botim", "wechat",
                "snapchat", "instagram", "line/", "line ", "skype", "teams", "discord",
                "voice notes", "voicenotes", "voice messages", "voice messages",
                "audiomessages", "audio messages", "ptt", "push_to_talk",
                "recordings", "sound_recorder", "soundrecorder", "call_rec",
                "call recordings", "callrecording", "screen recordings",
                "notifications", "ringtones", "alarms", "ui sounds", "ui/sounds",
                "sounds/", "/sound/", "samsung/music/sound", "miui/sound",
                "native/media/audio", "android/media/com.whatsapp",
                "android/media/org.telegram", "android/data/com.whatsapp",
                "android/data/org.telegram", "android/data/com.facebook",
                "android/obb/", ".shared/tmp", "cache/", "tmp/",
                "audiobooks" // optional — keep if user wants all music; leave ok
        };
        for (String b : blocked) {
            // allow "sounds/Music" style? still blocked
            if (joined.contains(b)) {
                // Exception: real "Music/" library folder is fine
                if ("sounds/".equals(b) || "/sound/".equals(b)) {
                    if (joined.contains("/music/") || joined.contains("music/")) continue;
                }
                return true;
            }
        }
        // Extension heuristics for chat/export names
        if (display != null) {
            String d = display.toLowerCase(Locale.US);
            if (d.startsWith("ptt-") || d.startsWith("aud-")
                    || d.matches(".*whatsapp.*\\.(opus|ogg|aac|m4a|mp3)$")
                    || d.matches(".*telegram.*\\.(ogg|opus|m4a)$")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeChatVoice(@Nullable String display, @Nullable String title) {
        String s = ((display != null ? display : "") + " " + (title != null ? title : ""))
                .toLowerCase(Locale.US);
        return s.contains("ptt") || s.contains("voice") || s.contains("msg")
                || s.matches(".*audio[-_ ]?\\d+.*")
                || s.matches(".*recording[-_ ]?\\d+.*")
                || s.matches(".*\\d{8,}_\\d+\\.(opus|ogg|amr|m4a)$");
    }

    @NonNull
    private static String resolveTitle(
            @Nullable String titleRaw, @Nullable String display, @Nullable String path) {
        String title = safe(titleRaw);
        if (isUnknown(title) && display != null) title = stripExtension(display);
        if (isUnknown(title) && path != null) {
            String name = new File(path).getName();
            title = stripExtension(name);
        }
        if (isUnknown(title)) title = "أغنية";
        // Clean common MediaStore junk titles
        if (title != null) {
            title = title.replace('_', ' ').replaceAll("\\s+", " ").trim();
        }
        return title != null && !title.isEmpty() ? title : "أغنية";
    }

    @NonNull
    private static String resolveArtist(
            @Nullable String artistRaw,
            @NonNull String title,
            @Nullable String display,
            @Nullable String path,
            @Nullable String album) {
        String artist = firstNonUnknown(artistRaw);
        if (!isUnknown(artist)) return artist.trim();

        Matcher m = ARTIST_TITLE.matcher(title);
        if (m.matches()) {
            String a = m.group(1).trim();
            if (!isUnknown(a) && a.length() >= 2) return a;
        }
        // Parent folder sometimes is artist name (Music/Fairuz/song.mp3)
        if (path != null) {
            File parent = new File(path).getParentFile();
            if (parent != null) {
                String folder = parent.getName();
                if (!isUnknown(folder)
                        && !folder.equalsIgnoreCase("Music")
                        && !folder.equalsIgnoreCase("Download")
                        && !folder.equalsIgnoreCase("Downloads")
                        && !folder.equalsIgnoreCase("Songs")
                        && !folder.equalsIgnoreCase("audio")
                        && folder.length() >= 2
                        && !folder.matches("\\d+")) {
                    return folder.replace('_', ' ').trim();
                }
            }
        }
        if (!isUnknown(album) && album.length() >= 2) {
            // Don't use album as artist usually — skip.
        }
        return "فنان غير معروف";
    }

    private static boolean isUnknown(@Nullable String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        String l = t.toLowerCase(Locale.US);
        return l.equals("<unknown>")
                || l.equals("unknown")
                || l.equals("unknown artist")
                || l.equals("unknown album")
                || l.equals("artist")
                || l.equals("null")
                || l.equals("-")
                || l.equals("فنان غير معروف");
    }

    @Nullable
    private static String firstNonUnknown(String... values) {
        if (values == null) return null;
        for (String v : values) {
            String s = safe(v);
            if (!isUnknown(s)) return s;
        }
        return null;
    }

    @Nullable
    private static String safe(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String stripExtension(@Nullable String name) {
        if (name == null) return null;
        String n = name.trim();
        // content paths sometimes "primary:Music/x.mp3"
        int colon = n.lastIndexOf(':');
        if (colon >= 0 && colon < n.length() - 1) n = n.substring(colon + 1);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0 && slash < n.length() - 1) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return n.isEmpty() ? null : n;
    }
}
