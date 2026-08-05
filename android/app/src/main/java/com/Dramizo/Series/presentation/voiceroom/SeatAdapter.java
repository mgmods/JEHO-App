package com.Dramizo.Series.presentation.voiceroom;

import com.Dramizo.Series.util.ImagePlaceholder;

import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ItemSeatBinding;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.SeatReactionEmojis;
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
    private final Set<String> speakingUsers = new HashSet<>();
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
        for (int i = 0; i < items.size(); i++) {
            RoomDtos.SeatDto seat = items.get(i);
            if (seat != null && selfUserId.equals(seatUserId(seat))) {
                notifyItemChanged(i, "mute");
                return;
            }
        }
    }

    public void setScaleMode(int mode) {
        int next = Math.max(SCALE_NORMAL, Math.min(SCALE_COMPACT, mode));
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
        if (agencyRoom == agency) return;
        agencyRoom = agency;
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
            if (isBlank(seat.user.levelBadgeUrl) && !isBlank(prev.levelBadgeUrl)) {
                seat.user.levelBadgeUrl = prev.levelBadgeUrl;
            }
            if (isBlank(seat.user.displayName) && !isBlank(prev.displayName)) {
                seat.user.displayName = prev.displayName;
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
                && Objects.equals(a.user.levelBadgeUrl, b.user.levelBadgeUrl)
                && Objects.equals(a.user.hostBadgeMeta, b.user.hostBadgeMeta)
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
        String key = normalizeUserId(userId);
        if (key.isEmpty()) {
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
            if (speaking) speakingUsers.remove(key);
            return;
        }
        boolean changed = speaking ? speakingUsers.add(key) : speakingUsers.remove(key);
        if (!changed) {
            return;
        }
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
        // Legacy: resolve by drawable index when unique; shared fallback cannot map alone.
        if (emojiResId != 0 && emojiResId != SeatReactionEmojis.FALLBACK_DRAWABLE) {
            for (int i = 0; i < SeatReactionEmojis.DRAWABLES.length; i++) {
                if (SeatReactionEmojis.DRAWABLES[i] == emojiResId) {
                    return SeatReactionEmojis.KEYS[i];
                }
            }
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
                    boolean match = userId != null
                            && speakingUsers.contains(userId)
                            && !activeReactions.containsKey(userId);
                    bindSpeaking(holder, match);
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
            bindEmptySeatShell(holder, true);
            holder.b.seatRing.setVisibility(View.GONE);
            holder.b.tvName.setText(String.valueOf(displaySeatNo(seat)));
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

            if (seat.user != null) {
                holder.b.tvName.setText(seat.user.displayName != null
                        ? seat.user.displayName
                        : String.valueOf(displaySeatNo(seat)));
                holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
                AvatarCosmetics.bindAvatar(holder.b.imgAvatar, seat.user.avatarUrl);
                try {
                    bindHostWear(holder, seat.user);
                } catch (Exception ignored) {
                    AvatarCosmetics.bindAvatar(holder.b.imgAvatar, seat.user.avatarUrl);
                }
            } else if (selfUserId != null && selfUserId.equals(userId)) {
                // Seat hop race: userId set before nested user DTO arrives — keep self visible.
                holder.b.tvName.setText(selfDisplayName != null && !selfDisplayName.isEmpty()
                        ? selfDisplayName : String.valueOf(displaySeatNo(seat)));
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
                holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
            }

            bindGiftCount(holder, userId);
            bindGiftSelection(holder, userId);

            if (holder.b.imgSeatHostMark != null) {
                holder.b.imgSeatHostMark.setVisibility(isCreator ? View.VISIBLE : View.GONE);
            }
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

        bindEmptySeatShell(holder, false);
        holder.b.seatRing.setVisibility(View.GONE);
        holder.b.imgAvatar.setVisibility(View.GONE);
        if (holder.b.imgSeatShell != null) {
            holder.b.imgSeatShell.setVisibility(View.VISIBLE);
        }
        holder.b.tvName.setText(String.valueOf(displaySeatNo(seat)));
        holder.b.tvName.setCompoundDrawablesRelative(null, null, null, null);
        if (holder.b.imgSeatHostMark != null) {
            holder.b.imgSeatHostMark.setVisibility(View.GONE);
        }
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
            case SCALE_COMPACT -> {
                // Mikoo 20-mic: container 64dp, avatar 48dp
                frame = 64;
                avatar = 48;
                ring = 50;
                reaction = 50;
                muted = 14;
                nameMax = 60;
                nameSp = 10f;
                padTop = 1;
                padBottom = 2;
                hostMark = 11;
            }
            case SCALE_MEDIUM -> {
                // Between normal and compact (15 mics / 3 rows)
                frame = 70;
                avatar = 52;
                ring = 54;
                reaction = 54;
                muted = 15;
                nameMax = 66;
                nameSp = 10.5f;
                padTop = 2;
                padBottom = 3;
                hostMark = 12;
            }
            default -> {
                // Mikoo default item_rv_multi_audio_micro: 80 / 55
                frame = 80;
                avatar = 55;
                ring = 55;
                reaction = 56;
                muted = 16;
                nameMax = 72;
                nameSp = 11f;
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
        setSeatSize(frameView, frame, ctx);
        setSeatSize(holder.b.seatRing, ring, ctx);
        setSeatSize(holder.b.seatRippleOuter, frame, ctx);
        setSeatSize(holder.b.seatRippleMid, Math.round(frame * 0.89f), ctx);
        setSeatSize(holder.b.seatRippleInner, Math.round(frame * 0.79f), ctx);
        setSeatSize(holder.b.imgSeatShell, avatar, ctx);
        setSeatSize(holder.b.imgAvatar, avatar, ctx);
        setSeatSize(holder.b.imgFrame, frame, ctx);
        setSeatSize(holder.b.imgHostBadge, frame, ctx);
        if (holder.b.seatHostSignal != null) {
            setSeatSize(holder.b.seatHostSignal, frame, ctx);
        }
        setSeatSize(holder.b.imgSeatReaction, reaction, ctx);
        setSeatSize(holder.b.lottieSeatReaction, reaction, ctx);
        setSeatSize(holder.b.imgMuted, muted, ctx);
        positionMuteBadge(holder, frame, avatar, ctx);

        if (holder.b.tvName != null) {
            holder.b.tvName.setTextSize(nameSp);
            holder.b.tvName.setMaxWidth(Math.round(nameMax * d));
        }
        if (holder.b.imgSeatHostMark != null) {
            setSeatSize(holder.b.imgSeatHostMark, hostMark, ctx);
        }
        if (holder.b.tvGiftCount != null) {
            holder.b.tvGiftCount.setTextSize(scaleMode == SCALE_COMPACT ? 9f : 10f);
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
        String uri = key != null ? SeatReactionEmojis.assetUriForKey(key) : null;
        holder.b.imgSeatReaction.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (uri != null) {
            // GIF pack (e01-e26) + Mikoo animated WebP (e27+).
            if (SeatReactionEmojis.isWebpKey(key)) {
                Glide.with(holder.b.imgSeatReaction)
                        .load(uri)
                        .fitCenter()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .error(res)
                        .into(holder.b.imgSeatReaction);
            } else {
                Glide.with(holder.b.imgSeatReaction)
                        .asGif()
                        .load(uri)
                        .fitCenter()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
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
        String hostUrl = user.hostBadgeUrl;
        String vipUrl = user.vipBadgeUrl;
        java.util.Map<String, Object> badgeMeta = user.hostBadgeMeta;
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

    private void clearHostWear(VH holder) {
        if (holder.b.seatHostSignal != null) {
            holder.b.seatHostSignal.clearSignal();
            holder.b.seatHostSignal.setVisibility(View.GONE);
        }
        AvatarCosmetics.applyHostWear(holder.b.imgFrame, holder.b.imgHostBadge, holder.b.imgAvatar,
                null, null, null, null);
    }

    private void bindSpeaking(VH holder, boolean speaking) {
        if (!speaking) {
            // Always force-hide: recycled holders can keep VISIBLE ripples while speaking=false.
            stopSpeakingRipples(holder);
            return;
        }
        if (holder.speaking
                && holder.b.seatRippleOuter.getVisibility() == View.VISIBLE
                && holder.b.seatRippleOuter.getAnimation() != null) {
            return;
        }
        holder.speaking = true;
        holder.b.seatRippleOuter.setVisibility(View.VISIBLE);
        holder.b.seatRippleOuter.setAlpha(1f);
        if (holder.b.seatRippleMid != null) {
            holder.b.seatRippleMid.setVisibility(View.VISIBLE);
            holder.b.seatRippleMid.setAlpha(1f);
        }
        holder.b.seatRippleInner.setVisibility(View.VISIBLE);
        holder.b.seatRippleInner.setAlpha(1f);
        startRipple(holder.b.seatRippleOuter, 0);
        if (holder.b.seatRippleMid != null) startRipple(holder.b.seatRippleMid, 280);
        startRipple(holder.b.seatRippleInner, 520);
    }

    private void stopSpeakingRipples(VH holder) {
        holder.speaking = false;
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
        if (view == null) return;
        view.clearAnimation();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        AnimationSet set = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(
                0.94f, 1.14f, 0.94f, 1.14f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        AlphaAnimation alpha = new AlphaAnimation(0.85f, 0.08f);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        set.setDuration(900);
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
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        cancelReaction(holder.boundUserId);
        holder.boundUserId = null;
        holder.lastGiftCoins = Long.MIN_VALUE;
        // Do NOT pause HostSignalView here — grid refresh recycles often and left frames frozen.
        stopSpeakingRipples(holder);
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemSeatBinding b;
        String boundUserId;
        boolean speaking;
        long lastGiftCoins = Long.MIN_VALUE;

        VH(ItemSeatBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
