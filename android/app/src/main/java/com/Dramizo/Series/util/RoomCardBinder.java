package com.Dramizo.Series.util;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ItemPartyRoomBinding;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mikoo {@code HomeLiveListAdapter} / {@code item_home_live_list} binder.
 * Cover, level badge image, pretty ID (ic_pretty_id_level_* by room level), flag, title, online chips, viewer count.
 */
public final class RoomCardBinder {
    private static final int MAX_AVATARS = 5;
    /** Mikoo pretty-ID banners — curved ends + "ID" shield; index by roomLevel 1..6. */
    private static final int[] PRETTY_ID_BG = {
            R.drawable.ic_pretty_id_level_1,
            R.drawable.ic_pretty_id_level_2,
            R.drawable.ic_pretty_id_level_3,
            R.drawable.ic_pretty_id_level_4,
            R.drawable.ic_pretty_id_level_5,
            R.drawable.ic_pretty_id_level_6,
    };

    private RoomCardBinder() {}

    public static void bind(ItemPartyRoomBinding b, @Nullable RoomDtos.RoomDto room) {
        bind(b, room, false);
    }

    public static void bind(ItemPartyRoomBinding b, @Nullable RoomDtos.RoomDto room, boolean compactStrip) {
        if (b == null || room == null) return;

        if (b.tvWelcome != null) b.tvWelcome.setVisibility(View.GONE);
        if (b.tvLiveBadge != null) b.tvLiveBadge.setVisibility(View.GONE);
        if (b.rowRoomLevel != null) b.rowRoomLevel.setVisibility(View.GONE);
        // Always show type chip so users separate personal vs agency rooms at a glance.
        if (b.tvRoomTypeBadge != null) {
            RoomUiHelper.bindTypeBadge(b.tvRoomTypeBadge, room);
        }

        String title = displayTitle(room);
        b.tvTitle.setText(title);
        b.tvTitle.setSelected(false);
        b.tvHost.setText(resolveHostName(room));

        RoomUiHelper.bindLockOverlay(b.flLock, room);
        bindRoomLevelBadge(b, room);
        bindOfficialBadge(b, room);
        bindHotTop(b, room);
        bindCountryFlag(b, room);
        bindPrettyId(b, room);
        bindCover(b, room);
        bindAvatars(b, room);
        bindViewerCount(b, room);
        bindLiveEqualizer(b);
        bindCardFrame(b, room, compactStrip);
    }

    private static String displayTitle(RoomDtos.RoomDto room) {
        boolean agency = RoomUiHelper.isAgencyRoom(room);
        String title = room.title != null && !room.title.isEmpty() ? room.title.trim() : "";
        if (title.startsWith("وكالة · ")) title = title.substring("وكالة · ".length()).trim();
        if (title.startsWith("وكالة·")) title = title.substring("وكالة·".length()).trim();
        if (!title.isEmpty()) {
            return agency ? title : title;
        }
        String hostName = resolveHostName(room);
        if (hostName != null && !hostName.isEmpty() && !"مضيف".equals(hostName)) {
            return hostName;
        }
        return "غرفة";
    }

    /** Mikoo ivRoomLevel — only when room has a real level (>1); default gone like Mikoo XML. */
    private static void bindRoomLevelBadge(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        if (b.imgRoomLevel == null) return;
        int level = Math.max(0, room.roomLevel);
        if (level <= 1) {
            b.imgRoomLevel.setVisibility(View.GONE);
            return;
        }
        b.imgRoomLevel.setImageResource(R.drawable.icon_room_level_1);
        b.imgRoomLevel.setVisibility(View.VISIBLE);
        b.imgRoomLevel.setContentDescription("LV" + level);
    }

    private static void bindOfficialBadge(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        if (b.imgOfficialRoom == null) return;
        b.imgOfficialRoom.setVisibility(View.GONE);
    }

    /**
     * Hot rank chips (1/2/3) — disabled; ranking numbers are not shown on feed cards.
     */
    private static void bindHotTop(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        if (b.imgHotTop != null) b.imgHotTop.setVisibility(View.GONE);
        if (b.tvHotTop != null) b.tvHotTop.setVisibility(View.GONE);
    }

    private static void bindCountryFlag(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        String country = room.host != null ? room.host.country : null;
        FlagImages.bind(b.imgCountryFlag, b.tvCountryFlag, country);
    }

