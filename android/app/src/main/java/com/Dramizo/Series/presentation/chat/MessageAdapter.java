package com.Dramizo.Series.presentation.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.AgencyInviteCodec;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.CosmeticMedia;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.RemoteTheme;
import com.Dramizo.Series.util.RoomShareCodec;
import com.Dramizo.Series.util.VipStyle;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
    private static final int TYPE_PEER = 0;
    private static final int TYPE_MINE = 1;

    public interface Listener {
        void onLongClick(ChatDtos.MessageDto msg);
        void onSwipeReply(ChatDtos.MessageDto msg);
        void onTranslate(ChatDtos.MessageDto msg, int adapterPosition);
        default void onAvatarClick(String userId) {}
        /** Recipient tapped أوافق on a family/agency invite card. */
        default void onAgencyInviteAgree(ChatDtos.MessageDto msg, AgencyInviteCodec.Parsed invite) {}
        /** Recipient tapped لا أوافق → hide the invite bubble. */
        default void onAgencyInviteDisagree(ChatDtos.MessageDto msg, AgencyInviteCodec.Parsed invite) {}
    }

    private final List<ChatDtos.MessageDto> items = new ArrayList<>();
    private final Map<String, String> translations = new HashMap<>();
    private final String myUserId;
    private final AuthDtos.UserDto me;
    private final Listener listener;
    private String peerAvatarUrl;
    private String peerHostBadgeUrl;
    private java.util.Map<String, Object> peerHostBadgeMeta;
    private String peerDisplayName;
    private int peerVipLevel;
    private int peerLevel = 1;
    private long peerLastReadAtMs;
    private final Set<String> playedAudioIds = new HashSet<>();
    private MediaPlayer playing;
    private String playingUrl;
    private String playingMessageId;
    private ImageView playingIcon;
    private View playingPlayWrap;
    private AudioWaveformView playingWave;
    private final Handler audioHandler = new Handler(Looper.getMainLooper());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Short TTL cache so scroll rebinds don't hammer GET /rooms/:id. */
    private static final long ROOM_LIVE_CACHE_MS = 25_000L;
    private static final Map<String, RoomLiveSnap> ROOM_LIVE_CACHE = new ConcurrentHashMap<>();
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (playing == null || playingWave == null) return;
            try {
                if (playing.isPlaying()) {
                    int pos = playing.getCurrentPosition();
                    int dur = Math.max(1, playing.getDuration());
                    playingWave.setProgress(pos / (float) dur);
                    audioHandler.postDelayed(this, 50);
                }
            } catch (Exception ignored) {}
        }
    };

    public MessageAdapter(String myUserId, AuthDtos.UserDto me, Listener listener) {
        this.myUserId = myUserId;
        this.me = me;
        this.listener = listener;
    }

    public void setPeerProfile(
            String displayName,
            String avatarUrl,
            String hostBadgeUrl,
            java.util.Map<String, Object> hostBadgeMeta,
            int vipLevel,
            int level) {
        this.peerDisplayName = displayName;
        this.peerAvatarUrl = avatarUrl;
        this.peerHostBadgeUrl = hostBadgeUrl;
        this.peerHostBadgeMeta = hostBadgeMeta;
        this.peerVipLevel = Math.max(0, vipLevel);
        this.peerLevel = Math.max(1, level);
        notifyDataSetChanged();
    }

    public void setPeerProfile(AuthDtos.UserDto peer) {
        if (peer == null) {
            setPeerProfile(null, null, null, null, 0, 1);
            return;
        }
        setPeerProfile(
                peer.displayName != null ? peer.displayName : peer.username,
                peer.avatarUrl,
                peer.vipBadgeUrl,
                null,
                peer.vipLevel,
                peer.level > 0 ? peer.level : 1);
    }

    public void setPeerLastReadAt(String iso) {
        long ms = parseIsoMs(iso);
        if (ms == peerLastReadAtMs) return;
        peerLastReadAtMs = ms;
        notifyDataSetChanged();
    }

    public void setPeerLastReadAtMs(long ms) {
        if (ms <= peerLastReadAtMs) return;
        peerLastReadAtMs = ms;
        notifyDataSetChanged();
    }

    private static long parseIsoMs(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        return 0L;
    }

    public ChatDtos.MessageDto getItem(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public void submit(List<ChatDtos.MessageDto> data) {
        // Chat updates are frequent. Avoid clearing/rebinding the whole RecyclerView on
        // every realtime message; that causes visible flicker and dropped frames while typing.
        List<ChatDtos.MessageDto> next = data != null ? data : Collections.emptyList();
        int oldSize = items.size();
        boolean samePrefix = next.size() >= oldSize;
        if (samePrefix) {
            for (int i = 0; i < oldSize; i++) {
                ChatDtos.MessageDto a = items.get(i);
                ChatDtos.MessageDto b = next.get(i);
                String aid = a != null ? a.id : null;
                String bid = b != null ? b.id : null;
                if (aid == null ? bid != null : !aid.equals(bid)) {
                    samePrefix = false;
                    break;
                }
            }
        }
        if (samePrefix && next.size() > oldSize) {
            items.addAll(next.subList(oldSize, next.size()));
            notifyItemRangeInserted(oldSize, next.size() - oldSize);
            return;
        }
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    public void setTranslation(String messageId, String translated) {
        if (messageId == null) return;
        if (translated == null || translated.isEmpty()) translations.remove(messageId);
        else translations.put(messageId, translated);
        for (int i = 0; i < items.size(); i++) {
            ChatDtos.MessageDto m = items.get(i);
            if (m != null && messageId.equals(m.id)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void clearTranslation(String messageId) {
        setTranslation(messageId, null);
    }

    public boolean hasTranslation(String messageId) {
        return messageId != null && translations.containsKey(messageId);
    }

    public void releasePlayer() {
        audioHandler.removeCallbacks(progressTick);
        if (playingIcon != null) {
            playingIcon.setImageResource(R.drawable.ic_audio_play);
            playingIcon = null;
        }
        if (playingPlayWrap != null && playingMessageId != null) {
            applyAudioPlayStyle(playingPlayWrap, playedAudioIds.contains(playingMessageId));
            playingPlayWrap = null;
        }
        playingMessageId = null;
        if (playingWave != null) {
            playingWave.setAnimating(false);
            playingWave.setProgress(0f);
            playingWave = null;
        }
        if (playing != null) {
            try { playing.stop(); } catch (Exception ignored) {}
            try { playing.release(); } catch (Exception ignored) {}
            playing = null;
            playingUrl = null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return isMine(items.get(position)) ? TYPE_MINE : TYPE_PEER;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_MINE
                ? R.layout.item_message_mine
                : R.layout.item_message_peer;
        View root = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(root);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatDtos.MessageDto msg = items.get(position);
        boolean mine = holder.getItemViewType() == TYPE_MINE || isMine(msg);

        bindClusterSpacing(holder, position, mine, msg);
        bindSideChrome(holder, msg, mine, position);

        if (holder.b.tvVipChip != null) holder.b.tvVipChip.setVisibility(View.GONE);
        if (holder.b.rowBadges != null) holder.b.rowBadges.setVisibility(View.GONE);
        if (holder.b.tvMeta != null) holder.b.tvMeta.setVisibility(View.GONE);

        // Soft me/peer bubbles; paid VIP skins (1–7) for text/voice when sender has VIP.
        // Cards / photos / gifts keep their own chrome so VIP 9-patches don't collapse.
        boolean cardLike = isCardMessage(msg);
        boolean chromeFree = cardLike || isBareImageMessage(msg);
        boolean giftLike = isGiftType(msg);
        int vipLevel = resolveVipLevel(holder, msg, mine);
        boolean useVipSkin = vipLevel > 0 && !chromeFree && !giftLike;
        if (chromeFree) {
            holder.b.bubbleRoot.setBackgroundResource(android.R.color.transparent);
            holder.b.bubbleRoot.setPadding(0, 0, 0, 0);
        } else if (giftLike) {
            int padH = dp(holder, 12);
            int padT = dp(holder, 8);
            int padB = dp(holder, 6);
            holder.b.bubbleRoot.setPadding(padH, padT, padH, padB);
            holder.b.bubbleRoot.setBackgroundResource(R.drawable.bg_chat_bubble_peer);
        } else if (useVipSkin) {
            // Paid VIP bubble skins apply to BOTH mine and peer (sender's VIP tier).
            VipStyle.applyBubble(holder.b.bubbleRoot, vipLevel);
            int padH = dp(holder, 16);
            int padT = dp(holder, 12);
            int padB = dp(holder, 10);
            holder.b.bubbleRoot.setPadding(padH, padT, padH, padB);
        } else {
            int padH = dp(holder, 12);
            int padT = dp(holder, 8);
            int padB = dp(holder, 6);
            holder.b.bubbleRoot.setPadding(padH, padT, padH, padB);
            if (mine) {
                applyBrandMineBubble(holder.b.bubbleRoot);
            } else {
                holder.b.bubbleRoot.setBackgroundResource(R.drawable.bg_chat_bubble_peer);
            }
        }

        // VIP-aware body colors (white on rose/VIP; dark on peer light gray).
        applyBubbleInk(holder, mine, useVipSkin ? vipLevel : 0);

        bindReplyQuote(holder, msg);
        bindBody(holder, msg);

        String time = DeviceTimeFormat.messageTime(holder.itemView.getContext(), msg.createdAt);
        if (msg.isEdited) time = time + " · معدّلة";
        holder.b.tvTime.setText(time);
        bindImageTimeOverlay(holder, msg, time);
        bindStatusTick(holder, msg, mine);
        bindDaySeparator(holder, position, msg);

        View.OnLongClickListener longClick = v -> {
            if (listener != null) listener.onLongClick(msg);
            return true;
        };
        holder.itemView.setOnLongClickListener(longClick);
        holder.b.bubbleRoot.setOnLongClickListener(longClick);
    }

    private void bindDaySeparator(
            @NonNull VH holder, int position, @NonNull ChatDtos.MessageDto msg) {
        if (holder.b.tvDaySeparator == null) return;
        boolean show = position == 0 || !sameCalendarDay(
                items.get(position - 1).createdAt, msg.createdAt);
        if (!show) {
            holder.b.tvDaySeparator.setVisibility(View.GONE);
            return;
        }
        String label = daySeparatorLabel(holder.itemView.getContext(), msg.createdAt);
        if (label.isEmpty()) {
            holder.b.tvDaySeparator.setVisibility(View.GONE);
            return;
        }
        holder.b.tvDaySeparator.setText(label);
        holder.b.tvDaySeparator.setVisibility(View.VISIBLE);
    }

    private static boolean sameCalendarDay(@Nullable String a, @Nullable String b) {
        java.util.Date da = DeviceTimeFormat.parse(a);
        java.util.Date db = DeviceTimeFormat.parse(b);
        if (da == null || db == null) return a != null && a.equals(b);
        java.util.Calendar ca = java.util.Calendar.getInstance();
        java.util.Calendar cb = java.util.Calendar.getInstance();
        ca.setTime(da);
        cb.setTime(db);
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR)
                && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR);
    }

    @NonNull
    private static String daySeparatorLabel(@NonNull Context context, @Nullable String iso) {
        java.util.Date date = DeviceTimeFormat.parse(iso);
        if (date == null) return "";
        java.util.Calendar msg = java.util.Calendar.getInstance();
        msg.setTime(date);
        java.util.Calendar now = java.util.Calendar.getInstance();
        String time = DeviceTimeFormat.messageTime(context, iso);
        boolean sameDay = msg.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && msg.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return context.getString(R.string.chat_day_today, time);
        }
        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        boolean yesterday = msg.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && msg.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        if (yesterday) {
            return context.getString(R.string.chat_day_yesterday, time);
        }
        java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(context);
        return df.format(date) + " · " + time;
    }

    private void bindImageTimeOverlay(
            @NonNull VH holder, @NonNull ChatDtos.MessageDto msg, @NonNull String time) {
        boolean bareImage = isBareImageMessage(msg);
        if (holder.b.tvImageTime != null) {
            if (bareImage) {
                holder.b.tvImageTime.setVisibility(View.VISIBLE);
                holder.b.tvImageTime.setText(time);
            } else {
                holder.b.tvImageTime.setVisibility(View.GONE);
            }
        }
        if (holder.b.rowMetaFooter != null) {
            holder.b.rowMetaFooter.setVisibility(bareImage ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * WhatsApp-like spacing: roomy by default, still slightly tighter for a consecutive cluster.
     * Cards and side switches always keep air so VIP skins / invites never overlap.
     */
    private void bindClusterSpacing(
            @NonNull VH holder, int position, boolean mine, @NonNull ChatDtos.MessageDto msg) {
        boolean sameAsPrev = position > 0 && isMine(items.get(position - 1)) == mine
                && sameSender(items.get(position - 1), items.get(position));
        boolean sameAsNext = position + 1 < items.size()
                && isMine(items.get(position + 1)) == mine
                && sameSender(items.get(position), items.get(position + 1));
        boolean card = isCardMessage(msg);
        boolean prevCard = position > 0 && isCardMessage(items.get(position - 1));
        int topDp;
        int bottomDp;
        if (card || prevCard || !sameAsPrev) {
            topDp = 10;
        } else {
            topDp = 4;
        }
        bottomDp = (card || !sameAsNext) ? 8 : 4;
        View root = holder.b.rootRow;
        root.setPadding(
                root.getPaddingLeft(),
                dp(holder, topDp),
                root.getPaddingRight(),
                dp(holder, bottomDp));
    }

    private static boolean isCardMessage(@Nullable ChatDtos.MessageDto msg) {
        if (msg == null) return false;
        if (isAgencyInviteMessage(msg)) return true;
        return RoomShareCodec.isRoomShare(msg.content);
    }

    private static boolean isBareImageMessage(@Nullable ChatDtos.MessageDto msg) {
        if (msg == null || msg.type == null) return false;
        String type = msg.type.toLowerCase(Locale.US);
        if (!"image".equals(type) && !"media".equals(type)) return false;
        return msg.content == null || msg.content.trim().isEmpty();
    }

    private static boolean isGiftType(@Nullable ChatDtos.MessageDto msg) {
        return msg != null && msg.type != null && "gift".equalsIgnoreCase(msg.type);
    }

    /** Public for conversation swipe guard (cards must stay tappable). */
    public boolean isSwipeLocked(int position) {
        if (position < 0 || position >= items.size()) return true;
        return isCardMessage(items.get(position));
    }

    private void bindSideChrome(
            @NonNull VH holder, ChatDtos.MessageDto msg, boolean mine, int position) {
        boolean sameAsNext = position + 1 < items.size()
                && isMine(items.get(position + 1)) == mine
                && sameSender(msg, items.get(position + 1));
        String type = msg.type != null ? msg.type.toLowerCase(Locale.US) : "text";
        // Mock: peer avatar on every cluster end (and cards); mine avatar mainly on voice notes.
        boolean showAvatar;
        if (mine) {
            showAvatar = "audio".equals(type) && !sameAsNext;
        } else {
            showAvatar = !sameAsNext;
        }
        // Room / invite / image still show peer avatar for mock rhythm.
        if (!mine && (isCardMessage(msg) || isBareImageMessage(msg) || isGiftType(msg))) {
            showAvatar = true;
        }
        // Layout: peer keeps slot (INVISIBLE) for alignment; mine audio shows, else GONE so bubble hugs edge.
        int avatarState = showAvatar
                ? View.VISIBLE
                : (mine ? View.GONE : View.INVISIBLE);
        holder.b.avatarWrap.setVisibility(avatarState);
        if (holder.b.imgAvatar != null) {
            holder.b.imgAvatar.setVisibility(showAvatar ? View.VISIBLE : View.INVISIBLE);
        }
        if (holder.b.imgFrame != null && !showAvatar) {
            holder.b.imgFrame.setVisibility(View.GONE);
        }
        if (holder.b.imgHostBadge != null && !showAvatar) {
            holder.b.imgHostBadge.setVisibility(View.GONE);
        }

        String avatarUrl;
        String frameUrl;
        String hostBadgeUrl;
        String name;
        java.util.Map<String, Object> frameMeta = null;
        java.util.Map<String, Object> hostBadgeMeta = null;

        if (mine) {
            AuthDtos.UserDto liveMe = me;
            try {
                AuthDtos.UserDto sessionMe = com.Dramizo.Series.presentation.common.ContainerProvider
                        .from(holder.itemView.getContext())
                        .getSessionManager()
                        .getUser();
                if (sessionMe != null) liveMe = sessionMe;
            } catch (Exception ignored) {
            }
            avatarUrl = liveMe != null ? liveMe.avatarUrl : null;
            frameUrl = liveMe != null ? liveMe.vipBadgeUrl : null;
            hostBadgeUrl = null;
            name = firstNonEmpty(
                    liveMe != null ? liveMe.displayName : null,
                    liveMe != null ? liveMe.username : null,
                    "أنت");
        } else {
            AuthDtos.UserDto sender = msg.sender;
            avatarUrl = sender != null && sender.avatarUrl != null
                    ? sender.avatarUrl : peerAvatarUrl;
            frameUrl = firstNonEmpty(
                    sender != null ? sender.vipBadgeUrl : null,
                    peerHostBadgeUrl);
            hostBadgeUrl = null;
            name = firstNonEmpty(
                    sender != null ? sender.displayName : null,
                    peerDisplayName,
                    sender != null ? sender.username : null,
                    "صديق");
        }

        if (showAvatar) {
            AvatarCosmetics.bindAvatar(holder.b.imgAvatar, avatarUrl);
            AvatarCosmetics.applyHostWear(
                    holder.b.imgFrame,
                    holder.b.imgHostBadge,
                    holder.b.imgAvatar,
                    frameUrl,
                    hostBadgeUrl,
                    frameMeta,
                    hostBadgeMeta);
            if (holder.b.imgFrame != null && frameUrl != null && !frameUrl.isEmpty()) {
                holder.b.imgFrame.bringToFront();
            }
        }

        holder.b.tvSender.setVisibility(View.GONE);
        holder.b.tvSender.setText(name);

        if (showAvatar && listener != null) {
            String clickUserId;
            if (mine) {
                clickUserId = firstNonEmpty(
                        myUserId,
                        me != null ? me.id : null);
            } else {
                clickUserId = firstNonEmpty(
                        msg.sender != null ? msg.sender.id : null,
                        msg.senderId);
            }
            final String avatarUserId = clickUserId;
            holder.b.avatarWrap.setOnClickListener(v -> {
                if (avatarUserId != null && !avatarUserId.isEmpty()) {
                    listener.onAvatarClick(avatarUserId);
                } else {
                    listener.onAvatarClick(null);
                }
            });
        } else {
            holder.b.avatarWrap.setOnClickListener(null);
            holder.b.avatarWrap.setClickable(false);
        }
    }

    /** Text / meta ink for light peer, rose me, and VIP skins. */
    private void applyBubbleInk(@NonNull VH holder, boolean mine, int vipLevel) {
        int bodyColor;
        int metaColor;
        int secondary;
        if (vipLevel > 0) {
            bodyColor = VipStyle.messageColor(vipLevel);
            metaColor = 0xCCFFFFFF;
            secondary = 0xE6FFFFFF;
        } else if (mine) {
            bodyColor = 0xFFFFFFFF;
            metaColor = 0xCCFFFFFF;
            secondary = 0xE6FFFFFF;
        } else {
            bodyColor = 0xFF1A1A1A;
            metaColor = 0xFF9F9F9F;
            secondary = 0xFF595959;
        }
        if (holder.b.tvContent != null) holder.b.tvContent.setTextColor(bodyColor);
        if (holder.b.tvTime != null) holder.b.tvTime.setTextColor(metaColor);
        if (holder.b.tvTranslated != null) holder.b.tvTranslated.setTextColor(secondary);
        if (holder.b.tvTranslateAction != null) holder.b.tvTranslateAction.setTextColor(secondary);
        if (holder.b.tvAudioDur != null) holder.b.tvAudioDur.setTextColor(bodyColor);
        if (holder.b.tvReplyAuthor != null) holder.b.tvReplyAuthor.setTextColor(bodyColor);
        if (holder.b.tvReplyBody != null) holder.b.tvReplyBody.setTextColor(secondary);
    }

    private boolean sameSender(@Nullable ChatDtos.MessageDto a, @Nullable ChatDtos.MessageDto b) {
        if (a == null || b == null) return false;
        String idA = firstNonEmpty(a.senderId, a.sender != null ? a.sender.id : null);
        String idB = firstNonEmpty(b.senderId, b.sender != null ? b.sender.id : null);
        if (!idA.isEmpty() && !idB.isEmpty() && idsEqual(idA, idB)) return true;
        return isMine(a) == isMine(b);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private int resolveVipLevel(@NonNull VH holder, @NonNull ChatDtos.MessageDto msg, boolean mine) {
        try {
            if (mine) {
                AuthDtos.UserDto liveMe = me;
                try {
                    AuthDtos.UserDto sessionMe = com.Dramizo.Series.presentation.common.ContainerProvider
                            .from(holder.itemView.getContext())
                            .getSessionManager()
                            .getUser();
                    if (sessionMe != null) liveMe = sessionMe;
                } catch (Exception ignored) {
                }
                return liveMe != null ? Math.max(0, liveMe.vipLevel) : 0;
            }
            if (msg.sender != null) {
                return Math.max(0, msg.sender.vipLevel);
            }
            return Math.max(0, peerVipLevel);
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void bindStatusTick(@NonNull VH holder, ChatDtos.MessageDto msg, boolean mine) {
        if (holder.b.imgMsgStatus == null) return;
        if (!mine) {
            holder.b.imgMsgStatus.setVisibility(View.GONE);
            return;
        }
        holder.b.imgMsgStatus.setVisibility(View.VISIBLE);
        if (msg.localPending || msg.id == null || msg.id.startsWith("local:")) {
            holder.b.imgMsgStatus.setImageResource(R.drawable.ic_msg_pending);
            return;
        }
        long created = parseIsoMs(msg.createdAt);
        if (peerLastReadAtMs > 0 && created > 0 && peerLastReadAtMs >= created) {
            holder.b.imgMsgStatus.setImageResource(R.drawable.ic_msg_read);
        } else {
            holder.b.imgMsgStatus.setImageResource(R.drawable.ic_msg_sent);
        }
    }

    private void bindReplyQuote(@NonNull VH holder, ChatDtos.MessageDto msg) {
        if (holder.b.replyQuote == null) return;
        ChatDtos.MessageDto reply = msg.replyTo;
        if (reply == null && (msg.replyToId == null || msg.replyToId.isEmpty())) {
            holder.b.replyQuote.setVisibility(View.GONE);
            return;
        }
        holder.b.replyQuote.setVisibility(View.VISIBLE);
        String author = "رسالة";
        if (reply != null) {
            if (isMine(reply)) author = "أنت";
            else if (peerDisplayName != null && !peerDisplayName.isEmpty()) author = peerDisplayName;
        }
        holder.b.tvReplyAuthor.setText(author);
        holder.b.tvReplyBody.setText(previewOf(reply != null ? reply : msg));
    }

    private void bindBody(@NonNull VH holder, ChatDtos.MessageDto msg) {
        String type = msg.type != null ? msg.type.toLowerCase(Locale.US) : "text";
        String mediaUrl = msg.media != null ? msg.media.url : null;

        if (holder.b.imageWrap != null) holder.b.imageWrap.setVisibility(View.GONE);
        holder.b.imgMedia.setVisibility(View.GONE);
        holder.b.imgMedia.setOnClickListener(null);
        if (holder.b.rowImageReaction != null) holder.b.rowImageReaction.setVisibility(View.GONE);
        if (holder.b.audioRow != null) holder.b.audioRow.setVisibility(View.GONE);
        if (holder.b.agencyInviteRow != null) holder.b.agencyInviteRow.setVisibility(View.GONE);
        if (holder.b.giftRow != null) holder.b.giftRow.setVisibility(View.GONE);
        if (holder.b.roomShareRow != null) {
            holder.b.roomShareRow.setVisibility(View.GONE);
            holder.b.roomShareRow.setOnClickListener(null);
        }
        holder.b.tvContent.setVisibility(View.GONE);
        hideTranslateViews(holder);

        RoomShareCodec.Parsed share = RoomShareCodec.parse(msg.content);
        if (share != null && holder.b.roomShareRow != null) {
            bindLiveRoomShare(holder, msg, share);
            return;
        }

        if ("image".equals(type) || "media".equals(type)) {
            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                String fullUrl = AssetCatalog.absoluteUrl(mediaUrl);
                if (holder.b.imageWrap != null) holder.b.imageWrap.setVisibility(View.VISIBLE);
                holder.b.imgMedia.setVisibility(View.VISIBLE);
                Glide.with(holder.b.imgMedia)
                        .load(fullUrl)
                        .placeholder(ImagePlaceholder.cover())
                        .centerCrop()
                        .into(holder.b.imgMedia);
                holder.b.imgMedia.setOnClickListener(v -> showImage(holder.b.imgMedia, fullUrl));
                // Reaction chip (mock-style presence; real reaction API not required for layout parity).
                if (holder.b.rowImageReaction != null && holder.b.tvImageReaction != null) {
                    holder.b.rowImageReaction.setVisibility(View.VISIBLE);
                    holder.b.tvImageReaction.setText("❤️ 3");
                }
            }
            if (msg.content != null && !msg.content.trim().isEmpty()) {
                holder.b.tvContent.setVisibility(View.VISIBLE);
                holder.b.tvContent.setText(msg.content);
                bindTranslate(holder, msg);
            }
            return;
        }

        if ("audio".equals(type) && holder.b.audioRow != null) {
            holder.b.audioRow.setVisibility(View.VISIBLE);
            int dur = msg.media != null ? Math.max(0, msg.media.duration) : 0;
            holder.b.tvAudioDur.setText(formatDuration(dur));
            String fullUrl = AssetCatalog.absoluteUrl(mediaUrl);
            boolean mine = isMine(msg);
            String msgId = msg.id != null ? msg.id : fullUrl;
            boolean played = msgId != null && playedAudioIds.contains(msgId);
            View playWrap = holder.b.audioPlayWrap != null
                    ? holder.b.audioPlayWrap
                    : (View) holder.b.imgAudioPlay.getParent();
            applyAudioPlayStyle(playWrap, played);
            if (holder.b.waveAudio != null) {
                holder.b.waveAudio.setProgress(0f);
                holder.b.waveAudio.setAnimating(false);
                if (mine) {
                    holder.b.waveAudio.setWaveColors(0xFFFFFFFF, 0x66FFFFFF);
                } else {
                    holder.b.waveAudio.setWaveColors(0xFFFE2C55, 0x44FE2C55);
                }
            }
            // Play glyph: brand rose on white circle.
            if (holder.b.imgAudioPlay != null) {
                holder.b.imgAudioPlay.setColorFilter(0xFFFE2C55);
            }
            holder.b.imgAudioPlay.setImageResource(R.drawable.ic_audio_play);
            View.OnClickListener play = v -> playAudio(
                    holder.itemView.getContext(),
                    fullUrl,
                    msgId,
                    holder.b.imgAudioPlay,
                    playWrap,
                    holder.b.waveAudio,
                    holder.b.tvAudioDur,
                    dur);
            holder.b.audioRow.setOnClickListener(play);
            holder.b.imgAudioPlay.setOnClickListener(play);
            return;
        }

        if (isAgencyInviteMessage(msg) && holder.b.agencyInviteRow != null) {
            bindAgencyInviteCard(holder, msg, mediaUrl);
            return;
        }

        if ("gift".equals(type) && holder.b.giftRow != null) {
            bindGiftCard(holder, msg, mediaUrl);
            return;
        }

        holder.b.tvContent.setVisibility(View.VISIBLE);
        holder.b.tvContent.setText(msg.content != null ? msg.content : "");
        // Color already set in bindSideChrome (VIP-aware).
        bindTranslate(holder, msg);
    }

    private void bindGiftCard(
            @NonNull VH holder, @NonNull ChatDtos.MessageDto msg, @Nullable String mediaUrl) {
        holder.b.giftRow.setVisibility(View.VISIBLE);
        Context ctx = holder.itemView.getContext();
        boolean mine = isMine(msg);
        String raw = msg.content != null ? msg.content.trim() : "";
        int qty = parseGiftQuantity(raw);
        String name = stripGiftQuantity(raw);
        if (name.isEmpty()) name = ctx.getString(R.string.gift_message);

        TextView caption = holder.b.tvGiftCaption;
        View leading = holder.b.imgGiftLeading;
        View trail = holder.b.giftIconTrail;
        TextView qtyView = holder.b.tvGiftQty;

        if (mine) {
            if (caption != null) {
                caption.setVisibility(View.VISIBLE);
                caption.setText(R.string.gift_message_sent);
                caption.setTextColor(0xFFFE2C55);
            }
            holder.b.tvGiftLabel.setText(name);
            holder.b.tvGiftLabel.setTextColor(0xFF1A1A1A);
            holder.b.tvGiftLabel.setTextSize(15f);
            holder.b.tvGiftLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            if (caption != null) {
                caption.setTextSize(11f);
                caption.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
            if (leading != null) leading.setVisibility(View.GONE);
            if (trail != null) trail.setVisibility(View.VISIBLE);
            loadGiftIcon(holder.b.imgGift, mediaUrl);
            if (qtyView != null) {
                if (qty > 1) {
                    qtyView.setVisibility(View.VISIBLE);
                    qtyView.setText(ctx.getString(R.string.gift_qty_x, qty));
                    qtyView.setTextColor(0xFF1A1A1A);
                } else {
                    qtyView.setVisibility(View.GONE);
                }
            }
        } else {
            // Received: mystery-box style (icon left, title + “tap to open”).
            if (caption != null) {
                caption.setVisibility(View.VISIBLE);
                caption.setText(R.string.gift_mystery_title);
                caption.setTextColor(0xFF1A1A1A);
                caption.setTextSize(15f);
                caption.setTypeface(caption.getTypeface(), android.graphics.Typeface.BOLD);
            }
            holder.b.tvGiftLabel.setText(R.string.gift_mystery_hint);
            holder.b.tvGiftLabel.setTextColor(0xFF595959);
            holder.b.tvGiftLabel.setTextSize(12f);
            holder.b.tvGiftLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            if (leading != null) {
                leading.setVisibility(View.VISIBLE);
                if (leading instanceof ImageView) {
                    loadGiftIcon((ImageView) leading, mediaUrl);
                }
            }
            if (trail != null) trail.setVisibility(View.GONE);
            if (qtyView != null) qtyView.setVisibility(View.GONE);
        }
    }

    private static int parseGiftQuantity(@Nullable String content) {
        if (content == null || content.isEmpty()) return 1;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:x|×|\\*)\\s*(\\d{1,4})\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content.trim());
        if (m.find()) {
            try {
                return Math.max(1, Integer.parseInt(m.group(1)));
            } catch (Exception ignored) {
            }
        }
        return 1;
    }

    @NonNull
    private static String stripGiftQuantity(@Nullable String content) {
        if (content == null) return "";
        return content.trim()
                .replaceAll("(?i)\\s*(?:x|×|\\*)\\s*\\d{1,4}\\s*$", "")
                .trim();
    }

    private void loadGiftIcon(@Nullable ImageView target, @Nullable String mediaUrl) {
        if (target == null) return;
        String giftIcon = mediaUrl;
        if (giftIcon != null) {
            CosmeticMedia.Kind k = CosmeticMedia.kind(giftIcon);
            if (k == CosmeticMedia.Kind.VIDEO || k == CosmeticMedia.Kind.SVGA) {
                giftIcon = null;
            }
        }
        target.setVisibility(View.VISIBLE);
        if (giftIcon != null && !giftIcon.isEmpty()) {
            try {
                Glide.with(target.getContext().getApplicationContext())
                        .load(AssetCatalog.absoluteUrl(giftIcon))
                        .placeholder(ImagePlaceholder.gift())
                        .error(ImagePlaceholder.gift())
                        .override(160, 160)
                        .centerInside()
                        .dontAnimate()
                        .into(target);
            } catch (Exception e) {
                target.setImageResource(ImagePlaceholder.gift());
            }
        } else {
            target.setImageResource(ImagePlaceholder.gift());
        }
    }

    private void bindLiveRoomShare(
            @NonNull VH holder,
            @NonNull ChatDtos.MessageDto msg,
            @NonNull RoomShareCodec.Parsed share) {
        Context ctx = holder.itemView.getContext();
        final String roomId = share.roomId != null ? share.roomId.trim() : "";
        if (roomId.isEmpty()) {
            showRoomShareEnded(holder);
            return;
        }

        // Tag binding so async result only applies if this row still shows the same room.
        holder.b.roomShareRow.setTag(R.id.roomShareRow, roomId);
        holder.b.roomShareRow.setVisibility(View.GONE);
        holder.b.tvContent.setVisibility(View.GONE);
        holder.b.roomShareRow.setOnClickListener(null);

        RoomLiveSnap cached = ROOM_LIVE_CACHE.get(roomId);
        if (cached != null && !cached.expired()) {
            applyRoomLiveSnap(holder, share, cached);
            return;
        }

        // Soft loading line while we resolve live status (no fake listener counts).
        holder.b.tvContent.setVisibility(View.VISIBLE);
        holder.b.tvContent.setText(R.string.chat_room_share_loading);
        holder.b.tvContent.setTextColor(0xFF9F9F9F);

        AppContainer c;
        try {
            c = ContainerProvider.from(ctx);
        } catch (Exception e) {
            showRoomShareEnded(holder);
            return;
        }
        final int bindGen = holder.getBindingAdapterPosition();
        c.getIoExecutor().execute(() -> {
            Result<RoomDtos.RoomDto> r = c.getRoomUseCase.execute(roomId);
            RoomLiveSnap snap = RoomLiveSnap.fromResult(r);
            ROOM_LIVE_CACHE.put(roomId, snap);
            mainHandler.post(() -> {
                if (holder.getBindingAdapterPosition() != bindGen) return;
                Object tag = holder.b.roomShareRow.getTag(R.id.roomShareRow);
                if (!(tag instanceof String) || !roomId.equals(tag)) return;
                applyRoomLiveSnap(holder, share, snap);
            });
        });
    }

    private void applyRoomLiveSnap(
            @NonNull VH holder,
            @NonNull RoomShareCodec.Parsed share,
            @NonNull RoomLiveSnap snap) {
        if (!snap.live) {
            showRoomShareEnded(holder);
            return;
        }
        Context ctx = holder.itemView.getContext();
        holder.b.tvContent.setVisibility(View.GONE);
        holder.b.roomShareRow.setVisibility(View.VISIBLE);

        String title = firstNonEmpty(snap.title, share.title, ctx.getString(R.string.chat_room_listening));
        holder.b.tvRoomShareTitle.setText(title);

        TextView listeners = holder.b.roomShareRow.findViewById(R.id.tvRoomShareListeners);
        if (listeners != null) {
            listeners.setText(ctx.getString(R.string.chat_room_listening_count, Math.max(0, snap.viewerCount)));
        }
        TextView people = holder.b.roomShareRow.findViewById(R.id.tvRoomSharePeople);
        if (people != null) {
            int extra = Math.max(0, snap.viewerCount - Math.min(3, snap.avatarUrls.size()));
            if (extra > 0) {
                people.setVisibility(View.VISIBLE);
                people.setText("+" + extra);
            } else if (snap.viewerCount > 0) {
                people.setVisibility(View.VISIBLE);
                people.setText(String.valueOf(snap.viewerCount));
            } else {
                people.setVisibility(View.GONE);
            }
        }
        TextView live = holder.b.roomShareRow.findViewById(R.id.tvRoomShareLive);
        if (live != null) live.setVisibility(View.VISIBLE);
        TextView sub = holder.b.roomShareRow.findViewById(R.id.tvRoomShareSubtitle);
        if (sub != null) sub.setText(R.string.chat_room_share_subtitle);

        TextView cta = holder.b.roomShareRow.findViewById(R.id.tvRoomShareCta);
        if (cta != null) {
            tintSolidRoundRect(cta, RemoteTheme.primaryColor(ctx), dp(holder, 14));
        }

        String cover = firstNonEmpty(snap.coverUrl, share.coverUrl);
        if (cover != null && !cover.isEmpty()) {
            Glide.with(holder.b.imgRoomShareCover)
                    .load(AssetCatalog.absoluteUrl(cover))
                    .placeholder(ImagePlaceholder.cover())
                    .centerCrop()
                    .into(holder.b.imgRoomShareCover);
        } else {
            holder.b.imgRoomShareCover.setImageResource(ImagePlaceholder.cover());
        }
        holder.b.imgRoomShareCover.setClipToOutline(true);

        bindRoomShareAvatars(holder, snap.avatarUrls);

        final String roomId = share.roomId;
        holder.b.roomShareRow.setOnClickListener(v ->
                com.Dramizo.Series.presentation.voiceroom.RoomJoinGateActivity.open(
                        holder.itemView.getContext(), roomId));
    }

    private void showRoomShareEnded(@NonNull VH holder) {
        if (holder.b.roomShareRow != null) {
            holder.b.roomShareRow.setVisibility(View.GONE);
            holder.b.roomShareRow.setOnClickListener(null);
        }
        holder.b.tvContent.setVisibility(View.VISIBLE);
        holder.b.tvContent.setText(R.string.chat_room_share_ended);
        holder.b.tvContent.setTextColor(0xFF9F9F9F);
    }

    private void bindRoomShareAvatars(@NonNull VH holder, @Nullable List<String> urls) {
        ImageView[] avatars = {
                holder.b.roomShareRow.findViewById(R.id.imgRoomShareAvatar1),
                holder.b.roomShareRow.findViewById(R.id.imgRoomShareAvatar2),
                holder.b.roomShareRow.findViewById(R.id.imgRoomShareAvatar3),
        };
        List<String> list = urls != null ? urls : Collections.emptyList();
        for (int i = 0; i < avatars.length; i++) {
            ImageView iv = avatars[i];
            if (iv == null) continue;
            if (i < list.size() && list.get(i) != null && !list.get(i).isEmpty()) {
                iv.setVisibility(View.VISIBLE);
                try {
                    Glide.with(iv)
                            .load(AssetCatalog.absoluteUrl(list.get(i)))
                            .placeholder(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .into(iv);
                } catch (Exception e) {
                    iv.setImageResource(R.drawable.ic_default_avatar);
                }
            } else {
                iv.setVisibility(View.GONE);
                iv.setImageDrawable(null);
            }
        }
    }

    private void applyBrandMineBubble(@NonNull View bubble) {
        int primary = RemoteTheme.primaryColor(bubble.getContext());
        int light = lightenColor(primary, 0.22f);
        float d = bubble.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{light, primary});
        g.setCornerRadii(new float[]{
                18 * d, 18 * d,
                4 * d, 4 * d,
                18 * d, 18 * d,
                18 * d, 18 * d
        });
        bubble.setBackground(g);
    }

    private static int lightenColor(int color, float amount) {
        int a = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, (int) (r + (255 - r) * amount));
        g = Math.min(255, (int) (g + (255 - g) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));
        return Color.argb(a, r, g, b);
    }

    private static void tintSolidRoundRect(@NonNull View view, int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        view.setBackground(g);
    }

    private static final class RoomLiveSnap {
        final boolean live;
        final int viewerCount;
        @Nullable final String title;
        @Nullable final String coverUrl;
        @NonNull final List<String> avatarUrls;
        final long fetchedAt;

        RoomLiveSnap(
                boolean live,
                int viewerCount,
                @Nullable String title,
                @Nullable String coverUrl,
                @Nullable List<String> avatarUrls) {
            this.live = live;
            this.viewerCount = viewerCount;
            this.title = title;
            this.coverUrl = coverUrl;
            this.avatarUrls = avatarUrls != null ? avatarUrls : Collections.emptyList();
            this.fetchedAt = System.currentTimeMillis();
        }

        boolean expired() {
            return System.currentTimeMillis() - fetchedAt > ROOM_LIVE_CACHE_MS;
        }

        @NonNull
        static RoomLiveSnap fromResult(@Nullable Result<RoomDtos.RoomDto> r) {
            if (r == null || !r.success || r.data == null) {
                String err = r != null && r.error != null ? r.error.toLowerCase(Locale.US) : "";
                // Forbidden private rooms may still be live — don't hard-end the card.
                if (err.contains("join this room") || err.contains("forbidden")
                        || err.contains("password") || err.contains("كلمة")) {
                    return new RoomLiveSnap(true, 0, null, null, null);
                }
                return new RoomLiveSnap(false, 0, null, null, null);
            }
            RoomDtos.RoomDto room = r.data;
            boolean closed = room.status != null && "closed".equalsIgnoreCase(room.status.trim());
            if (closed) {
                return new RoomLiveSnap(false, 0, room.title, room.coverUrl, room.viewerAvatars);
            }
            List<String> avatars = new ArrayList<>();
            if (room.viewerAvatars != null) {
                for (String u : room.viewerAvatars) {
                    if (u != null && !u.trim().isEmpty()) avatars.add(u.trim());
                    if (avatars.size() >= 3) break;
                }
            }
            if (avatars.isEmpty() && room.host != null && room.host.avatarUrl != null) {
                avatars.add(room.host.avatarUrl);
            }
            return new RoomLiveSnap(
                    true,
                    Math.max(0, room.viewerCount),
                    room.title,
                    firstNonEmpty(room.coverUrl, room.roomCardUrl, room.backgroundUrl),
                    avatars);
        }
    }

    private void bindAgencyInviteCard(
            @NonNull VH holder, @NonNull ChatDtos.MessageDto msg, @Nullable String mediaUrl) {
        holder.b.agencyInviteRow.setVisibility(View.VISIBLE);
        AgencyInviteCodec.Parsed invite = AgencyInviteCodec.parse(msg.content);
        Context ctx = holder.itemView.getContext();

        String agencyName;
        String logo = mediaUrl;
        if (invite != null) {
            agencyName = firstNonEmpty(invite.name, ctx.getString(R.string.agency_invite_title));
            if (invite.logoUrl != null && !invite.logoUrl.isEmpty()) {
                logo = invite.logoUrl;
            }
        } else {
            // Legacy free-text fallback (not room join).
            agencyName = firstNonEmpty(msg.content, ctx.getString(R.string.agency_invite_title));
            if (agencyName.contains("\n")) {
                agencyName = agencyName.split("\n", 2)[0].trim();
            }
            if (agencyName.length() > 40) agencyName = agencyName.substring(0, 40) + "…";
        }

        if (holder.b.tvAgencyInviteName != null) {
            holder.b.tvAgencyInviteName.setText(agencyName);
        }
        if (holder.b.tvAgencyInviteBody != null) {
            holder.b.tvAgencyInviteBody.setText(R.string.agency_invite_body);
        }
        if (holder.b.imgAgencyInviteLogo != null) {
            if (logo != null && !logo.isEmpty()) {
                Glide.with(holder.b.imgAgencyInviteLogo)
                        .load(AssetCatalog.absoluteUrl(logo))
                        .placeholder(R.drawable.icon_agency)
                        .error(R.drawable.icon_agency)
                        .centerCrop()
                        .into(holder.b.imgAgencyInviteLogo);
            } else {
                holder.b.imgAgencyInviteLogo.setImageResource(R.drawable.icon_agency);
            }
            holder.b.imgAgencyInviteLogo.setClipToOutline(true);
        }

        boolean mine = isMine(msg);
        final AgencyInviteCodec.Parsed inviteFinal = invite;
        // Sender sees their own invite card but cannot accept/decline their outgoing invite.
        if (holder.b.btnAgencyInviteYes != null) {
            holder.b.btnAgencyInviteYes.setClickable(true);
            holder.b.btnAgencyInviteYes.setFocusable(true);
            holder.b.btnAgencyInviteYes.setEnabled(!mine);
            holder.b.btnAgencyInviteYes.setAlpha(mine ? 0.5f : 1f);
            holder.b.btnAgencyInviteYes.setOnClickListener(v -> {
                if (mine) {
                    Toast.makeText(ctx, R.string.agency_invite_only_recipient, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (listener != null) {
                    listener.onAgencyInviteAgree(msg, inviteFinal);
                }
            });
        }
        if (holder.b.btnAgencyInviteNo != null) {
            holder.b.btnAgencyInviteNo.setClickable(true);
            holder.b.btnAgencyInviteNo.setFocusable(true);
            holder.b.btnAgencyInviteNo.setEnabled(!mine);
            holder.b.btnAgencyInviteNo.setAlpha(mine ? 0.5f : 1f);
            holder.b.btnAgencyInviteNo.setOnClickListener(v -> {
                if (mine) {
                    Toast.makeText(ctx, R.string.agency_invite_only_recipient, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (listener != null) {
                    listener.onAgencyInviteDisagree(msg, inviteFinal);
                } else {
                    Toast.makeText(ctx, R.string.agency_invite_declined, Toast.LENGTH_SHORT).show();
                }
            });
        }
        // Keep card taps from bubbling into swipe-reply drag start on sparse moves.
        if (holder.b.agencyInviteRow != null) {
            holder.b.agencyInviteRow.setClickable(true);
            holder.b.agencyInviteRow.setOnClickListener(v -> { /* consume */ });
        }
    }

    private static boolean isAgencyInviteMessage(@Nullable ChatDtos.MessageDto msg) {
        if (msg == null) return false;
        if (AgencyInviteCodec.isAgencyInvite(msg.content)) return true;
        return isAgencyInviteType(msg.type, msg.content);
    }

    private void hideTranslateViews(@NonNull VH holder) {
        if (holder.b.tvTranslateAction != null) {
            holder.b.tvTranslateAction.setVisibility(View.GONE);
            holder.b.tvTranslateAction.setOnClickListener(null);
        }
        if (holder.b.tvTranslated != null) {
            holder.b.tvTranslated.setVisibility(View.GONE);
            holder.b.tvTranslated.setText("");
        }
    }

    private void bindTranslate(@NonNull VH holder, ChatDtos.MessageDto msg) {
        if (holder.b.tvTranslateAction == null || holder.b.tvTranslated == null) return;
        boolean mine = isMine(msg);
        boolean hasText = msg.content != null && !msg.content.trim().isEmpty();
        if (mine || !hasText) {
            hideTranslateViews(holder);
            return;
        }
        String cached = msg.id != null ? translations.get(msg.id) : null;
        holder.b.tvTranslateAction.setVisibility(View.VISIBLE);
        if (cached != null && !cached.isEmpty()) {
            holder.b.tvTranslated.setVisibility(View.VISIBLE);
            holder.b.tvTranslated.setText(cached);
            holder.b.tvTranslateAction.setText(R.string.show_original);
            holder.b.tvTranslateAction.setOnClickListener(v -> {
                if (listener != null) listener.onTranslate(msg, holder.getBindingAdapterPosition());
            });
        } else {
            holder.b.tvTranslated.setVisibility(View.GONE);
            holder.b.tvTranslated.setText("");
            holder.b.tvTranslateAction.setText(R.string.translate);
            holder.b.tvTranslateAction.setOnClickListener(v -> {
                if (listener != null) listener.onTranslate(msg, holder.getBindingAdapterPosition());
            });
        }
    }

    private static void applyAudioPlayStyle(@Nullable View playWrap, boolean played) {
        if (playWrap == null) return;
        // Mock: white circular play control on purple voice bubble.
        playWrap.setBackgroundResource(R.drawable.bg_chat_audio_play_dot);
        playWrap.setAlpha(played ? 0.85f : 1f);
    }

    private void markAudioPlayed(@Nullable String messageId, @Nullable View playWrap) {
        if (messageId == null || messageId.isEmpty()) return;
        playedAudioIds.add(messageId);
        applyAudioPlayStyle(playWrap, true);
    }

    private void playAudio(Context context, String url, String messageId, ImageView icon,
                           View playWrap, AudioWaveformView wave,
                           android.widget.TextView durView, int knownDurSec) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (url.equals(playingUrl) && playing != null) {
                if (playing.isPlaying()) {
                    playing.pause();
                    if (icon != null) icon.setImageResource(R.drawable.ic_audio_play);
                    if (wave != null) wave.setAnimating(false);
                    audioHandler.removeCallbacks(progressTick);
                } else {
                    playing.start();
                    markAudioPlayed(messageId, playWrap);
                    if (icon != null) icon.setImageResource(R.drawable.ic_audio_pause);
                    if (wave != null) wave.setAnimating(true);
                    playingIcon = icon;
                    playingPlayWrap = playWrap;
                    playingMessageId = messageId;
                    playingWave = wave;
                    audioHandler.post(progressTick);
                }
                return;
            }
            releasePlayer();
            playing = new MediaPlayer();
            playingUrl = url;
            playingIcon = icon;
            playingPlayWrap = playWrap;
            playingMessageId = messageId;
            playingWave = wave;
            if (wave != null) {
                wave.setProgress(0f);
                wave.setAnimating(false);
            }
            playing.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "HamsLive (Android; MediaPlayer)");
            playing.setDataSource(context, Uri.parse(url), headers);
            playing.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(context, "تعذر تشغيل الصوت", Toast.LENGTH_SHORT).show();
                releasePlayer();
                return true;
            });
            playing.setOnCompletionListener(mp -> {
                markAudioPlayed(messageId, playWrap);
                if (icon != null) icon.setImageResource(R.drawable.ic_audio_play);
                if (wave != null) {
                    wave.setAnimating(false);
                    wave.setProgress(0f);
                }
                if (durView != null && knownDurSec > 0) {
                    durView.setText(formatDuration(knownDurSec));
                }
                releasePlayer();
            });
            playing.setOnPreparedListener(mp -> {
                markAudioPlayed(messageId, playWrap);
                if (icon != null) icon.setImageResource(R.drawable.ic_audio_pause);
                if (wave != null) wave.setAnimating(true);
                mp.start();
                audioHandler.post(progressTick);
            });
            if (icon != null) icon.setImageResource(R.drawable.ic_audio_play);
            playing.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(context, "تعذر تشغيل الصوت", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private static String previewOf(ChatDtos.MessageDto msg) {
        if (msg == null) return "";
        String type = msg.type != null ? msg.type.toLowerCase(Locale.US) : "text";
        if ("image".equals(type)) return "📷 صورة";
        if ("audio".equals(type)) return "🎤 رسالة صوتية";
        if ("gift".equals(type)) return "🎁 " + (msg.content != null ? msg.content : "هدية");
        if (RoomShareCodec.isRoomShare(msg.content)) return RoomShareCodec.previewLabel(msg.content);
        if (AgencyInviteCodec.isAgencyInvite(msg.content)) {
            return AgencyInviteCodec.previewLabel(msg.content);
        }
        if (isAgencyInviteType(msg.type, msg.content)) {
            return "دعوة للانضمام إلى العائلة";
        }
        if (msg.content != null && !msg.content.isEmpty()) return msg.content;
        return "رسالة";
    }

    private static String formatDuration(int sec) {
        // Mikoo voice bubbles show 3" / 8" for short clips.
        if (sec < 60) {
            return sec + "\"";
        }
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private static boolean isAgencyInviteType(@Nullable String type, @Nullable String content) {
        if (AgencyInviteCodec.isAgencyInvite(content)) return true;
        if (type != null) {
            String t = type.toLowerCase(Locale.US);
            if (t.contains("agency") || t.contains("guild") || t.contains("family_invite")) {
                return true;
            }
        }
        if (content != null) {
            String c = content;
            // Avoid matching bare "وكالة" (too broad); require family-join phrasing.
            return c.contains("الانضمام إلى العائلة")
                    || c.contains("join the family")
                    || c.contains("join agency")
                    || c.contains("يدعوك للانضمام");
        }
        return false;
    }

    private boolean isMine(ChatDtos.MessageDto msg) {
        if (msg == null) return false;
        // Optimistic send must always stay on the outgoing side.
        if (msg.localPending) return true;
        if (msg.id != null && msg.id.startsWith("local:")) return true;

        String meId = myUserId;
        if ((meId == null || meId.isEmpty()) && this.me != null) meId = this.me.id;
        if (meId == null || meId.isEmpty()) return false;

        if (idsEqual(meId, msg.senderId)) return true;
        if (msg.sender != null && idsEqual(meId, msg.sender.id)) return true;
        if (this.me != null && this.me.publicId != null && !this.me.publicId.isEmpty()) {
            if (idsEqual(this.me.publicId, msg.senderId)) return true;
            if (msg.sender != null && idsEqual(this.me.publicId, msg.sender.publicId)) return true;
        }
        return false;
    }

    private static boolean idsEqual(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.trim();
        String y = b.trim();
        return !x.isEmpty() && x.equalsIgnoreCase(y);
    }

    private static int dp(@NonNull VH holder, int value) {
        return Math.round(value * holder.itemView.getResources().getDisplayMetrics().density);
    }

    private static void showImage(@NonNull ImageView source, @NonNull String imageUrl) {
        Dialog dialog = new Dialog(source.getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(source.getContext());
        root.setBackgroundColor(Color.BLACK);
        ImageView fullImage = new ImageView(source.getContext());
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(fullImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        Glide.with(fullImage).load(imageUrl)
                .placeholder(ImagePlaceholder.cover())
                .error(ImagePlaceholder.cover())
                .fitCenter()
                .into(fullImage);
        root.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final Rows b;

        VH(View root) {
            super(root);
            b = new Rows(root);
        }
    }

    /** Manual binding so mine/peer layouts can share the same field graph. */
    static final class Rows {
        final View rootRow;
        final View avatarWrap;
        final ImageView imgAvatar;
        final ImageView imgFrame;
        final ImageView imgHostBadge;
        final View bubbleColumn;
        final LinearLayout bubbleRoot;
        final TextView tvDaySeparator;
        final TextView tvVipChip;
        final View rowBadges;
        final TextView tvMeta;
        final TextView tvSender;
        final View replyQuote;
        final TextView tvReplyAuthor;
        final TextView tvReplyBody;
        final View imageWrap;
        final ImageView imgMedia;
        final View rowImageReaction;
        final TextView tvImageReaction;
        final TextView tvImageTime;
        final View audioRow;
        final TextView tvAudioDur;
        final AudioWaveformView waveAudio;
        final View audioPlayWrap;
        final ImageView imgAudioPlay;
        final View agencyInviteRow;
        final ImageView imgAgencyInviteLogo;
        final TextView tvAgencyInviteName;
        final TextView tvAgencyInviteBody;
        final TextView btnAgencyInviteNo;
        final TextView btnAgencyInviteYes;
        final View giftRow;
        final ImageView imgGift;
        final ImageView imgGiftLeading;
        final View giftIconTrail;
        final TextView tvGiftCaption;
        final TextView tvGiftLabel;
        final TextView tvGiftQty;
        final View roomShareRow;
        final ImageView imgRoomShareCover;
        final TextView tvRoomShareTitle;
        final TextView tvContent;
        final TextView tvTranslateAction;
        final TextView tvTranslated;
        final View rowMetaFooter;
        final TextView tvTime;
        final ImageView imgMsgStatus;

        Rows(View root) {
            rootRow = root.findViewById(R.id.rootRow);
            avatarWrap = root.findViewById(R.id.avatarWrap);
            imgAvatar = root.findViewById(R.id.imgAvatar);
            imgFrame = root.findViewById(R.id.imgFrame);
            imgHostBadge = root.findViewById(R.id.imgHostBadge);
            bubbleColumn = root.findViewById(R.id.bubbleColumn);
            bubbleRoot = root.findViewById(R.id.bubbleRoot);
            tvDaySeparator = root.findViewById(R.id.tvDaySeparator);
            tvVipChip = root.findViewById(R.id.tvVipChip);
            rowBadges = root.findViewById(R.id.rowBadges);
            tvMeta = root.findViewById(R.id.tvMeta);
            tvSender = root.findViewById(R.id.tvSender);
            replyQuote = root.findViewById(R.id.replyQuote);
            tvReplyAuthor = root.findViewById(R.id.tvReplyAuthor);
            tvReplyBody = root.findViewById(R.id.tvReplyBody);
            imageWrap = root.findViewById(R.id.imageWrap);
            imgMedia = root.findViewById(R.id.imgMedia);
            rowImageReaction = root.findViewById(R.id.rowImageReaction);
            tvImageReaction = root.findViewById(R.id.tvImageReaction);
            tvImageTime = root.findViewById(R.id.tvImageTime);
            audioRow = root.findViewById(R.id.audioRow);
            tvAudioDur = root.findViewById(R.id.tvAudioDur);
            waveAudio = root.findViewById(R.id.waveAudio);
            audioPlayWrap = root.findViewById(R.id.audioPlayWrap);
            imgAudioPlay = root.findViewById(R.id.imgAudioPlay);
            agencyInviteRow = root.findViewById(R.id.agencyInviteRow);
            imgAgencyInviteLogo = root.findViewById(R.id.imgAgencyInviteLogo);
            tvAgencyInviteName = root.findViewById(R.id.tvAgencyInviteName);
            tvAgencyInviteBody = root.findViewById(R.id.tvAgencyInviteBody);
            btnAgencyInviteNo = root.findViewById(R.id.btnAgencyInviteNo);
            btnAgencyInviteYes = root.findViewById(R.id.btnAgencyInviteYes);
            giftRow = root.findViewById(R.id.giftRow);
            imgGift = root.findViewById(R.id.imgGift);
            imgGiftLeading = root.findViewById(R.id.imgGiftLeading);
            giftIconTrail = root.findViewById(R.id.giftIconTrail);
            tvGiftCaption = root.findViewById(R.id.tvGiftCaption);
            tvGiftLabel = root.findViewById(R.id.tvGiftLabel);
            tvGiftQty = root.findViewById(R.id.tvGiftQty);
            roomShareRow = root.findViewById(R.id.roomShareRow);
            imgRoomShareCover = root.findViewById(R.id.imgRoomShareCover);
            tvRoomShareTitle = root.findViewById(R.id.tvRoomShareTitle);
            tvContent = root.findViewById(R.id.tvContent);
            tvTranslateAction = root.findViewById(R.id.tvTranslateAction);
            tvTranslated = root.findViewById(R.id.tvTranslated);
            rowMetaFooter = root.findViewById(R.id.rowMetaFooter);
            tvTime = root.findViewById(R.id.tvTime);
            imgMsgStatus = root.findViewById(R.id.imgMsgStatus);
        }
    }
}
