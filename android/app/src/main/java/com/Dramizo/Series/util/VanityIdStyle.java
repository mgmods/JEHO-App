package com.Dramizo.Series.util;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.View;
import android.widget.TextView;
import android.animation.ValueAnimator;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.VanityApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.VanityDtos;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** One shared ID-label renderer: dashboard style for owned special IDs, animated VIP treatment otherwise. */
public final class VanityIdStyle {
    private static final Map<String, VanityDtos.Style> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHE_AT = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60_000L;
    // Recycled ID views must never accumulate multiple infinite shimmer animators.
    private static final Map<TextView, ValueAnimator> ANIMATORS = new WeakHashMap<>();
    private VanityIdStyle() {}

    public static void bind(@Nullable TextView view, @Nullable VanityApi api,
                            @Nullable String publicId, int vipLevel) {
        if (view == null) return;
        cancelAnimation(view);
        if (publicId == null || publicId.trim().isEmpty()) {
            view.setTag(R.id.tag_vanity_id_style_key, null);
            view.getPaint().setShader(null);
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            view.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }

        final String id = publicId.trim();
        view.setTag(R.id.tag_vanity_id_style_key, id);
        VanityDtos.Style cached = CACHE.get(id);
        Long cachedAt = CACHE_AT.get(id);
        boolean fresh = cached != null && cachedAt != null
                && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS;

        // Stale-while-revalidate: keep the last known style on screen while refreshing.
        // This avoids IDs flashing unstyled while the API responds.
        if (cached != null) {
            applyStyle(view, id, cached, vipLevel);
            if (fresh) return;
        } else if (vipLevel > 0) {
            // Render a lightweight VIP fallback immediately; special dashboard styles can replace it.
            apply(view, id, vipColors(vipLevel), false, false);
        }

        if (api == null || !id.matches("\\d{3,16}")) return;
        if (LOADING.putIfAbsent(id, Boolean.TRUE) != null) return;
        api.style(id).enqueue(new Callback<ApiResponse<VanityDtos.Style>>() {
            @Override public void onResponse(Call<ApiResponse<VanityDtos.Style>> call, Response<ApiResponse<VanityDtos.Style>> response) {
                LOADING.remove(id);
                ApiResponse<VanityDtos.Style> body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null) return;
                VanityDtos.Style style = body.data;
                CACHE.put(id, style);
                CACHE_AT.put(id, System.currentTimeMillis());
                Object current = view.getTag(R.id.tag_vanity_id_style_key);
                if (!id.equals(current)) return;
                applyStyle(view, id, style, vipLevel);
            }
            @Override public void onFailure(Call<ApiResponse<VanityDtos.Style>> call, Throwable error) {
                LOADING.remove(id);
            }
        });
    }

    private static void applyStyle(TextView view, String id, VanityDtos.Style style, int vipLevel) {
        ServerAssets.loadCompoundStart(view, resolveIconUrl(style.styleIconUrl), 14);
        if ("none".equals(style.styleMode)) clear(view);
        else if ("vip".equals(style.styleMode)) {
            int level = style.styleVipLevel > 0 ? style.styleVipLevel : vipLevel;
            apply(view, id, vipColors(level), false, style.styleGlow);
        } else {
            apply(view, id, colors(style), "animated".equals(style.styleMode), style.styleGlow);
        }
    }

    private static void cancelAnimation(TextView view) {
        ValueAnimator old;
        synchronized (ANIMATORS) {
            old = ANIMATORS.remove(view);
        }
        if (old != null) {
            old.cancel();
        }
    }

    private static String resolveIconUrl(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String url = value.trim();
        if (url.startsWith("https://") || url.startsWith("http://")
                || url.startsWith("/api/v1/") || url.startsWith("/uploads/")) return url;
        if (url.startsWith("uploads/")) return "/" + url;
        return url;
    }

    private static int[] vipColors(int level) {
        switch (Math.max(1, Math.min(7, level))) {
            case 1: return new int[]{0xFFB6C4D2, 0xFFB6C4D2, 0xFFEEF3F7};
            case 2: return new int[]{0xFF68C8FF, 0xFF68C8FF, 0xFFB9E9FF};
            case 3: return new int[]{0xFF65E6B4, 0xFF65E6B4, 0xFFB7FFE2};
            case 4: return new int[]{0xFFB28AFF, 0xFFB28AFF, 0xFFE0D0FF};
            case 5: return new int[]{0xFFFF9A52, 0xFFFF9A52, 0xFFFFD1A9};
            case 6: return new int[]{0xFFFFD66B, 0xFFFFD66B, 0xFFFFF0B7};
            default: return new int[]{0xFFFF6AA9, 0xFFFF6AA9, 0xFFFFC3DC};
        }
    }

    private static int[] colors(VanityDtos.Style style) {
        return new int[]{color(style.styleColor1, 0xFF52E8FF), color(style.styleColor2, 0xFFC084FC), color(style.styleColor3, 0xFFFF7AC8)};
    }
    private static int color(String value, int fallback) {
        try { return value == null ? fallback : Color.parseColor(value); } catch (Exception ignored) { return fallback; }
    }
    private static void clear(TextView view) {
        cancelAnimation(view);
        view.getPaint().setShader(null);
        view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        view.invalidate();
    }
    private static void apply(TextView view, String id, int[] colors, boolean animated, boolean glow) {
        cancelAnimation(view);
        view.setShadowLayer(glow ? 7f : 0f, 0f, 0f, colors[1]);
        if (!animated) {
            // The profile screen can bind the style before the TextView has completed layout.
            // Rebuild the gradient after measurement so static/colored IDs do not collapse to
            // a near-solid color because their first measured width was zero.
            view.post(() -> {
                if (!id.equals(view.getTag(R.id.tag_vanity_id_style_key)) || !view.isAttachedToWindow()) return;
                float width = Math.max(1f, view.getWidth());
                view.getPaint().setShader(new LinearGradient(0f, 0f, width, 0f, colors, null, Shader.TileMode.CLAMP));
                view.invalidate();
            });
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(a -> {
            if (!id.equals(view.getTag(R.id.tag_vanity_id_style_key)) || !view.isAttachedToWindow()) {
                cancelAnimation(view);
                return;
            }
            float f = (float) a.getAnimatedValue();
            float width = Math.max(1f, view.getWidth());
            view.getPaint().setShader(new LinearGradient(-width * f, 0f, width * (1f - f) + width, 0f, colors, null, Shader.TileMode.MIRROR));
            view.invalidate();
        });
        synchronized (ANIMATORS) {
            ANIMATORS.put(view, animator);
        }
        view.post(() -> {
            boolean current;
            synchronized (ANIMATORS) {
                current = ANIMATORS.get(view) == animator;
            }
            if (!current) {
                animator.cancel();
            } else if (id.equals(view.getTag(R.id.tag_vanity_id_style_key))
                    && view.isAttachedToWindow()) {
                animator.start();
            } else {
                cancelAnimation(view);
            }
        });
    }
}
