package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class BadgeDto {
    @SerializedName("id") public String id;
    @SerializedName("name") public String name;
    @SerializedName("description") public String description;
    @SerializedName("imageUrl") public String imageUrl;
    @SerializedName("category") public String category;
    @SerializedName("isAnimated") public boolean isAnimated;
    @SerializedName("earned") public boolean earned;
    @SerializedName("grantedAt") public String grantedAt;
    @SerializedName("source") public String source;
}
