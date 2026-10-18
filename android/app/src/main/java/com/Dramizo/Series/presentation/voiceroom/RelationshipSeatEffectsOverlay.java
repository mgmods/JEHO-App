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
        // Use an Activity-content-sized overlay instead of a RecyclerView-sized sibling.
        // The old sibling was clipped to the seat grid and could be drawn under room cards;
        // the relationship ribbon therefore existed in code but was not actually visible.
        // This root overlay stays inside the app content (below the system status bar), spans
        // the live seat coordinates, and never intercepts taps or gestures.
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setClickable(false);
        layer.setFocusable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        layer.setElevation(dp(240));
        layer.setTranslationZ(dp(240));
        if (root instanceof ConstraintLayout) {
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            root.addView(layer, lp);
        } else {
            root.addView(layer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        layer.bringToFront();
        root.invalidate();
        seatsRecycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (layer.getWidth() != root.getWidth() || layer.getHeight() != root.getHeight()) {
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
            String a = normalize(firstValue(relation, "userAId", "user1Id", "fromUserId", "requesterId", "userA", "user1", "fromUser"));
            String b = normalize(firstValue(relation, "userBId", "user2Id", "toUserId", "targetId", "userB", "user2", "toUser"));
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
            int sizeDp = intValue(effect == null ? null : effect.get("sizeDp"), 28, 20, 36);
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
    /**
     * Render the configured artwork as fixed-size moving copies along the live CP path.
     * Never stretch or rotate the uploaded image: its native aspect ratio and orientation
     * are preserved regardless of the distance or direction between the two seats.
     */
    private void addEffect(@NonNull String userA, @NonNull String userB,
                           String type, String url, int sizeDp, long duration) {
        final int iconSize = dp(Math.max(20, Math.min(36, sizeDp)));
        final boolean hasAsset = !url.trim().isEmpty()
                && (url.startsWith("https://") || url.startsWith("http://"));
        final RibbonFallbackView fallback = new RibbonFallbackView(context);
        fallback.setVisibility(hasAsset ? View.INVISIBLE : View.VISIBLE);
        FrameLayout.LayoutParams fallbackLp = new FrameLayout.LayoutParams(1, iconSize);
        fallback.setLayoutParams(fallbackLp);
        fallback.setClickable(false);
        fallback.setFocusable(false);
        layer.addView(fallback);

        final List<ImageView> copies = new ArrayList<>();
        final boolean[] assetLoaded = {false};
        final boolean[] assetFailed = {!hasAsset};
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
                // A holder may detach during seat movement/recycling. Keep the animator alive
                // and reconnect to the same users as soon as their new holders are attached.
                for (ImageView copy : copies) copy.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.INVISIBLE);
                return;
            }

            layer.getLocationInWindow(gridLoc);
            anchorA.getLocationInWindow(aLoc);
            anchorB.getLocationInWindow(bLoc);
            float centerAX = aLoc[0] - gridLoc[0] + anchorA.getWidth() / 2f;
            float centerAY = aLoc[1] - gridLoc[1] + anchorA.getHeight() / 2f;
            float centerBX = bLoc[0] - gridLoc[0] + anchorB.getWidth() / 2f;
            float centerBY = bLoc[1] - gridLoc[1] + anchorB.getHeight() / 2f;
            float centerDx = centerBX - centerAX;
            float centerDy = centerBY - centerAY;
            float centerDistance = (float) Math.hypot(centerDx, centerDy);
            if (centerDistance < dp(8)) {
                for (ImageView copy : copies) copy.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.INVISIBLE);
                return;
            }

            float unitX = centerDx / centerDistance;
            float unitY = centerDy / centerDistance;
            float radiusA = Math.max(anchorA.getWidth(), anchorA.getHeight()) * 0.46f;
            float radiusB = Math.max(anchorB.getWidth(), anchorB.getHeight()) * 0.46f;
            float fromX = centerAX + unitX * radiusA;
            float fromY = centerAY + unitY * radiusA;
            float toX = centerBX - unitX * radiusB;
            float toY = centerBY - unitY * radiusB;
            fromX = clamp(fromX, 0f, layer.getWidth());
            toX = clamp(toX, 0f, layer.getWidth());
            fromY = clamp(fromY, 0f, layer.getHeight());
            toY = clamp(toY, 0f, layer.getHeight());
            float dx = toX - fromX;
            float dy = toY - fromY;
            float distance = (float) Math.hypot(dx, dy);
            if (distance < dp(8)) {
                for (ImageView copy : copies) copy.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.INVISIBLE);
                return;
            }

            float progress = (Float) animation.getAnimatedValue();
            if (hasAsset && !assetFailed[0]) {
                // One fixed-size copy every ~1.3 icon widths; more copies are created for
                // longer seat-to-seat distances instead of stretching a single bitmap.
                int wantedCopies = Math.max(2, Math.min(32,
                        (int) Math.ceil(distance / Math.max(1f, iconSize * 1.30f))));
                while (copies.size() < wantedCopies) {
                    ImageView copy = new ImageView(context);
                    copy.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    copy.setAdjustViewBounds(false);
                    copy.setContentDescription("moving relationship effect");
                    copy.setClickable(false);
                    copy.setFocusable(false);
                    copy.setVisibility(View.INVISIBLE);
                    copy.setRotation(0f);
                    copy.setScaleX(1f);
                    copy.setScaleY(1f);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize);
                    copy.setLayoutParams(lp);
                    layer.addView(copy);
                    copies.add(copy);
                    Glide.with(context).load(url).listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                Target<Drawable> target, boolean isFirstResource) {
                            assetFailed[0] = true;
                            android.util.Log.w("RelationshipEffect",
                                    "Effect image failed; switching to animated fallback", e);
                            return false;
                        }
                        @Override public boolean onResourceReady(Drawable resource, Object model,
                                Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            assetLoaded[0] = true;
                            return false;
                        }
                    }).into(copy);
                }
                while (copies.size() > wantedCopies) {
                    ImageView last = copies.remove(copies.size() - 1);
                    Glide.with(context).clear(last);
                    layer.removeView(last);
                }

                fallback.setVisibility(View.INVISIBLE);
                for (int i = 0; i < copies.size(); i++) {
                    ImageView copy = copies.get(i);
                    if (assetFailed[0]) {
                        copy.setVisibility(View.INVISIBLE);
                        continue;
                    }
                    // Every copy follows the same direction, with a phase offset. The image
                    // itself is never rotated, mirrored, or resized to match the path length.
                    float phase = (progress + (float) i / copies.size()) % 1f;
                    float x = fromX + dx * phase;
                    float y = fromY + dy * phase;
                    copy.setX(x - iconSize / 2f);
                    copy.setY(y - iconSize / 2f);
                    copy.setRotation(0f);
                    copy.setScaleX(1f);
                    copy.setScaleY(1f);
                    copy.setAlpha(0.94f + 0.06f
                            * (float) Math.sin((progress * Math.PI * 2f) + i));
                    copy.setVisibility(assetLoaded[0] ? View.VISIBLE : View.INVISIBLE);
                }
            } else {
                for (ImageView copy : copies) copy.setVisibility(View.INVISIBLE);
                fallback.setVisibility(View.VISIBLE);
                int wantedWidth = Math.max(1, Math.round(distance));
                ViewGroup.LayoutParams params = fallback.getLayoutParams();
                if (params.width != wantedWidth || params.height != iconSize) {
                    params.width = wantedWidth;
                    params.height = iconSize;
                    fallback.setLayoutParams(params);
                }
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                fallback.setPivotX(wantedWidth / 2f);
                fallback.setPivotY(iconSize / 2f);
                fallback.setRotation(angle);
                fallback.setX((fromX + toX) / 2f - wantedWidth / 2f);
                fallback.setY((fromY + toY) / 2f - iconSize / 2f);
                fallback.setAlpha(0.94f + 0.06f * (float) Math.sin(progress * Math.PI * 2f));
                fallback.setProgress(progress);
            }
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
        private final Paint heart = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint capsule = new Paint(Paint.ANTI_ALIAS_FLAG);
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
            heart.setStyle(Paint.Style.FILL);
            heart.setColor(Color.WHITE);
            heart.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            heart.setTextAlign(Paint.Align.CENTER);
            capsule.setStyle(Paint.Style.FILL);
            capsule.setColor(0xEEFF4F93);
            capsule.setShadowLayer(5f, 0f, 1f, 0xAAFF2F86);
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        private float dpForView(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cy = getHeight() / 2f;
            float right = Math.max(1f, getWidth());
            float inset = Math.max(1f, getHeight() * 0.06f);
            canvas.drawRoundRect(inset, inset, Math.max(inset + 1f, right - inset),
                    Math.max(inset + 1f, getHeight() - inset), getHeight() / 2f,
                    getHeight() / 2f, capsule);
            capsule.clearShadowLayer();
            glow.setStrokeWidth(Math.max(5f, getHeight() * 0.24f));
            core.setStrokeWidth(Math.max(2f, getHeight() * 0.12f));
            canvas.drawLine(inset, cy, right - inset, cy, glow);
            canvas.drawLine(inset, cy, right - inset, cy, core);
            float dot = Math.max(2f, getHeight() * 0.22f);
            for (int i = 0; i < 3; i++) {
                float p = (progress + i / 3f) % 1f;
                float x = dot + p * Math.max(0f, right - 2f * dot);
                canvas.drawCircle(x, cy, dot, particle);
                canvas.drawCircle(x, cy, dot * 1.8f, glow);
            }
            // A clear heart at the center makes the ribbon recognizable even when no custom
            // effect image is configured in the dashboard or its URL fails to load.
            heart.setTextSize(Math.max(dpForView(10), getHeight() * 0.58f));
            heart.setShadowLayer(2f, 0f, 0f, 0xAA9B174D);
            canvas.drawText("CP  ♥", right / 2f, cy + heart.getTextSize() * 0.34f, heart);
            heart.clearShadowLayer();
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
                    // The avatar ImageView is intentionally GONE while an animated VIP/host
                    // frame is rendered by HostSignalView. Anchor to the seat's fixed avatar
                    // stack container instead, so CP motion does not disappear for framed users.
                    View avatar = child.findViewById(R.id.imgAvatar);
                    if (avatar != null && avatar.getParent() instanceof View) {
                        View stack = (View) avatar.getParent();
                        if (stack.getWidth() > 0 && stack.getHeight() > 0) return stack;
                    }
                    View liveWear = child.findViewById(R.id.seatHostSignal);
                    if (liveWear != null && liveWear.getVisibility() == View.VISIBLE) return liveWear;
                    View frame = child.findViewById(R.id.imgFrame);
                    if (frame != null && frame.getVisibility() == View.VISIBLE) return frame;
                    View badge = child.findViewById(R.id.imgHostBadge);
                    if (badge != null && badge.getVisibility() == View.VISIBLE) return badge;
                    if (avatar != null && avatar.getWidth() > 0 && avatar.getHeight() > 0) return avatar;
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
