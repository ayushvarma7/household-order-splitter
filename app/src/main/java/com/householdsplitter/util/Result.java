package com.householdsplitter.util;

import androidx.annotation.Nullable;

/** Success with a value, or failure with a reason the UI can show inline. */
public final class Result<T> {

    private final T value;
    private final String error;

    private Result(T value, String error) {
        this.value = value;
        this.error = error;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(value, null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(null, error);
    }

    public boolean isOk() {
        return error == null;
    }

    @Nullable
    public T value() {
        return value;
    }

    @Nullable
    public String error() {
        return error;
    }
}
