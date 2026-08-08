package com.urlshortener.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * =====================================================
 * URL RESPONSE DTO
 * =====================================================
 *
 * Returned when a URL is created, retrieved, or listed.
 * This is what the client sees — a clean representation of a URL.
 *
 * RESPONSE JSON EXAMPLE:
 * {
 *   "id": 1,
 *   "shortCode": "abc123",
 *   "shortUrl": "http://localhost:8080/abc123",
 *   "originalUrl": "https://www.example.com/very/long/path?param=value",
 *   "clickCount": 42,
 *   "isActive": true,
 *   "createdAt": "2026-08-08T14:30:00",
 *   "expiresAt": null,
 *   "username": "john_doe"
 * }
 *
 * WHY IS THIS DIFFERENT FROM THE URL ENTITY?
 * ───────────────────────────────────────────
 * The Url entity has:
 *   - A User OBJECT (full nested entity with password hash!)
 *   - A List<ClickEvent> (potentially millions of records!)
 *   - Internal fields like updatedAt (not useful for client)
 *
 * This DTO has:
 *   - "username" (just the string, not the full User object)
 *   - "shortUrl" (the FULL short URL, computed by the service)
 *   - No click events list (that's a separate analytics endpoint)
 *   - Only fields the CLIENT actually needs
 *
 * ENTITY vs DTO COMPARISON:
 * ┌─────────────────────┬──────────────────────────────┐
 * │ Url Entity          │ UrlResponse DTO              │
 * ├─────────────────────┼──────────────────────────────┤
 * │ User user (object)  │ String username              │
 * │ shortCode           │ shortCode + shortUrl (full)  │
 * │ List<ClickEvent>    │ (not included)               │
 * │ updatedAt           │ (not included)               │
 * │ ─                   │ shortUrl (computed field)     │
 * └─────────────────────┴──────────────────────────────┘
 *
 * This prevents:
 * 1. SECURITY LEAK: User's password hash never reaches the client
 * 2. PERFORMANCE: Millions of click events aren't serialized
 * 3. CIRCULAR REFERENCE: User → URLs → User → URLs → ... (infinite loop!)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlResponse {

    /* Database ID — useful for update/delete operations */
    private Long id;

    /* The short code part: "abc123" */
    private String shortCode;

    /*
     * The FULL shortened URL: "http://localhost:8080/abc123"
     *
     * This is a COMPUTED field — it doesn't exist in the database.
     * The service layer constructs it by combining:
     *   base URL (from config) + "/" + shortCode
     *
     * Why compute it server-side?
     * - The base URL changes between environments
     *   (localhost in dev, yourdomain.com in prod)
     * - Client doesn't need to know the base URL
     * - Guarantees consistent URL format
     */
    private String shortUrl;

    /* The original long URL this short code redirects to */
    private String originalUrl;

    /* Total number of times this URL has been clicked */
    private Long clickCount;

    /* Whether this URL is active (false = soft-deleted) */
    private boolean isActive;

    /* When the short URL was created */
    private LocalDateTime createdAt;

    /* When this URL expires (null = never expires) */
    private LocalDateTime expiresAt;

    /* The username of the owner (NOT the full User object!) */
    private String username;
}
