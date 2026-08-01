package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {
    @SerializedName("success")
    public boolean success;
    @SerializedName("data")
    public T data;
    @SerializedName("timestamp")
    public String timestamp;
    @SerializedName("message")
    public String message;
}
