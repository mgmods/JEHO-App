package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
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

/** Decorative CP effects confined to the seat grid; this class never touches RTC/audio state. */
final class RelationshipSeatEffectsOverlay {
    private final Context context;
    private final ViewGroup root;
    private final RecyclerView seatsRecycler;
    private final FrameLayout layer;
    private final List<Animator> running = new ArrayList<>();
    @Nullable private List<RoomDtos.SeatDto> lastSeats;
    @Nullable private List<Map<String, Object>> lastRelationships;
    @Nullable private Map<String, Map<String, Object>> lastEffects;
    private boolean retryPosted;

    RelationshipSeatEffectsOverlay(@NonNull Context context, @NonNull ViewGroup root,
                                   @NonNull RecyclerView seatsRecycler) {
        this.context = context;
        this.root = root;
        this.seatsRecycler = seatsRecycler;
        layer = new FrameLayout(context);
        layer.setBackgroundColor(Color.TRANSPARENT);
        // The effect canvas must have exactly the seat-grid bounds, not the whole Activity.
        // This prevents any particle from entering the status bar or leaving the room surface.
        layer.setClipChildren(true);
        layer.setClipToPadding(true);
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setElevation(dp(3));
        if (root instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.startToStart = R.id.recyclerSeats;
            lp.endToEnd = R.id.recyclerSeats;
            lp.topToTop = R.id.recyclerSeats;
            lp.bottomToBottom = R.id.recyclerSeats;
            root.addView(layer, lp);
        } else if (seatsRecycler.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) seatsRecycler.getParent();
            parent.addView(layer, parent.indexOfChild(seatsRecycler) + 1,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.addView(layer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        seatsRecycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (layer.getWidth() != seatsRecycler.getWidth()
                    || layer.getHeight() != seatsRecycler.getHeight()) {
                layer.requestLayout();
            }
            retryIfNoParticles();
        });
        seatsRecycler.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View view) {
                retryIfNoParticles();
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View view) {
                // Existing animations recalculate their endpoints from the live avatar views.
            }
        });
    }

    void render(@Nullable List<RoomDtos.SeatDto> seats,
                @Nullable List<Map<String, Object>> relationships,
                @Nullable Map<String, Map<String, Object>> effects) {
        lastSeats = seats == null ? null : new ArrayList<>(seats);
        lastRelationships = relationships == null ? null : new ArrayList<>(relationships);
        lastEffects = effects == null ? null : new HashMap<>(effects);
        clearParticles();
        if (seats == null || relationships == null || relationships.isEmpty()
                || layer.getWidth() <= 0 || layer.getHeight() <= 0) {
            return;
        }

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < seats.size(); i++) {
            RoomDtos.SeatDto seat = seats.get(i);
            if (seat == null || seat.locked()) continue;
            String id = normalize(seat.userId);
            if (id.isEmpty() && seat.user != null) id = normalize(seat.user.id);
            if (!id.isEmpty()) positions.put(id, i);
        }
        if (positions.size() < 2) return;

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

            String type = String.valueOf(relation.get("type") == null ? "" : relation.get("type"));
            Map<String, Object> effect = lastEffects == null ? null : lastEffects.get(type);
            if (effect != null && Boolean.FALSE.equals(effect.get("enabled"))) continue;
            String url = effect == null || effect.get("url") == null ? "" : String.valueOf(effect.get("url"));
            int sizeDp = intValue(effect == null ? null : effect.get("sizeDp"), 18, 10, 36);
            long duration = intValue(effect == null ? null : effect.get("durationMs"), 1800, 500, 10000);
            addEffect(avatarA, avatarB, type, url, sizeDp, duration);
        }
    }

    private void retryIfNoParticles() {
        if (retryPosted || running.size() > 0 || lastRelationships == null
                || lastRelationships.isEmpty() || lastSeats == null) return;
        retryPosted = true;
        // RecyclerView can report the new seat list before holders are laid out. Retry once
        // after its next layout/attachment instead of silently losing the effect for 15 seconds.
        seatsRecycler.post(() -> {
            retryPosted = false;
            if (running.isEmpty() && lastSeats != null && lastRelationships != null) {
                render(lastSeats, lastRelationships, lastEffects);
            }
        });
    }

    private void addEffect(@NonNull View anchorA, @NonNull View anchorB,
                           String type, String url, int sizeDp, long duration) {
        final int size = dp(Math.max(10, Math.min(36, sizeDp)));
        final int[] gridLoc = new int[2];
        final int[] aLoc = new int[2];
        final int[] bLoc = new int[2];
        anchorA.getLocationInWindow(aLoc);
        anchorB.getLocationInWindow(bLoc);
        float dx = (bLoc[0] + anchorB.getWidth() / 2f) - (aLoc[0] + anchorA.getWidth() / 2f);
        float dy = (bLoc[1] + anchorB.getHeight() / 2f) - (aLoc[1] + anchorA.getHeight() / 2f);
        float distanceDp = (float) Math.hypot(dx, dy)
                / context.getResources().getDisplayMetrics().density;
        // Repeat the dashboard-uploaded animated image along the full path between seats.
        final int particleCount = Math.max(3, Math.min(24,
                (int) Math.ceil(distanceDp / Math.max(28f, sizeDp * 1.65f))));
        for (int i = 0; i < particleCount; i++) {
            final View effectView;
            if (!url.trim().isEmpty()
                    && (url.startsWith("https://") || url.startsWith("http://"))) {
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription("relationship effect");
                Glide.with(context).load(url).into(image);
                effectView = image;
            } else {
                TextView emoji = new TextView(context);
                emoji.setText(defaultEmoji(type));
                emoji.setTextSize(Math.max(10, sizeDp * 0.72f));
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
            effectView.setAlpha(0.95f);
            layer.addView(effectView);

            ValueAnimator travel = ValueAnimator.ofFloat(0f, 1f);
            travel.setDuration(duration);
            travel.setRepeatCount(ValueAnimator.INFINITE);
            travel.setRepeatMode(ValueAnimator.RESTART);
            travel.setInterpolator(new LinearInterpolator());
            travel.setStartDelay((duration / particleCount) * i);
            travel.addUpdateListener(animation -> {
                if (anchorA.getParent() == null || anchorB.getParent() == null
                        || anchorA.getVisibility() != View.VISIBLE
                        || anchorB.getVisibility() != View.VISIBLE
                        || layer.getWidth() <= 0 || layer.getHeight() <= 0) {
                    effectView.setVisibility(View.INVISIBLE);
                    return;
                }
                effectView.setVisibility(View.VISIBLE);
                layer.getLocationInWindow(gridLoc);
                anchorA.getLocationInWindow(aLoc);
                anchorB.getLocationInWindow(bLoc);
                float fromX = aLoc[0] - gridLoc[0] + anchorA.getWidth() / 2f;
                float fromY = aLoc[1] - gridLoc[1] + anchorA.getHeight() / 2f;
                float toX = bLoc[0] - gridLoc[0] + anchorB.getWidth() / 2f;
                float toY = bLoc[1] - gridLoc[1] + anchorB.getHeight() / 2f;
                float maxX = Math.max(0f, layer.getWidth() - size);
                float maxY = Math.max(0f, layer.getHeight() - size);
                fromX = clamp(fromX - size / 2f, 0f, maxX) + size / 2f;
                toX = clamp(toX - size / 2f, 0f, maxX) + size / 2f;
                fromY = clamp(fromY - size / 2f, 0f, maxY) + size / 2f;
                toY = clamp(toY - size / 2f, 0f, maxY) + size / 2f;
                float progress = (Float) animation.getAnimatedValue();
                float x = fromX + (toX - fromX) * progress - size / 2f;
                float y = fromY + (toY - fromY) * progress - size / 2f;
                effectView.setX(clamp(x, 0f, maxX));
                effectView.setY(clamp(y, 0f, maxY));
                float scale = 0.92f + 0.08f * (float) Math.sin(progress * Math.PI);
                effectView.setScaleX(scale);
                effectView.setScaleY(scale);
            });
            running.add(travel);
            travel.start();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    void clear() {
        clearParticles();
        lastSeats = null;
        lastRelationships = null;
        lastEffects = null;
    }

    private void clearParticles() {
        List<Animator> toStop = new ArrayList<>(running);
        running.clear();
        for (Animator animator : toStop) {
            try { animator.cancel(); } catch (Exception ignored) {}
        }
        layer.removeAllViews();
    }

    void destroy() {
        clear();
        if (layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int intValue(Object value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    (int) Math.round(Double.parseDouble(String.valueOf(value)))));
        } catch (Exception ignored) {
            return fallback;
        }
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
