package com.urlshortener.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * =====================================================
 * API RESPONSE DTO — Generic Wrapper
 * =====================================================
 *
 * A UNIVERSAL wrapper that wraps ALL API responses in a
 * consistent structure. Every endpoint returns this format.
 *
 * WHY A WRAPPER?
 * ──────────────
 * Without a wrapper, different endpoints return different shapes:
 *
 *   GET  /api/urls       → [ {...}, {...} ]           (array)
 *   POST /api/urls       → { "shortCode": "abc123" }  (object)
 *   POST /api/auth/login → { "token": "eyJ..." }      (object)
 *   DELETE /api/urls/1   → (nothing? string? object?)
 *
 * The frontend has to handle each response differently. Messy!
 *
 * With a wrapper, EVERY response has the same structure:
 * {
 *   "success": true/false,
 *   "message": "Human-readable message",
 *   "data": { ... actual payload ... },
 *   "timestamp": "2026-08-08T14:30:00"
 * }
 *
 * The frontend can always check response.success first,
 * then access response.data for the payload. Clean and predictable!
 *
 * SUCCESS EXAMPLE:
 * {
 *   "success": true,
 *   "message": "URL shortened successfully",
 *   "data": {
 *     "shortCode": "abc123",
 *     "shortUrl": "http://localhost:8080/abc123",
 *     "originalUrl": "https://www.example.com/page"
 *   },
 *   "timestamp": "2026-08-08T14:30:00"
 * }
 *
 * ERROR EXAMPLE:
 * {
 *   "success": false,
 *   "message": "URL not found",
 *   "data": null,
 *   "timestamp": "2026-08-08T14:30:00"
 * }
 *
 * @JsonInclude(JsonInclude.Include.NON_NULL)
 *   → Tells Jackson (JSON serializer) to SKIP null fields.
 *     So if "data" is null (error case), the JSON won't include
 *     "data": null — it just won't have the "data" field at all.
 *     This makes error responses cleaner.
 *
 * <T> → GENERIC TYPE parameter.
 *     T can be any type: UrlResponse, AuthResponse, List<UrlResponse>, etc.
 *     This means ONE wrapper class works for ALL response types.
 *     ApiResponse<UrlResponse>  → data is a UrlResponse
 *     ApiResponse<List<UrlResponse>> → data is a list of UrlResponse
 *     ApiResponse<Void> → no data (success message only)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /* Whether the request was successful */
    private boolean success;

    /* Human-readable message describing the result */
    private String message;

    /*
     * The actual response payload — type varies per endpoint.
     * Generic type <T> makes this flexible:
     *   - ApiResponse<UrlResponse> → data is UrlResponse
     *   - ApiResponse<AuthResponse> → data is AuthResponse
     *   - ApiResponse<Void> → data is null
     */
    private T data;

    /* When this response was generated — useful for debugging */
    private LocalDateTime timestamp;

    /*
     * ===== STATIC FACTORY METHODS =====
     *
     * These are convenience methods to create ApiResponse instances.
     * They follow the "Static Factory Method" pattern (Effective Java, Item 1).
     *
     * Instead of:
     *   new ApiResponse<>(true, "Success", data, LocalDateTime.now())
     *
     * You write:
     *   ApiResponse.success("Success", data)
     *
     * Benefits:
     * 1. More readable — the method NAME describes what you're creating
     * 2. Less error-prone — can't accidentally set success=false for a success
     * 3. Encapsulates timestamp creation — always uses now()
     */

    /**
     * Create a SUCCESS response with a message and data payload.
     *
     * @param message Human-readable success message
     * @param data    The response payload
     * @param <T>     Type of the payload
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a SUCCESS response with a message only (no data payload).
     * Used for operations like DELETE where there's nothing to return.
     *
     * @param message Human-readable success message
     * @return ApiResponse with success=true and data=null
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an ERROR response with a message.
     *
     * @param message Human-readable error description
     * @return ApiResponse with success=false and data=null
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
