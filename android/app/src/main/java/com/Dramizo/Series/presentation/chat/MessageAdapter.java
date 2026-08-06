package com.Dramizo.Series.presentation.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.databinding.ItemMessageBinding;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.CosmeticMedia;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.RoomShareCodec;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
    public interface Listener {
        void onLongClick(ChatDtos.MessageDto msg);
        void onSwipeReply(ChatDtos.MessageDto msg);
        void onTranslate(ChatDtos.MessageDto msg, int adapterPosition);
        default void onAvatarClick(String userId) {}
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
        items.clear();
        if (data != null) items.addAll(data);
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatDtos.MessageDto msg = items.get(position);
        boolean mine = isMine(msg);

        bindRowOrder(holder, mine);
        bindSideChrome(holder, msg, mine);
        holder.b.tvVipChip.setVisibility(View.GONE);
        holder.b.rowBadges.setVisibility(View.GONE);
        holder.b.tvMeta.setVisibility(View.GONE);

        holder.b.bubbleRoot.setBackgroundResource(
                mine ? R.drawable.bg_chat_bubble_me : R.drawable.bg_chat_bubble_peer);

        bindReplyQuote(holder, msg);
        bindBody(holder, msg);

        String time = DeviceTimeFormat.messageTime(holder.itemView.getContext(), msg.createdAt);
        if (msg.isEdited) time = time + " · معدّلة";
        holder.b.tvTime.setText(time);
        bindStatusTick(holder, msg, mine);

        View.OnLongClickListener longClick = v -> {
            if (listener != null) listener.onLongClick(msg);
            return true;
        };
        holder.itemView.setOnLongClickListener(longClick);
        holder.b.bubbleRoot.setOnLongClickListener(longClick);
    }

    /** WhatsApp-style: peer = avatar|bubble , mine = bubble|avatar (always LTR). */
    private void bindRowOrder(@NonNull VH holder, boolean mine) {
        LinearLayout root = holder.b.rootRow;
        View avatar = holder.b.avatarWrap;
        View column = holder.b.bubbleColumn;
        root.setGravity(mine ? (Gravity.END | Gravity.BOTTOM) : (Gravity.START | Gravity.BOTTOM));
        int avatarIndex = root.indexOfChild(avatar);
        int columnIndex = root.indexOfChild(column);
        if (mine) {
            // bubble then avatar
            if (avatarIndex < columnIndex) {
                root.removeView(avatar);
                root.addView(avatar);
            }
        } else {
            // avatar then bubble
            if (columnIndex < avatarIndex) {
                root.removeView(avatar);
                root.addView(avatar, 0);
            }
        }
    }

    private void bindSideChrome(@NonNull VH holder, ChatDtos.MessageDto msg, boolean mine) {
        holder.b.avatarWrap.setVisibility(View.VISIBLE);

        String avatarUrl;
        String frameUrl;
        String hostBadgeUrl;
        String name;
        java.util.Map<String, Object> frameMeta = null;
        java.util.Map<String, Object> hostBadgeMeta = null;

        if (mine) {
            // Always re-read session so equipped VIP/head frames show for all roles (incl. staff).
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
            // Private chat: personal VIP frame only (host signal is agency-room wear).
            frameUrl = liveMe != null ? liveMe.vipBadgeUrl : null;
            hostBadgeUrl = null;
            // Same rule as chat header: displayName first, then username.
            name = firstNonEmpty(
                    liveMe != null ? liveMe.displayName : null,
                    liveMe != null ? liveMe.username : null,
                    "أنت");
        } else {
            AuthDtos.UserDto sender = msg.sender;
            avatarUrl = sender != null && sender.avatarUrl != null
                    ? sender.avatarUrl : peerAvatarUrl;
            // Prefer sender payload frame, then conversation peer VIP frame (not host-signal).
            frameUrl = firstNonEmpty(
                    sender != null ? sender.vipBadgeUrl : null,
                    peerHostBadgeUrl);
            hostBadgeUrl = null;
            // Same name shown above "متصل الآن" in the conversation header.
            name = firstNonEmpty(
                    sender != null ? sender.displayName : null,
                    peerDisplayName,
                    sender != null ? sender.username : null,
                    "صديق");
        }

        AvatarCosmetics.bindAvatar(holder.b.imgAvatar, avatarUrl);
        // Stack VIP frame on top of the face (staff roles included — no role-based strip).
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

        holder.b.tvSender.setVisibility(View.GONE);
        holder.b.tvSender.setText(name);

        // Better contrast on mine / peer bubbles
        int bodyColor = mine ? 0xFFFFFFFF : holder.itemView.getContext().getColor(R.color.text_primary);
        int metaColor = mine ? 0xCCFFFFFF : holder.itemView.getContext().getColor(R.color.text_hint);
        holder.b.tvContent.setTextColor(bodyColor);
        holder.b.tvTime.setTextColor(metaColor);
        if (holder.b.tvTranslated != null) {
            holder.b.tvTranslated.setTextColor(mine ? 0xE6FFFFFF : holder.itemView.getContext().getColor(R.color.text_secondary));
        }
        if (holder.b.tvTranslateAction != null) {
            holder.b.tvTranslateAction.setTextColor(mine ? 0xCCFFFFFF : holder.itemView.getContext().getColor(R.color.text_secondary));
        }
        if (holder.b.tvAudioDur != null) {
            holder.b.tvAudioDur.setTextColor(metaColor);
        }

        if (!mine && listener != null) {
            String clickUserId = firstNonEmpty(
                    msg.sender != null ? msg.sender.id : null,
                    msg.senderId);
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

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
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

        holder.b.imgMedia.setVisibility(View.GONE);
        holder.b.imgMedia.setOnClickListener(null);
        if (holder.b.audioRow != null) holder.b.audioRow.setVisibility(View.GONE);
        if (holder.b.giftRow != null) holder.b.giftRow.setVisibility(View.GONE);
        if (holder.b.roomShareRow != null) {
            holder.b.roomShareRow.setVisibility(View.GONE);
            holder.b.roomShareRow.setOnClickListener(null);
        }
        holder.b.tvContent.setVisibility(View.GONE);
        hideTranslateViews(holder);

        RoomShareCodec.Parsed share = RoomShareCodec.parse(msg.content);
        if (share != null && holder.b.roomShareRow != null) {
            holder.b.roomShareRow.setVisibility(View.VISIBLE);
            holder.b.tvRoomShareTitle.setText(share.title);
            if (share.coverUrl != null && !share.coverUrl.isEmpty()) {
                Glide.with(holder.b.imgRoomShareCover)
                        .load(AssetCatalog.absoluteUrl(share.coverUrl))
                        .placeholder(ImagePlaceholder.cover())
                        .centerCrop()
                        .into(holder.b.imgRoomShareCover);
            } else {
                holder.b.imgRoomShareCover.setImageResource(ImagePlaceholder.cover());
            }
            holder.b.imgRoomShareCover.setClipToOutline(true);
            holder.b.roomShareRow.setOnClickListener(v -> {
                // Validate live/exists first — never open an empty closed room from chat shares.
                com.Dramizo.Series.presentation.voiceroom.RoomJoinGateActivity.open(
                        holder.itemView.getContext(), share.roomId);
            });
            return;
        }

        if ("image".equals(type) || "media".equals(type)) {
            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                String fullUrl = AssetCatalog.absoluteUrl(mediaUrl);
                holder.b.imgMedia.setVisibility(View.VISIBLE);
                Glide.with(holder.b.imgMedia)
                        .load(fullUrl)
                        .placeholder(ImagePlaceholder.cover())
                        .centerCrop()
                        .into(holder.b.imgMedia);
                holder.b.imgMedia.setOnClickListener(v -> showImage(holder.b.imgMedia, fullUrl));
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
                    holder.b.waveAudio.setWaveColors(0xFFE8F4F8, 0x55A8C5D4);
                }
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

        if ("gift".equals(type) && holder.b.giftRow != null) {
            holder.b.giftRow.setVisibility(View.VISIBLE);
            String label = msg.content != null && !msg.content.isEmpty() ? msg.content : "هدية";
            holder.b.tvGiftLabel.setText(label);
            String giftIcon = mediaUrl;
            if (giftIcon != null) {
                CosmeticMedia.Kind k = CosmeticMedia.kind(giftIcon);
                if (k == CosmeticMedia.Kind.VIDEO || k == CosmeticMedia.Kind.SVGA) {
                    giftIcon = null;
                }
            }
            if (giftIcon != null && !giftIcon.isEmpty()) {
                holder.b.imgGift.setVisibility(View.VISIBLE);
                try {
                    Glide.with(holder.b.imgGift.getContext().getApplicationContext())
                            .load(AssetCatalog.absoluteUrl(giftIcon))
                            .placeholder(ImagePlaceholder.gift())
                            .error(ImagePlaceholder.gift())
                            .override(160, 160)
                            .centerInside()
                            .dontAnimate()
                            .into(holder.b.imgGift);
                } catch (Exception e) {
                    holder.b.imgGift.setImageResource(ImagePlaceholder.gift());
                }
            } else {
                holder.b.imgGift.setVisibility(View.VISIBLE);
                holder.b.imgGift.setImageResource(ImagePlaceholder.gift());
            }
            return;
        }

        holder.b.tvContent.setVisibility(View.VISIBLE);
        holder.b.tvContent.setText(msg.content != null ? msg.content : "");
        holder.b.tvContent.setTextColor(0xFF333333);
        bindTranslate(holder, msg);
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
        playWrap.setBackgroundResource(played
                ? R.drawable.bg_chat_audio_play_played
                : R.drawable.bg_chat_audio_play);
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
        if (msg.content != null && !msg.content.isEmpty()) return msg.content;
        return "رسالة";
    }

    private static String formatDuration(int sec) {
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
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
        final ItemMessageBinding b;
        VH(ItemMessageBinding b) { super(b.getRoot()); this.b = b; }
    }
}
