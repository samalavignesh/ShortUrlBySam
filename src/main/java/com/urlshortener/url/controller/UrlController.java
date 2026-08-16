package com.urlshortener.url.controller;

import com.urlshortener.dto.request.ShortenUrlRequest;
import com.urlshortener.dto.response.ApiResponse;
import com.urlshortener.dto.response.UrlAnalyticsResponse;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.url.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * =====================================================
 * URL CONTROLLER
 * =====================================================
 *
 * This is the REST API entry point for ALL URL shortening operations.
 * It's the CORE of the application — everything a URL shortener does
 * is exposed through this controller.
 *
 * ENDPOINTS:
 * ──────────
 * POST   /api/urls/shorten              → Create a shortened URL (authenticated)
 * GET    /api/urls                      → List user's URLs (authenticated)
 * GET    /api/urls/{shortCode}/analytics → Get click analytics (authenticated)
 * DELETE /api/urls/{shortCode}          → Deactivate a URL (authenticated)
 * GET    /{shortCode}                   → Redirect to original URL (public)
 *
 * WHERE DOES THIS FIT IN THE ARCHITECTURE?
 * ─────────────────────────────────────────
 *
 * ┌─────────┐     HTTP       ┌────────────────┐    Business    ┌─────────────┐
 * │ Client  │ ─────────────→ │ UrlController  │ ────────────→ │ UrlService  │
 * │(Browser,│  JSON Request  │  (this class)  │    Logic      │             │
 * │ Mobile, │                │                │               │ shortenUrl()│
 * │ Postman)│ ←───────────── │ Handles HTTP   │ ←──────────── │ redirect()  │
 * └─────────┘  JSON Response │ concerns ONLY  │  Returns DTO  │ analytics() │
 *              or 302 redirect└───────────────┘               └─────────────┘
 *
 * TWO TYPES OF ENDPOINTS IN THIS CONTROLLER:
 * ───────────────────────────────────────────
 *
 * 1. API ENDPOINTS (/api/urls/**)
 *    → Return JSON responses wrapped in ApiResponse
 *    → Require JWT authentication (enforced by SecurityConfig)
 *    → Used by the frontend application
 *
 * 2. REDIRECT ENDPOINT (/{shortCode})
 *    → Returns HTTP 302 redirect (not JSON!)
 *    → Public — no authentication required
 *    → Used when someone clicks a short URL
 *
 * WHY ARE THESE IN THE SAME CONTROLLER?
 * ──────────────────────────────────────
 * The redirect endpoint (/{shortCode}) could be in a separate controller,
 * but it's closely related to URL operations and shares the same service.
 * Keeping it here makes the codebase easier to navigate — all URL-related
 * endpoints are in one place.
 *
 * CONTROLLER RESPONSIBILITIES (and what it does NOT do):
 * ──────────────────────────────────────────────────────
 *
 * ✅ DOES:
 *   - Receive HTTP requests and deserialize JSON to DTOs
 *   - Extract the authenticated username from the JWT/SecurityContext
 *   - Extract request metadata (IP address, User-Agent, Referer)
 *   - Trigger input validation (@Valid)
 *   - Call the appropriate service method
 *   - Wrap the result in ApiResponse (for API endpoints)
 *   - Issue HTTP 302 redirects (for the redirect endpoint)
 *   - Set the correct HTTP status code
 *
 * ❌ DOES NOT:
 *   - Contain business logic (that's UrlService's job)
 *   - Access the database directly (that's Repository's job)
 *   - Handle exceptions (that's GlobalExceptionHandler's job)
 *   - Generate short codes or track clicks (that's Service's job)
 *
 * @RestController → Combines @Controller + @ResponseBody.
 *                  All methods return data (JSON) or ResponseEntity directly.
 *
 * @RequiredArgsConstructor → Lombok generates constructor for the final
 *                            UrlService field. Spring injects it automatically.
 */
@RestController
@RequiredArgsConstructor
public class UrlController {

    /*
     * UrlService — our ONLY dependency. The controller delegates
     * ALL business logic to this service.
     *
     * Note: Unlike AuthController which uses @RequestMapping("/api/auth")
     * as a base path, we DON'T set a class-level @RequestMapping here.
     *
     * WHY?
     * → Because the redirect endpoint is at /{shortCode} (root level),
     *   while all other endpoints are at /api/urls/**. These two
     *   path patterns don't share a common prefix, so we set paths
     *   individually on each method.
     */
    private final UrlService urlService;

    // ================================================================
    // API ENDPOINTS (require authentication)
    // ================================================================

    /*
     * ===== SHORTEN A URL =====
     *
     * POST /api/urls/shorten
     *
     * Creates a new shortened URL for the authenticated user.
     *
     * REQUEST:
     * ────────
     * POST /api/urls/shorten
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     * Content-Type: application/json
     *
     * {
     *   "originalUrl": "https://www.example.com/very/long/path?param=value",
     *   "customCode": "my-link",      ← optional
     *   "expiresAt": "2027-01-01T00:00:00"  ← optional
     * }
     *
     * SUCCESS RESPONSE (201 Created):
     * ────────────────────────────────
     * {
     *   "success": true,
     *   "message": "URL shortened successfully",
     *   "data": {
     *     "id": 1,
     *     "shortCode": "my-link",
     *     "shortUrl": "http://localhost:8080/my-link",
     *     "originalUrl": "https://www.example.com/very/long/path?param=value",
     *     "clickCount": 0,
     *     "isActive": true,
     *     "createdAt": "2026-08-10T14:30:00",
     *     "expiresAt": "2027-01-01T00:00:00",
     *     "username": "john_doe"
     *   },
     *   "timestamp": "2026-08-10T14:30:00"
     * }
     *
     * ERROR RESPONSES:
     * ────────────────
     * 400 Bad Request  → Invalid URL or validation failed
     * 401 Unauthorized → Missing or invalid JWT token
     * 409 Conflict     → Custom short code already taken
     *
     * ANNOTATIONS EXPLAINED:
     * ──────────────────────
     *
     * @PostMapping("/api/urls/shorten")
     *   → Maps HTTP POST requests to this method.
     *     POST is correct because we're CREATING a new resource.
     *
     * @Valid @RequestBody ShortenUrlRequest
     *   → Deserializes the JSON body and validates @NotBlank, @URL, etc.
     *     If validation fails → 400 Bad Request (handled by GlobalExceptionHandler).
     *
     * Authentication authentication
     *   → Spring Security automatically injects the current user's
     *     Authentication object. This is populated by our JwtAuthenticationFilter.
     *     authentication.getName() returns the username from the JWT token.
     *
     *     WHY Authentication INSTEAD OF @RequestHeader("Authorization")?
     *     → We don't parse the JWT manually. The JwtAuthenticationFilter
     *       already did that work and stored the result in the SecurityContext.
     *       We just retrieve it — cleaner and more secure.
     *
     * WHY RETURN HTTP 201 (Created) INSTEAD OF 200 (OK)?
     * ───────────────────────────────────────────────────
     * Same reasoning as AuthController's register endpoint:
     * We're CREATING a new resource (a shortened URL), so 201 is
     * the semantically correct HTTP status.
     */
    @PostMapping("/api/urls/shorten")
    public ResponseEntity<ApiResponse<UrlResponse>> shortenUrl(
            @Valid @RequestBody ShortenUrlRequest request,
            Authentication authentication) {

        /*
         * authentication.getName() returns the username stored
         * in the JWT token's "sub" (subject) claim.
         *
         * The flow:
         * JWT token → JwtAuthenticationFilter extracts username →
         * Sets SecurityContext → Spring injects Authentication here →
         * We call getName() to get the username string.
         */
        UrlResponse urlResponse = urlService.shortenUrl(request, authentication.getName());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("URL shortened successfully", urlResponse));
    }

    /*
     * ===== LIST USER'S URLS =====
     *
     * GET /api/urls
     *
     * Returns all URLs belonging to the authenticated user.
     * Used for the user's dashboard — "Your Shortened URLs".
     *
     * REQUEST:
     * ────────
     * GET /api/urls
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *
     * SUCCESS RESPONSE (200 OK):
     * ──────────────────────────
     * {
     *   "success": true,
     *   "message": "URLs retrieved successfully",
     *   "data": [
     *     {
     *       "id": 1,
     *       "shortCode": "abc123",
     *       "shortUrl": "http://localhost:8080/abc123",
     *       "originalUrl": "https://www.example.com/page1",
     *       "clickCount": 42,
     *       "isActive": true,
     *       "createdAt": "2026-08-08T10:00:00",
     *       "expiresAt": null,
     *       "username": "john_doe"
     *     },
     *     {
     *       "id": 2,
     *       "shortCode": "xyz789",
     *       "shortUrl": "http://localhost:8080/xyz789",
     *       "originalUrl": "https://www.example.com/page2",
     *       "clickCount": 7,
     *       "isActive": true,
     *       "createdAt": "2026-08-09T15:00:00",
     *       "expiresAt": "2027-01-01T00:00:00",
     *       "username": "john_doe"
     *     }
     *   ],
     *   "timestamp": "2026-08-10T14:30:00"
     * }
     *
     * WHY 200 (OK)?
     * → We're READING data, not creating anything. 200 is correct.
     *
     * NOTE ON PAGINATION:
     * In a production app, this should support pagination:
     * GET /api/urls?page=0&size=20&sort=createdAt,desc
     * For now, we return ALL URLs. This is fine for a learning project
     * but would be a problem with thousands of URLs.
     */
    @GetMapping("/api/urls")
    public ResponseEntity<ApiResponse<List<UrlResponse>>> getUserUrls(
            Authentication authentication) {

        List<UrlResponse> urls = urlService.getUserUrls(authentication.getName());

        return ResponseEntity
                .ok(ApiResponse.success("URLs retrieved successfully", urls));
    }

    /*
     * ===== GET URL ANALYTICS =====
     *
     * GET /api/urls/{shortCode}/analytics
     *
     * Returns comprehensive click analytics for a specific URL.
     * Only the URL owner can view analytics (ownership check in service).
     *
     * REQUEST:
     * ────────
     * GET /api/urls/abc123/analytics
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *
     * SUCCESS RESPONSE (200 OK):
     * ──────────────────────────
     * {
     *   "success": true,
     *   "message": "Analytics retrieved successfully",
     *   "data": {
     *     "shortCode": "abc123",
     *     "originalUrl": "https://www.example.com/page",
     *     "totalClicks": 42,
     *     "createdAt": "2026-08-08T10:00:00",
     *     "clickEvents": [
     *       {
     *         "id": 100,
     *         "clickedAt": "2026-08-10T14:30:00",
     *         "ipAddress": "192.168.1.100",
     *         "userAgent": "Mozilla/5.0...",
     *         "referer": "https://twitter.com"
     *       }
     *     ]
     *   },
     *   "timestamp": "2026-08-10T14:30:00"
     * }
     *
     * ERROR RESPONSES:
     * ────────────────
     * 401 Unauthorized → Missing or invalid JWT token
     * 403 Forbidden    → User doesn't own this URL
     * 404 Not Found    → Short code doesn't exist
     *
     * @PathVariable
     *   → Extracts {shortCode} from the URL path.
     *     GET /api/urls/abc123/analytics → shortCode = "abc123"
     */
    @GetMapping("/api/urls/{shortCode}/analytics")
    public ResponseEntity<ApiResponse<UrlAnalyticsResponse>> getUrlAnalytics(
            @PathVariable String shortCode,
            Authentication authentication) {

        UrlAnalyticsResponse analytics = urlService.getUrlAnalytics(
                shortCode, authentication.getName());

        return ResponseEntity
                .ok(ApiResponse.success("Analytics retrieved successfully", analytics));
    }

    /*
     * ===== DEACTIVATE A URL (Soft Delete) =====
     *
     * DELETE /api/urls/{shortCode}
     *
     * Soft-deletes a URL by setting isActive = false.
     * Only the URL owner can deactivate it (ownership check in service).
     *
     * REQUEST:
     * ────────
     * DELETE /api/urls/abc123
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     *
     * SUCCESS RESPONSE (200 OK):
     * ──────────────────────────
     * {
     *   "success": true,
     *   "message": "URL deactivated successfully",
     *   "timestamp": "2026-08-10T14:30:00"
     * }
     *
     * ERROR RESPONSES:
     * ────────────────
     * 401 Unauthorized → Missing or invalid JWT token
     * 403 Forbidden    → User doesn't own this URL
     * 404 Not Found    → Short code doesn't exist
     *
     * WHY "DEACTIVATE" INSTEAD OF "DELETE"?
     * ─────────────────────────────────────
     * We use the HTTP DELETE method (which is semantically correct —
     * the client wants to "remove" the URL), but the service performs
     * a SOFT DELETE (sets isActive = false). The URL still exists in
     * the database with all its analytics data.
     *
     * This is a common pattern in enterprise applications:
     * - HTTP DELETE = "Make this resource no longer accessible"
     * - Implementation = soft delete (preserve data)
     *
     * WHY RETURN 200 INSTEAD OF 204 (No Content)?
     * ─────────────────────────────────────────────
     * 204 means "success, no response body." Some APIs prefer this
     * for DELETE operations. However, we return 200 with an ApiResponse
     * so the client gets a confirmation message. This is consistent
     * with our standard response wrapper pattern.
     */
    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<ApiResponse<Void>> deactivateUrl(
            @PathVariable String shortCode,
            Authentication authentication) {

        urlService.deactivateUrl(shortCode, authentication.getName());

        return ResponseEntity
                .ok(ApiResponse.success("URL deactivated successfully"));
    }

    // ================================================================
    // PUBLIC ENDPOINT (no authentication required)
    // ================================================================

    /*
     * ===== REDIRECT — The Core URL Shortener Feature =====
     *
     * GET /{shortCode}
     *
     * This is THE most important endpoint in the entire application.
     * When someone clicks a short URL like http://localhost:8080/abc123,
     * this endpoint:
     *   1. Looks up the original URL
     *   2. Records the click (IP, browser, referer)
     *   3. Sends an HTTP 302 redirect to the original URL
     *
     * REQUEST:
     * ────────
     * GET /abc123
     * (No Authorization header needed — this is PUBLIC)
     *
     * RESPONSE (302 Found):
     * ─────────────────────
     * HTTP/1.1 302 Found
     * Location: https://www.example.com/very/long/path?param=value
     *
     * The browser automatically navigates to the Location URL.
     * The user never sees this response — they're instantly redirected.
     *
     * ERROR RESPONSES:
     * ────────────────
     * 404 Not Found → Short code doesn't exist or URL is deactivated
     * 410 Gone      → URL has expired
     *
     * WHY HTTP 302 INSTEAD OF 301?
     * ────────────────────────────
     * ┌─────────┬───────────────────────────────────────────────────────┐
     * │ Status  │ Behavior                                             │
     * ├─────────┼───────────────────────────────────────────────────────┤
     * │ 301     │ "Moved PERMANENTLY" — Browser CACHES the redirect.   │
     * │         │ Future clicks skip our server entirely.              │
     * │         │ ❌ We lose click tracking!                           │
     * │         │ ❌ Can't change the destination later!               │
     * ├─────────┼───────────────────────────────────────────────────────┤
     * │ 302     │ "Found" (Temporary) — Browser does NOT cache.        │
     * │         │ Every click comes through our server.                │
     * │         │ ✅ Click tracking works perfectly!                   │
     * │         │ ✅ Can change the destination anytime!               │
     * └─────────┴───────────────────────────────────────────────────────┘
     *
     * For a URL shortener, 302 is the correct choice because we WANT
     * every click to hit our server for analytics tracking.
     *
     * WHY THIS ENDPOINT IS AT THE ROOT LEVEL:
     * ────────────────────────────────────────
     * Short URLs must be as short as possible:
     *   ✅ http://localhost:8080/abc123        (clean, short)
     *   ❌ http://localhost:8080/api/urls/abc123 (too long, defeats the purpose)
     *
     * The SecurityConfig allows GET /{shortCode} without authentication.
     *
     * HOW WE EXTRACT CLICK METADATA:
     * ──────────────────────────────
     * HttpServletRequest gives us access to:
     *
     * 1. IP Address (request.getRemoteAddr())
     *    → The visitor's IP address.
     *    CAVEAT: Behind a reverse proxy (Nginx, AWS ELB), this returns
     *    the proxy's IP, not the user's. In production, we'd check the
     *    X-Forwarded-For header first. Simplified here for clarity.
     *
     * 2. User-Agent (request.getHeader("User-Agent"))
     *    → Identifies the browser/device.
     *    Example: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)..."
     *    Used for device/browser analytics breakdowns.
     *
     * 3. Referer (request.getHeader("Referer"))
     *    → The page that linked to our short URL.
     *    Example: "https://twitter.com/post/123" → Traffic from Twitter.
     *    Note: "Referer" is intentionally misspelled — it's a typo from
     *    the original HTTP specification (RFC 2616) that's now permanent.
     *    Can be null if the user typed the URL directly.
     */
    @GetMapping("/{shortCode:[A-Za-z0-9]{3,10}}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        /*
         * Extract click metadata from the HTTP request.
         *
         * These values are passed to UrlService.redirect() which
         * records them in a ClickEvent entity for analytics.
         */
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String referer = request.getHeader(HttpHeaders.REFERER);

        /*
         * Call the service to:
         * 1. Look up the original URL
         * 2. Verify the URL is active and not expired
         * 3. Increment the click count (atomic)
         * 4. Save a ClickEvent record
         * 5. Return the original URL string
         */
        String originalUrl = urlService.redirect(shortCode, ipAddress, userAgent, referer);

        /*
         * Build the HTTP 302 redirect response.
         *
         * ResponseEntity.status(HttpStatus.FOUND) → HTTP 302
         * .location(URI.create(originalUrl)) → Sets the "Location" header
         * .build() → No response body (the browser follows the Location)
         *
         * The browser receives:
         *   HTTP/1.1 302 Found
         *   Location: https://www.example.com/original/page
         *
         * And automatically navigates to that URL.
         */
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
