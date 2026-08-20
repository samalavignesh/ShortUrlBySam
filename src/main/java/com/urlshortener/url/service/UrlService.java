package com.urlshortener.url.service;

import com.urlshortener.dto.request.ShortenUrlRequest;
import com.urlshortener.dto.response.CachedUrl;
import com.urlshortener.dto.response.ClickEventResponse;
import com.urlshortener.dto.response.UrlAnalyticsResponse;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.analytics.messaging.ClickEventProducer;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.exception.DuplicateResourceException;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.exception.UnauthorizedAccessException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.analytics.messaging.ClickEventProducer;
import com.urlshortener.dto.event.ClickEventPayload;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * =====================================================
 * URL SERVICE
 * =====================================================
 *
 * This is the HEART of the application — the core business logic
 * for URL shortening, redirection, and analytics.
 *
 * WHAT THIS SERVICE DOES:
 * ───────────────────────
 * 1. SHORTEN → Takes a long URL, generates a short code, saves it
 * 2. REDIRECT → Looks up a short code, returns the original URL
 * 3. ANALYTICS → Returns click statistics for a URL
 * 4. LIST → Returns all URLs for a user
 * 5. DEACTIVATE → Soft-deletes a URL (sets isActive = false)
 *
 * WHERE DOES THIS FIT?
 * ────────────────────
 *
 * ┌─────────┐ ┌────────────────┐ ┌─────────────┐ ┌──────────────┐
 * │ Client │ ──→ │ UrlController │ ──→ │ UrlService │ ──→ │ UrlRepository│
 * │ │ │ │ │ (this class) │ │ ClickEventRep│
 * └─────────┘ └────────────────┘ └──────┬───────┘ └──────────────┘
 * │
 * ┌──────┴──────┐
 * │ │
 * Short Code Click Event
 * Generation Recording
 *
 * DESIGN DECISIONS:
 * ─────────────────
 *
 * 1. SHORT CODE GENERATION
 * We use SecureRandom (cryptographically strong) to generate
 * random alphanumeric codes. We check for collisions before saving.
 *
 * 2. ATOMIC CLICK COUNTING
 * We use UrlRepository.incrementClickCount() which runs:
 * UPDATE urls SET click_count = click_count + 1 WHERE id = ?
 * This prevents race conditions when multiple clicks happen simultaneously.
 *
 * 3. OWNERSHIP CHECKS
 * Users can only view/delete their OWN URLs. We verify ownership
 * before performing any operation. Admins bypass this check.
 *
 * 4. SOFT DELETE
 * URLs are never physically deleted — we set isActive = false.
 * This preserves analytics data and prevents short code reuse.
 *
 * @Service → Marks this as a Spring service bean with business logic.
 * @RequiredArgsConstructor → Lombok generates constructor for final fields.
 */
@Service
@RequiredArgsConstructor
public class UrlService {

    /*
     * ===== DEPENDENCIES =====
     */

    /* Database access for URL entities */
    private final UrlRepository urlRepository;

    /* Async producer for click events */
    private final ClickEventProducer clickEventProducer;

    /* Database access for click event entities (used for analytics) */
    private final ClickEventRepository clickEventRepository;

    /* Database access for user entities (to look up current user) */
    private final UserRepository userRepository;

    /*
     * Dedicated cache service — handles all Redis lookups and evictions
     * for URL entities. Lives in a SEPARATE Spring bean so that
     * 
     * @Cacheable / @CacheEvict AOP proxies are properly intercepted.
     * (Calling these from within this same class would bypass the proxy.)
     */
    private final UrlCacheService urlCacheService;

    /*
     * BASE URL — The domain/host used to construct full short URLs.
     *
     * @Value injects from application.properties:
     * app.base-url=http://localhost:8080
     *
     * In production, this would be your actual domain:
     * app.base-url=https://short.example.com
     *
     * We use this to build the full short URL:
     * base URL + "/" + short code = "http://localhost:8080/abc123"
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /*
     * SHORT CODE LENGTH — How many characters in the generated short code.
     *
     * 7 characters from a 62-char alphabet (a-z, A-Z, 0-9) gives us:
     * 62^7 = 3.52 trillion possible combinations
     *
     * That's more than enough for most URL shorteners.
     * For context, bit.ly has shortened about 50 billion links total.
     */
    private static final int SHORT_CODE_LENGTH = 7;

