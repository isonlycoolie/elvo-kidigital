package com.elvo.identity.exception;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, Object> details,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Map.of(), Instant.now());
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, code, message, null, details, Instant.now());
    }
}
