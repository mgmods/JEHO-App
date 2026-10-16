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
import java.util.concurrent.ConcurrentHashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** One shared ID-label renderer: dashboard style for owned special IDs, animated VIP treatment otherwise. */
public final class VanityIdStyle {
    private static final Map<String, VanityDtos.Style> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private VanityIdStyle() {}

    public static void bind(@Nullable TextView view, @Nullable VanityApi api,
                            @Nullable String publicId, int vipLevel) {
        if (view == null) return;
        if (publicId == null || publicId.trim().isEmpty()) {
            view.getPaint().setShader(null);
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            return;
        }
        final String id = publicId.trim();
        view.setTag(R.id.tag_vanity_id_style_key, id);
        if (vipLevel > 0) {
            apply(view, id, new int[]{Color.rgb(255, 224, 117), Color.rgb(255, 105, 190), Color.rgb(116, 232, 255)}, true, true);
            return;
        }
        VanityDtos.Style cached = CACHE.get(id);
        if (cached != null) {
            if ("none".equals(cached.styleMode)) clear(view);
            else apply(view, id, colors(cached), "animated".equals(cached.styleMode), cached.styleGlow);
            return;
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
                Object current = view.getTag(R.id.tag_vanity_id_style_key);
                if (!id.equals(current)) return;
                if ("none".equals(style.styleMode)) clear(view);
                else apply(view, id, colors(style), "animated".equals(style.styleMode), style.styleGlow);
            }
            @Override public void onFailure(Call<ApiResponse<VanityDtos.Style>> call, Throwable error) { LOADING.remove(id); }
        });
    }

    private static int[] colors(VanityDtos.Style style) {
        return new int[]{color(style.styleColor1, 0xFF52E8FF), color(style.styleColor2, 0xFFC084FC), color(style.styleColor3, 0xFFFF7AC8)};
    }
    private static int color(String value, int fallback) {
        try { return value == null ? fallback : Color.parseColor(value); } catch (Exception ignored) { return fallback; }
    }
    private static void clear(TextView view) {
        view.getPaint().setShader(null);
        view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        view.invalidate();
    }
    private static void apply(TextView view, String id, int[] colors, boolean animated, boolean glow) {
        view.setShadowLayer(glow ? 7f : 0f, 0f, 0f, colors[1]);
        if (!animated) {
            view.getPaint().setShader(new LinearGradient(0, 0, Math.max(1, view.getWidth()), 0, colors, null, Shader.TileMode.CLAMP));
            view.invalidate();
            view.post(view::invalidate);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(a -> {
            if (!id.equals(view.getTag(R.id.tag_vanity_id_style_key)) || !view.isAttachedToWindow()) { a.cancel(); return; }
            float f = (float) a.getAnimatedValue();
            float width = Math.max(1f, view.getWidth());
            view.getPaint().setShader(new LinearGradient(-width * f, 0f, width * (1f - f) + width, 0f, colors, null, Shader.TileMode.MIRROR));
            view.invalidate();
        });
        view.post(animator::start);
    }
}
