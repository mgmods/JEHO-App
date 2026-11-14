package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.InviteDtos;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface InviteApi {
    @GET("invites/me")
    Call<ApiResponse<InviteDtos.InviteMeDto>> me();

    @POST("invites/bind")
    Call<ApiResponse<InviteDtos.InviteMeDto>> bind(@Body Map<String, String> body);
}
