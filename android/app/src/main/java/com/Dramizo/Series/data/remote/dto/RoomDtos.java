package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class RoomDtos {
    private RoomDtos() {}

    public static class RoomDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("backgroundUrl") public String backgroundUrl;
        @SerializedName("roomCardUrl") public String roomCardUrl;
        @SerializedName("type") public String type;
        @SerializedName("status") public String status;
        @SerializedName("hostId") public String hostId;
        @SerializedName("cohostId") public String cohostId;
        @SerializedName("agencyId") public String agencyId;
        @SerializedName("roomKind") public String roomKind;
        @SerializedName("isSupport") public boolean isSupport;
        @SerializedName("isPersistent") public boolean isPersistent;
        @SerializedName("activeHostId") public String activeHostId;
        @SerializedName("moderatorIds") public List<String> moderatorIds;
        @SerializedName("moderatorPermissions") public List<ModeratorPermissionDto> moderatorPermissions;
        @SerializedName("musicUrl") public String musicUrl;
        @SerializedName("musicTitle") public String musicTitle;
        @SerializedName("musicArtist") public String musicArtist;
        @SerializedName("musicStatus") public String musicStatus;
        @SerializedName("musicPositionMs") public long musicPositionMs;
        @SerializedName("musicStartedAt") public String musicStartedAt;
        @SerializedName("seatCount") public int seatCount;
        @SerializedName("viewerCount") public int viewerCount;
        @SerializedName("zegoRoomId") public String zegoRoomId;
        @SerializedName("hasPassword") public boolean hasPassword;
        @SerializedName("isPublic") public boolean isPublic;
        @SerializedName("seats") public List<SeatDto> seats;
        @SerializedName("host") public AuthDtos.UserDto host;
        @SerializedName("challengeBadge") public String challengeBadge;
        @SerializedName("viewerAvatars") public List<String> viewerAvatars;
        /** Room progression level from gift activity (Mikoo-style Lv badge). */
        @SerializedName("roomLevel") public int roomLevel = 1;
        /** 1-based rank in explore feed sorted by viewers. */
        @SerializedName("exploreRank") public int exploreRank;
        /** When false, gift audio is muted for everyone in this room. */
        @SerializedName("giftSoundsEnabled") public boolean giftSoundsEnabled = true;
        /** Mikoo room-more display toggles (server-synced for staff). */
        @SerializedName("chatZoneEnabled") public boolean chatZoneEnabled = true;
        @SerializedName("charmEnabled") public boolean charmEnabled = true;
        @SerializedName("bannerEnabled") public boolean bannerEnabled = true;
        @SerializedName("micInteractEnabled") public boolean micInteractEnabled = true;
        @SerializedName("entryEffectsEnabled") public boolean entryEffectsEnabled = true;
        @SerializedName("lowGiftEffectsEnabled") public boolean lowGiftEffectsEnabled = true;
        /** Server stamp of last full public-chat wipe (ISO-8601). */
        @SerializedName("chatClearedAt") public String chatClearedAt;
        /** 0=off, 1/5/10 = auto wipe every N minutes. */
        @SerializedName("chatAutoClearMinutes") public int chatAutoClearMinutes;
        /** Numeric room id shown in UI (host publicId, or agency GID for agency rooms). */
        @SerializedName("displayRoomId") public String displayRoomId;
        /** Agency brand on room payload (agency rooms only). */
        @SerializedName("agencyPublicId") public String agencyPublicId;
        @SerializedName("agencyLogoUrl") public String agencyLogoUrl;
        @SerializedName("agencyLevel") public int agencyLevel;
        @SerializedName("agencyTotalDiamonds") public long agencyTotalDiamonds;
    }

    public static class ModeratorPermissionDto {
        @SerializedName("userId") public String userId;
        @SerializedName("canManageMusic") public boolean canManageMusic;
        @SerializedName("canChangeFrames") public boolean canChangeFrames;
        @SerializedName("canControlGames") public boolean canControlGames;
        @SerializedName("canMute") public boolean canMute;
        @SerializedName("canKick") public boolean canKick;
        @SerializedName("canBan") public boolean canBan;
        @SerializedName("canManageSeats") public boolean canManageSeats;
        @SerializedName("canInvite") public boolean canInvite;
        @SerializedName("canManageRoom") public boolean canManageRoom;
    }

    public static class SeatDto {
        @SerializedName("id") public String id;
        @SerializedName("seatIndex") public int seatIndex;
        @SerializedName("userId") public String userId;
        @SerializedName("isMuted") public boolean isMuted;
        @SerializedName("isModeratorMuted") public boolean isModeratorMuted;
        @SerializedName("isLocked") public boolean isLocked;
        @SerializedName("status") public String status;
        @SerializedName("user") public AuthDtos.UserDto user;

        public boolean locked() {
            return isLocked || "locked".equalsIgnoreCase(status);
        }
    }

    public static class JoinRoomResult {
        @SerializedName("room") public RoomDto room;
        @SerializedName("token") public String token;
        @SerializedName("appId") public long appId;
        @SerializedName("zegoRoomId") public String zegoRoomId;
        /** {@code zego} (default) or {@code livekit} free self-hosted. */
        @SerializedName("voiceProvider") public String voiceProvider;
        @SerializedName("livekitUrl") public String livekitUrl;
        @SerializedName("livekitRoomName") public String livekitRoomName;
        @SerializedName("userId") public String userId;
        @SerializedName("expireAt") public long expireAt;
        @SerializedName("canPublish") public boolean canPublish;
    }

    public static class RaiseHandRequest {
        @SerializedName("raised") public boolean raised;
        @SerializedName("seatIndex") public Integer seatIndex;
        public RaiseHandRequest(boolean raised) { this.raised = raised; }
        public RaiseHandRequest(boolean raised, Integer seatIndex) {
            this.raised = raised;
            this.seatIndex = seatIndex;
        }
    }

    public static class SeatRequestDto {
        @SerializedName("userId") public String userId;
        @SerializedName("seatIndex") public Integer seatIndex;
        @SerializedName("displayName") public String displayName;
        @SerializedName("at") public String at;
    }

    public static class SeatRequestsResult {
        @SerializedName("items") public java.util.List<SeatRequestDto> items;
    }

    public static class SupporterDto {
        @SerializedName("rank") public int rank;
        @SerializedName("userId") public String userId;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("hostBadgeUrl") public String hostBadgeUrl;
        @SerializedName("vipBadgeUrl") public String vipBadgeUrl;
        @SerializedName("score") public long score;
    }

    public static class SupportersResult {
        @SerializedName("roomId") public String roomId;
        @SerializedName("agencyId") public String agencyId;
        @SerializedName("room") public java.util.List<SupporterDto> room;
        @SerializedName("agency") public java.util.List<SupporterDto> agency;
        /** Mikoo dayGold — room gift coins today (K/M/B on cup chip). */
        @SerializedName("dayGold") public long dayGold;
    }

    public static class ContributeResult {
        @SerializedName("roomId") public String roomId;
        @SerializedName("type") public String type;
        @SerializedName("period") public String period;
        @SerializedName("dayGold") public long dayGold;
        @SerializedName("items") public java.util.List<SupporterDto> items;
    }

    public static class RoomBanDto {
        @SerializedName("id") public String id;
        /** "ban" or "chat_mute" */
        @SerializedName("kind") public String kind;
        @SerializedName("userId") public String userId;
        @SerializedName("reason") public String reason;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("user") public AuthDtos.UserDto user;
    }

    public static class RoomBanListResult {
        @SerializedName("items") public java.util.List<RoomBanDto> items;
    }

    public static class MusicLibraryDto {
        @SerializedName("items") public java.util.List<MusicTrackDto> items;
    }

    public static class MusicTrackDto {
        @SerializedName("id") public String id;
        @SerializedName("url") public String url;
        @SerializedName("title") public String title;
        @SerializedName("artist") public String artist;
        @SerializedName("uploadedBy") public String uploadedBy;
        @SerializedName("updatedAt") public String updatedAt;
    }

    public static class InternetMusicSearchDto {
        @SerializedName("items") public java.util.List<InternetMusicHitDto> items;
    }

    public static class InternetMusicHitDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("artist") public String artist;
        @SerializedName("thumbnailUrl") public String thumbnailUrl;
        @SerializedName("durationSec") public Integer durationSec;
        @SerializedName("source") public String source;
    }

    public static class InternetMusicResolvedDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("artist") public String artist;
        @SerializedName("thumbnailUrl") public String thumbnailUrl;
        @SerializedName("audioUrl") public String audioUrl;
        @SerializedName("mimeType") public String mimeType;
        @SerializedName("expiresHintSec") public Integer expiresHintSec;
        @SerializedName("source") public String source;
    }

    public static class LockRoomRequest {
        @SerializedName("locked") public boolean locked;
        @SerializedName("password") public String password;
        public LockRoomRequest(boolean locked, String password) {
            this.locked = locked; this.password = password;
        }
    }

    public static class CreateRoomRequest {
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("type") public String type;
        @SerializedName("seatCount") public int seatCount;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("backgroundUrl") public String backgroundUrl;
        /** Force personal STANDARD room even when the host is an agency host. */
        @SerializedName("preferPersonal") public Boolean preferPersonal;

        public CreateRoomRequest(String title, String type, int seatCount, String password, boolean isPublic) {
            this(title, type, seatCount, password, isPublic, null);
        }

        public CreateRoomRequest(String title, String type, int seatCount, String password, boolean isPublic, String coverUrl) {
            this.title = title;
            this.type = type;
            this.seatCount = seatCount;
            this.coverUrl = coverUrl;
        }

        public static CreateRoomRequest agencyRoom(String agencyId, String title, String coverUrl) {
            CreateRoomRequest r = new CreateRoomRequest(title, "voice", 10, null, true, coverUrl);
            r.preferPersonal = false;
            return r;
        }

        public static CreateRoomRequest personalRoom(String title, String coverUrl) {
            CreateRoomRequest r = new CreateRoomRequest(title, "voice", 10, null, true, coverUrl);
            r.preferPersonal = true;
            return r;
        }
    }
}
