package com.Dramizo.Series.util;



import android.net.Uri;

import android.view.View;

import android.widget.ImageView;



import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;



import com.airbnb.lottie.LottieAnimationView;

import com.airbnb.lottie.LottieCompositionFactory;

import com.bumptech.glide.Glide;



import java.util.Locale;

import java.util.concurrent.atomic.AtomicInteger;



/**

 * Gift overlay player — auto-detects PNG/JPG, Lottie JSON, or MP4 from URL extension.

 * TikTok-style full-screen effect; no JSON required for static image gifts.

 */

@OptIn(markerClass = UnstableApi.class)
public final class GiftPlayer {

    public static final long OVERLAY_DURATION_MS = 4000L;



    private static final AtomicInteger PLAY_GEN = new AtomicInteger();

    @Nullable private static ExoPlayer videoPlayer;



    public enum MediaKind { NONE, IMAGE, LOTTIE, VIDEO }



    private GiftPlayer() {}



    @Nullable

    public static MediaKind detectKind(@Nullable String urlOrPath) {

        if (urlOrPath == null || urlOrPath.isEmpty()) return MediaKind.NONE;

        String s = stripQuery(urlOrPath).toLowerCase(Locale.US);

        if (s.endsWith(".json")) return MediaKind.LOTTIE;

        if (s.endsWith(".mp4") || s.endsWith(".mov") || s.endsWith(".webm")) return MediaKind.VIDEO;

        if (s.endsWith(".png") || s.endsWith(".webp") || s.endsWith(".jpg")

                || s.endsWith(".jpeg") || s.endsWith(".gif")) return MediaKind.IMAGE;

        return MediaKind.NONE;

    }



    public static boolean looksLikeLottie(@Nullable String urlOrPath) {

        return detectKind(urlOrPath) == MediaKind.LOTTIE;

    }



    private static String stripQuery(String url) {

        int q = url.indexOf('?');

        return q >= 0 ? url.substring(0, q) : url;

    }



    public static boolean play(

            LottieAnimationView lottie,

            ImageView iconView,

            @Nullable PlayerView videoView,

            @Nullable String giftName,

            @Nullable String iconUrl,

            @Nullable String animationUrl,

            int placeholderRes) {

        if (lottie == null || iconView == null) return false;

        stop(lottie, iconView, videoView);



        final int gen = PLAY_GEN.incrementAndGet();

        String remoteAnim = AssetCatalog.absoluteUrl(animationUrl);

        String remoteIcon = AssetCatalog.absoluteUrl(iconUrl);

        MediaKind animKind = detectKind(remoteAnim);

        MediaKind iconKind = detectKind(remoteIcon);



        // Prefer dedicated animation asset; fall back to icon-only image gifts.

        String effectUrl = remoteAnim;

        MediaKind effectKind = animKind;

        if (effectKind == MediaKind.NONE && iconKind == MediaKind.IMAGE) {

            effectUrl = remoteIcon;

            effectKind = MediaKind.IMAGE;

        }



        if (effectKind == MediaKind.VIDEO && videoView != null && effectUrl != null) {

            playVideo(videoView, iconView, lottie, effectUrl, remoteIcon, placeholderRes, gen);

            return true;

        }

        if (effectKind == MediaKind.LOTTIE && effectUrl != null

                && (effectUrl.startsWith("http://") || effectUrl.startsWith("https://"))) {

            showIcon(iconView, remoteIcon != null ? remoteIcon : effectUrl, placeholderRes);

            playLottie(lottie, iconView, effectUrl, remoteIcon, placeholderRes, gen);

            return true;

        }

        if (effectKind == MediaKind.IMAGE && effectUrl != null) {

            showIcon(iconView, effectUrl, placeholderRes);

            iconView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(320).start();

            return false;

        }

        if (remoteIcon != null && !remoteIcon.isEmpty()) {

            showIcon(iconView, remoteIcon, placeholderRes);

            return false;

        }

        return false;

    }



    /** Backward-compatible call without video view. */

