package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class DramaDtos {
    private DramaDtos() {}

    public static class DramaConfigDto {
        @SerializedName("enabled") public boolean enabled;
        @SerializedName("ads") public AdsConfigDto ads;
        @SerializedName("rewards") public RewardsConfigDto rewards;
    }

    public static class AdsConfigDto {
        @SerializedName("adsEnabled") public boolean adsEnabled = true;
        @SerializedName("testMode") public boolean testMode = false;
        @SerializedName("appId") public String appId;
        @SerializedName("bannerId") public String bannerId;
        @SerializedName("interstitialId") public String interstitialId;
        @SerializedName("rewardedId") public String rewardedId;
        @SerializedName("rewardedInterval") public int rewardedInterval = 1;
        @SerializedName("freeEpisodesCount") public int freeEpisodesCount = 1;

        public static AdsConfigDto defaults() {
            AdsConfigDto d = new AdsConfigDto();
            d.adsEnabled = true;
            d.testMode = false;
            d.rewardedInterval = 1;
            d.freeEpisodesCount = 1;
            return d;
        }
    }

    public static class RewardsConfigDto {
        @SerializedName("enabled") public boolean enabled = true;
        @SerializedName("coinsPerEpisode") public int coinsPerEpisode = 5;
        @SerializedName("watchThreshold") public double watchThreshold = 0.8;

        public RewardsConfigDto() {
            this.enabled = true;
            this.coinsPerEpisode = 5;
            this.watchThreshold = 0.8;
        }

        public static RewardsConfigDto defaults() {
            return new RewardsConfigDto();
        }
    }

    public static class ClaimRewardResult {
        @SerializedName("credited") public boolean credited;
        @SerializedName("coins") public int coins;
        @SerializedName("alreadyClaimed") public boolean alreadyClaimed;
        @SerializedName("balance") public long balance;
    }

    public static class SeriesDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("category") public String category;
        @SerializedName("isFeatured") public boolean isFeatured;
        @SerializedName("episodeCount") public int episodeCount;
        @SerializedName("totalViews") public long totalViews;
        @SerializedName("totalLikes") public long totalLikes;
        @SerializedName("isLiked") public boolean isLiked;
        @SerializedName("episodes") public List<EpisodeDto> episodes;
    }

    public static class EpisodeDto {
        @SerializedName("id") public String id;
        @SerializedName("seriesId") public String seriesId;
        @SerializedName("title") public String title;
        @SerializedName("episodeNumber") public int episodeNumber;
        @SerializedName("videoUrl") public String videoUrl;
        @SerializedName("thumbnailUrl") public String thumbnailUrl;
        @SerializedName("durationSec") public int durationSec;
        @SerializedName("viewCount") public long viewCount;
        @SerializedName("likeCount") public long likeCount;
        @SerializedName("isLiked") public boolean isLiked;
    }
}
