package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class StoriesDtos {
    private StoriesDtos() {}

    public static class StoriesConfigDto {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("publishingEnabled") public boolean publishingEnabled = true;
        @SerializedName("viewingEnabled") public boolean viewingEnabled = true;
        @SerializedName("imagesEnabled") public boolean imagesEnabled = true;
        @SerializedName("videosEnabled") public boolean videosEnabled = true;
        @SerializedName("emergencyMode") public boolean emergencyMode;
        @SerializedName("cacheEnabled") public boolean cacheEnabled = true;
        @SerializedName("durationHours") public int durationHours = 24;
        @SerializedName("maxVideoSeconds") public int maxVideoSeconds = 30;
        @SerializedName("maxMediaMb") public int maxMediaMb = 20;
    }

    public static class StoryDto {
        @SerializedName("id") public String id;
        @SerializedName("userId") public String userId;
        @SerializedName("mediaType") public String mediaType;
        @SerializedName("mediaUrl") public String mediaUrl;
        @SerializedName("caption") public String caption;
        @SerializedName("viewCount") public long viewCount;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("displayName") public String displayName;
        @SerializedName("username") public String username;
        @SerializedName("publicId") public String publicId;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("viewedByMe") public boolean viewedByMe;
        @SerializedName("isMine") public boolean isMine;
    }

    public static class CreateStoryDto {
        @SerializedName("mediaType") public String mediaType;
        @SerializedName("mediaUrl") public String mediaUrl;
        @SerializedName("caption") public String caption;
        @SerializedName("visibility") public String visibility = "public";
        @SerializedName("sizeBytes") public long sizeBytes;
        @SerializedName("durationSeconds") public int durationSeconds;
    }

    public static class ViewersResult {
        @SerializedName("storyId") public String storyId;
        @SerializedName("viewCount") public long viewCount;
        @SerializedName("viewers") public java.util.List<StoryViewerDto> viewers;
    }

    public static class StoryViewerDto {
        @SerializedName("id") public String id;
        @SerializedName("username") public String username;
        @SerializedName("displayName") public String displayName;
        @SerializedName("publicId") public String publicId;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("viewedAt") public String viewedAt;
    }

    public static class ViewResult {
        @SerializedName("recorded") public boolean recorded;
        @SerializedName("counted") public boolean counted;
    }
}
