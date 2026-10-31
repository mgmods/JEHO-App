package com.Dramizo.Series.util;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;

import java.util.Map;

/** Applies the public VIP account-name treatment supplied by the server. */
public final class VipNameStyle {
    private VipNameStyle() {}

    public static void bind(@Nullable TextView view, @Nullable AuthDtos.UserDto user) {
        bind(view, user == null ? null : user.nameStyle);
    }

    public static void bind(@Nullable TextView view, @Nullable Map<String, Object> style) {
        if (view == null) return;
        Object previous = view.getTag(R.id.tag_vip_name_animator);
        if (previous instanceof ValueAnimator) {
            ((ValueAnimator) previous).cancel();
            view.setTag(R.id.tag_vip_name_animator, null);
        }
        view.getPaint().setShader(null);
        view.setAlpha(1f);
        view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        if (style == null || !asBoolean(style.get("enabled"))) return;

        int c1 = color(style.get("color1"), 0xFFD946EF);
        int c2 = color(style.get("color2"), 0xFF8B5CF6);
        int c3 = color(style.get("color3"), 0xFF22D3EE);
        String mode = string(style.get("mode"), "solid");
        String animation = string(style.get("animation"), "rainbow");
        boolean glow = asBoolean(style.get("glow"));
        float density = view.getResources().getDisplayMetrics().density;
        if (glow) {
            view.setShadowLayer(5f * density, 0f, 0f, c2);
        }

        if ("solid".equals(mode)) {
            view.setTextColor(c1);
            return;
        }

        int[] colors = new int[]{c1, c2, c3};
        if ("animated".equals(mode)) {
            if ("pulse".equals(animation)) {
                view.setTextColor(c1);
                ValueAnimator pulse = ValueAnimator.ofFloat(0.72f, 1f);
                pulse.setDuration(duration(style));
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(a -> view.setAlpha((Float) a.getAnimatedValue()));
                view.setTag(R.id.tag_vip_name_animator, pulse);
                pulse.start();
                return;
            }
            ValueAnimator motion = ValueAnimator.ofFloat(0f, 1f);
            motion.setDuration(duration(style));
            motion.setRepeatCount(ValueAnimator.INFINITE);
            motion.addUpdateListener(a -> {
                int width = Math.max(1, view.getWidth());
                LinearGradient shader = new LinearGradient(
                        0f, 0f, width * 1.8f, 0f, colors, null, Shader.TileMode.CLAMP);
                Matrix matrix = new Matrix();
                float phase = (Float) a.getAnimatedValue();
                matrix.setTranslate(-width * 1.8f + phase * width * 3.6f, 0f);
                shader.setLocalMatrix(matrix);
                view.getPaint().setShader(shader);
                view.invalidate();
            });
            view.setTag(R.id.tag_vip_name_animator, motion);
            motion.start();
            return;
        }

        LinearGradient gradient = new LinearGradient(
                0f, 0f, Math.max(1, view.getWidth()), 0f,
                colors, null, Shader.TileMode.CLAMP);
        view.getPaint().setShader(gradient);
        view.invalidate();
    }

    private static long duration(Map<String, Object> style) {
        double speed = 1d;
        try { speed = Double.parseDouble(String.valueOf(style.get("speed"))); }
        catch (Exception ignored) { }
        speed = Math.max(0.6d, Math.min(1.8d, speed));
        return Math.max(1200L, Math.round(3200d * speed));
    }

    private static boolean asBoolean(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String string(Object value, String fallback) {
        String s = value == null ? "" : String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static int color(Object value, int fallback) {
        try { return Color.parseColor(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }
}
