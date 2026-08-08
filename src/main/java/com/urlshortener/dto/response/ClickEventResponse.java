package com.urlshortener.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * =====================================================
 * CLICK EVENT RESPONSE DTO
 * =====================================================
 *
 * Represents a single click event in the analytics data.
 * Returned as part of the analytics response (inside UrlAnalyticsResponse).
 *
 * RESPONSE JSON EXAMPLE:
 * {
 *   "id": 1,
 *   "clickedAt": "2026-08-08T14:30:00",
 *   "ipAddress": "192.168.1.100",
 *   "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
 *   "referer": "https://twitter.com/user/status/123456"
 * }
 *
 * NOTE: No "url" or "urlId" field here.
 * Why? Because this DTO is always returned INSIDE UrlAnalyticsResponse,
 * which already identifies which URL these clicks belong to.
 * Including the URL info again would be redundant.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEventResponse {

    private Long id;

    /* When the click happened */
    private LocalDateTime clickedAt;

    /* Visitor's IP address (for geographic analysis) */
    private String ipAddress;

    /* Browser/device identification string */
    private String userAgent;

    /* The website the visitor came from (can be null) */
    private String referer;
}