    /*
     * CHARACTER SET for short code generation.
     * Using alphanumeric characters (a-z, A-Z, 0-9) = 62 characters.
     *
     * We deliberately EXCLUDE:
     * - Special characters (!@#$%^&*) → URL encoding issues
     * - Confusing characters (0/O, l/1/I) → Optional, kept for simplicity
     */
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /*
     * SecureRandom — Cryptographically strong random number generator.
     *
     * WHY SecureRandom INSTEAD OF Random?
     * Random uses a predictable algorithm (LCG). If an attacker knows
     * the seed, they can predict ALL future short codes.
     * SecureRandom uses the OS's entropy source (/dev/urandom on Linux,
     * CryptGenRandom on Windows) — truly unpredictable.
     *
     * This prevents attackers from guessing short codes of other users.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ================================================================
    // PUBLIC METHODS
    // ================================================================

    /*
     * ===== SHORTEN A URL =====
     *
     * Takes a long URL and creates a shortened version.
     *
     * FLOW:
     * ┌──────────────────────────────────────────────────────────────────┐
     * │ 1. LOOK UP the authenticated user from the database │
     * │ 2. If custom code provided → CHECK if it's available │
     * │ 3. If no custom code → GENERATE a random unique code │
     * │ 4. PARSE optional expiration date │
     * │ 5. BUILD the Url entity │
     * │ 6. SAVE to database │
     * │ 7. CONVERT to UrlResponse and return │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * @param request The ShortenUrlRequest with originalUrl, optional customCode,
     * expiresAt
     * 
     * @param username The authenticated user's username (from JWT token)
     * 
     * @return UrlResponse with the shortened URL details
     *
     * @Transactional → Wraps the entire method in a database transaction.
     * If ANY step fails, ALL changes are rolled back.
     * This ensures we don't create a URL without a valid user,
     * or leave orphaned records if saving fails.
     */
    /*
     * @CacheEvict → When a user creates a new URL, their cached URL list
     * is now stale (missing the new URL). We evict the user's cached list
     * so the next getUserUrls() call fetches fresh data from the database.
     *
     * key = #username → Each user has their own cached list.
     * Example: CacheEvict removes key "urlListCache::john_doe"
     */
    @CacheEvict(value = "urlListCache", key = "#username")
    @Transactional
    public UrlResponse shortenUrl(ShortenUrlRequest request, String username) {

        /*
         * STEP 1: Look up the authenticated user.
         *
         * The username comes from the JWT token (extracted by the
         * JwtAuthenticationFilter). We need the User entity to set
         * the @ManyToOne relationship on the Url entity.
         *
         * This should never throw because the JWT filter already
         * verified the user exists. But defensive coding is good practice.
         */
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        /*
         * STEP 2 & 3: Determine the short code.
         *
         * Two paths:
         * A) Custom code provided → Validate and use it
         * B) No custom code → Generate a random one
         */
        String shortCode;

        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            /*
             * PATH A: Custom short code (vanity URL)
             *
             * User wants a specific code like "my-brand".
             * We must check if it's already taken.
             *
             * If taken → DuplicateResourceException → 409 Conflict
             * If available → Use it
             */
            if (urlRepository.existsByShortCode(request.getCustomCode())) {
                throw new DuplicateResourceException("Url", "shortCode", request.getCustomCode());
            }
            shortCode = request.getCustomCode();
        } else {
            /*
             * PATH B: Generate a random short code.
             *
             * We generate a random code and check for collisions.
             * If the code already exists, we generate a new one.
             *
             * Collision probability is extremely low:
             * With 62^6 = ~56 billion possibilities and even
             * 1 million existing URLs, the chance of collision
             * is ~0.002%. But we still check, because Murphy's Law.
             *
             * We use a do-while loop to guarantee at least one attempt.
             */
            shortCode = generateUniqueShortCode();
        }

        /*
         * STEP 4: Parse the optional expiration date.
         *
         * The expiresAt field comes as a String (ISO 8601 format)
         * from the request DTO. We parse it to LocalDateTime.
         *
         * If null or blank → URL never expires (expiresAt stays null).
         * If provided → Parse and set the expiration.
         *
         * We use LocalDateTime.parse() which expects ISO 8601:
         * "2027-01-01T00:00:00" → LocalDateTime(2027, 1, 1, 0, 0)
         */
        LocalDateTime expiresAt = null;
        if (request.getExpiresAt() != null && !request.getExpiresAt().isBlank()) {
            expiresAt = LocalDateTime.parse(request.getExpiresAt());
        }

