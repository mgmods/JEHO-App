package com.Dramizo.Series.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native host-frame renderer matching Host signals:
 * wing flap (clipped frame duplicates), aura pulse, shine sweep.
 * No vertical bounce — the stage stays planted while wings move.
 */
@OptIn(markerClass = UnstableApi.class)
public final class HostSignalView extends FrameLayout {
    private static final Pattern LEVEL =
            Pattern.compile("frame-(\\d{1,2})-", Pattern.CASE_INSENSITIVE);
    private static final String[] FRAME_FILES = {
            "frame-01-bronze-falcon-transparent.png",
            "frame-02-silver-stag-transparent.png",
            "frame-03-crimson-griffin-transparent.png",
            "frame-04-emerald-jaguar-transparent.png",
            "frame-05-sapphire-leviathan-transparent.png",
            "frame-06-amethyst-owl-transparent.png",
            "frame-07-solar-scarab-transparent.png",
            "frame-08-fire-stallion-transparent.png",
            "frame-09-celestial-swan-transparent.png",
            "frame-10-royal-seraph-transparent.png",
            "frame-11-cobra-transparent.png", "frame-12-lion-transparent.png",
            "frame-13-eagle-transparent.png", "frame-14-wolf-transparent.png",
            "frame-15-phoenix-transparent.png", "frame-16-scorpion-transparent.png",
            "frame-17-kraken-transparent.png", "frame-18-ram-transparent.png",
            "frame-19-raven-transparent.png", "frame-20-emperor-transparent.png",
            "frame-21-dragon-transparent.png", "frame-22-tiger-transparent.png",
            "frame-23-polar-bear-transparent.png", "frame-24-shark-transparent.png",
            "frame-25-minotaur-transparent.png", "frame-26-spider-transparent.png",
            "frame-27-thunder-transparent.png", "frame-28-samurai-transparent.png",
            "frame-29-demon-transparent.png", "frame-30-cosmic-transparent.png"
    };
    private static final int[] ACCENTS = {
            0xff91a1b8, 0xff735cff, 0xffe64d36, 0xff14b88a, 0xff248dff,
            0xffc849ff, 0xffffb21d, 0xffff304f, 0xff33d6e8, 0xfff6ce62,
            0xff64e572, 0xffff9f1c, 0xff69b7ff, 0xff8f9baa, 0xffff4b24,
            0xffd4ff2c, 0xff9f63ff, 0xffc88952, 0xff5474d8, 0xffffd65a,
            0xffff3d24, 0xff5caeff, 0xff7dd8ff, 0xff16c8ee, 0xffdf641f,
            0xfff2254f, 0xff3284ff, 0xffd92325, 0xffff253c, 0xff9d6cff
    };
    /**
     * Opening geometry from Host signals CSS {@code .profile-photo} / {@code .level-N}:
     * {left, top, width} so the avatar fills the authored hole (no empty ring).
     */
    private static final float[][] AVATAR_OPENINGS = {
            // 1–9 default: top 27% / left 27% / width 46%
            {.270f, .270f, .460f}, {.270f, .270f, .460f}, {.270f, .270f, .460f},
            {.270f, .270f, .460f}, {.270f, .270f, .460f}, {.270f, .270f, .460f},
            {.270f, .270f, .460f}, {.270f, .270f, .460f}, {.270f, .270f, .460f},
            // 10
            {.255f, .225f, .490f},
            // 11–30
            {.240f, .240f, .520f}, {.300f, .380f, .400f}, {.280f, .360f, .440f},
            {.270f, .370f, .460f}, {.330f, .380f, .340f}, {.280f, .310f, .440f},
            {.290f, .350f, .420f}, {.300f, .370f, .400f}, {.300f, .340f, .400f},
            {.380f, .390f, .240f}, {.320f, .400f, .360f}, {.310f, .410f, .380f},
            {.330f, .420f, .340f}, {.290f, .340f, .420f}, {.300f, .400f, .400f},
            {.310f, .400f, .380f}, {.300f, .340f, .400f}, {.330f, .390f, .340f},
            {.310f, .390f, .380f}, {.385f, .400f, .230f}
    };
    private static final LruCache<String, Bitmap> BITMAPS = new LruCache<String, Bitmap>(8 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    /** Soft cap — mall grid must not keep dozens of decoded SVGA in RAM. */
    private static final LruCache<String, SVGAVideoEntity> SVGA_CACHE = new LruCache<>(6);
    /** Prevent parallel SVGA downloads from OOMing the mall. */
    private static final java.util.concurrent.Semaphore SVGA_DOWNLOADS =
            new java.util.concurrent.Semaphore(2);

    private final FrameLayout stage;
    private final View halo;
    private final AppCompatImageView avatar;
    private final ClippedWingImageView wingLeft;
    private final ClippedWingImageView wingRight;
    private final ImageView frame;
    private final View shine;
    @Nullable private AnimatorSet motion;
    private int generation;
    private int boundLevel = 1;
    @Nullable private Map<String, ?> boundMetadata;
    @Nullable private String boundFrameUrl;
    /** Mall grid: never decode SVGA — static PNG only (avoids OOM). */
    private boolean staticPreviewOnly;
    private float artworkAspect = 1f;
    private boolean svgaActive;
    @Nullable private Runnable onFrameReadyListener;

    public HostSignalView(Context context) {
        this(context, null);
    }

    public HostSignalView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HostSignalView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);

