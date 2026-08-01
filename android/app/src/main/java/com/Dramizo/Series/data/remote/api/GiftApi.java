package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface GiftApi {
    @GET("gifts")
    Call<ApiResponse<GiftDtos.GiftList>> list();

    @POST("gifts/send")
    Call<ApiResponse<GiftDtos.SendGiftResult>> send(@Body GiftDtos.SendGiftRequest body);

    @POST("gifts/send-all-mic")
    Call<ApiResponse<GiftDtos.SendGiftResult>> sendAllMic(@Body GiftDtos.SendAllMicRequest body);

    @GET("gifts/history")
    Call<ApiResponse<MiscDtos.ListResult<GiftDtos.GiftHistoryDto>>> history(@retrofit2.http.Query("page") int page);
}
