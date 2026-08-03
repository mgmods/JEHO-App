package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class AuthDtos {
    private AuthDtos() {}

    public static class RegisterRequest {
        @SerializedName("email") public String email;
        @SerializedName("password") public String password;
        @SerializedName("username") public String username;
        @SerializedName("displayName") public String displayName;

        public RegisterRequest(String email, String password, String username, String displayName) {
            this.email = email;
            this.password = password;
            this.username = username;
            this.displayName = displayName;
        }
    }

    public static class LoginRequest {
        @SerializedName("identifier") public String identifier;
        @SerializedName("password") public String password;

        public LoginRequest(String identifier, String password) {
            this.identifier = identifier;
            this.password = password;
        }
    }

    public static class ChangePasswordRequest {
        @SerializedName("currentPassword") public String currentPassword;
        @SerializedName("newPassword") public String newPassword;
        public ChangePasswordRequest(String currentPassword, String newPassword) {
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
        }
    }

    public static class SendOtpRequest {
        @SerializedName("phone") public String phone;
        public SendOtpRequest(String phone) { this.phone = phone; }
    }

    public static class VerifyOtpRequest {
        @SerializedName("phone") public String phone;
        @SerializedName("code") public String code;
        @SerializedName("username") public String username;

        public VerifyOtpRequest(String phone, String code, String username) {
            this.phone = phone;
            this.code = code;
            this.username = username;
        }
    }

    public static class GuestLoginRequest {
        @SerializedName("deviceId") public String deviceId;
        @SerializedName("displayName") public String displayName;

        public GuestLoginRequest(String deviceId, String displayName) {
            this.deviceId = deviceId;
            this.displayName = displayName;
        }
    }

    public static class SocialLoginRequest {
        @SerializedName("provider") public String provider;
        @SerializedName("token") public String token;
        @SerializedName("email") public String email;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("providerUserId") public String providerUserId;

        public SocialLoginRequest(String provider, String token) {
            this.provider = provider;
            this.token = token;
        }
    }

    public static class RefreshTokenRequest {
        @SerializedName("refreshToken") public String refreshToken;
        public RefreshTokenRequest(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class AuthResult {
        @SerializedName("accessToken") public String accessToken;
        @SerializedName("refreshToken") public String refreshToken;
        @SerializedName("tokenType") public String tokenType;
        @SerializedName("user") public UserDto user;
    }

    public static class OtpSentResult {
        @SerializedName("sent") public boolean sent;
        @SerializedName("expiresIn") public int expiresIn;
        @SerializedName("debugCode") public String debugCode;
    }

    public static class UserDto {
        @SerializedName("id") public String id;
        @SerializedName("email") public String email;
        @SerializedName("phone") public String phone;
        @SerializedName("username") public String username;
        /** Stable numeric app ID — not Google / store account name. */
        @SerializedName("publicId") public String publicId;
        @SerializedName("displayName") public String displayName;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("coverUrl") public String coverUrl;
        @SerializedName("levelBadgeUrl") public String levelBadgeUrl;
        @SerializedName("vipBadgeUrl") public String vipBadgeUrl;
        @SerializedName("hostBadgeUrl") public String hostBadgeUrl;
        /** Cosmetic meta JSON for worn host badge: offsetX/offsetY/scale/avatarScale */
        @SerializedName("hostBadgeMeta") public java.util.Map<String, Object> hostBadgeMeta;
        @SerializedName("entryEffectUrl") public String entryEffectUrl;
        @SerializedName("entryAnimationUrl") public String entryAnimationUrl;
        @SerializedName("roomCardUrl") public String roomCardUrl;
        @SerializedName("bio") public String bio;
        @SerializedName("gender") public String gender;
        @SerializedName("level") public int level;
        @SerializedName("experience") public long experience;
        @SerializedName("isGuest") public boolean isGuest;
        @SerializedName("isAdmin") public boolean isAdmin;
        @SerializedName("followersCount") public int followersCount;
        @SerializedName("followingCount") public int followingCount;
        @SerializedName("friendsCount") public int friendsCount;
        @SerializedName("vipLevel") public int vipLevel;
        @SerializedName("charmScore") public long charmScore;
        @SerializedName("wealthScore") public long wealthScore;
        @SerializedName("popularityScore") public long popularityScore;
        @SerializedName("popularityLevel") public int popularityLevel;
        @SerializedName("wealthLevel") public int wealthLevel;
        @SerializedName("totalReceivedDiamonds") public long totalReceivedDiamonds;
        @SerializedName("totalSentCoins") public long totalSentCoins;
        @SerializedName("country") public String country;
        /** ISO time of last country change (server). */
        @SerializedName("countryChangedAt") public String countryChangedAt;
        /** ISO time when next country change is allowed (null = available). */
        @SerializedName("countryChangeAvailableAt") public String countryChangeAvailableAt;
        @SerializedName("birthday") public String birthday;
        @SerializedName("city") public String city;
        @SerializedName("albumUrls") public java.util.List<String> albumUrls;
        @SerializedName("showOnlineStatus") public Boolean showOnlineStatus;
        @SerializedName("allowDmFromStrangers") public Boolean allowDmFromStrangers;
        @SerializedName("dmGiftGateEnabled") public Boolean dmGiftGateEnabled;
        @SerializedName("dmRequiredGiftId") public String dmRequiredGiftId;
        @SerializedName("lastSeenAt") public String lastSeenAt;
        @SerializedName("isOnline") public Boolean isOnline;
        @SerializedName("genderVerified") public boolean genderVerified;
        @SerializedName("genderVerificationStatus") public String genderVerificationStatus;
        @SerializedName("createdAt") public String createdAt;
        /** Account age ≤ 24h — show Hi / اليوم الأول. */
        @SerializedName("isFirstDay") public boolean isFirstDay;
        @SerializedName("showHiBadge") public boolean showHiBadge;
        @SerializedName("isNewUser") public boolean isNewUser;
        @SerializedName("isNewMale") public boolean isNewMale;
        /** True when the authenticated viewer follows this user. */
        @SerializedName("isFollowing") public Boolean isFollowing;

        /** App-facing ID: numeric publicId, never the login/store username. */
        public String displayPublicId() {
            if (publicId != null && !publicId.trim().isEmpty()) return publicId.trim();
            return "";
        }
    }
}
