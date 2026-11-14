package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.EventDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface EventsApi {
    @GET("events")
    Call<ApiResponse<EventDtos.EventList>> list(@Query("tab") String tab);

    @GET("events/{id}")
    Call<ApiResponse<EventDtos.EventDto>> get(@Path("id") String id);

    @POST("events")
    Call<ApiResponse<EventDtos.EventDto>> create(@Body EventDtos.CreateEventRequest body);

    @retrofit2.http.PATCH("events/{id}")
    Call<ApiResponse<EventDtos.EventDto>> update(
            @Path("id") String id, @Body EventDtos.CreateEventRequest body);

    @DELETE("events/{id}")
    Call<ApiResponse<Object>> delete(@Path("id") String id);

    @POST("events/{id}/subscribe")
    Call<ApiResponse<EventDtos.EventDto>> subscribe(@Path("id") String id);

    @DELETE("events/{id}/subscribe")
    Call<ApiResponse<EventDtos.EventDto>> unsubscribe(@Path("id") String id);
}
