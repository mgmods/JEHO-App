package com.Dramizo.Series.presentation.voiceroom;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.RotateAnimation;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ItemSeatBinding;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.SeatReactionEmojis;
import com.Dramizo.Series.util.StaffRoleHelper;
import com.Dramizo.Series.util.StaffIdentityConfigCache;
import com.Dramizo.Series.util.VipNameStyle;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SeatAdapter extends RecyclerView.Adapter<SeatAdapter.VH> {
    public static final int SCALE_NORMAL = 0;
    public static final int SCALE_MEDIUM = 1;
    public static final int SCALE_COMPACT = 2;
    /** Six-column 30-mic layout: compact enough to keep five rows on-screen. */
    public static final int SCALE_MICRO = 3;

    public interface Listener {
        void onSeatClick(View anchor, RoomDtos.SeatDto seat);
        void onSeatLongClick(RoomDtos.SeatDto seat);
    }

    private static final long DEFAULT_REACTION_MS = 3000L;

    private final List<RoomDtos.SeatDto> items = new ArrayList<>();
    private final Listener listener;
    private String hostUserId;
    @Nullable private String selfUserId;
    @Nullable private String selfHostBadgeUrl;
    @Nullable private String selfVipBadgeUrl;
    @Nullable private java.util.Map<String, Object> selfHostBadgeMeta;
    @Nullable private String selfDisplayName;
    @Nullable private String selfAvatarUrl;
    private boolean agencyRoom;
    @Nullable private String roomAgencyId;
    private final Set<String> roomAgencyModeratorIds = new HashSet<>();
    private final Set<String> speakingUsers = new HashSet<>();
    private final Map<String, Float> speakingLevels = new HashMap<>();
    private final Map<String, Float> lastPaintedSpeakLevel = new HashMap<>();
    // Ignore very short VAD silence gaps; otherwise the equipped wave flashes off/on.
    private final Map<String, Runnable> speakingStopRunnables = new HashMap<>();
    private final Map<String, Integer> activeReactions = new HashMap<>();
    private final Map<String, String> activeReactionKeys = new HashMap<>();
    private final Map<String, Runnable> hideReactionRunnables = new HashMap<>();
    private final Map<String, Long> giftCoinsByUser = new HashMap<>();
    /** Users highlighted as gift targets while GiftBottomSheet is open. */
    private final Set<String> giftSelectedUsers = new HashSet<>();
    private boolean giftSelectionActive;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int scaleMode = SCALE_NORMAL;
    private boolean selfMicMuted;

    public void setSelfMicMuted(boolean muted) {
        if (selfMicMuted == muted) return;
        selfMicMuted = muted;
        if (selfUserId == null || selfUserId.isEmpty()) return;
        if (muted) {
            // Closing the local mic must stop the local speaking ring immediately.
            String key = normalizeUserId(selfUserId);
            Runnable pending = speakingStopRunnables.remove(key);
            if (pending != null) handler.removeCallbacks(pending);
            speakingUsers.remove(key);
            speakingLevels.remove(key);
            lastPaintedSpeakLevel.remove(key);
            for (int i = 0; i < items.size(); i++) {
                if (key.equals(seatUserId(items.get(i)))) {
                    notifyItemChanged(i, "speaking");
                    notifyItemChanged(i, "mute");
                    return;
                }
            }
        }
        for (int i = 0; i < items.size(); i++) {
            RoomDtos.SeatDto seat = items.get(i);
            if (seat != null && selfUserId.equals(seatUserId(seat))) {
                notifyItemChanged(i, "mute");
                return;
            }
        }
    }

    public void setScaleMode(int mode) {
        int next = Math.max(SCALE_NORMAL, Math.min(SCALE_MICRO, mode));
        if (scaleMode == next) return;
        scaleMode = next;
        notifyItemRangeChanged(0, getItemCount(), "scale");
    }

    public int getScaleMode() {
        return scaleMode;
    }

    /** Exposed so the host stage can mirror the same totals. */
    public long getGiftCoins(String userId) {
        String key = normalizeUserId(userId);
        if (key.isEmpty()) return 0L;
        Long v = giftCoinsByUser.get(key);
        return v != null ? v : 0L;
    }

    public SeatAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setHostUserId(String hostUserId) {
        String normalized = normalizeUserId(hostUserId);
        if (Objects.equals(this.hostUserId, normalized)) {
            return;
        }
        this.hostUserId = normalized;
        for (int i = 0; i < items.size(); i++) {
            notifyItemChanged(i, "host");
        }
    }

    public void setAgencyRoom(boolean agency) {
        setAgencyContext(agency, null, null);
    }

    public void setAgencyContext(boolean agency, @Nullable String agencyId,
                                 @Nullable List<String> moderatorIds) {
        String normalizedId = agencyId == null ? null : agencyId.trim();
        if (normalizedId != null && normalizedId.isEmpty()) normalizedId = null;
        Set<String> nextModerators = new HashSet<>();
        if (moderatorIds != null) {
            for (String id : moderatorIds) {
                String normalized = normalizeUserId(id);
                if (!normalized.isEmpty()) nextModerators.add(normalized);
            }
        }
        if (agencyRoom == agency && Objects.equals(roomAgencyId, normalizedId)
                && roomAgencyModeratorIds.equals(nextModerators)) return;
        agencyRoom = agency;
        roomAgencyId = normalizedId;
        roomAgencyModeratorIds.clear();
        roomAgencyModeratorIds.addAll(nextModerators);
        notifyItemRangeChanged(0, getItemCount(), "wear");
    }

    public boolean isAgencyRoom() {
        return agencyRoom;
    }

    /** Session wear for the local user when seat DTOs omit badge URLs. */
    public void setSelfHostWear(@Nullable String userId, @Nullable String hostBadgeUrl,
                                @Nullable java.util.Map<String, Object> hostBadgeMeta) {
        setSelfHostWear(userId, hostBadgeUrl, null, hostBadgeMeta, null, null);
    }

    public void setSelfHostWear(@Nullable String userId, @Nullable String hostBadgeUrl,
                                @Nullable java.util.Map<String, Object> hostBadgeMeta,
                                @Nullable String displayName, @Nullable String avatarUrl) {
        setSelfHostWear(userId, hostBadgeUrl, null, hostBadgeMeta, displayName, avatarUrl);
    }

    public void setSelfHostWear(@Nullable String userId,
                                @Nullable String hostBadgeUrl,
                                @Nullable String vipBadgeUrl,
                                @Nullable java.util.Map<String, Object> hostBadgeMeta,
                                @Nullable String displayName,
                                @Nullable String avatarUrl) {
        String nextId = normalizeUserId(userId);
        boolean same = Objects.equals(this.selfUserId, nextId)
                && Objects.equals(this.selfHostBadgeUrl, hostBadgeUrl)
                && Objects.equals(this.selfVipBadgeUrl, vipBadgeUrl)
                && Objects.equals(this.selfHostBadgeMeta, hostBadgeMeta)
                && Objects.equals(this.selfDisplayName, displayName)
                && Objects.equals(this.selfAvatarUrl, avatarUrl);
        this.selfUserId = nextId.isEmpty() ? null : nextId;
        this.selfHostBadgeUrl = hostBadgeUrl != null && !hostBadgeUrl.isEmpty() ? hostBadgeUrl : null;
        this.selfVipBadgeUrl = vipBadgeUrl != null && !vipBadgeUrl.isEmpty() ? vipBadgeUrl : null;
        this.selfHostBadgeMeta = hostBadgeMeta;
        this.selfDisplayName = displayName != null && !displayName.isEmpty() ? displayName : null;
        this.selfAvatarUrl = avatarUrl != null && !avatarUrl.isEmpty() ? avatarUrl : null;
        if (same) return;
        for (int i = 0; i < items.size(); i++) {
            RoomDtos.SeatDto seat = items.get(i);
            if (seat != null && this.selfUserId != null
                    && this.selfUserId.equals(normalizeUserId(seatUserId(seat)))) {
                notifyItemChanged(i, "wear");
            }
        }
    }

    public void notifySeatMute(String userId) {
        String key = normalizeUserId(userId);
        if (key.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(seatUserId(items.get(i)))) {
                notifyItemChanged(i, "mute");
                return;
            }
        }
    }

    public void submit(List<RoomDtos.SeatDto> data) {
        List<RoomDtos.SeatDto> next = data != null
                ? new ArrayList<>(data)
                : new ArrayList<>();
        // Room refresh often omits wear URLs briefly — keep last known cosmetics so
        // frames/SVGA are not cleared and re-decoded on every poll.
        preserveWearFromPrevious(items, next);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                RoomDtos.SeatDto oldSeat = items.get(oldPos);
                RoomDtos.SeatDto newSeat = next.get(newPos);
                if (oldSeat == null || newSeat == null) return oldSeat == newSeat;
                return oldSeat.seatIndex == newSeat.seatIndex;
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return sameSeat(items.get(oldPos), next.get(newPos));
            }
            @Nullable
            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                RoomDtos.SeatDto a = items.get(oldPos);
                RoomDtos.SeatDto b = next.get(newPos);
                if (a == null || b == null) return null;
                // Mute-only updates: avoid full rebind flicker.
                if (sameSeatIgnoringMute(a, b)
                        && (a.isMuted != b.isMuted || a.isModeratorMuted != b.isModeratorMuted)) {
                    return "mute";
                }
                return null;
            }
        }, false);
        items.clear();
        items.addAll(next);
        pruneSpeakingToSeatedUsers();
        diff.dispatchUpdatesTo(this);
    }

    private static void preserveWearFromPrevious(
            List<RoomDtos.SeatDto> previous, List<RoomDtos.SeatDto> next) {
        if (previous == null || previous.isEmpty() || next == null || next.isEmpty()) return;
        Map<String, com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto> byUser = new HashMap<>();
        for (RoomDtos.SeatDto seat : previous) {
            String uid = seatUserId(seat);
            if (uid == null || uid.isEmpty() || seat == null || seat.user == null) continue;
            byUser.put(uid, seat.user);
        }
        if (byUser.isEmpty()) return;
        for (RoomDtos.SeatDto seat : next) {
            if (seat == null || seat.user == null) continue;
            String uid = seatUserId(seat);
            if (uid == null || uid.isEmpty()) continue;
            com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto prev = byUser.get(uid);
            if (prev == null) continue;
            if (isBlank(seat.user.avatarUrl) && !isBlank(prev.avatarUrl)) {
                seat.user.avatarUrl = prev.avatarUrl;
            }
            if (isBlank(seat.user.vipBadgeUrl) && !isBlank(prev.vipBadgeUrl)) {
                seat.user.vipBadgeUrl = prev.vipBadgeUrl;
            }
            if (isBlank(seat.user.hostBadgeUrl) && !isBlank(prev.hostBadgeUrl)) {
                seat.user.hostBadgeUrl = prev.hostBadgeUrl;
                if (seat.user.hostBadgeMeta == null) {
                    seat.user.hostBadgeMeta = prev.hostBadgeMeta;
                }
            }
            // Room refresh payloads can temporarily omit equipped wear fields.
            // Keep the last confirmed voice-wave URL for this seated user so the
            // next speaking update does not fall back to the legacy default wave.
            if (isBlank(seat.user.voiceWaveUrl) && !isBlank(prev.voiceWaveUrl)) {
                seat.user.voiceWaveUrl = prev.voiceWaveUrl;
            }
            if (isBlank(seat.user.levelBadgeUrl) && !isBlank(prev.levelBadgeUrl)) {
                seat.user.levelBadgeUrl = prev.levelBadgeUrl;
            }
            if (isBlank(seat.user.displayName) && !isBlank(prev.displayName)) {
                seat.user.displayName = prev.displayName;
            }
            if (seat.user.roomSupportMedal == null && prev.roomSupportMedal != null) {
                seat.user.roomSupportMedal = prev.roomSupportMedal;
            }
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isEmpty();
    }

    /** Drop speaking flags for users who are no longer on any guest seat. */
    private void pruneSpeakingToSeatedUsers() {
        if (speakingUsers.isEmpty()) return;
        Set<String> seated = new HashSet<>();
        for (RoomDtos.SeatDto seat : items) {
            String uid = seatUserId(seat);
            if (uid != null && !uid.isEmpty()) seated.add(uid);
        }
        speakingUsers.retainAll(seated);
        speakingLevels.keySet().retainAll(seated);
        for (String uid : new HashSet<>(speakingStopRunnables.keySet())) {
            if (!seated.contains(uid)) {
                Runnable pending = speakingStopRunnables.remove(uid);
                if (pending != null) handler.removeCallbacks(pending);
            }
        }
    }

    private static boolean sameSeat(RoomDtos.SeatDto a, RoomDtos.SeatDto b) {
        if (!sameSeatIgnoringMute(a, b)) return false;
        return a.isMuted == b.isMuted && a.isModeratorMuted == b.isModeratorMuted;
    }

    private static boolean sameSeatIgnoringMute(RoomDtos.SeatDto a, RoomDtos.SeatDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.seatIndex != b.seatIndex
                || a.isLocked != b.isLocked
                || !Objects.equals(a.status, b.status)
                || !Objects.equals(seatUserId(a), seatUserId(b))) {
            return false;
        }
        if (a.user == b.user) return true;
        if (a.user == null || b.user == null) return false;
        return Objects.equals(a.user.displayName, b.user.displayName)
                && Objects.equals(a.user.username, b.user.username)
                && Objects.equals(a.user.avatarUrl, b.user.avatarUrl)
                && Objects.equals(a.user.hostBadgeUrl, b.user.hostBadgeUrl)
                && Objects.equals(a.user.vipBadgeUrl, b.user.vipBadgeUrl)
                && Objects.equals(a.user.vipFrameUrl, b.user.vipFrameUrl)
                && Objects.equals(a.user.vipCardUrl, b.user.vipCardUrl)
                && Objects.equals(a.user.profileCardUrl, b.user.profileCardUrl)
                && Objects.equals(a.user.chatBubbleUrl, b.user.chatBubbleUrl)
                && Objects.equals(a.user.nameplateUrl, b.user.nameplateUrl)
                && Objects.equals(a.user.voiceWaveUrl, b.user.voiceWaveUrl)
                && Objects.equals(a.user.levelBadgeUrl, b.user.levelBadgeUrl)
                && Objects.equals(a.user.nameStyle, b.user.nameStyle)
                && Objects.equals(a.user.agencyClub == null ? null : a.user.agencyClub.label,
                        b.user.agencyClub == null ? null : b.user.agencyClub.label)
                && Objects.equals(a.user.agencyClub == null ? null : a.user.agencyClub.role,
                        b.user.agencyClub == null ? null : b.user.agencyClub.role)
                && Objects.equals(a.user.agencyClub == null ? null : a.user.agencyClub.agencyId,
                        b.user.agencyClub == null ? null : b.user.agencyClub.agencyId)
                && Objects.equals(a.user.agency == null ? null : a.user.agency.role,
                        b.user.agency == null ? null : b.user.agency.role)
                && Objects.equals(a.user.agency == null ? null : a.user.agency.id,
                        b.user.agency == null ? null : b.user.agency.id)
                && Objects.equals(a.user.hostBadgeMeta, b.user.hostBadgeMeta)
                && Objects.equals(a.user.roomSupportMedal == null ? null : a.user.roomSupportMedal.id,
                        b.user.roomSupportMedal == null ? null : b.user.roomSupportMedal.id)
                && Objects.equals(a.user.roomSupportMedal == null ? null : a.user.roomSupportMedal.imageUrl,
                        b.user.roomSupportMedal == null ? null : b.user.roomSupportMedal.imageUrl)
                && a.user.level == b.user.level
                && a.user.vipLevel == b.user.vipLevel;
    }

    public void addGiftCoins(String userId, long coins) {
        String key = normalizeUserId(userId);
        if (key.isEmpty() || coins <= 0) {
            return;
        }
        giftCoinsByUser.put(key, giftCoinsByUser.getOrDefault(key, 0L) + coins);
        notifyGift(key);
    }

    public void setGiftCoins(String userId, long totalCoins) {
        String key = normalizeUserId(userId);
        if (key.isEmpty()) {
            return;
        }
        giftCoinsByUser.put(key, Math.max(0, totalCoins));
        notifyGift(key);
    }

    /**
     * While gift sheet is open: teal ring + underline under selected seats (Mikoo).
     * Pass null/empty to clear.
     */
    public void setGiftSelectedUsers(@Nullable Set<String> userIds) {
        giftSelectedUsers.clear();
        if (userIds != null) {
            for (String id : userIds) {
                String key = normalizeUserId(id);
                if (!key.isEmpty()) giftSelectedUsers.add(key);
            }
        }
        giftSelectionActive = !giftSelectedUsers.isEmpty();
        for (int i = 0; i < items.size(); i++) {
            notifyItemChanged(i, "gift_select");
        }
    }

    public void clearGiftSelection() {
        if (!giftSelectionActive && giftSelectedUsers.isEmpty()) return;
        giftSelectedUsers.clear();
        giftSelectionActive = false;
        for (int i = 0; i < items.size(); i++) {
            notifyItemChanged(i, "gift_select");
        }
    }

    private void notifyGift(String userId) {
        for (int i = 0; i < items.size(); i++) {
            if (userId.equals(seatUserId(items.get(i)))) {
                notifyItemChanged(i, "gift");
                return;
            }
        }
    }

    public void setSpeaking(String userId, boolean speaking) {
        // A microphone being enabled is not proof of speech. Only a measured
        // live audio level may activate the seat waveform.
        setSpeaking(userId, speaking, 0f);
    }

    public void setSpeaking(String userId, boolean speaking, float level0to100) {
        String key = normalizeUserId(userId);
        if (key.isEmpty()) {
            return;
        }
        // A muted local microphone can never be considered actively speaking.
        if (speaking && selfMicMuted && selfUserId != null && selfUserId.equals(key)) {
            return;
        }
        // Only light waves on a seat that currently holds this user.
        int seatPos = -1;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(seatUserId(items.get(i)))) {
                seatPos = i;
                break;
            }
        }
        if (seatPos < 0) {
            Runnable pending = speakingStopRunnables.remove(key);
            if (pending != null) handler.removeCallbacks(pending);
            speakingUsers.remove(key);
            speakingLevels.remove(key);
            lastPaintedSpeakLevel.remove(key);
            return;
        }
        RoomDtos.SeatDto currentSeat = items.get(seatPos);
        if (speaking && currentSeat != null && (currentSeat.isMuted || currentSeat.isModeratorMuted)) {
            speaking = false;
            level0to100 = 0f;
        }
        // Suppress the false "speaking" pulse emitted when a user only opens
        // the mic. RTC level updates must cross the speech-activity threshold.
        if (speaking && (Float.isNaN(level0to100) || Float.isInfinite(level0to100)
                || level0to100 < 5f)) {
            speaking = false;
            level0to100 = 0f;
        }
        Runnable pendingStop = speakingStopRunnables.remove(key);
        if (pendingStop != null) handler.removeCallbacks(pendingStop);
        if (!speaking) {
            // Voice activity detection can briefly dip to zero between syllables or
            // during RTC level updates. Keep the current wave through that short gap.
            Runnable delayedStop = () -> {
                speakingStopRunnables.remove(key);
                if (!speakingUsers.remove(key)) return;
                speakingLevels.remove(key);
                lastPaintedSpeakLevel.remove(key);
                for (int i = 0; i < items.size(); i++) {
                    if (key.equals(seatUserId(items.get(i)))) {
                        notifyItemChanged(i, "speaking");
                        break;
                    }
                }
            };
            speakingStopRunnables.put(key, delayedStop);
            handler.postDelayed(delayedStop, 40L);
            return;
        }
        boolean changed = speakingUsers.add(key);
        speakingLevels.put(key, Math.max(0f, level0to100));
        if (!changed) {
            // Already speaking: refresh intensity sparingly (avoid RecyclerView churn).
            if (speaking) {
                Float prev = lastPaintedSpeakLevel.get(key);
                if (prev == null || Math.abs(prev - level0to100) >= 6f) {
                    lastPaintedSpeakLevel.put(key, level0to100);
                    notifyItemChanged(seatPos, "speak_level");
                }
            }
            return;
        }
        if (speaking) lastPaintedSpeakLevel.put(key, level0to100);
        else lastPaintedSpeakLevel.remove(key);
        notifyItemChanged(seatPos, "speaking");
    }

    public boolean showReaction(String userId, String emojiKey, int emojiResId, long durationMs) {
        String userKey = normalizeUserId(userId);
        if (userKey.isEmpty() || emojiResId == 0) {
            return false;
        }
        String normalizedKey = normalizeReactionKey(emojiKey, emojiResId);
        if (normalizedKey == null) {
            return false;
        }
        boolean onSeat = false;
        for (RoomDtos.SeatDto seat : items) {
            // Match via seatUserId (userId OR nested user.id) — seat.userId alone missed many seats.
            if (seat != null && userKey.equals(normalizeUserId(seatUserId(seat)))) {
                onSeat = true;
                break;
            }
        }
        if (!onSeat) {
            return false;
        }
        activeReactions.put(userKey, emojiResId);
        activeReactionKeys.put(userKey, normalizedKey);
        Runnable prev = hideReactionRunnables.remove(userKey);
        if (prev != null) {
            handler.removeCallbacks(prev);
        }
        notifyReaction(userKey);
        Runnable hide = () -> {
            activeReactions.remove(userKey);
            activeReactionKeys.remove(userKey);
            hideReactionRunnables.remove(userKey);
            notifyReaction(userKey);
        };
        hideReactionRunnables.put(userKey, hide);
        handler.postDelayed(hide, Math.max(800L, durationMs));
        return true;
    }

    public void showReaction(String userId, int emojiResId) {
        showReaction(userId, null, emojiResId, DEFAULT_REACTION_MS);
    }

    public void showReaction(String userId, String emojiKey, int emojiResId) {
        showReaction(userId, emojiKey, emojiResId, DEFAULT_REACTION_MS);
    }

    public void showReaction(String userId, int emojiResId, long durationMs) {
        showReaction(userId, null, emojiResId, durationMs);
    }

    private void notifyReaction(String userId) {
        String key = normalizeUserId(userId);
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(normalizeUserId(seatUserId(items.get(i))))) {
                notifyItemChanged(i, "reaction");
                return;
            }
        }
    }

    /** True when this user occupies a guest-grid seat (not boss card). */
    public boolean isUserOnGuestSeat(@Nullable String userId) {
        String key = normalizeUserId(userId);
        if (key.isEmpty()) return false;
        for (RoomDtos.SeatDto seat : items) {
            if (key.equals(normalizeUserId(seatUserId(seat)))) return true;
        }
        return false;
    }

    private static String normalizeReactionKey(String emojiKey, int emojiResId) {
        if (emojiKey != null && !emojiKey.isEmpty()) {
            return SeatReactionEmojis.isAllowed(emojiKey)
                    ? emojiKey.trim().toLowerCase(Locale.US)
                    : null;
        }
        return null;
    }

    private static String seatUserId(RoomDtos.SeatDto seat) {
        if (seat == null) return null;
        if (seat.userId != null && !seat.userId.isEmpty()) return normalizeUserId(seat.userId);
        return seat.user != null ? normalizeUserId(seat.user.id) : null;
    }

    private static String normalizeUserId(String userId) {
        return userId == null ? "" : userId.trim().toLowerCase(Locale.ROOT);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemSeatBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            RoomDtos.SeatDto seat = items.get(position);
            String userId = seatUserId(seat);
            for (Object p : payloads) {
                if ("speaking".equals(p)) {
                    // Re-sync bound id: payload-only binds can otherwise drop waves forever.
                    if (userId != null) holder.boundUserId = userId;
                    holder.voiceWaveUrl = seat.user != null ? seat.user.voiceWaveUrl : null;
                    boolean match = userId != null
                            && speakingUsers.contains(userId)
                            && seat != null && !seat.isMuted && !seat.isModeratorMuted
                            && !(selfMicMuted && selfUserId != null && selfUserId.equals(userId))
                            && !activeReactions.containsKey(userId);
                    bindSpeaking(holder, match);
                    if (match && (holder.voiceWaveUrl == null || holder.voiceWaveUrl.trim().isEmpty())) {
                        // Only drive the legacy speaking animation when no purchased wave is equipped.
                        // bindSpeaking() already owns the equipped-wave visibility and loading state.
                        float lv = speakingLevels.containsKey(userId)
                                ? speakingLevels.get(userId) : 48f;
                        if (holder.b.seatSpeakAura != null) {
                            holder.b.seatSpeakAura.setIntensity(lv);
                        }
                    }
                } else if ("speak_level".equals(p)) {
                    if (userId != null && speakingUsers.contains(userId)) {
                        float lv = speakingLevels.containsKey(userId)
                                ? speakingLevels.get(userId) : 48f;
                        boolean hasEquippedWave = holder.voiceWaveUrl != null
                                && !holder.voiceWaveUrl.trim().isEmpty();
                        if (hasEquippedWave) {
                            if (holder.b.seatSpeakAura != null) {
                                holder.b.seatSpeakAura.setSpeaking(false);
                                holder.b.seatSpeakAura.setVisibility(View.GONE);
                            }
                            if (holder.b.seatVoiceWaveBars != null) {
                                holder.b.seatVoiceWaveBars.setSpeaking(false, 0f);
                                holder.b.seatVoiceWaveBars.setVisibility(View.GONE);
                            }
                            // Make equipped waves react to live RTC level updates instead of
                            // remaining a decorative, constant-speed image around the avatar.
                            float amplitude = Math.max(0f, Math.min(100f, lv)) / 100f;
                            float innerScale = 0.96f + amplitude * 0.16f;
                            if (holder.b.imgVoiceWave != null) {
                                holder.b.imgVoiceWave.animate().cancel();
                                holder.b.imgVoiceWave.animate()
                                        .scaleX(innerScale).scaleY(innerScale)
                                        .alpha(0.78f + amplitude * 0.22f)
                                        .setDuration(110L).start();
                            }
                            if (holder.b.imgVoiceWaveOuter != null) {
                                float outerScale = 1.02f + amplitude * 0.14f;
                                holder.b.imgVoiceWaveOuter.animate().cancel();
                                holder.b.imgVoiceWaveOuter.animate()
                                        .scaleX(outerScale).scaleY(outerScale)
                                        .alpha(0.28f + amplitude * 0.42f)
                                        .setDuration(130L).start();
                            }
                        } else {
                            if (holder.b.seatSpeakAura != null) {
                                holder.b.seatSpeakAura.setSpeaking(false);
                                holder.b.seatSpeakAura.setVisibility(View.GONE);
                            }
                            if (holder.b.seatVoiceWaveBars != null) {
                                holder.b.seatVoiceWaveBars.setVisibility(View.VISIBLE);
                                holder.b.seatVoiceWaveBars.setSpeaking(true, lv);
                            }
                        }
                        holder.speaking = true;
                    }
                } else if ("scale".equals(p)) {
                    applySeatScale(holder);
                } else if ("reaction".equals(p)) {
                    if (!activeReactions.containsKey(userId)) {
                        onBindViewHolder(holder, position);
                        return;
                    }
                    bindReaction(holder, userId);
                } else if ("gift".equals(p)) {
                    bindGiftCount(holder, userId);
                } else if ("gift_select".equals(p)) {
                    bindGiftSelection(holder, userId);
                } else if ("mute".equals(p)) {
                    if (holder.b.imgMuted != null) {
                        boolean show = seat != null && (seat.isMuted || seat.isModeratorMuted);
                        if (!show && selfMicMuted && userId != null && selfUserId != null
                                && selfUserId.equals(userId)) {
                            show = true;
                        }
                        holder.b.imgMuted.setImageResource(R.drawable.ic_asset_mic_close);
                        holder.b.imgMuted.setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                } else if ("wear".equals(p)) {
                    if (seat != null && seat.user != null) {
                        try {
                            bindHostWear(holder, seat.user);
                        } catch (Exception ignored) {
                            // Keep previous frame rather than blanking the seat.
                        }
                    } else if (selfUserId != null && selfUserId.equals(userId)) {
                        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto stub =
                                new com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto();
                        stub.id = selfUserId;
                        stub.avatarUrl = selfAvatarUrl;
                        stub.displayName = selfDisplayName;
                        stub.hostBadgeUrl = selfHostBadgeUrl;
                        stub.vipBadgeUrl = selfVipBadgeUrl;
                        stub.hostBadgeMeta = selfHostBadgeMeta;
                        try {
                            bindHostWear(holder, stub);
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("host".equals(p)) {
                    onBindViewHolder(holder, position);
                    return;
                }
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < 0 || position >= items.size()) return;
        RoomDtos.SeatDto seat = items.get(position);
        if (seat == null) return;
        String userId = seatUserId(seat);
        holder.boundUserId = userId;
        holder.voiceWaveUrl = seat.user != null ? seat.user.voiceWaveUrl : null;
        Context ctx = holder.itemView.getContext();
        holder.b.getRoot().setOnClickListener(v -> {
            if (listener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos < 0 || pos >= items.size()) return;
            RoomDtos.SeatDto current = items.get(pos);
            if (current != null) listener.onSeatClick(v, current);
        });
        holder.b.getRoot().setOnLongClickListener(v -> {
            if (listener == null) return true;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos < 0 || pos >= items.size()) return true;
            RoomDtos.SeatDto current = items.get(pos);
            if (current != null) listener.onSeatLongClick(current);
            return true;
        });
        applySeatScale(holder);

        boolean isCreator = userId != null && hostUserId != null && hostUserId.equals(userId);
        boolean occupied = seat.user != null || (userId != null && !userId.isEmpty());

        if (seat.locked()) {
            bindVipNameplate(holder, null);
            if (holder.b.tvAgencyClub != null) holder.b.tvAgencyClub.setVisibility(View.GONE);
            bindAgencyRoleSeatBadge(holder, null);
            bindEmptySeatShell(holder, true);
            holder.b.seatRing.setVisibility(View.GONE);
            holder.b.tvName.setText(String.valueOf(displaySeatNo(seat)));
            VipNameStyle.bind(holder.b.tvName, (com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto) null);
            holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
            holder.b.imgAvatar.setVisibility(View.GONE);
            if (holder.b.imgSeatShell != null) {
                holder.b.imgSeatShell.setVisibility(View.VISIBLE);
            }
            clearHostWear(holder);
            clearReactionView(holder);
            bindSpeaking(holder, false);
            if (holder.b.tvStatus != null) {
                holder.b.tvStatus.setVisibility(View.GONE);
            }
            if (holder.b.imgSeatHostMark != null) {
                holder.b.imgSeatHostMark.setVisibility(View.GONE);
            }
            bindStaffSeatBadge(holder, null);
            if (holder.b.imgMuted != null) {
                holder.b.imgMuted.setVisibility(View.GONE);
            }
            if (holder.b.tvGiftCount != null) {
                holder.b.tvGiftCount.setVisibility(View.GONE);
            }
            bindGiftSelection(holder, null);
            return;
        }

        if (occupied) {
            bindEmptySeatShell(holder, false);
            holder.b.seatRing.setVisibility(View.GONE);
            holder.b.imgAvatar.setVisibility(View.VISIBLE);
            if (holder.b.imgSeatShell != null) {
                holder.b.imgSeatShell.setVisibility(View.GONE);
            }
            bindSpeaking(holder, userId != null && speakingUsers.contains(userId));
            // Do not GONE frame before wear bind — causes hollow face then late frame (unlike Mikoo).
            if (holder.b.imgHostBadge != null) {
                holder.b.imgHostBadge.setVisibility(View.GONE);
            }
            holder.b.tvStatus.setVisibility(View.GONE);
            if (holder.b.imgMuted != null) {
                holder.b.imgMuted.setVisibility(View.GONE);
            }

            if (holder.b.tvAgencyClub != null) {
                // Agency role is rendered once through tvAgencyRoleBadge, without agency name.
                holder.b.tvAgencyClub.setText("");
                holder.b.tvAgencyClub.setVisibility(View.GONE);
            }
            if (seat.user != null) {
                bindVipNameplate(holder, seat.user);
                holder.b.tvName.setText(seat.user.displayName != null
                        ? seat.user.displayName
                        : String.valueOf(displaySeatNo(seat)));
                VipNameStyle.bind(holder.b.tvName, seat.user);
                holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
                AvatarCosmetics.bindAvatar(holder.b.imgAvatar, seat.user.avatarUrl);
                try {
                    bindHostWear(holder, seat.user);
                } catch (Exception ignored) {
                    AvatarCosmetics.bindAvatar(holder.b.imgAvatar, seat.user.avatarUrl);
                }
            } else if (selfUserId != null && selfUserId.equals(userId)) {
                bindVipNameplate(holder, null);
                // Seat hop race: userId set before nested user DTO arrives — keep self visible.
                holder.b.tvName.setText(selfDisplayName != null && !selfDisplayName.isEmpty()
                        ? selfDisplayName : String.valueOf(displaySeatNo(seat)));
                VipNameStyle.bind(holder.b.tvName, (com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto) null);
                holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
                com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto stub =
                        new com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto();
                stub.id = selfUserId;
                stub.avatarUrl = selfAvatarUrl;
                stub.displayName = selfDisplayName;
                stub.hostBadgeUrl = selfHostBadgeUrl;
                stub.vipBadgeUrl = selfVipBadgeUrl;
                stub.hostBadgeMeta = selfHostBadgeMeta;
                AvatarCosmetics.bindAvatar(holder.b.imgAvatar, selfAvatarUrl);
                try {
                    bindHostWear(holder, stub);
                } catch (Exception ignored) {
                    AvatarCosmetics.bindAvatar(holder.b.imgAvatar, selfAvatarUrl);
                }
            } else {
                holder.b.imgAvatar.setImageResource(ImagePlaceholder.avatar());
                clearHostWear(holder);
                holder.b.tvName.setText(String.valueOf(displaySeatNo(seat)));
            VipNameStyle.bind(holder.b.tvName, (com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto) null);
                holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
            }

            bindGiftCount(holder, userId);
            bindGiftSelection(holder, userId);

            if (holder.b.imgSeatHostMark != null) {
                holder.b.imgSeatHostMark.setVisibility(isCreator ? View.VISIBLE : View.GONE);
            }
            bindStaffSeatBadge(holder, null);
            bindAgencyRoleSeatBadge(holder, null);
            bindCustomDisplayRank(holder, seat.user);
            bindRoomSupportMedal(holder, seat.user);
            if (holder.b.tvStatus != null) {
                holder.b.tvStatus.setVisibility(View.GONE);
            }
            if ((seat.isMuted || seat.isModeratorMuted
                    || (selfMicMuted && selfUserId != null && selfUserId.equals(userId)))
                    && holder.b.imgMuted != null) {
                holder.b.imgMuted.setImageResource(R.drawable.ic_asset_mic_close);
                holder.b.imgMuted.setVisibility(View.VISIBLE);
            }
            bindReaction(holder, userId);
            return;
        }

        if (holder.b.tvAgencyClub != null) holder.b.tvAgencyClub.setVisibility(View.GONE);
        bindAgencyRoleSeatBadge(holder, null);
        bindEmptySeatShell(holder, false);
        holder.b.seatRing.setVisibility(View.GONE);
        holder.b.imgAvatar.setVisibility(View.GONE);
        if (holder.b.imgSeatShell != null) {
            holder.b.imgSeatShell.setVisibility(View.VISIBLE);
        }
        holder.b.tvName.setText(String.valueOf(displaySeatNo(seat)));
            VipNameStyle.bind(holder.b.tvName, (com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto) null);
        holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
        if (holder.b.imgSeatHostMark != null) {
            holder.b.imgSeatHostMark.setVisibility(View.GONE);
        }
        bindStaffSeatBadge(holder, null);
        bindCustomDisplayRank(holder, null);
        bindRoomSupportMedal(holder, null);
        if (holder.b.tvGiftCount != null) {
            holder.b.tvGiftCount.setVisibility(View.GONE);
        }
        if (holder.b.tvStatus != null) {
            holder.b.tvStatus.setVisibility(View.GONE);
        }
        if (holder.b.imgMuted != null) {
            holder.b.imgMuted.setVisibility(View.GONE);
        }
        clearHostWear(holder);
        clearReactionView(holder);
        bindSpeaking(holder, false);
        bindGiftSelection(holder, null);
    }

    /** Host is index 0 (center stage). Guest seats are 1..N shown as-is. */
    private static int displaySeatNo(RoomDtos.SeatDto seat) {
        if (seat == null) return 1;
        return Math.max(1, seat.seatIndex);
    }

    private void applySeatScale(VH holder) {
        int frame;
        int avatar;
        int ring;
        int reaction;
        int muted;
        int nameMax;
        float nameSp;
        int padTop;
        int padBottom;
        int hostMark;
        switch (scaleMode) {
            case SCALE_MICRO -> {
                // 30-mic six-column grid: 58dp seat frame with the number/name directly below.
                // Keep all five rows visible while preserving room chat and controls.
                frame = 58;
                avatar = 48;
                ring = 50;
                reaction = 50;
                muted = 14;
                nameMax = 56;
                nameSp = 9.5f;
                padTop = 0;
                padBottom = 0;
                hostMark = 11;
            }
            case SCALE_COMPACT -> {
                // Mikoo 20-mic: container 64dp, avatar 48dp
                frame = 60;
                avatar = 45;
                ring = 47;
                reaction = 47;
                muted = 14;
                nameMax = 56;
                nameSp = 9.5f;
                padTop = 1;
                padBottom = 2;
                hostMark = 11;
            }
            case SCALE_MEDIUM -> {
                // Between normal and compact (15 mics / 3 rows)
                frame = 66;
                avatar = 49;
                ring = 51;
                reaction = 51;
                muted = 15;
                nameMax = 62;
                nameSp = 10f;
                padTop = 2;
                padBottom = 3;
                hostMark = 12;
            }
            default -> {
                // Slightly compacted normal room seats to preserve spacing around room controls.
                frame = 74;
                avatar = 51;
                ring = 53;
                reaction = 53;
                muted = 16;
                nameMax = 68;
                nameSp = 10.5f;
                padTop = 2;
                padBottom = 4;
                hostMark = 12;
            }
        }
        Context ctx = holder.itemView.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        int px = (int) (padTop * d);
        int pb = (int) (padBottom * d);
        holder.b.getRoot().setPadding(holder.b.getRoot().getPaddingLeft(), px,
                holder.b.getRoot().getPaddingRight(), pb);

        android.view.View frameView = (android.view.View) holder.b.imgAvatar.getParent();
        // Keep normal seat footprint so grid spacing stays tight (FX may draw slightly outside via clipChildren=false).
        setSeatSize(frameView, frame, ctx);
        setSeatSize(holder.b.seatRing, ring, ctx);
        if (holder.b.seatSpeakAura != null) {
            setSeatSize(holder.b.seatSpeakAura, frame, ctx);
        }
        setSeatSize(holder.b.imgSeatShell, avatar, ctx);
        setSeatSize(holder.b.imgAvatar, avatar, ctx);
        setSeatSize(holder.b.imgVoiceWave, frame, ctx);
        if (holder.b.seatVoiceWaveBars != null) {
            setSeatSize(holder.b.seatVoiceWaveBars, frame + 10, ctx);
            holder.b.seatVoiceWaveBars.setBarCount(
                    scaleMode == SCALE_MICRO ? 24 : scaleMode == SCALE_COMPACT ? 28
                            : scaleMode == SCALE_MEDIUM ? 34 : 40);
        }
        setSeatSize(holder.b.imgFrame, frame, ctx);
        setSeatSize(holder.b.imgHostBadge, frame, ctx);
        if (holder.b.imgRoomSupportMedal != null) setSeatSize(holder.b.imgRoomSupportMedal, scaleMode == SCALE_MICRO ? 20 : 30, ctx);
        if (holder.b.seatHostSignal != null) {
            setSeatSize(holder.b.seatHostSignal, frame, ctx);
        }
        setSeatSize(holder.b.imgSeatReaction, reaction, ctx);
        setSeatSize(holder.b.lottieSeatReaction, reaction, ctx);
        setSeatSize(holder.b.imgMuted, muted, ctx);
        positionMuteBadge(holder, frame, avatar, ctx);

        if (holder.b.tvName != null) {
            holder.b.tvName.setTextSize(nameSp);
            int nameWidthPx = Math.round(nameMax * d);
            holder.b.tvName.setMaxWidth(nameWidthPx);
            View nameContainer = (View) holder.b.tvName.getParent();
            ViewGroup.LayoutParams containerLp = nameContainer.getLayoutParams();
            containerLp.width = nameWidthPx;
            nameContainer.setLayoutParams(containerLp);
            ViewGroup.LayoutParams textLp = holder.b.tvName.getLayoutParams();
            textLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            holder.b.tvName.setLayoutParams(textLp);
            // Marquee activates only when the full name exceeds its viewport.
            holder.b.tvName.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            holder.b.tvName.setMarqueeRepeatLimit(-1);
            holder.b.tvName.setSelected(true);
            if (scaleMode == SCALE_MICRO) {
                View nameRow = (View) holder.b.tvName.getParent().getParent();
                ViewGroup.LayoutParams nameRowRaw = nameRow.getLayoutParams();
                if (nameRowRaw instanceof ViewGroup.MarginLayoutParams nameRowLp) {
                    nameRowLp.topMargin = 0;
                    nameRowLp.bottomMargin = 0;
                    nameRow.setLayoutParams(nameRowLp);
                }
                ViewGroup.LayoutParams nameRaw = holder.b.tvName.getLayoutParams();
                nameRaw.height = Math.round(16 * d);
                holder.b.tvName.setLayoutParams(nameRaw);
                View rankRow = holder.b.rowCustomDisplayRank;
                if (rankRow != null) {
                    ViewGroup.LayoutParams rankRaw = rankRow.getLayoutParams();
                    if (rankRaw instanceof ViewGroup.MarginLayoutParams rankLp) {
                        rankLp.topMargin = Math.round(7 * d);
                        rankRow.setLayoutParams(rankLp);
                    }
                }
            }
        }
        if (holder.b.imgSeatHostMark != null) {
            setSeatSize(holder.b.imgSeatHostMark, hostMark, ctx);
        }
        if (holder.b.tvGiftCount != null) {
            holder.b.tvGiftCount.setTextSize(scaleMode == SCALE_MICRO ? 8f : scaleMode == SCALE_COMPACT ? 9f : 10f);
        }
    }

    private static void positionMuteBadge(VH holder, int frameDp, int avatarDp, Context ctx) {
        if (holder.b.imgMuted == null) return;
        ViewGroup.LayoutParams raw = holder.b.imgMuted.getLayoutParams();
        if (!(raw instanceof android.widget.FrameLayout.LayoutParams lp)) return;
        float d = ctx.getResources().getDisplayMetrics().density;
        int insetPx = Math.round((frameDp - avatarDp) * 0.5f * d);
        int edgePx = Math.max(Math.round(2 * d), Math.round(avatarDp * 0.08f * d));
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        lp.setMarginEnd(insetPx + edgePx);
        lp.bottomMargin = insetPx + edgePx;
        lp.setMarginStart(0);
        lp.topMargin = 0;
        holder.b.imgMuted.setLayoutParams(lp);
    }

    private static void setSeatSize(@Nullable android.view.View view, int dp, Context ctx) {
        if (view == null) return;
        int px = Math.round(dp * ctx.getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        lp.width = px;
        lp.height = px;
        view.setLayoutParams(lp);
    }


    private void bindRoomSupportMedal(VH holder, @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (holder.b.imgRoomSupportMedal == null) return;
        // Use the same physical anchor in agency and personal rooms. When a frame is
        // equipped, pin the medal to the frame edge; otherwise pin it to the avatar.
        android.view.ViewGroup.LayoutParams raw = holder.b.imgRoomSupportMedal.getLayoutParams();
        if (raw instanceof android.widget.FrameLayout.LayoutParams lp) {
            float density = holder.b.imgRoomSupportMedal.getResources().getDisplayMetrics().density;
            int size = Math.round((scaleMode == SCALE_MICRO ? 20f : 30f) * density);
            android.view.View anchor = holder.b.imgAvatar;
            if (holder.b.imgFrame != null && holder.b.imgFrame.getVisibility() == View.VISIBLE) {
                anchor = holder.b.imgFrame;
            }
            android.view.ViewGroup parent = anchor != null && anchor.getParent() instanceof android.view.ViewGroup
                    ? (android.view.ViewGroup) anchor.getParent() : null;
            if (parent != null && holder.b.imgRoomSupportMedal.getParent() == parent) {
                android.view.ViewGroup.LayoutParams anchorParams = anchor.getLayoutParams();
                android.view.ViewGroup.LayoutParams parentParams = parent.getLayoutParams();
                int parentWidth = parent.getWidth() > 0 ? parent.getWidth()
                        : (parentParams != null && parentParams.width > 0
                            ? parentParams.width : Math.round(76f * density));
                int parentHeight = parent.getHeight() > 0 ? parent.getHeight()
                        : (parentParams != null && parentParams.height > 0
                            ? parentParams.height : Math.round(76f * density));
                int anchorWidth = anchor.getWidth() > 0 ? anchor.getWidth()
                        : (anchorParams != null && anchorParams.width > 0
                            ? anchorParams.width : Math.round(52f * density));
                int anchorHeight = anchor.getHeight() > 0 ? anchor.getHeight()
                        : (anchorParams != null && anchorParams.height > 0
                            ? anchorParams.height : Math.round(52f * density));
                int anchorLeft = anchor.getWidth() > 0 ? anchor.getLeft()
                        : Math.round((parentWidth - anchorWidth) / 2f);
                int anchorTop = anchor.getHeight() > 0 ? anchor.getTop()
                        : Math.round((parentHeight - anchorHeight) / 2f);
                // Center the medal over the upper-start corner: no room-type offsets.
                lp.width = size;
                lp.height = size;
                lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                int inset = Math.round(size * 0.25f);
                lp.setMarginStart(anchorLeft - inset);
                lp.topMargin = anchorTop - inset;
                lp.setMarginEnd(0);
                lp.rightMargin = 0;
                lp.bottomMargin = 0;
                holder.b.imgRoomSupportMedal.setLayoutParams(lp);
            }
        }
        String url = user != null && user.roomSupportMedal != null ? user.roomSupportMedal.imageUrl : null;
        if (url == null || url.trim().isEmpty()) {
            holder.b.imgRoomSupportMedal.setVisibility(View.GONE);
            Glide.with(holder.b.imgRoomSupportMedal).clear(holder.b.imgRoomSupportMedal);
            return;
        }
        holder.b.imgRoomSupportMedal.setVisibility(View.VISIBLE);
        holder.b.imgRoomSupportMedal.bringToFront();
        Glide.with(holder.b.imgRoomSupportMedal)
                .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(url))
                .fitCenter()
                .dontAnimate()
                .into(holder.b.imgRoomSupportMedal);
    }

    private void bindGiftCount(VH holder, String userId) {
        if (holder.b.tvGiftCount == null) {
            return;
        }
        Long coins = userId != null ? giftCoinsByUser.get(userId) : null;
        if (coins == null || coins <= 0) {
            holder.b.tvGiftCount.setVisibility(View.GONE);
            holder.lastGiftCoins = 0L;
            return;
        }
        holder.b.tvGiftCount.setVisibility(View.VISIBLE);
        holder.b.tvGiftCount.setText(formatGiftCoins(coins));
        int coinPx = Math.round(12 * holder.itemView.getResources().getDisplayMetrics().density);
        android.graphics.drawable.Drawable coin =
                androidx.core.content.ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.icon_coin);
        if (coin != null) {
            coin = coin.mutate();
            coin.setBounds(0, 0, coinPx, coinPx);
            holder.b.tvGiftCount.setCompoundDrawablesRelative(coin, null, null, null);
            holder.b.tvGiftCount.setCompoundDrawablePadding(
                    Math.round(3 * holder.itemView.getResources().getDisplayMetrics().density));
        }
        if (holder.lastGiftCoins != coins) {
            holder.lastGiftCoins = coins;
            holder.b.tvGiftCount.animate().cancel();
            holder.b.tvGiftCount.setScaleX(1.25f);
            holder.b.tvGiftCount.setScaleY(1.25f);
            holder.b.tvGiftCount.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start();
        }
    }

    private void bindGiftSelection(VH holder, @Nullable String userId) {
        boolean on = giftSelectionActive
                && userId != null
                && !userId.isEmpty()
                && giftSelectedUsers.contains(userId);
        if (holder.b.giftSelectRing != null) {
            holder.b.giftSelectRing.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        if (holder.b.giftSelectUnderline != null) {
            holder.b.giftSelectUnderline.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    public static String formatGiftCoinsLabel(long coins) {
        return formatGiftCoins(coins);
    }

    private static String formatGiftCoins(long coins) {
        if (coins >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", coins / 1_000_000f);
        }
        if (coins >= 1_000) {
            return String.format(Locale.US, "%.1fK", coins / 1_000f);
        }
        return String.valueOf(coins);
    }

    private void bindEmptySeatShell(VH holder, boolean locked) {
        if (holder.b.imgSeatShell != null) {
            holder.b.imgSeatShell.setImageResource(locked
                    ? R.drawable.icon_classic_seat_locked
                    : R.drawable.icon_classic_seat_normal);
            holder.b.imgSeatShell.setVisibility(View.VISIBLE);
        }
        if (holder.b.seatRing != null) {
            holder.b.seatRing.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("unused")
    private void bindSeatShell(VH holder, boolean hostSeat, boolean locked, boolean occupied) {
        if (holder.b.imgSeatShell == null) {
            return;
        }
        int res;
        if (locked) {
            res = R.drawable.icon_classic_seat_locked;
        } else if (occupied) {
            res = hostSeat ? R.drawable.seat_host_base : R.drawable.seat_base;
        } else {
            res = R.drawable.icon_classic_seat_normal;
        }
        holder.b.imgSeatShell.setImageResource(res);
        holder.b.imgSeatShell.setVisibility(View.VISIBLE);
    }

    private void bindReaction(VH holder, String userId) {
        if (holder.b.imgSeatReaction == null) {
            return;
        }
        Integer res = userId != null ? activeReactions.get(userId) : null;
        if (res == null || res == 0) {
            clearReactionView(holder);
            return;
        }
        if (holder.b.lottieSeatReaction != null) {
            holder.b.lottieSeatReaction.setVisibility(View.GONE);
            try {
                holder.b.lottieSeatReaction.cancelAnimation();
            } catch (Exception ignored) {
            }
        }
        holder.b.imgSeatReaction.setBackground(null);
        holder.b.imgSeatReaction.setImageDrawable(null);
        String key = activeReactionKeys.get(userId);
        if (key == null || key.isEmpty()) {
            key = null;
        }
        String uri = key != null ? SeatReactionEmojis.urlForKey(key) : null;
        // Circular overlay exactly matches the 76dp seat stage, covering the avatar/seat.
        android.graphics.drawable.GradientDrawable circularMask = new android.graphics.drawable.GradientDrawable();
        circularMask.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circularMask.setColor(android.graphics.Color.TRANSPARENT);
        holder.b.imgSeatReaction.setBackground(circularMask);
        holder.b.imgSeatReaction.setClipToOutline(true);
        holder.b.imgSeatReaction.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (uri != null) {
            if (uri.toLowerCase(java.util.Locale.US).contains(".gif")) {
                Glide.with(holder.b.imgSeatReaction)
                        .asGif()
                        .load(uri)
                        .centerCrop()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .error(res)
                        .into(holder.b.imgSeatReaction);
            } else {
                Glide.with(holder.b.imgSeatReaction)
                        .load(uri)
                        .centerCrop()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .error(res)
                        .into(holder.b.imgSeatReaction);
            }
        } else {
            holder.b.imgSeatReaction.setImageResource(res);
        }
        holder.b.imgSeatReaction.setVisibility(View.VISIBLE);
        holder.b.imgSeatReaction.bringToFront();
        holder.b.imgSeatReaction.setElevation(8f);
        holder.b.imgSeatReaction.animate().cancel();
        holder.b.imgSeatReaction.setScaleX(0.92f);
        holder.b.imgSeatReaction.setScaleY(0.92f);
        holder.b.imgSeatReaction.setAlpha(0f);
        holder.b.imgSeatReaction.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(new OvershootInterpolator(0.6f))
                .start();
    }

    private void clearReactionView(VH holder) {
        if (holder.b.lottieSeatReaction != null) {
            try {
                holder.b.lottieSeatReaction.cancelAnimation();
            } catch (Exception ignored) {
            }
            holder.b.lottieSeatReaction.setVisibility(View.GONE);
        }
        if (holder.b.imgSeatReaction != null) {
            holder.b.imgSeatReaction.animate().cancel();
            holder.b.imgSeatReaction.setVisibility(View.GONE);
            holder.b.imgSeatReaction.setImageDrawable(null);
        }
    }

    private void bindHostWear(VH holder, com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (user == null) {
            clearHostWear(holder);
            return;
        }
        if (user.mysteriousMan) {
            if (holder.b.seatHostSignal != null) {
                holder.b.seatHostSignal.clearSignal();
                holder.b.seatHostSignal.setVisibility(View.GONE);
            }
            holder.b.imgFrame.setVisibility(View.GONE);
            holder.b.imgHostBadge.setVisibility(View.GONE);
            String sharedAvatar = holder.itemView.getContext()
                    .getSharedPreferences("jeho_mysterious_mode", android.content.Context.MODE_PRIVATE)
                    .getString("avatar_url", "");
            if (sharedAvatar != null && !sharedAvatar.trim().isEmpty()) {
                com.Dramizo.Series.util.AvatarImageLoader.load(holder.b.imgAvatar, sharedAvatar.trim());
            } else {
                holder.b.imgAvatar.setImageResource(R.drawable.ic_mysterious_man);
            }
            return;
        }
        // Keep VIP wearable/frame on the seat; role/agency/competition marks are not seat wear.
        String hostUrl = null;
        // Dashboard VIP frame has priority for active VIP members; retain equipped wear as fallback.
        String vipUrl = AvatarCosmetics.avatarFrameUrl(user);
        java.util.Map<String, Object> badgeMeta = null;
        boolean self = selfUserId != null && selfUserId.equals(normalizeUserId(user.id));
        if (self) {
            if ((hostUrl == null || hostUrl.isEmpty()) && selfHostBadgeUrl != null) {
                hostUrl = selfHostBadgeUrl;
                if (badgeMeta == null) badgeMeta = selfHostBadgeMeta;
            }
            if ((vipUrl == null || vipUrl.isEmpty()) && selfVipBadgeUrl != null) {
                vipUrl = selfVipBadgeUrl;
            }
        }
        AvatarCosmetics.bindRoomWear(
                holder.b.seatHostSignal,
                holder.b.imgAvatar,
                holder.b.imgFrame,
                holder.b.imgHostBadge,
                user.avatarUrl,
                vipUrl,
                hostUrl,
                badgeMeta,
                agencyRoom,
                1);
        if (holder.b.seatHostSignal != null
                && holder.b.seatHostSignal.getVisibility() == View.VISIBLE) {
            holder.b.seatHostSignal.resumeMotion();
        }
    }

    private void bindVipNameplate(VH holder, @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (holder.b.imgVipNameplate == null) return;
        String url = user != null ? user.nameplateUrl : null;
        if (url == null || url.trim().isEmpty()) {
            Glide.with(holder.itemView).clear(holder.b.imgVipNameplate);
            holder.b.imgVipNameplate.setVisibility(View.GONE);
            return;
        }
        holder.b.imgVipNameplate.setVisibility(View.VISIBLE);
        Glide.with(holder.itemView)
                .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(url.trim()))
                .centerCrop()
                .into(holder.b.imgVipNameplate);
    }

    private void clearHostWear(VH holder) {
        if (holder.b.seatHostSignal != null) {
            holder.b.seatHostSignal.clearSignal();
            holder.b.seatHostSignal.setVisibility(View.GONE);
        }
        AvatarCosmetics.applyHostWear(holder.b.imgFrame, holder.b.imgHostBadge, holder.b.imgAvatar,
                null, null, null, null);
    }

    private void bindCustomDisplayRank(VH holder, @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        if (holder.b.rowCustomDisplayRank == null || holder.b.tvCustomRankBadge == null || holder.b.imgCustomRankBadge == null) return;
        // Platform/administrative identities are deliberately never shown
        // on microphone seats; their horizontal badge belongs to profile and chat UI.
        if (user != null && (StaffRoleHelper.isStaff(user) || user.isRechargeAgent)) {
            holder.b.rowCustomDisplayRank.setVisibility(View.GONE);
            holder.b.tvCustomRankBadge.setText("");
            Glide.with(holder.itemView).clear(holder.b.imgCustomRankBadge);
            holder.b.imgCustomRankBadge.setVisibility(View.GONE);
            return;
        }
        String title = user != null ? user.customDisplayRankName : null;
        String imageUrl = user != null ? user.customDisplayRankImageUrl : null;
        if (title == null || title.trim().isEmpty()) {
            holder.b.rowCustomDisplayRank.setVisibility(View.GONE);
            holder.b.tvCustomRankBadge.setText("");
            Glide.with(holder.itemView).clear(holder.b.imgCustomRankBadge);
            return;
        }
        holder.b.rowCustomDisplayRank.setVisibility(View.VISIBLE);
        holder.b.tvCustomRankBadge.setText(title.trim());
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            holder.b.imgCustomRankBadge.setVisibility(View.VISIBLE);
            // Glide animates GIF assets; static images remain static.
            Glide.with(holder.itemView).load(imageUrl).into(holder.b.imgCustomRankBadge);
        } else {
            holder.b.imgCustomRankBadge.setVisibility(View.GONE);
            Glide.with(holder.itemView).clear(holder.b.imgCustomRankBadge);
        }
    }

    private void bindAgencyRoleSeatBadge(VH holder,
            @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        TextView badge = holder.b.tvAgencyRoleBadge;
        if (badge == null) return;
        ViewGroup parent = badge.getParent() instanceof ViewGroup ? (ViewGroup) badge.getParent() : null;
        ImageView image = parent != null ? parent.findViewWithTag("jeho_agency_role_badge_image") : null;
        final ImageView[] imageRef = new ImageView[]{image};
        badge.setText("");
        badge.setVisibility(View.GONE);
        if (imageRef[0] != null) {
            Glide.with(holder.itemView).clear(imageRef[0]);
            imageRef[0].setVisibility(View.GONE);
        }
        if (user == null) return;

        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto.AgencySnip agency = user.agency;
        com.Dramizo.Series.data.remote.dto.AuthDtos.AgencyClubDto club = user.agencyClub;
        String role = "";
        String memberAgencyId = "";
        if (agency != null && agency.role != null) {
            role = agency.role.trim().toLowerCase(Locale.ROOT);
            memberAgencyId = agency.id == null ? "" : agency.id.trim();
        }
        if ((role.isEmpty() || "member".equals(role)) && club != null && club.role != null) {
            role = club.role.trim().toLowerCase(Locale.ROOT);
            memberAgencyId = club.agencyId == null ? "" : club.agencyId.trim();
        }
        boolean leader = "owner".equals(role) || "leader".equals(role);
        boolean host = "host".equals(role);
        if (!leader && !(host && agencyRoom && roomAgencyId != null
                && !roomAgencyId.isEmpty() && roomAgencyId.equals(memberAgencyId))) return;

        String key = leader ? "agencyLeader" : "host";
        String fallback = StaffRoleHelper.genderizeLabel(leader ? "قائد الوكالة" : "مضيف", user.gender);
        badge.setText(fallback);
        badge.setVisibility(View.VISIBLE);
        StaffIdentityConfigCache.load(holder.itemView.getContext(), () -> {
            if (badge.getParent() == null) return;
            StaffIdentityConfigCache.RoleIdentity identity = StaffIdentityConfigCache.get(key);
            String label = identity.label == null || identity.label.trim().isEmpty() ? fallback
                    : StaffRoleHelper.genderizeLabel(identity.label, user.gender);
            if (identity.badgeUrl != null && !identity.badgeUrl.trim().isEmpty() && parent != null) {
                badge.setVisibility(View.GONE);
                ImageView target = imageRef[0];
                if (target == null) {
                    target = new ImageView(holder.itemView.getContext());
                    target.setTag("jeho_agency_role_badge_image");
                    target.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int size = Math.round(16 * holder.itemView.getResources().getDisplayMetrics().density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.topMargin = Math.round(2 * holder.itemView.getResources().getDisplayMetrics().density);
                    int index = Math.min(parent.indexOfChild(badge) + 1, parent.getChildCount());
                    parent.addView(target, index, lp);
                    imageRef[0] = target;
                }
                target.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView).load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(identity.badgeUrl.trim())).into(target);
                target.setContentDescription(label);
            } else {
                if (imageRef[0] != null) {
                    Glide.with(holder.itemView).clear(imageRef[0]);
                    imageRef[0].setVisibility(View.GONE);
                }
                badge.setText(label);
                badge.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindStaffSeatBadge(VH holder, @Nullable com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user) {
        View superFrame = holder.itemView.findViewById(R.id.superAdminSeatFrame);
        boolean isSuperAdmin = user != null && StaffRoleHelper.isSuper(user);
        if (superFrame != null) {
            superFrame.setVisibility(isSuperAdmin ? View.VISIBLE : View.GONE);
            if (isSuperAdmin) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                ring.setStroke(Math.max(2, Math.round(holder.itemView.getResources().getDisplayMetrics().density * 2)),
                        Color.rgb(255, 194, 64));
                superFrame.setBackground(ring);
            }
        }
        if (holder.b.tvStaffSeatBadge == null) return;
        ViewGroup parent = holder.b.tvStaffSeatBadge.getParent() instanceof ViewGroup
                ? (ViewGroup) holder.b.tvStaffSeatBadge.getParent() : null;
        ImageView roleImage = parent != null ? parent.findViewWithTag("jeho_staff_role_badge_image") : null;
        final ImageView[] roleImageRef = new ImageView[]{roleImage};
        if (user == null || !StaffRoleHelper.isStaff(user)) {
            holder.b.tvStaffSeatBadge.setVisibility(View.GONE);
            holder.b.tvStaffSeatBadge.setTag(null);
            holder.b.tvStaffSeatBadge.setText("");
            if (roleImageRef[0] != null) {
                Glide.with(holder.itemView).clear(roleImageRef[0]);
                roleImageRef[0].setVisibility(View.GONE);
            }
            return;
        }
        String role = StaffRoleHelper.normalize(user);
        StaffIdentityConfigCache.RoleIdentity identity = StaffIdentityConfigCache.get(role);
        String fallbackLabel = StaffRoleHelper.badgeAr(user);
        holder.b.tvStaffSeatBadge.setTag(role);
        holder.b.tvStaffSeatBadge.setText(identity.label.isEmpty() ? fallbackLabel : identity.label);
        holder.b.tvStaffSeatBadge.setBackgroundResource(
                StaffRoleHelper.SUPER.equals(role) ? R.drawable.bg_chip_staff_super : R.drawable.bg_chip_staff_manager);
        holder.b.tvStaffSeatBadge.setVisibility(View.VISIBLE);
        StaffIdentityConfigCache.load(holder.itemView.getContext(), () -> {
            if (holder.b.tvStaffSeatBadge == null || !role.equals(holder.b.tvStaffSeatBadge.getTag())) return;
            StaffIdentityConfigCache.RoleIdentity latest = StaffIdentityConfigCache.get(role);
            String label = latest.label.isEmpty() ? fallbackLabel : latest.label;
            if (latest.badgeUrl != null && !latest.badgeUrl.trim().isEmpty() && parent != null) {
                holder.b.tvStaffSeatBadge.setVisibility(View.GONE);
                ImageView target = roleImageRef[0];
                if (target == null) {
                    target = new ImageView(holder.itemView.getContext());
                    target.setTag("jeho_staff_role_badge_image");
                    target.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int size = Math.round(16 * holder.itemView.getResources().getDisplayMetrics().density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.topMargin = Math.round(2 * holder.itemView.getResources().getDisplayMetrics().density);
                    int index = Math.min(parent.indexOfChild(holder.b.tvStaffSeatBadge) + 1, parent.getChildCount());
                    parent.addView(target, index, lp);
                    roleImageRef[0] = target;
                }
                target.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView).load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(latest.badgeUrl.trim())).into(target);
                target.setContentDescription(label);
            } else {
                if (roleImageRef[0] != null) {
                    Glide.with(holder.itemView).clear(roleImageRef[0]);
                    roleImageRef[0].setVisibility(View.GONE);
                }
                holder.b.tvStaffSeatBadge.setText(label);
                holder.b.tvStaffSeatBadge.setVisibility(View.VISIBLE);
            }
            if (!latest.frameUrl.isEmpty() && holder.b.imgFrame != null) {
                try {
                    Glide.with(holder.itemView).load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(latest.frameUrl))
                            .into(holder.b.imgFrame);
                    com.Dramizo.Series.util.StaffIdentityFrameMotion.start(holder.b.imgFrame);
                } catch (Exception ignored) { }
            }
        });
    }

    private void bindSpeaking(VH holder, boolean speaking) {
        if (!speaking) {
            stopSpeakingRipples(holder);
            return;
        }
        holder.speaking = true;
        String waveUrl = holder.voiceWaveUrl;
        boolean hasEquippedWave = waveUrl != null && !waveUrl.trim().isEmpty();

        // Exactly one visual ring: the equipped asset OR the built-in seat aura.
        if (holder.b.imgVoiceWaveOuter != null) {
            holder.b.imgVoiceWaveOuter.animate().cancel();
            holder.b.imgVoiceWaveOuter.clearAnimation();
            holder.b.imgVoiceWaveOuter.setVisibility(View.GONE);
        }
        if (hasEquippedWave && holder.b.imgVoiceWave != null) {
            android.widget.ImageView wave = holder.b.imgVoiceWave;
            wave.animate().cancel();
            wave.setAlpha(1f);
            wave.setScaleX(1f);
            wave.setScaleY(1f);
            wave.setVisibility(View.VISIBLE);
            wave.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            wave.setClipToOutline(false);
            wave.setOutlineProvider(null);
            if (!Objects.equals(holder.loadedVoiceWaveUrl, waveUrl.trim())) {
                String absoluteWave = com.Dramizo.Series.util.AssetCatalog.absoluteUrl(waveUrl.trim());
                Glide.with(holder.itemView).clear(wave);
                Glide.with(holder.itemView).load(absoluteWave).into(wave);
                holder.loadedVoiceWaveUrl = waveUrl.trim();
            }
            startEquippedVoiceWaveMotion(holder);
        } else if (holder.b.imgVoiceWave != null) {
            holder.b.imgVoiceWave.animate().cancel();
            holder.b.imgVoiceWave.clearAnimation();
            holder.b.imgVoiceWave.setVisibility(View.GONE);
            stopEquippedVoiceWaveMotion(holder);
            if (holder.loadedVoiceWaveUrl != null) {
                Glide.with(holder.itemView).clear(holder.b.imgVoiceWave);
                holder.loadedVoiceWaveUrl = null;
            }
        }

        String uid = holder.boundUserId;
        float liveLevel = uid != null && speakingLevels.containsKey(uid)
                ? speakingLevels.get(uid) : 48f;
        if (holder.b.seatSpeakAura != null) {
            holder.b.seatSpeakAura.setSpeaking(false);
            holder.b.seatSpeakAura.setVisibility(View.GONE);
        }
        if (holder.b.seatVoiceWaveBars != null) {
            if (hasEquippedWave) {
                holder.b.seatVoiceWaveBars.setSpeaking(false, 0f);
                holder.b.seatVoiceWaveBars.setVisibility(View.GONE);
            } else {
                holder.b.seatVoiceWaveBars.setVisibility(View.VISIBLE);
                holder.b.seatVoiceWaveBars.setSpeaking(true, liveLevel);
            }
        }
        stopRipple(holder.b.seatRippleOuter);
        stopRipple(holder.b.seatRippleMid);
        stopRipple(holder.b.seatRippleInner);
    }

    /** The equipped image is one ring at the avatar boundary; only its rotation is continuous. */
    private void startEquippedVoiceWaveMotion(VH holder) {
        if (holder.b.imgVoiceWave == null) return;
        holder.b.imgVoiceWave.animate().cancel();
        holder.b.imgVoiceWave.clearAnimation();
        RotateAnimation rotate = new RotateAnimation(
                0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(1250L);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setRepeatMode(Animation.RESTART);
        rotate.setInterpolator(new android.view.animation.LinearInterpolator());
        holder.b.imgVoiceWave.startAnimation(rotate);
    }

    private void ensureEquippedVoiceWaveMotion(VH holder) {
        if (holder.b.imgVoiceWave != null && holder.b.imgVoiceWave.getAnimation() == null) {
            startEquippedVoiceWaveMotion(holder);
        }
    }

    private void stopEquippedVoiceWaveMotion(VH holder) {
        if (holder.b.imgVoiceWave != null) {
            holder.b.imgVoiceWave.clearAnimation();
            holder.b.imgVoiceWave.setScaleX(1f);
            holder.b.imgVoiceWave.setScaleY(1f);
            holder.b.imgVoiceWave.setRotation(0f);
            holder.b.imgVoiceWave.setAlpha(1f);
        }
    }

    private void stopSpeakingRipples(VH holder) {
        holder.speaking = false;
        if (holder.b.seatSpeakAura != null) {
            // SeatSpeakLightningView owns its short fade-out and hides itself afterwards.
            holder.b.seatSpeakAura.setSpeaking(false);
        }
        if (holder.b.seatVoiceWaveBars != null) {
            holder.b.seatVoiceWaveBars.setSpeaking(false, 0f);
            holder.b.seatVoiceWaveBars.setVisibility(View.GONE);
        }
        if (holder.b.imgVoiceWaveOuter != null) {
            holder.b.imgVoiceWaveOuter.animate().cancel();
            holder.b.imgVoiceWaveOuter.setVisibility(View.GONE);
        }
        if (holder.b.imgVoiceWave != null) {
            android.widget.ImageView wave = holder.b.imgVoiceWave;
            wave.clearAnimation();
            wave.animate().cancel();
            wave.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
                    .setDuration(130L)
                    .withEndAction(() -> {
                        if (holder.speaking) return;
                        wave.setVisibility(View.GONE);
                        wave.setScaleX(1f);
                        wave.setScaleY(1f);
                        wave.setAlpha(1f);
                        stopEquippedVoiceWaveMotion(holder);
                        Glide.with(holder.itemView).clear(wave);
                        holder.loadedVoiceWaveUrl = null;
                    }).start();
        }
        stopRipple(holder.b.seatRippleOuter);
        stopRipple(holder.b.seatRippleMid);
        stopRipple(holder.b.seatRippleInner);
    }

    private void stopRipple(View view) {
        if (view == null) return;
        view.clearAnimation();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        view.setVisibility(View.INVISIBLE);
    }

    private void startRipple(View view, long offset) {
        if (view == null || view.getVisibility() == View.GONE) return;
        view.clearAnimation();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        AnimationSet set = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(
                0.9f, 1.35f, 0.9f, 1.35f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        AlphaAnimation alpha = new AlphaAnimation(0.95f, 0.05f);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        set.setDuration(720);
        set.setStartOffset(offset);
        set.setRepeatCount(Animation.INFINITE);
        set.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        view.startAnimation(set);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VH holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.b.seatHostSignal != null
                && holder.b.seatHostSignal.getVisibility() == View.VISIBLE) {
            holder.b.seatHostSignal.resumeMotion();
        }
        if (holder.speaking && (holder.voiceWaveUrl == null || holder.voiceWaveUrl.trim().isEmpty())
                && holder.b.seatVoiceWaveBars != null) {
            String uid = holder.boundUserId;
            float liveLevel = uid != null && speakingLevels.containsKey(uid)
                    ? speakingLevels.get(uid) : 48f;
            holder.b.seatVoiceWaveBars.setVisibility(View.VISIBLE);
            holder.b.seatVoiceWaveBars.setSpeaking(true, liveLevel);
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        cancelReaction(holder.boundUserId);
        holder.boundUserId = null;
        holder.voiceWaveUrl = null;
        holder.loadedVoiceWaveUrl = null;
        holder.lastGiftCoins = Long.MIN_VALUE;
        // Do NOT pause HostSignalView here — grid refresh recycles often and left frames frozen.
        stopSpeakingRipples(holder);
        com.Dramizo.Series.util.StaffIdentityFrameMotion.stop(holder.b.imgFrame);
        clearReactionView(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        cleanup();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private void cancelReaction(String userId) {
        if (userId == null || userId.isEmpty()) return;
        Runnable hide = hideReactionRunnables.remove(userId);
        if (hide != null) handler.removeCallbacks(hide);
        activeReactions.remove(userId);
        activeReactionKeys.remove(userId);
    }

    /** Cancels all delayed reaction work when the owning room view is torn down. */
    public void cleanup() {
        for (Runnable hide : hideReactionRunnables.values()) {
            handler.removeCallbacks(hide);
        }
        hideReactionRunnables.clear();
        for (Runnable stop : speakingStopRunnables.values()) {
            handler.removeCallbacks(stop);
        }
        speakingStopRunnables.clear();
        activeReactions.clear();
        activeReactionKeys.clear();
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position).seatIndex;
        }
        return RecyclerView.NO_ID;
    }

    /** Stable user lookup for lightweight gift-flight overlays. */
    @Nullable
    public String getUserIdAtPosition(int position) {
        if (position < 0 || position >= items.size()) return null;
        return seatUserId(items.get(position));
    }

    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSeatBinding b;
        String boundUserId;
        String voiceWaveUrl;
        String loadedVoiceWaveUrl;
        boolean speaking;
        long lastGiftCoins = Long.MIN_VALUE;

        VH(ItemSeatBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
