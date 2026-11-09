package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.StoriesDtos;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface StoriesApi {
    @GET("stories/config")
    Call<ApiResponse<StoriesDtos.StoriesConfigDto>> config();

    @GET("stories/feed")
    Call<ApiResponse<List<StoriesDtos.StoryDto>>> feed();

    @POST("stories")
    Call<ApiResponse<StoriesDtos.StoryDto>> create(@Body StoriesDtos.CreateStoryDto body);

    @POST("stories/{id}/view")
    Call<ApiResponse<StoriesDtos.ViewResult>> view(@Path("id") String id);

    @DELETE("stories/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") String id);
}
