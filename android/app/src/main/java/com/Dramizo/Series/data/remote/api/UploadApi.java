package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface UploadApi {
    @Multipart
    @POST("uploads")
    Call<ApiResponse<UploadResult>> upload(@Part MultipartBody.Part file);

    @DELETE("uploads/{filename}")
    Call<ApiResponse<DeleteResult>> delete(@Path("filename") String filename);

    class UploadResult {
        @com.google.gson.annotations.SerializedName("url") public String url;
        @com.google.gson.annotations.SerializedName("filename") public String filename;
    }

    class DeleteResult {
        @com.google.gson.annotations.SerializedName("deleted") public boolean deleted;
    }
}
