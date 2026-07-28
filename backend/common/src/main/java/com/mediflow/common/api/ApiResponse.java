package com.mediflow.common.api;

import java.time.Instant;
import java.util.List;

/**
 * Standard response envelope shared by every MediFlow service.
 * See docs/ai/05-api-conventions.md.
 *
 * @param <T> the payload type
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp,
        String correlationId
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, Instant.now(), correlationId);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now(), null);
    }

    public static <T> ApiResponse<T> fail(ApiError error, String correlationId) {
        return new ApiResponse<>(false, null, error, Instant.now(), correlationId);
    }

    /** Machine-readable error body. */
    public record ApiError(
            String code,
            String message,
            List<ErrorDetail> details
    ) {
        public static ApiError of(String code, String message) {
            return new ApiError(code, message, List.of());
        }
    }

    /** Per-field validation detail. */
    public record ErrorDetail(String field, String message) {
    }
}
