package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class EventDtos {
    private EventDtos() {}

    public static class EventList {
        @SerializedName("items") public List<EventDto> items;
    }

    public static class EventDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("tag") public String tag;
        @SerializedName("status") public String status;
        @SerializedName("startAt") public String startAt;
        @SerializedName("endAt") public String endAt;
        @SerializedName("hostId") public String hostId;
        @SerializedName("roomId") public String roomId;
        @SerializedName("roomTitle") public String roomTitle;
        @SerializedName("roomCoverUrl") public String roomCoverUrl;
        @SerializedName("roomKind") public String roomKind;
        @SerializedName("agencyId") public String agencyId;
        @SerializedName("agencyName") public String agencyName;
        @SerializedName("agencyLogoUrl") public String agencyLogoUrl;
        @SerializedName("isAgencyRoom") public boolean isAgencyRoom;
        @SerializedName("subscribersCount") public int subscribersCount;
        @SerializedName("isPublic") public boolean isPublic;
        @SerializedName("subscribed") public boolean subscribed;
        @SerializedName("isHost") public boolean isHost;
        @SerializedName("host") public HostDto host;
    }

    public static class HostDto {
        @SerializedName("id") public String id;
        @SerializedName("username") public String username;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("country") public String country;
    }

    public static class CreateEventRequest {
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("tag") public String tag;
        @SerializedName("startAt") public String startAt;
        @SerializedName("endAt") public String endAt;
        @SerializedName("roomId") public String roomId;
        @SerializedName("isPublic") public Boolean isPublic;
    }
}
