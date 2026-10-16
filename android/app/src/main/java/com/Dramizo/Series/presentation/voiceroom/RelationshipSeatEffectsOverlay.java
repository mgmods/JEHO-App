package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decorative CP relationship effects. This class only renders views; it never touches RTC/audio state. */
final class RelationshipSeatEffectsOverlay {
    private final Context context;
    private final ViewGroup root;
    private final RecyclerView seatsRecycler;
    private final FrameLayout layer;
    private final List<AnimatorSet> running = new ArrayList<>();

    RelationshipSeatEffectsOverlay(@NonNull Context context, @NonNull ViewGroup root,
                                   @NonNull RecyclerView seatsRecycler) {
        this.context = context;
        this.root = root;
        this.seatsRecycler = seatsRecycler;
        layer = new FrameLayout(context);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setElevation(dp(20));
        if (root instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            root.addView(layer, lp);
        } else {
            root.addView(layer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    void render(@Nullable List<RoomDtos.SeatDto> seats,
                @Nullable List<Map<String, Object>> relationships,
                @Nullable Map<String, Map<String, Object>> effects) {
        clear();
        if (seats == null || relationships == null || relationships.isEmpty()) return;
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < seats.size(); i++) {
            RoomDtos.SeatDto seat = seats.get(i);
            if (seat == null || seat.locked()) continue;
            String id = normalize(seat.userId);
            if (id.isEmpty() && seat.user != null) id = normalize(seat.user.id);
            if (!id.isEmpty()) positions.put(id, i);
        }
        if (positions.size() < 2) return;

        int rootLoc[] = new int[2];
        root.getLocationInWindow(rootLoc);
        Set<String> drawn = new HashSet<>();
        for (Map<String, Object> relation : relationships) {
            if (relation == null) continue;
            String a = normalize(relation.get("userAId"));
            String b = normalize(relation.get("userBId"));
            Integer indexA = positions.get(a);
            Integer indexB = positions.get(b);
            if (indexA == null || indexB == null || a.equals(b)) continue;
            String key = a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
            if (!drawn.add(key)) continue;

            RecyclerView.ViewHolder holderA = seatsRecycler.findViewHolderForAdapterPosition(indexA);
            RecyclerView.ViewHolder holderB = seatsRecycler.findViewHolderForAdapterPosition(indexB);
            if (holderA == null || holderB == null) continue;
            View avatarA = holderA.itemView.findViewById(R.id.imgAvatar);
            View avatarB = holderB.itemView.findViewById(R.id.imgAvatar);
            if (avatarA == null || avatarB == null
                    || avatarA.getVisibility() != View.VISIBLE
                    || avatarB.getVisibility() != View.VISIBLE) continue;

            float[] pointA = center(avatarA, rootLoc);
            float[] pointB = center(avatarB, rootLoc);
            String type = String.valueOf(relation.get("type") == null ? "" : relation.get("type"));
            Map<String, Object> effect = effects == null ? null : effects.get(type);
            if (effect != null && Boolean.FALSE.equals(effect.get("enabled"))) continue;
            String url = effect == null || effect.get("url") == null ? "" : String.valueOf(effect.get("url"));
            int sizeDp = intValue(effect == null ? null : effect.get("sizeDp"), 48, 24, 160);
            long duration = intValue(effect == null ? null : effect.get("durationMs"), 1800, 500, 10000);
            addEffect(avatarA, avatarB, type, url, sizeDp, duration);
        }
    }

    private void addEffect(@NonNull View anchorA, @NonNull View anchorB,
                           String type, String url, int sizeDp, long duration) {
        // Track the live avatar anchors during every animation frame. This keeps the
        // effect attached to people when seats are reordered, the grid relayouts, or
        // the room view changes size, without touching RTC/audio/session state.
        final int particleCount = 3;
        final int particleSizeDp = Math.max(18, Math.min(44, Math.round(sizeDp / 2.2f)));
        final int size = dp(particleSizeDp);
        for (int i = 0; i < particleCount; i++) {
            final View effectView;
            if (!url.trim().isEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) {
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription("relationship effect");
                Glide.with(context).load(url).into(image);
                effectView = image;
            } else {
                TextView emoji = new TextView(context);
                emoji.setText(defaultEmoji(type));
                emoji.setTextSize(Math.max(15, particleSizeDp * 0.72f));
                emoji.setGravity(Gravity.CENTER);
                emoji.setIncludeFontPadding(false);
                emoji.setTextColor(Color.WHITE);
                emoji.setShadowLayer(dp(3), 0, dp(1), 0xAA000000);
                effectView = emoji;
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            effectView.setLayoutParams(lp);
            effectView.setClickable(false);
            effectView.setFocusable(false);
            layer.addView(effectView);

            ValueAnimator travel = ValueAnimator.ofFloat(0f, 1f);
            travel.setDuration(duration);
            travel.setRepeatCount(ValueAnimator.INFINITE);
            travel.setRepeatMode(ValueAnimator.RESTART);
            travel.setInterpolator(new android.view.animation.LinearInterpolator());
            travel.setStartDelay((duration / particleCount) * i);
            travel.addUpdateListener(animation -> {
                if (anchorA.getParent() == null || anchorB.getParent() == null
                        || anchorA.getVisibility() != View.VISIBLE
                        || anchorB.getVisibility() != View.VISIBLE) {
                    effectView.setVisibility(View.INVISIBLE);
                    return;
                }
                effectView.setVisibility(View.VISIBLE);
                int[] rootLoc = new int[2];
                root.getLocationInWindow(rootLoc);
                float[] from = center(anchorA, rootLoc);
                float[] to = center(anchorB, rootLoc);
                float progress = (Float) animation.getAnimatedValue();
                effectView.setX(from[0] + (to[0] - from[0]) * progress - size / 2f);
                effectView.setY(from[1] + (to[1] - from[1]) * progress - size / 2f);
                float scale = 0.82f + 0.18f * (float) Math.sin(progress * Math.PI);
                effectView.setScaleX(scale);
                effectView.setScaleY(scale);
            });
            running.add(travel);
            travel.start();
        }
    }

    private float[] center(View view, int[] rootLoc) {
        int loc[] = new int[2];
        view.getLocationInWindow(loc);
        return new float[] { loc[0] - rootLoc[0] + view.getWidth() / 2f,
                loc[1] - rootLoc[1] + view.getHeight() / 2f };
    }

    void clear() {
        List<AnimatorSet> toStop = new ArrayList<>(running);
        running.clear();
        for (AnimatorSet animator : toStop) {
            try { animator.cancel(); } catch (Exception ignored) {}
        }
        layer.removeAllViews();
    }

    void destroy() {
        clear();
        if (layer.getParent() instanceof ViewGroup) ((ViewGroup) layer.getParent()).removeView(layer);
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int intValue(Object value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, (int) Math.round(Double.parseDouble(String.valueOf(value))))); }
        catch (Exception ignored) { return fallback; }
    }

    private static String defaultEmoji(String type) {
        switch (type) {
            case "love": return "💗";
            case "engagement": return "💍";
            case "marriage": return "💞";
            case "friendship": return "🤝";
            case "brotherhood": return "💙";
            default: return "✨";
        }
    }
}
