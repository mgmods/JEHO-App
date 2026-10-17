package com.Dramizo.Series.presentation.voiceroom;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.drawable.Drawable;
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
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

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
    private boolean retryAttempted;

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
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
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
        retryAttempted = false;
        renderCurrent();
    }

    private void renderCurrent() {
        clearParticles();
        if (lastSeats == null || lastRelationships == null || lastRelationships.isEmpty()) return;
        if (layer.getWidth() <= 0 || layer.getHeight() <= 0) {
            retryIfNoParticles();
            return;
        }

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < lastSeats.size(); i++) {
            RoomDtos.SeatDto seat = lastSeats.get(i);
            if (seat == null || seat.locked()) continue;
            String id = normalize(seat.userId);
            if (id.isEmpty() && seat.user != null) id = normalize(seat.user.id);
            if (!id.isEmpty()) positions.put(id, i);
        }
        if (positions.size() < 2) return;

        Set<String> drawn = new HashSet<>();
        for (Map<String, Object> relation : lastRelationships) {
            if (relation == null) continue;
            // Accept the API's canonical pair fields and common serialized aliases.
            // Older CP payloads used user1/user2 or nested user objects; treating those as
            // missing IDs silently removed every room ribbon after the recent renderer change.
            String a = firstValue(relation, "userAId", "user1Id", "fromUserId", "requesterId", "userA", "user1", "fromUser");
            String b = firstValue(relation, "userBId", "user2Id", "toUserId", "targetId", "userB", "user2", "toUser");
            Integer indexA = positions.get(a);
            Integer indexB = positions.get(b);
            if (indexA == null || indexB == null || a.equals(b)) continue;
            String key = a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
            if (!drawn.add(key)) continue;

            // Do not require holders to be attached during this pass. The animation resolves
            // each endpoint by user ID on every frame, including while RecyclerView rebinds seats.

            String type = firstValue(relation, "type", "relationshipType", "relationType", "bondType");
            Map<String, Object> effect = findEffect(type);
            if (effect != null && Boolean.FALSE.equals(effect.get("enabled"))) continue;
            String url = effect == null ? "" : firstValue(effect, "url", "imageUrl", "assetUrl", "effectUrl", "iconUrl");
            int sizeDp = intValue(effect == null ? null : effect.get("sizeDp"), 18, 10, 36);
            long duration = intValue(effect == null ? null : effect.get("durationMs"), 1800, 500, 10000);
            addEffect(a, b, type, url, sizeDp, duration);
        }
        if (running.isEmpty()) retryIfNoParticles();
    }

    private void retryIfNoParticles() {
        if (retryPosted || retryAttempted || running.size() > 0 || lastRelationships == null
                || lastRelationships.isEmpty() || lastSeats == null) return;
        retryPosted = true;
        retryAttempted = true;
        // RecyclerView can report the new seat list before holders are laid out. Retry once
        // after its next layout/attachment instead of silently losing the effect for 15 seconds.
        seatsRecycler.post(() -> {
            retryPosted = false;
            if (running.isEmpty() && lastSeats != null && lastRelationships != null) {
                renderCurrent();
            }
        });
    }

    /**
     * Draw the configured relationship ribbon between USER IDs, not stale View references.
     * RecyclerView recycles seat holders when either member changes seats; resolving by ID on
     * every frame keeps the ribbon attached to the correct people during and after seat moves.
     */
    private void addEffect(@NonNull String userA, @NonNull String userB,
                           String type, String url, int sizeDp, long duration) {
        final int thickness = dp(Math.max(6, Math.min(48, sizeDp)));
        final boolean hasRibbon = !url.trim().isEmpty()
                && (url.startsWith("https://") || url.startsWith("http://"));
        final RibbonFallbackView fallback = new RibbonFallbackView(context);
        fallback.setVisibility(hasRibbon ? View.INVISIBLE : View.VISIBLE);
        FrameLayout.LayoutParams fallbackLp = new FrameLayout.LayoutParams(1, thickness);
        fallback.setLayoutParams(fallbackLp);
        fallback.setClickable(false);
        fallback.setFocusable(false);
        layer.addView(fallback);

        final boolean[] assetLoaded = {false};
        final boolean[] assetFailed = {!hasRibbon};
        final ImageView ribbon;
        if (hasRibbon) {
            ribbon = new ImageView(context);
            ribbon.setScaleType(ImageView.ScaleType.FIT_XY);
            ribbon.setContentDescription("relationship ribbon");
            ribbon.setVisibility(View.INVISIBLE);
            ribbon.setClickable(false);
            ribbon.setFocusable(false);
            layer.addView(ribbon);
            Glide.with(context).load(url).listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(@Nullable GlideException e, Object model,
                        Target<Drawable> target, boolean isFirstResource) {
                    assetFailed[0] = true;
                    assetLoaded[0] = false;
                    ribbon.setVisibility(View.INVISIBLE);
                    fallback.setVisibility(View.VISIBLE);
                    android.util.Log.w("RelationshipEffect", "Ribbon asset failed to load; using animated fallback", e);
                    return false;
                }
                @Override public boolean onResourceReady(Drawable resource, Object model,
                        Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    assetLoaded[0] = true;
                    assetFailed[0] = false;
                    ribbon.setVisibility(View.VISIBLE);
                    fallback.setVisibility(View.INVISIBLE);
                    return false;
                }
            }).into(ribbon);
        } else {
            ribbon = null;
        }
        final View effectView = hasRibbon ? ribbon : fallback;
        final int[] gridLoc = new int[2];
        final int[] aLoc = new int[2];
        final int[] bLoc = new int[2];
        ValueAnimator travel = ValueAnimator.ofFloat(0f, 1f);
        travel.setDuration(Math.max(500L, duration));
        travel.setRepeatCount(ValueAnimator.INFINITE);
        travel.setRepeatMode(ValueAnimator.RESTART);
        travel.setInterpolator(new LinearInterpolator());
        travel.addUpdateListener(animation -> {
            View anchorA = findAvatarForUser(userA);
            View anchorB = findAvatarForUser(userB);
            if (anchorA == null || anchorB == null
                    || layer.getWidth() <= 0 || layer.getHeight() <= 0) {
                // Seats can temporarily detach during a move/layout pass. Keep the animation
                // alive and reconnect it as soon as both current holders are attached again.
                effectView.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.INVISIBLE);
                if (ribbon != null) ribbon.setVisibility(View.INVISIBLE);
                return;
            }
            layer.getLocationInWindow(gridLoc);
            anchorA.getLocationInWindow(aLoc);
            anchorB.getLocationInWindow(bLoc);
            float fromX = aLoc[0] - gridLoc[0] + anchorA.getWidth() / 2f;
            float fromY = aLoc[1] - gridLoc[1] + anchorA.getHeight() / 2f;
            float toX = bLoc[0] - gridLoc[0] + anchorB.getWidth() / 2f;
            float toY = bLoc[1] - gridLoc[1] + anchorB.getHeight() / 2f;

            // Keep the entire effect inside the RecyclerView's own canvas. It cannot drift into
            // the status bar, bottom controls, or outside the room's seat grid.
            fromX = clamp(fromX, 0f, layer.getWidth());
            toX = clamp(toX, 0f, layer.getWidth());
            fromY = clamp(fromY, 0f, layer.getHeight());
            toY = clamp(toY, 0f, layer.getHeight());
            float dx = toX - fromX;
            float dy = toY - fromY;
            float distance = (float) Math.hypot(dx, dy);
            if (distance < dp(8)) {
                effectView.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.INVISIBLE);
                if (ribbon != null) ribbon.setVisibility(View.INVISIBLE);
                return;
            }
            if (hasRibbon) {
                ribbon.setVisibility(assetLoaded[0] ? View.VISIBLE : View.INVISIBLE);
                fallback.setVisibility(assetFailed[0] ? View.VISIBLE : View.INVISIBLE);
            } else {
                fallback.setVisibility(View.VISIBLE);
            }
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            float progress = (Float) animation.getAnimatedValue();
            // Keep a full connector spanning the two live avatar centers. Both the uploaded
            // asset and the fallback use the same endpoints, so neither can drift from a seat.
            int wantedWidth = Math.max(1, Math.round(distance));
            View[] views = hasRibbon ? new View[]{ribbon, fallback} : new View[]{fallback};
            for (View view : views) {
                if (view == null) continue;
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params.width != wantedWidth || params.height != thickness) {
                    params.width = wantedWidth;
                    params.height = thickness;
                    view.setLayoutParams(params);
                }
                view.setPivotX(wantedWidth / 2f);
                view.setPivotY(thickness / 2f);
                view.setRotation(angle);
                view.setX((fromX + toX) / 2f - wantedWidth / 2f);
                view.setY((fromY + toY) / 2f - thickness / 2f);
                float pulse = 0.90f + 0.10f * (float) Math.sin(progress * Math.PI * 2f);
                view.setAlpha(pulse);
            }
            fallback.setProgress(progress);
        });
        running.add(travel);
        travel.start();
    }

    /** Drawn fallback connector: visible and animated even if the dashboard asset is missing,
     * an unsupported format, or temporarily unreachable. */
    private static final class RibbonFallbackView extends View {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint particle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        RibbonFallbackView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setStrokeWidth(10f);
            glow.setColor(0x55FF5B9A);
            core.setStyle(Paint.Style.STROKE);
            core.setStrokeCap(Paint.Cap.ROUND);
            core.setStrokeWidth(3.5f);
            core.setColor(0xFFFF8DBB);
            particle.setStyle(Paint.Style.FILL);
            particle.setColor(Color.WHITE);
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cy = getHeight() / 2f;
            float right = Math.max(1f, getWidth());
            glow.setStrokeWidth(Math.max(5f, getHeight() * 0.9f));
            core.setStrokeWidth(Math.max(2f, getHeight() * 0.24f));
            canvas.drawLine(0f, cy, right, cy, glow);
            canvas.drawLine(0f, cy, right, cy, core);
            float dot = Math.max(2f, getHeight() * 0.22f);
            for (int i = 0; i < 3; i++) {
                float p = (progress + i / 3f) % 1f;
                float x = dot + p * Math.max(0f, right - 2f * dot);
                canvas.drawCircle(x, cy, dot, particle);
                canvas.drawCircle(x, cy, dot * 1.8f, glow);
            }
        }
    }

    @Nullable
    private View findAvatarForUser(@NonNull String userId) {
        String wanted = normalize(userId);
        if (wanted.isEmpty()) return null;
        for (int i = 0; i < seatsRecycler.getChildCount(); i++) {
            View child = seatsRecycler.getChildAt(i);
            RecyclerView.ViewHolder holder = seatsRecycler.getChildViewHolder(child);
            if (holder instanceof SeatAdapter.VH) {
                SeatAdapter.VH seatHolder = (SeatAdapter.VH) holder;
                if (wanted.equals(normalize(seatHolder.boundUserId))) {
                    View avatar = child.findViewById(R.id.imgAvatar);
                    if (avatar != null && avatar.getVisibility() == View.VISIBLE) return avatar;
                }
            }
        }
        return null;
    }

    private Map<String, Object> findEffect(@NonNull String type) {
        if (lastEffects == null) return null;
        Map<String, Object> effect = lastEffects.get(type);
        if (effect != null) return effect;
        for (Map.Entry<String, Map<String, Object>> entry : lastEffects.entrySet()) {
            if (normalize(entry.getKey()).equals(normalize(type))) return entry.getValue();
        }
        return null;
    }

    @NonNull
    private static String firstValue(@Nullable Map<String, Object> source, String... keys) {
        if (source == null) return "";
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map) {
                Map<?, ?> nested = (Map<?, ?>) value;
                for (String nestedKey : new String[]{"id", "userId", "uid"}) {
                    Object nestedValue = nested.get(nestedKey);
                    if (nestedValue != null && !String.valueOf(nestedValue).trim().isEmpty()) {
                        return String.valueOf(nestedValue).trim();
                    }
                }
            } else if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
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
