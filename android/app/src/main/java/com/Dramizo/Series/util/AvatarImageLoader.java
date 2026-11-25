package com.Dramizo.Series.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import com.Dramizo.Series.R;

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
            view.setTag(R.id.tag_image_url, null);
            view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
            try { Glide.with(view).clear(view); } catch (Exception ignored) {}
            view.setImageResource(ImagePlaceholder.avatar());
            return;
        }
        String abs = AssetCatalog.absoluteUrl(avatarUrl.trim());
        if (abs == null || abs.isEmpty()) return;
        boolean animated = isAnimatedMediaUrl(abs);
        int size = Math.max(96, Math.min(
                view.getWidth() > 0 ? view.getWidth() : 256, 512));
        String requestKey = abs + "#avatar:" + size;
        Object previous = view.getTag(R.id.tag_image_url);
        Object state = view.getTag(R.id.tag_image_loaded);
        if (requestKey.equals(previous) && state instanceof Boolean) {
            // FALSE means the same request is already in flight; TRUE means decoded.
            // Failed requests clear the key below, so a later bind can retry.
            return;
        }
        view.setTag(R.id.tag_image_url, requestKey);
        view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
        var req = Glide.with(view)
                .load(abs)
                .override(size, size)
                .diskCacheStrategy(animated
                        ? com.bumptech.glide.load.engine.DiskCacheStrategy.DATA
                        : com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                .error(ImagePlaceholder.avatar())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e, Object model,
                            Target<Drawable> target, boolean isFirstResource) {
                        if (requestKey.equals(view.getTag(R.id.tag_image_url))) {
                            view.setTag(R.id.tag_image_url, null);
                            view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource, Object model, Target<Drawable> target,
                            DataSource dataSource, boolean isFirstResource) {
                        if (requestKey.equals(view.getTag(R.id.tag_image_url))) {
                            view.setTag(R.id.tag_image_loaded, Boolean.TRUE);
                        }
                        return false;
                    }
                });
        if (view.getDrawable() == null) req = req.placeholder(ImagePlaceholder.avatar());
        // Do not bitmap-transform animated frames; static art gets a tiny preview.
        if (!animated) req = req.centerCrop().dontAnimate().thumbnail(0.2f);
        else req = req.dontTransform();
        req.into(view);
    }

    /**
     * Load profile / room covers at the actual view size while preserving GIF/WebP.
     * Repeated room/profile refreshes must not restart an already-running animation.
     */
    public static void loadMedia(@Nullable ImageView view, @Nullable String mediaUrl, int placeholder) {
        if (view == null) return;
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            view.setTag(R.id.tag_image_url, null);
            view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
            try { Glide.with(view).clear(view); } catch (Exception ignored) {}
            view.setImageResource(placeholder);
            return;
        }

        String abs = AssetCatalog.absoluteUrl(mediaUrl.trim());
        if (abs == null || abs.isEmpty()) {
            view.setImageResource(placeholder);
            return;
        }
        int width = view.getWidth() > 0 ? view.getWidth() : 1024;
        int height = view.getHeight() > 0 ? view.getHeight() : 512;
        // Avoid decoding a full-resolution upload into a small profile/room surface.
        width = Math.max(96, Math.min(width, 2048));
        height = Math.max(96, Math.min(height, 2048));
        String requestKey = abs + "#media:" + width + "x" + height;
        Object previous = view.getTag(R.id.tag_image_url);
        Object state = view.getTag(R.id.tag_image_loaded);
        if (requestKey.equals(previous) && state instanceof Boolean) {
            // TRUE = already decoded; FALSE = request still in flight. Failed requests
            // clear the key in the listener below, so a later bind can retry.
            return;
        }

        view.setTag(R.id.tag_image_url, requestKey);
        view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
        boolean animated = isAnimatedMediaUrl(abs);
        try {
            var request = Glide.with(view).load(abs)
                    .override(width, height)
                    .diskCacheStrategy(animated
                            ? com.bumptech.glide.load.engine.DiskCacheStrategy.DATA
                            : com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                    .error(placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e, Object model,
                                Target<Drawable> target, boolean isFirstResource) {
                            if (requestKey.equals(view.getTag(R.id.tag_image_url))) {
                                view.setTag(R.id.tag_image_url, null);
                                view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource, Object model, Target<Drawable> target,
                                DataSource dataSource, boolean isFirstResource) {
                            if (requestKey.equals(view.getTag(R.id.tag_image_url))) {
                                view.setTag(R.id.tag_image_loaded, Boolean.TRUE);
                            }
                            return false;
                        }
                    });
            if (view.getDrawable() == null) request = request.placeholder(placeholder);
            // Do not bitmap-transform animated frames; static art gets a tiny preview
            // while the correctly sized final resource is decoded.
            if (animated) {
                request = request.dontTransform();
            } else {
                request = request.centerCrop().dontAnimate().thumbnail(0.15f);
            }
            request.into(view);
        } catch (Exception ignored) {
            view.setTag(R.id.tag_image_url, null);
            view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
            if (view.getDrawable() == null) view.setImageResource(placeholder);
        }
    }

    /** Local cover preview (gallery Uri) — keep the original animated drawable. */
    public static void loadMedia(@Nullable ImageView view, @Nullable Uri uri, int placeholder) {
        if (view == null) return;
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (uri == null) {
            view.setImageResource(placeholder);
            return;
        }
        Glide.with(view).load(uri).dontTransform()
                .placeholder(placeholder).error(placeholder).into(view);
    }

    /** Local preview (gallery Uri) — preserves GIF animation. */
    public static void load(@Nullable ImageView view, @Nullable Uri uri) {
        if (view == null) return;
        applyCircularClip(view);
        if (uri == null) {
            view.setImageResource(ImagePlaceholder.avatar());
            return;
        }
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(view).load(uri).dontTransform().into(view);
    }

    private static boolean isAnimatedMediaUrl(@Nullable String value) {
        if (value == null) return false;
        String path = value.toLowerCase(Locale.US);
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        return path.endsWith(".gif") || path.endsWith(".webp");
    }

    /**
     * Build multipart upload preserving GIF/PNG/WebP mime + extension
     * (do not force JPEG — that breaks animated avatars).
     */
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_UPLOAD_BYTES = 6 * 1024 * 1024;

    public static MultipartBody.Part multipartFromUri(
            ContentResolver resolver, Uri uri, String baseName) throws Exception {
        if (uri == null) throw new IllegalArgumentException("اختر صورة أولاً");

        String sourceMime = resolver.getType(uri);
        if (sourceMime != null) sourceMime = sourceMime.toLowerCase(Locale.US);
        String displayName = "";
        try (android.database.Cursor cursor = resolver.query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) displayName = cursor.getString(column);
            }
        } catch (Exception ignored) {}
        String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.US);
        boolean gif = (sourceMime != null && sourceMime.contains("gif")) || lowerName.endsWith(".gif");
        boolean webp = (sourceMime != null && sourceMime.contains("webp")) || lowerName.endsWith(".webp");
        // Some gallery/providers return a generic MIME type. Inspect the file signature
        // before deciding to recompress it, otherwise a GIF can silently become a JPEG.
        if (!gif && !webp) {
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in != null) {
                    byte[] header = new byte[12];
                    int n = in.read(header);
                    gif = n >= 3 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F';
                    webp = n >= 12 && header[0] == 'R' && header[1] == 'I'
                            && header[2] == 'F' && header[3] == 'F'
                            && header[8] == 'W' && header[9] == 'E'
                            && header[10] == 'B' && header[11] == 'P';
                }
            } catch (Exception ignored) {}
        }
        if (gif || webp) {
            byte[] bytes;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("تعذر قراءة الصورة");
                bytes = readAll(in);
            }
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("الصورة المتحركة كبيرة جداً (الحد 6MB)");
            }
            String mime = gif ? "image/gif" : "image/webp";
            String ext = extensionForMime(mime);
            String name = (baseName != null && !baseName.isEmpty() ? baseName : "avatar") + ext;
            return MultipartBody.Part.createFormData(
                    "file", name, RequestBody.create(bytes, MediaType.parse(mime)));
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("تعذر قراءة الصورة");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalArgumentException(
                    "صيغة الصورة غير مدعومة. استخدم JPG أو PNG أو WebP أو GIF");
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("تعذر قراءة الصورة");
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) {
            throw new IllegalArgumentException(
                    "تعذر تجهيز الصورة. استخدم JPG أو PNG أو WebP أو GIF");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int quality = 88;
        boolean wrote = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        while (wrote && out.size() > MAX_UPLOAD_BYTES && quality > 65) {
            out.reset();
            quality -= 8;
            wrote = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }
        bitmap.recycle();
        if (!wrote || out.size() == 0) throw new IllegalStateException("تعذر ضغط الصورة");
        if (out.size() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("الصورة كبيرة جداً بعد الضغط (الحد 6MB)");
        }

        byte[] bytes = out.toByteArray();
        String name = (baseName != null && !baseName.isEmpty() ? baseName : "avatar") + ".jpg";
        return MultipartBody.Part.createFormData(
                "file", name, RequestBody.create(bytes, MediaType.parse("image/jpeg")));
    }

    private static int sampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2;
        return Math.max(1, sample);
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
