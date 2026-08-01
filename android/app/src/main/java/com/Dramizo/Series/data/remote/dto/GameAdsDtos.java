package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class GameAdsDtos {
    private GameAdsDtos() {}

    public static class AdsConfigDto {
        @SerializedName("adsEnabled") public boolean adsEnabled;
        @SerializedName("appId") public String appId;
        @SerializedName("bannerEnabled") public boolean bannerEnabled;
        @SerializedName("bannerId") public String bannerId;
        @SerializedName("interstitialEnabled") public boolean interstitialEnabled;
        @SerializedName("interstitialId") public String interstitialId;
        @SerializedName("interstitialEveryNOpens") public int interstitialEveryNOpens = 3;
        @SerializedName("interstitialOnClose") public boolean interstitialOnClose;
        @SerializedName("rewardedEnabled") public boolean rewardedEnabled;
        @SerializedName("rewardedId") public String rewardedId;
        @SerializedName("rewardedCoins") public int rewardedCoins;
        @SerializedName("rewardedDailyCap") public int rewardedDailyCap;

        public static AdsConfigDto disabled() {
            AdsConfigDto d = new AdsConfigDto();
            d.adsEnabled = false;
            d.interstitialEveryNOpens = 3;
            return d;
        }
    }

    public static class ClaimResultDto {
        @SerializedName("credited") public boolean credited;
        @SerializedName("coins") public int coins;
        @SerializedName("remainingToday") public int remainingToday;
        @SerializedName("balance") public long balance;
        @SerializedName("dailyCapReached") public boolean dailyCapReached;
    }
}
