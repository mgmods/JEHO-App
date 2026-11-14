package com.Dramizo.Series.domain.model;

public class Result<T> {
    public final boolean success;
    public final T data;
    public final String error;

    private Result(boolean success, T data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> Result<T> ok(T data) { return new Result<>(true, data, null); }
    public static <T> Result<T> err(String error) { return new Result<>(false, null, error); }
}
