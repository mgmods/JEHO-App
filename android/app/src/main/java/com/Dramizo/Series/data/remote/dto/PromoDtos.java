package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public final class PromoDtos {
    private PromoDtos() {}

    public static class Catalog {
        @SerializedName("version") public String version;
        @SerializedName("monthlyOffers") public List<Offer> monthlyOffers;
        @SerializedName("supporterPacks") public List<Offer> supporterPacks;
        @SerializedName("agentTiers") public List<AgentTier> agentTiers;
        @SerializedName("vipDurations") public List<VipDuration> vipDurations;
    }

    public static class MyProgress {
        @SerializedName("monthKey") public String monthKey;
        @SerializedName("usdSpent") public double usdSpent;
        @SerializedName("claimedOfferIds") public List<String> claimedOfferIds;
        @SerializedName("monthlyOffers") public List<OfferProgress> monthlyOffers;
        @SerializedName("supporterPacks") public List<OfferProgress> supporterPacks;
        @SerializedName("agentTiers") public List<AgentTier> agentTiers;
        @SerializedName("vipDurations") public List<VipDuration> vipDurations;

        public List<OfferProgress> safeMonthly() {
            return monthlyOffers != null ? monthlyOffers : Collections.emptyList();
        }

        public List<OfferProgress> safeSupporter() {
            return supporterPacks != null ? supporterPacks : Collections.emptyList();
        }
    }

    public static class Offer {
        @SerializedName("id") public String id;
        @SerializedName("thresholdUsd") public double thresholdUsd;
        @SerializedName("rewardDays") public int rewardDays;
        @SerializedName("titleAr") public String titleAr;
        @SerializedName("titleEn") public String titleEn;
        @SerializedName("descriptionAr") public String descriptionAr;
        @SerializedName("descriptionEn") public String descriptionEn;
    }

    public static class OfferProgress extends Offer {
        @SerializedName("progressUsd") public double progressUsd;
        @SerializedName("claimed") public boolean claimed;
        @SerializedName("unlocked") public boolean unlocked;
    }

    public static class AgentTier {
        @SerializedName("id") public String id;
        @SerializedName("thresholdUsd") public double thresholdUsd;
        @SerializedName("bonusPercent") public int bonusPercent;
        @SerializedName("titleAr") public String titleAr;
    }

    public static class VipDuration {
        @SerializedName("id") public String id;
        @SerializedName("days") public int days;
        @SerializedName("priceMultiplier") public double priceMultiplier;
        @SerializedName("labelAr") public String labelAr;
        @SerializedName("labelEn") public String labelEn;
        @SerializedName("color") public String color;
    }
}