    /** Mikoo VipIdView — room display ID on curved level banner (ID badge + number). */
    private static void bindPrettyId(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        if (b.rowPrettyId == null) return;
        // Home room cards: no pretty ID row (requested).
        b.rowPrettyId.setVisibility(View.GONE);
    }

    private static void bindCover(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        String cover = resolveCover(room);
        String abs = AssetCatalog.absoluteUrl(cover);
        Object tag = b.imgCover.getTag(R.id.tag_room_cover_url);
        boolean loadedOk = Boolean.TRUE.equals(b.imgCover.getTag(R.id.tag_room_cover_loaded));
        // Skip only after a successful decode — placeholder/error drawables must not lock the tag.
        if (abs != null && abs.equals(tag) && loadedOk && b.imgCover.getDrawable() != null) {
            return;
        }
        b.imgCover.setTag(R.id.tag_room_cover_url, abs);
        b.imgCover.setTag(R.id.tag_room_cover_loaded, Boolean.FALSE);
        if (abs == null || abs.isEmpty()) {
            Glide.with(b.imgCover).clear(b.imgCover);
            b.imgCover.setImageResource(ImagePlaceholder.cover());
            return;
        }
        int w = b.imgCover.getWidth();
        int h = b.imgCover.getHeight();
        if (w <= 0 || h <= 0) {
            float d = b.imgCover.getResources().getDisplayMetrics().density;
            w = Math.round(175 * d);
            h = Math.round(195 * d);
        }
        Glide.with(b.imgCover)
                .load(abs)
                .dontAnimate()
                .placeholder(ImagePlaceholder.cover())
                .error(ImagePlaceholder.cover())
                .centerCrop()
                .override(w, h)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource) {
                        b.imgCover.setTag(R.id.tag_room_cover_loaded, Boolean.FALSE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource dataSource,
                            boolean isFirstResource) {
                        b.imgCover.setTag(R.id.tag_room_cover_loaded, Boolean.TRUE);
                        return false;
                    }
                })
                .into(b.imgCover);
    }

    /** Clear cover load state when a list card is recycled. */
    public static void clearCoverState(@Nullable ItemPartyRoomBinding b) {
        if (b == null || b.imgCover == null) return;
        b.imgCover.setTag(R.id.tag_room_cover_url, null);
        b.imgCover.setTag(R.id.tag_room_cover_loaded, Boolean.FALSE);
    }

    public static void bindViewerOnly(ItemPartyRoomBinding b, @Nullable RoomDtos.RoomDto room) {
        if (b == null || room == null) return;
        bindViewerCount(b, room);
    }

    private static void bindViewerCount(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        int viewers = Math.max(room.viewerCount, 0);
        String text = formatCount(Math.max(viewers, 1));
        // Mikoo uses tv_room_num TextView with drawableStart — prefer that.
        if (b.tvExtraCount != null) {
            b.tvExtraCount.setVisibility(View.VISIBLE);
            b.tvExtraCount.setText(text);
        }
        if (b.rowViewerCount != null) {
            b.rowViewerCount.setVisibility(View.GONE);
        }
    }

    private static void bindLiveEqualizer(ItemPartyRoomBinding b) {
        // Count icon is drawableStart on tvExtraCount (Mikoo tv_room_num).
        if (b.imgLiveEq != null) b.imgLiveEq.setVisibility(View.GONE);
    }

    private static void bindCardFrame(ItemPartyRoomBinding b, RoomDtos.RoomDto room, boolean compactStrip) {
        if (b.itemCard != null) {
            b.itemCard.setBackgroundResource(android.R.color.transparent);
        }
        if (b.imgCardFrame == null) return;
        // Prefer room frame; fall back to host-equipped mall card (same as in-room header).
        String url = room != null ? room.roomCardUrl : null;
        if ((url == null || url.isEmpty()) && room != null && room.host != null) {
            url = room.host.roomCardUrl;
        }
        String key = room != null ? room.id : null;
        String lower = url != null ? url.toLowerCase(Locale.ROOT) : "";
        boolean animated = lower.contains(".gif")
                || lower.contains(".webp")
                || RoomKenarHelper.isRoomFrameUrl(url);
        // fillItem=false: keep ConstraintLayout constraints on list cards (see RoomKenarHelper).
        RoomKenarHelper.bind(b.imgCardFrame, b.itemCard, url, key, animated, false);
    }

    private static void bindAvatars(ItemPartyRoomBinding b, RoomDtos.RoomDto room) {
        FrameLayout[] slots = {
                b.avatar1, b.avatar2, b.avatar3, b.avatar4, b.avatar5
        };
        ImageView[] images = {
                b.imgAvatar1, b.imgAvatar2, b.imgAvatar3, b.imgAvatar4, b.imgAvatar5
        };
        ImageView[] frames = {
                b.imgFrame1, b.imgFrame2, b.imgFrame3, b.imgFrame4, b.imgFrame5
        };

        if (b.rowAvatars != null) {
            b.rowAvatars.setVisibility(View.VISIBLE);
            b.rowAvatars.setAlpha(1f);
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) slots[i].setVisibility(View.GONE);
            if (frames[i] != null) {
                frames[i].setVisibility(View.GONE);
                frames[i].setImageDrawable(null);
            }
            if (images[i] != null) {
                try {
                    Glide.with(images[i]).clear(images[i]);
                } catch (Exception ignored) {
                }
            }
        }

        List<AuthDtos.UserDto> users = resolveViewerUsers(room);
        int shown = Math.min(MAX_AVATARS, users.size());
        if (shown == 0 && b.rowAvatars != null) {
            b.rowAvatars.setAlpha(0f);
        }
        for (int i = 0; i < shown; i++) {
            if (slots[i] == null || images[i] == null) continue;
            slots[i].setVisibility(View.VISIBLE);
            AuthDtos.UserDto u = users.get(i);
            AvatarImageLoader.load(images[i], u != null ? u.avatarUrl : null);
            // Host / seated VIP frame on the tiny card avatar (was missing).
            if (frames[i] != null && u != null) {
                AvatarCosmetics.bindWearOnAvatar(images[i], frames[i], u);
            }
        }
    }

    private static List<AuthDtos.UserDto> resolveViewerUsers(RoomDtos.RoomDto room) {
        List<AuthDtos.UserDto> users = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // Host first so their VIP frame shows on the small card avatar.
        if (room.host != null
                && room.host.avatarUrl != null && !room.host.avatarUrl.isEmpty()) {
            seen.add(room.host.avatarUrl);
            users.add(room.host);
        }
        if (room.seats != null) {
            for (RoomDtos.SeatDto seat : room.seats) {
                if (seat == null || seat.user == null) continue;
                String url = seat.user.avatarUrl;
                if (url == null || url.isEmpty() || seen.contains(url)) continue;
                seen.add(url);
                users.add(seat.user);
                if (users.size() >= MAX_AVATARS) break;
            }
        }
        if (users.size() >= MAX_AVATARS) return users;
        if (room.viewerAvatars != null) {
            for (String url : room.viewerAvatars) {
                if (url == null || url.isEmpty() || seen.contains(url)) continue;
                seen.add(url);
                AuthDtos.UserDto u = new AuthDtos.UserDto();
                u.avatarUrl = url;
                users.add(u);
                if (users.size() >= MAX_AVATARS) break;
            }
        }
        return users;
    }

    private static String resolveCover(RoomDtos.RoomDto room) {
        // Prefer a real cover; fall back to host avatar when the list has no custom art.
        String cover = room.coverUrl != null ? room.coverUrl.trim() : "";
        if (!cover.isEmpty() && !isGenericServerCover(cover)) {
            return cover;
        }
        if (room.host != null && room.host.avatarUrl != null && !room.host.avatarUrl.isEmpty()) {
            return room.host.avatarUrl;
        }
        if (!cover.isEmpty()) return cover;
        return null;
    }

    public static boolean isGenericServerCoverForList(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        // Placeholder list cards + the default background pack rarely look good on feed tiles.
        return (lower.contains("/assets/rooms/") && lower.contains("card_"))
                || lower.contains("room_default");
    }

    private static boolean isGenericServerCover(String url) {
        return isGenericServerCoverForList(url);
    }

    private static String resolveHostName(RoomDtos.RoomDto room) {
        if (room.host == null) return "مضيف";
        if (room.host.displayName != null && !room.host.displayName.isEmpty()) {
            return room.host.displayName;
        }
        if (room.host.username != null && !room.host.username.isEmpty()) {
            return room.host.username;
        }
        return "مضيف";
    }

    private static String formatCount(int count) {
        if (count >= 1_000_000) return String.format(Locale.US, "%.1fM", count / 1_000_000f);
        if (count >= 1_000) return String.format(Locale.US, "%.1fK", count / 1_000f);
        return String.valueOf(count);
    }
}
