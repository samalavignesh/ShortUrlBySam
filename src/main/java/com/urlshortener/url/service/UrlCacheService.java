package com.urlshortener.url.service;

import com.urlshortener.entity.Url;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * =====================================================
 * URL CACHE SERVICE
 * =====================================================
 *
 * A dedicated Spring bean whose SOLE RESPONSIBILITY is performing
 * cached URL lookups by short code.
 *
 * WHY IS THIS A SEPARATE CLASS?
 * ─────────────────────────────
 * Spring's @Cacheable works via AOP (Aspect-Oriented Programming) proxies.
 * Spring wraps your @Service beans in a proxy object at startup. When
 * an EXTERNAL caller invokes a method, the proxy intercepts the call,
 * checks the cache, and only calls the real method on a cache miss.
 *
 * THE PROBLEM — SELF-INVOCATION:
 * ──────────────────────────────
 * If a method inside UrlService calls ANOTHER method inside the same
 * UrlService class (like redirect() calling findUrlByShortCode()), it
 * calls it directly on "this" — bypassing the proxy entirely.
 *
 *  redirect()  ─── this.findUrlByShortCode() ──→ BYPASSES PROXY!
 *                                                  @Cacheable is ignored.
 *                                                  Always hits the DB.
 *
 * THE FIX — SEPARATE BEAN:
 * ─────────────────────────
 * By moving findUrlByShortCode() to this separate class, UrlService
 * calls it via an injected reference (urlCacheService.findByShortCode()).
 * That call goes THROUGH the Spring proxy — Redis is actually used!
 *
 *  UrlService.redirect()
 *      │
 *      └─ urlCacheService.findByShortCode("abc123")
 *              │
 *          [Spring AOP Proxy]
 *              │
 *          Check Redis "urlCache::abc123"
 *              ├── HIT  → Return from Redis (~0.5ms) ✅
 *              └── MISS → Query DB, store in Redis, return result
 *
 * PRODUCTION NOTE:
 * ─────────────────
 * This is the standard, clean pattern used in production Spring Boot apps.
 * Alternatives like @Autowired self-injection or AopContext.currentProxy()
 * are considered code smells and are harder to test.
 *
 * @Service → Registers this as a Spring-managed bean.
 * @RequiredArgsConstructor → Lombok generates constructor for final fields.
 */
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private final UrlRepository urlRepository;

    /**
     * Looks up a URL by its short code with Redis caching.
     *
     * This is the HOT PATH — called on EVERY redirect request.
     *
     * @Cacheable → Spring checks Redis before calling this method.
     *
     *   value = "urlCache"
     *     → The name of the cache (configured with 10-min TTL in CacheConfig)
     *
     *   key = "#shortCode"
     *     → The Redis key is the short code itself.
     *       Example Redis key: "urlCache::a8vWRn"
     *       You can verify in Redis CLI: GET "urlCache::a8vWRn"
     *
     * FIRST CALL (Cache MISS):
     *   1. Spring checks Redis → Key not found
     *   2. Spring calls this method → Queries PostgreSQL
     *   3. Spring stores the Url object in Redis as JSON
     *   4. Returns the Url object to the caller
     *   DB query time: ~5-20ms
     *
     * SUBSEQUENT CALLS (Cache HIT):
     *   1. Spring checks Redis → Key found!
     *   2. Spring deserializes the JSON back to a Url object
     *   3. Returns immediately — this method body is NEVER called
     *   Redis lookup time: ~0.1-1ms → 10-100x faster than DB!
     *
     * CACHE INVALIDATION:
     *   When a URL is deactivated, evictUrl() is called to remove
     *   the stale entry from Redis. Next redirect attempt will
     *   get a cache MISS, query the DB, find isActive=false → 404.
     *
     * @param shortCode The short code to look up (e.g., "a8vWRn")
     * @return The Url entity
     * @throws ResourceNotFoundException if no URL exists with this short code
     */
    @Cacheable(value = "urlCache", key = "#shortCode")
    public Url findByShortCode(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Url", "shortCode", shortCode));
    }

    /**
     * Evicts a URL from the Redis cache by its short code.
     *
     * Called by UrlService when a URL is deactivated (soft-deleted).
     * Without this, a deactivated URL would still be served from cache
     * for up to 10 minutes (the TTL configured in CacheConfig).
     *
     * @CacheEvict → Removes the entry with the given key from the cache.
     *
     * FLOW AFTER EVICTION:
     *   User clicks short link → cache MISS → DB query → isActive=false
     *   → ResourceNotFoundException → HTTP 404 Not Found ✅
     *
     * @param shortCode The short code of the URL to evict from cache
     */
    @CacheEvict(value = "urlCache", key = "#shortCode")
    public void evictUrl(String shortCode) {
        /*
         * This method body is intentionally empty.
         *
         * @CacheEvict is an AOP annotation — the proxy handles the cache
         * eviction BEFORE (or after) this method runs. The method itself
         * doesn't need to do anything.
         *
         * We declare the method to give @CacheEvict a place to attach.
         * This is the standard Spring caching pattern.
         */
    }
}