        /*
         * STEP 5: Build the Url entity.
         */
        Url url = Url.builder()
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .user(user)
                .expiresAt(expiresAt)
                .build();
        // isActive defaults to true via @Builder.Default
        // clickCount defaults to 0L via @Builder.Default

        /*
         * STEP 6: Save to database.
         *
         * JPA generates:
         * INSERT INTO urls (short_code, original_url, user_id, is_active,
         * click_count, expires_at, created_at, updated_at)
         * VALUES ('abc123', 'https://...', 1, true, 0, null, NOW(), NOW())
         */
        Url savedUrl = urlRepository.save(url);

        /*
         * STEP 7: Convert to response DTO and return.
         */
        return mapToUrlResponse(savedUrl);
    }

    /*
     * ===== REDIRECT — Look Up Original URL by Short Code =====
     *
     * This is the MOST CRITICAL method in the entire application.
     * It's called every time someone clicks a short URL.
     *
     * FLOW:
     * ┌──────────────────────────────────────────────────────────────────┐
     * │ 1. FIND the URL by short code │
     * │ 2. CHECK if the URL is active (not soft-deleted) │
     * │ 3. CHECK if the URL has expired │
     * │ 4. INCREMENT the click count (atomic) │
     * │ 5. RECORD the click event (IP, user-agent, referer) │
     * │ 6. RETURN the original URL for redirect │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * PERFORMANCE IS CRITICAL HERE:
     * ─────────────────────────────
     * This method is on the HOT PATH — every single redirect goes through it.
     * A URL shortener's primary job is redirecting, so this must be FAST.
     *
     * Current performance characteristics:
     * Step 1: O(1) — short_code has a UNIQUE index
     * Step 4: O(1) — single UPDATE with WHERE clause on indexed PK
     * Step 5: O(1) — single INSERT
     *
     * Future optimization: Add Redis caching for popular URLs
     * to avoid hitting the database on every redirect.
     *
     * @param shortCode The short code from the URL path (e.g., "abc123")
     * 
     * @param ipAddress The visitor's IP address (from HttpServletRequest)
     * 
     * @param userAgent The browser's User-Agent header
     * 
     * @param referer The referring page URL (can be null)
     * 
     * @return The original long URL to redirect to
     *
     * @Transactional → Ensures both the click count increment AND
     * the click event insert happen atomically. If either fails,
     * both are rolled back.
     */
    /*
     * NOTE: We intentionally do NOT cache the redirect method.
     *
     * WHY NOT?
     * ────────
     * The redirect method has SIDE EFFECTS:
     * 1. It increments the click count
     * 2. It records a click event for analytics
     *
     * If we cached this, repeated clicks would return the cached URL
     * but SKIP the click tracking — making analytics inaccurate.
     *
     * Instead, we cache just the URL lookup in a separate method
     * (findUrlByShortCode) and keep the side effects outside the cache.
     */
    @Transactional
    public String redirect(String shortCode, String ipAddress,
            String userAgent, String referer) {

        /*
         * STEP 1: Find the URL by short code — via Redis cache.
         *
         * We call urlCacheService (a SEPARATE Spring bean) instead of
         * a private method on this class. This ensures the @Cacheable
         * AOP proxy is properly intercepted:
         *
         * Cache HIT → returns from Redis instantly (~0.5ms)
         * Cache MISS → queries PostgreSQL, stores in Redis, returns result
         *
         * If not found → ResourceNotFoundException → HTTP 404
         */
        CachedUrl cachedUrl = urlCacheService.findByShortCode(shortCode);

        /*
         * STEP 2: Check if the URL is active.
         *
         * Soft-deleted URLs should not redirect. The owner deactivated
         * this URL intentionally, so we treat it as "not found."
         */
        if (!cachedUrl.isActive()) {
            throw new ResourceNotFoundException("Url", "shortCode", shortCode);
        }

        /*
         * STEP 3: Check if the URL has expired.
         *
         * If expiresAt is set and the current time is past it,
         * the URL is no longer valid. We throw UrlExpiredException
         * which maps to HTTP 410 Gone.
         */
        if (cachedUrl.getExpiresAt() != null && LocalDateTime.now().isAfter(cachedUrl.getExpiresAt())) {
            throw new UrlExpiredException(shortCode);
        }

        /*
         * STEP 4: Asynchronously record the click event for analytics.
         *
         * Instead of synchronously blocking the redirect to write to PostgreSQL,
         * we push a payload to a Redis Stream. A background consumer will process
         * it to increment the click count and save the ClickEvent entity.
         */
        ClickEventPayload payload = ClickEventPayload.builder()
                .urlId(cachedUrl.getId())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .referer(referer)
                .build();

        clickEventProducer.publishEvent(payload);

        /*
         * STEP 6: Return the original URL.
         *
         * The controller will use this to send an HTTP 302 redirect
         * response, which tells the browser to navigate to this URL.
         */
        return cachedUrl.getOriginalUrl();
    }

    /*
     * ===== GET USER'S URLS =====
     *
     * Returns all URLs belonging to the authenticated user.
     *
     * Used for the user's dashboard — "Your Shortened URLs".
     *
     * @param username The authenticated user's username (from JWT)
     * 
     * @return List of UrlResponse DTOs
     *
     * @Transactional(readOnly = true) → Optimization hint.
     * Tells Spring this method only reads data, never writes.
     * Benefits:
     * - JPA skips dirty checking (faster)
     * - DB can use read-only optimizations
     * - Prevents accidental modifications
     */
    /*
     * @Cacheable → Caches the user's URL list in Redis.
     *
     * key = #username → Each user's list is cached separately.
     * Example key: "urlListCache::john_doe"
     *
     * First call: Hits DB, stores result in Redis, returns data
     * Second call: Returns directly from Redis (~1ms), skips DB entirely
     *
     * Cache is invalidated when the user creates or deactivates a URL
     * (via @CacheEvict on shortenUrl and deactivateUrl).
     */
    @Cacheable(value = "urlListCache", key = "#username")
    @Transactional(readOnly = true)
    public List<UrlResponse> getUserUrls(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        return urlRepository.findByUser(user).stream()
                .map(this::mapToUrlResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    /*
     * ===== GET URL ANALYTICS =====
     *
     * Returns comprehensive analytics for a specific URL.
     *
     * FLOW:
     * ┌──────────────────────────────────────────────────────────────────┐
     * │ 1. FIND the URL by short code │
     * │ 2. VERIFY the requesting user owns this URL │
     * │ 3. LOAD all click events for this URL │
     * │ 4. MAP click events to ClickEventResponse DTOs │
     * │ 5. BUILD and return the UrlAnalyticsResponse │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * @param shortCode The short code to get analytics for
     * 
     * @param username The authenticated user's username
     * 
     * @return UrlAnalyticsResponse with click details
     */
    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getUrlAnalytics(String shortCode, String username) {

        /*
         * STEP 1: Find the URL.
         */
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Url", "shortCode", shortCode));

        /*
         * STEP 2: Verify ownership.
         *
         * Users can only view analytics for their OWN URLs.
         * This prevents User A from seeing User B's click data.
         *
         * We compare the URL owner's username with the requesting user's
         * username. If they don't match → 403 Forbidden.
         */
        if (!url.getUser().getUsername().equals(username)) {
            throw new UnauthorizedAccessException(
                    "You can only view analytics for your own URLs");
        }

        /*
         * STEP 3 & 4: Load and map click events.
         *
         * findByUrlOrderByClickedAtDesc returns clicks newest-first.
         * We map each ClickEvent entity to a ClickEventResponse DTO.
         */
        List<ClickEventResponse> clickEvents = clickEventRepository
                .findByUrlOrderByClickedAtDesc(url)
                .stream()
                .map(this::mapToClickEventResponse)
                .toList();

        /*
         * STEP 5: Build the analytics response.
         *
         * totalClicks comes from the denormalized Url.clickCount
         * (fast read, no need to count all click events).
         */
        return UrlAnalyticsResponse.builder()
                .shortCode(url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .totalClicks(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .clickEvents(clickEvents)
                .build();
    }

    /*
     * ===== DEACTIVATE A URL (Soft Delete) =====
     *
     * Sets isActive = false instead of physically deleting.
     *
     * WHY SOFT DELETE?
     * ────────────────
     * 1. RECOVERY: Accidentally deactivated URLs can be restored
     * 2. ANALYTICS: Historical click data is preserved
     * 3. SHORT CODE REUSE: Prevents reusing codes that were
     * previously active (which would confuse cached links)
     * 4. AUDIT TRAIL: We can see which URLs existed and when
     *
     * @param shortCode The short code of the URL to deactivate
     * 
     * @param username The authenticated user's username
     */
    @Transactional
    public void deactivateUrl(String shortCode, String username) {

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Url", "shortCode", shortCode));

        /*
         * Verify ownership — users can only deactivate their own URLs.
         */
        if (!url.getUser().getUsername().equals(username)) {
            throw new UnauthorizedAccessException("You can only delete your own URLs");
        }

        /*
         * Set isActive to false and persist.
         */
        url.setActive(false);
        urlRepository.save(url);

        /*
         * Evict the URL from Redis cache via UrlCacheService.
         *
         * We call urlCacheService.evictUrl() (external bean) instead of
         * using @CacheEvict on this method. This ensures the eviction
         * goes through the AOP proxy — same reason we use UrlCacheService
         * for lookups.
         *
         * After eviction:
         * - Next redirect → cache MISS → DB query → isActive=false → 404
         * - User's dashboard will also show the URL as deactivated
         */
        urlCacheService.evictUrl(shortCode);
    }

    // ================================================================
    // PRIVATE HELPER METHODS
    // ================================================================

    /*
     * ===== GENERATE A UNIQUE SHORT CODE =====
     *
     * Generates random alphanumeric codes until we find one
     * that doesn't already exist in the database.
     *
     * ALGORITHM:
     * ──────────
     * 1. Generate a random 6-character string from CHARACTERS
     * 2. Check if it exists in the database
     * 3. If exists → generate again (collision)
     * 4. If not exists → return it
     *
     * COLLISION ANALYSIS:
     * ───────────────────
     * With 62^6 = ~56 billion possibilities:
     * - At 1 million URLs: collision chance ≈ 0.002%
     * - At 10 million URLs: collision chance ≈ 0.02%
     * - At 100 million URLs: collision chance ≈ 0.2%
     *
     * Even in the worst case, we just generate another code.
     * The do-while loop handles this automatically.
     *
     * SECURITY:
     * ─────────
     * We use SecureRandom (not Random) to prevent short code prediction.
     * If codes were predictable, an attacker could enumerate all URLs.
     */
    private String generateUniqueShortCode() {
        String shortCode;
        do {
            shortCode = generateRandomCode();
        } while (urlRepository.existsByShortCode(shortCode));
        return shortCode;
    }

    /*
     * Generates a random string of SHORT_CODE_LENGTH characters
     * from the CHARACTERS alphabet.
     *
     * Example output: "aB3xZ7", "Km9pQw", "7fHnLe"
     */
    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(randomIndex));
        }
        return sb.toString();
    }

    /*
     * ===== ENTITY → DTO MAPPING =====
     *
     * Converts a Url entity to a UrlResponse DTO.
     *
     * WHY A SEPARATE METHOD?
     * ──────────────────────
     * This mapping is used in multiple places (shortenUrl, getUserUrls),
     * so we extract it to a private method to avoid duplication (DRY).
     *
     * KEY TRANSFORMATIONS:
     * - Constructs the full shortUrl by combining baseUrl + shortCode
     * - Extracts username from the User relationship (avoids exposing
     * the full User entity with password hash)
     */
    private UrlResponse mapToUrlResponse(Url url) {
        return UrlResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .isActive(url.isActive())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .username(url.getUser().getUsername())
                .build();
    }

    /*
     * Converts a ClickEvent entity to a ClickEventResponse DTO.
     *
     * Simple 1:1 field mapping. We exclude the Url relationship
     * because the ClickEventResponse is always returned INSIDE
     * UrlAnalyticsResponse, which already identifies the URL.
     */
    private ClickEventResponse mapToClickEventResponse(ClickEvent clickEvent) {
        return ClickEventResponse.builder()
                .id(clickEvent.getId())
                .clickedAt(clickEvent.getClickedAt())
                .ipAddress(clickEvent.getIpAddress())
                .userAgent(clickEvent.getUserAgent())
                .referer(clickEvent.getReferer())
                .os(clickEvent.getOs())
                .browser(clickEvent.getBrowser())
                .deviceType(clickEvent.getDeviceType())
                .country(clickEvent.getCountry())
                .city(clickEvent.getCity())
                .build();
    }
}