    public static boolean play(

            LottieAnimationView lottie,

            ImageView iconView,

            @Nullable String giftName,

            @Nullable String iconUrl,

            @Nullable String animationUrl,

            int placeholderRes) {

        return play(lottie, iconView, null, giftName, iconUrl, animationUrl, placeholderRes);

    }



    private static void playLottie(LottieAnimationView lottie, ImageView iconView, String remoteAnim,

                                   @Nullable String remoteIcon, int placeholderRes, int gen) {

        lottie.setVisibility(View.INVISIBLE);

        LottieCompositionFactory.fromUrl(lottie.getContext(), remoteAnim)

                .addListener(composition -> {

                    if (gen != PLAY_GEN.get() || composition == null) return;

                    try {

                        lottie.setVisibility(View.VISIBLE);

                        lottie.setComposition(composition);

                        lottie.setRepeatCount(0);

                        lottie.playAnimation();

                        iconView.setVisibility(View.VISIBLE);

                        iconView.setAlpha(0.35f);

                    } catch (Exception ignored) {

                        lottie.setVisibility(View.GONE);

                        showIcon(iconView, remoteIcon, placeholderRes);

                    }

                })

                .addFailureListener(result -> {

                    if (gen != PLAY_GEN.get()) return;

                    lottie.setVisibility(View.GONE);

                    showIcon(iconView, remoteIcon, placeholderRes);

                });

    }



    private static void playVideo(PlayerView videoView, ImageView iconView, LottieAnimationView lottie,

                                  String videoUrl, @Nullable String remoteIcon, int placeholderRes, int gen) {

        lottie.setVisibility(View.GONE);

        iconView.setVisibility(View.GONE);

        videoView.setVisibility(View.VISIBLE);

        releaseVideoPlayer();

        videoPlayer = new ExoPlayer.Builder(videoView.getContext()).build();

        videoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);

        videoView.setPlayer(videoPlayer);

        videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));

        videoPlayer.prepare();

        videoPlayer.play();

        videoPlayer.addListener(new Player.Listener() {

            @Override

            public void onPlaybackStateChanged(int state) {

                if (gen != PLAY_GEN.get()) return;

                if (state == Player.STATE_ENDED) {

                    videoView.setVisibility(View.GONE);

                    if (remoteIcon != null) showIcon(iconView, remoteIcon, placeholderRes);

                }

            }

        });

    }



    private static void showIcon(ImageView iconView, @Nullable String url, int placeholderRes) {

        iconView.setVisibility(View.VISIBLE);

        iconView.setScaleX(0.6f);

        iconView.setScaleY(0.6f);

        iconView.setAlpha(0f);

        // Glide animates GIF/WebP automatically when loaded into ImageView.
        Glide.with(iconView.getContext().getApplicationContext())
                .load(url)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(iconView);

        iconView.animate().cancel();

        iconView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start();

    }



    public static void stop(LottieAnimationView lottie, ImageView iconView) {

        stop(lottie, iconView, null);

    }



    public static void stop(LottieAnimationView lottie, ImageView iconView, @Nullable PlayerView videoView) {

        PLAY_GEN.incrementAndGet();

        releaseVideoPlayer();

        if (videoView != null) {

            videoView.setPlayer(null);

            videoView.setVisibility(View.GONE);

        }

        if (lottie != null) {

            try { lottie.cancelAnimation(); } catch (Exception ignored) {}

            lottie.setProgress(0f);

            lottie.setVisibility(View.GONE);

        }

        if (iconView != null) {

            GiftBurstAnimator.stop(iconView);

            iconView.animate().cancel();

            iconView.setRotation(0f);

            iconView.setTranslationY(0f);

            iconView.setVisibility(View.GONE);

            iconView.setAlpha(1f);

            iconView.setScaleX(1f);

            iconView.setScaleY(1f);

        }

    }



    private static void releaseVideoPlayer() {

        if (videoPlayer != null) {

            try {

                videoPlayer.stop();

                videoPlayer.release();

            } catch (Exception ignored) {}

            videoPlayer = null;

        }

    }

}


