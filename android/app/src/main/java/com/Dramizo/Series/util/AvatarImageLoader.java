package com.Dramizo.Series.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
            view.setImageResource(ImagePlaceholder.avatar());
            return;
        }
        String abs = AssetCatalog.absoluteUrl(avatarUrl);
        Object prev = view.getTag(R.id.tag_image_url);
        boolean loadedOk = Boolean.TRUE.equals(view.getTag(R.id.tag_image_loaded));
        // Only skip when the same URL already decoded — never lock on placeholder/error drawable.
        if (prev instanceof String && abs != null && abs.equals(prev)
                && loadedOk && view.getDrawable() != null) {
            return;
        }
        view.setTag(R.id.tag_image_url, abs);
        view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
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
                .error(ImagePlaceholder.avatar())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource) {
                        view.setTag(R.id.tag_image_loaded, Boolean.FALSE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource dataSource,
                            boolean isFirstResource) {
                        view.setTag(R.id.tag_image_loaded, Boolean.TRUE);
                        return false;
                    }
                });
        // Placeholder only on cold first paint (no current drawable).
        if (view.getDrawable() == null) {
            req = req.placeholder(ImagePlaceholder.avatar());
        }
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
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_UPLOAD_BYTES = 6 * 1024 * 1024;

    public static MultipartBody.Part multipartFromUri(
            ContentResolver resolver, Uri uri, String baseName) throws Exception {
        if (uri == null) throw new IllegalArgumentException("اختر صورة أولاً");

        String sourceMime = resolver.getType(uri);
        if (sourceMime != null) sourceMime = sourceMime.toLowerCase(Locale.US);

        boolean animated = sourceMime != null
                && (sourceMime.contains("gif") || sourceMime.contains("webp"));
        if (animated) {
            byte[] bytes;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("تعذر قراءة الصورة");
                bytes = readAll(in);
            }
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("الصورة المتحركة كبيرة جداً (الحد 6MB)");
            }
            String mime = sourceMime.contains("gif") ? "image/gif" : "image/webp";
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
