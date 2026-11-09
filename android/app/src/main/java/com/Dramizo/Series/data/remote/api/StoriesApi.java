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

    @GET("stories/{id}/viewers")
    Call<ApiResponse<StoriesDtos.ViewersResult>> viewers(@Path("id") String id);

    @POST("stories/{id}/view")
    Call<ApiResponse<StoriesDtos.ViewResult>> view(@Path("id") String id);

    @POST("stories/{id}/reaction")
    Call<ApiResponse<Object>> react(@Path("id") String id, @Body java.util.Map<String,String> body);

    @DELETE("stories/{id}")
    Call<ApiResponse<Void>> delete(@Path("id") String id);
}
