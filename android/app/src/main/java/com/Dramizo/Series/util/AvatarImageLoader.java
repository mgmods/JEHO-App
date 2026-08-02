package com.Dramizo.Series.util;

import android.content.ContentResolver;
import android.graphics.Outline;
import android.net.Uri;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Profile/avatar images: keep GIF/WebP animation by clipping the view to a circle
 * instead of Glide {@code circleCrop()} (Bitmap transforms freeze animation).
 */
public final class AvatarImageLoader {
    private AvatarImageLoader() {}

    public static void applyCircularClip(@Nullable ImageView view) {
        if (view == null) return;
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                int w = Math.max(1, v.getWidth());
                int h = Math.max(1, v.getHeight());
                outline.setOval(0, 0, w, h);
            }
        });
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(
                        View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right - left > 0 && bottom - top > 0) {
                        v.removeOnLayoutChangeListener(this);
                        v.invalidateOutline();
                    }
                }
            });
        } else {
            view.invalidateOutline();
        }
    }

    public static void load(@Nullable ImageView view, @Nullable String avatarUrl) {
        if (view == null) return;
        applyCircularClip(view);
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            view.setImageResource(ImagePlaceholder.avatar());
            return;
        }
        String abs = AssetCatalog.absoluteUrl(avatarUrl);
        String lower = abs != null ? abs.toLowerCase(Locale.US) : "";
        boolean animated = lower.contains(".gif") || lower.contains(".webp");
        int size = Math.max(96, Math.min(
                view.getWidth() > 0 ? view.getWidth() : 256, 512));
        var req = Glide.with(view)
                .load(abs)
                .thumbnail(0.2f)
                .override(size, size)
                .centerCrop()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .placeholder(ImagePlaceholder.avatar())
                .error(ImagePlaceholder.avatar());
        // Still photos: freeze. GIF/WebP: keep motion (Mikoo shows live head).
        if (!animated) req = req.dontAnimate();
        req.into(view);
    }

    /** Local preview (gallery Uri) — preserves GIF animation. */
    public static void load(@Nullable ImageView view, @Nullable Uri uri) {
        if (view == null) return;
        applyCircularClip(view);
        if (uri == null) {
            view.setImageResource(ImagePlaceholder.avatar());
            return;
        }
        Glide.with(view).load(uri).into(view);
    }

    /**
     * Build multipart upload preserving GIF/PNG/WebP mime + extension
     * (do not force JPEG — that breaks animated avatars).
     */
    public static MultipartBody.Part multipartFromUri(
            ContentResolver resolver, Uri uri, String baseName) throws Exception {
        byte[] bytes;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("cannot open image");
            bytes = readAll(in);
        }
        String mime = resolver.getType(uri);
        if (mime == null || !mime.toLowerCase(Locale.US).startsWith("image/")) {
            mime = sniffImageMime(bytes);
        }
        String ext = extensionForMime(mime);
        String name = (baseName != null && !baseName.isEmpty() ? baseName : "avatar") + ext;
        RequestBody body = RequestBody.create(bytes, MediaType.parse(mime));
        return MultipartBody.Part.createFormData("file", name, body);
    }

    public static String sniffImageMime(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return "image/jpeg";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public static String extensionForMime(@Nullable String mime) {
        if (mime == null) return ".jpg";
        String m = mime.toLowerCase(Locale.US);
        if (m.contains("gif")) return ".gif";
        if (m.contains("png")) return ".png";
        if (m.contains("webp")) return ".webp";
        if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
        return ".jpg";
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
