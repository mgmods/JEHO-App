package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Response;
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

    static String errorMessage(Response<?> response, String fallback) {
        int code = response != null ? response.code() : 0;
        try {
            if (response != null && response.errorBody() != null) {
                String raw = response.errorBody().string();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"message\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(raw);
                if (m.find()) return m.group(1);
                m = java.util.regex.Pattern
                        .compile("\"error\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(raw);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return code > 0 ? fallback + " (" + code + ")" : fallback;
    }

    class UploadResult {
        @com.google.gson.annotations.SerializedName("url") public String url;
        @com.google.gson.annotations.SerializedName("filename") public String filename;
    }

    class DeleteResult {
        @com.google.gson.annotations.SerializedName("deleted") public boolean deleted;
    }
}
