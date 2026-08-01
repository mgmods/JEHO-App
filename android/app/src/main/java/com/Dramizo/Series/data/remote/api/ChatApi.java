package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ChatApi {
    @GET("chat/conversations")
    Call<ApiResponse<ChatDtos.ConversationList>> conversations();

    @GET("chat/conversations/{id}")
    Call<ApiResponse<ChatDtos.ConversationDto>> conversation(@Path("id") String id);

    @GET("chat/conversations/{id}/messages")
    Call<ApiResponse<ChatDtos.MessageList>> messages(
            @Path("id") String id,
            @Query("page") int page,
            @Query("limit") int limit);

    @POST("chat/conversations/{id}/messages")
    Call<ApiResponse<ChatDtos.MessageDto>> send(@Path("id") String id, @Body ChatDtos.SendMessageRequest body);

    @POST("chat/conversations")
    Call<ApiResponse<ChatDtos.ConversationDto>> create(@Body java.util.Map<String, String> body);

    @PATCH("chat/messages/{id}")
    Call<ApiResponse<ChatDtos.MessageDto>> edit(@Path("id") String id, @Body java.util.Map<String, String> body);

    @POST("chat/messages/{id}/unsend")
    Call<ApiResponse<ChatDtos.MessageDto>> unsend(@Path("id") String id);

    @POST("chat/conversations/{id}/archive")
    Call<ApiResponse<Object>> archive(@Path("id") String id, @Body java.util.Map<String, Boolean> body);

    @POST("chat/conversations/{id}/delete")
    Call<ApiResponse<Object>> deleteConversation(@Path("id") String id);
}