        stage = new FrameLayout(context);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        halo = new View(context);
        avatar = new AppCompatImageView(context);
        wingLeft = new ClippedWingImageView(context, true);
        wingRight = new ClippedWingImageView(context, false);
        frame = new ImageView(context);
        shine = new View(context);
        addView(stage);
        stage.addView(halo);
        stage.addView(avatar);
        stage.addView(wingLeft);
        stage.addView(wingRight);
        stage.addView(frame);
        stage.addView(shine);

        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(null);
        avatar.setPadding(0, 0, 0, 0);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, Math.max(1, view.getWidth()), Math.max(1, view.getHeight()));
            }
        });
        frame.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wingLeft.setScaleType(ImageView.ScaleType.FIT_CENTER);
        wingRight.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable shimmer = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, 0x88ffffff, Color.TRANSPARENT});
        shine.setBackground(shimmer);
        shine.setRotation(18f);
        shine.setAlpha(0f);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    /**
     * Warm Glide caches only. Never decode SVGA here — parallel SVGA downloads
     * OOMed the process when opening the mall / profile.
     */
    public static void prefetchWear(@Nullable Context context, @Nullable String wearUrl) {
        if (context == null || wearUrl == null || wearUrl.isEmpty()) return;
        String abs = AssetCatalog.absoluteUrl(wearUrl);
        if (abs == null || abs.isEmpty()) return;
        try {
            if (CosmeticMedia.kind(wearUrl) == CosmeticMedia.Kind.SVGA) {
                // Prefetch PNG sibling only — skip SVGA download entirely.
                String png = abs.replaceAll("(?i)\\.svga(\\?.*)?$", ".png$1");
                if (!png.equals(abs)) {
                    Glide.with(context.getApplicationContext())
                            .load(png)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .preload(256, 256);
                }
                return;
            }
            Glide.with(context.getApplicationContext())
                    .load(abs)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .preload(256, 256);
        } catch (Exception ignored) {
        }
    }

    public boolean bind(@Nullable String hostBadgeUrl, @Nullable String avatarUrl, int fallbackLevel) {
        return bind(hostBadgeUrl, avatarUrl, null, fallbackLevel);
    }

    /**
     * Mall grid preview: avatar + static PNG frame only (no SVGA/GIF decode flood).
     * Prevents OutOfMemoryError when many cells bind at once.
     */
    public boolean bindStaticPreview(@Nullable String hostBadgeUrl, @Nullable String avatarUrl,
                                     @Nullable Map<String, ?> metadata, int fallbackLevel) {
        staticPreviewOnly = true;
        try {
            String still = stillPreviewUrl(hostBadgeUrl);
            return bind(still, avatarUrl, metadata, fallbackLevel);
        } finally {
            staticPreviewOnly = false;
        }
    }

    @Nullable
    private static String stillPreviewUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) return url;
        String lower = url.toLowerCase(java.util.Locale.US);
        if (lower.contains(".svga")) {
            return url.replaceAll("(?i)\\.svga", ".png");
        }
        return url;
    }

    /** Fired once when frame artwork is actually painted (avoids half-frame flash). */
    public void setOnFrameReadyListener(@Nullable Runnable listener) {
        onFrameReadyListener = listener;
    }

    private void notifyFrameReady() {
        Runnable r = onFrameReadyListener;
        if (r == null) return;
        onFrameReadyListener = null;
        post(r);
    }

    public boolean bind(@Nullable String hostBadgeUrl, @Nullable String avatarUrl,
                        @Nullable Map<String, ?> metadata, int fallbackLevel) {
        // Frame level = meta.level → URL frame-NN → fallback (never use XP level here).
        int level = metadataLevel(metadata);
        if (level <= 0) level = parseLevel(hostBadgeUrl);
        if (level <= 0 && hostBadgeUrl != null && !hostBadgeUrl.isEmpty()) {
            level = Math.max(1, Math.min(30, fallbackLevel > 0 ? fallbackLevel : 1));
        }
        if (level <= 0) {
            clearSignal();
            return false;
        }
        // Mic/profile refreshes rebind often — keep playing SVGA/PNG if URL unchanged.
        if (sameFrameUrl(boundFrameUrl, hostBadgeUrl)
                && getVisibility() == VISIBLE
                && (svgaActive || frame.getDrawable() != null || wingLeft.getDrawable() != null)) {
            this.boundLevel = level;
            this.boundMetadata = metadata;
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                avatar.setImageResource(ImagePlaceholder.avatar());
            } else {
                int avatarPx = Math.max(dp(64), Math.min(
                        avatar.getWidth() > 0 ? avatar.getWidth() : dp(96),
                        512));
                Glide.with(avatar)
                        .load(AssetCatalog.absoluteUrl(avatarUrl))
                        .thumbnail(0.2f)
                        .override(avatarPx, avatarPx)
                        .centerCrop()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .placeholder(ImagePlaceholder.avatar())
                        .error(ImagePlaceholder.avatar())
                        .into(avatar);
            }
            if (!svgaActive) startMotion();
            else resumeMotion();
            notifyFrameReady();
            return true;
        }
        final int boundLevel = level;
        final int token = ++generation;
        this.boundLevel = level;
        this.boundMetadata = metadata;
        final String previousFrameUrl = this.boundFrameUrl;
        this.boundFrameUrl = hostBadgeUrl;
        this.artworkAspect = metadataFloat(metadata, "aspectRatio", 1f);
        if (this.artworkAspect <= 0f) this.artworkAspect = 1f;
        setVisibility(VISIBLE);
        configureAccent(level);
        // Avatar + frame start in the same bind (Mikoo ModelMicView) — no post delay on frame.
        // Avatar size = view size (Mikoo HeadImageView override(thumbSize)), not fixed 72dp.
        int avatarPx = Math.max(dp(64), Math.min(
                avatar.getWidth() > 0 ? avatar.getWidth() : dp(96),
                512));
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatar.setImageResource(ImagePlaceholder.avatar());
        } else {
            Glide.with(avatar)
                    .load(AssetCatalog.absoluteUrl(avatarUrl))
                    .thumbnail(0.2f)
                    .override(avatarPx, avatarPx)
                    .centerCrop()
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .placeholder(ImagePlaceholder.avatar())
                    .error(ImagePlaceholder.avatar())
                    .into(avatar);
        }
        // Keep previous frame drawable until the new URL is ready (no hollow pop-in).
        if (!sameFrameUrl(previousFrameUrl, hostBadgeUrl)) {
            // Only clear SVGA when URL actually changes.
            clearSvgaFrame();
            // Drop wing halves immediately — mall VIP must never flash clipped half-frames.
            wingLeft.setImageDrawable(null);
            wingRight.setImageDrawable(null);
            wingLeft.setVisibility(GONE);
            wingRight.setVisibility(GONE);
            shine.setVisibility(GONE);
            halo.setVisibility(GONE);
            if (!shouldUseHostWingMotion()) {
                frame.setImageDrawable(null);
            }
        }
        int sizeHint = getWidth() > 0 && getHeight() > 0
                ? Math.min(getWidth(), getHeight())
                : dp(80);
        loadFrame(boundLevel, hostBadgeUrl, token, sizeHint);
        if (!svgaActive) startMotion();
        post(() -> {
            if (token != generation) return;
            layoutLayers();
        });
        return true;
    }

    private static boolean sameFrameUrl(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        String na = a;
        String nb = b;
        int qa = na.indexOf('?');
        int qb = nb.indexOf('?');
        if (qa >= 0) na = na.substring(0, qa);
        if (qb >= 0) nb = nb.substring(0, qb);
        return na.equals(nb);
    }

    public void clearSignal() {
        generation++;
        onFrameReadyListener = null;
        stopMotion();
        clearSvgaFrame();
        View video = stage.findViewWithTag("host_video_frame");
        if (video instanceof androidx.media3.ui.PlayerView) {
            androidx.media3.ui.PlayerView pv = (androidx.media3.ui.PlayerView) video;
            try {
                if (pv.getPlayer() != null) {
                    pv.getPlayer().release();
                    pv.setPlayer(null);
                }
            } catch (Exception ignored) {}
            stage.removeView(pv);
        }
        frame.setVisibility(VISIBLE);
        Glide.with(getContext().getApplicationContext()).clear(avatar);
        Glide.with(getContext().getApplicationContext()).clear(frame);
        Glide.with(getContext().getApplicationContext()).clear(wingLeft);
        Glide.with(getContext().getApplicationContext()).clear(wingRight);
        avatar.setImageDrawable(null);
        frame.setImageDrawable(null);
        wingLeft.setImageDrawable(null);
        wingRight.setImageDrawable(null);
        boundMetadata = null;
        boundFrameUrl = null;
        svgaActive = false;
        setVisibility(GONE);
    }

    public void destroy() {
        clearSignal();
    }

    public void pauseMotion() {
        stopMotion();
        // Keep SVGA video item; only stop playback so rebind can resume without re-decode.
        View existing = stage.findViewWithTag("host_svga_frame");
        if (existing instanceof SVGAImageView) {
            try {
                ((SVGAImageView) existing).pauseAnimation();
            } catch (Exception ignored) {
            }
        }
    }

    public void resumeMotion() {
        if (getVisibility() != VISIBLE || !isAttachedToWindow()) return;
        if (svgaActive) {
            View existing = stage.findViewWithTag("host_svga_frame");
            if (existing instanceof SVGAImageView) {
                try {
                    SVGAImageView svga = (SVGAImageView) existing;
                    if (!svga.isAnimating()) svga.startAnimation();
                } catch (Exception ignored) {
                }
            }
            return;
        }
        startMotion();
    }

    @Override protected void onDetachedFromWindow() {
        stopMotion();
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() != VISIBLE) return;
        if (svgaActive) resumeMotion();
        else startMotion();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            layoutLayers();
            if ((oldw <= 0 || oldh <= 0)
                    && getVisibility() == VISIBLE
                    && boundLevel > 0
                    && isAttachedToWindow()
                    && !svgaActive) {
                stopMotion();
                startMotion();
            }
        }
    }

    private void layoutLayers() {
        int availableWidth = getWidth();
        int availableHeight = getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) return;
        float aspect = resolvedArtworkAspect();
        // Fit-center the authored frame inside the view, then map HTML percentages
        // into that content rect so the avatar sits in the real opening.
        int stageWidth = availableWidth;
        int stageHeight = Math.round(stageWidth / aspect);
        if (stageHeight > availableHeight) {
            stageHeight = availableHeight;
            stageWidth = Math.round(stageHeight * aspect);
        }
        setCentered(stage, stageWidth, stageHeight, 0, 0);

        int levelIndex = Math.max(0, Math.min(AVATAR_OPENINGS.length - 1, boundLevel - 1));
        // Host-signal agency frames keep authored CSS openings.
        // Mikoo mall / VIP headwear: face must stay fully inside the frame hole
        // (ornate SVGA/PNG art has a smaller opening than Me-page 77.8%).
        float[] opening;
        if (!shouldUseHostWingMotion()) {
            float face = mikooMallFaceFraction();
            float inset = (1f - face) / 2f;
            opening = new float[]{inset, inset, face};
        } else {
            opening = AVATAR_OPENINGS[levelIndex];
        }
        // opening = {left, top, width} matching Host signals CSS .profile-photo
        float left = opening[0];
        float top = opening[1];
        float sizeFrac = opening[2];
        float scaleFactor = 1f;
        // Only host-signal CSS openings accept authored avatarScale tweaks.
        if (shouldUseHostWingMotion()) {
            float metaScale = metadataFloat(boundMetadata, "avatarScale", .72f);
            if (metaScale > 0f && Math.abs(metaScale - .72f) > .02f && Math.abs(metaScale - .70f) > .02f) {
                scaleFactor = clamp(metaScale / .72f, .97f, 1.05f);
            }
        }
        int avatarSize = Math.max(dp(14), Math.round(stageWidth * sizeFrac * scaleFactor));
        // Keep the circle square against the frame width basis (same as CSS width %).
        int leftPx = Math.round(left * stageWidth
                + metadataFloat(boundMetadata, "avatarOffsetX", 0f) * getResources().getDisplayMetrics().density);
        int topPx = Math.round(top * stageHeight
                + metadataFloat(boundMetadata, "avatarOffsetY", 0f) * getResources().getDisplayMetrics().density);
        // If metadata tries to push the avatar out of the opening, clamp inside.
        leftPx = Math.max(0, Math.min(stageWidth - avatarSize, leftPx));
        topPx = Math.max(0, Math.min(stageHeight - avatarSize, topPx));

        setPosition(avatar, avatarSize, avatarSize, leftPx, topPx);
        setPosition(frame, stageWidth, stageHeight, 0, 0);
        setPosition(wingLeft, stageWidth, stageHeight, 0, 0);
        setPosition(wingRight, stageWidth, stageHeight, 0, 0);
        View svga = stage.findViewWithTag("host_svga_frame");
        if (svga != null) setPosition(svga, stageWidth, stageHeight, 0, 0);
        View video = stage.findViewWithTag("host_video_frame");
        if (video != null) setPosition(video, stageWidth, stageHeight, 0, 0);
        wingLeft.setPivotX(stageWidth * .43f);
        wingLeft.setPivotY(stageHeight * .52f);
        wingRight.setPivotX(stageWidth * .57f);
        wingRight.setPivotY(stageHeight * .52f);
        int haloSize = Math.round(avatarSize * 1.36f);
        setPosition(halo, haloSize, haloSize,
                leftPx + (avatarSize - haloSize) / 2,
                topPx + (avatarSize - haloSize) / 2);
        int shineWidth = Math.max(dp(6), Math.round(stageWidth * .08f));
        int shineHeight = Math.max(dp(22), Math.round(stageHeight * .58f));
        setPosition(shine, shineWidth, shineHeight,
                Math.round(stageWidth * .12f), Math.round(stageHeight * .18f));
    }

    private void loadFrame(int level, @Nullable String remoteUrl, int token) {
        int target = Math.max(64, Math.min(
                getWidth() > 0 && getHeight() > 0 ? Math.min(getWidth(), getHeight()) : dp(80),
                512));
        loadFrame(level, remoteUrl, token, target);
    }

    private void loadFrame(int level, @Nullable String remoteUrl, int token, int sizeHint) {
        int target = Math.max(64, Math.min(sizeHint > 0 ? sizeHint : dp(80), 512));
        String path = "visual-system/host-frames/assets/" + FRAME_FILES[level - 1];
        boolean nativeOnly = remoteUrl == null || remoteUrl.isEmpty()
                || remoteUrl.startsWith("native://");
        if (nativeOnly) {
            clearSvgaFrame();
            setLocalFrame(path, target, token);
            return;
        }
        // GIF / animated WebP / video / SVGA: show media frame without cloning frozen wing bitmaps.
        boolean animated = CosmeticMedia.isAnimatedWear(remoteUrl)
                || CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.VIDEO
                || CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.SVGA;
        if (animated) {
            wingLeft.setVisibility(GONE);
            wingRight.setVisibility(GONE);
            shine.setVisibility(GONE);
            halo.setVisibility(GONE);
        }
        if (CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.SVGA) {
            if (staticPreviewOnly) {
                clearSvgaFrame();
                String png = remoteUrl.replaceAll("(?i)\\.svga(\\?.*)?$", ".png$1");
                String absPng = AssetCatalog.absoluteUrl(png);
                int size = Math.max(64, Math.min(target, 256));
                Glide.with(this)
                        .load(absPng)
                        .override(size, size)
                        .fitCenter()
                        .dontAnimate()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                        Target<Drawable> targetView,
                                                        boolean isFirstResource) {
                                // Mall: never swap Mikoo PNG for host-signal wing art.
                                post(() -> {
                                    if (token != generation) return;
                                    frame.setImageDrawable(null);
                                    frame.setVisibility(VISIBLE);
                                    notifyFrameReady();
                                });
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model,
                                                           Target<Drawable> targetView,
                                                           DataSource dataSource,
                                                           boolean isFirstResource) {
                                if (token != generation) return true;
                                applyFrameDrawable(resource);
                                updateAspectFromDrawable(resource);
                                return false;
                            }
                        })
                        .into(frame);
                return;
            }
            // Skip re-decode when same SVGA already playing (Mikoo svgPreUrl).
            if (svgaActive && sameFrameUrl(boundFrameUrl, remoteUrl)) {
                return;
            }
            clearSvgaFrame();
            bindSvgaFrame(AssetCatalog.absoluteUrl(remoteUrl), token, path, target);
            return;
        }
        if (staticPreviewOnly && (CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.GIF
                || CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.VIDEO
                || CosmeticMedia.isAnimatedWear(remoteUrl))) {
            // Still prefer PNG sibling / local frame over heavy anim in mall.
            clearSvgaFrame();
            String png = remoteUrl.replaceAll("(?i)\\.(gif|webp|mp4|webm)(\\?.*)?$", ".png$1");
            if (!png.equals(remoteUrl)) {
                remoteUrl = png;
            } else {
                setLocalFrame(path, target, token);
                return;
            }
        }
        if (CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.VIDEO) {
            clearSvgaFrame();
            bindVideoFrame(AssetCatalog.absoluteUrl(remoteUrl), token);
            return;
        }
        clearSvgaFrame();
        String abs = AssetCatalog.absoluteUrl(remoteUrl);
        int size = Math.max(64, Math.min(target, 512));
        if (CosmeticMedia.kind(remoteUrl) == CosmeticMedia.Kind.GIF) {
            Glide.with(this)
                    .asGif()
                    .load(abs)
                    .override(size, size)
                    .fitCenter()
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .listener(new RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<com.bumptech.glide.load.resource.gif.GifDrawable> t,
                                                    boolean isFirstResource) {
                            post(() -> setLocalFrame(path, target, token));
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(
                                com.bumptech.glide.load.resource.gif.GifDrawable resource,
                                Object model,
                                Target<com.bumptech.glide.load.resource.gif.GifDrawable> t,
                                DataSource dataSource,
                                boolean isFirstResource) {
                            if (token != generation) return true;
                            applyFrameDrawable(resource);
                            updateAspectFromDrawable(resource);
                            return true;
                        }
                    })
                    .into(frame);
            return;
        }
        // Static PNG stay dontAnimate; animated WebP must keep motion.
        boolean still = abs != null && abs.toLowerCase(java.util.Locale.US).endsWith(".png");
        var req = Glide.with(this)
                .load(abs)
                .override(size, size)
                .fitCenter()
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL);
        if (still) req = req.dontAnimate();
        req.listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> targetView,
                                                boolean isFirstResource) {
                        post(() -> setLocalFrame(path, target, token));
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> targetView,
                                                   DataSource dataSource,
                                                   boolean isFirstResource) {
                        if (token != generation) return true;
                        applyFrameDrawable(resource);
                        updateAspectFromDrawable(resource);
                        return false;
                    }
                })
                .into(frame);
    }

    private void bindSvgaFrame(@Nullable String absUrl, int token,
                               String fallbackPath, int target) {
        if (absUrl == null || absUrl.isEmpty()) {
            setLocalFrame(fallbackPath, target, token);
            return;
        }
        stopMotion();
        svgaActive = true;
        // One frame only: hide wings/halo so PNG placeholder never stacks with SVGA.
        wingLeft.setVisibility(GONE);
        wingRight.setVisibility(GONE);
        shine.setVisibility(GONE);
        halo.setVisibility(GONE);
        // Mikoo-fast: paint PNG sibling as placeholder, then replace (never stack) with SVGA.
        String pngSibling = absUrl.replaceAll("(?i)\\.svga(\\?.*)?$", ".png$1");
        if (!pngSibling.equals(absUrl)) {
            Glide.with(this)
                    .load(AssetCatalog.absoluteUrl(pngSibling))
                    .fitCenter()
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(frame);
            frame.setVisibility(VISIBLE);
        } else if (frame.getDrawable() == null) {
            setLocalFrame(fallbackPath, target, token);
        } else {
            frame.setVisibility(VISIBLE);
        }
        View existing = stage.findViewWithTag("host_svga_frame");
        if (existing != null) stage.removeView(existing);
        try {
            SVGAImageView svga = new SVGAImageView(getContext());
            svga.setTag("host_svga_frame");
            svga.setClearsAfterStop(false);
            svga.setLoops(-1);
            svga.setScaleType(ImageView.ScaleType.FIT_CENTER);
            svga.setLayoutParams(frame.getLayoutParams());
            // Keep SVGA invisible until ready so PNG placeholder is the only visible frame.
            svga.setAlpha(0f);
            stage.addView(svga, stage.indexOfChild(frame) + 1);
            SVGAVideoEntity cached = SVGA_CACHE.get(absUrl);
            if (cached != null) {
                svga.setVideoItem(cached);
                svga.setAlpha(1f);
                svga.startAnimation();
                frame.setImageDrawable(null);
                frame.setVisibility(GONE);
                updateAspect(1, 1);
                notifyFrameReady();
                return;
            }
            if (!SVGA_DOWNLOADS.tryAcquire()) {
                // Too many parallel SVGA downloads — keep PNG placeholder, skip decode.
                notifyFrameReady();
                return;
            }
            SVGAParser parser = new SVGAParser(getContext());
            parser.decodeFromURL(new URL(absUrl), new SVGAParser.ParseCompletion() {
                @Override
                public void onComplete(@androidx.annotation.NonNull SVGAVideoEntity videoItem) {
                    SVGA_DOWNLOADS.release();
                    try {
                        SVGA_CACHE.put(absUrl, videoItem);
                    } catch (Exception ignored) {
                    }
                    post(() -> {
                        if (token != generation) return;
                        svga.setVideoItem(videoItem);
                        svga.setAlpha(1f);
                        svga.startAnimation();
                        frame.setImageDrawable(null);
                        frame.setVisibility(GONE);
                        updateAspect(1, 1);
                        notifyFrameReady();
                    });
                }

                @Override
                public void onError() {
                    SVGA_DOWNLOADS.release();
                    post(() -> {
                        if (token != generation) return;
                        clearSvgaFrame();
                        // Fall back to PNG sibling when SVGA fails.
                        String png = absUrl.replace(".svga", ".png");
                        if (!png.equals(absUrl)) {
                            String absPng = AssetCatalog.absoluteUrl(png);
                            Glide.with(HostSignalView.this)
                                    .load(absPng)
                                    .fitCenter()
                                    .into(frame);
                            frame.setVisibility(VISIBLE);
                            startMotion();
                        } else {
                            setLocalFrame(fallbackPath, target, token);
                            startMotion();
                        }
                    });
                }
            }, null);
        } catch (Exception ignored) {
            svgaActive = false;
            frame.setVisibility(VISIBLE);
            setLocalFrame(fallbackPath, target, token);
        }
    }

    private void clearSvgaFrame() {
        View existing = stage.findViewWithTag("host_svga_frame");
        if (existing instanceof SVGAImageView) {
            try {
                ((SVGAImageView) existing).stopAnimation(true);
            } catch (Exception ignored) {}
            stage.removeView(existing);
        } else if (existing != null) {
            stage.removeView(existing);
        }
        svgaActive = false;
    }

    private void bindVideoFrame(@Nullable String absUrl, int token) {
        if (absUrl == null || absUrl.isEmpty()) return;
        frame.setImageDrawable(null);
        frame.setVisibility(GONE);
        // Reuse shine slot as video host: replace with PlayerView once.
        View existing = stage.findViewWithTag("host_video_frame");
        if (existing != null) stage.removeView(existing);
        try {
            androidx.media3.ui.PlayerView pv = new androidx.media3.ui.PlayerView(getContext());
            pv.setTag("host_video_frame");
            pv.setUseController(false);
            pv.setBackgroundColor(Color.TRANSPARENT);
            androidx.media3.exoplayer.ExoPlayer player =
                    new androidx.media3.exoplayer.ExoPlayer.Builder(getContext()).build();
            player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
            player.setVolume(0f);
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(absUrl));
            player.prepare();
            player.setPlayWhenReady(true);
            pv.setPlayer(player);
            pv.setLayoutParams(frame.getLayoutParams());
            stage.addView(pv, stage.indexOfChild(frame));
            pv.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) {
                    try {
                        player.setPlayWhenReady(false);
                        player.release();
                    } catch (Exception ignored) {}
                }
            });
            if (token == generation) {
                updateAspect(1, 1);
            }
        } catch (Exception ignored) {
            frame.setVisibility(VISIBLE);
        }
    }

    private void setLocalFrame(String path, int target, int token) {
        if (token != generation) return;
        Bitmap bitmap = decodeAsset(path, target, target);
        if (token != generation) return;
        if (bitmap != null) {
            Glide.with(getContext().getApplicationContext()).clear(frame);
            frame.setImageBitmap(bitmap);
            frame.setVisibility(VISIBLE);
            // Local host-signal assets only — wing halves are for CSS host frames.
            if (shouldUseHostWingMotion()) {
                wingLeft.setImageBitmap(bitmap);
                wingRight.setImageBitmap(bitmap);
                wingLeft.setVisibility(VISIBLE);
                wingRight.setVisibility(VISIBLE);
                shine.setVisibility(VISIBLE);
                halo.setVisibility(VISIBLE);
            } else {
                wingLeft.setImageDrawable(null);
                wingRight.setImageDrawable(null);
                wingLeft.setVisibility(GONE);
                wingRight.setVisibility(GONE);
                shine.setVisibility(GONE);
                halo.setVisibility(GONE);
            }
            updateAspect(bitmap.getWidth(), bitmap.getHeight());
            if (!svgaActive) startMotion();
            notifyFrameReady();
        } else {
            frame.setImageDrawable(null);
            wingLeft.setImageDrawable(null);
            wingRight.setImageDrawable(null);
        }
    }

    private void applyFrameDrawable(@Nullable Drawable resource) {
        frame.setImageDrawable(resource);
        if (resource == null) return;
        frame.setVisibility(VISIBLE);
        // Mall / VIP: one full frame only. Wing clips look like "half a frame"
        // stacked under the real artwork on profile during load.
        if (shouldUseHostWingMotion()) {
            wingLeft.setImageDrawable(resource.getConstantState() != null
                    ? resource.getConstantState().newDrawable().mutate()
                    : resource);
            wingRight.setImageDrawable(resource.getConstantState() != null
                    ? resource.getConstantState().newDrawable().mutate()
                    : resource);
            wingLeft.setVisibility(VISIBLE);
            wingRight.setVisibility(VISIBLE);
            shine.setVisibility(VISIBLE);
            halo.setVisibility(VISIBLE);
        } else {
            wingLeft.setImageDrawable(null);
            wingRight.setImageDrawable(null);
            wingLeft.setVisibility(GONE);
            wingRight.setVisibility(GONE);
            shine.setVisibility(GONE);
            halo.setVisibility(GONE);
        }
        if (!svgaActive) startMotion();
        notifyFrameReady();
    }

    private void updateAspectFromDrawable(@Nullable Drawable drawable) {
        if (drawable == null) return;
        updateAspect(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    private void updateAspect(int width, int height) {
        if (metadataFloat(boundMetadata, "aspectRatio", 0f) > 0f) return;
        if (width > 0 && height > 0) artworkAspect = width / (float) height;
        layoutLayers();
    }

    @Nullable
    private Bitmap decodeAsset(String path, int width, int height) {
        String key = path + ":" + width + "x" + height;
        Bitmap cached = BITMAPS.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContext().getAssets().open(path)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= width
                    && bounds.outHeight / (sample * 2) >= height) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded;
            try (InputStream input = getContext().getAssets().open(path)) {
                decoded = BitmapFactory.decodeStream(input, null, options);
            }
            if (decoded != null) BITMAPS.put(key, decoded);
            return decoded;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void configureAccent(int level) {
        int color = ACCENTS[level - 1];
        GradientDrawable glow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(color, 90), Color.TRANSPARENT});
        glow.setShape(GradientDrawable.OVAL);
        halo.setBackground(glow);
    }

    /**
     * Host-signal badges keep wing-flap. Mall PNG frames stay still —
     * only real SVGA (Mikoo headwear) animates. Fake wobble/twist was
     * making some wears look like they vibrate.
     */
    private void startMotion() {
        if (svgaActive) return;
        if (!isAttachedToWindow() || getVisibility() != VISIBLE) return;
        if (getWidth() <= 0 || getHeight() <= 0) {
            post(this::startMotion);
            return;
        }
        if (motion != null) {
            if (motion.isRunning()) return;
            motion.cancel();
            motion = null;
        }

        stopMotionLayersOnly();
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stage.setTranslationY(0f);
        stage.setTranslationX(0f);
        stage.setRotation(0f);
        stage.setScaleX(1f);
        stage.setScaleY(1f);

        // Static mall PNG / Mikoo headwear: soft pulse so every cell feels alive
        // (SVGA is disabled in mall to avoid OOM — pulse replaces it).
        if (!shouldUseHostWingMotion()) {
            long phase = Math.floorMod(
                    (boundFrameUrl != null ? boundFrameUrl.hashCode() : boundLevel) * 97L, 900L);
            motion = motionPulse(phase);
            motion.start();
            return;
        }

        long phase = Math.floorMod(
                (boundFrameUrl != null ? boundFrameUrl.hashCode() : boundLevel) * 131L, 720L);
        motion = motionWingFlap(phase);
        motion.start();
    }

    /**
     * Face diameter as fraction of frame for Mikoo mall / VIP PNG-SVGA wears.
     * Keeps the profile photo fully inside the decorative opening.
     */
    private float mikooMallFaceFraction() {
        float fromMeta = metadataFloat(boundMetadata, "avatarScale", 0f);
        if (fromMeta > 0.35f && fromMeta < 0.92f) {
            // Treat authored avatarScale as face÷frame (same units as HostSignal opening width).
            return clamp(fromMeta, 0.48f, 0.78f);
        }
        float faceRatio = metadataFloat(boundMetadata, "faceRatio", 0f);
        if (faceRatio > 0.35f && faceRatio < 0.92f) {
            return clamp(faceRatio, 0.48f, 0.78f);
        }
        // Ornate Mikoo headwear holes are smaller than Me-page 77.8% VIP medals.
        return 0.62f;
    }

    /** True only for Host-signals CSS frames (wing artwork), not Mikoo mall PNGs. */
    private boolean shouldUseHostWingMotion() {
        if (boundFrameUrl == null || boundFrameUrl.isEmpty()) return true;
        String lower = boundFrameUrl.toLowerCase(java.util.Locale.US);
        if (lower.contains(".svga")) return false;
        if (lower.contains("/cosmetics/frames/") || lower.contains("frame_mikoo_")) return false;
        return lower.contains("host-frames")
                || lower.contains("host_signal")
                || lower.startsWith("native://")
                || (lower.contains("frame-") && lower.contains("transparent"));
    }

    /** @deprecated kept for call-site clarity; wing flap is the only host motion. */
    private int motionStyleForFrame() {
        return 0;
    }

    private AnimatorSet motionWingFlap(long phase) {
        long wingMs = 1650L;
        long breathMs = 2300L;
        long shineMs = 4200L;
        wingLeft.setVisibility(VISIBLE);
        wingRight.setVisibility(VISIBLE);
        shine.setVisibility(VISIBLE);
        halo.setVisibility(VISIBLE);

        ObjectAnimator wingL = looping(wingLeft, View.ROTATION, wingMs, -1.2f, -8.5f, -1.2f);
        ObjectAnimator wingLX = looping(wingLeft, View.TRANSLATION_X, wingMs, 0f, -dp(4), 0f);
        ObjectAnimator wingLY = looping(wingLeft, View.TRANSLATION_Y, wingMs, 0f, -dp(3), 0f);
        ObjectAnimator wingLS = looping(wingLeft, View.SCALE_X, wingMs, 1f, 1.03f, 1f);
        ObjectAnimator wingR = looping(wingRight, View.ROTATION, wingMs, 1.2f, 8.5f, 1.2f);
        ObjectAnimator wingRX = looping(wingRight, View.TRANSLATION_X, wingMs, 0f, dp(4), 0f);
        ObjectAnimator wingRY = looping(wingRight, View.TRANSLATION_Y, wingMs, 0f, -dp(3), 0f);
        ObjectAnimator wingRS = looping(wingRight, View.SCALE_X, wingMs, 1f, 1.03f, 1f);
        ObjectAnimator pulseX = looping(halo, View.SCALE_X, breathMs, .96f, 1.06f, .96f);
        ObjectAnimator pulseY = looping(halo, View.SCALE_Y, breathMs, .96f, 1.06f, .96f);
        ObjectAnimator haloAlpha = looping(halo, View.ALPHA, breathMs, .40f, 1f, .40f);
        float sweepDistance = Math.max(dp(48), stage.getWidth() * 1.35f);
        ObjectAnimator sweep = looping(shine, View.TRANSLATION_X, shineMs,
                -sweepDistance * .35f, sweepDistance * .15f, sweepDistance, sweepDistance);
        ObjectAnimator sweepAlpha = looping(shine, View.ALPHA, shineMs, 0f, 0f, .9f, 0f, 0f);
        PathInterpolator wingEase = new PathInterpolator(.45f, .05f, .55f, .95f);
        wingL.setInterpolator(wingEase);
        wingR.setInterpolator(wingEase);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(wingL, wingLX, wingLY, wingLS, wingR, wingRX, wingRY, wingRS,
                pulseX, pulseY, haloAlpha, sweep, sweepAlpha);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.setStartDelay(phase);
        return set;
    }

    /** Soft heartbeat scale — common on Mikoo VIP / mall headwear. */
    private AnimatorSet motionPulse(long phase) {
        hideWingsKeepFrame();
        shine.setVisibility(GONE);
        halo.setVisibility(VISIBLE);
        // Keep pulse subtle in mall grid cells so the face stays readable.
        long ms = 2000L;
        ObjectAnimator sx = looping(stage, View.SCALE_X, ms, 1f, 1.035f, 1f);
        ObjectAnimator sy = looping(stage, View.SCALE_Y, ms, 1f, 1.035f, 1f);
        ObjectAnimator hx = looping(halo, View.SCALE_X, ms, .94f, 1.08f, .94f);
        ObjectAnimator hy = looping(halo, View.SCALE_Y, ms, .94f, 1.08f, .94f);
        ObjectAnimator ha = looping(halo, View.ALPHA, ms, .30f, .85f, .30f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, hx, hy, ha);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.setStartDelay(phase);
        return set;
    }

    /** Gentle left-right twist (فتلة خفيفة). */
    private AnimatorSet motionWobble(long phase) {
        hideWingsKeepFrame();
        shine.setVisibility(VISIBLE);
        halo.setVisibility(VISIBLE);
        long ms = 2200L;
        ObjectAnimator rot = looping(stage, View.ROTATION, ms, -5.5f, 5.5f, -5.5f);
        ObjectAnimator sx = looping(stage, View.SCALE_X, ms, 1f, 1.03f, 1f);
        ObjectAnimator sy = looping(stage, View.SCALE_Y, ms, 1f, 1.03f, 1f);
        ObjectAnimator ha = looping(halo, View.ALPHA, ms, .45f, 1f, .45f);
        float sweepDistance = Math.max(dp(40), stage.getWidth() * 1.2f);
        ObjectAnimator sweep = looping(shine, View.TRANSLATION_X, ms * 2,
                -sweepDistance * .3f, sweepDistance * .2f, sweepDistance);
        ObjectAnimator sweepA = looping(shine, View.ALPHA, ms * 2, 0f, .75f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(rot, sx, sy, ha, sweep, sweepA);
        set.setInterpolator(new PathInterpolator(.4f, .0f, .2f, 1f));
        set.setStartDelay(phase);
        return set;
    }

    /** Slow breathe + soft wing tips. */
    private AnimatorSet motionBreathe(long phase) {
        wingLeft.setVisibility(VISIBLE);
        wingRight.setVisibility(VISIBLE);
        shine.setVisibility(GONE);
        halo.setVisibility(VISIBLE);
        long ms = 2800L;
        ObjectAnimator sx = looping(stage, View.SCALE_X, ms, .98f, 1.045f, .98f);
        ObjectAnimator sy = looping(stage, View.SCALE_Y, ms, .98f, 1.045f, .98f);
        ObjectAnimator wl = looping(wingLeft, View.ROTATION, ms, -2f, -6f, -2f);
        ObjectAnimator wr = looping(wingRight, View.ROTATION, ms, 2f, 6f, 2f);
        ObjectAnimator hx = looping(halo, View.SCALE_X, ms, .94f, 1.08f, .94f);
        ObjectAnimator hy = looping(halo, View.SCALE_Y, ms, .94f, 1.08f, .94f);
        ObjectAnimator ha = looping(halo, View.ALPHA, ms, .3f, .9f, .3f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, wl, wr, hx, hy, ha);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.setStartDelay(phase);
        return set;
    }

    /** Shine sweep + tiny pulse. */
    private AnimatorSet motionShimmer(long phase) {
        hideWingsKeepFrame();
        shine.setVisibility(VISIBLE);
        halo.setVisibility(VISIBLE);
        long ms = 1600L;
        long shineMs = 3200L;
        ObjectAnimator sx = looping(stage, View.SCALE_X, ms, 1f, 1.035f, 1f);
        ObjectAnimator sy = looping(stage, View.SCALE_Y, ms, 1f, 1.035f, 1f);
        float sweepDistance = Math.max(dp(56), stage.getWidth() * 1.45f);
        ObjectAnimator sweep = looping(shine, View.TRANSLATION_X, shineMs,
                -sweepDistance * .4f, -sweepDistance * .1f, sweepDistance * .2f, sweepDistance);
        ObjectAnimator sweepA = looping(shine, View.ALPHA, shineMs, 0f, 0f, 1f, 0f, 0f);
        ObjectAnimator ha = looping(halo, View.ALPHA, ms, .5f, 1f, .5f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, sweep, sweepA, ha);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.setStartDelay(phase);
        return set;
    }

    /** Quick twist + tip flap. */
    private AnimatorSet motionTwist(long phase) {
        wingLeft.setVisibility(VISIBLE);
        wingRight.setVisibility(VISIBLE);
        shine.setVisibility(GONE);
        halo.setVisibility(VISIBLE);
        long ms = 1400L;
        ObjectAnimator rot = looping(stage, View.ROTATION, ms, -3.5f, 3.5f, -3.5f);
        ObjectAnimator wl = looping(wingLeft, View.ROTATION, ms, -1f, -10f, -1f);
        ObjectAnimator wr = looping(wingRight, View.ROTATION, ms, 1f, 10f, 1f);
        ObjectAnimator wlX = looping(wingLeft, View.TRANSLATION_X, ms, 0f, -dp(5), 0f);
        ObjectAnimator wrX = looping(wingRight, View.TRANSLATION_X, ms, 0f, dp(5), 0f);
        ObjectAnimator ha = looping(halo, View.ALPHA, ms, .4f, 1f, .4f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(rot, wl, wr, wlX, wrX, ha);
        set.setInterpolator(new PathInterpolator(.45f, .05f, .55f, .95f));
        set.setStartDelay(phase);
        return set;
    }

    private void hideWingsKeepFrame() {
        wingLeft.setVisibility(INVISIBLE);
        wingRight.setVisibility(INVISIBLE);
        resetWing(wingLeft);
        resetWing(wingRight);
    }

    private void stopMotionLayersOnly() {
        if (motion != null) {
            motion.cancel();
            motion = null;
        }
        resetWing(wingLeft);
        resetWing(wingRight);
        halo.setScaleX(1f);
        halo.setScaleY(1f);
        halo.setAlpha(1f);
        shine.setTranslationX(0f);
        shine.setAlpha(0f);
    }

    private void stopMotion() {
        stopMotionLayersOnly();
        stage.setLayerType(View.LAYER_TYPE_NONE, null);
        stage.setTranslationX(0f);
        stage.setTranslationY(0f);
        stage.setRotation(0f);
        stage.setScaleX(1f);
        stage.setScaleY(1f);
    }

    private ObjectAnimator looping(View target, android.util.Property<View, Float> property,
                                   long duration, float... values) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, property, values);
        animator.setDuration(duration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        return animator;
    }

    private void resetWing(View wing) {
        wing.setTranslationX(0f);
        wing.setTranslationY(0f);
        wing.setRotation(0f);
        wing.setScaleX(1f);
        wing.setScaleY(1f);
    }

    private void setCentered(View view, int width, int height, int left, int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        params.leftMargin = left;
        params.topMargin = top;
        view.setLayoutParams(params);
    }

    private void setPosition(View view, int width, int height, int left, int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        // Frame artwork coordinates are authored in physical left/top space.
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = left;
        params.topMargin = top;
        view.setLayoutParams(params);
    }

    private float resolvedArtworkAspect() {
        return clamp(artworkAspect, .45f, 2.4f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private GradientDrawable circle(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parseLevel(@Nullable String url) {
        if (url == null) return 0;
        Matcher matcher = LEVEL.matcher(url);
        if (!matcher.find()) return 0;
        try {
            return Math.max(1, Math.min(30, Integer.parseInt(matcher.group(1))));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int metadataLevel(@Nullable Map<String, ?> metadata) {
        if (metadata == null) return 0;
        Object raw = metadata.get("level");
        if (raw instanceof Number) {
            return Math.max(1, Math.min(30, ((Number) raw).intValue()));
        }
        try {
            return raw != null
                    ? Math.max(1, Math.min(30, Integer.parseInt(String.valueOf(raw))))
                    : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static float metadataFloat(@Nullable Map<String, ?> metadata,
                                       String key, float fallback) {
        if (metadata == null) return fallback;
        Object raw = metadata.get(key);
        if (raw instanceof Number) return ((Number) raw).floatValue();
        if (raw instanceof String && ((String) raw).contains(":")) {
            String[] parts = ((String) raw).split(":");
            if (parts.length == 2) {
                try {
                    float right = Float.parseFloat(parts[1].trim());
                    return right == 0f ? fallback
                            : Float.parseFloat(parts[0].trim()) / right;
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        try {
            return raw != null ? Float.parseFloat(String.valueOf(raw)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Clips the frame artwork to left/right wing polygons matching CSS clip-path. */
    private static final class ClippedWingImageView extends AppCompatImageView {
        private final boolean left;
        private final Path clipPath = new Path();

        ClippedWingImageView(Context context, boolean left) {
            super(context);
            this.left = left;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            clipPath.reset();
            if (w <= 0 || h <= 0) return;
            if (left) {
                // polygon(0 14%, 31% 13%, 45% 28%, 35% 42%, 46% 56%, 34% 82%, 0 86%)
                clipPath.moveTo(0, h * .14f);
                clipPath.lineTo(w * .31f, h * .13f);
                clipPath.lineTo(w * .45f, h * .28f);
                clipPath.lineTo(w * .35f, h * .42f);
                clipPath.lineTo(w * .46f, h * .56f);
                clipPath.lineTo(w * .34f, h * .82f);
                clipPath.lineTo(0, h * .86f);
            } else {
                // polygon(100% 14%, 69% 13%, 55% 28%, 65% 42%, 54% 56%, 66% 82%, 100% 86%)
                clipPath.moveTo(w, h * .14f);
                clipPath.lineTo(w * .69f, h * .13f);
                clipPath.lineTo(w * .55f, h * .28f);
                clipPath.lineTo(w * .65f, h * .42f);
                clipPath.lineTo(w * .54f, h * .56f);
                clipPath.lineTo(w * .66f, h * .82f);
                clipPath.lineTo(w, h * .86f);
            }
            clipPath.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(clipPath);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
