package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public final class MiscDtos {
    private MiscDtos() {}

    public static class VipPlanDto {
        @SerializedName("id") public String id;
        @SerializedName("level") public int level;
        @SerializedName("name") public String name;
        @SerializedName("coinPriceMonthly") public int coinPriceMonthly;
        @SerializedName("badgeUrl") public String badgeUrl;
        @SerializedName("benefits") public VipBenefits benefits;
        @SerializedName("isActive") public boolean isActive;
    }

    public static class VipBenefits {
        @SerializedName("entryEffect") public boolean entryEffect;
        @SerializedName("antiKick") public boolean antiKick;
        @SerializedName("antiMute") public boolean antiMute;
        @SerializedName("customFrame") public boolean customFrame;
        @SerializedName("exclusiveGifts") public boolean exclusiveGifts;
        @SerializedName("flyingComment") public boolean flyingComment;
        @SerializedName("roomPriority") public int roomPriority;
        @SerializedName("dmLimit") public int dmLimit;
        @SerializedName("extra") public List<String> extra;
    }

    public static class PurchaseVipRequest {
        @SerializedName("level") public int level;
        /** 7 = weekly, 30 = monthly, 40 = 40 days — VIP is never permanent. */
        @SerializedName("durationDays") public int durationDays;
        public PurchaseVipRequest(int level) { this(level, 30); }
        public PurchaseVipRequest(int level, int durationDays) {
            this.level = level;
            this.durationDays = durationDays;
        }
    }

    /** VIP plans API returns a raw array in `data`. */
    public static class VipPlanList extends java.util.ArrayList<VipPlanDto> {}

    public static class AgencyDto {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
        @SerializedName("logoUrl") public String logoUrl;
        @SerializedName("coverUrl") public String coverUrl;
        /** Decorative frame / room card overlay when live. */
        @SerializedName("frameUrl") public String frameUrl;
        @SerializedName("description") public String description;
        @SerializedName("memberCount") public int memberCount;
        @SerializedName("ownerId") public String ownerId;
        @SerializedName("status") public String status;
        @SerializedName("commissionPercent") public double commissionPercent;
        @SerializedName("totalDiamonds") public long totalDiamonds;
        @SerializedName("activationCode") public String activationCode;
        @SerializedName("notificationStyle") public String notificationStyle;
        @SerializedName("members") public java.util.List<AgencyMemberDto> members;
        /** Short shareable agency ID (admin-assigned). */
        @SerializedName("publicId") public String publicId;
        /** Official verified badge for active agencies. */
        @SerializedName("isVerified") public boolean isVerified;
        @SerializedName("exclusiveFrameCode") public String exclusiveFrameCode;
        @SerializedName("exclusiveRoomCardCode") public String exclusiveRoomCardCode;
        @SerializedName("exclusiveFrameUrl") public String exclusiveFrameUrl;
        /** True when an agency voice room is open with an active host. */
        @SerializedName("isLive") public boolean isLive;
        @SerializedName("openRoomId") public String openRoomId;
        @SerializedName("liveViewerCount") public int liveViewerCount;
    }

    public static class AgencyMemberDto {
        @SerializedName("userId") public String userId;
        @SerializedName("role") public String role;
        @SerializedName("status") public String status;
        @SerializedName("isActive") public Boolean isActive;
        @SerializedName("user") public AuthDtos.UserDto user;

        public boolean isPending() {
            return status != null && "pending".equalsIgnoreCase(status);
        }
    }

    public static class AgencyPricingDto {
        @SerializedName("createPriceCoins") public int createPriceCoins;
        @SerializedName("createFree") public boolean createFree;
        @SerializedName("defaultCommissionPercent") public double defaultCommissionPercent;
        @SerializedName("platformCutPercent") public double platformCutPercent;
        @SerializedName("hostSharePercent") public double hostSharePercent;
        @SerializedName("autoApproveAfterPayment") public boolean autoApproveAfterPayment;
        @SerializedName("isPaid") public boolean isPaid;
        @SerializedName("currency") public String currency;
    }

    public static class AgencyApplicationDto {
        @SerializedName("id") public String id;
        @SerializedName("status") public String status;
        @SerializedName("reviewNote") public String reviewNote;
        @SerializedName("proposedName") public String proposedName;
        @SerializedName("description") public String description;
        @SerializedName("businessPlan") public String businessPlan;
        @SerializedName("country") public String country;
        @SerializedName("contactEmail") public String contactEmail;
        @SerializedName("contactPhone") public String contactPhone;
        @SerializedName("socialLink") public String socialLink;
        @SerializedName("experience") public String experience;
        @SerializedName("expectedHostCount") public int expectedHostCount;
        @SerializedName("documentUrls") public List<String> documentUrls;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("updatedAt") public String updatedAt;
    }

    public static class AgencyApplicationRequest {
        @SerializedName("proposedName") public String proposedName;
        @SerializedName("description") public String description;
        @SerializedName("businessPlan") public String businessPlan;
        @SerializedName("country") public String country;
        @SerializedName("contactEmail") public String contactEmail;
        @SerializedName("contactPhone") public String contactPhone;
        @SerializedName("socialLink") public String socialLink;
        @SerializedName("experience") public String experience;
        @SerializedName("expectedHostCount") public int expectedHostCount;
        @SerializedName("documentUrls") public List<String> documentUrls;
        @SerializedName("termsAccepted") public boolean termsAccepted;
    }

    public static class RankingEntryDto {
        @SerializedName("rank") public int rank;
        @SerializedName("userId") public String userId;
        @SerializedName("targetId") public String targetId;
        @SerializedName("targetName") public String targetName;
        @SerializedName("score") public long score;
        @SerializedName("user") public AuthDtos.UserDto user;
    }

    public static class NotificationDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("title") public String title;
        @SerializedName("body") public String body;
        @SerializedName("data") public Map<String, Object> data;
        @SerializedName("isRead") public boolean isRead;
        @SerializedName("createdAt") public String createdAt;
    }

    public static class OfficialNewsPreviewDto {
        @SerializedName("unread") public int unread;
        @SerializedName("lastTitle") public String lastTitle;
        @SerializedName("lastBody") public String lastBody;
        @SerializedName("lastAt") public String lastAt;
    }

    public static class UpdateProfileRequest {
        @SerializedName("displayName") public String displayName;
        @SerializedName("bio") public String bio;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("gender") public String gender;
        @SerializedName("birthday") public String birthday;
        @SerializedName("country") public String country;
        @SerializedName("showOnlineStatus") public Boolean showOnlineStatus;
        @SerializedName("allowDmFromStrangers") public Boolean allowDmFromStrangers;
        @SerializedName("dmGiftGateEnabled") public Boolean dmGiftGateEnabled;
        @SerializedName("dmRequiredGiftId") public String dmRequiredGiftId;
        @SerializedName("albumUrls") public java.util.List<String> albumUrls;

        public UpdateProfileRequest() {}

        public UpdateProfileRequest(String displayName, String bio, String avatarUrl, String coverUrl, String gender) {
            this.displayName = displayName;
            this.bio = bio;
            this.avatarUrl = avatarUrl;
            this.coverUrl = coverUrl;
            this.gender = gender;
        }

        public UpdateProfileRequest(String displayName, String bio, String avatarUrl, String coverUrl, String gender,
                                    String birthday, String country) {
            this(displayName, bio, avatarUrl, coverUrl, gender);
            this.birthday = birthday;
            this.country = country;
        }
    }

    public static class GenderVerificationDto {
        @SerializedName("genderVerified") public boolean genderVerified;
        @SerializedName("genderVerificationStatus") public String genderVerificationStatus;
        @SerializedName("selfieUrl") public String selfieUrl;
        @SerializedName("reviewNote") public String reviewNote;
        @SerializedName("submittedAt") public String submittedAt;
        @SerializedName("reviewedAt") public String reviewedAt;
    }

    public static class SubmitGenderVerificationRequest {
        @SerializedName("selfieUrl") public String selfieUrl;
        @SerializedName("livenessPassed") public boolean livenessPassed;
        @SerializedName("livenessScore") public float livenessScore;
        @SerializedName("livenessYawCenter") public float livenessYawCenter;
        @SerializedName("livenessYawLeft") public float livenessYawLeft;
        @SerializedName("livenessYawRight") public float livenessYawRight;
        @SerializedName("blinkPassed") public boolean blinkPassed;
        @SerializedName("faceTrackingStable") public boolean faceTrackingStable;
        @SerializedName("femaleConfidence") public float femaleConfidence;
    }

    public static class ListResult<T> {
        @SerializedName("items") public List<T> items;
        @SerializedName("page") public int page;
        @SerializedName("total") public int total;
        @SerializedName("limit") public int limit;
        @SerializedName("meta") public PageMeta meta;

        public int resolveTotal() {
            if (meta != null && meta.total > 0) return meta.total;
            return total;
        }

        public int resolvePage() {
            if (meta != null && meta.page > 0) return meta.page;
            return page;
        }
    }

    public static class PageMeta {
        @SerializedName("total") public int total;
        @SerializedName("page") public int page;
        @SerializedName("limit") public int limit;
        @SerializedName("totalPages") public int totalPages;
    }

    public static class BannerDto {
        @SerializedName("imageUrl") public String imageUrl;
        @SerializedName("title") public String title;
        @SerializedName("link") public String link;
        @SerializedName("lang") public String lang;
    }

    public static class OfferDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("subtitle") public String subtitle;
        @SerializedName("sku") public String sku;
        @SerializedName("coins") public int coins;
        @SerializedName("bonusCoins") public int bonusCoins;
        @SerializedName("priceUsd") public double priceUsd;
        @SerializedName("lottieUrl") public String lottieUrl;
        @SerializedName("imageUrl") public String imageUrl;
        @SerializedName("popular") public boolean popular;
        @SerializedName("active") public boolean active;
        public transient String playPriceLabel;
        public transient double playPriceAmount;
        public transient boolean playPriceLoaded;

        public String displayPrice() {
            if (playPriceLabel != null && !playPriceLabel.isEmpty()) return playPriceLabel;
            return String.format(java.util.Locale.US, "$%.2f", priceUsd);
        }

        public double amountForVerify() {
            return playPriceLoaded ? playPriceAmount : priceUsd;
        }
    }

    public static class GameDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("titleEn") public String titleEn;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("playUrl") public String playUrl;
        @SerializedName("sortOrder") public int sortOrder;
        @SerializedName("mode") public String mode;
        /** When false, game is hidden. Null/omitted means visible. */
        @SerializedName("enabled") public Boolean enabled;
    }

    /** Public feature flags from GET /config/features. */
    public static class FeaturesDto {
        /** When true, female users must pass identity verification for host features. */
        @SerializedName("femaleOnlyVoiceHosts") public boolean femaleOnlyVoiceHosts = false;
        @SerializedName("genderVerificationAutoAccept") public boolean genderVerificationAutoAccept;
        /** When true, guests can take a mic seat without host approval. */
        @SerializedName("micWithoutHostApproval") public boolean micWithoutHostApproval = true;
        /** Room rules banner text managed from dashboard. */
        @SerializedName("roomRulesText") public String roomRulesText;
        /** When false, gift effect audio is fully muted across the app. */
        @SerializedName("giftSoundsEnabled") public boolean giftSoundsEnabled = true;
        /** When false, hide all tasks UI and stop client-side task flows. */
        @SerializedName("tasksEnabled") public boolean tasksEnabled = true;
        @SerializedName("autoModeration") public boolean autoModeration = true;
        @SerializedName("chatPromoFilterEnabled") public boolean chatPromoFilterEnabled = true;
        @SerializedName("chatPromoKickEnabled") public boolean chatPromoKickEnabled = true;
        @SerializedName("liveNsfwEnabled") public boolean liveNsfwEnabled = true;
        @SerializedName("extraKeywords") public java.util.List<String> extraKeywords;
    }

    /** Store update prompt from GET /config/app-update. */
    public static class AppUpdateDto {
        @SerializedName("latestVersionCode") public int latestVersionCode;
        @SerializedName("latestVersionName") public String latestVersionName;
        @SerializedName("minVersionCode") public int minVersionCode;
        @SerializedName("forceUpdate") public boolean forceUpdate;
        @SerializedName("titleAr") public String titleAr;
        @SerializedName("messageAr") public String messageAr;
        @SerializedName("titleEn") public String titleEn;
        @SerializedName("messageEn") public String messageEn;
        @SerializedName("storeUrl") public String storeUrl;
    }

    /** Public ZEGOCLOUD client config from GET /config/zego (AppID only — AppSign never sent). */
    public static class ZegoConfigDto {
        @SerializedName("appId") public long appId;
        /** Always empty from server; kept for older clients. */
        @SerializedName("appSign") public String appSign;
        @SerializedName("wsUrl") public String wsUrl;
        @SerializedName("wsUrlBak") public String wsUrlBak;
    }

    /** Customer support channels from GET /config/support. */
    public static class SupportConfigDto {
        @SerializedName("whatsapp") public String whatsapp;
        @SerializedName("telegram") public String telegram;
        @SerializedName("instagram") public String instagram;
        @SerializedName("email") public String email;
        @SerializedName("phone") public String phone;
    }

    /** Sham Cash (Syria) from GET /config/sham-cash — WhatsApp manual top-up. */
    public static class ShamCashConfigDto {
        @SerializedName("enabled") public boolean enabled;
        @SerializedName("whatsapp") public String whatsapp;
        @SerializedName("displayName") public String displayName;
        @SerializedName("accountName") public String accountName;
        @SerializedName("accountId") public String accountId;
        @SerializedName("instructions") public String instructions;
    }

    /** Bottom tab icons from GET /config/nav-icons (PNG/JPG URLs). */
    public static class NavIconsDto {
        @SerializedName("version") public int version;
        @SerializedName("updatedAt") public String updatedAt;
        @SerializedName("party") public NavIconPairDto party;
        @SerializedName("drama") public NavIconPairDto drama;
        @SerializedName("games") public NavIconPairDto games;
        @SerializedName("chat") public NavIconPairDto chat;
        @SerializedName("me") public NavIconPairDto me;
    }

    public static class NavIconPairDto {
        @SerializedName("normal") public String normal;
        @SerializedName("selected") public String selected;
        @SerializedName("icon") public String icon;
    }

    public static class ThemeDto {
        @SerializedName("version") public int version;
        @SerializedName("updatedAt") public String updatedAt;
        @SerializedName("brand") public ThemeBrand brand;
        @SerializedName("colors") public ThemeColors colors;
        @SerializedName("backgrounds") public ThemeBackgrounds backgrounds;
        @SerializedName("assets") public Map<String, String> assets;

        public static ThemeDto defaults() {
            ThemeDto t = new ThemeDto();
            t.version = 1;
            t.brand = new ThemeBrand();
            t.brand.appName = "JEHO CHAT";
            t.brand.logoUrl = "";
            t.brand.splashUrl = "";
            t.brand.faviconUrl = "";
            t.colors = new ThemeColors();
            t.colors.primary = "#00C2A8";
            t.colors.secondary = "#22D3EE";
            t.colors.background = "#0B1220";
            t.colors.surface = "#121A2B";
            t.colors.onPrimary = "#FFFFFF";
            t.colors.danger = "#F0435B";
            t.colors.gold = "#F5C542";
            t.backgrounds = new ThemeBackgrounds();
            // Empty — wallpapers/icons ship inside the APK.
            t.assets = new java.util.HashMap<>();
            return t;
        }
    }

    public static class ThemeBrand {
        @SerializedName("appName") public String appName;
        @SerializedName("logoUrl") public String logoUrl;
        @SerializedName("splashUrl") public String splashUrl;
        @SerializedName("faviconUrl") public String faviconUrl;
    }

    public static class ThemeColors {
        @SerializedName("primary") public String primary;
        @SerializedName("secondary") public String secondary;
        @SerializedName("background") public String background;
        @SerializedName("surface") public String surface;
        @SerializedName("onPrimary") public String onPrimary;
        @SerializedName("danger") public String danger;
        @SerializedName("gold") public String gold;
    }

    public static class ThemeBackgrounds {
        @SerializedName("app") public String app;
        @SerializedName("auth") public String auth;
        @SerializedName("splash") public String splash;
        @SerializedName("home") public String home;
        @SerializedName("profile") public String profile;
        @SerializedName("chat") public String chat;
        @SerializedName("games") public String games;
        @SerializedName("createRoom") public String createRoom;
        @SerializedName("liveRoom") public String liveRoom;
        @SerializedName("voiceRoom") public String voiceRoom;
        @SerializedName("appNight") public String appNight;
        @SerializedName("homeHeader") public String homeHeader;
        @SerializedName("roomDefault") public String roomDefault;
    }

    public static class TickerDto {
        @SerializedName("text") public String text;
    }

    public static class TaskDto {
        @SerializedName("id") public String id;
        @SerializedName("title") public String title;
        @SerializedName("rewardPoints") public int rewardPoints;
        @SerializedName("rewardSilver") public int rewardSilver;
        @SerializedName("rewardDiamonds") public int rewardDiamonds;
        @SerializedName("rewardHostDiamonds") public int rewardHostDiamonds;
        @SerializedName("type") public String type;
        /** "all" or "host" */
        @SerializedName("audience") public String audience;
        /** "daily" or "host" section hint from API */
        @SerializedName("section") public String section;
        @SerializedName("claimed") public boolean claimed;
        @SerializedName("claimable") public boolean claimable;
        @SerializedName("current") public int current;
        @SerializedName("target") public int target;
        @SerializedName("progressLabel") public String progressLabel;

        public boolean isHostTask() {
            return "host".equalsIgnoreCase(audience) || "host".equalsIgnoreCase(section);
        }
    }

    public static class AgencyEarningsDto {
        @SerializedName("agencyId") public String agencyId;
        @SerializedName("name") public String name;
        @SerializedName("ownerId") public String ownerId;
        @SerializedName("commissionPercent") public double commissionPercent;
        @SerializedName("platformCutPercent") public double platformCutPercent;
        @SerializedName("hostSharePercent") public double hostSharePercent;
        @SerializedName("memberCount") public int memberCount;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        @SerializedName("totals") public AgencyEarningsTotals totals;
        @SerializedName("periods") public AgencyEarningsPeriods periods;
        /** Live withdrawable pools — agency vs personal never mixed */
        @SerializedName("available") public AgencyAvailableBalances available;
        @SerializedName("explanation") public Map<String, String> explanation;
    }

    public static class AgencyAvailableBalances {
        @SerializedName("agencyDiamonds") public long agencyDiamonds;
        @SerializedName("agencyUsd") public double agencyUsd;
        @SerializedName("personalDiamonds") public long personalDiamonds;
        @SerializedName("personalUsd") public double personalUsd;
    }

    public static class AgencyEarningsPeriods {
        @SerializedName("week") public AgencyPeriodSlice week;
        @SerializedName("month") public AgencyPeriodSlice month;
        @SerializedName("allTime") public AgencyPeriodSlice allTime;
    }

    public static class AgencyPeriodSlice {
        @SerializedName("from") public String from;
        @SerializedName("grossGiftsDiamonds") public long grossGiftsDiamonds;
        @SerializedName("ownerCommissionEarned") public long ownerCommissionEarned;
        @SerializedName("ownerCommissionUsd") public double ownerCommissionUsd;
        @SerializedName("estimatedHostShare") public long estimatedHostShare;
        @SerializedName("estimatedHostShareUsd") public double estimatedHostShareUsd;
        @SerializedName("estimatedAgentShare") public long estimatedAgentShare;
        @SerializedName("estimatedPlatformCut") public long estimatedPlatformCut;
        @SerializedName("grossGiftsUsd") public double grossGiftsUsd;
    }

    public static class AgencyHostDashboardDto {
        @SerializedName("giftsGrossWeek") public long giftsGrossWeek;
        @SerializedName("giftsGrossMonth") public long giftsGrossMonth;
        @SerializedName("giftsGrossAllTime") public long giftsGrossAllTime;
        @SerializedName("diamondsEarnedWeek") public long diamondsEarnedWeek;
        @SerializedName("diamondsEarnedMonth") public long diamondsEarnedMonth;
        @SerializedName("diamondsEarnedAllTime") public long diamondsEarnedAllTime;
        @SerializedName("usdEarnedWeek") public double usdEarnedWeek;
        @SerializedName("usdEarnedMonth") public double usdEarnedMonth;
        @SerializedName("usdEarnedAllTime") public double usdEarnedAllTime;
        @SerializedName("walletDiamonds") public long walletDiamonds;
        @SerializedName("walletUsd") public double walletUsd;
        @SerializedName("personalDiamonds") public long personalDiamonds;
        @SerializedName("personalUsd") public double personalUsd;
        @SerializedName("agencyDiamonds") public long agencyDiamonds;
        @SerializedName("agencyUsd") public double agencyUsd;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        @SerializedName("weekFrom") public String weekFrom;
        @SerializedName("monthFrom") public String monthFrom;
    }

    public static class AgencyEarningsTotals {
        @SerializedName("grossGiftsDiamonds") public long grossGiftsDiamonds;
        @SerializedName("agencyTotalDiamonds") public long agencyTotalDiamonds;
        @SerializedName("ownerCommissionEarned") public long ownerCommissionEarned;
        @SerializedName("ownerCommissionUsd") public double ownerCommissionUsd;
        @SerializedName("estimatedHostShare") public long estimatedHostShare;
        @SerializedName("estimatedHostShareUsd") public double estimatedHostShareUsd;
        @SerializedName("estimatedPlatformCut") public long estimatedPlatformCut;
        @SerializedName("estimatedAgentShare") public long estimatedAgentShare;
        @SerializedName("platformRevenueAllTime") public long platformRevenueAllTime;
    }

    public static class AgencyPayoutRequestDto {
        @SerializedName("id") public String id;
        @SerializedName("agencyId") public String agencyId;
        @SerializedName("hostUserId") public String hostUserId;
        @SerializedName("diamonds") public long diamonds;
        @SerializedName("amountUsd") public double amountUsd;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        @SerializedName("method") public String method;
        @SerializedName("payoutDetails") public Map<String, Object> payoutDetails;
        @SerializedName("status") public String status;
        @SerializedName("reviewNote") public String reviewNote;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("host") public AuthDtos.UserDto host;
    }

    public static class AgencyPayoutListDto {
        @SerializedName("items") public java.util.List<AgencyPayoutRequestDto> items;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
    }

    public static class AgencyMineDto {
        @SerializedName("agency") public AgencyDto agency;
        @SerializedName("role") public String role;
        @SerializedName("membershipStatus") public String membershipStatus;
        @SerializedName("agencyStatus") public String agencyStatus;
        @SerializedName("canHostRoom") public boolean canHostRoom;
        @SerializedName("roomId") public String roomId;
        @SerializedName("application") public AgencyApplicationDto application;
        @SerializedName("earnings") public AgencyEarningsDto earnings;
        @SerializedName("hostDashboard") public AgencyHostDashboardDto hostDashboard;

        public boolean isEligibleHost() {
            if (!canHostRoom || agency == null || agency.id == null) return false;
            boolean activeMembership = membershipStatus == null
                    || membershipStatus.isEmpty()
                    || "active".equalsIgnoreCase(membershipStatus)
                    || "approved".equalsIgnoreCase(membershipStatus);
            boolean activeAgency = agencyStatus == null
                    || agencyStatus.isEmpty()
                    || "active".equalsIgnoreCase(agencyStatus)
                    || "approved".equalsIgnoreCase(agencyStatus);
            // Broadcast only for agency owner / manager (admin). Regular hosts: stats only.
            boolean roleAllowed = "owner".equalsIgnoreCase(role)
                    || "manager".equalsIgnoreCase(role);
            return activeMembership && activeAgency && roleAllowed;
        }
    }

    public static class LevelInfoDto {
        @SerializedName("level") public int level;
        @SerializedName("levelBadgeUrl") public String levelBadgeUrl;
        @SerializedName("experience") public long experience;
        @SerializedName("currentThreshold") public long currentThreshold;
        @SerializedName("nextThreshold") public long nextThreshold;
        @SerializedName("progress") public double progress;
    }

    public static class SocialRequestDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("status") public String status;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("user") public AuthDtos.UserDto user;
    }

    public static class CpStatusDto {
        @SerializedName("hasCp") public boolean hasCp;
        @SerializedName("partner") public AuthDtos.UserDto partner;
        @SerializedName("bondScore") public long bondScore;
        @SerializedName("level") public int level;
        @SerializedName("nextLevelAt") public long nextLevelAt;
        @SerializedName("relationId") public String relationId;
    }

    public static class BondDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("label") public String label;
        @SerializedName("iconUrl") public String iconUrl;
        @SerializedName("peerId") public String peerId;
        @SerializedName("peerName") public String peerName;
        @SerializedName("peerAvatarUrl") public String peerAvatarUrl;
    }

    public static class LuckyBoxDto {
        @SerializedName("id") public String id;
        @SerializedName("code") public String code;
        @SerializedName("title") public String title;
        @SerializedName("kind") public String kind;
        @SerializedName("costCoins") public long costCoins;
        @SerializedName("dailyLimitPerUser") public int dailyLimitPerUser;
        @SerializedName("isActive") public boolean isActive;
        @SerializedName("opensToday") public int opensToday;
        @SerializedName("remainingToday") public int remainingToday;
    }

    public static class LuckyBoxRewardDto {
        @SerializedName("openId") public String openId;
        @SerializedName("boxCode") public String boxCode;
        @SerializedName("boxTitle") public String boxTitle;
        @SerializedName("reward") public LuckyReward reward;
        @SerializedName("remainingToday") public int remainingToday;
    }

    public static class LuckyReward {
        @SerializedName("type") public String type;
        @SerializedName("amount") public long amount;
    }


    public static class AgencyRoomDto {
        @SerializedName("roomId") public String roomId;
        @SerializedName("name") public String name;
        @SerializedName("status") public String status;
    }
}
