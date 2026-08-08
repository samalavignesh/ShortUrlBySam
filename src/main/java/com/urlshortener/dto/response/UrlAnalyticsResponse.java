package com.urlshortener.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================
 * URL ANALYTICS RESPONSE DTO
 * =====================================================
 *
 * A comprehensive analytics summary for a single URL.
 * This is the main response for the analytics endpoint.
 *
 * It combines:
 * - URL metadata (short code, original URL, creation date)
 * - Aggregate statistics (total clicks)
 * - Detailed click history (list of individual click events)
 *
 * RESPONSE JSON EXAMPLE:
 * {
 *   "shortCode": "abc123",
 *   "originalUrl": "https://www.example.com/page",
 *   "totalClicks": 42,
 *   "createdAt": "2026-08-08T10:00:00",
 *   "clickEvents": [
 *     {
 *       "id": 100,
 *       "clickedAt": "2026-08-08T14:30:00",
 *       "ipAddress": "192.168.1.100",
 *       "userAgent": "Mozilla/5.0...",
 *       "referer": "https://twitter.com"
 *     },
 *     {
 *       "id": 99,
 *       "clickedAt": "2026-08-08T13:15:00",
 *       "ipAddress": "10.0.0.50",
 *       "userAgent": "Mozilla/5.0 (iPhone;...)...",
 *       "referer": null
 *     }
 *   ]
 * }
 *
 * DESIGN DECISIONS:
 * ─────────────────
 * 1. totalClicks comes from the denormalized Url.clickCount field
 *    (fast read, no need to count all click events).
 *
 * 2. clickEvents list is sorted by clickedAt DESC (newest first).
 *    In production, this should be PAGINATED for URLs with
 *    thousands of clicks. For now, we return all of them.
 *
 * 3. We include URL metadata (shortCode, originalUrl) so the
 *    response is self-contained — the client doesn't need to
 *    cross-reference with another API call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlAnalyticsResponse {

    /* The short code — identifies which URL these analytics are for */
    private String shortCode;

    /* The original URL — for display context */
    private String originalUrl;

    /* Total click count (from denormalized Url.clickCount) */
    private Long totalClicks;

    /* When the URL was created — for "clicks since creation" context */
    private LocalDateTime createdAt;

    /*
     * List of individual click events, newest first.
     *
     * Each element is a ClickEventResponse DTO (not the raw entity).
     * This list could be very large for popular URLs, so in a future
     * improvement, we'd add pagination parameters (page, size).
     */
    private List<ClickEventResponse> clickEvents;
}
