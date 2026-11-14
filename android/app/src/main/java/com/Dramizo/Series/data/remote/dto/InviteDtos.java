package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class InviteDtos {
    private InviteDtos() {}

    public static class InviteMeDto {
        @SerializedName("code") public String code;
        @SerializedName("invitedCount") public int invitedCount;
        @SerializedName("totalCoins") public long totalCoins;
        @SerializedName("canBind") public boolean canBind;
        @SerializedName("invitedBy") public InvitedByDto invitedBy;
        @SerializedName("rewards") public InviteRewardsDto rewards;
    }

    public static class InvitedByDto {
        @SerializedName("id") public String id;
        @SerializedName("displayName") public String displayName;
        @SerializedName("publicId") public String publicId;
    }

    public static class InviteRewardsDto {
        @SerializedName("inviterCoins") public int inviterCoins;
        @SerializedName("inviteeCoins") public int inviteeCoins;
    }
}
